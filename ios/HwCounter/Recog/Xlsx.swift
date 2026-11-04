import Foundation

/// 极简 XLSX 生成（无依赖）：ZIP 仅存储 + 内联字符串，带表头/状态颜色。与网页版 xlsx.js 同样的样式编号
enum Xlsx {
    enum Style: Int { case normal = 0, header, green, red, yellow, blue, gold }
    struct Cell { var v: String?; var n: Double? = nil; var s: Style = .normal }
    struct Sheet { var name: String; var rows: [[Cell]] }

    static func t(_ v: String, _ s: Style = .normal) -> Cell { Cell(v: v, s: s) }
    static func num(_ v: Int, _ s: Style = .normal) -> Cell { Cell(v: nil, n: Double(v), s: s) }
    static func h(_ v: String) -> Cell { Cell(v: v, s: .header) }

    static func style(_ s: HwStatus) -> Style {
        switch s {
        case .SUBMITTED: return .green
        case .EXCELLENT: return .gold
        case .LATE: return .yellow
        case .MISSING: return .red
        case .LEAVE: return .blue
        case .NONE: return .normal
        }
    }

    private static func esc(_ s: String) -> String {
        var o = ""
        for ch in s.unicodeScalars {
            switch ch {
            case "&": o += "&amp;"
            case "<": o += "&lt;"
            case ">": o += "&gt;"
            case "\"": o += "&quot;"
            default: o.unicodeScalars.append(ch.value < 0x20 && ch != "\n" && ch != "\t" ? " " : ch)
            }
        }
        return o
    }
    private static func col(_ i: Int) -> String {
        var n = i + 1, s = ""
        while n > 0 { let r = (n - 1) % 26; s = String(UnicodeScalar(UInt8(65 + r))) + s; n = (n - 1) / 26 }
        return s
    }
    private static func fill(_ rgb: String) -> String { "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF\(rgb)\"/><bgColor indexed=\"64\"/></patternFill></fill>" }
    private static let styles = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font><font><b/><sz val=\"11\"/><color rgb=\"FFFFFFFF\"/><name val=\"Calibri\"/></font></fonts><fills count=\"7\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill>\(fill("0FA38A"))\(fill("D9F7EC"))\(fill("FFE0E0"))\(fill("FFF3C4"))\(fill("DCE8FF"))</fills><borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs><cellXfs count=\"7\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/><xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\"/><xf numFmtId=\"0\" fontId=\"0\" fillId=\"3\" borderId=\"0\" xfId=\"0\" applyFill=\"1\"/><xf numFmtId=\"0\" fontId=\"0\" fillId=\"4\" borderId=\"0\" xfId=\"0\" applyFill=\"1\"/><xf numFmtId=\"0\" fontId=\"0\" fillId=\"5\" borderId=\"0\" xfId=\"0\" applyFill=\"1\"/><xf numFmtId=\"0\" fontId=\"0\" fillId=\"6\" borderId=\"0\" xfId=\"0\" applyFill=\"1\"/><xf numFmtId=\"0\" fontId=\"0\" fillId=\"5\" borderId=\"0\" xfId=\"0\" applyFill=\"1\"/></cellXfs></styleSheet>"

    static func build(_ sheets: [Sheet]) -> Data {
        var files: [(String, String)] = []
        let x = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
        files.append(("[Content_Types].xml", x + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>" + sheets.indices.map { "<Override PartName=\"/xl/worksheets/sheet\($0 + 1).xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" }.joined() + "</Types>"))
        files.append(("_rels/.rels", x + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>"))
        files.append(("xl/workbook.xml", x + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>" + sheets.enumerated().map { i, s in
            let nm = String(s.name.map { "\\/?*[]:".contains($0) ? " " : $0 }.prefix(31))
            return "<sheet name=\"\(esc(nm))\" sheetId=\"\(i + 1)\" r:id=\"rId\(i + 1)\"/>"
        }.joined() + "</sheets></workbook>"))
        files.append(("xl/_rels/workbook.xml.rels", x + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" + sheets.indices.map { "<Relationship Id=\"rId\($0 + 1)\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet\($0 + 1).xml\"/>" }.joined() + "<Relationship Id=\"rId\(sheets.count + 1)\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/></Relationships>"))
        files.append(("xl/styles.xml", styles))
        for (si, sh) in sheets.enumerated() {
            var body = ""
            for (r, cells) in sh.rows.enumerated() {
                body += "<row r=\"\(r + 1)\">"
                for (c, cell) in cells.enumerated() {
                    let ref = col(c) + "\(r + 1)", st = cell.s == .normal ? "" : " s=\"\(cell.s.rawValue)\""
                    if let n = cell.n { body += "<c r=\"\(ref)\"\(st)><v>\(n == n.rounded() ? String(Int(n)) : String(n))</v></c>" }
                    else if let v = cell.v, !v.isEmpty { body += "<c r=\"\(ref)\"\(st) t=\"inlineStr\"><is><t xml:space=\"preserve\">\(esc(v))</t></is></c>" }
                    else if !st.isEmpty { body += "<c r=\"\(ref)\"\(st)/>" }
                }
                body += "</row>"
            }
            files.append(("xl/worksheets/sheet\(si + 1).xml", x + "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews><sheetData>\(body)</sheetData></worksheet>"))
        }
        return zip(files)
    }

    private static let crcTable: [UInt32] = (0..<256).map { n -> UInt32 in
        var c = UInt32(n)
        for _ in 0..<8 { c = c & 1 != 0 ? 0xEDB88320 ^ (c >> 1) : c >> 1 }
        return c
    }
    private static func crc32(_ d: [UInt8]) -> UInt32 {
        var c: UInt32 = 0xFFFFFFFF
        for b in d { c = crcTable[Int((c ^ UInt32(b)) & 0xFF)] ^ (c >> 8) }
        return c ^ 0xFFFFFFFF
    }
    private static func zip(_ files: [(String, String)]) -> Data {
        var out = [UInt8](), central = [UInt8]()
        func le16(_ v: Int, _ a: inout [UInt8]) { a += [UInt8(v & 0xFF), UInt8((v >> 8) & 0xFF)] }
        func le32(_ v: UInt32, _ a: inout [UInt8]) { a += [UInt8(v & 0xFF), UInt8((v >> 8) & 0xFF), UInt8((v >> 16) & 0xFF), UInt8((v >> 24) & 0xFF)] }
        for (name, content) in files {
            let nb = Array(name.utf8), data = Array(content.utf8), crc = crc32(data), off = UInt32(out.count)
            le32(0x04034b50, &out); le16(20, &out); le16(0x0800, &out); le16(0, &out); le16(0, &out); le16(0, &out)
            le32(crc, &out); le32(UInt32(data.count), &out); le32(UInt32(data.count), &out); le16(nb.count, &out); le16(0, &out)
            out += nb; out += data
            le32(0x02014b50, &central); le16(20, &central); le16(20, &central); le16(0x0800, &central); le16(0, &central); le16(0, &central); le16(0, &central)
            le32(crc, &central); le32(UInt32(data.count), &central); le32(UInt32(data.count), &central); le16(nb.count, &central)
            le16(0, &central); le16(0, &central); le16(0, &central); le16(0, &central); le32(0, &central); le32(off, &central)
            central += nb
        }
        let cenOff = UInt32(out.count), cenSize = UInt32(central.count)
        out += central
        le32(0x06054b50, &out); le16(0, &out); le16(0, &out); le16(files.count, &out); le16(files.count, &out)
        le32(cenSize, &out); le32(cenOff, &out); le16(0, &out)
        return Data(out)
    }

    // ---------------- 统计名单（三端同样的三张表） ----------------
    private static let order: [HwStatus] = [.SUBMITTED, .EXCELLENT, .LATE, .MISSING, .LEAVE, .NONE]
    private static func pct(_ x: Double) -> String { let r = (x * 1000).rounded() / 10; return (r == r.rounded() ? String(Int(r)) : String(r)) + "%" }

    /// 一次作业 / 草稿 / 识别结果：统计汇总 + 分类名单 + 明细
    static func statListSheets(_ c: SchoolClass, title: String, subject: String, date: Date, statuses: [String: String], comments: [String: String] = [:]) -> [Sheet] {
        let t = StatTable.tally(c.students, statuses), n = c.students.count
        var sum: [[Cell]] = [
            [h("班级"), self.t(c.name)], [h("作业"), self.t([subject, title].filter { !$0.isEmpty }.joined(separator: " "))], [h("日期"), self.t(fmtDate(date))],
            [h("应交"), num(t.expected)], [h("已交（含优秀、迟交）"), num(t.handedIn)], [h("交齐率"), self.t(pct(t.rate))], [],
            [h("状态"), h("人数"), h("占比"), h("名单")],
        ]
        for k in order { sum.append([self.t(k.label, style(k)), num(t.count(k)), self.t(n > 0 ? pct(Double(t.count(k)) / Double(n)) : "0%"), self.t((t.lists[k] ?? []).map(\.name).joined(separator: "、"))]) }
        var cat: [[Cell]] = [order.map { h("\($0.label)（\(t.count($0))）") }]
        let height = order.map { t.count($0) }.max() ?? 0
        for i in 0..<height {
            cat.append(order.map { k in
                let l = t.lists[k] ?? []
                guard i < l.count else { return Cell(v: nil) }
                return self.t(l[i].no.isEmpty ? l[i].name : "\(l[i].no) \(l[i].name)", style(k))
            })
        }
        var detail: [[Cell]] = [[h("学号"), h("姓名"), h("小组"), h("状态"), h("备注")]]
        for s in c.students {
            let k = HwStatus(rawValue: statuses[s.id] ?? "") ?? .NONE
            detail.append([self.t(s.no), self.t(s.name), self.t(s.group), self.t(k.label, style(k)), self.t(comments[s.id] ?? "")])
        }
        return [Sheet(name: "统计汇总", rows: sum), Sheet(name: "分类名单", rows: cat), Sheet(name: "明细", rows: detail)]
    }

    /// 全班各次作业：一行一次作业（应交、已交、交齐率、未交/迟交/请假/未登记名单）
    static func classStatSheet(_ c: SchoolClass) -> Sheet {
        var rows: [[Cell]] = [["日期", "科目", "作业", "应交", "已交", "交齐率", "未交人数", "未交名单", "迟交名单", "请假名单", "未登记名单"].map(h)]
        for a in c.assignments.sorted(by: { $0.createdAt < $1.createdAt }) {
            let tl = StatTable.tally(c.students, a.statuses)
            func names(_ k: HwStatus) -> Cell { t((tl.lists[k] ?? []).map(\.name).joined(separator: "、")) }
            rows.append([t(fmtDate(a.date)), t(a.subject), t(a.title), num(tl.expected), num(tl.handedIn),
                         t(pct(tl.rate), tl.rate >= 0.999 ? .green : tl.rate < 0.8 ? .red : .normal), num(tl.count(.MISSING)),
                         names(.MISSING), names(.LATE), names(.LEAVE), names(.NONE)])
        }
        return Sheet(name: "各次统计名单", rows: rows)
    }

    /// 写到临时目录，返回文件地址（用于分享）
    static func file(_ name: String, _ sheets: [Sheet]) -> URL? {
        let safe = String(name.map { "\\/:*?\"<>|".contains($0) ? "_" : $0 })
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("\(safe).xlsx")
        do { try build(sheets).write(to: url); return url } catch { return nil }
    }
}
