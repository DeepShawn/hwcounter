package com.deepshawn.hwcounter.recog

import android.graphics.Bitmap
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * “扫描线数本数”：用户在一摞作业本的侧面画一条穿过所有本子的线，
 * 沿线采样亮度（垂直方向带宽平均降噪）→ 去趋势 → 自相关估计本子厚度 → 峰值检测。
 * 每个峰代表一条“本与本之间的缝隙”。
 */
object StackCounter {
    data class Result(
        /** 每个分界在线上的位置 0..1 */
        val marks: List<Float>,
        /** 归一化后的信号，用于绘制波形 */
        val profile: FloatArray,
        val estimatedPitchPx: Float,
    )

    fun analyze(
        bmp: Bitmap,
        x0: Float, y0: Float, x1: Float, y1: Float,
        sensitivity: Float = 0.5f,
        darkGaps: Boolean = true,
        bandHalf: Int = 12,
    ): Result {
        val w = bmp.width
        val h = bmp.height
        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)
        val len = hypot(x1 - x0, y1 - y0)
        val n = max(24, len.roundToInt())
        val ux = (x1 - x0) / len
        val uy = (y1 - y0) / len
        val vx = -uy
        val vy = ux
        val raw = FloatArray(n)
        for (i in 0 until n) {
            val px = x0 + ux * i * len / n
            val py = y0 + uy * i * len / n
            var sum = 0f
            var cnt = 0
            for (j in -bandHalf..bandHalf) {
                val sx = (px + vx * j).roundToInt()
                val sy = (py + vy * j).roundToInt()
                if (sx in 0 until w && sy in 0 until h) {
                    val c = pixels[sy * w + sx]
                    val r = (c shr 16) and 0xff
                    val g = (c shr 8) and 0xff
                    val b = c and 0xff
                    sum += 0.299f * r + 0.587f * g + 0.114f * b
                    cnt++
                }
            }
            raw[i] = if (cnt > 0) sum / cnt else 0f
        }
        val smooth = boxBlur(raw, 1)
        val trend = boxBlur(smooth, max(6, n / 25))
        val sig = FloatArray(n) { i -> (if (darkGaps) trend[i] - smooth[i] else smooth[i] - trend[i]) }

        val pitch = estimatePitch(sig)
        val minDist = max(3, (pitch * 0.6f).roundToInt())
        val mean = sig.average().toFloat()
        val std = sqrt(sig.fold(0f) { a, v -> a + (v - mean) * (v - mean) } / n)
        val k = 1.6f - 1.5f * sensitivity.coerceIn(0f, 1f)
        val thr = mean + k * std

        val peaks = ArrayList<Int>()
        var i = 1
        while (i < n - 1) {
            if (sig[i] > thr && sig[i] >= sig[i - 1] && sig[i] >= sig[i + 1]) {
                val lo = max(0, i - minDist)
                val hi = min(n - 1, i + minDist)
                var isMax = true
                for (q in lo..hi) if (sig[q] > sig[i]) { isMax = false; break }
                if (isMax && (peaks.isEmpty() || i - peaks.last() >= minDist)) peaks += i
            }
            i++
        }
        val mx = sig.maxOrNull() ?: 1f
        val mn = sig.minOrNull() ?: 0f
        val range = if (mx - mn < 1e-3f) 1f else mx - mn
        return Result(
            marks = peaks.map { it.toFloat() / (n - 1) },
            profile = FloatArray(n) { (sig[it] - mn) / range },
            estimatedPitchPx = pitch * len / n,
        )
    }

    private fun boxBlur(a: FloatArray, r: Int): FloatArray {
        val n = a.size
        val prefix = DoubleArray(n + 1)
        for (i in 0 until n) prefix[i + 1] = prefix[i] + a[i]
        return FloatArray(n) { i ->
            val lo = max(0, i - r)
            val hi = min(n - 1, i + r)
            ((prefix[hi + 1] - prefix[lo]) / (hi - lo + 1)).toFloat()
        }
    }

    /** 自相关找第一个显著峰 → 平均每本厚度（采样点数） */
    private fun estimatePitch(sig: FloatArray): Float {
        val n = sig.size
        val mean = sig.average().toFloat()
        val c = FloatArray(n) { sig[it] - mean }
        val maxLag = n / 3
        if (maxLag < 4) return 4f
        val ac = FloatArray(maxLag + 1)
        var e0 = 0f
        for (i in 0 until n) e0 += c[i] * c[i]
        if (e0 <= 0f) return 4f
        for (lag in 2..maxLag) {
            var s = 0f
            for (i in 0 until n - lag) s += c[i] * c[i + lag]
            ac[lag] = s / e0
        }
        for (lag in 3 until maxLag) {
            if (ac[lag] > 0.15f && ac[lag] >= ac[lag - 1] && ac[lag] >= ac[lag + 1]) return lag.toFloat()
        }
        return 4f
    }
}
