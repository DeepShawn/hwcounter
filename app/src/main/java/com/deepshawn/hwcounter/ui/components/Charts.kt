package com.deepshawn.hwcounter.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepshawn.hwcounter.data.HwStatus
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

/** 图表里状态的固定顺序：优秀与迟交（黄/橙）永不相邻（已用色盲校验脚本验证） */
val ChartStatusOrder = listOf(HwStatus.SUBMITTED, HwStatus.EXCELLENT, HwStatus.LEAVE, HwStatus.LATE, HwStatus.MISSING, HwStatus.NONE)

fun pctText(x: Float) = "${(x * 100).roundToInt()}%"

/** 进入时从 0 长到 1；动效关闭时直接是 1（全局动画倍率为 0） */
@Composable
private fun rememberGrow(key: Any?): Float {
    val a = remember { Animatable(0f) }
    LaunchedEffect(key) { a.snapTo(0f); a.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
    return a.value
}

/** 图例：色块 + 文字（文字用正文颜色，不用系列色） */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChartLegend(items: List<Pair<Color, String>>, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEach { (c, t) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(c))
                Spacer(Modifier.width(5.dp))
                Text(t, fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceSecondary)
            }
        }
    }
}

/** 点选后显示的数值说明行（代替悬停提示） */
@Composable
private fun Readout(text: String) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MiuixTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/**
 * 趋势折线（单系列，0–100%）：2px 线、≥8dp 点、浅色面积、虚线为平均值。
 * 点按图表选中最近的一次，在上方显示详情。
 */
@Composable
fun TrendLine(values: List<Float>, labels: List<String>, details: List<String>, modifier: Modifier = Modifier, height: Dp = 170.dp) {
    if (values.isEmpty()) return
    val colors = MiuixTheme.colorScheme
    val line = colors.primary
    val grid = colors.dividerLine
    val muted = colors.onSurfaceVariantSummary
    val grow = rememberGrow(values)
    var sel by remember(values) { mutableIntStateOf(values.lastIndex) }
    val avg = values.average().toFloat()
    Column(modifier) {
        Readout(details.getOrElse(sel) { "" })
        Spacer(Modifier.height(6.dp))
        Row {
            Column(Modifier.height(height), verticalArrangement = Arrangement.SpaceBetween) {
                listOf("100%", "50%", "0%").forEach { Text(it, fontSize = 10.sp, color = muted) }
            }
            Spacer(Modifier.width(6.dp))
            Canvas(
                Modifier.weight(1f).height(height)
                    .semantics { contentDescription = "提交率趋势，平均 ${pctText(avg)}，最近一次 ${pctText(values.last())}" }
                    .pointerInput(values) {
                        detectTapGestures { o ->
                            val n = values.size
                            sel = if (n == 1) 0 else ((o.x / size.width) * (n - 1)).roundToInt().coerceIn(0, n - 1)
                        }
                    },
            ) {
                val pad = 6.dp.toPx()
                val w = size.width - pad * 2
                val h = size.height - pad * 2
                fun x(i: Int) = pad + if (values.size == 1) w / 2 else w * i / (values.size - 1)
                fun y(v: Float) = pad + h * (1f - v.coerceIn(0f, 1f) * grow)
                listOf(0f, 0.5f, 1f).forEach { g -> drawLine(grid, Offset(pad, y(g)), Offset(size.width - pad, y(g)), 1.dp.toPx()) }
                val p = Path().apply { values.forEachIndexed { i, v -> if (i == 0) moveTo(x(i), y(v)) else lineTo(x(i), y(v)) } }
                val area = Path().apply { addPath(p); lineTo(x(values.lastIndex), y(0f)); lineTo(x(0), y(0f)); close() }
                drawPath(area, Brush.verticalGradient(listOf(line.copy(alpha = 0.20f), line.copy(alpha = 0.02f))))
                drawLine(muted.copy(alpha = 0.7f), Offset(pad, y(avg)), Offset(size.width - pad, y(avg)), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
                drawPath(p, line, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                values.forEachIndexed { i, v ->
                    val r = if (i == sel) 5.dp.toPx() else 4.dp.toPx()
                    drawCircle(colors.surfaceContainer, r + 2.dp.toPx(), Offset(x(i), y(v)))
                    drawCircle(line, r, Offset(x(i), y(v)))
                }
                drawLine(line.copy(alpha = 0.35f), Offset(x(sel), pad), Offset(x(sel), size.height - pad), 1.dp.toPx())
            }
        }
        AxisLabels(labels, Modifier.padding(start = 34.dp, top = 4.dp), muted)
        Text("虚线为平均 ${pctText(avg)} · 点按图表查看每次详情", fontSize = 11.sp, color = muted, modifier = Modifier.padding(top = 2.dp))
    }
}

/** 每次作业的状态构成：堆叠柱，段间留 2px 间隙，顶部 4px 圆角；点按查看人数 */
@Composable
fun StackedColumns(columns: List<Map<HwStatus, Int>>, labels: List<String>, details: List<String>, modifier: Modifier = Modifier, height: Dp = 150.dp) {
    if (columns.isEmpty()) return
    val colors = MiuixTheme.colorScheme
    val statusColors = ChartStatusOrder.associateWith { statusChartColor(it) }
    val grow = rememberGrow(columns)
    var sel by remember(columns) { mutableIntStateOf(columns.lastIndex) }
    Column(modifier) {
        Readout(details.getOrElse(sel) { "" })
        Spacer(Modifier.height(6.dp))
        Canvas(
            Modifier.fillMaxWidth().height(height)
                .semantics { contentDescription = "每次作业的状态构成，共 ${columns.size} 次" }
                .pointerInput(columns) {
                    detectTapGestures { o -> sel = ((o.x / size.width) * columns.size).toInt().coerceIn(0, columns.lastIndex) }
                },
        ) {
            val n = columns.size
            val gap = 2.dp.toPx()
            val slot = size.width / n
            val bw = (slot * 0.62f).coerceAtMost(28.dp.toPx())
            columns.forEachIndexed { i, m ->
                val total = m.values.sum().coerceAtLeast(1)
                val cx = slot * i + slot / 2
                var top = size.height
                val segs = ChartStatusOrder.filter { (m[it] ?: 0) > 0 }
                segs.forEachIndexed { k, s ->
                    val h = size.height * (m[s] ?: 0) / total * grow
                    val segH = (h - if (k < segs.lastIndex) gap else 0f).coerceAtLeast(0f)
                    val t = top - h
                    val c = statusColors.getValue(s).copy(alpha = if (i == sel) 1f else 0.82f)
                    if (k == segs.lastIndex) {
                        drawPath(Path().apply {
                            addRoundRect(RoundRect(cx - bw / 2, t, cx + bw / 2, t + segH, topLeftCornerRadius = CornerRadius(4.dp.toPx()), topRightCornerRadius = CornerRadius(4.dp.toPx())))
                        }, c)
                    } else drawRect(c, Offset(cx - bw / 2, t + (h - segH)), Size(bw, segH))
                    top = t
                }
            }
            drawLine(colors.dividerLine, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
        }
        AxisLabels(labels, Modifier.padding(top = 4.dp), colors.onSurfaceVariantSummary)
        Spacer(Modifier.height(8.dp))
        ChartLegend(ChartStatusOrder.map { statusChartColor(it) to it.label })
    }
}

/** 横向条形（科目 / 星期 / 小组等）：标签和数值用正文颜色 */
@Composable
fun HBars(items: List<Triple<String, Float, String>>, modifier: Modifier = Modifier, color: Color = MiuixTheme.colorScheme.primary, labelWidth: Dp = 52.dp) {
    val colors = MiuixTheme.colorScheme
    val grow = rememberGrow(items)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (label, v, text) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.width(labelWidth), fontSize = 13.sp, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Box(Modifier.weight(1f).height(10.dp).clip(CircleShape).background(colors.secondaryContainer)) {
                    Box(Modifier.fillMaxWidth((v * grow).coerceIn(0.015f, 1f)).height(10.dp).clip(CircleShape).background(color))
                }
                Text(text, Modifier.width(76.dp).padding(start = 8.dp), fontSize = 12.sp, color = colors.onSurfaceSecondary, maxLines = 1, textAlign = TextAlign.End)
            }
        }
    }
}

/** 直方图（积分分布）：单色柱 + 柱顶人数 */
@Composable
fun Histogram(bins: List<Pair<String, Int>>, modifier: Modifier = Modifier, height: Dp = 120.dp) {
    if (bins.isEmpty()) return
    val colors = MiuixTheme.colorScheme
    val max = bins.maxOf { it.second }.coerceAtLeast(1)
    val grow = rememberGrow(bins)
    Column(modifier) {
        Row(Modifier.fillMaxWidth().height(height), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
            bins.forEach { (_, n) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (n > 0) Text("$n", fontSize = 11.sp, color = colors.onSurfaceSecondary)
                    Box(
                        Modifier.fillMaxWidth().height(((height.value - 18) * n / max * grow).coerceAtLeast(if (n > 0) 3f else 1f).dp)
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(if (n > 0) colors.primary else colors.dividerLine),
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            bins.forEach { (label, _) -> Text(label, Modifier.weight(1f), fontSize = 9.sp, color = colors.onSurfaceVariantSummary, textAlign = TextAlign.Center, maxLines = 1) }
        }
    }
}

/** 单个核心指标：大数字 + 与上一时段的变化 */
@Composable
fun KpiTile(label: String, value: String, delta: Float?, modifier: Modifier = Modifier, goodWhenUp: Boolean = true) {
    val colors = MiuixTheme.colorScheme
    Column(modifier.glassPanel(RoundedCornerShape(18.dp)).padding(14.dp)) {
        Text(label, fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
        if (delta != null) {
            val pts = (delta * 100).roundToInt()
            val good = if (goodWhenUp) pts >= 0 else pts <= 0
            Text(
                when { pts > 0 -> "▲ $pts 个百分点"; pts < 0 -> "▼ ${-pts} 个百分点"; else -> "与上期持平" },
                fontSize = 11.sp, color = if (pts == 0) colors.onSurfaceVariantSummary else if (good) Color(0xFF169B68) else Color(0xFFD93A4C),
            )
        } else Text(" ", fontSize = 11.sp)
    }
}

/**
 * 数值折线（积分走势、考试平均分等）：纵轴按数据范围自动取整，2px 线、点按看详情。
 */
@Composable
fun ValueLine(values: List<Double>, labels: List<String>, details: List<String>, modifier: Modifier = Modifier, height: Dp = 150.dp, format: (Double) -> String = ::fmtValue) {
    if (values.isEmpty()) return
    val colors = MiuixTheme.colorScheme
    val line = colors.primary
    val muted = colors.onSurfaceVariantSummary
    val grow = rememberGrow(values)
    var sel by remember(values) { mutableIntStateOf(values.lastIndex) }
    val lo0 = values.min(); val hi0 = values.max()
    val pad = maxOf((hi0 - lo0) * 0.15, 0.5)
    val lo = kotlin.math.floor(lo0 - pad); val hi = kotlin.math.ceil(hi0 + pad)
    Column(modifier) {
        Readout(details.getOrElse(sel) { "" })
        Spacer(Modifier.height(6.dp))
        Row {
            Column(Modifier.height(height), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.End) {
                listOf(hi, (hi + lo) / 2, lo).forEach { Text(format(it), fontSize = 10.sp, color = muted) }
            }
            Spacer(Modifier.width(6.dp))
            Canvas(
                Modifier.weight(1f).height(height)
                    .pointerInput(values) {
                        detectTapGestures { o -> val n = values.size; sel = if (n == 1) 0 else ((o.x / size.width) * (n - 1)).roundToInt().coerceIn(0, n - 1) }
                    },
            ) {
                val p = 6.dp.toPx(); val w = size.width - p * 2; val h = size.height - p * 2
                fun x(i: Int) = p + if (values.size == 1) w / 2 else w * i / (values.size - 1)
                fun y(v: Double) = p + h * (1f - (((v - lo) / (hi - lo)).toFloat().coerceIn(0f, 1f)) * grow)
                listOf(lo, (hi + lo) / 2, hi).forEach { g -> drawLine(colors.dividerLine, Offset(p, y(g)), Offset(size.width - p, y(g)), 1.dp.toPx()) }
                val path = Path().apply { values.forEachIndexed { i, v -> if (i == 0) moveTo(x(i), y(v)) else lineTo(x(i), y(v)) } }
                drawPath(path, line, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                values.forEachIndexed { i, v ->
                    val r = if (i == sel) 5.dp.toPx() else 3.5.dp.toPx()
                    drawCircle(colors.surfaceContainer, r + 2.dp.toPx(), Offset(x(i), y(v)))
                    drawCircle(line, r, Offset(x(i), y(v)))
                }
                drawLine(line.copy(alpha = 0.35f), Offset(x(sel), p), Offset(x(sel), size.height - p), 1.dp.toPx())
            }
        }
        AxisLabels(labels, Modifier.padding(start = 34.dp, top = 4.dp), muted)
    }
}

/** 自动结论列表：彩色圆点区分“好 / 提示 / 注意” */
@Composable
fun InsightList(items: List<com.deepshawn.hwcounter.data.Analytics.Insight>, modifier: Modifier = Modifier) {
    val colors = MiuixTheme.colorScheme
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (items.isEmpty()) Text("数据还不够多，暂时没有明显的结论。", fontSize = 14.sp, color = colors.onSurfaceVariantSummary)
        items.forEach { s ->
            val c = when (s.level) {
                com.deepshawn.hwcounter.data.Analytics.Level.GOOD -> Color(0xFF169B68)
                com.deepshawn.hwcounter.data.Analytics.Level.WARN -> Color(0xFFE8792C)
                else -> colors.primary
            }
            Row {
                Box(Modifier.padding(top = 6.dp).size(8.dp).clip(CircleShape).background(c))
                Spacer(Modifier.width(10.dp))
                Text(s.text, fontSize = 14.sp, lineHeight = 20.sp, color = colors.onSurface)
            }
        }
    }
}

private fun fmtValue(x: Double): String = with(x) { com.deepshawn.hwcounter.data.fmtNumber(this) }


/** 横轴标签：少于 7 个逐个显示；更多时只显示首、中、尾，不截断 */
@Composable
private fun AxisLabels(labels: List<String>, modifier: Modifier, color: Color) {
    if (labels.size <= 6) Row(modifier.fillMaxWidth()) {
        labels.forEach { Text(it, Modifier.weight(1f), fontSize = 10.sp, color = color, textAlign = TextAlign.Center, maxLines = 1) }
    } else Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf(labels.first(), labels[labels.size / 2], labels.last()).forEach { Text(it, fontSize = 10.sp, color = color, maxLines = 1) }
    }
}
