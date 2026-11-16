// 网页查看版端到端测试：本地起静态服务 + Playwright 拦截 Supabase（只读公开数据）
// 运行：node tools/web-tests/e2e.cjs   截图输出到 $OUT（默认 /tmp/hw-e2e）
const { chromium } = require(process.env.PW || '/opt/node22/lib/node_modules/playwright');
const http = require('http'); const fs = require('fs'); const path = require('path');
const ROOT = path.join(__dirname, '../../web'); const OUT = process.env.OUT || '/tmp/hw-e2e';
fs.mkdirSync(OUT, { recursive: true });
const types = { '.html': 'text/html', '.js': 'text/javascript', '.mjs': 'text/javascript', '.css': 'text/css', '.svg': 'image/svg+xml', '.png': 'image/png', '.webmanifest': 'application/manifest+json' };
const server = http.createServer((req, res) => {
  let p = decodeURIComponent(req.url.split('?')[0]); if (p.endsWith('/')) p += 'index.html';
  const f = path.join(ROOT, p);
  if (!f.startsWith(ROOT) || !fs.existsSync(f)) { res.writeHead(404); return res.end(); }
  res.writeHead(200, { 'Content-Type': types[path.extname(f)] || 'application/octet-stream' }); fs.createReadStream(f).pipe(res);
});

// ---------- 示例数据（合成，不含真实信息） ----------
const DAY = 86400000; const now = Date.now();
const names = ['张锦宇', '李子涵', '林静', '王芳', '冉浩', '周杰', '陈浩宏', '胡晨', '赵敏', '孙悦', '郑怡', '吴磊'];
const students = names.map((n, i) => ({ id: `s${i}`, no: String(i + 1).padStart(2, '0'), name: n, group: `第${(i % 3) + 1}组` }));
const st = (i, k) => ((i * 7 + k * 3) % 11 === 0 ? 'MISSING' : (i + k) % 9 === 0 ? 'LATE' : (i * 3 + k) % 7 === 0 ? 'EXCELLENT' : (i + k) % 13 === 0 ? 'LEAVE' : 'SUBMITTED');
const assignments = Array.from({ length: 12 }, (_, k) => ({ id: `a${k}`, title: `练习${k + 1}`, subject: ['语文', '数学', '英语'][k % 3], createdAt: now - (12 - k) * 2 * DAY,
  statuses: Object.fromEntries(students.map((s, i) => [s.id, k === 11 && i === 3 ? 'MISSING' : st(i, k)])), comments: k === 11 ? { s1: '字迹潦草' } : {}, reviewFailed: k === 11 ? ['s3'] : [] }));
const seed = { version: 1, currentClassId: 'c1', settings: { rules: {} },
  classes: [{ id: 'c1', name: '高一(3)班', emoji: '🚀', students, assignments, groupLeaders: { 第1组: 's0', 第2组: 's1' }, reps: ['s1'],
    scores: [{ id: 'm1', studentId: 's0', delta: 2, reason: '课堂表现好', time: now - DAY }, { id: 'm2', studentId: 's5', delta: -1, reason: '迟到', time: now - 3 * DAY }],
    exams: [{ id: 'e1', title: '第一次月考', subject: '数学', date: now - 5 * DAY, fullScore: 100, scores: Object.fromEntries(students.slice(1).map((s, i) => [s.id, 60 + ((i * 37) % 40)])), absent: ['s0'] }] }] };
let requests = 0; let writes = 0;
function mock(route) {
  const req = route.request(); const url = new URL(req.url()); requests++;
  if (req.method() !== 'GET') { writes++; return route.fulfill({ status: 403, body: '{}' }); }
  if (url.pathname.startsWith('/rest/v1/hw_snapshots') && url.searchParams.get('public_read') === 'eq.true')
    return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([{ data: seed, version: 3, updated_at: new Date(now).toISOString() }]) });
  return route.fulfill({ status: 404, contentType: 'application/json', body: '{}' });
}

let failures = 0;
const check = (ok, msg) => { console.log(`${ok ? '✓' : '✗'} ${msg}`); if (!ok) failures++; };

(async () => {
  await new Promise((r) => server.listen(0, r));
  const base = `http://127.0.0.1:${server.address().port}/`;
  const browser = await chromium.launch(process.env.CHROMIUM ? { executablePath: process.env.CHROMIUM } : {});
  const ctx = await browser.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2, hasTouch: true, isMobile: true, colorScheme: 'light' });
  await ctx.route('https://beasxxexggnjcocdbtdl.supabase.co/**', mock);
  const page = await ctx.newPage();
  const errors = []; page.on('pageerror', (e) => errors.push(e.message)); page.on('console', (m) => { if (m.type() === 'error' && !m.text().startsWith('Failed to load resource')) errors.push(m.text()); });
  const shot = async (n) => { await page.waitForTimeout(700); await page.screenshot({ path: `${OUT}/${n}.png`, fullPage: true }); };
  await page.goto(base);

  // 首页：不登录直接看到数据
  await page.waitForSelector('text=练习12', { timeout: 8000 });
  check(await page.isVisible('h1:has-text("高一(3)班")'), '首页大标题是班级名');
  check((await page.locator('input[type=email], input[type=password]').count()) === 0, '没有登录入口');
  check(!(await page.content()).includes('登录'), '页面里没有“登录”字样');
  await shot('1-home');

  // 作业详情：只读网格、复审不合格
  await page.click('text=练习12');
  await page.waitForSelector('.stu');
  check(await page.isVisible('.stu:has-text("王芳") >> text=复审不合格'), '作业详情显示复审不合格');
  check(await page.isVisible('text=-4'), '复审不合格扣分翻倍（-4）');
  check((await page.locator('button:has-text("多选"), button:has-text("剩余标未交"), button:has-text("语音登记"), form').count()) === 0, '作业页没有任何登记 / 修改按钮');
  await shot('2-assignment');
  await page.click('.chip:has-text("复审不合格")');
  check((await page.locator('.stu').count()) === 1, '按“复审不合格”筛选');
  await page.click('.seg button:has-text("列表")');
  await shot('3-assignment-list');
  await page.click('.cell:has-text("王芳")');
  await page.waitForSelector('text=积分流水');
  check(await page.isVisible('text=复审不合格 >> nth=0'), '学生档案显示复审不合格');
  await shot('4-student');
  await page.goBack(); await page.waitForSelector('.cell:has-text("王芳")');
  check(true, '浏览器返回键回到作业页');
  await page.click('button[aria-label="返回"]'); await page.waitForSelector('.tabbar');
  check(await page.isVisible('.tabbar'), '返回首页后显示底栏');

  // 名单、积分、统计、设置
  await page.click('.tabbar button:has-text("名单")'); await page.waitForSelector('.search');
  await page.fill('.search', '周'); check((await page.locator('.cell').count()) === 1, '名单搜索');
  await shot('5-roster');
  await page.click('.seg button:has-text("小组")'); check(await page.isVisible('text=第1组'), '小组列表');
  await page.click('.tabbar button:has-text("积分")'); await page.waitForSelector('text=排行榜');
  await shot('6-scores');
  await page.click('.seg button:has-text("加减分记录")'); check(await page.isVisible('text=课堂表现好'), '加减分记录');
  await page.click('.tabbar button:has-text("统计")'); await page.waitForSelector('text=数据分析');
  check(await page.isVisible('text=复审不合格'), '统计里有复审不合格人次');
  await shot('7-stats');
  await page.click('.seg button:has-text("小组")'); await page.waitForSelector('text=小组对抗榜');
  check(await page.isVisible('text=组长奖惩（计入当前积分）'), '小组对抗榜与组长奖惩');
  await shot('7b-groups');
  await page.click('.seg button:has-text("考试")'); await page.click('text=第一次月考'); await page.waitForSelector('text=成绩排名');
  check(await page.isVisible('text=缺考 · 1 人'), '考试详情：排名与缺考');
  await shot('8-exam');
  await page.goBack(); await page.click('.seg button:has-text("热力图")'); await page.waitForSelector('.heat');
  check((await page.locator('.heat .hc').count()) === 12 * 12, '热力图');
  await shot('9-heat');
  await page.click('.tabbar button:has-text("设置")'); await page.waitForSelector('text=主题配色');
  check((await page.locator('input:not([type=checkbox])').count()) === 0, '设置里没有任何输入框（只有个性化开关）');
  await shot('10-settings');
  await page.click('.seg button:has-text("深色")');
  check(await page.waitForFunction(() => document.documentElement.dataset.theme === 'dark', null, { timeout: 3000 }).then(() => true, () => false), '深色模式');
  await page.click('.pal:has-text("樱花粉")');
  await page.click('.sw >> nth=0');
  await shot('11-settings-dark');
  await page.click('.tabbar button:has-text("首页")'); await page.waitForSelector('text=练习12');
  await shot('12-home-dark');
  await page.reload(); await page.waitForSelector('text=练习12');
  check((await page.getAttribute('html', 'data-theme')) === 'dark' && (await page.evaluate(() => document.documentElement.classList.contains('motion'))), '个性化设置记在本机');

  check(writes === 0, `网页端没有发出任何写请求（共 ${requests} 次请求）`);
  check(errors.length === 0, `没有脚本错误${errors.length ? '：' + errors.join(' | ') : ''}`);
  await browser.close(); server.close();
  console.log(failures ? `\n${failures} 项失败` : '\n全部通过');
  process.exit(failures ? 1 : 0);
})().catch((e) => { console.error(e); process.exit(1); });
