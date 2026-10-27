import com.deepshawn.hwcounter.data.Student
import com.deepshawn.hwcounter.recog.Polyphones
import com.deepshawn.hwcounter.recog.VoiceParser

val PY3 = """
张zhang 锦jin 宇yu 朱zhu 嘉jia 怡yi 冉ran 浩hao 这zhe 几ji 样yang 金jin 雨yu 章zhang 真zhen 级ji 鱼yu 主zhu 家jia 以yi
然ran 后hou 李li 子zi 涵han 林lin 静jing 进jin 王wang 芳fang 还hai 有you 同tong 学xue 早zao 就jiu 已yi 走zou 加jia 油you
赵zhao 军jun 一yi 周zhou 杰jie 陈chen 浩hao 宏hong 好hao 话hua 吃chi 胡hu 晨chen 很hen 差cha 号hao 三san 四si
""".trim().split(Regex("\\s+")).associate { it[0] to it.substring(1) }

fun rd3(c: Char): List<String> = (listOfNotNull(PY3[c]) + Polyphones.of(c)).distinct()

fun mainV3() {
    val names = listOf("张锦宇", "李子涵", "林静", "王芳", "冉浩", "周杰", "陈浩宏", "胡晨")
    fun roster(extra: List<String>) = (names + extra).mapIndexed { i, n -> Student(id = "s${i + 1}", no = "${i + 1}", name = n) }
    fun run(title: String, r: List<Student>, cases: List<Pair<String, String>>, initials: Boolean = true) {
        val nameOf = r.associate { it.id to it.name }
        val p = VoiceParser(r, ::rd3, 1f, initials)
        println("== $title")
        var ok = 0
        for ((text, want) in cases) {
            val res = p.parse(text)
            val got = res.matches.joinToString("|") { m -> m.studentIds.joinToString("/") { nameOf[it]!! } }
            if (got == want) ok++
            println("%s %-14s -> %-16s %s".format(if (got == want) "✓" else "✗", text, got.ifEmpty { "(无)" }, res.matches.joinToString(" ") { "[${it.kind.label}]" }))
        }
        println("   $ok/${cases.size}")
    }
    run("首字母 ZJY（班上唯一）", roster(emptyList()), listOf(
        "张锦宇" to "张锦宇",          // 原名
        "章金雨" to "张锦宇",          // 同音
        "这几样" to "张锦宇",          // 读音不像，但首字母 ZJY
        "主家以" to "张锦宇",
        "ZJY" to "张锦宇", "zjy" to "张锦宇", "Z J Y" to "张锦宇",
        "早就已" to "张锦宇",          // zao jiu yi = ZJY
        "李子涵 这几样 林静" to "李子涵|张锦宇|林静",
        "然后" to "",                 // 口头禅不参与（冉浩 = RH）
        "还有同学" to "",
        "陈浩宏" to "陈浩宏", "吃好话" to "陈浩宏",
        "很差" to "胡晨",             // HC
    ))
    run("首字母撞车：朱嘉怡也是 ZJY", roster(listOf("朱嘉怡")), listOf(
        "章金雨" to "张锦宇",          // 完整拼音更像张锦宇
        "主家以" to "朱嘉怡",          // 更像朱嘉怡
        "这几样" to "张锦宇/朱嘉怡",    // 都不像 → 让老师点选
        "ZJY" to "张锦宇/朱嘉怡",
    ))
    run("关闭首字母匹配", roster(emptyList()), listOf("这几样" to "", "ZJY" to "", "章金雨" to "张锦宇"), initials = false)
}
