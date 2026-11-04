@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)

package com.deepshawn.hwcounter.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deepshawn.hwcounter.data.AppData
import com.deepshawn.hwcounter.data.AppViewModel
import com.deepshawn.hwcounter.data.Draft
import com.deepshawn.hwcounter.data.HwStatus
import com.deepshawn.hwcounter.data.SchoolClass
import com.deepshawn.hwcounter.data.Student
import com.deepshawn.hwcounter.export.Exporter
import com.deepshawn.hwcounter.recog.ImageUtils
import com.deepshawn.hwcounter.recog.LocalRecognizer
import com.deepshawn.hwcounter.recog.StatTable
import com.deepshawn.hwcounter.ui.LocalKit
import com.deepshawn.hwcounter.ui.Route
import com.deepshawn.hwcounter.ui.components.AppCard
import com.deepshawn.hwcounter.ui.components.Avatar
import com.deepshawn.hwcounter.ui.components.Chip2
import com.deepshawn.hwcounter.ui.components.EmptyState
import com.deepshawn.hwcounter.ui.components.GradientHeader
import com.deepshawn.hwcounter.ui.components.ProgressRing
import com.deepshawn.hwcounter.ui.components.statusColor
import com.deepshawn.hwcounter.ui.components.statusContainer
import com.deepshawn.hwcounter.ui.rememberPhotoLauncher
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val timeFmt = SimpleDateFormat("M月d日 HH:mm", Locale.CHINA)
fun fmtTime(t: Long): String = timeFmt.format(Date(t))
private val REG = listOf(HwStatus.SUBMITTED, HwStatus.EXCELLENT, HwStatus.LATE, HwStatus.MISSING, HwStatus.LEAVE)

/** 分享统计名单 Excel */
fun shareStatList(ctx: android.content.Context, c: SchoolClass, title: String, subject: String, createdAt: Long, statuses: Map<String, HwStatus>, comments: Map<String, String> = emptyMap()) {
    val f = Exporter.statListFile(ctx, c, title, subject, createdAt, statuses, comments)
    Exporter.shareFile(ctx, f, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "分享统计名单")
}

/** 选择定时上传的时间：快捷选项 + 系统日期/时间选择器 */
@Composable
fun ScheduleDialog(initial: Long?, onDismiss: () -> Unit, onSave: (Long?) -> Unit) {
    val ctx = LocalContext.current
    var at by remember { mutableStateOf(initial ?: (System.currentTimeMillis() + 3_600_000)) }
    fun today(h: Int, m: Int, addDay: Boolean) = Calendar.getInstance().apply { if (addDay) add(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m); set(Calendar.SECOND, 0) }.timeInMillis
    val now = System.currentTimeMillis()
    val quick = listOf(
        "45 分钟后" to now + 45 * 60_000L, "1 小时后" to now + 3_600_000L,
        "今晚 20:00" to today(20, 0, Calendar.getInstance().get(Calendar.HOUR_OF_DAY) >= 20), "明早 7:00" to today(7, 0, true),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("⏰ 定时上传") },
        text = {
            Column {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    quick.forEach { (l, t) -> Chip2(l, false, { at = t }) }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(fmtTime(at), fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        val cal = Calendar.getInstance().apply { timeInMillis = at }
                        DatePickerDialog(ctx, { _, y, mo, d ->
                            TimePickerDialog(ctx, { _, h, mi ->
                                at = Calendar.getInstance().apply { set(y, mo, d, h, mi, 0) }.timeInMillis
                            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
                        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
                    }) { Text("选时间") }
                }
                Text("到时间自动写进作业并上传到云端。App 需在后台运行；如果被系统关掉，下次打开时立即上传。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { Button(onClick = { onSave(if (at <= System.currentTimeMillis()) System.currentTimeMillis() + 1000 else at) }) { Text("确定") } },
        dismissButton = {
            Row {
                if (initial != null) TextButton(onClick = { onSave(null) }) { Text("取消定时") }
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        },
    )
}

// ---------------- 草稿页 ----------------
@Composable
fun DraftScreen(vm: AppViewModel, data: AppData, draftId: String) {
    val kit = LocalKit.current
    val ctx = LocalContext.current
    val drafts by vm.drafts.collectAsStateWithLifecycle()
    val d = drafts.firstOrNull { it.id == draftId }
    val c = d?.let { dr -> data.classes.firstOrNull { it.id == dr.classId } }
    if (d == null || c == null) { LaunchedEffect(Unit) { kit.nav.pop() }; return }
    var sheetFor by remember { mutableStateOf<Student?>(null) }
    var sched by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmFill by remember { mutableStateOf(false) }
    var filter by remember { mutableIntStateOf(0) }
    var seatView by remember { mutableStateOf(c.seating != null) }
    var typed by remember { mutableStateOf("") }
    var typedAs by remember { mutableStateOf(HwStatus.SUBMITTED) }
    var ambiguous by remember { mutableStateOf<List<com.deepshawn.hwcounter.recog.VoiceParser.Match>>(emptyList()) }
    val t = StatTable.tally(c.students, d.statuses)
    val changed = remember(d) { StatTable.changes(d).toSet() }
    fun stOf(s: Student) = d.statuses[s.id] ?: HwStatus.NONE
    fun toggle(s: Student) { val x = stOf(s); vm.setDraftStatus(d.id, listOf(s.id), if (x == HwStatus.NONE || x == HwStatus.MISSING) HwStatus.SUBMITTED else HwStatus.NONE); kit.haptic() }
    fun submitTyped() {
        val txt = typed.trim(); if (txt.isEmpty()) return
        val parser = com.deepshawn.hwcounter.recog.VoiceParser(c.students, { ch -> com.deepshawn.hwcounter.recog.Pinyin.readings(ch) }, listOf(0.6f, 1f, 1.4f)[data.settings.voiceFuzzy.coerceIn(0, 2)], true)
        val r = parser.parse(txt)
        val sure = r.matches.filter { it.studentIds.size == 1 }.map { it.studentIds[0] }.distinct()
        if (sure.isNotEmpty()) {
            vm.setDraftStatus(d.id, sure, typedAs)
            kit.toast("草稿：" + sure.mapNotNull { id -> c.students.firstOrNull { it.id == id }?.name }.joinToString("、") + " → ${typedAs.label}")
        } else if (r.matches.none { it.studentIds.size > 1 }) kit.toast("没有认出名单里的同学")
        ambiguous = r.matches.filter { it.studentIds.size > 1 }
        typed = ""
    }
    val filters = listOf("全部", "未登记", "未交", "已交", "改动")
    val list = c.students.filter { s ->
        val x = stOf(s)
        when (filter) {
            1 -> x == HwStatus.NONE
            2 -> x == HwStatus.NONE || x == HwStatus.MISSING
            3 -> x == HwStatus.SUBMITTED || x == HwStatus.EXCELLENT || x == HwStatus.LATE
            4 -> s.id in changed
            else -> true
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            GradientHeader {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { kit.nav.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = Color.White) }
                    Column(Modifier.weight(1f)) {
                        Text("📝 草稿 · 只保存在本机 · ${if (d.isNew) "新作业" else "已有作业"}", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                        Text(d.title.ifBlank { "未命名作业" }, color = Color.White, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.combinedClickable(onClick = { edit = true }))
                        Text("${d.subject} · ${c.name} · 编辑于 ${fmtTime(d.updatedAt)}", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProgressRing(t.rate, size = 86.dp, stroke = 9.dp) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${t.handedIn}", color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp)
                            Text("/ ${t.expected}", color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp)
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        (REG + HwStatus.NONE).forEach { k -> Text("${k.label} ${t.count(k)}", color = Color.White, fontSize = 12.sp) }
                        Text("✏️ 改动 ${changed.size} 人", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        item {
            AppCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), padding = PaddingValues(12.dp)) {
                Text(d.scheduledAt?.let { "⏰ 将在 ${fmtTime(it)} 自动上传" } ?: "还没上传：云端和其它设备暂时看不到这些登记", fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.uploadDrafts(listOf(d.id)) }, Modifier.weight(1f)) { Text("☁️ 上传") }
                    FilledTonalButton(onClick = { sched = true }, Modifier.weight(1f)) { Text(if (d.scheduledAt != null) "⏰ 改时间" else "⏰ 定时上传") }
                }
            }
        }
        item {
            FlowRow(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip({ kit.nav.push(Route.StatTable(d.id)) }, { Text("📊 从统计表导入") })
                AssistChip({ confirmFill = true }, { Text("☑️ 剩余标未交") })
                AssistChip({ shareStatList(ctx, c, d.title, d.subject, d.createdAt, d.statuses, d.comments) }, { Text("📋 统计名单") })
                AssistChip({ confirmDelete = true }, { Text("🗑️ 删除草稿", color = statusColor(HwStatus.MISSING)) })
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(typed, { typed = it }, Modifier.weight(1f), singleLine = true, placeholder = { Text("⌨️ 首字母 zjy lzh / 学号 / 姓名", fontSize = 13.sp) })
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { submitTyped() }) { Text("登记") }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { REG.forEach { s -> Chip2(s.label, typedAs == s, { typedAs = s }, color = statusColor(s)) } }
                ambiguous.forEach { m ->
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.Center) {
                        Text("“${m.heard}”是：", fontSize = 13.sp)
                        m.studentIds.forEach { id -> c.students.firstOrNull { it.id == id }?.let { s -> Chip2("${s.name} ${s.no}", false, { vm.setDraftStatus(d.id, listOf(id), typedAs); ambiguous = ambiguous - m }) } }
                    }
                }
            }
        }
        item {
            FlowRow(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (c.seating != null) Chip2("🪑 座位表", seatView, { seatView = !seatView })
                filters.forEachIndexed { i, l -> Chip2(l, filter == i, { filter = i }) }
            }
            Text("点一下切换 已交/未登记 · 长按设置状态、备注 · ✏️ = 相对云端改过", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
        }
        val seating = c.seating
        if (seatView && seating != null) {
            item {
                SeatChart(
                    seating, c.students,
                    cell = { st, _ ->
                        if (st == null) SeatCellUi(Color.Transparent, MaterialTheme.colorScheme.outlineVariant) { Text("") }
                        else {
                            val x = stOf(st)
                            SeatCellUi(if (x == HwStatus.NONE) MaterialTheme.colorScheme.surfaceContainerLowest else statusContainer(x), if (x == HwStatus.NONE) MaterialTheme.colorScheme.outlineVariant else statusColor(x)) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text((if (st.id in changed) "✏️" else "") + st.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(x.label, fontSize = 10.sp, color = statusColor(x))
                                }
                            }
                        }
                    },
                    selected = { _, _ -> false },
                    onCell = { _, st -> st?.let { toggle(it) } },
                    onLong = { sheetFor = it },
                )
            }
            val un = c.students.filter { it.id !in seating.seats }
            if (un.isNotEmpty()) item {
                FlowRow(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("未入座：", fontSize = 12.sp)
                    un.forEach { s -> Chip2("${s.name} · ${stOf(s).label}", false, { toggle(s) }) }
                }
            }
        } else {
            items(list.chunked(3), key = { r -> r.joinToString { it.id } }) { row ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { s -> DraftStudentCard(s, stOf(s), s.id in changed, d.comments[s.id], Modifier.weight(1f), { toggle(s) }, { sheetFor = s }) }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }

    sheetFor?.let { s ->
        var note by remember(s.id) { mutableStateOf(d.comments[s.id] ?: "") }
        AlertDialog(
            onDismissRequest = { vm.setDraftComment(d.id, s.id, note); sheetFor = null },
            title = { Row(verticalAlignment = Alignment.CenterVertically) { Avatar(s.name, size = 34.dp); Spacer(Modifier.width(8.dp)); Text("${s.name} ${s.no}") } },
            text = {
                Column {
                    Text("草稿里的状态", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        (REG + HwStatus.NONE).forEach { k -> StatusButton(k, stOf(s) == k) { vm.setDraftStatus(d.id, listOf(s.id), k) } }
                    }
                    if (!d.isNew) Text("云端现在：${(d.baseStatuses[s.id] ?: HwStatus.NONE).label}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(note, { note = it }, label = { Text("备注") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = { TextButton(onClick = { vm.setDraftComment(d.id, s.id, note); sheetFor = null }) { Text("完成") } },
        )
    }
    if (sched) ScheduleDialog(d.scheduledAt, { sched = false }) { at ->
        vm.editDraft(d.id) { it.copy(scheduledAt = at) }; sched = false
        kit.toast(if (at != null) "⏰ 将在 ${fmtTime(at)} 自动上传" else "已取消定时上传")
    }
    if (edit) NewAssignmentDialog(data.settings.subjects, d.title, d.subject, "保存", onDismiss = { edit = false }) { tt, ss ->
        vm.editDraft(d.id) { it.copy(title = tt, subject = ss) }; edit = false
    }
    if (confirmDelete) ConfirmDialog("删除草稿？", "里面的登记不会上传。", "删除", { confirmDelete = false }) {
        confirmDelete = false; vm.deleteDraft(d.id); kit.toast("已删除草稿")
    }
    if (confirmFill) ConfirmDialog("剩余标未交", "将 ${t.count(HwStatus.NONE)} 名未登记的同学标记为“未交”？", "确定", { confirmFill = false }) {
        confirmFill = false; vm.setDraftStatus(d.id, t.lists[HwStatus.NONE].orEmpty().map { it.id }, HwStatus.MISSING)
    }
}

@Composable
private fun DraftStudentCard(s: Student, st: HwStatus, changed: Boolean, comment: String?, modifier: Modifier, onTap: () -> Unit, onLong: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (st == HwStatus.NONE) MaterialTheme.colorScheme.surfaceContainerLowest else statusContainer(st),
        border = if (st == HwStatus.NONE) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
        modifier = modifier.combinedClickable(onClick = onTap, onLongClick = onLong),
    ) {
        Column(Modifier.padding(8.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Avatar(s.name, size = 38.dp)
            Text((if (changed) "✏️" else "") + s.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(s.no, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(st.label, color = statusColor(st), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            if (!comment.isNullOrBlank()) Text("💬 $comment", fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ---------------- 草稿箱 ----------------
@Composable
fun DraftListScreen(vm: AppViewModel, data: AppData) {
    val kit = LocalKit.current
    val drafts by vm.drafts.collectAsStateWithLifecycle()
    var sched by remember { mutableStateOf<Draft?>(null) }
    var del by remember { mutableStateOf<Draft?>(null) }
    val list = drafts.sortedByDescending { it.updatedAt }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            GradientHeader {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { kit.nav.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = Color.White) }
                    Column(Modifier.weight(1f)) {
                        Text("🗂️ 草稿箱", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                        Text("草稿只保存在这台设备上，上传后才会进入云端数据库", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                    }
                    if (list.size > 1) TextButton(onClick = { vm.uploadDrafts(list.map { it.id }) }) { Text("全部上传", color = Color.White) }
                }
            }
        }
        if (list.isEmpty()) item { EmptyState("📭", "没有草稿", "新建作业时勾选“先存为草稿”，或在作业页菜单里选“在草稿里登记”，也可以用“统计表识别”导入") }
        items(list, key = { it.id }) { d ->
            val c = data.classes.firstOrNull { it.id == d.classId }
            val t = c?.let { StatTable.tally(it.students, d.statuses) }
            AppCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp), onClick = { if (c != null) kit.nav.push(Route.Draft(d.id)) else kit.toast("草稿所属的班级已不存在") }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text((if (d.isNew) "🆕 " else "") + d.title.ifBlank { "未命名作业" }, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${c?.let { "${it.emoji} ${it.name}" } ?: "⚠️ 班级已删除"} · ${d.subject} · 改动 ${StatTable.changes(d).size} 人 · 编辑于 ${fmtTime(d.updatedAt)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        d.scheduledAt?.let { Text("⏰ ${fmtTime(it)} 自动上传", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary) }
                    }
                    if (t != null) Column(horizontalAlignment = Alignment.End) {
                        Text("${t.handedIn}/${t.expected}", fontWeight = FontWeight.Black)
                        Text("${(t.rate * 100).toInt()}%", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { del = d }) { Text("删除", color = statusColor(HwStatus.MISSING)) }
                    TextButton(onClick = { sched = d }) { Text(if (d.scheduledAt != null) "⏰ 改时间" else "⏰ 定时上传") }
                    Button(onClick = { vm.uploadDrafts(listOf(d.id)) }) { Text("☁️ 上传") }
                }
            }
        }
    }
    sched?.let { d ->
        ScheduleDialog(d.scheduledAt, { sched = null }) { at ->
            vm.editDraft(d.id) { it.copy(scheduledAt = at) }; sched = null
            kit.toast(if (at != null) "⏰ 将在 ${fmtTime(at)} 自动上传" else "已取消定时上传")
        }
    }
    del?.let { d -> ConfirmDialog("删除草稿？", "「${d.title}」里的登记不会上传。", "删除", { del = null }) { del = null; vm.deleteDraft(d.id) } }
}

// ---------------- 统计表识别 ----------------
@Composable
fun StatTableScreen(vm: AppViewModel, data: AppData, draftId: String?) {
    val kit = LocalKit.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val drafts by vm.drafts.collectAsStateWithLifecycle()
    val target = draftId?.let { id -> drafts.firstOrNull { it.id == id } }
    val c = target?.let { t -> data.classes.firstOrNull { it.id == t.classId } } ?: vm.currentClass(data)
    var res by remember { mutableStateOf<StatTable.Result?>(null) }
    var col by remember { mutableIntStateOf(0) }
    var st by remember { mutableStateOf<Map<String, HwStatus>>(emptyMap()) }
    var src by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var paste by remember { mutableStateOf(false) }
    var photoSheet by remember { mutableStateOf(false) }
    var saveAs by remember { mutableStateOf(false) }
    var into by remember { mutableStateOf(false) }
    var pickFor by remember { mutableStateOf<Student?>(null) }
    if (c == null) { EmptyState("🏫", "还没有班级", "先建立班级、导入名单"); return }

    fun use(r: StatTable.Result, from: String) {
        if (r.rows.isEmpty()) { kit.toast("没有认出名单里的同学，请检查表格（需要有姓名或学号一列）"); return }
        if (r.columns.isEmpty()) kit.toast("认出了学生，但没找到 ✓ × 优 迟 假 这类标记列")
        res = r; col = (r.columns.size - 1).coerceAtLeast(0); st = StatTable.columnStatuses(r, col); src = from
    }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                val name = uri.lastPathSegment.orEmpty()
                val bytes = withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } } ?: error("文件打不开")
                val mime = ctx.contentResolver.getType(uri).orEmpty()
                withContext(Dispatchers.Default) {
                    val isZip = bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
                    if (isZip || mime.contains("spreadsheetml")) StatTable.recognizeTable(StatTable.readXlsx(bytes), c.students)
                    else if (name.endsWith(".xls", true) || mime == "application/vnd.ms-excel") error("老版 .xls 读不了：请在 Excel 里“另存为 .xlsx 或 CSV”再导入")
                    else StatTable.recognizeTable(StatTable.splitTable(bytes.toString(Charsets.UTF_8)), c.students)
                }
            }.onSuccess { use(it, "文件") }.onFailure { kit.toast("读取失败：${it.message}") }
        }
    }
    val photo = rememberPhotoLauncher { uris ->
        val uri = uris.firstOrNull() ?: return@rememberPhotoLauncher
        busy = true
        scope.launch {
            runCatching {
                val bmp = withContext(Dispatchers.IO) { ImageUtils.load(ctx, uri, 2400) } ?: error("图片无法打开")
                val text = LocalRecognizer.textRecognizer.process(InputImage.fromBitmap(bmp, 0)).await()
                val lines = text.textBlocks.flatMap { b -> b.lines }.mapNotNull { l -> l.boundingBox?.let { bx -> StatTable.Line(l.text, bx.left.toFloat(), bx.top.toFloat(), bx.height().toFloat()) } }
                withContext(Dispatchers.Default) { StatTable.recognizeLines(StatTable.mergeRows(lines), c.students) }
            }.onSuccess { use(it, "照片") }.onFailure { kit.toast("识别失败：${it.message}") }
            busy = false
        }
    }
    val r = res
    val t = StatTable.tally(c.students, st)
    val header = r?.columns?.getOrNull(col)?.header.orEmpty()
    val today = remember { SimpleDateFormat("M月d日", Locale.CHINA).format(Date()) }
    val inTable = r?.rows?.map { it.studentId }?.toSet().orEmpty()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            GradientHeader {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { kit.nav.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = Color.White) }
                    Column(Modifier.weight(1f)) {
                        Text("📊 统计表识别", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                        Text(target?.let { "导入到草稿：${it.title}" } ?: "${c.emoji} ${c.name} · 认出每个同学交没交，自动统计", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                    }
                }
            }
        }
        item {
            AppCard(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("拍照（登记表、点名册）、Excel（.xlsx）、CSV，或直接粘贴表格。每行一个同学（姓名或学号），后面是标记：", fontSize = 13.sp)
                Text("✓ √ 交 1 → 已交 · 优 A ★ → 优秀 · 迟 补 → 迟交 · × X 未交 缺 0 → 未交 · 假 病 → 请假 · 空白 → 未登记。多列（多次作业）可以选用哪一列。", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { photoSheet = true }, Modifier.weight(1f)) { Text("📷 照片") }
                    FilledTonalButton(onClick = { openFile.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/vnd.ms-excel", "text/*", "*/*")) }, Modifier.weight(1f)) { Text("📄 文件") }
                    FilledTonalButton(onClick = { paste = true }, Modifier.weight(1f)) { Text("📋 粘贴") }
                }
                if (busy) Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)); Text("正在识别照片…", fontSize = 13.sp)
                }
            }
        }
        if (r != null) {
            item {
                AppCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Text("$src · 认出 ${r.rows.size} 人" + if (r.unmatched.isNotEmpty()) " · ${r.unmatched.size} 行没对上名单" else "", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (r.columns.size > 1) {
                        Text("表里有 ${r.columns.size} 列标记，用哪一列：", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { r.columns.forEachIndexed { k, cc -> Chip2(cc.header, k == col, { col = k; st = StatTable.columnStatuses(r, k) }) } }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProgressRing(t.rate, size = 80.dp, stroke = 9.dp, track = MaterialTheme.colorScheme.surfaceVariant, colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)) {
                            Text("${t.handedIn}/${t.expected}", fontWeight = FontWeight.Black)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("交齐率 ${(t.rate * 100).toInt()}%", fontWeight = FontWeight.Bold)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { (REG + HwStatus.NONE).forEach { k -> Text("${k.label} ${t.count(k)}", color = statusColor(k), fontSize = 12.sp, fontWeight = FontWeight.Bold) } }
                            if (t.count(HwStatus.MISSING) > 0) Text("❌ 未交：" + t.lists[HwStatus.MISSING].orEmpty().joinToString("、") { it.name }, fontSize = 12.sp)
                            if (t.count(HwStatus.NONE) > 0) Text("⏳ 未登记：" + t.lists[HwStatus.NONE].orEmpty().joinToString("、") { it.name }, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { shareStatList(ctx, c, header, "", System.currentTimeMillis(), st) }) { Text("📋 导出统计名单") }
                        if (target != null) Button(onClick = {
                            vm.editDraft(target.id) { it.copy(statuses = it.statuses + st) }; kit.toast("已导入 ${st.size} 人到草稿"); kit.nav.pop()
                        }) { Text("写入这份草稿") }
                        else {
                            if (r.columns.size > 1) FilledTonalButton(onClick = {
                                vm.addDrafts(r.columns.mapIndexed { k, cc -> StatTable.newDraft(c, null, if (Regex("^第\\d+列$").matches(cc.header)) "$today 作业 ${k + 1}" else cc.header, "", StatTable.columnStatuses(r, k)) })
                                kit.toast("已存为 ${r.columns.size} 份草稿"); kit.nav.replace(Route.Drafts)
                            }) { Text("每列各存一份草稿") }
                            if (c.assignments.isNotEmpty()) FilledTonalButton(onClick = { into = true }) { Text("写入已有作业") }
                            Button(onClick = { saveAs = true }) { Text("📝 存为新作业草稿") }
                        }
                    }
                }
            }
            item { Text("核对一下：点状态可以改；没对上的行在最下面", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp, 10.dp)) }
            items(c.students, key = { it.id }) { s ->
                val k = st[s.id] ?: HwStatus.NONE
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(s.name, fontWeight = FontWeight.Bold)
                    Text(" ${s.no}" + if (s.id !in inTable) " · 表里没有" else "", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Chip2(k.label, k != HwStatus.NONE, { pickFor = s }, color = statusColor(k))
                }
            }
            if (r.unmatched.isNotEmpty()) item {
                AppCard(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("没对上名单的行（${r.unmatched.size}）", fontWeight = FontWeight.Bold)
                    r.unmatched.take(30).forEach { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }

    if (photoSheet) PhotoSourceSheet({ photoSheet = false }, { photoSheet = false; photo.takePhoto() }, { photoSheet = false; photo.pickGallery() })
    pickFor?.let { s ->
        AlertDialog(
            onDismissRequest = { pickFor = null },
            title = { Text(s.name) },
            text = {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    (REG + HwStatus.NONE).forEach { k -> StatusButton(k, (st[s.id] ?: HwStatus.NONE) == k) { st = if (k == HwStatus.NONE) st - s.id else st + (s.id to k); pickFor = null } }
                }
            },
            confirmButton = { TextButton(onClick = { pickFor = null }) { Text("关闭") } },
        )
    }
    if (paste) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { paste = false },
            title = { Text("📋 粘贴统计表") },
            text = {
                Column {
                    Text("从 Excel / WPS / 在线表格里选中整张表复制，粘贴到这里", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(text, { text = it }, minLines = 6, placeholder = { Text("姓名\t10月30日\t10月31日\n张三\t✓\t×\n李四\t优\t✓") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = { Button(enabled = text.isNotBlank(), onClick = { paste = false; use(StatTable.recognizeTable(StatTable.splitTable(text), c.students), "粘贴的表格") }) { Text("识别") } },
            dismissButton = { TextButton(onClick = { paste = false }) { Text("取消") } },
        )
    }
    if (saveAs) {
        val guess = header.takeIf { it.isNotBlank() && !Regex("^第\\d+列$").matches(it) } ?: "$today 作业"
        NewAssignmentDialog(data.settings.subjects, guess, "", "存为草稿", onDismiss = { saveAs = false }) { tt, ss ->
            saveAs = false
            val d = StatTable.newDraft(c, null, tt, ss, st)
            vm.addDraft(d); kit.toast("📝 已存为草稿（只保存在本机）"); kit.nav.replace(Route.Draft(d.id))
        }
    }
    if (into) {
        var menu by remember { mutableStateOf(false) }
        val sorted = c.assignments.sortedByDescending { it.createdAt }
        var pick by remember { mutableStateOf(sorted.first().id) }
        AlertDialog(
            onDismissRequest = { into = false },
            title = { Text("写入已有作业") },
            text = {
                Column {
                    Text("会先存为这次作业的草稿（只改表里有标记的同学），核对后再上传", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Box {
                        TextButton(onClick = { menu = true }) { Text(sorted.firstOrNull { it.id == pick }?.let { "${it.subject} ${it.title} ▾" } ?: "选择作业") }
                        DropdownMenu(menu, { menu = false }) { sorted.forEach { a -> DropdownMenuItem(text = { Text("${a.subject} ${a.title}") }, onClick = { pick = a.id; menu = false }) } }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    into = false
                    val a = c.assignments.first { it.id == pick }
                    val ex = drafts.firstOrNull { it.assignmentId == a.id }
                    if (ex != null) { vm.editDraft(ex.id) { it.copy(statuses = it.statuses + st) }; kit.nav.replace(Route.Draft(ex.id)) }
                    else { val d = StatTable.newDraft(c, a, statuses = st); vm.addDraft(d); kit.nav.replace(Route.Draft(d.id)) }
                    kit.toast("已写入“${a.title}”的草稿，确认后再上传")
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { into = false }) { Text("取消") } },
        )
    }
}

/** 新建作业对话框里的“先存为草稿”选项 */
@Composable
fun DraftCheckbox(checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, onChange)
        Column {
            Text("📝 先存为草稿", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text("登记只保存在本机，确认后再手动上传或定时上传", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
