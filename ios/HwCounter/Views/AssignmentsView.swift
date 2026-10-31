import SwiftUI

struct AssignmentsView: View {
    @EnvironmentObject var store: Store
    @State private var creating = false
    @State private var query = ""
    @State private var showDrafts = false
    @State private var showStatTable = false
    @State private var openDraft: IdBox? = nil

    var body: some View {
        Group {
            if let c = store.data.currentClass {
                let list = c.assignments
                    .filter { query.isEmpty || $0.title.contains(query) || $0.subject.contains(query) }
                    .sorted { (x: Assignment, y: Assignment) -> Bool in x.pinned != y.pinned ? x.pinned : x.createdAt > y.createdAt }
                if c.assignments.isEmpty {
                    EmptyHint(emoji: "📝", title: "还没有作业", detail: store.readOnly ? "老师还没有布置作业" : "点右上角 ＋ 布置第一次作业，然后拍照或语音登记谁交了")
                } else {
                    ScrollView {
                        LazyVGrid(columns: [GridItem(.adaptive(minimum: 300), spacing: 16)], spacing: 16) {
                            ForEach(list) { a in
                                NavigationLink(value: a.id) { AssignmentCard(c: c, a: a) }
                                    .buttonStyle(.plain)
                                    .contextMenu {
                                        if !store.readOnly {
                                            Button { store.editAssignment(a.id) { $0.pinned.toggle() } } label: { Label(a.pinned ? "取消置顶" : "置顶", systemImage: "pin") }
                                            Button(role: .destructive) { store.deleteAssignment(a.id); store.say("已删除「\(a.title)」") } label: { Label("删除", systemImage: "trash") }
                                        }
                                    }
                            }
                        }
                        .padding()
                    }
                    .searchable(text: $query, prompt: "搜索作业")
                }
            } else {
                EmptyHint(emoji: "🎒", title: store.readOnly ? "暂无可预览的数据" : "先建一个班级", detail: store.readOnly ? "登录后可以创建自己的班级" : "点左上角班级卡片新建班级，再到「名单」导入学生")
            }
        }
        .navigationTitle("作业")
        .navigationDestination(for: String.self) { id in AssignmentDetailView(assignmentId: id) }
        .toolbar {
            if !store.readOnly && store.data.currentClass != nil {
                ToolbarItem(placement: .primaryAction) {
                    Button { creating = true } label: { Label("布置作业", systemImage: "plus") }
                }
                ToolbarItem(placement: .primaryAction) {
                    Button { showStatTable = true } label: { Label("统计表识别", systemImage: "tablecells") }
                }
                ToolbarItem(placement: .primaryAction) {
                    Button { showDrafts = true } label: { Label(store.drafts.isEmpty ? "草稿箱" : "草稿箱 \(store.drafts.count)", systemImage: "tray.full") }
                }
            }
        }
        .safeAreaInset(edge: .top) {
            if !store.readOnly, let c = store.data.currentClass, store.drafts.contains(where: { $0.classId == c.id }) {
                Button { showDrafts = true } label: {
                    Text("🗂️ 有 \(store.drafts.filter { $0.classId == c.id }.count) 份草稿只保存在本机，还没上传 · 查看 ›").font(.callout)
                        .frame(maxWidth: .infinity).padding(10)
                        .background(Color.yellow.opacity(0.18), in: RoundedRectangle(cornerRadius: 12))
                }
                .buttonStyle(.plain).padding(.horizontal)
            }
        }
        .sheet(isPresented: $creating) { NewAssignmentSheet { id in openDraft = IdBox(id: id) } }
        .sheet(isPresented: $showDrafts) { DraftListView() }
        .sheet(isPresented: $showStatTable) { StatTableView { id in openDraft = IdBox(id: id) } }
        .sheet(item: $openDraft) { b in NavigationStack { DraftDetailView(draftId: b.id) } }
        .refreshable { if store.readOnly { await store.refreshPreview() } else { await store.runSync() } }
    }
}

struct AssignmentCard: View {
    let c: SchoolClass
    let a: Assignment
    var body: some View {
        let s = c.summary(a)
        HStack(spacing: 14) {
            Ring(value: s.rate, color: s.rate >= 0.999 ? .green : .accentColor, line: 7).frame(width: 58, height: 58)
            VStack(alignment: .leading, spacing: 6) {
                HStack {
                    if a.pinned { Image(systemName: "pin.fill").font(.caption).foregroundStyle(.orange) }
                    Text(a.title).font(.headline).lineLimit(1)
                }
                HStack(spacing: 6) {
                    if !a.subject.isEmpty { Chip(text: a.subject, color: .accentColor) }
                    Text(fmtDate(a.date)).font(.caption).foregroundStyle(.secondary)
                }
                HStack(spacing: 10) {
                    Text("✅ \(s.handedIn)").font(.caption)
                    Text("❌ \(s.c(.MISSING))").font(.caption)
                    Text("🏖️ \(s.c(.LEAVE))").font(.caption)
                    Text("⏳ \(s.c(.NONE))").font(.caption).foregroundStyle(.secondary)
                }
            }
            Spacer(minLength: 0)
            Image(systemName: "chevron.right").foregroundStyle(.tertiary)
        }
        .padding(16)
        .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 20))
        .overlay(RoundedRectangle(cornerRadius: 20).stroke(Color.primary.opacity(0.06)))
    }
}

struct NewAssignmentSheet: View {
    @EnvironmentObject var store: Store
    @Environment(\.dismiss) var dismiss
    /// 存为草稿后打开它
    var onDraft: ((String) -> Void)? = nil
    @State private var title = ""
    @State private var subject = ""
    @State private var asDraft = false

    var body: some View {
        NavigationStack {
            Form {
                TextField("作业名称，如 数学练习册 P12", text: $title)
                Section {
                    Toggle(isOn: $asDraft) {
                        VStack(alignment: .leading) {
                            Text("📝 先存为草稿")
                            Text("登记只保存在本机，确认后再手动上传或定时上传").font(.caption).foregroundStyle(.secondary)
                        }
                    }
                }
                Section("科目") {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack {
                            ForEach(store.data.subjects, id: \.self) { s in
                                Button(s) { subject = subject == s ? "" : s }
                                    .buttonStyle(.bordered)
                                    .tint(subject == s ? .accentColor : .gray)
                            }
                        }
                    }
                }
                Section {
                    Text("日期：\(fmtDate(Date()))").foregroundStyle(.secondary)
                }
            }
            .navigationTitle("布置作业")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("取消") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button(asDraft ? "创建草稿" : "创建") {
                        let t = title.trimmingCharacters(in: .whitespaces)
                        let name = t.isEmpty ? "\(subject.isEmpty ? "" : subject)作业 \(fmtDate(Date()))" : t
                        if asDraft, let c = store.data.currentClass {
                            let d = StatTable.newDraft(c, nil, title: name, subject: subject)
                            store.addDraft(d); dismiss()
                            store.say("📝 已建草稿：只保存在本机，登记完再上传")
                            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { onDraft?(d.id) }
                        } else {
                            store.createAssignment(title: name, subject: subject)
                            dismiss()
                        }
                    }
                }
            }
        }
        .presentationDetents([.medium])
    }
}
