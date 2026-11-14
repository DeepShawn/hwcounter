package com.deepshawn.hwcounter.data

import kotlinx.serialization.Serializable
import java.util.UUID
import java.util.Locale

fun newId(): String = UUID.randomUUID().toString().replace("-", "").take(16)

private val studentNumberParts = Regex("[0-9]+|[^0-9]+")

/** 学号按自然顺序比较，不转换为数值，避免长学号溢出。空学号排在最后。 */
fun compareStudentNumbers(left: String, right: String): Int {
    val a = left.trim().lowercase(Locale.ROOT)
    val b = right.trim().lowercase(Locale.ROOT)
    if (a.isEmpty() || b.isEmpty()) return if (a == b) 0 else if (a.isEmpty()) 1 else -1
    val xs = studentNumberParts.findAll(a).map { it.value }.toList()
    val ys = studentNumberParts.findAll(b).map { it.value }.toList()
    for (i in 0 until minOf(xs.size, ys.size)) {
        var x = xs[i]; var y = ys[i]
        if (x[0] in '0'..'9' && y[0] in '0'..'9') {
            x = x.trimStart('0').ifEmpty { "0" }; y = y.trimStart('0').ifEmpty { "0" }
            if (x.length != y.length) return x.length.compareTo(y.length)
        }
        if (x != y) return x.compareTo(y)
    }
    return xs.size.compareTo(ys.size)
}

/** 作业提交状态 */
@Serializable
enum class HwStatus(val label: String, val short: String, val emoji: String) {
    NONE("未登记", "—", "⏳"),
    SUBMITTED("已交", "交", "✅"),
    EXCELLENT("优秀", "优", "🌟"),
    LATE("迟交", "迟", "🐢"),
    MISSING("未交", "缺", "❌"),
    LEAVE("请假", "假", "🏖️");
}

@Serializable
data class Student(
    val id: String = newId(),
    val no: String,
    val name: String,
    val group: String = "",
    val gender: String = "",
    val note: String = "",
    /** 长期请假：新建作业时自动标为请假 */
    val longLeave: Boolean = false,
)

@Serializable
data class Assignment(
    val id: String = newId(),
    val title: String,
    val subject: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val statuses: Map<String, HwStatus> = emptyMap(),
    val comments: Map<String, String> = emptyMap(),
    /** 通过“数本数”得到的实物本数 */
    val countedBooks: Int? = null,
    val pinned: Boolean = false,
)

@Serializable
data class ScoreEntry(
    val id: String = newId(),
    val studentId: String,
    val delta: Double,
    val reason: String,
    val time: Long = System.currentTimeMillis(),
)

@Serializable
data class SchoolClass(
    val id: String = newId(),
    val name: String,
    val students: List<Student> = emptyList(),
    val assignments: List<Assignment> = emptyList(),
    val scores: List<ScoreEntry> = emptyList(),
    val emoji: String = "🎒",
    /** 座位表（可选） */
    val seating: Seating? = null,
)

/** 座位表：seats 为 学生id → 下标(行*cols+列)；frontAtTop 讲台是否在上方。三端同一格式 */
@Serializable
data class Seating(
    val rows: Int = 1,
    val cols: Int = 1,
    val seats: Map<String, Int> = emptyMap(),
    val frontAtTop: Boolean = true,
)

@Serializable
data class ScoreRules(
    val initial: Double = 100.0,
    val submitted: Double = 0.0,
    val excellent: Double = 1.0,
    val late: Double = -1.0,
    val missing: Double = -2.0,
    val leave: Double = 0.0,
) {
    fun of(s: HwStatus): Double = when (s) {
        HwStatus.NONE -> 0.0
        HwStatus.SUBMITTED -> submitted
        HwStatus.EXCELLENT -> excellent
        HwStatus.LATE -> late
        HwStatus.MISSING -> missing
        HwStatus.LEAVE -> leave
    }
}

@Serializable
data class AiConfig(
    /** openai: OpenAI 兼容接口（通义千问/DeepSeek/豆包/Kimi/GPT 等）; anthropic: Claude */
    val provider: String = "openai",
    val baseUrl: String = "https://dashscope.aliyuncs.com/compatible-mode/v1",
    val apiKey: String = "",
    val model: String = "qwen-vl-max",
    /** 模型能否直接看图；纯文本模型（如 deepseek-chat）改为“本地 OCR 文字 → AI 校对匹配” */
    val vision: Boolean = true,
)

object RecogMode {
    const val LOCAL = 0
    const val AI = 1
    const val HYBRID = 2
}

@Serializable
data class Settings(
    val theme: String = "mint",
    /** 0 跟随系统 1 浅色 2 深色 */
    val dark: Int = 0,
    /** list / grid / seat / group */
    val layout: String = "grid",
    /** 0 方正 1 圆润 2 超圆 */
    val shape: Int = 1,
    val compact: Boolean = false,
    val rules: ScoreRules = ScoreRules(),
    val ai: AiConfig = AiConfig(),
    val recogMode: Int = RecogMode.LOCAL,
    val minIdLength: Int = 4,
    val fuzzy: Boolean = true,
    val matchNames: Boolean = true,
    val haptic: Boolean = true,
    val sound: Boolean = true,
    val confetti: Boolean = true,
    val subjects: List<String> = listOf("语文", "数学", "英语", "物理", "化学", "生物", "历史", "地理", "政治"),
    val quickReasons: List<String> = listOf("课堂表现好", "主动回答", "作业优秀", "进步明显", "迟到", "违反纪律", "未带作业"),
    val onboarded: Boolean = false,
    /** 语音登记的模糊程度：0 严格 1 标准 2 宽松 */
    val voiceFuzzy: Int = 1,
    /** 语音登记：拼音首字母匹配（如 ZJY = 张锦宇） */
    val voiceInitials: Boolean = true,
)

@Serializable
data class AppData(
    val version: Int = 1,
    val classes: List<SchoolClass> = emptyList(),
    val currentClassId: String? = null,
    val settings: Settings = Settings(),
)

// ---------- 派生计算 ----------

data class StudentStat(
    val student: Student,
    val score: Double,
    val counts: Map<HwStatus, Int>,
) {
    val missing get() = counts[HwStatus.MISSING] ?: 0
    val late get() = counts[HwStatus.LATE] ?: 0
    val submitted get() = (counts[HwStatus.SUBMITTED] ?: 0) + (counts[HwStatus.EXCELLENT] ?: 0)
}

fun SchoolClass.statusOf(a: Assignment, s: Student): HwStatus = a.statuses[s.id] ?: HwStatus.NONE

/** 新增调整流水，不删除历史加减分，也不修改作业及未交次数。 */
fun SchoolClass.resetStudentScores(rules: ScoreRules, studentIds: Collection<String>, target: Double): SchoolClass {
    if (!target.isFinite()) return this
    val ids = studentIds.toSet()
    val entries = studentStats(rules).filter { it.student.id in ids && it.score != target }.map {
        ScoreEntry(studentId = it.student.id, delta = target - it.score, reason = "重置分数至 ${target.fmt()}")
    }
    return if (entries.isEmpty()) this else copy(scores = scores + entries)
}

fun SchoolClass.studentStats(rules: ScoreRules): List<StudentStat> {
    val manual = scores.groupBy { it.studentId }.mapValues { e -> e.value.sumOf { it.delta } }
    return students.map { st ->
        val counts = HashMap<HwStatus, Int>()
        var auto = 0.0
        for (a in assignments) {
            val s = a.statuses[st.id] ?: HwStatus.NONE
            counts[s] = (counts[s] ?: 0) + 1
            auto += rules.of(s)
        }
        StudentStat(st, rules.initial + auto + (manual[st.id] ?: 0.0), counts)
    }
}

data class AssignmentSummary(val total: Int, val counts: Map<HwStatus, Int>) {
    fun c(s: HwStatus) = counts[s] ?: 0
    val handedIn get() = c(HwStatus.SUBMITTED) + c(HwStatus.EXCELLENT) + c(HwStatus.LATE)
    /** 应交人数（去掉请假） */
    val expected get() = total - c(HwStatus.LEAVE)
    val rate: Float get() = if (expected <= 0) 1f else handedIn.toFloat() / expected
}

fun SchoolClass.summary(a: Assignment): AssignmentSummary {
    val counts = HashMap<HwStatus, Int>()
    for (st in students) {
        val s = a.statuses[st.id] ?: HwStatus.NONE
        counts[s] = (counts[s] ?: 0) + 1
    }
    return AssignmentSummary(students.size, counts)
}

fun Double.fmt(): String {
    val r = Math.round(this * 10) / 10.0
    return if (r == Math.floor(r)) r.toLong().toString() else r.toString()
}

fun Double.signed(): String = if (this > 0) "+" + fmt() else fmt()
