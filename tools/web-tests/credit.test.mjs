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
