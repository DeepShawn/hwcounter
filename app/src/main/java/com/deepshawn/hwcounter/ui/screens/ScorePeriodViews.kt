package com.deepshawn.hwcounter.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepshawn.hwcounter.data.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ScorePeriodBanner(c: SchoolClass) {
    val reset = c.activeScoreReset ?: return
    Column(Modifier.fillMaxWidth().padding(16.dp).background(Color(0xFFFFF3D6), MaterialTheme.shapes.medium).padding(12.dp)) {
        Text("本轮积分起点：${reset.assignmentTitle}（含本次）", color = Color(0xFF895400), fontWeight = FontWeight.Bold)
        Text("之前的作业、未交次数及积分流水完整保留", color = Color(0xFF895400), fontSize = 12.sp)
    }
}

@Composable
fun ScorePeriodDivider(reset: ScoreReset) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        HorizontalDivider(thickness = 3.dp, color = Color(0xFFF59F00))
        Text("积分重置分界 · ${reset.assignmentTitle}（含本次）", color = Color(0xFFAD6800), fontWeight = FontWeight.Bold)
        Text("以下为历史作业，仅保留记录与次数，不计入本轮积分", fontSize = 12.sp)
    }
}

@Composable
fun ScorePeriodDialog(c: SchoolClass, baseline: Double, onDismiss: () -> Unit, onSave: (String, Boolean) -> Unit, onRestore: () -> Unit) {
    val reset = c.activeScoreReset
    val assignments = remember(c.assignments) { c.assignments.sortedByDescending { it.createdAt } }
    var selected by remember(c.id) { mutableStateOf(reset?.assignmentId?.takeIf { id -> assignments.any { it.id == id } } ?: assignments.firstOrNull()?.id) }
    var keepManual by remember(c.id) { mutableStateOf(reset?.keepManualSinceStart ?: true) }
    val date = remember { SimpleDateFormat("yyyy/M/d HH:mm", Locale.CHINA) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("按作业重置积分") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text("选中的作业及之后的作业重新计分，基准为 ${baseline.fmt()} 分。作业、未交次数和积分流水全部保留。", fontSize = 13.sp)
                assignments.forEach { a ->
                    Row(Modifier.fillMaxWidth().clickable { selected = a.id }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected == a.id, { selected = a.id })
                        Column { Text(a.title); Text(date.format(Date(a.createdAt)), fontSize = 11.sp) }
                    }
                }
                Row(Modifier.fillMaxWidth().clickable { keepManual = !keepManual }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(keepManual, { keepManual = it })
                    Text("保留起点日期之后的手动加减分", fontSize = 13.sp)
                }
                Text(if (keepManual) "起点之前的手动分和之前的重置调整不再计入。" else "现有手动分全部转为历史记录，重置后的新加减分继续累计。", fontSize = 12.sp)
                if (reset != null) TextButton(onClick = onRestore) { Text("恢复全部历史计分") }
            }
        },
        confirmButton = { TextButton(enabled = selected != null, onClick = { selected?.let { onSave(it, keepManual) } }) { Text("应用积分起点") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
