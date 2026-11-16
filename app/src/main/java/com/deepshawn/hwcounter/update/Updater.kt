package com.deepshawn.hwcounter.update

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import com.deepshawn.hwcounter.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 自动检查更新、自动下载更新：从 GitHub Releases 读取最新版本，找到 APK 下载到本机缓存，下载完成后调起系统安装。
 * 设置（只存本机）：自动检查（默认开）、自动下载（默认开）、仅 Wi-Fi 下自动下载（默认开）、跳过的版本。
 */
object Updater {
    const val REPO = "DeepShawn/hwcounter"
    private const val API = "https://api.github.com/repos/$REPO/releases/latest"
    private const val PREFS = "update"
    /** 两次自动检查的最小间隔 */
    private const val INTERVAL = 6 * 3600_000L

    data class Release(val tag: String, val version: String, val notes: String, val apkUrl: String?, val apkName: String?, val size: Long, val page: String)

    sealed interface Status {
        data object Idle : Status
        data object Checking : Status
        data class UpToDate(val latest: String) : Status
        data class Available(val release: Release) : Status
        data class Downloading(val release: Release, val progress: Float) : Status
        data class Ready(val release: Release, val file: File) : Status
        data class Failed(val message: String) : Status
    }

    /** 界面观察的状态 */
    var status by mutableStateOf<Status>(Status.Idle)
        private set
    /** 自动检查发现新版本 / 下载完成时弹窗 */
    var prompt by mutableStateOf<Status?>(null)

    data class Prefs(val autoCheck: Boolean = true, val autoDownload: Boolean = true, val wifiOnly: Boolean = true, val skip: String = "", val lastCheck: Long = 0)

    fun prefs(ctx: Context): Prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).let {
        Prefs(it.getBoolean("autoCheck", true), it.getBoolean("autoDownload", true), it.getBoolean("wifiOnly", true), it.getString("skip", "") ?: "", it.getLong("lastCheck", 0))
    }

    fun setPrefs(ctx: Context, p: Prefs) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("autoCheck", p.autoCheck).putBoolean("autoDownload", p.autoDownload).putBoolean("wifiOnly", p.wifiOnly)
            .putString("skip", p.skip).putLong("lastCheck", p.lastCheck).apply()
    }

    val current: String get() = BuildConfig.VERSION_NAME

    /** 版本号比较："2.10.0" > "2.9.1"；忽略前缀 v 和后缀 */
    fun isNewer(latest: String, cur: String = current): Boolean {
        fun parts(v: String) = v.trim().removePrefix("v").removePrefix("V").split('.', '-', '+').map { p -> p.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
        val a = parts(latest); val b = parts(cur)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** 发布说明里只取本版本那一段（到下一个“### ”为止） */
    fun notesOf(body: String): String {
        val lines = body.lines()
        val start = lines.indexOfFirst { it.trimStart().startsWith("### ") }
        if (start < 0) return body.trim().take(1500)
        val end = lines.drop(start + 1).indexOfFirst { it.trimStart().startsWith("### ") }.let { if (it < 0) lines.size else start + 1 + it }
        return lines.subList(start, end).joinToString("\n") { it.trim().replace("**", "").replace("`", "") }.trim()
    }

    fun parse(body: String): Release {
        val o = Json.parseToJsonElement(body).jsonObject
        val tag = o["tag_name"]?.jsonPrimitive?.contentOrNull ?: error("没有读到版本号")
        val asset = (o["assets"] as? JsonArray)?.map { it as JsonObject }?.firstOrNull { (it["name"]?.jsonPrimitive?.contentOrNull ?: "").endsWith(".apk") }
        return Release(
            tag, tag.removePrefix("v"), notesOf(o["body"]?.jsonPrimitive?.contentOrNull ?: ""),
            asset?.get("browser_download_url")?.jsonPrimitive?.contentOrNull, asset?.get("name")?.jsonPrimitive?.contentOrNull,
            asset?.get("size")?.jsonPrimitive?.longOrNull ?: 0, o["html_url"]?.jsonPrimitive?.contentOrNull ?: "https://github.com/$REPO/releases",
        )
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 15_000; c.readTimeout = 20_000
            c.setRequestProperty("Accept", "application/vnd.github+json")
            c.setRequestProperty("User-Agent", "hwcounter-android")
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) error(if (code == 403) "GitHub 访问太频繁，请稍后再试" else "HTTP $code")
            return text
        } finally { c.disconnect() }
    }

    private fun onWifi(ctx: Context): Boolean {
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    private fun apkFile(ctx: Context, r: Release) = File(File(ctx.cacheDir, "update").apply { mkdirs() }, r.apkName ?: "hwcounter-${r.version}.apk")

    /**
     * 检查更新。[manual] 为 true 时（设置里点“检查更新”）忽略间隔和“跳过此版本”。
     * 自动模式：发现新版本时，若开启自动下载（且满足 Wi-Fi 条件）就直接下载，下载好再提示安装；否则提示下载。
     */
    suspend fun check(ctx: Context, manual: Boolean = false) {
        if (Build.FINGERPRINT == "robolectric") return
        val p = prefs(ctx)
        // 调试版（包名带 .debug）装不上正式版的安装包，不自动检查
        if (!manual && (BuildConfig.DEBUG || !p.autoCheck || System.currentTimeMillis() - p.lastCheck < INTERVAL)) return
        if (status is Status.Checking || status is Status.Downloading) return
        status = Status.Checking
        val r = runCatching { withContext(Dispatchers.IO) { parse(get(API)) } }.getOrElse {
            status = Status.Failed("检查更新失败：${it.message ?: "网络不可用"}")
            return
        }
        setPrefs(ctx, prefs(ctx).copy(lastCheck = System.currentTimeMillis()))
        if (!isNewer(r.version)) { status = Status.UpToDate(r.version); return }
        val file = apkFile(ctx, r)
        if (file.exists() && r.size > 0 && file.length() == r.size) { status = Status.Ready(r, file); if (manual || r.version != p.skip) prompt = status; return }
        status = Status.Available(r)
        if (!manual && r.version == p.skip) return
        if (!manual && p.autoDownload && r.apkUrl != null && (!p.wifiOnly || onWifi(ctx))) download(ctx, r)
        else prompt = status
    }

    /** 下载 APK（断开会重来；已下载完整的直接用） */
    suspend fun download(ctx: Context, r: Release) {
        val url = r.apkUrl ?: run { status = Status.Failed("这个版本没有安卓安装包"); return }
        val file = apkFile(ctx, r)
        status = Status.Downloading(r, 0f)
        val ok = runCatching {
            withContext(Dispatchers.IO) {
                val tmp = File(file.parentFile, file.name + ".part")
                var link = url
                var conn: HttpURLConnection
                var hops = 0
                while (true) {   // 跟随 GitHub 的跳转
                    conn = URL(link).openConnection() as HttpURLConnection
                    conn.instanceFollowRedirects = false
                    conn.connectTimeout = 15_000; conn.readTimeout = 30_000
                    conn.setRequestProperty("User-Agent", "hwcounter-android")
                    val code = conn.responseCode
                    if (code in 300..399 && hops < 5) { link = conn.getHeaderField("Location") ?: error("跳转失败"); conn.disconnect(); hops++; continue }
                    if (code !in 200..299) error("HTTP $code")
                    break
                }
                val total = conn.contentLengthLong.takeIf { it > 0 } ?: r.size
                conn.inputStream.use { inp ->
                    tmp.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024); var done = 0L; var last = 0L
                        while (true) {
                            val n = inp.read(buf); if (n < 0) break
                            out.write(buf, 0, n); done += n
                            if (total > 0 && done - last > 256 * 1024) { last = done; status = Status.Downloading(r, (done.toFloat() / total).coerceIn(0f, 1f)) }
                        }
                    }
                }
                conn.disconnect()
                if (r.size > 0 && tmp.length() != r.size) error("下载不完整")
                file.delete(); tmp.renameTo(file)
                // 清理旧安装包
                file.parentFile?.listFiles()?.filter { it != file }?.forEach { it.delete() }
            }
        }
        ok.onSuccess { status = Status.Ready(r, file); prompt = status }
            .onFailure { status = Status.Failed("下载失败：${it.message ?: "网络不可用"}") }
    }

    fun skip(ctx: Context, version: String) = setPrefs(ctx, prefs(ctx).copy(skip = version))

    /** 调起系统安装；没有“安装未知应用”权限时先打开授权页面 */
    fun install(ctx: Context, file: File): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !ctx.packageManager.canRequestPackageInstalls()) {
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return false
        }
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        return true
    }
}
