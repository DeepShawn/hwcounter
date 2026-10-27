package com.deepshawn.hwcounter.recog

import com.deepshawn.hwcounter.data.Student

/**
 * 把语音识别出的一段话解析成“依次说出的学生”。
 *
 * 顺序：
 * 1. 名字原文精确匹配（最长优先）；
 * 2. “音近数字 + 号”：识别器把“四十四号”写成“是十四号”时，按读音枚举 四/十 等可能，取名单里存在且符合数字语法的解释；
 * 3. 拼音模糊匹配名字：声母/韵母混淆矩阵 + 编辑距离对齐（容忍多字/漏字），阈值随名字长度变化，接近的候选交给老师点选；
 * 4. 只叫名不带姓（“子涵”→李子涵，班上唯一时）；
 * 5. 阿拉伯数字/中文数字学号，连读学号按名单拆分。
 */
class VoiceParser(
    private val roster: List<Student>,
    /** 单个汉字的所有可能读音（无声调），非汉字返回空 */
    private val readings: (Char) -> List<String>,
    /** 模糊程度：0.6 严格 / 1.0 标准 / 1.4 宽松 */
    private val tolerance: Float = 1f,
) {
    enum class Kind(val label: String) {
        NAME("原名"), PINYIN("同音"), FUZZY("音近"), GIVEN("叫名"), NUMBER("学号"), NUMBER_SOUND("音近学号")
    }

    data class Match(
        /** 多于一个表示有歧义 */
        val studentIds: List<String>,
        val heard: String,
        val kind: Kind,
        val start: Int,
        /** 拼音距离（越小越像） */
        val cost: Float = 0f,
    )

    data class Result(val matches: List<Match>, val leftover: String)

    private class Cand(val student: Student, val syl: List<List<String>>)

    private val named = roster.filter { it.name.length >= 2 }
    private val cands: List<Cand> = named.mapNotNull { s ->
        val syl = s.name.map { readings(it) }
        if (syl.any { it.isEmpty() }) null else Cand(s, syl)
    }
    private val givenCands: List<Cand> = cands.filter { it.syl.size >= 3 }.map { Cand(it.student, it.syl.drop(1)) }
    private val nameLens = cands.map { it.syl.size }.toSet()

    private val byNo: Map<String, Student> = roster.filter { it.no.isNotBlank() }.associateBy { Matcher.canon(it.no) }
    private val byNoValue: Map<Long, List<Student>> = roster.mapNotNull { s -> s.no.trim().toLongOrNull()?.let { it to s } }
        .groupBy({ it.first }, { it.second })
    private val noLens = byNo.keys.filter { k -> k.all { it.isDigit() } }.map { it.length }.toSet()

    /** 名字长度 → 允许的拼音距离上限（2 字名只容忍轻微混淆；长名字容忍多一点） */
    private fun maxCost(len: Int) = tolerance * when (len) {
        2 -> 0.36f
        3 -> 0.62f
        // 4 字及以上（如复姓“欧阳娜娜”）允许识别器多/漏一个字
        else -> 1.05f
    }

    private fun isHan(c: Char) = c.code in 0x3400..0x9FFF

    fun parse(text: String): Result {
        val t = Matcher.halfWidth(text).replace(Regex("(?<=[\\u3400-\\u9fff])\\s+(?=[\\u3400-\\u9fff])"), "")
        val used = BooleanArray(t.length)
        val matches = ArrayList<Match>()
        fun free(a: Int, b: Int) = a >= 0 && b <= t.length && (a until b).none { used[it] }
        fun take(a: Int, b: Int) { for (i in a until b) used[i] = true }

        // 1) 原文精确匹配
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

        // 2) 音近数字 + “号”
        numbersBySound(t, used, matches)

        // 3) 拼音模糊匹配全名
        fuzzyNames(t, used, matches, cands, Kind.FUZZY) { maxCost(it) }
        // 4) 只叫名（不带姓），要求很像且班上唯一
        fuzzyNames(t, used, matches, givenCands, Kind.GIVEN, uniqueOnly = true) { 0.2f * tolerance }

        // 5) 数字学号
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
        }.replace(Regex("[\\s，。、,.!?！？号和还有跟以及然后]+"), " ").trim()
        return Result(matches.sortedBy { it.start }, leftover)
    }

    /** 在未用过的汉字窗口上做拼音对齐匹配，按“平均距离”从小到大贪心选取不重叠的结果 */
    private fun fuzzyNames(
        t: String, used: BooleanArray, out: MutableList<Match>, pool: List<Cand>, kind: Kind,
        uniqueOnly: Boolean = false, limit: (Int) -> Float,
    ) {
        if (pool.isEmpty()) return
        val lens = pool.map { it.syl.size }.toSet()
        data class W(val start: Int, val end: Int, val ids: List<String>, val cost: Float)
        val found = ArrayList<W>()
        val readCache = HashMap<Int, List<String>>()
        fun rd(i: Int) = readCache.getOrPut(i) { readings(t[i]) }
        val windowLens = lens.flatMap { listOf(it - 1, it, it + 1) }.filter { it >= 2 }.toSet()
        for (start in t.indices) {
            for (len in windowLens) {
                val end = start + len
                if (end > t.length) continue
                if ((start until end).any { used[it] || !isHan(t[it]) || rd(it).isEmpty() }) continue
                val heard = (start until end).map { rd(it) }
                val scored = pool.filter { kotlin.math.abs(it.syl.size - len) <= 1 }
                    .map { it to PinyinSim.sequence(heard, it.syl) }
                    .filter { (c, d) -> d <= limit(c.syl.size) }
                    .sortedBy { it.second }
                if (scored.isEmpty()) continue
                val best = scored[0].second
                // 与最优几乎一样像的都算候选（同音/近音的不同学生）
                val close = scored.filter { it.second <= best + 0.12f }.map { it.first.student.id }.distinct()
                if (uniqueOnly && close.size > 1) continue
                found += W(start, end, close, best)
            }
        }
        found.sortWith(compareBy<W>({ it.cost / (it.end - it.start) }, { -(it.end - it.start) }))
        for (w in found) {
            if ((w.start until w.end).any { used[it] }) continue
            val k = if (w.cost == 0f && kind == Kind.FUZZY) Kind.PINYIN else kind
            out += Match(w.ids, t.substring(w.start, w.end), k, w.start, w.cost)
            for (i in w.start until w.end) used[i] = true
        }
    }

    private val numeralReadings = listOf(
        '零' to "ling", '一' to "yi", '一' to "yao", '二' to "er", '二' to "liang", '三' to "san", '四' to "si",
        '五' to "wu", '六' to "liu", '七' to "qi", '八' to "ba", '九' to "jiu", '十' to "shi", '百' to "bai",
    )
    private val haoChars = setOf('号', '好', '浩', '豪', '耗', '毫', '郝', '昊', '皓')

    /** 识别器把数字写成同音/近音字（是→四/十，留→六，林→零），以“号”结尾时按读音还原 */
    private fun numbersBySound(t: String, used: BooleanArray, out: MutableList<Match>) {
        var i = 0
        while (i < t.length) {
            if (used[i] || !isHan(t[i])) { i++; continue }
            // 找到以“号”结尾、前面全是“可能是数字”的字的片段
            var j = i
            val options = ArrayList<List<Pair<Char, Int>>>() // 每个字的候选数字及代价（0=本来就是该数字/读音完全一致）
            while (j < t.length && !used[j] && t[j] !in haoChars && j - i < 6) {
                val c = t[j]
                val opts = numeralOptions(c)
                if (opts.isEmpty()) break
                options += opts
                j++
            }
            val endsWithHao = j < t.length && t[j] in haoChars && options.isNotEmpty()
            val allRealNumerals = (i until j).all { t[it] in "零〇一幺二两三四五六七八九十百" }
            if (!endsWithHao || allRealNumerals) { i++; continue }
            // 枚举组合（最多 64 种），取“符合数字语法、名单里存在、替换最少”的解释
            var best: Pair<List<String>, Int>? = null
            fun dfs(k: Int, acc: StringBuilder, cost: Int) {
                if (best != null && cost >= best!!.second) return
                if (k == options.size) {
                    val s = acc.toString()
                    val segs = cnNumber.findAll(s).toList()
                    if (segs.joinToString("") { it.value } != s) return
                    val pieces = segs.map { cnToNumber(it.value) }
                    // “…号”前面通常只是一个学号：拆成多个数要额外付出代价
                    val total = cost + 3 * (pieces.size - 1)
                    if (pieces.all { lookupNo(it).isNotEmpty() } && (best == null || total < best!!.second)) best = pieces to total
                    return
                }
                for ((ch, c) in options[k]) { acc.append(ch); dfs(k + 1, acc, cost + c); acc.setLength(acc.length - 1) }
            }
            dfs(0, StringBuilder(), 0)
            val b = best
            if (b != null) {
                for (p in b.first) out += Match(lookupNo(p).map { it.id }, t.substring(i, j + 1) + "→" + p, Kind.NUMBER_SOUND, i)
                for (k in i..j) used[k] = true
                i = j + 1
            } else i++
        }
    }

    private fun numeralOptions(c: Char): List<Pair<Char, Int>> {
        val rd = readings(c)
        if (rd.isEmpty()) return emptyList()
        val res = LinkedHashMap<Char, Int>()
        for ((n, r) in numeralReadings) {
            val d = rd.minOf { PinyinSim.syllable(it, r) }
            val cost = when {
                c == n -> 0
                d == 0f -> 1
                d <= 0.3f -> 2
                else -> continue
            }
            if ((res[n] ?: Int.MAX_VALUE) > cost) res[n] = cost
        }
        return res.map { it.key to it.value }
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
    }
}
