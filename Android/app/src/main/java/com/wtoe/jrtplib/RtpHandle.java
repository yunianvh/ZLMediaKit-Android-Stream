package com.wtoe.jrtplib;

import java.io.IOException;
import java.net.ServerSocket;

public class RtpHandle {
    static {
        System.loadLibrary("rtp-handle");
    }

    /**
     * 生成一个可使用的udp端口（20000以上）
     *
     * @param port 传20000即可
     * @return
     */
    public static int getAvailablePort(int port) {
        try {
            ServerSocket socket = new ServerSocket(port);
            socket.close();
            return port;
        } catch (IOException e) {
            return getAvailablePort(port + 2);
        }
    }

    /**
     * 只发送数据，不做接收处理
     */
    public static native long initSendHandle(int localport, String desthost, int destport, RtpListener listener, int framerate);

    /**
     * 只接收处理
     */
    public static native long initReceiveHandle(String localhost, int localreceiveport, RtpListener listener);

    /**
     * 接收数据，并转发
     */
    public static native long initReceiveAndSendHandle(String localhost, int localreceiveport, int localsendport, String desthost, int destport, RtpListener listener);

    /**
     * 发送数据
     */
    public static native boolean sendByte(long rtpHandler, byte[] src, int byteLength, boolean isSpsOrMarker, boolean isRtpData,long lastTime);

    /**
     * 销毁资源
     */
    public static native boolean finiHandle(long rtpHandler);
}
