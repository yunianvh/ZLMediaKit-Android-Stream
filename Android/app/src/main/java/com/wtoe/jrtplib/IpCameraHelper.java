package com.wtoe.jrtplib;

import com.stream.util.FLogUtil;

import java.util.concurrent.ArrayBlockingQueue;

public class IpCameraHelper implements RtpListener {
    private static final String TAG = IpCameraHelper.class.getSimpleName();
    private boolean isRunning = false;

    private RtpHandle rtpHandle;
    private long mRtpHandle;
    private String localIP, remoteIp;
    private int localPort, remotePort;

    class RtpData {
        private byte[] data;
        private int length;
        private boolean isMark;
        private long lastTime;

        public RtpData(byte[] data, int length, boolean isMark, long lastTime) {
            this.data = data;
            this.length = length;
            this.isMark = isMark;
            this.lastTime = lastTime;
        }
    }

    private int queueSize = 3000;
    private ArrayBlockingQueue<RtpData> rtpDataQueue = new ArrayBlockingQueue<>(queueSize);

    /**
     * 构造
     *
     * @param localIP
     * @param localPort
     * @param remoteIp
     * @param remotePort
     */
    public IpCameraHelper(String localIP, int localPort, String remoteIp, int remotePort) {
        this.localIP = localIP;
        this.localPort = localPort;
        this.remoteIp = remoteIp;
        this.remotePort = remotePort;
    }

    /**
     * 收发数据
     */
    public void initData() {
        new Thread(() -> {
            isRunning = true;
            rtpHandle = new RtpHandle();
            FLogUtil.INSTANCE.e(TAG, "localIp:" + localIP + "  / localPort:" + localPort);
            FLogUtil.INSTANCE.e(TAG, "remoteIp:" + remoteIp + "  / remotePort:" + remotePort);

            int mreceiveport = rtpHandle.getAvailablePort(localPort);

            int msendport = rtpHandle.getAvailablePort(mreceiveport+2);

            mRtpHandle = rtpHandle.initReceiveAndSendHandle(localIP, mreceiveport,msendport, remoteIp, remotePort, IpCameraHelper.this);
            if (mRtpHandle == 0) {
                rtpHandle = null;
                FLogUtil.INSTANCE.e(TAG, "JNI init error");
                return;
            }
            FLogUtil.INSTANCE.e(TAG, "JNI init ok");
            RtpData rtpData = null;
            while (isRunning) {
                try {
                    rtpData = rtpDataQueue.take();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                FLogUtil.INSTANCE.e(TAG, "DataQueue size : " + rtpDataQueue.size());
                if (rtpData != null && rtpHandle != null && mRtpHandle != 0) {
//                        rtpHandle.sendByte(mRtpHandle, rtpData.data, rtpData.length, rtpData.isMark, true,rtpData.lastTime);
                }
                rtpData = null;
            }
        }).start();
    }


    public void fini() {
        isRunning = false;
        if (rtpHandle != null && mRtpHandle != 0) {
            rtpHandle.finiHandle(mRtpHandle);
            rtpHandle = null;
            mRtpHandle = 0;
        }
    }

    private int mCount = 0;

    @Override
    public void receiveRtpData(byte[] rtp_data, int pkg_size, boolean isMarker, long lastTime) {
        if (mCount % 100 == 0) {
            mCount = 0;
            System.out.println("收到 【RTP】" + pkg_size);
        }
        mCount++;
        addDataToQueue(new RtpData(rtp_data, pkg_size, isMarker, lastTime));
    }

    private void addDataToQueue(RtpData rtpData) {
        try {
            if (rtpDataQueue.size() >= queueSize) {
                rtpDataQueue.poll();
                FLogUtil.INSTANCE.e(TAG, "addDataToQueue lost packet");
            }
            rtpDataQueue.offer(rtpData);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void receiveRtcpData(String remote_ip) {
        System.out.println("收到 【RTCP】 " + " IP:" + remote_ip);
    }

    @Override
    public void receiveBye(String remote_ip) {
        System.out.println("收到 【BYE】 " + " IP:" + remote_ip);
    }
}
