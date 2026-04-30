package com.betpass.mc01pilot.airport.notam

import com.betpass.mc01pilot.airport.data.Notam
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotamDecoderTest {
    private val decoder = NotamDecoder()

    @Test
    fun extractHeaderAndQLine() {
        val raw = """F0813/26 R F8199/25 27/02/2026 19:34
Q) SBBS/QACXX/IV/NBO/AE/033/033/2310S04656W007
CTR - ALTERACOES NA CIRCULACAO VFR""".trimIndent()
        val decoded = decoder.decode(Notam("x", raw, 0L, null))
        assertEquals("F0813/26", decoded.notamId)
        assertEquals("F8199/25", decoded.replacesNotam)
        assertEquals("27/02/2026 19:34", decoded.publishedAt)
        assertEquals("SBBS", decoded.fir)
        assertEquals("QACXX", decoded.qCode)
        assertEquals("IV", decoded.traffic)
    }

    @Test
    fun classifiesRunwayClosedAsCritical() {
        val decoded = decoder.decodeRaw("RWY 09 CLSD")
        assertEquals(NotamSeverity.CRITICAL, decoded.severity)
    }

    @Test
    fun detectsFrequencyAndRunway() {
        val decoded = decoder.decodeRaw("CTC COMPULSORIO PELA FREQ 118.750 MHZ RWY 09")
        assertEquals("118.750 MHz", decoded.affectedFrequency)
        assertEquals("RWY 09", decoded.affectedRunway)
        assertTrue(decoded.severity == NotamSeverity.HIGH || decoded.severity == NotamSeverity.MEDIUM)
    }

    @Test
    fun summaryAndRawPreserved() {
        val raw = "CTR CIRCULACAO VFR CTC COMPULSORIO PELA FREQ 118.750 MHZ"
        val decoded = decoder.decodeRaw(raw)
        assertTrue(decoded.plainLanguageSummary.isNotBlank())
        assertEquals(raw, decoded.rawText)
        assertNotNull(decoded.decodedTerms.find { it.original.equals("CTC", true) })
    }
}
