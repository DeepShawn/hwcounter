// 数据模型：与安卓版 AppData JSON 完全一致（字段名、枚举名、默认值）
export const newId = () => Array.from(crypto.getRandomValues(new Uint8Array(8)), (b) => b.toString(16).padStart(2, '0')).join('');

export const STATUS = {
  NONE: { label: '未登记', short: '—', emoji: '⏳', color: '#9aa0a8' },
  SUBMITTED: { label: '已交', short: '交', emoji: '✅', color: '#1db57a' },
  EXCELLENT: { label: '优秀', short: '优', emoji: '🌟', color: '#ffb300' },
  LATE: { label: '迟交', short: '迟', emoji: '🐢', color: '#ff8a3d' },
  MISSING: { label: '未交', short: '缺', emoji: '❌', color: '#f0475a' },
  LEAVE: { label: '请假', short: '假', emoji: '🏖️', color: '#4a8cff' },
};
export const STATUS_KEYS = Object.keys(STATUS);

export const defaultRules = () => ({ initial: 100, submitted: 0, excellent: 1, late: -1, missing: -2, leave: 0 });
export const defaultSettings = () => ({
  theme: 'mint', dark: 0, layout: 'grid', shape: 1, compact: false, rules: defaultRules(),
  ai: { provider: 'openai', baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1', apiKey: '', model: 'qwen-vl-max', vision: true },
  recogMode: 0, minIdLength: 4, fuzzy: true, matchNames: true, haptic: true, sound: true, confetti: true,
  subjects: ['语文', '数学', '英语', '物理', '化学', '生物', '历史', '地理', '政治'],
  quickReasons: ['课堂表现好', '主动回答', '作业优秀', '进步明显', '迟到', '违反纪律', '未带作业'],
  onboarded: false, voiceFuzzy: 1, voiceInitials: true,
});
export const emptyData = () => ({ version: 1, classes: [], currentClassId: null, settings: defaultSettings() });

/** 兼容旧数据/缺字段：保留未知字段，补齐默认值 */
export function normalizeData(d) {
  const base = emptyData();
  const out = { ...base, ...(d || {}) };
  out.settings = { ...base.settings, ...(out.settings || {}) };
  out.settings.rules = { ...defaultRules(), ...(out.settings.rules || {}) };
  out.settings.ai = { ...base.settings.ai, ...(out.settings.ai || {}) };
  out.classes = (out.classes || []).map((c) => ({
    id: newId(), emoji: '🎒', students: [], assignments: [], scores: [], ...c,
    students: (c.students || []).map((s) => ({ id: newId(), group: '', gender: '', note: '', longLeave: false, ...s })),
    assignments: (c.assignments || []).map((a) => ({ id: newId(), subject: '', createdAt: Date.now(), statuses: {}, comments: {}, countedBooks: null, pinned: false, ...a })),
    scores: (c.scores || []).map((e) => ({ id: newId(), time: Date.now(), ...e })),
    scoreResets: c.scoreResets || [],
  }));
  return out;
}

export const ruleOf = (rules, s) => (s === 'NONE' || !s ? 0 : rules[{ SUBMITTED: 'submitted', EXCELLENT: 'excellent', LATE: 'late', MISSING: 'missing', LEAVE: 'leave' }[s]] ?? 0);

const summaryCache = new WeakMap();
const statsCache = new WeakMap();

/** Natural student-number order, without converting digit runs to imprecise numbers. */
export function compareStudentNumbers(left, right) {
  const a = String(left ?? '').trim().toLowerCase(); const b = String(right ?? '').trim().toLowerCase();
  if (!a || !b) return a === b ? 0 : !a ? 1 : -1;
  const xs = a.match(/[0-9]+|[^0-9]+/g); const ys = b.match(/[0-9]+|[^0-9]+/g);
  for (let i = 0; i < Math.min(xs.length, ys.length); i++) {
    let x = xs[i]; let y = ys[i];
    if (/^[0-9]/.test(x) && /^[0-9]/.test(y)) {
      x = x.replace(/^0+(?=\d)/, ''); y = y.replace(/^0+(?=\d)/, '');
      if (x.length !== y.length) return x.length - y.length;
    }
    if (x !== y) return x < y ? -1 : 1;
  }
  return xs.length - ys.length;
}

export function summary(c, a) {
  let assignments = summaryCache.get(c.students);
  if (!assignments) { assignments = new WeakMap(); summaryCache.set(c.students, assignments); }
  if (assignments.has(a)) return assignments.get(a);
  const counts = Object.fromEntries(STATUS_KEYS.map((k) => [k, 0]));
  for (const s of c.students) counts[a.statuses[s.id] || 'NONE']++;
  const handedIn = counts.SUBMITTED + counts.EXCELLENT + counts.LATE;
  const expected = c.students.length - counts.LEAVE;
  const result = { counts, handedIn, expected, rate: expected <= 0 ? 1 : handedIn / expected };
  assignments.set(a, result);
  return result;
}

export function studentStats(c, rules) {
  let byRules = statsCache.get(c);
  if (!byRules) { byRules = new WeakMap(); statsCache.set(c, byRules); }
  if (byRules.has(rules)) return byRules.get(rules);
  const manual = {};
  const reset = currentScoreReset(c);
  for (const e of c.scores) if (scoreEntryCounts(c, e, reset)) manual[e.studentId] = (manual[e.studentId] || 0) + e.delta;
  const counted = new Set(c.assignments.filter((a) => assignmentCounts(c, a, reset)).map((a) => a.id));
  const result = c.students.map((st) => {
    const counts = Object.fromEntries(STATUS_KEYS.map((k) => [k, 0]));
    let auto = 0;
    for (const a of c.assignments) { const s = a.statuses[st.id] || 'NONE'; counts[s]++; if (counted.has(a.id)) auto += ruleOf(rules, s); }
    return { student: st, score: rules.initial + auto + (manual[st.id] || 0), counts, submitted: counts.SUBMITTED + counts.EXCELLENT, late: counts.LATE, missing: counts.MISSING };
  });
  byRules.set(rules, result);
  return result;
}

export function addStudentScores(c, studentIds, delta, reason) {
  if (!Number.isFinite(delta) || delta === 0) return c;
  const ids = new Set(studentIds); const time = Date.now();
  const entries = c.students.filter((st) => ids.has(st.id)).map((st) => ({ id: newId(), studentId: st.id, delta, reason, time }));
  return entries.length ? { ...c, scores: [...c.scores, ...entries] } : c;
}

/** 通过一条调整记录重置积分，保留原有流水、作业状态及未交次数。 */
export function resetStudentScores(c, rules, studentIds, target) {
  if (!Number.isFinite(target)) return c;
  const ids = new Set(studentIds); const time = Date.now();
  const entries = studentStats(c, rules).filter((st) => ids.has(st.student.id) && st.score !== target)
    .map((st) => ({ id: newId(), studentId: st.student.id, delta: target - st.score, reason: `重置分数至 ${fmtNum(target)}`, time, kind: 'reset' }));
  return entries.length ? { ...c, scores: [...c.scores, ...entries] } : c;
}

export function currentScoreReset(c) {
  const latest = (c.scoreResets || []).reduce((best, r) => !best || r.time > best.time || (r.time === best.time && r.id > best.id) ? r : best, null);
  return latest?.assignmentId ? latest : null;
}
const isResetAdjustment = (e) => e.kind === 'reset' || /^重置分数至 [+-]?[0-9]+(?:\.[0-9]+)?$/.test(e.reason);
export function assignmentCounts(c, a, reset = currentScoreReset(c)) {
  return !reset || (a.createdAt >= reset.startAt && !(reset.excludedAssignmentIds || []).includes(a.id));
}
export function scoreEntryCounts(c, e, reset = currentScoreReset(c)) {
  return !reset || (!(reset.ignoredScoreIds || []).includes(e.id) && !(isResetAdjustment(e) && e.time < reset.time) && e.time >= (reset.keepManualSinceStart === false ? reset.time : reset.startAt));
}
export function startScorePeriod(c, assignmentId, keepManualSinceStart = true, time = Date.now()) {
  const ordered = [...c.assignments].sort((a, b) => a.createdAt - b.createdAt);
  const index = ordered.findIndex((a) => a.id === assignmentId);
  if (index < 0) return c;
  const a = ordered[index];
  const reset = { id: newId(), assignmentId, assignmentTitle: a.title, startAt: a.createdAt, time, keepManualSinceStart,
    excludedAssignmentIds: ordered.slice(0, index).filter((x) => x.createdAt === a.createdAt).map((x) => x.id),
    ignoredScoreIds: c.scores.filter((e) => e.time >= time && (!keepManualSinceStart || isResetAdjustment(e))).map((e) => e.id) };
  return { ...c, scoreResets: [...(c.scoreResets || []), reset] };
}
export function restoreScoreHistory(c, time = Date.now()) {
  return { ...c, scoreResets: [...(c.scoreResets || []), { id: newId(), assignmentId: null, assignmentTitle: '', startAt: 0, time, keepManualSinceStart: true, excludedAssignmentIds: [], ignoredScoreIds: [] }] };
}
export function scoreBoundaryIndex(c, assignments) {
  const reset = currentScoreReset(c);
  return reset ? assignments.findIndex((a, i) => assignmentCounts(c, a, reset) &&
    (a.id === reset.assignmentId || (i > 0 && !assignmentCounts(c, assignments[i - 1], reset)))) : -1;
}

export const fmtNum = (x) => { const r = Math.round(x * 10) / 10; return Number.isInteger(r) ? String(r) : r.toFixed(1); };
export const signed = (x) => (x > 0 ? '+' : '') + fmtNum(x);
/** 作业只显示年月日 */
export const fmtDate = (t) => { const d = new Date(t); return `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日`; };
export const isToday = (t) => new Date(t).toDateString() === new Date().toDateString();

// ---------- 名单导入（JSON / CSV / 粘贴），与安卓版同规则 ----------
const noKeys = ['no', 'id', 'studentno', 'student_no', 'studentid', 'student_id', 'number', 'num', 'code', '学号', '编号', '考号'];
const nameKeys = ['name', 'studentname', 'student_name', '姓名', '名字'];
const groupKeys = ['group', 'team', '小组', '组', '组别'];
const genderKeys = ['gender', 'sex', '性别'];
const listKeys = ['students', 'list', 'data', 'members', '学生', '名单'];
const classKeys = ['classname', 'class', 'name', '班级', '班级名称'];
const pick = (o, keys) => { for (const [k, v] of Object.entries(o)) if (keys.includes(k.toLowerCase()) && v != null && String(v).trim()) return String(v).trim(); return null; };
const mkStudent = (no, name, group = '', gender = '') => ({ id: newId(), no: no || '', name: name || no || '', group, gender, note: '', longLeave: false });
function partsTo(parts) {
  if (parts.length === 1) return mkStudent('', parts[0]);
  const aIsNo = /\d/.test(parts[0]);
  return mkStudent(aIsNo ? parts[0] : parts[1], aIsNo ? parts[1] : parts[0], parts[2] || '');
}
export function parseRoster(text) {
  const t = text.trim().replace(/^﻿/, '');
  let className = null; let list = [];
  if (t.startsWith('{') || t.startsWith('[')) {
    const root = JSON.parse(t);
    let arr = Array.isArray(root) ? root : null;
    if (!arr && root && typeof root === 'object') {
      const lk = Object.keys(root).find((k) => listKeys.includes(k.toLowerCase()) && Array.isArray(root[k]));
      if (lk) { arr = root[lk]; className = pick(root, classKeys); }
    }
    if (arr) {
      list = arr.map((el) => {
        if (el && typeof el === 'object' && !Array.isArray(el)) { const no = pick(el, noKeys); const name = pick(el, nameKeys); return no || name ? mkStudent(no, name, pick(el, groupKeys) || '', pick(el, genderKeys) || '') : null; }
        if (Array.isArray(el)) return el.length ? partsTo(el.map(String)) : null;
        if (typeof el === 'string' || typeof el === 'number') return partsTo(String(el).split(/[,，\t;； ]+/).filter(Boolean));
        return null;
      }).filter(Boolean);
    } else if (root && typeof root === 'object') {
      list = Object.entries(root).filter(([, v]) => typeof v === 'string').map(([k, v]) => mkStudent(k.trim(), v.trim()));
    }
  } else {
    list = t.split(/\r?\n/).map((l) => l.trim()).filter(Boolean).map((l) => partsTo(l.split(/[,，\t;； ]+/).map((p) => p.replace(/^"|"$/g, '')).filter(Boolean)))
      .filter((s) => s.no !== '学号' && s.no.toLowerCase() !== 'no' && s.name !== '姓名');
  }
  list.forEach((s, i) => { if (!s.no) s.no = String(i + 1).padStart(2, '0'); });
  return { className, students: list };
}
