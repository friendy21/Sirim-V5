package com.sirim.scanner.feedback

import com.sirim.scanner.analytics.FeedbackSubmittedEvent
import com.sirim.scanner.analytics.ScanAnalytics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Handles user feedback submissions. At the moment feedback is forwarded to the analytics pipeline
 * so that remote logging providers (Crashlytics, Sentry, etc.) can persist the reports. The design
 * keeps IO work off the main thread to avoid UI jank.
 */
object FeedbackManager {
    suspend fun submitFeedback(submission: FeedbackSubmission) {
        withContext(Dispatchers.IO) {
            ScanAnalytics.reportFeedback(
                FeedbackSubmittedEvent(
                    descriptionLength = submission.description.length,
                    contactProvided = !submission.contact.isNullOrBlank(),
                    includeDiagnostics = submission.includeDiagnostics
                )
            )
        }
    }
}

data class FeedbackSubmission(
    val description: String,
    val contact: String?,
    val includeDiagnostics: Boolean
)
