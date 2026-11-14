@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.deepshawn.hwcounter.ui.screens

import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.foundation.lazy.itemsIndexed
import com.deepshawn.hwcounter.ui.components.listCell
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.filled.Drafts
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.material.icons.filled.Mic
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
        in 5..10 -> "早上好"
        in 11..13 -> "中午好"
        in 14..17 -> "下午好"
        in 18..22 -> "晚上好"
        else -> "夜深了，注意休息"
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
    val drafts by vm.drafts.collectAsStateWithLifecycle()

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

    // 快捷登记：有今天的作业就直接用；没有就先弹出“新建作业”让老师确认，不再自动添加作业
    var afterCreate by remember { mutableStateOf<((String) -> Unit)?>(null) }
    var photoTarget by remember { mutableStateOf<String?>(null) }
    fun withTarget(go: (String) -> Unit) {
        if (vm.readOnly.value) { kit.toast("👀 预览模式：登录后才能使用"); return }
        val t = todayTarget
        if (t != null) go(t.id) else { afterCreate = go; showNew = true }
    }

    val photo = rememberPhotoLauncher { uris -> photoTarget?.let { kit.nav.push(Route.Recognize(it, uris)) } }

    var classSheet by remember { mutableStateOf(false) }
    var rowMenu by remember { mutableStateOf<Assignment?>(null) }
    val colors = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
    val stats = remember(c, data.settings.rules) { c.studentStats(data.settings.rules) }
    com.deepshawn.hwcounter.ui.components.MiuixPage(
        title = c.name,
        actions = {
            top.yukonga.miuix.kmp.basic.IconButton(onClick = { classSheet = true }, modifier = Modifier.padding(end = 16.dp)) {
                top.yukonga.miuix.kmp.basic.Icon(Icons.Filled.SwapHoriz, "切换班级", tint = colors.onSurface)
            }
        },
    ) {
        item(key = "sub") {
            top.yukonga.miuix.kmp.basic.Text(
                greeting() + " · " + SimpleDateFormat("M月d日 EEEE", Locale.CHINA).format(Date()) + " · ${c.students.size} 名学生",
                fontSize = 13.sp, color = colors.onSurfaceVariantSummary, modifier = Modifier.padding(start = 28.dp, end = 28.dp, bottom = 10.dp),
            )
        }
        item(key = "today") {
            top.yukonga.miuix.kmp.basic.Card(
                Modifier.padding(horizontal = 12.dp).fillMaxWidth(),
                onClick = { if (hero != null) kit.nav.push(Route.Assign(hero.id)) else showNew = true },
            ) {
                if (hero != null) {
                    val sm = c.summary(hero)
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        ProgressRing(sm.rate, size = 72.dp, stroke = 7.dp, track = colors.secondaryContainer, colors = listOf(colors.primary, colors.primary)) {
                            top.yukonga.miuix.kmp.basic.Text("${(sm.rate * 100).toInt()}%", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            top.yukonga.miuix.kmp.basic.Text(if (isToday(hero.createdAt)) "今日作业" else "最近作业", fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
                            top.yukonga.miuix.kmp.basic.Text("${hero.subject} · ${hero.title}".trimStart(' ', '·'), fontWeight = FontWeight.SemiBold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                CountDot(statusColor(HwStatus.SUBMITTED), "已交", sm.handedIn)
                                CountDot(statusColor(HwStatus.MISSING), "未交", sm.c(HwStatus.MISSING) + sm.c(HwStatus.NONE))
                                CountDot(statusColor(HwStatus.LEAVE), "请假", sm.c(HwStatus.LEAVE))
                            }
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = colors.onSurfaceVariantActions)
                    }
                } else Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    top.yukonga.miuix.kmp.basic.Text("还没有作业，点这里新建第一次作业", Modifier.weight(1f), color = colors.onSurfaceVariantSummary)
                    Icon(Icons.Filled.Add, null, tint = colors.primary)
                }
            }
        }
        item(key = "actions") {
            top.yukonga.miuix.kmp.basic.SmallTitle("快捷登记")
            top.yukonga.miuix.kmp.basic.Card(Modifier.padding(horizontal = 12.dp).fillMaxWidth()) {
                val actions: List<Triple<String, ImageVector, Pair<Color, () -> Unit>>> = listOf(
                    Triple("新建作业", Icons.Filled.Add, Color(0xFF3482FF) to { showNew = true }),
                    Triple("拍照识别", Icons.Filled.CameraAlt, Color(0xFF12B886) to { withTarget { id -> photoTarget = id; pendingAction = "photo" } }),
                    Triple("语音登记", Icons.Filled.Mic, Color(0xFF1098AD) to { withTarget { kit.nav.push(Route.Voice(it)) } }),
                    Triple("实时扫描", Icons.Filled.DocumentScanner, Color(0xFFF76707) to { withTarget { kit.nav.push(Route.Live(it)) } }),
                    Triple("侧面学号", Icons.Filled.ViewWeek, Color(0xFFE64980) to { withTarget { kit.nav.push(Route.Spine(it)) } }),
                    Triple("数本数", Icons.Filled.Layers, Color(0xFFAE3EC9) to { kit.nav.push(Route.Stack(todayTarget?.id)) }),
                    Triple(if (drafts.isNotEmpty()) "草稿 ${drafts.size}" else "草稿箱", Icons.Filled.Drafts, Color(0xFF7A5CFF) to { kit.nav.push(Route.Drafts) }),
                    Triple("统计表识别", Icons.Filled.TableChart, Color(0xFF0CA678) to { kit.nav.push(Route.StatTable(null)) }),
                )
                Column(Modifier.padding(vertical = 10.dp)) {
                    actions.chunked(4).forEach { row ->
                        Row(Modifier.fillMaxWidth()) {
                            row.forEach { (label, icon, pair) ->
                                Column(
                                    Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).clickable { kit.haptic(); pair.second() }.padding(vertical = 10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Box(Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(pair.first.copy(alpha = if (com.deepshawn.hwcounter.ui.theme.LocalExtra.current.isDark) 0.22f else 0.12f)), contentAlignment = Alignment.Center) {
                                        Icon(icon, null, tint = pair.first, modifier = Modifier.size(24.dp))
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    top.yukonga.miuix.kmp.basic.Text(label, fontSize = 12.sp, maxLines = 1, color = colors.onSurface)
                                }
                            }
                        }
                    }
                }
            }
        }
        item(key = "overview") {
            val recent = c.assignments.sortedByDescending { it.createdAt }.take(7)
            val avg = if (recent.isEmpty()) 0f else recent.map { c.summary(it).rate }.average().toFloat()
            val perfect = stats.count { it.missing == 0 && it.late == 0 && c.assignments.isNotEmpty() }
            val missTotal = stats.sumOf { it.missing }
            top.yukonga.miuix.kmp.basic.SmallTitle("概览")
            top.yukonga.miuix.kmp.basic.Card(Modifier.padding(horizontal = 12.dp).fillMaxWidth()) {
                Row(Modifier.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Overview("近 7 次交齐率", "${(avg * 100).toInt()}%", Modifier.weight(1f)) { kit.nav.tab(Route.Stats) }
                    Box(Modifier.width(0.5.dp).height(32.dp).background(colors.dividerLine))
                    Overview("全勤之星", "$perfect 人", Modifier.weight(1f)) { kit.nav.tab(Route.Stats) }
                    Box(Modifier.width(0.5.dp).height(32.dp).background(colors.dividerLine))
                    Overview("累计未交", "$missTotal 次", Modifier.weight(1f)) { kit.nav.tab(Route.Scores) }
                }
            }
        }
        item(key = "tools") {
            top.yukonga.miuix.kmp.basic.SmallTitle("工具")
            top.yukonga.miuix.kmp.basic.Card(Modifier.padding(horizontal = 12.dp).fillMaxWidth()) {
                top.yukonga.miuix.kmp.extra.SuperArrow(title = "随机点名", summary = "老虎机抽一位同学，可直接加分", onClick = { random = true })
                top.yukonga.miuix.kmp.extra.SuperArrow(title = "学号二维码贴纸", summary = "在名单页生成 A4 贴纸，识别率接近 100%", onClick = { kit.nav.tab(Route.Students) })
            }
        }
        item(key = "allTitle") { top.yukonga.miuix.kmp.basic.SmallTitle("全部作业 · ${c.assignments.size}") }
        if (sorted.isEmpty()) {
            item { EmptyState("📭", "还没有作业", "点击“新建作业”开始；用拍照、语音等快捷登记时也会先让你确认新建") }
        }
        itemsIndexed(sorted, key = { _, a -> a.id }) { i, a ->
            CompactAssignmentRow(c, a, Modifier.listCell(i, sorted.size), onClick = { kit.nav.push(Route.Assign(a.id)) }, onLong = { rowMenu = a })
        }
    }

    com.deepshawn.hwcounter.ui.components.ActionSheet(
        show = classSheet, title = "班级", onDismiss = { classSheet = false },
        actions = data.classes.map { k ->
            com.deepshawn.hwcounter.ui.components.SheetAction("${k.emoji} ${k.name}" + if (k.id == c.id) "（当前）" else "", "${k.students.size} 人 · ${k.assignments.size} 次作业") { vm.switchClass(k.id) }
        } + listOf(
            com.deepshawn.hwcounter.ui.components.SheetAction("编辑当前班级") { classDialog = c },
            com.deepshawn.hwcounter.ui.components.SheetAction("新建班级") { newClass = true },
        ),
    )
    rowMenu?.let { a ->
        com.deepshawn.hwcounter.ui.components.ActionSheet(
            show = true, title = a.title, onDismiss = { rowMenu = null },
            actions = listOf(
                com.deepshawn.hwcounter.ui.components.SheetAction(if (a.pinned) "取消置顶" else "置顶") { vm.editAssignment(a.id) { it.copy(pinned = !it.pinned) } },
                com.deepshawn.hwcounter.ui.components.SheetAction("编辑名称和科目") { editA = a },
                com.deepshawn.hwcounter.ui.components.SheetAction("删除", danger = true) { deleteA = a },
            ),
        )
    }

    if (pendingAction == "photo") {
        PhotoSourceSheet(onDismiss = { pendingAction = null }, onCamera = { pendingAction = null; photo.takePhoto() }, onGallery = { pendingAction = null; photo.pickGallery() })
    }
    if (showNew) {
        NewAssignmentDialog(
            data.settings.subjects,
            onDraft = if (afterCreate == null) { t, s ->
                showNew = false
                val d = com.deepshawn.hwcounter.recog.StatTable.newDraft(c, null, t, s)
                vm.addDraft(d); kit.toast("📝 已建草稿：只保存在本机，登记完再上传"); kit.nav.push(Route.Draft(d.id))
            } else null,
            onDismiss = { showNew = false; afterCreate = null },
        ) { t, s ->
            val id = vm.createAssignment(t, s)
            showNew = false
            val next = afterCreate
            afterCreate = null
            if (next != null) next(id) else kit.nav.push(Route.Assign(id))
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
    val dateFmt = remember { SimpleDateFormat("yyyy年M月d日", Locale.CHINA) }
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

@Composable
private fun CountDot(color: Color, label: String, n: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(5.dp))
        top.yukonga.miuix.kmp.basic.Text("$label $n", fontSize = 13.sp, color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onSurfaceSecondary)
    }
}

@Composable
private fun Overview(label: String, value: String, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        top.yukonga.miuix.kmp.basic.Text(value, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        top.yukonga.miuix.kmp.basic.Text(label, fontSize = 12.sp, color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}

/** 紧凑的作业行：科目徽标 · 标题/日期 · 状态细条 · 交齐数 */
@Composable
private fun CompactAssignmentRow(c: SchoolClass, a: Assignment, modifier: Modifier, onClick: () -> Unit, onLong: () -> Unit) {
    val colors = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
    val sm = c.summary(a)
    val sc = subjectColor(a.subject)
    val dateFmt = remember { SimpleDateFormat("M月d日", Locale.CHINA) }
    Row(
        modifier.combinedClickable(onClick = onClick, onLongClick = onLong).padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(sc.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            top.yukonga.miuix.kmp.basic.Text(a.subject.take(1).ifEmpty { "作" }, color = sc, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (a.pinned) Icon(Icons.Filled.PushPin, null, Modifier.size(13.dp).padding(end = 2.dp), tint = colors.primary)
                top.yukonga.miuix.kmp.basic.Text(a.title, fontWeight = FontWeight.Medium, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
                if (isToday(a.createdAt)) {
                    Spacer(Modifier.width(6.dp))
                    top.yukonga.miuix.kmp.basic.Text("今天", fontSize = 10.sp, color = colors.primary, modifier = Modifier.clip(CircleShape).background(colors.primary.copy(alpha = 0.12f)).padding(horizontal = 6.dp, vertical = 1.dp))
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                top.yukonga.miuix.kmp.basic.Text(
                    listOf(a.subject, dateFmt.format(Date(a.createdAt))).filter { it.isNotBlank() }.joinToString(" · ") + (a.countedBooks?.let { " · 实数${it}本" } ?: ""),
                    fontSize = 12.sp, color = colors.onSurfaceVariantSummary, maxLines = 1,
                )
                Spacer(Modifier.width(10.dp))
                StackedBar(
                    com.deepshawn.hwcounter.ui.components.ChartStatusOrder.filter { it != HwStatus.NONE }.map { statusColor(it) to sm.c(it) },
                    Modifier.weight(1f), height = 4.dp,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            top.yukonga.miuix.kmp.basic.Text("${sm.handedIn}/${sm.expected}", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            top.yukonga.miuix.kmp.basic.Text("${(sm.rate * 100).toInt()}%", fontSize = 12.sp, color = if (sm.rate >= 1f) statusColor(HwStatus.SUBMITTED) else colors.onSurfaceVariantSummary)
        }
    }
}
