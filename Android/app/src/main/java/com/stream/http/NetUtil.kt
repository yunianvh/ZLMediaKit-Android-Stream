package com.stream.http

import android.os.Environment
import android.text.TextUtils
import android.util.Log
import com.drake.net.Get
import com.drake.net.NetConfig
import com.drake.net.okhttp.trustSSLCertificate
import com.drake.net.time.Interval
import com.drake.net.utils.scopeNet
import com.google.gson.Gson
import com.zlmediakit.jni.ZLMediaKit
import java.util.concurrent.TimeUnit

/**
 * Created by 玉念聿辉.
 * User: 吴明辉
 * Date: 2023/1/2
 * Time: 17:18
 */
class NetUtil private constructor() {
    companion object {
        val instance by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
            NetUtil()
        }
    }

    fun init() {
        NetConfig.logEnabled = false
        NetConfig.converter = GsonConverter()
        NetConfig.init("http://127.0.0.1") {
            trustSSLCertificate()
        }
    }

    private val API_GET_API_LIST = "http://127.0.0.1:8080/index/api/getApiList"
    private val ADD_STREAM_PROXY = "http://127.0.0.1:8080/index/api/addStreamProxy"
    private val DEL_STREAM_PROXY = "http://127.0.0.1:8080/index/api/delStreamProxy"

    private val API_START_PUSH = "http://127.0.0.1:8080/index/api/addStreamPusherProxy"
    private val API_STOP_PUSH = "http://127.0.0.1:8080/index/api/delStreamPusherProxy"
    private val API_GET_MEDIA_LIST = "http://127.0.0.1:8080/index/api/getMediaList"
    private val API_GET_SNAP = "http://127.0.0.1:8080/index/api/getSnap"
    private val API_START_RECORD = "http://127.0.0.1:8080/index/api/startRecord"
    private val API_STOP_RECORD = "http://127.0.0.1:8080/index/api/stopRecord"
    private val API_IS_RECORDING = "http://127.0.0.1:8080/index/api/isRecording"

    //拉流代理的key
    private var STREAM_PROXY_KEY: String? = null
    private var checkZlmInterval: Interval? = null

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
                Log.e("CameraUtil", "checkZlmStatus zlm状态:${Gson().toJson(res)}")
                if (res.code != 0) {
                    //zlm状态异常，重新初始化
                    ZLMediaKit.startDemo(
                        Environment.getExternalStoragePublicDirectory("").toString()
                    )
                }
            }.catch {
                Log.e("CameraUtil", "checkZlmStatus zlm状态异常，重新初始化:$it")
                //zlm状态异常，重新初始化
                ZLMediaKit.startDemo(Environment.getExternalStoragePublicDirectory("").toString())
            }
        }?.start()
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
        }.catch {
            STREAM_PROXY_KEY = null
            Log.e("CameraUtil", "startProxy :$it")
        }
        Log.e("CameraUtil", "startProxy STREAM_PROXY_KEY: $STREAM_PROXY_KEY")
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
        }.catch {

        }
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
            Log.e("CameraUtil", "startRecord 开始录制: ${Gson().toJson(result)}")
        }.catch {
            Log.e("CameraUtil", "startRecord 开始录制 异常: $it")
        }
    }
}