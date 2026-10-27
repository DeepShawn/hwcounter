import Foundation

/// 名单导入（JSON / CSV / 粘贴），与安卓版、网页版同规则
enum RosterParser {
    private static let noKeys = ["no", "id", "studentno", "student_no", "studentid", "student_id", "number", "num", "code", "学号", "编号", "考号"]
    private static let nameKeys = ["name", "studentname", "student_name", "姓名", "名字"]
    private static let groupKeys = ["group", "team", "小组", "组", "组别"]
    private static let genderKeys = ["gender", "sex", "性别"]
    private static let listKeys = ["students", "list", "data", "members", "学生", "名单"]
    private static let classKeys = ["classname", "class", "name", "班级", "班级名称"]

    private static func str(_ v: Any) -> String? {
        if let s = v as? String { return s }
        if let n = v as? NSNumber { return n.stringValue }
        return nil
    }
    private static func pick(_ o: [String: Any], _ keys: [String]) -> String? {
        for (k, v) in o where keys.contains(k.lowercased()) {
            if let s = str(v)?.trimmingCharacters(in: .whitespaces), !s.isEmpty { return s }
        }
        return nil
    }
    private static func mk(_ no: String?, _ name: String?, _ group: String = "", _ gender: String = "") -> Student {
        Student(no: no ?? "", name: (name?.isEmpty == false ? name : no) ?? "", group: group, gender: gender)
    }
    private static func partsTo(_ p: [String]) -> Student {
        if p.count == 1 { return mk("", p[0]) }
        let aIsNo = p[0].contains { $0.isNumber }
        return mk(aIsNo ? p[0] : p[1], aIsNo ? p[1] : p[0], p.count > 2 ? p[2] : "")
    }
    private static func splitParts(_ s: String) -> [String] {
        s.components(separatedBy: CharacterSet(charactersIn: ",，\t;； ")).map { $0.trimmingCharacters(in: CharacterSet(charactersIn: "\"")) }.filter { !$0.isEmpty }
    }

    static func parse(_ text: String) throws -> (String?, [Student]) {
        let t = text.trimmingCharacters(in: .whitespacesAndNewlines).replacingOccurrences(of: "\u{FEFF}", with: "")
        var className: String? = nil
        var list: [Student] = []
        if t.hasPrefix("{") || t.hasPrefix("[") {
            let root = try JSONSerialization.jsonObject(with: Data(t.utf8))
            var arr = root as? [Any]
            if arr == nil, let o = root as? [String: Any] {
                if let lk = o.keys.first(where: { listKeys.contains($0.lowercased()) && o[$0] is [Any] }) {
                    arr = o[lk] as? [Any]
                    className = pick(o, classKeys)
                }
            }
            if let arr {
                list = arr.compactMap { el in
                    if let o = el as? [String: Any] {
                        let no = pick(o, noKeys), name = pick(o, nameKeys)
                        return no != nil || name != nil ? mk(no, name, pick(o, groupKeys) ?? "", pick(o, genderKeys) ?? "") : nil
                    }
                    if let a = el as? [Any] { let p = a.compactMap(str); return p.isEmpty ? nil : partsTo(p) }
                    if let s = str(el) { let p = splitParts(s); return p.isEmpty ? nil : partsTo(p) }
                    return nil
                }
            } else if let o = root as? [String: Any] {
                list = o.compactMap { k, v in (v as? String).map { mk(k.trimmingCharacters(in: .whitespaces), $0.trimmingCharacters(in: .whitespaces)) } }
                    .sorted { $0.no < $1.no }
            }
        } else {
            list = t.components(separatedBy: .newlines).map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }
                .map { partsTo(splitParts($0)) }
                .filter { $0.no != "学号" && $0.no.lowercased() != "no" && $0.name != "姓名" }
        }
        for i in list.indices where list[i].no.isEmpty { list[i].no = String(format: "%02d", i + 1) }
        return (className, list)
    }
}
