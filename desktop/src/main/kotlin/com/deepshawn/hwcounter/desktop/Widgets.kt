@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.deepshawn.hwcounter.desktop

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

// ---------------- 主题（与安卓、网页同一组配色） ----------------

data class Palette(val key: String, val name: String, val a: Color, val b: Color, val forceDark: Boolean = false)

val Palettes = listOf(
    Palette("hyper", "澎湃蓝", Color(0xFF3482FF), Color(0xFF6E5BFF)), Palette("notion", "Notion 风格", Color(0xFF5645D4), Color(0xFF7B3FF2)),
    Palette("mint", "薄荷清新", Color(0xFF12C2A0), Color(0xFF2F8FEA)),
    Palette("academy", "学院蓝", Color(0xFF3A6BFF), Color(0xFF6A3DF0)), Palette("sakura", "樱花粉", Color(0xFFFF7EA5), Color(0xFFB57BFF)),
    Palette("sunset", "暖阳橙", Color(0xFFFFA62B), Color(0xFFFF5E62)), Palette("neon", "赛博霓虹", Color(0xFF7F5CFF), Color(0xFF00D4FF), forceDark = true),
    Palette("forest", "森林物语", Color(0xFF4C8C3F), Color(0xFF1F5E54)), Palette("paper", "纸本手账", Color(0xFFC79A63), Color(0xFF8B5E34)),
    Palette("grape", "葡萄汽水", Color(0xFF9B59B6), Color(0xFF3498DB)), Palette("ink", "水墨极简", Color(0xFF37474F), Color(0xFF90A4AE)),
)

data class Look(val dark: Boolean, val palette: Palette, val motion: Boolean, val usColors: Boolean)
val LocalLook = staticCompositionLocalOf { Look(false, Palettes[0], false, true) }

@Composable
fun HwTheme(prefs: Prefs, content: @Composable () -> Unit) {
    val p = Palettes.firstOrNull { it.key == prefs.theme } ?: Palettes[0]
    val sys = isSystemInDarkTheme()
    val dark = p.forceDark || when (prefs.dark) { 1 -> false; 2 -> true; else -> sys }
    val controller = remember(p.key, dark) {
        if (p.key == "notion") ThemeController(if (dark) ColorSchemeMode.Dark else ColorSchemeMode.Light, lightColors = notionLightColors(), darkColors = notionDarkColors())
        else if (p.key == "hyper") ThemeController(if (dark) ColorSchemeMode.Dark else ColorSchemeMode.Light)
        else ThemeController(if (dark) ColorSchemeMode.MonetDark else ColorSchemeMode.MonetLight, keyColor = p.a)
    }
    MiuixTheme(controller = controller) {
        CompositionLocalProvider(LocalLook provides Look(dark, p, prefs.animations, prefs.usColors), content = content)
    }
}

val GOOD = Color(0xFF16A34A); val BAD = Color(0xFFE5384D); val WARN = Color(0xFFE8792C); val BLUE = Color(0xFF3482FF); val PURPLE = Color(0xFF7A5CFF)
fun rateColor(r: Double?): Color = when { r == null -> Color(0xFF8A8A8F); r >= 0.95 -> GOOD; r >= 0.8 -> BLUE; r >= 0.6 -> WARN; else -> BAD }
fun View.c() = Color(color)

@Composable fun sub() = MiuixTheme.colorScheme.onSurfaceVariantSummary
@Composable fun cardColor() = MiuixTheme.colorScheme.surfaceContainer
@Composable fun track() = MiuixTheme.colorScheme.secondaryContainer

// ---------------- 通用组件（仿手机端 miuix 卡片分组） ----------------

/** 小标题 */
@Composable
fun Title(text: String) = Text(text, Modifier.padding(start = 28.dp, end = 28.dp, top = 16.dp, bottom = 8.dp), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = sub())

/** 卡片分组 */
@Composable
fun Group(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    if (title != null) Title(title)
    Column(Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(cardColor()), content = content)
}

/** 一行：左侧图标 / 标题 + 副标题 / 右侧数值 */
@Composable
fun Cell(title: String, sub: String? = null, left: (@Composable () -> Unit)? = null, right: (@Composable () -> Unit)? = null, dim: Boolean = false, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (left != null) { left(); Spacer(Modifier.width(12.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = if (dim) sub() else MiuixTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!sub.isNullOrBlank()) Text(sub, fontSize = 12.sp, color = sub(), maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        if (right != null) { Spacer(Modifier.width(10.dp)); right() }
        if (onClick != null) Text("›", Modifier.padding(start = 8.dp), fontSize = 20.sp, color = sub())
    }
}

/** 分段选择 */
@Composable
fun <T> Seg(items: List<Pair<T, String>>, value: T, modifier: Modifier = Modifier, onChange: (T) -> Unit) {
    Row(modifier.fillMaxWidth().clip(CircleShape).background(track()).padding(3.dp)) {
        items.forEach { (k, l) ->
            val on = k == value
            Box(
                Modifier.weight(1f).height(34.dp).clip(CircleShape).background(if (on) cardColor() else Color.Transparent).clickable { onChange(k) },
                contentAlignment = Alignment.Center,
            ) { Text(l, fontSize = 13.5.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, color = if (on) MiuixTheme.colorScheme.onSurface else sub(), maxLines = 1) }
        }
    }
}

/** 数字方块 */
@Composable
fun RowScope.Tile(label: String, value: String, color: Color? = null, note: String? = null, noteColor: Color? = null, onClick: (() -> Unit)? = null) {
    Column(
        Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(cardColor()).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color ?: MiuixTheme.colorScheme.onSurface, maxLines = 1)
        Text(label, fontSize = 11.5.sp, color = sub(), maxLines = 1)
        if (note != null) Text(note, fontSize = 11.sp, color = noteColor ?: sub(), maxLines = 1)
    }
}

@Composable
fun Tiles(content: @Composable RowScope.() -> Unit) = Row(Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), content = content)

private val AV = listOf(0xFF5B8DEF, 0xFF12B886, 0xFFF76707, 0xFFAE3EC9, 0xFFE64980, 0xFF1098AD, 0xFF7950F2, 0xFF37B24D, 0xFFF59F00, 0xFF4263EB)
fun avatarColor(name: String): Color { var x = 0L; for (ch in name) x = (x * 31 + ch.code) and 0xFFFFFFFFL; return Color(AV[(x % AV.size).toInt()]) }

@Composable
fun Avatar(name: String, size: Dp = 40.dp, color: Color? = null) {
    Box(Modifier.size(size).clip(CircleShape).background(color ?: avatarColor(name)), contentAlignment = Alignment.Center) {
        Text(if (name.length >= 3) name.takeLast(2) else name.takeLast(1), color = Color.White, fontSize = (size.value * 0.36f).sp, fontWeight = FontWeight.Bold)
    }
}

/** 环形进度 */
@Composable
fun Ring(value: Double, color: Color, size: Dp = 84.dp, stroke: Dp = 8.dp, content: @Composable ColumnScope.() -> Unit) {
    val anim by animateFloatAsState(value.toFloat().coerceIn(0f, 1f), if (LocalLook.current.motion) tween(700) else tween(0), label = "ring")
    val tr = track()
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val s = stroke.toPx(); val r = Size(this.size.width - s, this.size.height - s)
            drawArc(tr, 0f, 360f, false, Offset(s / 2, s / 2), r, style = Stroke(s))
            drawArc(color, -90f, 360f * anim, false, Offset(s / 2, s / 2), r, style = Stroke(s, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) { content() }
    }
}

@Composable
fun Empty(emoji: String = "📭", text: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, fontSize = 46.sp)
        Spacer(Modifier.height(8.dp))
        Text(text, color = sub(), textAlign = TextAlign.Center)
    }
}

/** 胶囊标签 */
@Composable
fun Pill(text: String, color: Color) {
    Text(text, Modifier.clip(CircleShape).background(color.copy(alpha = 0.12f)).padding(horizontal = 9.dp, vertical = 3.dp), color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
}

/** 页面说明文字 */
@Composable
fun Meta(text: String) = Text(text, Modifier.padding(start = 28.dp, end = 28.dp, bottom = 12.dp), fontSize = 13.sp, color = sub())

/** 一组可点的名字标签 */
@Composable
fun Chips(items: List<Pair<String, String>>, color: Color, onClick: ((Int) -> Unit)? = null) {
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEachIndexed { i, (t, tag) ->
            Text(
                if (tag.isBlank()) t else "$t · $tag",
                Modifier.clip(CircleShape).background(color.copy(alpha = 0.13f)).then(if (onClick != null) Modifier.clickable { onClick(i) } else Modifier).padding(horizontal = 10.dp, vertical = 4.dp),
                fontSize = 12.5.sp,
            )
        }
    }
}

/** 渐变色块（主题选择） */
@Composable
fun Swatch(p: Palette, on: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(64.dp).clickable(onClick = onClick).padding(4.dp)) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(Brush.linearGradient(listOf(p.a, p.b))).then(if (on) Modifier.border(2.dp, MiuixTheme.colorScheme.primary, CircleShape) else Modifier))
        Spacer(Modifier.height(4.dp))
        Text(p.name, fontSize = 11.5.sp, color = if (on) MiuixTheme.colorScheme.onSurface else sub(), fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
    }
}

// ---------------- Notion 风格：暖灰底、白卡片、细边框、紫色主色（#5645D4）、深墨文字 ----------------
fun notionLightColors() = top.yukonga.miuix.kmp.theme.lightColorScheme(
    primary = Color(0xFF5645D4), primaryVariant = Color(0xFF4534B3), onPrimaryVariant = Color(0xFFD6CFF6),
    error = Color(0xFFE03131), errorContainer = Color(0xFFFDECEC),
    disabledPrimary = Color(0xFFD9D4F4), disabledPrimaryButton = Color(0xFFD9D4F4), disabledPrimarySlider = Color(0xFFD9D4F4),
    primaryContainer = Color(0xFF6E5FDD),
    secondary = Color(0xFFE5E3DF), secondaryVariant = Color(0xFFF0EEEC), onSecondaryVariant = Color(0xFF37352F),
    secondaryContainer = Color(0xFFF0EEEC), onSecondaryContainer = Color(0xFFA4A097), secondaryContainerVariant = Color(0xFFE5E3DF),
    tertiaryContainer = Color(0xFFE6E0F5), onTertiaryContainer = Color(0xFF5645D4), tertiaryContainerVariant = Color(0xFFE6E0F5),
    background = Color.White, onBackground = Color(0xFF37352F),
    surface = Color(0xFFF6F5F4), onSurface = Color(0xFF37352F), surfaceVariant = Color.White,
    onSurfaceSecondary = Color(0xFF5D5B54), onSurfaceVariantSummary = Color(0xFF787671), onSurfaceVariantActions = Color(0xFFA4A097),
    surfaceContainer = Color.White, onSurfaceContainer = Color(0xFF37352F), surfaceContainerHigh = Color(0xFFEDE9E4), surfaceContainerHighest = Color(0xFFE5E3DF),
    outline = Color(0xFFC8C4BE), dividerLine = Color(0xFFE5E3DF),
    sliderKeyPointForeground = Color(0xFF5645D4), sliderBackground = Color(0x14373530),
)
fun notionDarkColors() = top.yukonga.miuix.kmp.theme.darkColorScheme(
    primary = Color(0xFF7B6BE6), primaryVariant = Color(0xFF6E5FDD), onPrimaryVariant = Color(0xFFC9C1F5),
    error = Color(0xFFFF6B6B), errorContainer = Color(0xFF3A1717),
    disabledPrimary = Color(0xFF332C5C), disabledPrimaryButton = Color(0xFF332C5C), disabledPrimarySlider = Color(0xFF332C5C),
    primaryContainer = Color(0xFF8C7EEA),
    secondary = Color(0xFF373737), secondaryVariant = Color(0xFF2F2F2F), onSecondaryVariant = Color(0xFFEBEBEA),
    secondaryContainer = Color(0xFF2A2A2A), onSecondaryContainer = Color(0xFF9B9A97), secondaryContainerVariant = Color(0xFF373737),
    tertiaryContainer = Color(0xFF2B2546), onTertiaryContainer = Color(0xFFA99FF0), tertiaryContainerVariant = Color(0xFF2F2F2F),
    background = Color(0xFF202020), onBackground = Color(0xFFEBEBEA),
    surface = Color(0xFF191919), onSurface = Color(0xFFEBEBEA), surfaceVariant = Color(0xFF202020),
    onSurfaceSecondary = Color(0xFFC7C6C4), onSurfaceVariantSummary = Color(0xFF9B9A97), onSurfaceVariantActions = Color(0xFF6F6E6B),
    surfaceContainer = Color(0xFF202020), onSurfaceContainer = Color(0xFFEBEBEA), surfaceContainerHigh = Color(0xFF2F2F2F), surfaceContainerHighest = Color(0xFF373737),
    outline = Color(0xFF3F3F3F), dividerLine = Color(0xFF2F2F2F),
    sliderKeyPointForeground = Color(0xFF7B6BE6), sliderBackground = Color(0x29FFFFFF),
)
