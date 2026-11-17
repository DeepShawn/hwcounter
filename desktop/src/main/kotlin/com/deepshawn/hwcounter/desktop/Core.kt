package com.deepshawn.hwcounter.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.deepshawn.hwcounter.data.AppData
import com.deepshawn.hwcounter.data.Assignment
import com.deepshawn.hwcounter.data.HwStatus
import com.deepshawn.hwcounter.data.SchoolClass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.prefs.Preferences

/** 云端公开数据（只读）：与网页版读取同一份老师公开的快照，不登录、不写入 */
object Cloud {
    private const val URL = "https://beasxxexggnjcocdbtdl.supabase.co"
    private const val KEY = "sb_publishable_85hufMmFQvmLQt2Rz1Hcrw_S4t7BjAO"
    val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; encodeDefaults = true }
    private val http: HttpClient by lazy { HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(12)).followRedirects(HttpClient.Redirect.NORMAL).build() }

    /** 读取最新的公开快照；老师没有开放时返回 null */
    suspend fun pullPublic(): AppData? = withContext(Dispatchers.IO) {
        val req = HttpRequest.newBuilder(URI("$URL/rest/v1/hw_snapshots?select=data,version,updated_at&public_read=eq.true&order=updated_at.desc&limit=1"))
            .header("apikey", KEY).header("Accept", "application/json").timeout(Duration.ofSeconds(20)).GET().build()
        val res = http.send(req, HttpResponse.BodyHandlers.ofString())
        if (res.statusCode() !in 200..299) error("HTTP ${res.statusCode()}")
        val arr = json.parseToJsonElement(res.body()) as? JsonArray ?: return@withContext null
        val row = arr.firstOrNull() as? JsonObject ?: return@withContext null
        val data = row["data"] ?: return@withContext null
        json.decodeFromJsonElement(AppData.serializer(), data)
    }

    private val dir: File get() = File(System.getenv("APPDATA")?.takeIf { it.isNotBlank() } ?: (System.getProperty("user.home") + File.separator + ".hwcounter"), "HwCounter")
    private val cacheFile get() = File(dir, "preview.json")

    /** 本机缓存（离线也能看上次的数据） */
    fun loadCache(): AppData? = runCatching { json.decodeFromString(AppData.serializer(), cacheFile.readText()) }.getOrNull()
    fun saveCache(d: AppData) { runCatching { dir.mkdirs(); cacheFile.writeText(json.encodeToString(AppData.serializer(), d)) } }
}

/** 本机个性化设置（只存这台电脑，与网页版相同的几项） */
class Prefs {
    private val p = Preferences.userRoot().node("com/deepshawn/hwcounter/desktop")
    private val _theme = mutableStateOf(p.get("theme", "hyper"))
    private val _dark = mutableStateOf(p.getInt("dark", 0))
    private val _animations = mutableStateOf(p.getBoolean("animations", false))
    private val _usColors = mutableStateOf(p.getBoolean("usColors", true))
    private val _layout = mutableStateOf(p.get("layout", "grid"))
    private val _classId = mutableStateOf<String?>(p.get("classId", null))
    private val _onboarded = mutableStateOf(p.getBoolean("onboarded", false))
    var theme: String get() = _theme.value; set(v) { _theme.value = v; p.put("theme", v) }
    var dark: Int get() = _dark.value; set(v) { _dark.value = v; p.putInt("dark", v) }
    var animations: Boolean get() = _animations.value; set(v) { _animations.value = v; p.putBoolean("animations", v) }
    var usColors: Boolean get() = _usColors.value; set(v) { _usColors.value = v; p.putBoolean("usColors", v) }
    var layout: String get() = _layout.value; set(v) { _layout.value = v; p.put("layout", v) }
    var classId: String? get() = _classId.value; set(v) { _classId.value = v; if (v != null) p.put("classId", v) }
    var onboarded: Boolean get() = _onboarded.value; set(v) { _onboarded.value = v; p.putBoolean("onboarded", v) }
}

/** 页面路由 */
sealed interface Route {
    data class Assign(val id: String) : Route
    data class Student(val id: String) : Route
    data class Group(val id: String) : Route
    data class Exam(val id: String) : Route
    data object Exams : Route
}

enum class Tab(val label: String, val icon: String) { HOME("首页", "🏠"), ROSTER("名单", "👥"), SCORES("积分", "🏆"), STATS("统计", "📈"), SETTINGS("设置", "⚙️") }

/** 显示用的状态：在原有状态上加“复审不合格”“抄袭” */
enum class View(val label: String, val color: Long) {
    SUBMITTED("已交", 0xFF1DB57A), EXCELLENT("优秀", 0xFFD99A00), LATE("迟交", 0xFFFF8A3D), MISSING("未交", 0xFFF0475A),
    REVIEW("复审不合格", 0xFFB0306F), PLAG("抄袭", 0xFFD9480F), LEAVE("请假", 0xFF4A8CFF), NONE("未登记", 0xFF9AA0A8),
}

fun viewOf(a: Assignment, sid: String): View = when {
    a.plagiarized(sid) -> View.PLAG
    a.failedReview(sid) -> View.REVIEW
    else -> when (a.statuses[sid] ?: HwStatus.NONE) {
        HwStatus.SUBMITTED -> View.SUBMITTED; HwStatus.EXCELLENT -> View.EXCELLENT; HwStatus.LATE -> View.LATE
        HwStatus.MISSING -> View.MISSING; HwStatus.LEAVE -> View.LEAVE; HwStatus.NONE -> View.NONE
    }
}

/** 交齐率（请假、未登记不算） */
fun handRate(c: SchoolClass, sid: String, list: List<Assignment> = c.assignments): Double? {
    var got = 0; var need = 0
    for (a in list) {
        val k = a.statuses[sid] ?: HwStatus.NONE
        if (k == HwStatus.LEAVE || k == HwStatus.NONE) continue
        need++; if (k == HwStatus.SUBMITTED || k == HwStatus.EXCELLENT || k == HwStatus.LATE) got++
    }
    return if (need == 0) null else got.toDouble() / need
}

/** 这次作业里这位同学的抄袭关系（“抄袭 甲” / “提供给 乙 抄”） */
fun plagLine(c: SchoolClass, a: Assignment, sid: String): String {
    fun names(ids: List<String>) = ids.mapNotNull { id -> c.students.firstOrNull { it.id == id }?.name }.joinToString("、")
    val from = a.copiedFrom(sid); val by = a.copiedBy(sid)
    return listOfNotNull(from.takeIf { it.isNotEmpty() }?.let { "抄袭 ${names(it)}" }, by.takeIf { it.isNotEmpty() }?.let { "提供给 ${names(it)} 抄" }).joinToString("；")
}

fun pct(x: Double?): String = if (x == null || !x.isFinite()) "—" else "${Math.round(x * 100)}%"
fun pct(x: Float): String = pct(x.toDouble())
fun md(t: Long): String { val c = java.util.Calendar.getInstance().apply { timeInMillis = t }; return "${c.get(java.util.Calendar.MONTH) + 1}/${c.get(java.util.Calendar.DAY_OF_MONTH)}" }
fun fmtTime(t: Long): String { val c = java.util.Calendar.getInstance().apply { timeInMillis = t }; return "${c.get(java.util.Calendar.MONTH) + 1}月${c.get(java.util.Calendar.DAY_OF_MONTH)}日 %02d:%02d".format(c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE)) }
