package com.deepshawn.hwcounter.recog

import android.graphics.Bitmap
import android.util.Base64
import com.deepshawn.hwcounter.data.AiConfig
import com.deepshawn.hwcounter.data.Student
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 多模态大模型识别。支持：
 *  - OpenAI 兼容协议（通义千问 VL、豆包、Kimi、智谱 GLM-4V、DeepSeek、GPT-4o、本地 Ollama 等）
 *  - Anthropic Claude Messages 协议
 */
object AiRecognizer {
    data class AiResult(val count: Int?, val ids: List<String>, val names: List<String>, val note: String, val raw: String)

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun prompt(roster: List<Student>, mode: String): String {
        val list = roster.take(150).joinToString("; ") { "${it.no} ${it.name}" }
        return if (mode == "stack") """
你是一名细心的助教。图片是一摞叠放的作业本（可能是侧面或俯视）。请仔细数出作业本的数量。
只输出 JSON，不要任何解释：{"count": 本数(整数), "ids": [], "names": [], "note": "一句话说明依据"}
""".trimIndent() else """
你是一名细心的助教。图片里是若干本学生作业本（可能平铺、错开或成摞展开）。
任务：1) 数出图中作业本的数量；2) 读出每本封面上的学号与姓名。
班级名单（学号 姓名）：$list
请尽量把识别结果对应到名单中的学号；看不清的不要编造。
只输出 JSON，不要任何解释：{"count": 本数(整数), "ids": ["学号", ...], "names": ["姓名", ...], "note": "一句话说明"}
""".trimIndent()
    }

    suspend fun recognize(cfg: AiConfig, bmp: Bitmap, roster: List<Student>, mode: String = "covers"): AiResult =
        withContext(Dispatchers.IO) {
            requireKey(cfg)
            require(cfg.vision) { "当前模型为纯文本模式，不能直接看图（可在设置中切换）" }
            val b64 = Base64.encodeToString(ImageUtils.toJpeg(bmp), Base64.NO_WRAP)
            val text = prompt(roster, mode)
            val content = if (cfg.provider == "anthropic") callAnthropic(cfg, b64, text) else callOpenAi(cfg, b64, text)
            parse(content)
        }

    /**
     * 纯文本模式：把本地 OCR 出的文字交给大模型做“纠错 + 对名单”。
     * 适合不支持图片的模型（DeepSeek 文本模型等），照片本身不会离开手机。
     */
    suspend fun recognizeText(cfg: AiConfig, ocrLines: List<String>, roster: List<Student>): AiResult =
        withContext(Dispatchers.IO) {
            requireKey(cfg)
            val list = roster.take(200).joinToString("; ") { "${it.no} ${it.name}" }
            val text = """
你是一名细心的助教。下面是用 OCR 从一张作业本照片中识别出的文字（每行一段，可能有错字、漏字、数字误识，如 O/0、l/1、S/5）。
照片里是若干本学生作业本的封面。请推断照片中共有几本作业，以及分别属于名单中的哪些学生。
班级名单（学号 姓名）：$list
OCR 文字：
${ocrLines.take(300).joinToString("\n")}
只采纳有把握的匹配，不要编造。只输出 JSON，不要任何解释：{"count": 本数(整数), "ids": ["学号", ...], "names": ["姓名", ...], "note": "一句话说明"}
""".trimIndent()
            val body = buildJsonObject {
                put("model", cfg.model)
                if (cfg.provider == "anthropic") put("max_tokens", 2048) else put("temperature", 0.1)
                putJsonArray("messages") { addJsonObject { put("role", "user"); put("content", text) } }
            }
            parse(extractText(cfg, post(cfg, body.toString())))
        }

    /** 拉取服务商可用模型列表（OpenAI 兼容 GET /models；Anthropic GET /v1/models） */
    suspend fun listModels(cfg: AiConfig): List<String> = withContext(Dispatchers.IO) {
        val base = cfg.baseUrl.trim().trimEnd('/').removeSuffix("/chat/completions").removeSuffix("/messages")
        val url = if (cfg.provider == "anthropic") (if (base.endsWith("/v1")) "$base/models" else "$base/v1/models") else "$base/models"
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            if (cfg.provider == "anthropic") {
                conn.setRequestProperty("x-api-key", cfg.apiKey)
                conn.setRequestProperty("anthropic-version", "2023-06-01")
            } else conn.setRequestProperty("Authorization", "Bearer ${cfg.apiKey}")
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) throw RuntimeException("HTTP $code：${text.take(200)}")
            val root = json.parseToJsonElement(text)
            val arr = (root as? JsonObject)?.get("data") as? JsonArray ?: (root as? JsonArray) ?: JsonArray(emptyList())
            arr.mapNotNull { (it as? JsonObject)?.get("id")?.jsonPrimitive?.contentOrNull }.sorted()
        } finally {
            conn.disconnect()
        }
    }

    private fun requireKey(cfg: AiConfig) =
        require(cfg.apiKey.isNotBlank() || cfg.baseUrl.contains("localhost") || cfg.baseUrl.contains("192.168.")) { "请先在设置中填写 API Key" }

    suspend fun ping(cfg: AiConfig): String = withContext(Dispatchers.IO) {
        val body = if (cfg.provider == "anthropic") buildJsonObject {
            put("model", cfg.model); put("max_tokens", 20)
            putJsonArray("messages") { addJsonObject { put("role", "user"); put("content", "回复“连接成功”四个字") } }
        } else buildJsonObject {
            put("model", cfg.model)
            putJsonArray("messages") { addJsonObject { put("role", "user"); put("content", "回复“连接成功”四个字") } }
        }
        val resp = post(cfg, body.toString())
        extractText(cfg, resp)
    }

    private fun endpoint(cfg: AiConfig): String {
        val base = cfg.baseUrl.trim().trimEnd('/')
        return if (cfg.provider == "anthropic") {
            if (base.endsWith("/messages")) base else if (base.endsWith("/v1")) "$base/messages" else "$base/v1/messages"
        } else {
            if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
        }
    }

    private fun post(cfg: AiConfig, body: String): String {
        val conn = URL(endpoint(cfg)).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 20_000
            conn.readTimeout = 120_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            if (cfg.provider == "anthropic") {
                conn.setRequestProperty("x-api-key", cfg.apiKey)
                conn.setRequestProperty("anthropic-version", "2023-06-01")
            } else {
                conn.setRequestProperty("Authorization", "Bearer ${cfg.apiKey}")
            }
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (code !in 200..299) throw RuntimeException("HTTP $code：${text.take(300)}")
            return text
        } finally {
            conn.disconnect()
        }
    }

    private fun extractText(cfg: AiConfig, resp: String): String {
        val root = json.parseToJsonElement(resp).jsonObject
        return if (cfg.provider == "anthropic") {
            root["content"]?.jsonArray?.joinToString("") { (it as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull ?: "" } ?: resp
        } else {
            val msg = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonObject
            val c = msg?.get("content")
            when (c) {
                is JsonPrimitive -> c.contentOrNull ?: ""
                is JsonArray -> c.joinToString("") { (it as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull ?: "" }
                else -> resp
            }
        }
    }

    private fun callOpenAi(cfg: AiConfig, b64: String, text: String): String {
        val body = buildJsonObject {
            put("model", cfg.model)
            put("temperature", 0.1)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "user")
                    putJsonArray("content") {
                        addJsonObject {
                            put("type", "image_url")
                            putJsonObject("image_url") { put("url", "data:image/jpeg;base64,$b64") }
                        }
                        addJsonObject { put("type", "text"); put("text", text) }
                    }
                }
            }
        }
        return extractText(cfg, post(cfg, body.toString()))
    }

    private fun callAnthropic(cfg: AiConfig, b64: String, text: String): String {
        val body = buildJsonObject {
            put("model", cfg.model)
            put("max_tokens", 2048)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "user")
                    putJsonArray("content") {
                        addJsonObject {
                            put("type", "image")
                            putJsonObject("source") {
                                put("type", "base64"); put("media_type", "image/jpeg"); put("data", b64)
                            }
                        }
                        addJsonObject { put("type", "text"); put("text", text) }
                    }
                }
            }
        }
        return extractText(cfg, post(cfg, body.toString()))
    }

    fun parse(content: String): AiResult {
        val start = content.indexOf('{')
        val end = content.lastIndexOf('}')
        if (start < 0 || end <= start) {
            val n = Regex("(\\d+)\\s*本").find(content)?.groupValues?.get(1)?.toIntOrNull()
            return AiResult(n, emptyList(), emptyList(), content.take(120), content)
        }
        return try {
            val o = json.parseToJsonElement(content.substring(start, end + 1)).jsonObject
            fun arr(k: String) = (o[k] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
            val count = (o["count"] as? JsonPrimitive)?.let { it.intOrNull ?: it.contentOrNull?.filter { ch -> ch.isDigit() }?.toIntOrNull() }
            AiResult(count, arr("ids"), arr("names"), (o["note"] as? JsonPrimitive)?.contentOrNull ?: "", content)
        } catch (e: Exception) {
            AiResult(null, emptyList(), emptyList(), "解析失败：" + content.take(120), content)
        }
    }
}
