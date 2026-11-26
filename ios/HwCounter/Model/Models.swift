import Foundation

// 与安卓版 / 网页版 AppData 的 JSON 完全一致（字段名、枚举名、默认值）。
// 解码时缺字段一律用默认值；settings 原样保留为 JSON，只读写 iPad 用到的字段，避免同步时抹掉安卓端的设置。

func newId() -> String {
    (0..<8).map { _ in String(format: "%02x", UInt8.random(in: 0...255)) }.joined()
}

func nowMillis() -> Int64 { Int64(Date().timeIntervalSince1970 * 1000) }

private let studentNumberParts = try! NSRegularExpression(pattern: "[0-9]+|[^0-9]+")

/// 自然学号顺序；不转为数值，保留长学号精度，空学号排在最后。
func compareStudentNumbers(_ left: String, _ right: String) -> Int {
    let a = left.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
    let b = right.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
    if a.isEmpty || b.isEmpty { return a == b ? 0 : a.isEmpty ? 1 : -1 }
    func parts(_ value: String) -> [String] {
        studentNumberParts.matches(in: value, range: NSRange(value.startIndex..., in: value)).compactMap {
            Range($0.range, in: value).map { String(value[$0]) }
        }
    }
    let xs = parts(a), ys = parts(b)
    for (leftPart, rightPart) in zip(xs, ys) {
        var x = leftPart, y = rightPart
        if let firstX = x.utf8.first, let firstY = y.utf8.first, (48...57).contains(firstX), (48...57).contains(firstY) {
            x = String(x.drop(while: { $0 == "0" })); y = String(y.drop(while: { $0 == "0" }))
            if x.isEmpty { x = "0" }; if y.isEmpty { y = "0" }
            if x.count != y.count { return x.count < y.count ? -1 : 1 }
        }
        if x != y { return x < y ? -1 : 1 }
    }
    return xs.count == ys.count ? 0 : xs.count < ys.count ? -1 : 1
}

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
    /// 复审不合格的同学：状态记为未交（多算一次未交），扣分按倍数翻倍
    var reviewFailed: [String] = []
    /// 抄袭：抄袭者 id → 提供者 id 列表。抄袭者和提供者都记为未交，扣分翻倍、累犯加重
    var plagiarism: [String: [String]] = [:]

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
        reviewFailed = try c.decodeIfPresent([String].self, forKey: .reviewFailed) ?? []
        plagiarism = try c.decodeIfPresent([String: [String]].self, forKey: .plagiarism) ?? [:]
    }
    func status(_ sid: String) -> HwStatus { HwStatus(rawValue: statuses[sid] ?? "") ?? .NONE }
    /// 复审不合格（只有状态仍是未交时才算）
    func failedReview(_ sid: String) -> Bool { reviewFailed.contains(sid) && status(sid) == .MISSING }
    /// 这位同学抄了谁的（只有状态仍是未交时才算）
    func copiedFrom(_ sid: String) -> [String] { status(sid) == .MISSING ? plagiarism[sid] ?? [] : [] }
    /// 谁抄了这位同学的（只有状态仍是未交时才算）
    func copiedBy(_ sid: String) -> [String] {
        guard status(sid) == .MISSING else { return [] }
        return plagiarism.filter { $0.value.contains(sid) && status($0.key) == .MISSING }.map(\.key).sorted()
    }
    /// 这次作业是否涉及抄袭（抄袭者或提供者）
    func plagiarized(_ sid: String) -> Bool { !copiedFrom(sid).isEmpty || !copiedBy(sid).isEmpty }
    /// 这次作业记为抄袭但提供者不明
    func unknownProvider(_ sid: String) -> Bool { copiedFrom(sid).contains(unknownProviderId) }
    /// 去掉这些同学的抄袭标记（作为抄袭者和提供者）
    mutating func removePlagiarism(_ ids: [String]) {
        var p: [String: [String]] = [:]
        for (r, ps) in plagiarism where !ids.contains(r) { let l = ps.filter { !ids.contains($0) }; if !l.isEmpty { p[r] = l } }
        plagiarism = p
    }
    var date: Date { Date(timeIntervalSince1970: Double(createdAt) / 1000) }
}

struct ScoreEntry: Codable, Identifiable, Equatable {
    var id: String = newId()
    var studentId: String
    var delta: Double
    var reason: String
    var time: Int64 = nowMillis()
    /// 来自哪次结算（安卓结算写入，三端保留）
    var settlementId: String? = nil

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
        settlementId = try c.decodeIfPresent(String.self, forKey: .settlementId)
    }
}

/// 积分重置：把选中学生的当前积分设为 target 并留下一条记录（与安卓 Recharge.kt / 网页 model.js 同格式同算法）。
/// 当前积分 = target + 重置之后布置的作业自动计分 + 重置之后的手动加减分；历史记录全部保留，删除记录即撤销。
struct ScoreRecharge: Codable, Identifiable, Equatable {
    var id: String = newId()
    var time: Int64 = nowMillis()
    var target: Double
    var studentIds: [String]
    var before: [String: Double] = [:]
    var note: String = ""
    /// 生效时间：nil = 操作时立即生效；否则从这一刻（含）起重新计分（从某次作业 / 某一天起）
    var effectiveAt: Int64? = nil
    var assignmentId: String? = nil
    var assignmentTitle: String = ""

    var start: Int64 { effectiveAt ?? time }
    func counts(_ t: Int64) -> Bool { if let e = effectiveAt { return t >= e } else { return t > time } }
    var startLabel: String {
        guard let e = effectiveAt else { return "立即生效" }
        if assignmentId != nil { return "从「\(assignmentTitle.isEmpty ? "所选作业" : assignmentTitle)」起" }
        return "从 \(fmtDate(Date(timeIntervalSince1970: Double(e) / 1000))) 起"
    }

    init(target: Double, studentIds: [String], before: [String: Double], note: String, time: Int64 = nowMillis()) {
        self.target = target; self.studentIds = studentIds; self.before = before; self.note = note; self.time = time
    }
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decodeIfPresent(String.self, forKey: .id) ?? newId()
        time = try c.decodeIfPresent(Int64.self, forKey: .time) ?? nowMillis()
        target = try c.decodeIfPresent(Double.self, forKey: .target) ?? 0
        studentIds = try c.decodeIfPresent([String].self, forKey: .studentIds) ?? []
        before = try c.decodeIfPresent([String: Double].self, forKey: .before) ?? [:]
        note = try c.decodeIfPresent(String.self, forKey: .note) ?? ""
        effectiveAt = try c.decodeIfPresent(Int64.self, forKey: .effectiveAt)
        assignmentId = try c.decodeIfPresent(String.self, forKey: .assignmentId)
        assignmentTitle = try c.decodeIfPresent(String.self, forKey: .assignmentTitle) ?? ""
    }
}

/// 考试（与安卓 Exams.kt、网页 model.js 同格式）：分数与作业分开记录
struct Exam: Codable, Identifiable, Equatable {
    var id: String = newId()
    var title: String
    var subject: String = ""
    var date: Int64 = nowMillis()
    var fullScore: Double = 100
    var scores: [String: Double] = [:]
    var absent: [String] = []
    var note: String = ""
    var autoScore: Bool = true
    /// 不计入分析：不参加信誉分、小组对抗等统计（成绩照常显示，本场的加减积分仍按 autoScore）。与安卓 skipAnalysis 同一字段
    var skipAnalysis: Bool = false

    init(title: String, subject: String = "", date: Int64 = nowMillis(), fullScore: Double = 100, scores: [String: Double] = [:], absent: [String] = []) {
        self.title = title; self.subject = subject; self.date = date; self.fullScore = fullScore; self.scores = scores; self.absent = absent
    }
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decodeIfPresent(String.self, forKey: .id) ?? newId()
        title = try c.decodeIfPresent(String.self, forKey: .title) ?? "考试"
        subject = try c.decodeIfPresent(String.self, forKey: .subject) ?? ""
        date = try c.decodeIfPresent(Int64.self, forKey: .date) ?? nowMillis()
        fullScore = try c.decodeIfPresent(Double.self, forKey: .fullScore) ?? 100
        scores = try c.decodeIfPresent([String: Double].self, forKey: .scores) ?? [:]
        absent = try c.decodeIfPresent([String].self, forKey: .absent) ?? []
        note = try c.decodeIfPresent(String.self, forKey: .note) ?? ""
        autoScore = try c.decodeIfPresent(Bool.self, forKey: .autoScore) ?? true
        skipAnalysis = try c.decodeIfPresent(Bool.self, forKey: .skipAnalysis) ?? false
    }
}

/// 考试计分档：得分率落在 [minPct, maxPct) 加 delta 分（最高档含 100%）
struct ExamBand: Equatable { var minPct: Double; var maxPct: Double; var delta: Double }
struct ExamRules: Equatable {
    var enabled = true
    var bands: [ExamBand] = [ExamBand(minPct: 90, maxPct: 100, delta: 2), ExamBand(minPct: 80, maxPct: 90, delta: 1), ExamBand(minPct: 0, maxPct: 60, delta: -1)]
    var absent = 0.0
    func delta(_ score: Double, full: Double) -> Double {
        guard enabled, full > 0, score.isFinite else { return 0 }
        let pct = score / full * 100
        let top = max(100, bands.map(\.maxPct).max() ?? 100)
        return bands.first { pct >= $0.minPct && (pct < $0.maxPct || ($0.maxPct >= top && pct >= $0.maxPct)) }?.delta ?? 0
    }
    func delta(_ e: Exam, _ sid: String) -> Double {
        guard enabled, e.autoScore else { return 0 }
        if e.absent.contains(sid) { return absent }
        guard let s = e.scores[sid] else { return 0 }
        return delta(s, full: e.fullScore)
    }
}

struct SchoolClass: Codable, Identifiable, Equatable {
    var id: String = newId()
    var name: String
    var students: [Student] = []
    var assignments: [Assignment] = []
    var scores: [ScoreEntry] = []
    var emoji: String = "🎒"
    /// 座位表（可选），与安卓版、网页版同一格式
    var seating: Seating? = nil
    /// 积分重置记录
    var recharges: [ScoreRecharge] = []
    /// 考试
    var exams: [Exam] = []
    /// 参加分析统计的考试（去掉“不计入分析”的）
    var analysisExams: [Exam] { exams.contains(where: \.skipAnalysis) ? exams.filter { !$0.skipAnalysis } : exams }
    /// 小组 → 组长学生 id
    var groupLeaders: [String: String] = [:]
    /// 课代表（学生 id），可以同时是组长
    var reps: [String] = []
    /// 每月定期重置（与安卓 AutoReset.kt 同格式同算法）
    var autoReset: AutoReset? = nil
    /// 结算记录（安卓生成；iPad 原样保留并同步）
    var settlements: [JSONValue] = []

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
        seating = try? c.decodeIfPresent(Seating.self, forKey: .seating)
        recharges = (try? c.decodeIfPresent([ScoreRecharge].self, forKey: .recharges)) ?? []
        exams = (try? c.decodeIfPresent([Exam].self, forKey: .exams)) ?? []
        groupLeaders = (try? c.decodeIfPresent([String: String].self, forKey: .groupLeaders)) ?? [:]
        reps = (try? c.decodeIfPresent([String].self, forKey: .reps)) ?? []
        autoReset = try? c.decodeIfPresent(AutoReset.self, forKey: .autoReset)
        settlements = (try? c.decodeIfPresent([JSONValue].self, forKey: .settlements)) ?? []
    }
}

/// 每月定期重置：每月 day 号 0 点起全班积分重置为 target；记录 id 为 “auto-年月”，撤销的月份记入 skipped 不再补回
struct AutoReset: Codable, Equatable {
    var enabled = true
    var target = 100.0
    var day = 1
    var note = "每月定期重置"
    var since: Int64 = nowMillis()
    var skipped: [String] = []

    init(enabled: Bool = true, target: Double = 100, day: Int = 1, note: String = "每月定期重置") {
        self.enabled = enabled; self.target = target; self.day = day; self.note = note
    }
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        enabled = try c.decodeIfPresent(Bool.self, forKey: .enabled) ?? true
        target = try c.decodeIfPresent(Double.self, forKey: .target) ?? 100
        day = try c.decodeIfPresent(Int.self, forKey: .day) ?? 1
        note = try c.decodeIfPresent(String.self, forKey: .note) ?? "每月定期重置"
        since = try c.decodeIfPresent(Int64.self, forKey: .since) ?? nowMillis()
        skipped = try c.decodeIfPresent([String].self, forKey: .skipped) ?? []
    }

    static func moment(_ y: Int, _ m: Int, _ day: Int) -> Int64 {
        var dc = DateComponents(); dc.year = y; dc.month = m; dc.day = min(max(day, 1), 28)
        return Int64((Calendar.current.date(from: dc) ?? Date()).timeIntervalSince1970 * 1000)
    }

    /// 开启之后、到 now 为止已到期的（最多 24 个），按时间先后：(yyyyMM, 时刻)
    func due(_ now: Int64 = nowMillis()) -> [(String, Int64)] {
        guard enabled, now > since else { return [] }
        let d = Date(timeIntervalSince1970: Double(now) / 1000)
        var y = Calendar.current.component(.year, from: d), m = Calendar.current.component(.month, from: d)
        var out: [(String, Int64)] = []
        for _ in 0..<24 {
            let t = AutoReset.moment(y, m, day)
            if t <= since { break }
            if t <= now { out.append((String(format: "%04d%02d", y, m), t)) }
            m -= 1; if m < 1 { m = 12; y -= 1 }
        }
        return out.reversed()
    }
}

extension SchoolClass {
    /// 补上到期的定期重置
    func applyingAutoReset(_ rules: Rules, now: Int64 = nowMillis()) -> SchoolClass {
        guard let ar = autoReset, ar.enabled, !students.isEmpty, ar.target.isFinite else { return self }
        var c = self
        for (k, t) in ar.due(now) {
            let id = "auto-" + k
            if ar.skipped.contains(k) || c.recharges.contains(where: { $0.id == id }) { continue }
            var past = c
            past.assignments = c.assignments.filter { $0.createdAt <= t - 1 }
            past.scores = c.scores.filter { $0.time <= t - 1 }
            past.exams = c.exams.filter { $0.date <= t - 1 }
            past.recharges = c.recharges.filter { $0.start <= t - 1 }
            var before: [String: Double] = [:]
            for st in past.stats(rules) { before[st.id] = st.score }
            var r = ScoreRecharge(target: ar.target, studentIds: c.students.map(\.id), before: before, note: ar.note.isEmpty ? "每月定期重置" : ar.note, time: t)
            r.id = id; r.effectiveAt = t
            c.recharges.append(r)
        }
        return c
    }

    /// 撤销重置；定期重置记下月份
    func undoingRecharge(_ id: String) -> SchoolClass {
        var c = self
        c.recharges.removeAll { $0.id == id }
        if id.hasPrefix("auto-") {
            var ar = c.autoReset ?? AutoReset(enabled: false)
            let k = String(id.dropFirst(5))
            if !ar.skipped.contains(k) { ar.skipped.append(k) }
            c.autoReset = ar
        }
        return c
    }
}

/// 座位表：seats 为 学生id → 下标(行*cols+列)；frontAtTop 讲台是否在上方
struct Seating: Codable, Equatable {
    var rows: Int = 1
    var cols: Int = 1
    var seats: [String: Int] = [:]
    var frontAtTop: Bool = true

    init(rows: Int, cols: Int, seats: [String: Int], frontAtTop: Bool) {
        self.rows = rows; self.cols = cols; self.seats = seats; self.frontAtTop = frontAtTop
    }
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        rows = try c.decodeIfPresent(Int.self, forKey: .rows) ?? 1
        cols = try c.decodeIfPresent(Int.self, forKey: .cols) ?? 1
        seats = try c.decodeIfPresent([String: Int].self, forKey: .seats) ?? [:]
        frontAtTop = try c.decodeIfPresent(Bool.self, forKey: .frontAtTop) ?? true
    }
}

/// 分段倍率的一段：从第 from 次起 × factor
struct EscalationTier: Equatable { var from: Int, factor: Double }

/// 未交连续递增扣分（与安卓 Penalty.kt、网页 model.js 同算法），三种方式（mode）：
/// 1 正常加倍（2、4、8…）；2 第 2 次起加倍（2、2、4、8…）；3 分段倍率：在 1 或 2（tierBase）基础上按次数分段乘倍率（tiers），
/// 次数按连续未交次数（tierBy = "streak"）或本轮累计未交次数（"total"）算；单次上限在乘完倍率后再封顶
struct MissingEscalation: Equatable {
    static let defaultTiers = [EscalationTier(from: 1, factor: 1), EscalationTier(from: 3, factor: 1.5), EscalationTier(from: 5, factor: 2)]
    var enabled = true, base = 2.0, heavyBase = 3.0, heavyAfter = 6, cap = 30.0
    var mode = 2, tierBase = 2, tierBy = "streak", tiers = MissingEscalation.defaultTiers
    /// 间断未交平衡（默认开启）：次数取连续未交次数和最近 gapWindow 次有记录的作业里的未交次数中较大的（请假、未登记不算），
    /// 间断未交最多按第 gapMax 次算（真正连续未交不受限制）
    var gapBalance = true, gapWindow = 6, gapMax = 3
    func countOf(streak: Int, recent: Int) -> Int { gapBalance ? max(streak, min(recent, max(1, gapMax))) : streak }
    /// 实际使用的加倍方式（1 或 2）
    var shape: Int { mode == 1 ? 1 : mode == 3 ? (tierBase == 1 ? 1 : 2) : 2 }
    func tierFactor(_ n: Int) -> Double {
        guard mode == 3 else { return 1 }
        return tiers.filter { $0.from <= n }.max { $0.from < $1.from }?.factor ?? 1
    }
    func tierFactor(streak: Int, total: Int) -> Double { tierFactor(tierBy == "total" ? total : streak) }
    func penalty(streak: Int, total: Int) -> Double {
        let b = total > heavyAfter ? heavyBase : base
        let p = MissingEscalationAmount.amount(b, streak, shape) * tierFactor(streak: streak, total: total)
        return cap > 0 ? min(p, cap) : p
    }
    /// 从第 1 次起连续未交依次扣的分（含分段倍率和上限），如“2、2、6、12…”
    func sample(_ b: Double? = nil, _ k: Int = 4) -> String {
        (1...k).map { n -> String in
            let p = MissingEscalationAmount.amount(b ?? base, n, shape) * tierFactor(n)
            return fmtNum(cap > 0 ? min(p, cap) : p)
        }.joined(separator: "、") + "…"
    }
    var modeLabel: String { MissingEscalationAmount.label(mode) }
    var tierByLabel: String { tierBy == "total" ? "本轮累计未交次数" : "连续未交次数" }
    /// “第 1–2 次 ×1、第 3–4 次 ×1.5、第 5 次起 ×2（按连续未交次数）”
    func tiersText() -> String {
        let t = tiers.sorted { $0.from < $1.from }
        guard let first = t.first else { return "未设分段（都 ×1）" }
        var parts: [String] = []
        if first.from > 1 { parts.append("第 1\(first.from > 2 ? "–\(first.from - 1)" : "") 次 ×1") }
        for (i, x) in t.enumerated() {
            let to = i + 1 < t.count ? t[i + 1].from - 1 : nil
            if let to = to { parts.append(to <= x.from ? "第 \(x.from) 次 ×\(fmtNum(x.factor))" : "第 \(x.from)–\(to) 次 ×\(fmtNum(x.factor))") }
            else { parts.append("第 \(x.from) 次起 ×\(fmtNum(x.factor))") }
        }
        return parts.joined(separator: "、") + "（按\(tierByLabel)）"
    }
    func describe() -> String {
        let gap = gapBalance ? "；间断未交平衡：次数取连续未交次数和最近 \(gapWindow) 次作业里的未交次数中较大的（请假、未登记不算），间断未交最多按第 \(gapMax) 次算" : ""
        switch mode {
        case 1: return "正常加倍：连续未交依次扣 \(MissingEscalationAmount.seq(base, 1))" + gap
        case 3: return "分段倍率（在\(MissingEscalationAmount.label(shape))基础上）：\(tiersText())，依次扣 \(sample())" + gap
        default: return "第 2 次起加倍：连续未交依次扣 \(MissingEscalationAmount.seq(base, 2))" + gap
        }
    }
}

/// 连续第 n 次未交的扣分（不含分段倍率和上限），与安卓、网页一致：
/// shape 1：b、b²、b³……；shape 2：b、b、b²、b³……（第 1、2 次相同，之后每次翻 b 倍）
enum MissingEscalationAmount {
    static func amount(_ b: Double, _ n: Int, _ shape: Int = 2) -> Double {
        shape == 1 ? pow(b, Double(max(n, 1))) : pow(b, Double(max(max(n, 1) - 1, 1)))
    }
    /// 说明文字：“2、2、4、8…”（shape 1 时“2、4、8、16…”）
    static func seq(_ b: Double, _ shape: Int = 2) -> String { (1...4).map { fmtNum(amount(b, $0, shape)) }.joined(separator: "、") + "…" }
    static func label(_ mode: Int) -> String { mode == 1 ? "正常加倍" : mode == 3 ? "分段倍率" : "第 2 次起加倍" }
}

/// 连续交作业奖励：本轮连续按时交满 after 次后，之后每连续交一次额外加 bonus
struct StreakBonus: Equatable { var enabled = true, after = 5, bonus = 1.0, lateCounts = false }

/// 组长奖惩（与安卓 Leader.kt、网页 model.js 同算法）：按周期看小组人均作业扣分
struct LeaderRules: Equatable {
    var enabled = true, period = "week"
    var clean = 3.0, low = 2.0, below = 1.0, above = -1.0, high = -2.0, veryHigh = -3.0
    var top = 1.0, bottom = -1.0, progress = 1.0, regress = -1.0, streakStep = 1.0, streakMax = 3, cap = 5.0
    /// both 加分和扣分 / plus 仅加分 / minus 仅扣分
    var mode = "both"
}
/// 加减方式：只留加分或扣分
func applyMode(_ mode: String, _ x: Double) -> Double { mode == "plus" ? max(0, x) : mode == "minus" ? min(0, x) : x }
/// 小组对抗排名（与安卓 GroupRank.kt、网页 model.js 同算法）：按周期给小组综合排名，按名次给整组每人加减分
struct GroupRankRules: Equatable {
    var enabled = true, period = "week"
    var first = 2.0, second = 1.0, third = 0.0, last = -1.0
    var wRate = 0.5, wDeduct = 0.25, wExcellent = 0.15, wExam = 0.1
    var mode = "both"
    func points(rank: Int, worst: Int, n: Int) -> Double {
        if n >= 3 && rank == worst && rank > 1 { return applyMode(mode, last) }
        return applyMode(mode, rank == 1 ? first : rank == 2 ? second : rank == 3 ? third : 0)
    }
}
struct GroupStanding { var group: String; var rank: Int; var score: Double; var points: Double }
struct RankPeriod { var key: String; var label: String; var time: Int64; var standings: [GroupStanding] }

/// 信誉分加减分（与安卓 Credit.kt、网页 model.js 同算法）：个人 / 小组分别选 both 加分和扣分 / plus 只加不扣 / minus 只扣不加 / none 不计入
struct CreditRules: Equatable {
    var personal = "none", group = "none", period = "week"
    var excellentAt = 90.0, excellentPts = 2.0, goodAt = 80.0, goodPts = 1.0, poorBelow = 60.0, poorPts = -1.0, badBelow = 40.0, badPts = -2.0
    var active: Bool { personal != "none" || group != "none" }
    func band(_ x: Double) -> Double { x >= excellentAt ? excellentPts : x >= goodAt ? goodPts : x < badBelow ? badPts : x < poorBelow ? poorPts : 0 }
    static func apply(_ m: String, _ x: Double) -> Double { m == "both" ? x : m == "plus" ? max(0, x) : m == "minus" ? min(0, x) : 0 }
    static let modes: [(String, String)] = [("both", "加分和扣分"), ("plus", "只加不扣"), ("minus", "只扣不加"), ("none", "不计入")]
    static func label(_ m: String) -> String { modes.first { $0.0 == m }?.1 ?? "不计入" }
    var bandsText: String { "≥\(fmtNum(excellentAt)) \(signed(excellentPts))、≥\(fmtNum(goodAt)) \(signed(goodPts))、<\(fmtNum(poorBelow)) \(signed(poorPts))、<\(fmtNum(badBelow)) \(signed(badPts))" }
}

/// 一位同学的信誉分（0–100，越优秀越高）
struct CreditResult: Equatable { var index: Double; var hasData: Bool
    var level: String { !hasData ? "暂无" : index >= 90 ? "优秀" : index >= 75 ? "良好" : index >= 60 ? "一般" : "较差" }
}
/// 保留 2 位小数（三端比较分段时用同样的值）
func r2(_ x: Double) -> Double { (x * 100).rounded() / 100 }

enum Credit {
    /// 交齐 25%、按时 15%、优秀 10%（÷30%）、连续交 10%（÷5 次）、诚信 20%（最近 10 次复审不合格 / 抄袭每次 −0.5）、
    /// 进步 5%（最近 5 次比之前 5 次，不足 8 次为 0.5）、考试 15%（最近 3 场平均得分率）；没有数据的项不算
    static func one(_ sid: String, newest: [Assignment], exams: [Exam]) -> CreditResult {
        var m: [(Assignment, HwStatus)] = []
        for a in newest { let k = a.status(sid); if k != .NONE && k != .LEAVE { m.append((a, k)) } }
        func handed(_ k: HwStatus) -> Bool { k == .SUBMITTED || k == .EXCELLENT || k == .LATE }
        var parts: [(Double, Double)] = []
        if !m.isEmpty {
            let m10 = Array(m.prefix(10)); let n = Double(m10.count)
            parts.append((0.25, Double(m10.filter { handed($0.1) }.count) / n))
            parts.append((0.15, Double(m10.filter { $0.1 == .SUBMITTED || $0.1 == .EXCELLENT }.count) / n))
            parts.append((0.10, min(1, Double(m10.filter { $0.1 == .EXCELLENT }.count) / n / 0.3)))
            var run = 0; for (_, k) in m { if handed(k) { run += 1 } else { break } }
            parts.append((0.10, Double(min(run, 5)) / 5))
            let bad = m10.filter { $0.0.failedReview(sid) || $0.0.plagiarized(sid) }.count
            parts.append((0.20, max(0, 1 - 0.5 * Double(bad))))
            var trend = 0.5
            if m.count >= 8 {
                let a5 = Double(m.prefix(5).filter { handed($0.1) }.count) / 5
                let b = Array(m.dropFirst(5).prefix(5)); let b5 = Double(b.filter { handed($0.1) }.count) / Double(b.count)
                trend = min(1, max(0, 0.5 + (a5 - b5) / 0.4 * 0.5))
            }
            parts.append((0.05, trend))
        }
        let taken = exams.filter { $0.fullScore > 0 && $0.scores[sid] != nil }.suffix(3)
        if !taken.isEmpty {
            let p = min(1, max(0, taken.map { $0.scores[sid]! / $0.fullScore }.reduce(0, +) / Double(taken.count)))
            parts.append((0.15, p))
        }
        if parts.isEmpty { return CreditResult(index: 0, hasData: false) }
        let wsum = parts.reduce(0) { $0 + $1.0 }
        var sum = 0.0; for (w, v) in parts { sum += w / wsum * v * 100 }
        return CreditResult(index: r2(sum), hasData: true)
    }
}

/// 一段的信誉分加减分
struct CreditPeriod { var key: String; var label: String; var time: Int64; var personal: [String: (Double, Double)]; var groups: [String: (Double, Double)] }

struct LeaderPeriod { var group: String; var key: String; var label: String; var time: Int64; var perCapita: Double; var parts: [(String, Double)]; var points: Double { parts.reduce(0) { $0 + $1.1 } } }

/// 作业复审：不合格记一次未交，这一次扣分 × multiplier（默认翻倍）
struct ReviewRules: Equatable { var enabled = true, multiplier = 2.0 }

/// 抄袭：抄袭者和提供者都记一次未交，这一次扣分 × multiplier；累犯逐次加重（本轮第 n 次再 ×n，最多 ×maxTimes），每轮重新计次
struct PlagiarismRules: Equatable {
    var enabled = true, multiplier = 2.0, escalate = true, maxTimes = 4
    func factor(_ n: Int) -> Double { multiplier * Double(escalate ? min(max(1, n), max(1, maxTimes)) : 1) }
    var text: String {
        enabled ? "抄袭：抄袭者和提供者都记一次未交，扣分 ×\(fmtNum(multiplier))" + (escalate ? "，累犯逐次加重（第 n 次再 ×n，最多 ×\(maxTimes)）" : "") + "；同时复审不合格时倍数叠加" : "抄袭：按普通未交扣分"
    }
}

/// 正分向上取整、负分向下取整
func roundAway(_ x: Double) -> Double { x > 0 ? (x - 1e-9).rounded(.up) : x < 0 ? (x + 1e-9).rounded(.down) : 0 }

struct Rules: Equatable {
    var initial = 100.0, submitted = 0.0, excellent = 1.0, late = -1.0, missing = -2.0, leave = 0.0
    var exam = ExamRules()
    var escalation = MissingEscalation()
    var streakBonus = StreakBonus()
    var roundPoints = true
    var review = ReviewRules()
    var plagiarism = PlagiarismRules()
    var leader = LeaderRules()
    var groupRank = GroupRankRules()
    var credit = CreditRules()
    /// 单笔加减分保留原值；取整只作用在最终显示的总积分上
    func pt(_ x: Double) -> Double { x }
    func shown(_ total: Double) -> Double { roundPoints ? roundAway(total) : total }
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
            if let b = r?["streakBonus"] {
                x.streakBonus.enabled = b["enabled"]?.bool ?? x.streakBonus.enabled
                x.streakBonus.after = b["after"]?.int ?? x.streakBonus.after
                x.streakBonus.bonus = b["bonus"]?.double ?? x.streakBonus.bonus
                x.streakBonus.lateCounts = b["lateCounts"]?.bool ?? x.streakBonus.lateCounts
            }
            x.roundPoints = r?["roundPoints"]?.bool ?? x.roundPoints
            if let v = r?["leader"] {
                x.leader.enabled = v["enabled"]?.bool ?? x.leader.enabled
                x.leader.period = v["period"]?.string ?? x.leader.period
                x.leader.clean = v["clean"]?.double ?? x.leader.clean
                x.leader.low = v["low"]?.double ?? x.leader.low
                x.leader.below = v["below"]?.double ?? x.leader.below
                x.leader.above = v["above"]?.double ?? x.leader.above
                x.leader.high = v["high"]?.double ?? x.leader.high
                x.leader.veryHigh = v["veryHigh"]?.double ?? x.leader.veryHigh
                x.leader.top = v["top"]?.double ?? x.leader.top
                x.leader.bottom = v["bottom"]?.double ?? x.leader.bottom
                x.leader.progress = v["progress"]?.double ?? x.leader.progress
                x.leader.regress = v["regress"]?.double ?? x.leader.regress
                x.leader.streakStep = v["streakStep"]?.double ?? x.leader.streakStep
                x.leader.streakMax = v["streakMax"]?.int ?? x.leader.streakMax
                x.leader.cap = v["cap"]?.double ?? x.leader.cap
                x.leader.mode = v["mode"]?.string ?? x.leader.mode
            }
            if let v = r?["credit"] {
                x.credit.personal = v["personal"]?.string ?? x.credit.personal
                x.credit.group = v["group"]?.string ?? x.credit.group
                x.credit.period = v["period"]?.string ?? x.credit.period
                x.credit.excellentAt = v["excellentAt"]?.double ?? x.credit.excellentAt
                x.credit.excellentPts = v["excellentPts"]?.double ?? x.credit.excellentPts
                x.credit.goodAt = v["goodAt"]?.double ?? x.credit.goodAt
                x.credit.goodPts = v["goodPts"]?.double ?? x.credit.goodPts
                x.credit.poorBelow = v["poorBelow"]?.double ?? x.credit.poorBelow
                x.credit.poorPts = v["poorPts"]?.double ?? x.credit.poorPts
                x.credit.badBelow = v["badBelow"]?.double ?? x.credit.badBelow
                x.credit.badPts = v["badPts"]?.double ?? x.credit.badPts
            }
            if let v = r?["groupRank"] {
                x.groupRank.enabled = v["enabled"]?.bool ?? x.groupRank.enabled
                x.groupRank.period = v["period"]?.string ?? x.groupRank.period
                x.groupRank.first = v["first"]?.double ?? x.groupRank.first
                x.groupRank.second = v["second"]?.double ?? x.groupRank.second
                x.groupRank.third = v["third"]?.double ?? x.groupRank.third
                x.groupRank.last = v["last"]?.double ?? x.groupRank.last
                x.groupRank.wRate = v["wRate"]?.double ?? x.groupRank.wRate
                x.groupRank.wDeduct = v["wDeduct"]?.double ?? x.groupRank.wDeduct
                x.groupRank.wExcellent = v["wExcellent"]?.double ?? x.groupRank.wExcellent
                x.groupRank.wExam = v["wExam"]?.double ?? x.groupRank.wExam
                x.groupRank.mode = v["mode"]?.string ?? x.groupRank.mode
            }
            if let v = r?["review"] {
                x.review.enabled = v["enabled"]?.bool ?? x.review.enabled
                x.review.multiplier = v["multiplier"]?.double ?? x.review.multiplier
            }
            if let v = r?["plagiarism"] {
                x.plagiarism.enabled = v["enabled"]?.bool ?? x.plagiarism.enabled
                x.plagiarism.multiplier = v["multiplier"]?.double ?? x.plagiarism.multiplier
                x.plagiarism.escalate = v["escalate"]?.bool ?? x.plagiarism.escalate
                x.plagiarism.maxTimes = v["maxTimes"]?.int ?? x.plagiarism.maxTimes
            }
            if let e = r?["escalation"] {
                x.escalation.enabled = e["enabled"]?.bool ?? x.escalation.enabled
                x.escalation.base = e["base"]?.double ?? x.escalation.base
                x.escalation.heavyBase = e["heavyBase"]?.double ?? x.escalation.heavyBase
                x.escalation.heavyAfter = e["heavyAfter"]?.int ?? x.escalation.heavyAfter
                x.escalation.cap = e["cap"]?.double ?? x.escalation.cap
                x.escalation.mode = e["mode"]?.int ?? x.escalation.mode
                x.escalation.tierBase = e["tierBase"]?.int ?? x.escalation.tierBase
                x.escalation.tierBy = e["tierBy"]?.string ?? x.escalation.tierBy
                x.escalation.gapBalance = e["gapBalance"]?.bool ?? x.escalation.gapBalance
                x.escalation.gapWindow = e["gapWindow"]?.int ?? x.escalation.gapWindow
                x.escalation.gapMax = e["gapMax"]?.int ?? x.escalation.gapMax
                if case .array(let arr)? = e["tiers"] {
                    x.escalation.tiers = arr.compactMap { t in
                        guard let f = t["from"]?.int, let k = t["factor"]?.double else { return nil }
                        return EscalationTier(from: f, factor: k)
                    }
                }
            }
            if let e = r?["exam"] {
                x.exam.enabled = e["enabled"]?.bool ?? x.exam.enabled
                x.exam.absent = e["absent"]?.double ?? x.exam.absent
                if case .array(let arr)? = e["bands"] {
                    x.exam.bands = arr.compactMap { b in
                        guard let lo = b["minPct"]?.double, let hi = b["maxPct"]?.double, let d = b["delta"]?.double else { return nil }
                        return ExamBand(minPct: lo, maxPct: hi, delta: d)
                    }
                }
            }
            return x
        }
        set {
            var r = settings["rules"] ?? .object([:])
            r["initial"] = .number(newValue.initial); r["submitted"] = .number(newValue.submitted)
            r["excellent"] = .number(newValue.excellent); r["late"] = .number(newValue.late)
            r["missing"] = .number(newValue.missing); r["leave"] = .number(newValue.leave)
            var e = r["escalation"] ?? .object([:])
            e["enabled"] = .bool(newValue.escalation.enabled); e["base"] = .number(newValue.escalation.base)
            e["heavyBase"] = .number(newValue.escalation.heavyBase); e["heavyAfter"] = .number(Double(newValue.escalation.heavyAfter)); e["cap"] = .number(newValue.escalation.cap)
            e["mode"] = .number(Double(newValue.escalation.mode)); e["tierBase"] = .number(Double(newValue.escalation.tierBase))
            e["tierBy"] = .string(newValue.escalation.tierBy)
            e["gapBalance"] = .bool(newValue.escalation.gapBalance); e["gapWindow"] = .number(Double(newValue.escalation.gapWindow)); e["gapMax"] = .number(Double(newValue.escalation.gapMax))
            e["tiers"] = .array(newValue.escalation.tiers.map { .object(["from": .number(Double($0.from)), "factor": .number($0.factor)]) })
            r["escalation"] = e
            var b = r["streakBonus"] ?? .object([:])
            b["enabled"] = .bool(newValue.streakBonus.enabled); b["after"] = .number(Double(newValue.streakBonus.after))
            b["bonus"] = .number(newValue.streakBonus.bonus); b["lateCounts"] = .bool(newValue.streakBonus.lateCounts)
            r["streakBonus"] = b
            r["roundPoints"] = .bool(newValue.roundPoints)
            var v = r["review"] ?? .object([:])
            v["enabled"] = .bool(newValue.review.enabled); v["multiplier"] = .number(newValue.review.multiplier)
            r["review"] = v
            var pg = r["plagiarism"] ?? .object([:])
            pg["enabled"] = .bool(newValue.plagiarism.enabled); pg["multiplier"] = .number(newValue.plagiarism.multiplier)
            pg["escalate"] = .bool(newValue.plagiarism.escalate); pg["maxTimes"] = .number(Double(newValue.plagiarism.maxTimes))
            r["plagiarism"] = pg
            var cr = r["credit"] ?? .object([:])
            let c = newValue.credit
            cr["personal"] = .string(c.personal); cr["group"] = .string(c.group); cr["period"] = .string(c.period)
            cr["excellentAt"] = .number(c.excellentAt); cr["excellentPts"] = .number(c.excellentPts); cr["goodAt"] = .number(c.goodAt); cr["goodPts"] = .number(c.goodPts)
            cr["poorBelow"] = .number(c.poorBelow); cr["poorPts"] = .number(c.poorPts); cr["badBelow"] = .number(c.badBelow); cr["badPts"] = .number(c.badPts)
            r["credit"] = cr
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

    /// 新增调整流水，保留历史加减分、作业状态和未交次数。
    func resettingScores(_ rules: Rules, ids: [String], target: Double) -> SchoolClass {
        guard target.isFinite else { return self }
        let selected = Set(ids)
        var result = self
        result.scores += stats(rules).filter { selected.contains($0.id) && $0.score != target }.map {
            ScoreEntry(studentId: $0.id, delta: target - $0.score, reason: "重置分数至 \(fmtNum(target))")
        }
        return result
    }

    /// 每位学生最近一次生效的重置
    func latestRecharges() -> [String: ScoreRecharge] {
        var out: [String: ScoreRecharge] = [:]
        for r in recharges.sorted(by: { $0.start != $1.start ? $0.start < $1.start : $0.time != $1.time ? $0.time < $1.time : $0.id < $1.id }) {
            for sid in r.studentIds { out[sid] = r }
        }
        return out
    }

    /// 积分重置：只新增记录，不改动任何历史数据
    func recharging(_ rules: Rules, ids: [String], target: Double, note: String = "", time: Int64 = nowMillis(), effectiveAt: Int64? = nil, assignmentId: String? = nil) -> SchoolClass {
        guard target.isFinite else { return self }
        let wanted = Set(ids)
        let chosen = students.map(\.id).filter { wanted.contains($0) }
        guard !chosen.isEmpty else { return self }
        var now: [String: Double] = [:]
        for st in stats(rules) { now[st.id] = st.score }
        var result = self
        var before: [String: Double] = [:]
        for id in chosen { before[id] = now[id] ?? 0 }
        var r = ScoreRecharge(target: target, studentIds: chosen, before: before, note: note.trimmingCharacters(in: .whitespaces), time: time)
        if let aid = assignmentId, let a = assignments.first(where: { $0.id == aid }) {
            r.effectiveAt = a.createdAt; r.assignmentId = a.id; r.assignmentTitle = a.title
        } else { r.effectiveAt = effectiveAt }
        result.recharges.append(r)
        return result
    }

    /// 某位同学每次作业的自动计分（作业 id → 分），未交按连续递增规则
    func autoPoints(_ rules: Rules, _ sid: String) -> [String: Double] {
        let esc = rules.escalation, sb = rules.streakBonus
        var out: [String: Double] = [:]
        let resets = recharges.filter { $0.studentIds.contains(sid) }.sorted { ($0.start, $0.time, $0.id) < ($1.start, $1.time, $1.id) }
        var round: String? = nil, streak = 0, total = 0, run = 0, plag = 0
        // 最近几次有记录的作业是否未交（间断未交平衡用），每轮清空
        var recent: [Bool] = []
        func record(_ m: Bool) { recent.append(m); while recent.count > max(1, esc.gapWindow) { recent.removeFirst() } }
        for a in assignments.sorted(by: { ($0.createdAt, $0.id) < ($1.createdAt, $1.id) }) {
            let s = a.status(sid)
            let r = resets.last { $0.counts(a.createdAt) }?.id
            if r != round { round = r; streak = 0; total = 0; run = 0; plag = 0; recent = [] }
            switch s {
            case .MISSING:
                streak += 1; total += 1; run = 0
                record(true)
                let n = esc.countOf(streak: streak, recent: recent.filter { $0 }.count)
                let base = esc.enabled ? -esc.penalty(streak: n, total: total) : rules.of(s)
                // 抄袭与复审不合格可以叠加：两个倍数相乘
                let plagged = rules.plagiarism.enabled && a.plagiarized(sid)
                if plagged { plag += 1 }
                let f = plagged ? rules.plagiarism.factor(plag) : 1
                let rv = rules.review.enabled && a.failedReview(sid) ? rules.review.multiplier : 1
                out[a.id] = rules.pt(base * f * rv)
            case .SUBMITTED, .EXCELLENT, .LATE:
                streak = 0
                record(false)
                let counts = s != .LATE || sb.lateCounts
                run = counts ? run + 1 : 0
                let bonus = sb.enabled && counts && run > sb.after ? sb.bonus : 0
                out[a.id] = rules.pt(rules.of(s)) + rules.pt(bonus)
            default: out[a.id] = rules.pt(rules.of(s))
            }
        }
        return out
    }

    /// 当前有效的组长：组长须仍在该组
    func leaderOf(_ group: String) -> Student? {
        guard let id = groupLeaders[group] else { return nil }
        return students.first { $0.id == id && ($0.group.isEmpty ? "未分组" : $0.group) == group }
    }

    /// 某次作业所在的段
    static func periodKey(_ lr: LeaderRules, _ a: Assignment) -> (String, String) {
        if lr.period == "assignment" { return (a.id, "「\(a.title)」") }
        return periodKeyAt(lr.period, a.date)
    }
    static func periodKeyAt(_ period: String, _ date: Date) -> (String, String) {
        let cal = Calendar.current
        var d = cal.startOfDay(for: date)
        let y = cal.component(.year, from: d), m = cal.component(.month, from: d), day = cal.component(.day, from: d)
        if period == "day" { return ("\(y)-\(m)-\(day)", "\(m)月\(day)日") }
        if period == "month" { return ("\(y)-\(m)", "\(y)年\(m)月") }
        let wd = cal.component(.weekday, from: d) // 周日 = 1
        d = cal.date(byAdding: .day, value: -((wd + 5) % 7), to: d)!
        let y2 = cal.component(.year, from: d), m2 = cal.component(.month, from: d), d2 = cal.component(.day, from: d)
        let e = cal.date(byAdding: .day, value: 6, to: d)!
        return ("\(y2)-\(m2)-\(d2)", "\(m2)/\(d2)–\(cal.component(.month, from: e))/\(cal.component(.day, from: e)) 这周")
    }

    /// 全班所有组长的每段奖惩：组长 id → 各段（旧 → 新）
    func leaderPeriods(_ rules: Rules) -> [String: [LeaderPeriod]] {
        let lr = rules.leader
        guard lr.enabled else { return [:] }
        var groups: [String: [Student]] = [:]
        for s in students where !s.group.isEmpty { groups[s.group, default: []].append(s) }
        groups = groups.filter { $0.value.count >= 2 }
        var leaders: [String: Student] = [:]
        for g in groups.keys { if let l = leaderOf(g) { leaders[g] = l } }
        guard !leaders.isEmpty else { return [:] }
        var auto: [String: [String: Double]] = [:]
        for ms in groups.values { for m in ms { auto[m.id] = autoPoints(rules, m.id) } }
        let sorted = assignments.sorted { ($0.createdAt, $0.id) < ($1.createdAt, $1.id) }
        var order: [String] = []; var buckets: [String: (String, [Assignment])] = [:]
        for a in sorted { let (k, l) = SchoolClass.periodKey(lr, a); if buckets[k] == nil { order.append(k); buckets[k] = (l, []) }; buckets[k]!.1.append(a) }
        var resetsOf: [String: [ScoreRecharge]] = [:]
        for l in leaders.values { resetsOf[l.id] = recharges.filter { $0.studentIds.contains(l.id) }.sorted { ($0.start, $0.time, $0.id) < ($1.start, $1.time, $1.id) } }
        var round: [String: String?] = [:], prevD: [String: Double] = [:], lastSub: [String: Double] = [:], run: [String: Int] = [:]
        var out: [String: [LeaderPeriod]] = [:]
        func f1(_ x: Double) -> String { fmtNum(x) }
        for key in order {
            let (label, list) = buckets[key]!
            var per: [String: Double] = [:]
            for (g, ms) in groups {
                let active = ms.contains { m in list.contains { a in let k = a.status(m.id); return k != .NONE && k != .LEAVE } }
                if !active { continue }
                var total = 0.0
                for m in ms { for a in list { let p = auto[m.id]?[a.id] ?? 0; if p < 0 { total -= p } } }
                per[g] = total / Double(ms.count)
            }
            if per.isEmpty { continue }
            let vals = Array(per.values)
            let avg = vals.reduce(0, +) / Double(vals.count)
            let best = vals.min()!, worst = vals.max()!
            let first = list.first!.createdAt, last = list.last!.createdAt
            for g in per.keys.sorted() {
                guard let leader = leaders[g], let d = per[g] else { continue }
                let r = resetsOf[leader.id]!.last { $0.counts(first) }?.id
                if round[g] == nil || round[g]! != r { round[g] = .some(r); prevD[g] = nil; lastSub[g] = nil; run[g] = nil }
                var parts: [(String, Double)] = []
                if d <= 1e-9 { parts.append(("全组零扣分", lr.clean)) }
                else if d <= avg * 0.5 { parts.append(("人均扣分 \(f1(d))，不到各组平均 \(f1(avg)) 的一半", lr.low)) }
                else if d <= avg { parts.append(("人均扣分 \(f1(d))，不高于各组平均 \(f1(avg))", lr.below)) }
                else if d <= avg * 1.5 { parts.append(("人均扣分 \(f1(d))，高于各组平均 \(f1(avg))", lr.above)) }
                else if d <= avg * 2 { parts.append(("人均扣分 \(f1(d))，超过各组平均 1.5 倍", lr.high)) }
                else { parts.append(("人均扣分 \(f1(d))，超过各组平均 2 倍", lr.veryHigh)) }
                if vals.count >= 3 && best < worst {
                    if d == best && vals.filter({ $0 == best }).count == 1 { parts.append(("人均扣分各组最少", lr.top)) }
                    if d == worst && vals.filter({ $0 == worst }).count == 1 { parts.append(("人均扣分各组最多", lr.bottom)) }
                }
                if let p = prevD[g] {
                    if p > 0 && d <= p * 0.7 { parts.append(("比上一段少（\(f1(p)) → \(f1(d))）", lr.progress)) }
                    else if d >= p * 1.5 && d - p >= 2 { parts.append(("比上一段多（\(f1(p)) → \(f1(d))）", lr.regress)) }
                }
                let sub = parts.reduce(0) { $0 + $1.1 }
                var k = 0
                if sub != 0, let ps = lastSub[g], ps != 0, (ps > 0) == (sub > 0) { k = run[g] ?? 1 }
                if lr.streakMax > 0 && k > 0 { parts.append((sub > 0 ? "连续 \(k + 1) 段表现好" : "连续 \(k + 1) 段扣分偏多", (sub > 0 ? 1 : -1) * Double(min(k, lr.streakMax)) * lr.streakStep)) }
                run[g] = k > 0 ? k + 1 : 1; lastSub[g] = sub; prevD[g] = d
                let total = parts.reduce(0) { $0 + $1.1 }
                if lr.cap > 0 && abs(total) > lr.cap { parts.append(("每段最多 ±\(f1(lr.cap))", (total > 0 ? lr.cap : -lr.cap) - total)) }
                let capped = parts.reduce(0) { $0 + $1.1 }
                if applyMode(lr.mode, capped) != capped { parts.append((lr.mode == "plus" ? "仅加分，不扣分" : "仅扣分，不加分", -capped)) }
                out[leader.id, default: []].append(LeaderPeriod(group: g, key: key, label: label, time: last, perCapita: d, parts: parts))
            }
        }
        return out
    }

    /// 全班每一段的小组排名（旧 → 新）
    func groupRankPeriods(_ rules: Rules) -> [RankPeriod] {
        let gr = rules.groupRank
        guard gr.enabled else { return [] }
        var groups: [String: [Student]] = [:]
        for s in students where !s.group.isEmpty { groups[s.group, default: []].append(s) }
        groups = groups.filter { $0.value.count >= 2 }
        guard groups.count >= 2 else { return [] }
        var auto: [String: [String: Double]] = [:]
        for ms in groups.values { for m in ms { auto[m.id] = autoPoints(rules, m.id) } }
        let sorted = assignments.sorted { ($0.createdAt, $0.id) < ($1.createdAt, $1.id) }
        var order: [String] = []; var buckets: [String: (String, [Assignment])] = [:]
        for a in sorted {
            let (k, l) = gr.period == "assignment" ? (a.id, "「\(a.title)」") : SchoolClass.periodKeyAt(gr.period, a.date)
            if buckets[k] == nil { order.append(k); buckets[k] = (l, []) }
            buckets[k]!.1.append(a)
        }
        var examsBy: [String: [Exam]] = [:]
        if gr.period != "assignment" { for e in analysisExams { examsBy[SchoolClass.periodKeyAt(gr.period, Date(timeIntervalSince1970: Double(e.date) / 1000)).0, default: []].append(e) } }
        var out: [RankPeriod] = []
        for key in order {
            let (label, list) = buckets[key]!
            var raws: [(g: String, rate: Double, deduct: Double, exc: Double, exam: Double?)] = []
            for g in groups.keys.sorted() {
                let ms = groups[g]!
                var st: [HwStatus] = []
                for a in list { for m in ms { let k = a.status(m.id); if k != .NONE && k != .LEAVE { st.append(k) } } }
                if st.isEmpty { continue }
                var ded = 0.0
                for m in ms { for a in list { let p = auto[m.id]?[a.id] ?? 0; if p < 0 { ded -= p } } }
                var ex: [Double] = []
                for e in examsBy[key] ?? [] { for m in ms { if let v = e.scores[m.id] { ex.append(v / (e.fullScore > 0 ? e.fullScore : 100)) } } }
                let handed = st.filter { $0 == .SUBMITTED || $0 == .EXCELLENT || $0 == .LATE }.count
                raws.append((g, Double(handed) / Double(st.count), ded / Double(ms.count), Double(st.filter { $0 == .EXCELLENT }.count) / Double(st.count), ex.isEmpty ? nil : ex.reduce(0, +) / Double(ex.count)))
            }
            if raws.count < 2 { continue }
            let maxD = raws.map(\.deduct).max()!
            let allExam = raws.allSatisfy { $0.exam != nil }
            let scored: [(String, Double, Double)] = raws.map { r in
                let dn = maxD <= 1e-9 ? 1 : 1 - r.deduct / maxD
                var num = gr.wRate * r.rate + gr.wDeduct * dn + gr.wExcellent * r.exc
                var den = gr.wRate + gr.wDeduct + gr.wExcellent
                if allExam, let x = r.exam { num += gr.wExam * x; den += gr.wExam }
                return (r.g, ((den <= 0 ? 0 : num / den) * 10000).rounded() / 10000, 0)
            }
            func rankOf(_ sc: Double) -> Int { 1 + scored.filter { $0.1 > sc }.count }
            let worst = scored.map { rankOf($0.1) }.max()!
            let standings = scored.map { x -> GroupStanding in let rk = rankOf(x.1); return GroupStanding(group: x.0, rank: rk, score: x.1, points: gr.points(rank: rk, worst: worst, n: scored.count)) }
                .sorted { ($0.rank, $0.group) < ($1.rank, $1.group) }
            out.append(RankPeriod(key: key, label: label, time: list.last!.createdAt, standings: standings))
        }
        return out
    }

    /// 全班当前的信誉分：学生 id → 结果
    func creditAll(at: Int64 = nowMillis()) -> [String: CreditResult] {
        let newest = assignments.filter { $0.createdAt <= at }.sorted { ($0.createdAt, $0.id) > ($1.createdAt, $1.id) }
        let ex = analysisExams.filter { $0.date <= at }.sorted { ($0.date, $0.id) < ($1.date, $1.id) }
        return Dictionary(uniqueKeysWithValues: students.map { ($0.id, Credit.one($0.id, newest: newest, exams: ex)) })
    }
    /// 已命名、2 人以上的小组
    var creditGroups: [String: [Student]] {
        var g: [String: [Student]] = [:]
        for s in students where !s.group.isEmpty { g[s.group, default: []].append(s) }
        return g.filter { $0.value.count >= 2 }
    }
    /// 全班每一段的信誉分加减分（旧 → 新）；个人、小组都“不计入”时为空
    func creditPeriods(_ rules: Rules) -> [CreditPeriod] {
        let cr = rules.credit
        guard cr.active, !students.isEmpty else { return [] }
        let sorted = assignments.sorted { ($0.createdAt, $0.id) < ($1.createdAt, $1.id) }
        var order: [String] = []; var buckets: [String: (String, [Assignment])] = [:]
        for a in sorted {
            let (k, l) = cr.period == "assignment" ? (a.id, "「\(a.title)」") : SchoolClass.periodKeyAt(cr.period, a.date)
            if buckets[k] == nil { order.append(k); buckets[k] = (l, []) }
            buckets[k]!.1.append(a)
        }
        let groups = creditGroups
        let examsSorted = analysisExams.sorted { ($0.date, $0.id) < ($1.date, $1.id) }
        var out: [CreditPeriod] = []
        for key in order {
            let (label, list) = buckets[key]!
            let t = list.last!.createdAt
            let active = Set(students.filter { s in list.contains { a in let k = a.status(s.id); return k != .NONE && k != .LEAVE } }.map(\.id))
            if active.isEmpty { continue }
            let newest = Array(sorted.filter { $0.createdAt <= t }.reversed())
            let ex = examsSorted.filter { $0.date <= t }
            var res: [String: CreditResult] = [:]
            for s in students { res[s.id] = Credit.one(s.id, newest: newest, exams: ex) }
            var personal: [String: (Double, Double)] = [:]
            if cr.personal != "none" { for id in active { if let r = res[id], r.hasData { personal[id] = (r.index, CreditRules.apply(cr.personal, cr.band(r.index))) } } }
            var gs: [String: (Double, Double)] = [:]
            if cr.group != "none" {
                for (g, ms) in groups where ms.contains(where: { active.contains($0.id) }) {
                    let vals = ms.compactMap { res[$0.id] }.filter(\.hasData).map(\.index)
                    if vals.isEmpty { continue }
                    let x = r2(vals.reduce(0, +) / Double(vals.count))
                    gs[g] = (x, CreditRules.apply(cr.group, cr.band(x)))
                }
            }
            out.append(CreditPeriod(key: key, label: label, time: t, personal: personal, groups: gs))
        }
        return out
    }

    func stats(_ rules: Rules) -> [Stat] {
        let last = latestRecharges()
        var manual: [String: Double] = [:]
        for e in scores where last[e.studentId].map({ $0.counts(e.time) }) ?? true { manual[e.studentId, default: 0] += rules.pt(e.delta) }
        let lead = leaderPeriods(rules)
        let periods = groupRankPeriods(rules)
        let credit = creditPeriods(rules)
        let creditGroupsNow = creditGroups
        return students.map { st in
            let r = last[st.id]
            var auto = 0.0, sub = 0, late = 0, miss = 0
            let pts = autoPoints(rules, st.id)
            for a in assignments {
                let s = a.status(st.id)
                if r.map({ $0.counts(a.createdAt) }) ?? true { auto += pts[a.id] ?? rules.of(s) }
                if s == .SUBMITTED || s == .EXCELLENT { sub += 1 }
                if s == .LATE { late += 1 }
                if s == .MISSING { miss += 1 }
            }
            var examPts = 0.0
            for e in exams where r.map({ $0.counts(e.date) }) ?? true { examPts += rules.pt(rules.exam.delta(e, st.id)) }
            var leaderPts = (lead[st.id] ?? []).filter { p in r.map { $0.counts(p.time) } ?? true }.reduce(0.0) { $0 + rules.pt($1.points) }
            if !st.group.isEmpty {
                for p in periods where r.map({ $0.counts(p.time) }) ?? true {
                    if let g = p.standings.first(where: { $0.group == st.group }) { leaderPts += rules.pt(g.points) }
                }
            }
            for p in credit where r.map({ $0.counts(p.time) }) ?? true {
                if let v = p.personal[st.id] { leaderPts += rules.pt(v.1) }
                if creditGroupsNow[st.group] != nil, let v = p.groups[st.group] { leaderPts += rules.pt(v.1) }
            }
            return Stat(student: st, score: rules.shown((r?.target ?? rules.initial) + auto + (manual[st.id] ?? 0) + examPts + leaderPts), submitted: sub, late: late, missing: miss)
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

// MARK: - 抄袭关系（与安卓 Plagiarism.kt、网页 analytics.js 同算法）

/// 抄袭的提供者不明时，提供者列表里放这个占位 id（不是任何同学），与安卓、网页一致
let unknownProviderId = "?"

/// 这次作业里这位同学的抄袭关系（“抄袭 甲” / “抄袭（提供者不明）” / “提供给 乙 抄”）
func plagiarismLine(_ c: SchoolClass, _ a: Assignment, _ sid: String) -> String {
    func names(_ ids: [String]) -> String { ids.compactMap { id in c.students.first { $0.id == id }?.name }.joined(separator: "、") }
    let from = a.copiedFrom(sid).filter { $0 != unknownProviderId }, by = a.copiedBy(sid)
    let copied: String? = !from.isEmpty ? "抄袭 \(names(from))" : a.unknownProvider(sid) ? "抄袭（提供者不明）" : nil
    return [copied, by.isEmpty ? nil : "提供给 \(names(by)) 抄"].compactMap { $0 }.joined(separator: "；")
}

/// unknown：提供者不明（这时 providers 可能为空）
struct PlagiarismCase {
    var assignment: Assignment; var receiver: Student; var providers: [Student]; var unknown = false
    var providerText: String { (providers.map(\.name) + (unknown ? ["提供者不明"] : [])).joined(separator: "、") }
}
struct PlagiarismEdge { var provider: Student; var receiver: Student; var assignments: [Assignment]; var count: Int { assignments.count } }
struct PlagiarismNode {
    var student: Student; var copied: Int; var provided: Int; var points: Double; var roundTimes: Int
    /// 单次作业的图里：这次扣分的倍数（全部作业汇总时为 0）
    var factor: Double = 0
    var total: Int { copied + provided }
    var role: String { copied > 0 && provided > 0 ? "既抄又提供" : copied > 0 ? "抄袭者" : "提供者" }
}
struct PlagiarismReport {
    var cases: [PlagiarismCase]; var edges: [PlagiarismEdge]; var nodes: [PlagiarismNode]; var clusters: [[Student]]
    var copiers: Int { nodes.filter { $0.copied > 0 }.count }
    var providers: Int { nodes.filter { $0.provided > 0 }.count }
    var points: Double { nodes.reduce(0) { $0 + $1.points } }
}

extension SchoolClass {
    /// 抄袭记录、提供者 → 抄袭者的关系边、每人的次数与扣分、抄袭圈
    /// 有抄袭记录的作业，最新的在前（关系图按作业切换用）
    func plagiarismAssignments() -> [Assignment] {
        let ids = Set(students.map(\.id))
        return assignments.filter { a in a.plagiarism.keys.contains { r in ids.contains(r) && a.copiedFrom(r).contains { $0 == unknownProviderId || (ids.contains($0) && a.status($0) == .MISSING) } } }
            .sorted { ($0.createdAt, $0.id) > ($1.createdAt, $1.id) }
    }

    /// 这次作业这位同学的计分说明，如“本次 -8（抄袭第 1 次 ×2 × 复审不合格 ×2 = ×4）”
    func assignmentPointNote(_ rules: Rules, _ aid: String, _ sid: String) -> String {
        guard let a = assignments.first(where: { $0.id == aid }), let v = autoPoints(rules, sid)[aid], v != 0 else { return "" }
        var parts: [String] = []; var total = 1.0
        if rules.plagiarism.enabled, let n = plagiarismTimes(rules, sid)[aid] { let f = rules.plagiarism.factor(n); parts.append("抄袭第 \(n) 次 ×\(fmtNum(f))"); total *= f }
        if rules.review.enabled && a.failedReview(sid) { parts.append("复审不合格 ×\(fmtNum(rules.review.multiplier))"); total *= rules.review.multiplier }
        let why = parts.count >= 2 ? parts.joined(separator: " × ") + " = ×\(fmtNum(total))" : parts.first ?? ""
        return "本次 \(v > 0 ? "+" : "")\(fmtNum(v))" + (why.isEmpty ? "" : "（\(why)）")
    }

    /// 某位同学每次涉及抄袭是本轮第几次（作业 id → 次数），与计分同一口径
    func plagiarismTimes(_ rules: Rules, _ sid: String) -> [String: Int] {
        var out: [String: Int] = [:]
        guard rules.plagiarism.enabled else { return out }
        let resets = recharges.filter { $0.studentIds.contains(sid) }.sorted { ($0.start, $0.time, $0.id) < ($1.start, $1.time, $1.id) }
        var round: String? = nil, plag = 0
        for a in assignments.sorted(by: { ($0.createdAt, $0.id) < ($1.createdAt, $1.id) }) {
            let r = resets.last { $0.counts(a.createdAt) }?.id
            if r != round { round = r; plag = 0 }
            if a.status(sid) == .MISSING && a.plagiarized(sid) { plag += 1; out[a.id] = plag }
        }
        return out
    }

    /// only 不为空时只看这一次作业（每次作业各有一张关系图）
    func plagiarismReport(_ rules: Rules, only: String? = nil) -> PlagiarismReport {
        let byId = Dictionary(students.map { ($0.id, $0) }, uniquingKeysWith: { a, _ in a })
        let sorted = assignments.sorted { ($0.createdAt, $0.id) < ($1.createdAt, $1.id) }.filter { only == nil || $0.id == only }
        var cases: [PlagiarismCase] = []
        var edgeKeys: [String] = []; var edges: [String: PlagiarismEdge] = [:]
        for a in sorted {
            for rid in a.plagiarism.keys.sorted() {
                guard let r = byId[rid] else { continue }
                let ps = a.copiedFrom(rid).filter { a.status($0) == .MISSING }.compactMap { byId[$0] }
                let unknown = a.unknownProvider(rid)
                if ps.isEmpty && !unknown { continue }
                cases.append(PlagiarismCase(assignment: a, receiver: r, providers: ps, unknown: unknown))
                for p in ps {
                    let k = p.id + "\u{0}" + r.id
                    if edges[k] == nil { edges[k] = PlagiarismEdge(provider: p, receiver: r, assignments: []); edgeKeys.append(k) }
                    edges[k]!.assignments.append(a)
                }
            }
        }
        var involved: [String] = []
        for k in edgeKeys { for id in [edges[k]!.provider.id, edges[k]!.receiver.id] where !involved.contains(id) { involved.append(id) } }
        // 提供者不明的抄袭者也算涉及（关系图里单独一个点）
        for k in cases where k.unknown && !involved.contains(k.receiver.id) { involved.append(k.receiver.id) }
        let last = latestRecharges()
        let nodes = involved.compactMap { id -> PlagiarismNode? in
            guard let s = byId[id] else { return nil }
            let copied = sorted.filter { a in a.copiedFrom(id).contains { $0 == unknownProviderId || a.status($0) == .MISSING } }.count
            let provided = sorted.filter { !$0.copiedBy(id).isEmpty }.count
            let pts = autoPoints(rules, id)
            let list = sorted.filter { $0.plagiarized(id) }
            let sum = list.reduce(0) { $0 + (pts[$1.id] ?? 0) }
            if let only {
                let n = plagiarismTimes(rules, id)[only] ?? 0
                return PlagiarismNode(student: s, copied: copied, provided: provided, points: sum, roundTimes: n, factor: n > 0 ? rules.plagiarism.factor(n) : 0)
            }
            return PlagiarismNode(student: s, copied: copied, provided: provided, points: sum,
                                  roundTimes: list.filter { a in last[id].map { $0.counts(a.createdAt) } ?? true }.count)
        }.sorted { x, y in x.total != y.total ? x.total > y.total : x.points != y.points ? x.points < y.points : compareStudentNumbers(x.student.no, y.student.no) < 0 }
        var adj: [String: Set<String>] = [:]
        for e in edges.values { adj[e.provider.id, default: []].insert(e.receiver.id); adj[e.receiver.id, default: []].insert(e.provider.id) }
        var seen = Set<String>(); var clusters: [[Student]] = []
        for id in involved where !seen.contains(id) {
            seen.insert(id); var comp: [String] = []; var stack = [id]
            while let x = stack.popLast() { comp.append(x); for y in adj[x] ?? [] where !seen.contains(y) { seen.insert(y); stack.append(y) } }
            clusters.append(comp.compactMap { byId[$0] }.sorted { compareStudentNumbers($0.no, $1.no) < 0 })
        }
        let edgeList = edgeKeys.map { edges[$0]! }.sorted { x, y in x.count != y.count ? x.count > y.count : (x.assignments.last?.createdAt ?? 0) > (y.assignments.last?.createdAt ?? 0) }
        return PlagiarismReport(cases: cases.reversed(), edges: edgeList, nodes: nodes, clusters: clusters.sorted { $0.count > $1.count })
    }
}
