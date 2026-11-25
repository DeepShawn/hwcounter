package com.deepshawn.hwcounter.desktop

import com.deepshawn.hwcounter.data.AppData
import com.deepshawn.hwcounter.data.Assignment
import com.deepshawn.hwcounter.data.Exam
import com.deepshawn.hwcounter.data.HwStatus
import com.deepshawn.hwcounter.data.SchoolClass
import com.deepshawn.hwcounter.data.ScoreEntry
import com.deepshawn.hwcounter.data.ScoreRules
import com.deepshawn.hwcounter.data.Student
import com.deepshawn.hwcounter.data.UNKNOWN_PROVIDER
import com.deepshawn.hwcounter.data.recharge
import com.deepshawn.hwcounter.data.removeExam
import com.deepshawn.hwcounter.data.setGroupLeader
import com.deepshawn.hwcounter.data.undoRecharge
import com.deepshawn.hwcounter.data.upsertExam

/** 修改数据的操作：与安卓 AppViewModel 同样的规则（纯函数，输入旧数据返回新数据） */
object Ops {
    // ---------- 班级 ----------
    fun addClass(d: AppData, name: String, emoji: String): Pair<AppData, String> {
        val c = SchoolClass(name = name.trim().ifBlank { "新班级" }, emoji = emoji)
        return d.copy(classes = d.classes + c, currentClassId = c.id) to c.id
    }
    fun renameClass(d: AppData, id: String, name: String, emoji: String) = d.copy(classes = d.classes.map { if (it.id == id) it.copy(name = name.trim().ifBlank { it.name }, emoji = emoji) else it })
    fun deleteClass(d: AppData, id: String): AppData {
        val rest = d.classes.filterNot { it.id == id }
        return d.copy(classes = rest, currentClassId = if (d.currentClassId == id) rest.firstOrNull()?.id else d.currentClassId)
    }

    // ---------- 作业 ----------
    /** 新建作业：长期请假的同学自动记为请假 */
    fun createAssignment(c: SchoolClass, title: String, subject: String, createdAt: Long = System.currentTimeMillis()): Pair<SchoolClass, String> {
        val a = Assignment(title = title.trim().ifBlank { "作业" }, subject = subject.trim(), createdAt = createdAt, statuses = c.students.filter { it.longLeave }.associate { it.id to HwStatus.LEAVE })
        return c.copy(assignments = c.assignments + a) to a.id
    }
    fun editAssignment(c: SchoolClass, id: String, f: (Assignment) -> Assignment) = c.copy(assignments = c.assignments.map { if (it.id == id) f(it) else it })
    fun deleteAssignment(c: SchoolClass, id: String) = c.copy(assignments = c.assignments.filterNot { it.id == id })

    /** 登记状态；改成其他状态时取消复审不合格和抄袭标记 */
    fun setStatus(c: SchoolClass, aid: String, ids: Collection<String>, st: HwStatus) = editAssignment(c, aid) { a ->
        val m = a.statuses.toMutableMap()
        ids.forEach { if (st == HwStatus.NONE) m.remove(it) else m[it] = st }
        a.copy(statuses = m, reviewFailed = a.reviewFailed - ids.toSet()).withoutPlagiarism(ids)
    }
    /** 复审不合格：记为未交（多算一次未交），扣分翻倍 */
    fun markReviewFailed(c: SchoolClass, aid: String, ids: Collection<String>) = editAssignment(c, aid) { a ->
        a.copy(statuses = a.statuses + ids.associateWith { HwStatus.MISSING }, reviewFailed = (a.reviewFailed + ids).distinct())
    }
    /** 抄袭：抄袭者和提供者都记为未交；一个提供者都没选 = 提供者不明 */
    fun markPlagiarism(c: SchoolClass, aid: String, receivers: Collection<String>, providers: Collection<String>) = editAssignment(c, aid) { a ->
        val p = a.plagiarism.toMutableMap()
        val real = providers.filter { it != UNKNOWN_PROVIDER }.distinct()
        for (r in receivers) p[r] = real.filter { it != r }.ifEmpty { listOf(UNKNOWN_PROVIDER) }
        a.copy(statuses = a.statuses + (receivers + real).associateWith { HwStatus.MISSING }, plagiarism = p)
    }
    fun clearPlagiarism(c: SchoolClass, aid: String, ids: Collection<String>) = editAssignment(c, aid) { it.withoutPlagiarism(ids) }
    /** 未登记的同学一键标为某状态 */
    fun fillRemaining(c: SchoolClass, aid: String, st: HwStatus) = editAssignment(c, aid) { a ->
        val m = a.statuses.toMutableMap()
        c.students.forEach { s -> if ((m[s.id] ?: HwStatus.NONE) == HwStatus.NONE) m[s.id] = st }
        a.copy(statuses = m)
    }
    fun setComment(c: SchoolClass, aid: String, sid: String, text: String) = editAssignment(c, aid) { a ->
        a.copy(comments = if (text.isBlank()) a.comments - sid else a.comments + (sid to text.trim()))
    }

    // ---------- 积分 ----------
    fun addScore(c: SchoolClass, ids: Collection<String>, delta: Double, reason: String): SchoolClass {
        if (!delta.isFinite() || delta == 0.0 || ids.isEmpty()) return c
        val t = System.currentTimeMillis(); val set = ids.toSet()
        val why = reason.trim().ifBlank { if (delta > 0) "加分" else "扣分" }
        return c.copy(scores = c.scores + c.students.filter { it.id in set }.map { ScoreEntry(studentId = it.id, delta = delta, reason = why, time = t) })
    }
    fun removeScore(c: SchoolClass, id: String) = c.copy(scores = c.scores.filterNot { it.id == id })
    fun recharge(c: SchoolClass, rules: ScoreRules, ids: Collection<String>, target: Double, note: String, effectiveAt: Long?) = c.recharge(rules, ids, target, note, effectiveAt = effectiveAt)
    fun undoRecharge(c: SchoolClass, id: String) = c.undoRecharge(id)

    // ---------- 学生 ----------
    fun upsertStudent(c: SchoolClass, s: Student) =
        if (c.students.any { it.id == s.id }) c.copy(students = c.students.map { if (it.id == s.id) s else it }) else c.copy(students = c.students + s)
    fun deleteStudent(c: SchoolClass, id: String) = c.copy(
        students = c.students.filterNot { it.id == id },
        scores = c.scores.filterNot { it.studentId == id },
        exams = c.exams.map { e -> e.copy(scores = e.scores - id, absent = e.absent - id) },
        groupLeaders = c.groupLeaders.filterValues { it != id },
        reps = c.reps - id,
        recharges = c.recharges.mapNotNull { r -> if (id !in r.studentIds) r else r.copy(studentIds = r.studentIds - id, before = r.before - id).takeIf { it.studentIds.isNotEmpty() } },
        assignments = c.assignments.map { it.copy(statuses = it.statuses - id, comments = it.comments - id, reviewFailed = it.reviewFailed - id).withoutPlagiarism(listOf(id)) },
        seating = c.seating?.let { it.copy(seats = it.seats - id) },
    )
    /** 保存同学，同时设置课代表、组长 */
    fun saveStudent(c: SchoolClass, s: Student, rep: Boolean, leader: Boolean): SchoolClass {
        var y = upsertStudent(c, s)
        y = y.copy(reps = if (rep) (y.reps + s.id).distinct() else y.reps - s.id)
        val oldGroup = c.groupLeaders.entries.firstOrNull { it.value == s.id }?.key
        if (oldGroup != null && (!leader || oldGroup != s.group)) y = y.setGroupLeader(oldGroup, null)
        if (leader && s.group.isNotBlank()) y = y.setGroupLeader(s.group, s.id)
        return y
    }
    /** 导入名单：按学号合并（同学号更新姓名），或整体替换 */
    fun importRoster(c: SchoolClass, list: List<Student>, replace: Boolean): SchoolClass {
        if (replace) return c.copy(students = list, assignments = emptyList(), scores = emptyList(), recharges = emptyList(), exams = emptyList(), groupLeaders = emptyMap())
        val byNo = c.students.map { it.no }.toSet()
        val merged = c.students.map { old -> list.firstOrNull { it.no == old.no }?.let { n -> old.copy(name = n.name, group = n.group.ifBlank { old.group }, gender = n.gender.ifBlank { old.gender }) } ?: old }
        return c.copy(students = merged + list.filter { it.no !in byNo })
    }

    // ---------- 考试 ----------
    fun upsertExam(c: SchoolClass, e: Exam) = c.upsertExam(e)
    fun deleteExam(c: SchoolClass, id: String) = c.removeExam(id)
    /** 录入一位同学的成绩（score 为 null 时清除），absent 为 true 时记为缺考 */
    fun setExamScore(c: SchoolClass, eid: String, sid: String, score: Double?, absent: Boolean) = c.copy(exams = c.exams.map { e ->
        if (e.id != eid) e else e.copy(scores = if (score == null || absent) e.scores - sid else e.scores + (sid to score), absent = if (absent) (e.absent + sid).distinct() else e.absent - sid)
    })
}
