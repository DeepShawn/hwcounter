package com.deepshawn.hwcounter.data

import java.util.Calendar

/**
 * 股票模式：把关注度指数当成“股价”。
 * - 每个同学是一只股票，全班平均是“大盘”（关注指数 ATTN）；
 * - 按所选区间（1周 / 1月 / 3月 / 6月 / 1年 / 全部）切成若干根 K 线：开盘 = 这段开始时的指数，收盘 = 这段结束时的指数，
 *   最高 / 最低 = 这段里每次有新记录（作业、加减分、考试、重置、结算）后指数的最大 / 最小值；
 * - 成交量 = 这段时间里的记录条数（作业登记 + 加减分 + 考试成绩）；
 * - 涨跌 = 最新值 − 区间开盘（前收）。
 */
object AttentionStock {
    enum class Span(val label: String, val days: Int, val bucketDays: Int) {
        W1("1周", 7, 1), M1("1月", 30, 1), M3("3月", 91, 7), M6("6月", 182, 7), Y1("1年", 364, 14), ALL("全部", -1, 0),
    }

    data class Candle(val start: Long, val end: Long, val open: Double, val high: Double, val low: Double, val close: Double, val volume: Int) {
        val up: Boolean get() = close >= open
    }

    data class Quote(val id: String, val candles: List<Candle>) {
        val base: Double get() = candles.firstOrNull()?.open ?: 0.0
        val last: Double get() = candles.lastOrNull()?.close ?: 0.0
        val change: Double get() = last - base
        /** 涨跌幅；起点太小（< 1）时没有意义，返回 null */
        val pct: Double? get() = if (base < 1.0) null else change / base
        val high: Double get() = candles.maxOfOrNull { it.high } ?: 0.0
        val low: Double get() = candles.minOfOrNull { it.low } ?: 0.0
        val volume: Int get() = candles.sumOf { it.volume }
        /** 上一根 K 线的收盘（“前收”） */
        val prevClose: Double get() = candles.getOrNull(candles.size - 2)?.close ?: base
        val closes: List<Double> get() = listOf(base) + candles.map { it.close }
    }

    data class Board(val span: Span, val from: Long, val now: Long, val market: Quote, val stocks: Map<String, Quote>) {
        val up: Int get() = stocks.values.count { it.change > 0.05 }
        val down: Int get() = stocks.values.count { it.change < -0.05 }
        val flat: Int get() = stocks.size - up - down
    }

    private const val DAY = 86_400_000L

    private fun startOfDay(t: Long): Long = Calendar.getInstance().apply {
        timeInMillis = t; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** 产生数据的时刻（关注度只会在这些时刻变化） */
    private fun eventTimes(c: SchoolClass): List<Long> =
        (c.assignments.map { it.createdAt } + c.scores.map { it.time } + c.exams.map { it.date } + c.recharges.map { it.start } + c.settlements.map { it.time }).distinct().sorted()

    /**
     * 没有数据的时刻（还没开始记录、这位同学 / 这个组还没有数据）不算 0：沿用前一个值，最开始那段用第一个有数据的值。
     * 否则区间一开盘就是 0，之后“全部上涨”（与网页 analytics.buildBoard 一致）。
     */
    fun fill(points: List<Long>, ids: Collection<String>, raw: Map<Long, Map<String, Double>>): Map<Long, Map<String, Double>> {
        val sorted = points.sorted()
        val out = sorted.associateWith { HashMap<String, Double>() }
        for (id in ids) {
            var last = sorted.firstNotNullOfOrNull { raw[it]?.get(id) } ?: continue
            for (t in sorted) { raw[t]?.get(id)?.let { last = it }; out.getValue(t)[id] = last }
        }
        return out
    }

    /** 区间的起点和每根 K 线的长度 */
    fun window(c: SchoolClass, span: Span, now: Long): Pair<Long, Long> {
        val today = startOfDay(now)
        if (span != Span.ALL) return (today - (span.days - 1) * DAY) to span.bucketDays * DAY
        val first = eventTimes(c).firstOrNull()?.let { startOfDay(it) } ?: today
        val days = ((today - first) / DAY + 1).coerceAtLeast(1)
        val bucket = when { days <= 45 -> 1; days <= 300 -> 7; days <= 900 -> 30; else -> 91 }
        return first to bucket * DAY
    }

    /**
     * 生成行情。[maxPerBucket] 限制每根 K 线里额外取样的记录时刻数，避免记录很多时太慢。
     */
    fun board(c: SchoolClass, rules: ScoreRules, span: Span, now: Long = System.currentTimeMillis(), maxPerBucket: Int = 6): Board {
        val (from, step) = window(c, span, now)
        val bounds = generateSequence(from) { it + step }.takeWhile { it < now }.toList() + now
        val events = eventTimes(c).filter { it in (from + 1) until now }
        // 每根 K 线：起点 + 中间取样的记录时刻 + 终点
        val inner = bounds.zipWithNext().map { (a, b) ->
            val ev = events.filter { it in a until b }
            if (ev.size <= maxPerBucket) ev else (0 until maxPerBucket).map { k -> ev[(k * (ev.size - 1)) / (maxPerBucket - 1).coerceAtLeast(1)] }.distinct()
        }
        val points = (bounds + inner.flatten()).distinct().sorted()
        val ids = c.students.map { it.id }
        val first = eventTimes(c).firstOrNull()
        val values = fill(points, ids, points.associateWith { t -> if (first == null || t < first) emptyMap() else Attention.all(c, rules, t).associate { it.student.id to it.index } })
        fun v(t: Long, id: String?) = values.getValue(t).let { m -> if (id == null) (if (m.isEmpty()) 0.0 else m.values.average()) else m[id] ?: 0.0 }

        // 成交量：每段的记录条数
        fun volume(a: Long, b: Long, id: String?): Int {
            val hw = c.assignments.filter { it.createdAt in a until b }.sumOf { as_ ->
                if (id == null) as_.statuses.count { (_, s) -> s != HwStatus.NONE } else if ((as_.statuses[id] ?: HwStatus.NONE) != HwStatus.NONE) 1 else 0
            }
            val sc = c.scores.count { it.time in a until b && (id == null || it.studentId == id) }
            val ex = c.exams.filter { it.date in a until b }.sumOf { e -> if (id == null) e.scores.size else if (id in e.scores) 1 else 0 }
            return hw + sc + ex
        }

        fun quote(id: String?): Quote = Quote(id ?: "", bounds.zipWithNext().mapIndexed { i, (a, b) ->
            val vs = listOf(v(a, id)) + inner[i].map { v(it, id) } + v(b, id)
            Candle(a, b, vs.first(), vs.max(), vs.min(), vs.last(), volume(a, b, id))
        })

        return Board(span, from, now, quote(null), ids.associateWith { quote(it) })
    }
}
