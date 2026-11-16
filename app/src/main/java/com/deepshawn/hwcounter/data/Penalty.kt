package com.deepshawn.hwcounter.data

import kotlinx.serialization.Serializable
import kotlin.math.pow

/**
 * 未交连续递增扣分（三端同一算法）：
 * - 每位同学按作业先后计算；连续未交第 1 次扣 base，第 2 次扣 base²，第 3 次扣 base³……（默认 2、4、8）；
 * - 中间请假、未登记不打断也不计数；只要交过一次（已交 / 优秀 / 迟交）就打断，下次未交重新从第 1 次算；
 * - 本轮累计未交超过 [heavyAfter] 次（默认 6 次）后，底数变为 [heavyBase]（默认 3：3、9、27……）；
 * - 每次积分重置开始新一轮：连续次数、累计次数清零，底数回到 [base]。
 * - [cap] > 0 时，单次最多扣 cap 分（默认 30）。
 */
@Serializable
data class MissingEscalation(
    val enabled: Boolean = true,
    val base: Double = 2.0,
    val heavyBase: Double = 3.0,
    val heavyAfter: Int = 6,
    val cap: Double = 30.0,
) {
    /** 本轮第 total 次未交、连续第 streak 次时应扣的分（正数） */
    fun penalty(streak: Int, total: Int): Double {
        val b = if (total > heavyAfter) heavyBase else base
        val p = b.pow(streak.coerceAtLeast(1))
        return if (cap > 0) minOf(p, cap) else p
    }
}

/** 某位同学每次作业的自动计分（作业 id → 分），未交按连续递增规则计算 */
fun SchoolClass.autoPoints(rules: ScoreRules, studentId: String): Map<String, Double> {
    val esc = rules.escalation
    val out = HashMap<String, Double>()
    val resets = recharges.filter { studentId in it.studentIds }.sortedWith(compareBy<ScoreRecharge> { it.start }.thenBy { it.time }.thenBy { it.id })
    var round: String? = null; var streak = 0; var total = 0
    for (a in assignments.sortedWith(compareBy<Assignment> { it.createdAt }.thenBy { it.id })) {
        val st = a.statuses[studentId] ?: HwStatus.NONE
        if (!esc.enabled) { out[a.id] = rules.of(st); continue }
        val r = resets.lastOrNull { it.counts(a.createdAt) }?.id
        if (r != round) { round = r; streak = 0; total = 0 }
        out[a.id] = when (st) {
            HwStatus.MISSING -> { streak++; total++; -esc.penalty(streak, total) }
            HwStatus.SUBMITTED, HwStatus.EXCELLENT, HwStatus.LATE -> { streak = 0; rules.of(st) }
            else -> rules.of(st)
        }
    }
    return out
}

/** 全班每位同学的作业自动计分 */
fun SchoolClass.autoPointsAll(rules: ScoreRules): Map<String, Map<String, Double>> = students.associate { it.id to autoPoints(rules, it.id) }

/** 规则说明（界面上显示） */
fun ScoreRules.missingText(): String = if (escalation.enabled)
    "未交连续递增（${escalation.base.fmt()}、${(escalation.base * escalation.base).fmt()}、${(escalation.base * escalation.base * escalation.base).fmt()}…，累计超 ${escalation.heavyAfter} 次后 ${escalation.heavyBase.fmt()}、${(escalation.heavyBase * escalation.heavyBase).fmt()}…）"
else "未交 ${missing.signed()}"
