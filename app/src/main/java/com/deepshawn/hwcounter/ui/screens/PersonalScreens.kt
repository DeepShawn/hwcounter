@file:OptIn(ExperimentalLayoutApi::class)

package com.deepshawn.hwcounter.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepshawn.hwcounter.data.Analytics
import com.deepshawn.hwcounter.data.AppData
import com.deepshawn.hwcounter.data.AppViewModel
import com.deepshawn.hwcounter.data.Attention
import com.deepshawn.hwcounter.data.HwStatus
import com.deepshawn.hwcounter.data.Personal
import com.deepshawn.hwcounter.data.SchoolClass
import com.deepshawn.hwcounter.data.ScoreRules
import com.deepshawn.hwcounter.data.fmt
import com.deepshawn.hwcounter.data.fmtDay
import com.deepshawn.hwcounter.data.signed
import com.deepshawn.hwcounter.export.Exporter
import com.deepshawn.hwcounter.export.PdfReport
import com.deepshawn.hwcounter.ui.LocalKit
import com.deepshawn.hwcounter.ui.Route
import com.deepshawn.hwcounter.ui.components.ActionSheet
import com.deepshawn.hwcounter.ui.components.Avatar
import com.deepshawn.hwcounter.ui.components.EmptyState
import com.deepshawn.hwcounter.ui.components.HBars
import com.deepshawn.hwcounter.ui.components.HCard
import com.deepshawn.hwcounter.ui.components.MiuixPage
import com.deepshawn.hwcounter.ui.components.ProgressRing
import com.deepshawn.hwcounter.ui.components.SheetAction
import com.deepshawn.hwcounter.ui.components.TrendLine
import com.deepshawn.hwcounter.ui.components.ValueLine
import com.deepshawn.hwcounter.ui.components.animatedText
import com.deepshawn.hwcounter.ui.components.group
import com.deepshawn.hwcounter.ui.components.listCell
import com.deepshawn.hwcounter.ui.components.statusColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val GOOD = Color(0xFF169B68)
private val BAD = Color(0xFFE5384D)

fun levelColor(l: Attention.Level) = when (l) {
    Attention.Level.HIGH -> Color(0xFFE5384D)
    Attention.Level.MID -> Color(0xFFE8792C)
    Attention.Level.LOW -> Color(0xFFD9A400)
    Attention.Level.OK -> Color(0xFF169B68)
}

private fun pct(x: Double) = "${(x * 100).roundToInt()}%"

/** 小走势线（关注榜每行右侧） */
@Composable
private fun Spark(values: List<Double>, color: Color, modifier: Modifier) {
    if (values.size < 2) return
    Canvas(modifier) {
        val lo = 0.0; val hi = maxOf(100.0, values.max())
        fun x(i: Int) = size.width * i / (values.size - 1)
        fun y(v: Double) = (size.height * (1 - (v - lo) / (hi - lo))).toFloat().coerceIn(1f, size.height - 1f)
        val p = Path().apply { values.forEachIndexed { i, v -> if (i == 0) moveTo(x(i), y(v)) else lineTo(x(i), y(v)) } }
        drawPath(p, color, style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(color, 2.5.dp.toPx(), Offset(x(values.lastIndex), y(values.last())))
    }
}

// ---------------- 统计页：关注榜 ----------------

/** 关注榜：按关注度指数从高到低排，显示等级、主要原因、近 8 周走势和较上周的变化；数据一变立即重算 */
fun LazyListScope.attentionSections(c: SchoolClass, rules: ScoreRules, list: List<Attention.Result>, weekly: Map<String, List<Double>>, onOpen: (String) -> Unit) {
    if (list.isEmpty()) { item { EmptyState("🎯", "还没有学生", "") }; return }
    item(key = "att-head") {
        val colors = MiuixTheme.colorScheme
        HCard(Modifier.padding(horizontal = 12.dp, vertical = 10.dp).fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("关注度指数（0–100，越高越需要关注）", fontWeight = FontWeight.SemiBold)
                Text("由缺交风险 30%、连续未交 15%、交齐差距 15%、近期下滑 10%、考试偏弱 15%、考试退步 5%、积分靠后 10% 算出；数据一变立即重算，走势为最近 8 周每周一个点。", fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
                val byLevel = Attention.Level.entries.associateWith { l -> list.count { it.level == l } }
                Row(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape)) {
                    Attention.Level.entries.forEach { l -> val n = byLevel.getValue(l); if (n > 0) Box(Modifier.weight(n.toFloat()).height(10.dp).background(levelColor(l))) }
                }
                Row {
                    Attention.Level.entries.forEach { l ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(animatedText("${byLevel.getValue(l)}"), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = levelColor(l))
                            Text(l.label, fontSize = 11.sp, color = colors.onSurfaceVariantSummary)
                        }
                    }
                }
            }
        }
    }
    item(key = "att-title") { SmallTitle("关注榜 · 点同学看个人分析") }
    itemsIndexed(list, key = { _, r -> "att" + r.student.id }) { i, r ->
        val colors = MiuixTheme.colorScheme
        val w = weekly[r.student.id].orEmpty()
        val delta = if (w.size >= 2) r.index - w[w.size - 2] else 0.0
        val col = levelColor(r.level)
        Row(
            Modifier.listCell(i, list.size).clickable { onOpen(r.student.id) }.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${i + 1}", Modifier.width(26.dp), fontWeight = FontWeight.Bold, color = if (i < 3 && r.level != Attention.Level.OK) col else colors.onSurfaceVariantSummary)
            Avatar(r.student.name, size = 36.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(r.student.name, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(r.level.label, fontSize = 10.sp, color = col, modifier = Modifier.clip(CircleShape).background(col.copy(alpha = 0.12f)).padding(horizontal = 6.dp, vertical = 1.dp))
                }
                Text(r.top.take(2).joinToString(" · ") { it.detail }.ifBlank { "各项正常" }, fontSize = 11.sp, color = colors.onSurfaceVariantSummary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spark(w, col, Modifier.width(48.dp).height(22.dp))
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(48.dp)) {
                Text(animatedText("${r.index.roundToInt()}"), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = col)
                val d = delta.roundToInt()
                Text(if (d > 0) "▲$d" else if (d < 0) "▼${-d}" else "持平", fontSize = 10.sp, color = if (d > 0) BAD else if (d < 0) GOOD else colors.onSurfaceVariantSummary)
            }
        }
    }
}

// ---------------- 个人分析报告 ----------------

private fun share(ctx: Context, c: SchoolClass, name: String, write: (java.io.OutputStream) -> Unit) {
    val f = java.io.File(Exporter.exportDir(ctx), "${c.name}_${name}_${Exporter.fileStamp()}.pdf".replace("/", "_"))
    f.outputStream().use(write)
    Exporter.shareFile(ctx, f, "application/pdf", "分享$name")
}

@Composable
fun PersonalReportScreen(vm: AppViewModel, data: AppData, studentId: String) {
    val kit = LocalKit.current
    val ctx = LocalContext.current
    val colors = MiuixTheme.colorScheme
    val c = vm.currentClass(data) ?: return
    val rules = data.settings.rules
    val r = remember(c, rules, studentId) { Personal.report(c, rules, studentId) } ?: run { EmptyState("👤", "找不到这位同学", ""); return }
    var exporting by remember { mutableStateOf(false) }
    val df = remember { SimpleDateFormat("M/d", Locale.CHINA) }
    val s = r.student

    MiuixPage(
        title = s.name, largeTitle = "${s.name} · 个人分析", onBack = { kit.nav.pop() },
        actions = { IconButton(onClick = { exporting = true }, modifier = Modifier.padding(end = 16.dp)) { Icon(Icons.Filled.IosShare, "导出报告", tint = colors.onSurface) } },
    ) {
        // 概览
        item(key = "hero") {
            HCard(Modifier.padding(horizontal = 12.dp).fillMaxWidth(), onClick = { kit.nav.push(Route.StudentDetail(s.id)) }) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(s.name, size = 54.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(listOf(s.no, s.group, s.gender).filter { it.isNotBlank() }.joinToString(" · "), fontSize = 13.sp, color = colors.onSurfaceSecondary)
                        Text("积分第 ${r.rank}/${r.of} 名" + if (r.percentile >= 0.01 && r.rank > 1) " · 超过 ${pct(r.percentile)} 的同学" else if (r.rank == 1) " · 全班第一" else "", fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
                        Text(animatedText(r.score.fmt()), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = colors.primary)
                    }
                    ProgressRing((r.attention.index / 100).toFloat(), size = 74.dp, stroke = 7.dp, track = colors.secondaryContainer, colors = listOf(levelColor(r.attention.level), levelColor(r.attention.level))) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(animatedText("${r.attention.index.roundToInt()}"), fontSize = 19.sp, fontWeight = FontWeight.Bold, color = levelColor(r.attention.level))
                            Text(r.attention.level.label, fontSize = 9.sp, color = colors.onSurfaceVariantSummary)
                        }
                    }
                }
            }
        }
        // 优点、问题、建议
        if (r.strengths.isNotEmpty() || r.concerns.isNotEmpty()) group("综合评价", key = "eval") {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (r.strengths.isNotEmpty()) Bullets("优点", r.strengths, GOOD)
                if (r.concerns.isNotEmpty()) Bullets("需要注意", r.concerns, BAD)
                if (r.suggestions.isNotEmpty()) Bullets("建议", r.suggestions, colors.primary)
            }
        }
        // 关注度
        group("关注度构成与走势", key = "att") {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (r.attentionTrend.size >= 2) TrendLine(r.attentionTrend.map { (it / 100).toFloat() }, r.attentionTrend.indices.map { if (it == r.attentionTrend.lastIndex) "本周" else "${r.attentionTrend.size - 1 - it}周前" },
                    r.attentionTrend.mapIndexed { i, v -> "${if (i == r.attentionTrend.lastIndex) "现在" else "${r.attentionTrend.size - 1 - i} 周前"} 关注度 ${v.roundToInt()}" }, height = 110.dp)
                r.attention.parts.forEach { p ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(p.label, Modifier.width(64.dp), fontSize = 13.sp)
                        Box(Modifier.weight(1f).height(8.dp).clip(CircleShape).background(colors.secondaryContainer)) {
                            Box(Modifier.fillMaxWidth(p.value.toFloat().coerceIn(0.02f, 1f)).height(8.dp).clip(CircleShape).background(levelColor(Attention.levelOf(p.value * 100))))
                        }
                        Text("${p.points.roundToInt()}", Modifier.width(30.dp), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
                    }
                    Text(p.detail, fontSize = 11.sp, color = colors.onSurfaceVariantSummary, modifier = Modifier.padding(start = 64.dp))
                }
            }
        }
        r.credit?.takeIf { it.hasData }?.let { cr ->
            group("信誉分 ${cr.index.fmt()}（${cr.level.label}）" + (r.groupCredit?.let { " · 小组 ${it.fmt()}" } ?: ""), key = "credit") {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("越优秀越高（0–100）", fontSize = 11.sp, color = colors.onSurfaceVariantSummary)
                    if (r.creditTrend.count { it > 0 } >= 2) TrendLine(r.creditTrend.map { (it / 100).toFloat() }, r.creditTrend.indices.map { if (it == r.creditTrend.lastIndex) "本周" else "${r.creditTrend.size - 1 - it}周前" },
                        r.creditTrend.mapIndexed { i, v -> "${if (i == r.creditTrend.lastIndex) "现在" else "${r.creditTrend.size - 1 - i} 周前"} 信誉分 ${v.roundToInt()}" }, height = 110.dp)
                    cr.parts.forEach { p ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(p.label, Modifier.width(64.dp), fontSize = 13.sp)
                            Box(Modifier.weight(1f).height(8.dp).clip(CircleShape).background(colors.secondaryContainer)) {
                                Box(Modifier.fillMaxWidth(p.value.toFloat().coerceIn(0.02f, 1f)).height(8.dp).clip(CircleShape).background(creditColor(com.deepshawn.hwcounter.data.Credit.levelOf(p.value * 100))))
                            }
                            Text("${(p.value * 100).roundToInt()}%", Modifier.width(44.dp), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
                        }
                        Text(p.detail + " · 权重 ${(p.weight * 100).roundToInt()}%", fontSize = 11.sp, color = colors.onSurfaceVariantSummary, modifier = Modifier.padding(start = 64.dp))
                    }
                }
            }
        }
        // 作业概况
        group("作业概况（应交 ${r.expected} 次）", key = "hw") {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(HwStatus.SUBMITTED, HwStatus.EXCELLENT, HwStatus.LATE, HwStatus.MISSING, HwStatus.LEAVE).forEach { st ->
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(statusColor(st).copy(alpha = 0.10f)).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(animatedText("${r.counts[st] ?: 0}"), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = statusColor(st))
                            Text(st.label, fontSize = 11.sp, color = colors.onSurfaceVariantSummary)
                        }
                    }
                }
                r.stats.forEach { st ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(st.label, Modifier.width(56.dp), fontSize = 13.sp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Bar(st.mine, if (st.better) GOOD else BAD)
                            Bar(st.classAvg, colors.onSurfaceVariantSummary.copy(alpha = 0.5f))
                        }
                        Text("${st.text} / ${st.classText}", Modifier.width(86.dp), fontSize = 12.sp, textAlign = TextAlign.End, color = if (st.better) GOOD else BAD)
                    }
                }
                Text("上条为本人，下条为全班平均", fontSize = 11.sp, color = colors.onSurfaceVariantSummary)
                Row {
                    Mini("连续交齐", "${r.handStreak} 次", Modifier.weight(1f)); Mini("最长连续交齐", "${r.longestHand} 次", Modifier.weight(1f))
                    Mini("连续未交", "${r.missStreak} 次", Modifier.weight(1f)); Mini("最长连续未交", "${r.longestMiss} 次", Modifier.weight(1f))
                }
                if (r.recentRate != null) Text(
                    "最近 10 次交齐率 ${pct(r.recentRate)}" + (r.olderRate?.let { " · 之前 10 次 ${pct(it)}（${if (r.recentRate >= it) "提高" else "下降"} ${(kotlin.math.abs(r.recentRate - it) * 100).roundToInt()} 个百分点）" } ?: ""),
                    fontSize = 12.sp, color = colors.onSurfaceSecondary,
                )
            }
        }
        // 作业足迹
        if (r.timeline.isNotEmpty()) group("作业足迹（最近 ${minOf(40, r.timeline.size)} 次，新 → 旧）", key = "tl") {
            FlowRow(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                r.timeline.take(40).forEach { (a, st) ->
                    Box(Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)).background(statusColor(st).copy(alpha = if (st == HwStatus.NONE) 0.25f else 0.9f)).clickable { kit.nav.push(Route.Assign(a.id)) }, contentAlignment = Alignment.Center) {
                        Text(st.short, fontSize = 10.sp, color = Color.White)
                    }
                }
            }
        }
        // 各科、星期、按月
        if (r.subjects.size > 1) group("各科交齐率（本人 / 全班）", key = "sub") {
            Column(Modifier.padding(vertical = 6.dp)) {
                r.subjects.forEach { sr ->
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(sr.subject, Modifier.width(48.dp), fontSize = 14.sp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) { Bar(sr.rate, if (sr.rate >= sr.classRate) GOOD else BAD); Bar(sr.classRate, colors.onSurfaceVariantSummary.copy(alpha = 0.5f)) }
                        Text("${pct(sr.rate)} / ${pct(sr.classRate)}", Modifier.width(78.dp), fontSize = 12.sp, textAlign = TextAlign.End)
                    }
                    Text("${sr.count} 次 · 优秀 ${sr.excellent} · 缺交 ${sr.missing}", fontSize = 11.sp, color = colors.onSurfaceVariantSummary, modifier = Modifier.padding(start = 64.dp, bottom = 2.dp))
                }
            }
        }
        val wd = r.weekdays.mapNotNull { (d, v) -> v?.let { Triple(d, it.toFloat(), pct(it)) } }
        if (wd.size >= 2) group("星期规律（交齐率）", key = "wd") { HBars(wd, Modifier.padding(16.dp)) }
        if (r.months.size >= 2) group("按月交齐率", key = "mon") {
            TrendLine(r.months.map { it.rate.toFloat() }, r.months.map { it.label }, r.months.map { "${it.label}：${it.count} 次 · 本人 ${pct(it.rate)} · 全班 ${pct(it.classRate)}" }, Modifier.padding(16.dp), height = 120.dp)
        }
        // 积分
        if (r.scoreTrend.size >= 2) group("积分走势", key = "sc") {
            ValueLine(r.scoreTrend.map { it.score }, r.scoreTrend.map { df.format(Date(it.time)) }, r.scoreTrend.map { "${df.format(Date(it.time))} 积分 ${it.score.fmt()} · 第 ${it.rank} 名" }, Modifier.padding(16.dp), height = 130.dp)
        }
        group("积分来源（当前一轮）", key = "src") {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val max = r.sources.values.maxOf { kotlin.math.abs(it) }.coerceAtLeast(1.0)
                r.sources.forEach { (k, v) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(k, Modifier.width(40.dp), fontSize = 13.sp)
                        Box(Modifier.weight(1f).height(8.dp).clip(CircleShape).background(colors.secondaryContainer)) {
                            Box(Modifier.fillMaxWidth((kotlin.math.abs(v) / max).toFloat().coerceIn(0.01f, 1f)).height(8.dp).clip(CircleShape).background(if (v >= 0) GOOD else BAD))
                        }
                        Text(if (v == 0.0) "0" else v.signed(), Modifier.width(48.dp), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
                    }
                }
                if (r.reasons.isNotEmpty()) {
                    Text("手动加减分原因", fontSize = 12.sp, color = colors.onSurfaceVariantSummary, modifier = Modifier.padding(top = 4.dp))
                    r.reasons.forEach { (k, v) -> Row { Text(k, Modifier.weight(1f), fontSize = 13.sp); Text(v.signed(), fontSize = 13.sp, color = if (v >= 0) GOOD else BAD) } }
                }
            }
        }
        // 考试
        if (r.exams.isNotEmpty()) group("考试（${r.exams.count { it.pct != null }} 场）", key = "ex") {
            Column(Modifier.padding(vertical = 6.dp)) {
                val taken = r.exams.filter { it.pct != null }
                if (taken.size >= 2) TrendLine(taken.map { it.pct!!.toFloat() }, taken.map { df.format(Date(it.exam.date)) }, taken.map { "${it.exam.title} ${pct(it.pct!!)} · 第 ${it.rank}/${it.of} 名 · 全班 ${pct(it.classAvgPct)}" }, Modifier.padding(16.dp), height = 110.dp)
                r.exams.asReversed().forEach { e ->
                    Row(Modifier.fillMaxWidth().clickable { kit.nav.push(Route.Exam(e.exam.id)) }.padding(horizontal = 16.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${e.exam.subject} ${e.exam.title}".trim(), fontSize = 14.sp)
                            Text(df.format(Date(e.exam.date)) + " · 全班平均 ${pct(e.classAvgPct)}" + (e.z?.let { " · ${if (it >= 0) "高" else "低"}于平均 ${"%.1f".format(kotlin.math.abs(it))}σ" } ?: ""), fontSize = 11.sp, color = colors.onSurfaceVariantSummary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(if (e.absent) "缺考" else e.score?.let { "${it.fmt()}" } ?: "—", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = if (e.pct != null && e.pct < 0.6) BAD else colors.onSurface)
                        Text(e.rank?.let { "  第$it" } ?: "", fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
                    }
                }
                Text(
                    listOfNotNull(r.examAvgPct?.let { "平均得分率 ${pct(it)}" }, r.bestSubject?.let { "最好 ${it.first} ${pct(it.second)}" }, r.worstSubject?.takeIf { it != r.bestSubject }?.let { "最弱 ${it.first} ${pct(it.second)}" },
                        if (taken.size >= 3) "相对位置${if (r.examTrend >= 0) "逐场上升" else "逐场下降"}" else null).joinToString(" · "),
                    fontSize = 12.sp, color = colors.onSurfaceSecondary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
        }
        // 各轮、结算
        if (r.rounds.size > 1) group("各轮表现（以积分重置为分界）", key = "rd") {
            Column(Modifier.padding(vertical = 6.dp)) {
                r.rounds.forEach { rd ->
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(rd.label, Modifier.weight(1.4f), fontSize = 13.sp)
                        Text("${rd.count} 次", Modifier.weight(0.6f), fontSize = 12.sp, textAlign = TextAlign.End)
                        Text("交齐 ${pct(rd.rate)}", Modifier.weight(1f), fontSize = 12.sp, textAlign = TextAlign.End)
                        Text("${rd.startScore.fmt()}→${rd.endScore.fmt()}", Modifier.weight(1f), fontSize = 12.sp, textAlign = TextAlign.End, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        if (r.settlements.isNotEmpty()) group("结算记录", key = "st") {
            Column(Modifier.padding(vertical = 6.dp)) {
                r.settlements.asReversed().forEach { (s0, l) ->
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        Row { Text(s0.title, Modifier.weight(1f), fontSize = 14.sp); Text(if (l.skipped) "不结算" else l.delta.signed(), fontWeight = FontWeight.SemiBold, color = if (l.delta >= 0) GOOD else BAD) }
                        Text(l.reasons.joinToString("；"), fontSize = 11.sp, color = colors.onSurfaceVariantSummary, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        if (r.group.isNotBlank()) group("小组", key = "grp") {
            Text("${r.group}（${r.groupSize} 人）" + (r.groupRank?.let { " · 小组交齐率排第 $it" } ?: "") + (r.groupAvgRate?.let { " · 小组交齐率 ${pct(it)}，本人 ${r.stats[0].text}" } ?: ""), fontSize = 13.sp, modifier = Modifier.padding(16.dp).clickable { kit.nav.push(Route.Group(r.group)) })
        }
    }

    ActionSheet(exporting, "导出 ${s.name} 的分析报告", listOf(
        SheetAction("导出 PDF 报告", "多页排版，适合打印或发给家长") { runCatching { share(ctx, c, "${s.name}个人分析报告") { PdfReport.writePersonal(c, r, it) } }.onFailure { kit.toast("导出失败：${it.message}") } },
        SheetAction("复制文字报告") {
            (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("个人分析报告", Personal.text(c, r)))
            kit.toast("已复制")
        },
    ), onDismiss = { exporting = false })
}

@Composable
private fun Bullets(title: String, items: List<String>, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = color)
        items.forEach { t ->
            Row {
                Box(Modifier.padding(top = 6.dp).size(6.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(8.dp))
                Text(t, fontSize = 13.sp, lineHeight = 19.sp)
            }
        }
    }
}

@Composable
private fun Bar(v: Double, color: Color) {
    Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(MiuixTheme.colorScheme.secondaryContainer)) {
        Box(Modifier.fillMaxWidth(v.toFloat().coerceIn(0.01f, 1f)).height(6.dp).clip(CircleShape).background(color))
    }
}

@Composable
internal fun Mini(label: String, value: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(animatedText(value), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(label, fontSize = 10.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}

@Suppress("unused") private fun keep(a: Analytics.Level) = a
