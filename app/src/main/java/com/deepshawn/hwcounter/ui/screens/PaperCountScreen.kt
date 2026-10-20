@file:OptIn(ExperimentalMaterial3Api::class)

package com.deepshawn.hwcounter.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepshawn.hwcounter.data.AppData
import com.deepshawn.hwcounter.data.AppViewModel
import com.deepshawn.hwcounter.data.HwStatus
import com.deepshawn.hwcounter.recog.ImageUtils
import com.deepshawn.hwcounter.recog.PaperCounter
import com.deepshawn.hwcounter.ui.LocalKit
import com.deepshawn.hwcounter.ui.Route
import com.deepshawn.hwcounter.ui.components.AppCard
import com.deepshawn.hwcounter.ui.components.EmptyState
import com.deepshawn.hwcounter.ui.components.SectionTitle
import com.deepshawn.hwcounter.ui.components.gradientBrush
import com.deepshawn.hwcounter.ui.components.statusColor
import com.deepshawn.hwcounter.ui.rememberPhotoLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.hypot

/** 作业本 / 试卷 两种计数页的切换条 */
@Composable
fun CountModeSwitch(paper: Boolean, assignmentId: String?) {
    val kit = LocalKit.current
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        SegmentedButton(selected = !paper, onClick = { if (paper) kit.nav.replace(Route.Stack(assignmentId)) }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("📚 数作业本") }
        SegmentedButton(selected = paper, onClick = { if (!paper) kit.nav.replace(Route.Paper(assignmentId)) }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("📄 数试卷") }
    }
}

@Composable
fun PaperCountScreen(vm: AppViewModel, data: AppData, assignmentId: String?) {
    val kit = LocalKit.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val c = vm.currentClass(data)

    var bmp by remember { mutableStateOf<Bitmap?>(null) }
    var loading by remember { mutableStateOf(false) }
    var p0 by remember { mutableStateOf<Offset?>(null) }
    var p1 by remember { mutableStateOf<Offset?>(null) }
    var dragging by remember { mutableStateOf<Offset?>(null) }
    var spread by remember { mutableFloatStateOf(40f) }
    var darkGaps by remember { mutableStateOf(true) }
    var result by remember { mutableStateOf<PaperCounter.Result?>(null) }
    var analyzing by remember { mutableStateOf(false) }
    var strip by remember { mutableStateOf<Bitmap?>(null) }
    var marks by remember { mutableStateOf<List<Float>>(emptyList()) }
    var edited by remember { mutableStateOf(false) }
    var adjust by remember { mutableIntStateOf(0) }
    var target by remember { mutableStateOf(assignmentId) }
    var pickMenu by remember { mutableStateOf(false) }

    val photo = rememberPhotoLauncher { uris ->
        loading = true
        scope.launch {
            // 试卷很薄：尽量保留原始分辨率
            val b = withContext(Dispatchers.IO) { runCatching { ImageUtils.load(ctx, uris.first(), 4096) }.getOrNull() }
            bmp = b; p0 = null; p1 = null; result = null; strip = null; marks = emptyList(); edited = false; adjust = 0
            loading = false
            if (b == null) kit.toast("图片读取失败")
        }
    }

    // 参数变化后自动重新分析（防抖）
    LaunchedEffect(bmp, p0, p1, spread, darkGaps, dragging == null) {
        val b = bmp ?: return@LaunchedEffect
        val a0 = p0 ?: return@LaunchedEffect
        val a1 = p1 ?: return@LaunchedEffect
        if (dragging != null || hypot(a1.x - a0.x, a1.y - a0.y) < 30f) return@LaunchedEffect
        delay(150)
        analyzing = true
        val r = withContext(Dispatchers.Default) { PaperCounter.analyze(b, a0.x, a0.y, a1.x, a1.y, spread, darkGaps) }
        result = r
        marks = r?.marks ?: emptyList()
        edited = false
        adjust = 0
        strip = withContext(Dispatchers.Default) { PaperCounter.strip(b, a0.x, a0.y, a1.x, a1.y, 30, 3, r?.skewDeg ?: 0f) }
        analyzing = false
        if (r != null) kit.haptic()
    }

    val res = result
    val count = ((if (edited) marks.size + 1 else res?.count ?: 0) + adjust).coerceAtLeast(0)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TopAppBar(
            title = { Text("数试卷") },
            navigationIcon = { IconButton(onClick = { kit.nav.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
        )
        CountModeSwitch(true, assignmentId)
        val b = bmp
        if (b == null) {
            EmptyState(
                "📄", if (loading) "正在载入高清原图…" else "拍一张试卷侧面",
                "把一摞试卷侧面（纸边）对准镜头，越近越好。\n然后在照片上沿着叠放方向画一条穿过整摞的线。",
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { photo.takePhoto() }) { Icon(Icons.Filled.CameraAlt, null); Spacer(Modifier.width(6.dp)); Text("拍照") }
                    OutlinedButton(onClick = { photo.pickGallery() }) { Icon(Icons.Filled.PhotoLibrary, null); Spacer(Modifier.width(6.dp)); Text("相册") }
                }
            }
            AppCard(Modifier.padding(16.dp)) {
                Text("📷 拍出好结果的诀窍", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                listOf(
                    "🔍 尽量靠近：每张纸在照片里至少 3 个像素厚（100 张一摞时，纸边最好占画面一半高度以上）",
                    "🎯 点击屏幕对焦在纸边上，手稳住，必要时用 2× 镜头",
                    "💡 光线从侧前方打过来，纸缝的阴影更明显；避免反光",
                    "📐 把纸边墩齐；正对拍摄，不要太斜",
                    "🗂️ 太厚的一摞可以分成几叠分别数",
                ).forEach { Text(it, fontSize = 13.sp, modifier = Modifier.padding(vertical = 2.dp)) }
            }
            return@Column
        }

        // 结果卡片
        AppCard(Modifier.padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(76.dp).clip(CircleShape).background(gradientBrush()), contentAlignment = Alignment.Center) {
                    Text(if (res == null) "?" else "$count", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        when {
                            p1 == null -> "沿叠放方向画一条线"
                            analyzing -> "分析中…"
                            res == null -> "线太短或没有找到纸堆"
                            else -> "约 $count 张"
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (res != null) {
                        val conf = res.confidence
                        val col = when {
                            conf >= 0.8f -> statusColor(HwStatus.SUBMITTED)
                            conf >= 0.6f -> statusColor(HwStatus.LATE)
                            else -> statusColor(HwStatus.MISSING)
                        }
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(progress = { conf }, color = col, modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape))
                        Text(
                            "置信度 ${(conf * 100).toInt()}% · " + when {
                                conf >= 0.8f -> "可信"
                                conf >= 0.6f -> "基本可信，建议抽查"
                                else -> "请用下方放大条带核对"
                            },
                            fontSize = 12.sp, color = col,
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FilledIconButton(onClick = { adjust++ }, modifier = Modifier.size(36.dp)) { Icon(Icons.Filled.Add, "加一") }
                    FilledIconButton(onClick = { adjust-- }, modifier = Modifier.size(36.dp)) { Icon(Icons.Filled.Remove, "减一") }
                }
            }
            if (res != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "逐线计数 ${res.lineCounts.joinToString(" / ")}\n" +
                        "周期法 ${res.periodCount ?: "-"} 张 · 单张 ${"%.1f".format(res.pitchPx)} 像素 · 纸边偏角 ${res.skewDeg.toInt()}°" +
                        (if (res.inferred > 0) " · 推断缝 ${res.inferred}" else "") +
                        (if (edited) " · 已手动修正" else "") + (if (adjust != 0) " · 微调 ${if (adjust > 0) "+" else ""}$adjust" else ""),
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!res.resolvable || res.pitchPx < 3f) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "⚠️ 纸缝偏细/模糊（每张约 ${"%.1f".format(res.pitchPx)} 像素）。靠近一些、对焦纸边、侧光拍摄，让每张至少 3 像素，结果会准很多。",
                        fontSize = 12.sp, color = statusColor(HwStatus.LATE),
                        modifier = Modifier.clip(MaterialTheme.shapes.small).background(statusColor(HwStatus.LATE).copy(alpha = 0.12f)).padding(8.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        // 照片 + 扫描线（可拖动端点，带放大镜）
        val img = remember(b) { b.asImageBitmap() }
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).aspectRatio(b.width.toFloat() / b.height).clip(MaterialTheme.shapes.medium)) {
            Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            Canvas(
                Modifier.fillMaxSize().pointerInput(b) {
                    var which = 1
                    detectDragGestures(
                        onDragStart = { o ->
                            val s = size.width.toFloat() / b.width
                            val io = Offset(o.x / s, o.y / s)
                            val a0 = p0
                            val a1 = p1
                            val near = 36f / s
                            which = when {
                                a0 != null && (a0 - io).getDistance() < near -> 0
                                a1 != null && (a1 - io).getDistance() < near -> 1
                                else -> { p0 = io; p1 = io; 1 }
                            }
                            dragging = io
                        },
                        onDrag = { change, _ ->
                            val s = size.width.toFloat() / b.width
                            val io = Offset(
                                (change.position.x / s).coerceIn(0f, b.width - 1f),
                                (change.position.y / s).coerceIn(0f, b.height - 1f),
                            )
                            if (which == 0) p0 = io else p1 = io
                            dragging = io
                        },
                        onDragEnd = { dragging = null },
                        onDragCancel = { dragging = null },
                    )
                },
            ) {
                val s = size.width / b.width
                val a0 = p0
                val a1 = p1
                if (a0 != null && a1 != null) {
                    val v0 = Offset(a0.x * s, a0.y * s)
                    val v1 = Offset(a1.x * s, a1.y * s)
                    val len = (v1 - v0).getDistance().coerceAtLeast(1f)
                    val nx = -(v1.y - v0.y) / len
                    val ny = (v1.x - v0.x) / len
                    // 平行取样范围
                    val sp = spread * s
                    val path = Path().apply {
                        moveTo(v0.x + nx * sp, v0.y + ny * sp); lineTo(v1.x + nx * sp, v1.y + ny * sp)
                        lineTo(v1.x - nx * sp, v1.y - ny * sp); lineTo(v0.x - nx * sp, v0.y - ny * sp); close()
                    }
                    drawPath(path, Color(0x3300E5FF))
                    drawLine(Color(0xFF00E5FF), v0, v1, 3f, cap = StrokeCap.Round)
                    if (dragging == null) {
                        marks.forEach { t ->
                            val p = Offset(v0.x + (v1.x - v0.x) * t, v0.y + (v1.y - v0.y) * t)
                            drawLine(Color(0xFFFF4081), Offset(p.x - nx * 10f, p.y - ny * 10f), Offset(p.x + nx * 10f, p.y + ny * 10f), 2f)
                        }
                    }
                    listOf(v0, v1).forEach { v ->
                        drawCircle(Color.White, 14f, v)
                        drawCircle(Color(0xFF00E5FF), 10f, v, style = Stroke(4f))
                    }
                }
                // 放大镜：显示手指附近 3× 原图
                dragging?.let { d ->
                    val r = size.minDimension * 0.18f
                    val center = if (d.x * s < size.width / 2) Offset(size.width - r - 12f, r + 12f) else Offset(r + 12f, r + 12f)
                    val srcHalf = (r / 3f / s).toInt().coerceAtLeast(4)
                    val sx = (d.x.toInt() - srcHalf).coerceIn(0, (b.width - 2 * srcHalf).coerceAtLeast(0))
                    val sy = (d.y.toInt() - srcHalf).coerceIn(0, (b.height - 2 * srcHalf).coerceAtLeast(0))
                    val clip = Path().apply { addOval(androidx.compose.ui.geometry.Rect(center, r)) }
                    clipPath(clip) {
                        drawImage(
                            img,
                            srcOffset = IntOffset(sx, sy),
                            srcSize = IntSize(minOf(2 * srcHalf, b.width), minOf(2 * srcHalf, b.height)),
                            dstOffset = IntOffset((center.x - r).toInt(), (center.y - r).toInt()),
                            dstSize = IntSize((2 * r).toInt(), (2 * r).toInt()),
                        )
                    }
                    drawCircle(Color.White, r, center, style = Stroke(5f))
                    // 十字准星对准手指位置
                    val fx = center.x - r + (d.x - sx) / (2 * srcHalf) * 2 * r
                    val fy = center.y - r + (d.y - sy) / (2 * srcHalf) * 2 * r
                    drawLine(Color(0xFFFF4081), Offset(fx - 12f, fy), Offset(fx + 12f, fy), 2f)
                    drawLine(Color(0xFFFF4081), Offset(fx, fy - 12f), Offset(fx, fy + 12f), 2f)
                }
            }
        }
        Text(
            "拖动画线，拖端点微调（会出现放大镜）。线要从纸堆外一点开始、到纸堆外一点结束。",
            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
        )

        // 放大核对条带
        val st = strip
        if (st != null && res != null) {
            SectionTitle("🔬 放大核对（左右滑动，点击增删标记）", Modifier.padding(horizontal = 12.dp))
            val stripImg = remember(st) { st.asImageBitmap() }
            val wDp = with(density) { st.width.toDp() }
            val hDp = with(density) { st.height.toDp() }
            val pitchStrip = res.pitchPx * 3
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(MaterialTheme.shapes.small).horizontalScroll(rememberScrollState())) {
                Box(Modifier.size(wDp, hDp)) {
                    Image(stripImg, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                    Canvas(
                        Modifier.fillMaxSize().pointerInput(st, marks) {
                            detectTapGestures { o ->
                                val t = o.x / size.width
                                val nearest = marks.minByOrNull { abs(it - t) }
                                marks = if (nearest != null && abs(nearest - t) * size.width < pitchStrip * 0.45f) marks - nearest else (marks + t).sorted()
                                edited = true
                                kit.haptic()
                            }
                        },
                    ) {
                        // 纸堆起止
                        listOf(res.start, res.end).forEach { t ->
                            drawLine(Color(0xFF00E5FF), Offset(t * size.width, 0f), Offset(t * size.width, size.height), 3f)
                        }
                        marks.forEachIndexed { i, t ->
                            val x = t * size.width
                            drawLine(Color(0xCCFF4081), Offset(x, 0f), Offset(x, size.height * 0.28f), 2f)
                            drawLine(Color(0xCCFF4081), Offset(x, size.height * 0.72f), Offset(x, size.height), 2f)
                            if ((i + 1) % 10 == 0) drawCircle(Color(0xFFFFC107), 5f, Offset(x, size.height * 0.5f))
                        }
                    }
                }
            }
            Text("粉色短线 = 纸缝 · 黄点 = 每 10 张 · 青线 = 纸堆起止", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        }

        // 参数
        AppCard(Modifier.padding(16.dp)) {
            Text("取样宽度 ${spread.toInt()} 像素（9 条平行线投票）", style = MaterialTheme.typography.labelLarge)
            Slider(spread, { spread = it }, valueRange = 5f..150f)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("纸缝是暗线")
                    Text("白纸一般是；深色纸或逆光拍时关闭", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(darkGaps, { darkGaps = it })
            }
        }
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { photo.takePhoto() }, Modifier.weight(1f)) { Text("重拍") }
            OutlinedButton(onClick = { photo.pickGallery() }, Modifier.weight(1f)) { Text("换一张") }
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.padding(horizontal = 16.dp)) {
            val tgt = c?.assignments?.firstOrNull { it.id == target }
            Button(
                enabled = res != null,
                onClick = {
                    if (tgt == null) pickMenu = true else {
                        vm.editAssignment(tgt.id) { it.copy(countedBooks = count) }
                        kit.toast("已记录「${tgt.title}」实数 $count 份")
                        kit.nav.pop()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(if (tgt == null) "保存到某次作业…" else "保存到「${tgt.title}」：$count 份") }
            DropdownMenu(pickMenu, { pickMenu = false }) {
                c?.sortedAssignments()?.take(12)?.forEach { a ->
                    DropdownMenuItem(text = { Text("${a.subject} ${a.title}") }, onClick = { target = a.id; pickMenu = false })
                }
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}
