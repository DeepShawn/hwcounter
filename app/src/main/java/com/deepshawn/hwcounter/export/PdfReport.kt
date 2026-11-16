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

    private class Pager(val c: SchoolClass, val now: Long, val newPage: () -> Canvas, val endPage: () -> Unit, val header: String = "${c.name} · 作业与成绩分析报告") {
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
                text(header, 9f, MUTED, x = M, maxW = W - 2 * M)
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
        if (e.quality.isNotEmpty()) {
            p.sub("难度与区分度", 50f)
            table(p, listOf("考试", "平均得分率", "难度", "下四分位", "上四分位", "区分度"), listOf(1.8f, 0.9f, 0.6f, 0.8f, 0.8f, 1f),
                e.quality.map { q -> listOf("${q.exam.subject} ${q.exam.title}".trim(), pct(q.difficulty), Analytics.difficultyText(q.difficulty), q.q1.fmt(), q.q3.fmt(), "%.2f ".format(q.discrimination) + Analytics.discriminationText(q.discrimination)) })
        }
        if (e.levels.any { it.second > 0 }) { p.sub("成绩层级（按平均得分率）"); p.text(e.levels.joinToString("，") { "${it.first} ${it.second} 人" }, 9.5f); p.gap(4f) }
        fun prog(title: String, l: List<Analytics.ExamProgress>, col: Int) {
            if (l.isEmpty()) return
            p.sub(title, 50f)
            table(p, listOf("姓名", "最近一场", "得分率", "之前相对平均", "最近相对平均", "变化"), listOf(1f, 1.6f, 0.8f, 1f, 1f, 0.7f),
                l.map { x -> listOf(x.student.name, x.last.title, pct(x.lastPct), (x.before * 100).roundToInt().let { if (it >= 0) "+$it" else "$it" }, (x.after * 100).roundToInt().let { if (it >= 0) "+$it" else "$it" }, (x.delta * 100).roundToInt().let { if (it >= 0) "+$it" else "$it" }) },
                l.map { listOf(null, null, null, null, null, col) })
        }
        prog("考试进步之星", e.stars, GOOD); prog("考试退步关注", e.watch, BAD)
        e.flow?.let { f ->
            p.sub("层级流动：${f.from.title} → ${f.to.title}", 90f)
            table(p, listOf("上场 → 本场") + f.labels, listOf(1.4f, 1f, 1f, 1f, 1f), f.matrix.mapIndexed { i, r -> listOf(f.labels[i]) + r.map { if (it == 0) "" else "$it" } },
                f.matrix.mapIndexed { i, r -> listOf<Int?>(null) + r.indices.map { j -> if (j < i) GOOD else if (j > i) BAD else null } })
            p.note("升档 ${f.up} 人 · 降档 ${f.down} 人 · 不变 ${f.same} 人")
        }
        fun names(t: String, l: List<String>, col: Int = INK) { if (l.isNotEmpty()) { p.text("$t：" + l.joinToString("、"), 9.5f, col); p.gap(3f) } }
        if (e.nearPass.isNotEmpty() || e.nearExcellent.isNotEmpty() || e.rising.isNotEmpty() || e.falling.isNotEmpty() || e.imbalance.isNotEmpty() || e.diligentLow.isNotEmpty()) p.sub("重点学生")
        names("临界及格（55–60%）", e.nearPass.map { "${it.student.name} ${pct(it.avgPct ?: 0.0)}" }, WARN)
        names("临界优秀（80–85%）", e.nearExcellent.map { "${it.student.name} ${pct(it.avgPct ?: 0.0)}" }, PRIMARY)
        names("连续三场上升", e.rising.map { it.student.name }, GOOD)
        names("连续三场下滑", e.falling.map { it.student.name }, BAD)
        names("成绩稳定", e.steady.map { it.student.name })
        names("偏科", e.imbalance.map { "${it.student.name}（${it.best.first}${pct(it.best.second)} / ${it.worst.first}${pct(it.worst.second)}）" })
        names("作业交得齐但考试不理想", e.diligentLow.map { "${it.student.name}（交${pct(it.hwRate)} 考${pct(it.examPct)}）" }, WARN)
        names("作业常缺但考试不错", e.skipButGood.map { "${it.student.name}（交${pct(it.hwRate)} 考${pct(it.examPct)}）" })
        if (e.genders.size == 2) { p.sub("男女生对比"); bars(p, e.genders.map { Triple("${it.first}生 ${it.second}人", it.third.toFloat(), pct(it.third)) }, Color.rgb(0x7A, 0x5C, 0xFF)) }
        if (e.insights.isNotEmpty()) {
            p.sub("考试结论")
            e.insights.forEach { s -> p.text("• ${s.text}", 10f); p.gap(2f) }
        }
        if (e.top.isNotEmpty()) p.text("平均得分率最高：" + e.top.joinToString("、") { "${it.student.name} ${pct(it.avgPct ?: 0.0)}" }, 9.5f, GOOD)
        if (e.struggling.isNotEmpty()) p.text("平均得分率不足 60%：" + e.struggling.joinToString("、") { "${it.student.name} ${pct(it.avgPct ?: 0.0)}" }, 9.5f, BAD)
    }

    // ---------------- 其他文档：积分流水、结算、计分规则通知 ----------------

    /** 测试用：不写 PDF，而是画到给定的画布上（截图检查排版） */
    var testPages: Pair<() -> Canvas, () -> Unit>? = null

    private fun doc(out: OutputStream, c: SchoolClass, header: String, now: Long, block: (Pager) -> Unit) {
        testPages?.let { (np, ep) -> val p = Pager(c, now, np, ep, header); p.start(); block(p); p.finish(); return }
        val d = PdfDocument(); var page: PdfDocument.Page? = null; var n = 0
        val p = Pager(c, now, {
            n++; page = d.startPage(PdfDocument.PageInfo.Builder(W.toInt(), H.toInt(), n).create()); page!!.canvas
        }, { page?.let { d.finishPage(it) }; page = null }, header)
        p.start(); block(p); p.finish()
        d.writeTo(out); d.close()
    }

    /** 蓝色标题块 */
    private fun banner(p: Pager, title: String, sub1: String, sub2: String = "") {
        val top = p.y
        p.cv.drawRoundRect(RectF(M, top, W - M, top + 80f), 14f, 14f, p.paint(PRIMARY))
        p.cell(title, M + 18f, top + 14f, W - 2 * M - 36f, 18f, Color.WHITE, true)
        p.cell(sub1, M + 18f, top + 42f, W - 2 * M - 36f, 10f, Color.argb(230, 255, 255, 255))
        if (sub2.isNotBlank()) p.cell(sub2, M + 18f, top + 58f, W - 2 * M - 36f, 9.5f, Color.argb(220, 255, 255, 255))
        p.y = top + 94f
    }

    private fun stamp(t: Long) = SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.CHINA).format(Date(t))
    private fun day(t: Long) = SimpleDateFormat("yyyy/M/d", Locale.CHINA).format(Date(t))

    /** 积分流水 PDF：rows 为（学生名，流水行），已按需筛选 */
    fun writeLedger(c: SchoolClass, title: String, scope: String, rows: List<Pair<String, com.deepshawn.hwcounter.data.LedgerRow>>, out: OutputStream, now: Long = System.currentTimeMillis()) {
        doc(out, c, "${c.name} · $title", now) { p ->
            val counted = rows.filter { it.second.counted }
            val plus = counted.filter { it.second.delta > 0 }.sumOf { it.second.delta }; val minus = counted.filter { it.second.delta < 0 }.sumOf { it.second.delta }
            banner(p, "${c.name} $title", scope, "共 ${rows.size} 条 · 计入的加分 +${plus.fmt()} · 扣分 ${minus.fmt()} · 净变化 ${(plus + minus).signed()} · 导出于 ${stamp(now)}")
            val kinds = mapOf(
                com.deepshawn.hwcounter.data.LedgerRow.Kind.MANUAL to "手动", com.deepshawn.hwcounter.data.LedgerRow.Kind.AUTO to "作业",
                com.deepshawn.hwcounter.data.LedgerRow.Kind.EXAM to "考试", com.deepshawn.hwcounter.data.LedgerRow.Kind.SETTLE to "结算",
                com.deepshawn.hwcounter.data.LedgerRow.Kind.RECHARGE to "重置",
            )
            table(
                p, listOf("日期", "学生", "类型", "内容", "分值", "计入"), listOf(0.9f, 0.8f, 0.5f, 3.6f, 0.6f, 0.5f),
                rows.map { (name, r) -> listOf(day(r.time), name, kinds[r.kind] ?: "", r.title, if (r.kind == com.deepshawn.hwcounter.data.LedgerRow.Kind.RECHARGE) "→" else r.delta.signed(), if (r.counted) "是" else "否") },
                rows.map { (_, r) -> listOf(null, null, null, null, if (r.delta > 0) GOOD else if (r.delta < 0) BAD else null, if (r.counted) null else MUTED) },
                rowH = 15f,
            )
            p.note("“计入”为否的记录在积分重置之前，保留可查，但不计入当前积分。")
        }
    }

    /** 结算单 PDF */
    fun writeSettlement(c: SchoolClass, s: com.deepshawn.hwcounter.data.Settlement, out: OutputStream, now: Long = System.currentTimeMillis()) {
        val names = c.students.associate { it.id to it }
        doc(out, c, "${c.name} · ${s.title}", now) { p ->
            val done = s.lines.filter { !it.skipped }
            banner(p, "${c.name} ${s.title}", "范围：" + (s.roundStart?.let { "${day(it)} 起" } ?: "有记录以来") + " 至 ${day(s.roundEnd - 1)} · 结算于 ${stamp(s.time)}",
                "结算 ${done.size} 人 · 加分 ${done.count { it.delta > 0 }} 人 · 扣分 ${done.count { it.delta < 0 }} 人 · 合计 ${done.sumOf { it.delta }.signed()}" + if (s.resetId != null) " · 结算后开始新一轮" else "")
            val list = s.lines.sortedWith(compareByDescending<com.deepshawn.hwcounter.data.SettlementLine> { it.delta }.thenBy { names[it.studentId]?.no ?: "" })
            table(
                p, listOf("学号", "姓名", "应交", "交齐度", "迟交", "缺交", "最长连缺", "结算分", "原因"), listOf(0.9f, 0.8f, 0.5f, 0.6f, 0.5f, 0.5f, 0.7f, 0.6f, 3f),
                list.map { l -> val st = names[l.studentId]; listOf(st?.no ?: "", st?.name ?: "已删除", "${l.expected}", pct(l.rate), "${l.late}", "${l.missing}", "${l.longestMissing}", if (l.skipped) "不结算" else l.delta.signed(), l.reasons.joinToString("；")) },
                list.map { l -> listOf(null, null, null, if (l.rate < 0.7) BAD else if (l.rate >= 0.95) GOOD else null, null, if (l.missing > 0) BAD else null, null, if (l.delta > 0) GOOD else if (l.delta < 0) BAD else MUTED, MUTED) },
            )
        }
    }

    /** 计分规则通知 PDF（按当前设置生成） */
    fun writeRules(c: SchoolClass, rules: com.deepshawn.hwcounter.data.ScoreRules, out: OutputStream, now: Long = System.currentTimeMillis()) {
        val e = rules.escalation; val b = rules.streakBonus; val st = rules.settlement
        doc(out, c, "${c.name} · 积分计分标准", now) { p ->
            banner(p, "关于实行新积分计分标准的通知", "${c.name} · 自 ${SimpleDateFormat("yyyy年M月d日", Locale.CHINA).format(Date(now))} 起执行", "以前的积分已按新标准重新计算，历史记录全部保留可查")
            p.text("为鼓励同学们按时、完整地完成作业，班级积分采用以下计分标准。每位同学初始 ${rules.initial.fmt()} 分，以下各项自动计入。", 10.5f); p.gap(6f)
            p.heading("一、每次作业")
            table(p, listOf("情况", "计分", "说明"), listOf(1f, 0.8f, 3f), listOf(
                listOf("按时交", rules.submitted.signed().let { if (rules.submitted == 0.0) "0" else it }, "正常完成"),
                listOf("优秀", rules.excellent.signed(), "作业被评为优秀"),
                listOf("迟交", rules.late.signed(), "交了但晚于要求时间"),
                listOf("请假", rules.leave.signed().let { if (rules.leave == 0.0) "0" else it }, "不计次，不影响连续记录"),
                listOf("未交", if (e.enabled) "见第二条" else rules.missing.signed(), if (e.enabled) "连续未交加倍扣分" else "每次固定扣分"),
            ))
            if (e.enabled) {
                p.heading("二、未交连续加倍扣分")
                p.text("连续未交时，第 1 次扣 ${e.base.fmt()} 分，第 2 次扣 ${(e.base * e.base).fmt()} 分，第 3 次扣 ${(e.base * e.base * e.base).fmt()} 分，以此类推（每次翻倍）。", 10.5f)
                p.text("· 中间请假、未登记的不打断连续，也不计次；只要交过一次，就打断，下次未交重新从第 1 次算。", 10f)
                p.text("· 同一轮内累计未交超过 ${e.heavyAfter} 次后，改为第 1 次扣 ${e.heavyBase.fmt()} 分、第 2 次 ${(e.heavyBase * e.heavyBase).fmt()} 分、第 3 次 ${(e.heavyBase * e.heavyBase * e.heavyBase).fmt()} 分……", 10f)
                if (e.cap > 0) p.text("· 单次最多扣 ${e.cap.fmt()} 分。", 10f)
                p.text("· 每次积分重置后进入新一轮，连续次数和累计次数清零，重新从 ${e.base.fmt()} 分开始。", 10f)
                p.gap(4f)
                val ex = (1..6).map { k -> val v = minOf(Math.pow(e.base, k.toDouble()), if (e.cap > 0) e.cap else Double.MAX_VALUE); "第 $k 次 −${v.fmt()}" }
                p.text("举例：连续未交 " + ex.joinToString("，") + "。", 10f, MUTED)
                p.text("举例：未交、请假、未交 → 按连续两次计，分别扣 ${e.base.fmt()}、${(e.base * e.base).fmt()} 分；未交、交、未交 → 各扣 ${e.base.fmt()} 分。", 10f, MUTED)
            }
            if (b.enabled) {
                p.heading("三、连续交作业奖励")
                p.text("同一轮内连续按时交（已交或优秀）满 ${b.after} 次后，之后每连续交一次，额外加 ${b.bonus.fmt()} 分。", 10.5f)
                p.text("· 请假、未登记不打断也不计次；未交" + (if (b.lateCounts) "打断（迟交也算连续交）" else "、迟交都会打断") + "，之后重新计数。每次积分重置后重新计。", 10f)
                p.text("举例：连续交第 1–${b.after} 次不额外加分，第 ${b.after + 1} 次起每次 +${b.bonus.fmt()}。", 10f, MUTED)
            }
            p.heading("四、取整与其他加减分")
            p.text(if (rules.roundPoints) "所有加减分都取整数：加分向上取整（如 +0.5 记 +1），扣分向下取整（如 −0.5 记 −1）。" else "加减分保留小数。", 10.5f)
            p.text("老师可根据课堂表现等手动加减分，原因会记录在积分流水中。", 10f)
            if (rules.exam.enabled) {
                p.text("考试按得分率自动加减分：" + rules.exam.bands.sortedByDescending { it.minPct }.joinToString("；") { "${it.minPct.fmt()}%–${it.maxPct.fmt()}% ${it.delta.signed()}" } + if (rules.exam.absent != 0.0) "；缺考 ${rules.exam.absent.signed()}" else "。", 10f)
            }
            p.heading("五、积分重置与结算")
            p.text("积分重置：把积分重新设为指定分数，开始新的一轮；之前的加减分记录全部保留，可随时查看。" + (c.autoReset?.takeIf { it.enabled }?.let { " 本班每月 ${it.day} 号自动重置为 ${it.target.fmt()} 分。" } ?: ""), 10.5f)
            p.text("每轮结束时进行结算，按本轮交齐度（已交 + 优秀 + 迟交 ÷ 应交，请假不算应交）加减分，结算分计入下一轮：", 10.5f)
            table(p, listOf("本轮交齐度", "结算分"), listOf(2f, 1f), listOf(
                listOf("100% 且无迟交（全勤）", st.perfect.signed()), listOf("95% 及以上", st.r95.signed()), listOf("90%–95%", st.r90.signed()),
                listOf("80%–90%", if (st.r80 == 0.0) "0" else st.r80.signed()), listOf("70%–80%", st.r70.signed()), listOf("70% 以下", st.below.signed()),
            ))
            p.text("另外：本轮缺交 ${st.manyMissing} 次及以上 ${st.manyMissingDelta.signed()}；最长连续缺交 ${st.streakMissing} 次及以上 ${st.streakMissingDelta.signed()}；交齐度比上一轮提高 ${(st.progressPts * 100).toInt()} 个百分点以上（进步奖）${st.progressDelta.signed()}；本轮平均交齐度第一的小组每人 ${st.groupDelta.signed()}。本轮应交少于 ${st.minExpected} 次的同学不参加结算。", 10f)
            p.gap(8f)
            // 结语和落款放在一起，不让落款单独掉到下一页
            p.ensure(46f)
            p.text("请同学们按时完成作业，积极争取连续交作业奖励和结算加分。积分明细可随时向老师查询。", 10.5f)
            p.gap(6f)
            p.cell("${c.name}    ${SimpleDateFormat("yyyy年M月d日", Locale.CHINA).format(Date(now))}", M, p.y, W - 2 * M, 10.5f, INK, align = Layout.Alignment.ALIGN_OPPOSITE)
            p.gap(16f)
        }
    }
}
