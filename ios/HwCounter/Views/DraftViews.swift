import SwiftUI
import PhotosUI
import UniformTypeIdentifiers

struct IdBox: Identifiable, Hashable { let id: String }

func fmtTime(_ ms: Int64) -> String {
    let f = DateFormatter(); f.locale = Locale(identifier: "zh_CN"); f.dateFormat = "M月d日 HH:mm"
    return f.string(from: Date(timeIntervalSince1970: Double(ms) / 1000))
}

private let regStatuses: [HwStatus] = [.SUBMITTED, .EXCELLENT, .LATE, .MISSING, .LEAVE]

/// 定时上传：快捷时间 + 日期时间选择
struct ScheduleSheet: View {
    @Environment(\.dismiss) var dismiss
    let initial: Int64?
    let onSave: (Int64?) -> Void
    @State private var at = Date().addingTimeInterval(3600)

    var body: some View {
        NavigationStack {
            Form {
                Section("快捷") {
                    HStack {
                        Button("45 分钟后") { at = Date().addingTimeInterval(45 * 60) }
                        Button("1 小时后") { at = Date().addingTimeInterval(3600) }
                        Button("今晚 20:00") { at = clock(20, 0, Calendar.current.component(.hour, from: Date()) >= 20) }
                        Button("明早 7:00") { at = clock(7, 0, true) }
                    }
                    .buttonStyle(.bordered)
                }
                DatePicker("上传时间", selection: $at, in: Date()..., displayedComponents: [.date, .hourAndMinute])
                Text("到时间自动写进作业并上传到云端。需要 App 开着（可以在后台短暂运行）；如果到点时 App 没开，下次打开会立即上传。").font(.caption).foregroundStyle(.secondary)
                if initial != nil { Button("取消定时上传", role: .destructive) { onSave(nil); dismiss() } }
            }
            .navigationTitle("⏰ 定时上传")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("取消") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("确定") { onSave(Int64(max(at.timeIntervalSince1970, Date().timeIntervalSince1970 + 1) * 1000)); dismiss() }
                }
            }
            .onAppear { if let i = initial { at = Date(timeIntervalSince1970: Double(i) / 1000) } }
        }
        .presentationDetents([.medium, .large])
    }

    private func clock(_ h: Int, _ m: Int, _ addDay: Bool) -> Date {
        var d = Calendar.current.date(bySettingHour: h, minute: m, second: 0, of: Date()) ?? Date()
        if addDay { d = Calendar.current.date(byAdding: .day, value: 1, to: d) ?? d }
        return d
    }
}

// ---------------- 草稿页 ----------------
struct DraftDetailView: View {
    @EnvironmentObject var store: Store
    @Environment(\.dismiss) var dismiss
    let draftId: String
    @State private var filter = 0
    @State private var seatMode = true
    @State private var picked: Student? = nil
    @State private var schedule = false
    @State private var importing = false
    @State private var editing = false
    @State private var confirmDelete = false
    @State private var share: ShareFile? = nil
    @State private var typed = ""
    @State private var typedAs: HwStatus = .SUBMITTED
    @State private var ambiguous: [VoiceMatch] = []

    var body: some View {
        if let d = store.drafts.first(where: { $0.id == draftId }), let c = store.data.classes.first(where: { $0.id == d.classId }) {
            content(d, c)
        } else {
            EmptyHint(emoji: "🗂️", title: "草稿已上传或删除", detail: "可以在作业列表里查看").toolbar { ToolbarItem(placement: .confirmationAction) { Button("关闭") { dismiss() } } }
        }
    }

    private func content(_ d: Draft, _ c: SchoolClass) -> some View {
        let t = StatTable.tally(c.students, d.statuses)
        let changed = Set(StatTable.changes(d))
        let list = c.students.filter { s in
            let x = d.status(s.id)
            switch filter {
            case 1: return x == .NONE
            case 2: return x == .NONE || x == .MISSING
            case 3: return x.handedIn
            case 4: return changed.contains(s.id)
            default: return true
            }
        }
        return ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                HStack(spacing: 18) {
                    Ring(value: t.rate, color: .purple, line: 10).frame(width: 88, height: 88)
                    VStack(alignment: .leading, spacing: 6) {
                        Text("📝 草稿 · 只保存在本机 · \(d.isNew ? "新作业" : "已有作业") · \(c.name)").font(.caption).foregroundStyle(.secondary)
                        Button { editing = true } label: { Text(d.title.isEmpty ? "未命名作业" : d.title).font(.title2.bold()) }.buttonStyle(.plain)
                        Text("已交 \(t.handedIn) / 应交 \(t.expected) · 未交 \(t.count(.MISSING)) · 未登记 \(t.count(.NONE)) · ✏️ 改动 \(changed.count) 人").font(.callout).foregroundStyle(.secondary)
                        Text(d.scheduledAt.map { "⏰ 将在 \(fmtTime($0)) 自动上传" } ?? "还没上传：云端和其它设备暂时看不到这些登记").font(.callout)
                    }
                    Spacer()
                }
                .padding(18)
                .background(Color.purple.opacity(0.1), in: RoundedRectangle(cornerRadius: 22))
                HStack {
                    Button { if store.uploadDrafts([d.id]) > 0 { dismiss() } } label: { Label("上传到云端", systemImage: "icloud.and.arrow.up") }.buttonStyle(.borderedProminent)
                    Button { schedule = true } label: { Label(d.scheduledAt == nil ? "定时上传" : "改时间", systemImage: "alarm") }.buttonStyle(.bordered)
                }
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 10) {
                        ActionButton(icon: "tablecells", title: "从统计表导入") { importing = true }
                        ActionButton(icon: "xmark.circle", title: "剩余标未交") { store.setDraftStatus(d.id, (t.lists[.NONE] ?? []).map(\.id), .MISSING) }
                        ActionButton(icon: "list.bullet.rectangle", title: "统计名单 Excel") {
                            share = Xlsx.file("\(d.subject)_\(d.title)_统计名单", Xlsx.statListSheets(c, title: d.title, subject: d.subject, date: Date(timeIntervalSince1970: Double(d.createdAt) / 1000), statuses: d.statuses, comments: d.comments)).map { ShareFile(url: $0) }
                        }
                        ActionButton(icon: "trash", title: "删除草稿") { confirmDelete = true }
                    }
                }
                typedBar(d, c)
                HStack {
                    if c.seating != nil { Toggle(isOn: $seatMode) { Label("座位表", systemImage: "rectangle.grid.3x2") }.toggleStyle(.button) }
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(Array(["全部", "未登记", "未交", "已交", "改动"].enumerated()), id: \.offset) { i, l in
                                FilterChip(title: l, on: filter == i) { filter = i }
                            }
                        }
                    }
                }
                Text("点一下切换 已交/未登记 · 长按设置状态、备注 · ✏️ = 相对云端改过").font(.caption).foregroundStyle(.secondary)
                if let seating = c.seating, seatMode {
                    SeatChart(seating: seating, students: c.students, onCell: { _, st in if let st { toggle(d, st) } }, onLong: { st in picked = st }) { st in
                        let x = st.map { d.status($0.id) } ?? .NONE
                        VStack(spacing: 2) {
                            if let st {
                                Text((changed.contains(st.id) ? "✏️" : "") + st.name).font(.subheadline.bold()).lineLimit(1).minimumScaleFactor(0.7)
                                Text("\(x.emoji) \(x.label)").font(.caption2.weight(.semibold)).foregroundStyle(x.color)
                            }
                        }
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .background(st == nil ? Color.clear : (x == .NONE ? Color(.secondarySystemGroupedBackground) : x.color.opacity(0.16)), in: RoundedRectangle(cornerRadius: 12))
                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(st == nil ? Color.secondary.opacity(0.2) : (x == .NONE ? Color.primary.opacity(0.08) : x.color.opacity(0.6)), lineWidth: 1.5))
                    }
                    let unseated = c.students.filter { seating.seats[$0.id] == nil }
                    if !unseated.isEmpty {
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack {
                                Text("未入座").font(.caption).foregroundStyle(.secondary)
                                ForEach(unseated) { st in Button("\(st.name) · \(d.status(st.id).label)") { toggle(d, st) }.buttonStyle(.bordered) }
                            }
                        }
                    }
                } else {
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 104), spacing: 12)], spacing: 12) {
                        ForEach(list) { st in
                            StudentTile(st: st, status: d.status(st.id), comment: d.comments[st.id])
                                .overlay(alignment: .topLeading) { if changed.contains(st.id) { Text("✏️").font(.caption2).padding(6) } }
                                .onTapGesture { toggle(d, st) }
                                .contextMenu { ForEach(HwStatus.allCases) { s in Button("\(s.emoji) \(s.label)") { store.setDraftStatus(d.id, [st.id], s) } } }
                                .onLongPressGesture { picked = st }
                        }
                    }
                }
            }
            .padding()
        }
        .navigationTitle("草稿")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar { ToolbarItem(placement: .confirmationAction) { Button("完成") { dismiss() } } }
        .sheet(isPresented: $schedule) {
            ScheduleSheet(initial: d.scheduledAt) { at in
                store.editDraft(d.id) { $0.scheduledAt = at }
                store.say(at.map { "⏰ 将在 \(fmtTime($0)) 自动上传" } ?? "已取消定时上传")
            }
        }
        .sheet(isPresented: $importing) { StatTableView(draftId: d.id) }
        .sheet(isPresented: $editing) { DraftInfoSheet(draftId: d.id) }
        .sheet(item: $picked) { st in DraftStudentSheet(draftId: d.id, student: st) }
        .sheet(item: $share) { f in ShareSheet(items: [f.url]) }
        .confirmationDialog("删除这份草稿？里面的登记不会上传", isPresented: $confirmDelete, titleVisibility: .visible) {
            Button("删除", role: .destructive) { store.deleteDraft(d.id); dismiss() }
        }
    }

    private func toggle(_ d: Draft, _ st: Student) {
        let x = d.status(st.id)
        store.setDraftStatus(d.id, [st.id], x == .NONE || x == .MISSING ? .SUBMITTED : .NONE)
    }

    private func typedBar(_ d: Draft, _ c: SchoolClass) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Image(systemName: "keyboard").foregroundStyle(.secondary)
                TextField("输入拼音首字母登记：zjy lzh wf（也可写学号、姓名）", text: $typed)
                    .textInputAutocapitalization(.never).autocorrectionDisabled()
                    .onSubmit { submitTyped(d, c) }
                Picker("登记为", selection: $typedAs) { ForEach(regStatuses) { st in Text(st.label).tag(st) } }.pickerStyle(.menu)
                Button("登记") { submitTyped(d, c) }.buttonStyle(.borderedProminent).disabled(typed.trimmingCharacters(in: .whitespaces).isEmpty)
            }
            .padding(10)
            .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 14))
            ForEach(ambiguous) { m in
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack {
                        Text("“\(m.heard)”是谁？").font(.caption)
                        ForEach(m.ids, id: \.self) { id in
                            if let st = c.students.first(where: { $0.id == id }) {
                                Button("\(st.name) \(st.no)") { store.setDraftStatus(d.id, [id], typedAs); ambiguous.removeAll { $0.id == m.id } }.buttonStyle(.bordered)
                            }
                        }
                    }
                }
            }
        }
    }

    private func submitTyped(_ d: Draft, _ c: SchoolClass) {
        let t = typed.trimmingCharacters(in: .whitespaces)
        guard !t.isEmpty else { return }
        let tol = [0.6, 1.0, 1.4][max(0, min(2, store.data.voiceFuzzy))]
        let r = VoiceParser(roster: c.students, tolerance: tol, initials: true).parse(t)
        var sure: [String] = []
        for m in r.matches where m.ids.count == 1 && !sure.contains(m.ids[0]) { sure.append(m.ids[0]) }
        ambiguous = r.matches.filter { $0.ids.count > 1 }
        if !sure.isEmpty {
            store.setDraftStatus(d.id, sure, typedAs)
            store.say("草稿：" + sure.compactMap { id in c.students.first { $0.id == id }?.name }.joined(separator: "、") + " → \(typedAs.label)")
        } else if ambiguous.isEmpty { store.say("没有认出名单里的同学") }
        typed = ""
    }
}

struct DraftInfoSheet: View {
    @EnvironmentObject var store: Store
    @Environment(\.dismiss) var dismiss
    let draftId: String
    @State private var title = ""
    @State private var subject = ""
    var body: some View {
        NavigationStack {
            Form {
                TextField("作业名称", text: $title)
                Section("科目") {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack { ForEach(store.data.subjects, id: \.self) { s in Button(s) { subject = s }.buttonStyle(.bordered).tint(subject == s ? .accentColor : .gray) } }
                    }
                }
            }
            .navigationTitle("编辑草稿")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("取消") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) { Button("保存") { store.editDraft(draftId) { $0.title = title; $0.subject = subject }; dismiss() } }
            }
            .onAppear { if let d = store.drafts.first(where: { $0.id == draftId }) { title = d.title; subject = d.subject } }
        }
        .presentationDetents([.medium])
    }
}

struct DraftStudentSheet: View {
    @EnvironmentObject var store: Store
    @Environment(\.dismiss) var dismiss
    let draftId: String
    let student: Student
    @State private var comment = ""
    var body: some View {
        let d = store.drafts.first { $0.id == draftId }
        let cur = d?.status(student.id) ?? .NONE
        NavigationStack {
            Form {
                Section("草稿里的状态") {
                    LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: 3), spacing: 10) {
                        ForEach(HwStatus.allCases) { s in
                            Button { store.setDraftStatus(draftId, [student.id], s) } label: {
                                VStack { Text(s.emoji).font(.title2); Text(s.label).font(.caption) }
                                    .frame(maxWidth: .infinity).padding(.vertical, 8)
                                    .background(s == cur ? s.color.opacity(0.25) : Color(.tertiarySystemFill), in: RoundedRectangle(cornerRadius: 12))
                            }.buttonStyle(.plain)
                        }
                    }
                    if let d, !d.isNew { Text("云端现在：\((HwStatus(rawValue: d.baseStatuses[student.id] ?? "") ?? .NONE).label)").font(.caption).foregroundStyle(.secondary) }
                }
                Section("备注") { TextField("如：字迹潦草、缺第3页", text: $comment) }
            }
            .navigationTitle("\(student.no) \(student.name)")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("完成") { store.setDraftComment(draftId, student.id, comment); dismiss() } } }
            .onAppear { comment = d?.comments[student.id] ?? "" }
        }
        .presentationDetents([.medium, .large])
    }
}

// ---------------- 草稿箱 ----------------
struct DraftListView: View {
    @EnvironmentObject var store: Store
    @Environment(\.dismiss) var dismiss
    @State private var schedFor: Draft? = nil

    var body: some View {
        NavigationStack {
            List {
                if store.drafts.isEmpty {
                    EmptyHint(emoji: "📭", title: "没有草稿", detail: "布置作业时打开“先存为草稿”，或在作业页点“草稿登记”，也可以用“统计表识别”导入")
                }
                ForEach(store.drafts.sorted { $0.updatedAt > $1.updatedAt }) { d in
                    let c = store.data.classes.first { $0.id == d.classId }
                    NavigationLink(value: IdBox(id: d.id)) {
                        HStack {
                            VStack(alignment: .leading, spacing: 4) {
                                Text((d.isNew ? "🆕 " : "") + (d.title.isEmpty ? "未命名作业" : d.title)).font(.headline)
                                Text("\(c.map { "\($0.emoji) \($0.name)" } ?? "⚠️ 班级已删除") · \(d.subject) · 改动 \(StatTable.changes(d).count) 人 · 编辑于 \(fmtTime(d.updatedAt))").font(.caption).foregroundStyle(.secondary)
                                if let s = d.scheduledAt { Text("⏰ \(fmtTime(s)) 自动上传").font(.caption).foregroundStyle(Color.accentColor) }
                            }
                            Spacer()
                            if let c { let t = StatTable.tally(c.students, d.statuses); Text("\(t.handedIn)/\(t.expected)").font(.headline) }
                        }
                    }
                    .swipeActions {
                        Button(role: .destructive) { store.deleteDraft(d.id) } label: { Label("删除", systemImage: "trash") }
                        Button { schedFor = d } label: { Label("定时", systemImage: "alarm") }.tint(.orange)
                        Button { store.uploadDrafts([d.id]) } label: { Label("上传", systemImage: "icloud.and.arrow.up") }.tint(.green)
                    }
                }
            }
            .navigationTitle("🗂️ 草稿箱")
            .navigationDestination(for: IdBox.self) { b in DraftDetailView(draftId: b.id) }
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("关闭") { dismiss() } }
                if store.drafts.count > 1 { ToolbarItem(placement: .primaryAction) { Button("全部上传") { store.uploadDrafts(store.drafts.map(\.id)) } } }
            }
            .safeAreaInset(edge: .bottom) {
                Text("草稿只保存在这台 iPad 上，上传后才会进入云端数据库。左滑可上传 / 定时 / 删除").font(.caption).foregroundStyle(.secondary).padding()
            }
            .sheet(item: $schedFor) { d in
                ScheduleSheet(initial: d.scheduledAt) { at in store.editDraft(d.id) { $0.scheduledAt = at } }
            }
        }
    }
}

// ---------------- 统计表识别 ----------------
struct StatTableView: View {
    @EnvironmentObject var store: Store
    @Environment(\.dismiss) var dismiss
    /// 从草稿页打开时写回这份草稿
    var draftId: String? = nil
    /// 存成新草稿后打开它
    var onDraft: ((String) -> Void)? = nil
    @State private var res: StatTable.Result? = nil
    @State private var col = 0
    @State private var st: [String: String] = [:]
    @State private var src = ""
    @State private var busy = false
    @State private var item: PhotosPickerItem? = nil
    @State private var showCamera = false
    @State private var importing = false
    @State private var pasting = false
    @State private var pasteText = ""
    @State private var saveTitle: String? = nil
    @State private var saveSubject = ""
    @State private var intoPick: String? = nil
    @State private var share: ShareFile? = nil

    private var target: Draft? { draftId.flatMap { id in store.drafts.first { $0.id == id } } }
    private var cls: SchoolClass? { target.flatMap { t in store.data.classes.first { $0.id == t.classId } } ?? store.data.currentClass }

    var body: some View {
        NavigationStack {
            Group {
                if let c = cls { content(c) } else { EmptyHint(emoji: "🏫", title: "还没有班级", detail: "先建立班级、导入名单") }
            }
            .navigationTitle("📊 统计表识别")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("关闭") { dismiss() } } }
        }
    }

    private func content(_ c: SchoolClass) -> some View {
        let t = StatTable.tally(c.students, st)
        let header = res.flatMap { r in col < r.columns.count ? r.columns[col].header : nil } ?? ""
        let inTable = Set(res?.rows.map(\.studentId) ?? [])
        return ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                VStack(alignment: .leading, spacing: 8) {
                    Text(target.map { "导入到草稿：\($0.title)" } ?? "\(c.emoji) \(c.name) · 认出每个同学交没交，自动统计").font(.headline)
                    Text("拍照（登记表、点名册）、Excel（.xlsx）、CSV，或直接粘贴表格。每行一个同学（姓名或学号），后面是标记：").font(.callout)
                    Text("✓ √ 交 1 → 已交 · 优 A ★ → 优秀 · 迟 补 → 迟交 · × X 未交 缺 0 → 未交 · 假 病 → 请假 · 空白 → 未登记。多列（多次作业）时可以选用哪一列。").font(.caption).foregroundStyle(.secondary)
                    HStack {
                        PhotosPicker(selection: $item, matching: .images) { Label("相册", systemImage: "photo") }.buttonStyle(.borderedProminent)
                        if UIImagePickerController.isSourceTypeAvailable(.camera) { Button { showCamera = true } label: { Label("拍照", systemImage: "camera") }.buttonStyle(.bordered) }
                        Button { importing = true } label: { Label("Excel / CSV", systemImage: "doc") }.buttonStyle(.bordered)
                        Button { pasting = true } label: { Label("粘贴", systemImage: "doc.on.clipboard") }.buttonStyle(.bordered)
                    }
                    if busy { HStack { ProgressView(); Text("正在识别照片…").font(.callout) } }
                }
                .padding(16)
                .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 18))
                if let r = res {
                    VStack(alignment: .leading, spacing: 10) {
                        Text("\(src) · 认出 \(r.rows.count) 人\(r.unmatched.isEmpty ? "" : " · \(r.unmatched.count) 行没对上名单")").font(.caption).foregroundStyle(.secondary)
                        if r.columns.count > 1 {
                            Text("表里有 \(r.columns.count) 列标记，用哪一列：").font(.callout.bold())
                            ScrollView(.horizontal, showsIndicators: false) {
                                HStack { ForEach(Array(r.columns.enumerated()), id: \.offset) { k, cc in FilterChip(title: cc.header, on: k == col) { col = k; st = StatTable.columnStatuses(r, k) } } }
                            }
                        }
                        HStack(spacing: 16) {
                            Ring(value: t.rate, color: .accentColor, line: 9).frame(width: 80, height: 80)
                            VStack(alignment: .leading, spacing: 4) {
                                Text("已交 \(t.handedIn) / 应交 \(t.expected)").font(.title3.bold())
                                Text([HwStatus.SUBMITTED, .EXCELLENT, .LATE, .MISSING, .LEAVE, .NONE].map { "\($0.label) \(t.count($0))" }.joined(separator: " · ")).font(.callout).foregroundStyle(.secondary)
                                if t.count(.MISSING) > 0 { Text("❌ 未交：" + (t.lists[.MISSING] ?? []).map(\.name).joined(separator: "、")).font(.callout) }
                                if t.count(.NONE) > 0 { Text("⏳ 未登记：" + (t.lists[.NONE] ?? []).map(\.name).joined(separator: "、")).font(.callout).foregroundStyle(.secondary) }
                            }
                        }
                        HStack {
                            Button { share = Xlsx.file("\(header)_统计名单", Xlsx.statListSheets(c, title: header, subject: "", date: Date(), statuses: st)).map { ShareFile(url: $0) } } label: { Label("导出统计名单", systemImage: "square.and.arrow.up") }.buttonStyle(.bordered)
                            if let tg = target {
                                Button("写入这份草稿") { store.editDraft(tg.id) { $0.statuses.merge(st) { _, n in n } }; store.say("已导入 \(st.count) 人到草稿"); dismiss() }.buttonStyle(.borderedProminent)
                            } else {
                                if r.columns.count > 1 {
                                    Button("每列各存一份草稿") {
                                        store.addDrafts(r.columns.enumerated().map { k, cc in StatTable.newDraft(c, nil, title: cc.header.hasPrefix("第") && cc.header.hasSuffix("列") ? "\(fmtDate(Date())) 作业 \(k + 1)" : cc.header, statuses: StatTable.columnStatuses(r, k)) })
                                        store.say("已存为 \(r.columns.count) 份草稿，在草稿箱里查看"); dismiss()
                                    }.buttonStyle(.bordered)
                                }
                                if !c.assignments.isEmpty { Button("写入已有作业") { intoPick = c.assignments.sorted { $0.createdAt > $1.createdAt }.first?.id }.buttonStyle(.bordered) }
                                Button("存为新作业草稿") { saveTitle = header.isEmpty || (header.hasPrefix("第") && header.hasSuffix("列")) ? "\(fmtDate(Date())) 作业" : header; saveSubject = store.data.subjects.first ?? "" }.buttonStyle(.borderedProminent)
                            }
                        }
                    }
                    .padding(16)
                    .background(Color.accentColor.opacity(0.08), in: RoundedRectangle(cornerRadius: 18))
                    Text("核对一下：点状态可以改；没对上的行在最下面").font(.caption).foregroundStyle(.secondary)
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 220), spacing: 10)], spacing: 8) {
                        ForEach(c.students) { s in
                            let k = HwStatus(rawValue: st[s.id] ?? "") ?? .NONE
                            HStack {
                                VStack(alignment: .leading) {
                                    Text(s.name).font(.headline)
                                    Text(s.no + (inTable.contains(s.id) ? "" : " · 表里没有")).font(.caption).foregroundStyle(.secondary)
                                }
                                Spacer()
                                Menu {
                                    ForEach(HwStatus.allCases) { x in Button("\(x.emoji) \(x.label)") { if x == .NONE { st.removeValue(forKey: s.id) } else { st[s.id] = x.rawValue } } }
                                } label: { Text("\(k.emoji) \(k.label)").font(.callout.bold()).foregroundStyle(k.color) }
                            }
                            .padding(10)
                            .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 12))
                        }
                    }
                    if !r.unmatched.isEmpty {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("没对上名单的行（\(r.unmatched.count)）").font(.callout.bold())
                            ForEach(Array(r.unmatched.prefix(30).enumerated()), id: \.offset) { _, u in Text(u).font(.caption).foregroundStyle(.secondary) }
                        }
                    }
                }
            }
            .padding()
        }
        .onChange(of: item) { it in
            guard let it else { return }
            Task { if let d = try? await it.loadTransferable(type: Data.self), let img = UIImage(data: d) { await runPhoto(img, c) } }
        }
        .fullScreenCover(isPresented: $showCamera) {
            CameraPicker { img in showCamera = false; if let img { Task { await runPhoto(img, c) } } }.ignoresSafeArea()
        }
        .fileImporter(isPresented: $importing, allowedContentTypes: [UTType(filenameExtension: "xlsx") ?? .data, .commaSeparatedText, .tabSeparatedText, .plainText, .data]) { result in
            guard case .success(let url) = result else { return }
            let ok = url.startAccessingSecurityScopedResource()
            defer { if ok { url.stopAccessingSecurityScopedResource() } }
            guard let data = try? Data(contentsOf: url) else { store.say("文件打不开"); return }
            do {
                if data.starts(with: [0x50, 0x4B]) { use(StatTable.recognizeTable(try StatTable.readXlsx(data), c.students), "Excel：\(url.lastPathComponent)", c) }
                else if url.pathExtension.lowercased() == "xls" { store.say("老版 .xls 读不了：请在 Excel 里“另存为 .xlsx 或 CSV”再导入") }
                else { use(StatTable.recognizeTable(StatTable.splitTable(String(decoding: data, as: UTF8.self)), c.students), "文件：\(url.lastPathComponent)", c) }
            } catch { store.say("读取失败：\(error.localizedDescription)") }
        }
        .sheet(isPresented: $pasting) {
            NavigationStack {
                VStack(alignment: .leading) {
                    Text("从 Excel / WPS / 在线表格里选中整张表复制，粘贴到这里").font(.callout).foregroundStyle(.secondary)
                    TextEditor(text: $pasteText).font(.body.monospaced()).border(Color.secondary.opacity(0.3))
                }
                .padding()
                .navigationTitle("📋 粘贴统计表")
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) { Button("取消") { pasting = false } }
                    ToolbarItem(placement: .confirmationAction) { Button("识别") { pasting = false; use(StatTable.recognizeTable(StatTable.splitTable(pasteText), c.students), "粘贴的表格", c) }.disabled(pasteText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty) }
                }
            }
        }
        .sheet(isPresented: Binding(get: { saveTitle != nil }, set: { if !$0 { saveTitle = nil } })) {
            NavigationStack {
                Form {
                    TextField("作业名称", text: Binding(get: { saveTitle ?? "" }, set: { saveTitle = $0 }))
                    Section("科目") {
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack { ForEach(store.data.subjects, id: \.self) { s in Button(s) { saveSubject = s }.buttonStyle(.bordered).tint(saveSubject == s ? .accentColor : .gray) } }
                        }
                    }
                }
                .navigationTitle("存为新作业草稿")
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) { Button("取消") { saveTitle = nil } }
                    ToolbarItem(placement: .confirmationAction) {
                        Button("保存") {
                            let d = StatTable.newDraft(c, nil, title: saveTitle ?? "", subject: saveSubject, statuses: st)
                            store.addDraft(d); saveTitle = nil
                            store.say("📝 已存为草稿（只保存在本机）")
                            dismiss(); onDraft?(d.id)
                        }
                    }
                }
            }
            .presentationDetents([.medium])
        }
        .sheet(isPresented: Binding(get: { intoPick != nil }, set: { if !$0 { intoPick = nil } })) {
            NavigationStack {
                List(c.assignments.sorted { $0.createdAt > $1.createdAt }) { a in
                    Button {
                        intoPick = nil
                        if let ex = store.drafts.first(where: { $0.assignmentId == a.id }) { store.editDraft(ex.id) { $0.statuses.merge(st) { _, n in n } }; dismiss(); onDraft?(ex.id) }
                        else { let d = StatTable.newDraft(c, a, statuses: st); store.addDraft(d); dismiss(); onDraft?(d.id) }
                        store.say("已写入“\(a.title)”的草稿，确认后再上传")
                    } label: { Text("\(fmtDate(a.date)) \(a.subject) \(a.title)") }
                }
                .navigationTitle("写入哪次作业（先存为草稿）")
                .toolbar { ToolbarItem(placement: .cancellationAction) { Button("取消") { intoPick = nil } } }
            }
        }
        .sheet(item: $share) { f in ShareSheet(items: [f.url]) }
    }

    private func use(_ r: StatTable.Result, _ from: String, _ c: SchoolClass) {
        if r.rows.isEmpty { store.say("没有认出名单里的同学，请检查表格（需要有姓名或学号一列）"); return }
        if r.columns.isEmpty { store.say("认出了学生，但没找到 ✓ × 优 迟 假 这类标记列") }
        res = r; col = max(0, r.columns.count - 1); st = StatTable.columnStatuses(r, col); src = from
    }

    private func runPhoto(_ img: UIImage, _ c: SchoolClass) async {
        busy = true
        let pieces = await OCR.recognizePieces(img)
        let lines = pieces.map { StatTable.Line(text: $0.text, x: $0.x, y: $0.y, h: $0.h) }
        use(StatTable.recognizeLines(StatTable.mergeRows(lines), c.students), "照片", c)
        busy = false
    }
}
