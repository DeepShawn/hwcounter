package com.deepshawn.hwcounter.recog

import android.os.Build

/** 汉字 → 无声调拼音（系统 ICU，Android 10+）；更早的系统返回 null，退化为只按原字匹配 */
object Pinyin {
    private val tr: android.icu.text.Transliterator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { android.icu.text.Transliterator.getInstance("Han-Latin; Latin-ASCII; Lower") }.getOrNull()
        } else null
    }
    private val cache = HashMap<Char, String?>()

    val available: Boolean get() = tr != null

    @Synchronized
    fun of(c: Char): String? {
        if (c.code < 0x3400 || c.code > 0x9FFF) return null
        return cache.getOrPut(c) {
            tr?.transliterate(c.toString())?.trim()?.takeIf { s -> s.isNotEmpty() && s.all { it in 'a'..'z' } }
        }
    }
}
