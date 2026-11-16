// 小组对抗排名与整组加减分：与安卓 GroupRankTest、iPad 同一组用例
import test from 'node:test';
import assert from 'node:assert/strict';
import { defaultRules, defaultGroupRank, groupRankPeriods, studentStats, normalizeData } from '../../web/js/model.js';

const DAY = 86400000;
function cls() {
  const ids = ['a1', 'a2', 'b1', 'b2', 'c1', 'c2', 'x'];
  const students = ids.map((id) => ({ id, no: id, name: id, group: { a: '一组', b: '二组', c: '三组' }[id[0]] || '' }));
  const assignments = [0, 1].map((k) => {
    const m = Object.fromEntries(ids.map((id) => [id, 'SUBMITTED']));
    m.a1 = 'EXCELLENT'; m.c2 = 'MISSING'; if (k === 1) m.b2 = 'LATE';
    return { id: `a${k}`, title: `作业${k}`, createdAt: (k + 1) * DAY, statuses: m };
  });
  return normalizeData({ classes: [{ id: 'c', name: '一班', students, assignments }] }).classes[0];
}
const each = { ...defaultRules(), leader: { enabled: false }, groupRank: { ...defaultGroupRank(), period: 'assignment' } };

test('排名与整组加减分', () => {
  const ps = groupRankPeriods(cls(), each);
  assert.equal(ps.length, 2);
  const p0 = ps[0].standings;
  assert.deepEqual(p0.map((s) => s.group), ['一组', '二组', '三组']);
  assert.deepEqual(p0.map((s) => s.rank), [1, 2, 3]);
  assert.deepEqual(p0.map((s) => s.points), [2, 1, -1]);
  assert.equal(p0[0].score, Math.round((0.5 + 0.25 + 0.075) / 0.9 * 10000) / 10000);
  const on = Object.fromEntries(studentStats(cls(), each).map((s) => [s.student.id, s.score]));
  const off = Object.fromEntries(studentStats(cls(), { ...each, groupRank: { enabled: false } }).map((s) => [s.student.id, s.score]));
  assert.equal(on.a2 - off.a2, 4); assert.equal(on.c1 - off.c1, -2); assert.equal(on.x - off.x, 0);
});

test('并列与按月', () => {
  const st = ['a1', 'a2', 'b1', 'b2'].map((id) => ({ id, no: id, name: id, group: id[0] === 'a' ? '一组' : '二组' }));
  const c = normalizeData({ classes: [{ id: 'c', name: '班', students: st, assignments: [{ id: 'a0', title: '作业', createdAt: DAY, statuses: Object.fromEntries(st.map((s) => [s.id, 'SUBMITTED'])) }] }] }).classes[0];
  const p = groupRankPeriods(c, each)[0].standings;
  assert.deepEqual(p.map((s) => s.rank), [1, 1]); assert.deepEqual(p.map((s) => s.points), [2, 2]);
  assert.equal(groupRankPeriods(cls(), { ...each, groupRank: { ...defaultGroupRank(), period: 'month' } }).length, 1);
});
