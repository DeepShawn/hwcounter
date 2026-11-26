package com.deepshawn.hwcounter.data

/**
 * 股票模式 · 小组板块：每个小组是一只股票，“股价”是小组综合分（0–100）。
 * 某一时刻的综合分 = 截至这一刻最近 [window] 次作业里的：交齐率、少扣分（人均作业扣分，各组里最多的为 0、零扣分为 1）、
 * 优秀率，以及这段时间里的考试得分率（各组都有考试时才算），按小组对抗排名的权重加权，× 100。
 * 大盘“小组综合指数”= 各组综合分的平均。K 线、区间、成交量与个人关注度行情相同（见 [AttentionStock]）。
 * 综合分越高越好：上涨 = 小组在变好。
 */
object GroupStock {
    private val HANDED = setOf(HwStatus.SUBMITTED, HwStatus.EXCELLENT, HwStatus.LATE)

    /** 某一时刻各组的综合分（0–100）；有作业的组少于 1 个时为空 */
    fun scoresAt(c: SchoolClass, rules: ScoreRules, groups: Map<String, List<Student>>, auto: Map<String, Map<String, Double>>, sorted: List<Assignment>, t: Long, window: Int): Map<String, Double> {
        val recent = sorted.filter { it.createdAt <= t }.takeLast(window)
        if (recent.isEmpty()) return emptyMap()
        val from = recent.first().createdAt
        data class Raw(val rate: Double, val deduct: Double, val exc: Double, val exam: Double?)
        val raws = groups.mapNotNull { (g, ms) ->
            val st = recent.flatMap { a -> ms.map { a.statuses[it.id] ?: HwStatus.NONE } }.filter { it != HwStatus.NONE && it != HwStatus.LEAVE }
            if (st.isEmpty()) return@mapNotNull null
            val d = ms.sumOf { m -> recent.sumOf { a -> (auto.getValue(m.id)[a.id] ?: 0.0).let { if (it < 0) -it else 0.0 } } } / ms.size
            val ex = c.analysisExams.filter { it.date in from..t }.flatMap { e -> ms.mapNotNull { m -> e.scores[m.id]?.let { it / e.fullScore } } }
            g to Raw(st.count { it in HANDED }.toDouble() / st.size, d, st.count { it == HwStatus.EXCELLENT }.toDouble() / st.size, if (ex.isEmpty()) null else ex.average())
        }.toMap()
        if (raws.isEmpty()) return emptyMap()
        val gr = rules.groupRank
        val maxD = raws.values.maxOf { it.deduct }
        val allExam = raws.values.all { it.exam != null }
        return raws.mapValues { (_, r) ->
            val dn = if (maxD <= 1e-9) 1.0 else 1 - r.deduct / maxD
            var num = gr.wRate * r.rate + gr.wDeduct * dn + gr.wExcellent * r.exc
            var den = gr.wRate + gr.wDeduct + gr.wExcellent
            if (allExam) { num += gr.wExam * r.exam!!; den += gr.wExam }
            if (den <= 0) 0.0 else num / den * 100
        }
    }

    fun board(c: SchoolClass, rules: ScoreRules, span: AttentionStock.Span, now: Long = System.currentTimeMillis(), window: Int = 10, maxPerBucket: Int = 6): AttentionStock.Board? {
        val groups = c.students.filter { it.group.isNotBlank() }.groupBy { it.group }.filterValues { it.size >= 2 }
        if (groups.isEmpty()) return null
        val (from, step) = AttentionStock.window(c, span, now)
        val bounds = generateSequence(from) { it + step }.takeWhile { it < now }.toList() + now
        val sorted = c.assignments.sortedBy { it.createdAt }
        val events = (sorted.map { it.createdAt } + c.exams.map { it.date }).distinct().sorted().filter { it in (from + 1) until now }
        val inner = bounds.zipWithNext().map { (a, b) ->
            val ev = events.filter { it in a until b }
            if (ev.size <= maxPerBucket) ev else (0 until maxPerBucket).map { k -> ev[(k * (ev.size - 1)) / (maxPerBucket - 1).coerceAtLeast(1)] }.distinct()
        }
        val auto = groups.values.flatten().associate { it.id to c.autoPoints(rules, it.id) }
        val points = (bounds + inner.flatten()).distinct().sorted()
        val names = groups.keys.toList()
        val values = AttentionStock.fill(points, names, points.associateWith { t -> scoresAt(c, rules, groups, auto, sorted, t, window) })
        fun v(t: Long, g: String?): Double {
            val m = values.getValue(t)
            return if (g == null) (if (m.isEmpty()) 0.0 else m.values.average()) else m[g] ?: 0.0
        }
        fun volume(a: Long, b: Long, g: String?): Int {
            val ms = if (g == null) groups.values.flatten() else groups.getValue(g)
            return sorted.filter { it.createdAt in a until b }.sumOf { x -> ms.count { (x.statuses[it.id] ?: HwStatus.NONE) != HwStatus.NONE } } +
                c.analysisExams.filter { it.date in a until b }.sumOf { e -> ms.count { it.id in e.scores } }
        }
        fun quote(g: String?) = AttentionStock.Quote(g ?: "", bounds.zipWithNext().mapIndexed { i, (a, b) ->
            val vs = listOf(v(a, g)) + inner[i].map { v(it, g) } + v(b, g)
            AttentionStock.Candle(a, b, vs.first(), vs.max(), vs.min(), vs.last(), volume(a, b, g))
        })
        return AttentionStock.Board(span, from, now, quote(null), names.associateWith { quote(it) })
    }
}
