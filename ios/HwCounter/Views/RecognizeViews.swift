import SwiftUI
import PhotosUI
import UIKit

// ---------- 语音登记 ----------
struct VoiceSheet: View {
    @EnvironmentObject var store: Store
    @Environment(\.dismiss) var dismiss
    let assignmentId: String
    @StateObject private var mic = SpeechListener()
    @State private var log: [LogItem] = []
    @State private var typed = ""
    @State private var status: HwStatus = .SUBMITTED

    struct LogItem: Identifiable {
        let id = UUID()
        let heard: String
        let kind: String
        let names: String
        let ids: [String]
        let ambiguous: Bool
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 16) {
                Picker("登记为", selection: $status) {
                    ForEach([HwStatus.SUBMITTED, .EXCELLENT, .LATE, .MISSING, .LEAVE]) { s in Text("\(s.emoji) \(s.label)").tag(s) }
                }
                .pickerStyle(.segmented)

                Button { mic.listening ? mic.stop() : mic.start() } label: {
                    ZStack {
                        Circle().fill(mic.listening ? Color.red : Color.accentColor).frame(width: 110, height: 110)
                            .shadow(color: (mic.listening ? Color.red : Color.accentColor).opacity(0.4), radius: mic.listening ? 18 : 6)
                        Image(systemName: mic.listening ? "stop.fill" : "mic.fill").font(.system(size: 42)).foregroundStyle(.white)
                    }
                }
                .buttonStyle(.plain)
                Text(mic.listening ? (mic.partial.isEmpty ? "请连续念名字或学号…" : mic.partial) : "点麦克风开始，一个个念：张三、李四、三十四号…")
                    .font(.callout).foregroundStyle(.secondary).multilineTextAlignment(.center).frame(minHeight: 40)
                if let e = mic.error { Text(e).font(.callout).foregroundStyle(.red) }

                HStack {
                    TextField("也可以直接输入，如：张三 李四 34号", text: $typed).textFieldStyle(.roundedBorder).onSubmit(handleTyped)
                    Button("登记", action: handleTyped).buttonStyle(.bordered)
                }

                List {
                    ForEach(log) { item in
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(item.names).font(.headline)
                                Text("听到「\(item.heard)」· \(item.kind)").font(.caption).foregroundStyle(.secondary)
                            }
                            Spacer()
                            if item.ambiguous { Chip(text: "有重名/近音，请核对", color: .orange) }
                        }
                        .swipeActions {
                            Button(role: .destructive) {
                                store.setStatus(assignmentId, item.ids, .NONE)
                                log.removeAll { $0.id == item.id }
                            } label: { Label("撤销", systemImage: "arrow.uturn.backward") }
                        }
                    }
                }
                .listStyle(.plain)
            }
            .padding()
            .navigationTitle("🎙️ 语音登记")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("完成") { mic.stop(); dismiss() } } }
            .onAppear { mic.onFinal = { handle($0) } }
            .onDisappear { mic.stop() }
        }
    }

    private func handleTyped() {
        let t = typed.trimmingCharacters(in: .whitespaces)
        if !t.isEmpty { handle(t) }
        typed = ""
    }

    private func handle(_ text: String) {
        guard let c = store.data.currentClass else { return }
        let tol = [0.6, 1.0, 1.4][max(0, min(2, store.data.voiceFuzzy))]
        let parser = VoiceParser(roster: c.students, tolerance: tol, initials: store.data.voiceInitials)
        let r = parser.parse(text)
        let nameOf = Dictionary(uniqueKeysWithValues: c.students.map { ($0.id, "\($0.no) \($0.name)") })
        var newItems: [LogItem] = []
        for m in r.matches {
            // 有歧义（多个候选）时只登记第一个，并提示核对
            let ids = Array(m.ids.prefix(1))
            store.setStatus(assignmentId, ids, status)
            newItems.append(LogItem(heard: m.heard, kind: m.kind.rawValue, names: m.ids.compactMap { nameOf[$0] }.joined(separator: " / "), ids: ids, ambiguous: m.ids.count > 1))
        }
        if newItems.isEmpty {
            store.say("没听出名单里的名字：\(text)")
            UINotificationFeedbackGenerator().notificationOccurred(.warning)
        } else {
            UINotificationFeedbackGenerator().notificationOccurred(.success)
        }
        log.insert(contentsOf: newItems.reversed(), at: 0)
    }
}

// ---------- 拍照识别 ----------
struct PhotoRecognizeSheet: View {
    @EnvironmentObject var store: Store
    @Environment(\.dismiss) var dismiss
    let assignmentId: String
    @State private var item: PhotosPickerItem? = nil
    @State private var image: UIImage? = nil
    @State private var showCamera = false
    @State private var busy = false
    @State private var result: MatchOutput? = nil
    @State private var chosen: Set<String> = []

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    HStack(spacing: 12) {
                        if UIImagePickerController.isSourceTypeAvailable(.camera) {
                            Button { showCamera = true } label: { Label("拍照", systemImage: "camera.fill").frame(maxWidth: .infinity) }
                                .buttonStyle(.borderedProminent)
                        }
                        PhotosPicker(selection: $item, matching: .images) { Label("从相册选", systemImage: "photo.on.rectangle").frame(maxWidth: .infinity) }
                            .buttonStyle(.bordered)
                    }
                    .controlSize(.large)
                    Text("把作业本封面朝上摊开（或学号写在侧面竖排），尽量拍清楚学号").font(.caption).foregroundStyle(.secondary)

                    if let image {
                        Image(uiImage: image).resizable().scaledToFit().frame(maxHeight: 320)
                            .clipShape(RoundedRectangle(cornerRadius: 16))
                    }
                    if busy { ProgressView("正在识别…").padding() }
                    if let r = result, let c = store.data.currentClass { results(r, c) }
                }
                .padding()
            }
            .navigationTitle("📷 拍照识别")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("取消") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("登记 \(chosen.count) 人") {
                        store.setStatus(assignmentId, Array(chosen), .SUBMITTED)
                        if let n = result?.hits.count { store.editAssignment(assignmentId) { $0.countedBooks = max(n, $0.countedBooks ?? 0) } }
                        store.say("已登记 \(chosen.count) 人已交")
                        dismiss()
                    }
                    .disabled(chosen.isEmpty)
                }
            }
            .onChange(of: item) { it in
                guard let it else { return }
                Task {
                    if let d = try? await it.loadTransferable(type: Data.self), let img = UIImage(data: d) { await run(img) }
                }
            }
            .fullScreenCover(isPresented: $showCamera) {
                CameraPicker { img in showCamera = false; if let img { Task { await run(img) } } }.ignoresSafeArea()
            }
        }
    }

    private func results(_ r: MatchOutput, _ c: SchoolClass) -> some View {
        let byId = Dictionary(uniqueKeysWithValues: c.students.map { ($0.id, $0) })
        return VStack(alignment: .leading, spacing: 12) {
            Text("认出 \(r.hits.count) 本\(r.loose.isEmpty ? "" : "，另有 \(r.loose.count) 处疑似学号没对上")").font(.headline)
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 120), spacing: 10)], spacing: 10) {
                ForEach(r.hits) { h in
                    if let s = byId[h.studentId] {
                        let on = chosen.contains(s.id)
                        Button {
                            if on { chosen.remove(s.id) } else { chosen.insert(s.id) }
                        } label: {
                            VStack(spacing: 2) {
                                Text(s.name).font(.headline)
                                Text("\(s.no) · \(h.source.label)").font(.caption).foregroundStyle(.secondary)
                            }
                            .frame(maxWidth: .infinity).padding(10)
                            .background(on ? Color.green.opacity(0.18) : Color(.tertiarySystemFill), in: RoundedRectangle(cornerRadius: 12))
                            .overlay(RoundedRectangle(cornerRadius: 12).stroke(on ? Color.green : .clear, lineWidth: 2))
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            if !r.unknown.isEmpty || !r.loose.isEmpty {
                Text("没对上名单：\((r.unknown + r.loose).prefix(12).joined(separator: "、"))").font(.caption).foregroundStyle(.secondary)
            }
        }
    }

    private func run(_ img: UIImage) async {
        image = img; busy = true; result = nil
        let lines = await OCR.recognize(img)
        let roster = store.data.currentClass?.students ?? []
        let r = Matcher(roster: roster).match(lines)
        result = r
        chosen = Set(r.hits.map(\.studentId))
        busy = false
        UINotificationFeedbackGenerator().notificationOccurred(r.hits.isEmpty ? .warning : .success)
    }
}

struct CameraPicker: UIViewControllerRepresentable {
    var done: (UIImage?) -> Void
    func makeCoordinator() -> Coord { Coord(done: done) }
    func makeUIViewController(context: Context) -> UIImagePickerController {
        let p = UIImagePickerController()
        p.sourceType = .camera
        p.delegate = context.coordinator
        return p
    }
    func updateUIViewController(_ vc: UIImagePickerController, context: Context) {}

    final class Coord: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
        let done: (UIImage?) -> Void
        init(done: @escaping (UIImage?) -> Void) { self.done = done }
        func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
            done(info[.originalImage] as? UIImage)
        }
        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) { done(nil) }
    }
}
