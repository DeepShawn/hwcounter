// 离线缓存：静态资源缓存优先；云端接口不缓存
const CACHE = 'hwcounter-cf2214b';
const ASSETS = ['./', './index.html', './style.css', './manifest.webmanifest', './js/app.js', './js/model.js', './js/cloud.js', './js/voice.js', './js/xlsx.js', './js/seating.js', './js/ocr.js', './js/stats.js',
  './vendor/preact.module.js', './vendor/hooks.module.js', './vendor/htm.module.js', './vendor/pinyin-pro.mjs', './icons/icon.svg', './icons/icon-180.png', './icons/icon-192.png'];
self.addEventListener('install', (e) => { e.waitUntil(caches.open(CACHE).then((c) => c.addAll(ASSETS)).then(() => self.skipWaiting())); });
self.addEventListener('activate', (e) => { e.waitUntil(caches.keys().then((ks) => Promise.all(ks.filter((k) => k !== CACHE).map((k) => caches.delete(k)))).then(() => self.clients.claim())); });
self.addEventListener('fetch', (e) => {
  const url = new URL(e.request.url);
  if (e.request.method !== 'GET' || url.origin !== location.origin) return;
  // 先用网络（拿到新版本），失败再用缓存
  e.respondWith(fetch(e.request).then((r) => { const copy = r.clone(); caches.open(CACHE).then((c) => c.put(e.request, copy)); return r; }).catch(() => caches.match(e.request).then((r) => r || caches.match('./index.html'))));
});
