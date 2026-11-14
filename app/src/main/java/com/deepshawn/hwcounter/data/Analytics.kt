package com.deepshawn.hwcounter.data

import java.util.Calendar
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * 统计分析（纯计算，便于测试）。
 *
 * 作业：三种范围（全部 / 本轮：最近一次全班重置以来 / 近 10 次），每种都与“上一时段”对比；
 * 核心指标、回归趋势、星期 / 科目 / 小组对比、学生明细、交齐率分布、积分走势与涨跌、
 * 数据完整度，以及自动生成的结论（[Insight]）和文字版分析报告。
 *
 * 考试：每场的平均 / 中位 / 标准差 / 及格良好优秀低分率、分数段、名次与进退步，
 * 跨场次的得分率走势、各科对比、稳定性、与作业交齐率的相关性。
 */
object Analytics {

    enum class Range(val label: String) { ALL("全部"), PERIOD("本轮"), RECENT10("近 10 次") }

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
    )

    /** 全班重置：覆盖至少一半同学的重置，按生效时间排序 */
    fun classResets(c: SchoolClass): List<ScoreRecharge> =
        c.recharges.filter { it.studentIds.size * 2 >= c.students.size.coerceAtLeast(1) }.sortedWith(compareBy({ it.start }, { it.time }))

    fun window(c: SchoolClass, range: Range, dayFmt: (Long) -> String = ::fmtDay): Window {
        val sorted = c.assignments.sortedBy { it.createdAt }
        return when (range) {
            Range.ALL -> Window(range, sorted, emptyList(), null, null, "有记录以来全部 ${sorted.size} 次作业")
            Range.RECENT10 -> Window(range, sorted.takeLast(10), sorted.dropLast(10).takeLast(10), null, sorted.takeLast(10).firstOrNull()?.createdAt, "最近 ${minOf(10, sorted.size)} 次作业")
            Range.PERIOD -> {
                val resets = classResets(c)
                val r = resets.lastOrNull()
                if (r == null) Window(range, sorted, emptyList(), null, null, "还没有全班重置，显示全部作业")
                else {
                    val prevReset = resets.dropLast(1).lastOrNull()
                    val cur = sorted.filter { r.counts(it.createdAt) }
                    val prev = sorted.filter { !r.counts(it.createdAt) && (prevReset == null || prevReset.counts(it.createdAt)) }
                    Window(range, cur, prev, r, r.start, "本轮：${r.startLabel(dayFmt)}（${fmtDay(r.time)} 重置为 ${r.target.fmt()} 分）· ${cur.size} 次作业")
                }
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
    ) {
        val rate: Float get() = if (expected == 0) 1f else handedIn.toFloat() / expected
        val onTimeRate: Float get() = if (expected == 0) 1f else onTime.toFloat() / expected
    }

    data class SubjectStat(val subject: String, val count: Int, val rate: Float, val missing: Int, val onTimeRate: Float = rate)

    data class GroupStat(val name: String, val leader: Student?, val size: Int, val rate: Float, val missing: Int, val avgScore: Double, val scoreDelta: Double, val examPct: Double?)

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

    fun report(c: SchoolClass, rules: ScoreRules, range: Range, now: Long = System.currentTimeMillis()): Report {
        val w = window(c, range)
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
        val nowScore = stats.associate { it.student.id to it.score }
        val startScore: Map<String, Double> = when {
            w.reset != null -> c.students.associate { it.id to w.reset.target }
            w.start != null -> scoresAt(c, rules, w.start - 1)
            else -> c.students.associate { it.id to rules.initial }
        }
        val rankOf = stats.sortedByDescending { it.score }.mapIndexed { i, s -> s.student.id to i + 1 }.toMap()
        val examsIn = c.exams.filter { e -> w.start == null || e.date >= w.start }.sortedBy { it.date }
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

        val scores = stats.map { it.score }
        val scoreBins = if (scores.isEmpty()) emptyList() else {
            val lo = kotlin.math.floor(scores.min()).toInt()
            val hi = kotlin.math.floor(scores.max()).toInt()
            val step = maxOf(1, (hi - lo + 7) / 7)
            (lo..hi step step).map { b -> (if (step == 1) "$b" else "$b~${b + step - 1}") to scores.count { it >= b && it < b + step } }
        }
        val groups = stats.groupBy { it.student.group.ifBlank { "未分组" } }.map { (g, l) -> g to l.sumOf { it.score } / l.size }.sortedByDescending { it.second }
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

        val scoreTrend = if (points.size < 2) emptyList() else points.map { p -> p.assignment to scoresAt(c, rules, p.assignment.createdAt).values.average() }
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
        val base = Report(
            range, w, points, k, prev, totals, weekday, subjects, students, attention, improved, perfect, scoreBins, groups,
            groupStats = groupStats, rateBins = rateBins, scoreTrend = scoreTrend, risers = risers, fallers = fallers,
            unregistered = unregistered, slope = slope(points.map { it.summary.rate.toDouble() }).toFloat(),
            longestPerfectRun = best, avgScore = if (scores.isEmpty()) 0.0 else scores.average(),
            avgScoreDelta = if (students.isEmpty()) 0.0 else students.map { it.scoreDelta }.average(), examCorrelation = corr,
        )
        return base.copy(insights = insights(base, corrPairs))
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
        val half = marks.size / 2
        val trend = if (half < 2) 0f else marks.drop(marks.size - half).average().toFloat() - marks.take(half).average().toFloat()
        return StudentInsight(st, expected, handed, missing, late, streak, trend, onTime, excellent, leave, none, longest)
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
            if (abs(d) >= 2) out += Insight(if (d > 0) Level.GOOD else Level.WARN, "与上一时段相比交齐率${if (d > 0) "提高" else "下降"} ${abs(d)} 个百分点（${pct(p.handInRate)} → ${pct(k.handInRate)}）。")
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
        if (r.improved.isNotEmpty()) out += Insight(Level.GOOD, "进步明显：${names(r.improved)}（后半段比前半段交得更好）。")
        if (r.perfectAttendance.isNotEmpty()) out += Insight(Level.GOOD, "${r.perfectAttendance.size} 人无缺交、无迟交。")
        // 小组
        val gs = r.groupStats.filter { it.name != "未分组" && it.size >= 2 }
        if (gs.size >= 2) {
            val b = gs.maxBy { it.rate }; val wst = gs.minBy { it.rate }
            if (b.rate - wst.rate >= 0.08f) out += Insight(Level.INFO, "小组差距：${b.name}交齐率 ${pct(b.rate)}，${wst.name}只有 ${pct(wst.rate)}${wst.leader?.let { "（组长 ${it.name}）" } ?: ""}。")
        }
        // 积分
        if (r.range != Range.ALL && abs(r.avgScoreDelta) >= 0.5) out += Insight(Level.INFO, "本时段全班平均积分${if (r.avgScoreDelta > 0) "增加" else "减少"} ${abs(r.avgScoreDelta).fmt()} 分。")
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

    /** 文字版分析报告 */
    fun text(c: SchoolClass, r: Report, now: Long = System.currentTimeMillis(), exam: ExamReport? = null): String = buildString {
        val k = r.kpi
        appendLine("📊 ${c.name} 作业与成绩分析报告（${r.range.label}）")
        appendLine("范围：${r.window.label}")
        appendLine("生成时间：${java.text.SimpleDateFormat("yyyy年M月d日 HH:mm", java.util.Locale.CHINA).format(java.util.Date(now))}")
        appendLine()
        appendLine("一、总体情况")
        appendLine("共 ${k.assignments} 次作业，应交 ${k.expected} 人次。交齐率 ${pct(k.handInRate)}，按时率 ${pct(k.onTimeRate)}，优秀率 ${pct(k.excellentRate)}，缺交率 ${pct(k.missingRate)}，迟交率 ${pct(k.lateRate)}；全员交齐 ${k.perfect} 次（最长连续 ${r.longestPerfectRun} 次）。")
        appendLine("缺交 ${k.missing} 人次，迟交 ${k.late} 人次，请假 ${k.leave} 人次，未登记 ${k.unregistered} 人次。")
        r.previous?.let { p ->
            appendLine("上一时段：交齐率 ${pct(p.handInRate)}，按时率 ${pct(p.onTimeRate)}，缺交率 ${pct(p.missingRate)}。")
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
        if (r.improved.isNotEmpty()) appendLine("进步之星：" + r.improved.joinToString("、") { it.student.name })
        if (r.perfectAttendance.isNotEmpty()) appendLine("全勤之星（${r.perfectAttendance.size} 人）：" + r.perfectAttendance.joinToString("、") { it.student.name })
        if (r.risers.isNotEmpty()) appendLine("积分上升最多：" + r.risers.joinToString("、") { "${it.student.name} ${it.scoreDelta.signed()}" })
        if (r.fallers.isNotEmpty()) appendLine("积分下降最多：" + r.fallers.joinToString("、") { "${it.student.name} ${it.scoreDelta.signed()}" })
        appendLine("积分：全班平均 ${r.avgScore.fmt()} 分" + if (r.range != Range.ALL) "，本时段变化 ${r.avgScoreDelta.signed()}" else "")
        if (exam != null && exam.exams.isNotEmpty()) {
            appendLine(); appendLine("五、考试")
            exam.exams.forEach { e -> val s = exam.stats.getValue(e.id); appendLine("${fmtDay(e.date)} ${e.subject}「${e.title}」：平均 ${s.avg.fmt()}/${e.fullScore.fmt()}，中位 ${s.median.fmt()}，最高 ${s.max.fmt()}，及格率 ${pct(s.passRate)}，优秀率 ${pct(s.excellentRate)}") }
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
    )

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
        return ExamReport(exams, stats, avgPct, subjects, students, improvers, decliners, struggling, top, groups, ins)
    }
}
