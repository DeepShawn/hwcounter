package com.deepshawn.hwcounter.recog

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.tasks.await

/** 座位表照片 → 文字片段（ML Kit 中文离线识别），每个字估算横向位置，供 SeatingLogic.fromBoxes 排座 */
object SeatingOcr {
    suspend fun recognize(bmp: Bitmap): List<SeatingLogic.Piece> {
        val text = LocalRecognizer.textRecognizer.process(InputImage.fromBitmap(bmp, 0)).await()
        val out = ArrayList<SeatingLogic.Piece>()
        for (b in text.textBlocks) for (l in b.lines) {
            val box = l.boundingBox ?: continue
            val sb = StringBuilder()
            val xs = ArrayList<Float>()
            for (e in l.elements) {
                val t = e.text.replace(Regex("\\s+"), "")
                val eb = e.boundingBox ?: continue
                if (t.isEmpty()) continue
                val step = eb.width().toFloat() / t.length
                t.forEachIndexed { i, ch -> sb.append(ch); xs += eb.left + step * (i + 0.5f) }
                // 中文不加空格（靠每个字的横坐标区分座位）；英文/数字词之间补一个空格
                if (t.last().isLetterOrDigit() && t.last().code < 128) { sb.append(' '); xs += eb.right.toFloat() }
            }
            if (sb.isBlank()) continue
            out += SeatingLogic.Piece(sb.toString(), box.left.toFloat(), box.top.toFloat(), box.width().toFloat(), box.height().toFloat(), xs)
        }
        return out
    }
}
