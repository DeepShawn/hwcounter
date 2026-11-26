package com.deepshawn.hwcounter.data

import java.util.Calendar
import kotlin.math.abs

/**
 * 小组分析：小组对抗（每次作业 / 每周比交齐率）、进步与退步、组内分析、组长奖惩汇总。
 * 只看已命名、2 人以上的小组；交齐率 =（已交 + 优秀 + 迟交）÷ 应交（请假、未登记不算）。
 */
object GroupAnalysis {
    data class Team(
        val name: String, val leader: Student?, val members: List<Student>,
        val expected: Int, val handed: Int, val onTime: Int, val excellent: Int, val missing: Int,
        val avgScore: Double, val examPct: Double?,
        /** 全组交齐的作业次数 / 参加的作业次数；当前连续全组交齐、最长连续 */
        val complete: Int, val played: Int, val completeStreak: Int, val longestComplete: Int,
        /** 单次作业对抗：第一（不并列）次数、并列第一次数、平均名次 */
        val wins: Int, val ties: Int, val avgRank: Double,
        /** 每周对抗胜出周数 */
        val weekWins: Int,
        /** 组长奖惩合计（计入当前积分的部分）与段数 */
        val leaderPoints: Double, val leaderPeriods: Int,
        /** 作业人均扣分（全部作业） */
        val deductPerCapita: Double,
        /** 综合分（0–1，口径同小组排名）与综合名次；小组排名加减分合计（计入当前积分、按每人算） */
        val score: Double = 0.0, val rank: Int = 0, val rankPoints: Double = 0.0,
    ) {
        val rate: Double get() = if (expected == 0) 1.0 else handed.toDouble() / expected
        val onTimeRate: Double get() = if (expected == 0) 1.0 else onTime.toDouble() / expected
        val excellentRate: Double get() = if (expected == 0) 0.0 else excellent.toDouble() / expected
    }

    data class Progress(val group: String, val beforeRate: Double, val afterRate: Double, val beforeExam: Double?, val afterExam: Double?, val beforeDeduct: Double, val afterDeduct: Double, val index: Double, val reasons: List<String>)
    data class Week(val label: String, val start: Long, val rates: Map<String, Double>, val winners: List<String>)
    data class Duel(val a: String, val b: String, val aWins: Int, val bWins: Int, val draws: Int, val recent: List<Triple<Assignment, Double?, Double?>>)
    data class Report(val teams: List<Team>, val progressBasis: String?, val progress: List<Progress>, val weeks: List<Week>, val periods: List<RankPeriod> = emptyList())

    private val HANDED = setOf(HwStatus.SUBMITTED, HwStatus.EXCELLENT, HwStatus.LATE)

    fun groupsOf(c: SchoolClass): Map<String, List<Student>> =
        c.students.filter { it.group.isNotBlank() }.groupBy { it.group }.filterValues { it.size >= 2 }.toSortedMap(compareBy<String> { it.length }.thenBy { it })

    /** 某组某次作业的交齐率；全组都请假 / 未登记时为 null */
    fun rateOf(ms: List<Student>, a: Assignment): Double? {
        val st = ms.map { a.statuses[it.id] ?: HwStatus.NONE }.filter { it != HwStatus.NONE && it != HwStatus.LEAVE }
        return if (st.isEmpty()) null else st.count { it in HANDED }.toDouble() / st.size
    }

    private fun weekStart(t: Long): Long = Calendar.getInstance().apply {
        timeInMillis = t; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_YEAR, -((get(Calendar.DAY_OF_WEEK) + 5) % 7))
    }.timeInMillis

    private fun pct(x: Double) = "${Math.round(x * 100)}%"

    fun report(c: SchoolClass, rules: ScoreRules, weeksBack: Int = 8): Report {
        val groups = groupsOf(c)
        if (groups.isEmpty()) return Report(emptyList(), null, emptyList(), emptyList())
        val sorted = c.assignments.sortedBy { it.createdAt }
        val scores = c.studentStats(rules).associate { it.student.id to it.score }
        val last = c.latestRecharges()
        val lead = c.leaderPeriods(rules)
        val auto = groups.values.flatten().associate { it.id to c.autoPoints(rules, it.id) }
        // 单次作业对抗
        val wins = HashMap<String, Int>(); val ties = HashMap<String, Int>(); val rankSum = HashMap<String, Double>(); val rankN = HashMap<String, Int>()
        for (a in sorted) {
            val rs = groups.mapNotNull { (g, ms) -> rateOf(ms, a)?.let { g to it } }
            if (rs.size < 2) continue
            val best = rs.maxOf { it.second }
            val top = rs.filter { abs(it.second - best) < 1e-9 }
            if (top.size == 1) wins[top[0].first] = (wins[top[0].first] ?: 0) + 1 else top.forEach { ties[it.first] = (ties[it.first] ?: 0) + 1 }
            for ((g, r) in rs) { rankSum[g] = (rankSum[g] ?: 0.0) + 1 + rs.count { it.second > r + 1e-9 }; rankN[g] = (rankN[g] ?: 0) + 1 }
        }
        // 每周对抗（最近 weeksBack 周有作业的周）
        val byWeek = sorted.groupBy { weekStart(it.createdAt) }.toSortedMap()
        val weeks = byWeek.entries.toList().takeLast(weeksBack).map { (start, list) ->
            val rates = groups.mapNotNull { (g, ms) ->
                val st = list.flatMap { a -> ms.map { a.statuses[it.id] ?: HwStatus.NONE } }.filter { it != HwStatus.NONE && it != HwStatus.LEAVE }
                if (st.isEmpty()) null else g to st.count { it in HANDED }.toDouble() / st.size
            }.toMap()
            val best = rates.values.maxOrNull()
            val winners = if (best == null || rates.size < 2) emptyList() else rates.filterValues { abs(it - best) < 1e-9 }.keys.toList()
            val cal = Calendar.getInstance().apply { timeInMillis = start }
            Week("${cal.get(Calendar.MONTH) + 1}/${cal.get(Calendar.DAY_OF_MONTH)} 这周", start, rates, if (winners.size == rates.size) emptyList() else winners)
        }
        val allWeeks = byWeek.entries.map { (_, list) ->
            groups.mapNotNull { (g, ms) ->
                val st = list.flatMap { a -> ms.map { a.statuses[it.id] ?: HwStatus.NONE } }.filter { it != HwStatus.NONE && it != HwStatus.LEAVE }
                if (st.isEmpty()) null else g to st.count { it in HANDED }.toDouble() / st.size
            }.toMap()
        }
        val weekWins = HashMap<String, Int>()
        for (rates in allWeeks) {
            val best = rates.values.maxOrNull() ?: continue
            val w = rates.filterValues { abs(it - best) < 1e-9 }.keys
            if (rates.size >= 2 && w.size == 1) weekWins[w.first()] = (weekWins[w.first()] ?: 0) + 1
        }
        val teams = groups.map { (g, ms) ->
            var expected = 0; var handed = 0; var onTime = 0; var excellent = 0; var missing = 0
            var complete = 0; var played = 0; var cur = 0; var longest = 0
            for (a in sorted) {
                val st = ms.map { a.statuses[it.id] ?: HwStatus.NONE }.filter { it != HwStatus.NONE && it != HwStatus.LEAVE }
                if (st.isEmpty()) continue
                played++
                expected += st.size; handed += st.count { it in HANDED }; onTime += st.count { it == HwStatus.SUBMITTED || it == HwStatus.EXCELLENT }
                excellent += st.count { it == HwStatus.EXCELLENT }; missing += st.count { it == HwStatus.MISSING }
                if (st.all { it in HANDED }) { complete++; cur++; longest = maxOf(longest, cur) } else cur = 0
            }
            val ex = c.analysisExams.flatMap { e -> ms.mapNotNull { m -> e.scores[m.id]?.let { it / e.fullScore } } }
            val leader = c.leaderOf(g)
            val lps = leader?.let { l -> lead[l.id].orEmpty().filter { p -> last[l.id]?.counts(p.time) ?: true } }.orEmpty()
            val deduct = ms.sumOf { m -> auto.getValue(m.id).values.filter { it < 0 }.sumOf { -it } } / ms.size
            Team(
                g, leader, ms, expected, handed, onTime, excellent, missing,
                ms.map { scores[it.id] ?: rules.initial }.average(), if (ex.isEmpty()) null else ex.average(),
                complete, played, cur, longest, wins[g] ?: 0, ties[g] ?: 0, rankN[g]?.let { (rankSum[g] ?: 0.0) / it } ?: 0.0,
                weekWins[g] ?: 0, lps.sumOf { it.points }, lps.size, deduct,
            )
        }
        // 综合分（全部作业，口径同小组排名）
        val gr = rules.groupRank
        val maxD = teams.maxOfOrNull { it.deductPerCapita } ?: 0.0
        val allExam = teams.all { it.examPct != null }
        val scored = teams.map { t ->
            val dn = if (maxD <= 1e-9) 1.0 else 1 - t.deductPerCapita / maxD
            var num = gr.wRate * t.rate + gr.wDeduct * dn + gr.wExcellent * t.excellentRate
            var den = gr.wRate + gr.wDeduct + gr.wExcellent
            if (allExam) { num += gr.wExam * t.examPct!!; den += gr.wExam }
            t.copy(score = Math.round((if (den <= 0) 0.0 else num / den) * 10000) / 10000.0)
        }
        val periods = c.groupRankPeriods(rules)
        val rankTotal = HashMap<String, Double>()
        for (p in periods) for (st in p.standings) {
            val member = groups[st.group]?.firstOrNull() ?: continue
            if (last[member.id]?.counts(p.time) != false) rankTotal[st.group] = (rankTotal[st.group] ?: 0.0) + st.points
        }
        val ranked = scored.map { t -> t.copy(rank = 1 + scored.count { it.score > t.score }, rankPoints = rankTotal[t.name] ?: 0.0) }
            .sortedWith(compareBy<Team> { it.rank }.thenByDescending { it.wins + it.ties * 0.5 }.thenBy { it.name })
        val (basis, prog) = progress(c, rules, groups, sorted, auto)
        return Report(ranked, basis, prog, weeks, periods)
    }

    /** 小组进步 / 退步：与全班进步之星同样的分段（最近一次全班重置前后；没有时前半段 vs 后半段） */
    private fun progress(c: SchoolClass, rules: ScoreRules, groups: Map<String, List<Student>>, sorted: List<Assignment>, auto: Map<String, Map<String, Double>>): Pair<String?, List<Progress>> {
        if (sorted.size < 4) return null to emptyList()
        val resets = Analytics.classResets(c)
        val r = resets.asReversed().firstOrNull { rr -> sorted.count { rr.counts(it.createdAt) } >= 2 && sorted.count { !rr.counts(it.createdAt) } >= 2 }
        val (before, after, basis) = if (r != null) Triple(sorted.filter { !r.counts(it.createdAt) }, sorted.filter { r.counts(it.createdAt) }, "重置前 vs 重置后")
        else { val h = sorted.size / 2; Triple(sorted.take(h), sorted.drop(h), "前 $h 次 vs 后 ${sorted.size - h} 次") }
        val cut = after.first().createdAt
        fun side(ms: List<Student>, list: List<Assignment>, exams: List<Exam>): Triple<Double?, Double?, Double> {
            val st = list.flatMap { a -> ms.map { a.statuses[it.id] ?: HwStatus.NONE } }.filter { it != HwStatus.NONE && it != HwStatus.LEAVE }
            val rate = if (st.isEmpty()) null else st.count { it in HANDED }.toDouble() / st.size
            val ex = exams.flatMap { e -> ms.mapNotNull { m -> e.scores[m.id]?.let { it / e.fullScore } } }
            val ids = list.map { it.id }.toSet()
            val d = ms.sumOf { m -> auto.getValue(m.id).filterKeys { it in ids }.values.filter { it < 0 }.sumOf { -it } } / ms.size / list.size.coerceAtLeast(1)
            return Triple(rate, if (ex.isEmpty()) null else ex.average(), d)
        }
        val out = groups.mapNotNull { (g, ms) ->
            val (br, bx, bd) = side(ms, before, c.analysisExams.filter { it.date < cut })
            val (ar, ax, ad) = side(ms, after, c.analysisExams.filter { it.date >= cut })
            if (br == null || ar == null) return@mapNotNull null
            val dr = ar - br
            val dx = if (ax != null && bx != null) ax - bx else null
            // 每次作业人均扣分的变化（扣分少了是进步），按 2 分折算成 10 个百分点
            val dd = ((bd - ad) / 2.0 * 0.1).coerceIn(-0.3, 0.3)
            val idx = if (dx != null) dr * 0.5 + dd * 0.3 + dx * 0.2 else (dr * 0.5 + dd * 0.3) / 0.8
            val why = ArrayList<String>()
            if (abs(dr) >= 0.02) why += "交齐率 ${pct(br)} → ${pct(ar)}"
            if (abs(bd - ad) >= 0.1) why += "每次人均扣分 ${bd.fmt()} → ${ad.fmt()}"
            if (dx != null && abs(dx) >= 0.02) why += "考试得分率 ${pct(bx!!)} → ${pct(ax!!)}"
            Progress(g, br, ar, bx, ax, bd, ad, idx, why)
        }.sortedByDescending { it.index }
        return basis to out
    }

    /** 两组对决：每次作业比交齐率 */
    fun duel(c: SchoolClass, a: String, b: String): Duel {
        val groups = groupsOf(c)
        val ma = groups[a].orEmpty(); val mb = groups[b].orEmpty()
        var aw = 0; var bw = 0; var dr = 0
        val rows = ArrayList<Triple<Assignment, Double?, Double?>>()
        for (x in c.assignments.sortedBy { it.createdAt }) {
            val ra = rateOf(ma, x); val rb = rateOf(mb, x)
            if (ra == null && rb == null) continue
            rows += Triple(x, ra, rb)
            if (ra == null || rb == null) continue
            when { ra > rb + 1e-9 -> aw++; rb > ra + 1e-9 -> bw++; else -> dr++ }
        }
        return Duel(a, b, aw, bw, dr, rows.takeLast(10).reversed())
    }

    /** 组内进步 / 退步的同学（来自全班进步分析） */
    fun memberProgress(c: SchoolClass, rules: ScoreRules, group: String): Pair<List<Analytics.StudentProgress>, List<Analytics.StudentProgress>> {
        val p = Analytics.progress(c, rules) ?: return emptyList<Analytics.StudentProgress>() to emptyList()
        val mine = p.all.filter { it.student.group == group }
        return mine.filter { it.index >= 0.03 }.sortedByDescending { it.index } to mine.filter { it.index <= -0.03 }.sortedBy { it.index }
    }
}
