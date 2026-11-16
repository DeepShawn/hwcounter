package com.deepshawn.hwcounter.export

import com.deepshawn.hwcounter.data.LedgerRow
import com.deepshawn.hwcounter.data.SchoolClass
import com.deepshawn.hwcounter.data.ScoreRules
import com.deepshawn.hwcounter.data.Settlement
import com.deepshawn.hwcounter.data.Student
import com.deepshawn.hwcounter.data.fmt
import com.deepshawn.hwcounter.data.ledger
import com.deepshawn.hwcounter.data.signed
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 全班积分流水（学生, 流水行），新 → 旧 */
object Ledger {
    val kindLabel = mapOf(LedgerRow.Kind.AUTO to "作业", LedgerRow.Kind.MANUAL to "手动", LedgerRow.Kind.EXAM to "考试", LedgerRow.Kind.SETTLE to "结算", LedgerRow.Kind.RECHARGE to "重置")

    fun all(c: SchoolClass, rules: ScoreRules): List<Pair<Student, LedgerRow>> =
        c.students.flatMap { s -> c.ledger(rules, s.id).map { s to it } }.sortedWith(compareByDescending<Pair<Student, LedgerRow>> { it.second.time }.thenBy { it.second.kind.ordinal }.thenBy { it.first.no })

    private val f = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)

    fun writeXlsx(c: SchoolClass, title: String, scope: String, rows: List<Pair<Student, LedgerRow>>, out: OutputStream) {
        val x = XlsxWriter()
        x.sheet("积分流水") {
            widths = listOf(17.0, 12.0, 10.0, 8.0, 8.0, 50.0, 8.0, 8.0)
            row(XlsxWriter.Cell("${c.name} $title", XlsxWriter.Style.TITLE))
            row(scope)
            rowCells(listOf("时间", "学号", "姓名", "小组", "类型", "内容", "分值", "计入").map { XlsxWriter.Cell(it, XlsxWriter.Style.HEADER) })
            rows.forEach { (s, r) ->
                val style = if (r.delta > 0) XlsxWriter.Style.GREEN else if (r.delta < 0) XlsxWriter.Style.RED else XlsxWriter.Style.NORMAL
                rowCells(listOf(
                    XlsxWriter.Cell(f.format(Date(r.time))), XlsxWriter.Cell(s.no), XlsxWriter.Cell(s.name), XlsxWriter.Cell(s.group), XlsxWriter.Cell(kindLabel[r.kind] ?: ""),
                    XlsxWriter.Cell(r.title), if (r.kind == LedgerRow.Kind.RECHARGE) XlsxWriter.Cell("→") else XlsxWriter.Cell(r.delta, style), XlsxWriter.Cell(if (r.counted) "是" else "否"),
                ))
            }
        }
        x.sheet("按学生汇总") {
            widths = listOf(12.0, 10.0, 10.0, 10.0, 10.0, 10.0, 10.0, 10.0)
            rowCells(listOf("学号", "姓名", "作业", "手动", "考试", "结算", "加分合计", "扣分合计").map { XlsxWriter.Cell(it, XlsxWriter.Style.HEADER) })
            rows.filter { it.second.counted && it.second.kind != LedgerRow.Kind.RECHARGE }.groupBy { it.first }.forEach { (s, l) ->
                fun k(kind: LedgerRow.Kind) = l.filter { it.second.kind == kind }.sumOf { it.second.delta }
                row(s.no, s.name, k(LedgerRow.Kind.AUTO), k(LedgerRow.Kind.MANUAL), k(LedgerRow.Kind.EXAM), k(LedgerRow.Kind.SETTLE), l.filter { it.second.delta > 0 }.sumOf { it.second.delta }, l.filter { it.second.delta < 0 }.sumOf { it.second.delta })
            }
        }
        x.write(out)
    }

    fun writeSettlementXlsx(c: SchoolClass, s: Settlement, out: OutputStream) {
        val names = c.students.associateBy { it.id }
        val x = XlsxWriter()
        x.sheet("结算") {
            widths = listOf(12.0, 10.0, 8.0, 10.0, 8.0, 8.0, 10.0, 10.0, 8.0, 10.0, 60.0)
            row(XlsxWriter.Cell("${c.name} ${s.title}", XlsxWriter.Style.TITLE))
            row("结算时间", f.format(Date(s.time)), "", "范围", (s.roundStart?.let { f.format(Date(it)) + " 起" } ?: "有记录以来"))
            rowCells(listOf("学号", "姓名", "应交", "交齐度%", "迟交", "缺交", "最长连缺", "综合指数", "名次", "结算分", "原因").map { XlsxWriter.Cell(it, XlsxWriter.Style.HEADER) })
            s.lines.sortedByDescending { it.delta }.forEach { l ->
                val st = names[l.studentId]
                rowCells(listOf(
                    XlsxWriter.Cell(st?.no ?: ""), XlsxWriter.Cell(st?.name ?: "已删除"), XlsxWriter.Cell(l.expected), XlsxWriter.Cell(Math.round(l.rate * 1000) / 10.0),
                    XlsxWriter.Cell(l.late), XlsxWriter.Cell(l.missing), XlsxWriter.Cell(l.longestMissing),
                    XlsxWriter.Cell(l.index?.let { Math.round(it * 100) / 100.0 } ?: ""), XlsxWriter.Cell(l.rank ?: ""),
                    if (l.skipped) XlsxWriter.Cell("不结算") else XlsxWriter.Cell(l.delta, if (l.delta > 0) XlsxWriter.Style.GREEN else if (l.delta < 0) XlsxWriter.Style.RED else XlsxWriter.Style.NORMAL),
                    XlsxWriter.Cell(l.reasons.joinToString("；")),
                ))
            }
        }
        x.write(out)
    }

    @Suppress("unused") private fun keep() = listOf(0.0.fmt(), 0.0.signed())
}
