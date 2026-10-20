@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.deepshawn.hwcounter.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepshawn.hwcounter.data.AppData
import com.deepshawn.hwcounter.data.AppViewModel
import com.deepshawn.hwcounter.data.HwStatus
import com.deepshawn.hwcounter.data.StudentStat
import com.deepshawn.hwcounter.data.fmt
import com.deepshawn.hwcounter.data.signed
import com.deepshawn.hwcounter.data.studentStats
import com.deepshawn.hwcounter.ui.LocalKit
import com.deepshawn.hwcounter.ui.Route
import com.deepshawn.hwcounter.ui.components.Avatar
import com.deepshawn.hwcounter.ui.components.EmptyState
import com.deepshawn.hwcounter.ui.components.GradientHeader
import com.deepshawn.hwcounter.ui.components.statusColor

@Composable
fun ScoresScreen(vm: AppViewModel, data: AppData) {
    val kit = LocalKit.current
    val c = vm.currentClass(data) ?: run { EmptyState("🏆", "还没有班级", ""); return }
    val rules = data.settings.rules
    var mode by remember { mutableIntStateOf(0) }
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var scoreFor by remember { mutableStateOf<List<String>?>(null) }
    var menu by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    val stats = remember(c, rules) { c.studentStats(rules).sortedByDescending { it.score } }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 100.dp)) {
            item {
                GradientHeader {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("积分榜", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                            Text("初始 ${rules.initial.fmt()} 分 · 未交 ${rules.missing.signed()} · 迟交 ${rules.late.signed()} · 优秀 ${rules.excellent.signed()}", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                        }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, null, tint = Color.White) }
                            DropdownMenu(menu, { menu = false }) {
                                DropdownMenuItem(text = { Text("全班 +1") }, onClick = { menu = false; vm.addScore(c.students.map { it.id }, 1.0, "全班表现好"); kit.celebrate(); kit.undoable("全班 +1 🎉") })
                                DropdownMenuItem(text = { Text("清空手动积分") }, onClick = { menu = false; confirmReset = true })
                                DropdownMenuItem(text = { Text("修改计分规则") }, onClick = { menu = false; kit.nav.tab(Route.Settings) })
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    if (stats.size >= 3 && mode == 0) Podium(stats.take(3)) { kit.nav.push(Route.StudentDetail(it)) }
                }
            }
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(16.dp)) {
                    listOf("个人榜", "小组榜", "催交榜").forEachIndexed { i, t ->
                        SegmentedButton(selected = mode == i, onClick = { mode = i }, shape = SegmentedButtonDefaults.itemShape(i, 3)) { Text(t) }
                    }
                }
            }
            when (mode) {
                0 -> itemsIndexed(stats, key = { _, s -> s.student.id }) { i, st ->
                    RankRow(i + 1, st, selecting, st.student.id in selected, valueText = st.score.fmt(), sub = "交${st.submitted} · 迟${st.late} · 缺${st.missing}",
                        onClick = {
                            if (selecting) selected = if (st.student.id in selected) selected - st.student.id else selected + st.student.id
                            else scoreFor = listOf(st.student.id)
                        },
                        onLong = { kit.nav.push(Route.StudentDetail(st.student.id)) },
                    )
                }
                1 -> {
                    val groups = stats.groupBy { it.student.group.ifBlank { "未分组" } }
                        .map { (g, l) -> Triple(g, l, l.sumOf { it.score } / l.size) }
                        .sortedByDescending { it.third }
                    itemsIndexed(groups, key = { _, g -> "g" + g.first }) { i, (g, l, avg) ->
                        GroupRow(i + 1, g, l, avg) { scoreFor = l.map { it.student.id } }
                    }
                }
                else -> {
                    val list = stats.filter { it.missing + it.late > 0 }.sortedWith(compareByDescending<StudentStat> { it.missing }.thenByDescending { it.late })
                    if (list.isEmpty()) item { EmptyState("🎉", "没有欠交记录", "全班都很自觉！") }
                    itemsIndexed(list, key = { _, s -> "m" + s.student.id }) { i, st ->
                        RankRow(i + 1, st, false, false, valueText = "${st.missing}次", sub = "迟交 ${st.late} 次 · 积分 ${st.score.fmt()}", warn = true,
                            onClick = { kit.nav.push(Route.StudentDetail(st.student.id)) }, onLong = {})
                    }
                }
            }
        }
        Row(Modifier.align(Alignment.BottomEnd).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (selecting) {
                ExtendedFloatingActionButton(
                    onClick = { if (selected.isEmpty()) { selecting = false } else scoreFor = selected.toList() },
                    icon = { Icon(Icons.Filled.Check, null) },
                    text = { Text(if (selected.isEmpty()) "取消" else "给 ${selected.size} 人打分") },
                )
            } else if (mode == 0) {
                ExtendedFloatingActionButton(onClick = { selecting = true; selected = emptySet() }, icon = { Text("✨") }, text = { Text("批量加减分") })
            }
        }
    }

    scoreFor?.let { ids ->
        val targets = c.students.filter { it.id in ids }
        ScoreDialog(targets, data.settings.quickReasons, { scoreFor = null }) { d, r ->
            vm.addScore(ids, d, r)
            kit.haptic(true)
            if (d > 0) kit.beep()
            kit.undoable(if (ids.size == 1) "${targets.firstOrNull()?.name} ${d.signed()}" else "${ids.size} 人 ${d.signed()}")
            selecting = false; selected = emptySet()
        }
    }
    if (confirmReset) ConfirmDialog("清空手动积分", "将删除所有手动加减分记录（作业自动计分不受影响）。", "清空", { confirmReset = false }) {
        vm.clearScores(); kit.undoable("已清空手动积分")
    }
}

@Composable
private fun Podium(top: List<StudentStat>, onClick: (String) -> Unit) {
    val order = listOf(1, 0, 2)
    val heights = listOf(110, 140, 90)
    val medals = listOf("🥇", "🥈", "🥉")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
        order.forEachIndexed { pos, idx ->
            val st = top[idx]
            val anim = remember { Animatable(0f) }
            LaunchedEffect(st.student.id) { anim.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow)) }
            Column(Modifier.weight(1f).combinedClickable(onClick = { onClick(st.student.id) }), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(medals[idx], fontSize = 26.sp)
                Avatar(st.student.name, size = if (idx == 0) 60.dp else 50.dp, color = Color.White.copy(alpha = 0.3f))
                Text(st.student.name, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier.fillMaxWidth().padding(horizontal = 6.dp).height((heights[pos] * anim.value).dp)
                        .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                        .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0.12f)))),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Text(st.score.fmt(), color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun RankRow(
    rank: Int, st: StudentStat, selecting: Boolean, sel: Boolean, valueText: String, sub: String,
    warn: Boolean = false, onClick: () -> Unit, onLong: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp).clip(MaterialTheme.shapes.medium)
            .background(if (sel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest)
            .combinedClickable(onClick = onClick, onLongClick = onLong)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            if (selecting) {
                Box(
                    Modifier.size(22.dp).clip(CircleShape).background(if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) { if (sel) Icon(Icons.Filled.Check, null, Modifier.size(14.dp), tint = Color.White) }
            } else Text("$rank", fontWeight = FontWeight.Black, color = if (rank <= 3) Color(0xFFFFA000) else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        Avatar(st.student.name, size = 38.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(st.student.name, fontWeight = FontWeight.Bold)
            Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(valueText, fontWeight = FontWeight.Black, fontSize = 20.sp, color = if (warn) statusColor(HwStatus.MISSING) else MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun GroupRow(rank: Int, name: String, list: List<StudentStat>, avg: Double, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .combinedClickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (rank <= 3) listOf("🥇", "🥈", "🥉")[rank - 1] else "$rank", fontSize = 22.sp, modifier = Modifier.width(36.dp))
            Column(Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text("${list.size} 人 · 缺交 ${list.sumOf { it.missing }} 次 · 点击给全组加减分", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(avg.fmt(), fontWeight = FontWeight.Black, fontSize = 22.sp, color = MaterialTheme.colorScheme.primary)
                Text("平均分", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            list.take(10).forEach { Avatar(it.student.name, size = 28.dp) }
        }
    }
}
