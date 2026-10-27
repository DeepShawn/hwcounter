import com.deepshawn.hwcounter.data.Student
import com.deepshawn.hwcounter.recog.VoiceParser

val PY = mapOf('张' to "zhang", '伟' to "wei", '章' to "zhang", '维' to "wei", '李' to "li", '四' to "si", '三' to "san",
    '王' to "wang", '芳' to "fang", '方' to "fang", '刘' to "liu", '洋' to "yang", '杨' to "yang", '陈' to "chen",
    '曦' to "xi", '希' to "xi", '林' to "lin", '静' to "jing", '进' to "jin", '号' to "hao", '和' to "he", '还' to "hai",
    '有' to "you", '一' to "yi", '博' to "bo", '牛' to "niu", '十' to "shi", '二' to "er", '九' to "jiu", '百' to "bai", '零' to "ling",
    '赵' to "zhao", '敏' to "min", '民' to "min", '然' to "ran", '后' to "hou")

fun mainV1() {
    val names = listOf("张伟", "李四", "王芳", "刘洋", "陈曦", "林静", "王一博", "章维", "赵敏")
    val roster = (1..50).map { i -> Student(id = "s$i", no = "$i", name = names.getOrElse(i - 1) { "学生$i" }) }
    val nameOf = roster.associate { it.id to it.name }
    val p = VoiceParser(roster, { c -> listOfNotNull(PY[c]) })
    val cases = listOf(
        "张伟 李四 三十四号 49号", "王方", "刘杨", "陈希", "林进", "3449", "三四 四九", "王一博", "章伟",
        "牛洋", "十二号和二十号", "一百零三", "赵民然后李四还有7号", "张伟张伟", "一百零三号", "三十四四十九", "34 三十四", "二十", "幺零", "十二二十", "二十一号三十号", "一百二十", "四十五十",
    )
    for (c in cases) {
        val r = p.parse(c)
        println("%-22s -> %s  | 剩余:%s".format(c, r.matches.joinToString(", ") { m ->
            m.studentIds.joinToString("/") { nameOf[it]!! } + "(${m.kind.name.lowercase()})"
        }, r.leftover))
    }
}
