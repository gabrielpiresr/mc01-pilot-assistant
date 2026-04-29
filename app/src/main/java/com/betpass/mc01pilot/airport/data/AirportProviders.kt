package com.betpass.mc01pilot.airport.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import android.util.Log
import kotlin.math.roundToInt
import java.text.Normalizer

interface AirportDataProvider {
    suspend fun searchAirports(query: String): List<Airport>
    suspend fun getAirportDetails(icao: String): AirportDetails?
    suspend fun getFrequencies(icao: String): List<Frequency>
}

interface WeatherDataProvider {
    suspend fun getWeather(icao: String): WeatherReport
    suspend fun decodeMetar(raw: String): DecodedMetar
    suspend fun decodeTaf(raw: String): DecodedTaf
}

interface NotamDataProvider {
    suspend fun getNotams(icao: String): List<Notam>
    suspend fun decodeNotam(notam: Notam): DecodedNotam
}

interface ChartDataProvider {
    suspend fun getAirportCharts(icao: String): List<AirportChart>
}

private data class AerodromosPayload(val aerodromos: List<AerodromoJson>)
private data class AerodromoJson(
    val codigo_oaci: String?,
    val nome: String?,
    val municipio: String?,
    val uf: String?,
    val latitude: Double?,
    val longitude: Double?,
    val altitude_m: Double?,
    val operacao: String?,
    val pistas: List<PistaJson>?
)
private data class PistaJson(
    val designacao: String?,
    val comprimento_m: Double?,
    val largura_m: Double?,
    val superficie: String?
)

private class BrazilAirportCatalog(private val context: Context) {
    private data class IndexedAirport(val airport: Airport, val normIcao: String, val normName: String, val normCity: String)

    private val airports: List<AerodromoJson> by lazy {
        val json = loadJson(context)
        runCatching { Gson().fromJson(json, AerodromosPayload::class.java) }
            .getOrNull()?.aerodromos.orEmpty()
            .filter { !it.codigo_oaci.isNullOrBlank() && it.latitude != null && it.longitude != null }
    }

    private fun loadJson(context: Context): String {
        val candidates = listOf(
            "airport/data/aerodromos_brasil_publicos_privados.json",
            "aerodromos_brasil_publicos_privados.json"
        )
        for (path in candidates) {
            runCatching { return context.assets.open(path).bufferedReader().use { it.readText() } }
        }
        this::class.java.classLoader
            ?.getResourceAsStream("com/betpass/mc01pilot/airport/data/aerodromos_brasil_publicos_privados.json")
            ?.bufferedReader()?.use { return it.readText() }

        Log.e("BrazilAirportCatalog", "Arquivo aerodromos_brasil_publicos_privados.json não encontrado em assets ou classpath")
        return "{\"aerodromos\":[]}"
    }

    private val indexedAirports: List<IndexedAirport> by lazy {
        airports.mapNotNull { row ->
            row.toAirport()?.let { airport ->
                IndexedAirport(
                    airport = airport,
                    normIcao = normalizeForSearch(airport.icao),
                    normName = normalizeForSearch(airport.name),
                    normCity = normalizeForSearch(airport.city)
                )
            }
        }.sortedBy { it.airport.icao }
    }

    fun search(query: String): List<Airport> {
        val q = normalizeForSearch(query)
        return indexedAirports
            .asSequence()
            .filter { q.isBlank() || it.normIcao.contains(q) || it.normName.contains(q) || it.normCity.contains(q) }
            .map { it.airport }
            .take(200)
            .toList()
    }

    fun details(icao: String): AirportDetails? {
        val row = airports.firstOrNull { it.codigo_oaci.equals(icao, ignoreCase = true) } ?: return null
        val airport = row.toAirport() ?: return null
        val runways = row.pistas.orEmpty().mapNotNull { pista ->
            val length = pista.comprimento_m?.roundToInt() ?: return@mapNotNull null
            val width = pista.largura_m?.roundToInt() ?: 0
            Runway(
                designation = pista.designacao ?: "N/D",
                lengthMeters = length,
                widthMeters = width,
                surface = pista.superficie ?: "N/D",
                lighting = null
            )
        }
        return AirportDetails(
            airport = airport,
            coordinatesText = "${airport.latitude}, ${airport.longitude}",
            elevationFt = row.altitude_m?.times(3.28084)?.roundToInt(),
            runways = runways,
            operatingHours = row.operacao ?: "Consultar AISWEB",
            restrictions = listOf("Dados operacionais detalhados sob demanda no AISWEB."),
            services = emptyList(),
            fuelAvailability = "Consultar AISWEB/ROTAER",
            rmk = emptyList()
        )
    }

    private fun normalizeForSearch(value: String): String =
        Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            .lowercase()

    private fun AerodromoJson.toAirport(): Airport? {
        val icaoValue = codigo_oaci ?: return null
        val latitudeValue = latitude ?: return null
        val longitudeValue = longitude ?: return null
        return Airport(
            icao = icaoValue,
            name = nome ?: "Sem nome",
            city = municipio ?: "",
            uf = uf ?: "",
            latitude = latitudeValue,
            longitude = longitudeValue,
            runwaySummary = pistas.orEmpty().firstOrNull()?.let { "${it.designacao ?: "N/D"} ${it.comprimento_m?.roundToInt() ?: 0}m" }
        )
    }
}

private suspend fun fetchAiswebText(url: String): String = withContext(Dispatchers.IO) {
    val connection = URL(url).openConnection() as HttpURLConnection
    connection.requestMethod = "GET"
    connection.connectTimeout = 15_000
    connection.readTimeout = 20_000
    connection.setRequestProperty("User-Agent", "MC01PilotAssistant/1.0")
    connection.setRequestProperty("Accept", "text/html,application/xhtml+xml")
    connection.setRequestProperty("Accept-Language", "pt-BR,pt;q=0.9,en;q=0.8")
    connection.setRequestProperty("Referer", "https://aisweb.decea.mil.br/")
    connection.inputStream.bufferedReader().use { it.readText() }
}

internal data class AiswebParsedData(
    val frequencies: List<Frequency>,
    val observationText: String?
)

internal object AiswebHtmlParser {
    private val frequencyRegex = Regex("(?i)\\b(TWR|GND|ATIS|APP|AFIS|R[ÁA]DIO|RADIO)\\b[^0-9]{0,40}(1\\d{2}[\\.,]\\d{1,3})")

    fun parse(html: String): AiswebParsedData {
        val decoded = decodePossibleJsonString(html)
        val plain = decoded
            .replace(Regex("<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("<style[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ")
            .replace("&#160;", " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        val frequencies = frequencyRegex.findAll(plain).map { match ->
            val service = match.groupValues[1].uppercase().replace("Á", "A")
            val normalizedValue = match.groupValues[2].replace(',', '.')
            Frequency(type = service, value = normalizedValue, remarks = "AISWEB")
        }.toList().distinctBy { "${it.type}-${it.value}" }

        return AiswebParsedData(
            frequencies = frequencies,
            observationText = plain.takeIf { it.isNotBlank() }?.take(260)
        )
    }

    private fun decodePossibleJsonString(raw: String): String {
        val trimmed = raw.trim()
        if (!(trimmed.startsWith("\"") && trimmed.endsWith("\""))) return raw
        return runCatching { JsonParser.parseString(trimmed).asString }.getOrDefault(raw)
    }
}

class AiswebAirportDataProvider(context: Context) : AirportDataProvider {
    companion object {
        private const val TAG = "AiswebAerodrome"
    }

    private val catalog = BrazilAirportCatalog(context)
    override suspend fun searchAirports(query: String): List<Airport> = withContext(Dispatchers.Default) { catalog.search(query) }

    override suspend fun getAirportDetails(icao: String): AirportDetails? = withContext(Dispatchers.IO) {
        val local = catalog.details(icao)
        Log.d(TAG, "getAirportDetails start for ${icao.uppercase()}")
        val html = runCatching { AiswebAerodromeService.fetchAiswebAerodromeHtml(icao) }
            .onFailure { Log.e(TAG, "getAirportDetails fetch failed for ${icao.uppercase()}: ${it.message}", it) }
            .getOrNull()
        if (html == null) {
            Log.w(TAG, "getAirportDetails fallback to local only for ${icao.uppercase()} (html null)")
            return@withContext local
        }

        val parsed = AiswebAerodromeService.parseAiswebAerodromeHtml(html, icao)
        Log.d(TAG, "getAirportDetails parsed for ${icao.uppercase()} freq=${parsed.frequencies.size} metar=${!parsed.metar.isNullOrBlank()} taf=${!parsed.taf.isNullOrBlank()}")
        local?.copy(
            restrictions = local.restrictions + listOf("Fonte complementar: AISWEB"),
            rmk = buildList {
                addAll(local.rmk)
                parsed.rmk
                    .flatMap { section -> section.items.map { item -> "[${section.section}] $item" } }
                    .forEach { add(RmkEntry(it, RmkCategory.OBSERVATION)) }
            }
        )
    }

    override suspend fun getFrequencies(icao: String): List<Frequency> = withContext(Dispatchers.IO) {
        Log.d(TAG, "getFrequencies start for ${icao.uppercase()}")
        val html = runCatching { AiswebAerodromeService.fetchAiswebAerodromeHtml(icao) }
            .onFailure { Log.e(TAG, "getFrequencies fetch failed for ${icao.uppercase()}: ${it.message}", it) }
            .getOrNull() ?: return@withContext emptyList()
        val parsed = AiswebAerodromeService.parseAiswebAerodromeHtml(html, icao)
        parsed.frequencies.map {
            Frequency(type = it.service.ifBlank { "COM" }, value = it.frequency, remarks = it.schedule ?: "AISWEB")
        }.also {
            Log.d(TAG, "getFrequencies done for ${icao.uppercase()} count=${it.size}")
        }
    }
}

class AiswebWeatherDataProvider : WeatherDataProvider {
    override suspend fun getWeather(icao: String): WeatherReport = WeatherReport(metarRaw = null, tafRaw = null)
    override suspend fun decodeMetar(raw: String): DecodedMetar = DecodedMetar("Decodificação não disponível", "", "", "", "", "", "", "")
    override suspend fun decodeTaf(raw: String): DecodedTaf = DecodedTaf("Decodificação não disponível", "", "", "", "", "")
}

class AiswebNotamDataProvider : NotamDataProvider {
    override suspend fun getNotams(icao: String): List<Notam> = emptyList()
    override suspend fun decodeNotam(notam: Notam): DecodedNotam = DecodedNotam(notam.id, "Sem mock", "Ver texto oficial", NotamSeverity.INFORMATIONAL, emptyList())
}

class AiswebChartDataProvider : ChartDataProvider {
    override suspend fun getAirportCharts(icao: String): List<AirportChart> = withContext(Dispatchers.IO) {
        val pageUrl = "https://aisweb.decea.mil.br/?i=aerodromos&codigo=${icao.uppercase()}"
        val html = runCatching { fetchAiswebText(pageUrl) }.getOrElse { return@withContext emptyList() }
        val linkRegex = Regex("https?://[^\\\"'\\s>]+\\.pdf")
        linkRegex.findAll(html).mapIndexed { index, match ->
            val url = match.value
            val title = url.substringAfterLast('/').substringBefore(".pdf").uppercase()
            AirportChart(
                id = "${icao.uppercase()}-$index",
                airportIcao = icao.uppercase(),
                title = title,
                category = "Carta",
                previewText = "Preview: $title",
                sourceUrl = url
            )
        }.toList().distinctBy { it.sourceUrl }
    }
}
