package com.deepshawn.hwcounter.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepshawn.hwcounter.data.Analytics
import com.deepshawn.hwcounter.data.AppData
import com.deepshawn.hwcounter.data.AppViewModel
import com.deepshawn.hwcounter.data.HwStatus
import com.deepshawn.hwcounter.data.SchoolClass
import com.deepshawn.hwcounter.data.compareStudentNumbers
import com.deepshawn.hwcounter.data.fmt
import com.deepshawn.hwcounter.data.signed
import com.deepshawn.hwcounter.ui.LocalKit
import com.deepshawn.hwcounter.ui.Route
import com.deepshawn.hwcounter.ui.components.ActionSheet
import com.deepshawn.hwcounter.ui.components.Avatar
import com.deepshawn.hwcounter.ui.components.ChartLegend
import com.deepshawn.hwcounter.ui.components.ChartStatusOrder
import com.deepshawn.hwcounter.ui.components.Donut
import com.deepshawn.hwcounter.ui.components.EmptyState
import com.deepshawn.hwcounter.ui.components.HBars
import com.deepshawn.hwcounter.ui.components.HCard
import com.deepshawn.hwcounter.ui.components.HTabs
import com.deepshawn.hwcounter.ui.components.Histogram
import com.deepshawn.hwcounter.ui.components.InsightList
import com.deepshawn.hwcounter.ui.components.KpiTile
import com.deepshawn.hwcounter.ui.components.MiuixPage
import com.deepshawn.hwcounter.ui.components.SheetAction
import com.deepshawn.hwcounter.ui.components.StackedColumns
import com.deepshawn.hwcounter.ui.components.TrendLine
import com.deepshawn.hwcounter.ui.components.ValueLine
import com.deepshawn.hwcounter.ui.components.group
import com.deepshawn.hwcounter.ui.components.pctText
import com.deepshawn.hwcounter.ui.components.statusChartColor
import com.deepshawn.hwcounter.ui.components.statusColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.theme.MiuixTheme

private fun pct(x: Double) = "${(x * 100).toInt()}%"

/** 一键分析报告：文字（复制 / 分享）与 Excel */
private fun shareReportText(ctx: Context, text: String) {
    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "分享分析报告"))
}

private fun exportExcelReport(ctx: Context, c: SchoolClass, r: Analytics.Report, e: Analytics.ExamReport) {
    val f = java.io.File(com.deepshawn.hwcounter.export.Exporter.exportDir(ctx), "${c.name}_分析报告_${com.deepshawn.hwcounter.export.Exporter.fileStamp()}.xlsx".replace("/", "_"))
    f.outputStream().use { com.deepshawn.hwcounter.export.AnalysisExport.write(c, r, e, it) }
    com.deepshawn.hwcounter.export.Exporter.shareFile(ctx, f, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "分享分析报告")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatsScreen(vm: AppViewModel, data: AppData) {
    val kit = LocalKit.current
    val ctx = LocalContext.current
    val c = vm.currentClass(data) ?: run { EmptyState("📊", "还没有班级", ""); return }
    val rules = data.settings.rules
    val colors = MiuixTheme.colorScheme
    var mode by rememberSaveable { mutableIntStateOf(0) }
    var share by remember { mutableStateOf(false) }
    val readOnly by vm.readOnly.collectAsState()
    // 范围：全部 / 本轮 / 近 10 次 / 按时间（年 季 月 周 日 自定义）
    var top by rememberSaveable { mutableIntStateOf(0) }
    var calIdx by rememberSaveable { mutableIntStateOf(2) }
    var offset by rememberSaveable { mutableIntStateOf(0) }
    var customFrom by rememberSaveable { mutableStateOf<Long?>(null) }
    var customTo by rememberSaveable { mutableStateOf<Long?>(null) }
    val calRanges = listOf(Analytics.Range.YEAR, Analytics.Range.QUARTER, Analytics.Range.MONTH, Analytics.Range.WEEK, Analytics.Range.DAY, Analytics.Range.CUSTOM)
    val spec = when (top) {
        0 -> Analytics.Spec(Analytics.Range.ALL)
        1 -> Analytics.Spec(Analytics.Range.PERIOD)
        2 -> Analytics.Spec(Analytics.Range.RECENT10)
        else -> calRanges[calIdx].let { r -> if (r == Analytics.Range.CUSTOM) Analytics.Spec(r, from = customFrom ?: (System.currentTimeMillis() - 29 * 86_400_000L), to = customTo ?: System.currentTimeMillis()) else Analytics.Spec(r, offset) }
    }
    // 分析报告在后台计算，不卡页面切换；换范围时先显示上一次的结果，算好再替换
    var reportState by remember { mutableStateOf<Analytics.Report?>(null) }
    androidx.compose.runtime.LaunchedEffect(c, rules, spec) {
        reportState = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { Analytics.report(c, rules, spec) }
    }
    val report = reportState ?: run {
        MiuixPage(title = "统计", largeTitle = "数据分析") {
            item(key = "loading") { Text("正在分析…", Modifier.fillMaxWidth().padding(40.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = colors.onSurfaceVariantSummary) }
        }
        return
    }
    var scoreFor by remember { mutableStateOf<Pair<List<String>, String>?>(null) }
    // 关注度：数据一变就重算（实时追踪）
    val attention = remember(c, rules) { com.deepshawn.hwcounter.data.Attention.all(c, rules) }
    val stockMode = com.deepshawn.hwcounter.ui.theme.LocalUiPrefs.current.stockMode
    val attentionWeekly = remember(c, rules, mode == 2 && !stockMode) { if (mode == 2 && !stockMode) com.deepshawn.hwcounter.data.Attention.weekly(c, rules) else emptyMap() }
    // 股票模式：关注度按美股行情显示
    val stockUi = rememberStockUi()
    val stockBoard = if (mode == 2 && stockMode && stockUi.kind == 0) rememberBoard(c, rules, stockUi.span) else null
    val groupStockBoard = if (mode == 2 && stockMode && stockUi.kind == 1) rememberGroupBoard(c, rules, stockUi.span) else null
    val plagReport = remember(c, rules, mode == 4) { if (mode == 4) com.deepshawn.hwcounter.data.PlagiarismGraph.report(c, rules) else null }
    val groupReport = remember(c, rules, mode == 3) { if (mode == 3) com.deepshawn.hwcounter.data.GroupAnalysis.report(c, rules) else null }
    val subjectsWithExams = remember(c.exams) { c.exams.map { it.subject }.filter { it.isNotBlank() }.distinct() }
    var examSubject by rememberSaveable { mutableIntStateOf(0) }
    val examReport = remember(c, examSubject, subjectsWithExams) { Analytics.examReport(c, subjectsWithExams.getOrNull(examSubject - 1)) }
    val reportText = { Analytics.text(c, report, exam = Analytics.examReport(c)) }

    MiuixPage(
        title = "统计", largeTitle = "数据分析",
        swipe = Triple(5, mode) { mode = it },
        actions = {
            IconButton(onClick = { share = true }, modifier = Modifier.padding(end = 16.dp)) { Icon(Icons.Filled.IosShare, "分析报告", tint = colors.onSurface) }
        },
    ) {
        item(key = "mode") {
            Column(Modifier.padding(horizontal = 12.dp)) {
                Text("${c.name} · ${c.students.size} 人 · ${c.assignments.size} 次作业 · ${c.exams.size} 场考试", fontSize = 13.sp, color = colors.onSurfaceVariantSummary, modifier = Modifier.padding(start = 16.dp, bottom = 10.dp))
                HTabs(listOf("作业", "考试", "关注", "小组", "抄袭"), mode, { mode = it })
            }
        }
        if (mode == 0) {
            item(key = "range") {
                RangePicker(
                    top, { top = it; offset = 0 }, calIdx, { calIdx = it; offset = 0 }, offset, { offset = it },
                    spec, report.window.label, onCustom = { a, b -> customFrom = a; customTo = b },
                )
            }
            homeworkSections(
                c, report, onOpenAssignment = { kit.nav.push(Route.Assign(it)) }, onOpenStudent = { kit.nav.push(Route.StudentDetail(it)) }, onReport = { share = true },
                onScore = if (readOnly) null else { ids, reason -> scoreFor = ids to reason },
                onBatch = if (readOnly) null else { ids, reason -> kit.nav.push(Route.BatchScore(ids, reason)) },
            )
        }
        else if (mode == 4) plagReport?.let { pr -> plagiarismSections(c, rules, pr, { kit.nav.push(Route.StudentDetail(it)) }) { kit.nav.push(Route.Assign(it)) } }
        else if (mode == 3) groupReport?.let { gr -> groupSections(c, rules, gr) { kit.nav.push(Route.Group(it)) } }
        else if (mode == 2) {
            item(key = "att-view") {
                HTabs(
                    listOf("榜单", "股票"), if (stockMode) 1 else 0,
                    { v -> com.deepshawn.hwcounter.ui.theme.UiPrefsStore.update(ctx) { it.copy(stockMode = v == 1) } },
                    Modifier.padding(horizontal = 12.dp, vertical = 6.dp), height = 36.dp,
                )
            }
            if (stockMode) stockSections(c, stockBoard, stockUi, attention, groupStockBoard, { kit.nav.push(Route.GroupStock(it)) }) { kit.nav.push(Route.Stock(it)) }
            else attentionSections(c, rules, attention, attentionWeekly) { kit.nav.push(Route.Personal(it)) }
        }
        else examSections(
            c, examReport, subjectsWithExams, examSubject, { examSubject = it }, report.examCorrelation, onOpenExam = { kit.nav.push(Route.Exam(it)) }, onManage = { kit.nav.push(Route.Exams) }, onOpenStudent = { kit.nav.push(Route.StudentDetail(it)) },
            onScore = if (readOnly) null else { ids, reason -> scoreFor = ids to reason },
            onBatch = if (readOnly) null else { ids, reason -> kit.nav.push(Route.BatchScore(ids, reason)) },
        )
    }

    ActionSheet(share, "一键生成分析报告", listOf(
        SheetAction("复制文字报告", "${report.range.label}：总体、主要发现、分项、学生、考试") {
            (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("分析报告", reportText()))
            kit.toast("已复制分析报告")
        },
        SheetAction("分享文字报告", "发到家长群或教研记录") { shareReportText(ctx, reportText()) },
        SheetAction("导出 PDF 分析报告", "排版好的多页报告：图表、结论、进步与退步、学生明细，可直接打印") {
            runCatching { exportPdfReport(ctx, c, report, Analytics.examReport(c)) }.onFailure { kit.toast("生成 PDF 失败：${it.message}") }
        },
        SheetAction("导出 Excel 分析报告", "概览、结论、学生明细、每次作业、进步退步、各轮、积分来源、考试") { exportExcelReport(ctx, c, report, Analytics.examReport(c)) },
        SheetAction("导出各次统计名单 Excel", "每次作业一行：未交、迟交、请假、未登记名单") {
            val f = java.io.File(com.deepshawn.hwcounter.export.Exporter.exportDir(ctx), "${c.name}_各次作业统计名单_${com.deepshawn.hwcounter.export.Exporter.fileStamp()}.xlsx".replace("/", "_"))
            f.outputStream().use { com.deepshawn.hwcounter.export.Exporter.writeClassStatList(c, it) }
            com.deepshawn.hwcounter.export.Exporter.shareFile(ctx, f, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "分享统计名单")
        },
    ), onDismiss = { share = false })

    scoreFor?.let { (ids, reason) ->
        val targets = c.students.filter { it.id in ids }
        if (targets.isNotEmpty() && !readOnly) ScoreDialog(targets, data.settings.quickReasons, { scoreFor = null }, initialReason = reason) { d, r ->
            vm.addScore(targets.map { it.id }, d, r)
            kit.haptic(true)
            kit.undoable(if (targets.size == 1) "${targets[0].name} ${d.signed()}" else "${targets.size} 人 ${d.signed()}")
            scoreFor = null
        }
    }
}

private fun exportPdfReport(ctx: Context, c: SchoolClass, r: Analytics.Report, e: Analytics.ExamReport) {
    val f = java.io.File(com.deepshawn.hwcounter.export.Exporter.exportDir(ctx), "${c.name}_分析报告_${com.deepshawn.hwcounter.export.Exporter.fileStamp()}.pdf".replace("/", "_"))
    f.outputStream().use { com.deepshawn.hwcounter.export.PdfReport.write(c, r, e, it) }
    com.deepshawn.hwcounter.export.Exporter.shareFile(ctx, f, "application/pdf", "分享 PDF 分析报告")
}

/** 统计范围选择：全部 / 本轮 / 近 10 次 / 按时间（年、季度、月、周、日可前后翻；自定义起止日） */
@Composable
private fun RangePicker(
    top: Int, onTop: (Int) -> Unit, calIdx: Int, onCal: (Int) -> Unit, offset: Int, onOffset: (Int) -> Unit,
    spec: Analytics.Spec, label: String, onCustom: (Long, Long) -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    var pick by remember { mutableIntStateOf(0) } // 1 选开始日 2 选结束日
    Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HTabs(listOf("全部", "本轮", "近 10 次", "按时间"), top, onTop, height = 36.dp)
        if (top == 3) {
            HTabs(listOf("年", "季度", "月", "周", "日", "自定义"), calIdx, onCal, height = 34.dp)
            if (spec.range == Analytics.Range.CUSTOM) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(com.deepshawn.hwcounter.data.fmtDay(spec.from ?: 0L), onClick = { pick = 1 }, modifier = Modifier.weight(1f), minHeight = 36.dp)
                    Text("至", fontSize = 13.sp, color = colors.onSurfaceVariantSummary)
                    TextButton(com.deepshawn.hwcounter.data.fmtDay(spec.to ?: 0L), onClick = { pick = 2 }, modifier = Modifier.weight(1f), minHeight = 36.dp)
                }
            } else {
                val (a, b) = Analytics.periodBounds(spec.range, offset)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton("‹", onClick = { onOffset(offset - 1) }, minWidth = 48.dp, minHeight = 36.dp)
                    Text(
                        Analytics.periodLabel(spec.range, a, b, offset), Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable { onOffset(0) }.padding(vertical = 6.dp),
                        textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    TextButton("›", onClick = { onOffset(offset + 1) }, enabled = offset < 0, minWidth = 48.dp, minHeight = 36.dp)
                }
            }
        }
        Text(label, fontSize = 12.sp, color = colors.onSurfaceVariantSummary, modifier = Modifier.padding(start = 16.dp))
    }
    if (pick != 0) {
        val init = if (pick == 1) spec.from else spec.to
        val state = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = init?.let { it + java.util.TimeZone.getDefault().getOffset(it) })
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { pick = 0 },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    state.selectedDateMillis?.let { utc ->
                        val day = com.deepshawn.hwcounter.data.startOfDay(utc - java.util.TimeZone.getDefault().getOffset(utc) + 12 * 3600_000L)
                        val from = spec.from ?: day; val to = spec.to ?: day
                        if (pick == 1) onCustom(day, maxOf(day, to)) else onCustom(minOf(from, day), day)
                    }
                    pick = 0
                }) { androidx.compose.material3.Text("确定") }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { pick = 0 }) { androidx.compose.material3.Text("取消") } },
        ) { androidx.compose.material3.DatePicker(state) }
    }
}

// ---------------- 作业 ----------------

private fun LazyListScope.homeworkSections(
    c: SchoolClass, report: Analytics.Report,
    onOpenAssignment: (String) -> Unit, onOpenStudent: (String) -> Unit, onReport: () -> Unit,
    onScore: ((List<String>, String) -> Unit)?, onBatch: ((List<String>, String) -> Unit)?,
) {
    val k = report.kpi; val p = report.previous
    if (report.points.isEmpty()) {
        item { EmptyState("📈", if (c.assignments.isEmpty()) "还没有作业数据" else "这个范围里没有作业", if (c.assignments.isEmpty()) "布置并登记几次作业后，这里会出现趋势、热力图和分析报告" else "换一个时间范围，或往前翻看看") }
        report.progress?.let { pg -> item(key = "progress") { ProgressCard(pg, onOpenStudent, onScore, onBatch) } }
        return
    }
    group("主要发现", key = "insights") { InsightList(report.insights, Modifier.padding(16.dp)) }
    item(key = "kpi") {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KpiTile("交齐率", pctText(k.handInRate), p?.let { k.handInRate - it.handInRate }, Modifier.weight(1f))
                KpiTile("按时率", pctText(k.onTimeRate), p?.let { k.onTimeRate - it.onTimeRate }, Modifier.weight(1f))
                KpiTile("优秀率", pctText(k.excellentRate), p?.let { k.excellentRate - it.excellentRate }, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KpiTile("缺交率", pctText(k.missingRate), p?.let { k.missingRate - it.missingRate }, Modifier.weight(1f), goodWhenUp = false)
                KpiTile("迟交率", pctText(k.lateRate), p?.let { k.lateRate - it.lateRate }, Modifier.weight(1f), goodWhenUp = false)
                KpiTile("全员交齐", "${k.perfect} 次", null, Modifier.weight(1f))
            }
            Text(
                "${k.assignments} 次作业 · 应交 ${k.expected} 人次 · 缺交 ${k.missing} · 迟交 ${k.late} · 请假 ${k.leave}" + (if (k.unregistered > 0) " · 未登记 ${k.unregistered}" else "") + (if (p != null) " · 对比${report.window.prevLabel} ${p.assignments} 次" else ""),
                fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
    report.progress?.let { pg -> item(key = "progress") { ProgressCard(pg, onOpenStudent, onScore, onBatch) } }
    val dateFmt = SimpleDateFormat("M/d", Locale.CHINA)
    group("交齐率趋势", key = "trend") {
        val pts = report.points
        TrendLine(
            pts.map { it.summary.rate }, pts.map { dateFmt.format(Date(it.assignment.createdAt)) },
            pts.map { pt -> "${dateFmt.format(Date(pt.assignment.createdAt))} ${pt.assignment.subject}「${pt.assignment.title}」 ${pctText(pt.summary.rate)} · 缺 ${pt.summary.c(HwStatus.MISSING)} 人" },
            Modifier.padding(16.dp),
        )
        val sl = report.slope * 100
        if (pts.size >= 4) Text(
            "趋势：平均每次${if (sl >= 0) "上升" else "下降"} ${"%.1f".format(kotlin.math.abs(sl))} 个百分点" + if (report.longestPerfectRun >= 2) " · 最长连续 ${report.longestPerfectRun} 次全员交齐" else "",
            fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
        )
    }
    if (report.buckets.size >= 2) group("按${report.bucketUnit}汇总", key = "buckets") {
        val b = report.buckets
        TrendLine(
            b.map { it.rate }, b.map { it.label },
            b.map { "${it.label}：${it.count} 次作业 · 交齐 ${pctText(it.rate)} · 按时 ${pctText(it.onTimeRate)} · 缺交 ${it.missing} 人次" },
            Modifier.padding(16.dp), height = 140.dp,
        )
        val best = b.maxBy { it.rate }; val worst = b.minBy { it.rate }
        if (best.rate - worst.rate >= 0.03f) Text(
            "最好：${best.label}（${pctText(best.rate)}）· 最差：${worst.label}（${pctText(worst.rate)}）",
            fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
        )
    }
    group("每次作业构成", key = "stack") {
        val pts = report.points
        StackedColumns(
            pts.map { it.summary.counts }, pts.map { dateFmt.format(Date(it.assignment.createdAt)) },
            pts.map { pt -> "${pt.assignment.title}：" + ChartStatusOrder.filter { pt.summary.c(it) > 0 }.joinToString(" ") { "${it.label}${pt.summary.c(it)}" } },
            Modifier.padding(16.dp),
        )
    }
    group("状态分布", key = "donut") {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Donut(ChartStatusOrder.map { statusChartColor(it) to (report.totals[it] ?: 0).toFloat() }, Modifier.size(128.dp), stroke = 18.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(pctText(k.handInRate), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("交齐率", fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
            }
            Spacer(Modifier.width(18.dp))
            val total = report.totals.values.sum().coerceAtLeast(1)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ChartStatusOrder.forEach { s ->
                    val n = report.totals[s] ?: 0
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(statusChartColor(s)))
                        Spacer(Modifier.width(6.dp))
                        Text("${s.label}  $n", fontSize = 13.sp, modifier = Modifier.width(72.dp))
                        Text(pctText(n.toFloat() / total), fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    }
                }
            }
        }
    }
    item(key = "heat") { Heatmap(c, report, onOpen = onOpenAssignment) }
    item(key = "students") { StudentTable(report, onOpenStudent) }
    item(key = "tiers") { TierCard(report.tiers, onOpenStudent) }
    group("交齐率分布（人数）", key = "rateBins") {
        Histogram(report.rateBins, Modifier.padding(16.dp))
        Text(
            "中位数 ${pctText(report.rateMedian)} · 标准差 ${(report.rateStd * 100).toInt()} 个百分点" + if (report.rateStd >= 0.15f) "（两极分化）" else if (report.rateStd < 0.06f) "（比较整齐）" else "",
            fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
        )
    }
    if (report.risk.isNotEmpty()) group("缺交风险预警（下一次）", key = "risk") {
        Column(Modifier.padding(vertical = 6.dp)) {
            report.risk.forEach { r ->
                Row(Modifier.fillMaxWidth().clickable { onOpenStudent(r.student.id) }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(r.student.name, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text(r.why, fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, maxLines = 2)
                    }
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.width(64.dp).height(6.dp).clip(CircleShape).background(MiuixTheme.colorScheme.secondaryContainer)) {
                        Box(Modifier.fillMaxWidth(r.prob.toFloat().coerceIn(0.04f, 1f)).height(6.dp).clip(CircleShape).background(if (r.prob >= 0.6) Color(0xFFE5384D) else Color(0xFFE8792C)))
                    }
                    Text(pct(r.prob), Modifier.width(44.dp), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
                }
            }
            Text("按最近 8 次加权估算（越近权重越大，迟交算 0.4 次缺交），只作提醒参考。", fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        }
    }
    if (report.streakers.isNotEmpty()) group("连续交齐榜", key = "streak") {
        FlowRow(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            report.streakers.forEach { s ->
                Text("${s.student.name} · ${s.handStreak} 次", fontSize = 13.sp, color = Color(0xFF0F7A52), modifier = Modifier.clip(CircleShape).background(Color(0xFF169B68).copy(alpha = 0.12f)).clickable { onOpenStudent(s.student.id) }.padding(horizontal = 10.dp, vertical = 5.dp))
            }
        }
    }
    if (report.weakSubjects.isNotEmpty()) group("偏科提醒（某科明显交得少）", key = "weak") {
        report.weakSubjects.forEach { w ->
            SuperArrow(
                title = "${w.student.name} · ${w.subject}",
                summary = "${w.subject}交齐率 ${pctText(w.subjectRate)}（${w.count} 次），整体 ${pctText(w.overallRate)}，低 ${((w.overallRate - w.subjectRate) * 100).toInt()} 个百分点",
                onClick = { onOpenStudent(w.student.id) },
            )
        }
    }
    if (report.scoreTrend.size >= 2) group("全班平均积分走势", key = "scoreTrend") {
        ValueLine(
            report.scoreTrend.map { it.second }, report.scoreTrend.map { dateFmt.format(Date(it.first.createdAt)) },
            report.scoreTrend.map { (a, v) -> "${dateFmt.format(Date(a.createdAt))}「${a.title}」后 平均 ${v.fmt()} 分" },
            Modifier.padding(16.dp),
        )
    }
    if (report.risers.isNotEmpty() || report.fallers.isNotEmpty()) group(if (report.range == Analytics.Range.ALL) "积分涨跌（有记录以来）" else "积分涨跌（本时段）", key = "movers") {
        Row(Modifier.padding(16.dp)) {
            Movers("上升", report.risers, Color(0xFF169B68), Modifier.weight(1f), onOpenStudent)
            Spacer(Modifier.width(16.dp))
            Movers("下降", report.fallers, Color(0xFFE5384D), Modifier.weight(1f), onOpenStudent)
        }
    }
    item(key = "sources") { SourcesCard(report) }
    if (report.rounds.size > 1) group("各轮对比（以全班重置为分界）", key = "rounds") {
        Column(Modifier.padding(vertical = 8.dp)) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text("轮次", Modifier.weight(1.6f), fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                Text("作业", Modifier.weight(0.6f), fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, textAlign = TextAlign.End)
                Text("交齐", Modifier.weight(0.7f), fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, textAlign = TextAlign.End)
                Text("优秀", Modifier.weight(0.7f), fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, textAlign = TextAlign.End)
                Text("考试", Modifier.weight(0.7f), fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, textAlign = TextAlign.End)
            }
            val best = report.rounds.filter { it.kpi.assignments > 0 }.maxOfOrNull { it.kpi.handInRate }
            report.rounds.forEach { r ->
                Row(Modifier.padding(horizontal = 16.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1.6f)) {
                        Text(r.label, fontSize = 14.sp, fontWeight = if (r.end == null) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
                        r.start?.let { Text("${dateFmt.format(Date(it))} 起" + (r.reset?.target?.let { t -> " · ${t.fmt()} 分" } ?: ""), fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary) }
                    }
                    Text("${r.kpi.assignments}", Modifier.weight(0.6f), fontSize = 13.sp, textAlign = TextAlign.End)
                    Text(if (r.kpi.assignments == 0) "—" else pctText(r.kpi.handInRate), Modifier.weight(0.7f), fontSize = 13.sp, textAlign = TextAlign.End, fontWeight = FontWeight.SemiBold, color = if (best != null && r.kpi.assignments > 0 && r.kpi.handInRate >= best) Color(0xFF169B68) else MiuixTheme.colorScheme.onSurface)
                    Text(if (r.kpi.assignments == 0) "—" else pctText(r.kpi.excellentRate), Modifier.weight(0.7f), fontSize = 13.sp, textAlign = TextAlign.End)
                    Text(r.examPct?.let { pct(it) } ?: "—", Modifier.weight(0.7f), fontSize = 13.sp, textAlign = TextAlign.End)
                }
            }
        }
    }
    if (report.groupStats.size > 1) group("小组对比", key = "groups") {
        Column(Modifier.padding(vertical = 8.dp)) {
            val max = report.groupStats.maxOf { it.avgScore }.coerceAtLeast(1.0)
            report.groupStats.forEach { g ->
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(g.name, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                        Text(g.leader?.let { "  组长 ${it.name}" } ?: "", fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = Modifier.weight(1f))
                        Text("${g.avgScore.fmt()} 分", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MiuixTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.height(6.dp))
                    HBars(listOf(Triple("交齐", g.rate, pctText(g.rate))), labelWidth = 36.dp)
                    Text(
                        "${g.size} 人 · 缺交 ${g.missing} 次" + (if (report.range != Analytics.Range.ALL) " · 积分 ${g.scoreDelta.signed()}" else "") + (g.examPct?.let { " · 考试得分率 ${pct(it)}" } ?: ""),
                        fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = Modifier.padding(top = 4.dp),
                    )
                    if (max <= 0) Unit
                }
            }
        }
    }
    if (report.subjects.size > 1) group("各科对比", key = "subjects") {
        HBars(report.subjects.map { Triple(it.subject, it.rate, "${pctText(it.rate)} · ${it.count}次") }, Modifier.padding(16.dp))
    }
    val days = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    val wd = report.weekday.mapIndexedNotNull { i, v -> v?.let { Triple(days[i], it, pctText(it)) } }
    if (wd.size >= 2) group("星期分布（平均交齐率）", key = "weekday") { HBars(wd, Modifier.padding(16.dp), color = MiuixTheme.colorScheme.primary.copy(alpha = 0.85f)) }
    group("需要关注", key = "attention") {
        if (report.attention.isEmpty()) Text("这段时间没有缺交，太棒了！", Modifier.padding(16.dp), color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        report.attention.forEach { s ->
            SuperArrow(
                title = s.student.name,
                summary = "缺交 ${s.missing} 次 · 迟交 ${s.late} 次 · 交齐率 ${pctText(s.rate)}" + if (s.missingStreak >= 2) " · 连续 ${s.missingStreak} 次未交" else "",
                startAction = { Avatar(s.student.name, Modifier.padding(end = 12.dp), size = 36.dp) },
                onClick = { onOpenStudent(s.student.id) },
            )
        }
    }
    group("全勤之星 ${report.perfectAttendance.size} 人（无缺交、无迟交）", key = "stars") {
        if (report.perfectAttendance.isEmpty()) Text("暂时还没有，继续加油！", Modifier.padding(16.dp), color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        else FlowRow(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            report.perfectAttendance.forEach { s ->
                Text(s.student.name, fontSize = 13.sp, modifier = Modifier.clip(CircleShape).background(MiuixTheme.colorScheme.secondaryContainer).padding(horizontal = 10.dp, vertical = 4.dp))
            }
        }
    }
    if (report.scoreBins.isNotEmpty()) group("当前积分分布", key = "scores") { Histogram(report.scoreBins, Modifier.padding(16.dp)) }
    if (report.unregistered.isNotEmpty()) group("数据完整度：${report.unregistered.size} 次作业有人未登记", key = "unreg") {
        report.unregistered.take(6).forEach { (a, n) ->
            SuperArrow(title = "${a.subject} ${a.title}".trim(), summary = "${dateFmt.format(Date(a.createdAt))} · $n 人未登记，点击补登记", onClick = { onOpenAssignment(a.id) })
        }
    }
    group("分析报告", key = "report") {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("把本页全部分析（含考试、进步与退步）整理成文字、PDF 或多表 Excel，一键分享。", fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            TextButton("生成分析报告", onClick = onReport, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.textButtonColorsPrimary())
        }
    }
}

/** 进步之星 / 退步关注：以往（重置前）vs 重置后，可直接给这些同学加减分 */
@Composable
private fun ProgressCard(pg: Analytics.Progress, onOpen: (String) -> Unit, onScore: ((List<String>, String) -> Unit)?, onBatch: ((List<String>, String) -> Unit)?) {
    val colors = MiuixTheme.colorScheme
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val list = if (tab == 0) pg.stars else pg.watch
    val good = Color(0xFF169B68); val bad = Color(0xFFE5384D)
    Column {
        SmallTitle("进步之星 · 退步关注")
        HCard(Modifier.padding(horizontal = 12.dp).padding(bottom = 6.dp).fillMaxWidth()) {
            Column(Modifier.padding(vertical = 12.dp)) {
                HTabs(listOf("进步之星 ${pg.stars.size}", "退步关注 ${pg.watch.size}"), tab, { tab = it }, Modifier.padding(horizontal = 12.dp), height = 36.dp)
                Text(pg.basis, fontSize = 12.sp, color = colors.onSurfaceVariantSummary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                if (list.isEmpty()) Text(
                    if (tab == 0) "暂时没有明显进步的同学（综合指数提高 5 分以上才算）。" else "没有明显退步的同学，很好！",
                    fontSize = 14.sp, color = colors.onSurfaceVariantSummary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                list.forEach { sp ->
                    Row(Modifier.fillMaxWidth().clickable { onOpen(sp.student.id) }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(sp.student.name, size = 36.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(sp.student.name, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                Spacer(Modifier.width(6.dp))
                                val v = (sp.index * 100).toInt()
                                Text(if (v >= 0) "+$v" else "$v", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if (v >= 0) good else bad,
                                    modifier = Modifier.clip(CircleShape).background((if (v >= 0) good else bad).copy(alpha = 0.12f)).padding(horizontal = 6.dp, vertical = 1.dp))
                            }
                            Text(sp.reasons.take(2).joinToString(" · ").ifBlank { "综合表现变化" }, fontSize = 12.sp, color = colors.onSurfaceVariantSummary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        if (onScore != null) TextButton(
                            if (tab == 0) "加分" else "加减分", onClick = { onScore(listOf(sp.student.id), if (tab == 0) "进步之星" else "退步提醒") },
                            minHeight = 32.dp, minWidth = 56.dp, insideMargin = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
                if (onBatch != null && list.isNotEmpty()) TextButton(
                    if (tab == 0) "给 ${list.size} 位进步之星加分" else "给 ${list.size} 人批量加减分",
                    onClick = { onBatch(list.map { it.student.id }, if (tab == 0) "进步之星" else "退步提醒") },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp).fillMaxWidth(),
                    colors = if (tab == 0) ButtonDefaults.textButtonColorsPrimary() else ButtonDefaults.textButtonColors(),
                )
                Text("综合指数 = 交齐率变化×45% + 按时率×20% + 优秀率×15% + 考试得分率×20%（以百分点计）。", fontSize = 11.sp, color = colors.onSurfaceVariantSummary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
        }
    }
}

/** 学生分层：全勤 / 稳定 / 波动 / 需关注 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TierCard(tiers: List<Analytics.Tier>, onOpen: (String) -> Unit) {
    val colors = MiuixTheme.colorScheme
    val total = tiers.sumOf { it.students.size }.coerceAtLeast(1)
    var open by rememberSaveable { mutableIntStateOf(-1) }
    val tierColors = listOf(Color(0xFF169B68), Color(0xFF3482FF), Color(0xFFE8792C), Color(0xFFE5384D))
    Column {
        SmallTitle("学生分层")
        HCard(Modifier.padding(horizontal = 12.dp).padding(bottom = 6.dp).fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth().height(12.dp).clip(CircleShape)) {
                    tiers.forEachIndexed { i, t -> if (t.students.isNotEmpty()) Box(Modifier.weight(t.students.size.toFloat()).height(12.dp).background(tierColors[i])) }
                }
                tiers.forEachIndexed { i, t ->
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { open = if (open == i) -1 else i }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(tierColors[i]))
                            Spacer(Modifier.width(8.dp))
                            Text(t.name, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Text("  ${t.desc}", fontSize = 12.sp, color = colors.onSurfaceVariantSummary, modifier = Modifier.weight(1f), maxLines = 1)
                            Text("${t.students.size} 人 · ${pctText(t.students.size.toFloat() / total)}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                        if (t.students.isNotEmpty()) {
                            val shown = if (open == i) t.students else t.students.take(8)
                            FlowRow(Modifier.padding(top = 6.dp, start = 18.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                shown.forEach { s -> Text(s.student.name, fontSize = 12.sp, modifier = Modifier.clip(CircleShape).background(tierColors[i].copy(alpha = 0.12f)).clickable { onOpen(s.student.id) }.padding(horizontal = 9.dp, vertical = 3.dp)) }
                                if (t.students.size > shown.size) Text("+${t.students.size - shown.size}", fontSize = 12.sp, color = colors.onSurfaceVariantSummary, modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 积分来源（作业 / 手动加分 / 手动扣分 / 考试）与常见加减分原因 */
@Composable
private fun SourcesCard(r: Analytics.Report) {
    val colors = MiuixTheme.colorScheme
    val src = r.sources
    val items = listOf(
        Triple("作业自动", src.homework, Color(0xFF3482FF)), Triple("手动加分", src.manualPlus, Color(0xFF169B68)),
        Triple("手动扣分", src.manualMinus, Color(0xFFE5384D)), Triple("考试计分", src.exam, Color(0xFF7A5CFF)),
    )
    val max = items.maxOf { kotlin.math.abs(it.second) }.coerceAtLeast(1.0)
    if (items.all { kotlin.math.abs(it.second) < 1e-9 } && r.reasons.isEmpty()) return
    Column {
        SmallTitle("积分来源（本范围，全班合计）")
        HCard(Modifier.padding(horizontal = 12.dp).padding(bottom = 6.dp).fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items.forEach { (label, v, col) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(label, Modifier.width(64.dp), fontSize = 13.sp, color = colors.onSurfaceSecondary)
                        Box(Modifier.weight(1f).height(10.dp).clip(CircleShape).background(colors.secondaryContainer)) {
                            Box(Modifier.fillMaxWidth((kotlin.math.abs(v) / max).toFloat().coerceIn(0.01f, 1f)).height(10.dp).clip(CircleShape).background(col))
                        }
                        Text(v.signed().let { if (v == 0.0) "0" else it }, Modifier.width(56.dp), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
                    }
                }
                if (r.reasons.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text("加减分原因（${src.manualCount} 条）", fontSize = 13.sp, color = colors.onSurfaceVariantSummary)
                    r.reasons.take(6).forEach { x ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(x.reason, Modifier.weight(1f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${x.count} 次 · ${x.people} 人", fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
                            Text(x.total.signed(), Modifier.width(52.dp), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End, color = if (x.total >= 0) Color(0xFF169B68) else Color(0xFFE5384D))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Movers(title: String, list: List<Analytics.StudentInsight>, color: Color, modifier: Modifier, onOpen: (String) -> Unit) {
    Column(modifier) {
        Text(title, fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = Modifier.padding(bottom = 6.dp))
        if (list.isEmpty()) Text("—", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        list.forEach { s ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onOpen(s.student.id) }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(s.student.name, Modifier.weight(1f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(s.scoreDelta.signed(), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = color)
            }
        }
    }
}

/** 学生明细：可按交齐率 / 缺交 / 积分 / 变化排序，默认显示 10 人 */
@Composable
private fun StudentTable(report: Analytics.Report, onOpen: (String) -> Unit) {
    val colors = MiuixTheme.colorScheme
    var sort by rememberSaveable { mutableIntStateOf(0) }
    var all by rememberSaveable { mutableStateOf(false) }
    val rows = remember(report, sort) {
        when (sort) {
            0 -> report.students.sortedWith(compareBy<Analytics.StudentInsight> { it.rate }.thenByDescending { it.missing })
            1 -> report.students.sortedWith(compareByDescending<Analytics.StudentInsight> { it.missing }.thenByDescending { it.late })
            2 -> report.students.sortedByDescending { it.score }
            else -> report.students.sortedBy { it.scoreDelta }
        }
    }
    Column {
        SmallTitle("学生明细（${report.students.size} 人）")
        HCard(Modifier.padding(horizontal = 12.dp).padding(bottom = 6.dp).fillMaxWidth()) {
            Column(Modifier.padding(vertical = 12.dp)) {
                HTabs(listOf("交齐率", "缺交", "积分", "变化"), sort, { sort = it }, Modifier.padding(horizontal = 12.dp), height = 34.dp)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Text("学生", Modifier.weight(1.3f), fontSize = 11.sp, color = colors.onSurfaceVariantSummary)
                    Text("交齐率", Modifier.weight(1.5f), fontSize = 11.sp, color = colors.onSurfaceVariantSummary)
                    Text("缺/迟", Modifier.weight(0.7f), fontSize = 11.sp, color = colors.onSurfaceVariantSummary, textAlign = TextAlign.Center)
                    Text("积分", Modifier.weight(0.9f), fontSize = 11.sp, color = colors.onSurfaceVariantSummary, textAlign = TextAlign.End)
                }
                (if (all) rows else rows.take(10)).forEach { s ->
                    Row(Modifier.fillMaxWidth().clickable { onOpen(s.student.id) }.padding(horizontal = 16.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(s.student.name, Modifier.weight(1.3f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(Modifier.weight(1.5f), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f).height(6.dp).clip(CircleShape).background(colors.secondaryContainer)) {
                                Box(Modifier.fillMaxWidth(s.rate.coerceIn(0.02f, 1f)).height(6.dp).clip(CircleShape).background(if (s.rate < 0.7f) Color(0xFFE5384D) else if (s.rate < 0.9f) Color(0xFFE8792C) else Color(0xFF169B68)))
                            }
                            Text(pctText(s.rate), Modifier.width(40.dp), fontSize = 12.sp, textAlign = TextAlign.End, color = colors.onSurfaceSecondary)
                        }
                        Text("${s.missing}/${s.late}", Modifier.weight(0.7f), fontSize = 13.sp, textAlign = TextAlign.Center, color = if (s.missing > 0) Color(0xFFE5384D) else colors.onSurfaceSecondary)
                        Column(Modifier.weight(0.9f), horizontalAlignment = Alignment.End) {
                            Text(s.score.fmt(), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            if (report.range != Analytics.Range.ALL && kotlin.math.abs(s.scoreDelta) > 1e-9) Text(s.scoreDelta.signed(), fontSize = 10.sp, color = if (s.scoreDelta > 0) Color(0xFF169B68) else Color(0xFFE5384D))
                        }
                    }
                }
                if (rows.size > 10) TextButton(if (all) "收起" else "显示全部 ${rows.size} 人", onClick = { all = !all }, modifier = Modifier.padding(horizontal = 12.dp).fillMaxWidth())
            }
        }
    }
}

// ---------------- 考试 ----------------

private fun LazyListScope.examSections(
    c: SchoolClass, r: Analytics.ExamReport, subjects: List<String>, subjectIdx: Int, onSubject: (Int) -> Unit, corr: Double?,
    onOpenExam: (String) -> Unit, onManage: () -> Unit, onOpenStudent: (String) -> Unit,
    onScore: ((List<String>, String) -> Unit)? = null, onBatch: ((List<String>, String) -> Unit)? = null,
) {
    if (subjects.size > 1) item(key = "esub") {
        Box(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) { HTabs(listOf("全部") + subjects, subjectIdx, onSubject, height = 36.dp) }
    } else item(key = "esp") { Spacer(Modifier.height(10.dp)) }
    if (r.exams.isEmpty()) {
        item {
            EmptyState("📝", "还没有考试成绩", "考试成绩和作业分开统计：导入成绩表或新建考试后，这里会出现均分走势、名次进退和薄弱学生") {
                TextButton("去录入考试", onClick = onManage, colors = ButtonDefaults.textButtonColorsPrimary())
            }
        }
        return
    }
    group("主要发现", key = "einsights") {
        InsightList(r.insights + listOfNotNull(corr?.takeIf { kotlin.math.abs(it) >= 0.3 }?.let { Analytics.Insight(Analytics.Level.INFO, "作业交齐率与考试得分率的相关系数 r = ${"%.2f".format(it)}（${if (it > 0) "正" else "负"}相关）。") }), Modifier.padding(16.dp))
    }
    val last = r.exams.last(); val ls = r.stats.getValue(last.id)
    item(key = "ekpi") {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val prev = r.exams.dropLast(1).lastOrNull()?.let { it to r.stats.getValue(it.id) }
            fun d(f: (com.deepshawn.hwcounter.data.ExamStat) -> Double) = prev?.let { (f(ls) - f(it.second)).toFloat() }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KpiTile("最近平均得分率", pct(ls.avg / last.fullScore), prev?.let { (ls.avg / last.fullScore - it.second.avg / it.first.fullScore).toFloat() }, Modifier.weight(1f))
                KpiTile("及格率", pct(ls.passRate), d { it.passRate }, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KpiTile("优秀率", pct(ls.excellentRate), d { it.excellentRate }, Modifier.weight(1f))
                KpiTile("低分率", pct(ls.lowRate), d { it.lowRate }, Modifier.weight(1f), goodWhenUp = false)
            }
            r.quality.lastOrNull()?.let { q ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KpiTile("难度", Analytics.difficultyText(q.difficulty), null, Modifier.weight(1f))
                    KpiTile("区分度", "%.2f".format(q.discrimination), null, Modifier.weight(1f))
                }
            }
            Text("最近一场：${last.title} · 平均 ${ls.avg.fmt()} / ${last.fullScore.fmt()} · 标准差 ${ls.std.fmt()}" + (r.quality.lastOrNull()?.let { " · 区分度${Analytics.discriminationText(it.discrimination)}" } ?: "") + if (r.exams.size > 1) " · 对比上一场" else "", fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = Modifier.padding(start = 4.dp))
        }
    }
    if (r.stars.isNotEmpty() || r.watch.isNotEmpty()) item(key = "eprogress") { ExamProgressCard(r, onOpenStudent, onScore, onBatch) }
    if (r.avgPct.size >= 2) group("平均得分率走势", key = "etrend") {
        val f = SimpleDateFormat("M/d", Locale.CHINA)
        TrendLine(r.avgPct.map { it.second.toFloat() }, r.avgPct.map { f.format(Date(it.first.date)) }, r.avgPct.map { (e, v) -> "${f.format(Date(e.date))} ${e.title} 平均得分率 ${pct(v)}" }, Modifier.padding(16.dp))
    }
    group("各场考试", key = "elist") {
        r.exams.asReversed().forEach { e ->
            val s = r.stats.getValue(e.id)
            SuperArrow(title = "${e.subject} ${e.title}".trim(), summary = "平均 ${s.avg.fmt()}/${e.fullScore.fmt()} · 及格 ${pct(s.passRate)} · 优秀 ${pct(s.excellentRate)} · ${s.count} 人", onClick = { onOpenExam(e.id) })
        }
        TextButton("管理考试 / 导入成绩", onClick = onManage, modifier = Modifier.padding(16.dp).fillMaxWidth())
    }
    if (r.levels.any { it.second > 0 }) group("成绩层级（按平均得分率）", key = "elevels") {
        val colorsL = listOf(Color(0xFF169B68), Color(0xFF3482FF), Color(0xFFE8792C), Color(0xFFE5384D))
        val total = r.levels.sumOf { it.second }.coerceAtLeast(1)
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().height(12.dp).clip(CircleShape)) {
                r.levels.forEachIndexed { i, (_, n) -> if (n > 0) Box(Modifier.weight(n.toFloat()).height(12.dp).background(colorsL[i])) }
            }
            ChartLegend(r.levels.mapIndexed { i, (l, n) -> colorsL[i] to "$l $n 人（${pctText(n.toFloat() / total)}）" })
            Text("优秀 ≥85%，良好 75–85%，及格 60–75%，不及格 <60%", fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
    }
    r.flow?.let { f -> group("层级流动：${f.from.title} → ${f.to.title}", key = "eflow") { FlowTable(f) } }
    if (r.quality.size >= 2) group("各场难度与区分度", key = "equality") {
        Column(Modifier.padding(vertical = 8.dp)) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text("考试", Modifier.weight(1.6f), fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                Text("平均得分率", Modifier.weight(1f), fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, textAlign = TextAlign.End)
                Text("难度", Modifier.weight(0.6f), fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, textAlign = TextAlign.End)
                Text("区分度", Modifier.weight(0.9f), fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, textAlign = TextAlign.End)
            }
            r.quality.asReversed().forEach { q ->
                Row(Modifier.fillMaxWidth().clickable { onOpenExam(q.exam.id) }.padding(horizontal = 16.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${q.exam.subject} ${q.exam.title}".trim(), Modifier.weight(1.6f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(pct(q.difficulty), Modifier.weight(1f), fontSize = 13.sp, textAlign = TextAlign.End, fontWeight = FontWeight.SemiBold)
                    Text(Analytics.difficultyText(q.difficulty), Modifier.weight(0.6f), fontSize = 12.sp, textAlign = TextAlign.End, color = MiuixTheme.colorScheme.onSurfaceSecondary)
                    Text("%.2f ".format(q.discrimination) + Analytics.discriminationText(q.discrimination), Modifier.weight(0.9f), fontSize = 12.sp, textAlign = TextAlign.End, color = if (q.discrimination < 0.2) Color(0xFFE8792C) else MiuixTheme.colorScheme.onSurfaceSecondary)
                }
            }
            Text("区分度 = 前 27% 与后 27% 学生的得分率之差：≥0.4 很好，0.3–0.4 良好，0.2–0.3 一般，<0.2 偏低。", fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        }
    }
    if (r.nearPass.isNotEmpty() || r.nearExcellent.isNotEmpty()) group("临界生（重点提升对象）", key = "enear") {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (r.nearPass.isNotEmpty()) NameChips("差一点及格（55%–60%）", r.nearPass.map { it.student to pct(it.avgPct ?: 0.0) }, Color(0xFFE8792C), onOpenStudent)
            if (r.nearExcellent.isNotEmpty()) NameChips("差一点优秀（80%–85%）", r.nearExcellent.map { it.student to pct(it.avgPct ?: 0.0) }, Color(0xFF3482FF), onOpenStudent)
        }
    }
    if (r.rising.isNotEmpty() || r.falling.isNotEmpty() || r.steady.isNotEmpty()) group("成绩走向", key = "etrendStudents") {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (r.rising.isNotEmpty()) NameChips("连续三场上升", r.rising.map { it.student to pct(it.avgPct ?: 0.0) }, Color(0xFF169B68), onOpenStudent)
            if (r.falling.isNotEmpty()) NameChips("连续三场下滑", r.falling.map { it.student to pct(it.avgPct ?: 0.0) }, Color(0xFFE5384D), onOpenStudent)
            if (r.steady.isNotEmpty()) NameChips("成绩稳定（波动 < 5 个百分点）", r.steady.map { it.student to pct(it.avgPct ?: 0.0) }, Color(0xFF7A5CFF), onOpenStudent)
            Text("按每场相对班级平均的位置判断，不受各场难度影响。", fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
    }
    if (r.diligentLow.isNotEmpty() || r.skipButGood.isNotEmpty()) group("作业与考试对照", key = "ehw") {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (r.diligentLow.isNotEmpty()) NameChips("作业交得齐但考试不理想（交齐 ≥90%，考试 <60%）", r.diligentLow.map { it.student to "交${pctText(it.hwRate)} 考${pct(it.examPct)}" }, Color(0xFFE8792C), onOpenStudent)
            if (r.skipButGood.isNotEmpty()) NameChips("作业常缺但考试不错（交齐 <75%，考试 ≥80%）", r.skipButGood.map { it.student to "交${pctText(it.hwRate)} 考${pct(it.examPct)}" }, Color(0xFF3482FF), onOpenStudent)
        }
    }
    if (r.imbalance.isNotEmpty()) group("偏科（最好与最差科目相差 ≥ 15 个百分点）", key = "eimb") {
        r.imbalance.forEach { im ->
            SuperArrow(
                title = im.student.name,
                summary = "${im.best.first} ${pct(im.best.second)} · ${im.worst.first} ${pct(im.worst.second)} · 相差 ${((im.best.second - im.worst.second) * 100).toInt()} 个百分点",
                onClick = { onOpenStudent(im.student.id) },
            )
        }
    }
    if (r.genders.size == 2) group("男女生对比", key = "egender") {
        HBars(r.genders.map { Triple("${it.first}生 ${it.second}人", it.third.toFloat(), pct(it.third)) }, Modifier.padding(16.dp), labelWidth = 72.dp)
    }
    if (r.subjects.size > 1) group("各科平均得分率", key = "esubj") {
        HBars(r.subjects.map { Triple(it.first, it.second.toFloat(), pct(it.second)) }, Modifier.padding(16.dp))
    }
    if (r.improvers.isNotEmpty() || r.decliners.isNotEmpty()) group("名次进退（与上一场同科考试比）", key = "erank") {
        Row(Modifier.padding(16.dp)) {
            Column(Modifier.weight(1f)) {
                Text("进步", fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = Modifier.padding(bottom = 6.dp))
                r.improvers.forEach { s -> RankRow(s.student.name, "↑${s.rankChange}", Color(0xFF169B68)) { onOpenStudent(s.student.id) } }
                if (r.improvers.isEmpty()) Text("—")
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("退步", fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = Modifier.padding(bottom = 6.dp))
                r.decliners.forEach { s -> RankRow(s.student.name, "↓${-(s.rankChange ?: 0)}", Color(0xFFE5384D)) { onOpenStudent(s.student.id) } }
                if (r.decliners.isEmpty()) Text("—")
            }
        }
    }
    group("学生考试明细（平均得分率从高到低）", key = "estudents") {
        Column(Modifier.padding(vertical = 8.dp)) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text("学生", Modifier.weight(1.2f), fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                Text("平均", Modifier.weight(0.8f), fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, textAlign = TextAlign.End)
                Text("最高/最低", Modifier.weight(1.2f), fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, textAlign = TextAlign.End)
                Text("稳定性", Modifier.weight(0.8f), fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, textAlign = TextAlign.End)
                Text("名次", Modifier.weight(0.7f), fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, textAlign = TextAlign.End)
            }
            r.students.filter { it.avgPct != null }.sortedByDescending { it.avgPct }.forEach { s ->
                Row(Modifier.fillMaxWidth().clickable { onOpenStudent(s.student.id) }.padding(horizontal = 16.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(s.student.name, Modifier.weight(1.2f), fontSize = 14.sp, maxLines = 1)
                    Text(pct(s.avgPct!!), Modifier.weight(0.8f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End, color = if (s.avgPct < 0.6) Color(0xFFE5384D) else MiuixTheme.colorScheme.onSurface)
                    Text("${pct(s.bestPct ?: 0.0)}/${pct(s.worstPct ?: 0.0)}", Modifier.weight(1.2f), fontSize = 12.sp, textAlign = TextAlign.End, color = MiuixTheme.colorScheme.onSurfaceSecondary)
                    Text(s.stdPct?.let { if (it < 0.06) "稳定" else if (it < 0.12) "一般" else "波动大" } ?: "—", Modifier.weight(0.8f), fontSize = 12.sp, textAlign = TextAlign.End, color = MiuixTheme.colorScheme.onSurfaceSecondary)
                    Text(s.lastRank?.toString() ?: "—", Modifier.weight(0.7f), fontSize = 13.sp, textAlign = TextAlign.End)
                }
            }
        }
    }
    if (r.groups.size > 1) group("小组平均得分率", key = "egroups") { HBars(r.groups.map { Triple(it.first, it.second.toFloat(), pct(it.second)) }, Modifier.padding(16.dp)) }
}

/** 考试进步之星 / 退步关注：最近一场相对班级平均的位置 vs 之前各场 */
@Composable
private fun ExamProgressCard(r: Analytics.ExamReport, onOpen: (String) -> Unit, onScore: ((List<String>, String) -> Unit)?, onBatch: ((List<String>, String) -> Unit)?) {
    val colors = MiuixTheme.colorScheme
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val list = if (tab == 0) r.stars else r.watch
    val good = Color(0xFF169B68); val bad = Color(0xFFE5384D)
    Column {
        SmallTitle("考试进步之星 · 退步关注")
        HCard(Modifier.padding(horizontal = 12.dp).padding(bottom = 6.dp).fillMaxWidth()) {
            Column(Modifier.padding(vertical = 12.dp)) {
                HTabs(listOf("进步之星 ${r.stars.size}", "退步关注 ${r.watch.size}"), tab, { tab = it }, Modifier.padding(horizontal = 12.dp), height = 36.dp)
                Text("比较最近一场与之前各场“比班级平均高/低多少”，消除各场难度不同的影响。", fontSize = 12.sp, color = colors.onSurfaceVariantSummary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                if (list.isEmpty()) Text(if (tab == 0) "暂时没有明显进步的同学（相对位置提高 5 个百分点以上）。" else "没有明显退步的同学。", fontSize = 14.sp, color = colors.onSurfaceVariantSummary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                list.forEach { p ->
                    Row(Modifier.fillMaxWidth().clickable { onOpen(p.student.id) }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(p.student.name, size = 36.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(p.student.name, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                Spacer(Modifier.width(6.dp))
                                val v = (p.delta * 100).toInt()
                                Text(if (v >= 0) "+$v" else "$v", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if (v >= 0) good else bad,
                                    modifier = Modifier.clip(CircleShape).background((if (v >= 0) good else bad).copy(alpha = 0.12f)).padding(horizontal = 6.dp, vertical = 1.dp))
                            }
                            fun rel(x: Double) = (x * 100).toInt().let { if (it >= 0) "高于平均 $it" else "低于平均 ${-it}" }
                            Text("「${p.last.title}」${pct(p.lastPct)}，${rel(p.after)}（之前${rel(p.before)}）", fontSize = 12.sp, color = colors.onSurfaceVariantSummary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        if (onScore != null) TextButton(
                            if (tab == 0) "加分" else "加减分", onClick = { onScore(listOf(p.student.id), if (tab == 0) "考试进步" else "考试退步提醒") },
                            minHeight = 32.dp, minWidth = 56.dp, insideMargin = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
                if (onBatch != null && list.isNotEmpty()) TextButton(
                    if (tab == 0) "给 ${list.size} 位考试进步之星加分" else "给 ${list.size} 人批量加减分",
                    onClick = { onBatch(list.map { it.student.id }, if (tab == 0) "考试进步" else "考试退步提醒") },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp).fillMaxWidth(),
                    colors = if (tab == 0) ButtonDefaults.textButtonColorsPrimary() else ButtonDefaults.textButtonColors(),
                )
            }
        }
    }
}

/** 层级流动表：行 = 上一场层级，列 = 这一场层级 */
@Composable
private fun FlowTable(f: Analytics.Flow) {
    val colors = MiuixTheme.colorScheme
    Column(Modifier.padding(16.dp)) {
        Row {
            Text("上场 → 本场", Modifier.weight(1.2f), fontSize = 11.sp, color = colors.onSurfaceVariantSummary)
            f.labels.forEach { Text(it, Modifier.weight(1f), fontSize = 12.sp, color = colors.onSurfaceVariantSummary, textAlign = TextAlign.Center) }
        }
        val max = f.matrix.flatten().maxOrNull()?.coerceAtLeast(1) ?: 1
        f.matrix.forEachIndexed { i, row ->
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(f.labels[i], Modifier.weight(1.2f), fontSize = 13.sp)
                row.forEachIndexed { j, n ->
                    val tint = when { j < i -> Color(0xFF169B68); j > i -> Color(0xFFE5384D); else -> colors.primary }
                    Box(Modifier.weight(1f).padding(2.dp).height(32.dp).clip(RoundedCornerShape(8.dp)).background(if (n == 0) colors.secondaryContainer.copy(alpha = 0.5f) else tint.copy(alpha = 0.12f + 0.5f * n / max)), contentAlignment = Alignment.Center) {
                        Text(if (n == 0) "" else "$n", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (n == 0) colors.onSurfaceVariantSummary else colors.onSurface)
                    }
                }
            }
        }
        Text("升档 ${f.up} 人 · 降档 ${f.down} 人 · 不变 ${f.same} 人（绿色为升档，红色为降档）", fontSize = 12.sp, color = colors.onSurfaceVariantSummary, modifier = Modifier.padding(top = 8.dp))
    }
}

/** 带标题的一组名字标签 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NameChips(title: String, items: List<Pair<com.deepshawn.hwcounter.data.Student, String>>, color: Color, onOpen: (String) -> Unit) {
    Column {
        Text(title, fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = Modifier.padding(bottom = 6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items.forEach { (s, tag) ->
                Text("${s.name} · $tag", fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurface, modifier = Modifier.clip(CircleShape).background(color.copy(alpha = 0.13f)).clickable { onOpen(s.id) }.padding(horizontal = 10.dp, vertical = 4.dp))
            }
        }
    }
}

@Composable
private fun RankRow(name: String, tag: String, color: Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(name, Modifier.weight(1f), fontSize = 14.sp, maxLines = 1)
        Text(tag, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}

/**
 * 作业热力图：每行一位同学、每列一次作业，一个 Canvas 画完（大班级也流畅）。
 * 可按学号或按缺交排序；竖线标出最近一次全班积分重置的位置；点格子进入该次作业。
 */
@Composable
private fun Heatmap(c: com.deepshawn.hwcounter.data.SchoolClass, report: Analytics.Report, onOpen: (String) -> Unit) {
    val colors = MiuixTheme.colorScheme
    var sort by rememberSaveable { mutableIntStateOf(0) }
    val cols = report.points.map { it.assignment }
    val insight = remember(report) { report.students.associateBy { it.student.id } }
    val rows = remember(c.students, sort, insight) {
        when (sort) {
            1 -> c.students.sortedWith(compareByDescending<com.deepshawn.hwcounter.data.Student> { insight[it.id]?.missing ?: 0 }.thenByDescending { insight[it.id]?.late ?: 0 })
            else -> c.students.sortedWith { a, b -> compareStudentNumbers(a.no, b.no) }
        }
    }
    // 全班（至少一半同学）参与的最近一次重置 → 在其后的第一列前画竖线
    val boundary = remember(c, cols) {
        val r = c.recharges.filter { it.studentIds.size * 2 >= c.students.size.coerceAtLeast(1) }.maxWithOrNull(compareBy({ it.start }, { it.time }))
        if (r == null) -1 else cols.indexOfFirst { r.counts(it.createdAt) }.takeIf { it > 0 } ?: -1
    }
    val statusColors = HwStatus.entries.associateWith { statusColor(it) }
    val cell = 18.dp; val gap = 3.dp
    val density = LocalDensity.current
    Column {
        top.yukonga.miuix.kmp.basic.SmallTitle("作业热力图（${cols.size} 次）")
        com.deepshawn.hwcounter.ui.components.HCard(Modifier.padding(horizontal = 12.dp).padding(bottom = 6.dp).fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                com.deepshawn.hwcounter.ui.components.HTabs(listOf("按学号", "按缺交"), sort, { sort = it })
                Spacer(Modifier.height(10.dp))
                Row {
                    Column(Modifier.width(60.dp)) {
                        rows.forEach { s ->
                            Text(s.name, Modifier.height(cell + gap).padding(end = 4.dp), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = colors.onSurface)
                        }
                    }
                    Box(Modifier.weight(1f).horizontalScroll(rememberScrollState(Int.MAX_VALUE))) {
                        Canvas(
                            Modifier.size(width = (cell + gap) * cols.size, height = (cell + gap) * rows.size)
                                .semantics { contentDescription = "作业热力图，${rows.size} 位同学 × ${cols.size} 次作业" }
                                .pointerInput(cols, rows) {
                                    detectTapGestures { o ->
                                        val step = with(density) { (cell + gap).toPx() }
                                        val ci = (o.x / step).toInt()
                                        if (ci in cols.indices) onOpen(cols[ci].id)
                                    }
                                },
                        ) {
                            val step = (cell + gap).toPx(); val sz = cell.toPx(); val r = CornerRadius(4.dp.toPx())
                            rows.forEachIndexed { ri, s ->
                                cols.forEachIndexed { ci, a ->
                                    val st = a.statuses[s.id] ?: HwStatus.NONE
                                    drawRoundRect(statusColors.getValue(st).copy(alpha = if (st == HwStatus.NONE) 0.22f else 0.92f), Offset(ci * step, ri * step), Size(sz, sz), r)
                                }
                            }
                            if (boundary > 0) {
                                val x = boundary * step - gap.toPx() / 2
                                drawLine(colors.primary, Offset(x, 0f), Offset(x, size.height), 2.dp.toPx())
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                ChartLegend(ChartStatusOrder.map { statusChartColor(it).copy(alpha = if (it == HwStatus.NONE) 0.3f else 1f) to it.label })
                Text(
                    "左右滑动查看更早的作业，点任意一列进入该次作业" + if (boundary > 0) " · 蓝色竖线：全班积分重置" else "",
                    fontSize = 11.sp, color = colors.onSurfaceVariantSummary, modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}
