package com.deepshawn.hwcounter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
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

// ---------------- 全局液态玻璃：彩色柔光背景 + 玻璃卡片 / 选项卡 ----------------

/** 页面背后的柔光背景层（只有打开液态玻璃时才有），玻璃卡片从这里取样 */
val LocalGlassBg = staticCompositionLocalOf<Backdrop?> { null }

/**
 * 柔光背景：在底色上铺几团主题色的径向渐变光斑。本身就很柔和，
 * 上面的半透明面板看起来就是“毛玻璃”，真正的模糊只用在卡片和栏上。
 */
@Composable
fun GlassBackground(modifier: Modifier = Modifier) {
    val colors = MiuixTheme.colorScheme
    val extra = LocalExtra.current
    val dark = extra.isDark
    val c1 = extra.gradient.first(); val c2 = extra.gradient.last()
    val c3 = androidx.compose.ui.graphics.lerp(c1, Color(0xFFFF9F6B), 0.55f)
    androidx.compose.foundation.Canvas(modifier.background(colors.surface)) {
        val w = size.width; val h = size.height
        fun blob(c: Color, a: Float, cx: Float, cy: Float, r: Float) = drawCircle(
            androidx.compose.ui.graphics.Brush.radialGradient(listOf(c.copy(alpha = a), c.copy(alpha = 0f)), center = androidx.compose.ui.geometry.Offset(cx, cy), radius = r),
            radius = r, center = androidx.compose.ui.geometry.Offset(cx, cy),
        )
        val k = if (dark) 1.25f else 1f
        blob(c1, 0.30f * k, w * 0.05f, h * 0.08f, w * 0.95f)
        blob(c2, 0.24f * k, w * 1.02f, h * 0.38f, w * 0.85f)
        blob(c3, 0.20f * k, w * 0.15f, h * 0.78f, w * 0.90f)
        blob(c1, 0.16f * k, w * 0.90f, h * 1.02f, w * 0.75f)
    }
}

/** 是否在用全局玻璃（打开液态玻璃且非低性能，背景层已提供） */
@Composable
fun glassActive(): Boolean = LocalGlassBg.current != null && LocalUiPrefs.current.glassOn

/**
 * 玻璃面板：模糊 + 鲜艳度 + 轻微折射 + 高光描边，叠一层半透明底色；
 * 没有玻璃时是普通的卡片底色。
 */
@Composable
fun Modifier.glassPanel(
    shape: Shape = RoundedCornerShape(18.dp),
    tint: Color = MiuixTheme.colorScheme.surfaceContainer,
    refraction: Dp = 10.dp,
): Modifier {
    val bg = LocalGlassBg.current
    val ui = LocalUiPrefs.current
    if (bg == null || !ui.glassOn) return this.clip(shape).background(tint)
    val dark = LocalExtra.current.isDark
    val lensOn = ui.lensOn
    return this.drawBackdrop(
        backdrop = bg,
        shape = { shape },
        effects = {
            vibrancy()
            blur(18.dp.toPx())
            if (lensOn && refraction > 0.dp) lens(refraction.toPx(), refraction.toPx() * 1.8f)
        },
        highlight = { Highlight.Default.copy(alpha = if (dark) 0.45f else 0.85f) },
        shadow = { Shadow(radius = 12.dp, color = Color.Black.copy(alpha = if (dark) 0.25f else 0.06f)) },
        onDrawSurface = { drawRect(tint.copy(alpha = if (dark) 0.40f else 0.52f)) },
    ).clip(shape)
}

/** 列表行的毛玻璃底色（行很多，不逐行模糊，只半透明，滚动也流畅） */
@Composable
fun rowTint(): Color {
    val c = MiuixTheme.colorScheme.surfaceContainer
    return if (glassActive()) c.copy(alpha = if (LocalExtra.current.isDark) 0.46f else 0.58f) else c
}

/** 卡片：玻璃开启时为液态玻璃，否则为 HyperOS 卡片 */
@Composable
fun HCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    radius: Dp = 18.dp,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    androidx.compose.foundation.layout.Column(
        modifier.glassPanel(shape)
            .then(
                if (onClick != null || onLongClick != null) Modifier.combinedClickableCompat(onClick ?: {}, onLongClick) else Modifier,
            ),
        content = content,
    )
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun Modifier.combinedClickableCompat(onClick: () -> Unit, onLongClick: (() -> Unit)?): Modifier =
    this.combinedClickable(onClick = onClick, onLongClick = onLongClick)

/** 分段选项卡：玻璃开启时整条为液态玻璃，选中项为浮起的亮色胶囊 */
@Composable
fun HTabs(tabs: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, height: Dp = 40.dp) {
    val colors = MiuixTheme.colorScheme
    val glass = glassActive()
    val dark = LocalExtra.current.isDark
    val shape = RoundedCornerShape(height / 2)
    androidx.compose.foundation.layout.Row(
        modifier.fillMaxWidth().height(height)
            .glassPanel(shape, tint = if (glass) colors.surfaceContainer else colors.secondaryContainer, refraction = 6.dp)
            .padding(3.dp),
    ) {
        tabs.forEachIndexed { i, t ->
            val sel = i == selected
            val bg by androidx.compose.animation.animateColorAsState(
                if (sel) (if (dark) colors.surfaceContainerHigh else Color.White).copy(alpha = if (glass) 0.92f else 1f) else Color.Transparent, label = "tab",
            )
            androidx.compose.foundation.layout.Box(
                Modifier.weight(1f).fillMaxSize()
                    .then(if (sel && !LocalUiPrefs.current.lowPerf) Modifier.shadow(3.dp, RoundedCornerShape(height / 2), ambientColor = Color.Black.copy(alpha = 0.08f), spotColor = Color.Black.copy(alpha = 0.10f)) else Modifier)
                    .clip(RoundedCornerShape(height / 2)).background(bg)
                    .clickable { onSelect(i) }
                    .semantics { this.selected = sel; this.role = androidx.compose.ui.semantics.Role.Tab },
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) {
                top.yukonga.miuix.kmp.basic.Text(
                    t, fontSize = 14.sp, maxLines = 1,
                    fontWeight = if (sel) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal,
                    color = if (sel) colors.onSurface else colors.onSurfaceVariantSummary,
                )
            }
        }
    }
}
