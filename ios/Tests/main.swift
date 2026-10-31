// iPad 版语音解析测试（与网页版 tools/web-tests/voice.test.mjs 同样的用例）
// 在 macOS 上运行：swiftc -o /tmp/vt HwCounter/Model/Models.swift HwCounter/Recog/Text.swift HwCounter/Recog/VoiceParser.swift HwCounter/Recog/Matcher.swift HwCounter/Recog/Roster.swift HwCounter/Recog/SeatingLogic.swift HwCounter/Recog/StatTable.swift HwCounter/Recog/Xlsx.swift Tests/main.swift && /tmp/vt
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

// 座位表（与网页版 seating.test.mjs 相同用例）
do {
    let names = ["张锦宇", "李子涵", "林静", "王芳", "冉浩", "周杰", "陈浩宏", "胡晨", "赵敏", "孙悦", "吴磊", "郑怡"]
    let st = names.enumerated().map { Student(id: "s\($0.offset + 1)", no: String(format: "%02d", $0.offset + 1), name: $0.element) }
    let nameOf = Dictionary(uniqueKeysWithValues: st.map { ($0.id, $0.name) })
    func show(_ s: Seating) -> String { SeatingLogic.toGrid(s, st).map { $0.map { $0.flatMap { nameOf[$0] } ?? "空" }.joined(separator: " ") }.joined(separator: " / ") }
    func check(_ ok: Bool, _ msg: String) { total += 1; if ok { pass += 1 }; print("\(ok ? "✓" : "✗") \(msg)") }
    var r = try! SeatingLogic.parseText("讲台\n张锦宇\t李子涵\t\t林静\n王芳\t冉浩\t周杰\t空\n陈浩宏\t08\t\t", st)
    check(show(r.seating) == "张锦宇 李子涵 空 林静 / 王芳 冉浩 周杰 空 / 陈浩宏 胡晨 空 空" && r.seating.frontAtTop, "座位表 Tab 分隔：\(show(r.seating))")
    r = try! SeatingLogic.parseText("张锦宇 李子涵 林静\n王芳 冉浩 周杰\n   讲  台   ", st)
    check(!r.seating.frontAtTop && show(r.seating) == "张锦宇 李子涵 林静 / 王芳 冉浩 周杰", "讲台在下：\(show(r.seating))")
    r = try! SeatingLogic.parseText("{\"frontAtTop\":false,\"seats\":[[\"01\",\"02\"],[null,\"王芳\"]]}", st)
    check(show(r.seating) == "张锦宇 李子涵 / 空 王芳" && !r.seating.frontAtTop, "JSON：\(show(r.seating))")
    typealias P = SeatingLogic.Piece
    r = SeatingLogic.fromBoxes([
        P(text: "高一(3)班座位表", x: 764, y: 65, w: 292, h: 45),
        P(text: "张锦宇李子涵", x: 284, y: 194, w: 376, h: 63, charX: [295, 330, 365, 545, 590, 635]),
        P(text: "林静", x: 1173, y: 216, w: 84, h: 42), P(text: "王芬", x: 1425, y: 222, w: 84, h: 42),
        P(text: "冉洗", x: 301, y: 332, w: 84, h: 42), P(text: "周杰", x: 555, y: 338, w: 82, h: 42),
        P(text: "陈浩宏", x: 898, y: 345, w: 125, h: 43), P(text: "一", x: 682, y: 411, w: 13, h: 1),
        P(text: "胡晨", x: 1423, y: 355, w: 82, h: 42), P(text: "赵敏", x: 299, y: 466, w: 83, h: 42),
        P(text: "ES", x: 917, y: 481, w: 82, h: 40), P(text: "郑怡", x: 1168, y: 485, w: 84, h: 42),
        P(text: "孙悦", x: 551, y: 473, w: 84, h: 40), P(text: "讲台", x: 846, y: 658, w: 99, h: 46),
    ], st)
    check(show(r.seating) == "张锦宇 李子涵 空 林静 王芳 / 冉浩 周杰 陈浩宏 空 胡晨 / 赵敏 孙悦 空 郑怡 空" && !r.seating.frontAtTop, "照片识别结果排座：\(show(r.seating))")
    var s = SeatingLogic.autoArrange(st, cols: 5)
    check(s.rows == 3 && show(s).hasPrefix("张锦宇 李子涵 林静 王芳 冉浩 / 周杰"), "按学号自动排列")
    check(show(SeatingLogic.rotate(s)).hasPrefix("空 空 空 郑怡 吴磊"), "讲台换边")
    s = SeatingLogic.swap(s, 0, 14)
    check(show(s).hasPrefix("空 李子涵") && show(s).hasSuffix("张锦宇"), "交换座位")
    s = SeatingLogic.resize(s, rows: 2, cols: 4)
    check(s.seats.count == 7, "缩小到 2×4")
}
// ---------- 统计表识别 / 统计名单 / 草稿（与 tools/web-tests/stats.test.mjs 同样的用例） ----------
do {
    print("== 统计表识别")
    func check(_ ok: Bool, _ msg: String) { total += 1; if ok { pass += 1 }; print("\(ok ? "✓" : "✗") \(msg)") }
    let names = ["张锦宇", "李子涵", "林静", "王芳", "冉浩", "周杰"]
    let st = names.enumerated().map { Student(id: "s\($0.offset + 1)", no: String(format: "%02d", $0.offset + 1), name: $0.element) }
    let nm = Dictionary(uniqueKeysWithValues: st.map { ($0.id, $0.name) })
    func show(_ r: StatTable.Result, _ col: Int = 0) -> String { r.rows.map { "\(nm[$0.studentId]!):\($0.cells[col].rawValue)" }.joined(separator: " ") }
    let marks: [String: HwStatus] = ["✓": .SUBMITTED, "√": .SUBMITTED, "×": .MISSING, "X": .MISSING, "优": .EXCELLENT, "A": .EXCELLENT, "迟交": .LATE, "补": .LATE, "请假": .LEAVE, "病假": .LEAVE, "": .NONE, "-": .NONE, "已交": .SUBMITTED, "未交": .MISSING, "1": .SUBMITTED, "0": .MISSING, "MISSING": .MISSING, "交(优)": .EXCELLENT, "ｘ": .MISSING]
    let bad = marks.filter { StatTable.parseStatus($0.key) != $0.value }.map(\.key)
    check(bad.isEmpty, "标记识别 \(marks.count) 种 \(bad)")
    check(StatTable.parseStatus("85") == nil && StatTable.parseStatus("张三") == nil, "分数、姓名不当成标记")
    var r = StatTable.recognizeTable(StatTable.splitTable("学号\t姓名\t10月30日 数学\t10月31日 语文\n01\t张锦宇\t✓\t×\n02\t李子涵\t×\t✓\n03\t林静\t优\t\n04\t王芳\t假\t假\n05\t冉浩\t迟\t✓\n李雷\t\t✓"), st)
    check(r.columns.map(\.header) == ["10月30日 数学", "10月31日 语文"], "状态列：\(r.columns.map(\.header))")
    check(show(r) == "张锦宇:SUBMITTED 李子涵:MISSING 林静:EXCELLENT 王芳:LEAVE 冉浩:LATE", "第 1 列：\(show(r))")
    check(show(r, 1) == "张锦宇:MISSING 李子涵:SUBMITTED 林静:NONE 王芳:LEAVE 冉浩:SUBMITTED", "第 2 列：\(show(r, 1))")
    check(r.unmatched.count == 1 && r.missingStudents == ["s6"], "名单外 \(r.unmatched)，表里没有 \(r.missingStudents)")
    r = StatTable.recognizeTable(StatTable.splitTable("张锦宇,1\n李子涵,1\n林静,0\n王芳,1"), st)
    check(r.columns.count == 1 && show(r) == "张锦宇:SUBMITTED 李子涵:SUBMITTED 林静:MISSING 王芳:SUBMITTED", "全是 1/0 的列：\(show(r))")
    r = StatTable.recognizeTable(StatTable.splitTable("1 已交\n2 未交\n3 请假\n6 迟交"), st)
    check(show(r) == "张锦宇:SUBMITTED 李子涵:MISSING 林静:LEAVE 周杰:LATE", "学号（不带前导 0）：\(show(r))")
    r = StatTable.recognizeTable(StatTable.splitTable("\"学号\",\"姓名\",\"状态\"\n\"01\",\"张锦宇\",\"已交\"\n\"02\",\"李子涵\",\"未交\""), st)
    check(show(r) == "张锦宇:SUBMITTED 李子涵:MISSING", "带引号 CSV：\(show(r))")
    let L = StatTable.Line.init
    r = StatTable.recognizeLines([L("作业登记表 高一(3)班", 0, 10, 0), L("01 张锦宇 √", 0, 50, 0), L("02 李子涵 X", 0, 90, 0), L("03 林静 优   04 王芳 假", 0, 130, 0), L("05冉浩迟交", 0, 170, 0)], st)
    check(show(r) == "张锦宇:SUBMITTED 李子涵:MISSING 林静:EXCELLENT 王芳:LEAVE 冉浩:LATE" && r.unmatched.count == 1, "拍照识别：\(show(r))")
    let frag = StatTable.mergeRows([L("√", 300, 52, 20), L("张锦宇", 60, 50, 24), L("×", 400, 49, 20), L("李子涵", 60, 92, 24), L("迟", 300, 95, 20)])
    check(frag.map(\.text).joined(separator: " / ") == "张锦宇 √ × / 李子涵 迟", "碎块拼行：\(frag.map(\.text))")
    r = StatTable.recognizeLines(frag, st)
    check(r.columns.count == 2 && show(r, 1) == "张锦宇:MISSING 李子涵:NONE", "碎块 → 两列：\(show(r, 1))")
    check(StatTable.scanStatuses("√ × 优 迟") == [.SUBMITTED, .MISSING, .EXCELLENT, .LATE], "一行多个标记")
    let sts = StatTable.columnStatuses(StatTable.recognizeTable(StatTable.splitTable("张锦宇\t✓\n李子涵\t×\n林静\t优\n王芳\t假"), st), 0)
    let t = StatTable.tally(st, sts)
    check(t.handedIn == 2 && t.expected == 5 && t.count(.NONE) == 2 && t.count(.MISSING) == 1, "统计：已交 \(t.handedIn)/\(t.expected)")
    // 统计名单 xlsx → 读回来
    var c = SchoolClass(name: "高一(3)班", students: st)
    let sheets = Xlsx.statListSheets(c, title: "练习", subject: "数学", date: Date(), statuses: sts, comments: ["s2": "没带"])
    check(sheets.map(\.name) == ["统计汇总", "分类名单", "明细"], "统计名单三张表")
    let back = try! StatTable.readXlsx(Xlsx.build([sheets[1]]))
    check(back.first?[3] == "未交（1）" && back.count > 1 && back[1][3] == "02 李子涵", "统计名单 xlsx 可读回：\(back.prefix(2))")
    var bad3 = ""; do { _ = try StatTable.readXlsx(Data([1, 2, 3])) } catch { bad3 = error.localizedDescription }
    check(bad3.contains("xlsx"), "坏文件报错")
    // 草稿
    c.students[5].longLeave = true
    var a = Assignment(title: "练习", subject: "数学", statuses: ["s1": "SUBMITTED", "s2": "MISSING"]); a.id = "a1"
    c.assignments = [a]
    var d1 = StatTable.newDraft(c, a)
    d1.statuses["s2"] = "LATE"; d1.statuses["s3"] = "SUBMITTED"; d1.statuses.removeValue(forKey: "s1"); d1.comments = ["s3": "字迹工整"]
    check(StatTable.changes(d1).sorted() == ["s1", "s2", "s3"], "草稿改动：\(StatTable.changes(d1))")
    var data = AppData(); var c2 = c; c2.assignments[0].statuses["s4"] = "EXCELLENT"; data.classes = [c2]
    let out = try! StatTable.apply(data, d1).classes[0].assignments[0]
    check(out.statuses == ["s2": "LATE", "s4": "EXCELLENT", "s3": "SUBMITTED"] && out.comments["s3"] == "字迹工整", "上传草稿：\(out.statuses)")
    let d2 = StatTable.newDraft(c, nil, title: "新作业", subject: "语文", statuses: ["s1": "SUBMITTED"])
    let as2 = try! StatTable.apply(data, d2).classes[0].assignments
    check(as2.count == 2 && as2[1].title == "新作业" && as2[1].statuses["s6"] == "LEAVE" && as2[1].statuses["s1"] == "SUBMITTED", "新作业草稿")
    check((try? StatTable.apply(AppData(), d2)) == nil, "班级被删 → 报错")
}
print("\n通过 \(pass)/\(total)")
exit(pass == total ? 0 : 1)
