package com.deepshawn.hwcounter.data

import kotlinx.serialization.Serializable
import kotlin.math.pow

/**
 * 未交连续递增扣分（三端同一算法），三种方式（[mode]）：
 * - 1「正常加倍」：连续未交第 1 次扣 base，第 2 次 base²，第 3 次 base³……（默认 2、4、8、16）；
 * - 2「第 2 次起加倍」：第 1、2 次都扣 base，第 3 次 base²，第 4 次 base³……（默认 2、2、4、8、16）；
 * - 3「分段倍率」：在第 1 种或第 2 种（[tierBase]）的基础上，按次数分段再乘倍率（[tiers]，如第 1–2 次 ×1、第 3–4 次 ×1.5、第 5 次起 ×2），
 *   次数按连续未交次数或本轮累计未交次数算（[tierBy]）；
 * - 中间请假、未登记不打断也不计数；只要交过一次（已交 / 优秀 / 迟交）就打断，下次未交重新从第 1 次算；
 * - 本轮累计未交超过 [heavyAfter] 次（默认 6 次）后，底数变为 [heavyBase]（默认 3）；
 * - 每次积分重置开始新一轮：连续次数、累计次数清零，底数回到 [base]。
 * - [cap] > 0 时，单次最多扣 cap 分（默认 30，分段倍率乘完后再封顶）。
 * - [gapBalance]「间断未交平衡」（默认开启）：隔几次不交一次的同学，交过一次连续次数就清零，扣分一直停在最低档，
 *   未交率更高反而比偶尔连续不交的人扣得少。开启后，这一次的次数取「连续未交次数」和「最近 [gapWindow] 次有记录的作业里的未交次数」中较大的那个
 *   （请假、未登记不算记录），分段倍率按连续次数时也用这个次数。
 */
@Serializable
data class MissingEscalation(
    val enabled: Boolean = true,
    val base: Double = 2.0,
    val heavyBase: Double = 3.0,
    val heavyAfter: Int = 6,
    val cap: Double = 30.0,
    /** 扣分方式：1 正常加倍（2、4、8…），2 第 2 次起加倍（2、2、4、8…），3 分段倍率 */
    val mode: Int = 2,
    /** 分段倍率在哪种方式的基础上算：1 或 2 */
    val tierBase: Int = 2,
    /** 分段按什么计次："streak" 连续未交次数，"total" 本轮累计未交次数 */
    val tierBy: String = "streak",
    /** 分段：从第 from 次起乘 factor（之前没有覆盖到的次数 ×1） */
    val tiers: List<EscalationTier> = DEFAULT_TIERS,
    /** 间断未交平衡：次数取连续未交次数与最近 [gapWindow] 次里的未交次数中较大的 */
    val gapBalance: Boolean = true,
    val gapWindow: Int = 8,
) {
    /** 这一次用来计算扣分的次数：连续第 [streak] 次；最近窗口里第 [recent] 次 */
    fun countOf(streak: Int, recent: Int): Int = if (gapBalance) maxOf(streak, recent) else streak

    /** 间断未交平衡的说明 */
    fun gapText(): String = "间断未交平衡：次数取连续未交次数和最近 $gapWindow 次作业里的未交次数中较大的（请假、未登记不算）"

    /** 实际使用的加倍方式（1 或 2） */
    val shape: Int get() = when (mode) { 1 -> 1; 3 -> if (tierBase == 1) 1 else 2; else -> 2 }

    /** 第 n 次（按 [tierBy] 计）的分段倍率；不是分段方式时为 1 */
    fun tierFactor(n: Int): Double = if (mode != 3) 1.0 else tiers.filter { it.from <= n }.maxByOrNull { it.from }?.factor ?: 1.0

    /** 本轮第 total 次未交、连续第 streak 次时的分段倍率 */
    fun tierFactor(streak: Int, total: Int): Double = tierFactor(if (tierBy == "total") total else streak)

    /** 本轮第 total 次未交、连续第 streak 次时应扣的分（正数） */
    fun penalty(streak: Int, total: Int): Double {
        val b = if (total > heavyAfter) heavyBase else base
        val p = amount(b, streak, shape) * tierFactor(streak, total)
        return if (cap > 0) minOf(p, cap) else p
    }

    /** 从第 1 次起连续未交（还没到加重）时依次扣的分，如“2、2、6、12…”（含分段倍率和上限） */
    fun sample(b: Double = base, k: Int = 4): String = (1..k).joinToString("、") { n ->
        val p = amount(b, n, shape) * tierFactor(n)
        (if (cap > 0) minOf(p, cap) else p).fmt()
    } + "…"

    /** 方式名称 */
    val modeLabel: String get() = modeLabelOf(mode)

    /** 分段说明，如“第 1–2 次 ×1、第 3–4 次 ×1.5、第 5 次起 ×2（按连续次数）” */
    fun tiersText(): String {
        val t = tiers.sortedBy { it.from }
        if (t.isEmpty()) return "未设分段（都 ×1）"
        val parts = ArrayList<String>()
        if (t.first().from > 1) parts += "第 1${if (t.first().from > 2) "–${t.first().from - 1}" else ""} 次 ×1"
        t.forEachIndexed { i, x ->
            val to = t.getOrNull(i + 1)?.from?.minus(1)
            parts += when {
                to == null -> "第 ${x.from} 次起 ×${x.factor.fmt()}"
                to <= x.from -> "第 ${x.from} 次 ×${x.factor.fmt()}"
                else -> "第 ${x.from}–$to 次 ×${x.factor.fmt()}"
            }
        }
        return parts.joinToString("、") + "（按${tierByLabel}）"
    }

    val tierByLabel: String get() = if (tierBy == "total") "本轮累计未交次数" else "连续未交次数"

    /** 一句话说明，设置页、通知、Excel 共用 */
    fun describe(): String = when (mode) {
        1 -> "正常加倍：连续未交依次扣 ${seq(base, 1)}"
        3 -> "分段倍率（在${modeLabelOf(shape)}基础上）：${tiersText()}，依次扣 ${sample()}"
        else -> "第 2 次起加倍：连续未交依次扣 ${seq(base, 2)}"
    } + if (gapBalance) "；${gapText()}" else ""

    companion object {
        val DEFAULT_TIERS = listOf(EscalationTier(1, 1.0), EscalationTier(3, 1.5), EscalationTier(5, 2.0))

        /**
         * 连续第 n 次未交的扣分（不含分段倍率和上限）：
         * shape = 1：b、b²、b³……；shape = 2：b、b、b²、b³……（第 1、2 次相同，之后每次翻 b 倍）
         */
        fun amount(b: Double, n: Int, shape: Int = 2): Double =
            if (shape == 1) b.pow(n.coerceAtLeast(1)) else b.pow((n.coerceAtLeast(1) - 1).coerceAtLeast(1))
        /** 说明文字：“2、2、4、8…”（shape = 1 时“2、4、8、16…”） */
        fun seq(b: Double, shape: Int = 2): String = (1..4).joinToString("、") { amount(b, it, shape).fmt() } + "…"
        fun modeLabelOf(mode: Int): String = when (mode) { 1 -> "正常加倍"; 3 -> "分段倍率"; else -> "第 2 次起加倍" }
    }
}

/** 分段倍率的一段：从第 [from] 次起 × [factor] */
@Serializable
data class EscalationTier(val from: Int = 1, val factor: Double = 1.0)

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
    /** 分段倍率方式下这次的分段倍率（其余为 1） */
    val tierFactor: Double = 1.0,
    /** 最近窗口里这是第几次未交（间断未交平衡用；0 = 没有用到） */
    val recentMiss: Int = 0,
) {
    /** “连续第 3 次未交，分段 ×1.5”；不需要说明时为 null */
    fun streakNote(rules: ScoreRules): String? {
        if (!rules.escalation.enabled) return null
        val tier = if (tierFactor != 1.0) "分段 ×${tierFactor.fmt()}" else null
        val streak = when {
            recentMiss > missStreak && recentMiss >= 2 -> "近 ${rules.escalation.gapWindow} 次第 $recentMiss 次未交（间断未交平衡）"
            missStreak >= 2 -> "连续第 $missStreak 次未交"
            else -> null
        }
        return listOfNotNull(streak, tier).joinToString("，").ifEmpty { null }
    }
}

/** 某位同学每次作业的自动计分明细（作业 id → 明细）：未交连续递增扣分 + 连续交奖励，每轮重置重新计 */
fun SchoolClass.autoPointDetails(rules: ScoreRules, studentId: String): Map<String, AutoPoint> {
    val esc = rules.escalation; val sb = rules.streakBonus
    val out = HashMap<String, AutoPoint>()
    val resets = recharges.filter { studentId in it.studentIds }.sortedWith(compareBy<ScoreRecharge> { it.start }.thenBy { it.time }.thenBy { it.id })
    var round: String? = null; var streak = 0; var total = 0; var run = 0; var plag = 0
    // 最近几次有记录的作业是否未交（间断未交平衡用），每轮清空
    val recent = ArrayDeque<Boolean>()
    fun record(missed: Boolean) { recent.addLast(missed); while (recent.size > maxOf(1, esc.gapWindow)) recent.removeFirst() }
    for (a in assignments.sortedWith(compareBy<Assignment> { it.createdAt }.thenBy { it.id })) {
        val st = a.statuses[studentId] ?: HwStatus.NONE
        val r = resets.lastOrNull { it.counts(a.createdAt) }?.id
        if (r != round) { round = r; streak = 0; total = 0; run = 0; plag = 0; recent.clear() }
        out[a.id] = when (st) {
            HwStatus.MISSING -> {
                streak++; total++; run = 0
                record(true)
                val inWindow = recent.count { it }
                val n = esc.countOf(streak, inWindow)
                val base = if (esc.enabled) -esc.penalty(n, total) else rules.of(st)
                // 抄袭与复审不合格可以叠加：两个倍数相乘
                val plagged = rules.plagiarism.enabled && a.plagiarized(studentId)
                if (plagged) plag++
                val f = if (plagged) rules.plagiarism.factor(plag) else 1.0
                val failed = rules.review.enabled && a.failedReview(studentId)
                val rv = if (failed) rules.review.multiplier else 1.0
                val tf = if (esc.enabled) esc.tierFactor(n, total) else 1.0
                AutoPoint(
                    rules.pt(base * f * rv), missStreak = streak, reviewFailed = failed, plagiarism = if (plagged) plag else 0, plagiarismFactor = f, tierFactor = tf,
                    recentMiss = if (esc.enabled && esc.gapBalance) inWindow else 0,
                )
            }
            HwStatus.SUBMITTED, HwStatus.EXCELLENT, HwStatus.LATE -> {
                streak = 0
                record(false)
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
    "未交${escalation.modeLabel}（${escalation.sample()}，累计超 ${escalation.heavyAfter} 次后 ${escalation.sample(escalation.heavyBase)}${if (escalation.gapBalance) "；间断未交按近 ${escalation.gapWindow} 次计" else ""}）"
else "未交 ${missing.signed()}"

/** 这次作业这位同学的计分说明，如“本次 -8（抄袭第 1 次 ×2 × 复审不合格 ×2 = ×4）”；没有分数变化时为空 */
fun SchoolClass.assignmentPointNote(rules: ScoreRules, assignmentId: String, studentId: String): String {
    val p = autoPointDetails(rules, studentId)[assignmentId] ?: return ""
    if (p.points == 0.0) return ""
    val parts = listOfNotNull(
        if (p.plagiarism > 0) "抄袭第 ${p.plagiarism} 次 ×${p.plagiarismFactor.fmt()}" else null,
        if (p.reviewFailed) "复审不合格 ×${rules.review.multiplier.fmt()}" else null,
    )
    val total = (if (p.plagiarism > 0) p.plagiarismFactor else 1.0) * (if (p.reviewFailed) rules.review.multiplier else 1.0)
    val why = when {
        parts.size >= 2 -> parts.joinToString(" × ") + " = ×${total.fmt()}"
        parts.size == 1 -> parts[0]
        p.streakNote(rules) != null -> p.streakNote(rules)!!
        p.bonus != 0.0 -> "连续交第 ${p.handStreak} 次"
        else -> ""
    }
    return "本次 ${p.points.signed()}" + if (why.isNotEmpty()) "（$why）" else ""
}
