package com.deepshawn.hwcounter.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepshawn.hwcounter.data.Assignment
import com.deepshawn.hwcounter.data.PlagiarismGraph
import com.deepshawn.hwcounter.data.SchoolClass
import com.deepshawn.hwcounter.data.ScoreRules
import com.deepshawn.hwcounter.data.Student
import com.deepshawn.hwcounter.data.compareStudentNumbers
import com.deepshawn.hwcounter.data.fmt
import com.deepshawn.hwcounter.data.fmtDay
import com.deepshawn.hwcounter.ui.components.Avatar
import com.deepshawn.hwcounter.ui.components.EmptyState
import com.deepshawn.hwcounter.ui.components.HCard
import com.deepshawn.hwcounter.ui.components.group
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** 抄袭的颜色（深橙） */
val PlagiarismColor = Color(0xFFD9480F)
private val ProviderColor = Color(0xFFE8792C)
private val CopierColor = Color(0xFFE5384D)
private val BothColor = Color(0xFF8E44AD)

fun roleColor(n: PlagiarismGraph.Node) = when { n.copied > 0 && n.provided > 0 -> BothColor; n.copied > 0 -> CopierColor; else -> ProviderColor }

/** 作业切换条上的短日期（11/17） */
private fun shortDay(t: Long): String = java.text.SimpleDateFormat("M/d", java.util.Locale.CHINA).format(java.util.Date(t))

/** 抄袭规则说明（作业页、设置页） */
fun ScoreRules.plagiarismText(): String =
    if (!plagiarism.enabled) "抄袭：按普通未交扣分"
    else "抄袭：抄袭者和提供者都记一次未交，扣分 ×${plagiarism.multiplier.fmt()}" + (if (plagiarism.escalate) "，累犯逐次加重（第 n 次再 ×n，最多 ×${plagiarism.maxTimes}）" else "") + if (review.enabled) "；同时复审不合格时倍数叠加" else ""

/** 选择“抄了谁的”：可多选，可搜索 */
@Composable
fun ProviderPickerDialog(c: SchoolClass, a: Assignment, receivers: Collection<String>, rules: ScoreRules, onDismiss: () -> Unit, onConfirm: (List<String>) -> Unit) {
    var q by remember { mutableStateOf("") }
    var picked by remember(receivers) { mutableStateOf(receivers.flatMap { a.copiedFrom(it) }.toSet()) }
    val names = c.students.filter { it.id in receivers }.joinToString("、") { it.name }
    val list = c.students.filter { it.id !in receivers && (q.isBlank() || it.name.contains(q.trim()) || it.no.contains(q.trim())) }
        .sortedWith { x, y -> compareStudentNumbers(x.no, y.no) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { androidx.compose.material3.Text("$names 抄了谁的？") },
        text = {
            Column {
                androidx.compose.material3.Text(rules.plagiarismText(), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.padding(4.dp))
                OutlinedTextField(q, { q = it }, singleLine = true, placeholder = { androidx.compose.material3.Text("搜索姓名或学号") }, modifier = Modifier.fillMaxWidth())
                LazyColumn(Modifier.heightIn(max = 340.dp)) {
                    items(list, key = { it.id }) { s ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { picked = if (s.id in picked) picked - s.id else picked + s.id }.padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(s.id in picked, { picked = if (s.id in picked) picked - s.id else picked + s.id })
                            Avatar(s.name, size = 30.dp)
                            Spacer(Modifier.width(10.dp))
                            androidx.compose.material3.Text("${s.name}  ", fontWeight = FontWeight.Medium)
                            androidx.compose.material3.Text(listOf(s.no, s.group).filter { it.isNotBlank() }.joinToString(" · "), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = picked.isNotEmpty(), onClick = { onConfirm(picked.toList()) }) { androidx.compose.material3.Text("记为抄袭（${picked.size} 位提供者）") } },
        dismissButton = { TextButton(onClick = onDismiss) { androidx.compose.material3.Text("取消") } },
    )
}

/** 这次作业里这位同学的抄袭关系（“抄袭 甲” / “提供给 乙 抄”） */
fun plagiarismLine(c: SchoolClass, a: Assignment, sid: String): String {
    fun names(ids: List<String>) = ids.mapNotNull { id -> c.students.firstOrNull { it.id == id }?.name }.joinToString("、")
    val from = a.copiedFrom(sid); val by = a.copiedBy(sid)
    return listOfNotNull(from.takeIf { it.isNotEmpty() }?.let { "抄袭 ${names(it)}" }, by.takeIf { it.isNotEmpty() }?.let { "提供给 ${names(it)} 抄" }).joinToString("；")
}

// ---------------- 统计页“抄袭”栏目 ----------------

@OptIn(ExperimentalLayoutApi::class)
fun LazyListScope.plagiarismSections(
    c: SchoolClass, rules: ScoreRules, r: PlagiarismGraph.Report, onOpen: (String) -> Unit, onAssignment: (String) -> Unit,
    /** 有抄袭的作业（最新在前）；选中的作业 id，null = 全部作业汇总 */
    assignments: List<Assignment> = emptyList(), sel: String? = null, onSel: (String?) -> Unit = {},
) {
    if (assignments.isEmpty() && r.cases.isEmpty()) {
        item(key = "pl-empty") { EmptyState("🕵️", "还没有抄袭记录", "在作业页长按同学（或多选后）点「抄袭」，选出抄了谁的，这里就会出现关系图。\n" + rules.plagiarismText()) }
        return
    }
    val one = assignments.firstOrNull { it.id == sel }
    if (assignments.isNotEmpty()) item(key = "pl-pick") {
        Column {
            SmallTitle("按作业看关系图 · 每次作业一张图")
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                com.deepshawn.hwcounter.ui.components.Chip2("全部作业 ${assignments.size}", sel == null, { onSel(null) }, color = PlagiarismColor)
                assignments.forEach { a ->
                    com.deepshawn.hwcounter.ui.components.Chip2(
                        "${shortDay(a.createdAt)} ${a.title.ifBlank { a.subject }} · ${PlagiarismGraph.involvedCount(c, a)}人",
                        a.id == sel, { onSel(a.id) }, color = PlagiarismColor,
                    )
                }
            }
        }
    }
    if (r.cases.isEmpty()) {
        item(key = "pl-empty-one") { EmptyState("🕵️", "这次作业没有抄袭记录", "选别的作业，或看全部作业汇总") }
        return
    }
    item(key = "pl-kpi") {
        val colors = MiuixTheme.colorScheme
        HCard(Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row {
                    listOf("${r.cases.size}" to "抄袭记录", "${r.copiers}" to "抄袭者", "${r.providers}" to "提供者", r.points.fmt() to if (one != null) "本次扣分" else "共扣分").forEach { (v, l) ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(v, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = PlagiarismColor)
                            Text(l, fontSize = 11.sp, color = colors.onSurfaceVariantSummary)
                        }
                    }
                }
                if (one != null) Text(
                    "${fmtDay(one.createdAt)} · ${one.subject} ${one.title}".trim(), fontSize = 13.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 10.dp).clip(RoundedCornerShape(8.dp)).clickable { onAssignment(one.id) },
                )
                Text(rules.plagiarismText() + "；改成其他状态即取消。", fontSize = 11.sp, color = colors.onSurfaceVariantSummary, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
    item(key = "pl-graph") {
        Column {
            SmallTitle(if (one != null) "本次作业的关系图 · 箭头从提供者指向抄袭者" else "全部作业汇总的关系图 · 线越粗次数越多")
            HCard(Modifier.padding(horizontal = 12.dp).fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    PlagiarismGraphView(r, onOpen)
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        listOf(ProviderColor to "提供者", CopierColor to "抄袭者", BothColor to "既抄又提供").forEach { (col, l) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(10.dp).clip(CircleShape).background(col)); Spacer(Modifier.width(4.dp))
                                Text(l, fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                            }
                        }
                    }
                }
            }
        }
    }
    group(if (one != null) "本次抄袭关系（提供者 → 抄袭者）" else "抄袭关系（提供者 → 抄袭者）", key = "pl-edges") {
        val colors = MiuixTheme.colorScheme
        Column(Modifier.padding(vertical = 6.dp)) {
            r.edges.forEach { e ->
                Row(Modifier.fillMaxWidth().clickable { onOpen(e.receiver.id) }.padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(e.provider.name, fontWeight = FontWeight.Medium, color = ProviderColor)
                    Text("  →  ", color = colors.onSurfaceVariantSummary)
                    Text(e.receiver.name, fontWeight = FontWeight.Medium, color = CopierColor, modifier = Modifier.weight(1f))
                    if (one == null) Column(horizontalAlignment = Alignment.End) {
                        Text("${e.count} 次", fontWeight = FontWeight.Bold)
                        Text(e.assignments.takeLast(2).joinToString("、") { it.title }, fontSize = 11.sp, color = colors.onSurfaceVariantSummary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(140.dp))
                    }
                }
            }
        }
    }
    group(if (one != null) "本次涉及的同学" else "涉及的同学（按次数）", key = "pl-nodes") {
        val colors = MiuixTheme.colorScheme
        Column(Modifier.padding(vertical = 6.dp)) {
            r.nodes.forEach { n ->
                Row(Modifier.fillMaxWidth().clickable { onOpen(n.student.id) }.padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(n.student.name, size = 36.dp, color = roleColor(n))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(n.student.name, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                            Spacer(Modifier.width(6.dp))
                            Text(n.role, fontSize = 10.sp, color = roleColor(n), modifier = Modifier.clip(CircleShape).background(roleColor(n).copy(alpha = 0.12f)).padding(horizontal = 6.dp, vertical = 1.dp))
                        }
                        Text(
                            if (one != null) listOfNotNull(
                                plagiarismLine(c, one, n.student.id).ifBlank { null },
                                if (n.roundTimes > 0) "本轮第 ${n.roundTimes} 次" + (if (n.factor > 0) "，扣分 ×${n.factor.fmt()}" else "") else null,
                            ).joinToString(" · ")
                            else listOfNotNull(if (n.copied > 0) "抄袭 ${n.copied} 次" else null, if (n.provided > 0) "提供 ${n.provided} 次" else null, "本轮第 ${n.roundTimes} 次" + if (rules.plagiarism.escalate && n.roundTimes > 0) "（下次 ×${rules.plagiarism.factor(n.roundTimes + 1).fmt()}）" else "").joinToString(" · "),
                            fontSize = 12.sp, color = colors.onSurfaceVariantSummary,
                        )
                    }
                    Text(n.points.fmt(), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = CopierColor)
                }
            }
        }
    }
    if (r.clusters.any { it.size >= 3 }) group("抄袭圈（互相连在一起的同学）", key = "pl-clusters") {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            r.clusters.filter { it.size >= 2 }.forEachIndexed { i, cl ->
                Text("第 ${i + 1} 圈 · ${cl.size} 人", fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    cl.forEach { s -> Text(s.name, fontSize = 12.sp, modifier = Modifier.clip(CircleShape).background(PlagiarismColor.copy(alpha = 0.12f)).clickable { onOpen(s.id) }.padding(horizontal = 10.dp, vertical = 4.dp)) }
                }
            }
        }
    }
    if (one == null) group("最近的抄袭记录", key = "pl-cases") {
        val colors = MiuixTheme.colorScheme
        Column(Modifier.padding(vertical = 6.dp)) {
            r.cases.take(20).forEach { k ->
                Row(Modifier.fillMaxWidth().clickable { onSel(k.assignment.id) }.padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${k.receiver.name} 抄袭 ${k.providers.joinToString("、") { it.name }}", fontWeight = FontWeight.Medium)
                        Text("${fmtDay(k.assignment.createdAt)} · ${k.assignment.subject} ${k.assignment.title}".trim(), fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
                    }
                    Text("看图 ›", fontSize = 12.sp, color = PlagiarismColor)
                }
            }
        }
    }
}

/** 关系图：同学排成一圈（同一个抄袭圈的挨在一起），箭头从提供者指向抄袭者；点头像进入个人档案 */
@Composable
fun PlagiarismGraphView(r: PlagiarismGraph.Report, onOpen: (String) -> Unit) {
    val colors = MiuixTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val nodeOf = r.nodes.associateBy { it.student.id }
    val order: List<Student> = r.clusters.flatten().filter { it.id in nodeOf }
    val n = order.size
    val maxCount = r.edges.maxOfOrNull { it.count } ?: 1
    val labelColor = colors.onSurface
    val lineColor = colors.onSurfaceVariantSummary
    var positions by remember { mutableStateOf<Map<String, Offset>>(emptyMap()) }
    Canvas(
        Modifier.fillMaxWidth().aspectRatio(1f)
            .semantics { contentDescription = "抄袭关系图：$n 位同学，${r.edges.size} 条关系" }
            .pointerInput(r) {
                detectTapGestures { o -> positions.minByOrNull { (o - it.value).getDistance() }?.takeIf { (o - it.value).getDistance() < 36.dp.toPx() }?.let { onOpen(it.key) } }
            },
    ) {
        val cx = size.width / 2; val cy = size.height / 2
        val nodeR = (if (n > 16) 13.dp else 18.dp).toPx()
        val ring = minOf(cx, cy) - nodeR - 22.dp.toPx()
        val pos = order.mapIndexed { i, s ->
            val ang = if (n == 1) -PI / 2 else -PI / 2 + 2 * PI * i / n
            s.id to Offset(cx + (ring * cos(ang)).toFloat(), cy + (ring * sin(ang)).toFloat())
        }.toMap()
        positions = pos
        // 边：从提供者到抄袭者，留出头像半径，画箭头
        for (e in r.edges) {
            val a = pos[e.provider.id] ?: continue; val b = pos[e.receiver.id] ?: continue
            val d = hypot(b.x - a.x, b.y - a.y).coerceAtLeast(1f)
            val ux = (b.x - a.x) / d; val uy = (b.y - a.y) / d
            val start = Offset(a.x + ux * nodeR, a.y + uy * nodeR); val end = Offset(b.x - ux * (nodeR + 2), b.y - uy * (nodeR + 2))
            val w = (1.5f + 3.5f * e.count / maxCount).dp.toPx()
            val col = PlagiarismColor.copy(alpha = 0.45f + 0.5f * e.count / maxCount)
            drawLine(col, start, end, w)
            val ang = atan2(uy, ux); val head = 9.dp.toPx() + w
            val p = Path().apply {
                moveTo(end.x, end.y)
                lineTo(end.x - head * cos(ang - 0.45f).toFloat(), end.y - head * sin(ang - 0.45f).toFloat())
                lineTo(end.x - head * cos(ang + 0.45f).toFloat(), end.y - head * sin(ang + 0.45f).toFloat())
                close()
            }
            drawPath(p, col)
            if (e.count > 1) {
                val mid = Offset((start.x + end.x) / 2, (start.y + end.y) / 2)
                val t = measurer.measure("${e.count}", TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White))
                drawCircle(PlagiarismColor, 9.dp.toPx(), mid)
                drawText(t, topLeft = Offset(mid.x - t.size.width / 2f, mid.y - t.size.height / 2f))
            }
        }
        // 节点
        for (s in order) {
            val o = pos.getValue(s.id); val node = nodeOf.getValue(s.id)
            val col = roleColor(node)
            drawCircle(col, nodeR, o)
            drawCircle(Color.White, nodeR, o, style = Stroke(2.dp.toPx()))
            val initial = measurer.measure(s.name.takeLast(1), TextStyle(fontSize = if (n > 16) 11.sp else 14.sp, fontWeight = FontWeight.Bold, color = Color.White))
            drawText(initial, topLeft = Offset(o.x - initial.size.width / 2f, o.y - initial.size.height / 2f))
            val label = measurer.measure(s.name, TextStyle(fontSize = 11.sp, color = labelColor))
            val below = o.y >= cy
            drawText(label, topLeft = Offset(o.x - label.size.width / 2f, if (below) o.y + nodeR + 2.dp.toPx() else o.y - nodeR - 2.dp.toPx() - label.size.height))
        }
        if (n == 0) drawCircle(lineColor, 2f, Offset(cx, cy))
    }
}

/** 作业页：这次作业的抄袭关系图，可收起 */
@Composable
fun AssignmentPlagiarismCard(r: PlagiarismGraph.Report, onOpen: (String) -> Unit) {
    var open by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(true) }
    val colors = MiuixTheme.colorScheme
    HCard(Modifier.padding(horizontal = 12.dp, vertical = 6.dp).fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { open = !open }.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("🕵️ 本次抄袭关系图", fontWeight = FontWeight.Bold, color = PlagiarismColor, modifier = Modifier.weight(1f))
                Text("${r.copiers} 人抄袭 · ${r.providers} 人提供 · ${if (open) "收起" else "展开"}", fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
            }
            if (open) {
                Box(Modifier.fillMaxWidth().padding(top = 4.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.widthIn(max = 360.dp).fillMaxWidth()) { PlagiarismGraphView(r, onOpen) }
                }
                r.edges.forEach { e ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(e.provider.name, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = ProviderColor)
                        Text("  →  ", fontSize = 13.sp, color = colors.onSurfaceVariantSummary)
                        Text(e.receiver.name, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = CopierColor)
                    }
                }
            }
        }
    }
}
