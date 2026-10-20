package com.deepshawn.hwcounter.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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

private fun schemeFor(p: AppPalette, dark: Boolean): ColorScheme = if (!dark) lightColorScheme(
    primary = p.primary,
    onPrimary = Color.White,
    primaryContainer = p.primary.mix(Color.White, 0.82f),
    onPrimaryContainer = p.primary.mix(Color.Black, 0.55f),
    secondary = p.secondary,
    onSecondary = Color.White,
    secondaryContainer = p.secondary.mix(Color.White, 0.85f),
    onSecondaryContainer = p.secondary.mix(Color.Black, 0.55f),
    tertiary = p.tertiary,
    tertiaryContainer = p.tertiary.mix(Color.White, 0.82f),
    onTertiaryContainer = p.tertiary.mix(Color.Black, 0.6f),
    background = p.lightBg,
    surface = p.lightBg,
    surfaceVariant = p.primary.mix(Color(0xFFF2F2F2), 0.9f),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = p.lightBg.mix(Color.White, 0.6f),
    surfaceContainer = p.lightBg.mix(p.primary, 0.04f),
    surfaceContainerHigh = p.lightBg.mix(p.primary, 0.07f),
    surfaceContainerHighest = p.lightBg.mix(p.primary, 0.1f),
    outlineVariant = p.primary.mix(Color(0xFFDDDDDD), 0.85f),
) else darkColorScheme(
    primary = p.primary.mix(Color.White, 0.25f),
    onPrimary = Color(0xFF101010),
    primaryContainer = p.primary.mix(Color.Black, 0.55f),
    onPrimaryContainer = p.primary.mix(Color.White, 0.75f),
    secondary = p.secondary.mix(Color.White, 0.25f),
    secondaryContainer = p.secondary.mix(Color.Black, 0.6f),
    onSecondaryContainer = p.secondary.mix(Color.White, 0.75f),
    tertiary = p.tertiary.mix(Color.White, 0.2f),
    tertiaryContainer = p.tertiary.mix(Color.Black, 0.6f),
    onTertiaryContainer = p.tertiary.mix(Color.White, 0.75f),
    background = p.darkBg,
    surface = p.darkBg,
    surfaceVariant = p.darkBg.mix(p.primary, 0.14f),
    surfaceContainerLowest = p.darkBg.mix(Color.Black, 0.4f),
    surfaceContainerLow = p.darkBg.mix(p.primary, 0.05f),
    surfaceContainer = p.darkBg.mix(p.primary, 0.08f),
    surfaceContainerHigh = p.darkBg.mix(p.primary, 0.12f),
    surfaceContainerHighest = p.darkBg.mix(p.primary, 0.16f),
    outlineVariant = p.darkBg.mix(p.primary, 0.3f),
)

object StatusColors {
    fun of(s: HwStatus, dark: Boolean): Color = when (s) {
        HwStatus.NONE -> if (dark) Color(0xFF5C5F66) else Color(0xFFB8BCC4)
        HwStatus.SUBMITTED -> Color(0xFF1DB57A)
        HwStatus.EXCELLENT -> Color(0xFFFFB300)
        HwStatus.LATE -> Color(0xFFFF8A3D)
        HwStatus.MISSING -> Color(0xFFF0475A)
        HwStatus.LEAVE -> Color(0xFF4A8CFF)
    }

    fun container(s: HwStatus, dark: Boolean): Color = of(s, dark).copy(alpha = if (dark) 0.22f else 0.14f)
}

@Composable
fun HwTheme(
    paletteKey: String,
    darkMode: Int,
    shapeStyle: Int,
    compact: Boolean,
    content: @Composable () -> Unit,
) {
    val p = paletteOf(paletteKey)
    val dark = p.forceDark || when (darkMode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    val ctx = LocalContext.current
    val scheme = if (p.key == "dynamic" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else schemeFor(p, dark)
    val gradient = if (p.key == "dynamic") listOf(scheme.primary, scheme.tertiary) else p.gradient
    val corner = when (shapeStyle) { 0 -> 6; 2 -> 30; else -> 18 }
    val shapes = Shapes(
        extraSmall = RoundedCornerShape((corner / 3).dp),
        small = RoundedCornerShape((corner / 2).dp),
        medium = RoundedCornerShape(corner.dp),
        large = RoundedCornerShape((corner + 6).dp),
        extraLarge = RoundedCornerShape((corner + 12).dp),
    )
    val base = Typography()
    val typography = base.copy(
        headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Black),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp),
    )
    CompositionLocalProvider(LocalExtra provides Extra(p, gradient, dark, compact, corner)) {
        MaterialTheme(colorScheme = scheme, shapes = shapes, typography = typography, content = content)
    }
}
