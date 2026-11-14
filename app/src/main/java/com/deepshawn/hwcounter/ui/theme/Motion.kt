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
 * - glass：液态玻璃效果（背景模糊 + 折射），需要 Android 12+，折射需要 Android 13+。
 */
@Immutable
data class UiPrefs(
    val animations: Boolean = false,
    val lowPerf: Boolean = false,
    val glass: Boolean = true,
) {
    /** 实际是否播放动画 */
    val motion: Boolean get() = animations && !lowPerf
    /** 实际是否绘制液态玻璃（模糊需要 Android 12） */
    val glassOn: Boolean get() = glass && !lowPerf && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
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
