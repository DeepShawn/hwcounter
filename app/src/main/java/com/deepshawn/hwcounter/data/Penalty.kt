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

/**
 * 连续交作业奖励（三端同一算法）：本轮连续按时交（已交 / 优秀）满 [after] 次后，之后每连续交一次额外加 [bonus] 分。
 * 请假、未登记不打断也不计次；未交打断；迟交默认也打断（[lateCounts] 为 true 时迟交算连续交）。每次积分重置开始新一轮。
 */
@Serializable
data class StreakBonus(
    val enabled: Boolean = true,
    val after: Int = 5,
    val bonus: Double = 1.0,
    val lateCounts: Boolean = false,
)

/**
 * 作业复审（三端同一算法）：老师复审认定不合格时，这次作业记为“未交”（多算一次未交，参与连续未交和累计次数），
 * 这一次的扣分 × [multiplier]（默认 2，即翻倍）。关闭后复审不合格按普通未交扣分。
 */
@Serializable
data class ReviewRules(
    val enabled: Boolean = true,
    val multiplier: Double = 2.0,
)

/**
 * 抄袭（三端同一算法）：作业里记下“谁抄了谁的”，抄袭者和提供者都记为“未交”（参与连续未交和累计次数），
 * 这一次的扣分 × [multiplier]（默认 2）；[escalate] 开启时累犯逐次加重：本轮第 2 次涉及抄袭再 ×2、第 3 次 ×3……最多 ×[maxTimes]。
 * 每次积分重置开始新一轮重新计次。同一次作业既复审不合格又抄袭时两个倍数叠加（相乘）。关闭后按普通未交扣分。
 */
@Serializable
data class PlagiarismRules(
    val enabled: Boolean = true,
    val multiplier: Double = 2.0,
    val escalate: Boolean = true,
    val maxTimes: Int = 4,
) {
    /** 本轮第 n 次涉及抄袭时的总倍数 */
    fun factor(n: Int): Double = multiplier * (if (escalate) n.coerceIn(1, maxOf(1, maxTimes)) else 1).toDouble()
}

/** 正分向上取整、负分向下取整（远离 0 取整） */
fun roundAway(x: Double): Double = if (x > 0) kotlin.math.ceil(x - 1e-9) else if (x < 0) kotlin.math.floor(x + 1e-9) else 0.0

/** 单笔加减分保留原值（取整只作用在最终显示的总积分上，见 [ScoreRules.shown]） */
@Suppress("UNUSED_PARAMETER", "NOTHING_TO_INLINE")
inline fun ScoreRules.pt(x: Double): Double = x

/** 最终显示的总积分：[ScoreRules.roundPoints] 开启时正分向上取整、负分向下取整 */
fun ScoreRules.shown(total: Double): Double = if (roundPoints) roundAway(total) else total

/** 某次作业的计分明细 */
data class AutoPoint(
    val points: Double, val missStreak: Int = 0, val handStreak: Int = 0, val bonus: Double = 0.0, val reviewFailed: Boolean = false,
    /** 本轮第几次涉及抄袭（0 = 这次没有），以及这次的总倍数 */
    val plagiarism: Int = 0, val plagiarismFactor: Double = 1.0,
)

/** 某位同学每次作业的自动计分明细（作业 id → 明细）：未交连续递增扣分 + 连续交奖励，每轮重置重新计 */
fun SchoolClass.autoPointDetails(rules: ScoreRules, studentId: String): Map<String, AutoPoint> {
    val esc = rules.escalation; val sb = rules.streakBonus
    val out = HashMap<String, AutoPoint>()
    val resets = recharges.filter { studentId in it.studentIds }.sortedWith(compareBy<ScoreRecharge> { it.start }.thenBy { it.time }.thenBy { it.id })
    var round: String? = null; var streak = 0; var total = 0; var run = 0; var plag = 0
    for (a in assignments.sortedWith(compareBy<Assignment> { it.createdAt }.thenBy { it.id })) {
        val st = a.statuses[studentId] ?: HwStatus.NONE
        val r = resets.lastOrNull { it.counts(a.createdAt) }?.id
        if (r != round) { round = r; streak = 0; total = 0; run = 0; plag = 0 }
        out[a.id] = when (st) {
            HwStatus.MISSING -> {
                streak++; total++; run = 0
                val base = if (esc.enabled) -esc.penalty(streak, total) else rules.of(st)
                // 抄袭与复审不合格可以叠加：两个倍数相乘
                val plagged = rules.plagiarism.enabled && a.plagiarized(studentId)
                if (plagged) plag++
                val f = if (plagged) rules.plagiarism.factor(plag) else 1.0
                val failed = rules.review.enabled && a.failedReview(studentId)
                val rv = if (failed) rules.review.multiplier else 1.0
                AutoPoint(rules.pt(base * f * rv), missStreak = streak, reviewFailed = failed, plagiarism = if (plagged) plag else 0, plagiarismFactor = f)
            }
            HwStatus.SUBMITTED, HwStatus.EXCELLENT, HwStatus.LATE -> {
                streak = 0
                val counts = st != HwStatus.LATE || sb.lateCounts
                if (counts) run++ else run = 0
                val b = if (sb.enabled && counts && run > sb.after) sb.bonus else 0.0
                AutoPoint(rules.pt(rules.of(st)) + rules.pt(b), handStreak = run, bonus = rules.pt(b))
            }
            else -> AutoPoint(rules.pt(rules.of(st)))
        }
    }
    return out
}

/** 某位同学每次作业的自动计分（作业 id → 分） */
private val autoCache = IdentityCache<HashMap<String, Map<String, Double>>>(4)

fun SchoolClass.autoPoints(rules: ScoreRules, studentId: String): Map<String, Double> {
    val m = autoCache.get(this, rules) { HashMap() }
    synchronized(m) { m[studentId]?.let { return it } }
    val v = autoPointDetails(rules, studentId).mapValues { it.value.points }
    synchronized(m) { m[studentId] = v }
    return v
}

/** 全班每位同学的作业自动计分 */
fun SchoolClass.autoPointsAll(rules: ScoreRules): Map<String, Map<String, Double>> = students.associate { it.id to autoPoints(rules, it.id) }

/** 规则说明（界面上显示） */
fun ScoreRules.missingText(): String = if (escalation.enabled)
    "未交连续递增（${escalation.base.fmt()}、${(escalation.base * escalation.base).fmt()}、${(escalation.base * escalation.base * escalation.base).fmt()}…，累计超 ${escalation.heavyAfter} 次后 ${escalation.heavyBase.fmt()}、${(escalation.heavyBase * escalation.heavyBase).fmt()}…）"
else "未交 ${missing.signed()}"
