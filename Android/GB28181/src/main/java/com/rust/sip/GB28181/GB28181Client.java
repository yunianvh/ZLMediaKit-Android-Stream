package com.rust.sip.GB28181;

import android.util.Log;

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

    /**
     * 初始化配置信息
     */
    public void GB28181Init() {
        GB28181Params.setSIPServerIPAddress("192.168.108.43");//SIP服务器地址
        GB28181Params.setRemoteSIPServerPort(5060);//SIP服务器端口
        GB28181Params.setLocalSIPIPAddress("192.168.108.15");//本机地址
        GB28181Params.setRemoteSIPServerID("44010200492000000001");
        GB28181Params.setRemoteSIPServerSerial("4401020049");
        GB28181Params.setLocalSIPPort(5090);//本机端口
        GB28181Params.setCameraHeigth(720);
        GB28181Params.setCameraWidth(1280);
        GB28181Params.setPassword("admin123");//密码
        GB28181Params.setLocalSIPDeviceId("340200000111000000002");
        GB28181Params.setLocalSIPMediaId("340200000132000000002");
        GB28181Params.setCurGBState(0);
        GB28181Params.setCurDeviceDownloadMeidaState(0);
        GB28181Params.setCurDeviceDownloadMeidaState(0);
        GB28181Params.setCurDevicePlayMediaState(0);
        GB28181Params.setCameraState(0);
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
            NameAddress contact = new NameAddress(new SipURL(GB28181Params.getLocalSIPDeviceId(), GB28181Params.getLocalSIPIPAddress()));
            Message message = MessageFactory.createRequest(sipProvider, SipMethods.REGISTER, new SipURL(GB28181Params.getRemoteSIPServerID(), GB28181Params.getRemoteSIPServerSerial()), to, from, contact, null);
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
            Log.e(TAG, "onReceivedMessage 回复信息:" + message);
            switch (message.getCSeqHeader().getMethod()) {
                case SipMethods.REGISTER:
                    if (message.getStatusLine().getCode() == 401) {
                        NameAddress to = new NameAddress(new SipURL(GB28181Params.getLocalSIPDeviceId(), GB28181Params.getLocalSIPIPAddress()));
                        NameAddress from = new NameAddress(new SipURL(GB28181Params.getLocalSIPDeviceId(), GB28181Params.getLocalSIPIPAddress()));
                        NameAddress contact = new NameAddress(new SipURL(GB28181Params.getLocalSIPDeviceId(), GB28181Params.getLocalSIPIPAddress()));
                        Message res = MessageFactory.createRegisterRequest(sipProvider, to, from, contact, null, null);
                        res.setUserAgentHeader(new UserAgentHeader(GB28181Params.defaultUserAgent));
                        AuthorizationHeader ah = new AuthorizationHeader("Digest");
                        ah.addUsernameParam(GB28181Params.getLocalSIPDeviceId());
                        ah.addRealmParam(message.getWwwAuthenticateHeader().getRealmParam());
                        ah.addNonceParam(message.getWwwAuthenticateHeader().getNonceParam());
                        ah.addUriParam(res.getRequestLine().getAddress().toString());
                        ah.addQopParam(message.getWwwAuthenticateHeader().getQopParam());
                        ah.addAlgorithParam("MD5");
                        ah.addCnonceParam("611eafd2-3b2e-4453-9d08-f61c2ad1b51c");
                        ah.addNcParam("00000001");
                        String response = (new DigestAuthentication(SipMethods.REGISTER,
                                ah, null, GB28181Params.getPassword())).getResponse();
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
                    break;
                case SipMethods.ACK:
                    break;
                case SipMethods.BYE:
                    break;
            }
        }
        //服务器主动发起的信令
        else if (message.isRequest()) {
            Log.e(TAG, "onReceivedMessage 接收后台信息:" + message);
            //心跳等Message
            if (message.isMessage()) {
                if (message.hasBody()) {
                    String body = message.getBody();
                    String sn = body.substring(body.indexOf("<SN>") + 4, body.indexOf("</SN>"));
                    String cmdType = body.substring(body.indexOf("<CmdType>") + 9, body.indexOf("</CmdType>"));
                    Log.e(TAG, "onReceivedMessage cmdType: " + cmdType);
                    //查询设备信息
                    if ("DeviceInfo".equals(cmdType)) {
                        sendDeviceInfo();
                    }

                    //设备通道
                    if (message.getBodyType().toLowerCase().equals("application/manscdp+xml")) {
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
                            //ToDo 解析控制指令
                            Message DeviceControlResponse = MessageFactory.createResponse(message, 200, SipResponses.reasonOf(200), null);
                            DeviceControlResponse.setUserAgentHeader(new UserAgentHeader(GB28181Params.defaultUserAgent));
                            sipProvider.sendMessage(DeviceControlResponse, GB28181Params.defaultProtol, GB28181Params.getSIPServerIPAddress(), GB28181Params.getRemoteSIPServerPort(), 0);
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
                            String ssrc = body.substring(body.indexOf("y=") + 2, body.indexOf("y=") + 12);
                            GB28181Params.setMediaServerIPAddress(address);
                            GB28181Params.setMediaServerPort(port);
                            GB28181Params.setSsrc(ssrc);
                            Log.i("sdp", ":收到INVATE,ADDRESS=" + address + ";port=" + port + "；ssrc=" + ssrc);

                            //region InviteResponseBody
                            String InviteResponseBody = "v=0\n" +
                                    "o=" + GB28181Params.getLocalSIPDeviceId() + " 0 0 IN IP4 " + GB28181Params.getLocalSIPIPAddress() + "\n" +
                                    "s=" + sdp.getSessionName().getValue() + "\n" +
                                    "c=IN IP4 " + GB28181Params.getLocalSIPIPAddress() + "\n" +
                                    "t=0 0\n" +
                                    "m=video " + port + " RTP/AVP 96\n" +
                                    "a=sendonly\n" +
                                    "a=rtpmap:96 PS/90000\n" +
                                    "y=" + ssrc + "";
                            //endregion

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
                if (GB28181Params.getCurDevicePlayMediaState() == 0) {
                    //调用ZLM进行推流
                    if (gb28181CallBack != null) {
                        gb28181CallBack.onStartRtp(GB28181Params.getSsrc(), GB28181Params.getMediaServerIPAddress(), GB28181Params.getMediaServerPort(), 0);
                    }
                    GB28181Params.setCurDevicePlayMediaState(1);
                    GB28181Params.setCameraState(GB28181Params.getCameraState() + 1);
                }
            }
            //关闭推流
            else if (message.isBye()) {
                if (GB28181Params.CurDevicePlayMediaState == 1) {
                    //200 OK
                    Message ByeResponse = MessageFactory.createResponse(message, 200, SipResponses.reasonOf(200), null);
                    ByeResponse.setUserAgentHeader(new UserAgentHeader(GB28181Params.defaultUserAgent));
                    sipProvider.sendMessage(ByeResponse, GB28181Params.defaultProtol, GB28181Params.getSIPServerIPAddress(), GB28181Params.getRemoteSIPServerPort(), 0);

                    GB28181Params.setCurDevicePlayMediaState(1);
                    if (GB28181Params.getCameraState() > 0) {
                        GB28181Params.setCameraState(GB28181Params.getCameraState() - 1);
                    }

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
            NameAddress contact = new NameAddress(new SipURL(GB28181Params.getLocalSIPDeviceId(), GB28181Params.getLocalSIPIPAddress()));
            String body = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                    "    <Notify>\n" +
                    "      <CmdType>Catalog</CmdType>\n" +
                    "      <SN>" + String.valueOf(keepAliveSN++) + "</SN>\n" +
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
            Log.i("KeepAlive", "上报设备信息：" + message);
            sipProvider.sendMessage(message, GB28181Params.defaultProtol, GB28181Params.getSIPServerIPAddress(), GB28181Params.getRemoteSIPServerPort(), 0);
        }
    }
}
