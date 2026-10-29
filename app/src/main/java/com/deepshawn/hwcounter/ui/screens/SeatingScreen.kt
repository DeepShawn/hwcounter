@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.deepshawn.hwcounter.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepshawn.hwcounter.data.AppData
import com.deepshawn.hwcounter.data.AppViewModel
import com.deepshawn.hwcounter.data.Seating
import com.deepshawn.hwcounter.data.Student
import com.deepshawn.hwcounter.recog.ImageUtils
import com.deepshawn.hwcounter.recog.SeatingLogic
import com.deepshawn.hwcounter.recog.SeatingOcr
import com.deepshawn.hwcounter.ui.LocalKit
import com.deepshawn.hwcounter.ui.components.AppCard
import com.deepshawn.hwcounter.ui.components.EmptyState
import com.deepshawn.hwcounter.ui.components.GradientHeader
import com.deepshawn.hwcounter.ui.rememberPhotoLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/** 一个座位格子显示什么 */
class SeatCellUi(val bg: Color, val border: Color, val content: @Composable () -> Unit)

/**
 * 座位表格子图：讲台位置、排号（从讲台数起）、列号。编辑座位表和按座位登记共用。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SeatChart(
    seating: Seating,
    students: List<Student>,
    cell: @Composable (Student?, Int) -> SeatCellUi,
    selected: (Int, Student?) -> Boolean,
    onCell: (Int, Student?) -> Unit,
    onLong: (Student) -> Unit = {},
    onRow: ((Int) -> Unit)? = null,
    onCol: ((Int) -> Unit)? = null,
) {
    val byId = remember(students) { students.associateBy { it.id } }
    val grid = remember(seating, students) { SeatingLogic.toGrid(seating, students) }
    val front: @Composable () -> Unit = {
        Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
            Text(
                "讲  台", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp)).padding(horizontal = 48.dp, vertical = 6.dp),
            )
        }
    }
    val cw = 64.dp
    Column(Modifier.fillMaxWidth()) {
        if (seating.frontAtTop) front()
        Column(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(34.dp))
                for (c in 0 until seating.cols) {
                    Text(
                        "${c + 1}列", fontSize = 11.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(cw).padding(2.dp).then(if (onCol != null) Modifier.clickable { onCol(c) } else Modifier),
                    )
                }
            }
            grid.forEachIndexed { r, row ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${if (seating.frontAtTop) r + 1 else seating.rows - r}排", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(34.dp).then(if (onRow != null) Modifier.clickable { onRow(r) } else Modifier),
                    )
                    row.forEachIndexed { c, id ->
                        val k = r * seating.cols + c
                        val st = id?.let { byId[it] }
                        val ui = cell(st, k)
                        val sel = selected(k, st)
                        Box(
                            Modifier.padding(2.dp).width(cw - 4.dp).height(56.dp)
                                .background(ui.bg, RoundedCornerShape(12.dp))
                                .border(if (sel) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, ui.border), RoundedCornerShape(12.dp))
                                .combinedClickable(onClick = { onCell(k, st) }, onLongClick = { if (st != null) onLong(st) }),
                            contentAlignment = Alignment.Center,
                        ) { ui.content() }
                    }
                }
            }
        }
        if (!seating.frontAtTop) front()
    }
}

/** 解析 JSON 座位表：二维数组，或 {seats:[[...]], frontAtTop} */
private fun parseSeatingJson(text: String, students: List<Student>): SeatingLogic.Result {
    val root = Json.parseToJsonElement(text)
    val arr = when (root) {
        is JsonArray -> root
        is JsonObject -> listOf("seats", "rows", "座位表", "座位", "grid").firstNotNullOfOrNull { root[it] as? JsonArray }
        else -> null
    } ?: throw IllegalArgumentException("JSON 里没有找到座位二维数组（seats）")
    val rows = arr.map { r ->
        when (r) {
            is JsonArray -> r.map { x -> if (x is JsonNull) "" else (x as? JsonPrimitive)?.contentOrNull ?: "" }
            is JsonPrimitive -> (r.contentOrNull ?: "").split(Regex("[\t,，;；|\\s]+"))
            else -> emptyList()
        }
    }
    val front = ((root as? JsonObject)?.get("frontAtTop") as? JsonPrimitive)?.booleanOrNull ?: true
    return SeatingLogic.parseRows(rows, front, students)
}

fun parseSeatingAny(text: String, students: List<Student>): SeatingLogic.Result {
    val t = text.removePrefix("﻿").trim()
    return if (t.startsWith("[") || t.startsWith("{")) parseSeatingJson(t, students) else SeatingLogic.parseText(t, students)
}

@Composable
fun SeatingScreen(vm: AppViewModel, data: AppData) {
    val kit = LocalKit.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = vm.currentClass(data)
    var sel by remember { mutableStateOf<Int?>(null) }
    var pick by remember { mutableStateOf<String?>(null) }
    var paste by remember { mutableStateOf(false) }
    var auto by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<String?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    fun apply(r: SeatingLogic.Result, how: String) {
        val n = r.seating.seats.size
        if (n == 0) { kit.toast("没有认出名单里的学生，请检查内容"); return }
        vm.setSeating { r.seating }
        kit.undoable("已按${how}排好 $n 人")
        report = "已入座 $n 人" + (if (r.unseated.isNotEmpty()) "，未入座 ${r.unseated.size} 人（点下方名字再点座位即可入座）" else "") +
            (if (r.unmatched.isNotEmpty()) "；没认出：" + r.unmatched.take(12).joinToString("、") else "")
        sel = null
    }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val cc = c ?: return@rememberLauncherForActivityResult
        if (uri != null) scope.launch {
            runCatching {
                val text = withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: "" }
                parseSeatingAny(text, cc.students)
            }.onSuccess { apply(it, "座位表") }.onFailure { kit.toast("解析失败：${it.message}") }
        }
    }
    val photo = rememberPhotoLauncher { uris ->
        val cc = c ?: return@rememberPhotoLauncher
        val uri = uris.firstOrNull() ?: return@rememberPhotoLauncher
        busy = true
        scope.launch {
            runCatching {
                val bmp = withContext(Dispatchers.IO) { ImageUtils.load(ctx, uri, 2400) } ?: error("图片无法打开")
                val pieces = SeatingOcr.recognize(bmp)
                withContext(Dispatchers.Default) { SeatingLogic.fromBoxes(pieces, cc.students) }
            }.onSuccess { apply(it, "照片") }.onFailure { kit.toast("识别失败：${it.message}") }
            busy = false
        }
    }
    var photoSheet by remember { mutableStateOf(false) }

    if (c == null) { EmptyState("🏫", "还没有班级", "去首页新建班级"); return }
    val seating = c.seating
    val unseated = c.students.filter { seating == null || it.id !in seating.seats }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            GradientHeader {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { kit.nav.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = Color.White) }
                    Column(Modifier.weight(1f)) {
                        Text("🪑 座位表", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                        Text("${c.emoji} ${c.name} · ${seating?.seats?.size ?: 0}/${c.students.size} 人已入座", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
                    }
                }
            }
        }
        item {
            FlowRow(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip({ photoSheet = true }, { Text("📷 照片识别") })
                AssistChip({ paste = true }, { Text("📋 粘贴座位表") })
                AssistChip({ openFile.launch(arrayOf("text/*", "application/json", "*/*")) }, { Text("📄 从文件导入") })
                AssistChip({ auto = true }, { Text("⚡ 按学号自动排列") })
                if (seating != null) {
                    AssistChip({ vm.setSeating { it?.let(SeatingLogic::rotate) }; kit.undoable("讲台已换边") }, { Text("🔄 讲台换边") })
                    AssistChip({ confirmClear = true }, { Text("🗑️ 清空") })
                }
            }
        }
        if (busy) item {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); Spacer(Modifier.width(10.dp)); Text("正在识别座位表照片…")
            }
        }
        report?.let { r -> item { Text(r, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp)) } }
        if (seating == null) {
            item {
                EmptyState(
                    "🪑", "还没有座位表",
                    "拍一张座位表照片自动识别排列；或粘贴 Excel 里的座位表（每行一排，写姓名或学号，空位写“空”）；也可以按学号自动排列",
                )
            }
        } else {
            item {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("排数", fontSize = 13.sp)
                    TextButton(onClick = { vm.setSeating { s -> s?.let { SeatingLogic.resize(it, it.rows - 1, it.cols) } } }) { Text("－") }
                    Text("${seating.rows}", fontWeight = FontWeight.Bold)
                    TextButton(onClick = { vm.setSeating { s -> s?.let { SeatingLogic.resize(it, it.rows + 1, it.cols) } } }) { Text("＋") }
                    Spacer(Modifier.width(12.dp))
                    Text("列数", fontSize = 13.sp)
                    TextButton(onClick = { vm.setSeating { s -> s?.let { SeatingLogic.resize(it, it.rows, it.cols - 1) } } }) { Text("－") }
                    Text("${seating.cols}", fontWeight = FontWeight.Bold)
                    TextButton(onClick = { vm.setSeating { s -> s?.let { SeatingLogic.resize(it, it.rows, it.cols + 1) } } }) { Text("＋") }
                }
                Text(
                    when {
                        pick != null -> "点一个座位，把这位同学放进去"
                        sel != null -> "再点另一个座位交换（空位也可以）"
                        else -> "点两个座位互换位置；长按座位可把人移出"
                    },
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            item {
                SeatChart(
                    seating, c.students,
                    cell = { st, _ ->
                        SeatCellUi(
                            if (st == null) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerLowest,
                            MaterialTheme.colorScheme.outlineVariant,
                        ) {
                            if (st == null) Text("空", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                            else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(st.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(st.no, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    },
                    selected = { k, _ -> k == sel },
                    onCell = { k, _ ->
                        val p = pick
                        when {
                            p != null -> { vm.setSeating { s -> s?.let { SeatingLogic.place(it, p, k) } }; pick = null }
                            sel == null -> sel = k
                            sel == k -> sel = null
                            else -> { val a = sel!!; vm.setSeating { s -> s?.let { SeatingLogic.swap(it, a, k) } }; sel = null }
                        }
                    },
                    onLong = { st -> vm.setSeating { s -> s?.let { SeatingLogic.remove(it, st.id) } }; kit.undoable("已把 ${st.name} 移出座位"); sel = null },
                )
            }
        }
        if (unseated.isNotEmpty() && seating != null) {
            item {
                Column(Modifier.padding(16.dp)) {
                    Text("未入座 ${unseated.size} 人", fontWeight = FontWeight.Bold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        unseated.forEach { st ->
                            com.deepshawn.hwcounter.ui.components.Chip2(st.name, pick == st.id, { pick = if (pick == st.id) null else st.id; sel = null })
                        }
                    }
                }
            }
        }
    }

    if (photoSheet) PhotoSourceSheet({ photoSheet = false }, { photoSheet = false; photo.takePhoto() }, { photoSheet = false; photo.pickGallery() })
    if (paste) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { paste = false },
            title = { Text("📋 粘贴座位表") },
            text = {
                Column {
                    Text("每行一排、从讲台那边写起；单元格写姓名或学号，用 Tab（Excel 直接复制）、逗号或空格隔开，空位写“空”。可以加一行“讲台”表示讲台位置。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(text, { text = it }, minLines = 6, placeholder = { Text("讲台\n张三\t李四\t空\t王五\n赵六\t孙七\t周八\t吴九") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(enabled = text.isNotBlank(), onClick = {
                    paste = false
                    runCatching { parseSeatingAny(text, c.students) }.onSuccess { apply(it, "座位表") }.onFailure { kit.toast("解析失败：${it.message}") }
                }) { Text("解析并排座") }
            },
            dismissButton = { TextButton(onClick = { paste = false }) { Text("取消") } },
        )
    }
    if (auto) {
        var cols by remember { mutableIntStateOf(seating?.cols ?: 8) }
        AlertDialog(
            onDismissRequest = { auto = false },
            title = { Text("⚡ 按学号自动排列") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("每排")
                    TextButton(onClick = { if (cols > 1) cols-- }) { Text("－") }
                    Text("$cols", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    TextButton(onClick = { if (cols < 20) cols++ }) { Text("＋") }
                    Text("人，共 ${(c.students.size + cols - 1) / cols} 排")
                }
            },
            confirmButton = {
                Button(onClick = {
                    auto = false
                    val s = SeatingLogic.autoArrange(c.students, cols, seating?.frontAtTop ?: true)
                    vm.setSeating { s }; kit.undoable("已按学号排成 ${s.rows} 排 × ${s.cols} 列"); report = null
                }) { Text("排列") }
            },
            dismissButton = { TextButton(onClick = { auto = false }) { Text("取消") } },
        )
    }
    if (confirmClear) ConfirmDialog("清空座位表？", "名单和作业记录不受影响。", "清空", { confirmClear = false }) {
        confirmClear = false; vm.setSeating { null }; kit.undoable("已清空座位表")
    }
}
