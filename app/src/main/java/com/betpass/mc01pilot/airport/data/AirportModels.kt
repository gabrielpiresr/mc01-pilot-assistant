package com.betpass.mc01pilot.airport.data

import com.betpass.mc01pilot.airport.notam.DecodedNotam

data class Airport(
    val icao: String,
    val name: String,
    val city: String,
    val uf: String,
    val latitude: Double,
    val longitude: Double,
    val runwaySummary: String? = null
)

data class AirportDetails(
    val airport: Airport,
    val coordinatesText: String,
    val elevationFt: Int?,
    val runways: List<Runway>,
    val operatingHours: String,
    val restrictions: List<String>,
    val services: List<String>,
    val fuelAvailability: String,
    val rmk: List<RmkEntry>
)

data class Runway(
    val designation: String,
    val lengthMeters: Int,
    val widthMeters: Int,
    val surface: String,
    val lighting: String? = null
)

data class Frequency(
    val type: String,
    val value: String,
    val remarks: String? = null
)

data class Notam(
    val id: String,
    val rawText: String,
    val validFromEpochMillis: Long,
    val validToEpochMillis: Long?
)

data class WeatherReport(
    val metarRaw: String?,
    val tafRaw: String?
)

data class DecodedMetar(
    val summary: String,
    val wind: String,
    val visibility: String,
    val clouds: String,
    val temperatureDewPoint: String,
    val qnh: String,
    val phenomena: String,
    val trend: String
)

data class DecodedTaf(
    val summary: String,
    val wind: String,
    val visibility: String,
    val clouds: String,
    val phenomena: String,
    val trend: String
)

data class AirportChart(
    val id: String,
    val airportIcao: String,
    val title: String,
    val category: String,
    val previewText: String,
    val sourceUrl: String? = null
)

data class RmkEntry(
    val text: String,
    val category: RmkCategory
)

enum class RmkCategory { OPERATIONAL_RESTRICTION, LOCAL_PROCEDURE, WARNING, OBSERVATION }

data class OfflineAirportBriefing(
    val icao: String,
    val details: AirportDetails,
    val frequencies: List<Frequency>,
    val weatherReport: WeatherReport,
    val decodedMetar: DecodedMetar?,
    val decodedTaf: DecodedTaf?,
    val notams: List<Notam>,
    val decodedNotams: List<DecodedNotam>,
    val charts: List<AirportChart>,
    val updatedAtEpochMillis: Long
)
