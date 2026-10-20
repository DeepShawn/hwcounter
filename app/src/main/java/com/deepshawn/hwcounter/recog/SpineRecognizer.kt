package com.deepshawn.hwcounter.recog

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import com.deepshawn.hwcounter.data.Student
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 侧面学号识别：每本作业本侧面写着学号，拍一摞的侧面，
 * 自动判断方向 → 高清分块 OCR → 对名单 → 按叠放顺序排列 → 条纹计数复核。
 */
object SpineRecognizer {
    /** 一本书：studentId 为 null 表示读到了像学号的文字但没对上名单 */
    data class Book(val studentId: String?, val text: String, val box: Rect, val source: MatchSource?)

    data class Result(
        /** 已旋转到文字正向的图片 */
        val bitmap: Bitmap,
        val rotation: Int,
        /** 按叠放顺序排列 */
        val books: List<Book>,
        /** 条纹法估计的本数（null 表示无法估计） */
        val stripeCount: Int?,
        /** 条纹扫描线（图像坐标）与分界位置，供界面绘制 */
        val stripeLine: FloatArray?,
        val stripeMarks: List<Float>,
    )

    private fun rotate(src: Bitmap, deg: Int): Bitmap {
        if (deg == 0) return src
        val m = Matrix().apply { postRotate(deg.toFloat()) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }

    private fun scaled(src: Bitmap, maxSide: Int): Bitmap {
        val longest = max(src.width, src.height)
        if (longest <= maxSide) return src
        val s = maxSide.toFloat() / longest
        return Bitmap.createScaledBitmap(src, (src.width * s).roundToInt(), (src.height * s).roundToInt(), true)
    }

    suspend fun analyze(
        src: Bitmap,
        roster: List<Student>,
        minLen: Int,
        fuzzy: Boolean,
        onPhase: (String) -> Unit = {},
    ): Result {
        // 侧面上一般只写学号，不匹配姓名，减少误报
        val matcher = Matcher(roster, minLen, fuzzy, matchNames = false)
        val noLens = roster.map { Matcher.canon(it.no).length }.filter { it > 0 }.toSet()

        // 1) 小图快速判断方向
        onPhase("判断拍摄方向…")
        val small = scaled(src, 1400)
        var bestRot = 0
        var bestScore = -1
        for (rot in listOf(0, 90, 270)) {
            val raw = runCatching { LocalRecognizer.recognize(rotate(small, rot)) }.getOrNull() ?: continue
            val m = matcher.match(raw.lines, raw.barcodes)
            val score = m.hits.size * 10 + m.unknown.count { idLike(it.first, noLens, minLen) }
            if (score > bestScore) { bestScore = score; bestRot = rot }
        }

        // 2) 正向高清图 + 重叠分块，提高小字召回
        onPhase("高清识别学号…")
        val full = rotate(src, bestRot)
        val pieces = ArrayList<TextPiece>()
        val codes = ArrayList<TextPiece>()
        LocalRecognizer.recognize(full).let { pieces += it.lines; codes += it.barcodes }
        val alongY = full.height >= full.width
        val len = if (alongY) full.height else full.width
        if (len > 1200) {
            val band = len / 2
            val step = band / 2
            var start = 0
            var i = 0
            while (start < len && i < 4) {
                onPhase("分块精读 ${i + 1}/3…")
                val end = minOf(len, start + band)
                val tile = if (alongY) Bitmap.createBitmap(full, 0, start, full.width, end - start)
                else Bitmap.createBitmap(full, start, 0, end - start, full.height)
                // 小块放大，帮助识别细小的手写数字
                val up = if (max(tile.width, tile.height) < 1600) Bitmap.createScaledBitmap(tile, tile.width * 3 / 2, tile.height * 3 / 2, true) else tile
                val k = tile.width.toFloat() / up.width
                val raw = runCatching { LocalRecognizer.recognize(up) }.getOrNull()
                raw?.lines?.forEach { p -> pieces += TextPiece(p.text, p.box?.let { map(it, k, if (alongY) 0 else start, if (alongY) start else 0) }) }
                raw?.barcodes?.forEach { p -> codes += TextPiece(p.text, p.box?.let { map(it, k, if (alongY) 0 else start, if (alongY) start else 0) }) }
                if (end >= len) break
                start += step
                i++
            }
        }

        // 3) 对名单
        onPhase("匹配名单…")
        val m = matcher.match(pieces, codes)
        val books = ArrayList<Book>()
        m.hits.forEach { h -> if (h.box != null) books += Book(h.studentId, h.raw, h.box, h.source) }
        val matchedRaw = m.hits.map { it.raw }.toSet()
        m.unknown.filter { (t, r) -> r != null && t !in matchedRaw && idLike(t, noLens, minLen) }
            .forEach { (t, r) -> books += Book(null, t, r!!, null) }

        // 同一位置重复读到的未匹配文字只算一次
        val dedup = ArrayList<Book>()
        for (b in books) {
            val dup = dedup.any { o ->
                (o.studentId != null && o.studentId == b.studentId) ||
                    (o.studentId == null && b.studentId == null && overlap(o.box, b.box) > 0.4f) ||
                    (o.studentId != null && b.studentId == null && overlap(o.box, b.box) > 0.4f)
            }
            if (!dup) dedup += b
        }

        // 4) 按叠放方向排序：中心点分布更分散的那个轴
        val xs = dedup.map { it.box.exactCenterX() }
        val ys = dedup.map { it.box.exactCenterY() }
        val spreadX = (xs.maxOrNull() ?: 0f) - (xs.minOrNull() ?: 0f)
        val spreadY = (ys.maxOrNull() ?: 0f) - (ys.minOrNull() ?: 0f)
        val stackAlongY = dedup.size < 2 || spreadY >= spreadX
        val sorted = if (stackAlongY) dedup.sortedBy { it.box.exactCenterY() } else dedup.sortedBy { it.box.exactCenterX() }

        // 5) 条纹计数复核：沿叠放方向、穿过学号旁边的纸边画扫描线
        onPhase("条纹复核本数…")
        var stripe: Int? = null
        var line: FloatArray? = null
        var marks = emptyList<Float>()
        if (sorted.size >= 2) {
            val boxes = sorted.map { it.box }
            if (stackAlongY) {
                // 取学号框右侧一点（纸边），避开文字本身
                val x = (boxes.map { it.right }.sorted()[boxes.size / 2] + full.width * 0.03f).coerceAtMost(full.width - 2f)
                val top = (boxes.minOf { it.top } - median(boxes.map { it.height() })).coerceAtLeast(0f)
                val bottom = (boxes.maxOf { it.bottom } + median(boxes.map { it.height() })).coerceAtMost(full.height - 1f)
                line = floatArrayOf(x, top, x, bottom)
            } else {
                val y = (boxes.map { it.bottom }.sorted()[boxes.size / 2] + full.height * 0.03f).coerceAtMost(full.height - 2f)
                val l = (boxes.minOf { it.left } - median(boxes.map { it.width() })).coerceAtLeast(0f)
                val r = (boxes.maxOf { it.right } + median(boxes.map { it.width() })).coerceAtMost(full.width - 1f)
                line = floatArrayOf(l, y, r, y)
            }
            val ln = line
            val res = StackCounter.analyze(full, ln[0], ln[1], ln[2], ln[3], 0.5f, true, 10)
            marks = res.marks
            // 两端各有一条外边缘：本数 ≈ 分界线 - 1，至少不少于学号数
            stripe = (res.marks.size - 1).coerceAtLeast(0)
        }
        return Result(full, bestRot, sorted, stripe, line, marks)
    }

    private fun median(v: List<Int>): Float = if (v.isEmpty()) 0f else v.sorted()[v.size / 2].toFloat()

    private fun idLike(t: String, lens: Set<Int>, minLen: Int): Boolean =
        if (lens.isEmpty()) t.length >= minLen else t.length in lens || lens.any { abs(it - t.length) <= 1 && t.length >= minLen }

    private fun map(r: Rect, k: Float, dx: Int, dy: Int) =
        Rect((r.left * k).roundToInt() + dx, (r.top * k).roundToInt() + dy, (r.right * k).roundToInt() + dx, (r.bottom * k).roundToInt() + dy)

    private fun overlap(a: Rect, b: Rect): Float {
        val ix = maxOf(0, minOf(a.right, b.right) - maxOf(a.left, b.left))
        val iy = maxOf(0, minOf(a.bottom, b.bottom) - maxOf(a.top, b.top))
        val inter = ix.toFloat() * iy
        val small = minOf(a.width() * a.height(), b.width() * b.height()).coerceAtLeast(1)
        return inter / small
    }
}
