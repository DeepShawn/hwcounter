import Foundation
import Security
import CryptoKit
import UIKit

// Supabase 云同步（与安卓版、网页版同一个项目、同一套表和冲突规则）
enum CloudConfig {
    static let url = "https://beasxxexggnjcocdbtdl.supabase.co"
    static let key = "sb_publishable_85hufMmFQvmLQt2Rz1Hcrw_S4t7BjAO"
}

struct CloudSession: Codable {
    var accessToken: String
    var refreshToken: String
    var expiresAt: Double
    var userId: String
    var email: String
}

struct SyncMeta: Codable {
    var baseVersion: Int = 0
    var syncedHash: String = ""
    var lastSyncAt: Double = 0
    var auto: Bool = true
    var previewInit: Bool = false
}

struct CloudError: LocalizedError {
    let message: String
    var remoteVersion: Int? = nil
    var errorDescription: String? { message }
}

struct RemoteRow {
    var data: AppData
    var version: Int
    var updatedAt: String
    var device: String
    var publicRead: Bool?
}

struct HistoryItem: Identifiable, Decodable {
    let id: Int64
    let version: Int
    let device: String?
    let saved_at: String
}

/// 登录凭证存钥匙串
enum Keychain {
    private static let service = "com.deepshawn.hwcounter.cloud"
    private static let account = "session"

    static func load() -> CloudSession? {
        let q: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service,
                                kSecAttrAccount as String: account, kSecReturnData as String: true, kSecMatchLimit as String: kSecMatchLimitOne]
        var out: CFTypeRef?
        guard SecItemCopyMatching(q as CFDictionary, &out) == errSecSuccess, let d = out as? Data else { return nil }
        return try? JSONDecoder().decode(CloudSession.self, from: d)
    }

    static func save(_ s: CloudSession?) {
        let base: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service, kSecAttrAccount as String: account]
        SecItemDelete(base as CFDictionary)
        guard let s, let d = try? JSONEncoder().encode(s) else { return }
        var add = base
        add[kSecValueData as String] = d
        add[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlock
        SecItemAdd(add as CFDictionary, nil)
    }
}

final class CloudClient {
    static let shared = CloudClient()
    private let metaKey = "hw.cloud.meta"

    var session: CloudSession? = Keychain.load()
    var meta: SyncMeta {
        get { (UserDefaults.standard.data(forKey: metaKey).flatMap { try? JSONDecoder().decode(SyncMeta.self, from: $0) }) ?? SyncMeta() }
        set { UserDefaults.standard.set(try? JSONEncoder().encode(newValue), forKey: metaKey) }
    }

    static let device: String = {
        let kind = UIDevice.current.userInterfaceIdiom == .pad ? "iPad" : "iPhone"
        return "\(kind) · \(UIDevice.current.name)"
    }()

    private func friendly(_ status: Int, _ body: Any?) -> String {
        let o = body as? [String: Any]
        let raw = (o?["error_description"] ?? o?["msg"] ?? o?["message"] ?? o?["error"]) as? String ?? ""
        let l = raw.lowercased()
        if l.contains("invalid login credentials") { return "邮箱或密码不对" }
        if l.contains("email not confirmed") { return "邮箱还没验证：请到邮箱里点确认链接后再登录" }
        if l.contains("already registered") { return "这个邮箱已经注册过了，直接登录即可" }
        if l.contains("password should be at least") || l.contains("weak_password") { return "密码太短或太简单（至少 6 位）" }
        if l.contains("same_password") || l.contains("should be different") { return "新密码不能和旧密码一样" }
        if l.contains("rate limit") || status == 429 { return "操作太频繁，请稍后再试" }
        if l.contains("invalid email") || l.contains("unable to validate email") { return "邮箱格式不正确" }
        return "出错了（\(status)）：\(raw.isEmpty ? "未知错误" : raw)"
    }

    private func http(_ method: String, _ path: String, body: Any? = nil, bearer: String? = nil, prefer: String? = nil) async throws -> (Int, Any?) {
        guard let u = URL(string: CloudConfig.url + path) else { throw CloudError(message: "地址错误") }
        var req = URLRequest(url: u, timeoutInterval: 20)
        req.httpMethod = method
        req.setValue(CloudConfig.key, forHTTPHeaderField: "apikey")
        req.setValue("application/json", forHTTPHeaderField: "Accept")
        if let bearer { req.setValue("Bearer \(bearer)", forHTTPHeaderField: "Authorization") }
        if let prefer { req.setValue(prefer, forHTTPHeaderField: "Prefer") }
        if let body {
            req.setValue("application/json", forHTTPHeaderField: "Content-Type")
            req.httpBody = try JSONSerialization.data(withJSONObject: body)
        }
        let data: Data, resp: URLResponse
        do { (data, resp) = try await URLSession.shared.data(for: req) } catch { throw CloudError(message: "网络连接失败，请检查网络") }
        let status = (resp as? HTTPURLResponse)?.statusCode ?? 0
        let json = data.isEmpty ? nil : try? JSONSerialization.jsonObject(with: data, options: [.fragmentsAllowed])
        return (status, json)
    }

    private func toSession(_ j: Any?, email: String) -> CloudSession? {
        guard let o = j as? [String: Any], let at = o["access_token"] as? String else { return nil }
        let user = o["user"] as? [String: Any]
        let exp = (o["expires_in"] as? Double) ?? 3600
        return CloudSession(accessToken: at, refreshToken: o["refresh_token"] as? String ?? "",
                            expiresAt: Date().timeIntervalSince1970 + exp, userId: user?["id"] as? String ?? "",
                            email: user?["email"] as? String ?? email)
    }

    private func setSession(_ s: CloudSession?) { session = s; Keychain.save(s) }

    private func token() async throws -> String {
        guard let s = session else { throw CloudError(message: "请先登录云端账号") }
        if s.expiresAt - Date().timeIntervalSince1970 > 60 { return s.accessToken }
        let (st, j) = try await http("POST", "/auth/v1/token?grant_type=refresh_token", body: ["refresh_token": s.refreshToken])
        guard (200..<300).contains(st), let ns = toSession(j, email: s.email) else {
            if st >= 400 && st < 500 { setSession(nil) }
            throw CloudError(message: "登录已过期，请重新登录")
        }
        setSession(ns)
        return ns.accessToken
    }

    // ---------- 账号 ----------
    func signIn(_ email: String, _ password: String) async throws {
        let e = email.trimmingCharacters(in: .whitespaces)
        let (st, j) = try await http("POST", "/auth/v1/token?grant_type=password", body: ["email": e, "password": password])
        guard (200..<300).contains(st), let s = toSession(j, email: e) else { throw CloudError(message: friendly(st, j)) }
        setSession(s)
    }

    /// 返回 true 表示已直接登录；false 表示需要去邮箱验证
    func signUp(_ email: String, _ password: String) async throws -> Bool {
        let e = email.trimmingCharacters(in: .whitespaces)
        let (st, j) = try await http("POST", "/auth/v1/signup", body: ["email": e, "password": password, "data": ["username": "teacher", "app": "hwcounter"]])
        guard (200..<300).contains(st) else { throw CloudError(message: friendly(st, j)) }
        if let s = toSession(j, email: e) { setSession(s); return true }
        return false
    }

    func recover(_ email: String) async throws {
        let (st, j) = try await http("POST", "/auth/v1/recover", body: ["email": email.trimmingCharacters(in: .whitespaces)])
        guard (200..<300).contains(st) else { throw CloudError(message: friendly(st, j)) }
    }

    /// 修改密码：先用旧密码验证身份，再提交新密码
    func changePassword(old: String, new: String) async throws {
        guard let s = session else { throw CloudError(message: "请先登录云端账号") }
        let (cs, cj) = try await http("POST", "/auth/v1/token?grant_type=password", body: ["email": s.email, "password": old])
        guard (200..<300).contains(cs) else { throw CloudError(message: cs == 400 ? "旧密码不对" : friendly(cs, cj)) }
        let fresh = toSession(cj, email: s.email) ?? s
        setSession(fresh)
        let (st, j) = try await http("PUT", "/auth/v1/user", body: ["password": new], bearer: fresh.accessToken)
        guard (200..<300).contains(st) else { throw CloudError(message: friendly(st, j)) }
    }

    func signOut() async {
        if let s = session { _ = try? await http("POST", "/auth/v1/logout", body: [String: String](), bearer: s.accessToken) }
        setSession(nil)
        UserDefaults.standard.removeObject(forKey: metaKey)
    }

    // ---------- 数据 ----------
    private func decodeData(_ any: Any?) throws -> AppData {
        guard let any else { return AppData() }
        let d = try JSONSerialization.data(withJSONObject: any)
        return try JSONDecoder().decode(AppData.self, from: d)
    }

    func encodeJSON(_ d: AppData) throws -> Any {
        try JSONSerialization.jsonObject(with: JSONEncoder().encode(d))
    }

    func pull() async throws -> RemoteRow? {
        let uid = session?.userId ?? ""
        let filter = uid.isEmpty ? "" : "&user_id=eq.\(uid)"
        let (st, j) = try await http("GET", "/rest/v1/hw_snapshots?select=data,version,updated_at,device,public_read\(filter)&limit=1", bearer: try await token())
        guard (200..<300).contains(st) else { throw CloudError(message: friendly(st, j)) }
        guard let row = (j as? [[String: Any]])?.first else { return nil }
        return RemoteRow(data: try decodeData(row["data"]), version: row["version"] as? Int ?? 0,
                         updatedAt: row["updated_at"] as? String ?? "", device: row["device"] as? String ?? "",
                         publicRead: row["public_read"] as? Bool)
    }

    /// 未登录预览：读取老师公开的那份数据（只读）
    func pullPublic() async throws -> RemoteRow? {
        let (st, j) = try await http("GET", "/rest/v1/hw_snapshots?select=data,version,updated_at&public_read=eq.true&order=updated_at.desc&limit=1")
        guard (200..<300).contains(st) else { throw CloudError(message: friendly(st, j)) }
        guard let row = (j as? [[String: Any]])?.first else { return nil }
        return RemoteRow(data: try decodeData(row["data"]), version: row["version"] as? Int ?? 0,
                         updatedAt: row["updated_at"] as? String ?? "", device: "", publicRead: true)
    }

    func setPublic(_ flag: Bool) async throws {
        let uid = session?.userId ?? ""
        let (st, _) = try await http("PATCH", "/rest/v1/hw_snapshots?user_id=eq.\(uid)", body: ["public_read": flag], bearer: try await token(), prefer: "return=minimal")
        guard (200..<300).contains(st) else { throw CloudError(message: "设置失败，请稍后再试（如果提示字段不存在，需要先在 Supabase 运行 002_public_preview.sql）") }
    }

    func push(_ data: AppData, base: Int) async throws -> Int {
        let body: [String: Any] = ["p_data": try encodeJSON(data.stripped()), "p_base_version": base, "p_device": Self.device]
        let (st, j) = try await http("POST", "/rest/v1/rpc/hw_push", body: body, bearer: try await token())
        guard (200..<300).contains(st) else {
            let msg = (j as? [String: Any])?["message"] as? String ?? ""
            if msg.hasPrefix("CONFLICT:") { throw CloudError(message: "云端已有更新的版本", remoteVersion: Int(msg.dropFirst(9)) ?? -1) }
            throw CloudError(message: friendly(st, j))
        }
        let row = (j as? [[String: Any]])?.first ?? (j as? [String: Any])
        return row?["version"] as? Int ?? 0
    }

    func history() async throws -> [HistoryItem] {
        let (st, j) = try await http("GET", "/rest/v1/hw_snapshot_history?select=id,version,device,saved_at&order=saved_at.desc&limit=30", bearer: try await token())
        guard (200..<300).contains(st), let j else { throw CloudError(message: friendly(st, j)) }
        return try JSONDecoder().decode([HistoryItem].self, from: JSONSerialization.data(withJSONObject: j))
    }

    func historyData(_ id: Int64) async throws -> AppData {
        let (st, j) = try await http("GET", "/rest/v1/hw_snapshot_history?select=data&id=eq.\(id)", bearer: try await token())
        guard (200..<300).contains(st), let row = (j as? [[String: Any]])?.first else { throw CloudError(message: friendly(st, j)) }
        return try decodeData(row["data"])
    }

    // ---------- 同步 ----------
    static func hash(_ d: AppData) -> String {
        let enc = JSONEncoder(); enc.outputFormatting = .sortedKeys
        let bytes = (try? enc.encode(d.stripped())) ?? Data()
        return SHA256.hash(data: bytes).map { String(format: "%02x", $0) }.joined().prefix(32).map { String($0) }.joined()
    }

    /// 合并：按 id 取并集；同一次作业逐个学生合并（本机优先）
    static func merge(local: AppData, remote: AppData) -> AppData {
        var order = remote.classes.map { $0.id }
        var classes = Dictionary(remote.classes.map { ($0.id, $0) }, uniquingKeysWith: { a, _ in a })
        for l in local.classes {
            guard let r = classes[l.id] else { classes[l.id] = l; order.append(l.id); continue }
            var c = l
            c.students = unionById(r.students, l.students)
            var assigns = r.assignments
            for la in l.assignments {
                if let i = assigns.firstIndex(where: { $0.id == la.id }) {
                    let ra = assigns[i]
                    var m = la
                    m.statuses = ra.statuses.merging(la.statuses) { _, n in n }
                    m.comments = ra.comments.merging(la.comments) { _, n in n }
                    m.countedBooks = la.countedBooks ?? ra.countedBooks
                    assigns[i] = m
                } else { assigns.append(la) }
            }
            c.assignments = assigns
            c.scores = unionById(r.scores, l.scores)
            c.seating = l.seating ?? r.seating
            classes[l.id] = c
        }
        var out = local
        out.classes = order.compactMap { classes[$0] }
        out.currentClassId = local.currentClassId ?? remote.currentClassId
        return out
    }

    private static func unionById<T: Identifiable>(_ a: [T], _ b: [T]) -> [T] where T.ID == String {
        var res = a
        for x in b { if let i = res.firstIndex(where: { $0.id == x.id }) { res[i] = x } else { res.append(x) } }
        return res
    }
}
