package com.betpass.mc01pilot.airport.data

import android.content.Context
import com.google.gson.Gson
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
    connection.inputStream.bufferedReader().use { it.readText() }
}

class AiswebAirportDataProvider(context: Context) : AirportDataProvider {
    private val catalog = BrazilAirportCatalog(context)
    override suspend fun searchAirports(query: String): List<Airport> = withContext(Dispatchers.Default) { catalog.search(query) }

    override suspend fun getAirportDetails(icao: String): AirportDetails? = withContext(Dispatchers.IO) {
        val local = catalog.details(icao)
        val pageUrl = "https://aisweb.decea.mil.br/?i=aerodromos&codigo=${icao.uppercase()}"
        val html = runCatching { fetchAiswebText(pageUrl) }.getOrNull()
        if (html == null) return@withContext local

        val text = html.replace(Regex("<[^>]+>"), " ").replace("&nbsp;", " ").replace(Regex("\\s+"), " ").trim()
        local?.copy(
            restrictions = local.restrictions + listOf("Fonte complementar: AISWEB"),
            rmk = local.rmk + listOf(RmkEntry(text.take(260), RmkCategory.OBSERVATION))
        )
    }

    override suspend fun getFrequencies(icao: String): List<Frequency> = withContext(Dispatchers.IO) {
        val pageUrl = "https://aisweb.decea.mil.br/?i=aerodromos&codigo=${icao.uppercase()}"
        val html = runCatching { fetchAiswebText(pageUrl) }.getOrNull() ?: return@withContext emptyList()
        val plain = html.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ")
        val regex = Regex("(?i)(TWR|GND|ATIS|APP|AFIS|R[ÁA]DIO|RADIO)[^0-9]{0,30}(\\d{3}\\.\\d{1,3})")
        regex.findAll(plain).map { match ->
            val service = match.groupValues[1].uppercase()
            val freq = match.groupValues[2]
            Frequency(type = service, value = freq, remarks = "AISWEB")
        }.toList().distinctBy { "${it.type}-${it.value}" }
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
