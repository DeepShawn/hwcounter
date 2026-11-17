@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.deepshawn.hwcounter.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepshawn.hwcounter.data.Analytics
import com.deepshawn.hwcounter.data.Attention
import com.deepshawn.hwcounter.data.AttentionStock
import com.deepshawn.hwcounter.data.GroupAnalysis
import com.deepshawn.hwcounter.data.GroupStock
import com.deepshawn.hwcounter.data.HwStatus
import com.deepshawn.hwcounter.data.PlagiarismGraph
import com.deepshawn.hwcounter.data.compareStudentNumbers
import com.deepshawn.hwcounter.data.fmt
import com.deepshawn.hwcounter.data.fmtDay
import com.deepshawn.hwcounter.data.signed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs
import kotlin.math.roundToInt

/** 统计页的选择在切换页面后保持 */
internal object Keep { var mode = 0; var top = 0; var cal = 2; var offset = 0; var from: Long? = null; var to: Long? = null; var examSub = 0; var att = 0; var kind = 0; var span = AttentionStock.Span.M1; var kline = false; var sort = 0; var pill = 0; var plagSel: String? = null }

private val STATUS_ORDER = listOf(HwStatus.SUBMITTED, HwStatus.EXCELLENT, HwStatus.LEAVE, HwStatus.LATE, HwStatus.MISSING, HwStatus.NONE)
private fun statusColor(s: HwStatus) = when (s) { HwStatus.SUBMITTED -> View.SUBMITTED.c(); HwStatus.EXCELLENT -> View.EXCELLENT.c(); HwStatus.LATE -> View.LATE.c(); HwStatus.MISSING -> View.MISSING.c(); HwStatus.LEAVE -> View.LEAVE.c(); HwStatus.NONE -> View.NONE.c() }
private val LEVEL_COLOR = mapOf(Analytics.Level.GOOD to GOOD, Analytics.Level.INFO to BLUE, Analytics.Level.WARN to BAD)

@Composable
fun StatsPage(x: Ctx) {
    var mode by remember { mutableIntStateOf(Keep.mode) }
    var share by remember { mutableStateOf(false) }
    val c = x.c
    Page("数据分析", null, actions = { Text("分析报告", Modifier.clip(CircleShape).clickable { share = true }.padding(horizontal = 12.dp, vertical = 6.dp), color = MiuixTheme.colorScheme.primary) }) {
        Meta("${c.name} · ${c.students.size} 人 · ${c.assignments.size} 次作业 · ${c.exams.size} 场考试")
        Seg(listOf(0 to "作业", 1 to "考试", 2 to "关注", 3 to "小组", 4 to "抄袭"), mode, Modifier.padding(horizontal = 12.dp).padding(bottom = 10.dp)) { mode = it; Keep.mode = it }
        when (mode) {
            0 -> Homework(x)
            1 -> Exams(x)
            2 -> AttentionTab(x)
            3 -> Groups(x)
            else -> Plagiarism(x)
        }
    }
    if (share) ReportDialog(x) { share = false }
}

@Composable private fun Note(t: String) = Text(t, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp), fontSize = 12.sp, color = sub())

@Composable
private fun Insights(items: List<Analytics.Insight>) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        if (items.isEmpty()) Text("数据还不多，暂时没有明显结论。", color = sub())
        items.forEach { i -> Row { Box(Modifier.padding(top = 7.dp).size(8.dp).clip(CircleShape).background(LEVEL_COLOR.getValue(i.level))); Spacer(Modifier.width(10.dp)); Text(i.text, fontSize = 14.sp) } }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Kpi(label: String, value: String, delta: Double?, goodWhenUp: Boolean = true) {
    val d = delta?.let { Math.round(it * 100).toInt() }
    Tile(label, value, note = d?.let { if (it == 0) "持平" else "${if (it > 0) "▲" else "▼"}${abs(it)} 个百分点" }, noteColor = d?.let { if (it == 0) null else if ((it > 0) == goodWhenUp) GOOD else BAD })
}

@Composable
private fun Tabs2(items: List<String>, value: Int, onChange: (Int) -> Unit) = Seg(items.mapIndexed { i, t -> i to t }, value, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), onChange)

// ---------------- 作业 ----------------
@Composable
private fun Homework(x: Ctx) {
    val c = x.c
    var top by remember { mutableIntStateOf(Keep.top) }; var cal by remember { mutableIntStateOf(Keep.cal) }; var offset by remember { mutableIntStateOf(Keep.offset) }
    var from by remember { mutableStateOf(Keep.from ?: (System.currentTimeMillis() - 29 * 86_400_000L)) }; var to by remember { mutableStateOf(Keep.to ?: System.currentTimeMillis()) }
    val cals = listOf(Analytics.Range.YEAR, Analytics.Range.QUARTER, Analytics.Range.MONTH, Analytics.Range.WEEK, Analytics.Range.DAY, Analytics.Range.CUSTOM)
    val spec = when (top) { 0 -> Analytics.Spec(Analytics.Range.ALL); 1 -> Analytics.Spec(Analytics.Range.PERIOD); 2 -> Analytics.Spec(Analytics.Range.RECENT10)
        else -> cals[cal].let { if (it == Analytics.Range.CUSTOM) Analytics.Spec(it, from = from, to = to) else Analytics.Spec(it, offset) } }
    val r by produceState<Analytics.Report?>(null, c, x.rules, spec) { value = withContext(Dispatchers.Default) { Analytics.report(c, x.rules, spec) } }
    Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Seg(listOf(0 to "全部", 1 to "本轮", 2 to "近 10 次", 3 to "按时间"), top) { top = it; offset = 0; Keep.top = it; Keep.offset = 0 }
        if (top == 3) {
            Seg(listOf("年", "季度", "月", "周", "日", "自定义").mapIndexed { i, t -> i to t }, cal) { cal = it; offset = 0; Keep.cal = it; Keep.offset = 0 }
            if (cals[cal] == Analytics.Range.CUSTOM) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateBox(from) { from = it; Keep.from = it; if (it > to) { to = it; Keep.to = it } }; Text("至", color = sub()); DateBox(to) { to = it; Keep.to = it; if (it < from) { from = it; Keep.from = it } }
            } else {
                val (a, b) = Analytics.periodBounds(cals[cal], offset)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("‹", Modifier.clip(CircleShape).clickable { offset--; Keep.offset = offset }.padding(horizontal = 18.dp, vertical = 4.dp), fontSize = 22.sp, color = MiuixTheme.colorScheme.primary)
                    Text(Analytics.periodLabel(cals[cal], a, b, offset), Modifier.weight(1f).clickable { offset = 0; Keep.offset = 0 }, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
                    Text("›", Modifier.clip(CircleShape).clickable(enabled = offset < 0) { offset++; Keep.offset = offset }.padding(horizontal = 18.dp, vertical = 4.dp), fontSize = 22.sp, color = if (offset < 0) MiuixTheme.colorScheme.primary else sub())
                }
            }
        }
        Text(r?.window?.label ?: "正在分析…", Modifier.padding(start = 16.dp, bottom = 6.dp), fontSize = 12.sp, color = sub())
    }
    val rep = r ?: return
    val k = rep.kpi; val p = rep.previous
    if (rep.points.isEmpty()) { Empty("📈", if (c.assignments.isEmpty()) "还没有作业数据" else "这个范围里没有作业，换一个时间范围看看"); rep.progress?.let { ProgressCard(it, x) }; return }
    Group("主要发现") { Insights(rep.insights) }
    Tiles { Kpi("交齐率", pct(k.handInRate), p?.let { (k.handInRate - it.handInRate).toDouble() }); Kpi("按时率", pct(k.onTimeRate), p?.let { (k.onTimeRate - it.onTimeRate).toDouble() }); Kpi("优秀率", pct(k.excellentRate), p?.let { (k.excellentRate - it.excellentRate).toDouble() }) }
    Tiles { Kpi("缺交率", pct(k.missingRate), p?.let { (k.missingRate - it.missingRate).toDouble() }, false); Kpi("迟交率", pct(k.lateRate), p?.let { (k.lateRate - it.lateRate).toDouble() }, false); Kpi("全员交齐", "${k.perfect} 次", null) }
    Meta("${k.assignments} 次作业 · 应交 ${k.expected} 人次 · 缺交 ${k.missing} · 迟交 ${k.late} · 请假 ${k.leave}" + (if (k.unregistered > 0) " · 未登记 ${k.unregistered}" else "") + (p?.let { " · 对比${rep.window.prevLabel} ${it.assignments} 次" } ?: ""))
    rep.progress?.let { ProgressCard(it, x) }
    Group("交齐率趋势") {
        LineChart(rep.points.map { it.summary.rate.toDouble() }, rep.points.map { md(it.assignment.createdAt) }, rep.points.map { "${md(it.assignment.createdAt)} ${it.assignment.subject}「${it.assignment.title}」 ${pct(it.summary.rate)} · 缺 ${it.summary.c(HwStatus.MISSING)} 人" })
        val sl = rep.slope * 100
        if (rep.points.size >= 4) Note("趋势：平均每次${if (sl >= 0) "上升" else "下降"} ${"%.1f".format(abs(sl))} 个百分点" + if (rep.longestPerfectRun >= 2) " · 最长连续 ${rep.longestPerfectRun} 次全员交齐" else "")
    }
    if (rep.buckets.size >= 2) Group("按${rep.bucketUnit}汇总") {
        LineChart(rep.buckets.map { it.rate.toDouble() }, rep.buckets.map { it.label }, rep.buckets.map { "${it.label}：${it.count} 次作业 · 交齐 ${pct(it.rate)} · 按时 ${pct(it.onTimeRate)} · 缺交 ${it.missing} 人次" }, height = 110.dp)
        val b = rep.buckets.maxBy { it.rate }; val w = rep.buckets.minBy { it.rate }
        if (b.rate - w.rate >= 0.03f) Note("最好：${b.label}（${pct(b.rate)}）· 最差：${w.label}（${pct(w.rate)}）")
    }
    Group("每次作业构成") {
        val max = rep.points.maxOf { it.summary.total }.coerceAtLeast(1)
        Row(Modifier.fillMaxWidth().height(150.dp).horizontalScroll(rememberScrollState()).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
            rep.points.forEach { pt ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Column(Modifier.width(18.dp).height(100.dp), verticalArrangement = Arrangement.Bottom) {
                        STATUS_ORDER.reversed().forEach { s -> val n = pt.summary.c(s); if (n > 0) Box(Modifier.fillMaxWidth().height((100f * n / max).dp).background(statusColor(s).copy(alpha = if (s == HwStatus.NONE) 0.35f else 1f))) }
                    }
                    Text(md(pt.assignment.createdAt), fontSize = 9.sp, color = sub())
                }
            }
        }
        Legend(STATUS_ORDER.map { statusColor(it) to it.label })
    }
    Group("状态分布") {
        val total = STATUS_ORDER.sumOf { rep.totals[it] ?: 0 }.coerceAtLeast(1)
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Donut(STATUS_ORDER.map { statusColor(it) to (rep.totals[it] ?: 0) }) { Text(pct(k.handInRate), fontSize = 22.sp, fontWeight = FontWeight.Bold); Text("交齐率", fontSize = 11.sp, color = sub()) }
            Spacer(Modifier.width(18.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                STATUS_ORDER.forEach { s -> val n = rep.totals[s] ?: 0; Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(statusColor(s))); Text("  ${s.label}  $n", Modifier.width(90.dp), fontSize = 13.sp); Text(pct(n.toDouble() / total), fontSize = 12.sp, color = sub()) } }
            }
        }
    }
    Heatmap(x, rep)
    StudentTable(x, rep)
    Tiers(x, rep.tiers)
    Group("交齐率分布（人数）") {
        Histogram(rep.rateBins)
        Note("中位数 ${pct(rep.rateMedian)} · 标准差 ${(rep.rateStd * 100).toInt()} 个百分点" + if (rep.rateStd >= 0.15f) "（两极分化）" else if (rep.rateStd < 0.06f) "（比较整齐）" else "")
    }
    if (rep.risk.isNotEmpty()) Group("缺交风险预警（下一次）") {
        rep.risk.forEach { rk ->
            Cell(rk.student.name, rk.why, right = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(64.dp).height(6.dp).clip(CircleShape).background(track())) { Box(Modifier.fillMaxWidth(rk.prob.toFloat().coerceIn(0.04f, 1f)).height(6.dp).clip(CircleShape).background(if (rk.prob >= 0.6) BAD else WARN)) }
                    Text(pct(rk.prob), Modifier.width(48.dp), textAlign = TextAlign.End, fontWeight = FontWeight.SemiBold)
                }
            }, onClick = { x.open(Route.Student(rk.student.id)) })
        }
        Note("按最近 8 次加权估算（越近权重越大，迟交算 0.4 次缺交），只作提醒参考。")
    }
    if (rep.streakers.isNotEmpty()) Group("连续交齐榜") { Box(Modifier.padding(16.dp)) { Chips(rep.streakers.map { it.student.name to "${it.handStreak} 次" }, GOOD) { x.open(Route.Student(rep.streakers[it].student.id)) } } }
    if (rep.weakSubjects.isNotEmpty()) Group("偏科提醒（某科明显交得少）") {
        rep.weakSubjects.forEach { w -> Cell("${w.student.name} · ${w.subject}", "${w.subject}交齐率 ${pct(w.subjectRate)}（${w.count} 次），整体 ${pct(w.overallRate)}，低 ${((w.overallRate - w.subjectRate) * 100).toInt()} 个百分点", onClick = { x.open(Route.Student(w.student.id)) }) }
    }
    if (rep.scoreTrend.size >= 2) Group("全班平均积分走势") {
        val v = rep.scoreTrend.map { it.second }
        LineChart(v, rep.scoreTrend.map { md(it.first.createdAt) }, rep.scoreTrend.map { (a, s) -> "${md(a.createdAt)}「${a.title}」后 平均 ${s.fmt()} 分" }, v.min(), v.max(), { it.fmt() })
    }
    if (rep.risers.isNotEmpty() || rep.fallers.isNotEmpty()) Group(if (rep.range == Analytics.Range.ALL) "积分涨跌（有记录以来）" else "积分涨跌（本时段）") {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            listOf(Triple("上升", rep.risers, GOOD), Triple("下降", rep.fallers, BAD)).forEach { (t, l, col) ->
                Column(Modifier.weight(1f)) { Text(t, fontSize = 13.sp, color = sub()); if (l.isEmpty()) Text("—", color = sub()); l.forEach { s -> MiniRow(s.student.name, s.scoreDelta.signed(), col) { x.open(Route.Student(s.student.id)) } } }
            }
        }
    }
    Sources(rep)
    if (rep.rounds.size > 1) Group("各轮对比（以全班重置为分界）") {
        val best = rep.rounds.filter { it.kpi.assignments > 0 }.maxOfOrNull { it.kpi.handInRate }
        TableRow(listOf("轮次", "作业", "交齐", "优秀", "考试"), header = true)
        rep.rounds.forEach { rd -> TableRow(listOf(rd.label + (rd.start?.let { "（${md(it)} 起）" } ?: ""), "${rd.kpi.assignments}", if (rd.kpi.assignments == 0) "—" else pct(rd.kpi.handInRate), if (rd.kpi.assignments == 0) "—" else pct(rd.kpi.excellentRate), rd.examPct?.let { pct(it) } ?: "—"),
            highlight = if (best != null && rd.kpi.assignments > 0 && rd.kpi.handInRate >= best) 2 else null) }
    }
    if (rep.groupStats.size > 1) Group("小组对比") {
        rep.groupStats.forEach { g ->
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Text(g.name, fontWeight = FontWeight.Medium); Text(g.leader?.let { "  组长 ${it.name}" } ?: "", Modifier.weight(1f), fontSize = 12.sp, color = sub()); Text("${g.avgScore.fmt()} 分", fontWeight = FontWeight.SemiBold, color = MiuixTheme.colorScheme.primary) }
                HBars(listOf(Triple("交齐", g.rate.toDouble(), pct(g.rate))), labelWidth = 40.dp)
                Text("${g.size} 人 · 缺交 ${g.missing} 次" + (if (rep.range != Analytics.Range.ALL) " · 积分 ${g.scoreDelta.signed()}" else "") + (g.examPct?.let { " · 考试得分率 ${pct(it)}" } ?: ""), fontSize = 12.sp, color = sub())
            }
        }
    }
    if (rep.subjects.size > 1) Group("各科对比") { HBars(rep.subjects.map { Triple(it.subject, it.rate.toDouble(), "${pct(it.rate)} · ${it.count}次") }) }
    val days = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    val wd = rep.weekday.mapIndexedNotNull { i, v -> v?.let { Triple(days[i], it.toDouble(), pct(it)) } }
    if (wd.size >= 2) Group("星期分布（平均交齐率）") { HBars(wd, MiuixTheme.colorScheme.primary) }
    Group("需要关注") {
        if (rep.attention.isEmpty()) Cell("这段时间没有缺交，太棒了！")
        rep.attention.forEach { s -> Cell(s.student.name, "缺交 ${s.missing} 次 · 迟交 ${s.late} 次 · 交齐率 ${pct(s.rate)}" + if (s.missingStreak >= 2) " · 连续 ${s.missingStreak} 次未交" else "", left = { Avatar(s.student.name, 36.dp) }, onClick = { x.open(Route.Student(s.student.id)) }) }
    }
    Group("全勤之星 ${rep.perfectAttendance.size} 人（无缺交、无迟交）") {
        if (rep.perfectAttendance.isEmpty()) Cell("暂时还没有，继续加油！")
        else Box(Modifier.padding(16.dp)) { Chips(rep.perfectAttendance.map { it.student.name to "" }, Color(0xFF8A8A8F)) { x.open(Route.Student(rep.perfectAttendance[it].student.id)) } }
    }
    if (rep.scoreBins.isNotEmpty()) Group("当前积分分布") { Histogram(rep.scoreBins) }
    if (rep.unregistered.isNotEmpty()) Group("数据完整度：${rep.unregistered.size} 次作业有人未登记") {
        rep.unregistered.take(6).forEach { (a, n) -> Cell("${a.subject} ${a.title}".trim(), "${md(a.createdAt)} · $n 人未登记", onClick = { x.open(Route.Assign(a.id)) }) }
    }
}

@Composable
private fun DateBox(t: Long, onChange: (Long) -> Unit) {
    var text by remember(t) { mutableStateOf(java.text.SimpleDateFormat("yyyy-MM-dd").format(java.util.Date(t))) }
    Box(Modifier.width(140.dp).height(36.dp).clip(RoundedCornerShape(10.dp)).background(cardColor()).padding(horizontal = 10.dp), contentAlignment = Alignment.CenterStart) {
        androidx.compose.foundation.text.BasicTextField(text, { v -> text = v; runCatching { java.text.SimpleDateFormat("yyyy-MM-dd").apply { isLenient = false }.parse(v)!!.time }.getOrNull()?.let(onChange) }, singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(color = MiuixTheme.colorScheme.onSurface, fontSize = 14.sp), cursorBrush = androidx.compose.ui.graphics.SolidColor(MiuixTheme.colorScheme.primary))
    }
}

@Composable
private fun TableRow(cells: List<String>, header: Boolean = false, highlight: Int? = null, weights: List<Float>? = null, onClick: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 16.dp, vertical = if (header) 6.dp else 8.dp), verticalAlignment = Alignment.CenterVertically) {
        cells.forEachIndexed { i, t ->
            Text(t, Modifier.weight(weights?.getOrNull(i) ?: if (i == 0) 2f else 1f), fontSize = if (header) 11.sp else 13.5.sp, color = if (header) sub() else if (highlight == i) GOOD else MiuixTheme.colorScheme.onSurface,
                fontWeight = if (highlight == i) FontWeight.SemiBold else FontWeight.Normal, textAlign = if (i == 0) TextAlign.Start else TextAlign.End, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ProgressCard(pg: Analytics.Progress, x: Ctx) {
    var tab by remember { mutableIntStateOf(0) }
    val list = if (tab == 0) pg.stars else pg.watch
    Group("进步之星 · 退步关注") {
        Tabs2(listOf("进步之星 ${pg.stars.size}", "退步关注 ${pg.watch.size}"), tab) { tab = it }
        Note(pg.basis)
        if (list.isEmpty()) Cell(if (tab == 0) "暂时没有明显进步的同学（综合指数提高 5 分以上才算）。" else "没有明显退步的同学，很好！")
        list.forEach { sp -> val v = (sp.index * 100).toInt(); Cell(sp.student.name, sp.reasons.take(2).joinToString(" · ").ifBlank { "综合表现变化" }, left = { Avatar(sp.student.name, 36.dp) }, right = { Pill(if (v >= 0) "+$v" else "$v", if (v >= 0) GOOD else BAD) }, onClick = { x.open(Route.Student(sp.student.id)) }) }
        Note("综合指数 = 交齐率变化×45% + 按时率×20% + 优秀率×15% + 考试得分率×20%（以百分点计）。")
    }
}

@Composable
private fun Heatmap(x: Ctx, rep: Analytics.Report) {
    var sort by remember { mutableIntStateOf(0) }
    val cols = rep.points.map { it.assignment }
    val ins = rep.students.associateBy { it.student.id }
    val rows = if (sort == 1) x.c.students.sortedWith(compareByDescending<com.deepshawn.hwcounter.data.Student> { ins[it.id]?.missing ?: 0 }.thenByDescending { ins[it.id]?.late ?: 0 }) else sortStudents(x.c.students)
    val scroll = rememberScrollState(Int.MAX_VALUE)
    Group("作业热力图（${cols.size} 次）") {
        Tabs2(listOf("按学号", "按缺交"), sort) { sort = it }
        Row(Modifier.padding(16.dp)) {
            Column { Spacer(Modifier.height(16.dp)); rows.forEach { s -> Text(s.name, Modifier.width(64.dp).height(21.dp).clickable { x.open(Route.Student(s.id)) }, fontSize = 11.5.sp, maxLines = 1) } }
            Column(Modifier.weight(1f).horizontalScroll(scroll)) {
                Row { cols.forEach { a -> Text(md(a.createdAt), Modifier.width(21.dp).clickable { x.open(Route.Assign(a.id)) }, fontSize = 8.sp, color = sub(), maxLines = 1) } }
                rows.forEach { s ->
                    Row(Modifier.height(21.dp)) {
                        cols.forEach { a -> val k = viewOf(a, s.id); Box(Modifier.padding(1.5.dp).size(18.dp).clip(RoundedCornerShape(4.dp)).background(k.c().copy(alpha = if (k == View.NONE) 0.22f else 0.92f)).clickable { x.open(Route.Assign(a.id)) }) }
                    }
                }
            }
            Column { Text("交齐率", fontSize = 10.sp, color = sub(), modifier = Modifier.height(16.dp)); rows.forEach { s -> Text(pct(ins[s.id]?.rate ?: 1f), Modifier.width(44.dp).height(21.dp), fontSize = 11.5.sp, textAlign = TextAlign.End, fontWeight = FontWeight.SemiBold) } }
        }
        Legend(listOf(View.SUBMITTED, View.EXCELLENT, View.LEAVE, View.LATE, View.MISSING, View.REVIEW, View.PLAG, View.NONE).map { it.c() to it.label })
        Note("左右滚动查看更早的作业，点格子进入该次作业")
    }
}

@Composable
private fun StudentTable(x: Ctx, rep: Analytics.Report) {
    var sort by remember { mutableIntStateOf(0) }; var all by remember { mutableStateOf(false) }
    val rows = when (sort) {
        0 -> rep.students.sortedWith(compareBy<Analytics.StudentInsight> { it.rate }.thenByDescending { it.missing })
        1 -> rep.students.sortedWith(compareByDescending<Analytics.StudentInsight> { it.missing }.thenByDescending { it.late })
        2 -> rep.students.sortedByDescending { it.score }
        else -> rep.students.sortedBy { it.scoreDelta }
    }
    Group("学生明细（${rep.students.size} 人）") {
        Tabs2(listOf("交齐率", "缺交", "积分", "变化"), sort) { sort = it }
        TableRow(listOf("学生", "交齐率", "缺/迟", "积分"), header = true, weights = listOf(1.3f, 1.5f, 0.7f, 0.9f))
        (if (all) rows else rows.take(10)).forEach { s ->
            Row(Modifier.fillMaxWidth().clickable { x.open(Route.Student(s.student.id)) }.padding(horizontal = 16.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(s.student.name, Modifier.weight(1.3f), maxLines = 1)
                Row(Modifier.weight(1.5f), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).height(6.dp).clip(CircleShape).background(track())) { Box(Modifier.fillMaxWidth(s.rate.coerceIn(0.02f, 1f)).height(6.dp).clip(CircleShape).background(if (s.rate < 0.7f) BAD else if (s.rate < 0.9f) WARN else GOOD)) }
                    Text(pct(s.rate), Modifier.width(44.dp), fontSize = 12.sp, textAlign = TextAlign.End, color = sub())
                }
                Text("${s.missing}/${s.late}", Modifier.weight(0.7f), textAlign = TextAlign.End, color = if (s.missing > 0) BAD else sub())
                Column(Modifier.weight(0.9f), horizontalAlignment = Alignment.End) {
                    Text(s.score.fmt(), fontWeight = FontWeight.SemiBold)
                    if (rep.range != Analytics.Range.ALL && abs(s.scoreDelta) > 1e-9) Text(s.scoreDelta.signed(), fontSize = 10.sp, color = if (s.scoreDelta > 0) GOOD else BAD)
                }
            }
        }
        if (rows.size > 10) More(if (all) "收起" else "显示全部 ${rows.size} 人") { all = !all }
    }
}

@Composable
private fun Tiers(x: Ctx, tiers: List<Analytics.Tier>) {
    val cols = listOf(GOOD, BLUE, WARN, BAD); val total = tiers.sumOf { it.students.size }.coerceAtLeast(1)
    Group("学生分层") {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Split(tiers.mapIndexed { i, t -> t.students.size to cols[i] })
            tiers.forEachIndexed { i, t ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(cols[i])); Text("  ${t.name}", fontWeight = FontWeight.Medium)
                    Text("  ${t.desc}", Modifier.weight(1f), fontSize = 12.sp, color = sub(), maxLines = 1); Text("${t.students.size} 人 · ${pct(t.students.size.toDouble() / total)}", fontWeight = FontWeight.SemiBold)
                }
                if (t.students.isNotEmpty()) Box(Modifier.padding(start = 18.dp)) { Chips(t.students.map { it.student.name to "" }, cols[i]) { x.open(Route.Student(t.students[it].student.id)) } }
            }
        }
    }
}

@Composable
private fun Sources(rep: Analytics.Report) {
    val src = rep.sources
    val items = listOf(Triple("作业自动", src.homework, BLUE), Triple("手动加分", src.manualPlus, GOOD), Triple("手动扣分", src.manualMinus, BAD), Triple("考试计分", src.exam, PURPLE))
    if (items.all { abs(it.second) < 1e-9 } && rep.reasons.isEmpty()) return
    val max = items.maxOf { abs(it.second) }.coerceAtLeast(1.0)
    Group("积分来源（本范围，全班合计）") {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEach { (l, v, col) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(l, Modifier.width(72.dp), fontSize = 13.sp, color = sub())
                    Box(Modifier.weight(1f).height(10.dp).clip(CircleShape).background(track())) { Box(Modifier.fillMaxWidth((abs(v) / max).toFloat().coerceIn(0.01f, 1f)).height(10.dp).clip(CircleShape).background(col)) }
                    Text(if (v == 0.0) "0" else v.signed(), Modifier.width(60.dp), textAlign = TextAlign.End, fontWeight = FontWeight.SemiBold)
                }
            }
            if (rep.reasons.isNotEmpty()) {
                Text("加减分原因（${src.manualCount} 条）", fontSize = 13.sp, color = sub())
                rep.reasons.take(6).forEach { r -> Row { Text(r.reason, Modifier.weight(1f), maxLines = 1); Text("${r.count} 次 · ${r.people} 人  ", fontSize = 12.sp, color = sub()); Text(r.total.signed(), fontWeight = FontWeight.SemiBold, color = if (r.total >= 0) GOOD else BAD) } }
            }
        }
    }
}

// ---------------- 分析报告（文字） ----------------
@Composable
private fun ReportDialog(x: Ctx, onClose: () -> Unit) {
    val text by produceState("正在生成…", x.c, x.rules) { value = withContext(Dispatchers.Default) { Analytics.text(x.c, Analytics.report(x.c, x.rules, Analytics.Spec(Analytics.Range.ALL)), exam = Analytics.examReport(x.c)) } }
    val clip = LocalClipboardManager.current
    var msg by remember { mutableStateOf("") }
    androidx.compose.ui.window.DialogWindow(onCloseRequest = onClose, title = "分析报告", state = androidx.compose.ui.window.rememberDialogState(width = 620.dp, height = 700.dp)) {
        HwTheme(x.state.prefs) {
            Column(Modifier.background(MiuixTheme.colorScheme.surface).padding(16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("复制文字报告", Modifier.clip(RoundedCornerShape(12.dp)).background(MiuixTheme.colorScheme.primary).clickable { clip.setText(AnnotatedString(text)); msg = "已复制分析报告" }.padding(horizontal = 16.dp, vertical = 10.dp), color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text("保存为文本文件", Modifier.clip(RoundedCornerShape(12.dp)).background(cardColor()).clickable {
                        val fd = java.awt.FileDialog(null as java.awt.Frame?, "保存分析报告", java.awt.FileDialog.SAVE).apply { file = "${x.c.name}_分析报告.txt".replace("/", "_"); isVisible = true }
                        if (fd.file != null) runCatching { java.io.File(fd.directory, fd.file).writeText(text) }.onSuccess { msg = "已保存" }.onFailure { msg = "保存失败：${it.message}" }
                    }.padding(horizontal = 16.dp, vertical = 10.dp), fontWeight = FontWeight.SemiBold)
                    Text(msg, color = sub(), fontSize = 13.sp)
                }
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(14.dp)).background(cardColor()).padding(12.dp).verticalScroll(rememberScrollState())) {
                    androidx.compose.foundation.text.selection.SelectionContainer { Text(text, fontSize = 13.sp, lineHeight = 21.sp) }
                }
            }
        }
    }
}


// ---------------- 考试 ----------------
@Composable
private fun Exams(x: Ctx) {
    val c = x.c
    val subjects = remember(c) { c.exams.map { it.subject }.filter { it.isNotBlank() }.distinct() }
    var si by remember { mutableIntStateOf(Keep.examSub.coerceAtMost(subjects.size)) }
    val r = remember(c, si) { Analytics.examReport(c, subjects.getOrNull(si - 1)) }
    val corr by produceState<Double?>(null, c, x.rules) { value = withContext(Dispatchers.Default) { Analytics.report(c, x.rules, Analytics.Spec(Analytics.Range.ALL)).examCorrelation } }
    if (subjects.size > 1) Seg((listOf("全部") + subjects).mapIndexed { i, t -> i to t }, si, Modifier.padding(horizontal = 12.dp).padding(bottom = 10.dp)) { si = it; Keep.examSub = it }
    if (r.exams.isEmpty()) { Empty("📝", "还没有考试成绩：老师录入考试后，这里会出现均分走势、名次进退和薄弱学生"); return }
    Group("主要发现") { Insights(r.insights + listOfNotNull(corr?.takeIf { abs(it) >= 0.3 }?.let { Analytics.Insight(Analytics.Level.INFO, "作业交齐率与考试得分率的相关系数 r = ${"%.2f".format(it)}（${if (it > 0) "正" else "负"}相关）。") })) }
    val last = r.exams.last(); val ls = r.stats.getValue(last.id)
    val prev = r.exams.dropLast(1).lastOrNull()?.let { it to r.stats.getValue(it.id) }
    fun d(f: (com.deepshawn.hwcounter.data.ExamStat) -> Double) = prev?.let { f(ls) - f(it.second) }
    Tiles { Kpi("最近平均得分率", pct(ls.avg / last.fullScore), prev?.let { ls.avg / last.fullScore - it.second.avg / it.first.fullScore }); Kpi("及格率", pct(ls.passRate), d { it.passRate }); Kpi("优秀率", pct(ls.excellentRate), d { it.excellentRate }); Kpi("低分率", pct(ls.lowRate), d { it.lowRate }, false) }
    r.quality.lastOrNull()?.let { q -> Tiles { Kpi("难度", Analytics.difficultyText(q.difficulty), null); Kpi("区分度", "%.2f".format(q.discrimination), null) } }
    Meta("最近一场：${last.title} · 平均 ${ls.avg.fmt()} / ${last.fullScore.fmt()} · 标准差 ${ls.std.fmt()}" + (r.quality.lastOrNull()?.let { " · 区分度${Analytics.discriminationText(it.discrimination)}" } ?: "") + if (r.exams.size > 1) " · 对比上一场" else "")
    if (r.stars.isNotEmpty() || r.watch.isNotEmpty()) {
        var tab by remember { mutableIntStateOf(0) }
        val list = if (tab == 0) r.stars else r.watch
        fun rel(v: Double) = (v * 100).toInt().let { if (it >= 0) "高于平均 $it" else "低于平均 ${-it}" }
        Group("考试进步之星 · 退步关注") {
            Tabs2(listOf("进步之星 ${r.stars.size}", "退步关注 ${r.watch.size}"), tab) { tab = it }
            Note("比较最近一场与之前各场“比班级平均高/低多少”，消除各场难度不同的影响。")
            if (list.isEmpty()) Cell(if (tab == 0) "暂时没有明显进步的同学（相对位置提高 5 个百分点以上）。" else "没有明显退步的同学。")
            list.forEach { p -> val v = (p.delta * 100).toInt(); Cell(p.student.name, "「${p.last.title}」${pct(p.lastPct)}，${rel(p.after)}（之前${rel(p.before)}）", left = { Avatar(p.student.name, 36.dp) }, right = { Pill(if (v >= 0) "+$v" else "$v", if (v >= 0) GOOD else BAD) }, onClick = { x.open(Route.Student(p.student.id)) }) }
        }
    }
    if (r.avgPct.size >= 2) Group("平均得分率走势") { LineChart(r.avgPct.map { it.second }, r.avgPct.map { md(it.first.date) }, r.avgPct.map { (e, v) -> "${md(e.date)} ${e.title} 平均得分率 ${pct(v)}" }) }
    Group("各场考试") { r.exams.asReversed().forEach { e -> val s = r.stats.getValue(e.id); Cell("${e.subject} ${e.title}".trim(), "平均 ${s.avg.fmt()}/${e.fullScore.fmt()} · 及格 ${pct(s.passRate)} · 优秀 ${pct(s.excellentRate)} · ${s.count} 人", onClick = { x.open(Route.Exam(e.id)) }) } }
    if (r.levels.any { it.second > 0 }) Group("成绩层级（按平均得分率）") {
        val cols = listOf(GOOD, BLUE, WARN, BAD); val total = r.levels.sumOf { it.second }.coerceAtLeast(1)
        Column(Modifier.padding(16.dp)) { Split(r.levels.mapIndexed { i, (_, n) -> n to cols[i] }) }
        Legend(r.levels.mapIndexed { i, (l, n) -> cols[i] to "$l $n 人（${pct(n.toDouble() / total)}）" })
        Note("优秀 ≥85%，良好 75–85%，及格 60–75%，不及格 <60%")
    }
    r.flow?.let { f ->
        Group("层级流动：${f.from.title} → ${f.to.title}") {
            val max = f.matrix.flatten().maxOrNull()?.coerceAtLeast(1) ?: 1
            Column(Modifier.padding(16.dp)) {
                Row { Text("上场 → 本场", Modifier.weight(1.2f), fontSize = 11.sp, color = sub()); f.labels.forEach { Text(it, Modifier.weight(1f), fontSize = 12.sp, color = sub(), textAlign = TextAlign.Center) } }
                f.matrix.forEachIndexed { i, row ->
                    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(f.labels[i], Modifier.weight(1.2f), fontSize = 13.sp)
                        row.forEachIndexed { j, n -> val tint = if (j < i) GOOD else if (j > i) BAD else BLUE
                            Box(Modifier.weight(1f).padding(2.dp).height(32.dp).clip(RoundedCornerShape(8.dp)).background(if (n == 0) track() else tint.copy(alpha = 0.12f + 0.5f * n / max)), contentAlignment = Alignment.Center) { Text(if (n == 0) "" else "$n", fontWeight = FontWeight.SemiBold) } }
                    }
                }
                Text("升档 ${f.up} 人 · 降档 ${f.down} 人 · 不变 ${f.same} 人（绿色为升档，红色为降档）", Modifier.padding(top = 8.dp), fontSize = 12.sp, color = sub())
            }
        }
    }
    if (r.quality.size >= 2) Group("各场难度与区分度") {
        TableRow(listOf("考试", "平均得分率", "难度", "区分度"), header = true)
        r.quality.asReversed().forEach { q -> TableRow(listOf("${q.exam.subject} ${q.exam.title}".trim(), pct(q.difficulty), Analytics.difficultyText(q.difficulty), "%.2f ".format(q.discrimination) + Analytics.discriminationText(q.discrimination))) { x.open(Route.Exam(q.exam.id)) } }
        Note("区分度 = 前 27% 与后 27% 学生的得分率之差：≥0.4 很好，0.3–0.4 良好，0.2–0.3 一般，<0.2 偏低。")
    }
    @Composable fun names(title: String, items: List<Pair<com.deepshawn.hwcounter.data.Student, String>>, col: Color) {
        if (items.isEmpty()) return
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { Text(title, fontSize = 13.sp, color = sub()); Spacer(Modifier.height(6.dp)); Chips(items.map { it.first.name to it.second }, col) { x.open(Route.Student(items[it].first.id)) } }
    }
    if (r.nearPass.isNotEmpty() || r.nearExcellent.isNotEmpty()) Group("临界生（重点提升对象）") { names("差一点及格（55%–60%）", r.nearPass.map { it.student to pct(it.avgPct) }, WARN); names("差一点优秀（80%–85%）", r.nearExcellent.map { it.student to pct(it.avgPct) }, BLUE) }
    if (r.rising.isNotEmpty() || r.falling.isNotEmpty() || r.steady.isNotEmpty()) Group("成绩走向") {
        names("连续三场上升", r.rising.map { it.student to pct(it.avgPct) }, GOOD); names("连续三场下滑", r.falling.map { it.student to pct(it.avgPct) }, BAD); names("成绩稳定（波动 < 5 个百分点）", r.steady.map { it.student to pct(it.avgPct) }, PURPLE)
        Note("按每场相对班级平均的位置判断，不受各场难度影响。")
    }
    if (r.diligentLow.isNotEmpty() || r.skipButGood.isNotEmpty()) Group("作业与考试对照") {
        names("作业交得齐但考试不理想（交齐 ≥90%，考试 <60%）", r.diligentLow.map { it.student to "交${pct(it.hwRate)} 考${pct(it.examPct)}" }, WARN)
        names("作业常缺但考试不错（交齐 <75%，考试 ≥80%）", r.skipButGood.map { it.student to "交${pct(it.hwRate)} 考${pct(it.examPct)}" }, BLUE)
    }
    if (r.imbalance.isNotEmpty()) Group("偏科（最好与最差科目相差 ≥ 15 个百分点）") { r.imbalance.forEach { im -> Cell(im.student.name, "${im.best.first} ${pct(im.best.second)} · ${im.worst.first} ${pct(im.worst.second)} · 相差 ${((im.best.second - im.worst.second) * 100).toInt()} 个百分点", onClick = { x.open(Route.Student(im.student.id)) }) } }
    if (r.genders.size == 2) Group("男女生对比") { HBars(r.genders.map { Triple("${it.first}生 ${it.second}人", it.third, pct(it.third)) }, labelWidth = 90.dp) }
    if (r.subjects.size > 1) Group("各科平均得分率") { HBars(r.subjects.map { Triple(it.first, it.second, pct(it.second)) }) }
    if (r.improvers.isNotEmpty() || r.decliners.isNotEmpty()) Group("名次进退（与上一场同科考试比）") {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f)) { Text("进步", fontSize = 13.sp, color = sub()); r.improvers.forEach { s -> MiniRow(s.student.name, "↑${s.rankChange}", GOOD) { x.open(Route.Student(s.student.id)) } }; if (r.improvers.isEmpty()) Text("—") }
            Column(Modifier.weight(1f)) { Text("退步", fontSize = 13.sp, color = sub()); r.decliners.forEach { s -> MiniRow(s.student.name, "↓${-(s.rankChange ?: 0)}", BAD) { x.open(Route.Student(s.student.id)) } }; if (r.decliners.isEmpty()) Text("—") }
        }
    }
    Group("学生考试明细（平均得分率从高到低）") {
        TableRow(listOf("学生", "平均", "最高/最低", "稳定性", "名次"), header = true)
        r.students.filter { it.avgPct != null }.sortedByDescending { it.avgPct }.forEach { s ->
            TableRow(listOf(s.student.name, pct(s.avgPct), "${pct(s.bestPct)}/${pct(s.worstPct)}", s.stdPct?.let { if (it < 0.06) "稳定" else if (it < 0.12) "一般" else "波动大" } ?: "—", s.lastRank?.toString() ?: "—")) { x.open(Route.Student(s.student.id)) }
        }
    }
    if (r.groups.size > 1) Group("小组平均得分率") { HBars(r.groups.map { Triple(it.first, it.second, pct(it.second)) }) }
}

// ---------------- 关注 ----------------
private fun levelColor(l: Attention.Level) = when (l) { Attention.Level.HIGH -> BAD; Attention.Level.MID -> WARN; Attention.Level.LOW -> Color(0xFFD9A400); Attention.Level.OK -> GOOD }

@Composable
private fun AttentionTab(x: Ctx) {
    var att by remember { mutableIntStateOf(Keep.att) }
    val list by produceState<List<Attention.Result>?>(null, x.c, x.rules) { value = withContext(Dispatchers.Default) { Attention.all(x.c, x.rules) } }
    Tabs2(listOf("榜单", "股票"), att) { att = it; Keep.att = it }
    val l = list ?: run { Empty("⏳", "正在计算…"); return }
    if (att == 0) AttentionBoard(x, l) else StockTab(x, l)
}

@Composable
private fun AttentionBoard(x: Ctx, list: List<Attention.Result>) {
    if (list.isEmpty()) { Empty("🎯", "还没有学生"); return }
    val weekly by produceState<Map<String, List<Double>>>(emptyMap(), x.c, x.rules) { value = withContext(Dispatchers.Default) { Attention.weekly(x.c, x.rules) } }
    Group {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("关注度指数（0–100，越高越需要关注）", fontWeight = FontWeight.SemiBold)
            Text("由缺交风险 30%、连续未交 15%、交齐差距 15%、近期下滑 10%、考试偏弱 15%、考试退步 5%、积分靠后 10% 算出；数据一变立即重算，走势为最近 8 周每周一个点。", fontSize = 12.sp, color = sub())
            Split(Attention.Level.entries.map { lv -> list.count { it.level == lv } to levelColor(lv) })
            Row { Attention.Level.entries.forEach { lv -> Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) { Text("${list.count { it.level == lv }}", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = levelColor(lv)); Text(lv.label, fontSize = 11.sp, color = sub()) } } }
        }
    }
    Group("关注榜 · 点同学查看详情") {
        list.forEachIndexed { i, r ->
            val w = weekly[r.student.id].orEmpty(); val delta = if (w.size >= 2) (r.index - w[w.size - 2]).roundToInt() else 0; val col = levelColor(r.level)
            Row(Modifier.fillMaxWidth().clickable { x.open(Route.Student(r.student.id)) }.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}", Modifier.width(28.dp), fontWeight = FontWeight.Bold, color = if (i < 3 && r.level != Attention.Level.OK) col else sub())
                Avatar(r.student.name, 36.dp); Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Text(r.student.name, fontWeight = FontWeight.Medium); Spacer(Modifier.width(6.dp)); Pill(r.level.label, col) }
                    Text(r.top.take(2).joinToString(" · ") { it.detail }.ifBlank { "各项正常" }, fontSize = 11.5.sp, color = sub(), maxLines = 2)
                }
                Spark(w, col); Spacer(Modifier.width(8.dp))
                Column(Modifier.width(52.dp), horizontalAlignment = Alignment.End) {
                    Text("${r.index.roundToInt()}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = col)
                    Text(if (delta > 0) "▲$delta" else if (delta < 0) "▼${-delta}" else "持平", fontSize = 10.sp, color = if (delta > 0) BAD else if (delta < 0) GOOD else sub())
                }
            }
        }
    }
}

private fun p2(x: Double) = "%.2f".format(x)
private fun s2(x: Double) = when { x > 0.005 -> "+" + p2(x); x < -0.005 -> "-" + p2(abs(x)); else -> "0.00" }
private fun candleLabel(k: AttentionStock.Candle) = if (k.end - k.start <= 86_400_000L) md(k.start) else "${md(k.start)}–${md(k.end - 1)}"

@Composable
private fun StockTab(x: Ctx, list: List<Attention.Result>) {
    val c = x.c
    var kind by remember { mutableIntStateOf(Keep.kind) }; var span by remember { mutableStateOf(Keep.span) }; var kline by remember { mutableStateOf(Keep.kline) }
    var sort by remember { mutableIntStateOf(Keep.sort) }; var pill by remember { mutableIntStateOf(Keep.pill) }
    var sel by remember { mutableStateOf<Int?>(null) }
    // 行情在后台线程计算，每个区间算一次就缓存；切换区间时先显示上一次的行情
    val cache = remember(c, x.rules) { HashMap<String, AttentionStock.Board?>() }
    var board by remember(c, x.rules) { mutableStateOf<AttentionStock.Board?>(null) }
    var busy by remember { mutableStateOf(true) }
    LaunchedEffect(c, x.rules, kind, span) {
        val key = "$kind:$span"
        busy = true; sel = null
        val b = cache[key] ?: withContext(Dispatchers.Default) { if (kind == 1) GroupStock.board(c, x.rules, span) else AttentionStock.board(c, x.rules, span) }.also { cache[key] = it }
        board = b; busy = false
        for (sp in AttentionStock.Span.entries) { val k2 = "$kind:$sp"; if (!cache.containsKey(k2)) cache[k2] = withContext(Dispatchers.Default) { if (kind == 1) GroupStock.board(c, x.rules, sp) else AttentionStock.board(c, x.rules, sp) } }
    }
    val us = LocalLook.current.usColors
    val up = if (us) Color(0xFF12A866) else Color(0xFFE5433A); val down = if (us) Color(0xFFE5433A) else Color(0xFF12A866)
    val sc: (Double) -> Color = { v -> if (v > 0.005) up else if (v < -0.005) down else Color(0xFF8E8E93) }
    Tabs2(listOf("个人 · 关注度", "小组 · 综合分"), kind) { kind = it; Keep.kind = it; board = null }
    Tabs2(AttentionStock.Span.entries.map { it.label }, AttentionStock.Span.entries.indexOf(span)) { span = AttentionStock.Span.entries[it]; Keep.span = span }
    val b = board
    if (b == null) { Empty(if (busy) "⏳" else "👥", if (busy) "正在计算行情…" else "还没有小组（每组至少 2 人）"); return }
    val isGroup = kind == 1
    val m = b.market
    Group {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (isGroup) "GRP" else "ATTN", fontWeight = FontWeight.Bold, fontSize = 16.sp); Text("  " + if (isGroup) "小组综合指数 · 各组平均" else "关注指数 · 全班平均", fontSize = 13.sp, color = sub())
                Spacer(Modifier.weight(1f)); if (busy || b.span != span) Text("正在计算${span.label}…", fontSize = 11.sp, color = MiuixTheme.colorScheme.primary)
            }
            val k = sel?.let { m.candles.getOrNull(it) }; val v = k?.close ?: m.last; val ch = v - m.base
            Text(p2(v), fontSize = 34.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Row { Text("${s2(ch)} (" + (if (m.base < 1) "—" else (if (ch >= 0) "+" else "-") + "%.2f%%".format(abs(ch / m.base * 100))) + ")", fontWeight = FontWeight.SemiBold, color = sc(ch)); Text("  " + (k?.let { candleLabel(it) } ?: "近${b.span.label}"), color = sub()) }
            Box(Modifier.width(180.dp)) { Seg(listOf(false to "走势", true to "K 线"), kline, Modifier.padding(vertical = 8.dp)) { kline = it; Keep.kline = it } }
            StockChart(m, kline, sc, sel) { sel = it }
            FlowRow(Modifier.padding(top = 10.dp), maxItemsInEachRow = 4, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                listOf("开盘" to p2(m.base), "最高" to p2(m.high), "前收" to p2(m.prevClose), "最低" to p2(m.low), "成交量" to "${m.volume}", "振幅" to (if (m.base < 1) "—" else "%.2f%%".format((m.high - m.low) / m.base * 100)), "上涨" to "${b.up} 家", "下跌" to "${b.down} 家")
                    .forEach { (l, vv) -> Row(Modifier.width(150.dp).padding(vertical = 3.dp)) { Text(l, Modifier.weight(1f), fontSize = 13.sp, color = sub()); Text(vv, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) } }
            }
            Text(if (isGroup) "综合分越高越好：上涨表示小组在变好。成交量为这段时间的记录条数（作业登记、考试成绩）。" else "指数越高越需要关注：上涨表示情况变差，下跌表示在好转。成交量为这段时间的记录条数（作业登记、加减分、考试成绩）。", Modifier.padding(top = 8.dp), fontSize = 11.sp, color = sub())
        }
    }
    val att = list.associateBy { it.student.id }
    data class R(val id: String, val no: String, val title: String, val sub: String, val q: AttentionStock.Quote, val v: Double, val level: Attention.Level?, val name: String, val open: () -> Unit)
    val rows = if (isGroup) b.stocks.map { (g, q) -> R(g, "", g, "${c.students.count { it.group == g }} 人", q, q.last, null, g) { x.open(Route.Group(g)) } }
    else c.students.mapNotNull { s -> b.stocks[s.id]?.let { q -> R(s.id, s.no, if (s.no.isNotBlank()) "No.${s.no}" else s.name, s.name + (att[s.id]?.top?.firstOrNull()?.let { " · ${it.label}" } ?: ""), q, att[s.id]?.index ?: q.last, att[s.id]?.level, s.name) { x.open(Route.Student(s.id)) } } }
    val ups = rows.filter { it.q.change > 0.005 }.sortedByDescending { it.q.change }; val downs = rows.filter { it.q.change < -0.005 }.sortedBy { it.q.change }
    Row(Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf("领涨" to ups, "领跌" to downs).forEach { (t, l) ->
            Column(Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(cardColor()).padding(12.dp)) { Text(t, fontWeight = FontWeight.SemiBold); if (l.isEmpty()) Text("无", fontSize = 12.sp, color = sub()); l.take(3).forEach { r -> MiniRow(r.name, s2(r.q.change), sc(r.q.change), r.open) } }
        }
    }
    Title((if (isGroup) "小组板块" else "自选股") + " · 点胶囊切换涨跌额 / 涨跌幅" + if (isGroup) "" else " / 等级")
    Row(Modifier.padding(horizontal = 12.dp).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(if (isGroup) "综合分" else "关注度", "涨幅", "跌幅", if (isGroup) "名称" else "学号").forEachIndexed { i, t -> Chip(t, sort == i) { sort = i; Keep.sort = i } }
    }
    val sorted = when (sort) { 1 -> rows.sortedByDescending { it.q.change }; 2 -> rows.sortedBy { it.q.change }; 3 -> rows.sortedWith { a, bb -> compareStudentNumbers(a.no, bb.no).takeIf { it != 0 } ?: a.title.compareTo(bb.title) }; else -> rows.sortedByDescending { it.v } }
    Group {
        sorted.forEach { r ->
            val col = sc(r.q.change); val mode = if (isGroup) pill % 2 else pill
            val txt = when (mode) { 0 -> s2(r.q.change); 1 -> r.q.pct?.let { p -> (if (p > 0.00005) "+" else if (p < -0.00005) "-" else "") + "%.2f%%".format(abs(p * 100)) } ?: "—"; else -> r.level?.label ?: "—" }
            Row(Modifier.fillMaxWidth().clickable(onClick = r.open).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(r.title, fontWeight = FontWeight.Bold, fontSize = 16.sp); Text(r.sub, fontSize = 12.sp, color = sub(), maxLines = 1) }
                MiniChart(r.q, col); Spacer(Modifier.width(12.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(p2(r.v), fontWeight = FontWeight.SemiBold)
                    Text(txt, Modifier.padding(top = 3.dp).width(78.dp).clip(RoundedCornerShape(7.dp)).background(if (mode == 2) r.level?.let { levelColor(it) } ?: col else col).clickable { pill = (pill + 1) % (if (isGroup) 2 else 3); Keep.pill = pill }.padding(vertical = 3.dp),
                        color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

// ---------------- 小组 ----------------
private val TEAM = listOf(0xFF3482FF, 0xFFF76707, 0xFF12B886, 0xFFAE3EC9, 0xFFE64980, 0xFF1098AD, 0xFFF59F00, 0xFF4263EB).map { Color(it) }

@Composable
private fun Groups(x: Ctx) {
    val c = x.c; val rules = x.rules
    val r by produceState<GroupAnalysis.Report?>(null, c, rules) { value = withContext(Dispatchers.Default) { GroupAnalysis.report(c, rules) } }
    val rep = r ?: run { Empty("⏳", "正在分析…"); return }
    if (rep.teams.isEmpty()) { Empty("👥", "还没有小组：在名单里给同学填写小组（每组至少 2 人）后，这里会出现小组对抗和进步分析"); return }
    val names = rep.teams.map { it.name }.sorted()
    val colorOf = names.withIndex().associate { (i, g) -> g to TEAM[i % TEAM.size] }
    Group("小组对抗排名 · 综合分（交齐 50% · 少扣分 25% · 优秀 15% · 考试 10%）") {
        rep.teams.forEach { t ->
            Row(Modifier.fillMaxWidth().clickable { x.open(Route.Group(t.name)) }.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(32.dp)) { if (t.rank <= 3) LineIcon("🏅", listOf(Color(0xFFE8A400), Color(0xFF9AA4B2), Color(0xFFC07A3A))[t.rank - 1], size = 20.dp) else Text("${t.rank}", fontSize = 18.sp, color = sub()) }
                Box(Modifier.size(10.dp).clip(CircleShape).background(colorOf.getValue(t.name))); Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.name + (t.leader?.let { " · 组长 ${it.name}" } ?: ""), fontWeight = FontWeight.Medium)
                    Text("交齐 ${pct(t.rate)} · 人均扣分 ${t.deductPerCapita.fmt()} · 优秀 ${pct(t.excellentRate)}" + (t.examPct?.let { " · 考试 ${pct(it)}" } ?: "") + "\n胜 ${t.wins}" + (if (t.ties > 0) " · 并列 ${t.ties}" else "") + " · 周冠军 ${t.weekWins} · 全组交齐 ${t.complete}/${t.played}" + (if (rules.groupRank.enabled && t.rankPoints != 0.0) " · 排名奖惩每人 ${t.rankPoints.signed()}" else ""), fontSize = 11.5.sp, color = sub())
                }
                Column(horizontalAlignment = Alignment.End) { Text("${(t.score * 100).roundToInt()}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colorOf.getValue(t.name)); Text("综合 · 均分 ${t.avgScore.fmt()}", fontSize = 11.sp, color = sub()) }
            }
        }
    }
    if (rules.groupRank.enabled && rep.periods.isNotEmpty()) {
        val p = rep.periods.last()
        Group("本期排名 · ${rules.groupRank.periodLabel} · ${p.label}") {
            p.standings.forEach { st ->
                Cell(st.group, "综合 ${(st.score * 100).roundToInt()} · 交齐 ${pct(st.rate)} · 人均扣分 ${st.deduct.fmt()} · 优秀 ${pct(st.excellent)}" + (st.exam?.let { " · 考试 ${pct(it)}" } ?: ""),
                    left = { Row(verticalAlignment = Alignment.CenterVertically) { Text("第 ${st.rank} 名", Modifier.width(60.dp), fontSize = 13.sp, color = sub(), fontWeight = FontWeight.SemiBold); Box(Modifier.size(10.dp).clip(CircleShape).background(colorOf[st.group] ?: BLUE)) } },
                    right = { Text(if (st.points == 0.0) "0" else "每人 ${st.points.signed()}", fontWeight = FontWeight.Bold, color = if (st.points > 0) GOOD else if (st.points < 0) BAD else sub()) }, onClick = { x.open(Route.Group(st.group)) })
            }
            Note("第 1 名全组每人 ${rules.groupRank.first.signed()}、第 2 名 ${rules.groupRank.second.signed()}、第 3 名 ${rules.groupRank.third.signed()}；3 组以上时最后一名 ${rules.groupRank.last.signed()}。实时计入积分。")
        }
        if (rep.periods.size >= 2) Group("历次排名") {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rep.periods.asReversed().take(12).forEach { pp ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(pp.label.removeSuffix(" 这周"), Modifier.width(110.dp), fontSize = 12.sp, color = sub(), maxLines = 1)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            pp.standings.forEach { st -> val col = colorOf[st.group] ?: BLUE; Text("${st.rank}. ${st.group}" + if (st.points != 0.0) " ${st.points.signed()}" else "", Modifier.clip(RoundedCornerShape(8.dp)).background(col.copy(alpha = if (st.rank == 1) 0.18f else 0.08f)).padding(horizontal = 8.dp, vertical = 3.dp), fontSize = 12.sp, color = col, fontWeight = if (st.rank == 1) FontWeight.Bold else FontWeight.Normal) }
                        }
                    }
                }
            }
        }
    }
    if (rep.teams.size >= 2 && rep.weeks.isNotEmpty()) Group("每周对抗 · 交齐率最高的组胜出") {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            rep.weeks.asReversed().forEach { w ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(w.label, Modifier.width(90.dp), fontSize = 12.sp, color = sub(), maxLines = 1)
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        names.forEach { g -> val v = w.rates[g]; Box(Modifier.weight(1f).height(18.dp).clip(RoundedCornerShape(5.dp)).background(colorOf.getValue(g).copy(alpha = if (v == null) 0.06f else 0.12f + 0.88f * ((v.toFloat() - 0.5f) / 0.5f).coerceIn(0f, 1f))), contentAlignment = Alignment.Center) { if (g in w.winners) Text("★", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold) } }
                    }
                    Text(if (w.winners.isEmpty()) "平" else w.winners.joinToString("、"), Modifier.width(72.dp).padding(start = 8.dp), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
        Legend(names.map { colorOf.getValue(it) to it } + (Color.Transparent to "颜色越深交齐率越高（50% 以下最浅）· ★ 本周第一"))
    }
    if (rep.progress.isNotEmpty()) Group("小组进步与退步 · ${rep.progressBasis}") {
        rep.progress.forEach { p -> val v = (p.index * 100).roundToInt(); val up = v >= 2; val dn = v <= -2
            Cell(p.group + if (up) " · 进步" else if (dn) " · 退步" else " · 持平", p.reasons.joinToString(" · ").ifBlank { "变化不大" }, left = { LineIcon(if (up) "📈" else if (dn) "📉" else "➖", if (up) GOOD else if (dn) BAD else sub(), size = 18.dp) },
                right = { Text((if (p.index >= 0) "+" else "") + v, fontWeight = FontWeight.Bold, color = if (up) GOOD else if (dn) BAD else sub()) }, onClick = { x.open(Route.Group(p.group)) }) }
        Note("指数 = 交齐率变化 50% + 每次人均扣分减少 30% + 考试得分率变化 20%（没有考试时按比例分配）")
    }
    if (rep.teams.size >= 2) Duel(x, rep, names, colorOf)
    val leaders = rep.teams.filter { it.leader != null }
    if (leaders.isNotEmpty()) Group("组长奖惩 · ${rules.leader.periodLabel}（计入当前积分）") {
        if (!rules.leader.enabled) Cell("组长奖惩已关闭")
        else leaders.sortedByDescending { it.leaderPoints }.forEach { t -> Cell("${t.leader!!.name} · ${t.name}", "${t.leaderPeriods} 段 · 全部作业人均扣分 ${t.deductPerCapita.fmt()}", right = { Text(t.leaderPoints.signed(), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = if (t.leaderPoints > 0) GOOD else if (t.leaderPoints < 0) BAD else sub()) }, onClick = { x.open(Route.Group(t.name)) }) }
    }
}

@Composable
private fun Duel(x: Ctx, rep: GroupAnalysis.Report, names: List<String>, colorOf: Map<String, Color>) {
    var a by remember(names) { mutableStateOf(rep.teams[0].name) }; var b by remember(names) { mutableStateOf(rep.teams[1].name) }
    val d = remember(x.c, a, b) { GroupAnalysis.duel(x.c, a, b) }
    val ta = rep.teams.first { it.name == a }; val tb = rep.teams.first { it.name == b }
    @Composable fun vs(label: String, va: Double, vb: Double, show: (Double) -> String, higher: Boolean = true) {
        val max = maxOf(abs(va), abs(vb), 1e-9); val ab = if (higher) va > vb + 1e-9 else va < vb - 1e-9; val bb = if (higher) vb > va + 1e-9 else vb < va - 1e-9
        Column(Modifier.padding(vertical = 4.dp)) {
            Row { Text(show(va), Modifier.weight(1f), fontWeight = if (ab) FontWeight.Bold else FontWeight.Normal, color = if (ab) colorOf.getValue(a) else MiuixTheme.colorScheme.onSurface); Text(label, fontSize = 12.sp, color = sub()); Text(show(vb), Modifier.weight(1f), textAlign = TextAlign.End, fontWeight = if (bb) FontWeight.Bold else FontWeight.Normal, color = if (bb) colorOf.getValue(b) else MiuixTheme.colorScheme.onSurface) }
            Row(Modifier.fillMaxWidth().padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { Box(Modifier.fillMaxWidth((abs(va) / max).toFloat().coerceIn(0.02f, 1f)).height(6.dp).clip(CircleShape).background(colorOf.getValue(a).copy(alpha = if (ab) 1f else 0.4f))) }
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) { Box(Modifier.fillMaxWidth((abs(vb) / max).toFloat().coerceIn(0.02f, 1f)).height(6.dp).clip(CircleShape).background(colorOf.getValue(b).copy(alpha = if (bb) 1f else 0.4f))) }
            }
        }
    }
    Group("两组对决") {
        Column(Modifier.padding(16.dp)) {
            listOf(a, b).forEachIndexed { w, cur ->
                Row(Modifier.padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    names.forEach { n -> Chip(n, n == cur) { if (w == 0) { if (n != b) a = n else { b = a; a = n } } else { if (n != a) b = n else { a = b; b = n } } } }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) { Text(a, Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colorOf.getValue(a)); Text("${d.aWins} : ${d.bWins}", fontSize = 26.sp, fontWeight = FontWeight.Black); Text(b, Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colorOf.getValue(b), textAlign = TextAlign.End) }
            Text("每次作业比交齐率" + if (d.draws > 0) " · 平 ${d.draws} 次" else "", Modifier.fillMaxWidth().padding(bottom = 6.dp), fontSize = 11.sp, color = sub(), textAlign = TextAlign.Center)
            vs("交齐率", ta.rate, tb.rate, { pct(it) }); vs("按时率", ta.onTimeRate, tb.onTimeRate, { pct(it) }); vs("优秀率", ta.excellentRate, tb.excellentRate, { pct(it) })
            vs("平均积分", ta.avgScore, tb.avgScore, { it.fmt() }); vs("人均作业扣分", ta.deductPerCapita, tb.deductPerCapita, { it.fmt() }, false)
            if (ta.examPct != null && tb.examPct != null) vs("考试得分率", ta.examPct!!, tb.examPct!!, { pct(it) })
            vs("最长连续全组交齐", ta.longestComplete.toDouble(), tb.longestComplete.toDouble(), { "${it.toInt()} 次" }); vs("周冠军", ta.weekWins.toDouble(), tb.weekWins.toDouble(), { "${it.toInt()} 周" })
            if (d.recent.isNotEmpty()) {
                Text("最近 ${d.recent.size} 次", Modifier.padding(top = 6.dp), fontSize = 12.sp, color = sub())
                d.recent.forEach { (asg, ra, rb) -> Row(Modifier.padding(vertical = 2.dp)) { Text(pct(ra), Modifier.width(52.dp), fontSize = 12.sp, color = colorOf.getValue(a), fontWeight = if ((ra ?: -1.0) > (rb ?: -1.0)) FontWeight.Bold else FontWeight.Normal); Text(asg.title, Modifier.weight(1f), fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 1); Text(pct(rb), Modifier.width(52.dp), fontSize = 12.sp, textAlign = TextAlign.End, color = colorOf.getValue(b), fontWeight = if ((rb ?: -1.0) > (ra ?: -1.0)) FontWeight.Bold else FontWeight.Normal) } }
            }
        }
    }
}

// ---------------- 抄袭 ----------------
@Composable
private fun Plagiarism(x: Ctx) {
    val rules = x.rules; val pg = rules.plagiarism
    // 关系图按作业来：每次作业一张图；null = 全部作业汇总
    var sel by remember { mutableStateOf(Keep.plagSel) }
    val list = remember(x.c) { PlagiarismGraph.assignments(x.c) }
    val only = sel?.takeIf { id -> list.any { it.id == id } }
    val one = list.firstOrNull { it.id == only }
    val r = remember(x.c, rules, only) { PlagiarismGraph.report(x.c, rules, only) }
    val text = if (!pg.enabled) "抄袭：按普通未交扣分" else "抄袭：抄袭者和提供者都记一次未交，扣分 ×${pg.multiplier.fmt()}" + (if (pg.escalate) "，累犯逐次加重（第 n 次再 ×n，最多 ×${pg.maxTimes}）" else "") + if (rules.review.enabled) "；同时复审不合格时倍数叠加" else ""
    if (list.isEmpty() && r.cases.isEmpty()) { Empty("🕵️", "还没有抄袭记录。老师在作业里把同学记为抄袭并选出抄了谁的之后，这里会出现关系图。\n$text"); return }
    val pick = { id: String? -> sel = id; Keep.plagSel = id }
    if (list.isNotEmpty()) {
        Text("按作业看关系图 · 每次作业一张图", Modifier.padding(start = 28.dp, bottom = 6.dp), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = sub())
        FlowRow(Modifier.padding(horizontal = 12.dp).padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("全部作业 ${list.size}", only == null) { pick(null) }
            val df = java.text.SimpleDateFormat("M/d", java.util.Locale.CHINA)
            list.forEach { a -> Chip("${df.format(java.util.Date(a.createdAt))} ${a.title.ifBlank { a.subject }} · ${PlagiarismGraph.involvedCount(x.c, a)}人", a.id == only) { pick(a.id) } }
        }
    }
    if (r.cases.isEmpty()) { Empty("🕵️", "这次作业没有抄袭记录，选别的作业或看全部作业汇总。"); return }
    Tiles { Tile("抄袭记录", "${r.cases.size}", PlagColor); Tile("抄袭者", "${r.copiers}", PlagColor); Tile("提供者", "${r.providers}", PlagColor); Tile(if (one != null) "本次扣分" else "共扣分", r.points.fmt(), PlagColor) }
    if (one != null) Group(null) { Cell("${fmtDay(one.createdAt)} · ${one.subject} ${one.title}".trim(), "打开这次作业", onClick = { x.open(Route.Assign(one.id)) }) }
    Meta("$text；改成其他状态即取消。")
    Group(if (one != null) "本次作业的关系图 · 箭头从提供者指向抄袭者" else "全部作业汇总的关系图 · 线越粗次数越多") {
        Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) { PlagGraph(r) { x.open(Route.Student(it)) } }
        Legend(listOf(ProviderColor to "提供者", CopierColor to "抄袭者", BothColor to "既抄又提供"))
    }
    Group(if (one != null) "本次抄袭关系（提供者 → 抄袭者）" else "抄袭关系（提供者 → 抄袭者）") {
        r.cases.filter { it.unknown }.groupBy { it.receiver }.forEach { (rc, list) ->
            Row(Modifier.fillMaxWidth().clickable { x.open(Route.Student(rc.id)) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("提供者不明", color = sub()); Text("  →  ", color = sub()); Text(rc.name, Modifier.weight(1f), color = CopierColor, fontWeight = FontWeight.Medium)
                if (one == null) Text("${list.size} 次", fontWeight = FontWeight.Bold)
            }
        }
        r.edges.forEach { e ->
            Row(Modifier.fillMaxWidth().clickable { x.open(Route.Student(e.receiver.id)) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(e.provider.name, color = ProviderColor, fontWeight = FontWeight.Medium); Text("  →  ", color = sub()); Text(e.receiver.name, Modifier.weight(1f), color = CopierColor, fontWeight = FontWeight.Medium)
                if (one == null) Column(horizontalAlignment = Alignment.End) { Text("${e.count} 次", fontWeight = FontWeight.Bold); Text(e.assignments.takeLast(2).joinToString("、") { it.title }, fontSize = 11.sp, color = sub(), maxLines = 1) }
            }
        }
    }
    Group(if (one != null) "本次涉及的同学" else "涉及的同学（按次数）") {
        r.nodes.forEach { n ->
            Cell(n.student.name, if (one != null) listOfNotNull(plagLine(x.c, one, n.student.id).ifBlank { null }, if (n.roundTimes > 0) "本轮第 ${n.roundTimes} 次" + (if (n.factor > 0) "，扣分 ×${n.factor.fmt()}" else "") else null).joinToString(" · ") else listOfNotNull(if (n.copied > 0) "抄袭 ${n.copied} 次" else null, if (n.provided > 0) "提供 ${n.provided} 次" else null, "本轮第 ${n.roundTimes} 次" + if (pg.escalate && n.roundTimes > 0) "（下次 ×${pg.factor(n.roundTimes + 1).fmt()}）" else "").joinToString(" · "),
                left = { Avatar(n.student.name, 36.dp, roleColor(n)) }, right = { Row(verticalAlignment = Alignment.CenterVertically) { Pill(n.role, roleColor(n)); Text("  " + n.points.fmt(), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = CopierColor) } },
                onClick = { x.open(Route.Student(n.student.id)) })
        }
    }
    if (r.clusters.any { it.size >= 3 }) Group("抄袭圈（互相连在一起的同学）") {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            r.clusters.filter { it.size >= 2 }.forEachIndexed { i, cl -> Text("第 ${i + 1} 圈 · ${cl.size} 人", fontSize = 13.sp, color = sub()); Chips(cl.map { it.name to "" }, PlagColor) { x.open(Route.Student(cl[it].id)) } }
        }
    }
    if (one == null) Group("最近的抄袭记录（点按看这次的关系图）") { r.cases.take(20).forEach { k -> Cell("${k.receiver.name} 抄袭 ${k.providerText}", "${fmtDay(k.assignment.createdAt)} · ${k.assignment.subject} ${k.assignment.title}".trim(), onClick = { pick(k.assignment.id) }) } }
}
