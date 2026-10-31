// 统计表识别 + 统计名单 + 草稿。与安卓版、iPad 版同一套规则：
// - 统计表：老师手上的登记表（Excel / CSV / 粘贴 / 拍照），每行一个学生，后面若干列是 ✓ × 优 迟 假 之类的标记
// - 草稿：只保存在本机的作业登记，不进云端快照；上传时只把草稿里改动过的学生写进作业
import { newId, STATUS, STATUS_KEYS } from './model.js';
import { seatMatcher } from './seating.js';

// ---------------- 状态标记 → 状态 ----------------
const TOKENS = [
  ['EXCELLENT', ['优秀', '优', 'a+', 'a', '★', '☆', '⭐', '🌟', '好', '棒', '优+', '优-', 'a-']],
  ['LATE', ['迟交', '迟', '补交', '补', '晚交', '晚', '迟到', '后交']],
  ['LEAVE', ['请假', '病假', '事假', '假', '病', '免交', '免', '公假']],
  ['MISSING', ['未交', '没交', '缺交', '不交', '欠交', '未', '缺', '欠', '无', '没', '×', '✗', '✘', '✕', '╳', 'x', 'n', 'no', '否', '0', 'f', 'false']],
  ['SUBMITTED', ['已交', '交了', '已完成', '完成', '交', '有', '是', '对', '✓', '✔', '√', '☑', '✅', 'v', 'y', 'yes', 'ok', '1', 'true', '○', '〇', 'o', '良', '良好', '中', '合格', '及格', 'b', 'b+', 'b-', 'c', 'c+', 'c-', 'd']],
  ['NONE', ['', '-', '—', '–', '－', '/', '／', '\\', '空', '?', '？']],
];
const TOKEN_MAP = new Map();
for (const [k, list] of TOKENS) for (const t of list) TOKEN_MAP.set(t, k);
// 按长度排序，供 OCR 一行里“找出现过的标记”用（单个拉丁字母/数字太容易误伤，不参与扫描）
const SCAN = [...TOKEN_MAP.keys()].filter((t) => t && !/^[a-z0-9+\-]{1,2}$/.test(t) && !/^(true|false|yes|ok)$/.test(t)).sort((a, b) => b.length - a.length);

const half = (s) => String(s ?? '').replace(/[！-～]/g, (c) => String.fromCharCode(c.charCodeAt(0) - 0xfee0)).replace(/　/g, ' ');
/** 一个单元格 → 状态；认不出返回 null（空单元格返回 'NONE'） */
export function parseStatus(cell) {
  const t = half(cell).trim().toLowerCase().replace(/\s+/g, '');
  if (TOKEN_MAP.has(t)) return TOKEN_MAP.get(t);
  if (STATUS[t.toUpperCase()]) return t.toUpperCase(); // 本软件导出的英文枚举
  for (const k of STATUS_KEYS) if (STATUS[k].label === t) return k;
  // “交(优)”“✓迟”“已交-迟”：取最有信息量的那个
  for (const k of ['LEAVE', 'LATE', 'EXCELLENT', 'MISSING', 'SUBMITTED']) if (t.length <= 6 && SCAN.some((x) => TOKEN_MAP.get(x) === k && t.includes(x))) return k;
  return null;
}

// ---------------- 文本 → 表格 ----------------
/** 粘贴 / CSV / TSV → 二维数组（支持带引号的 CSV） */
export function splitTable(text) {
  const t = String(text).replace(/^﻿/, '').replace(/\r\n?/g, '\n');
  const lines = t.split('\n');
  const tab = lines.some((l) => l.includes('\t'));
  const comma = !tab && lines.some((l) => /[,，]/.test(l));
  const rows = [];
  for (const line of lines) {
    if (!line.trim()) continue;
    if (tab) { rows.push(line.split('\t')); continue; }
    if (comma) { rows.push(csvLine(line)); continue; }
    rows.push(line.trim().split(/[\s;；|]+/));
  }
  return rows.map((r) => r.map((c) => String(c).trim()));
}
function csvLine(line) {
  const out = []; let cur = ''; let q = false;
  for (let i = 0; i < line.length; i++) {
    const ch = line[i];
    if (q) { if (ch === '"' && line[i + 1] === '"') { cur += '"'; i++; } else if (ch === '"') q = false; else cur += ch; continue; }
    if (ch === '"') q = true; else if (ch === ',' || ch === '，') { out.push(cur); cur = ''; } else cur += ch;
  }
  out.push(cur);
  return out;
}

// ---------------- 表格识别 ----------------
/**
 * rows: 二维字符串数组；students: 名单
 * 返回 { columns: [{ index, header }], rows: [{ studentId, cells: [状态|null], raw }], unmatched: [原始行文本], missingStudents: [学生id] }
 * columns 是被判定为“状态列”的列；rows[i].cells[j] 对应 columns[j]
 */
export function recognizeTable(rows, students) {
  const m = seatMatcher(students);
  const width = Math.max(0, ...rows.map((r) => r.length));
  const exact = (cell) => {
    const c = String(cell || '').trim(); if (!c) return null;
    const s = students.find((x) => x.name === c) || students.find((x) => x.no && normNo(x.no) === normNo(c));
    return s || null;
  };
  // 1) 哪一列是学号/姓名：逐列统计“能直接对上名单”的格子数
  //    （要求对上的是不同的学生：一整列“1”会对上 1 号同学，但那是状态列）
  const idHits = Array.from({ length: width }, () => ({ n: 0, ids: new Set() }));
  rows.forEach((r) => r.forEach((cell, j) => { const s = exact(cell); if (s) { idHits[j].n++; idHits[j].ids.add(s.id); } }));
  const idCols = idHits.map((h, j) => [h, j]).filter(([h]) => h.ids.size >= Math.max(1, Math.ceil(rows.length * 0.25)) && h.ids.size >= h.n * 0.6).map(([, j]) => j);
  const han = (c) => /[一-鿿]/.test(c || '');
  // 2) 每行找学生：优先身份列精确匹配，再全行模糊
  const rowStu = rows.map((r) => {
    for (const j of idCols) { const s = exact(r[j]); if (s) return s; }
    for (const j of idCols) { const s = r[j] && m.one(r[j]); if (s) return s; }
    if (!idCols.length) { for (const cell of r) { const s = han(cell) ? exact(cell) || m.one(cell) : null; if (s) return s; } }
    const hit = m.all(r.filter((c, j) => (idCols.length ? idCols.includes(j) : han(c))).join(' '));
    return hit.length ? hit[0].s : null;
  });
  // 3) 状态列：除身份列外，能认出的标记占非空格子的 60% 以上
  const statCols = [];
  for (let j = 0; j < width; j++) {
    if (idCols.includes(j)) continue;
    let filled = 0; let ok = 0; let real = 0;
    rows.forEach((r, i) => { if (!rowStu[i]) return; const v = (r[j] || '').trim(); if (!v) return; filled++; const s = parseStatus(v); if (s) { ok++; if (s !== 'NONE') real++; } });
    if (real > 0 && ok >= filled * 0.6) statCols.push(j);
  }
  // 4) 表头：第一个没对上学生、且在状态列上有文字的行
  const headRow = rows.findIndex((r, i) => !rowStu[i] && statCols.some((j) => (r[j] || '').trim() && parseStatus(r[j]) === null));
  const columns = statCols.map((j, k) => ({ index: j, header: (headRow >= 0 && rows[headRow][j] ? rows[headRow][j].trim() : '') || `第${k + 1}列` }));
  const out = []; const unmatched = []; const seen = new Set();
  rows.forEach((r, i) => {
    const s = rowStu[i];
    if (!s) { if (i !== headRow && r.some((c) => c.trim())) unmatched.push(r.filter(Boolean).join(' ')); return; }
    if (seen.has(s.id)) return; // 同一个学生出现两次：以第一次为准
    seen.add(s.id);
    out.push({ studentId: s.id, cells: statCols.map((j) => parseStatus(r[j] || '') || 'NONE'), raw: r.filter(Boolean).join(' ') });
  });
  return { columns, rows: out, unmatched, missingStudents: students.filter((x) => !seen.has(x.id)).map((x) => x.id) };
}
const normNo = (x) => { const t = half(x).trim().toUpperCase(); return /^\d+$/.test(t) ? String(Number(t)) : t; };

/**
 * 拍照识别结果（OCR 文字行，从上到下）→ 同 recognizeTable 的结构。
 * 每行：先找出学生，再从剩下的文字里依次找状态标记（一行多个标记 = 多列）
 */
export function recognizeLines(lines, students) {
  const m = seatMatcher(students);
  const sorted = [...lines].sort((a, b) => (a.y ?? 0) - (b.y ?? 0));
  const out = []; const unmatched = []; const seen = new Set(); let width = 0;
  for (const ln of sorted) {
    const text = half(ln.text || '').trim(); if (!text) continue;
    const hits = m.all(text);
    if (!hits.length) { unmatched.push(text); continue; }
    // 一行里可能有好几个人（两栏排版的登记表）：每个人取到下一个人之前的文字
    hits.forEach((h, k) => {
      const end = k + 1 < hits.length ? hits[k + 1].at : text.length;
      const rest = text.slice(h.at + h.len, end);
      const cells = scanStatuses(rest);
      if (seen.has(h.s.id)) return;
      seen.add(h.s.id); width = Math.max(width, cells.length);
      out.push({ studentId: h.s.id, cells, raw: text.slice(h.at, end).trim() });
    });
  }
  const w = Math.max(1, width);
  for (const r of out) while (r.cells.length < w) r.cells.push('NONE');
  return { columns: Array.from({ length: w }, (_, k) => ({ index: k, header: `第${k + 1}列` })), rows: out, unmatched, missingStudents: students.filter((x) => !seen.has(x.id)).map((x) => x.id) };
}
/**
 * OCR 常把表格一行拆成好几块（姓名一块、✓ 一块）：按纵向位置把同一行的块拼起来（从左到右）
 * lines: [{ text, x, y, w, h }]
 */
export function mergeRows(lines) {
  const ls = lines.filter((l) => (l.text || '').trim()).map((l) => ({ ...l, cy: (l.y ?? 0) + (l.h ?? 0) / 2 })).sort((a, b) => a.cy - b.cy);
  const rows = [];
  for (const l of ls) {
    const r = rows[rows.length - 1];
    const tol = Math.max(6, Math.min(l.h || 20, r ? r.h : 20) * 0.55);
    if (r && Math.abs(l.cy - r.cy) <= tol) { r.items.push(l); r.cy = (r.cy * (r.items.length - 1) + l.cy) / r.items.length; r.h = Math.max(r.h, l.h || 0); } else rows.push({ cy: l.cy, h: l.h || 20, items: [l] });
  }
  return rows.map((r) => { const it = r.items.sort((a, b) => (a.x ?? 0) - (b.x ?? 0)); return { text: it.map((x) => x.text.trim()).join(' '), x: it[0].x ?? 0, y: r.cy - r.h / 2, w: 0, h: r.h }; });
}

/** 从一段文字里依次找出状态标记 */
export function scanStatuses(text) {
  const t = half(text).toLowerCase();
  const out = [];
  let i = 0;
  while (i < t.length) {
    const ch = t[i];
    if (/\s|[,，;；:：|()（）[\]【】.。、]/.test(ch)) { i++; continue; }
    const tok = SCAN.find((x) => t.startsWith(x, i));
    if (tok) { const k = TOKEN_MAP.get(tok); if (k !== 'NONE' || tok === '/' || tok === '-' || tok === '—') out.push(k); i += tok.length; continue; }
    // 单独的字母/数字（前后不是字母数字）：v、x、0、1、a 等
    const w = t.slice(i).match(/^[a-z0-9+\-]+/);
    if (w) { const k = TOKEN_MAP.get(w[0]); if (k && k !== 'NONE') out.push(k); i += w[0].length; continue; }
    i++;
  }
  return out;
}

/** 取识别结果某一列 → statuses 对象（NONE 不写） */
export function columnStatuses(result, col) {
  const st = {};
  for (const r of result.rows) { const k = r.cells[col] || 'NONE'; if (k !== 'NONE') st[r.studentId] = k; }
  return st;
}

// ---------------- 统计 ----------------
export const HANDED = ['SUBMITTED', 'EXCELLENT', 'LATE'];
/** statuses → 各状态人数、名单、交齐率 */
export function tally(students, statuses) {
  const lists = Object.fromEntries(STATUS_KEYS.map((k) => [k, []]));
  for (const s of students) lists[statuses[s.id] || 'NONE'].push(s);
  const handedIn = HANDED.reduce((n, k) => n + lists[k].length, 0);
  const expected = students.length - lists.LEAVE.length;
  return { lists, counts: Object.fromEntries(STATUS_KEYS.map((k) => [k, lists[k].length])), handedIn, expected, rate: expected <= 0 ? 1 : handedIn / expected };
}

const XS = { HEADER: 1, GREEN: 2, RED: 3, YELLOW: 4, BLUE: 5, GOLD: 6 };
const styleOf = { SUBMITTED: XS.GREEN, EXCELLENT: XS.GOLD, LATE: XS.YELLOW, MISSING: XS.RED, LEAVE: XS.BLUE, NONE: 0 };
const ORDER = ['SUBMITTED', 'EXCELLENT', 'LATE', 'MISSING', 'LEAVE', 'NONE'];
const pct = (x) => `${Math.round(x * 1000) / 10}%`;
const who = (s) => (s.no ? `${s.no} ${s.name}` : s.name);

/**
 * 统计名单工作簿（给 xlsx() 用）：统计汇总 + 分类名单 + 明细
 * title: 作业名；statuses/comments: 这次作业的登记
 */
export function statListSheets({ className, title, subject, date, students, statuses, comments = {} }) {
  const t = tally(students, statuses);
  const H = (v) => ({ v, s: XS.HEADER });
  const sum = [
    [H('班级'), className || ''], [H('作业'), [subject, title].filter(Boolean).join(' ')], [H('日期'), date || ''],
    [H('应交'), t.expected], [H('已交（含优秀、迟交）'), t.handedIn], [H('交齐率'), pct(t.rate)], [],
    [H('状态'), H('人数'), H('占比'), H('名单')],
    ...ORDER.map((k) => [{ v: STATUS[k].label, s: styleOf[k] }, t.counts[k], students.length ? pct(t.counts[k] / students.length) : '0%', t.lists[k].map((s) => s.name).join('、')]),
  ];
  const height = Math.max(0, ...ORDER.map((k) => t.lists[k].length));
  const cat = [ORDER.map((k) => H(`${STATUS[k].label}（${t.counts[k]}）`))];
  for (let i = 0; i < height; i++) cat.push(ORDER.map((k) => { const s = t.lists[k][i]; return s ? { v: who(s), s: styleOf[k] } : ''; }));
  const detail = [[H('学号'), H('姓名'), H('小组'), H('状态'), H('备注')],
    ...students.map((s) => { const k = statuses[s.id] || 'NONE'; return [s.no || '', s.name, s.group || '', { v: STATUS[k].label, s: styleOf[k] }, comments[s.id] || '']; })];
  return [{ name: '统计汇总', rows: sum }, { name: '分类名单', rows: cat }, { name: '明细', rows: detail }];
}

/** 全班各次作业的统计名单（一行一次作业：应交、已交、交齐率、未交/迟交/请假名单） */
export function classStatRows(c, fmtDate) {
  const H = (v) => ({ v, s: XS.HEADER });
  const rows = [['日期', '科目', '作业', '应交', '已交', '交齐率', '未交人数', '未交名单', '迟交名单', '请假名单', '未登记名单'].map(H)];
  for (const a of [...c.assignments].sort((x, y) => x.createdAt - y.createdAt)) {
    const t = tally(c.students, a.statuses);
    const names = (k) => t.lists[k].map((s) => s.name).join('、');
    rows.push([fmtDate(a.createdAt), a.subject || '', a.title, t.expected, t.handedIn, { v: pct(t.rate), s: t.rate >= 1 ? XS.GREEN : t.rate < 0.8 ? XS.RED : 0 }, t.counts.MISSING, names('MISSING'), names('LATE'), names('LEAVE'), names('NONE')]);
  }
  return rows;
}

// ---------------- 读取 .xlsx（无依赖：解析 ZIP + DecompressionStream 解压） ----------------
export async function readXlsx(buf) {
  const u8 = buf instanceof Uint8Array ? buf : new Uint8Array(buf);
  const files = await unzip(u8, (n) => n === 'xl/sharedStrings.xml' || n === 'xl/workbook.xml' || n === 'xl/_rels/workbook.xml.rels' || /^xl\/worksheets\/sheet\d+\.xml$/.test(n));
  const dec = (s) => s.replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"').replace(/&apos;/g, "'").replace(/&#(x?)([0-9a-f]+);/gi, (_, x, n) => String.fromCodePoint(parseInt(n, x ? 16 : 10))).replace(/&amp;/g, '&');
  const textOf = (xml) => [...xml.matchAll(/<t(?:\s[^>]*)?>([\s\S]*?)<\/t>|<t(?:\s[^>]*)?\/>/g)].map((m) => dec(m[1] || '')).join('');
  const shared = files['xl/sharedStrings.xml'] ? [...files['xl/sharedStrings.xml'].matchAll(/<si>([\s\S]*?)<\/si>/g)].map((m) => textOf(m[1].replace(/<rPh[\s\S]*?<\/rPh>/g, ''))) : [];
  // 第一个工作表
  let path = 'xl/worksheets/sheet1.xml';
  const wb = files['xl/workbook.xml']; const rels = files['xl/_rels/workbook.xml.rels'];
  if (wb && rels) {
    const rid = (wb.match(/<sheet\b[^>]*\br:id="([^"]+)"/) || [])[1];
    const tgt = rid && (rels.match(new RegExp(`<Relationship\\b[^>]*Id="${rid}"[^>]*Target="([^"]+)"`)) || rels.match(new RegExp(`<Relationship\\b[^>]*Target="([^"]+)"[^>]*Id="${rid}"`)) || [])[1];
    if (tgt) path = tgt.startsWith('/') ? tgt.slice(1) : 'xl/' + tgt.replace(/^\.\//, '');
  }
  const sheet = files[path] || Object.entries(files).find(([k]) => k.startsWith('xl/worksheets/'))?.[1];
  if (!sheet) throw new Error('Excel 里没有找到工作表');
  const rows = [];
  for (const rm of sheet.matchAll(/<row\b[^>]*>([\s\S]*?)<\/row>|<row\b[^>]*\/>/g)) {
    const row = [];
    for (const cm of (rm[1] || '').matchAll(/<c\b([^>]*?)(?:\/>|>([\s\S]*?)<\/c>)/g)) {
      const attrs = cm[1]; const body = cm[2] || '';
      const ref = (attrs.match(/\br="([A-Z]+)\d+"/) || [])[1];
      const type = (attrs.match(/\bt="(\w+)"/) || [])[1];
      const v = (body.match(/<v>([\s\S]*?)<\/v>/) || [])[1];
      let val = '';
      if (type === 's') val = shared[Number(v)] ?? '';
      else if (type === 'inlineStr') val = textOf(body);
      else if (type === 'b') val = v === '1' ? 'TRUE' : 'FALSE';
      else val = v != null ? dec(v) : '';
      const ci = ref ? colIndex(ref) : row.length;
      while (row.length < ci) row.push('');
      row[ci] = val;
    }
    rows.push(row);
  }
  return rows.filter((r) => r.some((c) => String(c).trim()));
}
const colIndex = (s) => { let n = 0; for (const ch of s) n = n * 26 + ch.charCodeAt(0) - 64; return n - 1; };

async function unzip(u8, want) {
  const dv = new DataView(u8.buffer, u8.byteOffset, u8.byteLength);
  let eocd = -1;
  for (let i = u8.length - 22; i >= Math.max(0, u8.length - 66000); i--) if (dv.getUint32(i, true) === 0x06054b50) { eocd = i; break; }
  if (eocd < 0) throw new Error('不是有效的 .xlsx 文件（老版 .xls 请另存为 .xlsx 或 CSV）');
  const n = dv.getUint16(eocd + 10, true); let p = dv.getUint32(eocd + 16, true);
  const out = {}; const td = new TextDecoder();
  for (let k = 0; k < n; k++) {
    if (dv.getUint32(p, true) !== 0x02014b50) break;
    const method = dv.getUint16(p + 10, true); const csize = dv.getUint32(p + 20, true);
    const nlen = dv.getUint16(p + 28, true); const elen = dv.getUint16(p + 30, true); const clen = dv.getUint16(p + 32, true);
    const off = dv.getUint32(p + 42, true);
    const name = td.decode(u8.subarray(p + 46, p + 46 + nlen));
    p += 46 + nlen + elen + clen;
    if (!want(name)) continue;
    const ln = dv.getUint16(off + 26, true); const le = dv.getUint16(off + 28, true);
    const data = u8.subarray(off + 30 + ln + le, off + 30 + ln + le + csize);
    if (method === 0) out[name] = td.decode(data);
    else if (method === 8) out[name] = td.decode(await inflateRaw(data));
  }
  return out;
}
async function inflateRaw(data) {
  if (typeof DecompressionStream === 'undefined') throw new Error('这个浏览器不支持读取 Excel，请另存为 CSV 再导入');
  const ds = new DecompressionStream('deflate-raw');
  const stream = new Blob([data]).stream().pipeThrough(ds);
  return new Uint8Array(await new Response(stream).arrayBuffer());
}

// ---------------- 草稿（只保存在本机，不进云端快照） ----------------
const DRAFT_KEY = 'hw.drafts';
export const loadDrafts = () => { try { const v = JSON.parse(localStorage.getItem(DRAFT_KEY)); return Array.isArray(v) ? v : []; } catch { return []; } };
export const saveDrafts = (list) => { try { localStorage.setItem(DRAFT_KEY, JSON.stringify(list)); } catch { /* 空间不足 / 隐私模式 */ } };

/** 新建草稿：基于已有作业（a）或一次新作业 */
export function makeDraft(c, a, { title, subject, statuses } = {}) {
  const now = Date.now();
  if (a) {
    return { id: newId(), classId: c.id, assignmentId: a.id, isNew: false, title: a.title, subject: a.subject, createdAt: a.createdAt, updatedAt: now, scheduledAt: null,
      statuses: { ...a.statuses, ...(statuses || {}) }, comments: { ...a.comments }, base: { title: a.title, subject: a.subject, statuses: { ...a.statuses }, comments: { ...a.comments } } };
  }
  const st = { ...Object.fromEntries(c.students.filter((s) => s.longLeave).map((s) => [s.id, 'LEAVE'])), ...(statuses || {}) };
  return { id: newId(), classId: c.id, assignmentId: newId(), isNew: true, title: title || '', subject: subject || '', createdAt: now, updatedAt: now, scheduledAt: null,
    statuses: st, comments: {}, base: { title: '', subject: '', statuses: {}, comments: {} } };
}

/** 草稿相对创建时改了哪些学生 */
export function draftChanges(dr) {
  const ids = new Set([...Object.keys(dr.statuses), ...Object.keys(dr.base.statuses), ...Object.keys(dr.comments), ...Object.keys(dr.base.comments)]);
  return [...ids].filter((id) => (dr.statuses[id] || 'NONE') !== (dr.base.statuses[id] || 'NONE') || (dr.comments[id] || '') !== (dr.base.comments[id] || ''));
}

/** 把草稿写进数据（只覆盖草稿里改过的学生，云端/其它设备在此期间的其它登记保留） */
export function applyDraft(data, dr) {
  const c = data.classes.find((x) => x.id === dr.classId);
  if (!c) throw new Error('草稿所属的班级已不存在');
  const ex = c.assignments.find((x) => x.id === dr.assignmentId);
  let na;
  if (!ex) {
    na = { id: dr.assignmentId, title: dr.title || '未命名作业', subject: dr.subject || '', createdAt: dr.createdAt, statuses: { ...dr.statuses }, comments: { ...dr.comments }, countedBooks: null, pinned: false };
  } else {
    const st = { ...ex.statuses }; const cm = { ...ex.comments };
    for (const id of draftChanges(dr)) {
      const v = dr.statuses[id] || 'NONE'; if (v === 'NONE') delete st[id]; else st[id] = v;
      const t = dr.comments[id] || ''; if (t) cm[id] = t; else delete cm[id];
    }
    na = { ...ex, statuses: st, comments: cm, title: dr.title !== dr.base.title ? dr.title || ex.title : ex.title, subject: dr.subject !== dr.base.subject ? dr.subject : ex.subject };
  }
  return { ...data, classes: data.classes.map((k) => (k.id !== c.id ? k : { ...k, assignments: ex ? k.assignments.map((x) => (x.id === na.id ? na : x)) : [...k.assignments, na] })) };
}
