import Foundation

// 语音登记解析（与安卓版 VoiceParser / 网页版 voice.js 同一套算法）

enum Py {
    static func isHan(_ c: Character) -> Bool {
        guard let u = c.unicodeScalars.first, c.unicodeScalars.count == 1 else { return false }
        return (0x3400...0x9FFF).contains(u.value)
    }

    static let polyphones: [Character: [String]] = [
        "曾": ["zeng", "ceng"], "单": ["shan", "dan", "chan"], "解": ["xie", "jie"], "仇": ["qiu", "chou"], "朴": ["piao", "pu"],
        "区": ["ou", "qu"], "查": ["zha", "cha"], "乐": ["yue", "le"], "尉": ["yu", "wei"], "盖": ["ge", "gai"], "缪": ["miao", "mou", "miu"],
        "覃": ["qin", "tan"], "翟": ["zhai", "di"], "长": ["chang", "zhang"], "行": ["xing", "hang"], "重": ["zhong", "chong"],
        "朝": ["zhao", "chao"], "传": ["chuan", "zhuan"], "都": ["du", "dou"], "角": ["jiao", "jue"], "宿": ["su", "xiu"], "茜": ["qian", "xi"],
        "蔚": ["wei", "yu"], "莘": ["shen", "xin"], "柏": ["bai", "bo"], "晟": ["sheng", "cheng"], "贾": ["jia", "gu"], "秘": ["mi", "bi"],
        "召": ["zhao", "shao"], "种": ["zhong", "chong"], "大": ["da", "dai"], "藏": ["zang", "cang"], "奇": ["qi", "ji"], "会": ["hui", "kuai"],
        "牟": ["mou", "mu"], "沈": ["shen", "chen"], "颉": ["xie", "jie"], "和": ["he", "huo", "hu"], "强": ["qiang", "jiang"],
        "弹": ["dan", "tan"], "还": ["hai", "huan"], "着": ["zhe", "zhuo", "zhao"], "参": ["can", "shen"], "似": ["si", "shi"],
    ]

    private static var cache: [Character: [String]] = [:]
    private static let lock = NSLock()

    /// 一个汉字的全部候选读音（无声调；ü 记作 u）
    static func readings(_ c: Character) -> [String] {
        guard isHan(c) else { return [] }
        lock.lock(); defer { lock.unlock() }
        if let r = cache[c] { return r }
        let m = NSMutableString(string: String(c))
        CFStringTransform(m, nil, kCFStringTransformMandarinLatin, false)
        CFStringTransform(m, nil, kCFStringTransformStripDiacritics, false)
        var all: [String] = []
        let base = (m as String).lowercased().replacingOccurrences(of: "v", with: "u").trimmingCharacters(in: .whitespaces)
        if base.allSatisfy({ $0 >= "a" && $0 <= "z" }) && !base.isEmpty { all.append(base) }
        for p in polyphones[c] ?? [] where !all.contains(p) { all.append(p) }
        cache[c] = all
        return all
    }

    // ---------- 拼音相似度 ----------
    private static let initials = ["zh", "ch", "sh", "b", "p", "m", "f", "d", "t", "n", "l", "g", "k", "h", "j", "q", "x", "r", "z", "c", "s", "y", "w"]
    private static func split(_ sy: String) -> (String, String) {
        let ini = initials.first { sy.hasPrefix($0) } ?? ""
        return (ini, String(sy.dropFirst(ini.count)))
    }
    private static func pair(_ a: String, _ b: String) -> String { a < b ? "\(a)|\(b)" : "\(b)|\(a)" }
    private static let lightIni: Set<String> = ["z|zh", "c|ch", "s|sh", "l|n", "f|h", "l|r"]
    private static let midIni: Set<String> = ["b|p", "d|t", "g|k", "j|zh", "ch|q", "sh|x", "j|z", "c|q", "s|x", "r|y", "|y", "|w", "h|k"]
    private static let lightFin: Set<String> = ["in|ing", "en|eng", "an|ang", "ian|iang", "uan|uang", "ong|un", "eng|ong", "ian|in", "e|o", "o|uo", "ui|uei", "iu|iou", "un|uen"]
    private static let midFin: Set<String> = ["ie|ue", "ei|ui", "ai|ei", "a|e", "en|in", "an|en", "ao|ou", "i|ie", "ing|ong", "iang|uang", "e|ie"]
    private static func iniCost(_ a: String, _ b: String) -> Double { a == b ? 0 : lightIni.contains(pair(a, b)) ? 0.3 : midIni.contains(pair(a, b)) ? 0.55 : 1 }
    private static func finCost(_ a: String, _ b: String) -> Double { a == b ? 0 : lightFin.contains(pair(a, b)) ? 0.3 : midFin.contains(pair(a, b)) ? 0.55 : 1 }

    static func syllable(_ a: String, _ b: String) -> Double {
        if a == b { return 0 }
        let (ia, fa) = split(a), (ib, fb) = split(b)
        return min(1, 0.55 * iniCost(ia, ib) + 0.45 * finCost(fa, fb))
    }
    static func syllableAlt(_ a: [String], _ b: [String]) -> Double {
        var best = 1.0
        for x in a { for y in b { best = min(best, syllable(x, y)) } }
        return best
    }
    static func sequence(_ heard: [[String]], _ name: [[String]]) -> Double {
        let n = heard.count, m = name.count
        var dp = [[Double]](repeating: [Double](repeating: 0, count: m + 1), count: n + 1)
        for i in 0...n { dp[i][0] = Double(i) }
        for j in 0...m { dp[0][j] = Double(j) }
        if n > 0 && m > 0 {
            for i in 1...n { for j in 1...m {
                dp[i][j] = min(dp[i - 1][j] + 1, dp[i][j - 1] + 1, dp[i - 1][j - 1] + syllableAlt(heard[i - 1], name[j - 1]))
            } }
        }
        return dp[n][m]
    }
}

// ---------- 中文数字 ----------
enum CnNum {
    static let digit: [Character: Int] = ["零": 0, "〇": 0, "一": 1, "幺": 1, "二": 2, "两": 2, "三": 3, "四": 4, "五": 5, "六": 6, "七": 7, "八": 8, "九": 9]
    static let numeralChars = Set("零〇一幺二两三四五六七八九十百")
    static let numberSrc = "[一二两三四五六七八九幺]?百(?:零?[一二三四五六七八九]十|零[一二三四五六七八九]|[一二三四五六七八九]十)?[一二三四五六七八九]?|[一二三四五六七八九]?十[一二三四五六七八九]?|[零〇一幺二两三四五六七八九]+"

    static func toNumber(_ s: String) -> String {
        if !s.contains("十") && !s.contains("百") { return s.map { String(digit[$0] ?? 0) }.joined() }
        var total = 0, cur = 0
        for c in s {
            if c == "百" { total += (cur == 0 ? 1 : cur) * 100; cur = 0 }
            else if c == "十" { total += (cur == 0 ? 1 : cur) * 10; cur = 0 }
            else { cur = digit[c] ?? 0 }
        }
        return String(total + cur)
    }

    static func segments(_ s: String) -> [Rx.M] { Rx.matches(s, numberSrc) }

    /// 把中文数字转成阿拉伯数字，并记录每个输出字符对应原文的位置
    static func convert(_ s: [Character]) -> (text: [Character], origin: [Int], originEnd: [Int], positional: [Bool]) {
        var out: [Character] = [], origin: [Int] = [], originEnd: [Int] = [], positional: [Bool] = []
        func put(_ c: Character, _ a: Int, _ b: Int, _ p: Bool) { out.append(c); origin.append(a); originEnd.append(b); positional.append(p) }
        var i = 0
        while i < s.count {
            guard numeralChars.contains(s[i]) else { put(s[i], i, i + 1, false); i += 1; continue }
            var j = i
            while j < s.count && numeralChars.contains(s[j]) { j += 1 }
            if i > 0 && s[i - 1].isASCII && s[i - 1].isNumber { put(" ", i, i, false) }
            let run = String(s[i..<j])
            var first = true
            for seg in segments(run) {
                let a = i + seg.start, b = i + seg.end
                let p = seg.text.contains("十") || seg.text.contains("百")
                if !first { put(" ", a, a, false) }
                for ch in toNumber(seg.text) { put(ch, a, b, p) }
                first = false
            }
            if j < s.count && s[j].isASCII && s[j].isNumber { put(" ", j, j, false) }
            i = j
        }
        return (out, origin, originEnd, positional)
    }
}

enum VoiceKind: String { case name = "原名", pinyin = "同音", fuzzy = "音近", given = "叫名", initials = "首字母", number = "学号", numberSound = "音近学号" }

struct VoiceMatch: Identifiable {
    let id = UUID()
    var ids: [String]
    var heard: String
    var kind: VoiceKind
    var start: Int
    var cost: Double
}

final class VoiceParser {
    private struct Cand { let s: Student; let syl: [[String]] }

    private let tolerance: Double
    private let useInitials: Bool
    private let named: [Student]
    private let cands: [Cand]
    private let givenCands: [Cand]
    private let nameLens: [Int]
    private let byNo: [String: Student]
    private let byVal: [Int: [Student]]
    private let noLens: [Int]
    private var initialsIndex: [String: [Cand]] = [:]

    private static let numeralReadings: [(Character, String)] = [("零", "ling"), ("一", "yi"), ("一", "yao"), ("二", "er"), ("二", "liang"), ("三", "san"), ("四", "si"), ("五", "wu"), ("六", "liu"), ("七", "qi"), ("八", "ba"), ("九", "jiu"), ("十", "shi"), ("百", "bai")]
    private static let haoChars = Set("号好浩豪耗毫郝昊皓")
    private static let fillers = ["然后", "还有", "这个", "那个", "下一个", "接着", "同学", "以及", "好的", "下面", "一下", "一个", "我们", "他们", "还是", "就是", "可以", "什么", "怎么", "这里", "那里", "现在", "已经", "名字", "学号", "作业", "交了", "没交", "请假", "没有", "对的", "是的", "继续", "最后", "第一", "再来", "嗯嗯", "那么", "所以", "但是"].map { Array($0) }

    /// tolerance: 0.6 严格 / 1 标准 / 1.4 宽松
    init(roster: [Student], tolerance: Double = 1, initials: Bool = true) {
        self.tolerance = tolerance
        useInitials = initials
        named = roster.filter { $0.name.count >= 2 }
        cands = named.map { Cand(s: $0, syl: $0.name.map(Py.readings)) }.filter { $0.syl.allSatisfy { !$0.isEmpty } }
        givenCands = cands.filter { $0.syl.count >= 3 }.map { Cand(s: $0.s, syl: Array($0.syl.dropFirst())) }
        nameLens = Array(Set(cands.map { $0.syl.count })).sorted(by: >)
        var bn: [String: Student] = [:], bv: [Int: [Student]] = [:]
        for s in roster {
            let no = s.no.trimmingCharacters(in: .whitespaces)
            if !no.isEmpty { bn[TextNorm.canon(no)] = s }
            if !no.isEmpty, no.allSatisfy({ $0.isASCII && $0.isNumber }), let v = Int(no) { bv[v, default: []].append(s) }
        }
        byNo = bn; byVal = bv
        noLens = Array(Set(bn.keys.filter { $0.allSatisfy { $0.isASCII && $0.isNumber } }.map { $0.count }))
        for c in cands {
            for k in Self.initialCombos(c.syl) {
                var l = initialsIndex[k] ?? []
                if !l.contains(where: { $0.s.id == c.s.id }) { l.append(c) }
                initialsIndex[k] = l
            }
        }
    }

    private func maxCost(_ len: Int) -> Double { tolerance * (len == 2 ? 0.36 : len == 3 ? 0.62 : 1.05) }

    private func lookupNo(_ n: String) -> [Student] {
        if let s = byNo[n] { return [s] }
        if n.allSatisfy({ $0.isASCII && $0.isNumber }), let v = Int(n) { return byVal[v] ?? [] }
        return []
    }

    private static func initialCombos(_ syl: [[String]]) -> [String] {
        var acc: [String] = [""]
        for alts in syl {
            var f: [String] = []
            for a in alts { let x = String(a.prefix(1)).uppercased(); if !f.contains(x) { f.append(x) } }
            var next: [String] = []
            for p in acc { for x in f { let k = p + x; if !next.contains(k) { next.append(k) } } }
            acc = Array(next.prefix(32))
        }
        return acc
    }

    private func splitNumber(_ run: String) -> [String] {
        if !lookupNo(run).isEmpty || noLens.isEmpty { return [run] }
        let r = Array(run), n = r.count
        var best = [[String]?](repeating: nil, count: n + 1)
        best[0] = []
        for i in 0..<n {
            guard let bi = best[i] else { continue }
            for l in noLens where i + l <= n {
                let piece = String(r[i..<(i + l)])
                if lookupNo(piece).isEmpty { continue }
                let cand = bi + [piece]
                if best[i + l] == nil || cand.count < best[i + l]!.count { best[i + l] = cand }
            }
        }
        return best[n] ?? [run]
    }

    private func numeralOptions(_ c: Character) -> [(Character, Int)] {
        let rd = Py.readings(c)
        if rd.isEmpty { return [] }
        var res: [Character: Int] = [:]
        for (n, r) in Self.numeralReadings {
            let d = rd.map { Py.syllable($0, r) }.min() ?? 1
            let cost: Int? = c == n ? 0 : d == 0 ? 1 : d <= 0.3 ? 2 : nil
            guard let cost else { continue }
            if res[n] == nil || res[n]! > cost { res[n] = cost }
        }
        return res.map { ($0.key, $0.value) }
    }

    func parse(_ text: String) -> (matches: [VoiceMatch], leftover: String) {
        // 半角化，并去掉汉字之间的空格
        let h = Array(TextNorm.halfWidth(text))
        var t: [Character] = []
        for (i, c) in h.enumerated() {
            if c.isWhitespace, let p = t.last, Py.isHan(p), i + 1 < h.count, Py.isHan(h[i + 1]) { continue }
            t.append(c)
        }
        let n = t.count
        var used = [Bool](repeating: false, count: n)
        var matches: [VoiceMatch] = []
        func free(_ a: Int, _ b: Int) -> Bool { (a..<b).allSatisfy { !used[$0] } }
        func take(_ a: Int, _ b: Int) { for i in a..<b { used[i] = true } }
        func find(_ w: [Character], from: Int) -> Int? {
            guard w.count > 0, n >= w.count, from <= n - w.count else { return nil }
            for i in from...(n - w.count) where Array(t[i..<(i + w.count)]) == w { return i }
            return nil
        }

        // 1) 原名
        var same: [String: [Student]] = [:]
        for s in named { same[s.name, default: []].append(s) }
        for name in same.keys.sorted(by: { $0.count > $1.count }) {
            let w = Array(name)
            var from = 0
            while let i = find(w, from: from) {
                if free(i, i + w.count) {
                    matches.append(VoiceMatch(ids: same[name]!.map(\.id), heard: name, kind: .name, start: i, cost: 0))
                    take(i, i + w.count)
                }
                from = i + 1
            }
        }
        // 口头禅屏蔽
        var blocked = [Bool](repeating: false, count: n)
        for w in Self.fillers {
            var from = 0
            while let i = find(w, from: from) { for k in i..<(i + w.count) { blocked[k] = true }; from = i + 1 }
        }

        // 2) 音近数字 + 号
        var i = 0
        while i < n {
            if used[i] || !Py.isHan(t[i]) { i += 1; continue }
            var j = i
            var options: [[(Character, Int)]] = []
            while j < n && !used[j] && !Self.haoChars.contains(t[j]) && j - i < 6 {
                let o = numeralOptions(t[j]); if o.isEmpty { break }
                options.append(o); j += 1
            }
            let endsHao = j < n && Self.haoChars.contains(t[j]) && !options.isEmpty
            let allReal = t[i..<j].allSatisfy { CnNum.numeralChars.contains($0) }
            if !endsHao || allReal { i += 1; continue }
            var best: (pieces: [String], cost: Int)? = nil
            func dfs(_ k: Int, _ acc: String, _ cost: Int) {
                if let b = best, cost >= b.cost { return }
                if k == options.count {
                    let segs = CnNum.segments(acc).map(\.text)
                    if segs.joined() != acc { return }
                    let pieces = segs.map(CnNum.toNumber)
                    let total = cost + 3 * (pieces.count - 1)
                    if pieces.allSatisfy({ !lookupNo($0).isEmpty }) && (best == nil || total < best!.cost) { best = (pieces, total) }
                    return
                }
                for (ch, c) in options[k] { dfs(k + 1, acc + String(ch), cost + c) }
            }
            dfs(0, "", 0)
            if let b = best {
                for p in b.pieces {
                    matches.append(VoiceMatch(ids: lookupNo(p).map(\.id), heard: "\(String(t[i...j]))→\(p)", kind: .numberSound, start: i, cost: 0))
                }
                take(i, j + 1); i = j + 1
            } else { i += 1 }
        }

        // 3/4) 拼音模糊全名 + 只叫名
        func fuzzy(_ pool: [Cand], _ kind: VoiceKind, _ limit: (Int) -> Double, _ uniqueOnly: Bool) {
            if pool.isEmpty { return }
            let lens = Set(pool.map { $0.syl.count })
            let wl = Set(lens.flatMap { [$0 - 1, $0, $0 + 1] }.filter { $0 >= 2 })
            var found: [(st: Int, en: Int, ids: [String], cost: Double)] = []
            for st in 0..<n {
                for len in wl {
                    let en = st + len
                    if en > n { continue }
                    if !(st..<en).allSatisfy({ !used[$0] && !blocked[$0] && Py.isHan(t[$0]) && !Py.readings(t[$0]).isEmpty }) { continue }
                    let heard = t[st..<en].map(Py.readings)
                    let scored = pool.filter { abs($0.syl.count - len) <= 1 }.map { ($0, Py.sequence(heard, $0.syl)) }
                        .filter { $0.1 <= limit($0.0.syl.count) }.sorted { $0.1 < $1.1 }
                    guard let b = scored.first?.1 else { continue }
                    var close: [String] = []
                    for x in scored where x.1 <= b + 0.12 && !close.contains(x.0.s.id) { close.append(x.0.s.id) }
                    if uniqueOnly && close.count > 1 { continue }
                    found.append((st, en, close, b))
                }
            }
            found.sort { a, b in
                let ra = a.cost / Double(a.en - a.st), rb = b.cost / Double(b.en - b.st)
                return ra != rb ? ra < rb : (a.en - a.st) > (b.en - b.st)
            }
            for w in found where free(w.st, w.en) {
                let k: VoiceKind = w.cost == 0 && kind == .fuzzy ? .pinyin : kind
                matches.append(VoiceMatch(ids: w.ids, heard: String(t[w.st..<w.en]), kind: k, start: w.st, cost: w.cost))
                take(w.st, w.en)
            }
        }
        fuzzy(cands, .fuzzy, maxCost, false)
        fuzzy(givenCands, .given, { _ in 0.2 * self.tolerance }, true)

        // 5) 首字母
        if useInitials && !initialsIndex.isEmpty {
            func resolve(_ heard: [[String]]?, _ list: [Cand]) -> [String] {
                guard list.count > 1, let heard else { return list.map(\.s.id) }
                let sc = list.map { ($0, Py.sequence(heard, $0.syl)) }.sorted { $0.1 < $1.1 }
                return sc.filter { $0.1 <= sc[0].1 + 0.15 }.map(\.0.s.id)
            }
            for m in Rx.matches(String(t), "(?:[A-Za-z]\\s*){2,5}") where free(m.start, m.end) {
                let key = m.text.filter { $0.isLetter }.uppercased()
                guard let list = initialsIndex[key] else { continue }
                matches.append(VoiceMatch(ids: resolve(nil, list), heard: m.text.trimmingCharacters(in: .whitespaces), kind: .initials, start: m.start, cost: 0))
                take(m.start, m.end)
            }
            for len in nameLens {
                var i = 0
                while i + len <= n {
                    let ok = (i..<(i + len)).allSatisfy { !used[$0] && !blocked[$0] && Py.isHan(t[$0]) && !CnNum.numeralChars.contains(t[$0]) && !Py.readings(t[$0]).isEmpty }
                    if !ok { i += 1; continue }
                    let heard = t[i..<(i + len)].map(Py.readings)
                    var hit: [Cand] = []
                    for k in Self.initialCombos(heard) {
                        for c in initialsIndex[k] ?? [] where c.syl.count == len && !hit.contains(where: { $0.s.id == c.s.id }) { hit.append(c) }
                    }
                    if !hit.isEmpty {
                        matches.append(VoiceMatch(ids: resolve(heard, hit), heard: String(t[i..<(i + len)]), kind: .initials, start: i, cost: 0))
                        take(i, i + len); i += len
                    } else { i += 1 }
                }
            }
        }

        // 6) 数字学号
        let rest = t.enumerated().map { used[$0.offset] ? " " : $0.element }
        let nt = CnNum.convert(rest)
        for m in Rx.matches(String(nt.text), "\\d+") {
            let a = m.start, b = m.end - 1
            let pos = nt.origin[a]
            var any = false
            for piece in nt.positional[a] ? [m.text] : splitNumber(m.text) {
                let st = lookupNo(piece)
                if !st.isEmpty { matches.append(VoiceMatch(ids: st.map(\.id), heard: piece, kind: .number, start: pos, cost: 0)); any = true }
            }
            if any { take(pos, nt.originEnd[b]) }
        }
        let left = String(t.enumerated().filter { !used[$0.offset] }.map(\.element))
        let leftover = Rx.replace(left, pattern: "[\\s，。、,.!?！？号和还有跟以及然后]+") { _ in " " }.trimmingCharacters(in: .whitespaces)
        matches.sort { $0.start < $1.start }
        return (matches, leftover)
    }
}
