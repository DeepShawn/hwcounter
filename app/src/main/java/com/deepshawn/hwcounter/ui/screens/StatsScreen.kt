package com.deepshawn.hwcounter.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepshawn.hwcounter.data.Analytics
import com.deepshawn.hwcounter.data.AppData
import com.deepshawn.hwcounter.data.AppViewModel
import com.deepshawn.hwcounter.data.HwStatus
import com.deepshawn.hwcounter.data.compareStudentNumbers
import com.deepshawn.hwcounter.data.fmt
import com.deepshawn.hwcounter.ui.LocalKit
import com.deepshawn.hwcounter.ui.Route
import com.deepshawn.hwcounter.ui.components.Avatar
import com.deepshawn.hwcounter.ui.components.ChartLegend
import com.deepshawn.hwcounter.ui.components.ChartStatusOrder
import com.deepshawn.hwcounter.ui.components.Donut
import com.deepshawn.hwcounter.ui.components.EmptyState
import com.deepshawn.hwcounter.ui.components.HBars
import com.deepshawn.hwcounter.ui.components.Histogram
import com.deepshawn.hwcounter.ui.components.KpiTile
import com.deepshawn.hwcounter.ui.components.MiuixPage
import com.deepshawn.hwcounter.ui.components.StackedColumns
import com.deepshawn.hwcounter.ui.components.TrendLine
import com.deepshawn.hwcounter.ui.components.group
import com.deepshawn.hwcounter.ui.components.pctText
import com.deepshawn.hwcounter.ui.components.statusChartColor
import com.deepshawn.hwcounter.ui.components.statusColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatsScreen(vm: AppViewModel, data: AppData) {
    val kit = LocalKit.current
    val ctx = LocalContext.current
    val c = vm.currentClass(data) ?: run { EmptyState("📊", "还没有班级", ""); return }
    val rules = data.settings.rules
    val colors = MiuixTheme.colorScheme
    var rangeIdx by rememberSaveable { mutableIntStateOf(0) }
    val range = Analytics.Range.entries[rangeIdx]
    val report = remember(c, rules, range) { Analytics.report(c, rules, range) }
    val dateFmt = remember { SimpleDateFormat("M/d", Locale.CHINA) }
    val k = report.kpi
    val p = report.previous

    MiuixPage(title = "统计", largeTitle = "数据分析") {
        item(key = "range") {
            Column(Modifier.padding(horizontal = 12.dp)) {
                Text("${c.name} · ${c.students.size} 人 · 共 ${c.assignments.size} 次作业", fontSize = 13.sp, color = colors.onSurfaceVariantSummary, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
                TabRowWithContour(Analytics.Range.entries.map { it.label }, rangeIdx, { rangeIdx = it })
            }
        }
        if (report.points.isEmpty()) {
            item { EmptyState("📈", if (c.assignments.isEmpty()) "还没有数据" else "这个时间段没有作业", "布置并登记几次作业后，这里会出现趋势、热力图和分析报告") }
            return@MiuixPage
        }
        item(key = "kpi") {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KpiTile("交齐率", pctText(k.handInRate), p?.let { k.handInRate - it.handInRate }, Modifier.weight(1f))
                    KpiTile("按时率", pctText(k.onTimeRate), p?.let { k.onTimeRate - it.onTimeRate }, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KpiTile("优秀率", pctText(k.excellentRate), p?.let { k.excellentRate - it.excellentRate }, Modifier.weight(1f))
                    KpiTile("缺交率 · ${k.missing} 人次", pctText(k.missingRate), p?.let { k.missingRate - it.missingRate }, Modifier.weight(1f), goodWhenUp = false)
                }
                Text(
                    "${k.assignments} 次作业，全员交齐 ${k.perfect} 次" + (if (p != null) " · 对比上一时段 ${p.assignments} 次" else "") + if (range == Analytics.Range.ALL) "" else "",
                    fontSize = 12.sp, color = colors.onSurfaceVariantSummary, modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
        group("交齐率趋势", key = "trend") {
            val pts = report.points
            TrendLine(
                values = pts.map { it.summary.rate },
                labels = pts.map { dateFmt.format(Date(it.assignment.createdAt)) },
                details = pts.map { pt -> "${dateFmt.format(Date(pt.assignment.createdAt))} ${pt.assignment.subject}「${pt.assignment.title}」 ${pctText(pt.summary.rate)} · 缺 ${pt.summary.c(HwStatus.MISSING)} 人" },
                modifier = Modifier.padding(16.dp),
            )
        }
        group("每次作业构成", key = "stack") {
            val pts = report.points
            StackedColumns(
                columns = pts.map { it.summary.counts },
                labels = pts.map { dateFmt.format(Date(it.assignment.createdAt)) },
                details = pts.map { pt -> "${pt.assignment.title}：" + ChartStatusOrder.filter { pt.summary.c(it) > 0 }.joinToString(" ") { "${it.label}${pt.summary.c(it)}" } },
                modifier = Modifier.padding(16.dp),
            )
        }
        group("状态分布", key = "donut") {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Donut(ChartStatusOrder.map { statusChartColor(it) to (report.totals[it] ?: 0).toFloat() }, Modifier.size(132.dp), stroke = 18.dp) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(pctText(k.handInRate), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text("交齐率", fontSize = 11.sp, color = colors.onSurfaceVariantSummary)
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
                            Text(pctText(n.toFloat() / total), fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
                        }
                    }
                }
            }
        }
        item(key = "heat") { Heatmap(c, report, onOpen = { kit.nav.push(Route.Assign(it)) }) }

        if (report.subjects.size > 1) group("各科对比", key = "subjects") {
            HBars(report.subjects.map { Triple(it.subject, it.rate, "${pctText(it.rate)} · ${it.count}次") }, Modifier.padding(16.dp))
        }
        val days = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        val wd = report.weekday.mapIndexedNotNull { i, v -> v?.let { Triple(days[i], it, pctText(it)) } }
        if (wd.size >= 2) group("星期分布（平均交齐率）", key = "weekday") {
            HBars(wd, Modifier.padding(16.dp), color = colors.primary.copy(alpha = 0.85f))
        }

        group("需要关注", key = "attention") {
            if (report.attention.isEmpty()) Text("这段时间没有缺交，太棒了！🎉", Modifier.padding(16.dp), color = colors.onSurfaceVariantSummary)
            report.attention.forEach { s ->
                SuperArrow(
                    title = s.student.name,
                    summary = "缺交 ${s.missing} 次 · 迟交 ${s.late} 次 · 交齐率 ${pctText(s.rate)}" + if (s.missingStreak >= 2) " · ⚠️ 连续 ${s.missingStreak} 次未交" else "",
                    startAction = { Avatar(s.student.name, Modifier.padding(end = 12.dp), size = 36.dp) },
                    onClick = { kit.nav.push(Route.StudentDetail(s.student.id)) },
                )
            }
        }
        if (report.improved.isNotEmpty()) group("进步之星（后半段比前半段交得更好）", key = "improved") {
            report.improved.forEach { s ->
                SuperArrow(
                    title = s.student.name, summary = "交齐率提升 ${(s.trend * 100).toInt()} 个百分点 · 现在 ${pctText(s.rate)}",
                    startAction = { Avatar(s.student.name, Modifier.padding(end = 12.dp), size = 36.dp) },
                    onClick = { kit.nav.push(Route.StudentDetail(s.student.id)) },
                )
            }
        }
        group("全勤之星 ${report.perfectAttendance.size} 人（无缺交、无迟交）", key = "stars") {
            if (report.perfectAttendance.isEmpty()) Text("暂时还没有，继续加油！", Modifier.padding(16.dp), color = colors.onSurfaceVariantSummary)
            else FlowRow(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                report.perfectAttendance.forEach { s ->
                    Text(s.student.name, fontSize = 13.sp, modifier = Modifier.clip(CircleShape).background(colors.secondaryContainer).padding(horizontal = 10.dp, vertical = 4.dp))
                }
            }
        }
        if (report.scoreBins.isNotEmpty()) group("当前积分分布", key = "scores") {
            Column(Modifier.padding(16.dp)) {
                Histogram(report.scoreBins)
                if (report.groups.size > 1) {
                    Spacer(Modifier.height(14.dp))
                    Text("小组平均分", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    val max = report.groups.maxOf { it.second }.coerceAtLeast(1.0)
                    HBars(report.groups.map { Triple(it.first, (it.second / max).toFloat(), it.second.fmt() + " 分") })
                }
            }
        }
        group("分析报告", key = "report") {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("把以上分析整理成一段文字，可直接发到家长群或写进教学记录。", fontSize = 13.sp, color = colors.onSurfaceVariantSummary)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton("复制报告", onClick = {
                        val t = Analytics.text(c, report)
                        (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("分析报告", t))
                        kit.toast("📋 已复制分析报告")
                    }, modifier = Modifier.weight(1f), colors = ButtonDefaults.textButtonColorsPrimary())
                    TextButton("分享…", onClick = {
                        val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, Analytics.text(c, report))
                        ctx.startActivity(Intent.createChooser(i, "分享分析报告"))
                    }, modifier = Modifier.weight(1f))
                }
                TextButton("导出各次统计名单 Excel", onClick = {
                    val f = java.io.File(com.deepshawn.hwcounter.export.Exporter.exportDir(ctx), "${c.name}_各次作业统计名单_${com.deepshawn.hwcounter.export.Exporter.fileStamp()}.xlsx".replace("/", "_"))
                    f.outputStream().use { com.deepshawn.hwcounter.export.Exporter.writeClassStatList(c, it) }
                    com.deepshawn.hwcounter.export.Exporter.shareFile(ctx, f, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "分享统计名单")
                }, modifier = Modifier.fillMaxWidth())
            }
        }
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
        top.yukonga.miuix.kmp.basic.Card(Modifier.padding(horizontal = 12.dp).padding(bottom = 6.dp).fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                TabRowWithContour(listOf("按学号", "按缺交"), sort, { sort = it })
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
