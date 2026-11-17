package com.deepshawn.hwcounter.cloud

import android.content.Context
import android.os.Build
import com.deepshawn.hwcounter.data.AppData
import com.deepshawn.hwcounter.data.SchoolClass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import java.io.File
import java.security.MessageDigest

/** 同步状态（显示在设置页与首页） */
data class SyncState(
    val email: String? = null,
    val busy: Boolean = false,
    val message: String = "",
    val error: Boolean = false,
    val version: Long = 0,
    val lastSyncAt: Long = 0,
    val auto: Boolean = true,
    /** 两边都改过：等待老师选择 */
    val conflict: AppData? = null,
    val conflictVersion: Long = 0,
    /** 云端是否开放了“未登录预览”（null = 未知） */
    val publicRead: Boolean? = null,
    /** 凭邀请码注册的普通用户（只读查看老师的数据、模拟炒股）；老师账号为 null */
    val member: MemberInfo? = null,
)

/** 普通用户的身份（登录后从 hw_me 取得） */
@Serializable
data class MemberInfo(val adminId: String, val username: String, val name: String, val active: Boolean = true)

@Serializable
private data class SyncMeta(
    val session: Session? = null,
    /** 本地当前数据基于的云端版本 */
    val baseVersion: Long = 0,
    /** 上次同步完成时本地数据的指纹（不含 API Key），用于判断本地是否改过 */
    val syncedHash: String = "",
    val lastSyncAt: Long = 0,
    val auto: Boolean = true,
    /** 是否已按默认值开启过“未登录预览” */
    val previewInit: Boolean = false,
    /** 普通用户（凭邀请码注册）；老师账号为 null */
    val member: MemberInfo? = null,
)

/**
 * 云同步：整份数据（不含 AI Key）作为一个快照，带版本号的乐观锁。
 * - 本地改了、云端没变 → 上传
 * - 云端变了、本地没改 → 下载覆盖
 * - 两边都改了 → 冲突，让老师选“用云端 / 用本机 / 合并”
 */
class SyncManager(
    ctx: Context,
    private val scope: CoroutineScope,
    private val json: Json,
    private val current: () -> AppData,
    private val apply: (AppData) -> Unit,
    /** 登录（true）/ 退出（false）时通知：切换可编辑数据与预览数据 */
    private val onAuthChanged: suspend (Boolean) -> Unit,
) {
    private val file = File(ctx.filesDir, "cloud_sync.json")
    private val metaJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private var meta: SyncMeta = runCatching { metaJson.decodeFromString<SyncMeta>(file.readText()) }.getOrDefault(SyncMeta())
    private val device = "Android · ${Build.MANUFACTURER} ${Build.MODEL}".take(60)
    private val lock = Mutex()
    private var pending: Job? = null

    val client = CloudClient(loadSession = { meta.session }, saveSession = { s -> setMeta(meta.copy(session = s)) })

    private val _state = MutableStateFlow(stateFromMeta())
    val state: StateFlow<SyncState> = _state.asStateFlow()

    private fun stateFromMeta(
        msg: String = "", error: Boolean = false, busy: Boolean = false,
        conflict: AppData? = null, conflictVersion: Long = 0,
    ) = SyncState(
        email = CloudConfig.displayEmail(meta.session?.email), busy = busy, message = msg, error = error,
        version = meta.baseVersion, lastSyncAt = meta.lastSyncAt, auto = meta.auto,
        conflict = conflict, conflictVersion = conflictVersion, member = meta.member,
    )

    private fun setMeta(m: SyncMeta) {
        meta = m
        runCatching { file.writeText(metaJson.encodeToString(m)) }
    }

    /** 更新提示文字，保留尚未处理的冲突与预览开关状态 */
    private fun post(msg: String, error: Boolean = false, busy: Boolean = false) {
        val c = _state.value
        _state.value = stateFromMeta(msg, error, busy, c.conflict, c.conflictVersion).copy(publicRead = c.publicRead)
    }

    val loggedIn: Boolean get() = meta.session != null
    /** 登录的是普通用户：只读，不上传 */
    val isMember: Boolean get() = meta.member != null
    val member: MemberInfo? get() = meta.member

    /** 登录后查身份；查不到（网络问题）时按邮箱判断，普通用户的管理员 id 下次再补 */
    private suspend fun loadIdentity() {
        val me = runCatching { Trade.me(client) }.getOrNull()
        val m = when {
            me != null && me.isMember -> MemberInfo(me.adminId ?: "", me.username, me.name, me.status == "active")
            me != null -> null
            CloudConfig.isMemberEmail(meta.session?.email) -> meta.member ?: MemberInfo("", meta.session?.email?.substringBefore("@") ?: "", "")
            else -> null
        }
        setMeta(meta.copy(member = m))
    }

    /** 普通用户：重新读取身份（余额、是否被停用） */
    suspend fun refreshMember(): MemberInfo? {
        if (!loggedIn || !isMember) return null
        loadIdentity()
        post(_state.value.message)
        return meta.member
    }

    /** 普通用户：读取所属老师的班级数据（只读） */
    suspend fun pullMemberData(): AppData? {
        var m = meta.member ?: return null
        if (m.adminId.isBlank()) { loadIdentity(); m = meta.member ?: return null }
        if (!m.active || m.adminId.isBlank()) return null
        return client.pullOf(m.adminId)?.let { decode(it.data) }
    }

    /** 凭邀请码注册普通用户并登录 */
    suspend fun registerWithInvite(code: String, username: String, password: String, name: String) {
        post("正在注册…", busy = true)
        try {
            client.registerWithInvite(code, username, password, name)
            setMeta(meta.copy(baseVersion = 0, syncedHash = "", lastSyncAt = 0))
            loadIdentity()
            onAuthChanged(true)
            post("注册成功，已登录：${meta.member?.name ?: username}")
        } catch (e: Exception) {
            post(e.message ?: "注册失败", error = true)
            throw e
        }
    }

    // ---------------- 老师：发布股价（有普通用户时，每次同步后按最新数据发布） ----------------
    private var hasMembersAt = 0L
    private var hasMembers = false
    private var lastQuotesHash = ""

    private fun publishPrices(d: AppData) {
        if (!loggedIn || isMember) return
        scope.launch {
            runCatching {
                val now = System.currentTimeMillis()
                if (now - hasMembersAt > 10 * 60_000L) {
                    hasMembers = Trade.members(client).isNotEmpty(); hasMembersAt = now
                }
                if (!hasMembers) return@runCatching
                val q = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { Trade.quotes(d) }
                val h = q.joinToString("|") { "${it.symbol}=${it.price}" }.hashCode().toString()
                if (h == lastQuotesHash) return@runCatching
                Trade.publish(client, q)
                lastQuotesHash = h
            }
        }
    }

    /** 老师在管理页手动发布股价 */
    suspend fun publishNow(d: AppData): Int {
        val n = Trade.publish(client, kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { Trade.quotes(d) })
        hasMembers = true; hasMembersAt = System.currentTimeMillis(); lastQuotesHash = ""
        return n
    }

    /** 上传前去掉只属于本机的东西（AI API Key） */
    private fun strip(d: AppData) = d.copy(settings = d.settings.copy(ai = d.settings.ai.copy(apiKey = "")))

    private fun hash(d: AppData): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(json.encodeToString(strip(d)).toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }.take(32)
    }

    /** 收到云端数据后保留本机的 API Key */
    private fun withLocalKey(remote: AppData, local: AppData) =
        remote.copy(settings = remote.settings.copy(ai = remote.settings.ai.copy(apiKey = local.settings.ai.apiKey)))

    private fun decode(e: JsonElement): AppData = json.decodeFromJsonElement(e)

    // ---------------- 账号 ----------------
    suspend fun signIn(email: String, password: String) {
        post("正在登录…", busy = true)
        try {
            client.signIn(email, password)
            // 新账号/新设备：基于版本 0 开始，第一次同步会判断要不要合并
            setMeta(meta.copy(baseVersion = 0, syncedHash = "", lastSyncAt = 0))
            loadIdentity()
            onAuthChanged(true)
            post(if (isMember) "登录成功（普通用户：只读查看，可模拟炒股）" else "登录成功")
            if (!isMember) syncNow()
        } catch (e: Exception) {
            post(e.message ?: "登录失败", error = true)
            throw e
        }
    }

    /** 返回 true 表示已直接登录；false 表示需要先去邮箱验证 */
    suspend fun signUp(email: String, password: String): Boolean {
        post("正在注册…", busy = true)
        try {
            val s = client.signUp(email, password)
            return if (s != null) {
                setMeta(meta.copy(baseVersion = 0, syncedHash = "", lastSyncAt = 0))
                onAuthChanged(true)
                post("注册成功")
                syncNow()
                true
            } else {
                post("注册成功！请到邮箱 $email 点确认链接，然后回来登录")
                false
            }
        } catch (e: Exception) {
            post(e.message ?: "注册失败", error = true)
            throw e
        }
    }

    suspend fun recover(email: String) {
        client.recover(email)
        post("重置密码邮件已发送到 $email")
    }

    suspend fun changePassword(old: String, new: String) {
        client.changePassword(old, new)
        post("密码已修改，其它设备下次需要用新密码登录")
    }

    suspend fun signOut() {
        client.signOut()
        setMeta(SyncMeta(auto = meta.auto))
        _state.value = SyncState(auto = meta.auto, message = "已退出登录，回到预览模式（本机数据保留）")
        onAuthChanged(false)
    }

    fun setAuto(on: Boolean) {
        setMeta(meta.copy(auto = on))
        post(if (on) "已开启自动同步" else "已关闭自动同步")
        if (on) schedule(500)
    }

    // ---------------- 同步 ----------------
    /** 本地数据变化后调用：防抖几秒再同步 */
    fun onLocalChange() {
        if (loggedIn && !isMember && meta.auto && _state.value.conflict == null) schedule(4000)
    }

    fun onResume() {
        if (loggedIn && !isMember && meta.auto) schedule(300)
    }

    private fun schedule(delayMs: Long) {
        pending?.cancel()
        pending = scope.launch {
            delay(delayMs)
            runCatching { syncNow() }
        }
    }

    suspend fun syncNow(): Unit = lock.withLock {
        if (!loggedIn || isMember) return
        post("同步中…", busy = true)
        try {
            val local = current()
            val localHash = hash(local)
            val localChanged = localHash != meta.syncedHash
            val localEmpty = local.classes.isEmpty()
            val remote = client.pull()
            if (remote?.publicRead != null) _state.value = _state.value.copy(publicRead = remote.publicRead)
            when {
                remote == null -> {
                    if (localEmpty) { post("云端还没有数据"); return }
                    upload(local, localHash, 0)
                }
                remote.version == meta.baseVersion -> {
                    if (localChanged) upload(local, localHash, remote.version) else done("已是最新")
                }
                // 云端更新了
                !localChanged || localEmpty -> {
                    val r = withLocalKey(decode(remote.data), local)
                    apply(r)
                    setMeta(meta.copy(baseVersion = remote.version, syncedHash = hash(r), lastSyncAt = System.currentTimeMillis()))
                    post("已从云端更新（来自 ${remote.device ?: "其它设备"}）")
                }
                else -> {
                    val r = withLocalKey(decode(remote.data), local)
                    _state.value = stateFromMeta("本机和云端都有改动，请选择怎么处理", conflict = r, conflictVersion = remote.version)
                }
            }
            ensurePreviewDefault()
            if (_state.value.conflict == null) publishPrices(current())
        } catch (e: ConflictException) {
            // 上传瞬间被其它设备抢先：下一轮会走冲突/下载逻辑
            post("云端刚被其它设备更新，正在重新比对…", busy = true)
            schedule(300)
        } catch (e: Exception) {
            post(e.message ?: "同步失败", error = true)
        }
    }

    private suspend fun upload(local: AppData, localHash: String, base: Long) {
        val v = client.push(json.encodeToJsonElement(strip(local)), base, device)
        setMeta(meta.copy(baseVersion = v, syncedHash = localHash, lastSyncAt = System.currentTimeMillis()))
        post("已上传到云端")
    }

    private fun done(msg: String) {
        setMeta(meta.copy(lastSyncAt = System.currentTimeMillis()))
        post(msg)
    }

    /** 冲突处理：0 用云端，1 用本机，2 合并 */
    suspend fun resolve(choice: Int) {
        val st = _state.value
        val remote = st.conflict ?: return
        val local = current()
        _state.value = st.copy(conflict = null, busy = true, message = "处理中…")
        try {
            lock.withLock {
                when (choice) {
                    0 -> {
                        apply(remote)
                        setMeta(meta.copy(baseVersion = st.conflictVersion, syncedHash = hash(remote), lastSyncAt = System.currentTimeMillis()))
                        post("已使用云端数据（本机旧数据可在“撤销”或历史版本中找回）")
                    }
                    1 -> upload(local, hash(local), st.conflictVersion)
                    else -> {
                        val merged = merge(local, remote)
                        apply(merged)
                        upload(merged, hash(merged), st.conflictVersion)
                        post("已合并两边的数据并上传")
                    }
                }
            }
        } catch (e: ConflictException) {
            post("云端又有新变化，正在重新比对…", busy = true)
            schedule(300)
        } catch (e: Exception) {
            post(e.message ?: "处理失败", error = true)
        }
    }

    /** 按老师的要求：账号第一次同步后默认开放“未登录预览”（之后以老师的开关为准） */
    private suspend fun ensurePreviewDefault() {
        if (meta.previewInit || _state.value.conflict != null) return
        runCatching { client.setPublic(true) }.onSuccess {
            setMeta(meta.copy(previewInit = true))
            _state.value = _state.value.copy(publicRead = true)
        }
    }

    suspend fun setPublic(flag: Boolean) {
        client.setPublic(flag)
        setMeta(meta.copy(previewInit = true))
        _state.value = _state.value.copy(publicRead = flag)
        post(if (flag) "已开放未登录预览" else "已关闭预览，数据恢复私密")
    }

    /** 未登录时读取公开预览 */
    suspend fun pullPublic(): AppData? = client.pullPublic()?.let { decode(it.data) }

    suspend fun history() = client.history()

    suspend fun restoreHistory(id: Long) {
        val local = current()
        val d = withLocalKey(decode(client.historyData(id)), local)
        apply(d)
        // 恢复后作为本地改动上传成新版本（强制覆盖，旧的云端版本会进历史）
        lock.withLock { upload(d, hash(d), -1) }
        post("已恢复该历史版本并上传")
    }

    companion object {
        /** 合并：班级/学生/作业/积分按 id 取并集；同一次作业的登记状态逐个学生合并（本机优先） */
        fun merge(local: AppData, remote: AppData): AppData {
            val classes = LinkedHashMap<String, SchoolClass>()
            remote.classes.forEach { classes[it.id] = it }
            local.classes.forEach { l ->
                val r = classes[l.id]
                classes[l.id] = if (r == null) l else mergeClass(l, r)
            }
            return local.copy(classes = classes.values.toList(), currentClassId = local.currentClassId ?: remote.currentClassId)
        }

        private fun mergeClass(l: SchoolClass, r: SchoolClass): SchoolClass {
            val students = LinkedHashMap<String, com.deepshawn.hwcounter.data.Student>()
            r.students.forEach { students[it.id] = it }
            l.students.forEach { students[it.id] = it }
            val assigns = LinkedHashMap<String, com.deepshawn.hwcounter.data.Assignment>()
            r.assignments.forEach { assigns[it.id] = it }
            l.assignments.forEach { la ->
                val ra = assigns[la.id]
                assigns[la.id] = if (ra == null) la else la.copy(
                    statuses = ra.statuses + la.statuses,
                    comments = ra.comments + la.comments,
                    countedBooks = la.countedBooks ?: ra.countedBooks,
                )
            }
            val scores = (r.scores + l.scores).distinctBy { it.id }
            val recharges = (r.recharges + l.recharges).distinctBy { it.id }.sortedBy { it.time }
            val exams = LinkedHashMap<String, com.deepshawn.hwcounter.data.Exam>()
            r.exams.forEach { exams[it.id] = it }
            l.exams.forEach { le -> val re = exams[le.id]; exams[le.id] = if (re == null) le else le.copy(scores = re.scores + le.scores, absent = (re.absent + le.absent).distinct().filter { it !in le.scores }) }
            return l.copy(
                students = students.values.toList(), assignments = assigns.values.toList(), scores = scores, recharges = recharges,
                exams = exams.values.sortedBy { it.date }, groupLeaders = r.groupLeaders + l.groupLeaders, seating = l.seating ?: r.seating,
                settlements = (r.settlements + l.settlements).distinctBy { it.id }.sortedBy { it.time },
                autoReset = (l.autoReset ?: r.autoReset)?.let { ar -> ar.copy(skipped = (ar.skipped + l.autoReset?.skipped.orEmpty() + r.autoReset?.skipped.orEmpty()).distinct()) },
            ).let { with(com.deepshawn.hwcounter.data.AutoResetOps) { it.dropSkipped() } }
        }
    }
}
