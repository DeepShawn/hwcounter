@file:OptIn(ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.deepshawn.hwcounter.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepshawn.hwcounter.data.AppData
import com.deepshawn.hwcounter.data.AppViewModel
import com.deepshawn.hwcounter.data.Exam
import com.deepshawn.hwcounter.data.ExamImport
import com.deepshawn.hwcounter.data.compareStudentNumbers
import com.deepshawn.hwcounter.data.fmt
import com.deepshawn.hwcounter.data.fmtDay
import com.deepshawn.hwcounter.data.previousExam
import com.deepshawn.hwcounter.data.ranks
import com.deepshawn.hwcounter.data.signed
import com.deepshawn.hwcounter.data.startOfDay
import com.deepshawn.hwcounter.data.stat
import com.deepshawn.hwcounter.recog.StatTable
import com.deepshawn.hwcounter.ui.LocalKit
import com.deepshawn.hwcounter.ui.Route
import com.deepshawn.hwcounter.ui.components.ActionSheet
import com.deepshawn.hwcounter.ui.components.Avatar
import com.deepshawn.hwcounter.ui.components.EmptyState
import com.deepshawn.hwcounter.ui.components.HCard
import com.deepshawn.hwcounter.ui.components.HTabs
import com.deepshawn.hwcounter.ui.components.Histogram
import com.deepshawn.hwcounter.ui.components.HyperDialog
import com.deepshawn.hwcounter.ui.components.MiuixPage
import com.deepshawn.hwcounter.ui.components.SheetAction
import com.deepshawn.hwcounter.ui.components.listCell
import com.deepshawn.hwcounter.ui.components.pctText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val shortDate = SimpleDateFormat("M月d日", Locale.CHINA)

/** 分数颜色：优秀绿、及格正文、不及格红 */
@Composable
private fun scoreColor(score: Double, full: Double): Color {
    val p = if (full <= 0) 0.0 else score / full
    return when {
        p >= 0.85 -> Color(0xFF169B68)
        p < 0.6 -> Color(0xFFE5384D)
        else -> MiuixTheme.colorScheme.onSurface
    }
}

// ---------------- 考试列表 ----------------

@Composable
fun ExamListScreen(vm: AppViewModel, data: AppData) {
    val kit = LocalKit.current
    val colors = MiuixTheme.colorScheme
    val c = vm.currentClass(data) ?: return
    val readOnly by vm.readOnly.collectAsState()
    var creating by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<Exam?>(null) }
    var editing by remember { mutableStateOf<Exam?>(null) }
    var deleting by remember { mutableStateOf<Exam?>(null) }
    val exams = c.exams.sortedByDescending { it.date }
    val rules = data.settings.rules.exam

    MiuixPage(
        title = "考试",
        onBack = { kit.nav.pop() },
        actions = {
            if (!readOnly) {
                IconButton(onClick = { kit.nav.push(Route.ExamImport(null)) }) { Icon(Icons.Filled.FileUpload, "导入成绩", tint = colors.onSurface) }
                IconButton(onClick = { creating = true }, modifier = Modifier.padding(start = 4.dp, end = 16.dp)) { Icon(Icons.Filled.Add, "新建考试", tint = colors.onSurface) }
            }
        },
    ) {
        item(key = "sub") {
            Text(
                "考试成绩与作业分开统计" + if (rules.enabled) " · 按分数档自动加减积分" else " · 不自动加减积分",
                fontSize = 13.sp, color = colors.onSurfaceVariantSummary, modifier = Modifier.padding(start = 28.dp, end = 28.dp, bottom = 10.dp),
            )
        }
        if (exams.isEmpty()) {
            item {
                EmptyState("📝", "还没有考试", "导入成绩表（粘贴 / CSV / Excel），或新建考试后逐个录入") {
                    if (!readOnly) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TextButton("导入成绩表", onClick = { kit.nav.push(Route.ExamImport(null)) }, colors = ButtonDefaults.textButtonColorsPrimary())
                        TextButton("新建考试", onClick = { creating = true })
                    }
                }
            }
        } else {
            item(key = "overview") {
                val last = exams.first(); val st = last.stat(c.students)
                HCard(Modifier.padding(horizontal = 12.dp).fillMaxWidth(), onClick = { kit.nav.push(Route.Exam(last.id)) }) {
                    Column(Modifier.padding(16.dp)) {
                        Text("最近一场 · ${shortDate.format(Date(last.date))}", fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
                        Text("${last.subject} ${last.title}".trim(), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(12.dp))
                        Row {
                            Num("平均分", st.avg.fmt(), Modifier.weight(1f))
                            Num("最高", st.max.fmt(), Modifier.weight(1f))
                            Num("及格率", pctText(st.passRate.toFloat()), Modifier.weight(1f))
                            Num("优秀率", pctText(st.excellentRate.toFloat()), Modifier.weight(1f))
                        }
                    }
                }
            }
            item(key = "allTitle") { SmallTitle("全部考试 · ${exams.size} 场") }
            itemsIndexed(exams, key = { _, e -> e.id }) { i, e ->
                val st = remember(e, c.students) { e.stat(c.students) }
                Row(
                    Modifier.listCell(i, exams.size)
                        .combinedClickable(onClick = { kit.nav.push(Route.Exam(e.id)) }, onLongClick = { if (!readOnly) menuFor = e })
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val sc = subjectColor(e.subject)
                    Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(sc.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                        Text(e.subject.take(1).ifEmpty { "考" }, color = sc, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(e.title, fontWeight = FontWeight.Medium, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOf(e.subject, shortDate.format(Date(e.date)), "满分 ${e.fullScore.fmt()}", "${st.count} 人" + if (st.missing > 0) "（${st.missing} 人未录入）" else "").filter { it.isNotBlank() }.joinToString(" · "),
                            fontSize = 12.sp, color = colors.onSurfaceVariantSummary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(st.avg.fmt(), fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                        Text("及格 ${pctText(st.passRate.toFloat())}", fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
                    }
                }
            }
        }
    }

    if (creating) ExamInfoDialog(null, data.settings.subjects, onDismiss = { creating = false }) { e -> vm.upsertExam(e); creating = false; kit.nav.push(Route.Exam(e.id)) }
    editing?.let { e -> ExamInfoDialog(e, data.settings.subjects, onDismiss = { editing = null }) { vm.upsertExam(it); editing = null } }
    menuFor?.let { e ->
        ActionSheet(true, e.title, listOf(
            SheetAction("编辑考试信息") { editing = e },
            SheetAction("导入成绩到这场考试") { kit.nav.push(Route.ExamImport(e.id)) },
            SheetAction("删除", "成绩和相应的积分加减一起删除", danger = true) { deleting = e },
        ), onDismiss = { menuFor = null })
    }
    deleting?.let { e ->
        HyperDialog(true, "删除「${e.title}」？", { deleting = null }, summary = "这场考试的成绩和按分数档加减的积分会一起删除，可撤销。", confirmText = "删除", danger = true, onConfirm = {
            vm.deleteExam(e.id); deleting = null; kit.undoable("已删除「${e.title}」")
        })
    }
}

@Composable
private fun Num(label: String, value: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(label, fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}

/** 新建 / 编辑考试：名称、科目、满分、日期、是否按分数档自动加减分 */
@Composable
fun ExamInfoDialog(initial: Exam?, subjects: List<String>, onDismiss: () -> Unit, onSave: (Exam) -> Unit) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var subject by remember { mutableStateOf(initial?.subject ?: "") }
    var full by remember { mutableStateOf((initial?.fullScore ?: 100.0).fmt()) }
    var day by remember { mutableStateOf(startOfDay(initial?.date ?: System.currentTimeMillis())) }
    var auto by remember { mutableStateOf(initial?.autoScore ?: true) }
    var pickDay by remember { mutableStateOf(false) }
    val colors = MiuixTheme.colorScheme
    val fullV = full.trim().toDoubleOrNull()?.takeIf { it > 0 && it.isFinite() }
    HyperDialog(
        show = true, title = if (initial == null) "新建考试" else "编辑考试", onDismiss = onDismiss,
        confirmText = "保存", confirmEnabled = title.isNotBlank() && fullV != null,
        onConfirm = {
            val base = initial ?: Exam(title = title.trim())
            // 保留原来的时分，只改日期
            val keep = initial?.let { it.date - startOfDay(it.date) } ?: (System.currentTimeMillis() - startOfDay(System.currentTimeMillis()))
            onSave(base.copy(title = title.trim(), subject = subject.trim(), fullScore = fullV!!, date = day + keep, autoScore = auto))
        },
    ) {
        TextField(title, { title = it }, label = "考试名称，如：期中考试", singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        Text("科目", fontSize = 13.sp, color = colors.onSurfaceVariantSummary, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            (listOf("") + subjects).forEach { s ->
                val sel = s == subject
                Text(
                    s.ifEmpty { "不限" }, fontSize = 13.sp, color = if (sel) Color.White else colors.onSurfaceSecondary,
                    modifier = Modifier.clip(CircleShape).background(if (sel) colors.primary else colors.secondaryContainer).clickable { subject = s }.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextField(full, { full = it }, label = "满分", singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
            Spacer(Modifier.width(10.dp))
            TextButton(fmtDay(day), onClick = { pickDay = true }, modifier = Modifier.weight(1.2f))
        }
        SuperSwitch(checked = auto, onCheckedChange = { auto = it }, title = "按分数档自动加减积分", insideMargin = PaddingValues(horizontal = 4.dp, vertical = 12.dp))
    }
    if (pickDay) {
        val state = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = day + java.util.TimeZone.getDefault().getOffset(day))
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { pickDay = false },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    state.selectedDateMillis?.let { utc -> day = startOfDay(utc - java.util.TimeZone.getDefault().getOffset(utc) + 12 * 3600_000L) }
                    pickDay = false
                }) { androidx.compose.material3.Text("确定") }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { pickDay = false }) { androidx.compose.material3.Text("取消") } },
        ) { androidx.compose.material3.DatePicker(state) }
    }
}

// ---------------- 考试详情 ----------------

@Composable
fun ExamDetailScreen(vm: AppViewModel, data: AppData, examId: String) {
    val kit = LocalKit.current
    val colors = MiuixTheme.colorScheme
    val c = vm.currentClass(data) ?: return
    val e = c.exams.firstOrNull { it.id == examId } ?: run { androidx.compose.runtime.LaunchedEffect(Unit) { kit.nav.pop() }; return }
    val readOnly by vm.readOnly.collectAsState()
    var sort by rememberSaveable { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var editInfo by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var editScore by remember { mutableStateOf<com.deepshawn.hwcounter.data.Student?>(null) }
    val st = remember(e, c.students) { e.stat(c.students) }
    val ranks = remember(e) { e.ranks() }
    val prev = remember(c, e) { c.previousExam(e) }
    val prevRanks = remember(prev) { prev?.ranks().orEmpty() }
    val rules = data.settings.rules.exam
    val rows = remember(e, c.students, sort) {
        val withScore = c.students.filter { it.id in e.scores }
        val rest = c.students.filter { it.id !in e.scores }
        if (sort == 0) withScore.sortedWith(compareBy<com.deepshawn.hwcounter.data.Student> { ranks[it.id] ?: Int.MAX_VALUE }.thenComparator { a, b -> compareStudentNumbers(a.no, b.no) }) + rest.sortedWith { a, b -> compareStudentNumbers(a.no, b.no) }
        else c.students.sortedWith { a, b -> compareStudentNumbers(a.no, b.no) }
    }

    MiuixPage(
        title = e.title,
        onBack = { kit.nav.pop() },
        actions = {
            if (!readOnly) IconButton(onClick = { menu = true }, modifier = Modifier.padding(end = 16.dp)) { Icon(Icons.Filled.MoreVert, "更多", tint = colors.onSurface) }
        },
    ) {
        item(key = "sub") {
            Text(
                listOf(e.subject, fmtDay(e.date), "满分 ${e.fullScore.fmt()}", if (e.autoScore && rules.enabled) "按分数档加减积分" else "不加减积分").filter { it.isNotBlank() }.joinToString(" · "),
                fontSize = 13.sp, color = colors.onSurfaceVariantSummary, modifier = Modifier.padding(start = 28.dp, end = 28.dp, bottom = 10.dp),
            )
        }
        item(key = "stats") {
            HCard(Modifier.padding(horizontal = 12.dp).fillMaxWidth()) {
                Row(Modifier.padding(top = 16.dp)) {
                    Num("平均分", st.avg.fmt(), Modifier.weight(1f))
                    Num("中位数", st.median.fmt(), Modifier.weight(1f))
                    Num("最高", st.max.fmt(), Modifier.weight(1f))
                    Num("最低", st.min.fmt(), Modifier.weight(1f))
                }
                Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth().height(0.5.dp).background(colors.dividerLine))
                Row(Modifier.padding(bottom = 6.dp)) {
                    Num("优秀率", pctText(st.excellentRate.toFloat()), Modifier.weight(1f))
                    Num("良好率", pctText(st.goodRate.toFloat()), Modifier.weight(1f))
                    Num("及格率", pctText(st.passRate.toFloat()), Modifier.weight(1f))
                    Num("低分率", pctText(st.lowRate.toFloat()), Modifier.weight(1f))
                }
                Text(
                    "${st.count} 人有成绩" + (if (st.absent > 0) " · ${st.absent} 人缺考" else "") + (if (st.missing > 0) " · ${st.missing} 人未录入" else "") + " · 标准差 ${st.std.fmt()}" +
                        (prev?.let { p -> val ps = p.stat(c.students); " · 比上次（${p.title}）平均${if (st.avg / e.fullScore >= ps.avg / p.fullScore) "高" else "低"} ${pctText(kotlin.math.abs(st.avg / e.fullScore - ps.avg / p.fullScore).toFloat())}" } ?: ""),
                    fontSize = 12.sp, color = colors.onSurfaceVariantSummary, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
        if (st.count > 0) item(key = "dist") {
            SmallTitle("分数段（满分的 10% 一档）")
            HCard(Modifier.padding(horizontal = 12.dp).fillMaxWidth()) {
                // 去掉前面没人的分数段（保留一档留白）
                val first = (st.bins.indexOfFirst { it.second > 0 } - 1).coerceAtLeast(0)
                Histogram(st.bins.drop(first).map { (l, n) -> l.substringBefore("–") to n }, Modifier.padding(16.dp))
            }
        }
        item(key = "sort") {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
                HTabs(listOf("按排名", "按学号"), sort, { sort = it })
            }
        }
        itemsIndexed(rows, key = { _, s -> s.id }) { i, s ->
            val score = e.scores[s.id]
            val absent = s.id in e.absent
            val r = ranks[s.id]
            val pr = prevRanks[s.id]
            val delta = rules.deltaFor(e, s.id)
            Row(
                Modifier.listCell(i, rows.size)
                    .combinedClickable(onClick = { if (!readOnly) editScore = s else kit.nav.push(Route.StudentDetail(s.id)) }, onLongClick = { kit.nav.push(Route.StudentDetail(s.id)) })
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (sort == 0) r?.toString() ?: "–" else s.no.takeLast(3), Modifier.width(34.dp), fontWeight = FontWeight.Bold, fontSize = if (sort == 0) 15.sp else 12.sp,
                    color = if (sort == 0 && r != null && r <= 3) Color(0xFFFF9F0A) else colors.onSurfaceVariantSummary,
                )
                Avatar(s.name, size = 36.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.name, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                    val sub = buildList {
                        if (sort == 1 && r != null) add("第 $r 名")
                        if (sort == 0) add(s.no)
                        if (r != null && pr != null && r != pr) add(if (pr > r) "比上次进步 ${pr - r} 名" else "比上次退步 ${r - pr} 名")
                    }
                    if (sub.isNotEmpty()) Text(sub.joinToString(" · "), fontSize = 12.sp, color = if (r != null && pr != null && pr > r) Color(0xFF169B68) else colors.onSurfaceVariantSummary, maxLines = 1)
                }
                if (delta != 0.0) {
                    Text(
                        delta.signed(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if (delta > 0) Color(0xFF169B68) else Color(0xFFE5384D),
                        modifier = Modifier.padding(end = 10.dp).clip(CircleShape).background((if (delta > 0) Color(0xFF169B68) else Color(0xFFE5384D)).copy(alpha = 0.12f)).padding(horizontal = 7.dp, vertical = 2.dp),
                    )
                }
                when {
                    score != null -> Text(score.fmt(), fontWeight = FontWeight.SemiBold, fontSize = 19.sp, color = scoreColor(score, e.fullScore))
                    absent -> Text("缺考", fontSize = 14.sp, color = colors.onSurfaceVariantSummary)
                    else -> Text("未录入", fontSize = 14.sp, color = colors.onSurfaceVariantActions)
                }
            }
        }
    }

    if (menu) ActionSheet(true, e.title, listOf(
        SheetAction("编辑考试信息", "名称、科目、满分、日期") { editInfo = true },
        SheetAction("导入成绩", "粘贴 / CSV / Excel 写入这场考试") { kit.nav.push(Route.ExamImport(e.id)) },
        SheetAction(if (e.autoScore) "这场不加减积分" else "这场按分数档加减积分") { vm.upsertExam(e.copy(autoScore = !e.autoScore)) },
        SheetAction("删除考试", danger = true) { deleting = true },
    ), onDismiss = { menu = false })
    if (editInfo) ExamInfoDialog(e, data.settings.subjects, onDismiss = { editInfo = false }) { vm.upsertExam(it); editInfo = false }
    if (deleting) HyperDialog(true, "删除「${e.title}」？", { deleting = false }, summary = "成绩和按分数档加减的积分会一起删除，可撤销。", confirmText = "删除", danger = true, onConfirm = {
        deleting = false; vm.deleteExam(e.id); kit.nav.pop(); kit.undoable("已删除「${e.title}」")
    })
    editScore?.let { s ->
        var text by remember(s.id) { mutableStateOf(e.scores[s.id]?.fmt() ?: "") }
        val v = text.trim().toDoubleOrNull()?.takeIf { it >= 0 && it <= e.fullScore * 2 }
        HyperDialog(
            true, "${s.name} 的成绩", { editScore = null },
            summary = "满分 ${e.fullScore.fmt()}" + if (rules.enabled && e.autoScore) " · 按分数档加减积分" else "",
            confirmText = "保存", confirmEnabled = v != null,
            onConfirm = { vm.setExamScore(e.id, s.id, v); editScore = null; kit.haptic() },
        ) {
            TextField(text, { text = it }, label = "分数", singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton("记为缺考", onClick = { vm.setExamScore(e.id, s.id, null, absent = true); editScore = null }, modifier = Modifier.weight(1f))
                TextButton("清除", onClick = { vm.setExamScore(e.id, s.id, null); editScore = null }, modifier = Modifier.weight(1f))
            }
        }
    }
}

// ---------------- 导入成绩表 ----------------

@Composable
fun ExamImportScreen(vm: AppViewModel, data: AppData, examId: String?) {
    val kit = LocalKit.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = MiuixTheme.colorScheme
    val c = vm.currentClass(data) ?: return
    val target = examId?.let { id -> c.exams.firstOrNull { it.id == id } }
    var text by rememberSaveable { mutableStateOf("") }
    var table by remember { mutableStateOf<List<List<String>>?>(null) }
    var col by remember { mutableIntStateOf(0) }
    var title by remember { mutableStateOf("") }
    var full by remember { mutableStateOf("") }
    val result = remember(table, c.students) { table?.let { ExamImport.recognize(it, c.students) } }
    val column = result?.columns?.getOrNull(col)
    androidx.compose.runtime.LaunchedEffect(result, col) {
        if (column != null) {
            if (title.isBlank() || result.columns.any { it.header == title }) title = target?.title ?: column.header
            if (full.isBlank()) full = (target?.fullScore ?: ExamImport.guessFull(column.max)).fmt()
        }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val rows = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
                    if (bytes.size > 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) StatTable.readXlsx(bytes)
                    else StatTable.splitTable(bytes.toString(Charsets.UTF_8))
                }.getOrNull()
            }
            if (rows.isNullOrEmpty()) kit.toast("读取失败：请用 .xlsx、CSV 或文本") else { table = rows; col = 0; title = ""; full = "" }
        }
    }
    val fullV = full.trim().toDoubleOrNull()?.takeIf { it > 0 }

    MiuixPage(title = if (target != null) "导入到「${target.title}」" else "导入成绩", onBack = { kit.nav.pop() }) {
        item(key = "input") {
            HCard(Modifier.padding(horizontal = 12.dp).fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("粘贴 Excel 里复制的成绩（包含姓名或学号列和分数列），或选择 .xlsx / CSV 文件。可以一次包含多科，每列一科。", fontSize = 13.sp, color = colors.onSurfaceVariantSummary)
                    Spacer(Modifier.height(10.dp))
                    TextField(text, { text = it }, label = "学号\t姓名\t数学\n01\t张三\t95", minLines = 4, maxLines = 8, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TextButton("识别粘贴内容", onClick = { table = StatTable.splitTable(text); col = 0; title = ""; full = "" }, enabled = text.isNotBlank(), modifier = Modifier.weight(1f), colors = ButtonDefaults.textButtonColorsPrimary())
                        TextButton("选择文件", onClick = { pick.launch(arrayOf("*/*")) }, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        if (result != null) {
            if (result.columns.isEmpty()) item { Text("没有找到分数列：请确认表格里有姓名或学号，以及一列数字分数。", Modifier.padding(28.dp), color = colors.onSurfaceVariantSummary) }
            else {
                item(key = "cols") {
                    SmallTitle("识别到 ${result.columns.size} 列成绩 · 匹配 ${result.rows.size} 人")
                    if (result.columns.size > 1) Box(Modifier.padding(horizontal = 12.dp).padding(bottom = 8.dp)) { HTabs(result.columns.map { it.header }, col, { col = it; title = ""; full = "" }) }
                }
                item(key = "meta") {
                    HCard(Modifier.padding(horizontal = 12.dp).fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            if (target == null) TextField(title, { title = it }, label = "考试名称", singleLine = true, modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(8.dp))
                            TextField(full, { full = it }, label = "满分（最高分 ${column?.max?.fmt()}，已自动推测）", singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(10.dp))
                            val scores = result.scoresOf(col); val absent = result.absentOf(col)
                            val avg = if (scores.isEmpty()) 0.0 else scores.values.average()
                            Text("${scores.size} 人有成绩 · ${absent.size} 人缺考 · 平均 ${avg.fmt()}" + if (result.missingStudents.isNotEmpty()) " · ${result.missingStudents.size} 人不在表里" else "", fontSize = 13.sp, color = colors.onSurfaceSecondary)
                            if (result.unmatched.isNotEmpty()) Text("对不上名单的行：" + result.unmatched.take(5).joinToString("；") + if (result.unmatched.size > 5) " …" else "", fontSize = 12.sp, color = Color(0xFFE5384D), modifier = Modifier.padding(top = 4.dp))
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                TextButton(
                                    if (target != null) "写入这场考试" else "保存为新考试",
                                    onClick = {
                                        val base = target ?: Exam(title = title.trim().ifBlank { column!!.header }, subject = data.settings.subjects.firstOrNull { title.contains(it) || column!!.header.contains(it) } ?: "")
                                        vm.upsertExam(base.copy(fullScore = fullV!!, scores = base.scores + scores, absent = (base.absent + absent).distinct().filter { it !in scores }))
                                        kit.toast("已导入 ${scores.size} 人的成绩")
                                        kit.nav.pop(); if (target == null) kit.nav.push(Route.Exam(base.id))
                                    },
                                    enabled = fullV != null && scores.isNotEmpty(), modifier = Modifier.weight(1f), colors = ButtonDefaults.textButtonColorsPrimary(),
                                )
                                if (target == null && result.columns.size > 1) TextButton("每列各存一场", onClick = {
                                    result.columns.forEachIndexed { k, cc ->
                                        val sc = result.scoresOf(k)
                                        if (sc.isNotEmpty()) vm.upsertExam(Exam(title = cc.header, subject = data.settings.subjects.firstOrNull { cc.header.contains(it) } ?: "", fullScore = ExamImport.guessFull(cc.max), scores = sc, absent = result.absentOf(k)))
                                    }
                                    kit.toast("已保存 ${result.columns.size} 场考试"); kit.nav.pop()
                                }, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
                item(key = "pv") { SmallTitle("预览") }
                val preview = result.rows.sortedByDescending { it.values.getOrNull(col) ?: -1.0 }
                itemsIndexed(preview, key = { _, r -> "p" + r.studentId }) { i, r ->
                    val s = c.students.first { it.id == r.studentId }
                    Row(Modifier.listCell(i, preview.size).padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${s.no} ${s.name}", Modifier.weight(1f), fontSize = 14.sp)
                        val v = r.values.getOrNull(col)
                        Text(if (v != null) v.fmt() else if (r.absent.getOrNull(col) == true) "缺考" else "—", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
