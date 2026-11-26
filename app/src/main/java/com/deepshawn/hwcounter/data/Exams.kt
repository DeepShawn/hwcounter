package com.deepshawn.hwcounter.data

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.round
import kotlin.math.sqrt

/**
 * 考试：与平时作业分开记录、分开统计。三端（Android / iPad / 网页）同一格式。
 *
 * @param scores 学生id → 分数；不在表里且不在 [absent] 里 = 未录入
 * @param absent 缺考的学生
 * @param autoScore 这次考试是否按“考试计分档”自动加减积分
 */
@Serializable
data class Exam(
    val id: String = newId(),
    val title: String,
    val subject: String = "",
    val date: Long = System.currentTimeMillis(),
    val fullScore: Double = 100.0,
    val scores: Map<String, Double> = emptyMap(),
    val absent: List<String> = emptyList(),
    val note: String = "",
    val autoScore: Boolean = true,
    /** 不计入分析：这场考试不参加考试分析、关注度、信誉分、小组对比、个人分析、股票行情等统计（成绩照常显示，本场的加减积分仍按 [autoScore]） */
    val skipAnalysis: Boolean = false,
)

/** 参加分析统计的考试（去掉“不计入分析”的） */
val SchoolClass.analysisExams: List<Exam> get() = if (exams.none { it.skipAnalysis }) exams else exams.filter { !it.skipAnalysis }

/** 分数档：得分率在 [minPct, maxPct) 之间（最高档含 100%）加 delta 分 */
@Serializable
data class ExamBand(val minPct: Double, val maxPct: Double, val delta: Double)

@Serializable
data class ExamRules(
    val enabled: Boolean = true,
    val bands: List<ExamBand> = listOf(
        ExamBand(90.0, 100.0, 2.0),
        ExamBand(80.0, 90.0, 1.0),
        ExamBand(0.0, 60.0, -1.0),
    ),
    /** 缺考加减分（默认 0） */
    val absent: Double = 0.0,
) {
    /** 某个分数应加减的积分 */
    fun deltaFor(score: Double, full: Double): Double {
        if (!enabled || full <= 0 || !score.isFinite()) return 0.0
        val pct = score / full * 100.0
        val top = bands.maxOfOrNull { it.maxPct } ?: 100.0
        return bands.firstOrNull { pct >= it.minPct && (pct < it.maxPct || (it.maxPct >= top && pct >= it.maxPct)) }?.delta ?: 0.0
    }

    fun deltaFor(e: Exam, studentId: String): Double = when {
        !enabled || !e.autoScore -> 0.0
        studentId in e.absent -> absent
        else -> e.scores[studentId]?.let { deltaFor(it, e.fullScore) } ?: 0.0
    }
}

// ---------- 单次考试统计 ----------

data class ExamStat(
    val count: Int,
    val absent: Int,
    val missing: Int,
    val avg: Double,
    val median: Double,
    val max: Double,
    val min: Double,
    val std: Double,
    /** 及格率（≥60%）、良好率（≥75%）、优秀率（≥85%）、低分率（<40%） */
    val passRate: Double,
    val goodRate: Double,
    val excellentRate: Double,
    val lowRate: Double,
    /** 分数段（按满分 10% 一档，从低到高） */
    val bins: List<Pair<String, Int>>,
)

fun Exam.stat(students: List<Student>): ExamStat {
    val ids = students.map { it.id }.toSet()
    val v = scores.filterKeys { it in ids }.values.filter { it.isFinite() }.sorted()
    val n = v.size
    val avg = if (n == 0) 0.0 else v.average()
    val median = when { n == 0 -> 0.0; n % 2 == 1 -> v[n / 2]; else -> (v[n / 2 - 1] + v[n / 2]) / 2 }
    val std = if (n < 2) 0.0 else sqrt(v.sumOf { (it - avg) * (it - avg) } / n)
    fun rate(p: Double) = if (n == 0) 0.0 else v.count { it >= fullScore * p }.toDouble() / n
    val bins = (0 until 10).map { k ->
        val lo = fullScore * k / 10; val hi = fullScore * (k + 1) / 10
        "${fmtShort(lo)}–${fmtShort(hi)}" to v.count { it >= lo && (it < hi || (k == 9 && it <= hi)) }
    }
    val absentN = absent.count { it in ids }
    return ExamStat(
        count = n, absent = absentN, missing = (students.size - n - absentN).coerceAtLeast(0),
        avg = avg, median = median, max = v.maxOrNull() ?: 0.0, min = v.minOrNull() ?: 0.0, std = std,
        passRate = rate(0.6), goodRate = rate(0.75), excellentRate = rate(0.85),
        lowRate = if (n == 0) 0.0 else v.count { it < fullScore * 0.4 }.toDouble() / n,
        bins = bins,
    )
}

private fun fmtShort(x: Double): String = if (x == round(x)) x.toLong().toString() else ((round(x * 10) / 10)).toString()

/** 名次（并列同名次，“1、2、2、4”），只给有分数的学生 */
fun Exam.ranks(): Map<String, Int> {
    val sorted = scores.entries.filter { it.value.isFinite() }.sortedByDescending { it.value }
    val out = HashMap<String, Int>()
    sorted.forEachIndexed { i, e -> out[e.key] = if (i > 0 && abs(sorted[i - 1].value - e.value) < 1e-9) out.getValue(sorted[i - 1].key) else i + 1 }
    return out
}

/** 同一科目（科目为空则全部）按时间排序的考试中，这次考试的上一场 */
fun SchoolClass.previousExam(e: Exam): Exam? =
    analysisExams.filter { it.id != e.id && it.date < e.date && (e.subject.isBlank() || it.subject == e.subject) }.maxByOrNull { it.date }

// ---------- 修改 ----------

fun SchoolClass.upsertExam(e: Exam) = copy(exams = if (exams.any { it.id == e.id }) exams.map { if (it.id == e.id) e else it } else exams + e)
fun SchoolClass.removeExam(id: String) = copy(exams = exams.filterNot { it.id == id })

/** 设置 / 取消组长（studentId 为 null 时取消） */
fun SchoolClass.setGroupLeader(group: String, studentId: String?) =
    copy(groupLeaders = if (studentId == null) groupLeaders - group else groupLeaders + (group to studentId))

/** 当前有效的组长：组长须仍在该组 */
fun SchoolClass.leaderOf(group: String): Student? =
    groupLeaders[group]?.let { id -> students.firstOrNull { it.id == id && it.group.ifBlank { "未分组" } == group } }

// ---------- 导入（粘贴 / CSV / Excel） ----------

object ExamImport {
    data class Column(val index: Int, val header: String, val max: Double)
    data class Row(val studentId: String, val values: List<Double?>, val absent: List<Boolean>, val raw: String)
    data class Result(val columns: List<Column>, val rows: List<Row>, val unmatched: List<String>, val missingStudents: List<String>) {
        fun scoresOf(col: Int): Map<String, Double> = rows.mapNotNull { r -> r.values.getOrNull(col)?.let { r.studentId to it } }.toMap()
        fun absentOf(col: Int): List<String> = rows.filter { it.absent.getOrNull(col) == true }.map { it.studentId }
    }

    /** 本地 OCR 的文字行（像素坐标）→ 表格：同一行的块按横向位置拼起来，再按空白分格 */
    fun tableFromOcr(lines: List<com.deepshawn.hwcounter.recog.StatTable.Line>): List<List<String>> =
        com.deepshawn.hwcounter.recog.StatTable.mergeRows(lines).map { com.deepshawn.hwcounter.recog.StatTable.half(it.text).trim().split(Regex("\\s+")).filter { c -> c.isNotBlank() } }.filter { it.isNotEmpty() }

    private val absentWords = setOf("缺考", "缺", "请假", "假", "病假", "免考", "缓考", "absent", "abs", "-", "/", "—")

    fun number(cell: String?): Double? {
        val t = com.deepshawn.hwcounter.recog.StatTable.half(cell.orEmpty()).trim().removeSuffix("分")
        if (t.isEmpty()) return null
        return t.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 && it <= 1000 }
    }

    private fun normNo(x: String): String {
        val t = com.deepshawn.hwcounter.recog.StatTable.half(x).trim().uppercase()
        return if (t.isNotEmpty() && t.all { it.isDigit() }) t.trimStart('0').ifEmpty { "0" } else t
    }

    /** 表格 → 学生 + 分数列。学号/姓名列按“能对上不同学生”识别，其余以数字为主的列视为分数列 */
    fun recognize(rows: List<List<String>>, students: List<Student>): Result {
        val width = rows.maxOfOrNull { it.size } ?: 0
        val matcher = com.deepshawn.hwcounter.recog.SeatingLogic.SeatMatcher(students)
        fun exact(cell: String?): Student? {
            val c = cell?.trim().orEmpty(); if (c.isEmpty()) return null
            return students.firstOrNull { it.name == c } ?: students.firstOrNull { it.no.isNotBlank() && normNo(it.no) == normNo(c) }
        }
        val hitIds = Array(width) { HashSet<String>() }; val hitN = IntArray(width)
        rows.forEach { r -> r.forEachIndexed { j, c -> exact(c)?.let { hitN[j]++; hitIds[j] += it.id } } }
        val need = maxOf(1, (rows.size * 0.25).toInt())
        // 学号列要求对上的学生足够多且不重复（避免把分数“1”“2”当成学号）
        val idCols = (0 until width).filter { hitIds[it].size >= need && hitIds[it].size >= hitN[it] * 0.6 }
        val rowStu = rows.map { r ->
            idCols.firstNotNullOfOrNull { exact(r.getOrNull(it)) }
                ?: idCols.firstNotNullOfOrNull { j -> r.getOrNull(j)?.takeIf { it.isNotBlank() }?.let { matcher.one(it) } }
                ?: r.firstNotNullOfOrNull { c -> if (c.any { it in '一'..'鿿' }) exact(c) ?: matcher.one(c) else null }
        }
        val scoreCols = (0 until width).filter { j ->
            if (j in idCols) return@filter false
            var filled = 0; var nums = 0
            rows.forEachIndexed { i, r ->
                if (rowStu[i] == null) return@forEachIndexed
                val v = r.getOrNull(j)?.trim().orEmpty(); if (v.isEmpty()) return@forEachIndexed
                filled++
                if (number(v) != null || v.lowercase() in absentWords) nums++
            }
            filled > 0 && nums >= filled * 0.7 && rows.indices.any { rowStu[it] != null && number(rows[it].getOrNull(j)) != null }
        }
        val headRow = rows.indices.firstOrNull { i -> rowStu[i] == null && scoreCols.any { j -> val v = rows[i].getOrNull(j)?.trim().orEmpty(); v.isNotEmpty() && number(v) == null } } ?: -1
        val columns = scoreCols.mapIndexed { k, j ->
            val maxV = rows.indices.filter { rowStu[it] != null }.mapNotNull { number(rows[it].getOrNull(j)) }.maxOrNull() ?: 0.0
            Column(j, (if (headRow >= 0) rows[headRow].getOrNull(j)?.trim() else null)?.takeIf { it.isNotEmpty() } ?: "第${k + 1}列", maxV)
        }
        val out = ArrayList<Row>(); val unmatched = ArrayList<String>(); val seen = HashSet<String>()
        rows.forEachIndexed { i, r ->
            val s = rowStu[i]
            if (s == null) { if (i != headRow && r.any { it.isNotBlank() }) unmatched += r.filter { it.isNotBlank() }.joinToString(" "); return@forEachIndexed }
            if (!seen.add(s.id)) return@forEachIndexed
            out += Row(
                s.id,
                columns.map { number(r.getOrNull(it.index)) },
                columns.map { r.getOrNull(it.index)?.trim()?.lowercase() in absentWords && number(r.getOrNull(it.index)) == null },
                r.filter { it.isNotBlank() }.joinToString(" "),
            )
        }
        return Result(columns, out, unmatched, students.filter { it.id !in seen }.map { it.id })
    }

    /** 根据最高分猜满分：≤100 → 100，≤120 → 120，≤150 → 150，否则取整到 10 */
    fun guessFull(max: Double): Double = when {
        max <= 100 -> 100.0
        max <= 120 -> 120.0
        max <= 150 -> 150.0
        else -> kotlin.math.ceil(max / 10) * 10
    }
}
