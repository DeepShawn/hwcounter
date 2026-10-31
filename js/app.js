import { h, render } from '../vendor/preact.module.js';
import { useState, useEffect, useMemo, useRef, useCallback } from '../vendor/hooks.module.js';
import htm from '../vendor/htm.module.js';
import { newId, STATUS, STATUS_KEYS, emptyData, normalizeData, summary, studentStats, ruleOf, fmtNum, signed, fmtDate, isToday, parseRoster } from './model.js';
import { api, session, meta, setMeta, syncOnce, pushAs, merge, hashData, withLocalKey } from './cloud.js';
import { createVoiceParser } from './voice.js';
import { xlsx, ST } from './xlsx.js';
import { parseSeatingText, seatingFromBoxes, seatingToGrid, autoArrange, rotateSeating, swapSeats, resizeSeating } from './seating.js';
import { splitTable, recognizeTable, recognizeLines as tableFromLines, mergeRows, columnStatuses, tally, statListSheets, classStatRows, readXlsx, loadDrafts, saveDrafts, makeDraft, draftChanges, applyDraft } from './stats.js';

const html = htm.bind(h);
const DATA_KEY = 'hw.data';
const PREVIEW_KEY = 'hw.preview';
const PALETTES = {
  mint: ['#12c2a0', '#2f8fea', '🌿 薄荷清新'], academy: ['#3a6bff', '#6a3df0', '🎓 学院蓝'], sakura: ['#ff7ea5', '#b57bff', '🌸 樱花粉'],
  sunset: ['#ffa62b', '#ff5e62', '🌅 暖阳橙'], neon: ['#7f5cff', '#00d4ff', '🌃 赛博霓虹'], forest: ['#4c8c3f', '#1f5e54', '🌲 森林物语'],
  paper: ['#c79a63', '#8b5e34', '📒 纸本手账'], grape: ['#9b59b6', '#3498db', '🍇 葡萄汽水'], ink: ['#37474f', '#90a4ae', '🖌️ 水墨极简'],
};

// ---------------- 存储 + 撤销 + 同步 ----------------
function loadData() { try { const t = localStorage.getItem(DATA_KEY); return t ? normalizeData(JSON.parse(t)) : emptyData(); } catch { return emptyData(); } }
/** 预览数据 = 云端公开的班级数据 + 本机自己的显示设置（主题等） */
function loadPreview() {
  const local = loadData();
  try { const t = localStorage.getItem(PREVIEW_KEY); const p = t ? normalizeData(JSON.parse(t)) : null; return p ? { ...p, settings: local.settings } : { ...emptyData(), settings: local.settings }; } catch { return { ...emptyData(), settings: local.settings }; }
}

let toastTimer = 0;
function useStore() {
  const [data, setData] = useState(() => (session() ? loadData() : loadPreview()));
  const [toast, setToast] = useState(null);
  const [sync, setSync] = useState({ email: session()?.email || null, busy: false, msg: '', error: false, conflict: null, conflictVersion: 0, publicRead: null, previewState: '' });
  const readOnly = !sync.email;
  const roRef = useRef(readOnly); roRef.current = readOnly;
  const undo = useRef([]);
  const dataRef = useRef(data); dataRef.current = data;
  const syncTimer = useRef(0);

  const say = useCallback((msg, undoable = false) => { clearTimeout(toastTimer); setToast({ msg, undoable }); toastTimer = setTimeout(() => setToast(null), 3500); }, []);

  const runSync = useCallback(async () => {
    if (!session()) return;
    setSync((s) => ({ ...s, busy: true, msg: '同步中…', error: false }));
    try {
      const r = await syncOnce(dataRef.current, (d) => { setData(d); dataRef.current = d; persist(d); }, normalizeData);
      setSync((s) => ({ ...s, busy: false, msg: r.message, conflict: r.action === 'conflict' ? r.remote : s.conflict, conflictVersion: r.remoteVersion || s.conflictVersion, publicRead: r.publicRead ?? s.publicRead }));
      // 首次同步：按老师的要求默认开放“未登录预览”
      const m = meta();
      if (!m.previewInit && r.action !== 'conflict' && (r.action === 'uploaded' || r.publicRead !== undefined)) {
        try { await api.setPublic(true); setMeta({ ...meta(), previewInit: true }); setSync((s) => ({ ...s, publicRead: true })); } catch { /* 未运行 002 迁移时忽略 */ }
      }
    } catch (e) {
      if (e.remoteVersion) { setTimeout(runSync, 400); return; }
      setSync((s) => ({ ...s, busy: false, msg: e.message, error: true }));
    }
  }, []);

  const scheduleSync = useCallback((ms = 4000) => {
    clearTimeout(syncTimer.current);
    if (session() && meta().auto !== false) syncTimer.current = setTimeout(runSync, ms);
  }, [runSync]);

  function persist(d) { try { localStorage.setItem(DATA_KEY, JSON.stringify(d)); } catch { /* 空间不足 */ } }

  const update = useCallback((fn, undoable = true) => {
    const old = dataRef.current; const next = fn(old);
    if (next === old) return;
    if (roRef.current) {
      // 预览模式：班级数据只读；只允许切换班级、改主题等本机显示设置
      if (next.classes !== old.classes) { say('👀 预览模式：登录后才能修改'); return; }
      dataRef.current = next; setData(next);
      try { localStorage.setItem(DATA_KEY, JSON.stringify({ ...loadData(), settings: next.settings })); } catch { /* 忽略 */ }
      return;
    }
    if (undoable) { undo.current.push(old); if (undo.current.length > 30) undo.current.shift(); }
    dataRef.current = next; setData(next); persist(next); scheduleSync();
  }, [scheduleSync]);

  const doUndo = useCallback(() => {
    const prev = undo.current.pop(); if (!prev) return;
    dataRef.current = prev; setData(prev); persist(prev); scheduleSync(); setToast(null);
  }, [scheduleSync]);

  /** 拉取老师公开的预览数据 */
  const refreshPreview = useCallback(async () => {
    if (session()) return;
    setSync((s) => ({ ...s, previewState: 'loading' }));
    try {
      const row = await api.pullPublic();
      if (!row) { setSync((s) => ({ ...s, previewState: 'none' })); return; }
      const p = normalizeData(row.data);
      try { localStorage.setItem(PREVIEW_KEY, JSON.stringify(p)); } catch { /* 忽略 */ }
      const cur = dataRef.current;
      const d = { ...p, settings: loadData().settings, currentClassId: p.classes.some((c) => c.id === cur.currentClassId) ? cur.currentClassId : p.currentClassId };
      dataRef.current = d; setData(d);
      setSync((s) => ({ ...s, previewState: 'ok' }));
    } catch (e) { setSync((s) => ({ ...s, previewState: 'error' })); }
  }, []);

  /** 登录成功：切回本机可编辑的数据并同步；退出：回到预览 */
  const onLogin = useCallback((email) => {
    const d = loadData(); dataRef.current = d; setData(d);
    setSync((s) => ({ ...s, email, busy: false, msg: '登录成功', error: false }));
    setTimeout(runSync, 50);
  }, [runSync]);
  const onLogout = useCallback(() => {
    const d = loadPreview(); dataRef.current = d; setData(d); undo.current = [];
    setSync({ email: null, busy: false, msg: '已退出登录，回到预览模式', error: false, conflict: null, conflictVersion: 0, publicRead: null, previewState: '' });
    setTimeout(refreshPreview, 50);
  }, [refreshPreview]);

  useEffect(() => {
    if (session()) runSync(); else refreshPreview();
    const onVis = () => { if (document.visibilityState === 'visible') { if (session()) scheduleSync(300); else refreshPreview(); } };
    document.addEventListener('visibilitychange', onVis);
    return () => document.removeEventListener('visibilitychange', onVis);
  }, []);

  const resolve = useCallback(async (choice) => {
    const remote = sync.conflict; const v = sync.conflictVersion; if (!remote) return;
    setSync((s) => ({ ...s, conflict: null, busy: true, msg: '处理中…' }));
    try {
      if (choice === 'cloud') {
        dataRef.current = remote; setData(remote); persist(remote);
        setMeta({ ...meta(), baseVersion: v, syncedHash: await hashData(remote), lastSyncAt: Date.now() });
        setSync((s) => ({ ...s, busy: false, msg: '已使用云端数据' }));
      } else {
        const d = choice === 'merge' ? merge(dataRef.current, remote) : dataRef.current;
        if (choice === 'merge') { dataRef.current = d; setData(d); persist(d); }
        await pushAs(d, v);
        setSync((s) => ({ ...s, busy: false, msg: choice === 'merge' ? '已合并并上传' : '已用本机覆盖云端' }));
      }
    } catch (e) { setSync((s) => ({ ...s, busy: false, msg: e.message, error: true })); if (e.remoteVersion) setTimeout(runSync, 400); }
  }, [sync]);

  // ---------- 草稿：只保存在本机（单独的 localStorage 键，不进云端快照） ----------
  const [drafts, setDraftList] = useState(loadDrafts);
  const draftsRef = useRef(drafts); draftsRef.current = drafts;
  const setDrafts = useCallback((fn) => { const next = fn(draftsRef.current); draftsRef.current = next; setDraftList(next); saveDrafts(next); }, []);
  const editDraft = useCallback((id, fn) => setDrafts((xs) => xs.map((x) => (x.id === id ? { ...fn(x), updatedAt: Date.now() } : x))), [setDrafts]);
  /** 上传草稿：写进作业 → 立即同步到云端；ids 为空表示全部 */
  const uploadDrafts = useCallback((ids, why = '') => {
    if (!session()) { say('请先在“设置”里登录，才能上传到云端'); return 0; }
    const list = draftsRef.current.filter((x) => ids.includes(x.id));
    let d = dataRef.current; const done = []; const errs = [];
    for (const dr of list) { try { d = applyDraft(d, dr); done.push(dr.id); } catch (e) { errs.push(`${dr.title || '草稿'}：${e.message}`); } }
    if (done.length) {
      undo.current.push(dataRef.current); dataRef.current = d; setData(d); persist(d);
      setDrafts((xs) => xs.filter((x) => !done.includes(x.id)));
      clearTimeout(syncTimer.current); runSync();
      say(`${why}☁️ 已上传 ${done.length} 份草稿${errs.length ? `；${errs.join('；')}` : ''}`);
    } else if (errs.length) say(errs.join('；'));
    return done.length;
  }, [runSync]);
  // 定时上传：每 15 秒检查一次；网页关着时错过的，下次打开立即上传
  useEffect(() => {
    const tick = () => {
      if (!session()) return;
      const due = draftsRef.current.filter((x) => x.scheduledAt && x.scheduledAt <= Date.now()).map((x) => x.id);
      if (due.length) uploadDrafts(due, '⏰ 定时');
    };
    const t = setInterval(tick, 15000); const t0 = setTimeout(tick, 1500);
    const onVis = () => { if (document.visibilityState === 'visible') tick(); };
    document.addEventListener('visibilitychange', onVis);
    return () => { clearInterval(t); clearTimeout(t0); document.removeEventListener('visibilitychange', onVis); };
  }, [uploadDrafts]);

  return { data, update, say, toast, doUndo, sync, setSync, runSync, resolve, scheduleSync, readOnly, refreshPreview, onLogin, onLogout, drafts, setDrafts, editDraft, uploadDrafts };
}

// ---------------- 小工具 ----------------
let audioCtx;
function beep(ok = true) {
  try {
    audioCtx = audioCtx || new (window.AudioContext || window.webkitAudioContext)();
    const o = audioCtx.createOscillator(); const g = audioCtx.createGain();
    o.frequency.value = ok ? 1320 : 440; g.gain.value = 0.08; o.connect(g); g.connect(audioCtx.destination);
    o.start(); o.stop(audioCtx.currentTime + 0.12);
  } catch { /* 无音频 */ }
  if (navigator.vibrate) navigator.vibrate(ok ? 15 : [30, 40, 30]);
}
const avatarColor = (name) => { const p = ['#5b8def', '#12b886', '#f76707', '#ae3ec9', '#e64980', '#1098ad', '#7950f2', '#37b24d', '#f59f00', '#4263eb']; let x = 0; for (const c of name) x = (x * 31 + c.charCodeAt(0)) >>> 0; return p[x % p.length]; };
const Avatar = ({ name, size = 40 }) => html`<div class="avatar" style=${{ width: size, height: size, background: avatarColor(name || '?'), fontSize: size * 0.36 }}>${(name || '?').slice(name && name.length >= 3 ? -2 : -1)}</div>`;
function download(blob, name) { const a = document.createElement('a'); a.href = URL.createObjectURL(blob); a.download = name; document.body.appendChild(a); a.click(); setTimeout(() => { URL.revokeObjectURL(a.href); a.remove(); }, 1000); }
const currentClass = (d) => d.classes.find((c) => c.id === d.currentClassId) || d.classes[0] || null;
const mapClass = (d, id, fn) => ({ ...d, classes: d.classes.map((c) => (c.id === id ? fn(c) : c)) });
const today = () => { const t = new Date(); return `${t.getMonth() + 1}月${t.getDate()}日`; };

function Modal({ onClose, title, children, wide }) {
  return html`<div class="modal-bg" onClick=${(e) => e.target === e.currentTarget && onClose && onClose()}>
    <div class=${'modal' + (wide ? ' wide' : '')}>
      ${title && html`<div class="modal-title">${title}${onClose && html`<button class="icon-btn" onClick=${onClose}>✕</button>`}</div>`}
      ${children}
    </div></div>`;
}

// ---------------- 首页 ----------------
function Home({ s, open }) {
  const { data, update, say } = s;
  const c = currentClass(data);
  const [newAssign, setNewAssign] = useState(false); const [afterNew, setAfterNew] = useState(null);
  const [newClass, setNewClass] = useState(false);
  if (!c && s.readOnly) return html`<div class="page">
    <div class="hero"><div class="hero-big">📚 作业通</div><div>预览模式 · 只读</div></div>
    <div class="empty">${s.sync.previewState === 'loading' ? '正在加载预览…' : s.sync.previewState === 'error' ? '加载失败，请检查网络后刷新' : '老师还没有开放预览。'}<br /><br />老师请到“设置”里登录后编辑。</div>
  </div>`;
  if (!c) return html`<div class="page">
    <div class="hero"><div class="hero-big">📚 欢迎使用作业通</div><div>拍照、语音登记作业，安卓 / iPad / 网页数据同步</div></div>
    <div class="card"><b>第一步：建立班级</b>
      <p class="muted">可以在“名单”页导入 JSON/CSV，或先手动新建班级。登录云端账号后会自动拉取你在其它设备上的数据。</p>
      <button class="btn" onClick=${() => setNewClass(true)}>＋ 新建班级</button></div>
    ${newClass && html`<${ClassDialog} onClose=${() => setNewClass(false)} onSave=${(name, emoji) => { const nc = { id: newId(), name, emoji, students: [], assignments: [], scores: [] }; update((d) => ({ ...d, classes: [...d.classes, nc], currentClassId: nc.id })); setNewClass(false); }} />`}
  </div>`;
  const sorted = [...c.assignments].sort((a, b) => (b.pinned - a.pinned) || b.createdAt - a.createdAt);
  return html`<div class="page">
    <div class="hero">
      <div class="row">
        <select class="class-select" value=${c.id} onChange=${(e) => e.target.value === '__new' ? setNewClass(true) : update((d) => ({ ...d, currentClassId: e.target.value }), false)}>
          ${data.classes.map((k) => html`<option value=${k.id}>${k.emoji} ${k.name}（${k.students.length}人）</option>`)}
          <option value="__new">＋ 新建班级</option>
        </select>
      </div>
      <div class="muted-light">${fmtDate(Date.now())} · ${c.students.length} 名学生 · ${c.assignments.length} 次作业</div>
    </div>
    ${!s.readOnly && html`<div class="quick">
      <button class="qa" onClick=${() => setNewAssign(true)}><span>📝</span>新建作业</button>
      <button class="qa" onClick=${() => {
        // 有今天的作业就直接用；没有就先弹出“新建作业”让老师确认，不再自动添加作业
        if (s.readOnly) { say('👀 预览模式：登录后才能登记'); return; }
        const t = c.assignments.find((a) => isToday(a.createdAt));
        if (t) open({ view: 'voice', id: t.id }); else { setAfterNew('voice'); setNewAssign(true); }
      }}><span>🎙️</span>语音登记</button>
      <button class="qa" onClick=${() => open({ view: 'drafts' })}><span>🗂️</span>草稿箱${s.drafts.length ? html` <span class="tag">${s.drafts.length}</span>` : ''}</button>
      <button class="qa" onClick=${() => open({ view: 'stattable' })}><span>📊</span>统计表识别</button>
    </div>`}
    ${!s.readOnly && s.drafts.some((x) => x.classId === c.id) && html`<div class="card draft-note" onClick=${() => open({ view: 'drafts' })}>🗂️ 有 ${s.drafts.filter((x) => x.classId === c.id).length} 份草稿只保存在本机，还没上传 <b>查看 ›</b></div>`}
    ${!sorted.length && html`<div class="empty">📭 还没有作业，点“新建作业”开始</div>`}
    ${sorted.map((a) => { const sm = summary(c, a); return html`
      <div class="card assign" onClick=${() => open({ view: 'assign', id: a.id })}>
        <div class="subj" style=${{ background: subjectColor(a.subject) + '22', color: subjectColor(a.subject) }}>${(a.subject || '作')[0]}</div>
        <div class="grow">
          <div class="title">${a.pinned ? '📌 ' : ''}${a.title}${isToday(a.createdAt) ? html` <span class="tag">今天</span>` : ''}</div>
          <div class="muted small">${a.subject} · ${fmtDate(a.createdAt)}${a.countedBooks != null ? ` · 实数${a.countedBooks}本` : ''}</div>
          <${Bar} c=${c} a=${a} />
        </div>
        <div class="right"><b>${sm.handedIn}/${sm.expected}</b><div class="muted small">${Math.round(sm.rate * 100)}%</div></div>
      </div>`; })}
    ${newAssign && html`<${AssignDialog} subjects=${data.settings.subjects} allowDraft=${!afterNew} onClose=${() => { setNewAssign(false); setAfterNew(null); }} onSave=${(title, subject, asDraft) => {
      setNewAssign(false);
      if (asDraft) { const dr = makeDraft(c, null, { title, subject }); s.setDrafts((xs) => [dr, ...xs]); open({ view: 'draft', id: dr.id }); say('📝 已建草稿：只保存在本机，登记完再上传'); return; }
      const a = mkAssignment(title, subject, c); update((d) => mapClass(d, c.id, (k) => ({ ...k, assignments: [...k.assignments, a] }))); open({ view: afterNew || 'assign', id: a.id }); setAfterNew(null); }} />`}
    ${newClass && html`<${ClassDialog} onClose=${() => setNewClass(false)} onSave=${(name, emoji) => { const nc = { id: newId(), name, emoji, students: [], assignments: [], scores: [] }; update((d) => ({ ...d, classes: [...d.classes, nc], currentClassId: nc.id })); setNewClass(false); say(`已新建 ${name}`); }} />`}
  </div>`;
}
const mkAssignment = (title, subject, c) => ({ id: newId(), title, subject, createdAt: Date.now(), statuses: Object.fromEntries(c.students.filter((s) => s.longLeave).map((s) => [s.id, 'LEAVE'])), comments: {}, countedBooks: null, pinned: false });
const subjectColor = (s) => ({ 语文: '#d6336c', 数学: '#4c6ef5', 英语: '#e8590c', 物理: '#1098ad', 化学: '#ae3ec9', 生物: '#0ca678', 历史: '#f59f00', 地理: '#5c940d', 政治: '#7048e8' }[s] || '#4c6ef5');
function Bar({ c, a }) {
  const sm = summary(c, a); const total = Math.max(1, c.students.length);
  return html`<div class="bar">${['EXCELLENT', 'SUBMITTED', 'LATE', 'LEAVE', 'MISSING'].map((k) => sm.counts[k] ? html`<i style=${{ width: (sm.counts[k] / total * 100) + '%', background: STATUS[k].color }}></i>` : null)}</div>`;
}
function AssignDialog({ subjects, onClose, onSave, initial, allowDraft, draftDefault = false }) {
  const [title, setTitle] = useState(initial?.title || ''); const [subject, setSubject] = useState(initial?.subject || subjects[0] || '');
  const [asDraft, setAsDraft] = useState(draftDefault);
  return html`<${Modal} title=${initial ? '✏️ 编辑作业' : '📝 新建作业'} onClose=${onClose}>
    <input class="input" placeholder=${`${today()} ${subject} 作业`} value=${title} onInput=${(e) => setTitle(e.target.value)} />
    <div class="chips">${subjects.map((x) => html`<button class=${'chip' + (x === subject ? ' on' : '')} onClick=${() => setSubject(x)}>${x}</button>`)}</div>
    ${allowDraft && html`<label class="row between"><span><b>📝 先存为草稿</b><div class="muted tiny">登记只保存在本机，确认无误后再手动上传或定时上传到云端</div></span><input type="checkbox" checked=${asDraft} onChange=${(e) => setAsDraft(e.target.checked)} /></label>`}
    <div class="actions"><button class="btn" onClick=${() => onSave(title.trim() || `${today()} ${subject} 作业`, subject, allowDraft && asDraft)}>${initial ? '保存' : asDraft && allowDraft ? '创建草稿' : '创建'}</button></div>
  </${Modal}>`;
}
function ClassDialog({ onClose, onSave, initial }) {
  const emojis = ['🎒', '🚀', '🌟', '📚', '🦄', '🐼', '🌈', '⚡', '🍀', '🎯'];
  const [name, setName] = useState(initial?.name || ''); const [emoji, setEmoji] = useState(initial?.emoji || '🎒');
  return html`<${Modal} title=${initial ? '编辑班级' : '新建班级'} onClose=${onClose}>
    <input class="input" placeholder="班级名称，如 高一(3)班" value=${name} onInput=${(e) => setName(e.target.value)} />
    <div class="chips">${emojis.map((x) => html`<button class=${'chip' + (x === emoji ? ' on' : '')} onClick=${() => setEmoji(x)}>${x}</button>`)}</div>
    <div class="actions"><button class="btn" disabled=${!name.trim()} onClick=${() => onSave(name.trim(), emoji)}>确定</button></div>
  </${Modal}>`;
}

// ---------------- 作业详情 ----------------
function AssignView({ s, id, back, open }) {
  const { data, update, say } = s;
  const c = currentClass(data); const a = c?.assignments.find((x) => x.id === id);
  const [filter, setFilter] = useState('ALL'); const [q, setQ] = useState(''); const [sheet, setSheet] = useState(null); const [edit, setEdit] = useState(false);
  const [view, setView] = useState(c?.seating ? 'seat' : 'card'); const [pick, setPick] = useState(new Set()); const [multi, setMulti] = useState(false);
  const [typed, setTyped] = useState(''); const [typedAs, setTypedAs] = useState('SUBMITTED'); const [typedRes, setTypedRes] = useState(null);
  const lastRate = useRef(null);
  const rate = a ? summary(c, a).rate : 0; const expected = a ? summary(c, a).expected : 0;
  useEffect(() => { if (!a) back(); }, [a]);
  // 交齐时庆祝（hooks 必须在任何提前 return 之前）
  useEffect(() => { if (lastRate.current != null && rate >= 1 && lastRate.current < 1 && expected > 0) { say('🎉 全员交齐！'); beep(); } lastRate.current = rate; }, [rate]);
  if (!a) return null;
  const sm = summary(c, a); const rules = data.settings.rules;
  const setStatus = (ids, st) => update((d) => mapClass(d, c.id, (k) => ({ ...k, assignments: k.assignments.map((x) => { if (x.id !== a.id) return x; const m = { ...x.statuses }; ids.forEach((i) => { if (st === 'NONE') delete m[i]; else m[i] = st; }); return { ...x, statuses: m }; }) })));
  // 输入拼音首字母 / 学号 / 姓名（空格隔开或连着写都行）登记
  const submitTyped = () => {
    const t = typed.trim(); if (!t) return;
    const parse = createVoiceParser(c.students, { tolerance: [0.6, 1, 1.4][data.settings.voiceFuzzy ?? 1], initials: true });
    const r = parse(t); const sure = []; const amb = [];
    for (const m of r.matches) { if (m.ids.length === 1) { if (!sure.includes(m.ids[0])) sure.push(m.ids[0]); } else amb.push(m); }
    if (sure.length) setStatus(sure, typedAs);
    const nm = (id) => c.students.find((x) => x.id === id)?.name || '?';
    setTypedRes({ ok: sure.map(nm), amb, left: r.leftover });
    if (sure.length) say(`已登记 ${sure.length} 人为“${STATUS[typedAs].label}”：${sure.map(nm).join('、')}`, true); else if (!amb.length) say('没有认出名单里的同学');
    setTyped('');
  };
  const togglePick = (ids) => setPick((p) => { const n = new Set(p); const all = ids.every((i) => n.has(i)); ids.forEach((i) => (all ? n.delete(i) : n.add(i))); return n; });
  const filters = { ALL: ['全部', () => true], NONE: ['未登记', (x) => x === 'NONE'], MISSING: ['未交', (x) => x === 'MISSING' || x === 'NONE'], DONE: ['已交', (x) => ['SUBMITTED', 'EXCELLENT', 'LATE'].includes(x)], LEAVE: ['请假', (x) => x === 'LEAVE'] };
  const list = c.students.filter((st) => filters[filter][1](a.statuses[st.id] || 'NONE') && (!q || st.name.includes(q) || st.no.includes(q)));
  let pressTimer = 0;
  const exportX = () => {
    const rows = [[{ v: '学号', s: ST.HEADER }, { v: '姓名', s: ST.HEADER }, { v: '状态', s: ST.HEADER }, { v: '本次分值', s: ST.HEADER }, { v: '备注', s: ST.HEADER }]];
    const styleOf = { SUBMITTED: ST.GREEN, EXCELLENT: ST.GOLD, LATE: ST.YELLOW, MISSING: ST.RED, LEAVE: ST.BLUE, NONE: 0 };
    for (const st of c.students) { const x = a.statuses[st.id] || 'NONE'; rows.push([st.no, st.name, { v: STATUS[x].label, s: styleOf[x] }, ruleOf(rules, x), a.comments[st.id] || '']); }
    download(xlsx([{ name: '本次作业', rows }]), `${a.subject}_${a.title}.xlsx`.replace(/[\\/]/g, '_'));
  };
  const shareText = async () => {
    const names = (k) => c.students.filter((st) => (a.statuses[st.id] || 'NONE') === k).map((st) => st.name).join('、');
    const t = `📚【${c.name}】${a.subject} ${a.title}\n📅 ${fmtDate(a.createdAt)}\n✅ 已交 ${sm.handedIn}/${sm.expected}（${Math.round(sm.rate * 100)}%）\n` +
      [['MISSING', '❌ 未交'], ['NONE', '⏳ 未登记'], ['LATE', '🐢 迟交'], ['LEAVE', '🏖️ 请假'], ['EXCELLENT', '🌟 优秀']].map(([k, l]) => names(k) && `${l}：${names(k)}`).filter(Boolean).join('\n');
    try { if (navigator.share) await navigator.share({ text: t }); else { await navigator.clipboard.writeText(t); say('已复制，可粘贴到家长群'); } } catch { /* 取消 */ }
  };
  return html`<div class="page">
    <div class="hero">
      <div class="row"><button class="icon-btn light" onClick=${back}>←</button>
        <div class="grow" onClick=${() => setEdit(true)}><div class="muted-light small">${a.subject} · ${fmtDate(a.createdAt)}</div><div class="hero-title">${a.title}</div></div></div>
      <div class="row gap">
        <div class="ring" style=${{ '--p': sm.rate }}><b>${sm.handedIn}</b><span>/ ${sm.expected}</span></div>
        <div class="chips light">${['SUBMITTED', 'MISSING', 'NONE', 'LATE', 'LEAVE', 'EXCELLENT'].map((k) => html`<span class="chip">${STATUS[k].label} ${sm.counts[k]}</span>`)}</div>
      </div>
    </div>
    <div class="toolbar">
      ${!s.readOnly && html`<button class="chip" onClick=${() => open({ view: 'voice', id: a.id })}>🎙️ 语音登记</button>`}
      ${!s.readOnly && html`<button class="chip" onClick=${() => { if (confirm(`将 ${sm.counts.NONE} 名未登记的同学标记为“未交”？`)) { setStatus(c.students.filter((st) => !a.statuses[st.id]).map((st) => st.id), 'MISSING'); say(`已标记 ${sm.counts.NONE} 人未交`, true); } }}>☑️ 剩余标未交</button>`}
      ${!s.readOnly && html`<button class="chip" onClick=${() => { const ex = s.drafts.find((x) => x.assignmentId === a.id); if (ex) { open({ view: 'draft', id: ex.id }); return; } const dr = makeDraft(c, a); s.setDrafts((xs) => [dr, ...xs]); open({ view: 'draft', id: dr.id }); }}>📝 ${s.drafts.some((x) => x.assignmentId === a.id) ? '打开草稿' : '在草稿里登记'}</button>`}
      <button class="chip" onClick=${exportX}>📊 导出 Excel</button>
      <button class="chip" onClick=${() => exportStatList(c, a.title, a.subject, a.createdAt, a.statuses, a.comments)}>📋 统计名单</button>
      <button class="chip" onClick=${shareText}>💬 通报文字</button>
    </div>
    ${!s.readOnly && s.drafts.some((x) => x.assignmentId === a.id) && html`<div class="card draft-note" onClick=${() => open({ view: 'draft', id: s.drafts.find((x) => x.assignmentId === a.id).id })}>📝 这次作业有一份<b>未上传的草稿</b>（改动 ${draftChanges(s.drafts.find((x) => x.assignmentId === a.id)).length} 人），上传后才会写进这里 <b>打开 ›</b></div>`}
    ${!s.readOnly && html`<form class="typed" onSubmit=${(e) => { e.preventDefault(); submitTyped(); }}>
      <input class="input" placeholder="⌨️ 输入拼音首字母登记：zjy lzh wf（也可写学号、姓名）" value=${typed} onInput=${(e) => setTyped(e.target.value)} autocapitalize="off" autocomplete="off" />
      <select class="input" value=${typedAs} onChange=${(e) => setTypedAs(e.target.value)}>${['SUBMITTED', 'EXCELLENT', 'LATE', 'MISSING', 'LEAVE'].map((k) => html`<option value=${k}>${STATUS[k].label}</option>`)}</select>
      <button class="btn" type="submit">登记</button>
    </form>`}
    ${typedRes && (typedRes.amb.length > 0 || typedRes.left) && html`<div class="card small">
      ${typedRes.amb.map((m) => html`<div class="row gap">“${m.heard}”有多位同学：${m.ids.map((id) => { const st = c.students.find((x) => x.id === id); return html`<button class="chip" onClick=${() => { setStatus([id], typedAs); setTypedRes({ ...typedRes, amb: typedRes.amb.filter((x) => x !== m) }); say(`已登记 ${st.name}`, true); }}>${st.name} ${st.no}</button>`; })}</div>`)}
      ${typedRes.left && html`<div class="muted">没认出：${typedRes.left}</div>`}
    </div>`}
    <div class="toolbar">
      ${c.seating && html`<button class=${'chip' + (view === 'seat' ? ' on' : '')} onClick=${() => { setView(view === 'seat' ? 'card' : 'seat'); setPick(new Set()); }}>🪑 座位表</button>`}
      ${Object.entries(filters).map(([k, [l, f]]) => html`<button class=${'chip' + (k === filter ? ' on' : '')} onClick=${() => setFilter(k)}>${l} ${c.students.filter((st) => f(a.statuses[st.id] || 'NONE')).length}</button>`)}
      <input class="input small-input" placeholder="搜索" value=${q} onInput=${(e) => setQ(e.target.value)} />
    </div>
    ${!(view === 'seat' && c.seating) && html`<div class="muted small pad">${s.readOnly ? '预览模式：只能查看，登录后可登记' : '点一下切换 已交/未登记 · 长按（或右键）设置状态、加减分、备注'}</div>`}
    ${view === 'seat' && c.seating ? html`
      <div class="muted tiny pad">${s.readOnly ? '' : '点座位切换 已交/未登记；点“排”“列”或开启多选，可整排/整列一起登记'}${!s.readOnly && html` <button class=${'chip' + (multi ? ' on' : '')} onClick=${() => { setMulti(!multi); setPick(new Set()); }}>☑️ 多选</button>`}</div>
      <${SeatChart} seating=${c.seating} students=${c.students} sel=${(k, st) => st && pick.has(st.id)}
        cell=${(st) => { if (!st) return { body: '' }; const x = a.statuses[st.id] || 'NONE'; return { style: x === 'NONE' ? {} : { background: STATUS[x].color + '33', borderColor: STATUS[x].color }, body: html`<b>${st.name}</b><span class="tiny" style=${{ color: STATUS[x].color }}>${STATUS[x].label}</span>`, onMenu: () => setSheet(st) }; }}
        onCell=${(k, st) => { if (!st) return; if (multi || pick.size) { togglePick([st.id]); return; } const x = a.statuses[st.id] || 'NONE'; setStatus([st.id], x === 'NONE' || x === 'MISSING' ? 'SUBMITTED' : 'NONE'); }}
        onRow=${(r) => { if (s.readOnly) return; const g = seatingToGrid(c.seating, c.students); togglePick(g[r].filter(Boolean)); }}
        onCol=${(col) => { if (s.readOnly) return; const g = seatingToGrid(c.seating, c.students); togglePick(g.map((row) => row[col]).filter(Boolean)); }} />
      ${(() => { const un = c.students.filter((x) => c.seating.seats[x.id] == null); return un.length ? html`<div class="muted tiny pad">未入座：${un.map((x) => html`<button class="chip" onClick=${() => { const v = a.statuses[x.id] || 'NONE'; setStatus([x.id], v === 'NONE' || v === 'MISSING' ? 'SUBMITTED' : 'NONE'); }}>${x.name} · ${STATUS[a.statuses[x.id] || 'NONE'].label}</button>`)}</div>` : ''; })()}
      ${pick.size > 0 && !s.readOnly && html`<div class="pickbar"><b>已选 ${pick.size} 人</b>
        ${['SUBMITTED', 'EXCELLENT', 'LATE', 'MISSING', 'LEAVE', 'NONE'].map((k) => html`<button class="chip" style=${{ borderColor: STATUS[k].color, color: STATUS[k].color }} onClick=${() => { setStatus([...pick], k); say(`已将 ${pick.size} 人设为“${STATUS[k].label}”`, true); setPick(new Set()); }}>${STATUS[k].label}</button>`)}
        <button class="chip" onClick=${() => setPick(new Set())}>取消</button></div>`}
    ` : html`
    <div class="grid">${list.map((st) => { const x = a.statuses[st.id] || 'NONE'; const d = ruleOf(rules, x); return html`
      <div class="stu" style=${x === 'NONE' ? {} : { background: STATUS[x].color + '26', borderColor: STATUS[x].color + '66' }}
        onClick=${() => { if (pressTimer === -1) { pressTimer = 0; return; } setStatus([st.id], x === 'NONE' || x === 'MISSING' ? 'SUBMITTED' : 'NONE'); }}
        onContextMenu=${(e) => { e.preventDefault(); setSheet(st); }}
        onTouchStart=${() => { pressTimer = setTimeout(() => { pressTimer = -1; setSheet(st); }, 500); }}
        onTouchEnd=${() => { if (pressTimer > 0) clearTimeout(pressTimer); }} onTouchMove=${() => { if (pressTimer > 0) clearTimeout(pressTimer); }}>
        <${Avatar} name=${st.name} />
        <div class="stu-name">${st.name}</div><div class="muted tiny">${st.no}</div>
        <div class="tiny" style=${{ color: STATUS[x].color, fontWeight: 700 }}>${STATUS[x].label}${d ? ` ${signed(d)}` : ''}</div>
        ${a.comments[st.id] && html`<div class="tiny muted">💬 ${a.comments[st.id]}</div>`}
      </div>`; })}</div>
    `}
    ${!c.students.length && html`<div class="empty">👥 名单为空，去“名单”页导入</div>`}
    ${sheet && html`<${StudentSheet} s=${s} c=${c} a=${a} st=${sheet} onClose=${() => setSheet(null)} setStatus=${setStatus} />`}
    ${edit && html`<${AssignDialog} initial=${a} subjects=${data.settings.subjects} onClose=${() => setEdit(false)} onSave=${(title, subject) => { update((d) => mapClass(d, c.id, (k) => ({ ...k, assignments: k.assignments.map((x) => (x.id === a.id ? { ...x, title, subject } : x)) }))); setEdit(false); }} />`}
  </div>`;
}
function StudentSheet({ s, c, a, st, onClose, setStatus }) {
  const { update, say, data } = s; const cur = a.statuses[st.id] || 'NONE';
  const [comment, setComment] = useState(a.comments[st.id] || '');
  const close = () => { if (comment !== (a.comments[st.id] || '')) update((d) => mapClass(d, c.id, (k) => ({ ...k, assignments: k.assignments.map((x) => { if (x.id !== a.id) return x; const m = { ...x.comments }; if (comment.trim()) m[st.id] = comment.trim(); else delete m[st.id]; return { ...x, comments: m }; }) }))); onClose(); };
  const score = (dv) => { update((d) => mapClass(d, c.id, (k) => ({ ...k, scores: [...k.scores, { id: newId(), studentId: st.id, delta: dv, reason: `作业：${a.title}`, time: Date.now() }] }))); say(`${st.name} ${signed(dv)} 分`, true); };
  return html`<${Modal} title=${html`<span class="row"><${Avatar} name=${st.name} size=${36} /> ${st.name} <span class="muted small">${st.no}</span></span>`} onClose=${close}>
    <div class="label">本次作业状态</div>
    <div class="chips">${STATUS_KEYS.map((k) => html`<button class=${'chip' + (k === cur ? ' on' : '')} style=${k === cur ? { background: STATUS[k].color, borderColor: STATUS[k].color } : {}} onClick=${() => setStatus([st.id], k)}>${STATUS[k].emoji} ${STATUS[k].label}</button>`)}</div>
    <div class="label">额外加减分</div>
    <div class="chips">${[-2, -1, 1, 2, 5].map((v) => html`<button class="chip" onClick=${() => score(v)}>${signed(v)}</button>`)}</div>
    <div class="label">备注</div>
    <input class="input" placeholder="如：字迹潦草、缺第3页" value=${comment} onInput=${(e) => setComment(e.target.value)} />
    <div class="actions">
      <button class="btn ghost" onClick=${() => { update((d) => mapClass(d, c.id, (k) => ({ ...k, students: k.students.map((x) => (x.id === st.id ? { ...x, longLeave: !x.longLeave } : x)) }))); if (!st.longLeave) setStatus([st.id], 'LEAVE'); say(st.longLeave ? '已取消长期请假' : '已设为长期请假'); onClose(); }}>${st.longLeave ? '取消长期请假' : '🏖️ 设为长期请假'}</button>
      <button class="btn" onClick=${close}>完成</button>
    </div>
  </${Modal}>`;
}

// ---------------- 语音登记 ----------------
function VoiceView({ s, id, back }) {
  const { data, update, say } = s;
  const c = currentClass(data); const a = c?.assignments.find((x) => x.id === id);
  const [status, setStatus] = useState('SUBMITTED');
  const [entries, setEntries] = useState([]); const [pend, setPend] = useState([]); const [misses, setMisses] = useState([]);
  const [listening, setListening] = useState(false); const [partial, setPartial] = useState(''); const [typed, setTyped] = useState('');
  const fuzzy = data.settings.voiceFuzzy ?? 1; const initials = data.settings.voiceInitials ?? true;
  const parse = useMemo(() => createVoiceParser(c ? c.students : [], { tolerance: [0.6, 1, 1.4][fuzzy], initials }), [c?.students, fuzzy, initials]);
  const recRef = useRef(null); const want = useRef(false); const dismissed = useRef(new Set());
  const st = useRef({}); st.current = { a, status, entries, pend };
  const SR = window.SpeechRecognition || window.webkitSpeechRecognition;
  useEffect(() => () => { want.current = false; recRef.current?.abort?.(); }, []);
  // 输入法语音输入：停顿 0.8 秒后解析（hooks 必须在提前 return 之前）
  useEffect(() => { if (!typed.trim() || !a) return; const t = setTimeout(() => handle(typed), 800); return () => clearTimeout(t); }, [typed]);
  useEffect(() => { if (!a) back(); }, [a]);
  if (!a) return null;
  const byId = Object.fromEntries(c.students.map((x) => [x.id, x]));

  function register(sid, heard, how) {
    const { a: aa, status: stt, entries: es } = st.current;
    if (es.some((e) => e.sid === sid) || dismissed.current.has(sid)) return;
    const prev = aa.statuses[sid] || 'NONE';
    update((d) => mapClass(d, c.id, (k) => ({ ...k, assignments: k.assignments.map((x) => (x.id === aa.id ? { ...x, statuses: { ...x.statuses, [sid]: stt } } : x)) })), false);
    const e = { key: newId(), sid, heard, how, prev, status: stt };
    st.current.entries = [e, ...es]; setEntries((xs) => [e, ...xs]); beep();
  }
  function handle(text) {
    const r = parse(text);
    for (const m of r.matches) {
      if (m.ids.length === 1) register(m.ids[0], m.heard, m.kind);
      else if (!st.current.pend.some((p) => p.heard === m.heard)) { const p = { key: newId(), heard: m.heard, ids: m.ids }; st.current.pend = [p, ...st.current.pend]; setPend((xs) => [p, ...xs]); beep(false); }
    }
    if (r.leftover && r.leftover.length <= 20) setMisses((xs) => [r.leftover, ...xs].slice(0, 6));
  }
  function start() {
    if (!SR) { say('此浏览器不支持语音识别，请用下面的输入框 + 输入法语音输入'); return; }
    const rec = new SR(); rec.lang = 'zh-CN'; rec.continuous = true; rec.interimResults = true;
    rec.onresult = (ev) => {
      let interim = '';
      for (let i = ev.resultIndex; i < ev.results.length; i++) { const r = ev.results[i]; if (r.isFinal) handle(r[0].transcript); else interim += r[0].transcript; }
      setPartial(interim);
    };
    rec.onerror = (ev) => { if (ev.error === 'not-allowed' || ev.error === 'service-not-allowed') { want.current = false; setListening(false); say('没有麦克风/语音识别权限（iPad 需在设置里开启 Siri 与听写）'); } };
    rec.onend = () => { setPartial(''); if (want.current) setTimeout(() => { try { rec.start(); } catch { /* 已在运行 */ } }, 150); else setListening(false); };
    recRef.current = rec; want.current = true; setListening(true);
    try { rec.start(); } catch { /* 忽略 */ }
  }
  function stop() { want.current = false; recRef.current?.stop(); setListening(false); }
  const sm = summary(c, a);
  return html`<div class="page">
    <div class="topbar"><button class="icon-btn" onClick=${() => { stop(); back(); }}>←</button><div class="grow"><b>语音登记</b><div class="muted small">${a.subject} · ${a.title}</div></div></div>
    <div class="pad">
      <div class="row between"><span class="label">模糊程度</span><span class="chips">${['严格', '标准', '宽松'].map((l, i) => html`<button class=${'chip' + (i === fuzzy ? ' on' : '')} onClick=${() => update((d) => ({ ...d, settings: { ...d.settings, voiceFuzzy: i } }), false)}>${l}</button>`)}</span></div>
      <label class="row between"><span><span class="label">拼音首字母匹配</span><div class="muted tiny">首字母一致即登记，如 张锦宇 = ZJY；可直接念字母“Z J Y”，连着念几个人也行</div></span><input type="checkbox" checked=${initials} onChange=${(e) => update((d) => ({ ...d, settings: { ...d.settings, voiceInitials: e.target.checked } }), false)} /></label>
      <div class="label">念到的同学登记为：</div>
      <div class="chips">${['SUBMITTED', 'EXCELLENT', 'LATE', 'MISSING', 'LEAVE'].map((k) => html`<button class=${'chip' + (k === status ? ' on' : '')} style=${k === status ? { background: STATUS[k].color, borderColor: STATUS[k].color } : {}} onClick=${() => setStatus(k)}>${STATUS[k].emoji} ${STATUS[k].label}</button>`)}</div>
    </div>
    <div class="mic-wrap">
      <button class=${'mic' + (listening ? ' on' : '')} onClick=${() => (listening ? stop() : start())}>${listening ? '🎙️' : '🎤'}</button>
      <div><b>${listening ? '请念名字或学号（可连续念）' : SR ? '点麦克风开始，连续念名字或学号' : '此浏览器不支持语音识别'}</b></div>
      <div class="partial">${partial || ' '}</div>
    </div>
    <div class="pad">
      <textarea class="input" rows="3" placeholder="也可以用输入法的语音输入（🎤）连续念，或直接打字：张三 李四 三十四号 49号" value=${typed} onInput=${(e) => setTyped(e.target.value)}></textarea>
      <div class="row between"><b>本次已登记 ${entries.length} 人</b><span class="small">作业已交 ${sm.handedIn}/${sm.expected}</span></div>
    </div>
    ${pend.map((p) => html`<div class="card warn"><div class="row between"><b>听到「${p.heard}」，是哪位？</b><button class="icon-btn" onClick=${() => setPend((xs) => xs.filter((x) => x !== p))}>✕</button></div>
      <div class="chips">${p.ids.map((sid) => byId[sid] && html`<button class="chip" onClick=${() => { setPend((xs) => xs.filter((x) => x !== p)); st.current.pend = st.current.pend.filter((x) => x !== p); dismissed.current.delete(sid); register(sid, p.heard, '点选'); }}>${byId[sid].name} ${byId[sid].no}</button>`)}</div></div>`)}
    ${misses.length > 0 && html`<div class="muted small pad">没听懂：${misses.join(' · ')}</div>`}
    ${entries.map((e, i) => { const x = byId[e.sid]; return x && html`<div class="card row">
      <b class="idx">${entries.length - i}</b><${Avatar} name=${x.name} size=${34} />
      <div class="grow"><b>${x.name}</b><div class="muted tiny">${x.no} · 听到：${e.heard} · ${e.how}</div></div>
      <span style=${{ color: STATUS[e.status].color, fontWeight: 700 }}>${STATUS[e.status].label}</span>
      <button class="icon-btn" onClick=${() => { update((d) => mapClass(d, c.id, (k) => ({ ...k, assignments: k.assignments.map((y) => { if (y.id !== a.id) return y; const m = { ...y.statuses }; if (e.prev === 'NONE') delete m[e.sid]; else m[e.sid] = e.prev; return { ...y, statuses: m }; }) })), false); dismissed.current.add(e.sid); setEntries((xs) => xs.filter((y) => y !== e)); st.current.entries = st.current.entries.filter((y) => y !== e); say(`已撤销 ${x.name}`); }}>✕</button>
    </div>`; })}
    ${!entries.length && !pend.length && html`<div class="empty small">依次念名字或学号即可，例如：“张三、李四、三十四号、49号……”<br />念错了点 ✕ 撤销</div>`}
  </div>`;
}

// ---------------- 座位表 ----------------
/** 座位表格子图：讲台位置、排号（从讲台数起）、列号；cell(id, k) 返回 { style, body } */
function SeatChart({ seating, students, cell, onCell, onRow, onCol, sel }) {
  const byId = Object.fromEntries(students.map((x) => [x.id, x]));
  const grid = seatingToGrid(seating, students); const { rows, cols, frontAtTop } = seating;
  const front = html`<div class="seat-front">讲 台</div>`;
  return html`<div class="seat-wrap">
    ${frontAtTop && front}
    <div class="seat-grid" style=${{ gridTemplateColumns: `34px repeat(${cols}, minmax(58px, 96px))` }}>
      <div></div>
      ${Array.from({ length: cols }, (_, c) => html`<button class="seat-head" onClick=${() => onCol && onCol(c)}>${c + 1}列</button>`)}
      ${grid.map((row, r) => html`
        <button class="seat-head" onClick=${() => onRow && onRow(r)}>${frontAtTop ? r + 1 : rows - r}排</button>
        ${row.map((id, c) => { const k = r * cols + c; const st = id && byId[id]; const v = cell(st, k); return html`
          <button class=${'seat' + (st ? '' : ' empty') + (sel && sel(k, st) ? ' sel' : '')} style=${v.style || {}} onClick=${() => onCell(k, st)}
            onContextMenu=${v.onMenu ? (e) => { e.preventDefault(); v.onMenu(); } : undefined}>${v.body}</button>`; })}`)}
    </div>
    ${!frontAtTop && front}
  </div>`;
}

function SeatingEditor({ s, c, onClose }) {
  const { update, say } = s;
  const [sel, setSel] = useState(null); const [pick, setPick] = useState(null); const [text, setText] = useState(''); const [mode, setMode] = useState(c.seating ? '' : 'import');
  const [cols, setCols] = useState(c.seating?.cols || 8); const [busy, setBusy] = useState(''); const [report, setReport] = useState(null);
  const fileRef = useRef(); const imgRef = useRef();
  const seating = c.seating;
  const save = (fn, msg) => { update((d) => mapClass(d, c.id, (k) => ({ ...k, seating: fn(k.seating) }))); if (msg) say(msg, true); };
  const applyResult = (r, how) => {
    const n = Object.keys(r.seating.seats).length;
    if (!n) { say('没有认出名单里的学生，请检查内容'); return; }
    save(() => r.seating, `已按${how}排好 ${n} 人`);
    setReport({ n, unmatched: r.unmatched, unseated: r.unseated.length }); setMode(''); setSel(null);
  };
  const doText = (t) => { try { applyResult(parseSeatingText(t, c.students), '座位表'); } catch (e) { say(`解析失败：${e.message}`); } };
  const doImage = async (f) => {
    setBusy('准备识别…');
    try {
      const { recognizeLines } = await import('./ocr.js');
      const lines = await recognizeLines(f, (p, m) => setBusy(`${m} ${Math.round(p * 100)}%`));
      applyResult(seatingFromBoxes(lines, c.students), '照片');
    } catch (e) { say(e.message || '识别失败'); } finally { setBusy(''); }
  };
  const tapSeat = (k, st) => {
    if (pick) { // 把未入座的同学放到这个座位（原来的人变成未入座）
      save((x) => { const seats = { ...x.seats }; for (const [id, v] of Object.entries(seats)) if (v === k) delete seats[id]; seats[pick] = k; return { ...x, seats }; });
      setPick(null); return;
    }
    if (sel == null) { setSel(k); return; }
    if (sel === k) { setSel(null); return; }
    save((x) => swapSeats(x, sel, k)); setSel(null);
  };
  const unseated = seating ? c.students.filter((x) => seating.seats[x.id] == null) : c.students;
  const selId = seating && sel != null ? Object.keys(seating.seats).find((id) => seating.seats[id] === sel) : null;
  return html`<${Modal} title="🪑 座位表" wide onClose=${onClose}>
    <div class="chips">
      <button class=${'chip' + (mode === 'import' ? ' on' : '')} onClick=${() => setMode(mode === 'import' ? '' : 'import')}>📥 导入座位表</button>
      <button class="chip" onClick=${() => imgRef.current.click()}>📷 照片识别</button>
      <button class=${'chip' + (mode === 'auto' ? ' on' : '')} onClick=${() => setMode(mode === 'auto' ? '' : 'auto')}>⚡ 按学号自动排列</button>
      ${seating && html`<button class="chip" onClick=${() => save((x) => rotateSeating(x), '讲台已换边')}>🔄 讲台换边</button>
        <button class="chip danger" onClick=${() => { if (confirm('清空座位表？（名单不受影响）')) { save(() => null, '已清空座位表'); setMode('import'); } }}>🗑️ 清空</button>`}
      <input type="file" ref=${fileRef} accept=".txt,.csv,.json,text/*,application/json" style="display:none" onChange=${async (e) => { const f = e.target.files[0]; e.target.value = ''; if (f) doText(await f.text()); }} />
      <input type="file" ref=${imgRef} accept="image/*" style="display:none" onChange=${(e) => { const f = e.target.files[0]; e.target.value = ''; if (f) doImage(f); }} />
    </div>
    ${busy && html`<div class="card warn small">⏳ ${busy}</div>`}
    ${mode === 'import' && html`<div class="card">
      <div class="muted small">每行一排、从讲台那边写起；单元格写<b>姓名或学号</b>，用 Tab（从 Excel 直接复制）、逗号或空格隔开，空位写“空”。可以加一行“讲台”表示讲台位置。也可以拍一张座位表照片，自动识别排列。</div>
      <textarea class="input" rows="6" value=${text} onInput=${(e) => setText(e.target.value)} placeholder=${'讲台\n张三\t李四\t空\t王五\n赵六\t孙七\t周八\t吴九'}></textarea>
      <div class="actions"><button class="btn" disabled=${!text.trim()} onClick=${() => doText(text)}>解析并排座</button><button class="btn ghost" onClick=${() => fileRef.current.click()}>从文件导入</button></div>
    </div>`}
    ${mode === 'auto' && html`<div class="card row gap">
      <span>每排</span><input class="input num" type="number" min="1" max="20" value=${cols} onInput=${(e) => setCols(parseInt(e.target.value, 10) || 1)} /><span>人</span>
      <button class="btn" onClick=${() => { const a = autoArrange(c.students, cols, seating ? seating.frontAtTop : true); save(() => a, `已按学号排成 ${a.rows} 排 × ${a.cols} 列`); setMode(''); setReport(null); }}>排列</button>
    </div>`}
    ${report && html`<div class="small ${report.unmatched.length || report.unseated ? 'err' : 'muted'}">已入座 ${report.n} 人${report.unseated ? `，未入座 ${report.unseated} 人` : ''}${report.unmatched.length ? `；没认出：${report.unmatched.slice(0, 12).join('、')}` : ''}</div>`}
    ${seating && html`
      <div class="row gap small">
        <span class="muted">排数</span><button class="chip" onClick=${() => save((x) => resizeSeating(x, x.rows - 1, x.cols))}>－</button><b>${seating.rows}</b><button class="chip" onClick=${() => save((x) => resizeSeating(x, x.rows + 1, x.cols))}>＋</button>
        <span class="muted">列数</span><button class="chip" onClick=${() => save((x) => resizeSeating(x, x.rows, x.cols - 1))}>－</button><b>${seating.cols}</b><button class="chip" onClick=${() => save((x) => resizeSeating(x, x.rows, x.cols + 1))}>＋</button>
      </div>
      <div class="muted tiny">${pick ? '点一个座位，把这位同学放进去' : sel != null ? '再点另一个座位交换（空位也可以）' : '点两个座位互换位置；下方未入座的同学点一下再点座位即可入座'}</div>
      <${SeatChart} seating=${seating} students=${c.students} sel=${(k) => k === sel}
        cell=${(st) => ({ body: st ? html`<b>${st.name}</b><span class="tiny muted">${st.no}</span>` : html`<span class="muted tiny">空</span>` })}
        onCell=${tapSeat} />
      ${selId && html`<div class="actions"><button class="btn ghost" onClick=${() => { save((x) => { const seats = { ...x.seats }; delete seats[selId]; return { ...x, seats }; }); setSel(null); }}>把这位同学移出座位</button></div>`}
    `}
    ${unseated.length > 0 && html`<div class="section small">未入座 ${unseated.length} 人</div>
      <div class="chips">${unseated.map((x) => html`<button class=${'chip' + (pick === x.id ? ' on' : '')} onClick=${() => { setPick(pick === x.id ? null : x.id); setSel(null); }}>${x.name}</button>`)}</div>`}
  </${Modal}>`;
}

// ---------------- 名单 ----------------
function Roster({ s }) {
  const { data, update, say } = s; const c = currentClass(data);
  const [paste, setPaste] = useState(false); const [text, setText] = useState(''); const [pending, setPending] = useState(null); const [edit, setEdit] = useState(null); const [q, setQ] = useState(''); const [seat, setSeat] = useState(false);
  const fileRef = useRef();
  const tryParse = (t) => { try { const r = parseRoster(t); if (!r.students.length) say('没有解析到学生，请检查格式'); else setPending(r); } catch (e) { say(`解析失败：${e.message}`); } };
  const doImport = (mode) => {
    const r = pending; setPending(null);
    if (mode === 'new' || !c) { const nc = { id: newId(), name: r.className || '新班级', emoji: '🎒', students: r.students, assignments: [], scores: [] }; update((d) => ({ ...d, classes: [...d.classes, nc], currentClassId: nc.id })); say(`已创建班级，共 ${r.students.length} 人`); return; }
    if (mode === 'replace') { update((d) => mapClass(d, c.id, (k) => ({ ...k, name: r.className || k.name, students: r.students, assignments: [], scores: [] }))); say(`已替换为 ${r.students.length} 人`, true); return; }
    update((d) => mapClass(d, c.id, (k) => { const byNo = new Map(k.students.map((x) => [x.no, x])); const merged = k.students.map((o) => { const n = r.students.find((x) => x.no === o.no); return n ? { ...o, name: n.name, group: n.group || o.group, gender: n.gender || o.gender } : o; }); return { ...k, students: [...merged, ...r.students.filter((x) => !byNo.has(x.no))] }; }));
    say(`已合并 ${r.students.length} 人`, true);
  };
  const list = (c?.students || []).filter((x) => !q || x.name.includes(q) || x.no.includes(q) || (x.group || '').includes(q));
  return html`<div class="page">
    <div class="hero"><div class="hero-title">名单管理</div><div class="muted-light">${c ? `${c.emoji} ${c.name} · ${c.students.length} 人` : '还没有班级：导入名单会自动新建'}</div></div>
    <div class="toolbar">
      <button class="chip" onClick=${() => fileRef.current.click()}>📥 导入 JSON/CSV</button>
      <button class="chip" onClick=${() => setPaste(true)}>📋 粘贴名单</button>
      ${c && html`<button class="chip" onClick=${() => setSeat(true)}>🪑 座位表${c.seating ? ` (${Object.keys(c.seating.seats).length}人)` : ''}</button>`}
      ${c && html`<button class="chip" onClick=${() => setEdit({ id: newId(), no: '', name: '', group: '', gender: '', note: '', longLeave: false, _new: true })}>＋ 添加学生</button>`}
      ${c && html`<button class="chip" onClick=${() => download(new Blob([JSON.stringify({ className: c.name, students: c.students.map(({ no, name, group, gender }) => ({ no, name, group, gender })) }, null, 2)], { type: 'application/json' }), `${c.name}_名单.json`)}>📤 导出名单</button>`}
      <input type="file" ref=${fileRef} accept=".json,.csv,.txt,application/json,text/*" style="display:none" onChange=${async (e) => { const f = e.target.files[0]; if (f) tryParse(await f.text()); e.target.value = ''; }} />
      <input class="input small-input" placeholder="搜索" value=${q} onInput=${(e) => setQ(e.target.value)} />
    </div>
    ${list.map((x) => html`<div class="card row" onClick=${() => setEdit(x)}>
      <${Avatar} name=${x.name} size=${38} /><div class="grow"><b>${x.name}</b>${x.longLeave ? html` <span class="tag blue">长期请假</span>` : ''}<div class="muted tiny">${[x.no, x.group, x.note].filter(Boolean).join(' · ')}</div></div><span class="muted">›</span></div>`)}
    ${c && !c.students.length && html`<div class="empty">👥 名单为空。JSON 示例：<code>{"className":"高一3班","students":[{"no":"202401","name":"张三"}]}</code></div>`}
    ${seat && c && html`<${SeatingEditor} s=${s} c=${c} onClose=${() => setSeat(false)} />`}
    ${paste && html`<${Modal} title="📋 粘贴名单" onClose=${() => setPaste(false)}>
      <div class="muted small">从 Excel 复制“学号 姓名”两列直接粘贴，或粘贴 JSON</div>
      <textarea class="input" rows="8" value=${text} onInput=${(e) => setText(e.target.value)} placeholder=${'202401\t张三\n202402\t李四'}></textarea>
      <div class="actions"><button class="btn" onClick=${() => { setPaste(false); tryParse(text); }}>解析</button></div></${Modal}>`}
    ${pending && html`<${Modal} title=${`📥 导入 ${pending.students.length} 名学生`} onClose=${() => setPending(null)}>
      ${pending.className && html`<b>班级：${pending.className}</b>`}
      <div class="small">${pending.students.slice(0, 8).map((x) => `${x.name}(${x.no})`).join('、')}${pending.students.length > 8 ? ' …' : ''}</div>
      <div class="actions col">
        ${c && html`<button class="btn" onClick=${() => doImport('merge')}>合并到当前班级</button>`}
        <button class="btn ghost" onClick=${() => doImport('new')}>创建为新班级</button>
        ${c && html`<button class="btn danger" onClick=${() => doImport('replace')}>替换当前班级（清空作业记录）</button>`}
      </div></${Modal}>`}
    ${edit && html`<${StudentEdit} st=${edit} onClose=${() => setEdit(null)}
      onDelete=${edit._new ? null : () => { if (confirm(`删除 ${edit.name}？其作业记录和积分也会删除`)) { update((d) => mapClass(d, c.id, (k) => ({ ...k, students: k.students.filter((x) => x.id !== edit.id), scores: k.scores.filter((e) => e.studentId !== edit.id), assignments: k.assignments.map((a) => { const m = { ...a.statuses }; delete m[edit.id]; return { ...a, statuses: m }; }) }))); setEdit(null); say(`已删除 ${edit.name}`, true); } }}
      onSave=${(n) => { const { _new, ...clean } = n; update((d) => mapClass(d, c.id, (k) => ({ ...k, students: _new ? [...k.students, clean] : k.students.map((x) => (x.id === clean.id ? clean : x)) }))); setEdit(null); }} />`}
  </div>`;
}
function StudentEdit({ st, onClose, onSave, onDelete }) {
  const [v, setV] = useState(st); const f = (k) => (e) => setV({ ...v, [k]: e.target.type === 'checkbox' ? e.target.checked : e.target.value });
  return html`<${Modal} title=${st._new ? '添加学生' : '编辑学生'} onClose=${onClose}>
    <input class="input" placeholder="学号" value=${v.no} onInput=${f('no')} />
    <input class="input" placeholder="姓名" value=${v.name} onInput=${f('name')} />
    <input class="input" placeholder="小组（可选）" value=${v.group} onInput=${f('group')} />
    <input class="input" placeholder="备注" value=${v.note} onInput=${f('note')} />
    <label class="row between"><span>长期请假（新作业自动标请假）</span><input type="checkbox" checked=${v.longLeave} onChange=${f('longLeave')} /></label>
    <div class="actions">${onDelete && html`<button class="btn danger" onClick=${onDelete}>删除</button>`}<button class="btn" disabled=${!v.name.trim()} onClick=${() => onSave({ ...v, name: v.name.trim(), no: v.no.trim() })}>保存</button></div>
  </${Modal}>`;
}

// ---------------- 积分 ----------------
function Scores({ s }) {
  const { data, update, say } = s; const c = currentClass(data); const rules = data.settings.rules;
  const [target, setTarget] = useState(null); const [val, setVal] = useState(1); const [reason, setReason] = useState('');
  if (!c) return html`<div class="page"><div class="empty">🏆 还没有班级</div></div>`;
  const stats = studentStats(c, rules).sort((x, y) => y.score - x.score);
  const exportAll = () => {
    const assigns = [...c.assignments].sort((x, y) => x.createdAt - y.createdAt);
    const styleOf = { SUBMITTED: ST.GREEN, EXCELLENT: ST.GOLD, LATE: ST.YELLOW, MISSING: ST.RED, LEAVE: ST.BLUE, NONE: 0 };
    const head = ['学号', '姓名', ...assigns.map((a) => `${fmtDate(a.createdAt)} ${a.subject} ${a.title}`), '已交', '迟交', '未交', '积分'].map((v) => ({ v, s: ST.HEADER }));
    const byId = Object.fromEntries(studentStats(c, rules).map((x) => [x.student.id, x]));
    const rows = [head, ...c.students.map((st) => { const x = byId[st.id]; return [st.no, st.name, ...assigns.map((a) => { const k = a.statuses[st.id] || 'NONE'; return { v: STATUS[k].label, s: styleOf[k] }; }), x.submitted, x.late, x.missing, x.score]; })];
    const rank = [['名次', '学号', '姓名', '积分'].map((v) => ({ v, s: ST.HEADER })), ...stats.map((x, i) => [i + 1, x.student.no, x.student.name, x.score])];
    const log = [['日期', '学号', '姓名', '分值', '原因'].map((v) => ({ v, s: ST.HEADER })), ...[...c.scores].sort((a, b) => b.time - a.time).map((e) => { const st = c.students.find((x) => x.id === e.studentId); return st ? [fmtDate(e.time), st.no, st.name, { v: e.delta, s: e.delta >= 0 ? ST.GREEN : ST.RED }, e.reason] : null; }).filter(Boolean)];
    download(xlsx([{ name: '作业统计', rows }, { name: '各次统计名单', rows: classStatRows(c, fmtDate) }, { name: '积分榜', rows: rank }, { name: '积分明细', rows: log }]), `${c.name}_作业统计.xlsx`);
  };
  return html`<div class="page">
    <div class="hero"><div class="hero-title">积分榜</div><div class="muted-light small">初始 ${fmtNum(rules.initial)} · 未交 ${signed(rules.missing)} · 迟交 ${signed(rules.late)} · 优秀 ${signed(rules.excellent)}</div>
      ${stats.length >= 3 && html`<div class="podium">${[1, 0, 2].map((i) => html`<div class=${'pod p' + i}><div>${['🥇', '🥈', '🥉'][i]}</div><${Avatar} name=${stats[i].student.name} size=${i ? 44 : 54} /><div class="small">${stats[i].student.name}</div><div class="pod-bar"><b>${fmtNum(stats[i].score)}</b></div></div>`)}</div>`}
    </div>
    <div class="toolbar"><button class="chip" onClick=${exportAll}>📊 导出全班 Excel</button>
      <button class="chip" onClick=${() => download(xlsx([{ name: '各次统计名单', rows: classStatRows(c, fmtDate) }]), `${c.name}_各次作业统计名单.xlsx`)}>📋 各次统计名单</button>
      <button class="chip" onClick=${() => { update((d) => mapClass(d, c.id, (k) => ({ ...k, scores: [...k.scores, ...k.students.map((x) => ({ id: newId(), studentId: x.id, delta: 1, reason: '全班表现好', time: Date.now() }))] }))); say('全班 +1 🎉', true); }}>🎉 全班 +1</button></div>
    ${stats.map((x, i) => html`<div class="card row" onClick=${() => { setTarget(x.student); setVal(1); setReason(''); }}>
      <b class="idx">${i + 1}</b><${Avatar} name=${x.student.name} size=${36} /><div class="grow"><b>${x.student.name}</b><div class="muted tiny">交${x.submitted} · 迟${x.late} · 缺${x.missing}</div></div><b class="score">${fmtNum(x.score)}</b></div>`)}
    ${target && html`<${Modal} title=${`给 ${target.name} 加减分`} onClose=${() => setTarget(null)}>
      <div class="chips center">${[-5, -2, -1, 1, 2, 5].map((v) => html`<button class=${'chip' + (v === val ? ' on' : '')} onClick=${() => setVal(v)}>${signed(v)}</button>`)}</div>
      <input class="input" placeholder="原因" value=${reason} onInput=${(e) => setReason(e.target.value)} />
      <div class="chips">${data.settings.quickReasons.map((r) => html`<button class=${'chip' + (r === reason ? ' on' : '')} onClick=${() => setReason(r)}>${r}</button>`)}</div>
      <div class="actions"><button class="btn" onClick=${() => { update((d) => mapClass(d, c.id, (k) => ({ ...k, scores: [...k.scores, { id: newId(), studentId: target.id, delta: val, reason: reason || (val > 0 ? '表现加分' : '扣分'), time: Date.now() }] }))); say(`${target.name} ${signed(val)}`, true); setTarget(null); }}>确定</button></div>
    </${Modal}>`}
  </div>`;
}

// ---------------- 设置（含云同步） ----------------
function Settings({ s }) {
  const { data, update, say, sync, setSync, runSync, onLogin, onLogout } = s;
  const [email, setEmail] = useState(''); const [pw, setPw] = useState(''); const [hist, setHist] = useState(null);
  const [pwBox, setPwBox] = useState(null);
  const m = meta(); const set = (fn) => update((d) => ({ ...d, settings: fn(d.settings) }), false);
  const doAuth = async (kind) => {
    setSync((x) => ({ ...x, busy: true, msg: kind === 'in' ? '正在登录…' : '正在注册…', error: false }));
    try {
      const sess = kind === 'in' ? await api.signIn(email, pw) : await api.signUp(email, pw);
      if (!sess) { setSync((x) => ({ ...x, busy: false, msg: `注册成功！请到邮箱 ${email} 点确认链接，然后回来登录` })); return; }
      setMeta({ baseVersion: 0, syncedHash: '', lastSyncAt: 0, auto: true });
      onLogin(sess.email);
    } catch (e) { setSync((x) => ({ ...x, busy: false, msg: e.message, error: true })); }
  };
  const fileRef = useRef();
  return html`<div class="page">
    <div class="hero"><div class="hero-title">设置</div></div>
    <div class="section">☁️ 云端同步</div>
    <div class="card">
      ${!sync.email ? html`
        <div class="muted small">现在是<b>预览模式</b>（只读）。登录后才能修改，数据自动在安卓、iPad、网页之间同步。</div>
        <input class="input" type="email" placeholder="邮箱" value=${email} onInput=${(e) => setEmail(e.target.value.trim())} />
        <input class="input" type="password" placeholder="密码（至少 6 位）" value=${pw} onInput=${(e) => setPw(e.target.value)} />
        <div class="actions"><button class="btn" disabled=${sync.busy || !email || pw.length < 6} onClick=${() => doAuth('in')}>登录</button><button class="btn ghost" disabled=${sync.busy || !email || pw.length < 6} onClick=${() => doAuth('up')}>注册</button></div>
        <button class="link" disabled=${!email.includes('@')} onClick=${async () => { try { await api.recover(email); say('重置密码邮件已发送'); } catch (e) { say(e.message); } }}>忘记密码？</button>` : html`
        <div class="row"><b class="grow">☁️ ${sync.email}</b>${sync.busy && html`<span class="spin"></span>`}</div>
        <div class="muted small">云端版本 v${m.baseVersion}${m.lastSyncAt ? ` · 上次同步 ${fmtDate(m.lastSyncAt)}` : ''}</div>
        <label class="row between"><span>自动同步</span><input type="checkbox" checked=${m.auto !== false} onChange=${(e) => { setMeta({ ...meta(), auto: e.target.checked }); setSync((x) => ({ ...x })); }} /></label>
        <div class="actions"><button class="btn" disabled=${sync.busy} onClick=${runSync}>立即同步</button><button class="btn ghost" onClick=${async () => { try { setHist(await api.history()); } catch (e) { say(e.message); } }}>历史版本</button></div>
        <label class="row between"><span><b>允许未登录预览</b><div class="muted tiny">打开后，任何人不登录也能只读查看你的班级数据（学生姓名、作业、积分）；关闭即恢复私密</div></span>
          <input type="checkbox" checked=${sync.publicRead !== false} disabled=${sync.publicRead == null} onChange=${async (e) => { const v = e.target.checked; try { await api.setPublic(v); setMeta({ ...meta(), previewInit: true }); setSync((x) => ({ ...x, publicRead: v })); say(v ? '已开放未登录预览' : '已关闭预览，数据恢复私密'); } catch (err) { say(err.message); } }} /></label>
        <div class="actions"><button class="link" onClick=${() => setPwBox({ old: '', n1: '', n2: '', err: '', busy: false })}>修改密码</button>
        <button class="link danger" onClick=${async () => { await api.signOut(); onLogout(); }}>退出登录</button></div>`}
      ${sync.msg && html`<div class=${'small ' + (sync.error ? 'err' : 'muted')}>${sync.msg}</div>`}
    </div>
    <div class="section">🎨 主题</div>
    <div class="card"><div class="chips">${Object.entries(PALETTES).map(([k, [a, b, n]]) => html`<button class=${'chip' + (data.settings.theme === k ? ' on' : '')} style=${{ background: `linear-gradient(135deg,${a},${b})`, color: '#fff', borderColor: 'transparent' }} onClick=${() => set((x) => ({ ...x, theme: k }))}>${n}</button>`)}</div>
      <div class="chips">${['跟随系统', '浅色', '深色'].map((l, i) => html`<button class=${'chip' + (data.settings.dark === i ? ' on' : '')} onClick=${() => set((x) => ({ ...x, dark: i }))}>${l}</button>`)}</div></div>
    <div class="section">🧮 计分规则</div>
    <div class="card">${[['initial', '初始分'], ['submitted', '✅ 已交'], ['excellent', '🌟 优秀'], ['late', '🐢 迟交'], ['missing', '❌ 未交'], ['leave', '🏖️ 请假']].map(([k, l]) => html`
      <div class="row between rule"><span>${l}</span><input class="input num" type="number" step="0.5" value=${data.settings.rules[k]} onChange=${(e) => { const v = parseFloat(e.target.value); if (!Number.isNaN(v)) set((x) => ({ ...x, rules: { ...x.rules, [k]: v } })); }} /></div>`)}</div>
    <div class="section">💾 数据</div>
    <div class="card"><div class="actions">
      <button class="btn ghost" onClick=${() => download(new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' }), `作业通备份_${new Date().toISOString().slice(0, 10)}.json`)}>备份全部数据</button>
      <button class="btn ghost" onClick=${() => fileRef.current.click()}>从备份恢复</button>
      <input type="file" accept=".json,application/json" ref=${fileRef} style="display:none" onChange=${async (e) => { const f = e.target.files[0]; e.target.value = ''; if (!f) return; try { const d = normalizeData(JSON.parse(await f.text())); update(() => withLocalKey(d, data)); say('已从备份恢复', true); } catch { say('文件格式不正确'); } }} />
    </div><div class="muted tiny">备份文件与安卓版通用。</div></div>
    <div class="muted tiny center pad">作业通 网页版 · 数据保存在本浏览器${sync.email ? '并同步到云端' : ''}</div>
    ${pwBox && html`<${Modal} title="🔑 修改密码" onClose=${() => !pwBox.busy && setPwBox(null)}>
      <input class="input" type="password" placeholder="旧密码" value=${pwBox.old} onInput=${(e) => setPwBox({ ...pwBox, old: e.target.value })} />
      <input class="input" type="password" placeholder="新密码（至少 6 位）" value=${pwBox.n1} onInput=${(e) => setPwBox({ ...pwBox, n1: e.target.value })} />
      <input class="input" type="password" placeholder="再输一次新密码" value=${pwBox.n2} onInput=${(e) => setPwBox({ ...pwBox, n2: e.target.value })} />
      <div class="muted tiny">这个账号和你的游戏共用，改完后游戏里也用新密码登录。</div>
      ${pwBox.err && html`<div class="small err">${pwBox.err}</div>`}
      <div class="actions"><button class="btn" disabled=${pwBox.busy || !pwBox.old || !pwBox.n1} onClick=${async () => {
        const err = pwBox.n1.length < 6 ? '新密码至少 6 位' : pwBox.n1 !== pwBox.n2 ? '两次输入的新密码不一样' : pwBox.n1 === pwBox.old ? '新密码不能和旧密码一样' : '';
        if (err) { setPwBox({ ...pwBox, err }); return; }
        setPwBox({ ...pwBox, busy: true, err: '' });
        try { await api.changePassword(pwBox.old, pwBox.n1); setPwBox(null); say('✅ 密码已修改'); } catch (e) { setPwBox((x) => ({ ...x, busy: false, err: e.message })); }
      }}>${pwBox.busy ? '提交中…' : '确认修改'}</button></div>
    </${Modal}>`}
    ${hist && html`<${Modal} title="☁️ 云端历史版本" onClose=${() => setHist(null)}>
      ${!hist.length && html`<div class="muted">还没有历史版本（每次云端被覆盖前自动保存一份，最多 30 份）</div>`}
      ${hist.map((h0) => html`<div class="card row" onClick=${async () => { if (!confirm(`恢复 v${h0.version}？本机数据会替换为这个版本并上传为最新版`)) return; try { const d = withLocalKey(normalizeData(await api.historyData(h0.id)), data); update(() => d); await pushAs(d, -1); setHist(null); say('已恢复该历史版本'); } catch (e) { say(e.message); } }}>
        <div class="grow"><b>v${h0.version} · ${(h0.saved_at || '').slice(0, 10)}</b><div class="muted tiny">${h0.device || '未知设备'}</div></div><span>恢复 ›</span></div>`)}
    </${Modal}>`}
  </div>`;
}

function Conflict({ s }) {
  const { sync, resolve, data } = s; const r = sync.conflict; if (!r) return null;
  const brief = (d) => `${d.classes.length} 个班 · ${d.classes.reduce((n, c) => n + c.students.length, 0)} 名学生 · ${d.classes.reduce((n, c) => n + c.assignments.length, 0)} 次作业`;
  return html`<${Modal} title="☁️ 两台设备都改过数据">
    <div class="small">本机：${brief(data)}</div><div class="small">云端 v${sync.conflictVersion}：${brief(r)}</div>
    <div class="muted small">推荐“合并”：两边的班级、学生、作业和登记都保留；同一个学生同一次作业不一致时以本机为准。</div>
    <div class="actions col"><button class="btn" onClick=${() => resolve('merge')}>合并（推荐）</button><button class="btn ghost" onClick=${() => resolve('cloud')}>用云端覆盖本机</button><button class="btn ghost" onClick=${() => resolve('local')}>用本机覆盖云端</button></div>
  </${Modal}>`;
}

// ---------------- 热力图 ----------------
function Heatmap({ s, open }) {
  const { data } = s; const c = currentClass(data);
  if (!c || !c.assignments.length || !c.students.length) return html`<div class="page"><div class="hero"><div class="hero-title">🔥 作业热力图</div></div><div class="empty">📈 布置并登记几次作业后，这里会出现全班热力图</div></div>`;
  const recent = [...c.assignments].sort((x, y) => x.createdAt - y.createdAt).slice(-30);
  const md = (t) => { const d = new Date(t); return `${d.getMonth() + 1}/${d.getDate()}`; };
  const rate = (st) => { let got = 0; let need = 0; for (const a of recent) { const k = a.statuses[st.id] || 'NONE'; if (k === 'LEAVE' || k === 'NONE') continue; need++; if (k === 'SUBMITTED' || k === 'EXCELLENT' || k === 'LATE') got++; } return need ? Math.round((got / need) * 100) : null; };
  return html`<div class="page">
    <div class="hero"><div class="hero-title">🔥 作业热力图</div><div class="muted-light">${c.emoji} ${c.name} · 最近 ${recent.length} 次作业</div></div>
    <div class="card">
      <div class="heat-legend">${STATUS_KEYS.map((k) => html`<span><i style=${{ background: STATUS[k].color, opacity: k === 'NONE' ? 0.3 : 0.9 }}></i>${STATUS[k].label}</span>`)}</div>
      <div class="muted tiny">每行一位同学，每格一次作业，点格子进入该次作业</div>
      <div class="heat-wrap"><table class="heat">
        <thead><tr><th class="heat-name"></th>${recent.map((a) => html`<th title=${`${fmtDate(a.createdAt)} ${a.subject} ${a.title}`} onClick=${() => open({ view: 'assign', id: a.id })}>${md(a.createdAt)}</th>`)}<th>交齐率</th></tr></thead>
        <tbody>${c.students.map((st) => { const r = rate(st); return html`<tr>
          <td class="heat-name">${st.name}</td>
          ${recent.map((a) => { const k = a.statuses[st.id] || 'NONE'; return html`<td><button class="heat-cell" title=${`${st.name} · ${a.title}：${STATUS[k].label}`} style=${{ background: STATUS[k].color, opacity: k === 'NONE' ? 0.25 : 0.9 }} onClick=${() => open({ view: 'assign', id: a.id })}></button></td>`; })}
          <td class=${'heat-rate' + (r !== null && r < 80 ? ' low' : '')}>${r === null ? '—' : r + '%'}</td></tr>`; })}</tbody>
      </table></div>
    </div>
  </div>`;
}

// ---------------- 统计名单导出 ----------------
function exportStatList(c, title, subject, createdAt, statuses, comments = {}) {
  const sheets = statListSheets({ className: c.name, title, subject, date: fmtDate(createdAt), students: c.students, statuses, comments });
  download(xlsx(sheets), `${[subject, title].filter(Boolean).join('_') || '作业'}_统计名单.xlsx`.replace(/[\\/:*?"<>|]/g, '_'));
}
const fmtTime = (t) => { const d = new Date(t); const p = (n) => String(n).padStart(2, '0'); return `${d.getMonth() + 1}月${d.getDate()}日 ${p(d.getHours())}:${p(d.getMinutes())}`; };
const toLocalInput = (t) => { const d = new Date(t); const p = (n) => String(n).padStart(2, '0'); return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`; };

// ---------------- 草稿 ----------------
/** 定时上传：选时间（快捷：1 小时后 / 今晚 8 点 / 明早 7 点） */
function ScheduleDialog({ initial, onClose, onSave }) {
  const at = (h, m, addDay) => { const d = new Date(); if (addDay) d.setDate(d.getDate() + 1); d.setHours(h, m, 0, 0); return d.getTime(); };
  const [v, setV] = useState(toLocalInput(initial || Date.now() + 3600000));
  const quick = [['45 分钟后', Date.now() + 45 * 60000], ['1 小时后', Date.now() + 3600000], ['今晚 20:00', at(20, 0, new Date().getHours() >= 20)], ['明早 7:00', at(7, 0, true)]];
  const t = new Date(v).getTime();
  return html`<${Modal} title="⏰ 定时上传" onClose=${onClose}>
    <div class="chips">${quick.map(([l, x]) => html`<button class="chip" onClick=${() => setV(toLocalInput(x))}>${l}</button>`)}</div>
    <input class="input" type="datetime-local" value=${v} onInput=${(e) => setV(e.target.value)} />
    <div class="muted tiny">到时间自动写进作业并上传到云端。网页需要开着（可以在后台）；如果到点时网页没开，下次打开会立即上传。</div>
    <div class="actions">${initial && html`<button class="btn ghost" onClick=${() => onSave(null)}>取消定时</button>`}
      <button class="btn" disabled=${!t || Number.isNaN(t)} onClick=${() => onSave(t <= Date.now() ? Date.now() + 1000 : t)}>确定</button></div>
  </${Modal}>`;
}

/** 输入首字母/学号/姓名登记（草稿页用，与作业页同一套解析） */
function parseTyped(c, settings, text) {
  const parse = createVoiceParser(c.students, { tolerance: [0.6, 1, 1.4][settings.voiceFuzzy ?? 1], initials: true });
  const r = parse(text); const sure = []; const amb = [];
  for (const m of r.matches) { if (m.ids.length === 1) { if (!sure.includes(m.ids[0])) sure.push(m.ids[0]); } else amb.push(m); }
  return { sure, amb, left: r.leftover };
}

function DraftView({ s, id, back, open }) {
  const { data, say } = s;
  const dr = s.drafts.find((x) => x.id === id);
  const c = dr && data.classes.find((k) => k.id === dr.classId);
  const [filter, setFilter] = useState('ALL'); const [sheet, setSheet] = useState(null); const [edit, setEdit] = useState(false); const [sched, setSched] = useState(false);
  const [typed, setTyped] = useState(''); const [typedAs, setTypedAs] = useState('SUBMITTED'); const [typedRes, setTypedRes] = useState(null);
  const [view, setView] = useState(c?.seating ? 'seat' : 'card');
  useEffect(() => { if (!dr || !c) back(); }, [dr, c]);
  if (!dr || !c) return null;
  const setStatus = (ids, st) => s.editDraft(id, (x) => { const m = { ...x.statuses }; ids.forEach((i) => { if (st === 'NONE') delete m[i]; else m[i] = st; }); return { ...x, statuses: m }; });
  const t = tally(c.students, dr.statuses); const changed = draftChanges(dr);
  const toggle = (st) => { const x = dr.statuses[st.id] || 'NONE'; setStatus([st.id], x === 'NONE' || x === 'MISSING' ? 'SUBMITTED' : 'NONE'); };
  const submitTyped = () => {
    if (!typed.trim()) return;
    const r = parseTyped(c, data.settings, typed.trim());
    if (r.sure.length) setStatus(r.sure, typedAs);
    const nm = (i) => c.students.find((x) => x.id === i)?.name || '?';
    setTypedRes(r); setTyped('');
    if (r.sure.length) say(`草稿：${r.sure.map(nm).join('、')} → ${STATUS[typedAs].label}`); else if (!r.amb.length) say('没有认出名单里的同学');
  };
  const filters = { ALL: ['全部', () => true], NONE: ['未登记', (x) => x === 'NONE'], MISSING: ['未交', (x) => x === 'MISSING' || x === 'NONE'], DONE: ['已交', (x) => ['SUBMITTED', 'EXCELLENT', 'LATE'].includes(x)], CHANGED: ['改动', null] };
  const list = c.students.filter((st) => (filter === 'CHANGED' ? changed.includes(st.id) : filters[filter][1](dr.statuses[st.id] || 'NONE')));
  let pressTimer = 0;
  return html`<div class="page">
    <div class="hero draft-hero">
      <div class="row"><button class="icon-btn light" onClick=${back}>←</button>
        <div class="grow" onClick=${() => setEdit(true)}><div class="muted-light small">📝 草稿 · 只保存在本机 · ${dr.isNew ? '新作业' : '已有作业'} · ${c.name}</div><div class="hero-title">${dr.title || '未命名作业'}</div><div class="muted-light tiny">${dr.subject} · 最后编辑 ${fmtTime(dr.updatedAt)}</div></div></div>
      <div class="row gap">
        <div class="ring" style=${{ '--p': t.rate }}><b>${t.handedIn}</b><span>/ ${t.expected}</span></div>
        <div class="chips light">${['SUBMITTED', 'MISSING', 'NONE', 'LATE', 'LEAVE', 'EXCELLENT'].map((k) => html`<span class="chip">${STATUS[k].label} ${t.counts[k]}</span>`)}<span class="chip">✏️ 改动 ${changed.length} 人</span></div>
      </div>
    </div>
    <div class="card draft-bar">
      <div class="grow small">${dr.scheduledAt ? html`⏰ 将在 <b>${fmtTime(dr.scheduledAt)}</b> 自动上传` : '还没上传：云端和其它设备暂时看不到这些登记'}</div>
      <button class="btn" onClick=${() => s.uploadDrafts([id])}>☁️ 上传</button>
      <button class="btn ghost" onClick=${() => setSched(true)}>⏰ ${dr.scheduledAt ? '改时间' : '定时上传'}</button>
    </div>
    <div class="toolbar">
      <button class="chip" onClick=${() => open({ view: 'stattable', draftId: id })}>📊 从统计表导入</button>
      <button class="chip" onClick=${() => { if (confirm(`将 ${t.counts.NONE} 名未登记的同学标记为“未交”？`)) setStatus(t.lists.NONE.map((x) => x.id), 'MISSING'); }}>☑️ 剩余标未交</button>
      <button class="chip" onClick=${() => exportStatList(c, dr.title, dr.subject, dr.createdAt, dr.statuses, dr.comments)}>📋 统计名单</button>
      <button class="chip danger" onClick=${() => { if (confirm('删除这份草稿？里面的登记不会上传')) { s.setDrafts((xs) => xs.filter((x) => x.id !== id)); say('已删除草稿'); } }}>🗑️ 删除草稿</button>
    </div>
    <form class="typed" onSubmit=${(e) => { e.preventDefault(); submitTyped(); }}>
      <input class="input" placeholder="⌨️ 输入拼音首字母登记：zjy lzh wf（也可写学号、姓名）" value=${typed} onInput=${(e) => setTyped(e.target.value)} autocapitalize="off" autocomplete="off" />
      <select class="input" value=${typedAs} onChange=${(e) => setTypedAs(e.target.value)}>${['SUBMITTED', 'EXCELLENT', 'LATE', 'MISSING', 'LEAVE'].map((k) => html`<option value=${k}>${STATUS[k].label}</option>`)}</select>
      <button class="btn" type="submit">登记</button>
    </form>
    ${typedRes && (typedRes.amb.length > 0 || typedRes.left) && html`<div class="card small">
      ${typedRes.amb.map((m) => html`<div class="row gap">“${m.heard}”有多位同学：${m.ids.map((sid) => { const st = c.students.find((x) => x.id === sid); return html`<button class="chip" onClick=${() => { setStatus([sid], typedAs); setTypedRes({ ...typedRes, amb: typedRes.amb.filter((x) => x !== m) }); }}>${st.name} ${st.no}</button>`; })}</div>`)}
      ${typedRes.left && html`<div class="muted">没认出：${typedRes.left}</div>`}</div>`}
    <div class="toolbar">
      ${c.seating && html`<button class=${'chip' + (view === 'seat' ? ' on' : '')} onClick=${() => setView(view === 'seat' ? 'card' : 'seat')}>🪑 座位表</button>`}
      ${Object.entries(filters).map(([k, [l, f]]) => html`<button class=${'chip' + (k === filter ? ' on' : '')} onClick=${() => setFilter(k)}>${l} ${k === 'CHANGED' ? changed.length : c.students.filter((st) => f(dr.statuses[st.id] || 'NONE')).length}</button>`)}
    </div>
    <div class="muted small pad">点一下切换 已交/未登记 · 长按（或右键）设置状态、备注 · 带 ✏️ 的是相对云端改过的</div>
    ${view === 'seat' && c.seating ? html`<${SeatChart} seating=${c.seating} students=${c.students} sel=${() => false}
        cell=${(st) => { if (!st) return { body: '' }; const x = dr.statuses[st.id] || 'NONE'; return { style: x === 'NONE' ? {} : { background: STATUS[x].color + '33', borderColor: STATUS[x].color }, body: html`<b>${changed.includes(st.id) ? '✏️' : ''}${st.name}</b><span class="tiny" style=${{ color: STATUS[x].color }}>${STATUS[x].label}</span>`, onMenu: () => setSheet(st) }; }}
        onCell=${(k, st) => st && toggle(st)} onRow=${() => {}} onCol=${() => {}} />
      ${(() => { const un = c.students.filter((x) => c.seating.seats[x.id] == null); return un.length ? html`<div class="muted tiny pad">未入座：${un.map((x) => html`<button class="chip" onClick=${() => toggle(x)}>${x.name} · ${STATUS[dr.statuses[x.id] || 'NONE'].label}</button>`)}</div>` : ''; })()}` : html`
    <div class="grid">${list.map((st) => { const x = dr.statuses[st.id] || 'NONE'; return html`
      <div class="stu" style=${x === 'NONE' ? {} : { background: STATUS[x].color + '26', borderColor: STATUS[x].color + '66' }}
        onClick=${() => { if (pressTimer === -1) { pressTimer = 0; return; } toggle(st); }}
        onContextMenu=${(e) => { e.preventDefault(); setSheet(st); }}
        onTouchStart=${() => { pressTimer = setTimeout(() => { pressTimer = -1; setSheet(st); }, 500); }}
        onTouchEnd=${() => { if (pressTimer > 0) clearTimeout(pressTimer); }} onTouchMove=${() => { if (pressTimer > 0) clearTimeout(pressTimer); }}>
        <${Avatar} name=${st.name} />
        <div class="stu-name">${changed.includes(st.id) ? '✏️' : ''}${st.name}</div><div class="muted tiny">${st.no}</div>
        <div class="tiny" style=${{ color: STATUS[x].color, fontWeight: 700 }}>${STATUS[x].label}</div>
        ${dr.comments[st.id] && html`<div class="tiny muted">💬 ${dr.comments[st.id]}</div>`}
      </div>`; })}</div>`}
    ${sheet && html`<${Modal} title=${html`<span class="row"><${Avatar} name=${sheet.name} size=${36} /> ${sheet.name} <span class="muted small">${sheet.no}</span></span>`} onClose=${() => setSheet(null)}>
      <div class="label">草稿里的状态</div>
      <div class="chips">${STATUS_KEYS.map((k) => { const cur = dr.statuses[sheet.id] || 'NONE'; return html`<button class=${'chip' + (k === cur ? ' on' : '')} style=${k === cur ? { background: STATUS[k].color, borderColor: STATUS[k].color } : {}} onClick=${() => setStatus([sheet.id], k)}>${STATUS[k].emoji} ${STATUS[k].label}</button>`; })}</div>
      ${!dr.isNew && html`<div class="muted tiny">云端现在：${STATUS[dr.base.statuses[sheet.id] || 'NONE'].label}</div>`}
      <div class="label">备注</div>
      <input class="input" placeholder="如：字迹潦草、缺第3页" value=${dr.comments[sheet.id] || ''} onInput=${(e) => { const v = e.target.value; s.editDraft(id, (x) => { const m = { ...x.comments }; if (v.trim()) m[sheet.id] = v; else delete m[sheet.id]; return { ...x, comments: m }; }); }} />
      <div class="actions"><button class="btn" onClick=${() => setSheet(null)}>完成</button></div>
    </${Modal}>`}
    ${edit && html`<${AssignDialog} initial=${dr} subjects=${data.settings.subjects} onClose=${() => setEdit(false)} onSave=${(title, subject) => { s.editDraft(id, (x) => ({ ...x, title, subject })); setEdit(false); }} />`}
    ${sched && html`<${ScheduleDialog} initial=${dr.scheduledAt} onClose=${() => setSched(false)} onSave=${(at) => { s.editDraft(id, (x) => ({ ...x, scheduledAt: at })); setSched(false); say(at ? `⏰ 将在 ${fmtTime(at)} 自动上传` : '已取消定时上传'); }} />`}
  </div>`;
}

function DraftList({ s, back, open }) {
  const { data, say } = s; const [sched, setSched] = useState(null);
  const list = [...s.drafts].sort((a, b) => b.updatedAt - a.updatedAt);
  const cls = (id) => data.classes.find((k) => k.id === id);
  return html`<div class="page">
    <div class="topbar"><button class="icon-btn" onClick=${back}>←</button><div class="grow"><b>🗂️ 草稿箱</b><div class="muted small">草稿只保存在这台设备上，上传后才会进入云端数据库</div></div>
      ${list.length > 1 && html`<button class="btn" onClick=${() => s.uploadDrafts(list.map((x) => x.id))}>全部上传</button>`}</div>
    ${!list.length && html`<div class="empty">📭 没有草稿<br /><br />新建作业时勾选“先存为草稿”，或在作业页点“📝 在草稿里登记”、用“📊 统计表识别”导入</div>`}
    ${list.map((dr) => { const c = cls(dr.classId); const t = c ? tally(c.students, dr.statuses) : null; return html`<div class="card draft-card">
      <div class="row" onClick=${() => (c ? open({ view: 'draft', id: dr.id }) : say('草稿所属的班级已不存在'))}>
        <div class="subj" style=${{ background: subjectColor(dr.subject) + '22', color: subjectColor(dr.subject) }}>${(dr.subject || '草')[0]}</div>
        <div class="grow"><div class="title">${dr.title || '未命名作业'} ${dr.isNew ? html`<span class="tag">新作业</span>` : ''}</div>
          <div class="muted small">${c ? `${c.emoji} ${c.name}` : '⚠️ 班级已删除'} · 改动 ${draftChanges(dr).length} 人 · 编辑于 ${fmtTime(dr.updatedAt)}</div>
          ${dr.scheduledAt && html`<div class="small" style="color:var(--g1)">⏰ ${fmtTime(dr.scheduledAt)} 自动上传</div>`}</div>
        ${t && html`<div class="right"><b>${t.handedIn}/${t.expected}</b><div class="muted small">${Math.round(t.rate * 100)}%</div></div>`}
      </div>
      <div class="actions">
        <button class="chip danger" onClick=${() => { if (confirm('删除这份草稿？')) s.setDrafts((xs) => xs.filter((x) => x.id !== dr.id)); }}>删除</button>
        <button class="chip" onClick=${() => setSched(dr)}>⏰ ${dr.scheduledAt ? '改时间' : '定时上传'}</button>
        <button class="chip on" onClick=${() => s.uploadDrafts([dr.id])}>☁️ 上传</button>
      </div></div>`; })}
    ${sched && html`<${ScheduleDialog} initial=${sched.scheduledAt} onClose=${() => setSched(null)} onSave=${(at) => { s.editDraft(sched.id, (x) => ({ ...x, scheduledAt: at })); setSched(null); say(at ? `⏰ 将在 ${fmtTime(at)} 自动上传` : '已取消定时上传'); }} />`}
  </div>`;
}

// ---------------- 统计表识别 ----------------
function StatTable({ s, draftId, back, open, replace }) {
  const { data, say } = s; const c = currentClass(data);
  const target = draftId ? s.drafts.find((x) => x.id === draftId) : null;
  const tc = target ? data.classes.find((k) => k.id === target.classId) : c;
  const [res, setRes] = useState(null); const [col, setCol] = useState(0); const [st, setSt] = useState({}); const [src, setSrc] = useState('');
  const [paste, setPaste] = useState(false); const [text, setText] = useState(''); const [busy, setBusy] = useState(null);
  const [saveAs, setSaveAs] = useState(false); const [into, setInto] = useState('');
  const fileRef = useRef(); const imgRef = useRef();
  if (!tc) return html`<div class="page"><div class="topbar"><button class="icon-btn" onClick=${back}>←</button><b>统计表识别</b></div><div class="empty">先建立班级、导入名单</div></div>`;
  const useResult = (r, from) => {
    if (!r.rows.length) { say('没有认出名单里的同学，请检查表格（需要有姓名或学号一列）'); return; }
    if (!r.columns.length) { say('认出了学生，但没找到 ✓ × 优 迟 假 这类标记列'); }
    setRes(r); setCol(Math.max(0, r.columns.length - 1)); setSt(columnStatuses(r, Math.max(0, r.columns.length - 1))); setSrc(from);
  };
  const onFile = async (f) => {
    if (!f) return;
    try {
      if (/\.xlsx$/i.test(f.name) || f.type.includes('spreadsheetml')) useResult(recognizeTable(await readXlsx(await f.arrayBuffer()), tc.students), `Excel：${f.name}`);
      else if (/\.xls$/i.test(f.name)) say('老版 .xls 读不了：请在 Excel 里“另存为 .xlsx 或 CSV”再导入');
      else useResult(recognizeTable(splitTable(await f.text()), tc.students), `文件：${f.name}`);
    } catch (e) { say(`读取失败：${e.message}`); }
  };
  const onImage = async (f) => {
    if (!f) return;
    setBusy({ p: 0, msg: '准备识别…' });
    try {
      const { recognizeLines: ocr } = await import('./ocr.js');
      const lines = await ocr(f, (p, msg) => setBusy({ p, msg }));
      useResult(tableFromLines(mergeRows(lines), tc.students), `照片：${f.name}`);
    } catch (e) { say(`识别失败：${e.message}`); }
    setBusy(null);
  };
  const pickCol = (k) => { setCol(k); setSt(columnStatuses(res, k)); };
  const t = res ? tally(tc.students, st) : null;
  const header = res?.columns[col]?.header || '';
  const titleGuess = header && !/^第\d+列$/.test(header) ? header : `${today()} 作业`;
  const toDraft = (title, subject) => {
    const dr = makeDraft(tc, null, { title, subject, statuses: st });
    s.setDrafts((xs) => [dr, ...xs]); replace({ view: 'draft', id: dr.id }); say('📝 已存为草稿（只保存在本机）');
  };
  const intoAssign = (aid) => {
    const a = tc.assignments.find((x) => x.id === aid); if (!a) return;
    const ex = s.drafts.find((x) => x.assignmentId === a.id);
    if (ex) { s.editDraft(ex.id, (x) => ({ ...x, statuses: { ...x.statuses, ...st } })); replace({ view: 'draft', id: ex.id }); } else { const dr = makeDraft(tc, a, { statuses: st }); s.setDrafts((xs) => [dr, ...xs]); replace({ view: 'draft', id: dr.id }); }
    say(`已写入“${a.title}”的草稿，确认后再上传`);
  };
  const allCols = () => {
    const made = res.columns.map((cc, k) => makeDraft(tc, null, { title: /^第\d+列$/.test(cc.header) ? `${today()} 作业 ${k + 1}` : cc.header, subject: '', statuses: columnStatuses(res, k) }));
    s.setDrafts((xs) => [...made, ...xs]); replace({ view: 'drafts' }); say(`已存为 ${made.length} 份草稿`);
  };
  const nm = Object.fromEntries(tc.students.map((x) => [x.id, x]));
  const inTable = new Set(res ? res.rows.map((r) => r.studentId) : []);
  return html`<div class="page">
    <div class="topbar"><button class="icon-btn" onClick=${back}>←</button><div class="grow"><b>📊 统计表识别</b><div class="muted small">${target ? `导入到草稿：${target.title}` : `${tc.emoji} ${tc.name}`} · 认出每个同学交没交，自动统计</div></div></div>
    <div class="card">
      <div class="small">支持：拍照/图片（登记表、点名册）、Excel（.xlsx）、CSV、直接粘贴表格。每行一个同学（姓名或学号），后面是标记：</div>
      <div class="muted tiny">✓ √ 交 1 → 已交 · 优 A ★ → 优秀 · 迟 补 → 迟交 · × X 未交 缺 0 → 未交 · 假 病 → 请假 · 空白 → 未登记。有多列（多次作业）时可以选择用哪一列。</div>
      <div class="actions">
        <button class="btn" onClick=${() => imgRef.current.click()}>📷 拍照 / 图片</button>
        <button class="btn ghost" onClick=${() => fileRef.current.click()}>📄 Excel / CSV</button>
        <button class="btn ghost" onClick=${() => setPaste(true)}>📋 粘贴</button>
      </div>
      <input type="file" ref=${imgRef} accept="image/*" style="display:none" onChange=${(e) => { const f = e.target.files[0]; e.target.value = ''; onImage(f); }} />
      <input type="file" ref=${fileRef} accept=".xlsx,.xls,.csv,.txt,.tsv,text/*,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" style="display:none" onChange=${(e) => { const f = e.target.files[0]; e.target.value = ''; onFile(f); }} />
      ${busy && html`<div class="row"><span class="spin"></span><span class="small">${busy.msg} ${Math.round(busy.p * 100)}%</span></div>`}
    </div>
    ${res && html`
      <div class="card stat-sum">
        <div class="muted tiny">${src} · 认出 ${res.rows.length} 人${res.unmatched.length ? ` · ${res.unmatched.length} 行没对上名单` : ''}</div>
        ${res.columns.length > 1 && html`<div class="label">表里有 ${res.columns.length} 列标记，用哪一列：</div><div class="chips">${res.columns.map((cc, k) => html`<button class=${'chip' + (k === col ? ' on' : '')} onClick=${() => pickCol(k)}>${cc.header}</button>`)}</div>`}
        <div class="row gap stat-row">
          <div class="ring dark" style=${{ '--p': t.rate }}><b>${t.handedIn}</b><span>/ ${t.expected}</span></div>
          <div class="grow">
            <div><b>交齐率 ${Math.round(t.rate * 100)}%</b></div>
            <div class="chips">${['SUBMITTED', 'EXCELLENT', 'LATE', 'MISSING', 'LEAVE', 'NONE'].map((k) => html`<span class="chip" style=${{ borderColor: STATUS[k].color, color: STATUS[k].color }}>${STATUS[k].label} ${t.counts[k]}</span>`)}</div>
            ${t.counts.MISSING > 0 && html`<div class="small">❌ 未交：${t.lists.MISSING.map((x) => x.name).join('、')}</div>`}
            ${t.counts.NONE > 0 && html`<div class="small muted">⏳ 未登记：${t.lists.NONE.map((x) => x.name).join('、')}</div>`}
          </div>
        </div>
        <div class="actions">
          <button class="btn ghost" onClick=${() => exportStatList(tc, header, '', Date.now(), st)}>📋 导出统计名单 Excel</button>
          ${target ? html`<button class="btn" onClick=${() => { s.editDraft(target.id, (x) => ({ ...x, statuses: { ...x.statuses, ...st } })); say(`已导入 ${Object.keys(st).length} 人到草稿`); back(); }}>写入这份草稿</button>` : html`
            ${res.columns.length > 1 && html`<button class="btn ghost" onClick=${allCols}>每列各存一份草稿</button>`}
            <button class="btn ghost" onClick=${() => setInto(tc.assignments[0]?.id || '')} disabled=${!tc.assignments.length}>写入已有作业</button>
            <button class="btn" onClick=${() => setSaveAs(true)}>📝 存为新作业草稿</button>`}
        </div>
      </div>
      <div class="muted small pad">核对一下：点状态可以改；没对上的行在最下面</div>
      <div class="card stat-list">${tc.students.map((x) => { const k = st[x.id] || 'NONE'; return html`<div class="row stat-line">
        <span class="grow"><b>${x.name}</b> <span class="muted tiny">${x.no}${inTable.has(x.id) ? '' : ' · 表里没有'}</span></span>
        <select class="input stat-sel" style=${{ color: STATUS[k].color }} value=${k} onChange=${(e) => setSt((m) => { const n = { ...m }; if (e.target.value === 'NONE') delete n[x.id]; else n[x.id] = e.target.value; return n; })}>${STATUS_KEYS.map((q) => html`<option value=${q}>${STATUS[q].label}</option>`)}</select></div>`; })}</div>
      ${res.unmatched.length > 0 && html`<div class="card small"><b>没对上名单的行（${res.unmatched.length}）</b>${res.unmatched.slice(0, 30).map((u) => html`<div class="muted">${u}</div>`)}</div>`}`}
    ${paste && html`<${Modal} title="📋 粘贴统计表" onClose=${() => setPaste(false)}>
      <div class="muted small">从 Excel / WPS / 在线表格里选中整张表复制，粘贴到这里</div>
      <textarea class="input" rows="9" value=${text} onInput=${(e) => setText(e.target.value)} placeholder=${'姓名\t10月30日\t10月31日\n张三\t✓\t×\n李四\t优\t✓'}></textarea>
      <div class="actions"><button class="btn" disabled=${!text.trim()} onClick=${() => { setPaste(false); useResult(recognizeTable(splitTable(text), tc.students), '粘贴的表格'); }}>识别</button></div></${Modal}>`}
    ${saveAs && html`<${AssignDialog} initial=${{ title: titleGuess, subject: data.settings.subjects[0] || '' }} subjects=${data.settings.subjects} onClose=${() => setSaveAs(false)} onSave=${(title, subject) => { setSaveAs(false); toDraft(title, subject); }} />`}
    ${into !== '' && html`<${Modal} title="写入已有作业" onClose=${() => setInto('')}>
      <div class="muted small">会先存为这次作业的草稿（只改表里有标记的同学），核对后再上传</div>
      <select class="input" value=${into} onChange=${(e) => setInto(e.target.value)}>${[...tc.assignments].sort((a, b) => b.createdAt - a.createdAt).map((a) => html`<option value=${a.id}>${fmtDate(a.createdAt)} ${a.subject} ${a.title}</option>`)}</select>
      <div class="actions"><button class="btn" onClick=${() => { const v = into; setInto(''); intoAssign(v); }}>确定</button></div></${Modal}>`}
  </div>`;
}

// ---------------- 外壳 ----------------
function App() {
  const s = useStore();
  const [tab, setTab] = useState('home'); const [stack, setStack] = useState([]);
  const open = (r) => { if (['voice', 'draft', 'drafts', 'stattable'].includes(r.view) && s.readOnly) { s.say('👀 预览模式：登录后才能登记'); return; } setStack((x) => [...x, r]); };
  const back = () => setStack((x) => x.slice(0, -1));
  const [p1, p2] = PALETTES[s.data.settings.theme] || PALETTES.mint;
  const dark = s.data.settings.dark === 2 || (s.data.settings.dark === 0 && window.matchMedia('(prefers-color-scheme: dark)').matches) || s.data.settings.theme === 'neon';
  useEffect(() => { document.documentElement.dataset.theme = dark ? 'dark' : 'light'; document.documentElement.style.setProperty('--g1', p1); document.documentElement.style.setProperty('--g2', p2); document.querySelector('meta[name=theme-color]')?.setAttribute('content', p1); }, [dark, p1, p2]);
  const top = stack[stack.length - 1];
  let body;
  if (top?.view === 'assign') body = html`<${AssignView} s=${s} id=${top.id} back=${back} open=${open} />`;
  else if (top?.view === 'voice') body = html`<${VoiceView} s=${s} id=${top.id} back=${back} />`;
  else if (top?.view === 'draft') body = html`<${DraftView} s=${s} id=${top.id} back=${back} open=${open} />`;
  else if (top?.view === 'drafts') body = html`<${DraftList} s=${s} back=${back} open=${open} />`;
  else if (top?.view === 'stattable') body = html`<${StatTable} s=${s} draftId=${top.draftId} back=${back} open=${open} replace=${(r) => setStack((x) => [...x.slice(0, -1), r])} />`;
  // 预览模式不显示名单页（热力图照常显示）
  const curTab = s.readOnly && tab === 'roster' ? 'home' : tab;
  const tabs = [['home', '🏠', '首页'], ...(s.readOnly ? [] : [['roster', '👥', '名单']]), ['heat', '🔥', '热力图'], ['scores', '🏆', '积分'], ['settings', '⚙️', '设置']];
  if (!top) body = { home: html`<${Home} s=${s} open=${open} />`, roster: html`<${Roster} s=${s} />`, heat: html`<${Heatmap} s=${s} open=${open} />`, scores: html`<${Scores} s=${s} />`, settings: html`<${Settings} s=${s} />` }[curTab];
  return html`<div class="app">
    ${s.readOnly && html`<div class="ro-banner" onClick=${() => { setStack([]); setTab('settings'); }}>👀 预览模式 · 只读　<b>登录后可修改 ›</b></div>`}
    <main>${body}</main>
    ${!top && html`<nav class="tabs">${tabs.map(([k, i, l]) => html`<button class=${curTab === k ? 'on' : ''} onClick=${() => setTab(k)}><span>${i}</span>${l}</button>`)}</nav>`}
    ${s.sync.email && s.sync.busy && html`<div class="sync-pill">☁️ 同步中…</div>`}
    ${s.toast && html`<div class="toast">${s.toast.msg}${s.toast.undoable && html`<button onClick=${s.doUndo}>撤销</button>`}</div>`}
    <${Conflict} s=${s} />
  </div>`;
}

render(html`<${App} />`, document.getElementById('app'));
if ('serviceWorker' in navigator) navigator.serviceWorker.register('./sw.js').catch(() => {});
