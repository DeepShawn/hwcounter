import SwiftUI
import PhotosUI

struct AssignmentDetailView: View {
    @EnvironmentObject var store: Store
    let assignmentId: String
    @State private var filter: HwStatus? = nil
    @State private var picked: Student? = nil
    @State private var showVoice = false
    @State private var showPhoto = false
    @State private var exportURL: URL? = nil
    @State private var seatMode: Bool? = nil
    @State private var pick: Set<String> = []
    @State private var typed = ""
    @State private var typedAs: HwStatus = .SUBMITTED
    @State private var ambiguous: [VoiceMatch] = []
    @State private var openDraft: IdBox? = nil

    var body: some View {
        if let c = store.data.currentClass, let a = c.assignments.first(where: { $0.id == assignmentId }) {
            content(c, a)
        } else {
            EmptyHint(emoji: "🗂️", title: "作业不存在", detail: "可能已在其它设备上删除")
        }
    }

    private func content(_ c: SchoolClass, _ a: Assignment) -> some View {
        let s = c.summary(a)
        let students = c.students.filter { filter == nil || a.status($0.id) == filter }
        return ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                header(c, a, s)
                if !store.readOnly, let dr = store.drafts.first(where: { $0.assignmentId == a.id }) {
                    Button { openDraft = IdBox(id: dr.id) } label: {
                        Text("📝 这次作业有一份未上传的草稿（改动 \(StatTable.changes(dr).count) 人），上传后才会写进这里 · 打开 ›").font(.callout)
                            .frame(maxWidth: .infinity, alignment: .leading).padding(12)
                            .background(Color.yellow.opacity(0.18), in: RoundedRectangle(cornerRadius: 12))
                    }.buttonStyle(.plain)
                }
                if !store.readOnly { actions(a); typedBar(c, a) }
                HStack {
                    if c.seating != nil {
                        Toggle(isOn: Binding(get: { seatMode ?? true }, set: { seatMode = $0; pick = [] })) { Label("座位表", systemImage: "rectangle.grid.3x2") }
                            .toggleStyle(.button)
                    }
                    filterBar(s)
                }
                if let seating = c.seating, seatMode ?? true {
                    seatView(c, a, seating)
                } else {
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 104), spacing: 12)], spacing: 12) {
                        ForEach(students) { st in
                            StudentTile(st: st, status: a.status(st.id), comment: a.comments[st.id])
                                .onTapGesture { tap(a, st) }
                                .contextMenu { if !store.readOnly { statusMenu(a, st) } }
                        }
                    }
                }
                if c.students.isEmpty {
                    EmptyHint(emoji: "👥", title: "班级里还没有学生", detail: "到「名单」导入学生名单（JSON / CSV / 直接粘贴）")
                }
            }
            .padding()
        }
        .navigationTitle(a.title)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Menu {
                    Button { exportURL = Xlsx.file("\(a.subject)_\(a.title)_统计名单", Xlsx.statListSheets(c, title: a.title, subject: a.subject, date: a.date, statuses: a.statuses, comments: a.comments)) } label: { Label("统计名单 Excel", systemImage: "list.bullet.rectangle") }
                    Button { exportURL = Xlsx.file("\(c.name)_各次作业统计名单", [Xlsx.classStatSheet(c)]) } label: { Label("全班各次统计名单 Excel", systemImage: "tablecells") }
                    Button { exportURL = Exporter.assignmentCSV(c, a) } label: { Label("本次 CSV", systemImage: "doc.text") }
                } label: { Label("导出", systemImage: "square.and.arrow.up") }
            }
        }
        .sheet(item: $picked) { st in StudentStatusSheet(assignmentId: a.id, student: st) }
        .sheet(isPresented: $showVoice) { VoiceSheet(assignmentId: a.id) }
        .sheet(isPresented: $showPhoto) { PhotoRecognizeSheet(assignmentId: a.id) }
        .sheet(item: $openDraft) { b in NavigationStack { DraftDetailView(draftId: b.id) } }
        .sheet(item: Binding(get: { exportURL.map { ShareFile(url: $0) } }, set: { exportURL = $0?.url })) { f in ShareSheet(items: [f.url]) }
    }

    private func header(_ c: SchoolClass, _ a: Assignment, _ s: Summary) -> some View {
        HStack(spacing: 20) {
            Ring(value: s.rate, color: s.rate >= 0.999 ? .green : .accentColor, line: 10).frame(width: 92, height: 92)
            VStack(alignment: .leading, spacing: 8) {
                HStack(spacing: 8) {
                    if !a.subject.isEmpty { Chip(text: a.subject, color: .accentColor) }
                    Text(fmtDate(a.date)).foregroundStyle(.secondary)
                }
                Text("已交 \(s.handedIn) / 应交 \(s.expected)").font(.title2.bold())
                HStack(spacing: 12) {
                    Text("🌟 优秀 \(s.c(.EXCELLENT))"); Text("🐢 迟交 \(s.c(.LATE))"); Text("❌ 未交 \(s.c(.MISSING))"); Text("🏖️ 请假 \(s.c(.LEAVE))")
                }
                .font(.callout).foregroundStyle(.secondary)
                if let n = a.countedBooks { Text("📚 实物本数：\(n)").font(.callout) }
            }
            Spacer()
        }
        .padding(18)
        .background(Palette.of(store.data.theme).gradient.opacity(0.12), in: RoundedRectangle(cornerRadius: 22))
    }

    private func actions(_ a: Assignment) -> some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 10) {
                ActionButton(icon: "camera.viewfinder", title: "拍照识别") { showPhoto = true }
                ActionButton(icon: "waveform", title: "语音登记") { showVoice = true }
                ActionButton(icon: "square.and.pencil", title: store.drafts.contains { $0.assignmentId == a.id } ? "打开草稿" : "草稿登记") {
                    if let dr = store.drafts.first(where: { $0.assignmentId == a.id }) { openDraft = IdBox(id: dr.id) }
                    else if let c = store.data.currentClass { let d = StatTable.newDraft(c, a); store.addDraft(d); openDraft = IdBox(id: d.id) }
                }
                ActionButton(icon: "xmark.circle", title: "其余记未交") { store.fillRemaining(a.id, .MISSING); store.say("其余学生已记为未交") }
                ActionButton(icon: "checkmark.circle", title: "其余记已交") { store.fillRemaining(a.id, .SUBMITTED); store.say("其余学生已记为已交") }
            }
        }
    }

    // ---------- 按座位表登记 ----------
    private func seatView(_ c: SchoolClass, _ a: Assignment, _ seating: Seating) -> some View {
        let grid = SeatingLogic.toGrid(seating, c.students)
        let unseated = c.students.filter { seating.seats[$0.id] == nil }
        func toggle(_ ids: [String]) {
            if store.readOnly { return }
            if ids.allSatisfy({ pick.contains($0) }) { pick.subtract(ids) } else { pick.formUnion(ids) }
        }
        return VStack(alignment: .leading, spacing: 10) {
            if !store.readOnly { Text("点座位切换 已交/未登记；点“排”“列”可整排/整列选中后一起登记；长按看详情").font(.caption).foregroundStyle(.secondary) }
            SeatChart(seating: seating, students: c.students, selected: { _, st in st.map { pick.contains($0.id) } ?? false },
                      onCell: { _, st in
                          guard let st else { return }
                          if !pick.isEmpty { toggle([st.id]) } else { tap(a, st) }
                      },
                      onLong: { st in picked = st },
                      onRow: { r in toggle(grid[r].compactMap { $0 }) },
                      onCol: { col in toggle(grid.compactMap { $0.indices.contains(col) ? $0[col] : nil }) }) { st in
                let x = st.map { a.status($0.id) } ?? .NONE
                VStack(spacing: 2) {
                    if let st {
                        Text(st.name).font(.subheadline.bold()).lineLimit(1).minimumScaleFactor(0.7)
                        Text("\(x.emoji) \(x.label)").font(.caption2.weight(.semibold)).foregroundStyle(x.color)
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(st == nil ? Color.clear : (x == .NONE ? Color(.secondarySystemGroupedBackground) : x.color.opacity(0.16)), in: RoundedRectangle(cornerRadius: 12))
                .overlay(RoundedRectangle(cornerRadius: 12).stroke(st == nil ? Color.secondary.opacity(0.2) : (x == .NONE ? Color.primary.opacity(0.08) : x.color.opacity(0.6)), lineWidth: 1.5))
            }
            if !unseated.isEmpty {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack {
                        Text("未入座").font(.caption).foregroundStyle(.secondary)
                        ForEach(unseated) { st in
                            let x = a.status(st.id)
                            Button("\(st.name) · \(x.label)") { tap(a, st) }.buttonStyle(.bordered).tint(x == .NONE ? Color.gray : x.color)
                        }
                    }
                }
            }
            if !pick.isEmpty && !store.readOnly {
                HStack(spacing: 8) {
                    Text("已选 \(pick.count) 人").font(.headline)
                    ForEach([HwStatus.SUBMITTED, .EXCELLENT, .LATE, .MISSING, .LEAVE, .NONE]) { st in
                        Button(st.label) {
                            store.setStatus(a.id, Array(pick), st); store.say("已将 \(pick.count) 人设为“\(st.label)”"); pick = []
                        }
                        .buttonStyle(.bordered).tint(st.color)
                    }
                    Button("取消") { pick = [] }
                }
                .padding(12)
                .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 16))
            }
        }
    }

    // ---------- 输入拼音首字母登记 ----------
    private func typedBar(_ c: SchoolClass, _ a: Assignment) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Image(systemName: "keyboard").foregroundStyle(.secondary)
                TextField("输入拼音首字母登记：zjy lzh wf（也可写学号、姓名）", text: $typed)
                    .textInputAutocapitalization(.never).autocorrectionDisabled()
                    .onSubmit { submitTyped(c, a) }
                Picker("登记为", selection: $typedAs) {
                    ForEach([HwStatus.SUBMITTED, .EXCELLENT, .LATE, .MISSING, .LEAVE]) { st in Text(st.label).tag(st) }
                }
                .pickerStyle(.menu)
                Button("登记") { submitTyped(c, a) }.buttonStyle(.borderedProminent).disabled(typed.trimmingCharacters(in: .whitespaces).isEmpty)
            }
            .padding(10)
            .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 14))
            ForEach(ambiguous) { m in
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack {
                        Text("“\(m.heard)”是谁？").font(.caption)
                        ForEach(m.ids, id: \.self) { id in
                            if let st = c.students.first(where: { $0.id == id }) {
                                Button("\(st.name) \(st.no)") {
                                    store.setStatus(a.id, [id], typedAs); ambiguous.removeAll { $0.id == m.id }; store.say("已登记 \(st.name)")
                                }.buttonStyle(.bordered)
                            }
                        }
                    }
                }
            }
        }
    }

    private func submitTyped(_ c: SchoolClass, _ a: Assignment) {
        let t = typed.trimmingCharacters(in: .whitespaces)
        guard !t.isEmpty else { return }
        let tol = [0.6, 1.0, 1.4][max(0, min(2, store.data.voiceFuzzy))]
        let r = VoiceParser(roster: c.students, tolerance: tol, initials: true).parse(t)
        var sure: [String] = []
        for m in r.matches where m.ids.count == 1 && !sure.contains(m.ids[0]) { sure.append(m.ids[0]) }
        ambiguous = r.matches.filter { $0.ids.count > 1 }
        if !sure.isEmpty {
            store.setStatus(a.id, sure, typedAs)
            let names = sure.compactMap { id in c.students.first { $0.id == id }?.name }
            store.say("已登记 \(sure.count) 人为“\(typedAs.label)”：" + names.joined(separator: "、") + (r.leftover.isEmpty ? "" : "；没认出：\(r.leftover)"))
        } else if ambiguous.isEmpty {
            store.say("没有认出名单里的同学" + (r.leftover.isEmpty ? "" : "：\(r.leftover)"))
        }
        typed = ""
    }

    private func filterBar(_ s: Summary) -> some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                FilterChip(title: "全部 \(s.total)", on: filter == nil) { filter = nil }
                ForEach(HwStatus.allCases) { st in
                    FilterChip(title: "\(st.emoji) \(st.label) \(s.c(st))", on: filter == st) { filter = filter == st ? nil : st }
                }
            }
        }
    }

    @ViewBuilder private func statusMenu(_ a: Assignment, _ st: Student) -> some View {
        ForEach(HwStatus.allCases) { s in
            Button { store.setStatus(a.id, [st.id], s) } label: { Text("\(s.emoji) \(s.label)") }
        }
    }

    /// 点一下：未登记 → 已交 → 未登记；其它状态打开详情
    private func tap(_ a: Assignment, _ st: Student) {
        if store.readOnly { picked = st; return }
        switch a.status(st.id) {
        case .NONE: store.setStatus(a.id, [st.id], .SUBMITTED)
        case .SUBMITTED: store.setStatus(a.id, [st.id], .NONE)
        default: picked = st
        }
    }
}

struct ActionButton: View {
    var icon: String
    var title: String
    var action: () -> Void
    var body: some View {
        Button(action: action) {
            Label(title, systemImage: icon).font(.callout.weight(.semibold))
                .padding(.horizontal, 14).padding(.vertical, 10)
                .background(Color.accentColor.opacity(0.12), in: Capsule())
        }
        .buttonStyle(.plain)
        .foregroundStyle(Color.accentColor)
    }
}

struct FilterChip: View {
    var title: String
    var on: Bool
    var action: () -> Void
    var body: some View {
        Button(action: action) {
            Text(title).font(.subheadline)
                .padding(.horizontal, 12).padding(.vertical, 7)
                .background(on ? Color.accentColor : Color(.tertiarySystemFill), in: Capsule())
                .foregroundStyle(on ? Color.white : Color.primary)
        }
        .buttonStyle(.plain)
    }
}

struct StudentTile: View {
    let st: Student
    let status: HwStatus
    var comment: String? = nil
    var body: some View {
        VStack(spacing: 4) {
            Text(st.no).font(.caption2.monospacedDigit()).foregroundStyle(.secondary)
            Text(st.name).font(.headline).lineLimit(1).minimumScaleFactor(0.7)
            Text("\(status.emoji) \(status.label)").font(.caption.weight(.semibold)).foregroundStyle(status.color)
        }
        .frame(maxWidth: .infinity, minHeight: 84)
        .padding(6)
        .background(status == .NONE ? Color(.secondarySystemGroupedBackground) : status.color.opacity(0.14), in: RoundedRectangle(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16).stroke(status == .NONE ? Color.primary.opacity(0.08) : status.color.opacity(0.5), lineWidth: 1.5))
        .overlay(alignment: .topTrailing) {
            if let comment, !comment.isEmpty { Image(systemName: "text.bubble.fill").font(.caption2).foregroundStyle(.orange).padding(6) }
        }
        .contentShape(Rectangle())
        .animation(.easeOut(duration: 0.15), value: status)
    }
}

struct StudentStatusSheet: View {
    @EnvironmentObject var store: Store
    @Environment(\.dismiss) var dismiss
    let assignmentId: String
    let student: Student
    @State private var comment = ""

    var body: some View {
        let a = store.data.currentClass?.assignments.first { $0.id == assignmentId }
        let cur = a?.status(student.id) ?? .NONE
        NavigationStack {
            Form {
                Section("状态") {
                    LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: 3), spacing: 10) {
                        ForEach(HwStatus.allCases) { s in
                            Button {
                                store.setStatus(assignmentId, [student.id], s); dismiss()
                            } label: {
                                VStack { Text(s.emoji).font(.title2); Text(s.label).font(.caption) }
                                    .frame(maxWidth: .infinity).padding(.vertical, 8)
                                    .background(s == cur ? s.color.opacity(0.25) : Color(.tertiarySystemFill), in: RoundedRectangle(cornerRadius: 12))
                            }
                            .buttonStyle(.plain)
                            .disabled(store.readOnly)
                        }
                    }
                }
                Section("评语") {
                    TextField("给这次作业写一句评语", text: $comment, axis: .vertical).disabled(store.readOnly)
                }
            }
            .navigationTitle("\(student.no) \(student.name)")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button(store.readOnly ? "关闭" : "保存") {
                        if !store.readOnly && comment != (a?.comments[student.id] ?? "") { store.setComment(assignmentId, student.id, comment) }
                        dismiss()
                    }
                }
            }
            .onAppear { comment = a?.comments[student.id] ?? "" }
        }
        .presentationDetents([.medium, .large])
    }
}

// ---------- 导出 / 分享 ----------
struct ShareFile: Identifiable { let url: URL; var id: String { url.path } }

struct ShareSheet: UIViewControllerRepresentable {
    let items: [Any]
    func makeUIViewController(context: Context) -> UIActivityViewController { UIActivityViewController(activityItems: items, applicationActivities: nil) }
    func updateUIViewController(_ vc: UIActivityViewController, context: Context) {}
}

enum Exporter {
    private static func esc(_ s: String) -> String {
        s.contains(where: { ",\"\n".contains($0) }) ? "\"" + s.replacingOccurrences(of: "\"", with: "\"\"") + "\"" : s
    }
    private static func write(_ name: String, _ rows: [[String]]) -> URL? {
        let text = "\u{FEFF}" + rows.map { $0.map(esc).joined(separator: ",") }.joined(separator: "\n")
        let safe = name.replacingOccurrences(of: "/", with: "-")
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("\(safe).csv")
        do { try text.write(to: url, atomically: true, encoding: .utf8); return url } catch { return nil }
    }

    static func assignmentCSV(_ c: SchoolClass, _ a: Assignment) -> URL? {
        var rows: [[String]] = [["学号", "姓名", "状态", "评语"]]
        for s in c.students { rows.append([s.no, s.name, a.status(s.id).label, a.comments[s.id] ?? ""]) }
        let sm = c.summary(a)
        rows.append([])
        rows.append(["日期", fmtDate(a.date)])
        rows.append(["已交", "\(sm.handedIn)", "应交", "\(sm.expected)"])
        return write("\(c.name)-\(a.title)", rows)
    }

    static func classCSV(_ c: SchoolClass, _ rules: Rules) -> URL? {
        let asg = c.assignments.sorted { $0.createdAt < $1.createdAt }
        var rows: [[String]] = [["学号", "姓名"] + asg.map { "\($0.title)（\(fmtDate($0.date))）" } + ["已交", "迟交", "未交", "积分"]]
        let stats = Dictionary(c.stats(rules).map { ($0.student.id, $0) }, uniquingKeysWith: { a, _ in a })
        for s in c.students {
            let st = stats[s.id]
            rows.append([s.no, s.name] + asg.map { $0.status(s.id).label } + ["\(st?.submitted ?? 0)", "\(st?.late ?? 0)", "\(st?.missing ?? 0)", fmtNum(st?.score ?? 0)])
        }
        rows.append([])
        rows.append(["导出日期", fmtDate(Date())])
        return write("\(c.name)-作业统计", rows)
    }
}
