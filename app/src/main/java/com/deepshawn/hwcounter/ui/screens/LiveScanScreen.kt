@file:OptIn(ExperimentalMaterial3Api::class)

package com.deepshawn.hwcounter.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Rect
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn as AndroidOptIn
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.deepshawn.hwcounter.data.AppData
import com.deepshawn.hwcounter.data.AppViewModel
import com.deepshawn.hwcounter.data.HwStatus
import com.deepshawn.hwcounter.data.Student
import com.deepshawn.hwcounter.recog.LocalRecognizer
import com.deepshawn.hwcounter.recog.MatchSource
import com.deepshawn.hwcounter.recog.Matcher
import com.deepshawn.hwcounter.recog.TextPiece
import com.deepshawn.hwcounter.ui.LocalKit
import com.deepshawn.hwcounter.ui.components.Avatar
import com.deepshawn.hwcounter.ui.components.EmptyState
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

private data class Frame(val boxes: List<Pair<Rect, Int>>, val w: Int, val h: Int)

@Composable
fun LiveScanScreen(vm: AppViewModel, dataIn: AppData, assignmentId: String) {
    // 刚创建的作业可能比 Compose 状态早一帧到达，回退到 ViewModel 的最新值
    val data = if (vm.currentClass(dataIn)?.assignments?.any { it.id == assignmentId } == true) dataIn else vm.data.value
    val kit = LocalKit.current
    val ctx = LocalContext.current
    val c = vm.currentClass(data) ?: return
    val a = c.assignments.firstOrNull { it.id == assignmentId } ?: return
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) perm.launch(Manifest.permission.CAMERA) }

    val found = remember { mutableStateListOf<String>() }
    val votes = remember { HashMap<String, Int>() }
    var last by remember { mutableStateOf<Student?>(null) }
    var lastTick by remember { mutableIntStateOf(0) }
    var frame by remember { mutableStateOf(Frame(emptyList(), 1, 1)) }
    var paused by remember { mutableStateOf(false) }
    var torch by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    val byId = remember(c.students) { c.students.associateBy { it.id } }
    val matcher = remember(c.students, data.settings) { Matcher(c.students, data.settings.minIdLength, data.settings.fuzzy, data.settings.matchNames) }
    val pausedState by rememberUpdatedState(paused)

    fun onFrame(lines: List<TextPiece>, codes: List<TextPiece>, w: Int, h: Int) {
        if (pausedState) return
        val m = matcher.match(lines, codes)
        val boxes = ArrayList<Pair<Rect, Int>>()
        m.unknown.forEach { (_, r) -> if (r != null) boxes += r to 0 }
        for (hit in m.hits) {
            hit.box?.let { boxes += it to 1 }
            if (hit.studentId in found) continue
            val strong = hit.source == MatchSource.EXACT || hit.source == MatchSource.QR
            val v = (votes[hit.studentId] ?: 0) + 1
            votes[hit.studentId] = v
            if (strong || v >= 2) {
                found.add(0, hit.studentId)
                last = byId[hit.studentId]
                lastTick++
                kit.haptic(true)
                kit.beep()
            }
        }
        frame = Frame(boxes, w, h)
    }
    val onFrameState by rememberUpdatedState(::onFrame)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (!granted) {
            Column(Modifier.align(Alignment.Center)) {
                EmptyState("📷", "需要相机权限", "实时扫描需要使用相机来识别作业本封面") {
                    Button(onClick = { perm.launch(Manifest.permission.CAMERA) }) { Text("授权相机") }
                }
            }
        } else {
            CameraPreview(onCamera = { camera = it }) { lines, codes, w, h -> onFrameState(lines, codes, w, h) }
            // 识别框叠加（PreviewView FILL_CENTER 映射）
            Canvas(Modifier.fillMaxSize()) {
                val f = frame
                val s = maxOf(size.width / f.w, size.height / f.h)
                val ox = (size.width - f.w * s) / 2
                val oy = (size.height - f.h * s) / 2
                f.boxes.forEach { (r, kind) ->
                    val col = if (kind == 1) Color(0xFF1DE9B6) else Color(0xFFFFB74D)
                    drawRoundRect(
                        col, Offset(ox + r.left * s, oy + r.top * s),
                        androidx.compose.ui.geometry.Size(r.width() * s, r.height() * s), CornerRadius(8f), style = Stroke(if (kind == 1) 5f else 2f),
                    )
                }
            }
            Reticle(Modifier.align(Alignment.Center))
        }

        // 顶部栏
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { kit.nav.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = Color.White) }
            Column(Modifier.weight(1f)) {
                Text("实时扫描", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("逐本翻开对准封面，识别到会“嘀”一声", color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
            }
            IconButton(onClick = { paused = !paused }) { Icon(if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause, "暂停", tint = Color.White) }
            IconButton(onClick = {
                torch = !torch
                camera?.cameraControl?.enableTorch(torch)
            }) { Icon(if (torch) Icons.Filled.FlashlightOn else Icons.Filled.FlashlightOff, "手电", tint = Color.White) }
        }

        // 底部面板
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
                .navigationBarsPadding()
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        AnimatedContent(found.size, transitionSpec = { (scaleIn() + fadeIn()) togetherWith fadeOut() }, label = "cnt") { n ->
                            Text("$n", fontSize = 44.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                        }
                        Text(" / ${c.students.size} 本", fontSize = 16.sp, modifier = Modifier.padding(bottom = 10.dp))
                    }
                    Text(if (paused) "已暂停" else "正在识别…", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AnimatedContent(lastTick, transitionSpec = { (scaleIn(initialScale = 0.6f) + fadeIn()) togetherWith fadeOut() }, label = "last") { _ ->
                    val s = last
                    if (s != null) {
                        Row(
                            Modifier.clip(CircleShape).background(Color(0xFF1DB57A)).padding(start = 4.dp, end = 14.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(s.name, size = 36.dp, color = Color.White.copy(alpha = 0.3f))
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text("✓ ${s.name}", color = Color.White, fontWeight = FontWeight.Bold)
                                Text(s.no, color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
            if (found.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(end = 8.dp)) {
                    items(found.toList(), key = { it }) { id ->
                        val s = byId[id] ?: return@items
                        Box(
                            Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        ) { Text(s.name, fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer) }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    val ids = found.filter { (a.statuses[it] ?: HwStatus.NONE) in setOf(HwStatus.NONE, HwStatus.MISSING) }
                    vm.setStatus(a.id, ids, HwStatus.SUBMITTED)
                    kit.undoable("已登记 ${ids.size} 人已交")
                    kit.nav.pop()
                },
                enabled = found.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Icon(Icons.Filled.Check, null)
                Spacer(Modifier.width(6.dp))
                Text("完成，登记 ${found.size} 人")
            }
        }
    }
}

@Composable
private fun Reticle(modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "ret")
    val p by t.animateFloat(0.92f, 1.04f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "p")
    Canvas(modifier.size(260.dp, 180.dp)) {
        val w = size.width * p
        val h = size.height * p
        val l = (size.width - w) / 2
        val tp = (size.height - h) / 2
        val len = 36f
        val col = Color.White.copy(alpha = 0.85f)
        val sw = 6f
        // 四角
        drawLine(col, Offset(l, tp), Offset(l + len, tp), sw); drawLine(col, Offset(l, tp), Offset(l, tp + len), sw)
        drawLine(col, Offset(l + w, tp), Offset(l + w - len, tp), sw); drawLine(col, Offset(l + w, tp), Offset(l + w, tp + len), sw)
        drawLine(col, Offset(l, tp + h), Offset(l + len, tp + h), sw); drawLine(col, Offset(l, tp + h), Offset(l, tp + h - len), sw)
        drawLine(col, Offset(l + w, tp + h), Offset(l + w - len, tp + h), sw); drawLine(col, Offset(l + w, tp + h), Offset(l + w, tp + h - len), sw)
    }
}

@Composable
private fun CameraPreview(onCamera: (Camera) -> Unit, onFrame: (List<TextPiece>, List<TextPiece>, Int, Int) -> Unit) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val frameCb by rememberUpdatedState(onFrame)
    val previewView = remember { PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }

    DisposableEffect(owner) {
        val future = ProcessCameraProvider.getInstance(ctx)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setResolutionStrategy(ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                        .build(),
                )
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(executor, Analyzer { lines, codes, w, h -> ContextCompat.getMainExecutor(ctx).execute { frameCb(lines, codes, w, h) } })
            try {
                provider.unbindAll()
                onCamera(provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis))
            } catch (_: Exception) {
            }
        }, ContextCompat.getMainExecutor(ctx))
        onDispose {
            runCatching { future.get().unbindAll() }
            executor.shutdown()
        }
    }
    AndroidView({ previewView }, Modifier.fillMaxSize())
}

private class Analyzer(private val cb: (List<TextPiece>, List<TextPiece>, Int, Int) -> Unit) : ImageAnalysis.Analyzer {
    @AndroidOptIn(ExperimentalGetImage::class)
    override fun analyze(proxy: androidx.camera.core.ImageProxy) {
        val media = proxy.image
        if (media == null) {
            proxy.close(); return
        }
        val rot = proxy.imageInfo.rotationDegrees
        val input = InputImage.fromMediaImage(media, rot)
        val t1 = LocalRecognizer.textRecognizer.process(input)
        val t2 = LocalRecognizer.barcodeScanner.process(input)
        Tasks.whenAllComplete(t1, t2).addOnCompleteListener {
            try {
                val lines = if (t1.isSuccessful) t1.result.textBlocks.flatMap { b -> b.lines.map { TextPiece(it.text, it.boundingBox) } } else emptyList()
                val codes = if (t2.isSuccessful) t2.result.mapNotNull { bc -> bc.rawValue?.let { TextPiece(it, bc.boundingBox) } } else emptyList()
                val w = if (rot % 180 == 0) proxy.width else proxy.height
                val h = if (rot % 180 == 0) proxy.height else proxy.width
                cb(lines, codes, w, h)
            } finally {
                proxy.close()
            }
        }
    }
}
