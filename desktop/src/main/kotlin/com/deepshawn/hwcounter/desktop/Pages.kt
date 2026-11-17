@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.deepshawn.hwcounter.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepshawn.hwcounter.data.LedgerRow
import com.deepshawn.hwcounter.data.SchoolClass
import com.deepshawn.hwcounter.data.ScoreRules
import com.deepshawn.hwcounter.data.Student
import com.deepshawn.hwcounter.data.autoPoints
import com.deepshawn.hwcounter.data.compareStudentNumbers
import com.deepshawn.hwcounter.data.fmt
import com.deepshawn.hwcounter.data.fmtDay
import com.deepshawn.hwcounter.data.ledger
import com.deepshawn.hwcounter.data.leaderOf
import com.deepshawn.hwcounter.data.ranks
import com.deepshawn.hwcounter.data.signed
import com.deepshawn.hwcounter.data.stat
import com.deepshawn.hwcounter.data.studentStats
import com.deepshawn.hwcounter.data.summary
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 页面上下文：当前班级、规则、导航 */
class Ctx(val state: AppState, val c: SchoolClass, val rules: ScoreRules) {
    fun open(r: Route) = state.open(r)
    fun back() = state.back()
    val stats by lazy { c.studentStats(rules) }
    val ranked by lazy { stats.sortedByDescending { it.score } }
    val rankOf by lazy { ranked.mapIndexed { i, s -> s.student.id to i + 1 }.toMap() }
    val scoreOf by lazy { stats.associate { it.student.id to it.score } }
}

fun sortStudents(l: List<Student>) = l.sortedWith { a, b -> compareStudentNumbers(a.no, b.no).takeIf { it != 0 } ?: a.name.compareTo(b.name) }
private val ORDER = listOf(View.SUBMITTED, View.EXCELLENT, View.LATE, View.MISSING, View.REVIEW, View.PLAG, View.LEAVE, View.NONE)
private fun pts(d: Double) = if (d > 0) GOOD else BAD

@Composable
private fun Counts(c: SchoolClass, a: com.deepshawn.hwcounter.data.Assignment) {
    val ks = c.students.map { viewOf(a, it.id) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ORDER.forEach { k -> val n = ks.count { it == k }; if (n > 0) Text("${k.label} $n", color = k.c(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun Hero(onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Row(
        Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(cardColor())
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) { content() }
}

// ---------------- 首页 ----------------
@Composable
fun HomePage(x: Ctx) {
    val c = x.c
    val list = remember(c) { c.assignments.sortedByDescending { it.createdAt } }
    var limit by remember(c) { mutableIntStateOf(40) }
    Page(c.name, null) {
        Meta("${c.students.size} 人 · ${c.assignments.size} 次作业 · ${c.exams.size} 场考试")
        val latest = list.firstOrNull()
        if (latest != null) {
            val sm = c.summary(latest)
            Hero({ x.open(Route.Assign(latest.id)) }) {
                Ring(sm.rate.toDouble(), rateColor(sm.rate.toDouble())) { Text(pct(sm.rate), fontWeight = FontWeight.Bold, fontSize = 18.sp); Text("交齐率", fontSize = 11.sp, color = sub()) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("最近一次作业", fontSize = 12.sp, color = sub())
                    Text(latest.title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(fmtDay(latest.createdAt) + if (latest.subject.isNotBlank()) " · ${latest.subject}" else "", fontSize = 13.sp, color = sub())
                    Counts(c, latest)
                }
            }
        } else Empty("📚", "还没有作业")
        val avg = if (list.isEmpty()) null else list.map { c.summary(it).rate.toDouble() }.average()
        val missing = list.sumOf { c.summary(it).c(com.deepshawn.hwcounter.data.HwStatus.MISSING) }
        Tiles {
            Tile("作业", "${list.size}")
            Tile("平均交齐率", pct(avg), rateColor(avg))
            Tile("未交人次", "$missing", if (missing > 0) BAD else null)
            Tile("考试", "${c.exams.size}", onClick = if (c.exams.isNotEmpty()) ({ x.open(Route.Exams) }) else null)
        }
        if (list.isNotEmpty()) Group("全部作业") {
            list.take(limit).forEach { a ->
                val s = c.summary(a); val rv = c.students.count { a.failedReview(it.id) }; val pl = c.students.count { a.plagiarized(it.id) }
                val miss = s.c(com.deepshawn.hwcounter.data.HwStatus.MISSING) - rv - pl
                Cell(
                    a.title,
                    listOfNotNull(md(a.createdAt), a.subject.ifBlank { null }, if (miss > 0) "未交 $miss" else null, if (rv > 0) "复审不合格 $rv" else null, if (pl > 0) "抄袭 $pl" else null,
                        if (s.c(com.deepshawn.hwcounter.data.HwStatus.NONE) > 0) "未登记 ${s.c(com.deepshawn.hwcounter.data.HwStatus.NONE)}" else null).joinToString(" · "),
                    right = { Text(pct(s.rate), fontWeight = FontWeight.Bold, color = rateColor(s.rate.toDouble())) },
                    onClick = { x.open(Route.Assign(a.id)) },
                )
            }
        }
        if (list.size > limit) More("显示更多（还有 ${list.size - limit} 次）") { limit += 40 }
    }
}

@Composable
fun More(text: String, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
        Text(text, Modifier.clip(CircleShape).background(cardColor()).clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp), color = MiuixTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
    }
}

// ---------------- 作业详情 ----------------
@Composable
fun AssignPage(x: Ctx, id: String) {
    val c = x.c
    val a = c.assignments.firstOrNull { it.id == id } ?: run { Page("作业", x::back) { Empty(text = "这次作业已被删除") }; return }
    var filter by remember(id) { mutableStateOf<View?>(null) }
    val prefs = x.state.prefs
    val sm = c.summary(a)
    val ptsOf = remember(c, x.rules, id) { c.students.associate { it.id to (c.autoPoints(x.rules, it.id)[a.id] ?: 0.0) } }
    val keys = c.students.associate { it.id to viewOf(a, it.id) }
    val shown = sortStudents(c.students).filter { filter == null || keys[it.id] == filter }
    Page(a.title, x::back) {
        Meta(fmtDay(a.createdAt) + if (a.subject.isNotBlank()) " · ${a.subject}" else "")
        Hero {
            Ring(sm.rate.toDouble(), rateColor(sm.rate.toDouble())) { Text(pct(sm.rate), fontWeight = FontWeight.Bold, fontSize = 18.sp); Text("交齐率", fontSize = 11.sp, color = sub()) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("已交 ${sm.handedIn} / 应交 ${sm.expected}", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Counts(c, a)
                a.countedBooks?.let { Text("数本数：$it 本", fontSize = 13.sp, color = sub()) }
            }
        }
        // 这次作业自己的抄袭关系图（每次作业各自一张图）
        val plag = remember(c, x.rules, id) { if (a.plagiarism.isEmpty()) null else com.deepshawn.hwcounter.data.PlagiarismGraph.report(c, x.rules, id) }
        if (plag != null && plag.cases.isNotEmpty()) Group("本次抄袭关系图 · ${plag.copiers} 人抄袭、${plag.providers} 人提供") {
            Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) { PlagGraph(plag) { x.open(Route.Student(it)) } }
            Text((plag.cases.filter { it.unknown }.map { "提供者不明 → ${it.receiver.name}" } + plag.edges.map { "${it.provider.name} → ${it.receiver.name}" }).joinToString("　"), Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp), fontSize = 13.sp, color = sub())
        }
        FlowRow(Modifier.padding(horizontal = 12.dp).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("全部 ${c.students.size}", filter == null) { filter = null }
            ORDER.forEach { k -> val n = keys.values.count { it == k }; if (n > 0) Chip("${k.label} $n", filter == k) { filter = k } }
        }
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("同学 · ${shown.size} 人", Modifier.weight(1f).padding(start = 16.dp), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = sub())
            Seg(listOf("grid" to "网格", "list" to "列表"), prefs.layout, Modifier.width(180.dp)) { prefs.layout = it }
        }
        Spacer(Modifier.height(10.dp))
        if (prefs.layout == "list") Group {
            shown.forEach { s ->
                val k = keys.getValue(s.id); val d = ptsOf[s.id] ?: 0.0
                Cell(s.name, listOfNotNull(s.no, s.group.ifBlank { null }, if (k == View.PLAG) plagLine(c, a, s.id) else null, a.comments[s.id]?.let { "$it" }).joinToString(" · "),
                    left = { Avatar(s.name, 38.dp) },
                    right = { Row(verticalAlignment = Alignment.CenterVertically) { if (d != 0.0) Text(d.signed() + "  ", color = pts(d), fontWeight = FontWeight.Bold); Pill(k.label, k.c()) } },
                    onClick = { x.open(Route.Student(s.id)) })
            }
        } else FlowRow(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            shown.forEach { s ->
                val k = keys.getValue(s.id); val d = ptsOf[s.id] ?: 0.0
                Column(
                    Modifier.width(118.dp).clip(RoundedCornerShape(16.dp)).background(cardColor()).border(1.5.dp, k.c().copy(alpha = if (k == View.NONE) 0.15f else 0.55f), RoundedCornerShape(16.dp))
                        .clickable { x.open(Route.Student(s.id)) }.padding(vertical = 12.dp, horizontal = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Avatar(s.name, 40.dp)
                    Text(s.name, Modifier.padding(top = 4.dp), fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(s.no, fontSize = 10.5.sp, color = sub(), maxLines = 1)
                    Text((if (k == View.PLAG) plagLine(c, a, s.id).ifBlank { k.label } else k.label) + if (d != 0.0) " ${d.signed()}" else "", fontSize = 12.sp, color = k.c(), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2)
                    a.comments[s.id]?.let { Text("$it", fontSize = 10.5.sp, color = sub(), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
    }
}

@Composable
fun Chip(text: String, on: Boolean, onClick: () -> Unit) {
    val p = MiuixTheme.colorScheme.primary
    Text(text, Modifier.clip(CircleShape).background(if (on) p else track()).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 7.dp),
        color = if (on) Color.White else sub(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
}

// ---------------- 名单 ----------------
@Composable
fun RosterPage(x: Ctx) {
    val c = x.c
    var mode by remember { mutableStateOf("stu") }
    var q by remember { mutableStateOf("") }
    val groups = remember(c) { sortStudents(c.students).groupBy { it.group.ifBlank { "未分组" } }.toSortedMap(compareBy<String> { it.length }.thenBy { it }) }
    Page("名单", null) {
        Meta("${c.students.size} 名同学 · ${groups.keys.count { it != "未分组" }} 个小组")
        Seg(listOf("stu" to "学生", "grp" to "小组"), mode, Modifier.padding(horizontal = 12.dp).padding(bottom = 10.dp)) { mode = it }
        if (mode == "stu") {
            SearchBox(q, "搜索姓名、学号、小组") { q = it }
            val list = sortStudents(c.students).filter { q.isBlank() || it.name.contains(q) || it.no.contains(q) || it.group.contains(q) }
            if (list.isEmpty()) Empty("🔍", "没有找到")
            else Group {
                list.forEach { s ->
                    Cell(s.name, listOfNotNull(s.no, s.group.ifBlank { null }, if (s.longLeave) "长期请假" else null).joinToString(" · "), left = { Avatar(s.name) },
                        right = { Text((x.scoreOf[s.id] ?: 0.0).fmt(), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MiuixTheme.colorScheme.primary) }, onClick = { x.open(Route.Student(s.id)) })
                }
            }
        } else Group {
            groups.forEach { (g, ms) ->
                val avg = ms.map { x.scoreOf[it.id] ?: 0.0 }.average()
                val rates = ms.mapNotNull { handRate(c, it.id) }
                val r = if (rates.isEmpty()) null else rates.average()
                val leader = c.leaderOf(g)
                Cell(g, "${ms.size} 人" + (leader?.let { " · 组长 ${it.name}" } ?: "") + " · 平均积分 ${avg.fmt()}",
                    left = { Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(avatarColor(g)), contentAlignment = Alignment.Center) { Text(g.take(1), color = Color.White, fontWeight = FontWeight.Bold) } },
                    right = { Text(pct(r), fontWeight = FontWeight.Bold, color = rateColor(r)) }, onClick = { x.open(Route.Group(g)) })
            }
        }
    }
}

@Composable
fun SearchBox(value: String, hint: String, onChange: (String) -> Unit) {
    val colors = MiuixTheme.colorScheme
    Box(Modifier.padding(horizontal = 12.dp).padding(bottom = 10.dp).fillMaxWidth().height(42.dp).clip(RoundedCornerShape(12.dp)).background(cardColor()).padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
        if (value.isEmpty()) Text(hint, color = sub(), fontSize = 15.sp)
        BasicTextField(value, onChange, singleLine = true, textStyle = TextStyle(color = colors.onSurface, fontSize = 15.sp), cursorBrush = SolidColor(colors.primary), modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun GroupPage(x: Ctx, id: String) {
    val c = x.c
    val ms = sortStudents(c.students.filter { it.group.ifBlank { "未分组" } == id })
    Page(id, x::back) {
        Meta("${ms.size} 人")
        Group {
            ms.forEach { s ->
                Cell(s.name, "${s.no} · 交齐率 ${pct(handRate(c, s.id))}" + if (c.groupLeaders[id] == s.id) " · 组长" else "", left = { Avatar(s.name, 38.dp) },
                    right = { Text((x.scoreOf[s.id] ?: 0.0).fmt(), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MiuixTheme.colorScheme.primary) }, onClick = { x.open(Route.Student(s.id)) })
            }
        }
    }
}

// ---------------- 学生档案 ----------------
@Composable
fun StudentPage(x: Ctx, id: String) {
    val c = x.c
    val s = c.students.firstOrNull { it.id == id } ?: run { Page("学生", x::back) { Empty(text = "找不到这位同学") }; return }
    var more by remember(id) { mutableStateOf(false) }
    val rows = remember(c, x.rules, id) { c.ledger(x.rules, id) }
    val hist = remember(c) { c.assignments.sortedBy { it.createdAt } }
    val keys = hist.map { viewOf(it, id) }
    fun cnt(k: View) = keys.count { it == k }
    val r = handRate(c, id)
    val exams = c.exams.sortedByDescending { it.date }
    Page(s.name, x::back) {
        Hero {
            Avatar(s.name, 64.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(s.name, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(listOfNotNull(s.no, s.group.ifBlank { null }, s.gender.ifBlank { null }, if (s.longLeave) "长期请假" else null).joinToString(" · "), fontSize = 13.sp, color = sub())
                val roles = listOfNotNull(if (s.id in c.reps) "课代表" else null, if (s.group.isNotBlank() && c.leaderOf(s.group)?.id == s.id) "${s.group}组长" else null)
                if (roles.isNotEmpty()) Text(roles.joinToString(" · "), fontSize = 13.sp, color = MiuixTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                if (s.note.isNotBlank()) Text("${s.note}", fontSize = 13.sp, color = sub())
            }
            Column(horizontalAlignment = Alignment.End) {
                Text((x.scoreOf[id] ?: 0.0).fmt(), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MiuixTheme.colorScheme.primary)
                Text("积分 · 第 ${x.rankOf[id]} / ${c.students.size} 名", fontSize = 12.sp, color = sub())
            }
        }
        Tiles {
            Tile("交齐率", pct(r), rateColor(r)); Tile("已交", "${cnt(View.SUBMITTED)}", View.SUBMITTED.c()); Tile("优秀", "${cnt(View.EXCELLENT)}", View.EXCELLENT.c())
            Tile("迟交", "${cnt(View.LATE)}", View.LATE.c()); Tile("未交", "${cnt(View.MISSING) + cnt(View.REVIEW) + cnt(View.PLAG)}", View.MISSING.c())
        }
        Tiles {
            Tile("复审不合格", "${cnt(View.REVIEW)}", View.REVIEW.c()); Tile("抄袭", "${cnt(View.PLAG)}", View.PLAG.c()); Tile("请假", "${cnt(View.LEAVE)}", View.LEAVE.c())
            Tile("考试", "${exams.count { it.scores[id] != null }}")
        }
        if (hist.isNotEmpty()) {
            Title("作业足迹 · 最近 ${minOf(60, hist.size)} 次")
            FlowRow(Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(cardColor()).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                hist.takeLast(60).forEach { a -> val k = viewOf(a, id); Box(Modifier.size(16.dp).clip(RoundedCornerShape(5.dp)).background(k.c().copy(alpha = if (k == View.NONE) 0.3f else 1f)).clickable { x.open(Route.Assign(a.id)) }) }
            }
        }
        if (exams.isNotEmpty()) Group("考试") {
            exams.forEach { e ->
                val sc = e.scores[id]; val ab = id in e.absent
                val rk = e.ranks(); val n = rk.size
                Cell(e.title, "${md(e.date)}" + (if (e.subject.isNotBlank()) " · ${e.subject}" else "") + (rk[id]?.let { " · 第 $it / $n 名" } ?: ""),
                    right = { if (ab) Pill("缺考", Color(0xFF8E8E93)) else if (sc != null) Text("${sc.fmt()} / ${e.fullScore.fmt()}", fontWeight = FontWeight.Bold) else Text("—", color = sub()) },
                    onClick = { x.open(Route.Exam(e.id)) })
            }
        }
        Group("积分流水 · ${rows.size} 条") {
            if (rows.isEmpty()) Cell("还没有积分记录")
            (if (more) rows else rows.take(30)).forEach { row ->
                val kind = when (row.kind) { LedgerRow.Kind.MANUAL -> "加减分"; LedgerRow.Kind.AUTO -> "作业"; LedgerRow.Kind.RECHARGE -> "重置"; LedgerRow.Kind.EXAM -> "考试"; LedgerRow.Kind.SETTLE -> "结算" }
                Cell(row.title, "${fmtTime(row.time)} · $kind" + if (row.counted) "" else " · 重置前，不计入", dim = !row.counted,
                    right = { if (row.kind == LedgerRow.Kind.RECHARGE) Text("重置", color = sub()) else Text(row.delta.signed(), fontWeight = FontWeight.Bold, color = pts(row.delta)) })
            }
        }
        if (rows.size > 30 && !more) More("显示全部 ${rows.size} 条") { more = true }
    }
}

// ---------------- 积分 ----------------
@Composable
fun ScoresPage(x: Ctx) {
    val c = x.c
    var mode by remember { mutableStateOf("rank") }
    var limit by remember { mutableIntStateOf(60) }
    val vals = x.stats.map { it.score }
    Page("积分", null) {
        Tiles {
            Tile("平均分", if (vals.isEmpty()) "—" else vals.average().fmt())
            Tile("最高", vals.maxOrNull()?.fmt() ?: "—", GOOD)
            Tile("最低", vals.minOrNull()?.fmt() ?: "—", BAD)
        }
        Seg(listOf("rank" to "排行榜", "flow" to "加减分记录"), mode, Modifier.padding(horizontal = 12.dp).padding(bottom = 10.dp)) { mode = it }
        if (mode == "rank") Group {
            x.ranked.forEachIndexed { i, st ->
                Cell(st.student.name, "${st.student.no}" + (if (st.student.group.isNotBlank()) " · ${st.student.group}" else "") + " · 未交 ${st.missing} 次",
                    left = { Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.width(30.dp), contentAlignment = Alignment.Center) { if (i < 3) LineIcon("🏅", listOf(Color(0xFFE8A400), Color(0xFF9AA4B2), Color(0xFFC07A3A))[i], size = 20.dp) else Text("${i + 1}", textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, color = sub()) }; Avatar(st.student.name, 38.dp) } },
                    right = { Text(st.score.fmt(), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MiuixTheme.colorScheme.primary) }, onClick = { x.open(Route.Student(st.student.id)) })
            }
        } else {
            val names = c.students.associate { it.id to it.name }
            data class F(val t: Long, val d: Double?, val who: String, val title: String, val kind: String)
            val flow = remember(c) {
                (c.scores.map { F(it.time, it.delta, names[it.studentId] ?: "已删除的同学", it.reason.ifBlank { "加减分" }, if (it.settlementId != null) "结算" else "加减分") } +
                    c.recharges.map { F(it.time, null, "${it.studentIds.size} 人", "积分重置为 ${it.target.fmt()}" + if (it.note.isNotBlank()) " · ${it.note}" else "", "重置") }).sortedByDescending { it.t }
            }
            if (flow.isEmpty()) Empty(text = "还没有加减分记录")
            else Group { flow.take(limit).forEach { f -> Cell("${f.who} · ${f.title}", "${fmtTime(f.t)} · ${f.kind}", right = { if (f.d == null) Text("重置", color = sub()) else Text(f.d.signed(), fontWeight = FontWeight.Bold, color = pts(f.d)) }) } }
            if (flow.size > limit) More("显示更多") { limit += 60 }
        }
    }
}

// ---------------- 考试 ----------------
@Composable
fun ExamListPage(x: Ctx) {
    val exams = x.c.exams.sortedByDescending { it.date }
    Page("考试", x::back) {
        Group { exams.forEach { e -> val st = e.stat(x.c.students); Cell(e.title, "${fmtDay(e.date)}" + (if (e.subject.isNotBlank()) " · ${e.subject}" else "") + " · ${st.count} 人", right = { Text(if (st.count == 0) "—" else st.avg.fmt(), fontWeight = FontWeight.Bold) }, onClick = { x.open(Route.Exam(e.id)) }) } }
    }
}

@Composable
fun ExamPage(x: Ctx, id: String) {
    val c = x.c
    val e = c.exams.firstOrNull { it.id == id } ?: run { Page("考试", x::back) { Empty(text = "这场考试已被删除") }; return }
    val st = e.stat(c.students)
    val rows = c.students.mapNotNull { s -> e.scores[s.id]?.let { s to it } }.sortedByDescending { it.second }
    val absent = c.students.filter { it.id in e.absent }
    val none = c.students.filter { it.id !in e.scores && it.id !in e.absent }
    Page(e.title, x::back) {
        Meta(fmtDay(e.date) + (if (e.subject.isNotBlank()) " · ${e.subject}" else "") + " · 满分 ${e.fullScore.fmt()}" + if (e.note.isNotBlank()) " · ${e.note}" else "")
        Tiles {
            Tile("平均分", if (st.count == 0) "—" else st.avg.fmt()); Tile("最高", if (st.count == 0) "—" else st.max.fmt(), GOOD)
            Tile("最低", if (st.count == 0) "—" else st.min.fmt(), BAD); Tile("及格率", pct(st.passRate)); Tile("缺考", "${absent.size}")
        }
        Group("成绩排名 · ${rows.size} 人") {
            rows.forEachIndexed { i, (s, v) ->
                val d = x.rules.exam.deltaFor(e, s.id)
                Cell(s.name, "${s.no} · 得分率 ${pct(v / e.fullScore)}" + if (d != 0.0) " · 积分 ${d.signed()}" else "",
                    left = { Row(verticalAlignment = Alignment.CenterVertically) { Text("${i + 1}", Modifier.width(28.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, color = if (i < 3) MiuixTheme.colorScheme.primary else sub()); Avatar(s.name, 34.dp) } },
                    right = { Text(v.fmt(), fontWeight = FontWeight.Bold) }, onClick = { x.open(Route.Student(s.id)) })
            }
        }
        if (absent.isNotEmpty()) Group("缺考 · ${absent.size} 人") { absent.forEach { Cell(it.name, it.no) } }
        if (none.isNotEmpty()) Group("未录入 · ${none.size} 人") { Cell(none.joinToString("、") { it.name }) }
    }
}

// ---------------- 设置（只有本机的个性化显示） ----------------
@Composable
fun SettingsPage(x: Ctx, reload: () -> Unit) {
    val p = x.state.prefs
    Page("设置", null) {
        Title("主题配色")
        FlowRow(Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(cardColor()).padding(10.dp)) {
            Palettes.forEach { pl -> Swatch(pl, p.theme == pl.key) { p.theme = pl.key } }
        }
        Group("显示") {
            Text("深色模式", Modifier.padding(start = 16.dp, top = 12.dp, bottom = 6.dp), fontWeight = FontWeight.Medium)
            Seg(listOf(0 to "跟随系统", 1 to "浅色", 2 to "深色"), p.dark, Modifier.padding(horizontal = 12.dp).padding(bottom = 8.dp)) { p.dark = it }
            SuperSwitch(title = "界面动效", summary = "环形进度、图表生长动画", checked = p.animations, onCheckedChange = { p.animations = it })
            SuperSwitch(title = "美股配色", summary = if (p.usColors) "股票模式：绿涨红跌" else "股票模式：红涨绿跌", checked = p.usColors, onCheckedChange = { p.usColors = it })
            Text("作业页布局", Modifier.padding(start = 16.dp, top = 8.dp, bottom = 6.dp), fontWeight = FontWeight.Medium)
            Seg(listOf("grid" to "网格", "list" to "列表"), p.layout, Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) { p.layout = it }
        }
        Group("帮助") { Cell("新手提示", "重新查看 Windows 版的功能介绍", onClick = { x.state.guide = true }) }
        Group("数据") {
            Cell("刷新数据", when (x.state.load) { Load.LOADING -> "正在加载…"; Load.ERROR -> "加载失败，请检查网络"; else -> "重新获取老师公开的最新数据" }, onClick = reload)
        }
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            LineIcon("📚", MiuixTheme.colorScheme.primary, size = 30.dp)
            Text("作业通 · Windows 查看版 v$APP_VERSION", fontWeight = FontWeight.Bold)
            Text("Windows 版只能查看，修改请在手机或 iPad 上操作", fontSize = 12.5.sp, color = sub())
        }
    }
}
