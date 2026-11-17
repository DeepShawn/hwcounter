// 股票模式（与安卓 StockScreens.kt 同样的页面）：大盘行情、滚动行情条、领涨领跌、自选股 / 小组板块、个股详情、小组个股详情。
// 行情在 Web Worker 里计算并按区间缓存；图表可按住左右拖动查看每一根，点按选中 / 再点取消。
import { h } from '../vendor/preact.module.js';
import { useState, useEffect, useMemo, useRef, useLayoutEffect } from '../vendor/hooks.module.js';
import htm from '../vendor/htm.module.js';
import { fmtNum, compareStudentNumbers, studentStats, leaderOf } from './model.js';
import { attention, ATT_LEVELS, stockBoard, groupStockBoard, SPANS, groupsOf, groupReport } from './analytics.js';

const html = htm.bind(h);
const attColor = (l) => (ATT_LEVELS.find((x) => x[0] === l) || ATT_LEVELS[3])[2];
const attLabel = (l) => (ATT_LEVELS.find((x) => x[0] === l) || ATT_LEVELS[3])[1];
const md = (t) => { const d = new Date(t); return `${d.getMonth() + 1}/${d.getDate()}`; };
const p2 = (x) => x.toFixed(2);
const s2 = (x) => (x > 0.005 ? '+' + p2(x) : x < -0.005 ? '-' + p2(Math.abs(x)) : '0.00');
const pctOf = (q) => (q.pct == null ? '—' : `${q.pct > 0.00005 ? '+' : q.pct < -0.00005 ? '-' : ''}${Math.abs(q.pct * 100).toFixed(2)}%`);
const amp = (q) => (q.base < 1 ? '—' : `${((q.high - q.low) / q.base * 100).toFixed(2)}%`);
const candleLabel = (k) => (k.end - k.start <= 86400000 ? md(k.start) : `${md(k.start)}–${md(k.end - 1)}`);
const ticker = (s) => (s.no ? `No.${s.no}` : s.name);
const spanLabel = (sp) => (SPANS.find((s) => s[0] === sp) || SPANS[1])[1];

/** 涨跌配色：美股绿涨红跌 / A 股红涨绿跌 */
export function stockColors(us) {
  const up = us ? '#12a866' : '#e5433a'; const down = us ? '#e5433a' : '#12a866'; const flat = '#8e8e93';
  return { up, down, flat, of: (x) => (x > 0.005 ? up : x < -0.005 ? down : flat) };
}

// 切换页面后回来仍保持（与安卓 StockUi 一致）
export const stockUi = { kind: 0, span: 'M1', kline: false, pill: 0, sort: 0 };
function useUi(k) { const [v, set] = useState(stockUi[k]); return [v, (x) => { stockUi[k] = typeof x === 'function' ? x(stockUi[k]) : x; set(stockUi[k]); }]; }

function Tabs({ items, value, onChange, cls = '' }) {
  return html`<div class=${'an-tabs ' + cls}>${items.map((t, i) => html`<button class=${i === value ? 'on' : ''} onClick=${() => onChange(i)}>${t}</button>`)}</div>`;
}
const SpanTabs = ({ span, setSpan }) => html`<${Tabs} items=${SPANS.map((s) => s[1])} value=${SPANS.findIndex((s) => s[0] === span)} onChange=${(i) => setSpan(SPANS[i][0])} />`;

// ---------------- 行情计算：Web Worker + 每个区间缓存 ----------------
let stockWorker = null; let reqSeq = 0; const pending = new Map();
function worker() {
  if (stockWorker !== null) return stockWorker;
  try {
    stockWorker = new Worker(new URL('./stockworker.js', import.meta.url), { type: 'module' });
    stockWorker.onmessage = (e) => { const p = pending.get(e.data.id); if (p) { pending.delete(e.data.id); e.data.error ? p.reject(new Error(e.data.error)) : p.resolve(e.data.board); } };
    stockWorker.onerror = () => { stockWorker = false; for (const p of pending.values()) p.reject(new Error('worker')); pending.clear(); };
  } catch { stockWorker = false; }
  return stockWorker;
}
function computeBoard(c, rules, kind, span) {
  const now = Date.now();
  const local = () => new Promise((resolve) => setTimeout(() => resolve(kind === 1 ? groupStockBoard(c, rules, span, now) : stockBoard(c, rules, span, now)), 0));
  const w = worker();
  if (!w) return local();
  const id = ++reqSeq;
  return new Promise((resolve, reject) => { pending.set(id, { resolve, reject }); w.postMessage({ id, c, rules, kind, span, now }); }).catch(local);
}
const boardCache = new WeakMap();
/** 行情：算好之前先显示同一类上一次的结果（stale = true） */
export function useBoard(c, rules, kind, span) {
  let byRules = boardCache.get(c); if (!byRules) { byRules = new WeakMap(); boardCache.set(c, byRules); }
  let cache = byRules.get(rules); if (!cache) { cache = new Map(); byRules.set(rules, cache); }
  const key = `${kind}:${span}`;
  const [, tick] = useState(0);
  const [shown, setShown] = useState(null);
  useEffect(() => {
    let alive = true;
    const want = (k, sp) => {
      const kk = `${k}:${sp}`;
      if (!cache.has(kk)) cache.set(kk, computeBoard(c, rules, k, sp).then((b) => { cache.set(kk, { board: b }); return b; }, () => { cache.delete(kk); return undefined; }));
      return cache.get(kk);
    };
    const hit = cache.get(key);
    if (hit && hit.board !== undefined) { setShown({ key, board: hit.board }); return; }
    want(kind, span).then((b) => {
      if (!alive) return;
      setShown({ key, board: b }); tick((x) => x + 1);
      for (const [sp] of SPANS) want(kind, sp); // 预先把其他区间也算好，切换时立即显示
    });
    return () => { alive = false; };
  }, [c, rules, key]);
  const hit = cache.get(key);
  if (hit && hit.board !== undefined) return { board: hit.board, stale: false };
  return { board: shown && shown.key.split(':')[0] === String(kind) ? shown.board : undefined, stale: true };
}

// ---------------- 图表 ----------------
/** 元素实际宽度（图按真实像素画，不拉伸） */
function useWidth(fallback = 320) {
  const ref = useRef(null); const [w, setW] = useState(fallback);
  useLayoutEffect(() => {
    const el = ref.current; if (!el) return undefined;
    const set = () => { const x = Math.round(el.getBoundingClientRect().width); if (x > 0) setW(x); };
    set();
    if (typeof ResizeObserver === 'undefined') { window.addEventListener('resize', set); return () => window.removeEventListener('resize', set); }
    const ro = new ResizeObserver(set); ro.observe(el); return () => ro.disconnect();
  }, []);
  return [ref, w];
}

/** 价格 + 涨跌（拖动图表时显示所选那一根） */
export function QuoteHeader({ q, sel, span, sc }) {
  const k = sel == null ? null : q.candles[sel]; const v = k ? k.close : q.last; const ch = v - q.base;
  const pc = q.base < 1 ? '—' : `${ch >= 0 ? '+' : '-'}${Math.abs(ch / q.base * 100).toFixed(2)}%`;
  // 数据更新时价格闪一下
  const [flash, setFlash] = useState(''); const last = useRef(q.last);
  useEffect(() => { if (Math.abs(q.last - last.current) > 0.004) { setFlash(q.last > last.current ? 'up' : 'down'); const t = setTimeout(() => setFlash(''), 900); last.current = q.last; return () => clearTimeout(t); } last.current = q.last; return undefined; }, [q.last]);
  return html`<div class="an-quote"><b class=${flash ? 'flash-' + flash : ''}>${p2(v)}</b><div><span style=${{ color: sc.of(ch) }}>${s2(ch)} (${pc})</span><small>${k ? candleLabel(k) : `近${spanLabel(span)}`}</small></div></div>`;
}

/** 行情图：走势（面积线）或 K 线 + 成交量；按住左右拖动查看每一根，松手恢复；点按选中、再点取消；鼠标悬停也可查看 */
export function StockChart({ q, kline, sc, sel, onSel, height = 190 }) {
  const [ref, W0] = useWidth();
  const drag = useRef(null); const pinned = useRef(null);
  const n = q.candles.length; if (!n) return null;
  const AX = 44; const W = Math.max(120, W0 - AX); const Hc = height; const VH = 34;
  let lo = Math.min(q.low, q.base); let hi = Math.max(q.high, q.base);
  if (hi - lo < 2) { const m = (hi + lo) / 2; lo = m - 1; hi = m + 1; }
  const pad = (hi - lo) * 0.08; lo = Math.max(0, lo - pad); hi += pad;
  const y = (v) => Hc * (1 - (v - lo) / (hi - lo));
  const slot = W / n;
  const idx = (x) => (kline ? Math.max(0, Math.min(n - 1, Math.floor(x / slot))) : Math.max(0, Math.min(n - 1, Math.round(x / W * n) - 1)));
  const xOf = (e) => { const r = e.currentTarget.getBoundingClientRect(); return Math.max(0, Math.min(W, e.clientX - r.left)); };
  const down = (e) => { if (e.button > 0) return; try { e.currentTarget.setPointerCapture(e.pointerId); } catch { /* 旧浏览器 */ } const i = idx(xOf(e)); drag.current = { x: e.clientX, moved: false, before: sel, i }; onSel(i); };
  const move = (e) => {
    const d = drag.current; const i = idx(xOf(e));
    if (d) { if (Math.abs(e.clientX - d.x) > 4) d.moved = true; d.i = i; onSel(i); } else if (e.pointerType === 'mouse' && pinned.current == null) onSel(i);
  };
  const up = () => { const d = drag.current; drag.current = null; if (!d) return; const v = d.moved ? null : pinned.current === d.i ? null : d.i; pinned.current = v; onSel(v); };
  const leave = (e) => { if (!drag.current && e.pointerType === 'mouse') onSel(pinned.current); };
  const handlers = { onPointerDown: down, onPointerMove: move, onPointerUp: up, onPointerCancel: () => { drag.current = null; onSel(pinned.current); }, onPointerLeave: leave };
  const color = sc.of(q.change);
  const xs = (i) => W * i / Math.max(1, q.closes.length - 1);
  const line = q.closes.map((v, i) => `${i ? 'L' : 'M'}${xs(i).toFixed(1)},${y(v).toFixed(1)}`).join(' ');
  const gid = `g${q.id || 'mkt'}`.replace(/[^\w-]/g, '_');
  const lastX = xs(q.closes.length - 1); const lastY = y(q.closes[q.closes.length - 1]);
  const vmax = Math.max(1, ...q.candles.map((k) => k.volume));
  const vbw = Math.max(1, Math.min(24, slot - Math.max(1, slot * 0.12)));
  const kbw = Math.max(1, Math.min(20, slot - Math.max(1, slot * 0.22)));
  const sx = sel == null ? null : kline ? slot * sel + slot / 2 : xs(sel + 1);
  const k = sel == null ? null : q.candles[sel];
  return html`<div class="stk-chart" ref=${ref}>
    <div class="stk-row">
      <svg class="an-stock" width=${W} height=${Hc} viewBox=${`0 0 ${W} ${Hc}`} ...${handlers} role="img" aria-label=${`行情，最新 ${p2(q.last)}，涨跌 ${s2(q.change)}`}>
        <defs><linearGradient id=${gid} x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color=${color} stop-opacity="0.28" /><stop offset="1" stop-color=${color} stop-opacity="0" /></linearGradient></defs>
        ${[0, 0.5, 1].map((g) => html`<line x1="0" x2=${W} y1=${Hc * g} y2=${Hc * g} stroke="var(--line)" stroke-width="1" />`)}
        <line x1="0" x2=${W} y1=${y(q.base)} y2=${y(q.base)} stroke="var(--sub)" stroke-dasharray="3 3" stroke-width="1" opacity="0.8" />
        ${kline ? q.candles.map((c, i) => { const col = c.close - c.open > 0.005 ? sc.up : c.close - c.open < -0.005 ? sc.down : 'var(--sub)'; const cx = slot * i + slot / 2; const top = y(Math.max(c.open, c.close)); const bot = y(Math.min(c.open, c.close));
            return html`<g class="stk-candle" style=${{ animationDelay: `${Math.min(600, i * 12)}ms` }}><line x1=${cx} x2=${cx} y1=${y(c.high)} y2=${y(c.low)} stroke=${col} stroke-width="1.2" /><rect x=${cx - kbw / 2} width=${kbw} y=${top} height=${Math.max(1.2, bot - top)} fill=${col} /></g>`; })
          : html`<path d=${`${line} L${lastX.toFixed(1)},${Hc} L0,${Hc} Z`} fill=${`url(#${gid})`} class="stk-area" />
            <path d=${line} fill="none" stroke=${color} stroke-width="2" stroke-linejoin="round" stroke-linecap="round" class="stk-line" pathLength="1" />
            ${sel == null && html`<circle cx=${lastX} cy=${lastY} r="7" fill=${color} class="stk-pulse" /><circle cx=${lastX} cy=${lastY} r="3.5" fill=${color} />`}`}
        ${sx != null && html`<line x1=${sx} x2=${sx} y1="0" y2=${Hc} stroke="var(--fg)" stroke-width="1" opacity="0.4" />
          ${!kline && html`<circle cx=${sx} cy=${y(q.closes[sel + 1])} r="6" fill="var(--card)" /><circle cx=${sx} cy=${y(q.closes[sel + 1])} r="4" fill=${color} />`}`}
      </svg>
      <div class="stk-axis" style=${{ height: `${Hc}px` }}>${[hi, (hi + lo) / 2, lo].map((v) => html`<small>${p2(v)}</small>`)}</div>
    </div>
    <div class="stk-row">
      <svg class="an-stock vol" width=${W} height=${VH} viewBox=${`0 0 ${W} ${VH}`} ...${handlers} aria-hidden="true">
        ${q.candles.map((c, i) => { const bh = c.volume === 0 ? 1 : VH * c.volume / vmax; return html`<rect x=${slot * i + slot / 2 - vbw / 2} width=${vbw} y=${VH - bh} height=${bh} fill=${sc.of(c.close - c.open)} opacity=${sel == null || sel === i ? 0.65 : 0.3} />`; })}
      </svg>
      <div class="stk-axis vol"><small>成交</small><small>${vmax}</small></div>
    </div>
    <div class="stk-foot" style=${{ marginRight: `${AX}px` }}><small>${md(q.candles[0].start)}</small><small>${k ? `${candleLabel(k)} 开 ${p2(k.open)} 收 ${p2(k.close)} 成交 ${k.volume}` : '按住左右拖动查看'}</small><small>现在</small></div>
  </div>`;
}

/** 关键数据：两列 */
const KeyStats = ({ items }) => html`<div class="an-keys">${items.map(([l, v]) => html`<div><small>${l}</small><b>${v}</b></div>`)}</div>`;

/** 走势 / K 线切换 + 配色切换 */
function ChartToggles({ kline, setKline, us, setUs }) {
  return html`<div class="stk-toggles">
    <button class=${'chip' + (!kline ? ' on' : '')} onClick=${() => setKline(false)}>走势</button>
    <button class=${'chip' + (kline ? ' on' : '')} onClick=${() => setKline(true)}>K线</button>
    <span class="grow"></span>
    ${setUs && html`<button class="chip" onClick=${() => setUs(!us)}>${us ? '美股 · 绿涨红跌' : 'A股 · 红涨绿跌'}</button>`}
  </div>`;
}

/** 列表右侧的小走势线：虚线为区间开盘 */
function MiniChart({ q, color }) {
  const v = q.closes; if (v.length < 2) return html`<span class="stk-mini"></span>`;
  let lo = Math.min(...v); let hi = Math.max(...v); if (hi - lo < 1) { const m = (hi + lo) / 2; lo = m - 0.5; hi = m + 0.5; }
  const W = 70; const Hh = 30; const x = (i) => W * i / (v.length - 1); const y = (t) => Math.max(1, Math.min(Hh - 1, Hh * (1 - (t - lo) / (hi - lo))));
  return html`<svg class="stk-mini" width=${W} height=${Hh} aria-hidden="true"><line x1="0" x2=${W} y1=${y(q.base)} y2=${y(q.base)} stroke="var(--sub)" stroke-dasharray="4 4" stroke-width="1" opacity="0.6" />
    <path d=${v.map((t, i) => `${i ? 'L' : 'M'}${x(i).toFixed(1)},${y(t).toFixed(1)}`).join(' ')} fill="none" stroke=${color} stroke-width="1.6" stroke-linejoin="round" stroke-linecap="round" /></svg>`;
}

/** 滚动行情条：大盘 + 涨跌幅最大的 20 只，一直向左滚动，悬停暂停 */
function TickerTape({ c, b, sc, onOpen }) {
  const items = c.students.map((s) => (b.stocks[s.id] ? [s, b.stocks[s.id]] : null)).filter(Boolean).sort((x, y) => Math.abs(y[1].change) - Math.abs(x[1].change)).slice(0, 20);
  if (!items.length) return null;
  const all = [['ATTN', b.market, null], ...items.map(([s, q]) => [ticker(s), q, s])];
  const one = (key) => all.map(([t, q, s]) => html`<button key=${key + t} class="stk-tick" tabindex=${key ? -1 : 0} onClick=${s ? () => onOpen(s.id) : null}>
    <b>${t}</b>${s && html`<span class="muted">${s.name}</span>`}<span>${p2(q.last)}</span><span style=${{ color: sc.of(q.change) }}>${q.change > 0.005 ? '▲' : q.change < -0.005 ? '▼' : '■'}${p2(Math.abs(q.change))}</span></button>`);
  // 内容复制一份接在后面，平移一半正好无缝循环；速度约 36px/秒
  return html`<div class="stk-tape" aria-label="滚动行情"><div class="stk-tape-in" style=${{ animationDuration: `${Math.max(12, all.length * 4)}s` }}>${one('')}${one('d')}</div></div>`;
}

const groupsMin2 = (c) => Object.fromEntries(Object.entries(groupsOf(c)));
const ranksOf = (b) => Object.fromEntries(Object.entries(b.stocks).map(([g, q]) => [g, 1 + Object.values(b.stocks).filter((x) => x.last > q.last + 1e-9).length]));

/** 统计 → 关注 → 股票 */
export function StockTab({ c, rules, list, open, us, setUs }) {
  const [kind, setKind] = useUi('kind'); const [span, setSpan] = useUi('span'); const [kline, setKline] = useUi('kline');
  const [pill, setPill] = useUi('pill'); const [sort, setSort] = useUi('sort');
  const [sel, setSel] = useState(null);
  const { board, stale } = useBoard(c, rules, kind, span);
  useEffect(() => setSel(null), [board, kind]);
  const sc = stockColors(us);
  if (!c.students.length) return html`<div class="empty"><div class="empty-e">📈</div>还没有学生</div>`;
  const isGroup = kind === 1;
  const head = html`<div class="pad an-picker"><${Tabs} items=${['个人 · 关注度', '小组 · 综合分']} value=${kind} onChange=${(v) => { setKind(v); setSel(null); }} />
    ${!(isGroup && !Object.keys(groupsMin2(c)).length) && html`<${SpanTabs} span=${span} setSpan=${(v) => { setSpan(v); setSel(null); }} />`}</div>`;
  if (isGroup && !Object.keys(groupsMin2(c)).length) return html`${head}<div class="empty"><div class="empty-e">👥</div>还没有小组：在名单里给同学填写小组（每组至少 2 人）</div>`;
  if (board === undefined) return html`${head}<div class="empty">正在计算行情…</div>`;
  if (board === null) return html`${head}<div class="empty"><div class="empty-e">👥</div>还没有小组（每组至少 2 人）</div>`;
  const m = board.market; const busy = stale || board.span !== span;
  const market = html`<section class="grp an-pad stk-card">
    <div class="an-mkt"><b>${isGroup ? 'GRP' : 'ATTN'}</b><small>${isGroup ? '小组综合指数 · 各组平均' : '关注指数 · 全班平均'}</small>${busy && html`<small class="an-busy">计算中…</small>`}<i class="stk-live" title="随数据实时更新"></i></div>
    <${QuoteHeader} q=${m} sel=${sel} span=${board.span} sc=${sc} />
    <${ChartToggles} kline=${kline} setKline=${setKline} us=${us} setUs=${setUs} />
    <${StockChart} q=${m} kline=${kline} sc=${sc} sel=${sel} onSel=${setSel} />
    <${KeyStats} items=${[['开盘', p2(m.base)], ['最高', p2(m.high)], ['前收', p2(m.prevClose)], ['最低', p2(m.low)], ['成交量', `${m.volume}`], ['振幅', amp(m)], ['上涨', `${board.up} ${isGroup ? '组' : '家'}`], ['下跌', `${board.down} ${isGroup ? '组' : '家'}`]]} />
    <div class="muted stk-note">${isGroup ? '综合分 = 最近 10 次作业的交齐率、少扣分、优秀率和考试得分率加权（与小组对抗排名同一口径）× 100。综合分越高越好：上涨表示小组在变好。' : '指数越高越需要关注：上涨表示情况变差，下跌表示在好转。成交量为这段时间的记录条数（作业登记、加减分、考试成绩）。'}</div></section>`;

  if (isGroup) {
    const ranks = ranksOf(board); const gm = groupsMin2(c);
    const rows = Object.entries(board.stocks).sort((a, b) => b[1].last - a[1].last || (a[0] < b[0] ? -1 : 1));
    return html`${head}${market}
      <div class="st">小组 · 点胶囊切换涨跌额 / 涨跌幅 / 名次</div>
      <section class="grp">${rows.map(([g, q]) => { const col = sc.of(q.change); const ld = leaderOf(c, g); const rk = ranks[g] || 0;
        const [txt, bg] = pill === 1 ? [pctOf(q), col] : pill === 2 ? [`第 ${rk} 名`, rk === 1 ? '#e8a400' : 'var(--accent)'] : [s2(q.change), col];
        return html`<div class="an-row tap" key=${g} onClick=${() => open({ view: 'gstock', id: g })}><div class="an-grow"><b class="an-ticker">${g}</b><small>${(gm[g] || []).length} 人${ld ? ` · 组长 ${ld.name}` : ''}</small></div>
          <${MiniChart} q=${q} color=${col} /><div class="an-att"><b>${p2(q.last)}</b><button class="an-pill" style=${{ background: bg }} onClick=${(e) => { e.stopPropagation(); setPill((p) => (p + 1) % 3); }}>${txt}</button></div></div>`; })}</section>`;
  }

  const att = Object.fromEntries(list.map((x) => [x.student.id, x]));
  const rows = c.students.filter((s) => board.stocks[s.id]).map((s) => ({ s, q: board.stocks[s.id], a: att[s.id] }));
  const sorted = [...rows].sort([(x, y) => (y.a?.index ?? y.q.last) - (x.a?.index ?? x.q.last), (x, y) => y.q.change - x.q.change, (x, y) => x.q.change - y.q.change, (x, y) => compareStudentNumbers(x.s.no || '', y.s.no || '')][sort]);
  const ups = rows.filter((x) => x.q.change > 0.005).sort((x, y) => y.q.change - x.q.change); const downs = rows.filter((x) => x.q.change < -0.005).sort((x, y) => x.q.change - y.q.change);
  const go = (id) => open({ view: 'stock', id });
  return html`${head}<${TickerTape} c=${c} b=${board} sc=${sc} onOpen=${go} />${market}
    <div class="an-movers">${[['领涨', ups], ['领跌', downs]].map(([t, l]) => html`<section class="grp an-pad"><b>${t}</b>${l.length ? l.slice(0, 3).map((x) => html`<button class="an-mini" onClick=${() => go(x.s.id)}><span>${x.s.name}</span><b style=${{ color: sc.of(x.q.change) }}>${s2(x.q.change)}</b></button>`) : html`<div class="muted">无</div>`}</section>`)}</div>
    <div class="st">自选股 · 点胶囊切换涨跌额 / 涨跌幅 / 等级</div>
    <div class="chips">${['关注度', '涨幅', '跌幅', '学号'].map((t, i) => html`<button class=${'chip' + (sort === i ? ' on' : '')} onClick=${() => setSort(i)}>${t}</button>`)}</div>
    <section class="grp">${sorted.map(({ s, q, a }) => { const col = sc.of(q.change);
      const [txt, bg] = pill === 1 ? [pctOf(q), col] : pill === 2 ? [a ? attLabel(a.level) : '—', a ? attColor(a.level) : sc.flat] : [s2(q.change), col];
      return html`<div class="an-row tap" key=${s.id} onClick=${() => go(s.id)}><div class="an-grow"><b class="an-ticker">${ticker(s)}</b><small>${s.name}${a?.top[0] ? ` · ${a.top[0].label}` : ''}</small></div>
        <${MiniChart} q=${q} color=${col} /><div class="an-att"><b>${p2(a?.index ?? q.last)}</b><button class="an-pill" style=${{ background: bg }} onClick=${(e) => { e.stopPropagation(); setPill((p) => (p + 1) % 3); }}>${txt}</button></div></div>`; })}</section>`;
}

/** 历史行情表：最近 14 根 */
const History = ({ q, sc }) => html`<div class="st">历史行情</div><section class="grp an-pad"><table class="stk-hist"><thead><tr><th>日期</th><th>开盘</th><th>收盘</th><th>涨跌</th><th>成交</th></tr></thead>
  <tbody>${[...q.candles].reverse().slice(0, 14).map((k) => html`<tr><td>${candleLabel(k)}</td><td>${p2(k.open)}</td><td><b>${p2(k.close)}</b></td><td style=${{ color: sc.of(k.close - k.open) }}>${s2(k.close - k.open)}</td><td>${k.volume}</td></tr>`)}</tbody></table></section>`;

/** 个股详情（关注度） */
export function StockDetail({ c, rules, id, open, us, setUs }) {
  const [span, setSpan] = useUi('span'); const [kline, setKline] = useUi('kline');
  const [sel, setSel] = useState(null);
  const { board, stale } = useBoard(c, rules, 0, span);
  useEffect(() => setSel(null), [board]);
  const list = useMemo(() => attention(c, rules), [c, rules]);
  const score = useMemo(() => studentStats(c, rules).find((x) => x.student.id === id)?.score, [c, rules, id]);
  const a = list.find((x) => x.student.id === id);
  if (!a) return html`<div class="empty"><div class="empty-e">👤</div>找不到这位同学</div>`;
  const s = a.student; const sc = stockColors(us); const q = board?.stocks?.[id]; const col = attColor(a.level);
  const rank = list.findIndex((x) => x.student.id === id) + 1;
  return html`<section class="grp an-pad stk-card">
      <div class="an-mkt"><small>关注度 · ${s.name}</small><em class="an-tag" style=${{ color: col, background: col + '1f' }}>${attLabel(a.level)}</em>${(stale || (board && board.span !== span)) && html`<small class="an-busy">计算中…</small>`}<i class="stk-live"></i></div>
      ${q ? html`<${QuoteHeader} q=${q} sel=${sel} span=${board.span} sc=${sc} />` : html`<div class="an-quote"><b>${p2(a.index)}</b></div>`}
      <div class="stk-span"><${SpanTabs} span=${span} setSpan=${(v) => { setSpan(v); setSel(null); }} /></div>
      <${ChartToggles} kline=${kline} setKline=${setKline} us=${us} setUs=${setUs} />
      ${q ? html`<${StockChart} q=${q} kline=${kline} sc=${sc} sel=${sel} onSel=${setSel} />` : html`<div class="empty">正在计算行情…</div>`}</section>
    ${q && html`<div class="st">关键数据</div><section class="grp an-pad"><${KeyStats} items=${[['开盘', p2(q.base)], ['最高', p2(q.high)], ['前收', p2(q.prevClose)], ['最低', p2(q.low)], ['振幅', amp(q)], ['成交量', `${q.volume}`],
      ['积分（市值）', score == null ? '—' : fmtNum(score)], ['关注排名', `${rank} / ${list.length}`], ['同期大盘', s2(board.market.change)], ['相对大盘', s2(q.change - board.market.change)]]} /></section>`}
    <div class="st">成分 · 关注度构成</div>
    <section class="grp">${[...a.parts].sort((x, y) => y.points - x.points).map((p) => html`<div class="an-row" key=${p.key}><div class="an-grow"><b>${p.label}</b><small>${p.detail}</small></div>
      <div class="an-att"><b class="mono">${p2(p.points)}</b><small>权重 ${Math.round(p.weight * 100)}%</small></div></div>`)}</section>
    ${q && html`<${History} q=${q} sc=${sc} />`}
    <button class="more" onClick=${() => open({ view: 'student', id })}>查看完整个人分析</button>`;
}

/** 小组个股详情（综合分） */
export function GroupStockDetail({ c, rules, id: group, open, us, setUs }) {
  const [span, setSpan] = useUi('span'); const [kline, setKline] = useUi('kline');
  const [sel, setSel] = useState(null);
  const { board, stale } = useBoard(c, rules, 1, span);
  useEffect(() => setSel(null), [board]);
  const team = useMemo(() => groupReport(c, rules).teams.find((t) => t.name === group), [c, rules, group]);
  const sc = stockColors(us); const q = board?.stocks?.[group];
  const members = groupsMin2(c)[group] || []; const ld = leaderOf(c, group);
  return html`<section class="grp an-pad stk-card">
      <div class="an-mkt"><small>小组综合分 · ${members.length} 人${ld ? ` · 组长 ${ld.name}` : ''}</small>${(stale || (board && board.span !== span)) && html`<small class="an-busy">计算中…</small>`}<i class="stk-live"></i></div>
      ${q ? html`<${QuoteHeader} q=${q} sel=${sel} span=${board.span} sc=${sc} />
        <div class="stk-span"><${SpanTabs} span=${span} setSpan=${(v) => { setSpan(v); setSel(null); }} /></div>
        <${ChartToggles} kline=${kline} setKline=${setKline} us=${us} setUs=${setUs} />
        <${StockChart} q=${q} kline=${kline} sc=${sc} sel=${sel} onSel=${setSel} />` : html`<div class="empty">${board === null ? '还没有小组（每组至少 2 人）' : '正在计算行情…'}</div>`}</section>
    ${q && html`<div class="st">关键数据</div><section class="grp an-pad"><${KeyStats} items=${[['开盘', p2(q.base)], ['最高', p2(q.high)], ['前收', p2(q.prevClose)], ['最低', p2(q.low)], ['振幅', amp(q)], ['成交量', `${q.volume}`],
      ['当前名次', `${ranksOf(board)[group] || 0} / ${Object.keys(board.stocks).length}`], ['平均积分', team ? fmtNum(team.avgScore) : '—'], ['同期大盘', s2(board.market.change)], ['相对大盘', s2(q.change - board.market.change)],
      ['交齐率', team ? `${Math.round(team.rate * 100)}%` : '—'], ['人均作业扣分', team ? fmtNum(team.deductPerCapita) : '—']]} /></section>`}
    ${q && html`<${History} q=${q} sc=${sc} />`}
    <button class="more" onClick=${() => open({ view: 'group', id: group })}>查看小组详情</button>`;
}
