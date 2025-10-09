package com.sirim.scanner

import com.sirim.scanner.data.ocr.FieldConfidence
import com.sirim.scanner.data.ocr.FieldSource
import com.sirim.scanner.data.validation.FieldValidator
import org.junit.Assert.assertTrue
import org.junit.Test

class FieldValidatorTest {
    @Test
    fun `validate rejects invalid serial format`() {
        val fields = mapOf(
            "sirimSerialNo" to FieldConfidence("INVALID", 0.8f, FieldSource.OCR)
        )

        val result = FieldValidator.validate(fields)
        assertTrue(result.errors.containsKey("sirimSerialNo"))
    }
}
