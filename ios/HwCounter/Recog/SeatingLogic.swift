import Foundation

/// 座位表：导入（文字 / 图片识别结果）、自动排列、交换、旋转。与安卓版 SeatingLogic.kt、网页版 seating.js 同一套规则。
enum SeatingLogic {
    /// 图片识别出的一段文字（图片像素坐标，左上角为原点）；charX 为每个字的横向中心（可选）
    struct Piece { var text: String; var x: Double; var y: Double; var w: Double; var h: Double; var charX: [Double]? = nil }

    struct Result { var seating: Seating; var unmatched: [String]; var unseated: [String] }

    private static let empty: Set<String> = ["", "空", "空位", "-", "—", "－", "–", "x", "X", "×", "/", "无", "0"]
    private static let frontWords = ["讲台", "黑板", "讲桌", "前门", "講台"]
    private static func isFront(_ s: String) -> Bool { frontWords.contains { s.contains($0) } }
    private static func canon(_ s: String) -> String { s.uppercased().filter { !$0.isWhitespace } }
    private static func isCJK(_ c: Character) -> Bool { c.unicodeScalars.first.map { (0x4E00...0x9FFF).contains($0.value) } ?? false }
    private static func digitRuns(_ t: [Character]) -> [(Int, String)] {
        var out: [(Int, String)] = [], i = 0
        while i < t.count {
            if t[i].isASCII && t[i].isNumber {
                var j = i; while j < t.count && t[j].isASCII && t[j].isNumber { j += 1 }
                out.append((i, String(t[i..<j]))); i = j
            } else { i += 1 }
        }
        return out
    }
    private static func stripZeros(_ s: String) -> String { let t = String(s.drop { $0 == "0" }); return t.isEmpty ? "0" : t }

    struct Hit { let s: Student; let at: Int; let len: Int }

    struct SeatMatcher {
        var byNo: [String: Student] = [:]
        var byName: [String: Student] = [:]
        var names: [String] = []

        init(_ students: [Student]) {
            for s in students where !s.no.trimmingCharacters(in: .whitespaces).isEmpty { byNo[SeatingLogic.canon(s.no)] = s }
            for s in students where !s.name.isEmpty && byName[s.name] == nil { byName[s.name] = s }
            names = byName.keys.filter { $0.count >= 2 }.sorted { $0.count > $1.count }
        }

        func one(_ tok: String) -> Student? {
            let t = SeatingLogic.canon(tok)
            if t.isEmpty { return nil }
            if let s = byNo[t] { return s }
            if let s = byName[t] { return s }
            if let n = names.first(where: { t.contains($0) }) { return byName[n] }
            for (_, d) in SeatingLogic.digitRuns(Array(t)) {
                if let s = byNo[d] { return s }
                let v = SeatingLogic.stripZeros(d)
                if let e = byNo.first(where: { k, _ in k.allSatisfy { $0.isASCII && $0.isNumber } && SeatingLogic.stripZeros(k) == v }) { return e.value }
            }
            return nil
        }

        func near(_ z: [Character]) -> Student? {
            guard (2...4).contains(z.count) else { return nil }
            let c = names.filter { n in
                let a = Array(n)
                return a.count == z.count && a[0] == z[0] && zip(a, z).filter { $0 != $1 }.count == 1
            }
            return c.count == 1 ? byName[c[0]] : nil
        }

        /// 一段文字里依次出现的所有学生（OCR 把同一排几个人读成一行时用）
        func all(_ text: String) -> [Hit] {
            let t = Array(text)
            var used = [Bool](repeating: false, count: t.count)
            var out: [Hit] = []
            for n in names {
                let w = Array(n)
                guard w.count <= t.count else { continue }
                var i = 0
                while i + w.count <= t.count {
                    if Array(t[i..<(i + w.count)]) == w && (i..<(i + w.count)).allSatisfy({ !used[$0] }) {
                        for k in i..<(i + w.count) { used[k] = true }
                        out.append(Hit(s: byName[n]!, at: i, len: w.count))
                    }
                    i += 1
                }
            }
            for (i, d) in SeatingLogic.digitRuns(t) where !used[i] {
                // “高一(3)班”“第3排”这类不是学号
                let prev: Character? = i > 0 ? t[i - 1] : nil
                let next: Character? = i + d.count < t.count ? t[i + d.count] : nil
                if let p = prev, "(（第".contains(p) { continue }
                if let n = next, ")）班级年排列组".contains(n) { continue }
                if let s = one(d), !out.contains(where: { $0.s.id == s.id }) { out.append(Hit(s: s, at: i, len: d.count)) }
            }
            if out.isEmpty {
                let z = t.filter(SeatingLogic.isCJK)
                if let f = near(z), let first = z.first { out.append(Hit(s: f, at: t.firstIndex(of: first) ?? 0, len: z.count)) }
            }
            return out.sorted { $0.at < $1.at }
        }
    }

    static func gridToSeating(_ grid: [[String?]], frontAtTop: Bool) -> Seating {
        let cols = max(1, grid.map(\.count).max() ?? 1)
        var seats: [String: Int] = [:]
        for (i, r) in grid.enumerated() { for (j, id) in r.enumerated() { if let id { seats[id] = i * cols + j } } }
        return Seating(rows: max(1, grid.count), cols: cols, seats: seats, frontAtTop: frontAtTop)
    }

    static func toGrid(_ s: Seating, _ students: [Student]) -> [[String?]] {
        let ok = Set(students.map(\.id))
        var grid = [[String?]](repeating: [String?](repeating: nil, count: max(1, s.cols)), count: max(1, s.rows))
        for (id, k) in s.seats where ok.contains(id) && s.cols > 0 {
            let r = k / s.cols, c = k % s.cols
            if r >= 0 && r < s.rows && grid[r][c] == nil { grid[r][c] = id }
        }
        return grid
    }

    private static func splitLine(_ line: String) -> [String] {
        let seps = CharacterSet(charactersIn: "\t,，;；|")
        if line.unicodeScalars.contains(where: { seps.contains($0) }) { return line.components(separatedBy: seps) }
        return line.split(whereSeparator: { $0.isWhitespace }).map(String.init)
    }

    /// 文字座位表：每行一排，单元格用 Tab/逗号/空格分隔，空位写“空”；有“讲台”那一行时据此判断讲台在上还是在下。也支持 JSON 二维数组
    static func parseText(_ text: String, _ students: [Student]) throws -> Result {
        let t = text.replacingOccurrences(of: "\u{FEFF}", with: "").trimmingCharacters(in: .whitespacesAndNewlines)
        if t.hasPrefix("[") || t.hasPrefix("{") {
            let root = try JSONSerialization.jsonObject(with: Data(t.utf8))
            let arr: [Any]? = (root as? [Any]) ?? ["seats", "rows", "座位表", "座位", "grid"].lazy.compactMap { (root as? [String: Any])?[$0] as? [Any] }.first
            guard let arr else { throw NSError(domain: "seat", code: 1, userInfo: [NSLocalizedDescriptionKey: "JSON 里没有找到座位二维数组（seats）"]) }
            let rows: [[String]] = arr.map { r in
                if let a = r as? [Any] { return a.map { x in (x as? String) ?? (x as? NSNumber)?.stringValue ?? "" } }
                return splitLine(String(describing: r))
            }
            let front = ((root as? [String: Any])?["frontAtTop"] as? Bool) ?? true
            return parseRows(rows, frontAtTop: front, students)
        }
        var rows: [[String]] = []
        var frontAtTop = true
        for raw in t.components(separatedBy: .newlines) {
            let line = raw.replacingOccurrences(of: "\\s+$", with: "", options: .regularExpression)
            if line.trimmingCharacters(in: .whitespaces).isEmpty { continue }
            let compact = line.filter { !$0.isWhitespace }
            let core = line.filter { !" \t,，;；|-—_=*·【】[]()（）".contains($0) && !$0.isWhitespace }
            if isFront(compact) && core.count <= 4 { frontAtTop = rows.isEmpty; continue }
            rows.append(splitLine(line).map { $0.trimmingCharacters(in: CharacterSet(charactersIn: " \"'")) })
        }
        return parseRows(rows, frontAtTop: frontAtTop, students)
    }

    static func parseRows(_ rows: [[String]], frontAtTop: Bool, _ students: [Student]) -> Result {
        let m = SeatMatcher(students)
        var placed = Set<String>(), unmatched: [String] = []
        let grid: [[String?]] = rows.map { r in
            r.map { cell -> String? in
                if empty.contains(cell.trimmingCharacters(in: .whitespaces)) { return nil }
                guard let s = m.one(cell), !placed.contains(s.id) else { unmatched.append(cell); return nil }
                placed.insert(s.id); return s.id
            }
        }
        return finish(grid, frontAtTop, students, unmatched)
    }

    private static func finish(_ g0: [[String?]], _ frontAtTop: Bool, _ students: [Student], _ unmatched: [String]) -> Result {
        var g = g0
        while let l = g.last, l.allSatisfy({ $0 == nil }) { g.removeLast() }
        while let f = g.first, f.allSatisfy({ $0 == nil }) { g.removeFirst() }
        let cols = g.map(\.count).max() ?? 0
        let grid = g.map { $0 + [String?](repeating: nil, count: cols - $0.count) }
        let seating = gridToSeating(grid, frontAtTop: frontAtTop)
        return Result(seating: seating, unmatched: unmatched, unseated: students.filter { seating.seats[$0.id] == nil }.map(\.id))
    }

    /// 图片识别结果 → 座位表：先定位每段文字里的姓名/学号，再按纵向间隙分排（纠正轻微倾斜）、按横向位置聚成列
    static func fromBoxes(_ pieces: [Piece], _ students: [Student]) -> Result {
        let m = SeatMatcher(students)
        struct P { let id: String; let cx: Double; let cy: Double; let w: Double; let h: Double }
        var pts: [P] = [], front: Double? = nil, unmatched: [String] = []
        for p in pieces {
            let compact = p.text.filter { !$0.isWhitespace }
            if isFront(compact) && compact.count <= 6 { front = p.y + p.h / 2; continue }
            if ["座位表", "座次表", "座位图"].contains(where: { p.text.contains($0) }) { continue }
            let hits = m.all(p.text)
            if hits.isEmpty {
                let t = Array(p.text)
                if t.filter(isCJK).count >= 2 || t.filter({ $0.isASCII && $0.isNumber }).count >= 2 { unmatched.append(p.text.trimmingCharacters(in: .whitespaces)) }
                continue
            }
            let len = Double(max(1, p.text.count))
            for h in hits {
                let cx: Double
                if let xs = p.charX, xs.count >= h.at + h.len { cx = xs[h.at..<(h.at + h.len)].reduce(0, +) / Double(h.len) }
                else { cx = p.x + p.w * ((Double(h.at) + Double(h.len) / 2) / len) }
                pts.append(P(id: h.s.id, cx: cx, cy: p.y + p.h / 2, w: p.w * Double(h.len) / len, h: p.h))
            }
        }
        var seen = Set<String>()
        let pp = pts.filter { seen.insert($0.id).inserted }
        if pp.isEmpty { return Result(seating: Seating(rows: 1, cols: 1, seats: [:], frontAtTop: true), unmatched: unmatched, unseated: students.map(\.id)) }
        func med(_ a: [Double]) -> Double { a.sorted()[a.count / 2] }
        let mh = max(med(pp.map(\.h)), 1), mw = max(med(pp.map(\.w)), 1)
        let slope = estimateSlope(pp.map { ($0.cx, $0.cy) }, mh)
        let rowsOf = cluster(pp.map { $0.cy - slope * $0.cx }, mh * 0.9)
        let colsOf = cluster(pp.map(\.cx), max(mw * 0.75, mh))
        let nr = (rowsOf.max() ?? 0) + 1, nc = (colsOf.max() ?? 0) + 1
        var grid = [[String?]](repeating: [String?](repeating: nil, count: nc), count: nr)
        for (i, q) in pp.enumerated() {
            let r = rowsOf[i]
            var c = colsOf[i]
            if grid[r][c] != nil {
                var best = -1
                for k in grid[r].indices where grid[r][k] == nil && (best < 0 || abs(k - c) < abs(best - c)) { best = k }
                if best < 0 { grid[r].append(nil); best = grid[r].count - 1 }
                c = best
            }
            grid[r][c] = q.id
        }
        var frontAtTop = true
        if let f = front { let ys = pp.map(\.cy); frontAtTop = f <= ((ys.min() ?? 0) + (ys.max() ?? 0)) / 2 }
        return finish(grid, frontAtTop, students, unmatched)
    }

    private static func cluster(_ vals: [Double], _ gap: Double) -> [Int] {
        let idx = vals.indices.sorted { vals[$0] < vals[$1] }
        var out = [Int](repeating: 0, count: vals.count)
        var g = 0, prev = vals[idx[0]]
        for (k, i) in idx.enumerated() {
            if k > 0 && vals[i] - prev > gap { g += 1 }
            out[i] = g; prev = vals[i]
        }
        return out
    }

    private static func estimateSlope(_ p: [(Double, Double)], _ mh: Double) -> Double {
        var s: [Double] = []
        let byX = p.sorted { $0.0 < $1.0 }
        for i in byX.indices {
            var j = i + 1
            while j < byX.count && j < i + 12 {
                let dx = byX[j].0 - byX[i].0, dy = byX[j].1 - byX[i].1
                if dx > mh && abs(dy) < mh * 0.8 { s.append(dy / dx) }
                j += 1
            }
        }
        if s.count < 3 { return 0 }
        s.sort()
        let v = s[s.count / 2]
        return abs(v) < 0.2 ? v : 0
    }

    /// 按学号顺序自动排列（cols 列，一排排坐满）
    static func autoArrange(_ students: [Student], cols: Int, frontAtTop: Bool = true) -> Seating {
        let list = students.sorted { ($0.no.count, $0.no) < ($1.no.count, $1.no) }
        let c = max(1, cols)
        var seats: [String: Int] = [:]
        for (i, s) in list.enumerated() { seats[s.id] = i }
        return Seating(rows: max(1, (list.count + c - 1) / c), cols: c, seats: seats, frontAtTop: frontAtTop)
    }

    /// 讲台在上/下互换：整张表旋转 180°
    static func rotate(_ s: Seating) -> Seating {
        let n = s.rows * s.cols
        var r = s; r.seats = s.seats.mapValues { n - 1 - $0 }; r.frontAtTop.toggle(); return r
    }

    static func swap(_ s: Seating, _ a: Int, _ b: Int) -> Seating {
        var r = s; r.seats = s.seats.mapValues { $0 == a ? b : $0 == b ? a : $0 }; return r
    }

    /// 把某位同学放到座位 k（原来坐在 k 的人变成未入座）
    static func place(_ s: Seating, _ id: String, _ k: Int) -> Seating {
        var r = s; r.seats = s.seats.filter { $0.value != k }; r.seats[id] = k; return r
    }

    static func remove(_ s: Seating, _ id: String) -> Seating { var r = s; r.seats.removeValue(forKey: id); return r }

    static func resize(_ s: Seating, rows: Int, cols: Int) -> Seating {
        let r1 = max(1, rows), c1 = max(1, cols)
        var seats: [String: Int] = [:]
        for (id, k) in s.seats {
            let r = k / s.cols, c = k % s.cols
            if r < r1 && c < c1 { seats[id] = r * c1 + c }
        }
        return Seating(rows: r1, cols: c1, seats: seats, frontAtTop: s.frontAtTop)
    }
}
