package com.deepshawn.hwcounter.export

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
            row(XlsxWriter.Cell("${c.name} 作业与成绩分析报告（${r.range.label}）", XlsxWriter.Style.TITLE))
            row("范围", r.window.label)
            row("生成时间", fmtDay(System.currentTimeMillis()))
            row()
            rowCells(header("指标", "本时段", "上一时段", "说明"))
            fun line(n: String, v: Any, p: Any?, d: String) = rowCells(listOf(XlsxWriter.Cell(n), XlsxWriter.Cell(v), XlsxWriter.Cell(p ?: ""), XlsxWriter.Cell(d)))
            val p = r.previous
            line("作业次数", k.assignments, p?.assignments, "")
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
        x.sheet("积分") {
            widths = listOf(12.0, 10.0, 10.0, 12.0, 8.0)
            rowCells(header("学号", "姓名", "积分", "本时段变化", "名次"))
            r.students.sortedBy { it.rank }.forEach { s -> row(s.student.no, s.student.name, s.score, s.scoreDelta, s.rank) }
        }
        if (exam.exams.isNotEmpty()) {
            x.sheet("考试统计") {
                widths = listOf(12.0, 8.0, 18.0, 8.0, 8.0, 8.0, 8.0, 8.0, 8.0, 8.0, 8.0, 8.0, 8.0, 8.0)
                rowCells(header("日期", "科目", "考试", "满分", "人数", "缺考", "平均", "中位", "最高", "最低", "标准差", "及格率%", "良好率%", "优秀率%"))
                exam.exams.forEach { e ->
                    val s = exam.stats.getValue(e.id)
                    row(fmtDay(e.date), e.subject, e.title, e.fullScore, s.count, s.absent, s.avg.fmt(), s.median.fmt(), s.max, s.min, s.std.fmt(), pct(s.passRate), pct(s.goodRate), pct(s.excellentRate))
                }
                row()
                exam.insights.forEach { row(it.text) }
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
