@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)

package com.deepshawn.hwcounter.ui.screens

import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepshawn.hwcounter.data.AppData
import com.deepshawn.hwcounter.data.AppViewModel
import com.deepshawn.hwcounter.data.Assignment
import com.deepshawn.hwcounter.data.HwStatus
import com.deepshawn.hwcounter.data.SchoolClass
import com.deepshawn.hwcounter.data.Student
import com.deepshawn.hwcounter.data.signed
import com.deepshawn.hwcounter.data.fmt
import com.deepshawn.hwcounter.data.studentStats
import com.deepshawn.hwcounter.data.summary
import com.deepshawn.hwcounter.export.Exporter
import com.deepshawn.hwcounter.ui.LocalKit
import com.deepshawn.hwcounter.ui.Route
import com.deepshawn.hwcounter.ui.components.Avatar
import com.deepshawn.hwcounter.ui.components.GradientHeader
import com.deepshawn.hwcounter.ui.components.ProgressRing
import com.deepshawn.hwcounter.ui.components.StatusPill
import com.deepshawn.hwcounter.ui.components.statusColor
import com.deepshawn.hwcounter.ui.components.statusContainer
import com.deepshawn.hwcounter.ui.rememberPhotoLauncher
import com.deepshawn.hwcounter.ui.theme.LocalExtra
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/** 简单 TTS 封装 */
@Composable
fun rememberTts(): (String) -> Unit {
    val ctx = LocalContext.current
    val holder = remember { arrayOfNulls<TextToSpeech>(1) }
    DisposableEffect(Unit) {
        var tts: TextToSpeech? = null
        tts = TextToSpeech(ctx.applicationContext) { st ->
            if (st == TextToSpeech.SUCCESS) {
                tts?.language = Locale.CHINA
                tts?.setSpeechRate(0.95f)
            }
        }
        holder[0] = tts
        onDispose { tts?.stop(); tts?.shutdown() }
    }
    return { text -> holder[0]?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "hw") }
}

@Composable
fun PhotoSourceSheet(onDismiss: () -> Unit, onCamera: () -> Unit, onGallery: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            Text("📸 拍照识别作业", style = MaterialTheme.typography.titleLarge)
            Text(
                "小技巧：把作业本错开平铺或成摞展开，露出封面学号/姓名；也可以多拍几张一起识别，结果会自动去重合并。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickAction("拍照", Icons.Filled.CameraAlt, Color(0xFF12B886), Modifier.weight(1f), onCamera)
                QuickAction("相册多选", Icons.Filled.PhotoLibrary, Color(0xFF4C6EF5), Modifier.weight(1f), onGallery)
            }
        }
    }
}

private enum class Filter(val label: String, val match: (HwStatus) -> Boolean) {
    ALL("全部", { true }),
    TODO("未登记", { it == HwStatus.NONE }),
    MISSING("未交", { it == HwStatus.MISSING || it == HwStatus.NONE }),
    DONE("已交", { it == HwStatus.SUBMITTED || it == HwStatus.EXCELLENT || it == HwStatus.LATE }),
    LATE("迟交", { it == HwStatus.LATE }),
    LEAVE("请假", { it == HwStatus.LEAVE }),
    STAR("优秀", { it == HwStatus.EXCELLENT }),
}

@Composable
fun AssignmentScreen(vm: AppViewModel, data: AppData, assignmentId: String) {
    val kit = LocalKit.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = vm.currentClass(data)
    val a = c?.assignments?.firstOrNull { it.id == assignmentId }
    if (c == null || a == null) {
        LaunchedEffect(Unit) { kit.nav.pop() }
        return
    }
    val settings = data.settings
    val sm = c.summary(a)
    var filter by remember { mutableStateOf(Filter.ALL) }
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var batch by remember { mutableStateOf(false) }
    val selected = remember { mutableStateOf(setOf<String>()) }
    var sheetFor by remember { mutableStateOf<Student?>(null) }
    var menu by remember { mutableStateOf(false) }
    var photoSheet by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf(false) }
    var random by remember { mutableStateOf(false) }
    var confirmFill by remember { mutableStateOf(false) }
    val speak = rememberTts()
    val photo = rememberPhotoLauncher { uris -> kit.nav.push(Route.Recognize(a.id, uris)) }

    // 交齐时庆祝
    var lastRate by remember { mutableStateOf(sm.rate) }
    LaunchedEffect(sm.rate) {
        if (sm.rate >= 1f && lastRate < 1f && sm.expected > 0) {
            kit.celebrate(); kit.haptic(true); kit.toast("🎉 全员交齐！")
        }
        lastRate = sm.rate
    }

    val saveXlsx = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { uri ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { ctx.contentResolver.openOutputStream(uri)?.use { Exporter.writeXlsx(c, settings.rules, it) } }.isSuccess
            }
            kit.toast(if (ok) "✅ 已导出 Excel" else "导出失败")
        }
    }

    val extra = LocalExtra.current
    val students = c.students.filter { s ->
        val st = a.statuses[s.id] ?: HwStatus.NONE
        filter.match(st) && (query.isBlank() || s.name.contains(query) || s.no.contains(query))
    }

    fun tap(s: Student) {
        if (batch) {
            selected.value = if (s.id in selected.value) selected.value - s.id else selected.value + s.id
            kit.haptic()
            return
        }
        val cur = a.statuses[s.id] ?: HwStatus.NONE
        val next = if (cur == HwStatus.NONE || cur == HwStatus.MISSING) HwStatus.SUBMITTED else HwStatus.NONE
        vm.setStatus(a.id, listOf(s.id), next)
        kit.haptic()
    }

    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = when (settings.layout) {
                "list" -> GridCells.Fixed(1)
                "seat" -> GridCells.Adaptive(64.dp)
                else -> GridCells.Adaptive(if (extra.compact) 92.dp else 108.dp)
            },
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                GradientHeader {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { kit.nav.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = Color.White) }
                        Column(Modifier.weight(1f).clickable { edit = true }) {
                            Text(a.subject, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                            Text(a.title, color = Color.White, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = { searching = !searching }) { Icon(Icons.Filled.Search, "搜索", tint = Color.White) }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "更多", tint = Color.White) }
                            DropdownMenu(menu, { menu = false }) {
                                DropdownMenuItem(text = { Text("📊 导出 Excel（全班总表）") }, onClick = {
                                    menu = false; saveXlsx.launch("${c.name}_作业统计_${Exporter.fileStamp()}.xlsx")
                                })
                                DropdownMenuItem(text = { Text("📤 分享本次 Excel") }, onClick = {
                                    menu = false
                                    scope.launch {
                                        val f = withContext(Dispatchers.IO) {
                                            File(Exporter.exportDir(ctx), "${a.subject}_${a.title}_${Exporter.fileStamp()}.xlsx".replace("/", "_")).also { file ->
                                                file.outputStream().use { Exporter.writeXlsx(c, settings.rules, it, a) }
                                            }
                                        }
                                        Exporter.shareFile(ctx, f, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                                    }
                                })
                                DropdownMenuItem(text = { Text("📄 分享 CSV") }, onClick = {
                                    menu = false
                                    val f = File(Exporter.exportDir(ctx), "${a.title}_${Exporter.fileStamp()}.csv".replace("/", "_"))
                                    f.writeText(Exporter.csv(c, a, settings.rules))
                                    Exporter.shareFile(ctx, f, "text/csv")
                                })
                                DropdownMenuItem(text = { Text("🖼️ 生成战报海报") }, onClick = {
                                    menu = false
                                    scope.launch {
                                        val f = withContext(Dispatchers.Default) {
                                            val bmp = Exporter.poster(c, a, extra.gradient.first().toArgb(), extra.gradient.last().toArgb())
                                            File(Exporter.exportDir(ctx), "poster_${Exporter.fileStamp()}.png").also { Exporter.savePng(bmp, it) }
                                        }
                                        Exporter.shareFile(ctx, f, "image/png", "分享战报")
                                    }
                                })
                                DropdownMenuItem(text = { Text("💬 分享文字通报（发家长群）") }, onClick = {
                                    menu = false; Exporter.shareText(ctx, Exporter.summaryText(c, a))
                                })
                                HorizontalDivider()
                                DropdownMenuItem(text = { Text("🎲 从未交中随机抽") }, onClick = { menu = false; random = true })
                                DropdownMenuItem(text = { Text("✏️ 编辑作业信息") }, onClick = { menu = false; edit = true })
                                DropdownMenuItem(text = { Text("🧹 清空本次登记") }, onClick = {
                                    menu = false; vm.editAssignment(a.id) { it.copy(statuses = emptyMap()) }; kit.undoable("已清空")
                                })
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProgressRing(sm.rate, size = 104.dp, stroke = 11.dp) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${sm.handedIn}", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
                                Text("/ ${sm.expected}", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            HeaderStat("已交", sm.c(HwStatus.SUBMITTED) + sm.c(HwStatus.EXCELLENT))
                            HeaderStat("未交", sm.c(HwStatus.MISSING))
                            HeaderStat("未登记", sm.c(HwStatus.NONE))
                            HeaderStat("迟交", sm.c(HwStatus.LATE))
                            HeaderStat("请假", sm.c(HwStatus.LEAVE))
                            HeaderStat("优秀", sm.c(HwStatus.EXCELLENT))
                        }
                    }
                    a.countedBooks?.let { n ->
                        val diff = n - sm.handedIn
                        Spacer(Modifier.height(10.dp))
                        Row(
                            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(Color.White.copy(alpha = 0.18f)).padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(if (diff == 0) Icons.Filled.Check else Icons.Filled.Warning, null, tint = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                when {
                                    diff == 0 -> "实物 $n 本，与登记人数一致 👍"
                                    diff > 0 -> "实物 $n 本，比登记多 $diff 本：可能有人没被识别到"
                                    else -> "实物 $n 本，比登记少 ${-diff} 本：请核对是否误标"
                                },
                                color = Color.White, fontSize = 13.sp,
                            )
                        }
                    }
                }
            }
            // 动作栏
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ActionChip("拍照识别", Icons.Filled.CameraAlt) { photoSheet = true }
                    ActionChip("实时扫描", Icons.Filled.DocumentScanner) { kit.nav.push(Route.Live(a.id)) }
                    ActionChip("数本数", Icons.Filled.Layers) { kit.nav.push(Route.Stack(a.id)) }
                    ActionChip("剩余标未交", Icons.Filled.DoneAll) { confirmFill = true }
                    ActionChip("播报未交", Icons.AutoMirrored.Filled.VolumeUp) {
                        val miss = c.students.filter { (a.statuses[it.id] ?: HwStatus.NONE) in setOf(HwStatus.MISSING, HwStatus.NONE) }
                        speak(if (miss.isEmpty()) "全部同学都交齐了，真棒！" else "还有${miss.size}位同学没交作业：" + miss.joinToString("，") { it.name } + "。请尽快上交。")
                    }
                    ActionChip(if (batch) "退出批量" else "批量操作", Icons.Filled.Checklist) {
                        batch = !batch; selected.value = emptySet()
                    }
                }
            }
            if (searching) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    TextField(
                        query, { query = it },
                        placeholder = { Text("搜索姓名或学号") },
                        leadingIcon = { Icon(Icons.Filled.Search, null) },
                        trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, null) } },
                        singleLine = true,
                        shape = CircleShape,
                        colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    )
                }
            }
            // 过滤 + 布局
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Filter.entries.forEach { f ->
                            val n = c.students.count { f.match(a.statuses[it.id] ?: HwStatus.NONE) }
                            com.deepshawn.hwcounter.ui.components.Chip2("${f.label} $n", f == filter, { filter = f })
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (batch) "已选 ${selected.value.size} 人 · 点击选择" else "点击切换 已交/未登记 · 长按更多",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f),
                        )
                        LayoutToggle(settings.layout) { l -> vm.updateSettings { it.copy(layout = l) } }
                    }
                }
            }
            if (settings.layout == "group") {
                val groups = students.groupBy { it.group.ifBlank { "未分组" } }
                groups.forEach { (g, list) ->
                    item(span = { GridItemSpan(maxLineSpan) }, key = "g_$g") {
                        val done = list.count { (a.statuses[it.id] ?: HwStatus.NONE) in setOf(HwStatus.SUBMITTED, HwStatus.EXCELLENT, HwStatus.LATE) }
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(g, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(8.dp))
                            Text("$done/${list.size}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            if (done == list.size) Text("  🏅 全组交齐", fontSize = 12.sp)
                        }
                    }
                    items(list, key = { it.id }) { s ->
                        Box(Modifier.padding(horizontal = 4.dp)) {
                            StudentCard(s, a, settings.rules.of(a.statuses[s.id] ?: HwStatus.NONE), batch, s.id in selected.value, { tap(s) }, { sheetFor = s })
                        }
                    }
                }
            } else {
                items(students, key = { it.id }) { s ->
                    val st = a.statuses[s.id] ?: HwStatus.NONE
                    Box(Modifier.padding(horizontal = if (settings.layout == "list") 16.dp else 4.dp)) {
                        when (settings.layout) {
                            "list" -> StudentListRow(s, a, settings.rules.of(st), batch, s.id in selected.value, { tap(s) }, { sheetFor = s })
                            "seat" -> SeatCell(s, st, batch, s.id in selected.value, { tap(s) }, { sheetFor = s })
                            else -> StudentCard(s, a, settings.rules.of(st), batch, s.id in selected.value, { tap(s) }, { sheetFor = s })
                        }
                    }
                }
            }
            if (students.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    com.deepshawn.hwcounter.ui.components.EmptyState(if (c.students.isEmpty()) "👥" else "🔍", if (c.students.isEmpty()) "名单为空" else "没有符合条件的同学", if (c.students.isEmpty()) "去“名单”页导入 JSON 或手动添加" else "换个筛选条件试试")
                }
            }
        }

        // 批量操作栏
        AnimatedVisibility(
            visible = batch,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Surface(
                tonalElevation = 6.dp, shadowElevation = 12.dp,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.padding(12.dp).navigationBarsPadding(),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("批量 · ${selected.value.size} 人", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("全选", color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { selected.value = students.map { it.id }.toSet() }.padding(6.dp))
                        Text("清除", color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { selected.value = emptySet() }.padding(6.dp))
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        HwStatus.entries.forEach { st ->
                            StatusButton(st) {
                                if (selected.value.isEmpty()) kit.toast("先选择同学") else {
                                    vm.setStatus(a.id, selected.value, st)
                                    kit.undoable("已将 ${selected.value.size} 人标记为「${st.label}」")
                                    selected.value = emptySet()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    sheetFor?.let { s ->
        StudentSheet(vm, data, c, a, s) { sheetFor = null }
    }
    if (photoSheet) PhotoSourceSheet({ photoSheet = false }, { photoSheet = false; photo.takePhoto() }, { photoSheet = false; photo.pickGallery() })
    if (edit) {
        NewAssignmentDialog(settings.subjects, a.title, a.subject, "保存", onDismiss = { edit = false }) { t, sub ->
            vm.editAssignment(a.id) { it.copy(title = t, subject = sub) }; edit = false
        }
    }
    if (confirmFill) {
        ConfirmDialog("剩余标记为未交", "将 ${sm.c(HwStatus.NONE)} 名“未登记”的同学标记为「未交」，并按规则扣分（${settings.rules.missing.signed()} 分/人）。", "确定", { confirmFill = false }) {
            vm.fillRemaining(a.id, HwStatus.MISSING)
            kit.undoable("已标记 ${sm.c(HwStatus.NONE)} 人未交")
        }
    }
    if (random) {
        val pool = c.students.filter { (a.statuses[it.id] ?: HwStatus.NONE) in setOf(HwStatus.MISSING, HwStatus.NONE) }
        RandomPickDialog(pool, "🎯 抽一位未交的同学", onDismiss = { random = false })
    }
}

@Composable
private fun HeaderStat(label: String, n: Int) {
    Box(Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.18f)).padding(horizontal = 10.dp, vertical = 5.dp)) {
        Text("$label $n", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ActionChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val kit = LocalKit.current
    AssistChip(
        onClick = { kit.haptic(); onClick() },
        label = { Text(label) },
        leadingIcon = { Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary) },
        shape = CircleShape,
        colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    )
}

@Composable
fun LayoutToggle(layout: String, onChange: (String) -> Unit) {
    Row(Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).padding(2.dp)) {
        listOf(
            "grid" to Icons.Filled.ViewModule,
            "list" to Icons.Filled.ViewAgenda,
            "seat" to Icons.Filled.GridView,
            "group" to Icons.Filled.Groups,
        ).forEach { (k, ic) ->
            val sel = k == layout
            Box(
                Modifier.size(34.dp).clip(CircleShape)
                    .background(if (sel) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { onChange(k) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(ic, k, Modifier.size(18.dp), tint = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun StatusButton(st: HwStatus, selected: Boolean = false, onClick: () -> Unit) {
    val col = statusColor(st)
    Column(
        Modifier.clip(MaterialTheme.shapes.medium)
            .background(if (selected) col else statusContainer(st))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(st.emoji, fontSize = 18.sp)
        Text(st.label, fontSize = 12.sp, color = if (selected) Color.White else col, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StudentCard(s: Student, a: Assignment, delta: Double, batch: Boolean, sel: Boolean, onTap: () -> Unit, onLong: () -> Unit) {
    val st = a.statuses[s.id] ?: HwStatus.NONE
    val col = statusColor(st)
    val bg by animateColorAsState(if (st == HwStatus.NONE) MaterialTheme.colorScheme.surfaceContainerLowest else statusContainer(st), label = "bg")
    val scale by animateFloatAsState(if (sel) 0.94f else 1f, spring(dampingRatio = 0.5f), label = "sc")
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = bg,
        border = if (sel) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else if (st == HwStatus.NONE) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
        modifier = Modifier.fillMaxWidth().scale(scale).clip(MaterialTheme.shapes.medium).combinedClickable(onClick = onTap, onLongClick = onLong),
    ) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box {
                Avatar(s.name, size = 42.dp)
                if (st != HwStatus.NONE) {
                    Text(st.emoji, fontSize = 13.sp, modifier = Modifier.align(Alignment.BottomEnd))
                }
                if (batch && sel) {
                    Box(Modifier.size(18.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary).align(Alignment.TopEnd), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Check, null, Modifier.size(12.dp), tint = Color.White)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(s.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(s.no, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(st.label, color = col, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                if (delta != 0.0) Text(" ${delta.signed()}", color = if (delta > 0) statusColor(HwStatus.SUBMITTED) else statusColor(HwStatus.MISSING), fontSize = 11.sp, fontWeight = FontWeight.Black)
            }
            if (a.comments[s.id] != null) Text("💬 " + a.comments[s.id], fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StudentListRow(s: Student, a: Assignment, delta: Double, batch: Boolean, sel: Boolean, onTap: () -> Unit, onLong: () -> Unit) {
    val st = a.statuses[s.id] ?: HwStatus.NONE
    val bg by animateColorAsState(if (sel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest, label = "bg")
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(bg)
            .combinedClickable(onClick = onTap, onLongClick = onLong).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(width = 4.dp, height = 36.dp).clip(CircleShape).background(statusColor(st)))
        Spacer(Modifier.width(10.dp))
        Avatar(s.name, size = 38.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(s.name, fontWeight = FontWeight.Bold)
            Text(
                listOf(s.no, s.group, a.comments[s.id]?.let { "💬$it" } ?: "").filter { it.isNotBlank() }.joinToString(" · "),
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (delta != 0.0) {
            Text(delta.signed(), color = if (delta > 0) statusColor(HwStatus.SUBMITTED) else statusColor(HwStatus.MISSING), fontWeight = FontWeight.Black)
            Spacer(Modifier.width(8.dp))
        }
        if (batch) {
            Icon(if (sel) Icons.Filled.Check else Icons.Filled.Close, null, tint = if (sel) MaterialTheme.colorScheme.primary else Color.Transparent)
        } else StatusPill(st)
    }
}

@Composable
private fun SeatCell(s: Student, st: HwStatus, batch: Boolean, sel: Boolean, onTap: () -> Unit, onLong: () -> Unit) {
    val col = statusColor(st)
    val bg by animateColorAsState(if (st == HwStatus.NONE) MaterialTheme.colorScheme.surfaceContainerLowest else col, label = "bg")
    Box(
        Modifier.aspectRatio(1f).clip(MaterialTheme.shapes.small).background(bg)
            .then(if (sel) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small) else Modifier)
            .combinedClickable(onClick = onTap, onLongClick = onLong),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(s.no.takeLast(2), fontSize = 18.sp, fontWeight = FontWeight.Black, color = if (st == HwStatus.NONE) MaterialTheme.colorScheme.onSurface else Color.White)
            Text(s.name, fontSize = 10.sp, maxLines = 1, color = if (st == HwStatus.NONE) MaterialTheme.colorScheme.onSurfaceVariant else Color.White.copy(alpha = 0.9f), textAlign = TextAlign.Center)
        }
        if (batch && sel) Icon(Icons.Filled.Check, null, Modifier.align(Alignment.TopEnd).size(14.dp), tint = MaterialTheme.colorScheme.primary)
    }
}

/** 长按学生的详细面板：状态 / 加减分 / 备注 */
@Composable
fun StudentSheet(vm: AppViewModel, data: AppData, c: SchoolClass, a: Assignment, s: Student, onDismiss: () -> Unit) {
    val kit = LocalKit.current
    val st = a.statuses[s.id] ?: HwStatus.NONE
    var comment by remember(s.id) { mutableStateOf(a.comments[s.id] ?: "") }
    val total = remember(c, data.settings.rules) {
        c.studentStats(data.settings.rules).firstOrNull { it.student.id == s.id }?.score
    }
    ModalBottomSheet(onDismissRequest = {
        if (comment != (a.comments[s.id] ?: "")) vm.setComment(a.id, s.id, comment)
        onDismiss()
    }) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(s.name, size = 56.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.name, style = MaterialTheme.typography.titleLarge)
                    Text("${s.no}  ${s.group}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(total?.fmt() ?: "-", fontSize = 26.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                    Text("当前积分", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("本次作业状态", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HwStatus.entries.forEach { h ->
                    StatusButton(h, h == st) { vm.setStatus(a.id, listOf(s.id), h); kit.haptic() }
                }
            }
            Text(
                "计分：${HwStatus.entries.filter { it != HwStatus.NONE }.joinToString("  ") { it.label + data.settings.rules.of(it).signed() }}",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text("额外加减分", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(-2.0, -1.0, 1.0, 2.0, 5.0).forEach { d ->
                    OutlinedButton(
                        onClick = { vm.addScore(listOf(s.id), d, "作业：${a.title}"); kit.haptic(); kit.undoable("${s.name} ${d.signed()} 分") },
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        modifier = Modifier.weight(1f),
                    ) { Text(d.signed(), color = if (d > 0) statusColor(HwStatus.SUBMITTED) else statusColor(HwStatus.MISSING), fontWeight = FontWeight.Black) }
                }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(comment, { comment = it }, label = { Text("备注（如：字迹潦草、缺第3页）") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = {
                    vm.upsertStudent(s.copy(longLeave = !s.longLeave))
                    if (!s.longLeave) vm.setStatus(a.id, listOf(s.id), HwStatus.LEAVE)
                    kit.toast(if (s.longLeave) "已取消长期请假" else "已设为长期请假，之后新作业自动标请假")
                    onDismiss()
                }, modifier = Modifier.weight(1f)) { Text(if (s.longLeave) "取消长期请假" else "🏖️ 设为长期请假") }
                FilledTonalButton(onClick = { onDismiss(); kit.nav.push(Route.StudentDetail(s.id)) }, modifier = Modifier.weight(1f)) { Text("📇 个人档案") }
            }
        }
    }
}
