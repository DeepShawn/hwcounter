// 间断未交平衡：与安卓 GapBalanceTest.kt、iPad Tests/main.swift 同一组用例
import test from 'node:test';
import assert from 'node:assert/strict';
import { defaultRules, defaultEscalation, autoPoints } from '../../web/js/model.js';

const rules = { ...defaultRules(), streakBonus: { enabled: false } };
const cls = (...marks) => ({ id: 'c', name: '一班', students: [{ id: 's1', no: '01', name: '甲' }], scores: [], recharges: [],
  assignments: marks.map((m, k) => ({ id: `a${k}`, title: `作业${k}`, createdAt: (k + 1) * 1000, statuses: { s1: m } })) });
const misses = (c, r = rules) => { const p = autoPoints(c, r, 's1'); return c.assignments.filter((a) => a.statuses.s1 === 'MISSING').map((a) => p[a.id]); };
const esc = (o) => ({ ...rules, escalation: { ...defaultEscalation(), ...o } });
const sum = (l) => l.reduce((x, y) => x + y, 0);
const M = 'MISSING', S = 'SUBMITTED', L = 'LEAVE';

test('默认开启、看最近 8 次', () => {
  assert.equal(defaultEscalation().gapBalance, true);
  assert.equal(defaultEscalation().gapWindow, 8);
});

test('隔一次不交一次也逐次加重', () => {
  const c = cls(M, S, M, S, M, S, M, S, M, S);
  assert.deepEqual(misses(c), [-2, -2, -4, -8, -8]);
  assert.deepEqual(misses(c, esc({ gapBalance: false })), [-2, -2, -2, -2, -2]);
});

test('未交率更高的间断未交不会比连续未交扣得少', () => {
  const a = cls(M, M, M, M, S, S, S, S, S, S, S, S);
  const b = cls(M, S, M, S, M, S, M, S, M, S, M, S);
  assert.equal(sum(misses(a)), -16);
  assert.ok(sum(misses(b)) < sum(misses(a)));
  const off = esc({ gapBalance: false });
  assert.ok(sum(misses(b, off)) > sum(misses(a, off)));
});

test('窗口滑动；请假不算记录', () => {
  assert.deepEqual(misses(cls(M, S, M, S, S, S, M), esc({ gapWindow: 3 })), [-2, -2, -2]);
  assert.deepEqual(misses(cls(M, S, L, L, L, M), esc({ mode: 1, gapWindow: 3 })), [-2, -4]);
});

test('积分重置后窗口清空', () => {
  const c = cls(M, S, M, S, M);
  assert.equal(autoPoints(c, rules, 's1').a4, -4);
  c.recharges = [{ id: 'r1', time: 4500, effectiveAt: 5000, score: 100, studentIds: ['s1'] }];
  assert.equal(autoPoints(c, rules, 's1').a4, -2);
});
