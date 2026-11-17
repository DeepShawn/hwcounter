package com.deepshawn.hwcounter.data

import org.junit.Assert.*
import org.junit.Test

/** 抄袭：抄袭者和提供者都记未交、扣分翻倍，累犯逐次加重；关系图（三端同一组用例） */
class PlagiarismTest {
    private val S = HwStatus.SUBMITTED; private val M = HwStatus.MISSING
    private val day = 86_400_000L

    /** 甲提供、乙丙抄袭、丁无关：a0 乙抄甲；a2 乙丙都抄甲；a3 乙抄甲但乙已改为已交（不算） */
    private fun cls(): SchoolClass {
        val st = listOf("a" to "甲", "b" to "乙", "c" to "丙", "d" to "丁").mapIndexed { i, (id, n) -> Student(id = id, no = "${i + 1}", name = n) }
        fun A(k: Int, s: Map<String, HwStatus>, p: Map<String, List<String>>) = Assignment(id = "a$k", title = "作业$k", createdAt = (k + 1) * day, statuses = s, plagiarism = p)
        return SchoolClass(
            name = "一班", students = st,
            assignments = listOf(
                A(0, mapOf("a" to M, "b" to M, "c" to S, "d" to S), mapOf("b" to listOf("a"))),
                A(1, mapOf("a" to S, "b" to S, "c" to S, "d" to S), emptyMap()),
                A(2, mapOf("a" to M, "b" to M, "c" to M, "d" to S), mapOf("b" to listOf("a"), "c" to listOf("a"))),
                A(3, mapOf("a" to M, "b" to S, "c" to S, "d" to S), mapOf("b" to listOf("a"))),
            ),
        )
    }

    private fun pts(c: SchoolClass, rules: ScoreRules, id: String) = c.autoPointDetails(rules, id).mapValues { it.value.points }

    @Test fun bothSidesDoubledAndRepeatEscalates() {
        val c = cls(); val r = ScoreRules()
        // 甲：a0 第 1 次 2×2=4；a2 第 2 次 2×2×2=8；a3 乙已交 → 普通连续未交（第 2 次连续）4
        assertEquals(mapOf("a0" to -4.0, "a1" to 0.0, "a2" to -8.0, "a3" to -4.0), pts(c, r, "a"))
        assertEquals(mapOf("a0" to -4.0, "a1" to 0.0, "a2" to -8.0, "a3" to 0.0), pts(c, r, "b"))
        assertEquals(-4.0, pts(c, r, "c").getValue("a2"), 1e-9)
        assertEquals(0.0, pts(c, r, "d").values.sum(), 1e-9)
        val d = c.autoPointDetails(r, "a").getValue("a2")
        assertEquals(2, d.plagiarism); assertEquals(4.0, d.plagiarismFactor, 1e-9)
        assertFalse(c.assignments[3].plagiarized("a"))
        // 抄袭算一次未交
        assertEquals(3, c.studentStats(r).first { it.student.id == "a" }.missing)
    }

    @Test fun resetDisabledAndCap() {
        val c = cls()
        // 全班在 a2 前重置：累犯次数重新算
        val reset = c.recharge(ScoreRules(), c.students.map { it.id }, 100.0, time = 10 * day, assignment = c.assignments[2])
        assertEquals(-4.0, pts(reset, ScoreRules(), "a").getValue("a2"), 1e-9)
        // 关闭：按普通未交
        assertEquals(-2.0, pts(c, ScoreRules(plagiarism = PlagiarismRules(enabled = false)), "b").getValue("a0"), 1e-9)
        // 不加重：每次 ×2
        assertEquals(-4.0, pts(c, ScoreRules(plagiarism = PlagiarismRules(escalate = false)), "b").getValue("a2"), 1e-9)
        // 最多 ×maxTimes：倍数 2、4、6、8、8
        val p = PlagiarismRules()
        assertEquals(listOf(2.0, 4.0, 6.0, 8.0, 8.0), (1..5).map { p.factor(it) })
    }

    @Test fun graphLedgerAndClear() {
        val c = cls(); val r = ScoreRules()
        val g = PlagiarismGraph.report(c, r)
        assertEquals(3, g.cases.size)
        assertEquals(listOf("a→b" to 2, "a→c" to 1), g.edges.map { "${it.provider.id}→${it.receiver.id}" to it.count })
        val a = g.nodes.first { it.student.id == "a" }
        assertEquals(0, a.copied); assertEquals(2, a.provided); assertEquals(-12.0, a.points, 1e-9); assertEquals("提供者", a.role)
        val b = g.nodes.first { it.student.id == "b" }
        assertEquals(2, b.copied); assertEquals(-12.0, b.points, 1e-9)
        assertEquals(1, g.clusters.size); assertEquals(listOf("a", "b", "c"), g.clusters[0].map { it.id })
        assertEquals(2, g.copiers); assertEquals(1, g.providers)
        val row = c.ledger(r, "b").first { it.assignmentId == "a0" }
        assertTrue(row.title, row.title.contains("抄袭 甲") && row.title.contains("×2"))
        assertTrue(c.ledger(r, "a").first { it.assignmentId == "a2" }.title.contains("提供给 乙、丙 抄"))
        // 去掉标记
        val cleared = c.assignments[2].withoutPlagiarism(listOf("a"))
        assertTrue(cleared.plagiarism.isEmpty())
    }

    @Test fun graphPerAssignment() {
        val c = cls(); val r = ScoreRules()
        // 只有 a0、a2 有有效的抄袭记录，最新的在前
        assertEquals(listOf("a2", "a0"), PlagiarismGraph.assignments(c).map { it.id })
        val g0 = PlagiarismGraph.report(c, r, "a0")
        assertEquals(listOf("a→b" to 1), g0.edges.map { "${it.provider.id}→${it.receiver.id}" to it.count })
        assertEquals(setOf("a", "b"), g0.nodes.map { it.student.id }.toSet())
        val a0 = g0.nodes.first { it.student.id == "a" }
        assertEquals(-4.0, a0.points, 1e-9); assertEquals(1, a0.roundTimes); assertEquals(2.0, a0.factor, 1e-9)
        val g2 = PlagiarismGraph.report(c, r, "a2")
        assertEquals(listOf("a→b" to 1, "a→c" to 1), g2.edges.map { "${it.provider.id}→${it.receiver.id}" to it.count })
        val a2 = g2.nodes.first { it.student.id == "a" }
        assertEquals(-8.0, a2.points, 1e-9); assertEquals(2, a2.roundTimes); assertEquals(4.0, a2.factor, 1e-9)
        assertEquals(2, g2.copiers); assertEquals(1, g2.providers)
        // 没有抄袭的作业：空图
        assertTrue(PlagiarismGraph.report(c, r, "a3").cases.isEmpty())
    }

    @Test fun reviewAndPlagiarismStack() {
        // 乙在 a0 既抄袭又复审不合格：2 × 抄袭 ×2 × 复审 ×2 = 8
        val c = cls().let { c -> c.copy(assignments = c.assignments.map { if (it.id == "a0") it.copy(reviewFailed = listOf("b")) else it }) }
        val r = ScoreRules()
        val d = c.autoPointDetails(r, "b").getValue("a0")
        assertEquals(-8.0, d.points, 1e-9); assertTrue(d.reviewFailed); assertEquals(1, d.plagiarism)
        // 甲（只抄袭）不受影响
        assertEquals(-4.0, c.autoPointDetails(r, "a").getValue("a0").points, 1e-9)
        // 关闭复审：只按抄袭
        assertEquals(-4.0, c.autoPointDetails(r.copy(review = r.review.copy(enabled = false)), "b").getValue("a0").points, 1e-9)
        val row = c.ledger(r, "b").first { it.assignmentId == "a0" }
        assertTrue(row.title, row.title.contains("复审不合格再 ×2") && row.title.contains("合计 ×4"))
        assertEquals("本次 -8（抄袭第 1 次 ×2 × 复审不合格 ×2 = ×4）", c.assignmentPointNote(r, "a0", "b"))
        assertEquals("本次 -4（抄袭第 1 次 ×2）", c.assignmentPointNote(r, "a0", "a"))
    }
}
