package com.deepshawn.hwcounter.data

import java.util.Calendar
import kotlin.math.roundToInt

/**
 * 统计分析（纯计算，便于测试）：时间范围、核心指标及与上一时段的对比、趋势、
 * 星期 / 科目分布、需要关注的学生、进步之星、积分分布，以及文字版分析报告。
 */
object Analytics {

    enum class Range(val label: String) { RECENT10("近 10 次"), DAYS30("近 30 天"), ALL("全部") }

    data class Kpi(
        val assignments: Int,
        /** 交齐率：已交 + 优秀 + 迟交 / 应交 */
        val handInRate: Float,
        /** 按时率：已交 + 优秀 / 应交 */
        val onTimeRate: Float,
        val excellentRate: Float,
        val missing: Int,
        val late: Int,
        val leave: Int,
        val unregistered: Int,
        /** 全部交齐的次数 */
        val perfect: Int,
        /** 应交人次 */
        val expected: Int = 0,
    ) {
        val missingRate: Float get() = if (expected == 0) 0f else missing.toFloat() / expected
    }

    data class Point(val assignment: Assignment, val summary: AssignmentSummary)

    data class StudentInsight(
        val student: Student,
        val expected: Int,
        val handedIn: Int,
        val missing: Int,
        val late: Int,
        /** 最近连续未交次数 */
        val missingStreak: Int,
        /** 后半段交齐率 − 前半段交齐率（正数为进步） */
        val trend: Float,
    ) {
        val rate: Float get() = if (expected == 0) 1f else handedIn.toFloat() / expected
    }

    data class Report(
        val range: Range,
        val points: List<Point>,
        val kpi: Kpi,
        /** 上一时段（同样长度）的指标，没有足够数据时为 null */
        val previous: Kpi?,
        val totals: Map<HwStatus, Int>,
        /** 周一..周日 的平均交齐率（没有作业为 null） */
        val weekday: List<Float?>,
        val subjects: List<SubjectStat>,
        val students: List<StudentInsight>,
        val attention: List<StudentInsight>,
        val improved: List<StudentInsight>,
        val perfectAttendance: List<StudentInsight>,
        val scoreBins: List<Pair<String, Int>>,
        val groups: List<Pair<String, Double>>,
    )

    data class SubjectStat(val subject: String, val count: Int, val rate: Float, val missing: Int)

    /** 按范围挑出作业（按布置时间升序） */
    fun pick(all: List<Assignment>, range: Range, now: Long = System.currentTimeMillis()): Pair<List<Assignment>, List<Assignment>> {
        val sorted = all.sortedBy { it.createdAt }
        return when (range) {
            Range.RECENT10 -> sorted.takeLast(10) to sorted.dropLast(10).takeLast(10)
            Range.DAYS30 -> {
                val day = 24L * 3600 * 1000
                sorted.filter { it.createdAt > now - 30 * day } to sorted.filter { it.createdAt in (now - 60 * day + 1)..(now - 30 * day) }
            }
            Range.ALL -> sorted to emptyList()
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

    fun report(c: SchoolClass, rules: ScoreRules, range: Range, now: Long = System.currentTimeMillis()): Report {
        val (list, prev) = pick(c.assignments, range, now)
        val points = list.map { Point(it, c.summary(it)) }
        val totals = HashMap<HwStatus, Int>()
        points.forEach { p -> p.summary.counts.forEach { (k, v) -> totals[k] = (totals[k] ?: 0) + v } }

        // 星期分布
        val cal = Calendar.getInstance()
        val byDay = Array(7) { ArrayList<Float>() }
        points.forEach { p ->
            cal.timeInMillis = p.assignment.createdAt
            val idx = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7 // 周一 = 0
            byDay[idx] += p.summary.rate
        }
        val weekday = byDay.map { if (it.isEmpty()) null else it.average().toFloat() }

        val subjects = points.groupBy { it.assignment.subject.ifBlank { "其他" } }.map { (k, l) ->
            SubjectStat(k, l.size, l.map { it.summary.rate }.average().toFloat(), l.sumOf { it.summary.c(HwStatus.MISSING) })
        }.sortedByDescending { it.rate }

        val students = c.students.map { st -> insight(st, list) }
        val attention = students.filter { it.missing > 0 || it.missingStreak >= 2 }
            .sortedWith(compareByDescending<StudentInsight> { it.missingStreak }.thenByDescending { it.missing }.thenByDescending { it.late })
            .take(8)
        val improved = students.filter { it.trend >= 0.2f && it.expected >= 4 }.sortedByDescending { it.trend }.take(5)
        val perfect = students.filter { it.expected > 0 && it.missing == 0 && it.late == 0 }

        val stats = c.studentStats(rules)
        val scores = stats.map { it.score }
        val scoreBins = if (scores.isEmpty()) emptyList() else {
            val lo = kotlin.math.floor(scores.min() / 5).toInt() * 5
            val hi = kotlin.math.floor(scores.max() / 5).toInt() * 5
            val step = maxOf(5, ((hi - lo) / 6.0).roundToInt().let { ((it + 4) / 5) * 5 })
            (lo..hi step step).map { b -> "$b~${b + step - 1}" to scores.count { it >= b && it < b + step } }
        }
        val groups = stats.groupBy { it.student.group.ifBlank { "未分组" } }.map { (g, l) -> g to l.sumOf { it.score } / l.size }.sortedByDescending { it.second }

        return Report(
            range, points, kpi(c, list), if (prev.isEmpty()) null else kpi(c, prev), totals, weekday, subjects,
            students, attention, improved, perfect, scoreBins, groups,
        )
    }

    fun insight(st: Student, list: List<Assignment>): StudentInsight {
        var expected = 0; var handed = 0; var missing = 0; var late = 0
        val marks = ArrayList<Int>() // 1 交 0 缺；请假和未登记不计
        for (a in list) {
            when (a.statuses[st.id] ?: HwStatus.NONE) {
                HwStatus.SUBMITTED, HwStatus.EXCELLENT -> { expected++; handed++; marks += 1 }
                HwStatus.LATE -> { expected++; handed++; late++; marks += 1 }
                HwStatus.MISSING -> { expected++; missing++; marks += 0 }
                HwStatus.NONE -> expected++
                HwStatus.LEAVE -> {}
            }
        }
        var streak = 0
        for (m in marks.asReversed()) if (m == 0) streak++ else break
        val half = marks.size / 2
        val trend = if (half < 2) 0f else marks.drop(marks.size - half).average().toFloat() - marks.take(half).average().toFloat()
        return StudentInsight(st, expected, handed, missing, late, streak, trend)
    }

    private fun pct(x: Float) = "${(x * 100).roundToInt()}%"

    /** 文字版分析报告（可复制到家长群 / 教研记录） */
    fun text(c: SchoolClass, r: Report, now: Long = System.currentTimeMillis()): String = buildString {
        val k = r.kpi
        appendLine("📊 ${c.name} 作业分析报告（${r.range.label}）")
        appendLine("生成时间：${java.text.SimpleDateFormat("yyyy年M月d日 HH:mm", java.util.Locale.CHINA).format(java.util.Date(now))}")
        appendLine()
        appendLine("【总体】共 ${k.assignments} 次作业，交齐率 ${pct(k.handInRate)}，按时率 ${pct(k.onTimeRate)}，优秀率 ${pct(k.excellentRate)}；全员交齐 ${k.perfect} 次。")
        appendLine("缺交 ${k.missing} 人次，迟交 ${k.late} 人次，请假 ${k.leave} 人次，未登记 ${k.unregistered} 人次。")
        r.previous?.let { p ->
            val d = ((k.handInRate - p.handInRate) * 100).roundToInt()
            appendLine("与上一时段相比，交齐率${if (d >= 0) "上升" else "下降"} ${kotlin.math.abs(d)} 个百分点。")
        }
        if (r.subjects.size > 1) {
            appendLine()
            appendLine("【各科】" + r.subjects.joinToString("；") { "${it.subject} ${pct(it.rate)}（${it.count} 次，缺交 ${it.missing}）" })
        }
        val days = listOf("一", "二", "三", "四", "五", "六", "日")
        val wd = r.weekday.mapIndexedNotNull { i, v -> v?.let { days[i] to it } }
        if (wd.size >= 2) {
            val best = wd.maxBy { it.second }; val worst = wd.minBy { it.second }
            appendLine("【星期】周${best.first}交得最好（${pct(best.second)}），周${worst.first}最弱（${pct(worst.second)}）。")
        }
        appendLine()
        if (r.attention.isNotEmpty()) appendLine("【需要关注】" + r.attention.joinToString("；") { "${it.student.name} 缺交 ${it.missing} 次" + if (it.missingStreak >= 2) "（连续 ${it.missingStreak} 次）" else "" })
        if (r.improved.isNotEmpty()) appendLine("【进步之星】" + r.improved.joinToString("、") { it.student.name })
        if (r.perfectAttendance.isNotEmpty()) appendLine("【全勤之星】${r.perfectAttendance.size} 人：" + r.perfectAttendance.joinToString("、") { it.student.name })
        if (r.groups.size > 1) appendLine("【小组平均分】" + r.groups.joinToString("；") { "${it.first} ${it.second.fmt()}" })
    }.trimEnd()
}
