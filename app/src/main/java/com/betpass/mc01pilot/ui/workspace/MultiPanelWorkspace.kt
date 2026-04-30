package com.betpass.mc01pilot.ui.workspace

import android.util.Log
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.betpass.mc01pilot.data.Module
import kotlinx.coroutines.flow.first

private const val MAX_PANELS = 3
private const val WORKSPACE_LOG_TAG = "MultiPanelWorkspace"
private val Context.workspaceDataStore by preferencesDataStore(name = "workspace_prefs")
private val WORKSPACE_KEY = stringPreferencesKey("workspace_state")

data class OpenPanel(
    val id: String,
    val module: Module
)

enum class MultiPanelLayoutType {
    SINGLE,
    TWO_COLUMNS,
    TWO_ROWS,
    THREE_COLUMNS,
    THREE_ROWS,
    TOP_ONE_BOTTOM_TWO,
    TOP_TWO_BOTTOM_ONE,
    LEFT_TWO_RIGHT_ONE,
    LEFT_ONE_RIGHT_TWO
}

@Composable
fun MultiPanelWorkspace(
    modifier: Modifier = Modifier,
    renderModule: @Composable (Module, Modifier) -> Unit
) {
    val context = LocalContext.current
    val stateHolder = rememberSaveableStateHolder()
    val openPanels = remember { mutableStateListOf(OpenPanel("panel-1", Module.CHECKLISTS)) }
    var focusedPanelId by rememberSaveable { mutableStateOf("panel-1") }
    var currentLayout by rememberSaveable { mutableStateOf(MultiPanelLayoutType.SINGLE) }
    val layoutWeights = remember {
        mutableStateMapOf(
            MultiPanelLayoutType.TWO_COLUMNS to mutableListOf(0.5f, 0.5f),
            MultiPanelLayoutType.TWO_ROWS to mutableListOf(0.5f, 0.5f),
            MultiPanelLayoutType.THREE_COLUMNS to mutableListOf(1f, 1f, 1f),
            MultiPanelLayoutType.THREE_ROWS to mutableListOf(1f, 1f, 1f),
            MultiPanelLayoutType.TOP_ONE_BOTTOM_TWO to mutableListOf(0.5f, 0.5f),
            MultiPanelLayoutType.TOP_TWO_BOTTOM_ONE to mutableListOf(0.5f, 0.5f),
            MultiPanelLayoutType.LEFT_TWO_RIGHT_ONE to mutableListOf(0.5f, 0.5f),
            MultiPanelLayoutType.LEFT_ONE_RIGHT_TWO to mutableListOf(0.5f, 0.5f)
        )
    }

    LaunchedEffect(Unit) {
        val raw = context.workspaceDataStore.data.first()[WORKSPACE_KEY] ?: return@LaunchedEffect
        val restored = restoreWorkspace(raw)
        if (restored.openPanels.isNotEmpty()) {
            openPanels.clear()
            openPanels.addAll(restored.openPanels)
            focusedPanelId = restored.focusedPanelId ?: restored.openPanels.first().id
            currentLayout = restored.layoutType
            restored.layoutWeights.forEach { (k, v) -> layoutWeights[k] = v.toMutableList() }
        }
    }

    LaunchedEffect(openPanels.toList(), focusedPanelId, currentLayout, layoutWeights.toMap()) {
        val snapshot = WorkspaceSnapshot(
            openPanels = openPanels.toList(),
            focusedPanelId = focusedPanelId,
            layoutType = currentLayout,
            layoutWeights = layoutWeights.mapValues { it.value.toList() }
        )
        context.workspaceDataStore.edit { it[WORKSPACE_KEY] = snapshot.serialize() }
    }

    val availableLayouts = remember(openPanels.size) { allowedLayouts(openPanels.size) }
    if (currentLayout !in availableLayouts) currentLayout = availableLayouts.first()

    Column(modifier.fillMaxSize()) {
        WorkspaceToolbar(
            openPanels = openPanels.toList(),
            currentLayout = currentLayout,
            availableLayouts = availableLayouts,
            onLayoutSelected = { currentLayout = it },
            onFocusDuringFlight = {
                Log.d(WORKSPACE_LOG_TAG, "during-flight action tapped; openPanels=${openPanels.size} focused=$focusedPanelId")
                if (openPanels.isEmpty()) {
                    openPanels.add(OpenPanel("panel-${System.currentTimeMillis()}", Module.CHECKLISTS))
                    focusedPanelId = openPanels.first().id
                    Log.d(WORKSPACE_LOG_TAG, "no panels found; created CHECKLISTS panel id=$focusedPanelId")
                    return@WorkspaceToolbar
                }
                val defaultDuringFlightModule = Module.ROUTE
                val existingPanel = openPanels.firstOrNull { it.module == defaultDuringFlightModule }
                if (existingPanel != null) {
                    focusedPanelId = existingPanel.id
                    Log.d(WORKSPACE_LOG_TAG, "focused existing CHECKLISTS panel id=${existingPanel.id}")
                } else {
                    val focusedIndex = openPanels.indexOfFirst { it.id == focusedPanelId }.takeIf { it >= 0 } ?: 0
                    openPanels[focusedIndex] = openPanels[focusedIndex].copy(module = defaultDuringFlightModule)
                    focusedPanelId = openPanels[focusedIndex].id
                    Log.d(WORKSPACE_LOG_TAG, "replaced focused panel index=$focusedIndex with CHECKLISTS id=$focusedPanelId")
                }
            },
            onAddPanel = {
                if (openPanels.size >= MAX_PANELS) return@WorkspaceToolbar
                val opened = openPanels.map { it.module }.toSet()
                val next = Module.entries.firstOrNull { it !in opened } ?: return@WorkspaceToolbar
                openPanels.add(OpenPanel("panel-${System.currentTimeMillis()}", next))
                focusedPanelId = openPanels.last().id
            }
        )
        WorkspaceCanvas(
            panels = openPanels.toList(),
            focusedPanelId = focusedPanelId,
            layoutType = currentLayout,
            layoutWeights = layoutWeights,
            onFocusPanel = { focusedPanelId = it },
            onClosePanel = { panelId ->
                if (openPanels.size == 1) return@WorkspaceCanvas
                val index = openPanels.indexOfFirst { it.id == panelId }
                if (index >= 0) {
                    openPanels.removeAt(index)
                    focusedPanelId = openPanels.getOrNull(index)?.id ?: openPanels.last().id
                }
            },
            onChangePanelModule = { panelId, targetModule ->
                val existing = openPanels.firstOrNull { it.module == targetModule }
                if (existing != null && existing.id != panelId) {
                    focusedPanelId = existing.id
                    return@WorkspaceCanvas
                }
                val index = openPanels.indexOfFirst { it.id == panelId }
                if (index >= 0) openPanels[index] = openPanels[index].copy(module = targetModule)
            },
            onWeightsChange = { type, values -> layoutWeights[type] = values.toMutableList() },
            renderModule = { panel, panelModifier ->
                stateHolder.SaveableStateProvider(panel.module.name) {
                    renderModule(panel.module, panelModifier)
                }
            }
        )
    }
}

@Composable
private fun WorkspaceToolbar(
    openPanels: List<OpenPanel>,
    currentLayout: MultiPanelLayoutType,
    availableLayouts: List<MultiPanelLayoutType>,
    onLayoutSelected: (MultiPanelLayoutType) -> Unit,
    onFocusDuringFlight: () -> Unit,
    onAddPanel: () -> Unit
) {
    var openLayoutMenu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = false,
            onClick = onAddPanel,
            enabled = openPanels.size < MAX_PANELS,
            label = { Text("+ Painel") },
            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) }
        )
        Box {
            FilterChip(
                selected = false,
                onClick = { openLayoutMenu = true },
                label = { Text("Layout: ${currentLayout.label}") },
                leadingIcon = { Icon(Icons.Default.ViewColumn, contentDescription = null) }
            )
            DropdownMenu(expanded = openLayoutMenu, onDismissRequest = { openLayoutMenu = false }) {
                availableLayouts.forEach { layout ->
                    DropdownMenuItem(
                        text = { Text(layout.label) },
                        onClick = {
                            onLayoutSelected(layout)
                            openLayoutMenu = false
                        }
                    )
                }
            }
        }
        FilterChip(
            selected = false,
            onClick = onFocusDuringFlight,
            label = { Text("Durante o voo") }
        )
        Text("Painéis abertos: ${openPanels.size}/3", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun WorkspaceCanvas(
    panels: List<OpenPanel>,
    focusedPanelId: String,
    layoutType: MultiPanelLayoutType,
    layoutWeights: Map<MultiPanelLayoutType, MutableList<Float>>,
    onFocusPanel: (String) -> Unit,
    onClosePanel: (String) -> Unit,
    onChangePanelModule: (String, Module) -> Unit,
    onWeightsChange: (MultiPanelLayoutType, List<Float>) -> Unit,
    renderModule: @Composable (OpenPanel, Modifier) -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize().padding(8.dp)) {
        val minWidth = 280.dp
        val minHeight = 220.dp
        val widthLimit = if (maxWidth.value > 0f) (minWidth / maxWidth).coerceAtMost(0.45f) else 0.2f
        val heightLimit = if (maxHeight.value > 0f) (minHeight / maxHeight).coerceAtMost(0.45f) else 0.2f
        val widthLimitForTwoPanels = widthLimit.coerceAtMost(0.35f)
        val heightLimitForTwoPanels = heightLimit.coerceAtMost(0.35f)
        when (panels.size) {
            1 -> {
                PanelCard(panels[0], focusedPanelId == panels[0].id, onFocusPanel, onClosePanel, onChangePanelModule) {
                    renderModule(panels[0], Modifier.fillMaxSize())
                }
            }
            2 -> when (layoutType) {
                MultiPanelLayoutType.TWO_ROWS -> SplitRows(
                    panels = panels,
                    focusedPanelId = focusedPanelId,
                    split = safeSplitFraction(layoutWeights[layoutType]?.firstOrNull(), heightLimitForTwoPanels),
                    minFraction = heightLimitForTwoPanels,
                    onFocusPanel = onFocusPanel,
                    onClosePanel = onClosePanel,
                    onChangePanelModule = onChangePanelModule,
                    onSplitChange = { onWeightsChange(layoutType, listOf(it, 1f - it)) },
                    renderModule = renderModule
                )
                else -> SplitColumns(
                    panels = panels,
                    focusedPanelId = focusedPanelId,
                    split = safeSplitFraction(layoutWeights[MultiPanelLayoutType.TWO_COLUMNS]?.firstOrNull(), widthLimitForTwoPanels),
                    minFraction = widthLimitForTwoPanels,
                    onFocusPanel = onFocusPanel,
                    onClosePanel = onClosePanel,
                    onChangePanelModule = onChangePanelModule,
                    onSplitChange = { onWeightsChange(MultiPanelLayoutType.TWO_COLUMNS, listOf(it, 1f - it)) },
                    renderModule = renderModule
                )
            }
            else -> ThreePanelLayout(
                panels = panels.take(3),
                focusedPanelId = focusedPanelId,
                layoutType = layoutType,
                layoutWeights = layoutWeights,
                widthLimit = widthLimit,
                heightLimit = heightLimit,
                onFocusPanel = onFocusPanel,
                onClosePanel = onClosePanel,
                onChangePanelModule = onChangePanelModule,
                onWeightsChange = onWeightsChange,
                renderModule = renderModule
            )
        }
    }
}

@Composable
private fun SplitColumns(
    panels: List<OpenPanel>,
    focusedPanelId: String,
    split: Float,
    minFraction: Float,
    onFocusPanel: (String) -> Unit,
    onClosePanel: (String) -> Unit,
    onChangePanelModule: (String, Module) -> Unit,
    onSplitChange: (Float) -> Unit,
    renderModule: @Composable (OpenPanel, Modifier) -> Unit
) {
    var widthPx by remember { mutableStateOf(1f) }
    var currentSplit by remember(split) { mutableStateOf(split) }
    Row(Modifier.fillMaxSize().onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }) {
        PanelCard(panels[0], focusedPanelId == panels[0].id, onFocusPanel, onClosePanel, onChangePanelModule, Modifier.weight(currentSplit)) {
            renderModule(panels[0], Modifier.fillMaxSize())
        }
        DividerHandle(
            vertical = true,
            modifier = Modifier.fillMaxHeight(),
            onDrag = { delta ->
                val new = (currentSplit + delta / widthPx).coerceIn(minFraction, 1f - minFraction)
                currentSplit = new
                onSplitChange(new)
            }
        )
        PanelCard(panels[1], focusedPanelId == panels[1].id, onFocusPanel, onClosePanel, onChangePanelModule, Modifier.weight(1f - currentSplit)) {
            renderModule(panels[1], Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun SplitRows(
    panels: List<OpenPanel>,
    focusedPanelId: String,
    split: Float,
    minFraction: Float,
    onFocusPanel: (String) -> Unit,
    onClosePanel: (String) -> Unit,
    onChangePanelModule: (String, Module) -> Unit,
    onSplitChange: (Float) -> Unit,
    renderModule: @Composable (OpenPanel, Modifier) -> Unit
) {
    var heightPx by remember { mutableStateOf(1f) }
    var currentSplit by remember(split) { mutableStateOf(split) }
    Column(Modifier.fillMaxSize().onSizeChanged { heightPx = it.height.toFloat().coerceAtLeast(1f) }) {
        PanelCard(panels[0], focusedPanelId == panels[0].id, onFocusPanel, onClosePanel, onChangePanelModule, Modifier.weight(currentSplit)) {
            renderModule(panels[0], Modifier.fillMaxSize())
        }
        DividerHandle(
            vertical = false,
            modifier = Modifier.fillMaxWidth(),
            onDrag = { delta ->
                val new = (currentSplit + delta / heightPx).coerceIn(minFraction, 1f - minFraction)
                currentSplit = new
                onSplitChange(new)
            }
        )
        PanelCard(panels[1], focusedPanelId == panels[1].id, onFocusPanel, onClosePanel, onChangePanelModule, Modifier.weight(1f - currentSplit)) {
            renderModule(panels[1], Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun ThreePanelLayout(
    panels: List<OpenPanel>,
    focusedPanelId: String,
    layoutType: MultiPanelLayoutType,
    layoutWeights: Map<MultiPanelLayoutType, MutableList<Float>>,
    widthLimit: Float,
    heightLimit: Float,
    onFocusPanel: (String) -> Unit,
    onClosePanel: (String) -> Unit,
    onChangePanelModule: (String, Module) -> Unit,
    onWeightsChange: (MultiPanelLayoutType, List<Float>) -> Unit,
    renderModule: @Composable (OpenPanel, Modifier) -> Unit
) {
    var widthPx by remember { mutableStateOf(1f) }
    var heightPx by remember { mutableStateOf(1f) }
    val sizedModifier = Modifier
        .fillMaxSize()
        .onSizeChanged {
            widthPx = it.width.toFloat().coerceAtLeast(1f)
            heightPx = it.height.toFloat().coerceAtLeast(1f)
        }
    when (layoutType) {
        MultiPanelLayoutType.THREE_ROWS -> {
            val weights = normalizeThree(layoutWeights[layoutType] ?: mutableListOf(1f, 1f, 1f))
            var first by remember(weights) { mutableStateOf(weights[0]) }
            var second by remember(weights) { mutableStateOf(weights[1]) }
            val third = (1f - first - second).coerceAtLeast(heightLimit)
            Column(sizedModifier) {
                PanelCard(panels[0], focusedPanelId == panels[0].id, onFocusPanel, onClosePanel, onChangePanelModule, Modifier.weight(first)) {
                    renderModule(panels[0], Modifier.fillMaxSize())
                }
                DividerHandle(false, Modifier.fillMaxWidth(), onDrag = { dy ->
                    val delta = dy / heightPx
                    val newFirst = (first + delta).coerceIn(heightLimit, 1f - heightLimit * 2)
                    val remainder = 1f - newFirst
                    second = second.coerceIn(heightLimit, remainder - heightLimit)
                    first = newFirst
                    onWeightsChange(layoutType, listOf(first, second, 1f - first - second))
                })
                PanelCard(panels[1], focusedPanelId == panels[1].id, onFocusPanel, onClosePanel, onChangePanelModule, Modifier.weight(second)) {
                    renderModule(panels[1], Modifier.fillMaxSize())
                }
                DividerHandle(false, Modifier.fillMaxWidth(), onDrag = { dy ->
                    val delta = dy / heightPx
                    val newSecond = (second + delta).coerceIn(heightLimit, 1f - first - heightLimit)
                    second = newSecond
                    onWeightsChange(layoutType, listOf(first, second, 1f - first - second))
                })
                PanelCard(panels[2], focusedPanelId == panels[2].id, onFocusPanel, onClosePanel, onChangePanelModule, Modifier.weight(third)) {
                    renderModule(panels[2], Modifier.fillMaxSize())
                }
            }
        }
        MultiPanelLayoutType.TOP_ONE_BOTTOM_TWO -> {
            val weights = layoutWeights[layoutType] ?: mutableListOf(1f, 1f)
            var topFrac by remember(weights) { mutableStateOf((weights[0] / (weights[0] + weights[1])).coerceIn(heightLimit, 1f - heightLimit)) }
            var bottomSplit by remember(weights) { mutableStateOf(0.5f) }
            Column(sizedModifier) {
                PanelCard(panels[0], focusedPanelId == panels[0].id, onFocusPanel, onClosePanel, onChangePanelModule, Modifier.weight(topFrac)) {
                    renderModule(panels[0], Modifier.fillMaxSize())
                }
                DividerHandle(false, Modifier.fillMaxWidth(), onDrag = { dy ->
                    topFrac = (topFrac + dy / heightPx).coerceIn(heightLimit, 1f - heightLimit)
                    onWeightsChange(layoutType, listOf(topFrac, 1f - topFrac, bottomSplit))
                })
                Row(Modifier.weight(1f - topFrac).fillMaxWidth()) {
                    PanelCard(panels[1], focusedPanelId == panels[1].id, onFocusPanel, onClosePanel, onChangePanelModule, Modifier.weight(bottomSplit)) {
                        renderModule(panels[1], Modifier.fillMaxSize())
                    }
                    DividerHandle(true, Modifier.fillMaxHeight(), onDrag = { dx ->
                        bottomSplit = (bottomSplit + dx / widthPx).coerceIn(widthLimit, 1f - widthLimit)
                        onWeightsChange(layoutType, listOf(topFrac, 1f - topFrac, bottomSplit))
                    })
                    PanelCard(panels[2], focusedPanelId == panels[2].id, onFocusPanel, onClosePanel, onChangePanelModule, Modifier.weight(1f - bottomSplit)) {
                        renderModule(panels[2], Modifier.fillMaxSize())
                    }
                }
            }
        }
        MultiPanelLayoutType.TOP_TWO_BOTTOM_ONE -> {
            var topFrac by remember { mutableStateOf(0.5f) }
            var topSplit by remember { mutableStateOf(0.5f) }
            Column(sizedModifier) {
                Row(Modifier.weight(topFrac).fillMaxWidth()) {
                    PanelCard(panels[0], focusedPanelId == panels[0].id, onFocusPanel, onClosePanel, onChangePanelModule, Modifier.weight(topSplit)) {
                        renderModule(panels[0], Modifier.fillMaxSize())
                    }
                    DividerHandle(true, Modifier.fillMaxHeight(), onDrag = { dx ->
                        topSplit = (topSplit + dx / widthPx).coerceIn(widthLimit, 1f - widthLimit)
                        onWeightsChange(layoutType, listOf(topFrac, topSplit))
                    })
                    PanelCard(panels[1], focusedPanelId == panels[1].id, onFocusPanel, onClosePanel, onChangePanelModule, Modifier.weight(1f - topSplit)) {
                        renderModule(panels[1], Modifier.fillMaxSize())
                    }
                }
                DividerHandle(false, Modifier.fillMaxWidth(), onDrag = { dy ->
                    topFrac = (topFrac + dy / heightPx).coerceIn(heightLimit, 1f - heightLimit)
                    onWeightsChange(layoutType, listOf(topFrac, topSplit))
                })
                PanelCard(panels[2], focusedPanelId == panels[2].id, onFocusPanel, onClosePanel, onChangePanelModule, Modifier.weight(1f - topFrac)) {
                    renderModule(panels[2], Modifier.fillMaxSize())
                }
            }
        }
        MultiPanelLayoutType.LEFT_TWO_RIGHT_ONE -> {
            var leftFrac by remember { mutableStateOf(0.5f) }
            var leftSplit by remember { mutableStateOf(0.5f) }
            Row(sizedModifier) {
                Column(Modifier.weight(leftFrac)) {
                    PanelCard(panels[0], focusedPanelId == panels[0].id, onFocusPanel, onClosePanel, onChangePanelModule, Modifier.weight(leftSplit)) {
                        renderModule(panels[0], Modifier.fillMaxSize())
                    }
                    DividerHandle(false, Modifier.fillMaxWidth(), onDrag = { dy ->
                        leftSplit = (leftSplit + dy / heightPx).coerceIn(heightLimit, 1f - heightLimit)
                        onWeightsChange(layoutType, listOf(leftFrac, leftSplit))
                    })
                    PanelCard(panels[1], focusedPanelId == panels[1].id, onFocusPanel, onClosePanel, onChangePanelModule, Modifier.weight(1f - leftSplit)) {
                        renderModule(panels[1], Modifier.fillMaxSize())
                    }
                }
                DividerHandle(true, Modifier.fillMaxHeight(), onDrag = { dx ->
                    leftFrac = (leftFrac + dx / widthPx).coerceIn(widthLimit, 1f - widthLimit)
                    onWeightsChange(layoutType, listOf(leftFrac, leftSplit))
                })
                PanelCard(panels[2], focusedPanelId == panels[2].id, onFocusPanel, onClosePanel, onChangePanelModule, Modifier.weight(1f - leftFrac)) {
                    renderModule(panels[2], Modifier.fillMaxSize())
                }
            }
        }
        MultiPanelLayoutType.LEFT_ONE_RIGHT_TWO -> {
            var leftFrac by remember { mutableStateOf(0.5f) }
            var rightSplit by remember { mutableStateOf(0.5f) }
            Row(sizedModifier) {
                PanelCard(panels[0], focusedPanelId == panels[0].id, onFocusPanel, onClosePanel, onChangePanelModule, Modifier.weight(leftFrac)) {
                    renderModule(panels[0], Modifier.fillMaxSize())
                }
                DividerHandle(true, Modifier.fillMaxHeight(), onDrag = { dx ->
                    leftFrac = (leftFrac + dx / widthPx).coerceIn(widthLimit, 1f - widthLimit)
                    onWeightsChange(layoutType, listOf(leftFrac, rightSplit))
                })
                Column(Modifier.weight(1f - leftFrac)) {
                    PanelCard(panels[1], focusedPanelId == panels[1].id, onFocusPanel, onClosePanel, onChangePanelModule, Modifier.weight(rightSplit)) {
                        renderModule(panels[1], Modifier.fillMaxSize())
                    }
                    DividerHandle(false, Modifier.fillMaxWidth(), onDrag = { dy ->
                        rightSplit = (rightSplit + dy / heightPx).coerceIn(heightLimit, 1f - heightLimit)
                        onWeightsChange(layoutType, listOf(leftFrac, rightSplit))
                    })
                    PanelCard(panels[2], focusedPanelId == panels[2].id, onFocusPanel, onClosePanel, onChangePanelModule, Modifier.weight(1f - rightSplit)) {
                        renderModule(panels[2], Modifier.fillMaxSize())
                    }
                }
            }
        }
        else -> {
            val weights = normalizeThree(layoutWeights[MultiPanelLayoutType.THREE_COLUMNS] ?: mutableListOf(1f, 1f, 1f))
            var first by remember(weights) { mutableStateOf(weights[0]) }
            var second by remember(weights) { mutableStateOf(weights[1]) }
            val third = (1f - first - second).coerceAtLeast(widthLimit)
            Row(sizedModifier) {
                PanelCard(panels[0], focusedPanelId == panels[0].id, onFocusPanel, onClosePanel, onChangePanelModule, Modifier.weight(first)) {
                    renderModule(panels[0], Modifier.fillMaxSize())
                }
                DividerHandle(true, Modifier.fillMaxHeight(), onDrag = { dx ->
                    val newFirst = (first + dx / widthPx).coerceIn(widthLimit, 1f - widthLimit * 2)
                    val remainder = 1f - newFirst
                    second = second.coerceIn(widthLimit, remainder - widthLimit)
                    first = newFirst
                    onWeightsChange(MultiPanelLayoutType.THREE_COLUMNS, listOf(first, second, 1f - first - second))
                })
                PanelCard(panels[1], focusedPanelId == panels[1].id, onFocusPanel, onClosePanel, onChangePanelModule, Modifier.weight(second)) {
                    renderModule(panels[1], Modifier.fillMaxSize())
                }
                DividerHandle(true, Modifier.fillMaxHeight(), onDrag = { dx ->
                    second = (second + dx / widthPx).coerceIn(widthLimit, 1f - first - widthLimit)
                    onWeightsChange(MultiPanelLayoutType.THREE_COLUMNS, listOf(first, second, 1f - first - second))
                })
                PanelCard(panels[2], focusedPanelId == panels[2].id, onFocusPanel, onClosePanel, onChangePanelModule, Modifier.weight(third)) {
                    renderModule(panels[2], Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable
private fun DividerHandle(vertical: Boolean, modifier: Modifier = Modifier, onDrag: (Float) -> Unit) {
    Box(
        modifier = modifier
            .then(if (vertical) Modifier.width(10.dp) else Modifier.height(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
            .pointerInput(Unit) { detectDragGestures { _, drag -> onDrag(if (vertical) drag.x else drag.y) } },
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.DragIndicator, contentDescription = null)
    }
}

@Composable
private fun PanelCard(
    panel: OpenPanel,
    isFocused: Boolean,
    onFocusPanel: (String) -> Unit,
    onClosePanel: (String) -> Unit,
    onChangePanelModule: (String, Module) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var showModules by remember { mutableStateOf(false) }
    val borderColor = if (isFocused) {
        MaterialTheme.colorScheme.outline
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    Surface(
        modifier = modifier
            .padding(2.dp)
            .border(if (isFocused) 2.dp else 1.dp, borderColor, RoundedCornerShape(10.dp))
            .pointerInput(panel.id) { detectTapGestures(onTap = { onFocusPanel(panel.id) }) },
        tonalElevation = if (isFocused) 3.dp else 1.dp,
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    panel.module.label(),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f).clickable { showModules = true }
                )
                IconButton(onClick = { showModules = true }) { Icon(Icons.Default.ArrowDropDown, contentDescription = "Trocar aba") }
                IconButton(onClick = { onClosePanel(panel.id) }) { Icon(Icons.Default.Close, contentDescription = "Fechar painel") }
                DropdownMenu(expanded = showModules, onDismissRequest = { showModules = false }) {
                    Module.entries.forEach { module ->
                        DropdownMenuItem(
                            text = { Text(module.label()) },
                            onClick = {
                                onChangePanelModule(panel.id, module)
                                showModules = false
                            }
                        )
                    }
                }
            }
            Box(Modifier.fillMaxSize().padding(2.dp)) { content() }
        }
    }
}

private fun normalizeThree(values: MutableList<Float>): List<Float> {
    val raw = if (values.size >= 3) values.take(3) else listOf(1f, 1f, 1f)
    val total = raw.sum().takeIf { it > 0f } ?: 3f
    return raw.map { it / total }
}

private fun safeSplitFraction(raw: Float?, minFraction: Float): Float {
    val fallback = 0.5f
    val base = raw ?: fallback
    if (!base.isFinite()) return fallback
    return base.coerceIn(minFraction, 1f - minFraction)
}

private fun allowedLayouts(panels: Int): List<MultiPanelLayoutType> = when (panels) {
    1 -> listOf(MultiPanelLayoutType.SINGLE)
    2 -> listOf(MultiPanelLayoutType.TWO_COLUMNS, MultiPanelLayoutType.TWO_ROWS)
    else -> listOf(
        MultiPanelLayoutType.THREE_COLUMNS,
        MultiPanelLayoutType.THREE_ROWS,
        MultiPanelLayoutType.TOP_ONE_BOTTOM_TWO,
        MultiPanelLayoutType.TOP_TWO_BOTTOM_ONE,
        MultiPanelLayoutType.LEFT_TWO_RIGHT_ONE,
        MultiPanelLayoutType.LEFT_ONE_RIGHT_TWO
    )
}

private val MultiPanelLayoutType.label: String
    get() = when (this) {
        MultiPanelLayoutType.SINGLE -> "Tela cheia"
        MultiPanelLayoutType.TWO_COLUMNS -> "2 lado a lado"
        MultiPanelLayoutType.TWO_ROWS -> "2 empilhados"
        MultiPanelLayoutType.THREE_COLUMNS -> "3 lado a lado"
        MultiPanelLayoutType.THREE_ROWS -> "3 empilhados"
        MultiPanelLayoutType.TOP_ONE_BOTTOM_TWO -> "1 em cima + 2 embaixo"
        MultiPanelLayoutType.TOP_TWO_BOTTOM_ONE -> "2 em cima + 1 embaixo"
        MultiPanelLayoutType.LEFT_TWO_RIGHT_ONE -> "2 esquerda + 1 direita"
        MultiPanelLayoutType.LEFT_ONE_RIGHT_TWO -> "1 esquerda + 2 direita"
    }

private data class WorkspaceSnapshot(
    val openPanels: List<OpenPanel>,
    val focusedPanelId: String?,
    val layoutType: MultiPanelLayoutType,
    val layoutWeights: Map<MultiPanelLayoutType, List<Float>>
) {
    fun serialize(): String {
        val panels = openPanels.joinToString(";") { "${it.id},${it.module.name}" }
        val weights = layoutWeights.entries.joinToString(";") { "${it.key.name}:${it.value.joinToString(",")}" }
        return listOf(layoutType.name, focusedPanelId.orEmpty(), panels, weights).joinToString("|")
    }
}

private fun restoreWorkspace(raw: String): WorkspaceSnapshot {
    val parts = raw.split("|")
    val layout = parts.getOrNull(0)?.let { runCatching { MultiPanelLayoutType.valueOf(it) }.getOrNull() }
        ?: MultiPanelLayoutType.SINGLE
    val focused = parts.getOrNull(1).takeUnless { it.isNullOrBlank() }
    val panels = parts.getOrNull(2).orEmpty().split(";").mapNotNull { token ->
        val panelParts = token.split(",")
        if (panelParts.size < 2) return@mapNotNull null
        val module = runCatching { Module.valueOf(panelParts[1]) }.getOrNull() ?: return@mapNotNull null
        OpenPanel(panelParts[0], module)
    }.ifEmpty { listOf(OpenPanel("panel-1", Module.CHECKLISTS)) }.take(MAX_PANELS)
    val weights = parts.getOrNull(3).orEmpty().split(";").mapNotNull { entry ->
        val pair = entry.split(":")
        if (pair.size != 2) return@mapNotNull null
        val key = runCatching { MultiPanelLayoutType.valueOf(pair[0]) }.getOrNull() ?: return@mapNotNull null
        val values = pair[1].split(",").mapNotNull { it.toFloatOrNull() }
        key to values
    }.toMap()
    return WorkspaceSnapshot(
        openPanels = panels,
        focusedPanelId = focused,
        layoutType = layout,
        layoutWeights = weights
    )
}

private fun Module.label() = when (this) {
    Module.CHECKLISTS -> "Checklists"
    Module.EMERGENCY -> "Emergência"
    Module.CHARTS -> "Cartas"
    Module.DOCUMENTS -> "Documentos"
    Module.NOTES -> "Anotações"
    Module.WEIGHT_BALANCE -> "Peso e Balanceamento"
    Module.ROUTE -> "Rota"
}
