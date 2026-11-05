@file:OptIn(ExperimentalMaterial3Api::class)

package com.deepshawn.hwcounter.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepshawn.hwcounter.data.activeScoreReset
import com.deepshawn.hwcounter.data.scoreBoundaryIndex
import com.deepshawn.hwcounter.data.AppData
import com.deepshawn.hwcounter.data.AppViewModel
import com.deepshawn.hwcounter.data.HwStatus
import com.deepshawn.hwcounter.data.studentStats
import com.deepshawn.hwcounter.data.summary
import com.deepshawn.hwcounter.ui.LocalKit
import com.deepshawn.hwcounter.ui.Route
import com.deepshawn.hwcounter.ui.components.AppCard
import com.deepshawn.hwcounter.ui.components.BarChart
import com.deepshawn.hwcounter.ui.components.Donut
import com.deepshawn.hwcounter.ui.components.EmptyState
import com.deepshawn.hwcounter.ui.components.GradientHeader
import com.deepshawn.hwcounter.ui.components.SectionTitle
import com.deepshawn.hwcounter.ui.components.StatTile
import com.deepshawn.hwcounter.ui.components.statusColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StatsScreen(vm: AppViewModel, data: AppData) {
    val kit = LocalKit.current
    val c = vm.currentClass(data) ?: run { EmptyState("📊", "还没有班级", ""); return }
    val rules = data.settings.rules
    val assigns = remember(c) { c.assignments.sortedBy { it.createdAt } }
    val recent = assigns.takeLast(10)
    val boundary = c.scoreBoundaryIndex(recent)
    val stats = remember(c, rules) { c.studentStats(rules) }
    val dateFmt = remember { SimpleDateFormat("M/d", Locale.CHINA) }
    val summaries = remember(c.students, c.assignments) { assigns.associate { it.id to c.summary(it) } }
    val totals = remember(summaries) {
        HashMap<HwStatus, Int>().apply {
            summaries.values.forEach { summary -> summary.counts.forEach { (status, n) -> this[status] = (this[status] ?: 0) + n } }
        }
    }
    val handed = (totals[HwStatus.SUBMITTED] ?: 0) + (totals[HwStatus.EXCELLENT] ?: 0) + (totals[HwStatus.LATE] ?: 0)
    val expected = summaries.values.sumOf { it.expected }
    val overall = if (expected == 0) 0f else handed.toFloat() / expected

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { GradientHeader {
            Text("数据统计", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text("${c.name} · 共 ${assigns.size} 次作业 · ${c.students.size} 人", color = Color.White.copy(alpha = 0.85f))
            if (assigns.isNotEmpty()) {
                val ctx = androidx.compose.ui.platform.LocalContext.current
                androidx.compose.material3.TextButton(onClick = {
                    val f = java.io.File(com.deepshawn.hwcounter.export.Exporter.exportDir(ctx), "${c.name}_各次作业统计名单_${com.deepshawn.hwcounter.export.Exporter.fileStamp()}.xlsx".replace("/", "_"))
                    f.outputStream().use { com.deepshawn.hwcounter.export.Exporter.writeClassStatList(c, it) }
                    com.deepshawn.hwcounter.export.Exporter.shareFile(ctx, f, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "分享统计名单")
                }) { Text("📋 导出各次统计名单 Excel", color = Color.White, fontWeight = FontWeight.Bold) }
            }
        } }
        if (assigns.isEmpty()) {
            item { EmptyState("📈", "还没有数据", "布置并登记几次作业后，这里会出现趋势图、热力图和催交榜") }
            return@LazyColumn
        }
        item { Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("总提交率", "${(overall * 100).toInt()}%", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
            StatTile("优秀次数", "${totals[HwStatus.EXCELLENT] ?: 0}", statusColor(HwStatus.EXCELLENT), Modifier.weight(1f))
            StatTile("缺交人次", "${totals[HwStatus.MISSING] ?: 0}", statusColor(HwStatus.MISSING), Modifier.weight(1f))
        } }

        item { AppCard(Modifier.padding(horizontal = 16.dp)) {
            SectionTitle("📈 最近 ${recent.size} 次提交率")
            BarChart(recent.map { summaries.getValue(it.id).rate }, recent.map { dateFmt.format(Date(it.createdAt)) }, Modifier.padding(top = 8.dp))
        }
        Spacer(Modifier.height(12.dp)) }
        item { AppCard(Modifier.padding(horizontal = 16.dp)) {
            SectionTitle("🍩 状态分布")
            Row(verticalAlignment = Alignment.CenterVertically) {
                val order = listOf(HwStatus.SUBMITTED, HwStatus.EXCELLENT, HwStatus.LATE, HwStatus.LEAVE, HwStatus.MISSING, HwStatus.NONE)
                Donut(order.map { statusColor(it) to (totals[it] ?: 0).toFloat() }, Modifier.size(150.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${(overall * 100).toInt()}%", fontSize = 26.sp, fontWeight = FontWeight.Black)
                        Text("提交率", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.width(20.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    order.forEach { s ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(statusColor(s)))
                            Spacer(Modifier.width(6.dp))
                            Text("${s.label}  ${totals[s] ?: 0}", fontSize = 13.sp)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp)) }
        // 科目
        val bySubject = assigns.groupBy { it.subject.ifBlank { "其他" } }.map { (k, l) -> k to l.map { summaries.getValue(it.id).rate }.average().toFloat() }.sortedByDescending { it.second }
        if (bySubject.size > 1) {
            item { AppCard(Modifier.padding(horizontal = 16.dp)) {
                SectionTitle("📚 各科平均提交率")
                bySubject.forEach { (sub, r) ->
                    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(sub, Modifier.width(48.dp), fontWeight = FontWeight.Bold, color = subjectColor(sub))
                        Box(Modifier.weight(1f).height(12.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
                            Box(Modifier.fillMaxWidth(r.coerceIn(0.02f, 1f)).height(12.dp).clip(CircleShape).background(subjectColor(sub)))
                        }
                        Text("${(r * 100).toInt()}%", Modifier.width(48.dp).padding(start = 8.dp), fontSize = 13.sp)
                    }
                }
            }
            Spacer(Modifier.height(12.dp)) }
        }
        // 热力图
        item { AppCard(Modifier.padding(horizontal = 16.dp)) {
            SectionTitle("🔥 作业热力图（最近 ${recent.size} 次）")
            ScorePeriodBanner(c)
            if (c.activeScoreReset != null && boundary < 0) Text("积分重置起点在当前显示范围之外，作业历史仍完整保留", fontSize = 12.sp, color = Color(0xFFAD6800))
            Text("每行一位同学，每格一次作业，点击格子进入该次作业", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Column(Modifier.horizontalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Spacer(Modifier.width(56.dp))
                    recent.forEachIndexed { index, a ->
                        if (index == boundary) Box(Modifier.width(3.dp).height(32.dp).background(Color(0xFFF59F00)))
                        Column(Modifier.width(36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            if (index == boundary) Text("积分重置", fontSize = 8.sp, color = Color(0xFFAD6800))
                            Text(dateFmt.format(Date(a.createdAt)), fontSize = 9.sp)
                        }
                    }
                }
                c.students.forEach { s ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 1.5.dp)) {
                        Text(s.name, Modifier.width(56.dp), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        recent.forEachIndexed { index, a ->
                            if (index == boundary) Box(Modifier.width(3.dp).height(21.dp).background(Color(0xFFF59F00)))
                            val st = a.statuses[s.id] ?: HwStatus.NONE
                            Box(
                                Modifier.width(36.dp).padding(horizontal = 9.dp, vertical = 1.5.dp).size(18.dp).clip(MaterialTheme.shapes.extraSmall)
                                    .background(statusColor(st).copy(alpha = if (st == HwStatus.NONE) 0.25f else 0.9f))
                                    .clickable { kit.nav.push(Route.Assign(a.id)) },
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp)) }
        // 进步之星：最近 3 次全交且之前有缺交
        val stars = stats.filter { it.missing == 0 && it.late == 0 }.map { it.student.name }
        item { AppCard(Modifier.padding(horizontal = 16.dp)) {
            SectionTitle("🌟 全勤之星 ${stars.size} 人")
            Text(if (stars.isEmpty()) "暂时还没有，继续加油！" else stars.joinToString("、"), fontSize = 14.sp)
        }
        Spacer(Modifier.height(24.dp)) }
    }
}
