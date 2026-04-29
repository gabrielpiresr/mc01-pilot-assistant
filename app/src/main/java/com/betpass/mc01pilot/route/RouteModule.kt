package com.betpass.mc01pilot.route

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
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

private val zuluFormatter = DateTimeFormatter.ofPattern("HH:mm'Z'").withZone(ZoneOffset.UTC)

data class RoutePlan(val id:String,val title:String,val createdAt:Long,val cruiseSpeedKt:Int,val cruisingAlt:String?,val departureId:String?,val destinationId:String?,val waypoints:List<RouteWaypoint>)
data class RouteWaypoint(val name:String,val lat:Double,val lon:Double,val altitudeFt:Int?)
data class RouteSettings(val waypointRadiusNm:Double=1.0,val autoConfirm:Boolean=true,val timerSeconds:Int=15)
data class RoutePassage(val index:Int,val actualZulu:String)

private class RouteRepository(context: Context) {
    private val prefs = context.getSharedPreferences("route_plans", Context.MODE_PRIVATE)
    private val gson = Gson(); private val plansKey = "plans"; private val settingsKey = "settings"
    private val listType = object: TypeToken<List<RoutePlan>>() {}.type
    fun loadAll(): List<RoutePlan> = gson.fromJson(prefs.getString(plansKey, "[]"), listType) ?: emptyList()
    fun save(plan: RoutePlan) { val current = loadAll().filterNot { it.id == plan.id }; prefs.edit().putString(plansKey, gson.toJson((current + plan).sortedByDescending { it.createdAt })).apply() }
    fun loadSettings(): RouteSettings = gson.fromJson(prefs.getString(settingsKey, null), RouteSettings::class.java) ?: RouteSettings()
    fun saveSettings(settings: RouteSettings) { prefs.edit().putString(settingsKey, gson.toJson(settings)).apply() }
}

@Composable
fun RouteModule(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repository = remember { RouteRepository(context) }
    val plans = remember { mutableStateListOf<RoutePlan>().also { it.addAll(repository.loadAll()) } }
    var settings by remember { mutableStateOf(repository.loadSettings()) }
    var selected by remember { mutableStateOf(plans.firstOrNull()) }
    var departureZulu by remember { mutableStateOf("") }
    var cruiseKt by remember { mutableStateOf((selected?.cruiseSpeedKt ?: 95).toString()) }
    var routeMenuExpanded by remember { mutableStateOf(false) }
    val passages = remember { mutableStateListOf<RoutePassage>() }
    val airportRepository = remember { AirportRepository(AiswebAirportDataProvider(context)) }
    var aerodromeInfo by remember { mutableStateOf("Dados do aeródromo indisponíveis") }

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

    LaunchedEffect(active?.departureId, active?.destinationId) {
        val dep = active?.departureId ?: return@LaunchedEffect
        val dst = active.destinationId ?: return@LaunchedEffect
        runCatching {
            val d1 = airportRepository.details(dep)
            val d2 = airportRepository.details(dst)
            aerodromeInfo = buildString {
                append("Origem $dep elevação: ${d1?.elevationFt ?: "-"} ft | pistas: ${d1?.runways?.joinToString { "${it.designation}/${it.lengthMeters}m/${it.surface}" } ?: "-"}\n")
                append("Destino $dst elevação: ${d2?.elevationFt ?: "-"} ft | pistas: ${d2?.runways?.joinToString { "${it.designation}/${it.lengthMeters}m/${it.surface}" } ?: "-"}")
            }
        }.onFailure { aerodromeInfo = "Falha ao carregar dados de aeródromo: ${it.message}" }
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
                OutlinedTextField(value = departureZulu, onValueChange = { departureZulu = it }, label = { Text("Hora partida (HH:mmZ)") }, modifier = Modifier.weight(1f))
                OutlinedTextField(value = cruiseKt, onValueChange = { cruiseKt = it.filter { c -> c.isDigit() || c == ',' } }, label = { Text("Velocidade (kt)") }, modifier = Modifier.weight(1f))
            }
            TextButton(onClick = { departureZulu = zuluFormatter.format(Instant.now()) }) { Text("Setar hora agora") }
        } } }

        item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Configurações", fontWeight = FontWeight.SemiBold)
            OutlinedTextField(value = settings.waypointRadiusNm.toString(), onValueChange = { settings = settings.copy(waypointRadiusNm = it.replace(',', '.').toDoubleOrNull() ?: settings.waypointRadiusNm); repository.saveSettings(settings) }, label = { Text("Raio waypoint (NM)") })
            OutlinedTextField(value = settings.timerSeconds.toString(), onValueChange = { settings = settings.copy(timerSeconds = it.toIntOrNull() ?: settings.timerSeconds); repository.saveSettings(settings) }, label = { Text("Timer auto confirmação (s)") })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Checkbox(checked = settings.autoConfirm, onCheckedChange = { settings = settings.copy(autoConfirm = it); repository.saveSettings(settings) })
                Text("Auto confirmação")
            }
        } } }
        item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(8.dp)) { Text("Dados do Aeródromo", fontWeight = FontWeight.SemiBold); Text(aerodromeInfo) } } }
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
                            BodyCell(row.eta ?: "--:--Z", 0.8f)
                            OutlinedTextField(
                                value = row.actual ?: "",
                                onValueChange = { value ->
                                    passages.removeAll { it.index == idx }
                                    passages.add(RoutePassage(idx, value.uppercase()))
                                },
                                placeholder = { Text("--:--Z") },
                                singleLine = true,
                                textStyle = TextStyle(fontSize = 11.sp, color = Color(0xFF1C1C1C)),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedContainerColor = Color(0xFF3A3A3A),
                                    unfocusedContainerColor = Color(0xFF3A3A3A)
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

data class NavRow(val name:String,val bearing:Double,val legNm:Double,val totalNm:Double,val eteMin:Int,val eta:String?,val actual:String?,val groundSpeedKt:Int?,val isCompleted:Boolean=false)

private fun computeRows(plan: RoutePlan, cruiseKt: Int, departureZulu: String, passages: List<RoutePassage>): List<NavRow> {
    if (plan.waypoints.size < 2) return emptyList(); val dep = parseZuluToday(departureZulu); var totNm = 0.0; var totMin = 0
    return plan.waypoints.zipWithNext().mapIndexed { index, (a,b) ->
        val d = haversineNm(a.lat,a.lon,b.lat,b.lon); val brg = bearingDeg(a.lat,a.lon,b.lat,b.lon); val ete = ((d / cruiseKt) * 60.0).roundToInt().coerceAtLeast(1)
        totNm += d; totMin += ete; val pass = passages.firstOrNull { it.index == index }
        val passInstant = pass?.actualZulu?.let { parseZuluToday(it) }
        val gs = if (passInstant != null && dep != null) { val elapsedH = ((passInstant.toEpochMilli() - dep.toEpochMilli()) / 3600000.0).coerceAtLeast(0.0001); (totNm / elapsedH).roundToInt() } else null
        NavRow(b.name, brg, d, totNm, ete, dep?.plusSeconds(totMin*60L)?.let { zuluFormatter.format(it) }, pass?.actualZulu, gs, passInstant != null)
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
