// 修改数据的操作（与安卓 AppViewModel、iPad Store 同样的规则）：都是纯函数，输入旧数据返回新数据，不改原对象。
// 班级级别的操作写成 (c, ...) => c，配合 onClass(d, classId, f) 用在整份数据上。
import { newId, rechargeScores, removeRecharge, addStudentScores } from './model.js';

const without = (o, ids) => { const s = new Set(ids); return Object.fromEntries(Object.entries(o || {}).filter(([k]) => !s.has(k))); };
const UNKNOWN = '?';

/** 把 f 用在某个班级上（找不到班级时原样返回） */
export function onClass(d, classId, f) {
  let hit = false;
  const classes = d.classes.map((c) => { if (c.id !== classId) return c; const n = f(c); if (n !== c) hit = true; return n; });
  return hit ? { ...d, classes } : d;
}

// ---------- 班级 ----------
export function addClass(d, name, emoji = '🎒') {
  const c = { id: newId(), name: String(name).trim() || '新班级', emoji, students: [], assignments: [], scores: [], recharges: [], exams: [], groupLeaders: {}, reps: [], seating: null, autoReset: null, settlements: [] };
  return { d: { ...d, classes: [...d.classes, c], currentClassId: c.id }, id: c.id };
}
export const renameClass = (d, id, name, emoji) => onClass(d, id, (c) => ({ ...c, name: String(name).trim() || c.name, emoji: emoji || c.emoji }));
export function deleteClass(d, id) {
  const rest = d.classes.filter((c) => c.id !== id);
  return { ...d, classes: rest, currentClassId: d.currentClassId === id ? rest[0]?.id ?? null : d.currentClassId };
}

// ---------- 作业 ----------
/** 新建作业：长期请假的同学自动记为请假 */
export function createAssignment(c, title, subject = '', createdAt = Date.now()) {
  const statuses = Object.fromEntries(c.students.filter((s) => s.longLeave).map((s) => [s.id, 'LEAVE']));
  const a = { id: newId(), title: String(title).trim() || '作业', subject: String(subject || '').trim(), createdAt, statuses, comments: {}, countedBooks: null, pinned: false, reviewFailed: [], plagiarism: {} };
  return { c: { ...c, assignments: [...c.assignments, a] }, id: a.id };
}
export const editAssignment = (c, id, f) => ({ ...c, assignments: c.assignments.map((a) => (a.id === id ? f(a) : a)) });
export const deleteAssignment = (c, id) => ({ ...c, assignments: c.assignments.filter((a) => a.id !== id) });

/** 去掉这些同学的抄袭标记（作为抄袭者和提供者） */
export function withoutPlagiarism(a, ids) {
  const p = a.plagiarism || {};
  if (!Object.keys(p).length) return a;
  const set = new Set(ids);
  const out = {};
  for (const [r, ps] of Object.entries(p)) { if (set.has(r)) continue; const rest = ps.filter((x) => !set.has(x)); if (rest.length) out[r] = rest; }
  return { ...a, plagiarism: out };
}
/** 登记状态；改成其他状态时取消复审不合格和抄袭标记 */
export const setStatus = (c, aid, ids, status) => editAssignment(c, aid, (a) => {
  const m = { ...a.statuses };
  for (const id of ids) { if (status === 'NONE') delete m[id]; else m[id] = status; }
  const set = new Set(ids);
  return withoutPlagiarism({ ...a, statuses: m, reviewFailed: (a.reviewFailed || []).filter((x) => !set.has(x)) }, ids);
});
/** 复审不合格：记为未交（多算一次未交），扣分翻倍 */
export const markReviewFailed = (c, aid, ids) => editAssignment(c, aid, (a) => ({
  ...a, statuses: { ...a.statuses, ...Object.fromEntries(ids.map((id) => [id, 'MISSING'])) }, reviewFailed: [...new Set([...(a.reviewFailed || []), ...ids])],
}));
/** 抄袭：抄袭者和提供者都记为未交；一个提供者都没选 = 提供者不明 */
export const markPlagiarism = (c, aid, receivers, providers) => editAssignment(c, aid, (a) => {
  const p = { ...(a.plagiarism || {}) };
  const real = [...new Set(providers.filter((x) => x !== UNKNOWN))];
  for (const r of receivers) { const ps = real.filter((x) => x !== r); p[r] = ps.length ? ps : [UNKNOWN]; }
  return { ...a, statuses: { ...a.statuses, ...Object.fromEntries([...receivers, ...real].map((id) => [id, 'MISSING'])) }, plagiarism: p };
});
export const clearPlagiarism = (c, aid, ids) => editAssignment(c, aid, (a) => withoutPlagiarism(a, ids));
/** 未登记的同学一键标为某状态 */
export const fillRemaining = (c, aid, status) => editAssignment(c, aid, (a) => {
  const m = { ...a.statuses };
  for (const s of c.students) if ((m[s.id] || 'NONE') === 'NONE') m[s.id] = status;
  return { ...a, statuses: m };
});
export const setComment = (c, aid, sid, text) => editAssignment(c, aid, (a) => {
  const m = { ...(a.comments || {}) };
  if (String(text || '').trim()) m[sid] = String(text).trim(); else delete m[sid];
  return { ...a, comments: m };
});

// ---------- 积分 ----------
export const addScore = (c, ids, delta, reason) => addStudentScores(c, ids, delta, String(reason || '').trim() || (delta > 0 ? '加分' : '扣分'));
export const removeScore = (c, entryId) => ({ ...c, scores: c.scores.filter((e) => e.id !== entryId) });
export const recharge = (c, rules, ids, target, note, opts) => rechargeScores(c, rules, ids, target, note, Date.now(), opts);
export const undoRecharge = (c, id) => removeRecharge(c, id);

// ---------- 学生 ----------
export const upsertStudent = (c, s) => (c.students.some((x) => x.id === s.id)
  ? { ...c, students: c.students.map((x) => (x.id === s.id ? s : x)) }
  : { ...c, students: [...c.students, { id: newId(), group: '', gender: '', note: '', longLeave: false, ...s }] });
export function deleteStudent(c, id) {
  return {
    ...c,
    students: c.students.filter((s) => s.id !== id),
    scores: c.scores.filter((e) => e.studentId !== id),
    exams: (c.exams || []).map((e) => ({ ...e, scores: without(e.scores, [id]), absent: (e.absent || []).filter((x) => x !== id) })),
    groupLeaders: Object.fromEntries(Object.entries(c.groupLeaders || {}).filter(([, v]) => v !== id)),
    reps: (c.reps || []).filter((x) => x !== id),
    recharges: (c.recharges || []).flatMap((r) => (!(r.studentIds || []).includes(id) ? [r] : (r.studentIds.length > 1 ? [{ ...r, studentIds: r.studentIds.filter((x) => x !== id), before: without(r.before, [id]) }] : []))),
    assignments: c.assignments.map((a) => withoutPlagiarism({ ...a, statuses: without(a.statuses, [id]), comments: without(a.comments, [id]), reviewFailed: (a.reviewFailed || []).filter((x) => x !== id) }, [id])),
    seating: c.seating ? { ...c.seating, seats: without(c.seating.seats, [id]) } : c.seating,
  };
}
/** 设置 / 取消组长（studentId 为 null 时取消） */
export function setGroupLeader(c, group, studentId) {
  const m = { ...(c.groupLeaders || {}) };
  if (studentId) m[group] = studentId; else delete m[group];
  return { ...c, groupLeaders: m };
}
export const setRep = (c, sid, on) => ({ ...c, reps: on ? [...new Set([...(c.reps || []), sid])] : (c.reps || []).filter((x) => x !== sid) });
/** 导入名单：按学号合并（同学号更新姓名），或整体替换 */
export function importRoster(c, students, replace) {
  if (replace) return { ...c, students, assignments: [], scores: [], recharges: [], exams: [], groupLeaders: {} };
  const byNo = new Map(students.filter((s) => s.no).map((s) => [s.no, s]));
  const merged = c.students.map((o) => { const n = o.no && byNo.get(o.no); return n ? { ...o, name: n.name, group: n.group || o.group, gender: n.gender || o.gender } : o; });
  const have = new Set(c.students.map((s) => s.no).filter(Boolean));
  return { ...c, students: [...merged, ...students.filter((s) => !s.no || !have.has(s.no))] };
}

// ---------- 考试 ----------
export const newExamId = () => newId();
export function upsertExam(c, e) {
  const exams = c.exams || [];
  const full = { id: newId(), subject: '', date: Date.now(), fullScore: 100, scores: {}, absent: [], note: '', autoScore: true, ...e };
  return { ...c, exams: exams.some((x) => x.id === full.id) ? exams.map((x) => (x.id === full.id ? full : x)) : [...exams, full] };
}
export const deleteExam = (c, id) => ({ ...c, exams: (c.exams || []).filter((e) => e.id !== id) });
/** 录入一位同学的成绩（score 为 null 时清除），absent 为 true 时记为缺考 */
export const setExamScore = (c, eid, sid, score, absent = false) => ({
  ...c,
  exams: (c.exams || []).map((e) => (e.id !== eid ? e : {
    ...e,
    scores: score == null || absent ? without(e.scores, [sid]) : { ...e.scores, [sid]: score },
    absent: absent ? [...new Set([...(e.absent || []), sid])] : (e.absent || []).filter((x) => x !== sid),
  })),
});
