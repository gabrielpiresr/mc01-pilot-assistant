package com.betpass.mc01pilot.route

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.w3c.dom.Element
import java.io.StringReader
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.*

private val zuluFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm'Z'").withZone(ZoneOffset.UTC)

data class RoutePlan(val id:String,val title:String,val createdAt:Long,val cruiseSpeedKt:Int,val cruisingAlt:String?,val departureId:String?,val destinationId:String?,val waypoints:List<RouteWaypoint>)
data class RouteWaypoint(val name:String,val lat:Double,val lon:Double,val altitudeFt:Int?)

private class RouteRepository(context: Context) {
    private val prefs = context.getSharedPreferences("route_plans", Context.MODE_PRIVATE)
    private val gson = Gson(); private val key = "plans"; private val listType = object: TypeToken<List<RoutePlan>>() {}.type
    fun loadAll(): List<RoutePlan> = gson.fromJson(prefs.getString(key, "[]"), listType) ?: emptyList()
    fun save(plan: RoutePlan) { val current = loadAll().filterNot { it.id == plan.id }; prefs.edit().putString(key, gson.toJson((current + plan).sortedByDescending { it.createdAt })).apply() }
}

@Composable
fun RouteModule(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repository = remember { RouteRepository(context) }
    val plans = remember { mutableStateListOf<RoutePlan>().also { it.addAll(repository.loadAll()) } }
    var selected by remember { mutableStateOf(plans.firstOrNull()) }
    var departureZulu by remember { mutableStateOf("") }
    var cruiseKt by remember { mutableStateOf((selected?.cruiseSpeedKt ?: 95).toString()) }
    var routeMenuExpanded by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val parsed = runCatching { parsePln(context, uri) }.getOrNull() ?: return@rememberLauncherForActivityResult
        repository.save(parsed); plans.removeAll { it.id == parsed.id }; plans.add(0, parsed); selected = parsed; cruiseKt = parsed.cruiseSpeedKt.toString()
    }
    val active = selected
    val rows = active?.let { computeRows(it, cruiseKt.toIntOrNull()?.coerceAtLeast(40) ?: 95, departureZulu) }.orEmpty()

    Column(modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Rota", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { picker.launch(arrayOf("text/xml", "application/xml", "*/*")) }) { Text("Importar .pln") }
            TextButton(onClick = { routeMenuExpanded = true }, enabled = plans.isNotEmpty()) { Text(active?.title ?: "Selecionar rota") }
            DropdownMenu(expanded = routeMenuExpanded, onDismissRequest = { routeMenuExpanded = false }) {
                plans.forEach { route -> DropdownMenuItem(text = { Text(route.title) }, onClick = { selected = route; cruiseKt = route.cruiseSpeedKt.toString(); routeMenuExpanded = false }) }
            }
        }
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Header", fontWeight = FontWeight.SemiBold)
            Text("${active?.departureId ?: "---"} → ${active?.destinationId ?: "---"} | ALT/FL ${active?.cruisingAlt ?: "---"}")
            OutlinedTextField(value = departureZulu, onValueChange = { departureZulu = it }, label = { Text("Hora partida (HH:mmZ)") })
            OutlinedTextField(value = cruiseKt, onValueChange = { cruiseKt = it.filter(Char::isDigit) }, label = { Text("Velocidade cruzeiro (kt)") })
        } }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f, true)) {
            itemsIndexed(rows) { idx, row ->
                val color = when { row.isCompleted -> Color(0xFFDFF5E3); idx == 0 -> Color(0xFFE7F1FF); idx == 1 -> Color(0xFFF5F5F5); else -> Color.Transparent }
                Card(Modifier.fillMaxWidth().background(color)) { Column(Modifier.padding(8.dp)) {
                    Text("${row.name} • Proa ${row.bearing.roundToInt()}° • ${"%.1f".format(row.legNm)} NM")
                    Text("Acum ${"%.1f".format(row.totalNm)} NM | ETE ${row.eteMin} min | ETA ${row.eta ?: "--:--Z"}")
                } }
            }
        }
    }
}

data class NavRow(val name:String,val bearing:Double,val legNm:Double,val totalNm:Double,val eteMin:Int,val eta:String?,val isCompleted:Boolean=false)

private fun computeRows(plan: RoutePlan, cruiseKt: Int, departureZulu: String): List<NavRow> {
    if (plan.waypoints.size < 2) return emptyList(); val dep = parseZuluToday(departureZulu); var totNm = 0.0; var totMin = 0
    return plan.waypoints.zipWithNext().map { (a,b) ->
        val d = haversineNm(a.lat,a.lon,b.lat,b.lon); val brg = bearingDeg(a.lat,a.lon,b.lat,b.lon); val ete = ((d / cruiseKt) * 60.0).roundToInt().coerceAtLeast(1)
        totNm += d; totMin += ete; NavRow(b.name, brg, d, totNm, ete, dep?.plusSeconds(totMin*60L)?.let { zuluFormatter.format(it) })
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
private fun bearingDeg(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double { val y=sin(Math.toRadians(lon2-lon1))*cos(Math.toRadians(lat2)); val x=cos(Math.toRadians(lat1))*sin(Math.toRadians(lat2))-sin(Math.toRadians(lat1))*cos(Math.toRadians(lat2))*cos(Math.toRadians(lon2-lon1)); return (Math.toDegrees(atan2(y,x))+360)%360 }
