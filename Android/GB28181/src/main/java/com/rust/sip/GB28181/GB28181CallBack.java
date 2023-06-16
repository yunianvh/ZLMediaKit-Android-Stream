package com.rust.sip.GB28181;

/**
 * Created by 玉念聿辉.
 * Use: 信令入口回调
 * Date: 2023/6/16
 * Time: 17:59
 */
public interface GB28181CallBack {
    //开始rtp推流
    void onStartRtp(String ssrc, String url, int port, int is_udp);
    //关闭rtp推流
    void onStopRtp(String ssrc);
}
