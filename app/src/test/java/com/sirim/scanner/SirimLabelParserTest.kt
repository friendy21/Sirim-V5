package com.sirim.scanner

import com.sirim.scanner.data.ocr.SirimLabelParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SirimLabelParserTest {
    @Test
    fun `parse extracts serial number correctly`() {
        val input = "SIRIM Serial No: TEA1234567"
        val result = SirimLabelParser.parse(input)

        val serial = result["sirimSerialNo"]
        assertEquals("TEA1234567", serial?.value)
        assertTrue("Expected confidence to be > 0.5", (serial?.confidence ?: 0f) > 0.5f)
    }

    @Test
    fun `parse handles malformed input gracefully`() {
        val result = SirimLabelParser.parse("")
        assertTrue(result.isEmpty())
    }
}
