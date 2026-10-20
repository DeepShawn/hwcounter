@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.deepshawn.hwcounter.ui.screens

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepshawn.hwcounter.data.AppData
import com.deepshawn.hwcounter.data.AppViewModel
import com.deepshawn.hwcounter.data.HwStatus
import com.deepshawn.hwcounter.data.RecogMode
import com.deepshawn.hwcounter.data.Student
import com.deepshawn.hwcounter.data.signed
import com.deepshawn.hwcounter.recog.AiRecognizer
import com.deepshawn.hwcounter.recog.ImageUtils
import com.deepshawn.hwcounter.recog.Matcher
import com.deepshawn.hwcounter.recog.SpineRecognizer
import com.deepshawn.hwcounter.recog.TextPiece
import com.deepshawn.hwcounter.ui.LocalKit
import com.deepshawn.hwcounter.ui.Route
import com.deepshawn.hwcounter.ui.components.AppCard
import com.deepshawn.hwcounter.ui.components.Avatar
import com.deepshawn.hwcounter.ui.components.EmptyState
import com.deepshawn.hwcounter.ui.components.SectionTitle
import com.deepshawn.hwcounter.ui.components.gradientBrush
import com.deepshawn.hwcounter.ui.components.statusColor
import com.deepshawn.hwcounter.ui.rememberPhotoLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 一行“书”：来自识别（可被用户改指派/剔除），或用户手动补的 */
private data class Row2(val key: String, val studentId: String?, val text: String, val fromAi: Boolean = false)

@Composable
fun SpineScreen(vm: AppViewModel, dataIn: AppData, assignmentId: String) {
    // 刚创建的作业可能比 Compose 状态早一帧到达，回退到 ViewModel 的最新值
    val data = if (vm.currentClass(dataIn)?.assignments?.any { it.id == assignmentId } == true) dataIn else vm.data.value
    val kit = LocalKit.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = vm.currentClass(data) ?: return
    val a = c.assignments.firstOrNull { it.id == assignmentId } ?: return
    val settings = data.settings
    val byId = remember(c.students) { c.students.associateBy { it.id } }

    var busy by remember { mutableStateOf(false) }
    var phase by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<SpineRecognizer.Result?>(null) }
    val rows = remember { mutableStateListOf<Row2>() }
    val excluded = remember { mutableStateMapOf<String, Boolean>() }
    var aiNote by remember { mutableStateOf("") }
    var aiCount by remember { mutableStateOf<Int?>(null) }
    var assignFor by remember { mutableStateOf<Row2?>(null) }
    var addDialog by remember { mutableStateOf(false) }
    var countOverride by remember { mutableStateOf("") }
    var markRestMissing by remember { mutableStateOf(false) }

    fun process(uris: List<Uri>) {
        if (busy) return
        busy = true
        result = null; rows.clear(); excluded.clear(); aiNote = ""; aiCount = null; countOverride = ""
        scope.launch {
            try {
                phase = "读取图片…"
                val src = withContext(Dispatchers.IO) { ImageUtils.load(ctx, uris.first(), 3200) }
                if (src == null) { kit.toast("图片读取失败"); return@launch }
                val r = withContext(Dispatchers.Default) {
                    SpineRecognizer.analyze(src, c.students, settings.minIdLength, settings.fuzzy) { p -> phase = p }
                }
                result = r
                r.books.forEachIndexed { i, b -> rows += Row2("b$i", b.studentId, b.text) }
                if (settings.recogMode != RecogMode.LOCAL && settings.ai.vision) {
                    phase = "AI 复核中 ✨"
                    try {
                        val ai = AiRecognizer.recognize(settings.ai, r.bitmap, c.students, "spine")
                        aiCount = ai.count
                        aiNote = ai.note
                        val m = Matcher(c.students, 2, false, false).match(ai.ids.map { TextPiece(it) })
                        val have = rows.mapNotNull { it.studentId }.toSet()
                        m.hits.filter { it.studentId !in have }.forEachIndexed { i, h -> rows += Row2("ai$i", h.studentId, h.raw, fromAi = true) }
                    } catch (e: Exception) {
                        aiNote = "AI 复核失败：${e.message?.take(80)}"
                    }
                }
                kit.beep()
                kit.haptic(true)
            } catch (e: Exception) {
                kit.toast("识别失败：${e.message}")
            } finally {
                busy = false
            }
        }
    }

    val photo = rememberPhotoLauncher { uris -> process(uris) }

    val active = rows.filter { excluded[it.key] != true }
    val matchedIds = active.mapNotNull { it.studentId }.distinct()
    val unknownCount = active.count { it.studentId == null }
    val idBooks = matchedIds.size + unknownCount
    val stripe = result?.stripeCount
    val estimate = listOfNotNull(idBooks, stripe, aiCount).maxOrNull() ?: 0
    val finalCount = countOverride.toIntOrNull() ?: idBooks.coerceAtLeast(aiCount ?: 0)

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 120.dp)) {
            item {
                TopAppBar(
                    title = { Text("侧面学号 · 数本数") },
                    navigationIcon = { IconButton(onClick = { kit.nav.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
                )
            }
            val r = result
            if (r == null) {
                item {
                    if (busy) {
                        Column(Modifier.padding(16.dp)) {
                            ScanningPlaceholder(Modifier.fillMaxWidth().height(260.dp))
                            Spacer(Modifier.height(12.dp))
                            Text(phase, Modifier.align(Alignment.CenterHorizontally), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        SpineGuide(onCamera = { photo.takePhoto() }, onGallery = { photo.pickGallery() })
                    }
                }
                return@LazyColumn
            }
            // 汇总
            item {
                AppCard(Modifier.padding(horizontal = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(70.dp).clip(CircleShape).background(gradientBrush()), contentAlignment = Alignment.Center) {
                            Text("$idBooks", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("读到 $idBooks 本 · 匹配 ${matchedIds.size} 人", style = MaterialTheme.typography.titleMedium)
                            Text(
                                listOfNotNull(
                                    if (unknownCount > 0) "$unknownCount 个学号未对上名单" else null,
                                    stripe?.let { "条纹估计 ≈$it 本" },
                                    aiCount?.let { "AI 数出 $it 本" },
                                ).joinToString(" · ").ifEmpty { "全部对上名单 👍" },
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (estimate > idBooks) {
                        Spacer(Modifier.height(10.dp))
                        Row(
                            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(statusColor(HwStatus.LATE).copy(alpha = 0.15f)).padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Warning, null, tint = statusColor(HwStatus.LATE))
                            Spacer(Modifier.width(8.dp))
                            Text("可能还有约 ${estimate - idBooks} 本的学号没读到：可补拍局部，或在下方手动补录", fontSize = 13.sp)
                        }
                    }
                    if (aiNote.isNotBlank()) Text("🤖 $aiNote", fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
            // 图片 + 标注
            item {
                val img = remember(r) { r.bitmap.asImageBitmap() }
                val green = statusColor(HwStatus.SUBMITTED)
                val orange = Color(0xFFFF8A3D)
                Box(
                    Modifier.padding(16.dp).fillMaxWidth().heightIn(max = 520.dp)
                        .aspectRatio(r.bitmap.width.toFloat() / r.bitmap.height, matchHeightConstraintsFirst = r.bitmap.height > r.bitmap.width)
                        .clip(MaterialTheme.shapes.medium),
                ) {
                    Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    Canvas(Modifier.fillMaxSize()) {
                        val s = size.width / r.bitmap.width
                        r.stripeLine?.let { l ->
                            val p0 = Offset(l[0] * s, l[1] * s)
                            val p1 = Offset(l[2] * s, l[3] * s)
                            drawLine(Color(0xFF00E5FF).copy(alpha = 0.8f), p0, p1, 3f, cap = StrokeCap.Round)
                            r.stripeMarks.forEach { t ->
                                val p = Offset(p0.x + (p1.x - p0.x) * t, p0.y + (p1.y - p0.y) * t)
                                drawCircle(Color(0xFFFF4081), 4f, p)
                            }
                        }
                        r.books.forEachIndexed { i, b ->
                            val key = "b$i"
                            if (excluded[key] == true) return@forEachIndexed
                            val col = if (rows.firstOrNull { it.key == key }?.studentId != null) green else orange
                            val tl = Offset(b.box.left * s, b.box.top * s)
                            val sz = Size(b.box.width() * s, b.box.height() * s)
                            drawRoundRect(col.copy(alpha = 0.2f), tl, sz, CornerRadius(5f))
                            drawRoundRect(col, tl, sz, CornerRadius(5f), style = Stroke(2.5f))
                        }
                    }
                }
            }
            item {
                Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Legend(statusColor(HwStatus.SUBMITTED), "已匹配")
                    Legend(Color(0xFFFF8A3D), "未对上名单")
                    Legend(Color(0xFFFF4081), "条纹分界")
                }
            }
            item { SectionTitle("📚 叠放顺序（点击可剔除 / 指派）", Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) }
            itemsIndexed(rows, key = { _, row -> row.key }) { i, row ->
                val st = row.studentId?.let { byId[it] }
                val off = excluded[row.key] == true
                val prev = st?.let { a.statuses[it.id] } ?: HwStatus.NONE
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp).clip(MaterialTheme.shapes.medium)
                        .background(if (off) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceContainerLowest)
                        .clickable { if (st == null && !off) assignFor = row else excluded[row.key] = !off }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("#${i + 1}", Modifier.width(36.dp), fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (st != null) Avatar(st.name, size = 32.dp) else Box(Modifier.size(32.dp).clip(CircleShape).background(Color(0xFFFF8A3D)), contentAlignment = Alignment.Center) { Text("?", color = Color.White, fontWeight = FontWeight.Black) }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            st?.name ?: "未对上名单 · 点击指派",
                            fontWeight = FontWeight.Bold,
                            color = if (off) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "读到：${row.text}" + (st?.let { " · ${it.no}" } ?: "") + (if (row.fromAi) " · AI" else "") +
                                (if (prev != HwStatus.NONE) " · 已是${prev.label}" else ""),
                            fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(if (off) "已剔除" else "✓", color = if (off) MaterialTheme.colorScheme.error else statusColor(HwStatus.SUBMITTED), fontWeight = FontWeight.Bold)
                }
            }
            item {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { addDialog = true }, Modifier.weight(1f)) { Text("➕ 手动补录") }
                    OutlinedButton(onClick = { photo.takePhoto() }, Modifier.weight(1f)) { Text("📷 重拍") }
                }
            }
            item {
                AppCard(Modifier.padding(horizontal = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("记录实物本数", Modifier.weight(1f))
                        OutlinedTextField(
                            countOverride, { v -> countOverride = v.filter { it.isDigit() }.take(4) },
                            placeholder = { Text("$finalCount") }, singleLine = true, modifier = Modifier.width(96.dp),
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(markRestMissing, { markRestMissing = it })
                        Text("其余未登记的同学标记为「未交」（${settings.rules.missing.signed()} 分）")
                    }
                }
            }
        }

        if (result != null) {
            Button(
                enabled = !busy,
                onClick = {
                    val toSubmit = matchedIds.filter { (a.statuses[it] ?: HwStatus.NONE) in setOf(HwStatus.NONE, HwStatus.MISSING) }
                    vm.setStatus(a.id, toSubmit, HwStatus.SUBMITTED)
                    if (finalCount > 0) vm.editAssignment(a.id) { it.copy(countedBooks = finalCount) }
                    if (markRestMissing) vm.fillRemaining(a.id, HwStatus.MISSING)
                    kit.undoable("已登记 ${toSubmit.size} 人已交，实物 $finalCount 本")
                    kit.nav.pop()
                    if (kit.nav.current != Route.Assign(a.id)) kit.nav.push(Route.Assign(a.id))
                },
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp).navigationBarsPadding().height(54.dp),
            ) {
                Icon(Icons.Filled.Check, null)
                Spacer(Modifier.width(8.dp))
                Text("确认：${matchedIds.size} 人已交 · $finalCount 本", fontSize = 16.sp)
            }
        }
    }

    assignFor?.let { row ->
        StudentPickDialog(
            title = "「${row.text}」是谁的作业？",
            students = c.students.filter { it.id !in matchedIds },
            onDismiss = { assignFor = null },
            onExclude = { excluded[row.key] = true; assignFor = null },
        ) { s ->
            val idx = rows.indexOfFirst { it.key == row.key }
            if (idx >= 0) rows[idx] = row.copy(studentId = s.id)
            assignFor = null
        }
    }
    if (addDialog) {
        StudentPickDialog(
            title = "补录一本",
            students = c.students.filter { it.id !in matchedIds },
            onDismiss = { addDialog = false },
            onExclude = null,
        ) { s ->
            rows += Row2("m${rows.size}_${s.id}", s.id, "手动")
            addDialog = false
            kit.haptic()
        }
    }
}

@Composable
private fun SpineGuide(onCamera: () -> Unit, onGallery: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(20.dp)) {
        EmptyState("📏", "侧面写学号，一拍全知道", "在每本作业本的侧面（书脊或纸边）写上学号，叠成一摞，侧面对准镜头拍一张。\n自动读出每本学号、数出本数，并按叠放顺序列出来。")
        AppCard {
            Text("拍摄小贴士", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            listOf(
                "✍️ 学号写在同一侧、字迹清晰，数字尽量写大一点",
                "📐 横放竖放都可以，会自动判断方向",
                "💡 光线均匀，镜头尽量正对侧面、贴近拍满画面",
                "📚 一摞太厚可以分两次拍",
                "🏷️ 也可以把学号二维码贴纸贴在侧面",
            ).forEach { Text(it, fontSize = 13.sp, modifier = Modifier.padding(vertical = 2.dp)) }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onCamera, Modifier.weight(1f).height(52.dp)) { Icon(Icons.Filled.CameraAlt, null); Spacer(Modifier.width(6.dp)); Text("拍侧面") }
            OutlinedButton(onClick = onGallery, Modifier.weight(1f).height(52.dp)) { Icon(Icons.Filled.PhotoLibrary, null); Spacer(Modifier.width(6.dp)); Text("相册") }
        }
    }
}

@Composable
private fun StudentPickDialog(
    title: String,
    students: List<Student>,
    onDismiss: () -> Unit,
    onExclude: (() -> Unit)?,
    onPick: (Student) -> Unit,
) {
    var q by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(q, { q = it }, placeholder = { Text("搜索姓名/学号") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    students.filter { q.isBlank() || it.name.contains(q) || it.no.contains(q) }.forEach { s ->
                        Box(
                            Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).clickable { onPick(s) }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) { Text("${s.name} ${s.no.takeLast(3)}", fontSize = 13.sp) }
                    }
                }
            }
        },
        confirmButton = { if (onExclude != null) TextButton(onClick = onExclude) { Text("不是作业，剔除", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
