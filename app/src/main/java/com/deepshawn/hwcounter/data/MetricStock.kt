package com.deepshawn.hwcounter.data

/**
 * 股票模式的其他板块（K 线、区间、成交量与 [AttentionStock] 相同）：
 * - 个人 · 信誉分：每个同学一只股票，股价 = 信誉分，大盘 CRDT = 全班平均（越高越好）；
 * - 小组 · 信誉分：每组一只股票，股价 = 小组信誉分，大盘 GCRD = 各组平均（越高越好）；
 * - 小组 · 关注度：每组一只股票，股价 = 组员关注度平均，大盘 GATT = 各组平均（越高越需要关注）。
 */
object MetricStock {
    private fun build(
        c: SchoolClass, span: AttentionStock.Span, now: Long, maxPerBucket: Int, ids: List<String>, events: List<Long>,
        valuesAt: (Long) -> Map<String, Double>, volume: (Long, Long, String?) -> Int,
    ): AttentionStock.Board {
        val (from, step) = AttentionStock.window(c, span, now)
        val bounds = generateSequence(from) { it + step }.takeWhile { it < now }.toList() + now
        val ev = events.distinct().sorted().filter { it in (from + 1) until now }
        val inner = bounds.zipWithNext().map { (a, b) ->
            val e = ev.filter { it in a until b }
            if (e.size <= maxPerBucket) e else (0 until maxPerBucket).map { k -> e[(k * (e.size - 1)) / (maxPerBucket - 1).coerceAtLeast(1)] }.distinct()
        }
        val points = (bounds + inner.flatten()).distinct().sorted()
        val values = AttentionStock.fill(points, ids, points.associateWith(valuesAt))
        fun v(t: Long, id: String?): Double {
            val m = values.getValue(t)
            return if (id == null) (if (m.isEmpty()) 0.0 else m.values.average()) else m[id] ?: 0.0
        }
        fun quote(id: String?) = AttentionStock.Quote(id ?: "", bounds.zipWithNext().mapIndexed { i, (a, b) ->
            val vs = listOf(v(a, id)) + inner[i].map { v(it, id) } + v(b, id)
            AttentionStock.Candle(a, b, vs.first(), vs.max(), vs.min(), vs.last(), volume(a, b, id))
        })
        return AttentionStock.Board(span, from, now, quote(null), ids.associateWith { quote(it) })
    }

    private fun studentVolume(c: SchoolClass, a: Long, b: Long, ids: Collection<String>?): Int {
        val hw = c.assignments.filter { it.createdAt in a until b }.sumOf { x ->
            x.statuses.count { (id, s) -> s != HwStatus.NONE && (ids == null || id in ids) }
        }
        val ex = c.exams.filter { it.date in a until b }.sumOf { e -> e.scores.keys.count { ids == null || it in ids } }
        return hw + ex
    }

    private fun events(c: SchoolClass) = c.assignments.map { it.createdAt } + c.exams.map { it.date }

    /** 个人 · 信誉分 */
    fun personalCredit(c: SchoolClass, span: AttentionStock.Span, now: Long = System.currentTimeMillis(), maxPerBucket: Int = 6): AttentionStock.Board =
        build(c, span, now, maxPerBucket, c.students.map { it.id }, events(c),
            { t -> Credit.all(c, t).filter { it.hasData }.associate { it.student.id to it.index } },
            { a, b, id -> studentVolume(c, a, b, id?.let { setOf(it) }) })

    /** 小组 · 信誉分；没有小组时为 null */
    fun groupCredit(c: SchoolClass, span: AttentionStock.Span, now: Long = System.currentTimeMillis(), maxPerBucket: Int = 6): AttentionStock.Board? {
        val groups = Credit.groupsOf(c)
        if (groups.isEmpty()) return null
        return build(c, span, now, maxPerBucket, groups.keys.toList(), events(c),
            { t -> Credit.groups(c, t) },
            { a, b, g -> studentVolume(c, a, b, (if (g == null) groups.values.flatten() else groups.getValue(g)).map { it.id }.toSet()) })
    }

    /** 小组 · 关注度（组员关注度平均）；没有小组时为 null */
    fun groupAttention(c: SchoolClass, rules: ScoreRules, span: AttentionStock.Span, now: Long = System.currentTimeMillis(), maxPerBucket: Int = 6): AttentionStock.Board? {
        val groups = Credit.groupsOf(c)
        if (groups.isEmpty()) return null
        val first = events(c).minOrNull()
        return build(c, span, now, maxPerBucket, groups.keys.toList(), events(c) + c.scores.map { it.time },
            { t -> if (first == null || t < first) emptyMap() else groupAttentionAt(c, rules, t, groups) },
            { a, b, g -> studentVolume(c, a, b, (if (g == null) groups.values.flatten() else groups.getValue(g)).map { it.id }.toSet()) })
    }

    /** 各组在某一刻的关注度（组员平均） */
    fun groupAttentionAt(c: SchoolClass, rules: ScoreRules, t: Long = System.currentTimeMillis(), groups: Map<String, List<Student>> = Credit.groupsOf(c)): Map<String, Double> {
        val by = Attention.all(c, rules, t).associate { it.student.id to it.index }
        return groups.mapValues { (_, ms) -> ms.map { by[it.id] ?: 0.0 }.average() }
    }
}
