// 座位表测试：node tools/web-tests/seating.test.mjs
import { parseSeatingText, seatingFromBoxes, seatingToGrid, autoArrange, rotateSeating, swapSeats, resizeSeating } from '../../web/js/seating.js';

let pass = 0; let total = 0;
const check = (ok, msg) => { total++; if (ok) pass++; console.log(`${ok ? '✓' : '✗'} ${msg}`); };
const names = ['张锦宇', '李子涵', '林静', '王芳', '冉浩', '周杰', '陈浩宏', '胡晨', '赵敏', '孙悦', '吴磊', '郑怡'];
const students = names.map((n, i) => ({ id: `s${i + 1}`, no: String(i + 1).padStart(2, '0'), name: n }));
const nameOf = Object.fromEntries(students.map((s) => [s.id, s.name]));
const show = (seat) => seatingToGrid(seat, students).map((r) => r.map((id) => (id ? nameOf[id] : '空')).join(' ')).join(' / ');

// 1) 文字：Tab 分隔、空位、讲台在上
let r = parseSeatingText('讲台\n张锦宇\t李子涵\t\t林静\n王芳\t冉浩\t周杰\t空\n陈浩宏\t08\t\t', students);
check(show(r.seating) === '张锦宇 李子涵 空 林静 / 王芳 冉浩 周杰 空 / 陈浩宏 胡晨 空 空' && r.seating.frontAtTop, `Tab 分隔 + 空位 + 学号：${show(r.seating)}`);
check(r.unseated.length === 4, `未入座 ${r.unseated.length} 人`);
// 2) 空格分隔、讲台在下
r = parseSeatingText('张锦宇 李子涵 林静\n王芳 冉浩 周杰\n   讲  台   ', students);
check(!r.seating.frontAtTop && show(r.seating) === '张锦宇 李子涵 林静 / 王芳 冉浩 周杰', `讲台在下：${show(r.seating)}`);
// 3) 认不出的名字
r = parseSeatingText('张锦宇,李雷,林静', students);
check(r.unmatched.join() === '李雷' && show(r.seating) === '张锦宇 空 林静', `未匹配：${r.unmatched}`);
// 4) JSON
r = parseSeatingText('{"frontAtTop":false,"seats":[["01","02"],[null,"王芳"]]}', students);
check(show(r.seating) === '张锦宇 李子涵 / 空 王芳' && !r.seating.frontAtTop, `JSON：${show(r.seating)}`);
// 5) 图片识别结果：每人一个框，带轻微倾斜，讲台在下方
const boxes = []; const layout = [['张锦宇', '李子涵', null, '林静'], ['王芳', '冉浩', '周杰', '陈浩宏'], ['胡晨', null, '赵敏', '孙悦']];
layout.forEach((row, i) => row.forEach((n, j) => { if (n) boxes.push({ text: n, x: 100 + j * 220 + (j >= 2 ? 60 : 0), y: 100 + i * 150 + j * 6, w: 90, h: 40 }); }));
boxes.push({ text: '讲 台', x: 400, y: 620, w: 120, h: 50 });
boxes.push({ text: '第一组', x: 100, y: 40, w: 80, h: 30 });
r = seatingFromBoxes(boxes, students);
check(show(r.seating) === '张锦宇 李子涵 空 林静 / 王芳 冉浩 周杰 陈浩宏 / 胡晨 空 赵敏 孙悦' && !r.seating.frontAtTop, `图片（逐人框 + 倾斜 + 讲台在下）：${show(r.seating)}`);
// 6) 图片识别结果：一整排被读成一行
r = seatingFromBoxes([
  { text: '讲台', x: 300, y: 10, w: 100, h: 40 },
  { text: '张锦宇  李子涵  林静  王芳', x: 50, y: 100, w: 600, h: 40 },
  { text: '冉浩    周杰    陈浩宏  胡晨', x: 50, y: 200, w: 600, h: 40 },
], students);
check(show(r.seating) === '张锦宇 李子涵 林静 王芳 / 冉浩 周杰 陈浩宏 胡晨' && r.seating.frontAtTop, `图片（整排一行）：${show(r.seating)}`);
// 6b) 真实 OCR 输出（Tesseract 识别去框线后的照片）：标题里的“(3)”不能当学号、认错一个字也能对上、两人粘成一行能拆开
r = seatingFromBoxes([
  { text: '高一(3)班座位表', x: 764, y: 65, w: 292, h: 45 },
  { text: '张锦宇李子涵', x: 284, y: 194, w: 376, h: 63, charX: [295, 330, 365, 545, 590, 635] },
  { text: '林静', x: 1173, y: 216, w: 84, h: 42 }, { text: '王芬', x: 1425, y: 222, w: 84, h: 42 },
  { text: '冉洗', x: 301, y: 332, w: 84, h: 42 }, { text: '周杰', x: 555, y: 338, w: 82, h: 42 },
  { text: '陈浩宏', x: 898, y: 345, w: 125, h: 43 }, { text: '一', x: 682, y: 411, w: 13, h: 1 },
  { text: '胡晨', x: 1423, y: 355, w: 82, h: 42 }, { text: '赵敏', x: 299, y: 466, w: 83, h: 42 },
  { text: 'ES', x: 917, y: 481, w: 82, h: 40 }, { text: '郑怡', x: 1168, y: 485, w: 84, h: 42 },
  { text: '孙悦', x: 551, y: 473, w: 84, h: 40 }, { text: '讲台', x: 846, y: 658, w: 99, h: 46 },
], students);
check(show(r.seating) === '张锦宇 李子涵 空 林静 王芳 / 冉浩 周杰 陈浩宏 空 胡晨 / 赵敏 孙悦 空 郑怡 空' && !r.seating.frontAtTop, `真实 OCR 输出：${show(r.seating)}`);
// 7) 自动排列、旋转、交换、改尺寸
let s = autoArrange(students, 5);
check(s.rows === 3 && show(s).startsWith('张锦宇 李子涵 林静 王芳 冉浩 / 周杰'), `按学号自动排列：${show(s)}`);
const rot = rotateSeating(s);
check(!rot.frontAtTop && show(rot).startsWith('空 空 空 郑怡 吴磊'), `讲台换边（旋转 180°）：${show(rot)}`);
s = swapSeats(s, 0, 14);
check(show(s).startsWith('空 李子涵') && show(s).endsWith('张锦宇'), '交换座位（含空位）');
s = resizeSeating(s, 2, 4);
check(s.rows === 2 && s.cols === 4 && Object.keys(s.seats).length === 7, `缩小到 2×4 后入座 ${Object.keys(s.seats).length} 人`);
console.log(`\n通过 ${pass}/${total}`);
process.exit(pass === total ? 0 : 1);
