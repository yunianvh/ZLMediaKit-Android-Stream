package com.zlmediakit.demo;

import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.drake.net.NetConfig;
import com.rust.sip.GB28181.GB28181CallBack;
import com.rust.sip.GB28181.GB28181Client;
import com.stream.CameraUtil;
import com.stream.http.NetUtil;
import com.stream.util.FLogUtil;
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
        //初始化日志工具
        FLogUtil.INSTANCE.init(this, true);
        //初始化net
        NetConfig.INSTANCE.setApp(this);
        NetUtil.Companion.getInstance().init(new NetUtil.ZlmListener() {
            @Override
            public void onPushStatus(boolean status) {
                Log.e(TAG, "onRecordStatue  推流状态: " + status);
            }

            @Override
            public void onRecordStatus(boolean status) {
                Log.e(TAG, "onRecordStatue  录制状态: " + status);
            }
        });
        findViewById(R.id.sample_text).setOnClickListener(view -> {
            File f = new File(sd_dir + "/aaaa/");
            if (!f.exists()) f.mkdirs();
            Log.e("CameraUtil", "onClick  录制地址: " + sd_dir + "/aaaa/");
            CameraUtil.getInstance().start(f.getAbsolutePath());
        });
        findViewById(R.id.btn_record).setOnClickListener(view -> {
            File f = new File(getExternalFilesDir((String) null).getAbsolutePath() + "/video");
            if (!f.exists()) f.mkdirs();
            Log.e("CameraUtil", "onClick  录制地址: " + f.getAbsolutePath());
            NetUtil.Companion.getInstance().startRecord(f.getAbsolutePath());
        });
        findViewById(R.id.btn_record_stop).setOnClickListener(view -> {
            NetUtil.Companion.getInstance().stopRecord();
        });
        findViewById(R.id.btn_push_start).setOnClickListener(view -> {
            NetUtil.Companion.getInstance().startPush("rtmp://119.23.154.45:11006/live/36147_58009");
        });
        findViewById(R.id.btn_push_stop).setOnClickListener(view -> {
            NetUtil.Companion.getInstance().stopPush();
        });
        findViewById(R.id.btn_init_gb28181).setOnClickListener(view -> {
            GB28181Client.getInstance().GB28181Init();
            GB28181Client.getInstance().GB28181_Start();
        });

        GB28181Client.getInstance().setGB28181CallBack(new GB28181CallBack() {
            @Override
            public void onStartRtp(String ssrc, String url, int port, int is_udp) {
                Log.e(TAG, "onStartRtp  开始RTP推流: " + ssrc);
                NetUtil.Companion.getInstance().startSendRtp(ssrc, url, port, is_udp);
            }

            @Override
            public void onStopRtp(String ssrc) {
                Log.e(TAG, "onStartRtp  关闭RTP推流: " + ssrc);
                NetUtil.Companion.getInstance().stopSendRtp(ssrc);
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
