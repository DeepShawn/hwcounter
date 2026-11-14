package com.deepshawn.hwcounter.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepshawn.hwcounter.data.HwStatus

@Immutable
data class AppPalette(
    val key: String,
    val name: String,
    val emoji: String,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val lightBg: Color,
    val darkBg: Color,
    val gradient: List<Color>,
    val forceDark: Boolean = false,
)

val Palettes = listOf(
    AppPalette("hyper", "澎湃蓝", "💧", Color(0xFF3482FF), Color(0xFF00B4FF), Color(0xFFFF9F0A), Color(0xFFF7F7F7), Color(0xFF000000), listOf(Color(0xFF3482FF), Color(0xFF6E5BFF))),
    AppPalette("mint", "薄荷清新", "🌿", Color(0xFF0FA38A), Color(0xFF3D8BFD), Color(0xFFFFB74D), Color(0xFFF4FBF8), Color(0xFF0F1715), listOf(Color(0xFF12C2A0), Color(0xFF2F8FEA))),
    AppPalette("academy", "学院蓝", "🎓", Color(0xFF2F5BEA), Color(0xFF00A6D6), Color(0xFFFF8A65), Color(0xFFF5F7FF), Color(0xFF0E1220), listOf(Color(0xFF3A6BFF), Color(0xFF6A3DF0))),
    AppPalette("sakura", "樱花粉", "🌸", Color(0xFFE2557B), Color(0xFFB370E0), Color(0xFFFFB300), Color(0xFFFFF6F8), Color(0xFF1C1215), listOf(Color(0xFFFF7EA5), Color(0xFFB57BFF))),
    AppPalette("sunset", "暖阳橙", "🌅", Color(0xFFF07C1F), Color(0xFFE53935), Color(0xFF8E7CC3), Color(0xFFFFF8F1), Color(0xFF1C140E), listOf(Color(0xFFFFA62B), Color(0xFFFF5E62))),
    AppPalette("neon", "赛博霓虹", "🌃", Color(0xFF8C6CFF), Color(0xFF00E5FF), Color(0xFFFF4FD8), Color(0xFFF6F3FF), Color(0xFF0B0A14), listOf(Color(0xFF7F5CFF), Color(0xFF00D4FF)), forceDark = true),
    AppPalette("forest", "森林物语", "🌲", Color(0xFF3E7B3A), Color(0xFF8D6E3F), Color(0xFF4FA3A5), Color(0xFFF6F8F1), Color(0xFF111610), listOf(Color(0xFF4C8C3F), Color(0xFF1F5E54))),
    AppPalette("paper", "纸本手账", "📒", Color(0xFF8B5E34), Color(0xFFB5523B), Color(0xFF5B7F6B), Color(0xFFFAF4E8), Color(0xFF1A1611), listOf(Color(0xFFC79A63), Color(0xFF8B5E34))),
    AppPalette("grape", "葡萄汽水", "🍇", Color(0xFF8E44AD), Color(0xFF16A085), Color(0xFFF39C12), Color(0xFFFAF5FF), Color(0xFF15101A), listOf(Color(0xFF9B59B6), Color(0xFF3498DB))),
    AppPalette("ink", "水墨极简", "🖌️", Color(0xFF263238), Color(0xFF607D8B), Color(0xFFD84315), Color(0xFFFAFAFA), Color(0xFF101213), listOf(Color(0xFF37474F), Color(0xFF90A4AE))),
    AppPalette("dynamic", "动态取色", "🎨", Color(0xFF6750A4), Color(0xFF625B71), Color(0xFF7D5260), Color(0xFFFFFBFE), Color(0xFF1C1B1F), listOf(Color(0xFF6750A4), Color(0xFF7D5260))),
)

fun paletteOf(key: String) = Palettes.firstOrNull { it.key == key } ?: Palettes[0]

@Immutable
data class Extra(
    val palette: AppPalette,
    val gradient: List<Color>,
    val isDark: Boolean,
    val compact: Boolean,
    val corner: Int,
)

val LocalExtra = staticCompositionLocalOf {
    Extra(Palettes[0], Palettes[0].gradient, false, false, 20)
}

private fun Color.mix(o: Color, t: Float) = Color(
    red = red + (o.red - red) * t, green = green + (o.green - green) * t,
    blue = blue + (o.blue - blue) * t, alpha = 1f,
)

object StatusColors {
    fun of(s: HwStatus, dark: Boolean): Color = when (s) {
        HwStatus.NONE -> if (dark) Color(0xFF5C5F66) else Color(0xFFB8BCC4)
        HwStatus.SUBMITTED -> Color(0xFF1DB57A)
        HwStatus.EXCELLENT -> if (dark) Color(0xFFFFB300) else Color(0xFFD99A00)
        HwStatus.LATE -> Color(0xFFFF8A3D)
        HwStatus.MISSING -> Color(0xFFF0475A)
        HwStatus.LEAVE -> Color(0xFF4A8CFF)
    }

    fun container(s: HwStatus, dark: Boolean): Color = of(s, dark).copy(alpha = if (dark) 0.22f else 0.14f)
}

/** miuix 颜色 → Material3 颜色：未重写的旧页面（Material 组件）也跟随 HyperOS 配色 */
private fun bridge(m: top.yukonga.miuix.kmp.theme.Colors, dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = m.primary, onPrimary = m.onPrimary,
        primaryContainer = m.tertiaryContainer, onPrimaryContainer = m.onTertiaryContainer,
        secondary = m.primary.mix(if (dark) Color.White else Color.Black, 0.15f), onSecondary = m.onPrimary,
        secondaryContainer = m.secondaryContainer, onSecondaryContainer = m.onSecondaryVariant,
        tertiary = m.primaryVariant, tertiaryContainer = m.tertiaryContainerVariant, onTertiaryContainer = m.onTertiaryContainer,
        background = m.surface, onBackground = m.onSurface,
        surface = m.surface, onSurface = m.onSurface,
        surfaceVariant = m.secondaryContainer, onSurfaceVariant = m.onSurfaceVariantSummary,
        surfaceContainerLowest = m.surfaceContainer, surfaceContainerLow = m.surfaceContainer,
        surfaceContainer = m.surfaceContainer, surfaceContainerHigh = m.secondaryContainer,
        surfaceContainerHighest = m.surfaceContainerHigh,
        inverseSurface = m.onSurface, inverseOnSurface = m.surface,
        outline = m.outline, outlineVariant = m.dividerLine,
        error = m.error, onError = m.onError, errorContainer = m.errorContainer, onErrorContainer = m.onErrorContainer,
        scrim = m.windowDimming,
    )
}

@Composable
fun HwTheme(
    paletteKey: String,
    darkMode: Int,
    shapeStyle: Int,
    compact: Boolean,
    ui: UiPrefs = UiPrefs(),
    content: @Composable () -> Unit,
) {
    val p = paletteOf(paletteKey)
    val dark = p.forceDark || when (darkMode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    // 澎湃蓝 = miuix 原生配色；动态取色 = 系统壁纸取色（Android 12+）；其余主题用主色生成 Monet 色板
    val controller = remember(p.key, dark) {
        when {
            p.key == "hyper" -> top.yukonga.miuix.kmp.theme.ThemeController(
                if (dark) top.yukonga.miuix.kmp.theme.ColorSchemeMode.Dark else top.yukonga.miuix.kmp.theme.ColorSchemeMode.Light,
            )
            p.key == "dynamic" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> top.yukonga.miuix.kmp.theme.ThemeController(
                if (dark) top.yukonga.miuix.kmp.theme.ColorSchemeMode.MonetDark else top.yukonga.miuix.kmp.theme.ColorSchemeMode.MonetLight,
            )
            else -> top.yukonga.miuix.kmp.theme.ThemeController(
                if (dark) top.yukonga.miuix.kmp.theme.ColorSchemeMode.MonetDark else top.yukonga.miuix.kmp.theme.ColorSchemeMode.MonetLight,
                keyColor = p.primary,
            )
        }
    }
    val corner = when (shapeStyle) { 0 -> 8; 2 -> 28; else -> 18 }
    val shapes = Shapes(
        extraSmall = RoundedCornerShape((corner / 3).dp),
        small = RoundedCornerShape((corner / 2).dp),
        medium = RoundedCornerShape(corner.dp),
        large = RoundedCornerShape((corner + 4).dp),
        extraLarge = RoundedCornerShape((corner + 10).dp),
    )
    val base = Typography()
    val typography = base.copy(
        headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
    )
    top.yukonga.miuix.kmp.theme.MiuixTheme(controller = controller, smoothRounding = !ui.lowPerf) {
        val m = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
        val scheme = bridge(m, dark)
        val gradient = if (p.key == "dynamic" || p.key == "hyper") listOf(m.primary, m.primary.mix(Color(0xFF7A5CFF), 0.45f)) else p.gradient
        CompositionLocalProvider(
            LocalExtra provides Extra(p, gradient, dark, compact, corner),
            LocalUiPrefs provides ui,
            // 低性能模式：关闭越界回弹（拉伸/弹簧）以减少绘制
            androidx.compose.foundation.LocalOverscrollFactory provides if (ui.lowPerf) null else androidx.compose.foundation.LocalOverscrollFactory.current,
        ) {
            MaterialTheme(colorScheme = scheme, shapes = shapes, typography = typography) {
                // 根布局不再是 Material Scaffold，需要自己给 Material 文字/图标默认颜色，否则深色模式下是黑字
                CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides scheme.onSurface, content = content)
            }
        }
    }
}
