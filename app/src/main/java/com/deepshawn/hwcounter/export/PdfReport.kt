package com.deepshawn.hwcounter.export

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import com.deepshawn.hwcounter.data.Analytics
import com.deepshawn.hwcounter.data.HwStatus
import com.deepshawn.hwcounter.data.SchoolClass
import com.deepshawn.hwcounter.data.fmt
import com.deepshawn.hwcounter.data.fmtDay
import com.deepshawn.hwcounter.data.signed
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 一键 PDF 分析报告（A4，多页）：概览指标、主要发现、趋势图、状态构成、进步之星与退步关注、
 * 学生分层与风险、分项对比、各轮对比、积分来源、学生明细、考试。
 *
 * 绘制与分页和输出目标无关（[render]），既能写 PDF，也能画到位图上做截图检查。
 */
object PdfReport {
    const val W = 595f
    const val H = 842f
    private const val M = 40f

    private val PRIMARY = Color.rgb(0x2F, 0x6B, 0xFF)
    private val GOOD = Color.rgb(0x16, 0x9B, 0x68)
    private val WARN = Color.rgb(0xE8, 0x79, 0x2C)
    private val BAD = Color.rgb(0xE5, 0x38, 0x4D)
    private val INK = Color.rgb(0x1C, 0x1D, 0x22)
    private val MUTED = Color.rgb(0x76, 0x7B, 0x86)
    private val LINE = Color.rgb(0xE3, 0xE6, 0xEC)
    private val SOFT = Color.rgb(0xF4, 0xF6, 0xFA)
    private val STATUS = mapOf(
        HwStatus.SUBMITTED to Color.rgb(0x1D, 0xB5, 0x7A), HwStatus.EXCELLENT to Color.rgb(0xF2, 0xB1, 0x00), HwStatus.LEAVE to Color.rgb(0x4A, 0x8C, 0xFF),
        HwStatus.LATE to Color.rgb(0xFF, 0x8A, 0x3D), HwStatus.MISSING to Color.rgb(0xF0, 0x47, 0x5A), HwStatus.NONE to Color.rgb(0xC4, 0xC8, 0xD0),
    )
    private val ORDER = listOf(HwStatus.SUBMITTED, HwStatus.EXCELLENT, HwStatus.LEAVE, HwStatus.LATE, HwStatus.MISSING, HwStatus.NONE)

    private fun pct(x: Float) = "${(x * 100).roundToInt()}%"
    private fun pct(x: Double) = "${(x * 100).roundToInt()}%"

    fun write(c: SchoolClass, r: Analytics.Report, e: Analytics.ExamReport, out: OutputStream, now: Long = System.currentTimeMillis()) {
        val doc = PdfDocument()
        var page: PdfDocument.Page? = null
        var n = 0
        render(c, r, e, now, newPage = {
            n++
            page = doc.startPage(PdfDocument.PageInfo.Builder(W.toInt(), H.toInt(), n).create())
            page!!.canvas
        }, endPage = { page?.let { doc.finishPage(it) }; page = null })
        doc.writeTo(out)
        doc.close()
    }

    /** 绘制全部页面；返回页数 */
    fun render(c: SchoolClass, r: Analytics.Report, e: Analytics.ExamReport, now: Long, newPage: () -> Canvas, endPage: () -> Unit): Int {
        val p = Pager(c, now, newPage, endPage)
        p.start()
        cover(p, c, r, e, now)
        findings(p, r)
        trends(p, r)
        progress(p, r)
        students(p, r)
        breakdown(p, r)
        scores(p, r)
        detail(p, r)
        exams(p, e)
        p.finish()
        return p.pages
    }

    // ---------------- 分页与基础绘制 ----------------

    private class Pager(val c: SchoolClass, val now: Long, val newPage: () -> Canvas, val endPage: () -> Unit) {
        lateinit var cv: Canvas
        var y = M
        var pages = 0
        val bottom = H - M - 18f

        fun start() { open() }
        private fun open() {
            cv = newPage(); pages++
            cv.drawColor(Color.WHITE)
            y = M
            if (pages > 1) {
                text("${c.name} · 作业与成绩分析报告", 9f, MUTED, x = M, maxW = W - 2 * M)
                cv.drawLine(M, y + 2f, W - M, y + 2f, paint(LINE, 0.8f)); y += 12f
            }
        }
        private fun footer() {
            val tp = tp(8.5f, MUTED)
            cv.drawText("第 $pages 页 · 生成于 ${SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.CHINA).format(Date(now))} · 作业登记", M, H - M + 6f, tp)
        }
        fun ensure(h: Float) { if (y + h > bottom) { footer(); endPage(); open() } }
        fun finish() { footer(); endPage() }
        fun gap(h: Float) { y += h }

        fun tp(size: Float, color: Int, bold: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size; this.color = color; typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        fun paint(color: Int, stroke: Float = 0f) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color; if (stroke > 0) { style = Paint.Style.STROKE; strokeWidth = stroke }
        }

        fun layout(t: String, size: Float, color: Int, bold: Boolean, w: Float, align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL, lines: Int = Int.MAX_VALUE): StaticLayout =
            StaticLayout.Builder.obtain(t, 0, t.length, tp(size, color, bold), w.toInt().coerceAtLeast(10))
                .setAlignment(align).setLineSpacing(0f, 1.18f).setIncludePad(false).setMaxLines(lines).setEllipsize(TextUtils.TruncateAt.END).build()

        /** 在 (x, y) 画一段文字并下移；返回高度 */
        fun text(t: String, size: Float, color: Int = INK, bold: Boolean = false, x: Float = M, maxW: Float = W - 2 * M, move: Boolean = true): Float {
            val l = layout(t, size, color, bold, maxW)
            ensure(l.height.toFloat())
            cv.save(); cv.translate(x, y); l.draw(cv); cv.restore()
            if (move) y += l.height
            return l.height.toFloat()
        }
        /** 不换页、不移动，在指定位置画一行（超长省略） */
        fun cell(t: String, x: Float, top: Float, w: Float, size: Float = 9.5f, color: Int = INK, bold: Boolean = false, align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL) {
            val l = layout(t, size, color, bold, w, align, 1)
            cv.save(); cv.translate(x, top); l.draw(cv); cv.restore()
        }

        fun heading(t: String) {
            ensure(60f)
            y += 10f
            cv.drawRoundRect(RectF(M, y + 2f, M + 4f, y + 17f), 2f, 2f, paint(PRIMARY))
            text(t, 14f, INK, true, x = M + 12f)
            y += 6f
        }
        /** 小标题；need = 标题后紧跟内容需要的高度，避免标题孤零零留在页底 */
        fun sub(t: String, need: Float = 30f) { ensure(need + 22f); y += 4f; text(t, 11f, INK, true); y += 3f }
        fun note(t: String) { text(t, 9f, MUTED); y += 2f }
    }

    // ---------------- 各部分 ----------------

    private fun cover(p: Pager, c: SchoolClass, r: Analytics.Report, e: Analytics.ExamReport, now: Long) {
        val cv = p.cv
        cv.drawRoundRect(RectF(M, p.y, W - M, p.y + 92f), 14f, 14f, p.paint(PRIMARY))
        val top = p.y
        p.cell("${c.name} 作业与成绩分析报告", M + 18f, top + 16f, W - 2 * M - 36f, 19f, Color.WHITE, true)
        p.cell("范围：${r.window.label}", M + 18f, top + 46f, W - 2 * M - 36f, 10f, Color.argb(230, 255, 255, 255))
        p.cell("${c.students.size} 名学生 · ${c.assignments.size} 次作业 · ${c.exams.size} 场考试 · 生成于 ${SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.CHINA).format(Date(now))}", M + 18f, top + 64f, W - 2 * M - 36f, 9.5f, Color.argb(220, 255, 255, 255))
        p.y = top + 108f
        val k = r.kpi; val pv = r.previous
        fun d(cur: Float, prev: Float?, up: Boolean = true): Pair<String, Int> {
            if (prev == null) return "" to MUTED
            val pts = ((cur - prev) * 100).roundToInt()
            val good = if (up) pts >= 0 else pts <= 0
            return (if (pts == 0) "与${r.window.prevLabel}持平" else "${if (pts > 0) "▲" else "▼"} ${abs(pts)} 个百分点") to (if (pts == 0) MUTED else if (good) GOOD else BAD)
        }
        val tiles = listOf(
            Triple("交齐率", pct(k.handInRate), d(k.handInRate, pv?.handInRate)),
            Triple("按时率", pct(k.onTimeRate), d(k.onTimeRate, pv?.onTimeRate)),
            Triple("优秀率", pct(k.excellentRate), d(k.excellentRate, pv?.excellentRate)),
            Triple("缺交率", pct(k.missingRate), d(k.missingRate, pv?.missingRate, false)),
            Triple("迟交率", pct(k.lateRate), d(k.lateRate, pv?.lateRate, false)),
            Triple("全员交齐", "${k.perfect} 次", "最长连续 ${r.longestPerfectRun} 次" to MUTED),
            Triple("平均积分", r.avgScore.fmt(), (if (r.range != Analytics.Range.ALL) "本时段 ${r.avgScoreDelta.signed()}" else "") to MUTED),
            Triple("考试", "${r.examCount} 场", (e.avgPct.lastOrNull()?.let { "最近平均 ${pct(it.second)}" } ?: "") to MUTED),
        )
        val tw = (W - 2 * M - 3 * 8f) / 4
        tiles.chunked(4).forEach { row ->
            p.ensure(62f)
            val t0 = p.y
            row.forEachIndexed { i, (label, value, delta) ->
                val x = M + i * (tw + 8f)
                p.cv.drawRoundRect(RectF(x, t0, x + tw, t0 + 56f), 10f, 10f, p.paint(SOFT))
                p.cell(label, x + 10f, t0 + 7f, tw - 20f, 8.5f, MUTED)
                p.cell(value, x + 10f, t0 + 19f, tw - 20f, 16f, INK, true)
                p.cell(delta.first, x + 10f, t0 + 40f, tw - 20f, 7.5f, delta.second)
            }
            p.y = t0 + 64f
        }
        p.note("${k.assignments} 次作业 · 应交 ${k.expected} 人次 · 缺交 ${k.missing} · 迟交 ${k.late} · 请假 ${k.leave}" + (if (k.unregistered > 0) " · 未登记 ${k.unregistered}" else "") + (pv?.let { " · 对比${r.window.prevLabel}（${it.assignments} 次）" } ?: ""))
    }

    private fun findings(p: Pager, r: Analytics.Report) {
        p.heading("一、主要发现")
        if (r.insights.isEmpty()) { p.note("数据还不够多，暂时没有明显的结论。"); return }
        r.insights.forEach { s ->
            val col = when (s.level) { Analytics.Level.GOOD -> GOOD; Analytics.Level.WARN -> WARN; else -> PRIMARY }
            val l = p.layout(s.text, 10.5f, INK, false, W - 2 * M - 16f)
            p.ensure(l.height + 6f)
            p.cv.drawCircle(M + 4f, p.y + 7f, 3.2f, p.paint(col))
            p.cv.save(); p.cv.translate(M + 14f, p.y); l.draw(p.cv); p.cv.restore()
            p.y += l.height + 6f
        }
    }

    /** 折线（0–1），带网格和首中尾标签 */
    private fun line(p: Pager, values: List<Float>, labels: List<String>, h: Float = 120f, color: Int = PRIMARY) {
        if (values.size < 2) return
        p.ensure(h + 26f)
        val x0 = M + 28f; val x1 = W - M - 6f; val top = p.y + 4f; val bot = top + h
        listOf(0f, 0.5f, 1f).forEach { g ->
            val yy = bot - (bot - top) * g
            p.cv.drawLine(x0, yy, x1, yy, p.paint(LINE, 0.7f))
            p.cell("${(g * 100).toInt()}%", M, yy - 6f, 26f, 7.5f, MUTED)
        }
        val avg = values.average().toFloat()
        val ay = bot - (bot - top) * avg
        p.cv.drawLine(x0, ay, x1, ay, p.paint(Color.argb(120, Color.red(color), Color.green(color), Color.blue(color)), 0.8f).apply { pathEffect = android.graphics.DashPathEffect(floatArrayOf(4f, 3f), 0f) })
        fun x(i: Int) = x0 + (x1 - x0) * i / (values.size - 1)
        fun yv(v: Float) = bot - (bot - top) * v.coerceIn(0f, 1f)
        val area = Path().apply { moveTo(x(0), bot); values.forEachIndexed { i, v -> lineTo(x(i), yv(v)) }; lineTo(x(values.lastIndex), bot); close() }
        p.cv.drawPath(area, p.paint(Color.argb(28, Color.red(color), Color.green(color), Color.blue(color))))
        val path = Path().apply { values.forEachIndexed { i, v -> if (i == 0) moveTo(x(i), yv(v)) else lineTo(x(i), yv(v)) } }
        p.cv.drawPath(path, p.paint(color, 1.8f).apply { strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND })
        if (values.size <= 40) values.forEachIndexed { i, v -> p.cv.drawCircle(x(i), yv(v), 2.4f, p.paint(color)) }
        val idx = if (labels.size <= 7) labels.indices.toList() else listOf(0, labels.size / 2, labels.lastIndex)
        idx.forEach { i ->
            val cx = x(i)
            p.cell(labels[i], (cx - 30f).coerceIn(x0 - 10f, x1 - 60f), bot + 4f, 60f, 7.5f, MUTED, align = Layout.Alignment.ALIGN_CENTER)
        }
        p.y = bot + 18f
    }

    /** 横向条形：标签 + 比例 + 数值 */
    private fun bars(p: Pager, items: List<Triple<String, Float, String>>, color: Int = PRIMARY, labelW: Float = 70f) {
        items.forEach { (label, v, txt) ->
            p.ensure(16f)
            val t = p.y
            p.cell(label, M, t, labelW - 6f, 9f, INK)
            val bx = M + labelW; val bw = W - 2 * M - labelW - 70f
            p.cv.drawRoundRect(RectF(bx, t + 3f, bx + bw, t + 11f), 4f, 4f, p.paint(SOFT))
            p.cv.drawRoundRect(RectF(bx, t + 3f, bx + bw * v.coerceIn(0.01f, 1f), t + 11f), 4f, 4f, p.paint(color))
            p.cell(txt, bx + bw + 6f, t, 64f, 9f, INK, true)
            p.y += 15f
        }
    }

    /** 表格：列宽为比例；超出一页自动续表并重画表头 */
    private fun table(p: Pager, headers: List<String>, widths: List<Float>, rows: List<List<String>>, colors: List<List<Int?>>? = null, rowH: Float = 16f) {
        val total = widths.sum(); val tw = W - 2 * M
        val xs = widths.runningFold(M) { acc, w -> acc + tw * w / total }
        fun head() {
            p.ensure(rowH * 2)
            p.cv.drawRect(RectF(M, p.y, W - M, p.y + rowH), p.paint(SOFT))
            headers.forEachIndexed { i, h -> p.cell(h, xs[i] + 4f, p.y + 3.5f, xs[i + 1] - xs[i] - 8f, 8.5f, MUTED, true) }
            p.y += rowH
        }
        head()
        rows.forEachIndexed { ri, row ->
            if (p.y + rowH > p.bottom) { p.ensure(rowH * 3); head() }
            row.forEachIndexed { i, v -> p.cell(v, xs[i] + 4f, p.y + 3.5f, xs[i + 1] - xs[i] - 8f, 9f, colors?.getOrNull(ri)?.getOrNull(i) ?: INK) }
            p.cv.drawLine(M, p.y + rowH, W - M, p.y + rowH, p.paint(LINE, 0.5f))
            p.y += rowH
        }
        p.y += 6f
    }

    private fun trends(p: Pager, r: Analytics.Report) {
        if (r.points.isEmpty()) return
        p.heading("二、趋势与构成")
        val f = SimpleDateFormat("M/d", Locale.CHINA)
        p.sub("每次作业交齐率（虚线为平均 ${pct(r.points.map { it.summary.rate }.average())}）", 146f)
        line(p, r.points.map { it.summary.rate }, r.points.map { f.format(Date(it.assignment.createdAt)) })
        if (r.points.size >= 4) p.note("线性趋势：平均每次${if (r.slope >= 0) "上升" else "下降"} ${"%.1f".format(abs(r.slope * 100))} 个百分点" + if (r.longestPerfectRun >= 2) " · 最长连续 ${r.longestPerfectRun} 次全员交齐" else "")
        if (r.buckets.size >= 2) {
            p.sub("按${r.bucketUnit}汇总", 116f)
            line(p, r.buckets.map { it.rate }, r.buckets.map { it.label }, 90f, GOOD)
        }
        p.sub("状态构成（人次）", 40f)
        val total = r.totals.values.sum().coerceAtLeast(1)
        p.ensure(40f)
        var x = M; val t = p.y; val w = W - 2 * M
        ORDER.forEach { s -> val n = r.totals[s] ?: 0; if (n > 0) { val ww = w * n / total; p.cv.drawRect(RectF(x, t, x + ww, t + 14f), p.paint(STATUS.getValue(s))); x += ww } }
        p.y = t + 20f
        var lx = M
        ORDER.forEach { s ->
            val n = r.totals[s] ?: 0
            p.cv.drawRoundRect(RectF(lx, p.y + 2f, lx + 8f, p.y + 10f), 2f, 2f, p.paint(STATUS.getValue(s)))
            p.cell("${s.label} $n（${pct(n.toFloat() / total)}）", lx + 11f, p.y, 75f, 8.5f, INK)
            lx += 86f
        }
        p.y += 18f
    }

    private fun progress(p: Pager, r: Analytics.Report) {
        val pg = r.progress ?: return
        p.heading("三、进步之星与退步关注")
        p.note("比较方式：${pg.basis}。综合指数 = 交齐率变化×45% + 按时率×20% + 优秀率×15% + 考试得分率×20%。")
        fun block(title: String, list: List<Analytics.StudentProgress>, color: Int) {
            p.sub("$title（${list.size} 人）", 50f)
            if (list.isEmpty()) { p.note("暂无"); return }
            table(
                p, listOf("姓名", "以往 → 重置后交齐率", "考试得分率", "指数", "主要变化"), listOf(1f, 1.6f, 1.2f, 0.6f, 3f),
                list.map { s ->
                    listOf(
                        s.student.name, "${pct(s.before.rate)} → ${pct(s.after.rate)}",
                        if (s.before.examPct != null && s.after.examPct != null) "${pct(s.before.examPct)} → ${pct(s.after.examPct)}" else "—",
                        (s.index * 100).roundToInt().let { if (it >= 0) "+$it" else "$it" }, s.reasons.take(2).joinToString("，"),
                    )
                },
                list.map { listOf(null, null, null, color, MUTED) },
            )
        }
        block("进步之星", pg.stars, GOOD)
        block("退步关注", pg.watch, BAD)
    }

    private fun students(p: Pager, r: Analytics.Report) {
        p.heading("四、学生分层与预警")
        val total = r.tiers.sumOf { it.students.size }.coerceAtLeast(1)
        val tierColors = listOf(GOOD, PRIMARY, WARN, BAD)
        p.ensure(24f)
        var x = M; val t = p.y; val w = W - 2 * M
        r.tiers.forEachIndexed { i, tier -> val n = tier.students.size; if (n > 0) { val ww = w * n / total; p.cv.drawRect(RectF(x, t, x + ww, t + 12f), p.paint(tierColors[i])); x += ww } }
        p.y = t + 18f
        r.tiers.forEachIndexed { i, tier ->
            val l = p.layout("${tier.name}（${tier.desc}）${tier.students.size} 人：" + tier.students.joinToString("、") { it.student.name }.ifBlank { "—" }, 9.5f, INK, false, W - 2 * M - 14f)
            p.ensure(l.height + 4f)
            p.cv.drawRoundRect(RectF(M, p.y + 3f, M + 8f, p.y + 11f), 2f, 2f, p.paint(tierColors[i]))
            p.cv.save(); p.cv.translate(M + 14f, p.y); l.draw(p.cv); p.cv.restore()
            p.y += l.height + 4f
        }
        p.note("交齐率中位数 ${pct(r.rateMedian)}，标准差 ${(r.rateStd * 100).roundToInt()} 个百分点" + if (r.rateStd >= 0.15f) "（两极分化）" else "")
        if (r.risk.isNotEmpty()) {
            p.sub("下次缺交风险")
            table(p, listOf("姓名", "风险", "依据"), listOf(1f, 0.7f, 4f), r.risk.map { listOf(it.student.name, pct(it.prob), it.why) }, r.risk.map { listOf(null, if (it.prob >= 0.6) BAD else WARN, MUTED) })
        }
        if (r.attention.isNotEmpty()) {
            p.sub("需要关注（缺交）")
            table(p, listOf("姓名", "缺交", "迟交", "连续未交", "交齐率"), listOf(1.2f, 0.7f, 0.7f, 0.9f, 0.9f), r.attention.map { listOf(it.student.name, "${it.missing}", "${it.late}", if (it.missingStreak >= 2) "${it.missingStreak} 次" else "—", pct(it.rate)) })
        }
        if (r.weakSubjects.isNotEmpty()) {
            p.sub("偏科提醒")
            table(p, listOf("姓名", "科目", "该科交齐率", "整体交齐率"), listOf(1f, 1f, 1f, 1f), r.weakSubjects.map { listOf(it.student.name, it.subject, pct(it.subjectRate), pct(it.overallRate)) })
        }
        if (r.streakers.isNotEmpty()) { p.sub("连续交齐榜"); p.text(r.streakers.joinToString("、") { "${it.student.name} ${it.handStreak} 次" }, 9.5f); p.gap(4f) }
        if (r.perfectAttendance.isNotEmpty()) { p.sub("全勤之星（${r.perfectAttendance.size} 人）"); p.text(r.perfectAttendance.joinToString("、") { it.student.name }, 9.5f); p.gap(4f) }
    }

    private fun breakdown(p: Pager, r: Analytics.Report) {
        if (r.points.isEmpty() && r.rounds.isEmpty()) return
        p.heading("五、分项对比")
        if (r.subjects.size > 1) { p.sub("各科交齐率"); bars(p, r.subjects.map { Triple(it.subject, it.rate, "${pct(it.rate)} · ${it.count} 次") }) }
        val days = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        val wd = r.weekday.mapIndexedNotNull { i, v -> v?.let { Triple(days[i], it, pct(it)) } }
        if (wd.size >= 2) { p.sub("星期分布（平均交齐率）"); bars(p, wd, Color.rgb(0x5B, 0x7C, 0xFA)) }
        if (r.groupStats.size > 1) {
            p.sub("小组对比")
            table(
                p, listOf("小组", "组长", "人数", "交齐率", "缺交", "平均积分", "积分变化", "考试得分率"), listOf(1f, 1f, 0.6f, 0.8f, 0.6f, 0.9f, 0.9f, 1f),
                r.groupStats.map { g -> listOf(g.name, g.leader?.name ?: "—", "${g.size}", pct(g.rate), "${g.missing}", g.avgScore.fmt(), g.scoreDelta.signed(), g.examPct?.let { pct(it) } ?: "—") },
            )
        }
        if (r.rounds.size > 1) {
            p.sub("各轮对比（以全班重置为分界）")
            table(
                p, listOf("轮次", "起始", "作业", "交齐率", "按时率", "优秀率", "缺交率", "考试得分率"), listOf(1.5f, 1.1f, 0.6f, 0.8f, 0.8f, 0.8f, 0.8f, 1f),
                r.rounds.map { x -> listOf(x.label, x.start?.let { fmtDay(it) } ?: "—", "${x.kpi.assignments}", pct(x.kpi.handInRate), pct(x.kpi.onTimeRate), pct(x.kpi.excellentRate), pct(x.kpi.missingRate), x.examPct?.let { pct(it) } ?: "—") },
            )
        }
    }

    private fun scores(p: Pager, r: Analytics.Report) {
        p.heading("六、积分")
        val s = r.sources
        val max = listOf(s.homework, s.manualPlus, s.manualMinus, s.exam).maxOf { abs(it) }.coerceAtLeast(1.0)
        p.sub("积分来源（本范围，全班合计）")
        bars(p, listOf(
            Triple("作业自动", (abs(s.homework) / max).toFloat(), s.homework.signed()),
            Triple("手动加分", (abs(s.manualPlus) / max).toFloat(), "+" + s.manualPlus.fmt()),
            Triple("手动扣分", (abs(s.manualMinus) / max).toFloat(), s.manualMinus.fmt()),
            Triple("考试计分", (abs(s.exam) / max).toFloat(), s.exam.signed()),
        ))
        if (r.reasons.isNotEmpty()) {
            p.sub("加减分原因")
            table(p, listOf("原因", "次数", "涉及人数", "合计"), listOf(2.4f, 0.8f, 0.8f, 0.8f), r.reasons.map { listOf(it.reason, "${it.count}", "${it.people}", it.total.signed()) }, r.reasons.map { listOf(null, null, null, if (it.total >= 0) GOOD else BAD) })
        }
        if (r.scoreTrend.size >= 2) {
            p.sub("全班平均积分走势", 120f)
            val v = r.scoreTrend.map { it.second }; val lo = v.min(); val hi = v.max(); val span = (hi - lo).coerceAtLeast(1.0)
            line(p, v.map { ((it - lo) / span * 0.8 + 0.1).toFloat() }, r.scoreTrend.map { SimpleDateFormat("M/d", Locale.CHINA).format(Date(it.first.createdAt)) }, 80f, Color.rgb(0x7A, 0x5C, 0xFF))
            p.note("纵轴按 ${lo.fmt()} – ${hi.fmt()} 分缩放；最新平均 ${v.last().fmt()} 分")
        }
        if (r.risers.isNotEmpty() || r.fallers.isNotEmpty()) {
            p.sub("积分涨跌")
            p.text("上升：" + r.risers.joinToString("、") { "${it.student.name} ${it.scoreDelta.signed()}" }.ifBlank { "—" }, 9.5f, GOOD)
            p.text("下降：" + r.fallers.joinToString("、") { "${it.student.name} ${it.scoreDelta.signed()}" }.ifBlank { "—" }, 9.5f, BAD)
            p.gap(4f)
        }
    }

    private fun detail(p: Pager, r: Analytics.Report) {
        if (r.students.isEmpty()) return
        p.heading("七、学生明细（按学号）")
        val list = r.students.sortedWith { a, b -> com.deepshawn.hwcounter.data.compareStudentNumbers(a.student.no, b.student.no) }
        table(
            p, listOf("学号", "姓名", "应交", "交齐率", "按时率", "优秀", "迟交", "缺交", "连续交齐", "积分", "变化", "名次"),
            listOf(1.2f, 1f, 0.55f, 0.7f, 0.7f, 0.55f, 0.55f, 0.55f, 0.75f, 0.65f, 0.65f, 0.55f),
            list.map { s ->
                listOf(s.student.no, s.student.name, "${s.expected}", pct(s.rate), pct(s.onTimeRate), "${s.excellent}", "${s.late}", "${s.missing}", "${s.handStreak}", s.score.fmt(), if (r.range == Analytics.Range.ALL) "—" else s.scoreDelta.signed(), "${s.rank}")
            },
            list.map { s -> listOf(null, null, null, if (s.rate < 0.7f) BAD else if (s.rate >= 0.999f) GOOD else null, null, null, null, if (s.missing > 0) BAD else null, null, null, if (s.scoreDelta < 0) BAD else if (s.scoreDelta > 0) GOOD else null, null) },
            rowH = 15f,
        )
    }

    private fun exams(p: Pager, e: Analytics.ExamReport) {
        if (e.exams.isEmpty()) return
        p.heading("八、考试")
        if (e.avgPct.size >= 2) {
            p.sub("平均得分率走势", 116f)
            line(p, e.avgPct.map { it.second.toFloat() }, e.avgPct.map { SimpleDateFormat("M/d", Locale.CHINA).format(Date(it.first.date)) }, 90f, Color.rgb(0x7A, 0x5C, 0xFF))
        }
        table(
            p, listOf("日期", "科目", "考试", "满分", "人数", "缺考", "平均", "中位", "最高", "标准差", "及格率", "优秀率"),
            listOf(0.9f, 0.6f, 1.4f, 0.55f, 0.5f, 0.5f, 0.6f, 0.6f, 0.6f, 0.65f, 0.65f, 0.65f),
            e.exams.map { x -> val s = e.stats.getValue(x.id); listOf(SimpleDateFormat("M月d日", Locale.CHINA).format(Date(x.date)), x.subject, x.title, x.fullScore.fmt(), "${s.count}", "${s.absent}", s.avg.fmt(), s.median.fmt(), s.max.fmt(), s.std.fmt(), pct(s.passRate), pct(s.excellentRate)) },
        )
        if (e.subjects.size > 1) { p.sub("各科平均得分率"); bars(p, e.subjects.map { Triple(it.first, it.second.toFloat(), pct(it.second)) }, Color.rgb(0x7A, 0x5C, 0xFF)) }
        if (e.insights.isNotEmpty()) {
            p.sub("考试结论")
            e.insights.forEach { s -> p.text("• ${s.text}", 10f); p.gap(2f) }
        }
        if (e.top.isNotEmpty()) p.text("平均得分率最高：" + e.top.joinToString("、") { "${it.student.name} ${pct(it.avgPct ?: 0.0)}" }, 9.5f, GOOD)
        if (e.struggling.isNotEmpty()) p.text("平均得分率不足 60%：" + e.struggling.joinToString("、") { "${it.student.name} ${pct(it.avgPct ?: 0.0)}" }, 9.5f, BAD)
    }
}
