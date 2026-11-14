package com.deepshawn.hwcounter.data

import kotlinx.serialization.Serializable

/**
 * 积分充值：把选中学生的当前积分重置为 [target]，并留下一条记录。
 *
 * - 只重置积分：作业状态、未交次数、备注、原有加减分流水全部原样保留；
 * - 充值后的分数 = target + 充值之后布置的作业自动计分 + 充值之后的手动加减分；
 * - 充值之前的记录仍然可以查看，只是不再计入当前积分；
 * - 删除这条记录即可撤销充值，分数恢复为按全部历史计算。
 *
 * 三端（Android / iPad / 网页）使用同一格式与同一算法。
 */
@Serializable
data class ScoreRecharge(
    val id: String = newId(),
    val time: Long = System.currentTimeMillis(),
    val target: Double,
    val studentIds: List<String>,
    /** 充值前每人的积分（仅作记录展示，不参与计算） */
    val before: Map<String, Double> = emptyMap(),
    val note: String = "",
)

/** 每位学生最近一次生效的充值 */
fun SchoolClass.latestRecharges(): Map<String, ScoreRecharge> {
    if (recharges.isEmpty()) return emptyMap()
    val out = HashMap<String, ScoreRecharge>()
    for (r in recharges.sortedWith(compareBy<ScoreRecharge> { it.time }.thenBy { it.id })) {
        for (sid in r.studentIds) out[sid] = r
    }
    return out
}

/** 该条加减分是否计入当前积分 */
fun ScoreEntry.countsAfter(r: ScoreRecharge?) = r == null || time > r.time
fun Assignment.countsAfter(r: ScoreRecharge?) = r == null || createdAt > r.time

/** 充值：新增一条记录，不改动任何历史数据 */
fun SchoolClass.recharge(rules: ScoreRules, studentIds: Collection<String>, target: Double, note: String = "", time: Long = System.currentTimeMillis()): SchoolClass {
    if (!target.isFinite()) return this
    val ids = students.map { it.id }.filter { it in studentIds.toSet() }
    if (ids.isEmpty()) return this
    val now = studentStats(rules).associate { it.student.id to it.score }
    val r = ScoreRecharge(time = time, target = target, studentIds = ids, before = ids.associateWith { now[it] ?: 0.0 }, note = note.trim())
    return copy(recharges = recharges + r)
}

fun SchoolClass.removeRecharge(id: String) = copy(recharges = recharges.filterNot { it.id == id })

/** 积分流水中的一行（手动加减分 / 作业自动计分 / 充值） */
data class LedgerRow(
    val key: String,
    val kind: Kind,
    val time: Long,
    val delta: Double,
    val title: String,
    /** 是否计入当前积分（充值之前的记录为 false） */
    val counted: Boolean,
    val entryId: String? = null,
    val assignmentId: String? = null,
    val rechargeId: String? = null,
) {
    enum class Kind { MANUAL, AUTO, RECHARGE }
}

/** 某位学生的完整积分流水（新 → 旧） */
fun SchoolClass.ledger(rules: ScoreRules, studentId: String): List<LedgerRow> {
    val last = latestRecharges()[studentId]
    val rows = ArrayList<LedgerRow>()
    for (e in scores) if (e.studentId == studentId) {
        rows += LedgerRow("m" + e.id, LedgerRow.Kind.MANUAL, e.time, e.delta, e.reason, e.countsAfter(last), entryId = e.id)
    }
    for (a in assignments) {
        val st = a.statuses[studentId] ?: HwStatus.NONE
        val d = rules.of(st)
        if (d != 0.0) rows += LedgerRow("a" + a.id, LedgerRow.Kind.AUTO, a.createdAt, d, "${a.subject}「${a.title}」${st.label}", a.countsAfter(last), assignmentId = a.id)
    }
    for (r in recharges) if (studentId in r.studentIds) {
        val before = r.before[studentId]
        rows += LedgerRow(
            "r" + r.id, LedgerRow.Kind.RECHARGE, r.time, if (before != null) r.target - before else 0.0,
            "积分充值至 ${r.target.fmt()}" + (if (before != null) "（原 ${before.fmt()}）" else "") + (if (r.note.isNotBlank()) " · ${r.note}" else ""),
            counted = r.id == last?.id, rechargeId = r.id,
        )
    }
    return rows.sortedWith(compareByDescending<LedgerRow> { it.time }.thenBy { it.kind.ordinal })
}
