package com.deepshawn.hwcounter.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepshawn.hwcounter.cloud.Trade
import com.deepshawn.hwcounter.data.AppData
import com.deepshawn.hwcounter.data.AppViewModel
import com.deepshawn.hwcounter.data.SchoolClass
import com.deepshawn.hwcounter.ui.LocalKit
import com.deepshawn.hwcounter.ui.Route
import com.deepshawn.hwcounter.ui.components.Chip2
import com.deepshawn.hwcounter.ui.components.EmptyState
import com.deepshawn.hwcounter.ui.components.HCard
import com.deepshawn.hwcounter.ui.components.HTabs
import com.deepshawn.hwcounter.ui.components.Avatar
import com.deepshawn.hwcounter.ui.components.listCell
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import com.deepshawn.hwcounter.ui.components.MiuixPage
import com.deepshawn.hwcounter.ui.components.group
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs

private fun m2(x: Double) = "%,.2f".format(x)
private fun sgn(x: Double) = (if (x > 0.005) "+" else if (x < -0.005) "-" else "") + "%,.2f".format(abs(x))
private fun day(iso: String) = iso.take(10)

/** 炒股页面的数据（从云端读取） */
internal class TradeData(
    val me: Trade.Me? = null,
    val prices: List<Trade.Price> = emptyList(),
    val positions: List<Trade.Position> = emptyList(),
    val orders: List<Trade.Order> = emptyList(),
    val fills: List<Trade.Fill> = emptyList(),
    val cash: List<Trade.CashLog> = emptyList(),
    val ranks: List<Trade.Rank> = emptyList(),
)

/** 只看这个班的股票（股票代码里带学生 id 或班级 id） */
private fun inClass(c: SchoolClass, symbol: String): Boolean {
    Trade.studentOf(symbol)?.let { id -> return c.students.any { it.id == id } }
    Trade.groupOf(symbol)?.let { (cid, _) -> return cid == c.id }
    return false
}

/** 统计 → 股票 → 炒股 */
fun LazyListScope.tradeSections(vm: AppViewModel, data: AppData, c: SchoolClass) {
    item(key = "trade-panel") { TradePanel(vm, data, c) }
}

@Composable
private fun TradePanel(vm: AppViewModel, data: AppData, c: SchoolClass) {
    val kit = LocalKit.current
    val scope = rememberCoroutineScope()
    val colors = MiuixTheme.colorScheme
    val st by vm.sync.state.collectAsState()
    var td by remember { mutableStateOf<TradeData?>(null) }
    var err by remember { mutableStateOf("") }
    var tick by remember { mutableIntStateOf(0) }
    fun order(p: Trade.Price?, side: String) = kit.nav.push(Route.TradeOrder(p?.symbol, side))
    var tab by remember { mutableIntStateOf(0) }
    val loggedIn = st.email != null
    val member = st.member

    LaunchedEffect(st.email, tick, tradeTick.intValue) {
        if (!loggedIn) { td = null; return@LaunchedEffect }
        err = ""
        runCatching {
            val cl = vm.sync.client
            val me = Trade.me(cl)
            if (me.isMember) TradeData(me, Trade.prices(cl), Trade.positions(cl, me.userId), Trade.orders(cl, me.userId), Trade.fills(cl, me.userId), Trade.cashLog(cl, me.userId), Trade.leaderboard(cl))
            else TradeData(me, Trade.prices(cl), ranks = Trade.leaderboard(cl), fills = Trade.fills(cl, limit = 30))
        }.onSuccess { td = it }.onFailure { err = it.message ?: "读取失败" }
    }

    Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (!loggedIn) {
            HCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("模拟炒股", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("用老师发放的模拟币（与真实货币无关）买卖信誉分、关注度“股票”：个人和小组都有，股价随班级数据变化。", fontSize = 13.sp, color = colors.onSurfaceVariantSummary)
                    Text("· 老师：登录后在 设置 → 用户与邀请码 生成邀请码、发放模拟币\n· 学生 / 家长：点下面的按钮 → 注册 → 学生 / 家长，填写邀请码", fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
                    TextButton("去登录 / 注册", onClick = { kit.nav.push(Route.Auth()) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.textButtonColorsPrimary())
                }
            }
            return@Column
        }
        if (err.isNotBlank()) Text(err, color = colors.error, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 4.dp))
        val d = td ?: run { Text("正在读取…", Modifier.fillMaxWidth().padding(24.dp), textAlign = TextAlign.Center, color = colors.onSurfaceVariantSummary); return@Column }
        val prices = d.prices.filter { it.active && inClass(c, it.symbol) }
        val priceOf = d.prices.associateBy { it.symbol }
        val updated = d.prices.maxOfOrNull { it.updatedAt }?.let { day(it) }

        if (d.me?.isMember != true) {
            // ---------- 管理员（老师） ----------
            HCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("你是管理员", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("股价 = 各同学 / 小组的信誉分、关注度（成交价最低 1）。你的数据每次同步后自动发布最新股价，到价的限价单自动成交。" + (updated?.let { "\n最近发布：$it（共 ${d.prices.count { it.active }} 只）" } ?: "\n还没有发布过股价"), fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton("立即发布股价", onClick = {
                            scope.launch { runCatching { vm.sync.publishNow(data) }.onSuccess { kit.toast("已发布，撮合成交 $it 笔挂单"); tick++ }.onFailure { kit.toast(it.message ?: "发布失败") } }
                        }, modifier = Modifier.weight(1f), colors = ButtonDefaults.textButtonColorsPrimary())
                        TextButton("用户与邀请码", onClick = { kit.nav.push(Route.Accounts) }, modifier = Modifier.weight(1f))
                    }
                }
            }
            Leaderboard(d.ranks, null)
            if (d.fills.isNotEmpty()) {
                val names = d.ranks.associate { it.userId to it.name }
                FillList("最近成交（全部用户）", d.fills, priceOf) { names[it] }
            }
            return@Column
        }

        // ---------- 普通用户 ----------
        val me = d.me!!
        val pos = d.positions.filter { it.qty != 0L }
        val stockValue = pos.sumOf { it.qty * (priceOf[it.symbol]?.px ?: it.avgPrice) }
        val equity = me.cash + stockValue
        val invested = d.cash.sumOf { it.amount }.takeIf { d.cash.isNotEmpty() } ?: d.ranks.firstOrNull { it.userId == me.userId }?.invested ?: 0.0
        val profit = equity - invested
        if (me.status != "active") Text("账号已被老师停用：不能交易", color = colors.error, fontWeight = FontWeight.Bold)
        HCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${me.name} 的账户", fontSize = 13.sp, color = colors.onSurfaceVariantSummary, modifier = Modifier.weight(1f))
                    Text("刷新", fontSize = 13.sp, color = colors.primary, modifier = Modifier.clickable { tick++ }.padding(4.dp))
                }
                Text("总资产 ${m2(equity)}", fontSize = 26.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Text("累计盈亏 ${sgn(profit)}" + (if (invested > 0) "（${sgn(profit / invested * 100)}%）" else ""), fontSize = 14.sp, color = if (profit >= 0) Color(0xFF16A34A) else Color(0xFFDC2626), fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row {
                    Column(Modifier.weight(1f)) { Text("现金", fontSize = 11.sp, color = colors.onSurfaceVariantSummary); Text(m2(me.cash), fontWeight = FontWeight.Medium) }
                    Column(Modifier.weight(1f)) { Text("持仓市值", fontSize = 11.sp, color = colors.onSurfaceVariantSummary); Text(m2(stockValue), fontWeight = FontWeight.Medium) }
                    Column(Modifier.weight(1f)) { Text("老师发放", fontSize = 11.sp, color = colors.onSurfaceVariantSummary); Text(m2(invested), fontWeight = FontWeight.Medium) }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton("买入", onClick = { order(null, "buy") }, enabled = me.status == "active", modifier = Modifier.weight(1f), colors = ButtonDefaults.textButtonColorsPrimary())
                    TextButton("卖出 / 做空", onClick = { order(null, "sell") }, enabled = me.status == "active", modifier = Modifier.weight(1f))
                }
                Text("股价最近更新：${updated ?: "老师还没有发布"}。做空需保留 2 倍空头市值的现金。", fontSize = 11.sp, color = colors.onSurfaceVariantSummary, modifier = Modifier.padding(top = 6.dp))
            }
        }
        HTabs(listOf("持仓 ${pos.size}", "委托", "成交", "行情", "排行"), tab, { tab = it }, height = 36.dp)
        when (tab) {
            0 -> Section("持仓") {
                if (pos.isEmpty()) Text("还没有持仓。点“买入”开始。", fontSize = 13.sp, color = colors.onSurfaceVariantSummary)
                pos.forEach { p ->
                    val px = priceOf[p.symbol]
                    val cur = px?.px ?: p.avgPrice
                    val pl = (cur - p.avgPrice) * p.qty
                    Row(Modifier.fillMaxWidth().clickable { order(px, if (p.qty > 0) "sell" else "buy") }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text((px?.name ?: p.symbol) + if (p.qty < 0) "（空）" else "", fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${px?.kindLabel ?: ""} · ${abs(p.qty)} 股 · 成本 ${m2(p.avgPrice)} · 现价 ${m2(cur)}", fontSize = 11.sp, color = colors.onSurfaceVariantSummary)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(sgn(pl), fontWeight = FontWeight.Bold, color = if (pl >= 0) Color(0xFF16A34A) else Color(0xFFDC2626))
                            if (p.realized != 0.0) Text("已实现 ${sgn(p.realized)}", fontSize = 10.sp, color = colors.onSurfaceVariantSummary)
                        }
                    }
                }
            }
            1 -> Section("委托") {
                if (d.orders.isEmpty()) Text("还没有委托", fontSize = 13.sp, color = colors.onSurfaceVariantSummary)
                d.orders.forEach { o ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${if (o.side == "buy") "买入" else "卖出"} ${priceOf[o.symbol]?.name ?: o.symbol} × ${o.qty}", fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text((o.limitPrice?.let { "限价 ${m2(it)}" } ?: "市价") + " · ${day(o.createdAt)}" + (if (o.reason.isNotBlank()) " · ${o.reason}" else ""), fontSize = 11.sp, color = colors.onSurfaceVariantSummary)
                        }
                        when (o.status) {
                            "open" -> Text("撤单", color = colors.primary, fontWeight = FontWeight.Bold, modifier = Modifier.clickable {
                                scope.launch { runCatching { Trade.cancel(vm.sync.client, o.id) }.onSuccess { kit.toast("已撤单"); tick++ }.onFailure { kit.toast(it.message ?: "撤单失败") } }
                            }.padding(6.dp))
                            "filled" -> Text("已成交 ${o.fillPrice?.let { m2(it) } ?: ""}", fontSize = 12.sp, color = Color(0xFF16A34A))
                            else -> Text("已撤销", fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
                        }
                    }
                }
            }
            2 -> {
                FillList("成交记录", d.fills, priceOf) { null }
                if (d.cash.isNotEmpty()) Section("资金记录") {
                    d.cash.forEach { l ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                            Text("${day(l.createdAt)} ${l.note}", fontSize = 12.sp, modifier = Modifier.weight(1f))
                            Text(sgn(l.amount), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            3 -> Section("可交易的股票（${prices.size}）") {
                if (prices.isEmpty()) Text("老师还没有发布这个班的股价", fontSize = 13.sp, color = colors.onSurfaceVariantSummary)
                listOf("C", "A", "GC", "GA").forEach { k ->
                    val l = prices.filter { it.kind == k }.sortedByDescending { it.price }
                    if (l.isNotEmpty()) {
                        Text(Trade.kindLabel(k), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
                        l.forEach { p ->
                            Row(Modifier.fillMaxWidth().clickable { order(p, "buy") }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(p.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(m2(p.px), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)
                                Text("交易", color = colors.primary, fontSize = 12.sp, modifier = Modifier.padding(start = 10.dp))
                            }
                        }
                    }
                }
            }
            else -> Leaderboard(d.ranks, me.userId)
        }
    }

}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    HCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.padding(bottom = 4.dp))
            content()
        }
    }
}

@Composable
private fun FillList(title: String, fills: List<Trade.Fill>, priceOf: Map<String, Trade.Price>, who: (String) -> String?) {
    val colors = MiuixTheme.colorScheme
    Section(title) {
        if (fills.isEmpty()) Text("还没有成交", fontSize = 13.sp, color = colors.onSurfaceVariantSummary)
        fills.forEach { f ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text((who(f.userId)?.let { "$it · " } ?: "") + "${if (f.side == "buy") "买入" else "卖出"} ${priceOf[f.symbol]?.name ?: f.symbol} × ${f.qty}", fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${day(f.createdAt)} · 价格 ${m2(f.price)} · 金额 ${m2(f.amount)}", fontSize = 11.sp, color = colors.onSurfaceVariantSummary)
                }
                if (f.realized != 0.0) Text(sgn(f.realized), fontWeight = FontWeight.Bold, color = if (f.realized >= 0) Color(0xFF16A34A) else Color(0xFFDC2626))
            }
        }
    }
}

@Composable
private fun Leaderboard(ranks: List<Trade.Rank>, me: String?) {
    val colors = MiuixTheme.colorScheme
    Section("收益排行（${ranks.size} 人）") {
        if (ranks.isEmpty()) Text("还没有用户。老师在 设置 → 用户与邀请码 生成邀请码。", fontSize = 13.sp, color = colors.onSurfaceVariantSummary)
        ranks.forEachIndexed { i, r ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(if (r.userId == me) colors.primary.copy(alpha = 0.08f) else Color.Transparent).padding(vertical = 6.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${i + 1}", Modifier.width(28.dp), fontWeight = FontWeight.Bold, color = if (i < 3) Color(0xFFE8A400) else colors.onSurfaceVariantSummary)
                Column(Modifier.weight(1f)) {
                    Text(r.name + if (r.status != "active") "（已停用）" else "", fontWeight = FontWeight.Medium, maxLines = 1)
                    Text("总资产 ${m2(r.equity)} · 现金 ${m2(r.cash)}", fontSize = 11.sp, color = colors.onSurfaceVariantSummary)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(sgn(r.profit), fontWeight = FontWeight.Bold, color = if (r.profit >= 0) Color(0xFF16A34A) else Color(0xFFDC2626))
                    r.pct?.let { Text(sgn(it * 100) + "%", fontSize = 11.sp, color = colors.onSurfaceVariantSummary) }
                }
            }
        }
    }
}

/** 截图测试用：不连网，直接用这份行情 */
internal var tradeFake: TradeData? = null

/** 下单后让炒股页重新读取 */
internal val tradeTick = androidx.compose.runtime.mutableIntStateOf(0)

/** 选股与下单页：买入 / 卖出 / 做空 / 平空 × 市价 / 限价 */
@Composable
fun TradeOrderScreen(vm: AppViewModel, data: AppData, symbol: String?, initialSide: String) {
    val kit = LocalKit.current
    val scope = rememberCoroutineScope()
    val colors = MiuixTheme.colorScheme
    val us = com.deepshawn.hwcounter.ui.theme.LocalUiPrefs.current.usColors
    val buyColor = if (us) Color(0xFF16A34A) else Color(0xFFE5384D)
    val sellColor = if (us) Color(0xFFE5384D) else Color(0xFF16A34A)
    val c = vm.currentClass(data)
    var td by remember { mutableStateOf<TradeData?>(null) }
    var err by remember { mutableStateOf("") }
    var sel by remember { mutableStateOf(symbol) }
    var side by remember { mutableStateOf(initialSide) }
    var limitMode by remember { mutableIntStateOf(0) }
    var qtyText by remember { mutableStateOf("10") }
    var limitText by remember { mutableStateOf("") }
    var filter by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        tradeFake?.let { td = it; return@LaunchedEffect }
        runCatching {
            val cl = vm.sync.client
            val me = Trade.me(cl)
            TradeData(me, Trade.prices(cl), Trade.positions(cl, me.userId))
        }.onSuccess { td = it }.onFailure { err = it.message ?: "读取失败" }
    }
    val d = td
    val prices = d?.prices.orEmpty().filter { it.active && (c == null || inClass(c, it.symbol)) }
    val held = d?.positions.orEmpty().associate { it.symbol to it.qty }
    val cost = d?.positions.orEmpty().associate { it.symbol to it.avgPrice }
    val p = sel?.let { s -> prices.firstOrNull { it.symbol == s } }
    LaunchedEffect(p?.symbol) { if (p != null && limitText.isEmpty()) limitText = "%.2f".format(p.px) }

    MiuixPage(
        title = p?.name ?: "选择股票", largeTitle = p?.name ?: "选择股票",
        onBack = { if (p != null && symbol == null) { sel = null; limitText = "" } else kit.nav.pop() },
    ) {
        if (d == null) {
            item(key = "loading") { Text(err.ifBlank { "正在读取行情…" }, Modifier.fillMaxWidth().padding(32.dp), textAlign = TextAlign.Center, color = if (err.isBlank()) colors.onSurfaceVariantSummary else colors.error) }
            return@MiuixPage
        }
        val cash = d.me?.cash ?: 0.0
        if (p == null) {
            // ---------- 选股 ----------
            val kinds = listOf("C", "A", "GC", "GA")
            item(key = "search") {
                FormField(query, { query = it }, "", Icons.Rounded.Search, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), placeholder = "搜索同学或小组", height = 46.dp)
            }
            item(key = "kinds") {
                HTabs(listOf("个人信誉", "个人关注", "小组信誉", "小组关注"), filter, { filter = it }, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), height = 40.dp)
            }
            val l = prices.filter { it.kind == kinds[filter] && (query.isBlank() || query.trim() in it.name) }.sortedByDescending { it.price }
            item(key = "count") {
                Text(
                    if (prices.isEmpty()) "老师还没有发布这个班的股价" else "${Trade.kindLabel(kinds[filter])} · ${l.size} 只 · 现金 ${m2(cash)}",
                    fontSize = 12.sp, color = colors.onSurfaceVariantSummary, modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp),
                )
            }
            if (l.isEmpty() && prices.isNotEmpty()) item(key = "none") { Text("没有找到", Modifier.fillMaxWidth().padding(24.dp), textAlign = TextAlign.Center, color = colors.onSurfaceVariantSummary) }
            l.forEachIndexed { i, x ->
                item(key = "s-${x.symbol}") {
                    val h = held[x.symbol] ?: 0L
                    Row(
                        Modifier.listCell(i, l.size).clickable { sel = x.symbol; limitText = "%.2f".format(x.px) }.padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${i + 1}", Modifier.width(26.dp), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (i < 3) colors.primary else colors.onSurfaceVariantSummary)
                        Avatar(if (x.kind.startsWith("G")) x.name.take(1) else x.name, size = 38.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(x.name, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(x.kindLabel + if (h != 0L) " · ${if (h > 0) "持有" else "做空"} ${abs(h)} 股" else "", fontSize = 12.sp, color = if (h != 0L) colors.primary else colors.onSurfaceVariantSummary, maxLines = 1)
                        }
                        Text(m2(x.px), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
                        Text("›", fontSize = 20.sp, color = colors.onSurfaceVariantSummary, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
            item(key = "pad") { Spacer(Modifier.height(24.dp)) }
            return@MiuixPage
        }

        // ---------- 下单 ----------
        val h = held[p.symbol] ?: 0L
        val px = p.px
        val qty = qtyText.toLongOrNull() ?: 0L
        val limit = limitText.toDoubleOrNull()
        val usePx = if (limitMode == 1) (limit ?: px) else px
        val est = qty * usePx
        val action = when {
            side == "buy" && h < 0 -> if (qty <= -h) "买入平空" else "平空并买入"
            side == "buy" -> "买入"
            h > 0 -> if (qty <= h) "卖出" else "卖出并做空"
            else -> "卖出做空"
        }
        val sideColor = if (side == "buy") buyColor else sellColor
        val cashAfter = if (side == "buy") cash - est else cash + est
        val warn = when {
            qty <= 0 -> "请输入数量"
            limitMode == 1 && (limit ?: 0.0) <= 0 -> "请输入限价"
            side == "buy" && est > cash + 1e-9 -> "现金不够：最多可买 ${(cash / usePx).toLong()} 股"
            else -> null
        }
        item(key = "quote") {
            HCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth(), radius = 22.dp) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(if (p.kind.startsWith("G")) p.name.take(1) else p.name, size = 44.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(p.kindLabel, fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
                        }
                        if (symbol == null) Text("换一只", fontSize = 13.sp, color = colors.primary, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { sel = null; limitText = "" }.padding(6.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("现价", fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
                    Text(m2(px), fontSize = 36.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.height(10.dp))
                    Row {
                        QuoteStat("持仓", if (h == 0L) "—" else (if (h < 0) "空 " else "") + "${abs(h)} 股", Modifier.weight(1f))
                        QuoteStat("成本", cost[p.symbol]?.takeIf { h != 0L }?.let { m2(it) } ?: "—", Modifier.weight(1f))
                        QuoteStat("可用现金", m2(cash), Modifier.weight(1.3f))
                    }
                }
            }
        }
        item(key = "side") {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().height(48.dp).clip(RoundedCornerShape(16.dp)).background(colors.secondaryContainer).padding(4.dp)) {
                listOf("buy" to (if (h < 0) "买入平空" else "买入"), "sell" to (if (h > 0) "卖出" else "卖出做空")).forEach { (k, t) ->
                    val on = side == k
                    val col = if (k == "buy") buyColor else sellColor
                    Box(
                        Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(12.dp)).background(if (on) col else Color.Transparent).clickable { side = k },
                        contentAlignment = Alignment.Center,
                    ) { Text(t, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (on) Color.White else colors.onSurfaceVariantSummary) }
                }
            }
        }
        item(key = "type") { HTabs(listOf("市价单", "限价单"), limitMode, { limitMode = it }, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), height = 40.dp) }
        item(key = "form") {
            HCard(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(), radius = 22.dp) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (limitMode == 1) FormField(
                        limitText, { limitText = it.filter { ch -> ch.isDigit() || ch == '.' }.take(9) }, "限价", null,
                        keyboard = KeyboardType.Decimal, mono = true, fontSize = 18.sp,
                        hint = if (side == "buy") "股价 ≤ 限价时自动成交；可在 委托 里撤单" else "股价 ≥ 限价时自动成交；可在 委托 里撤单",
                    )
                    Column {
                        Text("数量（股）", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.onSurfaceVariantSummary, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StepButton("−") { qtyText = maxOf(1L, qty - 1).toString() }
                            FormField(qtyText, { qtyText = it.filter { ch -> ch.isDigit() }.take(7) }, "", null, Modifier.weight(1f).padding(horizontal = 10.dp), keyboard = KeyboardType.Number, mono = true, fontSize = 22.sp, textAlign = TextAlign.Center)
                            StepButton("+") { qtyText = (qty + 1).toString() }
                        }
                    }
                    // 快捷数量：买入按现金，卖出按持仓，做空按保证金（2 倍空头市值）
                    val quick: List<Pair<String, Long>> = when {
                        side == "buy" && h < 0 -> listOf("平一半" to (-h + 1) / 2, "全部平空" to -h)
                        side == "buy" -> listOf(4, 3, 2, 1).map { k -> (if (k == 1) "全仓" else "1/$k") to (cash / k / usePx).toLong() }
                        h > 0 -> listOf(4, 2, 1).map { k -> (if (k == 1) "全部" else "1/$k") to (h / k) }
                        else -> listOf(4, 2, 1).map { k -> (if (k == 1) "最多" else "1/$k") to (cash / 2 / k / usePx).toLong() }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        quick.forEach { (t, n) ->
                            Box(
                                Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(12.dp)).background(colors.secondaryContainer).clickable(enabled = n > 0) { qtyText = n.toString() },
                                contentAlignment = Alignment.Center,
                            ) { Text(t, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (n > 0) colors.onSurface else colors.onSurfaceVariantSummary.copy(alpha = 0.5f), maxLines = 1) }
                        }
                    }
                }
            }
        }
        item(key = "summary") {
            HCard(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(), radius = 22.dp) {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SummaryRow("操作", action, sideColor)
                    SummaryRow("价格", if (limitMode == 1) "限价 ${limit?.let { m2(it) } ?: "—"}" else "市价 ≈ ${m2(px)}")
                    SummaryRow("预估金额", m2(est), bold = true)
                    SummaryRow("成交后现金", m2(cashAfter), if (cashAfter < 0) colors.error else null)
                }
            }
        }
        item(key = "go") {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                warn?.let { Text(it, fontSize = 13.sp, color = colors.error, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)) }
                if (d.me?.status != "active") Text("账号已被老师停用：不能交易", fontSize = 13.sp, color = colors.error, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
                BigButton("$action ${if (qty > 0) "$qty 股" else ""}".trim(), {
                    busy = true
                    scope.launch {
                        runCatching { Trade.place(vm.sync.client, p.symbol, side, qty, if (limitMode == 1) limit else null) }
                            .onSuccess { r ->
                                kit.toast(when (r.status) { "filled" -> "已成交，价格 ${r.price?.let { m2(it) } ?: ""}"; "open" -> "已挂单，到价自动成交"; else -> "未成交：${r.reason}" })
                                tradeTick.intValue++
                                if (r.status == "filled" || r.status == "open") kit.nav.pop()
                            }
                            .onFailure { kit.toast(it.message ?: "下单失败") }
                        busy = false
                    }
                }, enabled = warn == null && d.me?.status == "active", busy = busy, color = sideColor)
                Text(
                    "市价单按老师最近发布的股价立即成交；限价单挂单等待。做空需保留 2 倍空头市值的现金。模拟币与真实货币无关。",
                    fontSize = 12.sp, color = colors.onSurfaceVariantSummary, modifier = Modifier.padding(start = 4.dp, top = 10.dp, bottom = 24.dp),
                )
            }
        }
    }
}

@Composable
private fun QuoteStat(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun SummaryRow(label: String, value: String, color: Color? = null, bold: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = Modifier.weight(1f))
        Text(value, fontSize = if (bold) 17.sp else 14.sp, fontWeight = if (bold || color != null) FontWeight.Bold else FontWeight.Medium, color = color ?: MiuixTheme.colorScheme.onSurface, fontFamily = if (value.firstOrNull()?.isDigit() == true) FontFamily.Monospace else null)
    }
}

@Composable
private fun StepButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(MiuixTheme.colorScheme.secondaryContainer).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, fontSize = 24.sp, fontWeight = FontWeight.Medium, color = MiuixTheme.colorScheme.primary) }
}

// ---------------- 管理员：用户与邀请码 ----------------

@Composable
fun AccountsScreen(vm: AppViewModel, data: AppData) {
    val kit = LocalKit.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = MiuixTheme.colorScheme
    val st by vm.sync.state.collectAsState()
    var invites by remember { mutableStateOf<List<Trade.Invite>>(emptyList()) }
    var members by remember { mutableStateOf<List<Trade.Member>>(emptyList()) }
    var ranks by remember { mutableStateOf<Map<String, Trade.Rank>>(emptyMap()) }
    var err by remember { mutableStateOf("") }
    var tick by remember { mutableIntStateOf(0) }
    var creating by remember { mutableStateOf(false) }
    var granting by remember { mutableStateOf<Trade.Member?>(null) }
    var renaming by remember { mutableStateOf<Trade.Member?>(null) }
    var removing by remember { mutableStateOf<Trade.Member?>(null) }
    var showUsed by remember { mutableStateOf(false) }

    fun act(ok: String, f: suspend () -> Unit) = scope.launch { runCatching { f() }.onSuccess { kit.toast(ok); tick++ }.onFailure { kit.toast(it.message ?: "操作失败") } }
    fun copy(text: String) {
        (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("邀请码", text))
        kit.toast("已复制")
    }

    LaunchedEffect(st.email, tick) {
        if (st.email == null || st.member != null) return@LaunchedEffect
        runCatching {
            val c = vm.sync.client
            Triple(Trade.invites(c), Trade.members(c), Trade.leaderboard(c).associateBy { it.userId })
        }.onSuccess { (i, m, r) -> invites = i; members = m; ranks = r; err = "" }.onFailure { err = it.message ?: "读取失败" }
    }

    MiuixPage(title = "用户与邀请码", largeTitle = "用户与邀请码", onBack = { kit.nav.pop() }) {
        if (st.email == null || st.member != null) {
            item(key = "need") { EmptyState("👤", "需要老师账号", "用老师的邮箱登录后，才能生成邀请码、管理用户") }
            return@MiuixPage
        }
        item(key = "intro") {
            Text(
                "邀请码一次性有效：学生或家长在 设置 → 账号与云同步 → 注册 里选“学生 / 家长”填写邀请码，注册后只读查看你的班级数据，用你发的模拟币（与真实货币无关）炒信誉分、关注度股票。",
                fontSize = 12.sp, color = colors.onSurfaceVariantSummary, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
            if (err.isNotBlank()) Text(err, color = colors.error, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 24.dp))
        }
        group("邀请码", key = "invites") {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton("生成邀请码", onClick = { creating = true }, modifier = Modifier.weight(1f), colors = ButtonDefaults.textButtonColorsPrimary())
                    TextButton(if (showUsed) "只看未使用" else "显示全部", onClick = { showUsed = !showUsed }, modifier = Modifier.weight(1f))
                }
                val list = invites.filter { showUsed || (it.usedAt == null && !it.revoked) }
                if (list.isEmpty()) Text(if (invites.isEmpty()) "还没有邀请码" else "没有未使用的邀请码", fontSize = 13.sp, color = colors.onSurfaceVariantSummary, modifier = Modifier.padding(vertical = 8.dp))
                val unused = list.filter { it.usedAt == null && !it.revoked }
                if (unused.size > 1) Text("复制全部未使用的 ${unused.size} 个", color = colors.primary, fontSize = 13.sp, modifier = Modifier.clickable { copy(unused.joinToString("\n") { it.code + if (it.note.isNotBlank()) "  ${it.note}" else "" }) }.padding(vertical = 6.dp))
                list.forEach { inv ->
                    val usedBy = members.firstOrNull { it.userId == inv.usedBy }
                    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(inv.code, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(listOfNotNull(inv.state, usedBy?.let { "→ ${it.name}" }, "注册送 ${m2(inv.initialCash)}", inv.note.takeIf { it.isNotBlank() }).joinToString(" · "), fontSize = 11.sp, color = colors.onSurfaceVariantSummary)
                        }
                        if (inv.usedAt == null && !inv.revoked) {
                            Text("复制", color = colors.primary, fontSize = 13.sp, modifier = Modifier.clickable { copy(inv.code) }.padding(6.dp))
                            Text("作废", color = colors.error, fontSize = 13.sp, modifier = Modifier.clickable { act("已作废") { Trade.revokeInvite(vm.sync.client, inv.code) } }.padding(6.dp))
                        }
                    }
                }
            }
        }
        group("用户（${members.size}）", key = "members") {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                if (members.isEmpty()) Text("还没有用户注册", fontSize = 13.sp, color = colors.onSurfaceVariantSummary, modifier = Modifier.padding(vertical = 8.dp))
                members.forEach { m ->
                    val r = ranks[m.userId]
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${m.name}（${m.username}）" + if (m.status != "active") " · 已停用" else "", fontWeight = FontWeight.Medium, color = if (m.status != "active") colors.onSurfaceVariantSummary else colors.onSurface)
                                Text("现金 ${m2(m.cash)}" + (r?.let { " · 总资产 ${m2(it.equity)} · 盈亏 ${sgn(it.profit)}" } ?: ""), fontSize = 11.sp, color = colors.onSurfaceVariantSummary)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(top = 4.dp)) {
                            Text("发币 / 收回", color = colors.primary, fontSize = 13.sp, modifier = Modifier.clickable { granting = m })
                            Text(if (m.status == "active") "停用" else "启用", color = colors.primary, fontSize = 13.sp, modifier = Modifier.clickable { act(if (m.status == "active") "已停用" else "已启用") { Trade.setStatus(vm.sync.client, m.userId, m.status != "active") } })
                            Text("改名", color = colors.primary, fontSize = 13.sp, modifier = Modifier.clickable { renaming = m })
                            Text("移除", color = colors.error, fontSize = 13.sp, modifier = Modifier.clickable { removing = m })
                        }
                    }
                }
                if (members.size > 1) TextButton("给所有用户发币", onClick = { granting = Trade.Member("*", "全部用户") }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
            }
        }
        group("股价", key = "publish") {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("每次同步后自动按最新数据发布股价（个人关注度、个人信誉分、小组关注度、小组信誉分）；也可以现在手动发布。", fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
                TextButton("立即发布股价", onClick = { act("已发布股价") { vm.sync.publishNow(data) } }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp), colors = ButtonDefaults.textButtonColorsPrimary())
            }
        }
    }

    if (creating) {
        var note by remember { mutableStateOf("") }
        var cash by remember { mutableStateOf("10000") }
        var count by remember { mutableStateOf("1") }
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text("生成邀请码", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(count, { count = it.filter { ch -> ch.isDigit() }.take(2) }, label = { Text("数量（1–50）") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(cash, { cash = it.filter { ch -> ch.isDigit() || ch == '.' }.take(10) }, label = { Text("注册赠送模拟币") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(note, { note = it.take(40) }, label = { Text("备注（可选，如：张三家长）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    val n = (count.toIntOrNull() ?: 1).coerceIn(1, 50); val m = cash.toDoubleOrNull() ?: 0.0
                    scope.launch {
                        runCatching { Trade.createInvites(vm.sync.client, note, m, n) }
                            .onSuccess { l -> copy(l.joinToString("\n") { it.code }); kit.toast("已生成 ${l.size} 个邀请码并复制"); creating = false; tick++ }
                            .onFailure { kit.toast(it.message ?: "生成失败") }
                    }
                }) { Text("生成") }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { creating = false }) { Text("取消") } },
        )
    }
    granting?.let { m ->
        var amount by remember(m) { mutableStateOf("1000") }
        var note by remember(m) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { granting = null },
            title = { Text(if (m.userId == "*") "给所有用户发币" else "${m.name}：发币 / 收回", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (m.userId != "*") Text("当前现金 ${m2(m.cash)}", fontSize = 13.sp)
                    OutlinedTextField(amount, { amount = it.filter { ch -> ch.isDigit() || ch == '.' || ch == '-' }.take(11) }, label = { Text("金额（负数为收回）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(note, { note = it.take(40) }, label = { Text("备注（可选）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text("模拟币只用于炒股模拟，与真实货币无关。", fontSize = 11.sp, color = colors.onSurfaceVariantSummary)
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    val a = amount.toDoubleOrNull() ?: 0.0
                    if (a == 0.0) return@TextButton
                    val targets = if (m.userId == "*") members.filter { it.status == "active" } else listOf(m)
                    act(if (a > 0) "已发放" else "已收回") { targets.forEach { Trade.grant(vm.sync.client, it.userId, a, note) } }
                    granting = null
                }) { Text("确定") }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { granting = null }) { Text("取消") } },
        )
    }
    renaming?.let { m ->
        var name by remember(m) { mutableStateOf(m.displayName) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("改显示名", fontWeight = FontWeight.Bold) },
            text = { OutlinedTextField(name, { name = it.take(40) }, label = { Text("显示名") }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { act("已改名") { Trade.rename(vm.sync.client, m.userId, name) }; renaming = null }) { Text("确定") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { renaming = null }) { Text("取消") } },
        )
    }
    removing?.let { m ->
        ConfirmDialog("移除 ${m.name}？", "删除这个用户的资金、持仓、委托和成交记录，账号不能再查看你的数据。不能撤销。", "移除", { removing = null }) {
            act("已移除") { Trade.remove(vm.sync.client, m.userId) }
            removing = null
        }
    }
}
