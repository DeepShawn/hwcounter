@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.deepshawn.hwcounter.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.deepshawn.hwcounter.data.AppData
import com.deepshawn.hwcounter.data.AppViewModel
import com.deepshawn.hwcounter.data.HwStatus
import com.deepshawn.hwcounter.data.summary
import com.deepshawn.hwcounter.recog.Pinyin
import com.deepshawn.hwcounter.recog.VoiceListener
import com.deepshawn.hwcounter.recog.VoiceParser
import com.deepshawn.hwcounter.ui.LocalKit
import com.deepshawn.hwcounter.ui.components.Avatar
import com.deepshawn.hwcounter.ui.components.Chip2
import com.deepshawn.hwcounter.ui.components.gradientBrush
import com.deepshawn.hwcounter.ui.components.statusColor
import kotlinx.coroutines.delay

/** 本次语音会话里登记过的一条（用于撤销） */
private data class VoiceEntry(val key: Long, val studentId: String, val heard: String, val prev: HwStatus, val status: HwStatus, val how: String = "")

/** 有歧义（同音/同名）待老师点选 */
private data class Pending(val key: Long, val heard: String, val candidates: List<String>)

@Composable
fun VoiceScreen(vm: AppViewModel, dataIn: AppData, assignmentId: String) {
    val data = if (vm.currentClass(dataIn)?.assignments?.any { it.id == assignmentId } == true) dataIn else vm.data.value
    val kit = LocalKit.current
    val ctx = LocalContext.current
    val c = vm.currentClass(data) ?: return
    val a = c.assignments.firstOrNull { it.id == assignmentId } ?: return
    val byId = remember(c.students) { c.students.associateBy { it.id } }
    val fuzzyLevel = data.settings.voiceFuzzy
    val useInitials = data.settings.voiceInitials
    val parser = remember(c.students, fuzzyLevel, useInitials) {
        VoiceParser(c.students, { ch -> Pinyin.readings(ch) }, listOf(0.6f, 1f, 1.4f)[fuzzyLevel.coerceIn(0, 2)], useInitials)
    }

    var status by remember { mutableStateOf(HwStatus.SUBMITTED) }
    val entries = remember { mutableStateListOf<VoiceEntry>() }
    val pendings = remember { mutableStateListOf<Pending>() }
    val misses = remember { mutableStateListOf<String>() }
    var partial by remember { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }
    var state by remember { mutableStateOf(VoiceListener.State.IDLE) }
    var keyboardMode by remember { mutableStateOf(!VoiceListener.available(ctx)) }
    var typed by remember { mutableStateOf("") }
    var seq by remember { mutableStateOf(0L) }
    /** 被撤销的学生：同一段文字再次解析时不再自动登记 */
    val dismissed = remember { mutableStateListOf<String>() }

    val latestA by rememberUpdatedState(a)
    val latestStatus by rememberUpdatedState(status)

    fun register(id: String, heard: String, how: String = "") {
        if (entries.any { it.studentId == id } || id in dismissed) return
        val prev = latestA.statuses[id] ?: HwStatus.NONE
        vm.setStatus(latestA.id, listOf(id), latestStatus)
        entries.add(0, VoiceEntry(seq++, id, heard, prev, latestStatus, how))
        kit.beep()
        kit.haptic()
    }

    fun handle(text: String) {
        val r = parser.parse(text)
        for (m in r.matches) {
            if (m.studentIds.size == 1) register(m.studentIds[0], m.heard, m.kind.label)
            else if (pendings.none { it.heard == m.heard } && m.studentIds.none { id -> entries.any { it.studentId == id } }) {
                pendings.add(0, Pending(seq++, m.heard, m.studentIds))
                kit.beep(false)
            }
        }
        if (r.leftover.isNotBlank() && r.leftover.length <= 20) {
            misses.add(0, r.leftover)
            while (misses.size > 6) misses.removeAt(misses.lastIndex)
        }
    }
    val handleLatest by rememberUpdatedState(::handle)

    val listener = remember {
        VoiceListener(
            ctx,
            onPartial = { partial = it },
            onFinal = { text -> handleLatest(text) },
            onState = { state = it },
        )
    }
    DisposableEffect(Unit) { onDispose { listener.destroy() } }

    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) { listening = true; listener.start() } else kit.toast("需要麦克风权限才能语音登记")
    }
    fun toggle() {
        if (listening) { listening = false; listener.stop(); partial = "" }
        else if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            listening = true; listener.start()
        } else perm.launch(Manifest.permission.RECORD_AUDIO)
    }
    LaunchedEffect(state) {
        if (state == VoiceListener.State.UNAVAILABLE) { listening = false; keyboardMode = true }
    }
    // 键盘语音输入：停顿 0.8 秒后解析整段（已登记/已撤销的不会重复）
    LaunchedEffect(typed) {
        if (typed.isBlank()) return@LaunchedEffect
        delay(800)
        handle(typed)
    }

    val sm = c.summary(a)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            TopAppBar(
                title = {
                    Column {
                        Text("语音登记")
                        Text("${a.subject} · ${a.title}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = { kit.nav.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
                actions = {
                    IconButton(onClick = {
                        keyboardMode = !keyboardMode
                        if (keyboardMode && listening) { listening = false; listener.stop() }
                    }) { Icon(if (keyboardMode) Icons.Filled.Mic else Icons.Filled.Keyboard, "切换输入方式") }
                },
            )
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("模糊程度", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                listOf("严格", "标准", "宽松").forEachIndexed { i, label ->
                    Chip2(label, fuzzyLevel == i, { vm.updateSettings { it.copy(voiceFuzzy = i) } }, Modifier.padding(start = 6.dp))
                }
            }
            Text(
                when (fuzzyLevel) {
                    0 -> "只认同音字和轻微口音，宁可漏也不认错"
                    2 -> "前后鼻音、平翘舌、n/l、f/h 都能认，漏字多字也认；同音多人时让你点选"
                    else -> "认同音字、常见口音（z/zh、n/l、in/ing…）和多音字姓氏"
                },
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp),
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("拼音首字母匹配", style = MaterialTheme.typography.labelLarge)
                    Text("首字母一致即登记，如 张锦宇 = ZJY：可以直接念字母“Z J Y”，连着念几个人也行（“ZJY LZH”），说“这几样”也算；撞首字母时按读音挑最像的或让你点选", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                androidx.compose.material3.Switch(useInitials, { v -> vm.updateSettings { it.copy(voiceInitials = v) } })
            }
            Spacer(Modifier.height(8.dp))
            Text("念到的同学登记为：", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 16.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(HwStatus.SUBMITTED, HwStatus.EXCELLENT, HwStatus.LATE, HwStatus.MISSING, HwStatus.LEAVE).forEach { s ->
                    Chip2("${s.emoji} ${s.label}", s == status, { status = s }, color = statusColor(s))
                }
            }
        }
        item {
            if (!keyboardMode) {
                MicPanel(listening, state, partial) { toggle() }
            } else {
                Column(Modifier.padding(16.dp)) {
                    if (!VoiceListener.available(ctx)) {
                        Text(
                            "本机没有系统语音识别服务。请点下面的输入框，用输入法自带的语音输入（搜狗、讯飞、百度、微信键盘上的🎤）连续念，停顿后自动登记。",
                            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                    } else {
                        Text("用输入法的语音输入连续念，停顿后自动登记；也可以直接打字。", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                    }
                    OutlinedTextField(
                        typed, { typed = it },
                        placeholder = { Text("例如：张三 李四 三十四号 49号") },
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                    )
                    TextButton(onClick = { typed = "" }) { Text("清空文字（已登记的不受影响）") }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("本次已登记 ${entries.size} 人", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("作业已交 ${sm.handedIn}/${sm.expected}", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
            }
            if (!Pinyin.available) {
                Text("提示：系统版本低于 Android 10，同音字只能按原字匹配。", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
        // 歧义待选
        items(pendings.toList(), key = { "p" + it.key }) { p ->
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(MaterialTheme.shapes.medium)
                    .background(statusColor(HwStatus.LATE).copy(alpha = 0.14f)).padding(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("听到「${p.heard}」，是哪位？", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = { pendings.remove(p) }, modifier = Modifier.size(28.dp)) { Icon(Icons.Filled.Close, "忽略") }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    p.candidates.forEach { id ->
                        val s = byId[id] ?: return@forEach
                        Chip2("${s.name} ${s.no}", false, { pendings.remove(p); dismissed.remove(id); register(id, p.heard) })
                    }
                }
            }
        }
        if (misses.isNotEmpty()) {
            item {
                Text(
                    "没听懂：" + misses.joinToString("  ·  "),
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
        }
        items(entries.toList(), key = { it.key }) { e ->
            val s = byId[e.studentId] ?: return@items
            val idx = entries.size - entries.indexOf(e)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp).clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("$idx", Modifier.width(30.dp), fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Avatar(s.name, size = 34.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.name, fontWeight = FontWeight.Bold)
                    Text("${s.no} · 听到：${e.heard}" + if (e.how.isNotEmpty()) " · ${e.how}" else "", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(e.status.label, color = statusColor(e.status), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                IconButton(onClick = {
                    vm.setStatus(a.id, listOf(e.studentId), e.prev)
                    entries.remove(e)
                    dismissed += e.studentId
                    kit.toast("已撤销 ${s.name}")
                }) { Icon(Icons.Filled.Close, "撤销", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        if (entries.isEmpty() && pendings.isEmpty()) {
            item {
                Text(
                    "依次念名字或学号即可，例如：\n“张三、李四、三十四号、49号……”\n念错了点右侧 ✕ 撤销。",
                    textAlign = TextAlign.Center, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                )
            }
        }
    }
}

@Composable
private fun MicPanel(listening: Boolean, state: VoiceListener.State, partial: String, onToggle: () -> Unit) {
    val t = rememberInfiniteTransition(label = "mic")
    val pulse by t.animateFloat(1f, 1.18f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "p")
    Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            if (listening) {
                Box(Modifier.size(132.dp).scale(pulse).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)))
            }
            Box(
                Modifier.size(104.dp).clip(CircleShape).background(if (listening) gradientBrush() else androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.surfaceVariant))
                    .clickable { onToggle() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (listening) Icons.Filled.Mic else Icons.Filled.MicOff, null, Modifier.size(48.dp), tint = if (listening) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            when {
                !listening -> "点麦克风开始，连续念名字或学号"
                state == VoiceListener.State.HEARING -> "正在听…"
                state == VoiceListener.State.ERROR -> "识别服务暂时出错，正在重试…"
                else -> "请念名字或学号（可连续念）"
            },
            fontWeight = FontWeight.Bold,
        )
        Text(
            partial.ifBlank { " " }, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 6.dp), textAlign = TextAlign.Center,
        )
    }
}
