// 作业通 · 网页版：显示老师公开的数据，布局与手机端一致（大标题 + 卡片分组 + 悬浮底栏）。
// 设置里是本机的个性化显示选项。
import { h, render } from '../vendor/preact.module.js';
import { useState, useEffect, useMemo, useRef, useLayoutEffect } from '../vendor/hooks.module.js';
import htm from '../vendor/htm.module.js';
import {
  STATUS, normalizeData, summary, studentStats, compareStudentNumbers, autoPoints, ruleOf, fmtNum, signed, fmtDate,
  latestRecharges, entryCounts, assignmentCounts, rechargeCounts, examDelta, failedReview, plagiarized, copiedFrom, copiedBy, defaultPlagiarism, plagiarismFactor, defaultReview, plagiarismDesc, leaderPeriods, leaderOf, groupRankPoints, creditPoints, creditLevel,
} from './model.js';
import { api } from './cloud.js';
import { StatsPage, PlagGraph } from './statspage.js';
import { Icon as LIcon } from './icons.js';
import { StockDetail, GroupStockDetail } from './stockpage.js';
import { plagiarismReport } from './analytics.js';

const html = htm.bind(h);

// ---------------- 本机偏好（只存这台设备） ----------------
const PREF_KEY = 'hw.view';
const CACHE_KEY = 'hw.preview';
const DEF_PREFS = { theme: 'hyper', dark: 0, animations: false, glass: true, layout: 'grid', classId: null };
const loadPrefs = () => { try { return { ...DEF_PREFS, ...JSON.parse(localStorage.getItem(PREF_KEY) || '{}') }; } catch { return { ...DEF_PREFS }; } };
const PALETTES = {
  hyper: ['#3482ff', '#6e5bff', '澎湃蓝'], mint: ['#12c2a0', '#2f8fea', '薄荷清新'], academy: ['#3a6bff', '#6a3df0', '学院蓝'], sakura: ['#ff7ea5', '#b57bff', '樱花粉'],
  sunset: ['#ffa62b', '#ff5e62', '暖阳橙'], neon: ['#7f5cff', '#00d4ff', '赛博霓虹'], forest: ['#4c8c3f', '#1f5e54', '森林物语'],
  paper: ['#c79a63', '#8b5e34', '纸本手账'], grape: ['#9b59b6', '#3498db', '葡萄汽水'], ink: ['#37474f', '#90a4ae', '水墨极简'],
};

/** 状态显示：在原有状态上加“复审不合格” */
const REVIEW = { label: '复审不合格', short: '不合格', emoji: '⛔', color: '#b0306f' };
const PLAG = { label: '抄袭', short: '抄', emoji: '🕵️', color: '#d9480f' };
const view = (a, sid) => (plagiarized(a, sid) ? 'PLAG' : failedReview(a, sid) ? 'REVIEW' : a.statuses[sid] || 'NONE');
const SV = (k) => (k === 'PLAG' ? PLAG : k === 'REVIEW' ? REVIEW : STATUS[k] || STATUS.NONE);
const HANDED = new Set(['SUBMITTED', 'EXCELLENT', 'LATE']);
const pct = (x) => (x == null || !Number.isFinite(x) ? '—' : `${Math.round(x * 100)}%`);
const md = (t) => { const d = new Date(t); return `${d.getMonth() + 1}/${d.getDate()}`; };
const fmtTime = (t) => { const d = new Date(t); return `${d.getMonth() + 1}月${d.getDate()}日`; }; // 记录只显示日期
const rateColor = (r) => (r == null ? 'var(--sub)' : r >= 0.95 ? '#16a34a' : r >= 0.8 ? 'var(--accent)' : r >= 0.6 ? '#e8792c' : '#e5384d');

// ---------------- 图标 ----------------
const I = {
  home: 'M3 10.5 12 3l9 7.5M5 9v11h5v-6h4v6h5V9',
  people: 'M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM2 21v-1a6 6 0 0 1 6-6h2a6 6 0 0 1 6 6v1M16 3.5a4 4 0 0 1 0 7.5M22 21v-1a6 6 0 0 0-4-5.6',
  trophy: 'M8 21h8M12 17v4M7 4h10v5a5 5 0 0 1-10 0zM7 6H4a3 3 0 0 0 3 4M17 6h3a3 3 0 0 1-3 4',
  chart: 'M3 3v18h18M7 15l4-4 3 3 6-7',
  gear: 'M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 1 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z',
  back: 'M15 18l-6-6 6-6',
  chevron: 'M9 18l6-6-6-6',
  refresh: 'M21 12a9 9 0 1 1-2.64-6.36M21 3v6h-6',
};
const Icon = ({ d, size = 24 }) => html`<svg width=${size} height=${size} viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d=${d} /></svg>`;

const AV = ['#5b8def', '#12b886', '#f76707', '#ae3ec9', '#e64980', '#1098ad', '#7950f2', '#37b24d', '#f59f00', '#4263eb'];
const avatarColor = (name) => { let x = 0; for (const ch of name) x = (x * 31 + ch.charCodeAt(0)) >>> 0; return AV[x % AV.length]; };
const Avatar = ({ name = '?', size = 40 }) => html`<div class="av" style=${{ width: size + 'px', height: size + 'px', background: avatarColor(name), fontSize: size * 0.36 + 'px' }}>${name.slice(name.length >= 3 ? -2 : -1)}</div>`;

// ---------------- 通用组件（仿手机端 miuix） ----------------
function Page({ title, large = title, onBack, actions, children }) {
  const [scrolled, setScrolled] = useState(false);
  useEffect(() => {
    const f = () => setScrolled(window.scrollY > 44);
    f(); window.addEventListener('scroll', f, { passive: true });
    return () => window.removeEventListener('scroll', f);
  }, []);
  return html`<div class="pg">
    <header class=${'topbar' + (scrolled ? ' on' : '')}>
      ${onBack ? html`<button class="ib" onClick=${onBack} aria-label="返回"><${Icon} d=${I.back} /></button>` : html`<span class="ib"></span>`}
      <div class="tb-title">${title}</div>
      <div class="tb-act">${actions || html`<span class="ib"></span>`}</div>
    </header>
    <h1 class="lt">${large}</h1>
    ${children}
  </div>`;
}
const Title = ({ children }) => html`<div class="st">${children}</div>`;
const Group = ({ title, children, cls = '' }) => html`${title && html`<${Title}>${title}</${Title}>`}<section class=${'grp ' + cls}>${children}</section>`;
function Cell({ title, sub, right, left, onClick, cls = '' }) {
  const T = onClick ? 'button' : 'div';
  return html`<${T} class=${'cell ' + cls + (onClick ? ' tap' : '')} onClick=${onClick}>
    ${left}
    <div class="cell-main"><div class="cell-title">${title}</div>${sub != null && sub !== '' && html`<div class="cell-sub">${sub}</div>`}</div>
    ${right != null && html`<div class="cell-right">${right}</div>`}
    ${onClick && html`<span class="arrow"><${Icon} d=${I.chevron} size=${18} /></span>`}
  </${T}>`;
}
function Seg({ items, value, onChange }) {
  const i = Math.max(0, items.findIndex((x) => x[0] === value));
  return html`<div class="seg" role="tablist" style=${{ '--n': items.length, '--i': i }}><i class="seg-thumb"></i>
    ${items.map(([k, l]) => html`<button role="tab" aria-selected=${k === value} class=${k === value ? 'on' : ''} onClick=${() => onChange(k)}>${l}</button>`)}</div>`;
}
const Switch = ({ on, onChange, label }) => html`<label class="sw" aria-label=${label}><input type="checkbox" checked=${on} onChange=${(e) => onChange(e.target.checked)} /><i></i></label>`;
function Ring({ value, size = 84, stroke = 8, color = 'var(--accent)', children }) {
  const r = (size - stroke) / 2; const len = 2 * Math.PI * r; const v = Math.max(0, Math.min(1, value || 0));
  return html`<div class="ring" style=${{ width: size + 'px', height: size + 'px' }}>
    <svg width=${size} height=${size}><circle cx=${size / 2} cy=${size / 2} r=${r} stroke="var(--track)" stroke-width=${stroke} fill="none" />
      <circle class="ring-v" cx=${size / 2} cy=${size / 2} r=${r} stroke=${color} stroke-width=${stroke} fill="none" stroke-linecap="round"
        stroke-dasharray=${len} stroke-dashoffset=${len * (1 - v)} transform=${`rotate(-90 ${size / 2} ${size / 2})`} /></svg>
    <div class="ring-in">${children}</div></div>`;
}
const Tile = ({ label, value, color, onClick }) => html`<div class=${'tile' + (onClick ? ' tap' : '')} onClick=${onClick}><b style=${color ? { color } : null}>${value}</b><span>${label}</span></div>`;
const Pill = ({ k }) => { const s = SV(k); return html`<span class="pill" style=${{ color: s.color, background: s.color + '1f' }}>${s.label}</span>`; };
const Empty = ({ emoji = '📭', text }) => html`<div class="empty"><div class="empty-i"><${LIcon} name=${emoji} size=${44} stroke=${1.5} /></div>${text}</div>`;
/** 这次作业里这位同学的抄袭关系（“抄袭 甲” / “抄袭（提供者不明）” / “提供给 乙 抄”） */
const plagLine = (c, a, sid) => plagiarismDesc(c, a, sid);
const sortStudents = (list) => [...list].sort((a, b) => compareStudentNumbers(a.no, b.no) || a.name.localeCompare(b.name, 'zh'));

// ---------------- 计算 ----------------
function useStats(c, rules) {
  return useMemo(() => {
    const list = studentStats(c, rules);
    const ranked = [...list].sort((a, b) => b.score - a.score);
    const rank = new Map(); ranked.forEach((s, i) => rank.set(s.student.id, i + 1));
    const by = new Map(list.map((s) => [s.student.id, s]));
    return { list, ranked, rank, by };
  }, [c, rules]);
}
function handRate(c, sid, list = c.assignments) {
  let got = 0; let need = 0;
  for (const a of list) { const k = a.statuses[sid] || 'NONE'; if (k === 'LEAVE' || k === 'NONE') continue; need++; if (HANDED.has(k)) got++; }
  return need ? got / need : null;
}
/** 某位同学的积分流水（新 → 旧），与手机端一致 */
function ledger(c, rules, sid) {
  const last = latestRecharges(c)[sid];
  const rows = [];
  for (const e of c.scores) if (e.studentId === sid) rows.push({ key: 'm' + e.id, t: e.time, d: e.delta, title: e.reason || '加减分', kind: e.settlementId ? '结算' : '加减分', counts: entryCounts(e, last) });
  const pts = autoPoints(c, rules, sid);
  const pg = { ...defaultPlagiarism(), ...(rules.plagiarism || {}) };
  const resets = (c.recharges || []).filter((r) => (r.studentIds || []).includes(sid))
    .sort((x, y) => (x.effectiveAt ?? x.time) - (y.effectiveAt ?? y.time) || x.time - y.time || (x.id < y.id ? -1 : x.id > y.id ? 1 : 0));
  let round; let plag = 0;
  for (const a of [...c.assignments].sort((x, y) => x.createdAt - y.createdAt || (x.id < y.id ? -1 : 1))) {
    let r = null; for (const x of resets) if (rechargeCounts(x, a.createdAt)) r = x;
    if ((r && r.id) !== round) { round = r && r.id; plag = 0; }
    const k = view(a, sid); const d = pts[a.id] ?? ruleOf(rules, a.statuses[sid]);
    let extra = '';
    if (k === 'PLAG' && pg.enabled) { plag++; const f = plagiarismFactor(pg, plag); const rv = { ...defaultReview(), ...(rules.review || {}) }; const both = rv.enabled && failedReview(a, sid);
      extra = `（${plagLine(c, a, sid)}，本轮第 ${plag} 次，扣分 ×${fmtNum(f)}${both ? `，复审不合格再 ×${fmtNum(rv.multiplier)}，合计 ×${fmtNum(f * rv.multiplier)}` : ''}）`; }
    if (d) rows.push({ key: 'a' + a.id, t: a.createdAt, d, title: `${a.subject ? a.subject : ''}「${a.title}」${SV(k).label}${extra}`, kind: '作业', counts: assignmentCounts(a, last) });
  }
  for (const e of c.exams || []) {
    const d = examDelta(rules, e, sid);
    if (d) rows.push({ key: 'e' + e.id, t: e.date, d, title: `考试「${e.title}」${(e.absent || []).includes(sid) ? '缺考' : fmtNum(e.scores?.[sid] ?? 0) + ' 分'}`, kind: '考试', counts: !last || rechargeCounts(last, e.date) });
  }
  for (const x of groupRankPoints(c, rules)[sid] || []) rows.push({ key: 'g' + x.period.key, t: x.period.time, d: x.points, title: `小组排名（${x.standing.group}）${x.period.label}第 ${x.standing.rank} 名（综合 ${Math.round(x.standing.score * 100)}）`, kind: '小组排名', counts: !last || rechargeCounts(last, x.period.time) });
  for (const x of creditPoints(c, rules)[sid] || []) rows.push({ key: (x.group ? 'cg' : 'cp') + x.period.key, t: x.period.time, d: x.points, title: x.group ? `信誉分 ${x.period.label}：小组（${x.group}）信誉 ${fmtNum(x.credit)}（${creditLevel(x.credit)}）` : `信誉分 ${x.period.label}：个人信誉 ${fmtNum(x.credit)}（${creditLevel(x.credit)}）`, kind: '信誉分', counts: !last || rechargeCounts(last, x.period.time) });
  for (const p of leaderPeriods(c, rules)[sid] || []) if (p.points) rows.push({ key: 'l' + p.key, t: p.time, d: p.points, title: `组长（${p.group}）${p.label}：${p.parts.map(([t, v]) => `${t} ${signed(v)}`).join('；')}`, kind: '组长', counts: !last || rechargeCounts(last, p.time) });
  for (const r of c.recharges || []) if ((r.studentIds || []).includes(sid)) rows.push({ key: 'r' + r.id, t: r.effectiveAt ?? r.time, d: null, title: `积分重置为 ${fmtNum(r.target)}${r.note ? ' · ' + r.note : ''}`, kind: '重置', counts: true });
  return rows.sort((x, y) => y.t - x.t);
}
function examStats(c, e) {
  const vals = c.students.map((s) => e.scores?.[s.id]).filter((x) => x != null && Number.isFinite(x));
  const full = e.fullScore || 100;
  const avg = vals.length ? vals.reduce((a, b) => a + b, 0) / vals.length : null;
  return { n: vals.length, avg, max: vals.length ? Math.max(...vals) : null, min: vals.length ? Math.min(...vals) : null, pass: vals.length ? vals.filter((v) => v / full >= 0.6).length / vals.length : null, absent: (e.absent || []).length, full };
}

// ---------------- 首页 ----------------
function Home({ c, data, prefs, setPrefs, open }) {
  const list = useMemo(() => [...c.assignments].sort((a, b) => b.createdAt - a.createdAt), [c]);
  const [limit, setLimit] = useState(40);
  const latest = list[0];
  const sm = latest && summary(c, latest);
  const avg = list.length ? list.reduce((n, a) => n + summary(c, a).rate, 0) / list.length : null;
  const missing = list.reduce((n, a) => n + summary(c, a).counts.MISSING, 0);
  const classes = data.classes;
  const actions = classes.length > 1 && html`<select class="cls" value=${c.id} aria-label="切换班级" onChange=${(e) => setPrefs({ classId: e.target.value })}>${classes.map((x) => html`<option value=${x.id}>${x.name}</option>`)}</select>`;
  return html`<${Page} title=${c.name} large=${c.name} actions=${actions}>
    <div class="meta">${c.students.length} 人 · ${c.assignments.length} 次作业 · ${(c.exams || []).length} 场考试</div>
    ${latest ? html`<section class="grp hero tap" onClick=${() => open({ view: 'assign', id: latest.id })}>
      <${Ring} value=${sm.rate} color=${rateColor(sm.rate)}><b>${pct(sm.rate)}</b><span>交齐率</span></${Ring}>
      <div class="hero-main">
        <div class="hero-kicker">最近一次作业</div>
        <div class="hero-title">${latest.title}</div>
        <div class="cell-sub">${fmtDate(latest.createdAt)}${latest.subject ? ' · ' + latest.subject : ''}</div>
        <div class="hero-counts">
          ${(() => { const ks = c.students.map((x) => view(latest, x.id)); return ['SUBMITTED', 'EXCELLENT', 'LATE', 'MISSING', 'REVIEW', 'PLAG', 'LEAVE', 'NONE'].map((k) => [k, ks.filter((x) => x === k).length]).filter(([, n]) => n).map(([k, n]) => html`<span style=${{ color: SV(k).color }}>${SV(k).label} ${n}</span>`); })()}
        </div>
      </div>
    </section>` : html`<${Empty} emoji="📚" text="还没有作业" />`}
    <div class="tiles">
      <${Tile} label="作业" value=${list.length} />
      <${Tile} label="平均交齐率" value=${pct(avg)} color=${rateColor(avg)} />
      <${Tile} label="未交人次" value=${missing} color=${missing ? '#e5384d' : null} />
      <${Tile} label="考试" value=${(c.exams || []).length} onClick=${(c.exams || []).length ? () => open({ view: 'exams' }) : null} />
    </div>
    ${list.length > 0 && html`<${Group} title="作业">
      ${list.slice(0, limit).map((a) => { const s = summary(c, a); const rv = (a.reviewFailed || []).filter((id) => failedReview(a, id)).length; return html`<${Cell} key=${a.id}
        left=${html`<div class="subj" style=${{ background: avatarColor(a.subject || a.title) }}>${(a.subject || a.title || '作').slice(0, 1)}</div>`}
        title=${a.title}
        sub=${`${md(a.createdAt)}${a.subject ? ' · ' + a.subject : ''}${s.counts.MISSING - rv ? ` · 未交 ${s.counts.MISSING - rv}` : ''}${rv ? ` · 复审不合格 ${rv}` : ''}${s.counts.NONE ? ` · 未登记 ${s.counts.NONE}` : ''}`}
        right=${html`<div class="rate"><b style=${{ color: rateColor(s.rate) }}>${pct(s.rate)}</b><i><em style=${{ width: pct(s.rate), background: rateColor(s.rate) }}></em></i></div>`}
        onClick=${() => open({ view: 'assign', id: a.id })} />`; })}
    </${Group}>`}
    ${list.length > limit && html`<button class="more" onClick=${() => setLimit((n) => n + 40)}>显示更多（还有 ${list.length - limit} 次）</button>`}
  </${Page}>`;
}

// ---------------- 作业详情 ----------------
function AssignView({ c, rules, id, back, open, prefs, setPrefs }) {
  const a = c.assignments.find((x) => x.id === id);
  const [filter, setFilter] = useState('ALL');
  const pts = useMemo(() => (a ? Object.fromEntries(c.students.map((s) => [s.id, autoPoints(c, rules, s.id)[a.id] || 0])) : {}), [c, rules, id]);
  // 这次作业自己的抄袭关系图（每次作业各自一张图）
  const plag = useMemo(() => (a && Object.keys(a.plagiarism || {}).length ? plagiarismReport(c, rules, a.id) : null), [c, rules, id]);
  if (!a) return html`<${Page} title="作业" onBack=${back}><${Empty} text="这次作业已被删除" /></${Page}>`;
  const sm = summary(c, a);
  const keys = c.students.map((s) => view(a, s.id));
  const count = (k) => keys.filter((x) => x === k).length;
  const chips = [['ALL', `全部 ${c.students.length}`], ...['SUBMITTED', 'EXCELLENT', 'LATE', 'MISSING', 'REVIEW', 'PLAG', 'LEAVE', 'NONE'].filter((k) => count(k)).map((k) => [k, `${SV(k).label} ${count(k)}`])];
  const shown = sortStudents(c.students).filter((s) => filter === 'ALL' || view(a, s.id) === filter);
  return html`<${Page} title=${a.title} onBack=${back}>
    <div class="meta">${fmtDate(a.createdAt)}${a.subject ? ' · ' + a.subject : ''}</div>
    <section class="grp hero">
      <${Ring} value=${sm.rate} color=${rateColor(sm.rate)}><b>${pct(sm.rate)}</b><span>交齐率</span></${Ring}>
      <div class="hero-main">
        <div class="hero-title">已交 ${sm.handedIn} / 应交 ${sm.expected}</div>
        <div class="hero-counts">${['SUBMITTED', 'EXCELLENT', 'LATE', 'MISSING', 'REVIEW', 'PLAG', 'LEAVE', 'NONE'].filter((k) => count(k)).map((k) => html`<span style=${{ color: SV(k).color }}>${SV(k).label} ${count(k)}</span>`)}</div>
        ${a.countedBooks != null && html`<div class="cell-sub">数本数：${a.countedBooks} 本</div>`}
      </div>
    </section>
    ${plag && plag.cases.length > 0 && html`<${Title}>本次抄袭关系图 · ${plag.copiers} 人抄袭、${plag.providers} 人提供</${Title}>
      <section class="grp an-pad"><${PlagGraph} r=${plag} open=${open} />
        <div class="cell-sub" style=${{ paddingTop: '6px' }}>${[...plag.cases.filter((k) => k.unknown).map((k) => `提供者不明 → ${k.receiver.name}`), ...plag.edges.map((e) => `${e.provider.name} → ${e.receiver.name}`)].join('　')}</div></section>`}
    <div class="chips">${chips.map(([k, l]) => html`<button class=${'chip' + (filter === k ? ' on' : '')} onClick=${() => setFilter(k)}>${l}</button>`)}</div>
    <div class="row-between"><${Title}>同学 · ${shown.length} 人</${Title}><${Seg} items=${[['grid', '网格'], ['list', '列表']]} value=${prefs.layout} onChange=${(v) => setPrefs({ layout: v })} /></div>
    ${prefs.layout === 'list'
      ? html`<${Group}>${shown.map((s) => { const k = view(a, s.id); const d = pts[s.id]; return html`<${Cell} key=${s.id} left=${html`<${Avatar} name=${s.name} size=${38} />`} title=${s.name}
          sub=${[s.no, s.group, a.comments?.[s.id] && '评语：' + a.comments[s.id]].filter(Boolean).join(' · ')}
          right=${html`<span class="pts">${d ? html`<b style=${{ color: d > 0 ? '#16a34a' : '#e5384d' }}>${signed(d)}</b>` : ''}<${Pill} k=${k} /></span>`} onClick=${() => open({ view: 'student', id: s.id })} />`; })}</${Group}>`
      : html`<div class="grid">${shown.map((s) => { const k = view(a, s.id); const st = SV(k); const d = pts[s.id]; return html`<button key=${s.id} class="stu" style=${{ '--c': st.color }} onClick=${() => open({ view: 'student', id: s.id })}>
          <${Avatar} name=${s.name} size=${40} />
          <b>${s.name}</b><small>${s.no}</small>
          <span class="stu-st">${k === 'PLAG' ? plagLine(c, a, s.id) || st.label : st.label}${d ? html` <em style=${{ color: d > 0 ? '#16a34a' : '#e5384d' }}>${signed(d)}</em>` : ''}</span>
          ${a.comments?.[s.id] && html`<small class="cm"><${LIcon} name="message_circle" size=${11} /> ${a.comments[s.id]}</small>`}
        </button>`; })}</div>`}
  </${Page}>`;
}

// ---------------- 名单 ----------------
function Roster({ c, rules, open }) {
  const [mode, setMode] = useState('stu');
  const [q, setQ] = useState('');
  const st = useStats(c, rules);
  const list = sortStudents(c.students).filter((s) => !q || s.name.includes(q) || s.no.includes(q) || (s.group || '').includes(q));
  const groups = useMemo(() => {
    const m = new Map();
    for (const s of sortStudents(c.students)) { const g = s.group || '未分组'; if (!m.has(g)) m.set(g, []); m.get(g).push(s); }
    return [...m.entries()].sort((a, b) => a[0].localeCompare(b[0], 'zh', { numeric: true }));
  }, [c]);
  return html`<${Page} title="名单">
    <div class="meta">${c.students.length} 名同学 · ${groups.filter(([g]) => g !== '未分组').length} 个小组</div>
    <div class="pad"><${Seg} items=${[['stu', '学生'], ['grp', '小组']]} value=${mode} onChange=${setMode} /></div>
    ${mode === 'stu' ? html`
      <div class="pad"><input class="search" type="search" placeholder="搜索姓名、学号、小组" value=${q} onInput=${(e) => setQ(e.target.value)} /></div>
      ${list.length ? html`<${Group}>${list.map((s) => html`<${Cell} key=${s.id} left=${html`<${Avatar} name=${s.name} size=${40} />`} title=${s.name}
        sub=${[s.no, s.group, s.longLeave && '长期请假'].filter(Boolean).join(' · ')}
        right=${html`<b class="score">${fmtNum(st.by.get(s.id)?.score ?? 0)}</b>`} onClick=${() => open({ view: 'student', id: s.id })} />`)}</${Group}>` : html`<${Empty} emoji="🔍" text="没有找到" />`}`
    : html`<${Group}>${groups.map(([g, ms]) => {
        const avgScore = ms.reduce((n, s) => n + (st.by.get(s.id)?.score ?? 0), 0) / ms.length;
        const rates = ms.map((s) => handRate(c, s.id)).filter((x) => x != null);
        const r = rates.length ? rates.reduce((a, b) => a + b, 0) / rates.length : null;
        const leader = c.groupLeaders?.[g]; const lname = leader && c.students.find((s) => s.id === leader)?.name;
        return html`<${Cell} key=${g} left=${html`<div class="subj" style=${{ background: avatarColor(g) }}>${g.slice(0, 1)}</div>`} title=${g}
          sub=${`${ms.length} 人${lname ? ' · 组长 ' + lname : ''} · 平均积分 ${fmtNum(avgScore)}`} right=${html`<b style=${{ color: rateColor(r) }}>${pct(r)}</b>`} onClick=${() => open({ view: 'group', id: g })} />`;
      })}</${Group}>`}
  </${Page}>`;
}
// ---------------- 股票模式：个股详情 / 小组个股详情（与安卓一致） ----------------
function StockView({ c, rules, id, back, open, prefs, setPrefs }) {
  const s = c.students.find((x) => x.id === id);
  const t = s ? (s.no ? `No.${s.no}` : s.name) : '个股';
  return html`<${Page} title=${t} large=${s ? `${t} ${s.name}` : t} onBack=${back}><${StockDetail} c=${c} rules=${rules} id=${id} open=${open} us=${prefs.usColors !== false} setUs=${(v) => setPrefs({ usColors: v })} /></${Page}>`;
}
function GroupStockView({ c, rules, id, back, open, prefs, setPrefs }) {
  return html`<${Page} title=${id} large=${`${id} · 行情`} onBack=${back}><${GroupStockDetail} c=${c} rules=${rules} id=${id} open=${open} us=${prefs.usColors !== false} setUs=${(v) => setPrefs({ usColors: v })} /></${Page}>`;
}

function GroupView({ c, rules, id, back, open }) {
  const st = useStats(c, rules);
  const ms = sortStudents(c.students.filter((s) => (s.group || '未分组') === id));
  return html`<${Page} title=${id} onBack=${back}>
    <div class="meta">${ms.length} 人</div>
    <${Group}>${ms.map((s) => html`<${Cell} key=${s.id} left=${html`<${Avatar} name=${s.name} size=${38} />`} title=${s.name}
      sub=${`${s.no} · 交齐率 ${pct(handRate(c, s.id))}${c.groupLeaders?.[id] === s.id ? ' · 组长' : ''}`} right=${html`<b class="score">${fmtNum(st.by.get(s.id)?.score ?? 0)}</b>`} onClick=${() => open({ view: 'student', id: s.id })} />`)}</${Group}>
  </${Page}>`;
}

// ---------------- 学生档案 ----------------
function StudentView({ c, rules, id, back, open }) {
  const s = c.students.find((x) => x.id === id);
  const st = useStats(c, rules);
  const [more, setMore] = useState(false);
  const rows = useMemo(() => (s ? ledger(c, rules, id) : []), [c, rules, id]);
  if (!s) return html`<${Page} title="学生" onBack=${back}><${Empty} text="找不到这位同学" /></${Page}>`;
  const me = st.by.get(id);
  const hist = [...c.assignments].sort((a, b) => a.createdAt - b.createdAt);
  const keys = hist.map((a) => view(a, id));
  const cnt = (k) => keys.filter((x) => x === k).length;
  const r = handRate(c, id);
  const exams = [...(c.exams || [])].sort((a, b) => b.date - a.date);
  return html`<${Page} title=${s.name} onBack=${back}>
    <section class="grp hero">
      <${Avatar} name=${s.name} size=${64} />
      <div class="hero-main">
        <div class="hero-title">${s.name}</div>
        <div class="cell-sub">${[s.no, s.group, s.gender, s.longLeave && '长期请假'].filter(Boolean).join(' · ')}</div>
        ${(() => { const roles = [(c.reps || []).includes(s.id) && '课代表', s.group && leaderOf(c, s.group)?.id === s.id && `${s.group}组长`].filter(Boolean); return roles.length ? html`<div class="cell-sub" style=${{ color: 'var(--accent)', fontWeight: 600 }}>${roles.join(' · ')}</div>` : ''; })()}
        ${s.note && html`<div class="cell-sub"><${LIcon} name="notebook_pen" size=${12} /> ${s.note}</div>`}
      </div>
      <div class="big-score"><b>${fmtNum(me?.score ?? 0)}</b><span>积分 · 第 ${st.rank.get(id)} / ${c.students.length} 名</span></div>
    </section>
    <div class="tiles">
      <${Tile} label="交齐率" value=${pct(r)} color=${rateColor(r)} />
      <${Tile} label="已交" value=${cnt('SUBMITTED')} color=${STATUS.SUBMITTED.color} />
      <${Tile} label="优秀" value=${cnt('EXCELLENT')} color=${STATUS.EXCELLENT.color} />
      <${Tile} label="迟交" value=${cnt('LATE')} color=${STATUS.LATE.color} />
      <${Tile} label="未交" value=${cnt('MISSING') + cnt('REVIEW') + cnt('PLAG')} color=${STATUS.MISSING.color} />
      ${cnt('PLAG') > 0 && html`<${Tile} label="抄袭" value=${cnt('PLAG')} color=${PLAG.color} />`}
      <${Tile} label="复审不合格" value=${cnt('REVIEW')} color=${REVIEW.color} />
      <${Tile} label="请假" value=${cnt('LEAVE')} color=${STATUS.LEAVE.color} />
      <${Tile} label="考试" value=${exams.filter((e) => e.scores?.[id] != null).length} />
    </div>
    ${hist.length > 0 && html`<${Title}>作业足迹 · 最近 ${Math.min(60, hist.length)} 次</${Title}>
      <section class="grp foot">${hist.slice(-60).map((a) => { const k = view(a, id); return html`<button class="dot" title=${`${md(a.createdAt)} ${a.title}：${SV(k).label}`} style=${{ background: SV(k).color, opacity: k === 'NONE' ? 0.3 : 1 }} onClick=${() => open({ view: 'assign', id: a.id })}></button>`; })}</section>`}
    ${exams.length > 0 && html`<${Group} title="考试">${exams.map((e) => {
      const sc = e.scores?.[id]; const ab = (e.absent || []).includes(id);
      const ranked = c.students.map((x) => e.scores?.[x.id]).filter((x) => x != null).sort((a, b) => b - a);
      const rank = sc != null ? ranked.indexOf(sc) + 1 : null;
      return html`<${Cell} key=${e.id} title=${e.title} sub=${`${md(e.date)}${e.subject ? ' · ' + e.subject : ''}${rank ? ` · 第 ${rank} / ${ranked.length} 名` : ''}`}
        right=${ab ? html`<span class="pill" style=${{ color: '#8e8e93', background: '#8e8e931f' }}>缺考</span>` : sc != null ? html`<b>${fmtNum(sc)}<small class="muted"> / ${fmtNum(e.fullScore || 100)}</small></b>` : '—'}
        onClick=${() => open({ view: 'exam', id: e.id })} />`;
    })}</${Group}>`}
    <${Group} title=${`积分流水 · ${rows.length} 条`}>
      ${rows.length ? (more ? rows : rows.slice(0, 30)).map((x) => html`<${Cell} key=${x.key} cls=${x.counts ? '' : 'dim'} title=${x.title}
        sub=${`${fmtTime(x.t)} · ${x.kind}${x.counts ? '' : ' · 重置前，不计入'}`}
        right=${x.d == null ? html`<span class="muted">重置</span>` : html`<b style=${{ color: x.d > 0 ? '#16a34a' : '#e5384d' }}>${signed(x.d)}</b>`} />`) : html`<div class="cell"><span class="muted">还没有积分记录</span></div>`}
    </${Group}>
    ${rows.length > 30 && !more && html`<button class="more" onClick=${() => setMore(true)}>显示全部 ${rows.length} 条</button>`}
  </${Page}>`;
}

// ---------------- 积分 ----------------
function Scores({ c, rules, open }) {
  const [mode, setMode] = useState('rank');
  const st = useStats(c, rules);
  const vals = st.list.map((x) => x.score);
  const avg = vals.length ? vals.reduce((a, b) => a + b, 0) / vals.length : 0;
  const flow = useMemo(() => {
    const name = new Map(c.students.map((s) => [s.id, s.name]));
    const rows = c.scores.map((e) => ({ key: e.id, t: e.time, d: e.delta, who: name.get(e.studentId) || '已删除的同学', title: e.reason || '加减分', kind: e.settlementId ? '结算' : '加减分' }));
    for (const r of c.recharges || []) rows.push({ key: 'r' + r.id, t: r.time, d: null, who: `${(r.studentIds || []).length} 人`, title: `积分重置为 ${fmtNum(r.target)}${r.note ? ' · ' + r.note : ''}`, kind: '重置' });
    return rows.sort((a, b) => b.t - a.t);
  }, [c]);
  const [limit, setLimit] = useState(60);
  const MEDAL = ['#e8a400', '#9aa4b2', '#c07a3a'];
const medal = (i) => (i < 3 ? html`<${LIcon} name="medal" size=${20} style=${{ color: MEDAL[i] }} />` : i + 1);
  return html`<${Page} title="积分">
    <div class="tiles three">
      <${Tile} label="平均分" value=${fmtNum(avg)} />
      <${Tile} label="最高" value=${vals.length ? fmtNum(Math.max(...vals)) : '—'} color="#16a34a" />
      <${Tile} label="最低" value=${vals.length ? fmtNum(Math.min(...vals)) : '—'} color="#e5384d" />
    </div>
    <div class="pad"><${Seg} items=${[['rank', '排行榜'], ['flow', '加减分记录']]} value=${mode} onChange=${setMode} /></div>
    ${mode === 'rank'
      ? html`<${Group}>${st.ranked.map((x, i) => html`<${Cell} key=${x.student.id}
          left=${html`<span class=${'rank' + (i < 3 ? ' top' : '')}>${medal(i)}</span><${Avatar} name=${x.student.name} size=${38} />`}
          title=${x.student.name} sub=${`${x.student.no}${x.student.group ? ' · ' + x.student.group : ''} · 未交 ${x.missing} 次`}
          right=${html`<b class="score">${fmtNum(x.score)}</b>`} onClick=${() => open({ view: 'student', id: x.student.id })} />`)}</${Group}>`
      : flow.length ? html`<${Group}>${flow.slice(0, limit).map((x) => html`<${Cell} key=${x.key} title=${`${x.who} · ${x.title}`} sub=${`${fmtTime(x.t)} · ${x.kind}`}
          right=${x.d == null ? html`<span class="muted">重置</span>` : html`<b style=${{ color: x.d > 0 ? '#16a34a' : '#e5384d' }}>${signed(x.d)}</b>`} />`)}</${Group}>
          ${flow.length > limit && html`<button class="more" onClick=${() => setLimit((n) => n + 60)}>显示更多</button>`}` : html`<${Empty} text="还没有加减分记录" />`}
  </${Page}>`;
}

// ---------------- 统计（与手机端同样的四个栏目和分析，见 statspage.js） ----------------
function Stats(props) {
  return h(StatsPage, { ...props, kit: { Page, Group, Cell, Seg, Tile, Empty, Avatar } });
}
function ExamList({ c, back, open }) {
  const exams = [...(c.exams || [])].sort((a, b) => b.date - a.date);
  return html`<${Page} title="考试" onBack=${back}>
    <${Group}>${exams.map((e) => { const x = examStats(c, e); return html`<${Cell} key=${e.id} title=${e.title} sub=${`${fmtDate(e.date)}${e.subject ? ' · ' + e.subject : ''} · ${x.n} 人`} right=${html`<b>${x.avg == null ? '—' : fmtNum(x.avg)}</b>`} onClick=${() => open({ view: 'exam', id: e.id })} />`; })}</${Group}>
  </${Page}>`;
}
function ExamView({ c, rules, id, back, open }) {
  const e = (c.exams || []).find((x) => x.id === id);
  if (!e) return html`<${Page} title="考试" onBack=${back}><${Empty} text="这场考试已被删除" /></${Page}>`;
  const x = examStats(c, e);
  const rows = c.students.filter((s) => e.scores?.[s.id] != null).map((s) => ({ s, v: e.scores[s.id] })).sort((a, b) => b.v - a.v);
  const absent = c.students.filter((s) => (e.absent || []).includes(s.id));
  const none = c.students.filter((s) => e.scores?.[s.id] == null && !(e.absent || []).includes(s.id));
  return html`<${Page} title=${e.title} onBack=${back}>
    <div class="meta">${fmtDate(e.date)}${e.subject ? ' · ' + e.subject : ''} · 满分 ${fmtNum(x.full)}${e.note ? ' · ' + e.note : ''}</div>
    <div class="tiles">
      <${Tile} label="平均分" value=${x.avg == null ? '—' : fmtNum(x.avg)} />
      <${Tile} label="最高" value=${x.max == null ? '—' : fmtNum(x.max)} color="#16a34a" />
      <${Tile} label="最低" value=${x.min == null ? '—' : fmtNum(x.min)} color="#e5384d" />
      <${Tile} label="及格率" value=${pct(x.pass)} color=${rateColor(x.pass)} />
    </div>
    <${Group} title=${`成绩排名 · ${rows.length} 人`}>${rows.map((r, i) => { const d = examDelta(rules, e, r.s.id); return html`<${Cell} key=${r.s.id}
      left=${html`<span class=${'rank' + (i < 3 ? ' top' : '')}>${i + 1}</span><${Avatar} name=${r.s.name} size=${34} />`}
      title=${r.s.name} sub=${`${r.s.no} · 得分率 ${pct(r.v / x.full)}${d ? ` · 积分 ${signed(d)}` : ''}`} right=${html`<b>${fmtNum(r.v)}</b>`} onClick=${() => open({ view: 'student', id: r.s.id })} />`; })}</${Group}>
    ${absent.length > 0 && html`<${Group} title=${`缺考 · ${absent.length} 人`}>${absent.map((s) => html`<${Cell} key=${s.id} title=${s.name} sub=${s.no} />`)}</${Group}>`}
    ${none.length > 0 && html`<${Group} title=${`未录入 · ${none.length} 人`}><div class="cell"><span class="cell-sub">${none.map((s) => s.name).join('、')}</span></div></${Group}>`}
  </${Page}>`;
}

// ---------------- 设置（只有本机的个性化显示） ----------------
function Settings({ prefs, setPrefs, status, reload, showGuide }) {
  return html`<${Page} title="设置">
    <${Title}>主题配色</${Title}>
    <section class="grp palettes">${Object.entries(PALETTES).map(([k, [a, b, n]]) => html`<button class=${'pal' + (prefs.theme === k ? ' on' : '')} onClick=${() => setPrefs({ theme: k })} aria-pressed=${prefs.theme === k}>
      <i style=${{ background: `linear-gradient(135deg, ${a}, ${b})` }}></i><span>${n}</span></button>`)}</section>
    <${Group} title="显示">
      <div class="cell lbl"><div class="cell-main"><div class="cell-title">深色模式</div></div></div>
      <div class="cell-pad"><${Seg} items=${[[0, '跟随系统'], [1, '浅色'], [2, '深色']]} value=${prefs.dark} onChange=${(v) => setPrefs({ dark: v })} /></div>
      <${Cell} title="界面动效" sub="页面滑入、卡片浮现、选项滑块动画" right=${html`<${Switch} label="界面动效" on=${prefs.animations} onChange=${(v) => setPrefs({ animations: v })} />`} />
      <${Cell} title="毛玻璃效果" sub="顶栏和底栏半透明模糊" right=${html`<${Switch} label="毛玻璃效果" on=${prefs.glass} onChange=${(v) => setPrefs({ glass: v })} />`} />
      <${Cell} title="美股配色" sub=${prefs.usColors !== false ? '股票模式：绿涨红跌' : '股票模式：红涨绿跌'} right=${html`<${Switch} label="美股配色" on=${prefs.usColors !== false} onChange=${(v) => setPrefs({ usColors: v })} />`} />
      <div class="cell lbl"><div class="cell-main"><div class="cell-title">作业页布局</div></div></div>
      <div class="cell-pad"><${Seg} items=${[['grid', '网格'], ['list', '列表']]} value=${prefs.layout} onChange=${(v) => setPrefs({ layout: v })} /></div>
    </${Group}>
    <${Group} title="帮助">
      <${Cell} title="新手提示" sub="重新查看网页版的功能介绍" onClick=${showGuide} />
    </${Group}>
    <${Group} title="数据">
      <${Cell} title="刷新数据" sub=${status === 'loading' ? '正在加载…' : status === 'error' ? '加载失败，请检查网络' : '重新获取老师公开的最新数据'} onClick=${reload} />
    </${Group}>
    <div class="about"><div class="logo"><${LIcon} name="book_open" size=${28} /></div><b>作业通 · 网页版</b><span>与手机、iPad 数据同步</span></div>
  </${Page}>`;
}

// ---------------- 新手提示（只存本机，可跳过） ----------------
const GUIDE_KEY = 'hw.onboarded';
const GUIDE = [
  ['📚', '欢迎使用作业通 · 网页版', '这里可以查看老师登记的作业、积分、考试和统计。', ['底栏五个页面：首页、名单、积分、统计、设置', '数据会自动刷新，也可以在 设置 → 刷新数据'], ['#3482ff', '#6e5bff']],
  ['✅', '作业', '首页列出每次作业的交齐率，点进去可以看每位同学的情况。', ['颜色代表状态：已交、优秀、迟交、未交、请假、复审不合格', '可以按状态筛选，切换网格 / 列表'], ['#12c2a0', '#2f8fea']],
  ['🏆', '名单与积分', '名单里点同学进入档案：积分、名次、作业足迹、考试和每一笔积分流水。', ['积分页有排行榜和加减分记录', '小组里可以看组长、组员和各自积分'], ['#ffa62b', '#ff5e62']],
  ['📈', '统计', '统计页有作业、考试、小组和热力图四个栏目。', ['小组：综合分对抗排名、本期排名、组长奖惩', '热力图：每格一次作业，点格子进入'], ['#9b59b6', '#3498db']],
  ['🎨', '个性化', '设置里可以换主题配色、深色模式、界面动效、毛玻璃和作业页布局。', ['这些只保存在这台设备上', '随时可以在 设置 → 新手提示 再看一遍'], ['#37474f', '#90a4ae']],
];
function Guide({ onDone }) {
  const [i, setI] = useState(0);
  const [emoji, title, body, tips, [c1, c2]] = GUIDE[i];
  const last = i === GUIDE.length - 1;
  return html`<div class="guide" role="dialog" aria-modal="true" aria-label="新手提示">
    <div class="guide-card">
      <div class="guide-top"><span>新手提示 ${i + 1}/${GUIDE.length}</span><button class="guide-skip" onClick=${onDone}>跳过</button></div>
      <div class="guide-icon" style=${{ background: `linear-gradient(135deg, ${c1}, ${c2})` }}><${LIcon} name=${emoji} size=${38} /></div>
      <h2>${title}</h2>
      <p>${body}</p>
      <ul>${tips.map((t) => html`<li><i style=${{ background: c1 }}></i>${t}</li>`)}</ul>
      <div class="guide-dots">${GUIDE.map((_, k) => html`<b class=${k === i ? 'on' : ''}></b>`)}</div>
      <div class="guide-btns">
        ${i > 0 && html`<button class="btn2" onClick=${() => setI(i - 1)}>上一步</button>`}
        <button class="btn1" onClick=${() => (last ? onDone() : setI(i + 1))}>${last ? '开始查看' : '下一步'}</button>
      </div>
    </div>
  </div>`;
}

// ---------------- 数据加载 ----------------
function useData() {
  const [st, setSt] = useState(() => {
    try { const t = localStorage.getItem(CACHE_KEY); return { data: t ? normalizeData(JSON.parse(t)) : null, status: 'loading', updatedAt: Number(localStorage.getItem(CACHE_KEY + '.at')) || 0 }; } catch { return { data: null, status: 'loading', updatedAt: 0 }; }
  });
  const busy = useRef(false);
  const load = async () => {
    if (busy.current) return; busy.current = true;
    setSt((s) => ({ ...s, status: 'loading' }));
    try {
      const row = await api.pullPublic();
      if (!row) { setSt((s) => ({ ...s, status: 'none' })); return; }
      const d = normalizeData(row.data); const at = Date.parse(row.updatedAt) || Date.now();
      try { localStorage.setItem(CACHE_KEY, JSON.stringify(d)); localStorage.setItem(CACHE_KEY + '.at', String(at)); } catch { /* 忽略 */ }
      setSt({ data: d, status: 'ok', updatedAt: at });
    } catch { setSt((s) => ({ ...s, status: 'error' })); } finally { busy.current = false; }
  };
  useEffect(() => {
    load();
    const t = setInterval(() => { if (!document.hidden) load(); }, 60_000);
    const f = () => { if (!document.hidden) load(); };
    document.addEventListener('visibilitychange', f);
    return () => { clearInterval(t); document.removeEventListener('visibilitychange', f); };
  }, []);
  return [st, load];
}

// ---------------- 外壳 ----------------
const TABS = [['home', '首页', I.home], ['roster', '名单', I.people], ['scores', '积分', I.trophy], ['stats', '统计', I.chart], ['settings', '设置', I.gear]];

function App() {
  const [prefs, setPrefsState] = useState(loadPrefs);
  const setPrefs = (patch) => setPrefsState((p) => { const n = { ...p, ...patch }; try { localStorage.setItem(PREF_KEY, JSON.stringify(n)); } catch { /* 本机偏好 */ } return n; });
  const [{ data, status }, reload] = useData();
  const [tab, setTab] = useState('home');
  const [guide, setGuide] = useState(() => { try { return localStorage.getItem(GUIDE_KEY) !== '1'; } catch { return false; } });
  const doneGuide = () => { try { localStorage.setItem(GUIDE_KEY, '1'); } catch { /* 本机 */ } setGuide(false); };
  const [stack, setStack] = useState([]);
  const pos = useRef(new Map());
  const top = stack[stack.length - 1];
  const key = top ? `${top.view}:${top.id || ''}:${stack.length}` : tab;

  // 主题、深色、动效、毛玻璃
  const [p1, p2] = PALETTES[prefs.theme] || PALETTES.hyper;
  const sysDark = window.matchMedia?.('(prefers-color-scheme: dark)').matches;
  const dark = prefs.dark === 2 || (prefs.dark === 0 && sysDark);
  useEffect(() => {
    const r = document.documentElement;
    r.dataset.theme = dark ? 'dark' : 'light';
    r.style.setProperty('--accent', p1); r.style.setProperty('--accent2', p2);
    r.classList.toggle('motion', !!prefs.animations); r.classList.toggle('glass', !!prefs.glass);
    document.querySelector('meta[name=theme-color]')?.setAttribute('content', dark ? '#000000' : '#f7f7f7');
  }, [dark, p1, p2, prefs.animations, prefs.glass]);

  // 浏览器返回键 = 返回上一页
  useEffect(() => {
    const f = () => setStack((s) => s.slice(0, -1));
    window.addEventListener('popstate', f);
    return () => window.removeEventListener('popstate', f);
  }, []);
  const open = (r) => { pos.current.set(key, window.scrollY); history.pushState({ d: stack.length + 1 }, ''); setStack((s) => [...s, r]); };
  const back = () => history.back();
  useLayoutEffect(() => { window.scrollTo(0, pos.current.get(key) || 0); }, [key]);

  if (!data) {
    return html`<div class="app"><${Page} title="作业通">
      ${status === 'loading' ? html`<${Empty} emoji="⏳" text="正在加载…" />` : status === 'none' ? html`<${Empty} emoji="🔒" text="老师还没有开放查看" />` : html`<${Empty} emoji="📶" text="加载失败，请检查网络后刷新" />`}
      ${status !== 'loading' && html`<button class="more" onClick=${reload}>重新加载</button>`}
    </${Page}></div>`;
  }
  const c = data.classes.find((x) => x.id === prefs.classId) || data.classes.find((x) => x.id === data.currentClassId) || data.classes[0];
  if (!c) return html`<div class="app"><${Page} title="作业通"><${Empty} emoji="🏫" text="还没有班级" /></${Page}></div>`;
  const rules = data.settings.rules;
  const props = { c, data, rules, open, back, prefs, setPrefs };
  let body;
  if (top) {
    const V = { assign: AssignView, student: StudentView, group: GroupView, exam: ExamView, exams: ExamList, stock: StockView, gstock: GroupStockView }[top.view];
    body = V ? h(V, { ...props, id: top.id }) : null;
  } else if (tab === 'home') body = h(Home, props);
  else if (tab === 'roster') body = h(Roster, props);
  else if (tab === 'scores') body = h(Scores, props);
  else if (tab === 'stats') body = h(Stats, props);
  else body = h(Settings, { prefs, setPrefs, status, reload, showGuide: () => setGuide(true) });
  const idx = TABS.findIndex((t) => t[0] === tab);
  return html`<div class="app">
    <main key=${key} class=${top ? 'detail' : 'tabpage'}>${body}</main>
    ${!top && html`<nav class="tabbar" style=${{ '--i': idx }}><i class="tab-pill"></i>
      ${TABS.map(([k, l, d]) => html`<button class=${tab === k ? 'on' : ''} aria-current=${tab === k ? 'page' : null} onClick=${() => { pos.current.set(key, window.scrollY); setTab(k); }}><${Icon} d=${d} /><span>${l}</span></button>`)}
    </nav>`}
    ${guide && html`<${Guide} onDone=${doneGuide} />`}
  </div>`;
}

render(html`<${App} />`, document.getElementById('app'));
if ('serviceWorker' in navigator) navigator.serviceWorker.register('./sw.js').catch(() => {});
