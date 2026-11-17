package com.deepshawn.hwcounter.data

import org.junit.Assert.*
import org.junit.Test

/** 信誉分：与网页 credit.test.mjs、iPad Tests/main.swift 同一组用例 */
class CreditTest {
    private val S = HwStatus.SUBMITTED; private val E = HwStatus.EXCELLENT; private val T = HwStatus.LATE; private val M = HwStatus.MISSING

    private fun cls(vararg rows: Pair<String, List<HwStatus>>, groups: Map<String, String> = emptyMap(), exams: List<Exam> = emptyList(), review: Map<Int, List<String>> = emptyMap()): SchoolClass {
        val n = rows.maxOf { it.second.size }
        val students = rows.mapIndexed { i, (id, _) -> Student(id = id, no = "0${i + 1}", name = "同学$id", group = groups[id] ?: "") }
        val asg = (0 until n).map { k -> Assignment(id = "a$k", title = "作业$k", createdAt = (k + 1) * 1000L, statuses = rows.associate { (id, l) -> id to (l.getOrNull(k) ?: HwStatus.NONE) }, reviewFailed = review[k].orEmpty()) }
        return SchoolClass(name = "一班", students = students, assignments = asg, exams = exams)
    }

    @Test fun personalIndex() {
        val c = cls("s1" to listOf(S, S, E, T, M))
        val r = Credit.all(c).single()
        assertEquals(68.43, r.index, 1e-9)
        assertEquals(Credit.Level.C, r.level)
        assertEquals(listOf("rate", "ontime", "excellent", "streak", "integrity", "trend"), r.parts.map { it.key })
        // 加一场考试 90/100：权重补齐到 100%
        val withExam = c.copy(exams = listOf(Exam(id = "e1", title = "月考", subject = "数学", date = 6000L, fullScore = 100.0, scores = mapOf("s1" to 90.0))))
        assertEquals(71.67, Credit.all(withExam).single().index, 1e-9)
        // 没有记录：没有信誉分
        assertFalse(Credit.all(cls("s1" to listOf(HwStatus.LEAVE))).single().hasData)
    }

    @Test fun integrityLowersCredit() {
        val ok = Credit.all(cls("s1" to listOf(S, S, M))).single().index
        val failed = Credit.all(cls("s1" to listOf(S, S, M), review = mapOf(2 to listOf("s1")))).single()
        assertTrue(failed.index < ok)
        assertEquals(0.5, failed.parts.first { it.key == "integrity" }.value, 1e-9)
    }

    @Test fun personalPointsEachPeriod() {
        val c = cls("s1" to listOf(E, E, E, E, E))
        val rules = ScoreRules(credit = CreditRules(personal = "both", period = "assignment"))
        val ps = c.creditPeriods(rules)
        assertEquals(listOf(87.65, 90.0, 92.35, 94.71, 97.06), ps.map { it.personal.getValue("s1").first })
        assertEquals(listOf(1.0, 2.0, 2.0, 2.0, 2.0), ps.map { it.personal.getValue("s1").second })
        assertEquals(100.0 + 5 + 9, c.studentStats(rules).single().score, 1e-9)
        // 默认不计入
        assertEquals(105.0, c.studentStats(ScoreRules()).single().score, 1e-9)
        assertTrue(c.ledger(rules, "s1").any { it.title.startsWith("信誉分") && it.delta == 2.0 })
    }

    @Test fun modesPlusMinusNone() {
        val c = cls("s1" to listOf(M, M, M))
        fun score(m: String) = c.studentStats(ScoreRules(credit = CreditRules(personal = m, period = "assignment"))).single().score
        assertEquals(26.47, c.creditPeriods(ScoreRules(credit = CreditRules(personal = "both", period = "assignment"))).first().personal.getValue("s1").first, 1e-9)
        assertEquals(100.0 - 8 - 6, score("both"), 1e-9)
        assertEquals(100.0 - 8 - 6, score("minus"), 1e-9)
        assertEquals(100.0 - 8, score("plus"), 1e-9)
        assertEquals(100.0 - 8, score("none"), 1e-9)
    }

    @Test fun groupPoints() {
        val c = cls("s1" to listOf(E), "s2" to listOf(M), groups = mapOf("s1" to "A", "s2" to "A"))
        val rules = ScoreRules(credit = CreditRules(group = "both", period = "assignment"), groupRank = GroupRankRules(enabled = false), leader = LeaderRules(enabled = false))
        val p = c.creditPeriods(rules).single()
        assertEquals(57.06, p.groups.getValue("A").first, 1e-9)
        assertEquals(-1.0, p.groups.getValue("A").second, 1e-9)
        assertTrue(p.personal.isEmpty())
        val sc = c.studentStats(rules).associate { it.student.id to it.score }
        assertEquals(100.0 + 1 - 1, sc.getValue("s1"), 1e-9)
        assertEquals(100.0 - 2 - 1, sc.getValue("s2"), 1e-9)
        assertEquals(mapOf("A" to 57.06), Credit.groups(c))
    }

    @Test fun boardsAndSummary() {
        val c = cls("s1" to listOf(E, E), "s2" to listOf(S, M), groups = mapOf("s1" to "A", "s2" to "A"))
        val b = MetricStock.personalCredit(c, AttentionStock.Span.ALL, now = 10_000L)
        assertEquals(setOf("s1", "s2"), b.stocks.keys)
        assertNotNull(MetricStock.groupCredit(c, AttentionStock.Span.ALL, now = 10_000L))
        assertNotNull(MetricStock.groupAttention(c, ScoreRules(), AttentionStock.Span.ALL, now = 10_000L))
        val sum = Credit.classSummary(c, ScoreRules(), now = 10_000L)
        assertEquals(2, sum.levels.sumOf { it.second })
        assertEquals(listOf("A"), sum.groups.map { it.first })
    }
}
