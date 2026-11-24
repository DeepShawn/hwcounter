@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.deepshawn.hwcounter.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.onClick
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepshawn.hwcounter.cloud.HistoryItem
import com.deepshawn.hwcounter.data.Assignment
import com.deepshawn.hwcounter.data.Exam
import com.deepshawn.hwcounter.data.HwStatus
import com.deepshawn.hwcounter.data.RosterImport
import com.deepshawn.hwcounter.data.SchoolClass
import com.deepshawn.hwcounter.data.ScoreRules
import com.deepshawn.hwcounter.data.Student
import com.deepshawn.hwcounter.data.fmt
import com.deepshawn.hwcounter.data.fmtDay
import com.deepshawn.hwcounter.data.signed
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.theme.MiuixTheme

// ---------------- 基础控件 ----------------

/** 盖在页面上的对话框 */
@Composable
fun Dialog(title: String, onClose: () -> Unit, buttons: (@Composable androidx.compose.foundation.layout.RowScope.() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val colors = MiuixTheme.colorScheme
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(24.dp).clip(RoundedCornerShape(24.dp)).background(colors.surfaceContainer).clickable(enabled = false) {}.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("关闭", Modifier.clip(CircleShape).clickable(onClick = onClose).padding(8.dp), color = colors.primary, fontWeight = FontWeight.SemiBold)
            }
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
            if (buttons != null) Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) { buttons() }
        }
    }
}

@Composable
fun androidx.compose.foundation.layout.RowScope.Btn(text: String, primary: Boolean = true, danger: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    val bg = when { !primary -> track(); danger -> BAD; else -> colors.primary }
    Text(text, Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(if (enabled) bg else bg.copy(alpha = 0.4f)).clickable(enabled = enabled, onClick = onClick).padding(vertical = 12.dp),
        textAlign = TextAlign.Center, color = if (primary) Color.White else if (danger) BAD else colors.onSurface, fontWeight = FontWeight.SemiBold)
}

/** 带标签的输入框 */
@Composable
fun Field(label: String?, value: String, hint: String = "", password: Boolean = false, onDone: (() -> Unit)? = null, onChange: (String) -> Unit) {
    val colors = MiuixTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        if (label != null) Text(label, fontSize = 13.sp, color = sub(), fontWeight = FontWeight.SemiBold)
        Box(Modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(12.dp)).background(track()).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty() && hint.isNotEmpty()) Text(hint, color = sub(), fontSize = 15.sp)
            BasicTextField(value, onChange, singleLine = true, textStyle = TextStyle(color = colors.onSurface, fontSize = 15.sp), cursorBrush = SolidColor(colors.primary),
                visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }), modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun PickChips(items: List<String>, value: String, onPick: (String) -> Unit) {
    if (items.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { items.forEach { Chip(it, it == value) { onPick(it) } } }
}

private fun num(s: String): Double? = s.trim().replace('，', '.').takeIf { it.isNotEmpty() }?.toDoubleOrNull()?.takeIf { it.isFinite() }

@Composable
fun BoxScope.ToastBar(state: AppState) {
    val t = state.toast ?: return
    Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp).clip(CircleShape).background(Color(0xEE1E1E20)).padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(t.text, color = Color.White, fontSize = 14.sp)
        if (t.undoable && state.undo.isNotEmpty()) Text("撤销", Modifier.padding(start = 14.dp).clickable { state.doUndo() }, color = Color(0xFF8AB4FF), fontWeight = FontWeight.Bold)
    }
}

@Composable
fun Confirm(state: AppState, title: String, text: String, ok: String, onOk: () -> Unit) {
    val close = { state.dialog = null }
    Dialog(title, close, buttons = { Btn("取消", primary = false, onClick = close); Btn(ok, danger = true) { close(); onOk() } }) { Text(text, fontSize = 14.sp) }
}

@Composable
fun ConflictDialog(live: Live) {
    Dialog("本机和云端都有改动", {}, buttons = null) {
        Text("可能在别的设备（安卓 / iPad / 网页）上也改过。请选择怎么处理：", fontSize = 14.sp, color = sub())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Btn("合并两边（推荐）") { live.resolve("merge") } }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Btn("使用云端的数据", primary = false) { live.resolve("cloud") }; Btn("用本机覆盖云端", primary = false, danger = true) { live.resolve("local") } }
    }
}

// ---------------- 作业 ----------------

private val ST = listOf(HwStatus.SUBMITTED, HwStatus.EXCELLENT, HwStatus.LATE, HwStatus.MISSING, HwStatus.LEAVE, HwStatus.NONE)
fun HwStatus.color(): Color = when (this) { HwStatus.SUBMITTED -> View.SUBMITTED.c(); HwStatus.EXCELLENT -> View.EXCELLENT.c(); HwStatus.LATE -> View.LATE.c(); HwStatus.MISSING -> View.MISSING.c(); HwStatus.LEAVE -> View.LEAVE.c(); HwStatus.NONE -> View.NONE.c() }

@Composable
fun StatusButtons(enabled: Boolean = true, extra: (@Composable () -> Unit)? = null, onPick: (HwStatus) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ST.forEach { st -> ColorBtn(st.label, st.color(), enabled) { onPick(st) } }
        extra?.invoke()
    }
}
@Composable
fun ColorBtn(text: String, color: Color, enabled: Boolean = true, onClick: () -> Unit) =
    Text(text, Modifier.width(104.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = if (enabled) 0.14f else 0.06f)).clickable(enabled = enabled, onClick = onClick).padding(vertical = 10.dp),
        textAlign = TextAlign.Center, color = if (enabled) color else color.copy(alpha = 0.4f), fontWeight = FontWeight.SemiBold)

@Composable
fun AssignDialog(state: AppState, c: SchoolClass, a: Assignment?, onCreated: (String) -> Unit = {}) {
    var title by remember { mutableStateOf(a?.title ?: "") }; var subject by remember { mutableStateOf(a?.subject ?: "") }
    val close = { state.dialog = null }
    val save = {
        if (title.isNotBlank()) {
            close()
            if (a == null) { var id = ""; state.updateClass(c.id, "已新建作业「${title.trim()}」") { val (n, i) = Ops.createAssignment(it, title, subject); id = i; n }; if (id.isNotEmpty()) onCreated(id) }
            else state.updateClass(c.id, "已保存") { Ops.editAssignment(it, a.id) { x -> x.copy(title = title.trim(), subject = subject.trim()) } }
        }
    }
    Dialog(if (a == null) "新建作业" else "修改作业", close, buttons = {
        if (a != null) Btn("删除", primary = false, danger = true) { state.dialog = { Confirm(state, "删除这次作业？", "「${a.title}」的登记记录会一起删除，积分随之重算。删除后可以撤销。", "删除") { state.updateClass(c.id, "已删除「${a.title}」") { Ops.deleteAssignment(it, a.id) }; state.back() } } }
        Btn(if (a == null) "新建" else "保存", enabled = title.isNotBlank(), onClick = save)
    }) {
        Field("作业名称", title, "如：练习册 P12", onDone = save) { title = it }
        Field("科目", subject, "可不填") { subject = it }
        PickChips(state.data?.settings?.subjects.orEmpty(), subject) { subject = if (subject == it) "" else it }
    }
}

/** 同学的登记面板：状态、复审不合格、抄袭、评语（与安卓长按同学一样） */
@Composable
fun StudentSheet(state: AppState, c: SchoolClass, a: Assignment, s: Student, onProfile: () -> Unit) {
    var plag by remember { mutableStateOf(false) }
    var prov by remember { mutableStateOf(a.plagiarism[s.id].orEmpty().filter { it != "?" }.toSet()) }
    var comment by remember { mutableStateOf(a.comments[s.id] ?: "") }
    val close = { state.dialog = null }
    fun act(msg: String, f: (SchoolClass) -> SchoolClass) { close(); state.updateClass(c.id, msg, f) }
    if (plag) {
        Dialog("${s.name} 抄了谁的？", close, buttons = {
            Btn("返回", primary = false) { plag = false }
            Btn(if (prov.isEmpty()) "记为抄袭（提供者不明）" else "记为抄袭（${prov.size} 人）") { act("${s.name}：抄袭") { Ops.markPlagiarism(it, a.id, listOf(s.id), prov) } }
        }) {
            Text("抄袭者和提供者都记一次未交，扣分翻倍，累犯逐次加重。不知道抄了谁的，可以一个都不选。", fontSize = 13.sp, color = sub())
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                sortStudents(c.students.filter { it.id != s.id }).forEach { o -> Chip(o.name, o.id in prov) { prov = if (o.id in prov) prov - o.id else prov + o.id } }
            }
        }
        return
    }
    val line = plagLine(c, a, s.id)
    Dialog(s.name, close) {
        Text("${s.no}${if (s.group.isNotBlank()) " · ${s.group}" else ""} · 当前：${viewOf(a, s.id).label}${if (line.isNotBlank()) "（$line）" else ""}", fontSize = 13.sp, color = sub())
        StatusButtons(extra = {
            ColorBtn("复审不合格", View.REVIEW.c()) { act("${s.name}：复审不合格") { Ops.markReviewFailed(it, a.id, listOf(s.id)) } }
            ColorBtn("抄袭…", View.PLAG.c()) { plag = true }
            if (line.isNotBlank()) ColorBtn("取消抄袭", Color(0xFF8E8E93)) { act("已取消 ${s.name} 的抄袭标记") { Ops.clearPlagiarism(it, a.id, listOf(s.id)) } }
        }) { st -> act("${s.name}：${st.label}") { Ops.setStatus(it, a.id, listOf(s.id), st) } }
        Field("评语", comment, "写给这位同学的评语（可不填）") { comment = it }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Btn("查看档案", primary = false) { close(); onProfile() }
            Btn("保存评语") { act(if (comment.isBlank()) "已删除评语" else "已保存评语") { Ops.setComment(it, a.id, s.id, comment) } }
        }
    }
}

// ---------------- 学生 ----------------
@Composable
fun StudentDialog(state: AppState, c: SchoolClass, s: Student?) {
    var name by remember { mutableStateOf(s?.name ?: "") }; var no by remember { mutableStateOf(s?.no ?: "") }; var group by remember { mutableStateOf(s?.group ?: "") }
    var gender by remember { mutableStateOf(s?.gender ?: "") }; var note by remember { mutableStateOf(s?.note ?: "") }; var leave by remember { mutableStateOf(s?.longLeave ?: false) }
    var rep by remember { mutableStateOf(s != null && s.id in c.reps) }; var leader by remember { mutableStateOf(s != null && s.group.isNotBlank() && c.groupLeaders[s.group] == s.id) }
    val dup = no.isNotBlank() && c.students.any { it.no == no.trim() && it.id != s?.id }
    val close = { state.dialog = null }
    Dialog(if (s == null) "添加同学" else "修改同学", close, buttons = {
        if (s != null) Btn("删除", primary = false, danger = true) { state.dialog = { Confirm(state, "删除 ${s.name}？", "这位同学的作业登记、加减分、考试成绩会一起删除。删除后可以撤销。", "删除") { state.updateClass(c.id, "已删除 ${s.name}") { Ops.deleteStudent(it, s.id) }; state.back() } } }
        Btn("保存", enabled = name.isNotBlank() && !dup) {
            close()
            val st = (s ?: Student(no = "", name = "")).copy(name = name.trim(), no = no.trim(), group = group.trim(), gender = gender, note = note.trim(), longLeave = leave)
            state.updateClass(c.id, if (s == null) "已添加 ${st.name}" else "已保存 ${st.name}") { Ops.saveStudent(it, st, rep, leader && st.group.isNotBlank()) }
        }
    }) {
        Field("姓名", name) { name = it }
        Field("学号", no) { no = it }
        if (dup) Text("这个学号已经有人用了", color = BAD, fontSize = 12.sp)
        Field("小组", group, "可不填") { group = it }
        PickChips(c.students.map { it.group }.filter { it.isNotBlank() }.distinct().sorted(), group) { group = if (group == it) "" else it }
        Seg(listOf("" to "性别不填", "男" to "男", "女" to "女"), gender) { gender = it }
        Field("备注", note, "可不填") { note = it }
        SuperSwitch(title = "长期请假", summary = "之后新建的作业自动记为请假，不扣分", checked = leave, onCheckedChange = { leave = it })
        SuperSwitch(title = "课代表", summary = "每次结算加分，可以同时是组长", checked = rep, onCheckedChange = { rep = it })
        SuperSwitch(title = "组长" + if (group.isNotBlank()) "（${group.trim()}）" else "", summary = if (group.isBlank()) "先填小组" else "按小组人均扣分计组长奖惩", checked = leader && group.isNotBlank(), onCheckedChange = { leader = it })
    }
}

@Composable
fun ImportDialog(state: AppState, c: SchoolClass) {
    var text by remember { mutableStateOf("") }; var replace by remember { mutableStateOf(false) }
    val r = remember(text) { if (text.isBlank()) null else runCatching { RosterImport.parse(text) }.getOrNull() }
    val n = r?.students?.size ?: 0
    val close = { state.dialog = null }
    Dialog("导入名单", close, buttons = { Btn("导入 $n 人", enabled = n > 0) { close(); state.updateClass(c.id, "已导入 $n 人") { Ops.importRoster(it, r!!.students, replace) } } }) {
        Text("粘贴 Excel 的两三列（学号、姓名、小组），每行一人；也支持 CSV / JSON 名单。", fontSize = 13.sp, color = sub())
        val colors = MiuixTheme.colorScheme
        BasicTextField(text, { text = it }, textStyle = TextStyle(color = colors.onSurface, fontSize = 14.sp), cursorBrush = SolidColor(colors.primary),
            modifier = Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(12.dp)).background(track()).padding(12.dp))
        if (n > 0) Text("识别到 $n 人：" + r!!.students.take(8).joinToString("、") { "${it.no} ${it.name}" } + if (n > 8) "…" else "", fontSize = 13.sp, color = sub())
        Seg(listOf(false to "按学号合并", true to "替换整个名单"), replace) { replace = it }
        if (replace) Text("替换会清空本班的作业、积分、考试记录", color = BAD, fontSize = 12.sp)
    }
}

// ---------------- 积分 ----------------
@Composable
fun ScoreDialog(state: AppState, c: SchoolClass, ids: List<String>, title: String, defaultReason: String = "") {
    var sign by remember { mutableStateOf(1) }; var v by remember { mutableStateOf("1") }; var reason by remember { mutableStateOf("") }
    val d = num(v)?.let { sign * kotlin.math.abs(it) }
    val close = { state.dialog = null }
    Dialog(title, close, buttons = { Btn(if (d != null && d != 0.0) "确定 ${d.signed()}" else "确定", enabled = d != null && d != 0.0) { close(); state.updateClass(c.id, "已给 ${ids.size} 人 ${d!!.signed()}") { Ops.addScore(it, ids, d, reason.ifBlank { defaultReason }) } } }) {
        Seg(listOf(1 to "加分", -1 to "扣分"), sign) { sign = it }
        PickChips(listOf("1", "2", "3", "5", "10"), v) { v = it }
        Field("分值", v) { v = it }
        Field("原因", reason, "可不填") { reason = it }
        PickChips(state.data?.settings?.quickReasons.orEmpty(), reason) { reason = if (reason == it) "" else it }
    }
}

@Composable
fun RechargeDialog(state: AppState, c: SchoolClass, rules: ScoreRules, ids: List<String>) {
    var t by remember { mutableStateOf(rules.initial.fmt()) }; var note by remember { mutableStateOf("") }; var today by remember { mutableStateOf(false) }
    val close = { state.dialog = null }
    val x = num(t)
    Dialog("积分重置", close, buttons = { Btn("重置 ${ids.size} 人", enabled = x != null) { close(); state.updateClass(c.id, "已把 ${ids.size} 人的积分重置为 ${x!!.fmt()}") { Ops.recharge(it, rules, ids, x, note, if (today) com.deepshawn.hwcounter.data.startOfDay(System.currentTimeMillis()) else null) } } }) {
        Text("只重置积分并留下记录：此前的加减分、作业状态、未交次数全部保留，可以随时撤销。", fontSize = 13.sp, color = sub())
        Field("重置为", t) { t = it }
        Field("备注", note, "如：期中重新计分") { note = it }
        Seg(listOf(false to "立即", true to "从今天 0 点起"), today) { today = it }
    }
}

// ---------------- 考试 ----------------
@Composable
fun ExamDialog(state: AppState, c: SchoolClass, e: Exam?, onCreated: (String) -> Unit = {}) {
    var title by remember { mutableStateOf(e?.title ?: "") }; var subject by remember { mutableStateOf(e?.subject ?: "") }
    var full by remember { mutableStateOf((e?.fullScore ?: 100.0).fmt()) }; var note by remember { mutableStateOf(e?.note ?: "") }; var auto by remember { mutableStateOf(e?.autoScore ?: true) }
    val close = { state.dialog = null }
    val f = num(full)
    Dialog(if (e == null) "新建考试" else "修改考试", close, buttons = {
        if (e != null) Btn("删除", primary = false, danger = true) { state.dialog = { Confirm(state, "删除这场考试？", "「${e.title}」的成绩会一起删除，积分随之重算。删除后可以撤销。", "删除") { state.updateClass(c.id, "已删除「${e.title}」") { Ops.deleteExam(it, e.id) }; state.back() } } }
        Btn(if (e == null) "新建" else "保存", enabled = title.isNotBlank() && f != null && f > 0) {
            close()
            val n = (e ?: Exam(title = "")).copy(title = title.trim(), subject = subject.trim(), fullScore = f!!, note = note.trim(), autoScore = auto)
            state.updateClass(c.id, if (e == null) "已新建考试「${n.title}」" else "已保存") { Ops.upsertExam(it, n) }
            if (e == null) onCreated(n.id)
        }
    }) {
        Field("考试名称", title, "如：第一单元测验") { title = it }
        Field("科目", subject, "可不填") { subject = it }
        PickChips(state.data?.settings?.subjects.orEmpty(), subject) { subject = if (subject == it) "" else it }
        Field("满分", full) { full = it }
        Field("备注", note, "可不填") { note = it }
        SuperSwitch(title = "按得分率自动加减积分", summary = "分档与安卓一致", checked = auto, onCheckedChange = { auto = it })
    }
}

/** 成绩录入：一行一位同学 */
@Composable
fun ExamScoreRow(state: AppState, c: SchoolClass, e: Exam, s: Student) {
    val ab = s.id in e.absent; val v = e.scores[s.id]
    var t by remember(e.id, s.id, v) { mutableStateOf(v?.fmt() ?: "") }
    val commit = {
        val x = num(t)
        when {
            t.isBlank() -> if (v != null) state.updateClass(c.id) { Ops.setExamScore(it, e.id, s.id, null, false) }
            x == null || x < 0 -> t = v?.fmt() ?: ""
            x != v -> state.updateClass(c.id) { Ops.setExamScore(it, e.id, s.id, x, false) }
        }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f)) { Text(s.name, fontWeight = FontWeight.Medium); Text(s.no, fontSize = 12.sp, color = sub()) }
        if (ab) Pill("缺考", Color(0xFF8E8E93))
        else Box(Modifier.width(110.dp)) { Field(null, t, "—", onDone = commit) { t = it } }
        if (!ab && t != (v?.fmt() ?: "")) Text("保存", Modifier.clip(CircleShape).clickable(onClick = commit).padding(8.dp), color = MiuixTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        Chip(if (ab) "取消缺考" else "缺考", false) { state.updateClass(c.id) { Ops.setExamScore(it, e.id, s.id, null, !ab) } }
    }
}

// ---------------- 班级 ----------------
private val EMOJIS = listOf("🎒", "📚", "✏️", "🧪", "🌏", "🎨", "⚽️", "🎵", "🦊", "🐼", "🚀", "🌟")
@Composable
fun ClassDialog(state: AppState, c: SchoolClass?) {
    var name by remember { mutableStateOf(c?.name ?: "") }; var emoji by remember { mutableStateOf(c?.emoji ?: "🎒") }
    val close = { state.dialog = null }
    Dialog(if (c == null) "新建班级" else "修改班级", close, buttons = {
        if (c != null) Btn("删除班级", primary = false, danger = true) { state.dialog = { Confirm(state, "删除「${c.name}」？", "这个班级的名单、作业、积分、考试会全部删除。删除后可以撤销。", "删除班级") { state.update("已删除「${c.name}」") { Ops.deleteClass(it, c.id) }; state.prefs.classId = null } } }
        Btn("保存", enabled = name.isNotBlank()) {
            close()
            if (c == null) { var id = ""; state.update("已新建班级「${name.trim()}」") { val (d, i) = Ops.addClass(it, name, emoji); id = i; d }; if (id.isNotEmpty()) { state.prefs.classId = id; state.stack.clear() } }
            else state.update("已保存") { Ops.renameClass(it, c.id, name, emoji) }
        }
    }) {
        Field("班级名称", name, "如：高一(3)班") { name = it }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { EMOJIS.forEach { e -> Chip(e, e == emoji) { emoji = e } } }
    }
}

// ---------------- 账号 ----------------
/** 登录（老师用邮箱，凭邀请码注册的用户用用户名） */
@Composable
fun LoginGroup(state: AppState) {
    var user by remember { mutableStateOf("") }; var pw by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }; var err by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val go = {
        if (user.isNotBlank() && pw.isNotEmpty() && !busy) {
            busy = true; err = ""
            scope.launch { runCatching { state.live?.signIn(user, pw) }.onFailure { err = it.message ?: "登录失败" }.onSuccess { pw = "" }; busy = false }
        }
    }
    Group("账号") {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(null, user, "邮箱（老师）或用户名") { user = it }
            Field(null, pw, "密码", password = true, onDone = go) { pw = it }
            if (err.isNotEmpty()) Text(err, color = BAD, fontSize = 13.sp)
            Row { Btn(if (busy) "登录中…" else "登录", enabled = user.isNotBlank() && pw.isNotEmpty() && !busy, onClick = go) }
            Text("老师登录后可以登记作业、加减分、录入成绩、修改名单，改动自动同步到手机和 iPad；凭邀请码注册的用户用用户名登录，只读查看。", fontSize = 12.sp, color = sub())
        }
    }
}

@Composable
fun AccountGroup(state: AppState) {
    val me = state.me
    Group("账号") {
        Cell(if (me != null && me.isMember) "${me.name}（${me.username}）" else Account.displayEmail(),
            if (me?.isMember == true) (if (me.status == "active") "普通用户 · 只读查看老师的班级数据" else "账号已被老师停用") else "老师账号（管理员）")
        Cell("修改密码", "先输入旧密码，再设置新密码", onClick = { state.dialog = { ChangePasswordDialog(state) } })
        Cell("退出登录", if (state.editable) "退出前先把改动上传到云端，回到未登录预览" else "回到未登录预览", onClick = { state.live?.signOut() })
    }
}

private fun strongPw(p: String) = p.length >= 6 && p.any { it.isLowerCase() } && p.any { it.isUpperCase() } && p.any { it.isDigit() } && p.any { !it.isLetterOrDigit() }

@Composable
fun ChangePasswordDialog(state: AppState) {
    var old by remember { mutableStateOf("") }; var n1 by remember { mutableStateOf("") }; var n2 by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }; var err by remember { mutableStateOf("") }; var ok by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val member = state.me?.isMember == true
    val close = { if (!busy) state.dialog = null }
    if (ok) { Dialog("修改密码", close, buttons = { Btn("好的", onClick = close) }) { Text("密码已修改，下次登录请用新密码。") }; return }
    Dialog("修改密码", close, buttons = {
        Btn(if (busy) "提交中…" else "确认修改", enabled = !busy && old.isNotEmpty() && n1.isNotEmpty()) {
            err = when { n1.length < 6 -> "新密码至少 6 位"; member && !strongPw(n1) -> "新密码需要同时包含小写字母、大写字母、数字和符号（如 Abc123!）"; n1 != n2 -> "两次输入的新密码不一样"; n1 == old -> "新密码不能和旧密码一样"; else -> "" }
            if (err.isEmpty()) { busy = true; scope.launch { runCatching { Account.changePassword(old, n1) }.onSuccess { ok = true }.onFailure { err = it.message ?: "修改失败" }; busy = false } }
        }
    }) {
        Field("旧密码", old, password = true) { old = it }
        Field("新密码", n1, if (member) "含小写、大写、数字和符号" else "至少 6 位", password = true) { n1 = it }
        Field("再输一次新密码", n2, password = true) { n2 = it }
        if (!member) Text("这个账号和你的游戏共用，改完后游戏里也用新密码登录。", fontSize = 12.sp, color = sub())
        if (err.isNotEmpty()) Text(err, color = BAD, fontSize = 13.sp)
    }
}

/** 云端同步（老师） */
@Composable
fun CloudGroup(state: AppState) {
    val live = state.live ?: return
    val sy = state.sync
    val last = Account.lastSyncAt
    Group("云端同步") {
        Cell(Account.displayEmail(), "云端版本 v${Account.baseVersion}" + (if (last > 0) " · 上次同步 ${fmtDay(last)} ${java.text.SimpleDateFormat("HH:mm").format(java.util.Date(last))}" else "") + if (sy.msg.isNotBlank()) " · ${sy.msg}" else "")
        SuperSwitch(title = "自动同步", summary = "改动后几秒自动上传；每分钟检查其它设备的更新", checked = Account.auto, onCheckedChange = { live.setAuto(it) })
        SuperSwitch(title = "允许未登录预览", summary = "打开后，任何人不登录也能只读查看你的班级数据；关闭即恢复私密", checked = sy.publicRead != false, onCheckedChange = { live.setPublic(it) })
        Cell(if (sy.busy) "同步中…" else "立即同步", onClick = if (sy.busy) null else ({ live.syncNow() }))
        Cell("历史版本", "云端保留最近 50 份 + 90 天内每天一份，可一键恢复", onClick = { state.dialog = { HistoryDialog(state) } })
    }
}

@Composable
fun HistoryDialog(state: AppState) {
    var list by remember { mutableStateOf<List<HistoryItem>?>(null) }; var err by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { runCatching { Account.history() }.onSuccess { list = it }.onFailure { err = it.message ?: "读取失败" } }
    val scope = rememberCoroutineScope()
    Dialog("历史版本", { state.dialog = null }) {
        if (err.isNotEmpty()) Text(err, color = BAD)
        val l = list
        if (l == null && err.isEmpty()) Text("正在读取…", color = sub())
        if (l != null && l.isEmpty()) Text("还没有历史版本（云端数据被覆盖前自动保存）", color = sub())
        l?.forEach { h ->
            Cell("v${h.version} · ${h.savedAt.take(16).replace('T', ' ')}", h.device ?: "未知设备", onClick = {
                state.dialog = { Confirm(state, "恢复这个版本？", "把数据恢复成 v${h.version}。现在的数据会先保存在历史里，恢复后也可以撤销。", "恢复") {
                    scope.launch { runCatching { Account.historyData(h.id) }.onSuccess { d -> state.update("已恢复到 v${h.version}") { d } }.onFailure { state.say(it.message ?: "恢复失败") } }
                } }
            })
        }
    }
}

/** 计分规则（老师；改后历史积分自动重算） */
@Composable
fun RulesGroup(state: AppState, rules: ScoreRules) {
    fun set(f: (ScoreRules) -> ScoreRules) = state.update(null, undoable = false) { d -> d.copy(settings = d.settings.copy(rules = f(d.settings.rules))) }
    @Composable fun NumRow(title: String, value: Double, min: Double? = null, f: (ScoreRules, Double) -> ScoreRules) {
        var t by remember(value) { mutableStateOf(value.fmt()) }
        val commit = { val x = num(t); if (x == null || (min != null && x < min)) t = value.fmt() else if (x != value) set { f(it, x) } }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), fontSize = 15.sp)
            Box(Modifier.width(110.dp)) { Field(null, t, onDone = commit) { t = it } }
            if (t != value.fmt()) Text("保存", Modifier.padding(start = 6.dp).clip(CircleShape).clickable(onClick = commit).padding(8.dp), color = MiuixTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        }
    }
    val e = rules.escalation
    Group("作业计分（改后历史积分自动重算）") {
        NumRow("初始分", rules.initial) { r, x -> r.copy(initial = x) }
        NumRow("已交", rules.submitted) { r, x -> r.copy(submitted = x) }
        NumRow("优秀", rules.excellent) { r, x -> r.copy(excellent = x) }
        NumRow("迟交", rules.late) { r, x -> r.copy(late = x) }
        if (!e.enabled) NumRow("未交", rules.missing) { r, x -> r.copy(missing = x) }
        NumRow("请假", rules.leave) { r, x -> r.copy(leave = x) }
        SuperSwitch(title = "连续未交自动扣分", summary = if (e.enabled) e.describe() else "已关闭：每次未交固定扣分", checked = e.enabled, onCheckedChange = { v -> set { it.copy(escalation = it.escalation.copy(enabled = v)) } })
        if (e.enabled) {
            Seg(listOf(1 to "① 正常加倍", 2 to "② 第 2 次起加倍", 3 to "③ 分段倍率"), e.mode, Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) { v -> set { it.copy(escalation = it.escalation.copy(mode = v)) } }
            NumRow("底数（第 1 次扣的分）", e.base, 0.0) { r, x -> r.copy(escalation = r.escalation.copy(base = x)) }
            NumRow("单次最多扣（0 为不限）", e.cap, 0.0) { r, x -> r.copy(escalation = r.escalation.copy(cap = x)) }
            SuperSwitch(title = "间断未交平衡", summary = if (e.gapBalance) "隔几次不交一次也逐次加重，最多按第 ${e.gapMax} 次算" else "只看连续未交", checked = e.gapBalance, onCheckedChange = { v -> set { it.copy(escalation = it.escalation.copy(gapBalance = v)) } })
        }
        SuperSwitch(title = "连续交满后每次加分", summary = "本轮连续按时交满 ${rules.streakBonus.after} 次后，之后每连续交一次 +${rules.streakBonus.bonus.fmt()}", checked = rules.streakBonus.enabled, onCheckedChange = { v -> set { it.copy(streakBonus = it.streakBonus.copy(enabled = v)) } })
        SuperSwitch(title = "积分取整", summary = "正分向上、负分向下取整；单笔保留原值", checked = rules.roundPoints, onCheckedChange = { v -> set { it.copy(roundPoints = v) } })
        SuperSwitch(title = "作业复审", summary = "复审不合格记一次未交，扣分 ×${rules.review.multiplier.fmt()}", checked = rules.review.enabled, onCheckedChange = { v -> set { it.copy(review = it.review.copy(enabled = v)) } })
        if (rules.review.enabled) NumRow("复审不合格扣分倍数", rules.review.multiplier, 1.0) { r, x -> r.copy(review = r.review.copy(multiplier = x)) }
        SuperSwitch(title = "抄袭处罚", summary = "抄袭者和提供者都记未交，扣分 ×${rules.plagiarism.multiplier.fmt()}" + if (rules.plagiarism.escalate) "，累犯逐次加重" else "", checked = rules.plagiarism.enabled, onCheckedChange = { v -> set { it.copy(plagiarism = it.plagiarism.copy(enabled = v)) } })
        if (rules.plagiarism.enabled) NumRow("抄袭扣分倍数", rules.plagiarism.multiplier, 1.0) { r, x -> r.copy(plagiarism = r.plagiarism.copy(multiplier = x)) }
        SuperSwitch(title = "考试按得分率加减积分", summary = "分数档与安卓一致", checked = rules.exam.enabled, onCheckedChange = { v -> set { it.copy(exam = it.exam.copy(enabled = v)) } })
    }
}

/** 老师：班级管理 */
@Composable
fun ClassesGroup(state: AppState, cur: SchoolClass?) {
    val d = state.data ?: return
    Group("班级") {
        d.classes.forEach { x -> Cell("${x.emoji} ${x.name}", "${x.students.size} 名学生 · ${x.assignments.size} 次作业" + if (x.id == cur?.id) " · 当前班级" else "", onClick = { state.dialog = { ClassDialog(state, x) } }) }
        Cell("新建班级", onClick = { state.dialog = { ClassDialog(state, null) } })
    }
}

/** 作业通图标：连续点三下 显示 / 隐藏登录入口（与网页、iPad 一致） */
@Composable
fun AboutLogo(state: AppState) {
    var taps by remember { mutableStateOf(0) }; var at by remember { mutableStateOf(0L) }; var tip by remember { mutableStateOf("") }
    LaunchedEffect(tip) { if (tip.isNotEmpty()) { kotlinx.coroutines.delay(2500); tip = "" } }
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.clip(RoundedCornerShape(14.dp)).clickable {
            val now = System.currentTimeMillis(); taps = if (now - at < 600) taps + 1 else 1; at = now
            if (taps >= 3) {
                taps = 0
                if (Account.loadSession() != null) tip = "已登录：可在页面顶部的「账号」退出登录"
                else { state.prefs.loginUnlocked = !state.prefs.loginUnlocked; tip = if (state.prefs.loginUnlocked) "已显示登录入口（在页面顶部）" else "已隐藏登录入口" }
            }
        }.padding(6.dp)) { LineIcon("📚", MiuixTheme.colorScheme.primary, size = 30.dp) }
        Text("作业通 · Windows 版 v$APP_VERSION", fontWeight = FontWeight.Bold)
        Text(tip.ifEmpty { "与手机、iPad、网页数据同步" }, fontSize = 12.5.sp, color = sub())
    }
}

/** 点按 = onTap；长按（触摸屏）或鼠标右键 = onMore */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun Modifier.pressable(onTap: () -> Unit, onMore: () -> Unit): Modifier = this
    .onClick(matcher = androidx.compose.foundation.PointerMatcher.mouse(androidx.compose.ui.input.pointer.PointerButton.Secondary), onClick = onMore)
    .combinedClickable(onClick = onTap, onLongClick = onMore)
