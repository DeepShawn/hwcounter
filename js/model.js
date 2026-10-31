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
  }));
  return out;
}

export const ruleOf = (rules, s) => (s === 'NONE' || !s ? 0 : rules[{ SUBMITTED: 'submitted', EXCELLENT: 'excellent', LATE: 'late', MISSING: 'missing', LEAVE: 'leave' }[s]] ?? 0);

export function summary(c, a) {
  const counts = Object.fromEntries(STATUS_KEYS.map((k) => [k, 0]));
  for (const s of c.students) counts[a.statuses[s.id] || 'NONE']++;
  const handedIn = counts.SUBMITTED + counts.EXCELLENT + counts.LATE;
  const expected = c.students.length - counts.LEAVE;
  return { counts, handedIn, expected, rate: expected <= 0 ? 1 : handedIn / expected };
}

export function studentStats(c, rules) {
  const manual = {};
  for (const e of c.scores) manual[e.studentId] = (manual[e.studentId] || 0) + e.delta;
  return c.students.map((st) => {
    const counts = Object.fromEntries(STATUS_KEYS.map((k) => [k, 0]));
    let auto = 0;
    for (const a of c.assignments) { const s = a.statuses[st.id] || 'NONE'; counts[s]++; auto += ruleOf(rules, s); }
    return { student: st, score: rules.initial + auto + (manual[st.id] || 0), counts, submitted: counts.SUBMITTED + counts.EXCELLENT, late: counts.LATE, missing: counts.MISSING };
  });
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
