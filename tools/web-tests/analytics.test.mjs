// 统计分析：与安卓 AnalyticsTest 同一组用例（网页 analytics.js 与 Analytics.kt 同算法）
import test from 'node:test';
import assert from 'node:assert/strict';
import { defaultRules, normalizeData, rechargeScores } from '../../web/js/model.js';
import { window, kpi, report, text, examReport, attention, stockBoard, groupStockBoard, groupReport, groupDuel, slope, pearson } from '../../web/js/analytics.js';

const DAY = 86400000;
const NOW = 100 * DAY;
const FLAT = { ...defaultRules(), escalation: { enabled: false }, streakBonus: { enabled: false }, roundPoints: false, leader: { enabled: false }, groupRank: { enabled: false } };

function cls() {
  const students = [1, 2, 3, 4].map((i) => ({ id: `s${i}`, no: `0${i}`, name: `学生${i}`, group: i <= 2 ? '一组' : '二组' }));
  // 12 次作业：s1 全交；s2 后 3 次连续未交；s3 前半缺交后半全交；s4 请假一次、迟交一次
  const assignments = Array.from({ length: 12 }, (_, k) => {
    const i = k + 1;
    const m = {
      s1: i % 4 === 0 ? 'EXCELLENT' : 'SUBMITTED',
      s2: i >= 10 ? 'MISSING' : 'SUBMITTED',
      s3: i <= 6 ? 'MISSING' : 'SUBMITTED',
      s4: i === 3 ? 'LEAVE' : i === 5 ? 'LATE' : 'SUBMITTED',
    };
    return { id: `a${i}`, title: `作业${i}`, subject: i % 2 === 0 ? '数学' : '语文', createdAt: NOW - (13 - i) * DAY, statuses: m };
  });
  return normalizeData({ classes: [{ id: 'c', name: '一班', students, assignments }] }).classes[0];
}
const at = (c, id) => c.assignments.find((a) => a.id === id);
const reset = (c, ids, target, a) => rechargeScores(c, FLAT, ids, target, '', NOW, { assignmentId: a.id });

test('三种范围', () => {
  const c = cls();
  const all = window(c, { range: 'ALL' }, NOW);
  assert.equal(all.assignments.length, 12); assert.equal(all.previous.length, 0);
  const recent = window(c, { range: 'RECENT10' }, NOW);
  assert.equal(recent.assignments.length, 10); assert.equal(recent.assignments[0].id, 'a3');
  assert.deepEqual(recent.previous.map((a) => a.id), ['a1', 'a2']);
  assert.equal(window(c, { range: 'PERIOD' }, NOW).assignments.length, 12);
  const w = window(reset(c, c.students.map((s) => s.id), 100, at(c, 'a9')), { range: 'PERIOD' }, NOW);
  assert.deepEqual(w.assignments.map((a) => a.id), ['a9', 'a10', 'a11', 'a12']);
  assert.equal(w.previous.length, 8); assert.ok(w.reset);
  assert.equal(window(reset(c, ['s1'], 100, at(c, 'a9')), { range: 'PERIOD' }, NOW).reset, null);
});

test('核心指标', () => {
  const c = cls(); const k = kpi(c, c.assignments);
  assert.equal(k.missing, 9); assert.equal(k.late, 1); assert.equal(k.leave, 1);
  assert.ok(Math.abs(k.handInRate - 38 / 47) < 1e-6); assert.ok(Math.abs(k.onTimeRate - 37 / 47) < 1e-6);
  assert.equal(k.perfect, 3);
});

test('结论：连续未交、进步、小组', () => {
  const r = report(cls(), FLAT, { range: 'ALL' }, NOW);
  const s2 = r.students.find((x) => x.student.id === 's2');
  assert.equal(s2.missingStreak, 3);
  assert.equal(r.students.find((x) => x.student.id === 's3').longestMissing, 6);
  assert.equal(r.attention[0].student.id, 's2');
  assert.deepEqual(r.improved.map((x) => x.student.id), ['s3']);
  assert.deepEqual(r.perfectAttendance.map((x) => x.student.id), ['s1']);
  assert.equal(r.longestPerfectRun, 3);
  assert.equal(r.rateBins.reduce((n, x) => n + x[1], 0), 4);
  assert.equal(r.groupStats.length, 2);
  assert.equal(r.scoreTrend.length, 10);
  assert.ok(r.insights.some((x) => x.text.includes('学生2') && x.text.includes('连续')));
  const t = text(cls(), r, NOW);
  assert.ok(t.includes('交齐率')); assert.ok(t.includes('学生2 缺交 3 次（连续 3 次）')); assert.ok(/进步之星（[^）]*）：学生3/.test(t));
});

test('本轮积分变化从重置分数算起', () => {
  const c0 = cls(); const c = reset(c0, c0.students.map((s) => s.id), 50, at(c0, 'a9'));
  const r = report(c, FLAT, { range: 'PERIOD' }, NOW);
  const s2 = r.students.find((x) => x.student.id === 's2');
  assert.equal(s2.score, 44); assert.equal(s2.scoreDelta, -6);
  assert.equal(r.fallers[0].student.id, 's2');
});

test('空班级不报错', () => {
  const c = normalizeData({ classes: [{ id: 'e', name: '空' }] }).classes[0];
  const r = report(c, FLAT, { range: 'RECENT10' }, NOW);
  assert.equal(r.kpi.assignments, 0); assert.equal(r.scoreBins.length, 0); assert.equal(r.previous, null);
  assert.ok(text(c, r, NOW).trim());
  assert.equal(examReport(c).exams.length, 0);
  assert.deepEqual(attention(c, FLAT, NOW), []);
  assert.equal(groupStockBoard(c, FLAT, 'M1', NOW), null);
  assert.equal(groupReport(c, FLAT).teams.length, 0);
});

test('统计工具', () => {
  assert.equal(slope([1, 2, 3, 4]), 1);
  assert.ok(Math.abs(pearson([1, 2, 3, 4], [2, 4, 6, 8]) - 1) < 1e-9);
  assert.equal(pearson([1, 2], [1, 2]), null);
});

test('考试、关注度、行情、小组', () => {
  const c0 = cls();
  const exams = [
    { id: 'e1', title: '单元一', subject: '数学', date: NOW - 8 * DAY, fullScore: 100, scores: { s1: 95, s2: 80, s3: 50, s4: 70 } },
    { id: 'e2', title: '单元二', subject: '数学', date: NOW - 2 * DAY, fullScore: 100, scores: { s1: 90, s2: 60, s3: 75, s4: 72 } },
  ];
  const c = normalizeData({ classes: [{ ...c0, exams }] }).classes[0];
  const e = examReport(c);
  assert.equal(e.exams.length, 2);
  assert.equal(e.stats.e2.count, 4); assert.equal(e.stats.e2.avg, 74.25);
  assert.ok(e.stars.some((x) => x.student.id === 's3')); assert.ok(e.watch.some((x) => x.student.id === 's2'));
  assert.equal(e.improvers[0].student.id, 's3');
  assert.ok(e.flow && e.flow.up + e.flow.down + e.flow.same === 4);
  const att = attention(c, FLAT, NOW);
  assert.equal(att[0].student.id, 's2'); assert.ok(att[0].index > att[att.length - 1].index);
  const b = stockBoard(c, FLAT, 'M1', NOW);
  assert.equal(b.market.candles.length, 29); // 现在正好是 0 点：今天这一根还没开始 assert.equal(Object.keys(b.stocks).length, 4);
  assert.ok(Math.abs(b.stocks.s2.last - att[0].index) < 1e-9);
  const gb = groupStockBoard(c, FLAT, 'M1', NOW);
  assert.deepEqual(Object.keys(gb.stocks).sort(), ['一组', '二组']);
  const g = groupReport(c, FLAT);
  assert.equal(g.teams.length, 2); assert.equal(g.teams[0].rank, 1);
  const d = groupDuel(c, '一组', '二组');
  assert.equal(d.aWins + d.bWins + d.draws, 12);
});
