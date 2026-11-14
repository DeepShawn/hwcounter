// node --test tools/web-tests/recharge.test.mjs —— 与安卓 RechargeTest 同一组用例
import test from 'node:test';
import assert from 'node:assert/strict';
import { defaultRules, studentStats, rechargeScores, removeRecharge, normalizeData } from '../../web/js/model.js';

const rules = defaultRules();
const fixture = () => ({
  id: 'c1', name: '一班', recharges: [],
  students: [{ id: 's1', no: '01', name: '张三' }, { id: 's2', no: '02', name: '李四' }],
  assignments: [1, 2, 3].map((i) => ({ id: `a${i}`, title: `作业${i}`, createdAt: 1000 * i, statuses: { s1: 'MISSING' }, comments: { s1: '补交中' } })),
  scores: [{ id: 'e1', studentId: 's1', delta: 3, reason: '课堂表现', time: 1500 }, { id: 'e2', studentId: 's1', delta: -1, reason: '迟到', time: 2500 }],
});
const score = (c, id, r = rules) => studentStats(c, r).find((x) => x.student.id === id).score;

test('充值只重置积分，历史流水、作业状态、未交次数都保留', () => {
  const before = fixture();
  assert.equal(score(before, 's1'), 96);
  const c = rechargeScores(before, rules, ['s1'], 100, '', 5000);
  assert.equal(score(c, 's1'), 100);
  assert.equal(score(c, 's2'), 100);
  assert.deepEqual(c.scores, before.scores);
  assert.deepEqual(c.assignments, before.assignments);
  assert.equal(studentStats(c, rules)[0].missing, 3);
  assert.equal(c.recharges[0].before.s1, 96);
  assert.deepEqual(c.recharges[0].studentIds, ['s1']);
});

test('充值后的新作业和加减分继续累计；改动充值前的作业不影响当前积分', () => {
  let c = rechargeScores(fixture(), rules, ['s1', 's2'], 50, '', 5000);
  c = { ...c, scores: [...c.scores, { id: 'e3', studentId: 's1', delta: 2, reason: '主动回答', time: 6000 }],
    assignments: [...c.assignments, { id: 'a4', title: '作业4', createdAt: 7000, statuses: { s1: 'EXCELLENT', s2: 'MISSING' }, comments: {} }] };
  assert.equal(score(c, 's1'), 53);
  assert.equal(score(c, 's2'), 48);
  const edited = { ...c, assignments: c.assignments.map((a) => a.id === 'a1' ? { ...a, statuses: { s1: 'EXCELLENT' } } : a) };
  assert.equal(score(edited, 's1'), 53);
});

test('修改计分规则不改变充值后的基准', () => {
  const c = rechargeScores(fixture(), rules, ['s1'], 100, '', 5000);
  assert.equal(score(c, 's1', { ...rules, missing: -10, initial: 0 }), 100);
  assert.equal(score(c, 's2', { ...rules, initial: 0 }), 0);
});

test('最近一次充值生效；删除记录即撤销', () => {
  let c = rechargeScores(fixture(), rules, ['s1'], 100, '', 5000);
  c = { ...c, scores: [...c.scores, { id: 'e4', studentId: 's1', delta: -4, reason: '违纪', time: 6000 }] };
  c = rechargeScores(c, rules, ['s1'], 80, '', 7000);
  assert.equal(score(c, 's1'), 80);
  assert.equal(c.recharges.at(-1).before.s1, 96);
  const undone = removeRecharge(c, c.recharges.at(-1).id);
  assert.equal(score(undone, 's1'), 96);
  assert.equal(score(removeRecharge(undone, undone.recharges[0].id), 's1'), 92);
});

test('旧数据没有 recharges 字段也能读取', () => {
  const d = normalizeData({ classes: [{ id: 'c', name: '一班', students: [{ id: 's1', no: '1', name: '张三' }] }] });
  assert.deepEqual(d.classes[0].recharges, []);
});

test('从某次作业起充值：该作业（含）及之后计入', () => {
  const c = rechargeScores(fixture(), rules, ['s1'], 100, '', 9000, { assignmentId: 'a2' });
  assert.equal(score(c, 's1'), 95);
  assert.equal(c.recharges[0].effectiveAt, 2000);
  assert.equal(c.recharges[0].assignmentTitle, '作业2');
  assert.equal(c.recharges[0].before.s1, 96);
});

test('从某一天起充值；生效更晚的充值优先', () => {
  const byDay = rechargeScores(fixture(), rules, ['s1'], 100, '', 9000, { effectiveAt: 2500 });
  assert.equal(score(byDay, 's1'), 97);
  const both = rechargeScores(byDay, rules, ['s1'], 50, '', 10000, { effectiveAt: 500 });
  assert.equal(score(both, 's1'), 97);
  assert.equal(score(removeRecharge(both, byDay.recharges[0].id), 's1'), 46);
});
