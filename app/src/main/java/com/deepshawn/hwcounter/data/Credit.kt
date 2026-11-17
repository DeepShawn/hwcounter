package com.deepshawn.hwcounter.data

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/**
 * 信誉分（0–100，越优秀越高；三端同一算法，见网页 model.js、iPad Models.swift）。
 *
 * 个人信誉分 = 下面 7 项（每项 0–1）的加权和 × 100（没有数据的项不算，权重按比例分给其他项）：
 * 1. 交齐 25%：最近 10 次（不含请假、未登记）的交齐率
 * 2. 按时 15%：最近 10 次的按时率（已交、优秀）
 * 3. 优秀 10%：最近 10 次的优秀率 ÷ 30%（最多 1）
 * 4. 连续交 10%：当前连续交（已交 / 优秀 / 迟交）的次数 ÷ 5（最多 1）
 * 5. 诚信 20%：最近 10 次里复审不合格、抄袭（含提供者）每有 1 次扣 0.5（最少 0）
 * 6. 进步 5%：最近 5 次比再往前 5 次的交齐率，持平为 0.5，每提高 / 下降 40 个百分点 ±0.5（记录不足 8 次为 0.5）
 * 7. 考试 15%：最近 3 场考试的平均得分率
 * 一次作业记录、一场考试都没有时没有信誉分。等级：≥90 优秀，75–90 良好，60–75 一般，<60 较差。
 * 小组信誉分 = 组内有信誉分的同学的平均（已命名、2 人以上的小组）。
 *
 * 信誉分不使用积分，所以可以反过来按信誉分给积分加减分（见 [CreditRules]），不会循环。
 */
object Credit {
    data class Part(val key: String, val label: String, val weight: Double, val value: Double, val detail: String) {
        /** 这一项贡献的分数（已按权重归一化） */
        var points: Double = 0.0
    }

    data class Result(val student: Student, val index: Double, val level: Level, val parts: List<Part>, val hasData: Boolean) {
        /** 拉低信誉分最多的几项（得分率 < 60%） */
        val weak: List<Part> get() = parts.filter { it.value < 0.6 }.sortedBy { it.value }
        /** 最好的几项 */
        val strong: List<Part> get() = parts.filter { it.value >= 0.9 }.sortedByDescending { it.weight }
    }

    enum class Level(val label: String) { A("优秀"), B("良好"), C("一般"), D("较差"), NONE("暂无") }

    fun levelOf(x: Double) = when { x >= 90 -> Level.A; x >= 75 -> Level.B; x >= 60 -> Level.C; else -> Level.D }

    /** 保留 2 位小数（三端比较分段时用同样的值） */
    fun r2(x: Double): Double = Math.round(x * 100) / 100.0

    private fun handed(s: HwStatus) = s == HwStatus.SUBMITTED || s == HwStatus.EXCELLENT || s == HwStatus.LATE
    private fun pct(x: Double) = "${(x * 100).roundToInt()}%"

    /** 全班在时刻 [at] 的个人信誉分（从高到低；没有数据的排在最后） */
    fun all(c: SchoolClass, at: Long = System.currentTimeMillis()): List<Result> {
        val sorted = c.assignments.filter { it.createdAt <= at }.sortedWith(compareByDescending<Assignment> { it.createdAt }.thenByDescending { it.id })
        val exams = c.exams.filter { it.date <= at }.sortedWith(compareBy<Exam> { it.date }.thenBy { it.id })
        return c.students.map { one(it, sorted, exams) }.sortedWith(compareByDescending<Result> { it.hasData }.thenByDescending { it.index })
    }

    /** 一位同学的信誉分；[newestFirst] 为截至某一刻的作业（新 → 旧），[exams] 为截至这一刻的考试（旧 → 新） */
    fun one(st: Student, newestFirst: List<Assignment>, exams: List<Exam>): Result {
        val m = newestFirst.mapNotNull { a -> (a.statuses[st.id] ?: HwStatus.NONE).takeIf { it != HwStatus.NONE && it != HwStatus.LEAVE }?.let { a to it } }
        val parts = ArrayList<Part>()
        if (m.isNotEmpty()) {
            val m10 = m.take(10); val n = m10.size.toDouble()
            val rate = m10.count { handed(it.second) } / n
            parts += Part("rate", "交齐", 0.25, rate, "最近 ${m10.size} 次交了 ${m10.count { handed(it.second) }} 次（${pct(rate)}）")
            val onTime = m10.count { it.second == HwStatus.SUBMITTED || it.second == HwStatus.EXCELLENT } / n
            parts += Part("ontime", "按时", 0.15, onTime, "按时 ${pct(onTime)}，迟交 ${m10.count { it.second == HwStatus.LATE }} 次")
            val exc = m10.count { it.second == HwStatus.EXCELLENT } / n
            parts += Part("excellent", "优秀", 0.10, minOf(1.0, exc / 0.3), "优秀 ${m10.count { it.second == HwStatus.EXCELLENT }} 次（${pct(exc)}）")
            val run = m.takeWhile { handed(it.second) }.size
            parts += Part("streak", "连续交", 0.10, minOf(run, 5) / 5.0, if (run == 0) "最近一次没交" else "已连续交 $run 次")
            val bad = m10.count { (a, _) -> a.failedReview(st.id) || a.plagiarized(st.id) }
            parts += Part("integrity", "诚信", 0.20, maxOf(0.0, 1 - 0.5 * bad), if (bad == 0) "最近没有复审不合格、抄袭" else "最近 ${m10.size} 次里复审不合格 / 抄袭 $bad 次")
            val trend = if (m.size < 8) 0.5 else {
                val a5 = m.take(5).count { handed(it.second) } / 5.0
                val b = m.drop(5).take(5); val b5 = b.count { handed(it.second) }.toDouble() / b.size
                (0.5 + (a5 - b5) / 0.4 * 0.5).coerceIn(0.0, 1.0)
            }
            parts += Part("trend", "进步", 0.05, trend, if (m.size < 8) "记录不足 8 次" else if (trend > 0.5) "最近 5 次比之前好" else if (trend < 0.5) "最近 5 次比之前差" else "和之前持平")
        }
        val taken = exams.filter { it.fullScore > 0 && st.id in it.scores }.takeLast(3)
        if (taken.isNotEmpty()) {
            val p = taken.map { it.scores.getValue(st.id) / it.fullScore }.average().coerceIn(0.0, 1.0)
            parts += Part("exam", "考试", 0.15, p, "最近 ${taken.size} 场平均得分率 ${pct(p)}")
        }
        if (parts.isEmpty()) return Result(st, 0.0, Level.NONE, parts, false)
        val wsum = parts.sumOf { it.weight }
        parts.forEach { it.points = it.weight / wsum * it.value * 100 }
        val idx = r2(parts.sumOf { it.points })
        return Result(st, idx, levelOf(idx), parts, true)
    }

    /** 已命名、2 人以上的小组 */
    fun groupsOf(c: SchoolClass): Map<String, List<Student>> = c.students.filter { it.group.isNotBlank() }.groupBy { it.group }.filterValues { it.size >= 2 }

    /** 各组在时刻 [at] 的小组信誉分（组内有信誉分的同学的平均；没有的组不出现） */
    fun groups(c: SchoolClass, at: Long = System.currentTimeMillis(), personal: List<Result> = all(c, at)): Map<String, Double> {
        val by = personal.associateBy { it.student.id }
        return groupsOf(c).mapNotNull { (g, ms) ->
            val v = ms.mapNotNull { m -> by[m.id]?.takeIf { it.hasData }?.index }
            if (v.isEmpty()) null else g to r2(v.average())
        }.toMap()
    }

    /** 一句话：等级、最强项、最弱项 */
    fun summary(r: Result): String = if (!r.hasData) "暂无记录" else buildString {
        append("信誉分 ${r.index.fmt()}（${r.level.label}）")
        r.strong.firstOrNull()?.let { append("，${it.label}好") }
        r.weak.firstOrNull()?.let { append("，${it.label}偏弱：${it.detail}") }
    }
}

/**
 * 按信誉分给积分加减分（三端同一算法）：按 [period]（每次作业 / 每天 / 每周 / 每月）分段，
 * 每段在这段最后一次作业的时刻算信誉分：≥ [excellentAt] 加 [excellentPts]，≥ [goodAt] 加 [goodPts]，
 * < [badBelow] 扣 [badPts]，< [poorBelow] 扣 [poorPts]，其余 0。
 * 个人（[personal]）和小组（[group]，小组信誉分，全组每人同样加减）分别选：
 * both 加分和扣分 / plus 只加不扣 / minus 只扣不加 / none 不计入（默认）。
 * 个人：这一段有作业记录（不含请假、未登记）的同学才算；小组：这一段有组员有记录的组才算。
 * 一段的分按这段最后一次作业的时间计入，每人积分重置后只计重置之后的段。
 */
@Serializable
data class CreditRules(
    val personal: String = "none",
    val group: String = "none",
    val period: String = "week",
    val excellentAt: Double = 90.0,
    val excellentPts: Double = 2.0,
    val goodAt: Double = 80.0,
    val goodPts: Double = 1.0,
    val poorBelow: Double = 60.0,
    val poorPts: Double = -1.0,
    val badBelow: Double = 40.0,
    val badPts: Double = -2.0,
) {
    val periodLabel: String get() = periodLabelOf(period)
    val active: Boolean get() = personal != "none" || group != "none"

    /** 信誉分 → 加减分（还没按方式过滤） */
    fun bandPoints(x: Double): Double = when {
        x >= excellentAt -> excellentPts
        x >= goodAt -> goodPts
        x < badBelow -> badPts
        x < poorBelow -> poorPts
        else -> 0.0
    }

    fun bandsText(): String = "≥${excellentAt.fmt()} ${excellentPts.signed()}、≥${goodAt.fmt()} ${goodPts.signed()}、<${poorBelow.fmt()} ${poorPts.signed()}、<${badBelow.fmt()} ${badPts.signed()}"

    companion object {
        val MODES = listOf("both", "plus", "minus", "none")
        fun modeLabel(m: String) = when (m) { "both" -> "加分和扣分"; "plus" -> "只加不扣"; "minus" -> "只扣不加"; else -> "不计入" }
        fun apply(m: String, x: Double) = when (m) { "both" -> x; "plus" -> maxOf(0.0, x); "minus" -> minOf(0.0, x); else -> 0.0 }
    }
}

/** 一段的信誉分加减分 */
data class CreditPeriod(
    val key: String, val label: String, val time: Long,
    /** 学生 id → (个人信誉分, 加减分)，只含这一段参加的同学 */
    val personal: Map<String, Pair<Double, Double>>,
    /** 组名 → (小组信誉分, 每人加减分)，只含这一段参加的组 */
    val groups: Map<String, Pair<Double, Double>>,
)

/** 某位同学某一段的一笔信誉分加减分 */
data class CreditPoint(val period: CreditPeriod, val group: String?, val credit: Double, val points: Double)

private val creditCache = IdentityCache<List<CreditPeriod>>()

/** 全班每一段的信誉分加减分（旧 → 新）；个人、小组都“不计入”时为空 */
fun SchoolClass.creditPeriods(rules: ScoreRules): List<CreditPeriod> = creditCache.get(this, rules) { computeCreditPeriods(rules.credit) }

private fun SchoolClass.computeCreditPeriods(cr: CreditRules): List<CreditPeriod> {
    if (!cr.active || students.isEmpty()) return emptyList()
    val sorted = assignments.sortedWith(compareBy<Assignment> { it.createdAt }.thenBy { it.id })
    val buckets = LinkedHashMap<String, Pair<String, MutableList<Assignment>>>()
    for (a in sorted) { val (k, l) = periodKeyOf(cr.period, a); buckets.getOrPut(k) { l to ArrayList() }.second += a }
    val groups = Credit.groupsOf(this)
    val examsSorted = exams.sortedWith(compareBy<Exam> { it.date }.thenBy { it.id })
    val out = ArrayList<CreditPeriod>()
    for ((key, v) in buckets) {
        val (label, list) = v
        val t = list.last().createdAt
        val active = students.filter { s -> list.any { a -> (a.statuses[s.id] ?: HwStatus.NONE).let { it != HwStatus.NONE && it != HwStatus.LEAVE } } }.map { it.id }.toSet()
        if (active.isEmpty()) continue
        val newest = sorted.filter { it.createdAt <= t }.asReversed()
        val ex = examsSorted.filter { it.date <= t }
        val res = students.associate { it.id to Credit.one(it, newest, ex) }
        val personal = if (cr.personal == "none") emptyMap() else active.mapNotNull { id ->
            res.getValue(id).takeIf { it.hasData }?.let { id to (it.index to CreditRules.apply(cr.personal, cr.bandPoints(it.index))) }
        }.toMap()
        val gs = if (cr.group == "none") emptyMap() else groups.mapNotNull { (g, ms) ->
            if (ms.none { it.id in active }) return@mapNotNull null
            val vals = ms.mapNotNull { m -> res.getValue(m.id).takeIf { it.hasData }?.index }
            if (vals.isEmpty()) null else { val x = Credit.r2(vals.average()); g to (x to CreditRules.apply(cr.group, cr.bandPoints(x))) }
        }.toMap()
        out += CreditPeriod(key, label, t, personal, gs)
    }
    return out
}

/** 每位同学的信誉分加减分（只含不为 0 的）：学生 id → 列表 */
fun SchoolClass.creditPoints(rules: ScoreRules, periods: List<CreditPeriod> = creditPeriods(rules)): Map<String, List<CreditPoint>> {
    if (periods.isEmpty()) return emptyMap()
    val groups = Credit.groupsOf(this)
    val out = HashMap<String, MutableList<CreditPoint>>()
    for (p in periods) {
        for ((id, v) in p.personal) if (v.second != 0.0) out.getOrPut(id) { ArrayList() } += CreditPoint(p, null, v.first, v.second)
        for ((g, v) in p.groups) if (v.second != 0.0) for (m in groups[g].orEmpty()) out.getOrPut(m.id) { ArrayList() } += CreditPoint(p, g, v.first, v.second)
    }
    return out
}

/** 规则说明 */
fun CreditRules.text(): String = if (!active) "信誉分不计入积分" else
    "信誉分${periodLabel}结算：个人${CreditRules.modeLabel(personal)}、小组${CreditRules.modeLabel(group)}（${bandsText()}）"

/** 分析报告里的信誉分汇总 */
data class CreditSummary(
    val results: List<Credit.Result>,
    val average: Double,
    val levels: List<Pair<String, Int>>,
    val top: List<Credit.Result>,
    val bottom: List<Credit.Result>,
    /** 小组信誉分（高 → 低） */
    val groups: List<Pair<String, Double>>,
    /** 时段内信誉分加减分合计（全班每人每笔相加）、加分、扣分 */
    val pointsTotal: Double, val pointsPlus: Double, val pointsMinus: Double,
    val rules: CreditRules,
)

/** 全班当前信誉分汇总；[inRange] 判断一段加减分是否在报告时段内 */
fun Credit.classSummary(c: SchoolClass, rules: ScoreRules, now: Long = System.currentTimeMillis(), inRange: (Long) -> Boolean = { true }): CreditSummary {
    val res = all(c, now)
    val has = res.filter { it.hasData }
    val groups = groups(c, now, res).entries.sortedWith(compareByDescending<Map.Entry<String, Double>> { it.value }.thenBy { it.key }).map { it.key to it.value }
    val pts = c.creditPoints(rules).values.flatten().filter { inRange(it.period.time) }.map { rules.pt(it.points) }
    return CreditSummary(
        res, if (has.isEmpty()) 0.0 else r2(has.map { it.index }.average()),
        listOf(Credit.Level.A, Credit.Level.B, Credit.Level.C, Credit.Level.D).map { l -> l.label to has.count { it.level == l } },
        has.take(5), has.filter { it.index < 75 }.takeLast(5).reversed(), groups,
        pts.sum(), pts.filter { it > 0 }.sum(), pts.filter { it < 0 }.sum(), rules.credit,
    )
}
