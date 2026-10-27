@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.deepshawn.hwcounter.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepshawn.hwcounter.BuildConfig
import com.deepshawn.hwcounter.data.AiConfig
import com.deepshawn.hwcounter.data.AppData
import com.deepshawn.hwcounter.data.AppViewModel
import com.deepshawn.hwcounter.data.RecogMode
import com.deepshawn.hwcounter.data.ScoreRules
import com.deepshawn.hwcounter.data.fmt
import com.deepshawn.hwcounter.recog.AiRecognizer
import com.deepshawn.hwcounter.ui.LocalKit
import com.deepshawn.hwcounter.ui.components.AppCard
import com.deepshawn.hwcounter.ui.components.Chip2
import com.deepshawn.hwcounter.ui.components.GradientHeader
import com.deepshawn.hwcounter.ui.components.SectionTitle
import com.deepshawn.hwcounter.ui.theme.Palettes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class AiPreset(val name: String, val provider: String, val url: String, val model: String, val vision: Boolean = true)

private val aiPresets = listOf(
    AiPreset("DeepSeek视觉", "openai", "https://api.deepseek.com/v1", "deepseek-v4-flash-vision-exp"),
    AiPreset("DeepSeek文本", "openai", "https://api.deepseek.com/v1", "deepseek-chat", vision = false),
    AiPreset("通义千问VL", "openai", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-vl-max"),
    AiPreset("豆包视觉", "openai", "https://ark.cn-beijing.volces.com/api/v3", "doubao-1.5-vision-pro-32k"),
    AiPreset("智谱GLM-4V", "openai", "https://open.bigmodel.cn/api/paas/v4", "glm-4v-plus"),
    AiPreset("Kimi视觉", "openai", "https://api.moonshot.cn/v1", "moonshot-v1-8k-vision-preview"),
    AiPreset("硅基流动", "openai", "https://api.siliconflow.cn/v1", "Qwen/Qwen2.5-VL-72B-Instruct"),
    AiPreset("OpenAI", "openai", "https://api.openai.com/v1", "gpt-4o"),
    AiPreset("Claude", "anthropic", "https://api.anthropic.com", "claude-sonnet-4-5"),
    AiPreset("本地Ollama", "openai", "http://192.168.1.100:11434/v1", "qwen2.5vl"),
)

@Composable
fun SettingsScreen(vm: AppViewModel, data: AppData) {
    val kit = LocalKit.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val s = data.settings
    var showKey by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var newSubject by remember { mutableStateOf("") }
    var confirmReset by remember { mutableStateOf(false) }
    var models by remember { mutableStateOf<List<String>>(emptyList()) }
    var modelMenu by remember { mutableStateOf(false) }
    var loadingModels by remember { mutableStateOf(false) }

    val backup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { ctx.contentResolver.openOutputStream(uri)?.use { it.write(vm.exportBackup().toByteArray()) } }.isSuccess }
            kit.toast(if (ok) "✅ 备份完成" else "备份失败")
        }
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val text = withContext(Dispatchers.IO) { runCatching { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } }.getOrNull() }
            val ok = text != null && vm.restoreBackup(text)
            if (ok) kit.undoable("已从备份恢复") else kit.toast("恢复失败：文件格式不正确")
        }
    }
    fun ai(f: (AiConfig) -> AiConfig) = vm.updateSettings { it.copy(ai = f(it.ai)) }
    fun rules(f: (ScoreRules) -> ScoreRules) = vm.updateSettings { it.copy(rules = f(it.rules)) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        GradientHeader {
            Text("设置", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text("打造你的专属作业通", color = Color.White.copy(alpha = 0.85f))
        }

        CloudSection(vm)

        // ---------------- 外观 ----------------
        SectionTitle("🎨 主题风格", Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
        FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Palettes.forEach { p ->
                val sel = p.key == s.theme
                Column(
                    Modifier.width(96.dp).clip(MaterialTheme.shapes.medium)
                        .then(if (sel) Modifier.border(2.5.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium) else Modifier)
                        .clickable { vm.updateSettings { it.copy(theme = p.key) }; kit.haptic() }
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.fillMaxWidth().height(44.dp).clip(MaterialTheme.shapes.small).background(Brush.linearGradient(p.gradient)), contentAlignment = Alignment.Center) {
                        Text(p.emoji, fontSize = 20.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(p.name, fontSize = 12.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
        AppCard(Modifier.padding(16.dp)) {
            Text("深色模式", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            Segmented(listOf("跟随系统", "浅色", "深色"), s.dark) { i -> vm.updateSettings { it.copy(dark = i) } }
            Spacer(Modifier.height(14.dp))
            Text("圆角风格", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            Segmented(listOf("方正", "圆润", "超圆"), s.shape) { i -> vm.updateSettings { it.copy(shape = i) } }
            Spacer(Modifier.height(14.dp))
            Text("学生排版", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            val layouts = listOf("grid", "list", "seat", "group")
            Segmented(listOf("卡片", "列表", "座位", "分组"), layouts.indexOf(s.layout).coerceAtLeast(0)) { i -> vm.updateSettings { it.copy(layout = layouts[i]) } }
            SwitchRow("紧凑模式", "卡片更小，一屏显示更多学生", s.compact) { v -> vm.updateSettings { it.copy(compact = v) } }
        }

        // ---------------- 计分 ----------------
        SectionTitle("🧮 计分规则", Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
        AppCard(Modifier.padding(horizontal = 16.dp)) {
            val r = s.rules
            RuleRow("初始分", r.initial, 10.0) { v -> rules { it.copy(initial = v) } }
            RuleRow("✅ 已交", r.submitted) { v -> rules { it.copy(submitted = v) } }
            RuleRow("🌟 优秀", r.excellent) { v -> rules { it.copy(excellent = v) } }
            RuleRow("🐢 迟交", r.late) { v -> rules { it.copy(late = v) } }
            RuleRow("❌ 未交", r.missing) { v -> rules { it.copy(missing = v) } }
            RuleRow("🏖️ 请假", r.leave) { v -> rules { it.copy(leave = v) } }
            Text("修改后所有历史作业的自动计分会立即按新规则重算", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // ---------------- 识别 ----------------
        SectionTitle("🔍 识别引擎", Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
        AppCard(Modifier.padding(horizontal = 16.dp)) {
            Segmented(listOf("本地离线", "AI 大模型", "本地+AI"), s.recogMode) { i -> vm.updateSettings { it.copy(recogMode = i) } }
            Text(
                when (s.recogMode) {
                    RecogMode.LOCAL -> "使用内置的 ML Kit 中文 OCR + 二维码识别，完全离线、免费、隐私安全。"
                    RecogMode.AI -> "把照片发送给你配置的多模态大模型，能理解更复杂的场景并数出本数。"
                    else -> "先本地识别，再用 AI 补漏并数本数，准确率最高。"
                },
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text("学号最少位数：${s.minIdLength}", style = MaterialTheme.typography.labelLarge)
            Slider(s.minIdLength.toFloat(), { v -> vm.updateSettings { it.copy(minIdLength = v.toInt()) } }, valueRange = 2f..10f, steps = 7)
            SwitchRow("模糊匹配", "允许 1 位误识（唯一匹配时才采纳）", s.fuzzy) { v -> vm.updateSettings { it.copy(fuzzy = v) } }
            SwitchRow("姓名匹配", "封面上写了姓名也能识别", s.matchNames) { v -> vm.updateSettings { it.copy(matchNames = v) } }
        }

        if (s.recogMode != RecogMode.LOCAL || s.ai.apiKey.isNotBlank()) {
            SectionTitle("🤖 AI 大模型", Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
            AppCard(Modifier.padding(horizontal = 16.dp)) {
                Text("快速预设", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    aiPresets.forEach { p ->
                        Chip2(p.name, s.ai.baseUrl == p.url && s.ai.model == p.model, { ai { it.copy(provider = p.provider, baseUrl = p.url, model = p.model, vision = p.vision) } })
                    }
                }
                Spacer(Modifier.height(12.dp))
                Segmented(listOf("OpenAI 兼容协议", "Anthropic 协议"), if (s.ai.provider == "anthropic") 1 else 0) { i -> ai { it.copy(provider = if (i == 1) "anthropic" else "openai") } }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(s.ai.baseUrl, { v -> ai { it.copy(baseUrl = v.trim()) } }, label = { Text("接口地址 Base URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Box {
                    OutlinedTextField(
                        s.ai.model, { v -> ai { it.copy(model = v.trim()) } }, label = { Text("模型名称") }, singleLine = true,
                        trailingIcon = {
                            TextButton(enabled = !loadingModels, onClick = {
                                loadingModels = true
                                scope.launch {
                                    try {
                                        models = AiRecognizer.listModels(s.ai)
                                        if (models.isEmpty()) kit.toast("没有获取到模型") else modelMenu = true
                                    } catch (e: Exception) {
                                        kit.toast("获取失败：${e.message?.take(100)}")
                                    }
                                    loadingModels = false
                                }
                            }) { Text(if (loadingModels) "…" else "列表") }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    DropdownMenu(modelMenu, { modelMenu = false }) {
                        models.forEach { m ->
                            DropdownMenuItem(
                                text = { Text(m + if (looksVision(m)) "  🖼️" else "") },
                                onClick = { ai { it.copy(model = m, vision = looksVision(m) || it.vision && m == it.model) }; modelMenu = false },
                            )
                        }
                    }
                }
                SwitchRow(
                    "模型支持看图（多模态）",
                    if (s.ai.vision) "直接把照片发给模型识别、数本数" else "纯文本模式：本地 OCR 出文字后交给模型纠错对名单，照片不离开手机",
                    s.ai.vision,
                ) { v -> ai { it.copy(vision = v) } }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    s.ai.apiKey, { v -> ai { it.copy(apiKey = v.trim()) } }, label = { Text("API Key") }, singleLine = true,
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = { IconButton(onClick = { showKey = !showKey }) { Icon(if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                FilledTonalButton(
                    enabled = !testing,
                    onClick = {
                        testing = true
                        scope.launch {
                            try {
                                val r = AiRecognizer.ping(s.ai)
                                kit.toast("✅ 连接成功：${r.take(30)}")
                            } catch (e: Exception) {
                                kit.toast("❌ ${e.message?.take(120)}")
                            }
                            testing = false
                        }
                    },
                ) { Text(if (testing) "测试中…" else "测试连接") }
                Text("API Key 仅保存在本机。照片只在识别时发送给你配置的服务商。", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            }
        }

        // ---------------- 科目 / 理由 ----------------
        SectionTitle("📚 科目", Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
        AppCard(Modifier.padding(horizontal = 16.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                s.subjects.forEach { sub ->
                    Chip2("$sub ✕", false, { vm.updateSettings { it.copy(subjects = it.subjects - sub) } })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                OutlinedTextField(newSubject, { newSubject = it }, placeholder = { Text("新科目 / 加分理由") }, singleLine = true, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = {
                    val t = newSubject.trim()
                    if (t.isNotEmpty()) vm.updateSettings { it.copy(subjects = (it.subjects + t).distinct()) }
                    newSubject = ""
                }) { Text("加科目") }
            }
            Spacer(Modifier.height(10.dp))
            Text("快捷加减分理由", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                s.quickReasons.forEach { r -> Chip2("$r ✕", false, { vm.updateSettings { it.copy(quickReasons = it.quickReasons - r) } }) }
                Chip2("+ 用上面输入框添加", true, {
                    val t = newSubject.trim()
                    if (t.isNotEmpty()) vm.updateSettings { it.copy(quickReasons = (it.quickReasons + t).distinct()) }
                    newSubject = ""
                })
            }
        }

        // ---------------- 反馈 ----------------
        SectionTitle("✨ 交互反馈", Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
        AppCard(Modifier.padding(horizontal = 16.dp)) {
            SwitchRow("震动反馈", null, s.haptic) { v -> vm.updateSettings { it.copy(haptic = v) } }
            SwitchRow("提示音", "扫描识别成功时“嘀”一声", s.sound) { v -> vm.updateSettings { it.copy(sound = v) } }
            SwitchRow("彩带庆祝", "全员交齐时放彩带 🎉", s.confetti) { v -> vm.updateSettings { it.copy(confetti = v) } }
        }

        // ---------------- 数据 ----------------
        SectionTitle("💾 数据", Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
        AppCard(Modifier.padding(horizontal = 16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { backup.launch("作业通备份_${com.deepshawn.hwcounter.export.Exporter.fileStamp()}.json") }, Modifier.weight(1f)) { Text("备份全部数据") }
                FilledTonalButton(onClick = { restore.launch(arrayOf("application/json", "*/*")) }, Modifier.weight(1f)) { Text("从备份恢复") }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { confirmReset = true }, Modifier.fillMaxWidth()) { Text("清空所有数据", color = MaterialTheme.colorScheme.error) }
            Text("数据保存在本机，卸载前请先备份。", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        }

        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(56.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF12C2A0), Color(0xFF2F5BEA)))), contentAlignment = Alignment.Center) {
                Text("📚", fontSize = 26.sp)
            }
            Spacer(Modifier.height(8.dp))
            Text("作业通 v${BuildConfig.VERSION_NAME}", fontWeight = FontWeight.Bold)
            Text("拍照识别 · 实时扫描 · 数本数 · 积分 · 统计", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (confirmReset) ConfirmDialog("清空所有数据", "将删除所有班级、名单和记录（设置保留）。建议先备份。", "清空", { confirmReset = false }) {
        vm.resetAll(); kit.undoable("已清空所有数据")
    }
}

/** 根据模型名粗略判断是否多模态，仅用于列表提示与默认开关 */
private fun looksVision(model: String): Boolean {
    val m = model.lowercase()
    return listOf("vision", "-vl", "vl-", "4v", "gpt-4o", "gpt-4.1", "gpt-5", "claude", "gemini", "omni", "qvq", "llava").any { it in m }
}

@Composable
private fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, t ->
            SegmentedButton(selected = i == selected, onClick = { onSelect(i) }, shape = SegmentedButtonDefaults.itemShape(i, options.size)) {
                Text(t, maxLines = 1, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            if (sub != null) Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onChange)
    }
}

@Composable
private fun RuleRow(label: String, value: Double, step: Double = 0.5, onChange: (Double) -> Unit) {
    var text by remember(value) { mutableStateOf(value.fmt()) }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        OutlinedButton(onClick = { onChange(value - step) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp), modifier = Modifier.size(40.dp)) { Text("−") }
        OutlinedTextField(
            text, { t -> text = t; t.toDoubleOrNull()?.takeIf { !t.endsWith(".") }?.let(onChange) },
            singleLine = true, modifier = Modifier.width(80.dp).padding(horizontal = 6.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
        )
        OutlinedButton(onClick = { onChange(value + step) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp), modifier = Modifier.size(40.dp)) { Text("+") }
    }
}
