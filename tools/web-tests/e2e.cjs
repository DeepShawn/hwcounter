// 网页版端到端测试：本地起静态服务 + Playwright 拦截 Supabase（只读公开数据）
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
  statuses: Object.fromEntries(students.map((s, i) => [s.id, (k === 11 && i === 3) || (k === 10 && (i === 2 || i === 4)) || (k === 8 && (i === 6 || i === 7)) || (k === 9 && i === 9) ? 'MISSING' : st(i, k)])), comments: k === 11 ? { s1: '字迹潦草' } : {}, reviewFailed: k === 11 ? ['s3'] : [],
  plagiarism: k === 10 ? { s4: ['s2'] } : k === 8 ? { s7: ['s6'] } : k === 9 ? { s9: ['?'] } : {} }));
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
  const ctx = await browser.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2, hasTouch: true, isMobile: true, colorScheme: 'light', acceptDownloads: true });
  await ctx.route('https://beasxxexggnjcocdbtdl.supabase.co/**', mock);
  const page = await ctx.newPage();
  const errors = []; globalThis.pageErrors = errors; page.on('pageerror', (e) => errors.push(e.message)); page.on('console', (m) => { if (m.type() === 'error' && !m.text().startsWith('Failed to load resource')) errors.push(m.text()); });
  const shot = async (n) => { await page.waitForTimeout(700); await page.screenshot({ path: `${OUT}/${n}.png`, fullPage: true }); };
  await page.goto(base);

  // 新手提示：第一次打开出现，可以跳过
  await page.waitForSelector('.guide', { timeout: 8000 });
  check(await page.isVisible('text=欢迎使用作业通 · 网页版'), '第一次打开显示新手提示');
  await page.click('.guide .btn1'); check(await page.isVisible('text=新手提示 2/5'), '新手提示：下一步');
  await shot('0-guide');
  await page.click('.guide-skip');
  check((await page.locator('.guide').count()) === 0, '新手提示可以跳过');
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
  // 浏览器手势已禁用：不下拉刷新、不左右滑动前进 / 后退、不缩放；左边缘右滑由页面自己返回
  check(await page.evaluate(() => getComputedStyle(document.documentElement).overscrollBehaviorY === 'none' && getComputedStyle(document.body).overscrollBehaviorX === 'none'), '浏览器手势：禁用下拉刷新和左右滑动前进 / 后退');
  check(await page.evaluate(() => { const e = new WheelEvent('wheel', { deltaY: -5, ctrlKey: true, bubbles: true, cancelable: true }); document.body.dispatchEvent(e); const z = e.defaultPrevented;
    const x = new WheelEvent('wheel', { deltaX: -80, bubbles: true, cancelable: true }); document.body.dispatchEvent(x); return z && x.defaultPrevented; }), '浏览器手势：触控板缩放、横向滑动返回被拦下');
  const pinch = await page.evaluate(() => { const t = (id, x) => new Touch({ identifier: id, target: document.body, clientX: x, clientY: 300 }); const e = new TouchEvent('touchstart', { touches: [t(1, 100), t(2, 200)], bubbles: true, cancelable: true }); document.body.dispatchEvent(e); document.body.dispatchEvent(new TouchEvent('touchend', { touches: [], bubbles: true, cancelable: true })); return e.defaultPrevented; });
  check(pinch, '浏览器手势：双指缩放被拦下');
  const edgeSwipe = () => page.evaluate(async () => { const el = document.querySelector('main'); const t = (x) => new Touch({ identifier: 9, target: el, clientX: x, clientY: 400 });
    const s = new TouchEvent('touchstart', { touches: [t(4)], bubbles: true, cancelable: true }); el.dispatchEvent(s);
    for (const x of [40, 90, 150]) el.dispatchEvent(new TouchEvent('touchmove', { touches: [t(x)], bubbles: true, cancelable: true }));
    el.dispatchEvent(new TouchEvent('touchend', { touches: [], changedTouches: [t(150)], bubbles: true, cancelable: true })); return s.defaultPrevented; });
  check(await edgeSwipe(), '浏览器手势：边缘滑动不交给浏览器');
  await page.waitForSelector('.tabbar'); check(await page.isVisible('text=练习12') && (await page.locator('.stu').count()) === 0, '左边缘右滑：页面内返回上一页');
  await edgeSwipe(); await page.waitForTimeout(300); check(await page.isVisible('.tabbar') && page.url().startsWith(base), '首页左边缘右滑：不离开网页');
  await page.click('text=练习12'); await page.waitForSelector('.stu');
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
  check(!/\d{1,2}:\d{2}/.test(await page.textContent('main')), '记录只显示日期，不显示时间点');
  await page.click('.tabbar button:has-text("统计")'); await page.waitForSelector('text=数据分析');
  // 作业：与手机端同样的分析项
  await page.waitForSelector('text=主要发现');
  for (const t of ['交齐率趋势', '每次作业构成', '状态分布', '学生分层', '交齐率分布（人数）', '需要关注', '进步之星 · 退步关注', '积分来源（本范围，全班合计）', '各科对比', '分析报告'])
    check(await page.isVisible(`.st:has-text("${t}")`), `作业分析：${t}`);
  check((await page.locator('.heat .hc').count()) === 12 * 12, '作业热力图');
  check(await page.isVisible('text=有记录以来全部 12 次作业'), '范围：全部');
  await shot('7-stats');
  await page.click('.seg button:has-text("近 10 次")'); await page.waitForSelector('text=最近 10 次作业');
  check((await page.locator('.heat .hc').count()) === 12 * 10 && await page.isVisible('text=对比再往前 10 次'), '范围：近 10 次，与上一时段对比');
  await page.click('.seg button:has-text("按时间")'); await page.waitForSelector('.an-range');
  check(await page.isVisible('.an-range b:has-text("本月")'), '范围：按月，可前后翻');
  await page.click('.seg button:has-text("全部") >> nth=0');
  await page.click('button:has-text("生成分析报告")'); await page.waitForSelector('.sheet');
  check((await page.textContent('.sheet-txt')).includes('作业与成绩分析报告') && (await page.textContent('.sheet-txt')).includes('五、考试'), '一键生成文字分析报告（含考试）');
  await shot('7a-report');
  const [dl] = await Promise.all([page.waitForEvent('download'), page.click('.sheet button:has-text("导出 Excel 报告")')]);
  const xpath = path.join(OUT, 'report.xlsx'); await dl.saveAs(xpath);
  const xbuf = fs.readFileSync(xpath);
  check(xbuf.slice(0, 2).toString() === 'PK' && xbuf.includes(Buffer.from('xl/worksheets/sheet8.xml')), '导出 Excel 分析报告（多张工作表）');
  await page.evaluate(() => { window.__printed = 0; const f = HTMLIFrameElement.prototype; const d = Object.getOwnPropertyDescriptor(f, 'contentWindow'); Object.defineProperty(f, 'contentWindow', { get() { const w = d.get.call(this); try { w.print = () => { window.__printed++; window.__printDoc = w.document.body.innerText; }; } catch { /* 跨域 */ } return w; } }); });
  await page.click('.sheet button:has-text("导出 PDF 报告")'); await page.waitForFunction(() => window.__printed > 0);
  check((await page.evaluate(() => window.__printDoc)).includes('、学生明细') && await page.isVisible('text=存储为 PDF'), '导出 PDF 分析报告（打印版）');
  await page.click('.sheet-bg', { position: { x: 10, y: 10 } });
  // 考试
  await page.click('.seg button:has-text("考试")'); await page.waitForSelector('text=各场考试');
  check(await page.isVisible('text=最近平均得分率') && await page.isVisible('text=学生考试明细（平均得分率从高到低）'), '考试分析');
  await shot('7c-exam-stats');
  await page.click('.cell:has-text("第一次月考")'); await page.waitForSelector('text=成绩排名');
  check(await page.isVisible('text=缺考 · 1 人'), '考试详情：排名与缺考');
  await shot('8-exam');
  await page.goBack(); await page.waitForSelector('text=各场考试');
  // 关注：榜单；股票：一级栏目（行情 / 炒股）
  await page.click('.seg button:has-text("关注")'); await page.waitForSelector('text=关注度指数（0–100，越高越需要关注）');
  check((await page.locator('.an-att').count()) === 12, '关注榜：每位同学一行');
  await shot('7d-attention');
  await page.click('.seg button:has-text("信誉")'); await page.waitForSelector('text=信誉分（0–100，越优秀越高）');
  check((await page.locator('.an-att').count()) === 12, '信誉榜：每位同学一行');
  check(await page.isVisible('text=信誉榜 · 点同学查看详情'), '信誉榜标题');
  await shot('7d2-credit');
  await page.click('.seg button:has-text("股票")'); await page.waitForSelector('text=ATTN', { timeout: 15000 });
  check(await page.isVisible('.an-tabs button:has-text("行情")') && await page.isVisible('.an-tabs button:has-text("炒股")'), '股票是一级栏目，第二级：行情 / 炒股');
  check(await page.isVisible('text=关注指数 · 全班平均') && (await page.locator('.an-pill').count()) === 12, '股票模式：大盘与个股');
  // 滚动行情条一直在动
  const tx1 = await page.$eval('.stk-tape-in', (e) => new DOMMatrix(getComputedStyle(e).transform).m41); await page.waitForTimeout(700);
  const tx2 = await page.$eval('.stk-tape-in', (e) => new DOMMatrix(getComputedStyle(e).transform).m41);
  check((await page.locator('.stk-tick').count()) >= 13 && tx2 < tx1, '滚动行情条：大盘 + 个股，持续滚动');
  check(await page.isVisible('.stk-axis') && await page.isVisible('text=按住左右拖动查看') && await page.isVisible('.stk-toggles button:has-text("美股 · 绿涨红跌")'), '行情图：价格轴、提示、配色切换');
  // 按住拖动：显示所选那一根；松手恢复
  const box = await page.locator('.stk-card .an-stock').first().boundingBox();
  await page.mouse.move(box.x + box.width * 0.3, box.y + box.height / 2); await page.mouse.down();
  await page.mouse.move(box.x + box.width * 0.6, box.y + box.height / 2, { steps: 5 });
  const during = await page.textContent('.stk-card .an-quote small');
  check(/\d+\/\d+/.test(during) && await page.isVisible('.stk-foot >> text=成交'), '拖动图表：显示那一天的价格和成交');
  await page.mouse.up(); await page.mouse.move(box.x + box.width * 0.5, box.y - 60);
  check((await page.textContent('.stk-card .an-quote small')).startsWith('近'), '松手恢复最新价');
  // 点按选中、再点取消
  await page.mouse.click(box.x + box.width * 0.5, box.y + box.height / 2); await page.mouse.move(box.x + box.width * 0.5, box.y - 60);
  check(/\d+\/\d+/.test(await page.textContent('.stk-card .an-quote small')), '点按选中一根');
  await page.click('.stk-toggles button:has-text("K线")'); check((await page.locator('.stk-candle').count()) > 20, '股票模式：K 线');
  await page.click('.an-pill >> nth=0'); check((await page.locator('.an-pill:has-text("%")').count()) > 0, '胶囊切换涨跌幅');
  await page.click('.an-pill >> nth=0'); await page.click('.an-pill >> nth=0');
  await shot('7e-stock');
  // 个股详情
  await page.click('.an-row.tap >> nth=0'); await page.waitForSelector('text=成分 · 关注度构成');
  for (const t of ['关键数据', '历史行情', '成分 · 关注度构成']) check(await page.isVisible(`.st:has-text("${t}")`), `个股详情：${t}`);
  check(await page.isVisible('text=积分（市值）') && await page.isVisible('text=相对大盘') && (await page.locator('.stk-hist tbody tr').count()) > 5, '个股详情：关键数据与历史行情');
  await shot('7e2-stock-detail');
  await page.click('button.more:has-text("查看完整个人分析")'); await page.waitForSelector('text=积分流水');
  await page.goBack(); await page.goBack(); await page.waitForSelector('text=ATTN');
  // 信誉分（越高越好）：个人 / 小组
  await page.click('.an-tabs button:has-text("信誉分")'); await page.waitForSelector('text=CRDT', { timeout: 15000 });
  check(await page.isVisible('text=信誉指数 · 全班平均') && (await page.locator('.an-pill').count()) === 12, '信誉分行情：大盘与个股');
  await shot('7e4-credit');
  await page.click('.an-row.tap >> nth=0'); await page.waitForSelector('text=成分 · 信誉分构成（越高越好）');
  check(await page.isVisible('text=诚信') && await page.isVisible('text=信誉排名'), '信誉分个股详情：构成与排名');
  await page.goBack(); await page.waitForSelector('text=CRDT');
  await page.click('.an-tabs button:has-text("小组") >> nth=-1'); await page.waitForSelector('text=GCRD', { timeout: 15000 });
  check((await page.locator('.an-pill').count()) === 3, '小组信誉分板块');
  await page.click('.an-tabs button:has-text("关注度")'); await page.waitForSelector('text=GATT', { timeout: 15000 });
  check((await page.locator('.an-pill').count()) === 3, '小组关注度板块');
  await page.click('.an-tabs button:has-text("个人") >> nth=0');
  await page.click('.an-tabs button:has-text("小组综合")'); await page.waitForSelector('text=GRP', { timeout: 15000 });
  check((await page.locator('.an-pill').count()) === 3 && !(await page.isVisible('.stk-tape')), '股票模式：小组板块');
  await page.click('.an-pill >> nth=0'); await page.click('.an-pill >> nth=0');
  check((await page.textContent('.an-pill >> nth=0')).includes('名'), '小组胶囊：涨跌额 / 涨跌幅 / 名次');
  await page.click('.an-row.tap >> nth=0'); await page.waitForSelector('text=当前名次');
  check(await page.isVisible('text=人均作业扣分') && await page.isVisible('button.more:has-text("查看小组详情")'), '小组个股详情');
  await shot('7e3-group-stock');
  await page.goBack(); await page.waitForSelector('text=GRP');
  await page.click('.an-tabs button:has-text("关注度")'); await page.click('.stk-toggles button:has-text("走势")');
  // 炒股：登录入口隐藏时不提示去登录
  await page.click('.an-tabs button:has-text("炒股")'); await page.waitForSelector('text=模拟炒股');
  check(!(await page.isVisible('text=去登录 / 注册')) && await page.isVisible('text=与真实货币无关'), '炒股：登录入口隐藏时不出现“去登录”');
  await page.click('.an-tabs button:has-text("行情")');
  // 小组
  await page.click('.seg button:has-text("小组")'); await page.waitForSelector('text=小组对抗排名');
  for (const t of ['本期排名', '历次排名', '每周对抗 · 交齐率最高的组胜出', '两组对决', '组长奖惩'])
    check(await page.isVisible(`.st:has-text("${t}")`), `小组分析：${t}`);
  await shot('7b-groups');
  // 抄袭：关系图
  await page.click('.seg button:has-text("抄袭")'); await page.waitForSelector('.an-graph');
  check((await page.locator('.an-graph circle').count()) >= 4 && await page.isVisible('text=冉浩 抄袭 林静') && await page.isVisible('text=胡晨 抄袭 陈浩宏'), '抄袭关系图（全部作业汇总）与记录');
  check(await page.isVisible('.chip.on:has-text("全部作业 3")'), '按作业切换：全部作业');
  check(await page.isVisible('text=孙悦 抄袭 提供者不明') && await page.isVisible('.an-row:has-text("提供者不明 →"):has-text("孙悦")'), '抄袭：提供者不明（记录与关系列表）');
  await shot('7f-plagiarism');
  // 关系图按作业来：每次作业一张图
  await page.click('.cell:has-text("冉浩 抄袭 林静")'); await page.waitForSelector('text=本次作业的关系图');
  const names1 = await page.locator('.an-graph text').allTextContents();
  check(names1.includes('冉浩') && names1.includes('林静') && !names1.includes('胡晨'), '练习11 的关系图只有这次的同学');
  check(await page.isVisible('.chip.on:has-text("练习11")'), '点记录切到那次作业的图');
  await page.click('.chip:has-text("练习9")'); await page.waitForSelector('.chip.on:has-text("练习9")');
  const names2 = await page.locator('.an-graph text').allTextContents();
  check(names2.includes('胡晨') && names2.includes('陈浩宏') && !names2.includes('冉浩'), '练习9 的关系图不一样');
  check(await page.isVisible('text=抄袭 陈浩宏 · 本轮第 1 次，扣分 ×2'), '本次涉及的同学：第几次、倍数');
  await shot('7g-plagiarism-one');
  await page.click('.chip:has-text("练习11")'); await page.click('.cell:has-text("打开这次作业")'); await page.waitForSelector('.cell:has-text("冉浩"), .stu:has-text("冉浩")');
  check(await page.isVisible('text=本次抄袭关系图') && (await page.locator('.an-graph text').allTextContents()).includes('林静'), '作业页显示这次作业的关系图');
  await shot('7h-assign-plag-graph');
  check(await page.isVisible('.cell:has-text("冉浩") .pill:has-text("抄袭")') && await page.isVisible('.cell:has-text("林静") .pill:has-text("抄袭")'), '作业页：抄袭者和提供者都标为抄袭');
  await page.click('.cell:has-text("冉浩")'); await page.waitForSelector('text=积分流水');
  check(await page.isVisible('text=抄袭 林静，本轮第 1 次，扣分 ×2'), '积分流水写明抄袭了谁、第几次、倍数');
  check(!/\d{1,2}:\d{2}/.test(await page.textContent('main')), '积分流水只显示日期');
  await page.goBack(); await page.goBack(); await page.waitForSelector('.an-graph');
  await page.click('.seg button:has-text("作业")');
  await page.click('.tabbar button:has-text("设置")'); await page.waitForSelector('text=主题配色');
  check((await page.locator('.acct input').count()) === 0 && !(await page.content()).includes('登录'), '设置：登录入口默认隐藏');
  const logo = '.about .logo';
  await page.click(logo); await page.click(logo); await page.waitForTimeout(700); await page.click(logo);
  check((await page.locator('.acct input').count()) === 0, '设置：不连续的三下不会打开登录');
  for (let i = 0; i < 3; i++) await page.click(logo);
  await page.waitForSelector('.acct input', { timeout: 3000 });
  check(await page.isVisible('text=已显示登录入口'), '设置：连续点三下作业通图标打开登录入口');
  check((await page.locator('.acct input').count()) === 2 && await page.isVisible('.chip:has-text("用邀请码注册")'), '设置：账号登录（邮箱或用户名）');
  await page.click('.chip:has-text("用邀请码注册")');
  check((await page.locator('.acct input').count()) === 4 && await page.isDisabled('.acct .btn1'), '邀请码注册：邀请码、用户名、显示名、密码，填好前不能提交');
  await page.fill('.acct input >> nth=0', 'ab12cd34'); await page.fill('.acct input >> nth=1', 'test_user'); await page.fill('.acct input >> nth=3', 'weak');
  check(await page.isDisabled('.acct .btn1') && (await page.inputValue('.acct input >> nth=0')) === 'AB12CD34', '密码太简单不能注册；邀请码自动转大写');
  await page.click('.chip:has-text("登录")');
  check(!(await page.content()).includes('更新于') && !(await page.content()).includes('设备'), '不显示数据更新时间和设备来源');
  await page.click('.cell:has-text("新手提示")'); await page.waitForSelector('.guide');
  check(true, '设置里可以重新打开新手提示'); await page.click('.guide-skip');
  await shot('10-settings');
  await page.click('.seg button:has-text("深色")');
  check(await page.waitForFunction(() => document.documentElement.dataset.theme === 'dark', null, { timeout: 3000 }).then(() => true, () => false), '深色模式');
  await page.click('.pal:has-text("樱花粉")');
  await page.click('.sw >> nth=0');
  await shot('11-settings-dark');
  await page.click('.tabbar button:has-text("首页")'); await page.waitForSelector('text=练习12');
  await shot('12-home-dark');
  await page.reload(); await page.waitForSelector('text=练习12');
  check((await page.locator('.guide').count()) === 0, '跳过后刷新不再出现新手提示');
  check((await page.getAttribute('html', 'data-theme')) === 'dark' && (await page.evaluate(() => document.documentElement.classList.contains('motion'))), '个性化设置记在本机');

  await page.click('.tabbar button:has-text("统计")'); await page.waitForSelector('text=数据分析');
  await page.click('.seg button:has-text("股票")'); await page.click('.an-tabs button:has-text("炒股")');
  check(await page.isVisible('text=去登录 / 注册'), '打开登录入口后：炒股提示去登录');
  await page.click('.tabbar button:has-text("设置")'); await page.waitForSelector('text=主题配色');
  for (let i = 0; i < 3; i++) await page.click(logo);
  await page.waitForSelector('.acct input', { state: 'detached', timeout: 3000 });
  check(await page.isVisible('text=已隐藏登录入口'), '再连续点三下：重新隐藏登录入口');
  check(writes === 0, `网页端没有发出任何写请求（共 ${requests} 次请求）`);

  // ---------- 普通用户（凭邀请码注册）：登录后只读查看老师的数据，模拟炒股 ----------
  const ctx2 = await browser.newContext({ viewport: { width: 400, height: 860 }, deviceScaleFactor: 1 });
  await ctx2.addInitScript(() => localStorage.setItem('hw.onboarded', '1'));
  const placed = []; const pwChanges = [];
  const iso = new Date(now).toISOString();
  await ctx2.route(/supabase\.co/, (route) => {
    const req = route.request(); const url = new URL(req.url()); const json = (b) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(b) });
    const p = url.pathname;
    if (p === '/auth/v1/token') return json({ access_token: 't', refresh_token: 'r', expires_in: 3600, user: { id: 'u1', email: 'stu@member.hwcounter.invalid' } });
    if (p === '/auth/v1/user' && req.method() === 'PUT') { pwChanges.push(JSON.parse(req.postData() || '{}')); return json({ id: 'u1' }); }
    if (p === '/rest/v1/rpc/hw_me') return json({ role: 'member', user_id: 'u1', admin_id: 'adm', username: 'stu', display_name: '小明家长', status: 'active', cash: 5000 });
    if (p.startsWith('/rest/v1/hw_snapshots')) return json(url.searchParams.get('user_id') === 'eq.adm' ? [{ data: seed, version: 3, updated_at: iso }] : []);
    if (p === '/rest/v1/hw_prices') return json([{ symbol: 'C:s0', name: '张锦宇', kind: 'C', price: 80, active: true, updated_at: iso }, { symbol: 'A:s1', name: '李子涵', kind: 'A', price: 20, active: true, updated_at: iso }, { symbol: 'GC:c1/第1组', name: '第1组', kind: 'GC', price: 70, active: true, updated_at: iso }]);
    if (p === '/rest/v1/hw_positions') return json([{ symbol: 'C:s0', qty: 10, avg_price: 75, realized: 0 }]);
    if (p === '/rest/v1/hw_orders') return json([{ id: 1, symbol: 'C:s0', side: 'buy', qty: 5, limit_price: 70, status: 'open', reason: '', fill_price: null, created_at: iso }]);
    if (p === '/rest/v1/hw_trades') return json([{ id: 1, symbol: 'C:s0', side: 'buy', qty: 10, price: 75, amount: 750, realized: 0, created_at: iso }]);
    if (p === '/rest/v1/hw_cash_log') return json([{ id: 1, amount: 5000, note: '注册赠送', cash_after: 5000, created_at: iso }]);
    if (p === '/rest/v1/rpc/hw_leaderboard') return json([{ user_id: 'u1', username: 'stu', display_name: '小明家长', status: 'active', cash: 4250, stock_value: 800, equity: 5050, invested: 5000, profit: 50 }]);
    if (p === '/rest/v1/rpc/hw_place_order') { placed.push(JSON.parse(req.postData() || '{}')); return json({ id: 2, status: 'filled', price: 80 }); }
    return route.fulfill({ status: 404, contentType: 'application/json', body: '{}' });
  });
  const p2 = await ctx2.newPage(); p2.on('pageerror', (e) => errors.push(e.message));
  await p2.goto(`http://localhost:${server.address().port}/`); await p2.waitForSelector('text=老师还没有开放查看');
  check((await p2.locator('.acct input').count()) === 0, '没有公开数据时：登录入口同样隐藏');
  for (let i = 0; i < 3; i++) await p2.click('.about .logo');
  await p2.waitForSelector('.acct input', { timeout: 3000 });
  check(true, '没有公开数据时：连续点三下作业通图标后可以登录');
  await p2.fill('.acct input >> nth=0', 'stu'); await p2.fill('.acct input >> nth=1', 'Abc123!x'); await p2.click('.acct .btn1');
  await p2.waitForSelector('.tabbar');
  check(await p2.isVisible('h1:has-text("高一(3)班")'), '普通用户登录后读到老师的数据');
  await p2.click('.tabbar button:has-text("设置")'); await p2.waitForSelector('text=小明家长（stu）');
  check(await p2.isVisible('text=普通用户 · 只读查看老师的班级数据'), '普通用户登录（用户名）');
  await p2.click('.tabbar button:has-text("统计")'); await p2.waitForSelector('text=数据分析');
  await p2.click('.seg button:has-text("股票")'); await p2.click('.an-tabs button:has-text("炒股")'); await p2.waitForSelector('text=总资产');
  check(await p2.isVisible('text=小明家长 的账户') && await p2.isVisible('text=张锦宇（空）') === false && await p2.isVisible('.an-row:has-text("张锦宇")'), '炒股：账户与持仓');
  await p2.screenshot({ path: path.join(OUT, '13-trade.png') });
  await p2.click('.chip:has-text("委托")'); check(await p2.isVisible('text=限价 70.00') && await p2.isVisible('.chip.on:has-text("撤单")'), '炒股：委托（可撤单）');
  await p2.click('.chip:has-text("排行")'); check(await p2.isVisible('.trade-me:has-text("小明家长")'), '炒股：收益排行');
  await p2.click('.trade-card .btn1'); await p2.waitForSelector('.trade-sheet');
  await p2.click('.trade-list .an-row:has-text("张锦宇")'); await p2.waitForSelector('text=现价 80.00');
  await p2.click('.trade-sheet .guide-btns .btn1'); await p2.waitForSelector('text=已成交');
  check(placed.length === 1 && placed[0].p_symbol === 'C:s0' && placed[0].p_side === 'buy' && placed[0].p_qty === 10 && placed[0].p_limit === undefined, '炒股：市价买入下单');
  await p2.click('.chip:has-text("持仓")'); await p2.click('.an-row.tap:has-text("张锦宇")'); await p2.waitForSelector('.trade-sheet');
  await p2.click('.trade-sheet .chip:has-text("限价")'); await p2.fill('.trade-sheet .acct input >> nth=1', '90');
  check(await p2.isVisible('.trade-sheet b:has-text("卖出 10 股")'), '持仓点开：默认卖出');
  await p2.click('.trade-sheet .guide-btns .btn1'); await p2.waitForTimeout(300);
  check(placed.length === 2 && placed[1].p_side === 'sell' && placed[1].p_limit === 90, '炒股：限价卖出下单');
  // 普通用户也能修改密码（与安卓一致）
  await p2.click('.tabbar button:has-text("设置")'); await p2.click('.cell:has-text("修改密码")'); await p2.waitForSelector('text=确认修改');
  await p2.fill('.ed-sheet input >> nth=0', 'Abc123!x'); await p2.fill('.ed-sheet input >> nth=1', 'weakpass'); await p2.fill('.ed-sheet input >> nth=2', 'weakpass'); await p2.click('.ed-sheet .btn1');
  check(await p2.isVisible('text=新密码需要同时包含小写字母、大写字母、数字和符号'), '普通用户改密码：新密码要够复杂');
  await p2.fill('.ed-sheet input >> nth=1', 'Xyz789!q'); await p2.fill('.ed-sheet input >> nth=2', 'Xyz789!q'); await p2.click('.ed-sheet .btn1');
  await p2.waitForSelector('text=密码已修改'); check(pwChanges.some((x) => x.password === 'Xyz789!q'), '普通用户可以修改密码');
  await p2.click('.ed-sheet .btn1');
  check(!(await p2.isVisible('text=录入成绩')) && !(await p2.isVisible('.ib[aria-label="新建作业"]')), '普通用户没有修改入口');
  await ctx2.close();

  // ---------- 老师登录：可以修改数据（与安卓一致），改动自动上传到云端 ----------
  const ctx3 = await browser.newContext({ viewport: { width: 400, height: 860 }, deviceScaleFactor: 1 });
  await ctx3.addInitScript(() => localStorage.setItem('hw.onboarded', '1'));
  const pushes = []; let ver = 5; let conflictOnce = false;
  await ctx3.route(/supabase\.co/, (route) => {
    const req = route.request(); const url = new URL(req.url()); const json = (b, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(b) });
    const p = url.pathname;
    if (p === '/auth/v1/token') return json({ access_token: 't3', refresh_token: 'r3', expires_in: 3600, user: { id: 'u9', email: 'teacher@example.com' } });
    if (p === '/auth/v1/user' && req.method() === 'PUT') { pwChanges.push(JSON.parse(req.postData() || '{}')); return json({ id: 'u9' }); }
    if (p === '/auth/v1/logout') return route.fulfill({ status: 204, body: '' });
    if (p === '/rest/v1/rpc/hw_me') return json({ role: 'admin', user_id: 'u9' });
    if (p === '/rest/v1/rpc/hw_push') {
      if (conflictOnce) { conflictOnce = false; return json({ message: `CONFLICT:${ver}` }, 400); }
      pushes.push(JSON.parse(req.postData() || '{}')); ver++; return json([{ version: ver }]);
    }
    if (p.startsWith('/rest/v1/hw_snapshots') && req.method() === 'PATCH') return route.fulfill({ status: 204, body: '' });
    if (p.startsWith('/rest/v1/hw_snapshots')) {
      if (url.searchParams.get('public_read') === 'eq.true') return json([{ data: seed, version: ver, updated_at: iso }]);
      return json([{ data: pushes.length ? pushes[pushes.length - 1].p_data : seed, version: ver, updated_at: iso, device: '安卓', public_read: true }]);
    }
    if (p.startsWith('/rest/v1/hw_snapshot_history')) return url.searchParams.get('id') ? json([{ data: seed }]) : json([{ id: 7, version: 4, device: '安卓', saved_at: iso }]);
    return route.fulfill({ status: 404, contentType: 'application/json', body: '{}' });
  });
  const p3 = await ctx3.newPage(); p3.on('pageerror', (e) => errors.push(e.message)); p3.on('console', (m) => { if (m.type() === 'error' && !m.text().startsWith('Failed to load resource')) errors.push(m.text()); });
  const lastPush = () => pushes[pushes.length - 1]?.p_data;
  const cls = () => lastPush()?.classes.find((x) => x.id === 'c1');
  const waitPush = async (n) => { for (let i = 0; i < 60 && pushes.length < n; i++) await p3.waitForTimeout(100); return pushes.length >= n; };
  await p3.goto(`http://localhost:${server.address().port}/`); await p3.waitForSelector('text=练习12');
  await p3.click('.tabbar button:has-text("设置")'); for (let i = 0; i < 3; i++) await p3.click('.about .logo');
  await p3.fill('.acct input >> nth=0', 'teacher@example.com'); await p3.fill('.acct input >> nth=1', 'pw123456'); await p3.click('.acct .btn1');
  await p3.waitForSelector('text=云端同步');
  check(await p3.isVisible('text=teacher@example.com') && await p3.isVisible('text=自动同步') && await p3.isVisible('text=历史版本'), '老师登录：云端同步（自动同步、未登录预览、历史版本）');
  check(await p3.isVisible('text=作业计分（改后历史积分自动重算）') && await p3.isVisible('.cell:has-text("新建班级")'), '老师登录：计分规则、班级管理');
  await p3.screenshot({ path: path.join(OUT, '20-teacher-settings.png'), fullPage: true });
  // 作业：点一下 已交 ↔ 未登记
  await p3.click('.tabbar button:has-text("首页")'); await p3.click('text=练习12'); await p3.waitForSelector('.stu');
  check(await p3.isVisible('text=剩余标为未交') || await p3.isVisible('.chip:has-text("多选")'), '作业页有登记工具（多选、剩余一键登记）');
  await p3.click('.stu:has-text("王芳")'); await p3.waitForSelector('.ed-toast:has-text("王芳：已交")');
  check(await p3.isVisible('.stu:has-text("王芳") >> text=已交'), '点一下：未交 → 已交（取消复审不合格）');
  check(await waitPush(1) && cls().assignments.find((a) => a.id === 'a11').statuses.s3 === 'SUBMITTED' && !cls().assignments.find((a) => a.id === 'a11').reviewFailed.includes('s3'), '改动几秒后自动上传到云端');
  check(pushes[0].p_base_version === 5, '上传带着云端版本号（防止覆盖别的设备的改动）');
  // 撤销
  await p3.click('.stu:has-text("周杰")'); await p3.waitForSelector('.ed-toast:has-text("周杰")');
  const before = await p3.textContent('.stu:has-text("周杰") .stu-st');
  await p3.click('.ed-toast button:has-text("撤销")'); await p3.waitForTimeout(200);
  check((await p3.textContent('.stu:has-text("周杰") .stu-st')) !== before, '可以撤销刚才的修改');
  // 长按 / 右键：登记面板
  await p3.click('.stu:has-text("林静")', { button: 'right' }); await p3.waitForSelector('.ed-sheet');
  check(await p3.isVisible('.ed-sheet button:has-text("复审不合格")') && await p3.isVisible('.ed-sheet button:has-text("抄袭…")') && await p3.isVisible('text=保存评语'), '长按 / 右键：状态、复审、抄袭、评语');
  await p3.click('.ed-sheet .ed-st button:has-text("优秀")');
  check(await p3.isVisible('.stu:has-text("林静") >> text=优秀'), '登记面板：改为优秀');
  await p3.click('.stu:has-text("吴磊")', { button: 'right' }); await p3.click('.ed-sheet button:has-text("抄袭…")'); await p3.click('.ed-sheet .chip:has-text("赵敏")'); await p3.click('.ed-sheet .btn1');
  check(await p3.isVisible('.stu:has-text("吴磊") >> text=抄袭 赵敏') && await p3.isVisible('.stu:has-text("赵敏") >> text=提供给 吴磊 抄'), '登记面板：抄袭（抄袭者和提供者都记未交）');
  await p3.click('.stu:has-text("李子涵")', { button: 'right' }); await p3.fill('.ed-sheet textarea', '书写工整'); await p3.click('.ed-sheet button:has-text("保存评语")');
  check(await p3.isVisible('.stu:has-text("李子涵") >> text=书写工整'), '登记面板：评语');
  // 多选
  await p3.click('.chip:has-text("多选")'); await p3.click('.stu:has-text("张锦宇")'); await p3.click('.stu:has-text("郑怡")');
  check(await p3.isVisible('.ed-selbar >> text=已选 2 人'), '多选：选中 2 人');
  await p3.click('.ed-selbar button:has-text("请假")');
  check(await p3.isVisible('.stu:has-text("张锦宇") >> text=请假') && await p3.isVisible('.stu:has-text("郑怡") >> text=请假'), '多选：统一登记为请假');
  await p3.screenshot({ path: path.join(OUT, '21-teacher-assignment.png'), fullPage: true });
  await p3.click('.ed-selbar button:has-text("完成")');
  // 编辑作业信息
  await p3.click('.ib[aria-label="编辑作业"]'); await p3.fill('.ed-sheet input >> nth=0', '练习12（订正）'); await p3.click('.ed-sheet .btn1:has-text("保存")');
  check(await p3.isVisible('h1:has-text("练习12（订正）")'), '修改作业名称');
  await p3.goBack(); await p3.waitForSelector('.tabbar');
  // 新建作业：长期请假的同学自动记请假
  await p3.click('.ib[aria-label="新建作业"]'); await p3.fill('.ed-sheet input >> nth=0', '周末作业'); await p3.click('.ed-sheet .chip:has-text("英语")'); await p3.click('.ed-sheet .btn1');
  await p3.waitForSelector('h1:has-text("周末作业")');
  check((await p3.locator('.stu').count()) === 12 && await p3.isVisible('text=未登记 12'), '新建作业并打开');
  await p3.click('.chip:has-text("剩余标为已交")');
  check(await p3.isVisible('text=已交 12'), '剩余一键标为已交');
  await p3.goBack(); await p3.waitForSelector('.tabbar');
  // 名单：添加同学、修改、加减分、删除加减分
  await p3.click('.tabbar button:has-text("名单")'); await p3.click('.ib[aria-label="添加同学"]');
  await p3.fill('.ed-sheet input >> nth=0', '新同学'); await p3.fill('.ed-sheet input >> nth=1', '13'); await p3.fill('.ed-sheet input >> nth=2', '第2组'); await p3.click('.ed-sheet .btn1');
  check(await p3.isVisible('.cell:has-text("新同学")'), '添加同学');
  await p3.click('.ib[aria-label="添加同学"]'); await p3.fill('.ed-sheet input >> nth=1', '13');
  check(await p3.isVisible('text=这个学号已经有人用了') && await p3.isDisabled('.ed-sheet .btn1'), '学号重复时不能保存');
  await p3.click('.ed-sheet .guide-skip');
  await p3.click('.cell:has-text("新同学")'); await p3.waitForSelector('text=积分流水');
  await p3.click('.ed-btns button:has-text("加减分")'); await p3.click('.ed-sheet .chip:has-text("5")'); await p3.click('.ed-sheet .chip:has-text("主动回答")'); await p3.click('.ed-sheet .btn1');
  check(await p3.isVisible('.cell:has-text("主动回答") >> text=+5'), '学生档案：加分（快捷理由）');
  await p3.click('.cell:has-text("主动回答")'); await p3.click('.ed-sheet .btn1.danger');
  check(!(await p3.isVisible('.cell:has-text("主动回答")')), '删除一条加减分');
  await p3.click('.ib[aria-label="修改同学"]'); await p3.click('.ed-sheet .sw[aria-label="组长"]'); await p3.click('.ed-sheet .btn1');
  check(await p3.isVisible('text=第2组组长'), '设为组长');
  await p3.click('.ib[aria-label="修改同学"]'); await p3.click('.ed-sheet .btn2.danger-t'); await p3.click('.ed-sheet .btn1.danger');
  await p3.waitForSelector('.tabbar'); check(!(await p3.isVisible('.cell:has-text("新同学")')), '删除同学');
  // 积分：多选加分、全班积分重置、撤销重置
  await p3.click('.tabbar button:has-text("积分")'); await p3.click('.ed-tools .chip:has-text("多选")');
  await p3.click('.cell:has-text("胡晨")'); await p3.click('.cell:has-text("赵敏")'); await p3.click('.ed-selbar button:has-text("加减分")');
  await p3.click('.ed-sheet .seg button:has-text("扣分")'); await p3.click('.ed-sheet .chip:has-text("2")'); await p3.fill('.ed-sheet input >> nth=1', '值日'); await p3.click('.ed-sheet .btn1');
  await p3.click('.ed-selbar button:has-text("完成")');
  await p3.click('.ed-tools .chip:has-text("全班积分重置")'); await p3.fill('.ed-sheet input >> nth=0', '80'); await p3.fill('.ed-sheet input >> nth=1', '期中重新计分'); await p3.click('.ed-sheet .btn1');
  check(await p3.isVisible('.tile:has-text("平均分") >> text=80') || await p3.isVisible('.score:has-text("80")'), '全班积分重置为 80');
  await p3.click('.seg button:has-text("加减分记录")');
  check(await p3.isVisible('text=胡晨 · 值日') && await p3.isVisible('text=赵敏 · 值日'), '多选扣分写进流水');
  await p3.click('.cell:has-text("积分重置为 80")'); await p3.click('.ed-sheet .btn1.danger');
  check(!(await p3.isVisible('.cell:has-text("积分重置为 80")')), '撤销积分重置');
  // 考试：新建、录入成绩、缺考
  await p3.click('.tabbar button:has-text("首页")'); await p3.click('.tile:has-text("考试")'); await p3.click('.ib[aria-label="新建考试"]');
  await p3.fill('.ed-sheet input >> nth=0', '单元测验'); await p3.fill('.ed-sheet input >> nth=2', '50'); await p3.click('.ed-sheet .btn1');
  await p3.waitForSelector('h1:has-text("单元测验")'); await p3.click('.seg button:has-text("录入成绩")');
  await p3.fill('input[aria-label="张锦宇 的成绩"]', '48'); await p3.press('input[aria-label="张锦宇 的成绩"]', 'Enter');
  await p3.fill('input[aria-label="李子涵 的成绩"]', '30'); await p3.press('input[aria-label="李子涵 的成绩"]', 'Enter');
  await p3.click('.ed-exam:has-text("林静") .chip');
  await p3.click('.seg button:has-text("成绩排名")');
  check(await p3.isVisible('text=成绩排名 · 2 人') && await p3.isVisible('text=缺考 · 1 人'), '考试：录入成绩、缺考');
  await p3.goBack(); await p3.goBack(); await p3.waitForSelector('.tabbar');
  // 计分规则：初始分改为 0，积分立即重算
  await p3.click('.tabbar button:has-text("设置")');
  await p3.fill('input[aria-label="初始分"]', '0'); await p3.press('input[aria-label="初始分"]', 'Enter');
  await p3.click('.tabbar button:has-text("积分")'); const avg0 = Number(await p3.textContent('.tile:has-text("平均分") b'));
  check(avg0 < 20, `改初始分后积分重算（平均 ${avg0}）`);
  // 新建班级
  await p3.click('.tabbar button:has-text("设置")'); await p3.click('.cell:has-text("新建班级")'); await p3.fill('.ed-sheet input', '高一(4)班'); await p3.click('.ed-sheet .btn1');
  await p3.click('.tabbar button:has-text("首页")'); check(await p3.isVisible('h1:has-text("高一(4)班")'), '新建班级并切换过去');
  const n0 = pushes.length; await waitPush(n0 + 1);
  const d = lastPush();
  check(d.classes.length === 2 && d.settings.rules.initial === 0 && cls().exams.some((e) => e.title === '单元测验' && e.scores.s0 === 48 && e.absent.includes('s2')), '所有改动都上传到云端（班级、规则、考试）');
  check(!JSON.stringify(d).includes('apiKey":"sk'), '上传的数据不含 AI Key');
  // 冲突：别的设备先改过 → 自动重试后上传
  conflictOnce = true; await p3.click('.ib[aria-label="新建作业"]'); await p3.fill('.ed-sheet input >> nth=0', '冲突测试'); await p3.click('.ed-sheet .btn1');
  const n1 = pushes.length; check(await waitPush(n1 + 1), '云端版本变了：重新拉取后再上传');
  // 历史版本、修改密码
  await p3.goBack(); await p3.waitForSelector('.tabbar'); await p3.click('.tabbar button:has-text("设置")');
  await p3.click('.cell:has-text("历史版本")'); await p3.waitForSelector('text=v4');
  check(await p3.isVisible('.ed-row:has-text("安卓")'), '历史版本列表'); await p3.click('.ed-sheet .guide-skip');
  await p3.click('.cell:has-text("修改密码")'); await p3.fill('.ed-sheet input >> nth=0', 'pw123456'); await p3.fill('.ed-sheet input >> nth=1', 'newpw123'); await p3.fill('.ed-sheet input >> nth=2', 'newpw123'); await p3.click('.ed-sheet .btn1');
  await p3.waitForSelector('text=密码已修改'); check(pwChanges.some((x) => x.password === 'newpw123'), '老师可以修改密码'); await p3.click('.ed-sheet .btn1');
  await p3.screenshot({ path: path.join(OUT, '22-teacher-settings-after.png'), fullPage: true });
  // 退出登录：先上传，再回到预览，本机数据清掉
  await p3.click('.cell:has-text("修改密码")'); await p3.click('.ed-sheet .guide-skip');
  await p3.click('.cell:has-text("退出登录")'); await p3.waitForSelector('.cell:has-text("修改密码")', { state: 'detached' });
  await p3.click('.tabbar button:has-text("首页")'); await p3.waitForSelector('text=练习12');
  check(await p3.evaluate(() => !localStorage.getItem('hw.data')) && !(await p3.isVisible('.ib[aria-label="新建作业"]')), '退出登录后回到只读预览，本机数据清掉');
  await ctx3.close();
  check(errors.length === 0, `没有脚本错误${errors.length ? '：' + errors.join(' | ') : ''}`);
  await browser.close(); server.close();
  console.log(failures ? `\n${failures} 项失败` : '\n全部通过');
  process.exit(failures ? 1 : 0);
})().catch((e) => { console.error(e); if (globalThis.pageErrors?.length) console.error('页面错误：', globalThis.pageErrors.join(' | ')); process.exit(1); });
