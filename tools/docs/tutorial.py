# 生成《作业通新手使用教程》PDF：python3 tools/docs/tutorial.py 截图目录 网页截图目录 输出.pdf
# 截图目录为 CI “UI screenshots” 的 shots/（light_*.png），网页截图为 tools/web-tests/e2e.cjs 的输出
from reportlab.lib.pagesizes import A4
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle, KeepTogether, Image, PageBreak
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib import colors
from reportlab.lib.units import mm
from PIL import Image as PImage
import sys, os

pdfmetrics.registerFont(TTFont('Hei', '/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc', subfontIndex=0))
BLUE = colors.HexColor('#2F6BFF'); INK = colors.HexColor('#1C1D22'); MUTED = colors.HexColor('#6B7280'); SOFT = colors.HexColor('#F2F5FB'); LINE = colors.HexColor('#DDE3EC')
def S(size=10.5, color=INK, lead=None, align=0, space=0):
    return ParagraphStyle('s', fontName='Hei', fontSize=size, leading=lead or size * 1.55, textColor=color, alignment=align, spaceAfter=space, wordWrap='CJK')
cover = S(30, INK, 40, 1); cover2 = S(13, MUTED, 20, 1); h1 = S(16, BLUE, 24, 0, 2); h2 = S(12, INK, 18, 0, 1)
body = S(10.5); note = S(9.5, MUTED); cap = S(8.5, MUTED, 12, 1); cell = S(10, INK, 14)

shots, web, out = sys.argv[1], sys.argv[2], sys.argv[3]
def shot(name, w=50):
    p = os.path.join(shots, f'light_{name}.png') if not name.startswith('web:') else os.path.join(web, name[4:] + '.png')
    if not os.path.exists(p): return None
    im = PImage.open(p); ratio = im.height / im.width
    h = w * ratio
    if h > 118: h = 118; w = h / ratio
    return Image(p, width=w * mm, height=h * mm)
def row(items):
    """items: [(截图名, 说明)]，一行 2–3 张"""
    cells = [[shot(n) or Paragraph('（截图）', cap) for n, _ in items], [Paragraph(c, cap) for _, c in items]]
    t = Table(cells, colWidths=[170 / len(items) * mm] * len(items))
    t.setStyle(TableStyle([('ALIGN', (0, 0), (-1, -1), 'CENTER'), ('VALIGN', (0, 0), (-1, -1), 'TOP'), ('TOPPADDING', (0, 0), (-1, -1), 2), ('BOTTOMPADDING', (0, 0), (-1, -1), 2)]))
    return KeepTogether([t, Spacer(1, 3 * mm)])
def bullets(lines): return [Paragraph('· ' + l, body) for l in lines]
def table(rows, widths):
    t = Table([[Paragraph(str(c), cell) for c in r] for r in rows], colWidths=[w * mm for w in widths], hAlign='LEFT')
    t.setStyle(TableStyle([('GRID', (0, 0), (-1, -1), 0.5, LINE), ('BACKGROUND', (0, 0), (-1, 0), SOFT), ('VALIGN', (0, 0), (-1, -1), 'MIDDLE'),
                           ('TOPPADDING', (0, 0), (-1, -1), 4), ('BOTTOMPADDING', (0, 0), (-1, -1), 4), ('LEFTPADDING', (0, 0), (-1, -1), 6)]))
    return t

doc = SimpleDocTemplate(out, pagesize=A4, leftMargin=20*mm, rightMargin=20*mm, topMargin=18*mm, bottomMargin=18*mm, title='作业通新手使用教程', author='作业通')
E = []
# ---------- 封面 ----------
E += [Spacer(1, 60*mm), Paragraph('作业通', cover), Spacer(1, 4*mm), Paragraph('新手使用教程', S(22, BLUE, 30, 1)), Spacer(1, 8*mm),
      Paragraph('收作业 · 记积分 · 看统计', cover2), Paragraph('适用于 v2.14.0 · 安卓 / iPad / 网页', cover2), Spacer(1, 30*mm),
      table([['章节', '内容'],
             ['一、认识作业通', '三端分工、安装与更新'], ['二、第一次打开', '新手引导、登录与同步'], ['三、班级与名单', '添加学生、小组、组长、课代表'],
             ['四、登记作业', '新建作业、登记状态、复审、识别与语音'], ['五、积分', '自动计分、加减分、重置、结算、积分流水'], ['六、考试', '录入成绩、缺考、按分数档加减分'],
             ['七、统计与报告', '作业 / 考试 / 关注 / 小组分析、股票模式、导出'], ['八、设置', '计分规则、外观、数据、更新'], ['九、iPad 与网页版', '各自能做什么'], ['十、常见问题', '']], [55, 115]),
      PageBreak()]

# ---------- 一 ----------
E += [Paragraph('一、认识作业通', h1),
      Paragraph('作业通是给老师和课代表用的收作业、记积分工具。三个版本共用同一份云端数据：', body),
      table([['版本', '能做什么', '获取方式'],
             ['安卓（主力）', '全部功能：登记作业、积分、考试、结算、统计、小组、股票模式、导出', 'GitHub 发布页下载 HwCounter-arm64-v8a-*.apk 安装；之后自动提示更新'],
             ['iPad', '登记作业、积分、名单、热力图、设置；积分算法与安卓一致', '发布页下载 HwCounter-iPad-unsigned-*.ipa，用 AltStore / Sideloadly / 爱思助手 签名安装'],
             ['网页', '只能查看：作业、名单、积分、统计、小组，适合分享给同学和家长', '浏览器打开 deepshawn.github.io/hwcounter，可“添加到主屏幕”']], [26, 82, 62]),
      Spacer(1, 2*mm),
      Paragraph('<b>自动更新</b>：安卓 2.8.0 以后，打开 App 会自动检查新版本，Wi-Fi 下后台下载，下好后提示“立即安装”。第一次安装更新时需要允许“安装未知应用”。', body),
      Spacer(1, 4*mm)]

# ---------- 二 ----------
E += [Paragraph('二、第一次打开', h1),
      *bullets(['第一次打开会出现<b>新手引导</b>，左右滑动或点“下一步”查看，随时可以点右上角“跳过”。以后可以在 设置 → 帮助 → 新手引导 再看。',
                '没有登录时是<b>预览模式</b>：只能查看老师公开的数据，不能修改。到 设置 → 账号与云同步 登录后即可编辑，改动几秒后自动上传，多台设备之间同步。',
                '如果两台设备都改过数据，会提示选择“合并 / 用云端 / 用本机”，推荐合并。']),
      Spacer(1, 2*mm),
      row([('00a_onboarding', '新手引导（可跳过）'), ('00b_onboarding', '左右滑动查看各项功能'), ('59_settings_account', '设置 → 账号与云同步')]),
      PageBreak()]

# ---------- 三 ----------
E += [Paragraph('三、班级与名单', h1),
      *bullets(['首页右上角可以切换 / 新建班级。',
                '“名单”页添加学生：学号、姓名、小组、性别、备注；也可以从 JSON、粘贴文本导入，按学号排序。',
                '在“名单 → 小组”里打开某个小组，<b>长按同学</b>可以“设为组长”或“设为课代表”。课代表每次结算 +2，可以同时是组长。',
                '学生档案里能看到积分、名次、各状态次数、关注度和个人分析报告。']),
      row([('03_roster', '名单'), ('06_group_detail', '小组详情：组长、组员、整组加减分'), ('60_student', '学生档案')]),
      Spacer(1, 2*mm)]

# ---------- 四 ----------
E += [Paragraph('四、登记作业', h1),
      *bullets(['首页点“新建作业”，填写名称和科目。长期请假的同学会自动标为请假。',
                '进入作业页：<b>点一下</b>同学在“已交 / 未登记”之间切换；<b>长按</b>可设为优秀、迟交、未交、请假、<b>复审不合格</b>，以及加减分、写备注。',
                '<b>复审不合格</b>：记一次未交，这一次扣分翻倍；没有复审的默认合格。',
                '“多选”可以一次登记一批；“剩余标未交”一键把没登记的同学标为未交。',
                '也可以用拍照识别、实时扫描、侧面学号、数本数、语音登记、首字母登记，或先在“草稿”里登记再上传。',
                '作业页可以切换卡片、列表、座位、分组四种排版。']),
      row([('01_home', '首页：最近作业与快捷登记'), ('07a_assignment', '作业页：复审不合格显示为紫红色'), ('07b_assignment', '下滑查看全部同学')]),
      PageBreak()]

# ---------- 五 ----------
E += [Paragraph('五、积分', h1),
      Paragraph('积分全部<b>自动计算</b>，三端算法一致。默认规则（都可以在设置里调整）：', body),
      table([['项目', '默认规则'],
             ['初始积分', '100'],
             ['每次作业', '按时交 0、优秀 +1、迟交 -1、请假 0'],
             ['未交', '连续未交 2、4、8……逐次翻倍；本轮累计超 6 次后 3、9、27……；单次最多 30'],
             ['复审不合格', '记一次未交，这一次扣分 ×2'],
             ['连续交奖励', '本轮连续按时交满 5 次后，每次再 +1'],
             ['考试', '得分率 ≥90% +2、80–90% +1、<60% -1'],
             ['组长奖惩', '每周看小组人均作业扣分与各组平均比较，每周最多 ±5'],
             ['小组排名', '每周按综合分排名：第 1 名全组每人 +2、第 2 名 +1、最后一名 -1'],
             ['取整', '最终积分正分向上、负分向下取整']], [30, 140]),
      Spacer(1, 2*mm),
      *bullets(['“积分”页点同学可以加减分；右上角“多选”批量加减分；菜单里有<b>积分重置</b>（只重置积分，历史全部保留）、<b>每月定期重置</b>、<b>结算本轮</b>、<b>积分流水</b>。',
                '<b>结算</b>：按本轮交齐度、缺交、进步、小组和综合表现加减分，结算后按学号列出每人结算分，方便登记。',
                '<b>积分流水</b>记录每一笔变化（作业、复审、组长、小组排名、考试、结算、重置），可导出 Excel / PDF。']),
      row([('10_scores', '积分榜'), ('19_ledger', '积分流水'), ('17_settle', '结算本轮')]),
      PageBreak()]

# ---------- 六 ----------
E += [Paragraph('六、考试', h1),
      *bullets(['首页“考试”进入考试列表，新建考试后录入成绩：可以手动输入、粘贴 / 导入表格，或拍照让 AI 识别成绩表。',
                '多选同学可以“记为缺考”；单场考试可以关闭自动加减分。',
                '考试详情有排名、分数分布、与上一场对比等分析。']),
      row([('40_exams', '考试列表'), ('41_exam_detail', '考试详情与排名'), ('42_exam_detail', '分数分析')]),
      Spacer(1, 2*mm)]

# ---------- 七 ----------
E += [Paragraph('七、统计与报告', h1),
      *bullets(['统计页有<b>作业、考试、关注、小组</b>四个栏目，作业分析可以按全部、本轮、近 10 次或年 / 季 / 月 / 周 / 日 / 自定义范围查看。',
                '<b>关注</b>：关注度指数（0–100，越高越需要关注）排行；切到“股票”可以用行情的方式看个人关注度和<b>小组综合分</b>。',
                '<b>小组</b>：综合分对抗排名、本期 / 历次排名、每周对抗、进步与退步、两组对决、组长奖惩汇总。',
                '右上角分享：一键导出 PDF 分析报告、Excel；学生档案里有个人分析报告。']),
      row([('20_stats', '作业分析'), ('34a_groups', '小组对抗排名'), ('39a_group_stock', '股票模式 · 小组板块')]),
      PageBreak()]

# ---------- 八 ----------
E += [Paragraph('八、设置', h1),
      Paragraph('设置首页是账号卡片和分组入口，点进去调整：', body),
      table([['入口', '可以设置'],
             ['账号与云同步', '登录、注册、同步状态、历史版本、修改密码'],
             ['作业计分', '基础分值、未交递增扣分、作业复审、组长奖惩、小组对抗排名、连续交奖励、取整、导出计分规则通知'],
             ['考试计分', '是否按分数档加减分、各档分值、缺考'],
             ['结算规则', '各档结算分、附加项、扩大区分度、课代表加分、定期重置前自动结算'],
             ['识别引擎与 AI', '本地 / AI / 混合识别、学号位数、AI 服务商与模型'],
             ['主题与显示', '配色、深色模式、圆角、排版、界面动效、液态玻璃、低性能模式'],
             ['数据与备份', '备份全部数据、从备份恢复、清空'],
             ['软件更新', '检查更新、自动检查 / 自动下载 / 仅 Wi-Fi'],
             ['帮助', '重新查看新手引导']], [32, 138]),
      Spacer(1, 2*mm),
      Paragraph('<b>组长奖惩、小组对抗排名</b>都可以单独关闭，也可以把“加减方式”改成“仅加分”或“仅扣分”。修改计分规则后，历史积分会自动按新规则重算。', body),
      row([('50_settings', '设置首页'), ('53_settings_scoring', '作业计分'), ('55_settings_scoring', '组长奖惩与小组排名')]),
      PageBreak()]

# ---------- 九 ----------
E += [Paragraph('九、iPad 与网页版', h1),
      *bullets(['<b>iPad</b>：左侧栏有作业、热力图、积分、名单、设置；可以登记作业（含复审不合格）、加减分、积分重置、管理名单；积分与安卓完全一致。小组分析、股票模式、结算、考试录入请在安卓上使用。',
                '<b>网页版</b>：只能查看，没有登录入口和修改按钮；可以看作业、名单与学生档案（含积分流水）、积分榜、统计（作业 / 考试 / 小组 / 热力图）。设置里只有本机的个性化显示。',
                '网页版适合发给同学和家长查询积分。只有老师在 设置 → 账号与云同步 打开“允许未登录预览”后才能看到数据。']),
      row([('web:1-home', '网页版首页'), ('web:4-student', '网页版学生档案'), ('web:7b-groups', '网页版小组排名')]),
      Spacer(1, 2*mm)]

# ---------- 十 ----------
E += [Paragraph('十、常见问题', h1),
      table([['问题', '解答'],
             ['为什么不能修改？', '没有登录时是预览模式（只读）。到 设置 → 账号与云同步 登录。网页版本身就只能查看。'],
             ['积分怎么突然变了？', '修改计分规则、复审、组长奖惩、小组排名都会让历史积分按新规则重算。打开学生的“积分流水”能看到每一笔的原因。'],
             ['不想要小组排名 / 组长奖惩', '设置 → 作业计分，关闭对应开关，或改成仅加分 / 仅扣分。'],
             ['各设备积分不一样？', '请把所有设备更新到最新版本；旧版本不认识新规则。'],
             ['数据怎么备份？', '登录后会自动同步到云端，云端保留最近 30 个历史版本；也可以 设置 → 数据与备份 导出 JSON 文件。'],
             ['安装更新失败？', '第一次需要允许“安装未知应用”；网络不好时可以到发布页手动下载 APK。']], [42, 128]),
      Spacer(1, 6*mm), Paragraph('祝使用愉快！', S(12, BLUE, 18, 1))]

def footer(canvas, d):
    if d.page == 1: return
    canvas.saveState(); canvas.setFont('Hei', 8.5); canvas.setFillColor(MUTED)
    canvas.drawCentredString(A4[0] / 2, 10*mm, f'作业通新手使用教程 · 第 {d.page} 页'); canvas.restoreState()
doc.build(E, onFirstPage=footer, onLaterPages=footer)
print('ok')
