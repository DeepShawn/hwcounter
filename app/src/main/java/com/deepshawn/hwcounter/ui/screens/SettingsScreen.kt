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
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.deepshawn.hwcounter.ui.components.MiuixPage
import com.deepshawn.hwcounter.update.UpdateSettings
import com.deepshawn.hwcounter.ui.components.group
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.extra.SuperDropdown
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlin.math.roundToInt
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
import androidx.compose.runtime.collectAsState
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
import com.deepshawn.hwcounter.data.autoPointsAll
import com.deepshawn.hwcounter.data.autoPointDetails
import com.deepshawn.hwcounter.data.missingText
import com.deepshawn.hwcounter.data.signed
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
fun SettingsScreen(vm: AppViewModel, data: AppData, section: String? = null) {
    val kit = LocalKit.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val s = data.settings
    var showKey by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var newSubject by remember { mutableStateOf("") }
    var confirmReset by remember { mutableStateOf(false) }
    var editBands by remember { mutableStateOf(false) }
    var models by remember { mutableStateOf<List<String>>(emptyList()) }
    var modelMenu by remember { mutableStateOf(false) }
    var loadingModels by remember { mutableStateOf(false) }

    val backup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { ctx.contentResolver.openOutputStream(uri)?.use { it.write(vm.exportBackup().toByteArray()) } }.isSuccess }
            kit.toast(if (ok) "备份完成" else "备份失败")
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

    val ui = com.deepshawn.hwcounter.ui.theme.LocalUiPrefs.current
    fun prefs(f: (com.deepshawn.hwcounter.ui.theme.UiPrefs) -> com.deepshawn.hwcounter.ui.theme.UiPrefs) = com.deepshawn.hwcounter.ui.theme.UiPrefsStore.update(ctx, f)
    val layouts = listOf("grid", "list", "seat", "group")
    val titles = mapOf(
        "account" to "账号与云同步", "scoring" to "作业计分", "exam" to "考试计分", "settle" to "结算规则", "subjects" to "科目与加减分理由",
        "recog" to "识别引擎与 AI", "look" to "外观与体验", "data" to "数据与备份", "update" to "软件更新",
    )
    MiuixPage(title = section?.let { titles[it] } ?: "设置", onBack = if (section != null) ({ kit.nav.pop() }) else null) {
        if (section == null) settingsIndex(vm, s, ui) { kit.nav.push(com.deepshawn.hwcounter.ui.Route.SettingsPage(it)) }
        when (section) {
            "account" -> {
                item(key = "cloud") { CloudSection(vm) }
                item(key = "accounts") {
                    val st by vm.sync.state.collectAsState()
                    if (st.email != null && st.member == null) Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        top.yukonga.miuix.kmp.basic.Card {
                            SuperArrow(title = "用户与邀请码", summary = "生成一次性邀请码、管理用户、发放模拟币（炒股模拟）", onClick = { kit.nav.push(com.deepshawn.hwcounter.ui.Route.Accounts) })
                        }
                    }
                }
            }
            "scoring" -> {
                group("计分规则", key = "rules") {
                    Column(Modifier.padding(16.dp)) {
                        val r = s.rules
                        RuleRow("初始分", r.initial, 10.0) { v -> rules { it.copy(initial = v) } }
                        RuleRow("已交", r.submitted) { v -> rules { it.copy(submitted = v) } }
                        RuleRow("优秀", r.excellent) { v -> rules { it.copy(excellent = v) } }
                        RuleRow("迟交", r.late) { v -> rules { it.copy(late = v) } }
                        if (!r.escalation.enabled) RuleRow("未交", r.missing) { v -> rules { it.copy(missing = v) } }
                        RuleRow("请假", r.leave) { v -> rules { it.copy(leave = v) } }
                        Text("修改后所有历史作业的自动计分会立即按新规则重算；已积分重置的同学从重置分数起算。", fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    }
                }
                group("未交连续递增扣分", key = "escalation") {
                    val e = s.rules.escalation
                    fun esc(f: (com.deepshawn.hwcounter.data.MissingEscalation) -> com.deepshawn.hwcounter.data.MissingEscalation) = rules { it.copy(escalation = f(it.escalation)) }
                    SuperSwitch(
                        checked = e.enabled, onCheckedChange = { v -> esc { it.copy(enabled = v) } }, title = "连续未交自动扣分",
                        summary = if (e.enabled) "${e.describe()}；关闭后每次未交固定 ${s.rules.missing.signed()}" else "关闭：每次未交固定 ${s.rules.missing.signed()}",
                    )
                    if (e.enabled) {
                        SuperDropdown(
                            items = listOf(
                                "① 正常加倍（${com.deepshawn.hwcounter.data.MissingEscalation.seq(e.base, 1)}）",
                                "② 第 2 次起加倍（${com.deepshawn.hwcounter.data.MissingEscalation.seq(e.base, 2)}）",
                                "③ 分段倍率（在 ① 或 ② 上分段乘倍率）",
                            ),
                            selectedIndex = (e.mode - 1).coerceIn(0, 2), title = "扣分方式",
                            summary = when (e.mode) {
                                1 -> "第 1 次 −${e.base.fmt()}，之后每次翻倍"
                                3 -> "在${com.deepshawn.hwcounter.data.MissingEscalation.modeLabelOf(e.shape)}的基础上按次数分段乘倍率，可自行调整"
                                else -> "第 1、2 次都 −${e.base.fmt()}，第 3 次起每次翻倍"
                            },
                            onSelectedIndexChange = { i -> esc { it.copy(mode = i + 1) } },
                        )
                        if (e.mode == 3) {
                            SuperDropdown(
                                items = listOf("① 正常加倍", "② 第 2 次起加倍"), selectedIndex = if (e.shape == 1) 0 else 1, title = "分段基于",
                                onSelectedIndexChange = { i -> esc { it.copy(tierBase = i + 1) } },
                            )
                            SuperDropdown(
                                items = listOf("连续未交次数", "本轮累计未交次数"), selectedIndex = if (e.tierBy == "total") 1 else 0, title = "分段按",
                                summary = if (e.tierBy == "total") "本轮第几次未交（交过也不清零，积分重置才清零）" else "连续第几次未交（交过一次就重新从第 1 次算）",
                                onSelectedIndexChange = { i -> esc { it.copy(tierBy = if (i == 1) "total" else "streak") } },
                            )
                        }
                        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                            if (e.mode == 3) {
                                Text("分段倍率", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                                val tiers = e.tiers.sortedBy { it.from }
                                fun setTiers(t: List<com.deepshawn.hwcounter.data.EscalationTier>) = esc { it.copy(tiers = t.sortedBy { x -> x.from }) }
                                tiers.forEachIndexed { i, t ->
                                    RuleRow("第 ${i + 1} 段：从第几次起", t.from.toDouble(), 1.0) { v ->
                                        val n = v.toInt()
                                        if (n >= 1 && tiers.withIndex().none { (j, x) -> j != i && x.from == n }) setTiers(tiers.mapIndexed { j, x -> if (j == i) x.copy(from = n) else x })
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.weight(1f)) {
                                            RuleRow("　倍率", t.factor, 0.5) { v -> if (v >= 0) setTiers(tiers.mapIndexed { j, x -> if (j == i) x.copy(factor = v) else x }) }
                                        }
                                        if (tiers.size > 1) TextButton(onClick = { setTiers(tiers.filterIndexed { j, _ -> j != i }) }) { Text("删除") }
                                    }
                                }
                                TextButton(onClick = {
                                    val last = tiers.lastOrNull()
                                    setTiers(tiers + com.deepshawn.hwcounter.data.EscalationTier((last?.from ?: 0) + 2, (last?.factor ?: 1.0) + 0.5))
                                }) { Text("＋ 添加一段") }
                                TextButton(onClick = { esc { it.copy(tiers = com.deepshawn.hwcounter.data.MissingEscalation.DEFAULT_TIERS) } }) { Text("恢复默认分段（第 1–2 次 ×1、第 3–4 次 ×1.5、第 5 次起 ×2）") }
                                Text("当前：${e.tiersText()}；连续未交依次扣 ${e.sample(k = 6)}", fontSize = 11.sp, lineHeight = 17.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = Modifier.padding(bottom = 6.dp))
                            }
                            RuleRow(if (e.shape == 1) "底数（第 1 次扣）" else "底数（第 1、2 次扣）", e.base) { v -> if (v >= 1) esc { it.copy(base = v) } }
                            RuleRow("累计超过几次后", e.heavyAfter.toDouble(), 1.0) { v -> if (v >= 1) esc { it.copy(heavyAfter = v.toInt()) } }
                            RuleRow("加重后的底数", e.heavyBase) { v -> if (v >= 1) esc { it.copy(heavyBase = v) } }
                            RuleRow("单次最多扣（0 不限）", e.cap, 1.0) { v -> if (v >= 0) esc { it.copy(cap = v) } }
                            SuperSwitch(
                                checked = e.gapBalance, onCheckedChange = { v -> esc { it.copy(gapBalance = v) } },
                                title = "间断未交平衡",
                                summary = if (e.gapBalance) "已开启：隔几次不交一次也会逐次加重。次数取“连续未交次数”和“最近 ${e.gapWindow} 次作业里的未交次数”中较大的（请假、未登记不算），间断未交最多按第 ${e.gapMax} 次算。例：隔一次不交一次，依次扣 ${gapExample(e)}"
                                else "已关闭：只看连续未交，交过一次就从第 1 次重新算（隔一次不交一次，每次都只扣 ${e.penalty(1, 1).fmt()}）",
                            )
                            if (e.gapBalance) {
                                RuleRow("看最近几次作业", e.gapWindow.toDouble(), 1.0) { v -> if (v >= 2 && v <= 30) esc { it.copy(gapWindow = v.toInt()) } }
                                RuleRow("间断最多按第几次算", e.gapMax.toDouble(), 1.0) { v -> if (v >= 1 && v <= 20) esc { it.copy(gapMax = v.toInt()) } }
                            }
                            Text(
                                (if (e.gapBalance) "· 请假、未登记不打断连续，也不计次；交过一次会打断连续，但间断未交平衡仍按最近 ${e.gapWindow} 次里的未交次数计\n" else "· 请假、未登记不打断连续，也不计次；只要交过一次（已交 / 优秀 / 迟交）就打断，重新从第 1 次算\n") +
                                    "· 本轮累计未交超过 ${e.heavyAfter} 次后，底数变为 ${e.heavyBase.fmt()}（${e.sample(e.heavyBase)}）\n" +
                                    "· 每次积分重置开始新一轮：连续次数和累计次数清零，底数回到 ${e.base.fmt()}\n" +
                                    (if (e.mode == 3) "· 分段倍率乘完后再按单次上限封顶；复审不合格、抄袭的倍数照常再乘\n" else "") +
                                    "· 修改后历史积分自动重算",
                                fontSize = 11.sp, lineHeight = 17.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }
                group("作业复审", key = "review") {
                    val rv = s.rules.review
                    SuperSwitch(
                        checked = rv.enabled, onCheckedChange = { v -> rules { it.copy(review = it.review.copy(enabled = v)) } }, title = "作业复审",
                        summary = "复审不合格：记一次未交（参与连续未交、累计未交），这一次扣分 ×${rv.multiplier.fmt()}；没有复审的默认合格",
                    )
                    if (rv.enabled) Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                        RuleRow("扣分倍数", rv.multiplier, 0.5) { v -> if (v >= 1) rules { it.copy(review = it.review.copy(multiplier = v)) } }
                        Text("在作业页长按同学，或多选后点「复审不合格」。改回其他状态即取消。修改后历史积分自动重算。", fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = Modifier.padding(top = 6.dp))
                    }
                }
                group("抄袭处罚", key = "plagiarism") {
                    val pg = s.rules.plagiarism
                    fun pl(f: (com.deepshawn.hwcounter.data.PlagiarismRules) -> com.deepshawn.hwcounter.data.PlagiarismRules) = rules { it.copy(plagiarism = f(it.plagiarism)) }
                    SuperSwitch(
                        checked = pg.enabled, onCheckedChange = { v -> pl { it.copy(enabled = v) } }, title = "抄袭处罚",
                        summary = "抄袭者和提供者都记一次未交（参与连续未交、累计未交），这一次扣分 ×${pg.multiplier.fmt()}；关闭后按普通未交扣分",
                    )
                    if (pg.enabled) {
                        SuperSwitch(
                            checked = pg.escalate, onCheckedChange = { v -> pl { it.copy(escalate = v) } }, title = "累犯逐次加重",
                            summary = "本轮第 2 次涉及抄袭再 ×2、第 3 次 ×3……最多 ×${pg.maxTimes}；每次积分重置重新计次",
                        )
                        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                            RuleRow("扣分倍数", pg.multiplier, 0.5) { v -> if (v >= 1) pl { it.copy(multiplier = v) } }
                            if (pg.escalate) RuleRow("累犯最多再乘", pg.maxTimes.toDouble(), 1.0) { v -> if (v >= 1) pl { it.copy(maxTimes = v.toInt()) } }
                            Text(
                                "例：连续未交第 1 次扣 2 分，抄袭第 1 次扣 ${(2 * pg.factor(1)).fmt()}、第 2 次扣 ${(2 * pg.factor(2)).fmt()}、第 3 次扣 ${(2 * pg.factor(3)).fmt()}。\n" +
                                    "在作业页长按同学（或多选后）点「抄袭」，再选出抄了谁的；改回其他状态即取消。统计 → 抄袭 可看关系图。抄袭扣分也计入小组对抗和组长奖惩。",
                                fontSize = 11.sp, lineHeight = 17.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }
                group("组长奖惩", key = "leader") {
                    val lr = s.rules.leader
                    fun ld(f: (com.deepshawn.hwcounter.data.LeaderRules) -> com.deepshawn.hwcounter.data.LeaderRules) = rules { it.copy(leader = f(it.leader)) }
                    SuperSwitch(
                        checked = lr.enabled, onCheckedChange = { v -> ld { it.copy(enabled = v) } }, title = "组长按小组扣分奖惩",
                        summary = "${lr.periodLabel}看一次组长所在小组的人均作业扣分，和各组平均比较，再看名次、比上一段、连续几段",
                    )
                    if (lr.enabled) {
                        val periods = listOf("assignment", "day", "week", "month")
                        SuperDropdown(
                            items = listOf("每次作业", "每天", "每周", "每月"), selectedIndex = periods.indexOf(lr.period).coerceAtLeast(2), title = "计算周期",
                            onSelectedIndexChange = { i -> ld { it.copy(period = periods[i]) } },
                        )
                        val modes = listOf("both", "plus", "minus")
                        SuperDropdown(
                            items = listOf("加分和扣分", "仅加分", "仅扣分"), selectedIndex = modes.indexOf(lr.mode).coerceAtLeast(0), title = "加减方式",
                            summary = when (lr.mode) { "plus" -> "只给组长加分，扣分的那一段记 0"; "minus" -> "只给组长扣分，加分的那一段记 0"; else -> "表现好加分、扣分多扣分" },
                            onSelectedIndexChange = { i -> ld { it.copy(mode = modes[i]) } },
                        )
                        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                            RuleRow("全组零扣分", lr.clean, 1.0) { v -> ld { it.copy(clean = v) } }
                            RuleRow("≤ 各组平均一半", lr.low, 1.0) { v -> ld { it.copy(low = v) } }
                            RuleRow("≤ 各组平均", lr.below, 1.0) { v -> ld { it.copy(below = v) } }
                            RuleRow("≤ 平均 1.5 倍", lr.above, 1.0) { v -> ld { it.copy(above = v) } }
                            RuleRow("≤ 平均 2 倍", lr.high, 1.0) { v -> ld { it.copy(high = v) } }
                            RuleRow("超过平均 2 倍", lr.veryHigh, 1.0) { v -> ld { it.copy(veryHigh = v) } }
                            RuleRow("人均扣分最少的组", lr.top, 1.0) { v -> ld { it.copy(top = v) } }
                            RuleRow("人均扣分最多的组", lr.bottom, 1.0) { v -> ld { it.copy(bottom = v) } }
                            RuleRow("比上一段明显减少", lr.progress, 1.0) { v -> ld { it.copy(progress = v) } }
                            RuleRow("比上一段明显增多", lr.regress, 1.0) { v -> ld { it.copy(regress = v) } }
                            RuleRow("连续每多一段 ±", lr.streakStep, 1.0) { v -> if (v >= 0) ld { it.copy(streakStep = v) } }
                            RuleRow("连续最多算几段", lr.streakMax.toDouble(), 1.0) { v -> if (v >= 0) ld { it.copy(streakMax = v.toInt()) } }
                            RuleRow("每段最多 ±（0 不限）", lr.cap, 1.0) { v -> if (v >= 0) ld { it.copy(cap = v) } }
                            Text(
                                "· 人均作业扣分 = 这一段组员作业自动扣分（未交递增、复审不合格翻倍、迟交）之和 ÷ 组员人数\n· 只对设置了组长、2 人以上的小组生效；这一段组员都请假或未登记时不计\n· 名次奖惩在 3 组以上且不并列时才有；“明显减少”指降 30% 以上，“明显增多”指升 50% 且多 2 分以上\n· 组长积分重置后重新开始计算；修改后历史积分自动重算",
                                fontSize = 11.sp, lineHeight = 17.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }
                group("小组对抗排名（整组加减分）", key = "groupRank") {
                    val gr = s.rules.groupRank
                    fun gk(f: (com.deepshawn.hwcounter.data.GroupRankRules) -> com.deepshawn.hwcounter.data.GroupRankRules) = rules { it.copy(groupRank = f(it.groupRank)) }
                    SuperSwitch(
                        checked = gr.enabled, onCheckedChange = { v -> gk { it.copy(enabled = v) } }, title = "按小组综合排名整组加减分",
                        summary = "${gr.periodLabel}给小组排一次名：第 1 名全组每人 ${gr.first.signed()}，第 2 名 ${gr.second.signed()}，最后一名 ${gr.last.signed()}",
                    )
                    if (gr.enabled) {
                        val periods = listOf("assignment", "day", "week", "month")
                        SuperDropdown(
                            items = listOf("每次作业", "每天", "每周", "每月"), selectedIndex = periods.indexOf(gr.period).coerceAtLeast(2), title = "排名周期",
                            onSelectedIndexChange = { i -> gk { it.copy(period = periods[i]) } },
                        )
                        val modes = listOf("both", "plus", "minus")
                        SuperDropdown(
                            items = listOf("加分和扣分", "仅加分", "仅扣分"), selectedIndex = modes.indexOf(gr.mode).coerceAtLeast(0), title = "加减方式",
                            summary = when (gr.mode) { "plus" -> "只给名次靠前的组加分，最后一名不扣"; "minus" -> "只给最后一名扣分，靠前的组不加"; else -> "靠前的组加分、最后一名扣分" },
                            onSelectedIndexChange = { i -> gk { it.copy(mode = modes[i]) } },
                        )
                        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                            RuleRow("第 1 名每人", gr.first, 1.0) { v -> gk { it.copy(first = v) } }
                            RuleRow("第 2 名每人", gr.second, 1.0) { v -> gk { it.copy(second = v) } }
                            RuleRow("第 3 名每人", gr.third, 1.0) { v -> gk { it.copy(third = v) } }
                            RuleRow("最后一名每人", gr.last, 1.0) { v -> gk { it.copy(last = v) } }
                            RuleRow("交齐率权重 %", gr.wRate * 100, 5.0) { v -> if (v >= 0) gk { it.copy(wRate = v / 100) } }
                            RuleRow("少扣分权重 %", gr.wDeduct * 100, 5.0) { v -> if (v >= 0) gk { it.copy(wDeduct = v / 100) } }
                            RuleRow("优秀率权重 %", gr.wExcellent * 100, 5.0) { v -> if (v >= 0) gk { it.copy(wExcellent = v / 100) } }
                            RuleRow("考试权重 %", gr.wExam * 100, 5.0) { v -> if (v >= 0) gk { it.copy(wExam = v / 100) } }
                            Text(
                                "· 综合分 = 交齐率 + 少扣分（人均作业扣分，扣分最多的组为 0、零扣分为 1）+ 优秀率 + 考试得分率，按权重加权；这一段没有考试或按每次作业排名时，考试权重按比例分给其他项\n· 综合分相同名次并列；最后一名只在 3 组以上时才有\n· 整组每位同学都加减（包括请假的同学）；每人积分重置后只算重置之后的段\n· 修改后历史积分自动重算",
                                fontSize = 11.sp, lineHeight = 17.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }
                group("信誉分加减分", key = "credit") {
                    val cr = s.rules.credit
                    fun ck(f: (com.deepshawn.hwcounter.data.CreditRules) -> com.deepshawn.hwcounter.data.CreditRules) = rules { it.copy(credit = f(it.credit)) }
                    val modes = com.deepshawn.hwcounter.data.CreditRules.MODES
                    val modeItems = modes.map { com.deepshawn.hwcounter.data.CreditRules.modeLabel(it) }
                    Text(
                        "信誉分 0–100，越优秀越高：交齐 25%、按时 15%、优秀 10%、连续交 10%、诚信（复审不合格、抄袭）20%、进步 5%、考试 15%。小组信誉分 = 组员平均。",
                        fontSize = 12.sp, lineHeight = 18.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    SuperDropdown(
                        items = modeItems, selectedIndex = modes.indexOf(cr.personal).coerceAtLeast(0), title = "个人信誉分",
                        summary = if (cr.personal == "none") "不计入积分" else "按个人信誉分${cr.periodLabel}给本人${com.deepshawn.hwcounter.data.CreditRules.modeLabel(cr.personal)}",
                        onSelectedIndexChange = { i -> ck { it.copy(personal = modes[i]) } },
                    )
                    SuperDropdown(
                        items = modeItems, selectedIndex = modes.indexOf(cr.group).coerceAtLeast(0), title = "小组信誉分",
                        summary = if (cr.group == "none") "不计入积分" else "按小组信誉分${cr.periodLabel}给全组每人${com.deepshawn.hwcounter.data.CreditRules.modeLabel(cr.group)}",
                        onSelectedIndexChange = { i -> ck { it.copy(group = modes[i]) } },
                    )
                    if (cr.active) {
                        val periods = listOf("assignment", "day", "week", "month")
                        SuperDropdown(
                            items = listOf("每次作业", "每天", "每周", "每月"), selectedIndex = periods.indexOf(cr.period).coerceAtLeast(2), title = "结算周期",
                            onSelectedIndexChange = { i -> ck { it.copy(period = periods[i]) } },
                        )
                        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                            RuleRow("优秀线（≥）", cr.excellentAt, 5.0) { v -> if (v in 0.0..100.0) ck { it.copy(excellentAt = v) } }
                            RuleRow("　优秀加分", cr.excellentPts, 0.5) { v -> ck { it.copy(excellentPts = v) } }
                            RuleRow("良好线（≥）", cr.goodAt, 5.0) { v -> if (v in 0.0..100.0) ck { it.copy(goodAt = v) } }
                            RuleRow("　良好加分", cr.goodPts, 0.5) { v -> ck { it.copy(goodPts = v) } }
                            RuleRow("偏低线（<）", cr.poorBelow, 5.0) { v -> if (v in 0.0..100.0) ck { it.copy(poorBelow = v) } }
                            RuleRow("　偏低扣分", cr.poorPts, 0.5) { v -> ck { it.copy(poorPts = v) } }
                            RuleRow("很差线（<）", cr.badBelow, 5.0) { v -> if (v in 0.0..100.0) ck { it.copy(badBelow = v) } }
                            RuleRow("　很差扣分", cr.badPts, 0.5) { v -> ck { it.copy(badPts = v) } }
                            Text(
                                "· 现在：${cr.bandsText()}，其余 0\n· 每段在这段最后一次作业时算信誉分；这一段有作业记录的同学（小组：有组员有记录）才计入\n· 只加不扣：扣分的段记 0；只扣不加：加分的段记 0；不计入：只显示信誉分，不影响积分\n· 每人积分重置后只算重置之后的段；修改后历史积分自动重算",
                                fontSize = 11.sp, lineHeight = 17.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }
                group("连续交作业奖励", key = "streakBonus") {
                    val b = s.rules.streakBonus
                    fun sb(f: (com.deepshawn.hwcounter.data.StreakBonus) -> com.deepshawn.hwcounter.data.StreakBonus) = rules { it.copy(streakBonus = f(it.streakBonus)) }
                    SuperSwitch(
                        checked = b.enabled, onCheckedChange = { v -> sb { it.copy(enabled = v) } }, title = "连续交满后每次加分",
                        summary = "本轮连续按时交满 ${b.after} 次后，之后每连续交一次额外 ${b.bonus.signed()}",
                    )
                    if (b.enabled) Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                        RuleRow("连续交满几次后", b.after.toDouble(), 1.0) { v -> if (v >= 0) sb { it.copy(after = v.toInt()) } }
                        RuleRow("之后每次额外加", b.bonus, 1.0) { v -> if (v >= 0) sb { it.copy(bonus = v) } }
                    }
                    if (b.enabled) SuperSwitch(checked = b.lateCounts, onCheckedChange = { v -> sb { it.copy(lateCounts = v) } }, title = "迟交也算连续交", summary = if (b.lateCounts) "迟交不打断连续，也能拿奖励" else "迟交会打断连续（请假、未登记不打断）")
                    SuperSwitch(
                        checked = s.rules.roundPoints, onCheckedChange = { v -> rules { it.copy(roundPoints = v) } }, title = "积分取整",
                        summary = "最终显示的积分取整：正分向上取整（如 98.5 显示 99），负分向下取整（如 −2.5 显示 −3）；每一笔加减分保留原值",
                    )
                    Text("每次积分重置后重新开始计连续次数。修改后历史积分自动重算。", fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp))
                }
                group("计分规则通知", key = "notice") {
                    val ctx2 = androidx.compose.ui.platform.LocalContext.current
                    top.yukonga.miuix.kmp.extra.SuperArrow(title = "导出计分规则通知 PDF", summary = "按当前全部计分规则生成一份通知，可发给学生和家长", onClick = {
                        vm.currentClass(data)?.let { c -> runCatching { com.deepshawn.hwcounter.ui.screens.shareRulesNotice(ctx2, c, s.rules) } }
                    })
                }
            }
            "exam" -> {
                group("考试计分", key = "examRules") {
                    val er = s.rules.exam
                    fun exam(f: (com.deepshawn.hwcounter.data.ExamRules) -> com.deepshawn.hwcounter.data.ExamRules) = rules { it.copy(exam = f(it.exam)) }
                    SuperSwitch(checked = er.enabled, onCheckedChange = { v -> exam { it.copy(enabled = v) } }, title = "考试按分数档自动加减积分", summary = "按得分率计算，150 分制的考试也适用；可在单场考试里关闭")
                    if (er.enabled) Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        er.bands.sortedByDescending { it.minPct }.forEach { b ->
                            RuleRow("${b.minPct.fmt()}%–${b.maxPct.fmt()}%", b.delta) { v -> exam { r -> r.copy(bands = r.bands.map { if (it == b) it.copy(delta = v) else it }) } }
                        }
                        RuleRow("缺考", er.absent) { v -> exam { it.copy(absent = v) } }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                            top.yukonga.miuix.kmp.basic.TextButton("编辑分数档", onClick = { editBands = true }, modifier = Modifier.weight(1f))
                            top.yukonga.miuix.kmp.basic.TextButton("恢复默认", onClick = { exam { com.deepshawn.hwcounter.data.ExamRules() } }, modifier = Modifier.weight(1f))
                        }
                        Text("得分率落在区间内（含下限，不含上限；最高档含满分）加减对应分数。", fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = Modifier.padding(bottom = 8.dp))
                    }
                }
            }
            "settle" -> {
                group("结算规则", key = "settlement") {
                    val st = s.rules.settlement
                    fun sr(f: (com.deepshawn.hwcounter.data.SettlementRules) -> com.deepshawn.hwcounter.data.SettlementRules) = rules { it.copy(settlement = f(it.settlement)) }
                    Column(Modifier.padding(16.dp)) {
                        Text("按本轮交齐度（已交 + 优秀 + 迟交 ÷ 应交，请假不算应交）结算", fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        RuleRow("全勤（100% 无迟交）", st.perfect, 1.0) { v -> sr { it.copy(perfect = v) } }
                        RuleRow("≥95%", st.r95, 1.0) { v -> sr { it.copy(r95 = v) } }
                        RuleRow("90%–95%", st.r90, 1.0) { v -> sr { it.copy(r90 = v) } }
                        RuleRow("80%–90%", st.r80, 1.0) { v -> sr { it.copy(r80 = v) } }
                        RuleRow("70%–80%", st.r70, 1.0) { v -> sr { it.copy(r70 = v) } }
                        RuleRow("<70%", st.below, 1.0) { v -> sr { it.copy(below = v) } }
                        RuleRow("缺交满几次", st.manyMissing.toDouble(), 1.0) { v -> if (v >= 0) sr { it.copy(manyMissing = v.toInt()) } }
                        RuleRow("  再加减", st.manyMissingDelta, 1.0) { v -> sr { it.copy(manyMissingDelta = v) } }
                        RuleRow("连续缺交满几次", st.streakMissing.toDouble(), 1.0) { v -> if (v >= 0) sr { it.copy(streakMissing = v.toInt()) } }
                        RuleRow("  再加减", st.streakMissingDelta, 1.0) { v -> sr { it.copy(streakMissingDelta = v) } }
                        RuleRow("进步奖（提高百分点）", st.progressPts * 100, 5.0) { v -> if (v >= 0) sr { it.copy(progressPts = v / 100) } }
                        RuleRow("  进步奖分数", st.progressDelta, 1.0) { v -> sr { it.copy(progressDelta = v) } }
                        RuleRow("小组第一每人", st.groupDelta, 1.0) { v -> sr { it.copy(groupDelta = v) } }
                        RuleRow("应交少于几次不结算", st.minExpected.toDouble(), 1.0) { v -> if (v >= 0) sr { it.copy(minExpected = v.toInt()) } }
                    }
                    SuperSwitch(checked = st.withMonthlyReset, onCheckedChange = { v -> sr { it.copy(withMonthlyReset = v) } }, title = "每月定期重置前自动结算", summary = "开启了“每月定期重置”的班级，每次重置前先结算上一轮，结算分计入新一轮")
                    SuperSwitch(checked = st.spreadEnabled, onCheckedChange = { v -> sr { it.copy(spreadEnabled = v) } }, title = "结算时扩大区分度", summary = "按综合表现指数（交齐、按时、优秀、缺交、考试、进步）加减分，全班合计约为 0")
                    if (st.spreadEnabled) Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                        RuleRow("力度（每 1 个标准差）", st.spreadK, 1.0) { v -> if (v >= 0) sr { it.copy(spreadK = v) } }
                        RuleRow("单人最多 ±", st.spreadCap, 1.0) { v -> if (v >= 0) sr { it.copy(spreadCap = v) } }
                        RuleRow("交齐率权重 %", st.wHand * 100, 5.0) { v -> if (v >= 0) sr { it.copy(wHand = v / 100) } }
                        RuleRow("按时率权重 %", st.wOnTime * 100, 5.0) { v -> if (v >= 0) sr { it.copy(wOnTime = v / 100) } }
                        RuleRow("优秀率权重 %", st.wExcellent * 100, 5.0) { v -> if (v >= 0) sr { it.copy(wExcellent = v / 100) } }
                        RuleRow("缺交度权重 %", st.wMissing * 100, 5.0) { v -> if (v >= 0) sr { it.copy(wMissing = v / 100) } }
                        RuleRow("考试权重 %", st.wExam * 100, 5.0) { v -> if (v >= 0) sr { it.copy(wExam = v / 100) } }
                        RuleRow("进步权重 %", st.wProgress * 100, 5.0) { v -> if (v >= 0) sr { it.copy(wProgress = v / 100) } }
                        Text("各项先做小样本校正（+${st.prior.fmt()} 次全班平均），再换算成全班标准分（限制在 ±2.5）后加权；没有考试成绩时考试权重按比例分给其他项。", fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    }
                }
                group("课代表", key = "repSettle") {
                    Column(Modifier.padding(16.dp)) {
                        RuleRow("课代表每次结算加", s.rules.settlement.repDelta, 1.0) { v -> rules { it.copy(settlement = it.settlement.copy(repDelta = v)) } }
                        Text("在名单里打开同学的档案设置课代表；课代表可以同时是组长，两边的分都算。", fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
            "subjects" -> {
                item(key = "subjects") {
                    Column {
                SmallTitle("科目与加减分理由")
                AppCard(Modifier.padding(horizontal = 12.dp)) {
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

                    }
                }
            }
            "recog" -> {
                group("识别引擎", key = "recog") {
                    SuperDropdown(
                        items = listOf("本地离线", "AI 大模型", "本地 + AI"), selectedIndex = s.recogMode, title = "识别方式",
                        summary = when (s.recogMode) {
                            RecogMode.LOCAL -> "内置 ML Kit 中文 OCR + 二维码，完全离线、免费、隐私安全"
                            RecogMode.AI -> "照片发送给你配置的多模态大模型，能理解更复杂的场景并数出本数"
                            else -> "先本地识别，再用 AI 补漏并数本数，准确率最高"
                        },
                        onSelectedIndexChange = { i -> vm.updateSettings { it.copy(recogMode = i) } },
                    )
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text("学号最少位数：${s.minIdLength}", fontSize = 15.sp)
                        Spacer(Modifier.height(8.dp))
                        top.yukonga.miuix.kmp.basic.Slider(value = s.minIdLength.toFloat(), onValueChange = { v -> vm.updateSettings { it.copy(minIdLength = v.roundToInt()) } }, valueRange = 2f..10f, steps = 7)
                    }
                    SuperSwitch(checked = s.fuzzy, onCheckedChange = { v -> vm.updateSettings { it.copy(fuzzy = v) } }, title = "模糊匹配", summary = "允许 1 位误识（唯一匹配时才采纳）")
                    SuperSwitch(checked = s.matchNames, onCheckedChange = { v -> vm.updateSettings { it.copy(matchNames = v) } }, title = "姓名匹配", summary = "封面上写了姓名也能识别")
                }
                item(key = "ai") {
                    Column {
                if (s.recogMode != RecogMode.LOCAL || s.ai.apiKey.isNotBlank()) {
                    SmallTitle("AI 大模型")
                    AppCard(Modifier.padding(horizontal = 12.dp)) {
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
                                        text = { Text(m + if (looksVision(m)) "（识图）" else "") },
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
                                        kit.toast("连接成功：${r.take(30)}")
                                    } catch (e: Exception) {
                                        kit.toast("${e.message?.take(120)}")
                                    }
                                    testing = false
                                }
                            },
                        ) { Text(if (testing) "测试中…" else "测试连接") }
                        Text("API Key 仅保存在本机。照片只在识别时发送给你配置的服务商。", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                    }
                }

                    }
                }
            }
            "look" -> {
                item(key = "themes") {
                    SmallTitle("主题风格")
                    FlowRow(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Palettes.forEach { p ->
                            val sel = p.key == s.theme
                            Column(
                                Modifier.width(80.dp).clip(RoundedCornerShape(16.dp))
                                    .background(MiuixTheme.colorScheme.surfaceContainer)
                                    .then(if (sel) Modifier.border(2.dp, MiuixTheme.colorScheme.primary, RoundedCornerShape(16.dp)) else Modifier)
                                    .clickable { vm.updateSettings { it.copy(theme = p.key) }; kit.haptic() }
                                    .padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box(Modifier.fillMaxWidth().height(36.dp).clip(RoundedCornerShape(10.dp)).background(Brush.linearGradient(p.gradient)), contentAlignment = Alignment.Center) {
                                    com.deepshawn.hwcounter.ui.components.LineIcon(p.emoji, Color.White, size = 18.dp)
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(p.name, fontSize = 11.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
                            }
                        }
                    }
                }
                group(null, key = "appearance") {
                    SuperDropdown(items = listOf("跟随系统", "浅色", "深色"), selectedIndex = s.dark, title = "深色模式", onSelectedIndexChange = { i -> vm.updateSettings { it.copy(dark = i) } })
                    SuperDropdown(items = listOf("方正", "圆润", "超圆"), selectedIndex = s.shape, title = "圆角风格", onSelectedIndexChange = { i -> vm.updateSettings { it.copy(shape = i) } })
                    SuperDropdown(items = listOf("卡片", "列表", "座位", "分组"), selectedIndex = layouts.indexOf(s.layout).coerceAtLeast(0), title = "学生排版", onSelectedIndexChange = { i -> vm.updateSettings { it.copy(layout = layouts[i]) } })
                    SuperSwitch(checked = s.compact, onCheckedChange = { v -> vm.updateSettings { it.copy(compact = v) } }, title = "紧凑模式", summary = "卡片更小，一屏显示更多学生")
                }
                group("流畅度与动效（仅本机）", key = "perf") {
                    SuperSwitch(
                        checked = ui.perf, onCheckedChange = { v -> prefs { it.copy(perf = v, lowPerf = if (v) false else it.lowPerf) }; kit.toast(if (v) "已开启性能模式" else "已关闭性能模式") },
                        title = "性能模式",
                        summary = if (ui.perf) "已开启：弹簧翻页带纵深、卡片缩放浮现、选项滑块拉伸回弹、玻璃更强的模糊与折射（纵深 + 色散）、流动的柔光背景、屏幕最高刷新率。更耗电"
                        else "默认关闭。开启后动效和液态玻璃都开到最好（需要较新的手机，更耗电）",
                    )
                    SuperSwitch(
                        checked = ui.animations, onCheckedChange = { v -> prefs { it.copy(animations = v) } },
                        title = "界面动效", enabled = !ui.lowPerf && !ui.perf,
                        summary = if (ui.lowPerf) "低性能模式下始终关闭" else if (ui.perf) "性能模式下始终开启" else if (ui.animations) "页面弹簧滑入、卡片依次浮现、数字滚动、按压回弹、列表重排、选项滑块等动画" else "默认关闭：所有操作即点即达。开启后有页面滑入、卡片浮现、数字滚动、按压回弹等动画",
                    )
                    SuperSwitch(
                        checked = ui.glass, onCheckedChange = { v -> prefs { it.copy(glass = v) } },
                        title = "液态玻璃", enabled = !ui.lowPerf && !ui.perf && android.os.Build.VERSION.SDK_INT >= 31,
                        summary = when {
                            android.os.Build.VERSION.SDK_INT < 31 -> "需要 Android 12 及以上，当前使用半透明效果"
                            ui.lowPerf -> "低性能模式下已关闭"
                            ui.perf -> "性能模式下始终开启（增强）"
                            android.os.Build.VERSION.SDK_INT < 33 -> "底栏与顶栏背景模糊（折射需要 Android 13）"
                            else -> "底栏与顶栏背景模糊 + 边缘折射高光"
                        },
                    )
                    SuperSwitch(
                        checked = ui.lowPerf, onCheckedChange = { v -> prefs { it.copy(lowPerf = v, perf = if (v) false else it.perf) }; kit.toast(if (v) "已开启低性能模式" else "已关闭低性能模式") },
                        title = "低性能模式",
                        summary = "适合老旧或卡顿的设备：关闭液态玻璃、动效、平滑圆角、阴影、回弹和彩带",
                    )
                }
                group("交互反馈", key = "feedback") {
                    SuperSwitch(checked = s.haptic, onCheckedChange = { v -> vm.updateSettings { it.copy(haptic = v) } }, title = "震动反馈")
                    SuperSwitch(checked = s.sound, onCheckedChange = { v -> vm.updateSettings { it.copy(sound = v) } }, title = "提示音", summary = "扫描识别成功时“嘀”一声")
                    SuperSwitch(checked = s.confetti, onCheckedChange = { v -> vm.updateSettings { it.copy(confetti = v) } }, title = "彩带庆祝", summary = if (ui.lowPerf) "低性能模式下不显示" else "全员交齐时放彩带")
                }
            }
            "data" -> {
                group("数据", key = "data") {
                    SuperArrow(title = "备份全部数据", summary = "导出为 JSON 文件", onClick = { backup.launch("作业通备份_${com.deepshawn.hwcounter.export.Exporter.fileStamp()}.json") })
                    SuperArrow(title = "从备份恢复", summary = "数据保存在本机，卸载前请先备份", onClick = { restore.launch(arrayOf("application/json", "*/*")) })
                    SuperArrow(
                        title = "清空所有数据", onClick = { confirmReset = true },
                        titleColor = top.yukonga.miuix.kmp.basic.BasicComponentDefaults.titleColor(color = MiuixTheme.colorScheme.error),
                    )
                }
            }
            "update" -> {
                group("软件更新", key = "update") { UpdateSettings() }
            }
            else -> {}
        }
        if (section == null) item(key = "about") {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(Brush.linearGradient(listOf(Color(0xFF3482FF), Color(0xFF6E5BFF)))), contentAlignment = Alignment.Center) {
                    com.deepshawn.hwcounter.ui.components.LineIcon("📚", Color.White, size = 28.dp)
                }
                Spacer(Modifier.height(8.dp))
                Text("作业通 v${BuildConfig.VERSION_NAME}", fontWeight = FontWeight.Bold)
                Text("拍照识别 · 实时扫描 · 数本数 · 积分 · 统计", fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
        }
    }

    if (editBands) {
        var text by remember { mutableStateOf(s.rules.exam.bands.sortedByDescending { it.minPct }.joinToString("\n") { "${it.minPct.fmt()} ${it.maxPct.fmt()} ${it.delta.fmt()}" }) }
        val parsed = text.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { l ->
            val n = l.split(Regex("[\\s,，~～\\-–]+")).mapNotNull { it.trim().removeSuffix("%").toDoubleOrNull() }
            if (n.size == 3 && n[0] < n[1]) com.deepshawn.hwcounter.data.ExamBand(n[0], n[1], n[2]) else null
        }
        com.deepshawn.hwcounter.ui.components.HyperDialog(
            true, "编辑分数档", { editBands = false },
            summary = "每行一档：下限% 上限% 加减分，例如“90 100 2”表示得分率 90%–100% 加 2 分，“0 60 -1”表示不及格减 1 分。",
            confirmText = "保存", confirmEnabled = parsed.isNotEmpty() && parsed.all { it != null },
            onConfirm = { rules { it.copy(exam = it.exam.copy(bands = parsed.filterNotNull())) }; editBands = false },
        ) {
            top.yukonga.miuix.kmp.basic.TextField(text, { text = it }, minLines = 4, maxLines = 8, modifier = Modifier.fillMaxWidth())
        }
    }
    if (confirmReset) ConfirmDialog("清空所有数据", "将删除所有班级、名单和记录（设置保留）。建议先备份。", "清空", { confirmReset = false }) {
        vm.resetAll(); kit.undoable("已清空所有数据")
    }
}

/** 设置首页：账号卡片 + 分组入口（点进二级页面） */
private fun androidx.compose.foundation.lazy.LazyListScope.settingsIndex(vm: AppViewModel, s: com.deepshawn.hwcounter.data.Settings, ui: com.deepshawn.hwcounter.ui.theme.UiPrefs, open: (String) -> Unit) {
    item(key = "idx-account") {
        val st by vm.sync.state.collectAsStateWithLifecycle()
        val colors = MiuixTheme.colorScheme
        com.deepshawn.hwcounter.ui.components.HCard(Modifier.padding(horizontal = 12.dp, vertical = 6.dp).fillMaxWidth(), onClick = { open("account") }) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF3482FF), Color(0xFF6E5BFF)))), contentAlignment = Alignment.Center) {
                    Icon(if (st.email == null) Icons.Filled.CloudOff else Icons.Filled.Cloud, null, tint = Color.White)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(st.email ?: "未登录 · 预览模式", fontWeight = FontWeight.SemiBold, fontSize = 17.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text(
                        if (st.email == null) "登录后才能修改 · 三端自动同步" else "云端版本 v${st.version} · 改动自动同步",
                        fontSize = 12.sp, color = colors.onSurfaceVariantSummary, maxLines = 2,
                    )
                }
                Text("›", fontSize = 22.sp, color = colors.onSurfaceVariantSummary)
            }
        }
    }
    val r = s.rules
    group("计分与结算", key = "idx-score") {
        SuperArrow(
            title = "作业计分",
            summary = "初始 ${r.initial.fmt()} 分 · " + (if (r.escalation.enabled) "未交${r.escalation.modeLabel}" else "未交 ${r.missing.signed()}") +
                " · 复审" + (if (r.review.enabled) "不合格 ×${r.review.multiplier.fmt()}" else "已关闭") + (if (r.plagiarism.enabled) " · 抄袭 ×${r.plagiarism.multiplier.fmt()}${if (r.plagiarism.escalate) "起累犯加重" else ""}" else "") + (if (r.leader.enabled) " · 组长${r.leader.periodLabel}奖惩${if (r.leader.mode != "both") "（" + com.deepshawn.hwcounter.data.modeLabelOf(r.leader.mode) + "）" else ""}" else "") + (if (r.groupRank.enabled) " · 小组${r.groupRank.periodLabel}排名${if (r.groupRank.mode != "both") "（" + com.deepshawn.hwcounter.data.modeLabelOf(r.groupRank.mode) + "）" else ""}" else "") + (if (r.credit.active) " · 信誉分（个人${com.deepshawn.hwcounter.data.CreditRules.modeLabel(r.credit.personal)}、小组${com.deepshawn.hwcounter.data.CreditRules.modeLabel(r.credit.group)}）" else "") + if (r.streakBonus.enabled) " · 连续交奖励" else "",
            onClick = { open("scoring") },
        )
        SuperArrow(title = "考试计分", summary = if (r.exam.enabled) "按得分率分 ${r.exam.bands.size} 档自动加减积分" else "已关闭", onClick = { open("exam") })
        SuperArrow(title = "结算规则", summary = "按本轮交齐度结算" + (if (r.settlement.spreadEnabled) " · 扩大区分度" else "") + " · 课代表 ${r.settlement.repDelta.signed()}" + if (r.settlement.withMonthlyReset) " · 定期重置前自动结算" else "", onClick = { open("settle") })
        SuperArrow(title = "科目与加减分理由", summary = "${s.subjects.size} 个科目 · ${s.quickReasons.size} 个常用理由", onClick = { open("subjects") })
    }
    group("识别", key = "idx-recog") {
        SuperArrow(
            title = "识别引擎与 AI",
            summary = when (s.recogMode) { RecogMode.LOCAL -> "本地离线识别"; RecogMode.AI -> "AI 大模型 · ${s.ai.model}"; else -> "本地 + AI · ${s.ai.model}" } + " · 学号至少 ${s.minIdLength} 位",
            onClick = { open("recog") },
        )
    }
    group("外观与体验", key = "idx-look") {
        SuperArrow(
            title = "主题与显示",
            summary = com.deepshawn.hwcounter.ui.theme.paletteOf(s.theme).name + " · " + listOf("深色跟随系统", "浅色", "深色")[s.dark.coerceIn(0, 2)] +
                " · 动效" + (if (ui.motion) "开" else "关") + if (ui.lowPerf) " · 低性能模式" else if (ui.perf) " · 性能模式" else "",
            onClick = { open("look") },
        )
    }
    group("数据与更新", key = "idx-data") {
        SuperArrow(title = "数据与备份", summary = "备份、恢复、清空数据", onClick = { open("data") })
        SuperArrow(title = "软件更新", summary = "当前 v${BuildConfig.VERSION_NAME}", onClick = { open("update") })
    }
    group("帮助", key = "idx-help") {
        val ctx = androidx.compose.ui.platform.LocalContext.current
        SuperArrow(title = "新手引导", summary = "重新查看主要功能介绍", onClick = { com.deepshawn.hwcounter.ui.theme.UiPrefsStore.update(ctx) { it.copy(onboarded = false) } })
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

/** “隔一次不交一次”时依次扣的分（前 5 次未交），说明间断未交平衡的效果 */
private fun gapExample(e: com.deepshawn.hwcounter.data.MissingEscalation): String {
    val c = com.deepshawn.hwcounter.data.SchoolClass(
        id = "x", name = "x", students = listOf(com.deepshawn.hwcounter.data.Student(id = "s", no = "1", name = "x")),
        assignments = (0 until 10).map { k -> com.deepshawn.hwcounter.data.Assignment(id = "a$k", title = "", createdAt = k + 1L, statuses = mapOf("s" to if (k % 2 == 0) com.deepshawn.hwcounter.data.HwStatus.MISSING else com.deepshawn.hwcounter.data.HwStatus.SUBMITTED)) },
    )
    val p = c.autoPointDetails(com.deepshawn.hwcounter.data.ScoreRules(escalation = e, streakBonus = com.deepshawn.hwcounter.data.StreakBonus(enabled = false)), "s")
    return (0 until 10 step 2).joinToString("、") { (-(p["a$it"]?.points ?: 0.0)).fmt() } + "…"
}
