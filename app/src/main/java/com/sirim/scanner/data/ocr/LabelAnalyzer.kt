// app/src/main/java/com/sirim/scanner/data/ocr/LabelAnalyzer.kt
package com.sirim.scanner.data.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.Result
import com.google.zxing.common.HybridBinarizer
import java.nio.ByteBuffer
import java.util.ArrayDeque
import kotlin.io.use
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.tasks.await

/**
 * Analyzes camera frames to extract SIRIM label information using OCR and barcode detection.
 *
 * The analyzer performs the following steps:
 * 1. Pre-process the incoming [ImageProxy] to enhance contrast and isolate the region of interest.
 * 2. Run ML Kit text recognition followed by optional rotated retries to capture skewed text.
 * 3. Merge OCR output with QR payloads (via ML Kit and ZXing) and aggregate results across frames
 *    to reach a stable consensus before reporting success.
 *
 * @property frameWindow Number of frame observations required for consensus.
 * @property successThreshold Minimum average confidence before reporting success.
 * @property frameIntervalMillis Minimum delay between successive frame analyses to throttle work.
 */
class LabelAnalyzer(
    private val frameWindow: Int = 5,
    private val successThreshold: Float = 0.75f,
    private val frameIntervalMillis: Long = 150L
) {

    private companion object {
        private val ROTATION_ANGLES = floatArrayOf(90f, -90f)
    }

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val barcodeScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
    )
    private val multiFormatReader = MultiFormatReader()
    private val recentFrames = ArrayDeque<FrameObservation>()
    private var lastAnalysisTimestamp = 0L

    /**
     * Analyzes a single [ImageProxy] frame and returns OCR/QR extraction results.
     *
     * @param imageProxy Frame provided by CameraX.
     * @return [OcrResult] describing the extracted fields, confidence, and status.
     */
    suspend fun analyze(imageProxy: ImageProxy): OcrResult {
        if (!shouldProcessFrame()) {
            return OcrResult.Skipped
        }

        val preprocessed = ImagePreprocessor.preprocess(imageProxy)
            ?: return OcrResult.Failure(OcrFailureReason.Preprocessing, "Image preprocessing failed")

        return preprocessed.use { processed ->
            val textSegments = mutableListOf<String>()

            val primaryRecognition = recognizeText(processed.enhanced)
            primaryRecognition?.let(textSegments::add)

            if (textSegments.isEmpty()) {
                val rotatedResult = tryRotatedRecognition(processed.enhanced, ROTATION_ANGLES)
                rotatedResult?.let(textSegments::add)
            }

            val combinedText = textSegments.joinToString("\n") { it.trim() }
            val parsedFields = if (combinedText.isNotBlank()) {
                SirimLabelParser.parse(combinedText).toMutableMap()
            } else {
                mutableMapOf()
            }

            val barcodeImage = InputImage.fromBitmap(processed.original, 0)
            val barcodeResults = barcodeScanner.safeProcess(barcodeImage)
            val qrPayload = barcodeResults.firstOrNull()?.rawValue
                ?: decodeWithZxing(processed.original)
                ?: processed.regionOfInterest?.let { region ->
                    processed.original.safeCrop(region)?.use { cropped ->
                        decodeWithZxing(cropped)
                    }
                }

            val qrField = SirimLabelParser.mergeWithQr(parsedFields, qrPayload)
            val observation = if (parsedFields.isEmpty()) {
                null
            } else {
                FrameObservation(
                    fields = parsedFields,
                    qr = qrField,
                    confidence = parsedFields.averageConfidence()
                )
            }
            val consensus = observation?.let(::updateConsensus)

            val aggregatedFields = consensus?.fields ?: parsedFields
            val aggregatedConfidence = consensus?.confidence ?: observation?.confidence ?: 0f
            val frames = consensus?.frames ?: observation?.let { recentFrames.size } ?: 0
            val isStable = consensus?.isStable ?: (aggregatedConfidence >= successThreshold && frames >= 2)

            if (aggregatedFields.isEmpty()) {
                recentFrames.clear()
                return@use OcrResult.Empty
            }

            val qrValue = consensus?.qr?.value ?: qrField?.value ?: qrPayload
            val resultBitmap = processed.original.copy(Bitmap.Config.ARGB_8888, false)
                ?: Bitmap.createBitmap(processed.original)
            if (isStable) {
                OcrResult.Success(
                    fields = aggregatedFields,
                    qrCode = qrValue,
                    bitmap = resultBitmap,
                    confidence = aggregatedConfidence,
                    frames = frames
                )
            } else {
                OcrResult.Partial(
                    fields = aggregatedFields,
                    qrCode = qrValue,
                    bitmap = resultBitmap,
                    confidence = aggregatedConfidence,
                    frames = frames
                )
            }
        }
    }

    private fun updateConsensus(observation: FrameObservation): ConsensusResult? {
        recentFrames.addLast(observation)
        if (recentFrames.size > frameWindow) {
            recentFrames.removeFirst()
        }
        val aggregated = mutableMapOf<String, FieldConfidence>()
        val frequency = mutableMapOf<String, MutableMap<String, Int>>()

        recentFrames.forEach { frame ->
            frame.fields.forEach { (key, candidate) ->
                val valueKey = candidate.value.uppercase()
                val fieldFrequency = frequency.getOrPut(key) { mutableMapOf() }
                val count = fieldFrequency.getOrPut(valueKey) { 0 } + 1
                fieldFrequency[valueKey] = count
                val boostedConfidence = (candidate.confidence + count * 0.08f).coerceAtMost(0.98f)
                val adjusted = candidate.copy(confidence = boostedConfidence)
                aggregated.merge(key, adjusted) { old, new ->
                    when {
                        old.value.equals(new.value, ignoreCase = true) ->
                            old.copy(confidence = max(old.confidence, new.confidence), notes = old.notes + new.notes)
                        new.confidence > old.confidence -> new.mergeWith(old)
                        else -> old.mergeWith(new)
                    }
                }
            }
        }

        val consensusConfidence = aggregated.values.map(FieldConfidence::confidence).average().toFloat()
        val stableFields = aggregated.count { (key, candidate) ->
            val fieldFreq = frequency[key]?.get(candidate.value.uppercase()) ?: 0
            fieldFreq >= min(3, frameWindow / 2)
        }
        val isStable = stableFields >= 3 || (consensusConfidence >= successThreshold && stableFields >= 2)
        val qrField = aggregated["sirimSerialNo"]
        return ConsensusResult(
            fields = aggregated,
            confidence = consensusConfidence,
            isStable = isStable,
            frames = recentFrames.size,
            qr = qrField
        )
    }

    private fun Map<String, FieldConfidence>.averageConfidence(): Float =
        if (isEmpty()) 0f else values.map(FieldConfidence::confidence).average().toFloat()

    private suspend fun recognizeText(bitmap: Bitmap): String? {
        return runCatching {
            recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
        }.getOrNull()?.text?.takeIf { it.isNotBlank() }
    }

    private suspend fun tryRotatedRecognition(
        bitmap: Bitmap,
        angles: FloatArray
    ): String? {
        angles.forEach { angle ->
            val rotated = bitmap.rotate(angle) ?: return@forEach
            try {
                val result = recognizeText(rotated)
                if (!result.isNullOrBlank()) {
                    return result.also {
                        if (rotated !== bitmap && !rotated.isRecycled) {
                            rotated.recycle()
                        }
                    }
                }
            } finally {
                if (rotated !== bitmap && !rotated.isRecycled) {
                    rotated.recycle()
                }
            }
        }
        return null
    }

    private fun shouldProcessFrame(): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now - lastAnalysisTimestamp < frameIntervalMillis) {
            return false
        }
        lastAnalysisTimestamp = now
        return true
    }

    private suspend fun com.google.mlkit.vision.barcode.BarcodeScanner.safeProcess(image: InputImage) =
        runCatching { process(image).await() }.getOrNull().orEmpty()

    private fun decodeWithZxing(bitmap: Bitmap): String? {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val source = RGBLuminanceSource(width, height, pixels)
        val binary = BinaryBitmap(HybridBinarizer(source))
        return runCatching<Result> {
            multiFormatReader.decode(binary)
        }.onSuccess {
            multiFormatReader.reset()
        }.getOrNull()?.text
    }
}

sealed interface OcrResult {
    data class Success(
        val fields: Map<String, FieldConfidence>,
        val qrCode: String?,
        val bitmap: Bitmap?,
        val confidence: Float,
        val frames: Int
    ) : OcrResult

    data class Partial(
        val fields: Map<String, FieldConfidence>,
        val qrCode: String?,
        val bitmap: Bitmap?,
        val confidence: Float,
        val frames: Int
    ) : OcrResult

    data class Failure(
        val reason: OcrFailureReason,
        val detail: String? = null
    ) : OcrResult

    data object Empty : OcrResult

    data object Skipped : OcrResult
}

enum class OcrFailureReason {
    Preprocessing
}

private data class FrameObservation(
    val fields: Map<String, FieldConfidence>,
    val qr: FieldConfidence?,
    val confidence: Float
)

private data class ConsensusResult(
    val fields: Map<String, FieldConfidence>,
    val confidence: Float,
    val isStable: Boolean,
    val frames: Int,
    val qr: FieldConfidence?
)

private fun ImageProxy.toBitmap(): Bitmap? {
    val buffer: ByteBuffer = planes.firstOrNull()?.buffer ?: return null
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    return BitmapUtils.nv21ToBitmap(bytes, width, height)?.rotate(imageInfo.rotationDegrees.toFloat())
}

private fun Bitmap.rotate(degrees: Float): Bitmap? = if (degrees == 0f) this else {
    val matrix = android.graphics.Matrix().apply { postRotate(degrees) }
    Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}

private inline fun <T> Bitmap.use(block: (Bitmap) -> T): T {
    return try {
        block(this)
    } finally {
        if (!isRecycled) recycle()
    }
}

private fun Bitmap.safeCrop(rect: Rect): Bitmap? = runCatching {
    val left = rect.left.coerceIn(0, width - 1)
    val top = rect.top.coerceIn(0, height - 1)
    val right = rect.right.coerceIn(left + 1, width)
    val bottom = rect.bottom.coerceIn(top + 1, height)
    Bitmap.createBitmap(this, left, top, right - left, bottom - top)
}.getOrNull()
