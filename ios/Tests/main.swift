// iPad 版语音解析测试（与网页版 tools/web-tests/voice.test.mjs 同样的用例）
// 在 macOS 上运行：swiftc -o /tmp/vt HwCounter/Model/Models.swift HwCounter/Recog/Text.swift HwCounter/Recog/VoiceParser.swift HwCounter/Recog/Matcher.swift HwCounter/Recog/Roster.swift Tests/main.swift && /tmp/vt
import Foundation

var pass = 0, total = 0
func suite(_ title: String, _ names: [String], _ cases: [(String, String)], initials: Bool = true) {
    var roster = names.enumerated().map { Student(id: "s\($0.offset + 1)", no: "\($0.offset + 1)", name: $0.element) }
    if names.count < 50 { for i in (names.count + 1)...50 { roster.append(Student(id: "s\(i)", no: "\(i)", name: "号\(i)")) } }
    let nameOf = Dictionary(uniqueKeysWithValues: roster.map { ($0.id, $0.name) })
    let p = VoiceParser(roster: roster, tolerance: 1, initials: initials)
    print("== \(title)")
    for (text, want) in cases {
        let r = p.parse(text)
        let got = r.matches.map { m in
            m.kind == .number || m.kind == .numberSound ? "#" + (m.heard.components(separatedBy: "→").last ?? "") : m.ids.map { nameOf[$0] ?? "?" }.joined(separator: "/")
        }.joined(separator: "|")
        total += 1
        if got == want { pass += 1 }
        print("\(got == want ? "✓" : "✗") \(text) -> \(got.isEmpty ? "(无)" : got) \(r.matches.map { "[\($0.kind.rawValue)]" }.joined(separator: " "))\(got == want ? "" : "   期望: \(want.isEmpty ? "(无)" : want)")")
    }
}

suite("模糊拼音", ["张伟", "李四", "王芳", "刘洋", "陈曦", "林静", "王一博", "章维", "赵敏", "李子涵", "欧阳娜娜", "曾小贤", "孙悦", "周杰", "吴磊", "黄宇轩", "胡晨", "郑怡", "徐婷", "马俊", "宋雨萱", "罗诺", "单山", "谢楠", "高明", "叶晓东", "朴彤"], [
    ("林进", "林静"),
    ("李子航", "李子涵"),
    ("李紫寒", "李子涵"),
    ("欧阳娜", "欧阳娜娜"),
    ("增小先", "曾小贤"),
    ("孙月", "孙悦"),
    ("周节", "周杰"),
    ("吴雷", "吴磊"),
    ("皇宇宣", "黄宇轩"),
    ("福晨", "胡晨"),
    ("胡程", "胡晨"),
    ("振依", "郑怡"),
    ("许庭", "徐婷"),
    ("妈军", "马俊"),
    ("送鱼萱", "宋雨萱"),
    ("落诺", "罗诺"),
    ("善山", "单山"),
    ("解楠", "谢楠"),
    ("高名", "高明"),
    ("也晓冬", "叶晓东"),
    ("飘同", "朴彤"),
    ("子涵", "李子涵"),
    ("是十四号", "#44"),
    ("斯十九号", "#49"),
    ("留号", "#6"),
    ("酒号", "#9"),
    ("四是号", "#40"),
    ("三是二号", "#32"),
    ("王伟", ""),
    ("张芳", ""),
    ("黄明", ""),
    ("天天", ""),
    ("林进 李子航 是十九号 欧阳娜", "林静|李子涵|#49|欧阳娜娜"),
    ("张伟 章伟", "张伟|张伟/章维"),
    ("张伟 李四 三十四号 49号", "张伟|李四|#34|#49"),
    ("3449", "#34|#49"),
    ("三十四四十九", "#34|#49"),
    ("一百零三", ""),
    ("十二号和二十号", "#12|#20"),
], initials: false)

suite("首字母 ZJY", ["张锦宇", "李子涵", "林静", "王芳", "冉浩", "周杰", "陈浩宏", "胡晨"], [
    ("张锦宇", "张锦宇"),
    ("章金雨", "张锦宇"),
    ("这几样", "张锦宇"),
    ("主家以", "张锦宇"),
    ("ZJY", "张锦宇"),
    ("z j y", "张锦宇"),
    ("李子涵 这几样 林静", "李子涵|张锦宇|林静"),
    ("然后", ""),
    ("还有同学", ""),
    ("陈浩宏", "陈浩宏"),
    ("一百零三", ""),
])

suite("首字母撞车", ["张锦宇", "李子涵", "林静", "王芳", "冉浩", "周杰", "陈浩宏", "胡晨", "朱嘉怡"], [
    ("章金雨", "张锦宇"),
    ("主家以", "朱嘉怡"),
    ("这几样", "张锦宇/朱嘉怡"),
    ("ZJY", "张锦宇/朱嘉怡"),
])

suite("念字母（拼音首字母）", ["张锦宇", "李子涵", "林静", "王芳", "冉浩", "周杰", "陈浩宏", "胡晨"], [
    ("ZJY", "张锦宇"),
    ("zjy", "张锦宇"),
    ("Z J Y", "张锦宇"),
    ("Z、J、Y", "张锦宇"),
    ("Z.J.Y.", "张锦宇"),
    ("ZJY LZH", "张锦宇|李子涵"),
    ("ZJYLZH", "张锦宇|李子涵"),
    ("ZJY，LJ，WF", "张锦宇|林静|王芳"),
    ("Z J Y L Z H", "张锦宇|李子涵"),
    ("贼杰歪", "张锦宇"),
    ("贼 杰 歪", "张锦宇"),
    ("艾勒兹艾尺", "李子涵"),
    ("达不溜艾弗", "王芳"),
    ("ZJY 然后 LJ", "张锦宇|林静"),
    ("XQZJY", "张锦宇"),
    ("CHH", "陈浩宏"),
    ("CHHHC", "陈浩宏|胡晨"),
    ("我爱你", ""),
    ("提交作业", ""),
    ("比较", ""),
    ("开始", ""),
])

// 名单解析 & 学号匹配
let (cn, list) = try! RosterParser.parse("{\"className\":\"三年二班\",\"students\":[{\"学号\":\"01\",\"姓名\":\"张三\"},{\"no\":2,\"name\":\"李四\"}]}")
total += 1; if cn == "三年二班" && list.count == 2 && list[1].no == "2" && list[1].name == "李四" { pass += 1; print("✓ 名单 JSON") } else { print("✗ 名单 JSON \(String(describing: cn)) \(list)") }
let mroster = (1...45).map { Student(id: "m\($0)", no: String(format: "2024%04d", $0), name: "学生\($0)") }
let mo = Matcher(roster: mroster).match(["学号:2024 0O12", "20240O45班", "学生7"])
total += 1; if mo.hits.map(\.studentId) == ["m12", "m45", "m7"] { pass += 1; print("✓ 学号匹配") } else { print("✗ 学号匹配 \(mo.hits.map(\.studentId))") }
print("\n通过 \(pass)/\(total)")
exit(pass == total ? 0 : 1)
