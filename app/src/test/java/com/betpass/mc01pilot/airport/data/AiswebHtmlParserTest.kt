package com.betpass.mc01pilot.airport.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiswebHtmlParserTest {
    @Test
    fun `parse aerodrome html with com and metar taf`() {
        val html = """
            <h1>Comandante Rolim Adolfo Amaro (SBJD) <span>Jundiaí/SP - CIAD: <strong>SP0031</strong></span></h1>
            <script>L.map('location-map').setView([-23.181666666667 , -46.943611111111], 14);</script>
            <td style="width: 165px;">23 10 54S/046 56 37W</td>
            <td>753 <strong>(2470)</strong></td>
            <tr><td valign="top"><strong>COM</strong> - </td><td>
              <div>TORRE <span title="(DLY 1015-2145)">[2]</span> 118.750</div>
              <div>SOLO 121.650 <span title="(DLY 1015-2145)">[2]</span></div>
            </td></tr>
            <strong>COMPL</strong> -
            <ul><li>[<strong>2</strong>] (DLY 1015-2145)</li></ul>
            <h5>METAR</h5><p>METAR SBJD 291200Z 13005KT 100V160 6000 NSC 22/21 Q1018=</p>
            <h5>TAF</h5><p>TAF SBJD 290900Z 2912/2924 RMK PGM=</p>
            <h4>IAC</h4><ul><li><a href="https://aisweb.decea.gov.br/download/?arquivo=abc&amp;apikey=123">RNP A RWY 18</a></li></ul>
        """.trimIndent()

        val parsed = AiswebAerodromeParser.parse(html, "SBJD")

        assertEquals("Comandante Rolim Adolfo Amaro", parsed.name)
        assertEquals("Jundiaí", parsed.city)
        assertEquals("SP", parsed.state)
        assertEquals("SP0031", parsed.ciad)
        assertEquals("23 10 54S/046 56 37W", parsed.coordinates?.raw)
        assertEquals(-23.181666666667, parsed.coordinates?.latitude)
        assertEquals(2, parsed.frequencies.size)
        assertEquals("(DLY 1015-2145)", parsed.frequencies.first().schedule)
        assertTrue(parsed.charts.first().url.contains("&apikey=123"))
        assertTrue(parsed.metar!!.startsWith("METAR SBJD"))
        assertTrue(parsed.taf!!.startsWith("TAF SBJD"))
    }

    @Test
    fun `parse metar taf when header structure changes`() {
        val html = """
            <h1>Aeroporto Teste (SBJD) <span>Jundiaí/SP - CIAD: <strong>SP0031</strong></span></h1>
            <h6>METAR</h6>
            <div class="metar-box"><pre>METAR SBJD 301300Z 08007KT CAVOK 25/17 Q1015=</pre></div>
            <strong>TAF</strong>
            <div><pre>TAF SBJD 301100Z 3012/0112 14008KT CAVOK TX28/3018Z TN16/0108Z=</pre></div>
        """.trimIndent()

        val parsed = AiswebAerodromeParser.parse(html, "SBJD")

        assertEquals("METAR SBJD 301300Z 08007KT CAVOK 25/17 Q1015=", parsed.metar)
        assertEquals("TAF SBJD 301100Z 3012/0112 14008KT CAVOK TX28/3018Z TN16/0108Z=", parsed.taf)
    }
}
