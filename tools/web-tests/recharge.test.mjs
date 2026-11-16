// node --test tools/web-tests/recharge.test.mjs —— 与安卓 RechargeTest 同一组用例
import test from 'node:test';
import assert from 'node:assert/strict';
import { defaultRules, studentStats, rechargeScores, removeRecharge, normalizeData } from '../../web/js/model.js';

const rules = { ...defaultRules(), escalation: { enabled: false } }; // 旧用例沿用“未交固定扣分”
const fixture = () => ({
  id: 'c1', name: '一班', recharges: [],
  students: [{ id: 's1', no: '01', name: '张三' }, { id: 's2', no: '02', name: '李四' }],
  assignments: [1, 2, 3].map((i) => ({ id: `a${i}`, title: `作业${i}`, createdAt: 1000 * i, statuses: { s1: 'MISSING' }, comments: { s1: '补交中' } })),
  scores: [{ id: 'e1', studentId: 's1', delta: 3, reason: '课堂表现', time: 1500 }, { id: 'e2', studentId: 's1', delta: -1, reason: '迟到', time: 2500 }],
});
const score = (c, id, r = rules) => studentStats(c, r).find((x) => x.student.id === id).score;

test('重置只重置积分，历史流水、作业状态、未交次数都保留', () => {
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

test('重置后的新作业和加减分继续累计；改动重置前的作业不影响当前积分', () => {
  let c = rechargeScores(fixture(), rules, ['s1', 's2'], 50, '', 5000);
  c = { ...c, scores: [...c.scores, { id: 'e3', studentId: 's1', delta: 2, reason: '主动回答', time: 6000 }],
    assignments: [...c.assignments, { id: 'a4', title: '作业4', createdAt: 7000, statuses: { s1: 'EXCELLENT', s2: 'MISSING' }, comments: {} }] };
  assert.equal(score(c, 's1'), 53);
  assert.equal(score(c, 's2'), 48);
  const edited = { ...c, assignments: c.assignments.map((a) => a.id === 'a1' ? { ...a, statuses: { s1: 'EXCELLENT' } } : a) };
  assert.equal(score(edited, 's1'), 53);
});

test('修改计分规则不改变重置后的基准', () => {
  const c = rechargeScores(fixture(), rules, ['s1'], 100, '', 5000);
  assert.equal(score(c, 's1', { ...rules, missing: -10, initial: 0 }), 100);
  assert.equal(score(c, 's2', { ...rules, initial: 0 }), 0);
});

test('最近一次重置生效；删除记录即撤销', () => {
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

test('从某次作业起重置：该作业（含）及之后计入', () => {
  const c = rechargeScores(fixture(), rules, ['s1'], 100, '', 9000, { assignmentId: 'a2' });
  assert.equal(score(c, 's1'), 95);
  assert.equal(c.recharges[0].effectiveAt, 2000);
  assert.equal(c.recharges[0].assignmentTitle, '作业2');
  assert.equal(c.recharges[0].before.s1, 96);
});

test('从某一天起重置；生效更晚的重置优先', () => {
  const byDay = rechargeScores(fixture(), rules, ['s1'], 100, '', 9000, { effectiveAt: 2500 });
  assert.equal(score(byDay, 's1'), 97);
  const both = rechargeScores(byDay, rules, ['s1'], 50, '', 10000, { effectiveAt: 500 });
  assert.equal(score(both, 's1'), 97);
  assert.equal(score(removeRecharge(both, byDay.recharges[0].id), 's1'), 46);
});

import { examDelta } from '../../web/js/model.js';
test('考试计分档与安卓一致，并计入积分、受重置影响', () => {
  assert.equal(examDelta(rules, { scores: { a: 90 }, fullScore: 100 }, 'a'), 2);
  assert.equal(examDelta(rules, { scores: { a: 100 }, fullScore: 100 }, 'a'), 2);
  assert.equal(examDelta(rules, { scores: { a: 85 }, fullScore: 100 }, 'a'), 1);
  assert.equal(examDelta(rules, { scores: { a: 59.5 }, fullScore: 100 }, 'a'), -1);
  assert.equal(examDelta(rules, { scores: { a: 140 }, fullScore: 150 }, 'a'), 2);
  assert.equal(examDelta({ ...rules, exam: { absent: -3 } }, { scores: {}, absent: ['a'], fullScore: 100 }, 'a'), -3);
  assert.equal(examDelta(rules, { scores: { a: 95 }, fullScore: 100, autoScore: false }, 'a'), 0);
  let c = { ...fixture(), exams: [{ id: 'e1', title: '期中', date: 5000, fullScore: 100, scores: { s1: 95, s2: 50 }, absent: [] }] };
  assert.equal(score(c, 's1'), 98); // 96 + 2
  assert.equal(score(c, 's2'), 99);
  c = rechargeScores(c, rules, ['s1'], 100, '', 9000);
  assert.equal(score(c, 's1'), 100);
});

import { autoResetDue, applyAutoReset } from '../../web/js/model.js';
import { merge } from '../../web/js/cloud.js';
test('每月定期重置：只补开启后的月份，id 固定，撤销后不再补回（与安卓 AutoReset.kt 一致）', () => {
  const at = (y, m, d) => new Date(y, m - 1, d).getTime();
  const ar = { enabled: true, target: 100, day: 1, note: '每月定期重置', since: at(2026, 9, 15), skipped: [] };
  assert.deepEqual(autoResetDue(ar, at(2026, 11, 20)).map((x) => x[0]), ['202610', '202611']);
  let c = { ...fixture(), autoReset: ar };
  c = applyAutoReset(c, rules, at(2026, 11, 2));
  assert.deepEqual(c.recharges.map((r) => r.id), ['auto-202610', 'auto-202611']);
  assert.equal(applyAutoReset(c, rules, at(2026, 11, 2)), c);
  assert.equal(score(c, 's1'), 100);
  c = removeRecharge(c, 'auto-202611');
  assert.deepEqual(c.autoReset.skipped, ['202611']);
  assert.deepEqual(applyAutoReset(c, rules, at(2026, 11, 3)).recharges.map((r) => r.id), ['auto-202610']);
  // 合并：另一端还留着已撤销的月份 → 去掉
  const remote = { version: 1, classes: [{ ...c, autoReset: { ...ar }, recharges: [...c.recharges, { ...c.recharges[0], id: 'auto-202611' }] }], settings: {} };
  const local = { version: 1, classes: [c], settings: {} };
  assert.deepEqual(merge(local, remote).classes[0].recharges.map((r) => r.id), ['auto-202610']);
});

import { autoPoints } from '../../web/js/model.js';
test('未交连续递增扣分（与安卓 EscalationTest 一致）', () => {
  const r = defaultRules();
  const mk = (...m) => ({ id: 'c', name: '一班', recharges: [], scores: [], students: [{ id: 's1', no: '01', name: '张三' }], assignments: m.map((x, i) => ({ id: `a${i}`, title: `作业${i}`, createdAt: (i + 1) * 1000, statuses: { s1: x } })) });
  const pts = (c, rr = r) => c.assignments.map((a) => autoPoints(c, rr, 's1')[a.id]);
  assert.deepEqual(pts(mk('MISSING', 'MISSING', 'MISSING', 'MISSING')), [-2, -4, -8, -16]);
  assert.deepEqual(pts(mk('MISSING', 'LEAVE', 'MISSING')), [-2, 0, -4]);
  assert.deepEqual(pts(mk('MISSING', 'MISSING', 'SUBMITTED', 'MISSING')), [-2, -4, 0, -2]);
  const many = mk('MISSING', 'SUBMITTED', 'MISSING', 'SUBMITTED', 'MISSING', 'SUBMITTED', 'MISSING', 'SUBMITTED', 'MISSING', 'SUBMITTED', 'MISSING', 'SUBMITTED', 'MISSING', 'MISSING', 'MISSING', 'MISSING');
  assert.deepEqual(pts(many).filter((_, i) => many.assignments[i].statuses.s1 === 'MISSING'), [-2, -2, -2, -2, -2, -2, -3, -9, -27, -30]);
  const reset = rechargeScores(many, r, ['s1'], 100, '', 99000, { assignmentId: 'a14' });
  const p = autoPoints(reset, r, 's1');
  assert.equal(p.a13, -9); assert.equal(p.a14, -2); assert.equal(p.a15, -4);
  assert.equal(studentStats(reset, r)[0].score, 94);
  assert.deepEqual(pts(mk('MISSING', 'MISSING', 'MISSING'), { ...r, escalation: { ...r.escalation, cap: 5 } }), [-2, -4, -5]);
  assert.deepEqual(pts(mk('MISSING', 'MISSING'), { ...r, escalation: { enabled: false } }), [-2, -2]);
});
