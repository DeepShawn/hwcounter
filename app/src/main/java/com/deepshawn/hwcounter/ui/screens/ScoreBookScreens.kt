@file:OptIn(ExperimentalFoundationApi::class)

package com.deepshawn.hwcounter.ui.screens

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepshawn.hwcounter.data.Analytics
import com.deepshawn.hwcounter.data.AppData
import com.deepshawn.hwcounter.data.AppViewModel
import com.deepshawn.hwcounter.data.LedgerRow
import com.deepshawn.hwcounter.data.SchoolClass
import com.deepshawn.hwcounter.data.ScoreRules
import com.deepshawn.hwcounter.data.Settle
import com.deepshawn.hwcounter.data.Settlement
import com.deepshawn.hwcounter.data.Student
import com.deepshawn.hwcounter.data.compareStudentNumbers
import com.deepshawn.hwcounter.data.fmt
import com.deepshawn.hwcounter.data.fmtDay
import com.deepshawn.hwcounter.data.signed
import com.deepshawn.hwcounter.data.pt
import com.deepshawn.hwcounter.data.startOfDay
import com.deepshawn.hwcounter.export.Exporter
import com.deepshawn.hwcounter.export.Ledger
import com.deepshawn.hwcounter.export.PdfReport
import com.deepshawn.hwcounter.ui.LocalKit
import com.deepshawn.hwcounter.ui.Route
import com.deepshawn.hwcounter.ui.components.ActionSheet
import com.deepshawn.hwcounter.ui.components.Avatar
import com.deepshawn.hwcounter.ui.components.EmptyState
import com.deepshawn.hwcounter.ui.components.HCard
import com.deepshawn.hwcounter.ui.components.HTabs
import com.deepshawn.hwcounter.ui.components.HyperDialog
import com.deepshawn.hwcounter.ui.components.LocalBottomInset
import com.deepshawn.hwcounter.ui.components.MiuixPage
import com.deepshawn.hwcounter.ui.components.NumberDialog
import com.deepshawn.hwcounter.ui.components.SheetAction
import com.deepshawn.hwcounter.ui.components.glass
import com.deepshawn.hwcounter.ui.components.listCell
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val GOOD = Color(0xFF169B68)
private val BAD = Color(0xFFE5384D)

private fun share(ctx: Context, c: SchoolClass, name: String, pdf: Boolean, write: (java.io.OutputStream) -> Unit) {
    val f = java.io.File(Exporter.exportDir(ctx), "${c.name}_${name}_${Exporter.fileStamp()}.${if (pdf) "pdf" else "xlsx"}".replace("/", "_"))
    f.outputStream().use(write)
    Exporter.shareFile(ctx, f, if (pdf) "application/pdf" else "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "分享$name")
}

/** 导出计分规则通知（按当前设置） */
fun shareRulesNotice(ctx: Context, c: SchoolClass, rules: ScoreRules) = share(ctx, c, "积分计分标准通知", true) { PdfReport.writeRules(c, rules, it) }

// ---------------- 积分流水 ----------------

/**
 * 全班（或某位同学）的积分流水：作业自动计分、手动加减分、考试、结算、重置，
 * 可按类型、时间、学生筛选，导出 Excel / PDF。
 */
@Composable
fun LedgerScreen(vm: AppViewModel, data: AppData, studentId: String?) {
    val kit = LocalKit.current
    val ctx = LocalContext.current
    val colors = MiuixTheme.colorScheme
    val c = vm.currentClass(data) ?: run { EmptyState("🧾", "还没有班级", ""); return }
    val rules = data.settings.rules
    var who by rememberSaveable { mutableStateOf(studentId) }
    var kind by rememberSaveable { mutableIntStateOf(0) }
    var range by rememberSaveable { mutableIntStateOf(0) }
    var pickWho by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    val all = remember(c, rules) { Ledger.all(c, rules) }
    val kinds = listOf(null, LedgerRow.Kind.AUTO, LedgerRow.Kind.MANUAL, LedgerRow.Kind.EXAM, LedgerRow.Kind.SETTLE, LedgerRow.Kind.RECHARGE)
    val roundStart = remember(c) { Analytics.classResets(c).lastOrNull()?.start }
    val monthStart = remember { Calendar.getInstance().apply { timeInMillis = startOfDay(System.currentTimeMillis()); set(Calendar.DAY_OF_MONTH, 1) }.timeInMillis }
    val from: Long? = when (range) { 0 -> roundStart; 1 -> monthStart; else -> null }
    val rows = remember(all, who, kind, from) {
        val f = all.filter { (s, r) -> (who == null || s.id == who) && (kinds[kind] == null || r.kind == kinds[kind]) && (from == null || r.time >= from) }
        // 全班查看时，同一次重置只显示一行
        if (who != null) f else f.distinctBy { (s, r) -> if (r.kind == LedgerRow.Kind.RECHARGE) r.rechargeId.orEmpty() else s.id + r.key }
    }
    val counted = rows.filter { it.second.counted && it.second.kind != LedgerRow.Kind.RECHARGE }
    val plus = counted.filter { it.second.delta > 0 }.sumOf { it.second.delta }
    val minus = counted.filter { it.second.delta < 0 }.sumOf { it.second.delta }
    val whoName = who?.let { id -> c.students.firstOrNull { it.id == id }?.name } ?: "全班"
    val scope = "$whoName · ${listOf("本轮", "本月", "全部")[range]}" + (kinds[kind]?.let { " · ${Ledger.kindLabel[it]}" } ?: "") +
        (from?.let { " · ${fmtDay(it)} 起" } ?: "")
    val df = remember { SimpleDateFormat("M/d HH:mm", Locale.CHINA) }

    MiuixPage(
        title = "积分流水", onBack = { kit.nav.pop() },
        actions = { IconButton(onClick = { exporting = true }, modifier = Modifier.padding(end = 16.dp)) { Icon(Icons.Filled.IosShare, "导出", tint = colors.onSurface) } },
        swipe = Triple(6, kind) { kind = it },
    ) {
        item(key = "filters") {
            Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.secondaryContainer).clickable { pickWho = true }.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("学生", fontSize = 13.sp, color = colors.onSurfaceVariantSummary)
                    Spacer(Modifier.width(10.dp))
                    Text(whoName, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Text("切换 ›", fontSize = 13.sp, color = colors.primary)
                }
                HTabs(listOf("全部", "作业", "手动", "考试", "结算", "重置"), kind, { kind = it }, height = 36.dp)
                HTabs(listOf("本轮", "本月", "全部时间"), range, { range = it }, height = 34.dp)
            }
        }
        item(key = "sum") {
            HCard(Modifier.padding(horizontal = 12.dp, vertical = 10.dp).fillMaxWidth()) {
                Row(Modifier.padding(vertical = 14.dp)) {
                    listOf("记录" to "${rows.size} 条", "加分" to "+${plus.fmt()}", "扣分" to minus.fmt(), "净变化" to (plus + minus).signed().let { if (plus + minus == 0.0) "0" else it }).forEach { (l, v) ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(v, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = when (l) { "加分" -> GOOD; "扣分" -> BAD; else -> colors.onSurface })
                            Text(l, fontSize = 11.sp, color = colors.onSurfaceVariantSummary)
                        }
                    }
                }
                Text("只统计计入当前积分的记录；重置之前的记录标“不计入”。", fontSize = 11.sp, color = colors.onSurfaceVariantSummary, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp))
            }
        }
        if (rows.isEmpty()) item { EmptyState("🧾", "没有符合条件的记录", "换个类型或时间范围看看") }
        itemsIndexed(rows, key = { _, (s, r) -> s.id + r.key }) { i, (s, r) ->
            Row(
                Modifier.listCell(i, rows.size).clickable { kit.nav.push(Route.StudentDetail(s.id)) }.padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (who == null && r.kind != LedgerRow.Kind.RECHARGE) { Text(s.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp); Spacer(Modifier.width(6.dp)) }
                        Text(
                            Ledger.kindLabel[r.kind] ?: "", fontSize = 10.sp, color = colors.primary,
                            modifier = Modifier.clip(CircleShape).background(colors.primary.copy(alpha = 0.10f)).padding(horizontal = 6.dp, vertical = 1.dp),
                        )
                        if (!r.counted) Text("  不计入", fontSize = 10.sp, color = colors.onSurfaceVariantSummary)
                    }
                    Text(
                        if (who == null && r.kind == LedgerRow.Kind.RECHARGE) r.title.substringBefore("（原") + "（全班）" else r.title,
                        fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, color = if (r.counted) colors.onSurface else colors.onSurfaceVariantSummary,
                    )
                    Text(df.format(Date(r.time)), fontSize = 11.sp, color = colors.onSurfaceVariantSummary)
                }
                Text(
                    if (r.kind == LedgerRow.Kind.RECHARGE) "重置" else r.delta.signed().let { if (r.delta == 0.0) "0" else it },
                    fontWeight = FontWeight.Bold, fontSize = 17.sp,
                    color = if (!r.counted) colors.onSurfaceVariantSummary else if (r.delta > 0) GOOD else if (r.delta < 0) BAD else colors.onSurfaceSecondary,
                )
            }
        }
    }

    ActionSheet(exporting, "导出积分流水（$scope）", listOf(
        SheetAction("导出 Excel", "流水明细 + 按学生汇总（作业 / 手动 / 考试 / 结算）") {
            share(ctx, c, "积分流水", false) { Ledger.writeXlsx(c, "积分流水", scope, rows, it) }
        },
        SheetAction("导出 PDF", "表格排版，可直接打印") {
            runCatching { share(ctx, c, "积分流水", true) { PdfReport.writeLedger(c, "积分流水", scope, rows.map { it.first.name to it.second }, it) } }.onFailure { kit.toast("导出失败：${it.message}") }
        },
    ), onDismiss = { exporting = false })
    ActionSheet(pickWho, "选择学生", listOf(SheetAction("全班") { who = null }) + c.students.sortedWith { a, b -> compareStudentNumbers(a.no, b.no) }.map { s ->
        SheetAction("${s.no} ${s.name}") { who = s.id }
    }, onDismiss = { pickWho = false })
}

// ---------------- 结算 ----------------

/** 结算本轮：预览每人的交齐度、缺交与结算分（可逐人调整），确认后写入；下方是结算记录，可导出、撤销 */
@Composable
fun SettlementScreen(vm: AppViewModel, data: AppData) {
    val kit = LocalKit.current
    val ctx = LocalContext.current
    val colors = MiuixTheme.colorScheme
    val c = vm.currentClass(data) ?: run { EmptyState("🧾", "还没有班级", ""); return }
    val readOnly by vm.readOnly.collectAsState()
    val rules = data.settings.rules
    val preview = remember(c, rules) { Settle.preview(c, rules) }
    var overrides by remember(preview) { mutableStateOf(mapOf<String, Double>()) }
    var newRound by rememberSaveable { mutableStateOf(true) }
    var target by rememberSaveable { mutableStateOf(rules.initial) }
    var editTarget by remember { mutableStateOf(false) }
    var editFor by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf(false) }
    var exportFor by remember { mutableStateOf<Settlement?>(null) }
    var undoFor by remember { mutableStateOf<Settlement?>(null) }
    val names = remember(c.students) { c.students.associateBy { it.id } }
    val lines = preview.lines.map { l -> overrides[l.studentId]?.let { l.copy(delta = it, reasons = l.reasons + "老师调整为 ${it.signed()}") } ?: l }
    val done = lines.filter { !it.skipped }
    val already = c.settlements.lastOrNull { it.roundStart == preview.roundStart && it.resetId == null }
    val roundAssignments = remember(c, preview) { c.assignments.count { a -> (preview.roundStart == null || a.createdAt >= preview.roundStart) } }

    MiuixPage(
        title = "结算", onBack = { kit.nav.pop() },
        extraBottom = if (readOnly) 0.dp else 90.dp,
        floating = {
            if (!readOnly && done.isNotEmpty()) Row(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp + LocalBottomInset.current)
                    .widthIn(max = 560.dp).fillMaxWidth().glass(RoundedCornerShape(24.dp)).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text("结算 ${done.size} 人 · 合计 ${done.sumOf { it.delta }.signed()}", fontWeight = FontWeight.SemiBold)
                    Text(if (newRound) "结算后开始新一轮（重置为 ${target.fmt()} 分）" else "只结算，不重置", fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
                }
                TextButton("确认结算", onClick = { confirm = true }, colors = ButtonDefaults.textButtonColorsPrimary(), minHeight = 38.dp, insideMargin = PaddingValues(horizontal = 16.dp, vertical = 6.dp))
            }
        },
    ) {
        item(key = "head") {
            HCard(Modifier.padding(horizontal = 12.dp).fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("本轮：" + (preview.roundStart?.let { "${fmtDay(it)} 起" } ?: "有记录以来") + " · $roundAssignments 次作业", fontWeight = FontWeight.SemiBold)
                    Text("按交齐度与缺交度结算：全勤 ${rules.settlement.perfect.signed()}，≥95% ${rules.settlement.r95.signed()}，90–95% ${rules.settlement.r90.signed()}，70–80% ${rules.settlement.r70.signed()}，<70% ${rules.settlement.below.signed()}；另有缺交扣分、进步奖、小组奖。规则可在设置中修改。", fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
                    Row(Modifier.padding(top = 6.dp)) {
                        listOf("加分" to done.count { it.delta > 0 }, "不变" to done.count { it.delta == 0.0 }, "扣分" to done.count { it.delta < 0 }, "不结算" to lines.count { it.skipped }).forEach { (l, n) ->
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$n", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = when (l) { "加分" -> GOOD; "扣分" -> BAD; else -> colors.onSurface })
                                Text("$l（人）", fontSize = 11.sp, color = colors.onSurfaceVariantSummary)
                            }
                        }
                    }
                    already?.let { Text("注意：本轮已于 ${fmtStamp(it.time)} 结算过一次，再结算会重复加减分。", fontSize = 12.sp, color = BAD) }
                }
            }
        }
        if (!readOnly) item(key = "opts") {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HTabs(listOf("结算并开始新一轮", "只结算"), if (newRound) 0 else 1, { newRound = it == 0 }, height = 36.dp)
                if (newRound) Text(
                    "新一轮从 ${target.fmt()} 分开始，结算分计入新一轮（点此修改分数）。",
                    fontSize = 12.sp, color = colors.primary, modifier = Modifier.padding(start = 6.dp).clickable { editTarget = true },
                )
            }
        }
        item(key = "ltitle") { SmallTitle("结算明细 · 点同学可调整结算分") }
        val sorted = lines.sortedWith(compareBy<com.deepshawn.hwcounter.data.SettlementLine> { it.skipped }.thenByDescending { it.delta }.thenBy { names[it.studentId]?.no ?: "" })
        itemsIndexed(sorted, key = { _, l -> "l" + l.studentId }) { i, l ->
            val s = names[l.studentId] ?: return@itemsIndexed
            Row(
                Modifier.listCell(i, sorted.size).clickable(enabled = !readOnly && !l.skipped) { editFor = l.studentId }.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(s.name, size = 36.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.name, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                    Text("交齐 ${(l.rate * 100).toInt()}% · 应交 ${l.expected} · 缺交 ${l.missing}" + (if (l.longestMissing >= 2) " · 连缺 ${l.longestMissing}" else "") + (if (l.late > 0) " · 迟交 ${l.late}" else ""), fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
                    Text(l.reasons.joinToString("；"), fontSize = 11.sp, color = colors.onSurfaceSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                Text(if (l.skipped) "不结算" else if (l.delta == 0.0) "0" else l.delta.signed(), fontWeight = FontWeight.Bold, fontSize = if (l.skipped) 13.sp else 19.sp,
                    color = if (l.skipped) colors.onSurfaceVariantSummary else if (l.delta > 0) GOOD else if (l.delta < 0) BAD else colors.onSurfaceSecondary)
            }
        }
        if (c.settlements.isNotEmpty()) {
            item(key = "htitle") { SmallTitle("结算记录") }
            val hist = c.settlements.sortedByDescending { it.time }
            itemsIndexed(hist, key = { _, s -> "h" + s.id }) { i, s ->
                val ok = s.lines.filter { !it.skipped }
                Row(Modifier.listCell(i, hist.size).clickable { exportFor = s }.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(s.title, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                        Text("${fmtStamp(s.time)} · ${ok.size} 人 · 合计 ${ok.sumOf { it.delta }.signed()}" + if (s.resetId != null) " · 已开始新一轮" else "", fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
                    }
                    Text("导出 / 撤销 ›", fontSize = 12.sp, color = colors.primary)
                }
            }
        }
        item(key = "pad") { Spacer(Modifier.height(12.dp)) }
    }

    editFor?.let { id ->
        val l = lines.first { it.studentId == id }
        NumberDialog(true, "调整 ${names[id]?.name} 的结算分", "按规则算出 ${preview.lines.first { it.studentId == id }.delta.signed()}：" + preview.lines.first { it.studentId == id }.reasons.joinToString("；"), l.delta, "结算分", onDismiss = { editFor = null }) { v ->
            overrides = overrides + (id to rules.pt(v)); editFor = null
        }
    }
    NumberDialog(editTarget, "新一轮的起始分", "结算后全班积分重置为这个分数，再加上各自的结算分。", target, "分数", onDismiss = { editTarget = false }) { v -> target = v; editTarget = false }
    HyperDialog(
        confirm, "确认结算？", { confirm = false },
        summary = "给 ${done.size} 人写入结算分（合计 ${done.sumOf { it.delta }.signed()}）" + (if (newRound) "，并把全班积分重置为 ${target.fmt()} 分开始新一轮；结算分计入新一轮" else "") + "。之后可在结算记录里撤销。",
        confirmText = "结算",
        onConfirm = {
            confirm = false
            vm.settle(preview.copy(lines = lines, time = System.currentTimeMillis()), if (newRound) target else null)
            kit.haptic(true); kit.celebrate()
            kit.undoable("已结算 ${done.size} 人" + if (newRound) "，新一轮开始" else "")
            kit.nav.pop()
        },
    )
    exportFor?.let { s ->
        ActionSheet(true, s.title, buildList {
            add(SheetAction("导出结算单 PDF") { runCatching { share(ctx, c, s.title, true) { PdfReport.writeSettlement(c, s, it) } }.onFailure { kit.toast("导出失败：${it.message}") } })
            add(SheetAction("导出结算单 Excel") { share(ctx, c, s.title, false) { Ledger.writeSettlementXlsx(c, s, it) } })
            if (!readOnly) add(SheetAction("撤销这次结算", "删除结算加减分" + if (s.resetId != null) "，并撤销随后的重置" else "", danger = true) { undoFor = s })
        }, onDismiss = { exportFor = null })
    }
    undoFor?.let { s ->
        HyperDialog(true, "撤销「${s.title}」？", { undoFor = null }, summary = "删除这次结算写入的加减分" + (if (s.resetId != null && !s.id.startsWith("auto-")) "，并撤销结算后开始新一轮的那次重置" else "") + "。", confirmText = "撤销", danger = true, onConfirm = {
            vm.undoSettlement(s.id); undoFor = null; kit.undoable("已撤销「${s.title}」")
        })
    }
}

@Suppress("unused") private fun keepStudent(s: Student) = s
