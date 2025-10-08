package com.sirim.scanner.data.ocr

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.BarcodeFormat
import java.nio.ByteBuffer
import kotlinx.coroutines.tasks.await

class BarcodeAnalyzer {

    private val supportedFormats = setOf(
        Barcode.FORMAT_EAN_8,
        Barcode.FORMAT_EAN_13,
        Barcode.FORMAT_UPC_A,
        Barcode.FORMAT_UPC_E,
        Barcode.FORMAT_CODE_128,
        Barcode.FORMAT_QR_CODE
    )

    private val barcodeScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E,
                Barcode.FORMAT_CODE_128,
                Barcode.FORMAT_QR_CODE
            )
            .build()
    )

    private val zxingReader = MultiFormatReader().apply {
        val hints = mapOf(
            DecodeHintType.POSSIBLE_FORMATS to listOf(
                BarcodeFormat.EAN_8,
                BarcodeFormat.EAN_13,
                BarcodeFormat.UPC_A,
                BarcodeFormat.UPC_E,
                BarcodeFormat.CODE_128,
                BarcodeFormat.QR_CODE
            ),
            DecodeHintType.TRY_HARDER to true
        )
        setHints(hints)
    }

    suspend fun analyze(imageProxy: ImageProxy): BarcodeDetection? {
        val mediaImage = imageProxy.image ?: return null
        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        val results = runCatching { barcodeScanner.process(inputImage).await() }.getOrNull().orEmpty()
        val mlKitResult = results.firstOrNull { result ->
            val payload = result.rawValue
            payload != null && payload.isNotBlank() && result.format in supportedFormats
        }
        if (mlKitResult != null) {
            return BarcodeDetection(
                value = mlKitResult.rawValue.orEmpty(),
                format = formatLabel(mlKitResult.format)
            )
        }

        val bitmap = imageProxy.toBitmap() ?: return null
        return try {
            decodeWithZxing(bitmap)
        } finally {
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
    }

    private fun decodeWithZxing(bitmap: Bitmap): BarcodeDetection? {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val source = com.google.zxing.RGBLuminanceSource(width, height, pixels)
        val binary = BinaryBitmap(HybridBinarizer(source))
        return try {
            val result = zxingReader.decodeWithState(binary)
            BarcodeDetection(result.text, formatLabel(result.barcodeFormat))
        } catch (notFound: NotFoundException) {
            null
        } finally {
            zxingReader.reset()
        }
    }

    private fun formatLabel(format: Int): String = when (format) {
        Barcode.FORMAT_EAN_8 -> "EAN-8"
        Barcode.FORMAT_EAN_13 -> "EAN-13"
        Barcode.FORMAT_UPC_A -> "UPC-A"
        Barcode.FORMAT_UPC_E -> "UPC-E"
        Barcode.FORMAT_CODE_128 -> "Code 128"
        Barcode.FORMAT_QR_CODE -> "QR Code"
        else -> "Barcode"
    }

    private fun formatLabel(format: BarcodeFormat): String = when (format) {
        BarcodeFormat.EAN_8 -> "EAN-8"
        BarcodeFormat.EAN_13 -> "EAN-13"
        BarcodeFormat.UPC_A -> "UPC-A"
        BarcodeFormat.UPC_E -> "UPC-E"
        BarcodeFormat.CODE_128 -> "Code 128"
        BarcodeFormat.QR_CODE -> "QR Code"
        else -> "Barcode"
    }
}

data class BarcodeDetection(
    val value: String,
    val format: String
)

private fun ImageProxy.toBitmap(): Bitmap? {
    val plane = planes.firstOrNull() ?: return null
    val buffer: ByteBuffer = plane.buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    val raw = BitmapUtils.nv21ToBitmap(bytes, width, height) ?: return null
    return if (imageInfo.rotationDegrees == 0) {
        raw
    } else {
        val matrix = android.graphics.Matrix().apply { postRotate(imageInfo.rotationDegrees.toFloat()) }
        Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true).also {
            if (!raw.isRecycled) {
                raw.recycle()
            }
        }
    }
}
