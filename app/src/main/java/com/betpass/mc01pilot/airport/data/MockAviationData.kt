package com.betpass.mc01pilot.airport.data

object MockAviationData {
    val airports = listOf(
        Airport("SBJD", "Aeroporto de Jundiaí", "Jundiaí", "SP", -23.1816, -46.9448, "17/35 1400x30m ASP"),
        Airport("SBSP", "Aeroporto de Congonhas", "São Paulo", "SP", -23.6261, -46.6566, "17R/35L 1940m"),
        Airport("SBMT", "Campo de Marte", "São Paulo", "SP", -23.5092, -46.6378, "12/30 1600m"),
        Airport("SBBH", "Pampulha", "Belo Horizonte", "MG", -19.8510, -43.9506, "13/31 2500m"),
        Airport("SBPR", "Carlos Prates", "Belo Horizonte", "MG", -19.9093, -43.9910, "09/27 1210m"),
        Airport("SBRJ", "Santos Dumont", "Rio de Janeiro", "RJ", -22.9114, -43.1641, "02R/20L 1323m"),
        Airport("SBGR", "Guarulhos", "Guarulhos", "SP", -23.4356, -46.4731, "10L/28R 3000m"),
        Airport("SBKP", "Viracopos", "Campinas", "SP", -23.0074, -47.1345, "15/33 3240m")
    )

    val detailsByIcao = airports.associate { airport ->
        airport.icao to AirportDetails(
            airport = airport,
            coordinatesText = "${airport.latitude}, ${airport.longitude}",
            elevationFt = if (airport.icao == "SBSP") 2631 else 2300,
            runways = listOf(Runway("17/35", 1400, 30, "Asfalto", "Luzes borda")),
            operatingHours = "HJ e conforme AIP",
            restrictions = listOf("Atenção: verificar NOTAMs ativos antes da decolagem"),
            services = listOf("Pátio", "Combustível", "Serviço de informação"),
            fuelAvailability = "AVGAS/JET A1 sob coordenação local",
            rmk = listOf(
                RmkEntry("Procedimento local sujeito a coordenação prévia.", RmkCategory.LOCAL_PROCEDURE),
                RmkEntry("Atenção a operações em pista molhada.", RmkCategory.WARNING)
            )
        )
    }

    val frequenciesByIcao = airports.associate { airport ->
        airport.icao to listOf(
            Frequency("TWR", "118.100", "Principal"),
            Frequency("GND", "121.900"),
            Frequency("ATIS", "127.650")
        )
    }

    val weatherByIcao = airports.associate { airport ->
        airport.icao to WeatherReport(
            metarRaw = "${airport.icao} 271200Z 14008KT 9999 FEW020 24/16 Q1016",
            tafRaw = "TAF ${airport.icao} 271100Z 2712/2812 15010KT 9999 SCT025 TX29/2718Z TN18/2809Z"
        )
    }

    val notamsByIcao = airports.associate { airport ->
        airport.icao to listOf(
            Notam(
                id = "N1234/26",
                rawText = "RWY 17/35 CLSD DLY 0200-0400Z DUE MAINT",
                validFromEpochMillis = System.currentTimeMillis() - 86_400_000,
                validToEpochMillis = System.currentTimeMillis() + 7 * 86_400_000
            ),
            Notam(
                id = "N2234/26",
                rawText = "ATIS U/S, INFO VIA TWR",
                validFromEpochMillis = System.currentTimeMillis() - 10_000,
                validToEpochMillis = null
            )
        )
    }

    val chartsByIcao = airports.associate { airport ->
        airport.icao to listOf(
            AirportChart("${airport.icao}-adc", airport.icao, "ADC", "Aproximação", "Carta de aproximação mock", null),
            AirportChart("${airport.icao}-parking", airport.icao, "Pátio", "Solo", "Carta de pátio mock", null)
        )
    }

    fun searchAirports(query: String): List<Airport> {
        if (query.isBlank()) return airports
        val q = query.trim().lowercase()
        return airports.filter {
            it.icao.lowercase().contains(q) ||
                it.name.lowercase().contains(q) ||
                it.city.lowercase().contains(q)
        }
    }

    fun decodeNotam(notam: Notam): DecodedNotam {
        val raw = notam.rawText.lowercase()
        return when {
            "clsd" in raw || "closed" in raw -> DecodedNotam(
                notamId = notam.id,
                simplifiedPtBr = "Pista fechada em período específico.",
                probableImpact = "Impacto provável crítico na operação de pouso/decolagem.",
                severity = NotamSeverity.CRITICAL,
                tags = listOf("runway closed")
            )
            "atis" in raw -> DecodedNotam(
                notamId = notam.id,
                simplifiedPtBr = "Serviço ATIS indisponível.",
                probableImpact = "Atenção: verificar informações diretamente com TWR.",
                severity = NotamSeverity.ATTENTION,
                tags = listOf("communication/navigation limitation")
            )
            else -> DecodedNotam(
                notamId = notam.id,
                simplifiedPtBr = "NOTAM informativo. Verificar texto bruto.",
                probableImpact = "Impacto provável informacional.",
                severity = NotamSeverity.INFORMATIONAL,
                tags = emptyList()
            )
        }
    }

    fun decodeMetar(raw: String): DecodedMetar = DecodedMetar(
        summary = "Condições VMC aparentes. Verificar fonte oficial.",
        wind = "140° / 8 kt",
        visibility = ">= 10 km",
        clouds = "Poucas nuvens a 2000 ft",
        temperatureDewPoint = "24°C / 16°C",
        qnh = "1016 hPa",
        phenomena = "Sem fenômeno significativo",
        trend = "Sem tendência crítica no trecho"
    )

    fun decodeTaf(raw: String): DecodedTaf = DecodedTaf(
        summary = "Previsão estável no período",
        wind = "150° / 10 kt",
        visibility = ">= 10 km",
        clouds = "Nuvens esparsas em 2500 ft",
        phenomena = "Sem fenômeno significativo",
        trend = "Verificar atualizações ao longo do briefing"
    )
}
