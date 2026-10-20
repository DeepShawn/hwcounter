@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.deepshawn.hwcounter.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepshawn.hwcounter.data.AppData
import com.deepshawn.hwcounter.data.AppViewModel
import com.deepshawn.hwcounter.data.Assignment
import com.deepshawn.hwcounter.data.HwStatus
import com.deepshawn.hwcounter.data.SchoolClass
import com.deepshawn.hwcounter.data.studentStats
import com.deepshawn.hwcounter.data.summary
import com.deepshawn.hwcounter.ui.LocalKit
import com.deepshawn.hwcounter.ui.Route
import com.deepshawn.hwcounter.ui.components.AppCard
import com.deepshawn.hwcounter.ui.components.EmptyState
import com.deepshawn.hwcounter.ui.components.GradientHeader
import com.deepshawn.hwcounter.ui.components.ProgressRing
import com.deepshawn.hwcounter.ui.components.SectionTitle
import com.deepshawn.hwcounter.ui.components.StackedBar
import com.deepshawn.hwcounter.ui.components.StatTile
import com.deepshawn.hwcounter.ui.components.statusColor
import com.deepshawn.hwcounter.ui.rememberPhotoLauncher
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

fun subjectColor(subject: String): Color {
    val list = listOf(
        Color(0xFF4C6EF5), Color(0xFFE8590C), Color(0xFF0CA678), Color(0xFFAE3EC9),
        Color(0xFFD6336C), Color(0xFF1098AD), Color(0xFFF59F00), Color(0xFF5C940D), Color(0xFF7048E8),
    )
    return when (subject) {
        "语文" -> list[4]; "数学" -> list[0]; "英语" -> list[1]; "物理" -> list[5]; "化学" -> list[3]
        "生物" -> list[2]; "历史" -> list[6]; "地理" -> list[7]; "政治" -> list[8]
        else -> list[(subject.hashCode() and 0x7fffffff) % list.size]
    }
}

private fun greeting(): String {
    val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when (h) {
        in 5..10 -> "早上好，老师 ☀️"
        in 11..13 -> "中午好，老师 🍱"
        in 14..17 -> "下午好，老师 🌤️"
        in 18..22 -> "晚上好，老师 🌙"
        else -> "夜深了，老师注意休息 ✨"
    }
}

fun isToday(t: Long): Boolean {
    val a = Calendar.getInstance().apply { timeInMillis = t }
    val b = Calendar.getInstance()
    return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
}

fun SchoolClass.sortedAssignments(): List<Assignment> =
    assignments.sortedWith(compareByDescending<Assignment> { it.pinned }.thenByDescending { it.createdAt })

@Composable
fun HomeScreen(vm: AppViewModel, data: AppData) {
    val kit = LocalKit.current
    val c = vm.currentClass(data)
    var showNew by remember { mutableStateOf(false) }
    var classMenu by remember { mutableStateOf(false) }
    var classDialog by remember { mutableStateOf<SchoolClass?>(null) }
    var newClass by remember { mutableStateOf(false) }
    var random by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<String?>(null) }
    var editA by remember { mutableStateOf<Assignment?>(null) }
    var deleteA by remember { mutableStateOf<Assignment?>(null) }

    if (c == null) {
        WelcomeScreen(onNewClass = { newClass = true }, onImported = { r ->
            val n = vm.importRoster(r, replace = false, asNewClass = true)
            kit.toast("✅ 已创建「${r.className ?: "新班级"}」，共 $n 人")
        })
        if (newClass) ClassDialog(null, { newClass = false }) { n, e -> vm.addClass(n, e); newClass = false }
        return
    }

    val sorted = c.sortedAssignments()
    val hero = sorted.firstOrNull()
    val todayTarget = c.assignments.filter { isToday(it.createdAt) }.maxByOrNull { it.createdAt }

    fun ensureTarget(): String = todayTarget?.id ?: vm.createAssignment(
        SimpleDateFormat("M月d日", Locale.CHINA).format(Date()) + " 作业", data.settings.subjects.firstOrNull() ?: "",
    )

    val photo = rememberPhotoLauncher { uris -> kit.nav.push(Route.Recognize(ensureTarget(), uris)) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            GradientHeader {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(greeting(), color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyMedium)
                        Box {
                            Row(
                                Modifier.clip(CircleShape).clickable { classMenu = true }.padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("${c.emoji} ${c.name}", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Icon(Icons.Filled.ArrowDropDown, null, tint = Color.White)
                            }
                            DropdownMenu(classMenu, { classMenu = false }) {
                                data.classes.forEach { k ->
                                    DropdownMenuItem(
                                        text = { Text("${k.emoji} ${k.name}  (${k.students.size}人)", fontWeight = if (k.id == c.id) FontWeight.Bold else null) },
                                        onClick = { vm.switchClass(k.id); classMenu = false },
                                    )
                                }
                                HorizontalDivider()
                                DropdownMenuItem(text = { Text("✏️ 编辑当前班级") }, onClick = { classDialog = c; classMenu = false })
                                DropdownMenuItem(text = { Text("➕ 新建班级") }, onClick = { newClass = true; classMenu = false })
                            }
                        }
                        Text(
                            SimpleDateFormat("M月d日 EEEE", Locale.CHINA).format(Date()) + " · ${c.students.size} 名学生",
                            color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                if (hero != null) {
                    val sm = c.summary(hero)
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = Color.White.copy(alpha = 0.16f),
                        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).clickable { kit.nav.push(Route.Assign(hero.id)) },
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            ProgressRing(sm.rate, size = 96.dp, stroke = 10.dp) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("${(sm.rate * 100).toInt()}%", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
                                    Text("提交率", color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp)
                                }
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(if (isToday(hero.createdAt)) "今日作业" else "最近作业", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                                Text("${hero.subject} · ${hero.title}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    MiniStat("已交", sm.handedIn)
                                    MiniStat("未交", sm.c(HwStatus.MISSING) + sm.c(HwStatus.NONE))
                                    MiniStat("请假", sm.c(HwStatus.LEAVE))
                                }
                            }
                        }
                    }
                } else {
                    Text("还没有作业，点下面“新建作业”开始吧 👇", color = Color.White)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickAction("新建作业", Icons.Filled.Add, Color(0xFF4C6EF5), Modifier.weight(1f)) { showNew = true }
                QuickAction("拍照识别", Icons.Filled.CameraAlt, Color(0xFF12B886), Modifier.weight(1f)) { pendingAction = "photo" }
                QuickAction("实时扫描", Icons.Filled.DocumentScanner, Color(0xFFF76707), Modifier.weight(1f)) {
                    kit.nav.push(Route.Live(ensureTarget()))
                }
                QuickAction("数本数", Icons.Filled.Layers, Color(0xFFAE3EC9), Modifier.weight(1f)) {
                    kit.nav.push(Route.Stack(todayTarget?.id))
                }
            }
        }
        item {
            val stats = remember(c, data.settings.rules) { c.studentStats(data.settings.rules) }
            val recent = c.assignments.sortedByDescending { it.createdAt }.take(7)
            val avg = if (recent.isEmpty()) 0f else recent.map { c.summary(it).rate }.average().toFloat()
            val perfect = stats.count { it.missing == 0 && it.late == 0 && c.assignments.isNotEmpty() }
            val missTotal = stats.sumOf { it.missing }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("近7次提交率", "${(avg * 100).toInt()}%", MaterialTheme.colorScheme.primary, Modifier.weight(1f), Icons.Filled.AutoAwesome)
                StatTile("全勤之星", "$perfect", Color(0xFFFFA000), Modifier.weight(1f)) { kit.nav.tab(Route.Scores) }
                StatTile("累计未交", "$missTotal", Color(0xFFF0475A), Modifier.weight(1f)) { kit.nav.tab(Route.Stats) }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppCard(Modifier.weight(1f), onClick = { random = true }, padding = PaddingValues(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Casino, null, tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("随机点名", fontWeight = FontWeight.Bold)
                            Text("老虎机抽一位幸运儿", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                AppCard(Modifier.weight(1f), onClick = { kit.nav.tab(Route.Students) }, padding = PaddingValues(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🏷️", fontSize = 22.sp)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("学号二维码", fontWeight = FontWeight.Bold)
                            Text("打印贴纸，识别率≈100%", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        item {
            SectionTitle("全部作业 · ${c.assignments.size}", Modifier.padding(start = 12.dp, end = 12.dp, top = 16.dp))
        }
        if (sorted.isEmpty()) {
            item { EmptyState("📭", "还没有作业", "点击“新建作业”，或直接“拍照识别”自动创建今天的作业") }
        }
        items(sorted, key = { it.id }) { a ->
            AssignmentRow(
                c, a,
                onClick = { kit.nav.push(Route.Assign(a.id)) },
                onPin = { vm.editAssignment(a.id) { it.copy(pinned = !it.pinned) } },
                onEdit = { editA = a },
                onDelete = { deleteA = a },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp).animateItemPlacement(),
            )
        }
    }

    if (pendingAction == "photo") {
        PhotoSourceSheet(onDismiss = { pendingAction = null }, onCamera = { pendingAction = null; photo.takePhoto() }, onGallery = { pendingAction = null; photo.pickGallery() })
    }
    if (showNew) {
        NewAssignmentDialog(data.settings.subjects, onDismiss = { showNew = false }) { t, s ->
            val id = vm.createAssignment(t, s)
            showNew = false
            kit.nav.push(Route.Assign(id))
        }
    }
    editA?.let { a ->
        NewAssignmentDialog(data.settings.subjects, a.title, a.subject, "保存", onDismiss = { editA = null }) { t, s ->
            vm.editAssignment(a.id) { it.copy(title = t, subject = s) }; editA = null
        }
    }
    deleteA?.let { a ->
        ConfirmDialog("删除作业", "确定删除「${a.title}」？相关的扣分也会一起消失。", "删除", { deleteA = null }) {
            vm.deleteAssignment(a.id); kit.undoable("已删除「${a.title}」")
        }
    }
    if (newClass) ClassDialog(null, { newClass = false }) { n, e -> vm.addClass(n, e); newClass = false }
    classDialog?.let { k ->
        ClassDialog(k, { classDialog = null }) { n, e -> vm.renameClass(k.id, n, e); classDialog = null }
    }
    if (random) RandomPickDialog(c.students, onDismiss = { random = false }) { s -> vm.addScore(listOf(s.id), 1.0, "随机点名回答"); kit.toast("${s.name} +1") }
}

@Composable
private fun MiniStat(label: String, v: Int) {
    Column {
        Text("$v", color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
        Text(label, color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp)
    }
}

@Composable
fun QuickAction(label: String, icon: ImageVector, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val kit = LocalKit.current
    Column(
        modifier
            .clip(MaterialTheme.shapes.large)
            .background(color.copy(alpha = 0.12f))
            .clickable { kit.haptic(); onClick() }
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White)
        }
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
fun AssignmentRow(
    c: SchoolClass,
    a: Assignment,
    onClick: () -> Unit,
    onPin: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    val sm = c.summary(a)
    val sc = subjectColor(a.subject)
    val dateFmt = remember { SimpleDateFormat("M/d HH:mm", Locale.CHINA) }
    Box(modifier) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            tonalElevation = 1.dp,
            shadowElevation = 1.dp,
            modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).combinedClickable(onClick = onClick, onLongClick = { menu = true }),
        ) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(46.dp).clip(MaterialTheme.shapes.medium).background(sc.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                    Text(a.subject.take(1).ifEmpty { "作" }, color = sc, fontWeight = FontWeight.Black, fontSize = 20.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (a.pinned) Icon(Icons.Filled.PushPin, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(a.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
                        if (isToday(a.createdAt)) {
                            Spacer(Modifier.width(6.dp))
                            Text("今天", fontSize = 10.sp, color = Color.White, modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primary).padding(horizontal = 6.dp, vertical = 1.dp))
                        }
                    }
                    Text(
                        "${a.subject} · ${dateFmt.format(Date(a.createdAt))}" + (a.countedBooks?.let { " · 实数${it}本" } ?: ""),
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    StackedBar(
                        listOf(
                            statusColor(HwStatus.EXCELLENT) to sm.c(HwStatus.EXCELLENT),
                            statusColor(HwStatus.SUBMITTED) to sm.c(HwStatus.SUBMITTED),
                            statusColor(HwStatus.LATE) to sm.c(HwStatus.LATE),
                            statusColor(HwStatus.LEAVE) to sm.c(HwStatus.LEAVE),
                            statusColor(HwStatus.MISSING) to sm.c(HwStatus.MISSING),
                        ),
                        height = 6.dp,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text("${sm.handedIn}/${sm.expected}", fontWeight = FontWeight.Black, fontSize = 16.sp)
                    Text("${(sm.rate * 100).toInt()}%", fontSize = 12.sp, color = if (sm.rate >= 1f) statusColor(HwStatus.SUBMITTED) else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        DropdownMenu(menu, { menu = false }) {
            DropdownMenuItem(text = { Text(if (a.pinned) "取消置顶" else "置顶") }, leadingIcon = { Icon(Icons.Outlined.PushPin, null) }, onClick = { onPin(); menu = false })
            DropdownMenuItem(text = { Text("编辑") }, leadingIcon = { Icon(Icons.Filled.Edit, null) }, onClick = { onEdit(); menu = false })
            DropdownMenuItem(text = { Text("删除") }, leadingIcon = { Icon(Icons.Filled.Delete, null) }, onClick = { onDelete(); menu = false })
        }
    }
}

/** 首次使用：没有任何班级时的欢迎页 */
@Composable
private fun WelcomeScreen(onNewClass: () -> Unit, onImported: (com.deepshawn.hwcounter.data.RosterImport.Result) -> Unit) {
    val kit = LocalKit.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val openJson = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                val text = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
                }
                val r = com.deepshawn.hwcounter.data.RosterImport.parse(text)
                if (r.students.isEmpty()) kit.toast("没有解析到学生，请检查格式") else onImported(r)
            } catch (e: Exception) {
                kit.toast("解析失败：${e.message}")
            }
        }
    }
    Column(Modifier.fillMaxSize()) {
        GradientHeader {
            Spacer(Modifier.height(24.dp))
            Text("📚", fontSize = 48.sp)
            Text("欢迎使用作业通", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Text("拍一张照，就知道谁交了作业", color = Color.White.copy(alpha = 0.85f))
            Spacer(Modifier.height(12.dp))
        }
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("第一步：建立你的班级", style = MaterialTheme.typography.titleMedium)
            AppCard(onClick = { openJson.launch(arrayOf("application/json", "text/*", "*/*")) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("📥", fontSize = 30.sp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("导入名单文件", fontWeight = FontWeight.Bold)
                        Text("JSON / CSV，自动识别学号、姓名、小组，一步建好班级", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            AppCard(onClick = onNewClass) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("✏️", fontSize = 30.sp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("手动新建班级", fontWeight = FontWeight.Bold)
                        Text("之后可在“名单”页添加学生或粘贴 Excel 两列", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Text(
                "名单 JSON 示例：{\"className\":\"高一3班\",\"students\":[{\"no\":\"202401\",\"name\":\"张三\"}]}",
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
