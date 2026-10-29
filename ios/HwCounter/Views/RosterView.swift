import SwiftUI
import UniformTypeIdentifiers

struct RosterView: View {
    @EnvironmentObject var store: Store
    @State private var editing: Student? = nil
    @State private var importing = false
    @State private var pasting = false
    @State private var pending: (String?, [Student])? = nil
    @State private var query = ""
    @State private var seatingOpen = false

    var body: some View {
        Group {
            if let c = store.data.currentClass {
                let list = c.students.filter { query.isEmpty || $0.name.contains(query) || $0.no.contains(query) }
                List {
                    if c.students.isEmpty {
                        EmptyHint(emoji: "👥", title: "还没有学生", detail: store.readOnly ? "老师还没有导入名单" : "点右上角导入 JSON / CSV 文件，或直接粘贴名单")
                    }
                    ForEach(list) { s in
                        Button { editing = s } label: {
                            HStack {
                                Text(s.no).font(.callout.monospacedDigit()).foregroundStyle(.secondary).frame(width: 64, alignment: .leading)
                                Text(s.name).font(.headline)
                                if !s.group.isEmpty { Chip(text: s.group, color: .purple) }
                                if s.longLeave { Chip(text: "长期请假", color: HwStatus.LEAVE.color) }
                                Spacer()
                            }
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .swipeActions {
                            if !store.readOnly { Button(role: .destructive) { store.deleteStudent(s.id) } label: { Label("删除", systemImage: "trash") } }
                        }
                    }
                }
                .searchable(text: $query, prompt: "搜索姓名或学号")
            } else {
                EmptyHint(emoji: "🎒", title: "还没有班级", detail: store.readOnly ? "登录后可以创建自己的班级" : "导入名单时会自动新建班级")
            }
        }
        .navigationTitle("名单")
        .toolbar {
            if !store.readOnly {
                ToolbarItemGroup(placement: .primaryAction) {
                    Menu {
                        Button { importing = true } label: { Label("导入文件（JSON / CSV / TXT）", systemImage: "doc") }
                        Button { pasting = true } label: { Label("粘贴名单", systemImage: "doc.on.clipboard") }
                    } label: { Label("导入", systemImage: "square.and.arrow.down") }
                    Button { seatingOpen = true } label: { Label("座位表", systemImage: "rectangle.grid.3x2") }
                        .disabled(store.data.currentClass == nil)
                    Button { editing = Student(no: "", name: "") } label: { Label("添加学生", systemImage: "person.badge.plus") }
                        .disabled(store.data.currentClass == nil)
                }
            }
        }
        .sheet(item: $editing) { s in StudentEditSheet(student: s) }
        .sheet(isPresented: $seatingOpen) { SeatingSheet() }
        .sheet(isPresented: $pasting) { PasteRosterSheet { text in parse(text) } }
        .fileImporter(isPresented: $importing, allowedContentTypes: [.json, .commaSeparatedText, .plainText, .text]) { r in
            guard case .success(let url) = r else { return }
            let ok = url.startAccessingSecurityScopedResource()
            defer { if ok { url.stopAccessingSecurityScopedResource() } }
            if let d = try? Data(contentsOf: url) {
                parse(String(data: d, encoding: .utf8) ?? String(decoding: d, as: UTF8.self))
            }
        }
        .confirmationDialog(pendingTitle, isPresented: Binding(get: { pending != nil }, set: { if !$0 { pending = nil } }), titleVisibility: .visible) {
            if let p = pending {
                if store.data.currentClass != nil {
                    Button("合并到当前班级（同学号更新姓名）") { store.importRoster(className: p.0, students: p.1, mode: 0); done(p.1.count) }
                    Button("替换当前班级（清空作业和积分）", role: .destructive) { store.importRoster(className: p.0, students: p.1, mode: 1); done(p.1.count) }
                }
                Button("新建班级「\(p.0 ?? "新班级")」") { store.importRoster(className: p.0, students: p.1, mode: 2); done(p.1.count) }
            }
        }
    }

    private var pendingTitle: String { "识别到 \(pending?.1.count ?? 0) 名学生，导入到哪里？" }

    private func parse(_ text: String) {
        do {
            let r = try RosterParser.parse(text)
            if r.1.isEmpty { store.say("没有读到学生，请检查格式"); return }
            pending = r
        } catch {
            store.say("文件格式不正确：\(error.localizedDescription)")
        }
    }

    private func done(_ n: Int) { pending = nil; store.say("已导入 \(n) 名学生") }
}

struct PasteRosterSheet: View {
    @Environment(\.dismiss) var dismiss
    var onDone: (String) -> Void
    @State private var text = ""
    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 10) {
                Text("每行一个学生：学号 姓名（逗号、空格、Tab 分隔都行）；也可以粘贴 JSON").font(.callout).foregroundStyle(.secondary)
                TextEditor(text: $text).font(.body.monospaced())
                    .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.secondary.opacity(0.3)))
            }
            .padding()
            .navigationTitle("粘贴名单")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("取消") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) { Button("识别") { let t = text; dismiss(); DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) { onDone(t) } }.disabled(text.isEmpty) }
            }
        }
    }
}

struct StudentEditSheet: View {
    @EnvironmentObject var store: Store
    @Environment(\.dismiss) var dismiss
    @State var student: Student

    var body: some View {
        let c = store.data.currentClass
        let isNew = !(c?.students.contains { $0.id == student.id } ?? false)
        NavigationStack {
            Form {
                Section {
                    TextField("学号", text: $student.no).keyboardType(.numbersAndPunctuation)
                    TextField("姓名", text: $student.name)
                    TextField("小组（可选）", text: $student.group)
                    Picker("性别", selection: $student.gender) { Text("未填").tag(""); Text("男").tag("男"); Text("女").tag("女") }
                    Toggle("长期请假（新作业自动记请假）", isOn: $student.longLeave)
                    TextField("备注", text: $student.note)
                }
                .disabled(store.readOnly)
                if let c, !isNew {
                    let st = c.stats(store.data.rules).first { $0.student.id == student.id }
                    Section("统计") {
                        LabeledContent("积分", value: fmtNum(st?.score ?? 0))
                        LabeledContent("已交 / 迟交 / 未交", value: "\(st?.submitted ?? 0) / \(st?.late ?? 0) / \(st?.missing ?? 0)")
                    }
                    Section("最近作业") {
                        ForEach(c.assignments.sorted { $0.createdAt > $1.createdAt }.prefix(10)) { a in
                            let s = a.status(student.id)
                            HStack { Text(a.title).lineLimit(1); Spacer(); Text("\(s.emoji) \(s.label)").foregroundStyle(s.color) }
                        }
                    }
                }
            }
            .navigationTitle(isNew ? "添加学生" : student.name)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button(store.readOnly ? "关闭" : "取消") { dismiss() } }
                if !store.readOnly {
                    ToolbarItem(placement: .confirmationAction) {
                        Button("保存") {
                            student.no = student.no.trimmingCharacters(in: .whitespaces)
                            student.name = student.name.trimmingCharacters(in: .whitespaces)
                            store.upsertStudent(student); dismiss()
                        }
                        .disabled(student.name.trimmingCharacters(in: .whitespaces).isEmpty)
                    }
                }
            }
        }
    }
}
