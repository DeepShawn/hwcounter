package com.deepshawn.hwcounter.recog

import com.deepshawn.hwcounter.data.Student

/**
 * 把语音识别出的一段话解析成“依次说出的学生”。
 *
 * 1. 名字原文精确匹配（最长优先）；
 * 2. 拼音匹配：识别器常把名字写成同音字（张伟→章维），按拼音比对；再放宽 z/zh、c/ch、s/sh、n/l、in/ing、en/eng、an/ang；
 * 3. 学号：支持“三十四号”“三四”“幺零三”“34”；几个学号连着说被并成一串（“3449”）时按名单拆开。
 * 同音/同名对应多人时返回多个候选，交给老师点选。
 */
class VoiceParser(
    private val roster: List<Student>,
    /** 单个汉字 → 无声调拼音（如 '张' → "zhang"），非汉字返回 null */
    private val pinyin: (Char) -> String?,
) {
    enum class Kind { NAME, PINYIN, FUZZY, NUMBER }

    data class Match(
        /** 多于一个表示有歧义 */
        val studentIds: List<String>,
        val heard: String,
        val kind: Kind,
        val start: Int,
    )

    data class Result(val matches: List<Match>, val leftover: String)

    private val named = roster.filter { it.name.length >= 2 }
    private val nameLens = named.map { it.name.length }.distinct().sortedDescending()
    private val byNo: Map<String, Student> = roster.filter { it.no.isNotBlank() }.associateBy { Matcher.canon(it.no) }
    private val byNoValue: Map<Long, List<Student>> = roster.mapNotNull { s -> s.no.trim().toLongOrNull()?.let { it to s } }
        .groupBy({ it.first }, { it.second })
    private val noLens = byNo.keys.filter { k -> k.all { it.isDigit() } }.map { it.length }.toSet()

    private fun syllables(s: String): List<String>? {
        val out = ArrayList<String>(s.length)
        for (c in s) out += (pinyin(c) ?: return null)
        return out
    }

    private val exactKey: Map<String, List<Student>> by lazy {
        named.mapNotNull { s -> syllables(s.name)?.let { it.joinToString(" ") to s } }.groupBy({ it.first }, { it.second })
    }
    private val fuzzyKey: Map<String, List<Student>> by lazy {
        named.mapNotNull { s -> syllables(s.name)?.let { sy -> sy.joinToString(" ") { fuzzy(it) } to s } }.groupBy({ it.first }, { it.second })
    }

    fun parse(text: String): Result {
        val t = Matcher.halfWidth(text)
        val used = BooleanArray(t.length)
        val matches = ArrayList<Match>()

        fun free(a: Int, b: Int) = (a until b).none { used[it] }
        fun take(a: Int, b: Int) { for (i in a until b) used[i] = true }

        // 1) 原文精确匹配（长名字优先，避免“张三丰”被拆成“张三”）
        val sameName = named.groupBy { it.name }
        for (name in sameName.keys.sortedByDescending { it.length }) {
            var from = 0
            while (true) {
                val i = t.indexOf(name, from)
                if (i < 0) break
                if (free(i, i + name.length)) {
                    matches += Match(sameName.getValue(name).map { it.id }, name, Kind.NAME, i)
                    take(i, i + name.length)
                }
                from = i + 1
            }
        }

        // 2) 拼音匹配（同音字），先严格后放宽
        for ((keys, kind) in listOf(exactKey to Kind.PINYIN, fuzzyKey to Kind.FUZZY)) {
            if (keys.isEmpty()) continue
            for (len in nameLens) {
                var i = 0
                while (i + len <= t.length) {
                    if (!free(i, i + len)) { i++; continue }
                    val seg = t.substring(i, i + len)
                    val sy = syllables(seg)
                    if (sy == null) { i++; continue }
                    val key = if (kind == Kind.PINYIN) sy.joinToString(" ") else sy.joinToString(" ") { fuzzy(it) }
                    val hit = keys[key]
                    if (hit != null) {
                        matches += Match(hit.map { it.id }, seg, kind, i)
                        take(i, i + len)
                        i += len
                    } else i++
                }
            }
        }

        // 3) 学号：剩余文字里的中文数字 → 阿拉伯数字
        val rest = StringBuilder()
        for (i in t.indices) rest.append(if (used[i]) ' ' else t[i])
        val numText = convertChineseNumbers(rest.toString())
        for (m in Regex("\\d+").findAll(numText.text)) {
            val a = m.range.first
            val b = m.range.last
            val pos = numText.origin[a]
            // 带“十/百”的是数值读法（如“一百零三”），不能再拆成几个学号
            val positional = numText.positional[a]
            var any = false
            for (piece in if (positional) listOf(m.value) else splitNumber(m.value)) {
                val st = lookupNo(piece)
                if (st.isNotEmpty()) { matches += Match(st.map { it.id }, piece, Kind.NUMBER, pos); any = true }
            }
            if (any) take(pos, numText.originEnd[b])
        }
        val leftover = buildString {
            for (i in t.indices) if (!used[i]) append(t[i])
        }.replace(Regex("[\\s，。、,.!?！？号和还有跟以及]+"), " ").trim()
        return Result(matches.sortedBy { it.start }, leftover)
    }

    private fun lookupNo(n: String): List<Student> {
        byNo[n]?.let { return listOf(it) }
        // “4”与名单里的“04”等价
        return n.toLongOrNull()?.let { byNoValue[it] } ?: emptyList()
    }

    /** 名单里没有这个数字串时，尝试拆成若干个名单里存在的学号（连着说的情况） */
    private fun splitNumber(run: String): List<String> {
        if (lookupNo(run).isNotEmpty() || noLens.isEmpty()) return listOf(run)
        val n = run.length
        val best = arrayOfNulls<List<String>>(n + 1)
        best[0] = emptyList()
        for (i in 0 until n) {
            val prev = best[i] ?: continue
            for (l in noLens) {
                if (i + l > n) continue
                val piece = run.substring(i, i + l)
                if (lookupNo(piece).isEmpty()) continue
                val cand = prev + piece
                val cur = best[i + l]
                if (cur == null || cand.size < cur.size) best[i + l] = cand
            }
        }
        return best[n] ?: listOf(run)
    }

    /** origin/originEnd：每个输出字符对应原文的起止位置；positional：是否来自带十/百的读法 */
    class Converted(val text: String, val origin: IntArray, val originEnd: IntArray, val positional: BooleanArray)

    companion object {
        private val cnDigit = mapOf(
            '零' to 0, '〇' to 0, '一' to 1, '幺' to 1, '二' to 2, '两' to 2, '三' to 3, '四' to 4,
            '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9,
        )
        private val cnRun = Regex("[零〇一幺二两三四五六七八九十百]+")
        /** 把一串中文数字切成若干个完整的数：“三十四四十九”→“三十四”“四十九” */
        private val cnNumber = Regex(
            "[一二两三四五六七八九幺]?百(?:零?[一二三四五六七八九]十|零[一二三四五六七八九]|[一二三四五六七八九]十)?[一二三四五六七八九]?" +
                "|[一二三四五六七八九]?十[一二三四五六七八九]?" +
                "|[零〇一幺二两三四五六七八九]+",
        )

        /** “三十四”→34，“一百零三”→103，“十二”→12；不含十/百时逐位读：“三四”→34，“零三”→03 */
        fun cnToNumber(s: String): String {
            if (s.none { it == '十' || it == '百' }) return s.map { cnDigit[it] ?: 0 }.joinToString("")
            var total = 0
            var cur = 0
            for (c in s) {
                when (c) {
                    '百' -> { total += (if (cur == 0) 1 else cur) * 100; cur = 0 }
                    '十' -> { total += (if (cur == 0) 1 else cur) * 10; cur = 0 }
                    else -> cur = cnDigit[c] ?: 0
                }
            }
            return (total + cur).toString()
        }

        fun convertChineseNumbers(s: String): Converted {
            val sb = StringBuilder()
            val origin = ArrayList<Int>()
            val originEnd = ArrayList<Int>()
            val positional = ArrayList<Boolean>()
            fun put(ch: Char, a: Int, b: Int, p: Boolean) { sb.append(ch); origin += a; originEnd += b; positional += p }
            var last = 0
            for (m in cnRun.findAll(s)) {
                for (i in last until m.range.first) put(s[i], i, i + 1, false)
                // 前后紧挨着阿拉伯数字时补空格，避免粘连成一个数
                if (m.range.first > 0 && s[m.range.first - 1].isDigit()) put(' ', m.range.first, m.range.first, false)
                var firstSeg = true
                for (seg in cnNumber.findAll(m.value)) {
                    val a = m.range.first + seg.range.first
                    val b = m.range.first + seg.range.last + 1
                    val p = seg.value.any { it == '十' || it == '百' }
                    if (!firstSeg) put(' ', a, a, false)
                    for (ch in cnToNumber(seg.value)) put(ch, a, b, p)
                    firstSeg = false
                }
                last = m.range.last + 1
                if (last < s.length && s[last].isDigit()) put(' ', last, last, false)
            }
            for (i in last until s.length) put(s[i], i, i + 1, false)
            return Converted(sb.toString(), origin.toIntArray(), originEnd.toIntArray(), positional.toBooleanArray())
        }

        /** 南北口音/识别常见混淆的拼音归一 */
        fun fuzzy(sy: String): String {
            var s = sy
            s = when {
                s.startsWith("zh") -> "z" + s.substring(2)
                s.startsWith("ch") -> "c" + s.substring(2)
                s.startsWith("sh") -> "s" + s.substring(2)
                s.startsWith("n") && !s.startsWith("ng") -> "l" + s.substring(1)
                else -> s
            }
            if (s.endsWith("ing")) s = s.dropLast(1)
            else if (s.endsWith("eng")) s = s.dropLast(1)
            else if (s.endsWith("ang")) s = s.dropLast(1)
            return s
        }
    }
}
