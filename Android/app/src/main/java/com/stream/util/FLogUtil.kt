package com.stream.util

import android.content.Context
import android.icu.text.SimpleDateFormat
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.ParseException
import java.util.*

/**
 * Created by 玉念聿辉.
 * User: 吴明辉
 * Date: 2023/3/13
 * Time: 9:38
 */
object FLogUtil {
    private val TAG = FLogUtil::class.java.simpleName
    private var isLog = true
    private var deviceId: String? = null
    private var logFile: File? = null
    private val df = SimpleDateFormat("yyyyMMddHHmmss")
    private val s = SimpleDateFormat("HH:mm:ss")

    /**
     * 初始化
     */
    fun init(context: Context, isLog: Boolean) {
        init(context, isLog, null, null)
    }

    fun init(context: Context, isLog: Boolean, deviceId: String) {
        init(context, isLog, deviceId, null)
    }

    fun init(context: Context, isLog: Boolean, deviceId: String?, path: File?) {
        this.isLog = isLog
        this.deviceId = deviceId
        logFile = path ?: File(context.getExternalFilesDir(null)!!.absolutePath + "/log/")
        if (!logFile!!.exists()) logFile!!.mkdir()
        createLog()
        Log.e(TAG, "本地日志路径：" + logFile!!.absolutePath)
        val d = Date()
        val dateStr = df.format(d)
        if (logFile!!.listFiles() != null && logFile!!.listFiles().isNotEmpty()) {
            for (f in logFile!!.listFiles()) {
                val fileTime = f.name.split("-".toRegex()).dropLastWhile { it.isEmpty() }
                    .toTypedArray()
                if (fileTime.size in 2..3) {
                    if (calDateDifferent(
                            dateStr,
                            if (f.name.contains("ERROR")) fileTime[2] else fileTime[1]
                        ) > 1
                    ) {
                        f.delete()
                        writeLogFile(TAG, "该日志已经保存大于两天直接删除: " + f.name)
                    }
                }
            }
        }
    }

    fun e(msg: String) {
        if (isLog) {
            Log.e(TAG, msg)
            writeLogFile(TAG, msg)
        }
    }

    fun e(tag: String, msg: String) {
        if (isLog) {
            Log.e(tag, msg)
            writeLogFile(tag, msg)
        }
    }

    fun i(tag: String, msg: String) {
        if (isLog) {
            Log.i(tag, msg)
            writeLogFile(tag, msg)
        }
    }

    fun i(msg: String) {
        if (isLog) {
            Log.i(TAG, msg)
            writeLogFile(TAG, msg)
        }
    }

    fun d(tag: String, msg: String) {
        if (isLog) {
            Log.i(tag, msg)
            writeLogFile(tag, msg)
        }
    }

    fun d(msg: String) {
        if (isLog) {
            Log.i(TAG, msg)
            writeLogFile(TAG, msg)
        }
    }

    /**
     * 写入文件
     *
     * @param msg
     */
    var file: File? = null
    private fun writeLogFile(tag: String, msg: String) {
        if (logFile == null) return
        synchronized(Any()) {
            try {
                val dateStr = s.format(Date())
                val fw: FileWriter = if (file!!.exists()) {
                    if (file!!.length() > 1024 * 1024 * 2) {
                        createLog()
                        FileWriter(file, false)
                    } else {
                        FileWriter(file, true)
                    }
                } else {
                    createLog()
                    FileWriter(file, false)
                }
                fw.write(String.format("[%s] [%s] %s", dateStr, tag, msg))
                fw.write(13)
                fw.write(10)
                fw.flush()
                fw.close()
            } catch (ex: Throwable) {
                ex.printStackTrace()
            }
        }
    }

    /**
     * 创建日志文件
     */
    private fun createLog() {
        file = File(
            "${logFile!!.absolutePath}/" + (if (deviceId == null || "" == deviceId) "" else "$deviceId-") + "${
                df.format(Date())
            }.txt"
        )
    }

    /**
     * 计算的时间差
     *
     * @param date1
     * @param date2
     * @return
     */
    private fun calDateDifferent(date1: String, date2: String): Long {
        try {
            val d1 = df.parse(date1)
            val d2 = df.parse(date2)
            val diff = d1.time - d2.time
            return diff / (1000 * 60 * 60 * 24)
        } catch (e: ParseException) {
            e.printStackTrace()
        }
        return 0
    }
}