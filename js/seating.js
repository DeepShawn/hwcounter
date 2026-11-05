// 座位表：导入（文字 / JSON / 图片识别结果）、自动排列、按座位取学生。与安卓版、iPad 版同一套规则和数据格式：
// class.seating = { rows, cols, seats: { 学生id: 下标(行*cols+列) }, frontAtTop: 讲台是否在上方 }

const EMPTY = new Set(['', '空', '空位', '-', '—', '－', '–', 'x', 'X', '×', '/', '无', '0']);
const canon = (s) => String(s).trim().toUpperCase().replace(/\s+/g, '');
const FRONT = /讲台|黑板|讲桌|前门|講台/;

/** 学生匹配器：学号 → 姓名 → 单元格里含学号或姓名 */
export function seatMatcher(students) {
  const byNo = new Map(students.filter((s) => (s.no || '').trim()).map((s) => [canon(s.no), s]));
  const byName = new Map();
  for (const s of students) if (s.name && !byName.has(s.name)) byName.set(s.name, s);
  const names = [...byName.keys()].filter((n) => n.length >= 2).sort((a, b) => b.length - a.length);
  const one = (tok) => {
    const t = canon(tok);
    if (!t) return null;
    if (byNo.has(t)) return byNo.get(t);
    if (byName.has(t)) return byName.get(t);
    const n = names.find((x) => t.includes(x)); if (n) return byName.get(n);
    const d = t.match(/\d+/g); if (d) for (const x of d) { if (byNo.has(x)) return byNo.get(x); const v = String(Number(x)); for (const [k, s] of byNo) if (/^\d+$/.test(k) && String(Number(k)) === v) return s; }
    return null;
  };
  const near = (z) => {
    if (z.length < 2 || z.length > 4) return null;
    const c = names.filter((n) => n.length === z.length && n[0] === z[0] && [...n].filter((ch, i) => ch !== z[i]).length === 1);
    return c.length === 1 ? byName.get(c[0]) : null;
  };
  /** 一段文字里依次出现的所有学生（OCR 把同一排几个人读成一行时用），带字符位置 */
  const all = (text) => {
    const t = String(text); const used = Array(t.length).fill(false); const out = [];
    for (const n of names) {
      let from = 0;
      for (;;) { const i = t.indexOf(n, from); if (i < 0) break; let ok = true; for (let k = i; k < i + n.length; k++) if (used[k]) ok = false; if (ok) { for (let k = i; k < i + n.length; k++) used[k] = true; out.push({ s: byName.get(n), at: i, len: n.length }); } from = i + 1; }
    }
    for (const m of t.matchAll(/\d+/g)) {
      if (used[m.index]) continue;
      // “高一(3)班”“第3排”这类不是学号
      const prev = t[m.index - 1] || ''; const next = t[m.index + m[0].length] || '';
      if ('(（第'.includes(prev) && prev || ')）班级年排列组'.includes(next) && next) continue;
      const s = one(m[0]); if (s && !out.some((o) => o.s.id === s.id)) out.push({ s, at: m.index, len: m[0].length });
    }
    // 整段没认出人：容忍一个字认错（姓相同、长度相同、且只有一个候选）
    if (!out.length) {
      const z = t.replace(/[^\u4e00-\u9fff]/g, '');
      const f = near(z);
      if (f) out.push({ s: f, at: Math.max(0, t.indexOf(z[0])), len: z.length });
    }
    return out.sort((a, b) => a.at - b.at);
  };
  return { one, all };
}

/** 把二维格子（学生 id 或 null）变成 seating */
export function gridToSeating(grid, frontAtTop = true) {
  const rows = grid.length; const cols = Math.max(1, ...grid.map((r) => r.length));
  const seats = {};
  grid.forEach((r, i) => r.forEach((id, j) => { if (id) seats[id] = i * cols + j; }));
  return { rows: Math.max(1, rows), cols, seats, frontAtTop };
}

export function seatingToGrid(seating, students) {
  const { rows, cols, seats } = seating;
  const grid = Array.from({ length: rows }, () => Array(cols).fill(null));
  const ok = new Set(students.map((s) => s.id));
  for (const [id, k] of Object.entries(seats || {})) { if (!ok.has(id)) continue; const r = Math.floor(k / cols); const c = k % cols; if (r < rows && c < cols && !grid[r][c]) grid[r][c] = id; }
  return grid;
}

/**
 * 解析文字座位表：每行一排，单元格用 Tab/逗号/空格分隔，空位写“空”或留空；
 * 单元格写姓名或学号都行；有“讲台”那一行时据此判断讲台在上还是在下。也支持 JSON 二维数组。
 */
export function parseSeatingText(text, students) {
  const m = seatMatcher(students);
  const t = String(text).replace(/^﻿/, '').trim();
  let rowsRaw = []; let frontAtTop = true;
  if (t.startsWith('[') || t.startsWith('{')) {
    const root = JSON.parse(t);
    const arr = Array.isArray(root) ? root : (root.seats || root.rows || root['座位表'] || root['座位'] || root.grid);
    if (!Array.isArray(arr)) throw new Error('JSON 里没有找到座位二维数组（seats）');
    rowsRaw = arr.map((r) => (Array.isArray(r) ? r.map((x) => (x == null ? '' : String(x))) : String(r).split(/[\t,，;；|\s]+/)));
    if (root && root.frontAtTop === false) frontAtTop = false;
  } else {
    const lines = t.split(/\r?\n/);
    let seen = false;
    for (const raw of lines) {
      const line = raw.replace(/\s+$/, '');
      if (!line.trim()) continue;
      if (FRONT.test(line.replace(/\s/g, '')) && line.replace(/[\s\t,，;；|\-—_=*·【】\[\]()（）]/g, '').length <= 4) { frontAtTop = !seen; continue; }
      const cells = /[\t,，;；|]/.test(line) ? line.split(/[\t,，;；|]/) : line.trim().split(/\s+/);
      rowsRaw.push(cells.map((x) => x.trim().replace(/^["']|["']$/g, '')));
      seen = true;
    }
  }
  const placed = new Set(); const unmatched = [];
  const grid = rowsRaw.map((r) => r.map((cell) => {
    if (EMPTY.has(cell.trim())) return null;
    const s = m.one(cell);
    if (!s || placed.has(s.id)) { unmatched.push(cell); return null; }
    placed.add(s.id); return s.id;
  }));
  // 去掉整列/整行都空的边缘
  return finish(grid, frontAtTop, students, unmatched);
}

function finish(grid, frontAtTop, students, unmatched) {
  while (grid.length && grid[grid.length - 1].every((x) => !x)) grid.pop();
  while (grid.length && grid[0].every((x) => !x)) grid.shift();
  const cols = Math.max(0, ...grid.map((r) => r.length));
  grid = grid.map((r) => [...r, ...Array(cols - r.length).fill(null)]);
  const seating = gridToSeating(grid, frontAtTop);
  const seated = new Set(Object.keys(seating.seats));
  return { seating, unmatched, unseated: students.filter((s) => !seated.has(s.id)).map((s) => s.id) };
}

/**
 * 图片识别结果 → 座位表。pieces: [{ text, x, y, w, h }]（图片像素坐标，OCR 的一行或一个词）。
 * 先把每段文字里的姓名/学号定位到大致的位置，再按纵向间隙分排、按横向位置聚成列。
 */
export function seatingFromBoxes(pieces, students) {
  const m = seatMatcher(students);
  const pts = []; let front = null; const unmatched = [];
  for (const p of pieces) {
    const text = String(p.text || '');
    if (FRONT.test(text.replace(/\s/g, '')) && text.replace(/\s/g, '').length <= 6) { front = p.y + p.h / 2; continue; }
    if (/座位表|座次表|座位图/.test(text)) continue;
    const hits = m.all(text);
    if (!hits.length) { if (/[一-鿿]{2,}|\d{2,}/.test(text)) unmatched.push(text.trim()); continue; }
    const L = Math.max(1, text.length);
    for (const h of hits) {
      // 一行里有几个人：按字符位置估算每个人的横坐标
      const cx = p.charX && p.charX.length >= h.at + h.len
        ? p.charX.slice(h.at, h.at + h.len).reduce((a, b) => a + b, 0) / h.len
        : p.x + p.w * ((h.at + h.len / 2) / L);
      pts.push({ id: h.s.id, cx, cy: p.y + p.h / 2, w: p.w * (h.len / L), h: p.h });
    }
  }
  // 同一个人被读到多次：保留第一次
  const seen = new Set(); const P = pts.filter((q) => (seen.has(q.id) ? false : (seen.add(q.id), true)));
  if (!P.length) return { seating: { rows: 1, cols: 1, seats: {}, frontAtTop: true }, unmatched, unseated: students.map((s) => s.id) };
  const med = (a) => { const s = [...a].sort((x, y) => x - y); return s[Math.floor(s.length / 2)]; };
  const mh = med(P.map((q) => q.h)) || 10; const mw = med(P.map((q) => q.w)) || 10;
  // 纠正轻微倾斜：用同一排相邻点估计斜率
  const slope = estimateSlope(P, mh);
  for (const q of P) q.ry = q.cy - slope * q.cx;
  const rowsOf = cluster(P.map((q) => q.ry), mh * 0.9);
  const colsOf = cluster(P.map((q) => q.cx), Math.max(mw * 0.75, mh));
  const R = Math.max(...rowsOf) + 1; const C = Math.max(...colsOf) + 1;
  const grid = Array.from({ length: R }, () => Array(C).fill(null));
  P.forEach((q, i) => {
    const r = rowsOf[i]; let c = colsOf[i];
    if (grid[r][c]) { // 同一格两人：放到本排最近的空位
      let best = -1; for (let k = 0; k < C; k++) if (!grid[r][k] && (best < 0 || Math.abs(k - c) < Math.abs(best - c))) best = k;
      if (best < 0) { grid[r].push(null); best = grid[r].length - 1; }
      c = best;
    }
    grid[r][c] = q.id;
  });
  let frontAtTop = true;
  if (front != null) { const ys = P.map((q) => q.cy); frontAtTop = front <= (Math.min(...ys) + Math.max(...ys)) / 2; }
  return finish(grid, frontAtTop, students, unmatched);
}

/** 一维聚类：排序后相邻间隙大于 gap 就分组，返回每个值的组号（从小到大） */
function cluster(vals, gap) {
  const idx = vals.map((v, i) => i).sort((a, b) => vals[a] - vals[b]);
  const out = Array(vals.length).fill(0); let g = 0; let start = vals[idx[0]]; let prev = start;
  idx.forEach((i, k) => {
    const v = vals[i];
    if (k > 0 && v - prev > gap) { g++; start = v; }
    out[i] = g; prev = v;
  });
  return out;
}

function estimateSlope(P, mh) {
  const s = [];
  const byX = [...P].sort((a, b) => a.cx - b.cx);
  for (let i = 0; i < byX.length; i++) {
    for (let j = i + 1; j < byX.length && j < i + 12; j++) {
      const dx = byX[j].cx - byX[i].cx; const dy = byX[j].cy - byX[i].cy;
      if (dx > mh && Math.abs(dy) < mh * 0.8) s.push(dy / dx);
    }
  }
  if (s.length < 3) return 0;
  s.sort((a, b) => a - b);
  const v = s[Math.floor(s.length / 2)];
  return Math.abs(v) < 0.2 ? v : 0;
}

/** 按学号顺序自动排列（cols 列，从第一排开始，一排排坐满） */
export function autoArrange(students, cols, frontAtTop = true) {
  const list = [...students].sort((a, b) => (a.no || '').length - (b.no || '').length || String(a.no).localeCompare(String(b.no)));
  const c = Math.max(1, cols | 0); const rows = Math.max(1, Math.ceil(list.length / c));
  const seats = {}; list.forEach((s, i) => { seats[s.id] = i; });
  return { rows, cols: c, seats, frontAtTop };
}

/** 讲台在上/下互换：整张表旋转 180°（左右也跟着换，和实际教室一致） */
export function rotateSeating(seating) {
  const { rows, cols } = seating; const n = rows * cols; const seats = {};
  for (const [id, k] of Object.entries(seating.seats)) seats[id] = n - 1 - k;
  return { ...seating, seats, frontAtTop: !seating.frontAtTop };
}

/** 交换两个座位（可以是空位） */
export function swapSeats(seating, a, b) {
  const seats = { ...seating.seats };
  for (const [id, k] of Object.entries(seats)) { if (k === a) seats[id] = b; else if (k === b) seats[id] = a; }
  return { ...seating, seats };
}

/** 增减行列，保持每个人的行列位置不变；放不下的人变成未入座 */
export function resizeSeating(seating, rows, cols) {
  const seats = {};
  for (const [id, k] of Object.entries(seating.seats)) {
    const r = Math.floor(k / seating.cols); const c = k % seating.cols;
    if (r < rows && c < cols) seats[id] = r * cols + c;
  }
  return { ...seating, rows: Math.max(1, rows), cols: Math.max(1, cols), seats };
}
