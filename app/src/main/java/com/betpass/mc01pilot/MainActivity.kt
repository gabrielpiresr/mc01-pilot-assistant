package com.betpass.mc01pilot

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.viewinterop.AndroidView
import com.betpass.mc01pilot.ui.DrawingPad
import com.betpass.mc01pilot.ui.theme.MC01Theme
import com.betpass.mc01pilot.ui.workspace.MultiPanelWorkspace
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.betpass.mc01pilot.data.*
import java.text.DateFormat
import java.text.Normalizer
import java.util.Locale
import android.graphics.pdf.PdfRenderer
import androidx.compose.ui.layout.ContentScale
import com.betpass.mc01pilot.airport.ui.AirportDetailsModule
import com.betpass.mc01pilot.ai.AiSearchScreen
import com.betpass.mc01pilot.route.RouteModule
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MC01App() }
    }
}

private enum class RootDestination { HOME, AIRPORT_DETAILS, DURING_FLIGHT, AI_SEARCH }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MC01App() {
    var destination by rememberSaveable { mutableStateOf(RootDestination.HOME) }
    MC01Theme {
        when (destination) {
            RootDestination.HOME -> HomeMenuScreen(
                onOpenAirportDetails = { destination = RootDestination.AIRPORT_DETAILS },
                onOpenDuringFlight = { destination = RootDestination.DURING_FLIGHT },
                onOpenAiSearch = { destination = RootDestination.AI_SEARCH }
            )

            RootDestination.AIRPORT_DETAILS -> Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Detalhes do Aeroporto") },
                        navigationIcon = {
                            IconButton(onClick = { destination = RootDestination.HOME }) {
                                Icon(Icons.Default.Home, contentDescription = "Voltar para menu inicial")
                            }
                        }
                    )
                }
            ) { pad ->
                AirportDetailsModule(Modifier.padding(pad).fillMaxSize())
            }



            RootDestination.AI_SEARCH -> Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Busca IA MC01") },
                        navigationIcon = {
                            IconButton(onClick = { destination = RootDestination.HOME }) {
                                Icon(Icons.Default.Home, contentDescription = "Voltar para menu inicial")
                            }
                        }
                    )
                }
            ) { pad ->
                AiSearchScreen(modifier = Modifier.padding(pad).fillMaxSize())
            }

                        RootDestination.DURING_FLIGHT -> Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Durante o Voo") },
                        navigationIcon = {
                            IconButton(onClick = { destination = RootDestination.HOME }) {
                                Icon(Icons.Default.Home, contentDescription = "Voltar para menu inicial")
                            }
                        }
                    )
                }
            ) { pad ->
                MultiPanelWorkspace(modifier = Modifier.padding(pad).fillMaxSize()) { module, mod ->
                    ModuleContent(module, mod.fillMaxSize())
                }
            }
        }
    }
}

@Composable
private fun HomeMenuScreen(
    onOpenAirportDetails: () -> Unit,
    onOpenDuringFlight: () -> Unit,
    onOpenAiSearch: () -> Unit
) {
    Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.widthIn(max = 980.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("MC01 Pilot Assistant", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Selecione o modo de operação", style = MaterialTheme.typography.bodyLarge)
            HomeEntryCard(
                title = "Detalhes do Aeroporto",
                subtitle = "Consulta pré-voo, NOTAM, METAR, TAF, cartas e frequências",
                onClick = onOpenAirportDetails
            )
            HomeEntryCard(
                title = "Durante o Voo",
                subtitle = "Checklists, cartas, documentos e anotações",
                onClick = onOpenDuringFlight
            )
            HomeEntryCard(
                title = "Busca IA MC01",
                subtitle = "Resumo curto com trechos e fonte dos documentos operacionais",
                onClick = onOpenAiSearch
            )
        }
    }
}

@Composable
private fun HomeEntryCard(title: String, subtitle: String, onClick: () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().height(140.dp).clickable(onClick = onClick)
    ) {
        Column(
            Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable fun Pane(module: Module, onModule: (Module) -> Unit, modifier: Modifier) {
    Column(modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        ModulePicker(module, onModule)
        ModuleContent(module, Modifier.weight(1f).fillMaxWidth())
    }
}

@Composable fun ModulePicker(current: Module, onPick: (Module) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Module.entries.forEach { m ->
            FilterChip(selected = current == m, onClick = { onPick(m) }, label = { Text(m.label()) })
        }
    }
}
fun Module.label() = when (this) {
    Module.CHECKLISTS -> "Checklists"
    Module.EMERGENCY -> "Emergência"
    Module.CHARTS -> "Cartas"
    Module.DOCUMENTS -> "Documentos"
    Module.NOTES -> "Anotações"
    Module.WEIGHT_BALANCE -> "Peso e Balanceamento"
    Module.ROUTE -> "Rota"
}

@Composable
fun ModuleContent(module: Module, modifier: Modifier) = Box(modifier.padding(10.dp)) {
    when (module) {
        Module.CHECKLISTS -> ChecklistScreen(modifier = modifier)
        Module.EMERGENCY -> EmergencyScreen(modifier = modifier)
        Module.CHARTS -> FileLibraryScreen(type = "chart", title = "Cartas", modifier = modifier)
        Module.DOCUMENTS -> FileLibraryScreen(type = "document", title = "Documentos", modifier = modifier)
        Module.NOTES -> NotesScreen(modifier = modifier)
        Module.WEIGHT_BALANCE -> WeightAndBalanceScreen(modifier = modifier)
        Module.ROUTE -> RouteModule(modifier = modifier)
    }
}

private enum class FuelUnit { LITERS, KG }

private object WeightBalanceConstants {
    const val EMPTY_WEIGHT_KG = 398.80
    const val EMPTY_XCG_MM = 2166.45
    const val EMPTY_MOMENT_KG_MM = 863980.0
    const val FUEL_ARM_MM = 2180.0
    const val OCCUPANTS_ARM_MM = 2180.0
    const val BAGGAGE_ARM_MM = 3000.0
    const val FUEL_DENSITY_KG_PER_L = 0.72
    const val MAX_FUEL_LITERS = 140.0
    const val MAX_FUEL_KG = 100.8
    const val MAX_BAGGAGE_KG = 30.0
    const val MAX_TAKEOFF_WEIGHT_KG = 600.0
    const val MIN_CG_MM = 2094.0
    const val MAX_CG_MM = 2224.0
    const val MIN_CG_PERCENT_CMA = 16.73
    const val MAX_CG_PERCENT_CMA = 26.07
}

private data class WeightBalanceResult(
    val fuelKg: Double,
    val occupantsKg: Double,
    val fuelMoment: Double,
    val occupantsMoment: Double,
    val baggageMoment: Double,
    val totalWeight: Double,
    val totalMoment: Double,
    val xcg: Double,
    val isFuelOk: Boolean,
    val isBaggageOk: Boolean,
    val isWeightOk: Boolean,
    val isCgOk: Boolean
) {
    val isSafe: Boolean get() = isFuelOk && isBaggageOk && isWeightOk && isCgOk
}

private fun calculateWeightBalance(
    fuelInput: Double,
    fuelUnit: FuelUnit,
    pilotKg: Double,
    passengerKg: Double,
    baggageKg: Double
): WeightBalanceResult {
    val occupantsKg = pilotKg + passengerKg
    val fuelKg = if (fuelUnit == FuelUnit.LITERS) fuelInput * WeightBalanceConstants.FUEL_DENSITY_KG_PER_L else fuelInput
    val fuelMoment = fuelKg * WeightBalanceConstants.FUEL_ARM_MM
    val occupantsMoment = occupantsKg * WeightBalanceConstants.OCCUPANTS_ARM_MM
    val baggageMoment = baggageKg * WeightBalanceConstants.BAGGAGE_ARM_MM
    val totalWeight = WeightBalanceConstants.EMPTY_WEIGHT_KG + fuelKg + occupantsKg + baggageKg
    val totalMoment = WeightBalanceConstants.EMPTY_MOMENT_KG_MM + fuelMoment + occupantsMoment + baggageMoment
    val xcg = if (totalWeight > 0.0) totalMoment / totalWeight else 0.0
    val isFuelOk = fuelKg <= WeightBalanceConstants.MAX_FUEL_KG &&
        (fuelUnit != FuelUnit.LITERS || fuelInput <= WeightBalanceConstants.MAX_FUEL_LITERS)
    val isBaggageOk = baggageKg <= WeightBalanceConstants.MAX_BAGGAGE_KG
    val isWeightOk = totalWeight <= WeightBalanceConstants.MAX_TAKEOFF_WEIGHT_KG
    val isCgOk = xcg in WeightBalanceConstants.MIN_CG_MM..WeightBalanceConstants.MAX_CG_MM
    return WeightBalanceResult(
        fuelKg = fuelKg,
        occupantsKg = occupantsKg,
        fuelMoment = fuelMoment,
        occupantsMoment = occupantsMoment,
        baggageMoment = baggageMoment,
        totalWeight = totalWeight,
        totalMoment = totalMoment,
        xcg = xcg,
        isFuelOk = isFuelOk,
        isBaggageOk = isBaggageOk,
        isWeightOk = isWeightOk,
        isCgOk = isCgOk
    )
}

private fun parseInput(text: String): Double = text.replace(",", ".").toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0

private fun Double.fmt(decimals: Int): String =
    String.format(Locale.US, "%.${decimals}f", this).replace(".", ",")

@Composable
private fun WeightAndBalanceScreen(modifier: Modifier = Modifier) {
    var fuelUnit by rememberSaveable { mutableStateOf(FuelUnit.LITERS) }
    var fuelInputText by rememberSaveable { mutableStateOf("0") }
    var pilotText by rememberSaveable { mutableStateOf("0") }
    var passengerText by rememberSaveable { mutableStateOf("0") }
    var baggageText by rememberSaveable { mutableStateOf("0") }

    val fuelInput = parseInput(fuelInputText)
    val pilotKg = parseInput(pilotText)
    val passengerKg = parseInput(passengerText)
    val baggageKg = parseInput(baggageText)
    val result = remember(fuelInput, fuelUnit, pilotKg, passengerKg, baggageKg) {
        calculateWeightBalance(fuelInput, fuelUnit, pilotKg, passengerKg, baggageKg)
    }

    val alertColor = MaterialTheme.colorScheme.error
    val safeColor = Color(0xFF2E7D32)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("Peso e Balanceamento", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Entradas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = fuelUnit == FuelUnit.LITERS,
                            onClick = { fuelUnit = FuelUnit.LITERS },
                            label = { Text("Combustível (L)") }
                        )
                        FilterChip(
                            selected = fuelUnit == FuelUnit.KG,
                            onClick = { fuelUnit = FuelUnit.KG },
                            label = { Text("Combustível (kg)") }
                        )
                    }
                    OutlinedTextField(
                        value = fuelInputText,
                        onValueChange = { fuelInputText = it },
                        label = { Text(if (fuelUnit == FuelUnit.LITERS) "Combustível (L)" else "Combustível (kg)") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = pilotText,
                            onValueChange = { pilotText = it },
                            label = { Text("Piloto (kg)") },
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = passengerText,
                            onValueChange = { passengerText = it },
                            label = { Text("Passageiro (kg)") },
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = baggageText,
                        onValueChange = { baggageText = it },
                        label = { Text("Bagageiro (kg)") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryCard(
                    title = "Peso Total",
                    value = "${result.totalWeight.fmt(1)} kg",
                    subtitle = "Máx: ${WeightBalanceConstants.MAX_TAKEOFF_WEIGHT_KG.fmt(0)} kg",
                    isOk = result.isWeightOk,
                    modifier = Modifier.weight(1f)
                )
                SummaryCard(
                    title = "XCG",
                    value = "${result.xcg.fmt(0)} mm",
                    subtitle = "${WeightBalanceConstants.MIN_CG_MM.fmt(0)}–${WeightBalanceConstants.MAX_CG_MM.fmt(0)} mm",
                    isOk = result.isCgOk,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item {
            ElevatedCard(Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(
                containerColor = if (result.isSafe) safeColor.copy(alpha = 0.2f) else alertColor.copy(alpha = 0.2f)
            )) {
                Text(
                    text = if (result.isSafe) "DENTRO DOS LIMITES" else "FORA DOS LIMITES",
                    color = if (result.isSafe) safeColor else alertColor,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
        item {
            CgSimpleChart(result = result, modifier = Modifier.fillMaxWidth())
        }
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Detalhamento", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Peso vazio: ${WeightBalanceConstants.EMPTY_WEIGHT_KG.fmt(2)} kg | XCG vazio: ${WeightBalanceConstants.EMPTY_XCG_MM.fmt(2)} mm")
                    Text("Combustível: ${result.fuelKg.fmt(1)} kg")
                    Text("Ocupantes: ${result.occupantsKg.fmt(1)} kg")
                    Text("Bagagem: ${baggageKg.fmt(1)} kg")
                    Text("Momento total: ${result.totalMoment.fmt(1)} kg·mm")
                    Text("Faixa CG (%CMA): ${WeightBalanceConstants.MIN_CG_PERCENT_CMA.fmt(2)}%–${WeightBalanceConstants.MAX_CG_PERCENT_CMA.fmt(2)}%")
                }
            }
        }
        item {
            val warnings = buildList {
                if (!result.isFuelOk) add("Combustível excede ${WeightBalanceConstants.MAX_FUEL_LITERS.fmt(0)} L / ${WeightBalanceConstants.MAX_FUEL_KG.fmt(1)} kg.")
                if (!result.isBaggageOk) add("Bagagem excede ${WeightBalanceConstants.MAX_BAGGAGE_KG.fmt(0)} kg.")
                if (!result.isWeightOk) add("Peso total excede ${WeightBalanceConstants.MAX_TAKEOFF_WEIGHT_KG.fmt(0)} kg.")
                if (!result.isCgOk) add("XCG fora da faixa ${WeightBalanceConstants.MIN_CG_MM.fmt(0)}–${WeightBalanceConstants.MAX_CG_MM.fmt(0)} mm.")
            }
            if (warnings.isNotEmpty()) {
                ElevatedCard(Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = alertColor.copy(alpha = 0.2f))) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Alertas", color = alertColor, fontWeight = FontWeight.Bold)
                        warnings.forEach { Text("• $it", color = alertColor) }
                    }
                }
            }
        }
        item {
            Text(
                "Cálculo auxiliar. Sempre confirme com a ficha oficial de Peso e Balanceamento da aeronave.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary
            )
        }
    }
}

@Composable
private fun SummaryCard(title: String, value: String, subtitle: String, isOk: Boolean, modifier: Modifier = Modifier) {
    val statusColor = if (isOk) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
    ElevatedCard(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(value, style = MaterialTheme.typography.headlineMedium, color = statusColor, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun CgSimpleChart(result: WeightBalanceResult, modifier: Modifier = Modifier) {
    val errorColor = MaterialTheme.colorScheme.error
    ElevatedCard(modifier) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Gráfico de CG", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
            ) {
                val minX = WeightBalanceConstants.MIN_CG_MM.toFloat()
                val maxX = WeightBalanceConstants.MAX_CG_MM.toFloat()
                val current = result.xcg.toFloat()
                val startX = size.width * 0.1f
                val endX = size.width * 0.9f
                val y = size.height * 0.55f
                drawLine(color = Color.Gray, start = androidx.compose.ui.geometry.Offset(startX, y), end = androidx.compose.ui.geometry.Offset(endX, y), strokeWidth = 8f)
                val clamped = current.coerceIn(minX, maxX)
                val pointX = startX + ((clamped - minX) / (maxX - minX)) * (endX - startX)
                val pointColor = if (result.isCgOk) Color(0xFF2E7D32) else errorColor
                drawCircle(color = pointColor, radius = 14f, center = androidx.compose.ui.geometry.Offset(pointX, y))
            }
            Text("CG dianteiro ${WeightBalanceConstants.MIN_CG_MM.fmt(0)} mm — CG traseiro ${WeightBalanceConstants.MAX_CG_MM.fmt(0)} mm")
            Text("CG atual: ${result.xcg.fmt(1)} mm (${if (result.isCgOk) "dentro" else "fora"} da faixa)")
            Text("Peso total: ${result.totalWeight.fmt(1)} / ${WeightBalanceConstants.MAX_TAKEOFF_WEIGHT_KG.fmt(0)} kg")
        }
    }
}

@Composable fun ChecklistScreen(
    modifier: Modifier = Modifier,
    assetPath: String = "checklists/mc01_checklist.json",
    favoritesKey: String = "favorite_category_ids"
) {
    val ctx = LocalContext.current
    val repo = remember(assetPath, favoritesKey) { ChecklistRepository(ctx, assetPath, favoritesKey) }
    val checklist = remember(repo) { repo.load() }
    val checklistGroups = remember(checklist) {
        checklist.checklists.ifEmpty {
            checklist.categories.map { category ->
                ChecklistGroup(
                    id = category.id,
                    title = category.title,
                    sections = listOf(ChecklistSection(id = "${category.id}_items", title = "", items = category.items))
                )
            }
        }
    }
    val defaultChecklistId = checklistGroups.firstOrNull()?.id.orEmpty()
    var selectedChecklistId by rememberSaveable(assetPath) { mutableStateOf(defaultChecklistId) }
    var favoriteChecklistIds by rememberSaveable(assetPath) { mutableStateOf(repo.loadFavoriteCategoryIds()) }
    var checked by rememberSaveable(assetPath) { mutableStateOf(setOf<String>()) }
    var selectorOpen by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val checklists = remember(checklistGroups) { checklistGroups }
    val checklistIndex = checklists.indexOfFirst { it.id == selectedChecklistId }.coerceAtLeast(0)
    val selectedChecklist = checklists.getOrNull(checklistIndex)
    val favoriteChecklists = remember(checklists, favoriteChecklistIds) { checklists.filter { it.id in favoriteChecklistIds } }
    val allItems = remember(selectedChecklist) {
        selectedChecklist?.sections?.flatMap { it.items }.orEmpty()
    }

    if (selectedChecklistId.isBlank() && defaultChecklistId.isNotBlank()) {
        selectedChecklistId = defaultChecklistId
    }

    if (selectedChecklist == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Nenhum checklist disponível.")
        }
        return
    }

    fun toggleFavorite(checklistId: String) {
        favoriteChecklistIds = if (checklistId in favoriteChecklistIds) favoriteChecklistIds - checklistId else favoriteChecklistIds + checklistId
        repo.saveFavoriteCategoryIds(favoriteChecklistIds)
    }

    Column(modifier.fillMaxSize()) {
        Box {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${checklist.aircraft} • ${selectedChecklist.title}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { selectorOpen = true }
                        .padding(4.dp)
                )
                IconButton(onClick = { toggleFavorite(selectedChecklist.id) }) {
                    Icon(
                        imageVector = if (selectedChecklist.id in favoriteChecklistIds) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = if (selectedChecklist.id in favoriteChecklistIds) "Remover dos favoritos" else "Adicionar aos favoritos"
                    )
                }
            }
            DropdownMenu(expanded = selectorOpen, onDismissRequest = { selectorOpen = false }) {
                checklists.forEach { checklistOption ->
                    DropdownMenuItem(
                        text = { Text(checklistOption.title) },
                        trailingIcon = {
                            if (checklistOption.id in favoriteChecklistIds) Icon(Icons.Default.Star, contentDescription = null)
                        },
                        onClick = {
                            selectedChecklistId = checklistOption.id
                            checked = emptySet()
                            selectorOpen = false
                        }
                    )
                }
            }
        }
        if (favoriteChecklists.isNotEmpty()) {
            Text("Favoritos", style = MaterialTheme.typography.labelLarge)
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(favoriteChecklists) { favorite ->
                    FilterChip(
                        selected = favorite.id == selectedChecklist.id,
                        onClick = {
                            selectedChecklistId = favorite.id
                            checked = emptySet()
                        },
                        label = { Text(favorite.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = { Icon(Icons.Default.Star, contentDescription = null) }
                    )
                }
            }
        }
        checklist.source_note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary) }
        LinearProgressIndicator(progress = { checked.size.toFloat() / allItems.size.coerceAtLeast(1) }, Modifier.fillMaxWidth().padding(vertical = 8.dp))
        Box(Modifier.weight(1f)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(end = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                selectedChecklist.sections.forEach { section ->
                    if (section.title.isNotBlank()) {
                        item(key = "section_${section.id}") {
                            Text(section.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                    items(section.items) { item ->
                        val key = "${selectedChecklist.id}_${section.id}_${item.label}"
                        ElevatedCard(Modifier.fillMaxWidth().clickable { checked = if (key in checked) checked - key else checked + key }) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = key in checked, onCheckedChange = { checked = if (it) checked + key else checked - key })
                                Spacer(Modifier.width(8.dp))
                                Column { Text(item.label, fontWeight = FontWeight.SemiBold); Text(item.action) }
                            }
                        }
                    }
                }
            }
            ScrollIndicator(listState = listState, modifier = Modifier.align(Alignment.CenterEnd))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(
                enabled = checklistIndex > 0,
                onClick = {
                    selectedChecklistId = checklists[checklistIndex - 1].id
                    checked = emptySet()
                }
            ) { Icon(Icons.Default.ArrowBack, null); Text("Anterior") }
            Button(
                onClick = {
                    checked = selectedChecklist.sections
                        .flatMap { section -> section.items.map { item -> "${selectedChecklist.id}_${section.id}_${item.label}" } }
                        .toSet()
                }
            ) { Text("Marcar tudo") }
            Button(
                enabled = checklistIndex < checklists.lastIndex,
                onClick = {
                    selectedChecklistId = checklists[checklistIndex + 1].id
                    checked = emptySet()
                }
            ) { Text("Próximo"); Icon(Icons.Default.ArrowForward, null) }
        }
    }
}


@Composable
fun EmergencyScreen(modifier: Modifier = Modifier) {
    ChecklistScreen(
        modifier = modifier,
        assetPath = "checklists/mc01_emergency.json",
        favoritesKey = "favorite_emergency_ids"
    )
}
@Composable
private fun ScrollIndicator(listState: androidx.compose.foundation.lazy.LazyListState, modifier: Modifier = Modifier) {
    val layoutInfo = listState.layoutInfo
    val totalItems = layoutInfo.totalItemsCount
    val visibleItems = layoutInfo.visibleItemsInfo
    if (totalItems <= visibleItems.size || visibleItems.isEmpty()) return

    val firstVisible = listState.firstVisibleItemIndex
    val proportion = (visibleItems.size.toFloat() / totalItems.toFloat()).coerceIn(0.08f, 1f)
    val offset = (firstVisible.toFloat() / totalItems.toFloat()).coerceIn(0f, 1f - proportion)
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val thumbColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
    Canvas(
        modifier = modifier
            .fillMaxHeight()
            .width(5.dp)
    ) {
        drawRoundRect(
            color = trackColor,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(x = 100f, y = 100f)
        )
        val barHeight = size.height * proportion
        val startY = size.height * offset
        drawRoundRect(
            color = thumbColor,
            topLeft = androidx.compose.ui.geometry.Offset(0f, startY),
            size = androidx.compose.ui.geometry.Size(size.width, barHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(x = 100f, y = 100f)
        )
    }
}

@Composable fun FileLibraryScreen(type: String, title: String, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val repo = remember { LibraryRepository(ctx) }
    var items by remember(type) { mutableStateOf(repo.list(type)) }
    var currentFolderId by rememberSaveable(type) { mutableStateOf<String?>(null) }
    var selectedDocumentId by rememberSaveable(type) { mutableStateOf<String?>(null) }
    var isPreviewExpanded by rememberSaveable(type) { mutableStateOf(false) }
    var searchQuery by rememberSaveable(type) { mutableStateOf("") }
    var searchGlobal by rememberSaveable(type) { mutableStateOf(false) }
    var showCreateMenu by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showCreateFileDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<StoredFile?>(null) }
    var deleteTarget by remember { mutableStateOf<StoredFile?>(null) }
    var renameValue by rememberSaveable(type) { mutableStateOf("") }
    var renameError by rememberSaveable(type) { mutableStateOf<String?>(null) }
    var createName by rememberSaveable(type) { mutableStateOf("") }
    var createError by rememberSaveable(type) { mutableStateOf<String?>(null) }
    val listState = rememberSaveable(type, saver = androidx.compose.foundation.lazy.LazyListState.Saver) {
        androidx.compose.foundation.lazy.LazyListState()
    }

    fun refresh() {
        items = repo.list(type)
    }

    val selectedDocument = items.firstOrNull { it.id == selectedDocumentId && !it.isFolder }
    val folderMap = remember(items) { items.associateBy { it.id } }
    val currentItems = remember(items, currentFolderId) { items.filter { it.parentId == currentFolderId } }
    val normalizedQuery = remember(searchQuery) { normalizeText(searchQuery) }
    val displayedItems = remember(items, currentItems, normalizedQuery, searchGlobal) {
        val base = if (normalizedQuery.isBlank()) currentItems else if (searchGlobal) items else currentItems
        base.filter { normalizeText(it.name).contains(normalizedQuery) }
            .sortedWith(compareBy<StoredFile>({ !it.isFolder }, { it.name.lowercase() }))
    }

    val breadcrumbs = remember(currentFolderId, folderMap) {
        val chain = mutableListOf<StoredFile>()
        var cursor = currentFolderId
        while (cursor != null) {
            val folder = folderMap[cursor] ?: break
            chain.add(folder)
            cursor = folder.parentId
        }
        chain.reversed()
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            val name = runCatching {
                ctx.contentResolver.query(it, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            }.getOrNull() ?: (it.lastPathSegment?.substringAfterLast('/') ?: "arquivo")
            repo.addImported(name = name, parentId = currentFolderId, uri = it, type = type)
            refresh()
        }
    }

    val isWideScreen = with(LocalConfiguration.current) { screenWidthDp >= 900 }
    val showInlinePreview = isWideScreen && selectedDocument != null && !isPreviewExpanded

    BackHandler(enabled = isPreviewExpanded || currentFolderId != null) {
        when {
            isPreviewExpanded -> isPreviewExpanded = false
            currentFolderId != null -> {
                currentFolderId = folderMap[currentFolderId]?.parentId
                selectedDocumentId = null
            }
        }
    }

    Scaffold(
        floatingActionButton = {
            Box {
                FloatingActionButton(onClick = { showCreateMenu = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Criar")
                }
                DropdownMenu(expanded = showCreateMenu, onDismissRequest = { showCreateMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Nova pasta") },
                        leadingIcon = { Icon(Icons.Default.CreateNewFolder, null) },
                        onClick = {
                            showCreateMenu = false
                            createName = ""
                            createError = null
                            showCreateFolderDialog = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Novo arquivo") },
                        leadingIcon = { Icon(Icons.Default.NoteAdd, null) },
                        onClick = {
                            showCreateMenu = false
                            createName = ""
                            createError = null
                            showCreateFileDialog = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Importar arquivo") },
                        leadingIcon = { Icon(Icons.Default.UploadFile, null) },
                        onClick = {
                            showCreateMenu = false
                            picker.launch(arrayOf("application/pdf", "image/*", "text/*"))
                        }
                    )
                }
            }
        }
    ) { padding ->
        BoxWithConstraints(modifier.fillMaxSize().padding(padding)) {
            val paneWidth = maxWidth
            if (isPreviewExpanded && selectedDocument != null) {
                FullScreenPreview(
                    file = selectedDocument,
                    onClose = { isPreviewExpanded = false },
                    modifier = Modifier.fillMaxSize()
                )
            } else if (paneWidth < 760.dp || !showInlinePreview) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DocumentBrowserPane(
                        title = title,
                        items = displayedItems,
                        currentFolderId = currentFolderId,
                        breadcrumbs = breadcrumbs,
                        searchQuery = searchQuery,
                        searchGlobal = searchGlobal,
                        onSearchChange = { searchQuery = it },
                        onSearchGlobalChange = { searchGlobal = it },
                        onNavigateToRoot = {
                            currentFolderId = null
                            selectedDocumentId = null
                        },
                        onNavigateToFolder = { folderId ->
                            currentFolderId = folderId
                            selectedDocumentId = null
                        },
                        onItemClick = { item ->
                            if (item.isFolder) {
                                currentFolderId = item.id
                                selectedDocumentId = null
                            } else {
                                selectedDocumentId = item.id
                            }
                        },
                        onDelete = { item -> deleteTarget = item },
                        onRename = { item ->
                            renameTarget = item
                            renameValue = item.name
                            renameError = null
                        },
                        selectedDocumentId = selectedDocumentId,
                        listState = listState,
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )
                    if (selectedDocument != null && (paneWidth < 760.dp || !isWideScreen)) {
                        ElevatedCard(Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 420.dp)) {
                            PreviewFileCard(
                                file = selectedDocument,
                                modifier = Modifier.fillMaxSize(),
                                onExpand = { isPreviewExpanded = true },
                                onClose = { selectedDocumentId = null }
                            )
                        }
                    }
                }
            } else {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DocumentBrowserPane(
                        title = title,
                        items = displayedItems,
                        currentFolderId = currentFolderId,
                        breadcrumbs = breadcrumbs,
                        searchQuery = searchQuery,
                        searchGlobal = searchGlobal,
                        onSearchChange = { searchQuery = it },
                        onSearchGlobalChange = { searchGlobal = it },
                        onNavigateToRoot = {
                            currentFolderId = null
                            selectedDocumentId = null
                        },
                        onNavigateToFolder = { folderId ->
                            currentFolderId = folderId
                            selectedDocumentId = null
                        },
                        onItemClick = { item ->
                            if (item.isFolder) {
                                currentFolderId = item.id
                                selectedDocumentId = null
                            } else {
                                selectedDocumentId = item.id
                            }
                        },
                        onDelete = { item -> deleteTarget = item },
                        onRename = { item ->
                            renameTarget = item
                            renameValue = item.name
                            renameError = null
                        },
                        selectedDocumentId = selectedDocumentId,
                        listState = listState,
                        modifier = Modifier.fillMaxHeight().weight(.35f)
                    )
                    ElevatedCard(Modifier.fillMaxHeight().weight(.65f)) {
                        selectedDocument?.let {
                            PreviewFileCard(
                                file = it,
                                modifier = Modifier.fillMaxSize(),
                                onExpand = { isPreviewExpanded = true },
                                onClose = { selectedDocumentId = null }
                            )
                        } ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Selecione um documento para visualizar.")
                        }
                    }
                }
            }
        }
    }

    if (showCreateFolderDialog || showCreateFileDialog) {
        val creatingFolder = showCreateFolderDialog
        AlertDialog(
            onDismissRequest = {
                showCreateFolderDialog = false
                showCreateFileDialog = false
            },
            title = { Text(if (creatingFolder) "Nova pasta" else "Novo arquivo") },
            text = {
                Column {
                    OutlinedTextField(
                        value = createName,
                        onValueChange = {
                            createName = it
                            createError = null
                        },
                        label = { Text("Nome") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    createError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val safeName = createName.trim()
                    when {
                        safeName.isBlank() -> createError = "Informe um nome válido."
                        repo.nameExists(type, currentFolderId, safeName) -> createError = "Já existe um item com esse nome nesta pasta."
                        creatingFolder -> {
                            repo.createFolder(type, safeName, currentFolderId)
                            refresh()
                            showCreateFolderDialog = false
                        }
                        else -> {
                            repo.createPlaceholderFile(type, safeName, currentFolderId)
                            refresh()
                            showCreateFileDialog = false
                        }
                    }
                }) { Text("Criar") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCreateFolderDialog = false
                    showCreateFileDialog = false
                }) { Text("Cancelar") }
            }
        )
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(if (target.isFolder) "Renomear pasta" else "Renomear arquivo") },
            text = {
                Column {
                    OutlinedTextField(
                        value = renameValue,
                        onValueChange = {
                            renameValue = it
                            renameError = null
                        },
                        label = { Text("Novo nome") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    renameError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val safeName = renameValue.trim()
                    when {
                        safeName.isBlank() -> renameError = "Informe um nome válido."
                        !repo.rename(target.id, safeName) -> renameError = "Já existe um item com esse nome nesta pasta."
                        else -> {
                            refresh()
                            renameTarget = null
                        }
                    }
                }) { Text("Salvar") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancelar") }
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(if (target.isFolder) "Excluir pasta?" else "Excluir arquivo?") },
            text = {
                Text(
                    if (target.isFolder) {
                        "Tem certeza que deseja excluir a pasta \"${target.name}\"? Esta ação não pode ser desfeita."
                    } else {
                        "Tem certeza que deseja excluir o arquivo \"${target.name}\"? Esta ação não pode ser desfeita."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (selectedDocumentId == target.id) selectedDocumentId = null
                    repo.delete(target.id)
                    refresh()
                    deleteTarget = null
                }) { Text("Excluir") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
fun PreviewFileCard(
    file: StoredFile,
    modifier: Modifier = Modifier,
    onExpand: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    showOnlyActionButtons: Boolean = false
) {
    val ctx = LocalContext.current
    val uri = remember(file.uri) { file.uri?.let(Uri::parse) }
    val mimeType = remember(file.uri, file.name) {
        uri?.let { ctx.contentResolver.getType(it).orEmpty() }.orEmpty()
    }
    val previewText = remember(file.id, mimeType) {
        if (mimeType.startsWith("text") && uri != null) {
            runCatching {
                ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
            }.getOrDefault("Não foi possível carregar o conteúdo de texto.")
        } else file.contentText.orEmpty()
    }
    val pdfPreview = remember(file.id, mimeType) {
        if (uri != null && (mimeType == "application/pdf" || file.name.lowercase().endsWith(".pdf"))) {
            runCatching {
                ctx.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        if (renderer.pageCount <= 0) return@use null
                        renderer.openPage(0).use { page ->
                            val bitmap = Bitmap.createBitmap(
                                (page.width * 1.4f).toInt().coerceAtLeast(1),
                                (page.height * 1.4f).toInt().coerceAtLeast(1),
                                Bitmap.Config.ARGB_8888
                            )
                            bitmap.eraseColor(android.graphics.Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bitmap
                        }
                    }
                }
            }.getOrNull()
        } else null
    }

    Column(if (showOnlyActionButtons) modifier.fillMaxSize() else modifier.padding(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (showOnlyActionButtons) {
                Spacer(Modifier.weight(1f))
            } else {
                Column(Modifier.weight(1f)) {
                    Text(file.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (file.isFolder) "Pasta" else "Arquivo", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (onExpand != null) IconButton(onClick = onExpand) { Icon(Icons.Default.OpenInFull, "Expandir") }
            if (onClose != null) IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Fechar") }
            if (uri != null) {
                IconButton(onClick = {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                }) { Icon(Icons.Default.OpenInNew, null) }
            }
        }
        Spacer(Modifier.height(if (showOnlyActionButtons) 2.dp else 4.dp))
        when {
            uri != null && mimeType.startsWith("image") -> ZoomableContainer(Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { android.widget.ImageView(it).apply { scaleType = android.widget.ImageView.ScaleType.FIT_CENTER } },
                    update = { it.setImageURI(uri) },
                    modifier = Modifier.fillMaxSize()
                )
            }
            mimeType.startsWith("text") || file.contentText != null -> ElevatedCard(Modifier.fillMaxSize()) {
                ZoomableContainer(Modifier.fillMaxSize().padding(2.dp)) {
                    LazyColumn(Modifier.fillMaxSize()) { item { Text(previewText) } }
                }
            }
            pdfPreview != null -> ElevatedCard(Modifier.fillMaxSize()) {
                ZoomableContainer(Modifier.fillMaxSize().padding(2.dp)) {
                    Image(
                        bitmap = pdfPreview.asImageBitmap(),
                        contentDescription = "Pré-visualização do PDF",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            else -> ElevatedCard(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (uri == null && file.contentText.isNullOrBlank()) "Este documento ainda não possui conteúdo" else "Preview indisponível para este tipo de arquivo")
                    Text("Tipo: ${mimeType.ifBlank { "desconhecido" }}")
                    Text("Adicionado em: ${DateFormat.getDateTimeInstance().format(file.createdAt)}")
                    if (uri != null) {
                        Button(onClick = {
                            ctx.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                        }) { Text("Abrir em tela cheia") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoomableContainer(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .clipToBounds()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    if (scale <= 1.02f) {
                        offsetX = 0f
                        offsetY = 0f
                    } else {
                        offsetX += pan.x
                        offsetY += pan.y
                    }
                }
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
        ) {
            content()
        }
    }
}

@Composable
private fun DocumentBrowserPane(
    title: String,
    items: List<StoredFile>,
    currentFolderId: String?,
    breadcrumbs: List<StoredFile>,
    searchQuery: String,
    searchGlobal: Boolean,
    onSearchChange: (String) -> Unit,
    onSearchGlobalChange: (Boolean) -> Unit,
    onNavigateToRoot: () -> Unit,
    onNavigateToFolder: (String) -> Unit,
    onItemClick: (StoredFile) -> Unit,
    onDelete: (StoredFile) -> Unit,
    onRename: (StoredFile) -> Unit,
    selectedDocumentId: String?,
    listState: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier = Modifier
) {
    ElevatedCard(modifier) {
        Column(Modifier.fillMaxSize().padding(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onNavigateToRoot, contentPadding = PaddingValues(horizontal = 4.dp)) { Text("Home") }
                breadcrumbs.forEach { folder ->
                    Text(" > ")
                    TextButton(
                        onClick = { onNavigateToFolder(folder.id) },
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) { Text(folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text("Buscar documentos...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { onSearchChange("") }) { Icon(Icons.Default.Clear, "Limpar") }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = searchGlobal, onCheckedChange = onSearchGlobalChange)
                Text("Buscar em todas as pastas")
            }
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            if (items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(if (searchQuery.isNotBlank()) "Nenhum documento encontrado" else if (currentFolderId == null) "Nenhum item na raiz." else "Esta pasta está vazia.")
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(items) { item ->
                        ListItem(
                            modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { onItemClick(item) },
                            leadingContent = { Icon(if (item.isFolder) Icons.Default.Folder else Icons.Default.InsertDriveFile, null) },
                            headlineContent = { Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text(if (item.isFolder) "Pasta" else "Documento") },
                            trailingContent = {
                                Row {
                                    IconButton(onClick = { onRename(item) }) { Icon(Icons.Default.Edit, "Renomear") }
                                    IconButton(onClick = { onDelete(item) }) { Icon(Icons.Default.Delete, null) }
                                }
                            },
                            colors = ListItemDefaults.colors(
                                containerColor = if (!item.isFolder && selectedDocumentId == item.id) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FullScreenPreview(file: StoredFile, onClose: () -> Unit, modifier: Modifier = Modifier) {
    PreviewFileCard(
        file = file,
        modifier = modifier.fillMaxSize(),
        onClose = onClose,
        showOnlyActionButtons = true
    )
}

private fun normalizeText(value: String): String =
    Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")

@Composable
fun NotesScreen(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val repo = remember { NotesRepository(ctx) }
    val initialState = remember { repo.loadState() }
    val initialNotes = initialState.notes.sortedByDescending { it.updatedAt }
    var notes by remember { mutableStateOf(initialNotes) }
    var activeNoteId by rememberSaveable { mutableStateOf(initialState.activeNoteId ?: initialNotes.firstOrNull()?.id) }
    var searchQuery by rememberSaveable { mutableStateOf(initialState.searchQuery) }
    var compactEditorOpen by rememberSaveable { mutableStateOf(initialState.isEditorOpenOnCompact) }
    var editorValue by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var editorInitializedForId by rememberSaveable { mutableStateOf<String?>(null) }

    fun createNote(initialContent: String = ""): Note {
        val now = System.currentTimeMillis()
        return Note(
            id = java.util.UUID.randomUUID().toString(),
            content = initialContent,
            createdAt = now,
            updatedAt = now,
            mode = "text",
            cursorStart = initialContent.length,
            cursorEnd = initialContent.length
        )
    }

    fun ensureActiveNote() {
        if (activeNoteId != null) return
        val newNote = createNote()
        notes = listOf(newNote) + notes
        activeNoteId = newNote.id
    }

    LaunchedEffect(Unit) {
        ensureActiveNote()
    }

    val activeNote = notes.firstOrNull { it.id == activeNoteId }
    if (activeNote != null && editorInitializedForId != activeNote.id) {
        editorValue = TextFieldValue(
            text = activeNote.content,
            selection = androidx.compose.ui.text.TextRange(
                activeNote.cursorStart.coerceIn(0, activeNote.content.length),
                activeNote.cursorEnd.coerceIn(0, activeNote.content.length)
            )
        )
        editorInitializedForId = activeNote.id
    }

    val filteredNotes = remember(notes, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isBlank()) notes
        else notes.filter {
            it.autoTitle().lowercase().contains(q) || it.content.lowercase().contains(q)
        }
    }

    LaunchedEffect(notes, activeNoteId, searchQuery, compactEditorOpen) {
        repo.saveState(
            NotesState(
                notes = notes.sortedByDescending { it.updatedAt },
                activeNoteId = activeNoteId,
                searchQuery = searchQuery,
                isEditorOpenOnCompact = compactEditorOpen
            )
        )
    }

    LaunchedEffect(activeNoteId, editorValue) {
        val id = activeNoteId ?: return@LaunchedEffect
        val currentNote = notes.firstOrNull { it.id == id } ?: return@LaunchedEffect
        if (currentNote.safeMode() == "draw") return@LaunchedEffect
        kotlinx.coroutines.delay(300)
        notes = notes.map { note ->
            if (note.id == id) {
                note.copy(
                    content = editorValue.text,
                    updatedAt = System.currentTimeMillis(),
                    mode = note.safeMode(),
                    cursorStart = editorValue.selection.start,
                    cursorEnd = editorValue.selection.end
                )
            } else note
        }.sortedByDescending { it.updatedAt }
    }

    fun openNote(noteId: String) {
        activeNoteId = noteId
        compactEditorOpen = true
    }

    fun newNoteAndOpen() {
        val newNote = createNote()
        notes = listOf(newNote) + notes
        openNote(newNote.id)
    }

    fun deleteNote(noteId: String) {
        val remaining = notes.filterNot { it.id == noteId }
        repo.drawingFile(noteId).delete()
        notes = remaining
        if (activeNoteId == noteId) {
            activeNoteId = remaining.firstOrNull()?.id
            editorInitializedForId = null
        }
        if (remaining.isEmpty()) {
            val replacement = createNote()
            notes = listOf(replacement)
            activeNoteId = replacement.id
        }
    }

    val notesListPane: @Composable (Modifier) -> Unit = { paneModifier ->
        Column(
            paneModifier
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                .padding(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Anotações", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                FilledTonalButton(onClick = { newNoteAndOpen() }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Nova nota")
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                placeholder = { Text("Buscar nas notas") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filteredNotes, key = { it.id }) { note ->
                    ListItem(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { openNote(note.id) },
                        headlineContent = {
                            Text(
                                note.autoTitle(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleMedium
                            )
                        },
                        supportingContent = {
                            Column {
                                Text(
                                    note.preview(),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "Atualizado: ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(java.util.Date(note.updatedAt))}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        },
                        trailingContent = {
                            IconButton(onClick = { deleteNote(note.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Deletar nota")
                            }
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = if (activeNoteId == note.id) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                        )
                    )
                }
            }
        }
    }

    val editorPane: @Composable (Modifier) -> Unit = { paneModifier ->
        Column(
            paneModifier
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                .padding(12.dp)
        ) {
            val note = notes.firstOrNull { it.id == activeNoteId }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (compactEditorOpen) {
                    FilledTonalIconButton(onClick = { compactEditorOpen = false }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar para lista")
                    }
                }
                Text(
                    text = note?.autoTitle() ?: "Nova nota",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = note?.safeMode() != "draw",
                    onClick = {
                        val id = note?.id ?: return@FilterChip
                        notes = notes.map {
                            if (it.id == id) it.copy(mode = "text", updatedAt = System.currentTimeMillis()) else it
                        }.sortedByDescending { it.updatedAt }
                    },
                    label = { Text("Texto") }
                )
                FilterChip(
                    selected = note?.safeMode() == "draw",
                    onClick = {
                        val id = note?.id ?: return@FilterChip
                        notes = notes.map {
                            if (it.id == id) it.copy(mode = "draw", updatedAt = System.currentTimeMillis()) else it
                        }.sortedByDescending { it.updatedAt }
                    },
                    label = { Text("Desenho") }
                )
            }
            Spacer(Modifier.height(8.dp))
            if (note?.safeMode() == "draw" && note.id == activeNoteId) {
                var pad by remember(note.id) { mutableStateOf<DrawingPad?>(null) }
                var drawChangeTick by remember(note.id) { mutableStateOf(0) }
                AndroidView(
                    factory = { context ->
                        DrawingPad(context).also { view ->
                            val drawingFile = repo.drawingFile(note.id)
                            view.loadPng(drawingFile)
                            view.onStrokeFinished = { drawChangeTick++ }
                            pad = view
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(onClick = {
                        pad?.clear()
                        repo.drawingFile(note.id).delete()
                        drawChangeTick++
                    }) { Text("Limpar") }
                }
                LaunchedEffect(drawChangeTick, note.id) {
                    if (drawChangeTick == 0) return@LaunchedEffect
                    kotlinx.coroutines.delay(300)
                    pad?.savePng(repo.drawingFile(note.id))
                    notes = notes.map {
                        if (it.id == note.id) it.copy(updatedAt = System.currentTimeMillis(), mode = it.safeMode()) else it
                    }.sortedByDescending { it.updatedAt }
                }
            } else {
                OutlinedTextField(
                    value = editorValue,
                    onValueChange = { value ->
                        if (activeNoteId == null) ensureActiveNote()
                        editorValue = value
                    },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    placeholder = { Text("Digite imediatamente…") },
                    modifier = Modifier.fillMaxSize(),
                    maxLines = Int.MAX_VALUE
                )
            }
        }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val isCompact = maxWidth < 720.dp
        if (!isCompact && compactEditorOpen) compactEditorOpen = false
        if (isCompact) {
            BackHandler(enabled = compactEditorOpen) { compactEditorOpen = false }
            if (compactEditorOpen) {
                editorPane(Modifier.fillMaxSize())
            } else {
                notesListPane(Modifier.fillMaxSize())
            }
        } else {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                notesListPane(Modifier.fillMaxHeight().weight(0.34f))
                editorPane(Modifier.fillMaxHeight().weight(0.66f))
            }
        }
    }
}

private fun Note.autoTitle(): String {
    val firstLine = content.lineSequence().firstOrNull()?.trim().orEmpty()
    if (firstLine.isNotBlank()) return firstLine.take(48)
    return "Nova nota"
}

private fun Note.preview(): String {
    val collapsed = content.replace("\n", " ").trim()
    return if (collapsed.isBlank()) "Sem conteúdo" else collapsed.take(120)
}

private fun Note.safeMode(): String =
    runCatching { mode }
        .getOrNull()
        .orEmpty()
        .ifBlank { "text" }
