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
  const browser = await chromium.launch();
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
  await page.click('.toolbar .chip:has-text("座位表")');
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

  // 8) 手机尺寸截图
  await page.setViewportSize({ width: 390, height: 844 });
  await page.click('.tabs button:has-text("首页")');
  await page.screenshot({ path: `${OUT}/6-phone-preview.png` });

  check(errors.length === 0, `页面无 JS 报错${errors.length ? '：' + errors.join(' | ') : ''}`);
  await browser.close(); server.close();
  console.log(failures ? `\n失败 ${failures} 项` : '\n全部通过');
  process.exit(failures ? 1 : 0);
})().catch((e) => { console.error(e); process.exit(1); });
