// 网页导出分析报告：Excel 工作表与安卓 AnalysisExport.kt 一致，PDF 打印版包含各部分
import test from 'node:test';
import assert from 'node:assert/strict';
import { normalizeData, defaultRules } from '../../web/js/model.js';
import { report, examReport } from '../../web/js/analytics.js';
import { analysisSheets, analysisXlsx, analysisHtml } from '../../web/js/reportexport.js';

const DAY = 86400000; const now = Date.UTC(2026, 10, 17);
function cls() {
  const students = ['甲', '乙', '丙', '丁', '戊', '己'].map((name, i) => ({ id: `s${i}`, no: String(i + 1), name, group: i % 2 ? '一组' : '二组' }));
  const st = ['SUBMITTED', 'EXCELLENT', 'MISSING', 'LATE', 'SUBMITTED', 'LEAVE'];
  const assignments = Array.from({ length: 8 }, (_, k) => ({ id: `a${k}`, title: `作业${k + 1}`, subject: k % 2 ? '数学' : '语文', createdAt: now - (8 - k) * DAY,
    statuses: Object.fromEntries(students.map((s, i) => [s.id, st[(i + k) % st.length]])) }));
  const exams = [{ id: 'e1', title: '月考', subject: '数学', date: now - 3 * DAY, fullScore: 100, scores: { s0: 90, s1: 72, s2: 55, s3: 88, s4: 61 }, absent: ['s5'] }];
  return normalizeData({ classes: [{ id: 'c', name: '一班', students, assignments, exams, scores: [{ id: 'm', studentId: 's0', delta: 2, reason: '表扬', time: now - DAY }] }] }).classes[0];
}

test('Excel：工作表与安卓一致', () => {
  const c = cls(); const r = report(c, defaultRules(), { range: 'ALL' }, now); const ex = examReport(c);
  const sh = analysisSheets(c, r, ex, now);
  assert.deepEqual(sh.map((x) => x.name), ['概览', '学生明细', '每次作业', '进步与退步', '各轮与分段', '积分来源与预警', '科目与星期', '小组', '信誉分', '积分', '考试统计', '考试进退与分层', '考试成绩']);
  assert.equal(sh[1].rows.length, 1 + c.students.length);
  assert.equal(sh[2].rows.length, 1 + c.assignments.length);
  assert.equal(sh[12].rows[1].length, 2 + 2 + 2);
  assert.equal(sh[8].rows[0][0].v ?? sh[8].rows[0][0], '学号');
  const blob = analysisXlsx(c, r, ex, now);
  assert.ok(blob.size > 2000);
});

test('PDF：打印版包含各部分', () => {
  const c = cls(); const r = report(c, defaultRules(), { range: 'ALL' }, now);
  const doc = analysisHtml(c, r, examReport(c), now);
  for (const t of ['一班 作业与成绩分析报告', '一、总体情况', '、学生明细', '、考试', '月考', '@page']) assert.ok(doc.includes(t), t);
});
