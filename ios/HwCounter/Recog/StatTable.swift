import Foundation
import Compression

/// 作业草稿：只保存在本机（drafts.json），不进云端快照。
/// 上传时写进作业（只覆盖草稿里改过的学生），然后立即同步到云端；也可以定时上传。
struct Draft: Codable, Identifiable, Equatable {
    var id: String = newId()
    var classId: String
    /// 目标作业；新作业草稿上传时用这个 id 创建
    var assignmentId: String = newId()
    var isNew: Bool = true
    var title: String = ""
    var subject: String = ""
    var createdAt: Int64 = nowMillis()
    var updatedAt: Int64 = nowMillis()
    /// 定时上传时间（毫秒，nil = 不定时）
    var scheduledAt: Int64? = nil
    var statuses: [String: String] = [:]
    var comments: [String: String] = [:]
    /// 创建草稿时作业的样子，用来算“改了谁”
    var baseTitle: String = ""
    var baseSubject: String = ""
    var baseStatuses: [String: String] = [:]
    var baseComments: [String: String] = [:]

    func status(_ sid: String) -> HwStatus { HwStatus(rawValue: statuses[sid] ?? "") ?? .NONE }
}

/// 统计表识别 + 统计 + 草稿合并。与网页版 stats.js、安卓版 StatTable.kt 同一套规则：
/// 老师手上的登记表（Excel / CSV / 粘贴 / 拍照），每行一个学生，后面若干列是 ✓ × 优 迟 假 之类的标记。
enum StatTable {
    private static let tokens: [(HwStatus, [String])] = [
        (.EXCELLENT, ["优秀", "优", "a+", "a", "★", "☆", "⭐", "🌟", "好", "棒", "优+", "优-", "a-"]),
        (.LATE, ["迟交", "迟", "补交", "补", "晚交", "晚", "迟到", "后交"]),
        (.LEAVE, ["请假", "病假", "事假", "假", "病", "免交", "免", "公假"]),
        (.MISSING, ["未交", "没交", "缺交", "不交", "欠交", "未", "缺", "欠", "无", "没", "×", "✗", "✘", "✕", "╳", "x", "n", "no", "否", "0", "f", "false"]),
        (.SUBMITTED, ["已交", "交了", "已完成", "完成", "交", "有", "是", "对", "✓", "✔", "√", "☑", "✅", "v", "y", "yes", "ok", "1", "true", "○", "〇", "o", "良", "良好", "中", "合格", "及格", "b", "b+", "b-", "c", "c+", "c-", "d"]),
        (.NONE, ["", "-", "—", "–", "－", "/", "／", "\\", "空", "?", "？"]),
    ]
    private static let tokenMap: [String: HwStatus] = {
        var m: [String: HwStatus] = [:]
        for (k, l) in tokens { for t in l where m[t] == nil { m[t] = k } }
        return m
    }()
    private static func shortAscii(_ t: String) -> Bool { t.count <= 2 && t.allSatisfy { ($0.isASCII && ($0.isLetter || $0.isNumber)) || $0 == "+" || $0 == "-" } }
    /// 扫描一行 OCR 文字时用的标记（单个拉丁字母/数字太容易误伤，不参与扫描），长的优先
    private static let scan: [[Character]] = tokenMap.keys
        .filter { !$0.isEmpty && !shortAscii($0) && !["true", "false", "yes", "ok"].contains($0) }
        .sorted { $0.count > $1.count }.map { Array($0) }

    /// 全角转半角
    static func half(_ s: String) -> String {
        String(String.UnicodeScalarView(s.unicodeScalars.map { (u: Unicode.Scalar) -> Unicode.Scalar in
            if (0xFF01...0xFF5E).contains(u.value) { return Unicode.Scalar(u.value - 0xFEE0)! }
            if u.value == 0x3000 { return " " }
            return u
        }))
    }

    /// 一个单元格 → 状态；认不出返回 nil（空单元格返回 .NONE）
    static func parseStatus(_ cell: String) -> HwStatus? {
        let t = half(cell).lowercased().filter { !$0.isWhitespace }
        if let k = tokenMap[t] { return k }
        if let k = HwStatus.allCases.first(where: { $0.rawValue.lowercased() == t || $0.label == t }) { return k }
        if t.count <= 6 {
            for k in [HwStatus.LEAVE, .LATE, .EXCELLENT, .MISSING, .SUBMITTED] where scan.contains(where: { tokenMap[String($0)] == k && t.contains(String($0)) }) { return k }
        }
        return nil
    }

    /// 粘贴 / CSV / TSV → 二维数组
    static func splitTable(_ text: String) -> [[String]] {
        var t = text
        if t.hasPrefix("\u{FEFF}") { t.removeFirst() }
        let lines = t.replacingOccurrences(of: "\r\n", with: "\n").replacingOccurrences(of: "\r", with: "\n").components(separatedBy: "\n")
        let tab = lines.contains { $0.contains("\t") }
        let comma = !tab && lines.contains { $0.contains(",") || $0.contains("，") }
        return lines.filter { !$0.trimmingCharacters(in: .whitespaces).isEmpty }.map { line -> [String] in
            let cells: [String]
            if tab { cells = line.components(separatedBy: "\t") }
            else if comma { cells = csvLine(line) }
            else { cells = line.split(whereSeparator: { $0.isWhitespace || ";；|".contains($0) }).map(String.init) }
            return cells.map { $0.trimmingCharacters(in: .whitespaces) }
        }
    }

    private static func csvLine(_ line: String) -> [String] {
        var out: [String] = [], cur = "", q = false
        let a = Array(line); var i = 0
        while i < a.count {
            let ch = a[i]
            if q {
                if ch == "\"" && i + 1 < a.count && a[i + 1] == "\"" { cur.append("\""); i += 1 } else if ch == "\"" { q = false } else { cur.append(ch) }
            } else if ch == "\"" { q = true } else if ch == "," || ch == "，" { out.append(cur); cur = "" } else { cur.append(ch) }
            i += 1
        }
        out.append(cur)
        return out
    }

    struct Column: Equatable { var index: Int; var header: String }
    struct Row: Equatable { var studentId: String; var cells: [HwStatus]; var raw: String }
    struct Result: Equatable { var columns: [Column]; var rows: [Row]; var unmatched: [String]; var missingStudents: [String] }

    private static func normNo(_ x: String) -> String {
        let t = half(x).trimmingCharacters(in: .whitespaces).uppercased()
        if !t.isEmpty && t.allSatisfy({ $0.isASCII && $0.isNumber }) { let s = String(t.drop { $0 == "0" }); return s.isEmpty ? "0" : s }
        return t
    }
    private static func han(_ c: String) -> Bool { c.unicodeScalars.contains { (0x4E00...0x9FFF).contains($0.value) } }

    /// 表格（二维字符串）→ 每个学生每列的状态
    static func recognizeTable(_ rows: [[String]], _ students: [Student]) -> Result {
        let m = SeatingLogic.SeatMatcher(students)
        let width = rows.map(\.count).max() ?? 0
        func cell(_ r: [String], _ j: Int) -> String { j < r.count ? r[j] : "" }
        func exact(_ c0: String) -> Student? {
            let c = c0.trimmingCharacters(in: .whitespaces); if c.isEmpty { return nil }
            return students.first { $0.name == c } ?? students.first { !$0.no.isEmpty && normNo($0.no) == normNo(c) }
        }
        // 1) 学号/姓名列（要求对上不同的学生：一整列“1”会对上 1 号同学，但那是状态列）
        var hitN = [Int](repeating: 0, count: width), hitIds = [Set<String>](repeating: [], count: width)
        for r in rows { for (j, c) in r.enumerated() { if let s = exact(c) { hitN[j] += 1; hitIds[j].insert(s.id) } } }
        let need = max(1, Int((Double(rows.count) * 0.25).rounded(.up)))
        let idCols = (0..<width).filter { hitIds[$0].count >= need && Double(hitIds[$0].count) >= Double(hitN[$0]) * 0.6 }
        // 2) 每行的学生
        let rowStu: [Student?] = rows.map { r in
            for j in idCols { if let s = exact(cell(r, j)) { return s } }
            for j in idCols { let v = cell(r, j); if !v.isEmpty, let s = m.one(v) { return s } }
            if idCols.isEmpty { for c in r where han(c) { if let s = exact(c) ?? m.one(c) { return s } } }
            let joined = r.enumerated().filter { idCols.isEmpty ? han($0.element) : idCols.contains($0.offset) }.map(\.element).joined(separator: " ")
            return m.all(joined).first?.s
        }
        // 3) 状态列
        var statCols: [Int] = []
        for j in 0..<width where !idCols.contains(j) {
            var filled = 0, ok = 0, real = 0
            for (i, r) in rows.enumerated() where rowStu[i] != nil {
                let v = cell(r, j).trimmingCharacters(in: .whitespaces); if v.isEmpty { continue }
                filled += 1
                if let s = parseStatus(v) { ok += 1; if s != .NONE { real += 1 } }
            }
            if real > 0 && Double(ok) >= Double(filled) * 0.6 { statCols.append(j) }
        }
        // 4) 表头
        let headRow = rows.indices.first { i in rowStu[i] == nil && statCols.contains { j in let v = cell(rows[i], j).trimmingCharacters(in: .whitespaces); return !v.isEmpty && parseStatus(v) == nil } } ?? -1
        let columns = statCols.enumerated().map { k, j -> Column in
            let h = headRow >= 0 ? cell(rows[headRow], j).trimmingCharacters(in: .whitespaces) : ""
            return Column(index: j, header: h.isEmpty ? "第\(k + 1)列" : h)
        }
        var out: [Row] = [], unmatched: [String] = [], seen = Set<String>()
        for (i, r) in rows.enumerated() {
            let raw = r.filter { !$0.trimmingCharacters(in: .whitespaces).isEmpty }.joined(separator: " ")
            guard let s = rowStu[i] else { if i != headRow && !raw.isEmpty { unmatched.append(raw) }; continue }
            if seen.contains(s.id) { continue }
            seen.insert(s.id)
            out.append(Row(studentId: s.id, cells: statCols.map { parseStatus(cell(r, $0)) ?? .NONE }, raw: raw))
        }
        return Result(columns: columns, rows: out, unmatched: unmatched, missingStudents: students.filter { !seen.contains($0.id) }.map(\.id))
    }

    /// OCR 的一块文字（图片像素坐标，左上角为原点）
    struct Line: Equatable { var text: String; var x: Double; var y: Double; var h: Double }

    /// OCR 常把表格一行拆成好几块：按纵向位置把同一行的块拼起来（从左到右）
    static func mergeRows(_ lines: [Line]) -> [Line] {
        struct R { var cy: Double; var h: Double; var items: [Line] }
        var rows: [R] = []
        for l in lines.filter({ !$0.text.trimmingCharacters(in: .whitespaces).isEmpty }).sorted(by: { $0.y + $0.h / 2 < $1.y + $1.h / 2 }) {
            let cy = l.y + l.h / 2
            let lh = l.h > 0 ? l.h : 20
            let tol = max(6, min(lh, rows.last?.h ?? 20) * 0.55)
            if var r = rows.last, abs(cy - r.cy) <= tol {
                r.items.append(l); r.cy = (r.cy * Double(r.items.count - 1) + cy) / Double(r.items.count); r.h = max(r.h, l.h)
                rows[rows.count - 1] = r
            } else { rows.append(R(cy: cy, h: lh, items: [l])) }
        }
        return rows.map { r in
            let it = r.items.sorted { $0.x < $1.x }
            return Line(text: it.map { $0.text.trimmingCharacters(in: .whitespaces) }.joined(separator: " "), x: it[0].x, y: r.cy - r.h / 2, h: r.h)
        }
    }

    /// 拍照识别结果 → 同 recognizeTable 的结构（每行先找学生，再找状态标记；一行多个标记 = 多列）
    static func recognizeLines(_ lines: [Line], _ students: [Student]) -> Result {
        let m = SeatingLogic.SeatMatcher(students)
        var out: [Row] = [], unmatched: [String] = [], seen = Set<String>(), width = 0
        for ln in lines.sorted(by: { $0.y < $1.y }) {
            let text = half(ln.text).trimmingCharacters(in: .whitespaces)
            if text.isEmpty { continue }
            let hits = m.all(text)
            if hits.isEmpty { unmatched.append(text); continue }
            let chars = Array(text)
            for (k, h) in hits.enumerated() {
                let end = k + 1 < hits.count ? hits[k + 1].at : chars.count
                let from = min(h.at + h.len, end)
                let cells = scanStatuses(String(chars[from..<end]))
                if seen.contains(h.s.id) { continue }
                seen.insert(h.s.id); width = max(width, cells.count)
                out.append(Row(studentId: h.s.id, cells: cells, raw: String(chars[h.at..<end]).trimmingCharacters(in: .whitespaces)))
            }
        }
        let w = max(1, width)
        for i in out.indices { while out[i].cells.count < w { out[i].cells.append(.NONE) } }
        return Result(columns: (0..<w).map { Column(index: $0, header: "第\($0 + 1)列") }, rows: out, unmatched: unmatched, missingStudents: students.filter { !seen.contains($0.id) }.map(\.id))
    }

    /// 从一段文字里依次找出状态标记
    static func scanStatuses(_ text: String) -> [HwStatus] {
        let t = Array(half(text).lowercased())
        var out: [HwStatus] = [], i = 0
        let skip: Set<Character> = [",", "，", ";", "；", ":", "：", "|", "(", ")", "（", "）", "[", "]", "【", "】", ".", "。", "、"]
        func isW(_ c: Character) -> Bool { (c.isASCII && (c.isLetter || c.isNumber)) || c == "+" || c == "-" }
        while i < t.count {
            if t[i].isWhitespace || skip.contains(t[i]) { i += 1; continue }
            if let tok = scan.first(where: { i + $0.count <= t.count && Array(t[i..<(i + $0.count)]) == $0 }) {
                let s = String(tok)
                if let k = tokenMap[s], k != .NONE || s == "/" || s == "-" || s == "—" { out.append(k) }
                i += tok.count; continue
            }
            if isW(t[i]) {
                var j = i; while j < t.count && isW(t[j]) { j += 1 }
                if let k = tokenMap[String(t[i..<j])], k != .NONE { out.append(k) }
                i = j; continue
            }
            i += 1
        }
        return out
    }

    static func columnStatuses(_ r: Result, _ col: Int) -> [String: String] {
        var m: [String: String] = [:]
        for row in r.rows where col < row.cells.count && row.cells[col] != .NONE { m[row.studentId] = row.cells[col].rawValue }
        return m
    }

    // ---------------- 统计 ----------------
    struct Tally {
        var lists: [HwStatus: [Student]]
        func count(_ s: HwStatus) -> Int { lists[s]?.count ?? 0 }
        var handedIn: Int { count(.SUBMITTED) + count(.EXCELLENT) + count(.LATE) }
        var expected: Int { lists.values.reduce(0) { $0 + $1.count } - count(.LEAVE) }
        var rate: Double { expected <= 0 ? 1 : Double(handedIn) / Double(expected) }
    }

    static func tally(_ students: [Student], _ statuses: [String: String]) -> Tally {
        var l: [HwStatus: [Student]] = [:]
        for k in HwStatus.allCases { l[k] = [] }
        for s in students { l[HwStatus(rawValue: statuses[s.id] ?? "") ?? .NONE, default: []].append(s) }
        return Tally(lists: l)
    }

    // ---------------- 读取 .xlsx ----------------
    struct XlsxError: LocalizedError { var errorDescription: String? }

    static func readXlsx(_ data: Data) throws -> [[String]] {
        let files = try unzip(data) { n in n == "xl/sharedStrings.xml" || n == "xl/workbook.xml" || n == "xl/_rels/workbook.xml.rels" || (n.hasPrefix("xl/worksheets/sheet") && n.hasSuffix(".xml")) }
        if files.isEmpty { throw XlsxError(errorDescription: "不是有效的 .xlsx 文件（老版 .xls 请另存为 .xlsx 或 CSV）") }
        func matches(_ p: String, _ s: String) -> [[String]] {
            guard let re = try? NSRegularExpression(pattern: p) else { return [] }
            let ns = s as NSString
            return re.matches(in: s, range: NSRange(location: 0, length: ns.length)).map { m in
                (0..<m.numberOfRanges).map { m.range(at: $0).location == NSNotFound ? "" : ns.substring(with: m.range(at: $0)) }
            }
        }
        func dec(_ s: String) -> String {
            var t = s.replacingOccurrences(of: "&lt;", with: "<").replacingOccurrences(of: "&gt;", with: ">").replacingOccurrences(of: "&quot;", with: "\"").replacingOccurrences(of: "&apos;", with: "'")
            for m in matches("&#(x?)([0-9a-fA-F]+);", t) {
                if let v = UInt32(m[2], radix: m[1].isEmpty ? 10 : 16), let u = Unicode.Scalar(v) { t = t.replacingOccurrences(of: m[0], with: String(Character(u))) }
            }
            return t.replacingOccurrences(of: "&amp;", with: "&")
        }
        func textOf(_ xml: String) -> String { matches("<t(?:\\s[^>]*)?>([\\s\\S]*?)</t>", xml).map { dec($0[1]) }.joined() }
        let shared = files["xl/sharedStrings.xml"].map { x in matches("<si>([\\s\\S]*?)</si>", x).map { textOf($0[1].replacingOccurrences(of: "<rPh[\\s\\S]*?</rPh>", with: "", options: .regularExpression)) } } ?? []
        var path = "xl/worksheets/sheet1.xml"
        if let wb = files["xl/workbook.xml"], let rels = files["xl/_rels/workbook.xml.rels"], let rid = matches("<sheet\\b[^>]*\\br:id=\"([^\"]+)\"", wb).first?[1] {
            if let rel = matches("<Relationship\\b[^>]*>", rels).map({ $0[0] }).first(where: { $0.contains("Id=\"\(rid)\"") }), let tgt = matches("Target=\"([^\"]+)\"", rel).first?[1] {
                path = tgt.hasPrefix("/") ? String(tgt.dropFirst()) : "xl/" + (tgt.hasPrefix("./") ? String(tgt.dropFirst(2)) : tgt)
            }
        }
        guard let sheet = files[path] ?? files.first(where: { $0.key.hasPrefix("xl/worksheets/") })?.value else { throw XlsxError(errorDescription: "Excel 里没有找到工作表") }
        var rows: [[String]] = []
        for rm in matches("<row\\b[^>]*?(?:/>|>([\\s\\S]*?)</row>)", sheet) {
            var row: [String] = []
            for cm in matches("<c\\b([^>]*?)(?:/>|>([\\s\\S]*?)</c>)", rm[1]) {
                let attrs = cm[1], body = cm[2]
                let ref = matches("\\br=\"([A-Z]+)\\d+\"", attrs).first?[1]
                let type = matches("\\bt=\"(\\w+)\"", attrs).first?[1]
                let v = matches("<v>([\\s\\S]*?)</v>", body).first?[1]
                let value: String
                switch type {
                case "s": value = v.flatMap { Int($0) }.flatMap { $0 < shared.count ? shared[$0] : nil } ?? ""
                case "inlineStr": value = textOf(body)
                case "b": value = v == "1" ? "TRUE" : "FALSE"
                default: value = v.map(dec) ?? ""
                }
                let ci = ref.map { letters in letters.unicodeScalars.reduce(0) { n, ch in n * 26 + Int(ch.value) - 64 } - 1 } ?? row.count
                while row.count < ci { row.append("") }
                if row.count == ci { row.append(value) } else { row[ci] = value }
            }
            while let last = row.last, last.isEmpty { row.removeLast() }
            rows.append(row)
        }
        return rows.filter { $0.contains { !$0.trimmingCharacters(in: .whitespaces).isEmpty } }
    }

    private static func unzip(_ data: Data, want: (String) -> Bool) throws -> [String: String] {
        let u = [UInt8](data)
        func u16(_ p: Int) -> Int { p + 1 < u.count ? Int(u[p]) | Int(u[p + 1]) << 8 : 0 }
        func u32(_ p: Int) -> Int { p + 3 < u.count ? u16(p) | u16(p + 2) << 16 : 0 }
        var eocd = -1
        if u.count >= 22 { var i = u.count - 22; while i >= max(0, u.count - 66000) { if u32(i) == 0x06054b50 { eocd = i; break }; i -= 1 } }
        if eocd < 0 { throw XlsxError(errorDescription: "不是有效的 .xlsx 文件（老版 .xls 请另存为 .xlsx 或 CSV）") }
        let n = u16(eocd + 10); var p = u32(eocd + 16)
        var out: [String: String] = [:]
        for _ in 0..<n {
            guard u32(p) == 0x02014b50 else { break }
            let method = u16(p + 10), csize = u32(p + 20), usize = u32(p + 24)
            let nlen = u16(p + 28), elen = u16(p + 30), clen = u16(p + 32), off = u32(p + 42)
            guard p + 46 + nlen <= u.count else { break }
            let name = String(decoding: u[(p + 46)..<(p + 46 + nlen)], as: UTF8.self)
            p += 46 + nlen + elen + clen
            if !want(name) { continue }
            let start = off + 30 + u16(off + 26) + u16(off + 28)
            guard start + csize <= u.count else { continue }
            let src = Array(u[start..<(start + csize)])
            if method == 0 { out[name] = String(decoding: src, as: UTF8.self) }
            else if method == 8 {
                var dst = [UInt8](repeating: 0, count: max(usize, 1))
                let got = compression_decode_buffer(&dst, dst.count, src, src.count, nil, COMPRESSION_ZLIB)
                if got > 0 { out[name] = String(decoding: dst[0..<got], as: UTF8.self) }
            }
        }
        return out
    }

    // ---------------- 草稿 ----------------
    static func newDraft(_ c: SchoolClass, _ a: Assignment?, title: String = "", subject: String = "", statuses: [String: String] = [:]) -> Draft {
        if let a {
            var d = Draft(classId: c.id, assignmentId: a.id, isNew: false, title: a.title, subject: a.subject, createdAt: a.createdAt,
                          statuses: a.statuses, comments: a.comments, baseTitle: a.title, baseSubject: a.subject, baseStatuses: a.statuses, baseComments: a.comments)
            d.statuses.merge(statuses) { _, n in n }
            return d
        }
        var st: [String: String] = [:]
        for s in c.students where s.longLeave { st[s.id] = HwStatus.LEAVE.rawValue }
        st.merge(statuses) { _, n in n }
        return Draft(classId: c.id, isNew: true, title: title, subject: subject, statuses: st)
    }

    /// 草稿相对创建时改了哪些学生
    static func changes(_ d: Draft) -> [String] {
        var ids: [String] = []
        for k in Array(d.statuses.keys) + Array(d.baseStatuses.keys) + Array(d.comments.keys) + Array(d.baseComments.keys) where !ids.contains(k) { ids.append(k) }
        return ids.filter { (d.statuses[$0] ?? "NONE") != (d.baseStatuses[$0] ?? "NONE") || (d.comments[$0] ?? "") != (d.baseComments[$0] ?? "") }
    }

    /// 把草稿写进数据：只覆盖草稿里改过的学生（期间云端/其它设备的其它登记保留）
    static func apply(_ data: AppData, _ d: Draft) throws -> AppData {
        guard let ci = data.classes.firstIndex(where: { $0.id == d.classId }) else { throw XlsxError(errorDescription: "草稿所属的班级已不存在") }
        var out = data
        if let ai = out.classes[ci].assignments.firstIndex(where: { $0.id == d.assignmentId }) {
            var a = out.classes[ci].assignments[ai]
            for id in changes(d) {
                let v = d.statuses[id] ?? "NONE"
                if v == "NONE" { a.statuses.removeValue(forKey: id) } else { a.statuses[id] = v }
                let t = d.comments[id] ?? ""
                if t.isEmpty { a.comments.removeValue(forKey: id) } else { a.comments[id] = t }
            }
            if d.title != d.baseTitle && !d.title.isEmpty { a.title = d.title }
            if d.subject != d.baseSubject { a.subject = d.subject }
            out.classes[ci].assignments[ai] = a
        } else {
            var a = Assignment(title: d.title.isEmpty ? "未命名作业" : d.title, subject: d.subject, statuses: d.statuses)
            a.id = d.assignmentId; a.createdAt = d.createdAt; a.comments = d.comments
            out.classes[ci].assignments.append(a)
        }
        return out
    }
}
