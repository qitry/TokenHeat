package app.anylyralabs.lyraproxy.qoder

import android.util.Base64

/**
 * Qoder 的请求体编码。
 *
 * 上游用一套自定义 Base64 字母表，并把标准 Base64 结果按三段轮转：
 * 末段在前、中段居中、首段在后。不编码直接发明文 JSON，
 * 服务端会在 `CustomBase64Util.decode0` 处抛异常。
 */
object QoderBodyEncoder {

    private const val CUSTOM =
        "_doRTgHZBKcGVjlvpC,@aFSx#DPuNJme&i*MzLOEn)sUrthbf%Y^w.(kIQyXqWA!"
    private const val STANDARD =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    /** 建立 ASCII → 自定义字符 的映射表。 */
    private val table = IntArray(256) { it }.also { map ->
        STANDARD.forEachIndexed { index, ch -> map[ch.code] = CUSTOM[index].code }
        map['='.code] = '$'.code
    }

    fun encode(plain: ByteArray): ByteArray {
        val standard = Base64.encodeToString(plain, Base64.NO_WRAP)
        val length = standard.length
        if (length == 0) return ByteArray(0)
        val third = length / 3
        val out = ByteArray(length)
        var cursor = 0

        // 末段、中段、首段
        for (index in length - third until length) {
            out[cursor++] = table[standard[index].code].toByte()
        }
        for (index in third until length - third) {
            out[cursor++] = table[standard[index].code].toByte()
        }
        for (index in 0 until third) {
            out[cursor++] = table[standard[index].code].toByte()
        }
        return out
    }
}
