package com.sirim.scanner.data.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import androidx.camera.core.ImageProxy
import com.sirim.scanner.analytics.PreprocessingMetricsEvent
import com.sirim.scanner.analytics.ScanAnalytics
import com.sirim.scanner.analytics.nanosecondsToMillis
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Rect as CvRect
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

class PreprocessedImage internal constructor(
    val original: Bitmap,
    val enhanced: Bitmap,
    val regionOfInterest: Rect?
) : AutoCloseable {
    override fun close() {
        if (!original.isRecycled) {
            original.recycle()
        }
        if (!enhanced.isRecycled) {
            enhanced.recycle()
        }
    }
}

object ImagePreprocessor {

    private val opencvInitialised = AtomicBoolean(false)
    private const val MAX_DIMENSION = 960

    fun preprocess(imageProxy: ImageProxy): PreprocessedImage? {
        val startNs = SystemClock.elapsedRealtimeNanos()
        val original = imageProxy.toBitmap()?.ensureMaxDimension(MAX_DIMENSION) ?: return null
        ensureOpenCv()

        val mats = mutableListOf<Mat>()
        val rgba = Mat()
        Utils.bitmapToMat(original, rgba)
        mats += rgba
        Imgproc.cvtColor(rgba, rgba, Imgproc.COLOR_RGBA2RGB)

        val gray = Mat()
        mats += gray
        Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGB2GRAY)

        val clahe = Imgproc.createCLAHE(3.0, Size(8.0, 8.0))
        val equalized = Mat()
        mats += equalized
        clahe.apply(gray, equalized)
        // Note: clahe doesn't have a release() method in OpenCV Android

        val denoised = Mat()
        mats += denoised
        Imgproc.bilateralFilter(equalized, denoised, 7, 100.0, 100.0)

        val sharpenKernel = Mat(3, 3, CvType.CV_32F)
        mats += sharpenKernel
        sharpenKernel.put(0, 0,
            0.0, -1.0, 0.0,
            -1.0, 5.0, -1.0,
            0.0, -1.0, 0.0
        )
        val sharpened = Mat()
        mats += sharpened
        Imgproc.filter2D(denoised, sharpened, -1, sharpenKernel)

        val thresholded = Mat()
        mats += thresholded
        Imgproc.adaptiveThreshold(
            sharpened,
            thresholded,
            255.0,
            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
            Imgproc.THRESH_BINARY,
            25,
            8.0
        )

        val deskewed = deskew(thresholded)
        if (deskewed !== thresholded) {
            mats += deskewed
        }
        val roi = detectRoi(deskewed)
        val finalMat = if (roi != null) Mat(deskewed, roi) else deskewed
        if (finalMat !== deskewed) {
            mats += finalMat
        }

        val rgbaFinal = Mat()
        mats += rgbaFinal
        Imgproc.cvtColor(finalMat, rgbaFinal, Imgproc.COLOR_GRAY2RGBA)
        val enhanced = Bitmap.createBitmap(rgbaFinal.cols(), rgbaFinal.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(rgbaFinal, enhanced)

        val region = roi?.let { Rect(it.x, it.y, it.width, it.height) }

        mats.forEach(Mat::release)

        val durationMs = (SystemClock.elapsedRealtimeNanos() - startNs).nanosecondsToMillis()
        ScanAnalytics.reportPreprocessingMetrics(
            PreprocessingMetricsEvent(
                durationMillis = durationMs,
                inputWidth = original.width,
                inputHeight = original.height,
                roiDetected = roi != null
            )
        )

        return PreprocessedImage(
            original = original,
            enhanced = enhanced,
            regionOfInterest = region
        )
    }

    private fun ensureOpenCv() {
        if (opencvInitialised.get()) return
        opencvInitialised.compareAndSet(false, OpenCVLoader.initLocal())
    }

    private fun deskew(source: Mat): Mat {
        val nonZero = Mat()
        Core.findNonZero(source, nonZero)
        if (nonZero.empty()) {
            nonZero.release()
            return source
        }
        val points = MatOfPoint2f()
        nonZero.convertTo(points, CvType.CV_32F)
        nonZero.release()
        val box = Imgproc.minAreaRect(points)
        points.release()
        var angle = box.angle
        if (angle < -45) angle += 90.0
        if (abs(angle) < 0.5) {
            return source
        }
        val rotationMatrix = Imgproc.getRotationMatrix2D(box.center, angle, 1.0)
        val rotated = Mat()
        Imgproc.warpAffine(source, rotated, rotationMatrix, source.size(), Imgproc.INTER_LINEAR)
        rotationMatrix.release()
        return rotated
    }

    private fun detectRoi(source: Mat): CvRect? {
        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        val working = source.clone()
        Imgproc.GaussianBlur(working, working, Size(5.0, 5.0), 0.0)
        Imgproc.Canny(working, working, 50.0, 150.0)
        Imgproc.findContours(working, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
        hierarchy.release()
        working.release()
        val largest = contours.maxByOrNull { contour -> Imgproc.contourArea(contour) }
        contours.filter { it !== largest }.forEach(Mat::release)
        if (largest == null) {
            return null
        }
        val rect = Imgproc.boundingRect(largest)
        largest.release()
        val minArea = source.width() * source.height() * 0.1
        return if (rect.width * rect.height < minArea) null else rect
    }
}

private fun Bitmap.ensureMaxDimension(maxDimension: Int): Bitmap {
    val largestSide = max(width, height)
    if (largestSide <= maxDimension) {
        return this
    }
    val scale = maxDimension.toFloat() / largestSide
    val targetWidth = (width * scale).roundToInt().coerceAtLeast(1)
    val targetHeight = (height * scale).roundToInt().coerceAtLeast(1)
    val scaled = Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true)
    if (scaled !== this && !isRecycled) {
        recycle()
    }
    return scaled
}