// 统计表识别 / 草稿（StatTable）测试，用例与网页版 tools/web-tests/stats.test.mjs 相同
import com.deepshawn.hwcounter.data.AppData
import com.deepshawn.hwcounter.data.Assignment
import com.deepshawn.hwcounter.data.HwStatus
import com.deepshawn.hwcounter.data.HwStatus.*
import com.deepshawn.hwcounter.data.SchoolClass
import com.deepshawn.hwcounter.data.Student
import com.deepshawn.hwcounter.recog.StatTable
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

fun main() {
    var pass = 0; var total = 0
    fun check(ok: Boolean, msg: String) { total++; if (ok) pass++; println("${if (ok) "✓" else "✗"} $msg") }
    val names = listOf("张锦宇", "李子涵", "林静", "王芳", "冉浩", "周杰")
    val st = names.mapIndexed { i, n -> Student("s${i + 1}", (i + 1).toString().padStart(2, '0'), n) }
    val nm = st.associate { it.id to it.name }
    fun show(r: StatTable.Result, col: Int = 0) = r.rows.joinToString(" ") { "${nm[it.studentId]}:${it.cells[col]}" }

    val marks = mapOf("✓" to SUBMITTED, "√" to SUBMITTED, "×" to MISSING, "X" to MISSING, "优" to EXCELLENT, "A" to EXCELLENT, "迟交" to LATE, "补" to LATE, "请假" to LEAVE, "病假" to LEAVE, "" to NONE, "-" to NONE, "已交" to SUBMITTED, "未交" to MISSING, "1" to SUBMITTED, "0" to MISSING, "MISSING" to MISSING, "交(优)" to EXCELLENT, "ｘ" to MISSING)
    val bad = marks.filter { (k, v) -> StatTable.parseStatus(k) != v }
    check(bad.isEmpty(), "标记识别 ${marks.size} 种 ${bad.keys}")
    check(StatTable.parseStatus("85") == null && StatTable.parseStatus("张三") == null, "分数、姓名不当成标记")

    var r = StatTable.recognizeTable(StatTable.splitTable("学号\t姓名\t10月30日 数学\t10月31日 语文\n01\t张锦宇\t✓\t×\n02\t李子涵\t×\t✓\n03\t林静\t优\t\n04\t王芳\t假\t假\n05\t冉浩\t迟\t✓\n李雷\t\t✓"), st)
    check(r.columns.map { it.header } == listOf("10月30日 数学", "10月31日 语文"), "状态列：${r.columns.map { it.header }}")
    check(show(r) == "张锦宇:SUBMITTED 李子涵:MISSING 林静:EXCELLENT 王芳:LEAVE 冉浩:LATE", "第 1 列：${show(r)}")
    check(show(r, 1) == "张锦宇:MISSING 李子涵:SUBMITTED 林静:NONE 王芳:LEAVE 冉浩:SUBMITTED", "第 2 列：${show(r, 1)}")
    check(r.unmatched.size == 1 && "李雷" in r.unmatched[0] && r.missingStudents == listOf("s6"), "名单外 ${r.unmatched}，表里没有 ${r.missingStudents}")

    r = StatTable.recognizeTable(StatTable.splitTable("张锦宇,1\n李子涵,1\n林静,0\n王芳,1"), st)
    check(r.columns.size == 1 && show(r) == "张锦宇:SUBMITTED 李子涵:SUBMITTED 林静:MISSING 王芳:SUBMITTED", "全是 1/0 的列：${show(r)}")
    r = StatTable.recognizeTable(StatTable.splitTable("1 已交\n2 未交\n3 请假\n6 迟交"), st)
    check(show(r) == "张锦宇:SUBMITTED 李子涵:MISSING 林静:LEAVE 周杰:LATE", "学号（不带前导 0）：${show(r)}")
    r = StatTable.recognizeTable(StatTable.splitTable("\"学号\",\"姓名\",\"状态\"\n\"01\",\"张锦宇\",\"已交\"\n\"02\",\"李子涵\",\"未交\""), st)
    check(show(r) == "张锦宇:SUBMITTED 李子涵:MISSING", "带引号 CSV：${show(r)}")

    val lines = listOf(StatTable.Line("作业登记表 高一(3)班", 0f, 10f, 0f), StatTable.Line("01 张锦宇 √", 0f, 50f, 0f), StatTable.Line("02 李子涵 X", 0f, 90f, 0f), StatTable.Line("03 林静 优   04 王芳 假", 0f, 130f, 0f), StatTable.Line("05冉浩迟交", 0f, 170f, 0f))
    r = StatTable.recognizeLines(lines, st)
    check(show(r) == "张锦宇:SUBMITTED 李子涵:MISSING 林静:EXCELLENT 王芳:LEAVE 冉浩:LATE" && r.unmatched.size == 1, "拍照识别：${show(r)}")
    val frag = StatTable.mergeRows(listOf(StatTable.Line("√", 300f, 52f, 20f), StatTable.Line("张锦宇", 60f, 50f, 24f), StatTable.Line("×", 400f, 49f, 20f), StatTable.Line("李子涵", 60f, 92f, 24f), StatTable.Line("迟", 300f, 95f, 20f)))
    check(frag.joinToString(" / ") { it.text } == "张锦宇 √ × / 李子涵 迟", "碎块拼行：${frag.joinToString(" / ") { it.text }}")
    r = StatTable.recognizeLines(frag, st)
    check(r.columns.size == 2 && show(r, 1) == "张锦宇:MISSING 李子涵:NONE", "碎块 → 两列：${show(r, 1)}")
    check(StatTable.scanStatuses("√ × 优 迟") == listOf(SUBMITTED, MISSING, EXCELLENT, LATE), "一行多个标记")

    val sts = StatTable.columnStatuses(StatTable.recognizeTable(StatTable.splitTable("张锦宇\t✓\n李子涵\t×\n林静\t优\n王芳\t假"), st), 0)
    val t = StatTable.tally(st, sts)
    check(t.handedIn == 2 && t.expected == 5 && t.count(NONE) == 2 && t.count(MISSING) == 1, "统计：已交 ${t.handedIn}/${t.expected}")

    // xlsx：压缩 + 共享字符串 + 工作表重定向
    val bos = ByteArrayOutputStream()
    ZipOutputStream(bos).use { z ->
        fun put(n: String, s: String) { z.putNextEntry(ZipEntry(n)); z.write(s.toByteArray()); z.closeEntry() }
        put("xl/workbook.xml", "<workbook xmlns:r=\"r\"><sheets><sheet name=\"登记\" sheetId=\"1\" r:id=\"rId7\"/></sheets></workbook>")
        put("xl/_rels/workbook.xml.rels", "<Relationships><Relationship Id=\"rId7\" Type=\"ws\" Target=\"worksheets/sheet3.xml\"/></Relationships>")
        put("xl/sharedStrings.xml", "<sst><si><t>姓名</t></si><si><r><t>张</t></r><r><t xml:space=\"preserve\">锦宇</t></r></si><si><t>李子涵</t></si><si><t>作业</t></si><si><t>√</t></si><si><t>未交 &amp; 补</t></si></sst>")
        put("xl/worksheets/sheet3.xml", "<worksheet><sheetData><row r=\"1\"><c r=\"A1\" t=\"s\"><v>0</v></c><c r=\"C1\" t=\"s\"><v>3</v></c></row><row r=\"2\"><c r=\"A2\" t=\"s\"><v>1</v></c><c r=\"C2\" t=\"s\"><v>4</v></c></row><row r=\"3\"><c r=\"A3\" t=\"s\"><v>2</v></c><c r=\"C3\" t=\"s\"><v>5</v></c></row><row r=\"4\"/><row r=\"5\"><c r=\"A5\" t=\"inlineStr\"><is><t>林静</t></is></c><c r=\"C5\"/></row></sheetData></worksheet>")
    }
    val xr = StatTable.readXlsx(bos.toByteArray())
    check(xr == listOf(listOf("姓名", "", "作业"), listOf("张锦宇", "", "√"), listOf("李子涵", "", "未交 & 补"), listOf("林静")), "读取 xlsx：$xr")
    check(runCatching { StatTable.readXlsx(byteArrayOf(1, 2, 3)) }.exceptionOrNull()?.message?.contains("xlsx") == true, "坏文件报错")

    // 草稿
    val c = SchoolClass(id = "c1", name = "班", students = st.map { if (it.id == "s6") it.copy(longLeave = true) else it }, assignments = listOf(Assignment(id = "a1", title = "练习", subject = "数学", createdAt = 5, statuses = mapOf("s1" to SUBMITTED, "s2" to MISSING))))
    var d1 = StatTable.newDraft(c, c.assignments[0])
    d1 = d1.copy(statuses = d1.statuses + mapOf("s2" to LATE, "s3" to SUBMITTED) - "s1", comments = mapOf("s3" to "字迹工整"))
    check(StatTable.changes(d1).sorted() == listOf("s1", "s2", "s3"), "草稿改动：${StatTable.changes(d1)}")
    val data = AppData(classes = listOf(c.copy(assignments = listOf(c.assignments[0].copy(statuses = c.assignments[0].statuses + ("s4" to EXCELLENT))))))
    val out = StatTable.applyDraft(data, d1).classes[0].assignments[0]
    check(out.statuses == mapOf("s2" to LATE, "s4" to EXCELLENT, "s3" to SUBMITTED) && out.comments["s3"] == "字迹工整", "上传草稿：${out.statuses}")
    val d2 = StatTable.newDraft(c, null, "新作业", "语文", mapOf("s1" to SUBMITTED))
    val as2 = StatTable.applyDraft(data, d2).classes[0].assignments
    check(as2.size == 2 && as2[1].title == "新作业" && as2[1].statuses["s6"] == LEAVE && as2[1].statuses["s1"] == SUBMITTED, "新作业草稿")
    check(runCatching { StatTable.applyDraft(AppData(), d2) }.exceptionOrNull()?.message?.contains("班级") == true, "班级被删 → 报错")

    println("\n$pass/$total 通过")
    if (pass != total) kotlin.system.exitProcess(1)
}
