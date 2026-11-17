package com.deepshawn.hwcounter.data

import kotlinx.serialization.Serializable

/**
 * 积分重置：把选中学生的当前积分重置为 [target]，并留下一条记录。
 *
 * - 只重置积分：作业状态、未交次数、备注、原有加减分流水全部原样保留；
 * - 重置后的分数 = target + 重置之后布置的作业自动计分 + 重置之后的手动加减分；
 * - 重置之前的记录仍然可以查看，只是不再计入当前积分；
 * - 删除这条记录即可撤销重置，分数恢复为按全部历史计算。
 * - 可以从某次作业或某一天起生效（[effectiveAt]）：该作业 / 当天 0 点及之后的作业与加减分计入，之前的不计入。
 *
 * 三端（Android / iPad / 网页）使用同一格式与同一算法。
 */
@Serializable
data class ScoreRecharge(
    val id: String = newId(),
    val time: Long = System.currentTimeMillis(),
    val target: Double,
    val studentIds: List<String>,
    /** 重置前每人的积分（仅作记录展示，不参与计算） */
    val before: Map<String, Double> = emptyMap(),
    val note: String = "",
    /** 生效时间：null = 操作时立即生效；否则从这一刻（含）起重新计分 */
    val effectiveAt: Long? = null,
    /** 选择“从某次作业起”时记下作业，便于展示 */
    val assignmentId: String? = null,
    val assignmentTitle: String = "",
) {
    /** 计分起点 */
    val start: Long get() = effectiveAt ?: time

    /** 时刻 t 的记录是否计入（立即生效时只计入之后的；指定起点时含起点本身） */
    fun counts(t: Long) = if (effectiveAt != null) t >= effectiveAt else t > time
}

/** 每位学生最近一次生效的重置 */
fun SchoolClass.latestRecharges(): Map<String, ScoreRecharge> {
    if (recharges.isEmpty()) return emptyMap()
    val out = HashMap<String, ScoreRecharge>()
    for (r in recharges.sortedWith(compareBy<ScoreRecharge> { it.start }.thenBy { it.time }.thenBy { it.id })) {
        for (sid in r.studentIds) out[sid] = r
    }
    return out
}

/** 该条加减分是否计入当前积分 */
fun ScoreEntry.countsAfter(r: ScoreRecharge?) = r == null || r.counts(time)
fun Assignment.countsAfter(r: ScoreRecharge?) = r == null || r.counts(createdAt)

/** 重置：新增一条记录，不改动任何历史数据 */
fun SchoolClass.recharge(
    rules: ScoreRules, studentIds: Collection<String>, target: Double, note: String = "", time: Long = System.currentTimeMillis(),
    effectiveAt: Long? = null, assignment: Assignment? = null,
): SchoolClass {
    if (!target.isFinite()) return this
    val ids = students.map { it.id }.filter { it in studentIds.toSet() }
    if (ids.isEmpty()) return this
    val now = studentStats(rules).associate { it.student.id to it.score }
    val start = assignment?.createdAt ?: effectiveAt
    val r = ScoreRecharge(
        time = time, target = target, studentIds = ids, before = ids.associateWith { now[it] ?: 0.0 }, note = note.trim(),
        effectiveAt = start, assignmentId = assignment?.id, assignmentTitle = assignment?.title.orEmpty(),
    )
    return copy(recharges = recharges + r)
}

fun SchoolClass.removeRecharge(id: String) = copy(recharges = recharges.filterNot { it.id == id })

private val dayFormat = java.text.SimpleDateFormat("yyyy年M月d日", java.util.Locale.CHINA)
fun fmtDay(t: Long): String = synchronized(dayFormat) { dayFormat.format(java.util.Date(t)) }

/** 某天 0 点（本地时区） */
fun startOfDay(t: Long): Long = java.util.Calendar.getInstance().apply {
    timeInMillis = t; set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
}.timeInMillis

/** 重置的生效说明，如“立即生效”“从「练习5」起”“从 2026年9月1日 起” */
fun ScoreRecharge.startLabel(dayFmt: (Long) -> String): String = when {
    effectiveAt == null -> "立即生效"
    assignmentId != null -> "从「${assignmentTitle.ifBlank { "所选作业" }}」起"
    else -> "从 ${dayFmt(effectiveAt)} 起"
}

/** 积分流水中的一行（手动加减分 / 作业自动计分 / 重置） */
data class LedgerRow(
    val key: String,
    val kind: Kind,
    val time: Long,
    val delta: Double,
    val title: String,
    /** 是否计入当前积分（重置之前的记录为 false） */
    val counted: Boolean,
    val entryId: String? = null,
    val assignmentId: String? = null,
    val rechargeId: String? = null,
) {
    enum class Kind { MANUAL, AUTO, RECHARGE, EXAM, SETTLE }
}

/** 某位学生的完整积分流水（新 → 旧） */
fun SchoolClass.ledger(rules: ScoreRules, studentId: String): List<LedgerRow> {
    val last = latestRecharges()[studentId]
    val rows = ArrayList<LedgerRow>()
    val pts = autoPointDetails(rules, studentId)
    for (e in scores) if (e.studentId == studentId) {
        rows += LedgerRow("m" + e.id, if (e.settlementId != null) LedgerRow.Kind.SETTLE else LedgerRow.Kind.MANUAL, e.time, rules.pt(e.delta), e.reason, e.countsAfter(last), entryId = e.id)
    }
    for (a in assignments) {
        val st = a.statuses[studentId] ?: HwStatus.NONE
        val p = pts[a.id]
        val d = p?.points ?: rules.of(st)
        val extra = when {
            p != null && p.plagiarism > 0 -> {
                val who = plagiarismDesc(a, studentId)
                val review = if (p.reviewFailed) "，复审不合格再 ×${rules.review.multiplier.fmt()}，合计 ×${(p.plagiarismFactor * rules.review.multiplier).fmt()}" else ""
                "（$who，本轮第 ${p.plagiarism} 次，扣分 ×${p.plagiarismFactor.fmt()}$review${p.streakNote(rules)?.let { "，$it" } ?: ""}）"
            }
            p != null && p.reviewFailed -> "（复审不合格，扣分 ×${rules.review.multiplier.fmt()}${p.streakNote(rules)?.let { "，$it" } ?: ""}）"
            p != null && p.streakNote(rules) != null -> "（${p.streakNote(rules)}）"
            p != null && p.bonus != 0.0 -> "（连续交第 ${p.handStreak} 次 ${p.bonus.signed()}）"
            else -> ""
        }
        if (d != 0.0) rows += LedgerRow("a" + a.id, LedgerRow.Kind.AUTO, a.createdAt, d, "${a.subject}「${a.title}」${st.label}$extra", a.countsAfter(last), assignmentId = a.id)
    }
    for ((p, st, d) in groupRankPoints(rules)[studentId].orEmpty()) {
        rows += LedgerRow("g" + p.key, LedgerRow.Kind.AUTO, p.time, rules.pt(d), "小组排名（${st.group}）${p.label}第 ${st.rank} 名（综合 ${Math.round(st.score * 100)}）", last?.counts(p.time) ?: true)
    }
    for (cp in creditPoints(rules)[studentId].orEmpty()) {
        val p = cp.period
        val title = if (cp.group == null) "信誉分 ${p.label}：个人信誉 ${cp.credit.fmt()}（${Credit.levelOf(cp.credit).label}）"
        else "信誉分 ${p.label}：小组（${cp.group}）信誉 ${cp.credit.fmt()}（${Credit.levelOf(cp.credit).label}）"
        rows += LedgerRow((if (cp.group == null) "cp" else "cg") + p.key, LedgerRow.Kind.AUTO, p.time, rules.pt(cp.points), title, last?.counts(p.time) ?: true)
    }
    for (lp in leaderPeriods(rules)[studentId].orEmpty()) {
        if (lp.points == 0.0) continue
        rows += LedgerRow("l" + lp.key, LedgerRow.Kind.AUTO, lp.time, rules.pt(lp.points), "组长（${lp.group}）${lp.label}：" + lp.parts.joinToString("；") { "${it.first} ${it.second.signed()}" }, last?.counts(lp.time) ?: true)
    }
    for (e in exams) {
        val d = rules.pt(rules.exam.deltaFor(e, studentId))
        if (d != 0.0) {
            val what = if (studentId in e.absent) "缺考" else "${e.scores[studentId]?.fmt()} 分"
            rows += LedgerRow("e" + e.id, LedgerRow.Kind.EXAM, e.date, d, "考试「${e.title}」$what", last?.counts(e.date) ?: true)
        }
    }
    for (r in recharges) if (studentId in r.studentIds) {
        val before = r.before[studentId]
        rows += LedgerRow(
            "r" + r.id, LedgerRow.Kind.RECHARGE, r.start, if (before != null) r.target - before else 0.0,
            "积分重置为 ${r.target.fmt()}" + (if (r.effectiveAt != null) "（${r.startLabel(::fmtDay)}）" else if (before != null) "（原 ${before.fmt()}）" else "") + (if (r.note.isNotBlank()) " · ${r.note}" else ""),
            counted = r.id == last?.id, rechargeId = r.id,
        )
    }
    return rows.sortedWith(compareByDescending<LedgerRow> { it.time }.thenBy { it.kind.ordinal })
}
