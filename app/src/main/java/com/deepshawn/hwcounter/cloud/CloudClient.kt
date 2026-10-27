package com.deepshawn.hwcounter.cloud

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Supabase 项目（与网页版、iPad 版共用）。publishable key 可以公开，数据由行级安全保护 */
object CloudConfig {
    const val URL = "https://beasxxexggnjcocdbtdl.supabase.co"
    const val KEY = "sb_publishable_85hufMmFQvmLQt2Rz1Hcrw_S4t7BjAO"
}

@Serializable
data class Session(
    val accessToken: String,
    val refreshToken: String,
    /** 毫秒时间戳 */
    val expiresAt: Long,
    val userId: String,
    val email: String,
)

data class Remote(val data: JsonElement, val version: Long, val updatedAt: String, val device: String?)

data class HistoryItem(val id: Long, val version: Long, val device: String?, val savedAt: String)

class CloudException(message: String, val status: Int = 0) : Exception(message)

/** 云端版本已被别的设备更新 */
class ConflictException(val remoteVersion: Long) : Exception("云端已有更新的版本 v$remoteVersion")

/** 直接调用 Supabase Auth（GoTrue）与 PostgREST 的 REST 接口 */
class CloudClient(
    private val loadSession: () -> Session?,
    private val saveSession: (Session?) -> Unit,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private data class Resp(val code: Int, val body: String)

    private fun http(method: String, path: String, body: String? = null, bearer: String? = null, extra: Map<String, String> = emptyMap()): Resp {
        val conn = URL(CloudConfig.URL + path).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("apikey", CloudConfig.KEY)
            conn.setRequestProperty("Accept", "application/json")
            if (bearer != null) conn.setRequestProperty("Authorization", "Bearer $bearer")
            extra.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            return Resp(code, text)
        } catch (e: java.io.IOException) {
            throw CloudException("网络连接失败：${e.message ?: "请检查网络"}")
        } finally {
            conn.disconnect()
        }
    }

    /** 把 Supabase 的英文错误翻成老师看得懂的话 */
    private fun friendly(r: Resp): String {
        val o = runCatching { json.parseToJsonElement(r.body).jsonObject }.getOrNull()
        val raw = listOf("error_description", "msg", "message", "error").firstNotNullOfOrNull { k -> (o?.get(k) as? JsonPrimitive)?.contentOrNull } ?: r.body.take(200)
        val l = raw.lowercase()
        return when {
            "invalid login credentials" in l -> "邮箱或密码不对"
            "email not confirmed" in l -> "邮箱还没验证：请到邮箱里点确认链接后再登录"
            "already registered" in l || "already been registered" in l -> "这个邮箱已经注册过了，直接登录即可"
            "password should be at least" in l || "weak_password" in l -> "密码太短或太简单（至少 6 位）"
            "rate limit" in l || r.code == 429 -> "操作太频繁，请稍后再试"
            "invalid email" in l || "unable to validate email" in l -> "邮箱格式不正确"
            "jwt expired" in l -> "登录已过期，请重新登录"
            else -> "出错了（${r.code}）：$raw"
        }
    }

    private fun parseSession(body: String, fallbackEmail: String): Session? {
        val o = json.parseToJsonElement(body).jsonObject
        val at = (o["access_token"] as? JsonPrimitive)?.contentOrNull ?: return null
        val rt = (o["refresh_token"] as? JsonPrimitive)?.contentOrNull ?: return null
        val exp = (o["expires_in"] as? JsonPrimitive)?.longOrNull ?: 3600
        val user = o["user"] as? JsonObject
        val uid = (user?.get("id") as? JsonPrimitive)?.contentOrNull ?: ""
        val email = (user?.get("email") as? JsonPrimitive)?.contentOrNull ?: fallbackEmail
        return Session(at, rt, System.currentTimeMillis() + exp * 1000, uid, email)
    }

    suspend fun signIn(email: String, password: String): Session = withContext(Dispatchers.IO) {
        val body = buildJsonObject { put("email", email.trim()); put("password", password) }.toString()
        val r = http("POST", "/auth/v1/token?grant_type=password", body)
        if (r.code !in 200..299) throw CloudException(friendly(r), r.code)
        val s = parseSession(r.body, email) ?: throw CloudException("登录响应异常")
        saveSession(s)
        s
    }

    /** 注册：项目开启邮箱验证时返回 null（需要先去邮箱确认） */
    suspend fun signUp(email: String, password: String): Session? = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("email", email.trim()); put("password", password)
            put("data", buildJsonObject { put("username", "teacher"); put("app", "hwcounter") })
        }.toString()
        val r = http("POST", "/auth/v1/signup", body)
        if (r.code !in 200..299) throw CloudException(friendly(r), r.code)
        val s = parseSession(r.body, email)
        if (s != null) saveSession(s)
        s
    }

    suspend fun recover(email: String) = withContext(Dispatchers.IO) {
        val r = http("POST", "/auth/v1/recover", buildJsonObject { put("email", email.trim()) }.toString())
        if (r.code !in 200..299) throw CloudException(friendly(r), r.code)
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        val s = loadSession()
        if (s != null) runCatching { http("POST", "/auth/v1/logout", "{}", s.accessToken) }
        saveSession(null)
    }

    /** 有效的 access token，快过期时自动刷新 */
    private fun token(): String {
        val s = loadSession() ?: throw CloudException("请先登录云端账号", 401)
        if (s.expiresAt - System.currentTimeMillis() > 60_000) return s.accessToken
        val r = http("POST", "/auth/v1/token?grant_type=refresh_token", buildJsonObject { put("refresh_token", s.refreshToken) }.toString())
        if (r.code !in 200..299) {
            if (r.code in 400..499) saveSession(null)
            throw CloudException("登录已过期，请重新登录", 401)
        }
        val ns = parseSession(r.body, s.email)?.let { if (it.userId.isEmpty()) it.copy(userId = s.userId) else it }
            ?: throw CloudException("刷新登录失败")
        saveSession(ns)
        return ns.accessToken
    }

    suspend fun pull(): Remote? = withContext(Dispatchers.IO) {
        val r = http("GET", "/rest/v1/hw_snapshots?select=data,version,updated_at,device&limit=1", bearer = token())
        if (r.code !in 200..299) throw CloudException(friendly(r), r.code)
        val arr = json.parseToJsonElement(r.body) as? JsonArray ?: return@withContext null
        val o = arr.firstOrNull()?.jsonObject ?: return@withContext null
        Remote(
            data = o["data"] ?: throw CloudException("云端数据为空"),
            version = o["version"]?.jsonPrimitive?.longOrNull ?: 0,
            updatedAt = (o["updated_at"] as? JsonPrimitive)?.contentOrNull ?: "",
            device = (o["device"] as? JsonPrimitive)?.contentOrNull,
        )
    }

    /** 上传；baseVersion 与云端不一致时抛 ConflictException。-1 表示强制覆盖 */
    suspend fun push(data: JsonElement, baseVersion: Long, device: String): Long = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("p_data", data); put("p_base_version", baseVersion); put("p_device", device)
        }.toString()
        val r = http("POST", "/rest/v1/rpc/hw_push", body, token())
        if (r.code !in 200..299) {
            val msg = runCatching { (json.parseToJsonElement(r.body).jsonObject["message"] as? JsonPrimitive)?.contentOrNull }.getOrNull() ?: ""
            if (msg.startsWith("CONFLICT:")) throw ConflictException(msg.removePrefix("CONFLICT:").trim().toLongOrNull() ?: -1)
            throw CloudException(friendly(r), r.code)
        }
        val arr = json.parseToJsonElement(r.body)
        val row = (arr as? JsonArray)?.firstOrNull()?.jsonObject ?: (arr as? JsonObject)
        row?.get("version")?.jsonPrimitive?.longOrNull ?: throw CloudException("上传响应异常")
    }

    suspend fun history(): List<HistoryItem> = withContext(Dispatchers.IO) {
        val r = http("GET", "/rest/v1/hw_snapshot_history?select=id,version,device,saved_at&order=saved_at.desc&limit=30", bearer = token())
        if (r.code !in 200..299) throw CloudException(friendly(r), r.code)
        (json.parseToJsonElement(r.body) as? JsonArray ?: JsonArray(emptyList())).mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            HistoryItem(
                id = o["id"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null,
                version = o["version"]?.jsonPrimitive?.longOrNull ?: 0,
                device = (o["device"] as? JsonPrimitive)?.contentOrNull,
                savedAt = (o["saved_at"] as? JsonPrimitive)?.contentOrNull ?: "",
            )
        }
    }

    suspend fun historyData(id: Long): JsonElement = withContext(Dispatchers.IO) {
        val r = http("GET", "/rest/v1/hw_snapshot_history?select=data&id=eq.${URLEncoder.encode(id.toString(), "UTF-8")}", bearer = token())
        if (r.code !in 200..299) throw CloudException(friendly(r), r.code)
        (json.parseToJsonElement(r.body) as? JsonArray)?.firstOrNull()?.jsonObject?.get("data") ?: throw CloudException("找不到该历史版本")
    }
}
