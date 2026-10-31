// 网页版图片文字识别：按需从 CDN 加载 Tesseract.js（首次需下载约 20MB 中文模型，之后浏览器会缓存）
const TESS = 'https://cdn.jsdelivr.net/npm/tesseract.js@5.1.1/dist/tesseract.min.js';
let loading = null;

function loadScript() {
  if (window.Tesseract) return Promise.resolve(window.Tesseract);
  if (!loading) {
    loading = new Promise((res, rej) => {
      const el = document.createElement('script');
      el.src = TESS; el.onload = () => res(window.Tesseract); el.onerror = () => { loading = null; rej(new Error('文字识别组件下载失败，请检查网络')); };
      document.head.appendChild(el);
    });
  }
  return loading;
}

/**
 * 预处理：放大到合适尺寸、转灰度二值化，并擦掉表格框线（框线会被认成“[ ]”等乱码）。
 * img: 可绘制到 canvas 的图片；返回 canvas
 */
export function cleanImage(img) {
  const W0 = img.naturalWidth || img.width; const H0 = img.naturalHeight || img.height;
  const k = Math.min(2.5, Math.max(1, 1800 / Math.max(W0, H0)));
  const W = Math.round(W0 * k); const H = Math.round(H0 * k);
  const cv = document.createElement('canvas'); cv.width = W; cv.height = H;
  const g = cv.getContext('2d', { willReadFrequently: true });
  g.drawImage(img, 0, 0, W, H);
  const im = g.getImageData(0, 0, W, H); const d = im.data;
  const gray = new Uint8Array(W * H);
  for (let i = 0; i < W * H; i++) gray[i] = (d[i * 4] * 299 + d[i * 4 + 1] * 587 + d[i * 4 + 2] * 114) / 1000;
  const dark = binarize(gray, W, H);
  removeLines(dark, W, H);
  for (let i = 0; i < W * H; i++) { const v = dark[i] ? 0 : 255; d[i * 4] = d[i * 4 + 1] = d[i * 4 + 2] = v; d[i * 4 + 3] = 255; }
  g.putImageData(im, 0, 0);
  return cv;
}

/** 局部自适应阈值（积分图），比全局阈值更能应付拍照时的光照不均 */
export function binarize(gray, W, H) {
  const S = new Float64Array((W + 1) * (H + 1));
  for (let y = 0; y < H; y++) { let row = 0; for (let x = 0; x < W; x++) { row += gray[y * W + x]; S[(y + 1) * (W + 1) + x + 1] = S[y * (W + 1) + x + 1] + row; } }
  const r = Math.max(8, Math.round(Math.min(W, H) / 40)); const out = new Uint8Array(W * H);
  for (let y = 0; y < H; y++) {
    const y0 = Math.max(0, y - r); const y1 = Math.min(H, y + r + 1);
    for (let x = 0; x < W; x++) {
      const x0 = Math.max(0, x - r); const x1 = Math.min(W, x + r + 1);
      const sum = S[y1 * (W + 1) + x1] - S[y0 * (W + 1) + x1] - S[y1 * (W + 1) + x0] + S[y0 * (W + 1) + x0];
      const mean = sum / ((x1 - x0) * (y1 - y0));
      out[y * W + x] = gray[y * W + x] < mean * 0.85 && gray[y * W + x] < 200 ? 1 : 0;
    }
  }
  return out;
}

/** 擦掉长的横线/竖线（表格框），容许 1 像素的断点和轻微倾斜 */
export function removeLines(dark, W, H) {
  const minH = Math.max(40, Math.round(W / 22)); const minV = Math.max(40, Math.round(H / 22));
  const kill = new Uint8Array(W * H);
  for (let y = 0; y < H; y++) {
    let start = -1; let gap = 0;
    for (let x = 0; x <= W; x++) {
      const on = x < W && (dark[y * W + x] || (y > 0 && dark[(y - 1) * W + x] && dark[(y + 1 < H ? y + 1 : y) * W + x]));
      if (on) { if (start < 0) start = x; gap = 0; } else if (start >= 0 && ++gap > 2) { const end = x - gap; if (end - start >= minH) for (let i = start; i <= end; i++) kill[y * W + i] = 1; start = -1; gap = 0; }
    }
  }
  for (let x = 0; x < W; x++) {
    let start = -1; let gap = 0;
    for (let y = 0; y <= H; y++) {
      const on = y < H && dark[y * W + x];
      if (on) { if (start < 0) start = y; gap = 0; } else if (start >= 0 && ++gap > 2) { const end = y - gap; if (end - start >= minV) for (let i = start; i <= end; i++) kill[i * W + x] = 1; start = -1; gap = 0; }
    }
  }
  // 线条有粗细：把被擦像素上下（横线）左右（竖线）各扩 1 像素
  for (let i = 0; i < W * H; i++) if (kill[i]) dark[i] = 0;
  for (let i = 0; i < W * H; i++) if (kill[i]) { for (const j of [i - 1, i + 1, i - W, i + W]) if (j >= 0 && j < W * H) dark[j] = 0; }
}

/**
 * 识别图片里的文字行，返回 [{ text, x, y, w, h, charX }]（去掉中文字间空格；charX 为每个字的横向中心）
 * onProgress(0~1, 说明)
 */
export async function recognizeLines(file, onProgress = () => {}) {
  const T = await loadScript();
  onProgress(0.02, '加载识别引擎…');
  const worker = await T.createWorker(['chi_sim', 'eng'], 1, {
    logger: (m) => { if (m && typeof m.progress === 'number') onProgress(m.progress, m.status === 'recognizing text' ? '识别中…' : '下载中文模型（首次较慢）…'); },
  });
  try {
    await worker.setParameters({ tessedit_pageseg_mode: '11' }); // 稀疏文字：座位表上名字是一块块分散的
    const img = await loadImage(file);
    const cv = cleanImage(img); const k = cv.width / (img.naturalWidth || img.width);
    const { data } = await worker.recognize(cv);
    const out = [];
    for (const line of data.lines || []) {
      let text = ''; const charX = [];
      for (const w of line.words || []) {
        const t = (w.text || '').replace(/\s+/g, '');
        if (!t) continue;
        const { x0, x1 } = w.bbox; const step = (x1 - x0) / t.length;
        for (let i = 0; i < t.length; i++) { text += t[i]; charX.push(x0 + step * (i + 0.5)); }
        // 中文常被切成单字：不加空格直接拼接，靠每个字的横坐标区分座位；英文/数字词之间补一个空格
        if (/[A-Za-z0-9]$/.test(t)) { text += ' '; charX.push(x1); }
      }
      if (!text.trim()) continue;
      const b = line.bbox;
      out.push({ text, x: b.x0 / k, y: b.y0 / k, w: (b.x1 - b.x0) / k, h: (b.y1 - b.y0) / k, charX: charX.map((v) => v / k) });
    }
    return out;
  } finally {
    await worker.terminate();
  }
}

function loadImage(file) {
  return new Promise((res, rej) => {
    const url = URL.createObjectURL(file); const img = new Image();
    img.onload = () => { URL.revokeObjectURL(url); res(img); };
    img.onerror = () => { URL.revokeObjectURL(url); rej(new Error('图片无法打开')); };
    img.src = url;
  });
}
