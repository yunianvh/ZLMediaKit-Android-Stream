package com.wtoe.jrtplib;

/**
 * author：create by Administrator on 2019/9/2
 * email：1564063766@qq.com
 * remark:
 */
public interface RtpListener {
    /**
     * 收到rtp数据
     *
     * @param rtp_data 负载内容
     * @param pkg_size 数据长度
     */
    void receiveRtpData(byte[] rtp_data, int pkg_size, boolean isMarker,long lastTime);

    /**
     * 收到RTCP数据
     *
     * @param remote_ip 用于区分是谁发过来的数据
     */
    void receiveRtcpData(String remote_ip);

    /**
     * 收到Bye指令
     *
     * @param remote_ip 用于区分是谁发过来的数据
     */
    void receiveBye(String remote_ip);
}
