package com.deepshawn.hwcounter.data

import org.junit.Assert.*
import org.junit.Test

class RechargeTest {
    private val rules = ScoreRules()

    /** s1：3 次未交（−6）+ 手动 +3 −1 → 96；s2：100 */
    private fun fixture() = SchoolClass(
        name = "一班",
        students = listOf(Student(id = "s1", no = "01", name = "张三"), Student(id = "s2", no = "02", name = "李四")),
        assignments = (1..3).map { Assignment(id = "a$it", title = "作业$it", createdAt = 1_000L * it, statuses = mapOf("s1" to HwStatus.MISSING), comments = mapOf("s1" to "补交中")) },
        scores = listOf(
            ScoreEntry(id = "e1", studentId = "s1", delta = 3.0, reason = "课堂表现", time = 1_500),
            ScoreEntry(id = "e2", studentId = "s1", delta = -1.0, reason = "迟到", time = 2_500),
        ),
    )

    private fun SchoolClass.score(id: String, r: ScoreRules = rules) = studentStats(r).first { it.student.id == id }.score

    @Test fun rechargeResetsScoreButKeepsHistory() {
        val before = fixture()
        assertEquals(96.0, before.score("s1"), 0.0)
        val c = before.recharge(rules, listOf("s1"), 100.0, time = 5_000)
        assertEquals(100.0, c.score("s1"), 0.0)
        assertEquals(100.0, c.score("s2"), 0.0)
        // 历史数据一条不少
        assertEquals(before.scores, c.scores)
        assertEquals(before.assignments, c.assignments)
        assertEquals(3, c.studentStats(rules).first { it.student.id == "s1" }.missing)
        // 记录了充值前分数
        assertEquals(96.0, c.recharges.single().before["s1"]!!, 0.0)
        assertEquals(listOf("s1"), c.recharges.single().studentIds)
    }

    @Test fun laterActivityAccumulatesOnTopOfRecharge() {
        var c = fixture().recharge(rules, listOf("s1", "s2"), 50.0, time = 5_000)
        c = c.copy(
            scores = c.scores + ScoreEntry(studentId = "s1", delta = 2.0, reason = "主动回答", time = 6_000),
            assignments = c.assignments + Assignment(id = "a4", title = "作业4", createdAt = 7_000, statuses = mapOf("s1" to HwStatus.EXCELLENT, "s2" to HwStatus.MISSING)),
        )
        assertEquals(53.0, c.score("s1"), 0.0)
        assertEquals(48.0, c.score("s2"), 0.0)
        // 改动充值之前的作业状态不影响当前积分
        val edited = c.copy(assignments = c.assignments.map { if (it.id == "a1") it.copy(statuses = mapOf("s1" to HwStatus.EXCELLENT)) else it })
        assertEquals(53.0, edited.score("s1"), 0.0)
    }

    @Test fun ruleChangesDoNotMoveTheRechargedBase() {
        val c = fixture().recharge(rules, listOf("s1"), 100.0, time = 5_000)
        assertEquals(100.0, c.score("s1", rules.copy(missing = -10.0, initial = 0.0)), 0.0)
        assertEquals(0.0, c.score("s2", rules.copy(initial = 0.0)), 0.0)
    }

    @Test fun latestRechargeWinsAndRemovingRestoresHistory() {
        var c = fixture().recharge(rules, listOf("s1"), 100.0, time = 5_000)
        c = c.copy(scores = c.scores + ScoreEntry(studentId = "s1", delta = -4.0, reason = "违纪", time = 6_000))
        c = c.recharge(rules, listOf("s1"), 80.0, time = 7_000)
        assertEquals(80.0, c.score("s1"), 0.0)
        assertEquals(96.0, c.recharges.last().before["s1"]!!, 0.0)
        val undone = c.removeRecharge(c.recharges.last().id)
        assertEquals(96.0, undone.score("s1"), 0.0)
        val all = undone.removeRecharge(undone.recharges.single().id)
        assertEquals(92.0, all.score("s1"), 0.0)
    }

    @Test fun ledgerMarksEntriesBeforeRechargeAsNotCounted() {
        val c = fixture().recharge(rules, listOf("s1"), 100.0, time = 5_000)
        val rows = c.ledger(rules, "s1")
        assertEquals(LedgerRow.Kind.RECHARGE, rows.first().kind)
        assertTrue(rows.first().counted)
        assertEquals(4.0, rows.first().delta, 0.0)
        assertTrue(rows.drop(1).none { it.counted })
        assertEquals(6, rows.size)
    }

    @Test fun legacyJsonWithoutRechargesStillLoads() {
        val json = """{"classes":[{"id":"c","name":"一班","students":[{"id":"s1","no":"1","name":"张三"}]}]}"""
        val d = AppViewModel.json.decodeFromString(AppData.serializer(), json)
        assertTrue(d.classes.single().recharges.isEmpty())
        val round = AppViewModel.json.decodeFromString(AppData.serializer(), AppViewModel.json.encodeToString(AppData.serializer(), d.copy(classes = d.classes.map { it.recharge(rules, listOf("s1"), 90.0, "期中", 1) })))
        assertEquals("期中", round.classes.single().recharges.single().note)
    }
}
