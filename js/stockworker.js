// 股票模式行情在后台线程计算（关注度要在很多时间点重算，放在主线程会卡住页面）
import { boardOf } from './analytics.js';

self.onmessage = (e) => {
  const { id, c, rules, kind, span, now } = e.data;
  try {
    const board = boardOf(c, rules, kind, span, now);
    self.postMessage({ id, board });
  } catch (err) {
    self.postMessage({ id, error: String(err && err.message || err) });
  }
};
