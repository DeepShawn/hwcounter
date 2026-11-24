import SwiftUI

/// 考试：列表、新建 / 修改 / 删除、录入成绩、缺考（与安卓同样的数据，三端同步）
struct ExamRef: Hashable { let id: String }

struct ExamsView: View {
    @EnvironmentObject var store: Store
    @State private var adding = false

    var body: some View {
        let c = store.data.currentClass
        Group {
            if let c, !c.exams.isEmpty {
                List {
                    ForEach(c.exams.sorted { $0.date > $1.date }) { e in
                        NavigationLink(value: ExamRef(id: e.id)) {
                            let vals = c.students.compactMap { e.scores[$0.id] }
                            HStack {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(e.title).font(.headline)
                                    Text("\(fmtDate(Date(timeIntervalSince1970: Double(e.date) / 1000)))\(e.subject.isEmpty ? "" : " · \(e.subject)") · \(vals.count) 人").font(.caption).foregroundStyle(.secondary)
                                }
                                Spacer()
                                Text(vals.isEmpty ? "—" : fmtNum(vals.reduce(0, +) / Double(vals.count))).font(.headline)
                            }
                        }
                    }
                }
            } else {
                EmptyHint(emoji: "📝", title: "还没有考试", detail: store.readOnly ? "老师录入后会显示在这里" : "点右上角 ＋ 新建考试，再录入成绩")
            }
        }
        .navigationTitle("考试")
        .navigationDestination(for: ExamRef.self) { r in ExamDetailView(examId: r.id) }
        .toolbar { if !store.readOnly && c != nil { Button { adding = true } label: { Label("新建考试", systemImage: "plus") } } }
        .sheet(isPresented: $adding) { ExamEditSheet(exam: nil) }
    }
}

struct ExamDetailView: View {
    @EnvironmentObject var store: Store
    @Environment(\.dismiss) var dismiss
    let examId: String
    @State private var mode = 0
    @State private var editing = false

    var body: some View {
        if let c = store.data.currentClass, let e = c.exams.first(where: { $0.id == examId }) {
            let vals = c.students.compactMap { e.scores[$0.id] }
            let ranked = c.students.compactMap { s in e.scores[s.id].map { (s, $0) } }.sorted { $0.1 > $1.1 }
            let rules = store.data.rules
            List {
                Section {
                    HStack {
                        stat("平均分", vals.isEmpty ? "—" : fmtNum(vals.reduce(0, +) / Double(vals.count)))
                        stat("最高", vals.max().map(fmtNum) ?? "—")
                        stat("最低", vals.min().map(fmtNum) ?? "—")
                        stat("及格率", vals.isEmpty ? "—" : "\(Int((Double(vals.filter { $0 / max(e.fullScore, 1) >= 0.6 }.count) / Double(vals.count) * 100).rounded()))%")
                        stat("缺考", "\(e.absent.count)")
                    }
                } footer: { Text("\(fmtDate(Date(timeIntervalSince1970: Double(e.date) / 1000)))\(e.subject.isEmpty ? "" : " · \(e.subject)") · 满分 \(fmtNum(e.fullScore))\(e.note.isEmpty ? "" : " · \(e.note)")") }
                if !store.readOnly {
                    Picker("", selection: $mode) { Text("成绩排名").tag(0); Text("录入成绩").tag(1) }.pickerStyle(.segmented)
                }
                if mode == 1 && !store.readOnly {
                    Section("录入成绩 · 满分 \(fmtNum(e.fullScore))（输入后按回车保存）") {
                        ForEach(c.students.sorted { $0.no.localizedStandardCompare($1.no) == .orderedAscending }) { s in
                            ExamScoreRow(exam: e, student: s)
                        }
                    }
                } else {
                    Section("成绩排名 · \(ranked.count) 人") {
                        ForEach(Array(ranked.enumerated()), id: \.element.0.id) { i, r in
                            let d = rules.exam.delta(e, r.0.id)
                            HStack {
                                Text("\(i + 1)").font(.headline).frame(width: 30).foregroundStyle(i < 3 ? Color.accentColor : .secondary)
                                VStack(alignment: .leading) {
                                    Text(r.0.name)
                                    Text("\(r.0.no) · 得分率 \(Int((r.1 / max(e.fullScore, 1) * 100).rounded()))%\(d != 0 ? " · 积分 \(signed(d))" : "")").font(.caption).foregroundStyle(.secondary)
                                }
                                Spacer()
                                Text(fmtNum(r.1)).font(.headline)
                            }
                        }
                    }
                    let absent = c.students.filter { e.absent.contains($0.id) }
                    if !absent.isEmpty { Section("缺考 · \(absent.count) 人") { Text(absent.map(\.name).joined(separator: "、")) } }
                    let none = c.students.filter { e.scores[$0.id] == nil && !e.absent.contains($0.id) }
                    if !none.isEmpty { Section("未录入 · \(none.count) 人") { Text(none.map(\.name).joined(separator: "、")).foregroundStyle(.secondary) } }
                }
            }
            .navigationTitle(e.title)
            .toolbar { if !store.readOnly { Button("修改") { editing = true } } }
            .sheet(isPresented: $editing) { ExamEditSheet(exam: e) { dismiss() } }
        } else {
            EmptyHint(emoji: "📝", title: "这场考试已被删除", detail: "")
        }
    }

    private func stat(_ label: String, _ value: String) -> some View {
        VStack(spacing: 2) { Text(value).font(.title3.bold()); Text(label).font(.caption).foregroundStyle(.secondary) }.frame(maxWidth: .infinity)
    }
}

/// 一位同学的成绩：输入后回车保存；可记为缺考
struct ExamScoreRow: View {
    @EnvironmentObject var store: Store
    let exam: Exam
    let student: Student
    @State private var text = ""
    @FocusState private var focused: Bool

    var body: some View {
        let absent = exam.absent.contains(student.id)
        let cur = exam.scores[student.id]
        HStack {
            VStack(alignment: .leading) { Text(student.name); Text(student.no).font(.caption).foregroundStyle(.secondary) }
            Spacer()
            if absent {
                Text("缺考").font(.caption.weight(.semibold)).padding(.horizontal, 8).padding(.vertical, 4).background(Color.gray.opacity(0.15), in: Capsule()).foregroundStyle(.secondary)
            } else {
                TextField("—", text: $text).keyboardType(.decimalPad).multilineTextAlignment(.trailing).frame(width: 90).textFieldStyle(.roundedBorder)
                    .focused($focused).onSubmit(commit)
                    .onChange(of: focused) { f in if !f { commit() } }
            }
            Button(absent ? "取消缺考" : "缺考") { store.setExamScore(exam.id, student.id, nil, absent: !absent) }.buttonStyle(.bordered).controlSize(.small)
        }
        .onAppear { text = cur.map(fmtNum) ?? "" }
        .onChange(of: cur) { v in if !focused { text = v.map(fmtNum) ?? "" } }
    }

    private func commit() {
        let t = text.trimmingCharacters(in: .whitespaces).replacingOccurrences(of: "，", with: ".")
        let cur = exam.scores[student.id]
        if t.isEmpty { if cur != nil { store.setExamScore(exam.id, student.id, nil) }; return }
        guard let x = Double(t), x >= 0, x.isFinite else { text = cur.map(fmtNum) ?? ""; return }
        if x != cur { store.setExamScore(exam.id, student.id, x) }
    }
}

struct ExamEditSheet: View {
    @EnvironmentObject var store: Store
    @Environment(\.dismiss) var dismiss
    let exam: Exam?
    var onDeleted: () -> Void = {}
    @State private var title = ""
    @State private var subject = ""
    @State private var full = "100"
    @State private var date = Date()
    @State private var note = ""
    @State private var auto = true
    @State private var confirmDelete = false

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("考试名称，如 第一单元测验", text: $title)
                    Picker("科目", selection: $subject) { Text("不填").tag(""); ForEach(store.data.subjects, id: \.self) { Text($0).tag($0) } }
                    HStack { Text("满分"); TextField("100", text: $full).keyboardType(.decimalPad).multilineTextAlignment(.trailing) }
                    DatePicker("日期", selection: $date, displayedComponents: .date)
                    TextField("备注（可不填）", text: $note)
                    Toggle(isOn: $auto) {
                        VStack(alignment: .leading) { Text("按得分率自动加减积分"); Text("分档与安卓一致").font(.caption).foregroundStyle(.secondary) }
                    }
                }
                if exam != nil {
                    Section { Button("删除这场考试", role: .destructive) { confirmDelete = true } }
                }
            }
            .navigationTitle(exam == nil ? "新建考试" : "修改考试")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("取消") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("保存") {
                        var e = exam ?? Exam(title: "")
                        e.title = title.trimmingCharacters(in: .whitespaces); e.subject = subject
                        e.fullScore = Double(full.replacingOccurrences(of: "，", with: ".")) ?? 100
                        e.date = Int64(date.timeIntervalSince1970 * 1000); e.note = note.trimmingCharacters(in: .whitespaces); e.autoScore = auto
                        store.upsertExam(e); store.say(exam == nil ? "已新建考试「\(e.title)」" : "已保存"); dismiss()
                    }
                    .disabled(title.trimmingCharacters(in: .whitespaces).isEmpty || (Double(full.replacingOccurrences(of: "，", with: ".")) ?? 0) <= 0)
                }
            }
            .confirmationDialog("删除这场考试？成绩会一起删除，积分随之重算。", isPresented: $confirmDelete, titleVisibility: .visible) {
                Button("删除", role: .destructive) { if let e = exam { store.deleteExam(e.id); dismiss(); onDeleted() } }
            }
            .onAppear {
                guard let e = exam else { return }
                title = e.title; subject = e.subject; full = fmtNum(e.fullScore); date = Date(timeIntervalSince1970: Double(e.date) / 1000); note = e.note; auto = e.autoScore
            }
        }
    }
}
