package com.deepshawn.hwcounter.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepshawn.hwcounter.data.AppData
import com.deepshawn.hwcounter.data.AppViewModel
import com.deepshawn.hwcounter.data.Attention
import com.deepshawn.hwcounter.data.AttentionStock
import com.deepshawn.hwcounter.data.SchoolClass
import com.deepshawn.hwcounter.data.ScoreRules
import com.deepshawn.hwcounter.data.Student
import com.deepshawn.hwcounter.data.fmt
import com.deepshawn.hwcounter.data.shown
import com.deepshawn.hwcounter.data.studentStats
import com.deepshawn.hwcounter.ui.LocalKit
import com.deepshawn.hwcounter.ui.Route
import com.deepshawn.hwcounter.ui.components.Chip2
import com.deepshawn.hwcounter.ui.components.EmptyState
import com.deepshawn.hwcounter.ui.components.HCard
import com.deepshawn.hwcounter.ui.components.HTabs
import com.deepshawn.hwcounter.ui.components.MiuixPage
import com.deepshawn.hwcounter.ui.components.group
import com.deepshawn.hwcounter.ui.components.listCell
import com.deepshawn.hwcounter.ui.components.motionOn
import com.deepshawn.hwcounter.ui.theme.LocalUiPrefs
import com.deepshawn.hwcounter.ui.theme.UiPrefsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

// ---------------- 股票模式：关注度按美股行情显示 ----------------

/** 股票模式页面状态（区间、胶囊显示、排序、K 线） */
@Stable
class StockUi {
    var span by mutableStateOf(AttentionStock.Span.M1)
    /** 胶囊：0 涨跌额，1 涨跌幅，2 等级 */
    var pill by mutableIntStateOf(0)
    /** 排序：0 关注度，1 涨幅，2 跌幅，3 学号 */
    var sort by mutableIntStateOf(0)
    var kline by mutableStateOf(false)
}

@Composable
fun rememberStockUi() = remember { StockUi() }

/** 后台计算行情；切换区间时先保留旧行情，算好再替换 */
@Composable
fun rememberBoard(c: SchoolClass, rules: ScoreRules, span: AttentionStock.Span): AttentionStock.Board? {
    var board by remember { mutableStateOf<AttentionStock.Board?>(null) }
    LaunchedEffect(c, rules, span) {
        board = withContext(Dispatchers.Default) { runCatching { AttentionStock.board(c, rules, span) }.getOrNull() }
    }
    return board
}

data class StockColors(val up: Color, val down: Color, val flat: Color) {
    fun of(change: Double) = when { change > 0.005 -> up; change < -0.005 -> down; else -> flat }
}

private val GREEN = Color(0xFF12A866)
private val RED = Color(0xFFE5433A)

@Composable
fun stockColors(): StockColors {
    val us = LocalUiPrefs.current.usColors
    return StockColors(if (us) GREEN else RED, if (us) RED else GREEN, Color(0xFF8E8E93))
}

private fun p2(x: Double) = "%.2f".format(x)
private fun s2(x: Double) = when { x > 0.005 -> "+" + p2(x); x < -0.005 -> "-" + p2(abs(x)); else -> "0.00" }
private fun pctOf(q: AttentionStock.Quote) = q.pct?.let { p -> (if (p > 0.00005) "+" else if (p < -0.00005) "-" else "") + "%.2f%%".format(abs(p * 100)) } ?: "—"
private fun ticker(s: Student) = if (s.no.isNotBlank()) "No.${s.no}" else s.name

private val md = SimpleDateFormat("M/d", Locale.CHINA)
private fun candleLabel(k: AttentionStock.Candle): String {
    val endDay = k.end - 1
    return if (k.end - k.start <= 86_400_000L) md.format(Date(k.start)) else "${md.format(Date(k.start))}–${md.format(Date(endDay))}"
}

/** 价格 + 涨跌（拖动图表时显示所选那根 K 线） */
@Composable
private fun QuoteHeader(q: AttentionStock.Quote, sel: Int?, span: AttentionStock.Span, sc: StockColors) {
    val colors = MiuixTheme.colorScheme
    val k = sel?.let { q.candles.getOrNull(it) }
    val value = k?.close ?: q.last
    val change = value - q.base
    val pct = if (q.base < 1.0) "—" else (if (change >= 0) "+" else "-") + "%.2f%%".format(abs(change / q.base * 100))
    Column {
        Text(p2(value), fontSize = 34.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${s2(change)} ($pct)", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = sc.of(change))
            Spacer(Modifier.width(8.dp))
            Text(if (k != null) candleLabel(k) else "近${span.label}", fontSize = 13.sp, color = colors.onSurfaceVariantSummary)
        }
    }
}

/** 行情图：走势（面积线）或 K 线，下面是成交量；左右拖动查看每一根 */
@Composable
private fun StockChart(q: AttentionStock.Quote, kline: Boolean, sc: StockColors, sel: Int?, onSel: (Int?) -> Unit, height: Dp = 190.dp) {
    val colors = MiuixTheme.colorScheme
    val n = q.candles.size
    if (n == 0) return
    val selNow by rememberUpdatedState(sel)
    val lineColor = sc.of(q.change)
    val muted = colors.onSurfaceVariantSummary
    val grid = colors.dividerLine
    var lo = minOf(q.low, q.base); var hi = maxOf(q.high, q.base)
    if (hi - lo < 2) { val m = (hi + lo) / 2; lo = m - 1; hi = m + 1 }
    val padV = (hi - lo) * 0.08
    lo = (lo - padV).coerceAtLeast(0.0); hi += padV
    fun index(x: Float, w: Float): Int = if (kline) (x / (w / n)).toInt().coerceIn(0, n - 1) else ((x / w) * n).roundToInt().minus(1).coerceIn(0, n - 1)
    val pick by rememberUpdatedState(onSel)
    // 按住左右拖动：显示手指下那一根，松手恢复；点按：选中 / 再点取消
    val gestures = Modifier
        .pointerInput(q, kline) {
            val w = size.width.toFloat()
            detectHorizontalDragGestures(
                onDragStart = { o -> pick(index(o.x, w)) },
                onDragEnd = { pick(null) },
                onDragCancel = { pick(null) },
            ) { ch, _ -> pick(index(ch.position.x.coerceIn(0f, w), w)) }
        }
        .pointerInput(q, kline, "tap") {
            val w = size.width.toFloat()
            detectTapGestures { o -> val i = index(o.x, w); pick(if (selNow == i) null else i) }
        }
    Column {
        Row {
            Canvas(
                Modifier.weight(1f).height(height).then(gestures)
                    .semantics { contentDescription = "关注度行情，最新 ${p2(q.last)}，涨跌 ${s2(q.change)}" },
            ) {
                val w = size.width; val h = size.height
                fun y(v: Double) = (h * (1 - (v - lo) / (hi - lo))).toFloat()
                listOf(0.0, 0.5, 1.0).forEach { g -> val yy = (h * g).toFloat(); drawLine(grid.copy(alpha = 0.6f), Offset(0f, yy), Offset(w, yy), 1f) }
                // 前收（区间开盘）虚线
                drawLine(muted.copy(alpha = 0.8f), Offset(0f, y(q.base)), Offset(w, y(q.base)), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())))
                if (kline) {
                    val slot = w / n
                    val bw = (slot - maxOf(1.dp.toPx(), slot * 0.22f)).coerceIn(1.dp.toPx(), 20.dp.toPx())
                    q.candles.forEachIndexed { i, k ->
                        val cx = slot * i + slot / 2
                        val col = sc.of(k.close - k.open).let { if (it == sc.flat) colors.onSurface.copy(alpha = 0.55f) else it }
                        drawLine(col, Offset(cx, y(k.high)), Offset(cx, y(k.low)), 1.2.dp.toPx())
                        val top = y(maxOf(k.open, k.close)); val bottom = y(minOf(k.open, k.close))
                        drawRect(col, Offset(cx - bw / 2, top), Size(bw, (bottom - top).coerceAtLeast(1.2.dp.toPx())))
                    }
                    selNow?.let { i -> val cx = slot * i + slot / 2; drawLine(colors.onSurface.copy(alpha = 0.4f), Offset(cx, 0f), Offset(cx, h), 1.dp.toPx()) }
                } else {
                    val pts = q.closes
                    fun x(i: Int) = w * i / (pts.size - 1).coerceAtLeast(1)
                    val path = Path().apply { pts.forEachIndexed { i, v -> if (i == 0) moveTo(x(i), y(v)) else lineTo(x(i), y(v)) } }
                    val area = Path().apply { addPath(path); lineTo(x(pts.lastIndex), h); lineTo(0f, h); close() }
                    drawPath(area, Brush.verticalGradient(listOf(lineColor.copy(alpha = 0.28f), lineColor.copy(alpha = 0.0f))))
                    drawPath(path, lineColor, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    val s = selNow
                    if (s != null) {
                        val px = x(s + 1); val py = y(pts[s + 1])
                        drawLine(colors.onSurface.copy(alpha = 0.4f), Offset(px, 0f), Offset(px, h), 1.dp.toPx())
                        drawCircle(colors.surface, 6.dp.toPx(), Offset(px, py))
                        drawCircle(lineColor, 4.dp.toPx(), Offset(px, py))
                    } else {
                        drawCircle(lineColor.copy(alpha = 0.25f), 7.dp.toPx(), Offset(x(pts.lastIndex), y(pts.last())))
                        drawCircle(lineColor, 3.5.dp.toPx(), Offset(x(pts.lastIndex), y(pts.last())))
                    }
                }
            }
            Column(Modifier.width(40.dp).height(height).padding(start = 6.dp), verticalArrangement = Arrangement.SpaceBetween) {
                listOf(hi, (hi + lo) / 2, lo).forEach { Text(p2(it), fontSize = 10.sp, color = muted, maxLines = 1) }
            }
        }
        // 成交量
        val vmax = (q.candles.maxOfOrNull { it.volume } ?: 0).coerceAtLeast(1)
        Row(Modifier.padding(top = 2.dp)) {
            Canvas(Modifier.weight(1f).height(34.dp).then(gestures)) {
                val slot = size.width / n
                // 成交量柱：紧挨着排，只留 1dp 左右的缝；没有成交的那天画一条细底线
                val bw = (slot - maxOf(1.dp.toPx(), slot * 0.12f)).coerceIn(1.dp.toPx(), 24.dp.toPx())
                q.candles.forEachIndexed { i, k ->
                    val bh = if (k.volume == 0) 1.dp.toPx() else size.height * k.volume / vmax
                    val col = sc.of(k.close - k.open).copy(alpha = if (selNow == null || selNow == i) 0.65f else 0.3f)
                    drawRect(col, Offset(slot * i + slot / 2 - bw / 2, size.height - bh), Size(bw, bh))
                }
            }
            Text("成交\n${vmax}", fontSize = 9.sp, lineHeight = 11.sp, color = muted, modifier = Modifier.width(40.dp).padding(start = 6.dp))
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp, end = 40.dp)) {
            Text(md.format(Date(q.candles.first().start)), fontSize = 10.sp, color = muted)
            Spacer(Modifier.weight(1f))
            Text(if (sel == null) "按住左右拖动查看" else "${candleLabel(q.candles[sel])} 成交 ${q.candles[sel].volume}", fontSize = 10.sp, color = muted)
            Spacer(Modifier.weight(1f))
            Text("现在", fontSize = 10.sp, color = muted)
        }
    }
}

/** 关键数据：两列 */
@Composable
private fun KeyStats(items: List<Pair<String, String>>) {
    val colors = MiuixTheme.colorScheme
    Column {
        items.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                row.forEach { (k, v) ->
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Text(k, fontSize = 12.sp, color = colors.onSurfaceVariantSummary, modifier = Modifier.weight(1f), maxLines = 1)
                        Text(v, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(colors.dividerLine))
        }
    }
}

/** 走势 / K 线切换 + 配色切换 */
@Composable
private fun ChartToggles(ui: StockUi) {
    val ctx = LocalContext.current
    val us = LocalUiPrefs.current.usColors
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Chip2("走势", !ui.kline, { ui.kline = false })
        Chip2("K线", ui.kline, { ui.kline = true })
        Spacer(Modifier.weight(1f))
        Chip2(if (us) "美股 · 绿涨红跌" else "A股 · 红涨绿跌", false, { UiPrefsStore.update(ctx) { it.copy(usColors = !it.usColors) } })
    }
}

/** 列表右侧的小走势线：虚线为区间开盘 */
@Composable
private fun MiniChart(q: AttentionStock.Quote, color: Color, modifier: Modifier) {
    val muted = MiuixTheme.colorScheme.onSurfaceVariantSummary
    val pts = q.closes
    if (pts.size < 2) return
    Canvas(modifier) {
        var lo = pts.min(); var hi = pts.max()
        if (hi - lo < 1) { val m = (hi + lo) / 2; lo = m - 0.5; hi = m + 0.5 }
        fun x(i: Int) = size.width * i / (pts.size - 1)
        fun y(v: Double) = (size.height * (1 - (v - lo) / (hi - lo))).toFloat().coerceIn(1f, size.height - 1f)
        drawLine(muted.copy(alpha = 0.6f), Offset(0f, y(q.base)), Offset(size.width, y(q.base)), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)))
        val p = Path().apply { pts.forEachIndexed { i, v -> if (i == 0) moveTo(x(i), y(v)) else lineTo(x(i), y(v)) } }
        drawPath(p, color, style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** 涨跌胶囊（点按切换 涨跌额 / 涨跌幅 / 等级） */
@Composable
private fun Pill(q: AttentionStock.Quote, level: Attention.Level?, ui: StockUi, sc: StockColors) {
    val (text, bg) = when (ui.pill) {
        1 -> pctOf(q) to sc.of(q.change)
        2 -> (level?.label ?: "—") to (level?.let { levelColor(it) } ?: sc.flat)
        else -> s2(q.change) to sc.of(q.change)
    }
    Box(
        Modifier.width(78.dp).clip(RoundedCornerShape(7.dp)).background(bg).clickable { ui.pill = (ui.pill + 1) % 3 }.padding(vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1) }
}

/** 滚动行情条 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TickerTape(c: SchoolClass, b: AttentionStock.Board, sc: StockColors, onOpen: (String) -> Unit) {
    val colors = MiuixTheme.colorScheme
    val items = c.students.mapNotNull { s -> b.stocks[s.id]?.let { s to it } }.sortedByDescending { abs(it.second.change) }.take(20)
    if (items.isEmpty()) return
    val mod = if (motionOn() && android.os.Build.FINGERPRINT != "robolectric") Modifier.basicMarquee(iterations = Int.MAX_VALUE, velocity = 36.dp) else Modifier.horizontalScroll(rememberScrollState())
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).clip(RoundedCornerShape(10.dp)).background(colors.secondaryContainer.copy(alpha = 0.6f)).padding(vertical = 7.dp).then(mod)) {
        (listOf(Triple("ATTN", b.market, null as Student?)) + items.map { Triple(ticker(it.first), it.second, it.first) }).forEach { (t, q, s) ->
            Spacer(Modifier.width(14.dp))
            Row(if (s != null) Modifier.clickable { onOpen(s.id) } else Modifier, verticalAlignment = Alignment.CenterVertically) {
            Text(t, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            if (s != null) { Spacer(Modifier.width(3.dp)); Text(s.name, fontSize = 12.sp, color = colors.onSurfaceVariantSummary, maxLines = 1) }
            Spacer(Modifier.width(5.dp))
            Text(p2(q.last), fontSize = 12.sp, maxLines = 1)
            Spacer(Modifier.width(4.dp))
            val ch = q.change
            Text((if (ch > 0.005) "▲" else if (ch < -0.005) "▼" else "■") + p2(abs(ch)), fontSize = 12.sp, color = sc.of(ch), fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
        Spacer(Modifier.width(14.dp))
    }
}

private val spans = AttentionStock.Span.entries

/** 统计页“关注”栏目的股票模式 */
fun LazyListScope.stockSections(
    c: SchoolClass,
    board: AttentionStock.Board?,
    ui: StockUi,
    attention: List<Attention.Result>,
    onOpen: (String) -> Unit,
) {
    if (c.students.isEmpty()) { item { EmptyState("📈", "还没有学生", "") }; return }
    item(key = "stk-span") { HTabs(spans.map { it.label }, spans.indexOf(ui.span), { ui.span = spans[it] }, Modifier.padding(horizontal = 12.dp, vertical = 4.dp), height = 36.dp) }
    if (board == null) { item(key = "stk-loading") { Text("正在计算行情…", Modifier.fillMaxWidth().padding(32.dp), textAlign = TextAlign.Center, color = MiuixTheme.colorScheme.onSurfaceVariantSummary) }; return }
    item(key = "stk-tape") { Column(Modifier.padding(vertical = 6.dp)) { TickerTape(c, board, stockColors(), onOpen) } }
    item(key = "stk-market") {
        val sc = stockColors()
        val colors = MiuixTheme.colorScheme
        var sel by remember(board) { mutableStateOf<Int?>(null) }
        HCard(Modifier.padding(horizontal = 12.dp, vertical = 6.dp).fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("ATTN", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.width(6.dp))
                    Text("关注指数 · 全班平均", fontSize = 13.sp, color = colors.onSurfaceVariantSummary)
                    Spacer(Modifier.weight(1f))
                    if (board.span != ui.span) Text("计算中…", fontSize = 11.sp, color = colors.onSurfaceVariantSummary)
                }
                Spacer(Modifier.height(6.dp))
                QuoteHeader(board.market, sel, board.span, sc)
                ChartToggles(ui)
                StockChart(board.market, ui.kline, sc, sel, { sel = it })
                Spacer(Modifier.height(10.dp))
                val m = board.market
                KeyStats(
                    listOf(
                        "开盘" to p2(m.base), "最高" to p2(m.high),
                        "前收" to p2(m.prevClose), "最低" to p2(m.low),
                        "成交量" to "${m.volume}", "振幅" to if (m.base < 1) "—" else "%.2f%%".format((m.high - m.low) / m.base * 100),
                        "上涨" to "${board.up} 家", "下跌" to "${board.down} 家",
                    ),
                )
                Text("指数越高越需要关注：上涨表示情况变差，下跌表示在好转。成交量为这段时间的记录条数（作业登记、加减分、考试成绩）。", fontSize = 11.sp, color = colors.onSurfaceVariantSummary, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
    item(key = "stk-movers") {
        val sc = stockColors()
        val names = c.students.associateBy { it.id }
        val qs = board.stocks.entries.mapNotNull { (id, q) -> names[id]?.let { it to q } }
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("领涨" to qs.filter { it.second.change > 0.005 }.sortedByDescending { it.second.change }, "领跌" to qs.filter { it.second.change < -0.005 }.sortedBy { it.second.change }).forEach { (title, list) ->
                HCard(Modifier.weight(1f)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        if (list.isEmpty()) Text("无", fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        list.take(3).forEach { (s, q) ->
                            Row(Modifier.fillMaxWidth().clickable { onOpen(s.id) }, verticalAlignment = Alignment.CenterVertically) {
                                Text(s.name, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(s2(q.change), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = sc.of(q.change))
                            }
                        }
                    }
                }
            }
        }
    }
    item(key = "stk-sort") {
        Column {
            SmallTitle("自选股 · 点胶囊切换涨跌额 / 涨跌幅 / 等级")
            Row(Modifier.padding(horizontal = 12.dp).padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("关注度", "涨幅", "跌幅", "学号").forEachIndexed { i, t -> Chip2(t, ui.sort == i, { ui.sort = i }) }
            }
        }
    }
    val byId = attention.associateBy { it.student.id }
    val rows = c.students.mapNotNull { s -> board.stocks[s.id]?.let { Triple(s, it, byId[s.id]) } }.let { l ->
        when (ui.sort) {
            1 -> l.sortedByDescending { it.second.change }
            2 -> l.sortedBy { it.second.change }
            3 -> l.sortedWith(compareBy({ it.first.no.toIntOrNull() ?: Int.MAX_VALUE }, { it.first.no }))
            else -> l.sortedByDescending { it.third?.index ?: it.second.last }
        }
    }
    itemsIndexed(rows, key = { _, r -> "stk" + r.first.id }) { i, (s, q, a) ->
        val sc = stockColors()
        val colors = MiuixTheme.colorScheme
        Row(
            Modifier.listCell(i, rows.size).clickable { onOpen(s.id) }.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(ticker(s), fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1)
                Text(s.name + (a?.top?.firstOrNull()?.let { " · ${it.label}" } ?: ""), fontSize = 12.sp, color = colors.onSurfaceVariantSummary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            MiniChart(q, sc.of(q.change), Modifier.width(70.dp).height(30.dp))
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(p2(a?.index ?: q.last), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Spacer(Modifier.height(3.dp))
                Pill(q, a?.level, ui, sc)
            }
        }
    }
}

// ---------------- 个股详情 ----------------

@Composable
fun StockDetailScreen(vm: AppViewModel, data: AppData, studentId: String) {
    val kit = LocalKit.current
    val colors = MiuixTheme.colorScheme
    val c = vm.currentClass(data) ?: return
    val rules = data.settings.rules
    val ui = rememberStockUi()
    val board = rememberBoard(c, rules, ui.span)
    val attention = remember(c, rules) { Attention.all(c, rules) }
    val a = attention.firstOrNull { it.student.id == studentId } ?: run { EmptyState("👤", "找不到这位同学", ""); return }
    val s = a.student
    val score = remember(c, rules, studentId) { c.studentStats(rules).firstOrNull { it.student.id == studentId }?.score?.let { rules.shown(it) } }
    val sc = stockColors()
    var sel by remember(board) { mutableStateOf<Int?>(null) }
    val q = board?.stocks?.get(studentId)

    MiuixPage(title = ticker(s), largeTitle = "${ticker(s)} ${s.name}", onBack = { kit.nav.pop() }) {
        item(key = "quote") {
            HCard(Modifier.padding(horizontal = 12.dp, vertical = 6.dp).fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("关注度 · ${s.name}", fontSize = 13.sp, color = colors.onSurfaceVariantSummary)
                        Spacer(Modifier.width(6.dp))
                        val col = levelColor(a.level)
                        Text(a.level.label, fontSize = 10.sp, color = col, modifier = Modifier.clip(CircleShape).background(col.copy(alpha = 0.12f)).padding(horizontal = 6.dp, vertical = 1.dp))
                        Spacer(Modifier.weight(1f))
                        if (board != null && board.span != ui.span) Text("计算中…", fontSize = 11.sp, color = colors.onSurfaceVariantSummary)
                    }
                    Spacer(Modifier.height(6.dp))
                    if (q == null) Text(p2(a.index), fontSize = 34.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    else QuoteHeader(q, sel, board.span, sc)
                    Spacer(Modifier.height(8.dp))
                    HTabs(spans.map { it.label }, spans.indexOf(ui.span), { ui.span = spans[it] }, height = 34.dp)
                    ChartToggles(ui)
                    if (q == null) Text("正在计算行情…", Modifier.fillMaxWidth().padding(24.dp), textAlign = TextAlign.Center, color = colors.onSurfaceVariantSummary)
                    else StockChart(q, ui.kline, sc, sel, { sel = it })
                }
            }
        }
        if (q != null && board != null) {
            group("关键数据", key = "stats") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    val rank = attention.indexOfFirst { it.student.id == studentId } + 1
                    val mk = board.market
                    KeyStats(
                        listOf(
                            "开盘" to p2(q.base), "最高" to p2(q.high),
                            "前收" to p2(q.prevClose), "最低" to p2(q.low),
                            "振幅" to if (q.base < 1) "—" else "%.2f%%".format((q.high - q.low) / q.base * 100), "成交量" to "${q.volume}",
                            "积分（市值）" to (score?.fmt() ?: "—"), "关注排名" to "$rank / ${attention.size}",
                            "同期大盘" to "${s2(mk.change)}", "相对大盘" to s2(q.change - mk.change),
                        ),
                    )
                }
            }
        }
        group("成分 · 关注度构成", key = "parts") {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                a.parts.sortedByDescending { it.points }.forEach { p ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.label, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            Text(p.detail, fontSize = 11.sp, color = colors.onSurfaceVariantSummary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(Modifier.width(8.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            Text(p2(p.points), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                            Text("权重 ${(p.weight * 100).roundToInt()}%", fontSize = 10.sp, color = colors.onSurfaceVariantSummary)
                        }
                    }
                }
            }
        }
        if (q != null) {
            group("历史行情", key = "history") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        listOf("日期" to 1.6f, "开盘" to 1f, "收盘" to 1f, "涨跌" to 1f, "成交" to 0.8f).forEach { (t, w) ->
                            Text(t, Modifier.weight(w), fontSize = 11.sp, color = colors.onSurfaceVariantSummary, textAlign = if (t == "日期") TextAlign.Start else TextAlign.End)
                        }
                    }
                    q.candles.asReversed().take(14).forEach { k ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                            Text(candleLabel(k), Modifier.weight(1.6f), fontSize = 12.sp, maxLines = 1)
                            Text(p2(k.open), Modifier.weight(1f), fontSize = 12.sp, textAlign = TextAlign.End)
                            Text(p2(k.close), Modifier.weight(1f), fontSize = 12.sp, textAlign = TextAlign.End, fontWeight = FontWeight.Medium)
                            Text(s2(k.close - k.open), Modifier.weight(1f), fontSize = 12.sp, textAlign = TextAlign.End, color = sc.of(k.close - k.open))
                            Text("${k.volume}", Modifier.weight(0.8f), fontSize = 12.sp, textAlign = TextAlign.End)
                        }
                    }
                }
            }
        }
        item(key = "full") {
            TextButton(
                "查看完整个人分析", onClick = { kit.nav.push(Route.Personal(studentId)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
}
