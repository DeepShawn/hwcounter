// 网页查看版 · 统计页：与安卓“统计”页同样的四个栏目（作业 / 考试 / 关注 / 小组）、同样的分析项和算法（analytics.js）。
// 只读：没有加减分按钮；分析报告可以复制或下载为文字。
import { h } from '../vendor/preact.module.js';
import { useState, useEffect, useMemo } from '../vendor/hooks.module.js';
import htm from '../vendor/htm.module.js';
import { STATUS, fmtNum, signed, compareStudentNumbers, rechargeCounts } from './model.js';
import {
  report as buildReport, text as reportText, examReport, periodBounds, periodLabel, difficultyText, discriminationText,
  attention, attentionWeekly, ATT_LEVELS, stockBoard, groupStockBoard, SPANS, groupReport, groupDuel, classResets,
} from './analytics.js';

const html = htm.bind(h);
const GOOD = '#16a34a'; const BAD = '#e5384d'; const WARN = '#e8792c'; const BLUE = '#3482ff'; const PURPLE = '#7a5cff';
const pct = (x) => (x == null || !Number.isFinite(x) ? '—' : `${Math.round(x * 100)}%`);
const md = (t) => { const d = new Date(t); return `${d.getMonth() + 1}/${d.getDate()}`; };
const ORDER = ['SUBMITTED', 'EXCELLENT', 'LEAVE', 'LATE', 'MISSING', 'NONE'];
const SC = (k) => STATUS[k].color;
const LEVEL_COLOR = { GOOD, INFO: BLUE, WARN: BAD };
const TEAM_COLORS = ['#3482ff', '#f76707', '#12b886', '#ae3ec9', '#e64980', '#1098ad', '#f59f00', '#4263eb'];
const attColor = (l) => (ATT_LEVELS.find((x) => x[0] === l) || ATT_LEVELS[3])[2];
const attLabel = (l) => (ATT_LEVELS.find((x) => x[0] === l) || ATT_LEVELS[3])[1];
const toDay = (t) => { const d = new Date(t); const p = (n) => String(n).padStart(2, '0'); return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`; };
const fromDay = (s) => { const [y, m, d] = s.split('-').map(Number); return new Date(y, m - 1, d).getTime(); };

// 切换底栏后回来仍保持上次的选择（与手机端 rememberSaveable 一致）
const keep = { mode: 'hw', top: 0, cal: 2, offset: 0, from: null, to: null, examSubject: 0, att: 0, kind: 0, span: 'M1', kline: false, pill: 0, sort: 0, heatSort: 0, stuSort: 0 };
function useKeep(k) { const [v, set] = useState(keep[k]); return [v, (x) => { keep[k] = typeof x === 'function' ? x(keep[k]) : x; set(keep[k]); }]; }
/** 耗时的计算放到下一帧，先把页面画出来；算好之前返回 undefined */
function useLater(fn, deps) {
  const [v, setV] = useState(null);
  useEffect(() => { let alive = true; const id = setTimeout(() => { const r = fn(); if (alive) setV({ r, deps }); }, 16); return () => { alive = false; clearTimeout(id); }; }, deps);
  return v && v.deps.length === deps.length && v.deps.every((x, i) => x === deps[i]) ? v.r : undefined;
}

export function StatsPage({ kit, c, rules, open, prefs }) {
  const { Page, Seg } = kit;
  const [mode, setMode] = useKeep('mode');
  const [share, setShare] = useState(false);
  const exams = c.exams || [];
  return html`<${Page} title="统计" large="数据分析" actions=${html`<button class="ib" aria-label="分析报告" onClick=${() => setShare(true)}><svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M12 3v12M7 8l5-5 5 5M5 13v6a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-6" /></svg></button>`}>
    <div class="meta">${c.name} · ${c.students.length} 人 · ${c.assignments.length} 次作业 · ${exams.length} 场考试</div>
    <div class="pad"><${Seg} items=${[['hw', '作业'], ['exam', '考试'], ['att', '关注'], ['group', '小组']]} value=${mode} onChange=${setMode} /></div>
    ${mode === 'hw' && html`<${Homework} kit=${kit} c=${c} rules=${rules} open=${open} share=${share} setShare=${setShare} />`}
    ${mode === 'exam' && html`<${Exams} kit=${kit} c=${c} rules=${rules} open=${open} />`}
    ${mode === 'att' && html`<${AttentionTab} kit=${kit} c=${c} rules=${rules} open=${open} us=${prefs.usColors !== false} />`}
    ${mode === 'group' && html`<${Groups} kit=${kit} c=${c} rules=${rules} open=${open} />`}
    ${mode !== 'hw' && share && html`<${ReportSheet} c=${c} rules=${rules} spec=${{ range: 'ALL' }} onClose=${() => setShare(false)} />`}
  </${Page}>`;
}

// ---------------- 通用小部件 ----------------
const Card = ({ title, children, cls = '' }) => html`${title && html`<div class="st">${title}</div>`}<section class=${'grp ' + cls}>${children}</section>`;
const Note = ({ children }) => html`<div class="an-note">${children}</div>`;
const Chips = ({ title, items, color, open }) => html`<div class="an-chips">${title && html`<div class="an-sub">${title}</div>`}<div>${items.map(([s, tag]) => html`<button key=${s.id} class="an-chip" style=${{ background: color + '22' }} onClick=${() => open({ view: 'student', id: s.id })}>${s.name}${tag ? ` · ${tag}` : ''}</button>`)}</div></div>`;
function Kpi({ label, value, delta, goodWhenUp = true }) {
  let d = null;
  if (delta != null && Number.isFinite(delta)) {
    const p = Math.round(delta * 100);
    const good = p === 0 ? null : (p > 0) === goodWhenUp;
    d = html`<small style=${{ color: good == null ? 'var(--sub)' : good ? GOOD : BAD }}>${p > 0 ? '▲' : p < 0 ? '▼' : ''}${p === 0 ? '持平' : `${Math.abs(p)} 个百分点`}</small>`;
  }
  return html`<div class="tile kpi"><b>${value}</b><span>${label}</span>${d}</div>`;
}
const Insights = ({ items }) => html`<div class="an-ins">${items.length ? items.map((x, i) => html`<div key=${i}><i style=${{ background: LEVEL_COLOR[x.level] }}></i><span>${x.text}</span></div>`) : html`<div class="muted">数据还不多，暂时没有明显结论。</div>`}</div>`;
function HBars({ rows, color }) {
  return html`<div class="an-hbars">${rows.map(([l, v, t]) => html`<div class="bar-row" key=${l}><span class="bar-l" title=${l}>${l}</span><i><em style=${{ width: `${Math.max(2, Math.min(100, v * 100))}%`, background: color || (v >= 0.9 ? GOOD : v >= 0.7 ? 'var(--accent)' : v >= 0.6 ? WARN : BAD) }}></em></i><b class="an-wide">${t}</b></div>`)}</div>`;
}
function Line({ values, labels, tips, lo = 0, hi = 1, fmt = pct, height = 130 }) {
  const n = values.length; if (n < 2) return null;
  const W = 320; const H = height; const P = 10;
  if (hi - lo < 1e-9) { lo -= 1; hi += 1; }
  const x = (i) => P + (W - 2 * P) * i / (n - 1); const y = (v) => P + (H - 2 * P) * (1 - (v - lo) / (hi - lo));
  const d = values.map((v, i) => `${i ? 'L' : 'M'}${x(i).toFixed(1)},${y(v).toFixed(1)}`).join(' ');
  const a = values.reduce((s, v) => s + v, 0) / n;
  return html`<div class="chart"><svg class="trend" style=${{ height: H + 'px' }} viewBox=${`0 0 ${W} ${H}`} preserveAspectRatio="none" role="img" aria-label=${`走势，平均 ${fmt(a)}`}>
      <line x1=${P} x2=${W - P} y1=${y(a)} y2=${y(a)} stroke="var(--sub)" stroke-dasharray="4 4" stroke-width="1" vector-effect="non-scaling-stroke" />
      <path d=${`${d} L${x(n - 1)},${H - P} L${x(0)},${H - P} Z`} fill="var(--accent)" opacity="0.12" />
      <path d=${d} fill="none" stroke="var(--accent)" stroke-width="2.2" stroke-linejoin="round" vector-effect="non-scaling-stroke" />
      ${values.map((v, i) => html`<circle cx=${x(i)} cy=${y(v)} r="2.6" fill="var(--accent)"><title>${tips ? tips[i] : fmt(v)}</title></circle>`)}
    </svg>
    <div class="axis"><span>${labels[0]}</span><span>虚线为平均 ${fmt(a)}</span><span>${labels[n - 1]}</span></div></div>`;
}
function Histogram({ bins }) {
  const max = Math.max(1, ...bins.map((b) => b[1]));
  return html`<div class="an-hist">${bins.map(([l, n]) => html`<div key=${l} title=${`${l}：${n} 人`}><small>${n || ''}</small><i style=${{ height: `${n / max * 100}%` }}></i><span>${l}</span></div>`)}</div>`;
}
function Stack({ points }) {
  const max = Math.max(1, ...points.map((p) => ORDER.reduce((s, k) => s + p.summary.counts[k], 0)));
  return html`<div class="an-stack">${points.map((p) => html`<div key=${p.assignment.id} title=${`${p.assignment.title}：${ORDER.filter((k) => p.summary.counts[k] > 0).map((k) => STATUS[k].label + p.summary.counts[k]).join(' ')}`}>
      <div class="an-col">${ORDER.map((k) => p.summary.counts[k] > 0 && html`<i style=${{ height: `${p.summary.counts[k] / max * 100}%`, background: SC(k), opacity: k === 'NONE' ? 0.35 : 1 }}></i>`)}</div>
      <span>${md(p.assignment.createdAt)}</span></div>`)}</div>
    <div class="legend in">${ORDER.map((k) => html`<span><i style=${{ background: SC(k), opacity: k === 'NONE' ? 0.35 : 1 }}></i>${STATUS[k].label}</span>`)}</div>`;
}
function Donut({ parts, size = 128, stroke = 18, children }) {
  const total = parts.reduce((s, p) => s + p[1], 0) || 1; const r = (size - stroke) / 2; const len = 2 * Math.PI * r; let off = 0;
  return html`<div class="ring" style=${{ width: size + 'px', height: size + 'px' }}><svg width=${size} height=${size}>
      <circle cx=${size / 2} cy=${size / 2} r=${r} stroke="var(--track)" stroke-width=${stroke} fill="none" />
      ${parts.filter((p) => p[1] > 0).map(([col, v]) => { const l = v / total * len; const el = html`<circle cx=${size / 2} cy=${size / 2} r=${r} stroke=${col} stroke-width=${stroke} fill="none" stroke-dasharray=${`${l} ${len - l}`} stroke-dashoffset=${-off} transform=${`rotate(-90 ${size / 2} ${size / 2})`} />`; off += l; return el; })}
    </svg><div class="ring-in">${children}</div></div>`;
}
function Tabs2({ items, value, onChange }) { return html`<div class="an-tabs">${items.map((t, i) => html`<button class=${i === value ? 'on' : ''} onClick=${() => onChange(i)}>${t}</button>`)}</div>`; }
const Tag = ({ v }) => html`<em class="an-tag" style=${{ color: v >= 0 ? GOOD : BAD, background: (v >= 0 ? GOOD : BAD) + '1f' }}>${v >= 0 ? '+' : ''}${v}</em>`;

// ---------------- 作业 ----------------
function RangePicker({ top, setTop, cal, setCal, offset, setOffset, from, to, setFrom, setTo, label, Seg }) {
  const calKeys = ['YEAR', 'QUARTER', 'MONTH', 'WEEK', 'DAY', 'CUSTOM'];
  const range = calKeys[cal];
  let nav = null;
  if (top === 3 && range === 'CUSTOM') {
    nav = html`<div class="an-range"><input type="date" value=${toDay(from)} onChange=${(e) => { if (!e.target.value) return; const d = fromDay(e.target.value); setFrom(d); if (d > to) setTo(d); }} /><span>至</span>
      <input type="date" value=${toDay(to)} onChange=${(e) => { if (!e.target.value) return; const d = fromDay(e.target.value); setTo(d); if (d < from) setFrom(d); }} /></div>`;
  } else if (top === 3) {
    const [a, b] = periodBounds(range, offset);
    nav = html`<div class="an-range"><button onClick=${() => setOffset(offset - 1)} aria-label="上一个">‹</button><b onClick=${() => setOffset(0)}>${periodLabel(range, a, b, offset)}</b><button disabled=${offset >= 0} onClick=${() => setOffset(offset + 1)} aria-label="下一个">›</button></div>`;
  }
  return html`<div class="pad an-picker">
    <${Seg} items=${['全部', '本轮', '近 10 次', '按时间'].map((l, i) => [i, l])} value=${top} onChange=${(v) => { setTop(v); setOffset(0); }} />
    ${top === 3 && html`<${Seg} items=${['年', '季度', '月', '周', '日', '自定义'].map((l, i) => [i, l])} value=${cal} onChange=${(v) => { setCal(v); setOffset(0); }} />`}
    ${nav}
    <div class="an-label">${label}</div></div>`;
}
function specOf(top, cal, offset, from, to) {
  if (top === 0) return { range: 'ALL' }; if (top === 1) return { range: 'PERIOD' }; if (top === 2) return { range: 'RECENT10' };
  const r = ['YEAR', 'QUARTER', 'MONTH', 'WEEK', 'DAY', 'CUSTOM'][cal];
  return r === 'CUSTOM' ? { range: r, from, to } : { range: r, offset };
}

function Homework({ kit, c, rules, open, share, setShare }) {
  const { Seg, Empty, Avatar, Cell } = kit;
  const [top, setTop] = useKeep('top'); const [cal, setCal] = useKeep('cal'); const [offset, setOffset] = useKeep('offset');
  const [from, setFrom] = useKeep('from'); const [to, setTo] = useKeep('to');
  const f = from ?? Date.now() - 29 * 86400000; const t = to ?? Date.now();
  const spec = specOf(top, cal, offset, f, t);
  const r = useMemo(() => buildReport(c, rules, spec), [c, rules, spec.range, spec.offset, spec.from, spec.to]);
  const k = r.kpi; const p = r.previous;
  const picker = html`<${RangePicker} top=${top} setTop=${setTop} cal=${cal} setCal=${setCal} offset=${offset} setOffset=${setOffset} from=${f} to=${t} setFrom=${setFrom} setTo=${setTo} label=${r.window.label} Seg=${Seg} />`;
  const sheet = share && html`<${ReportSheet} c=${c} rules=${rules} spec=${spec} report=${r} onClose=${() => setShare(false)} />`;
  if (!r.points.length) {
    return html`${picker}<${Empty} emoji="📈" text=${c.assignments.length ? '这个范围里没有作业，换一个时间范围，或往前翻看看' : '还没有作业数据：登记几次作业后，这里会出现趋势、热力图和分析报告'} />
      ${r.progress && html`<${Progress} pg=${r.progress} open=${open} Avatar=${Avatar} />`}${sheet}`;
  }
  const days = ['周一', '周二', '周三', '周四', '周五', '周六', '周日'];
  const wd = r.weekday.map((v, i) => (v == null ? null : [days[i], v, pct(v)])).filter(Boolean);
  const sl = r.slope * 100;
  const total = Math.max(1, ORDER.reduce((s, x) => s + (r.totals[x] || 0), 0));
  const best = r.buckets.length ? r.buckets.reduce((a, b) => (b.rate > a.rate ? b : a)) : null;
  const worst = r.buckets.length ? r.buckets.reduce((a, b) => (b.rate < a.rate ? b : a)) : null;
  const src = r.sources;
  const srcItems = [['作业自动', src.homework, BLUE], ['手动加分', src.manualPlus, GOOD], ['手动扣分', src.manualMinus, BAD], ['考试计分', src.exam, PURPLE]];
  const srcMax = Math.max(1, ...srcItems.map((x) => Math.abs(x[1])));
  const roundBest = Math.max(...r.rounds.filter((x) => x.kpi.assignments > 0).map((x) => x.kpi.handInRate));
  return html`${picker}
    <${Card} title="主要发现"><${Insights} items=${r.insights} /></${Card}>
    <div class="tiles three">
      <${Kpi} label="交齐率" value=${pct(k.handInRate)} delta=${p && k.handInRate - p.handInRate} />
      <${Kpi} label="按时率" value=${pct(k.onTimeRate)} delta=${p && k.onTimeRate - p.onTimeRate} />
      <${Kpi} label="优秀率" value=${pct(k.excellentRate)} delta=${p && k.excellentRate - p.excellentRate} />
      <${Kpi} label="缺交率" value=${pct(k.missingRate)} delta=${p && k.missingRate - p.missingRate} goodWhenUp=${false} />
      <${Kpi} label="迟交率" value=${pct(k.lateRate)} delta=${p && k.lateRate - p.lateRate} goodWhenUp=${false} />
      <${Kpi} label="全员交齐" value=${`${k.perfect} 次`} />
    </div>
    <div class="meta">${k.assignments} 次作业 · 应交 ${k.expected} 人次 · 缺交 ${k.missing} · 迟交 ${k.late} · 请假 ${k.leave}${k.unregistered > 0 ? ` · 未登记 ${k.unregistered}` : ''}${p ? ` · 对比${r.window.prevLabel} ${p.assignments} 次` : ''}</div>
    ${r.progress && html`<${Progress} pg=${r.progress} open=${open} Avatar=${Avatar} />`}
    <${Card} title="交齐率趋势"><${Line} values=${r.points.map((x) => x.summary.rate)} labels=${r.points.map((x) => md(x.assignment.createdAt))}
      tips=${r.points.map((x) => `${md(x.assignment.createdAt)} ${x.assignment.subject || ''}「${x.assignment.title}」 ${pct(x.summary.rate)} · 缺 ${x.summary.counts.MISSING} 人`)} />
      ${r.points.length >= 4 && html`<${Note}>趋势：平均每次${sl >= 0 ? '上升' : '下降'} ${Math.abs(sl).toFixed(1)} 个百分点${r.longestPerfectRun >= 2 ? ` · 最长连续 ${r.longestPerfectRun} 次全员交齐` : ''}</${Note}>`}</${Card}>
    ${r.buckets.length >= 2 && html`<${Card} title=${`按${r.bucketUnit}汇总`}><${Line} values=${r.buckets.map((b) => b.rate)} labels=${r.buckets.map((b) => b.label)} height=${110}
      tips=${r.buckets.map((b) => `${b.label}：${b.count} 次作业 · 交齐 ${pct(b.rate)} · 按时 ${pct(b.onTimeRate)} · 缺交 ${b.missing} 人次`)} />
      ${best.rate - worst.rate >= 0.03 && html`<${Note}>最好：${best.label}（${pct(best.rate)}）· 最差：${worst.label}（${pct(worst.rate)}）</${Note}>`}</${Card}>`}
    <${Card} title="每次作业构成"><${Stack} points=${r.points} /></${Card}>
    <${Card} title="状态分布"><div class="an-donut"><${Donut} parts=${ORDER.map((x) => [SC(x), r.totals[x] || 0])}><b>${pct(k.handInRate)}</b><small>交齐率</small></${Donut}>
      <div>${ORDER.map((x) => html`<div class="an-leg"><i style=${{ background: SC(x) }}></i><span>${STATUS[x].label}  ${r.totals[x] || 0}</span><small>${pct((r.totals[x] || 0) / total)}</small></div>`)}</div></div></${Card}>
    <${Heatmap} c=${c} r=${r} open=${open} />
    <${StudentTable} r=${r} open=${open} />
    <${Tiers} tiers=${r.tiers} open=${open} />
    <${Card} title="交齐率分布（人数）"><${Histogram} bins=${r.rateBins} />
      <${Note}>中位数 ${pct(r.rateMedian)} · 标准差 ${Math.trunc(r.rateStd * 100)} 个百分点${r.rateStd >= 0.15 ? '（两极分化）' : r.rateStd < 0.06 ? '（比较整齐）' : ''}</${Note}></${Card}>
    ${r.risk.length > 0 && html`<${Card} title="缺交风险预警（下一次）">${r.risk.map((x) => html`<button class="an-row" onClick=${() => open({ view: 'student', id: x.student.id })}>
        <div class="an-grow"><b>${x.student.name}</b><small>${x.why}</small></div><i class="an-bar"><em style=${{ width: `${Math.max(4, x.prob * 100)}%`, background: x.prob >= 0.6 ? BAD : WARN }}></em></i><b class="an-num">${pct(x.prob)}</b></button>`)}
      <${Note}>按最近 8 次加权估算（越近权重越大，迟交算 0.4 次缺交），只作提醒参考。</${Note}></${Card}>`}
    ${r.streakers.length > 0 && html`<${Card} title="连续交齐榜"><${Chips} items=${r.streakers.map((x) => [x.student, `${x.handStreak} 次`])} color=${GOOD} open=${open} /></${Card}>`}
    ${r.weakSubjects.length > 0 && html`<${Card} title="偏科提醒（某科明显交得少）">${r.weakSubjects.map((x) => html`<${Cell} key=${x.student.id} title=${`${x.student.name} · ${x.subject}`}
      sub=${`${x.subject}交齐率 ${pct(x.subjectRate)}（${x.count} 次），整体 ${pct(x.overallRate)}，低 ${Math.trunc((x.overallRate - x.subjectRate) * 100)} 个百分点`} onClick=${() => open({ view: 'student', id: x.student.id })} />`)}</${Card}>`}
    ${r.scoreTrend.length >= 2 && (() => { const v = r.scoreTrend.map((x) => x[1]); return html`<${Card} title="全班平均积分走势"><${Line} values=${v} lo=${Math.min(...v)} hi=${Math.max(...v)} fmt=${fmtNum}
      labels=${r.scoreTrend.map((x) => md(x[0].createdAt))} tips=${r.scoreTrend.map(([a, s]) => `${md(a.createdAt)}「${a.title}」后 平均 ${fmtNum(s)} 分`)} /></${Card}>`; })()}
    ${(r.risers.length > 0 || r.fallers.length > 0) && html`<${Card} title=${r.range === 'ALL' ? '积分涨跌（有记录以来）' : '积分涨跌（本时段）'}><div class="an-two">
      ${[['上升', r.risers, GOOD], ['下降', r.fallers, BAD]].map(([tt, l, col]) => html`<div><div class="an-sub">${tt}</div>${l.length ? l.map((x) => html`<button class="an-mini" onClick=${() => open({ view: 'student', id: x.student.id })}><span>${x.student.name}</span><b style=${{ color: col }}>${signed(x.scoreDelta)}</b></button>`) : html`<div class="muted">—</div>`}</div>`)}</div></${Card}>`}
    ${(srcItems.some((x) => Math.abs(x[1]) > 1e-9) || r.reasons.length > 0) && html`<${Card} title="积分来源（本范围，全班合计）"><div class="an-src">
      ${srcItems.map(([l, v, col]) => html`<div class="an-srow"><span>${l}</span><i class="an-bar"><em style=${{ width: `${Math.max(1, Math.abs(v) / srcMax * 100)}%`, background: col }}></em></i><b>${v === 0 ? '0' : signed(v)}</b></div>`)}
      ${r.reasons.length > 0 && html`<div class="an-sub">加减分原因（${src.manualCount} 条）</div>${r.reasons.slice(0, 6).map((x) => html`<div class="an-srow"><span class="an-grow">${x.reason}</span><small>${x.count} 次 · ${x.people} 人</small><b style=${{ color: x.total >= 0 ? GOOD : BAD }}>${signed(x.total)}</b></div>`)}`}
    </div></${Card}>`}
    ${r.rounds.length > 1 && html`<${Card} title="各轮对比（以全班重置为分界）"><table class="an-table"><thead><tr><th>轮次</th><th>作业</th><th>交齐</th><th>优秀</th><th>考试</th></tr></thead><tbody>
      ${r.rounds.map((x) => html`<tr><td><b style=${{ fontWeight: x.end == null ? 600 : 400 }}>${x.label}</b>${x.start != null && html`<small>${md(x.start)} 起${x.reset ? ` · ${fmtNum(x.reset.target)} 分` : ''}</small>`}</td>
        <td>${x.kpi.assignments}</td><td style=${{ color: x.kpi.assignments > 0 && x.kpi.handInRate >= roundBest ? GOOD : null, fontWeight: 600 }}>${x.kpi.assignments ? pct(x.kpi.handInRate) : '—'}</td><td>${x.kpi.assignments ? pct(x.kpi.excellentRate) : '—'}</td><td>${x.examPct == null ? '—' : pct(x.examPct)}</td></tr>`)}</tbody></table></${Card}>`}
    ${r.groupStats.length > 1 && html`<${Card} title="小组对比">${r.groupStats.map((g) => html`<div class="an-group" key=${g.name}><div class="an-gh"><b>${g.name}</b><small>${g.leader ? `组长 ${g.leader.name}` : ''}</small><b style=${{ color: 'var(--accent)' }}>${fmtNum(g.avgScore)} 分</b></div>
      <${HBars} rows=${[['交齐', g.rate, pct(g.rate)]]} /><small>${g.size} 人 · 缺交 ${g.missing} 次${r.range !== 'ALL' ? ` · 积分 ${signed(g.scoreDelta)}` : ''}${g.examPct != null ? ` · 考试得分率 ${pct(g.examPct)}` : ''}</small></div>`)}</${Card}>`}
    ${r.subjects.length > 1 && html`<${Card} title="各科对比"><${HBars} rows=${r.subjects.map((x) => [x.subject, x.rate, `${pct(x.rate)} · ${x.count}次`])} /></${Card}>`}
    ${wd.length >= 2 && html`<${Card} title="星期分布（平均交齐率）"><${HBars} rows=${wd} color="var(--accent)" /></${Card}>`}
    <${Card} title="需要关注">${r.attention.length ? r.attention.map((x) => html`<${Cell} key=${x.student.id} left=${html`<${Avatar} name=${x.student.name} size=${36} />`} title=${x.student.name}
      sub=${`缺交 ${x.missing} 次 · 迟交 ${x.late} 次 · 交齐率 ${pct(x.rate)}${x.missingStreak >= 2 ? ` · 连续 ${x.missingStreak} 次未交` : ''}`} onClick=${() => open({ view: 'student', id: x.student.id })} />`) : html`<div class="an-pad muted">这段时间没有缺交，太棒了！</div>`}</${Card}>
    <${Card} title=${`全勤之星 ${r.perfectAttendance.length} 人（无缺交、无迟交）`}>${r.perfectAttendance.length ? html`<${Chips} items=${r.perfectAttendance.map((x) => [x.student, ''])} color="#8a8a8f" open=${open} />` : html`<div class="an-pad muted">暂时还没有，继续加油！</div>`}</${Card}>
    ${r.scoreBins.length > 0 && html`<${Card} title="当前积分分布"><${Histogram} bins=${r.scoreBins} /></${Card}>`}
    ${r.unregistered.length > 0 && html`<${Card} title=${`数据完整度：${r.unregistered.length} 次作业有人未登记`}>${r.unregistered.slice(0, 6).map(([a, n]) => html`<${Cell} key=${a.id} title=${`${a.subject || ''} ${a.title}`.trim()} sub=${`${md(a.createdAt)} · ${n} 人未登记`} onClick=${() => open({ view: 'assign', id: a.id })} />`)}</${Card}>`}
    <${Card} title="分析报告"><div class="an-pad"><div class="muted" style=${{ fontSize: '13px' }}>把本页全部分析（含考试、进步与退步）整理成文字，一键复制或下载。</div>
      <button class="btn1 an-btn" onClick=${() => setShare(true)}>生成分析报告</button></div></${Card}>
    ${sheet}`;
}

function Progress({ pg, open, Avatar }) {
  const [tab, setTab] = useState(0);
  const list = tab === 0 ? pg.stars : pg.watch;
  return html`<${Card} title="进步之星 · 退步关注"><div class="an-pad0">
    <${Tabs2} items=${[`进步之星 ${pg.stars.length}`, `退步关注 ${pg.watch.length}`]} value=${tab} onChange=${setTab} />
    <div class="an-note">${pg.basis}</div>
    ${!list.length && html`<div class="an-pad muted">${tab === 0 ? '暂时没有明显进步的同学（综合指数提高 5 分以上才算）。' : '没有明显退步的同学，很好！'}</div>`}
    ${list.map((x) => html`<button class="an-row" key=${x.student.id} onClick=${() => open({ view: 'student', id: x.student.id })}><${Avatar} name=${x.student.name} size=${36} />
      <div class="an-grow"><b>${x.student.name} <${Tag} v=${Math.trunc(x.index * 100)} /></b><small>${x.reasons.slice(0, 2).join(' · ') || '综合表现变化'}</small></div></button>`)}
    <div class="an-note">综合指数 = 交齐率变化×45% + 按时率×20% + 优秀率×15% + 考试得分率×20%（以百分点计）。</div></div></${Card}>`;
}

function Heatmap({ c, r, open }) {
  const [sort, setSort] = useKeep('heatSort');
  const cols = r.points.map((p) => p.assignment);
  const ins = Object.fromEntries(r.students.map((x) => [x.student.id, x]));
  const rows = sort === 1 ? [...c.students].sort((a, b) => (ins[b.id]?.missing || 0) - (ins[a.id]?.missing || 0) || (ins[b.id]?.late || 0) - (ins[a.id]?.late || 0))
    : [...c.students].sort((a, b) => compareStudentNumbers(a.no, b.no));
  const resets = classResets(c); const last = resets[resets.length - 1];
  const boundary = last ? cols.findIndex((a) => rechargeCounts(last, a.createdAt)) : -1;
  return html`<${Card} title=${`作业热力图（${cols.length} 次）`} cls="heat-card"><div class="an-pad0"><${Tabs2} items=${['按学号', '按缺交']} value=${sort} onChange=${setSort} /></div>
    <div class="heat-wrap" ref=${(el) => { if (el && !el.dataset.done) { el.scrollLeft = el.scrollWidth; el.dataset.done = '1'; } }}><table class="heat">
      <thead><tr><th class="hn"></th>${cols.map((a, i) => html`<th class=${i === boundary && boundary > 0 ? 'rb' : ''}><button onClick=${() => open({ view: 'assign', id: a.id })} title=${a.title}>${md(a.createdAt)}</button></th>`)}<th>交齐率</th></tr></thead>
      <tbody>${rows.map((s) => html`<tr key=${s.id}><td class="hn"><button onClick=${() => open({ view: 'student', id: s.id })}>${s.name}</button></td>
        ${cols.map((a, i) => { const k = a.statuses[s.id] || 'NONE'; return html`<td class=${i === boundary && boundary > 0 ? 'rb' : ''}><i class="hc" title=${`${s.name} · ${a.title}：${STATUS[k].label}`} style=${{ background: SC(k), opacity: k === 'NONE' ? 0.22 : 0.92 }}></i></td>`; })}
        <td class="hr">${pct(ins[s.id]?.rate)}</td></tr>`)}</tbody></table></div>
    <div class="legend in">${ORDER.map((k) => html`<span><i style=${{ background: SC(k), opacity: k === 'NONE' ? 0.3 : 1 }}></i>${STATUS[k].label}</span>`)}</div>
    <${Note}>左右滑动查看更早的作业，点日期进入该次作业${boundary > 0 ? ' · 蓝色竖线：全班积分重置' : ''}</${Note}></${Card}>`;
}

function StudentTable({ r, open }) {
  const [sort, setSort] = useKeep('stuSort'); const [all, setAll] = useState(false);
  const cmp = [
    (a, b) => a.rate - b.rate || b.missing - a.missing, (a, b) => b.missing - a.missing || b.late - a.late,
    (a, b) => b.score - a.score, (a, b) => a.scoreDelta - b.scoreDelta,
  ][sort];
  const rows = [...r.students].sort(cmp);
  return html`<${Card} title=${`学生明细（${r.students.length} 人）`}><div class="an-pad0"><${Tabs2} items=${['交齐率', '缺交', '积分', '变化']} value=${sort} onChange=${setSort} /></div>
    <table class="an-table an-stu"><thead><tr><th>学生</th><th>交齐率</th><th>缺/迟</th><th>积分</th></tr></thead><tbody>
    ${(all ? rows : rows.slice(0, 10)).map((x) => html`<tr key=${x.student.id} class="tap" onClick=${() => open({ view: 'student', id: x.student.id })}><td>${x.student.name}</td>
      <td><span class="an-rate"><i class="an-bar"><em style=${{ width: `${Math.max(2, x.rate * 100)}%`, background: x.rate < 0.7 ? BAD : x.rate < 0.9 ? WARN : GOOD }}></em></i><small>${pct(x.rate)}</small></span></td>
      <td style=${{ color: x.missing > 0 ? BAD : null }}>${x.missing}/${x.late}</td>
      <td><b>${fmtNum(x.score)}</b>${r.range !== 'ALL' && Math.abs(x.scoreDelta) > 1e-9 && html`<small style=${{ color: x.scoreDelta > 0 ? GOOD : BAD }}>${signed(x.scoreDelta)}</small>`}</td></tr>`)}</tbody></table>
    ${rows.length > 10 && html`<button class="more" onClick=${() => setAll(!all)}>${all ? '收起' : `显示全部 ${rows.length} 人`}</button>`}</${Card}>`;
}

function Tiers({ tiers, open }) {
  const cols = [GOOD, BLUE, WARN, BAD]; const total = Math.max(1, tiers.reduce((s, t) => s + t.students.length, 0));
  const [openI, setOpen] = useState(-1);
  return html`<${Card} title="学生分层"><div class="an-pad">
    <div class="an-split">${tiers.map((t, i) => t.students.length > 0 && html`<i style=${{ flex: t.students.length, background: cols[i] }}></i>`)}</div>
    ${tiers.map((t, i) => { const shown = openI === i ? t.students : t.students.slice(0, 8); return html`<div class="an-tier" onClick=${() => setOpen(openI === i ? -1 : i)}>
      <div class="an-th"><i style=${{ background: cols[i] }}></i><b>${t.name}</b><small class="an-grow">${t.desc}</small><b>${t.students.length} 人 · ${pct(t.students.length / total)}</b></div>
      ${t.students.length > 0 && html`<div class="an-chips in">${shown.map((x) => html`<button class="an-chip" style=${{ background: cols[i] + '1f' }} onClick=${(e) => { e.stopPropagation(); open({ view: 'student', id: x.student.id }); }}>${x.student.name}</button>`)}${t.students.length > shown.length && html`<span class="muted">+${t.students.length - shown.length}</span>`}</div>`}</div>`; })}
  </div></${Card}>`;
}

// ---------------- 分析报告（文字） ----------------
function ReportSheet({ c, rules, spec, report, onClose }) {
  const txt = useMemo(() => reportText(c, report || buildReport(c, rules, spec), Date.now(), examReport(c)), [c, rules, report]);
  const [msg, setMsg] = useState('');
  const copy = async () => { try { await navigator.clipboard.writeText(txt); setMsg('已复制分析报告'); } catch { setMsg('复制失败：请长按下面的文字手动复制'); } };
  const download = () => { const a = document.createElement('a'); a.href = URL.createObjectURL(new Blob([txt], { type: 'text/plain;charset=utf-8' })); a.download = `${c.name}_分析报告.txt`.replace(/\//g, '_'); a.click(); setTimeout(() => URL.revokeObjectURL(a.href), 1000); };
  return html`<div class="sheet-bg" onClick=${onClose}><div class="sheet" role="dialog" aria-label="分析报告" onClick=${(e) => e.stopPropagation()}>
    <div class="sheet-h"><b>一键生成分析报告</b><button class="ib" onClick=${onClose} aria-label="关闭">✕</button></div>
    <div class="sheet-btns"><button class="btn1" onClick=${copy}>复制文字报告</button><button class="btn2" onClick=${download}>下载文字报告</button></div>
    ${msg && html`<div class="an-note">${msg}</div>`}
    <pre class="sheet-txt">${txt}</pre></div></div>`;
}

// ---------------- 考试 ----------------
function Exams({ kit, c, rules, open }) {
  const { Seg, Empty, Avatar, Cell } = kit;
  const subjects = useMemo(() => [...new Set((c.exams || []).map((e) => e.subject).filter(Boolean))], [c]);
  const [si, setSi] = useKeep('examSubject');
  const idx = si > subjects.length ? 0 : si;
  const r = useMemo(() => examReport(c, subjects[idx - 1] ?? null), [c, idx, subjects.join()]);
  const corr = useMemo(() => buildReport(c, rules, { range: 'ALL' }).examCorrelation, [c, rules]);
  const sub = subjects.length > 1 && html`<div class="pad"><${Seg} items=${['全部', ...subjects].map((l, i) => [i, l])} value=${idx} onChange=${setSi} /></div>`;
  if (!r.exams.length) return html`${sub}<${Empty} emoji="📝" text="还没有考试成绩：老师录入考试后，这里会出现均分走势、名次进退和薄弱学生" />`;
  const ins = [...r.insights, ...(corr != null && Math.abs(corr) >= 0.3 ? [{ level: 'INFO', text: `作业交齐率与考试得分率的相关系数 r = ${corr.toFixed(2)}（${corr > 0 ? '正' : '负'}相关）。` }] : [])];
  const last = r.exams[r.exams.length - 1]; const ls = r.stats[last.id];
  const prevE = r.exams[r.exams.length - 2]; const ps = prevE ? r.stats[prevE.id] : null;
  const d = (f) => (ps ? f(ls) - f(ps) : null);
  const q = r.quality[r.quality.length - 1];
  const lvCols = [GOOD, BLUE, WARN, BAD]; const lvTotal = Math.max(1, r.levels.reduce((s, x) => s + x[1], 0));
  const stu = r.students.filter((x) => x.avgPct != null).sort((a, b) => b.avgPct - a.avgPct);
  return html`${sub}
    <${Card} title="主要发现"><${Insights} items=${ins} /></${Card}>
    <div class="tiles two">
      <${Kpi} label="最近平均得分率" value=${pct(ls.avg / last.fullScore)} delta=${ps ? ls.avg / last.fullScore - ps.avg / prevE.fullScore : null} />
      <${Kpi} label="及格率" value=${pct(ls.passRate)} delta=${d((x) => x.passRate)} />
      <${Kpi} label="优秀率" value=${pct(ls.excellentRate)} delta=${d((x) => x.excellentRate)} />
      <${Kpi} label="低分率" value=${pct(ls.lowRate)} delta=${d((x) => x.lowRate)} goodWhenUp=${false} />
      ${q && html`<${Kpi} label="难度" value=${difficultyText(q.difficulty)} /><${Kpi} label="区分度" value=${q.discrimination.toFixed(2)} />`}
    </div>
    <div class="meta">最近一场：${last.title} · 平均 ${fmtNum(ls.avg)} / ${fmtNum(last.fullScore)} · 标准差 ${fmtNum(ls.std)}${q ? ` · 区分度${discriminationText(q.discrimination)}` : ''}${r.exams.length > 1 ? ' · 对比上一场' : ''}</div>
    ${(r.stars.length > 0 || r.watch.length > 0) && html`<${ExamProgress} r=${r} open=${open} Avatar=${Avatar} />`}
    ${r.avgPct.length >= 2 && html`<${Card} title="平均得分率走势"><${Line} values=${r.avgPct.map((x) => x[1])} labels=${r.avgPct.map((x) => md(x[0].date))} tips=${r.avgPct.map(([e, v]) => `${md(e.date)} ${e.title} 平均得分率 ${pct(v)}`)} /></${Card}>`}
    <${Card} title="各场考试">${[...r.exams].reverse().map((e) => { const s = r.stats[e.id]; return html`<${Cell} key=${e.id} title=${`${e.subject || ''} ${e.title}`.trim()}
      sub=${`平均 ${fmtNum(s.avg)}/${fmtNum(e.fullScore)} · 及格 ${pct(s.passRate)} · 优秀 ${pct(s.excellentRate)} · ${s.count} 人`} onClick=${() => open({ view: 'exam', id: e.id })} />`; })}</${Card}>
    ${r.levels.some((x) => x[1] > 0) && html`<${Card} title="成绩层级（按平均得分率）"><div class="an-pad"><div class="an-split">${r.levels.map(([, n], i) => n > 0 && html`<i style=${{ flex: n, background: lvCols[i] }}></i>`)}</div>
      <div class="legend in">${r.levels.map(([l, n], i) => html`<span><i style=${{ background: lvCols[i] }}></i>${l} ${n} 人（${pct(n / lvTotal)}）</span>`)}</div>
      <div class="muted" style=${{ fontSize: '11px' }}>${'优秀 ≥85%，良好 75–85%，及格 60–75%，不及格 <60%'}</div></div></${Card}>`}
    ${r.flow && html`<${Card} title=${`层级流动：${r.flow.from.title} → ${r.flow.to.title}`}><${FlowTable} f=${r.flow} /></${Card}>`}
    ${r.quality.length >= 2 && html`<${Card} title="各场难度与区分度"><table class="an-table"><thead><tr><th>考试</th><th>平均得分率</th><th>难度</th><th>区分度</th></tr></thead><tbody>
      ${[...r.quality].reverse().map((x) => html`<tr class="tap" onClick=${() => open({ view: 'exam', id: x.exam.id })}><td>${`${x.exam.subject || ''} ${x.exam.title}`.trim()}</td><td><b>${pct(x.difficulty)}</b></td><td>${difficultyText(x.difficulty)}</td>
        <td style=${{ color: x.discrimination < 0.2 ? WARN : null }}>${x.discrimination.toFixed(2)} ${discriminationText(x.discrimination)}</td></tr>`)}</tbody></table>
      <${Note}>${'区分度 = 前 27% 与后 27% 学生的得分率之差：≥0.4 很好，0.3–0.4 良好，0.2–0.3 一般，<0.2 偏低。'}</${Note}></${Card}>`}
    ${(r.nearPass.length > 0 || r.nearExcellent.length > 0) && html`<${Card} title="临界生（重点提升对象）"><div class="an-pad">
      ${r.nearPass.length > 0 && html`<${Chips} title="差一点及格（55%–60%）" items=${r.nearPass.map((x) => [x.student, pct(x.avgPct)])} color=${WARN} open=${open} />`}
      ${r.nearExcellent.length > 0 && html`<${Chips} title="差一点优秀（80%–85%）" items=${r.nearExcellent.map((x) => [x.student, pct(x.avgPct)])} color=${BLUE} open=${open} />`}</div></${Card}>`}
    ${(r.rising.length > 0 || r.falling.length > 0 || r.steady.length > 0) && html`<${Card} title="成绩走向"><div class="an-pad">
      ${r.rising.length > 0 && html`<${Chips} title="连续三场上升" items=${r.rising.map((x) => [x.student, pct(x.avgPct)])} color=${GOOD} open=${open} />`}
      ${r.falling.length > 0 && html`<${Chips} title="连续三场下滑" items=${r.falling.map((x) => [x.student, pct(x.avgPct)])} color=${BAD} open=${open} />`}
      ${r.steady.length > 0 && html`<${Chips} title="成绩稳定（波动 < 5 个百分点）" items=${r.steady.map((x) => [x.student, pct(x.avgPct)])} color=${PURPLE} open=${open} />`}
      <div class="muted" style=${{ fontSize: '11px' }}>按每场相对班级平均的位置判断，不受各场难度影响。</div></div></${Card}>`}
    ${(r.diligentLow.length > 0 || r.skipButGood.length > 0) && html`<${Card} title="作业与考试对照"><div class="an-pad">
      ${r.diligentLow.length > 0 && html`<${Chips} title="作业交得齐但考试不理想（交齐 ≥90%，考试 <60%）" items=${r.diligentLow.map((x) => [x.student, `交${pct(x.hwRate)} 考${pct(x.examPct)}`])} color=${WARN} open=${open} />`}
      ${r.skipButGood.length > 0 && html`<${Chips} title="作业常缺但考试不错（交齐 <75%，考试 ≥80%）" items=${r.skipButGood.map((x) => [x.student, `交${pct(x.hwRate)} 考${pct(x.examPct)}`])} color=${BLUE} open=${open} />`}</div></${Card}>`}
    ${r.imbalance.length > 0 && html`<${Card} title="偏科（最好与最差科目相差 ≥ 15 个百分点）">${r.imbalance.map((x) => html`<${Cell} key=${x.student.id} title=${x.student.name}
      sub=${`${x.best[0]} ${pct(x.best[1])} · ${x.worst[0]} ${pct(x.worst[1])} · 相差 ${Math.trunc((x.best[1] - x.worst[1]) * 100)} 个百分点`} onClick=${() => open({ view: 'student', id: x.student.id })} />`)}</${Card}>`}
    ${r.genders.length === 2 && html`<${Card} title="男女生对比"><${HBars} rows=${r.genders.map((g) => [`${g[0]}生 ${g[1]}人`, g[2], pct(g[2])])} /></${Card}>`}
    ${r.subjects.length > 1 && html`<${Card} title="各科平均得分率"><${HBars} rows=${r.subjects.map((x) => [x[0], x[1], pct(x[1])])} /></${Card}>`}
    ${(r.improvers.length > 0 || r.decliners.length > 0) && html`<${Card} title="名次进退（与上一场同科考试比）"><div class="an-two">
      ${[['进步', r.improvers, GOOD, (x) => `↑${x.rankChange}`], ['退步', r.decliners, BAD, (x) => `↓${-x.rankChange}`]].map(([t, l, col, tag]) => html`<div><div class="an-sub">${t}</div>
        ${l.length ? l.map((x) => html`<button class="an-mini" onClick=${() => open({ view: 'student', id: x.student.id })}><span>${x.student.name}</span><b style=${{ color: col }}>${tag(x)}</b></button>`) : html`<div class="muted">—</div>`}</div>`)}</div></${Card}>`}
    <${Card} title="学生考试明细（平均得分率从高到低）"><table class="an-table"><thead><tr><th>学生</th><th>平均</th><th>最高/最低</th><th>稳定性</th><th>名次</th></tr></thead><tbody>
      ${stu.map((x) => html`<tr key=${x.student.id} class="tap" onClick=${() => open({ view: 'student', id: x.student.id })}><td>${x.student.name}</td><td><b style=${{ color: x.avgPct < 0.6 ? BAD : null }}>${pct(x.avgPct)}</b></td>
        <td>${pct(x.bestPct)}/${pct(x.worstPct)}</td><td>${x.stdPct == null ? '—' : x.stdPct < 0.06 ? '稳定' : x.stdPct < 0.12 ? '一般' : '波动大'}</td><td>${x.lastRank ?? '—'}</td></tr>`)}</tbody></table></${Card}>
    ${r.groups.length > 1 && html`<${Card} title="小组平均得分率"><${HBars} rows=${r.groups.map((x) => [x[0], x[1], pct(x[1])])} /></${Card}>`}`;
}
function ExamProgress({ r, open, Avatar }) {
  const [tab, setTab] = useState(0); const list = tab === 0 ? r.stars : r.watch;
  const rel = (x) => { const v = Math.trunc(x * 100); return v >= 0 ? `高于平均 ${v}` : `低于平均 ${-v}`; };
  return html`<${Card} title="考试进步之星 · 退步关注"><div class="an-pad0">
    <${Tabs2} items=${[`进步之星 ${r.stars.length}`, `退步关注 ${r.watch.length}`]} value=${tab} onChange=${setTab} />
    <div class="an-note">比较最近一场与之前各场“比班级平均高/低多少”，消除各场难度不同的影响。</div>
    ${!list.length && html`<div class="an-pad muted">${tab === 0 ? '暂时没有明显进步的同学（相对位置提高 5 个百分点以上）。' : '没有明显退步的同学。'}</div>`}
    ${list.map((x) => html`<button class="an-row" key=${x.student.id} onClick=${() => open({ view: 'student', id: x.student.id })}><${Avatar} name=${x.student.name} size=${36} />
      <div class="an-grow"><b>${x.student.name} <${Tag} v=${Math.trunc(x.delta * 100)} /></b><small>「${x.last.title}」${pct(x.lastPct)}，${rel(x.after)}（之前${rel(x.before)}）</small></div></button>`)}</div></${Card}>`;
}
function FlowTable({ f }) {
  const max = Math.max(1, ...f.matrix.flat());
  return html`<div class="an-pad"><table class="an-flow"><thead><tr><th>上场 → 本场</th>${f.labels.map((l) => html`<th>${l}</th>`)}</tr></thead><tbody>
    ${f.matrix.map((row, i) => html`<tr><td>${f.labels[i]}</td>${row.map((n, j) => { const tint = j < i ? GOOD : j > i ? BAD : BLUE;
      return html`<td><i style=${{ background: n === 0 ? 'var(--track)' : tint, opacity: n === 0 ? 0.5 : 0.18 + 0.6 * n / max }}></i><b>${n || ''}</b></td>`; })}</tr>`)}</tbody></table>
    <div class="muted" style=${{ fontSize: '12px', marginTop: '8px' }}>升档 ${f.up} 人 · 降档 ${f.down} 人 · 不变 ${f.same} 人（绿色为升档，红色为降档）</div></div>`;
}

// ---------------- 关注 ----------------
function Spark({ values, color, w = 48, hgt = 22 }) {
  if (!values || values.length < 2) return html`<span style=${{ width: w + 'px' }}></span>`;
  const hi = Math.max(100, ...values); const x = (i) => w * i / (values.length - 1); const y = (v) => hgt - 2 - (hgt - 4) * v / hi;
  return html`<svg width=${w} height=${hgt} aria-hidden="true"><path d=${values.map((v, i) => `${i ? 'L' : 'M'}${x(i).toFixed(1)},${y(v).toFixed(1)}`).join(' ')} fill="none" stroke=${color} stroke-width="1.6" stroke-linejoin="round" /></svg>`;
}
function AttentionTab({ kit, c, rules, open, us }) {
  const [att, setAtt] = useKeep('att');
  const list = useMemo(() => attention(c, rules), [c, rules]);
  return html`<div class="pad"><${Tabs2} items=${['榜单', '股票']} value=${att} onChange=${setAtt} /></div>
    ${att === 0 ? html`<${AttentionBoard} kit=${kit} c=${c} rules=${rules} list=${list} open=${open} />` : html`<${StockTab} kit=${kit} c=${c} rules=${rules} list=${list} open=${open} us=${us} />`}`;
}
function AttentionBoard({ kit, c, rules, list, open }) {
  const { Empty, Avatar } = kit;
  const weekly = useLater(() => attentionWeekly(c, rules), [c, rules]) || {};
  if (!list.length) return html`<${Empty} emoji="🎯" text="还没有学生" />`;
  const by = Object.fromEntries(ATT_LEVELS.map(([l]) => [l, list.filter((x) => x.level === l).length]));
  return html`<section class="grp"><div class="an-pad"><b>关注度指数（0–100，越高越需要关注）</b>
      <div class="muted" style=${{ fontSize: '12px', margin: '6px 0 10px' }}>由缺交风险 30%、连续未交 15%、交齐差距 15%、近期下滑 10%、考试偏弱 15%、考试退步 5%、积分靠后 10% 算出；数据一变立即重算，走势为最近 8 周每周一个点。</div>
      <div class="an-split">${ATT_LEVELS.map(([l, , col]) => by[l] > 0 && html`<i style=${{ flex: by[l], background: col }}></i>`)}</div>
      <div class="an-levels">${ATT_LEVELS.map(([l, label, col]) => html`<div><b style=${{ color: col }}>${by[l]}</b><small>${label}</small></div>`)}</div></div></section>
    <div class="st">关注榜 · 点同学查看详情</div>
    <section class="grp">${list.map((x, i) => { const w = weekly[x.student.id] || []; const delta = w.length >= 2 ? Math.round(x.index - w[w.length - 2]) : 0; const col = attColor(x.level);
      return html`<button class="an-row" key=${x.student.id} onClick=${() => open({ view: 'student', id: x.student.id })}>
        <b class="an-idx" style=${{ color: i < 3 && x.level !== 'OK' ? col : 'var(--sub)' }}>${i + 1}</b><${Avatar} name=${x.student.name} size=${36} />
        <div class="an-grow"><b>${x.student.name} <em class="an-tag" style=${{ color: col, background: col + '1f' }}>${attLabel(x.level)}</em></b><small>${x.top.slice(0, 2).map((p) => p.detail).join(' · ') || '各项正常'}</small></div>
        <${Spark} values=${w} color=${col} />
        <div class="an-att"><b style=${{ color: col }}>${Math.round(x.index)}</b><small style=${{ color: delta > 0 ? BAD : delta < 0 ? GOOD : 'var(--sub)' }}>${delta > 0 ? `▲${delta}` : delta < 0 ? `▼${-delta}` : '持平'}</small></div></button>`; })}</section>`;
}

const p2 = (x) => x.toFixed(2);
const s2 = (x) => (x > 0.005 ? '+' + p2(x) : x < -0.005 ? '-' + p2(Math.abs(x)) : '0.00');
const pctOf = (q) => (q.pct == null ? '—' : `${q.pct > 0.00005 ? '+' : q.pct < -0.00005 ? '-' : ''}${Math.abs(q.pct * 100).toFixed(2)}%`);
const candleLabel = (k) => (k.end - k.start <= 86400000 ? md(k.start) : `${md(k.start)}–${md(k.end - 1)}`);
function StockChart({ q, kline, sc, sel, onSel }) {
  const n = q.candles.length; if (!n) return null;
  const W = 340; const H = 190; const VH = 36; const P = 4;
  let lo = Math.min(q.low, q.base); let hi = Math.max(q.high, q.base);
  if (hi - lo < 2) { const m = (hi + lo) / 2; lo = m - 1; hi = m + 1; }
  const pad = (hi - lo) * 0.08; lo = Math.max(0, lo - pad); hi += pad;
  const y = (v) => P + (H - VH - 2 * P) * (1 - (v - lo) / (hi - lo));
  const bw = W / n; const maxV = Math.max(1, ...q.candles.map((k) => k.volume));
  const color = sc(q.change);
  const pts = q.closes.map((v, i) => [W * i / n, y(v)]);
  const pick = (e) => { const r = e.currentTarget.getBoundingClientRect(); const x = (e.clientX - r.left) / r.width * W; onSel(Math.max(0, Math.min(n - 1, kline ? Math.floor(x / bw) : Math.round(x / W * n) - 1))); };
  const sx = sel == null ? null : kline ? (sel + 0.5) * bw : W * (sel + 1) / n;
  return html`<svg class="an-stock" viewBox=${`0 0 ${W} ${H}`} preserveAspectRatio="none" onPointerMove=${pick} onPointerDown=${pick} onPointerLeave=${() => onSel(null)} role="img" aria-label="行情图">
    <line x1="0" x2=${W} y1=${y(q.base)} y2=${y(q.base)} stroke="var(--sub)" stroke-dasharray="3 4" stroke-width="1" vector-effect="non-scaling-stroke" />
    ${kline ? q.candles.map((k, i) => { const col = k.close >= k.open ? sc(1) : sc(-1); const cx = (i + 0.5) * bw; const top = y(Math.max(k.open, k.close)); const bot = y(Math.min(k.open, k.close));
      return html`<g><line x1=${cx} x2=${cx} y1=${y(k.high)} y2=${y(k.low)} stroke=${col} stroke-width="1" vector-effect="non-scaling-stroke" /><rect x=${cx - bw * 0.32} width=${bw * 0.64} y=${top} height=${Math.max(1, bot - top)} fill=${col} /></g>`; })
      : html`<path d=${pts.map((p, i) => `${i ? 'L' : 'M'}${p[0].toFixed(1)},${p[1].toFixed(1)}`).join(' ') + ` L${W},${H - VH - P} L0,${H - VH - P} Z`} fill=${color} opacity="0.12" />
        <path d=${pts.map((p, i) => `${i ? 'L' : 'M'}${p[0].toFixed(1)},${p[1].toFixed(1)}`).join(' ')} fill="none" stroke=${color} stroke-width="2" stroke-linejoin="round" vector-effect="non-scaling-stroke" />`}
    ${q.candles.map((k, i) => html`<rect x=${i * bw + bw * 0.15} width=${bw * 0.7} y=${H - k.volume / maxV * (VH - 4)} height=${k.volume / maxV * (VH - 4)} fill=${sc(k.close - k.open)} opacity="0.45" />`)}
    ${sx != null && html`<line x1=${sx} x2=${sx} y1="0" y2=${H} stroke="var(--fg)" stroke-width="1" opacity="0.5" vector-effect="non-scaling-stroke" />`}
  </svg>`;
}
function QuoteHeader({ q, sel, spanLabel, sc }) {
  const k = sel == null ? null : q.candles[sel]; const v = k ? k.close : q.last; const ch = v - q.base;
  const pc = q.base < 1 ? '—' : `${ch >= 0 ? '+' : '-'}${Math.abs(ch / q.base * 100).toFixed(2)}%`;
  return html`<div class="an-quote"><b>${p2(v)}</b><div><span style=${{ color: sc(ch) }}>${s2(ch)} (${pc})</span><small>${k ? candleLabel(k) : `近${spanLabel}`}</small></div></div>`;
}
function MiniChart({ q, color }) {
  const v = q.closes; if (v.length < 2) return null;
  let lo = Math.min(...v); let hi = Math.max(...v); if (hi - lo < 1) { lo -= 0.5; hi += 0.5; }
  const W = 70; const H = 30; const x = (i) => W * i / (v.length - 1); const y = (t) => 2 + (H - 4) * (1 - (t - lo) / (hi - lo));
  return html`<svg width=${W} height=${H} aria-hidden="true"><line x1="0" x2=${W} y1=${y(q.base)} y2=${y(q.base)} stroke="var(--sub)" stroke-dasharray="2 3" stroke-width="0.8" />
    <path d=${v.map((t, i) => `${i ? 'L' : 'M'}${x(i).toFixed(1)},${y(t).toFixed(1)}`).join(' ')} fill="none" stroke=${color} stroke-width="1.6" /></svg>`;
}
function StockTab({ kit, c, rules, list, open, us }) {
  const { Empty } = kit;
  const [kind, setKind] = useKeep('kind'); const [span, setSpan] = useKeep('span'); const [kline, setKline] = useKeep('kline');
  const [pill, setPill] = useKeep('pill'); const [sort, setSort] = useKeep('sort');
  const [sel, setSel] = useState(null);
  const board = useLater(() => (kind === 0 ? stockBoard(c, rules, span) : groupStockBoard(c, rules, span)), [c, rules, span, kind]);
  const up = us ? '#12a866' : '#e5433a'; const down = us ? '#e5433a' : '#12a866';
  const sc = (x) => (x > 0.005 ? up : x < -0.005 ? down : '#8e8e93');
  if (!c.students.length) return html`<${Empty} emoji="📈" text="还没有学生" />`;
  const head = html`<div class="pad an-picker"><${Tabs2} items=${['个人 · 关注度', '小组 · 综合分']} value=${kind} onChange=${(v) => { setKind(v); setSel(null); }} />
    <${Tabs2} items=${SPANS.map((s) => s[1])} value=${SPANS.findIndex((s) => s[0] === span)} onChange=${(i) => { setSpan(SPANS[i][0]); setSel(null); }} /></div>`;
  if (board === undefined) return html`${head}<div class="empty">正在计算行情…</div>`;
  if (board === null) return html`${head}<${Empty} emoji="👥" text="还没有小组（每组至少 2 人）" />`;
  const m = board.market; const spanLabel = SPANS.find((s) => s[0] === board.span)[1];
  const isGroup = kind === 1;
  const att = Object.fromEntries(list.map((x) => [x.student.id, x]));
  const rows = isGroup
    ? Object.entries(board.stocks).map(([g, q]) => ({ id: g, title: g, sub: `${c.students.filter((s) => s.group === g).length} 人`, q, val: q.last, onClick: () => open({ view: 'group', id: g }) }))
    : c.students.filter((s) => board.stocks[s.id]).map((s) => ({ id: s.id, no: s.no, title: s.no ? `No.${s.no}` : s.name, sub: s.name + (att[s.id]?.top[0] ? ` · ${att[s.id].top[0].label}` : ''), q: board.stocks[s.id], val: att[s.id]?.index ?? board.stocks[s.id].last, level: att[s.id]?.level, name: s.name, onClick: () => open({ view: 'student', id: s.id }) }));
  const sorted = [...rows].sort([(a, b) => b.val - a.val, (a, b) => b.q.change - a.q.change, (a, b) => a.q.change - b.q.change, (a, b) => compareStudentNumbers(a.no || '', b.no || '') || (a.title < b.title ? -1 : 1)][sort]);
  const ups = rows.filter((x) => x.q.change > 0.005).sort((a, b) => b.q.change - a.q.change); const downs = rows.filter((x) => x.q.change < -0.005).sort((a, b) => a.q.change - b.q.change);
  return html`${head}
    <section class="grp an-pad"><div class="an-mkt"><b>${isGroup ? 'GRP' : 'ATTN'}</b><small>${isGroup ? '小组综合指数 · 各组平均' : '关注指数 · 全班平均'}</small></div>
      <${QuoteHeader} q=${m} sel=${sel} spanLabel=${spanLabel} sc=${sc} />
      <div class="an-toggles"><${Tabs2} items=${['走势', 'K 线']} value=${kline ? 1 : 0} onChange=${(v) => setKline(v === 1)} /></div>
      <${StockChart} q=${m} kline=${kline} sc=${sc} sel=${sel} onSel=${setSel} />
      <div class="an-keys">${[['开盘', p2(m.base)], ['最高', p2(m.high)], ['前收', p2(m.prevClose)], ['最低', p2(m.low)], ['成交量', `${m.volume}`], ['振幅', m.base < 1 ? '—' : `${((m.high - m.low) / m.base * 100).toFixed(2)}%`], ['上涨', `${board.up} 家`], ['下跌', `${board.down} 家`]].map(([l, v]) => html`<div><small>${l}</small><b>${v}</b></div>`)}</div>
      <div class="muted" style=${{ fontSize: '11px', marginTop: '8px' }}>${isGroup ? '综合分越高越好：上涨表示小组在变好。成交量为这段时间的记录条数（作业登记、考试成绩）。' : '指数越高越需要关注：上涨表示情况变差，下跌表示在好转。成交量为这段时间的记录条数（作业登记、加减分、考试成绩）。'}</div></section>
    <div class="an-movers">${[['领涨', ups], ['领跌', downs]].map(([t, l]) => html`<section class="grp an-pad"><b>${t}</b>${l.length ? l.slice(0, 3).map((x) => html`<button class="an-mini" onClick=${x.onClick}><span>${x.name || x.title}</span><b style=${{ color: sc(x.q.change) }}>${s2(x.q.change)}</b></button>`) : html`<div class="muted">无</div>`}</section>`)}</div>
    <div class="st">${isGroup ? '小组板块' : '自选股'} · 点胶囊切换涨跌额 / 涨跌幅${isGroup ? '' : ' / 等级'}</div>
    <div class="chips">${['关注度', '涨幅', '跌幅', isGroup ? '名称' : '学号'].map((t, i) => html`<button class=${'chip' + (sort === i ? ' on' : '')} onClick=${() => setSort(i)}>${isGroup && i === 0 ? '综合分' : t}</button>`)}</div>
    <section class="grp">${sorted.map((x) => { const col = sc(x.q.change); const mode = isGroup ? pill % 2 : pill;
      const txt = mode === 0 ? s2(x.q.change) : mode === 1 ? pctOf(x.q) : attLabel(x.level);
      const bg = mode === 2 ? attColor(x.level) : col;
      return html`<div class="an-row tap" key=${x.id} onClick=${x.onClick}><div class="an-grow"><b class="an-ticker">${x.title}</b><small>${x.sub}</small></div>
        <${MiniChart} q=${x.q} color=${col} /><div class="an-att"><b>${p2(x.val)}</b><button class="an-pill" style=${{ background: bg }} onClick=${(e) => { e.stopPropagation(); setPill((p) => (p + 1) % (isGroup ? 2 : 3)); }}>${txt}</button></div></div>`; })}</section>`;
}

// ---------------- 小组 ----------------
function Groups({ kit, c, rules, open }) {
  const { Empty } = kit;
  const r = useMemo(() => groupReport(c, rules), [c, rules]);
  const [a, setA] = useState(null); const [b, setB] = useState(null);
  if (!r.teams.length) return html`<${Empty} emoji="👥" text="还没有小组：在名单里给同学填写小组（每组至少 2 人）后，这里会出现小组对抗和进步分析" />`;
  const names = r.teams.map((t) => t.name).sort();
  const colorOf = Object.fromEntries(names.map((g, i) => [g, TEAM_COLORS[i % TEAM_COLORS.length]]));
  const gr = rules.groupRank || {}; const lr = rules.leader || {};
  const ga = a && names.includes(a) ? a : r.teams[0].name; const gb = b && names.includes(b) && b !== ga ? b : (r.teams.find((t) => t.name !== ga) || {}).name;
  const periodLabel = { assignment: '每次作业', day: '每天', month: '每月' };
  const nowP = r.periods[r.periods.length - 1];
  return html`<div class="st">小组对抗排名 · 综合分（交齐 50% · 少扣分 25% · 优秀 15% · 考试 10%）</div>
    <section class="grp">${r.teams.map((t) => html`<button class="an-row" key=${t.name} onClick=${() => open({ view: 'group', id: t.name })}>
      <b class="an-medal">${['🥇', '🥈', '🥉'][t.rank - 1] || t.rank}</b><i class="an-dot" style=${{ background: colorOf[t.name] }}></i>
      <div class="an-grow"><b>${t.name}${t.leader ? ` · 组长 ${t.leader.name}` : ''}</b><small>交齐 ${pct(t.rate)} · 人均扣分 ${fmtNum(t.deductPerCapita)} · 优秀 ${pct(t.excellentRate)}${t.examPct != null ? ` · 考试 ${pct(t.examPct)}` : ''}<br />胜 ${t.wins}${t.ties ? ` · 并列 ${t.ties}` : ''} · 周冠军 ${t.weekWins} · 全组交齐 ${t.complete}/${t.played}${gr.enabled && t.rankPoints ? ` · 排名奖惩每人 ${signed(t.rankPoints)}` : ''}</small></div>
      <div class="an-att"><b style=${{ color: colorOf[t.name], fontSize: '20px' }}>${Math.round(t.score * 100)}</b><small>综合 · 均分 ${fmtNum(t.avgScore)}</small></div></button>`)}</section>
    ${gr.enabled && nowP && html`<div class="st">本期排名 · ${periodLabel[gr.period] || '每周'} · ${nowP.label}</div><section class="grp">
      ${nowP.standings.map((s) => html`<button class="an-row" key=${s.group} onClick=${() => open({ view: 'group', id: s.group })}><small class="an-w60">第 ${s.rank} 名</small><i class="an-dot" style=${{ background: colorOf[s.group] || 'var(--accent)' }}></i>
        <div class="an-grow"><b>${s.group}</b><small>综合 ${Math.round(s.score * 100)} · 交齐 ${pct(s.rate)} · 人均扣分 ${fmtNum(s.deduct)} · 优秀 ${pct(s.excellent)}${s.exam != null ? ` · 考试 ${pct(s.exam)}` : ''}</small></div>
        <b style=${{ color: s.points > 0 ? GOOD : s.points < 0 ? BAD : 'var(--sub)' }}>${s.points ? `每人 ${signed(s.points)}` : '0'}</b></button>`)}
      <${Note}>第 1 名全组每人 ${signed(gr.first)}、第 2 名 ${signed(gr.second)}、第 3 名 ${signed(gr.third)}；3 组以上时最后一名 ${signed(gr.last)}。实时计入积分。</${Note}></section>`}
    ${gr.enabled && r.periods.length >= 2 && html`<${Card} title="历次排名"><div class="an-pad">${[...r.periods].reverse().slice(0, 12).map((p) => html`<div class="an-hist-row"><small>${p.label.replace(/ 这周$/, '')}</small>
      <div class="an-scroll">${p.standings.map((s) => { const col = colorOf[s.group] || BLUE; return html`<span style=${{ color: col, background: col + (s.rank === 1 ? '2e' : '14'), fontWeight: s.rank === 1 ? 700 : 400 }}>${s.rank}. ${s.group}${s.points ? ` ${signed(s.points)}` : ''}</span>`; })}</div></div>`)}</div></${Card}>`}
    ${r.teams.length >= 2 && r.weeks.length > 0 && html`<${Card} title="每周对抗 · 交齐率最高的组胜出"><div class="an-pad">
      ${[...r.weeks].reverse().map((w) => html`<div class="an-week"><small>${w.label}</small><div>${names.map((g) => { const v = w.rates[g];
        return html`<i title=${`${g} ${pct(v)}`} style=${{ background: colorOf[g], opacity: v == null ? 0.06 : 0.12 + 0.88 * Math.max(0, Math.min(1, (v - 0.5) / 0.5)) }}>${w.winners.includes(g) ? '★' : ''}</i>`; })}</div>
        <b>${w.winners.length ? w.winners.join('、') : '平'}</b></div>`)}
      <div class="legend in">${names.map((g) => html`<span><i style=${{ background: colorOf[g], borderRadius: '50%' }}></i>${g}</span>`)}<span>颜色越深交齐率越高（50% 以下最浅）· ★ 本周第一</span></div></div></${Card}>`}
    ${r.progress.length > 0 && html`<${Card} title=${`小组进步与退步 · ${r.progressBasis}`}>${r.progress.map((p) => { const v = Math.round(p.index * 100); const upq = v >= 2; const dn = v <= -2;
      return html`<button class="an-row" key=${p.group} onClick=${() => open({ view: 'group', id: p.group })}><span style=${{ fontSize: '18px' }}>${upq ? '📈' : dn ? '📉' : '➖'}</span>
        <div class="an-grow"><b style=${{ color: upq ? GOOD : dn ? BAD : null }}>${p.group}${upq ? ' · 进步' : dn ? ' · 退步' : ' · 持平'}</b><small>${p.reasons.join(' · ') || '变化不大'}</small></div>
        <b style=${{ color: upq ? GOOD : dn ? BAD : 'var(--sub)' }}>${p.index >= 0 ? '+' : ''}${v}</b></button>`; })}
      <${Note}>指数 = 交齐率变化 50% + 每次人均扣分减少 30% + 考试得分率变化 20%（没有考试时按比例分配）</${Note}></${Card}>`}
    ${gb && html`<${Duel} c=${c} r=${r} a=${ga} b=${gb} setA=${setA} setB=${setB} names=${names} colorOf=${colorOf} />`}
    ${r.teams.some((t) => t.leader) && html`<${Card} title=${`组长奖惩 · ${periodLabel[lr.period] || '每周'}（计入当前积分）`}>
      ${!lr.enabled ? html`<div class="an-pad muted">组长奖惩已关闭</div>` : r.teams.filter((t) => t.leader).sort((x, y) => y.leaderPoints - x.leaderPoints).map((t) => html`<button class="an-row" key=${t.name} onClick=${() => open({ view: 'group', id: t.name })}>
        <div class="an-grow"><b>${t.leader.name} · ${t.name}</b><small>${t.leaderPeriods} 段 · 全部作业人均扣分 ${fmtNum(t.deductPerCapita)}</small></div>
        <b style=${{ fontSize: '16px', color: t.leaderPoints > 0 ? GOOD : t.leaderPoints < 0 ? BAD : 'var(--sub)' }}>${signed(t.leaderPoints)}</b></button>`)}</${Card}>`}`;
}
function Duel({ c, r, a, b, setA, setB, names, colorOf }) {
  const d = useMemo(() => groupDuel(c, a, b), [c, a, b]);
  const ta = r.teams.find((t) => t.name === a); const tb = r.teams.find((t) => t.name === b);
  const pick = (which, n) => { if (which === 0) { if (n !== b) setA(n); else { setB(a); setA(n); } } else if (n !== a) setB(n); else { setA(b); setB(n); } };
  const Vs = ({ label, x, y, show, higher = true }) => {
    const max = Math.max(Math.abs(x), Math.abs(y), 1e-9); const ab = higher ? x > y + 1e-9 : x < y - 1e-9; const bb = higher ? y > x + 1e-9 : y < x - 1e-9;
    return html`<div class="an-vs"><div><b style=${{ color: ab ? colorOf[a] : null, fontWeight: ab ? 700 : 400 }}>${show(x)}</b><small>${label}</small><b style=${{ color: bb ? colorOf[b] : null, fontWeight: bb ? 700 : 400 }}>${show(y)}</b></div>
      <div><span><i style=${{ width: `${Math.max(2, Math.abs(x) / max * 100)}%`, background: colorOf[a], opacity: ab ? 1 : 0.4 }}></i></span><span><i style=${{ width: `${Math.max(2, Math.abs(y) / max * 100)}%`, background: colorOf[b], opacity: bb ? 1 : 0.4 }}></i></span></div></div>`;
  };
  return html`<${Card} title="两组对决"><div class="an-pad">
    ${[a, b].map((cur, w) => html`<div class="chips in">${names.map((n) => html`<button class=${'chip' + (n === cur ? ' on' : '')} style=${n === cur ? { background: colorOf[n] } : null} onClick=${() => pick(w, n)}>${n}</button>`)}</div>`)}
    <div class="an-duel"><b style=${{ color: colorOf[a] }}>${a}</b><strong>${d.aWins} : ${d.bWins}</strong><b style=${{ color: colorOf[b] }}>${b}</b></div>
    <div class="muted" style=${{ textAlign: 'center', fontSize: '11px', marginBottom: '8px' }}>每次作业比交齐率${d.draws ? ` · 平 ${d.draws} 次` : ''}</div>
    <${Vs} label="交齐率" x=${ta.rate} y=${tb.rate} show=${pct} />
    <${Vs} label="按时率" x=${ta.onTimeRate} y=${tb.onTimeRate} show=${pct} />
    <${Vs} label="优秀率" x=${ta.excellentRate} y=${tb.excellentRate} show=${pct} />
    <${Vs} label="平均积分" x=${ta.avgScore} y=${tb.avgScore} show=${fmtNum} />
    <${Vs} label="人均作业扣分" x=${ta.deductPerCapita} y=${tb.deductPerCapita} show=${fmtNum} higher=${false} />
    ${ta.examPct != null && tb.examPct != null && html`<${Vs} label="考试得分率" x=${ta.examPct} y=${tb.examPct} show=${pct} />`}
    <${Vs} label="最长连续全组交齐" x=${ta.longestComplete} y=${tb.longestComplete} show=${(v) => `${v} 次`} />
    <${Vs} label="周冠军" x=${ta.weekWins} y=${tb.weekWins} show=${(v) => `${v} 周`} />
    ${d.recent.length > 0 && html`<div class="an-sub" style=${{ marginTop: '8px' }}>最近 ${d.recent.length} 次</div>${d.recent.map(([x, ra, rb]) => html`<div class="an-recent">
      <b style=${{ color: colorOf[a], fontWeight: (ra ?? -1) > (rb ?? -1) ? 700 : 400 }}>${pct(ra)}</b><span>${x.title}</span><b style=${{ color: colorOf[b], fontWeight: (rb ?? -1) > (ra ?? -1) ? 700 : 400 }}>${pct(rb)}</b></div>`)}`}
  </div></${Card}>`;
}

