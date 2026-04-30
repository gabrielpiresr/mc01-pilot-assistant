package com.betpass.mc01pilot.airport.data

import android.util.Log
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import javax.xml.parsers.DocumentBuilderFactory

internal data class AiswebAerodromeData(
    val icao: String,
    val name: String? = null,
    val city: String? = null,
    val state: String? = null,
    val ciad: String? = null,
    val coordinates: Coordinates? = null,
    val elevation: Elevation? = null,
    val runways: List<RunwayData> = emptyList(),
    val frequencies: List<FrequencyData> = emptyList(),
    val complements: Map<String, String> = emptyMap(),
    val rmk: List<RmkSection> = emptyList(),
    val notams: List<Notam> = emptyList(),
    val metar: String? = null,
    val taf: String? = null,
    val charts: List<ChartData> = emptyList(),
    val opea: OpeaData? = null
)

internal data class Coordinates(val raw: String? = null, val latitude: Double? = null, val longitude: Double? = null)
internal data class Elevation(val meters: Int? = null, val feet: Int? = null)
internal data class RunwayData(val designators: List<String>, val lengthM: Int? = null, val widthM: Int? = null, val surface: String? = null, val resistance: String? = null)
internal data class FrequencyData(val service: String, val frequency: String, val notesRefs: List<String> = emptyList(), val schedule: String? = null, val raw: String)
internal data class RmkSection(val section: String, val items: List<String>)
internal data class ChartData(val category: String, val title: String, val url: String)
internal data class OpeaData(val xlsUrl: String? = null, val kmlUrl: String? = null, val csvUrl: String? = null, val updatedAt: String? = null)
internal data class NotamCredentials(val apiRoute: String, val apiKey: String, val apiPass: String)

internal object AiswebAerodromeService {
    private const val TAG = "AiswebAerodrome"
    private const val TTL_MS = 15 * 60 * 1000L
    private val htmlCache = ConcurrentHashMap<String, Pair<Long, String>>()

    suspend fun fetchAiswebAerodromeHtml(icao: String): String {
        val key = icao.uppercase()
        val now = System.currentTimeMillis()
        htmlCache[key]?.let { (ts, value) ->
            if ((now - ts) < TTL_MS) {
                Log.d(TAG, "cache hit for $key (ageMs=${now - ts}, size=${value.length})")
                return value
            }
        }
        val url = "https://aisweb.decea.mil.br/?i=aerodromos&codigo=$key"
        Log.d(TAG, "fetching aerodrome html for $key: $url")
        val html = fetch(url)
        Log.d(TAG, "html fetched for $key (size=${html.length})")
        htmlCache[key] = now to html
        return html
    }

    fun parseAiswebAerodromeHtml(html: String, icao: String): AiswebAerodromeData {
        Log.d(TAG, "starting parse for $icao (inputSize=${html.length})")
        val parsed = AiswebAerodromeParser.parse(html, icao)
        Log.d(
            TAG,
            "parse result for ${parsed.icao}: name=${parsed.name}, city=${parsed.city}, state=${parsed.state}, ciad=${parsed.ciad}, " +
                "freq=${parsed.frequencies.size}, charts=${parsed.charts.size}, rmk=${parsed.rmk.size}, metar=${!parsed.metar.isNullOrBlank()}, taf=${!parsed.taf.isNullOrBlank()}"
        )
        return parsed
    }

    fun extractNotamApiCredentials(html: String): NotamCredentials? {
        val route = Regex("https?://[^\"']+/api/\\?", RegexOption.IGNORE_CASE).find(html)?.value ?: "https://aisweb.decea.mil.br/api/?"
        val apiKey = Regex("apiKey\\s*[:=]\\s*[\"']([^\"']+)").find(html)?.groupValues?.get(1)
        val apiPass = Regex("apiPass\\s*[:=]\\s*[\"']([^\"']+)").find(html)?.groupValues?.get(1)
        if (apiKey.isNullOrBlank() || apiPass.isNullOrBlank()) {
            Log.w(TAG, "NOTAM credentials not found in html (route=$route, keyFound=${!apiKey.isNullOrBlank()}, passFound=${!apiPass.isNullOrBlank()})")
            return null
        }
        Log.d(TAG, "NOTAM credentials extracted (route=$route, keyLen=${apiKey.length}, passLen=${apiPass.length})")
        return NotamCredentials(route, apiKey, apiPass)
    }

    suspend fun fetchAiswebNotams(icao: String, credentials: NotamCredentials): List<Notam> {
        val url = "${credentials.apiRoute}apiKey=${credentials.apiKey}&apiPass=${credentials.apiPass}&area=notam&icaocode=${icao.uppercase()}&dist=N"
        Log.d(TAG, "fetching NOTAM xml for ${icao.uppercase()}: $url")
        val xml = fetch(url)
        Log.d(TAG, "NOTAM xml fetched for ${icao.uppercase()} (size=${xml.length})")
        val parsed = parseAiswebNotamXml(xml)
        Log.d(TAG, "NOTAM parsed count for ${icao.uppercase()}: ${parsed.size}")
        return parsed
    }

    private fun fetch(url: String): String {
        return try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("User-Agent", "MC01PilotAssistant/1.0")
            val code = connection.responseCode
            Log.d(TAG, "HTTP $code for $url")
            if (code !in 200..299) {
                val errorText = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                Log.e(TAG, "HTTP error for $url: code=$code body=${errorText.take(300)}")
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.e(TAG, "fetch failed for $url: ${e.message}", e)
            throw e
        }
    }
}

internal object AiswebAerodromeParser {
    private const val TAG = "AiswebAerodrome"
    fun parse(html: String, icao: String): AiswebAerodromeData {
        val cleanHtml = html.replace("\n", " ")
        val title = Regex("<h1>(.*?)</h1>", RegexOption.IGNORE_CASE).find(cleanHtml)?.groupValues?.get(1).orEmpty()
        val name = Regex("^(.*?)\\(", RegexOption.IGNORE_CASE).find(stripTags(title))?.groupValues?.get(1)?.norm()
        val spanText = Regex("<span[^>]*>(.*?)</span>", RegexOption.IGNORE_CASE).find(title)?.groupValues?.get(1)?.let { stripTags(it) }.orEmpty()
        val cityState = Regex("([A-Za-zÀ-ÿ ]+)\\s*/\\s*([A-Z]{2})").find(spanText)
        val ciad = Regex("CIAD:\\s*([A-Z0-9]+)", RegexOption.IGNORE_CASE).find(stripTags(title))?.groupValues?.get(1)
        val coordsRaw = Regex("(\\d{2}\\s+\\d{2}\\s+\\d{2}[NS]/\\d{3}\\s+\\d{2}\\s+\\d{2}[EW])").find(cleanHtml)?.groupValues?.get(1)
        val mapMatch = Regex("setView\\(\\[\\s*([-0-9.]+)\\s*,\\s*([-0-9.]+)").find(cleanHtml)
        val elevMatch = Regex(">(\\d{2,4})\\s*<strong>\\((\\d{2,5})\\)</strong>").find(cleanHtml)

        val complements = parseComplements(cleanHtml)
        val frequencies = parseFrequencies(cleanHtml, complements)
        val rmk = parseRmk(cleanHtml)
        val charts = parseCharts(cleanHtml)
        val opea = parseOpea(cleanHtml)
        Log.d(TAG, "fields extracted for ${icao.uppercase()}: titleFound=${title.isNotBlank()}, coordsRawFound=${coordsRaw != null}, mapCoordsFound=${mapMatch != null}, elevationFound=${elevMatch != null}")
        Log.d(TAG, "sections for ${icao.uppercase()}: complements=${complements.size}, frequencies=${frequencies.size}, rmk=${rmk.size}, charts=${charts.size}, opea=${opea != null}")

        return AiswebAerodromeData(
            icao = icao.uppercase(),
            name = name,
            city = cityState?.groupValues?.get(1)?.norm(),
            state = cityState?.groupValues?.get(2),
            ciad = ciad,
            coordinates = Coordinates(coordsRaw?.norm(), mapMatch?.groupValues?.get(1)?.toDoubleOrNull(), mapMatch?.groupValues?.get(2)?.toDoubleOrNull()),
            elevation = Elevation(elevMatch?.groupValues?.get(1)?.toIntOrNull(), elevMatch?.groupValues?.get(2)?.toIntOrNull()),
            frequencies = frequencies,
            complements = complements,
            rmk = rmk,
            metar = extractByHeader(cleanHtml, "METAR"),
            taf = extractByHeader(cleanHtml, "TAF"),
            charts = charts,
            opea = opea
        )
    }

    private fun parseFrequencies(html: String, complements: Map<String, String>): List<FrequencyData> {
        val block = Regex("<strong>COM</strong>\\s*-\\s*</td>\\s*<td>(.*?)</td>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(html)?.groupValues?.get(1) ?: return emptyList()
        return Regex("<div>(.*?)</div>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).findAll(block).mapNotNull { m ->
            val rawHtml = m.groupValues[1]
            val freq = Regex("\\b\\d{3}\\.\\d{3}\\b").find(rawHtml)?.value ?: return@mapNotNull null
            val refs = Regex("\\[(\\d+)]").findAll(stripTags(rawHtml)).map { it.groupValues[1] }.toList()
            val schedule = Regex("title=\"([^\"]+)\"").find(rawHtml)?.groupValues?.get(1) ?: refs.firstNotNullOfOrNull { complements[it] }
            val service = stripTags(rawHtml).replace(Regex("\\b\\d{3}\\.\\d{3}\\b"), " ").replace(Regex("\\[\\d+]"), " ").norm()
            FrequencyData(service = service, frequency = freq, notesRefs = refs, schedule = schedule, raw = stripTags(rawHtml).norm())
        }.toList()
    }

    private fun parseComplements(html: String): Map<String, String> {
        val block = Regex("<strong>COMPL</strong>\\s*-\\s*<ul>(.*?)</ul>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(html)?.groupValues?.get(1) ?: return emptyMap()
        return Regex("<li>\\s*\\[<strong>(\\d+)</strong>]\\s*(.*?)</li>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .findAll(block).associate { it.groupValues[1] to stripTags(it.groupValues[2]).norm() }
    }

    private fun parseRmk(html: String): List<RmkSection> {
        val sections = mutableListOf<RmkSection>()
        val headerRegex = Regex("<h5[^>]*>(.*?)</h5>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val headers = headerRegex.findAll(html).toList()
        for ((index, match) in headers.withIndex()) {
            val section = stripTags(match.groupValues[1]).norm()
            if (section.equals("METAR", true) || section.equals("TAF", true)) continue
            val start = match.range.last + 1
            val end = headers.getOrNull(index + 1)?.range?.first ?: html.length
            val between = html.substring(start, end)
            val olBlock = Regex("<ol[^>]*>(.*?)</ol>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(between)?.groupValues?.get(1) ?: continue
            val items = Regex("<li[^>]*>(.*?)</li>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                .findAll(olBlock).map { stripTags(it.groupValues[1]).norm() }.filter { it.isNotBlank() }.toList()
            if (items.isNotEmpty()) sections.add(RmkSection(section, items))
        }
        return sections
    }

    private fun parseCharts(html: String): List<ChartData> = Regex("<h4[^>]*>([^<]+)</h4>\\s*<ul[^>]*>(.*?)</ul>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        .findAll(html).flatMap { m ->
            val category = m.groupValues[1].norm()
            Regex("<a[^>]+href=\"([^\"]*?/download/\\?arquivo=[^\"]+)\"[^>]*>(.*?)</a>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                .findAll(m.groupValues[2])
                .map { ChartData(category, stripTags(it.groupValues[2]).norm(), it.groupValues[1].replace("&amp;", "&")) }
        }.toList()

    private fun parseOpea(html: String): OpeaData? {
        val xls = Regex("href=\"([^\"]+outputFormat=excel2007[^\"]*)\"").find(html)?.groupValues?.get(1)
        val kml = Regex("href=\"([^\"]+outputFormat=kml[^\"]*)\"").find(html)?.groupValues?.get(1)
        val csv = Regex("href=\"([^\"]+outputFormat=csv[^\"]*)\"").find(html)?.groupValues?.get(1)
        val updated = Regex("[ÚU]ltima atualiza[çc][ãa]o:\\s*([^<]+)", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)?.norm()
        if (xls == null && kml == null && csv == null && updated == null) return null
        return OpeaData(xls, kml, csv, updated)
    }

    private fun extractByHeader(html: String, header: String): String? {
        val sectionPatterns = listOf(
            Regex("<h5[^>]*>\\s*$header\\s*</h5>(.*?)((<h5)|$)", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
            Regex("<(?:h4|h6|strong|b)[^>]*>\\s*$header\\s*</(?:h4|h6|strong|b)>(.*?)((<(?:h4|h5|h6|strong|b))|$)", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        )

        val sectionBody = sectionPatterns
            .asSequence()
            .mapNotNull { it.find(html)?.groupValues?.get(1) }
            .firstOrNull()

        val textCandidates = buildList {
            if (sectionBody != null) {
                addAll(
                    Regex("<(?:p|pre|div|span|td)[^>]*>(.*?)</(?:p|pre|div|span|td)>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                        .findAll(sectionBody)
                        .map { stripTags(it.groupValues[1]).norm() }
                        .filter { it.isNotBlank() }
                        .toList()
                )
                add(stripTags(sectionBody).norm())
            }
            addAll(
                Regex("\\b$header\\s+[A-Z]{4}\\b[^\\n\\r<]*", RegexOption.IGNORE_CASE)
                    .findAll(stripTags(html))
                    .map { it.value.norm() }
                    .toList()
            )
        }

        return textCandidates.firstOrNull { candidate ->
            candidate.matches(Regex("^$header\\s+[A-Z]{4}\\b.*", RegexOption.IGNORE_CASE))
        }
    }

    private fun stripTags(value: String): String = value.replace(Regex("<[^>]+>"), " ").replace("&nbsp;", " ").replace("&#160;", " ")
    private fun String.norm(): String = replace(Regex("\\s+"), " ").trim()
}

internal fun parseAiswebNotamXml(xml: String): List<Notam> {
    return runCatching {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = false
        val builder = factory.newDocumentBuilder()
        val document = builder.parse(ByteArrayInputStream(xml.toByteArray()))
        val candidates = listOf("item", "row", "notam")
            .flatMap { tag -> (0 until document.getElementsByTagName(tag).length).map { idx -> document.getElementsByTagName(tag).item(idx) as? Element } }
            .filterNotNull()
        val parsed = candidates.mapNotNull { node ->
            fun tag(name: String): String? = node.getElementsByTagName(name).item(0)?.textContent?.replace(Regex("\\s+"), " ")?.trim()?.takeIf { it.isNotBlank() }
            val id = tag("id") ?: tag("n") ?: return@mapNotNull null
            val fir = tag("fir").orEmpty(); val cod = tag("cod").orEmpty(); val traffic = tag("traffic").orEmpty(); val purpose = tag("purpose").orEmpty(); val scope = tag("scope").orEmpty(); val lower = tag("lower").orEmpty(); val upper = tag("upper").orEmpty(); val geo = tag("geo").orEmpty()
            val qLine = "Q) $fir/$cod/$traffic/$purpose/$scope/$lower/$upper/$geo".trim()
            val b = formatNotamDate(tag("b")); val c = tag("c")?.let { if (it == "PERM") "PERM" else formatNotamDate(it) }
            val raw = listOfNotNull(tag("n"), qLine.takeIf { it.length > 3 }, tag("e"), tag("origem")?.let { "ORIGEM: $it" }, if (b != null && c != null) "$b a $c UTC" else null).joinToString("\n")
            Notam(id = id, rawText = raw.ifBlank { id }, validFromEpochMillis = 0L, validToEpochMillis = null)
        }
        Log.d("AiswebAerodrome", "parseAiswebNotamXml DOM nodes=${candidates.size} parsed=${parsed.size}")
        parsed
    }.getOrElse {
        Log.e("AiswebAerodrome", "parseAiswebNotamXml DOM failed: ${it.message}")
        val fallback = Regex("<id>(.*?)</id>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .findAll(xml).map { Notam(id = it.groupValues[1].trim(), rawText = it.groupValues[1].trim(), validFromEpochMillis = 0L, validToEpochMillis = null) }.toList()
        Log.d("AiswebAerodrome", "parseAiswebNotamXml regex fallback parsed=${fallback.size}")
        fallback
    }
}

private fun formatNotamDate(value: String?): String? {
    if (value == null || value.length < 10) return null
    val yy = value.substring(0, 2)
    val mm = value.substring(2, 4)
    val dd = value.substring(4, 6)
    val hh = value.substring(6, 8)
    val min = value.substring(8, 10)
    return "$dd/$mm/$yy $hh:$min"
}
