package com.deepshawn.hwcounter.recog

/**
 * 本地识别与 AI 识别结果的合并（纠正而不是简单相加）。
 *
 * 典型问题：本地 OCR 把手写“49”读成“4”，名单里恰好有 4 号 → 错配；AI 正确给出 34、49 共 2 本。
 * 若只是“AI 有而本地没有的就追加”，会得到 4、34、49 共 3 本。
 *
 * 规则：
 * 1. 本地“不可靠”的项（没对上名单 / 模糊或姓名匹配 / 读到的数字比名单最长学号短）且不在 AI 列表里，
 *    与“AI 有而本地没有”的学号按相似度配对，用 AI 的结果纠正；
 * 2. 剩余的 AI 学号只在总数不超过 AI 给出的本数时才追加；
 * 3. 本地可靠的精确匹配即使 AI 没提到也保留（AI 也会漏看）。
 */
object Reconcile {
    data class Local(val studentId: String?, val raw: String, val weak: Boolean)

    data class Out(
        /** 与 local 一一对应的（可能被纠正后的）学生 id */
        val corrected: List<String?>,
        /** 被 AI 纠正的下标 */
        val correctedIdx: Set<Int>,
        /** 需要额外追加的 AI 学生 id（按 AI 顺序） */
        val extra: List<String>,
    )

    fun merge(local: List<Local>, aiIds: List<String>, aiCount: Int?, noOf: (String) -> String): Out {
        val ai = aiIds.distinct()
        if (ai.isEmpty()) return Out(local.map { it.studentId }, emptySet(), emptyList())
        val localSet = local.mapNotNull { it.studentId }.toSet()
        val aiSet = ai.toSet()
        val aiOnly = ai.filter { it !in localSet }.toMutableList()
        val cands = local.indices.filter { i ->
            val l = local[i]
            l.studentId == null || (l.weak && l.studentId !in aiSet)
        }.toMutableList()

        val corrected = local.map { it.studentId }.toMutableList()
        val fixed = HashSet<Int>()
        // 先按相似度贪心配对，再按顺序配对剩下的
        while (cands.isNotEmpty() && aiOnly.isNotEmpty()) {
            var best: Triple<Int, String, Int>? = null
            for (ci in cands) for (a in aiOnly) {
                val sc = similarity(digits(local[ci].raw), digits(noOf(a)))
                if (best == null || sc > best.third) best = Triple(ci, a, sc)
            }
            val (ci, a, sc) = best!!
            val pick = if (sc > 0) ci to a else cands.first() to aiOnly.first()
            corrected[pick.first] = pick.second
            fixed += pick.first
            cands.remove(pick.first)
            aiOnly.remove(pick.second)
        }
        // AI 给出的总本数是上限，避免“本地 + AI”重复计数
        val target = maxOf(aiCount ?: ai.size, ai.size)
        val room = (target - local.size).coerceAtLeast(0)
        val have = corrected.filterNotNull().toSet()
        val extra = aiOnly.filter { it !in have }.take(room)
        return Out(corrected, fixed, extra)
    }

    private fun digits(s: String) = Matcher.normalize(s).filter { it.isDigit() }.ifEmpty { s }

    /** 前缀 > 后缀 > 包含 > 同长度仅差一位 */
    fun similarity(raw: String, no: String): Int {
        if (raw.isEmpty() || no.isEmpty()) return 0
        if (raw == no) return 5
        if (no.startsWith(raw)) return 4
        if (no.endsWith(raw)) return 3
        if (no.contains(raw) || raw.contains(no)) return 2
        if (raw.length == no.length && raw.zip(no).count { it.first != it.second } == 1) return 1
        return 0
    }
}
