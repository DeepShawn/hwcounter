@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.deepshawn.hwcounter.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepshawn.hwcounter.data.AttentionStock
import com.deepshawn.hwcounter.data.PlagiarismGraph
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** 折线（带均值虚线和面积）；鼠标悬停显示这一点的说明 */
@Composable
fun LineChart(values: List<Double>, labels: List<String>, tips: List<String>, lo: Double = 0.0, hi: Double = 1.0, fmt: (Double) -> String = { pct(it) }, height: Dp = 140.dp) {
    if (values.size < 2) return
    val accent = MiuixTheme.colorScheme.primary
    val subC = sub()
    var hover by remember(values) { mutableStateOf<Int?>(null) }
    val l = if (hi - lo < 1e-9) lo - 1 else lo; val h = if (hi - lo < 1e-9) hi + 1 else hi
    val avg = values.average()
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Canvas(Modifier.fillMaxWidth().height(height).pointerInput(values) {
            awaitPointerEventScope {
                while (true) {
                    val e = awaitPointerEvent()
                    val p = e.changes.first().position
                    hover = if (e.type == PointerEventType.Exit) null else ((p.x / size.width) * (values.size - 1)).let { Math.round(it).coerceIn(0, values.size - 1) }
                }
            }
        }) {
            val w = size.width; val hh = size.height; val n = values.size
            fun x(i: Int) = w * i / (n - 1)
            fun y(v: Double) = (hh * (1 - (v - l) / (h - l))).toFloat()
            drawLine(subC, Offset(0f, y(avg)), Offset(w, y(avg)), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
            val path = Path().apply { values.forEachIndexed { i, v -> if (i == 0) moveTo(x(i), y(v)) else lineTo(x(i), y(v)) } }
            val area = Path().apply { addPath(path); lineTo(w, hh); lineTo(0f, hh); close() }
            drawPath(area, accent.copy(alpha = 0.12f))
            drawPath(path, accent, style = Stroke(2.4.dp.toPx()))
            values.forEachIndexed { i, v -> drawCircle(accent, if (hover == i) 5.dp.toPx() else 2.6.dp.toPx(), Offset(x(i), y(v))) }
            hover?.let { i -> drawLine(subC.copy(alpha = 0.5f), Offset(x(i), 0f), Offset(x(i), hh), 1f) }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(labels.first(), fontSize = 11.sp, color = subC)
            Text(hover?.let { tips[it] } ?: "虚线为平均 ${fmt(avg)}", Modifier.weight(1f), fontSize = 11.sp, color = if (hover != null) MiuixTheme.colorScheme.onSurface else subC, textAlign = TextAlign.Center, maxLines = 1)
            Text(labels.last(), fontSize = 11.sp, color = subC)
        }
    }
}

/** 柱状分布 */
@Composable
fun Histogram(bins: List<Pair<String, Int>>, color: Color = MiuixTheme.colorScheme.primary) {
    val max = maxOf(1, bins.maxOfOrNull { it.second } ?: 1)
    Row(Modifier.fillMaxWidth().height(150.dp).padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
        bins.forEach { (l, n) ->
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                Text(if (n > 0) "$n" else "", fontSize = 11.sp, color = sub())
                Box(Modifier.fillMaxWidth(0.8f).fillMaxHeight(maxOf(0.02f, n.toFloat() / max * 0.78f)).clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)).background(color.copy(alpha = 0.85f)))
                Text(l, fontSize = 10.sp, color = sub(), maxLines = 1)
            }
        }
    }
}

/** 横条 */
@Composable
fun HBars(rows: List<Triple<String, Double, String>>, color: Color? = null, labelWidth: Dp = 72.dp) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { (l, v, t) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(l, Modifier.width(labelWidth), fontSize = 13.5.sp, maxLines = 1)
                Box(Modifier.weight(1f).height(8.dp).clip(CircleShape).background(track())) {
                    Box(Modifier.fillMaxWidth(v.toFloat().coerceIn(0.02f, 1f)).height(8.dp).clip(CircleShape).background(color ?: rateColor(v)))
                }
                Text(t, Modifier.padding(start = 10.dp), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
}

/** 一条比例：多段颜色 */
@Composable
fun Split(parts: List<Pair<Int, Color>>) {
    Row(Modifier.fillMaxWidth().height(12.dp).clip(CircleShape)) { parts.filter { it.first > 0 }.forEach { (n, c) -> Box(Modifier.weight(n.toFloat()).fillMaxHeight().background(c)) } }
}

/** 环形图 */
@Composable
fun Donut(parts: List<Pair<Color, Int>>, size: Dp = 128.dp, stroke: Dp = 18.dp, content: @Composable () -> Unit) {
    val tr = track()
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val s = stroke.toPx(); val r = Size(this.size.width - s, this.size.height - s); val o = Offset(s / 2, s / 2)
            drawArc(tr, 0f, 360f, false, o, r, style = Stroke(s))
            val total = parts.sumOf { it.second }.coerceAtLeast(1); var start = -90f
            parts.forEach { (c, n) -> val sweep = 360f * n / total; if (n > 0) drawArc(c, start, sweep, false, o, r, style = Stroke(s)); start += sweep }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) { content() }
    }
}

/** 小走势 */
@Composable
fun Spark(values: List<Double>, color: Color, w: Dp = 48.dp, h: Dp = 22.dp, hiMin: Double = 100.0) {
    if (values.size < 2) { Spacer(Modifier.width(w)); return }
    Canvas(Modifier.size(w, h)) {
        val hi = maxOf(hiMin, values.max()); val n = values.size
        val p = Path().apply { values.forEachIndexed { i, v -> val x = size.width * i / (n - 1); val y = (size.height - 2 - (size.height - 4) * v / hi).toFloat(); if (i == 0) moveTo(x, y) else lineTo(x, y) } }
        drawPath(p, color, style = Stroke(1.6.dp.toPx()))
    }
}

/** 个股小图（起点虚线） */
@Composable
fun MiniChart(q: AttentionStock.Quote, color: Color) {
    val v = q.closes; if (v.size < 2) return
    val subC = sub()
    Canvas(Modifier.size(70.dp, 30.dp)) {
        var lo = v.min(); var hi = v.max(); if (hi - lo < 1) { lo -= 0.5; hi += 0.5 }
        fun x(i: Int) = size.width * i / (v.size - 1)
        fun y(t: Double) = (2 + (size.height - 4) * (1 - (t - lo) / (hi - lo))).toFloat()
        drawLine(subC, Offset(0f, y(q.base)), Offset(size.width, y(q.base)), 0.8f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f)))
        drawPath(Path().apply { v.forEachIndexed { i, t -> if (i == 0) moveTo(x(i), y(t)) else lineTo(x(i), y(t)) } }, color, style = Stroke(1.6.dp.toPx()))
    }
}

/** 行情图：走势或 K 线 + 成交量；鼠标移动查看每一根 */
@Composable
fun StockChart(q: AttentionStock.Quote, kline: Boolean, sc: (Double) -> Color, sel: Int?, onSel: (Int?) -> Unit) {
    val n = q.candles.size; if (n == 0) return
    val fg = MiuixTheme.colorScheme.onSurface; val subC = sub()
    Canvas(Modifier.fillMaxWidth().height(200.dp).pointerInput(q, kline) {
        awaitPointerEventScope {
            while (true) {
                val e = awaitPointerEvent(); val x = e.changes.first().position.x; val w = size.width.toFloat()
                if (e.type == PointerEventType.Exit) onSel(null)
                else onSel((if (kline) (x / (w / n)).toInt() else Math.round(x / w * n) - 1).coerceIn(0, n - 1))
            }
        }
    }) {
        val vh = 36.dp.toPx(); val ch = size.height - vh - 8; val w = size.width
        var lo = minOf(q.low, q.base); var hi = maxOf(q.high, q.base)
        if (hi - lo < 2) { val m = (hi + lo) / 2; lo = m - 1; hi = m + 1 }
        val pad = (hi - lo) * 0.08; lo = maxOf(0.0, lo - pad); hi += pad
        fun y(v: Double) = (4 + ch * (1 - (v - lo) / (hi - lo))).toFloat()
        val bw = w / n; val maxV = maxOf(1, q.candles.maxOf { it.volume })
        drawLine(subC, Offset(0f, y(q.base)), Offset(w, y(q.base)), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f)))
        if (kline) q.candles.forEachIndexed { i, k ->
            val col = if (k.close >= k.open) sc(1.0) else sc(-1.0); val cx = (i + 0.5f) * bw
            drawLine(col, Offset(cx, y(k.high)), Offset(cx, y(k.low)), 1.2f)
            val top = y(maxOf(k.open, k.close)); val bot = y(minOf(k.open, k.close))
            drawRect(col, Offset(cx - bw * 0.32f, top), Size(bw * 0.64f, maxOf(1f, bot - top)))
        } else {
            val color = sc(q.change)
            val pts = q.closes.mapIndexed { i, v -> Offset(w * i / n, y(v)) }
            val path = Path().apply { pts.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } }
            drawPath(Path().apply { addPath(path); lineTo(w, 4 + ch); lineTo(0f, 4 + ch); close() }, color.copy(alpha = 0.12f))
            drawPath(path, color, style = Stroke(2.dp.toPx()))
        }
        q.candles.forEachIndexed { i, k ->
            val hgt = k.volume.toFloat() / maxV * (vh - 4)
            drawRect(sc(k.close - k.open).copy(alpha = 0.45f), Offset(i * bw + bw * 0.15f, size.height - hgt), Size(bw * 0.7f, hgt))
        }
        sel?.let { i -> val sx = if (kline) (i + 0.5f) * bw else w * (i + 1) / n; drawLine(fg.copy(alpha = 0.5f), Offset(sx, 0f), Offset(sx, size.height), 1f) }
    }
}

/** 抄袭关系图：同学排成一圈，箭头从提供者指向抄袭者；点头像进入档案 */
@Composable
fun PlagGraph(r: PlagiarismGraph.Report, onOpen: (String) -> Unit) {
    val measurer = rememberTextMeasurer()
    val nodeOf = r.nodes.associateBy { it.student.id }
    val order = r.clusters.flatten().filter { it.id in nodeOf }
    val n = order.size
    val maxC = r.edges.maxOfOrNull { it.count } ?: 1
    val fg = MiuixTheme.colorScheme.onSurface
    var pos by remember { mutableStateOf<Map<String, Offset>>(emptyMap()) }
    Canvas(Modifier.widthIn(max = 520.dp).fillMaxWidth().aspectRatio(1f).pointerInput(r) {
        detectTapGestures { o -> pos.minByOrNull { (o - it.value).getDistance() }?.takeIf { (o - it.value).getDistance() < 30.dp.toPx() }?.let { onOpen(it.key) } }
    }) {
        val cx = size.width / 2; val cy = size.height / 2
        val nr = (if (n > 16) 13 else 18).dp.toPx(); val ring = minOf(cx, cy) - nr - 22.dp.toPx()
        val p = order.mapIndexed { i, s -> val a = if (n == 1) -Math.PI / 2 else -Math.PI / 2 + 2 * Math.PI * i / n; s.id to Offset(cx + (ring * cos(a)).toFloat(), cy + (ring * sin(a)).toFloat()) }.toMap()
        pos = p
        for (e in r.edges) {
            val a = p[e.provider.id] ?: continue; val b = p[e.receiver.id] ?: continue
            val d = hypot(b.x - a.x, b.y - a.y).coerceAtLeast(1f); val ux = (b.x - a.x) / d; val uy = (b.y - a.y) / d
            val s = Offset(a.x + ux * nr, a.y + uy * nr); val t = Offset(b.x - ux * (nr + 2), b.y - uy * (nr + 2))
            val wdt = (1.5f + 3.5f * e.count / maxC).dp.toPx(); val col = PlagColor.copy(alpha = 0.45f + 0.5f * e.count / maxC)
            drawLine(col, s, t, wdt)
            val ang = atan2(uy, ux); val hd = 9.dp.toPx() + wdt
            drawPath(Path().apply { moveTo(t.x, t.y); lineTo(t.x - hd * cos(ang - 0.45f), t.y - hd * sin(ang - 0.45f)); lineTo(t.x - hd * cos(ang + 0.45f), t.y - hd * sin(ang + 0.45f)); close() }, col)
            if (e.count > 1) {
                val mid = Offset((s.x + t.x) / 2, (s.y + t.y) / 2)
                drawCircle(PlagColor, 9.dp.toPx(), mid)
                val tl = measurer.measure("${e.count}", TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White))
                drawText(tl, topLeft = Offset(mid.x - tl.size.width / 2f, mid.y - tl.size.height / 2f))
            }
        }
        for (s in order) {
            val o = p.getValue(s.id); val nd = nodeOf.getValue(s.id)
            drawCircle(roleColor(nd), nr, o); drawCircle(Color.White, nr, o, style = Stroke(2.dp.toPx()))
            val ini = measurer.measure(s.name.takeLast(1), TextStyle(fontSize = if (n > 16) 11.sp else 14.sp, fontWeight = FontWeight.Bold, color = Color.White))
            drawText(ini, topLeft = Offset(o.x - ini.size.width / 2f, o.y - ini.size.height / 2f))
            val lb = measurer.measure(s.name, TextStyle(fontSize = 11.sp, color = fg))
            drawText(lb, topLeft = Offset(o.x - lb.size.width / 2f, if (o.y >= cy) o.y + nr + 2.dp.toPx() else o.y - nr - 2.dp.toPx() - lb.size.height))
        }
    }
}

val PlagColor = Color(0xFFD9480F)
val ProviderColor = Color(0xFFE8792C); val CopierColor = Color(0xFFE5384D); val BothColor = Color(0xFF8E44AD)
fun roleColor(n: PlagiarismGraph.Node) = when { n.copied > 0 && n.provided > 0 -> BothColor; n.copied > 0 -> CopierColor; else -> ProviderColor }

/** 图例 */
@Composable
fun Legend(items: List<Pair<Color, String>>) {
    androidx.compose.foundation.layout.FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items.forEach { (c, l) -> Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(c)); Text(" $l", fontSize = 12.sp, color = sub()) } }
    }
}

/** 可点的小行（名字 + 数值） */
@Composable
fun MiniRow(name: String, value: String, color: Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(vertical = 5.dp)) {
        Text(name, Modifier.weight(1f), fontSize = 14.sp, maxLines = 1); Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}
