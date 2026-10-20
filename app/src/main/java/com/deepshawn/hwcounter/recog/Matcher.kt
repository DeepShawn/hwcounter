package com.deepshawn.hwcounter.recog

import android.graphics.Rect
import com.deepshawn.hwcounter.data.Student

/** 一段识别出的文字（含位置，可为空） */
data class TextPiece(val text: String, val box: Rect? = null)

enum class MatchSource(val label: String) { EXACT("学号"), FUZZY("学号≈"), NAME("姓名"), QR("二维码"), AI("AI") }

data class Hit(val studentId: String, val source: MatchSource, val raw: String, val box: Rect? = null)

data class MatchOutput(
    val hits: List<Hit>,
    val unknown: List<Pair<String, Rect?>>,
    /** 含数字、长度与学号相近、但没对上名单的短文字（如手写“3Y”），可能是一本没认清学号的作业 */
    val loose: List<Pair<String, Rect?>> = emptyList(),
)

/**
 * 把 OCR 文本与名单比对。
 * - 自动纠正 O→0、l→1、S→5、B→8 等常见误识
 * - 支持学号被空格隔开、学号嵌在更长字符串里（如“学号:20240105班”）
 * - 可选：一位之差的模糊匹配（唯一时才采纳）、姓名匹配
 */
class Matcher(
    private val roster: List<Student>,
    minLen: Int = 4,
    private val fuzzy: Boolean = true,
    private val matchNames: Boolean = true,
) {
    private val byNo: Map<String, Student> = roster.filter { it.no.isNotBlank() }.associateBy { canon(it.no) }
    private val noLens = byNo.keys.map { it.length }.toSet()
    /** 名单里有 2~3 位的短学号时自动放宽最小位数，否则永远匹配不上 */
    private val minLen: Int = minOf(minLen, byNo.keys.filter { k -> k.all { it.isDigit() } }.minOfOrNull { it.length } ?: minLen).coerceAtLeast(1)
    private val maxNoLen = byNo.keys.maxOfOrNull { it.length } ?: 8
    private val shortIds = maxNoLen <= 3
    private val names = roster.filter { it.name.length >= 2 }.sortedByDescending { it.name.length }

    companion object {
        private val confusable = mapOf(
            'O' to '0', 'o' to '0', 'D' to '0', 'Q' to '0', 'U' to '0',
            'I' to '1', 'l' to '1', 'i' to '1', '|' to '1', '!' to '1', 'L' to '1', 'T' to '7',
            'Z' to '2', 'z' to '2', 'S' to '5', 's' to '5', 'B' to '8', 'G' to '6', 'b' to '6',
            'g' to '9', 'q' to '9', 'A' to '4',
            // 手写体常见误识：开口的 4 被读成 y/Y/h/4 的变体，7 被读成 ?，1 被读成 )
            'y' to '4', 'Y' to '4', 'h' to '4', '?' to '7', ')' to '1', '(' to '1',
        )
        private val tokenRe = Regex("[A-Za-z0-9|!?()]+")
        private val digitsGap = Regex("(?<=\\d)[\\s\\-_.·]+(?=\\d)")

        fun canon(s: String): String = s.trim().uppercase().replace(" ", "")

        /** 全角数字/字母（中文 OCR 常输出）转半角 */
        fun halfWidth(s: String): String = buildString {
            for (ch in s) append(if (ch in '\uFF01'..'\uFF5E') (ch.code - 0xFEE0).toChar() else ch)
        }

        /**
         * 主要由数字组成的 token 才做字形纠正，避免把英文单词改坏：
         * 3 位及以上要求 ≥60% 是数字；2 位的（短学号常见，如手写“3Y”）只要含 1 个数字就纠正。
         */
        fun normalize(line: String): String {
            val fixed = tokenRe.replace(halfWidth(line)) { m ->
                val t = m.value
                val digits = t.count { it.isDigit() }
                val fix = when {
                    t.length >= 3 -> digits * 10 >= t.length * 6
                    t.length == 2 -> digits >= 1
                    else -> false
                }
                if (fix) t.map { confusable[it] ?: it }.joinToString("") else t
            }
            return digitsGap.replace(fixed, "")
        }
    }

    fun match(pieces: List<TextPiece>, barcodes: List<TextPiece> = emptyList()): MatchOutput {
        val hits = LinkedHashMap<String, Hit>()
        val unknown = ArrayList<Pair<String, Rect?>>()
        val loose = ArrayList<Pair<String, Rect?>>()

        fun add(h: Hit) {
            val old = hits[h.studentId]
            // 精确来源优先
            if (old == null || old.source.ordinal > h.source.ordinal) hits[h.studentId] = h
        }

        for (bc in barcodes) {
            val v = bc.text.trim().removePrefix("HW:").removePrefix("hw:")
            val st = byNo[canon(v)] ?: byNo[canon(v.substringBefore('|'))]
            if (st != null) add(Hit(st.id, MatchSource.QR, bc.text, bc.box)) else unknown += bc.text to bc.box
        }

        val alnumNos = byNo.keys.filter { k -> k.any { !it.isDigit() } }

        for (p in pieces) {
            val norm = normalize(p.text)
            var matchedInPiece = false
            for (run in Regex("\\d{$minLen,}").findAll(norm)) {
                val r = run.value
                val exact = byNo[r]
                if (exact != null) {
                    add(Hit(exact.id, MatchSource.EXACT, r, p.box)); matchedInPiece = true; continue
                }
                // 学号嵌在更长数字串中（如 “2024010523” 中含 “20240105”），取最长的
                val inner = byNo.entries.filter { it.key.length >= minLen && it.key.all { ch -> ch.isDigit() } && r.contains(it.key) }
                    .maxByOrNull { it.key.length }
                if (inner != null && (r.length - inner.key.length) <= (if (shortIds) 1 else 3)) {
                    add(Hit(inner.value.id, MatchSource.EXACT, r, p.box)); matchedInPiece = true; continue
                }
                if (fuzzy && r.length in noLens && r.length >= 5) {
                    val cands = byNo.entries.filter { it.key.length == r.length && hamming(it.key, r) == 1 }
                    if (cands.size == 1) {
                        add(Hit(cands[0].value.id, MatchSource.FUZZY, r, p.box)); matchedInPiece = true; continue
                    }
                }
                unknown += r to p.box
            }
            // 没认出完整学号的短文字（含数字、长度接近学号）：可能是一本没认清的作业
            if (!matchedInPiece) {
                Regex("[A-Za-z0-9?()]+").findAll(norm).map { it.value }
                    .filter { t -> t.any { it.isDigit() } && t.length <= maxNoLen + 1 && t.length >= minOf(2, maxNoLen) }
                    .filter { t -> unknown.none { it.first == t } }
                    .forEach { loose += it to p.box }
            }
            if (alnumNos.isNotEmpty()) {
                val up = canon(p.text)
                alnumNos.forEach { k -> if (up.contains(k)) { add(Hit(byNo.getValue(k).id, MatchSource.EXACT, k, p.box)); matchedInPiece = true } }
            }
            if (matchNames) {
                val compact = p.text.replace(Regex("\\s"), "")
                for (s in names) {
                    if (compact.contains(s.name)) {
                        add(Hit(s.id, MatchSource.NAME, s.name, p.box))
                    }
                }
            }
        }
        return MatchOutput(hits.values.toList(), unknown.distinctBy { it.first }, loose)
    }

    private fun hamming(a: String, b: String): Int {
        var d = 0
        for (i in a.indices) if (a[i] != b[i]) d++
        return d
    }
}
