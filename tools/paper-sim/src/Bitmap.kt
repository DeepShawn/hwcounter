// 仅用于 JVM 仿真测试的 android.graphics.Bitmap 替身
package android.graphics
class Bitmap(val width: Int, val height: Int, val px: IntArray) {
    enum class Config { ARGB_8888 }
    fun getPixels(out: IntArray, offset: Int, stride: Int, x: Int, y: Int, w: Int, h: Int) {
        for (r in 0 until h) for (c in 0 until w) out[offset + r * stride + c] = px[(y + r) * width + x + c]
    }
    companion object {
        fun createBitmap(p: IntArray, w: Int, h: Int, cfg: Config) = Bitmap(w, h, p.copyOf())
    }
}
