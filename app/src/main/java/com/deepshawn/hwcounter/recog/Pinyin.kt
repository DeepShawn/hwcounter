package com.deepshawn.hwcounter.recog

import android.os.Build

/** 汉字 → 无声调拼音（系统 ICU，Android 10+），并补充名字常见多音字的其它读音 */
object Pinyin {
    private val tr: android.icu.text.Transliterator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { android.icu.text.Transliterator.getInstance("Han-Latin; Latin-ASCII; Lower") }.getOrNull()
        } else null
    }
    private val cache = HashMap<Char, List<String>>()

    val available: Boolean get() = tr != null

    @Synchronized
    fun readings(c: Char): List<String> {
        if (c.code < 0x3400 || c.code > 0x9FFF) return emptyList()
        return cache.getOrPut(c) {
            val main = tr?.transliterate(c.toString())?.trim()?.takeIf { s -> s.isNotEmpty() && s.all { it in 'a'..'z' } }
            (listOfNotNull(main) + Polyphones.of(c)).distinct()
        }
    }
}
