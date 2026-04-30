package com.betpass.mc01pilot.airport.notam

data class DecodedNotam(
    val rawText: String,
    val notamId: String?,
    val replacesNotam: String?,
    val publishedAt: String?,
    val fir: String?,
    val qCode: String?,
    val traffic: String?,
    val purpose: String?,
    val scope: String?,
    val lowerLimit: String?,
    val upperLimit: String?,
    val coordinates: String?,
    val category: NotamCategory,
    val severity: NotamSeverity,
    val title: String,
    val plainLanguageSummary: String,
    val affectedArea: String?,
    val affectedRunway: String?,
    val affectedFrequency: String?,
    val affectedNavaid: String?,
    val affectedProcedure: String?,
    val startTime: String?,
    val endTime: String?,
    val schedule: String?,
    val tags: List<String>,
    val decodedTerms: List<DecodedTerm>
)

data class DecodedTerm(val original: String, val meaning: String)

enum class NotamCategory { RUNWAY, TAXIWAY, APRON, AIRSPACE, FREQUENCY, NAVAID, LIGHTING, PROCEDURE, OBSTACLE, FUEL, SERVICES, METEOROLOGY, GENERAL, UNKNOWN }

enum class NotamSeverity { CRITICAL, HIGH, MEDIUM, LOW, INFO }
