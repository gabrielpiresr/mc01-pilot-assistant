package com.betpass.mc01pilot.airport.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.betpass.mc01pilot.airport.data.Airport
import com.betpass.mc01pilot.airport.data.AirportChart
import com.betpass.mc01pilot.airport.data.AirportDetails
import com.betpass.mc01pilot.airport.data.AirportRepository
import com.betpass.mc01pilot.airport.data.AiswebAirportDataProvider
import com.betpass.mc01pilot.airport.data.AiswebChartDataProvider
import com.betpass.mc01pilot.airport.data.AiswebNotamDataProvider
import com.betpass.mc01pilot.airport.data.AiswebWeatherDataProvider
import com.betpass.mc01pilot.airport.data.ChartRepository
import com.betpass.mc01pilot.airport.data.DecodedMetar
import com.betpass.mc01pilot.airport.data.DecodedNotam
import com.betpass.mc01pilot.airport.data.DecodedTaf
import com.betpass.mc01pilot.airport.data.Frequency
import com.betpass.mc01pilot.airport.data.Notam
import com.betpass.mc01pilot.airport.data.NotamRepository
import com.betpass.mc01pilot.airport.data.NotamSeverity
import com.betpass.mc01pilot.airport.data.OfflineAirportBriefing
import com.betpass.mc01pilot.airport.data.OfflineAirportRepository
import com.betpass.mc01pilot.airport.data.RmkCategory
import com.betpass.mc01pilot.airport.data.WeatherReport
import com.betpass.mc01pilot.airport.data.WeatherRepository
import com.betpass.mc01pilot.airport.location.LocationClient
import com.betpass.mc01pilot.airport.location.distanceKm
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AirportDetailsModule(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val airportRepository = remember { AirportRepository(AiswebAirportDataProvider(context)) }
    val weatherRepository = remember { WeatherRepository(AiswebWeatherDataProvider()) }
    val notamRepository = remember { NotamRepository(AiswebNotamDataProvider()) }
    val chartRepository = remember { ChartRepository(context, AiswebChartDataProvider()) }
    val offlineRepository = remember { OfflineAirportRepository(context) }
    val locationClient = remember { LocationClient(context) }

    var query by remember { mutableStateOf("") }
    var airports by remember { mutableStateOf<List<Airport>>(emptyList()) }
    val recentAirports = remember { mutableStateListOf<String>() }
    var selectedIcao by remember { mutableStateOf<String?>(null) }
    var detail by remember { mutableStateOf<AirportDetails?>(null) }
    var frequencies by remember { mutableStateOf<List<Frequency>>(emptyList()) }
    var weather by remember { mutableStateOf<WeatherReport?>(null) }
    var decodedMetar by remember { mutableStateOf<DecodedMetar?>(null) }
    var decodedTaf by remember { mutableStateOf<DecodedTaf?>(null) }
    var notams by remember { mutableStateOf<List<Notam>>(emptyList()) }
    var decodedNotams by remember { mutableStateOf<List<DecodedNotam>>(emptyList()) }
    var charts by remember { mutableStateOf<List<AirportChart>>(emptyList()) }
    var nearby by remember { mutableStateOf<List<Pair<Airport, Double>>>(emptyList()) }
    var nearbyMessage by remember { mutableStateOf("") }
    var offlineStatus by remember { mutableStateOf("Não disponível offline") }
    var isLoadingAisweb by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun loadAirport(icao: String) {
        isLoadingAisweb = true
        selectedIcao = icao
        if (icao !in recentAirports) {
            recentAirports.add(0, icao)
            if (recentAirports.size > 6) recentAirports.removeLast()
        }
        detail = airportRepository.details(icao)
        frequencies = airportRepository.frequencies(icao)
        weather = weatherRepository.weather(icao)
        decodedMetar = weather?.metarRaw?.let { weatherRepository.decodeMetar(it) }
        decodedTaf = weather?.tafRaw?.let { weatherRepository.decodeTaf(it) }
        notams = notamRepository.notams(icao)
        decodedNotams = notams.map { notamRepository.decode(it) }
        charts = chartRepository.charts(icao)
        offlineStatus = offlineRepository.statusText(icao)
        isLoadingAisweb = false
    }

    LaunchedEffect(query) {
        airports = airportRepository.search(query)
    }

    val requestLocationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        scope.launch {
            val current = locationClient.getLastKnownLocation()
            if (current == null) {
                nearbyMessage = "Localização indisponível no momento. Você ainda pode buscar por ICAO/cidade."
                nearby = emptyList()
            } else {
                nearby = airportRepository.search("").map { airport ->
                    airport to distanceKm(current.latitude, current.longitude, airport.latitude, airport.longitude)
                }.sortedBy { it.second }
                nearbyMessage = "Aeroportos próximos da posição atual"
            }
        }
    }

    val isTablet = LocalConfiguration.current.screenWidthDp >= 900
    if (isTablet) {
        Row(modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AirportMasterPane(
                query = query,
                onQueryChange = { query = it },
                airports = airports,
                recentAirports = recentAirports,
                nearby = nearby,
                nearbyMessage = nearbyMessage,
                onRequestNearby = {
                    if (locationClient.hasLocationPermission()) {
                        scope.launch {
                            val current = locationClient.getLastKnownLocation()
                            if (current == null) nearbyMessage = "Não foi possível obter localização."
                            else {
                                nearby = airportRepository.search("").map { ap ->
                                    ap to distanceKm(current.latitude, current.longitude, ap.latitude, ap.longitude)
                                }.sortedBy { it.second }
                                nearbyMessage = "Aeroportos próximos da posição atual"
                            }
                        }
                    } else {
                        requestLocationPermission.launch(
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                        )
                    }
                },
                onSelectAirport = { icao -> scope.launch { loadAirport(icao) } },
                modifier = Modifier.weight(0.38f).fillMaxHeight()
            )
            AirportDetailPane(
                detail = detail,
                frequencies = frequencies,
                weather = weather,
                decodedMetar = decodedMetar,
                decodedTaf = decodedTaf,
                notams = notams,
                decodedNotams = decodedNotams,
                charts = charts,
                offlineStatus = offlineStatus,
                isLoadingAisweb = isLoadingAisweb,
                onSaveOffline = {
                    val selected = selectedIcao ?: return@AirportDetailPane
                    val detailsCurrent = detail ?: return@AirportDetailPane
                    offlineRepository.saveBriefing(
                        OfflineAirportBriefing(
                            icao = selected,
                            details = detailsCurrent,
                            frequencies = airportRepository.frequencies(selected),
                            weatherReport = weather ?: WeatherReport(null, null),
                            decodedMetar = decodedMetar,
                            decodedTaf = decodedTaf,
                            notams = notams,
                            decodedNotams = decodedNotams,
                            charts = charts,
                            updatedAtEpochMillis = offlineRepository.nowEpochMillis()
                        )
                    )
                    offlineStatus = offlineRepository.statusText(selected)
                },
                onSaveChart = { chart -> chartRepository.saveChartToLibrary(chart) },
                modifier = Modifier.weight(0.62f).fillMaxHeight()
            )
        }
    } else {
        var showDetail by remember { mutableStateOf(false) }
        if (!showDetail) {
            AirportMasterPane(
                query = query,
                onQueryChange = { query = it },
                airports = airports,
                recentAirports = recentAirports,
                nearby = nearby,
                nearbyMessage = nearbyMessage,
                onRequestNearby = {
                    if (locationClient.hasLocationPermission()) {
                        scope.launch {
                            val current = locationClient.getLastKnownLocation()
                            nearby = if (current == null) emptyList() else airportRepository.search("").map { ap ->
                                ap to distanceKm(current.latitude, current.longitude, ap.latitude, ap.longitude)
                            }.sortedBy { it.second }
                            nearbyMessage = if (current == null) "Não foi possível obter localização." else "Aeroportos próximos"
                        }
                    } else {
                        requestLocationPermission.launch(
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                        )
                    }
                },
                onSelectAirport = { icao ->
                    scope.launch {
                        loadAirport(icao)
                        showDetail = true
                    }
                },
                modifier = modifier
            )
        } else {
            Column(modifier.fillMaxSize()) {
                TextButton(onClick = { showDetail = false }) { Text("← Voltar para busca") }
                AirportDetailPane(
                    detail = detail,
                    frequencies = frequencies,
                    weather = weather,
                    decodedMetar = decodedMetar,
                    decodedTaf = decodedTaf,
                    notams = notams,
                    decodedNotams = decodedNotams,
                    charts = charts,
                    offlineStatus = offlineStatus,
                    isLoadingAisweb = isLoadingAisweb,
                    onSaveOffline = {
                        val selected = selectedIcao ?: return@AirportDetailPane
                        val detailsCurrent = detail ?: return@AirportDetailPane
                        scope.launch {
                            offlineRepository.saveBriefing(
                                OfflineAirportBriefing(
                                    icao = selected,
                                    details = detailsCurrent,
                                    frequencies = airportRepository.frequencies(selected),
                                    weatherReport = weather ?: WeatherReport(null, null),
                                    decodedMetar = decodedMetar,
                                    decodedTaf = decodedTaf,
                                    notams = notams,
                                    decodedNotams = decodedNotams,
                                    charts = charts,
                                    updatedAtEpochMillis = offlineRepository.nowEpochMillis()
                                )
                            )
                            offlineStatus = offlineRepository.statusText(selected)
                        }
                    },
                    onSaveChart = { chart -> chartRepository.saveChartToLibrary(chart) },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun AirportMasterPane(
    query: String,
    onQueryChange: (String) -> Unit,
    airports: List<Airport>,
    recentAirports: List<String>,
    nearby: List<Pair<Airport, Double>>,
    nearbyMessage: String,
    onRequestNearby: () -> Unit,
    onSelectAirport: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text("Detalhes do Aeroporto", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    label = { Text("Buscar ICAO, cidade ou nome") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(selected = false, onClick = onRequestNearby, label = { Text("Aeroportos próximos") },
                        leadingIcon = { Icon(Icons.Default.MyLocation, null) })
                    Spacer(Modifier.width(8.dp))
                    Text(nearbyMessage, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (nearby.isNotEmpty()) {
                item { Text("Próximos") }
                items(nearby.take(6), key = { it.first.icao }) { pair ->
                    AirportListCard(
                        airport = pair.first,
                        suffix = String.format(Locale.US, "%.1f km", pair.second),
                        onClick = { onSelectAirport(pair.first.icao) }
                    )
                }
            }
            if (recentAirports.isNotEmpty()) {
                item {
                    Text("Recentes")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        recentAirports.take(4).forEach { recent ->
                            AssistChip(onClick = { onSelectAirport(recent) }, label = { Text(recent) })
                        }
                    }
                }
            }
            item { Text("Resultados") }
            items(airports, key = { it.icao }) { airport ->
                AirportListCard(airport = airport, suffix = airport.runwaySummary ?: "", onClick = { onSelectAirport(airport.icao) })
            }
        }
    }
}

@Composable
private fun AirportListCard(airport: Airport, suffix: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(Modifier.padding(10.dp)) {
            Text("${airport.icao} · ${airport.name}", fontWeight = FontWeight.SemiBold)
            Text("${airport.city}/${airport.uf}")
            if (suffix.isNotBlank()) Text(suffix, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun AirportDetailPane(
    detail: AirportDetails?,
    frequencies: List<Frequency>,
    weather: WeatherReport?,
    decodedMetar: DecodedMetar?,
    decodedTaf: DecodedTaf?,
    notams: List<Notam>,
    decodedNotams: List<DecodedNotam>,
    charts: List<AirportChart>,
    offlineStatus: String,
    isLoadingAisweb: Boolean,
    onSaveOffline: suspend () -> Unit,
    onSaveChart: (AirportChart) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    ElevatedCard(modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text("Briefing pré-voo", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "Informações para apoio ao planejamento. Sempre confirme nas fontes oficiais antes do voo.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(selected = false, onClick = { scope.launch { onSaveOffline() } },
                        label = { Text("Salvar offline") }, leadingIcon = { Icon(Icons.Default.Save, null) })
                    Text(offlineStatus, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (detail == null) {
                item { Text("Selecione um aeroporto para visualizar detalhes.") }
            } else {
                item { GeneralInfoCard(detail) }
                if (isLoadingAisweb) {
                    item { AiswebLoadingCard() }
                } else {
                    item { FrequenciesCard(frequencies) }
                }
                item { NotamCard(notams, decodedNotams) }
                item { WeatherCard(weather, decodedMetar, decodedTaf) }
                item { RmkCard(detail) }
                item { ChartsCard(charts, onSaveChart) }
            }
        }
    }
}

@Composable
private fun AiswebLoadingCard() {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("AISWEB", fontWeight = FontWeight.Bold)
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            repeat(3) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(20.dp)
                        .padding(top = 4.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(6.dp)
                        )
                )
            }
        }
    }
}

@Composable
private fun GeneralInfoCard(detail: AirportDetails) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Informações Gerais / ROTAER", fontWeight = FontWeight.Bold)
            Text("ICAO: ${detail.airport.icao}")
            Text("Nome: ${detail.airport.name}")
            Text("Cidade/UF: ${detail.airport.city}/${detail.airport.uf}")
            Text("Coordenadas: ${detail.coordinatesText}")
            Text("Elevação: ${detail.elevationFt ?: 0} ft")
            Text("Horário: ${detail.operatingHours}")
            Text("Serviços: ${detail.services.joinToString()}")
            Text("Combustível: ${detail.fuelAvailability}")
            Text("Restrições: ${detail.restrictions.joinToString(" | ")}")
            Text("Pistas:")
            detail.runways.forEach { rw ->
                Text("• ${rw.designation} ${rw.lengthMeters}x${rw.widthMeters}m ${rw.surface} ${rw.lighting.orEmpty()}")
            }
        }
    }
}

@Composable
private fun FrequenciesCard(frequencies: List<Frequency>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Frequências", fontWeight = FontWeight.Bold)
            if (frequencies.isEmpty()) Text("Sem frequências carregadas.")
            frequencies.forEach { freq ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(freq.type, fontWeight = FontWeight.SemiBold)
                        Text(freq.value, color = MaterialTheme.colorScheme.tertiary)
                    }
                }
            }
        }
    }
}

@Composable
private fun NotamCard(notams: List<Notam>, decodedNotams: List<DecodedNotam>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("NOTAMs decodificados", fontWeight = FontWeight.Bold)
            if (notams.isEmpty()) Text("Sem NOTAM carregado")
            notams.forEach { notam ->
                val decoded = decodedNotams.firstOrNull { it.notamId == notam.id }
                val color = when (decoded?.severity) {
                    NotamSeverity.CRITICAL -> Color(0xFFB3261E)
                    NotamSeverity.ATTENTION -> Color(0xFFEF6C00)
                    else -> Color(0xFF0277BD)
                }
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.13f))) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(notam.id, fontWeight = FontWeight.Bold)
                        Text("Bruto: ${notam.rawText}")
                        Text("Decodificado: ${decoded?.simplifiedPtBr ?: "Atenção: verificar texto bruto."}")
                        Text("Impacto provável: ${decoded?.probableImpact ?: "Informacional"}")
                        val from = humanDate(notam.validFromEpochMillis)
                        val to = notam.validToEpochMillis?.let { humanDate(it) } ?: "indeterminado"
                        Text("Validade: $from até $to")
                    }
                }
            }
        }
    }
}

@Composable
private fun WeatherCard(weather: WeatherReport?, decodedMetar: DecodedMetar?, decodedTaf: DecodedTaf?) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Cloud, null)
                Spacer(Modifier.width(8.dp))
                Text("METAR e TAF decodificados", fontWeight = FontWeight.Bold)
            }
            Text("METAR bruto: ${weather?.metarRaw ?: "não carregado"}")
            decodedMetar?.let {
                Text("Vento: ${it.wind}")
                Text("Visibilidade: ${it.visibility}")
                Text("Nuvens: ${it.clouds}")
                Text("Temp/Orvalho: ${it.temperatureDewPoint}")
                Text("QNH: ${it.qnh}")
                Text("Fenômenos: ${it.phenomena}")
                Text("Tendência: ${it.trend}")
            }
            Divider()
            Text("TAF bruto: ${weather?.tafRaw ?: "não carregado"}")
            decodedTaf?.let {
                Text("Vento: ${it.wind}")
                Text("Visibilidade: ${it.visibility}")
                Text("Nuvens: ${it.clouds}")
                Text("Fenômenos: ${it.phenomena}")
                Text("Tendência: ${it.trend}")
            }
        }
    }
}

@Composable
private fun RmkCard(detail: AirportDetails) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("RMK", fontWeight = FontWeight.Bold)
            detail.rmk.forEach { item ->
                val title = when (item.category) {
                    RmkCategory.OPERATIONAL_RESTRICTION -> "Restrição operacional"
                    RmkCategory.LOCAL_PROCEDURE -> "Procedimento local"
                    RmkCategory.WARNING -> "Aviso"
                    RmkCategory.OBSERVATION -> "Observação"
                }
                Text("• $title: ${item.text}")
            }
        }
    }
}

@Composable
private fun ChartsCard(charts: List<AirportChart>, onSaveChart: (AirportChart) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Cartas", fontWeight = FontWeight.Bold)
            if (charts.isEmpty()) Text("Nenhuma carta disponível")
            charts.forEach { chart ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp)) {
                        Text("${chart.title} (${chart.category})", fontWeight = FontWeight.SemiBold)
                        Text("Preview: ${chart.previewText}")
                        TextButton(onClick = { onSaveChart(chart) }) {
                            Text("Salvar na biblioteca Cartas")
                        }
                    }
                }
            }
        }
    }
}

private fun humanDate(epochMillis: Long): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(Date(epochMillis))
