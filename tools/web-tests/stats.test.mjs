// 统计表识别 / 统计名单 / 草稿测试：node tools/web-tests/stats.test.mjs
import zlib from 'node:zlib';
import { parseStatus, splitTable, recognizeTable, recognizeLines, mergeRows, scanStatuses, columnStatuses, tally, statListSheets, classStatRows, readXlsx, makeDraft, draftChanges, applyDraft } from '../../web/js/stats.js';
import { xlsx } from '../../web/js/xlsx.js';

let pass = 0; let total = 0;
const check = (ok, msg) => { total++; if (ok) pass++; console.log(`${ok ? '✓' : '✗'} ${msg}`); };
const names = ['张锦宇', '李子涵', '林静', '王芳', '冉浩', '周杰'];
const students = names.map((n, i) => ({ id: `s${i + 1}`, no: String(i + 1).padStart(2, '0'), name: n }));
const nm = Object.fromEntries(students.map((s) => [s.id, s.name]));
const show = (r, col = 0) => r.rows.map((x) => `${nm[x.studentId]}:${x.cells[col]}`).join(' ');

// 1) 标记
const marks = { '✓': 'SUBMITTED', '√': 'SUBMITTED', '×': 'MISSING', 'X': 'MISSING', '优': 'EXCELLENT', 'A': 'EXCELLENT', '迟交': 'LATE', '补': 'LATE', '请假': 'LEAVE', '病假': 'LEAVE', '': 'NONE', '-': 'NONE', '已交': 'SUBMITTED', '未交': 'MISSING', '1': 'SUBMITTED', '0': 'MISSING', 'MISSING': 'MISSING', '交(优)': 'EXCELLENT', 'ｘ': 'MISSING' };
const bad = Object.entries(marks).filter(([k, v]) => parseStatus(k) !== v);
check(!bad.length, `标记识别 ${Object.keys(marks).length} 种${bad.length ? '：错 ' + bad.map(([k]) => `${k}→${parseStatus(k)}`).join(' ') : ''}`);
check(parseStatus('85') === null && parseStatus('张三') === null, '分数、姓名不当成标记');

// 2) 粘贴 Excel：表头 + 学号 + 姓名 + 两次作业
let rows = splitTable('学号\t姓名\t10月30日 数学\t10月31日 语文\n01\t张锦宇\t✓\t×\n02\t李子涵\t×\t✓\n03\t林静\t优\t\n04\t王芳\t假\t假\n05\t冉浩\t迟\t✓\n李雷\t\t✓');
let r = recognizeTable(rows, students);
check(r.columns.length === 2 && r.columns[0].header === '10月30日 数学' && r.columns[1].header === '10月31日 语文', `识别出 2 个状态列：${r.columns.map((c) => c.header).join(' | ')}`);
check(show(r) === '张锦宇:SUBMITTED 李子涵:MISSING 林静:EXCELLENT 王芳:LEAVE 冉浩:LATE', `第 1 列：${show(r)}`);
check(show(r, 1) === '张锦宇:MISSING 李子涵:SUBMITTED 林静:NONE 王芳:LEAVE 冉浩:SUBMITTED', `第 2 列：${show(r, 1)}`);
check(r.unmatched.length === 1 && r.unmatched[0].includes('李雷'), `名单外：${r.unmatched}`);
check(r.missingStudents.join() === 's6', `表里没有的同学：${r.missingStudents.map((x) => nm[x])}`);

// 3) 没有表头、只有姓名 + 全是“1”的状态列（不能把“1”当学号）
rows = splitTable('张锦宇,1\n李子涵,1\n林静,0\n王芳,1');
r = recognizeTable(rows, students);
check(r.columns.length === 1 && show(r) === '张锦宇:SUBMITTED 李子涵:SUBMITTED 林静:MISSING 王芳:SUBMITTED', `全是 1/0 的列：${show(r)}`);

// 4) 只有学号 + 文字状态，空格分隔
rows = splitTable('1 已交\n2 未交\n3 请假\n6 迟交');
r = recognizeTable(rows, students);
check(show(r) === '张锦宇:SUBMITTED 李子涵:MISSING 林静:LEAVE 周杰:LATE', `学号（不带前导 0）：${show(r)}`);

// 5) 带引号 CSV
rows = splitTable('"学号","姓名","状态"\n"01","张锦宇","已交"\n"02","李子涵","未交"');
r = recognizeTable(rows, students);
check(show(r) === '张锦宇:SUBMITTED 李子涵:MISSING', `带引号 CSV：${show(r)}`);

// 6) 拍照：OCR 文字行（框线擦掉后，一行 = 学号 姓名 标记；两栏排版一行两个人）
const lines = [
  { text: '作业登记表 高一(3)班', y: 10 },
  { text: '01 张锦宇 √', y: 50 }, { text: '02 李子涵 X', y: 90 },
  { text: '03 林静 优   04 王芳 假', y: 130 }, { text: '05冉浩迟交', y: 170 },
];
r = recognizeLines(lines, students);
check(show(r) === '张锦宇:SUBMITTED 李子涵:MISSING 林静:EXCELLENT 王芳:LEAVE 冉浩:LATE', `拍照识别：${show(r)}`);
check(r.unmatched.length === 1, `拍照：标题行不算学生（${r.unmatched}）`);
const frag = mergeRows([{ text: '√', x: 300, y: 52, h: 20 }, { text: '张锦宇', x: 60, y: 50, h: 24 }, { text: '×', x: 400, y: 49, h: 20 }, { text: '李子涵', x: 60, y: 92, h: 24 }, { text: '迟', x: 300, y: 95, h: 20 }]);
check(frag.map((x) => x.text).join(' / ') === '张锦宇 √ × / 李子涵 迟', `OCR 碎块按行拼接：${frag.map((x) => x.text).join(' / ')}`);
r = recognizeLines(frag, students);
check(r.columns.length === 2 && show(r, 1) === '张锦宇:MISSING 李子涵:NONE', `碎块 → 两列：${show(r, 1)}`);
check(scanStatuses('√ × 优 迟').join() === 'SUBMITTED,MISSING,EXCELLENT,LATE', '一行多个标记 → 多列');

// 7) 统计 + 统计名单工作簿
const st = columnStatuses(recognizeTable(splitTable('张锦宇\t✓\n李子涵\t×\n林静\t优\n王芳\t假'), students), 0);
const t = tally(students, st);
check(t.handedIn === 2 && t.expected === 5 && t.counts.NONE === 2 && t.counts.MISSING === 1, `统计：已交 ${t.handedIn}/${t.expected}，未登记 ${t.counts.NONE}`);
const sheets = statListSheets({ className: '高一(3)班', title: '练习册', subject: '数学', date: '2026年10月31日', students, statuses: st, comments: { s2: '没带' } });
check(sheets.map((s) => s.name).join() === '统计汇总,分类名单,明细', '工作簿：统计汇总 / 分类名单 / 明细');
const cat = sheets[1].rows;
check(cat[0][3].v === '未交（1）' && cat[1][3].v === '02 李子涵', `分类名单：${cat[0][3].v} ${cat[1][3].v}`);
check(sheets[2].rows[2][4] === '没带', '明细带备注');
const cls = classStatRows({ students, assignments: [{ createdAt: 1, subject: '数学', title: 'A', statuses: st }] }, () => 'd');
check(cls[1][7] === '李子涵' && cls[1][10] === '冉浩、周杰', `全班各次统计：未交 ${cls[1][7]}，未登记 ${cls[1][10]}`);

// 8) 读 .xlsx：本软件导出的（不压缩）和 Excel 生成的（deflate 压缩、共享字符串）
const blob = xlsx([{ name: '表', rows: [['学号', '姓名', '交'], ['01', '张锦宇', '✓'], ['02', '李子涵', { v: '×', s: 3 }], [3, '林静', '']] }]);
let xr = await readXlsx(await blob.arrayBuffer());
check(JSON.stringify(xr) === JSON.stringify([['学号', '姓名', '交'], ['01', '张锦宇', '✓'], ['02', '李子涵', '×'], ['3', '林静']]), `读取自己导出的 xlsx：${JSON.stringify(xr)}`);
xr = await readXlsx(deflatedXlsx());
check(JSON.stringify(xr) === JSON.stringify([['姓名', '', '作业'], ['张锦宇', '', '√'], ['李子涵', '', '未交 & 补']]), `读取压缩 + 共享字符串 xlsx：${JSON.stringify(xr)}`);
let err = ''; try { await readXlsx(new Uint8Array([1, 2, 3])); } catch (e) { err = e.message; }
check(err.includes('xlsx'), `坏文件报错：${err}`);

// 9) 草稿：只覆盖改过的学生
const c = { id: 'c1', students: students.map((s) => ({ ...s, longLeave: s.id === 's6' })), assignments: [{ id: 'a1', title: '练习', subject: '数学', createdAt: 5, statuses: { s1: 'SUBMITTED', s2: 'MISSING' }, comments: {} }] };
let data = { classes: [c] };
const d1 = makeDraft(c, c.assignments[0]);
d1.statuses = { ...d1.statuses, s2: 'LATE', s3: 'SUBMITTED' }; delete d1.statuses.s1; d1.comments = { s3: '字迹工整' };
check(draftChanges(d1).sort().join() === 's1,s2,s3', `草稿改动：${draftChanges(d1)}`);
// 同时别的设备给 s4 登记了
data = { classes: [{ ...c, assignments: [{ ...c.assignments[0], statuses: { ...c.assignments[0].statuses, s4: 'EXCELLENT' } }] }] };
let out = applyDraft(data, d1).classes[0].assignments[0];
check(JSON.stringify(out.statuses) === JSON.stringify({ s2: 'LATE', s4: 'EXCELLENT', s3: 'SUBMITTED' }) && out.comments.s3 === '字迹工整', `上传草稿：${JSON.stringify(out.statuses)}（保留其它设备的 s4）`);
const d2 = makeDraft(c, null, { title: '新作业', subject: '语文', statuses: { s1: 'SUBMITTED' } });
out = applyDraft(data, d2).classes[0].assignments;
check(out.length === 2 && out[1].title === '新作业' && out[1].statuses.s6 === 'LEAVE' && out[1].statuses.s1 === 'SUBMITTED', '新作业草稿：上传时创建作业（长期请假自动标请假）');
err = ''; try { applyDraft({ classes: [] }, d2); } catch (e) { err = e.message; } check(err.includes('班级'), '班级被删 → 报错不丢草稿');

console.log(`\n${pass}/${total} 通过`);
process.exit(pass === total ? 0 : 1);

function deflatedXlsx() {
  const files = {
    'xl/workbook.xml': '<workbook xmlns:r="r"><sheets><sheet name="登记" sheetId="1" r:id="rId7"/></sheets></workbook>',
    'xl/_rels/workbook.xml.rels': '<Relationships><Relationship Id="rId7" Type="ws" Target="worksheets/sheet3.xml"/></Relationships>',
    'xl/sharedStrings.xml': '<sst><si><t>姓名</t></si><si><r><t>张</t></r><r><t xml:space="preserve">锦宇</t></r></si><si><t>李子涵</t></si><si><t>作业</t></si><si><t>√</t></si><si><t>未交 &amp; 补</t></si></sst>',
    'xl/worksheets/sheet3.xml': '<worksheet><sheetData><row r="1"><c r="A1" t="s"><v>0</v></c><c r="C1" t="s"><v>3</v></c></row><row r="2"><c r="A2" t="s"><v>1</v></c><c r="C2" t="s"><v>4</v></c></row><row r="3"><c r="A3" t="s"><v>2</v></c><c r="C3" t="s"><v>5</v></c></row><row r="4"/></sheetData></worksheet>',
  };
  const parts = []; const central = []; let off = 0;
  for (const [name, text] of Object.entries(files)) {
    const nb = Buffer.from(name); const raw = Buffer.from(text); const data = zlib.deflateRawSync(raw);
    const lh = Buffer.alloc(30); lh.writeUInt32LE(0x04034b50, 0); lh.writeUInt16LE(8, 8); lh.writeUInt32LE(data.length, 18); lh.writeUInt32LE(raw.length, 22); lh.writeUInt16LE(nb.length, 26);
    const ch = Buffer.alloc(46); ch.writeUInt32LE(0x02014b50, 0); ch.writeUInt16LE(8, 10); ch.writeUInt32LE(data.length, 20); ch.writeUInt32LE(raw.length, 24); ch.writeUInt16LE(nb.length, 28); ch.writeUInt32LE(off, 42);
    parts.push(lh, nb, data); central.push(ch, nb); off += 30 + nb.length + data.length;
  }
  const cs = central.reduce((n, b) => n + b.length, 0);
  const end = Buffer.alloc(22); end.writeUInt32LE(0x06054b50, 0); end.writeUInt16LE(central.length / 2, 8); end.writeUInt16LE(central.length / 2, 10); end.writeUInt32LE(cs, 12); end.writeUInt32LE(off, 16);
  return new Uint8Array(Buffer.concat([...parts, ...central, end]));
}
