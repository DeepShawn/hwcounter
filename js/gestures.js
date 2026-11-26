// 禁用浏览器自带的手势：所有滑动、捏合都当作页面内操作，避免滑动时浏览器下拉刷新、左右滑动前进 / 后退、缩放页面。
// 配合 style.css 里的 overscroll-behavior: none 和 touch-action，一起覆盖 Chrome / Edge / Safari（含 iPad、Mac 触控板）。
// 从屏幕左边缘向右滑：如果当前在详情页，就由页面自己返回上一页（onEdgeBack），不交给浏览器。

const EDGE = 24; // 屏幕边缘多宽算“边缘滑动”（iOS Safari 用这一段识别返回 / 前进手势）

/** 从 el 往上找：有没有能在这个方向继续滚动的元素（有就让它自己滚，不拦） */
function canScroll(el, dx, dy) {
  for (let n = el; n && n !== document.body && n !== document.documentElement; n = n.parentElement) {
    if (!(n instanceof HTMLElement)) continue;
    const s = getComputedStyle(n);
    if (dy !== 0 && /(auto|scroll)/.test(s.overflowY) && n.scrollHeight > n.clientHeight + 1) {
      if (dy < 0 ? n.scrollTop > 0 : n.scrollTop + n.clientHeight < n.scrollHeight - 1) return true;
    }
    if (dx !== 0 && /(auto|scroll)/.test(s.overflowX) && n.scrollWidth > n.clientWidth + 1) {
      if (dx < 0 ? n.scrollLeft > 0 : n.scrollLeft + n.clientWidth < n.scrollWidth - 1) return true;
    }
  }
  return false;
}

const stop = (e) => { if (e.cancelable) e.preventDefault(); };

const FOCUSABLE = 'input, textarea, select, [contenteditable="true"]';

export function installGestureGuard({ onEdgeBack } = {}) {
  const opt = { passive: false };
  let start = null; // { x, y, edge, target, moved }

  document.addEventListener('touchstart', (e) => {
    if (e.touches.length > 1) { stop(e); start = null; return; } // 双指捏合缩放
    const t = e.touches[0];
    const edge = t.clientX < EDGE ? 'left' : t.clientX > window.innerWidth - EDGE ? 'right' : null;
    start = { x: t.clientX, y: t.clientY, edge, target: e.target, moved: false };
    // iOS Safari 的边缘返回 / 前进手势只能在 touchstart 时拦下；拦下后这次点按由下面 touchend 补发
    if (edge) stop(e);
  }, opt);

  document.addEventListener('touchmove', (e) => {
    if (e.touches.length > 1) { stop(e); return; }
    if (!start) return;
    const t = e.touches[0];
    const dx = t.clientX - start.x; const dy = t.clientY - start.y;
    if (Math.abs(dx) > 8 || Math.abs(dy) > 8) start.moved = true;
    if (start.edge) {
      // 边缘滑动：页面自己处理（左边缘右滑 = 返回上一页），不让浏览器前进 / 后退
      stop(e);
      if (start.edge === 'left' && !start.done && dx > 70 && Math.abs(dy) < dx * 0.6) { start.done = true; onEdgeBack?.(); }
      return;
    }
    // 页面已在顶部还往下拉：浏览器会下拉刷新，拦下（里面能滚动的区域照常滚动）
    if (dy > 0 && Math.abs(dy) > Math.abs(dx) && window.scrollY <= 0 && !canScroll(e.target, 0, -1)) stop(e);
  }, opt);

  document.addEventListener('touchend', (e) => {
    const s = start; start = null;
    if (!s || !s.edge || s.moved || e.touches.length) return;
    // 边缘的 touchstart 被拦下后浏览器不会再触发点击：在这里补一次
    const el = s.target instanceof Element ? s.target : s.target?.parentElement;
    if (!el) return;
    stop(e);
    const f = el.closest(FOCUSABLE);
    if (f) f.focus();
    el.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true, view: window, clientX: s.x, clientY: s.y }));
  }, opt);
  document.addEventListener('touchcancel', () => { start = null; });

  // Safari 的捏合缩放
  for (const ev of ['gesturestart', 'gesturechange', 'gestureend']) document.addEventListener(ev, stop, opt);

  // 触控板：Ctrl/⌘ + 滚轮 = 缩放；横向两指滑 = 前进 / 后退（Chrome、Edge）
  window.addEventListener('wheel', (e) => {
    if (e.ctrlKey || e.metaKey) { stop(e); return; }
    if (Math.abs(e.deltaX) > Math.abs(e.deltaY) && !canScroll(e.target, Math.sign(e.deltaX), 0)) stop(e);
  }, opt);
  // 双击缩放：由 style.css 的 touch-action: manipulation 关掉（不在这里拦 touchend，免得连点时第二下点不中）
}
