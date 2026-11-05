package com.deepshawn.hwcounter.data

import kotlinx.serialization.Serializable

/** Immutable scoring-period events; a null assignmentId restores all-history scoring. */
@Serializable
data class ScoreReset(
    val id: String = newId(),
    val assignmentId: String? = null,
    val assignmentTitle: String = "",
    val startAt: Long = 0,
    val time: Long = System.currentTimeMillis(),
    val excludedAssignmentIds: List<String> = emptyList(),
    val ignoredScoreIds: List<String> = emptyList(),
    val keepManualSinceStart: Boolean = true,
)

private val legacyScoreResetReason = Regex("^重置分数至 [+-]?[0-9]+(?:\\.[0-9]+)?$")
fun ScoreEntry.isResetAdjustment() = kind == "reset" || legacyScoreResetReason.matches(reason)

fun ScoreReset.includes(a: Assignment) = a.createdAt >= startAt && a.id !in excludedAssignmentIds
fun ScoreReset.includes(e: ScoreEntry): Boolean =
    e.id !in ignoredScoreIds && !(e.isResetAdjustment() && e.time < time) &&
        e.time >= if (keepManualSinceStart) startAt else time

val SchoolClass.activeScoreReset: ScoreReset?
    get() = scoreResets.maxWithOrNull(compareBy<ScoreReset> { it.time }.thenBy { it.id })?.takeIf { it.assignmentId != null }

fun SchoolClass.countsTowardScore(a: Assignment) = activeScoreReset?.includes(a) ?: true
fun SchoolClass.countsTowardScore(e: ScoreEntry) = activeScoreReset?.includes(e) ?: true
fun SchoolClass.scoreBoundaryIndex(assignments: List<Assignment>): Int {
    val reset = activeScoreReset ?: return -1
    return assignments.indices.firstOrNull { i -> reset.includes(assignments[i]) && (assignments[i].id == reset.assignmentId || (i > 0 && !reset.includes(assignments[i - 1]))) } ?: -1
}

fun SchoolClass.startScorePeriod(assignmentId: String, keepManualSinceStart: Boolean = true, time: Long = System.currentTimeMillis()): SchoolClass {
    val ordered = assignments.sortedBy { it.createdAt }
    val index = ordered.indexOfFirst { it.id == assignmentId }
    if (index < 0) return this
    val a = ordered[index]
    val reset = ScoreReset(
        assignmentId = a.id, assignmentTitle = a.title, startAt = a.createdAt, time = time,
        excludedAssignmentIds = ordered.take(index).filter { it.createdAt == a.createdAt }.map { it.id },
        ignoredScoreIds = scores.filter { it.time >= time && (!keepManualSinceStart || it.isResetAdjustment()) }.map { it.id },
        keepManualSinceStart = keepManualSinceStart,
    )
    return copy(scoreResets = scoreResets + reset)
}

fun SchoolClass.restoreScoreHistory(time: Long = System.currentTimeMillis()) =
    copy(scoreResets = scoreResets + ScoreReset(time = time))
