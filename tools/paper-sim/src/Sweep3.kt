import com.deepshawn.hwcounter.recog.PaperCounter
import kotlin.math.abs
import kotlin.random.Random
fun main() {
    for (seed in listOf(2026, 77, 31337)) {
        val r = Random(seed)
        var tot = 0; var ok1 = 0; var badConf = 0; var hiConf = 0; var hiConfOk = 0; var lowConfOk = 0; var lowConf = 0
        var thinTot = 0; var thinOk = 0
        for (t in 0 until 210) {
            val pitch = listOf(2f, 2.5f, 3f, 4f, 5f, 6f, 8f)[t % 7]
            val n = 20 + r.nextInt(131)
            val (bmp, ya, yb) = synth(n, pitch, (r.nextFloat() - 0.5f) * 14f, 4f + r.nextFloat() * 12f, 0.4f + r.nextFloat() * 0.6f, seed * 100 + t, r.nextFloat() * 0.08f)
            val x = bmp.width / 2f
            val res = PaperCounter.analyze(bmp, x, ya - 10, x, yb + 10, 60f) ?: continue
            val ok = abs(res.count - n) <= 1
            tot++; if (ok) ok1++
            if (pitch >= 2.5f) { thinTot++; if (ok) thinOk++ }
            if (res.confidence >= 0.6f) { hiConf++; if (ok) hiConfOk++ else badConf++ } else { lowConf++; if (ok) lowConfOk++ }
        }
        println("seed=$seed  ±1: $ok1/$tot  (≥2.5px: $thinOk/$thinTot)  高置信: $hiConfOk/$hiConf 正确, 错误=$badConf  低置信: $lowConfOk/$lowConf 正确")
    }
}
