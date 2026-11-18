package com.deepshawn.hwcounter.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class AppViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            prettyPrint = false
            coerceInputValues = true
        }
    }

    private val file = File(app.filesDir, "hwcounter.json")
    /** 未登录预览时缓存的云端公开数据（与本机自己的数据分开存放，互不覆盖） */
    private val previewFile = File(app.filesDir, "preview_cache.json")

    /** 云端同步（Supabase）：本地每次改动后防抖上传；回到前台时检查云端 */
    // 登录/退出时同步切换数据（必须在首次同步开始前完成，避免把预览数据当成本机数据上传）
    val sync: com.deepshawn.hwcounter.cloud.SyncManager = com.deepshawn.hwcounter.cloud.SyncManager(app, viewModelScope, json, { _data.value }, { d: AppData -> update { d } }) { loggedIn: Boolean ->
        if (loggedIn && !sync.isMember) enterEditMode() else enterPreviewMode()
    }

    private val _readOnly: MutableStateFlow<Boolean> = MutableStateFlow(!sync.loggedIn || sync.isMember)
    /** 未登录 = 预览模式（只读）；凭邀请码注册的普通用户也只读；老师登录后才能修改 */
    val readOnly: StateFlow<Boolean> = _readOnly.asStateFlow()

    private val _data: MutableStateFlow<AppData> = MutableStateFlow(if (sync.loggedIn && !sync.isMember) load() else loadPreview())
    val data: StateFlow<AppData> = _data.asStateFlow()

    /** 给界面的提示（如“预览模式：登录后才能修改”） */
    val notices = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 4)

    private val undoStack = ArrayDeque<AppData>()
    private var saveJob: Job? = null

    init {
        if (!sync.loggedIn || sync.isMember) viewModelScope.launch { refreshPreview() }
        // 定时上传草稿：每 15 秒检查一次（App 在后台但进程还在时也会执行；进程被杀则下次打开立即补传）
        viewModelScope.launch {
            delay(1500)
            applyAutoResets()
            while (true) { uploadDueDrafts(); applyAutoResets(); delay(15_000) }
        }
    }

    // ---------- 草稿：只保存在本机（drafts.json），不进云端快照 ----------
    private val draftFile = File(app.filesDir, "drafts.json")
    private val _drafts: MutableStateFlow<List<Draft>> = MutableStateFlow(
        runCatching { json.decodeFromString<List<Draft>>(draftFile.readText()) }.getOrDefault(emptyList())
    )
    val drafts: StateFlow<List<Draft>> = _drafts.asStateFlow()

    private fun setDrafts(list: List<Draft>) {
        _drafts.value = list
        val text = json.encodeToString(list)
        viewModelScope.launch(Dispatchers.IO) { runCatching { draftFile.writeText(text) } }
    }

    fun addDraft(d: Draft) = setDrafts(listOf(d) + _drafts.value)
    fun addDrafts(ds: List<Draft>) = setDrafts(ds + _drafts.value)
    fun editDraft(id: String, f: (Draft) -> Draft) = setDrafts(_drafts.value.map { if (it.id == id) f(it).copy(updatedAt = System.currentTimeMillis()) else it })
    fun deleteDraft(id: String) = setDrafts(_drafts.value.filterNot { it.id == id })

    /** 草稿里改状态 */
    fun setDraftStatus(id: String, studentIds: Collection<String>, status: HwStatus) = editDraft(id) { d ->
        val m = d.statuses.toMutableMap()
        studentIds.forEach { if (status == HwStatus.NONE) m.remove(it) else m[it] = status }
        d.copy(statuses = m)
    }

    fun setDraftComment(id: String, studentId: String, text: String) = editDraft(id) { d ->
        val m = d.comments.toMutableMap()
        if (text.isBlank()) m.remove(studentId) else m[studentId] = text
        d.copy(comments = m)
    }

    /** 上传草稿：写进作业 → 立即同步到云端。返回成功上传的份数 */
    fun uploadDrafts(ids: Collection<String>, why: String = ""): Int {
        if (!sync.loggedIn || _readOnly.value) { notices.tryEmit("请先在“设置”里登录，才能上传到云端"); return 0 }
        val list = _drafts.value.filter { it.id in ids }
        var d = _data.value
        val done = ArrayList<String>(); val errs = ArrayList<String>()
        for (dr in list) runCatching { d = com.deepshawn.hwcounter.recog.StatTable.applyDraft(d, dr); done += dr.id }.onFailure { errs += "${dr.title.ifBlank { "草稿" }}：${it.message}" }
        if (done.isNotEmpty()) {
            val next = d
            update { next }
            setDrafts(_drafts.value.filterNot { it.id in done })
            viewModelScope.launch { runCatching { sync.syncNow() } }
            notices.tryEmit("${why}已上传 ${done.size} 份草稿" + if (errs.isNotEmpty()) "；" + errs.joinToString("；") else "")
        } else if (errs.isNotEmpty()) notices.tryEmit(errs.joinToString("；"))
        return done.size
    }

    private fun uploadDueDrafts() {
        if (!sync.loggedIn || _readOnly.value) return
        val now = System.currentTimeMillis()
        val due = _drafts.value.filter { (it.scheduledAt ?: Long.MAX_VALUE) <= now }.map { it.id }
        if (due.isNotEmpty()) uploadDrafts(due, "定时上传：")
    }

    private fun loadPreview(): AppData {
        val local = load()
        val cached = runCatching { json.decodeFromString<AppData>(previewFile.readText()) }.getOrNull()
        // 积分规则、科目、理由用老师的（缓存里有就用缓存）；主题等显示设置用本机的
        return (cached ?: AppData()).let { it.copy(settings = (if (cached != null) it.settings else local.settings).withLocalDisplay(local.settings)) }
    }

    private fun previewing() = !sync.loggedIn || sync.isMember

    /** 拉取只读数据：未登录时是老师公开的预览；普通用户是所属老师的数据 */
    suspend fun refreshPreview() {
        if (!previewing()) return
        val remote = runCatching { if (sync.isMember) sync.pullMemberData() else sync.pullPublic() }.getOrNull() ?: return
        withContext(Dispatchers.IO) { runCatching { previewFile.writeText(json.encodeToString(remote)) } }
        if (!previewing()) return
        val cur = _data.value
        _data.value = remote.copy(
            // 计分按老师的规则算（普通用户看到的积分和老师一致）；只保留本机的显示设置
            settings = remote.settings.withLocalDisplay(cur.settings),
            currentClassId = cur.currentClassId?.takeIf { id -> remote.classes.any { it.id == id } } ?: remote.currentClassId,
        )
    }

    private suspend fun enterEditMode() {
        val d = withContext(Dispatchers.IO) { load() }
        undoStack.clear()
        _data.value = d
        _readOnly.value = false
        applyAutoResets()
    }

    private suspend fun enterPreviewMode() {
        undoStack.clear()
        _readOnly.value = true
        _data.value = withContext(Dispatchers.IO) { loadPreview() }
        refreshPreview()
    }

    fun onResume() {
        if (sync.loggedIn && !sync.isMember) sync.onResume() else viewModelScope.launch { refreshPreview() }
        uploadDueDrafts()
        applyAutoResets()
    }

    private fun load(): AppData {
        return try {
            if (file.exists()) json.decodeFromString<AppData>(file.readText()) else AppData()
        } catch (e: Exception) {
            val bak = File(file.parentFile, "hwcounter.broken.json")
            runCatching { file.copyTo(bak, overwrite = true) }
            AppData()
        }
    }

    private fun persist() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch(Dispatchers.IO) {
            delay(250)
            val text = json.encodeToString(_data.value)
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(file)) {
                file.writeText(text)
                tmp.delete()
            }
        }
    }

    fun update(undoable: Boolean = true, f: (AppData) -> AppData) {
        val old = _data.value
        val new = f(old)
        if (new == old) return
        if (_readOnly.value) {
            // 预览模式 / 普通用户：班级数据和老师的规则只读；只允许切换班级、改主题等本机显示设置
            if (new.classes != old.classes) {
                notices.tryEmit(if (sync.isMember) "普通用户只能查看，不能修改" else "预览模式：登录后才能修改")
                return
            }
            if (teacherOwned(new.settings) != teacherOwned(old.settings)) {
                notices.tryEmit(if (sync.isMember) "积分规则由老师设置，普通用户不能修改" else "预览模式：登录后才能修改积分规则")
                return
            }
            _data.value = new
            viewModelScope.launch(Dispatchers.IO) {
                val main = load()
                // 只把显示设置写回本机数据，不动本机老师账号的规则
                runCatching { file.writeText(json.encodeToString(main.copy(settings = main.settings.withLocalDisplay(new.settings)))) }
            }
            return
        }
        if (undoable) {
            undoStack.addLast(old)
            while (undoStack.size > 30) undoStack.removeFirst()
        }
        _data.value = new
        persist()
        sync.onLocalChange()
    }

    val canUndo: Boolean get() = undoStack.isNotEmpty()

    fun undo(): Boolean {
        if (_readOnly.value) return false
        val prev = undoStack.removeLastOrNull() ?: return false
        _data.value = prev
        persist()
        sync.onLocalChange()
        return true
    }

    fun currentClass(d: AppData = _data.value): SchoolClass? =
        d.classes.firstOrNull { it.id == d.currentClassId } ?: d.classes.firstOrNull()

    fun updateClass(classId: String? = null, undoable: Boolean = true, f: (SchoolClass) -> SchoolClass) = update(undoable) { d ->
        val id = classId ?: currentClass(d)?.id ?: return@update d
        d.copy(classes = d.classes.map { if (it.id == id) f(it) else it })
    }

    fun updateSettings(f: (Settings) -> Settings) = update(false) { it.copy(settings = f(it.settings)) }

    // ---------- 班级 ----------
    fun switchClass(id: String) = update(false) { it.copy(currentClassId = id) }

    fun addClass(name: String, emoji: String) = update { d ->
        val c = SchoolClass(name = name, emoji = emoji)
        d.copy(classes = d.classes + c, currentClassId = c.id)
    }

    fun renameClass(id: String, name: String, emoji: String) = updateClass(id) { it.copy(name = name, emoji = emoji) }

    fun deleteClass(id: String) = update { d ->
        val rest = d.classes.filterNot { it.id == id }
        d.copy(classes = rest, currentClassId = if (d.currentClassId == id) rest.firstOrNull()?.id else d.currentClassId)
    }

    // ---------- 作业 ----------
    fun createAssignment(title: String, subject: String): String {
        val a0 = Assignment(title = title, subject = subject)
        var id = a0.id
        updateClass { c ->
            val leaves = c.students.filter { it.longLeave }.associate { it.id to HwStatus.LEAVE }
            val a = a0.copy(statuses = leaves)
            id = a.id
            c.copy(assignments = c.assignments + a)
        }
        return id
    }

    fun editAssignment(id: String, f: (Assignment) -> Assignment) = updateClass { c ->
        c.copy(assignments = c.assignments.map { if (it.id == id) f(it) else it })
    }

    fun deleteAssignment(id: String) = updateClass { c -> c.copy(assignments = c.assignments.filterNot { it.id == id }) }

    fun setStatus(assignmentId: String, studentIds: Collection<String>, status: HwStatus) = editAssignment(assignmentId) { a ->
        val m = a.statuses.toMutableMap()
        studentIds.forEach { if (status == HwStatus.NONE) m.remove(it) else m[it] = status }
        // 改成其他状态时取消复审不合格和抄袭标记
        a.copy(statuses = m, reviewFailed = a.reviewFailed - studentIds.toSet()).withoutPlagiarism(studentIds)
    }

    /** 抄袭：抄袭者抄了提供者的，双方都记为未交（扣分翻倍，累犯加重） */
    fun markPlagiarism(assignmentId: String, receivers: Collection<String>, providers: Collection<String>) = editAssignment(assignmentId) { a ->
        val p = a.plagiarism.toMutableMap()
        // 提供者就是这次选的人；一个都没选 = 提供者不明
        val real = providers.filter { it != UNKNOWN_PROVIDER }.distinct()
        for (r in receivers) p[r] = real.filter { it != r }.ifEmpty { listOf(UNKNOWN_PROVIDER) }
        a.copy(statuses = a.statuses + (receivers + real).associateWith { HwStatus.MISSING }, plagiarism = p)
    }

    /** 取消这些同学的抄袭标记（状态保持“未交”，需要时再改） */
    fun clearPlagiarism(assignmentId: String, studentIds: Collection<String>) = editAssignment(assignmentId) { it.withoutPlagiarism(studentIds) }

    /** 复审不合格：记为未交（多算一次未交），扣分翻倍 */
    fun markReviewFailed(assignmentId: String, studentIds: Collection<String>) = editAssignment(assignmentId) { a ->
        a.copy(statuses = a.statuses + studentIds.associateWith { HwStatus.MISSING }, reviewFailed = (a.reviewFailed + studentIds).distinct())
    }

    /** 未登记的人 -> 某状态 */
    fun fillRemaining(assignmentId: String, status: HwStatus) = updateClass { c ->
        c.copy(assignments = c.assignments.map { a ->
            if (a.id != assignmentId) a else {
                val m = a.statuses.toMutableMap()
                c.students.forEach { s -> if ((m[s.id] ?: HwStatus.NONE) == HwStatus.NONE) m[s.id] = status }
                a.copy(statuses = m)
            }
        })
    }

    fun setComment(assignmentId: String, studentId: String, text: String) = editAssignment(assignmentId) { a ->
        val m = a.comments.toMutableMap()
        if (text.isBlank()) m.remove(studentId) else m[studentId] = text
        a.copy(comments = m)
    }

    // ---------- 积分 ----------
    fun addScore(studentIds: Collection<String>, delta: Double, reason: String) {
        if (!delta.isFinite() || delta == 0.0 || studentIds.isEmpty()) return
        val ids = studentIds.toSet()
        updateClass { c ->
            c.copy(scores = c.scores + c.students.filter { it.id in ids }.map { ScoreEntry(studentId = it.id, delta = delta, reason = reason) })
        }
    }

    fun removeScore(entryId: String) = updateClass { c -> c.copy(scores = c.scores.filterNot { it.id == entryId }) }

    fun clearScores() = updateClass { c -> c.copy(scores = emptyList(), recharges = emptyList()) }

    fun resetScores(studentIds: Collection<String>, target: Double) = updateClass { c ->
        c.resetStudentScores(_data.value.settings.rules, studentIds, target)
    }

    /** 积分重置：只重置积分并留下记录，历史加减分与作业状态不变 */
    fun rechargeScores(studentIds: Collection<String>, target: Double, note: String = "", effectiveAt: Long? = null, assignmentId: String? = null) = updateClass { c ->
        c.recharge(_data.value.settings.rules, studentIds, target, note, effectiveAt = effectiveAt, assignment = c.assignments.firstOrNull { it.id == assignmentId })
    }

    // ---------- 考试 / 小组 ----------
    fun upsertExam(e: Exam) = updateClass { it.upsertExam(e) }
    fun deleteExam(id: String) = updateClass { it.removeExam(id) }
    fun setExamScore(examId: String, studentId: String, score: Double?, absent: Boolean = false) = updateClass { c ->
        c.copy(exams = c.exams.map { e ->
            if (e.id != examId) e else e.copy(
                scores = if (score == null || absent) e.scores - studentId else e.scores + (studentId to score),
                absent = if (absent) (e.absent + studentId).distinct() else e.absent - studentId,
            )
        })
    }
    /** 把一批同学记为缺考（会去掉他们的分数） */
    fun markAbsent(examId: String, studentIds: Collection<String>) = updateClass { c ->
        val ids = studentIds.toSet()
        c.copy(exams = c.exams.map { e -> if (e.id != examId) e else e.copy(scores = e.scores - ids, absent = (e.absent + ids).distinct()) })
    }
    /** 取消缺考（scores=false）或清除成绩（scores=true） */
    fun clearExamMarks(examId: String, studentIds: Collection<String>, scores: Boolean) = updateClass { c ->
        val ids = studentIds.toSet()
        c.copy(exams = c.exams.map { e -> if (e.id != examId) e else if (scores) e.copy(scores = e.scores - ids) else e.copy(absent = e.absent - ids) })
    }
    fun setGroupLeader(group: String, studentId: String?) = updateClass { it.setGroupLeader(group, studentId) }
    /** 设为 / 取消课代表（可以同时是组长） */
    fun setRep(studentId: String, on: Boolean) = updateClass { c -> c.copy(reps = if (on) (c.reps + studentId).distinct() else c.reps - studentId) }

    /** 撤销某次重置（删除记录，分数按全部历史重新计算） */
    fun removeRecharge(id: String) = updateClass { c -> c.undoRecharge(id) }

    /** 设置每月定期重置（null = 关闭并清除设置） */
    fun setAutoReset(ar: AutoReset?) {
        updateClass { c -> c.copy(autoReset = ar?.let { n -> n.copy(skipped = (c.autoReset?.skipped.orEmpty() + n.skipped).distinct()) } ?: c.autoReset?.copy(enabled = false)) }
        applyAutoResets()
    }

    /** 补上所有班级到期的定期重置，以及与之联动的结算（打开 App、回到前台时调用） */
    fun applyAutoResets(now: Long = System.currentTimeMillis()) {
        if (_readOnly.value) return
        // 第一次运行新版本时记下联动结算的起点，之前的定期重置不追溯结算
        if (_data.value.settings.rules.settlement.autoSince <= 0L)
            update(undoable = false) { it.copy(settings = it.settings.copy(rules = it.settings.rules.copy(settlement = it.settings.rules.settlement.copy(autoSince = now)))) }
        val d = _data.value
        val rules = d.settings.rules
        fun step(c: SchoolClass) = Settle.applyAuto(c.applyAutoReset(rules, now), rules)
        val next = d.classes.map { step(it) }
        if (next.indices.any { next[it] != d.classes[it] }) update(undoable = false) { it.copy(classes = it.classes.map { c -> step(c) }) }
    }

    /** 结算：写入记录和每人加减分；[resetTo] 不为空时随即开始新一轮（结算分计入新一轮） */
    fun settle(s: Settlement, resetTo: Double?) = updateClass { c ->
        if (resetTo == null) Settle.apply(c, s)
        else {
            val withReset = c.recharge(_data.value.settings.rules, c.students.map { it.id }, resetTo, "${s.title}后开始新一轮", time = s.time)
            Settle.apply(withReset, s.copy(resetId = withReset.recharges.last().id), entryTime = s.time + 1)
        }
    }

    /** 撤销结算（同时删除结算产生的加减分；若结算时开始了新一轮，那次重置也一起撤销） */
    fun undoSettlement(id: String) = updateClass { c ->
        val s = c.settlements.firstOrNull { it.id == id }
        val out = Settle.undo(c, id)
        if (s?.resetId != null && !id.startsWith(AUTO_SETTLE_PREFIX)) out.removeRecharge(s.resetId) else out
    }

    /** 批量加减分：每人分值可以不同 */
    fun addScores(deltas: Map<String, Double>, reason: String) {
        val items = deltas.filterValues { it.isFinite() && it != 0.0 }
        if (items.isEmpty()) return
        val t = System.currentTimeMillis()
        updateClass { c ->
            c.copy(scores = c.scores + c.students.filter { it.id in items }.map { ScoreEntry(studentId = it.id, delta = items.getValue(it.id), reason = reason, time = t) })
        }
    }

    // ---------- 学生 ----------
    fun upsertStudent(s: Student) = updateClass { c ->
        if (c.students.any { it.id == s.id }) c.copy(students = c.students.map { if (it.id == s.id) s else it })
        else c.copy(students = c.students + s)
    }

    fun deleteStudent(id: String) = updateClass { c ->
        c.copy(
            students = c.students.filterNot { it.id == id },
            scores = c.scores.filterNot { it.studentId == id },
            exams = c.exams.map { e -> e.copy(scores = e.scores - id, absent = e.absent - id) },
            groupLeaders = c.groupLeaders.filterValues { it != id },
            recharges = c.recharges.mapNotNull { r ->
                if (id !in r.studentIds) r else r.copy(studentIds = r.studentIds - id, before = r.before - id).takeIf { it.studentIds.isNotEmpty() }
            },
            assignments = c.assignments.map { it.copy(statuses = it.statuses - id) },
            seating = c.seating?.let { it.copy(seats = it.seats - id) },
        )
    }

    /** 修改当前班级的座位表（null = 清空） */
    fun setSeating(classId: String? = null, f: (Seating?) -> Seating?) = updateClass(classId) { it.copy(seating = f(it.seating)) }

    fun sortStudents() = updateClass { c -> c.copy(students = c.students.sortedWith(compareBy({ it.no.length }, { it.no }))) }

    /** 导入名单：按学号合并（同学号更新姓名），或整体替换 */
    fun importRoster(r: RosterImport.Result, replace: Boolean, asNewClass: Boolean): Int {
        if (asNewClass) {
            update { d ->
                val c = SchoolClass(name = r.className ?: "新班级", students = r.students)
                d.copy(classes = d.classes + c, currentClassId = c.id)
            }
            return r.students.size
        }
        updateClass { c ->
            if (replace) c.copy(students = r.students, assignments = emptyList(), scores = emptyList(), recharges = emptyList(), exams = emptyList(), groupLeaders = emptyMap(), name = r.className ?: c.name)
            else {
                val byNo = c.students.associateBy { it.no }
                val merged = c.students.map { old ->
                    r.students.firstOrNull { it.no == old.no }?.let { n ->
                        old.copy(name = n.name, group = n.group.ifBlank { old.group }, gender = n.gender.ifBlank { old.gender })
                    } ?: old
                }
                c.copy(students = merged + r.students.filter { it.no !in byNo })
            }
        }
        return r.students.size
    }

    // ---------- 备份 ----------
    fun exportBackup(): String = Json { prettyPrint = true; encodeDefaults = true }.encodeToString(_data.value)

    suspend fun restoreBackup(text: String): Boolean = withContext(Dispatchers.Default) {
        if (_readOnly.value) {
            notices.tryEmit("预览模式：登录后才能恢复备份")
            return@withContext false
        }
        try {
            val d = json.decodeFromString<AppData>(text)
            withContext(Dispatchers.Main) { update { d } }
            true
        } catch (e: Exception) {
            false
        }
    }

    /** 清空所有班级数据（保留设置） */
    fun resetAll() = update { AppData(settings = it.settings) }
}

/** 老师才能改的设置：积分规则（作业、考试、结算等）、科目、常用加减分理由 */
internal fun teacherOwned(s: Settings) = Triple(s.rules, s.subjects, s.quickReasons)

/** 保留这份设置里老师的规则，其余（主题、排版、识别、语音、反馈等本机设置）取 [local] 的 */
internal fun Settings.withLocalDisplay(local: Settings): Settings =
    local.copy(rules = rules, subjects = subjects, quickReasons = quickReasons)
