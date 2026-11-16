package com.deepshawn.hwcounter.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepshawn.hwcounter.data.GroupAnalysis
import com.deepshawn.hwcounter.data.SchoolClass
import com.deepshawn.hwcounter.data.ScoreRules
import com.deepshawn.hwcounter.data.fmt
import com.deepshawn.hwcounter.data.signed
import com.deepshawn.hwcounter.ui.components.Chip2
import com.deepshawn.hwcounter.ui.components.EmptyState
import com.deepshawn.hwcounter.ui.components.HCard
import com.deepshawn.hwcounter.ui.components.group
import com.deepshawn.hwcounter.ui.components.listCell
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

private val UP = Color(0xFF16A34A)
private val DOWN = Color(0xFFE5384D)
private fun pc(x: Double?) = if (x == null) "—" else "${(x * 100).roundToInt()}%"
private val TEAM_COLORS = listOf(Color(0xFF3482FF), Color(0xFFF76707), Color(0xFF12B886), Color(0xFFAE3EC9), Color(0xFFE64980), Color(0xFF1098AD), Color(0xFFF59F00), Color(0xFF4263EB))
fun teamColor(i: Int) = TEAM_COLORS[i % TEAM_COLORS.size]

/** 统计页“小组”栏目：对抗榜、每周对抗、进步与退步、两组对决、组长奖惩 */
fun LazyListScope.groupSections(c: SchoolClass, rules: ScoreRules, r: GroupAnalysis.Report, onGroup: (String) -> Unit) {
    if (r.teams.size < 1) { item(key = "grp-empty") { EmptyState("👥", "还没有小组", "在名单里给同学填写小组（每组至少 2 人）后，这里会出现小组对抗和进步分析") }; return }
    val colorOf = r.teams.map { it.name }.sorted().withIndex().associate { (i, g) -> g to teamColor(i) }

    // ---- 对抗榜 ----
    item(key = "grp-board-title") { SmallTitle("小组对抗排名 · 综合分（交齐 50% · 少扣分 25% · 优秀 15% · 考试 10%）") }
    itemsIndexed(r.teams, key = { _, t -> "grp-" + t.name }) { i, t ->
        val colors = MiuixTheme.colorScheme
        Row(Modifier.listCell(i, r.teams.size).clickable { onGroup(t.name) }.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(listOf("🥇", "🥈", "🥉").getOrElse(t.rank - 1) { "${t.rank}" }, Modifier.width(30.dp), fontSize = if (t.rank <= 3) 20.sp else 15.sp, fontWeight = FontWeight.Bold, color = colors.onSurfaceVariantSummary)
            Box(Modifier.size(10.dp).clip(CircleShape).background(colorOf.getValue(t.name)))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(t.name + (t.leader?.let { " · 组长 ${it.name}" } ?: ""), fontWeight = FontWeight.Medium, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "交齐 ${pc(t.rate)} · 人均扣分 ${t.deductPerCapita.fmt()} · 优秀 ${pc(t.excellentRate)}" + (t.examPct?.let { " · 考试 ${pc(it)}" } ?: "") +
                        "\n胜 ${t.wins}" + (if (t.ties > 0) " · 并列 ${t.ties}" else "") + " · 周冠军 ${t.weekWins} · 全组交齐 ${t.complete}/${t.played}" + (if (rules.groupRank.enabled && t.rankPoints != 0.0) " · 排名奖惩每人 ${t.rankPoints.signed()}" else ""),
                    fontSize = 11.5.sp, color = colors.onSurfaceVariantSummary, maxLines = 2,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${(t.score * 100).roundToInt()}", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = colorOf.getValue(t.name))
                Text("综合 · 均分 ${t.avgScore.fmt()}", fontSize = 11.sp, color = colors.onSurfaceVariantSummary)
            }
        }
    }

    // ---- 本期排名、历次排名（整组加减分） ----
    if (rules.groupRank.enabled && r.periods.isNotEmpty()) group("本期排名 · ${rules.groupRank.periodLabel} · ${r.periods.last().label}", key = "grp-now") {
        val colors = MiuixTheme.colorScheme
        Column(Modifier.padding(vertical = 6.dp)) {
            r.periods.last().standings.forEach { st ->
                Row(Modifier.fillMaxWidth().clickable { onGroup(st.group) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("第 ${st.rank} 名", Modifier.width(60.dp), fontWeight = FontWeight.SemiBold, color = colors.onSurfaceVariantSummary, fontSize = 13.sp)
                    Box(Modifier.size(10.dp).clip(CircleShape).background(colorOf[st.group] ?: colors.primary)); Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(st.group, fontWeight = FontWeight.Medium)
                        Text("综合 ${(st.score * 100).roundToInt()} · 交齐 ${pc(st.rate)} · 人均扣分 ${st.deduct.fmt()} · 优秀 ${pc(st.excellent)}" + (st.exam?.let { " · 考试 ${pc(it)}" } ?: ""), fontSize = 11.5.sp, color = colors.onSurfaceVariantSummary, maxLines = 2)
                    }
                    Text(if (st.points == 0.0) "0" else "每人 ${st.points.signed()}", fontWeight = FontWeight.Bold, color = if (st.points > 0) UP else if (st.points < 0) DOWN else colors.onSurfaceVariantSummary)
                }
            }
            Text("第 1 名全组每人 ${rules.groupRank.first.signed()}、第 2 名 ${rules.groupRank.second.signed()}、第 3 名 ${rules.groupRank.third.signed()}；3 组以上时最后一名 ${rules.groupRank.last.signed()}。实时计入积分（设置 → 作业计分 → 小组对抗排名）", fontSize = 11.sp, color = colors.onSurfaceVariantSummary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        }
    }
    if (rules.groupRank.enabled && r.periods.size >= 2) group("历次排名", key = "grp-history") {
        val colors = MiuixTheme.colorScheme
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            r.periods.asReversed().take(12).forEach { p ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(p.label, Modifier.width(96.dp), fontSize = 12.sp, color = colors.onSurfaceVariantSummary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        p.standings.forEach { st ->
                            val col = colorOf[st.group] ?: colors.primary
                            Text(
                                "${st.rank}. ${st.group}" + if (st.points != 0.0) " ${st.points.signed()}" else "",
                                fontSize = 12.sp, fontWeight = if (st.rank == 1) FontWeight.Bold else FontWeight.Normal, color = col,
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(col.copy(alpha = if (st.rank == 1) 0.18f else 0.08f)).padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    // ---- 每周对抗 ----
    if (r.teams.size >= 2 && r.weeks.isNotEmpty()) group("每周对抗 · 交齐率最高的组胜出", key = "grp-weeks") {
        val colors = MiuixTheme.colorScheme
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            r.weeks.asReversed().forEach { w ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(w.label, Modifier.width(78.dp), fontSize = 12.sp, color = colors.onSurfaceVariantSummary, maxLines = 1)
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        r.teams.sortedBy { it.name }.forEach { t ->
                            val v = w.rates[t.name]
                            Box(Modifier.weight(1f).height(18.dp).clip(RoundedCornerShape(5.dp)).background(colorOf.getValue(t.name).copy(alpha = if (v == null) 0.06f else 0.12f + 0.88f * ((v.toFloat() - 0.5f) / 0.5f).coerceIn(0f, 1f))), contentAlignment = Alignment.Center) {
                                if (t.name in w.winners) Text("★", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Text(if (w.winners.isEmpty()) "平" else w.winners.joinToString("、"), Modifier.width(64.dp).padding(start = 8.dp), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                r.teams.sortedBy { it.name }.forEach { t ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(colorOf.getValue(t.name))); Spacer(Modifier.width(4.dp))
                        Text(t.name, fontSize = 11.sp, color = colors.onSurfaceVariantSummary)
                    }
                }
                Text("颜色越深交齐率越高（50% 以下最浅）· ★ 本周第一", fontSize = 11.sp, color = colors.onSurfaceVariantSummary)
            }
        }
    }

    // ---- 进步 / 退步 ----
    if (r.progress.isNotEmpty()) group("小组进步与退步 · ${r.progressBasis}", key = "grp-progress") {
        val colors = MiuixTheme.colorScheme
        Column(Modifier.padding(vertical = 6.dp)) {
            r.progress.forEach { p ->
                val up = p.index >= 0.02; val down = p.index <= -0.02
                Row(Modifier.fillMaxWidth().clickable { onGroup(p.group) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (up) "📈" else if (down) "📉" else "➖", fontSize = 18.sp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.group + if (up) " · 进步" else if (down) " · 退步" else " · 持平", fontWeight = FontWeight.Medium, color = if (up) UP else if (down) DOWN else colors.onSurface)
                        Text(p.reasons.joinToString(" · ").ifBlank { "变化不大" }, fontSize = 11.5.sp, color = colors.onSurfaceVariantSummary, maxLines = 2)
                    }
                    Text("${if (p.index >= 0) "+" else ""}${(p.index * 100).roundToInt()}", fontWeight = FontWeight.Bold, color = if (up) UP else if (down) DOWN else colors.onSurfaceVariantSummary)
                }
            }
            Text("指数 = 交齐率变化 50% + 每次人均扣分减少 30% + 考试得分率变化 20%（没有考试时按比例分配）", fontSize = 11.sp, color = colors.onSurfaceVariantSummary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        }
    }

    // ---- 两组对决 ----
    if (r.teams.size >= 2) item(key = "grp-duel") {
        val colors = MiuixTheme.colorScheme
        val names = r.teams.map { it.name }.sorted()
        var a by remember(names) { mutableStateOf(r.teams[0].name) }
        var b by remember(names) { mutableStateOf(r.teams[1].name) }
        val duel = remember(c, a, b) { GroupAnalysis.duel(c, a, b) }
        val ta = r.teams.first { it.name == a }; val tb = r.teams.first { it.name == b }
        Column {
            SmallTitle("两组对决")
            HCard(Modifier.padding(horizontal = 12.dp).fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        names.forEach { n -> Chip2(n, n == a, { if (n != b) a = n else { b = a; a = n } }, color = colorOf.getValue(n)) }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        names.forEach { n -> Chip2(n, n == b, { if (n != a) b = n else { a = b; b = n } }, color = colorOf.getValue(n)) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(a, Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = colorOf.getValue(a))
                        Text("${duel.aWins} : ${duel.bWins}", fontWeight = FontWeight.Black, fontSize = 26.sp)
                        Text(b, Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = colorOf.getValue(b), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                    }
                    Text("每次作业比交齐率${if (duel.draws > 0) " · 平 ${duel.draws} 次" else ""}", fontSize = 11.sp, color = colors.onSurfaceVariantSummary, modifier = Modifier.align(Alignment.CenterHorizontally))
                    VsRow("交齐率", ta.rate, tb.rate, ::pcd, colorOf.getValue(a), colorOf.getValue(b))
                    VsRow("按时率", ta.onTimeRate, tb.onTimeRate, ::pcd, colorOf.getValue(a), colorOf.getValue(b))
                    VsRow("优秀率", ta.excellentRate, tb.excellentRate, ::pcd, colorOf.getValue(a), colorOf.getValue(b))
                    VsRow("平均积分", ta.avgScore, tb.avgScore, { it.fmt() }, colorOf.getValue(a), colorOf.getValue(b))
                    VsRow("人均作业扣分", ta.deductPerCapita, tb.deductPerCapita, { it.fmt() }, colorOf.getValue(a), colorOf.getValue(b), higherBetter = false)
                    if (ta.examPct != null && tb.examPct != null) VsRow("考试得分率", ta.examPct, tb.examPct, ::pcd, colorOf.getValue(a), colorOf.getValue(b))
                    VsRow("最长连续全组交齐", ta.longestComplete.toDouble(), tb.longestComplete.toDouble(), { "${it.toInt()} 次" }, colorOf.getValue(a), colorOf.getValue(b))
                    VsRow("周冠军", ta.weekWins.toDouble(), tb.weekWins.toDouble(), { "${it.toInt()} 周" }, colorOf.getValue(a), colorOf.getValue(b))
                    if (duel.recent.isNotEmpty()) {
                        Text("最近 ${duel.recent.size} 次", fontSize = 12.sp, color = colors.onSurfaceVariantSummary, modifier = Modifier.padding(top = 4.dp))
                        duel.recent.forEach { (x, ra, rb) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(pc(ra), Modifier.width(48.dp), fontSize = 12.sp, fontWeight = if ((ra ?: -1.0) > (rb ?: -1.0)) FontWeight.Bold else FontWeight.Normal, color = colorOf.getValue(a))
                                Text(x.title, Modifier.weight(1f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                Text(pc(rb), Modifier.width(48.dp), fontSize = 12.sp, fontWeight = if ((rb ?: -1.0) > (ra ?: -1.0)) FontWeight.Bold else FontWeight.Normal, color = colorOf.getValue(b), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                            }
                        }
                    }
                }
            }
        }
    }

    // ---- 组长奖惩 ----
    val leaders = r.teams.filter { it.leader != null }
    if (leaders.isNotEmpty()) group("组长奖惩 · ${rules.leader.periodLabel}（计入当前积分）", key = "grp-leaders") {
        val colors = MiuixTheme.colorScheme
        Column(Modifier.padding(vertical = 6.dp)) {
            if (!rules.leader.enabled) Text("组长奖惩已关闭（设置 → 作业计分 → 组长奖惩）", fontSize = 12.sp, color = colors.onSurfaceVariantSummary, modifier = Modifier.padding(16.dp))
            else leaders.sortedByDescending { it.leaderPoints }.forEach { t ->
                Row(Modifier.fillMaxWidth().clickable { onGroup(t.name) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${t.leader!!.name} · ${t.name}", fontWeight = FontWeight.Medium)
                        Text("${t.leaderPeriods} 段 · 全部作业人均扣分 ${t.deductPerCapita.fmt()}", fontSize = 11.5.sp, color = colors.onSurfaceVariantSummary)
                    }
                    Text(t.leaderPoints.signed(), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = if (t.leaderPoints > 0) UP else if (t.leaderPoints < 0) DOWN else colors.onSurfaceVariantSummary)
                }
            }
        }
    }
}

private fun pcd(x: Double) = pc(x)

/** 对比行：两边条形，较好的一边加粗 */
@Composable
private fun VsRow(label: String, x: Double, y: Double, show: (Double) -> String, ca: Color, cb: Color, higherBetter: Boolean = true) {
    val colors = MiuixTheme.colorScheme
    val max = maxOf(kotlin.math.abs(x), kotlin.math.abs(y), 1e-9)
    val aBetter = if (higherBetter) x > y + 1e-9 else x < y - 1e-9
    val bBetter = if (higherBetter) y > x + 1e-9 else y < x - 1e-9
    Column {
        Row {
            Text(show(x), Modifier.weight(1f), fontSize = 13.sp, fontWeight = if (aBetter) FontWeight.Bold else FontWeight.Normal, color = if (aBetter) ca else colors.onSurface)
            Text(label, fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
            Text(show(y), Modifier.weight(1f), fontSize = 13.sp, fontWeight = if (bBetter) FontWeight.Bold else FontWeight.Normal, color = if (bBetter) cb else colors.onSurface, textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }
        Row(Modifier.fillMaxWidth().padding(top = 3.dp).height(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.weight(1f).height(6.dp), contentAlignment = Alignment.CenterEnd) {
                Box(Modifier.fillMaxWidth((kotlin.math.abs(x) / max).toFloat().coerceIn(0.02f, 1f)).height(6.dp).clip(CircleShape).background(ca.copy(alpha = if (aBetter) 1f else 0.4f)))
            }
            Box(Modifier.weight(1f).height(6.dp), contentAlignment = Alignment.CenterStart) {
                Box(Modifier.fillMaxWidth((kotlin.math.abs(y) / max).toFloat().coerceIn(0.02f, 1f)).height(6.dp).clip(CircleShape).background(cb.copy(alpha = if (bBetter) 1f else 0.4f)))
            }
        }
    }
}
