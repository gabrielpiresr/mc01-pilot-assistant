package com.betpass.mc01pilot.airport.ui

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import com.betpass.mc01pilot.airport.notam.DecodedNotam
import com.betpass.mc01pilot.airport.data.DecodedTaf
import com.betpass.mc01pilot.airport.data.Frequency
import com.betpass.mc01pilot.airport.data.Notam
import com.betpass.mc01pilot.airport.data.NotamRepository
import com.betpass.mc01pilot.airport.notam.NotamCategory
import com.betpass.mc01pilot.airport.notam.NotamSeverity
import com.betpass.mc01pilot.airport.data.OfflineAirportBriefing
import com.betpass.mc01pilot.airport.data.OfflineAirportRepository
import com.betpass.mc01pilot.airport.data.RmkCategory
import com.betpass.mc01pilot.airport.data.Runway
import com.betpass.mc01pilot.airport.data.WeatherReport
import com.betpass.mc01pilot.airport.data.WeatherRepository
import com.betpass.mc01pilot.airport.location.LocationClient
import com.betpass.mc01pilot.airport.location.distanceKm
import com.betpass.mc01pilot.data.LibraryRepository
import com.betpass.mc01pilot.data.StoredFile
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.io.File
import java.util.Date
import java.util.Locale
import java.net.URL
import java.net.HttpURLConnection
import kotlin.math.roundToInt
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AirportDetailsModule(modifier: Modifier = Modifier) {
    val logTag = "AirportDetailsModule"
    val context = LocalContext.current
    val airportRepository = remember { AirportRepository(AiswebAirportDataProvider(context)) }
    val weatherRepository = remember { WeatherRepository(AiswebWeatherDataProvider()) }
    val notamRepository = remember { NotamRepository(AiswebNotamDataProvider()) }
    val chartRepository = remember { ChartRepository(context, AiswebChartDataProvider()) }
    val offlineRepository = remember { OfflineAirportRepository(context) }
    val libraryRepository = remember { LibraryRepository(context) }
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
        airports = runCatching { airportRepository.search(query) }
            .onSuccess { result ->
                Log.d(logTag, "search success query='${query.trim()}' results=${result.size}")
            }
            .onFailure { error ->
                Log.e(logTag, "search failed query='${query.trim()}'", error)
            }
            .getOrElse { emptyList() }
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
                modifier = Modifier.weight(0.285f).fillMaxHeight()
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
                availableChartFolders = libraryRepository.list("chart").filter { it.isFolder },
                onCreateChartFolder = { name -> libraryRepository.createFolder("chart", name, null) },
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
                onSaveChart = { chart, folderId, folderName -> chartRepository.saveChartToLibrary(chart, folderId, folderName) },
                modifier = Modifier.weight(0.715f).fillMaxHeight()
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
                    availableChartFolders = libraryRepository.list("chart").filter { it.isFolder },
                    onCreateChartFolder = { name -> libraryRepository.createFolder("chart", name, null) },
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
                    onSaveChart = { chart, folderId, folderName -> chartRepository.saveChartToLibrary(chart, folderId, folderName) },
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
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    fun handleAirportSelection(icao: String) {
        Log.d("AirportMasterPane", "airport selected icao=$icao query='${query.trim()}'")
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        onSelectAirport(icao)
    }
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
            if (query.isNotBlank() && airports.isNotEmpty()) {
                item { Text("Resultado principal") }
                item {
                    AirportListCard(
                        airport = airports.first(),
                        suffix = airports.first().runwaySummary ?: "",
                        onClick = { handleAirportSelection(airports.first().icao) }
                    )
                }
            }
            if (nearby.isNotEmpty()) {
                item { Text("Próximos") }
                items(nearby.take(6), key = { it.first.icao }) { pair ->
                    AirportListCard(
                        airport = pair.first,
                        suffix = String.format(Locale.US, "%.1f km", pair.second),
                        onClick = { handleAirportSelection(pair.first.icao) }
                    )
                }
            }
            if (recentAirports.isNotEmpty()) {
                item {
                    Text("Recentes")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        recentAirports.take(4).forEach { recent ->
                            AssistChip(onClick = { handleAirportSelection(recent) }, label = { Text(recent) })
                        }
                    }
                }
            }
            item { Text("Resultados") }
            val remaining = if (query.isNotBlank()) airports.drop(1) else airports
            items(remaining, key = { it.icao }) { airport ->
                AirportListCard(airport = airport, suffix = airport.runwaySummary ?: "", onClick = { handleAirportSelection(airport.icao) })
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

private enum class AirportDetailTab(val title: String) {
    GENERAL("Info geral"),
    WEATHER("METAR / TAF"),
    NOTAM("NOTAM"),
    CHARTS("Cartas"),
    RMK("RMK")
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
    availableChartFolders: List<StoredFile>,
    onCreateChartFolder: (String) -> StoredFile,
    onSaveOffline: suspend () -> Unit,
    onSaveChart: suspend (AirportChart, String?, String?) -> Boolean,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(AirportDetailTab.GENERAL) }
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
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        AirportDetailTab.values().forEach { tab ->
                            FilterChip(selected = selectedTab == tab, onClick = { selectedTab = tab }, label = { Text(tab.title) })
                        }
                    }
                }
                when (selectedTab) {
                    AirportDetailTab.GENERAL -> {
                        item { GeneralInfoCard(detail) }
                        if (isLoadingAisweb) item { AiswebLoadingCard() } else item { FrequenciesCard(frequencies) }
                    }
                    AirportDetailTab.WEATHER -> item { WeatherCard(weather, decodedMetar, decodedTaf, detail.runways) }
                    AirportDetailTab.NOTAM -> item { NotamCard(notams, decodedNotams) }
                    AirportDetailTab.CHARTS -> item { ChartsCard(charts, availableChartFolders, onSaveChart, onCreateChartFolder, detail.airport.icao) }
                    AirportDetailTab.RMK -> item { RmkCard(detail) }
                }
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
    val dateFormatter = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("NOTAMs decodificados", fontWeight = FontWeight.Bold)
            if (notams.isEmpty()) Text("Sem NOTAM carregado")
            notams.forEach { notam ->
                val decoded = decodedNotams.firstOrNull { it.notamId == notam.id || it.rawText == notam.rawText }
                val color = when (decoded?.severity) {
                    NotamSeverity.CRITICAL -> Color(0xFFB3261E)
                    NotamSeverity.HIGH -> Color(0xFFEF6C00)
                    NotamSeverity.MEDIUM -> Color(0xFFF9A825)
                    else -> Color(0xFF0277BD)
                }
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.13f))) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(notam.id, fontWeight = FontWeight.Bold)
                        Text("Bruto: ${notam.rawText}")
                        Text("Título: ${decoded?.title ?: "Atenção"}")
                        Text("Resumo: ${decoded?.plainLanguageSummary ?: "Verifique o texto original."}")
                        Text("Categoria: ${decoded?.category ?: NotamCategory.UNKNOWN} • Severidade: ${decoded?.severity ?: NotamSeverity.INFO}")
                        val from = dateFormatter.format(Date(notam.validFromEpochMillis))
                        val to = notam.validToEpochMillis?.let { dateFormatter.format(Date(it)) } ?: "indeterminado"
                        Text("Validade: $from até $to")
                    }
                }
                Divider(color = Color.LightGray.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
private fun WeatherCard(weather: WeatherReport?, decodedMetar: DecodedMetar?, decodedTaf: DecodedTaf?, runways: List<Runway>) {
    val windData = parseWind(decodedMetar?.wind)
    val runwayComponents = computeRunwayWindComponents(runways, windData?.first, windData?.second)
    val idealRunway = runwayComponents.maxByOrNull { it.headwindKt }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Cloud, null)
                Spacer(Modifier.width(8.dp))
                Text("METAR e TAF decodificados", fontWeight = FontWeight.Bold)
            }
            Text("METAR bruto: ${weather?.metarRaw ?: "não carregado"}")
            decodedMetar?.let {
                Text("Tabela METAR", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    WeatherFieldTable(
                        rows = listOf(
                            "Vento" to it.wind,
                            "Visibilidade" to it.visibility,
                            "Nuvens" to it.clouds,
                            "Temp/Orvalho" to it.temperatureDewPoint
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    WeatherFieldTable(
                        rows = listOf(
                            "QNH" to it.qnh,
                            "Fenômenos" to it.phenomena,
                            "Tendência" to it.trend,
                            "Pista ideal" to (idealRunway?.runway ?: "--")
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
                if (runwayComponents.isNotEmpty()) {
                    Text("Componente de vento por pista", fontWeight = FontWeight.SemiBold)
                    runwayComponents.forEach { c ->
                        Text("${c.runway}: proa/cauda ${c.headwindLabel} kt • través ${c.crosswindLabel} kt")
                    }
                }
            }
            Divider()
            Text("TAF bruto: ${weather?.tafRaw ?: "não carregado"}")
            decodedTaf?.let {
                Text("Tabela TAF", fontWeight = FontWeight.SemiBold)
                WeatherFieldTable(
                    rows = listOf(
                        "Vento" to if (it.wind.isBlank()) "--" else it.wind,
                        "Visibilidade" to if (it.visibility.isBlank()) "--" else it.visibility,
                        "Nuvens" to if (it.clouds.isBlank()) "--" else it.clouds,
                        "Fenômenos" to if (it.phenomena.isBlank()) "--" else it.phenomena
                    )
                )
                WeatherFieldTable(rows = listOf("Tendência" to if (it.trend.isBlank()) "--" else it.trend))
            }
        }
    }
}

@Composable
private fun WeatherFieldTable(rows: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            rows.forEach { (k, v) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(k, fontWeight = FontWeight.Medium)
                    Text(v.ifBlank { "--" })
                }
            }
        }
    }
}

private data class RunwayWindComponent(val runway: String, val headwindKt: Double, val headwindLabel: String, val crosswindLabel: String)

private fun parseWind(windText: String?): Pair<Int, Int>? {
    if (windText.isNullOrBlank()) return null
    val m = Regex("(\\d{1,3})°\\s*-\\s*(\\d{1,3})").find(windText) ?: Regex("\\b(\\d{3})(\\d{2,3})KT\\b").find(windText)
    return m?.let { it.groupValues[1].toInt() to it.groupValues[2].toInt() }
}

private fun computeRunwayWindComponents(runways: List<Runway>, windDir: Int?, windKt: Int?): List<RunwayWindComponent> {
    if (windDir == null || windKt == null) return emptyList()
    return runways.mapNotNull { runway ->
        val runwayNum = Regex("(\\d{2})").find(runway.designation)?.groupValues?.get(1)?.toIntOrNull() ?: return@mapNotNull null
        val heading = runwayNum * 10.0
        val angle = normalizeAngle(windDir - heading)
        val rad = angle * PI / 180.0
        val headwind = windKt * cos(rad)
        val crosswind = windKt * sin(rad)
        RunwayWindComponent(
            runway = runway.designation,
            headwindKt = headwind,
            headwindLabel = "${if (headwind >= 0) "proa" else "cauda"} ${abs(headwind).roundToInt()}",
            crosswindLabel = "${if (crosswind >= 0) "dir" else "esq"} ${abs(crosswind).roundToInt()}"
        )
    }
}

private fun normalizeAngle(value: Double): Double {
    var angle = value % 360
    if (angle > 180) angle -= 360.0
    if (angle < -180) angle += 360.0
    return angle
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
                Divider(color = Color.LightGray.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
private fun ChartsCard(
    charts: List<AirportChart>,
    availableChartFolders: List<StoredFile>,
    onSaveChart: suspend (AirportChart, String?, String?) -> Boolean,
    onCreateChartFolder: (String) -> StoredFile,
    airportIcao: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var previewChartId by remember { mutableStateOf<String?>(null) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var previewMessage by remember { mutableStateOf<String?>(null) }
    var isPreviewFullscreen by remember { mutableStateOf(false) }
    var saveTargetChart by remember { mutableStateOf<AirportChart?>(null) }
    var isSavingChart by remember { mutableStateOf(false) }
    var selectedFolderId by remember { mutableStateOf<String?>(null) }
    var newFolderName by remember { mutableStateOf("") }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Cartas", fontWeight = FontWeight.Bold)
            if (charts.isEmpty()) Text("Nenhuma carta disponível")
            charts.forEach { chart ->
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Column(Modifier.padding(10.dp)) {
                        Text("${chart.title} (${chart.category})", fontWeight = FontWeight.SemiBold)
                        if (previewChartId == chart.id) {
                            previewBitmap?.let { bmp ->
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box(modifier = Modifier.fillMaxWidth().height(360.dp).background(Color.White)) {
                                        ZoomableChartImage(bitmap = bmp, contentDescription = "Preview")
                                    }
                                }
                            } ?: Text(previewMessage ?: "Carregando preview...", style = MaterialTheme.typography.bodySmall)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = {
                                if (previewChartId == chart.id) {
                                    previewChartId = null
                                    previewBitmap = null
                                    previewMessage = null
                                    isPreviewFullscreen = false
                                } else {
                                    previewChartId = chart.id
                                    previewMessage = "Carregando preview..."
                                    previewBitmap = null
                                    scope.launch {
                                        val bitmap = loadPdfPreviewFromUrl(context, chart.sourceUrl)
                                        previewBitmap = bitmap
                                        previewMessage = if (bitmap == null) "Não foi possível carregar o documento." else null
                                    }
                                }
                            }) { Text("Preview") }
                            if (previewChartId == chart.id && previewBitmap != null) {
                                OutlinedButton(onClick = { isPreviewFullscreen = true }) { Text("Tela cheia") }
                                OutlinedButton(onClick = {
                                    previewChartId = null
                                    previewBitmap = null
                                    previewMessage = null
                                    isPreviewFullscreen = false
                                }) { Text("Fechar preview") }
                            }
                            OutlinedButton(onClick = { saveTargetChart = chart }) { Text("Salvar no voo") }
                        }
                    }
                }
            }
            OutlinedButton(onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://aisweb.decea.mil.br/")))
            }) { Text("Abrir no AISWEB ($airportIcao)") }
        }
    }

    if (isPreviewFullscreen && previewBitmap != null) {
        Dialog(onDismissRequest = { isPreviewFullscreen = false }) {
            Card(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().background(Color.White).padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        OutlinedButton(onClick = { isPreviewFullscreen = false }) { Text("Fechar") }
                    }
                    Box(modifier = Modifier.weight(1f).fillMaxWidth().background(Color.White)) {
                        ZoomableChartImage(bitmap = previewBitmap!!, contentDescription = "Preview tela cheia")
                    }
                }
            }
        }
    }

    if (saveTargetChart != null) {
        AlertDialog(
            onDismissRequest = { saveTargetChart = null },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        isSavingChart = true
                        val folderName = availableChartFolders.firstOrNull { it.id == selectedFolderId }?.name
                        onSaveChart(saveTargetChart!!, selectedFolderId, folderName)
                        isSavingChart = false
                        saveTargetChart = null
                    }
                }) { Text("Salvar") }
            },
            dismissButton = { TextButton(onClick = { saveTargetChart = null }) { Text("Cancelar") } },
            title = { Text("Salvar carta na pasta do voo") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Selecione uma pasta:")
                    availableChartFolders.forEach { folder ->
                        Row(
                            Modifier.fillMaxWidth().clickable { selectedFolderId = folder.id },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(if (selectedFolderId == folder.id) "● " else "○ ")
                            Text(folder.name)
                        }
                    }
                    OutlinedTextField(
                        value = newFolderName,
                        onValueChange = { newFolderName = it },
                        label = { Text("Nova pasta (opcional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextButton(onClick = {
                        val safeName = newFolderName.trim()
                        if (safeName.isNotBlank()) {
                            val folder = onCreateChartFolder(safeName)
                            selectedFolderId = folder.id
                            newFolderName = ""
                        }
                    }) { Text("Criar nova pasta") }
                    if (isSavingChart) Text("Baixando e salvando arquivo...", style = MaterialTheme.typography.bodySmall)
                }
            }
        )
    }
}

@Composable
private fun ZoomableChartImage(bitmap: Bitmap, contentDescription: String) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, _, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    if (scale <= 1.01f) {
                        offsetX = 0f
                        offsetY = 0f
                    }
                }
            }
            .pointerInput(scale) {
                detectDragGestures { change, dragAmount ->
                    if (scale > 1f) {
                        change.consume()
                        offsetX += dragAmount.x
                        offsetY += dragAmount.y
                    }
                }
            }
    ) {
        androidx.compose.foundation.Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .graphicsLayer(scaleX = scale, scaleY = scale)
        )
    }
}

private suspend fun loadPdfPreviewFromUrl(context: android.content.Context, sourceUrl: String?): Bitmap? {
    val tag = "AirportChartsPreview"
    if (sourceUrl.isNullOrBlank()) return null
    return withContext(Dispatchers.IO) {
        runCatching {
            val tempFile = File.createTempFile("chart_preview", ".pdf", context.cacheDir)
            val copied = openHttpStreamForPreview(sourceUrl).use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            }
            Log.d(tag, "Preview download complete for $sourceUrl bytes=$copied file=${tempFile.absolutePath}")
            if (copied <= 0L) return@runCatching null
            ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (renderer.pageCount == 0) return@use null
                    renderer.openPage(0).use { page ->
                        Bitmap.createBitmap(
                            (page.width * 2.5f).toInt().coerceAtLeast(1),
                            (page.height * 2.5f).toInt().coerceAtLeast(1),
                            Bitmap.Config.ARGB_8888
                        ).also { bmp ->
                            bmp.eraseColor(android.graphics.Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        }
                    }
                }
            }
        }.onFailure {
            Log.e(tag, "Preview failed for $sourceUrl", it)
        }.getOrNull()
    }
}

private fun openHttpStreamForPreview(sourceUrl: String): java.io.InputStream {
    val sanitizedUrl = sourceUrl.trim()
    var lastError: java.io.IOException? = null
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
                throw java.io.IOException("HTTP $code while previewing PDF. url=$sanitizedUrl body=$err")
            }
            return connection.inputStream
        } catch (ex: java.io.IOException) {
            lastError = ex
            Log.w("AirportChartsPreview", "Attempt ${attempt + 1}/3 failed for $sanitizedUrl", ex)
            if (attempt < 2) Thread.sleep((1000L * (attempt + 1)))
        }
    }
    throw lastError ?: java.io.IOException("Unknown preview error for $sanitizedUrl")
}
