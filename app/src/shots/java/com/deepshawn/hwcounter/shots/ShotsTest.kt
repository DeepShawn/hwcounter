package com.deepshawn.hwcounter.shots

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.swipeDown
import androidx.test.core.app.ApplicationProvider
import com.deepshawn.hwcounter.data.*
import com.deepshawn.hwcounter.ui.App
import com.deepshawn.hwcounter.ui.theme.HwTheme
import com.deepshawn.hwcounter.ui.theme.UiPrefs
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 界面截图：仅在 CI 的 screenshots 工作流中运行（./gradlew -Pshots ...），
 * 输出到仓库根目录 shots/，作为构建产物上传，用来检查布局。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
class ShotsTest {
    @get:Rule val rule = createComposeRule()
    private val out = File(System.getProperty("shots.dir") ?: "build/shots").apply { mkdirs() }

    private fun sample(): AppData {
        val names = listOf("张锦宇", "李子涵", "林静", "王芳", "冉浩", "周杰", "陈浩宏", "胡晨", "赵敏", "孙悦", "郑怡", "吴磊", "钱多多", "冯雪", "褚明", "卫青", "蒋琳", "沈航", "韩梅", "杨帆", "朱婷", "秦川", "许诺", "何欢")
        val st = names.mapIndexed { i, n -> Student(id = "s$i", no = "2024%02d".format(i + 1), name = n, group = "第${i % 4 + 1}组", gender = if (i % 3 == 0) "女" else "男") }
        val day = 86_400_000L
        val now = System.currentTimeMillis()
        val subjects = listOf("语文", "数学", "英语")
        val assigns = (0 until 18).map { k ->
            val m = st.associate { s ->
                val i = s.id.drop(1).toInt()
                s.id to when {
                    k == 17 && i % 7 == 3 -> HwStatus.NONE
                    (i * 7 + k * 3) % 11 == 0 -> HwStatus.MISSING
                    (i + k) % 9 == 0 -> HwStatus.LATE
                    (i * 3 + k) % 7 == 0 -> HwStatus.EXCELLENT
                    i == 5 && k > 13 -> HwStatus.MISSING
                    (i + k) % 17 == 0 -> HwStatus.LEAVE
                    else -> HwStatus.SUBMITTED
                }
            }
            Assignment(id = "a$k", title = "练习${k + 1}", subject = subjects[k % 3], createdAt = now - (18 - k) * day * 2 + 3600_000L * 9, statuses = m)
        }
        fun examScores(seed: Int, base: Double) = st.associate { s -> val i = s.id.drop(1).toInt(); s.id to (base + ((i * 37 + seed * 11) % 41) - 8).coerceIn(28.0, 100.0) }
        val exams = listOf(
            Exam(id = "e1", title = "第一次月考", subject = "数学", date = now - 30 * day, scores = examScores(1, 66.0)),
            Exam(id = "e2", title = "期中考试", subject = "数学", date = now - 14 * day, scores = examScores(2, 70.0) - "s4", absent = listOf("s4")),
            Exam(id = "e3", title = "期中考试", subject = "语文", date = now - 13 * day, fullScore = 150.0, scores = examScores(3, 72.0).mapValues { it.value * 1.4 } - "s9"),
        )
        var c = SchoolClass(
            id = "c1", name = "高一(3)班", students = st, assignments = assigns, exams = exams,
            groupLeaders = mapOf("第1组" to "s0", "第2组" to "s5", "第3组" to "s2"),
            scores = listOf(
                ScoreEntry(studentId = "s0", delta = 2.0, reason = "主动回答", time = now - 20 * day), ScoreEntry(studentId = "s3", delta = -1.0, reason = "迟到", time = now - 3 * day),
                ScoreEntry(studentId = "s7", delta = 1.0, reason = "主动回答", time = now - 9 * day), ScoreEntry(studentId = "s8", delta = 1.0, reason = "主动回答", time = now - 5 * day),
                ScoreEntry(studentId = "s11", delta = 2.0, reason = "课堂表现好", time = now - 4 * day), ScoreEntry(studentId = "s12", delta = -2.0, reason = "违反纪律", time = now - 2 * day),
            ),
            autoReset = AutoReset(target = 100.0, day = 1, since = now - 400 * day),
        )
        c = c.recharge(ScoreRules(), st.map { it.id }, 100.0, "期中重新计分", now - 15 * day, assignment = assigns[9])
        return AppData(classes = listOf(c), currentClassId = "c1", settings = Settings(onboarded = true))
    }

    private fun save(name: String) {
        rule.waitForIdle()
        rule.onRoot().captureRoboImage(File(out, "$name.png").absolutePath)
    }

    private fun scroll(times: Int = 1) = repeat(times) {
        rule.onRoot().performTouchInput { swipeUp(startY = bottom * 0.78f, endY = top + bottom * 0.12f, durationMillis = 400) }
        rule.waitForIdle()
    }

    /** 一直返回直到看到底部选项卡 */
    private fun home() = repeat(4) { if (rule.onAllNodes(hasText("设置") and hasClickAction()).fetchSemanticsNodes().isEmpty()) runCatching { back() } }
    private fun top() = repeat(10) { rule.onRoot().performTouchInput { swipeDown(startY = top + bottom * 0.2f, endY = bottom * 0.95f, durationMillis = 200) } }
    private fun click(text: String) = rule.onAllNodes(hasText(text) and hasClickAction()).onFirst().performClick()
    private fun tab(label: String) = rule.onAllNodes(hasText(label) and hasClickAction()).onFirst().performClick()
    private fun back() = rule.onAllNodes(hasContentDescription("返回")).onFirst().performClick()
    private fun safe(block: () -> Unit) = runCatching(block).onFailure { println("shot step failed: ${it.message?.take(200)}") }

    /** PDF 报告画到位图上检查排版 */
    private fun pdfPages(prefix: String) {
        val c = sample().classes.first()
        val r = Analytics.report(c, ScoreRules(), Analytics.Spec(Analytics.Range.ALL))
        val pages = ArrayList<android.graphics.Bitmap>(); var cur: android.graphics.Bitmap? = null
        com.deepshawn.hwcounter.export.PdfReport.render(c, r, Analytics.examReport(c), System.currentTimeMillis(), newPage = {
            val b = android.graphics.Bitmap.createBitmap(595 * 2, 842 * 2, android.graphics.Bitmap.Config.ARGB_8888); cur = b
            android.graphics.Canvas(b).apply { scale(2f, 2f) }
        }, endPage = { cur?.let { pages += it } })
        pages.forEachIndexed { i, b -> File(out, "${prefix}_pdf_${i + 1}.png").outputStream().use { b.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
    }

    private fun run(prefix: String, dark: Int, ui: UiPrefs) {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm = AppViewModel(app)
        @Suppress("UNCHECKED_CAST")
        (AppViewModel::class.java.getDeclaredField("_readOnly").apply { isAccessible = true }.get(vm) as MutableStateFlow<Boolean>).value = false
        val data = sample().let { it.copy(settings = it.settings.copy(dark = dark)) }
        rule.setContent { HwTheme(data.settings.theme, dark, 1, false, ui) { App(vm, data) } }
        save("${prefix}_01_home"); scroll(); save("${prefix}_02_home_scrolled")

        tab("名单"); save("${prefix}_03_roster")
        safe { click("小组"); save("${prefix}_04_groups"); scroll(); save("${prefix}_05_groups_scrolled") }
        safe { click("第1组"); save("${prefix}_06_group_detail"); back() }

        tab("积分"); save("${prefix}_10_scores")
        safe {
            rule.onAllNodes(hasContentDescription("更多")).onFirst().performClick(); save("${prefix}_11_scores_menu")
            click("全班积分重置"); save("${prefix}_12_reset_dialog")
            click("取消")
        }
        safe {
            rule.onAllNodes(hasContentDescription("更多")).onFirst().performClick()
            click("每月定期重置"); save("${prefix}_13_auto_reset")
            click("取消")
        }
        safe {
            rule.onAllNodes(hasContentDescription("更多")).onFirst().performClick()
            click("批量加减分"); save("${prefix}_14_batch")
            rule.onAllNodes(hasText("第1组", substring = true) and hasClickAction()).onFirst().performClick()
            save("${prefix}_15_batch_pick"); scroll(); save("${prefix}_16_batch_list"); back()
        }

        tab("统计"); save("${prefix}_20_stats")
        for (i in 1..14) { scroll(); save("${prefix}_2${"%02d".format(i)}_stats") }
        safe {
            top()
            click("按时间"); save("${prefix}_28_stats_month")
            click("‹"); save("${prefix}_29_stats_prev_month")
            click("自定义"); save("${prefix}_29b_stats_custom")
            click("本轮"); save("${prefix}_30_stats_period")
            click("考试"); save("${prefix}_31_stats_exam"); scroll(); save("${prefix}_32_stats_exam"); scroll(); save("${prefix}_33_stats_exam"); scroll(); save("${prefix}_34_stats_exam")
        }

        tab("首页")
        safe { top(); click("考试"); save("${prefix}_40_exams") }
        safe { click("第一次月考"); save("${prefix}_41_exam_detail"); scroll(); save("${prefix}_42_exam_detail") }
        safe { top(); click("按学号"); save("${prefix}_43_exam_by_no") }
        safe { click("多选"); click("张锦宇"); click("李子涵"); save("${prefix}_46_exam_select"); click("记为缺考"); save("${prefix}_47_exam_absent") }
        safe { rule.onAllNodes(hasText("林静") and hasClickAction()).onFirst().performTouchInput { longClick() }; save("${prefix}_48_exam_row_menu"); click("查看个人详情"); back() }
        safe { back(); rule.onAllNodes(hasContentDescription("导入成绩")).onFirst().performClick(); save("${prefix}_44_exam_import") }
        safe { home(); tab("首页"); top(); click("批量加分"); save("${prefix}_45_batch_home"); back() }
        home()
        tab("设置"); save("${prefix}_50_settings"); scroll(); save("${prefix}_51_settings"); scroll(); save("${prefix}_52_settings")
        safe {
            tab("名单"); top(); click("学生")
            click("张锦宇")
            save("${prefix}_60_student"); scroll(); save("${prefix}_61_student")
        }
    }

    @Test fun light() { run("light", 1, UiPrefs()); pdfPages("report") }
    @Test fun dark() = run("dark", 2, UiPrefs())
    @Test fun plain() = run("plain", 1, UiPrefs(glass = false))

    @Suppress("unused") private val keep: SemanticsMatcher? = null
}
