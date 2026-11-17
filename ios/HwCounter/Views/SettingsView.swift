import SwiftUI
import UniformTypeIdentifiers

struct SettingsView: View {
    @EnvironmentObject var store: Store
    @AppStorage("onboarded") private var onboarded = false
    @State private var email = ""
    @State private var password = ""
    @State private var history: [HistoryItem]? = nil
    @State private var restoring: HistoryItem? = nil
    @State private var changingPw = false
    @State private var backupURL: URL? = nil

    var body: some View {
        Form {
            cloudSection
            Section("主题") {
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
            Section("计分规则") {
                ruleRow("初始分", \.initial)
                ruleRow("已交", \.submitted)
                ruleRow("优秀", \.excellent)
                ruleRow("迟交", \.late)
                if !store.data.rules.escalation.enabled { ruleRow("未交", \.missing) }
                ruleRow("请假", \.leave)
                Toggle(isOn: Binding(get: { store.data.rules.streakBonus.enabled }, set: { v in store.setSetting { d in var r = d.rules; r.streakBonus.enabled = v; d.rules = r } })) {
                    VStack(alignment: .leading) {
                        Text("连续交满后每次加分")
                        Text("本轮连续按时交满 \(store.data.rules.streakBonus.after) 次后，之后每连续交一次 +\(fmtNum(store.data.rules.streakBonus.bonus))；请假不打断，未交、迟交打断；每次积分重置后重新计").font(.caption).foregroundStyle(.secondary)
                    }
                }
                Toggle(isOn: Binding(get: { store.data.rules.roundPoints }, set: { v in store.setSetting { d in var r = d.rules; r.roundPoints = v; d.rules = r } })) {
                    VStack(alignment: .leading) {
                        Text("积分取整")
                        Text("最终显示的积分：正分向上取整、负分向下取整；单笔加减分保留原值").font(.caption).foregroundStyle(.secondary)
                    }
                }
                Toggle(isOn: Binding(get: { store.data.rules.escalation.enabled }, set: { v in store.setSetting { d in var r = d.rules; r.escalation.enabled = v; d.rules = r } })) {
                    VStack(alignment: .leading) {
                        Text("连续未交自动扣分")
                        Text("\(store.data.rules.escalation.describe())；请假不打断，交过一次就重新算；本轮累计超过 \(store.data.rules.escalation.heavyAfter) 次后底数变为 \(fmtNum(store.data.rules.escalation.heavyBase))；每次积分重置后重新开始").font(.caption).foregroundStyle(.secondary)
                    }
                }
                if store.data.rules.escalation.enabled {
                    let e = store.data.rules.escalation
                    Picker("扣分方式", selection: escBinding(\.mode)) {
                        Text("① 正常加倍（\(MissingEscalationAmount.seq(e.base, 1))）").tag(1)
                        Text("② 第 2 次起加倍（\(MissingEscalationAmount.seq(e.base, 2))）").tag(2)
                        Text("③ 分段倍率（在 ① 或 ② 上分段乘倍率）").tag(3)
                    }
                    if e.mode == 3 {
                        Picker("分段基于", selection: escBinding(\.tierBase)) {
                            Text("① 正常加倍").tag(1)
                            Text("② 第 2 次起加倍").tag(2)
                        }
                        Picker("分段按", selection: escBinding(\.tierBy)) {
                            Text("连续未交次数").tag("streak")
                            Text("本轮累计未交次数").tag("total")
                        }
                        let tiers = e.tiers.sorted { $0.from < $1.from }
                        ForEach(Array(tiers.enumerated()), id: \.offset) { i, t in
                            HStack {
                                Stepper("第 \(i + 1) 段：从第 \(t.from) 次起", value: Binding(get: { t.from }, set: { v in
                                    guard v >= 1, !tiers.enumerated().contains(where: { $0.offset != i && $0.element.from == v }) else { return }
                                    setTiers(tiers.enumerated().map { $0.offset == i ? EscalationTier(from: v, factor: t.factor) : $0.element })
                                }), in: 1...50)
                                Stepper("×\(fmtNum(t.factor))", value: Binding(get: { t.factor }, set: { v in
                                    setTiers(tiers.enumerated().map { $0.offset == i ? EscalationTier(from: t.from, factor: v) : $0.element })
                                }), in: 0...20, step: 0.5).frame(maxWidth: 170)
                                if tiers.count > 1 {
                                    Button(role: .destructive) { setTiers(tiers.enumerated().filter { $0.offset != i }.map { $0.element }) } label: { Image(systemName: "minus.circle") }.buttonStyle(.borderless)
                                }
                            }
                        }
                        HStack {
                            Button("添加一段") { let last = tiers.last; setTiers(tiers + [EscalationTier(from: (last?.from ?? 0) + 2, factor: (last?.factor ?? 1) + 0.5)]) }
                            Spacer()
                            Button("恢复默认分段") { setTiers(MissingEscalation.defaultTiers) }
                        }.buttonStyle(.borderless)
                        Text("当前：\(e.tiersText())；连续未交依次扣 \(e.sample(nil, 6))；分段倍率乘完后再按单次上限封顶").font(.caption).foregroundStyle(.secondary)
                    }
                    Stepper("底数（第 1\(e.shape == 1 ? "" : "、2") 次扣）\(fmtNum(e.base))", value: escBinding(\.base), in: 1...10, step: 0.5)
                    Stepper("累计超过 \(e.heavyAfter) 次后加重", value: escBinding(\.heavyAfter), in: 1...50)
                    Stepper("加重后的底数 \(fmtNum(e.heavyBase))", value: escBinding(\.heavyBase), in: 1...10, step: 0.5)
                    Stepper("单次最多扣 \(e.cap > 0 ? fmtNum(e.cap) : "不限")", value: escBinding(\.cap), in: 0...200, step: 1)
                    Toggle(isOn: escBinding(\.gapBalance)) {
                        VStack(alignment: .leading) {
                            Text("间断未交平衡")
                            Text(e.gapBalance ? "隔几次不交一次也会逐次加重：次数取连续未交次数和最近 \(e.gapWindow) 次作业里的未交次数中较大的（请假、未登记不算）" : "已关闭：只看连续未交，交过一次就从第 1 次重新算").font(.caption).foregroundStyle(.secondary)
                        }
                    }
                    if e.gapBalance { Stepper("看最近 \(e.gapWindow) 次作业", value: escBinding(\.gapWindow), in: 2...30) }
                }
                Toggle(isOn: Binding(get: { store.data.rules.review.enabled }, set: { v in store.setSetting { d in var r = d.rules; r.review.enabled = v; d.rules = r } })) {
                    VStack(alignment: .leading) {
                        Text("作业复审")
                        Text("老师复审不合格：记一次未交（参与连续 / 累计未交），这一次扣分 ×\(fmtNum(store.data.rules.review.multiplier))；没有复审的默认合格").font(.caption).foregroundStyle(.secondary)
                    }
                }
                if store.data.rules.review.enabled {
                    Stepper("复审不合格扣分倍数 ×\(fmtNum(store.data.rules.review.multiplier))", value: Binding(get: { store.data.rules.review.multiplier }, set: { v in store.setSetting { d in var r = d.rules; r.review.multiplier = v; d.rules = r } }), in: 1...5, step: 0.5)
                }
                VStack(alignment: .leading, spacing: 2) {
                    Text("信誉分加减分")
                    Text("信誉分 0–100，越优秀越高：交齐 25%、按时 15%、优秀 10%、连续交 10%、诚信（复审、抄袭）20%、进步 5%、考试 15%；小组信誉分为组员平均").font(.caption).foregroundStyle(.secondary)
                }
                Picker("个人信誉分", selection: creditBinding(\.personal)) { ForEach(CreditRules.modes.map { $0.0 }, id: \.self) { m in Text(CreditRules.label(m)).tag(m) } }
                Picker("小组信誉分", selection: creditBinding(\.group)) { ForEach(CreditRules.modes.map { $0.0 }, id: \.self) { m in Text(CreditRules.label(m)).tag(m) } }
                if store.data.rules.credit.active {
                    Picker("结算周期", selection: creditBinding(\.period)) {
                        Text("每次作业").tag("assignment"); Text("每天").tag("day"); Text("每周").tag("week"); Text("每月").tag("month")
                    }
                    Text("\(store.data.rules.credit.bandsText)，其余 0；只加不扣：扣分的段记 0；只扣不加：加分的段记 0；修改后历史积分自动重算（分档可在安卓上调整）").font(.caption).foregroundStyle(.secondary)
                }
                Toggle(isOn: Binding(get: { store.data.rules.plagiarism.enabled }, set: { v in store.setSetting { d in var r = d.rules; r.plagiarism.enabled = v; d.rules = r } })) {
                    VStack(alignment: .leading) {
                        Text("抄袭处罚")
                        Text(store.data.rules.plagiarism.text + "；在作业里长按同学选「抄袭」，侧边栏「抄袭关系」看关系图").font(.caption).foregroundStyle(.secondary)
                    }
                }
                if store.data.rules.plagiarism.enabled {
                    Stepper("抄袭扣分倍数 ×\(fmtNum(store.data.rules.plagiarism.multiplier))", value: Binding(get: { store.data.rules.plagiarism.multiplier }, set: { v in store.setSetting { d in var r = d.rules; r.plagiarism.multiplier = v; d.rules = r } }), in: 1...5, step: 0.5)
                    Toggle("累犯逐次加重（每轮重新计次）", isOn: Binding(get: { store.data.rules.plagiarism.escalate }, set: { v in store.setSetting { d in var r = d.rules; r.plagiarism.escalate = v; d.rules = r } }))
                    if store.data.rules.plagiarism.escalate {
                        Stepper("累犯最多再乘 ×\(store.data.rules.plagiarism.maxTimes)", value: Binding(get: { store.data.rules.plagiarism.maxTimes }, set: { v in store.setSetting { d in var r = d.rules; r.plagiarism.maxTimes = v; d.rules = r } }), in: 1...10)
                    }
                }
            }
            .disabled(store.readOnly)
            Section("语音登记") {
                Picker("模糊程度", selection: Binding(get: { store.data.voiceFuzzy }, set: { v in store.setSetting { $0.voiceFuzzy = v } })) {
                    Text("严格").tag(0); Text("标准").tag(1); Text("宽松").tag(2)
                }
                .pickerStyle(.segmented)
                Toggle(isOn: Binding(get: { store.data.voiceInitials }, set: { v in store.setSetting { $0.voiceInitials = v } })) {
                    VStack(alignment: .leading) {
                        Text("拼音首字母匹配")
                        Text("首字母一致即登记，如 张锦宇 = ZJY；可直接念字母“Z J Y”，连着念几个人也行").font(.caption).foregroundStyle(.secondary)
                    }
                }
            }
            Section("帮助") {
                Button { onboarded = false } label: { Label("重新查看新手引导", systemImage: "questionmark.circle") }
            }
            Section("数据") {
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
        } header: { Text("云端同步") }
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
            .navigationTitle("云端历史版本")
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("关闭") { history = nil } } }
            .confirmationDialog("恢复 v\(restoring?.version ?? 0)？本机数据会替换为这个版本，并上传为云端最新版本。", isPresented: Binding(get: { restoring != nil }, set: { if !$0 { restoring = nil } }), titleVisibility: .visible) {
                Button("恢复") {
                    if let h = restoring { Task { await store.restoreHistory(h.id) } }
                    restoring = nil; history = nil
                }
            }
        }
    }

    /// 修改未交扣分规则的某一项
    private func creditBinding<T>(_ k: WritableKeyPath<CreditRules, T>) -> Binding<T> {
        Binding(get: { store.data.rules.credit[keyPath: k] }, set: { v in store.setSetting { d in var r = d.rules; r.credit[keyPath: k] = v; d.rules = r } })
    }

    private func escBinding<T>(_ k: WritableKeyPath<MissingEscalation, T>) -> Binding<T> {
        Binding(get: { store.data.rules.escalation[keyPath: k] }, set: { v in store.setSetting { d in var r = d.rules; r.escalation[keyPath: k] = v; d.rules = r } })
    }

    private func setTiers(_ t: [EscalationTier]) {
        store.setSetting { d in var r = d.rules; r.escalation.tiers = t.sorted { $0.from < $1.from }; d.rules = r }
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
            .navigationTitle("修改密码")
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
