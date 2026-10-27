import Foundation

// 与安卓版 / 网页版 AppData 的 JSON 完全一致（字段名、枚举名、默认值）。
// 解码时缺字段一律用默认值；settings 原样保留为 JSON，只读写 iPad 用到的字段，避免同步时抹掉安卓端的设置。

func newId() -> String {
    (0..<8).map { _ in String(format: "%02x", UInt8.random(in: 0...255)) }.joined()
}

func nowMillis() -> Int64 { Int64(Date().timeIntervalSince1970 * 1000) }

enum HwStatus: String, CaseIterable, Identifiable {
    case NONE, SUBMITTED, EXCELLENT, LATE, MISSING, LEAVE
    var id: String { rawValue }
    var label: String {
        switch self {
        case .NONE: return "未登记"
        case .SUBMITTED: return "已交"
        case .EXCELLENT: return "优秀"
        case .LATE: return "迟交"
        case .MISSING: return "未交"
        case .LEAVE: return "请假"
        }
    }
    var emoji: String {
        switch self {
        case .NONE: return "⏳"
        case .SUBMITTED: return "✅"
        case .EXCELLENT: return "🌟"
        case .LATE: return "🐢"
        case .MISSING: return "❌"
        case .LEAVE: return "🏖️"
        }
    }
    var hex: UInt32 {
        switch self {
        case .NONE: return 0x9AA0A8
        case .SUBMITTED: return 0x1DB57A
        case .EXCELLENT: return 0xFFB300
        case .LATE: return 0xFF8A3D
        case .MISSING: return 0xF0475A
        case .LEAVE: return 0x4A8CFF
        }
    }
    var handedIn: Bool { self == .SUBMITTED || self == .EXCELLENT || self == .LATE }
}

/// 任意 JSON 值（用于原样保留 settings 等）
enum JSONValue: Codable, Equatable {
    case null, bool(Bool), number(Double), string(String), array([JSONValue]), object([String: JSONValue])

    init(from decoder: Decoder) throws {
        let c = try decoder.singleValueContainer()
        if c.decodeNil() { self = .null }
        else if let b = try? c.decode(Bool.self) { self = .bool(b) }
        else if let d = try? c.decode(Double.self) { self = .number(d) }
        else if let s = try? c.decode(String.self) { self = .string(s) }
        else if let a = try? c.decode([JSONValue].self) { self = .array(a) }
        else { self = .object(try c.decode([String: JSONValue].self)) }
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.singleValueContainer()
        switch self {
        case .null: try c.encodeNil()
        case .bool(let b): try c.encode(b)
        case .number(let d):
            if d == d.rounded(), abs(d) < 9e15 { try c.encode(Int64(d)) } else { try c.encode(d) }
        case .string(let s): try c.encode(s)
        case .array(let a): try c.encode(a)
        case .object(let o): try c.encode(o)
        }
    }

    subscript(key: String) -> JSONValue? {
        get { if case .object(let o) = self { return o[key] }; return nil }
        set {
            var o: [String: JSONValue] = [:]
            if case .object(let cur) = self { o = cur }
            o[key] = newValue
            self = .object(o)
        }
    }
    var double: Double? { if case .number(let d) = self { return d }; return nil }
    var int: Int? { double.map { Int($0) } }
    var bool: Bool? { if case .bool(let b) = self { return b }; return nil }
    var string: String? { if case .string(let s) = self { return s }; return nil }
    var strings: [String]? { if case .array(let a) = self { return a.compactMap { $0.string } }; return nil }
}

struct Student: Codable, Identifiable, Equatable, Hashable {
    var id: String = newId()
    var no: String
    var name: String
    var group: String = ""
    var gender: String = ""
    var note: String = ""
    var longLeave: Bool = false

    init(id: String = newId(), no: String, name: String, group: String = "", gender: String = "", note: String = "", longLeave: Bool = false) {
        self.id = id; self.no = no; self.name = name; self.group = group; self.gender = gender; self.note = note; self.longLeave = longLeave
    }
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decodeIfPresent(String.self, forKey: .id) ?? newId()
        no = try c.decodeIfPresent(String.self, forKey: .no) ?? ""
        name = try c.decodeIfPresent(String.self, forKey: .name) ?? ""
        group = try c.decodeIfPresent(String.self, forKey: .group) ?? ""
        gender = try c.decodeIfPresent(String.self, forKey: .gender) ?? ""
        note = try c.decodeIfPresent(String.self, forKey: .note) ?? ""
        longLeave = try c.decodeIfPresent(Bool.self, forKey: .longLeave) ?? false
    }
}

struct Assignment: Codable, Identifiable, Equatable {
    var id: String = newId()
    var title: String
    var subject: String = ""
    var createdAt: Int64 = nowMillis()
    var statuses: [String: String] = [:]
    var comments: [String: String] = [:]
    var countedBooks: Int? = nil
    var pinned: Bool = false

    init(title: String, subject: String, statuses: [String: String] = [:]) {
        self.title = title; self.subject = subject; self.statuses = statuses
    }
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decodeIfPresent(String.self, forKey: .id) ?? newId()
        title = try c.decodeIfPresent(String.self, forKey: .title) ?? ""
        subject = try c.decodeIfPresent(String.self, forKey: .subject) ?? ""
        createdAt = try c.decodeIfPresent(Int64.self, forKey: .createdAt) ?? nowMillis()
        statuses = try c.decodeIfPresent([String: String].self, forKey: .statuses) ?? [:]
        comments = try c.decodeIfPresent([String: String].self, forKey: .comments) ?? [:]
        countedBooks = try c.decodeIfPresent(Int.self, forKey: .countedBooks)
        pinned = try c.decodeIfPresent(Bool.self, forKey: .pinned) ?? false
    }
    func status(_ sid: String) -> HwStatus { HwStatus(rawValue: statuses[sid] ?? "") ?? .NONE }
    var date: Date { Date(timeIntervalSince1970: Double(createdAt) / 1000) }
}

struct ScoreEntry: Codable, Identifiable, Equatable {
    var id: String = newId()
    var studentId: String
    var delta: Double
    var reason: String
    var time: Int64 = nowMillis()

    init(studentId: String, delta: Double, reason: String) {
        self.studentId = studentId; self.delta = delta; self.reason = reason
    }
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decodeIfPresent(String.self, forKey: .id) ?? newId()
        studentId = try c.decodeIfPresent(String.self, forKey: .studentId) ?? ""
        delta = try c.decodeIfPresent(Double.self, forKey: .delta) ?? 0
        reason = try c.decodeIfPresent(String.self, forKey: .reason) ?? ""
        time = try c.decodeIfPresent(Int64.self, forKey: .time) ?? nowMillis()
    }
}

struct SchoolClass: Codable, Identifiable, Equatable {
    var id: String = newId()
    var name: String
    var students: [Student] = []
    var assignments: [Assignment] = []
    var scores: [ScoreEntry] = []
    var emoji: String = "🎒"

    init(name: String, emoji: String = "🎒", students: [Student] = []) {
        self.name = name; self.emoji = emoji; self.students = students
    }
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decodeIfPresent(String.self, forKey: .id) ?? newId()
        name = try c.decodeIfPresent(String.self, forKey: .name) ?? "班级"
        students = try c.decodeIfPresent([Student].self, forKey: .students) ?? []
        assignments = try c.decodeIfPresent([Assignment].self, forKey: .assignments) ?? []
        scores = try c.decodeIfPresent([ScoreEntry].self, forKey: .scores) ?? []
        emoji = try c.decodeIfPresent(String.self, forKey: .emoji) ?? "🎒"
    }
}

struct Rules: Equatable {
    var initial = 100.0, submitted = 0.0, excellent = 1.0, late = -1.0, missing = -2.0, leave = 0.0
    func of(_ s: HwStatus) -> Double {
        switch s {
        case .NONE: return 0
        case .SUBMITTED: return submitted
        case .EXCELLENT: return excellent
        case .LATE: return late
        case .MISSING: return missing
        case .LEAVE: return leave
        }
    }
}

struct AppData: Codable, Equatable {
    var version: Int = 1
    var classes: [SchoolClass] = []
    var currentClassId: String? = nil
    var settings: JSONValue = .object([:])

    init() {}
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        version = try c.decodeIfPresent(Int.self, forKey: .version) ?? 1
        classes = try c.decodeIfPresent([SchoolClass].self, forKey: .classes) ?? []
        currentClassId = try c.decodeIfPresent(String.self, forKey: .currentClassId)
        settings = try c.decodeIfPresent(JSONValue.self, forKey: .settings) ?? .object([:])
    }

    var currentClass: SchoolClass? { classes.first { $0.id == currentClassId } ?? classes.first }

    // ---------- settings 中 iPad 用到的字段 ----------
    var rules: Rules {
        get {
            let r = settings["rules"]
            var x = Rules()
            x.initial = r?["initial"]?.double ?? x.initial
            x.submitted = r?["submitted"]?.double ?? x.submitted
            x.excellent = r?["excellent"]?.double ?? x.excellent
            x.late = r?["late"]?.double ?? x.late
            x.missing = r?["missing"]?.double ?? x.missing
            x.leave = r?["leave"]?.double ?? x.leave
            return x
        }
        set {
            var r = settings["rules"] ?? .object([:])
            r["initial"] = .number(newValue.initial); r["submitted"] = .number(newValue.submitted)
            r["excellent"] = .number(newValue.excellent); r["late"] = .number(newValue.late)
            r["missing"] = .number(newValue.missing); r["leave"] = .number(newValue.leave)
            settings["rules"] = r
        }
    }
    var subjects: [String] { settings["subjects"]?.strings ?? ["语文", "数学", "英语", "物理", "化学", "生物", "历史", "地理", "政治"] }
    var quickReasons: [String] { settings["quickReasons"]?.strings ?? ["课堂表现好", "主动回答", "作业优秀", "进步明显", "迟到", "违反纪律", "未带作业"] }
    var voiceFuzzy: Int {
        get { settings["voiceFuzzy"]?.int ?? 1 }
        set { settings["voiceFuzzy"] = .number(Double(newValue)) }
    }
    var voiceInitials: Bool {
        get { settings["voiceInitials"]?.bool ?? true }
        set { settings["voiceInitials"] = .bool(newValue) }
    }
    var theme: String {
        get { settings["theme"]?.string ?? "mint" }
        set { settings["theme"] = .string(newValue) }
    }

    /// 上传前去掉只属于本机的 AI API Key
    func stripped() -> AppData {
        var d = self
        if var ai = d.settings["ai"] { ai["apiKey"] = .string(""); d.settings["ai"] = ai }
        return d
    }
}

// ---------- 统计 ----------
struct Summary {
    var counts: [HwStatus: Int]
    var total: Int
    func c(_ s: HwStatus) -> Int { counts[s] ?? 0 }
    var handedIn: Int { c(.SUBMITTED) + c(.EXCELLENT) + c(.LATE) }
    var expected: Int { total - c(.LEAVE) }
    var rate: Double { expected <= 0 ? 1 : Double(handedIn) / Double(expected) }
}

extension SchoolClass {
    func summary(_ a: Assignment) -> Summary {
        var counts: [HwStatus: Int] = [:]
        for s in students { counts[a.status(s.id), default: 0] += 1 }
        return Summary(counts: counts, total: students.count)
    }

    struct Stat: Identifiable { let student: Student; let score: Double; let submitted: Int; let late: Int; let missing: Int; var id: String { student.id } }

    func stats(_ rules: Rules) -> [Stat] {
        var manual: [String: Double] = [:]
        for e in scores { manual[e.studentId, default: 0] += e.delta }
        return students.map { st in
            var auto = 0.0, sub = 0, late = 0, miss = 0
            for a in assignments {
                let s = a.status(st.id)
                auto += rules.of(s)
                if s == .SUBMITTED || s == .EXCELLENT { sub += 1 }
                if s == .LATE { late += 1 }
                if s == .MISSING { miss += 1 }
            }
            return Stat(student: st, score: rules.initial + auto + (manual[st.id] ?? 0), submitted: sub, late: late, missing: miss)
        }
    }
}

func fmtNum(_ x: Double) -> String {
    let r = (x * 10).rounded() / 10
    return r == r.rounded() ? String(Int(r)) : String(format: "%.1f", r)
}
func signed(_ x: Double) -> String { (x > 0 ? "+" : "") + fmtNum(x) }

/// 作业只显示年月日
func fmtDate(_ d: Date) -> String {
    let f = DateFormatter(); f.locale = Locale(identifier: "zh_CN"); f.dateFormat = "yyyy年M月d日"
    return f.string(from: d)
}
