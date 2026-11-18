package com.deepshawn.hwcounter.data

import java.io.File
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * 本机自动备份：完整数据（不含 AI API Key）压缩后存在 App 私有目录 backups/，最多保留 [keep] 份（默认 50），最旧的自动删除。
 * 文件名里带上时间、原因和数量，列出备份时不用打开文件：bk_<毫秒>_<原因>_<班级数>_<学生数>_<作业数>.json.gz
 *
 * 什么时候备份（见 AppViewModel）：
 * - 自动：数据有改动、且距上次备份超过 [interval]（默认 10 分钟）；打开 App 时如有改动也备份一份；
 * - 危险操作前：从云端覆盖本机、恢复备份或历史版本、清空数据之前，先把当前数据存一份；
 * - 手动：设置 → 数据与备份 → 立即备份。
 */
class LocalBackups(private val dir: File, val keep: Int = 50, val interval: Long = 10 * 60_000L) {
    enum class Reason(val code: String, val label: String) {
        AUTO("auto", "自动"), MANUAL("manual", "手动"), LAUNCH("launch", "打开 App"),
        BEFORE_CLOUD("cloud", "从云端更新前"), BEFORE_RESTORE("restore", "恢复前"), BEFORE_RESET("reset", "清空前");
        companion object { fun of(code: String) = entries.firstOrNull { it.code == code } ?: AUTO }
    }

    data class Entry(val file: File, val time: Long, val reason: Reason, val classes: Int, val students: Int, val assignments: Int) {
        val size: Long get() = file.length()
    }

    /** 新的在前 */
    fun list(): List<Entry> = (dir.listFiles() ?: emptyArray())
        .mapNotNull { parse(it) }
        .sortedByDescending { it.time }

    private fun parse(f: File): Entry? {
        val m = NAME.matchEntire(f.name) ?: return null
        val (t, r, c, s, a) = m.destructured
        return Entry(f, t.toLong(), Reason.of(r), c.toInt(), s.toInt(), a.toInt())
    }

    /** 写一份备份（[text] 是完整数据的 JSON），然后删掉超出 [keep] 份的旧备份 */
    fun save(text: String, d: AppData, reason: Reason, now: Long = System.currentTimeMillis()): Entry? = runCatching {
        dir.mkdirs()
        // 同一毫秒不重名
        var t = now
        while (File(dir, name(t, reason, d)).exists()) t++
        val f = File(dir, name(t, reason, d))
        val tmp = File(dir, f.name + ".tmp")
        GZIPOutputStream(tmp.outputStream()).bufferedWriter(Charsets.UTF_8).use { it.write(text) }
        if (!tmp.renameTo(f)) { tmp.copyTo(f, overwrite = true); tmp.delete() }
        prune()
        parse(f)
    }.getOrNull()

    fun read(e: Entry): String = GZIPInputStream(e.file.inputStream()).bufferedReader(Charsets.UTF_8).use { it.readText() }

    fun delete(e: Entry) { e.file.delete() }

    /** 只留最新的 [keep] 份；顺便清理写了一半的临时文件 */
    fun prune() {
        list().drop(keep).forEach { it.file.delete() }
        (dir.listFiles() ?: emptyArray()).filter { it.name.endsWith(".tmp") && System.currentTimeMillis() - it.lastModified() > 60_000 }.forEach { it.delete() }
    }

    /** 上次备份的时间（没有为 0） */
    fun lastTime(): Long = list().firstOrNull()?.time ?: 0L

    /** 自动备份该不该做：数据和上次备份不同，并且离上次备份超过 [interval] */
    fun due(textHash: Int, lastHash: Int?, now: Long = System.currentTimeMillis()): Boolean =
        textHash != lastHash && now - lastTime() >= interval

    companion object {
        private val NAME = Regex("""bk_(\d+)_([a-z]+)_(\d+)_(\d+)_(\d+)\.json\.gz""")
        private fun name(t: Long, r: Reason, d: AppData) =
            "bk_${t}_${r.code}_${d.classes.size}_${d.classes.sumOf { it.students.size }}_${d.classes.sumOf { it.assignments.size }}.json.gz"
    }
}
