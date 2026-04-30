package com.betpass.mc01pilot.airport.notam

import com.betpass.mc01pilot.airport.data.Notam

class NotamDecoder {
    private val notamIdRegex = Regex("\\b([A-Z]\\d{4}/\\d{2})\\b")
    private val replacesRegex = Regex("\\bR\\s+([A-Z]\\d{4}/\\d{2})\\b")
    private val dateRegex = Regex("\\b\\d{2}/\\d{2}/\\d{4}\\s+\\d{2}:\\d{2}\\b")
    private val qLineRegex = Regex("Q\\)\\s*([^\\n]+)")
    private val frequencyRegex = Regex("\\b(1[1-3]\\d\\.\\d{2,3})\\s*MHZ\\b", RegexOption.IGNORE_CASE)
    private val runwayRegex = Regex("\\bRWY\\s*(\\d{2}[LRC]?)\\b", RegexOption.IGNORE_CASE)

    fun decode(notam: Notam): DecodedNotam = decodeRaw(notam.rawText, fallbackId = notam.id)

    fun decodeRaw(raw: String, fallbackId: String? = null): DecodedNotam {
        val normalized = raw.replace(Regex("\\s+"), " ").trim()
        val qLine = qLineRegex.find(raw)?.groupValues?.get(1)
        val qParts = qLine?.split("/")?.map { it.trim() } ?: emptyList()
        val frequency = frequencyRegex.find(normalized)?.groupValues?.get(1)
        val runway = runwayRegex.find(normalized)?.groupValues?.get(1)
        val category = classifyCategory(normalized)
        val severity = classifySeverity(normalized, category)
        val decodedTerms = NotamRules.abbreviations.keys.mapNotNull { abbr ->
            Regex("(?<![A-Z0-9/])${Regex.escape(abbr)}(?![A-Z0-9/])", RegexOption.IGNORE_CASE)
                .find(normalized)?.let { DecodedTerm(it.value.uppercase(), NotamRules.abbreviations[abbr].orEmpty()) }
        }
        return DecodedNotam(
            rawText = raw,
            notamId = notamIdRegex.find(raw)?.groupValues?.get(1) ?: fallbackId,
            replacesNotam = replacesRegex.find(raw)?.groupValues?.get(1),
            publishedAt = dateRegex.find(raw)?.value,
            fir = qParts.getOrNull(0),
            qCode = qParts.getOrNull(1),
            traffic = qParts.getOrNull(2),
            purpose = qParts.getOrNull(3),
            scope = qParts.getOrNull(4),
            lowerLimit = qParts.getOrNull(5),
            upperLimit = qParts.getOrNull(6),
            coordinates = qParts.getOrNull(7),
            category = category,
            severity = severity,
            title = buildTitle(category, runway, frequency),
            plainLanguageSummary = buildSummary(normalized, runway, frequency),
            affectedArea = Regex("\\b(CTR|TMA|ATZ|FIR|REH)\\b.*", RegexOption.IGNORE_CASE).find(normalized)?.value,
            affectedRunway = runway?.let { "RWY $it" },
            affectedFrequency = frequency?.let { "$it MHz" },
            affectedNavaid = Regex("\\b(VOR|NDB|DME|ILS|LOC|GP|PAPI|VASIS)\\b", RegexOption.IGNORE_CASE).find(normalized)?.value,
            affectedProcedure = Regex("\\b(SID|STAR|IAC|APCH|PROCEDIMENTO)\\b", RegexOption.IGNORE_CASE).find(normalized)?.value,
            startTime = null,
            endTime = null,
            schedule = Regex("\\b(DLY|HJ|UTC|SR/SS)\\b", RegexOption.IGNORE_CASE).find(normalized)?.value,
            tags = listOfNotNull(category.name, severity.name, frequency?.let { "freq" }, runway?.let { "runway" }),
            decodedTerms = decodedTerms
        )
    }

    private fun classifyCategory(text: String): NotamCategory = when {
        Regex("\\b(RWY|PISTA|LDG|TKOF|THR|FST|DISTANCIA DECLARADA)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) -> NotamCategory.RUNWAY
        Regex("\\b(TWY|TAXIWAY|TAXI|TÁXI)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) -> NotamCategory.TAXIWAY
        Regex("\\b(APN|PATIO|PÁTIO|ESTACIONAMENTO)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) -> NotamCategory.APRON
        Regex("\\b(CTR|TMA|ATZ|FIR|REH|CIRCULACAO VFR|ESPAÇO AÉREO|RESTRITO)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) -> NotamCategory.AIRSPACE
        Regex("\\b(FREQ|MHZ|TWR|TORRE|GND|APP|RADIO|RÁDIO)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) -> NotamCategory.FREQUENCY
        else -> NotamCategory.GENERAL
    }

    private fun classifySeverity(text: String, category: NotamCategory): NotamSeverity = when {
        Regex("\\b(RWY\\s*\\d{2}[LRC]?\\s*CLSD|AD\\s*CLSD|PROIBID[AO])\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) -> NotamSeverity.CRITICAL
        Regex("\\b(COMPULSORIO|OBRIGATORIO|PPR|RESTRI)\\w*", RegexOption.IGNORE_CASE).containsMatchIn(text) -> NotamSeverity.HIGH
        category == NotamCategory.AIRSPACE || Regex("\\b(ALTERACAO|ALTERACOES|O/R|PARCIAL)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) -> NotamSeverity.MEDIUM
        Regex("\\b(INFO|INFORMATIVO|RMK)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) -> NotamSeverity.INFO
        else -> NotamSeverity.LOW
    }

    private fun buildTitle(category: NotamCategory, runway: String?, frequency: String?): String = when (category) {
        NotamCategory.RUNWAY -> if (runway != null) "Pista $runway com restrição" else "Restrição de pista"
        NotamCategory.AIRSPACE -> "Alteração em espaço aéreo"
        NotamCategory.FREQUENCY -> if (frequency != null) "Contato em $frequency MHz" else "Alteração de frequência"
        NotamCategory.SERVICES -> "Serviço com alteração"
        else -> "Atualização operacional"
    }

    private fun buildSummary(text: String, runway: String?, frequency: String?): String {
        return when {
            text.contains("CIRCULACAO VFR", true) && frequency != null -> "Há alteração na circulação VFR. O contato na frequência $frequency MHz é obrigatório nas posições publicadas."
            text.contains("CLSD", true) && runway != null -> "A pista $runway possui trecho fechado. Verifique impacto em pouso/decolagem antes de operar."
            else -> "Atenção: há atualização operacional neste NOTAM. Verifique o texto original antes da operação."
        }
    }
}
