package com.deepshawn.hwcounter.export

import com.deepshawn.hwcounter.data.CreditRules

import com.deepshawn.hwcounter.data.Analytics
import com.deepshawn.hwcounter.data.HwStatus
import com.deepshawn.hwcounter.data.SchoolClass
import com.deepshawn.hwcounter.data.fmt
import com.deepshawn.hwcounter.data.fmtDay
import com.deepshawn.hwcounter.data.ranks
import com.deepshawn.hwcounter.data.signed
import java.io.OutputStream
import kotlin.math.roundToInt

/** 一键导出 Excel 分析报告：概览、结论、学生明细、每次作业、科目、小组、积分涨跌、考试 */
object AnalysisExport {
    private fun pct(x: Float) = (x * 1000).roundToInt() / 10.0
    private fun pct(x: Double) = (x * 1000).roundToInt() / 10.0
    private fun header(vararg t: String) = t.map { XlsxWriter.Cell(it, XlsxWriter.Style.HEADER) }

    fun write(c: SchoolClass, r: Analytics.Report, exam: Analytics.ExamReport, out: OutputStream) {
        val x = XlsxWriter()
        val k = r.kpi
        x.sheet("概览") {
            freezeRows = 0
            widths = listOf(22.0, 16.0, 16.0, 60.0)
            row(XlsxWriter.Cell("${c.name} 作业与成绩分析报告（${Analytics.rangeTitle(r)}）", XlsxWriter.Style.TITLE))
            row("范围", r.window.label)
            row("生成日期", fmtDay(System.currentTimeMillis()))
            row()
            rowCells(header("指标", "本时段", "上一时段", "说明"))
            fun line(n: String, v: Any, p: Any?, d: String) = rowCells(listOf(XlsxWriter.Cell(n), XlsxWriter.Cell(v), XlsxWriter.Cell(p ?: ""), XlsxWriter.Cell(d)))
            val p = r.previous
            line("作业次数", k.assignments, p?.assignments, if (p != null) "对比：${r.window.prevLabel}" else "")
            line("应交人次", k.expected, p?.expected, "去掉请假")
            line("交齐率 %", pct(k.handInRate), p?.let { pct(it.handInRate) }, "已交 + 优秀 + 迟交 / 应交")
            line("按时率 %", pct(k.onTimeRate), p?.let { pct(it.onTimeRate) }, "已交 + 优秀 / 应交")
            line("优秀率 %", pct(k.excellentRate), p?.let { pct(it.excellentRate) }, "")
            line("缺交率 %", pct(k.missingRate), p?.let { pct(it.missingRate) }, "")
            line("迟交率 %", pct(k.lateRate), p?.let { pct(it.lateRate) }, "")
            line("全员交齐次数", k.perfect, p?.perfect, "最长连续 ${r.longestPerfectRun} 次")
            line("未登记人次", k.unregistered, p?.unregistered, "大于 0 说明统计可能偏低")
            line("全班平均积分", r.avgScore.fmt(), null, if (r.range != Analytics.Range.ALL) "本时段变化 ${r.avgScoreDelta.signed()}" else "")
            line("交齐率趋势（每次）", "%.2f".format(r.slope * 100) + " 个百分点", null, "线性回归斜率，正数为上升")
            r.examCorrelation?.let { line("作业与考试相关系数", "%.2f".format(it), null, "0.3 以上为正相关") }
            line("学生交齐率中位数 %", pct(r.rateMedian), null, "标准差 ${pct(r.rateStd)} 个百分点，越大越两极分化")
            r.tiers.forEach { t -> line("分层：${t.name}", t.students.size, null, t.desc) }
            row()
            rowCells(header("主要发现"))
            r.insights.forEachIndexed { i, s -> row("${i + 1}. ${s.text}") }
        }
        x.sheet("学生明细") {
            widths = listOf(12.0, 10.0, 8.0, 8.0, 8.0, 8.0, 8.0, 8.0, 8.0, 10.0, 10.0, 10.0, 10.0, 8.0, 10.0)
            rowCells(header("学号", "姓名", "小组", "应交", "已交", "按时", "优秀", "迟交", "缺交", "交齐率%", "按时率%", "连续未交", "最长连续未交", "积分", "本时段变化"))
            r.students.sortedBy { it.rate }.forEach { s ->
                val style = when { s.rate < 0.7f -> XlsxWriter.Style.RED; s.rate >= 0.999f -> XlsxWriter.Style.GREEN; else -> XlsxWriter.Style.NORMAL }
                rowCells(listOf(
                    XlsxWriter.Cell(s.student.no), XlsxWriter.Cell(s.student.name), XlsxWriter.Cell(s.student.group), XlsxWriter.Cell(s.expected), XlsxWriter.Cell(s.handedIn),
                    XlsxWriter.Cell(s.onTime), XlsxWriter.Cell(s.excellent), XlsxWriter.Cell(s.late), XlsxWriter.Cell(s.missing),
                    XlsxWriter.Cell(pct(s.rate), style), XlsxWriter.Cell(pct(s.onTimeRate)), XlsxWriter.Cell(s.missingStreak), XlsxWriter.Cell(s.longestMissing),
                    XlsxWriter.Cell(s.score), XlsxWriter.Cell(s.scoreDelta, if (s.scoreDelta < 0) XlsxWriter.Style.RED else XlsxWriter.Style.NORMAL),
                ))
            }
        }
        x.sheet("每次作业") {
            widths = listOf(12.0, 8.0, 20.0, 8.0, 8.0, 8.0, 8.0, 8.0, 8.0, 8.0, 10.0)
            rowCells(header("日期", "科目", "作业", "应交", "已交", "优秀", "迟交", "未交", "请假", "未登记", "交齐率%"))
            r.points.forEach { p ->
                val s = p.summary
                rowCells(listOf(
                    XlsxWriter.Cell(fmtDay(p.assignment.createdAt)), XlsxWriter.Cell(p.assignment.subject), XlsxWriter.Cell(p.assignment.title), XlsxWriter.Cell(s.expected),
                    XlsxWriter.Cell(s.c(HwStatus.SUBMITTED)), XlsxWriter.Cell(s.c(HwStatus.EXCELLENT)), XlsxWriter.Cell(s.c(HwStatus.LATE)), XlsxWriter.Cell(s.c(HwStatus.MISSING)),
                    XlsxWriter.Cell(s.c(HwStatus.LEAVE)), XlsxWriter.Cell(s.c(HwStatus.NONE)), XlsxWriter.Cell(pct(s.rate), if (s.rate >= 0.999f) XlsxWriter.Style.GREEN else XlsxWriter.Style.NORMAL),
                ))
            }
        }
        r.progress?.let { pg ->
            x.sheet("进步与退步") {
                widths = listOf(12.0, 10.0, 10.0, 12.0, 12.0, 12.0, 12.0, 12.0, 12.0, 8.0, 50.0)
                row(XlsxWriter.Cell("比较方式：${pg.basis}", XlsxWriter.Style.TITLE))
                rowCells(header("学号", "姓名", "类别", "以往交齐率%", "重置后交齐率%", "以往按时率%", "重置后按时率%", "以往考试%", "重置后考试%", "指数", "主要变化"))
                val tag = pg.stars.map { it.student.id }.toSet() to pg.watch.map { it.student.id }.toSet()
                pg.all.sortedByDescending { it.index }.forEach { sp ->
                    val kind = when (sp.student.id) { in tag.first -> "进步之星"; in tag.second -> "退步关注"; else -> "" }
                    val st = if (kind == "进步之星") XlsxWriter.Style.GREEN else if (kind == "退步关注") XlsxWriter.Style.RED else XlsxWriter.Style.NORMAL
                    rowCells(listOf(
                        XlsxWriter.Cell(sp.student.no), XlsxWriter.Cell(sp.student.name), XlsxWriter.Cell(kind, st),
                        XlsxWriter.Cell(pct(sp.before.rate)), XlsxWriter.Cell(pct(sp.after.rate)), XlsxWriter.Cell(pct(sp.before.onTimeRate)), XlsxWriter.Cell(pct(sp.after.onTimeRate)),
                        XlsxWriter.Cell(sp.before.examPct?.let { pct(it) } ?: ""), XlsxWriter.Cell(sp.after.examPct?.let { pct(it) } ?: ""),
                        XlsxWriter.Cell((sp.index * 100).roundToInt(), st), XlsxWriter.Cell(sp.reasons.joinToString("，")),
                    ))
                }
            }
        }
        if (r.rounds.isNotEmpty() || r.buckets.isNotEmpty()) x.sheet("各轮与分段") {
            widths = listOf(18.0, 14.0, 8.0, 10.0, 10.0, 10.0, 10.0, 12.0)
            if (r.rounds.isNotEmpty()) {
                rowCells(header("轮次", "起始", "作业", "交齐率%", "按时率%", "优秀率%", "缺交率%", "考试得分率%"))
                r.rounds.forEach { o -> row(o.label, o.start?.let { fmtDay(it) } ?: "", o.kpi.assignments, pct(o.kpi.handInRate), pct(o.kpi.onTimeRate), pct(o.kpi.excellentRate), pct(o.kpi.missingRate), o.examPct?.let { pct(it) } ?: "") }
                row()
            }
            if (r.buckets.isNotEmpty()) {
                rowCells(header("按${r.bucketUnit}", "作业", "交齐率%", "按时率%", "缺交人次"))
                r.buckets.forEach { b -> row(b.label, b.count, pct(b.rate), pct(b.onTimeRate), b.missing) }
            }
        }
        x.sheet("积分来源与预警") {
            widths = listOf(16.0, 12.0, 12.0, 12.0, 40.0)
            rowCells(header("积分来源", "合计"))
            row("作业自动计分", r.sources.homework); row("手动加分", r.sources.manualPlus); row("手动扣分", r.sources.manualMinus); row("考试计分", r.sources.exam)
            row()
            if (r.reasons.isNotEmpty()) {
                rowCells(header("加减分原因", "次数", "涉及人数", "合计"))
                r.reasons.forEach { o -> row(o.reason, o.count, o.people, o.total) }
                row()
            }
            if (r.risk.isNotEmpty()) {
                rowCells(header("缺交风险", "学号", "风险%", "", "依据"))
                r.risk.forEach { o -> row(o.student.name, o.student.no, pct(o.prob), "", o.why) }
                row()
            }
            if (r.weakSubjects.isNotEmpty()) {
                rowCells(header("偏科", "科目", "该科交齐率%", "整体交齐率%"))
                r.weakSubjects.forEach { o -> row(o.student.name, o.subject, pct(o.subjectRate), pct(o.overallRate)) }
                row()
            }
            if (r.streakers.isNotEmpty()) {
                rowCells(header("连续交齐", "次数"))
                r.streakers.forEach { o -> row(o.student.name, o.handStreak) }
            }
        }
        x.sheet("科目与星期") {
            widths = listOf(12.0, 10.0, 12.0, 12.0, 10.0)
            rowCells(header("科目", "次数", "交齐率%", "按时率%", "缺交"))
            r.subjects.forEach { s -> row(s.subject, s.count, pct(s.rate), pct(s.onTimeRate), s.missing) }
            row()
            rowCells(header("星期", "平均交齐率%"))
            val days = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
            r.weekday.forEachIndexed { i, v -> if (v != null) row(days[i], pct(v)) }
        }
        x.sheet("小组") {
            widths = listOf(12.0, 10.0, 8.0, 12.0, 8.0, 12.0, 12.0, 14.0)
            rowCells(header("小组", "组长", "人数", "交齐率%", "缺交", "平均积分", "积分变化", "考试平均得分率%"))
            r.groupStats.forEach { g -> row(g.name, g.leader?.name ?: "", g.size, pct(g.rate), g.missing, g.avgScore.fmt(), g.scoreDelta.fmt(), g.examPct?.let { pct(it) } ?: "") }
        }
        r.credit?.takeIf { it.results.isNotEmpty() }?.let { cs ->
            x.sheet("信誉分") {
                widths = listOf(12.0, 10.0, 10.0, 8.0, 10.0, 10.0, 10.0, 10.0, 10.0, 10.0, 10.0, 30.0)
                rowCells(header("学号", "姓名", "小组", "等级", "信誉分", "交齐%", "按时%", "优秀%", "连续交%", "诚信%", "考试%", "偏弱项"))
                cs.results.forEach { c ->
                    fun part(k: String) = c.parts.firstOrNull { it.key == k }?.let { pct(it.value) } ?: ""
                    row(c.student.no, c.student.name, c.student.group, c.level.label, if (c.hasData) c.index.fmt() else "", part("rate"), part("ontime"), part("excellent"), part("streak"), part("integrity"), part("exam"), c.weak.joinToString("；") { "${it.label}：${it.detail}" })
                }
                row()
                rowCells(header("小组", "小组信誉分"))
                cs.groups.forEach { (g, v) -> row(g, v.fmt()) }
                row()
                row("全班平均", cs.average.fmt())
                row("信誉分加减分", if (cs.rules.active) "个人${CreditRules.modeLabel(cs.rules.personal)}、小组${CreditRules.modeLabel(cs.rules.group)}，本时段合计 ${cs.pointsTotal.fmt()}" else "不计入积分")
            }
        }
        x.sheet("积分") {
            widths = listOf(12.0, 10.0, 10.0, 12.0, 8.0)
            rowCells(header("学号", "姓名", "积分", "本时段变化", "名次"))
            r.students.sortedBy { it.rank }.forEach { s -> row(s.student.no, s.student.name, s.score, s.scoreDelta, s.rank) }
        }
        if (exam.exams.isNotEmpty()) {
            x.sheet("考试统计") {
                widths = listOf(12.0, 8.0, 18.0, 8.0, 8.0, 8.0, 8.0, 8.0, 8.0, 8.0, 8.0, 8.0, 8.0, 8.0, 8.0, 8.0, 8.0, 10.0)
                rowCells(header("日期", "科目", "考试", "满分", "人数", "缺考", "平均", "中位", "下四分位", "上四分位", "最高", "最低", "标准差", "及格率%", "良好率%", "优秀率%", "难度", "区分度"))
                val q = exam.quality.associateBy { it.exam.id }
                exam.exams.forEach { e ->
                    val s = exam.stats.getValue(e.id); val qq = q[e.id]
                    row(fmtDay(e.date), e.subject, e.title, e.fullScore, s.count, s.absent, s.avg.fmt(), s.median.fmt(), qq?.q1?.fmt() ?: "", qq?.q3?.fmt() ?: "", s.max, s.min, s.std.fmt(), pct(s.passRate), pct(s.goodRate), pct(s.excellentRate),
                        qq?.let { "${Analytics.difficultyText(it.difficulty)} ${pct(it.difficulty)}%" } ?: "", qq?.let { "%.2f ".format(it.discrimination) + Analytics.discriminationText(it.discrimination) } ?: "")
                }
                row()
                exam.insights.forEach { row(it.text) }
            }
            x.sheet("考试进退与分层") {
                widths = listOf(20.0, 12.0, 14.0, 14.0, 14.0, 40.0)
                rowCells(header("考试进步之星", "学号", "最近一场得分率%", "之前相对平均", "最近相对平均", "最近一场"))
                exam.stars.forEach { p -> row(p.student.name, p.student.no, pct(p.lastPct), pct(p.before), pct(p.after), p.last.title) }
                row()
                rowCells(header("考试退步关注", "学号", "最近一场得分率%", "之前相对平均", "最近相对平均", "最近一场"))
                exam.watch.forEach { p -> row(p.student.name, p.student.no, pct(p.lastPct), pct(p.before), pct(p.after), p.last.title) }
                row()
                exam.flow?.let { f ->
                    rowCells(header("层级流动：${f.from.title} → ${f.to.title}", *f.labels.toTypedArray()))
                    f.matrix.forEachIndexed { i, r -> row(f.labels[i], *r.toTypedArray()) }
                    row("升档 ${f.up} 人，降档 ${f.down} 人，不变 ${f.same} 人")
                    row()
                }
                rowCells(header("层级（按平均得分率）", "人数"))
                exam.levels.forEach { row(it.first, it.second) }
                row()
                fun names(title: String, l: List<Analytics.ExamStudent>) { if (l.isNotEmpty()) { rowCells(header(title, "平均得分率%")); l.forEach { row(it.student.name, pct(it.avgPct ?: 0.0)) }; row() } }
                names("临界及格（55–60%）", exam.nearPass); names("临界优秀（80–85%）", exam.nearExcellent)
                names("连续三场上升", exam.rising); names("连续三场下滑", exam.falling); names("成绩稳定", exam.steady)
                if (exam.imbalance.isNotEmpty()) { rowCells(header("偏科", "最好科目", "得分率%", "最差科目", "得分率%")); exam.imbalance.forEach { row(it.student.name, it.best.first, pct(it.best.second), it.worst.first, pct(it.worst.second)) }; row() }
                if (exam.diligentLow.isNotEmpty() || exam.skipButGood.isNotEmpty()) {
                    rowCells(header("作业与考试对照", "类别", "作业交齐率%", "考试平均得分率%"))
                    exam.diligentLow.forEach { row(it.student.name, "交得齐但考得不好", pct(it.hwRate), pct(it.examPct)) }
                    exam.skipButGood.forEach { row(it.student.name, "常缺交但考得不错", pct(it.hwRate), pct(it.examPct)) }
                    row()
                }
                if (exam.genders.size == 2) { rowCells(header("性别", "人数", "平均得分率%")); exam.genders.forEach { row(it.first, it.second, pct(it.third)) } }
            }
            x.sheet("考试成绩") {
                widths = listOf(12.0, 10.0) + exam.exams.flatMap { listOf(14.0, 8.0) } + listOf(12.0, 10.0)
                rowCells(header("学号", "姓名", *exam.exams.flatMap { listOf("${it.title}（${it.fullScore.fmt()}）", "名次") }.toTypedArray(), "平均得分率%", "名次变化"))
                val ranks = exam.exams.associate { it.id to it.ranks() }
                exam.students.forEach { st ->
                    val cells = ArrayList<XlsxWriter.Cell>()
                    cells += XlsxWriter.Cell(st.student.no); cells += XlsxWriter.Cell(st.student.name)
                    exam.exams.forEach { e ->
                        val v = e.scores[st.student.id]
                        cells += XlsxWriter.Cell(v ?: if (st.student.id in e.absent) "缺考" else "", if (v != null && v < e.fullScore * 0.6) XlsxWriter.Style.RED else if (v != null && v >= e.fullScore * 0.85) XlsxWriter.Style.GREEN else XlsxWriter.Style.NORMAL)
                        cells += XlsxWriter.Cell(ranks[e.id]?.get(st.student.id) ?: "")
                    }
                    cells += XlsxWriter.Cell(st.avgPct?.let { pct(it) } ?: ""); cells += XlsxWriter.Cell(st.rankChange ?: "")
                    rowCells(cells)
                }
            }
        }
        x.write(out)
    }
}
