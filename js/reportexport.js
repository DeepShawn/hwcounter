// 网页端导出分析报告：多表 Excel（与安卓 AnalysisExport.kt 同样的工作表）和排版好的 PDF（浏览器打印 → 存为 PDF）。
// 纯函数生成内容，界面只负责触发下载 / 打印。
import { xlsx, ST } from './xlsx.js';
import { fmtNum, signed } from './model.js';
import { rangeTitle, fmtDay, examRanks, difficultyText, discriminationText, creditLines } from './analytics.js';

const pc = (x) => Math.round(x * 1000) / 10;
const H = (...t) => t.map((v) => ({ v, s: ST.HEADER }));
const T = (v) => [{ v, s: ST.TITLE }];
const DAYS = ['周一', '周二', '周三', '周四', '周五', '周六', '周日'];

/** 多表 Excel 分析报告（工作表与安卓一致） */
export function analysisSheets(c, r, exam, now = Date.now()) {
  const k = r.kpi; const p = r.previous; const sheets = [];
  const ov = [T(`${c.name} 作业与成绩分析报告（${rangeTitle(r)}）`), ['范围', r.window.label], ['生成日期', fmtDay(now)], [], H('指标', '本时段', '上一时段', '说明')];
  const line = (n, v, pv, d) => ov.push([n, v, pv ?? '', d]);
  line('作业次数', k.assignments, p?.assignments, p ? `对比：${r.window.prevLabel}` : '');
  line('应交人次', k.expected, p?.expected, '去掉请假');
  line('交齐率 %', pc(k.handInRate), p && pc(p.handInRate), '已交 + 优秀 + 迟交 / 应交');
  line('按时率 %', pc(k.onTimeRate), p && pc(p.onTimeRate), '已交 + 优秀 / 应交');
  line('优秀率 %', pc(k.excellentRate), p && pc(p.excellentRate), '');
  line('缺交率 %', pc(k.missingRate), p && pc(p.missingRate), '');
  line('迟交率 %', pc(k.lateRate), p && pc(p.lateRate), '');
  line('全员交齐次数', k.perfect, p?.perfect, `最长连续 ${r.longestPerfectRun} 次`);
  line('未登记人次', k.unregistered, p?.unregistered, '大于 0 说明统计可能偏低');
  line('全班平均积分', fmtNum(r.avgScore), null, r.range !== 'ALL' ? `本时段变化 ${signed(r.avgScoreDelta)}` : '');
  line('交齐率趋势（每次）', `${(r.slope * 100).toFixed(2)} 个百分点`, null, '线性回归斜率，正数为上升');
  if (r.examCorrelation != null) line('作业与考试相关系数', r.examCorrelation.toFixed(2), null, '0.3 以上为正相关');
  line('学生交齐率中位数 %', pc(r.rateMedian), null, `标准差 ${pc(r.rateStd)} 个百分点，越大越两极分化`);
  r.tiers.forEach((t) => line(`分层：${t.name}`, t.students.length, null, t.desc));
  ov.push([], H('主要发现'));
  r.insights.forEach((s, i) => ov.push([`${i + 1}. ${s.text}`]));
  sheets.push({ name: '概览', rows: ov, widths: [22, 16, 16, 60], freeze: false });

  sheets.push({ name: '学生明细', widths: [12, 10, 8, 8, 8, 8, 8, 8, 8, 10, 10, 10, 12, 8, 10], rows: [
    H('学号', '姓名', '小组', '应交', '已交', '按时', '优秀', '迟交', '缺交', '交齐率%', '按时率%', '连续未交', '最长连续未交', '积分', '本时段变化'),
    ...[...r.students].sort((a, b) => a.rate - b.rate).map((s) => [s.student.no, s.student.name, s.student.group || '', s.expected, s.handedIn, s.onTime, s.excellent, s.late, s.missing,
      { v: pc(s.rate), s: s.rate < 0.7 ? ST.RED : s.rate >= 0.999 ? ST.GREEN : 0 }, pc(s.onTimeRate), s.missingStreak, s.longestMissing, s.score, { v: s.scoreDelta, s: s.scoreDelta < 0 ? ST.RED : 0 }]),
  ] });

  sheets.push({ name: '每次作业', widths: [14, 8, 20, 8, 8, 8, 8, 8, 8, 8, 10], rows: [
    H('日期', '科目', '作业', '应交', '已交', '优秀', '迟交', '未交', '请假', '未登记', '交齐率%'),
    ...r.points.map(({ assignment: a, summary: s }) => [fmtDay(a.createdAt), a.subject || '', a.title, s.expected, s.counts.SUBMITTED, s.counts.EXCELLENT, s.counts.LATE, s.counts.MISSING, s.counts.LEAVE, s.counts.NONE, { v: pc(s.rate), s: s.rate >= 0.999 ? ST.GREEN : 0 }]),
  ] });

  const pg = r.progress;
  if (pg) {
    const stars = new Set(pg.stars.map((x) => x.student.id)); const watch = new Set(pg.watch.map((x) => x.student.id));
    sheets.push({ name: '进步与退步', widths: [12, 10, 10, 12, 14, 12, 14, 12, 12, 8, 50], freeze: false, rows: [
      T(`比较方式：${pg.basis}`),
      H('学号', '姓名', '类别', '以往交齐率%', '重置后交齐率%', '以往按时率%', '重置后按时率%', '以往考试%', '重置后考试%', '指数', '主要变化'),
      ...[...pg.all].sort((a, b) => b.index - a.index).map((sp) => {
        const kind = stars.has(sp.student.id) ? '进步之星' : watch.has(sp.student.id) ? '退步关注' : '';
        const st = kind === '进步之星' ? ST.GREEN : kind === '退步关注' ? ST.RED : 0;
        return [sp.student.no, sp.student.name, { v: kind, s: st }, pc(sp.before.rate), pc(sp.after.rate), pc(sp.before.onTimeRate), pc(sp.after.onTimeRate),
          sp.before.examPct != null ? pc(sp.before.examPct) : '', sp.after.examPct != null ? pc(sp.after.examPct) : '', { v: Math.round(sp.index * 100), s: st }, sp.reasons.join('，')];
      }),
    ] });
  }

  if (r.rounds.length || r.buckets.length) {
    const rows = [];
    if (r.rounds.length) {
      rows.push(H('轮次', '起始', '作业', '交齐率%', '按时率%', '优秀率%', '缺交率%', '考试得分率%'));
      r.rounds.forEach((o) => rows.push([o.label, o.start != null ? fmtDay(o.start) : '', o.kpi.assignments, pc(o.kpi.handInRate), pc(o.kpi.onTimeRate), pc(o.kpi.excellentRate), pc(o.kpi.missingRate), o.examPct != null ? pc(o.examPct) : '']));
      rows.push([]);
    }
    if (r.buckets.length) {
      rows.push(H(`按${r.bucketUnit}`, '作业', '交齐率%', '按时率%', '缺交人次'));
      r.buckets.forEach((b) => rows.push([b.label, b.count, pc(b.rate), pc(b.onTimeRate), b.missing]));
    }
    sheets.push({ name: '各轮与分段', rows, widths: [18, 14, 8, 10, 10, 10, 10, 12] });
  }

  const src = r.sources; const sr = [H('积分来源', '合计'), ['作业自动计分', src.homework], ['手动加分', src.manualPlus], ['手动扣分', src.manualMinus], ['考试计分', src.exam], []];
  if (r.reasons.length) { sr.push(H('加减分原因', '次数', '涉及人数', '合计')); r.reasons.forEach((o) => sr.push([o.reason, o.count, o.people, o.total])); sr.push([]); }
  if (r.risk.length) { sr.push(H('缺交风险', '学号', '风险%', '', '依据')); r.risk.forEach((o) => sr.push([o.student.name, o.student.no, pc(o.prob), '', o.why])); sr.push([]); }
  if (r.weakSubjects.length) { sr.push(H('偏科', '科目', '该科交齐率%', '整体交齐率%')); r.weakSubjects.forEach((o) => sr.push([o.student.name, o.subject, pc(o.subjectRate), pc(o.overallRate)])); sr.push([]); }
  if (r.streakers.length) { sr.push(H('连续交齐', '次数')); r.streakers.forEach((o) => sr.push([o.student.name, o.handStreak])); }
  sheets.push({ name: '积分来源与预警', rows: sr, widths: [16, 12, 12, 12, 40] });

  sheets.push({ name: '科目与星期', widths: [12, 10, 12, 12, 10], rows: [
    H('科目', '次数', '交齐率%', '按时率%', '缺交'), ...r.subjects.map((s) => [s.subject, s.count, pc(s.rate), pc(s.onTimeRate), s.missing]), [],
    H('星期', '平均交齐率%'), ...r.weekday.map((v, i) => (v == null ? null : [DAYS[i], pc(v)])).filter(Boolean),
  ] });
  sheets.push({ name: '小组', widths: [12, 10, 8, 12, 8, 12, 12, 16], rows: [
    H('小组', '组长', '人数', '交齐率%', '缺交', '平均积分', '积分变化', '考试平均得分率%'),
    ...r.groupStats.map((g) => [g.name, g.leader?.name || '', g.size, pc(g.rate), g.missing, fmtNum(g.avgScore), fmtNum(g.scoreDelta), g.examPct != null ? pc(g.examPct) : '']),
  ] });
  if (r.credit && r.credit.results.length) {
    const cs = r.credit; const part = (x, k) => { const p = x.parts.find((q) => q.key === k); return p ? pc(p.value) : ''; };
    const rows = [H('学号', '姓名', '小组', '等级', '信誉分', '交齐%', '按时%', '优秀%', '连续交%', '诚信%', '考试%', '偏弱项')];
    for (const x of cs.results) rows.push([x.student.no, x.student.name, x.student.group || '', x.level, x.hasData ? x.index : '', part(x, 'rate'), part(x, 'ontime'), part(x, 'excellent'), part(x, 'streak'), part(x, 'integrity'), part(x, 'exam'), x.parts.filter((p) => p.value < 0.6).map((p) => `${p.label}：${p.detail}`).join('；')]);
    rows.push([]); rows.push(H('小组', '小组信誉分')); for (const [g, v] of cs.groups) rows.push([g, v]);
    rows.push([]); rows.push(['全班平均', cs.average]); rows.push(['信誉分加减分', creditLines(cs).slice(-1)[0]]);
    sheets.push({ name: '信誉分', rows, widths: [10, 10, 10, 8, 10, 9, 9, 9, 9, 9, 9, 40] });
  }
  sheets.push({ name: '积分', widths: [12, 10, 10, 12, 8], rows: [
    H('学号', '姓名', '积分', '本时段变化', '名次'), ...[...r.students].sort((a, b) => a.rank - b.rank).map((s) => [s.student.no, s.student.name, s.score, s.scoreDelta, s.rank]),
  ] });

  if (exam && exam.exams.length) {
    const q = Object.fromEntries(exam.quality.map((x) => [x.exam.id, x]));
    sheets.push({ name: '考试统计', widths: [14, 8, 18, ...Array(14).fill(8), 12], rows: [
      H('日期', '科目', '考试', '满分', '人数', '缺考', '平均', '中位', '下四分位', '上四分位', '最高', '最低', '标准差', '及格率%', '良好率%', '优秀率%', '难度', '区分度'),
      ...exam.exams.map((e) => { const s = exam.stats[e.id]; const qq = q[e.id];
        return [fmtDay(e.date), e.subject || '', e.title, e.fullScore, s.count, s.absent, fmtNum(s.avg), fmtNum(s.median), qq ? fmtNum(qq.q1) : '', qq ? fmtNum(qq.q3) : '', s.max, s.min, fmtNum(s.std), pc(s.passRate), pc(s.goodRate), pc(s.excellentRate),
          qq ? `${difficultyText(qq.difficulty)} ${pc(qq.difficulty)}%` : '', qq ? `${qq.discrimination.toFixed(2)} ${discriminationText(qq.discrimination)}` : '']; }),
      [], ...exam.insights.map((x) => [x.text]),
    ] });
    const er = [H('考试进步之星', '学号', '最近一场得分率%', '之前相对平均', '最近相对平均', '最近一场')];
    exam.stars.forEach((x) => er.push([x.student.name, x.student.no, pc(x.lastPct), pc(x.before), pc(x.after), x.last.title]));
    er.push([], H('考试退步关注', '学号', '最近一场得分率%', '之前相对平均', '最近相对平均', '最近一场'));
    exam.watch.forEach((x) => er.push([x.student.name, x.student.no, pc(x.lastPct), pc(x.before), pc(x.after), x.last.title]));
    er.push([]);
    const f = exam.flow;
    if (f) { er.push(H(`层级流动：${f.from.title} → ${f.to.title}`, ...f.labels)); f.matrix.forEach((row, i) => er.push([f.labels[i], ...row])); er.push([`升档 ${f.up} 人，降档 ${f.down} 人，不变 ${f.same} 人`], []); }
    er.push(H('层级（按平均得分率）', '人数')); exam.levels.forEach((x) => er.push([x[0], x[1]])); er.push([]);
    const names = (title, l) => { if (l.length) { er.push(H(title, '平均得分率%')); l.forEach((x) => er.push([x.student.name, pc(x.avgPct ?? 0)])); er.push([]); } };
    names('临界及格（55–60%）', exam.nearPass); names('临界优秀（80–85%）', exam.nearExcellent);
    names('连续三场上升', exam.rising); names('连续三场下滑', exam.falling); names('成绩稳定', exam.steady);
    if (exam.imbalance.length) { er.push(H('偏科', '最好科目', '得分率%', '最差科目', '得分率%')); exam.imbalance.forEach((x) => er.push([x.student.name, x.best[0], pc(x.best[1]), x.worst[0], pc(x.worst[1])])); er.push([]); }
    if (exam.diligentLow.length || exam.skipButGood.length) {
      er.push(H('作业与考试对照', '类别', '作业交齐率%', '考试平均得分率%'));
      exam.diligentLow.forEach((x) => er.push([x.student.name, '交得齐但考得不好', pc(x.hwRate), pc(x.examPct)]));
      exam.skipButGood.forEach((x) => er.push([x.student.name, '常缺交但考得不错', pc(x.hwRate), pc(x.examPct)]));
      er.push([]);
    }
    if (exam.genders.length === 2) { er.push(H('性别', '人数', '平均得分率%')); exam.genders.forEach((x) => er.push([x[0], x[1], pc(x[2])])); }
    sheets.push({ name: '考试进退与分层', rows: er, widths: [20, 12, 16, 14, 14, 40] });
    const ranks = Object.fromEntries(exam.exams.map((e) => [e.id, examRanks(e)]));
    sheets.push({ name: '考试成绩', widths: [12, 10, ...exam.exams.flatMap(() => [16, 8]), 12, 10], rows: [
      H('学号', '姓名', ...exam.exams.flatMap((e) => [`${e.title}（${fmtNum(e.fullScore)}）`, '名次']), '平均得分率%', '名次变化'),
      ...exam.students.map((x) => [x.student.no, x.student.name, ...exam.exams.flatMap((e) => {
        const v = e.scores?.[x.student.id];
        return [{ v: v ?? ((e.absent || []).includes(x.student.id) ? '缺考' : ''), s: v != null && v < e.fullScore * 0.6 ? ST.RED : v != null && v >= e.fullScore * 0.85 ? ST.GREEN : 0 }, ranks[e.id][x.student.id] ?? ''];
      }), x.avgPct != null ? pc(x.avgPct) : '', x.rankChange ?? '']),
    ] });
  }
  return sheets;
}

export const analysisXlsx = (c, r, exam, now) => xlsx(analysisSheets(c, r, exam, now));

// ---------------- PDF：排版好的打印版报告 ----------------
const esc = (s) => String(s ?? '').replace(/[&<>"]/g, (ch) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[ch]));
const pct = (x) => `${Math.round(x * 100)}%`;
const LV = { GOOD: '#16a34a', WARN: '#e5384d', INFO: '#2f6fed' };
const table = (head, rows, cls = '') => `<table class="${cls}"><thead><tr>${head.map((h) => `<th>${esc(h)}</th>`).join('')}</tr></thead><tbody>${rows.map((r) => `<tr>${r.map((v) => `<td>${v}</td>`).join('')}</tr>`).join('')}</tbody></table>`;

/** 交齐率走势：每次作业一根柱 */
function trendSvg(points) {
  if (points.length < 2) return '';
  const W = 680; const Hh = 140; const n = points.length; const bw = W / n;
  const bars = points.map((p, i) => { const h = Math.max(1, p.summary.rate * (Hh - 20)); const col = p.summary.rate >= 0.95 ? '#16a34a' : p.summary.rate >= 0.8 ? '#2f6fed' : p.summary.rate >= 0.6 ? '#e8a400' : '#e5384d';
    return `<rect x="${(i * bw + bw * 0.15).toFixed(1)}" y="${(Hh - h).toFixed(1)}" width="${(bw * 0.7).toFixed(1)}" height="${h.toFixed(1)}" fill="${col}" rx="2"/>`; }).join('');
  const grid = [0.6, 0.8, 1].map((g) => { const y = Hh - g * (Hh - 20); return `<line x1="0" x2="${W}" y1="${y}" y2="${y}" stroke="#ccc" stroke-dasharray="3 3"/><text x="${W - 2}" y="${y - 2}" font-size="9" text-anchor="end" fill="#888">${g * 100}%</text>`; }).join('');
  return `<svg viewBox="0 0 ${W} ${Hh}" style="width:100%;height:auto;display:block">${grid}${bars}</svg>`;
}
/** 状态构成：一根横条 */
function stackBar(totals) {
  const parts = [['SUBMITTED', '已交', '#16a34a'], ['EXCELLENT', '优秀', '#e8a400'], ['LATE', '迟交', '#e8792c'], ['MISSING', '未交', '#e5384d'], ['LEAVE', '请假', '#2f6fed'], ['NONE', '未登记', '#aaa']];
  const all = parts.reduce((s, [k]) => s + (totals[k] || 0), 0) || 1;
  return `<div class="stack">${parts.map(([k, , col]) => (totals[k] ? `<i style="width:${(totals[k] / all * 100).toFixed(2)}%;background:${col}"></i>` : '')).join('')}</div>
    <div class="legend">${parts.filter(([k]) => totals[k]).map(([k, l, col]) => `<span><b style="background:${col}"></b>${l} ${totals[k]}</span>`).join('')}</div>`;
}

export function analysisHtml(c, r, exam, now = Date.now()) {
  const k = r.kpi; const d = new Date(now);
  const kp = [['交齐率', pct(k.handInRate), r.previous && pct(r.previous.handInRate)], ['按时率', pct(k.onTimeRate), r.previous && pct(r.previous.onTimeRate)], ['优秀率', pct(k.excellentRate), r.previous && pct(r.previous.excellentRate)], ['缺交率', pct(k.missingRate), r.previous && pct(r.previous.missingRate)], ['作业', `${k.assignments} 次`, r.previous && `${r.previous.assignments} 次`], ['平均积分', fmtNum(r.avgScore), null]];
  const S = []; const NUM = '一二三四五六七八九十'; let no = 0; const h2 = (t) => `<h2>${NUM[no++]}、${t}</h2>`;
  S.push(`<header><h1>${esc(c.name)} 作业与成绩分析报告</h1><p>${esc(rangeTitle(r))} · ${esc(r.window.label)} · 生成于 ${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日</p></header>`);
  S.push(`<section>${h2('总体情况')}<div class="kpis">${kp.map(([l, v, pv]) => `<div><b>${v}</b><span>${l}</span>${pv ? `<small>上一时段 ${pv}</small>` : ''}</div>`).join('')}</div>
    <p>共 ${k.assignments} 次作业，应交 ${k.expected} 人次；缺交 ${k.missing} 人次，迟交 ${k.late} 人次，请假 ${k.leave} 人次，未登记 ${k.unregistered} 人次；全员交齐 ${k.perfect} 次（最长连续 ${r.longestPerfectRun} 次）。</p>
    ${stackBar(r.totals)}${r.points.length >= 2 ? `<h3>每次作业交齐率</h3>${trendSvg(r.points)}` : ''}</section>`);
  if (r.insights.length) S.push(`<section>${h2('主要发现')}<ol class="ins">${r.insights.map((x) => `<li style="border-color:${LV[x.level] || '#888'}">${esc(x.text)}</li>`).join('')}</ol></section>`);
  const pg = r.progress;
  if (pg && (pg.stars.length || pg.watch.length)) {
    const rows = (l) => l.map((x) => [esc(x.student.name), pct(x.before.rate), pct(x.after.rate), String(Math.round(x.index * 100)), esc(x.reasons.slice(0, 3).join('，'))]);
    S.push(`<section>${h2('进步与退步')}<p class="muted">${esc(pg.basis)}</p>
      ${pg.stars.length ? `<h3 class="good">进步之星</h3>${table(['姓名', '以往交齐', '现在交齐', '指数', '主要变化'], rows(pg.stars))}` : ''}
      ${pg.watch.length ? `<h3 class="bad">退步关注</h3>${table(['姓名', '以往交齐', '现在交齐', '指数', '主要变化'], rows(pg.watch))}` : ''}</section>`);
  }
  const sub = [];
  if (r.subjects.length) sub.push(`<h3>各科</h3>${table(['科目', '次数', '交齐率', '按时率', '缺交'], r.subjects.map((x) => [esc(x.subject), x.count, pct(x.rate), pct(x.onTimeRate), x.missing]))}`);
  if (r.groupStats.length > 1) sub.push(`<h3>小组</h3>${table(['小组', '组长', '人数', '交齐率', '缺交', '平均积分', '积分变化'], r.groupStats.map((g) => [esc(g.name), esc(g.leader?.name || ''), g.size, pct(g.rate), g.missing, fmtNum(g.avgScore), signed(g.scoreDelta)]))}`);
  if (r.rounds.length > 1) sub.push(`<h3>各轮对比</h3>${table(['轮次', '作业', '交齐率', '按时率', '缺交率', '考试得分率'], r.rounds.map((o) => [esc(o.label), o.kpi.assignments, pct(o.kpi.handInRate), pct(o.kpi.onTimeRate), pct(o.kpi.missingRate), o.examPct != null ? pct(o.examPct) : '—']))}`);
  if (sub.length) S.push(`<section>${h2('分项对比')}${sub.join('')}</section>`);
  const warn = [];
  if (r.attention.length) warn.push(`<h3>需要关注</h3>${table(['姓名', '缺交', '连续未交', '交齐率'], r.attention.map((x) => [esc(x.student.name), x.missing, x.missingStreak, pct(x.rate)]))}`);
  if (r.risk.length) warn.push(`<h3>缺交风险</h3>${table(['姓名', '风险', '依据'], r.risk.map((x) => [esc(x.student.name), pct(x.prob), esc(x.why)]))}`);
  if (r.weakSubjects.length) warn.push(`<h3>偏科提醒</h3>${table(['姓名', '科目', '该科交齐率', '整体交齐率'], r.weakSubjects.map((x) => [esc(x.student.name), esc(x.subject), pct(x.subjectRate), pct(x.overallRate)]))}`);
  if (r.credit && r.credit.results.some((x) => x.hasData)) {
    const has = r.credit.results.filter((x) => x.hasData); const pick = [...has.slice(0, 5), ...has.slice(-5)].filter((x, i, l) => l.findIndex((y) => y.student.id === x.student.id) === i);
    warn.push(`<h3>信誉分（越高越好）</h3>${creditLines(r.credit).map((x) => `<p>${esc(x)}</p>`).join('')}${table(['姓名', '信誉分', '等级', '偏弱项'], pick.map((x) => { const w = x.parts.filter((p) => p.value < 0.6).sort((a, b) => a.value - b.value)[0];
      return [esc(x.student.name), `<span class="${x.index >= 75 ? 'good' : x.index < 60 ? 'bad' : ''}">${fmtNum(x.index)}</span>`, x.level, w ? esc(`${w.label}：${w.detail}`) : '—']; }))}`);
  }
  warn.push(`<h3>学生分层</h3><p>${r.tiers.map((t) => `<b>${esc(t.name)}</b> ${t.students.length} 人（${esc(t.desc)}）`).join('；')}</p>`);
  S.push(`<section>${h2('学生与预警')}${warn.join('')}</section>`);
  S.push(`<section class="break">${h2('学生明细')}${table(['学号', '姓名', '小组', '应交', '已交', '缺交', '迟交', '交齐率', '按时率', '积分', '变化', '名次'],
    [...r.students].sort((a, b) => a.rank - b.rank).map((s) => [esc(s.student.no), esc(s.student.name), esc(s.student.group || ''), s.expected, s.handedIn, s.missing, s.late,
      `<span class="${s.rate < 0.7 ? 'bad' : s.rate >= 0.999 ? 'good' : ''}">${pct(s.rate)}</span>`, pct(s.onTimeRate), fmtNum(s.score), `<span class="${s.scoreDelta < 0 ? 'bad' : ''}">${signed(s.scoreDelta)}</span>`, s.rank]), 'dense')}</section>`);
  if (exam && exam.exams.length) {
    const q = Object.fromEntries(exam.quality.map((x) => [x.exam.id, x]));
    S.push(`<section class="break">${h2('考试')}${table(['日期', '考试', '满分', '人数', '平均', '中位', '最高', '及格率', '优秀率', '难度', '区分度'], exam.exams.map((e) => { const s = exam.stats[e.id]; const qq = q[e.id];
      return [fmtDay(e.date), esc(`${e.subject || ''} ${e.title}`), fmtNum(e.fullScore), s.count, fmtNum(s.avg), fmtNum(s.median), fmtNum(s.max), pct(s.passRate), pct(s.excellentRate), qq ? difficultyText(qq.difficulty) : '—', qq ? discriminationText(qq.discrimination) : '—']; }))}
      ${exam.insights.length ? `<ol class="ins">${exam.insights.map((x) => `<li style="border-color:${LV[x.level] || '#888'}">${esc(x.text)}</li>`).join('')}</ol>` : ''}
      ${exam.levels.length ? `<p>层级（按平均得分率）：${exam.levels.map((x) => `${x[0]} ${x[1]} 人`).join('，')}</p>` : ''}</section>`);
  }
  const css = `@page{size:A4;margin:14mm}*{box-sizing:border-box}body{font:12px/1.55 -apple-system,"PingFang SC","Microsoft YaHei",sans-serif;color:#111;margin:0}
header{border-bottom:3px solid #2f6fed;padding-bottom:8px;margin-bottom:12px}h1{font-size:22px;margin:0}header p,.muted{color:#666;margin:4px 0 0}
h2{font-size:16px;margin:18px 0 8px;color:#2f6fed}h3{font-size:13px;margin:12px 0 6px}section{break-inside:auto}.break{break-before:page}
.kpis{display:grid;grid-template-columns:repeat(6,1fr);gap:8px;margin:6px 0 10px}.kpis div{border:1px solid #e3e3e8;border-radius:8px;padding:8px;text-align:center}
.kpis b{display:block;font-size:18px}.kpis span{color:#666}.kpis small{display:block;color:#999;font-size:10px}
.stack{display:flex;height:14px;border-radius:7px;overflow:hidden;margin:8px 0 4px}.stack i{display:block;height:100%}
.legend{display:flex;flex-wrap:wrap;gap:10px;color:#555;font-size:11px}.legend b{display:inline-block;width:9px;height:9px;border-radius:50%;margin-right:4px}
.ins{padding-left:18px}.ins li{border-left:3px solid;padding:2px 0 2px 8px;margin:4px 0;break-inside:avoid}
table{width:100%;border-collapse:collapse;margin:4px 0 8px}th{background:#2f6fed;color:#fff;font-weight:600;text-align:left}th,td{padding:4px 6px;border-bottom:1px solid #e6e6ea}
tr{break-inside:avoid}.dense td,.dense th{padding:3px 5px;font-size:11px}.good{color:#16a34a}.bad{color:#e5384d}
@media screen{body{max-width:820px;margin:20px auto;padding:0 16px}}`;
  return `<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><title>${esc(c.name)}_分析报告</title><style>${css}</style></head><body>${S.join('\n')}</body></html>`;
}
