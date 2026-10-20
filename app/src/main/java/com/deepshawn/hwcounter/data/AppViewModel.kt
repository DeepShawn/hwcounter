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
    private val _data = MutableStateFlow(load())
    val data: StateFlow<AppData> = _data.asStateFlow()

    private val undoStack = ArrayDeque<AppData>()
    private var saveJob: Job? = null

    private fun load(): AppData {
        return try {
            if (file.exists()) json.decodeFromString<AppData>(file.readText())
            else {
                val demo = DemoData.demoClass()
                AppData(classes = listOf(demo), currentClassId = demo.id)
            }
        } catch (e: Exception) {
            val bak = File(file.parentFile, "hwcounter.broken.json")
            runCatching { file.copyTo(bak, overwrite = true) }
            val demo = DemoData.demoClass()
            AppData(classes = listOf(demo), currentClassId = demo.id)
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
        if (undoable) {
            undoStack.addLast(old)
            while (undoStack.size > 30) undoStack.removeFirst()
        }
        _data.value = new
        persist()
    }

    val canUndo: Boolean get() = undoStack.isNotEmpty()

    fun undo(): Boolean {
        val prev = undoStack.removeLastOrNull() ?: return false
        _data.value = prev
        persist()
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

    fun addClass(name: String, emoji: String, demo: Boolean = false) = update { d ->
        val c = if (demo) DemoData.demoClass().copy(name = name, emoji = emoji) else SchoolClass(name = name, emoji = emoji)
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
        a.copy(statuses = m)
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
    fun addScore(studentIds: Collection<String>, delta: Double, reason: String) = updateClass { c ->
        c.copy(scores = c.scores + studentIds.map { ScoreEntry(studentId = it, delta = delta, reason = reason) })
    }

    fun removeScore(entryId: String) = updateClass { c -> c.copy(scores = c.scores.filterNot { it.id == entryId }) }

    fun clearScores() = updateClass { c -> c.copy(scores = emptyList()) }

    // ---------- 学生 ----------
    fun upsertStudent(s: Student) = updateClass { c ->
        if (c.students.any { it.id == s.id }) c.copy(students = c.students.map { if (it.id == s.id) s else it })
        else c.copy(students = c.students + s)
    }

    fun deleteStudent(id: String) = updateClass { c ->
        c.copy(
            students = c.students.filterNot { it.id == id },
            scores = c.scores.filterNot { it.studentId == id },
            assignments = c.assignments.map { it.copy(statuses = it.statuses - id) },
        )
    }

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
            if (replace) c.copy(students = r.students, assignments = emptyList(), scores = emptyList(), name = r.className ?: c.name)
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
        try {
            val d = json.decodeFromString<AppData>(text)
            withContext(Dispatchers.Main) { update { d } }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun resetAll() = update {
        val demo = DemoData.demoClass()
        AppData(classes = listOf(demo), currentClassId = demo.id, settings = it.settings)
    }
}
