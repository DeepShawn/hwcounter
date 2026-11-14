@file:OptIn(ExperimentalFoundationApi::class)

package com.deepshawn.hwcounter.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepshawn.hwcounter.data.Analytics
import com.deepshawn.hwcounter.data.AppData
import com.deepshawn.hwcounter.data.AppViewModel
import com.deepshawn.hwcounter.data.SchoolClass
import com.deepshawn.hwcounter.data.ScoreRules
import com.deepshawn.hwcounter.data.Student
import com.deepshawn.hwcounter.data.fmt
import com.deepshawn.hwcounter.data.leaderOf
import com.deepshawn.hwcounter.data.signed
import com.deepshawn.hwcounter.data.studentStats
import com.deepshawn.hwcounter.ui.LocalKit
import com.deepshawn.hwcounter.ui.Route
import com.deepshawn.hwcounter.ui.components.ActionSheet
import com.deepshawn.hwcounter.ui.components.Avatar
import com.deepshawn.hwcounter.ui.components.EmptyState
import com.deepshawn.hwcounter.ui.components.HCard
import com.deepshawn.hwcounter.ui.components.LocalBottomInset
import com.deepshawn.hwcounter.ui.components.MiuixPage
import com.deepshawn.hwcounter.ui.components.SheetAction
import com.deepshawn.hwcounter.ui.components.glass
import com.deepshawn.hwcounter.ui.components.listCell
import com.deepshawn.hwcounter.ui.components.pctText
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 一个小组的汇总 */
data class GroupInfo(
    val name: String,
    val members: List<Student>,
    val leader: Student?,
    val avgScore: Double,
    val rate: Float,
    val missing: Int,
)

fun SchoolClass.groupInfos(rules: ScoreRules): List<GroupInfo> {
    val scores = studentStats(rules).associate { it.student.id to it.score }
    return students.groupBy { it.group.ifBlank { "未分组" } }.map { (g, m) ->
        val ins = m.map { Analytics.insight(it, assignments) }
        val exp = ins.sumOf { it.expected }
        GroupInfo(g, m, leaderOf(g), m.map { scores[it.id] ?: 0.0 }.average(), if (exp == 0) 1f else ins.sumOf { it.handedIn }.toFloat() / exp, ins.sumOf { it.missing })
    }.sortedWith(compareBy<GroupInfo> { it.name == "未分组" }.thenBy { it.name })
}

/** 名单页“小组”选项卡：每组一张卡片，可整组加减分或进入小组 */
fun LazyListScope.groupCards(vm: AppViewModel, data: AppData, c: SchoolClass, readOnly: Boolean, onScore: (List<String>, String) -> Unit) {
    val groups = c.groupInfos(data.settings.rules)
    if (groups.isEmpty()) {
        item { EmptyState("👥", "还没有小组", "在学生资料里填写“小组”，这里就会按组汇总；也可以导入带小组列的名单") }
        return
    }
    item(key = "g_hint") {
        Text(
            "点小组查看成员并给部分成员加减分 · 长按设置组长",
            fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = Modifier.padding(start = 28.dp, end = 28.dp, bottom = 8.dp),
        )
    }
    groups.forEach { g ->
        item(key = "g_" + g.name) { GroupCard(g, readOnly, onScore) }
    }
}

@Composable
private fun GroupCard(g: GroupInfo, readOnly: Boolean, onScore: (List<String>, String) -> Unit) {
    val kit = LocalKit.current
    val colors = MiuixTheme.colorScheme
    HCard(Modifier.padding(horizontal = 12.dp, vertical = 6.dp).fillMaxWidth(), onClick = { kit.nav.push(Route.Group(g.name)) }) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(g.name, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (g.leader != null) {
                            Icon(Icons.Filled.Star, null, Modifier.size(14.dp), tint = Color(0xFFFFB300))
                            Spacer(Modifier.width(3.dp))
                            Text("组长 ${g.leader.name}", fontSize = 13.sp, color = colors.onSurfaceSecondary)
                        } else Text("未设组长", fontSize = 13.sp, color = colors.onSurfaceVariantSummary)
                        Text(" · ${g.members.size} 人", fontSize = 13.sp, color = colors.onSurfaceVariantSummary)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(g.avgScore.fmt(), fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = colors.primary)
                    Text("平均分", fontSize = 11.sp, color = colors.onSurfaceVariantSummary)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                    g.members.take(7).forEach { s ->
                        Box(Modifier.size(32.dp).clip(CircleShape).background(colors.surfaceContainer).padding(1.5.dp)) { Avatar(s.name, size = 29.dp) }
                    }
                    if (g.members.size > 7) Box(Modifier.size(32.dp).clip(CircleShape).background(colors.secondaryContainer), contentAlignment = Alignment.Center) {
                        Text("+${g.members.size - 7}", fontSize = 11.sp, color = colors.onSurfaceSecondary)
                    }
                }
                Text("交齐 ${pctText(g.rate)} · 缺交 ${g.missing}", fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
            }
            if (!readOnly) {
                Spacer(Modifier.height(12.dp))
                TextButton("整组加减分", onClick = { onScore(g.members.map { it.id }, g.name) }, modifier = Modifier.fillMaxWidth(), minHeight = 38.dp, insideMargin = PaddingValues(vertical = 8.dp))
            }
        }
    }
}

/** 小组详情：成员列表，可多选部分成员加减分，可设置组长 */
@Composable
fun GroupDetailScreen(vm: AppViewModel, data: AppData, name: String) {
    val kit = LocalKit.current
    val colors = MiuixTheme.colorScheme
    val c = vm.currentClass(data) ?: return
    val readOnly by vm.readOnly.collectAsState()
    val info = remember(c, data.settings.rules, name) { c.groupInfos(data.settings.rules).firstOrNull { it.name == name } }
    if (info == null) { androidx.compose.runtime.LaunchedEffect(Unit) { kit.nav.pop() }; return }
    val scores = remember(c, data.settings.rules) { c.studentStats(data.settings.rules).associate { it.student.id to it } }
    val insights = remember(c) { info.members.associate { it.id to Analytics.insight(it, c.assignments) } }
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var scoreFor by remember { mutableStateOf<List<String>?>(null) }
    var menuFor by remember { mutableStateOf<Student?>(null) }
    val members = info.members.sortedWith(compareByDescending<Student> { it.id == info.leader?.id }.thenComparator { a, b -> com.deepshawn.hwcounter.data.compareStudentNumbers(a.no, b.no) })

    MiuixPage(
        title = name,
        onBack = { kit.nav.pop() },
        extraBottom = if (selecting) 90.dp else 0.dp,
        actions = {
            if (!readOnly) TextButton(
                if (selecting) "完成" else "多选", onClick = { selecting = !selecting; selected = emptySet() },
                minHeight = 34.dp, insideMargin = PaddingValues(horizontal = 14.dp, vertical = 6.dp), modifier = Modifier.padding(end = 16.dp),
                colors = if (selecting) ButtonDefaults.textButtonColorsPrimary() else ButtonDefaults.textButtonColors(),
            )
        },
        floating = {
            if (selecting && !readOnly) Row(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp + LocalBottomInset.current)
                    .widthIn(max = 560.dp).fillMaxWidth().glass(RoundedCornerShape(24.dp)).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("已选 ${selected.size} 人", Modifier.weight(1f).padding(start = 8.dp), fontWeight = FontWeight.SemiBold)
                TextButton("全选", onClick = { selected = members.map { it.id }.toSet() }, minHeight = 36.dp, insideMargin = PaddingValues(horizontal = 12.dp, vertical = 6.dp))
                Spacer(Modifier.width(8.dp))
                TextButton("加减分", onClick = { scoreFor = selected.toList() }, enabled = selected.isNotEmpty(), colors = ButtonDefaults.textButtonColorsPrimary(), minHeight = 36.dp, insideMargin = PaddingValues(horizontal = 14.dp, vertical = 6.dp))
            }
        },
    ) {
        item(key = "head") {
            HCard(Modifier.padding(horizontal = 12.dp).fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(colors.primary.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                        Text(name.take(2), fontWeight = FontWeight.Bold, color = colors.primary)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(info.leader?.let { "组长：${it.name}" } ?: "还没有组长", fontWeight = FontWeight.Medium)
                        Text("${info.members.size} 人 · 交齐 ${pctText(info.rate)} · 缺交 ${info.missing} 次", fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(info.avgScore.fmt(), fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = colors.primary)
                        Text("平均分", fontSize = 11.sp, color = colors.onSurfaceVariantSummary)
                    }
                }
                if (!readOnly) Row(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton("整组加减分", onClick = { scoreFor = members.map { it.id } }, modifier = Modifier.weight(1f), colors = ButtonDefaults.textButtonColorsPrimary(), minHeight = 38.dp, insideMargin = PaddingValues(vertical = 8.dp))
                    TextButton("部分成员加减分", onClick = { selecting = true; selected = emptySet() }, modifier = Modifier.weight(1f), minHeight = 38.dp, insideMargin = PaddingValues(vertical = 8.dp))
                }
            }
        }
        item(key = "mtitle") { top.yukonga.miuix.kmp.basic.SmallTitle(if (selecting) "点成员选择或取消" else "成员 · 点成员加减分，长按设组长") }
        itemsIndexed(members, key = { _, s -> s.id }) { i, s ->
            val sel = s.id in selected
            val st = scores[s.id]
            val ins = insights[s.id]
            Row(
                Modifier.listCell(i, members.size)
                    .background(if (sel) colors.primary.copy(alpha = 0.10f) else Color.Transparent)
                    .combinedClickable(
                        onClick = {
                            when {
                                selecting -> selected = if (sel) selected - s.id else selected + s.id
                                readOnly -> kit.nav.push(Route.StudentDetail(s.id))
                                else -> scoreFor = listOf(s.id)
                            }
                        },
                        onLongClick = { if (!readOnly) menuFor = s else kit.nav.push(Route.StudentDetail(s.id)) },
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (selecting) {
                    Box(Modifier.size(22.dp).clip(CircleShape).background(if (sel) colors.primary else colors.secondaryContainer), contentAlignment = Alignment.Center) {
                        if (sel) Icon(Icons.Filled.Check, null, Modifier.size(14.dp), tint = Color.White)
                    }
                    Spacer(Modifier.width(10.dp))
                }
                Avatar(s.name, size = 38.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(s.name, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                        if (s.id == info.leader?.id) {
                            Spacer(Modifier.width(6.dp))
                            Text("组长", fontSize = 10.sp, color = Color(0xFFB27800), modifier = Modifier.clip(CircleShape).background(Color(0xFFFFB300).copy(alpha = 0.18f)).padding(horizontal = 6.dp, vertical = 1.dp))
                        }
                    }
                    Text(
                        listOf(s.no, ins?.let { "交齐 ${pctText(it.rate)}" }, ins?.takeIf { it.missing > 0 }?.let { "缺交 ${it.missing}" }).filterNotNull().filter { it.isNotBlank() }.joinToString(" · "),
                        fontSize = 12.sp, color = colors.onSurfaceVariantSummary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(st?.score?.fmt() ?: "", fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = colors.primary)
            }
        }
    }

    scoreFor?.let { ids ->
        val targets = c.students.filter { it.id in ids }
        if (targets.isNotEmpty()) ScoreDialog(targets, data.settings.quickReasons, { scoreFor = null }, initialReason = if (targets.size == info.members.size) "${name}表现" else "") { d, r ->
            vm.addScore(targets.map { it.id }, d, r)
            kit.haptic(true)
            kit.undoable(if (targets.size == info.members.size) "$name 全组 ${d.signed()}" else if (targets.size == 1) "${targets[0].name} ${d.signed()}" else "${targets.size} 人 ${d.signed()}")
            scoreFor = null; selecting = false; selected = emptySet()
        }
    }
    menuFor?.let { s ->
        val isLeader = s.id == info.leader?.id
        ActionSheet(
            show = true, title = s.name, onDismiss = { menuFor = null },
            actions = listOf(
                if (isLeader) SheetAction("取消组长") { vm.setGroupLeader(name, null); kit.toast("已取消组长") }
                else SheetAction("设为组长", "${name}的组长") { vm.setGroupLeader(name, s.id); kit.toast("${s.name} 是${name}的组长") },
                SheetAction("查看个人详情") { kit.nav.push(Route.StudentDetail(s.id)) },
            ),
        )
    }
}
