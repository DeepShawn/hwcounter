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

export const defaultEscalation = () => ({ enabled: true, base: 2, heavyBase: 3, heavyAfter: 6, cap: 30 });
export const defaultStreakBonus = () => ({ enabled: true, after: 5, bonus: 1, lateCounts: false });
/** 作业复审：不合格记一次未交（参与连续 / 累计），这一次扣分 × multiplier；没复审的默认合格 */
export const defaultReview = () => ({ enabled: true, multiplier: 2 });
export const defaultRules = () => ({ initial: 100, submitted: 0, excellent: 1, late: -1, missing: -2, leave: 0, escalation: defaultEscalation(), streakBonus: defaultStreakBonus(), roundPoints: true, review: defaultReview() });
/** 这位同学这次作业是否复审不合格（只有状态仍是未交时才算） */
export const failedReview = (a, sid) => (a.reviewFailed || []).includes(sid) && a.statuses[sid] === 'MISSING';
/** 正分向上取整、负分向下取整（与安卓 roundAway 一致） */
export const roundAway = (x) => (x > 0 ? Math.ceil(x - 1e-9) : x < 0 ? Math.floor(x + 1e-9) : 0);
/** 单笔加减分保留原值；取整只作用在最终显示的总积分上 */
export const pt = (rules, x) => x;
export const shown = (rules, total) => (rules.roundPoints === false ? total : roundAway(total));
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
  out.settings.rules.escalation = { ...defaultEscalation(), ...(out.settings.rules.escalation || {}) };
  out.settings.rules.streakBonus = { ...defaultStreakBonus(), ...(out.settings.rules.streakBonus || {}) };
  out.settings.rules.review = { ...defaultReview(), ...(out.settings.rules.review || {}) };
  out.settings.ai = { ...base.settings.ai, ...(out.settings.ai || {}) };
  out.classes = (out.classes || []).map((c) => ({
    id: newId(), emoji: '🎒', students: [], assignments: [], scores: [], ...c,
    students: (c.students || []).map((s) => ({ id: newId(), group: '', gender: '', note: '', longLeave: false, ...s })),
    assignments: (c.assignments || []).map((a) => ({ id: newId(), subject: '', createdAt: Date.now(), statuses: {}, comments: {}, countedBooks: null, pinned: false, reviewFailed: [], ...a })),
    scores: (c.scores || []).map((e) => ({ id: newId(), time: Date.now(), ...e })),
    exams: (c.exams || []).map((e) => ({ id: newId(), subject: '', date: Date.now(), fullScore: 100, scores: {}, absent: [], note: '', autoScore: true, ...e })),
    groupLeaders: c.groupLeaders || {},
    recharges: (c.recharges || []).map((r) => ({ id: newId(), time: Date.now(), studentIds: [], before: {}, note: '', ...r })),
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

/**
 * 积分重置（与安卓 Recharge.kt 同算法）：每位学生最近一次重置生效，
 * 当前积分 = 重置分数 + 重置之后布置的作业自动计分 + 重置之后的手动加减分。
 */
/** 计分起点：effectiveAt（从某次作业 / 某一天起，含当刻）或操作时间（立即生效，只计之后的） */
export const rechargeStart = (r) => (r.effectiveAt ?? r.time);
export const rechargeCounts = (r, t) => (r.effectiveAt != null ? t >= r.effectiveAt : t > r.time);
export function latestRecharges(c) {
  const out = {};
  const list = [...(c.recharges || [])].sort((a, b) => rechargeStart(a) - rechargeStart(b) || a.time - b.time || (a.id < b.id ? -1 : a.id > b.id ? 1 : 0));
  for (const r of list) for (const sid of r.studentIds || []) out[sid] = r;
  return out;
}
export const entryCounts = (e, r) => !r || rechargeCounts(r, e.time);
export const assignmentCounts = (a, r) => !r || rechargeCounts(r, a.createdAt);
/** 某天 0 点（本地时区） */
export const startOfDay = (t) => { const d = new Date(t); d.setHours(0, 0, 0, 0); return d.getTime(); };

/** 考试计分档（与安卓 Exams.kt 同算法）：按得分率落档加减分，最高档含 100% */
export const defaultExamRules = () => ({ enabled: true, bands: [{ minPct: 90, maxPct: 100, delta: 2 }, { minPct: 80, maxPct: 90, delta: 1 }, { minPct: 0, maxPct: 60, delta: -1 }], absent: 0 });
export function examDelta(rules, e, sid) {
  const er = { ...defaultExamRules(), ...(rules.exam || {}) };
  if (!er.enabled || e.autoScore === false) return 0;
  if ((e.absent || []).includes(sid)) return er.absent || 0;
  const sc = e.scores?.[sid]; const full = e.fullScore || 0;
  if (sc == null || !Number.isFinite(sc) || full <= 0) return 0;
  const pct = sc / full * 100; const top = Math.max(100, ...er.bands.map((b) => b.maxPct));
  const b = er.bands.find((x) => pct >= x.minPct && (pct < x.maxPct || (x.maxPct >= top && pct >= x.maxPct)));
  return b ? b.delta : 0;
}

/**
 * 未交连续递增扣分（与安卓 Penalty.kt 同算法）：连续未交第 n 次扣 base^n（2、4、8…）；
 * 请假、未登记不打断也不计次，交过一次即重算；本轮累计超 heavyAfter 次后底数为 heavyBase（3、9、27…）；
 * 每次积分重置开始新一轮。返回 作业id → 分。
 */
export function autoPoints(c, rules, sid) {
  const esc = { ...defaultEscalation(), ...(rules.escalation || {}) };
  const sb = { ...defaultStreakBonus(), ...(rules.streakBonus || {}) };
  const rv = { ...defaultReview(), ...(rules.review || {}) };
  const out = {};
  const resets = (c.recharges || []).filter((r) => (r.studentIds || []).includes(sid))
    .sort((a, b) => rechargeStart(a) - rechargeStart(b) || a.time - b.time || (a.id < b.id ? -1 : a.id > b.id ? 1 : 0));
  const list = [...c.assignments].sort((a, b) => a.createdAt - b.createdAt || (a.id < b.id ? -1 : a.id > b.id ? 1 : 0));
  let round; let streak = 0; let total = 0; let run = 0;
  for (const a of list) {
    const s = a.statuses[sid] || 'NONE';
    let r = null; for (const x of resets) if (rechargeCounts(x, a.createdAt)) r = x.id;
    if (r !== round) { round = r; streak = 0; total = 0; run = 0; }
    if (s === 'MISSING') {
      streak++; total++; run = 0;
      let base;
      if (!esc.enabled) base = ruleOf(rules, s);
      else { const b = total > esc.heavyAfter ? esc.heavyBase : esc.base; let p = b ** streak; if (esc.cap > 0) p = Math.min(p, esc.cap); base = -p; }
      out[a.id] = pt(rules, rv.enabled && failedReview(a, sid) ? base * rv.multiplier : base);
    } else if (s === 'SUBMITTED' || s === 'EXCELLENT' || s === 'LATE') {
      streak = 0;
      const counts = s !== 'LATE' || sb.lateCounts;
      run = counts ? run + 1 : 0;
      const bonus = sb.enabled && counts && run > sb.after ? sb.bonus : 0;
      out[a.id] = pt(rules, ruleOf(rules, s)) + pt(rules, bonus);
    } else out[a.id] = pt(rules, ruleOf(rules, s));
  }
  return out;
}

export function studentStats(c, rules) {
  let byRules = statsCache.get(c);
  if (!byRules) { byRules = new WeakMap(); statsCache.set(c, byRules); }
  if (byRules.has(rules)) return byRules.get(rules);
  const last = latestRecharges(c);
  const manual = {};
  for (const e of c.scores) if (entryCounts(e, last[e.studentId])) manual[e.studentId] = (manual[e.studentId] || 0) + pt(rules, e.delta);
  const result = c.students.map((st) => {
    const r = last[st.id];
    const counts = Object.fromEntries(STATUS_KEYS.map((k) => [k, 0]));
    let auto = 0;
    const pts = autoPoints(c, rules, st.id);
    for (const a of c.assignments) { const s = a.statuses[st.id] || 'NONE'; counts[s]++; if (assignmentCounts(a, r)) auto += pts[a.id] ?? ruleOf(rules, s); }
    let examPts = 0;
    for (const e of c.exams || []) if (!r || rechargeCounts(r, e.date)) examPts += pt(rules, examDelta(rules, e, st.id));
    return { student: st, score: shown(rules, (r ? r.target : rules.initial) + auto + (manual[st.id] || 0) + examPts), counts, submitted: counts.SUBMITTED + counts.EXCELLENT, late: counts.LATE, missing: counts.MISSING, recharge: r || null };
  });
  byRules.set(rules, result);
  return result;
}

/** 积分重置：只重置积分并新增一条记录，历史流水 / 作业状态 / 未交次数全部保留 */
export function rechargeScores(c, rules, studentIds, target, note = '', time = Date.now(), { effectiveAt = null, assignmentId = null } = {}) {
  if (!Number.isFinite(target)) return c;
  const wanted = new Set(studentIds);
  const ids = c.students.map((s) => s.id).filter((id) => wanted.has(id));
  if (!ids.length) return c;
  const now = Object.fromEntries(studentStats(c, rules).map((st) => [st.student.id, st.score]));
  const a = assignmentId ? c.assignments.find((x) => x.id === assignmentId) : null;
  const start = a ? a.createdAt : effectiveAt;
  const r = { id: newId(), time, target, studentIds: ids, before: Object.fromEntries(ids.map((id) => [id, now[id] ?? 0])), note: String(note || '').trim(),
    effectiveAt: start ?? null, assignmentId: a ? a.id : null, assignmentTitle: a ? a.title : '' };
  return { ...c, recharges: [...(c.recharges || []), r] };
}
/** 撤销重置；撤销每月定期重置（id 为 auto-年月）时记下月份，不再自动补回 */
export const removeRecharge = (c, id) => {
  const out = { ...c, recharges: (c.recharges || []).filter((r) => r.id !== id) };
  if (String(id).startsWith('auto-')) {
    const ar = c.autoReset || { enabled: false, target: 100, day: 1, note: '每月定期重置', since: Date.now(), skipped: [] };
    out.autoReset = { ...ar, skipped: [...new Set([...(ar.skipped || []), String(id).slice(5)])] };
  }
  return out;
};

/** 每月定期重置（与安卓 AutoReset.kt 同算法）：开启后每月 day 号 0 点起全班重置为 target */
export function autoResetDue(ar, now = Date.now()) {
  if (!ar || !ar.enabled || !(now > (ar.since ?? 0))) return [];
  const d = new Date(now); let y = d.getFullYear(); let m = d.getMonth(); const out = [];
  for (let i = 0; i < 24; i++) {
    const t = new Date(y, m, Math.min(Math.max(ar.day || 1, 1), 28)).getTime();
    if (t <= (ar.since ?? 0)) break;
    if (t <= now) out.push([`${y}${String(m + 1).padStart(2, '0')}`, t]);
    m--; if (m < 0) { m = 11; y--; }
  }
  return out.reverse();
}
export function applyAutoReset(c, rules, now = Date.now()) {
  const ar = c.autoReset;
  if (!ar || !ar.enabled || !c.students.length || !Number.isFinite(ar.target)) return c;
  let out = c;
  for (const [k, t] of autoResetDue(ar, now)) {
    const id = `auto-${k}`;
    if ((ar.skipped || []).includes(k) || (out.recharges || []).some((r) => r.id === id)) continue;
    const past = { ...out, assignments: out.assignments.filter((a) => a.createdAt <= t - 1), scores: out.scores.filter((e) => e.time <= t - 1),
      exams: (out.exams || []).filter((e) => e.date <= t - 1), recharges: (out.recharges || []).filter((r) => rechargeStart(r) <= t - 1) };
    const before = Object.fromEntries(studentStats(past, rules).map((st) => [st.student.id, st.score]));
    out = { ...out, recharges: [...(out.recharges || []), { id, time: t, target: ar.target, studentIds: out.students.map((s) => s.id), before, note: ar.note || '每月定期重置', effectiveAt: t, assignmentId: null, assignmentTitle: '' }] };
  }
  return out;
}
export function applyAutoResets(d, now = Date.now()) {
  const classes = d.classes.map((c) => applyAutoReset(c, d.settings.rules, now));
  return classes.some((c, i) => c !== d.classes[i]) ? { ...d, classes } : d;
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
    .map((st) => ({ id: newId(), studentId: st.student.id, delta: target - st.score, reason: `重置分数至 ${fmtNum(target)}`, time }));
  return entries.length ? { ...c, scores: [...c.scores, ...entries] } : c;
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
