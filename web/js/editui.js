// 老师登录后的修改界面（与安卓同样的操作）：弹出面板、表单、多选操作栏、云端同步卡片、计分规则。
// 页面骨架组件（Group、Cell……）由 viewer.js 通过 setKit 传进来，保持与查看界面同一套样式。
import { h } from '../vendor/preact.module.js';
import { useState, useEffect, useRef } from '../vendor/hooks.module.js';
import htm from '../vendor/htm.module.js';
import { STATUS, fmtNum, signed, parseRoster, escalationModeLabel, escalationSeq } from './model.js';
import { api, session, meta } from './cloud.js';

const html = htm.bind(h);
let K = {};
export const setKit = (k) => { K = k; };

const num = (v) => { const x = Number(String(v).trim().replace('，', '.').replace(/^＋/, '+').replace(/^－/, '-')); return String(v).trim() !== '' && Number.isFinite(x) ? x : null; };
const dayInput = (t) => { const d = new Date(t); return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`; };
const fromDayInput = (v, keepTime) => { if (!v) return keepTime; const [y, m, d] = v.split('-').map(Number); const t = new Date(keepTime); return new Date(y, m - 1, d, t.getHours(), t.getMinutes(), t.getSeconds()).getTime(); };
const fmtSync = (t) => { if (!t) return ''; const d = new Date(t); return `${d.getMonth() + 1}月${d.getDate()}日 ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`; };

/** 长按（触屏）或右键（电脑）：用来打开同学的操作面板 */
export function useLongPress(onLong, onTap, ms = 480) {
  const t = useRef(0); const fired = useRef(false); const start = useRef(null);
  const clear = () => { clearTimeout(t.current); t.current = 0; };
  return {
    onPointerDown: (e) => { if (e.button > 0) return; fired.current = false; start.current = [e.clientX, e.clientY]; clear(); t.current = setTimeout(() => { fired.current = true; t.current = 0; onLong(); }, ms); },
    onPointerMove: (e) => { if (start.current && Math.hypot(e.clientX - start.current[0], e.clientY - start.current[1]) > 10) clear(); },
    onPointerUp: clear, onPointerCancel: clear, onPointerLeave: clear,
    onContextMenu: (e) => { e.preventDefault(); clear(); fired.current = true; onLong(); },
    onClick: (e) => { if (fired.current) { fired.current = false; e.preventDefault(); return; } onTap?.(e); },
  };
}

/** 底部弹出面板 */
export function Sheet({ title, onClose, children, foot }) {
  useEffect(() => { const f = (e) => { if (e.key === 'Escape') onClose(); }; window.addEventListener('keydown', f); return () => window.removeEventListener('keydown', f); }, []);
  return html`<div class="sheet-bg" onClick=${(e) => { if (e.target === e.currentTarget) onClose(); }}>
    <div class="sheet ed-sheet" role="dialog" aria-modal="true" aria-label=${title}>
      <div class="sheet-h"><b>${title}</b><button class="guide-skip" onClick=${onClose}>关闭</button></div>
      <div class="ed-body">${children}</div>
      ${foot && html`<div class="sheet-btns">${foot}</div>`}
    </div>
  </div>`;
}
const Field = ({ label, children, hint }) => html`<label class="ed-field"><span>${label}</span>${children}${hint && html`<small class="muted">${hint}</small>`}</label>`;
const Chips = ({ items, value, onPick }) => html`<div class="chips in wrap">${items.map((x) => html`<button type="button" class=${'chip' + (value === x ? ' on' : '')} onClick=${() => onPick(x)}>${x}</button>`)}</div>`;
export const Confirm = ({ title, text, ok = '确定', danger, onOk, onClose }) => html`<${Sheet} title=${title} onClose=${onClose}
  foot=${html`<button class="btn2" onClick=${onClose}>取消</button><button class=${danger ? 'btn1 danger' : 'btn1'} onClick=${() => { onOk(); onClose(); }}>${ok}</button>`}><p class="ed-text">${text}</p></${Sheet}>`;

/** 撤销提示条 */
export const Toast = ({ toast, onUndo }) => toast && html`<div class="ed-toast" role="status"><span>${toast.msg}</span>${toast.undoable && html`<button onClick=${onUndo}>撤销</button>`}</div>`;

// ---------- 作业 ----------
export function AssignDialog({ initial, subjects, onSave, onDelete, onClose }) {
  const [f, setF] = useState(() => ({ title: initial?.title || '', subject: initial?.subject || '', at: initial?.createdAt || Date.now() }));
  const ok = f.title.trim();
  return html`<${Sheet} title=${initial ? '修改作业' : '新建作业'} onClose=${onClose}
    foot=${html`${onDelete && html`<button class="btn2 danger-t" onClick=${onDelete}>删除</button>`}<button class="btn1" disabled=${!ok} onClick=${() => { onSave({ title: f.title.trim(), subject: f.subject.trim(), createdAt: f.at }); onClose(); }}>${initial ? '保存' : '新建'}</button>`}>
    <${Field} label="作业名称"><input autoFocus placeholder="如：练习册 P12" value=${f.title} onInput=${(e) => setF({ ...f, title: e.target.value })} /></${Field}>
    <${Field} label="科目"><input placeholder="可不填" value=${f.subject} onInput=${(e) => setF({ ...f, subject: e.target.value })} /></${Field}>
    <${Chips} items=${subjects} value=${f.subject} onPick=${(x) => setF({ ...f, subject: f.subject === x ? '' : x })} />
    <${Field} label="日期"><input type="date" value=${dayInput(f.at)} onInput=${(e) => setF({ ...f, at: fromDayInput(e.target.value, f.at) })} /></${Field}>
  </${Sheet}>`;
}

const ST_KEYS = ['SUBMITTED', 'EXCELLENT', 'LATE', 'MISSING', 'LEAVE', 'NONE'];
/** 同学的登记面板：状态、复审不合格、抄袭、评语（与安卓长按同学一样） */
export function StudentSheet({ c, a, s, cur, line, onAct, onProfile, onClose }) {
  const [plag, setPlag] = useState(false);
  const [prov, setProv] = useState(() => (a.plagiarism?.[s.id] || []).filter((x) => x !== '?'));
  const [q, setQ] = useState('');
  const [cm, setCm] = useState(a.comments?.[s.id] || '');
  const done = (fn) => { fn(); onClose(); };
  if (plag) {
    const others = c.students.filter((x) => x.id !== s.id && (!q || x.name.includes(q) || x.no.includes(q)));
    return html`<${Sheet} title=${`${s.name} 抄了谁的？`} onClose=${onClose}
      foot=${html`<button class="btn2" onClick=${() => setPlag(false)}>返回</button><button class="btn1" onClick=${() => done(() => onAct('plag', prov))}>${prov.length ? `记为抄袭（${prov.length} 人）` : '记为抄袭（提供者不明）'}</button>`}>
      <p class="ed-text muted">抄袭者和提供者都记一次未交，扣分翻倍，累犯逐次加重。不知道抄了谁的，可以一个都不选。</p>
      <input class="search" type="search" placeholder="搜索姓名、学号" value=${q} onInput=${(e) => setQ(e.target.value)} />
      <div class="chips in wrap">${others.map((x) => html`<button class=${'chip' + (prov.includes(x.id) ? ' on' : '')} onClick=${() => setProv(prov.includes(x.id) ? prov.filter((y) => y !== x.id) : [...prov, x.id])}>${x.name}</button>`)}</div>
    </${Sheet}>`;
  }
  return html`<${Sheet} title=${s.name} onClose=${onClose}>
    <div class="muted ed-text">${s.no}${s.group ? ' · ' + s.group : ''} · 当前：${cur}${line ? '（' + line + '）' : ''}</div>
    <div class="ed-st">${ST_KEYS.map((k) => html`<button style=${{ '--c': STATUS[k].color }} class=${a.statuses[s.id] === k || (k === 'NONE' && !a.statuses[s.id]) ? 'on' : ''} onClick=${() => done(() => onAct('status', k))}>${STATUS[k].label}</button>`)}</div>
    <div class="ed-st two">
      <button style=${{ '--c': '#b0306f' }} onClick=${() => done(() => onAct('review'))}>复审不合格</button>
      <button style=${{ '--c': '#d9480f' }} onClick=${() => setPlag(true)}>抄袭…</button>
      ${line && html`<button style=${{ '--c': '#8e8e93' }} onClick=${() => done(() => onAct('unplag'))}>取消抄袭</button>`}
    </div>
    <${Field} label="评语"><textarea rows="2" placeholder="写给这位同学的评语（可不填）" value=${cm} onInput=${(e) => setCm(e.target.value)} /></${Field}>
    <div class="sheet-btns"><button class="btn2" onClick=${() => done(onProfile)}>查看档案</button><button class="btn1" onClick=${() => done(() => onAct('comment', cm))}>保存评语</button></div>
  </${Sheet}>`;
}

/** 多选后的操作栏 */
export function SelectBar({ n, total, onAll, onClear, onStatus, onReview, onScore, onDone, extra }) {
  return html`<div class="ed-selbar">
    <div class="ed-selbar-top"><b>已选 ${n} 人</b><span><button onClick=${n === total ? onClear : onAll}>${n === total ? '全不选' : '全选'}</button><button onClick=${onDone}>完成</button></span></div>
    <div class="ed-st">${onStatus && ST_KEYS.map((k) => html`<button disabled=${!n} style=${{ '--c': STATUS[k].color }} onClick=${() => onStatus(k)}>${STATUS[k].label}</button>`)}
      ${onReview && html`<button disabled=${!n} style=${{ '--c': '#b0306f' }} onClick=${onReview}>复审不合格</button>`}
      ${onScore && html`<button disabled=${!n} style=${{ '--c': 'var(--accent)' }} onClick=${onScore}>加减分</button>`}${extra}</div>
  </div>`;
}

// ---------- 学生 ----------
export function StudentEdit({ c, s, onSave, onDelete, onClose }) {
  const [f, setF] = useState(() => ({ no: s?.no || '', name: s?.name || '', group: s?.group || '', gender: s?.gender || '', note: s?.note || '', longLeave: !!s?.longLeave,
    rep: !!s && (c.reps || []).includes(s.id), leader: !!s && !!s.group && c.groupLeaders?.[s.group] === s.id }));
  const groups = [...new Set(c.students.map((x) => x.group).filter(Boolean))].sort((a, b) => a.localeCompare(b, 'zh', { numeric: true }));
  const dup = f.no.trim() && c.students.some((x) => x.no === f.no.trim() && x.id !== s?.id);
  const ok = f.name.trim() && !dup;
  const set = (k) => (e) => setF({ ...f, [k]: e.target.value });
  return html`<${Sheet} title=${s ? '修改同学' : '添加同学'} onClose=${onClose}
    foot=${html`${onDelete && html`<button class="btn2 danger-t" onClick=${onDelete}>删除</button>`}<button class="btn1" disabled=${!ok} onClick=${() => { onSave({ ...(s || {}), no: f.no.trim(), name: f.name.trim(), group: f.group.trim(), gender: f.gender, note: f.note.trim(), longLeave: f.longLeave }, { rep: f.rep, leader: f.leader && !!f.group.trim() }); onClose(); }}>保存</button>`}>
    <${Field} label="姓名"><input autoFocus value=${f.name} onInput=${set('name')} /></${Field}>
    <${Field} label="学号" hint=${dup ? '这个学号已经有人用了' : ''}><input value=${f.no} onInput=${set('no')} inputmode="numeric" /></${Field}>
    <${Field} label="小组"><input value=${f.group} onInput=${set('group')} placeholder="可不填" /></${Field}>
    ${groups.length > 0 && html`<${Chips} items=${groups} value=${f.group} onPick=${(x) => setF({ ...f, group: f.group === x ? '' : x })} />`}
    <div class="ed-field"><span>性别</span><${K.Seg} items=${[['', '不填'], ['男', '男'], ['女', '女']]} value=${f.gender} onChange=${(v) => setF({ ...f, gender: v })} /></div>
    <${Field} label="备注"><input value=${f.note} onInput=${set('note')} placeholder="可不填" /></${Field}>
    <div class="ed-sw"><span>长期请假<small class="muted">之后新建的作业自动记为请假，不扣分</small></span><${K.Switch} label="长期请假" on=${f.longLeave} onChange=${(v) => setF({ ...f, longLeave: v })} /></div>
    <div class="ed-sw"><span>课代表<small class="muted">每次结算加分，可以同时是组长</small></span><${K.Switch} label="课代表" on=${f.rep} onChange=${(v) => setF({ ...f, rep: v })} /></div>
    <div class="ed-sw"><span>组长${f.group.trim() ? `（${f.group.trim()}）` : ''}<small class="muted">${f.group.trim() ? '按小组人均扣分计组长奖惩' : '先填小组'}</small></span><${K.Switch} label="组长" on=${f.leader && !!f.group.trim()} onChange=${(v) => setF({ ...f, leader: v })} /></div>
  </${Sheet}>`;
}

export function RosterImport({ onSave, onClose }) {
  const [text, setText] = useState(''); const [replace, setReplace] = useState(false);
  let parsed = null; let err = '';
  try { parsed = text.trim() ? parseRoster(text) : null; } catch { err = '格式不对：支持 JSON、CSV，或每行“学号 姓名 小组”'; }
  const n = parsed?.students.length || 0;
  return html`<${Sheet} title="导入名单" onClose=${onClose} foot=${html`<button class="btn1" disabled=${!n} onClick=${() => { onSave(parsed.students, replace); onClose(); }}>导入 ${n} 人</button>`}>
    <p class="ed-text muted">直接粘贴 Excel 的两三列（学号、姓名、小组），或 CSV / JSON 名单。</p>
    <textarea rows="7" placeholder=${'01 张三 第1组\n02 李四 第1组'} value=${text} onInput=${(e) => setText(e.target.value)}></textarea>
    ${err && html`<div class="acct-err">${err}</div>`}
    ${n > 0 && html`<div class="muted ed-text">识别到 ${n} 人：${parsed.students.slice(0, 8).map((s) => `${s.no} ${s.name}`).join('、')}${n > 8 ? '…' : ''}</div>`}
    <div class="ed-field"><span>导入方式</span><${K.Seg} items=${[[false, '按学号合并'], [true, '替换整个名单']]} value=${replace} onChange=${setReplace} /></div>
    ${replace && html`<div class="acct-err">替换会清空本班的作业、积分、考试记录</div>`}
  </${Sheet}>`;
}

// ---------- 积分 ----------
export function ScoreDialog({ title, reasons, onSave, onClose }) {
  const [sign, setSign] = useState(1); const [v, setV] = useState('1'); const [reason, setReason] = useState('');
  const x = num(v); const d = x == null ? null : sign * Math.abs(x);
  return html`<${Sheet} title=${title} onClose=${onClose} foot=${html`<button class="btn1" disabled=${!d} onClick=${() => { onSave(d, reason.trim()); onClose(); }}>${d ? `确定 ${signed(d)}` : '确定'}</button>`}>
    <${K.Seg} items=${[[1, '加分'], [-1, '扣分']]} value=${sign} onChange=${setSign} />
    <div class="chips in wrap" style=${{ marginTop: '10px' }}>${[1, 2, 3, 5, 10].map((n) => html`<button class=${'chip' + (x === n ? ' on' : '')} onClick=${() => setV(String(n))}>${n}</button>`)}</div>
    <${Field} label="分值"><input type="number" inputmode="decimal" min="0" step="0.5" value=${v} onInput=${(e) => setV(e.target.value)} /></${Field}>
    <${Field} label="原因"><input placeholder="可不填" value=${reason} onInput=${(e) => setReason(e.target.value)} /></${Field}>
    <${Chips} items=${reasons} value=${reason} onPick=${(r) => setReason(reason === r ? '' : r)} />
  </${Sheet}>`;
}

export function RechargeDialog({ count, initial, onSave, onClose }) {
  const [t, setT] = useState(String(initial)); const [note, setNote] = useState(''); const [when, setWhen] = useState('now');
  const x = num(t);
  return html`<${Sheet} title="积分重置" onClose=${onClose} foot=${html`<button class="btn1" disabled=${x == null} onClick=${() => { onSave(x, note.trim(), when === 'today' ? new Date().setHours(0, 0, 0, 0) : null); onClose(); }}>重置 ${count} 人</button>`}>
    <p class="ed-text muted">只重置积分并留下记录：此前的加减分、作业状态、未交次数全部保留，可以随时撤销。</p>
    <${Field} label="重置为"><input type="number" inputmode="decimal" value=${t} onInput=${(e) => setT(e.target.value)} /></${Field}>
    <${Field} label="备注"><input placeholder="如：期中重新计分" value=${note} onInput=${(e) => setNote(e.target.value)} /></${Field}>
    <div class="ed-field"><span>从什么时候起重新计分</span><${K.Seg} items=${[['now', '立即'], ['today', '从今天 0 点起']]} value=${when} onChange=${setWhen} /></div>
  </${Sheet}>`;
}

// ---------- 考试 ----------
export function ExamDialog({ initial, subjects, onSave, onDelete, onClose }) {
  const [f, setF] = useState(() => ({ title: initial?.title || '', subject: initial?.subject || '', full: String(initial?.fullScore ?? 100), at: initial?.date || Date.now(), note: initial?.note || '', auto: initial ? initial.autoScore !== false : true }));
  const full = num(f.full);
  const ok = f.title.trim() && full > 0;
  return html`<${Sheet} title=${initial ? '修改考试' : '新建考试'} onClose=${onClose}
    foot=${html`${onDelete && html`<button class="btn2 danger-t" onClick=${onDelete}>删除</button>`}<button class="btn1" disabled=${!ok} onClick=${() => { onSave({ ...(initial || {}), title: f.title.trim(), subject: f.subject.trim(), fullScore: full, date: f.at, note: f.note.trim(), autoScore: f.auto }); onClose(); }}>${initial ? '保存' : '新建'}</button>`}>
    <${Field} label="考试名称"><input autoFocus placeholder="如：第一单元测验" value=${f.title} onInput=${(e) => setF({ ...f, title: e.target.value })} /></${Field}>
    <${Field} label="科目"><input placeholder="可不填" value=${f.subject} onInput=${(e) => setF({ ...f, subject: e.target.value })} /></${Field}>
    <${Chips} items=${subjects} value=${f.subject} onPick=${(x) => setF({ ...f, subject: f.subject === x ? '' : x })} />
    <${Field} label="满分"><input type="number" inputmode="decimal" value=${f.full} onInput=${(e) => setF({ ...f, full: e.target.value })} /></${Field}>
    <${Field} label="日期"><input type="date" value=${dayInput(f.at)} onInput=${(e) => setF({ ...f, at: fromDayInput(e.target.value, f.at) })} /></${Field}>
    <${Field} label="备注"><input placeholder="可不填" value=${f.note} onInput=${(e) => setF({ ...f, note: e.target.value })} /></${Field}>
    <div class="ed-sw"><span>按得分率自动加减积分<small class="muted">分档在 设置 → 考试计分</small></span><${K.Switch} label="自动加减积分" on=${f.auto} onChange=${(v) => setF({ ...f, auto: v })} /></div>
  </${Sheet}>`;
}

/** 成绩录入：每位同学一行，输入分数（回车跳到下一行），或记为缺考 */
export function ExamScores({ c, e, sorted, onScore }) {
  const refs = useRef([]);
  return html`<${K.Group} title=${`录入成绩 · 满分 ${fmtNum(e.fullScore || 100)}`}>${sorted.map((s, i) => {
    const ab = (e.absent || []).includes(s.id); const v = e.scores?.[s.id];
    const commit = (el) => { const x = num(el.value); if (el.value.trim() === '') { if (v != null) onScore(s.id, null, false); return; } if (x == null || x < 0) { el.value = v ?? ''; return; } if (x !== v) onScore(s.id, x, false); };
    return html`<div class="cell ed-exam" key=${s.id}>
      <div class="cell-main"><div class="cell-title">${s.name}</div><div class="cell-sub">${s.no}</div></div>
      ${ab ? html`<span class="pill" style=${{ color: '#8e8e93', background: '#8e8e931f' }}>缺考</span>`
        : html`<input type="number" inputmode="decimal" aria-label=${`${s.name} 的成绩`} ref=${(el) => { refs.current[i] = el; }} value=${v ?? ''} placeholder="—"
          onBlur=${(ev) => commit(ev.target)} onKeyDown=${(ev) => { if (ev.key === 'Enter') { commit(ev.target); refs.current[i + 1]?.focus(); } }} />`}
      <button class="chip" onClick=${() => onScore(s.id, null, !ab)}>${ab ? '取消缺考' : '缺考'}</button>
    </div>`;
  })}</${K.Group}>`;
}

// ---------- 班级 ----------
const EMOJIS = ['🎒', '📚', '✏️', '🧪', '🌏', '🎨', '⚽️', '🎵', '🦊', '🐼', '🚀', '🌟'];
export function ClassDialog({ initial, onSave, onDelete, onClose }) {
  const [name, setName] = useState(initial?.name || ''); const [emoji, setEmoji] = useState(initial?.emoji || '🎒');
  return html`<${Sheet} title=${initial ? '修改班级' : '新建班级'} onClose=${onClose}
    foot=${html`${onDelete && html`<button class="btn2 danger-t" onClick=${onDelete}>删除班级</button>`}<button class="btn1" disabled=${!name.trim()} onClick=${() => { onSave(name.trim(), emoji); onClose(); }}>保存</button>`}>
    <${Field} label="班级名称"><input autoFocus placeholder="如：高一(3)班" value=${name} onInput=${(e) => setName(e.target.value)} /></${Field}>
    <div class="chips in wrap">${EMOJIS.map((x) => html`<button class=${'chip' + (emoji === x ? ' on' : '')} onClick=${() => setEmoji(x)}>${x}</button>`)}</div>
  </${Sheet}>`;
}

// ---------- 云端同步（老师） ----------
export function CloudCard({ store, onHistory }) {
  const { sync } = store; const m = meta(); const s = session();
  return html`<${K.Group} title="云端同步">
    <${K.Cell} title=${s?.email || ''} sub=${`云端版本 v${m.baseVersion || 0}${m.lastSyncAt ? ' · 上次同步 ' + fmtSync(m.lastSyncAt) : ''}${sync.msg ? ' · ' + sync.msg : ''}`} cls=${sync.error ? 'ed-err' : ''} />
    <${K.Cell} title="自动同步" sub="改动后几秒自动上传；回到页面时检查其它设备的更新" right=${html`<${K.Switch} label="自动同步" on=${m.auto !== false} onChange=${store.setAuto} />`} />
    <${K.Cell} title="允许未登录预览" sub="打开后，任何人不登录也能只读查看你的班级数据；关闭即恢复私密" right=${html`<${K.Switch} label="允许未登录预览" on=${sync.publicRead !== false} onChange=${store.setPublic} />`} />
    <${K.Cell} title=${sync.busy ? '同步中…' : '立即同步'} onClick=${sync.busy ? null : () => store.runSync()} />
    <${K.Cell} title="历史版本" sub="云端保留最近 50 份 + 90 天内每天一份，可一键恢复" onClick=${onHistory} />
  </${K.Group}>`;
}
export function HistorySheet({ onRestore, onClose }) {
  const [list, setList] = useState(null); const [err, setErr] = useState(''); const [pick, setPick] = useState(null); const [busy, setBusy] = useState(false);
  useEffect(() => { api.history().then(setList, (e) => setErr(e.message || '读取失败')); }, []);
  if (pick) return html`<${Confirm} title="恢复这个版本？" text=${`把数据恢复成 v${pick.version}（${fmtSync(Date.parse(pick.saved_at))}，${pick.device || '未知设备'}）。现在的数据会先保存在历史里，恢复后也可以撤销。`} ok=${busy ? '恢复中…' : '恢复'}
    onOk=${async () => { setBusy(true); try { onRestore(await api.historyData(pick.id), pick); } catch (e) { setErr(e.message); } setBusy(false); }} onClose=${() => setPick(null)} />`;
  return html`<${Sheet} title="历史版本" onClose=${onClose}>
    ${err && html`<div class="acct-err">${err}</div>`}
    ${!list && !err && html`<div class="muted ed-text">正在读取…</div>`}
    ${list && !list.length && html`<div class="muted ed-text">还没有历史版本（云端数据被覆盖前自动保存）</div>`}
    ${list && list.map((x) => html`<button class="cell tap ed-row" key=${x.id} onClick=${() => setPick(x)}><div class="cell-main"><div class="cell-title">v${x.version} · ${fmtSync(Date.parse(x.saved_at))}</div><div class="cell-sub">${x.device || '未知设备'}</div></div></button>`)}
  </${Sheet}>`;
}
export function ConflictSheet({ onPick }) {
  return html`<div class="sheet-bg"><div class="sheet ed-sheet" role="dialog" aria-modal="true">
    <div class="sheet-h"><b>本机和云端都有改动</b></div>
    <p class="ed-text muted">可能在别的设备（安卓 / iPad / 另一台电脑）上也改过。请选择怎么处理：</p>
    <div class="ed-col"><button class="btn1" onClick=${() => onPick('merge')}>合并两边（推荐）</button><button class="btn2" onClick=${() => onPick('cloud')}>使用云端的数据</button><button class="btn2 danger-t" onClick=${() => onPick('local')}>用本机覆盖云端</button></div>
  </div></div>`;
}

// ---------- 计分规则（老师；改后历史积分自动重算） ----------
function NumRow({ title, sub, value, onSave, min, step = 1 }) {
  // 不受控输入框：显示的值以输入框为准（key 随规则值变化时重建）；提交时读输入框当前的值，刚打开页面就输入也不会被改回
  const commit = (e) => { const x = num(e.target.value); if (x == null || (min != null && x < min)) { e.target.value = String(value); return; } if (x !== value) onSave(x); };
  return html`<div class="cell ed-num"><div class="cell-main"><div class="cell-title">${title}</div>${sub && html`<div class="cell-sub">${sub}</div>`}</div>
    <input key=${String(value)} type="number" inputmode="decimal" step=${step} aria-label=${title} defaultValue=${String(value)} onBlur=${commit} onKeyDown=${(e) => { if (e.key === 'Enter') e.target.blur(); }} /></div>`;
}
const SwRow = ({ title, sub, on, onChange }) => html`<${K.Cell} title=${title} sub=${sub} right=${html`<${K.Switch} label=${title} on=${on} onChange=${onChange} />`} />`;
export function RulesEditor({ rules, onRules }) {
  const set = (patch) => onRules({ ...rules, ...patch });
  const sub = (k) => (patch) => onRules({ ...rules, [k]: { ...rules[k], ...patch } });
  const e = rules.escalation; const sb = rules.streakBonus; const rv = rules.review; const pg = rules.plagiarism; const ex = rules.exam || { enabled: true };
  return html`<${K.Group} title="作业计分（改后历史积分自动重算）">
    <${NumRow} title="初始分" value=${rules.initial} onSave=${(x) => set({ initial: x })} />
    <${NumRow} title="已交" value=${rules.submitted} onSave=${(x) => set({ submitted: x })} />
    <${NumRow} title="优秀" value=${rules.excellent} onSave=${(x) => set({ excellent: x })} />
    <${NumRow} title="迟交" value=${rules.late} onSave=${(x) => set({ late: x })} />
    ${!e.enabled && html`<${NumRow} title="未交" value=${rules.missing} onSave=${(x) => set({ missing: x })} />`}
    <${NumRow} title="请假" value=${rules.leave} onSave=${(x) => set({ leave: x })} />
    <${SwRow} title="连续未交自动扣分" sub=${e.enabled ? `${escalationModeLabel(e.mode)}：${escalationSeq(e.base, e.mode === 1 ? 1 : 2)}；单次最多 ${e.cap > 0 ? fmtNum(e.cap) : '不限'}` : '已关闭：每次未交固定扣分'} on=${e.enabled} onChange=${(v) => sub('escalation')({ enabled: v })} />
    ${e.enabled && html`<div class="cell-pad"><${K.Seg} items=${[[1, '① 正常加倍'], [2, '② 第 2 次起加倍'], [3, '③ 分段倍率']]} value=${e.mode} onChange=${(v) => sub('escalation')({ mode: v })} /></div>
      <${NumRow} title="底数" sub="第 1 次扣的分" value=${e.base} min=${0} step=${0.5} onSave=${(x) => sub('escalation')({ base: x })} />
      <${NumRow} title="单次最多扣" sub="0 为不限" value=${e.cap} min=${0} onSave=${(x) => sub('escalation')({ cap: x })} />
      <${SwRow} title="间断未交平衡" sub=${e.gapBalance ? `隔几次不交一次也逐次加重，最多按第 ${e.gapMax} 次算` : '只看连续未交'} on=${!!e.gapBalance} onChange=${(v) => sub('escalation')({ gapBalance: v })} />`}
    <${SwRow} title="连续交满后每次加分" sub=${`本轮连续按时交满 ${sb.after} 次后，之后每连续交一次 +${fmtNum(sb.bonus)}`} on=${sb.enabled} onChange=${(v) => sub('streakBonus')({ enabled: v })} />
    <${SwRow} title="积分取整" sub="正分向上、负分向下取整；单笔保留原值" on=${rules.roundPoints !== false} onChange=${(v) => set({ roundPoints: v })} />
    <${SwRow} title="作业复审" sub=${`复审不合格记一次未交，扣分 ×${fmtNum(rv.multiplier)}`} on=${rv.enabled} onChange=${(v) => sub('review')({ enabled: v })} />
    ${rv.enabled && html`<${NumRow} title="复审不合格扣分倍数" value=${rv.multiplier} min=${1} step=${0.5} onSave=${(x) => sub('review')({ multiplier: x })} />`}
    <${SwRow} title="抄袭处罚" sub=${`抄袭者和提供者都记未交，扣分 ×${fmtNum(pg.multiplier)}${pg.escalate ? '，累犯逐次加重' : ''}`} on=${pg.enabled} onChange=${(v) => sub('plagiarism')({ enabled: v })} />
    ${pg.enabled && html`<${NumRow} title="抄袭扣分倍数" value=${pg.multiplier} min=${1} step=${0.5} onSave=${(x) => sub('plagiarism')({ multiplier: x })} />`}
    <${SwRow} title="考试按得分率加减积分" sub="分数档与安卓一致（≥90% +2、80–90% +1、<60% −1 等）" on=${ex.enabled !== false} onChange=${(v) => onRules({ ...rules, exam: { ...ex, enabled: v } })} />
  </${K.Group}>`;
}
