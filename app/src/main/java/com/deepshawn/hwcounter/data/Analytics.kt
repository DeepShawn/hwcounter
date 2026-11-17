package com.deepshawn.hwcounter.data

import java.util.Calendar
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * 统计分析（纯计算，便于测试）。
 *
 * 作业：全部 / 本轮（最近一次全班重置以来）/ 近 10 次 / 年 / 季度 / 月 / 周 / 日 / 自定义，每种都与“上一时段”对比；
 * 进步之星与退步关注（以往 vs 重置后）、各轮对比、按日周月汇总、积分来源与加减分原因、学生分层、缺交风险、偏科；
 * 核心指标、回归趋势、星期 / 科目 / 小组对比、学生明细、交齐率分布、积分走势与涨跌、
 * 数据完整度，以及自动生成的结论（[Insight]）和文字版分析报告。
 *
 * 考试：每场的平均 / 中位 / 标准差 / 及格良好优秀低分率、分数段、名次与进退步，
 * 跨场次的得分率走势、各科对比、稳定性、与作业交齐率的相关性。
 */
object Analytics {

    enum class Range(val label: String, val calendar: Boolean = false) {
        ALL("全部"), PERIOD("本轮"), RECENT10("近 10 次"),
        YEAR("年", true), QUARTER("季度", true), MONTH("月", true), WEEK("周", true), DAY("日", true), CUSTOM("自定义"),
    }

    /** 统计范围：类型 + 往前翻几个周期（0 = 本周期，-1 = 上一个）+ 自定义起止日（含） */
    data class Spec(val range: Range, val offset: Int = 0, val from: Long? = null, val to: Long? = null)

    // ---------------- 时间范围 ----------------

    data class Window(
        val range: Range,
        val assignments: List<Assignment>,
        val previous: List<Assignment>,
        /** 本轮的重置（只有 PERIOD 且有全班重置时） */
        val reset: ScoreRecharge?,
        /** 范围起点（用来计算积分变化），null = 有记录以来 */
        val start: Long?,
        val label: String,
        /** 范围终点（不含），null = 到现在 */
        val end: Long? = null,
        /** 上一时段的称呼，如“上月”“上一轮” */
        val prevLabel: String = "上一时段",
    )

    /** 全班重置：覆盖至少一半同学的重置，按生效时间排序 */
    fun classResets(c: SchoolClass): List<ScoreRecharge> =
        c.recharges.filter { it.studentIds.size * 2 >= c.students.size.coerceAtLeast(1) }.sortedWith(compareBy({ it.start }, { it.time }))

    /** 日历周期 [起, 止)：年 / 季度 / 月 / 周（周一起）/ 日，offset 往前（负）或往后（正）翻 */
    fun periodBounds(range: Range, offset: Int, now: Long = System.currentTimeMillis()): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply { timeInMillis = startOfDay(now) }
        val field: Int; val step: Int
        when (range) {
            Range.YEAR -> { cal.set(Calendar.DAY_OF_YEAR, 1); field = Calendar.YEAR; step = 1 }
            Range.QUARTER -> { cal.set(Calendar.DAY_OF_MONTH, 1); cal.set(Calendar.MONTH, cal.get(Calendar.MONTH) / 3 * 3); field = Calendar.MONTH; step = 3 }
            Range.MONTH -> { cal.set(Calendar.DAY_OF_MONTH, 1); field = Calendar.MONTH; step = 1 }
            Range.WEEK -> { cal.add(Calendar.DAY_OF_YEAR, -((cal.get(Calendar.DAY_OF_WEEK) + 5) % 7)); field = Calendar.DAY_OF_YEAR; step = 7 }
            else -> { field = Calendar.DAY_OF_YEAR; step = 1 }
        }
        cal.add(field, step * offset)
        val a = cal.timeInMillis
        cal.add(field, step)
        return a to cal.timeInMillis
    }

    private val weekNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    private fun md(t: Long) = java.text.SimpleDateFormat("M月d日", java.util.Locale.CHINA).format(java.util.Date(t))

    /** 日历周期的名称，如“2026年11月”“2026年第4季度” */
    fun periodLabel(range: Range, start: Long, end: Long, offset: Int): String {
        val cal = Calendar.getInstance().apply { timeInMillis = start }
        val y = cal.get(Calendar.YEAR); val m = cal.get(Calendar.MONTH) + 1
        val name = when (range) {
            Range.YEAR -> "${y}年"
            Range.QUARTER -> "${y}年第${(m - 1) / 3 + 1}季度（${m}–${m + 2}月）"
            Range.MONTH -> "${y}年${m}月"
            Range.WEEK -> "${y}年${md(start)} – ${md(end - 1)}"
            else -> "${fmtDay(start)} ${weekNames[(cal.get(Calendar.DAY_OF_WEEK) + 5) % 7]}"
        }
        val rel = when (offset) {
            0 -> mapOf(Range.YEAR to "今年", Range.QUARTER to "本季度", Range.MONTH to "本月", Range.WEEK to "本周", Range.DAY to "今天")[range]
            -1 -> mapOf(Range.YEAR to "去年", Range.QUARTER to "上季度", Range.MONTH to "上月", Range.WEEK to "上周", Range.DAY to "昨天")[range]
            else -> null
        }
        return if (rel != null) "$name · $rel" else name
    }

    fun window(c: SchoolClass, range: Range, dayFmt: (Long) -> String = ::fmtDay): Window = window(c, Spec(range), dayFmt = dayFmt)

    fun window(c: SchoolClass, spec: Spec, now: Long = System.currentTimeMillis(), dayFmt: (Long) -> String = ::fmtDay): Window {
        val sorted = c.assignments.sortedBy { it.createdAt }
        val range = spec.range
        fun between(a: Long, b: Long) = sorted.filter { it.createdAt >= a && it.createdAt < b }
        return when (range) {
            Range.ALL -> Window(range, sorted, emptyList(), null, null, "有记录以来全部 ${sorted.size} 次作业")
            Range.RECENT10 -> Window(range, sorted.takeLast(10), sorted.dropLast(10).takeLast(10), null, sorted.takeLast(10).firstOrNull()?.createdAt, "最近 ${minOf(10, sorted.size)} 次作业", prevLabel = "再往前 10 次")
            Range.PERIOD -> {
                val resets = classResets(c)
                val r = resets.lastOrNull()
                if (r == null) Window(range, sorted, emptyList(), null, null, "还没有全班重置，显示全部作业")
                else {
                    val prevReset = resets.dropLast(1).lastOrNull()
                    val cur = sorted.filter { r.counts(it.createdAt) }
                    val prev = sorted.filter { !r.counts(it.createdAt) && (prevReset == null || prevReset.counts(it.createdAt)) }
                    Window(range, cur, prev, r, r.start, "本轮：${r.startLabel(dayFmt)}（${fmtDay(r.time)} 重置为 ${r.target.fmt()} 分）· ${cur.size} 次作业", prevLabel = "上一轮")
                }
            }
            Range.CUSTOM -> {
                val a = startOfDay(spec.from ?: sorted.firstOrNull()?.createdAt ?: now)
                val toDay = startOfDay(maxOf(spec.to ?: now, a))
                val b = Calendar.getInstance().apply { timeInMillis = toDay; add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis
                val days = Calendar.getInstance().let { cal -> var n = 0; cal.timeInMillis = a; while (cal.timeInMillis < b && n < 100000) { cal.add(Calendar.DAY_OF_YEAR, 1); n++ }; n }
                val pa = Calendar.getInstance().apply { timeInMillis = a; add(Calendar.DAY_OF_YEAR, -days) }.timeInMillis
                val cur = between(a, b)
                Window(range, cur, between(pa, a), null, a, "${fmtDay(a)} – ${fmtDay(b - 1)}（$days 天）· ${cur.size} 次作业", end = b, prevLabel = "前 $days 天")
            }
            else -> {
                val (a, b) = periodBounds(range, spec.offset, now)
                val (pa, _) = periodBounds(range, spec.offset - 1, now)
                val cur = between(a, b)
                val prevName = mapOf(Range.YEAR to "上一年", Range.QUARTER to "上一季度", Range.MONTH to "上一月", Range.WEEK to "上一周", Range.DAY to "前一天")[range] ?: "上一时段"
                Window(range, cur, between(pa, a), null, a, "${periodLabel(range, a, b, spec.offset)} · ${cur.size} 次作业", end = b, prevLabel = prevName)
            }
        }
    }

    // ---------------- 作业指标 ----------------

    data class Kpi(
        val assignments: Int,
        val handInRate: Float,
        val onTimeRate: Float,
        val excellentRate: Float,
        val missing: Int,
        val late: Int,
        val leave: Int,
        val unregistered: Int,
        val perfect: Int,
        val expected: Int = 0,
    ) {
        val missingRate: Float get() = if (expected == 0) 0f else missing.toFloat() / expected
        val lateRate: Float get() = if (expected == 0) 0f else late.toFloat() / expected
    }

    data class Point(val assignment: Assignment, val summary: AssignmentSummary)

    data class StudentInsight(
        val student: Student,
        val expected: Int,
        val handedIn: Int,
        val missing: Int,
        val late: Int,
        val missingStreak: Int,
        val trend: Float,
        val onTime: Int = 0,
        val excellent: Int = 0,
        val leave: Int = 0,
        val unregistered: Int = 0,
        /** 范围内最长连续未交 */
        val longestMissing: Int = 0,
        /** 当前积分 / 范围内积分变化 / 名次 */
        val score: Double = 0.0,
        val scoreDelta: Double = 0.0,
        val rank: Int = 0,
        /** 范围内考试平均得分率（0–1），没有考试为 null */
        val examPct: Double? = null,
        /** 最近连续交齐的次数（请假、未登记不打断） */
        val handStreak: Int = 0,
    ) {
        val rate: Float get() = if (expected == 0) 1f else handedIn.toFloat() / expected
        val onTimeRate: Float get() = if (expected == 0) 1f else onTime.toFloat() / expected
    }

    data class SubjectStat(val subject: String, val count: Int, val rate: Float, val missing: Int, val onTimeRate: Float = rate)

    data class GroupStat(val name: String, val leader: Student?, val size: Int, val rate: Float, val missing: Int, val avgScore: Double, val scoreDelta: Double, val examPct: Double?)

    /** 按日 / 周 / 月汇总的一段 */
    data class Bucket(val label: String, val start: Long, val count: Int, val rate: Float, val onTimeRate: Float, val missing: Int)

    /** 范围内全班积分来源 */
    data class ScoreSources(val homework: Double = 0.0, val manualPlus: Double = 0.0, val manualMinus: Double = 0.0, val exam: Double = 0.0, val manualCount: Int = 0)

    /** 手动加减分原因统计 */
    data class ReasonStat(val reason: String, val count: Int, val total: Double, val people: Int)

    /** 学生分层 */
    data class Tier(val name: String, val desc: String, val level: Level, val students: List<StudentInsight>)

    /** 下次缺交风险（近 8 次加权，越近权重越大） */
    data class Risk(val student: Student, val prob: Double, val why: String)

    /** 偏科：某一科交齐率明显低于自己的整体水平 */
    data class WeakSubject(val student: Student, val subject: String, val subjectRate: Float, val overallRate: Float, val count: Int)

    /** 各轮对比（以全班重置为分界） */
    data class Round(val index: Int, val label: String, val start: Long?, val end: Long?, val kpi: Kpi, val examPct: Double?, val reset: ScoreRecharge?)

    /** 进步 / 退步：以往（重置前）vs 重置后 */
    data class Side(val expected: Int, val rate: Float, val onTimeRate: Float, val excellentRate: Float, val missing: Int, val examPct: Double?, val pace: Double)
    data class StudentProgress(val student: Student, val before: Side, val after: Side, val index: Double, val reasons: List<String>)
    data class Progress(val basis: String, val reset: ScoreRecharge?, val stars: List<StudentProgress>, val watch: List<StudentProgress>, val all: List<StudentProgress>)

    enum class Level { GOOD, INFO, WARN }
    data class Insight(val level: Level, val text: String)

    data class Report(
        val range: Range,
        val window: Window,
        val points: List<Point>,
        val kpi: Kpi,
        val previous: Kpi?,
        val totals: Map<HwStatus, Int>,
        val weekday: List<Float?>,
        val subjects: List<SubjectStat>,
        val students: List<StudentInsight>,
        val attention: List<StudentInsight>,
        val improved: List<StudentInsight>,
        val perfectAttendance: List<StudentInsight>,
        val scoreBins: List<Pair<String, Int>>,
        val groups: List<Pair<String, Double>>,
        val groupStats: List<GroupStat> = emptyList(),
        /** 学生交齐率分布 */
        val rateBins: List<Pair<String, Int>> = emptyList(),
        /** 每次作业之后的全班平均积分 */
        val scoreTrend: List<Pair<Assignment, Double>> = emptyList(),
        val risers: List<StudentInsight> = emptyList(),
        val fallers: List<StudentInsight> = emptyList(),
        /** 有未登记同学的作业 */
        val unregistered: List<Pair<Assignment, Int>> = emptyList(),
        /** 交齐率每次作业的线性趋势（正数为上升，单位：比例/次） */
        val slope: Float = 0f,
        /** 最长连续“全员交齐”次数 */
        val longestPerfectRun: Int = 0,
        val avgScore: Double = 0.0,
        val avgScoreDelta: Double = 0.0,
        /** 作业交齐率与考试得分率的相关系数 */
        val examCorrelation: Double? = null,
        val insights: List<Insight> = emptyList(),
        val spec: Spec = Spec(range),
        /** 按日 / 周 / 月汇总的交齐率（[bucketUnit] 为“日”“周”“月”） */
        val buckets: List<Bucket> = emptyList(),
        val bucketUnit: String = "",
        val sources: ScoreSources = ScoreSources(),
        val reasons: List<ReasonStat> = emptyList(),
        val tiers: List<Tier> = emptyList(),
        /** 连续交齐榜 */
        val streakers: List<StudentInsight> = emptyList(),
        val risk: List<Risk> = emptyList(),
        val weakSubjects: List<WeakSubject> = emptyList(),
        /** 学生交齐率的中位数与标准差（看两极分化） */
        val rateMedian: Float = 0f,
        val rateStd: Float = 0f,
        val rounds: List<Round> = emptyList(),
        val progress: Progress? = null,
        /** 范围内的考试场次 */
        val examCount: Int = 0,
        /** 信誉分（当前）与本时段的信誉分加减分 */
        val credit: CreditSummary? = null,
    )

    /** 兼容旧接口：按范围选出作业和上一时段 */
    fun pick(all: List<Assignment>, range: Range, now: Long = System.currentTimeMillis()): Pair<List<Assignment>, List<Assignment>> {
        val sorted = all.sortedBy { it.createdAt }
        return when (range) {
            Range.RECENT10 -> sorted.takeLast(10) to sorted.dropLast(10).takeLast(10)
            else -> sorted to emptyList()
        }
    }

    fun kpi(c: SchoolClass, list: List<Assignment>): Kpi {
        var expected = 0; var handed = 0; var onTime = 0; var excellent = 0
        var missing = 0; var late = 0; var leave = 0; var none = 0; var perfect = 0
        for (a in list) {
            val s = c.summary(a)
            expected += s.expected; handed += s.handedIn
            onTime += s.c(HwStatus.SUBMITTED) + s.c(HwStatus.EXCELLENT)
            excellent += s.c(HwStatus.EXCELLENT)
            missing += s.c(HwStatus.MISSING); late += s.c(HwStatus.LATE); leave += s.c(HwStatus.LEAVE); none += s.c(HwStatus.NONE)
            if (s.expected > 0 && s.handedIn >= s.expected) perfect++
        }
        fun r(x: Int) = if (expected == 0) 0f else x.toFloat() / expected
        return Kpi(list.size, r(handed), r(onTime), r(excellent), missing, late, leave, none, perfect, expected)
    }

    /** 某一时刻（含）之前已发生的数据算出的积分 */
    fun scoresAt(c: SchoolClass, rules: ScoreRules, t: Long): Map<String, Double> =
        c.copy(
            assignments = c.assignments.filter { it.createdAt <= t },
            scores = c.scores.filter { it.time <= t },
            exams = c.exams.filter { it.date <= t },
            recharges = c.recharges.filter { it.start <= t },
        ).studentStats(rules).associate { it.student.id to it.score }

    /** 线性回归斜率 */
    fun slope(ys: List<Double>): Double {
        val n = ys.size; if (n < 3) return 0.0
        val mx = (n - 1) / 2.0; val my = ys.average()
        var num = 0.0; var den = 0.0
        ys.forEachIndexed { i, y -> num += (i - mx) * (y - my); den += (i - mx) * (i - mx) }
        return if (den == 0.0) 0.0 else num / den
    }

    /** 皮尔逊相关系数（至少 4 对数据） */
    fun pearson(xs: List<Double>, ys: List<Double>): Double? {
        val n = minOf(xs.size, ys.size); if (n < 4) return null
        val mx = xs.take(n).average(); val my = ys.take(n).average()
        var sxy = 0.0; var sxx = 0.0; var syy = 0.0
        for (i in 0 until n) { val dx = xs[i] - mx; val dy = ys[i] - my; sxy += dx * dy; sxx += dx * dx; syy += dy * dy }
        return if (sxx == 0.0 || syy == 0.0) null else sxy / sqrt(sxx * syy)
    }

    fun report(c: SchoolClass, rules: ScoreRules, range: Range, now: Long = System.currentTimeMillis()): Report = report(c, rules, Spec(range), now)

    fun report(c: SchoolClass, rules: ScoreRules, spec: Spec, now: Long = System.currentTimeMillis()): Report {
        val range = spec.range
        val w = window(c, spec, now)
        val endT = w.end?.let { minOf(it - 1, now) }
        val list = w.assignments
        val points = list.map { Point(it, c.summary(it)) }
        val totals = HashMap<HwStatus, Int>()
        points.forEach { p -> p.summary.counts.forEach { (k, v) -> totals[k] = (totals[k] ?: 0) + v } }

        val cal = Calendar.getInstance()
        val byDay = Array(7) { ArrayList<Float>() }
        points.forEach { p -> cal.timeInMillis = p.assignment.createdAt; byDay[(cal.get(Calendar.DAY_OF_WEEK) + 5) % 7] += p.summary.rate }
        val weekday = byDay.map { if (it.isEmpty()) null else it.average().toFloat() }

        val subjects = points.groupBy { it.assignment.subject.ifBlank { "其他" } }.map { (k, l) ->
            val exp = l.sumOf { it.summary.expected }
            SubjectStat(
                k, l.size, l.map { it.summary.rate }.average().toFloat(), l.sumOf { it.summary.c(HwStatus.MISSING) },
                if (exp == 0) 0f else l.sumOf { it.summary.c(HwStatus.SUBMITTED) + it.summary.c(HwStatus.EXCELLENT) }.toFloat() / exp,
            )
        }.sortedByDescending { it.rate }

        // 积分：当前 / 范围起点
        val stats = c.studentStats(rules)
        // 过去的周期：积分取周期结束时的值
        val nowScore = if (endT != null && endT < now) scoresAt(c, rules, endT) else stats.associate { it.student.id to it.score }
        val startScore: Map<String, Double> = when {
            w.reset != null -> c.students.associate { it.id to w.reset.target }
            w.start != null -> scoresAt(c, rules, w.start - 1)
            else -> c.students.associate { it.id to rules.initial }
        }
        val rankOf = stats.sortedByDescending { it.score }.mapIndexed { i, s -> s.student.id to i + 1 }.toMap()
        val examsIn = c.exams.filter { e -> (w.start == null || e.date >= w.start) && (w.end == null || e.date < w.end) }.sortedBy { it.date }
        val examPctOf: Map<String, Double> = c.students.associate { st ->
            val v = examsIn.mapNotNull { e -> e.scores[st.id]?.let { if (e.fullScore > 0) it / e.fullScore else null } }
            st.id to (if (v.isEmpty()) Double.NaN else v.average())
        }.filterValues { !it.isNaN() }

        val students = c.students.map { st ->
            insight(st, list).copy(
                score = nowScore[st.id] ?: 0.0,
                scoreDelta = (nowScore[st.id] ?: 0.0) - (startScore[st.id] ?: rules.initial),
                rank = rankOf[st.id] ?: 0,
                examPct = examPctOf[st.id],
            )
        }
        val attention = students.filter { it.missing > 0 || it.missingStreak >= 2 }
            .sortedWith(compareByDescending<StudentInsight> { it.missingStreak }.thenByDescending { it.missing }.thenByDescending { it.late })
            .take(8)
        val improved = students.filter { it.trend >= 0.2f && it.expected >= 4 }.sortedByDescending { it.trend }.take(5)
        val perfect = students.filter { it.expected > 0 && it.missing == 0 && it.late == 0 }

        val scores = c.students.map { nowScore[it.id] ?: rules.initial }
        val scoreBins = if (scores.isEmpty()) emptyList() else {
            val lo = kotlin.math.floor(scores.min()).toInt()
            val hi = kotlin.math.floor(scores.max()).toInt()
            val step = maxOf(1, (hi - lo + 7) / 7)
            (lo..hi step step).map { b -> (if (step == 1) "$b" else "$b~${b + step - 1}") to scores.count { it >= b && it < b + step } }
        }
        val groups = c.students.groupBy { it.group.ifBlank { "未分组" } }.map { (g, l) -> g to l.sumOf { nowScore[it.id] ?: rules.initial } / l.size }.sortedByDescending { it.second }
        val byId = students.associateBy { it.student.id }
        val groupStats = c.students.groupBy { it.group.ifBlank { "未分组" } }.map { (g, members) ->
            val ins = members.mapNotNull { byId[it.id] }
            val exp = ins.sumOf { it.expected }
            val ex = ins.mapNotNull { it.examPct }
            GroupStat(
                g, c.leaderOf(g), members.size, if (exp == 0) 1f else ins.sumOf { it.handedIn }.toFloat() / exp, ins.sumOf { it.missing },
                ins.map { it.score }.average(), ins.map { it.scoreDelta }.average(), if (ex.isEmpty()) null else ex.average(),
            )
        }.sortedByDescending { it.rate }

        val rateBins = listOf("<60%" to (0f to 0.6f), "60–70%" to (0.6f to 0.7f), "70–80%" to (0.7f to 0.8f), "80–90%" to (0.8f to 0.9f), "90–99%" to (0.9f to 1f), "100%" to (1f to 1.01f))
            .map { (label, r) -> label to students.count { it.expected > 0 && it.rate >= r.first && it.rate < r.second } }

        // 每个点都要重算一次全班积分，最多取 10 个点（均匀抽取，保留首尾）
        val trendPts = if (points.size <= 10) points else (0 until 10).map { k -> points[k * (points.size - 1) / 9] }.distinct()
        val scoreTrend = if (trendPts.size < 2) emptyList() else trendPts.map { p -> p.assignment to scoresAt(c, rules, p.assignment.createdAt).values.average() }
        val movers = students.filter { abs(it.scoreDelta) > 1e-9 }
        val risers = movers.filter { it.scoreDelta > 0 }.sortedByDescending { it.scoreDelta }.take(5)
        val fallers = movers.filter { it.scoreDelta < 0 }.sortedBy { it.scoreDelta }.take(5)
        val unregistered = points.filter { it.summary.c(HwStatus.NONE) > 0 }.map { it.assignment to it.summary.c(HwStatus.NONE) }.sortedByDescending { it.first.createdAt }

        var run = 0; var best = 0
        points.forEach { p -> if (p.summary.expected > 0 && p.summary.handedIn >= p.summary.expected) { run++; best = maxOf(best, run) } else run = 0 }

        val corrPairs = students.filter { it.expected >= 3 && it.examPct != null }
        val corr = pearson(corrPairs.map { it.rate.toDouble() }, corrPairs.map { it.examPct!! })

        val k = kpi(c, list)
        val prev = if (w.previous.isEmpty()) null else kpi(c, w.previous)
        val (buckets, unit) = buckets(c, points, range)
        val withData = students.filter { it.expected > 0 }
        val rates = withData.map { it.rate }.sorted()
        val median = if (rates.isEmpty()) 0f else if (rates.size % 2 == 1) rates[rates.size / 2] else (rates[rates.size / 2 - 1] + rates[rates.size / 2]) / 2
        val mean = if (rates.isEmpty()) 0f else rates.average().toFloat()
        val std = if (rates.size < 2) 0f else sqrt(rates.sumOf { ((it - mean) * (it - mean)).toDouble() } / rates.size).toFloat()
        val base = Report(
            range, w, points, k, prev, totals, weekday, subjects, students, attention, improved, perfect, scoreBins, groups,
            groupStats = groupStats, rateBins = rateBins, scoreTrend = scoreTrend, risers = risers, fallers = fallers,
            unregistered = unregistered, slope = slope(points.map { it.summary.rate.toDouble() }).toFloat(),
            longestPerfectRun = best, avgScore = if (scores.isEmpty()) 0.0 else scores.average(),
            avgScoreDelta = if (students.isEmpty()) 0.0 else students.map { it.scoreDelta }.average(), examCorrelation = corr,
            spec = spec, buckets = buckets, bucketUnit = unit,
            sources = sources(c, rules, list, w, examsIn), reasons = reasons(c, w),
            tiers = tiers(withData), streakers = withData.filter { it.handStreak >= 3 }.sortedByDescending { it.handStreak }.take(8),
            risk = risk(c), weakSubjects = weakSubjects(c, list, byId),
            rateMedian = median, rateStd = std, rounds = rounds(c), progress = progress(c, rules), examCount = examsIn.size,
            credit = Credit.classSummary(c, rules, now) { inWindow(w, it) },
        )
        return base.copy(insights = insights(base, corrPairs))
    }

    // ---------------- 更多分析项 ----------------

    /** 交齐率按日 / 周 / 月汇总：一天看不出汇总，一周按日，一月或一季按周，更长按月 */
    fun buckets(c: SchoolClass, points: List<Point>, range: Range): Pair<List<Bucket>, String> {
        if (points.size < 2 || range == Range.DAY) return emptyList<Bucket>() to ""
        val first = points.first().assignment.createdAt; val last = points.last().assignment.createdAt
        val span = (last - first) / 86_400_000L
        val unit = when (range) {
            Range.WEEK -> "日"
            Range.MONTH, Range.QUARTER -> "周"
            Range.YEAR -> "月"
            else -> if (span <= 14) "日" else if (span <= 120) "周" else "月"
        }
        val cal = Calendar.getInstance()
        fun keyOf(t: Long): Long {
            cal.timeInMillis = startOfDay(t)
            when (unit) {
                "周" -> cal.add(Calendar.DAY_OF_YEAR, -((cal.get(Calendar.DAY_OF_WEEK) + 5) % 7))
                "月" -> cal.set(Calendar.DAY_OF_MONTH, 1)
            }
            return cal.timeInMillis
        }
        val out = points.groupBy { keyOf(it.assignment.createdAt) }.toSortedMap().map { (k, l) ->
            val exp = l.sumOf { it.summary.expected }
            val label = when (unit) {
                "月" -> java.text.SimpleDateFormat(if (span > 330) "yy年M月" else "M月", java.util.Locale.CHINA).format(java.util.Date(k))
                "周" -> java.text.SimpleDateFormat("M/d", java.util.Locale.CHINA).format(java.util.Date(k)) + "周"
                else -> java.text.SimpleDateFormat("M/d", java.util.Locale.CHINA).format(java.util.Date(k))
            }
            Bucket(
                label, k, l.size,
                if (exp == 0) 1f else l.sumOf { it.summary.handedIn }.toFloat() / exp,
                if (exp == 0) 1f else l.sumOf { it.summary.c(HwStatus.SUBMITTED) + it.summary.c(HwStatus.EXCELLENT) }.toFloat() / exp,
                l.sumOf { it.summary.c(HwStatus.MISSING) },
            )
        }
        return (if (out.size >= 2) out else emptyList()) to unit
    }

    private fun inWindow(w: Window, t: Long) = (w.start == null || t >= w.start) && (w.end == null || t < w.end)

    fun sources(c: SchoolClass, rules: ScoreRules, list: List<Assignment>, w: Window, exams: List<Exam>): ScoreSources {
        var hw = 0.0
        val all = c.autoPointsAll(rules)
        for (a in list) for (st in c.students) hw += all[st.id]?.get(a.id) ?: rules.of(a.statuses[st.id] ?: HwStatus.NONE)
        val manual = c.scores.filter { inWindow(w, it.time) }
        val ex = exams.sumOf { e -> c.students.sumOf { rules.pt(rules.exam.deltaFor(e, it.id)) } }
        return ScoreSources(hw, manual.filter { it.delta > 0 }.sumOf { rules.pt(it.delta) }, manual.filter { it.delta < 0 }.sumOf { rules.pt(it.delta) }, ex, manual.size)
    }

    fun reasons(c: SchoolClass, w: Window): List<ReasonStat> =
        c.scores.filter { inWindow(w, it.time) }.groupBy { it.reason.trim().ifBlank { if (it.delta > 0) "加分" else "扣分" } }
            .map { (r, l) -> ReasonStat(r, l.size, l.sumOf { it.delta }, l.map { it.studentId }.distinct().size) }
            .sortedWith(compareByDescending<ReasonStat> { it.count }.thenByDescending { abs(it.total) }).take(10)

    fun tiers(list: List<StudentInsight>): List<Tier> {
        val perfect = list.filter { it.missing == 0 && it.late == 0 && it.unregistered == 0 }
        val rest = list - perfect.toSet()
        val steady = rest.filter { it.rate >= 0.9f && it.missingStreak < 2 }
        val wobble = rest.filter { it.rate >= 0.7f && it.rate < 0.9f && it.missingStreak < 2 }
        val alert = rest.filter { it.rate < 0.7f || it.missingStreak >= 2 }
        return listOf(
            Tier("全勤", "没有缺交和迟交", Level.GOOD, perfect),
            Tier("稳定", "交齐率 ≥ 90%", Level.GOOD, steady.sortedByDescending { it.rate }),
            Tier("波动", "交齐率 70%–90%", Level.INFO, wobble.sortedByDescending { it.rate }),
            Tier("需关注", "交齐率 < 70% 或连续未交", Level.WARN, alert.sortedBy { it.rate }),
        )
    }

    /** 下次缺交风险：最近 8 次（不含请假、未登记），越近权重越大（0.75 衰减） */
    fun risk(c: SchoolClass): List<Risk> {
        val sorted = c.assignments.sortedByDescending { it.createdAt }
        return c.students.mapNotNull { st ->
            val marks = sorted.mapNotNull { a ->
                when (a.statuses[st.id] ?: HwStatus.NONE) { HwStatus.MISSING -> 1.0; HwStatus.LATE -> 0.4; HwStatus.SUBMITTED, HwStatus.EXCELLENT -> 0.0; else -> null }
            }.take(8)
            if (marks.size < 3) return@mapNotNull null
            var num = 0.0; var den = 0.0; var w = 1.0
            for (m in marks) { num += w * m; den += w; w *= 0.75 }
            val p = num / den
            val recentMiss = marks.take(3).count { it == 1.0 }
            if (p < 0.3) null else Risk(st, p, "近 ${marks.size} 次缺交 ${marks.count { it == 1.0 }} 次、迟交 ${marks.count { it == 0.4 }} 次" + if (recentMiss > 0) "，最近 3 次缺 $recentMiss 次" else "")
        }.sortedByDescending { it.prob }.take(8)
    }

    fun weakSubjects(c: SchoolClass, list: List<Assignment>, byId: Map<String, StudentInsight>): List<WeakSubject> {
        val bySubject = list.groupBy { it.subject.ifBlank { "其他" } }.filterValues { it.size >= 2 }
        if (bySubject.size < 2) return emptyList()
        return c.students.mapNotNull { st ->
            val overall = byId[st.id] ?: return@mapNotNull null
            if (overall.expected < 4) return@mapNotNull null
            val worst = bySubject.mapNotNull { (sub, l) -> insight(st, l).takeIf { it.expected >= 2 }?.let { sub to it } }.minByOrNull { it.second.rate } ?: return@mapNotNull null
            if (overall.rate - worst.second.rate < 0.2f) null else WeakSubject(st, worst.first, worst.second.rate, overall.rate, worst.second.expected)
        }.sortedBy { it.subjectRate - it.overallRate }.take(8)
    }

    /** 以全班重置为分界的各轮 */
    fun rounds(c: SchoolClass): List<Round> {
        val resets = classResets(c)
        if (resets.isEmpty()) return emptyList()
        val sorted = c.assignments.sortedBy { it.createdAt }
        val out = ArrayList<Round>()
        for (i in 0..resets.size) {
            val from = resets.getOrNull(i - 1)
            val to = resets.getOrNull(i)
            fun inside(t: Long) = (from == null || from.counts(t)) && (to == null || !to.counts(t))
            val l = sorted.filter { inside(it.createdAt) }
            if (l.isEmpty() && to != null) continue
            val pcts = c.exams.filter { inside(it.date) }.flatMap { e -> e.scores.values.map { it / e.fullScore } }
            val label = if (from == null) "首次重置前" else "第 $i 次重置后" + if (to == null) "（本轮）" else ""
            out += Round(i, label, from?.start, to?.start, kpi(c, l), if (pcts.isEmpty()) null else pcts.average(), from)
        }
        return out
    }

    private fun side(c: SchoolClass, rules: ScoreRules, st: Student, list: List<Assignment>, exams: List<Exam>): Side {
        val ins = insight(st, list)
        val auto = c.autoPoints(rules, st.id)
        val pts = list.mapNotNull { a -> (a.statuses[st.id] ?: HwStatus.NONE).takeIf { it != HwStatus.NONE && it != HwStatus.LEAVE }?.let { auto[a.id] ?: rules.of(it) } }
        val ex = exams.mapNotNull { e -> e.scores[st.id]?.let { it / e.fullScore } }
        return Side(
            ins.expected - ins.unregistered, if (ins.expected - ins.unregistered == 0) 1f else ins.handedIn.toFloat() / (ins.expected - ins.unregistered),
            if (ins.expected - ins.unregistered == 0) 1f else ins.onTime.toFloat() / (ins.expected - ins.unregistered),
            if (ins.expected - ins.unregistered == 0) 0f else ins.excellent.toFloat() / (ins.expected - ins.unregistered),
            ins.missing, if (ex.isEmpty()) null else ex.average(), if (pts.isEmpty()) 0.0 else pts.average(),
        )
    }

    /**
     * 进步之星 / 退步关注：比较“以往”（最近一次全班重置之前的全部记录）与“重置后”。
     * 重置后至少要有 2 次作业才比较；不够时往前找上一次重置。还没有全班重置时，比较前半段与后半段。
     * 综合指数 = 交齐率变化×0.45 + 按时率×0.2 + 优秀率×0.15 + 考试得分率×0.2（没有考试时按比例分给其他项）。
     */
    fun progress(c: SchoolClass, rules: ScoreRules): Progress? {
        val sorted = c.assignments.sortedBy { it.createdAt }
        if (sorted.size < 4 || c.students.isEmpty()) return null
        val resets = classResets(c)
        val r = resets.asReversed().firstOrNull { rr -> sorted.count { rr.counts(it.createdAt) } >= 2 && sorted.count { !rr.counts(it.createdAt) } >= 2 }
        val (before, after, basis) = if (r != null) {
            Triple(sorted.filter { !r.counts(it.createdAt) }, sorted.filter { r.counts(it.createdAt) }, run {
                val at = if (r.assignmentId != null) "「${r.assignmentTitle.ifBlank { "所选作业" }}」" else fmtDay(r.start)
                "重置前（$at 之前）vs 重置后（$at 起）"
            })
        } else {
            val h = sorted.size / 2
            Triple(sorted.take(h), sorted.drop(h), "还没有全班重置：前 $h 次 vs 后 ${sorted.size - h} 次")
        }
        val cut = after.first().createdAt
        val exBefore = c.exams.filter { it.date < cut }; val exAfter = c.exams.filter { it.date >= cut }
        val all = c.students.mapNotNull { st ->
            val b = side(c, rules, st, before, exBefore); val a = side(c, rules, st, after, exAfter)
            if (b.expected < 2 || a.expected < 2) return@mapNotNull null
            val dr = (a.rate - b.rate).toDouble(); val dt = (a.onTimeRate - b.onTimeRate).toDouble(); val de = (a.excellentRate - b.excellentRate).toDouble()
            val dx = if (a.examPct != null && b.examPct != null) a.examPct - b.examPct else null
            val idx = if (dx != null) dr * 0.45 + dt * 0.2 + de * 0.15 + dx * 0.2 else (dr * 0.45 + dt * 0.2 + de * 0.15) / 0.8
            val why = ArrayList<Pair<Double, String>>()
            fun p(x: Float) = "${(x * 100).roundToInt()}%"
            if (abs(dr) >= 0.05) why += abs(dr) to "交齐率 ${p(b.rate)}→${p(a.rate)}"
            if (abs(dt) >= 0.08) why += abs(dt) * 0.8 to "按时率 ${p(b.onTimeRate)}→${p(a.onTimeRate)}"
            if (abs(de) >= 0.08) why += abs(de) * 0.7 to "优秀率 ${p(b.excellentRate)}→${p(a.excellentRate)}"
            if (dx != null && abs(dx) >= 0.04) why += abs(dx) to "考试得分率 ${(b.examPct!! * 100).roundToInt()}%→${(a.examPct!! * 100).roundToInt()}%"
            if (abs(a.pace - b.pace) >= 0.3) why += 0.01 to "每次作业平均得分 ${b.pace.fmt()}→${a.pace.fmt()}"
            StudentProgress(st, b, a, idx, why.sortedByDescending { it.first }.map { it.second })
        }
        val stars = all.filter { it.index >= 0.05 }.sortedByDescending { it.index }.take(8)
        val watch = all.filter { it.index <= -0.05 || it.after.rate - it.before.rate <= -0.15f }.sortedBy { it.index }.take(8)
        return Progress(basis, r, stars, watch, all)
    }

    fun insight(st: Student, list: List<Assignment>): StudentInsight {
        var expected = 0; var handed = 0; var missing = 0; var late = 0; var onTime = 0; var excellent = 0; var leave = 0; var none = 0
        var longest = 0; var cur = 0
        val marks = ArrayList<Int>()
        for (a in list) {
            when (a.statuses[st.id] ?: HwStatus.NONE) {
                HwStatus.SUBMITTED -> { expected++; handed++; onTime++; marks += 1; cur = 0 }
                HwStatus.EXCELLENT -> { expected++; handed++; onTime++; excellent++; marks += 1; cur = 0 }
                HwStatus.LATE -> { expected++; handed++; late++; marks += 1; cur = 0 }
                HwStatus.MISSING -> { expected++; missing++; marks += 0; cur++; longest = maxOf(longest, cur) }
                HwStatus.NONE -> { expected++; none++ }
                HwStatus.LEAVE -> leave++
            }
        }
        var streak = 0
        for (m in marks.asReversed()) if (m == 0) streak++ else break
        var hand = 0
        for (m in marks.asReversed()) if (m == 1) hand++ else break
        val half = marks.size / 2
        val trend = if (half < 2) 0f else marks.drop(marks.size - half).average().toFloat() - marks.take(half).average().toFloat()
        return StudentInsight(st, expected, handed, missing, late, streak, trend, onTime, excellent, leave, none, longest, handStreak = hand)
    }

    private fun pct(x: Float) = "${(x * 100).roundToInt()}%"
    private fun pct(x: Double) = "${(x * 100).roundToInt()}%"
    private fun names(l: List<StudentInsight>, n: Int = 6) = l.take(n).joinToString("、") { it.student.name } + if (l.size > n) " 等 ${l.size} 人" else ""

    /** 自动结论：只说有依据、有差距的事 */
    private fun insights(r: Report, corrPairs: List<StudentInsight>): List<Insight> {
        val out = ArrayList<Insight>()
        val k = r.kpi
        if (r.points.isEmpty()) return out
        // 总体与趋势
        val per = r.slope * 100
        if (r.points.size >= 4 && abs(per) >= 0.8f)
            out += Insight(if (per > 0) Level.GOOD else Level.WARN, "交齐率呈${if (per > 0) "上升" else "下降"}趋势，平均每次${if (per > 0) "提高" else "下降"} ${"%.1f".format(abs(per))} 个百分点。")
        r.previous?.let { p ->
            val d = ((k.handInRate - p.handInRate) * 100).roundToInt()
            if (abs(d) >= 2) out += Insight(if (d > 0) Level.GOOD else Level.WARN, "与${r.window.prevLabel}相比交齐率${if (d > 0) "提高" else "下降"} ${abs(d)} 个百分点（${pct(p.handInRate)} → ${pct(k.handInRate)}）。")
            val m = ((k.missingRate - p.missingRate) * 100).roundToInt()
            if (abs(m) >= 2) out += Insight(if (m < 0) Level.GOOD else Level.WARN, "缺交率${if (m < 0) "降低" else "升高"} ${abs(m)} 个百分点。")
        }
        if (r.longestPerfectRun >= 2) out += Insight(Level.GOOD, "最长连续 ${r.longestPerfectRun} 次全员交齐。")
        if (k.lateRate >= 0.08f) out += Insight(Level.WARN, "迟交占应交人次的 ${pct(k.lateRate)}，按时率只有 ${pct(k.onTimeRate)}，可以强调截止时间。")
        // 科目、星期
        if (r.subjects.size >= 2) {
            val b = r.subjects.first(); val wst = r.subjects.last()
            if ((b.rate - wst.rate) >= 0.05f) out += Insight(Level.INFO, "${wst.subject}交齐率最低（${pct(wst.rate)}），比${b.subject}低 ${((b.rate - wst.rate) * 100).roundToInt()} 个百分点。")
        }
        val days = listOf("一", "二", "三", "四", "五", "六", "日")
        val wd = r.weekday.mapIndexedNotNull { i, v -> v?.let { i to it } }
        if (wd.size >= 3) {
            val lo = wd.minBy { it.second }; val hi = wd.maxBy { it.second }
            if (hi.second - lo.second >= 0.05f) out += Insight(Level.INFO, "周${days[lo.first]}布置的作业交得最差（${pct(lo.second)}），周${days[hi.first]}最好（${pct(hi.second)}）。")
        }
        // 学生
        val streak = r.students.filter { it.missingStreak >= 2 }.sortedByDescending { it.missingStreak }
        if (streak.isNotEmpty()) out += Insight(Level.WARN, "${names(streak)} 已连续 2 次及以上未交，建议尽快沟通。")
        val low = r.students.filter { it.expected >= 3 && it.rate < 0.7f }.sortedBy { it.rate }
        if (low.isNotEmpty()) out += Insight(Level.WARN, "${low.size} 人交齐率低于 70%：${names(low)}。")
        val lates = r.students.filter { it.late >= 3 }.sortedByDescending { it.late }
        if (lates.isNotEmpty()) out += Insight(Level.INFO, "${names(lates)} 经常迟交（≥3 次）。")
        r.progress?.let { pg ->
            if (pg.stars.isNotEmpty()) out += Insight(Level.GOOD, "进步之星（重置后比以往更好）：" + pg.stars.take(5).joinToString("、") { "${it.student.name}" + (it.reasons.firstOrNull()?.let { w -> "（$w）" } ?: "") } + "。")
            if (pg.watch.isNotEmpty()) out += Insight(Level.WARN, "退步关注（重置后不如以往）：" + pg.watch.take(5).joinToString("、") { "${it.student.name}" + (it.reasons.firstOrNull()?.let { w -> "（$w）" } ?: "") } + "。")
        }
        if (r.risk.isNotEmpty()) out += Insight(Level.WARN, "下次缺交风险较高：" + r.risk.take(5).joinToString("、") { "${it.student.name}（${pct(it.prob)}）" } + "，可以提前提醒。")
        if (r.weakSubjects.isNotEmpty()) out += Insight(Level.INFO, "偏科：" + r.weakSubjects.take(4).joinToString("；") { "${it.student.name}的${it.subject}交齐率 ${pct(it.subjectRate)}（整体 ${pct(it.overallRate)}）" } + "。")
        if (r.rateStd >= 0.15f && r.students.size >= 6) out += Insight(Level.INFO, "交齐率两极分化：中位数 ${pct(r.rateMedian)}，标准差 ${(r.rateStd * 100).roundToInt()} 个百分点，少数同学拉低了整体。")
        r.streakers.firstOrNull()?.let { if (it.handStreak >= 5) out += Insight(Level.GOOD, "${names(r.streakers.filter { s -> s.handStreak >= 5 }, 4)} 已连续 ${it.handStreak} 次及以上按要求交齐。") }
        if (r.perfectAttendance.isNotEmpty()) out += Insight(Level.GOOD, "${r.perfectAttendance.size} 人无缺交、无迟交。")
        // 小组
        val gs = r.groupStats.filter { it.name != "未分组" && it.size >= 2 }
        if (gs.size >= 2) {
            val b = gs.maxBy { it.rate }; val wst = gs.minBy { it.rate }
            if (b.rate - wst.rate >= 0.08f) out += Insight(Level.INFO, "小组差距：${b.name}交齐率 ${pct(b.rate)}，${wst.name}只有 ${pct(wst.rate)}${wst.leader?.let { "（组长 ${it.name}）" } ?: ""}。")
        }
        // 积分
        if (r.range != Range.ALL && abs(r.avgScoreDelta) >= 0.5) out += Insight(Level.INFO, "本时段全班平均积分${if (r.avgScoreDelta > 0) "增加" else "减少"} ${abs(r.avgScoreDelta).fmt()} 分。")
        val src = r.sources
        if (src.manualCount >= 3 && src.manualMinus < 0 && -src.manualMinus > src.manualPlus * 1.5) out += Insight(Level.INFO, "手动扣分（${src.manualMinus.fmt()}）明显多于加分（+${src.manualPlus.fmt()}），可以多用表扬加分。")
        r.reasons.firstOrNull()?.let { if (it.count >= 3) out += Insight(Level.INFO, "最常见的加减分原因是“${it.reason}”（${it.count} 次，涉及 ${it.people} 人，合计 ${it.total.signed()}）。") }
        // 考试相关
        r.examCorrelation?.let { cr ->
            if (cr >= 0.3) {
                val hi = corrPairs.filter { it.rate >= 0.9f }.mapNotNull { it.examPct }; val lo = corrPairs.filter { it.rate < 0.9f }.mapNotNull { it.examPct }
                val tail = if (hi.isNotEmpty() && lo.isNotEmpty()) "：交齐率 ≥90% 的同学考试平均得分率 ${pct(hi.average())}，其余 ${pct(lo.average())}" else ""
                out += Insight(Level.INFO, "作业交齐率与考试成绩正相关（r = ${"%.2f".format(cr)}）$tail。")
            } else if (cr <= -0.3) out += Insight(Level.INFO, "作业交齐率与考试成绩呈负相关（r = ${"%.2f".format(cr)}），可能存在“交了但没做好”的情况。")
        }
        // 数据
        if (k.unregistered > 0) out += Insight(Level.WARN, "有 ${k.unregistered} 人次未登记（${r.unregistered.size} 次作业），统计可能偏低，建议补登记。")
        return out
    }

    /** 报告标题里的范围名 */
    fun rangeTitle(r: Report) = when {
        r.range.calendar || r.range == Range.CUSTOM -> r.window.label.substringBefore(" · ").substringBefore("（")
        else -> r.range.label
    }

    /** 报告里信誉分的几行文字（文字报告、PDF 共用） */
    fun creditLines(cs: CreditSummary): List<String> = listOfNotNull(
        "信誉分：全班平均 ${cs.average.fmt()}；" + cs.levels.joinToString("，") { "${it.first} ${it.second} 人" },
        cs.top.takeIf { it.isNotEmpty() }?.let { l -> "信誉最高：" + l.joinToString("、") { "${it.student.name} ${it.index.fmt()}" } },
        cs.bottom.takeIf { it.isNotEmpty() }?.let { l -> "信誉偏低：" + l.joinToString("；") { "${it.student.name} ${it.index.fmt()}" + (it.weak.firstOrNull()?.let { w -> "（${w.label}：${w.detail}）" } ?: "") } },
        cs.groups.takeIf { it.isNotEmpty() }?.let { l -> "小组信誉分：" + l.joinToString("，") { "${it.first} ${it.second.fmt()}" } },
        if (cs.rules.active) "信誉分加减分（${cs.rules.periodLabel}，个人${CreditRules.modeLabel(cs.rules.personal)}、小组${CreditRules.modeLabel(cs.rules.group)}）：本时段合计 ${cs.pointsTotal.signed()}（加 ${cs.pointsPlus.fmt()}、扣 ${cs.pointsMinus.fmt()}）" else "信誉分加减分：不计入积分",
    )

    /** 文字版分析报告 */
    fun text(c: SchoolClass, r: Report, now: Long = System.currentTimeMillis(), exam: ExamReport? = null): String = buildString {
        val k = r.kpi
        appendLine("${c.name} 作业与成绩分析报告（${rangeTitle(r)}）")
        appendLine("范围：${r.window.label}")
        appendLine("生成日期：${java.text.SimpleDateFormat("yyyy年M月d日", java.util.Locale.CHINA).format(java.util.Date(now))}")
        appendLine()
        appendLine("一、总体情况")
        appendLine("共 ${k.assignments} 次作业，应交 ${k.expected} 人次。交齐率 ${pct(k.handInRate)}，按时率 ${pct(k.onTimeRate)}，优秀率 ${pct(k.excellentRate)}，缺交率 ${pct(k.missingRate)}，迟交率 ${pct(k.lateRate)}；全员交齐 ${k.perfect} 次（最长连续 ${r.longestPerfectRun} 次）。")
        appendLine("缺交 ${k.missing} 人次，迟交 ${k.late} 人次，请假 ${k.leave} 人次，未登记 ${k.unregistered} 人次。")
        r.previous?.let { p ->
            appendLine("${r.window.prevLabel}：交齐率 ${pct(p.handInRate)}，按时率 ${pct(p.onTimeRate)}，缺交率 ${pct(p.missingRate)}。")
        }
        if (r.insights.isNotEmpty()) {
            appendLine(); appendLine("二、主要发现")
            r.insights.forEachIndexed { i, s -> appendLine("${i + 1}. ${s.text}") }
        }
        appendLine(); appendLine("三、分项对比")
        if (r.subjects.isNotEmpty()) appendLine("各科：" + r.subjects.joinToString("；") { "${it.subject} 交齐 ${pct(it.rate)}、按时 ${pct(it.onTimeRate)}（${it.count} 次，缺交 ${it.missing}）" })
        val days = listOf("一", "二", "三", "四", "五", "六", "日")
        val wd = r.weekday.mapIndexedNotNull { i, v -> v?.let { "周${days[i]} ${pct(it)}" } }
        if (wd.isNotEmpty()) appendLine("星期：" + wd.joinToString("，"))
        if (r.groupStats.size > 1) appendLine("小组：" + r.groupStats.joinToString("；") { g -> "${g.name}${g.leader?.let { "（组长${it.name}）" } ?: ""} 交齐 ${pct(g.rate)}、缺交 ${g.missing}、平均积分 ${g.avgScore.fmt()}" })
        appendLine("交齐率分布：" + r.rateBins.joinToString("，") { "${it.first} ${it.second} 人" })
        appendLine(); appendLine("四、学生")
        if (r.attention.isNotEmpty()) appendLine("需要关注：" + r.attention.joinToString("；") { "${it.student.name} 缺交 ${it.missing} 次" + (if (it.missingStreak >= 2) "（连续 ${it.missingStreak} 次）" else "") + "，交齐率 ${pct(it.rate)}" })
        r.progress?.let { pg ->
            if (pg.stars.isNotEmpty()) appendLine("进步之星（${pg.basis}）：" + pg.stars.joinToString("；") { "${it.student.name} " + it.reasons.take(2).joinToString("，") })
            if (pg.watch.isNotEmpty()) appendLine("退步关注（${pg.basis}）：" + pg.watch.joinToString("；") { "${it.student.name} " + it.reasons.take(2).joinToString("，") })
        }
        if (r.risk.isNotEmpty()) appendLine("缺交风险：" + r.risk.joinToString("；") { "${it.student.name} ${pct(it.prob)}（${it.why}）" })
        if (r.weakSubjects.isNotEmpty()) appendLine("偏科提醒：" + r.weakSubjects.joinToString("；") { "${it.student.name} ${it.subject} ${pct(it.subjectRate)}（整体 ${pct(it.overallRate)}）" })
        if (r.streakers.isNotEmpty()) appendLine("连续交齐：" + r.streakers.joinToString("、") { "${it.student.name} ${it.handStreak} 次" })
        appendLine("学生分层：" + r.tiers.joinToString("；") { "${it.name} ${it.students.size} 人" } + "（交齐率中位数 ${pct(r.rateMedian)}，标准差 ${(r.rateStd * 100).roundToInt()} 个百分点）")
        if (r.perfectAttendance.isNotEmpty()) appendLine("全勤之星（${r.perfectAttendance.size} 人）：" + r.perfectAttendance.joinToString("、") { it.student.name })
        if (r.risers.isNotEmpty()) appendLine("积分上升最多：" + r.risers.joinToString("、") { "${it.student.name} ${it.scoreDelta.signed()}" })
        if (r.fallers.isNotEmpty()) appendLine("积分下降最多：" + r.fallers.joinToString("、") { "${it.student.name} ${it.scoreDelta.signed()}" })
        appendLine("积分：全班平均 ${r.avgScore.fmt()} 分" + if (r.range != Range.ALL) "，本时段变化 ${r.avgScoreDelta.signed()}" else "")
        val src = r.sources
        appendLine("积分来源：作业自动 ${src.homework.signed()}，手动加分 +${src.manualPlus.fmt()}，手动扣分 ${src.manualMinus.fmt()}，考试 ${src.exam.signed()}")
        if (r.reasons.isNotEmpty()) appendLine("加减分原因：" + r.reasons.take(6).joinToString("；") { "${it.reason} ${it.count} 次（${it.total.signed()}）" })
        r.credit?.takeIf { it.results.isNotEmpty() }?.let { cs -> creditLines(cs).forEach { appendLine(it) } }
        if (r.buckets.isNotEmpty()) appendLine("按${r.bucketUnit}汇总交齐率：" + r.buckets.joinToString("，") { "${it.label} ${pct(it.rate)}" })
        if (r.rounds.size > 1) appendLine("各轮对比：" + r.rounds.joinToString("；") { "${it.label} ${it.kpi.assignments} 次 交齐 ${pct(it.kpi.handInRate)}" + (it.examPct?.let { e -> " 考试 ${pct(e)}" } ?: "") })
        if (exam != null && exam.exams.isNotEmpty()) {
            appendLine(); appendLine("五、考试")
            exam.exams.forEach { e -> val s = exam.stats.getValue(e.id); appendLine("${fmtDay(e.date)} ${e.subject}「${e.title}」：平均 ${s.avg.fmt()}/${e.fullScore.fmt()}，中位 ${s.median.fmt()}，最高 ${s.max.fmt()}，及格率 ${pct(s.passRate)}，优秀率 ${pct(s.excellentRate)}") }
            exam.quality.takeIf { it.isNotEmpty() }?.let { q -> appendLine("难度与区分度：" + q.joinToString("；") { "${it.exam.title} 难度${difficultyText(it.difficulty)}（${pct(it.difficulty)}）、区分度${discriminationText(it.discrimination)}（${"%.2f".format(it.discrimination)}）" }) }
            if (exam.levels.isNotEmpty()) appendLine("层级（按平均得分率）：" + exam.levels.joinToString("，") { "${it.first} ${it.second} 人" })
            if (exam.stars.isNotEmpty()) appendLine("考试进步之星：" + exam.stars.joinToString("、") { "${it.student.name}（${it.last.title} ${pct(it.lastPct)}，相对位置 +${(it.delta * 100).roundToInt()}）" })
            if (exam.watch.isNotEmpty()) appendLine("考试退步关注：" + exam.watch.joinToString("、") { "${it.student.name}（${it.last.title} ${pct(it.lastPct)}，相对位置 ${(it.delta * 100).roundToInt()}）" })
            exam.insights.forEach { appendLine("• ${it.text}") }
        }
    }.trimEnd()

    // ---------------- 考试分析 ----------------

    data class ExamStudent(
        val student: Student,
        val taken: Int,
        val absent: Int,
        /** 平均得分率 0–1 */
        val avgPct: Double?,
        val bestPct: Double?,
        val worstPct: Double?,
        /** 得分率标准差（越小越稳定） */
        val stdPct: Double?,
        val lastRank: Int?,
        /** 名次变化（正数为进步） */
        val rankChange: Int?,
        val trend: Double,
    )

    data class ExamReport(
        val exams: List<Exam>,
        val stats: Map<String, ExamStat>,
        /** 每场平均得分率 */
        val avgPct: List<Pair<Exam, Double>>,
        val subjects: List<Pair<String, Double>>,
        val students: List<ExamStudent>,
        val improvers: List<ExamStudent>,
        val decliners: List<ExamStudent>,
        val struggling: List<ExamStudent>,
        val top: List<ExamStudent>,
        val groups: List<Pair<String, Double>>,
        val insights: List<Insight>,
        /** 每场难度（平均得分率）与区分度（前 27% 与后 27% 的得分率差） */
        val quality: List<ExamQuality> = emptyList(),
        /** 考试进步之星 / 退步关注（最近一场相对班级平均的位置 vs 之前各场） */
        val stars: List<ExamProgress> = emptyList(),
        val watch: List<ExamProgress> = emptyList(),
        /** 最近两场（同科）之间的层级流动 */
        val flow: Flow? = null,
        /** 临界生：平均得分率 55–60%（差一点及格）、80–85%（差一点优秀） */
        val nearPass: List<ExamStudent> = emptyList(),
        val nearExcellent: List<ExamStudent> = emptyList(),
        /** 偏科：最好与最差科目的得分率相差 ≥ 15 个百分点 */
        val imbalance: List<Imbalance> = emptyList(),
        /** 成绩稳定（≥3 场且波动 < 5 个百分点）、连续上升、连续下滑（最近 3 场相对位置） */
        val steady: List<ExamStudent> = emptyList(),
        val rising: List<ExamStudent> = emptyList(),
        val falling: List<ExamStudent> = emptyList(),
        /** 作业与考试对照：作业交得齐但考试不理想 / 作业常缺但考试不错 */
        val diligentLow: List<HwVsExam> = emptyList(),
        val skipButGood: List<HwVsExam> = emptyList(),
        /** 性别对比：性别、人数、平均得分率 */
        val genders: List<Triple<String, Int, Double>> = emptyList(),
        /** 各层级人数（按平均得分率）：优秀 / 良好 / 及格 / 不及格 */
        val levels: List<Pair<String, Int>> = emptyList(),
    )

    data class ExamQuality(val exam: Exam, val difficulty: Double, val discrimination: Double, val q1: Double, val q3: Double)
    data class ExamProgress(val student: Student, val before: Double, val after: Double, val delta: Double, val last: Exam, val lastPct: Double)
    data class Flow(val from: Exam, val to: Exam, val labels: List<String>, val matrix: List<List<Int>>, val up: Int, val down: Int, val same: Int)
    data class Imbalance(val student: Student, val best: Pair<String, Double>, val worst: Pair<String, Double>)
    data class HwVsExam(val student: Student, val hwRate: Float, val examPct: Double)

    /** 单场考试的深入分析 */
    data class ExamDeep(
        val stat: ExamStat,
        val q1: Double, val q3: Double, val range: Double,
        val difficulty: Double, val discrimination: Double, val top27: Double, val bottom27: Double,
        val nearPass: List<Pair<Student, Double>>, val nearExcellent: List<Pair<Student, Double>>, val failing: List<Pair<Student, Double>>,
        val rankUp: List<Triple<Student, Int, Int>>, val rankDown: List<Triple<Student, Int, Int>>,
        val enteredTop: List<Student>, val leftTop: List<Student>, val topN: Int,
        val genders: List<Triple<String, Int, Double>>, val groups: List<Triple<String, Int, Double>>,
        val absent: List<Student>, val missing: List<Student>,
        val prev: Exam?, val prevStat: ExamStat?,
        val insights: List<Insight>,
    )

    /** 四分位（线性插值） */
    fun quantile(sorted: List<Double>, q: Double): Double {
        if (sorted.isEmpty()) return 0.0
        val pos = (sorted.size - 1) * q; val lo = kotlin.math.floor(pos).toInt(); val hi = kotlin.math.ceil(pos).toInt()
        return sorted[lo] + (sorted[hi] - sorted[lo]) * (pos - lo)
    }

    /** 区分度：前 27% 与后 27% 的平均得分率之差（≥0.4 很好，0.3–0.4 良好，0.2–0.3 一般，<0.2 偏低） */
    private fun discrimination(v: List<Double>, full: Double): Triple<Double, Double, Double> {
        if (v.size < 4 || full <= 0) return Triple(0.0, 0.0, 0.0)
        val k = maxOf(1, (v.size * 0.27).roundToInt()); val d = v.sortedDescending()
        val top = d.take(k).average() / full; val bot = d.takeLast(k).average() / full
        return Triple(top - bot, top, bot)
    }

    fun discriminationText(d: Double) = when { d >= 0.4 -> "很好"; d >= 0.3 -> "良好"; d >= 0.2 -> "一般"; else -> "偏低" }
    fun difficultyText(p: Double) = when { p >= 0.85 -> "偏易"; p >= 0.7 -> "适中"; p >= 0.55 -> "偏难"; else -> "很难" }

    fun examDeep(c: SchoolClass, e: Exam): ExamDeep {
        val st = e.stat(c.students)
        val ids = c.students.map { it.id }.toSet()
        val byId = c.students.associateBy { it.id }
        val v = e.scores.filterKeys { it in ids }.values.filter { it.isFinite() }.sorted()
        val full = e.fullScore.takeIf { it > 0 } ?: 100.0
        val (disc, top27, bot27) = discrimination(v, full)
        fun pctOf(id: String) = e.scores[id]?.let { it / full }
        val withPct = c.students.mapNotNull { s -> pctOf(s.id)?.let { s to it } }
        val prev = c.previousExam(e)
        val ranks = e.ranks(); val pr = prev?.ranks().orEmpty()
        val moves = c.students.mapNotNull { s -> val a = pr[s.id]; val b = ranks[s.id]; if (a != null && b != null && a != b) Triple(s, a, b) else null }
        val topN = if (c.students.size >= 30) 10 else maxOf(3, c.students.size / 4)
        val topNow = ranks.filterValues { it <= topN }.keys; val topPrev = pr.filterValues { it <= topN }.keys
        fun grouped(key: (Student) -> String) = withPct.groupBy { key(it.first) }.filterKeys { it.isNotBlank() }
            .map { (g, l) -> Triple(g, l.size, l.map { it.second }.average()) }.sortedByDescending { it.third }
        val genders = grouped { it.gender.take(1) }.filter { it.first == "男" || it.first == "女" }
        val groups = grouped { it.group.ifBlank { "未分组" } }
        val nearPass = withPct.filter { it.second >= 0.55 && it.second < 0.6 }.sortedByDescending { it.second }
        val nearEx = withPct.filter { it.second >= 0.8 && it.second < 0.85 }.sortedByDescending { it.second }
        val failing = withPct.filter { it.second < 0.6 }.sortedBy { it.second }
        val prevStat = prev?.stat(c.students)
        val ins = ArrayList<Insight>()
        if (st.count >= 4) {
            ins += Insight(Level.INFO, "难度${difficultyText(st.avg / full)}（平均得分率 ${pct(st.avg / full)}），区分度${discriminationText(disc)}（前 27% 平均 ${pct(top27)}，后 27% 平均 ${pct(bot27)}）。")
            if (st.std / full >= 0.18) ins += Insight(Level.WARN, "分数很分散（标准差 ${st.std.fmt()} 分），两极分化明显。")
            else if (st.std / full < 0.08) ins += Insight(Level.INFO, "分数比较集中（标准差 ${st.std.fmt()} 分）。")
        }
        if (prev != null && prevStat != null && prevStat.count > 0 && st.count > 0) {
            val d = ((st.avg / full - prevStat.avg / prev.fullScore) * 100).roundToInt()
            if (abs(d) >= 2) ins += Insight(if (d > 0) Level.GOOD else Level.WARN, "平均得分率比上一场「${prev.title}」${if (d > 0) "提高" else "下降"} ${abs(d)} 个百分点" + "；及格率 ${pct(prevStat.passRate)} → ${pct(st.passRate)}。")
        }
        if (nearPass.isNotEmpty()) ins += Insight(Level.WARN, "差一点及格（55%–60%）：" + nearPass.joinToString("、") { it.first.name } + "，稍加辅导就能过线。")
        if (nearEx.isNotEmpty()) ins += Insight(Level.INFO, "差一点优秀（80%–85%）：" + nearEx.joinToString("、") { it.first.name } + "。")
        val ups = moves.filter { it.third < it.second }.sortedByDescending { it.second - it.third }
        val downs = moves.filter { it.third > it.second }.sortedByDescending { it.third - it.second }
        ups.firstOrNull()?.let { if (it.second - it.third >= 3) ins += Insight(Level.GOOD, "进步最大：${it.first.name}（第 ${it.second} → 第 ${it.third} 名）。") }
        downs.firstOrNull()?.let { if (it.third - it.second >= 3) ins += Insight(Level.WARN, "退步最大：${it.first.name}（第 ${it.second} → 第 ${it.third} 名）。") }
        if (genders.size == 2 && abs(genders[0].third - genders[1].third) >= 0.05) ins += Insight(Level.INFO, "${genders[0].first}生平均得分率 ${pct(genders[0].third)}，比${genders[1].first}生高 ${((genders[0].third - genders[1].third) * 100).roundToInt()} 个百分点。")
        if (st.missing > 0) ins += Insight(Level.WARN, "还有 ${st.missing} 人没有录入成绩。")
        return ExamDeep(
            st, quantile(v, 0.25), quantile(v, 0.75), (v.maxOrNull() ?: 0.0) - (v.minOrNull() ?: 0.0),
            st.avg / full, disc, top27, bot27, nearPass, nearEx, failing,
            ups.take(8), downs.take(8),
            if (prev == null) emptyList() else (topNow - topPrev).mapNotNull { byId[it] }, if (prev == null) emptyList() else (topPrev - topNow).filter { it in ranks || it in e.absent }.mapNotNull { byId[it] }, topN,
            genders, groups,
            c.students.filter { it.id in e.absent }, c.students.filter { it.id !in e.scores && it.id !in e.absent },
            prev, prevStat, ins,
        )
    }

    fun examReport(c: SchoolClass, subject: String? = null): ExamReport {
        val exams = c.exams.filter { subject == null || it.subject == subject }.sortedBy { it.date }
        val stats = exams.associate { it.id to it.stat(c.students) }
        val avgPct = exams.mapNotNull { e -> stats.getValue(e.id).takeIf { it.count > 0 }?.let { e to it.avg / e.fullScore } }
        val subjects = exams.groupBy { it.subject.ifBlank { "其他" } }.mapNotNull { (k, l) ->
            val v = l.mapNotNull { e -> stats.getValue(e.id).takeIf { it.count > 0 }?.let { it.avg / e.fullScore } }
            if (v.isEmpty()) null else k to v.average()
        }.sortedByDescending { it.second }
        val ranks = exams.associate { it.id to it.ranks() }
        val students = c.students.map { st ->
            val pcts = exams.mapNotNull { e -> e.scores[st.id]?.let { it / e.fullScore } }
            val taken = exams.filter { st.id in it.scores }
            val last = taken.lastOrNull()
            val prev = last?.let { l -> taken.filter { it.id != l.id && (l.subject.isBlank() || it.subject == l.subject) }.lastOrNull() }
            val lastRank = last?.let { ranks[it.id]?.get(st.id) }
            val prevRank = prev?.let { ranks[it.id]?.get(st.id) }
            val avg = if (pcts.isEmpty()) null else pcts.average()
            ExamStudent(
                st, pcts.size, exams.count { st.id in it.absent }, avg, pcts.maxOrNull(), pcts.minOrNull(),
                if (pcts.size < 2) null else sqrt(pcts.sumOf { (it - avg!!) * (it - avg) } / pcts.size),
                lastRank, if (lastRank != null && prevRank != null) prevRank - lastRank else null, slope(pcts),
            )
        }
        val improvers = students.filter { (it.rankChange ?: 0) > 0 }.sortedByDescending { it.rankChange }.take(5)
        val decliners = students.filter { (it.rankChange ?: 0) < 0 }.sortedBy { it.rankChange }.take(5)
        val struggling = students.filter { (it.avgPct ?: 1.0) < 0.6 }.sortedBy { it.avgPct }
        val top = students.filter { it.avgPct != null }.sortedByDescending { it.avgPct }.take(5)
        val groups = c.students.groupBy { it.group.ifBlank { "未分组" } }.mapNotNull { (g, m) ->
            val v = students.filter { s -> m.any { it.id == s.student.id } }.mapNotNull { it.avgPct }
            if (v.isEmpty()) null else g to v.average()
        }.sortedByDescending { it.second }

        // —— 更多考试分析 ——
        val quality = exams.mapNotNull { e ->
            val v = e.scores.filterKeys { id -> c.students.any { it.id == id } }.values.filter { it.isFinite() }.sorted()
            if (v.isEmpty() || e.fullScore <= 0) null else ExamQuality(e, v.average() / e.fullScore, discrimination(v, e.fullScore).first, quantile(v, 0.25), quantile(v, 0.75))
        }
        val avgOf = exams.associate { e -> e.id to (stats.getValue(e.id).takeIf { it.count > 0 }?.avg?.div(e.fullScore) ?: 0.0) }
        fun rel(e: Exam, sid: String) = e.scores[sid]?.let { it / e.fullScore - avgOf.getValue(e.id) }
        val progressAll = c.students.mapNotNull { st ->
            val taken = exams.filter { st.id in it.scores }
            if (taken.size < 2) return@mapNotNull null
            val last = taken.last()
            val before = taken.dropLast(1).mapNotNull { rel(it, st.id) }.average()
            val after = rel(last, st.id)!!
            ExamProgress(st, before, after, after - before, last, last.scores.getValue(st.id) / last.fullScore)
        }
        val stars = progressAll.filter { it.delta >= 0.05 }.sortedByDescending { it.delta }.take(8)
        val watch = progressAll.filter { it.delta <= -0.05 }.sortedBy { it.delta }.take(8)
        val labels = listOf("优秀", "良好", "及格", "不及格")
        fun level(p: Double) = when { p >= 0.85 -> 0; p >= 0.75 -> 1; p >= 0.6 -> 2; else -> 3 }
        val flow = exams.lastOrNull()?.let { to -> c.previousExam(to)?.takeIf { f -> f in exams || subject == null }?.let { from ->
            val m = List(4) { IntArray(4) }
            var up = 0; var down = 0; var same = 0
            c.students.forEach { s ->
                val a = from.scores[s.id]?.div(from.fullScore); val b = to.scores[s.id]?.div(to.fullScore)
                if (a != null && b != null) { val i = level(a); val j = level(b); m[i][j]++; if (j < i) up++ else if (j > i) down++ else same++ }
            }
            if (up + down + same == 0) null else Flow(from, to, labels, m.map { it.toList() }, up, down, same)
        } }
        val withAvg = students.filter { it.avgPct != null }
        val nearPass = withAvg.filter { it.avgPct!! >= 0.55 && it.avgPct < 0.6 }.sortedByDescending { it.avgPct }
        val nearEx = withAvg.filter { it.avgPct!! >= 0.8 && it.avgPct < 0.85 }.sortedByDescending { it.avgPct }
        val imbalance = if (subject != null) emptyList() else c.students.mapNotNull { st ->
            val bySub = exams.filter { st.id in it.scores && it.subject.isNotBlank() }.groupBy { it.subject }
                .mapValues { (_, l) -> l.map { it.scores.getValue(st.id) / it.fullScore }.average() }
            if (bySub.size < 2) return@mapNotNull null
            val b = bySub.maxBy { it.value }; val w = bySub.minBy { it.value }
            if (b.value - w.value >= 0.15) Imbalance(st, b.key to b.value, w.key to w.value) else null
        }.sortedByDescending { it.best.second - it.worst.second }.take(8)
        val steady = withAvg.filter { it.taken >= 3 && (it.stdPct ?: 1.0) < 0.05 }.sortedByDescending { it.avgPct }
        fun lastRels(st: Student) = exams.filter { st.id in it.scores }.takeLast(3).mapNotNull { rel(it, st.id) }
        val rising = withAvg.filter { s -> lastRels(s.student).let { r -> r.size == 3 && r[0] < r[1] && r[1] < r[2] && r[2] - r[0] >= 0.03 } }
        val falling = withAvg.filter { s -> lastRels(s.student).let { r -> r.size == 3 && r[0] > r[1] && r[1] > r[2] && r[0] - r[2] >= 0.03 } }
        val hw = c.students.associate { it.id to insight(it, c.assignments) }
        val diligentLow = withAvg.mapNotNull { s -> hw[s.student.id]?.takeIf { it.expected >= 3 && it.rate >= 0.9f && s.avgPct!! < 0.6 }?.let { HwVsExam(s.student, it.rate, s.avgPct!!) } }.sortedBy { it.examPct }
        val skipButGood = withAvg.mapNotNull { s -> hw[s.student.id]?.takeIf { it.expected >= 3 && it.rate < 0.75f && s.avgPct!! >= 0.8 }?.let { HwVsExam(s.student, it.rate, s.avgPct!!) } }.sortedByDescending { it.examPct }
        val genders = withAvg.groupBy { it.student.gender.take(1) }.filterKeys { it == "男" || it == "女" }
            .map { (g, l) -> Triple(g, l.size, l.mapNotNull { it.avgPct }.average()) }.sortedByDescending { it.third }
        val levels = labels.mapIndexed { i, l -> l to withAvg.count { level(it.avgPct!!) == i } }

        val ins = ArrayList<Insight>()
        if (avgPct.size >= 3) {
            val sl = slope(avgPct.map { it.second }) * 100
            if (abs(sl) >= 1) ins += Insight(if (sl > 0) Level.GOOD else Level.WARN, "全班平均得分率${if (sl > 0) "逐场上升" else "逐场下降"}，每场约 ${"%.1f".format(abs(sl))} 个百分点。")
        }
        exams.lastOrNull()?.let { e ->
            val s = stats.getValue(e.id)
            if (s.count > 0) ins += Insight(if (s.passRate >= 0.8) Level.GOOD else if (s.passRate < 0.6) Level.WARN else Level.INFO, "最近一场「${e.title}」平均 ${s.avg.fmt()} 分，及格率 ${pct(s.passRate)}，优秀率 ${pct(s.excellentRate)}，标准差 ${s.std.fmt()}。")
            if (s.missing > 0) ins += Insight(Level.WARN, "「${e.title}」还有 ${s.missing} 人没有录入成绩。")
        }
        if (subjects.size >= 2 && subjects.first().second - subjects.last().second >= 0.05)
            ins += Insight(Level.INFO, "${subjects.last().first}平均得分率最低（${pct(subjects.last().second)}），比${subjects.first().first}低 ${((subjects.first().second - subjects.last().second) * 100).roundToInt()} 个百分点。")
        if (improvers.isNotEmpty()) ins += Insight(Level.GOOD, "名次进步：" + improvers.joinToString("、") { "${it.student.name}（↑${it.rankChange}）" } + "。")
        if (decliners.isNotEmpty()) ins += Insight(Level.WARN, "名次退步：" + decliners.joinToString("、") { "${it.student.name}（↓${-it.rankChange!!}）" } + "。")
        if (struggling.isNotEmpty()) ins += Insight(Level.WARN, "${struggling.size} 人平均得分率不足 60%：${struggling.take(6).joinToString("、") { it.student.name }}${if (struggling.size > 6) " 等" else ""}。")
        val unstable = students.filter { (it.stdPct ?: 0.0) >= 0.12 && it.taken >= 3 }.sortedByDescending { it.stdPct }
        if (unstable.isNotEmpty()) ins += Insight(Level.INFO, "成绩波动较大：${unstable.take(5).joinToString("、") { it.student.name }}。")
        if (groups.size >= 2 && groups.first().second - groups.last().second >= 0.05)
            ins += Insight(Level.INFO, "小组平均得分率：${groups.first().first} 最高（${pct(groups.first().second)}），${groups.last().first} 最低（${pct(groups.last().second)}）。")
        if (stars.isNotEmpty()) ins += Insight(Level.GOOD, "考试进步之星（最近一场相对班级平均的位置比之前高）：" + stars.take(5).joinToString("、") { "${it.student.name}（高 ${(it.delta * 100).roundToInt()} 个百分点）" } + "。")
        if (watch.isNotEmpty()) ins += Insight(Level.WARN, "考试退步关注（最近一场相对班级平均的位置比之前低）：" + watch.take(5).joinToString("、") { "${it.student.name}（低 ${(-it.delta * 100).roundToInt()} 个百分点）" } + "。")
        flow?.let { f -> if (f.up + f.down > 0) ins += Insight(if (f.up >= f.down) Level.GOOD else Level.WARN, "从「${f.from.title}」到「${f.to.title}」：${f.up} 人升档、${f.down} 人降档、${f.same} 人不变。") }
        if (nearPass.isNotEmpty()) ins += Insight(Level.WARN, "临界及格（平均 55%–60%）：${nearPass.take(6).joinToString("、") { it.student.name }}，是提升及格率的重点。")
        if (nearEx.isNotEmpty()) ins += Insight(Level.INFO, "临界优秀（平均 80%–85%）：${nearEx.take(6).joinToString("、") { it.student.name }}。")
        if (imbalance.isNotEmpty()) ins += Insight(Level.INFO, "偏科：" + imbalance.take(4).joinToString("；") { "${it.student.name} ${it.best.first}${pct(it.best.second)}、${it.worst.first}${pct(it.worst.second)}" } + "。")
        if (diligentLow.isNotEmpty()) ins += Insight(Level.WARN, "作业交得齐但考试不理想（可能“交了没学会”）：${diligentLow.take(6).joinToString("、") { it.student.name }}。")
        if (skipButGood.isNotEmpty()) ins += Insight(Level.INFO, "作业常缺但考试不错（会但不交）：${skipButGood.take(6).joinToString("、") { it.student.name }}。")
        if (rising.isNotEmpty()) ins += Insight(Level.GOOD, "连续三场上升：${rising.take(6).joinToString("、") { it.student.name }}。")
        if (falling.isNotEmpty()) ins += Insight(Level.WARN, "连续三场下滑：${falling.take(6).joinToString("、") { it.student.name }}。")
        quality.lastOrNull()?.let { q -> if (q.discrimination in 0.01..0.2) ins += Insight(Level.INFO, "「${q.exam.title}」区分度偏低（${"%.2f".format(q.discrimination)}），题目可能过易或过难，难以拉开差距。") }
        if (genders.size == 2 && abs(genders[0].third - genders[1].third) >= 0.05) ins += Insight(Level.INFO, "${genders[0].first}生平均得分率比${genders[1].first}生高 ${((genders[0].third - genders[1].third) * 100).roundToInt()} 个百分点。")
        return ExamReport(
            exams, stats, avgPct, subjects, students, improvers, decliners, struggling, top, groups, ins,
            quality = quality, stars = stars, watch = watch, flow = flow, nearPass = nearPass, nearExcellent = nearEx, imbalance = imbalance,
            steady = steady, rising = rising, falling = falling, diligentLow = diligentLow, skipButGood = skipButGood, genders = genders, levels = levels,
        )
    }
}
