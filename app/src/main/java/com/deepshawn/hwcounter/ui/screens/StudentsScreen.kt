@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)

package com.deepshawn.hwcounter.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepshawn.hwcounter.data.AppData
import com.deepshawn.hwcounter.data.AppViewModel
import com.deepshawn.hwcounter.data.HwStatus
import com.deepshawn.hwcounter.data.RosterImport
import com.deepshawn.hwcounter.data.Student
import com.deepshawn.hwcounter.data.fmt
import com.deepshawn.hwcounter.data.signed
import com.deepshawn.hwcounter.data.studentStats
import com.deepshawn.hwcounter.export.Exporter
import com.deepshawn.hwcounter.ui.LocalKit
import com.deepshawn.hwcounter.ui.Route
import com.deepshawn.hwcounter.ui.components.AppCard
import com.deepshawn.hwcounter.ui.components.Avatar
import com.deepshawn.hwcounter.ui.components.EmptyState
import com.deepshawn.hwcounter.ui.components.GradientHeader
import com.deepshawn.hwcounter.ui.components.SectionTitle
import com.deepshawn.hwcounter.ui.components.StatTile
import com.deepshawn.hwcounter.ui.components.statusColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StudentsScreen(vm: AppViewModel, data: AppData) {
    val kit = LocalKit.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = vm.currentClass(data)
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Student?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Student?>(null) }
    var importResult by remember { mutableStateOf<RosterImport.Result?>(null) }
    var showTemplate by remember { mutableStateOf(false) }
    var paste by remember { mutableStateOf(false) }

    val openJson = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                val text = withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: "" }
                val r = RosterImport.parse(text)
                if (r.students.isEmpty()) kit.toast("没有解析到学生，请检查格式") else importResult = r
            } catch (e: Exception) {
                kit.toast("解析失败：${e.message}")
            }
        }
    }
    val saveTemplate = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) runCatching { ctx.contentResolver.openOutputStream(uri)?.use { it.write(RosterImport.TEMPLATE.toByteArray()) } }
            .onSuccess { kit.toast("模板已保存") }
    }
    val exportRoster = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val cc = c ?: return@rememberLauncherForActivityResult
        if (uri != null) runCatching {
            val text = buildString {
                append("{\n  \"className\": \"${cc.name.replace("\"", "'")}\",\n  \"students\": [\n")
                append(cc.students.joinToString(",\n") { s -> "    {\"no\": \"${s.no}\", \"name\": \"${s.name}\", \"group\": \"${s.group}\", \"gender\": \"${s.gender}\"}" })
                append("\n  ]\n}\n")
            }
            ctx.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
        }.onSuccess { kit.toast("名单已导出") }
    }

    if (c == null) {
        EmptyState("🏫", "还没有班级", "去首页新建班级")
        return
    }
    val list = c.students.filter { query.isBlank() || it.name.contains(query) || it.no.contains(query) || it.group.contains(query) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            GradientHeader {
                Text("名单管理", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Text("${c.emoji} ${c.name} · ${c.students.size} 人 · ${c.students.map { it.group }.filter { it.isNotBlank() }.distinct().size} 个小组", color = Color.White.copy(alpha = 0.85f))
                Spacer(Modifier.height(12.dp))
                TextField(
                    query, { query = it },
                    placeholder = { Text("搜索姓名 / 学号 / 小组") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, null) } },
                    singleLine = true, shape = CircleShape,
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                        focusedContainerColor = Color.White, unfocusedContainerColor = Color.White.copy(alpha = 0.92f),
                        focusedTextColor = Color(0xFF1B1B1F), unfocusedTextColor = Color(0xFF1B1B1F),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip({ openJson.launch(arrayOf("application/json", "text/*", "*/*")) }, { Text("导入 JSON/CSV") }, leadingIcon = { Icon(Icons.Filled.FileUpload, null, Modifier.size(18.dp)) })
                AssistChip({ paste = true }, { Text("粘贴名单") }, leadingIcon = { Text("📋") })
                AssistChip({ kit.nav.push(Route.Seating) }, { Text(if (c.seating == null) "座位表" else "座位表（${c.seating.seats.size}人）") }, leadingIcon = { Text("🪑") })
                AssistChip({ adding = true }, { Text("添加学生") }, leadingIcon = { Icon(Icons.Filled.PersonAdd, null, Modifier.size(18.dp)) })
                AssistChip({
                    if (c.students.isEmpty()) kit.toast("名单为空") else scope.launch {
                        val f = withContext(Dispatchers.Default) {
                            File(Exporter.exportDir(ctx), "${c.name}_学号二维码贴纸.pdf".replace("/", "_")).also { Exporter.qrLabelsPdf(c, it) }
                        }
                        Exporter.shareFile(ctx, f, "application/pdf", "打印二维码贴纸")
                    }
                }, { Text("二维码贴纸 PDF") }, leadingIcon = { Icon(Icons.Filled.QrCode2, null, Modifier.size(18.dp)) })
                AssistChip({ showTemplate = true }, { Text("JSON 格式说明") }, leadingIcon = { Text("📘") })
                AssistChip({ exportRoster.launch("${c.name}_名单.json") }, { Text("导出名单") }, leadingIcon = { Icon(Icons.Filled.FileDownload, null, Modifier.size(18.dp)) })
                AssistChip({ vm.sortStudents(); kit.toast("已按学号排序") }, { Text("按学号排序") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.Sort, null, Modifier.size(18.dp)) })
            }
        }
        if (c.students.isEmpty()) {
            item {
                EmptyState("👥", "名单还是空的", "导入 JSON（支持多种格式，自动识别字段）、粘贴 Excel 两列，或手动添加") {
                    FilledTonalButton(onClick = { showTemplate = true }) { Text("查看 JSON 模板") }
                }
            }
        }
        items(list, key = { it.id }) { s ->
            var menu by remember { mutableStateOf(false) }
            Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Row(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                        .combinedClickable(onClick = { kit.nav.push(Route.StudentDetail(s.id)) }, onLongClick = { menu = true })
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(s.name, size = 42.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(s.name, fontWeight = FontWeight.Bold)
                            if (s.gender.isNotBlank()) Text(if (s.gender.startsWith("女")) " ♀" else " ♂", color = if (s.gender.startsWith("女")) Color(0xFFE64980) else Color(0xFF4C6EF5))
                            if (s.longLeave) {
                                Spacer(Modifier.width(6.dp))
                                Text("长期请假", fontSize = 10.sp, color = Color.White, modifier = Modifier.clip(CircleShape).background(statusColor(HwStatus.LEAVE)).padding(horizontal = 6.dp, vertical = 1.dp))
                            }
                        }
                        Text(listOf(s.no, s.group, s.note).filter { it.isNotBlank() }.joinToString(" · "), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
                    }
                    IconButton(onClick = { editing = s }) { Icon(Icons.Filled.Edit, "编辑", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("编辑") }, leadingIcon = { Icon(Icons.Filled.Edit, null) }, onClick = { menu = false; editing = s })
                    DropdownMenuItem(text = { Text("删除") }, leadingIcon = { Icon(Icons.Filled.Delete, null) }, onClick = { menu = false; deleting = s })
                }
            }
        }
    }

    if (adding) StudentEditDialog(null, { adding = false }) { vm.upsertStudent(it); adding = false; kit.toast("已添加 ${it.name}") }
    editing?.let { s ->
        StudentEditDialog(s, { editing = null }, onDelete = { deleting = s; editing = null }) { vm.upsertStudent(it); editing = null }
    }
    deleting?.let { s ->
        ConfirmDialog("删除学生", "确定删除 ${s.name}？其作业记录和积分也会删除。", "删除", { deleting = null }) {
            vm.deleteStudent(s.id); kit.undoable("已删除 ${s.name}")
        }
    }
    importResult?.let { r ->
        AlertDialog(
            onDismissRequest = { importResult = null },
            title = { Text("📥 导入 ${r.students.size} 名学生") },
            text = {
                Column {
                    r.className?.let { Text("班级：$it", fontWeight = FontWeight.Bold) }
                    Text(r.students.take(8).joinToString("、") { "${it.name}(${it.no})" } + if (r.students.size > 8) " …" else "", fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("请选择导入方式：", style = MaterialTheme.typography.labelLarge)
                }
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = { val n = vm.importRoster(r, replace = false, asNewClass = false); importResult = null; kit.undoable("已合并 $n 人（同学号更新）") }) { Text("合并到当前班级") }
                    TextButton(onClick = { val n = vm.importRoster(r, replace = false, asNewClass = true); importResult = null; kit.toast("已创建新班级，共 $n 人") }) { Text("创建为新班级") }
                    TextButton(onClick = { val n = vm.importRoster(r, replace = true, asNewClass = false); importResult = null; kit.undoable("已替换为 $n 人（作业记录已清空）") }) { Text("替换当前班级", color = MaterialTheme.colorScheme.error) }
                }
            },
            dismissButton = { TextButton(onClick = { importResult = null }) { Text("取消") } },
        )
    }
    if (showTemplate) {
        AlertDialog(
            onDismissRequest = { showTemplate = false },
            title = { Text("📘 名单 JSON 格式") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    Text("推荐格式：", fontWeight = FontWeight.Bold)
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
                        Text(RosterImport.TEMPLATE, fontFamily = FontFamily.Monospace, fontSize = 11.sp, modifier = Modifier.padding(8.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("也支持：\n• 直接数组 [{\"学号\":\"01\",\"姓名\":\"张三\"}]\n• 映射 {\"202401\":\"张三\"}\n• 字段名可用 no/id/学号/编号、name/姓名、group/小组\n• CSV/TXT：每行“学号,姓名,小组”", fontSize = 13.sp)
                }
            },
            confirmButton = { TextButton(onClick = { saveTemplate.launch("名单模板.json"); showTemplate = false }) { Text("保存模板文件") } },
            dismissButton = { TextButton(onClick = { showTemplate = false }) { Text("关闭") } },
        )
    }
    if (paste) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { paste = false },
            title = { Text("📋 粘贴名单") },
            text = {
                Column {
                    Text("从 Excel 复制“学号 姓名”两列直接粘贴，或粘贴 JSON", fontSize = 12.sp)
                    OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth().height(220.dp), placeholder = { Text("202401\t张三\n202402\t李四") })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        val r = RosterImport.parse(text)
                        if (r.students.isEmpty()) kit.toast("没有解析到学生") else { importResult = r; paste = false }
                    } catch (e: Exception) {
                        kit.toast("解析失败：${e.message}")
                    }
                }) { Text("解析") }
            },
            dismissButton = { TextButton(onClick = { paste = false }) { Text("取消") } },
        )
    }
}

@Composable
fun StudentEditDialog(initial: Student?, onDismiss: () -> Unit, onDelete: (() -> Unit)? = null, onSave: (Student) -> Unit) {
    var no by remember { mutableStateOf(initial?.no ?: "") }
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var group by remember { mutableStateOf(initial?.group ?: "") }
    var gender by remember { mutableStateOf(initial?.gender ?: "") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var leave by remember { mutableStateOf(initial?.longLeave ?: false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "添加学生" else "编辑学生") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(no, { no = it.trim() }, label = { Text("学号") }, singleLine = true)
                OutlinedTextField(name, { name = it }, label = { Text("姓名") }, singleLine = true)
                OutlinedTextField(group, { group = it }, label = { Text("小组（可选）") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("性别")
                    listOf("男", "女", "").forEach { g ->
                        com.deepshawn.hwcounter.ui.components.Chip2(g.ifEmpty { "不填" }, gender == g, { gender = g })
                    }
                }
                OutlinedTextField(note, { note = it }, label = { Text("备注") }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("长期请假")
                        Text("新作业自动标记为请假", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(leave, { leave = it })
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                val base = initial ?: Student(no = no, name = name)
                onSave(base.copy(no = no, name = name.trim(), group = group.trim(), gender = gender, note = note.trim(), longLeave = leave))
            }) { Text("保存") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("删除", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}

/** 个人档案：积分、作业足迹、积分流水 */
@Composable
fun StudentDetailScreen(vm: AppViewModel, data: AppData, studentId: String) {
    val kit = LocalKit.current
    val c = vm.currentClass(data) ?: return
    val s = c.students.firstOrNull { it.id == studentId } ?: run {
        androidx.compose.runtime.LaunchedEffect(Unit) { kit.nav.pop() }
        return
    }
    val rules = data.settings.rules
    val stats = remember(c, rules) { c.studentStats(rules).sortedByDescending { it.score } }
    val me = stats.firstOrNull { it.student.id == s.id } ?: return
    val rank = stats.indexOfFirst { it.student.id == s.id } + 1
    var scoreDialog by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf(false) }
    val fmt = remember { SimpleDateFormat("yyyy年M月d日", Locale.CHINA) }
    val assigns = c.assignments.sortedByDescending { it.createdAt }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            GradientHeader {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { kit.nav.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = Color.White) }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { edit = true }) { Icon(Icons.Filled.Edit, "编辑", tint = Color.White) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(s.name, size = 72.dp, color = Color.White.copy(alpha = 0.25f))
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.name, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                        Text(listOf(s.no, s.group, s.gender).filter { it.isNotBlank() }.joinToString(" · "), color = Color.White.copy(alpha = 0.85f))
                        if (s.longLeave) Text("🏖️ 长期请假中", color = Color.White)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(me.score.fmt(), color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black)
                        Text("第 $rank 名", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("已交", "${me.submitted}", statusColor(HwStatus.SUBMITTED), Modifier.weight(1f))
                StatTile("迟交", "${me.late}", statusColor(HwStatus.LATE), Modifier.weight(1f))
                StatTile("未交", "${me.missing}", statusColor(HwStatus.MISSING), Modifier.weight(1f))
                StatTile("请假", "${me.counts[HwStatus.LEAVE] ?: 0}", statusColor(HwStatus.LEAVE), Modifier.weight(1f))
            }
        }
        item {
            SectionTitle("作业足迹（新→旧）", Modifier.padding(horizontal = 12.dp))
            FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                assigns.forEach { a ->
                    val st = a.statuses[s.id] ?: HwStatus.NONE
                    Box(
                        Modifier.size(30.dp).clip(MaterialTheme.shapes.small).background(statusColor(st).copy(alpha = if (st == HwStatus.NONE) 0.3f else 1f))
                            .clickable { kit.nav.push(Route.Assign(a.id)) },
                        contentAlignment = Alignment.Center,
                    ) { Text(st.short, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
        item {
            SectionTitle("积分流水", Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                FilledTonalButton(onClick = { scoreDialog = true }) { Text("加减分") }
            }
        }
        val entries = c.scores.filter { it.studentId == s.id }.sortedByDescending { it.time }
        val autoRows = assigns.mapNotNull { a ->
            val st = a.statuses[s.id] ?: HwStatus.NONE
            val d = rules.of(st)
            if (d == 0.0) null else Triple(a, st, d)
        }
        if (entries.isEmpty() && autoRows.isEmpty()) item { Text("暂无积分变化", Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(entries, key = { it.id }) { e ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(e.delta.signed(), fontWeight = FontWeight.Black, color = if (e.delta >= 0) statusColor(HwStatus.SUBMITTED) else statusColor(HwStatus.MISSING), modifier = Modifier.width(52.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.reason)
                    Text(fmt.format(Date(e.time)), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { vm.removeScore(e.id); kit.undoable("已撤回该条积分") }) { Icon(Icons.Filled.Close, "删除", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        items(autoRows, key = { "auto_" + it.first.id }) { (a, st, d) ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(d.signed(), fontWeight = FontWeight.Black, color = if (d >= 0) statusColor(HwStatus.SUBMITTED) else statusColor(HwStatus.MISSING), modifier = Modifier.width(52.dp))
                Column(Modifier.weight(1f)) {
                    Text("${a.subject}「${a.title}」${st.label}")
                    Text("自动计分 · ${fmt.format(Date(a.createdAt))}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    if (scoreDialog) ScoreDialog(listOf(s), data.settings.quickReasons, { scoreDialog = false }) { d, r -> vm.addScore(listOf(s.id), d, r); kit.undoable("${s.name} ${d.signed()}（$r）") }
    if (edit) StudentEditDialog(s, { edit = false }) { vm.upsertStudent(it); edit = false }
}

/** 加减分对话框：步进 + 快捷理由 */
@Composable
fun ScoreDialog(targets: List<Student>, reasons: List<String>, onDismiss: () -> Unit, onConfirm: (Double, String) -> Unit) {
    var value by remember { mutableStateOf(1.0) }
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (targets.size == 1) "给 ${targets[0].name} 加减分" else "给 ${targets.size} 人加减分") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { value -= 0.5 }) { Text("−") }
                    Text(
                        value.signed(), fontSize = 40.sp, fontWeight = FontWeight.Black,
                        color = if (value >= 0) statusColor(HwStatus.SUBMITTED) else statusColor(HwStatus.MISSING),
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                    OutlinedButton(onClick = { value += 0.5 }) { Text("+") }
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(-5.0, -2.0, -1.0, 1.0, 2.0, 5.0).forEach { v ->
                        com.deepshawn.hwcounter.ui.components.Chip2(v.signed(), value == v, { value = v }, Modifier.weight(1f))
                    }
                }
                OutlinedTextField(reason, { reason = it }, label = { Text("原因") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    reasons.forEach { r -> com.deepshawn.hwcounter.ui.components.Chip2(r, reason == r, { reason = r }) }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = value != 0.0, onClick = { onConfirm(value, reason.ifBlank { if (value > 0) "表现加分" else "扣分" }); onDismiss() }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

