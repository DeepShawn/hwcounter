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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.input.KeyboardType
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
    val readOnly by vm.readOnly.collectAsState()
    var mode by remember { mutableIntStateOf(0) }
    var selecting by remember(c.id, readOnly) { mutableStateOf(false) }
    var selected by remember(c.id, readOnly) { mutableStateOf(setOf<String>()) }
    var scoreFor by remember(c.id, readOnly) { mutableStateOf<List<String>?>(null) }
    var menu by remember { mutableStateOf(false) }
    var resetFor by remember(c.id, readOnly) { mutableStateOf<List<String>?>(null) }
    var editBase by remember(c.id, readOnly) { mutableStateOf(false) }
    val stats = remember(c, rules) { c.studentStats(rules).sortedByDescending { it.score } }
    val selectedIds = c.students.filter { it.id in selected }.map { it.id }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = if (selecting) 240.dp else 100.dp)) {
            item {
                GradientHeader {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("积分榜", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                            Text("基准 ${rules.initial.fmt()} 分 · 未交 ${rules.missing.signed()} · 迟交 ${rules.late.signed()} · 优秀 ${rules.excellent.signed()}", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                        }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, null, tint = Color.White) }
                            DropdownMenu(menu, { menu = false }) {
                                if (!readOnly) {
                                    DropdownMenuItem(text = { Text("全班 +1") }, enabled = !selecting && c.students.isNotEmpty(), onClick = { menu = false; vm.addScore(c.students.map { it.id }, 1.0, "全班表现好"); kit.celebrate(); kit.undoable("全班 +1 🎉") })
                                    DropdownMenuItem(text = { Text("重置全班分数") }, enabled = !selecting && c.students.isNotEmpty(), onClick = { menu = false; resetFor = c.students.map { it.id } })
                                    DropdownMenuItem(text = { Text("修改基准分") }, onClick = { menu = false; editBase = true })
                                }
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
                        SegmentedButton(selected = mode == i, onClick = { mode = i; selecting = false; selected = emptySet() }, shape = SegmentedButtonDefaults.itemShape(i, 3)) { Text(t) }
                    }
                }
                if (mode == 0 && !readOnly) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (selecting) "点击学生选择或取消" else "点学生加减分，或开启多选", modifier = Modifier.weight(1f), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(enabled = c.students.isNotEmpty(), onClick = { selecting = !selecting; selected = emptySet() }) { Text(if (selecting) "退出多选" else "多选") }
                    }
                }
            }
            when (mode) {
                0 -> itemsIndexed(stats, key = { _, s -> s.student.id }) { i, st ->
                    RankRow(i + 1, st, selecting, st.student.id in selected, valueText = st.score.fmt(), sub = "交${st.submitted} · 迟${st.late} · 缺${st.missing}",
                        onClick = {
                            if (selecting) selected = if (st.student.id in selected) selected - st.student.id else selected + st.student.id
                            else if (readOnly) kit.nav.push(Route.StudentDetail(st.student.id))
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
                        GroupRow(i + 1, g, l, avg) { if (!readOnly) scoreFor = l.map { it.student.id } }
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
        if (selecting && !readOnly) {
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp).clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainer).padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("已选 ${selectedIds.size} 人", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { selected = c.students.map { it.id }.toSet() }) { Text("全选") }
                    TextButton(onClick = { selected = emptySet() }) { Text("清空") }
                    TextButton(onClick = { selecting = false; selected = emptySet() }) { Text("取消") }
                }
                Button(enabled = selectedIds.isNotEmpty(), onClick = { scoreFor = selectedIds }, modifier = Modifier.fillMaxWidth()) { Text("批量加减分") }
                TextButton(enabled = selectedIds.isNotEmpty(), onClick = { resetFor = selectedIds }, modifier = Modifier.fillMaxWidth()) { Text("重置分数") }
            }
        }
    }

    scoreFor?.takeIf { !readOnly }?.let { ids ->
        val targets = c.students.filter { it.id in ids }
        if (targets.isNotEmpty()) ScoreDialog(targets, data.settings.quickReasons, { scoreFor = null }) { d, r ->
            vm.addScore(targets.map { it.id }, d, r)
            kit.haptic(true)
            if (d > 0) kit.beep()
            kit.undoable(if (targets.size == 1) "${targets[0].name} ${d.signed()}" else "${targets.size} 人 ${d.signed()}")
            selecting = false; selected = emptySet()
        }
    }
    if (editBase && !readOnly) ScoreValueDialog(
        title = "修改基准分", initial = rules.initial,
        description = "适用于所有班级。只修改计分基准，历史加减分继续计入，未交次数和记录保留。",
        onDismiss = { editBase = false },
    ) { value ->
        vm.update { it.copy(settings = it.settings.copy(rules = it.settings.rules.copy(initial = value))) }
        editBase = false; kit.undoable("基准分已改为 ${value.fmt()}，历史记录已保留")
    }
    resetFor?.takeIf { !readOnly }?.let { ids ->
        val targets = c.students.filter { it.id in ids }
        if (targets.isNotEmpty()) ScoreValueDialog(
            title = "重置 ${targets.size} 人的分数", initial = rules.initial,
            description = targets.joinToString("、") { "${it.no} ${it.name}" } + "\n\n将当前积分调整到指定分数，每人新增一条“重置分数”记录。未交次数、作业状态和原有加减分记录全部保留；之后的加减分继续累计。",
            onDismiss = { resetFor = null },
        ) { value ->
            vm.resetScores(targets.map { it.id }, value)
            resetFor = null; selecting = false; selected = emptySet()
            kit.undoable("已将 ${targets.size} 人重置为 ${value.fmt()} 分，未交次数及历史记录已保留")
        }
    }
}

@Composable
private fun ScoreValueDialog(title: String, initial: Double, description: String, onDismiss: () -> Unit, onConfirm: (Double) -> Unit) {
    var text by remember { mutableStateOf(initial.fmt()) }
    val value = text.toDoubleOrNull()?.takeIf { it.isFinite() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(description, modifier = Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState()), fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(text, { text = it }, label = { Text("分数") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            }
        },
        confirmButton = { TextButton(enabled = value != null, onClick = { value?.let(onConfirm) }) { Text("确认") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
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
