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
    var rangeIdx by rememberSaveable { mutableIntStateOf(0) }
    var share by remember { mutableStateOf(false) }
    val range = Analytics.Range.entries[rangeIdx]
    val report = remember(c, rules, range) { Analytics.report(c, rules, range) }
    val subjectsWithExams = remember(c.exams) { c.exams.map { it.subject }.filter { it.isNotBlank() }.distinct() }
    var examSubject by rememberSaveable { mutableIntStateOf(0) }
    val examReport = remember(c, examSubject, subjectsWithExams) { Analytics.examReport(c, subjectsWithExams.getOrNull(examSubject - 1)) }
    val reportText = { Analytics.text(c, report, exam = Analytics.examReport(c)) }

    MiuixPage(
        title = "统计", largeTitle = "数据分析",
        actions = {
            IconButton(onClick = { share = true }, modifier = Modifier.padding(end = 16.dp)) { Icon(Icons.Filled.IosShare, "分析报告", tint = colors.onSurface) }
        },
    ) {
        item(key = "mode") {
            Column(Modifier.padding(horizontal = 12.dp)) {
                Text("${c.name} · ${c.students.size} 人 · ${c.assignments.size} 次作业 · ${c.exams.size} 场考试", fontSize = 13.sp, color = colors.onSurfaceVariantSummary, modifier = Modifier.padding(start = 16.dp, bottom = 10.dp))
                HTabs(listOf("作业", "考试"), mode, { mode = it })
            }
        }
        if (mode == 0) homeworkSections(c, report, rangeIdx, { rangeIdx = it }, onOpenAssignment = { kit.nav.push(Route.Assign(it)) }, onOpenStudent = { kit.nav.push(Route.StudentDetail(it)) }, onReport = { share = true })
        else examSections(c, examReport, subjectsWithExams, examSubject, { examSubject = it }, report.examCorrelation, onOpenExam = { kit.nav.push(Route.Exam(it)) }, onManage = { kit.nav.push(Route.Exams) }, onOpenStudent = { kit.nav.push(Route.StudentDetail(it)) })
    }

    ActionSheet(share, "一键生成分析报告", listOf(
        SheetAction("复制文字报告", "${report.range.label}：总体、主要发现、分项、学生、考试") {
            (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("分析报告", reportText()))
            kit.toast("已复制分析报告")
        },
        SheetAction("分享文字报告", "发到家长群或教研记录") { shareReportText(ctx, reportText()) },
        SheetAction("导出 Excel 分析报告", "概览、结论、学生明细、每次作业、科目、小组、积分、考试") { exportExcelReport(ctx, c, report, Analytics.examReport(c)) },
        SheetAction("导出各次统计名单 Excel", "每次作业一行：未交、迟交、请假、未登记名单") {
            val f = java.io.File(com.deepshawn.hwcounter.export.Exporter.exportDir(ctx), "${c.name}_各次作业统计名单_${com.deepshawn.hwcounter.export.Exporter.fileStamp()}.xlsx".replace("/", "_"))
            f.outputStream().use { com.deepshawn.hwcounter.export.Exporter.writeClassStatList(c, it) }
            com.deepshawn.hwcounter.export.Exporter.shareFile(ctx, f, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "分享统计名单")
        },
    ), onDismiss = { share = false })
}

// ---------------- 作业 ----------------

private fun LazyListScope.homeworkSections(
    c: SchoolClass, report: Analytics.Report, rangeIdx: Int, onRange: (Int) -> Unit,
    onOpenAssignment: (String) -> Unit, onOpenStudent: (String) -> Unit, onReport: () -> Unit,
) {
    val k = report.kpi; val p = report.previous
    item(key = "range") {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            HTabs(Analytics.Range.entries.map { it.label }, rangeIdx, onRange, height = 36.dp)
            Text(report.window.label, fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
        }
    }
    if (report.points.isEmpty()) {
        item { EmptyState("📈", if (c.assignments.isEmpty()) "还没有作业数据" else "这个范围里没有作业", "布置并登记几次作业后，这里会出现趋势、热力图和分析报告") }
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
                "${k.assignments} 次作业 · 应交 ${k.expected} 人次 · 缺交 ${k.missing} · 迟交 ${k.late} · 请假 ${k.leave}" + (if (k.unregistered > 0) " · 未登记 ${k.unregistered}" else "") + (if (p != null) " · 对比上一时段 ${p.assignments} 次" else ""),
                fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
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
    group("交齐率分布（人数）", key = "rateBins") { Histogram(report.rateBins, Modifier.padding(16.dp)) }
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
    if (report.improved.isNotEmpty()) group("进步之星（后半段比前半段交得更好）", key = "improved") {
        report.improved.forEach { s ->
            SuperArrow(
                title = s.student.name, summary = "交齐率提升 ${(s.trend * 100).toInt()} 个百分点 · 现在 ${pctText(s.rate)}",
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
            Text("把本页全部分析（含考试）整理成文字报告或多表 Excel，一键分享。", fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            TextButton("生成分析报告", onClick = onReport, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.textButtonColorsPrimary())
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
            Text("最近一场：${last.title} · 平均 ${ls.avg.fmt()} / ${last.fullScore.fmt()} · 标准差 ${ls.std.fmt()}" + if (r.exams.size > 1) " · 对比上一场" else "", fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = Modifier.padding(start = 4.dp))
        }
    }
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
