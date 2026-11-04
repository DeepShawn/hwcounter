// PW=/path/to/playwright CHROMIUM=/usr/bin/chromium node tools/web-tests/navigation.bench.cjs
// Synthetic class only; no real account or cloud data. Timings are diagnostic, not pass/fail thresholds.
const { chromium } = require(process.env.PW || '/opt/node22/lib/node_modules/playwright');
const http = require('http'); const fs = require('fs'); const path = require('path');
const assert = require('node:assert/strict');
const root = path.resolve(__dirname, '../../web');
const students = Array.from({ length: 100 }, (_, i) => ({ id: `s${i}`, no: String(i + 1), name: `同学${i + 1}` }));
const assignments = Array.from({ length: 200 }, (_, i) => ({ id: `a${i}`, title: `作业${i + 1}`, subject: '数学', createdAt: Date.now() - i * 86400000, statuses: Object.fromEntries(students.map((s, j) => [s.id, j % 5 ? 'SUBMITTED' : 'MISSING'])), comments: {} }));
const data = { currentClassId: 'c1', classes: [{ id: 'c1', name: '性能测试班', emoji: '📚', students, assignments, scores: [] }], settings: {} };
const server = http.createServer((req, res) => {
  const file = path.join(root, req.url.split('?')[0] === '/' ? 'index.html' : req.url.split('?')[0]);
  if (!file.startsWith(root + path.sep) || !fs.existsSync(file)) { res.writeHead(404); res.end(); return; }
  res.setHeader('Content-Type', { '.html': 'text/html', '.js': 'text/javascript', '.mjs': 'text/javascript', '.css': 'text/css' }[path.extname(file)] || 'application/octet-stream');
  fs.createReadStream(file).pipe(res);
});
(async () => {
  let browser;
  try {
    await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
    browser = await chromium.launch(process.env.CHROMIUM ? { executablePath: process.env.CHROMIUM } : {});
    const context = await browser.newContext({ viewport: { width: 390, height: 844 }, serviceWorkers: 'block' });
    await context.route('https://beasxxexggnjcocdbtdl.supabase.co/**', (route) => route.fulfill({ contentType: 'application/json', body: JSON.stringify([{ data, version: 1 }]) }));
    const page = await context.newPage();
    const cdp = await context.newCDPSession(page); await cdp.send('Emulation.setCPUThrottlingRate', { rate: 4 });
    await page.goto(`http://127.0.0.1:${server.address().port}/`);
    await page.waitForSelector('.card.assign');
    await page.evaluate(() => document.fonts.ready);
    await page.evaluate(() => { window.navHomeCard = document.querySelector('.card.assign'); });
    const samples = [];
    for (const label of ['热力图', '积分', '首页', '热力图', '积分', '首页', '热力图', '积分', '首页']) {
      const ms = await page.evaluate(async (label) => {
        const button = [...document.querySelectorAll('.tabs button')].find((b) => b.textContent.endsWith(label));
        const start = performance.now(); button.click();
        await new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve)));
        return Math.round(performance.now() - start);
      }, label);
      samples.push({ page: label, ms });
    }
    assert(await page.evaluate(() => window.navHomeCard === document.querySelector('.card.assign')), 'Visited home tab should retain its DOM');
    await page.evaluate(() => window.scrollTo(0, 1200));
    await page.waitForFunction(() => window.scrollY >= 1000);
    await page.locator('.tabs button').filter({ hasText: '热力图' }).click();
    await page.locator('.tabs button').filter({ hasText: '首页' }).click();
    await page.waitForFunction(() => window.scrollY >= 1000);
    await page.locator('.tabs button').filter({ hasText: '热力图' }).click();
    assert(await page.locator('.heat-student-row').count() < students.length, 'Heatmap should render only visible rows');
    await page.locator('.heat-viewport').evaluate((el) => { el.scrollTop = el.scrollHeight; });
    await page.waitForSelector('.heat-student-row:has-text("同学100")');
    await page.locator('.heat-student-row').filter({ hasText: '同学100' }).locator('.heat-cell').first().click();
    assert.equal(await page.locator('.detail-page .hero-title').textContent(), '作业30');
    console.log(JSON.stringify({ students: 100, assignments: 200, cpuSlowdown: 4, samples, checks: ['visited tab DOM retained', 'scroll restored', 'heatmap virtualized', 'last student opens correct assignment'] }, null, 2));
  } finally { if (browser) await browser.close(); await new Promise((resolve) => server.close(resolve)); }
})().catch((e) => { console.error(e); process.exitCode = 1; });
