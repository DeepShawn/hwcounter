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
doc = SimpleDocTemplate(out, pagesize=A4, leftMargin=20*mm, rightMargin=20*mm, topMargin=18*mm, bottomMargin=18*mm, title='计分规则调整通知（完整版）', author='化学课代表')
E = []
E += [Paragraph('关于计分规则调整的通知', title), Spacer(1, 2*mm),
      Paragraph('全体同学：', body), Spacer(1, 1*mm),
      Paragraph('为了让作业积分更公平、更能体现小组合作，班级作业积分自即日起作以下调整。以前的积分已按新规则重新计算，所有历史记录都保留，可以随时查询。下面是完整的计分规则，标' + tag('新增') + '的是这次新加的内容，其余规则保持不变。每位同学的初始积分为 <b>100 分</b>。', body), Spacer(1, 2*mm)]

E += [Paragraph('一、每次作业的基本计分', h), table([
    ['作业情况', '计分', '说明'],
    ['按时交', '0', '正常完成，不加不减'],
    ['优秀', '+1', '作业被评为优秀'],
    ['迟交', '-1', '交了，但晚于要求时间'],
    ['请假', '0', '不计次，也不影响下面的“连续”记录'],
    ['未交', '见第二条', '连续未交加倍扣分'],
    ['复审不合格', '见第三条', '按未交计，这一次扣分翻倍'],
], [35, 25, 110]), Spacer(1, 2*mm)]

E += [Paragraph('二、未交作业：连续未交加倍扣分', h),
      Paragraph('连续未交时，第 1 次扣 2 分，第 2 次扣 4 分，第 3 次扣 8 分，每次翻倍。<b>单次最多扣 30 分。</b>', body),
      Paragraph('· 中间请假的不打断连续，也不计次；只要交过一次（按时交、优秀、迟交都算），连续就被打断，下次未交重新从第 1 次算。', body),
      Paragraph('· 同一轮内累计未交<b>超过 6 次</b>后，改为第 1 次扣 3 分、第 2 次扣 9 分、第 3 次扣 27 分……（同样最多 30 分）。', body),
      Paragraph('· 每次积分重置后进入新一轮，连续次数和累计次数都清零，重新从 2 分开始。', body), Spacer(1, 2*mm),
      table([
          ['连续未交', '第 1 次', '第 2 次', '第 3 次', '第 4 次', '第 5 次', '第 6 次起'],
          ['本轮累计 ≤ 6 次', '-2', '-4', '-8', '-16', '-30', '-30'],
          ['本轮累计 > 6 次', '-3', '-9', '-27', '-30', '-30', '-30'],
      ], [34, 21, 21, 21, 21, 21, 31]), Spacer(1, 2*mm),
      Paragraph('举例 1：未交、请假、未交 → 按连续两次计，分别扣 2 分、4 分，共 -6。', note),
      Paragraph('举例 2：未交、交、未交 → 中间交过，连续被打断，两次各扣 2 分，共 -4。', note),
      Paragraph('举例 3：连续 4 次未交 → 2 + 4 + 8 + 16 = -30；若再连续第 5 次，再扣 30 分。', note), Spacer(1, 2*mm)]

E += [Paragraph('三、作业复审 ' + tag('新增'), h),
      Paragraph('老师会对已交的作业进行复审。<b>复审不合格</b>的作业按“未交”处理：', body),
      Paragraph('· 记一次未交，计入“连续未交”和“本轮累计未交”的次数；', body),
      Paragraph('· 这一次的扣分<b>翻倍</b>（×2）。例如本来是连续第 3 次未交、应扣 8 分，复审不合格则扣 16 分。', body),
      Paragraph('· 没有复审的作业一律按合格处理。复审结果改回其他状态时，这次翻倍也随之取消。', body), Spacer(1, 2*mm)]

E += [Paragraph('四、连续交作业奖励', h),
      Paragraph('同一轮内<b>连续按时交（按时交或优秀）满 5 次</b>后，之后每连续交一次，<b>额外加 1 分</b>。', body),
      Paragraph('· 按作业次数计算，不是按天数。请假不打断也不计次；未交、迟交会打断，之后重新从第 1 次计数。每次积分重置后重新计。', body),
      table([['连续按时交', '第 1–5 次', '第 6 次', '第 7 次', '第 8 次', '……'], ['额外奖励', '0', '+1', '+1', '+1', '每次 +1']], [34, 30, 24, 24, 24, 34]), Spacer(1, 2*mm)]

E += [Paragraph('五、取整', h),
      Paragraph('每一笔加减分都按原值累计，<b>最终显示的积分取整</b>：正分向上取整（如 98.5 显示 99），负分向下取整（如 -2.5 显示 -3）。', body), Spacer(1, 2*mm)]

E += [KeepTogether([Paragraph('六、考试计分', h),
      Paragraph('考试按得分率（分数 ÷ 满分）自动加减分，150 分制等考试同样适用：', body),
      table([['得分率', '90%–100%', '80%–90%', '60%–80%', '60% 以下', '缺考'], ['计分', '+2', '+1', '0', '-1', '0']], [24, 30, 28, 28, 28, 22])]), Spacer(1, 2*mm)]

E += [Paragraph('七、积分重置', h),
      Paragraph('积分重置会把积分重新设为指定分数，开始新的一轮。重置前的所有加减分记录都保留，可以查看，只是不再计入当前积分。班级也可以设置每月固定日期自动重置。', body), Spacer(1, 2*mm)]

E += [Paragraph('八、每轮结算', h),
      Paragraph('每一轮结束时进行结算，按本轮的<b>交齐度</b>和<b>缺交情况</b>加减分。结算分计入下一轮。', body),
      Paragraph('交齐度 =（按时交 + 优秀 + 迟交）÷ 应交次数，请假不算应交。', note), Spacer(1, 1.5*mm),
      table([['本轮交齐度', '结算分'], ['100% 且没有迟交（全勤）', '+5'], ['95% 及以上', '+3'], ['90%–95%', '+1'], ['80%–90%', '0'], ['70%–80%', '-2'], ['70% 以下', '-5']], [80, 30]),
      Spacer(1, 2*mm),
      table([['附加项', '结算分'],
             ['本轮缺交 5 次及以上', '再 -3'],
             ['本轮最长连续缺交 3 次及以上', '再 -2'],
             ['进步奖：交齐度比上一轮提高 10 个百分点以上', '+2'],
             ['小组奖：本轮平均交齐度第一的小组，每人', '+2'],
             ['课代表 ' + tag('新增') + '（课代表同时是组长的两项都计算）', '+2']], [110, 30]),
      Spacer(1, 1.5*mm),
      Spacer(1, 2*mm),
      Paragraph('<b>区分度（综合表现指数）</b>：为公平地拉开差距，结算时还按综合表现加减分。', body),
      table([['指标', '权重', '说明'],
             ['交齐率', '30%', '做小样本校正：按“多算 3 次全班平均”平滑，交得次数少的不会因偶然全交而占优'],
             ['按时率', '15%', '同上'],
             ['优秀率', '10%', '同上'],
             ['缺交度', '15%', '缺交率 + 0.5 × 最长连续缺交 ÷ 应交，越低越好'],
             ['考试', '20%', '每场按全班平均和标准差换算，消除试卷难易差别；本轮没有考试时，这 20% 按比例分给其他项'],
             ['进步', '10%', '本轮交齐率 - 上一轮交齐率']], [24, 16, 125]),
      Paragraph('· 每项先换算成全班标准分（与全班平均相比高或低多少，限制在 ±2.5 之内），再按权重相加得到综合指数；全班综合指数的平均为 0。', body),
      Paragraph('· 区分度分 = 4 × 综合指数，四舍五入到整数，单人最多 ±10 分（与平均差不多的同学为 0）。指数高于平均的加分、低于平均的扣分，全班合计约为 0，平均分基本不变。', body),
      Paragraph('· 结算单列出每人的综合指数和名次；结算后按学号公布每人的结算分和积分。', body),
      Paragraph('本轮应交少于 3 次的同学（如长期请假）不参加结算。', note),
      Paragraph('举例：本轮应交 20 次，交了 19 次、缺交 1 次 → 交齐度 95%，结算 +3；若比上一轮的 80% 提高了 15 个百分点，再加进步奖 +2，共 +5。', note)]

E += [Spacer(1, 2*mm)]
E += [Paragraph('九、组长奖惩 ' + tag('新增'), h),
      Paragraph('设有组长的小组，<b>每周</b>看一次小组的“人均作业扣分”（组员这一周作业被扣的分之和 ÷ 组员人数），和各组平均相比，给组长加减分：', body),
      table([['本周小组人均扣分', '组长'], ['全组零扣分', '+3'], ['不到各组平均的一半', '+2'], ['不高于各组平均', '+1'], ['高于各组平均', '-1'], ['超过各组平均 1.5 倍', '-2'], ['超过各组平均 2 倍', '-3']], [100, 30]),
      Spacer(1, 1.5*mm),
      table([['附加项（3 组以上时）', '组长'], ['人均扣分各组最少', '再 +1'], ['人均扣分各组最多', '再 -1'], ['比上一周明显减少（降 30% 以上）', '再 +1'], ['比上一周明显增多（升 50% 且多 2 分以上）', '再 -1'], ['连续几周同为加分（或同为扣分），每多一周', '再 ±1（最多算 3 周）']], [100, 30]),
      Paragraph('· 每周组长奖惩合计最多 ±5 分；组长积分重置后重新开始计算。', body),
      Paragraph('· 组员都请假或没有登记的那一周不计。', note), Spacer(1, 2*mm)]

E += [KeepTogether([Paragraph('十、小组对抗排名与整组加减分 ' + tag('新增'), h),
      Paragraph('<b>每周</b>给各小组算一次综合分并排名，按名次给<b>整组每位同学</b>加减分：', body),
      table([['综合分组成', '权重', '说明'], ['交齐率', '50%', '（按时交 + 优秀 + 迟交）÷ 应交，请假不算应交'], ['少扣分', '25%', '人均作业扣分越少越好：各组里扣分最多的组为 0，零扣分为满分'], ['优秀率', '15%', '作业被评为优秀的比例'], ['考试得分率', '10%', '这一周有考试且各组都有成绩时才算，否则按比例分给前三项']], [28, 16, 126]),
      Spacer(1, 1.5*mm),
      table([['本周名次', '第 1 名', '第 2 名', '第 3 名', '最后一名（3 组以上）'], ['全组每人', '+2', '+1', '0', '-1']], [30, 28, 28, 28, 56]),
      Paragraph('· 综合分相同的小组名次并列，得分相同；每位同学积分重置后，只计重置之后的周。', body)]), Spacer(1, 2*mm)]

E += [Paragraph('十一、积分查询', h),
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
