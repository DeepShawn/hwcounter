package com.deepshawn.hwcounter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.deepshawn.hwcounter.ui.theme.LocalExtra
import com.deepshawn.hwcounter.ui.theme.LocalUiPrefs
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 根布局记录下来的页面内容，悬浮的玻璃控件从这里取背景 */
val LocalBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/** 悬浮栏、底部导航等占用的底部空间，滚动列表需要留出 */
val LocalBottomInset = staticCompositionLocalOf { 0.dp }

/**
 * 液态玻璃表面：模糊 + 鲜艳度 + 边缘折射（Android 13+）+ 高光描边。
 * 关闭玻璃或低性能模式时退化为半透明纯色，外观一致、开销极低。
 *
 * @param refraction 折射高度（只对圆角矩形 / 胶囊生效）
 */
@Composable
fun Modifier.glass(
    shape: Shape = RoundedCornerShape(28.dp),
    backdrop: Backdrop? = LocalBackdrop.current,
    tint: Color = MiuixTheme.colorScheme.surfaceContainer,
    blurRadius: Dp = 12.dp,
    refraction: Dp = 18.dp,
    elevated: Boolean = true,
): Modifier {
    val ui = LocalUiPrefs.current
    val dark = LocalExtra.current.isDark
    if (backdrop == null || !ui.glassOn) {
        val base = if (elevated && !ui.lowPerf) this.shadow(10.dp, shape, ambientColor = Color.Black.copy(alpha = 0.08f), spotColor = Color.Black.copy(alpha = 0.12f)) else this
        return base.clip(shape).background(tint.copy(alpha = if (ui.lowPerf) 1f else 0.96f))
            .border(0.5.dp, (if (dark) Color.White else Color.Black).copy(alpha = 0.06f), shape)
    }
    val lensOn = ui.lensOn
    return this.drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            blur(blurRadius.toPx())
            if (lensOn && refraction > 0.dp) lens(refraction.toPx(), refraction.toPx() * 1.6f)
        },
        highlight = { Highlight.Default.copy(alpha = if (dark) 0.55f else 0.9f) },
        shadow = if (elevated) ({ Shadow(radius = 18.dp, color = Color.Black.copy(alpha = if (dark) 0.35f else 0.10f)) }) else null,
        onDrawSurface = { drawRect(tint.copy(alpha = if (dark) 0.42f else 0.55f)) },
    )
}

/** 顶栏等矩形区域的磨砂玻璃（矩形不能折射，只模糊） */
@Composable
fun Modifier.frosted(backdrop: Backdrop?, tint: Color = MiuixTheme.colorScheme.surface): Modifier {
    val ui = LocalUiPrefs.current
    if (backdrop == null || !ui.glassOn) return this.background(tint)
    val dark = LocalExtra.current.isDark
    return this.drawBackdrop(
        backdrop = backdrop,
        shape = { androidx.compose.ui.graphics.RectangleShape },
        effects = { vibrancy(); blur(24.dp.toPx()) },
        highlight = null,
        shadow = null,
        onDrawSurface = { drawRect(tint.copy(alpha = if (dark) 0.62f else 0.72f)) },
    )
}
