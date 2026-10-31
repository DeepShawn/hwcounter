package com.deepshawn.hwcounter.recog

import com.deepshawn.hwcounter.data.AppData
import com.deepshawn.hwcounter.data.Assignment
import com.deepshawn.hwcounter.data.Draft
import com.deepshawn.hwcounter.data.HwStatus
import com.deepshawn.hwcounter.data.SchoolClass
import com.deepshawn.hwcounter.data.Student
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * 统计表识别 + 统计 + 草稿合并。与网页版 stats.js、iPad 版同一套规则：
 * 老师手上的登记表（Excel / CSV / 粘贴 / 拍照），每行一个学生，后面若干列是 ✓ × 优 迟 假 之类的标记。
 */
object StatTable {
    private val TOKENS: List<Pair<HwStatus, List<String>>> = listOf(
        HwStatus.EXCELLENT to listOf("优秀", "优", "a+", "a", "★", "☆", "⭐", "🌟", "好", "棒", "优+", "优-", "a-"),
        HwStatus.LATE to listOf("迟交", "迟", "补交", "补", "晚交", "晚", "迟到", "后交"),
        HwStatus.LEAVE to listOf("请假", "病假", "事假", "假", "病", "免交", "免", "公假"),
        HwStatus.MISSING to listOf("未交", "没交", "缺交", "不交", "欠交", "未", "缺", "欠", "无", "没", "×", "✗", "✘", "✕", "╳", "x", "n", "no", "否", "0", "f", "false"),
        HwStatus.SUBMITTED to listOf("已交", "交了", "已完成", "完成", "交", "有", "是", "对", "✓", "✔", "√", "☑", "✅", "v", "y", "yes", "ok", "1", "true", "○", "〇", "o", "良", "良好", "中", "合格", "及格", "b", "b+", "b-", "c", "c+", "c-", "d"),
        HwStatus.NONE to listOf("", "-", "—", "–", "－", "/", "／", "\\", "空", "?", "？"),
    )
    private val TOKEN_MAP = LinkedHashMap<String, HwStatus>().apply { for ((k, l) in TOKENS) for (t in l) if (t !in this) put(t, k) }
    private val SCAN = TOKEN_MAP.keys.filter { it.isNotEmpty() && !Regex("^[a-z0-9+\\-]{1,2}$").matches(it) && it !in setOf("true", "false", "yes", "ok") }.sortedByDescending { it.length }
    private val HANDED = listOf(HwStatus.SUBMITTED, HwStatus.EXCELLENT, HwStatus.LATE)

    /** 全角转半角 */
    fun half(s: String): String = buildString { for (c in s) append(if (c in '\uff01'..'\uff5e') (c.code - 0xfee0).toChar() else if (c == '\u3000') ' ' else c) }

    /** 一个单元格 → 状态；认不出返回 null（空单元格返回 NONE） */
    fun parseStatus(cell: String): HwStatus? {
        val t = half(cell).trim().lowercase().replace(Regex("\\s+"), "")
        TOKEN_MAP[t]?.let { return it }
        HwStatus.entries.firstOrNull { it.name.equals(t, true) || it.label == t }?.let { return it }
        if (t.length <= 6) for (k in listOf(HwStatus.LEAVE, HwStatus.LATE, HwStatus.EXCELLENT, HwStatus.MISSING, HwStatus.SUBMITTED)) {
            if (SCAN.any { TOKEN_MAP[it] == k && t.contains(it) }) return k
        }
        return null
    }

    /** 粘贴 / CSV / TSV → 二维数组 */
    fun splitTable(text: String): List<List<String>> {
        val lines = text.removePrefix("\ufeff").replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val tab = lines.any { '\t' in it }
        val comma = !tab && lines.any { ',' in it || '，' in it }
        return lines.filter { it.isNotBlank() }.map { line ->
            when {
                tab -> line.split('\t')
                comma -> csvLine(line)
                else -> line.trim().split(Regex("[\\s;；|]+"))
            }.map { it.trim() }
        }
    }

    private fun csvLine(line: String): List<String> {
        val out = ArrayList<String>(); val cur = StringBuilder(); var q = false; var i = 0
        while (i < line.length) {
            val ch = line[i]
            if (q) {
                if (ch == '"' && line.getOrNull(i + 1) == '"') { cur.append('"'); i++ } else if (ch == '"') q = false else cur.append(ch)
            } else if (ch == '"') q = true else if (ch == ',' || ch == '，') { out += cur.toString(); cur.clear() } else cur.append(ch)
            i++
        }
        out += cur.toString()
        return out
    }

    data class Column(val index: Int, val header: String)
    data class Row(val studentId: String, val cells: MutableList<HwStatus>, val raw: String)
    data class Result(val columns: List<Column>, val rows: List<Row>, val unmatched: List<String>, val missingStudents: List<String>)

    private fun normNo(x: String): String { val t = half(x).trim().uppercase(); return if (t.isNotEmpty() && t.all { it.isDigit() }) t.trimStart('0').ifEmpty { "0" } else t }
    private fun han(c: String?) = c != null && c.any { it in '\u4e00'..'\u9fff' }

    /** 表格（二维字符串）→ 每个学生每列的状态 */
    fun recognizeTable(rows: List<List<String>>, students: List<Student>): Result {
        val m = SeatingLogic.SeatMatcher(students)
        val width = rows.maxOfOrNull { it.size } ?: 0
        fun exact(cell: String?): Student? {
            val c = cell?.trim().orEmpty(); if (c.isEmpty()) return null
            return students.firstOrNull { it.name == c } ?: students.firstOrNull { it.no.isNotBlank() && normNo(it.no) == normNo(c) }
        }
        // 1) 学号/姓名列（要求对上不同的学生：一整列“1”会对上 1 号同学，但那是状态列）
        val hitN = IntArray(width); val hitIds = Array(width) { HashSet<String>() }
        rows.forEach { r -> r.forEachIndexed { j, cell -> exact(cell)?.let { hitN[j]++; hitIds[j] += it.id } } }
        val need = max(1, ceil(rows.size * 0.25).toInt())
        val idCols = (0 until width).filter { hitIds[it].size >= need && hitIds[it].size >= hitN[it] * 0.6 }
        // 2) 每行的学生
        val rowStu = rows.map { r ->
            idCols.firstNotNullOfOrNull { exact(r.getOrNull(it)) }
                ?: idCols.firstNotNullOfOrNull { j -> r.getOrNull(j)?.takeIf { it.isNotBlank() }?.let { m.one(it) } }
                ?: (if (idCols.isEmpty()) r.firstNotNullOfOrNull { c -> if (han(c)) exact(c) ?: m.one(c) else null } else null)
                ?: m.all(r.filterIndexed { j, c -> if (idCols.isNotEmpty()) j in idCols else han(c) }.joinToString(" ")).firstOrNull()?.s
        }
        // 3) 状态列
        val statCols = ArrayList<Int>()
        for (j in 0 until width) {
            if (j in idCols) continue
            var filled = 0; var ok = 0; var real = 0
            rows.forEachIndexed { i, r ->
                if (rowStu[i] == null) return@forEachIndexed
                val v = r.getOrNull(j)?.trim().orEmpty(); if (v.isEmpty()) return@forEachIndexed
                filled++
                val s = parseStatus(v); if (s != null) { ok++; if (s != HwStatus.NONE) real++ }
            }
            if (real > 0 && ok >= filled * 0.6) statCols += j
        }
        // 4) 表头
        val headRow = rows.indices.firstOrNull { i -> rowStu[i] == null && statCols.any { j -> val v = rows[i].getOrNull(j)?.trim().orEmpty(); v.isNotEmpty() && parseStatus(v) == null } } ?: -1
        val columns = statCols.mapIndexed { k, j -> Column(j, (if (headRow >= 0) rows[headRow].getOrNull(j)?.trim() else null)?.takeIf { it.isNotEmpty() } ?: "第${k + 1}列") }
        val out = ArrayList<Row>(); val unmatched = ArrayList<String>(); val seen = HashSet<String>()
        rows.forEachIndexed { i, r ->
            val s = rowStu[i]
            if (s == null) { if (i != headRow && r.any { it.isNotBlank() }) unmatched += r.filter { it.isNotBlank() }.joinToString(" "); return@forEachIndexed }
            if (!seen.add(s.id)) return@forEachIndexed
            out += Row(s.id, statCols.map { j -> parseStatus(r.getOrNull(j).orEmpty()) ?: HwStatus.NONE }.toMutableList(), r.filter { it.isNotBlank() }.joinToString(" "))
        }
        return Result(columns, out, unmatched, students.filter { it.id !in seen }.map { it.id })
    }

    /** OCR 的一行文字（图片像素坐标） */
    data class Line(val text: String, val x: Float, val y: Float, val h: Float)

    /** OCR 常把表格一行拆成好几块：按纵向位置把同一行的块拼起来（从左到右） */
    fun mergeRows(lines: List<Line>): List<Line> {
        class R(var cy: Float, var h: Float, val items: MutableList<Line>)
        val rows = ArrayList<R>()
        for (l in lines.filter { it.text.isNotBlank() }.sortedBy { it.y + it.h / 2 }) {
            val cy = l.y + l.h / 2
            val r = rows.lastOrNull()
            val tol = max(6f, min(if (l.h > 0) l.h else 20f, r?.h ?: 20f) * 0.55f)
            if (r != null && abs(cy - r.cy) <= tol) { r.items += l; r.cy = (r.cy * (r.items.size - 1) + cy) / r.items.size; r.h = max(r.h, l.h) }
            else rows += R(cy, if (l.h > 0) l.h else 20f, mutableListOf(l))
        }
        return rows.map { r -> val it = r.items.sortedBy { x -> x.x }; Line(it.joinToString(" ") { x -> x.text.trim() }, it.first().x, r.cy - r.h / 2, r.h) }
    }

    /** 拍照识别结果 → 同 recognizeTable 的结构（每行先找学生，再找状态标记；一行多个标记 = 多列） */
    fun recognizeLines(lines: List<Line>, students: List<Student>): Result {
        val m = SeatingLogic.SeatMatcher(students)
        val out = ArrayList<Row>(); val unmatched = ArrayList<String>(); val seen = HashSet<String>(); var width = 0
        for (ln in lines.sortedBy { it.y }) {
            val text = half(ln.text).trim(); if (text.isEmpty()) continue
            val hits = m.all(text)
            if (hits.isEmpty()) { unmatched += text; continue }
            hits.forEachIndexed { k, h ->
                val end = if (k + 1 < hits.size) hits[k + 1].at else text.length
                val cells = scanStatuses(text.substring(min(h.at + h.len, end), end))
                if (!seen.add(h.s.id)) return@forEachIndexed
                width = max(width, cells.size)
                out += Row(h.s.id, cells.toMutableList(), text.substring(h.at, end).trim())
            }
        }
        val w = max(1, width)
        for (r in out) while (r.cells.size < w) r.cells += HwStatus.NONE
        return Result((0 until w).map { Column(it, "第${it + 1}列") }, out, unmatched, students.filter { it.id !in seen }.map { it.id })
    }

    /** 从一段文字里依次找出状态标记 */
    fun scanStatuses(text: String): List<HwStatus> {
        val t = half(text).lowercase()
        val out = ArrayList<HwStatus>()
        var i = 0
        val skip = " \t,，;；:：|()（）[]【】.。、"
        while (i < t.length) {
            if (t[i] in skip || t[i].isWhitespace()) { i++; continue }
            val tok = SCAN.firstOrNull { t.startsWith(it, i) }
            if (tok != null) {
                val k = TOKEN_MAP.getValue(tok)
                if (k != HwStatus.NONE || tok == "/" || tok == "-" || tok == "—") out += k
                i += tok.length; continue
            }
            val w = Regex("^[a-z0-9+\\-]+").find(t.substring(i))?.value
            if (w != null) { TOKEN_MAP[w]?.takeIf { it != HwStatus.NONE }?.let { out += it }; i += w.length; continue }
            i++
        }
        return out
    }

    fun columnStatuses(r: Result, col: Int): Map<String, HwStatus> =
        r.rows.mapNotNull { row -> row.cells.getOrNull(col)?.takeIf { it != HwStatus.NONE }?.let { row.studentId to it } }.toMap()

    // ---------------- 统计 ----------------
    class Tally(val lists: Map<HwStatus, List<Student>>) {
        fun count(s: HwStatus) = lists[s]?.size ?: 0
        val handedIn get() = HANDED.sumOf { count(it) }
        val expected get() = lists.values.sumOf { it.size } - count(HwStatus.LEAVE)
        val rate: Float get() = if (expected <= 0) 1f else handedIn.toFloat() / expected
    }

    fun tally(students: List<Student>, statuses: Map<String, HwStatus>): Tally =
        Tally(HwStatus.entries.associateWith { k -> students.filter { (statuses[it.id] ?: HwStatus.NONE) == k } })

    // ---------------- 读取 .xlsx ----------------
    fun readXlsx(bytes: ByteArray): List<List<String>> {
        val files = HashMap<String, String>()
        runCatching {
            ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
                while (true) {
                    val e = z.nextEntry ?: break
                    val n = e.name
                    if (n == "xl/sharedStrings.xml" || n == "xl/workbook.xml" || n == "xl/_rels/workbook.xml.rels" || Regex("^xl/worksheets/sheet\\d+\\.xml$").matches(n)) files[n] = z.readBytes().toString(Charsets.UTF_8)
                }
            }
        }
        if (files.isEmpty()) error("不是有效的 .xlsx 文件（老版 .xls 请另存为 .xlsx 或 CSV）")
        fun dec(s: String) = s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'")
            .replace(Regex("&#(x?)([0-9a-fA-F]+);")) { m -> String(Character.toChars(m.groupValues[2].toInt(if (m.groupValues[1].isNotEmpty()) 16 else 10))) }.replace("&amp;", "&")
        fun textOf(xml: String) = Regex("<t(?:\\s[^>]*)?>([\\s\\S]*?)</t>").findAll(xml).joinToString("") { dec(it.groupValues[1]) }
        val shared = files["xl/sharedStrings.xml"]?.let { x -> Regex("<si>([\\s\\S]*?)</si>").findAll(x).map { textOf(it.groupValues[1].replace(Regex("<rPh[\\s\\S]*?</rPh>"), "")) }.toList() } ?: emptyList()
        var path = "xl/worksheets/sheet1.xml"
        val wb = files["xl/workbook.xml"]; val rels = files["xl/_rels/workbook.xml.rels"]
        if (wb != null && rels != null) {
            val rid = Regex("<sheet\\b[^>]*\\br:id=\"([^\"]+)\"").find(wb)?.groupValues?.get(1)
            if (rid != null) {
                val rel = Regex("<Relationship\\b[^>]*>").findAll(rels).map { it.value }.firstOrNull { "Id=\"$rid\"" in it }
                val tgt = rel?.let { Regex("Target=\"([^\"]+)\"").find(it)?.groupValues?.get(1) }
                if (tgt != null) path = if (tgt.startsWith("/")) tgt.drop(1) else "xl/" + tgt.removePrefix("./")
            }
        }
        val sheet = files[path] ?: files.entries.firstOrNull { it.key.startsWith("xl/worksheets/") }?.value ?: error("Excel 里没有找到工作表")
        val rows = ArrayList<List<String>>()
        for (rm in Regex("<row\\b[^>]*?(?:/>|>([\\s\\S]*?)</row>)").findAll(sheet)) {
            val row = ArrayList<String>()
            for (cm in Regex("<c\\b([^>]*?)(?:/>|>([\\s\\S]*?)</c>)").findAll(rm.groupValues[1])) {
                val attrs = cm.groupValues[1]; val body = cm.groupValues[2]
                val ref = Regex("\\br=\"([A-Z]+)\\d+\"").find(attrs)?.groupValues?.get(1)
                val type = Regex("\\bt=\"(\\w+)\"").find(attrs)?.groupValues?.get(1)
                val v = Regex("<v>([\\s\\S]*?)</v>").find(body)?.groupValues?.get(1)
                val value = when (type) {
                    "s" -> v?.toIntOrNull()?.let { shared.getOrNull(it) } ?: ""
                    "inlineStr" -> textOf(body)
                    "b" -> if (v == "1") "TRUE" else "FALSE"
                    else -> v?.let { dec(it) } ?: ""
                }
                val ci = ref?.fold(0) { n, ch -> n * 26 + (ch - 'A' + 1) }?.minus(1) ?: row.size
                while (row.size < ci) row += ""
                if (row.size == ci) row += value else row[ci] = value
            }
            while (row.isNotEmpty() && row.last().isEmpty()) row.removeAt(row.lastIndex)
            rows += row
        }
        return rows.filter { r -> r.any { it.isNotBlank() } }
    }

    // ---------------- 草稿 ----------------
    fun newDraft(c: SchoolClass, a: Assignment?, title: String = "", subject: String = "", statuses: Map<String, HwStatus> = emptyMap()): Draft {
        val now = System.currentTimeMillis()
        return if (a != null) Draft(
            classId = c.id, assignmentId = a.id, isNew = false, title = a.title, subject = a.subject, createdAt = a.createdAt, updatedAt = now,
            statuses = a.statuses + statuses, comments = a.comments,
            baseTitle = a.title, baseSubject = a.subject, baseStatuses = a.statuses, baseComments = a.comments,
        ) else Draft(
            classId = c.id, isNew = true, title = title, subject = subject, createdAt = now, updatedAt = now,
            statuses = c.students.filter { it.longLeave }.associate { it.id to HwStatus.LEAVE } + statuses,
        )
    }

    /** 草稿相对创建时改了哪些学生 */
    fun changes(d: Draft): List<String> {
        val ids = LinkedHashSet<String>().apply { addAll(d.statuses.keys); addAll(d.baseStatuses.keys); addAll(d.comments.keys); addAll(d.baseComments.keys) }
        return ids.filter { (d.statuses[it] ?: HwStatus.NONE) != (d.baseStatuses[it] ?: HwStatus.NONE) || (d.comments[it] ?: "") != (d.baseComments[it] ?: "") }
    }

    /** 把草稿写进数据：只覆盖草稿里改过的学生（期间云端/其它设备的其它登记保留） */
    fun applyDraft(data: AppData, d: Draft): AppData {
        val c = data.classes.firstOrNull { it.id == d.classId } ?: error("草稿所属的班级已不存在")
        val ex = c.assignments.firstOrNull { it.id == d.assignmentId }
        val na = if (ex == null) Assignment(id = d.assignmentId, title = d.title.ifBlank { "未命名作业" }, subject = d.subject, createdAt = d.createdAt, statuses = d.statuses, comments = d.comments)
        else {
            val st = ex.statuses.toMutableMap(); val cm = ex.comments.toMutableMap()
            for (id in changes(d)) {
                val v = d.statuses[id] ?: HwStatus.NONE
                if (v == HwStatus.NONE) st.remove(id) else st[id] = v
                val t = d.comments[id].orEmpty()
                if (t.isNotEmpty()) cm[id] = t else cm.remove(id)
            }
            ex.copy(
                statuses = st, comments = cm,
                title = if (d.title != d.baseTitle) d.title.ifBlank { ex.title } else ex.title,
                subject = if (d.subject != d.baseSubject) d.subject else ex.subject,
            )
        }
        return data.copy(classes = data.classes.map { k ->
            if (k.id != c.id) k else k.copy(assignments = if (ex != null) k.assignments.map { if (it.id == na.id) na else it } else k.assignments + na)
        })
    }
}
