import Foundation

enum HitSource: Int { case exact = 0, fuzzy, name
    var label: String { ["学号", "学号≈", "姓名"][rawValue] }
}

struct Hit: Identifiable { let studentId: String; let source: HitSource; let raw: String; var id: String { studentId } }

struct MatchOutput { var hits: [Hit]; var unknown: [String]; var loose: [String] }

/// 把 OCR 文本与名单比对（自动纠正误识、学号嵌在长串里、一位之差模糊匹配、姓名匹配）
struct Matcher {
    let byNo: [String: Student]
    let names: [Student]
    let minLen: Int
    let maxNoLen: Int

    init(roster: [Student], minLen: Int = 4) {
        var m: [String: Student] = [:]
        for s in roster where !s.no.trimmingCharacters(in: .whitespaces).isEmpty { m[TextNorm.canon(s.no)] = s }
        byNo = m
        let digitLens = m.keys.filter { $0.allSatisfy { $0.isASCII && $0.isNumber } }.map { $0.count }
        self.minLen = max(1, min(minLen, digitLens.min() ?? minLen))
        maxNoLen = m.keys.map { $0.count }.max() ?? 8
        names = roster.filter { $0.name.count >= 2 }.sorted { $0.name.count > $1.name.count }
    }

    func match(_ lines: [String]) -> MatchOutput {
        var hits: [String: Hit] = [:]
        var order: [String] = []
        var unknown: [String] = [], loose: [String] = []
        func add(_ h: Hit) {
            if let old = hits[h.studentId] { if old.source.rawValue > h.source.rawValue { hits[h.studentId] = h } }
            else { hits[h.studentId] = h; order.append(h.studentId) }
        }
        let shortIds = maxNoLen <= 3
        let alnum = byNo.keys.filter { $0.contains { !($0.isASCII && $0.isNumber) } }
        for line in lines {
            let norm = TextNorm.normalize(line)
            var matched = false
            for run in Rx.matches(norm, "\\d{\(minLen),}") {
                let r = run.text
                if let st = byNo[r] {
                    let trunc = r.count < maxNoLen && byNo.keys.contains { $0.count > r.count && ($0.hasPrefix(r) || $0.hasSuffix(r)) }
                    add(Hit(studentId: st.id, source: trunc ? .fuzzy : .exact, raw: r)); matched = true; continue
                }
                if let inner = byNo.filter({ $0.key.count >= minLen && $0.key.allSatisfy { $0.isNumber } && r.contains($0.key) }).max(by: { $0.key.count < $1.key.count }),
                   r.count - inner.key.count <= (shortIds ? 1 : 3) {
                    add(Hit(studentId: inner.value.id, source: .exact, raw: r)); matched = true; continue
                }
                if r.count >= 5 {
                    let c = byNo.filter { $0.key.count == r.count && zip($0.key, r).filter { $0 != $1 }.count == 1 }
                    if c.count == 1, let f = c.first { add(Hit(studentId: f.value.id, source: .fuzzy, raw: r)); matched = true; continue }
                }
                if !unknown.contains(r) { unknown.append(r) }
            }
            if !matched {
                for t in Rx.matches(norm, "[A-Za-z0-9?()]+").map(\.text)
                where t.contains(where: { $0.isNumber }) && t.count <= maxNoLen + 1 && t.count >= min(2, maxNoLen) && !unknown.contains(t) {
                    loose.append(t)
                }
            }
            if !alnum.isEmpty {
                let up = TextNorm.canon(line)
                for k in alnum where up.contains(k) { add(Hit(studentId: byNo[k]!.id, source: .exact, raw: k)) }
            }
            let compact = line.filter { !$0.isWhitespace }
            for s in names where compact.contains(s.name) { add(Hit(studentId: s.id, source: .name, raw: s.name)) }
        }
        return MatchOutput(hits: order.compactMap { hits[$0] }, unknown: unknown, loose: loose)
    }
}

