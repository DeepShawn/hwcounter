import android.graphics.Bitmap
import com.deepshawn.hwcounter.recog.PaperCounter
import kotlin.math.*
import kotlin.random.Random

fun synth(n: Int, pitch: Float, skewDeg: Float, noise: Float, blur: Float, seed: Int, jitter: Float = 0.08f): Triple<Bitmap, Float, Float> {
    val r = Random(seed)
    // sheet boundaries
    val edges = ArrayList<Float>()
    var y = 0f
    for (i in 0 until n) { val p = pitch * (1 + (r.nextFloat() - 0.5f) * 2 * jitter); edges += y; y += p }
    val total = y
    val W = 600; val top = 100f; val H = (total + 200).toInt()
    val px = IntArray(W * H)
    val t = tan(Math.toRadians(skewDeg.toDouble())).toFloat()
    fun shade(fy: Float, fx: Float): Float {
        val ly = fy - top - (fx - W / 2) * t  // position within stack along normal
        var v: Float
        if (ly < -2 || ly > total + 2) v = 60f
        else {
            // find sheet: bright core with dark gap ~ 30% of pitch, smoothed by blur
            var best = 0f
            val i = edges.binarySearch(ly).let { if (it < 0) -it - 2 else it }.coerceIn(0, n - 1)
            for (k in max(0, i - 2)..min(n - 1, i + 2)) {
                val s = edges[k]; val e = if (k + 1 < n) edges[k + 1] else total
                val core0 = s + (e - s) * 0.18f; val core1 = e - (e - s) * 0.18f
                val d = if (ly < core0) core0 - ly else if (ly > core1) ly - core1 else 0f
                best = max(best, exp(-(d * d) / (2 * blur * blur)))
            }
            v = 70f + 150f * best
        }
        return v
    }
    for (yy in 0 until H) for (xx in 0 until W) {
        // 像素面积积分（4×4 超采样），模拟真实传感器
        var acc = 0f
        for (sy in 0 until 4) for (sx in 0 until 4) acc += shade(yy - 0.375f + sy * 0.25f, xx - 0.375f + sx * 0.25f)
        var v = acc / 16f
        v *= 0.75f + 0.5f * xx / W  // illumination gradient
        v += (r.nextFloat() - 0.5f) * 2 * noise
        val c = v.toInt().coerceIn(0, 255)
        px[yy * W + xx] = (0xff shl 24) or (c shl 16) or (c shl 8) or c
    }
    return Triple(Bitmap(W, H, px), top - 3, top + total + 3)
}

fun main() {
    val cases = listOf(
        // n, pitch, skew, noise, blur
        listOf(30f, 6f, 0f, 6f, 0.8f),
        listOf(50f, 4f, 0f, 8f, 0.7f),
        listOf(50f, 3f, 2f, 8f, 0.6f),
        listOf(80f, 3f, 4f, 10f, 0.6f),
        listOf(100f, 2.5f, 3f, 8f, 0.5f),
        listOf(120f, 2.2f, 0f, 6f, 0.5f),
        listOf(40f, 3.5f, 6f, 14f, 0.9f),
        listOf(60f, 2.8f, -5f, 12f, 0.7f),
    )
    var ok = 0
    for ((ci, cs) in cases.withIndex()) {
        val n = cs[0].toInt()
        val (bmp, ya, yb) = synth(n, cs[1], cs[2], cs[3], cs[4], ci + 1)
        val x = bmp.width / 2f
        val t0 = System.currentTimeMillis()
        val res = PaperCounter.analyze(bmp, x, ya, x, yb, spread = 60f)!!
        val ms = System.currentTimeMillis() - t0
        val err = res.count - n
        if (abs(err) <= max(1, n / 50)) ok++
        println("n=$n pitch=${cs[1]} skew=${cs[2]} noise=${cs[3]} -> count=${res.count} period=${res.periodCount} pitch=${"%.2f".format(res.pitchPx)} skewEst=${res.skewDeg} conf=${"%.2f".format(res.confidence)} lines=${res.lineCounts} err=$err ${ms}ms")
    }
    println("PASS $ok/${cases.size}")
}
