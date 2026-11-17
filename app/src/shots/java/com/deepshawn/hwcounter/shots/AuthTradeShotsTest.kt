package com.deepshawn.hwcounter.shots

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import com.deepshawn.hwcounter.cloud.Trade
import com.deepshawn.hwcounter.data.*
import com.deepshawn.hwcounter.ui.App
import com.deepshawn.hwcounter.ui.Route
import com.deepshawn.hwcounter.ui.ShotHooks
import com.deepshawn.hwcounter.ui.screens.TradeData
import com.deepshawn.hwcounter.ui.screens.tradeFake
import com.deepshawn.hwcounter.ui.theme.HwTheme
import com.deepshawn.hwcounter.ui.theme.UiPrefs
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** 登录 / 注册页、选股下单页的截图（不连网：行情用假数据） */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
class AuthTradeShotsTest {
    @get:Rule val rule = createComposeRule()
    private val out = File(System.getProperty("shots.dir") ?: "build/shots").apply { mkdirs() }

    private fun data(dark: Int): AppData {
        val names = listOf("张锦宇", "李子涵", "林静", "王芳", "冉浩", "周杰")
        val st = names.mapIndexed { i, n -> Student(id = "s$i", no = "%02d".format(i + 1), name = n, group = listOf("生物组", "化学组", "语文组")[i % 3]) }
        return AppData(classes = listOf(SchoolClass(id = "c1", name = "高二（2）班", students = st)), currentClassId = "c1", settings = Settings(onboarded = true, dark = dark))
    }

    private fun save(name: String) { rule.waitForIdle(); rule.onRoot().captureRoboImage(File(out, "$name.png").absolutePath) }
    private fun click(text: String) = rule.onAllNodes(hasText(text) and hasClickAction()).onFirst().performClick()
    private fun scroll() { rule.onRoot().performTouchInput { swipeUp(startY = bottom * 0.78f, endY = top + bottom * 0.2f, durationMillis = 400) }; rule.waitForIdle() }

    private fun start(dark: Int, routes: List<Route>) {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm = AppViewModel(app)
        com.deepshawn.hwcounter.ui.theme.UiPrefsStore.update(app) { UiPrefs(onboarded = true) }
        ShotHooks.start = routes
        val d = data(dark)
        rule.setContent { HwTheme(d.settings.theme, dark, 1, false, com.deepshawn.hwcounter.ui.theme.UiPrefsStore.prefs) { App(vm, d) } }
    }

    @After fun reset() { ShotHooks.start = null; tradeFake = null }

    private fun auth(prefix: String, dark: Int) {
        start(dark, listOf(Route.Settings, Route.Auth(0)))
        save("${prefix}_auth_login")
        click("去注册"); save("${prefix}_auth_register_invite")
        click("老师"); save("${prefix}_auth_register_teacher")
        click("学生 / 家长"); scroll(); save("${prefix}_auth_register_invite_2")
    }
    @Test fun authLight() = auth("light", 1)
    @Test fun authDark() = auth("dark", 2)

    private fun trade(prefix: String, dark: Int) {
        val prices = listOf("生物组" to 24.09, "化学组" to 18.6, "语文组" to 16.44).map { (g, v) -> Trade.Price("GA:c1/$g", g, "GA", v) } +
            listOf("张锦宇" to 92.4, "李子涵" to 85.2, "林静" to 77.0, "王芳" to 64.3, "冉浩" to 58.1, "周杰" to 81.6).mapIndexed { i, (n, v) -> Trade.Price("C:s$i", n, "C", v) }
        tradeFake = TradeData(Trade.Me(role = "member", userId = "u1", username = "zhang", displayName = "张三家长", cash = 10_000.0), prices, listOf(Trade.Position("C:s1", 20, 80.0)))
        start(dark, listOf(Route.Stats, Route.TradeOrder(null, "buy")))
        save("${prefix}_trade_pick")
        click("小组关注"); save("${prefix}_trade_pick_groups")
        click("生物组"); save("${prefix}_trade_order"); scroll(); save("${prefix}_trade_order_2")
        click("限价单"); save("${prefix}_trade_order_limit")
    }
    @Test fun tradeLight() = trade("light", 1)
    @Test fun tradeDark() = trade("dark", 2)
}
