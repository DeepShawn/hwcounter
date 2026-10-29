import SwiftUI
import PhotosUI
import UniformTypeIdentifiers

/// 座位表格子图：讲台位置、排号（从讲台数起）、列号。编辑座位表和按座位登记共用
struct SeatChart<Cell: View>: View {
    let seating: Seating
    let students: [Student]
    var selected: (Int, Student?) -> Bool = { _, _ in false }
    var onCell: (Int, Student?) -> Void
    var onLong: ((Student) -> Void)? = nil
    var onRow: ((Int) -> Void)? = nil
    var onCol: ((Int) -> Void)? = nil
    @ViewBuilder var cell: (Student?) -> Cell

    private let w: CGFloat = 76

    var body: some View {
        let byId = Dictionary(students.map { ($0.id, $0) }, uniquingKeysWith: { a, _ in a })
        let grid = SeatingLogic.toGrid(seating, students)
        VStack(spacing: 8) {
            if seating.frontAtTop { front }
            ScrollView(.horizontal, showsIndicators: true) {
                VStack(alignment: .leading, spacing: 6) {
                    HStack(spacing: 6) {
                        Color.clear.frame(width: 40, height: 1)
                        ForEach(0..<seating.cols, id: \.self) { c in
                            Button("\(c + 1)列") { onCol?(c) }.font(.caption2).foregroundStyle(.secondary).frame(width: w).disabled(onCol == nil)
                        }
                    }
                    ForEach(Array(grid.enumerated()), id: \.offset) { r, row in
                        HStack(spacing: 6) {
                            Button("\(seating.frontAtTop ? r + 1 : seating.rows - r)排") { onRow?(r) }
                                .font(.caption2).foregroundStyle(.secondary).frame(width: 40).disabled(onRow == nil)
                            ForEach(Array(row.enumerated()), id: \.offset) { c, id in
                                let k = r * seating.cols + c
                                let st = id.flatMap { byId[$0] }
                                cell(st)
                                    .frame(width: w, height: 60)
                                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.accentColor, lineWidth: selected(k, st) ? 3 : 0))
                                    .contentShape(Rectangle())
                                    .onTapGesture { onCell(k, st) }
                                    .onLongPressGesture { if let st { onLong?(st) } }
                            }
                        }
                    }
                }
                .padding(.horizontal, 4)
            }
            if !seating.frontAtTop { front }
        }
    }

    private var front: some View {
        Text("讲  台").font(.callout.bold()).foregroundStyle(.secondary)
            .padding(.horizontal, 48).padding(.vertical, 6)
            .overlay(RoundedRectangle(cornerRadius: 10).stroke(style: StrokeStyle(lineWidth: 1, dash: [5])).foregroundStyle(.secondary))
            .frame(maxWidth: .infinity)
    }
}

/// 编辑座位表：照片识别 / 粘贴 / 文件导入 / 按学号自动排列，点两个座位互换
struct SeatingSheet: View {
    @EnvironmentObject var store: Store
    @Environment(\.dismiss) var dismiss
    @State private var sel: Int? = nil
    @State private var pick: String? = nil
    @State private var pasting = false
    @State private var pasteText = ""
    @State private var importing = false
    @State private var autoCols = 8
    @State private var showAuto = false
    @State private var photoItem: PhotosPickerItem? = nil
    @State private var showCamera = false
    @State private var busy = false
    @State private var report: String? = nil

    var body: some View {
        NavigationStack {
            Group {
                if let c = store.data.currentClass { content(c) } else { EmptyHint(emoji: "🏫", title: "还没有班级", detail: "") }
            }
            .navigationTitle("🪑 座位表")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("完成") { dismiss() } } }
        }
        .onChange(of: photoItem) { it in
            guard let it else { return }
            Task {
                if let d = try? await it.loadTransferable(type: Data.self), let img = UIImage(data: d) { await recognize(img) }
                photoItem = nil
            }
        }
        .fullScreenCover(isPresented: $showCamera) {
            CameraPicker { img in showCamera = false; if let img { Task { await recognize(img) } } }.ignoresSafeArea()
        }
        .fileImporter(isPresented: $importing, allowedContentTypes: [.plainText, .commaSeparatedText, .json, .text]) { r in
            guard case .success(let url) = r else { return }
            let ok = url.startAccessingSecurityScopedResource()
            defer { if ok { url.stopAccessingSecurityScopedResource() } }
            if let d = try? Data(contentsOf: url) { parseText(String(decoding: d, as: UTF8.self)) }
        }
        .sheet(isPresented: $pasting) { pasteSheet }
    }

    private func content(_ c: SchoolClass) -> some View {
        let unseated = c.students.filter { c.seating?.seats[$0.id] == nil }
        return ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 10) {
                        if UIImagePickerController.isSourceTypeAvailable(.camera) {
                            ActionButton(icon: "camera", title: "拍照识别") { showCamera = true }
                        }
                        PhotosPicker(selection: $photoItem, matching: .images) {
                            Label("相册照片识别", systemImage: "photo").font(.callout.weight(.semibold))
                                .padding(.horizontal, 14).padding(.vertical, 10)
                                .background(Color.accentColor.opacity(0.12), in: Capsule())
                        }
                        ActionButton(icon: "doc.on.clipboard", title: "粘贴座位表") { pasting = true }
                        ActionButton(icon: "doc", title: "从文件导入") { importing = true }
                        ActionButton(icon: "bolt", title: "按学号自动排列") { autoCols = c.seating?.cols ?? 8; showAuto = true }
                        if c.seating != nil {
                            ActionButton(icon: "arrow.triangle.2.circlepath", title: "讲台换边") { store.setSeating { $0.map(SeatingLogic.rotate) }; store.say("讲台已换边") }
                            ActionButton(icon: "trash", title: "清空") { store.setSeating { _ in nil }; store.say("已清空座位表") }
                        }
                    }
                }
                if busy { HStack { ProgressView(); Text("正在识别座位表照片…") } }
                if let r = report { Text(r).font(.caption).foregroundStyle(.secondary) }
                if showAuto {
                    HStack {
                        Stepper("每排 \(autoCols) 人，共 \((c.students.count + autoCols - 1) / max(1, autoCols)) 排", value: $autoCols, in: 1...20)
                        Button("排列") {
                            let s = SeatingLogic.autoArrange(c.students, cols: autoCols, frontAtTop: c.seating?.frontAtTop ?? true)
                            store.setSeating { _ in s }; store.say("已按学号排成 \(s.rows) 排 × \(s.cols) 列"); showAuto = false; report = nil
                        }.buttonStyle(.borderedProminent)
                    }
                }
                if let seating = c.seating {
                    HStack(spacing: 16) {
                        Stepper("排数 \(seating.rows)", onIncrement: { store.setSeating { $0.map { SeatingLogic.resize($0, rows: $0.rows + 1, cols: $0.cols) } } },
                                onDecrement: { store.setSeating { $0.map { SeatingLogic.resize($0, rows: $0.rows - 1, cols: $0.cols) } } })
                        Stepper("列数 \(seating.cols)", onIncrement: { store.setSeating { $0.map { SeatingLogic.resize($0, rows: $0.rows, cols: $0.cols + 1) } } },
                                onDecrement: { store.setSeating { $0.map { SeatingLogic.resize($0, rows: $0.rows, cols: $0.cols - 1) } } })
                    }
                    .frame(maxWidth: 520)
                    Text(pick != nil ? "点一个座位，把这位同学放进去" : sel != nil ? "再点另一个座位交换（空位也可以）" : "点两个座位互换位置；长按座位可把人移出")
                        .font(.caption).foregroundStyle(.secondary)
                    SeatChart(seating: seating, students: c.students, selected: { k, _ in k == sel }, onCell: { k, _ in tap(k) },
                              onLong: { st in store.setSeating { $0.map { SeatingLogic.remove($0, st.id) } }; store.say("已把 \(st.name) 移出座位"); sel = nil }) { st in
                        VStack(spacing: 2) {
                            if let st {
                                Text(st.name).font(.subheadline.bold()).lineLimit(1).minimumScaleFactor(0.7)
                                Text(st.no).font(.caption2).foregroundStyle(.secondary)
                            } else { Text("空").font(.caption2).foregroundStyle(.tertiary) }
                        }
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .background(st == nil ? Color.clear : Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 12))
                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.secondary.opacity(0.3), style: StrokeStyle(lineWidth: 1, dash: st == nil ? [4] : [])))
                    }
                } else {
                    EmptyHint(emoji: "🪑", title: "还没有座位表", detail: "拍一张座位表照片自动识别排列；或粘贴 Excel 里的座位表（每行一排，写姓名或学号，空位写“空”）；也可以按学号自动排列")
                }
                if c.seating != nil && !unseated.isEmpty {
                    Text("未入座 \(unseated.count) 人").font(.headline)
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 90))], spacing: 8) {
                        ForEach(unseated) { st in
                            Button(st.name) { pick = pick == st.id ? nil : st.id; sel = nil }
                                .buttonStyle(.bordered).tint(pick == st.id ? Color.accentColor : Color.gray)
                        }
                    }
                }
            }
            .padding()
        }
    }

    private func tap(_ k: Int) {
        if let p = pick { store.setSeating { $0.map { SeatingLogic.place($0, p, k) } }; pick = nil; return }
        guard let s = sel else { sel = k; return }
        if s == k { sel = nil; return }
        store.setSeating { $0.map { SeatingLogic.swap($0, s, k) } }
        sel = nil
    }

    private func apply(_ r: SeatingLogic.Result, _ how: String) {
        let n = r.seating.seats.count
        guard n > 0 else { store.say("没有认出名单里的学生，请检查内容"); return }
        store.setSeating { _ in r.seating }
        store.say("已按\(how)排好 \(n) 人")
        report = "已入座 \(n) 人" + (r.unseated.isEmpty ? "" : "，未入座 \(r.unseated.count) 人（点下方名字再点座位即可入座）")
            + (r.unmatched.isEmpty ? "" : "；没认出：" + r.unmatched.prefix(12).joined(separator: "、"))
        sel = nil
    }

    private func parseText(_ t: String) {
        guard let c = store.data.currentClass else { return }
        do { apply(try SeatingLogic.parseText(t, c.students), "座位表") } catch { store.say("解析失败：\(error.localizedDescription)") }
    }

    private func recognize(_ img: UIImage) async {
        guard let c = store.data.currentClass else { return }
        busy = true
        let pieces = await OCR.recognizePieces(img)
        apply(SeatingLogic.fromBoxes(pieces, c.students), "照片")
        busy = false
    }

    private var pasteSheet: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 10) {
                Text("每行一排、从讲台那边写起；单元格写姓名或学号，用 Tab（Excel 直接复制）、逗号或空格隔开，空位写“空”。可以加一行“讲台”表示讲台位置。").font(.callout).foregroundStyle(.secondary)
                TextEditor(text: $pasteText).font(.body.monospaced())
                    .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.secondary.opacity(0.3)))
            }
            .padding()
            .navigationTitle("粘贴座位表")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("取消") { pasting = false } }
                ToolbarItem(placement: .confirmationAction) { Button("解析并排座") { pasting = false; parseText(pasteText) }.disabled(pasteText.isEmpty) }
            }
        }
    }
}
