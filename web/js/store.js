// 网页版的数据来源（与安卓一致）：
// - 未登录：老师公开的预览（只读）
// - 普通用户（凭邀请码注册）：所属老师的数据（只读）
// - 老师登录：本机可修改的数据，改动几秒后自动上传；回到页面 / 每分钟检查其它设备的更新；两边都改过时可选合并
import { useState, useEffect, useRef, useCallback } from '../vendor/hooks.module.js';
import { normalizeData, emptyData, applyAutoResets } from './model.js';
import { api, session, meta, setMeta, syncOnce, pushAs, merge, hashData, hooks } from './cloud.js';
import { refreshMe, isMember } from './trade.js';

const CACHE_KEY = 'hw.preview';
const DATA_KEY = 'hw.data';
const OWNER_KEY = 'hw.data.owner';
const ROLE_KEY = 'hw.role';
const ls = {
  get(k) { try { return localStorage.getItem(k); } catch { return null; } },
  set(k, v) { try { if (v == null) localStorage.removeItem(k); else localStorage.setItem(k, v); } catch { /* 隐私模式 / 空间不足 */ } },
};

/** 老师本机的数据：只给同一个账号用；换了账号就从空数据开始（避免把别人的数据传进自己的云端） */
function loadLocal(uid) {
  if (ls.get(OWNER_KEY) !== uid) { ls.set(DATA_KEY, null); ls.set(OWNER_KEY, uid); setMeta({ baseVersion: 0, syncedHash: '', lastSyncAt: 0, auto: true }); return emptyData(); }
  try { const t = ls.get(DATA_KEY); return t ? normalizeData(JSON.parse(t)) : emptyData(); } catch { return emptyData(); }
}
const cachedPreview = () => { try { const t = ls.get(CACHE_KEY); return t ? normalizeData(JSON.parse(t)) : null; } catch { return null; } };
const saveLocal = (d) => ls.set(DATA_KEY, JSON.stringify(d));
const cachedRole = (uid) => { try { return JSON.parse(ls.get(ROLE_KEY) || '{}')[uid] || null; } catch { return null; } };
const cacheRole = (uid, role) => { try { const m = JSON.parse(ls.get(ROLE_KEY) || '{}'); m[uid] = role; ls.set(ROLE_KEY, JSON.stringify(m)); } catch { /* 忽略 */ } };

export function useStore() {
  const [st, setSt] = useState(() => {
    try { const t = ls.get(CACHE_KEY); return { data: t ? normalizeData(JSON.parse(t)) : null, status: 'loading', mode: session() ? 'pending' : 'guest' }; } catch { return { data: null, status: 'loading', mode: 'pending' }; }
  });
  const [sync, setSync] = useState({ busy: false, msg: '', error: false, conflict: null, conflictVersion: 0, publicRead: null });
  const [toast, setToast] = useState(null);
  const dataRef = useRef(st.data); const modeRef = useRef(st.mode);
  const undo = useRef([]); const busy = useRef(false); const syncing = useRef(false); const syncTimer = useRef(0); const toastTimer = useRef(0);
  const set = (patch) => { if ('data' in patch) dataRef.current = patch.data; if ('mode' in patch) modeRef.current = patch.mode; setSt((s) => ({ ...s, ...patch })); };

  const say = useCallback((msg, undoable = false) => { clearTimeout(toastTimer.current); setToast({ msg, undoable }); toastTimer.current = setTimeout(() => setToast(null), undoable ? 5000 : 3000); }, []);

  // ---------- 老师：同步 ----------
  const runSync = useCallback(async (retry = 0) => {
    if (modeRef.current !== 'teacher' || !session() || syncing.current) return;
    syncing.current = true;
    setSync((s) => ({ ...s, busy: true, msg: '同步中…', error: false }));
    try {
      const r = await syncOnce(dataRef.current, (d) => { saveLocal(d); set({ data: d, status: 'ok' }); }, normalizeData);
      setSync((s) => ({ ...s, busy: false, msg: r.message, error: false, conflict: r.action === 'conflict' ? r.remote : null, conflictVersion: r.remoteVersion || s.conflictVersion, publicRead: r.publicRead ?? s.publicRead }));
      // 首次同步：默认开放“未登录预览”（与安卓、iPad 一致）
      const m = meta();
      if (!m.previewInit && r.action !== 'conflict' && (r.action === 'uploaded' || r.publicRead !== undefined)) {
        try { if (r.publicRead !== true) await api.setPublic(true); setMeta({ ...meta(), previewInit: true }); setSync((s) => ({ ...s, publicRead: true })); } catch { /* 忽略 */ }
      }
    } catch (e) {
      syncing.current = false;
      if (e.remoteVersion && retry < 2) { setTimeout(() => runSync(retry + 1), 400); return; }
      setSync((s) => ({ ...s, busy: false, msg: e.message || '同步失败', error: true }));
      return;
    }
    syncing.current = false;
  }, []);
  const scheduleSync = useCallback((ms = 3000) => {
    clearTimeout(syncTimer.current);
    if (modeRef.current === 'teacher' && meta().auto !== false) syncTimer.current = setTimeout(() => runSync(), ms);
  }, [runSync]);

  // ---------- 读取：按身份决定数据来源 ----------
  const enterTeacher = useCallback((uid) => {
    undo.current = [];
    let d = loadLocal(uid);
    const next = applyAutoResets(d);
    if (next !== d) { d = next; saveLocal(d); }
    set({ data: d, mode: 'teacher', status: 'ok' });
    runSync();
  }, [runSync]);

  const load = useCallback(async () => {
    if (busy.current) return; busy.current = true;
    try {
      const s = session();
      if (!s) {
        if (modeRef.current !== 'guest') { undo.current = []; set({ mode: 'guest', data: cachedPreview() }); }
      } else {
        const me = await refreshMe();
        const role = me ? (isMember(me) ? 'member' : 'teacher') : cachedRole(s.userId) || (session() ? 'teacher' : 'guest');
        if (me) cacheRole(s.userId, role);
        if (!session()) { set({ mode: 'guest' }); } else if (role === 'teacher') {
          if (modeRef.current !== 'teacher') enterTeacher(s.userId); else runSync();
          return;
        } else {
          if (modeRef.current !== 'member') { undo.current = []; set({ mode: 'member', data: modeRef.current === 'teacher' ? cachedPreview() : dataRef.current }); }
          set({ status: 'loading' });
          const row = me && me.status === 'active' && me.admin_id ? await api.pullOf(me.admin_id) : null;
          if (!row) { set({ status: me ? 'none' : 'error' }); return; }
          const d = normalizeData(row.data);
          ls.set(CACHE_KEY, JSON.stringify(d));
          set({ data: d, status: 'ok' });
          return;
        }
      }
      set({ status: 'loading' });
      const row = await api.pullPublic();
      if (!row) { set({ status: 'none' }); return; }
      const d = normalizeData(row.data);
      ls.set(CACHE_KEY, JSON.stringify(d));
      set({ data: d, status: 'ok' });
    } catch { set({ status: 'error' }); } finally { busy.current = false; }
  }, [enterTeacher, runSync]);

  useEffect(() => {
    // 退出登录前：老师先把本机改动传到云端
    hooks.beforeSignOut = async () => { if (modeRef.current === 'teacher' && session()) { clearTimeout(syncTimer.current); await runSync(); } };
    load();
    const t = setInterval(() => { if (!document.hidden) { if (modeRef.current === 'teacher') scheduleSync(0); else load(); } }, 60_000);
    const f = () => { if (!document.hidden) { if (modeRef.current === 'teacher') scheduleSync(300); else load(); } };
    document.addEventListener('visibilitychange', f);
    return () => { clearInterval(t); document.removeEventListener('visibilitychange', f); };
  }, []);

  // ---------- 老师：修改 ----------
  /** 修改数据：fn(旧数据) → 新数据。只有老师能改；预览和普通用户只读 */
  const update = useCallback((fn, { undoable = true, msg } = {}) => {
    if (modeRef.current !== 'teacher') { say(modeRef.current === 'member' ? '普通用户只能查看，不能修改' : '预览模式：登录后才能修改'); return false; }
    const old = dataRef.current; const next = fn(old);
    if (!next || next === old) return false;
    if (undoable) { undo.current.push(old); if (undo.current.length > 30) undo.current.shift(); }
    saveLocal(next); set({ data: next, status: 'ok' }); scheduleSync();
    if (msg) say(msg, undoable);
    return true;
  }, [scheduleSync, say]);
  const doUndo = useCallback(() => {
    const prev = undo.current.pop(); if (!prev || modeRef.current !== 'teacher') return;
    saveLocal(prev); set({ data: prev }); scheduleSync(); setToast(null);
  }, [scheduleSync]);
  /** 恢复整份数据（历史版本 / 备份），可撤销 */
  const replaceAll = useCallback((d, msg) => update(() => normalizeData(d), { msg }), [update]);

  const resolve = useCallback(async (choice) => {
    const remote = sync.conflict; const v = sync.conflictVersion; if (!remote) return;
    setSync((s) => ({ ...s, conflict: null, busy: true, msg: '处理中…' }));
    try {
      if (choice === 'cloud') {
        undo.current.push(dataRef.current); saveLocal(remote); set({ data: remote });
        setMeta({ ...meta(), baseVersion: v, syncedHash: await hashData(remote), lastSyncAt: Date.now() });
        setSync((s) => ({ ...s, busy: false, msg: '已使用云端数据' }));
      } else {
        const d = choice === 'merge' ? merge(dataRef.current, remote) : dataRef.current;
        if (choice === 'merge') { undo.current.push(dataRef.current); saveLocal(d); set({ data: d }); }
        await pushAs(d, v);
        setSync((s) => ({ ...s, busy: false, msg: choice === 'merge' ? '已合并并上传' : '已用本机覆盖云端' }));
      }
    } catch (e) { setSync((s) => ({ ...s, busy: false, msg: e.message, error: true })); if (e.remoteVersion) setTimeout(() => runSync(), 400); }
  }, [sync, runSync]);

  const setAuto = useCallback((on) => { setMeta({ ...meta(), auto: on }); setSync((s) => ({ ...s })); if (on) scheduleSync(300); }, [scheduleSync]);
  const setPublic = useCallback(async (on) => {
    try { await api.setPublic(on); setSync((s) => ({ ...s, publicRead: on })); say(on ? '已允许未登录预览' : '已关闭未登录预览'); } catch (e) { say(e.message || '设置失败'); }
  }, [say]);

  return { ...st, editable: st.mode === 'teacher', reload: load, update, doUndo, canUndo: () => undo.current.length > 0, replaceAll, say, toast, sync, runSync, resolve, setAuto, setPublic };
}
