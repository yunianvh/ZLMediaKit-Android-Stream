package com.stream.http.bean

/**
 * Created by 玉念聿辉.
 * User: 吴明辉
 * Date: 2023/1/2
 * Time: 16:43
 */
class ApiDataBean(
    val code: Int,
    val local_port: Int,
    val data: DataBean?,
    val result: Boolean,
    val status: Boolean
) {
    class DataBean(val key: String?, val flag: Boolean, val originUrl: Boolean) {
    }
}