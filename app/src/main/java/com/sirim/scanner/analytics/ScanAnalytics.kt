package com.sirim.scanner.analytics

import android.util.Log
import com.sirim.scanner.data.ocr.FieldNote
import com.sirim.scanner.data.ocr.FieldSource
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Centralised analytics and telemetry helper for the scanning pipeline. The app can swap the
 * underlying sink implementation at runtime (for example to route events to Firebase Crashlytics)
 * while the rest of the codebase only depends on this facade.
 */
object ScanAnalytics {
    private const val TAG = "ScanAnalytics"

    private val sinkRef: AtomicReference<ScanAnalyticsSink> =
        AtomicReference<ScanAnalyticsSink>(LogScanAnalyticsSink)

    fun setSink(sink: ScanAnalyticsSink) {
        sinkRef.set(sink)
    }

    fun reportSerialExtraction(event: SerialExtractionEvent) {
        dispatch(event)
    }

    fun reportSerialVerification(event: SerialVerificationEvent) {
        dispatch(event)
    }

    fun reportPreprocessingMetrics(event: PreprocessingMetricsEvent) {
        dispatch(event)
    }

    fun reportFeedback(event: FeedbackSubmittedEvent) {
        dispatch(event)
    }

    fun installCrashlyticsIfAvailable() {
        runCatching {
            val sink = CrashlyticsScanAnalyticsSink()
            if (sink.isAvailable) {
                setSink(sink)
            }
        }.onFailure { throwable ->
            Log.w(TAG, "Crashlytics sink unavailable", throwable)
        }
    }

    private fun dispatch(event: ScanAnalyticsEvent) {
        runCatching { sinkRef.get().log(event) }
            .onFailure { throwable -> Log.w(TAG, "Failed to record analytics event: $event", throwable) }
    }
}

sealed interface ScanAnalyticsEvent

data class SerialExtractionEvent(
    val source: FieldSource,
    val serialPreview: String,
    val confidence: Float,
    val notes: Set<FieldNote>,
    val matchedWithQr: Boolean?
) : ScanAnalyticsEvent {
    override fun toString(): String =
        "SerialExtraction(source=$source, preview=$serialPreview, confidence=${"%.2f".format(Locale.US, confidence)}, " +
            "notes=${notes.joinToString()}, matched=$matchedWithQr)"
}

data class SerialVerificationEvent(
    val ocrSerial: String?,
    val qrSerial: String,
    val agreed: Boolean
) : ScanAnalyticsEvent {
    override fun toString(): String =
        "SerialVerification(ocr=$ocrSerial, qr=$qrSerial, agreed=$agreed)"
}

data class PreprocessingMetricsEvent(
    val durationMillis: Long,
    val inputWidth: Int,
    val inputHeight: Int,
    val roiDetected: Boolean
) : ScanAnalyticsEvent {
    override fun toString(): String =
        "PreprocessingMetrics(duration=${durationMillis}ms, size=${inputWidth}x$inputHeight, roi=$roiDetected)"
}

data class FeedbackSubmittedEvent(
    val descriptionLength: Int,
    val contactProvided: Boolean,
    val includeDiagnostics: Boolean,
    val timestampMillis: Long = System.currentTimeMillis()
) : ScanAnalyticsEvent {
    override fun toString(): String =
        "FeedbackSubmitted(len=$descriptionLength, contact=$contactProvided, diagnostics=$includeDiagnostics)"
}

fun interface ScanAnalyticsSink {
    fun log(event: ScanAnalyticsEvent)
}

object LogScanAnalyticsSink : ScanAnalyticsSink {
    override fun log(event: ScanAnalyticsEvent) {
        Log.d("ScanAnalytics", event.toString())
    }
}

class CrashlyticsScanAnalyticsSink : ScanAnalyticsSink {
    private val crashlytics: Any? = runCatching {
        val clazz = Class.forName("com.google.firebase.crashlytics.FirebaseCrashlytics")
        clazz.getMethod("getInstance").invoke(null)
    }.getOrNull()

    private val logMethod = crashlytics?.let {
        runCatching {
            it.javaClass.getMethod("log", String::class.java)
        }.getOrNull()
    }

    private val setCustomKeyMethod = crashlytics?.let {
        runCatching {
            it.javaClass.getMethod("setCustomKey", String::class.java, String::class.java)
        }.getOrNull()
    }

    val isAvailable: Boolean = crashlytics != null && logMethod != null

    override fun log(event: ScanAnalyticsEvent) {
        val instance = crashlytics ?: return
        val log = logMethod ?: return
        val payload = event.toString()
        runCatching { log.invoke(instance, payload) }
        if (event is SerialExtractionEvent) {
            val setKey = setCustomKeyMethod ?: return
            runCatching {
                setKey.invoke(instance, "serial_confidence", event.confidence.toString())
                setKey.invoke(instance, "serial_source", event.source.name)
            }
        }
    }
}

fun Long.nanosecondsToMillis(): Long = TimeUnit.NANOSECONDS.toMillis(this)
