package com.stream.http

import android.os.Environment
import android.text.TextUtils
import com.drake.net.Get
import com.drake.net.NetConfig
import com.drake.net.okhttp.trustSSLCertificate
import com.drake.net.time.Interval
import com.drake.net.utils.scopeNet
import com.google.gson.Gson
import com.rust.sip.GB28181.gb28181.GB28181Params
import com.stream.http.bean.ApiDataBean
import com.stream.http.bean.GsonConverter
import com.stream.http.bean.PushBaseListBean
import com.stream.http.bean.ZlmStatusBean
import com.stream.util.FLogUtil
import com.zlmediakit.jni.ZLMediaKit
import java.util.concurrent.TimeUnit

/**
 * Created by 玉念聿辉.
 * User: 吴明辉
 * Date: 2023/1/2
 * Time: 17:18
 */
class NetUtil private constructor() {
    private var zlmListener: ZlmListener? = null
    private val TAG = FLogUtil::class.java.simpleName

    companion object {
        val instance by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
            NetUtil()
        }
    }

    fun init(zlmListener: ZlmListener) {
        this.zlmListener = zlmListener
        NetConfig.logEnabled = false
        NetConfig.converter = GsonConverter()
        NetConfig.init("http://127.0.0.1") {
            trustSSLCertificate()
        }
    }

    private val API_GET_API_LIST = "http://127.0.0.1:8080/index/api/getApiList"
    private val API_GET_RESTART = "http://127.0.0.1/index/api/restartServer"

    //拉流代理的key
    private var STREAM_PROXY_KEY: String? = null
    private val ADD_STREAM_PROXY = "http://127.0.0.1:8080/index/api/addStreamProxy"
    private val DEL_STREAM_PROXY = "http://127.0.0.1:8080/index/api/delStreamProxy"

    //推流
    private var PUSH_KEY: String? = null
    private var pushStatus = false
    private var proxyUrl: String? = null
    private val API_START_PUSH = "http://127.0.0.1:8080/index/api/addStreamPusherProxy"
    private val API_STOP_PUSH = "http://127.0.0.1:8080/index/api/delStreamPusherProxy"
    private val API_GET_MEDIA_LIST = "http://127.0.0.1:8080/index/api/getMediaList"

    //录制
    private var recordStatus = false
    private val API_START_RECORD = "http://127.0.0.1:8080/index/api/startRecord"
    private val API_STOP_RECORD = "http://127.0.0.1:8080/index/api/stopRecord"
    private val API_IS_RECORDING = "http://127.0.0.1:8080/index/api/isRecording"

    //GB28181推流
    private val API_START_SEND_RTP = "http://127.0.0.1:8080/index/api/startSendRtp"
    private val API_STOP_SEND_RTP = "http://127.0.0.1:8080/index/api/stopSendRtp"

    private var checkZlmInterval: Interval? = null
    private var checkPushInterval: Interval? = null
    private var checkRecordInterval: Interval? = null

    /**
     * 循环检测zlm是否可用
     */
    fun checkZlmStatus() {
        if (checkZlmInterval == null) {
            checkZlmInterval = Interval(10, TimeUnit.SECONDS)
        }
        checkZlmInterval?.subscribe {
            scopeNet {
                val res = Get<ZlmStatusBean>(API_GET_API_LIST) {
                    param("secret", "035c73f7-bb6b-4889-a715-d9eb2d1925cc")
                }.await()
                FLogUtil.e(TAG, "checkZlmStatus zlm状态:${res.code == 0}")
                if (res.code != 0) {
                    //zlm状态异常，重新初始化
                    ZLMediaKit.startDemo(
                        Environment.getExternalStoragePublicDirectory("").toString()
                    )
                }
            }.catch {
                FLogUtil.e(TAG, "checkZlmStatus zlm状态异常，重新初始化:$it")
                //zlm状态异常，重新初始化
                ZLMediaKit.startDemo(Environment.getExternalStoragePublicDirectory("").toString())
            }
        }?.start()
    }

    /**
     * zlm重启
     */
    fun restartServer() {
        scopeNet {
            Get<ApiDataBean>(API_GET_RESTART) {
                param("secret", "035c73f7-bb6b-4889-a715-d9eb2d1925cc")
            }.await()
        }.catch {}
    }

    /**
     * zlm开始拉流
     */
    fun startProxy(path: String?, url: String) {
        scopeNet {
            STREAM_PROXY_KEY = Get<ApiDataBean>(ADD_STREAM_PROXY) {
                param("secret", "035c73f7-bb6b-4889-a715-d9eb2d1925cc")
                param("vhost", "__defaultVhost__")
                param("app", "live")
                param("stream", "1234")
                param("url", url)
//                param("rtp_type", 1)
//                param("enable_hls", 0)
//                param("enable_mp4", 1)
//                param("mp4_save_path", path)
            }.await().data?.key
            proxyUrl = url
        }.catch {
            STREAM_PROXY_KEY = null
            FLogUtil.e(TAG, "startProxy 异常:$it")
        }
        FLogUtil.e(TAG, "startProxy STREAM_PROXY_KEY: $STREAM_PROXY_KEY")
    }

    /**
     * zlm停止拉流
     */
    fun stopProxy() {
        if (TextUtils.isEmpty(STREAM_PROXY_KEY)) return
        scopeNet {
            Get<ApiDataBean>(DEL_STREAM_PROXY) {
                param("secret", "035c73f7-bb6b-4889-a715-d9eb2d1925cc")
                param("vhost", "__defaultVhost__")
                param("key", STREAM_PROXY_KEY)
            }.await()
        }.catch {}
    }

    /**
     * 开始推流
     */
    fun startPush(url: String) {
        val schema = if (url.contains("rtmp")) "rtmp" else "rtsp"
        scopeNet {
            var result = Get<ApiDataBean>(API_START_PUSH) {
                param("secret", "035c73f7-bb6b-4889-a715-d9eb2d1925cc")
                param("vhost", "__defaultVhost__")
                param("schema", schema)
                param("app", "live")
                param("stream", "1234")
                param("dst_url", url)
//                param("retry_count", 3)//转推失败重试次数，默认无限重试
//                param("rtp_type", 0)//rtsp拉流时，拉流方式，0：tcp，1：udp，2：组播
//                param("timeout_sec", 60 * 1)//拉流超时时间，单位秒，float类型
            }.await()
            FLogUtil.e(TAG, "startPush 开始推流: ${Gson().toJson(result)}")
            if (result.code == 0) {
                PUSH_KEY = result.data?.key
                zlmListener?.onPushStatus(true)
                pushStatus = true
                checkPushStatus(schema)
            }
        }.catch {
            FLogUtil.e(TAG, "startPush 开始推流 异常: $it")
        }
    }

    /**
     * 关闭推流
     */
    fun stopPush() {
        if (TextUtils.isEmpty(API_STOP_PUSH)) return
        scopeNet {
            var result = Get<ApiDataBean>(API_STOP_PUSH) {
                param("secret", "035c73f7-bb6b-4889-a715-d9eb2d1925cc")
                param("key", PUSH_KEY)
            }.await()
            FLogUtil.e(TAG, "stopPush 关闭推流: ${Gson().toJson(result)}")
            checkPushInterval?.cancel()
            checkPushInterval = null
            zlmListener?.onPushStatus(false)
            pushStatus = false
        }.catch {
            FLogUtil.e(TAG, "stopPush 关闭推流 异常: $it")
        }
    }

    /**
     * 循环检测是否在推流
     */
    private fun checkPushStatus(schema: String) {
        if (checkPushInterval == null) {
            checkPushInterval = Interval(10, TimeUnit.SECONDS)
        }
        checkPushInterval?.subscribe {
            scopeNet {
                val res = Get<PushBaseListBean>(API_GET_MEDIA_LIST) {
                    param("schema", schema)
                    param("vhost", "__defaultVhost__")
                    param("app", "live")
                    param("stream", "1234")
                    param("secret", "035c73f7-bb6b-4889-a715-d9eb2d1925cc")
                }.await()
                FLogUtil.e(TAG, "checkPushStatus 循环检测是否在推流:${Gson().toJson(res)}")
                pushStatus = if (res.data[0].originUrl.equals(proxyUrl)) {
                    if (!pushStatus) zlmListener?.onPushStatus(true)
                    true
                } else {
                    if (pushStatus) zlmListener?.onPushStatus(false)
                    false
                }
            }.catch {
                FLogUtil.e(TAG, "checkPushStatus 循环检测是否在推流 异常:$it")
            }
        }?.start()
    }

    /**
     * 开始录制
     */
    fun startRecord(path: String?) {
        scopeNet {
            var result = Get<ApiDataBean>(API_START_RECORD) {
                param("type", "1")
                param("vhost", "__defaultVhost__")
                param("app", "live")
                param("stream", "1234")
                param("max_second", 60 * 1)
                param("customized_path", path)
                param("secret", "035c73f7-bb6b-4889-a715-d9eb2d1925cc")
            }.await()
            FLogUtil.e(TAG, "startRecord 开始录制: ${Gson().toJson(result)}")
            recordStatus = result.result
            zlmListener?.onRecordStatus(result.result)
            if (result.result) checkRecordStatus()
        }.catch {
            FLogUtil.e(TAG, "startRecord 开始录制 异常: $it")
            if (!recordStatus) zlmListener?.onRecordStatus(false)
            recordStatus = false
        }
    }

    /**
     * 关闭录制
     */
    fun stopRecord() {
        scopeNet {
            var result = Get<ApiDataBean>(API_STOP_RECORD) {
                param("type", "1")
                param("vhost", "__defaultVhost__")
                param("app", "live")
                param("stream", "1234")
                param("secret", "035c73f7-bb6b-4889-a715-d9eb2d1925cc")
            }.await()
            FLogUtil.e(TAG, "stopRecord 关闭录制: ${Gson().toJson(result)}")
            if (result.result) {
                zlmListener?.onRecordStatus(false)
                recordStatus = false
            }
            checkRecordInterval?.cancel()
            checkRecordInterval = null
        }.catch {
            FLogUtil.e(TAG, "stopRecord 关闭录制 异常: $it")
        }
    }

    /**
     * 循环检测是否在录制
     */
    private fun checkRecordStatus() {
        if (checkRecordInterval == null) {
            checkRecordInterval = Interval(10, TimeUnit.SECONDS)
        }
        checkRecordInterval?.subscribe {
            scopeNet {
                val res = Get<ApiDataBean>(API_IS_RECORDING) {
                    param("type", "1")
                    param("vhost", "__defaultVhost__")
                    param("app", "live")
                    param("stream", "1234")
                    param("secret", "035c73f7-bb6b-4889-a715-d9eb2d1925cc")
                }.await()
                FLogUtil.e(TAG, "checkRecordStatus 录制状态:${Gson().toJson(res)}")
                recordStatus = if (res.status) {
                    if (!recordStatus) zlmListener?.onRecordStatus(true)
                    true
                } else {
                    if (recordStatus) zlmListener?.onRecordStatus(false)
                    false
                }
            }.catch {
                FLogUtil.e(TAG, "checkRecordStatus 录制状态 异常:$it")
            }
        }?.start()
    }

    /**
     * 作为GB28181客户端，启动ps-rtp推流
     * 注：这里仅提供推拉流功能，实际开发需要对接国标标准信令
     */
    fun startSendRtp(ssrc: String, url: String, port: Int, is_udp: Int) {
        FLogUtil.e(TAG, "startSendRtp 启动ps-rtp推流 url: $url  port:$port  is_udp:${is_udp==1}  ssrc:$ssrc")
        scopeNet {
            var result = Get<ApiDataBean>(API_START_SEND_RTP) {
                param("secret", "035c73f7-bb6b-4889-a715-d9eb2d1925cc")
                param("vhost", "__defaultVhost__")
                param("app", "live")
                param("stream", "1234")
                param("ssrc", ssrc.toInt())
                param("dst_url", url)
                param("dst_port", port)
                param("is_udp", is_udp)//1:udp 0:tcp  在C中0为false，1为true
//                param("src_port", GB28181Params.getLocalSIPPort())
//                param("pt", 96)//rtp的pt（uint8_t）,不传时默认为96
//                param("use_ps", 1)//为1时，负载为ps；为0时，为es；
//                param("only_audio", 0)//为1时，发送音频；为0时，发送视频；
            }.await()
            FLogUtil.e(TAG, "startSendRtp 启动ps-rtp推流: ${Gson().toJson(result)}")
            GB28181Params.setCurDevicePlayMediaState(if (result.code >= 0) 1 else 0)
        }.catch {
            FLogUtil.e(TAG, "startSendRtp 启动ps-rtp推流 异常: $it")
            GB28181Params.setCurDevicePlayMediaState(0)
        }
    }

    /**
     * 作为GB28181客户端，关闭ps-rtp推流
     */
    fun stopSendRtp(ssrc: String) {
        scopeNet {
            var result = Get<ApiDataBean>(API_STOP_SEND_RTP) {
                param("secret", "035c73f7-bb6b-4889-a715-d9eb2d1925cc")
                param("vhost", "__defaultVhost__")
                param("app", "live")
                param("stream", "1234")
                param("ssrc", ssrc)
            }.await()
            FLogUtil.e(TAG, "stopSendRtp 关闭ps-rtp推流: ${Gson().toJson(result)}")
        }.catch {
            FLogUtil.e(TAG, "stopSendRtp 关闭ps-rtp推流 异常: $it")
        }
    }

    /**
     * 回调监听
     */
    interface ZlmListener {
        fun onRecordStatus(status: Boolean)
        fun onPushStatus(status: Boolean)
    }
}