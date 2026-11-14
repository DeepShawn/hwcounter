package com.deepshawn.hwcounter.shots

import com.github.takahirom.roborazzi.captureRoboImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import com.deepshawn.hwcounter.data.*
import com.deepshawn.hwcounter.ui.App
import com.deepshawn.hwcounter.ui.theme.HwTheme
import com.deepshawn.hwcounter.ui.theme.UiPrefs
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
        val names = listOf("张锦宇", "李子涵", "林静", "王芳", "冉浩", "周杰", "陈浩宏", "胡晨", "赵敏", "孙悦", "郑怡", "吴磊", "钱多多", "冯雪", "褚明", "卫青", "蒋琳", "沈航")
        val st = names.mapIndexed { i, n -> Student(id = "s$i", no = "2024%02d".format(i + 1), name = n, group = "第${i % 4 + 1}组") }
        val day = 86_400_000L
        val now = System.currentTimeMillis()
        val subjects = listOf("语文", "数学", "英语")
        val assigns = (0 until 16).map { k ->
            val m = st.associate { s ->
                val i = s.id.drop(1).toInt()
                s.id to when {
                    (i * 7 + k * 3) % 11 == 0 -> HwStatus.MISSING
                    (i + k) % 9 == 0 -> HwStatus.LATE
                    (i * 3 + k) % 7 == 0 -> HwStatus.EXCELLENT
                    i == 5 && k > 12 -> HwStatus.MISSING
                    (i + k) % 17 == 0 -> HwStatus.LEAVE
                    else -> HwStatus.SUBMITTED
                }
            }
            Assignment(id = "a$k", title = "练习${k + 1}", subject = subjects[k % 3], createdAt = now - (16 - k) * day * 2, statuses = m)
        }
        var c = SchoolClass(
            id = "c1", name = "高一(3)班", students = st, assignments = assigns,
            scores = listOf(ScoreEntry(studentId = "s0", delta = 2.0, reason = "主动回答", time = now - 20 * day), ScoreEntry(studentId = "s3", delta = -1.0, reason = "迟到", time = now - 3 * day)),
        )
        c = c.recharge(ScoreRules(), st.map { it.id }, 100.0, "期中重新计分", now - 9 * day, assignment = assigns[8])
        return AppData(classes = listOf(c), currentClassId = "c1", settings = Settings(onboarded = true))
    }

    private fun save(name: String) {
        rule.waitForIdle()
        rule.onRoot().captureRoboImage(File(out, "$name.png").absolutePath)
    }

    private fun scroll(times: Int = 1) = repeat(times) {
        rule.onRoot().performTouchInput { swipeUp(startY = bottom * 0.75f, endY = top + bottom * 0.15f, durationMillis = 400) }
        rule.waitForIdle()
    }

    private fun tab(label: String) = rule.onNode(hasText(label) and hasClickAction()).performClick()

    private fun run(prefix: String, dark: Int, ui: UiPrefs) {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm = AppViewModel(app)
        @Suppress("UNCHECKED_CAST")
        (AppViewModel::class.java.getDeclaredField("_readOnly").apply { isAccessible = true }.get(vm) as MutableStateFlow<Boolean>).value = false
        val data = sample().let { it.copy(settings = it.settings.copy(dark = dark)) }
        rule.setContent { HwTheme(data.settings.theme, dark, 1, false, ui) { App(vm, data) } }
        save("${prefix}_01_home"); scroll(); save("${prefix}_02_home_scrolled")
        tab("名单"); save("${prefix}_03_roster")
        tab("积分"); save("${prefix}_04_scores"); scroll(); save("${prefix}_05_scores_scrolled")
        runCatching {
            rule.onNode(hasContentDescription("更多")).performClick(); rule.waitForIdle(); save("${prefix}_06_scores_menu")
            rule.onAllNodes(hasText("全班积分充值")).onFirst().performClick(); rule.waitForIdle(); save("${prefix}_07_recharge_dialog")
            rule.onAllNodes(hasText("从某次作业起")).onFirst().performClick(); rule.waitForIdle(); save("${prefix}_08_recharge_from_assignment")
            rule.onAllNodes(hasText("取消")).onFirst().performClick(); rule.waitForIdle()
        }
        tab("统计")
        save("${prefix}_10_stats")
        for (i in 1..6) { scroll(); save("${prefix}_1${i}_stats_scrolled") }
        tab("设置"); save("${prefix}_20_settings"); scroll(); save("${prefix}_21_settings_scrolled"); scroll(2); save("${prefix}_22_settings_scrolled")
        tab("积分")
        runCatching {
            rule.onAllNodes(hasText("张锦宇")).onFirst().performTouchInput { longClick() }
            rule.waitForIdle(); save("${prefix}_30_student_detail"); scroll(); save("${prefix}_31_student_detail_scrolled")
        }
    }

    @Test fun light() = run("light", 1, UiPrefs())
    @Test fun dark() = run("dark", 2, UiPrefs())
}
