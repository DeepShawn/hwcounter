package com.deepshawn.hwcounter.recog

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import kotlin.math.max

object ImageUtils {
    /** 按最长边缩放并根据 EXIF 旋转 */
    fun load(ctx: Context, uri: Uri, maxSide: Int = 2048): Bitmap? {
        val cr = ctx.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        var bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        val rotation = try {
            cr.openInputStream(uri)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        } catch (e: Exception) {
            0
        }
        val longest = max(bmp.width, bmp.height)
        val scale = if (longest > maxSide) maxSide.toFloat() / longest else 1f
        if (rotation != 0 || scale < 1f) {
            val m = Matrix().apply {
                postScale(scale, scale)
                postRotate(rotation.toFloat())
            }
            val r = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            if (r != bmp) bmp.recycle()
            bmp = r
        }
        return bmp
    }

    fun toJpeg(bmp: Bitmap, maxSide: Int = 1600, quality: Int = 85): ByteArray {
        val longest = max(bmp.width, bmp.height)
        val b = if (longest > maxSide) {
            val s = maxSide.toFloat() / longest
            Bitmap.createScaledBitmap(bmp, (bmp.width * s).toInt(), (bmp.height * s).toInt(), true)
        } else bmp
        val out = ByteArrayOutputStream()
        b.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return out.toByteArray()
    }
}
