@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.deepshawn.hwcounter.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepshawn.hwcounter.data.SchoolClass
import com.deepshawn.hwcounter.data.Student
import com.deepshawn.hwcounter.ui.LocalKit
import com.deepshawn.hwcounter.ui.components.Avatar
import com.deepshawn.hwcounter.ui.components.Chip2
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

@Composable
fun NewAssignmentDialog(
    subjects: List<String>,
    initialTitle: String = "",
    initialSubject: String = "",
    confirmText: String = "创建",
    /** 非空时显示“先存为草稿”选项，勾选后走这里 */
    onDraft: ((title: String, subject: String) -> Unit)? = null,
    onDismiss: () -> Unit,
    onConfirm: (title: String, subject: String) -> Unit,
) {
    var asDraft by remember { mutableStateOf(false) }
    var subject by remember { mutableStateOf(initialSubject.ifBlank { subjects.firstOrNull() ?: "" }) }
    val today = remember { SimpleDateFormat("M月d日", Locale.CHINA).format(Date()) }
    var title by remember { mutableStateOf(initialTitle) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialTitle.isBlank()) "📝 新建作业" else "✏️ 编辑作业") },
        text = {
            Column {
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text("作业名称") }, placeholder = { Text("$today $subject 作业") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Text("科目", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    subjects.forEach { s -> Chip2(s, s == subject, { subject = s }) }
                }
                if (onDraft != null) {
                    Spacer(Modifier.height(8.dp))
                    DraftCheckbox(asDraft) { asDraft = it }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val tt = title.ifBlank { "$today $subject 作业" }
                if (asDraft && onDraft != null) onDraft(tt, subject) else onConfirm(tt, subject)
            }) { Text(if (asDraft && onDraft != null) "创建草稿" else confirmText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

val ClassEmojis = listOf("🎒", "🚀", "🌟", "📚", "🦄", "🐼", "🌈", "⚡", "🍀", "🎯", "🐯", "🌻")

@Composable
fun ClassDialog(
    initial: SchoolClass?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, emoji: String) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var emoji by remember { mutableStateOf(initial?.emoji ?: ClassEmojis.first()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "新建班级" else "编辑班级") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("班级名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ClassEmojis.forEach { e -> Chip2(e, e == emoji, { emoji = e }) }
                }
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onConfirm(name.trim(), emoji) }) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String = "确定", onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirm, color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 随机点名：老虎机滚动效果 */
@Composable
fun RandomPickDialog(pool: List<Student>, title: String = "🎲 随机点名", onDismiss: () -> Unit, onPicked: ((Student) -> Unit)? = null) {
    val kit = LocalKit.current
    var shown by remember { mutableStateOf(pool.randomOrNull()) }
    var round by remember { mutableIntStateOf(0) }
    var done by remember { mutableStateOf(false) }
    val scale = remember { Animatable(1f) }
    LaunchedEffect(round) {
        if (round == 0 || pool.isEmpty()) return@LaunchedEffect
        done = false
        val steps = 22
        for (i in 0 until steps) {
            shown = pool[Random.nextInt(pool.size)]
            kit.haptic()
            delay((30 + i * i * 0.6).toLong())
        }
        done = true
        kit.haptic(true)
        kit.beep()
        scale.snapTo(1.4f)
        scale.animateTo(1f, tween(450, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(Unit) { round = 1 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                if (pool.isEmpty()) {
                    Text("没有可抽取的同学")
                } else {
                    val s = shown
                    Box(Modifier.padding(12.dp).scale(scale.value), contentAlignment = Alignment.Center) {
                        if (s != null) Avatar(s.name, size = 96.dp)
                    }
                    Text(s?.name ?: "", fontSize = 34.sp, fontWeight = FontWeight.Black)
                    Text(s?.no ?: "", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text("候选 ${pool.size} 人", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            Row {
                if (onPicked != null && done && shown != null) {
                    TextButton(onClick = { shown?.let(onPicked); onDismiss() }) { Text("加分 +1") }
                    Spacer(Modifier.width(4.dp))
                }
                FilledTonalButton(enabled = pool.isNotEmpty(), onClick = { round++ }) { Text("再抽一次") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
fun Dot(size: Int = 6) = Spacer(Modifier.size(size.dp))
