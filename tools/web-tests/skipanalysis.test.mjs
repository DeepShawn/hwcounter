// 考试“不计入分析”：不参加考试分析、信誉分、关注度等统计；成绩和本场的加减积分照常（与安卓 analysisExams 一致）
import test from 'node:test';
import assert from 'node:assert/strict';
import { defaultRules, creditAll, studentStats, analysisExams, normalizeData } from '../../web/js/model.js';
import { examReport, attention } from '../../web/js/analytics.js';
import { setSkipAnalysis, setSkippedExams } from '../../web/js/ops.js';

const students = ['a', 'b', 'c'].map((id, i) => ({ id, no: `0${i + 1}`, name: `同学${id}`, group: '' }));
const assignments = [1, 2, 3].map((k) => ({ id: `h${k}`, title: `作业${k}`, createdAt: k * 1000, statuses: { a: 'SUBMITTED', b: 'SUBMITTED', c: 'MISSING' }, reviewFailed: [], plagiarism: {} }));
const exam = (id, date, sc, skip = false) => ({ id, title: id, subject: '数学', date, fullScore: 100, scores: sc, absent: [], autoScore: true, ...(skip ? { skipAnalysis: true } : {}) });
const base = { id: 'c1', name: '班', students, assignments, scores: [], recharges: [],
  exams: [exam('e1', 1500, { a: 95, b: 70, c: 50 }), exam('e2', 2500, { a: 20, b: 99, c: 99 })] };

test('不计入分析的考试不参加考试分析', () => {
  const c = setSkipAnalysis(base, ['e2'], true);
  assert.deepEqual(analysisExams(c).map((e) => e.id), ['e1']);
  assert.deepEqual(examReport(c).exams.map((e) => e.id), ['e1']);
  assert.deepEqual(examReport(base).exams.map((e) => e.id), ['e1', 'e2']);
});

test('信誉分、关注度的考试部分只看计入分析的考试', () => {
  const c = setSkipAnalysis(base, ['e2'], true);
  const only1 = { ...base, exams: [base.exams[0]] };
  const pick = (l) => Object.fromEntries(l.map((r) => [r.student.id, r.index]));
  assert.deepEqual(pick(creditAll(c, 1e12)), pick(creditAll(only1, 1e12)));
  assert.notDeepEqual(pick(creditAll(base, 1e12)), pick(creditAll(only1, 1e12)));
  const att = (x) => Object.fromEntries(attention(x, defaultRules(), 1e12).map((r) => [r.student.id, r.index]));
  assert.deepEqual(att(c), att(only1));
});

test('积分照常：本场仍按分数档加减分', () => {
  const rules = defaultRules();
  const sc = (x) => Object.fromEntries(studentStats(x, rules).map((s) => [s.student.id, s.score]));
  assert.deepEqual(sc(setSkipAnalysis(base, ['e2'], true)), sc(base));
});

test('一次选好多场；旧数据没有这个字段时都计入', () => {
  const c = setSkippedExams(base, ['e1', 'e2']);
  assert.equal(analysisExams(c).length, 0);
  assert.equal(analysisExams(setSkippedExams(c, [])).length, 2);
  const d = normalizeData({ classes: [base] });
  assert.equal(analysisExams(d.classes[0]).length, 2);
});
