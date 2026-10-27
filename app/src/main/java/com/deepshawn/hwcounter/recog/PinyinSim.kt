package com.deepshawn.hwcounter.recog

/**
 * 拼音相似度：把音节拆成声母 + 韵母，按“混淆矩阵”给出 0（相同）~ 1（无关）的距离。
 * 轻度混淆（方言/口音）代价低，中度混淆（识别器常错）代价中等，无关读音代价为 1。
 */
object PinyinSim {
    private val initials = listOf("zh", "ch", "sh", "b", "p", "m", "f", "d", "t", "n", "l", "g", "k", "h", "j", "q", "x", "r", "z", "c", "s", "y", "w")

    fun split(sy: String): Pair<String, String> {
        val s = sy.lowercase().replace('ü', 'u').replace('v', 'u')
        val ini = initials.firstOrNull { s.startsWith(it) } ?: ""
        return ini to s.substring(ini.length)
    }

    private fun pair(a: String, b: String) = if (a < b) "$a|$b" else "$b|$a"

    private val lightIni = setOf("z|zh", "c|ch", "s|sh", "l|n", "f|h", "l|r")
    private val midIni = setOf("b|p", "d|t", "g|k", "j|zh", "ch|q", "sh|x", "j|z", "c|q", "s|x", "r|y", "|y", "|w", "h|k")
    private val lightFin = setOf("in|ing", "en|eng", "an|ang", "ian|iang", "uan|uang", "ong|un", "eng|ong", "ian|in", "e|o", "o|uo", "ui|uei", "iu|iou", "un|uen")
    private val midFin = setOf("ie|ue", "ei|ui", "ai|ei", "a|e", "en|in", "an|en", "ao|ou", "i|ü", "u|ü", "i|ie", "ing|ong", "iang|uang", "e|ie")

    fun initialCost(a: String, b: String): Float = when {
        a == b -> 0f
        pair(a, b) in lightIni -> 0.3f
        pair(a, b) in midIni -> 0.55f
        else -> 1f
    }

    fun finalCost(a: String, b: String): Float = when {
        a == b -> 0f
        pair(a, b) in lightFin -> 0.3f
        pair(a, b) in midFin -> 0.55f
        else -> 1f
    }

    /** 单个音节距离：声母权重略高于韵母（声母错更容易换成另一个人） */
    fun syllable(a: String, b: String): Float {
        if (a == b) return 0f
        val (ia, fa) = split(a)
        val (ib, fb) = split(b)
        return (0.55f * initialCost(ia, ib) + 0.45f * finalCost(fa, fb)).coerceAtMost(1f)
    }

    /** 每个字可能有多个读音（多音字），取最近的一对 */
    fun syllableAlt(a: List<String>, b: List<String>): Float {
        var best = 1f
        for (x in a) for (y in b) { val d = syllable(x, y); if (d < best) best = d }
        return best
    }

    /** 两个读音序列的对齐距离（编辑距离：替换 = 音节距离，多字/漏字 = 1） */
    fun sequence(heard: List<List<String>>, name: List<List<String>>): Float {
        val n = heard.size
        val m = name.size
        val dp = Array(n + 1) { FloatArray(m + 1) }
        for (i in 0..n) dp[i][0] = i.toFloat()
        for (j in 0..m) dp[0][j] = j.toFloat()
        for (i in 1..n) for (j in 1..m) {
            dp[i][j] = minOf(dp[i - 1][j] + 1f, dp[i][j - 1] + 1f, dp[i - 1][j - 1] + syllableAlt(heard[i - 1], name[j - 1]))
        }
        return dp[n][m]
    }
}

/** 名字里常见的多音字（含姓氏特殊读音）。系统拼音只给一个读音，这里补上其它读音 */
object Polyphones {
    private val alts: Map<Char, List<String>> = mapOf(
        '曾' to listOf("zeng", "ceng"), '单' to listOf("shan", "dan", "chan"), '解' to listOf("xie", "jie"),
        '仇' to listOf("qiu", "chou"), '朴' to listOf("piao", "pu"), '区' to listOf("ou", "qu"), '查' to listOf("zha", "cha"),
        '乐' to listOf("yue", "le"), '尉' to listOf("yu", "wei"), '盖' to listOf("ge", "gai"), '缪' to listOf("miao", "mou", "miu"),
        '覃' to listOf("qin", "tan"), '翟' to listOf("zhai", "di"), '长' to listOf("chang", "zhang"), '行' to listOf("xing", "hang"),
        '重' to listOf("zhong", "chong"), '朝' to listOf("zhao", "chao"), '传' to listOf("chuan", "zhuan"), '都' to listOf("du", "dou"),
        '角' to listOf("jiao", "jue"), '宿' to listOf("su", "xiu"), '茜' to listOf("qian", "xi"), '蔚' to listOf("wei", "yu"),
        '莘' to listOf("shen", "xin"), '柏' to listOf("bai", "bo"), '晟' to listOf("sheng", "cheng"), '贾' to listOf("jia", "gu"),
        '秘' to listOf("mi", "bi"), '召' to listOf("zhao", "shao"), '种' to listOf("zhong", "chong"), '大' to listOf("da", "dai"),
        '藏' to listOf("zang", "cang"), '奇' to listOf("qi", "ji"), '会' to listOf("hui", "kuai"), '牟' to listOf("mou", "mu"),
        '沈' to listOf("shen", "chen"), '颉' to listOf("xie", "jie"), '华' to listOf("hua"), '纪' to listOf("ji"),
        '和' to listOf("he", "huo", "hu"), '强' to listOf("qiang", "jiang"), '弹' to listOf("dan", "tan"), '还' to listOf("hai", "huan"),
        '着' to listOf("zhe", "zhuo", "zhao"), '参' to listOf("can", "shen"), '似' to listOf("si", "shi"),
    )

    fun of(c: Char): List<String> = alts[c] ?: emptyList()
}
