// 作业复审：与安卓 ReviewTest、iPad 同一组用例
import test from 'node:test';
import assert from 'node:assert/strict';
import { defaultRules, autoPoints, studentStats, normalizeData } from '../../web/js/model.js';

const mk = (m, failed = []) => normalizeData({ classes: [{ id: 'c', name: '一班', students: [{ id: 's', no: '1', name: '甲' }],
  assignments: m.map((x, i) => ({ id: `a${i}`, title: `作业${i}`, createdAt: (i + 1) * 1000, statuses: { s: x }, reviewFailed: failed.includes(i) ? ['s'] : [] })) }] }).classes[0];
const pts = (c, r = defaultRules()) => { const p = autoPoints(c, r, 's'); return c.assignments.map((a) => p[a.id] ?? 0); };
const M = 'MISSING';

test('复审不合格翻倍，并算一次未交', () => {
  assert.deepEqual(pts(mk([M, M], [0])), [-4, -2]);
  assert.equal(pts(mk([M, M, M], [2]))[2], -8);
  assert.equal(pts(mk(Array(6).fill(M), [5]))[5], -60);
  const st = studentStats(mk(['SUBMITTED', M], [1]), defaultRules())[0];
  assert.equal(st.missing, 1); assert.equal(st.score, 96);
});

test('关闭复审、状态不是未交、没复审：按普通计分', () => {
  assert.deepEqual(pts(mk([M], [0]), { ...defaultRules(), review: { enabled: false, multiplier: 2 } }), [-2]);
  assert.deepEqual(pts(mk(['SUBMITTED'], [0])), [0]);
  assert.deepEqual(pts(mk([M])), [-2]);
  assert.deepEqual(pts(mk([M], [0]), { ...defaultRules(), escalation: { enabled: false }, review: { enabled: true, multiplier: 3 } }), [-6]);
});
