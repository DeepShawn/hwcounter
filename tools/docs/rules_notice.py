# 生成《计分规则调整通知》PDF：python3 tools/docs/rules_notice.py docs/计分规则调整通知.pdf
from reportlab.lib.pagesizes import A4
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle, KeepTogether
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib import colors
from reportlab.lib.units import mm
import sys

pdfmetrics.registerFont(TTFont('Hei', '/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc', subfontIndex=0))
BLUE = colors.HexColor('#2F6BFF'); INK = colors.HexColor('#1C1D22'); MUTED = colors.HexColor('#6B7280')
SOFT = colors.HexColor('#F2F5FB'); LINE = colors.HexColor('#DDE3EC'); NEW = colors.HexColor('#D93A4C')
def S(size=10.5, color=INK, lead=None, align=0, space=0):
    return ParagraphStyle('s', fontName='Hei', fontSize=size, leading=lead or size * 1.5, textColor=color, alignment=align, spaceAfter=space, wordWrap='CJK')
title = S(20, INK, 26, 1); sub = S(10.5, MUTED, 16, 1); h = S(13.5, BLUE, 20); body = S(10.5); note = S(9.5, MUTED); cell = S(10, INK, 14)

def table(rows, widths, head=True):
    data = [[Paragraph(str(c), cell) for c in r] for r in rows]
    t = Table(data, colWidths=[w * mm for w in widths], hAlign='LEFT')
    st = [('GRID', (0, 0), (-1, -1), 0.5, LINE), ('VALIGN', (0, 0), (-1, -1), 'MIDDLE'),
          ('TOPPADDING', (0, 0), (-1, -1), 4), ('BOTTOMPADDING', (0, 0), (-1, -1), 4), ('LEFTPADDING', (0, 0), (-1, -1), 6)]
    if head: st.append(('BACKGROUND', (0, 0), (-1, 0), SOFT))
    t.setStyle(TableStyle(st))
    return t
def tag(text): return f'<font color="#D93A4C">【{text}】</font>'

out = sys.argv[1]
doc = SimpleDocTemplate(out, pagesize=A4, leftMargin=20*mm, rightMargin=20*mm, topMargin=18*mm, bottomMargin=18*mm, title='计分规则调整通知', author='化学课代表')
E = []
E += [Paragraph('关于计分规则调整的通知', title), Spacer(1, 2*mm),
      Paragraph('全体同学：', body), Spacer(1, 1*mm),
      Paragraph('为了让作业积分更公平、更能体现小组合作，班级作业积分自即日起作以下调整。以前的积分已按新规则重新计算，所有历史记录都保留，可以随时查询。下面标' + tag('新增') + '的是这次新加的规则，其余规则保持不变。', body), Spacer(1, 2*mm)]

E += [Paragraph('一、作业复审 ' + tag('新增'), h),
      Paragraph('老师会对已交的作业进行复审。<b>复审不合格</b>的作业按“未交”处理：', body),
      Paragraph('· 记一次未交，计入“连续未交”和“本轮累计未交”的次数；', body),
      Paragraph('· 这一次的扣分<b>翻倍</b>（×2）。例如本来是连续第 3 次未交、应扣 8 分，复审不合格则扣 16 分。', body),
      Paragraph('· 没有复审的作业一律按合格处理。复审结果改回其他状态时，这次翻倍也随之取消。', body), Spacer(1, 2*mm)]

E += [Paragraph('二、组长奖惩 ' + tag('新增'), h),
      Paragraph('设有组长的小组，<b>每周</b>看一次小组的“人均作业扣分”（组员这一周作业被扣的分之和 ÷ 组员人数），和各组平均相比，给组长加减分：', body),
      table([['本周小组人均扣分', '组长'], ['全组零扣分', '+3'], ['不到各组平均的一半', '+2'], ['不高于各组平均', '+1'], ['高于各组平均', '-1'], ['超过各组平均 1.5 倍', '-2'], ['超过各组平均 2 倍', '-3']], [100, 30]),
      Spacer(1, 1.5*mm),
      table([['附加项（3 组以上时）', '组长'], ['人均扣分各组最少', '再 +1'], ['人均扣分各组最多', '再 -1'], ['比上一周明显减少（降 30% 以上）', '再 +1'], ['比上一周明显增多（升 50% 且多 2 分以上）', '再 -1'], ['连续几周同为加分（或同为扣分），每多一周', '再 ±1（最多算 3 周）']], [100, 30]),
      Paragraph('· 每周组长奖惩合计最多 ±5 分；组长积分重置后重新开始计算。', body),
      Paragraph('· 组员都请假或没有登记的那一周不计。', note), Spacer(1, 2*mm)]

E += [KeepTogether([Paragraph('三、小组对抗排名与整组加减分 ' + tag('新增'), h),
      Paragraph('<b>每周</b>给各小组算一次综合分并排名，按名次给<b>整组每位同学</b>加减分：', body),
      table([['综合分组成', '权重', '说明'], ['交齐率', '50%', '（按时交 + 优秀 + 迟交）÷ 应交，请假不算应交'], ['少扣分', '25%', '人均作业扣分越少越好：各组里扣分最多的组为 0，零扣分为满分'], ['优秀率', '15%', '作业被评为优秀的比例'], ['考试得分率', '10%', '这一周有考试且各组都有成绩时才算，否则按比例分给前三项']], [28, 16, 126]),
      Spacer(1, 1.5*mm),
      table([['本周名次', '第 1 名', '第 2 名', '第 3 名', '最后一名（3 组以上）'], ['全组每人', '+2', '+1', '0', '-1']], [30, 28, 28, 28, 56]),
      Paragraph('· 综合分相同的小组名次并列，得分相同；每位同学积分重置后，只计重置之后的周。', body)]), Spacer(1, 2*mm)]

E += [Paragraph('四、课代表 ' + tag('新增'), h),
      Paragraph('课代表在每次结算时 <b>+2 分</b>。课代表同时是组长的，两项都计算。', body), Spacer(1, 2*mm)]

E += [KeepTogether([Paragraph('五、保持不变的规则（摘要）', h),
      table([['项目', '规则'],
             ['初始积分', '100 分'],
             ['每次作业', '按时交 0、优秀 +1、迟交 -1、请假 0'],
             ['未交', '连续未交第 1、2、3 次扣 2、4、8 分……逐次翻倍；本轮累计超过 6 次后改为 3、9、27……；单次最多扣 30 分；交过一次即打断'],
             ['连续交作业奖励', '本轮连续按时交满 5 次后，之后每次再 +1'],
             ['考试', '得分率 90% 以上 +2、80%–90% +1、60% 以下 -1、缺考 0'],
             ['每轮结算', '按交齐度：全勤 +5、95% 以上 +3、90%–95% +1、70%–80% -2、70% 以下 -5；另有缺交、进步、小组奖与区分度加减分'],
             ['积分重置', '重置后开始新一轮，连续次数清零；历史记录全部保留'],
             ['取整', '每一笔按原值累计，最终显示的积分正分向上取整、负分向下取整']], [32, 138])]), Spacer(1, 2*mm)]

E += [Paragraph('六、积分查询', h),
      Paragraph('每一笔积分变化（作业、复审、组长奖惩、小组排名、考试、结算、重置）都记录在“积分流水”里，注明时间、原因和分值。大家可以打开网页查看版查询自己的积分、作业记录和小组排名（网页版只能查看，不能修改）。', body), Spacer(1, 2*mm),
      Paragraph('以上分值为当前设置，如有调整会另行通知，具体以 App 中的计分规则为准。', note), Spacer(1, 6*mm),
      KeepTogether([Paragraph('希望同学们按时、认真完成作业，小组之间互相督促，争取小组排名靠前！', body), Spacer(1, 4*mm),
                    Paragraph('<b>本规则最终解释权归化学课代表所有。</b>', body), Spacer(1, 6*mm),
                    Paragraph('化学课代表', S(10.5, INK, 16, 2)), Paragraph('2026 年 11 月', S(10.5, INK, 16, 2))])]

def footer(canvas, d):
    canvas.saveState(); canvas.setFont('Hei', 8.5); canvas.setFillColor(MUTED)
    canvas.drawCentredString(A4[0] / 2, 10*mm, f'计分规则调整通知 · 第 {d.page} 页'); canvas.restoreState()
doc.build(E, onFirstPage=footer, onLaterPages=footer)
print('ok')
