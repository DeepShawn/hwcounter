import Foundation
import SwiftUI

struct SyncState {
    var email: String? = nil
    var busy = false
    var message = ""
    var error = false
    var conflict: AppData? = nil
    var conflictVersion = 0
    var publicRead: Bool? = nil
    var previewState = "" // loading / ok / none / error
}

/// 数据中心：本机存储 + 撤销 + 云同步 + 未登录预览（只读）
@MainActor
final class Store: ObservableObject {
    @Published private(set) var data: AppData
    @Published var sync = SyncState()
    @Published var toast: String? = nil
    @Published private(set) var canUndo = false

    var readOnly: Bool { sync.email == nil }

    private let cloud = CloudClient.shared
    private var undoStack: [AppData] = []
    private var syncTask: Task<Void, Never>? = nil
    private var toastTask: Task<Void, Never>? = nil

    private static let dir = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
    private static let file = dir.appendingPathComponent("hwcounter.json")
    private static let previewFile = dir.appendingPathComponent("preview_cache.json")

    init() {
        let logged = CloudClient.shared.session != nil
        data = logged ? Store.loadMain() : Store.loadPreview()
        sync.email = CloudClient.shared.session?.email
        Task { await self.start() }
    }

    private func start() async {
        if cloud.session != nil { await runSync() } else { await refreshPreview() }
    }

    // ---------- 本机文件 ----------
    private static func read(_ url: URL) -> AppData? {
        guard let d = try? Data(contentsOf: url) else { return nil }
        return try? JSONDecoder().decode(AppData.self, from: d)
    }
    private static func loadMain() -> AppData { read(file) ?? AppData() }
    private static func loadPreview() -> AppData {
        var p = read(previewFile) ?? AppData()
        p.settings = loadMain().settings
        return p
    }
    private static func write(_ d: AppData, to url: URL) {
        guard let bytes = try? JSONEncoder().encode(d) else { return }
        try? bytes.write(to: url, options: .atomic)
    }

    // ---------- 提示 ----------
    func say(_ msg: String) {
        toast = msg
        toastTask?.cancel()
        toastTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: 3_000_000_000)
            if !Task.isCancelled { self?.toast = nil }
        }
    }

    // ---------- 修改 ----------
    func update(undoable: Bool = true, _ f: (inout AppData) -> Void) {
        let old = data
        var new = old
        f(&new)
        if new == old { return }
        if readOnly {
            // 预览模式：班级数据只读；只允许切换班级、改主题等本机显示设置
            if new.classes != old.classes { say("👀 预览模式：登录后才能修改"); return }
            data = new
            var main = Store.loadMain()
            main.settings = new.settings
            Store.write(main, to: Store.file)
            return
        }
        if undoable {
            undoStack.append(old)
            if undoStack.count > 30 { undoStack.removeFirst() }
        }
        data = new
        canUndo = !undoStack.isEmpty
        Store.write(new, to: Store.file)
        scheduleSync()
    }

    func undo() {
        guard !readOnly, let prev = undoStack.popLast() else { return }
        data = prev
        canUndo = !undoStack.isEmpty
        Store.write(prev, to: Store.file)
        scheduleSync()
    }

    private func replaceAll(_ d: AppData) {
        data = d
        Store.write(d, to: Store.file)
    }

    func updateClass(_ id: String? = nil, undoable: Bool = true, _ f: @escaping (inout SchoolClass) -> Void) {
        update(undoable: undoable) { d in
            guard let cid = id ?? d.currentClass?.id, let i = d.classes.firstIndex(where: { $0.id == cid }) else { return }
            f(&d.classes[i])
        }
    }

    func editAssignment(_ aid: String, _ f: @escaping (inout Assignment) -> Void) {
        updateClass { c in
            if let i = c.assignments.firstIndex(where: { $0.id == aid }) { f(&c.assignments[i]) }
        }
    }

    // 班级
    func switchClass(_ id: String) { update(undoable: false) { $0.currentClassId = id } }
    func addClass(name: String, emoji: String) {
        update { d in
            let c = SchoolClass(name: name, emoji: emoji)
            d.classes.append(c); d.currentClassId = c.id
        }
    }
    func renameClass(_ id: String, name: String, emoji: String) { updateClass(id) { $0.name = name; $0.emoji = emoji } }
    func deleteClass(_ id: String) {
        update { d in
            d.classes.removeAll { $0.id == id }
            if d.currentClassId == id { d.currentClassId = d.classes.first?.id }
        }
    }

    // 作业
    @discardableResult
    func createAssignment(title: String, subject: String) -> String {
        var a = Assignment(title: title, subject: subject)
        if let c = data.currentClass {
            for s in c.students where s.longLeave { a.statuses[s.id] = HwStatus.LEAVE.rawValue }
        }
        let new = a
        updateClass { $0.assignments.append(new) }
        return a.id
    }
    func deleteAssignment(_ id: String) { updateClass { $0.assignments.removeAll { $0.id == id } } }
    func setStatus(_ aid: String, _ ids: [String], _ st: HwStatus) {
        editAssignment(aid) { a in
            for id in ids { if st == .NONE { a.statuses.removeValue(forKey: id) } else { a.statuses[id] = st.rawValue } }
        }
    }
    func fillRemaining(_ aid: String, _ st: HwStatus) {
        updateClass { c in
            guard let i = c.assignments.firstIndex(where: { $0.id == aid }) else { return }
            for s in c.students where c.assignments[i].status(s.id) == .NONE { c.assignments[i].statuses[s.id] = st.rawValue }
        }
    }
    func setComment(_ aid: String, _ sid: String, _ text: String) {
        editAssignment(aid) { a in
            if text.trimmingCharacters(in: .whitespaces).isEmpty { a.comments.removeValue(forKey: sid) } else { a.comments[sid] = text }
        }
    }

    // 积分
    func addScore(_ ids: [String], _ delta: Double, _ reason: String) {
        updateClass { c in c.scores += ids.map { ScoreEntry(studentId: $0, delta: delta, reason: reason) } }
    }
    func removeScore(_ id: String) { updateClass { $0.scores.removeAll { $0.id == id } } }

    // 学生
    func upsertStudent(_ s: Student) {
        updateClass { c in
            if let i = c.students.firstIndex(where: { $0.id == s.id }) { c.students[i] = s } else { c.students.append(s) }
        }
    }
    func deleteStudent(_ id: String) {
        updateClass { c in
            c.students.removeAll { $0.id == id }
            c.scores.removeAll { $0.studentId == id }
            c.seating?.seats.removeValue(forKey: id)
            for i in c.assignments.indices {
                c.assignments[i].statuses.removeValue(forKey: id)
                c.assignments[i].comments.removeValue(forKey: id)
            }
        }
    }
    func importRoster(className: String?, students: [Student], mode: Int) {
        // mode 0 合并 1 替换 2 新班级
        if mode == 2 || data.classes.isEmpty {
            update { d in
                let c = SchoolClass(name: className ?? "新班级", students: students)
                d.classes.append(c); d.currentClassId = c.id
            }
            return
        }
        updateClass { c in
            if mode == 1 {
                c.students = students; c.assignments = []; c.scores = []
                if let n = className { c.name = n }
            } else {
                for n in students {
                    if let i = c.students.firstIndex(where: { $0.no == n.no }) {
                        c.students[i].name = n.name
                        if !n.group.isEmpty { c.students[i].group = n.group }
                        if !n.gender.isEmpty { c.students[i].gender = n.gender }
                    } else { c.students.append(n) }
                }
            }
        }
    }

    /// 修改当前班级的座位表（nil = 清空）
    func setSeating(_ f: @escaping (Seating?) -> Seating?) { updateClass { $0.seating = f($0.seating) } }

    func setSetting(_ f: (inout AppData) -> Void) { update(undoable: false, f) }

    // ---------- 预览 ----------
    func refreshPreview() async {
        guard cloud.session == nil else { return }
        sync.previewState = "loading"
        do {
            guard let row = try await cloud.pullPublic() else { sync.previewState = "none"; return }
            Store.write(row.data, to: Store.previewFile)
            guard cloud.session == nil else { return }
            var d = row.data
            d.settings = Store.loadMain().settings
            if let cur = data.currentClassId, d.classes.contains(where: { $0.id == cur }) { d.currentClassId = cur }
            data = d
            sync.previewState = "ok"
        } catch {
            sync.previewState = "error"
        }
    }

    // ---------- 账号 ----------
    func signIn(_ email: String, _ pw: String) async {
        sync.busy = true; sync.error = false; sync.message = "登录中…"
        do {
            try await cloud.signIn(email, pw)
            onLogin()
        } catch {
            sync.busy = false; sync.error = true; sync.message = error.localizedDescription
        }
    }

    func signUp(_ email: String, _ pw: String) async {
        sync.busy = true; sync.error = false; sync.message = "注册中…"
        do {
            if try await cloud.signUp(email, pw) { onLogin() }
            else { sync.busy = false; sync.message = "注册成功！请到邮箱点确认链接，然后回来登录" }
        } catch {
            sync.busy = false; sync.error = true; sync.message = error.localizedDescription
        }
    }

    func recover(_ email: String) async {
        do { try await cloud.recover(email); sync.message = "重置密码邮件已发送"; sync.error = false }
        catch { sync.message = error.localizedDescription; sync.error = true }
    }

    func changePassword(old: String, new: String) async throws {
        try await cloud.changePassword(old: old, new: new)
        say("✅ 密码已修改")
    }

    private func onLogin() {
        undoStack.removeAll(); canUndo = false
        data = Store.loadMain()
        sync = SyncState(email: cloud.session?.email, busy: false, message: "登录成功")
        Task { await runSync() }
    }

    func signOut() async {
        syncTask?.cancel()
        await cloud.signOut()
        undoStack.removeAll(); canUndo = false
        data = Store.loadPreview()
        sync = SyncState(message: "已退出登录，回到预览模式")
        await refreshPreview()
    }

    // ---------- 同步 ----------
    var autoSync: Bool {
        get { cloud.meta.auto }
        set { var m = cloud.meta; m.auto = newValue; cloud.meta = m; objectWillChange.send() }
    }
    var lastSyncAt: Double { cloud.meta.lastSyncAt }
    var baseVersion: Int { cloud.meta.baseVersion }

    func scheduleSync(_ ms: UInt64 = 4000) {
        syncTask?.cancel()
        guard cloud.session != nil, cloud.meta.auto, sync.conflict == nil else { return }
        syncTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: ms * 1_000_000)
            if Task.isCancelled { return }
            await self?.runSync()
        }
    }

    func onActive() {
        if cloud.session != nil { scheduleSync(300) } else { Task { await refreshPreview() } }
    }

    func runSync(retry: Int = 0) async {
        guard cloud.session != nil else { return }
        sync.busy = true; sync.error = false; sync.message = "同步中…"
        do {
            let (action, msg, pr) = try await syncOnce()
            sync.busy = false; sync.message = msg
            if let pr { sync.publicRead = pr }
            // 首次同步：默认开放“未登录预览”
            if !cloud.meta.previewInit, action != "conflict", action == "uploaded" || pr != nil {
                if (try? await cloud.setPublic(true)) != nil {
                    var m = cloud.meta; m.previewInit = true; cloud.meta = m
                    sync.publicRead = true
                }
            }
        } catch let e as CloudError where e.remoteVersion != nil && retry < 2 {
            await runSync(retry: retry + 1)
        } catch {
            sync.busy = false; sync.error = true; sync.message = error.localizedDescription
            if cloud.session == nil { await signOut() }
        }
    }

    private func syncOnce() async throws -> (String, String, Bool?) {
        var m = cloud.meta
        let local = data
        let localHash = CloudClient.hash(local)
        let changed = localHash != m.syncedHash
        let empty = local.classes.isEmpty
        let remote = try await cloud.pull()
        let pr = remote?.publicRead
        func upload(_ base: Int) async throws -> (String, String, Bool?) {
            let v = try await cloud.push(local, base: base)
            m.baseVersion = v; m.syncedHash = localHash; m.lastSyncAt = Date().timeIntervalSince1970 * 1000
            cloud.meta = m
            return ("uploaded", "已上传到云端", pr)
        }
        guard let remote else {
            return empty ? ("none", "云端还没有数据", nil) : try await upload(0)
        }
        if remote.version == m.baseVersion {
            if changed { return try await upload(remote.version) }
            m.lastSyncAt = Date().timeIntervalSince1970 * 1000; cloud.meta = m
            return ("none", "已是最新", pr)
        }
        if !changed || empty {
            replaceAll(remote.data)
            m.baseVersion = remote.version; m.syncedHash = CloudClient.hash(remote.data); m.lastSyncAt = Date().timeIntervalSince1970 * 1000
            cloud.meta = m
            return ("downloaded", "已从云端更新（来自 \(remote.device.isEmpty ? "其它设备" : remote.device)）", pr)
        }
        sync.conflict = remote.data
        sync.conflictVersion = remote.version
        return ("conflict", "本机和云端都有改动，请选择怎么处理", pr)
    }

    /// 0 用云端 1 用本机 2 合并
    func resolve(_ choice: Int) async {
        guard let remote = sync.conflict else { return }
        let v = sync.conflictVersion
        sync.conflict = nil; sync.busy = true; sync.message = "处理中…"
        do {
            if choice == 0 {
                replaceAll(remote)
                var m = cloud.meta
                m.baseVersion = v; m.syncedHash = CloudClient.hash(remote); m.lastSyncAt = Date().timeIntervalSince1970 * 1000
                cloud.meta = m
                sync.message = "已使用云端数据"
            } else {
                let d = choice == 2 ? CloudClient.merge(local: data, remote: remote) : data
                if choice == 2 { replaceAll(d) }
                try await pushAs(d, base: v)
                sync.message = choice == 2 ? "已合并并上传" : "已用本机覆盖云端"
            }
            sync.busy = false
        } catch {
            sync.busy = false; sync.error = true; sync.message = error.localizedDescription
        }
    }

    private func pushAs(_ d: AppData, base: Int) async throws {
        let v = try await cloud.push(d, base: base)
        var m = cloud.meta
        m.baseVersion = v; m.syncedHash = CloudClient.hash(d); m.lastSyncAt = Date().timeIntervalSince1970 * 1000
        cloud.meta = m
    }

    func setPublic(_ flag: Bool) async {
        do {
            try await cloud.setPublic(flag)
            sync.publicRead = flag
            var m = cloud.meta; m.previewInit = true; cloud.meta = m
            say(flag ? "已允许未登录预览" : "已关闭未登录预览")
        } catch { say(error.localizedDescription) }
    }

    func history() async -> [HistoryItem] {
        do { return try await cloud.history() } catch { say(error.localizedDescription); return [] }
    }

    func restoreHistory(_ id: Int64) async {
        do {
            let d = try await cloud.historyData(id)
            update { $0 = d }
            try await pushAs(d, base: -1)
            sync.message = "已恢复该历史版本"
        } catch { say(error.localizedDescription) }
    }

    // ---------- 备份 ----------
    func backupJSON() -> Data {
        let enc = JSONEncoder(); enc.outputFormatting = [.prettyPrinted, .sortedKeys]
        return (try? enc.encode(data)) ?? Data()
    }
}
