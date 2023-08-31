package com.stream;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import com.pedro.rtsp.utils.ConnectCheckerRtsp;
import com.rtspserver.RtspServerCamera1;
import com.stream.http.NetUtil;
import com.stream.util.FLogUtil;
import com.zlmediakit.jni.ZLMediaKit;

/**
 * Created by 玉念聿辉.
 * User: 吴明辉
 * Date: 2023/1/2
 * Time: 16:43
 */
public class CameraUtil implements ConnectCheckerRtsp {
    private final String TAG = CameraUtil.class.getSimpleName();
    public static volatile CameraUtil mCameraUtil;

    public static CameraUtil getInstance() {
        if (mCameraUtil == null) {
            synchronized (CameraUtil.class) {
                if (mCameraUtil == null) {
                    mCameraUtil = new CameraUtil();
                }
            }
        }
        return mCameraUtil;
    }

    private Context context;
    private RtspServerCamera1 serverRtsp;

    /**
     * 初始化
     *
     * @param context
     */
    public void init(Context context) {
        this.context = context;
        //1、初始化rtsp
        serverRtsp = new RtspServerCamera1(context, this, 1936);
        if (serverRtsp != null && serverRtsp.isRecording() || serverRtsp.prepareAudio() && serverRtsp.prepareVideo(1280, 720, 1024 * 1024)) {
            serverRtsp.startStream();
        }else {
            FLogUtil.INSTANCE.e(TAG, "init:初始化rtsp服务器异常");
        }
        //2、初始化zlm
        ZLMediaKit.startDemo(Environment.getExternalStoragePublicDirectory("").toString());
        NetUtil.Companion.getInstance().checkZlmStatus();
    }

    /**
     * 开始
     */
    public void start(String path) {
        //3、zlm拉rtsp流
        NetUtil.Companion.getInstance().startProxy(path, serverRtsp.getEndPointConnection());
    }

    /**
     * 停止
     */
    public void stop() {
        NetUtil.Companion.getInstance().stopProxy();
        if (serverRtsp != null) serverRtsp.stopStream();
    }

    /**
     * -----------------------------RtspServer回调监听-------------------------------
     */
    @Override
    public void onAuthErrorRtsp() {

    }

    @Override
    public void onAuthSuccessRtsp() {

    }

    @Override
    public void onConnectionFailedRtsp(String s) {
        FLogUtil.INSTANCE.e(TAG, "onConnectionFailedRtsp: " + s);
    }

    @Override
    public void onConnectionStartedRtsp(String s) {

    }

    @Override
    public void onConnectionSuccessRtsp() {
        //初始化rtsp成功后获取rtsp的流地址
        FLogUtil.INSTANCE.e(TAG, "onConnectionSuccessRtsp: " + serverRtsp.getEndPointConnection());
    }

    @Override
    public void onDisconnectRtsp() {
        FLogUtil.INSTANCE.e(TAG, "onDisconnectRtsp");
    }

    @Override
    public void onNewBitrateRtsp(long l) {

    }
}
