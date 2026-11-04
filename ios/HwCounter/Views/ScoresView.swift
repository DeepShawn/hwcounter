import SwiftUI

struct ScoreRequest: Identifiable {
    let id = newId()
    let classId: String
    let studentIds: [String]
}

private struct ScoreValueRequest: Identifiable {
    let id = newId()
    let classId: String?
    let studentIds: [String]
}

struct ScoresView: View {
    @EnvironmentObject var store: Store
    @State private var scoreRequest: ScoreRequest? = nil
    @State private var selecting = false
    @State private var selected: Set<String> = []
    @State private var exportURL: URL? = nil
    @State private var scoreValueRequest: ScoreValueRequest? = nil

    var body: some View {
        Group {
            if let c = store.data.currentClass, !c.students.isEmpty {
                let stats = c.stats(store.data.rules).sorted { $0.score > $1.score }
                let picked = c.students.filter { selected.contains($0.id) }
                List {
                    Section {
                        podium(Array(stats.prefix(3)))
                        Text("基准分：\(fmtNum(store.data.rules.initial))").font(.caption).foregroundStyle(.secondary)
                    }
                    Section(selecting ? "点击学生选择或取消" : "排行（点学生加减分）") {
                        ForEach(Array(stats.enumerated()), id: \.element.id) { i, st in
                            Button {
                                if selecting {
                                    if selected.contains(st.id) { selected.remove(st.id) } else { selected.insert(st.id) }
                                } else { scoreRequest = ScoreRequest(classId: c.id, studentIds: [st.id]) }
                            } label: { row(i + 1, st) }
                            .buttonStyle(.plain)
                            .listRowBackground(selecting && selected.contains(st.id) ? Color.accentColor.opacity(0.12) : Color(uiColor: .secondarySystemGroupedBackground))
                            .accessibilityValue(selecting ? (selected.contains(st.id) ? "已选" : "未选") : fmtNum(st.score))
                        }
                    }
                }
                .toolbar {
                    ToolbarItem(placement: .primaryAction) {
                        HStack {
                            if !store.readOnly {
                                Button(selecting ? "退出多选" : "多选") { selecting.toggle(); selected.removeAll() }
                                Menu {
                                    Button("修改基准分") { scoreValueRequest = ScoreValueRequest(classId: nil, studentIds: []) }
                                    Button("重置全班分数") { scoreValueRequest = ScoreValueRequest(classId: c.id, studentIds: c.students.map(\.id)) }
                                        .disabled(selecting)
                                } label: { Label("分数设置", systemImage: "ellipsis.circle") }
                            }
                            Button { exportURL = Exporter.classCSV(c, store.data.rules) } label: { Label("导出统计表", systemImage: "tablecells") }
                        }
                    }
                }
                .safeAreaInset(edge: .bottom) {
                    if selecting && !store.readOnly {
                        VStack(spacing: 8) {
                            HStack {
                                Text("已选 \(picked.count) 人").font(.headline)
                                Spacer()
                                Button("全选") { selected = Set(c.students.map(\.id)) }
                                Button("清空") { selected.removeAll() }
                                Button("取消") { cancelSelection() }
                            }
                            Button {
                                scoreRequest = ScoreRequest(classId: c.id, studentIds: picked.map(\.id))
                            } label: { Text("批量加减分").frame(maxWidth: .infinity) }
                            .buttonStyle(.borderedProminent)
                            .disabled(picked.isEmpty)
                            Button("重置分数") { scoreValueRequest = ScoreValueRequest(classId: c.id, studentIds: picked.map(\.id)) }
                                .disabled(picked.isEmpty)
                        }
                        .padding().background(.regularMaterial)
                    }
                }
            } else {
                EmptyHint(emoji: "⭐️", title: "还没有学生", detail: "导入名单后，交作业情况会自动换算成积分")
            }
        }
        .navigationTitle("积分")
        .sheet(item: $scoreRequest) { request in
            let students = store.data.classes.first(where: { $0.id == request.classId })?.students.filter { request.studentIds.contains($0.id) } ?? []
            ScoreSheet(students: students, classId: request.classId) { cancelSelection() }
        }
        .sheet(item: Binding(get: { exportURL.map { ShareFile(url: $0) } }, set: { exportURL = $0?.url })) { f in ShareSheet(items: [f.url]) }
        .sheet(item: $scoreValueRequest) { request in
            ScoreValueSheet(request: request, initial: store.data.rules.initial) { cancelSelection() }
        }
        .onChange(of: store.data.currentClass?.id) { _ in cancelSelection(); scoreRequest = nil; scoreValueRequest = nil }
        .onChange(of: store.readOnly) { _ in cancelSelection(); scoreRequest = nil; scoreValueRequest = nil }
    }

    private func cancelSelection() { selecting = false; selected.removeAll() }

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
            if selecting {
                Image(systemName: selected.contains(st.id) ? "checkmark.circle.fill" : "circle")
                    .foregroundStyle(selected.contains(st.id) ? Color.accentColor : Color.secondary).frame(width: 28)
            } else {
                Text("\(rank)").font(.callout.monospacedDigit()).foregroundStyle(.secondary).frame(width: 28)
            }
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

private struct ScoreValueSheet: View {
    @EnvironmentObject var store: Store
    @Environment(\.dismiss) var dismiss
    let request: ScoreValueRequest
    let onSaved: () -> Void
    @State private var text: String

    init(request: ScoreValueRequest, initial: Double, onSaved: @escaping () -> Void) {
        self.request = request; self.onSaved = onSaved
        _text = State(initialValue: fmtNum(initial))
    }

    var body: some View {
        let editingBase = request.classId == nil
        let targets = store.data.classes.first(where: { $0.id == request.classId })?.students.filter { request.studentIds.contains($0.id) } ?? []
        let value = Double(text.trimmingCharacters(in: .whitespacesAndNewlines))
        NavigationStack {
            Form {
                Section {
                    Text(editingBase ? "适用于所有班级。只修改计分基准，历史加减分继续计入，未交次数和记录保留。" : "将当前积分调整到指定分数，每人新增一条“重置分数”记录。未交次数、作业状态和原有加减分记录全部保留；之后的加减分继续累计。")
                        .font(.callout).foregroundStyle(.secondary)
                    TextField(editingBase ? "基准分" : "重置到", text: $text)
                        .keyboardType(.numbersAndPunctuation)
                }
                if !editingBase {
                    Section("已选 \(targets.count) 人") { Text(targets.map { "\($0.no) \($0.name)" }.joined(separator: "、")) }
                }
                Section {
                    Button(editingBase ? "保存" : "确认重置") {
                        guard !store.readOnly, let value, value.isFinite else { return }
                        if let classId = request.classId {
                            guard !targets.isEmpty else { return }
                            store.resetScores(targets.map(\.id), target: value, classId: classId)
                            store.say("已将 \(targets.count) 人重置为 \(fmtNum(value)) 分，未交次数及历史记录已保留")
                        } else {
                            store.update { var rules = $0.rules; rules.initial = value; $0.rules = rules }
                            store.say("基准分已改为 \(fmtNum(value))，历史记录已保留")
                        }
                        onSaved(); dismiss()
                    }
                    .disabled(store.readOnly || value?.isFinite != true || (!editingBase && targets.isEmpty))
                }
            }
            .navigationTitle(editingBase ? "修改基准分" : "重置分数")
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("取消") { dismiss() } } }
        }
        .presentationDetents([.medium, .large])
    }
}

struct ScoreSheet: View {
    @EnvironmentObject var store: Store
    @Environment(\.dismiss) var dismiss
    let students: [Student]
    let classId: String
    var onSaved: () -> Void = {}
    var initialReason: String = ""
    @State private var delta = 1.0
    @State private var reason = ""

    var body: some View {
        let entries = (store.data.classes.first(where: { $0.id == classId })?.scores ?? []).filter { students.count == 1 && $0.studentId == students.first?.id }.sorted { $0.time > $1.time }
        NavigationStack {
            Form {
                if students.count > 1 {
                    Section("已选 \(students.count) 人（每人应用相同分值）") {
                        Text(students.map { "\($0.no) \($0.name)" }.joined(separator: "、"))
                    }
                }
                if !store.readOnly {
                    Section("加减分") {
                        HStack {
                            ForEach([-2.0, -1, 1, 2, 5], id: \.self) { d in
                                Button(signed(d)) { delta = d }
                                    .buttonStyle(.bordered)
                                    .tint(delta == d ? (d > 0 ? Color.green : Color.red) : Color.gray)
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
                            guard !store.readOnly, !students.isEmpty, delta.isFinite, delta != 0 else { return }
                            let trimmed = reason.trimmingCharacters(in: .whitespacesAndNewlines)
                            store.addScore(students.map(\.id), delta, trimmed.isEmpty ? (delta > 0 ? "加分" : "扣分") : trimmed, classId: classId)
                            store.say("\(students.count == 1 ? students[0].name : "\(students.count) 人") \(signed(delta))")
                            onSaved()
                            dismiss()
                        } label: { Text("确定 \(signed(delta))").frame(maxWidth: .infinity) }
                        .buttonStyle(.borderedProminent)
                        .disabled(delta == 0 || students.isEmpty)
                    }
                }
                if students.count == 1 { Section("记录") {
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
                } }
            }
            .navigationTitle(students.count == 1 ? "\(students[0].no) \(students[0].name)" : "给 \(students.count) 人加减分")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("关闭") { dismiss() } } }
        }
        .presentationDetents([.medium, .large])
        .onAppear { reason = initialReason }
    }
}
