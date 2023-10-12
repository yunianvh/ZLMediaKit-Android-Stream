package com.rust.sip.GB28181;

import android.text.TextUtils;
import android.util.Log;

import com.rtspserver.RtspServer;
import com.rust.sip.GB28181.gb28181.GB28181Params;
import com.rust.sip.GB28181.gb28181.XMLUtil;
import com.rust.sip.GB28181.net.IpAddress;
import com.rust.sip.GB28181.sdp.MediaDescriptor;
import com.rust.sip.GB28181.sdp.SessionDescriptor;
import com.rust.sip.GB28181.sip.address.NameAddress;
import com.rust.sip.GB28181.sip.address.SipURL;
import com.rust.sip.GB28181.sip.authentication.DigestAuthentication;
import com.rust.sip.GB28181.sip.header.AuthorizationHeader;
import com.rust.sip.GB28181.sip.header.ExpiresHeader;
import com.rust.sip.GB28181.sip.header.UserAgentHeader;
import com.rust.sip.GB28181.sip.message.Message;
import com.rust.sip.GB28181.sip.message.MessageFactory;
import com.rust.sip.GB28181.sip.message.SipMethods;
import com.rust.sip.GB28181.sip.message.SipResponses;
import com.rust.sip.GB28181.sip.provider.SipProvider;
import com.rust.sip.GB28181.sip.provider.SipProviderListener;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Created by 玉念聿辉.
 * Use: GB28181信令入口
 * Date: 2023/6/16
 * Time: 16:53
 */
public class GB28181Client {
    public volatile static GB28181Client instance;

    public static GB28181Client getInstance() {
        if (instance == null) {
            synchronized (GB28181Client.class) {
                if (instance == null) {
                    instance = new GB28181Client();
                }
            }
        }
        return instance;
    }

    private final String TAG = GB28181Client.class.getSimpleName();
    private final ThreadPoolExecutor poolExecutor = new ThreadPoolExecutor(3, 10, 10, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(1), new ThreadFactory() {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable);
            thread.setName("GB28181Client");
            return thread;
        }
    });
    private SipProvider sipProvider;
    private Timer timerForKeepAlive;
    private long keepAliveSN = 0;
    private GB28181CallBack gb28181CallBack;
    private String audioTag = "";//对讲发送数据时的tag，用于判断接收的tag是否一样，是就发送对讲sdp

    private boolean isWvp = false;

    /**
     * 国标模块参数初始化
     */
    public void GB28181Init() {
        GB28181Params.setRemoteSIPServerPort(28181);//SIP服务器端口
        GB28181Params.setLocalSIPIPAddress(RtspServer.Companion.getIPAddress());//本机地址
        if (isWvp) {
            GB28181Params.setSIPServerIPAddress("47.105.215.67");//SIP服务器地址
            GB28181Params.setRemoteSIPServerID("34020000002000000001");
            GB28181Params.setRemoteSIPServerSerial("3402000000");
            GB28181Params.setLocalSIPDeviceId("34020000001100000291");
            GB28181Params.setLocalSIPMediaId("34020000001100000291");
        } else {
//            GB28181Params.setSIPServerIPAddress("125.89.145.66");//SIP服务器地址
//            GB28181Params.setRemoteSIPServerID("44180000002000010001");
//            GB28181Params.setRemoteSIPServerSerial("4418000000");
//            GB28181Params.setLocalSIPDeviceId("34020000001320000016");
//            GB28181Params.setLocalSIPMediaId("34020000001320000016");

            GB28181Params.setSIPServerIPAddress("47.100.112.218");//SIP服务器地址
            GB28181Params.setRemoteSIPServerID("10000000002000000001");
            GB28181Params.setRemoteSIPServerSerial("1000000000");
            GB28181Params.setLocalSIPDeviceId("10000000001321000106");
            GB28181Params.setLocalSIPMediaId("10000000001321000106");
        }
        GB28181Params.setLocalSIPPort(5080);//本机端口
        GB28181Params.setPassword("123456");//密码


        GB28181Params.setCurGBState(0);
        GB28181Params.setCurDeviceDownloadMeidaState(0);
        GB28181Params.setCurDevicePlayMediaState(0);
        Log.d(TAG, "MyService -> GB28181Init()");
    }

    /**
     * 开始建立连接
     */
    public void GB28181_Start() {
        poolExecutor.execute(() -> {
            IpAddress.setLocalIpAddress(GB28181Params.getLocalSIPIPAddress());
            sipProvider = new SipProvider(GB28181Params.getLocalSIPIPAddress(), GB28181Params.getLocalSIPPort());
            sipProvider.addSipProviderListener(sipProviderListener);
            NameAddress to = new NameAddress(new SipURL(GB28181Params.getLocalSIPDeviceId(), GB28181Params.getRemoteSIPServerSerial()));
            NameAddress from = new NameAddress(new SipURL(GB28181Params.getLocalSIPDeviceId(), GB28181Params.getRemoteSIPServerSerial()));
            NameAddress contact = new NameAddress(new SipURL(GB28181Params.getLocalSIPDeviceId(), GB28181Params.getLocalSIPIPAddress() + ":" + GB28181Params.getLocalSIPPort()));
            Message message = MessageFactory.createRequest(sipProvider, SipMethods.REGISTER, new SipURL(GB28181Params.getRemoteSIPServerID(), GB28181Params.getSIPServerIPAddress() + ":" + GB28181Params.getRemoteSIPServerPort()), to, from, contact, null);
            message.setUserAgentHeader(new UserAgentHeader(GB28181Params.defaultUserAgent));
            sipProvider.sendMessage(message, GB28181Params.defaultProtol, GB28181Params.getSIPServerIPAddress(), GB28181Params.getRemoteSIPServerPort(), 0);
        });
    }

    /**
     * 设置回调
     *
     * @param back
     */
    public void setGB28181CallBack(GB28181CallBack back) {
        gb28181CallBack = back;
    }

    /**
     * 数据收发回调
     */
    SipProviderListener sipProviderListener = (sip_provider, message) -> {
        //自己发送后服务器回复的信息
        if (message.isResponse()) {
            Log.i(TAG, "-------------------自己发送后服务器回复的信息----------------------");
            switch (message.getCSeqHeader().getMethod()) {
                case SipMethods.REGISTER:
                    if (message.getStatusLine().getCode() == 401) {
                        NameAddress to = new NameAddress(new SipURL(GB28181Params.getLocalSIPDeviceId(), GB28181Params.getRemoteSIPServerSerial()));
                        NameAddress from = new NameAddress(new SipURL(GB28181Params.getLocalSIPDeviceId(), GB28181Params.getRemoteSIPServerSerial()));
                        NameAddress contact = new NameAddress(new SipURL(GB28181Params.getLocalSIPDeviceId(), GB28181Params.getLocalSIPIPAddress() + ":" + GB28181Params.getLocalSIPPort()));
                        Message res = MessageFactory.createRequest(sipProvider, SipMethods.REGISTER, new SipURL(GB28181Params.getRemoteSIPServerID(), GB28181Params.getSIPServerIPAddress() + ":" + GB28181Params.getRemoteSIPServerPort()), to, from, contact, null);
                        res.setUserAgentHeader(new UserAgentHeader(GB28181Params.defaultUserAgent));
                        AuthorizationHeader ah = new AuthorizationHeader("Digest");
                        ah.addUsernameParam(GB28181Params.getLocalSIPDeviceId());
                        ah.addRealmParam(message.getWwwAuthenticateHeader().getRealmParam());
                        ah.addNonceParam(message.getWwwAuthenticateHeader().getNonceParam());
                        ah.addUriParam(res.getRequestLine().getAddress().toString());
                        ah.addAlgorithParam("MD5");
                        String response = (new DigestAuthentication(SipMethods.REGISTER, ah, null, GB28181Params.getPassword())).getResponse();
                        ah.addResponseParam(response);
                        res.setAuthorizationHeader(ah);
                        if (GB28181Params.getCurGBState() == 1) {
                            res.setExpiresHeader(new ExpiresHeader(0));
                        }
                        sipProvider.sendMessage(res, GB28181Params.defaultProtol, GB28181Params.getSIPServerIPAddress(), GB28181Params.getRemoteSIPServerPort(), 0);
                    } else if (message.getStatusLine().getCode() == 200) {
                        //注销成功
                        if (GB28181Params.getCurGBState() == 1) {
                            GB28181Params.setCurGBState(0);
                            //取消发送心跳包
                            timerForKeepAlive.cancel();
                        } else {//注册成功
                            GB28181Params.setCurGBState(1);
                            //每隔30秒 发送心跳包
                            timerForKeepAlive = new Timer(true);
                            timerForKeepAlive.schedule(new TimerTask() {
                                @Override
                                public void run() {
                                    GB28181_KeepAlive();
                                }
                            }, 0, 30 * 1000);
                        }
                    }
                    break;
                case SipMethods.MESSAGE:
                    String tag = message.getFromHeader().getTag();
                    Log.e(TAG, "自己发送后服务器回复的MESSAGE信息  tag：" + tag + "  audioTag:" + audioTag);
                    if (audioTag.equals(tag)) {
                        //收到回复后Invite上报sdp
                        String y = "0" + GB28181Params.getLocalSIPDeviceId().substring(3, 8) + "9546";
                        String InviteResponseBody = "v=0\n" +
                                "o=" + GB28181Params.getLocalSIPDeviceId() + " 0 0 IN IP4 " + GB28181Params.getLocalSIPIPAddress() + "\n" +
                                "s=Play\n" +
                                "c=IN IP4 " + GB28181Params.getLocalSIPIPAddress() + "\n" +
                                "t=0 0\n" +
                                "m=audio 40004 TCP/RTP/AVP 96 8\n" +
                                "a=recvonly\n" +
                                "a=rtpmap:96 PS/90000\n" +
                                "a=rtpmap:8 PCMA/8000\n" +
                                "a=setup:active\n" +
                                "a=connection:new\n" +
                                "y=" + y + "\n" +
                                "f=v/////a/1/8/1";
                        NameAddress to = new NameAddress(new SipURL(GB28181Params.getRemoteSIPServerID(), GB28181Params.getSIPServerIPAddress() + ":" + GB28181Params.getRemoteSIPServerPort()));
                        NameAddress contact = new NameAddress(new SipURL(GB28181Params.getLocalSIPDeviceId(), GB28181Params.getLocalSIPIPAddress() + ":" + GB28181Params.getLocalSIPPort()));
                        Message CatalogResponseRequest = MessageFactory.createInviteRequest(sipProvider, message.getFromHeader().getNameAddress(), to,
                                message.getFromHeader().getNameAddress(), GB28181Params.getRemoteSIPServerID() + ":0bafa9e0918400aae7c," + GB28181Params.getLocalSIPDeviceId() + ":053038776c049b7a776", contact, (message.getCSeqHeader().getSequenceNumber() + 1), "application/sdp", InviteResponseBody);
                        CatalogResponseRequest.setUserAgentHeader(new UserAgentHeader(GB28181Params.defaultUserAgent));
                        sipProvider.sendMessage(CatalogResponseRequest, GB28181Params.defaultProtol, GB28181Params.getSIPServerIPAddress(), GB28181Params.getRemoteSIPServerPort(), 0);
                    }
                    break;
                case SipMethods.ACK:
                    break;
                case SipMethods.BYE:
                    break;
            }
        }
        //服务器主动发起的信令
        else if (message.isRequest()) {
            Log.i(TAG, "-------------------服务器主动发起的信令----------------------");
            //心跳等Message
            if (message.isMessage()) {
                if (message.hasBody()) {
                    String body = message.getBody();
                    String sn = body.substring(body.indexOf("<SN>") + 4, body.indexOf("</SN>"));
                    String cmdType = body.substring(body.indexOf("<CmdType>") + 9, body.indexOf("</CmdType>"));
                    Log.e(TAG, "onReceivedMessage cmdType: " + cmdType + "  sn:" + sn);
                    //查询设备信息
                    if ("DeviceInfo".equals(cmdType)) {
                        sendDeviceInfo();
                    }

                    //设备通道
                    if (message.getBodyType().equalsIgnoreCase("application/manscdp+xml")) {
                        //发送 200 OK
                        if (cmdType.equals("Catalog")) {
                            Message CatalogResponse = MessageFactory.createResponse(message, 200, SipResponses.reasonOf(200), null);
                            CatalogResponse.setUserAgentHeader(new UserAgentHeader(GB28181Params.defaultUserAgent));
                            sipProvider.sendMessage(CatalogResponse, GB28181Params.defaultProtol, GB28181Params.getSIPServerIPAddress(), GB28181Params.getRemoteSIPServerPort(), 0);

                            //region catalogBody 通道信息
                            String catalogBody = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                                    "    <Response>\n" +
                                    "      <CmdType>Catalog</CmdType>\n" +
                                    "      <SN>" + sn + "</SN>\n" +
                                    "      <DeviceID>" + GB28181Params.getLocalSIPDeviceId() + "</DeviceID>\n" +
                                    "      <SumNum>1</SumNum>\n" +
                                    "      <DeviceList Num='1'>\n" +
                                    "          <Item>\n" +
                                    "            <DeviceID>" + GB28181Params.getLocalSIPDeviceId() + "</DeviceID>\n" +
                                    "            <Name>Cap</Name>\n" +
                                    "            <Manufacturer>Runde</Manufacturer>\n" +
                                    "            <Model>MODEL</Model>\n" +
                                    "            <Owner>cxj</Owner>\n" +
                                    "            <CivilCode>3400200</CivilCode>\n" +
                                    "            <Address>local</Address>\n" +
                                    "            <Parental>0</Parental>\n" +
                                    "            <SafetyWay>0</SafetyWay>\n" +
                                    "            <RegisterWay>1</RegisterWay>\n" +
                                    "            <Secrecy>0</Secrecy>\n" +
                                    "            <IPAddress>" + GB28181Params.getLocalSIPIPAddress() + "</IPAddress>\n" +
                                    "            <Port>" + GB28181Params.getLocalSIPPort() + "</Port>\n" +
                                    "            <Password>12345678</Password>\n" +
                                    "            <Status>ON</Status>\n" +
                                    "          </Item>\n" +
                                    "      </DeviceList>\n" +
                                    "    </Response>";
                            //endregion

                            Message CatalogResponseRequest = MessageFactory.createMessageRequest(sipProvider, message.getFromHeader().getNameAddress(),
                                    message.getToHeader().getNameAddress(), null, XMLUtil.XML_MANSCDP_TYPE, catalogBody);
                            CatalogResponseRequest.setUserAgentHeader(new UserAgentHeader(GB28181Params.defaultUserAgent));
                            sipProvider.sendMessage(CatalogResponseRequest, GB28181Params.defaultProtol, GB28181Params.getSIPServerIPAddress(), GB28181Params.getRemoteSIPServerPort(), 0);
                        } else if (cmdType.equals("DeviceControl")) {
                            //解析控制指令
                            Message DeviceControlResponse = MessageFactory.createResponse(message, 200, SipResponses.reasonOf(200), null);
                            DeviceControlResponse.setUserAgentHeader(new UserAgentHeader(GB28181Params.defaultUserAgent));
                            sipProvider.sendMessage(DeviceControlResponse, GB28181Params.defaultProtol, GB28181Params.getSIPServerIPAddress(), GB28181Params.getRemoteSIPServerPort(), 0);
                        } else if (cmdType.equals("Broadcast")) {
                            //接受到广播
                            String sourceID = body.substring(body.indexOf("<SourceID>") + 10, body.indexOf("</SourceID>"));
                            Log.e(TAG, "onReceivedMessage   sourceID: " + sourceID);
                            String targetID = body.substring(body.indexOf("<TargetID>") + 10, body.indexOf("</TargetID>"));

                            //先回复个200
                            Message response = MessageFactory.createResponse(message, 200, SipResponses.reasonOf(200), null);
                            response.setUserAgentHeader(new UserAgentHeader(GB28181Params.defaultUserAgent));
                            sipProvider.sendMessage(response, GB28181Params.defaultProtol, GB28181Params.getSIPServerIPAddress(), GB28181Params.getRemoteSIPServerPort(), 0);


                            //再回复带broadcastBody信息
                            String broadcastBody = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                                    "    <Response>\n" +
                                    "      <CmdType>Broadcast</CmdType>\n" +
                                    "      <SN>" + sn + "</SN>\n" +
                                    "      <DeviceID>" + GB28181Params.getLocalSIPDeviceId() + "</DeviceID>\n" +
                                    "      <Result>OK</Result>\n" +
                                    "    </Response>";
                            Message CatalogResponseRequest = MessageFactory.createMessageRequest(sipProvider, message.getFromHeader().getNameAddress(),
                                    message.getToHeader().getNameAddress(), null, XMLUtil.XML_MANSCDP_TYPE, broadcastBody);
                            CatalogResponseRequest.setUserAgentHeader(new UserAgentHeader(GB28181Params.defaultUserAgent));
                            sipProvider.sendMessage(CatalogResponseRequest, GB28181Params.defaultProtol, GB28181Params.getSIPServerIPAddress(), GB28181Params.getRemoteSIPServerPort(), 0);
                            audioTag = CatalogResponseRequest.getFromHeader().getTag();
                            Log.e(TAG, "onReceivedMessage   targetID: " + targetID + "  判断接受的ID是否是自己：" + targetID.equals(GB28181Params.getLocalSIPDeviceId()) + "  audioTag:" + audioTag);
                        }
                    }
                }
            }
            //获取推流信息
            else if (message.isInvite()) {
                if (message.hasBody()) {
                    String body = message.getBody();
                    SessionDescriptor sdp = new SessionDescriptor(body);
                    MediaDescriptor mediaDescriptor = sdp.getMediaDescriptors().firstElement();
                    String address = sdp.getConnection().getAddress();
                    int port = mediaDescriptor.getMedia().getPort();
                    switch (sdp.getSessionName().getValue().toLowerCase()) {
                        case "play":
                            String y = body.substring(body.indexOf("y=") + 2, body.indexOf("y=") + 12);
                            Log.e(TAG, "onReceivedMessage   sdp: " + body);
                            Log.e(TAG, "onReceivedMessage   y: " + y);
                            if (TextUtils.isEmpty(y) || !TextUtils.isDigitsOnly(y)) {
                                y = "0" + GB28181Params.getLocalSIPDeviceId().substring(3, 8) + "9546";
                                Log.e(TAG, "onReceivedMessage  sdp没有y，自己生成: " + y);

                                //海康需要回复一下这个
                                Message InviteResponse = MessageFactory.createResponse(message, 180, SipResponses.reasonOf(180), new NameAddress(new SipURL(GB28181Params.getLocalSIPDeviceId(), GB28181Params.getLocalSIPIPAddress())));
                                InviteResponse.setUserAgentHeader(new UserAgentHeader(GB28181Params.defaultUserAgent));
                                sipProvider.sendMessage(InviteResponse, GB28181Params.defaultProtol, GB28181Params.getSIPServerIPAddress(), GB28181Params.getRemoteSIPServerPort(), 0);
                            }
                            //region InviteResponseBody
                            String m = body.substring(body.indexOf("m=") + 2);
                            Log.e(TAG, "onReceivedMessage 是否是TCP推流模式：" + m.contains("TCP/RTP/AVP") + "  m: " + m);

                            String InviteResponseBody = "v=0\n" +
                                    "o=" + GB28181Params.getLocalSIPDeviceId() + " 0 0 IN IP4 " + GB28181Params.getLocalSIPIPAddress() + "\n" +
                                    "s=" + sdp.getSessionName().getValue() + "\n" +
                                    "c=IN IP4 " + GB28181Params.getLocalSIPIPAddress() + "\n" +
                                    "t=0 0\n" +
                                    "m=" + m.substring(0, m.indexOf("96") + 2) + "\n" +
//                                    "m=video " + port + " TCP/RTP/AVP 96\n" +
                                    "a=sendonly\n" +
                                    "a=rtpmap:96 PS/90000\n" +
                                    "a=connection:new\n" +
                                    "a=streamnumber:0\n" +
                                    "a=sendonly\n" +
                                    "a=setup:active\n" +
                                    "y=" + y + "";
                            //endregion
                            GB28181Params.setMediaServerProtol(m.contains("TCP/RTP/AVP") ? 0 : 1);
                            GB28181Params.setMediaServerIPAddress(address);
                            GB28181Params.setMediaServerPort(port);
                            GB28181Params.setSsrc(y);
                            Log.e(TAG, "解析sdp数据:收到INVATE  ADDRESS=" + GB28181Params.getMediaServerIPAddress() + ";port=" + GB28181Params.getMediaServerPort() + ";ssrc=" + y + ";isUdp:" + (GB28181Params.getMediaServerProtol() == 1));
                            Message InviteResponse = MessageFactory.createResponse(message, 200, SipResponses.reasonOf(200), SipProvider.pickTag(),
                                    new NameAddress(new SipURL(GB28181Params.getLocalSIPDeviceId(), GB28181Params.getLocalSIPIPAddress())), "Application/Sdp", InviteResponseBody);
                            InviteResponse.setUserAgentHeader(new UserAgentHeader(GB28181Params.defaultUserAgent));
                            sipProvider.sendMessage(InviteResponse, GB28181Params.defaultProtol, GB28181Params.getSIPServerIPAddress(), GB28181Params.getRemoteSIPServerPort(), 0);
                            break;
                        case "palyback":

                            break;
                        case "download":
                            break;
                    }
                }
            }
            //开始推流
            else if (message.isAck()) {
                Log.e(TAG, "收到Ack 开始推流  CurDevicePlayMediaState: " + GB28181Params.getCurDevicePlayMediaState());
                if (GB28181Params.getCurDevicePlayMediaState() == 0) {
                    //调用ZLM进行推流
                    if (gb28181CallBack != null) {
                        gb28181CallBack.onStartRtp(GB28181Params.getSsrc(), GB28181Params.getMediaServerIPAddress(), GB28181Params.getMediaServerPort(), GB28181Params.getMediaServerProtol());
                    }
                }
            }
            //关闭推流
            else if (message.isBye()) {
                //200 OK
                Message ByeResponse = MessageFactory.createResponse(message, 200, SipResponses.reasonOf(200), null);
                ByeResponse.setUserAgentHeader(new UserAgentHeader(GB28181Params.defaultUserAgent));
                sipProvider.sendMessage(ByeResponse, GB28181Params.defaultProtol, GB28181Params.getSIPServerIPAddress(), GB28181Params.getRemoteSIPServerPort(), 0);

                Log.e(TAG, "收到Bye 关闭推流  CurDevicePlayMediaState: " + GB28181Params.getCurDevicePlayMediaState());
                GB28181Params.setCurDevicePlayMediaState(0);

                if (GB28181Params.getCurDevicePlayMediaState() == 1) {
                    //关闭ZLM推流
                    if (gb28181CallBack != null) {
                        gb28181CallBack.onStopRtp(GB28181Params.getSsrc());
                    }
                }
            }
        }
    };


    /**
     * 心跳
     */
    private void GB28181_KeepAlive() {
        if (sipProvider != null && GB28181Params.CurGBState == 1) {
            NameAddress to = new NameAddress(new SipURL(GB28181Params.getSIPServerIPAddress(), GB28181Params.getSIPServerIPAddress()));
            NameAddress from = new NameAddress(new SipURL(GB28181Params.getLocalSIPDeviceId(), GB28181Params.getLocalSIPIPAddress()));
            String body = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                    "    <Notify>\n" +
                    "      <CmdType>Catalog</CmdType>\n" +
                    "      <SN>" + keepAliveSN++ + "</SN>\n" +
                    "      <DeviceID>" + GB28181Params.getLocalSIPDeviceId() + "</DeviceID>\n" +
                    "      <Status>OK</Status>\n" +
                    "    </Notify>";
            Message message = MessageFactory.createMessageRequest(sipProvider, to, from, null, XMLUtil.XML_MANSCDP_TYPE, body);
            sipProvider.sendMessage(message, GB28181Params.defaultProtol, GB28181Params.getSIPServerIPAddress(), GB28181Params.getRemoteSIPServerPort(), 0);
        }
    }

    /**
     * 设备信息
     */
    private void sendDeviceInfo() {
        if (sipProvider != null && GB28181Params.CurGBState == 1) {
            NameAddress to = new NameAddress(new SipURL(GB28181Params.getSIPServerIPAddress(), GB28181Params.getSIPServerIPAddress()));
            NameAddress from = new NameAddress(new SipURL(GB28181Params.getLocalSIPDeviceId(), GB28181Params.getLocalSIPIPAddress()));
            String body = "<?xml version=\"1.0\" encoding=\"GB2312\"?>\n" +
                    "    <Response>\n" +
                    "      <CmdType>DeviceInfo</CmdType>\n" +
                    "      <SN>" + keepAliveSN++ + "</SN>\n" +
                    "      <DeviceID>" + GB28181Params.getLocalSIPDeviceId() + "</DeviceID>\n" +
                    "      <Status>OK</Status>\n" +
                    "      <DeviceName>gb28181-client</DeviceName>\n" +
                    "      <Manufacturer>Runde-cap</Manufacturer>\n" +
                    "      <Model>TC-2808AN-HD</Model>\n" +
                    "      <Firmware>V2.1,build091111</Firmware>\n" +
                    "      <Channel>1</Channel>\n" +
                    "    </Response>";
            Message message = MessageFactory.createMessageRequest(sipProvider, to, from, null, XMLUtil.XML_MANSCDP_TYPE, body);
            sipProvider.sendMessage(message, GB28181Params.defaultProtol, GB28181Params.getSIPServerIPAddress(), GB28181Params.getRemoteSIPServerPort(), 0);
        }
    }
}
