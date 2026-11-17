// 信誉分：与安卓 CreditTest、iPad Tests/main.swift 同一组用例
import test from 'node:test';
import assert from 'node:assert/strict';
import { defaultRules, creditAll, creditGroups, creditPeriods, studentStats } from '../../web/js/model.js';

const S = 'SUBMITTED'; const E = 'EXCELLENT'; const T = 'LATE'; const M = 'MISSING';
function cls(rows, { groups = {}, exams = [], review = {} } = {}) {
  const n = Math.max(...rows.map(([, l]) => l.length));
  const students = rows.map(([id], i) => ({ id, no: `0${i + 1}`, name: `同学${id}`, group: groups[id] || '' }));
  const assignments = Array.from({ length: n }, (_, k) => ({ id: `a${k}`, title: `作业${k}`, createdAt: (k + 1) * 1000, statuses: Object.fromEntries(rows.map(([id, l]) => [id, l[k] || 'NONE'])), reviewFailed: review[k] || [], plagiarism: {} }));
  return { id: 'c', name: '一班', students, assignments, scores: [], exams, recharges: [] };
}
const R = (credit, extra = {}) => ({ ...defaultRules(), credit: { ...defaultRules().credit, ...credit }, ...extra });

test('个人信誉分', () => {
  const c = cls([['s1', [S, S, E, T, M]]]);
  const r = creditAll(c, 1e12)[0];
  assert.equal(r.index, 68.43);
  assert.equal(r.level, '一般');
  assert.deepEqual(r.parts.map((p) => p.key), ['rate', 'ontime', 'excellent', 'streak', 'integrity', 'trend']);
  const withExam = { ...c, exams: [{ id: 'e1', title: '月考', subject: '数学', date: 6000, fullScore: 100, scores: { s1: 90 }, absent: [] }] };
  assert.equal(creditAll(withExam, 1e12)[0].index, 71.67);
  assert.equal(creditAll(cls([['s1', ['LEAVE']]]), 1e12)[0].hasData, false);
});

test('诚信：复审不合格拉低信誉分', () => {
  const ok = creditAll(cls([['s1', [S, S, M]]]), 1e12)[0].index;
  const failed = creditAll(cls([['s1', [S, S, M]]], { review: { 2: ['s1'] } }), 1e12)[0];
  assert.ok(failed.index < ok);
  assert.equal(failed.parts.find((p) => p.key === 'integrity').value, 0.5);
});

test('按段给个人加减分', () => {
  const c = cls([['s1', [E, E, E, E, E]]]);
  const rules = R({ personal: 'both', period: 'assignment' });
  const ps = creditPeriods(c, rules);
  assert.deepEqual(ps.map((p) => p.personal.s1[0]), [87.65, 90, 92.35, 94.71, 97.06]);
  assert.deepEqual(ps.map((p) => p.personal.s1[1]), [1, 2, 2, 2, 2]);
  assert.equal(studentStats(c, rules)[0].score, 100 + 5 + 9);
  assert.equal(studentStats(c, defaultRules())[0].score, 105);
});

test('只加不扣 / 只扣不加 / 不计入', () => {
  const c = cls([['s1', [M, M, M]]]);
  const score = (m) => studentStats(c, R({ personal: m, period: 'assignment' }))[0].score;
  assert.equal(creditPeriods(c, R({ personal: 'both', period: 'assignment' }))[0].personal.s1[0], 26.47);
  assert.equal(score('both'), 100 - 8 - 6);
  assert.equal(score('minus'), 100 - 8 - 6);
  assert.equal(score('plus'), 100 - 8);
  assert.equal(score('none'), 100 - 8);
});

test('小组信誉分加减分', () => {
  const c = cls([['s1', [E]], ['s2', [M]]], { groups: { s1: 'A', s2: 'A' } });
  const rules = R({ group: 'both', period: 'assignment' }, { groupRank: { ...defaultRules().groupRank, enabled: false }, leader: { ...defaultRules().leader, enabled: false } });
  const p = creditPeriods(c, rules)[0];
  assert.deepEqual(p.groups.A, [57.06, -1]);
  assert.deepEqual(p.personal, {});
  const sc = Object.fromEntries(studentStats(c, rules).map((x) => [x.student.id, x.score]));
  assert.equal(sc.s1, 100 + 1 - 1);
  assert.equal(sc.s2, 100 - 2 - 1);
  assert.deepEqual(creditGroups(c, 1e12), { A: 57.06 });
});

import { boardOf } from '../../web/js/analytics.js';
test('行情：没有数据的时刻不算 0，长区间也要有涨有跌（与安卓 StockOpenTest 同一份数据）', () => {
  const DAY = 86400000; const now = 1_800_000_000_000;
  const names = ['张锦宇', '李子涵', '林静', '王芳', '冉浩', '周杰', '陈浩宏', '胡晨', '赵敏', '孙悦', '郑怡', '吴磊'];
  const students = names.map((n, i) => ({ id: `s${i}`, no: `${i + 1}`, name: n, group: `第${(i % 3) + 1}组` }));
  const st = (i, k) => ((i * 7 + k * 3) % 11 === 0 ? 'MISSING' : (i + k) % 9 === 0 ? 'LATE' : (i * 3 + k) % 7 === 0 ? 'EXCELLENT' : (i + k) % 13 === 0 ? 'LEAVE' : 'SUBMITTED');
  const assignments = Array.from({ length: 30 }, (_, k) => ({ id: `a${k}`, title: `练习${k}`, createdAt: now - (30 - k) * 2 * DAY, statuses: Object.fromEntries(students.map((s, i) => [s.id, st(i, k)])), reviewFailed: [], plagiarism: {} }));
  const c = { id: 'c', name: '一班', students, assignments, scores: [], exams: [], recharges: [] };
  for (const [k, name] of [[1, '小组综合'], [3, '小组信誉分'], [4, '小组关注度'], [2, '个人信誉分'], [0, '个人关注度']]) {
    const qs = Object.values(boardOf(c, defaultRules(), k, 'ALL', now).stocks);
    if (k !== 0 && k !== 4) assert.ok(qs.every((q) => q.base > 0), `${name}：开盘价不能是没有数据时的 0`);
    assert.ok(qs.some((q) => q.change < -0.005) && qs.some((q) => q.change > 0.005), `${name}：有涨有跌`);
  }
  // 与安卓一致：小组信誉分 59.12→84.27、78.82→77.60、63.53→81.18
  const gc = boardOf(c, defaultRules(), 3, 'ALL', now).stocks;
  assert.deepEqual(Object.keys(gc).sort().map((g) => [gc[g].base.toFixed(2), gc[g].last.toFixed(2)]), [['59.12', '84.27'], ['78.82', '77.60'], ['63.53', '81.18']]);
});
