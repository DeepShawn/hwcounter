package com.deepshawn.hwcounter.data

import kotlinx.serialization.Serializable

/**
 * 结算规则（按交齐度与缺交度给分）。交齐度 =（已交 + 优秀 + 迟交）÷ 应交（请假、未登记不算应交）。
 *
 * - 100% 且无迟交 [perfect]；≥95% [r95]；90–95% [r90]；80–90% [r80]；70–80% [r70]；<70% [below]
 * - 本轮缺交 ≥ [manyMissing] 次再加 [manyMissingDelta]；最长连续缺交 ≥ [streakMissing] 次再加 [streakMissingDelta]
 * - 进步奖：交齐度比上一轮提高 ≥ [progressPts]（比例）加 [progressDelta]
 * - 小组奖：本轮平均交齐度第一的小组每人 [groupDelta]（并列都算，未分组不参加）
 * - 本轮应交少于 [minExpected] 次不结算
 * - [withMonthlyReset]：每月定期重置时先自动结算上一轮（只对 [autoSince] 之后的定期重置）
 */
@Serializable
data class SettlementRules(
    val perfect: Double = 5.0,
    val r95: Double = 3.0,
    val r90: Double = 1.0,
    val r80: Double = 0.0,
    val r70: Double = -2.0,
    val below: Double = -5.0,
    val manyMissing: Int = 5,
    val manyMissingDelta: Double = -3.0,
    val streakMissing: Int = 3,
    val streakMissingDelta: Double = -2.0,
    val progressPts: Double = 0.10,
    val progressDelta: Double = 2.0,
    val groupDelta: Double = 2.0,
    val minExpected: Int = 3,
    val withMonthlyReset: Boolean = true,
    val autoSince: Long = 0,
)

/** 一次结算的记录；每人的加减分另存为带 [ScoreEntry.settlementId] 的普通加减分 */
@Serializable
data class Settlement(
    val id: String = newId(),
    val time: Long = System.currentTimeMillis(),
    val title: String,
    /** 本轮起点（重置生效时间），null = 有记录以来 */
    val roundStart: Long? = null,
    /** 本轮终点（不含） */
    val roundEnd: Long,
    val lines: List<SettlementLine> = emptyList(),
    /** 结算后是否开始了新一轮（对应的重置记录） */
    val resetId: String? = null,
)

@Serializable
data class SettlementLine(
    val studentId: String,
    val expected: Int,
    val rate: Double,
    val late: Int,
    val missing: Int,
    val longestMissing: Int,
    val delta: Double,
    val reasons: List<String> = emptyList(),
    /** 应交太少，未结算 */
    val skipped: Boolean = false,
)

const val AUTO_SETTLE_PREFIX = "auto-settle-"

object Settle {
    private fun pct(x: Double) = "${Math.round(x * 100)}%"

    /** 当前（或截至 [end] 的）全班一轮：起点 = 最近一次全班重置 */
    fun roundBounds(c: SchoolClass, end: Long): Pair<ScoreRecharge?, ScoreRecharge?> {
        val resets = Analytics.classResets(c).filter { it.start < end }
        return resets.lastOrNull() to resets.dropLast(1).lastOrNull()
    }

    private fun inRound(r: ScoreRecharge?, end: Long, t: Long) = (r == null || r.counts(t)) && t < end

    /** 某段作业里的交齐情况（请假、未登记不算应交） */
    private fun measure(st: Student, list: List<Assignment>): SettlementLine {
        val ins = Analytics.insight(st, list)
        val exp = ins.expected - ins.unregistered
        return SettlementLine(st.id, exp, if (exp == 0) 1.0 else ins.handedIn.toDouble() / exp, ins.late, ins.missing, ins.longestMissing, 0.0)
    }

    /** 预览结算：每人的交齐度、缺交、结算分与原因 */
    fun preview(c: SchoolClass, rules: ScoreRules, end: Long = System.currentTimeMillis()): Settlement {
        val sr = rules.settlement
        val (cur, prev) = roundBounds(c, end)
        val sorted = c.assignments.sortedBy { it.createdAt }
        val list = sorted.filter { inRound(cur, end, it.createdAt) }
        val prevList = if (cur == null) emptyList() else sorted.filter { (prev == null || prev.counts(it.createdAt)) && !cur.counts(it.createdAt) }
        val base = c.students.associate { it.id to measure(it, list) }
        val prevRate = if (prevList.isEmpty()) emptyMap() else c.students.associate { it.id to measure(it, prevList) }
            .filterValues { it.expected >= sr.minExpected }.mapValues { it.value.rate }
        // 小组奖：参加结算的成员平均交齐度最高的组
        val groupAvg = c.students.filter { it.group.isNotBlank() && (base[it.id]?.expected ?: 0) >= sr.minExpected }
            .groupBy { it.group }.filterValues { it.size >= 2 }.mapValues { (_, m) -> m.map { base.getValue(it.id).rate }.average() }
        val topRate = groupAvg.values.maxOrNull()
        val topGroups = if (topRate == null || groupAvg.size < 2) emptySet() else groupAvg.filterValues { kotlin.math.abs(it - topRate) < 1e-9 }.keys
        val lines = c.students.map { st ->
            val m = base.getValue(st.id)
            if (m.expected < sr.minExpected) return@map m.copy(skipped = true, reasons = listOf("本轮应交 ${m.expected} 次，少于 ${sr.minExpected} 次，不结算"))
            val parts = ArrayList<Pair<String, Double>>()
            when {
                m.rate >= 0.9999 && m.late == 0 -> parts += "全勤（交齐 100% 无迟交）" to sr.perfect
                m.rate >= 0.95 -> parts += "交齐度 ${pct(m.rate)}（≥95%）" to sr.r95
                m.rate >= 0.90 -> parts += "交齐度 ${pct(m.rate)}（90%–95%）" to sr.r90
                m.rate >= 0.80 -> parts += "交齐度 ${pct(m.rate)}（80%–90%）" to sr.r80
                m.rate >= 0.70 -> parts += "交齐度 ${pct(m.rate)}（70%–80%）" to sr.r70
                else -> parts += "交齐度 ${pct(m.rate)}（<70%）" to sr.below
            }
            if (sr.manyMissing > 0 && m.missing >= sr.manyMissing) parts += "本轮缺交 ${m.missing} 次" to sr.manyMissingDelta
            if (sr.streakMissing > 0 && m.longestMissing >= sr.streakMissing) parts += "最长连续缺交 ${m.longestMissing} 次" to sr.streakMissingDelta
            prevRate[st.id]?.let { p -> if (m.rate - p >= sr.progressPts - 1e-9) parts += "进步奖（${pct(p)} → ${pct(m.rate)}）" to sr.progressDelta }
            if (st.group in topGroups) parts += "小组奖（${st.group}平均交齐度第一）" to sr.groupDelta
            val shown = parts.filter { it.second != 0.0 || parts.size == 1 }
            m.copy(delta = shown.sumOf { rules.pt(it.second) }, reasons = shown.map { (t, d) -> if (d == 0.0) "$t 0" else "$t ${rules.pt(d).signed()}" })
        }
        val n = c.settlements.size + 1
        return Settlement(title = "第 $n 次结算", roundStart = cur?.start, roundEnd = end, lines = lines)
    }

    /** 写入结算：记录 + 每人一条加减分（时间 [entryTime]），返回新班级 */
    fun apply(c: SchoolClass, s: Settlement, entryTime: Long = s.time, idPrefix: String? = null): SchoolClass {
        val entries = s.lines.filter { !it.skipped && it.delta != 0.0 }.map { l ->
            ScoreEntry(
                id = if (idPrefix != null) idPrefix + l.studentId else newId(),
                studentId = l.studentId, delta = l.delta, time = entryTime,
                reason = "${s.title}：" + l.reasons.joinToString("；"), settlementId = s.id,
            )
        }
        return c.copy(settlements = c.settlements + s, scores = c.scores + entries)
    }

    /** 撤销结算：删除记录和对应的加减分（自动结算记下月份，不再补回） */
    fun undo(c: SchoolClass, id: String): SchoolClass {
        var out = c.copy(settlements = c.settlements.filterNot { it.id == id }, scores = c.scores.filterNot { it.settlementId == id })
        if (id.startsWith(AUTO_SETTLE_PREFIX)) {
            val ar = out.autoReset ?: AutoReset(enabled = false)
            out = out.copy(autoReset = ar.copy(skipped = (ar.skipped + ("S" + id.removePrefix(AUTO_SETTLE_PREFIX))).distinct()))
        }
        return out
    }

    /** 每月定期重置联动：给开启联动之后的每次定期重置补上“重置前一轮”的结算，结算分计入新一轮 */
    fun applyAuto(c: SchoolClass, rules: ScoreRules): SchoolClass {
        val sr = rules.settlement
        if (!sr.withMonthlyReset || sr.autoSince <= 0) return c
        var out = c
        for (r in c.recharges.filter { it.isAuto() }.sortedBy { it.start }) {
            val key = r.id.removePrefix(AUTO_RESET_PREFIX)
            val id = AUTO_SETTLE_PREFIX + key
            if (r.start < sr.autoSince || out.settlements.any { it.id == id } || ("S$key") in out.autoReset?.skipped.orEmpty()) continue
            // 按重置之前的数据计算（不含这次重置本身）
            val before = out.copy(recharges = out.recharges.filter { it.id != r.id && it.start < r.start })
            val p = preview(before, rules, r.start)
            val ym = key.take(4) + "年" + key.drop(4).trimStart('0') + "月"
            val s = p.copy(id = id, time = r.start, title = "$ym 定期结算", resetId = r.id)
            out = apply(out, s, entryTime = r.start, idPrefix = "$id-")
        }
        return out
    }
}
