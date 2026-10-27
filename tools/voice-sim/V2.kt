import com.deepshawn.hwcounter.data.Student
import com.deepshawn.hwcounter.recog.Polyphones
import com.deepshawn.hwcounter.recog.VoiceParser

val PY2 = """
张zhang 伟wei 章zhang 维wei 李li 四si 三san 王wang 芳fang 方fang 刘liu 洋yang 杨yang 陈chen 曦xi 希xi 林lin 静jing 进jin
号hao 和he 还hai 有you 一yi 博bo 牛niu 十shi 二er 九jiu 百bai 零ling 赵zhao 敏min 民min 然ran 后hou 子zi 涵han 航hang 紫zi
寒han 欧ou 阳yang 娜na 曾ceng 增zeng 小xiao 贤xian 先xian 孙sun 悦yue 月yue 乐le 周zhou 杰jie 节jie 吴wu 磊lei 雷lei 宇yu
轩xuan 宣xuan 黄huang 皇huang 浩hao 好hao 是shi 斯si 留liu 酒jiu 久jiu 五wu 七qi 期qi 八ba 吧ba 六liu 舞wu 胡hu 福fu 晨chen
程cheng 成cheng 郑zheng 振zhen 怡yi 依yi 徐xu 许xu 婷ting 庭ting 亭ting 天tian 田tian 佑you 又you 嘉jia 佳jia 懿yi 义yi
马ma 妈ma 俊jun 军jun 宋song 送song 雨yu 鱼yu 萱xuan 罗luo 落luo 诺nuo 单dan 善shan 山shan 解jie 谢xie 楠nan 兰lan 男nan
刚gang 钢gang 高gao 明ming 名ming 叶ye 也ye 晓xiao 东dong 冬dong 梓zi 自zi 萌meng 梦meng 朴pu 飘piao 彤tong 同tong 书shu 苏su
""".trim().split(Regex("\\s+")).associate { it[0] to it.substring(1) }

fun rd(c: Char): List<String> = (listOfNotNull(PY2[c]) + Polyphones.of(c)).distinct()

fun main() {
    val names = listOf("张伟", "李四", "王芳", "刘洋", "陈曦", "林静", "王一博", "章维", "赵敏", "李子涵",
        "欧阳娜娜", "曾小贤", "孙悦", "周杰", "吴磊", "黄宇轩", "胡晨", "郑怡", "徐婷", "马俊",
        "宋雨萱", "罗诺", "单山", "谢楠", "高明", "叶晓东", "朴彤")
    val roster = (1..50).map { i -> Student(id = "s$i", no = "$i", name = names.getOrElse(i - 1) { "号$i" }) }
    val nameOf = roster.associate { it.id to it.name }
    val p = VoiceParser(roster, ::rd)
    // 期望（用“|”分隔）
    val cases = listOf(
        "林进" to "林静", "李子航" to "李子涵", "李紫寒" to "李子涵", "欧阳娜" to "欧阳娜娜", "增小先" to "曾小贤",
        "孙月" to "孙悦", "周节" to "周杰", "吴雷" to "吴磊", "皇宇宣" to "黄宇轩", "福晨" to "胡晨", "胡程" to "胡晨",
        "振依" to "郑怡", "许庭" to "徐婷", "妈军" to "马俊", "送鱼萱" to "宋雨萱", "落诺" to "罗诺", "善山" to "单山",
        "解楠" to "谢楠", "高名" to "高明", "也晓冬" to "叶晓东", "飘同" to "朴彤", "子涵" to "李子涵",
        "是十四号" to "#44", "斯十九号" to "#49", "留号" to "#6", "酒号" to "#9", "四是号" to "#40", "林五号" to "#05", "三是二号" to "#32",
        // 不应误配：读音差太远
        "王伟" to "", "张芳" to "", "黄明" to "", "天天" to "", "也行" to "",
        // 连说多个
        "林进 李子航 是十九号 欧阳娜" to "林静|李子涵|#49|欧阳娜娜",
        "张伟 章伟" to "张伟|张伟/章维",
    )
    var ok = 0
    for ((text, want) in cases) {
        val r = p.parse(text)
        val got = r.matches.joinToString("|") { m ->
            if (m.kind == VoiceParser.Kind.NUMBER_SOUND || m.kind == VoiceParser.Kind.NUMBER) "#" + m.heard.substringAfter("→")
            else m.studentIds.joinToString("/") { nameOf[it]!! }
        }
        val nums = r.matches.filter { it.kind == VoiceParser.Kind.NUMBER_SOUND || it.kind == VoiceParser.Kind.NUMBER }.map { it.heard }
        val pass = got == want
        if (pass) ok++
        println("%s %-22s -> %-22s %s".format(if (pass) "✓" else "✗", text, got.ifEmpty { "(无)" },
            r.matches.joinToString(" ") { "[${it.kind.label} ${"%.2f".format(it.cost)}]" } + if (nums.isNotEmpty()) " 数字:$nums" else ""))
    }
    println("names: $ok/${cases.size}")
    println("--- 回归（第一版用例）---")
    mainV1()
    println("--- 首字母 ---")
    mainV3()
}
