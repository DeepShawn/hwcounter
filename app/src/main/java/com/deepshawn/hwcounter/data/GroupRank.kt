package com.deepshawn.hwcounter.data

import kotlinx.serialization.Serializable

/**
 * 小组对抗排名与整组加减分（三端同一算法，见网页 model.js、iPad Models.swift）。
 *
 * 按 [period]（每次作业 / 每天 / 每周 / 每月）分段，每段给已命名、2 人以上的小组算综合分（0–1）：
 * 交齐率 50% + 人均作业扣分 25%（各组里扣分最多的组为 0、零扣分为 1）+ 优秀率 15% + 考试得分率 10%
 * （这一段没有考试或按“每次作业”分段时，考试权重按比例分给其他三项）。
 * 综合分保留 4 位小数比较，相同则名次并列（1、1、3…）。
 * 名次第 1 全组每人 [first]、第 2 [second]、第 3 [third]；3 组以上时最后一名（且不是第 1）每人 [last]（代替前面的名次分）。
 * 这一段组员都请假 / 未登记的组不参加；参加的组少于 2 个时这一段不排名。
 * 每人这一段的分按这段最后一次作业的时间计入，每人积分重置后只计重置之后的段。
 */
@Serializable
data class GroupRankRules(
    val enabled: Boolean = true,
    val period: String = "week",
    val first: Double = 2.0,
    val second: Double = 1.0,
    val third: Double = 0.0,
    val last: Double = -1.0,
    val wRate: Double = 0.50,
    val wDeduct: Double = 0.25,
    val wExcellent: Double = 0.15,
    val wExam: Double = 0.10,
) {
    val periodLabel: String get() = periodLabelOf(period)
}

/** 某一段里某个小组的成绩 */
data class GroupStanding(
    val group: String, val rank: Int, val score: Double, val points: Double,
    val rate: Double, val deduct: Double, val excellent: Double, val exam: Double?,
)

/** 一段的排名 */
data class RankPeriod(val key: String, val label: String, val time: Long, val standings: List<GroupStanding>)

private val HANDED_SET = setOf(HwStatus.SUBMITTED, HwStatus.EXCELLENT, HwStatus.LATE)

/** 名次 → 每人加减分 */
fun GroupRankRules.pointsFor(rank: Int, worst: Int, n: Int): Double = when {
    n >= 3 && rank == worst && rank > 1 -> last
    rank == 1 -> first
    rank == 2 -> second
    rank == 3 -> third
    else -> 0.0
}

/** 全班每一段的小组排名（旧 → 新） */
fun SchoolClass.groupRankPeriods(rules: ScoreRules): List<RankPeriod> {
    val gr = rules.groupRank
    if (!gr.enabled) return emptyList()
    val groups = students.filter { it.group.isNotBlank() }.groupBy { it.group }.filterValues { it.size >= 2 }
    if (groups.size < 2) return emptyList()
    val auto = groups.values.flatten().associate { it.id to autoPoints(rules, it.id) }
    val sorted = assignments.sortedWith(compareBy<Assignment> { it.createdAt }.thenBy { it.id })
    val buckets = LinkedHashMap<String, Pair<String, MutableList<Assignment>>>()
    for (a in sorted) { val (k, l) = periodKeyOf(gr.period, a); buckets.getOrPut(k) { l to ArrayList() }.second += a }
    val examsBy = if (gr.period == "assignment") emptyMap() else exams.groupBy { periodKeyAt(gr.period, it.date).first }
    val out = ArrayList<RankPeriod>()
    for ((key, v) in buckets) {
        val (label, list) = v
        data class Raw(val g: String, val rate: Double, val deduct: Double, val exc: Double, val exam: Double?)
        val raws = groups.mapNotNull { (g, ms) ->
            val st = list.flatMap { a -> ms.map { a.statuses[it.id] ?: HwStatus.NONE } }.filter { it != HwStatus.NONE && it != HwStatus.LEAVE }
            if (st.isEmpty()) return@mapNotNull null
            val d = ms.sumOf { m -> list.sumOf { a -> (auto.getValue(m.id)[a.id] ?: 0.0).let { if (it < 0) -it else 0.0 } } } / ms.size
            val ex = examsBy[key].orEmpty().flatMap { e -> ms.mapNotNull { m -> e.scores[m.id]?.let { it / e.fullScore } } }
            Raw(g, st.count { it in HANDED_SET }.toDouble() / st.size, d, st.count { it == HwStatus.EXCELLENT }.toDouble() / st.size, if (ex.isEmpty()) null else ex.average())
        }
        if (raws.size < 2) continue
        val maxD = raws.maxOf { it.deduct }
        val scored = raws.map { r ->
            val dn = if (maxD <= 1e-9) 1.0 else 1 - r.deduct / maxD
            var num = gr.wRate * r.rate + gr.wDeduct * dn + gr.wExcellent * r.exc
            var den = gr.wRate + gr.wDeduct + gr.wExcellent
            if (r.exam != null && raws.all { it.exam != null }) { num += gr.wExam * r.exam; den += gr.wExam }
            r to Math.round((if (den <= 0) 0.0 else num / den) * 10000) / 10000.0
        }
        val worst = scored.maxOf { (_, sc) -> 1 + scored.count { it.second > sc } }
        val standings = scored.map { (r, sc) ->
            val rank = 1 + scored.count { it.second > sc }
            GroupStanding(r.g, rank, sc, gr.pointsFor(rank, worst, scored.size), r.rate, r.deduct, r.exc, r.exam)
        }.sortedWith(compareBy<GroupStanding> { it.rank }.thenBy { it.group })
        out += RankPeriod(key, label, list.last().createdAt, standings)
    }
    return out
}

/** 每位同学的小组排名加减分：学生 id → [(段, 名次, 分)] */
fun SchoolClass.groupRankPoints(rules: ScoreRules, periods: List<RankPeriod> = groupRankPeriods(rules)): Map<String, List<Triple<RankPeriod, GroupStanding, Double>>> {
    if (periods.isEmpty()) return emptyMap()
    val byGroup = students.filter { it.group.isNotBlank() }.groupBy { it.group }
    val out = HashMap<String, MutableList<Triple<RankPeriod, GroupStanding, Double>>>()
    for (p in periods) for (s in p.standings) if (s.points != 0.0) for (m in byGroup[s.group].orEmpty()) out.getOrPut(m.id) { ArrayList() } += Triple(p, s, s.points)
    return out
}
