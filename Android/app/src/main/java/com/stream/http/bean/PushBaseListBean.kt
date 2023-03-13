package com.stream.http.bean

/**
 * Created by 玉念聿辉.
 * User: 吴明辉
 * Date: 2023/3/11
 * Time: 18:19
 */
class PushBaseListBean(val data: List<DataBean>, val code: Int) {
    class DataBean(val originUrl: String?) {}
}