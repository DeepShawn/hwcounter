package com.deepshawn.hwcounter.desktop

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.deepshawn.hwcounter.data.AppData
import com.deepshawn.hwcounter.data.Assignment
import com.deepshawn.hwcounter.data.Exam
import com.deepshawn.hwcounter.data.HwStatus
import com.deepshawn.hwcounter.data.SchoolClass
import com.deepshawn.hwcounter.data.ScoreEntry
import com.deepshawn.hwcounter.data.Student
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

/** 离屏截图（不需要显示器）：用合成的示例数据渲染各个页面，检查布局。./gradlew :desktop:shots */
fun main(args: Array<String>) {
    val out = File(args.firstOrNull() ?: "build/shots").apply { mkdirs() }
    val day = 86_400_000L; val now = System.currentTimeMillis()
    val names = listOf("张锦宇", "李子涵", "林静", "王芳", "冉浩", "周杰", "陈浩宏", "胡晨", "赵敏", "孙悦", "郑怡", "吴磊")
    val st = names.mapIndexed { i, n -> Student(id = "s$i", no = (i + 1).toString().padStart(2, '0'), name = n, group = "第${i % 3 + 1}组", gender = if (i % 2 == 0) "男" else "女") }
    fun status(i: Int, k: Int) = when { (i * 7 + k * 3) % 11 == 0 -> HwStatus.MISSING; (i + k) % 9 == 0 -> HwStatus.LATE; (i * 3 + k) % 7 == 0 -> HwStatus.EXCELLENT; (i + k) % 13 == 0 -> HwStatus.LEAVE; else -> HwStatus.SUBMITTED }
    val assigns = (0 until 14).map { k ->
        val m = st.mapIndexed { i, s -> s.id to status(i, k) }.toMap().toMutableMap()
        if (k == 12) { m["s2"] = HwStatus.MISSING; m["s4"] = HwStatus.MISSING }
        if (k == 13) m["s3"] = HwStatus.MISSING
        Assignment(id = "a$k", title = "练习${k + 1}", subject = listOf("语文", "数学", "英语")[k % 3], createdAt = now - (14 - k) * 2 * day, statuses = m,
            reviewFailed = if (k == 13) listOf("s3") else emptyList(), plagiarism = if (k == 12) mapOf("s4" to listOf("s2")) else emptyMap(), comments = if (k == 13) mapOf("s1" to "字迹潦草") else emptyMap())
    }
    val exams = listOf(
        Exam(id = "e1", title = "第一次月考", subject = "数学", date = now - 20 * day, scores = st.drop(1).mapIndexed { i, s -> s.id to (55.0 + (i * 37) % 45) }.toMap(), absent = listOf("s0")),
        Exam(id = "e2", title = "第二次月考", subject = "数学", date = now - 5 * day, scores = st.mapIndexed { i, s -> s.id to (50.0 + (i * 23 + 11) % 50) }.toMap()),
    )
    val c = SchoolClass(id = "c1", name = "高一(3)班", students = st, assignments = assigns, exams = exams, groupLeaders = mapOf("第1组" to "s0", "第2组" to "s1"), reps = listOf("s1"),
        scores = listOf(ScoreEntry(studentId = "s0", delta = 2.0, reason = "课堂表现好", time = now - day), ScoreEntry(studentId = "s5", delta = -1.0, reason = "迟到", time = now - 3 * day)))
    val data = AppData(classes = listOf(c), currentClassId = "c1")

    fun shot(name: String, height: Int = 1600, dark: Boolean = false, setup: (AppState) -> Unit) {
        val prefs = Prefs().apply { this.dark = if (dark) 2 else 1; theme = "hyper"; layout = "grid" }
        val state = AppState(prefs, data).apply { load = Load.OK; guide = false; setup(this) }
        val scene = ImageComposeScene(1180, height, Density(1f)) { HwTheme(prefs) { AppRoot(state, autoLoad = false) } }
        var t = 0L
        repeat(14) { scene.render(t); t += 100_000_000L; Thread.sleep(150) }
        val img = scene.render(t)
        File(out, "$name.png").writeBytes(img.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        scene.close()
        println("✓ $name")
    }
    shot("01_home") { }
    shot("02_assignment") { it.open(Route.Assign("a12")) }
    shot("03_roster") { it.tab = Tab.ROSTER }
    shot("04_student", 2200) { it.open(Route.Student("s4")) }
    shot("05_scores") { it.tab = Tab.SCORES }
    shot("06_stats_hw", 9000) { it.tab = Tab.STATS }
    shot("07_exam", 1400) { it.open(Route.Exam("e2")) }
    shot("06b_stats_exam", 4200) { it.tab = Tab.STATS; Keep.mode = 1 }
    shot("06c_stats_attention", 2200) { it.tab = Tab.STATS; Keep.mode = 2; Keep.att = 0 }
    shot("06d_stats_stock", 2600) { it.tab = Tab.STATS; Keep.mode = 2; Keep.att = 1 }
    shot("06e_stats_groups", 3600) { it.tab = Tab.STATS; Keep.mode = 3 }
    shot("06f_stats_plagiarism", 2400) { it.tab = Tab.STATS; Keep.mode = 4 }
    shot("08_settings") { it.tab = Tab.SETTINGS }
    shot("09_guide", 900) { it.guide = true }
    shot("10_home_dark", dark = true) { }
}
