// 组长奖惩：与安卓 LeaderTest、iPad 同一组用例
import test from 'node:test';
import assert from 'node:assert/strict';
import { defaultRules, defaultLeader, leaderPeriods, studentStats, normalizeData, rechargeScores } from '../../web/js/model.js';

const DAY = 86400000;
function cls() {
  const ids = ['a1', 'a2', 'a3', 'b1', 'b2', 'b3', 'c1', 'c2', 'c3', 'x'];
  const students = ids.map((id) => ({ id, no: id, name: id, group: { a: '一组', b: '二组', c: '三组' }[id[0]] || '' }));
  const assignments = [0, 1, 2, 3, 4, 5].map((k) => {
    const m = Object.fromEntries(ids.map((id) => [id, 'SUBMITTED']));
    if (k === 0) m.b2 = 'MISSING';
    if (k <= 1) { m.c2 = 'MISSING'; m.c3 = 'MISSING'; }
    return { id: `a${k}`, title: `作业${k}`, createdAt: (k + 1) * DAY, statuses: m };
  });
  return normalizeData({ classes: [{ id: 'c', name: '一班', students, assignments, groupLeaders: { 一组: 'a1', 二组: 'b1', 三组: 'c1' } }] }).classes[0];
}
const each = { ...defaultRules(), leader: { ...defaultLeader(), period: 'assignment' } };

test('按单次作业：零扣分、平均、超 2 倍、名次、连续、上限', () => {
  const lp = leaderPeriods(cls(), each);
  const pts = (id) => lp[id].map((p) => p.points);
  assert.deepEqual(pts('a1'), [4, 4, 5, 5, 5, 5]);
  assert.equal(pts('b1')[0], 1); assert.equal(pts('b1')[1], 5);
  assert.deepEqual(pts('c1').slice(0, 3), [-3, -5, 4]);
  assert.equal(lp.a2, undefined); assert.equal(lp.x, undefined);
  const on = studentStats(cls(), each).find((s) => s.student.id === 'a1').score;
  const off = studentStats(cls(), { ...each, leader: { ...defaultLeader(), enabled: false } }).find((s) => s.student.id === 'a1').score;
  assert.equal(on - off, 28);
});

test('按月；组长重置后重新算', () => {
  const lp = leaderPeriods(cls(), { ...defaultRules(), leader: { ...defaultLeader(), period: 'month' } });
  assert.equal(lp.a1.length, 1); assert.equal(lp.a1[0].points, 4); assert.equal(lp.c1[0].points, -4);
  const c0 = cls();
  const c = rechargeScores(c0, each, ['a1'], 100, '', 3 * DAY + 1, { effectiveAt: c0.assignments[3].createdAt, assignmentId: 'a3' });
  assert.deepEqual(leaderPeriods(c, each).a1.map((p) => p.points), [4, 4, 5, 3, 4, 5]);
  assert.equal(studentStats(c, each).find((s) => s.student.id === 'a1').score, 112);
});
