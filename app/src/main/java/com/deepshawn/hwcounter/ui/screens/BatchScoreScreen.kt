@file:OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)

package com.deepshawn.hwcounter.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepshawn.hwcounter.data.Analytics
import com.deepshawn.hwcounter.data.AppData
import com.deepshawn.hwcounter.data.AppViewModel
import com.deepshawn.hwcounter.data.HwStatus
import com.deepshawn.hwcounter.data.compareStudentNumbers
import com.deepshawn.hwcounter.data.fmt
import com.deepshawn.hwcounter.data.signed
import com.deepshawn.hwcounter.data.studentStats
import com.deepshawn.hwcounter.ui.LocalKit
import com.deepshawn.hwcounter.ui.components.Avatar
import com.deepshawn.hwcounter.ui.components.HCard
import com.deepshawn.hwcounter.ui.components.HTabs
import com.deepshawn.hwcounter.ui.components.LocalBottomInset
import com.deepshawn.hwcounter.ui.components.MiuixPage
import com.deepshawn.hwcounter.ui.components.glass
import com.deepshawn.hwcounter.ui.components.listCell
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 快速选择的一组人 */
private data class Preset(val label: String, val ids: List<String>, val hint: String = "")

/**
 * 批量加减分：按条件快速选人（全班、小组、最近一次作业、考试分数段、进步之星、退步关注、积分最低……），
 * 可以统一分值，也可以逐人设不同分值；确认前显示每人加减后的积分。
 */
@Composable
fun BatchScoreScreen(vm: AppViewModel, data: AppData, presetIds: List<String>, presetReason: String) {
    val kit = LocalKit.current
    val colors = MiuixTheme.colorScheme
    val c = vm.currentClass(data) ?: return
    val rules = data.settings.rules
    val stats = remember(c, rules) { c.studentStats(rules).associateBy { it.student.id } }
    val students = remember(c.students) { c.students.sortedWith { a, b -> compareStudentNumbers(a.no, b.no) } }
    var selected by rememberSaveable { mutableStateOf(presetIds.filter { id -> c.students.any { it.id == id } }) }
    var perPerson by rememberSaveable { mutableIntStateOf(0) }
    var value by rememberSaveable { mutableDoubleStateOf(if (presetReason.contains("退步")) -1.0 else if (presetReason.isBlank()) 1.0 else 2.0) }
    var custom by rememberSaveable { mutableStateOf(mapOf<String, Double>()) }
    var reason by rememberSaveable { mutableStateOf(presetReason) }
    val sel = selected.toSet()
    fun deltaOf(id: String) = if (perPerson == 1) custom[id] ?: value else value
    val deltas = selected.associateWith { deltaOf(it) }.filterValues { it != 0.0 && it.isFinite() }
    val total = deltas.values.sum()

    val presets = remember(c, rules) {
        val out = ArrayList<Preset>()
        out += Preset("全班", c.students.map { it.id })
        c.students.map { it.group }.filter { it.isNotBlank() }.distinct().sorted().forEach { g -> out += Preset(g, c.students.filter { it.group == g }.map { it.id }) }
        c.assignments.maxByOrNull { it.createdAt }?.let { a ->
            fun of(vararg st: HwStatus) = c.students.filter { (a.statuses[it.id] ?: HwStatus.NONE) in st }.map { it.id }
            out += Preset("「${a.title}」优秀", of(HwStatus.EXCELLENT))
            out += Preset("「${a.title}」按时交", of(HwStatus.SUBMITTED, HwStatus.EXCELLENT))
            out += Preset("「${a.title}」未交", of(HwStatus.MISSING))
            out += Preset("「${a.title}」迟交", of(HwStatus.LATE))
        }
        Analytics.progress(c, rules)?.let { pg ->
            out += Preset("进步之星", pg.stars.map { it.student.id })
            out += Preset("退步关注", pg.watch.map { it.student.id })
        }
        val all = c.assignments.sortedBy { it.createdAt }
        val ins = c.students.map { Analytics.insight(it, all) }
        out += Preset("全勤（无缺交迟交）", ins.filter { it.expected > 0 && it.missing == 0 && it.late == 0 }.map { it.student.id })
        out += Preset("连续未交 ≥ 2 次", ins.filter { it.missingStreak >= 2 }.map { it.student.id })
        c.exams.maxByOrNull { it.date }?.let { e ->
            out += Preset("「${e.title}」≥ 90%", c.students.filter { (e.scores[it.id] ?: -1.0) >= e.fullScore * 0.9 }.map { it.id })
            out += Preset("「${e.title}」< 60%", c.students.filter { e.scores[it.id]?.let { v -> v < e.fullScore * 0.6 } == true }.map { it.id })
            out += Preset("「${e.title}」缺考", c.students.filter { it.id in e.absent }.map { it.id })
        }
        val byScore = c.studentStats(rules).sortedBy { it.score }
        if (byScore.size >= 6) {
            out += Preset("积分最高 5 人", byScore.takeLast(5).map { it.student.id })
            out += Preset("积分最低 5 人", byScore.take(5).map { it.student.id })
        }
        out.filter { it.ids.isNotEmpty() }
    }

    MiuixPage(
        title = "批量加减分",
        onBack = { kit.nav.pop() },
        extraBottom = 96.dp,
        floating = {
            Row(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp + LocalBottomInset.current)
                    .widthIn(max = 560.dp).fillMaxWidth().glass(RoundedCornerShape(24.dp)).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text("已选 ${selected.size} 人", fontWeight = FontWeight.SemiBold)
                    Text(if (deltas.isEmpty()) "选好同学和分值" else "合计 ${total.signed()} 分", fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
                }
                TextButton("清空", onClick = { selected = emptyList() }, minHeight = 38.dp, insideMargin = PaddingValues(horizontal = 12.dp, vertical = 6.dp))
                Spacer(Modifier.width(8.dp))
                TextButton(
                    "确认加减分", enabled = deltas.isNotEmpty(), colors = ButtonDefaults.textButtonColorsPrimary(),
                    minHeight = 38.dp, insideMargin = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    onClick = {
                        val r = reason.trim().ifBlank { if (total >= 0) "批量加分" else "批量扣分" }
                        vm.addScores(deltas, r)
                        kit.haptic(true)
                        if (total > 0) kit.celebrate()
                        val vals = deltas.values.distinct()
                        kit.undoable("已给 ${deltas.size} 人" + if (vals.size == 1) " ${vals[0].signed()}" else "加减分（合计 ${total.signed()}）")
                        kit.nav.pop()
                    },
                )
            }
        },
    ) {
        item(key = "value") {
            Column {
                SmallTitle("分值与原因")
                HCard(Modifier.padding(horizontal = 12.dp).fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        HTabs(listOf("统一分值", "逐人分值"), perPerson, { perPerson = it }, height = 36.dp)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                            TextButton("−", onClick = { value -= 0.5 }, minWidth = 52.dp, minHeight = 44.dp)
                            Text(
                                value.signed().let { if (value == 0.0) "0" else it }, fontSize = 38.sp, fontWeight = FontWeight.Bold,
                                color = if (value >= 0) Color(0xFF169B68) else Color(0xFFE5384D), modifier = Modifier.padding(horizontal = 22.dp),
                            )
                            TextButton("+", onClick = { value += 0.5 }, minWidth = 52.dp, minHeight = 44.dp)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(-5.0, -2.0, -1.0, 1.0, 2.0, 5.0).forEach { v -> PickChip(v.signed(), value == v, Modifier.weight(1f)) { value = v } }
                        }
                        if (perPerson == 1) Text("逐人分值：下面每位同学可单独调整，没调的用上面的分值。", fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
                        TextField(reason, { reason = it }, label = "原因（如：进步之星、课堂表现好）", singleLine = true, modifier = Modifier.fillMaxWidth())
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            (listOf("进步之星", "退步提醒", "全勤奖励", "考试优秀") + data.settings.quickReasons).distinct().forEach { r -> PickChip(r, reason == r) { reason = r } }
                        }
                    }
                }
            }
        }
        item(key = "presets") {
            Column {
                SmallTitle("快速选择（点一下加入，再点一下移出）")
                HCard(Modifier.padding(horizontal = 12.dp).fillMaxWidth()) {
                    FlowRow(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        presets.forEach { p ->
                            val on = p.ids.all { it in sel }
                            PickChip("${p.label} ${p.ids.size}", on) { selected = if (on) selected.filterNot { it in p.ids } else (selected + p.ids).distinct() }
                        }
                    }
                }
            }
        }
        item(key = "listTitle") { SmallTitle("学生 · 点选择，${if (perPerson == 1) "右侧调整各自分值" else "右侧为加减后的积分"}") }
        itemsIndexed(students, key = { _, s -> s.id }) { i, s ->
            val on = s.id in sel
            val now = stats[s.id]?.score ?: rules.initial
            Row(
                Modifier.listCell(i, students.size)
                    .background(if (on) colors.primary.copy(alpha = 0.10f) else Color.Transparent)
                    .combinedClickable(onClick = { selected = if (on) selected - s.id else selected + s.id })
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(22.dp).clip(CircleShape).background(if (on) colors.primary else colors.secondaryContainer), contentAlignment = Alignment.Center) {
                    if (on) Icon(Icons.Filled.Check, null, Modifier.size(14.dp), tint = Color.White)
                }
                Spacer(Modifier.width(10.dp))
                Avatar(s.name, size = 34.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.name, fontWeight = FontWeight.Medium, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${s.no}${if (s.group.isNotBlank()) " · ${s.group}" else ""} · 现在 ${now.fmt()} 分", fontSize = 12.sp, color = colors.onSurfaceVariantSummary, maxLines = 1)
                }
                if (on && perPerson == 1) {
                    val d = custom[s.id] ?: value
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StepButton("−") { custom = custom + (s.id to d - 0.5) }
                        Text(if (d == 0.0) "0" else d.signed(), Modifier.width(44.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold, color = if (d >= 0) Color(0xFF169B68) else Color(0xFFE5384D))
                        StepButton("+") { custom = custom + (s.id to d + 0.5) }
                    }
                } else if (on) {
                    Text("${now.fmt()} → ${(now + value).fmt()}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.primary)
                }
            }
        }
    }
}

@Composable
private fun PickChip(text: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    Text(
        text, fontSize = 13.sp, maxLines = 1, textAlign = TextAlign.Center,
        color = if (on) Color.White else colors.onSurface, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
        modifier = modifier.clip(CircleShape).background(if (on) colors.primary else colors.secondaryContainer).clickable(onClick = onClick).padding(horizontal = 11.dp, vertical = 6.dp),
    )
}

@Composable
private fun StepButton(t: String, onClick: () -> Unit) {
    Box(
        Modifier.size(30.dp).clip(CircleShape).background(MiuixTheme.colorScheme.secondaryContainer).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(t, fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
}

