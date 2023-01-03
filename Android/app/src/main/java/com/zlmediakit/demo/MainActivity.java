package com.zlmediakit.demo;

import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.drake.net.NetConfig;
import com.stream.CameraUtil;
import com.stream.http.NetUtil;
import com.zlmediakit.jni.ZLMediaKit;

import java.io.File;

public class MainActivity extends AppCompatActivity {
    public static final String TAG = "ZLMediaKit";
    private static String[] PERMISSIONS_STORAGE = {
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.INTERNET",
            "android.permission.RECORD_AUDIO",
            "android.permission.CAMERA"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        String sd_dir = Environment.getExternalStoragePublicDirectory("").toString();
        //初始化net
        NetConfig.INSTANCE.setApp(this);
        NetUtil.Companion.getInstance().init();

        findViewById(R.id.sample_text).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                File f = new File(sd_dir + "/aaaa/");
                if (!f.exists()) f.mkdirs();
                Log.e("CameraUtil", "onClick  录制地址: " + sd_dir + "/aaaa/");
                CameraUtil.getInstance().start(f.getAbsolutePath());
            }
        });
        findViewById(R.id.btn_record).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                File f = new File(getExternalFilesDir((String)null).getAbsolutePath()+"/avideo");
                if (!f.exists()) f.mkdirs();
                Log.e("CameraUtil", "onClick  录制地址: " + f.getAbsolutePath());
                NetUtil.Companion.getInstance().startRecord(f.getAbsolutePath());
            }
        });

        boolean permissionSuccess = true;
        for (String str : PERMISSIONS_STORAGE) {
            int permission = ActivityCompat.checkSelfPermission(this, str);
            if (permission != PackageManager.PERMISSION_GRANTED) {
                // 没有写的权限，去申请写的权限，会弹出对话框
                ActivityCompat.requestPermissions(this, PERMISSIONS_STORAGE, 1);
                permissionSuccess = false;
                break;
            }
        }

        if (permissionSuccess) {
            Toast.makeText(this, "你可以修改配置文件再启动：" + sd_dir + "/zlmediakit.ini", Toast.LENGTH_LONG).show();
            Toast.makeText(this, "SSL证书请放置在：" + sd_dir + "/zlmediakit.pem", Toast.LENGTH_LONG).show();

            CameraUtil.getInstance().init(this);
        } else {
            Toast.makeText(this, "请给予我权限，否则无法启动测试！", Toast.LENGTH_LONG).show();
        }
    }

    private ZLMediaKit.MediaPlayer _player;

    private void test_player() {
        _player = new ZLMediaKit.MediaPlayer("rtmp://live.hkstv.hk.lxdns.com/live/hks1", new ZLMediaKit.MediaPlayerCallBack() {
            @Override
            public void onPlayResult(int code, String msg) {
                Log.d(TAG, "onPlayResult:" + code + "," + msg);
            }

            @Override
            public void onShutdown(int code, String msg) {
                Log.d(TAG, "onShutdown:" + code + "," + msg);
            }

            @Override
            public void onData(ZLMediaKit.MediaFrame frame) {
                Log.d(TAG, "onData:"
                        + frame.trackType + ","
                        + frame.codecId + ","
                        + frame.dts + ","
                        + frame.pts + ","
                        + frame.keyFrame + ","
                        + frame.prefixSize + ","
                        + frame.data.length);
            }
        });
    }

}
