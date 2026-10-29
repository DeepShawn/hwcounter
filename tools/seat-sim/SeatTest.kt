// 座位表算法（SeatingLogic）测试，用例与网页版 tools/web-tests/seating.test.mjs 相同
import com.deepshawn.hwcounter.data.Seating
import com.deepshawn.hwcounter.data.Student
import com.deepshawn.hwcounter.recog.SeatingLogic
import com.deepshawn.hwcounter.recog.SeatingLogic.Piece

fun main() {
    val names = listOf("张锦宇", "李子涵", "林静", "王芳", "冉浩", "周杰", "陈浩宏", "胡晨", "赵敏", "孙悦", "吴磊", "郑怡")
    val st = names.mapIndexed { i, n -> Student("s${i + 1}", (i + 1).toString().padStart(2, '0'), n) }
    val nameOf = st.associate { it.id to it.name }
    fun show(s: Seating) = SeatingLogic.toGrid(s, st).joinToString(" / ") { r -> r.joinToString(" ") { it?.let { id -> nameOf[id] } ?: "空" } }
    var pass = 0; var total = 0
    fun check(ok: Boolean, msg: String) { total++; if (ok) pass++; println("${if (ok) "✓" else "✗"} $msg") }

    var r = SeatingLogic.parseText("讲台\n张锦宇\t李子涵\t\t林静\n王芳\t冉浩\t周杰\t空\n陈浩宏\t08\t\t", st)
    check(show(r.seating) == "张锦宇 李子涵 空 林静 / 王芳 冉浩 周杰 空 / 陈浩宏 胡晨 空 空" && r.seating.frontAtTop, "Tab 分隔 + 空位 + 学号：${show(r.seating)}")
    check(r.unseated.size == 4, "未入座 ${r.unseated.size} 人")
    r = SeatingLogic.parseText("张锦宇 李子涵 林静\n王芳 冉浩 周杰\n   讲  台   ", st)
    check(!r.seating.frontAtTop && show(r.seating) == "张锦宇 李子涵 林静 / 王芳 冉浩 周杰", "讲台在下：${show(r.seating)}")
    r = SeatingLogic.parseText("张锦宇,李雷,林静", st)
    check(r.unmatched == listOf("李雷") && show(r.seating) == "张锦宇 空 林静", "未匹配：${r.unmatched}")

    val boxes = ArrayList<Piece>()
    val layout = listOf(listOf("张锦宇", "李子涵", null, "林静"), listOf("王芳", "冉浩", "周杰", "陈浩宏"), listOf("胡晨", null, "赵敏", "孙悦"))
    layout.forEachIndexed { i, row -> row.forEachIndexed { j, n -> if (n != null) boxes += Piece(n, 100f + j * 220 + (if (j >= 2) 60 else 0), 100f + i * 150 + j * 6, 90f, 40f) } }
    boxes += Piece("讲 台", 400f, 620f, 120f, 50f)
    boxes += Piece("第一组", 100f, 40f, 80f, 30f)
    r = SeatingLogic.fromBoxes(boxes, st)
    check(show(r.seating) == "张锦宇 李子涵 空 林静 / 王芳 冉浩 周杰 陈浩宏 / 胡晨 空 赵敏 孙悦" && !r.seating.frontAtTop, "图片（逐人框 + 倾斜 + 讲台在下）：${show(r.seating)}")
    r = SeatingLogic.fromBoxes(listOf(
        Piece("讲台", 300f, 10f, 100f, 40f),
        Piece("张锦宇  李子涵  林静  王芳", 50f, 100f, 600f, 40f),
        Piece("冉浩    周杰    陈浩宏  胡晨", 50f, 200f, 600f, 40f),
    ), st)
    check(show(r.seating) == "张锦宇 李子涵 林静 王芳 / 冉浩 周杰 陈浩宏 胡晨" && r.seating.frontAtTop, "图片（整排一行）：${show(r.seating)}")
    r = SeatingLogic.fromBoxes(listOf(
        Piece("高一(3)班座位表", 764f, 65f, 292f, 45f),
        Piece("张锦宇李子涵", 284f, 194f, 376f, 63f, listOf(295f, 330f, 365f, 545f, 590f, 635f)),
        Piece("林静", 1173f, 216f, 84f, 42f), Piece("王芬", 1425f, 222f, 84f, 42f),
        Piece("冉洗", 301f, 332f, 84f, 42f), Piece("周杰", 555f, 338f, 82f, 42f),
        Piece("陈浩宏", 898f, 345f, 125f, 43f), Piece("一", 682f, 411f, 13f, 1f),
        Piece("胡晨", 1423f, 355f, 82f, 42f), Piece("赵敏", 299f, 466f, 83f, 42f),
        Piece("ES", 917f, 481f, 82f, 40f), Piece("郑怡", 1168f, 485f, 84f, 42f),
        Piece("孙悦", 551f, 473f, 84f, 40f), Piece("讲台", 846f, 658f, 99f, 46f),
    ), st)
    check(show(r.seating) == "张锦宇 李子涵 空 林静 王芳 / 冉浩 周杰 陈浩宏 空 胡晨 / 赵敏 孙悦 空 郑怡 空" && !r.seating.frontAtTop, "真实 OCR 输出：${show(r.seating)}")

    var s = SeatingLogic.autoArrange(st, 5)
    check(s.rows == 3 && show(s).startsWith("张锦宇 李子涵 林静 王芳 冉浩 / 周杰"), "按学号自动排列：${show(s)}")
    val rot = SeatingLogic.rotate(s)
    check(!rot.frontAtTop && show(rot).startsWith("空 空 空 郑怡 吴磊"), "讲台换边：${show(rot)}")
    s = SeatingLogic.swap(s, 0, 14)
    check(show(s).startsWith("空 李子涵") && show(s).endsWith("张锦宇"), "交换座位（含空位）")
    s = SeatingLogic.resize(s, 2, 4)
    check(s.rows == 2 && s.cols == 4 && s.seats.size == 7, "缩小到 2×4 后入座 ${s.seats.size} 人")
    s = SeatingLogic.place(s, "s12", 0)
    check(show(s).startsWith("郑怡 李子涵"), "未入座同学放进空位")
    println("\n通过 $pass/$total")
    if (pass != total) kotlin.system.exitProcess(1)
}
