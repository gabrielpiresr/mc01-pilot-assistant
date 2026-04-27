package com.betpass.mc01pilot.airport.data

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

class AiswebAirportDataProvider : AirportDataProvider {
    override suspend fun searchAirports(query: String): List<Airport> {
        // TODO Integrar com endpoints AISWEB (internos/oficiais quando disponíveis)
        return MockAviationData.searchAirports(query)
    }

    override suspend fun getAirportDetails(icao: String): AirportDetails? {
        // TODO Migrar para dados AISWEB reais quando API/endpoint estiver estável.
        return MockAviationData.detailsByIcao[icao]
    }

    override suspend fun getFrequencies(icao: String): List<Frequency> {
        // TODO Integrar frequências reais do AISWEB/ROTAER.
        return MockAviationData.frequenciesByIcao[icao].orEmpty()
    }
}

class AiswebWeatherDataProvider : WeatherDataProvider {
    override suspend fun getWeather(icao: String): WeatherReport {
        // TODO Integrar METAR/TAF reais via AISWEB/requisição homologada.
        return MockAviationData.weatherByIcao[icao] ?: WeatherReport(metarRaw = null, tafRaw = null)
    }

    override suspend fun decodeMetar(raw: String): DecodedMetar = MockAviationData.decodeMetar(raw)

    override suspend fun decodeTaf(raw: String): DecodedTaf = MockAviationData.decodeTaf(raw)
}

class AiswebNotamDataProvider : NotamDataProvider {
    override suspend fun getNotams(icao: String): List<Notam> {
        // TODO Integrar NOTAM real (AISWEB/internal web endpoint/scraping controlado)
        return MockAviationData.notamsByIcao[icao].orEmpty()
    }

    override suspend fun decodeNotam(notam: Notam): DecodedNotam = MockAviationData.decodeNotam(notam)
}

class AiswebChartDataProvider : ChartDataProvider {
    override suspend fun getAirportCharts(icao: String): List<AirportChart> {
        // TODO Integrar cartas oficiais reais e metadados de atualização.
        return MockAviationData.chartsByIcao[icao].orEmpty()
    }
}
