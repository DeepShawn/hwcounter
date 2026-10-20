package com.deepshawn.hwcounter.recog

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 试卷张数计数（针对极薄纸张优化）。
 *
 * 一摞试卷侧面，每张纸的边缘是一条亮线，纸与纸之间是暗缝，单张厚度在照片里通常只有 2~5 像素。
 * 1. 只取扫描线附近的区域、原始分辨率，0.5px 步长双线性亚像素采样；
 * 2. 在 ±8° 内微调“沿纸边平均”的方向，取高频能量最大者（自动对齐纸边）；
 * 3. 自相关 + 抛物线插值估计单张厚度（周期法）；
 * 4. 在多条平行扫描线上分别做峰值计数，取中位数（多线投票）；
 * 5. 两种方法互相校验，给出置信度。
 */
object PaperCounter {
    data class Result(
        val count: Int,
        /** 多线峰值计数的中位数 */
        val peakCount: Int,
        /** 周期法：长度 / 单张厚度 */
        val periodCount: Int?,
        /** 单张厚度（原图像素） */
        val pitchPx: Float,
        val lineCounts: List<Int>,
        /** 0..1 */
        val confidence: Float,
        /** 中心线上每张纸的位置 0..1 */
        val marks: List<Float>,
        val profile: FloatArray,
        /** 自动对齐得到的纸边偏角（度） */
        val skewDeg: Float,
        /** 中心线上“推断”出的纸缝数量（看不清，按周期补上的） */
        val inferred: Int = 0,
        /** 纸缝是否足够清晰可分辨（否则建议靠近/补光重拍） */
        val resolvable: Boolean = true,
        val resolvability: Float = 0f,
        /** 纸堆在线上的起止位置 0..1 */
        val start: Float = 0f,
        val end: Float = 1f,
    )

    private const val STEP = 0.5f

    /** 只读取线段附近区域的像素，避免整图 getPixels 占用大量内存 */
    private class Region(bmp: Bitmap, x0: Float, y0: Float, x1: Float, y1: Float, margin: Int) {
        val left = max(0, (min(x0, x1) - margin).toInt())
        val top = max(0, (min(y0, y1) - margin).toInt())
        val right = min(bmp.width, (max(x0, x1) + margin).toInt() + 1)
        val bottom = min(bmp.height, (max(y0, y1) + margin).toInt() + 1)
        val w = max(1, right - left)
        val h = max(1, bottom - top)
        val lum = FloatArray(w * h)

        init {
            val px = IntArray(w * h)
            bmp.getPixels(px, 0, w, left, top, w, h)
            for (i in px.indices) {
                val c = px[i]
                lum[i] = 0.299f * ((c shr 16) and 0xff) + 0.587f * ((c shr 8) and 0xff) + 0.114f * (c and 0xff)
            }
        }

        /** 双线性插值取亮度（全局坐标） */
        fun at(gx: Float, gy: Float): Float {
            val x = (gx - left).coerceIn(0f, w - 1.001f)
            val y = (gy - top).coerceIn(0f, h - 1.001f)
            val ix = floor(x).toInt()
            val iy = floor(y).toInt()
            val fx = x - ix
            val fy = y - iy
            val i = iy * w + ix
            val a = lum[i]
            val b = lum[min(i + 1, lum.size - 1)]
            val c = lum[min(i + w, lum.size - 1)]
            val d = lum[min(i + w + 1, lum.size - 1)]
            return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy
        }
    }

    fun analyze(
        bmp: Bitmap,
        x0: Float, y0: Float, x1: Float, y1: Float,
        /** 平行线分布的半宽（原图像素） */
        spread: Float = 40f,
        /** true：纸缝是暗线（常见白纸）；false：纸缝是亮线（深色纸/逆光） */
        darkGaps: Boolean = true,
        lines: Int = 9,
    ): Result? {
        val len = hypot(x1 - x0, y1 - y0)
        if (len < 20f) return null
        val ux = (x1 - x0) / len
        val uy = (y1 - y0) / len
        val nx = -uy
        val ny = ux
        val maxHalf = 14f
        val region = Region(bmp, x0, y0, x1, y1, (spread + maxHalf + 4).toInt())
        val n = (len / STEP).toInt()

        // 沿“纸边方向”（法线偏 deg 角）平均，得到一条亮度剖面
        fun profile(offset: Float, deg: Float, half: Float): FloatArray {
            val rad = Math.toRadians(deg.toDouble())
            val dx = (nx * cos(rad) - ny * sin(rad)).toFloat()
            val dy = (nx * sin(rad) + ny * cos(rad)).toFloat()
            val ox = x0 + nx * offset
            val oy = y0 + ny * offset
            val out = FloatArray(n)
            val k = max(1, half.toInt())
            for (i in 0 until n) {
                val bx = ox + ux * i * STEP
                val by = oy + uy * i * STEP
                var sum = 0f
                for (j in -k..k) sum += region.at(bx + dx * j, by + dy * j)
                out[i] = sum / (2 * k + 1)
            }
            return out
        }

        // 纸缝为正峰的信号
        fun gapSignal(p: FloatArray, win: Int): FloatArray {
            val hp = highpass(p, win)
            return if (darkGaps) FloatArray(hp.size) { -hp[it] } else hp
        }

        // 1) 找出整摞纸在线上的范围（排除两端背景）
        val base = profile(0f, 0f, 4f)
        val (s0, e0) = extent(base)
        if (e0 - s0 < 12) return null

        // 2) 周期图粗估单张厚度
        var pitch = periodogram(gapSignal(base, 24), s0, e0).first

        // 3) 自动对齐纸边：在 ±10° 内找使周期能量最强的平均方向
        var bestDeg = 0f
        if (pitch > 0f) {
            var bestPow = -1.0
            var deg = -10f
            while (deg <= 10.01f) {
                val pw = periodogram(gapSignal(profile(0f, deg, 10f), 24), s0, e0, pitch).second
                if (pw > bestPow) { bestPow = pw; bestDeg = deg }
                deg += 1f
            }
        }
        val win = if (pitch > 0f) max(8, (pitch * 2.5f).toInt()) else 24
        val centerSig = gapSignal(profile(0f, bestDeg, maxHalf), win)
        val p2 = periodogram(centerSig, s0, e0).first
        if (p2 > 0f) pitch = p2

        // 周期自检：中点还有强缝 → 周期减半；大量“推断” → 周期加倍
        var track = trackGaps(centerSig, s0, e0, pitch)
        for (iter in 0 until 2) {
            val mid = midStrength(centerSig, track.gaps, pitch)
            val next = when {
                track.gaps.size >= 4 && mid > track.medProm * 0.5f && pitch / 2 >= 3.2f -> pitch / 2
                track.gaps.size >= 4 && track.inferred > track.gaps.size * 0.35f && pitch * 2 <= (e0 - s0) / 4f -> pitch * 2
                else -> 0f
            }
            if (next <= 0f) break
            val t2 = trackGaps(centerSig, s0, e0, next)
            // 只有新周期下的纸缝更“干净”才采用
            if (t2.medProm >= track.medProm * 0.45f && t2.inferred <= max(track.inferred, t2.gaps.size / 4)) {
                pitch = next; track = t2
            } else break
        }
        // 可分辨度：纸缝显著度 / 信号标准差；随机噪声的局部峰一般 < 1.2
        val resolv = track.medProm / max(1e-3f, std(centerSig, s0, e0))
        val periodCount = if (pitch > 0f) ((e0 - s0) / pitch).roundToInt() else null

        // 4) 多线投票：每条线跟踪“纸缝”，张数 = 纸缝 + 1
        val counts = ArrayList<Int>()
        var centerGaps = emptyList<Int>()
        var centerInferred = 0
        val lineCount = max(1, lines)
        for (li in 0 until lineCount) {
            val off = if (lineCount == 1) 0f else -spread + 2 * spread * li / (lineCount - 1)
            val sig = if (abs(off) < 0.01f) centerSig else gapSignal(profile(off, bestDeg, maxHalf), win)
            val t = if (sig === centerSig) track else trackGaps(sig, s0, e0, pitch)
            counts += t.gaps.size + 1
            if (li == lineCount / 2) { centerGaps = t.gaps; centerInferred = t.inferred }
        }
        val sorted = counts.sorted()
        val median = sorted[sorted.size / 2]

        // 5) 融合与置信度
        val mad = sorted.map { abs(it - median) }.sorted()[sorted.size / 2]
        val consistency = (1f - mad.toFloat() / max(1, median) * 6f).coerceIn(0f, 1f)
        val diff = periodCount?.let { abs(it - median).toFloat() / max(1, median) } ?: 1f
        val agree = (1f - diff * 6f).coerceIn(0f, 1f)
        val clarity = (1f - centerInferred.toFloat() / max(1, median) * 4f).coerceIn(0f, 1f)
        val resolvable = resolv >= 1.3f
        val clear = resolv >= 1.8f
        // 纸缝清晰：逐条跟踪最准（合成测试 ±1 准确率约 99%）；
        // 纸缝偏模糊：逐条跟踪易漏/多，周期法（总长 ÷ 单张厚度）更稳，改用周期法；
        // 两种独立方法吻合（±1）本身就是可靠的证据，给中等置信度
        val count = if (clear || periodCount == null) median else periodCount
        val confidence = when {
            clear -> (0.4f * consistency + 0.35f * agree + 0.25f * clarity).coerceIn(0f, 1f)
            resolv >= 0.4f && periodCount != null && abs(periodCount - median) <= 1 -> 0.7f
            else -> (0.2f + 0.3f * agree).coerceIn(0f, 0.5f)
        }

        val mx = centerSig.maxOrNull() ?: 1f
        val mn = centerSig.minOrNull() ?: 0f
        val range = if (mx - mn < 1e-3f) 1f else mx - mn
        val last = max(1, n - 1).toFloat()
        return Result(
            count = count,
            peakCount = median,
            periodCount = periodCount,
            pitchPx = pitch * STEP,
            lineCounts = counts,
            confidence = confidence,
            marks = centerGaps.map { it / last },
            profile = FloatArray(n) { (centerSig[it] - mn) / range },
            skewDeg = bestDeg,
            inferred = centerInferred,
            resolvable = resolvable,
            resolvability = resolv,
            start = s0 / last,
            end = e0 / last,
        )
    }

    private fun highpass(p: FloatArray, win: Int): FloatArray {
        val pre = DoubleArray(p.size + 1)
        for (i in p.indices) pre[i + 1] = pre[i] + p[i]
        return FloatArray(p.size) { i ->
            val lo = max(0, i - win)
            val hi = min(p.size - 1, i + win)
            p[i] - ((pre[hi + 1] - pre[lo]) / (hi - lo + 1)).toFloat()
        }
    }

    /**
     * 纸堆在线上的范围：分别看线的两端是否落在背景上（比纸堆主体暗很多），
     * 是则从该端向内找到亮度越过“背景与纸张中值”中点的位置；否则视为线从纸堆内部开始/结束。
     */
    private fun extent(p: FloatArray): Pair<Int, Int> {
        val n = p.size
        val r = 4
        val pre = DoubleArray(n + 1)
        for (i in 0 until n) pre[i + 1] = pre[i] + p[i]
        val sm = FloatArray(n) { i ->
            val lo = max(0, i - r)
            val hi = min(n - 1, i + r)
            ((pre[hi + 1] - pre[lo]) / (hi - lo + 1)).toFloat()
        }
        val paper = sm.sorted()[n / 2]
        val endN = max(3, n / 100)
        fun edge(fromStart: Boolean): Int {
            val bg = if (fromStart) (0 until endN).map { sm[it] }.average().toFloat() else (n - endN until n).map { sm[it] }.average().toFloat()
            if (paper - bg < 20f) return if (fromStart) 0 else n - 1
            val thr = (bg + paper) / 2
            return if (fromStart) {
                var i = 0
                while (i < n - 1 && sm[i] < thr) i++
                i
            } else {
                var i = n - 1
                while (i > 0 && sm[i] < thr) i--
                i
            }
        }
        val s0 = edge(true)
        val e0 = edge(false)
        return if (e0 > s0) s0 to e0 else 0 to n - 1
    }

    /**
     * 在 [s, e] 区间内对周期做 DFT 功率谱，返回（周期采样点数, 功率）。
     * 给定 near 时只在其 ±25% 内搜索（用于比较不同角度的清晰度）。
     */
    private fun periodogram(sig: FloatArray, s: Int, e: Int, near: Float = 0f): Pair<Float, Double> {
        val len = e - s + 1
        if (len < 16) return 0f to 0.0
        var mean = 0.0
        for (i in s..e) mean += sig[i]
        mean /= len
        val minP = if (near > 0f) near * 0.75f else 3.2f
        val maxP = if (near > 0f) near * 1.25f else min(48f, len / 8f)
        if (maxP <= minP) return 0f to 0.0
        // Welch：分段（带汉宁窗）求功率再平均，抵抗透视/厚度渐变造成的相位漂移
        val seg = min(len, max(96, (maxP * 8).toInt()))
        val hop = max(1, seg / 2)
        val starts = ArrayList<Int>()
        var st = s
        while (st + seg - 1 <= e) { starts += st; st += hop }
        if (starts.isEmpty()) starts += s
        val hann = FloatArray(seg) { (0.5 - 0.5 * cos(2 * Math.PI * it / (seg - 1))).toFloat() }
        var bestP = 0f
        var bestPow = -1.0
        var period = minP
        while (period <= maxP) {
            val w = 2.0 * Math.PI / period
            val cw = cos(w)
            val sw = sin(w)
            var pw = 0.0
            for (s1 in starts) {
                var re = 0.0
                var im = 0.0
                // 递推旋转代替逐点三角函数
                var c = 1.0
                var sn = 0.0
                for (k in 0 until seg) {
                    val v = (sig[s1 + k] - mean) * hann[k]
                    re += v * c
                    im += v * sn
                    val nc = c * cw - sn * sw
                    sn = sn * cw + c * sw
                    c = nc
                }
                pw += re * re + im * im
            }
            pw /= starts.size * seg
            if (pw > bestPow) { bestPow = pw; bestP = period }
            period *= 1.006f
        }
        return bestP to bestPow
    }

    private class Track(val gaps: List<Int>, val inferred: Int, val medProm: Float)

    private fun prominence(sm: FloatArray, i: Int, p: Float): Float {
        val n = sm.size
        val h = max(1, (p * 0.5f).toInt())
        var lm = sm[i]
        for (j in max(0, i - h) until i) lm = min(lm, sm[j])
        var rm = sm[i]
        for (j in i + 1..min(n - 1, i + h)) rm = min(rm, sm[j])
        return sm[i] - max(lm, rm)
    }

    private fun argmax(sm: FloatArray, lo: Int, hi: Int): Int {
        val n = sm.size
        var bi = lo.coerceIn(0, n - 1)
        for (i in max(0, lo)..min(n - 1, hi)) if (sm[i] > sm[bi]) bi = i
        return bi
    }

    private fun smoothFor(sig: FloatArray, pitch: Float): FloatArray {
        val n = sig.size
        return if (pitch >= 8f) FloatArray(n) { i -> 0.25f * sig[max(0, i - 1)] + 0.5f * sig[i] + 0.25f * sig[min(n - 1, i + 1)] } else sig
    }

    /**
     * 周期引导的纸缝跟踪：已知单张厚度后，从第一条缝开始逐条预测下一条缝的位置，
     * 在 ±35% 窗口内取响应最强处；局部厚度缓慢自适应（吸收透视/厚度渐变）。
     * 看不清但按周期必然存在的缝记为“推断”。
     */
    private fun trackGaps(sig: FloatArray, s: Int, e: Int, pitch: Float): Track {
        if (pitch <= 0f || e - s < pitch * 1.5f) return Track(emptyList(), 0, 0f)
        val sm = smoothFor(sig, pitch)
        val gaps = ArrayList<Int>()
        val proms = ArrayList<Float>()
        // 第一条缝：纸堆起点后 0.3~1.4 个周期内最强处
        var last = argmax(sm, s + (pitch * 0.3f).toInt(), s + (pitch * 1.4f).toInt())
        gaps += last; proms += prominence(sm, last, pitch)
        var local = pitch
        while (true) {
            val pred = last + local
            if (pred > e - pitch * 0.5f) break
            val w = local * 0.35f
            val i = argmax(sm, (pred - w).toInt(), (pred + w).toInt() + 1)
            if (i <= last) break
            gaps += i; proms += prominence(sm, i, local)
            local = (0.8f * local + 0.2f * (i - last)).coerceIn(pitch * 0.75f, pitch * 1.3f)
            last = i
        }
        val med = proms.sorted()[proms.size / 2]
        return Track(gaps, proms.count { it < med * 0.25f }, med)
    }

    /** 相邻两条已跟踪纸缝的中点处，是否还藏着同样明显的缝（说明真实周期只有一半） */
    private fun midStrength(sig: FloatArray, gaps: List<Int>, pitch: Float): Float {
        if (gaps.size < 4) return 0f
        val sm = smoothFor(sig, pitch / 2)
        val v = gaps.zipWithNext { a, b ->
            val mid = (a + b) / 2
            val w = max(1, ((b - a) * 0.2f).toInt())
            prominence(sm, argmax(sm, mid - w, mid + w), (b - a) / 2f)
        }.sorted()
        return v[v.size / 2]
    }

    private fun std(sig: FloatArray, s: Int, e: Int): Float {
        var m = 0.0
        for (i in s..e) m += sig[i]
        m /= (e - s + 1)
        var v = 0.0
        for (i in s..e) v += (sig[i] - m) * (sig[i] - m)
        return sqrt(v / (e - s + 1)).toFloat()
    }

    /** 把扫描线附近区域“拉直”成一条横向条带，便于放大逐张核对 */
    fun strip(bmp: Bitmap, x0: Float, y0: Float, x1: Float, y1: Float, halfHeight: Int = 40, scale: Int = 3, skewDeg: Float = 0f): Bitmap? {
        val len = hypot(x1 - x0, y1 - y0)
        if (len < 20f) return null
        val ux = (x1 - x0) / len
        val uy = (y1 - y0) / len
        val rad = Math.toRadians(skewDeg.toDouble())
        val bnx = -uy
        val bny = ux
        val nx = (bnx * cos(rad) - bny * sin(rad)).toFloat()
        val ny = (bnx * sin(rad) + bny * cos(rad)).toFloat()
        val region = Region(bmp, x0, y0, x1, y1, halfHeight + 4)
        val w = min(12000, (len * scale).toInt())
        val h = halfHeight * 2 * scale
        val px = IntArray(w * h)
        for (yy in 0 until h) {
            val off = (yy.toFloat() / scale) - halfHeight
            for (xx in 0 until w) {
                val t = xx.toFloat() / scale
                val v = region.at(x0 + ux * t + nx * off, y0 + uy * t + ny * off).toInt().coerceIn(0, 255)
                px[yy * w + xx] = (0xff shl 24) or (v shl 16) or (v shl 8) or v
            }
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }
}
