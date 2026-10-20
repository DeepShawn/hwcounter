@file:OptIn(ExperimentalMaterial3Api::class)

package com.deepshawn.hwcounter.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepshawn.hwcounter.data.HwStatus
import com.deepshawn.hwcounter.ui.theme.LocalExtra
import com.deepshawn.hwcounter.ui.theme.StatusColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun statusColor(s: HwStatus): Color = StatusColors.of(s, LocalExtra.current.isDark)

@Composable
fun statusContainer(s: HwStatus): Color = StatusColors.container(s, LocalExtra.current.isDark)

@Composable
fun gradientBrush(): Brush = Brush.linearGradient(LocalExtra.current.gradient)

/** 渐变头部 */
@Composable
fun GradientHeader(
    modifier: Modifier = Modifier,
    bottomRadius: Dp = 32.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = bottomRadius, bottomEnd = bottomRadius))
            .background(gradientBrush()),
    ) {
        // 装饰气泡
        Canvas(Modifier.matchParentSize()) {
            drawCircle(Color.White.copy(alpha = 0.10f), radius = size.minDimension * 0.55f, center = Offset(size.width * 0.92f, size.height * 0.1f))
            drawCircle(Color.White.copy(alpha = 0.07f), radius = size.minDimension * 0.35f, center = Offset(size.width * 0.05f, size.height * 0.95f))
        }
        Column(
            Modifier
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp),
            content = content,
        )
    }
}

/** 动画进度环 */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    stroke: Dp = 12.dp,
    track: Color = Color.White.copy(alpha = 0.25f),
    colors: List<Color> = listOf(Color.White, Color.White),
    content: @Composable BoxScope.() -> Unit = {},
) {
    val anim by animateFloatAsState(progress.coerceIn(0f, 1f), tween(900, easing = FastOutSlowInEasing), label = "ring")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = stroke.toPx()
            val d = this.size.minDimension - sw
            val tl = Offset((this.size.width - d) / 2, (this.size.height - d) / 2)
            drawArc(track, 0f, 360f, false, tl, Size(d, d), style = Stroke(sw))
            rotate(-90f) {
                drawArc(
                    Brush.sweepGradient(colors + colors.first()), 0f, 360f * anim, false, tl, Size(d, d),
                    style = Stroke(sw, cap = StrokeCap.Round),
                )
            }
        }
        content()
    }
}

/** 多段状态条 */
@Composable
fun StackedBar(parts: List<Pair<Color, Int>>, modifier: Modifier = Modifier, height: Dp = 10.dp) {
    val total = parts.sumOf { it.second }.coerceAtLeast(1)
    Row(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        parts.filter { it.second > 0 }.forEach { (c, n) ->
            Box(
                Modifier
                    .weight(n.toFloat() / total)
                    .fillMaxSize()
                    .background(c),
            )
        }
    }
}

@Composable
fun StatusPill(s: HwStatus, modifier: Modifier = Modifier, small: Boolean = false) {
    val c = statusColor(s)
    Box(
        modifier
            .clip(CircleShape)
            .background(statusContainer(s))
            .padding(horizontal = if (small) 6.dp else 10.dp, vertical = if (small) 1.dp else 4.dp),
    ) {
        Text(s.label, color = c, fontSize = if (small) 10.sp else 12.sp, fontWeight = FontWeight.Bold)
    }
}

/** 头像：取名字最后一个字 */
@Composable
fun Avatar(name: String, modifier: Modifier = Modifier, size: Dp = 40.dp, color: Color? = null) {
    val palette = listOf(
        Color(0xFF5B8DEF), Color(0xFF12B886), Color(0xFFF76707), Color(0xFFAE3EC9), Color(0xFFE64980),
        Color(0xFF1098AD), Color(0xFF7950F2), Color(0xFF37B24D), Color(0xFFF59F00), Color(0xFF4263EB),
    )
    val c = color ?: palette[(name.hashCode() and 0x7fffffff) % palette.size]
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(c, c.copy(alpha = 0.7f)))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.takeLast(if (name.length >= 3 && name.all { it.code > 0x2E80 }) 2 else 1).ifEmpty { "?" },
            color = Color.White, fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.36f).sp, maxLines = 1,
        )
    }
}

@Composable
fun StatTile(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier.then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        shape = MaterialTheme.shapes.medium,
        color = color.copy(alpha = if (LocalExtra.current.isDark) 0.18f else 0.10f),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(label, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1)
            }
            Spacer(Modifier.height(2.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 4.dp, height = 16.dp).clip(CircleShape).background(gradientBrush()))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        action()
    }
}

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.then(if (onClick != null) Modifier.clip(MaterialTheme.shapes.large).clickable { onClick() } else Modifier),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        tonalElevation = 1.dp,
        shadowElevation = if (LocalExtra.current.isDark) 0.dp else 1.dp,
    ) {
        Column(Modifier.padding(padding), content = content)
    }
}

@Composable
fun EmptyState(emoji: String, title: String, sub: String, modifier: Modifier = Modifier, action: @Composable () -> Unit = {}) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, fontSize = 56.sp)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        action()
    }
}

/** 竖向柱状图 */
@Composable
fun BarChart(values: List<Float>, labels: List<String>, modifier: Modifier = Modifier, maxValue: Float = 1f, valueFmt: (Float) -> String = { "${(it * 100).toInt()}%" }) {
    val brush = gradientBrush()
    val anim = remember { Animatable(0f) }
    LaunchedEffect(values) { anim.snapTo(0f); anim.animateTo(1f, tween(800)) }
    val onSurface = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier) {
        Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
            values.forEach { v ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(valueFmt(v), fontSize = 9.sp, color = onSurface, maxLines = 1)
                    Spacer(Modifier.height(2.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height((130 * (v / maxValue).coerceIn(0.02f, 1f) * anim.value).dp)
                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                            .background(brush),
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            labels.forEach { Text(it, Modifier.weight(1f), fontSize = 9.sp, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Clip, color = onSurface) }
        }
    }
}

/** 环形图 */
@Composable
fun Donut(parts: List<Pair<Color, Float>>, modifier: Modifier = Modifier, stroke: Dp = 22.dp, center: @Composable BoxScope.() -> Unit = {}) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(parts) { anim.snapTo(0f); anim.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    val total = parts.sumOf { it.second.toDouble() }.toFloat().coerceAtLeast(0.0001f)
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = stroke.toPx()
            val d = size.minDimension - sw
            val tl = Offset((size.width - d) / 2, (size.height - d) / 2)
            var start = -90f
            parts.forEach { (c, v) ->
                val sweep = 360f * v / total * anim.value
                if (sweep > 0f) drawArc(c, start, (sweep - 1.5f).coerceAtLeast(0.5f), false, tl, Size(d, d), style = Stroke(sw, cap = StrokeCap.Butt))
                start += sweep
            }
        }
        center()
    }
}

// ---------------- 彩带庆祝 ----------------
private class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float, val color: Color, var rot: Float, val vr: Float, val w: Float, val h: Float)

/** trigger 每变化一次放一次彩带 */
@Composable
fun Confetti(trigger: Int, modifier: Modifier = Modifier) {
    if (trigger <= 0) return
    val colors = listOf(Color(0xFFFF5E62), Color(0xFFFFC107), Color(0xFF00C853), Color(0xFF2979FF), Color(0xFFD500F9), Color(0xFF00E5FF))
    val particles = remember(trigger) {
        val r = Random(trigger)
        List(140) {
            val ang = (-PI / 2 + (r.nextFloat() - 0.5f) * PI * 0.9).toFloat()
            val sp = 900f + r.nextFloat() * 1300f
            Particle(0.5f, 1.05f, cos(ang) * sp, sin(ang) * sp, colors[r.nextInt(colors.size)], r.nextFloat() * 360f, (r.nextFloat() - 0.5f) * 720f, 8f + r.nextFloat() * 10f, 5f + r.nextFloat() * 6f)
        }
    }
    var frame by remember(trigger) { mutableLongStateOf(0L) }
    var alive by remember(trigger) { mutableLongStateOf(1L) }
    LaunchedEffect(trigger) {
        val start = withFrameNanos { it }
        var last = start
        while (true) {
            val now = withFrameNanos { it }
            val dt = ((now - last) / 1e9f).coerceAtMost(0.05f)
            last = now
            particles.forEach { p ->
                p.vy += 1600f * dt
                p.vx *= 0.99f
                p.x += p.vx * dt / 1000f
                p.y += p.vy * dt / 1000f
                p.rot += p.vr * dt
            }
            frame = now
            if ((now - start) > 4_000_000_000L) { alive = 0L; break }
        }
    }
    if (alive == 0L) return
    Canvas(modifier.fillMaxSize()) {
        if (frame < 0L) return@Canvas
        particles.forEach { p ->
            val px = p.x * size.width
            val py = p.y * size.height
            if (py < size.height + 40) {
                rotate(p.rot, Offset(px, py)) {
                    drawRect(p.color, Offset(px - p.w / 2, py - p.h / 2), Size(p.w * density / 2, p.h * density / 2))
                }
            }
        }
    }
}

@Composable
fun Chip2(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Box(
        modifier
            .clip(CircleShape)
            .background(if (selected) color else MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(text, color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
