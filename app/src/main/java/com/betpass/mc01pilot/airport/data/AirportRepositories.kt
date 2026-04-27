package com.betpass.mc01pilot.airport.data

import android.content.Context
import com.betpass.mc01pilot.data.LibraryRepository
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

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
    suspend fun charts(icao: String): List<AirportChart> = provider.getAirportCharts(icao)

    fun saveChartToLibrary(chart: AirportChart, folderId: String? = null) {
        val itemName = "${chart.airportIcao}_${chart.title}"
        if (!libraryRepository.nameExists("chart", folderId, itemName)) {
            libraryRepository.createPlaceholderFile(type = "chart", name = itemName, parentId = folderId)
        }
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
