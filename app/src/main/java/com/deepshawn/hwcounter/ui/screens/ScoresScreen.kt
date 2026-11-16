@file:OptIn(ExperimentalFoundationApi::class)

package com.deepshawn.hwcounter.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepshawn.hwcounter.data.AppData
import com.deepshawn.hwcounter.data.AppViewModel
import com.deepshawn.hwcounter.data.HwStatus
import com.deepshawn.hwcounter.data.ScoreRecharge
import com.deepshawn.hwcounter.data.Student
import com.deepshawn.hwcounter.data.StudentStat
import com.deepshawn.hwcounter.data.compareStudentNumbers
import com.deepshawn.hwcounter.data.fmt
import com.deepshawn.hwcounter.data.latestRecharges
import com.deepshawn.hwcounter.data.isAuto
import com.deepshawn.hwcounter.data.next
import com.deepshawn.hwcounter.data.recharge
import com.deepshawn.hwcounter.data.startLabel
import com.deepshawn.hwcounter.data.startOfDay
import com.deepshawn.hwcounter.data.fmtDay
import com.deepshawn.hwcounter.data.signed
import com.deepshawn.hwcounter.data.studentStats
import com.deepshawn.hwcounter.ui.LocalKit
import com.deepshawn.hwcounter.ui.Route
import com.deepshawn.hwcounter.ui.components.ActionSheet
import com.deepshawn.hwcounter.ui.components.Avatar
import com.deepshawn.hwcounter.ui.components.EmptyState
import com.deepshawn.hwcounter.ui.components.HyperDialog
import com.deepshawn.hwcounter.ui.components.LocalBottomInset
import com.deepshawn.hwcounter.ui.components.MiuixPage
import com.deepshawn.hwcounter.ui.components.NumberDialog
import com.deepshawn.hwcounter.ui.components.SheetAction
import com.deepshawn.hwcounter.ui.components.glass
import com.deepshawn.hwcounter.ui.components.gradientBrush
import com.deepshawn.hwcounter.ui.components.listCell
import com.deepshawn.hwcounter.ui.components.statusColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.extra.SuperBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ScoresScreen(vm: AppViewModel, data: AppData) {
    val kit = LocalKit.current
    val c = vm.currentClass(data) ?: run { EmptyState("🏆", "还没有班级", ""); return }
    val rules = data.settings.rules
    val readOnly by vm.readOnly.collectAsState()
    val colors = MiuixTheme.colorScheme
    var mode by rememberSaveable { mutableIntStateOf(0) }
    val ctx = LocalContext.current
    val preferences = remember(ctx) { ctx.getSharedPreferences("display", android.content.Context.MODE_PRIVATE) }
    var byNumber by rememberSaveable { mutableStateOf(preferences.getBoolean("scoresSortByNo", false)) }
    var selecting by remember(c.id, readOnly) { mutableStateOf(false) }
    var selected by remember(c.id, readOnly) { mutableStateOf(setOf<String>()) }
    var scoreFor by remember(c.id, readOnly) { mutableStateOf<List<String>?>(null) }
    var menu by remember { mutableStateOf(false) }
    var rechargeFor by remember(c.id, readOnly) { mutableStateOf<List<String>?>(null) }
    var history by remember { mutableStateOf(false) }
    var editBase by remember(c.id, readOnly) { mutableStateOf(false) }
    var editAuto by remember(c.id, readOnly) { mutableStateOf(false) }
    val stats = remember(c, rules) { c.studentStats(rules).sortedByDescending { it.score } }
    val personalStats = remember(stats, byNumber) { if (byNumber) stats.sortedWith { x, y -> compareStudentNumbers(x.student.no, y.student.no) } else stats }
    val ranks = remember(stats) { stats.mapIndexed { i, st -> st.student.id to i + 1 }.toMap() }
    // 全班同一次重置时不必逐行标注，只标出单独重置过的同学
    val recharged = remember(c) { c.latestRecharges().let { m -> if (m.size == c.students.size && m.values.map { it.id }.distinct().size == 1) emptyMap() else m } }
    val selectedIds = c.students.filter { it.id in selected }.map { it.id }
    fun exitSelect() { selecting = false; selected = emptySet() }

    MiuixPage(
        title = "积分",
        swipe = if (selecting) null else Triple(3, mode) { mode = it },
        largeTitle = "积分榜",
        extraBottom = if (selecting) 150.dp else 0.dp,
        actions = {
            if (!readOnly && mode == 0 && c.students.isNotEmpty()) TextButton(
                if (selecting) "完成" else "多选", onClick = { if (selecting) exitSelect() else selecting = true },
                minHeight = 34.dp, insideMargin = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                colors = if (selecting) ButtonDefaults.textButtonColorsPrimary() else ButtonDefaults.textButtonColors(),
            )
            IconButton(onClick = { menu = true }, modifier = Modifier.padding(start = 4.dp, end = 16.dp)) {
                Icon(Icons.Filled.MoreVert, "更多", tint = colors.onSurface)
            }
        },
        floating = {
            if (selecting && !readOnly) SelectionBar(
                count = selectedIds.size,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = LocalBottomInset.current),
                onAll = { selected = c.students.map { it.id }.toSet() },
                onClear = { selected = emptySet() },
                onCancel = { exitSelect() },
                onScore = { scoreFor = selectedIds },
                onRecharge = { rechargeFor = selectedIds },
            )
        },
    ) {
        item(key = "summary") {
            Column(Modifier.padding(horizontal = 12.dp)) {
                ScoreHero(stats, rules.initial, rules, c.recharges.size) { kit.nav.push(Route.StudentDetail(it)) }
                c.autoReset?.takeIf { it.enabled }?.let { ar ->
                    Text(
                        "🗓️ 每月 ${ar.day} 号自动重置为 ${ar.target.fmt()} 分 · 下次 ${fmtDay(ar.next())}",
                        fontSize = 12.sp, color = colors.onSurfaceVariantSummary, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).combinedClickable(onClick = { if (!readOnly) editAuto = true }),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
        item(key = "tabs") {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                com.deepshawn.hwcounter.ui.components.HTabs(listOf("个人榜", "小组榜", "催交榜"), mode, { mode = it; exitSelect() })
                if (mode == 0) com.deepshawn.hwcounter.ui.components.HTabs(listOf("按积分", "按学号"), if (byNumber) 1 else 0, {
                    byNumber = it == 1; preferences.edit().putBoolean("scoresSortByNo", byNumber).apply()
                })
                Text(
                    when {
                        mode != 0 -> if (mode == 1) "按小组平均分排名，点小组给全组加减分" else "按未交、迟交次数排列"
                        selecting -> "点学生选择或取消"
                        byNumber -> "学号从小到大 · 行首为积分名次"
                        readOnly -> "长按查看个人详情"
                        else -> "点学生加减分 · 长按查看详情"
                    },
                    fontSize = 12.sp, color = colors.onSurfaceVariantSummary, modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
        when (mode) {
            0 -> itemsIndexed(personalStats, key = { _, s -> s.student.id }) { i, st ->
                RankRow(
                    Modifier.listCell(i, personalStats.size), ranks.getValue(st.student.id), st, selecting, st.student.id in selected,
                    valueText = st.score.fmt(),
                    sub = "${st.student.no} · 交${st.submitted} · 迟${st.late} · 缺${st.missing}" + if (recharged.containsKey(st.student.id)) " · 已重置" else "",
                    onClick = {
                        if (selecting) selected = if (st.student.id in selected) selected - st.student.id else selected + st.student.id
                        else if (readOnly) kit.nav.push(Route.StudentDetail(st.student.id))
                        else scoreFor = listOf(st.student.id)
                    },
                    onLong = { kit.nav.push(Route.StudentDetail(st.student.id)) },
                )
            }
            1 -> {
                val groups = stats.groupBy { it.student.group.ifBlank { "未分组" } }
                    .map { (g, l) -> Triple(g, l, l.sumOf { it.score } / l.size) }
                    .sortedByDescending { it.third }
                itemsIndexed(groups, key = { _, g -> "g" + g.first }) { i, (g, l, avg) ->
                    GroupRow(i + 1, g, l, avg) { if (!readOnly) scoreFor = l.map { it.student.id } }
                }
            }
            else -> {
                val list = stats.filter { it.missing + it.late > 0 }.sortedWith(compareByDescending<StudentStat> { it.missing }.thenByDescending { it.late })
                if (list.isEmpty()) item { EmptyState("🎉", "没有欠交记录", "全班都很自觉！") }
                itemsIndexed(list, key = { _, s -> "m" + s.student.id }) { i, st ->
                    RankRow(
                        Modifier.listCell(i, list.size), i + 1, st, false, false, valueText = "${st.missing}次",
                        sub = "迟交 ${st.late} 次 · 积分 ${st.score.fmt()}", warn = true,
                        onClick = { kit.nav.push(Route.StudentDetail(st.student.id)) }, onLong = {},
                    )
                }
            }
        }
    }

    ActionSheet(
        show = menu, title = "积分操作", onDismiss = { menu = false },
        actions = buildList {
            if (!readOnly) {
                add(SheetAction("批量加减分", "按小组、作业、考试、进步之星等快速选人，可逐人设分值", enabled = c.students.isNotEmpty()) { exitSelect(); kit.nav.push(Route.BatchScore()) })
                add(SheetAction("全班积分重置", "只重置积分，保留全部历史记录", enabled = c.students.isNotEmpty()) { exitSelect(); rechargeFor = c.students.map { it.id } })
                add(SheetAction("每月定期重置", c.autoReset?.takeIf { it.enabled }?.let { "已开启：每月 ${it.day} 号重置为 ${it.target.fmt()} 分" } ?: "未开启：每月自动重置全班积分") { editAuto = true })
                add(SheetAction("全班 +1", "全班表现好", enabled = c.students.isNotEmpty()) {
                    vm.addScore(c.students.map { it.id }, 1.0, "全班表现好"); kit.celebrate(); kit.undoable("全班 +1 🎉")
                })
                add(SheetAction("修改基准分", "当前 ${rules.initial.fmt()} 分，适用于所有班级") { editBase = true })
            }
            add(SheetAction("重置记录", if (c.recharges.isEmpty()) "暂无" else "共 ${c.recharges.size} 次", enabled = c.recharges.isNotEmpty()) { history = true })
            add(SheetAction("修改计分规则", "已交 / 优秀 / 迟交 / 未交 / 请假") { kit.nav.tab(Route.Settings) })
        },
    )

    scoreFor?.takeIf { !readOnly }?.let { ids ->
        val targets = c.students.filter { it.id in ids }
        if (targets.isNotEmpty()) ScoreDialog(targets, data.settings.quickReasons, { scoreFor = null }) { d, r ->
            vm.addScore(targets.map { it.id }, d, r)
            kit.haptic(true)
            if (d > 0) kit.beep()
            kit.undoable(if (targets.size == 1) "${targets[0].name} ${d.signed()}" else "${targets.size} 人 ${d.signed()}")
            exitSelect()
        }
    }
    NumberDialog(
        show = editBase && !readOnly, title = "修改基准分", initial = rules.initial,
        summary = "适用于所有班级。只修改计分基准，历史加减分继续计入，未交次数和记录保留。已重置的同学以重置分数为基准，不受影响。",
        onDismiss = { editBase = false },
    ) { value ->
        vm.update { it.copy(settings = it.settings.copy(rules = it.settings.rules.copy(initial = value))) }
        editBase = false; kit.undoable("基准分已改为 ${value.fmt()}，历史记录已保留")
    }
    RechargeDialog(
        targets = rechargeFor?.takeIf { !readOnly }?.let { ids -> c.students.filter { it.id in ids } }.orEmpty(),
        scores = stats.associate { it.student.id to it.score },
        initial = rules.initial,
        assignments = remember(c.assignments) { c.assignments.sortedByDescending { it.createdAt } },
        preview = { ids, value, at, aid ->
            c.recharge(rules, ids, value, effectiveAt = at, assignment = c.assignments.firstOrNull { it.id == aid })
                .studentStats(rules).associate { it.student.id to it.score }
        },
        onDismiss = { rechargeFor = null },
    ) { targets, value, note, at, aid ->
        vm.rechargeScores(targets.map { it.id }, value, note, at, aid)
        rechargeFor = null; exitSelect()
        kit.haptic(true)
        val start = when {
            aid != null -> "，从「${c.assignments.firstOrNull { it.id == aid }?.title.orEmpty()}」起计分"
            at != null -> "，从 ${fmtDay(at)} 起计分"
            else -> ""
        }
        kit.undoable("已为 ${targets.size} 人重置为 ${value.fmt()} 分$start，历史记录已保留")
    }
    AutoResetDialog(editAuto && !readOnly, c.autoReset, rules.initial, onDismiss = { editAuto = false }) { ar ->
        vm.setAutoReset(ar); editAuto = false
        kit.toast(if (ar.enabled) "已开启：每月 ${ar.day} 号重置为 ${ar.target.fmt()} 分，下次 ${fmtDay(ar.next())}" else "已关闭每月定期重置")
    }
    RechargeHistorySheet(history, c.recharges, c.students, readOnly, onDismiss = { history = false }) { r ->
        vm.removeRecharge(r.id); kit.undoable("已撤销 ${fmtStamp(r.time)} 的重置")
    }
}

/** 每月定期重置设置 */
@Composable
private fun AutoResetDialog(show: Boolean, current: com.deepshawn.hwcounter.data.AutoReset?, initial: Double, onDismiss: () -> Unit, onSave: (com.deepshawn.hwcounter.data.AutoReset) -> Unit) {
    val colors = MiuixTheme.colorScheme
    var on by remember(show) { mutableStateOf(current?.enabled ?: true) }
    var text by remember(show) { mutableStateOf((current?.target ?: initial).fmt()) }
    var day by remember(show) { mutableIntStateOf(current?.day ?: 1) }
    var note by remember(show) { mutableStateOf(current?.note ?: "每月定期重置") }
    val value = text.trim().toDoubleOrNull()?.takeIf { it.isFinite() }
    val preview = com.deepshawn.hwcounter.data.AutoReset(enabled = true, target = value ?: 0.0, day = day, since = if (current?.enabled == true) current.since else System.currentTimeMillis())
    HyperDialog(
        show = show, title = "每月定期重置", onDismiss = onDismiss,
        summary = "每月指定日期 0 点起，全班积分自动重置为设定分数（打开 App 时补上）。和手动重置一样只重置积分、保留全部记录，可以单独撤销某个月。",
        confirmText = "保存", confirmEnabled = value != null || !on,
        onConfirm = {
            val keepSince = current?.enabled == true && on
            onSave(com.deepshawn.hwcounter.data.AutoReset(on, value ?: current?.target ?: initial, day, note.trim().ifBlank { "每月定期重置" }, if (keepSince) current!!.since else System.currentTimeMillis(), current?.skipped.orEmpty()))
        },
    ) {
        com.deepshawn.hwcounter.ui.components.HTabs(listOf("开启", "关闭"), if (on) 0 else 1, { on = it == 0 })
        if (on) {
            Spacer(Modifier.height(12.dp))
            Text("每月几号", fontSize = 13.sp, color = colors.onSurfaceVariantSummary, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton("−", onClick = { day = if (day <= 1) 28 else day - 1 }, minWidth = 48.dp)
                Text("$day 号", Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                TextButton("+", onClick = { day = if (day >= 28) 1 else day + 1 }, minWidth = 48.dp)
            }
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(1, 10, 15, 20).forEach { d -> com.deepshawn.hwcounter.ui.components.Chip2("$d 号", day == d, { day = d }, Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(10.dp))
            TextField(text, { text = it }, label = "重置为（分）", singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            TextField(note, { note = it }, label = "备注", singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Text("下次重置：${fmtDay(preview.next())} 0 点" + if (current?.skipped?.isNotEmpty() == true) " · 已撤销 ${current.skipped.size} 个月" else "", fontSize = 12.sp, color = colors.primary)
        }
    }
}

private val stampFmt = SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.CHINA)
fun fmtStamp(t: Long): String = stampFmt.format(Date(t))

/**
 * 积分重置：设定分数、备注和生效时间（立即 / 从某次作业起 / 从某一天起），
 * 实时预览每人重置后的积分。
 */
@Composable
private fun RechargeDialog(
    targets: List<Student>, scores: Map<String, Double>, initial: Double,
    assignments: List<com.deepshawn.hwcounter.data.Assignment>,
    preview: (List<String>, Double, Long?, String?) -> Map<String, Double>,
    onDismiss: () -> Unit, onConfirm: (List<Student>, Double, String, Long?, String?) -> Unit,
) {
    val show = targets.isNotEmpty()
    var text by remember(show) { mutableStateOf(initial.fmt()) }
    var note by remember(show) { mutableStateOf("") }
    var when_ by remember(show) { mutableIntStateOf(0) }
    var assignmentId by remember(show) { mutableStateOf(assignments.firstOrNull()?.id) }
    var day by remember(show) { mutableStateOf(startOfDay(System.currentTimeMillis())) }
    var pickDay by remember { mutableStateOf(false) }
    val value = text.trim().toDoubleOrNull()?.takeIf { it.isFinite() }
    val at: Long? = if (when_ == 2) day else null
    val aid: String? = if (when_ == 1) assignmentId else null
    val after = remember(value, at, aid, targets) { if (value == null || !show) emptyMap() else preview(targets.map { it.id }, value, at, aid) }
    val colors = MiuixTheme.colorScheme
    val shortFmt = remember { SimpleDateFormat("M月d日", Locale.CHINA) }
    HyperDialog(
        show = show, title = "积分重置 · ${targets.size} 人", onDismiss = onDismiss,
        summary = "只重置积分并留下重置记录。原有加减分、作业状态、备注和未交次数全部保留，可随时查看和撤销。",
        confirmText = "确认重置", confirmEnabled = value != null && (when_ != 1 || aid != null),
        onConfirm = { value?.let { onConfirm(targets, it, note, at, aid) } },
    ) {
        TextField(text, { text = it }, label = "重置为（分）", singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        TextField(note, { note = it }, label = "备注（可选，如：期中重新计分）", singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        Text("从什么时候起重新计分", fontSize = 13.sp, color = colors.onSurfaceVariantSummary, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        com.deepshawn.hwcounter.ui.components.HTabs(listOf("立即", "从某次作业起", "从某一天起"), when_, { when_ = it })
        Spacer(Modifier.height(8.dp))
        when (when_) {
            0 -> Text("现在起：之后布置的作业和之后的加减分在重置分数上累计。", fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
            1 -> if (assignments.isEmpty()) Text("还没有作业", fontSize = 12.sp, color = colors.onSurfaceVariantSummary) else {
                Text("所选作业（含）及之后的作业、以及这次作业布置之后的加减分计入；之前的不计入。", fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
                Spacer(Modifier.height(6.dp))
                Column(Modifier.fillMaxWidth().heightIn(max = 168.dp).clip(RoundedCornerShape(14.dp)).background(colors.secondaryContainer).verticalScroll(rememberScrollState())) {
                    assignments.forEach { a ->
                        val sel = a.id == assignmentId
                        Row(
                            Modifier.fillMaxWidth().background(if (sel) colors.primary.copy(alpha = 0.14f) else Color.Transparent)
                                .combinedClickable(onClick = { assignmentId = a.id }).padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(shortFmt.format(Date(a.createdAt)), fontSize = 13.sp, color = colors.onSurfaceVariantSummary, modifier = Modifier.width(56.dp))
                            Text("${a.subject} ${a.title}".trim(), fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
                            if (sel) Icon(Icons.Filled.Check, null, Modifier.size(16.dp), tint = colors.primary)
                        }
                    }
                }
            }
            else -> {
                Text("这一天 0 点起布置的作业和加减分计入；之前的不计入。", fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton("‹", onClick = { day = startOfDay(day - 12 * 3600_000L) }, minWidth = 44.dp)
                    TextButton(fmtDay(day), onClick = { pickDay = true }, modifier = Modifier.weight(1f))
                    TextButton("›", onClick = { day = startOfDay(day + 36 * 3600_000L) }, minWidth = 44.dp)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Column(
            Modifier.fillMaxWidth().heightIn(max = 160.dp).clip(RoundedCornerShape(14.dp)).background(colors.secondaryContainer)
                .verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text("当前 → 重置后", fontSize = 11.sp, color = colors.onSurfaceVariantSummary)
            targets.forEach { s ->
                val before = scores[s.id] ?: initial
                Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${s.no} ${s.name}", Modifier.weight(1f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(before.fmt(), fontSize = 13.sp, color = colors.onSurfaceVariantSummary)
                    Text("  →  ", fontSize = 13.sp, color = colors.onSurfaceVariantSummary)
                    Text(after[s.id]?.fmt() ?: "—", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.primary)
                }
            }
        }
    }
    if (pickDay) {
        val state = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = day + java.util.TimeZone.getDefault().getOffset(day))
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { pickDay = false },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    // DatePicker 返回 UTC 当天 0 点，换算成本地当天 0 点
                    state.selectedDateMillis?.let { utc -> day = startOfDay(utc - java.util.TimeZone.getDefault().getOffset(utc) + 12 * 3600_000L) }
                    pickDay = false
                }) { androidx.compose.material3.Text("确定") }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { pickDay = false }) { androidx.compose.material3.Text("取消") } },
        ) { androidx.compose.material3.DatePicker(state) }
    }
}

@Composable
private fun RechargeHistorySheet(show: Boolean, recharges: List<ScoreRecharge>, students: List<Student>, readOnly: Boolean, onDismiss: () -> Unit, onUndo: (ScoreRecharge) -> Unit) {
    val colors = MiuixTheme.colorScheme
    val names = remember(students) { students.associate { it.id to it.name } }
    var confirm by remember { mutableStateOf<ScoreRecharge?>(null) }
    SuperBottomSheet(show = show, title = "积分重置记录", onDismissRequest = onDismiss) {
        Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (recharges.isEmpty()) Text("暂无重置记录", color = colors.onSurfaceVariantSummary)
            recharges.sortedWith(compareByDescending<ScoreRecharge> { it.start }.thenByDescending { it.time }).forEach { r ->
                Card {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("重置为 ${r.target.fmt()} 分", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                            if (r.isAuto()) Text("每月定期", fontSize = 11.sp, color = colors.primary, modifier = Modifier.padding(start = 8.dp).clip(CircleShape).background(colors.primary.copy(alpha = 0.12f)).padding(horizontal = 7.dp, vertical = 2.dp))
                            Spacer(Modifier.weight(1f))
                            Text("${r.studentIds.size} 人", color = colors.primary, fontWeight = FontWeight.SemiBold)
                        }
                        Text(r.startLabel(::fmtDay) + " · 操作于 " + fmtStamp(r.time) + if (r.note.isNotBlank()) " · ${r.note}" else "", fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            r.studentIds.take(30).joinToString("、") { "${names[it] ?: "已删除"}（原${(r.before[it] ?: 0.0).fmt()}）" } + if (r.studentIds.size > 30) " 等 ${r.studentIds.size} 人" else "",
                            fontSize = 13.sp, color = colors.onSurfaceSecondary,
                        )
                        if (!readOnly) TextButton("撤销这次重置", onClick = { confirm = r }, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        }
    }
    HyperDialog(
        show = confirm != null, title = "撤销重置？", onDismiss = { confirm = null },
        summary = "删除这条重置记录后，这些同学的积分恢复按之前的记录计算。其他数据不受影响。" + if (confirm?.isAuto() == true) "这个月的定期重置不会再自动补回。" else "",
        confirmText = "撤销", danger = true,
        onConfirm = { confirm?.let(onUndo); confirm = null },
    )
}

/** 顶部概览：平均 / 最高 / 最低 / 重置次数 + 前三名 */
@Composable
private fun ScoreHero(stats: List<StudentStat>, base: Double, rules: com.deepshawn.hwcounter.data.ScoreRules, recharges: Int, onClick: (String) -> Unit) {
    val colors = MiuixTheme.colorScheme
    val avg = if (stats.isEmpty()) 0.0 else stats.sumOf { it.score } / stats.size
    com.deepshawn.hwcounter.ui.components.HCard {
        Row(Modifier.padding(top = 16.dp, bottom = 4.dp)) {
            HeroNumber("平均分", avg.fmt(), Modifier.weight(1f))
            HeroNumber("最高", (stats.maxOfOrNull { it.score } ?: 0.0).fmt(), Modifier.weight(1f))
            HeroNumber("最低", (stats.minOfOrNull { it.score } ?: 0.0).fmt(), Modifier.weight(1f))
            HeroNumber("重置", "$recharges 次", Modifier.weight(1f))
        }
        Text(
            "基准 ${base.fmt()} · " + (if (rules.escalation.enabled) "未交连续 −${rules.escalation.base.fmt()}、−${(rules.escalation.base * rules.escalation.base).fmt()}、−${(rules.escalation.base * rules.escalation.base * rules.escalation.base).fmt()}…" else "未交 ${rules.missing.signed()}") + " · 迟交 ${rules.late.signed()} · 优秀 ${rules.excellent.signed()}" + if (rules.streakBonus.enabled) " · 连续交满 ${rules.streakBonus.after} 次后每次 +${rules.streakBonus.bonus.fmt()}" else "",
            color = colors.onSurfaceVariantSummary, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        if (stats.size >= 3) {
            Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(0.5.dp).background(colors.dividerLine))
            Podium(stats.take(3), onClick)
        }
    }
}

@Composable
private fun HeroNumber(label: String, value: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = MiuixTheme.colorScheme.onSurface, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(label, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 12.sp)
    }
}

/** 前三名：第一名居中稍大，带奖牌 */
@Composable
private fun Podium(top: List<StudentStat>, onClick: (String) -> Unit) {
    val colors = MiuixTheme.colorScheme
    val medals = listOf("🥇", "🥈", "🥉")
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
        listOf(1, 0, 2).forEach { idx ->
            val st = top[idx]
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).combinedClickable(onClick = { onClick(st.student.id) }).padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    Avatar(st.student.name, size = if (idx == 0) 54.dp else 44.dp)
                    Text(medals[idx], fontSize = if (idx == 0) 20.sp else 17.sp, modifier = Modifier.padding(start = 2.dp))
                }
                Spacer(Modifier.height(6.dp))
                Text(st.student.name, fontWeight = FontWeight.Medium, fontSize = 14.sp, maxLines = 1)
                Text(st.score.fmt(), fontWeight = FontWeight.SemiBold, fontSize = if (idx == 0) 18.sp else 16.sp, color = colors.primary)
            }
        }
    }
}

/** 多选时的悬浮操作栏（液态玻璃） */
@Composable
private fun SelectionBar(count: Int, modifier: Modifier, onAll: () -> Unit, onClear: () -> Unit, onCancel: () -> Unit, onScore: () -> Unit, onRecharge: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    Column(
        modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp).widthIn(max = 560.dp).fillMaxWidth()
            .glass(RoundedCornerShape(26.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("已选 $count 人", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 6.dp), color = colors.onSurface)
            TextButton("全选", onClick = onAll, minHeight = 34.dp, insideMargin = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp))
            Spacer(Modifier.width(6.dp))
            TextButton("清空", onClick = onClear, minHeight = 34.dp, insideMargin = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp))
            Spacer(Modifier.width(6.dp))
            TextButton("取消", onClick = onCancel, minHeight = 34.dp, insideMargin = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TextButton("批量加减分", onClick = onScore, enabled = count > 0, modifier = Modifier.weight(1f), colors = ButtonDefaults.textButtonColorsPrimary())
            TextButton("积分重置", onClick = onRecharge, enabled = count > 0, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun RankRow(
    modifier: Modifier, rank: Int, st: StudentStat, selecting: Boolean, sel: Boolean, valueText: String, sub: String,
    warn: Boolean = false, onClick: () -> Unit, onLong: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier
            .background(if (sel) colors.primary.copy(alpha = 0.10f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLong)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            if (selecting) {
                Box(
                    Modifier.size(22.dp).clip(CircleShape).background(if (sel) colors.primary else colors.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) { if (sel) Icon(Icons.Filled.Check, null, Modifier.size(14.dp), tint = Color.White) }
            } else Text("$rank", fontWeight = FontWeight.Bold, color = if (rank <= 3) Color(0xFFFF9F0A) else colors.onSurfaceVariantSummary)
        }
        Spacer(Modifier.width(8.dp))
        Avatar(st.student.name, size = 38.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(st.student.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text(sub, fontSize = 12.sp, color = colors.onSurfaceVariantSummary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(valueText, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = if (warn) statusColor(HwStatus.MISSING) else colors.primary)
    }
}

@Composable
private fun GroupRow(rank: Int, name: String, list: List<StudentStat>, avg: Double, onClick: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    com.deepshawn.hwcounter.ui.components.HCard(Modifier.padding(horizontal = 12.dp, vertical = 5.dp), onClick = onClick) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (rank <= 3) listOf("🥇", "🥈", "🥉")[rank - 1] else "$rank", fontSize = 22.sp, modifier = Modifier.width(36.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                    Text("${list.size} 人 · 缺交 ${list.sumOf { it.missing }} 次 · 点击给全组加减分", fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(avg.fmt(), fontWeight = FontWeight.Bold, fontSize = 22.sp, color = colors.primary)
                    Text("平均分", fontSize = 10.sp, color = colors.onSurfaceVariantSummary)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) { list.take(10).forEach { Avatar(it.student.name, size = 28.dp) } }
        }
    }
}
