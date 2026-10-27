import SwiftUI

struct ScoresView: View {
    @EnvironmentObject var store: Store
    @State private var picked: Student? = nil
    @State private var exportURL: URL? = nil

    var body: some View {
        Group {
            if let c = store.data.currentClass, !c.students.isEmpty {
                let stats = c.stats(store.data.rules).sorted { $0.score > $1.score }
                List {
                    Section {
                        podium(Array(stats.prefix(3)))
                    }
                    Section("排行（点学生加减分）") {
                        ForEach(Array(stats.enumerated()), id: \.element.id) { i, st in
                            Button { picked = st.student } label: { row(i + 1, st) }.buttonStyle(.plain)
                        }
                    }
                }
                .toolbar {
                    ToolbarItem(placement: .primaryAction) {
                        Button { exportURL = Exporter.classCSV(c, store.data.rules) } label: { Label("导出统计表", systemImage: "tablecells") }
                    }
                }
            } else {
                EmptyHint(emoji: "⭐️", title: "还没有学生", detail: "导入名单后，交作业情况会自动换算成积分")
            }
        }
        .navigationTitle("积分")
        .sheet(item: $picked) { s in ScoreSheet(student: s) }
        .sheet(item: Binding(get: { exportURL.map { ShareFile(url: $0) } }, set: { exportURL = $0?.url })) { f in ShareSheet(items: [f.url]) }
    }

    private func podium(_ top: [SchoolClass.Stat]) -> some View {
        HStack(alignment: .bottom, spacing: 16) {
            ForEach(Array(top.enumerated()), id: \.element.id) { i, st in
                VStack(spacing: 6) {
                    Text(["🥇", "🥈", "🥉"][i]).font(.system(size: 40))
                    Text(st.student.name).font(.headline)
                    Text(fmtNum(st.score)).font(.title2.bold().monospacedDigit()).foregroundStyle(Color.accentColor)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 10)
            }
        }
    }

    private func row(_ rank: Int, _ st: SchoolClass.Stat) -> some View {
        HStack(spacing: 12) {
            Text("\(rank)").font(.callout.monospacedDigit()).foregroundStyle(.secondary).frame(width: 28)
            VStack(alignment: .leading, spacing: 2) {
                Text("\(st.student.name)").font(.headline)
                Text("\(st.student.no) · 交 \(st.submitted) · 迟 \(st.late) · 缺 \(st.missing)").font(.caption).foregroundStyle(.secondary)
            }
            Spacer()
            Text(fmtNum(st.score)).font(.title3.bold().monospacedDigit())
        }
        .contentShape(Rectangle())
    }
}

struct ScoreSheet: View {
    @EnvironmentObject var store: Store
    @Environment(\.dismiss) var dismiss
    let student: Student
    @State private var delta = 1.0
    @State private var reason = ""

    var body: some View {
        let entries = (store.data.currentClass?.scores ?? []).filter { $0.studentId == student.id }.sorted { $0.time > $1.time }
        NavigationStack {
            Form {
                if !store.readOnly {
                    Section("加减分") {
                        HStack {
                            ForEach([-2.0, -1, 1, 2, 5], id: \.self) { d in
                                Button(signed(d)) { delta = d }
                                    .buttonStyle(.bordered)
                                    .tint(delta == d ? (d > 0 ? .green : .red) : .gray)
                            }
                        }
                        Stepper("分值：\(signed(delta))", value: $delta, in: -20...20, step: 0.5)
                        TextField("原因", text: $reason)
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack {
                                ForEach(store.data.quickReasons, id: \.self) { r in
                                    Button(r) { reason = r }.buttonStyle(.bordered).font(.caption)
                                }
                            }
                        }
                        Button {
                            store.addScore([student.id], delta, reason.isEmpty ? (delta > 0 ? "加分" : "扣分") : reason)
                            store.say("\(student.name) \(signed(delta))")
                            dismiss()
                        } label: { Text("确定 \(signed(delta))").frame(maxWidth: .infinity) }
                        .buttonStyle(.borderedProminent)
                        .disabled(delta == 0)
                    }
                }
                Section("记录") {
                    if entries.isEmpty { Text("还没有手动加减分").foregroundStyle(.secondary) }
                    ForEach(entries) { e in
                        HStack {
                            Text(e.reason)
                            Spacer()
                            Text(fmtDate(Date(timeIntervalSince1970: Double(e.time) / 1000))).font(.caption).foregroundStyle(.secondary)
                            Text(signed(e.delta)).bold().foregroundStyle(e.delta >= 0 ? .green : .red)
                        }
                        .swipeActions { if !store.readOnly { Button(role: .destructive) { store.removeScore(e.id) } label: { Label("删除", systemImage: "trash") } } }
                    }
                }
            }
            .navigationTitle("\(student.no) \(student.name)")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("关闭") { dismiss() } } }
        }
        .presentationDetents([.medium, .large])
    }
}
