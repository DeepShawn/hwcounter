import Foundation

// ---------- 文本工具（与安卓版 Matcher 同规则） ----------
enum TextNorm {
    static func halfWidth(_ s: String) -> String {
        String(String.UnicodeScalarView(s.unicodeScalars.map { u in
            (0xFF01...0xFF5E).contains(u.value) ? Unicode.Scalar(u.value - 0xFEE0)! : u
        }))
    }
    static func canon(_ s: String) -> String { s.trimmingCharacters(in: .whitespaces).uppercased().replacingOccurrences(of: " ", with: "") }

    private static let confusable: [Character: Character] = [
        "O": "0", "o": "0", "D": "0", "Q": "0", "U": "0", "I": "1", "l": "1", "i": "1", "|": "1", "!": "1", "L": "1", "T": "7",
        "Z": "2", "z": "2", "S": "5", "s": "5", "B": "8", "G": "6", "b": "6", "g": "9", "q": "9", "A": "4",
        "y": "4", "Y": "4", "h": "4", "?": "7", ")": "1", "(": "1",
    ]

    /// 主要由数字组成的 token 才做字形纠正（O→0、l→1 …），并去掉数字间的空格/横线
    static func normalize(_ line: String) -> String {
        let src = halfWidth(line)
        let fixed = Rx.replace(src, pattern: "[A-Za-z0-9|!?()]+") { t in
            let digits = t.filter { $0.isASCII && $0.isNumber }.count
            let fix = t.count >= 3 ? digits * 10 >= t.count * 6 : t.count == 2 ? digits >= 1 : false
            return fix ? String(t.map { confusable[$0] ?? $0 }) : t
        }
        return Rx.replace(fixed, pattern: "(?<=\\d)[\\s\\-_.·]+(?=\\d)") { _ in "" }
    }
}

/// NSRegularExpression 的小封装（按 Character 下标返回结果，方便处理中文）
enum Rx {
    struct M { let start: Int; let end: Int; let text: String }

    static func matches(_ s: String, _ pattern: String) -> [M] {
        guard let re = try? NSRegularExpression(pattern: pattern) else { return [] }
        let ns = s as NSString
        // UTF-16 偏移 → Character 下标
        var map = [Int](repeating: 0, count: ns.length + 1)
        var ci = 0, u = 0
        for ch in s { let n = String(ch).utf16.count; for k in 0..<n { map[u + k] = ci }; u += n; ci += 1 }
        map[ns.length] = ci
        return re.matches(in: s, range: NSRange(location: 0, length: ns.length)).map { r in
            M(start: map[r.range.location], end: map[r.range.location + r.range.length], text: ns.substring(with: r.range))
        }
    }

    static func replace(_ s: String, pattern: String, _ f: (String) -> String) -> String {
        guard let re = try? NSRegularExpression(pattern: pattern) else { return s }
        let ns = s as NSString
        var out = "", last = 0
        for r in re.matches(in: s, range: NSRange(location: 0, length: ns.length)) {
            out += ns.substring(with: NSRange(location: last, length: r.range.location - last))
            out += f(ns.substring(with: r.range))
            last = r.range.location + r.range.length
        }
        out += ns.substring(from: last)
        return out
    }
}

