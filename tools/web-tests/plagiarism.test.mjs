// 抄袭：与安卓 PlagiarismTest、iPad 同一组用例
import test from 'node:test';
import assert from 'node:assert/strict';
import { defaultRules, defaultPlagiarism, plagiarismFactor, autoPoints, studentStats, normalizeData, rechargeScores, plagiarized } from '../../web/js/model.js';
import { plagiarismReport, plagiarismAssignments } from '../../web/js/analytics.js';
import { plagiarismDesc } from '../../web/js/model.js';

const DAY = 86400000; const S = 'SUBMITTED'; const M = 'MISSING';
function cls() {
  const students = [['a', '甲'], ['b', '乙'], ['c', '丙'], ['d', '丁']].map(([id, name], i) => ({ id, no: String(i + 1), name }));
  const A = (k, s, p) => ({ id: `a${k}`, title: `作业${k}`, createdAt: (k + 1) * DAY, statuses: s, plagiarism: p });
  return normalizeData({ classes: [{ id: 'c', name: '一班', students, assignments: [
    A(0, { a: M, b: M, c: S, d: S }, { b: ['a'] }),
    A(1, { a: S, b: S, c: S, d: S }, {}),
    A(2, { a: M, b: M, c: M, d: S }, { b: ['a'], c: ['a'] }),
    A(3, { a: M, b: S, c: S, d: S }, { b: ['a'] }),
  ] }] }).classes[0];
}
const R = defaultRules();

test('抄袭者和提供者都翻倍，累犯加重', () => {
  const c = cls();
  assert.deepEqual(autoPoints(c, R, 'a'), { a0: -4, a1: 0, a2: -8, a3: -4 });
  assert.deepEqual(autoPoints(c, R, 'b'), { a0: -4, a1: 0, a2: -8, a3: 0 });
  assert.equal(autoPoints(c, R, 'c').a2, -4);
  assert.equal(Object.values(autoPoints(c, R, 'd')).reduce((a, b) => a + b, 0), 0);
  assert.equal(plagiarized(c.assignments[3], 'a'), false);
  assert.equal(studentStats(c, R).find((x) => x.student.id === 'a').missing, 3);
});

test('重置、关闭、不加重、上限', () => {
  const c = cls();
  const reset = rechargeScores(c, R, c.students.map((s) => s.id), 100, '', 10 * DAY, { assignmentId: 'a2' });
  assert.equal(autoPoints(reset, R, 'a').a2, -4);
  assert.equal(autoPoints(c, { ...R, plagiarism: { ...defaultPlagiarism(), enabled: false } }, 'b').a0, -2);
  assert.equal(autoPoints(c, { ...R, plagiarism: { ...defaultPlagiarism(), escalate: false } }, 'b').a2, -4);
  assert.deepEqual([1, 2, 3, 4, 5].map((n) => plagiarismFactor(defaultPlagiarism(), n)), [2, 4, 6, 8, 8]);
});

test('关系图', () => {
  const g = plagiarismReport(cls(), R);
  assert.equal(g.cases.length, 3);
  assert.deepEqual(g.edges.map((e) => [`${e.provider.id}→${e.receiver.id}`, e.count]), [['a→b', 2], ['a→c', 1]]);
  const a = g.nodes.find((n) => n.student.id === 'a');
  assert.equal(a.copied, 0); assert.equal(a.provided, 2); assert.equal(a.points, -12); assert.equal(a.role, '提供者');
  const b = g.nodes.find((n) => n.student.id === 'b');
  assert.equal(b.copied, 2); assert.equal(b.points, -12);
  assert.deepEqual(g.clusters.map((x) => x.map((s) => s.id)), [['a', 'b', 'c']]);
  assert.equal(g.copiers, 2); assert.equal(g.providers, 1);
});

test('关系图按作业：每次作业一张图', () => {
  const c = cls();
  assert.deepEqual(plagiarismAssignments(c).map((a) => a.id), ['a2', 'a0']);
  const g0 = plagiarismReport(c, R, 'a0'); const g2 = plagiarismReport(c, R, 'a2');
  assert.deepEqual(g0.edges.map((e) => [`${e.provider.id}→${e.receiver.id}`, e.count]), [['a→b', 1]]);
  assert.deepEqual(g2.edges.map((e) => [`${e.provider.id}→${e.receiver.id}`, e.count]), [['a→b', 1], ['a→c', 1]]);
  const a0 = g0.nodes.find((n) => n.student.id === 'a'); const a2 = g2.nodes.find((n) => n.student.id === 'a');
  assert.deepEqual([a0.points, a0.roundTimes, a0.factor], [-4, 1, 2]);
  assert.deepEqual([a2.points, a2.roundTimes, a2.factor], [-8, 2, 4]);
  assert.equal(g2.copiers, 2); assert.equal(g2.providers, 1);
  assert.equal(plagiarismReport(c, R, 'a3').cases.length, 0);
});

test('抄袭与复审不合格叠加', () => {
  const c = cls(); c.assignments[0].reviewFailed = ['b'];
  assert.equal(autoPoints(c, R, 'b').a0, -8);
  assert.equal(autoPoints(c, R, 'a').a0, -4);
  assert.equal(autoPoints(c, { ...R, review: { ...R.review, enabled: false } }, 'b').a0, -4);
});

test('抄袭：提供者不明', () => {
  const c = cls(); const a = c.assignments[1];
  a.statuses.d = M; a.plagiarism = { d: ['?'] };
  assert.equal(autoPoints(c, R, 'd').a1, -4);
  assert.equal(plagiarized(a, 'd'), true);
  assert.equal(plagiarismDesc(c, a, 'd'), '抄袭（提供者不明）');
  const g = plagiarismReport(c, R, 'a1');
  assert.equal(g.cases.length, 1); assert.equal(g.cases[0].unknown, true); assert.equal(g.cases[0].providerText, '提供者不明');
  assert.equal(g.edges.length, 0); assert.deepEqual(g.nodes.map((n) => [n.student.id, n.copied, n.provided]), [['d', 1, 0]]);
  assert.deepEqual(plagiarismAssignments(c).map((x) => x.id), ['a2', 'a1', 'a0']);
  // 汇总：多出一位抄袭者，关系边不变
  const all = plagiarismReport(c, R);
  assert.equal(all.copiers, 3); assert.equal(all.edges.length, 2); assert.equal(all.cases.length, 4);
});
