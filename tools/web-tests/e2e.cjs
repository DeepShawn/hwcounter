// 网页版端到端测试：本地起静态服务 + Playwright 拦截 Supabase 请求（内存模拟）
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

// ---------- 模拟 Supabase ----------
const UID = 'u-owner';
const seed = {
  version: 1, currentClassId: 'c1', settings: {},
  classes: [{ id: 'c1', name: '高一(3)班', emoji: '🚀', scores: [],
    students: [{ id: 's1', no: '34', name: '张锦宇' }, { id: 's2', no: '49', name: '李子涵' }, { id: 's3', no: '12', name: '王芳' }],
    assignments: [{ id: 'a1', title: '练习册 P32', subject: '数学', createdAt: Date.now() - 86400000, statuses: { s1: 'SUBMITTED' }, comments: {} }] }],
};
const db = { row: { user_id: UID, data: seed, version: 3, device: 'Android · test', updated_at: new Date().toISOString(), public_read: true }, pushes: [], patches: [] };
function mock(route) {
  const req = route.request(); const url = new URL(req.url()); const p = url.pathname + url.search; const auth = req.headers()['authorization'];
  const json = (status, body) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
  if (p.startsWith('/auth/v1/token?grant_type=password')) {
    const b = JSON.parse(req.postData()); if (b.password !== 'right-password') return json(400, { error: 'invalid_grant', error_description: 'Invalid login credentials' });
    return json(200, { access_token: 'tok', refresh_token: 'ref', expires_in: 3600, user: { id: UID, email: b.email } });
  }
  if (p.startsWith('/auth/v1/user') && req.method() === 'PUT') { db.newPassword = JSON.parse(req.postData()).password; return json(200, { id: UID }); }
  if (p.startsWith('/rest/v1/hw_snapshots') && req.method() === 'GET') {
    if (url.searchParams.get('public_read') === 'eq.true') return json(200, db.row.public_read && !auth ? [{ data: db.row.data, version: db.row.version, updated_at: db.row.updated_at }] : db.row.public_read ? [db.row] : []);
    if (!auth) return json(401, { message: 'JWT required' });
    return json(200, [db.row]);
  }
  if (p.startsWith('/rest/v1/hw_snapshots') && req.method() === 'PATCH') { const b = JSON.parse(req.postData()); db.patches.push(b); Object.assign(db.row, b); return route.fulfill({ status: 204 }); }
  if (p.startsWith('/rest/v1/rpc/hw_push')) {
    const b = JSON.parse(req.postData()); db.pushes.push(b.p_base_version);
    if (b.p_base_version !== -1 && b.p_base_version !== db.row.version) return json(400, { code: 'P0001', message: `CONFLICT:${db.row.version}` });
    db.row = { ...db.row, data: b.p_data, version: db.row.version + 1, device: b.p_device, updated_at: new Date().toISOString() };
    return json(200, [{ version: db.row.version, updated_at: db.row.updated_at }]);
  }
  return json(404, { message: `mock: ${p}` });
}

let failures = 0;
const check = (ok, msg) => { console.log(`${ok ? '✓' : '✗'} ${msg}`); if (!ok) failures++; };

(async () => {
  await new Promise((r) => server.listen(0, r));
  const base = `http://127.0.0.1:${server.address().port}/`;
  const browser = await chromium.launch(process.env.CHROMIUM ? { executablePath: process.env.CHROMIUM } : {});
  const ctx = await browser.newContext({ viewport: { width: 1024, height: 1366 }, deviceScaleFactor: 1, hasTouch: true, userAgent: 'Mozilla/5.0 (iPad; CPU OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1' });
  await ctx.route('https://beasxxexggnjcocdbtdl.supabase.co/**', mock);
  const page = await ctx.newPage();
  const errors = []; page.on('pageerror', (e) => errors.push(e.message)); page.on('console', (m) => { if (m.type() === 'error' && !m.text().startsWith('Failed to load resource')) errors.push(m.text()); });
  await page.goto(base);

  // 1) 未登录：预览
  await page.waitForSelector('text=练习册 P32', { timeout: 8000 });
  check((await page.inputValue('.class-select')) === 'c1', '未登录也能看到云端公开的班级');
  check(await page.isVisible('.ro-banner'), '未登录显示“预览模式”横幅');
  await page.screenshot({ path: `${OUT}/1-preview-home.png` });
  await page.click('text=练习册 P32');
  await page.waitForSelector('.grid .stu');
  const before = await page.textContent('.grid .stu:nth-child(2)');
  await page.click('.grid .stu:nth-child(2)');
  await page.waitForTimeout(300);
  check((await page.textContent('.grid .stu:nth-child(2)')) === before, '预览模式点学生不会改变状态');
  check(await page.isVisible('text=预览模式：登录后才能修改'), '预览模式点学生提示需要登录');
  check(!(await page.textContent('.hero')).includes(':'), '作业日期不显示具体时间');
  check(await page.getByRole('button', { name: '☑️ 多选', exact: true }).count() === 0, '预览模式不显示作业多选操作');
  await page.screenshot({ path: `${OUT}/2-preview-assignment.png` });
  await page.click('.hero .icon-btn');

  // 预览模式：快捷“语音登记”不添加作业
  check((await page.locator('.qa').count()) === 0, '预览模式不显示快捷登记/新建作业按钮');

  // 预览模式：不显示名单页，热力图照常显示
  check(!(await page.isVisible('.tabs button:has-text("名单")')), '预览模式不显示“名单”页');
  await page.click('.tabs button:has-text("热力图")');
  await page.waitForSelector('.heat');
  const cells = await page.locator('.heat .heat-cell').count();
  check(cells === 3 && (await page.textContent('.heat tbody tr:first-child')).includes('张锦宇'), `预览模式热力图正常显示（${cells} 格）`);
  await page.screenshot({ path: `${OUT}/2b-preview-heatmap.png` });
  await page.click('.heat .heat-cell');
  await page.waitForSelector('.grid .stu');
  check(true, '点热力图格子进入该次作业');
  await page.click('.hero .icon-btn');
  await page.click('.tabs button:has-text("首页")');

  // 2) 登录：密码错误提示中文
  await page.click('.tabs button:has-text("设置")');
  await page.fill('input[type=email]', 'teacher@example.com');
  await page.fill('input[type=password]', 'wrong-pass');
  await page.click('button:has-text("登录")');
  await page.waitForSelector('text=邮箱或密码不对');
  check(true, '密码错误显示“邮箱或密码不对”');
  await page.fill('input[type=password]', 'right-password');
  await page.click('button:has-text("登录")');
  await page.waitForSelector('text=teacher@example.com');
  await page.waitForTimeout(800);
  check(!(await page.isVisible('.ro-banner')), '登录后预览横幅消失');
  check(db.patches.some((x) => x.public_read === true), '首次同步默认开放未登录预览');
  await page.screenshot({ path: `${OUT}/3-settings-logged-in.png` });

  // 3) 登录后可编辑 + 自动上传
  await page.click('.tabs button:has-text("首页")');
  // 快捷“语音登记”：没有今天的作业时先弹出新建框，不自动添加作业
  const n0 = await page.locator('.card.assign').count();
  await page.click('.qa:has-text("语音登记")');
  await page.waitForSelector('.modal-title:has-text("新建作业")');
  await page.click('.modal .icon-btn');
  await page.waitForTimeout(300);
  check((await page.locator('.card.assign').count()) === n0, '快捷语音登记不再自动添加作业（取消后作业数不变）');
  await page.click('text=练习册 P32');
  await page.click('.grid .stu:nth-child(2)');
  await page.waitForTimeout(300);
  check((await page.textContent('.grid .stu:nth-child(2)')).includes('已交'), '登录后点学生 → 已交');
  const v0 = db.row.version;
  await page.waitForTimeout(5000);
  check(db.row.version === v0 + 1 && db.row.data.classes[0].assignments[0].statuses.s2 === 'SUBMITTED', `改动自动上传到云端（v${v0} → v${db.row.version}）`);
  check(db.row.data.settings.ai.apiKey === '', '上传的数据不含 API Key');

  // 4) 语音登记（文字输入走同一解析）：首字母 ZJY / 音近学号
  await page.click('.toolbar .chip:has-text("语音登记")'); // 登录后才显示
  await page.selectOption; await page.click('.chips .chip:has-text("请假")');
  await page.fill('textarea', '这几样 是十二号');
  await page.waitForTimeout(1300);
  const voiceText = await page.textContent('main');
  check(voiceText.includes('张锦宇') && voiceText.includes('首字母'), '语音：“这几样” → 张锦宇（首字母）');
  check(voiceText.includes('王芳') && voiceText.includes('听到：12'), '语音：“是十二号” → 12 号王芳');
  await page.screenshot({ path: `${OUT}/4-voice.png` });
  await page.click('.topbar .icon-btn');

  // 5) 冲突：云端被别的设备改了，本机也改了
  db.row = { ...db.row, version: db.row.version + 1, data: { ...db.row.data, classes: [{ ...db.row.data.classes[0], students: [...db.row.data.classes[0].students, { id: 's9', no: '50', name: '新同学' }] }] } };
  await page.click('.grid .stu:nth-child(3)');
  await page.waitForSelector('text=两台设备都改过数据', { timeout: 9000 });
  check(true, '两边都改过 → 弹出冲突对话框');
  await page.screenshot({ path: `${OUT}/5-conflict.png` });
  const pushesBefore = db.pushes.length;
  await page.click('button:has-text("合并（推荐）")');
  await page.waitForTimeout(1500);
  const merged = db.row.data.classes[0];
  check(merged.students.some((s) => s.id === 's9') && merged.assignments[0].statuses.s1 === 'LEAVE' && db.pushes.slice(pushesBefore).length === 1, '合并后上传：云端同时保留“其它设备”加的新同学和本机的语音登记');
  await page.click('.hero .icon-btn');

  // 5b) 座位表：粘贴导入 → 交换座位 → 作业里按排登记；输入拼音首字母登记
  await page.click('.tabs button:has-text("名单")');
  await page.click('button:has-text("座位表")');
  await page.fill('.modal textarea', '讲台\n张锦宇\t李子涵\n王芳\t空');
  await page.click('.modal button:has-text("解析并排座")');
  await page.waitForSelector('.modal .seat-grid');
  const seatNames = async () => (await page.locator('.modal .seat-grid .seat').allTextContents()).map((x) => x.replace(/\d+/g, '').trim() || '空');
  check((await seatNames()).join(' ') === '张锦宇 李子涵 王芳 空', `粘贴导入座位表：${(await seatNames()).join(' ')}`);
  await page.screenshot({ path: `${OUT}/5b-seating-editor.png` });
  await page.locator('.modal .seat-grid .seat').nth(2).click();
  await page.locator('.modal .seat-grid .seat').nth(3).click();
  check((await seatNames()).join(' ') === '张锦宇 李子涵 空 王芳', '点两个座位互换（含空位）');
  await page.click('.modal .icon-btn');
  await page.click('.tabs button:has-text("首页")');
  await page.click('text=练习册 P32');
  await page.waitForSelector('.seat-grid');
  check(true, '有座位表时作业默认按座位表显示');
  await page.click('.seat-head:has-text("1排")');
  await page.waitForSelector('.pickbar');
  check((await page.textContent('.pickbar')).includes('已选 2 人'), '点“1排”选中整排 2 人');
  await page.click('.pickbar button:has-text("优秀")');
  await page.waitForTimeout(200);
  check((await page.locator('.seat-grid .seat').nth(0).textContent()).includes('优秀') && (await page.locator('.seat-grid .seat').nth(1).textContent()).includes('优秀'), '整排登记为优秀');
  await page.screenshot({ path: `${OUT}/5c-seat-assign.png` });
  await page.fill('.typed input', 'wf');
  await page.selectOption('.typed select', 'LATE');
  await page.click('.typed button');
  await page.waitForTimeout(200);
  check((await page.locator('.seat-grid .seat').nth(3).textContent()).includes('迟交'), '输入拼音首字母 wf → 王芳 迟交');
  await page.fill('.typed input', 'zjy lzh');
  await page.selectOption('.typed select', 'MISSING');
  await page.click('.typed button');
  await page.waitForTimeout(200);
  check((await page.locator('.seat-grid .seat').nth(0).textContent()).includes('未交') && (await page.locator('.seat-grid .seat').nth(1).textContent()).includes('未交'), '空格连续输入 zjy lzh → 两人都登记');
  await page.click('.hero .icon-btn');

  // 作业名单：卡片模式也可多选，选择本身不改登记，批量加减分独立于作业状态。
  await page.click('text=练习册 P32');
  await page.click('.toolbar .chip:visible:has-text("座位表")');
  const assignmentBefore = await page.evaluate(() => JSON.parse(localStorage.getItem('hw.data')).classes[0]);
  const assignmentStudent = (name) => page.locator('.assignment-page .grid .stu').filter({ hasText: name });
  await page.getByRole('button', { name: '☑️ 多选', exact: true }).click();
  check(await page.getByRole('button', { name: '批量加减分', exact: true }).isDisabled(), '作业多选未选人时不可批量加减分');
  await assignmentStudent('张锦宇').click(); await assignmentStudent('李子涵').click();
  await assignmentStudent('张锦宇').click(); await assignmentStudent('张锦宇').click();
  check(await page.locator('.assignment-page .stu[aria-pressed=true]').count() === 2 && JSON.stringify((await page.evaluate(() => JSON.parse(localStorage.getItem('hw.data')).classes[0])).assignments) === JSON.stringify(assignmentBefore.assignments), '作业卡片点选只改变选择，不改变提交状态');
  await page.setViewportSize({ width: 390, height: 844 });
  await page.screenshot({ path: `${OUT}/5d-assignment-multi-phone.png` });
  check(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), '手机作业多选操作栏不会横向溢出');
  await page.setViewportSize({ width: 1024, height: 1366 });
  await page.getByRole('button', { name: '批量加减分', exact: true }).click();
  check((await page.inputValue('.modal input[placeholder="原因"]')) === '作业：练习册 P32', '作业批量积分自动带上作业名称');
  await page.getByRole('button', { name: '取消', exact: true }).last().click();
  check(await page.locator('.assignment-page .stu[aria-pressed=true]').count() === 2, '取消作业批量积分后保留选择');
  await page.getByRole('button', { name: '批量加减分', exact: true }).click();
  await page.fill('.modal input[type=number]', '-1.5');
  await page.getByRole('button', { name: '确定', exact: true }).click();
  await page.waitForSelector('.modal', { state: 'hidden' });
  const assignmentScored = await page.evaluate(() => JSON.parse(localStorage.getItem('hw.data')).classes[0]);
  const assignmentAdded = assignmentScored.scores.slice(assignmentBefore.scores.length);
  check(assignmentAdded.length === 2 && assignmentAdded.every((e) => e.delta === -1.5 && e.reason === '作业：练习册 P32') && assignmentAdded.map((e) => e.studentId).sort().join() === 's1,s2', '作业批量扣分只影响所选学生，每人一条流水');
  check(JSON.stringify(assignmentScored.assignments) === JSON.stringify(assignmentBefore.assignments), '额外加减分保留作业状态和未交次数');
  await page.click('.toast button:has-text("撤销")');
  await page.getByRole('button', { name: '☑️ 多选', exact: true }).click();
  await page.fill('.assignment-page input[placeholder="搜索"]', '张');
  await page.getByRole('button', { name: '全选当前筛选', exact: true }).click();
  check(await page.locator('.assignment-page .stu[aria-pressed=true]').count() === 1, '作业全选只选择当前筛选的学生');
  await page.locator('.assignment-pickbar').getByRole('button', { name: '优秀', exact: true }).click();
  const assignmentRegistered = await page.evaluate(() => JSON.parse(localStorage.getItem('hw.data')).classes[0]);
  check(assignmentRegistered.assignments[0].statuses.s1 === 'EXCELLENT' && assignmentRegistered.assignments[0].statuses.s2 === assignmentBefore.assignments[0].statuses.s2, '作业批量登记只修改所选学生');
  await page.click('.toast button:has-text("撤销")');
  await page.locator('.assignment-pickbar').getByRole('button', { name: '取消', exact: true }).click();
  await page.click('.hero .icon-btn');

  // 5d) 点击多选 → 批量加减分；取消、零分校验和整批撤销
  await page.click('.tabs button:has-text("积分")');
  const readClass = () => page.evaluate(() => JSON.parse(localStorage.getItem('hw.data')).classes[0]);
  const scoreRow = (name) => page.locator('.score-row').filter({ hasText: name });
  const initialClass = await readClass();
  const originalPodium = await page.locator('.podium').textContent();
  await page.getByRole('button', { name: '按学号', exact: true }).click();
  check((await page.locator('.score-row .grow > b').allTextContents()).join('|') === '王芳|张锦宇|李子涵|新同学', '积分可按学号从小到大排列');
  check(await page.locator('.podium').textContent() === originalPodium && (await page.locator('.score-row .idx').allTextContents()).join() === '2,3,4,1', '切换学号排序保留真实积分名次及前三名');
  check(JSON.stringify((await readClass()).scores) === JSON.stringify(initialClass.scores), '切换排序不修改积分记录');
  await page.getByRole('button', { name: '☑️ 多选', exact: true }).click();
  check(await page.getByRole('button', { name: '批量加减分', exact: true }).isDisabled(), '未选学生时不能批量加减分');
  await scoreRow('张锦宇').click(); await scoreRow('王芳').click();
  await page.getByRole('button', { name: '按积分', exact: true }).click();
  check(await scoreRow('张锦宇').getAttribute('aria-pressed') === 'true' && await scoreRow('王芳').getAttribute('aria-pressed') === 'true', '多选时切换排序仍保留选中学生');
  await scoreRow('张锦宇').click(); await scoreRow('张锦宇').click();
  check((await page.textContent('.pickbar')).includes('已选 2 人') && await page.locator('.score-row[aria-pressed=true]').count() === 2, '点学生切换勾选，重复点选不会重复计数');
  await page.setViewportSize({ width: 390, height: 844 });
  await page.screenshot({ path: `${OUT}/5d-phone-batch-scores.png` });
  check(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), '手机多选操作栏不会横向溢出');
  await page.getByRole('button', { name: '批量加减分', exact: true }).click();
  check((await page.textContent('.modal .score-targets')).includes('张锦宇') && (await page.textContent('.modal .score-targets')).includes('王芳') && !(await page.textContent('.modal .score-targets')).includes('李子涵'), '确认窗口仅展示选中学生');
  await page.getByRole('button', { name: '取消', exact: true }).last().click();
  check(JSON.stringify((await readClass()).scores) === JSON.stringify(initialClass.scores) && await page.locator('.score-row[aria-pressed=true]').count() === 2, '取消打分保留选择且不写入积分');
  await page.getByRole('button', { name: '批量加减分', exact: true }).click();
  await page.fill('.modal input[type=number]', '0');
  check(await page.getByRole('button', { name: '确定', exact: true }).isDisabled(), '零分不可提交');
  await page.fill('.modal input[type=number]', '2');
  await page.fill('.modal input[placeholder="原因"]', '批量课堂表现');
  await page.getByRole('button', { name: '确定', exact: true }).click();
  await page.waitForSelector('.modal', { state: 'hidden' });
  let scored = await readClass(); let added = scored.scores.slice(initialClass.scores.length);
  check(added.length === 2 && added.every((e) => e.delta === 2 && e.reason === '批量课堂表现') && added.map((e) => e.studentId).sort().join() === 's1,s3', '统一加2分只影响两位选中学生，各写一条流水');
  check(JSON.stringify(scored.assignments) === JSON.stringify(initialClass.assignments) && !(await page.locator('.pickbar').count()), '批量打分保留作业状态并退出多选');
  await page.click('.toast button:has-text("撤销")');
  check(JSON.stringify((await readClass()).scores) === JSON.stringify(initialClass.scores), '一次撤销整批加分');
  await page.getByRole('button', { name: '☑️ 多选', exact: true }).click();
  await page.getByRole('button', { name: '全选', exact: true }).click();
  check(await page.locator('.score-row[aria-pressed=true]').count() === initialClass.students.length, '全选选中全班');
  await page.getByRole('button', { name: '清空选择', exact: true }).click();
  check(await page.locator('.score-row[aria-pressed=true]').count() === 0, '清空选择不影响积分');
  await scoreRow('张锦宇').click(); await scoreRow('王芳').click();
  await page.getByRole('button', { name: '批量加减分', exact: true }).click();
  await page.fill('.modal input[type=number]', '-1.5');
  await page.getByRole('button', { name: '确定', exact: true }).click();
  await page.waitForSelector('.modal', { state: 'hidden' });
  scored = await readClass(); added = scored.scores.slice(initialClass.scores.length);
  check(added.length === 2 && added.every((e) => e.delta === -1.5 && e.reason === '扣分'), '支持统一扣小数分及默认理由');

  // 5e) 基准100改0：保留手动积分和未交次数；重置分数保留历史
  const beforeBase = await scoreRow('张锦宇').locator('.score').textContent();
  const beforeMissing = await scoreRow('张锦宇').locator('.muted').textContent();
  await page.getByRole('button', { name: '修改基准分', exact: true }).click();
  await page.fill('.modal input[type=number]', '');
  check(await page.getByRole('button', { name: '保存', exact: true }).isDisabled(), '空白基准不能保存');
  await page.fill('.modal input[type=number]', '0');
  await page.getByRole('button', { name: '保存', exact: true }).click();
  await page.waitForSelector('.modal', { state: 'hidden' });
  check(Number(await scoreRow('张锦宇').locator('.score').textContent()) === Number(beforeBase) - 100 && JSON.stringify((await readClass()).scores) === JSON.stringify(scored.scores), '基准100改0仅平移积分，原始加减分流水完整保留');
  check(await scoreRow('张锦宇').locator('.muted').textContent() === beforeMissing, '修改基准保留未交次数');
  await page.click('.toast button:has-text("撤销")');
  check(await scoreRow('张锦宇').locator('.score').textContent() === beforeBase, '修改基准可撤销');
  const unselectedScore = await scoreRow('王芳').locator('.score').textContent();
  await page.getByRole('button', { name: '☑️ 多选', exact: true }).click();
  await scoreRow('张锦宇').click(); await scoreRow('李子涵').click();
  await page.getByRole('button', { name: '重置分数', exact: true }).click();
  await page.fill('.modal input[type=number]', '0');
  await page.getByRole('button', { name: '确认重置', exact: true }).click();
  await page.waitForSelector('.modal', { state: 'hidden' });
  let reset = await readClass();
  check(await scoreRow('张锦宇').locator('.score').textContent() === '0' && await scoreRow('李子涵').locator('.score').textContent() === '0' && await scoreRow('王芳').locator('.score').textContent() === unselectedScore, '重置选中学生至0分，未选中的学生不变');
  check(JSON.stringify(reset.scores.slice(0, scored.scores.length)) === JSON.stringify(scored.scores) && JSON.stringify(reset.assignments) === JSON.stringify(initialClass.assignments) && await scoreRow('张锦宇').locator('.muted').textContent() === beforeMissing, '重置保留原有加减分、作业备注及未交次数');
  await page.click('.toast button:has-text("撤销")');
  check(JSON.stringify((await readClass()).scores) === JSON.stringify(scored.scores), '重置可整批撤销');
  await page.getByRole('button', { name: '重置全班分数', exact: true }).click();
  await page.fill('.modal input[type=number]', '0');
  await page.getByRole('button', { name: '确认重置', exact: true }).click();
  await page.waitForSelector('.modal', { state: 'hidden' });
  reset = await readClass();
  check((await page.locator('.score-row .score').allTextContents()).every((v) => v === '0') && reset.scores.slice(scored.scores.length).every((e) => e.reason === '重置分数至 0'), '全班重置至0分，并新增可追溯的重置流水');
  await page.setViewportSize({ width: 1024, height: 1366 });
  await page.screenshot({ path: `${OUT}/5e-reset-scores.png` });
  await page.waitForTimeout(5000);
  check(JSON.stringify(db.row.data.classes[0].scores) === JSON.stringify(reset.scores), '批量积分及重置流水自动同步');
  await page.reload();
  await page.click('.tabs button:has-text("积分")');
  check((await page.locator('.score-row .score').allTextContents()).every((v) => v === '0') && JSON.stringify((await readClass()).scores) === JSON.stringify(reset.scores), '刷新后重置分数及原有流水保留');

  // 5c) 统计表识别（粘贴）→ 存为新作业草稿 → 只在本机 → 上传
  await page.click('.tabs button:has-text("首页")');
  await page.waitForTimeout(4500); // 等上一步的自动同步完成
  const cloudAssigns = db.row.data.classes[0].assignments.length; const vDraft0 = db.row.version;
  await page.click('.qa:has-text("统计表识别")');
  await page.click('button:has-text("📋 粘贴")');
  await page.fill('.modal textarea', '学号\t姓名\t11月1日 默写\n34\t张锦宇\t√\n49\t李子涵\t×\n12\t王芳\t优\n50\t新同学\t假\n99\t路人甲\t√');
  await page.click('.modal button:has-text("识别")');
  await page.waitForSelector('.stat-sum');
  const sumText = await page.textContent('.stat-sum');
  check(sumText.includes('认出 4 人') && sumText.includes('1 行没对上') && sumText.includes('未交：李子涵'), '统计表识别：认出 4 人、1 行名单外、未交名单');
  check(sumText.includes('交齐率 67%'), `统计：2/3 交齐率 67%（请假不计应交）`);
  await page.screenshot({ path: `${OUT}/5d-stat-table.png` });
  const [dl] = await Promise.all([page.waitForEvent('download'), page.click('button:has-text("导出统计名单 Excel")')]);
  const dlPath = `${OUT}/stat-list.xlsx`; await dl.saveAs(dlPath);
  const { readXlsx } = await import('../../web/js/stats.js');
  const wbRows = await readXlsx(fs.readFileSync(dlPath));
  check(wbRows.some((r) => r[0] === '未交' && r[3] === '李子涵'), `导出统计名单 Excel（${dl.suggestedFilename()}）`);
  await page.click('button:has-text("存为新作业草稿")');
  await page.waitForSelector('.modal-title:has-text("编辑作业")');
  check((await page.inputValue('.modal input')) === '11月1日 默写', '表头自动作为作业名');
  await page.click('.modal button:has-text("保存")');
  await page.waitForSelector('.draft-hero');
  check((await page.textContent('.draft-hero')).includes('只保存在本机'), '进入草稿页');
  await page.click('.seat-grid .seat:has-text("李子涵")');
  await page.waitForTimeout(5500);
  check(db.row.data.classes[0].assignments.length === cloudAssigns && db.row.version === vDraft0, '草稿不会自动上传到云端');
  const stored = await page.evaluate(() => JSON.parse(localStorage.getItem('hw.drafts')));
  check(stored.length === 1 && stored[0].statuses.s2 === 'SUBMITTED' && stored[0].statuses.s9 === 'LEAVE', '草稿保存在本机（含刚才的修改）');
  await page.screenshot({ path: `${OUT}/5e-draft.png` });
  await page.click('.draft-bar button:has-text("上传")');
  await page.waitForTimeout(1500);
  const up = db.row.data.classes[0].assignments.find((a) => a.title === '11月1日 默写');
  check(up && up.statuses.s1 === 'SUBMITTED' && up.statuses.s2 === 'SUBMITTED' && up.statuses.s3 === 'EXCELLENT' && db.row.version > vDraft0, '上传草稿 → 云端出现新作业和登记');
  check((await page.evaluate(() => JSON.parse(localStorage.getItem('hw.drafts')))).length === 0, '上传后草稿删除');

  // 5d) 已有作业 → 在草稿里登记 → 定时上传
  await page.waitForSelector('text=练习册 P32');
  await page.click('text=练习册 P32');
  await page.click('.toolbar .chip:has-text("在草稿里登记")');
  await page.waitForSelector('.draft-hero');
  await page.click('.toolbar .chip:visible:has-text("座位表")');
  await page.click('.grid .stu:has-text("王芳")');
  const before5d = db.row.data.classes[0].assignments.find((a) => a.id === 'a1').statuses.s3;
  await page.click('.draft-bar button:has-text("定时上传")');
  await page.fill('.modal input[type=datetime-local]', '2020-01-01T08:00');
  await page.click('.modal button:has-text("确定")');
  check((await page.textContent('.draft-bar')).includes('自动上传'), '设置定时上传');
  await page.click('.hero .icon-btn');
  check(await page.isVisible('.draft-note'), '作业页提示有未上传的草稿');
  await page.click('.hero .icon-btn');
  check((await page.textContent('.qa:has-text("草稿箱")')).includes('1'), '首页草稿箱显示 1 份');
  await page.waitForFunction(() => !(JSON.parse(localStorage.getItem('hw.drafts')) || []).length, null, { timeout: 20000 });
  await page.waitForTimeout(1500);
  const after5d = db.row.data.classes[0].assignments.find((a) => a.id === 'a1').statuses.s3;
  check(before5d !== after5d && after5d === (!before5d || before5d === 'MISSING' ? 'SUBMITTED' : undefined), `到时间自动上传（王芳 ${before5d} → ${after5d}）`);

  // 6) 修改密码（旧密码校验 + 两次新密码一致）
  await page.click('.tabs button:has-text("设置")');
  await page.click('button:has-text("修改密码")');
  await page.fill('.modal input[placeholder="旧密码"]', 'wrong');
  await page.fill('.modal input[placeholder^="新密码"]', 'new-pass-1');
  await page.fill('.modal input[placeholder="再输一次新密码"]', 'new-pass-1');
  await page.click('.modal button:has-text("确认修改")');
  await page.waitForSelector('.modal .err:has-text("旧密码不对")');
  check(!db.newPassword, '旧密码错误 → 提示且不提交新密码');
  await page.fill('.modal input[placeholder="旧密码"]', 'right-password');
  await page.click('.modal button:has-text("确认修改")');
  await page.waitForSelector('text=密码已修改');
  check(db.newPassword === 'new-pass-1', '旧密码正确 → 新密码提交到 Supabase');

  // 7) 退出 → 回到预览
  await page.click('button:has-text("退出登录")');
  await page.waitForSelector('.ro-banner');
  check(true, '退出登录后回到预览模式');
  check(!(await page.isVisible('.tabs button:has-text("名单")')), '退出后“名单”页隐藏');
  await page.click('.tabs button:has-text("积分")');
  check(await page.getByRole('button', { name: '☑️ 多选', exact: true }).count() === 0 && await page.getByRole('button', { name: '重置全班分数', exact: true }).count() === 0 && await page.getByRole('button', { name: '修改基准分', exact: true }).count() === 0, '预览模式隐藏批量打分、重置和修改基准入口');

  // 8) 手机尺寸截图
  await page.setViewportSize({ width: 390, height: 844 });
  await page.click('.tabs button:has-text("首页")');
  await page.screenshot({ path: `${OUT}/6-phone-preview.png` });

  check(errors.length === 0, `页面无 JS 报错${errors.length ? '：' + errors.join(' | ') : ''}`);
  // 独立账号上下文：按作业重置、撤销、重开、标线、历史恢复与云端往返。
  const periodData = {version:1,currentClassId:'period-class',settings:{},classes:[{
    id:'period-class',name:'重置回归班',emoji:'📚',students:[{id:'p1',no:'01',name:'张三'},{id:'p2',no:'02',name:'李四'}],
    assignments:[
      {id:'old',title:'上轮作业',subject:'数学',createdAt:1000,statuses:{p1:'MISSING',p2:'MISSING'},comments:{p1:'备注保留'},pinned:true},
      {id:'start',title:'本轮第一回',subject:'数学',createdAt:2000,statuses:{p1:'EXCELLENT',p2:'MISSING'},comments:{}},
      {id:'next',title:'本轮第二回',subject:'语文',createdAt:3000,statuses:{p1:'LATE',p2:'SUBMITTED'},comments:{}}
    ],scores:[{id:'old-manual',studentId:'p1',delta:3,reason:'上轮表现',time:1500},{id:'new-manual',studentId:'p1',delta:2,reason:'本轮表现',time:2500}]
  }]};
  db.row = {...db.row,data:periodData,version:30};
  const pc = await browser.newContext({viewport:{width:1024,height:900}});
  await pc.route('https://beasxxexggnjcocdbtdl.supabase.co/**', mock);
  await pc.addInitScript(() => localStorage.setItem('hw.cloud.session', JSON.stringify({access_token:'tok',refresh_token:'ref',expires_at:Date.now()+3600000,email:'period@test.local',user_id:'u-owner'})));
  const pp = await pc.newPage(); pp.on('pageerror',e=>errors.push(e.message));
  await pp.goto(base); await pp.waitForSelector('.assign:has-text("上轮作业")');
  const localClass = () => pp.evaluate(() => JSON.parse(localStorage.getItem('hw.data')).classes[0]);
  await pp.click('.tabs button:has-text("积分")');
  await pp.waitForSelector('.score-row');
  const score = name => pp.locator('.score-row:visible').filter({hasText:name}).locator('.score').textContent();
  check(await score('张三') === '103' && (await pp.locator('.score-row:visible').filter({hasText:'李四'}).textContent()).includes('缺2'), '重置前积分与历史未交次数正确');
  const applyPeriod = async keep => {
    await pp.getByRole('button',{name:'按作业重置积分',exact:true}).click();
    await pp.getByLabel('计分起点作业').selectOption('start');
    await pp.locator('.modal input[type=checkbox]').setChecked(keep);
    await pp.getByRole('button',{name:'应用积分起点',exact:true}).click();
    await pp.waitForSelector('.score-period-notice:visible');
  };
  await pp.getByRole('button',{name:'按作业重置积分',exact:true}).click();
  await pp.getByLabel('计分起点作业').selectOption('start');
  await pp.locator('.modal').getByRole('button',{name:'取消',exact:true}).click();
  check(!(await localClass()).scoreResets.length, '取消积分起点不改数据');
  await applyPeriod(true);
  check(await score('张三') === '102' && await score('李四') === '98', '所选作业含本次，起点后的手动分继续计入');
  check((await pp.locator('.score-row:visible').filter({hasText:'李四'}).textContent()).includes('缺2'), '积分重置不清除历史未交次数');
  const afterPeriod = await localClass();
  check(JSON.stringify(afterPeriod.assignments.map(a=>[a.id,a.statuses,a.comments])) === JSON.stringify(periodData.classes[0].assignments.map(a=>[a.id,a.statuses,a.comments])) && afterPeriod.scores.length === 2, '积分重置完整保留原作业、状态、备注及手动分记录');
  await pp.locator('.toast').getByRole('button',{name:'撤销',exact:true}).click();
  check(await score('张三') === '103' && !(await localClass()).scoreResets.length, '撤销积分起点可还原分数');
  await applyPeriod(true);
  await pp.click('.tabs button:has-text("首页")');
  const cards = await pp.locator('.assign:visible').allTextContents();
  check(cards.length === 3 && cards.at(-1).includes('上轮作业') && await pp.getByRole('separator',{name:'积分重置分界'}).isVisible(), '作业列表保留全部作业，历史置顶作业也位于重置分割线之后');
  await pp.screenshot({path:`${OUT}/7-score-period-assignments.png`});
  await pp.click('.tabs button:has-text("热力图")');
  await pp.waitForSelector('.heat');
  check(await pp.locator('.heat .heat-cell').count() === 6 && await pp.locator('.heat th.score-boundary').count() === 1 && await pp.locator('.heat td.score-boundary').count() === 2, '热力图保留全部历史格，起点表头及每个学生行均有分割线');
  await pp.screenshot({path:`${OUT}/8-score-period-heatmap.png`});
  await pp.waitForTimeout(5500);
  check(db.row.data.classes[0].scoreResets?.at(-1)?.assignmentId === 'start', '积分起点随既有 Supabase JSON 快照同步');
  await pp.reload(); await pp.click('.tabs button:has-text("积分")'); await pp.waitForSelector('.score-row');
  check(await score('张三') === '102' && await pp.locator('.score-period-notice:visible').count() === 1, '刷新并从云端读取后计分范围保留');
  await applyPeriod(false); check(await score('张三') === '100', '可选择仅保留手动分流水、不结转旧手动分');
  await pp.getByRole('button',{name:'按作业重置积分',exact:true}).click();
  await pp.getByRole('button',{name:'恢复全部历史计分',exact:true}).click();
  check(await score('张三') === '103', '恢复全部历史计分还原原分数');
  await pp.click('.tabs button:has-text("热力图")');
  check(await pp.locator('.heat .score-boundary').count() === 0 && await pp.locator('.heat .heat-cell').count() === 6, '恢复历史后分割线撤除，原热力图记录完整');
  check(errors.length === 0, '积分重置全流程无脚本错误');
  await pc.close();
  await browser.close(); server.close();
  console.log(failures ? `\n失败 ${failures} 项` : '\n全部通过');
  process.exit(failures ? 1 : 0);
})().catch((e) => { console.error(e); process.exit(1); });
