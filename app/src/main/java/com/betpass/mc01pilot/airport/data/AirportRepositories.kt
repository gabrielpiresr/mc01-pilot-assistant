package com.betpass.mc01pilot.airport.data

import android.content.Context
import android.util.Log
import android.os.Environment
import android.provider.MediaStore
import com.betpass.mc01pilot.data.LibraryRepository
import com.betpass.mc01pilot.airport.notam.DecodedNotam
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class AirportRepository(private val provider: AirportDataProvider) {
    suspend fun search(query: String): List<Airport> = provider.searchAirports(query)
    suspend fun details(icao: String): AirportDetails? = provider.getAirportDetails(icao)
    suspend fun frequencies(icao: String): List<Frequency> = provider.getFrequencies(icao)
}

class WeatherRepository(private val provider: WeatherDataProvider) {
    suspend fun weather(icao: String): WeatherReport = provider.getWeather(icao)
    suspend fun decodeMetar(raw: String): DecodedMetar = provider.decodeMetar(raw)
    suspend fun decodeTaf(raw: String): DecodedTaf = provider.decodeTaf(raw)
}

class NotamRepository(private val provider: NotamDataProvider) {
    suspend fun notams(icao: String): List<Notam> = provider.getNotams(icao)
    suspend fun decode(notam: Notam): DecodedNotam = provider.decodeNotam(notam)
}

class ChartRepository(
    private val context: Context,
    private val provider: ChartDataProvider,
    private val libraryRepository: LibraryRepository = LibraryRepository(context)
) {
    companion object {
        private const val TAG = "ChartRepository"
    }

    suspend fun charts(icao: String): List<AirportChart> = provider.getAirportCharts(icao)

    suspend fun saveChartToLibrary(chart: AirportChart, folderId: String? = null, folderName: String? = null): Boolean {
        return withContext(Dispatchers.IO) {
            val sourceUrl = chart.sourceUrl ?: return@withContext false
            val itemName = "${chart.airportIcao}_${chart.title}".replace(Regex("[^a-zA-Z0-9._-]"), "_")
            if (libraryRepository.nameExists("chart", folderId, itemName)) return@withContext true
            val pdfName = if (itemName.lowercase().endsWith(".pdf")) itemName else "$itemName.pdf"
            val relativePath = buildString {
                append(Environment.DIRECTORY_DOWNLOADS)
                append("/MC01Pilot/Cartas")
                if (!folderName.isNullOrBlank()) append("/").append(folderName.trim())
            }
            val values = android.content.ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, pdfName)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return@withContext false.also {
                Log.e(TAG, "Failed to create MediaStore entry for $pdfName")
            }
            val bytesCopied = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    openHttpStream(sourceUrl).use { input -> input.copyTo(output) }
                } ?: 0L
            }.getOrElse { err ->
                Log.e(TAG, "Download error for $sourceUrl", err)
                -1L
            }
            if (bytesCopied <= 0L) {
                Log.e(TAG, "Downloaded file is empty for $sourceUrl (bytes=$bytesCopied)")
                context.contentResolver.delete(uri, null, null)
                return@withContext false
            }
            libraryRepository.addImported(name = pdfName, parentId = folderId, uri = uri, type = "chart")
            Log.d(TAG, "Saved chart file: name=$pdfName bytes=$bytesCopied folderId=$folderId folderName=$folderName uri=$uri")
            true
        }
    }

    private fun openHttpStream(sourceUrl: String): java.io.InputStream {
        val sanitizedUrl = sourceUrl.trim()
        var lastError: IOException? = null
        repeat(3) { attempt ->
            try {
                val connection = (URL(sanitizedUrl).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    connectTimeout = 35_000
                    readTimeout = 60_000
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Android) MC01Pilot/1.0")
                    setRequestProperty("Accept", "application/pdf,*/*")
                }
                val code = connection.responseCode
                if (code !in 200..299) {
                    val err = runCatching { connection.errorStream?.bufferedReader()?.use { it.readText().take(200) } }.getOrNull()
                    connection.disconnect()
                    throw IOException("HTTP $code while downloading chart. url=$sanitizedUrl body=$err")
                }
                return connection.inputStream
            } catch (ex: IOException) {
                lastError = ex
                Log.w(TAG, "Attempt ${attempt + 1}/3 failed for $sanitizedUrl", ex)
                if (attempt < 2) Thread.sleep((1000L * (attempt + 1)))
            }
        }
        throw lastError ?: IOException("Unknown download error for $sanitizedUrl")
    }
}

class OfflineAirportRepository(context: Context) {
    private val gson = Gson()
    private val file = File(context.filesDir, "offline_airport_briefings.json")
    private val listType = object : TypeToken<List<OfflineAirportBriefing>>() {}.type

    fun saveBriefing(briefing: OfflineAirportBriefing) {
        val all = getAll().toMutableList()
        val index = all.indexOfFirst { it.icao == briefing.icao }
        if (index >= 0) all[index] = briefing else all.add(briefing)
        file.writeText(gson.toJson(all))
    }

    fun getBriefing(icao: String): OfflineAirportBriefing? = getAll().firstOrNull { it.icao == icao }

    private fun getAll(): List<OfflineAirportBriefing> {
        if (!file.exists()) return emptyList()
        return gson.fromJson<List<OfflineAirportBriefing>>(file.readText(), listType).orEmpty()
    }

    fun statusText(icao: String): String {
        val existing = getBriefing(icao) ?: return "Não disponível offline"
        return "Disponível offline · Atualizado em: ${java.util.Date(existing.updatedAtEpochMillis)}"
    }

    fun nowEpochMillis() = System.currentTimeMillis()
}
