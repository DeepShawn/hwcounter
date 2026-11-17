// 统计分析（与安卓 Analytics.kt / Personal.kt 里的 Attention / AttentionStock.kt / GroupStock.kt / GroupAnalysis.kt 同一套算法）。
// 纯计算，不碰界面，便于测试：node --test tools/web-tests/analytics.test.mjs
import {
  summary, studentStats, autoPoints, ruleOf, examDelta, leaderOf, leaderPeriods, groupRankPeriods, latestRecharges,
  rechargeStart, rechargeCounts, startOfDay, fmtNum, signed, defaultGroupRank, copiedFrom, copiedBy, plagiarized, compareStudentNumbers,
  defaultPlagiarism, plagiarismFactor, UNKNOWN_PROVIDER, unknownProvider, creditAll, creditGroups,
} from './model.js';

const DAY = 86_400_000;
const HANDED = new Set(['SUBMITTED', 'EXCELLENT', 'LATE']);
const st = (a, sid) => a.statuses[sid] || 'NONE';
const avg = (l) => (l.length ? l.reduce((a, b) => a + b, 0) / l.length : NaN);
const sum = (l, f = (x) => x) => l.reduce((n, x) => n + f(x), 0);
const byAsc = (f) => (a, b) => { const x = f(a); const y = f(b); return x < y ? -1 : x > y ? 1 : 0; };
const byDesc = (f) => (a, b) => byAsc(f)(b, a);
const chain = (...cmps) => (a, b) => { for (const c of cmps) { const r = c(a, b); if (r) return r; } return 0; };
const clamp = (x, lo, hi) => Math.min(hi, Math.max(lo, x));
const pct = (x) => `${Math.round(x * 100)}%`;
const f1 = (x) => Math.abs(x).toFixed(1);
const f2 = (x) => x.toFixed(2);
const weekIdx = (t) => (new Date(t).getDay() + 6) % 7;
const WEEK_NAMES = ['周一', '周二', '周三', '周四', '周五', '周六', '周日'];
export const fmtDay = (t) => { const d = new Date(t); return `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日`; };
const mdCn = (t) => { const d = new Date(t); return `${d.getMonth() + 1}月${d.getDate()}日`; };
const md = (t) => { const d = new Date(t); return `${d.getMonth() + 1}/${d.getDate()}`; };
const addDays = (t, n) => { const d = new Date(t); d.setDate(d.getDate() + n); return d.getTime(); };
const addMonths = (t, n) => { const d = new Date(t); d.setMonth(d.getMonth() + n); return d.getTime(); };
const addYears = (t, n) => { const d = new Date(t); d.setFullYear(d.getFullYear() + n); return d.getTime(); };
const sortedAssignments = (c) => [...c.assignments].sort((a, b) => a.createdAt - b.createdAt);

export const RANGES = {
  ALL: { label: '全部' }, PERIOD: { label: '本轮' }, RECENT10: { label: '近 10 次' },
  YEAR: { label: '年', calendar: true }, QUARTER: { label: '季度', calendar: true }, MONTH: { label: '月', calendar: true },
  WEEK: { label: '周', calendar: true }, DAY: { label: '日', calendar: true }, CUSTOM: { label: '自定义' },
};

/** 只保留 t（含）之前发生的数据 */
export function asOf(c, t) {
  return {
    ...c,
    assignments: c.assignments.filter((a) => a.createdAt <= t),
    scores: c.scores.filter((e) => e.time <= t),
    exams: (c.exams || []).filter((e) => e.date <= t),
    recharges: (c.recharges || []).filter((r) => rechargeStart(r) <= t),
    settlements: (c.settlements || []).filter((x) => x.time <= t),
  };
}

/** 全班重置：覆盖至少一半同学的重置，按生效时间排序 */
export const classResets = (c) => (c.recharges || []).filter((r) => (r.studentIds || []).length * 2 >= Math.max(1, c.students.length))
  .sort((a, b) => rechargeStart(a) - rechargeStart(b) || a.time - b.time);
const startLabel = (r) => (r.effectiveAt == null ? '立即生效' : r.assignmentId ? `从「${r.assignmentTitle || '所选作业'}」起` : `从 ${fmtDay(r.effectiveAt)} 起`);

/** 日历周期 [起, 止) */
export function periodBounds(range, offset, now = Date.now()) {
  let a = startOfDay(now);
  const d = new Date(a);
  if (range === 'YEAR') { d.setMonth(0, 1); a = addYears(d.getTime(), offset); return [a, addYears(a, 1)]; }
  if (range === 'QUARTER') { d.setDate(1); d.setMonth(Math.floor(d.getMonth() / 3) * 3); a = addMonths(d.getTime(), 3 * offset); return [a, addMonths(a, 3)]; }
  if (range === 'MONTH') { d.setDate(1); a = addMonths(d.getTime(), offset); return [a, addMonths(a, 1)]; }
  if (range === 'WEEK') { a = addDays(a, -weekIdx(a) + 7 * offset); return [a, addDays(a, 7)]; }
  a = addDays(a, offset); return [a, addDays(a, 1)];
}
export function periodLabel(range, start, end, offset) {
  const d = new Date(start); const y = d.getFullYear(); const m = d.getMonth() + 1;
  const name = range === 'YEAR' ? `${y}年` : range === 'QUARTER' ? `${y}年第${Math.floor((m - 1) / 3) + 1}季度（${m}–${m + 2}月）` : range === 'MONTH' ? `${y}年${m}月`
    : range === 'WEEK' ? `${y}年${mdCn(start)} – ${mdCn(end - 1)}` : `${fmtDay(start)} ${WEEK_NAMES[weekIdx(start)]}`;
  const rel = offset === 0 ? { YEAR: '今年', QUARTER: '本季度', MONTH: '本月', WEEK: '本周', DAY: '今天' }[range] : offset === -1 ? { YEAR: '去年', QUARTER: '上季度', MONTH: '上月', WEEK: '上周', DAY: '昨天' }[range] : null;
  return rel ? `${name} · ${rel}` : name;
}

export function window(c, spec, now = Date.now()) {
  const sorted = sortedAssignments(c);
  const range = spec.range;
  const between = (a, b) => sorted.filter((x) => x.createdAt >= a && x.createdAt < b);
  const W = (o) => ({ range, previous: [], reset: null, start: null, end: null, prevLabel: '上一时段', ...o });
  if (range === 'ALL') return W({ assignments: sorted, label: `有记录以来全部 ${sorted.length} 次作业` });
  if (range === 'RECENT10') { const cur = sorted.slice(-10); return W({ assignments: cur, previous: sorted.slice(0, Math.max(0, sorted.length - 10)).slice(-10), start: cur[0]?.createdAt ?? null, label: `最近 ${Math.min(10, sorted.length)} 次作业`, prevLabel: '再往前 10 次' }); }
  if (range === 'PERIOD') {
    const resets = classResets(c); const r = resets[resets.length - 1];
    if (!r) return W({ assignments: sorted, label: '还没有全班重置，显示全部作业' });
    const pr = resets[resets.length - 2];
    const cur = sorted.filter((x) => rechargeCounts(r, x.createdAt));
    const prev = sorted.filter((x) => !rechargeCounts(r, x.createdAt) && (!pr || rechargeCounts(pr, x.createdAt)));
    return W({ assignments: cur, previous: prev, reset: r, start: rechargeStart(r), label: `本轮：${startLabel(r)}（${fmtDay(r.time)} 重置为 ${fmtNum(r.target)} 分）· ${cur.length} 次作业`, prevLabel: '上一轮' });
  }
  if (range === 'CUSTOM') {
    const a = startOfDay(spec.from ?? sorted[0]?.createdAt ?? now);
    const b = addDays(startOfDay(Math.max(spec.to ?? now, a)), 1);
    let days = 0; for (let t = a; t < b && days < 100000; t = addDays(t, 1)) days++;
    const cur = between(a, b);
    return W({ assignments: cur, previous: between(addDays(a, -days), a), start: a, end: b, label: `${fmtDay(a)} – ${fmtDay(b - 1)}（${days} 天）· ${cur.length} 次作业`, prevLabel: `前 ${days} 天` });
  }
  const [a, b] = periodBounds(range, spec.offset || 0, now);
  const [pa] = periodBounds(range, (spec.offset || 0) - 1, now);
  const cur = between(a, b);
  return W({ assignments: cur, previous: between(pa, a), start: a, end: b, label: `${periodLabel(range, a, b, spec.offset || 0)} · ${cur.length} 次作业`,
    prevLabel: { YEAR: '上一年', QUARTER: '上一季度', MONTH: '上一月', WEEK: '上一周', DAY: '前一天' }[range] || '上一时段' });
}

// ---------------- 作业指标 ----------------

export function kpi(c, list) {
  let expected = 0; let handed = 0; let onTime = 0; let excellent = 0; let missing = 0; let late = 0; let leave = 0; let none = 0; let perfect = 0;
  for (const a of list) {
    const s = summary(c, a);
    expected += s.expected; handed += s.handedIn; onTime += s.counts.SUBMITTED + s.counts.EXCELLENT; excellent += s.counts.EXCELLENT;
    missing += s.counts.MISSING; late += s.counts.LATE; leave += s.counts.LEAVE; none += s.counts.NONE;
    if (s.expected > 0 && s.handedIn >= s.expected) perfect++;
  }
  const r = (x) => (expected === 0 ? 0 : x / expected);
  return { assignments: list.length, handInRate: r(handed), onTimeRate: r(onTime), excellentRate: r(excellent), missing, late, leave, unregistered: none, perfect, expected, missingRate: r(missing), lateRate: r(late) };
}

/** 某一时刻（含）之前已发生的数据算出的积分 */
export function scoresAt(c, rules, t) {
  const x = { ...c, assignments: c.assignments.filter((a) => a.createdAt <= t), scores: c.scores.filter((e) => e.time <= t), exams: (c.exams || []).filter((e) => e.date <= t), recharges: (c.recharges || []).filter((r) => rechargeStart(r) <= t) };
  return Object.fromEntries(studentStats(x, rules).map((s) => [s.student.id, s.score]));
}

export function slope(ys) {
  const n = ys.length; if (n < 3) return 0;
  const mx = (n - 1) / 2; const my = avg(ys);
  let num = 0; let den = 0;
  ys.forEach((y, i) => { num += (i - mx) * (y - my); den += (i - mx) * (i - mx); });
  return den === 0 ? 0 : num / den;
}
export function pearson(xs, ys) {
  const n = Math.min(xs.length, ys.length); if (n < 4) return null;
  const mx = avg(xs.slice(0, n)); const my = avg(ys.slice(0, n));
  let sxy = 0; let sxx = 0; let syy = 0;
  for (let i = 0; i < n; i++) { const dx = xs[i] - mx; const dy = ys[i] - my; sxy += dx * dy; sxx += dx * dx; syy += dy * dy; }
  return sxx === 0 || syy === 0 ? null : sxy / Math.sqrt(sxx * syy);
}

export function insight(s, list) {
  let expected = 0; let handed = 0; let missing = 0; let late = 0; let onTime = 0; let excellent = 0; let leave = 0; let none = 0; let longest = 0; let cur = 0;
  const marks = [];
  for (const a of list) {
    switch (st(a, s.id)) {
      case 'SUBMITTED': expected++; handed++; onTime++; marks.push(1); cur = 0; break;
      case 'EXCELLENT': expected++; handed++; onTime++; excellent++; marks.push(1); cur = 0; break;
      case 'LATE': expected++; handed++; late++; marks.push(1); cur = 0; break;
      case 'MISSING': expected++; missing++; marks.push(0); cur++; longest = Math.max(longest, cur); break;
      case 'LEAVE': leave++; break;
      default: expected++; none++;
    }
  }
  let streak = 0; for (let i = marks.length - 1; i >= 0 && marks[i] === 0; i--) streak++;
  let hand = 0; for (let i = marks.length - 1; i >= 0 && marks[i] === 1; i--) hand++;
  const half = Math.floor(marks.length / 2);
  const trend = half < 2 ? 0 : avg(marks.slice(marks.length - half)) - avg(marks.slice(0, half));
  return { student: s, expected, handedIn: handed, missing, late, missingStreak: streak, trend, onTime, excellent, leave, unregistered: none, longestMissing: longest, handStreak: hand,
    rate: expected === 0 ? 1 : handed / expected, onTimeRate: expected === 0 ? 1 : onTime / expected, score: 0, scoreDelta: 0, rank: 0, examPct: null };
}

export function report(c, rules, spec, now = Date.now()) {
  const range = spec.range;
  const w = window(c, spec, now);
  const endT = w.end != null ? Math.min(w.end - 1, now) : null;
  const list = w.assignments;
  const points = list.map((a) => ({ assignment: a, summary: summary(c, a) }));
  const totals = {};
  for (const p of points) for (const [k, v] of Object.entries(p.summary.counts)) totals[k] = (totals[k] || 0) + v;
  const byDay = Array.from({ length: 7 }, () => []);
  for (const p of points) byDay[weekIdx(p.assignment.createdAt)].push(p.summary.rate);
  const weekday = byDay.map((l) => (l.length ? avg(l) : null));
  const subjMap = new Map();
  for (const p of points) { const k = p.assignment.subject || '其他'; if (!subjMap.has(k)) subjMap.set(k, []); subjMap.get(k).push(p); }
  const subjects = [...subjMap].map(([k, l]) => {
    const exp = sum(l, (p) => p.summary.expected);
    return { subject: k, count: l.length, rate: avg(l.map((p) => p.summary.rate)), missing: sum(l, (p) => p.summary.counts.MISSING), onTimeRate: exp === 0 ? 0 : sum(l, (p) => p.summary.counts.SUBMITTED + p.summary.counts.EXCELLENT) / exp };
  }).sort(byDesc((x) => x.rate));

  const stats = studentStats(c, rules);
  const nowScore = endT != null && endT < now ? scoresAt(c, rules, endT) : Object.fromEntries(stats.map((s) => [s.student.id, s.score]));
  const startScore = w.reset ? Object.fromEntries(c.students.map((s) => [s.id, w.reset.target])) : w.start != null ? scoresAt(c, rules, w.start - 1) : Object.fromEntries(c.students.map((s) => [s.id, rules.initial]));
  const rankOf = Object.fromEntries([...stats].sort(byDesc((s) => s.score)).map((s, i) => [s.student.id, i + 1]));
  const examsIn = (c.exams || []).filter((e) => (w.start == null || e.date >= w.start) && (w.end == null || e.date < w.end)).sort((a, b) => a.date - b.date);
  const examPctOf = {};
  for (const s of c.students) { const v = examsIn.map((e) => { const x = e.scores?.[s.id]; return x != null && e.fullScore > 0 ? x / e.fullScore : null; }).filter((x) => x != null); if (v.length) examPctOf[s.id] = avg(v); }

  const students = c.students.map((s) => ({ ...insight(s, list), score: nowScore[s.id] ?? 0, scoreDelta: (nowScore[s.id] ?? 0) - (startScore[s.id] ?? rules.initial), rank: rankOf[s.id] || 0, examPct: examPctOf[s.id] ?? null }));
  const attention = students.filter((x) => x.missing > 0 || x.missingStreak >= 2).sort(chain(byDesc((x) => x.missingStreak), byDesc((x) => x.missing), byDesc((x) => x.late))).slice(0, 8);
  const improved = students.filter((x) => x.trend >= 0.2 && x.expected >= 4).sort(byDesc((x) => x.trend)).slice(0, 5);
  const perfect = students.filter((x) => x.expected > 0 && x.missing === 0 && x.late === 0);

  const scores = c.students.map((s) => nowScore[s.id] ?? rules.initial);
  let scoreBins = [];
  if (scores.length) {
    const lo = Math.floor(Math.min(...scores)); const hi = Math.floor(Math.max(...scores));
    const step = Math.max(1, Math.trunc((hi - lo + 7) / 7));
    for (let b = lo; b <= hi; b += step) scoreBins.push([step === 1 ? `${b}` : `${b}~${b + step - 1}`, scores.filter((x) => x >= b && x < b + step).length]);
  }
  const byId = Object.fromEntries(students.map((x) => [x.student.id, x]));
  const gmap = new Map();
  for (const s of c.students) { const g = s.group || '未分组'; if (!gmap.has(g)) gmap.set(g, []); gmap.get(g).push(s); }
  const groupStats = [...gmap].map(([g, members]) => {
    const ins = members.map((m) => byId[m.id]).filter(Boolean);
    const exp = sum(ins, (x) => x.expected); const ex = ins.map((x) => x.examPct).filter((x) => x != null);
    return { name: g, leader: leaderOf(c, g), size: members.length, rate: exp === 0 ? 1 : sum(ins, (x) => x.handedIn) / exp, missing: sum(ins, (x) => x.missing), avgScore: avg(ins.map((x) => x.score)), scoreDelta: avg(ins.map((x) => x.scoreDelta)), examPct: ex.length ? avg(ex) : null };
  }).sort(byDesc((x) => x.rate));
  const rateBins = [['<60%', 0, 0.6], ['60–70%', 0.6, 0.7], ['70–80%', 0.7, 0.8], ['80–90%', 0.8, 0.9], ['90–99%', 0.9, 1], ['100%', 1, 1.01]]
    .map(([l, a, b]) => [l, students.filter((x) => x.expected > 0 && x.rate >= a && x.rate < b).length]);

  const trendPts = points.length <= 10 ? points : [...new Set(Array.from({ length: 10 }, (_, k) => points[Math.trunc(k * (points.length - 1) / 9)]))];
  const scoreTrend = trendPts.length < 2 ? [] : trendPts.map((p) => [p.assignment, avg(Object.values(scoresAt(c, rules, p.assignment.createdAt)))]);
  const movers = students.filter((x) => Math.abs(x.scoreDelta) > 1e-9);
  const risers = movers.filter((x) => x.scoreDelta > 0).sort(byDesc((x) => x.scoreDelta)).slice(0, 5);
  const fallers = movers.filter((x) => x.scoreDelta < 0).sort(byAsc((x) => x.scoreDelta)).slice(0, 5);
  const unregistered = points.filter((p) => p.summary.counts.NONE > 0).map((p) => [p.assignment, p.summary.counts.NONE]).sort(byDesc((x) => x[0].createdAt));
  let run = 0; let best = 0;
  for (const p of points) if (p.summary.expected > 0 && p.summary.handedIn >= p.summary.expected) { run++; best = Math.max(best, run); } else run = 0;
  const corrPairs = students.filter((x) => x.expected >= 3 && x.examPct != null);
  const corr = pearson(corrPairs.map((x) => x.rate), corrPairs.map((x) => x.examPct));
  const k = kpi(c, list);
  const prev = w.previous.length ? kpi(c, w.previous) : null;
  const [bks, unit] = buckets(points, range);
  const withData = students.filter((x) => x.expected > 0);
  const rates = withData.map((x) => x.rate).sort((a, b) => a - b);
  const median = !rates.length ? 0 : rates.length % 2 ? rates[(rates.length - 1) / 2] : (rates[rates.length / 2 - 1] + rates[rates.length / 2]) / 2;
  const mean = rates.length ? avg(rates) : 0;
  const std = rates.length < 2 ? 0 : Math.sqrt(sum(rates, (x) => (x - mean) ** 2) / rates.length);
  const r = {
    range, spec, window: w, points, kpi: k, previous: prev, totals, weekday, subjects, students, attention, improved, perfectAttendance: perfect, scoreBins,
    groupStats, rateBins, scoreTrend, risers, fallers, unregistered, slope: slope(points.map((p) => p.summary.rate)), longestPerfectRun: best,
    avgScore: scores.length ? avg(scores) : 0, avgScoreDelta: students.length ? avg(students.map((x) => x.scoreDelta)) : 0, examCorrelation: corr,
    buckets: bks, bucketUnit: unit, sources: sources(c, rules, list, w, examsIn), reasons: reasons(c, w), tiers: tiers(withData),
    streakers: withData.filter((x) => x.handStreak >= 3).sort(byDesc((x) => x.handStreak)).slice(0, 8),
    risk: risk(c), weakSubjects: weakSubjects(c, list, byId), rateMedian: median, rateStd: std, rounds: rounds(c), progress: progress(c, rules), examCount: examsIn.length,
  };
  r.insights = insights(r, corrPairs);
  return r;
}

function buckets(points, range) {
  if (points.length < 2 || range === 'DAY') return [[], ''];
  const first = points[0].assignment.createdAt; const last = points[points.length - 1].assignment.createdAt;
  const span = Math.trunc((last - first) / DAY);
  const unit = range === 'WEEK' ? '日' : range === 'MONTH' || range === 'QUARTER' ? '周' : range === 'YEAR' ? '月' : span <= 14 ? '日' : span <= 120 ? '周' : '月';
  const keyOf = (t) => { let d = startOfDay(t); if (unit === '周') d = addDays(d, -weekIdx(d)); else if (unit === '月') { const x = new Date(d); x.setDate(1); d = x.getTime(); } return d; };
  const m = new Map();
  for (const p of points) { const k = keyOf(p.assignment.createdAt); if (!m.has(k)) m.set(k, []); m.get(k).push(p); }
  const out = [...m].sort((a, b) => a[0] - b[0]).map(([k, l]) => {
    const exp = sum(l, (p) => p.summary.expected); const d = new Date(k);
    const label = unit === '月' ? (span > 330 ? `${String(d.getFullYear()).slice(-2)}年${d.getMonth() + 1}月` : `${d.getMonth() + 1}月`) : unit === '周' ? `${md(k)}周` : md(k);
    return { label, start: k, count: l.length, rate: exp === 0 ? 1 : sum(l, (p) => p.summary.handedIn) / exp, onTimeRate: exp === 0 ? 1 : sum(l, (p) => p.summary.counts.SUBMITTED + p.summary.counts.EXCELLENT) / exp, missing: sum(l, (p) => p.summary.counts.MISSING) };
  });
  return [out.length >= 2 ? out : [], unit];
}
const inWindow = (w, t) => (w.start == null || t >= w.start) && (w.end == null || t < w.end);
function sources(c, rules, list, w, exams) {
  let hw = 0;
  const all = Object.fromEntries(c.students.map((s) => [s.id, autoPoints(c, rules, s.id)]));
  for (const a of list) for (const s of c.students) hw += all[s.id]?.[a.id] ?? ruleOf(rules, st(a, s.id));
  const manual = c.scores.filter((e) => inWindow(w, e.time));
  const ex = sum(exams, (e) => sum(c.students, (s) => examDelta(rules, e, s.id)));
  return { homework: hw, manualPlus: sum(manual.filter((e) => e.delta > 0), (e) => e.delta), manualMinus: sum(manual.filter((e) => e.delta < 0), (e) => e.delta), exam: ex, manualCount: manual.length };
}
function reasons(c, w) {
  const m = new Map();
  for (const e of c.scores.filter((x) => inWindow(w, x.time))) { const k = (e.reason || '').trim() || (e.delta > 0 ? '加分' : '扣分'); if (!m.has(k)) m.set(k, []); m.get(k).push(e); }
  return [...m].map(([reason, l]) => ({ reason, count: l.length, total: sum(l, (e) => e.delta), people: new Set(l.map((e) => e.studentId)).size }))
    .sort(chain(byDesc((x) => x.count), byDesc((x) => Math.abs(x.total)))).slice(0, 10);
}
export function tiers(list) {
  const perfect = list.filter((x) => x.missing === 0 && x.late === 0 && x.unregistered === 0);
  const ps = new Set(perfect); const rest = list.filter((x) => !ps.has(x));
  return [
    { name: '全勤', desc: '没有缺交和迟交', level: 'GOOD', students: perfect },
    { name: '稳定', desc: '交齐率 ≥ 90%', level: 'GOOD', students: rest.filter((x) => x.rate >= 0.9 && x.missingStreak < 2).sort(byDesc((x) => x.rate)) },
    { name: '波动', desc: '交齐率 70%–90%', level: 'INFO', students: rest.filter((x) => x.rate >= 0.7 && x.rate < 0.9 && x.missingStreak < 2).sort(byDesc((x) => x.rate)) },
    { name: '需关注', desc: '交齐率 < 70% 或连续未交', level: 'WARN', students: rest.filter((x) => x.rate < 0.7 || x.missingStreak >= 2).sort(byAsc((x) => x.rate)) },
  ];
}
export function risk(c) {
  const sorted = [...c.assignments].sort((a, b) => b.createdAt - a.createdAt);
  return c.students.map((s) => {
    const marks = sorted.map((a) => ({ MISSING: 1, LATE: 0.4, SUBMITTED: 0, EXCELLENT: 0 })[st(a, s.id)]).filter((x) => x != null).slice(0, 8);
    if (marks.length < 3) return null;
    let num = 0; let den = 0; let wt = 1;
    for (const m of marks) { num += wt * m; den += wt; wt *= 0.75; }
    const p = num / den; const recent = marks.slice(0, 3).filter((x) => x === 1).length;
    return p < 0.3 ? null : { student: s, prob: p, why: `近 ${marks.length} 次缺交 ${marks.filter((x) => x === 1).length} 次、迟交 ${marks.filter((x) => x === 0.4).length} 次${recent > 0 ? `，最近 3 次缺 ${recent} 次` : ''}` };
  }).filter(Boolean).sort(byDesc((x) => x.prob)).slice(0, 8);
}
function weakSubjects(c, list, byId) {
  const m = new Map();
  for (const a of list) { const k = a.subject || '其他'; if (!m.has(k)) m.set(k, []); m.get(k).push(a); }
  const bySubject = [...m].filter(([, l]) => l.length >= 2);
  if (bySubject.length < 2) return [];
  return c.students.map((s) => {
    const overall = byId[s.id]; if (!overall || overall.expected < 4) return null;
    const cand = bySubject.map(([sub, l]) => [sub, insight(s, l)]).filter(([, x]) => x.expected >= 2);
    if (!cand.length) return null;
    const worst = cand.reduce((a, b) => (b[1].rate < a[1].rate ? b : a));
    return overall.rate - worst[1].rate < 0.2 ? null : { student: s, subject: worst[0], subjectRate: worst[1].rate, overallRate: overall.rate, count: worst[1].expected };
  }).filter(Boolean).sort(byAsc((x) => x.subjectRate - x.overallRate)).slice(0, 8);
}
export function rounds(c) {
  const resets = classResets(c); if (!resets.length) return [];
  const sorted = sortedAssignments(c); const out = [];
  for (let i = 0; i <= resets.length; i++) {
    const from = resets[i - 1]; const to = resets[i];
    const inside = (t) => (!from || rechargeCounts(from, t)) && (!to || !rechargeCounts(to, t));
    const l = sorted.filter((a) => inside(a.createdAt));
    if (!l.length && to) continue;
    const pcts = (c.exams || []).filter((e) => inside(e.date)).flatMap((e) => Object.values(e.scores || {}).map((v) => v / e.fullScore));
    out.push({ index: i, label: !from ? '首次重置前' : `第 ${i} 次重置后${!to ? '（本轮）' : ''}`, start: from ? rechargeStart(from) : null, end: to ? rechargeStart(to) : null, kpi: kpi(c, l), examPct: pcts.length ? avg(pcts) : null, reset: from || null });
  }
  return out;
}
function side(c, rules, s, list, exams) {
  const ins = insight(s, list); const auto = autoPoints(c, rules, s.id);
  const pts = list.map((a) => { const k = st(a, s.id); return k === 'NONE' || k === 'LEAVE' ? null : auto[a.id] ?? ruleOf(rules, k); }).filter((x) => x != null);
  const ex = exams.map((e) => { const v = e.scores?.[s.id]; return v == null ? null : v / e.fullScore; }).filter((x) => x != null);
  const n = ins.expected - ins.unregistered;
  return { expected: n, rate: n === 0 ? 1 : ins.handedIn / n, onTimeRate: n === 0 ? 1 : ins.onTime / n, excellentRate: n === 0 ? 0 : ins.excellent / n, missing: ins.missing, examPct: ex.length ? avg(ex) : null, pace: pts.length ? avg(pts) : 0 };
}
export function progress(c, rules) {
  const sorted = sortedAssignments(c);
  if (sorted.length < 4 || !c.students.length) return null;
  const resets = classResets(c);
  const r = [...resets].reverse().find((rr) => sorted.filter((a) => rechargeCounts(rr, a.createdAt)).length >= 2 && sorted.filter((a) => !rechargeCounts(rr, a.createdAt)).length >= 2);
  let before; let after; let basis;
  if (r) {
    before = sorted.filter((a) => !rechargeCounts(r, a.createdAt)); after = sorted.filter((a) => rechargeCounts(r, a.createdAt));
    const at = r.assignmentId ? `「${r.assignmentTitle || '所选作业'}」` : fmtDay(rechargeStart(r));
    basis = `重置前（${at} 之前）vs 重置后（${at} 起）`;
  } else { const h = Math.floor(sorted.length / 2); before = sorted.slice(0, h); after = sorted.slice(h); basis = `还没有全班重置：前 ${h} 次 vs 后 ${sorted.length - h} 次`; }
  const cut = after[0].createdAt;
  const exB = (c.exams || []).filter((e) => e.date < cut); const exA = (c.exams || []).filter((e) => e.date >= cut);
  const p = (x) => `${Math.round(x * 100)}%`;
  const all = c.students.map((s) => {
    const b = side(c, rules, s, before, exB); const a = side(c, rules, s, after, exA);
    if (b.expected < 2 || a.expected < 2) return null;
    const dr = a.rate - b.rate; const dt = a.onTimeRate - b.onTimeRate; const de = a.excellentRate - b.excellentRate;
    const dx = a.examPct != null && b.examPct != null ? a.examPct - b.examPct : null;
    const idx = dx != null ? dr * 0.45 + dt * 0.2 + de * 0.15 + dx * 0.2 : (dr * 0.45 + dt * 0.2 + de * 0.15) / 0.8;
    const why = [];
    if (Math.abs(dr) >= 0.05) why.push([Math.abs(dr), `交齐率 ${p(b.rate)}→${p(a.rate)}`]);
    if (Math.abs(dt) >= 0.08) why.push([Math.abs(dt) * 0.8, `按时率 ${p(b.onTimeRate)}→${p(a.onTimeRate)}`]);
    if (Math.abs(de) >= 0.08) why.push([Math.abs(de) * 0.7, `优秀率 ${p(b.excellentRate)}→${p(a.excellentRate)}`]);
    if (dx != null && Math.abs(dx) >= 0.04) why.push([Math.abs(dx), `考试得分率 ${p(b.examPct)}→${p(a.examPct)}`]);
    if (Math.abs(a.pace - b.pace) >= 0.3) why.push([0.01, `每次作业平均得分 ${fmtNum(b.pace)}→${fmtNum(a.pace)}`]);
    return { student: s, before: b, after: a, index: idx, reasons: why.sort(byDesc((x) => x[0])).map((x) => x[1]) };
  }).filter(Boolean);
  return { basis, reset: r || null, all,
    stars: all.filter((x) => x.index >= 0.05).sort(byDesc((x) => x.index)).slice(0, 8),
    watch: all.filter((x) => x.index <= -0.05 || x.after.rate - x.before.rate <= -0.15).sort(byAsc((x) => x.index)).slice(0, 8) };
}

const names = (l, n = 6) => l.slice(0, n).map((x) => x.student.name).join('、') + (l.length > n ? ` 等 ${l.length} 人` : '');
function insights(r, corrPairs) {
  const out = []; const k = r.kpi; const I = (level, text) => out.push({ level, text });
  if (!r.points.length) return out;
  const per = r.slope * 100;
  if (r.points.length >= 4 && Math.abs(per) >= 0.8) I(per > 0 ? 'GOOD' : 'WARN', `交齐率呈${per > 0 ? '上升' : '下降'}趋势，平均每次${per > 0 ? '提高' : '下降'} ${f1(per)} 个百分点。`);
  if (r.previous) {
    const p = r.previous; const d = Math.round((k.handInRate - p.handInRate) * 100);
    if (Math.abs(d) >= 2) I(d > 0 ? 'GOOD' : 'WARN', `与${r.window.prevLabel}相比交齐率${d > 0 ? '提高' : '下降'} ${Math.abs(d)} 个百分点（${pct(p.handInRate)} → ${pct(k.handInRate)}）。`);
    const m = Math.round((k.missingRate - p.missingRate) * 100);
    if (Math.abs(m) >= 2) I(m < 0 ? 'GOOD' : 'WARN', `缺交率${m < 0 ? '降低' : '升高'} ${Math.abs(m)} 个百分点。`);
  }
  if (r.longestPerfectRun >= 2) I('GOOD', `最长连续 ${r.longestPerfectRun} 次全员交齐。`);
  if (k.lateRate >= 0.08) I('WARN', `迟交占应交人次的 ${pct(k.lateRate)}，按时率只有 ${pct(k.onTimeRate)}，可以强调截止时间。`);
  if (r.subjects.length >= 2) { const b = r.subjects[0]; const ws = r.subjects[r.subjects.length - 1]; if (b.rate - ws.rate >= 0.05) I('INFO', `${ws.subject}交齐率最低（${pct(ws.rate)}），比${b.subject}低 ${Math.round((b.rate - ws.rate) * 100)} 个百分点。`); }
  const days = ['一', '二', '三', '四', '五', '六', '日'];
  const wd = r.weekday.map((v, i) => (v == null ? null : [i, v])).filter(Boolean);
  if (wd.length >= 3) { const lo = wd.reduce((a, b) => (b[1] < a[1] ? b : a)); const hi = wd.reduce((a, b) => (b[1] > a[1] ? b : a)); if (hi[1] - lo[1] >= 0.05) I('INFO', `周${days[lo[0]]}布置的作业交得最差（${pct(lo[1])}），周${days[hi[0]]}最好（${pct(hi[1])}）。`); }
  const streak = r.students.filter((x) => x.missingStreak >= 2).sort(byDesc((x) => x.missingStreak));
  if (streak.length) I('WARN', `${names(streak)} 已连续 2 次及以上未交，建议尽快沟通。`);
  const low = r.students.filter((x) => x.expected >= 3 && x.rate < 0.7).sort(byAsc((x) => x.rate));
  if (low.length) I('WARN', `${low.length} 人交齐率低于 70%：${names(low)}。`);
  const lates = r.students.filter((x) => x.late >= 3).sort(byDesc((x) => x.late));
  if (lates.length) I('INFO', `${names(lates)} 经常迟交（≥3 次）。`);
  const tag = (x) => x.student.name + (x.reasons[0] ? `（${x.reasons[0]}）` : '');
  if (r.progress?.stars.length) I('GOOD', `进步之星（重置后比以往更好）：${r.progress.stars.slice(0, 5).map(tag).join('、')}。`);
  if (r.progress?.watch.length) I('WARN', `退步关注（重置后不如以往）：${r.progress.watch.slice(0, 5).map(tag).join('、')}。`);
  if (r.risk.length) I('WARN', `下次缺交风险较高：${r.risk.slice(0, 5).map((x) => `${x.student.name}（${pct(x.prob)}）`).join('、')}，可以提前提醒。`);
  if (r.weakSubjects.length) I('INFO', `偏科：${r.weakSubjects.slice(0, 4).map((x) => `${x.student.name}的${x.subject}交齐率 ${pct(x.subjectRate)}（整体 ${pct(x.overallRate)}）`).join('；')}。`);
  if (r.rateStd >= 0.15 && r.students.length >= 6) I('INFO', `交齐率两极分化：中位数 ${pct(r.rateMedian)}，标准差 ${Math.round(r.rateStd * 100)} 个百分点，少数同学拉低了整体。`);
  const s0 = r.streakers[0];
  if (s0 && s0.handStreak >= 5) I('GOOD', `${names(r.streakers.filter((x) => x.handStreak >= 5), 4)} 已连续 ${s0.handStreak} 次及以上按要求交齐。`);
  if (r.perfectAttendance.length) I('GOOD', `${r.perfectAttendance.length} 人无缺交、无迟交。`);
  const gs = r.groupStats.filter((g) => g.name !== '未分组' && g.size >= 2);
  if (gs.length >= 2) {
    const b = gs.reduce((a, x) => (x.rate > a.rate ? x : a)); const ws = gs.reduce((a, x) => (x.rate < a.rate ? x : a));
    if (b.rate - ws.rate >= 0.08) I('INFO', `小组差距：${b.name}交齐率 ${pct(b.rate)}，${ws.name}只有 ${pct(ws.rate)}${ws.leader ? `（组长 ${ws.leader.name}）` : ''}。`);
  }
  if (r.range !== 'ALL' && Math.abs(r.avgScoreDelta) >= 0.5) I('INFO', `本时段全班平均积分${r.avgScoreDelta > 0 ? '增加' : '减少'} ${fmtNum(Math.abs(r.avgScoreDelta))} 分。`);
  const src = r.sources;
  if (src.manualCount >= 3 && src.manualMinus < 0 && -src.manualMinus > src.manualPlus * 1.5) I('INFO', `手动扣分（${fmtNum(src.manualMinus)}）明显多于加分（+${fmtNum(src.manualPlus)}），可以多用表扬加分。`);
  const r0 = r.reasons[0];
  if (r0 && r0.count >= 3) I('INFO', `最常见的加减分原因是“${r0.reason}”（${r0.count} 次，涉及 ${r0.people} 人，合计 ${signed(r0.total)}）。`);
  const cr = r.examCorrelation;
  if (cr != null) {
    if (cr >= 0.3) {
      const hi = corrPairs.filter((x) => x.rate >= 0.9).map((x) => x.examPct); const lo = corrPairs.filter((x) => x.rate < 0.9).map((x) => x.examPct);
      I('INFO', `作业交齐率与考试成绩正相关（r = ${f2(cr)}）${hi.length && lo.length ? `：交齐率 ≥90% 的同学考试平均得分率 ${pct(avg(hi))}，其余 ${pct(avg(lo))}` : ''}。`);
    } else if (cr <= -0.3) I('INFO', `作业交齐率与考试成绩呈负相关（r = ${f2(cr)}），可能存在“交了但没做好”的情况。`);
  }
  if (k.unregistered > 0) I('WARN', `有 ${k.unregistered} 人次未登记（${r.unregistered.length} 次作业），统计可能偏低，建议补登记。`);
  return out;
}

export const rangeTitle = (r) => (RANGES[r.range].calendar || r.range === 'CUSTOM' ? r.window.label.split(' · ')[0].split('（')[0] : RANGES[r.range].label);

/** 文字版分析报告 */
export function text(c, r, now = Date.now(), exam = null) {
  const L = []; const k = r.kpi; const ln = (s = '') => L.push(s);
  const d = new Date(now);
  ln(`${c.name} 作业与成绩分析报告（${rangeTitle(r)}）`);
  ln(`范围：${r.window.label}`);
  ln(`生成日期：${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日`);
  ln(); ln('一、总体情况');
  ln(`共 ${k.assignments} 次作业，应交 ${k.expected} 人次。交齐率 ${pct(k.handInRate)}，按时率 ${pct(k.onTimeRate)}，优秀率 ${pct(k.excellentRate)}，缺交率 ${pct(k.missingRate)}，迟交率 ${pct(k.lateRate)}；全员交齐 ${k.perfect} 次（最长连续 ${r.longestPerfectRun} 次）。`);
  ln(`缺交 ${k.missing} 人次，迟交 ${k.late} 人次，请假 ${k.leave} 人次，未登记 ${k.unregistered} 人次。`);
  if (r.previous) ln(`${r.window.prevLabel}：交齐率 ${pct(r.previous.handInRate)}，按时率 ${pct(r.previous.onTimeRate)}，缺交率 ${pct(r.previous.missingRate)}。`);
  if (r.insights.length) { ln(); ln('二、主要发现'); r.insights.forEach((s, i) => ln(`${i + 1}. ${s.text}`)); }
  ln(); ln('三、分项对比');
  if (r.subjects.length) ln('各科：' + r.subjects.map((x) => `${x.subject} 交齐 ${pct(x.rate)}、按时 ${pct(x.onTimeRate)}（${x.count} 次，缺交 ${x.missing}）`).join('；'));
  const days = ['一', '二', '三', '四', '五', '六', '日'];
  const wd = r.weekday.map((v, i) => (v == null ? null : `周${days[i]} ${pct(v)}`)).filter(Boolean);
  if (wd.length) ln('星期：' + wd.join('，'));
  if (r.groupStats.length > 1) ln('小组：' + r.groupStats.map((g) => `${g.name}${g.leader ? `（组长${g.leader.name}）` : ''} 交齐 ${pct(g.rate)}、缺交 ${g.missing}、平均积分 ${fmtNum(g.avgScore)}`).join('；'));
  ln('交齐率分布：' + r.rateBins.map((x) => `${x[0]} ${x[1]} 人`).join('，'));
  ln(); ln('四、学生');
  if (r.attention.length) ln('需要关注：' + r.attention.map((x) => `${x.student.name} 缺交 ${x.missing} 次${x.missingStreak >= 2 ? `（连续 ${x.missingStreak} 次）` : ''}，交齐率 ${pct(x.rate)}`).join('；'));
  if (r.progress?.stars.length) ln(`进步之星（${r.progress.basis}）：` + r.progress.stars.map((x) => `${x.student.name} ${x.reasons.slice(0, 2).join('，')}`).join('；'));
  if (r.progress?.watch.length) ln(`退步关注（${r.progress.basis}）：` + r.progress.watch.map((x) => `${x.student.name} ${x.reasons.slice(0, 2).join('，')}`).join('；'));
  if (r.risk.length) ln('缺交风险：' + r.risk.map((x) => `${x.student.name} ${pct(x.prob)}（${x.why}）`).join('；'));
  if (r.weakSubjects.length) ln('偏科提醒：' + r.weakSubjects.map((x) => `${x.student.name} ${x.subject} ${pct(x.subjectRate)}（整体 ${pct(x.overallRate)}）`).join('；'));
  if (r.streakers.length) ln('连续交齐：' + r.streakers.map((x) => `${x.student.name} ${x.handStreak} 次`).join('、'));
  ln('学生分层：' + r.tiers.map((t) => `${t.name} ${t.students.length} 人`).join('；') + `（交齐率中位数 ${pct(r.rateMedian)}，标准差 ${Math.round(r.rateStd * 100)} 个百分点）`);
  if (r.perfectAttendance.length) ln(`全勤之星（${r.perfectAttendance.length} 人）：` + r.perfectAttendance.map((x) => x.student.name).join('、'));
  if (r.risers.length) ln('积分上升最多：' + r.risers.map((x) => `${x.student.name} ${signed(x.scoreDelta)}`).join('、'));
  if (r.fallers.length) ln('积分下降最多：' + r.fallers.map((x) => `${x.student.name} ${signed(x.scoreDelta)}`).join('、'));
  ln(`积分：全班平均 ${fmtNum(r.avgScore)} 分${r.range !== 'ALL' ? `，本时段变化 ${signed(r.avgScoreDelta)}` : ''}`);
  const src = r.sources;
  ln(`积分来源：作业自动 ${signed(src.homework)}，手动加分 +${fmtNum(src.manualPlus)}，手动扣分 ${fmtNum(src.manualMinus)}，考试 ${signed(src.exam)}`);
  if (r.reasons.length) ln('加减分原因：' + r.reasons.slice(0, 6).map((x) => `${x.reason} ${x.count} 次（${signed(x.total)}）`).join('；'));
  if (r.buckets.length) ln(`按${r.bucketUnit}汇总交齐率：` + r.buckets.map((x) => `${x.label} ${pct(x.rate)}`).join('，'));
  if (r.rounds.length > 1) ln('各轮对比：' + r.rounds.map((x) => `${x.label} ${x.kpi.assignments} 次 交齐 ${pct(x.kpi.handInRate)}${x.examPct != null ? ` 考试 ${pct(x.examPct)}` : ''}`).join('；'));
  if (exam && exam.exams.length) {
    ln(); ln('五、考试');
    for (const e of exam.exams) { const s = exam.stats[e.id]; ln(`${fmtDay(e.date)} ${e.subject || ''}「${e.title}」：平均 ${fmtNum(s.avg)}/${fmtNum(e.fullScore)}，中位 ${fmtNum(s.median)}，最高 ${fmtNum(s.max)}，及格率 ${pct(s.passRate)}，优秀率 ${pct(s.excellentRate)}`); }
    if (exam.quality.length) ln('难度与区分度：' + exam.quality.map((q) => `${q.exam.title} 难度${difficultyText(q.difficulty)}（${pct(q.difficulty)}）、区分度${discriminationText(q.discrimination)}（${f2(q.discrimination)}）`).join('；'));
    if (exam.levels.length) ln('层级（按平均得分率）：' + exam.levels.map((x) => `${x[0]} ${x[1]} 人`).join('，'));
    if (exam.stars.length) ln('考试进步之星：' + exam.stars.map((x) => `${x.student.name}（${x.last.title} ${pct(x.lastPct)}，相对位置 +${Math.round(x.delta * 100)}）`).join('、'));
    if (exam.watch.length) ln('考试退步关注：' + exam.watch.map((x) => `${x.student.name}（${x.last.title} ${pct(x.lastPct)}，相对位置 ${Math.round(x.delta * 100)}）`).join('、'));
    exam.insights.forEach((x) => ln(`• ${x.text}`));
  }
  return L.join('\n').trimEnd();
}

// ---------------- 考试分析 ----------------

const examScoresIn = (c, e) => { const ids = new Set(c.students.map((s) => s.id)); return Object.entries(e.scores || {}).filter(([id, v]) => ids.has(id) && Number.isFinite(v)).map(([, v]) => v).sort((a, b) => a - b); };
export function examStat(c, e) {
  const v = examScoresIn(c, e); const n = v.length; const full = e.fullScore;
  const a = n ? avg(v) : 0;
  const median = !n ? 0 : n % 2 ? v[(n - 1) / 2] : (v[n / 2 - 1] + v[n / 2]) / 2;
  const std = n < 2 ? 0 : Math.sqrt(sum(v, (x) => (x - a) ** 2) / n);
  const rate = (p) => (n ? v.filter((x) => x >= full * p).length / n : 0);
  const ids = new Set(c.students.map((s) => s.id));
  const absent = (e.absent || []).filter((id) => ids.has(id)).length;
  return { count: n, absent, missing: Math.max(0, c.students.length - n - absent), avg: a, median, max: n ? v[n - 1] : 0, min: n ? v[0] : 0, std,
    passRate: rate(0.6), goodRate: rate(0.75), excellentRate: rate(0.85), lowRate: n ? v.filter((x) => x < full * 0.4).length / n : 0 };
}
export function examRanks(e) {
  const sorted = Object.entries(e.scores || {}).filter(([, v]) => Number.isFinite(v)).sort((a, b) => b[1] - a[1]);
  const out = {};
  sorted.forEach(([id, v], i) => { out[id] = i > 0 && Math.abs(sorted[i - 1][1] - v) < 1e-9 ? out[sorted[i - 1][0]] : i + 1; });
  return out;
}
export const previousExam = (c, e) => (c.exams || []).filter((x) => x.id !== e.id && x.date < e.date && (!e.subject || x.subject === e.subject)).sort((a, b) => b.date - a.date)[0] || null;
export function quantile(sorted, q) {
  if (!sorted.length) return 0;
  const pos = (sorted.length - 1) * q; const lo = Math.floor(pos); const hi = Math.ceil(pos);
  return sorted[lo] + (sorted[hi] - sorted[lo]) * (pos - lo);
}
function discrimination(v, full) {
  if (v.length < 4 || full <= 0) return [0, 0, 0];
  const k = Math.max(1, Math.round(v.length * 0.27)); const d = [...v].sort((a, b) => b - a);
  const top = avg(d.slice(0, k)) / full; const bot = avg(d.slice(-k)) / full;
  return [top - bot, top, bot];
}
export const discriminationText = (d) => (d >= 0.4 ? '很好' : d >= 0.3 ? '良好' : d >= 0.2 ? '一般' : '偏低');
export const difficultyText = (p) => (p >= 0.85 ? '偏易' : p >= 0.7 ? '适中' : p >= 0.55 ? '偏难' : '很难');

export function examReport(c, subject = null) {
  const exams = (c.exams || []).filter((e) => subject == null || e.subject === subject).sort((a, b) => a.date - b.date);
  const stats = Object.fromEntries(exams.map((e) => [e.id, examStat(c, e)]));
  const avgPct = exams.filter((e) => stats[e.id].count > 0).map((e) => [e, stats[e.id].avg / e.fullScore]);
  const sm = new Map();
  for (const e of exams) { const k = e.subject || '其他'; if (!sm.has(k)) sm.set(k, []); if (stats[e.id].count > 0) sm.get(k).push(stats[e.id].avg / e.fullScore); }
  const subjects = [...sm].filter(([, v]) => v.length).map(([k, v]) => [k, avg(v)]).sort(byDesc((x) => x[1]));
  const ranks = Object.fromEntries(exams.map((e) => [e.id, examRanks(e)]));
  const has = (e, sid) => e.scores?.[sid] != null;
  const students = c.students.map((s) => {
    const pcts = exams.filter((e) => has(e, s.id)).map((e) => e.scores[s.id] / e.fullScore);
    const taken = exams.filter((e) => has(e, s.id)); const last = taken[taken.length - 1];
    const prev = last ? taken.filter((e) => e.id !== last.id && (!last.subject || e.subject === last.subject)).pop() : null;
    const lastRank = last ? ranks[last.id][s.id] ?? null : null; const prevRank = prev ? ranks[prev.id][s.id] ?? null : null;
    const a = pcts.length ? avg(pcts) : null;
    return { student: s, taken: pcts.length, absent: exams.filter((e) => (e.absent || []).includes(s.id)).length, avgPct: a, bestPct: pcts.length ? Math.max(...pcts) : null, worstPct: pcts.length ? Math.min(...pcts) : null,
      stdPct: pcts.length < 2 ? null : Math.sqrt(sum(pcts, (x) => (x - a) ** 2) / pcts.length), lastRank, rankChange: lastRank != null && prevRank != null ? prevRank - lastRank : null, trend: slope(pcts) };
  });
  const improvers = students.filter((x) => (x.rankChange || 0) > 0).sort(byDesc((x) => x.rankChange)).slice(0, 5);
  const decliners = students.filter((x) => (x.rankChange || 0) < 0).sort(byAsc((x) => x.rankChange)).slice(0, 5);
  const struggling = students.filter((x) => (x.avgPct ?? 1) < 0.6).sort(byAsc((x) => x.avgPct));
  const top = students.filter((x) => x.avgPct != null).sort(byDesc((x) => x.avgPct)).slice(0, 5);
  const gm = new Map();
  for (const s of c.students) { const g = s.group || '未分组'; if (!gm.has(g)) gm.set(g, []); gm.get(g).push(s.id); }
  const sById = Object.fromEntries(students.map((x) => [x.student.id, x]));
  const groups = [...gm].map(([g, ids]) => { const v = ids.map((id) => sById[id].avgPct).filter((x) => x != null); return v.length ? [g, avg(v)] : null; }).filter(Boolean).sort(byDesc((x) => x[1]));

  const quality = exams.map((e) => { const v = examScoresIn(c, e); return !v.length || e.fullScore <= 0 ? null : { exam: e, difficulty: avg(v) / e.fullScore, discrimination: discrimination(v, e.fullScore)[0], q1: quantile(v, 0.25), q3: quantile(v, 0.75) }; }).filter(Boolean);
  const avgOf = Object.fromEntries(exams.map((e) => [e.id, stats[e.id].count > 0 ? stats[e.id].avg / e.fullScore : 0]));
  const rel = (e, sid) => (has(e, sid) ? e.scores[sid] / e.fullScore - avgOf[e.id] : null);
  const progressAll = c.students.map((s) => {
    const taken = exams.filter((e) => has(e, s.id)); if (taken.length < 2) return null;
    const last = taken[taken.length - 1]; const before = avg(taken.slice(0, -1).map((e) => rel(e, s.id))); const after = rel(last, s.id);
    return { student: s, before, after, delta: after - before, last, lastPct: last.scores[s.id] / last.fullScore };
  }).filter(Boolean);
  const stars = progressAll.filter((x) => x.delta >= 0.05).sort(byDesc((x) => x.delta)).slice(0, 8);
  const watch = progressAll.filter((x) => x.delta <= -0.05).sort(byAsc((x) => x.delta)).slice(0, 8);
  const labels = ['优秀', '良好', '及格', '不及格'];
  const level = (p) => (p >= 0.85 ? 0 : p >= 0.75 ? 1 : p >= 0.6 ? 2 : 3);
  let flow = null;
  const toE = exams[exams.length - 1];
  const fromE = toE ? previousExam(c, toE) : null;
  if (toE && fromE && (exams.includes(fromE) || subject == null)) {
    const m = Array.from({ length: 4 }, () => [0, 0, 0, 0]); let up = 0; let down = 0; let same = 0;
    for (const s of c.students) {
      if (!has(fromE, s.id) || !has(toE, s.id)) continue;
      const i = level(fromE.scores[s.id] / fromE.fullScore); const j = level(toE.scores[s.id] / toE.fullScore);
      m[i][j]++; if (j < i) up++; else if (j > i) down++; else same++;
    }
    if (up + down + same > 0) flow = { from: fromE, to: toE, labels, matrix: m, up, down, same };
  }
  const withAvg = students.filter((x) => x.avgPct != null);
  const nearPass = withAvg.filter((x) => x.avgPct >= 0.55 && x.avgPct < 0.6).sort(byDesc((x) => x.avgPct));
  const nearEx = withAvg.filter((x) => x.avgPct >= 0.8 && x.avgPct < 0.85).sort(byDesc((x) => x.avgPct));
  const imbalance = subject != null ? [] : c.students.map((s) => {
    const bm = new Map();
    for (const e of exams) if (has(e, s.id) && e.subject) { if (!bm.has(e.subject)) bm.set(e.subject, []); bm.get(e.subject).push(e.scores[s.id] / e.fullScore); }
    if (bm.size < 2) return null;
    const vals = [...bm].map(([k, v]) => [k, avg(v)]);
    const b = vals.reduce((a, x) => (x[1] > a[1] ? x : a)); const w = vals.reduce((a, x) => (x[1] < a[1] ? x : a));
    return b[1] - w[1] >= 0.15 ? { student: s, best: b, worst: w } : null;
  }).filter(Boolean).sort(byDesc((x) => x.best[1] - x.worst[1])).slice(0, 8);
  const steady = withAvg.filter((x) => x.taken >= 3 && (x.stdPct ?? 1) < 0.05).sort(byDesc((x) => x.avgPct));
  const lastRels = (s) => exams.filter((e) => has(e, s.id)).slice(-3).map((e) => rel(e, s.id));
  const rising = withAvg.filter((x) => { const r = lastRels(x.student); return r.length === 3 && r[0] < r[1] && r[1] < r[2] && r[2] - r[0] >= 0.03; });
  const falling = withAvg.filter((x) => { const r = lastRels(x.student); return r.length === 3 && r[0] > r[1] && r[1] > r[2] && r[0] - r[2] >= 0.03; });
  const hw = Object.fromEntries(c.students.map((s) => [s.id, insight(s, c.assignments)]));
  const diligentLow = withAvg.map((x) => { const h = hw[x.student.id]; return h && h.expected >= 3 && h.rate >= 0.9 && x.avgPct < 0.6 ? { student: x.student, hwRate: h.rate, examPct: x.avgPct } : null; }).filter(Boolean).sort(byAsc((x) => x.examPct));
  const skipButGood = withAvg.map((x) => { const h = hw[x.student.id]; return h && h.expected >= 3 && h.rate < 0.75 && x.avgPct >= 0.8 ? { student: x.student, hwRate: h.rate, examPct: x.avgPct } : null; }).filter(Boolean).sort(byDesc((x) => x.examPct));
  const gen = new Map();
  for (const x of withAvg) { const g = (x.student.gender || '').slice(0, 1); if (g !== '男' && g !== '女') continue; if (!gen.has(g)) gen.set(g, []); gen.get(g).push(x.avgPct); }
  const genders = [...gen].map(([g, l]) => [g, l.length, avg(l)]).sort(byDesc((x) => x[2]));
  const levels = labels.map((l, i) => [l, withAvg.filter((x) => level(x.avgPct) === i).length]);

  const ins = []; const I = (level, text) => ins.push({ level, text });
  if (avgPct.length >= 3) { const sl = slope(avgPct.map((x) => x[1])) * 100; if (Math.abs(sl) >= 1) I(sl > 0 ? 'GOOD' : 'WARN', `全班平均得分率${sl > 0 ? '逐场上升' : '逐场下降'}，每场约 ${f1(sl)} 个百分点。`); }
  if (toE) {
    const s = stats[toE.id];
    if (s.count > 0) I(s.passRate >= 0.8 ? 'GOOD' : s.passRate < 0.6 ? 'WARN' : 'INFO', `最近一场「${toE.title}」平均 ${fmtNum(s.avg)} 分，及格率 ${pct(s.passRate)}，优秀率 ${pct(s.excellentRate)}，标准差 ${fmtNum(s.std)}。`);
    if (s.missing > 0) I('WARN', `「${toE.title}」还有 ${s.missing} 人没有录入成绩。`);
  }
  if (subjects.length >= 2 && subjects[0][1] - subjects[subjects.length - 1][1] >= 0.05) { const a = subjects[0]; const b = subjects[subjects.length - 1]; I('INFO', `${b[0]}平均得分率最低（${pct(b[1])}），比${a[0]}低 ${Math.round((a[1] - b[1]) * 100)} 个百分点。`); }
  if (improvers.length) I('GOOD', `名次进步：${improvers.map((x) => `${x.student.name}（↑${x.rankChange}）`).join('、')}。`);
  if (decliners.length) I('WARN', `名次退步：${decliners.map((x) => `${x.student.name}（↓${-x.rankChange}）`).join('、')}。`);
  if (struggling.length) I('WARN', `${struggling.length} 人平均得分率不足 60%：${struggling.slice(0, 6).map((x) => x.student.name).join('、')}${struggling.length > 6 ? ' 等' : ''}。`);
  const unstable = students.filter((x) => (x.stdPct ?? 0) >= 0.12 && x.taken >= 3).sort(byDesc((x) => x.stdPct));
  if (unstable.length) I('INFO', `成绩波动较大：${unstable.slice(0, 5).map((x) => x.student.name).join('、')}。`);
  if (groups.length >= 2 && groups[0][1] - groups[groups.length - 1][1] >= 0.05) I('INFO', `小组平均得分率：${groups[0][0]} 最高（${pct(groups[0][1])}），${groups[groups.length - 1][0]} 最低（${pct(groups[groups.length - 1][1])}）。`);
  if (stars.length) I('GOOD', `考试进步之星（最近一场相对班级平均的位置比之前高）：${stars.slice(0, 5).map((x) => `${x.student.name}（高 ${Math.round(x.delta * 100)} 个百分点）`).join('、')}。`);
  if (watch.length) I('WARN', `考试退步关注（最近一场相对班级平均的位置比之前低）：${watch.slice(0, 5).map((x) => `${x.student.name}（低 ${Math.round(-x.delta * 100)} 个百分点）`).join('、')}。`);
  if (flow && flow.up + flow.down > 0) I(flow.up >= flow.down ? 'GOOD' : 'WARN', `从「${flow.from.title}」到「${flow.to.title}」：${flow.up} 人升档、${flow.down} 人降档、${flow.same} 人不变。`);
  if (nearPass.length) I('WARN', `临界及格（平均 55%–60%）：${nearPass.slice(0, 6).map((x) => x.student.name).join('、')}，是提升及格率的重点。`);
  if (nearEx.length) I('INFO', `临界优秀（平均 80%–85%）：${nearEx.slice(0, 6).map((x) => x.student.name).join('、')}。`);
  if (imbalance.length) I('INFO', `偏科：${imbalance.slice(0, 4).map((x) => `${x.student.name} ${x.best[0]}${pct(x.best[1])}、${x.worst[0]}${pct(x.worst[1])}`).join('；')}。`);
  if (diligentLow.length) I('WARN', `作业交得齐但考试不理想（可能“交了没学会”）：${diligentLow.slice(0, 6).map((x) => x.student.name).join('、')}。`);
  if (skipButGood.length) I('INFO', `作业常缺但考试不错（会但不交）：${skipButGood.slice(0, 6).map((x) => x.student.name).join('、')}。`);
  if (rising.length) I('GOOD', `连续三场上升：${rising.slice(0, 6).map((x) => x.student.name).join('、')}。`);
  if (falling.length) I('WARN', `连续三场下滑：${falling.slice(0, 6).map((x) => x.student.name).join('、')}。`);
  const ql = quality[quality.length - 1];
  if (ql && ql.discrimination >= 0.01 && ql.discrimination <= 0.2) I('INFO', `「${ql.exam.title}」区分度偏低（${f2(ql.discrimination)}），题目可能过易或过难，难以拉开差距。`);
  if (genders.length === 2 && Math.abs(genders[0][2] - genders[1][2]) >= 0.05) I('INFO', `${genders[0][0]}生平均得分率比${genders[1][0]}生高 ${Math.round((genders[0][2] - genders[1][2]) * 100)} 个百分点。`);
  return { exams, stats, avgPct, subjects, students, improvers, decliners, struggling, top, groups, insights: ins, quality, stars, watch, flow, nearPass, nearExcellent: nearEx, imbalance, steady, rising, falling, diligentLow, skipButGood, genders, levels };
}

// ---------------- 关注度指数 ----------------

export const ATT_LEVELS = [['HIGH', '重点关注', '#e5384d'], ['MID', '需要关注', '#e8792c'], ['LOW', '留意', '#d4a514'], ['OK', '正常', '#16a34a']];
export const attLevelOf = (x) => (x >= 60 ? 'HIGH' : x >= 40 ? 'MID' : x >= 20 ? 'LOW' : 'OK');

/** 全班在时刻 at 的关注度（从高到低） */
export function attention(c0, rules, at = Date.now()) {
  const c = asOf(c0, at);
  if (!c.students.length) return [];
  const desc = [...c.assignments].sort((a, b) => b.createdAt - a.createdAt);
  const recent = Object.fromEntries(c.students.map((s) => [s.id, desc.map((a) => st(a, s.id)).filter((x) => x !== 'LEAVE' && x !== 'NONE')]));
  const rateOf = (l) => (l.length ? l.filter((x) => HANDED.has(x)).length / l.length : null);
  const classRate10 = rateOf(Object.values(recent).flatMap((l) => l.slice(0, 10))) ?? 1;
  const ranked = [...studentStats(c, rules)].sort(byDesc((x) => x.score));
  const rankOf = Object.fromEntries(ranked.map((x, i) => [x.student.id, i]));
  const n = ranked.length;
  const exams = [...(c.exams || [])].sort((a, b) => a.date - b.date);
  const examZ = {}; const avgPct = {};
  for (const e of exams) {
    const v = c.students.filter((s) => e.scores?.[s.id] != null).map((s) => [s.id, e.scores[s.id] / e.fullScore]);
    const mean = v.length ? avg(v.map((x) => x[1])) : 0;
    const sd = v.length < 2 ? 0 : Math.sqrt(sum(v, (x) => (x[1] - mean) ** 2) / v.length);
    examZ[e.id] = Object.fromEntries(v.map(([id, p]) => [id, sd <= 1e-9 ? 0 : (p - mean) / sd]));
    avgPct[e.id] = v.length ? mean : 0;
  }
  return c.students.map((s) => {
    const m = recent[s.id]; const parts = [];
    const P = (key, label, weight, value, detail) => parts.push({ key, label, weight, value, detail, points: 0 });
    let num = 0; let den = 0; let w = 1;
    for (const x of m.slice(0, 8)) { num += w * (x === 'MISSING' ? 1 : x === 'LATE' ? 0.4 : 0); den += w; w *= 0.75; }
    const m8 = m.slice(0, 8);
    P('risk', '缺交风险', 0.3, den === 0 ? 0 : num / den, !m.length ? '暂无记录' : `最近 ${Math.min(8, m.length)} 次：未交 ${m8.filter((x) => x === 'MISSING').length}、迟交 ${m8.filter((x) => x === 'LATE').length}`);
    let streak = 0; while (streak < m.length && m[streak] === 'MISSING') streak++;
    P('streak', '连续未交', 0.15, Math.min(streak, 4) / 4, streak === 0 ? '目前没有连续未交' : `已连续 ${streak} 次未交`);
    const r10 = rateOf(m.slice(0, 10));
    P('gap', '交齐差距', 0.15, r10 == null ? 0 : clamp((classRate10 - r10) / 0.3, 0, 1), r10 == null ? '暂无记录' : `最近交齐率 ${pct(r10)}，全班 ${pct(classRate10)}`);
    const a5 = rateOf(m.slice(0, 5)); const b5 = rateOf(m.slice(5, 10));
    const enough = a5 != null && b5 != null && m.length >= 8;
    P('decline', '近期下滑', 0.1, enough ? clamp((b5 - a5) / 0.4, 0, 1) : 0, enough ? `最近 5 次 ${pct(a5)}，之前 5 次 ${pct(b5)}` : '记录不足 8 次');
    const taken = exams.filter((e) => e.scores?.[s.id] != null);
    if (taken.length) {
      const last = taken[taken.length - 1]; const z = examZ[last.id][s.id] ?? 0;
      P('examWeak', '考试偏弱', 0.15, clamp(-z / 2, 0, 1), `「${last.title}」${z >= 0 ? '高于' : '低于'}全班平均 ${f1(z)} 个标准差`);
      if (taken.length >= 2) {
        const rel = (e) => e.scores[s.id] / e.fullScore - avgPct[e.id];
        const d = rel(last) - avg(taken.slice(0, -1).map(rel));
        P('examDrop', '考试退步', 0.05, clamp(-d / 0.15, 0, 1), `相对班级平均的位置${d >= 0 ? '提高' : '下降'} ${Math.round(Math.abs(d) * 100)} 个百分点`);
      }
    }
    const pr = n <= 1 ? 0 : rankOf[s.id] / (n - 1);
    P('low', '积分靠后', 0.1, clamp((pr - 0.5) * 2, 0, 1), `积分第 ${rankOf[s.id] + 1} 名（共 ${n} 人）`);
    const wsum = sum(parts, (p) => p.weight);
    for (const p of parts) p.points = p.weight / wsum * p.value * 100;
    const index = sum(parts, (p) => p.points);
    return { student: s, index, level: attLevelOf(index), parts, top: parts.filter((p) => p.points >= 1).sort(byDesc((p) => p.points)) };
  }).sort(byDesc((x) => x.index));
}
/** 每周一个点的走势（最近 weeks 周，含现在），学生 id → 指数列表（旧 → 新） */
export function attentionWeekly(c, rules, weeks = 8, now = Date.now()) {
  const per = Array.from({ length: weeks }, (_, i) => now - (weeks - 1 - i) * 7 * DAY).map((t) => Object.fromEntries(attention(c, rules, t).map((x) => [x.student.id, x.index])));
  return Object.fromEntries(c.students.map((s) => [s.id, per.map((m) => m[s.id] ?? 0)]));
}

// ---------------- 股票模式 ----------------

export const SPANS = [['W1', '1周', 7, 1], ['M1', '1月', 30, 1], ['M3', '3月', 91, 7], ['M6', '6月', 182, 7], ['Y1', '1年', 364, 14], ['ALL', '全部', -1, 0]];
const eventTimes = (c) => [...new Set([...c.assignments.map((a) => a.createdAt), ...c.scores.map((e) => e.time), ...(c.exams || []).map((e) => e.date), ...(c.recharges || []).map(rechargeStart), ...(c.settlements || []).map((x) => x.time)])].sort((a, b) => a - b);
export function stockWindow(c, span, now) {
  const today = startOfDay(now); const sp = SPANS.find((x) => x[0] === span);
  if (span !== 'ALL') return [today - (sp[2] - 1) * DAY, sp[3] * DAY];
  const ev = eventTimes(c); const first = ev.length ? startOfDay(ev[0]) : today;
  const days = Math.max(1, Math.trunc((today - first) / DAY) + 1);
  return [first, (days <= 45 ? 1 : days <= 300 ? 7 : days <= 900 ? 30 : 91) * DAY];
}
const quoteOf = (id, candles) => {
  const base = candles[0]?.open ?? 0; const last = candles[candles.length - 1]?.close ?? 0;
  return { id, candles, base, last, change: last - base, pct: base < 1 ? null : (last - base) / base,
    high: candles.length ? Math.max(...candles.map((k) => k.high)) : 0, low: candles.length ? Math.min(...candles.map((k) => k.low)) : 0,
    volume: sum(candles, (k) => k.volume), prevClose: candles.length >= 2 ? candles[candles.length - 2].close : base, closes: [base, ...candles.map((k) => k.close)] };
};
function buildBoard(span, from, step, now, eventsAll, valueAt, volumeOf, ids, maxPerBucket = 6) {
  const bounds = []; for (let t = from; t < now; t += step) bounds.push(t); bounds.push(now);
  const events = eventsAll.filter((t) => t > from && t < now);
  const inner = bounds.slice(0, -1).map((a, i) => {
    const b = bounds[i + 1]; const ev = events.filter((t) => t >= a && t < b);
    return ev.length <= maxPerBucket ? ev : [...new Set(Array.from({ length: maxPerBucket }, (_, k) => ev[Math.trunc(k * (ev.length - 1) / Math.max(1, maxPerBucket - 1))]))];
  });
  const values = new Map([...new Set([...bounds, ...inner.flat()])].map((t) => [t, valueAt(t)]));
  const quote = (id) => quoteOf(id ?? '', bounds.slice(0, -1).map((a, i) => {
    const b = bounds[i + 1]; const vs = [values.get(a)(id), ...inner[i].map((t) => values.get(t)(id)), values.get(b)(id)];
    return { start: a, end: b, open: vs[0], high: Math.max(...vs), low: Math.min(...vs), close: vs[vs.length - 1], volume: volumeOf(a, b, id) };
  }));
  const stocks = Object.fromEntries(ids.map((id) => [id, quote(id)]));
  const qs = Object.values(stocks);
  return { span, from, now, market: quote(null), stocks, up: qs.filter((q) => q.change > 0.05).length, down: qs.filter((q) => q.change < -0.05).length };
}
/** 个人关注度行情 */
export function stockBoard(c, rules, span, now = Date.now()) {
  const [from, step] = stockWindow(c, span, now);
  const ids = c.students.map((s) => s.id);
  const valueAt = (t) => { const m = Object.fromEntries(attention(c, rules, t).map((x) => [x.student.id, x.index])); return (id) => (id == null ? (ids.length ? avg(ids.map((i) => m[i] ?? 0)) : 0) : m[id] ?? 0); };
  const volumeOf = (a, b, id) => sum(c.assignments.filter((x) => x.createdAt >= a && x.createdAt < b), (x) => (id == null ? Object.values(x.statuses).filter((s) => s !== 'NONE').length : st(x, id) !== 'NONE' ? 1 : 0))
    + c.scores.filter((e) => e.time >= a && e.time < b && (id == null || e.studentId === id)).length
    + sum((c.exams || []).filter((e) => e.date >= a && e.date < b), (e) => (id == null ? Object.keys(e.scores || {}).length : e.scores?.[id] != null ? 1 : 0));
  return buildBoard(span, from, step, now, eventTimes(c), valueAt, volumeOf, ids);
}
const namedGroups = (c) => { const m = {}; for (const s of c.students) if (s.group) (m[s.group] = m[s.group] || []).push(s); for (const g of Object.keys(m)) if (m[g].length < 2) delete m[g]; return m; };
function groupScoresAt(c, rules, groups, auto, sorted, t, win) {
  const recent = sorted.filter((a) => a.createdAt <= t).slice(-win);
  if (!recent.length) return {};
  const from = recent[0].createdAt;
  const raws = {};
  for (const [g, ms] of Object.entries(groups)) {
    const sts = recent.flatMap((a) => ms.map((m) => st(a, m.id))).filter((x) => x !== 'NONE' && x !== 'LEAVE');
    if (!sts.length) continue;
    const d = sum(ms, (m) => sum(recent, (a) => { const v = auto[m.id][a.id] ?? 0; return v < 0 ? -v : 0; })) / ms.length;
    const ex = (c.exams || []).filter((e) => e.date >= from && e.date <= t).flatMap((e) => ms.filter((m) => e.scores?.[m.id] != null).map((m) => e.scores[m.id] / e.fullScore));
    raws[g] = { rate: sts.filter((x) => HANDED.has(x)).length / sts.length, deduct: d, exc: sts.filter((x) => x === 'EXCELLENT').length / sts.length, exam: ex.length ? avg(ex) : null };
  }
  const vals = Object.values(raws); if (!vals.length) return {};
  const gr = { ...defaultGroupRank(), ...(rules.groupRank || {}) };
  const maxD = Math.max(...vals.map((r) => r.deduct)); const allExam = vals.every((r) => r.exam != null);
  return Object.fromEntries(Object.entries(raws).map(([g, r]) => {
    const dn = maxD <= 1e-9 ? 1 : 1 - r.deduct / maxD;
    let num = gr.wRate * r.rate + gr.wDeduct * dn + gr.wExcellent * r.exc; let den = gr.wRate + gr.wDeduct + gr.wExcellent;
    if (allExam) { num += gr.wExam * r.exam; den += gr.wExam; }
    return [g, den <= 0 ? 0 : num / den * 100];
  }));
}
/** 小组综合分行情 */
export function groupStockBoard(c, rules, span, now = Date.now(), win = 10) {
  const groups = namedGroups(c); const names = Object.keys(groups);
  if (!names.length) return null;
  const [from, step] = stockWindow(c, span, now);
  const sorted = sortedAssignments(c);
  const auto = {}; for (const ms of Object.values(groups)) for (const m of ms) auto[m.id] = autoPoints(c, rules, m.id);
  const events = [...new Set([...sorted.map((a) => a.createdAt), ...(c.exams || []).map((e) => e.date)])].sort((a, b) => a - b);
  const valueAt = (t) => { const m = groupScoresAt(c, rules, groups, auto, sorted, t, win); const v = Object.values(m); return (g) => (g == null ? (v.length ? avg(v) : 0) : m[g] ?? 0); };
  const volumeOf = (a, b, g) => { const ms = g == null ? Object.values(groups).flat() : groups[g];
    return sum(sorted.filter((x) => x.createdAt >= a && x.createdAt < b), (x) => ms.filter((m) => st(x, m.id) !== 'NONE').length)
      + sum((c.exams || []).filter((e) => e.date >= a && e.date < b), (e) => ms.filter((m) => e.scores?.[m.id] != null).length); };
  return buildBoard(span, from, step, now, events, valueAt, volumeOf, names);
}

// ---------------- 信誉分、小组关注度行情（与安卓 MetricStock.kt 同口径） ----------------
const recordVolume = (c, a, b, ids) => sum(c.assignments.filter((x) => x.createdAt >= a && x.createdAt < b), (x) => Object.entries(x.statuses).filter(([id, k]) => k !== 'NONE' && (!ids || ids.has(id))).length)
  + sum((c.exams || []).filter((e) => e.date >= a && e.date < b), (e) => Object.keys(e.scores || {}).filter((id) => !ids || ids.has(id)).length);
const recordEvents = (c) => [...new Set([...c.assignments.map((a) => a.createdAt), ...(c.exams || []).map((e) => e.date)])].sort((a, b) => a - b);
const avgOf = (m) => (k) => { const v = Object.values(m); return k == null ? (v.length ? avg(v) : 0) : m[k] ?? 0; };
/** 个人 · 信誉分（越高越好） */
export function creditStockBoard(c, span, now = Date.now()) {
  const [from, step] = stockWindow(c, span, now);
  const ids = c.students.map((s) => s.id);
  const valueAt = (t) => avgOf(Object.fromEntries(creditAll(c, t).filter((r) => r.hasData).map((r) => [r.student.id, r.index])));
  return buildBoard(span, from, step, now, recordEvents(c), valueAt, (a, b, id) => recordVolume(c, a, b, id == null ? null : new Set([id])), ids);
}
/** 小组 · 信誉分 */
export function groupCreditBoard(c, span, now = Date.now()) {
  const groups = namedGroups(c); const names = Object.keys(groups);
  if (!names.length) return null;
  const [from, step] = stockWindow(c, span, now);
  const valueAt = (t) => avgOf(creditGroups(c, t));
  const volumeOf = (a, b, g) => recordVolume(c, a, b, new Set((g == null ? Object.values(groups).flat() : groups[g]).map((m) => m.id)));
  return buildBoard(span, from, step, now, recordEvents(c), valueAt, volumeOf, names);
}
/** 各组在某一刻的关注度（组员平均） */
export function groupAttentionAt(c, rules, t = Date.now(), groups = namedGroups(c)) {
  const by = Object.fromEntries(attention(c, rules, t).map((x) => [x.student.id, x.index]));
  return Object.fromEntries(Object.entries(groups).map(([g, ms]) => [g, avg(ms.map((m) => by[m.id] ?? 0))]));
}
/** 小组 · 关注度（越高越需要关注） */
export function groupAttentionBoard(c, rules, span, now = Date.now()) {
  const groups = namedGroups(c); const names = Object.keys(groups);
  if (!names.length) return null;
  const [from, step] = stockWindow(c, span, now);
  const valueAt = (t) => avgOf(groupAttentionAt(c, rules, t, groups));
  const volumeOf = (a, b, g) => recordVolume(c, a, b, new Set((g == null ? Object.values(groups).flat() : groups[g]).map((m) => m.id)));
  return buildBoard(span, from, step, now, [...recordEvents(c), ...c.scores.map((e) => e.time)].sort((a, b) => a - b), valueAt, volumeOf, names);
}
/** 行情板块：0 个人关注度、1 小组综合、2 个人信誉分、3 小组信誉分、4 小组关注度 */
export function boardOf(c, rules, kind, span, now = Date.now()) {
  if (kind === 1) return groupStockBoard(c, rules, span, now);
  if (kind === 2) return creditStockBoard(c, span, now);
  if (kind === 3) return groupCreditBoard(c, span, now);
  if (kind === 4) return groupAttentionBoard(c, rules, span, now);
  return stockBoard(c, rules, span, now);
}

// ---------------- 小组分析 ----------------

export function groupsOf(c) {
  const m = namedGroups(c);
  return Object.fromEntries(Object.keys(m).sort((a, b) => a.length - b.length || (a < b ? -1 : a > b ? 1 : 0)).map((g) => [g, m[g]]));
}
export function groupRateOf(ms, a) { const s = ms.map((m) => st(a, m.id)).filter((x) => x !== 'NONE' && x !== 'LEAVE'); return s.length ? s.filter((x) => HANDED.has(x)).length / s.length : null; }
const weekStart = (t) => { const d = startOfDay(t); return addDays(d, -weekIdx(d)); };

export function groupReport(c, rules, weeksBack = 8) {
  const groups = groupsOf(c); const gnames = Object.keys(groups);
  if (!gnames.length) return { teams: [], progressBasis: null, progress: [], weeks: [], periods: [] };
  const sorted = sortedAssignments(c);
  const scores = Object.fromEntries(studentStats(c, rules).map((x) => [x.student.id, x.score]));
  const last = latestRecharges(c); const lead = leaderPeriods(c, rules);
  const auto = {}; for (const ms of Object.values(groups)) for (const m of ms) auto[m.id] = autoPoints(c, rules, m.id);
  const wins = {}; const ties = {}; const rankSum = {}; const rankN = {};
  for (const a of sorted) {
    const rs = gnames.map((g) => [g, groupRateOf(groups[g], a)]).filter((x) => x[1] != null);
    if (rs.length < 2) continue;
    const best = Math.max(...rs.map((x) => x[1])); const top = rs.filter((x) => Math.abs(x[1] - best) < 1e-9);
    if (top.length === 1) wins[top[0][0]] = (wins[top[0][0]] || 0) + 1; else for (const [g] of top) ties[g] = (ties[g] || 0) + 1;
    for (const [g, r] of rs) { rankSum[g] = (rankSum[g] || 0) + 1 + rs.filter((x) => x[1] > r + 1e-9).length; rankN[g] = (rankN[g] || 0) + 1; }
  }
  const byWeek = new Map();
  for (const a of sorted) { const k = weekStart(a.createdAt); if (!byWeek.has(k)) byWeek.set(k, []); byWeek.get(k).push(a); }
  const weekRates = (list) => Object.fromEntries(gnames.map((g) => { const s = list.flatMap((a) => groups[g].map((m) => st(a, m.id))).filter((x) => x !== 'NONE' && x !== 'LEAVE'); return s.length ? [g, s.filter((x) => HANDED.has(x)).length / s.length] : null; }).filter(Boolean));
  const weekEntries = [...byWeek].sort((a, b) => a[0] - b[0]);
  const weeks = weekEntries.slice(-weeksBack).map(([start, list]) => {
    const rates = weekRates(list); const vals = Object.values(rates); const best = vals.length ? Math.max(...vals) : null;
    const winners = best == null || vals.length < 2 ? [] : Object.keys(rates).filter((g) => Math.abs(rates[g] - best) < 1e-9);
    return { label: `${md(start)} 这周`, start, rates, winners: winners.length === vals.length ? [] : winners };
  });
  const weekWins = {};
  for (const [, list] of weekEntries) {
    const rates = weekRates(list); const vals = Object.values(rates); if (!vals.length) continue;
    const best = Math.max(...vals); const w = Object.keys(rates).filter((g) => Math.abs(rates[g] - best) < 1e-9);
    if (vals.length >= 2 && w.length === 1) weekWins[w[0]] = (weekWins[w[0]] || 0) + 1;
  }
  let teams = gnames.map((g) => {
    const ms = groups[g];
    let expected = 0; let handed = 0; let onTime = 0; let excellent = 0; let missing = 0; let complete = 0; let played = 0; let cur = 0; let longest = 0;
    for (const a of sorted) {
      const s = ms.map((m) => st(a, m.id)).filter((x) => x !== 'NONE' && x !== 'LEAVE');
      if (!s.length) continue;
      played++; expected += s.length; handed += s.filter((x) => HANDED.has(x)).length; onTime += s.filter((x) => x === 'SUBMITTED' || x === 'EXCELLENT').length;
      excellent += s.filter((x) => x === 'EXCELLENT').length; missing += s.filter((x) => x === 'MISSING').length;
      if (s.every((x) => HANDED.has(x))) { complete++; cur++; longest = Math.max(longest, cur); } else cur = 0;
    }
    const ex = (c.exams || []).flatMap((e) => ms.filter((m) => e.scores?.[m.id] != null).map((m) => e.scores[m.id] / e.fullScore));
    const leader = leaderOf(c, g);
    const lps = leader ? (lead[leader.id] || []).filter((p) => !last[leader.id] || rechargeCounts(last[leader.id], p.time)) : [];
    const deduct = sum(ms, (m) => sum(Object.values(auto[m.id]).filter((v) => v < 0), (v) => -v)) / ms.length;
    return { name: g, leader, members: ms, expected, handed, onTime, excellent, missing, avgScore: avg(ms.map((m) => scores[m.id] ?? rules.initial)), examPct: ex.length ? avg(ex) : null,
      complete, played, completeStreak: cur, longestComplete: longest, wins: wins[g] || 0, ties: ties[g] || 0, avgRank: rankN[g] ? rankSum[g] / rankN[g] : 0,
      weekWins: weekWins[g] || 0, leaderPoints: sum(lps, (p) => p.points), leaderPeriods: lps.length, deductPerCapita: deduct,
      rate: expected === 0 ? 1 : handed / expected, onTimeRate: expected === 0 ? 1 : onTime / expected, excellentRate: expected === 0 ? 0 : excellent / expected };
  });
  const gr = { ...defaultGroupRank(), ...(rules.groupRank || {}) };
  const maxD = Math.max(0, ...teams.map((t) => t.deductPerCapita)); const allExam = teams.every((t) => t.examPct != null);
  teams = teams.map((t) => {
    const dn = maxD <= 1e-9 ? 1 : 1 - t.deductPerCapita / maxD;
    let num = gr.wRate * t.rate + gr.wDeduct * dn + gr.wExcellent * t.excellentRate; let den = gr.wRate + gr.wDeduct + gr.wExcellent;
    if (allExam) { num += gr.wExam * t.examPct; den += gr.wExam; }
    return { ...t, score: Math.round((den <= 0 ? 0 : num / den) * 10000) / 10000 };
  });
  const periods = groupRankPeriods(c, rules);
  const rankTotal = {};
  for (const p of periods) for (const s of p.standings) { const m = groups[s.group]?.[0]; if (!m) continue; if (!last[m.id] || rechargeCounts(last[m.id], p.time)) rankTotal[s.group] = (rankTotal[s.group] || 0) + s.points; }
  teams = teams.map((t) => ({ ...t, rank: 1 + teams.filter((x) => x.score > t.score).length, rankPoints: rankTotal[t.name] || 0 }))
    .sort(chain(byAsc((t) => t.rank), byDesc((t) => t.wins + t.ties * 0.5), byAsc((t) => t.name)));
  const [basis, prog] = groupProgress(c, groups, sorted, auto);
  return { teams, progressBasis: basis, progress: prog, weeks, periods };
}
function groupProgress(c, groups, sorted, auto) {
  if (sorted.length < 4) return [null, []];
  const resets = classResets(c);
  const r = [...resets].reverse().find((rr) => sorted.filter((a) => rechargeCounts(rr, a.createdAt)).length >= 2 && sorted.filter((a) => !rechargeCounts(rr, a.createdAt)).length >= 2);
  let before; let after; let basis;
  if (r) { before = sorted.filter((a) => !rechargeCounts(r, a.createdAt)); after = sorted.filter((a) => rechargeCounts(r, a.createdAt)); basis = '重置前 vs 重置后'; }
  else { const h = Math.floor(sorted.length / 2); before = sorted.slice(0, h); after = sorted.slice(h); basis = `前 ${h} 次 vs 后 ${sorted.length - h} 次`; }
  const cut = after[0].createdAt;
  const sideOf = (ms, list, exams) => {
    const s = list.flatMap((a) => ms.map((m) => st(a, m.id))).filter((x) => x !== 'NONE' && x !== 'LEAVE');
    const ex = exams.flatMap((e) => ms.filter((m) => e.scores?.[m.id] != null).map((m) => e.scores[m.id] / e.fullScore));
    const ids = new Set(list.map((a) => a.id));
    const d = sum(ms, (m) => sum(Object.entries(auto[m.id]).filter(([k, v]) => ids.has(k) && v < 0), ([, v]) => -v)) / ms.length / Math.max(1, list.length);
    return [s.length ? s.filter((x) => HANDED.has(x)).length / s.length : null, ex.length ? avg(ex) : null, d];
  };
  const out = Object.entries(groups).map(([g, ms]) => {
    const [br, bx, bd] = sideOf(ms, before, (c.exams || []).filter((e) => e.date < cut));
    const [ar, ax, ad] = sideOf(ms, after, (c.exams || []).filter((e) => e.date >= cut));
    if (br == null || ar == null) return null;
    const dr = ar - br; const dx = ax != null && bx != null ? ax - bx : null;
    const dd = clamp((bd - ad) / 2 * 0.1, -0.3, 0.3);
    const idx = dx != null ? dr * 0.5 + dd * 0.3 + dx * 0.2 : (dr * 0.5 + dd * 0.3) / 0.8;
    const why = [];
    if (Math.abs(dr) >= 0.02) why.push(`交齐率 ${pct(br)} → ${pct(ar)}`);
    if (Math.abs(bd - ad) >= 0.1) why.push(`每次人均扣分 ${fmtNum(bd)} → ${fmtNum(ad)}`);
    if (dx != null && Math.abs(dx) >= 0.02) why.push(`考试得分率 ${pct(bx)} → ${pct(ax)}`);
    return { group: g, beforeRate: br, afterRate: ar, beforeExam: bx, afterExam: ax, beforeDeduct: bd, afterDeduct: ad, index: idx, reasons: why };
  }).filter(Boolean).sort(byDesc((x) => x.index));
  return [basis, out];
}
/** 两组对决：每次作业比交齐率 */
export function groupDuel(c, a, b) {
  const groups = groupsOf(c); const ma = groups[a] || []; const mb = groups[b] || [];
  let aw = 0; let bw = 0; let dr = 0; const rows = [];
  for (const x of sortedAssignments(c)) {
    const ra = groupRateOf(ma, x); const rb = groupRateOf(mb, x);
    if (ra == null && rb == null) continue;
    rows.push([x, ra, rb]);
    if (ra == null || rb == null) continue;
    if (ra > rb + 1e-9) aw++; else if (rb > ra + 1e-9) bw++; else dr++;
  }
  return { a, b, aWins: aw, bWins: bw, draws: dr, recent: rows.slice(-10).reverse() };
}

// ---------------- 抄袭关系（与安卓 Plagiarism.kt 同算法） ----------------

/** 有抄袭记录的作业，最新的在前（关系图按作业切换用） */
export function plagiarismAssignments(c) {
  const ids = new Set(c.students.map((s) => s.id));
  return c.assignments.filter((a) => Object.keys(a.plagiarism || {}).some((r) => ids.has(r) && copiedFrom(a, r).some((p) => p === UNKNOWN_PROVIDER || (ids.has(p) && a.statuses[p] === 'MISSING'))))
    .sort((a, b) => b.createdAt - a.createdAt || (a.id < b.id ? 1 : a.id > b.id ? -1 : 0));
}

/** 某位同学每次涉及抄袭是本轮第几次（作业 id → 次数），与计分同一口径 */
export function plagiarismTimes(c, rules, sid) {
  const pg = { ...defaultPlagiarism(), ...(rules.plagiarism || {}) };
  const out = {}; if (!pg.enabled) return out;
  const resets = (c.recharges || []).filter((r) => (r.studentIds || []).includes(sid))
    .sort((a, b) => rechargeStart(a) - rechargeStart(b) || a.time - b.time || (a.id < b.id ? -1 : a.id > b.id ? 1 : 0));
  let round; let plag = 0;
  for (const a of [...c.assignments].sort((a, b) => a.createdAt - b.createdAt || (a.id < b.id ? -1 : a.id > b.id ? 1 : 0))) {
    let r = null; for (const x of resets) if (rechargeCounts(x, a.createdAt)) r = x.id;
    if (r !== round) { round = r; plag = 0; }
    if (a.statuses[sid] === 'MISSING' && plagiarized(a, sid)) out[a.id] = ++plag;
  }
  return out;
}

/**
 * 抄袭记录、提供者 → 抄袭者的关系边、每人的次数与扣分、抄袭圈（连在一起的同学）。
 * 关系图按作业来：传入 only（作业 id）时只看这一次作业，每次作业各有一张图；不传则汇总全部作业。
 */
export function plagiarismReport(c, rules, only = null) {
  const byId = Object.fromEntries(c.students.map((s) => [s.id, s]));
  const pg = { ...defaultPlagiarism(), ...(rules.plagiarism || {}) };
  const sorted = [...c.assignments].sort((a, b) => a.createdAt - b.createdAt || (a.id < b.id ? -1 : a.id > b.id ? 1 : 0)).filter((a) => only == null || a.id === only);
  const cases = []; const edges = new Map();
  for (const a of sorted) for (const rid of Object.keys(a.plagiarism || {})) {
    const r = byId[rid]; if (!r) continue;
    const ps = copiedFrom(a, rid).filter((id) => a.statuses[id] === 'MISSING').map((id) => byId[id]).filter(Boolean);
    const unknown = unknownProvider(a, rid);
    if (!ps.length && !unknown) continue;
    cases.push({ assignment: a, receiver: r, providers: ps, unknown, providerText: [...ps.map((p) => p.name), ...(unknown ? ['提供者不明'] : [])].join('、') });
    for (const p of ps) { const k = `${p.id}\u0000${r.id}`; if (!edges.has(k)) edges.set(k, { provider: p, receiver: r, assignments: [] }); edges.get(k).assignments.push(a); }
  }
  // 提供者不明的抄袭者也算涉及（关系图里单独一个点）
  const involved = [...new Set([...[...edges.values()].flatMap((e) => [e.provider.id, e.receiver.id]), ...cases.filter((k) => k.unknown).map((k) => k.receiver.id)])];
  const last = latestRecharges(c);
  const nodes = involved.map((id) => {
    const copied = sorted.filter((a) => copiedFrom(a, id).some((p) => p === UNKNOWN_PROVIDER || a.statuses[p] === 'MISSING')).length;
    const provided = sorted.filter((a) => copiedBy(a, id).length > 0).length;
    const pts = autoPoints(c, rules, id); const list = sorted.filter((a) => plagiarized(a, id));
    const role = copied > 0 && provided > 0 ? '既抄又提供' : copied > 0 ? '抄袭者' : '提供者';
    const points = sum(list, (a) => pts[a.id] ?? 0);
    if (only != null) { const t = plagiarismTimes(c, rules, id)[only] || 0; return { student: byId[id], copied, provided, total: copied + provided, role, points, roundTimes: t, factor: t > 0 ? plagiarismFactor(pg, t) : 0 }; }
    return { student: byId[id], copied, provided, total: copied + provided, role, points, roundTimes: list.filter((a) => !last[id] || rechargeCounts(last[id], a.createdAt)).length, factor: 0 };
  }).sort(chain(byDesc((n) => n.total), byAsc((n) => n.points), (a, b) => compareStudentNumbers(a.student.no, b.student.no)));
  const adj = {};
  for (const e of edges.values()) { (adj[e.provider.id] = adj[e.provider.id] || new Set()).add(e.receiver.id); (adj[e.receiver.id] = adj[e.receiver.id] || new Set()).add(e.provider.id); }
  const seen = new Set(); const clusters = [];
  for (const id of involved) {
    if (seen.has(id)) continue; seen.add(id);
    const comp = []; const stack = [id];
    while (stack.length) { const x = stack.pop(); comp.push(x); for (const y of adj[x] || []) if (!seen.has(y)) { seen.add(y); stack.push(y); } }
    clusters.push(comp.map((x) => byId[x]).sort((a, b) => compareStudentNumbers(a.no, b.no)));
  }
  const edgeList = [...edges.values()].map((e) => ({ ...e, count: e.assignments.length }))
    .sort(chain(byDesc((e) => e.count), byDesc((e) => e.assignments[e.assignments.length - 1].createdAt)));
  return { cases: cases.reverse(), edges: edgeList, nodes, clusters: clusters.sort(byDesc((x) => x.length)),
    copiers: nodes.filter((n) => n.copied > 0).length, providers: nodes.filter((n) => n.provided > 0).length, points: sum(nodes, (n) => n.points) };
}
