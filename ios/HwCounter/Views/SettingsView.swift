import SwiftUI
import UniformTypeIdentifiers

struct SettingsView: View {
    @EnvironmentObject var store: Store
    @State private var email = ""
    @State private var password = ""
    @State private var history: [HistoryItem]? = nil
    @State private var restoring: HistoryItem? = nil
    @State private var changingPw = false
    @State private var backupURL: URL? = nil

    var body: some View {
        Form {
            cloudSection
            Section("🎨 主题") {
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 140), spacing: 10)], spacing: 10) {
                    ForEach(Palette.all) { p in
                        Button { store.setSetting { $0.theme = p.id } } label: {
                            Text(p.name).font(.callout.bold()).foregroundStyle(.white)
                                .frame(maxWidth: .infinity).padding(.vertical, 12)
                                .background(p.gradient, in: RoundedRectangle(cornerRadius: 12))
                                .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.primary, lineWidth: store.data.theme == p.id ? 3 : 0))
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.vertical, 4)
            }
            Section("🧮 计分规则") {
                ruleRow("初始分", \.initial)
                ruleRow("✅ 已交", \.submitted)
                ruleRow("🌟 优秀", \.excellent)
                ruleRow("🐢 迟交", \.late)
                ruleRow("❌ 未交", \.missing)
                ruleRow("🏖️ 请假", \.leave)
            }
            .disabled(store.readOnly)
            Section("🎙️ 语音登记") {
                Picker("模糊程度", selection: Binding(get: { store.data.voiceFuzzy }, set: { v in store.setSetting { $0.voiceFuzzy = v } })) {
                    Text("严格").tag(0); Text("标准").tag(1); Text("宽松").tag(2)
                }
                .pickerStyle(.segmented)
                Toggle(isOn: Binding(get: { store.data.voiceInitials }, set: { v in store.setSetting { $0.voiceInitials = v } })) {
                    VStack(alignment: .leading) {
                        Text("拼音首字母匹配")
                        Text("首字母一致即登记，如 张锦宇 = ZJY").font(.caption).foregroundStyle(.secondary)
                    }
                }
            }
            Section("💾 数据") {
                Button { backupURL = writeBackup() } label: { Label("备份全部数据（JSON，与安卓/网页通用）", systemImage: "externaldrive") }
                if let c = store.data.currentClass {
                    Button { backupURL = Exporter.classCSV(c, store.data.rules) } label: { Label("导出本班统计表（CSV）", systemImage: "tablecells") }
                }
            }
            Section {
                Text("作业通 iPad 版 \(Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "")").font(.footnote).foregroundStyle(.secondary)
            }
        }
        .navigationTitle("设置")
        .sheet(item: Binding(get: { backupURL.map { ShareFile(url: $0) } }, set: { backupURL = $0?.url })) { f in ShareSheet(items: [f.url]) }
        .sheet(isPresented: $changingPw) { ChangePasswordSheet() }
        .sheet(isPresented: Binding(get: { history != nil }, set: { if !$0 { history = nil } })) { historySheet }
    }

    // ---------- 云端 ----------
    @ViewBuilder private var cloudSection: some View {
        Section {
            if let mail = store.sync.email {
                HStack {
                    Image(systemName: "checkmark.icloud.fill").foregroundStyle(.green).font(.title2)
                    VStack(alignment: .leading) {
                        Text(mail).font(.headline)
                        Text("云端版本 v\(store.baseVersion)\(store.lastSyncAt > 0 ? " · 上次同步 \(fmtDate(Date(timeIntervalSince1970: store.lastSyncAt / 1000)))" : "")")
                            .font(.caption).foregroundStyle(.secondary)
                    }
                    Spacer()
                    if store.sync.busy { ProgressView() }
                }
                Toggle(isOn: Binding(get: { store.autoSync }, set: { store.autoSync = $0; if $0 { store.scheduleSync(500) } })) {
                    VStack(alignment: .leading) {
                        Text("自动同步")
                        Text("改动后几秒自动上传；回到 App 时检查其它设备的更新").font(.caption).foregroundStyle(.secondary)
                    }
                }
                Toggle(isOn: Binding(get: { store.sync.publicRead != false }, set: { v in Task { await store.setPublic(v) } })) {
                    VStack(alignment: .leading) {
                        Text("允许未登录预览")
                        Text("打开后，任何人不登录也能只读查看你的班级数据（学生姓名、作业、积分）；关闭即恢复私密").font(.caption).foregroundStyle(.secondary)
                    }
                }
                .disabled(store.sync.publicRead == nil || store.sync.busy)
                HStack {
                    Button("立即同步") { Task { await store.runSync() } }.buttonStyle(.borderedProminent).disabled(store.sync.busy)
                    Button("历史版本") { Task { history = await store.history() } }.buttonStyle(.bordered)
                    Button("修改密码") { changingPw = true }.buttonStyle(.bordered)
                    Spacer()
                    Button("退出登录", role: .destructive) { Task { await store.signOut() } }.buttonStyle(.bordered)
                }
            } else {
                Text("现在是**预览模式**（只读）。登录后才能修改，数据自动在安卓、iPad、网页之间同步。").font(.callout)
                TextField("邮箱", text: $email).keyboardType(.emailAddress).textInputAutocapitalization(.never).autocorrectionDisabled()
                SecureField("密码（至少 6 位）", text: $password)
                HStack {
                    Button("登录") { Task { await store.signIn(email, password); if store.sync.email != nil { password = "" } } }
                        .buttonStyle(.borderedProminent).disabled(store.sync.busy || email.isEmpty || password.count < 6)
                    Button("注册") { Task { await store.signUp(email, password) } }.buttonStyle(.bordered)
                        .disabled(store.sync.busy || email.isEmpty || password.count < 6)
                    Spacer()
                    Button("忘记密码？") { Task { await store.recover(email) } }.buttonStyle(.borderless).disabled(!email.contains("@"))
                }
            }
            if !store.sync.message.isEmpty {
                Text(store.sync.message).font(.caption).foregroundStyle(store.sync.error ? .red : .secondary)
            }
        } header: { Text("☁️ 云端同步") }
    }

    private var historySheet: some View {
        NavigationStack {
            List {
                if history?.isEmpty ?? true { Text("还没有历史版本（每次云端被覆盖前会自动保存一份，最多 30 份）").foregroundStyle(.secondary) }
                ForEach(history ?? []) { h in
                    Button { restoring = h } label: {
                        VStack(alignment: .leading) {
                            Text("v\(h.version) · \(String(h.saved_at.prefix(10)))").font(.headline)
                            Text(h.device ?? "未知设备").font(.caption).foregroundStyle(.secondary)
                        }
                    }
                }
            }
            .navigationTitle("☁️ 云端历史版本")
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("关闭") { history = nil } } }
            .confirmationDialog("恢复 v\(restoring?.version ?? 0)？本机数据会替换为这个版本，并上传为云端最新版本。", isPresented: Binding(get: { restoring != nil }, set: { if !$0 { restoring = nil } }), titleVisibility: .visible) {
                Button("恢复") {
                    if let h = restoring { Task { await store.restoreHistory(h.id) } }
                    restoring = nil; history = nil
                }
            }
        }
    }

    private func ruleRow(_ title: String, _ key: WritableKeyPath<Rules, Double>) -> some View {
        Stepper(value: Binding(get: { store.data.rules[keyPath: key] }, set: { v in store.setSetting { d in var r = d.rules; r[keyPath: key] = v; d.rules = r } }), in: -20...200, step: 0.5) {
            HStack { Text(title); Spacer(); Text(signed(store.data.rules[keyPath: key])).monospacedDigit().foregroundStyle(.secondary) }
        }
    }

    private func writeBackup() -> URL? {
        let f = DateFormatter(); f.dateFormat = "yyyy-MM-dd"
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("作业通备份_\(f.string(from: Date())).json")
        do { try store.backupJSON().write(to: url); return url } catch { return nil }
    }
}

struct ChangePasswordSheet: View {
    @EnvironmentObject var store: Store
    @Environment(\.dismiss) var dismiss
    @State private var old = ""
    @State private var new1 = ""
    @State private var new2 = ""
    @State private var busy = false
    @State private var err: String? = nil

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    SecureField("旧密码", text: $old)
                    SecureField("新密码（至少 6 位）", text: $new1)
                    SecureField("再输一次新密码", text: $new2)
                } footer: {
                    Text("这个账号和你的游戏共用，改完后游戏里也用新密码登录。")
                }
                if let err { Text(err).foregroundStyle(.red) }
            }
            .navigationTitle("🔑 修改密码")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("取消") { dismiss() }.disabled(busy) }
                ToolbarItem(placement: .confirmationAction) {
                    Button(busy ? "提交中…" : "确认修改") { submit() }.disabled(busy || old.isEmpty || new1.isEmpty)
                }
            }
        }
        .presentationDetents([.medium])
    }

    private func submit() {
        if new1.count < 6 { err = "新密码至少 6 位"; return }
        if new1 != new2 { err = "两次输入的新密码不一样"; return }
        if new1 == old { err = "新密码不能和旧密码一样"; return }
        err = nil; busy = true
        Task {
            do { try await store.changePassword(old: old, new: new1); dismiss() }
            catch { err = error.localizedDescription }
            busy = false
        }
    }
}
