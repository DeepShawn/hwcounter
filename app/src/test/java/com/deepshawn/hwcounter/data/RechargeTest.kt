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
        // 记录了重置前分数
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
        // 改动重置之前的作业状态不影响当前积分
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

    @Test fun rechargeFromAnAssignmentCountsThatAssignmentOnward() {
        val base = fixture() // a1@1000 a2@2000 a3@3000 未交；e1@1500 +3, e2@2500 −1
        val a2 = base.assignments.first { it.id == "a2" }
        val c = base.recharge(rules, listOf("s1"), 100.0, time = 9_000, assignment = a2)
        // 计入 a2、a3（各 −2）和 e2（−1）：100 − 4 − 1 = 95
        assertEquals(95.0, c.score("s1"), 0.0)
        val r = c.recharges.single()
        assertEquals(2_000L, r.effectiveAt); assertEquals("a2", r.assignmentId); assertEquals("作业2", r.assignmentTitle)
        assertEquals(96.0, r.before["s1"]!!, 0.0)
        val rows = c.ledger(rules, "s1")
        assertTrue(rows.first { it.assignmentId == "a2" }.counted)
        assertFalse(rows.first { it.assignmentId == "a1" }.counted)
        assertFalse(rows.first { it.entryId == "e1" }.counted)
        assertTrue(rows.first { it.entryId == "e2" }.counted)
        assertTrue(rows.first { it.kind == LedgerRow.Kind.RECHARGE }.title.contains("从「作业2」起"))
    }

    @Test fun rechargeFromADayAndOrderingByEffectiveStart() {
        val base = fixture()
        // 从 2500 起：计入 e2（−1）、a3（−2）
        val byDay = base.recharge(rules, listOf("s1"), 100.0, time = 9_000, effectiveAt = 2_500)
        assertEquals(97.0, byDay.score("s1"), 0.0)
        // 之后再补一条更早生效的重置：生效更晚的那次仍然生效
        val both = byDay.recharge(rules, listOf("s1"), 50.0, time = 10_000, effectiveAt = 500)
        assertEquals(97.0, both.score("s1"), 0.0)
        assertEquals(50.0 - 6 + 2, both.removeRecharge(byDay.recharges.single().id).score("s1"), 0.0)
        assertEquals(startOfDay(startOfDay(1_700_000_000_000L) + 5), startOfDay(1_700_000_000_000L))
    }
}
