package com.deepshawn.hwcounter.export

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.deepshawn.hwcounter.data.Assignment
import com.deepshawn.hwcounter.data.HwStatus
import com.deepshawn.hwcounter.data.SchoolClass
import com.deepshawn.hwcounter.data.ScoreRules
import com.deepshawn.hwcounter.data.Student
import com.deepshawn.hwcounter.data.fmt
import com.deepshawn.hwcounter.data.studentStats
import com.deepshawn.hwcounter.data.summary
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Exporter {
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
    private val fullFmt = SimpleDateFormat("yyyy年M月d日", Locale.CHINA)

    fun fileStamp(): String = SimpleDateFormat("yyyyMMdd_HHmm", Locale.CHINA).format(Date())

    private fun styleOf(s: HwStatus) = when (s) {
        HwStatus.SUBMITTED -> XlsxWriter.Style.GREEN
        HwStatus.EXCELLENT -> XlsxWriter.Style.GOLD
        HwStatus.LATE -> XlsxWriter.Style.YELLOW
        HwStatus.MISSING -> XlsxWriter.Style.RED
        HwStatus.LEAVE -> XlsxWriter.Style.BLUE
        HwStatus.NONE -> XlsxWriter.Style.NORMAL
    }

    /** 导出整个班级：总表 + 积分榜 + 积分明细 + 每次作业清单 */
    fun writeXlsx(c: SchoolClass, rules: ScoreRules, out: OutputStream, onlyAssignment: Assignment? = null) {
        val x = XlsxWriter()
        val stats = c.studentStats(rules).associateBy { it.student.id }
        val assigns = onlyAssignment?.let { listOf(it) } ?: c.assignments.sortedBy { it.createdAt }
        x.sheet("作业统计") {
            freezeCols = 2
            widths = listOf(12.0, 10.0) + assigns.map { 12.0 } + listOf(8.0, 8.0, 8.0, 8.0, 9.0)
            rowCells(
                listOf("学号", "姓名").map { XlsxWriter.Cell(it, XlsxWriter.Style.HEADER) } +
                    assigns.map { XlsxWriter.Cell("${dateFmt.format(Date(it.createdAt))} ${it.subject}\n${it.title}", XlsxWriter.Style.HEADER) } +
                    listOf("已交", "迟交", "未交", "请假", "积分").map { XlsxWriter.Cell(it, XlsxWriter.Style.HEADER) },
            )
            for (s in c.students) {
                val st = stats[s.id]
                val cells = ArrayList<XlsxWriter.Cell>()
                cells += XlsxWriter.Cell(s.no)
                cells += XlsxWriter.Cell(s.name)
                for (a in assigns) {
                    val status = a.statuses[s.id] ?: HwStatus.NONE
                    val cm = a.comments[s.id]
                    cells += XlsxWriter.Cell(status.label + (if (cm.isNullOrBlank()) "" else "($cm)"), styleOf(status))
                }
                cells += XlsxWriter.Cell(st?.submitted ?: 0)
                cells += XlsxWriter.Cell(st?.late ?: 0)
                cells += XlsxWriter.Cell(st?.missing ?: 0)
                cells += XlsxWriter.Cell(st?.counts?.get(HwStatus.LEAVE) ?: 0)
                cells += XlsxWriter.Cell(st?.score ?: rules.initial)
                rowCells(cells)
            }
            // 汇总行
            val sumRow = ArrayList<XlsxWriter.Cell>()
            sumRow += XlsxWriter.Cell("提交率", XlsxWriter.Style.HEADER)
            sumRow += XlsxWriter.Cell("", XlsxWriter.Style.HEADER)
            for (a in assigns) {
                val sm = c.summary(a)
                sumRow += XlsxWriter.Cell("${sm.handedIn}/${sm.expected} (${(sm.rate * 100).toInt()}%)", XlsxWriter.Style.HEADER)
            }
            rowCells(sumRow)
        }
        if (onlyAssignment == null) x.sheet("各次统计名单") { classStatRows(c) }
        x.sheet("积分榜") {
            widths = listOf(8.0, 12.0, 10.0, 10.0, 8.0, 8.0, 8.0, 10.0)
            row(*listOf("名次", "学号", "姓名", "小组", "已交", "迟交", "未交", "积分").map { XlsxWriter.Cell(it, XlsxWriter.Style.HEADER) }.toTypedArray())
            stats.values.sortedByDescending { it.score }.forEachIndexed { i, st ->
                val medal = when (i) { 0 -> XlsxWriter.Style.GOLD; 1, 2 -> XlsxWriter.Style.GREEN; else -> XlsxWriter.Style.NORMAL }
                rowCells(
                    listOf(
                        XlsxWriter.Cell(i + 1, medal), XlsxWriter.Cell(st.student.no), XlsxWriter.Cell(st.student.name),
                        XlsxWriter.Cell(st.student.group), XlsxWriter.Cell(st.submitted), XlsxWriter.Cell(st.late),
                        XlsxWriter.Cell(st.missing), XlsxWriter.Cell(st.score, medal),
                    ),
                )
            }
        }
        x.sheet("积分明细") {
            widths = listOf(18.0, 12.0, 10.0, 8.0, 30.0)
            row(*listOf("日期", "学号", "姓名", "分值", "原因").map { XlsxWriter.Cell(it, XlsxWriter.Style.HEADER) }.toTypedArray())
            val byId = c.students.associateBy { it.id }
            c.scores.sortedByDescending { it.time }.forEach { e ->
                val s = byId[e.studentId] ?: return@forEach
                rowCells(
                    listOf(
                        XlsxWriter.Cell(fullFmt.format(Date(e.time))), XlsxWriter.Cell(s.no), XlsxWriter.Cell(s.name),
                        XlsxWriter.Cell(e.delta, if (e.delta >= 0) XlsxWriter.Style.GREEN else XlsxWriter.Style.RED),
                        XlsxWriter.Cell(e.reason),
                    ),
                )
            }
        }
        x.sheet("计分规则") {
            freezeRows = 0
            widths = listOf(14.0, 10.0)
            row(XlsxWriter.Cell("班级：${c.name}", XlsxWriter.Style.TITLE))
            row("导出日期", fullFmt.format(Date()))
            row("初始分", rules.initial)
            row("已交", rules.submitted)
            row("优秀", rules.excellent)
            row("迟交", rules.late)
            row("未交", rules.missing)
            row("请假", rules.leave)
        }
        x.write(out)
    }

    private val ORDER = listOf(HwStatus.SUBMITTED, HwStatus.EXCELLENT, HwStatus.LATE, HwStatus.MISSING, HwStatus.LEAVE, HwStatus.NONE)
    private fun pct(x: Float): String { val r = Math.round(x * 1000) / 10.0; return (if (r == Math.floor(r)) r.toLong().toString() else r.toString()) + "%" }
    private fun hdr(vararg v: String) = v.map { XlsxWriter.Cell(it, XlsxWriter.Style.HEADER) }

    /** 全班各次作业：一行一次作业（应交、已交、交齐率、未交/迟交/请假/未登记名单） */
    private fun XlsxWriter.Sheet.classStatRows(c: SchoolClass) {
        widths = listOf(14.0, 8.0, 20.0, 7.0, 7.0, 9.0, 9.0, 30.0, 20.0, 20.0, 24.0)
        rowCells(hdr("日期", "科目", "作业", "应交", "已交", "交齐率", "未交人数", "未交名单", "迟交名单", "请假名单", "未登记名单"))
        for (a in c.assignments.sortedBy { it.createdAt }) {
            val t = com.deepshawn.hwcounter.recog.StatTable.tally(c.students, a.statuses)
            fun names(k: HwStatus) = t.lists[k].orEmpty().joinToString("、") { it.name }
            rowCells(listOf(
                XlsxWriter.Cell(fullFmt.format(Date(a.createdAt))), XlsxWriter.Cell(a.subject), XlsxWriter.Cell(a.title),
                XlsxWriter.Cell(t.expected), XlsxWriter.Cell(t.handedIn),
                XlsxWriter.Cell(pct(t.rate), if (t.rate >= 1f) XlsxWriter.Style.GREEN else if (t.rate < 0.8f) XlsxWriter.Style.RED else XlsxWriter.Style.NORMAL),
                XlsxWriter.Cell(t.count(HwStatus.MISSING)), XlsxWriter.Cell(names(HwStatus.MISSING)), XlsxWriter.Cell(names(HwStatus.LATE)),
                XlsxWriter.Cell(names(HwStatus.LEAVE)), XlsxWriter.Cell(names(HwStatus.NONE)),
            ))
        }
    }

    /** 只导出“各次统计名单” */
    fun writeClassStatList(c: SchoolClass, out: OutputStream) {
        val x = XlsxWriter()
        x.sheet("各次统计名单") { classStatRows(c) }
        x.write(out)
    }

    /**
     * 统计名单（一次作业 / 草稿 / 识别结果）：统计汇总 + 分类名单 + 明细。三端同样的三张表。
     */
    fun writeStatList(c: SchoolClass, title: String, subject: String, createdAt: Long, statuses: Map<String, HwStatus>, comments: Map<String, String>, out: OutputStream) {
        val t = com.deepshawn.hwcounter.recog.StatTable.tally(c.students, statuses)
        val n = c.students.size
        val x = XlsxWriter()
        x.sheet("统计汇总") {
            freezeRows = 0
            widths = listOf(20.0, 10.0, 10.0, 60.0)
            rowCells(listOf(XlsxWriter.Cell("班级", XlsxWriter.Style.HEADER), XlsxWriter.Cell(c.name)))
            rowCells(listOf(XlsxWriter.Cell("作业", XlsxWriter.Style.HEADER), XlsxWriter.Cell(listOf(subject, title).filter { it.isNotBlank() }.joinToString(" "))))
            rowCells(listOf(XlsxWriter.Cell("日期", XlsxWriter.Style.HEADER), XlsxWriter.Cell(fullFmt.format(Date(createdAt)))))
            rowCells(listOf(XlsxWriter.Cell("应交", XlsxWriter.Style.HEADER), XlsxWriter.Cell(t.expected)))
            rowCells(listOf(XlsxWriter.Cell("已交（含优秀、迟交）", XlsxWriter.Style.HEADER), XlsxWriter.Cell(t.handedIn)))
            rowCells(listOf(XlsxWriter.Cell("交齐率", XlsxWriter.Style.HEADER), XlsxWriter.Cell(pct(t.rate))))
            row()
            rowCells(hdr("状态", "人数", "占比", "名单"))
            for (k in ORDER) rowCells(listOf(
                XlsxWriter.Cell(k.label, styleOf(k)), XlsxWriter.Cell(t.count(k)), XlsxWriter.Cell(if (n > 0) pct(t.count(k).toFloat() / n) else "0%"),
                XlsxWriter.Cell(t.lists[k].orEmpty().joinToString("、") { it.name }),
            ))
        }
        x.sheet("分类名单") {
            widths = ORDER.map { 16.0 }
            rowCells(ORDER.map { XlsxWriter.Cell("${it.label}（${t.count(it)}）", XlsxWriter.Style.HEADER) })
            val h = ORDER.maxOf { t.count(it) }
            for (i in 0 until h) rowCells(ORDER.map { k -> t.lists[k].orEmpty().getOrNull(i)?.let { s -> XlsxWriter.Cell(if (s.no.isNotBlank()) "${s.no} ${s.name}" else s.name, styleOf(k)) } ?: XlsxWriter.Cell(null) })
        }
        x.sheet("明细") {
            widths = listOf(12.0, 10.0, 10.0, 8.0, 30.0)
            rowCells(hdr("学号", "姓名", "小组", "状态", "备注"))
            for (s in c.students) {
                val k = statuses[s.id] ?: HwStatus.NONE
                rowCells(listOf(XlsxWriter.Cell(s.no), XlsxWriter.Cell(s.name), XlsxWriter.Cell(s.group), XlsxWriter.Cell(k.label, styleOf(k)), XlsxWriter.Cell(comments[s.id] ?: "")))
            }
        }
        x.write(out)
    }

    /** 写统计名单到导出目录并返回文件（用于分享） */
    fun statListFile(ctx: Context, c: SchoolClass, title: String, subject: String, createdAt: Long, statuses: Map<String, HwStatus>, comments: Map<String, String> = emptyMap()): File =
        File(exportDir(ctx), "${listOf(subject, title).filter { it.isNotBlank() }.joinToString("_").ifBlank { "作业" }}_统计名单_${fileStamp()}.xlsx".replace(Regex("[\\\\/:*?\"<>|]"), "_"))
            .also { f -> f.outputStream().use { writeStatList(c, title, subject, createdAt, statuses, comments, it) } }

    fun csv(c: SchoolClass, a: Assignment, rules: ScoreRules): String = buildString {
        append('﻿')
        append("学号,姓名,小组,状态,本次分值,备注\n")
        for (s in c.students) {
            val st = a.statuses[s.id] ?: HwStatus.NONE
            append(listOf(s.no, s.name, s.group, st.label, rules.of(st).fmt(), a.comments[s.id] ?: "").joinToString(",") { q(it) })
            append('\n')
        }
    }

    private fun q(s: String) = if (s.any { it == ',' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s

    fun shareFile(ctx: Context, file: File, mime: String, title: String = "分享") {
        val uri: Uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(intent, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun shareText(ctx: Context, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        ctx.startActivity(Intent.createChooser(intent, "分享").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun exportDir(ctx: Context): File = File(ctx.cacheDir, "export").apply { mkdirs() }

    fun summaryText(c: SchoolClass, a: Assignment): String {
        val sm = c.summary(a)
        fun names(s: HwStatus) = c.students.filter { (a.statuses[it.id] ?: HwStatus.NONE) == s }.joinToString("、") { it.name }
        return buildString {
            append("📚【${c.name}】${a.subject} ${a.title}\n")
            append("📅 ${fullFmt.format(Date(a.createdAt))}\n")
            append("✅ 已交 ${sm.handedIn}/${sm.expected}（${(sm.rate * 100).toInt()}%）")
            a.countedBooks?.let { append("  📦 实数 $it 本") }
            append('\n')
            val miss = names(HwStatus.MISSING)
            val none = names(HwStatus.NONE)
            val late = names(HwStatus.LATE)
            val leave = names(HwStatus.LEAVE)
            if (miss.isNotEmpty()) append("❌ 未交：$miss\n")
            if (none.isNotEmpty()) append("⏳ 未登记：$none\n")
            if (late.isNotEmpty()) append("🐢 迟交：$late\n")
            if (leave.isNotEmpty()) append("🏖️ 请假：$leave\n")
            val ex = names(HwStatus.EXCELLENT)
            if (ex.isNotEmpty()) append("🌟 优秀：$ex\n")
        }
    }

    /** 生成一张精美的收作业战报海报 */
    fun poster(c: SchoolClass, a: Assignment, primary: Int, secondary: Int): Bitmap {
        val w = 1080
        val sm = c.summary(a)
        val missing = c.students.filter { (a.statuses[it.id] ?: HwStatus.NONE) in setOf(HwStatus.MISSING, HwStatus.NONE) }
        val late = c.students.filter { a.statuses[it.id] == HwStatus.LATE }
        val stars = c.students.filter { a.statuses[it.id] == HwStatus.EXCELLENT }
        val chipRows = (missing.size + 3) / 4 + (late.size + 3) / 4 + (stars.size + 3) / 4
        val h = 980 + chipRows * 96 + 260
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), primary, secondary, Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        p.shader = null
        // 装饰圆
        p.color = Color.argb(28, 255, 255, 255)
        cv.drawCircle(w - 120f, 160f, 260f, p)
        cv.drawCircle(80f, h - 140f, 200f, p)

        val card = RectF(48f, 300f, w - 48f, h - 120f)
        p.color = Color.WHITE
        p.setShadowLayer(30f, 0f, 12f, Color.argb(60, 0, 0, 0))
        cv.drawRoundRect(card, 56f, 56f, p)
        p.clearShadowLayer()

        val t = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 64f; typeface = Typeface.DEFAULT_BOLD }
        cv.drawText("${c.emoji} ${c.name}", 72f, 140f, t)
        t.textSize = 40f; t.typeface = Typeface.DEFAULT
        cv.drawText("${a.subject}  ${a.title}", 72f, 210f, t)
        t.textSize = 30f; t.color = Color.argb(200, 255, 255, 255)
        cv.drawText(fullFmt.format(Date()) + " · 作业通 战报", 72f, 260f, t)

        // 进度环
        val cx = 260f
        val cy = 540f
        val r = 150f
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 36f; strokeCap = Paint.Cap.ROUND }
        ring.color = Color.argb(30, 0, 0, 0)
        cv.drawCircle(cx, cy, r, ring)
        ring.color = primary
        cv.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), -90f, 360f * sm.rate, false, ring)
        val big = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1B1B1F.toInt(); textSize = 88f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
        cv.drawText("${(sm.rate * 100).toInt()}%", cx, cy + 28f, big)
        big.textSize = 30f; big.typeface = Typeface.DEFAULT; big.color = 0xFF6B6B75.toInt()
        cv.drawText("提交率", cx, cy + 78f, big)

        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 40f; color = 0xFF1B1B1F.toInt() }
        val lines = listOf(
            "✅ 已交  ${sm.handedIn} / ${sm.expected}",
            "❌ 未交  ${sm.c(HwStatus.MISSING) + sm.c(HwStatus.NONE)}",
            "🐢 迟交  ${sm.c(HwStatus.LATE)}",
            "🏖️ 请假  ${sm.c(HwStatus.LEAVE)}",
        ) + (a.countedBooks?.let { listOf("📦 实数  $it 本") } ?: emptyList())
        lines.forEachIndexed { i, s -> cv.drawText(s, 480f, 440f + i * 64f, label) }

        var y = 760f
        fun section(title: String, list: List<Student>, chipColor: Int) {
            if (list.isEmpty()) return
            label.textSize = 38f; label.typeface = Typeface.DEFAULT_BOLD
            cv.drawText(title, 96f, y, label)
            y += 30f
            label.textSize = 34f; label.typeface = Typeface.DEFAULT
            val chip = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = chipColor }
            list.forEachIndexed { i, s ->
                val col = i % 4
                if (col == 0 && i > 0) y += 96f
                val left = 96f + col * 222f
                cv.drawRoundRect(RectF(left, y, left + 204f, y + 76f), 38f, 38f, chip)
                val txt = s.name.take(4)
                val tw = label.measureText(txt)
                cv.drawText(txt, left + 102f - tw / 2, y + 50f, label)
            }
            y += 140f
        }
        section("还没交的同学", missing, 0xFFFFE3E3.toInt())
        section("迟交", late, 0xFFFFF1C9.toInt())
        section("今日之星 🌟", stars, 0xFFFFE9A8.toInt())
        if (missing.isEmpty()) {
            label.textSize = 48f; label.typeface = Typeface.DEFAULT_BOLD
            cv.drawText("🎉 全员交齐，太棒了！", 96f, y + 20f, label)
        }
        t.color = Color.WHITE; t.textSize = 30f
        cv.drawText("由「作业通」生成", w / 2f - t.measureText("由「作业通」生成") / 2, h - 50f, t)
        return bmp
    }

    fun qrBitmap(content: String, size: Int = 256): Bitmap {
        val m = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1, EncodeHintType.CHARACTER_SET to "UTF-8"))
        val px = IntArray(size * size)
        for (yy in 0 until size) for (xx in 0 until size) px[yy * size + xx] = if (m[xx, yy]) Color.BLACK else Color.WHITE
        return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
    }

    /** 学号二维码贴纸 PDF（A4，每页 3×7 张），贴在作业本封面上识别率接近 100% */
    fun qrLabelsPdf(c: SchoolClass, file: File) {
        val doc = PdfDocument()
        val pageW = 595
        val pageH = 842
        val cols = 3
        val rows = 7
        val cellW = (pageW - 40) / cols
        val cellH = (pageH - 40) / rows
        val per = cols * rows
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.LTGRAY; strokeWidth = 0.6f; pathEffect = android.graphics.DashPathEffect(floatArrayOf(4f, 3f), 0f) }
        c.students.chunked(per).forEachIndexed { pi, chunk ->
            val page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pi + 1).create())
            val cv = page.canvas
            chunk.forEachIndexed { i, s ->
                val col = i % cols
                val row = i / cols
                val left = 20f + col * cellW
                val top = 20f + row * cellH
                cv.drawRoundRect(RectF(left + 3, top + 3, left + cellW - 3, top + cellH - 3), 8f, 8f, border)
                val qs = cellH - 22
                val qr = qrBitmap("HW:${s.no}", 240)
                cv.drawBitmap(qr, null, RectF(left + 8, top + 11, left + 8 + qs, top + 11 + qs), p)
                p.color = Color.BLACK; p.textSize = 17f; p.typeface = Typeface.DEFAULT_BOLD
                cv.drawText(s.name, left + qs + 16, top + cellH / 2f - 6, p)
                p.textSize = 13f; p.typeface = Typeface.MONOSPACE
                cv.drawText(s.no, left + qs + 16, top + cellH / 2f + 14, p)
                p.textSize = 9f; p.typeface = Typeface.DEFAULT; p.color = Color.GRAY
                cv.drawText(c.name.take(12), left + qs + 16, top + cellH / 2f + 30, p)
            }
            doc.finishPage(page)
        }
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
    }

    fun savePng(bmp: Bitmap, file: File) {
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
