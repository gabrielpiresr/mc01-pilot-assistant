package com.betpass.mc01pilot.route

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.w3c.dom.Element
import java.io.StringReader
import java.io.File
import android.graphics.pdf.PdfDocument
import android.os.Environment
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.*
import com.betpass.mc01pilot.airport.data.AirportRepository
import com.betpass.mc01pilot.airport.data.AiswebAirportDataProvider
import com.betpass.mc01pilot.airport.data.AiswebAerodromeService
import kotlinx.coroutines.launch

private val zuluFormatter = DateTimeFormatter.ofPattern("HH:mm'Z'").withZone(ZoneOffset.UTC)

data class RoutePlan(val id:String,val title:String,val createdAt:Long,val cruiseSpeedKt:Int,val cruisingAlt:String?,val departureId:String?,val destinationId:String?,val waypoints:List<RouteWaypoint>)
data class RouteWaypoint(val name:String,val lat:Double,val lon:Double,val altitudeFt:Int?)
data class RouteSettings(val waypointRadiusNm:Double=1.0,val autoConfirm:Boolean=true,val timerSeconds:Int=15)
data class RoutePassage(val index:Int,val actualZulu:String)
private data class RouteAerodromePanelData(
    val icao: String,
    val tabTitle: String = icao,
    val elevationFt: Int?,
    val runwaysText: String,
    val frequencies: List<Pair<String, String>>,
    val metar: String?,
    val taf: String?,
    val updatedAtMillis: Long = System.currentTimeMillis()
)

data class RouteDraft(
    val selectedPlanId: String? = null,
    val departureZulu: String = "",
    val cruiseKt: String = "95",
    val fuelBurnPerHour: String = "",
    val alternateIcao: String = "",
    val passages: List<RoutePassage> = emptyList()
)

private class RouteRepository(context: Context) {
    private val prefs = context.getSharedPreferences("route_plans", Context.MODE_PRIVATE)
    private val gson = Gson(); private val plansKey = "plans"; private val settingsKey = "settings"; private val draftKey = "draft"
    private val listType = object: TypeToken<List<RoutePlan>>() {}.type
    fun loadAll(): List<RoutePlan> = gson.fromJson(prefs.getString(plansKey, "[]"), listType) ?: emptyList()
    fun save(plan: RoutePlan) { val current = loadAll().filterNot { it.id == plan.id }; prefs.edit().putString(plansKey, gson.toJson((current + plan).sortedByDescending { it.createdAt })).apply() }
    fun loadSettings(): RouteSettings = gson.fromJson(prefs.getString(settingsKey, null), RouteSettings::class.java) ?: RouteSettings()
    fun saveSettings(settings: RouteSettings) { prefs.edit().putString(settingsKey, gson.toJson(settings)).apply() }
    fun loadDraft(): RouteDraft = gson.fromJson(prefs.getString(draftKey, null), RouteDraft::class.java) ?: RouteDraft()
    fun saveDraft(draft: RouteDraft) { prefs.edit().putString(draftKey, gson.toJson(draft)).apply() }
}

@Composable
fun RouteModule(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repository = remember { RouteRepository(context) }
    val plans = remember {
        mutableStateListOf<RoutePlan>().apply {
            addAll(repository.loadAll())
        }
    }
    var settings by remember { mutableStateOf(repository.loadSettings()) }
    val persistedDraft = remember { repository.loadDraft() }
    var selected by remember { mutableStateOf(plans.firstOrNull { it.id == persistedDraft.selectedPlanId } ?: plans.firstOrNull()) }
    var departureZulu by remember { mutableStateOf(persistedDraft.departureZulu) }
    var cruiseKt by remember { mutableStateOf(if (persistedDraft.cruiseKt.isBlank()) (selected?.cruiseSpeedKt ?: 95).toString() else persistedDraft.cruiseKt) }
    var fuelBurnPerHour by remember { mutableStateOf(persistedDraft.fuelBurnPerHour) }
    var alternateIcao by remember { mutableStateOf(persistedDraft.alternateIcao) }
    var routeMenuExpanded by remember { mutableStateOf(false) }
    val passages = remember { mutableStateListOf<RoutePassage>().apply { addAll(persistedDraft.passages) } }
    val airportRepository = remember { AirportRepository(AiswebAirportDataProvider(context)) }
    val coroutineScope = rememberCoroutineScope()
    var aerodromeInfo by remember { mutableStateOf<List<RouteAerodromePanelData>>(emptyList()) }
    var alternateAerodromeInfo by remember { mutableStateOf<RouteAerodromePanelData?>(null) }
    var selectedAerodromeTab by remember { mutableStateOf(0) }
    var isLoadingAerodromeInfo by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val parsed = runCatching { parsePln(context, uri) }.getOrNull() ?: return@rememberLauncherForActivityResult
        repository.save(parsed); plans.removeAll { it.id == parsed.id }; plans.add(0, parsed); selected = parsed; cruiseKt = parsed.cruiseSpeedKt.toString(); passages.clear()
    }

    val active = selected
    val rows = active?.let { computeRows(it, cruiseKt.toIntOrNull()?.coerceAtLeast(40) ?: 95, departureZulu, passages) }.orEmpty()
    val nextPending = rows.indexOfFirst { !it.isCompleted }
    val remainingNm = rows.drop(max(0, nextPending)).sumOf { it.legNm }
    val remainingMin = rows.drop(max(0, nextPending)).sumOf { it.eteMin }
    val routeTotalMinutes = rows.lastOrNull()?.totalMinutes ?: 0
    val reserveAndAlternateMinutes = 105
    val totalFuelMinutes = routeTotalMinutes + reserveAndAlternateMinutes
    val totalFuelLiters = fuelBurnPerHour.replace(',', '.').toDoubleOrNull()?.let { burnPerHour ->
        (burnPerHour / 60.0) * totalFuelMinutes
    }

    suspend fun refreshAerodromeInfo() {
        val dep = active?.departureId?.trim()?.uppercase().orEmpty()
        val dst = active?.destinationId?.trim()?.uppercase().orEmpty()
        if (dep.isBlank() || dst.isBlank()) {
            aerodromeInfo = emptyList()
            alternateAerodromeInfo = null
            return
        }
        isLoadingAerodromeInfo = true
        runCatching {
            val d1 = airportRepository.details(dep)
            val d2 = airportRepository.details(dst)
            val depParsed = runCatching {
                val depHtml = AiswebAerodromeService.fetchAiswebAerodromeHtml(dep)
                AiswebAerodromeService.parseAiswebAerodromeHtml(depHtml, dep)
            }.getOrElse {
                android.util.Log.w("RouteModule", "Falha ao carregar AISWEB de $dep: ${it.message}")
                com.betpass.mc01pilot.airport.data.AiswebAerodromeData(icao = dep)
            }
            val dstParsed = runCatching {
                val dstHtml = AiswebAerodromeService.fetchAiswebAerodromeHtml(dst)
                AiswebAerodromeService.parseAiswebAerodromeHtml(dstHtml, dst)
            }.getOrElse {
                android.util.Log.w("RouteModule", "Falha ao carregar AISWEB de $dst: ${it.message}")
                com.betpass.mc01pilot.airport.data.AiswebAerodromeData(icao = dst)
            }
            val depRunways = d1?.runways.orEmpty()
            val dstRunways = d2?.runways.orEmpty()
            aerodromeInfo = listOf(
                RouteAerodromePanelData(
                    icao = dep,
                    elevationFt = d1?.elevationFt,
                    runwaysText = depRunways.joinToString { "${it.designation}/${it.lengthMeters}m/${it.surface}" }.ifBlank { "-" },
                    frequencies = depParsed.frequencies.map { it.service to it.frequency },
                    metar = depParsed.metar,
                    taf = depParsed.taf
                ),
                RouteAerodromePanelData(
                    icao = dst,
                    elevationFt = d2?.elevationFt,
                    runwaysText = dstRunways.joinToString { "${it.designation}/${it.lengthMeters}m/${it.surface}" }.ifBlank { "-" },
                    frequencies = dstParsed.frequencies.map { it.service to it.frequency },
                    metar = dstParsed.metar,
                    taf = dstParsed.taf
                )
            )
            selectedAerodromeTab = selectedAerodromeTab.coerceAtMost(aerodromeInfo.lastIndex.coerceAtLeast(0))
        }.onFailure {
            android.util.Log.e("RouteModule", "Falha ao montar painel de aeródromo da rota", it)
            aerodromeInfo = emptyList()
        }
        isLoadingAerodromeInfo = false
    }

    LaunchedEffect(active?.departureId, active?.destinationId) { refreshAerodromeInfo() }

    LaunchedEffect(selected?.id, departureZulu, cruiseKt, fuelBurnPerHour, alternateIcao, passages.toList()) {
        repository.saveDraft(
            RouteDraft(
                selectedPlanId = selected?.id,
                departureZulu = departureZulu,
                cruiseKt = cruiseKt,
                fuelBurnPerHour = fuelBurnPerHour,
                alternateIcao = alternateIcao,
                passages = passages.toList()
            )
        )
    }

    LazyColumn(modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { picker.launch(arrayOf("text/xml", "application/xml", "*/*")) }) { Text("Importar .pln") }
            TextButton(onClick = { routeMenuExpanded = true }, enabled = plans.isNotEmpty()) { Text(active?.title ?: "Selecionar rota") }
            DropdownMenu(expanded = routeMenuExpanded, onDismissRequest = { routeMenuExpanded = false }) {
                plans.forEach { route -> DropdownMenuItem(text = { Text(route.title) }, onClick = { selected = route; cruiseKt = route.cruiseSpeedKt.toString(); passages.clear(); routeMenuExpanded = false }) }
            }
        } }
        item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Header", fontWeight = FontWeight.SemiBold)
            Text("${active?.departureId ?: "---"} → ${active?.destinationId ?: "---"} | ALT/FL ${active?.cruisingAlt ?: "---"}")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(value = departureZulu, onValueChange = { departureZulu = formatZuluInput(it) }, label = { Text("Hora partida (HH:mmZ)") }, modifier = Modifier.weight(1f))
                OutlinedTextField(value = cruiseKt, onValueChange = { cruiseKt = it.filter { c -> c.isDigit() || c == ',' } }, label = { Text("Velocidade (kt)") }, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(
                value = fuelBurnPerHour,
                onValueChange = { fuelBurnPerHour = it.filter { c -> c.isDigit() || c == ',' || c == '.' } },
                label = { Text("Consumo por hora (L/h)") },
                modifier = Modifier.fillMaxWidth()
            )
            TextButton(onClick = { departureZulu = zuluFormatter.format(Instant.now()) }) { Text("Setar hora agora") }
        } } }

        item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Alternativa", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = alternateIcao,
                    onValueChange = { alternateIcao = it.uppercase() },
                    label = { Text("ICAO alternativa") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Button(
                    onClick = {
                        val alt = alternateIcao.trim().uppercase()
                        if (alt.isBlank()) return@Button
                        coroutineScope.launch {
                            runCatching {
                                val details = airportRepository.details(alt)
                                val html = AiswebAerodromeService.fetchAiswebAerodromeHtml(alt)
                                val parsed = AiswebAerodromeService.parseAiswebAerodromeHtml(html, alt)
                                val runways = details?.runways.orEmpty()
                                alternateAerodromeInfo = RouteAerodromePanelData(
                                    icao = alt,
                                    tabTitle = "$alt (altn)",
                                    elevationFt = details?.elevationFt,
                                    runwaysText = runways.joinToString { "${it.designation}/${it.lengthMeters}m/${it.surface}" }.ifBlank { "-" },
                                    frequencies = parsed.frequencies.map { it.service to it.frequency },
                                    metar = parsed.metar,
                                    taf = parsed.taf
                                )
                            }.onFailure { alternateAerodromeInfo = null }
                        }
                    }
                ) { Text("Buscar") }
            }
        } } }
        item { Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Dados do Aeródromo", fontWeight = FontWeight.SemiBold)
                if (isLoadingAerodromeInfo) {
                    repeat(5) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (it == 0) 28.dp else 20.dp),
                            color = Color(0xFFE2E2E2),
                            shape = MaterialTheme.shapes.small
                        ) {}
                    }
                } else if (aerodromeInfo.isEmpty()) {
                    Text("Dados do aeródromo indisponíveis")
                } else {
                    val allAerodromes = aerodromeInfo + listOfNotNull(alternateAerodromeInfo)
                    TabRow(selectedTabIndex = selectedAerodromeTab) {
                        allAerodromes.forEachIndexed { index, info ->
                            Tab(
                                selected = selectedAerodromeTab == index,
                                onClick = { selectedAerodromeTab = index },
                                text = { Text(info.tabTitle) }
                            )
                        }
                    }
                    val selectedAerodrome = allAerodromes[selectedAerodromeTab.coerceIn(allAerodromes.indices)]
                    Text("Elevação: ${selectedAerodrome.elevationFt ?: "-"} ft")
                    Text("Pistas: ${selectedAerodrome.runwaysText}")
                    Text("Frequências:")
                    if (selectedAerodrome.frequencies.isEmpty()) {
                        Text("Sem frequências")
                    } else {
                        selectedAerodrome.frequencies.forEach { (service, frequency) ->
                            Card(Modifier.fillMaxWidth()) {
                                Row(
                                    Modifier.fillMaxWidth().padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(service, fontWeight = FontWeight.SemiBold)
                                    Text(frequency, color = MaterialTheme.colorScheme.tertiary)
                                }
                            }
                        }
                    }
                    Text("METAR: ${selectedAerodrome.metar ?: "-"}")
                    Text("Atualizado: ${zuluFormatter.format(Instant.ofEpochMilli(selectedAerodrome.updatedAtMillis))}")
                    HorizontalDivider(color = Color(0xFFE6E6E6), thickness = 1.dp)
                    Text("TAF: ${selectedAerodrome.taf ?: "-"}")
                    TextButton(onClick = { coroutineScope.launch { refreshAerodromeInfo() } }) { Text("Atualizar METAR/TAF") }
                }
            }
        } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { exportPdf(context, active, rows, false) }, enabled = active != null) { Text("Exportar PDF (pré)") }
            Button(onClick = { exportPdf(context, active, rows, true) }, enabled = active != null) { Text("Exportar PDF (pós)") }
        } }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(8.dp)) {
                    Row(
                        Modifier.fillMaxWidth().background(Color(0xFFD9D9D9)).padding(vertical = 6.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        HeaderCell("Ponto", 1.4f)
                        HeaderCell("Proa", 0.7f)
                        HeaderCell("Perna", 0.8f)
                        HeaderCell("Acum", 0.8f)
                        HeaderCell("ETE", 0.7f)
                        HeaderCell("Temp acum", 0.9f)
                        HeaderCell("ETA", 0.8f)
                        HeaderCell("Real", 1f)
                        HeaderCell("GS", 0.6f)
                        HeaderCell("", 0.5f)
                    }
                    rows.forEachIndexed { idx, row ->
                        Row(
                            Modifier.fillMaxWidth().background(Color(0xFF2B2B2B)).padding(vertical = 2.dp, horizontal = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            BodyCell(row.name, 1.4f)
                            BodyCell("${row.bearing.roundToInt()}°", 0.7f)
                            BodyCell("${"%.1f".format(row.legNm)}", 0.8f)
                            BodyCell("${"%.1f".format(row.totalNm)}", 0.8f)
                            BodyCell("${row.eteMin}m", 0.7f)
                            BodyCell("${row.totalMinutes}m", 0.9f)
                            BodyCell(row.eta ?: "--:--Z", 0.8f)
                            OutlinedTextField(
                                value = row.actual ?: "",
                                onValueChange = { value ->
                                    passages.removeAll { it.index == idx }
                                    passages.add(RoutePassage(idx, formatZuluInput(value)))
                                },
                                placeholder = { Text("--:--Z") },
                                singleLine = true,
                                textStyle = TextStyle(fontSize = 11.sp, color = Color.White),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedContainerColor = if (row.actual.isNullOrBlank()) Color(0xFF3A3A3A) else Color(0xFF0B3D0B),
                                    unfocusedContainerColor = if (row.actual.isNullOrBlank()) Color(0xFF3A3A3A) else Color(0xFF0B3D0B)
                                ),
                                modifier = Modifier.weight(0.8f).height(44.dp)
                            )
                            BodyCell(row.groundSpeedKt?.toString() ?: "--", 0.6f)
                            IconButton(
                                onClick = {
                                    passages.removeAll { it.index == idx }
                                    passages.add(RoutePassage(idx, zuluFormatter.format(Instant.now())))
                                },
                                modifier = Modifier.weight(0.5f)
                            ) { Icon(Icons.Default.Check, contentDescription = "Setar hora agora") }
                        }
                        HorizontalDivider(color = Color(0xFF4A4A4A), thickness = 0.5.dp)
                    }
                }
            }
        }
        item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(8.dp)) {
            Text("Combustível", fontWeight = FontWeight.SemiBold)
            Text("Cálculo: tempo de rota + 1h de reserva + 45min para alternativa (total extra: 1h45).")
            Text("Tempo de rota: ${routeTotalMinutes} min | Tempo total considerado: ${totalFuelMinutes} min")
            Text("Combustível total: ${totalFuelLiters?.let { "%.1f L".format(it) } ?: "informe o consumo por hora no header"}")
        } } }
        item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(8.dp)) {
            Text("Resumo de voo", fontWeight = FontWeight.SemiBold)
            Text("Distância restante: ${"%.1f".format(remainingNm)} NM")
            Text("Tempo restante: ${remainingMin} min")
            Text("Próximo waypoint: ${rows.getOrNull(nextPending)?.name ?: "Finalizado"}")
            Text("ETA destino: ${rows.lastOrNull()?.eta ?: "--:--Z"}")
        } } }
    }
}

private fun exportPdf(context: Context, plan: RoutePlan?, rows: List<NavRow>, postFlight: Boolean) {
    if (plan == null) return
    val pdf = PdfDocument()
    val page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
    val c = page.canvas
    val p = android.graphics.Paint().apply { textSize = 12f }
    var y = 30f
    c.drawText("Ficha de Navegação - ${plan.title}", 20f, y, p); y += 20
    rows.forEach { r ->
        val line = if (postFlight) "${r.name} P:${r.bearing.roundToInt()} ETE:${r.eteMin} ETA:${r.eta} REAL:${r.actual ?: "-"} GS:${r.groundSpeedKt ?: "-"}"
        else "${r.name} P:${r.bearing.roundToInt()} ETE:${r.eteMin} ETA:${r.eta}"
        c.drawText(line, 20f, y, p); y += 16
    }
    pdf.finishPage(page)
    val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
    val file = File(dir, "rota_${plan.id}_${if (postFlight) "pos" else "pre"}.pdf")
    file.outputStream().use { pdf.writeTo(it) }
    pdf.close()
}

data class NavRow(val name:String,val bearing:Double,val legNm:Double,val totalNm:Double,val eteMin:Int,val totalMinutes:Int,val eta:String?,val actual:String?,val groundSpeedKt:Int?,val isCompleted:Boolean=false)

private fun computeRows(plan: RoutePlan, cruiseKt: Int, departureZulu: String, passages: List<RoutePassage>): List<NavRow> {
    if (plan.waypoints.size < 2) return emptyList(); val dep = parseZuluToday(departureZulu); var totNm = 0.0; var totMin = 0
    return plan.waypoints.zipWithNext().mapIndexed { index, (a,b) ->
        val d = haversineNm(a.lat,a.lon,b.lat,b.lon); val brg = bearingDeg(a.lat,a.lon,b.lat,b.lon); val ete = ((d / cruiseKt) * 60.0).roundToInt().coerceAtLeast(1)
        totNm += d; totMin += ete; val pass = passages.firstOrNull { it.index == index }
        val passInstant = pass?.actualZulu?.let { parseZuluToday(it) }
        val gs = if (passInstant != null && dep != null) { val elapsedH = ((passInstant.toEpochMilli() - dep.toEpochMilli()) / 3600000.0).coerceAtLeast(0.0001); (totNm / elapsedH).roundToInt() } else null
        NavRow(b.name, brg, d, totNm, ete, totMin, dep?.plusSeconds(totMin*60L)?.let { zuluFormatter.format(it) }, pass?.actualZulu, gs, passInstant != null)
    }
}

private fun parsePln(context: Context, uri: Uri): RoutePlan {
    val xml = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: error("Arquivo inválido")
    val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(org.xml.sax.InputSource(StringReader(xml)))
    val fp = doc.getElementsByTagName("FlightPlan.FlightPlan").item(0) as Element
    val title = fp.getElementsByTagName("Title").item(0)?.textContent?.trim().orEmpty()
    val waypoints = mutableListOf<RouteWaypoint>(); val nodes = fp.getElementsByTagName("ATCWaypoint")
    for (i in 0 until nodes.length) { val wp = nodes.item(i) as? Element ?: continue; val pos = wp.getElementsByTagName("WorldPosition").item(0)?.textContent ?: continue; val p = parseWorldPosition(pos) ?: continue; waypoints += RouteWaypoint(wp.getAttribute("id").ifBlank { "WP-${i+1}" }, p.first, p.second, p.third) }
    return RoutePlan(title.ifBlank { "rota-${System.currentTimeMillis()}" }, title.ifBlank { "Rota importada" }, System.currentTimeMillis(),95,fp.getElementsByTagName("CruisingAlt").item(0)?.textContent?.trim(),fp.getElementsByTagName("DepartureID").item(0)?.textContent?.trim(),fp.getElementsByTagName("DestinationID").item(0)?.textContent?.trim(),waypoints)
}

private fun formatZuluInput(raw: String): String {
    val digits = raw.filter { it.isDigit() }.take(4)
    if (digits.isEmpty()) return ""
    val hh = digits.take(2)
    val mm = digits.drop(2)
    return when {
        mm.isEmpty() -> hh
        else -> "$hh:$mm" + if (digits.length == 4) "Z" else ""
    }
}

private fun parseWorldPosition(raw: String): Triple<Double, Double, Int?>? { val c = raw.split(","); if (c.size<2) return null; val lat=parseDms(c[0].trim())?:return null; val lon=parseDms(c[1].trim())?:return null; return Triple(lat,lon,c.getOrNull(2)?.replace("+","")?.toDoubleOrNull()?.roundToInt()) }
private fun parseDms(v: String): Double? { val m=Regex("([NSWE])(\\d+)°\\s*(\\d+)'\\s*([0-9.]+)\\\"").find(v)?:return null; val d=m.groupValues[2].toDouble()+m.groupValues[3].toDouble()/60+m.groupValues[4].toDouble()/3600; return if(m.groupValues[1]=="S"||m.groupValues[1]=="W") -d else d }
private fun parseZuluToday(input:String): Instant? { val m=Regex("^([0-1][0-9]|2[0-3]):([0-5][0-9])Z$").matchEntire(input.trim().uppercase())?:return null; val n=Instant.now().atZone(ZoneOffset.UTC); return n.withHour(m.groupValues[1].toInt()).withMinute(m.groupValues[2].toInt()).withSecond(0).withNano(0).toInstant() }
private fun haversineNm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double { val r=6371.0; val dLat=Math.toRadians(lat2-lat1); val dLon=Math.toRadians(lon2-lon1); val a=sin(dLat/2)*sin(dLat/2)+cos(Math.toRadians(lat1))*cos(Math.toRadians(lat2))*sin(dLon/2)*sin(dLon/2); return (r*(2*atan2(sqrt(a),sqrt(1-a))))*0.539957 }
private fun bearingDeg(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double { val y=sin(Math.toRadians(lon2-lon1))*cos(Math.toRadians(lat2)); val x=cos(Math.toRadians(lat1))*sin(Math.toRadians(lat2))-sin(Math.toRadians(lat1))*cos(Math.toRadians(lat2))*cos(Math.toRadians(lon2-lon1)); val trueBrg = (Math.toDegrees(atan2(y,x))+360)%360; return (trueBrg + 23) % 360 }

@Composable
private fun RowScope.HeaderCell(text: String, weight: Float) {
    Text(text = text, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color(0xFF1C1C1C), modifier = Modifier.weight(weight))
}

@Composable
private fun RowScope.BodyCell(text: String, weight: Float) {
    Text(text = text, fontSize = 11.sp, color = Color.White, modifier = Modifier.weight(weight))
}
