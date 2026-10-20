@file:OptIn(ExperimentalMaterial3Api::class)

package com.deepshawn.hwcounter.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepshawn.hwcounter.data.AppData
import com.deepshawn.hwcounter.data.AppViewModel
import com.deepshawn.hwcounter.recog.AiRecognizer
import com.deepshawn.hwcounter.recog.ImageUtils
import com.deepshawn.hwcounter.recog.StackCounter
import com.deepshawn.hwcounter.ui.LocalKit
import com.deepshawn.hwcounter.ui.components.AppCard
import com.deepshawn.hwcounter.ui.components.EmptyState
import com.deepshawn.hwcounter.ui.components.gradientBrush
import com.deepshawn.hwcounter.ui.rememberPhotoLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.hypot

@Composable
fun StackCountScreen(vm: AppViewModel, data: AppData, assignmentId: String?) {
    val kit = LocalKit.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = vm.currentClass(data)
    var bmp by remember { mutableStateOf<Bitmap?>(null) }
    // 图像坐标
    var p0 by remember { mutableStateOf<Offset?>(null) }
    var p1 by remember { mutableStateOf<Offset?>(null) }
    var sensitivity by remember { mutableFloatStateOf(0.5f) }
    var band by remember { mutableFloatStateOf(12f) }
    var darkGaps by remember { mutableStateOf(true) }
    var result by remember { mutableStateOf<StackCounter.Result?>(null) }
    var marks by remember { mutableStateOf<List<Float>>(emptyList()) }
    var extra by remember { mutableIntStateOf(1) }
    var aiBusy by remember { mutableStateOf(false) }
    var aiMsg by remember { mutableStateOf("") }
    var target by remember { mutableStateOf(assignmentId) }
    var pickMenu by remember { mutableStateOf(false) }

    val photo = rememberPhotoLauncher { uris ->
        scope.launch {
            val b = withContext(Dispatchers.IO) { ImageUtils.load(ctx, uris.first(), 1600) }
            bmp = b; p0 = null; p1 = null; result = null; marks = emptyList(); aiMsg = ""
            if (b == null) kit.toast("图片读取失败")
        }
    }

    LaunchedEffect(bmp, p0, p1, sensitivity, band, darkGaps) {
        val b = bmp ?: return@LaunchedEffect
        val a0 = p0 ?: return@LaunchedEffect
        val a1 = p1 ?: return@LaunchedEffect
        if (hypot(a1.x - a0.x, a1.y - a0.y) < 20f) return@LaunchedEffect
        val r = withContext(Dispatchers.Default) { StackCounter.analyze(b, a0.x, a0.y, a1.x, a1.y, sensitivity, darkGaps, band.toInt()) }
        result = r
        marks = r.marks
    }

    val count = (marks.size + extra).coerceAtLeast(0)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TopAppBar(
            title = { Text("数本数 · 扫描线") },
            navigationIcon = { IconButton(onClick = { kit.nav.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
        )
        CountModeSwitch(false, assignmentId)
        val b = bmp
        if (b == null) {
            EmptyState("📚", "拍一张作业本侧面照", "把一摞作业本侧面（书脊/纸边）对准镜头，尽量拍正、光线均匀。\n然后在照片上沿着本子叠放方向画一条线，自动数出分界线。") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { photo.takePhoto() }) { Icon(Icons.Filled.CameraAlt, null); Spacer(Modifier.width(6.dp)); Text("拍照") }
                    OutlinedButton(onClick = { photo.pickGallery() }) { Icon(Icons.Filled.PhotoLibrary, null); Spacer(Modifier.width(6.dp)); Text("相册") }
                }
            }
            return@Column
        }
        // 结果条
        AppCard(Modifier.padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(70.dp).clip(CircleShape).background(gradientBrush()), contentAlignment = Alignment.Center) {
                    Text("$count", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (p1 == null) "在照片上画一条穿过整摞本子的线" else "约 $count 本", style = MaterialTheme.typography.titleMedium)
                    Text("检测到 ${marks.size} 条分界线 · 点击照片上的线可增删标记", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FilledIconButton(onClick = { extra-- }) { Icon(Icons.Filled.Remove, "减") }
                FilledIconButton(onClick = { extra++ }) { Icon(Icons.Filled.Add, "加") }
            }
            if (aiMsg.isNotBlank()) Text(aiMsg, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        }
        Spacer(Modifier.height(12.dp))
        // 图片画线
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            val img = remember(b) { b.asImageBitmap() }
            Box(Modifier.fillMaxWidth().aspectRatio(b.width.toFloat() / b.height).clip(MaterialTheme.shapes.medium)) {
                Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                Canvas(
                    Modifier.fillMaxSize()
                        .pointerInput(b) {
                            detectDragGestures(
                                onDragStart = { o ->
                                    val s = size.width.toFloat() / b.width
                                    p0 = Offset(o.x / s, o.y / s); p1 = p0
                                },
                                onDrag = { change, _ ->
                                    val s = size.width.toFloat() / b.width
                                    p1 = Offset(change.position.x / s, change.position.y / s)
                                },
                            )
                        }
                        .pointerInput(b, marks) {
                            detectTapGestures { o ->
                                val a0 = p0 ?: return@detectTapGestures
                                val a1 = p1 ?: return@detectTapGestures
                                val s = size.width.toFloat() / b.width
                                val tx = o.x / s
                                val ty = o.y / s
                                val dx = a1.x - a0.x
                                val dy = a1.y - a0.y
                                val l2 = dx * dx + dy * dy
                                if (l2 < 1f) return@detectTapGestures
                                val t = (((tx - a0.x) * dx + (ty - a0.y) * dy) / l2).coerceIn(0f, 1f)
                                val px = a0.x + dx * t
                                val py = a0.y + dy * t
                                if (hypot(px - tx, py - ty) > 60f / s) return@detectTapGestures
                                val near = marks.minByOrNull { abs(it - t) }
                                val len = hypot(dx, dy)
                                marks = if (near != null && abs(near - t) * len < 12f) marks - near else (marks + t).sorted()
                                kit.haptic()
                            }
                        },
                ) {
                    val s = size.width / b.width
                    val a0 = p0
                    val a1 = p1
                    if (a0 != null && a1 != null) {
                        val v0 = Offset(a0.x * s, a0.y * s)
                        val v1 = Offset(a1.x * s, a1.y * s)
                        drawLine(Color.Black.copy(alpha = 0.4f), v0, v1, band * 2 * s, cap = StrokeCap.Round)
                        drawLine(Color(0xFF00E5FF), v0, v1, 4f, cap = StrokeCap.Round)
                        val dx = v1.x - v0.x
                        val dy = v1.y - v0.y
                        val len = hypot(dx, dy).coerceAtLeast(1f)
                        val nx = -dy / len * 22f
                        val ny = dx / len * 22f
                        marks.forEach { t ->
                            val p = Offset(v0.x + dx * t, v0.y + dy * t)
                            drawLine(Color(0xFFFF4081), Offset(p.x - nx, p.y - ny), Offset(p.x + nx, p.y + ny), 5f, cap = StrokeCap.Round)
                            drawCircle(Color.White, 7f, Offset(p.x + nx, p.y + ny))
                            drawCircle(Color(0xFFFF4081), 5f, Offset(p.x + nx, p.y + ny))
                        }
                        drawCircle(Color.White, 10f, v0); drawCircle(Color.White, 10f, v1)
                    }
                }
            }
        }
        // 波形
        result?.let { r ->
            Spacer(Modifier.height(10.dp))
            val prim = MaterialTheme.colorScheme.primary
            Canvas(Modifier.fillMaxWidth().height(70.dp).padding(horizontal = 16.dp)) {
                val path = Path()
                r.profile.forEachIndexed { i, v ->
                    val x = size.width * i / (r.profile.size - 1).coerceAtLeast(1)
                    val y = size.height * (1f - v)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, prim, style = Stroke(2.5f))
                marks.forEach { t -> drawLine(Color(0xFFFF4081), Offset(size.width * t, 0f), Offset(size.width * t, size.height), 2f) }
            }
        }
        // 参数
        AppCard(Modifier.padding(16.dp)) {
            Text("灵敏度 ${(sensitivity * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
            Slider(sensitivity, { sensitivity = it })
            Text("采样带宽 ${band.toInt()} px（越宽越抗噪）", style = MaterialTheme.typography.labelLarge)
            Slider(band, { band = it }, valueRange = 2f..40f)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("分界线是暗色缝隙", Modifier.weight(1f))
                Switch(darkGaps, { darkGaps = it })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("本数 = 分界线 ${if (extra >= 0) "+" else ""}$extra", Modifier.weight(1f), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("通常两端各有一条边，用 ± 微调", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { photo.takePhoto() }, Modifier.weight(1f)) { Text("重拍") }
            FilledTonalButton(
                enabled = !aiBusy,
                onClick = {
                    if (!data.settings.ai.vision) kit.toast("当前 AI 模型为纯文本模式，无法看图数本数")
                    else scope.launch {
                        aiBusy = true; aiMsg = "🤖 AI 正在数…"
                        try {
                            val r = AiRecognizer.recognize(data.settings.ai, b, emptyList(), "stack")
                            aiMsg = "🤖 AI：${r.count ?: "?"} 本。${r.note}"
                            r.count?.let { n -> extra = n - marks.size }
                        } catch (e: Exception) {
                            aiMsg = "AI 失败：${e.message}"
                        }
                        aiBusy = false
                    }
                },
                modifier = Modifier.weight(1f),
            ) { Icon(Icons.Filled.AutoAwesome, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("AI 数") }
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.padding(horizontal = 16.dp)) {
            val tgt = c?.assignments?.firstOrNull { it.id == target }
            Button(
                onClick = {
                    if (tgt == null) pickMenu = true else {
                        vm.editAssignment(tgt.id) { it.copy(countedBooks = count) }
                        kit.toast("已记录「${tgt.title}」实物 $count 本")
                        kit.nav.pop()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(if (tgt == null) "保存到某次作业…" else "保存到「${tgt.title}」") }
            DropdownMenu(pickMenu, { pickMenu = false }) {
                c?.sortedAssignments()?.take(12)?.forEach { a ->
                    DropdownMenuItem(text = { Text("${a.subject} ${a.title}") }, onClick = { target = a.id; pickMenu = false })
                }
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}
