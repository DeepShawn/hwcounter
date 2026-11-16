package com.deepshawn.hwcounter.data

import java.util.Calendar
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * 关注度指数（0–100，越高越需要关注）与个人分析报告。纯计算，便于测试。
 *
 * 关注度指数 = 下面 7 项（每项 0–1）的加权和 × 100：
 * 1. 缺交风险 30%：最近 8 次（不含请假、未登记），未交记 1、迟交记 0.4，越近权重越大（每往前一次 ×0.75）
 * 2. 连续未交 15%：当前连续未交次数 ÷ 4（最多 1）
 * 3. 交齐差距 15%：最近 10 次的交齐率比全班低多少 ÷ 30 个百分点（最多 1，高于全班为 0）
 * 4. 近期下滑 10%：最近 5 次比再往前 5 次的交齐率下降多少 ÷ 40 个百分点
 * 5. 考试偏弱 15%：最近一场考试低于全班平均几个标准差 ÷ 2
 * 6. 考试退步 5%：最近一场相对位置比之前各场下降多少 ÷ 15 个百分点
 * 7. 积分靠后 10%：积分排在后一半时，越靠后越高（倒数第一为 1）
 * 没有考试成绩的同学，考试两项的权重按比例分给其他项。
 * 等级：≥60 重点关注，40–60 需要关注，20–40 留意，<20 正常。
 */
object Attention {
    data class Part(val key: String, val label: String, val weight: Double, val value: Double, val detail: String) {
        /** 这一项贡献的分数（已按权重归一化后再算） */
        var points: Double = 0.0
    }

    data class Result(val student: Student, val index: Double, val level: Level, val parts: List<Part>) {
        val top: List<Part> get() = parts.filter { it.points >= 1.0 }.sortedByDescending { it.points }
    }

    enum class Level(val label: String) { HIGH("重点关注"), MID("需要关注"), LOW("留意"), OK("正常") }

    fun levelOf(x: Double) = when { x >= 60 -> Level.HIGH; x >= 40 -> Level.MID; x >= 20 -> Level.LOW; else -> Level.OK }

    /** 只保留 t（含）之前发生的数据 */
    fun SchoolClass.asOf(t: Long): SchoolClass = copy(
        assignments = assignments.filter { it.createdAt <= t },
        scores = scores.filter { it.time <= t },
        exams = exams.filter { it.date <= t },
        recharges = recharges.filter { it.start <= t },
        settlements = settlements.filter { it.time <= t },
    )

    private fun marks(c: SchoolClass, sid: String): List<HwStatus> =
        c.assignments.sortedByDescending { it.createdAt }.map { it.statuses[sid] ?: HwStatus.NONE }.filter { it != HwStatus.LEAVE && it != HwStatus.NONE }

    private fun handed(s: HwStatus) = s == HwStatus.SUBMITTED || s == HwStatus.EXCELLENT || s == HwStatus.LATE
    private fun rateOf(l: List<HwStatus>) = if (l.isEmpty()) null else l.count { handed(it) }.toDouble() / l.size
    private fun pct(x: Double) = "${(x * 100).roundToInt()}%"

    /** 全班在时刻 [at] 的关注度（从高到低） */
    fun all(c0: SchoolClass, rules: ScoreRules, at: Long = System.currentTimeMillis()): List<Result> {
        val c = c0.asOf(at)
        if (c.students.isEmpty()) return emptyList()
        val recent = c.students.associate { it.id to marks(c, it.id) }
        val classRate10 = recent.values.flatMap { it.take(10) }.let { rateOf(it) } ?: 1.0
        val scores = c.studentStats(rules).sortedByDescending { it.score }
        val rankOf = scores.mapIndexed { i, s -> s.student.id to i }.toMap()
        val n = scores.size
        val exams = c.exams.sortedBy { it.date }
        // 每场考试的标准分
        val examZ = exams.associate { e ->
            val v = c.students.mapNotNull { s -> e.scores[s.id]?.let { s.id to it / e.fullScore } }
            val mean = if (v.isEmpty()) 0.0 else v.map { it.second }.average()
            val sd = if (v.size < 2) 0.0 else sqrt(v.sumOf { (it.second - mean) * (it.second - mean) } / v.size)
            e.id to v.associate { (id, p) -> id to if (sd <= 1e-9) 0.0 else (p - mean) / sd }
        }
        val avgPct = exams.associate { e -> e.id to (c.students.mapNotNull { s -> e.scores[s.id]?.div(e.fullScore) }.takeIf { it.isNotEmpty() }?.average() ?: 0.0) }
        return c.students.map { st ->
            val m = recent.getValue(st.id)
            val parts = ArrayList<Part>()
            // 1 缺交风险
            var num = 0.0; var den = 0.0; var w = 1.0
            for (x in m.take(8)) { num += w * (if (x == HwStatus.MISSING) 1.0 else if (x == HwStatus.LATE) 0.4 else 0.0); den += w; w *= 0.75 }
            val risk = if (den == 0.0) 0.0 else num / den
            parts += Part("risk", "缺交风险", 0.30, risk, if (m.isEmpty()) "暂无记录" else "最近 ${minOf(8, m.size)} 次：未交 ${m.take(8).count { it == HwStatus.MISSING }}、迟交 ${m.take(8).count { it == HwStatus.LATE }}")
            // 2 连续未交
            val streak = m.takeWhile { it == HwStatus.MISSING }.size
            parts += Part("streak", "连续未交", 0.15, minOf(streak, 4) / 4.0, if (streak == 0) "目前没有连续未交" else "已连续 $streak 次未交")
            // 3 交齐差距
            val r10 = rateOf(m.take(10))
            val gap = if (r10 == null) 0.0 else ((classRate10 - r10) / 0.3).coerceIn(0.0, 1.0)
            parts += Part("gap", "交齐差距", 0.15, gap, if (r10 == null) "暂无记录" else "最近交齐率 ${pct(r10)}，全班 ${pct(classRate10)}")
            // 4 近期下滑
            val a5 = rateOf(m.take(5)); val b5 = rateOf(m.drop(5).take(5))
            val drop = if (a5 == null || b5 == null || m.size < 8) 0.0 else ((b5 - a5) / 0.4).coerceIn(0.0, 1.0)
            parts += Part("decline", "近期下滑", 0.10, drop, if (a5 == null || b5 == null || m.size < 8) "记录不足 8 次" else "最近 5 次 ${pct(a5)}，之前 5 次 ${pct(b5)}")
            // 5 考试偏弱、6 考试退步
            val taken = exams.filter { st.id in it.scores }
            if (taken.isNotEmpty()) {
                val z = examZ.getValue(taken.last().id)[st.id] ?: 0.0
                parts += Part("examWeak", "考试偏弱", 0.15, (-z / 2).coerceIn(0.0, 1.0), "「${taken.last().title}」${if (z >= 0) "高于" else "低于"}全班平均 ${"%.1f".format(abs(z))} 个标准差")
                if (taken.size >= 2) {
                    fun rel(e: Exam) = e.scores.getValue(st.id) / e.fullScore - avgPct.getValue(e.id)
                    val d = rel(taken.last()) - taken.dropLast(1).map { rel(it) }.average()
                    parts += Part("examDrop", "考试退步", 0.05, (-d / 0.15).coerceIn(0.0, 1.0), "相对班级平均的位置${if (d >= 0) "提高" else "下降"} ${(abs(d) * 100).roundToInt()} 个百分点")
                }
            }
            // 7 积分靠后
            val pr = if (n <= 1) 0.0 else (rankOf.getValue(st.id).toDouble() / (n - 1))
            parts += Part("low", "积分靠后", 0.10, ((pr - 0.5) * 2).coerceIn(0.0, 1.0), "积分第 ${rankOf.getValue(st.id) + 1} 名（共 $n 人）")
            val wsum = parts.sumOf { it.weight }
            parts.forEach { it.points = it.weight / wsum * it.value * 100 }
            val idx = parts.sumOf { it.points }
            Result(st, idx, levelOf(idx), parts)
        }.sortedByDescending { it.index }
    }

    /** 每周一个点的走势（最近 [weeks] 周，含现在），学生 id → 指数列表（旧 → 新） */
    fun weekly(c: SchoolClass, rules: ScoreRules, weeks: Int = 8, now: Long = System.currentTimeMillis()): Map<String, List<Double>> {
        val pts = (weeks - 1 downTo 0).map { k -> now - k * 7L * 86_400_000L }
        val per = pts.map { t -> all(c, rules, t).associate { it.student.id to it.index } }
        return c.students.associate { s -> s.id to per.map { it[s.id] ?: 0.0 } }
    }
}

/** 个人分析报告 */
object Personal {
    data class Stat(val label: String, val mine: Double, val classAvg: Double, val better: Boolean, val text: String, val classText: String)
    data class SubjectRow(val subject: String, val count: Int, val rate: Double, val classRate: Double, val excellent: Int, val missing: Int)
    data class Period(val label: String, val count: Int, val rate: Double, val classRate: Double)
    data class ExamRow(val exam: Exam, val score: Double?, val absent: Boolean, val pct: Double?, val rank: Int?, val of: Int, val z: Double?, val classAvgPct: Double)
    data class RoundRow(val label: String, val count: Int, val rate: Double, val missing: Int, val startScore: Double, val endScore: Double)
    data class ScorePoint(val time: Long, val score: Double, val rank: Int)

    data class Report(
        val student: Student,
        val score: Double, val rank: Int, val of: Int, val percentile: Double,
        val attention: Attention.Result, val attentionTrend: List<Double>,
        val counts: Map<HwStatus, Int>, val expected: Int,
        val stats: List<Stat>,
        val handStreak: Int, val longestHand: Int, val missStreak: Int, val longestMiss: Int,
        val subjects: List<SubjectRow>, val weekdays: List<Pair<String, Double?>>,
        val months: List<Period>,
        val recentRate: Double?, val olderRate: Double?,
        val scoreTrend: List<ScorePoint>,
        val sources: Map<String, Double>,
        val reasons: List<Pair<String, Double>>,
        val exams: List<ExamRow>, val examAvgPct: Double?, val examTrend: Double, val bestSubject: Pair<String, Double>?, val worstSubject: Pair<String, Double>?,
        val rounds: List<RoundRow>,
        val settlements: List<Pair<Settlement, SettlementLine>>,
        val group: String, val groupRank: Int?, val groupSize: Int, val groupAvgRate: Double?,
        val strengths: List<String>, val concerns: List<String>, val suggestions: List<String>,
        val timeline: List<Pair<Assignment, HwStatus>>,
    )

    private fun pct(x: Double) = "${(x * 100).roundToInt()}%"
    private fun handed(s: HwStatus) = s == HwStatus.SUBMITTED || s == HwStatus.EXCELLENT || s == HwStatus.LATE

    fun report(c: SchoolClass, rules: ScoreRules, sid: String, now: Long = System.currentTimeMillis()): Report? {
        val st = c.students.firstOrNull { it.id == sid } ?: return null
        val sorted = c.assignments.sortedBy { it.createdAt }
        val statsAll = c.studentStats(rules).sortedByDescending { it.score }
        val n = statsAll.size
        val rank = statsAll.indexOfFirst { it.student.id == sid } + 1
        val score = statsAll.first { it.student.id == sid }.score
        val attention = Attention.all(c, rules, now).first { it.student.id == sid }
        val trend = Attention.weekly(c, rules, 8, now)[sid].orEmpty()

        // —— 作业 ——
        val mine = sorted.map { it to (it.statuses[sid] ?: HwStatus.NONE) }
        val counts = HwStatus.entries.associateWith { s -> mine.count { it.second == s } }
        val expected = mine.count { it.second != HwStatus.LEAVE && it.second != HwStatus.NONE }
        fun rateOf(l: List<HwStatus>) = l.filter { it != HwStatus.LEAVE && it != HwStatus.NONE }.let { v -> if (v.isEmpty()) null else v.count { handed(it) }.toDouble() / v.size }
        fun share(l: List<HwStatus>, f: (HwStatus) -> Boolean) = l.filter { it != HwStatus.LEAVE && it != HwStatus.NONE }.let { v -> if (v.isEmpty()) 0.0 else v.count(f).toDouble() / v.size }
        val myMarks = mine.map { it.second }
        val classMarks = sorted.flatMap { a -> c.students.map { a.statuses[it.id] ?: HwStatus.NONE } }
        val stats = ArrayList<Stat>()
        fun add(label: String, m: Double, cl: Double, higherBetter: Boolean) {
            stats += Stat(label, m, cl, if (higherBetter) m >= cl else m <= cl, pct(m), pct(cl))
        }
        add("交齐率", rateOf(myMarks) ?: 1.0, rateOf(classMarks) ?: 1.0, true)
        add("按时率", share(myMarks) { it == HwStatus.SUBMITTED || it == HwStatus.EXCELLENT }, share(classMarks) { it == HwStatus.SUBMITTED || it == HwStatus.EXCELLENT }, true)
        add("优秀率", share(myMarks) { it == HwStatus.EXCELLENT }, share(classMarks) { it == HwStatus.EXCELLENT }, true)
        add("迟交率", share(myMarks) { it == HwStatus.LATE }, share(classMarks) { it == HwStatus.LATE }, false)
        add("缺交率", share(myMarks) { it == HwStatus.MISSING }, share(classMarks) { it == HwStatus.MISSING }, false)

        // 连续
        val eff = myMarks.filter { it != HwStatus.LEAVE && it != HwStatus.NONE }
        var run = 0; var bestRun = 0; var miss = 0; var bestMiss = 0
        for (x in eff) { if (handed(x)) { run++; bestRun = maxOf(bestRun, run); miss = 0 } else { miss++; bestMiss = maxOf(bestMiss, miss); run = 0 } }
        val handStreak = eff.asReversed().takeWhile { handed(it) }.size
        val missStreak = eff.asReversed().takeWhile { it == HwStatus.MISSING }.size

        // 各科
        val subjects = sorted.groupBy { it.subject.ifBlank { "其他" } }.map { (sub, l) ->
            val m = l.map { it.statuses[sid] ?: HwStatus.NONE }
            val cl = l.flatMap { a -> c.students.map { a.statuses[it.id] ?: HwStatus.NONE } }
            SubjectRow(sub, l.size, rateOf(m) ?: 1.0, rateOf(cl) ?: 1.0, m.count { it == HwStatus.EXCELLENT }, m.count { it == HwStatus.MISSING })
        }.sortedBy { it.rate - it.classRate }
        // 星期
        val cal = Calendar.getInstance()
        val days = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        val byDay = Array(7) { ArrayList<HwStatus>() }
        mine.forEach { (a, s) -> cal.timeInMillis = a.createdAt; byDay[(cal.get(Calendar.DAY_OF_WEEK) + 5) % 7] += s }
        val weekdays = days.mapIndexed { i, d -> d to rateOf(byDay[i]) }
        // 按月
        val months = sorted.groupBy { cal.timeInMillis = it.createdAt; cal.get(Calendar.YEAR) * 100 + cal.get(Calendar.MONTH) + 1 }.toSortedMap().map { (k, l) ->
            val m = l.map { it.statuses[sid] ?: HwStatus.NONE }
            val cl = l.flatMap { a -> c.students.map { a.statuses[it.id] ?: HwStatus.NONE } }
            Period("${k % 100}月", l.size, rateOf(m) ?: 1.0, rateOf(cl) ?: 1.0)
        }
        val recentRate = rateOf(eff.takeLast(10)); val olderRate = if (eff.size >= 12) rateOf(eff.dropLast(10).takeLast(10)) else null

        // —— 积分 ——
        val points = (sorted.map { it.createdAt } + c.scores.filter { it.studentId == sid }.map { it.time } + c.exams.map { it.date } + c.recharges.map { it.start }).distinct().sorted()
        val sample = if (points.size <= 12) points else points.filterIndexed { i, _ -> i % ((points.size + 11) / 12) == 0 } + points.last()
        val scoreTrend = sample.distinct().map { t ->
            val s = Analytics.scoresAt(c, rules, t)
            val ordered = s.entries.sortedByDescending { it.value }
            ScorePoint(t, s[sid] ?: rules.initial, ordered.indexOfFirst { it.key == sid } + 1)
        }
        val ledger = c.ledger(rules, sid).filter { it.counted && it.kind != LedgerRow.Kind.RECHARGE }
        val sources = linkedMapOf(
            "作业" to ledger.filter { it.kind == LedgerRow.Kind.AUTO }.sumOf { it.delta },
            "手动" to ledger.filter { it.kind == LedgerRow.Kind.MANUAL }.sumOf { it.delta },
            "考试" to ledger.filter { it.kind == LedgerRow.Kind.EXAM }.sumOf { it.delta },
            "结算" to ledger.filter { it.kind == LedgerRow.Kind.SETTLE }.sumOf { it.delta },
        )
        val reasons = c.scores.filter { it.studentId == sid && it.settlementId == null }.groupBy { it.reason.ifBlank { "未注明" } }
            .map { (r, l) -> r to l.sumOf { it.delta } }.sortedByDescending { abs(it.second) }.take(8)

        // —— 考试 ——
        val exams = c.exams.sortedBy { it.date }.map { e ->
            val v = c.students.mapNotNull { s -> e.scores[s.id]?.let { it / e.fullScore } }
            val mean = if (v.isEmpty()) 0.0 else v.average()
            val sd = if (v.size < 2) 0.0 else sqrt(v.sumOf { (it - mean) * (it - mean) } / v.size)
            val p = e.scores[sid]?.div(e.fullScore)
            ExamRow(e, e.scores[sid], sid in e.absent, p, e.ranks()[sid], e.scores.size, if (p == null || sd <= 1e-9) null else (p - mean) / sd, mean)
        }
        val taken = exams.filter { it.pct != null }
        val examAvgPct = if (taken.isEmpty()) null else taken.mapNotNull { it.pct }.average()
        val examTrend = Analytics.slope(taken.mapNotNull { it.z })
        val bySub = taken.groupBy { it.exam.subject.ifBlank { "其他" } }.mapValues { (_, l) -> l.mapNotNull { it.pct }.average() }
        val best = bySub.maxByOrNull { it.value }?.toPair(); val worst = bySub.minByOrNull { it.value }?.toPair()

        // —— 各轮 ——
        val resets = c.recharges.filter { sid in it.studentIds }.sortedWith(compareBy({ it.start }, { it.time }))
        val rounds = (0..resets.size).mapNotNull { i ->
            val from = resets.getOrNull(i - 1); val to = resets.getOrNull(i)
            fun inside(t: Long) = (from == null || from.counts(t)) && (to == null || !to.counts(t))
            val l = sorted.filter { inside(it.createdAt) }.map { it.statuses[sid] ?: HwStatus.NONE }
            if (l.isEmpty()) return@mapNotNull null
            val end = to?.let { Analytics.scoresAt(c, rules, it.start - 1)[sid] } ?: score
            RoundRow(if (from == null) "首次重置前" else "第 $i 轮" + if (to == null) "（本轮）" else "", l.size, rateOf(l) ?: 1.0, l.count { it == HwStatus.MISSING }, from?.target ?: rules.initial, end)
        }
        val settlements = c.settlements.sortedBy { it.time }.mapNotNull { s -> s.lines.firstOrNull { it.studentId == sid }?.let { s to it } }

        // —— 小组 ——
        val group = st.group.ifBlank { "" }
        val groupRates = c.students.filter { it.group.isNotBlank() }.groupBy { it.group }.mapValues { (_, m) ->
            rateOf(sorted.flatMap { a -> m.map { a.statuses[it.id] ?: HwStatus.NONE } }) ?: 1.0
        }
        val groupRank = if (group.isBlank()) null else groupRates.entries.sortedByDescending { it.value }.indexOfFirst { it.key == group }.takeIf { it >= 0 }?.plus(1)

        // —— 优点、问题、建议 ——
        val strengths = ArrayList<String>(); val concerns = ArrayList<String>(); val suggestions = ArrayList<String>()
        val rate = stats[0]; val onTime = stats[1]; val exc = stats[2]
        if (rate.mine >= 0.95) strengths += "交作业很稳定，交齐率 ${rate.text}（全班 ${rate.classText}）"
        if (exc.mine > exc.classAvg + 0.05) strengths += "优秀率 ${exc.text}，高于全班 ${((exc.mine - exc.classAvg) * 100).roundToInt()} 个百分点"
        if (bestRun >= 8) strengths += "最长连续交齐 $bestRun 次"
        if (handStreak >= 5) strengths += "目前已连续交齐 $handStreak 次"
        best?.let { (s, p) -> if (taken.size >= 1 && p >= 0.85) strengths += "${s}考试平均得分率 ${pct(p)}" }
        if (recentRate != null && olderRate != null && recentRate - olderRate >= 0.15) strengths += "最近 10 次比之前提高 ${((recentRate - olderRate) * 100).roundToInt()} 个百分点"
        if (rank <= maxOf(3, n / 10)) strengths += "积分排在全班第 $rank 名"
        if (rate.mine < rate.classAvg - 0.1) concerns += "交齐率 ${rate.text}，比全班低 ${((rate.classAvg - rate.mine) * 100).roundToInt()} 个百分点"
        if (missStreak >= 2) concerns += "已连续 $missStreak 次未交"
        if (bestMiss >= 3) concerns += "最长连续未交 $bestMiss 次"
        if (onTime.mine < onTime.classAvg - 0.1) concerns += "迟交偏多，按时率 ${onTime.text}"
        subjects.firstOrNull()?.let { s -> if (s.count >= 2 && s.classRate - s.rate >= 0.15) concerns += "${s.subject}交齐率 ${pct(s.rate)}，明显低于全班 ${pct(s.classRate)}" }
        weekdays.filter { it.second != null }.minByOrNull { it.second!! }?.let { (d, r) -> if (r!! < 0.7 && byDay[days.indexOf(d)].size >= 3) concerns += "${d}的作业交得最差（${pct(r)}）" }
        if (recentRate != null && olderRate != null && olderRate - recentRate >= 0.15) concerns += "最近 10 次比之前下降 ${((olderRate - recentRate) * 100).roundToInt()} 个百分点"
        taken.lastOrNull()?.z?.let { z -> if (z <= -1) concerns += "最近一场考试低于全班平均 ${"%.1f".format(-z)} 个标准差" }
        worst?.let { (s, p) -> if (p < 0.6) concerns += "${s}考试平均得分率只有 ${pct(p)}" }
        if (missStreak >= 2) suggestions += "尽快沟通未交原因，必要时联系家长"
        if (onTime.mine < onTime.classAvg - 0.1) suggestions += "提醒作业截止时间，帮助安排时间"
        subjects.firstOrNull()?.let { s -> if (s.count >= 2 && s.classRate - s.rate >= 0.15) suggestions += "关注${s.subject}作业，了解是否有困难" }
        worst?.let { (s, p) -> if (p < 0.6) suggestions += "${s}可安排针对性辅导" }
        if (taken.size >= 2 && taken.last().pct != null && taken.last().pct!! in 0.55..0.6) suggestions += "差一点及格，稍加辅导就能过线"
        if (strengths.isNotEmpty() && concerns.isEmpty()) suggestions += "保持现状，可以适当表扬"
        if (attention.level == Attention.Level.HIGH) suggestions += "关注度很高，建议本周内重点跟进"

        return Report(
            st, score, rank, n, if (n <= 1) 1.0 else 1 - (rank - 1).toDouble() / (n - 1),
            attention, trend, counts, expected, stats,
            handStreak, bestRun, missStreak, bestMiss,
            subjects, weekdays, months, recentRate, olderRate,
            scoreTrend, sources, reasons,
            exams, examAvgPct, examTrend, best, worst,
            rounds, settlements,
            group, groupRank, c.students.count { it.group == group && group.isNotBlank() }, if (group.isBlank()) null else groupRates[group],
            strengths, concerns, suggestions,
            mine.asReversed(),
        )
    }

    /** 文字版个人报告 */
    fun text(c: SchoolClass, r: Report): String = buildString {
        val s = r.student
        appendLine("👤 ${s.name}（${s.no}）个人分析报告 · ${c.name}")
        appendLine("积分 ${r.score.fmt()}，全班第 ${r.rank}/${r.of} 名；关注度指数 ${r.attention.index.roundToInt()}（${r.attention.level.label}）")
        appendLine()
        appendLine("一、作业：应交 ${r.expected} 次，已交 ${r.counts[HwStatus.SUBMITTED]}、优秀 ${r.counts[HwStatus.EXCELLENT]}、迟交 ${r.counts[HwStatus.LATE]}、未交 ${r.counts[HwStatus.MISSING]}、请假 ${r.counts[HwStatus.LEAVE]}")
        r.stats.forEach { appendLine("· ${it.label} ${it.text}（全班 ${it.classText}）") }
        appendLine("· 目前连续交齐 ${r.handStreak} 次，最长 ${r.longestHand} 次；最长连续未交 ${r.longestMiss} 次")
        if (r.subjects.isNotEmpty()) appendLine("· 各科：" + r.subjects.joinToString("；") { "${it.subject} ${pct(it.rate)}（全班 ${pct(it.classRate)}）" })
        appendLine()
        appendLine("二、积分来源：" + r.sources.entries.joinToString("，") { "${it.key} ${it.value.signed()}" })
        if (r.reasons.isNotEmpty()) appendLine("手动加减分：" + r.reasons.joinToString("；") { "${it.first} ${it.second.signed()}" })
        if (r.exams.isNotEmpty()) {
            appendLine(); appendLine("三、考试")
            r.exams.forEach { e -> appendLine("· ${e.exam.title}：" + (if (e.absent) "缺考" else e.score?.let { "${it.fmt()}/${e.exam.fullScore.fmt()}（${pct(e.pct!!)}，第 ${e.rank}/${e.of} 名，全班平均 ${pct(e.classAvgPct)}）" } ?: "未录入")) }
        }
        appendLine(); appendLine("四、关注度构成")
        r.attention.parts.forEach { appendLine("· ${it.label} ${it.points.roundToInt()} 分：${it.detail}") }
        if (r.strengths.isNotEmpty()) { appendLine(); appendLine("优点：" + r.strengths.joinToString("；")) }
        if (r.concerns.isNotEmpty()) appendLine("需要注意：" + r.concerns.joinToString("；"))
        if (r.suggestions.isNotEmpty()) appendLine("建议：" + r.suggestions.joinToString("；"))
    }.trimEnd()
}
