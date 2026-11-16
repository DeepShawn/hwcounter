package com.deepshawn.hwcounter.perf

import com.deepshawn.hwcounter.data.*
import org.junit.Test
import kotlin.random.Random

/** 粗测：53 人、6 组、160 次作业、500 条加减分、8 场考试时各项计算耗时（只打印，不判失败） */
class PerfTest {
    private fun big(): SchoolClass {
        val r = Random(3); val day = 86_400_000L; val now = 400 * day
        val groups = listOf("语文组", "数学组", "英语组", "物理组", "化学组", "生物组")
        val st = (1..53).map { Student(id = "s$it", no = "$it", name = "学生$it", group = groups[it % 6]) }
        val statuses = listOf(HwStatus.SUBMITTED, HwStatus.SUBMITTED, HwStatus.SUBMITTED, HwStatus.EXCELLENT, HwStatus.LATE, HwStatus.MISSING, HwStatus.LEAVE)
        val asg = (0 until 160).map { k -> Assignment(id = "a$k", title = "作业$k", subject = groups[k % 6], createdAt = now - (160 - k) * day / 2, statuses = st.associate { it.id to statuses[r.nextInt(statuses.size)] }) }
        val sc = (0 until 500).map { ScoreEntry(studentId = st[r.nextInt(53)].id, delta = (r.nextInt(5) - 2).toDouble(), reason = "表现", time = now - r.nextLong(80) * day) }
        val ex = (0 until 8).map { k -> Exam(id = "e$k", title = "考试$k", subject = "化学", date = now - k * 9 * day, scores = st.associate { it.id to (40 + r.nextInt(60)).toDouble() }) }
        return SchoolClass(name = "高二(2)班", students = st, assignments = asg, scores = sc, exams = ex, groupLeaders = groups.mapIndexed { i, g -> g to "s${i + 1 + (6 - (i + 1) % 6) % 6}" }.toMap().mapValues { (g, _) -> st.first { it.group == g }.id })
    }
    private fun t(label: String, n: Int = 3, f: () -> Unit) {
        f()
        val t0 = System.nanoTime(); repeat(n) { f() }
        println("PERF $label: ${"%.1f".format((System.nanoTime() - t0) / 1e6 / n)} ms")
    }
    @Test fun measure() {
        val c = big(); val rules = ScoreRules()
        t("studentStats") { c.studentStats(rules) }
        t("leaderPeriods") { c.leaderPeriods(rules) }
        t("groupRankPeriods") { c.groupRankPeriods(rules) }
        t("autoPoints×53") { c.students.forEach { c.autoPoints(rules, it.id) } }
        t("Analytics.report ALL") { Analytics.report(c, rules, Analytics.Spec(Analytics.Range.ALL)) }
        t("Attention.all") { Attention.all(c, rules) }
        t("GroupAnalysis.report") { GroupAnalysis.report(c, rules) }
        t("ledger one student") { c.ledger(rules, "s1") }
    }
}
