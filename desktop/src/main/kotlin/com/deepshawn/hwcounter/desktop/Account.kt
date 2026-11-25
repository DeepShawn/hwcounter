package com.deepshawn.hwcounter.desktop

import com.deepshawn.hwcounter.cloud.CloudClient
import com.deepshawn.hwcounter.cloud.CloudConfig
import com.deepshawn.hwcounter.cloud.HistoryItem
import com.deepshawn.hwcounter.cloud.Session
import com.deepshawn.hwcounter.cloud.Trade
import com.deepshawn.hwcounter.data.AppData
import com.deepshawn.hwcounter.data.Assignment
import com.deepshawn.hwcounter.data.AutoResetOps
import com.deepshawn.hwcounter.data.Exam
import com.deepshawn.hwcounter.data.SchoolClass
import com.deepshawn.hwcounter.data.Settle
import com.deepshawn.hwcounter.data.Student
import com.deepshawn.hwcounter.data.applyAutoReset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.time.Duration
import java.util.prefs.Preferences

/**
 * 账号与同步（与安卓 SyncManager、网页版 store.js 同样的规则）：
 * - 老师登录：本机一份可修改的数据（只给这个账号用），改动几秒后上传；上传带云端版本号，被别的设备抢先时比对后再传；两边都改过时可选合并
 * - 普通用户（凭邀请码注册）：只读查看所属老师的数据
 * 登录凭证存在本机（Windows 注册表的当前用户项），数据文件在 %APPDATA%\HwCounter。
 */
object Account {
    private val p = Preferences.userRoot().node("com/deepshawn/hwcounter/desktop/cloud")
    private val json = Cloud.json

    fun loadSession(): Session? = p.get("session", null)?.let { runCatching { json.decodeFromString(Session.serializer(), it) }.getOrNull() }
    fun saveSession(s: Session?) { if (s == null) p.remove("session") else p.put("session", json.encodeToString(Session.serializer(), s)); runCatching { p.flush() } }
    val client = CloudClient(::loadSession, ::saveSession)

    // ---------- 同步记录（只对当前账号） ----------
    var baseVersion: Long get() = p.getLong("baseVersion", 0); set(v) = p.putLong("baseVersion", v)
    var syncedHash: String get() = p.get("syncedHash", ""); set(v) = p.put("syncedHash", v)
    var lastSyncAt: Long get() = p.getLong("lastSyncAt", 0); set(v) = p.putLong("lastSyncAt", v)
    var auto: Boolean get() = p.getBoolean("auto", true); set(v) = p.putBoolean("auto", v)
    var previewInit: Boolean get() = p.getBoolean("previewInit", false); set(v) = p.putBoolean("previewInit", v)
    private var owner: String get() = p.get("owner", ""); set(v) = p.put("owner", v)
    fun cachedRole(uid: String): String? = p.get("role.$uid", null)
    fun cacheRole(uid: String, role: String) = p.put("role.$uid", role)
    private fun resetMeta() { baseVersion = 0; syncedHash = ""; lastSyncAt = 0; previewInit = false }

    private val dataFile get() = File(Cloud.dir, "data.json")

    /** 老师本机的数据：换了账号就从空数据开始（避免把别人的数据传进自己的云端） */
    fun loadLocal(uid: String): AppData {
        if (owner != uid) { owner = uid; resetMeta(); runCatching { dataFile.delete() }; return AppData() }
        return runCatching { json.decodeFromString(AppData.serializer(), dataFile.readText()) }.getOrElse { AppData() }
    }
    fun saveLocal(d: AppData) {
        runCatching {
            Cloud.dir.mkdirs()
            val tmp = File(Cloud.dir, "data.json.tmp"); tmp.writeText(json.encodeToString(AppData.serializer(), d))
            if (!tmp.renameTo(dataFile)) { dataFile.writeText(tmp.readText()); tmp.delete() }
        }
    }
    fun clearLocal() { owner = ""; resetMeta(); runCatching { dataFile.delete() } }

    // ---------- 云端 ----------
    private fun strip(d: AppData) = d.copy(settings = d.settings.copy(ai = d.settings.ai.copy(apiKey = "")))
    fun hash(d: AppData): String = MessageDigest.getInstance("SHA-256").digest(json.encodeToString(AppData.serializer(), strip(d)).toByteArray()).joinToString("") { "%02x".format(it) }.take(32)
    fun decode(e: JsonElement): AppData = json.decodeFromJsonElement(AppData.serializer(), e)
    private fun encode(d: AppData): JsonElement = json.encodeToJsonElement(AppData.serializer(), strip(d))
    val device: String get() = "Windows · ${System.getenv("COMPUTERNAME")?.takeIf { it.isNotBlank() } ?: "电脑"}"

    suspend fun signIn(user: String, pw: String): Session = client.signIn(user, pw)
    suspend fun me(): Trade.Me = Trade.me(client)
    suspend fun push(d: AppData, base: Long): Long = client.push(encode(d), base, device).also { v -> baseVersion = v; syncedHash = hash(d); lastSyncAt = System.currentTimeMillis() }
    suspend fun history(): List<HistoryItem> = client.history()
    suspend fun historyData(id: Long): AppData = decode(client.historyData(id))
    suspend fun changePassword(old: String, new: String) = client.changePassword(old, new)
    suspend fun signOut() { runCatching { client.signOut() }; saveSession(null); clearLocal() }
    fun displayEmail(): String = CloudConfig.displayEmail(loadSession()?.email) ?: ""

    /** 开关“允许未登录预览”（桌面版的 HttpURLConnection 不支持 PATCH，这里单独用 HttpClient 发） */
    suspend fun setPublic(flag: Boolean) = withContext(Dispatchers.IO) {
        client.rpc("hw_me") // 先发一个请求，让快过期的登录凭证自动续期
        val s = loadSession() ?: error("请先登录")
        val req = HttpRequest.newBuilder(URI("${CloudConfig.URL}/rest/v1/hw_snapshots?user_id=eq.${java.net.URLEncoder.encode(s.userId, "UTF-8")}"))
            .header("apikey", CloudConfig.KEY).header("Authorization", "Bearer ${s.accessToken}").header("Content-Type", "application/json").header("Prefer", "return=minimal")
            .timeout(Duration.ofSeconds(20)).method("PATCH", HttpRequest.BodyPublishers.ofString("""{"public_read":$flag}""")).build()
        val res = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString())
        if (res.statusCode() !in 200..299) error("设置失败，请稍后再试")
    }

    /** 每月定期重置（含联动结算），与安卓一致 */
    fun applyAutoResets(d: AppData, now: Long = System.currentTimeMillis()): AppData {
        val rules = d.settings.rules
        val next = d.classes.map { Settle.applyAuto(it.applyAutoReset(rules, now), rules) }
        return if (next == d.classes) d else d.copy(classes = next)
    }

    /** 合并：班级 / 学生 / 作业 / 积分按 id 取并集；同一次作业的登记状态逐个学生合并（本机优先）。与安卓 SyncManager.merge 相同 */
    fun merge(local: AppData, remote: AppData): AppData {
        val classes = LinkedHashMap<String, SchoolClass>()
        remote.classes.forEach { classes[it.id] = it }
        local.classes.forEach { l -> val r = classes[l.id]; classes[l.id] = if (r == null) l else mergeClass(l, r) }
        return local.copy(classes = classes.values.toList(), currentClassId = local.currentClassId ?: remote.currentClassId)
    }
    private fun mergeClass(l: SchoolClass, r: SchoolClass): SchoolClass {
        val students = LinkedHashMap<String, Student>(); r.students.forEach { students[it.id] = it }; l.students.forEach { students[it.id] = it }
        val assigns = LinkedHashMap<String, Assignment>(); r.assignments.forEach { assigns[it.id] = it }
        l.assignments.forEach { la -> val ra = assigns[la.id]; assigns[la.id] = if (ra == null) la else la.copy(statuses = ra.statuses + la.statuses, comments = ra.comments + la.comments, countedBooks = la.countedBooks ?: ra.countedBooks) }
        val exams = LinkedHashMap<String, Exam>(); r.exams.forEach { exams[it.id] = it }
        l.exams.forEach { le -> val re = exams[le.id]; exams[le.id] = if (re == null) le else le.copy(scores = re.scores + le.scores, absent = (re.absent + le.absent).distinct().filter { it !in le.scores }) }
        return l.copy(
            students = students.values.toList(), assignments = assigns.values.toList(), scores = (r.scores + l.scores).distinctBy { it.id },
            recharges = (r.recharges + l.recharges).distinctBy { it.id }.sortedBy { it.time }, exams = exams.values.sortedBy { it.date },
            groupLeaders = r.groupLeaders + l.groupLeaders, seating = l.seating ?: r.seating, settlements = (r.settlements + l.settlements).distinctBy { it.id }.sortedBy { it.time },
            autoReset = (l.autoReset ?: r.autoReset)?.let { ar -> ar.copy(skipped = (ar.skipped + l.autoReset?.skipped.orEmpty() + r.autoReset?.skipped.orEmpty()).distinct()) },
        ).let { with(AutoResetOps) { it.dropSkipped() } }
    }
}
