package com.betpass.mc01pilot.airport.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiswebHtmlParserTest {
    @Test
    fun `extracts frequencies from html`() {
        val html = """
            <html><body>
            <table>
              <tr><td>TWR</td><td>118.100</td></tr>
              <tr><td>RÁDIO</td><td>123,450</td></tr>
            </table>
            </body></html>
        """.trimIndent()

        val parsed = AiswebHtmlParser.parse(html)

        assertEquals(2, parsed.frequencies.size)
        assertTrue(parsed.frequencies.any { it.type == "TWR" && it.value == "118.100" })
        assertTrue(parsed.frequencies.any { it.type == "RADIO" && it.value == "123.450" })
    }

    @Test
    fun `extracts frequencies from json encoded html payload`() {
        val jsonEncoded = "\"<div>APP: 119.750</div>\""

        val parsed = AiswebHtmlParser.parse(jsonEncoded)

        assertEquals(1, parsed.frequencies.size)
        assertEquals("APP", parsed.frequencies.first().type)
        assertEquals("119.750", parsed.frequencies.first().value)
    }
}
