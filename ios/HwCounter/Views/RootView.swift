import SwiftUI

@main
struct HwCounterApp: App {
    @StateObject private var store = Store()
    @Environment(\.scenePhase) private var phase

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(store)
                .tint(Palette.of(store.data.theme).primary)
        }
        .onChange(of: phase) { p in if p == .active { store.onActive() } }
    }
}

enum Pane: String, CaseIterable, Identifiable {
    case homework, scores, roster, settings
    var id: String { rawValue }
    var title: String { ["作业", "积分", "名单", "设置"][Pane.allCases.firstIndex(of: self)!] }
    var icon: String { ["checklist", "star.circle", "person.3", "gearshape"][Pane.allCases.firstIndex(of: self)!] }
}

struct RootView: View {
    @EnvironmentObject var store: Store
    @State private var section: Pane? = .homework
    @State private var showClasses = false

    var body: some View {
        NavigationSplitView {
            List(selection: $section) {
                Section {
                    Button { showClasses = true } label: { classHeader }
                        .buttonStyle(.plain)
                }
                Section {
                    ForEach(Pane.allCases) { s in
                        NavigationLink(value: s) { Label(s.title, systemImage: s.icon) }
                    }
                }
                if let c = store.data.currentClass, !c.assignments.isEmpty {
                    Section("今日概况") { TodayCard(c: c) }
                }
            }
            .navigationTitle("作业通")
            .safeAreaInset(edge: .bottom) { syncFooter }
        } detail: {
            NavigationStack {
                switch section ?? .homework {
                case .homework: AssignmentsView()
                case .scores: ScoresView()
                case .roster: RosterView()
                case .settings: SettingsView()
                }
            }
        }
        .safeAreaInset(edge: .bottom) {
            if store.readOnly { PreviewBanner { section = .settings } }
        }
        .overlay(alignment: .top) { toast }
        .sheet(isPresented: $showClasses) { ClassesSheet() }
        .sheet(isPresented: Binding(get: { store.sync.conflict != nil }, set: { _ in })) { ConflictSheet() }
        .animation(.spring(response: 0.3, dampingFraction: 0.8), value: store.toast)
    }

    private var classHeader: some View {
        let c = store.data.currentClass
        return HStack(spacing: 12) {
            Text(c?.emoji ?? "🎒").font(.system(size: 34))
                .frame(width: 54, height: 54)
                .background(Palette.of(store.data.theme).gradient.opacity(0.25), in: RoundedRectangle(cornerRadius: 14))
            VStack(alignment: .leading, spacing: 2) {
                Text(c?.name ?? "还没有班级").font(.headline)
                Text(c.map { "\($0.students.count) 名学生 · \($0.assignments.count) 次作业" } ?? (store.readOnly ? "等待老师公开数据" : "点这里新建班级"))
                    .font(.caption).foregroundStyle(.secondary)
            }
            Spacer()
            Image(systemName: "chevron.up.chevron.down").foregroundStyle(.secondary)
        }
        .contentShape(Rectangle())
    }

    @ViewBuilder private var syncFooter: some View {
        if store.sync.email != nil {
            HStack(spacing: 6) {
                if store.sync.busy { ProgressView().controlSize(.small) }
                else { Image(systemName: store.sync.error ? "exclamationmark.icloud" : "checkmark.icloud").foregroundStyle(store.sync.error ? .red : .green) }
                Text(store.sync.message.isEmpty ? "云同步已开启" : store.sync.message).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                Spacer()
            }
            .padding(.horizontal).padding(.vertical, 8)
            .background(.bar)
        }
    }

    @ViewBuilder private var toast: some View {
        if let t = store.toast {
            HStack(spacing: 12) {
                Text(t).font(.callout.weight(.medium))
                if store.canUndo && !store.readOnly {
                    Button("撤销") { store.undo(); store.toast = nil }.font(.callout.bold())
                }
            }
            .padding(.horizontal, 18).padding(.vertical, 12)
            .background(.regularMaterial, in: Capsule())
            .shadow(radius: 8, y: 3)
            .padding(.top, 8)
            .transition(.move(edge: .top).combined(with: .opacity))
        }
    }
}

struct TodayCard: View {
    let c: SchoolClass
    var body: some View {
        let a = c.assignments.sorted { $0.createdAt > $1.createdAt }.first!
        let s = c.summary(a)
        HStack(spacing: 12) {
            Ring(value: s.rate, color: .accentColor).frame(width: 46, height: 46)
            VStack(alignment: .leading, spacing: 3) {
                Text(a.title).font(.subheadline.bold()).lineLimit(1)
                Text("已交 \(s.handedIn)/\(s.expected) · 未交 \(s.c(.MISSING))").font(.caption).foregroundStyle(.secondary)
            }
        }
        .padding(.vertical, 4)
    }
}

/// 班级切换 / 新建 / 改名 / 删除
struct ClassesSheet: View {
    @EnvironmentObject var store: Store
    @Environment(\.dismiss) var dismiss
    @State private var editing: SchoolClass? = nil
    @State private var adding = false
    @State private var name = ""
    @State private var emoji = "🎒"
    private let emojis = ["🎒", "📚", "✏️", "🧪", "🌏", "🎨", "⚽️", "🎵", "🦊", "🐼", "🚀", "🌟"]

    var body: some View {
        NavigationStack {
            List {
                ForEach(store.data.classes) { c in
                    Button {
                        store.switchClass(c.id); dismiss()
                    } label: {
                        HStack {
                            Text(c.emoji).font(.title2)
                            VStack(alignment: .leading) {
                                Text(c.name).font(.headline)
                                Text("\(c.students.count) 名学生 · \(c.assignments.count) 次作业").font(.caption).foregroundStyle(.secondary)
                            }
                            Spacer()
                            if c.id == store.data.currentClass?.id { Image(systemName: "checkmark.circle.fill").foregroundStyle(Color.accentColor) }
                        }
                    }
                    .buttonStyle(.plain)
                    .swipeActions {
                        if !store.readOnly {
                            Button(role: .destructive) { store.deleteClass(c.id) } label: { Label("删除", systemImage: "trash") }
                            Button { editing = c; name = c.name; emoji = c.emoji } label: { Label("改名", systemImage: "pencil") }.tint(.orange)
                        }
                    }
                }
                if !store.readOnly {
                    Button { adding = true; name = ""; emoji = "🎒" } label: { Label("新建班级", systemImage: "plus.circle.fill") }
                }
            }
            .navigationTitle("班级")
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("完成") { dismiss() } } }
            .sheet(isPresented: Binding(get: { adding || editing != nil }, set: { if !$0 { adding = false; editing = nil } })) {
                NavigationStack {
                    Form {
                        TextField("班级名称，如 三年二班", text: $name)
                        LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: 6)) {
                            ForEach(emojis, id: \.self) { e in
                                Text(e).font(.largeTitle).padding(4)
                                    .background(e == emoji ? Color.accentColor.opacity(0.2) : .clear, in: RoundedRectangle(cornerRadius: 10))
                                    .onTapGesture { emoji = e }
                            }
                        }
                    }
                    .navigationTitle(editing == nil ? "新建班级" : "修改班级")
                    .toolbar {
                        ToolbarItem(placement: .cancellationAction) { Button("取消") { adding = false; editing = nil } }
                        ToolbarItem(placement: .confirmationAction) {
                            Button("保存") {
                                let n = name.trimmingCharacters(in: .whitespaces)
                                if let e = editing { store.renameClass(e.id, name: n, emoji: emoji) } else { store.addClass(name: n, emoji: emoji) }
                                adding = false; editing = nil
                            }.disabled(name.trimmingCharacters(in: .whitespaces).isEmpty)
                        }
                    }
                }
                .presentationDetents([.medium])
            }
        }
    }
}

/// 本机和云端都改过：选择怎么处理
struct ConflictSheet: View {
    @EnvironmentObject var store: Store
    var body: some View {
        VStack(spacing: 18) {
            Text("☁️").font(.system(size: 54))
            Text("本机和云端都有改动").font(.title2.bold())
            Text("可能是在别的设备（安卓 / 网页 / 另一台 iPad）上也改过。请选择怎么处理：").foregroundStyle(.secondary).multilineTextAlignment(.center)
            VStack(spacing: 10) {
                Button { Task { await store.resolve(2) } } label: { Label("合并两边（推荐）", systemImage: "arrow.triangle.merge").frame(maxWidth: .infinity) }
                    .buttonStyle(.borderedProminent)
                Button { Task { await store.resolve(0) } } label: { Label("使用云端的数据", systemImage: "icloud.and.arrow.down").frame(maxWidth: .infinity) }
                    .buttonStyle(.bordered)
                Button { Task { await store.resolve(1) } } label: { Label("用本机覆盖云端", systemImage: "icloud.and.arrow.up").frame(maxWidth: .infinity) }
                    .buttonStyle(.bordered).tint(.orange)
            }
            .controlSize(.large)
        }
        .padding(28)
        .interactiveDismissDisabled()
        .presentationDetents([.medium])
    }
}
