import SwiftUI

/// 抄袭的颜色（深橙），与安卓、网页一致
let plagiarismColor = Color(red: 0.85, green: 0.28, blue: 0.06)
private let providerColor = Color(red: 0.91, green: 0.47, blue: 0.17)
private let copierColor = Color(red: 0.90, green: 0.22, blue: 0.30)
private let bothColor = Color(red: 0.56, green: 0.27, blue: 0.68)
private func roleColor(_ n: PlagiarismNode) -> Color { n.copied > 0 && n.provided > 0 ? bothColor : n.copied > 0 ? copierColor : providerColor }

/// 选择“抄了谁的”：可多选、可搜索；确定后抄袭者和提供者都记为未交
struct ProviderPickerSheet: View {
    @EnvironmentObject var store: Store
    @Environment(\.dismiss) var dismiss
    let assignmentId: String
    let receivers: [String]
    var onDone: () -> Void = {}
    @State private var query = ""
    @State private var picked: Set<String> = []

    var body: some View {
        let c = store.data.currentClass
        let a = c?.assignments.first { $0.id == assignmentId }
        let names = (c?.students ?? []).filter { receivers.contains($0.id) }.map(\.name).joined(separator: "、")
        let list = (c?.students ?? []).filter { !receivers.contains($0.id) && (query.isEmpty || $0.name.contains(query) || $0.no.contains(query)) }
            .sorted { compareStudentNumbers($0.no, $1.no) < 0 }
        NavigationStack {
            List {
                Section { Text(store.data.rules.plagiarism.text).font(.caption).foregroundStyle(.secondary) }
                Section("选出提供作业的同学（可多选）") {
                    ForEach(list) { s in
                        Button {
                            if picked.contains(s.id) { picked.remove(s.id) } else { picked.insert(s.id) }
                        } label: {
                            HStack {
                                Image(systemName: picked.contains(s.id) ? "checkmark.circle.fill" : "circle").foregroundStyle(picked.contains(s.id) ? plagiarismColor : .secondary)
                                Text(s.name).foregroundStyle(.primary)
                                Text([s.no, s.group].filter { !$0.isEmpty }.joined(separator: " · ")).font(.caption).foregroundStyle(.secondary)
                            }
                        }
                    }
                }
            }
            .searchable(text: $query, prompt: "搜索姓名或学号")
            .navigationTitle("\(names) 抄了谁的？")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("取消") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("记为抄袭") {
                        store.markPlagiarism(assignmentId, receivers: receivers, providers: Array(picked))
                        store.say("已记为抄袭：\(receivers.count) 人抄袭、\(picked.count) 人提供，双方都记未交")
                        onDone(); dismiss()
                    }.disabled(picked.isEmpty)
                }
            }
            .onAppear { picked = Set(receivers.flatMap { a?.copiedFrom($0) ?? [] }) }
        }
    }
}

/// 这次作业里这位同学的抄袭关系（“抄袭 甲” / “提供给 乙 抄”）
func plagiarismLine(_ c: SchoolClass, _ a: Assignment, _ sid: String) -> String {
    func names(_ ids: [String]) -> String { ids.compactMap { id in c.students.first { $0.id == id }?.name }.joined(separator: "、") }
    let from = a.copiedFrom(sid), by = a.copiedBy(sid)
    return [from.isEmpty ? nil : "抄袭 \(names(from))", by.isEmpty ? nil : "提供给 \(names(by)) 抄"].compactMap { $0 }.joined(separator: "；")
}

/// 侧边栏“抄袭关系”：概况、关系图、关系列表、涉及的同学、抄袭圈、最近记录
struct PlagiarismView: View {
    @EnvironmentObject var store: Store

    var body: some View {
        Group {
            if let c = store.data.currentClass {
                let rules = store.data.rules
                let r = c.plagiarismReport(rules)
                if r.cases.isEmpty {
                    EmptyHint(emoji: "🕵️", title: "还没有抄袭记录", detail: "在作业里长按同学（或多选后）选「抄袭」，再选出抄了谁的。\n" + rules.plagiarism.text)
                } else {
                    List {
                        Section {
                            HStack {
                                stat("\(r.cases.count)", "抄袭记录"); stat("\(r.copiers)", "抄袭者"); stat("\(r.providers)", "提供者"); stat(fmtNum(r.points), "共扣分")
                            }
                            Text(rules.plagiarism.text + "；改成其他状态即取消。").font(.caption).foregroundStyle(.secondary)
                        }
                        Section("关系图 · 箭头从提供者指向抄袭者，线越粗次数越多") {
                            PlagiarismGraph(r: r).frame(height: 420)
                            HStack(spacing: 14) {
                                legend(providerColor, "提供者"); legend(copierColor, "抄袭者"); legend(bothColor, "既抄又提供")
                            }.font(.caption)
                        }
                        Section("抄袭关系（提供者 → 抄袭者）") {
                            ForEach(Array(r.edges.enumerated()), id: \.offset) { _, e in
                                HStack {
                                    Text(e.provider.name).foregroundStyle(providerColor).bold()
                                    Text("→").foregroundStyle(.secondary)
                                    Text(e.receiver.name).foregroundStyle(copierColor).bold()
                                    Spacer()
                                    VStack(alignment: .trailing) {
                                        Text("\(e.count) 次").bold()
                                        Text(e.assignments.suffix(2).map(\.title).joined(separator: "、")).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                                    }
                                }
                            }
                        }
                        Section("涉及的同学（按次数）") {
                            ForEach(Array(r.nodes.enumerated()), id: \.offset) { _, n in
                                HStack {
                                    Circle().fill(roleColor(n)).frame(width: 10, height: 10)
                                    VStack(alignment: .leading, spacing: 2) {
                                        HStack { Text(n.student.name).bold(); Text(n.role).font(.caption2).foregroundStyle(roleColor(n)) }
                                        Text([n.copied > 0 ? "抄袭 \(n.copied) 次" : nil, n.provided > 0 ? "提供 \(n.provided) 次" : nil,
                                              "本轮第 \(n.roundTimes) 次" + (rules.plagiarism.escalate && n.roundTimes > 0 ? "（下次 ×\(fmtNum(rules.plagiarism.factor(n.roundTimes + 1)))）" : "")]
                                            .compactMap { $0 }.joined(separator: " · ")).font(.caption).foregroundStyle(.secondary)
                                    }
                                    Spacer()
                                    Text(fmtNum(n.points)).bold().foregroundStyle(copierColor)
                                }
                            }
                        }
                        if r.clusters.contains(where: { $0.count >= 3 }) {
                            Section("抄袭圈（互相连在一起的同学）") {
                                ForEach(Array(r.clusters.filter { $0.count >= 2 }.enumerated()), id: \.offset) { i, cl in
                                    Text("第 \(i + 1) 圈 · \(cl.count) 人：" + cl.map(\.name).joined(separator: "、"))
                                }
                            }
                        }
                        Section("最近的抄袭记录") {
                            ForEach(Array(r.cases.prefix(20).enumerated()), id: \.offset) { _, k in
                                NavigationLink(value: k.assignment.id) {
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text("\(k.receiver.name) 抄袭 \(k.providers.map(\.name).joined(separator: "、"))").bold()
                                        Text("\(fmtDate(k.assignment.date)) · \(k.assignment.subject) \(k.assignment.title)").font(.caption).foregroundStyle(.secondary)
                                    }
                                }
                            }
                        }
                    }
                    .navigationDestination(for: String.self) { id in AssignmentDetailView(assignmentId: id) }
                }
            } else {
                EmptyHint(emoji: "🏫", title: "还没有班级", detail: "")
            }
        }
        .navigationTitle("抄袭关系")
    }

    private func stat(_ v: String, _ l: String) -> some View {
        VStack { Text(v).font(.title2.bold()).foregroundStyle(plagiarismColor); Text(l).font(.caption).foregroundStyle(.secondary) }.frame(maxWidth: .infinity)
    }
    private func legend(_ c: Color, _ t: String) -> some View { HStack(spacing: 4) { Circle().fill(c).frame(width: 9, height: 9); Text(t).foregroundStyle(.secondary) } }
}

/// 关系图：同学排成一圈（同一个抄袭圈的挨在一起），箭头从提供者指向抄袭者
struct PlagiarismGraph: View {
    let r: PlagiarismReport

    var body: some View {
        let nodeOf = Dictionary(r.nodes.map { ($0.student.id, $0) }, uniquingKeysWith: { a, _ in a })
        let order = r.clusters.flatMap { $0 }.filter { nodeOf[$0.id] != nil }
        let maxCount = max(1, r.edges.map(\.count).max() ?? 1)
        Canvas { ctx, size in
            let n = order.count
            let nodeR: CGFloat = n > 16 ? 13 : 18
            let cx = size.width / 2, cy = size.height / 2
            let ring = min(cx, cy) - nodeR - 22
            var pos: [String: CGPoint] = [:]
            for (i, s) in order.enumerated() {
                let ang = n == 1 ? -Double.pi / 2 : -Double.pi / 2 + 2 * Double.pi * Double(i) / Double(n)
                pos[s.id] = CGPoint(x: cx + ring * CGFloat(cos(ang)), y: cy + ring * CGFloat(sin(ang)))
            }
            for e in r.edges {
                guard let a = pos[e.provider.id], let b = pos[e.receiver.id] else { continue }
                let d = max(1, hypot(b.x - a.x, b.y - a.y)); let ux = (b.x - a.x) / d, uy = (b.y - a.y) / d
                let start = CGPoint(x: a.x + ux * nodeR, y: a.y + uy * nodeR), end = CGPoint(x: b.x - ux * (nodeR + 2), y: b.y - uy * (nodeR + 2))
                let w = 1.5 + 3.5 * CGFloat(e.count) / CGFloat(maxCount)
                let col = plagiarismColor.opacity(0.45 + 0.5 * Double(e.count) / Double(maxCount))
                var line = Path(); line.move(to: start); line.addLine(to: end)
                ctx.stroke(line, with: .color(col), lineWidth: w)
                let ang = atan2(uy, ux), head = 9 + w
                var tip = Path()
                tip.move(to: end)
                tip.addLine(to: CGPoint(x: end.x - head * cos(ang - 0.45), y: end.y - head * sin(ang - 0.45)))
                tip.addLine(to: CGPoint(x: end.x - head * cos(ang + 0.45), y: end.y - head * sin(ang + 0.45)))
                tip.closeSubpath()
                ctx.fill(tip, with: .color(col))
                if e.count > 1 {
                    let mid = CGPoint(x: (start.x + end.x) / 2, y: (start.y + end.y) / 2)
                    ctx.fill(Path(ellipseIn: CGRect(x: mid.x - 9, y: mid.y - 9, width: 18, height: 18)), with: .color(plagiarismColor))
                    ctx.draw(Text("\(e.count)").font(.caption2.bold()).foregroundColor(.white), at: mid)
                }
            }
            for s in order {
                guard let o = pos[s.id], let node = nodeOf[s.id] else { continue }
                let rect = CGRect(x: o.x - nodeR, y: o.y - nodeR, width: nodeR * 2, height: nodeR * 2)
                ctx.fill(Path(ellipseIn: rect), with: .color(roleColor(node)))
                ctx.stroke(Path(ellipseIn: rect), with: .color(.white), lineWidth: 2)
                ctx.draw(Text(String(s.name.suffix(1))).font(.system(size: n > 16 ? 11 : 14, weight: .bold)).foregroundColor(.white), at: o)
                let below = o.y >= cy
                ctx.draw(Text(s.name).font(.caption2), at: CGPoint(x: o.x, y: below ? o.y + nodeR + 9 : o.y - nodeR - 9))
            }
        }
        .accessibilityLabel("抄袭关系图：\(order.count) 位同学，\(r.edges.count) 条关系")
    }
}
