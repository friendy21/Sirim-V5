package com.sirim.scanner.data.ocr

import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.YuvImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

object BitmapUtils {
    fun nv21ToBitmap(nv21: ByteArray, width: Int, height: Int): Bitmap? {
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        val outputStream = ByteArrayOutputStream()
        if (!yuvImage.compressToJpeg(android.graphics.Rect(0, 0, width, height), 90, outputStream)) {
            return null
        }
        val jpegBytes = outputStream.toByteArray()
        return android.graphics.BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
    }
}

fun Bitmap.toInputStream(): ByteArrayInputStream {
    val outputStream = ByteArrayOutputStream()
    compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
    return ByteArrayInputStream(outputStream.toByteArray())
}

fun Bitmap.toJpegByteArray(quality: Int = 90): ByteArray {
    val outputStream = ByteArrayOutputStream()
    compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
    return outputStream.toByteArray()
}
