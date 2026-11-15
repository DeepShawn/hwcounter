package com.deepshawn.hwcounter.data

import kotlinx.serialization.Serializable
import java.util.Calendar

/**
 * 每月定期重置：每月 [day] 号 0 点起，全班积分重置为 [target]。
 *
 * - 打开 App（或回到前台）时补上已经到期的重置，记录与手动重置相同，可在“重置记录”里查看和撤销；
 * - 记录 id 固定为 “auto-年月”，多台设备同时补也不会重复；
 * - 撤销某个月的定期重置后，记入 [skipped]，不会再自动补回；
 * - 只补开启（[since]）之后到期的，开启前的月份不追溯。
 */
@Serializable
data class AutoReset(
    val enabled: Boolean = true,
    val target: Double = 100.0,
    /** 每月几号（1–28） */
    val day: Int = 1,
    val note: String = "每月定期重置",
    /** 开启时间 */
    val since: Long = System.currentTimeMillis(),
    /** 撤销过的月份（yyyyMM） */
    val skipped: List<String> = emptyList(),
)

const val AUTO_RESET_PREFIX = "auto-"

/** 某月的重置时刻 */
fun autoResetMoment(year: Int, month0: Int, day: Int): Long = Calendar.getInstance().apply {
    clear(); set(year, month0, day.coerceIn(1, 28), 0, 0, 0)
}.timeInMillis

private fun key(t: Long): String = Calendar.getInstance().let { it.timeInMillis = t; "%04d%02d".format(it.get(Calendar.YEAR), it.get(Calendar.MONTH) + 1) }

/** 开启之后、到 now 为止已到期的重置（最多最近 24 个），按时间先后 */
fun AutoReset.due(now: Long = System.currentTimeMillis()): List<Pair<String, Long>> {
    if (!enabled || now <= since) return emptyList()
    val cal = Calendar.getInstance().apply { timeInMillis = now }
    val out = ArrayList<Pair<String, Long>>()
    var y = cal.get(Calendar.YEAR); var m = cal.get(Calendar.MONTH)
    repeat(24) {
        val t = autoResetMoment(y, m, day)
        if (t <= now && t > since) out += key(t) to t
        if (t <= since) return out.asReversed()
        m--; if (m < 0) { m = 11; y-- }
    }
    return out.asReversed()
}

/** 下一次定期重置的时刻 */
fun AutoReset.next(now: Long = System.currentTimeMillis()): Long {
    val cal = Calendar.getInstance().apply { timeInMillis = now }
    var y = cal.get(Calendar.YEAR); var m = cal.get(Calendar.MONTH)
    repeat(3) {
        val t = autoResetMoment(y, m, day)
        if (t > now && t > since) return t
        m++; if (m > 11) { m = 0; y++ }
    }
    return autoResetMoment(y, m, day)
}

fun ScoreRecharge.isAuto() = id.startsWith(AUTO_RESET_PREFIX)

/** 补上到期的定期重置；没有变化时返回原对象 */
fun SchoolClass.applyAutoReset(rules: ScoreRules, now: Long = System.currentTimeMillis()): SchoolClass {
    val ar = autoReset ?: return this
    if (!ar.enabled || students.isEmpty() || !ar.target.isFinite()) return this
    var c = this
    for ((k, t) in ar.due(now)) {
        val id = AUTO_RESET_PREFIX + k
        if (k in ar.skipped || c.recharges.any { it.id == id }) continue
        val before = Analytics.scoresAt(c, rules, t - 1)
        val ids = c.students.map { it.id }
        c = c.copy(recharges = c.recharges + ScoreRecharge(
            id = id, time = t, target = ar.target, studentIds = ids, before = ids.associateWith { before[it] ?: rules.initial },
            note = ar.note.ifBlank { "每月定期重置" }, effectiveAt = t,
        ))
    }
    return c
}

/** 撤销重置；撤销定期重置时记下月份，之后不再自动补回 */
fun SchoolClass.undoRecharge(id: String): SchoolClass {
    val c = removeRecharge(id)
    if (!id.startsWith(AUTO_RESET_PREFIX)) return c
    val k = id.removePrefix(AUTO_RESET_PREFIX)
    val ar = c.autoReset ?: AutoReset(enabled = false)
    return c.copy(autoReset = ar.copy(skipped = (ar.skipped + k).distinct()))
}

/** 合并后去掉已撤销月份的定期重置 */
fun SchoolClass.dropSkippedAuto(): SchoolClass {
    val sk = autoReset?.skipped.orEmpty()
    if (sk.isEmpty()) return this
    return copy(recharges = recharges.filterNot { it.isAuto() && it.id.removePrefix(AUTO_RESET_PREFIX) in sk })
}

/** 给不方便导入扩展函数的地方用 */
object AutoResetOps {
    fun SchoolClass.dropSkipped() = dropSkippedAuto()
}
