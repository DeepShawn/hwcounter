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
// 积分：重置保留历史，修改基准保留手动加减分及未交次数。
do {
    func check(_ ok: Bool, _ msg: String) { total += 1; if ok { pass += 1 }; print("\(ok ? "✓" : "✗") \(msg)") }
    var rules = Rules(); rules.escalation.enabled = false; rules.streakBonus.enabled = false; rules.roundPoints = false // 旧用例沿用“未交固定扣分”
    var c = SchoolClass(name: "一班", students: [Student(id: "s1", no: "01", name: "张三"), Student(id: "s2", no: "02", name: "李四")])
    for _ in 1...3 {
        var a = Assignment(title: "作业", subject: "数学")
        a.statuses = ["s1": "MISSING"]; a.comments = ["s1": "补交中"]
        c.assignments.append(a)
    }
    c.scores = [ScoreEntry(studentId: "s1", delta: 3, reason: "课堂表现"), ScoreEntry(studentId: "s1", delta: -1, reason: "迟到")]
    var zeroBase = rules; zeroBase.initial = 0
    check(c.stats(rules)[0].score == 96 && c.stats(zeroBase)[0].score == -4 && c.stats(zeroBase)[0].missing == 3, "基准100改0，手动加减分和三次未交保留")
    let reset = c.resettingScores(rules, ids: ["s1", "s1", "other-class"], target: 0)
    check(reset.stats(rules).map(\.score) == [0, 100] && reset.scores.count == c.scores.count + 1, "重置只影响选中学生，重复ID不重复计分")
    check(reset.assignments == c.assignments && Array(reset.scores.prefix(c.scores.count)) == c.scores && reset.stats(rules)[0].missing == 3, "重置保留原始流水、作业备注及未交次数")
    check(reset.scores.last?.reason == "重置分数至 0" && reset.scores.last?.delta == -96, "重置生成可追溯的调整记录")
    var next = reset
    next.scores.append(ScoreEntry(studentId: "s1", delta: 2, reason: "进步"))
    check(next.stats(rules)[0].score == 2, "重置后手动加减分继续累计")
    let all = c.resettingScores(rules, ids: ["s1", "s2"], target: 25.5)
    check(all.stats(rules).map(\.score) == [25.5, 25.5] && Set(all.scores.map(\.id)).count == all.scores.count, "全班重置到指定分数，流水ID唯一")
    check(reset.resettingScores(rules, ids: ["s1"], target: 0) == reset, "重复重置不新增记录")
    check(c.resettingScores(rules, ids: [], target: 0) == c && c.resettingScores(rules, ids: ["s1"], target: .nan) == c && c.resettingScores(rules, ids: ["s1"], target: .infinity) == c, "空选择和非有限分数不修改数据")
}
// 积分重置：与安卓 RechargeTest、网页 recharge.test.mjs 同一组用例
do {
    print("== 积分重置")
    func check(_ ok: Bool, _ msg: String) { total += 1; if ok { pass += 1 }; print("\(ok ? "✓" : "✗") \(msg)") }
    var rules = Rules(); rules.escalation.enabled = false; rules.streakBonus.enabled = false; rules.roundPoints = false
    var c = SchoolClass(name: "一班", students: [Student(id: "s1", no: "01", name: "张三"), Student(id: "s2", no: "02", name: "李四")])
    for i in 1...3 {
        var a = Assignment(title: "作业\(i)", subject: "数学")
        a.createdAt = Int64(1000 * i); a.statuses = ["s1": "MISSING"]; a.comments = ["s1": "补交中"]
        c.assignments.append(a)
    }
    var e1 = ScoreEntry(studentId: "s1", delta: 3, reason: "课堂表现"); e1.time = 1500
    var e2 = ScoreEntry(studentId: "s1", delta: -1, reason: "迟到"); e2.time = 2500
    c.scores = [e1, e2]
    func score(_ k: SchoolClass, _ id: String, _ r: Rules) -> Double { k.stats(r).first { $0.id == id }!.score }
    let r1 = c.recharging(rules, ids: ["s1"], target: 100, time: 5000)
    check(score(c, "s1", rules) == 96 && score(r1, "s1", rules) == 100 && score(r1, "s2", rules) == 100, "重置把积分设为目标分")
    check(r1.scores == c.scores && r1.assignments == c.assignments && r1.stats(rules)[0].missing == 3 && r1.recharges[0].before["s1"] == 96, "重置保留流水、作业状态、未交次数，并记录重置前分数")
    var later = c.recharging(rules, ids: ["s1", "s2"], target: 50, time: 5000)
    var e3 = ScoreEntry(studentId: "s1", delta: 2, reason: "主动回答"); e3.time = 6000
    later.scores.append(e3)
    var a4 = Assignment(title: "作业4", subject: "数学"); a4.createdAt = 7000; a4.statuses = ["s1": "EXCELLENT", "s2": "MISSING"]
    later.assignments.append(a4)
    check(score(later, "s1", rules) == 53 && score(later, "s2", rules) == 48, "重置后新作业与加减分继续累计")
    var zero = rules; zero.initial = 0; zero.missing = -10
    check(score(r1, "s1", zero) == 100, "修改规则不改变重置后的基准")
    var twice = r1
    var e4 = ScoreEntry(studentId: "s1", delta: -4, reason: "违纪"); e4.time = 6000
    twice.scores.append(e4)
    twice = twice.recharging(rules, ids: ["s1"], target: 80, time: 7000)
    check(score(twice, "s1", rules) == 80 && twice.recharges.last?.before["s1"] == 96, "最近一次重置生效")
    var undone = twice; undone.recharges.removeLast()
    check(score(undone, "s1", rules) == 96, "删除记录即撤销重置")
    let json = try! JSONEncoder().encode(twice)
    let back = try! JSONDecoder().decode(SchoolClass.self, from: json)
    check(back.recharges == twice.recharges, "重置记录可编码/解码")
    let legacy = try! JSONDecoder().decode(SchoolClass.self, from: Data(#"{"name":"旧班级"}"#.utf8))
    check(legacy.recharges.isEmpty, "旧数据没有 recharges 字段也能读取")
    let a2id = c.assignments[1].id
    let fromA = c.recharging(rules, ids: ["s1"], target: 100, time: 9000, assignmentId: a2id)
    check(score(fromA, "s1", rules) == 95 && fromA.recharges[0].effectiveAt == 2000 && fromA.recharges[0].assignmentTitle == "作业2", "从某次作业起重置（含该作业）")
    let byDay = c.recharging(rules, ids: ["s1"], target: 100, time: 9000, effectiveAt: 2500)
    let both = byDay.recharging(rules, ids: ["s1"], target: 50, time: 10000, effectiveAt: 500)
    check(score(byDay, "s1", rules) == 97 && score(both, "s1", rules) == 97, "从某一天起重置；生效更晚的优先")
}
// 考试计分（与安卓 ExamTest、网页 recharge.test.mjs 同一组用例）
do {
    print("== 考试计分")
    func check(_ ok: Bool, _ msg: String) { total += 1; if ok { pass += 1 }; print("\(ok ? "✓" : "✗") \(msg)") }
    let r = ExamRules()
    check(r.delta(90, full: 100) == 2 && r.delta(100, full: 100) == 2 && r.delta(85, full: 100) == 1 && r.delta(70, full: 100) == 0 && r.delta(59.5, full: 100) == -1 && r.delta(140, full: 150) == 2, "分数档按得分率")
    var c = SchoolClass(name: "一班", students: [Student(id: "s1", no: "01", name: "张三"), Student(id: "s2", no: "02", name: "李四")])
    var e = Exam(title: "期中", scores: ["s1": 95, "s2": 50])
    e.date = 5000
    c.exams = [e]
    var rules = Rules(); rules.escalation.enabled = false; rules.streakBonus.enabled = false; rules.roundPoints = false
    check(c.stats(rules).map(\.score) == [102, 99], "考试加减分计入积分")
    let reset = c.recharging(rules, ids: ["s1", "s2"], target: 100, time: 9000)
    check(reset.stats(rules).map(\.score) == [100, 100], "重置后之前的考试不再计分")
    let json = try! JSONEncoder().encode(c)
    let back = try! JSONDecoder().decode(SchoolClass.self, from: json)
    check(back.exams == c.exams, "考试可编码/解码")
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
// 后台文件写入：读取必须等待之前的写入，快速连续写入不能覆盖为旧版本。
do {
    let dir = FileManager.default.temporaryDirectory.appendingPathComponent("hwcounter-files-\(newId())")
    try! FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
    defer { try? FileManager.default.removeItem(at: dir) }
    let mainFile = dir.appendingPathComponent("main.json"), draftFile = dir.appendingPathComponent("drafts.json")
    for i in 0..<30 { LocalJSONFile.write(["version": i], to: mainFile) }
    LocalJSONFile.write(["草稿一", "草稿二"], to: draftFile)
    let saved = LocalJSONFile.read([String: Int].self, from: mainFile)
    let drafts = LocalJSONFile.read([String].self, from: draftFile)
    total += 1
    if saved?["version"] == 29 && drafts == ["草稿一", "草稿二"] { pass += 1; print("✓ 后台文件写入按顺序保存，立即读取返回最新数据") }
    LocalJSONFile.write(["version": 30], to: mainFile); LocalJSONFile.flush()
    let persisted = try! JSONDecoder().decode([String: Int].self, from: Data(contentsOf: mainFile))
    total += 1
    if persisted["version"] == 30 { pass += 1; print("✓ 进入后台前可等待最新数据落盘") }
}
do {
    let cases: [([String], [String])] = [
        (["10", "2", "1"], ["1", "2", "10"]),
        (["010", "2", "001"], ["001", "2", "010"]),
        (["A10", "a2", "A1"], ["A1", "a2", "A10"]),
        (["90071992547409931", "90071992547409930", "2"], ["2", "90071992547409930", "90071992547409931"]),
        (["", "10", " 2 "], [" 2 ", "10", ""]),
    ]
    for (input, expected) in cases {
        total += 1
        if input.sorted(by: { compareStudentNumbers($0, $1) < 0 }) == expected { pass += 1; print("✓ 学号自然排序：\(expected)") }
    }
    total += 1
    if compareStudentNumbers("02", "2") == 0 { pass += 1; print("✓ 前导零不改变学号的数字顺序") }
}
// 每月定期重置：与安卓 AutoResetAndProgressTest、网页 recharge.test.mjs 同一组用例
do {
    print("== 每月定期重置")
    func check(_ ok: Bool, _ msg: String) { total += 1; if ok { pass += 1 }; print("\(ok ? "✓" : "✗") \(msg)") }
    func at(_ y: Int, _ m: Int, _ d: Int) -> Int64 { AutoReset.moment(y, m, d) }
    var ar = AutoReset(target: 100, day: 1); ar.since = at(2026, 9, 15)
    check(ar.due(at(2026, 11, 20)).map { $0.0 } == ["202610", "202611"], "只补开启之后到期的月份")
    let rules = Rules()
    var c = SchoolClass(name: "一班", students: [Student(id: "s1", no: "01", name: "张三")])
    var a = Assignment(title: "作业1", subject: "数学"); a.createdAt = at(2026, 9, 20); a.statuses = ["s1": "MISSING"]
    c.assignments = [a]; c.autoReset = ar
    c = c.applyingAutoReset(rules, now: at(2026, 11, 2))
    check(c.recharges.map(\.id) == ["auto-202610", "auto-202611"] && c.recharges[0].before["s1"] == 98, "补上的重置 id 固定，记录重置前分数")
    check(c.applyingAutoReset(rules, now: at(2026, 11, 2)) == c, "再补一次不会重复")
    c = c.undoingRecharge("auto-202611")
    check(c.autoReset?.skipped == ["202611"] && c.applyingAutoReset(rules, now: at(2026, 11, 3)).recharges.map(\.id) == ["auto-202610"], "撤销的月份不再补回")
    let back = try! JSONDecoder().decode(SchoolClass.self, from: JSONEncoder().encode(c))
    check(back.autoReset == c.autoReset, "定期重置设置可编码/解码（与安卓字段一致）")
}
// 未交连续递增扣分：与安卓 EscalationTest、网页 recharge.test.mjs 同一组用例
do {
    print("== 未交连续递增扣分")
    func check(_ ok: Bool, _ msg: String) { total += 1; if ok { pass += 1 }; print("\(ok ? "✓" : "✗") \(msg)") }
    let rules = Rules()
    func mk(_ m: [String]) -> SchoolClass {
        var c = SchoolClass(name: "一班", students: [Student(id: "s1", no: "01", name: "张三")])
        for (i, x) in m.enumerated() { var a = Assignment(title: "作业\(i)", subject: "数学"); a.id = "a\(i)"; a.createdAt = Int64((i + 1) * 1000); a.statuses = ["s1": x]; c.assignments.append(a) }
        return c
    }
    func pts(_ c: SchoolClass, _ r: Rules = Rules()) -> [Double] { let p = c.autoPoints(r, "s1"); return c.assignments.map { p[$0.id] ?? 0 } }
    check(pts(mk(["MISSING", "MISSING", "MISSING", "MISSING"])) == [-2, -4, -8, -16], "连续未交 2、4、8、16")
    check(pts(mk(["MISSING", "LEAVE", "MISSING"])) == [-2, 0, -4], "请假不打断连续")
    check(pts(mk(["MISSING", "MISSING", "SUBMITTED", "MISSING"])) == [-2, -4, 0, -2], "交过一次就重新算")
    let many = mk(["MISSING", "SUBMITTED", "MISSING", "SUBMITTED", "MISSING", "SUBMITTED", "MISSING", "SUBMITTED", "MISSING", "SUBMITTED", "MISSING", "SUBMITTED", "MISSING", "MISSING", "MISSING", "MISSING"])
    let mp = pts(many).enumerated().filter { many.assignments[$0.offset].statuses["s1"] == "MISSING" }.map(\.element)
    check(mp == [-2, -2, -2, -2, -2, -2, -3, -9, -27, -30], "本轮累计超 6 次后底数为 3，单次最多扣 30")
    let reset = many.recharging(rules, ids: ["s1"], target: 100, time: 99000, assignmentId: "a14")
    let rp = reset.autoPoints(rules, "s1")
    check(rp["a13"] == -9 && rp["a14"] == -2 && rp["a15"] == -4 && reset.stats(rules)[0].score == 94, "重置后新一轮从 2 开始")
    var capped = Rules(); capped.escalation.cap = 5
    check(pts(mk(["MISSING", "MISSING", "MISSING"]), capped) == [-2, -4, -5], "单次最多扣分上限")
}
// 连续交奖励与取整：与安卓 StreakBonusAndRoundingTest、网页同一组用例
do {
    print("== 连续交奖励与取整")
    func check(_ ok: Bool, _ msg: String) { total += 1; if ok { pass += 1 }; print("\(ok ? "✓" : "✗") \(msg)") }
    let rules = Rules()
    func mk(_ m: [String]) -> SchoolClass {
        var c = SchoolClass(name: "一班", students: [Student(id: "s1", no: "01", name: "张三")])
        for (i, x) in m.enumerated() { var a = Assignment(title: "作业\(i)", subject: "数学"); a.id = "a\(i)"; a.createdAt = Int64((i + 1) * 1000); a.statuses = ["s1": x]; c.assignments.append(a) }
        return c
    }
    func pts(_ c: SchoolClass, _ r: Rules = Rules()) -> [Double] { let p = c.autoPoints(r, "s1"); return c.assignments.map { p[$0.id] ?? 0 } }
    let S = "SUBMITTED"
    check(pts(mk([S, S, S, S, S, S, S, "EXCELLENT"])) == [0, 0, 0, 0, 0, 1, 1, 2], "连续交满 5 次后每次 +1")
    check(pts(mk([S, S, "LEAVE", S, S, S, S])) == [0, 0, 0, 0, 0, 0, 1], "请假不打断、不计次")
    check(pts(mk([S, S, S, S, S, "MISSING", S]))[6] == 0 && pts(mk([S, S, S, S, S, "LATE", S]))[6] == 0, "未交、迟交打断")
    let c0 = mk([S, S, S, S, S, S, S, S])
    let p = c0.recharging(rules, ids: ["s1"], target: 100, time: 99000, assignmentId: "a6").autoPoints(rules, "s1")
    check(p["a5"] == 1 && p["a6"] == 0 && p["a7"] == 0, "每轮重置重新计")
    check(roundAway(0.5) == 1 && roundAway(-0.5) == -1 && roundAway(-2.1) == -3 && roundAway(0) == 0, "正分向上、负分向下取整")
    var halfLate = Rules(); halfLate.late = -0.5
    let lc = mk(["LATE"])
    check(lc.autoPoints(halfLate, "s1")["a0"] == -0.5 && lc.stats(halfLate)[0].score == 100, "单笔保留原值，只对最终积分取整")
    var neg = halfLate; neg.initial = -2
    check(lc.stats(neg)[0].score == -3, "负分向下取整")
}
// 作业复审：与安卓 ReviewTest、网页同一组用例
do {
    print("== 作业复审")
    func check(_ ok: Bool, _ msg: String) { total += 1; if ok { pass += 1 }; print("\(ok ? "✓" : "✗") \(msg)") }
    func mk(_ m: [String], failed: Set<Int> = []) -> SchoolClass {
        var c = SchoolClass(name: "一班", students: [Student(id: "s", no: "1", name: "甲")])
        for (i, x) in m.enumerated() { var a = Assignment(title: "作业\(i)", subject: ""); a.id = "a\(i)"; a.createdAt = Int64((i + 1) * 1000); a.statuses = ["s": x]; if failed.contains(i) { a.reviewFailed = ["s"] }; c.assignments.append(a) }
        return c
    }
    func pts(_ c: SchoolClass, _ r: Rules = Rules()) -> [Double] { let p = c.autoPoints(r, "s"); return c.assignments.map { p[$0.id] ?? 0 } }
    let M = "MISSING"
    check(pts(mk([M, M], failed: [0])) == [-4, -4], "复审不合格翻倍（2×2），并算一次未交")
    check(pts(mk([M, M, M], failed: [2]))[2] == -16, "连续第 3 次 8 分翻倍为 16")
    check(pts(mk(Array(repeating: M, count: 6), failed: [5]))[5] == -60, "上限 30 之后再翻倍")
    var off = Rules(); off.review.enabled = false
    check(pts(mk([M], failed: [0]), off) == [-2], "关闭复审按普通未交")
    check(pts(mk(["SUBMITTED"], failed: [0])) == [0], "状态不是未交时标记不生效")
    check(mk([M], failed: [0]).stats(Rules())[0].score == 96, "积分 100 − 4")
    let back = try! JSONDecoder().decode(Assignment.self, from: JSONEncoder().encode(mk([M], failed: [0]).assignments[0]))
    check(back.reviewFailed == ["s"], "复审标记可编码/解码")
}
// 组长奖惩：与安卓 LeaderTest、网页 leader.test.mjs 同一组用例
do {
    print("== 组长奖惩")
    func check(_ ok: Bool, _ msg: String) { total += 1; if ok { pass += 1 }; print("\(ok ? "✓" : "✗") \(msg)") }
    let ids = ["a1", "a2", "a3", "b1", "b2", "b3", "c1", "c2", "c3", "x"]
    var c = SchoolClass(name: "一班", students: ids.map { id in var s = Student(id: id, no: id, name: id); s.group = ["a": "一组", "b": "二组", "c": "三组"][String(id.prefix(1))] ?? ""; return s })
    for k in 0..<6 {
        var a = Assignment(title: "作业\(k)", subject: ""); a.id = "a\(k)"; a.createdAt = Int64((k + 1) * 86_400_000)
        var m: [String: String] = [:]; for id in ids { m[id] = "SUBMITTED" }
        if k == 0 { m["b2"] = "MISSING" }
        if k <= 1 { m["c2"] = "MISSING"; m["c3"] = "MISSING" }
        a.statuses = m; c.assignments.append(a)
    }
    c.groupLeaders = ["一组": "a1", "二组": "b1", "三组": "c1"]
    var each = Rules(); each.leader.period = "assignment"; each.groupRank.enabled = false
    let lp = c.leaderPeriods(each)
    check(lp["a1"]!.map(\.points) == [4, 4, 5, 5, 5, 5], "零扣分、最少、连续、每段上限")
    check(lp["b1"]![0].points == 1 && lp["b1"]![1].points == 5, "不高于平均；比上一段少")
    check(Array(lp["c1"]!.map(\.points).prefix(3)) == [-3, -5, 4], "超平均 2 倍、最多、连续扣分偏多")
    check(lp["a2"] == nil && lp["x"] == nil, "组员、未分组没有组长奖惩")
    var off = each; off.leader.enabled = false
    check(c.stats(each).first { $0.id == "a1" }!.score - c.stats(off).first { $0.id == "a1" }!.score == 28, "计入积分")
    var month = Rules(); month.leader.period = "month"; month.groupRank.enabled = false
    let lm = c.leaderPeriods(month)
    check(lm["a1"]!.count == 1 && lm["a1"]![0].points == 4 && lm["c1"]![0].points == -4, "按月")
    let rc = c.recharging(each, ids: ["a1"], target: 100, time: 3 * 86_400_000 + 1, assignmentId: "a3")
    check(rc.leaderPeriods(each)["a1"]!.map(\.points) == [4, 4, 5, 3, 4, 5] && rc.stats(each).first { $0.id == "a1" }!.score == 112, "组长重置后重新算")
    let back = try! JSONDecoder().decode(SchoolClass.self, from: JSONEncoder().encode({ var x = c; x.reps = ["a1"]; return x }()))
    check(back.reps == ["a1"], "课代表可编码/解码")
}
// 小组对抗排名与整组加减分：与安卓 GroupRankTest、网页 grouprank.test.mjs 同一组用例
do {
    print("== 小组对抗排名")
    func check(_ ok: Bool, _ msg: String) { total += 1; if ok { pass += 1 }; print("\(ok ? "✓" : "✗") \(msg)") }
    let ids = ["a1", "a2", "b1", "b2", "c1", "c2", "x"]
    var c = SchoolClass(name: "一班", students: ids.map { id in var s = Student(id: id, no: id, name: id); s.group = ["a": "一组", "b": "二组", "c": "三组"][String(id.prefix(1))] ?? ""; return s })
    for k in 0..<2 {
        var a = Assignment(title: "作业\(k)", subject: ""); a.id = "a\(k)"; a.createdAt = Int64((k + 1) * 86_400_000)
        var m: [String: String] = [:]; for id in ids { m[id] = "SUBMITTED" }
        m["a1"] = "EXCELLENT"; m["c2"] = "MISSING"; if k == 1 { m["b2"] = "LATE" }
        a.statuses = m; c.assignments.append(a)
    }
    var each = Rules(); each.leader.enabled = false; each.groupRank.period = "assignment"
    let ps = c.groupRankPeriods(each)
    check(ps.count == 2 && ps[0].standings.map(\.group) == ["一组", "二组", "三组"] && ps[0].standings.map(\.rank) == [1, 2, 3], "排名")
    check(ps[0].standings.map(\.points) == [2, 1, -1], "第 1 +2、第 2 +1、最后 −1")
    check(ps[0].standings[0].score == ((0.5 + 0.25 + 0.075) / 0.9 * 10000).rounded() / 10000, "综合分")
    var off = each; off.groupRank.enabled = false
    let on = c.stats(each), of = c.stats(off)
    func d(_ id: String) -> Double { on.first { $0.id == id }!.score - of.first { $0.id == id }!.score }
    check(d("a2") == 4 && d("c1") == -2 && d("x") == 0, "整组每人计入积分")
    var month = each; month.groupRank.period = "month"
    check(c.groupRankPeriods(month).count == 1, "按月")
    var plus = each; plus.groupRank.mode = "plus"
    var minus = each; minus.groupRank.mode = "minus"
    check(c.groupRankPeriods(plus)[0].standings.map(\.points) == [2, 1, 0] && c.groupRankPeriods(minus)[0].standings.map(\.points) == [0, 0, -1], "仅加分 / 仅扣分")
    var lp = Rules(); lp.groupRank.enabled = false; lp.leader.period = "assignment"; lp.leader.mode = "plus"
    var cl = c; cl.groupLeaders = ["三组": "c1"]
    check(cl.leaderPeriods(lp)["c1"]!.allSatisfy { $0.points >= 0 }, "组长仅加分")
}
// 抄袭：与安卓 PlagiarismTest、网页 plagiarism.test.mjs 同一组用例
do {
    print("== 抄袭")
    func check(_ ok: Bool, _ msg: String) { total += 1; if ok { pass += 1 }; print("\(ok ? "✓" : "✗") \(msg)") }
    let S = "SUBMITTED", M = "MISSING"
    var c = SchoolClass(name: "一班", students: [("a", "甲"), ("b", "乙"), ("c", "丙"), ("d", "丁")].enumerated().map { Student(id: $0.element.0, no: "\($0.offset + 1)", name: $0.element.1) })
    let rows: [([String: String], [String: [String]])] = [
        (["a": M, "b": M, "c": S, "d": S], ["b": ["a"]]),
        (["a": S, "b": S, "c": S, "d": S], [:]),
        (["a": M, "b": M, "c": M, "d": S], ["b": ["a"], "c": ["a"]]),
        (["a": M, "b": S, "c": S, "d": S], ["b": ["a"]]),
    ]
    for (k, (st, p)) in rows.enumerated() { var a = Assignment(title: "作业\(k)", subject: ""); a.id = "a\(k)"; a.createdAt = Int64((k + 1) * 86_400_000); a.statuses = st; a.plagiarism = p; c.assignments.append(a) }
    func pts(_ c: SchoolClass, _ r: Rules, _ id: String) -> [Double] { let p = c.autoPoints(r, id); return c.assignments.map { p[$0.id] ?? 0 } }
    let R = Rules()
    check(pts(c, R, "a") == [-4, 0, -8, -4] && pts(c, R, "b") == [-4, 0, -8, 0] && pts(c, R, "c")[2] == -4 && pts(c, R, "d").reduce(0, +) == 0, "抄袭者和提供者都翻倍，累犯加重")
    check(!c.assignments[3].plagiarized("a") && c.stats(R).first { $0.id == "a" }!.missing == 3, "对方已交不算；抄袭算一次未交")
    let reset = c.recharging(R, ids: c.students.map(\.id), target: 100, time: 10 * 86_400_000, assignmentId: "a2")
    check(reset.autoPoints(R, "a")["a2"] == -4, "重置后重新计次")
    var off = R; off.plagiarism.enabled = false
    var flat = R; flat.plagiarism.escalate = false
    check(c.autoPoints(off, "b")["a0"] == -2 && c.autoPoints(flat, "b")["a2"] == -4, "关闭 / 不加重")
    check((1...5).map { PlagiarismRules().factor($0) } == [2, 4, 6, 8, 8], "最多 ×4")
    let g = c.plagiarismReport(R)
    check(g.cases.count == 3 && g.edges.map { "\($0.provider.id)→\($0.receiver.id)\($0.count)" } == ["a→b2", "a→c1"], "关系边")
    let na = g.nodes.first { $0.student.id == "a" }!, nb = g.nodes.first { $0.student.id == "b" }!
    check(na.copied == 0 && na.provided == 2 && na.points == -12 && na.role == "提供者" && nb.copied == 2 && nb.points == -12, "每人次数与扣分")
    check(g.clusters.map { $0.map(\.id) } == [["a", "b", "c"]] && g.copiers == 2 && g.providers == 1, "抄袭圈")
    var a2 = c.assignments[2]; a2.removePlagiarism(["a"])
    let back = try! JSONDecoder().decode(Assignment.self, from: JSONEncoder().encode(c.assignments[2]))
    check(a2.plagiarism.isEmpty && back.plagiarism == ["b": ["a"], "c": ["a"]], "取消标记；可编码/解码")
    // 关系图按作业：每次作业一张图
    check(c.plagiarismAssignments().map(\.id) == ["a2", "a0"], "有抄袭的作业，最新在前")
    let g0 = c.plagiarismReport(R, only: "a0"), g2 = c.plagiarismReport(R, only: "a2")
    check(g0.edges.map { "\($0.provider.id)→\($0.receiver.id)\($0.count)" } == ["a→b1"] && g2.edges.map { "\($0.provider.id)→\($0.receiver.id)\($0.count)" } == ["a→b1", "a→c1"], "单次作业的关系边")
    let a0n = g0.nodes.first { $0.student.id == "a" }!, a2n = g2.nodes.first { $0.student.id == "a" }!
    check(a0n.points == -4 && a0n.roundTimes == 1 && a0n.factor == 2 && a2n.points == -8 && a2n.roundTimes == 2 && a2n.factor == 4, "单次作业的扣分、第几次、倍数")
    check(c.plagiarismReport(R, only: "a3").cases.isEmpty, "没有抄袭的作业是空图")
    // 抄袭与复审不合格叠加
    var both = c; both.assignments[0].reviewFailed = ["b"]
    var noReview = R; noReview.review.enabled = false
    check(both.autoPoints(R, "b")["a0"] == -8 && both.autoPoints(R, "a")["a0"] == -4 && both.autoPoints(noReview, "b")["a0"] == -4, "抄袭与复审不合格叠加")
    check(both.assignmentPointNote(R, "a0", "b") == "本次 -8（抄袭第 1 次 ×2 × 复审不合格 ×2 = ×4）", "作业页计分说明")
    // 提供者不明
    var unk = c; unk.assignments[1].statuses["d"] = M; unk.assignments[1].plagiarism = ["d": [unknownProviderId]]
    let gu = unk.plagiarismReport(R, only: "a1")
    check(unk.autoPoints(R, "d")["a1"] == -4 && unk.assignments[1].unknownProvider("d") && plagiarismLine(unk, unk.assignments[1], "d") == "抄袭（提供者不明）" && gu.cases.count == 1 && gu.cases[0].unknown && gu.edges.isEmpty && gu.nodes.map(\.student.id) == ["d"], "抄袭：提供者不明")
}
print("\n通过 \(pass)/\(total)")
exit(pass == total ? 0 : 1)
