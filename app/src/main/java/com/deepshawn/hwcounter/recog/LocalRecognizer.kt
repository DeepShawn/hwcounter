package com.deepshawn.hwcounter.recog

import android.graphics.Bitmap
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.tasks.await

/** 纯本地离线识别：ML Kit 中文 OCR + 二维码 */
object LocalRecognizer {
    val textRecognizer by lazy { TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build()) }
    val barcodeScanner by lazy { BarcodeScanning.getClient() }

    data class Raw(val lines: List<TextPiece>, val blocks: Int, val barcodes: List<TextPiece>)

    suspend fun recognize(bmp: Bitmap): Raw {
        val img = InputImage.fromBitmap(bmp, 0)
        val text = textRecognizer.process(img).await()
        val lines = ArrayList<TextPiece>()
        for (b in text.textBlocks) for (l in b.lines) lines += TextPiece(l.text, l.boundingBox)
        val codes = runCatching { barcodeScanner.process(img).await() }.getOrDefault(emptyList())
        val bc = codes.mapNotNull { c -> c.rawValue?.let { TextPiece(it, c.boundingBox) } }
        return Raw(lines, text.textBlocks.size, bc)
    }
}
