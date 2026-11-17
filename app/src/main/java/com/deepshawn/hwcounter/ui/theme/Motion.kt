package com.deepshawn.hwcounter.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.MotionDurationScale

/**
 * 本机显示偏好（只存本机，不随云端同步：每台设备性能不同）。
 * - animations：界面动效，默认关闭（关闭时所有动画瞬间完成，操作更跟手）；
 * - lowPerf：低性能模式，关闭液态玻璃、平滑圆角、阴影、回弹和彩带，强制关闭动效；
 * - glass：液态玻璃效果（背景模糊 + 折射），需要 Android 12+，折射需要 Android 13+；
 * - perf：性能模式（默认关闭），动效和液态玻璃都开到最好：弹簧翻页带纵深、卡片缩放浮现、
 *   滑块拉伸、玻璃更强的模糊与折射（纵深 + 色散）、流动的柔光背景、最高刷新率。与低性能模式互斥。
 */
@Immutable
data class UiPrefs(
    val animations: Boolean = false,
    val lowPerf: Boolean = false,
    val glass: Boolean = true,
    /** 性能模式：动效与液态玻璃做到极致（默认关闭） */
    val perf: Boolean = false,
    /** 关注度用股票行情的方式显示 */
    val stockMode: Boolean = false,
    /** 股票模式配色：true 美股（绿涨红跌），false A股（红涨绿跌） */
    val usColors: Boolean = true,
    /** 新手引导已看过（或跳过）；只存本机 */
    val onboarded: Boolean = false,
) {
    /** 实际是否播放动画 */
    val motion: Boolean get() = (animations || perf) && !lowPerf
    /** 性能模式实际生效（低性能模式优先） */
    val ultra: Boolean get() = perf && !lowPerf
    /** 实际是否绘制液态玻璃（模糊需要 Android 12） */
    val glassOn: Boolean get() = (glass || perf) && !lowPerf && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    /** 折射透镜需要 Android 13 */
    val lensOn: Boolean get() = glassOn && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
}

object UiPrefsStore {
    private const val FILE = "display"
    var prefs by mutableStateOf(UiPrefs())
        private set
    private var loaded = false

    fun load(ctx: Context): UiPrefs {
        if (!loaded) {
            val sp = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            prefs = UiPrefs(
                animations = sp.getBoolean("animations", false),
                lowPerf = sp.getBoolean("lowPerf", false),
                glass = sp.getBoolean("glass", true),
                perf = sp.getBoolean("perf", false),
                stockMode = sp.getBoolean("stockMode", false),
                usColors = sp.getBoolean("usColors", true),
                onboarded = sp.getBoolean("onboarded", false),
            )
            loaded = true
        }
        return prefs
    }

    fun update(ctx: Context, f: (UiPrefs) -> UiPrefs) {
        val next = f(prefs)
        prefs = next
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putBoolean("animations", next.animations)
            .putBoolean("lowPerf", next.lowPerf)
            .putBoolean("glass", next.glass)
            .putBoolean("perf", next.perf)
            .putBoolean("stockMode", next.stockMode)
            .putBoolean("usColors", next.usColors)
            .putBoolean("onboarded", next.onboarded)
            .apply()
    }
}

/**
 * 全局动画时长倍率：放进 Recomposer 的协程上下文后，所有 Compose 动画（animate*AsState、
 * AnimatedVisibility、弹窗、按压反馈……）都会读取它。倍率为 0 时动画直接跳到终点。
 * 列表的惯性滑动使用自己的倍率，不受影响。
 */
object AppMotionScale : MotionDurationScale {
    override val scaleFactor: Float get() = if (UiPrefsStore.prefs.motion) 1f else 0f
}

val LocalUiPrefs = staticCompositionLocalOf { UiPrefs() }
