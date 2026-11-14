// Supabase 云同步（与安卓版同一个项目、同一套表和冲突规则）
export const CLOUD = { url: 'https://beasxxexggnjcocdbtdl.supabase.co', key: 'sb_publishable_85hufMmFQvmLQt2Rz1Hcrw_S4t7BjAO' };

const SESSION_KEY = 'hw.cloud.session';
const META_KEY = 'hw.cloud.meta';
const load = (k, d) => { try { return JSON.parse(localStorage.getItem(k)) ?? d; } catch { return d; } };
const save = (k, v) => { try { v == null ? localStorage.removeItem(k) : localStorage.setItem(k, JSON.stringify(v)); } catch { /* 隐私模式 */ } };

export class ConflictError extends Error { constructor(v) { super(`云端已有更新的版本 v${v}`); this.remoteVersion = v; } }

function friendly(status, body) {
  const raw = (body && (body.error_description || body.msg || body.message || body.error)) || '';
  const l = String(raw).toLowerCase();
  if (l.includes('invalid login credentials')) return '邮箱或密码不对';
  if (l.includes('email not confirmed')) return '邮箱还没验证：请到邮箱里点确认链接后再登录';
  if (l.includes('already registered')) return '这个邮箱已经注册过了，直接登录即可';
  if (l.includes('password should be at least') || l.includes('weak_password')) return '密码太短或太简单（至少 6 位）';
  if (l.includes('same_password') || l.includes('should be different')) return '新密码不能和旧密码一样';
  if (l.includes('rate limit') || status === 429) return '操作太频繁，请稍后再试';
  if (l.includes('invalid email') || l.includes('unable to validate email')) return '邮箱格式不正确';
  return `出错了（${status}）：${raw || '未知错误'}`;
}

async function http(method, path, body, bearer) {
  let res;
  try {
    res = await fetch(CLOUD.url + path, {
      method,
      headers: { apikey: CLOUD.key, Accept: 'application/json', ...(body ? { 'Content-Type': 'application/json' } : {}), ...(bearer ? { Authorization: `Bearer ${bearer}` } : {}) },
      body: body ? JSON.stringify(body) : undefined,
    });
  } catch (e) {
    throw new Error('网络连接失败，请检查网络');
  }
  const text = await res.text();
  let json = null; try { json = text ? JSON.parse(text) : null; } catch { json = null; }
  return { status: res.status, ok: res.ok, json, text };
}

function toSession(j, email) {
  if (!j || !j.access_token) return null;
  return { accessToken: j.access_token, refreshToken: j.refresh_token, expiresAt: Date.now() + (j.expires_in || 3600) * 1000, userId: j.user?.id || '', email: j.user?.email || email };
}

export const session = () => load(SESSION_KEY, null);

async function token() {
  const s = session();
  if (!s) throw Object.assign(new Error('请先登录云端账号'), { status: 401 });
  if (s.expiresAt - Date.now() > 60000) return s.accessToken;
  const r = await http('POST', '/auth/v1/token?grant_type=refresh_token', { refresh_token: s.refreshToken });
  if (!r.ok) { if (r.status >= 400 && r.status < 500) save(SESSION_KEY, null); throw new Error('登录已过期，请重新登录'); }
  const ns = toSession(r.json, s.email);
  save(SESSION_KEY, ns);
  return ns.accessToken;
}

export const api = {
  async signIn(email, password) {
    const r = await http('POST', '/auth/v1/token?grant_type=password', { email: email.trim(), password });
    if (!r.ok) throw new Error(friendly(r.status, r.json));
    const s = toSession(r.json, email); save(SESSION_KEY, s); return s;
  },
  async signUp(email, password) {
    const r = await http('POST', '/auth/v1/signup', { email: email.trim(), password, data: { username: 'teacher', app: 'hwcounter' } });
    if (!r.ok) throw new Error(friendly(r.status, r.json));
    const s = toSession(r.json, email); if (s) save(SESSION_KEY, s); return s;
  },
  async recover(email) {
    const r = await http('POST', '/auth/v1/recover', { email: email.trim() });
    if (!r.ok) throw new Error(friendly(r.status, r.json));
  },
  /** 修改密码：先用旧密码验证身份，再提交新密码 */
  async changePassword(oldPw, newPw) {
    const s = session();
    if (!s) throw new Error('请先登录云端账号');
    const c = await http('POST', '/auth/v1/token?grant_type=password', { email: s.email, password: oldPw });
    if (!c.ok) throw new Error(c.status === 400 ? '旧密码不对' : friendly(c.status, c.json));
    const ns = toSession(c.json, s.email) || s; save(SESSION_KEY, ns);
    const r = await http('PUT', '/auth/v1/user', { password: newPw }, ns.accessToken);
    if (!r.ok) throw new Error(friendly(r.status, r.json));
  },
  async signOut() {
    const s = session();
    if (s) { try { await http('POST', '/auth/v1/logout', {}, s.accessToken); } catch { /* 忽略 */ } }
    save(SESSION_KEY, null); save(META_KEY, null);
  },
  async pull() {
    const uid = session()?.userId;
    const filter = uid ? `&user_id=eq.${encodeURIComponent(uid)}` : '';
    const r = await http('GET', `/rest/v1/hw_snapshots?select=data,version,updated_at,device,public_read${filter}&limit=1`, null, await token());
    if (!r.ok) throw new Error(friendly(r.status, r.json));
    const row = Array.isArray(r.json) ? r.json[0] : null;
    return row ? { data: row.data, version: row.version, updatedAt: row.updated_at, device: row.device, publicRead: !!row.public_read } : null;
  },
  /** 未登录预览：读取老师公开的那份数据（只读） */
  async pullPublic() {
    const r = await http('GET', '/rest/v1/hw_snapshots?select=data,version,updated_at&public_read=eq.true&order=updated_at.desc&limit=1');
    if (!r.ok) throw new Error(friendly(r.status, r.json));
    const row = Array.isArray(r.json) ? r.json[0] : null;
    return row ? { data: row.data, version: row.version, updatedAt: row.updated_at } : null;
  },
  /** 开关“允许未登录预览” */
  async setPublic(flag) {
    const uid = session()?.userId;
    const r = await fetch(`${CLOUD.url}/rest/v1/hw_snapshots?user_id=eq.${encodeURIComponent(uid)}`, {
      method: 'PATCH',
      headers: { apikey: CLOUD.key, Authorization: `Bearer ${await token()}`, 'Content-Type': 'application/json', Prefer: 'return=minimal' },
      body: JSON.stringify({ public_read: flag }),
    }).catch(() => null);
    if (!r || !r.ok) throw new Error('设置失败，请稍后再试（如果提示字段不存在，需要先在 Supabase 运行 002_public_preview.sql）');
  },
  async push(data, base, device) {
    const r = await http('POST', '/rest/v1/rpc/hw_push', { p_data: data, p_base_version: base, p_device: device }, await token());
    if (!r.ok) {
      const msg = r.json?.message || '';
      if (msg.startsWith('CONFLICT:')) throw new ConflictError(Number(msg.slice(9)) || -1);
      throw new Error(friendly(r.status, r.json));
    }
    const row = Array.isArray(r.json) ? r.json[0] : r.json;
    return row.version;
  },
  async history() {
    const r = await http('GET', '/rest/v1/hw_snapshot_history?select=id,version,device,saved_at&order=saved_at.desc&limit=30', null, await token());
    if (!r.ok) throw new Error(friendly(r.status, r.json));
    return r.json || [];
  },
  async historyData(id) {
    const r = await http('GET', `/rest/v1/hw_snapshot_history?select=data&id=eq.${encodeURIComponent(id)}`, null, await token());
    if (!r.ok) throw new Error(friendly(r.status, r.json));
    return r.json?.[0]?.data;
  },
};

// ---------- 同步逻辑 ----------
export const meta = () => load(META_KEY, { baseVersion: 0, syncedHash: '', lastSyncAt: 0, auto: true });
export const setMeta = (m) => save(META_KEY, m);

export const strip = (d) => ({ ...d, settings: { ...d.settings, ai: { ...d.settings.ai, apiKey: '' } } });
export const withLocalKey = (remote, local) => ({ ...remote, settings: { ...remote.settings, ai: { ...(remote.settings?.ai || {}), apiKey: local.settings.ai.apiKey } } });

export async function hashData(d) {
  const buf = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(JSON.stringify(strip(d))));
  return Array.from(new Uint8Array(buf), (b) => b.toString(16).padStart(2, '0')).join('').slice(0, 32);
}

export const DEVICE = (() => {
  const ua = navigator.userAgent;
  const ipad = /iPad/.test(ua) || (/Macintosh/.test(ua) && navigator.maxTouchPoints > 1);
  return `网页 · ${ipad ? 'iPad' : /iPhone/.test(ua) ? 'iPhone' : /Android/.test(ua) ? 'Android' : '电脑'}`;
})();

/** 合并：按 id 取并集；同一次作业逐个学生合并（本机优先） */
export function merge(local, remote) {
  const classes = new Map(remote.classes.map((c) => [c.id, c]));
  for (const l of local.classes) {
    const r = classes.get(l.id);
    if (!r) { classes.set(l.id, l); continue; }
    const students = new Map([...r.students, ...l.students].map((s) => [s.id, s]));
    const assigns = new Map(r.assignments.map((a) => [a.id, a]));
    for (const la of l.assignments) {
      const ra = assigns.get(la.id);
      assigns.set(la.id, ra ? { ...la, statuses: { ...ra.statuses, ...la.statuses }, comments: { ...ra.comments, ...la.comments }, countedBooks: la.countedBooks ?? ra.countedBooks } : la);
    }
    const scores = [...new Map([...r.scores, ...l.scores].map((e) => [e.id, e])).values()];
    classes.set(l.id, { ...l, students: [...students.values()], assignments: [...assigns.values()], scores, seating: l.seating || r.seating || null });
  }
  return { ...local, classes: [...classes.values()], currentClassId: local.currentClassId ?? remote.currentClassId };
}

/**
 * 一次同步。返回 {action, message, remote?, remoteVersion?}
 * action: none | uploaded | downloaded | conflict
 */
export async function syncOnce(local, applyRemote, normalize) {
  const m = meta();
  const localHash = await hashData(local);
  const localChanged = localHash !== m.syncedHash;
  const localEmpty = !local.classes.length;
  const remote = await api.pull();
  const pr = remote ? remote.publicRead : undefined;
  const upload = async (base) => {
    const v = await api.push(strip(local), base, DEVICE);
    setMeta({ ...m, baseVersion: v, syncedHash: localHash, lastSyncAt: Date.now() });
    return { action: 'uploaded', message: '已上传到云端', publicRead: pr };
  };
  if (!remote) return localEmpty ? { action: 'none', message: '云端还没有数据' } : upload(0);
  if (remote.version === m.baseVersion) {
    if (localChanged) return upload(remote.version);
    setMeta({ ...m, lastSyncAt: Date.now() });
    return { action: 'none', message: '已是最新', publicRead: pr };
  }
  const r = withLocalKey(normalize(remote.data), local);
  if (!localChanged || localEmpty) {
    applyRemote(r);
    setMeta({ ...m, baseVersion: remote.version, syncedHash: await hashData(r), lastSyncAt: Date.now() });
    return { action: 'downloaded', message: `已从云端更新（来自 ${remote.device || '其它设备'}）`, publicRead: pr };
  }
  return { action: 'conflict', message: '本机和云端都有改动，请选择怎么处理', remote: r, remoteVersion: remote.version, publicRead: pr };
}

export async function pushAs(data, base) {
  const v = await api.push(strip(data), base, DEVICE);
  setMeta({ ...meta(), baseVersion: v, syncedHash: await hashData(data), lastSyncAt: Date.now() });
  return v;
}
