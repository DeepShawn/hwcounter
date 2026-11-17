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

/** 分段倍率的默认分段：第 1–2 次 ×1、第 3–4 次 ×1.5、第 5 次起 ×2 */
export const defaultTiers = () => [{ from: 1, factor: 1 }, { from: 3, factor: 1.5 }, { from: 5, factor: 2 }];
/** mode：1 正常加倍（2、4、8…），2 第 2 次起加倍（2、2、4、8…），3 分段倍率（在 tierBase = 1 或 2 的基础上按 tierBy 计次分段乘倍率） */
export const defaultEscalation = () => ({ enabled: true, base: 2, heavyBase: 3, heavyAfter: 6, cap: 30, mode: 2, tierBase: 2, tierBy: 'streak', tiers: defaultTiers() });
export const defaultStreakBonus = () => ({ enabled: true, after: 5, bonus: 1, lateCounts: false });
/** 作业复审：不合格记一次未交（参与连续 / 累计），这一次扣分 × multiplier；没复审的默认合格 */
export const defaultReview = () => ({ enabled: true, multiplier: 2 });
/** 抄袭：抄袭者和提供者都记未交、扣分 ×multiplier；累犯逐次加重（第 n 次再 ×n，最多 ×maxTimes），每轮重新计次 */
export const defaultPlagiarism = () => ({ enabled: true, multiplier: 2, escalate: true, maxTimes: 4 });
export const plagiarismFactor = (pr, n) => pr.multiplier * (pr.escalate ? Math.min(Math.max(1, n), Math.max(1, pr.maxTimes)) : 1);
/** 组长奖惩（与安卓 Leader.kt 同算法）：按周期（assignment / day / week / month）看小组人均作业扣分 */
export const defaultLeader = () => ({ enabled: true, period: 'week', clean: 3, low: 2, below: 1, above: -1, high: -2, veryHigh: -3, top: 1, bottom: -1, progress: 1, regress: -1, streakStep: 1, streakMax: 3, cap: 5, mode: 'both' });
/** 加减方式：both / plus（仅加分）/ minus（仅扣分） */
export const applyMode = (mode, x) => (mode === 'plus' ? Math.max(0, x) : mode === 'minus' ? Math.min(0, x) : x);
/** 小组对抗排名（与安卓 GroupRank.kt 同算法）：按周期给小组综合排名，按名次给整组每人加减分 */
export const defaultGroupRank = () => ({ enabled: true, period: 'week', first: 2, second: 1, third: 0, last: -1, wRate: 0.5, wDeduct: 0.25, wExcellent: 0.15, wExam: 0.1, mode: 'both' });
/** 信誉分加减分（与安卓 Credit.kt 同算法）：个人 / 小组分别选 both 加分和扣分 / plus 只加不扣 / minus 只扣不加 / none 不计入 */
export const defaultCredit = () => ({ personal: 'none', group: 'none', period: 'week', excellentAt: 90, excellentPts: 2, goodAt: 80, goodPts: 1, poorBelow: 60, poorPts: -1, badBelow: 40, badPts: -2 });
export const defaultRules = () => ({ initial: 100, submitted: 0, excellent: 1, late: -1, missing: -2, leave: 0, escalation: defaultEscalation(), streakBonus: defaultStreakBonus(), roundPoints: true, review: defaultReview(), plagiarism: defaultPlagiarism(), leader: defaultLeader(), groupRank: defaultGroupRank(), credit: defaultCredit() });
/** 这位同学这次作业是否复审不合格（只有状态仍是未交时才算） */
export const failedReview = (a, sid) => (a.reviewFailed || []).includes(sid) && a.statuses[sid] === 'MISSING';
/** 抄袭（只有状态仍是“未交”时才算）：这位同学抄了谁的 / 谁抄了这位同学的 / 是否涉及 */
export const copiedFrom = (a, sid) => (a.statuses[sid] === 'MISSING' ? (a.plagiarism || {})[sid] || [] : []);
export const copiedBy = (a, sid) => (a.statuses[sid] !== 'MISSING' ? [] : Object.entries(a.plagiarism || {}).filter(([r, ps]) => ps.includes(sid) && a.statuses[r] === 'MISSING').map(([r]) => r));
export const plagiarized = (a, sid) => copiedFrom(a, sid).length > 0 || copiedBy(a, sid).length > 0;
/** 抄袭的提供者不明时，提供者列表里放这个占位 id（不是任何同学），与安卓、iPad 一致 */
export const UNKNOWN_PROVIDER = '?';
export const unknownProvider = (a, sid) => copiedFrom(a, sid).includes(UNKNOWN_PROVIDER);
/** 这次作业里这位同学的抄袭关系：“抄袭 甲” / “抄袭（提供者不明）” / “提供给 乙 抄” */
export function plagiarismDesc(c, a, sid) {
  const names = (ids) => ids.map((id) => c.students.find((x) => x.id === id)?.name).filter(Boolean).join('、');
  const from = copiedFrom(a, sid).filter((x) => x !== UNKNOWN_PROVIDER); const by = copiedBy(a, sid);
  const copied = from.length ? `抄袭 ${names(from)}` : unknownProvider(a, sid) ? '抄袭（提供者不明）' : null;
  return [copied, by.length && `提供给 ${names(by)} 抄`].filter(Boolean).join('；');
}
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
  out.settings.rules.plagiarism = { ...defaultPlagiarism(), ...(out.settings.rules.plagiarism || {}) };
  out.settings.rules.leader = { ...defaultLeader(), ...(out.settings.rules.leader || {}) };
  out.settings.rules.groupRank = { ...defaultGroupRank(), ...(out.settings.rules.groupRank || {}) };
  out.settings.rules.credit = { ...defaultCredit(), ...(out.settings.rules.credit || {}) };
  out.settings.ai = { ...base.settings.ai, ...(out.settings.ai || {}) };
  out.classes = (out.classes || []).map((c) => ({
    id: newId(), emoji: '🎒', students: [], assignments: [], scores: [], ...c,
    students: (c.students || []).map((s) => ({ id: newId(), group: '', gender: '', note: '', longLeave: false, ...s })),
    assignments: (c.assignments || []).map((a) => ({ id: newId(), subject: '', createdAt: Date.now(), statuses: {}, comments: {}, countedBooks: null, pinned: false, reviewFailed: [], plagiarism: {}, ...a })),
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
 * 未交连续递增扣分（与安卓 Penalty.kt 同算法），三种方式（mode）：
 * 1 正常加倍：连续未交第 n 次扣 base^n（2、4、8…）；2 第 2 次起加倍：第 1、2 次扣 base，之后 base^(n-1)（2、2、4、8…）；
 * 3 分段倍率：在 1 或 2（tierBase）基础上按次数分段乘倍率（tiers），次数按连续（tierBy = 'streak'）或本轮累计（'total'）算；
 * 请假、未登记不打断也不计次，交过一次即重算；本轮累计超 heavyAfter 次后底数为 heavyBase；
 * 单次上限 cap 在乘完分段倍率后封顶；每次积分重置开始新一轮。
 */
/** 连续第 n 次未交的扣分（不含分段倍率和上限）：shape 1：b、b²、b³……；shape 2：b、b、b²、b³…… */
export const escalationAmount = (b, n, shape = 2) => (shape === 1 ? b ** Math.max(1, n) : b ** Math.max(1, Math.max(1, n) - 1));
/** 说明文字：“2、2、4、8…”（shape 1 时“2、4、8、16…”） */
export const escalationSeq = (b, shape = 2) => [1, 2, 3, 4].map((n) => fmtNum(escalationAmount(b, n, shape))).join('、') + '…';
export const escalationModeLabel = (mode) => (mode === 1 ? '正常加倍' : mode === 3 ? '分段倍率' : '第 2 次起加倍');
/** 实际使用的加倍方式（1 或 2） */
export const escalationShape = (e) => (e.mode === 1 ? 1 : e.mode === 3 ? (e.tierBase === 1 ? 1 : 2) : 2);
/** 第 n 次的分段倍率（不是分段方式时为 1） */
export function tierFactor(e, n) {
  if (e.mode !== 3) return 1;
  let best = null;
  for (const t of e.tiers || []) if (t.from <= n && (!best || t.from > best.from)) best = t;
  return best ? best.factor : 1;
}
/** 本轮第 total 次未交、连续第 streak 次时应扣的分（正数） */
export function escalationPenalty(e0, streak, total) {
  const e = { ...defaultEscalation(), ...(e0 || {}) };
  const b = total > e.heavyAfter ? e.heavyBase : e.base;
  const p = escalationAmount(b, streak, escalationShape(e)) * tierFactor(e, e.tierBy === 'total' ? total : streak);
  return e.cap > 0 ? Math.min(p, e.cap) : p;
}
/** 分段说明：“第 1–2 次 ×1、第 3–4 次 ×1.5、第 5 次起 ×2（按连续未交次数）” */
export function tiersText(e0) {
  const e = { ...defaultEscalation(), ...(e0 || {}) };
  const t = [...(e.tiers || [])].sort((x, y) => x.from - y.from);
  if (!t.length) return '未设分段（都 ×1）';
  const parts = [];
  if (t[0].from > 1) parts.push(`第 1${t[0].from > 2 ? `–${t[0].from - 1}` : ''} 次 ×1`);
  t.forEach((x, i) => {
    const to = i + 1 < t.length ? t[i + 1].from - 1 : null;
    parts.push(to == null ? `第 ${x.from} 次起 ×${fmtNum(x.factor)}` : to <= x.from ? `第 ${x.from} 次 ×${fmtNum(x.factor)}` : `第 ${x.from}–${to} 次 ×${fmtNum(x.factor)}`);
  });
  return parts.join('、') + `（按${e.tierBy === 'total' ? '本轮累计未交次数' : '连续未交次数'}）`;
}
/** 从第 1 次起连续未交依次扣的分（含分段倍率和上限） */
export function escalationSample(e0, b, k = 4) {
  const e = { ...defaultEscalation(), ...(e0 || {}) };
  const out = [];
  for (let n = 1; n <= k; n++) { const p = escalationAmount(b ?? e.base, n, escalationShape(e)) * tierFactor(e, n); out.push(fmtNum(e.cap > 0 ? Math.min(p, e.cap) : p)); }
  return out.join('、') + '…';
}
/** 一句话说明 */
export function escalationText(e0) {
  const e = { ...defaultEscalation(), ...(e0 || {}) };
  if (e.mode === 1) return `正常加倍：连续未交依次扣 ${escalationSeq(e.base, 1)}`;
  if (e.mode === 3) return `分段倍率（在${escalationModeLabel(escalationShape(e))}基础上）：${tiersText(e)}，依次扣 ${escalationSample(e)}`;
  return `第 2 次起加倍：连续未交依次扣 ${escalationSeq(e.base, 2)}`;
}

export function autoPoints(c, rules, sid) {
  const esc = { ...defaultEscalation(), ...(rules.escalation || {}) };
  const sb = { ...defaultStreakBonus(), ...(rules.streakBonus || {}) };
  const rv = { ...defaultReview(), ...(rules.review || {}) };
  const pg = { ...defaultPlagiarism(), ...(rules.plagiarism || {}) };
  const out = {};
  const resets = (c.recharges || []).filter((r) => (r.studentIds || []).includes(sid))
    .sort((a, b) => rechargeStart(a) - rechargeStart(b) || a.time - b.time || (a.id < b.id ? -1 : a.id > b.id ? 1 : 0));
  const list = [...c.assignments].sort((a, b) => a.createdAt - b.createdAt || (a.id < b.id ? -1 : a.id > b.id ? 1 : 0));
  let round; let streak = 0; let total = 0; let run = 0; let plag = 0;
  for (const a of list) {
    const s = a.statuses[sid] || 'NONE';
    let r = null; for (const x of resets) if (rechargeCounts(x, a.createdAt)) r = x.id;
    if (r !== round) { round = r; streak = 0; total = 0; run = 0; plag = 0; }
    if (s === 'MISSING') {
      streak++; total++; run = 0;
      let base;
      if (!esc.enabled) base = ruleOf(rules, s);
      else base = -escalationPenalty(esc, streak, total);
      // 抄袭与复审不合格可以叠加：两个倍数相乘
      const plagged = pg.enabled && plagiarized(a, sid); if (plagged) plag++;
      const f = plagged ? plagiarismFactor(pg, plag) : 1; const r = rv.enabled && failedReview(a, sid) ? rv.multiplier : 1;
      out[a.id] = pt(rules, base * f * r);
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

/** 当前有效的组长：组长须仍在该组 */
export function leaderOf(c, group) {
  const id = (c.groupLeaders || {})[group];
  return id ? c.students.find((s) => s.id === id && (s.group || '未分组') === group) || null : null;
}
const f1 = (x) => { const r = Math.round(x * 10) / 10; return Number.isInteger(r) ? String(r) : String(r); };
/** 某次作业所在的段：[key, 显示名] */
export function periodKey(lr, a) {
  if (lr.period === 'assignment') return [a.id, `「${a.title}」`];
  return periodKeyAt(lr.period, a.createdAt);
}
/** 某个时间所在的日 / 周 / 月 */
export function periodKeyAt(period, t) {
  const lr = { period };
  const d = new Date(t); d.setHours(0, 0, 0, 0);
  if (lr.period === 'day') return [`${d.getFullYear()}-${d.getMonth() + 1}-${d.getDate()}`, `${d.getMonth() + 1}月${d.getDate()}日`];
  if (lr.period === 'month') return [`${d.getFullYear()}-${d.getMonth() + 1}`, `${d.getFullYear()}年${d.getMonth() + 1}月`];
  d.setDate(d.getDate() - ((d.getDay() + 6) % 7));
  const key = `${d.getFullYear()}-${d.getMonth() + 1}-${d.getDate()}`; const m = d.getMonth() + 1; const day = d.getDate();
  d.setDate(d.getDate() + 6);
  return [key, `${m}/${day}–${d.getMonth() + 1}/${d.getDate()} 这周`];
}
/** 全班所有组长的每段奖惩：组长 id → [{ group, key, label, time, perCapita, average, parts:[[文字, 分]], points }] */
export function leaderPeriods(c, rules) {
  const lr = { ...defaultLeader(), ...(rules.leader || {}) };
  if (!lr.enabled) return {};
  const groups = {};
  for (const s of c.students) if (s.group) (groups[s.group] = groups[s.group] || []).push(s);
  for (const g of Object.keys(groups)) if (groups[g].length < 2) delete groups[g];
  const leaders = {};
  for (const g of Object.keys(groups)) { const l = leaderOf(c, g); if (l) leaders[g] = l; }
  if (!Object.keys(leaders).length) return {};
  const auto = {}; for (const ms of Object.values(groups)) for (const m of ms) auto[m.id] = autoPoints(c, rules, m.id);
  const sorted = [...c.assignments].sort((a, b) => a.createdAt - b.createdAt || (a.id < b.id ? -1 : a.id > b.id ? 1 : 0));
  const buckets = new Map();
  for (const a of sorted) { const [k, l] = periodKey(lr, a); if (!buckets.has(k)) buckets.set(k, { label: l, list: [] }); buckets.get(k).list.push(a); }
  const resetsOf = {};
  for (const l of Object.values(leaders)) resetsOf[l.id] = (c.recharges || []).filter((r) => (r.studentIds || []).includes(l.id)).sort((a, b) => rechargeStart(a) - rechargeStart(b) || a.time - b.time || (a.id < b.id ? -1 : a.id > b.id ? 1 : 0));
  const round = {}; const prevD = {}; const lastSub = {}; const run = {}; const out = {};
  for (const [key, { label, list }] of buckets) {
    const per = {};
    for (const [g, ms] of Object.entries(groups)) {
      const active = ms.some((m) => list.some((a) => { const k = a.statuses[m.id] || 'NONE'; return k !== 'NONE' && k !== 'LEAVE'; }));
      if (!active) continue;
      let total = 0; for (const m of ms) for (const a of list) { const p = auto[m.id][a.id] ?? 0; if (p < 0) total -= p; }
      per[g] = total / ms.length;
    }
    const vals = Object.values(per);
    if (!vals.length) continue;
    const avg = vals.reduce((x, y) => x + y, 0) / vals.length;
    const best = Math.min(...vals); const worst = Math.max(...vals);
    const first = list[0].createdAt; const last = list[list.length - 1].createdAt;
    for (const [g, d] of Object.entries(per)) {
      const leader = leaders[g]; if (!leader) continue;
      let r = null; for (const x of resetsOf[leader.id]) if (rechargeCounts(x, first)) r = x.id;
      if (!(g in round) || round[g] !== r) { round[g] = r; delete prevD[g]; delete lastSub[g]; delete run[g]; }
      const parts = [];
      if (d <= 1e-9) parts.push(['全组零扣分', lr.clean]);
      else if (d <= avg * 0.5) parts.push([`人均扣分 ${f1(d)}，不到各组平均 ${f1(avg)} 的一半`, lr.low]);
      else if (d <= avg) parts.push([`人均扣分 ${f1(d)}，不高于各组平均 ${f1(avg)}`, lr.below]);
      else if (d <= avg * 1.5) parts.push([`人均扣分 ${f1(d)}，高于各组平均 ${f1(avg)}`, lr.above]);
      else if (d <= avg * 2) parts.push([`人均扣分 ${f1(d)}，超过各组平均 1.5 倍`, lr.high]);
      else parts.push([`人均扣分 ${f1(d)}，超过各组平均 2 倍`, lr.veryHigh]);
      if (vals.length >= 3 && best < worst) {
        if (d === best && vals.filter((v) => v === best).length === 1) parts.push(['人均扣分各组最少', lr.top]);
        if (d === worst && vals.filter((v) => v === worst).length === 1) parts.push(['人均扣分各组最多', lr.bottom]);
      }
      if (g in prevD) { const p = prevD[g]; if (p > 0 && d <= p * 0.7) parts.push([`比上一段少（${f1(p)} → ${f1(d)}）`, lr.progress]); else if (d >= p * 1.5 && d - p >= 2) parts.push([`比上一段多（${f1(p)} → ${f1(d)}）`, lr.regress]); }
      const sub = parts.reduce((n, x) => n + x[1], 0);
      const ps = lastSub[g];
      const k = sub !== 0 && ps != null && ps !== 0 && (ps > 0) === (sub > 0) ? (run[g] || 1) : 0;
      if (lr.streakMax > 0 && k > 0) parts.push([sub > 0 ? `连续 ${k + 1} 段表现好` : `连续 ${k + 1} 段扣分偏多`, (sub > 0 ? 1 : -1) * Math.min(k, lr.streakMax) * lr.streakStep]);
      run[g] = k > 0 ? k + 1 : 1; lastSub[g] = sub; prevD[g] = d;
      const total = parts.reduce((n, x) => n + x[1], 0);
      if (lr.cap > 0 && Math.abs(total) > lr.cap) parts.push([`每段最多 ±${f1(lr.cap)}`, (total > 0 ? lr.cap : -lr.cap) - total]);
      const capped = parts.reduce((n, x) => n + x[1], 0);
      if (applyMode(lr.mode, capped) !== capped) parts.push([lr.mode === 'plus' ? '仅加分，不扣分' : '仅扣分，不加分', -capped]);
      (out[leader.id] = out[leader.id] || []).push({ group: g, key, label, time: last, perCapita: d, average: avg, parts, points: parts.reduce((n, x) => n + x[1], 0) });
    }
  }
  return out;
}

const HANDED_K = new Set(['SUBMITTED', 'EXCELLENT', 'LATE']);
/** 名次 → 每人加减分 */
export const rankPoints = (gr, rank, worst, n) => applyMode(gr.mode, n >= 3 && rank === worst && rank > 1 ? gr.last : rank === 1 ? gr.first : rank === 2 ? gr.second : rank === 3 ? gr.third : 0);
/** 全班每一段的小组排名（旧 → 新）：[{ key, label, time, standings: [{ group, rank, score, points, rate, deduct, excellent, exam }] }] */
export function groupRankPeriods(c, rules) {
  const gr = { ...defaultGroupRank(), ...(rules.groupRank || {}) };
  if (!gr.enabled) return [];
  const groups = {};
  for (const s of c.students) if (s.group) (groups[s.group] = groups[s.group] || []).push(s);
  for (const g of Object.keys(groups)) if (groups[g].length < 2) delete groups[g];
  if (Object.keys(groups).length < 2) return [];
  const auto = {}; for (const ms of Object.values(groups)) for (const m of ms) auto[m.id] = autoPoints(c, rules, m.id);
  const sorted = [...c.assignments].sort((a, b) => a.createdAt - b.createdAt || (a.id < b.id ? -1 : a.id > b.id ? 1 : 0));
  const buckets = new Map();
  for (const a of sorted) { const [k, l] = periodKey(gr, a); if (!buckets.has(k)) buckets.set(k, { label: l, list: [] }); buckets.get(k).list.push(a); }
  const examsBy = {};
  if (gr.period !== 'assignment') for (const e of c.exams || []) { const k = periodKeyAt(gr.period, e.date)[0]; (examsBy[k] = examsBy[k] || []).push(e); }
  const out = [];
  for (const [key, { label, list }] of buckets) {
    const raws = [];
    for (const [g, ms] of Object.entries(groups)) {
      const st = []; for (const a of list) for (const m of ms) { const k = a.statuses[m.id] || 'NONE'; if (k !== 'NONE' && k !== 'LEAVE') st.push(k); }
      if (!st.length) continue;
      let ded = 0; for (const m of ms) for (const a of list) { const p = auto[m.id][a.id] ?? 0; if (p < 0) ded -= p; }
      const ex = []; for (const e of examsBy[key] || []) for (const m of ms) { const v = e.scores?.[m.id]; if (v != null && Number.isFinite(v)) ex.push(v / (e.fullScore || 100)); }
      raws.push({ g, rate: st.filter((k) => HANDED_K.has(k)).length / st.length, deduct: ded / ms.length, exc: st.filter((k) => k === 'EXCELLENT').length / st.length, exam: ex.length ? ex.reduce((x, y) => x + y, 0) / ex.length : null });
    }
    if (raws.length < 2) continue;
    const maxD = Math.max(...raws.map((r) => r.deduct));
    const allExam = raws.every((r) => r.exam != null);
    const scored = raws.map((r) => {
      const dn = maxD <= 1e-9 ? 1 : 1 - r.deduct / maxD;
      let num = gr.wRate * r.rate + gr.wDeduct * dn + gr.wExcellent * r.exc; let den = gr.wRate + gr.wDeduct + gr.wExcellent;
      if (allExam) { num += gr.wExam * r.exam; den += gr.wExam; }
      return { r, score: Math.round((den <= 0 ? 0 : num / den) * 10000) / 10000 };
    });
    const rankOf = (sc) => 1 + scored.filter((x) => x.score > sc).length;
    const worst = Math.max(...scored.map((x) => rankOf(x.score)));
    const standings = scored.map(({ r, score }) => { const rank = rankOf(score); return { group: r.g, rank, score, points: rankPoints(gr, rank, worst, scored.length), rate: r.rate, deduct: r.deduct, excellent: r.exc, exam: r.exam }; })
      .sort((a, b) => a.rank - b.rank || (a.group < b.group ? -1 : a.group > b.group ? 1 : 0));
    out.push({ key, label, time: list[list.length - 1].createdAt, standings });
  }
  return out;
}
/** 每位同学的小组排名加减分：学生 id → [{ period, standing, points }] */
export function groupRankPoints(c, rules, periods = groupRankPeriods(c, rules)) {
  const byGroup = {}; for (const s of c.students) if (s.group) (byGroup[s.group] = byGroup[s.group] || []).push(s);
  const out = {};
  for (const p of periods) for (const st of p.standings) if (st.points) for (const m of byGroup[st.group] || []) (out[m.id] = out[m.id] || []).push({ period: p, standing: st, points: st.points });
  return out;
}

// ---------------- 信誉分（0–100，越优秀越高；与安卓 Credit.kt、iPad 同算法） ----------------
/**
 * 交齐 25%、按时 15%、优秀 10%（÷30%）、连续交 10%（÷5 次）、诚信 20%（最近 10 次复审不合格 / 抄袭每次 −0.5）、
 * 进步 5%（最近 5 次比之前 5 次，记录不足 8 次为 0.5）、考试 15%（最近 3 场平均得分率）；没有数据的项不算，权重按比例分配。
 * 等级：≥90 优秀，75–90 良好，60–75 一般，<60 较差。小组信誉分 = 组员平均（已命名、2 人以上的组）。
 */
export const r2 = (x) => Math.round(x * 100) / 100;
export const creditLevel = (x) => (x >= 90 ? '优秀' : x >= 75 ? '良好' : x >= 60 ? '一般' : '较差');
export const CREDIT_MODES = [['both', '加分和扣分'], ['plus', '只加不扣'], ['minus', '只扣不加'], ['none', '不计入']];
export const creditModeLabel = (m) => (CREDIT_MODES.find((x) => x[0] === m) || CREDIT_MODES[3])[1];
const HANDED_C = new Set(['SUBMITTED', 'EXCELLENT', 'LATE']);
const pctC = (x) => `${Math.round(x * 100)}%`;
const byNewest = (a, b) => b.createdAt - a.createdAt || (a.id < b.id ? 1 : a.id > b.id ? -1 : 0);
const byExamOld = (a, b) => a.date - b.date || (a.id < b.id ? -1 : a.id > b.id ? 1 : 0);
/** 一位同学的信誉分；newest 为截至某一刻的作业（新 → 旧），exams 为截至这一刻的考试（旧 → 新） */
export function creditOne(st, newest, exams) {
  const m = []; for (const a of newest) { const k = a.statuses[st.id] || 'NONE'; if (k !== 'NONE' && k !== 'LEAVE') m.push([a, k]); }
  const parts = [];
  if (m.length) {
    const m10 = m.slice(0, 10); const n = m10.length;
    const handed = m10.filter(([, k]) => HANDED_C.has(k)).length;
    const rate = handed / n;
    parts.push({ key: 'rate', label: '交齐', weight: 0.25, value: rate, detail: `最近 ${n} 次交了 ${handed} 次（${pctC(rate)}）` });
    const onTime = m10.filter(([, k]) => k === 'SUBMITTED' || k === 'EXCELLENT').length / n;
    parts.push({ key: 'ontime', label: '按时', weight: 0.15, value: onTime, detail: `按时 ${pctC(onTime)}，迟交 ${m10.filter(([, k]) => k === 'LATE').length} 次` });
    const excN = m10.filter(([, k]) => k === 'EXCELLENT').length; const exc = excN / n;
    parts.push({ key: 'excellent', label: '优秀', weight: 0.10, value: Math.min(1, exc / 0.3), detail: `优秀 ${excN} 次（${pctC(exc)}）` });
    let run = 0; for (const [, k] of m) { if (HANDED_C.has(k)) run++; else break; }
    parts.push({ key: 'streak', label: '连续交', weight: 0.10, value: Math.min(run, 5) / 5, detail: run === 0 ? '最近一次没交' : `已连续交 ${run} 次` });
    const bad = m10.filter(([a]) => failedReview(a, st.id) || plagiarized(a, st.id)).length;
    parts.push({ key: 'integrity', label: '诚信', weight: 0.20, value: Math.max(0, 1 - 0.5 * bad), detail: bad === 0 ? '最近没有复审不合格、抄袭' : `最近 ${n} 次里复审不合格 / 抄袭 ${bad} 次` });
    let trend = 0.5;
    if (m.length >= 8) {
      const a5 = m.slice(0, 5).filter(([, k]) => HANDED_C.has(k)).length / 5;
      const b = m.slice(5, 10); const b5 = b.filter(([, k]) => HANDED_C.has(k)).length / b.length;
      trend = Math.min(1, Math.max(0, 0.5 + (a5 - b5) / 0.4 * 0.5));
    }
    parts.push({ key: 'trend', label: '进步', weight: 0.05, value: trend, detail: m.length < 8 ? '记录不足 8 次' : trend > 0.5 ? '最近 5 次比之前好' : trend < 0.5 ? '最近 5 次比之前差' : '和之前持平' });
  }
  const taken = exams.filter((e) => (e.fullScore || 0) > 0 && e.scores?.[st.id] != null && Number.isFinite(e.scores[st.id])).slice(-3);
  if (taken.length) {
    const p = Math.min(1, Math.max(0, taken.reduce((x, e) => x + e.scores[st.id] / e.fullScore, 0) / taken.length));
    parts.push({ key: 'exam', label: '考试', weight: 0.15, value: p, detail: `最近 ${taken.length} 场平均得分率 ${pctC(p)}` });
  }
  if (!parts.length) return { student: st, index: 0, level: '暂无', parts, hasData: false };
  const wsum = parts.reduce((x, q) => x + q.weight, 0);
  let sum = 0; for (const q of parts) { q.points = q.weight / wsum * q.value * 100; sum += q.points; }
  const index = r2(sum);
  return { student: st, index, level: creditLevel(index), parts, hasData: true };
}
const creditCache = new WeakMap();
/** 全班在时刻 at 的个人信誉分（从高到低；没有数据的排在最后） */
export function creditAll(c, at = Date.now()) {
  const useCache = at >= Date.now() - 1000;
  if (useCache && creditCache.has(c)) return creditCache.get(c);
  const newest = c.assignments.filter((a) => a.createdAt <= at).sort(byNewest);
  const exams = (c.exams || []).filter((e) => e.date <= at).sort(byExamOld);
  const out = c.students.map((s) => creditOne(s, newest, exams)).sort((a, b) => (b.hasData - a.hasData) || b.index - a.index);
  if (useCache) creditCache.set(c, out);
  return out;
}
/** 已命名、2 人以上的小组 */
export function creditGroupsOf(c) {
  const g = {}; for (const s of c.students) if (s.group) (g[s.group] = g[s.group] || []).push(s);
  for (const k of Object.keys(g)) if (g[k].length < 2) delete g[k];
  return g;
}
/** 各组的小组信誉分（组内有信誉分的同学的平均） */
export function creditGroups(c, at = Date.now(), personal = creditAll(c, at)) {
  const by = Object.fromEntries(personal.map((r) => [r.student.id, r]));
  const out = {};
  for (const [g, ms] of Object.entries(creditGroupsOf(c))) {
    const v = ms.map((m) => by[m.id]).filter((r) => r && r.hasData).map((r) => r.index);
    if (v.length) out[g] = r2(v.reduce((x, y) => x + y, 0) / v.length);
  }
  return out;
}
export const creditBandPoints = (cr, x) => (x >= cr.excellentAt ? cr.excellentPts : x >= cr.goodAt ? cr.goodPts : x < cr.badBelow ? cr.badPts : x < cr.poorBelow ? cr.poorPts : 0);
export const applyCreditMode = (m, x) => (m === 'both' ? x : m === 'plus' ? Math.max(0, x) : m === 'minus' ? Math.min(0, x) : 0);
const fmtC = (x) => { const r = Math.round(x * 10) / 10; return Number.isInteger(r) ? String(r) : String(r); };
const signedC = (x) => (x > 0 ? '+' + fmtC(x) : fmtC(x));
export const creditBandsText = (cr) => `≥${fmtC(cr.excellentAt)} ${signedC(cr.excellentPts)}、≥${fmtC(cr.goodAt)} ${signedC(cr.goodPts)}、<${fmtC(cr.poorBelow)} ${signedC(cr.poorPts)}、<${fmtC(cr.badBelow)} ${signedC(cr.badPts)}`;
const creditPeriodCache = new WeakMap();
/** 全班每一段的信誉分加减分（旧 → 新）：[{ key, label, time, personal: {sid: [信誉分, 分]}, groups: {组: [信誉分, 每人分]} }] */
export function creditPeriods(c, rules) {
  const cr = { ...defaultCredit(), ...(rules.credit || {}) };
  if ((cr.personal === 'none' && cr.group === 'none') || !c.students.length) return [];
  let byRules = creditPeriodCache.get(c);
  if (!byRules) { byRules = new WeakMap(); creditPeriodCache.set(c, byRules); }
  if (byRules.has(rules)) return byRules.get(rules);
  const sorted = [...c.assignments].sort((a, b) => a.createdAt - b.createdAt || (a.id < b.id ? -1 : a.id > b.id ? 1 : 0));
  const buckets = new Map();
  for (const a of sorted) { const [k, l] = periodKey(cr, a); if (!buckets.has(k)) buckets.set(k, { label: l, list: [] }); buckets.get(k).list.push(a); }
  const groups = creditGroupsOf(c);
  const examsSorted = [...(c.exams || [])].sort(byExamOld);
  const out = [];
  for (const [key, { label, list }] of buckets) {
    const t = list[list.length - 1].createdAt;
    const active = new Set(c.students.filter((s) => list.some((a) => { const k = a.statuses[s.id] || 'NONE'; return k !== 'NONE' && k !== 'LEAVE'; })).map((s) => s.id));
    if (!active.size) continue;
    const newest = sorted.filter((a) => a.createdAt <= t).reverse();
    const ex = examsSorted.filter((e) => e.date <= t);
    const res = Object.fromEntries(c.students.map((s) => [s.id, creditOne(s, newest, ex)]));
    const personal = {};
    if (cr.personal !== 'none') for (const id of c.students.map((s) => s.id)) if (active.has(id) && res[id].hasData) personal[id] = [res[id].index, applyCreditMode(cr.personal, creditBandPoints(cr, res[id].index))];
    const gs = {};
    if (cr.group !== 'none') for (const [g, ms] of Object.entries(groups)) {
      if (!ms.some((m) => active.has(m.id))) continue;
      const vals = ms.map((m) => res[m.id]).filter((r) => r.hasData).map((r) => r.index);
      if (!vals.length) continue;
      const x = r2(vals.reduce((a, b) => a + b, 0) / vals.length);
      gs[g] = [x, applyCreditMode(cr.group, creditBandPoints(cr, x))];
    }
    out.push({ key, label, time: t, personal, groups: gs });
  }
  byRules.set(rules, out);
  return out;
}
/** 每位同学的信誉分加减分（只含不为 0 的）：学生 id → [{ period, group, credit, points }] */
export function creditPoints(c, rules, periods = creditPeriods(c, rules)) {
  const out = {};
  if (!periods.length) return out;
  const groups = creditGroupsOf(c);
  for (const p of periods) {
    for (const [id, [x, d]] of Object.entries(p.personal)) if (d) (out[id] = out[id] || []).push({ period: p, group: null, credit: x, points: d });
    for (const [g, [x, d]] of Object.entries(p.groups)) if (d) for (const m of groups[g] || []) (out[m.id] = out[m.id] || []).push({ period: p, group: g, credit: x, points: d });
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
  const lead = leaderPeriods(c, rules);
  const rank = groupRankPoints(c, rules);
  const cred = creditPoints(c, rules);
  const result = c.students.map((st) => {
    const r = last[st.id];
    const counts = Object.fromEntries(STATUS_KEYS.map((k) => [k, 0]));
    let auto = 0;
    const pts = autoPoints(c, rules, st.id);
    for (const a of c.assignments) { const s = a.statuses[st.id] || 'NONE'; counts[s]++; if (assignmentCounts(a, r)) auto += pts[a.id] ?? ruleOf(rules, s); }
    let examPts = 0;
    for (const e of c.exams || []) if (!r || rechargeCounts(r, e.date)) examPts += pt(rules, examDelta(rules, e, st.id));
    const leaderPts = (lead[st.id] || []).filter((p) => !r || rechargeCounts(r, p.time)).reduce((n, p) => n + pt(rules, p.points), 0)
      + (rank[st.id] || []).filter((x) => !r || rechargeCounts(r, x.period.time)).reduce((n, x) => n + pt(rules, x.points), 0)
      + (cred[st.id] || []).filter((x) => !r || rechargeCounts(r, x.period.time)).reduce((n, x) => n + pt(rules, x.points), 0);
    return { student: st, score: shown(rules, (r ? r.target : rules.initial) + auto + (manual[st.id] || 0) + examPts + leaderPts), counts, submitted: counts.SUBMITTED + counts.EXCELLENT, late: counts.LATE, missing: counts.MISSING, recharge: r || null };
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
