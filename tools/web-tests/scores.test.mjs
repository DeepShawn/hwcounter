// node --test tools/web-tests/scores.test.mjs
import test from 'node:test';
import assert from 'node:assert/strict';
import { defaultRules, studentStats, resetStudentScores } from '../../web/js/model.js';

const rules = defaultRules();
const fixture = () => ({
  id: 'c1', name: '一班',
  students: [{ id: 's1', no: '01', name: '张三' }, { id: 's2', no: '02', name: '李四' }],
  assignments: [
    { id: 'a1', statuses: { s1: 'MISSING', s2: 'EXCELLENT' }, comments: { s1: '补交中' } },
    { id: 'a2', statuses: { s1: 'MISSING', s2: 'LATE' }, comments: {} },
    { id: 'a3', statuses: { s1: 'MISSING', s2: 'LEAVE' }, comments: {} },
  ],
  scores: [{ id: 'old1', studentId: 's1', delta: 3, reason: '课堂表现', time: 1 }, { id: 'old2', studentId: 's1', delta: -1, reason: '迟到', time: 2 }],
});

test('基准100改为0：96变为-4，保留手动加减分和三次未交', () => {
  const c = fixture(); const original = structuredClone(c);
  assert.equal(studentStats(c, rules)[0].score, 96);
  const st = studentStats(c, { ...rules, initial: 0 })[0];
  assert.equal(st.score, -4); assert.equal(st.missing, 3);
  assert.deepEqual(c, original);
});

test('只重置选中学生，去重并忽略其它班级的ID，原有流水、备注和未交次数保留', () => {
  const c = fixture(); const original = structuredClone(c);
  const reset = resetStudentScores(c, rules, ['s1', 's1', 'other-class-student'], 0);
  assert.deepEqual(studentStats(reset, rules).map((s) => s.score), [0, 100]);
  assert.equal(studentStats(reset, rules)[0].missing, 3);
  assert.deepEqual(reset.assignments, original.assignments);
  assert.deepEqual(reset.scores.slice(0, c.scores.length), original.scores);
  assert.equal(reset.scores.length, c.scores.length + 1);
  assert.equal(reset.scores.at(-1).delta, -96);
  assert.equal(reset.scores.at(-1).reason, '重置分数至 0');
  assert.deepEqual(c, original);
});

test('全班重置到任意基准，记录ID各不相同，可序列化到现有备份格式', () => {
  const c = fixture(); const reset = resetStudentScores(c, rules, ['s1', 's2'], 25.5);
  assert.deepEqual(studentStats(reset, rules).map((s) => s.score), [25.5, 25.5]);
  assert.equal(new Set(reset.scores.map((e) => e.id)).size, reset.scores.length);
  assert.deepEqual(studentStats(JSON.parse(JSON.stringify(reset)), rules), studentStats(reset, rules));
});

test('重置后新增手动分数及新作业继续累计，改变基准仍生效', () => {
  let c = resetStudentScores(fixture(), rules, ['s1'], 0);
  c = { ...c, scores: [...c.scores, { id: 'new', studentId: 's1', delta: 2, reason: '进步' }], assignments: [...c.assignments, { id: 'a4', statuses: { s1: 'MISSING' } }] };
  assert.equal(studentStats(c, rules)[0].score, 0);
  assert.equal(studentStats(c, rules)[0].missing, 4);
  assert.equal(studentStats(c, { ...rules, initial: 110 })[0].score, 10);
});

test('重复重置到相同分数、空选择、无效目标均不新增记录', () => {
  const c = fixture(); const reset = resetStudentScores(c, rules, ['s1'], -5);
  assert.equal(studentStats(reset, rules)[0].score, -5);
  assert.equal(resetStudentScores(reset, rules, ['s1'], -5), reset);
  assert.equal(resetStudentScores(c, rules, [], 0), c);
  assert.equal(resetStudentScores(c, rules, ['missing'], 0), c);
  for (const target of [NaN, Infinity, -Infinity]) assert.equal(resetStudentScores(c, rules, ['s1'], target), c);
});
