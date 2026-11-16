package com.deepshawn.hwcounter.data

import kotlinx.serialization.Serializable
import java.util.Calendar

/**
 * 组长奖惩（三端同一算法，见网页 model.js、iPad Models.swift）。
 *
 * 按课代表选的周期（[period]：单次作业 / 每天 / 每周 / 每月）分段，每一段看组长所在小组的“人均作业扣分”：
 * 小组组员这段时间里作业自动扣分（未交递增、复审不合格翻倍、迟交等负分）之和 ÷ 组员人数，和同一段里各组的平均比较：
 * - 全组零扣分 [clean]；≤ 平均一半 [low]；≤ 平均 [below]；≤ 1.5 倍 [above]；≤ 2 倍 [high]；更高 [veryHigh]
 * - 3 组以上、不并列时：人均扣分最少的组 [top]，最多的组 [bottom]
 * - 和本组上一段比：降 30% 以上 [progress]；升 50% 且多 2 分以上 [regress]
 * - 连续：上一段的组长奖惩与这一段同号时连续计数，每多一段再 ±[streakStep]（最多算 [streakMax] 段）
 * - 每段合计最多 ±[cap]（0 不限）
 * 只对已命名、2 人以上、设置了组长的小组生效；这一段组员都请假 / 未登记时不计。
 * 组长积分每次重置开始新一轮：连续和“上一段”都重新算；一段的分按这段最后一次作业的时间计入。
 */
@Serializable
data class LeaderRules(
    val enabled: Boolean = true,
    /** assignment / day / week / month */
    val period: String = "week",
    val clean: Double = 3.0,
    val low: Double = 2.0,
    val below: Double = 1.0,
    val above: Double = -1.0,
    val high: Double = -2.0,
    val veryHigh: Double = -3.0,
    val top: Double = 1.0,
    val bottom: Double = -1.0,
    val progress: Double = 1.0,
    val regress: Double = -1.0,
    val streakStep: Double = 1.0,
    val streakMax: Int = 3,
    val cap: Double = 5.0,
) {
    val periodLabel: String get() = periodLabelOf(period)
}

/** 组长某一段的奖惩 */
data class LeaderPeriod(
    val leaderId: String, val group: String, val key: String, val label: String,
    /** 这一段最后一次作业的时间（计入积分的时间） */
    val time: Long,
    val perCapita: Double, val average: Double, val parts: List<Pair<String, Double>>,
) {
    val points: Double get() = parts.sumOf { it.second }
}

/** 某次作业所在的段 */
fun LeaderRules.periodKey(a: Assignment): Pair<String, String> = periodKeyOf(period, a)

/** 某次作业所在的段：单次作业以作业为段，其余按日 / 周（周一起）/ 月 */
fun periodKeyOf(period: String, a: Assignment): Pair<String, String> =
    if (period == "assignment") a.id to "「${a.title}」" else periodKeyAt(period, a.createdAt)

/** 某个时间所在的日 / 周 / 月 */
fun periodKeyAt(period: String, t: Long): Pair<String, String> {
    val cal = Calendar.getInstance().apply { timeInMillis = t; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
    return when (period) {
        "day" -> "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.MONTH) + 1}-${cal.get(Calendar.DAY_OF_MONTH)}" to "${cal.get(Calendar.MONTH) + 1}月${cal.get(Calendar.DAY_OF_MONTH)}日"
        "month" -> "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.MONTH) + 1}" to "${cal.get(Calendar.YEAR)}年${cal.get(Calendar.MONTH) + 1}月"
        else -> {
            cal.add(Calendar.DAY_OF_YEAR, -((cal.get(Calendar.DAY_OF_WEEK) + 5) % 7))
            val m = cal.get(Calendar.MONTH) + 1; val d = cal.get(Calendar.DAY_OF_MONTH)
            val key = "${cal.get(Calendar.YEAR)}-$m-$d"
            cal.add(Calendar.DAY_OF_YEAR, 6)
            key to "$m/$d–${cal.get(Calendar.MONTH) + 1}/${cal.get(Calendar.DAY_OF_MONTH)} 这周"
        }
    }
}

fun periodLabelOf(period: String) = when (period) { "assignment" -> "每次作业"; "day" -> "每天"; "month" -> "每月"; else -> "每周" }

private fun fmt2(x: Double): String { val r = Math.round(x * 10) / 10.0; return if (r == Math.floor(r)) r.toLong().toString() else r.toString() }

/** 全班所有组长的每段奖惩：组长 id → 各段（旧 → 新） */
fun SchoolClass.leaderPeriods(rules: ScoreRules): Map<String, List<LeaderPeriod>> {
    val lr = rules.leader
    if (!lr.enabled) return emptyMap()
    val groups = students.filter { it.group.isNotBlank() }.groupBy { it.group }.filterValues { it.size >= 2 }
    val leaders = groups.keys.mapNotNull { g -> leaderOf(g)?.let { g to it } }.toMap()
    if (leaders.isEmpty()) return emptyMap()
    val auto = groups.values.flatten().associate { it.id to autoPoints(rules, it.id) }
    val sorted = assignments.sortedWith(compareBy<Assignment> { it.createdAt }.thenBy { it.id })
    val buckets = LinkedHashMap<String, Pair<String, MutableList<Assignment>>>()
    for (a in sorted) { val (k, l) = lr.periodKey(a); buckets.getOrPut(k) { l to ArrayList() }.second += a }
    val resetsOf = leaders.values.associate { st -> st.id to recharges.filter { st.id in it.studentIds }.sortedWith(compareBy<ScoreRecharge> { it.start }.thenBy { it.time }.thenBy { it.id }) }
    val round = HashMap<String, String?>(); val prevD = HashMap<String, Double>(); val lastSub = HashMap<String, Double>(); val run = HashMap<String, Int>()
    val out = HashMap<String, MutableList<LeaderPeriod>>()
    for ((key, v) in buckets) {
        val (label, list) = v
        // 这一段里各组的人均扣分（组员都请假 / 未登记的组不参加）
        val per = groups.mapNotNull { (g, ms) ->
            val active = ms.any { m -> list.any { a -> (a.statuses[m.id] ?: HwStatus.NONE).let { it != HwStatus.NONE && it != HwStatus.LEAVE } } }
            if (!active) return@mapNotNull null
            val total = ms.sumOf { m -> list.sumOf { a -> (auto.getValue(m.id)[a.id] ?: 0.0).let { if (it < 0) -it else 0.0 } } }
            g to total / ms.size
        }.toMap()
        if (per.isEmpty()) continue
        val avg = per.values.average()
        val best = per.values.min(); val worst = per.values.max()
        val first = list.first().createdAt; val last = list.last().createdAt
        for ((g, d) in per) {
            val leader = leaders[g] ?: continue
            val r = resetsOf.getValue(leader.id).lastOrNull { it.counts(first) }?.id
            if (!round.containsKey(g) || round[g] != r) { round[g] = r; prevD.remove(g); lastSub.remove(g); run.remove(g) }
            val parts = ArrayList<Pair<String, Double>>()
            when {
                d <= 1e-9 -> parts += "全组零扣分" to lr.clean
                d <= avg * 0.5 -> parts += "人均扣分 ${fmt2(d)}，不到各组平均 ${fmt2(avg)} 的一半" to lr.low
                d <= avg -> parts += "人均扣分 ${fmt2(d)}，不高于各组平均 ${fmt2(avg)}" to lr.below
                d <= avg * 1.5 -> parts += "人均扣分 ${fmt2(d)}，高于各组平均 ${fmt2(avg)}" to lr.above
                d <= avg * 2 -> parts += "人均扣分 ${fmt2(d)}，超过各组平均 1.5 倍" to lr.high
                else -> parts += "人均扣分 ${fmt2(d)}，超过各组平均 2 倍" to lr.veryHigh
            }
            if (per.size >= 3 && best < worst) {
                if (d == best && per.values.count { it == best } == 1) parts += "人均扣分各组最少" to lr.top
                if (d == worst && per.values.count { it == worst } == 1) parts += "人均扣分各组最多" to lr.bottom
            }
            prevD[g]?.let { p ->
                if (p > 0 && d <= p * 0.7) parts += "比上一段少（${fmt2(p)} → ${fmt2(d)}）" to lr.progress
                else if (d >= p * 1.5 && d - p >= 2) parts += "比上一段多（${fmt2(p)} → ${fmt2(d)}）" to lr.regress
            }
            val sub = parts.sumOf { it.second }
            val prevSub = lastSub[g]
            val k = if (sub != 0.0 && prevSub != null && prevSub != 0.0 && (prevSub > 0) == (sub > 0)) (run[g] ?: 1) else 0
            if (lr.streakMax > 0 && k > 0) {
                val n = minOf(k, lr.streakMax)
                parts += (if (sub > 0) "连续 ${k + 1} 段表现好" else "连续 ${k + 1} 段扣分偏多") to (if (sub > 0) 1 else -1) * n * lr.streakStep
            }
            run[g] = if (k > 0) k + 1 else 1
            lastSub[g] = sub
            prevD[g] = d
            val total = parts.sumOf { it.second }
            if (lr.cap > 0 && kotlin.math.abs(total) > lr.cap) parts += "每段最多 ±${fmt2(lr.cap)}" to ((if (total > 0) lr.cap else -lr.cap) - total)
            out.getOrPut(leader.id) { ArrayList() } += LeaderPeriod(leader.id, g, key, label, last, d, avg, parts)
        }
        // 没参加这一段的组：连续不中断，上一段保持
    }
    return out
}
