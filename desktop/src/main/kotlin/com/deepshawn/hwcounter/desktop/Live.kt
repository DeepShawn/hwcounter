package com.deepshawn.hwcounter.desktop

import com.deepshawn.hwcounter.cloud.ConflictException
import com.deepshawn.hwcounter.data.AppData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 数据从哪里来：未登录 = 老师公开的预览；普通用户 = 所属老师的数据（只读）；老师 = 本机可修改并同步的数据 */
enum class Mode { PENDING, GUEST, MEMBER, TEACHER }

data class SyncUi(val busy: Boolean = false, val msg: String = "", val error: Boolean = false, val publicRead: Boolean? = null, val conflict: AppData? = null, val conflictVersion: Long = 0)

/** 加载、同步、登录退出（与安卓、网页同样的规则） */
class Live(private val state: AppState, private val scope: CoroutineScope) {
    private val lock = Mutex()
    private var timer: Job? = null

    init { state.onChange = { schedule() } }

    /** 按身份决定数据来源并加载 */
    fun load() = scope.launch {
        val s = Account.loadSession()
        if (s == null) { enter(Mode.GUEST); pullPublic(); return@launch }
        val me = runCatching { Account.me() }.getOrNull()
        val role = me?.let { if (it.isMember) "member" else "teacher" } ?: Account.cachedRole(s.userId) ?: "teacher"
        if (me != null) Account.cacheRole(s.userId, role)
        state.me = me
        if (role == "teacher") {
            if (state.mode != Mode.TEACHER) {
                state.undo.clear()
                var d = Account.loadLocal(s.userId)
                val n = Account.applyAutoResets(d); if (n != d) { d = n; Account.saveLocal(d) }
                state.data = d; state.load = Load.OK; state.mode = Mode.TEACHER
            }
            runSync()
        } else {
            enter(Mode.MEMBER)
            state.load = Load.LOADING
            try {
                val adm = me?.adminId
                val r = if (me != null && me.status == "active" && adm != null) Account.client.pullOf(adm) else null
                if (r == null) { state.load = if (me == null) Load.ERROR else Load.NONE; return@launch }
                val d = Account.decode(r.data); state.data = d; Cloud.saveCache(d); state.load = Load.OK
            } catch (_: Throwable) { state.load = Load.ERROR }
        }
    }

    private fun enter(m: Mode) {
        if (state.mode == m) return
        if (state.mode == Mode.TEACHER) state.data = Cloud.loadCache()
        state.undo.clear(); state.mode = m
    }

    private suspend fun pullPublic() {
        state.load = Load.LOADING
        try {
            val d = Cloud.pullPublic()
            if (d == null) state.load = Load.NONE else { state.data = d; Cloud.saveCache(d); state.load = Load.OK }
        } catch (_: Throwable) { state.load = Load.ERROR }
    }

    /** 定时刷新：老师检查同步，其他人重新拉取 */
    fun tick() { if (state.mode == Mode.TEACHER) schedule(0) else load() }

    fun schedule(ms: Long = 3000) {
        timer?.cancel()
        if (state.mode != Mode.TEACHER || !Account.auto) return
        timer = scope.launch { delay(ms); runSync() }
    }

    /** 一次同步：只有本机改过 → 上传；只有云端改过 → 下载；都改过 → 让老师选 */
    suspend fun runSync(retry: Int = 0) {
        if (state.mode != Mode.TEACHER || Account.loadSession() == null) return
        lock.withLock {
            state.sync = state.sync.copy(busy = true, msg = "同步中…", error = false)
            try {
                val local = state.data ?: AppData()
                val h = Account.hash(local)
                val changed = h != Account.syncedHash
                val remote = Account.client.pull()
                val pr = remote?.publicRead
                val msg = when {
                    remote == null -> if (local.classes.isEmpty()) "云端还没有数据" else { Account.push(local, 0); "已上传到云端" }
                    remote.version == Account.baseVersion -> if (changed) { Account.push(local, remote.version); "已上传到云端" } else { Account.lastSyncAt = System.currentTimeMillis(); "已是最新" }
                    else -> {
                        val r = Account.decode(remote.data).let { it.copy(settings = it.settings.copy(ai = it.settings.ai.copy(apiKey = local.settings.ai.apiKey))) }
                        if (!changed || local.classes.isEmpty()) {
                            Account.saveLocal(r); state.data = r
                            Account.baseVersion = remote.version; Account.syncedHash = Account.hash(r); Account.lastSyncAt = System.currentTimeMillis()
                            "已从云端更新（来自 ${remote.device ?: "其它设备"}）"
                        } else { state.sync = state.sync.copy(conflict = r, conflictVersion = remote.version); "本机和云端都有改动，请选择怎么处理" }
                    }
                }
                state.sync = state.sync.copy(busy = false, msg = msg, error = false, publicRead = pr ?: state.sync.publicRead)
                // 第一次同步后默认开放“未登录预览”（与安卓、网页一致）
                if (!Account.previewInit && state.sync.conflict == null && (remote == null || pr != null) && Account.baseVersion > 0) {
                    runCatching { if (pr != true) Account.setPublic(true); Account.previewInit = true; state.sync = state.sync.copy(publicRead = true) }
                }
            } catch (e: ConflictException) {
                state.sync = state.sync.copy(busy = false)
                if (retry < 2) scope.launch { delay(400); runSync(retry + 1) }
            } catch (e: Throwable) {
                state.sync = state.sync.copy(busy = false, msg = e.message ?: "同步失败", error = true)
            }
        }
    }

    /** 冲突时的选择：merge 合并两边 / cloud 用云端 / local 用本机覆盖 */
    fun resolve(choice: String) = scope.launch {
        val remote = state.sync.conflict ?: return@launch
        val v = state.sync.conflictVersion
        state.sync = state.sync.copy(conflict = null, busy = true, msg = "处理中…")
        try {
            lock.withLock {
                val local = state.data ?: AppData()
                when (choice) {
                    "cloud" -> { state.pushUndo(local); Account.saveLocal(remote); state.data = remote; Account.baseVersion = v; Account.syncedHash = Account.hash(remote); Account.lastSyncAt = System.currentTimeMillis() }
                    "local" -> Account.push(local, v)
                    else -> { val m = Account.merge(local, remote); state.pushUndo(local); Account.saveLocal(m); state.data = m; Account.push(m, v) }
                }
            }
            state.sync = state.sync.copy(busy = false, msg = when (choice) { "cloud" -> "已使用云端数据"; "local" -> "已用本机覆盖云端"; else -> "已合并并上传" })
        } catch (e: ConflictException) { state.sync = state.sync.copy(busy = false); runSync() } catch (e: Throwable) { state.sync = state.sync.copy(busy = false, msg = e.message ?: "处理失败", error = true) }
    }

    suspend fun signIn(user: String, pw: String) { Account.signIn(user, pw); state.sync = SyncUi(); load().join() }

    /** 退出：老师先把改动传上去，再清掉本机这份数据，回到预览 */
    fun signOut() = scope.launch {
        timer?.cancel()
        if (state.mode == Mode.TEACHER) runCatching { runSync() }
        Account.signOut()
        state.me = null; state.sync = SyncUi(); state.stack.clear()
        load()
    }

    fun setAuto(on: Boolean) { Account.auto = on; state.sync = state.sync.copy(); if (on) schedule(300) }
    fun setPublic(on: Boolean) = scope.launch {
        runCatching { Account.setPublic(on) }.onSuccess { state.sync = state.sync.copy(publicRead = on); state.say(if (on) "已允许未登录预览" else "已关闭未登录预览") }.onFailure { state.say(it.message ?: "设置失败") }
    }
    fun syncNow() = scope.launch { timer?.cancel(); runSync() }
}
