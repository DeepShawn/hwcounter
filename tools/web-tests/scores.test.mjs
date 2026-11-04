// node --test tools/web-tests/scores.test.mjs
import test from 'node:test';
import assert from 'node:assert/strict';
import { defaultRules, summary, studentStats, compareStudentNumbers, addStudentScores, resetStudentScores } from '../../web/js/model.js';

const rules = defaultRules();

test('学号自然排序处理数字、前导零、字母、超长学号和空值，不修改原名单', () => {
  for (const [input, expected] of [
    [['10', '2', '1'], ['1', '2', '10']],
    [['010', '2', '001'], ['001', '2', '010']],
    [['A10', 'a2', 'A1'], ['A1', 'a2', 'A10']],
    [['90071992547409931', '90071992547409930', '2'], ['2', '90071992547409930', '90071992547409931']],
    [['', '10', ' 2 '], [' 2 ', '10', '']],
  ]) {
    const original = [...input];
    assert.deepEqual([...input].sort(compareStudentNumbers), expected);
    assert.deepEqual(input, original);
  }
  assert.equal(compareStudentNumbers('02', '2'), 0);
});
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

test('批量加减分去重并限制在当前班级，不改作业登记及未交次数', () => {
  const c = fixture();
  const scored = addStudentScores(c, ['s1', 's1', 'other-class'], -1.5, '作业：练习册');
  assert.equal(scored.scores.length, c.scores.length + 1);
  assert.equal(studentStats(scored, rules)[0].score, 94.5);
  assert.equal(studentStats(scored, rules)[0].missing, 3);
  assert.equal(studentStats(scored, rules)[1].score, 100);
  assert.equal(scored.assignments, c.assignments);
  assert.equal(addStudentScores(c, ['s1'], 0, '无效'), c);
});

test('统计缓存能复用重复读取，并在作业、名单或规则改变后更新', () => {
  const c = fixture();
  const stats = studentStats(c, rules); const first = summary(c, c.assignments[0]);
  assert.equal(studentStats(c, rules), stats);
  assert.equal(summary(c, c.assignments[0]), first);
  const assignment = { ...c.assignments[0], statuses: { ...c.assignments[0].statuses, s1: 'SUBMITTED' } };
  const changed = { ...c, assignments: [assignment, ...c.assignments.slice(1)] };
  assert.equal(summary(changed, assignment).counts.MISSING, 0);
  assert.equal(studentStats(changed, rules)[0].missing, 2);
  assert.equal(studentStats(c, { ...rules, initial: 0 })[0].score, -4);
  const roster = { ...c, students: [...c.students, { id: 's3', name: '新同学', no: '03' }] };
  assert.equal(summary(roster, c.assignments[0]).counts.NONE, 1);
  assert.equal(studentStats(roster, rules).length, 3);
});
