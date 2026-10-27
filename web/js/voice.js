// 语音登记解析（与安卓版 VoiceParser / PinyinSim 同一套算法）
import { pinyin } from '../vendor/pinyin-pro.mjs';

// ---------- 文本工具 ----------
export const halfWidth = (s) => s.replace(/[！-～]/g, (c) => String.fromCharCode(c.charCodeAt(0) - 0xfee0));
export const canon = (s) => s.trim().toUpperCase().replace(/ /g, '');

const confusable = {
  O: '0', o: '0', D: '0', Q: '0', U: '0', I: '1', l: '1', i: '1', '|': '1', '!': '1', L: '1', T: '7',
  Z: '2', z: '2', S: '5', s: '5', B: '8', G: '6', b: '6', g: '9', q: '9', A: '4',
  y: '4', Y: '4', h: '4', '?': '7', ')': '1', '(': '1',
};
export function normalize(line) {
  const fixed = halfWidth(line).replace(/[A-Za-z0-9|!?()]+/g, (t) => {
    const digits = [...t].filter((c) => c >= '0' && c <= '9').length;
    const fix = t.length >= 3 ? digits * 10 >= t.length * 6 : t.length === 2 ? digits >= 1 : false;
    return fix ? [...t].map((c) => confusable[c] ?? c).join('') : t;
  });
  return fixed.replace(/(?<=\d)[\s\-_.·]+(?=\d)/g, '');
}

// ---------- 拼音 ----------
const isHan = (c) => { const x = c.codePointAt(0); return x >= 0x3400 && x <= 0x9fff; };
const polyphones = {
  曾: ['zeng', 'ceng'], 单: ['shan', 'dan', 'chan'], 解: ['xie', 'jie'], 仇: ['qiu', 'chou'], 朴: ['piao', 'pu'],
  区: ['ou', 'qu'], 查: ['zha', 'cha'], 乐: ['yue', 'le'], 尉: ['yu', 'wei'], 盖: ['ge', 'gai'], 缪: ['miao', 'mou', 'miu'],
  覃: ['qin', 'tan'], 翟: ['zhai', 'di'], 长: ['chang', 'zhang'], 行: ['xing', 'hang'], 重: ['zhong', 'chong'],
  朝: ['zhao', 'chao'], 传: ['chuan', 'zhuan'], 都: ['du', 'dou'], 角: ['jiao', 'jue'], 宿: ['su', 'xiu'], 茜: ['qian', 'xi'],
  蔚: ['wei', 'yu'], 莘: ['shen', 'xin'], 柏: ['bai', 'bo'], 晟: ['sheng', 'cheng'], 贾: ['jia', 'gu'], 秘: ['mi', 'bi'],
  召: ['zhao', 'shao'], 种: ['zhong', 'chong'], 大: ['da', 'dai'], 藏: ['zang', 'cang'], 奇: ['qi', 'ji'], 会: ['hui', 'kuai'],
  牟: ['mou', 'mu'], 沈: ['shen', 'chen'], 颉: ['xie', 'jie'], 和: ['he', 'huo', 'hu'], 强: ['qiang', 'jiang'],
  弹: ['dan', 'tan'], 还: ['hai', 'huan'], 着: ['zhe', 'zhuo', 'zhao'], 参: ['can', 'shen'], 似: ['si', 'shi'],
};
const rdCache = new Map();
export function readings(c) {
  if (!isHan(c)) return [];
  let r = rdCache.get(c);
  if (!r) {
    const all = pinyin(c, { toneType: 'none', type: 'array', multiple: true }).map((s) => s.toLowerCase().replace(/ü/g, 'u').replace(/v/g, 'u'));
    r = [...new Set([...all, ...(polyphones[c] || [])])].filter((s) => /^[a-z]+$/.test(s));
    rdCache.set(c, r);
  }
  return r;
}

// ---------- 拼音相似度 ----------
const initials = ['zh', 'ch', 'sh', 'b', 'p', 'm', 'f', 'd', 't', 'n', 'l', 'g', 'k', 'h', 'j', 'q', 'x', 'r', 'z', 'c', 's', 'y', 'w'];
function split(sy) { const ini = initials.find((i) => sy.startsWith(i)) || ''; return [ini, sy.slice(ini.length)]; }
const pair = (a, b) => (a < b ? `${a}|${b}` : `${b}|${a}`);
const lightIni = new Set(['z|zh', 'c|ch', 's|sh', 'l|n', 'f|h', 'l|r']);
const midIni = new Set(['b|p', 'd|t', 'g|k', 'j|zh', 'ch|q', 'sh|x', 'j|z', 'c|q', 's|x', 'r|y', '|y', '|w', 'h|k']);
const lightFin = new Set(['in|ing', 'en|eng', 'an|ang', 'ian|iang', 'uan|uang', 'ong|un', 'eng|ong', 'ian|in', 'e|o', 'o|uo', 'ui|uei', 'iu|iou', 'un|uen']);
const midFin = new Set(['ie|ue', 'ei|ui', 'ai|ei', 'a|e', 'en|in', 'an|en', 'ao|ou', 'i|ie', 'ing|ong', 'iang|uang', 'e|ie']);
const iniCost = (a, b) => (a === b ? 0 : lightIni.has(pair(a, b)) ? 0.3 : midIni.has(pair(a, b)) ? 0.55 : 1);
const finCost = (a, b) => (a === b ? 0 : lightFin.has(pair(a, b)) ? 0.3 : midFin.has(pair(a, b)) ? 0.55 : 1);
export function syllable(a, b) {
  if (a === b) return 0;
  const [ia, fa] = split(a); const [ib, fb] = split(b);
  return Math.min(1, 0.55 * iniCost(ia, ib) + 0.45 * finCost(fa, fb));
}
function syllableAlt(a, b) { let best = 1; for (const x of a) for (const y of b) best = Math.min(best, syllable(x, y)); return best; }
export function sequence(heard, name) {
  const n = heard.length; const m = name.length;
  const dp = Array.from({ length: n + 1 }, (_, i) => Array.from({ length: m + 1 }, (_, j) => (i === 0 ? j : j === 0 ? i : 0)));
  for (let i = 1; i <= n; i++) for (let j = 1; j <= m; j++) {
    dp[i][j] = Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1, dp[i - 1][j - 1] + syllableAlt(heard[i - 1], name[j - 1]));
  }
  return dp[n][m];
}

// ---------- 中文数字 ----------
const cnDigit = { 零: 0, 〇: 0, 一: 1, 幺: 1, 二: 2, 两: 2, 三: 3, 四: 4, 五: 5, 六: 6, 七: 7, 八: 8, 九: 9 };
const cnRun = /[零〇一幺二两三四五六七八九十百]+/g;
const cnNumberSrc = '[一二两三四五六七八九幺]?百(?:零?[一二三四五六七八九]十|零[一二三四五六七八九]|[一二三四五六七八九]十)?[一二三四五六七八九]?|[一二三四五六七八九]?十[一二三四五六七八九]?|[零〇一幺二两三四五六七八九]+';
export function cnToNumber(s) {
  if (!/[十百]/.test(s)) return [...s].map((c) => cnDigit[c] ?? 0).join('');
  let total = 0; let cur = 0;
  for (const c of s) {
    if (c === '百') { total += (cur || 1) * 100; cur = 0; } else if (c === '十') { total += (cur || 1) * 10; cur = 0; } else cur = cnDigit[c] ?? 0;
  }
  return String(total + cur);
}
function convertChineseNumbers(s) {
  const out = []; const origin = []; const originEnd = []; const positional = [];
  const put = (ch, a, b, p) => { out.push(ch); origin.push(a); originEnd.push(b); positional.push(p); };
  let last = 0;
  for (const m of s.matchAll(cnRun)) {
    const st = m.index; const en = st + m[0].length;
    for (let i = last; i < st; i++) put(s[i], i, i + 1, false);
    if (st > 0 && /\d/.test(s[st - 1])) put(' ', st, st, false);
    let first = true;
    for (const seg of m[0].matchAll(new RegExp(cnNumberSrc, 'g'))) {
      const a = st + seg.index; const b = a + seg[0].length; const p = /[十百]/.test(seg[0]);
      if (!first) put(' ', a, a, false);
      for (const ch of cnToNumber(seg[0])) put(ch, a, b, p);
      first = false;
    }
    last = en;
    if (last < s.length && /\d/.test(s[last])) put(' ', last, last, false);
  }
  for (let i = last; i < s.length; i++) put(s[i], i, i + 1, false);
  return { text: out.join(''), origin, originEnd, positional };
}

const KIND = { NAME: '原名', PINYIN: '同音', FUZZY: '音近', GIVEN: '叫名', INITIALS: '首字母', NUMBER: '学号', NUMBER_SOUND: '音近学号' };
const numeralReadings = [['零', 'ling'], ['一', 'yi'], ['一', 'yao'], ['二', 'er'], ['二', 'liang'], ['三', 'san'], ['四', 'si'], ['五', 'wu'], ['六', 'liu'], ['七', 'qi'], ['八', 'ba'], ['九', 'jiu'], ['十', 'shi'], ['百', 'bai']];
const haoChars = new Set([...'号好浩豪耗毫郝昊皓']);
const numeralChars = '零〇一幺二两三四五六七八九十百';
const fillers = ['然后', '还有', '这个', '那个', '下一个', '接着', '同学', '以及', '好的', '下面', '一下', '一个', '我们', '他们', '还是', '就是', '可以', '什么', '怎么', '这里', '那里', '现在', '已经', '名字', '学号', '作业', '交了', '没交', '请假', '没有', '对的', '是的', '继续', '最后', '第一', '再来', '嗯嗯', '那么', '所以', '但是'];

/**
 * @param roster [{id,no,name}]
 * @param opts {tolerance: 0.6|1|1.4, initials: bool}
 */
export function createVoiceParser(roster, opts = {}) {
  const tolerance = opts.tolerance ?? 1;
  const useInitials = opts.initials ?? true;
  const named = roster.filter((s) => (s.name || '').length >= 2);
  const cands = named.map((s) => ({ s, syl: [...s.name].map(readings) })).filter((c) => c.syl.every((x) => x.length));
  const givenCands = cands.filter((c) => c.syl.length >= 3).map((c) => ({ s: c.s, syl: c.syl.slice(1) }));
  const nameLens = [...new Set(cands.map((c) => c.syl.length))].sort((a, b) => b - a);
  const byNo = new Map(roster.filter((s) => (s.no || '').trim()).map((s) => [canon(s.no), s]));
  const byVal = new Map();
  for (const s of roster) { if (/^\d+$/.test((s.no || '').trim())) { const v = Number(s.no.trim()); byVal.set(v, [...(byVal.get(v) || []), s]); } }
  const noLens = [...new Set([...byNo.keys()].filter((k) => /^\d+$/.test(k)).map((k) => k.length))];
  const maxCost = (len) => tolerance * (len === 2 ? 0.36 : len === 3 ? 0.62 : 1.05);
  const lookupNo = (n) => (byNo.has(n) ? [byNo.get(n)] : /^\d+$/.test(n) ? byVal.get(Number(n)) || [] : []);

  function initialCombos(syl) {
    let acc = new Set(['']);
    for (const alts of syl) {
      const f = new Set(alts.map((a) => a[0].toUpperCase()));
      acc = new Set([...acc].flatMap((p) => [...f].map((x) => p + x)));
      if (acc.size > 32) acc = new Set([...acc].slice(0, 32));
    }
    return acc;
  }
  const initialsIndex = new Map();
  for (const c of cands) for (const k of initialCombos(c.syl)) { const l = initialsIndex.get(k) || []; if (!l.some((x) => x.s.id === c.s.id)) l.push(c); initialsIndex.set(k, l); }

  function splitNumber(run) {
    if (lookupNo(run).length || !noLens.length) return [run];
    const n = run.length; const best = Array(n + 1).fill(null); best[0] = [];
    for (let i = 0; i < n; i++) {
      if (!best[i]) continue;
      for (const l of noLens) {
        if (i + l > n) continue;
        const piece = run.slice(i, i + l);
        if (!lookupNo(piece).length) continue;
        const cand = [...best[i], piece];
        if (!best[i + l] || cand.length < best[i + l].length) best[i + l] = cand;
      }
    }
    return best[n] || [run];
  }

  function numeralOptions(c) {
    const rd = readings(c); if (!rd.length) return [];
    const res = new Map();
    for (const [n, r] of numeralReadings) {
      const d = Math.min(...rd.map((x) => syllable(x, r)));
      const cost = c === n ? 0 : d === 0 ? 1 : d <= 0.3 ? 2 : null;
      if (cost === null) continue;
      if (!res.has(n) || res.get(n) > cost) res.set(n, cost);
    }
    return [...res.entries()];
  }

  return function parse(text) {
    const t = halfWidth(text).replace(/(?<=[㐀-鿿])\s+(?=[㐀-鿿])/g, '');
    const used = Array(t.length).fill(false);
    const matches = [];
    const free = (a, b) => { for (let i = a; i < b; i++) if (used[i]) return false; return true; };
    const take = (a, b) => { for (let i = a; i < b; i++) used[i] = true; };

    // 1) 原名
    const same = new Map();
    for (const s of named) same.set(s.name, [...(same.get(s.name) || []), s]);
    for (const name of [...same.keys()].sort((a, b) => b.length - a.length)) {
      let from = 0;
      for (;;) {
        const i = t.indexOf(name, from); if (i < 0) break;
        if (free(i, i + name.length)) { matches.push({ ids: same.get(name).map((s) => s.id), heard: name, kind: KIND.NAME, start: i, cost: 0 }); take(i, i + name.length); }
        from = i + 1;
      }
    }
    // 口头禅屏蔽
    const blocked = Array(t.length).fill(false);
    for (const w of fillers) { let from = 0; for (;;) { const i = t.indexOf(w, from); if (i < 0) break; for (let k = i; k < i + w.length; k++) blocked[k] = true; from = i + 1; } }

    // 2) 音近数字 + 号
    for (let i = 0; i < t.length;) {
      if (used[i] || !isHan(t[i])) { i++; continue; }
      let j = i; const options = [];
      while (j < t.length && !used[j] && !haoChars.has(t[j]) && j - i < 6) { const o = numeralOptions(t[j]); if (!o.length) break; options.push(o); j++; }
      const endsHao = j < t.length && haoChars.has(t[j]) && options.length;
      const allReal = [...t.slice(i, j)].every((c) => numeralChars.includes(c));
      if (!endsHao || allReal) { i++; continue; }
      let best = null;
      const dfs = (k, acc, cost) => {
        if (best && cost >= best.cost) return;
        if (k === options.length) {
          const segs = [...acc.matchAll(new RegExp(cnNumberSrc, 'g'))].map((m) => m[0]);
          if (segs.join('') !== acc) return;
          const pieces = segs.map(cnToNumber);
          const total = cost + 3 * (pieces.length - 1);
          if (pieces.every((p) => lookupNo(p).length) && (!best || total < best.cost)) best = { pieces, cost: total };
          return;
        }
        for (const [ch, c] of options[k]) dfs(k + 1, acc + ch, cost + c);
      };
      dfs(0, '', 0);
      if (best) {
        for (const p of best.pieces) matches.push({ ids: lookupNo(p).map((s) => s.id), heard: `${t.slice(i, j + 1)}→${p}`, kind: KIND.NUMBER_SOUND, start: i, cost: 0 });
        take(i, j + 1); i = j + 1;
      } else i++;
    }

    // 3/4) 拼音模糊全名 + 只叫名
    const fuzzy = (pool, kind, limit, uniqueOnly) => {
      if (!pool.length) return;
      const lens = new Set(pool.map((c) => c.syl.length));
      const wl = new Set([...lens].flatMap((l) => [l - 1, l, l + 1]).filter((l) => l >= 2));
      const found = [];
      for (let st = 0; st < t.length; st++) {
        for (const len of wl) {
          const en = st + len; if (en > t.length) continue;
          let ok = true; for (let k = st; k < en; k++) if (used[k] || blocked[k] || !isHan(t[k]) || !readings(t[k]).length) { ok = false; break; }
          if (!ok) continue;
          const heard = [...t.slice(st, en)].map(readings);
          const scored = pool.filter((c) => Math.abs(c.syl.length - len) <= 1).map((c) => [c, sequence(heard, c.syl)])
            .filter(([c, d]) => d <= limit(c.syl.length)).sort((a, b) => a[1] - b[1]);
          if (!scored.length) continue;
          const b = scored[0][1];
          const close = [...new Set(scored.filter((x) => x[1] <= b + 0.12).map((x) => x[0].s.id))];
          if (uniqueOnly && close.length > 1) continue;
          found.push({ st, en, ids: close, cost: b });
        }
      }
      found.sort((a, b) => a.cost / (a.en - a.st) - b.cost / (b.en - b.st) || (b.en - b.st) - (a.en - a.st));
      for (const w of found) {
        if (!free(w.st, w.en)) continue;
        const k = w.cost === 0 && kind === KIND.FUZZY ? KIND.PINYIN : kind;
        matches.push({ ids: w.ids, heard: t.slice(w.st, w.en), kind: k, start: w.st, cost: w.cost });
        take(w.st, w.en);
      }
    };
    fuzzy(cands, KIND.FUZZY, maxCost, false);
    fuzzy(givenCands, KIND.GIVEN, () => 0.2 * tolerance, true);

    // 5) 首字母
    if (useInitials && initialsIndex.size) {
      const resolve = (heard, list) => {
        if (list.length === 1 || !heard) return list.map((c) => c.s.id);
        const sc = list.map((c) => [c, sequence(heard, c.syl)]).sort((a, b) => a[1] - b[1]);
        return sc.filter((x) => x[1] <= sc[0][1] + 0.15).map((x) => x[0].s.id);
      };
      for (const m of t.matchAll(/(?:[A-Za-z]\s*){2,5}/g)) {
        if (!free(m.index, m.index + m[0].length)) continue;
        const list = initialsIndex.get(m[0].replace(/[^A-Za-z]/g, '').toUpperCase());
        if (!list) continue;
        matches.push({ ids: resolve(null, list), heard: m[0].trim(), kind: KIND.INITIALS, start: m.index, cost: 0 });
        take(m.index, m.index + m[0].length);
      }
      for (const len of nameLens) {
        for (let i = 0; i + len <= t.length;) {
          let ok = true;
          for (let k = i; k < i + len; k++) if (used[k] || blocked[k] || !isHan(t[k]) || numeralChars.includes(t[k]) || !readings(t[k]).length) { ok = false; break; }
          if (!ok) { i++; continue; }
          const heard = [...t.slice(i, i + len)].map(readings);
          const hit = [];
          for (const k of initialCombos(heard)) for (const c of initialsIndex.get(k) || []) if (c.syl.length === len && !hit.some((h) => h.s.id === c.s.id)) hit.push(c);
          if (hit.length) { matches.push({ ids: resolve(heard, hit), heard: t.slice(i, i + len), kind: KIND.INITIALS, start: i, cost: 0 }); take(i, i + len); i += len; } else i++;
        }
      }
    }

    // 6) 数字学号
    const rest = [...t].map((c, i) => (used[i] ? ' ' : c)).join('');
    const nt = convertChineseNumbers(rest);
    for (const m of nt.text.matchAll(/\d+/g)) {
      const a = m.index; const b = a + m[0].length - 1;
      const pos = nt.origin[a];
      let any = false;
      for (const piece of nt.positional[a] ? [m[0]] : splitNumber(m[0])) {
        const st = lookupNo(piece);
        if (st.length) { matches.push({ ids: st.map((s) => s.id), heard: piece, kind: KIND.NUMBER, start: pos, cost: 0 }); any = true; }
      }
      if (any) take(pos, nt.originEnd[b]);
    }
    const leftover = [...t].filter((_, i) => !used[i]).join('').replace(/[\s，。、,.!?！？号和还有跟以及然后]+/g, ' ').trim();
    matches.sort((x, y) => x.start - y.start);
    return { matches, leftover };
  };
}
