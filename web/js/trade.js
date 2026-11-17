// 邀请码账号与模拟炒股（与安卓 cloud/Trade.kt、TradeScreens.kt 同样的功能；数据库见 supabase/003_trading.sql）
// 老师（管理员）登录后可生成一次性邀请码、管理用户、发放模拟币（与真实货币无关）；普通用户凭邀请码注册，
// 只读查看老师的班级数据，用模拟币买卖信誉分 / 关注度“股票”（个人和小组）。股价由老师的设备算好后发布，成交都在数据库里完成。
import { h } from '../vendor/preact.module.js';
import { useState, useEffect } from '../vendor/hooks.module.js';
import htm from '../vendor/htm.module.js';
import { api, session, displayName } from './cloud.js';
import { creditAll, creditGroups } from './model.js';
import { attention, groupAttentionAt } from './analytics.js';

const html = htm.bind(h);
const m2 = (x) => Number(x || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const sgn = (x) => (x > 0.005 ? '+' : x < -0.005 ? '-' : '') + m2(Math.abs(x));
const day = (iso) => String(iso || '').slice(0, 10);
const GOOD = '#16a34a'; const BAD = '#dc2626';
export const KIND_LABEL = { A: '个人关注度', C: '个人信誉分', GA: '小组关注度', GC: '小组信誉分' };
const px = (p) => Math.max(Number(p.price) || 0, 1);

// ---------------- 账号状态（本机） ----------------
const listeners = new Set();
let acct = { session: session(), me: null, loading: false };
const emit = () => { for (const f of listeners) f(acct); };
export function setAccount(patch) { acct = { ...acct, ...patch }; emit(); }
export function useAccount() {
  const [a, setA] = useState(acct);
  useEffect(() => { listeners.add(setA); return () => listeners.delete(setA); }, []);
  return a;
}
/** 读取身份：管理员（老师）或普通用户 */
export async function refreshMe() {
  const s = session();
  if (!s) { setAccount({ session: null, me: null }); return null; }
  try { const me = await api.rpc('hw_me'); setAccount({ session: s, me }); return me; } catch { setAccount({ session: s }); return acct.me; }
}
export const isMember = (me) => me?.role === 'member';

// ---------------- 股价 ----------------
/** 按老师的全部班级数据算出要发布的股价（与安卓 Trade.quotes 一致） */
export function quotes(data, now = Date.now()) {
  const rules = data.settings.rules; const out = [];
  const multi = data.classes.length > 1;
  for (const c of data.classes) {
    if (!c.students.length) continue;
    const nm = (x) => (multi ? `${c.name}·${x}` : x);
    for (const a of attention(c, rules, now)) out.push({ symbol: `A:${a.student.id}`, name: nm(a.student.name), kind: 'A', price: a.index });
    const credit = creditAll(c, now);
    for (const r of credit) if (r.hasData) out.push({ symbol: `C:${r.student.id}`, name: nm(r.student.name), kind: 'C', price: r.index });
    for (const [g, v] of Object.entries(groupAttentionAt(c, rules, now))) out.push({ symbol: `GA:${c.id}/${g}`, name: nm(g), kind: 'GA', price: v });
    for (const [g, v] of Object.entries(creditGroups(c, now, credit))) out.push({ symbol: `GC:${c.id}/${g}`, name: nm(g), kind: 'GC', price: v });
  }
  return out.map((q) => ({ ...q, price: Math.round(q.price * 100) / 100, name: q.name.slice(0, 60) })).slice(0, 2000);
}
export const publish = (data) => api.rpc('hw_publish_prices', { p_prices: quotes(data) });

const inClass = (c, symbol) => {
  if (symbol.startsWith('A:') || symbol.startsWith('C:')) { const id = symbol.slice(2); return c.students.some((s) => s.id === id); }
  if (symbol.startsWith('GA:') || symbol.startsWith('GC:')) return symbol.slice(symbol.indexOf(':') + 1).split('/')[0] === c.id;
  return false;
};

// ---------------- 设置页：账号 ----------------
const strongPw = (p) => p.length >= 6 && /[a-z]/.test(p) && /[A-Z]/.test(p) && /\d/.test(p) && /[^A-Za-z0-9]/.test(p);
export function AccountSection({ kit, onChanged, openAccounts }) {
  const { Group, Cell } = kit;
  const a = useAccount();
  const [mode, setMode] = useState('login');
  const [f, setF] = useState({ user: '', pw: '', code: '', name: '' });
  const [busy, setBusy] = useState(false); const [err, setErr] = useState('');
  const set = (k) => (e) => setF({ ...f, [k]: e.target.value });
  const run = async (fn) => { setBusy(true); setErr(''); try { await fn(); await refreshMe(); onChanged?.(); } catch (e) { setErr(e.message || '失败'); } finally { setBusy(false); } };
  if (a.session) {
    const mem = isMember(a.me);
    return html`<${Group} title="账号">
      <${Cell} title=${mem ? `${a.me.display_name || a.me.username}（${a.me.username}）` : displayName(a.session.email)} sub=${mem ? (a.me.status === 'active' ? '普通用户 · 只读查看老师的班级数据 · 统计 → 股票 → 炒股' : '账号已被老师停用') : a.me ? '老师账号（管理员）' : '正在读取身份…'} />
      ${a.me && !mem && html`<${Cell} title="用户与邀请码" sub="生成一次性邀请码、管理用户、发放模拟币" onClick=${openAccounts} />`}
      <${Cell} title="退出登录" sub="回到未登录预览" onClick=${() => run(async () => { await api.signOut(); })} />
    </${Group}>`;
  }
  return html`<${Group} title="账号">
    <div class="cell-pad acct">
      <div class="chips">${[['login', '登录'], ['invite', '用邀请码注册']].map(([k, l]) => html`<button class=${'chip' + (mode === k ? ' on' : '')} onClick=${() => { setMode(k); setErr(''); }}>${l}</button>`)}</div>
      ${mode === 'invite' && html`<input placeholder="邀请码（8 位）" value=${f.code} onInput=${(e) => setF({ ...f, code: e.target.value.toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, 8) })} />`}
      <input placeholder=${mode === 'login' ? '邮箱（老师）或用户名' : '用户名（3–20 位字母、数字、下划线）'} value=${f.user} onInput=${set('user')} autocomplete="username" />
      ${mode === 'invite' && html`<input placeholder="显示名（可选，如：张三家长）" value=${f.name} onInput=${set('name')} />`}
      <input type="password" placeholder=${mode === 'invite' ? '密码：含小写、大写、数字和符号，如 Abc123!' : '密码'} value=${f.pw} onInput=${set('pw')} autocomplete=${mode === 'invite' ? 'new-password' : 'current-password'} />
      ${err && html`<div class="acct-err">${err}</div>`}
      ${mode === 'login'
        ? html`<button class="btn1" disabled=${busy || !f.user || !f.pw} onClick=${() => run(() => api.signIn(f.user, f.pw))}>${busy ? '登录中…' : '登录'}</button>`
        : html`<button class="btn1" disabled=${busy || f.code.length !== 8 || !/^[A-Za-z0-9_]{3,20}$/.test(f.user) || !strongPw(f.pw)} onClick=${() => run(() => api.register(f.code, f.user, f.pw, f.name))}>${busy ? '注册中…' : '注册并登录'}</button>`}
      <div class="muted acct-note">${mode === 'login' ? '老师用邮箱登录后能管理邀请码和用户；凭邀请码注册的用户用用户名登录，只读查看并可模拟炒股。' : '邀请码由老师生成，一个码只能注册一个账号。模拟币只用于炒股模拟，与真实货币无关。'}</div>
    </div>
  </${Group}>`;
}

// ---------------- 统计 → 股票 → 炒股 ----------------
export function TradePanel({ kit, c, data, openSettings, openAccounts }) {
  const { Group, Cell } = kit;
  const a = useAccount();
  const [td, setTd] = useState(null); const [err, setErr] = useState(''); const [tick, setTick] = useState(0);
  const [tab, setTab] = useState(0); const [order, setOrder] = useState(null); const [msg, setMsg] = useState('');
  useEffect(() => {
    let alive = true;
    if (!a.session) { setTd(null); return undefined; }
    (async () => {
      try {
        const me = await api.rpc('hw_me');
        const prices = await api.select('hw_prices?select=symbol,name,kind,price,active,updated_at&order=kind,name');
        const ranks = await api.rpc('hw_leaderboard');
        let rest = {};
        if (me.role === 'member') {
          const u = encodeURIComponent(me.user_id);
          const [positions, orders, fills, cash] = await Promise.all([
            api.select(`hw_positions?select=symbol,qty,avg_price,realized&user_id=eq.${u}`),
            api.select(`hw_orders?select=id,symbol,side,qty,limit_price,status,reason,fill_price,created_at&user_id=eq.${u}&order=id.desc&limit=60`),
            api.select(`hw_trades?select=id,symbol,side,qty,price,amount,realized,created_at&user_id=eq.${u}&order=id.desc&limit=60`),
            api.select(`hw_cash_log?select=id,amount,note,cash_after,created_at&user_id=eq.${u}&order=id.desc&limit=40`),
          ]);
          rest = { positions, orders, fills, cash };
        } else rest = { fills: await api.select('hw_trades?select=id,user_id,symbol,side,qty,price,amount,realized,created_at&order=id.desc&limit=30') };
        if (alive) { setTd({ me, prices, ranks, ...rest }); setErr(''); }
      } catch (e) { if (alive) setErr(e.message || '读取失败'); }
    })();
    return () => { alive = false; };
  }, [a.session?.userId, tick]);

  if (!a.session) {
    return html`<${Group} title="模拟炒股">
      <div class="cell-pad muted">用老师发放的模拟币（与真实货币无关）买卖信誉分、关注度“股票”：个人和小组都有，股价随班级数据变化。<br />· 老师：登录后在 设置 → 账号 → 用户与邀请码 生成邀请码、发放模拟币<br />· 学生 / 家长：在 设置 → 账号 用邀请码注册</div>
      <${Cell} title="去登录 / 注册" onClick=${openSettings} />
    </${Group}>`;
  }
  if (err) return html`<div class="pad"><div class="acct-err">${err}</div><button class="more" onClick=${() => setTick(tick + 1)}>重试</button></div>`;
  if (!td) return html`<div class="empty">正在读取…</div>`;
  const priceOf = Object.fromEntries(td.prices.map((p) => [p.symbol, p]));
  const prices = td.prices.filter((p) => p.active && inClass(c, p.symbol));
  const updated = td.prices.reduce((m, p) => (p.updated_at > m ? p.updated_at : m), '');
  const nameOf = (s) => priceOf[s]?.name || s;
  const Fills = ({ title, fills, who }) => html`<${Group} title=${title}>${fills.length ? fills.map((f) => html`<div class="an-row" key=${f.id}><div class="an-grow"><b>${who ? who(f.user_id) + ' · ' : ''}${f.side === 'buy' ? '买入' : '卖出'} ${nameOf(f.symbol)} × ${f.qty}</b><small>${day(f.created_at)} · 价格 ${m2(f.price)} · 金额 ${m2(f.amount)}</small></div>${Number(f.realized) ? html`<b style=${{ color: f.realized >= 0 ? GOOD : BAD }}>${sgn(f.realized)}</b>` : ''}</div>`) : html`<div class="cell-pad muted">还没有成交</div>`}</${Group}>`;
  const Ranks = ({ me }) => html`<${Group} title=${`收益排行（${td.ranks.length} 人）`}>${td.ranks.length ? td.ranks.map((r, i) => html`<div class=${'an-row' + (r.user_id === me ? ' trade-me' : '')} key=${r.user_id}><span class=${'rank' + (i < 3 ? ' top' : '')}>${i + 1}</span><div class="an-grow"><b>${r.display_name || r.username}${r.status !== 'active' ? '（已停用）' : ''}</b><small>总资产 ${m2(r.equity)} · 现金 ${m2(r.cash)}</small></div><div class="an-att"><b style=${{ color: r.profit >= 0 ? GOOD : BAD }}>${sgn(r.profit)}</b>${r.invested > 0 ? html`<small>${sgn(r.profit / r.invested * 100)}%</small>` : ''}</div></div>`) : html`<div class="cell-pad muted">还没有用户。老师在 设置 → 账号 → 用户与邀请码 生成邀请码。</div>`}</${Group}>`;

  if (td.me.role !== 'member') {
    const names = Object.fromEntries(td.ranks.map((r) => [r.user_id, r.display_name || r.username]));
    return html`<${Group} title="你是管理员">
        <div class="cell-pad muted">股价 = 各同学 / 小组的信誉分、关注度（成交价最低 1）。手机端每次同步后自动发布；网页端可以在这里手动发布。${updated ? ` 最近发布：${day(updated)}（共 ${td.prices.filter((p) => p.active).length} 只）` : ' 还没有发布过股价'}</div>
        <${Cell} title="立即发布股价" sub=${msg || '按当前数据算出并发布，到价的限价单自动成交'} onClick=${async () => { try { const n = await publish(data); setMsg(`已发布，撮合成交 ${n} 笔挂单`); setTick(tick + 1); } catch (e) { setMsg(e.message); } }} />
        <${Cell} title="用户与邀请码" onClick=${openAccounts} />
      </${Group}>
      <${Ranks} me=${null} />
      ${td.fills.length > 0 && html`<${Fills} title="最近成交（全部用户）" fills=${td.fills} who=${(u) => names[u] || '用户'} />`}`;
  }

  const me = td.me;
  const pos = (td.positions || []).filter((p) => Number(p.qty) !== 0);
  const stockValue = pos.reduce((s, p) => s + p.qty * (priceOf[p.symbol] ? px(priceOf[p.symbol]) : p.avg_price), 0);
  const equity = Number(me.cash) + stockValue;
  const invested = (td.cash || []).reduce((s, l) => s + Number(l.amount), 0);
  const profit = equity - invested;
  const held = Object.fromEntries((td.positions || []).map((p) => [p.symbol, Number(p.qty)]));
  return html`
    ${me.status !== 'active' && html`<div class="pad acct-err">账号已被老师停用：不能交易</div>`}
    <section class="grp an-pad trade-card">
      <div class="an-mkt"><small>${me.display_name || me.username} 的账户</small><button class="chip" onClick=${() => setTick(tick + 1)}>刷新</button></div>
      <div class="trade-big">总资产 ${m2(equity)}</div>
      <div style=${{ color: profit >= 0 ? GOOD : BAD, fontWeight: 600 }}>累计盈亏 ${sgn(profit)}${invested > 0 ? `（${sgn(profit / invested * 100)}%）` : ''}</div>
      <div class="an-keys"><div><small>现金</small><b>${m2(me.cash)}</b></div><div><small>持仓市值</small><b>${m2(stockValue)}</b></div><div><small>老师发放</small><b>${m2(invested)}</b></div></div>
      <div class="guide-btns"><button class="btn1" disabled=${me.status !== 'active'} onClick=${() => setOrder({ p: null, side: 'buy' })}>买入</button><button class="btn2" disabled=${me.status !== 'active'} onClick=${() => setOrder({ p: null, side: 'sell' })}>卖出 / 做空</button></div>
      <div class="muted stk-note">股价最近更新：${updated ? day(updated) : '老师还没有发布'}。做空需保留 2 倍空头市值的现金。${msg ? ` ${msg}` : ''}</div>
    </section>
    <div class="pad"><div class="chips">${[`持仓 ${pos.length}`, '委托', '成交', '行情', '排行'].map((t, i) => html`<button class=${'chip' + (tab === i ? ' on' : '')} onClick=${() => setTab(i)}>${t}</button>`)}</div></div>
    ${tab === 0 && html`<${Group} title="持仓">${pos.length ? pos.map((p) => { const pr = priceOf[p.symbol]; const cur = pr ? px(pr) : Number(p.avg_price); const pl = (cur - p.avg_price) * p.qty;
      return html`<div class="an-row tap" key=${p.symbol} onClick=${() => setOrder({ p: pr || null, side: p.qty > 0 ? 'sell' : 'buy' })}><div class="an-grow"><b>${nameOf(p.symbol)}${p.qty < 0 ? '（空）' : ''}</b><small>${KIND_LABEL[pr?.kind] || ''} · ${Math.abs(p.qty)} 股 · 成本 ${m2(p.avg_price)} · 现价 ${m2(cur)}</small></div>
        <div class="an-att"><b style=${{ color: pl >= 0 ? GOOD : BAD }}>${sgn(pl)}</b>${Number(p.realized) ? html`<small>已实现 ${sgn(p.realized)}</small>` : ''}</div></div>`; }) : html`<div class="cell-pad muted">还没有持仓。点“买入”开始。</div>`}</${Group}>`}
    ${tab === 1 && html`<${Group} title="委托">${td.orders.length ? td.orders.map((o) => html`<div class="an-row" key=${o.id}><div class="an-grow"><b>${o.side === 'buy' ? '买入' : '卖出'} ${nameOf(o.symbol)} × ${o.qty}</b><small>${o.limit_price != null ? `限价 ${m2(o.limit_price)}` : '市价'} · ${day(o.created_at)}${o.reason ? ` · ${o.reason}` : ''}</small></div>
      ${o.status === 'open' ? html`<button class="chip on" onClick=${async () => { try { await api.rpc('hw_cancel_order', { p_id: o.id }); setMsg('已撤单'); setTick(tick + 1); } catch (e) { setMsg(e.message); } }}>撤单</button>` : o.status === 'filled' ? html`<small style=${{ color: GOOD }}>已成交 ${m2(o.fill_price)}</small>` : html`<small class="muted">已撤销</small>`}</div>`) : html`<div class="cell-pad muted">还没有委托</div>`}</${Group}>`}
    ${tab === 2 && html`<${Fills} title="成交记录" fills=${td.fills} />
      ${td.cash.length > 0 && html`<${Group} title="资金记录">${td.cash.map((l) => html`<div class="an-row" key=${l.id}><div class="an-grow"><small>${day(l.created_at)} ${l.note}</small></div><b>${sgn(l.amount)}</b></div>`)}</${Group}>`}`}
    ${tab === 3 && html`<${Group} title=${`可交易的股票（${prices.length}）`}>${prices.length ? ['C', 'A', 'GC', 'GA'].map((k) => { const l = prices.filter((p) => p.kind === k).sort((x, y) => y.price - x.price);
      return l.length ? html`<div class="st">${KIND_LABEL[k]}</div>${l.map((p) => html`<div class="an-row tap" key=${p.symbol} onClick=${() => setOrder({ p, side: 'buy' })}><div class="an-grow"><b>${p.name}</b></div><b class="mono">${m2(px(p))}</b></div>`)}` : ''; }) : html`<div class="cell-pad muted">老师还没有发布这个班的股价</div>`}</${Group}>`}
    ${tab === 4 && html`<${Ranks} me=${me.user_id} />`}
    ${order && html`<${OrderSheet} prices=${prices} init=${order} cash=${Number(me.cash)} held=${held} onClose=${() => setOrder(null)} onDone=${(m) => { setMsg(m); setOrder(null); setTick(tick + 1); }} />`}`;
}

/** 下单：买入 / 卖出 / 做空 / 平空 × 市价 / 限价 */
function OrderSheet({ prices, init, cash, held, onClose, onDone }) {
  const [sel, setSel] = useState(init.p); const [side, setSide] = useState(init.side);
  const [limitMode, setLimitMode] = useState(false); const [qty, setQty] = useState('10');
  const [limit, setLimit] = useState(init.p ? px(init.p).toFixed(2) : ''); const [kind, setKind] = useState('C');
  const [busy, setBusy] = useState(false); const [err, setErr] = useState('');
  const q = parseInt(qty, 10) || 0; const lim = parseFloat(limit);
  const h0 = sel ? held[sel.symbol] || 0 : 0; const p0 = sel ? px(sel) : 0;
  const action = side === 'buy' ? (h0 < 0 ? (q <= -h0 ? '买入平空' : '平空并买入') : '买入') : (h0 > 0 ? (q <= h0 ? '卖出' : '卖出并做空') : '卖出做空');
  const place = async () => {
    setBusy(true); setErr('');
    try {
      const r = await api.rpc('hw_place_order', { p_symbol: sel.symbol, p_side: side, p_qty: q, ...(limitMode ? { p_limit: lim } : {}) });
      onDone(r.status === 'filled' ? `已成交，价格 ${m2(r.price)}` : r.status === 'open' ? '已挂单，到价自动成交' : `未成交：${r.reason}`);
    } catch (e) { setErr(e.message || '下单失败'); } finally { setBusy(false); }
  };
  return html`<div class="guide" role="dialog" aria-modal="true" onClick=${(e) => e.target === e.currentTarget && onClose()}><div class="guide-card trade-sheet">
    <div class="guide-top"><span>${sel ? `${sel.name} · ${KIND_LABEL[sel.kind] || ''}` : '选择股票'}</span><button class="guide-skip" onClick=${onClose}>关闭</button></div>
    ${!sel ? html`<div class="chips">${['C', 'A', 'GC', 'GA'].map((k) => html`<button class=${'chip' + (kind === k ? ' on' : '')} onClick=${() => setKind(k)}>${KIND_LABEL[k]}</button>`)}</div>
      <div class="trade-list">${prices.filter((p) => p.kind === kind).sort((x, y) => y.price - x.price).map((p) => html`<button class="an-row tap" key=${p.symbol} onClick=${() => { setSel(p); setLimit(px(p).toFixed(2)); }}><span class="an-grow">${p.name}</span><b class="mono">${m2(px(p))}${held[p.symbol] ? ` · 持 ${held[p.symbol]}` : ''}</b></button>`)}
        ${!prices.some((p) => p.kind === kind) && html`<div class="muted">没有可交易的股票（老师还没发布股价）</div>`}</div>`
    : html`<div class="muted">现价 ${m2(p0)} · 持有 ${h0} 股 · 现金 ${m2(cash)}</div>
      <div class="chips"><button class=${'chip' + (side === 'buy' ? ' on' : '')} onClick=${() => setSide('buy')}>买入</button><button class=${'chip' + (side === 'sell' ? ' on' : '')} onClick=${() => setSide('sell')}>${h0 > 0 ? '卖出' : '做空'}</button>
        <button class=${'chip' + (!limitMode ? ' on' : '')} onClick=${() => setLimitMode(false)}>市价</button><button class=${'chip' + (limitMode ? ' on' : '')} onClick=${() => setLimitMode(true)}>限价</button></div>
      ${h0 !== 0 && html`<div class="chips"><button class="chip" onClick=${() => { setSide(h0 > 0 ? 'sell' : 'buy'); setQty(String(Math.abs(h0))); }}>${h0 > 0 ? `全部卖出 ${h0}` : `全部平空 ${-h0}`}</button></div>`}
      <div class="acct"><input inputmode="numeric" placeholder="数量（股）" value=${qty} onInput=${(e) => setQty(e.target.value.replace(/\D/g, '').slice(0, 7))} />
        ${side === 'buy' && html`<div class="chips">${[4, 2, 1].map((k) => html`<button class="chip" onClick=${() => setQty(String(Math.max(1, Math.floor(cash / k / (limitMode && lim > 0 ? lim : p0)))))}>${k === 1 ? '全仓' : `1/${k} 仓`}</button>`)}</div>`}
        ${limitMode && html`<input inputmode="decimal" placeholder=${side === 'buy' ? '限价（股价 ≤ 限价时成交）' : '限价（股价 ≥ 限价时成交）'} value=${limit} onInput=${(e) => setLimit(e.target.value.replace(/[^\d.]/g, '').slice(0, 9))} />`}</div>
      <p><b>${action} ${q} 股，约 ${m2(q * (limitMode && lim > 0 ? lim : p0))}</b>${limitMode ? '（挂单，到价自动成交，可撤单）' : '（按现价立即成交）'}</p>
      ${err && html`<div class="acct-err">${err}</div>`}
      <div class="guide-btns"><button class="btn2" onClick=${() => setSel(null)}>换一只</button><button class="btn1" disabled=${busy || q < 1 || (limitMode && !(lim > 0))} onClick=${place}>${busy ? '提交中…' : action}</button></div>`}
  </div></div>`;
}

// ---------------- 管理员：用户与邀请码 ----------------
export function AccountsPage({ kit, data, back }) {
  const { Page, Group, Cell } = kit;
  const a = useAccount();
  const [st, setSt] = useState(null); const [err, setErr] = useState(''); const [tick, setTick] = useState(0); const [msg, setMsg] = useState('');
  const [form, setForm] = useState({ count: '1', cash: '10000', note: '' }); const [all, setAll] = useState(false);
  useEffect(() => {
    let alive = true;
    (async () => {
      try {
        const [invites, members, ranks] = await Promise.all([
          api.select('hw_invites?select=code,note,initial_cash,created_at,used_at,used_by,revoked&order=created_at.desc&limit=200'),
          api.select('hw_members?select=user_id,username,display_name,status,cash,created_at&order=created_at'),
          api.rpc('hw_leaderboard'),
        ]);
        if (alive) { setSt({ invites, members, ranks: Object.fromEntries(ranks.map((r) => [r.user_id, r])) }); setErr(''); }
      } catch (e) { if (alive) setErr(e.message || '读取失败'); }
    })();
    return () => { alive = false; };
  }, [tick, a.session?.userId]);
  const act = async (ok, fn) => { try { await fn(); setMsg(ok); setTick((t) => t + 1); } catch (e) { setMsg(e.message || '操作失败'); } };
  const copy = (t) => { try { navigator.clipboard.writeText(t); setMsg('已复制'); } catch { setMsg(t); } };
  if (!a.session || isMember(a.me)) return html`<${Page} title="用户与邀请码" onBack=${back}><div class="empty">需要老师账号：在 设置 → 账号 用邮箱登录</div></${Page}>`;
  const state = (i) => (i.revoked ? '已作废' : i.used_by ? '已使用' : i.used_at ? '注册中' : '未使用');
  const invites = st ? st.invites.filter((i) => all || (!i.used_at && !i.revoked)) : [];
  const unused = invites.filter((i) => !i.used_at && !i.revoked);
  return html`<${Page} title="用户与邀请码" onBack=${back}>
    <div class="meta">邀请码一次性有效：学生或家长在 设置 → 账号 用邀请码注册，注册后只读查看你的班级数据，用你发的模拟币（与真实货币无关）炒信誉分、关注度股票。</div>
    ${err && html`<div class="pad acct-err">${err}</div>`}${msg && html`<div class="pad muted">${msg}</div>`}
    <${Group} title="生成邀请码"><div class="cell-pad acct">
      <input inputmode="numeric" placeholder="数量（1–50）" value=${form.count} onInput=${(e) => setForm({ ...form, count: e.target.value.replace(/\D/g, '').slice(0, 2) })} />
      <input inputmode="decimal" placeholder="注册赠送模拟币" value=${form.cash} onInput=${(e) => setForm({ ...form, cash: e.target.value.replace(/[^\d.]/g, '').slice(0, 10) })} />
      <input placeholder="备注（可选，如：张三家长）" value=${form.note} onInput=${(e) => setForm({ ...form, note: e.target.value.slice(0, 40) })} />
      <button class="btn1" onClick=${() => act('已生成邀请码', async () => { const l = await api.rpc('hw_create_invites', { p_note: form.note, p_cash: Number(form.cash) || 0, p_count: Math.min(50, Math.max(1, parseInt(form.count, 10) || 1)) }); copy(l.map((x) => x.code).join('\n')); })}>生成并复制</button>
    </div></${Group}>
    <${Group} title=${`邀请码（${all ? '全部' : '未使用'} ${invites.length}）`}>
      <${Cell} title=${all ? '只看未使用' : '显示全部（含已使用、已作废）'} onClick=${() => setAll(!all)} />
      ${unused.length > 1 && html`<${Cell} title=${`复制全部未使用的 ${unused.length} 个`} onClick=${() => copy(unused.map((i) => i.code + (i.note ? '  ' + i.note : '')).join('\n'))} />`}
      ${invites.map((i) => { const u = st.members.find((m) => m.user_id === i.used_by);
        return html`<div class="an-row" key=${i.code}><div class="an-grow"><b class="mono">${i.code}</b><small>${[state(i), u && `→ ${u.display_name || u.username}`, `注册送 ${m2(i.initial_cash)}`, i.note].filter(Boolean).join(' · ')}</small></div>
          ${!i.used_at && !i.revoked && html`<button class="chip" onClick=${() => copy(i.code)}>复制</button><button class="chip" onClick=${() => act('已作废', () => api.rpc('hw_revoke_invite', { p_code: i.code }))}>作废</button>`}</div>`; })}
    </${Group}>
    <${Group} title=${`用户（${st ? st.members.length : 0}）`}>
      ${st && !st.members.length && html`<div class="cell-pad muted">还没有用户注册</div>`}
      ${st && st.members.map((m) => { const r = st.ranks[m.user_id]; const name = m.display_name || m.username;
        return html`<div class="an-row" key=${m.user_id}><div class="an-grow"><b>${name}（${m.username}）${m.status !== 'active' ? ' · 已停用' : ''}</b><small>现金 ${m2(m.cash)}${r ? ` · 总资产 ${m2(r.equity)} · 盈亏 ${sgn(r.profit)}` : ''}</small>
          <div class="chips">
            <button class="chip" onClick=${() => { const v = prompt(`给 ${name} 发币（负数为收回）`, '1000'); const n = Number(v); if (v != null && n) act(n > 0 ? '已发放' : '已收回', () => api.rpc('hw_grant', { p_user: m.user_id, p_amount: n, p_note: '' })); }}>发币 / 收回</button>
            <button class="chip" onClick=${() => act(m.status === 'active' ? '已停用' : '已启用', () => api.rpc('hw_set_member_status', { p_user: m.user_id, p_status: m.status === 'active' ? 'disabled' : 'active' }))}>${m.status === 'active' ? '停用' : '启用'}</button>
            <button class="chip" onClick=${() => { const v = prompt('显示名', m.display_name || ''); if (v != null) act('已改名', () => api.rpc('hw_rename_member', { p_user: m.user_id, p_name: v })); }}>改名</button>
            <button class="chip" onClick=${() => { if (confirm(`移除 ${name}？删除资金、持仓、委托和成交记录，账号不能再查看你的数据。不能撤销。`)) act('已移除', () => api.rpc('hw_remove_member', { p_user: m.user_id })); }}>移除</button>
          </div></div></div>`; })}
      ${st && st.members.length > 1 && html`<${Cell} title="给所有用户发币" onClick=${() => { const v = prompt('给所有在用的用户各发多少（负数为收回）', '1000'); const n = Number(v); if (v != null && n) act(n > 0 ? '已发放' : '已收回', async () => { for (const m of st.members.filter((x) => x.status === 'active')) await api.rpc('hw_grant', { p_user: m.user_id, p_amount: n, p_note: '' }); }); }} />`}
    </${Group}>
    <${Group} title="股价">
      <${Cell} title="立即发布股价" sub="按当前数据发布个人关注度、个人信誉分、小组关注度、小组信誉分；手机端每次同步后也会自动发布" onClick=${() => act('已发布股价', () => publish(data))} />
    </${Group}>
  </${Page}>`;
}
