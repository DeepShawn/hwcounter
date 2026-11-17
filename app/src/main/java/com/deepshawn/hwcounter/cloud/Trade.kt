package com.deepshawn.hwcounter.cloud

import com.deepshawn.hwcounter.data.AppData
import com.deepshawn.hwcounter.data.Attention
import com.deepshawn.hwcounter.data.Credit
import com.deepshawn.hwcounter.data.MetricStock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.URLEncoder

/**
 * 邀请码账号与模拟炒股（数据库见 supabase/003_trading.sql，注册见 Edge Function hw-register）。
 * 管理员 = 老师账号；普通用户凭一次性邀请码注册，只读查看老师的班级数据，用老师发的模拟币（与真实货币无关）买卖“股票”：
 * A:学生id 个人关注度、C:学生id 个人信誉分、GA:班级id/组名 小组关注度、GC:班级id/组名 小组信誉分。
 * 股价由老师的 App 按班级数据算好后发布，成交全部在数据库里按已发布的价格完成。
 */
object Trade {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    @Serializable
    data class Me(
        val role: String = "admin",
        @SerialName("user_id") val userId: String = "",
        @SerialName("admin_id") val adminId: String? = null,
        val username: String = "",
        @SerialName("display_name") val displayName: String = "",
        val status: String = "active",
        val cash: Double = 0.0,
    ) {
        val isMember: Boolean get() = role == "member"
        val name: String get() = displayName.ifBlank { username }
    }

    @Serializable
    data class Price(val symbol: String, val name: String = "", val kind: String = "", val price: Double = 0.0, val active: Boolean = true, @SerialName("updated_at") val updatedAt: String = "") {
        /** 成交价：不低于 1 */
        val px: Double get() = maxOf(price, 1.0)
        val kindLabel: String get() = kindLabel(kind)
    }

    @Serializable
    data class Position(val symbol: String, val qty: Long = 0, @SerialName("avg_price") val avgPrice: Double = 0.0, val realized: Double = 0.0, @SerialName("user_id") val userId: String = "")

    @Serializable
    data class Order(
        val id: Long, val symbol: String, val side: String, val qty: Long,
        @SerialName("limit_price") val limitPrice: Double? = null, val status: String = "open", val reason: String = "",
        @SerialName("fill_price") val fillPrice: Double? = null, @SerialName("created_at") val createdAt: String = "", @SerialName("user_id") val userId: String = "",
    )

    @Serializable
    data class Fill(
        val id: Long, val symbol: String, val side: String, val qty: Long, val price: Double, val amount: Double,
        val realized: Double = 0.0, @SerialName("cash_after") val cashAfter: Double = 0.0, @SerialName("created_at") val createdAt: String = "",
        @SerialName("user_id") val userId: String = "",
    )

    @Serializable
    data class CashLog(val id: Long, val amount: Double, val note: String = "", @SerialName("cash_after") val cashAfter: Double = 0.0, @SerialName("created_at") val createdAt: String = "", @SerialName("user_id") val userId: String = "")

    @Serializable
    data class Rank(
        @SerialName("user_id") val userId: String, val username: String = "", @SerialName("display_name") val displayName: String = "",
        val status: String = "active", val cash: Double = 0.0, @SerialName("stock_value") val stockValue: Double = 0.0,
        val equity: Double = 0.0, val invested: Double = 0.0, val profit: Double = 0.0,
    ) {
        val name: String get() = displayName.ifBlank { username }
        val pct: Double? get() = if (invested > 0) profit / invested else null
    }

    @Serializable
    data class Invite(
        val code: String, val note: String = "", @SerialName("initial_cash") val initialCash: Double = 0.0,
        @SerialName("created_at") val createdAt: String = "", @SerialName("used_at") val usedAt: String? = null,
        @SerialName("used_by") val usedBy: String? = null, val revoked: Boolean = false,
    ) {
        val state: String get() = when { revoked -> "已作废"; usedBy != null -> "已使用"; usedAt != null -> "注册中"; else -> "未使用" }
    }

    @Serializable
    data class Member(
        @SerialName("user_id") val userId: String, val username: String, @SerialName("display_name") val displayName: String = "",
        val status: String = "active", val cash: Double = 0.0, @SerialName("created_at") val createdAt: String = "", @SerialName("invite_code") val inviteCode: String? = null,
    ) {
        val name: String get() = displayName.ifBlank { username }
    }

    fun kindLabel(kind: String) = when (kind) { "A" -> "个人关注度"; "C" -> "个人信誉分"; "GA" -> "小组关注度"; "GC" -> "小组信誉分"; else -> "" }
    fun sideLabel(side: String, qtyBefore: Long? = null) = if (side == "buy") (if (qtyBefore != null && qtyBefore < 0) "买入平空" else "买入") else (if (qtyBefore != null && qtyBefore <= 0) "卖出做空" else "卖出")

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private inline fun <reified T> list(body: String): List<T> = json.decodeFromString(body)

    // ---------------- 身份 ----------------
    suspend fun me(c: CloudClient): Me = json.decodeFromString(c.rpc("hw_me"))

    // ---------------- 行情与交易（普通用户） ----------------
    suspend fun prices(c: CloudClient): List<Price> = list(c.select("hw_prices?select=symbol,name,kind,price,active,updated_at&order=kind,name"))
    suspend fun positions(c: CloudClient, userId: String? = null): List<Position> =
        list(c.select("hw_positions?select=symbol,qty,avg_price,realized,user_id" + (userId?.let { "&user_id=eq.${enc(it)}" } ?: "") + "&order=updated_at.desc"))
    suspend fun orders(c: CloudClient, userId: String? = null, limit: Int = 60): List<Order> =
        list(c.select("hw_orders?select=id,symbol,side,qty,limit_price,status,reason,fill_price,created_at,user_id" + (userId?.let { "&user_id=eq.${enc(it)}" } ?: "") + "&order=id.desc&limit=$limit"))
    suspend fun fills(c: CloudClient, userId: String? = null, limit: Int = 60): List<Fill> =
        list(c.select("hw_trades?select=id,symbol,side,qty,price,amount,realized,cash_after,created_at,user_id" + (userId?.let { "&user_id=eq.${enc(it)}" } ?: "") + "&order=id.desc&limit=$limit"))
    suspend fun cashLog(c: CloudClient, userId: String? = null, limit: Int = 40): List<CashLog> =
        list(c.select("hw_cash_log?select=id,amount,note,cash_after,created_at,user_id" + (userId?.let { "&user_id=eq.${enc(it)}" } ?: "") + "&order=id.desc&limit=$limit"))
    suspend fun leaderboard(c: CloudClient): List<Rank> = list(c.rpc("hw_leaderboard"))

    data class PlaceResult(val status: String, val price: Double?, val reason: String)

    /** 下单：limit 为空是市价单 */
    suspend fun place(c: CloudClient, symbol: String, side: String, qty: Long, limit: Double?): PlaceResult {
        val o = json.parseToJsonElement(c.rpc("hw_place_order", buildJsonObject {
            put("p_symbol", symbol); put("p_side", side); put("p_qty", qty); if (limit != null) put("p_limit", limit)
        })) as kotlinx.serialization.json.JsonObject
        fun s(k: String) = (o[k] as? JsonPrimitive)?.content
        return PlaceResult(s("status") ?: "", s("price")?.toDoubleOrNull(), s("reason") ?: "")
    }
    suspend fun cancel(c: CloudClient, id: Long) { c.rpc("hw_cancel_order", buildJsonObject { put("p_id", id) }) }

    // ---------------- 管理员 ----------------
    suspend fun invites(c: CloudClient): List<Invite> = list(c.select("hw_invites?select=code,note,initial_cash,created_at,used_at,used_by,revoked&order=created_at.desc&limit=200"))
    suspend fun members(c: CloudClient): List<Member> = list(c.select("hw_members?select=user_id,username,display_name,status,cash,created_at,invite_code&order=created_at"))
    suspend fun createInvites(c: CloudClient, note: String, cash: Double, count: Int): List<Invite> =
        list(c.rpc("hw_create_invites", buildJsonObject { put("p_note", note); put("p_cash", cash); put("p_count", count) }))
    suspend fun revokeInvite(c: CloudClient, code: String) { c.rpc("hw_revoke_invite", buildJsonObject { put("p_code", code) }) }
    suspend fun grant(c: CloudClient, userId: String, amount: Double, note: String) { c.rpc("hw_grant", buildJsonObject { put("p_user", userId); put("p_amount", amount); put("p_note", note) }) }
    suspend fun setStatus(c: CloudClient, userId: String, active: Boolean) { c.rpc("hw_set_member_status", buildJsonObject { put("p_user", userId); put("p_status", if (active) "active" else "disabled") }) }
    suspend fun rename(c: CloudClient, userId: String, name: String) { c.rpc("hw_rename_member", buildJsonObject { put("p_user", userId); put("p_name", name) }) }
    suspend fun remove(c: CloudClient, userId: String) { c.rpc("hw_remove_member", buildJsonObject { put("p_user", userId) }) }

    data class Quote(val symbol: String, val name: String, val kind: String, val price: Double)

    /** 按老师的全部班级数据算出要发布的股价（每个班：个人关注度、个人信誉分、小组关注度、小组信誉分） */
    fun quotes(d: AppData, now: Long = System.currentTimeMillis()): List<Quote> {
        val rules = d.settings.rules
        val out = ArrayList<Quote>()
        for (c in d.classes) {
            if (c.students.isEmpty()) continue
            val multi = d.classes.size > 1
            fun nm(x: String) = if (multi) "${c.name}·$x" else x
            Attention.all(c, rules, now).forEach { out += Quote("A:${it.student.id}", nm(it.student.name), "A", it.index) }
            val credit = Credit.all(c, now)
            credit.filter { it.hasData }.forEach { out += Quote("C:${it.student.id}", nm(it.student.name), "C", it.index) }
            MetricStock.groupAttentionAt(c, rules, now).forEach { (g, v) -> out += Quote("GA:${c.id}/$g", nm(g), "GA", v) }
            Credit.groups(c, now, credit).forEach { (g, v) -> out += Quote("GC:${c.id}/$g", nm(g), "GC", v) }
        }
        return out.map { it.copy(price = Math.round(it.price * 100) / 100.0) }
    }

    /** 发布股价（只有管理员能调用），返回这次撮合成交的挂单数 */
    suspend fun publish(c: CloudClient, quotes: List<Quote>): Int {
        val arr = buildJsonArray {
            quotes.take(2000).forEach { q -> add(buildJsonObject { put("symbol", q.symbol); put("name", q.name.take(60)); put("kind", q.kind); put("price", q.price) }) }
        }
        return c.rpc("hw_publish_prices", buildJsonObject { put("p_prices", arr) }).trim().toIntOrNull() ?: 0
    }

    /** 股票代码对应的学生 id 或 (班级id, 组名) */
    fun studentOf(symbol: String): String? = if (symbol.startsWith("A:") || symbol.startsWith("C:")) symbol.substringAfter(":") else null
    fun groupOf(symbol: String): Pair<String, String>? = if (symbol.startsWith("GA:") || symbol.startsWith("GC:")) symbol.substringAfter(":").let { it.substringBefore("/") to it.substringAfter("/") } else null
}
