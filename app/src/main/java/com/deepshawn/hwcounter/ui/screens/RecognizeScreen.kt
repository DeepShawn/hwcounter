@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.deepshawn.hwcounter.ui.screens

import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import com.deepshawn.hwcounter.recog.Hit
import com.deepshawn.hwcounter.recog.ImageUtils
import com.deepshawn.hwcounter.recog.LocalRecognizer
import com.deepshawn.hwcounter.recog.MatchSource
import com.deepshawn.hwcounter.recog.Matcher
import com.deepshawn.hwcounter.recog.TextPiece
import com.deepshawn.hwcounter.ui.LocalKit
import com.deepshawn.hwcounter.ui.Route
import com.deepshawn.hwcounter.ui.components.AppCard
import com.deepshawn.hwcounter.ui.components.Avatar
import com.deepshawn.hwcounter.ui.components.SectionTitle
import com.deepshawn.hwcounter.ui.components.gradientBrush
import com.deepshawn.hwcounter.ui.components.statusColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private class ImageResult(
    val bitmap: Bitmap,
    val hits: List<Hit>,
    val unknown: List<Pair<String, Rect?>>,
    val aiCount: Int?,
    val aiNote: String,
    val error: String?,
)

@Composable
fun RecognizeScreen(vm: AppViewModel, dataIn: AppData, assignmentId: String, uris: List<Uri>) {
    // 刚创建的作业可能比 Compose 状态早一帧到达，回退到 ViewModel 的最新值
    val data = if (vm.currentClass(dataIn)?.assignments?.any { it.id == assignmentId } == true) dataIn else vm.data.value
    val kit = LocalKit.current
    val ctx = LocalContext.current
    val c = vm.currentClass(data) ?: return
    val a = c.assignments.firstOrNull { it.id == assignmentId } ?: return
    val settings = data.settings
    val results = remember { mutableStateListOf<ImageResult>() }
    var progress by remember { mutableIntStateOf(0) }
    var phase by remember { mutableStateOf("加载图片…") }
    var done by remember { mutableStateOf(false) }
    /** 用户手动取消勾选的 / 手动添加的 */
    val excluded = remember { mutableStateListOf<String>() }
    val manual = remember { mutableStateListOf<String>() }
    var markRestMissing by remember { mutableStateOf(false) }
    var recordCount by remember { mutableStateOf(true) }
    var countOverride by remember { mutableStateOf("") }
    var addQuery by remember { mutableStateOf("") }

    LaunchedEffect(uris) {
        val roster = c.students
        val matcher = Matcher(roster, settings.minIdLength, settings.fuzzy, settings.matchNames)
        for ((i, u) in uris.withIndex()) {
            phase = "第 ${i + 1}/${uris.size} 张：读取图片"
            val bmp = withContext(Dispatchers.IO) { runCatching { ImageUtils.load(ctx, u) }.getOrNull() }
            if (bmp == null) { progress = i + 1; continue }
            var hits = emptyList<Hit>()
            var unknown = emptyList<Pair<String, Rect?>>()
            var aiCount: Int? = null
            var aiNote = ""
            var err: String? = null
            var ocrLines = emptyList<String>()
            // 纯文本 AI 需要先拿到 OCR 文字，所以即使是“仅 AI”模式也跑一次本地 OCR
            val needOcr = settings.recogMode != RecogMode.AI || !settings.ai.vision
            if (needOcr) {
                phase = "第 ${i + 1}/${uris.size} 张：本地 OCR 识别中"
                try {
                    val raw = LocalRecognizer.recognize(bmp)
                    ocrLines = raw.lines.map { it.text } + raw.barcodes.map { it.text }
                    if (settings.recogMode != RecogMode.AI) {
                        val m = withContext(Dispatchers.Default) { matcher.match(raw.lines, raw.barcodes) }
                        hits = m.hits; unknown = m.unknown
                    }
                } catch (e: Exception) {
                    err = "本地识别失败：${e.message}"
                }
            }
            if (settings.recogMode != RecogMode.LOCAL) {
                phase = "第 ${i + 1}/${uris.size} 张：AI 大模型分析中 ✨"
                try {
                    val r = if (settings.ai.vision) AiRecognizer.recognize(settings.ai, bmp, roster)
                    else AiRecognizer.recognizeText(settings.ai, ocrLines, roster)
                    aiCount = r.count
                    aiNote = r.note
                    val aiPieces = r.ids.map { TextPiece(it) } + r.names.map { TextPiece(it) }
                    val m2 = Matcher(roster, 2, false, true).match(aiPieces)
                    val known = hits.map { it.studentId }.toSet()
                    hits = hits + m2.hits.filter { it.studentId !in known }.map { it.copy(source = MatchSource.AI) }
                } catch (e: Exception) {
                    err = (err?.let { "$it\n" } ?: "") + "AI 识别失败：${e.message}"
                }
            }
            results += ImageResult(bmp, hits, unknown, aiCount, aiNote, err)
            progress = i + 1
            if (hits.isNotEmpty()) kit.haptic()
        }
        done = true
        phase = "完成"
        kit.beep()
    }

    val allHits = results.flatMap { it.hits }.distinctBy { it.studentId }
    val matchedIds = (allHits.map { it.studentId }.filter { it !in excluded } + manual).distinct()
    val byId = c.students.associateBy { it.id }
    val aiTotal = results.mapNotNull { it.aiCount }.takeIf { it.isNotEmpty() }?.sum()
    val estimated = maxOf(matchedIds.size, aiTotal ?: 0)
    val finalCount = countOverride.toIntOrNull() ?: estimated

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 120.dp)) {
        item {
            TopAppBar(
                title = { Text("识别结果") },
                navigationIcon = { IconButton(onClick = { kit.nav.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
            )
        }
        item {
            AppCard(Modifier.padding(horizontal = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(64.dp).clip(CircleShape).background(gradientBrush()), contentAlignment = Alignment.Center) {
                        Text("$finalCount", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (done) "识别到约 $estimated 本作业" else phase, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "匹配学生 ${matchedIds.size} 人" + (aiTotal?.let { " · AI 数出 $it 本" } ?: "") +
                                " · 模式：" + when (settings.recogMode) { RecogMode.LOCAL -> "本地离线"; RecogMode.AI -> "AI 大模型"; else -> "本地+AI" },
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(if (settings.recogMode == RecogMode.LOCAL) Icons.Filled.Memory else Icons.Filled.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                }
                if (!done) {
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(progress = { progress / uris.size.toFloat() }, modifier = Modifier.fillMaxWidth().clip(CircleShape))
                }
                results.mapNotNull { it.error }.forEach { Text("⚠️ $it", color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
                results.map { it.aiNote }.filter { it.isNotBlank() }.forEach { Text("🤖 $it", fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)) }
            }
        }
        // 图片 + 标注框
        item {
            LazyRow(contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                itemsIndexed(results) { _, r -> AnnotatedImage(r, Modifier.height(260.dp)) }
                if (!done) item { ScanningPlaceholder(Modifier.height(260.dp).width(180.dp)) }
            }
        }
        item {
            Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Legend(statusColor(HwStatus.SUBMITTED), "学号/二维码")
                Legend(Color(0xFF4A8CFF), "姓名/模糊")
                Legend(Color(0xFFFF8A3D), "未匹配编号")
            }
        }
        item { SectionTitle("✅ 已匹配 ${matchedIds.size} 人（点击可取消）", Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) }
        item {
            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                allHits.forEach { h ->
                    val s = byId[h.studentId] ?: return@forEach
                    val on = h.studentId !in excluded
                    MatchChip(s, h.source.label, on) { if (on) excluded += h.studentId else excluded -= h.studentId }
                }
                manual.forEach { id ->
                    val s = byId[id] ?: return@forEach
                    MatchChip(s, "手动", true) { manual -= id }
                }
            }
        }
        val unknownAll = results.flatMap { it.unknown }.map { it.first }.distinct()
        if (unknownAll.isNotEmpty()) {
            item {
                Text(
                    "未能匹配的编号：" + unknownAll.take(30).joinToString("、"),
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        }
        item {
            SectionTitle("➕ 漏识别？点击手动补录", Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
            OutlinedTextField(addQuery, { addQuery = it }, placeholder = { Text("搜索姓名/学号") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            Spacer(Modifier.height(8.dp))
            val rest = c.students.filter { it.id !in matchedIds && (addQuery.isBlank() || it.name.contains(addQuery) || it.no.contains(addQuery)) }
            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                rest.take(60).forEach { s ->
                    val already = (a.statuses[s.id] ?: HwStatus.NONE)
                    Box(
                        Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { excluded -= s.id; if (allHits.none { it.studentId == s.id }) manual += s.id; kit.haptic() }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) { Text(s.name + if (already != HwStatus.NONE) "·${already.short}" else "", fontSize = 13.sp) }
                }
            }
        }
        item {
            AppCard(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(recordCount, { recordCount = it })
                    Text("记录实物本数", Modifier.weight(1f))
                    OutlinedTextField(
                        countOverride, { v -> countOverride = v.filter { it.isDigit() }.take(4) },
                        placeholder = { Text("$estimated") }, singleLine = true, modifier = Modifier.width(96.dp),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(markRestMissing, { markRestMissing = it })
                    Text("其余未登记的同学标记为「未交」（${settings.rules.missing.signed()} 分）")
                }
            }
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Button(
            enabled = done,
            onClick = {
                // 已是“优秀/迟交/请假”的保持不变
                val toSubmit = matchedIds.filter { (a.statuses[it] ?: HwStatus.NONE) in setOf(HwStatus.NONE, HwStatus.MISSING) }
                vm.setStatus(a.id, toSubmit, HwStatus.SUBMITTED)
                if (recordCount && finalCount > 0) vm.editAssignment(a.id) { it.copy(countedBooks = finalCount) }
                if (markRestMissing) vm.fillRemaining(a.id, HwStatus.MISSING)
                kit.undoable("已登记 ${toSubmit.size} 人已交")
                kit.nav.pop()
                if (kit.nav.current != Route.Assign(a.id)) kit.nav.push(Route.Assign(a.id))
            },
            modifier = Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding().height(54.dp),
        ) {
            Icon(Icons.Filled.Check, null)
            Spacer(Modifier.width(8.dp))
            Text("确认登记 ${matchedIds.size} 人为已交", fontSize = 16.sp)
        }
    }
}

@Composable
private fun Legend(c: Color, t: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(c))
        Spacer(Modifier.width(4.dp))
        Text(t, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MatchChip(s: Student, tag: String, on: Boolean, onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.clip(CircleShape).clickable { onClick() },
    ) {
        Row(Modifier.padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(s.name, size = 26.dp)
            Spacer(Modifier.width(6.dp))
            Text(s.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (on) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            Spacer(Modifier.width(4.dp))
            Text(tag, fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun AnnotatedImage(r: ImageResult, modifier: Modifier) {
    val bmp = r.bitmap
    val img = remember(bmp) { bmp.asImageBitmap() }
    val green = Color(0xFF1DB57A)
    val blue = Color(0xFF4A8CFF)
    val orange = Color(0xFFFF8A3D)
    Box(modifier.aspectRatio(bmp.width.toFloat() / bmp.height).clip(MaterialTheme.shapes.medium)) {
        Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        Canvas(Modifier.fillMaxSize()) {
            val s = size.width / bmp.width
            fun box(rc: Rect, col: Color) {
                drawRoundRect(col.copy(alpha = 0.18f), Offset(rc.left * s, rc.top * s), Size(rc.width() * s, rc.height() * s), CornerRadius(6f))
                drawRoundRect(col, Offset(rc.left * s, rc.top * s), Size(rc.width() * s, rc.height() * s), CornerRadius(6f), style = Stroke(2.5f))
            }
            r.unknown.forEach { (_, rc) -> if (rc != null) box(rc, orange) }
            r.hits.forEach { h ->
                val rc = h.box ?: return@forEach
                box(rc, if (h.source == MatchSource.EXACT || h.source == MatchSource.QR) green else blue)
            }
        }
        Text(
            "${r.hits.size} 人", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp,
            modifier = Modifier.align(Alignment.TopStart).padding(6.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun ScanningPlaceholder(modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "scan")
    val y by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Reverse), label = "y")
    val brush = gradientBrush()
    Box(modifier.clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceVariant)) {
        Canvas(Modifier.fillMaxSize()) {
            val yy = size.height * y
            drawRect(
                Brush.verticalGradient(listOf(Color.Transparent, Color(0x553D8BFD)), startY = yy - 80f, endY = yy),
                Offset(0f, yy - 80f), Size(size.width, 80f),
            )
            drawRect(brush, Offset(0f, yy - 3f), Size(size.width, 6f))
        }
        Text("识别中…", Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
