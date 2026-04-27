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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import com.betpass.mc01pilot.ui.DrawingPad
import com.betpass.mc01pilot.ui.theme.MC01Theme
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.betpass.mc01pilot.data.*
import java.text.DateFormat
import java.text.Normalizer
import android.graphics.pdf.PdfRenderer

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MC01App() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MC01App() {
    val cfg = LocalConfiguration.current
    var left by rememberSaveable { mutableStateOf(Module.CHECKLISTS) }
    var right by rememberSaveable { mutableStateOf(Module.CHARTS) }
    var split by rememberSaveable { mutableFloatStateOf(0.55f) }
    var width by rememberSaveable { mutableIntStateOf(1) }
    MC01Theme {
        Scaffold(topBar = { TopAppBar(title = { Text("MC01 Pilot Assistant") }) }) { pad ->
            if (cfg.screenWidthDp < 700) {
                var selected by rememberSaveable { mutableStateOf(Module.CHECKLISTS) }
                Column(Modifier.padding(pad).fillMaxSize()) {
                    ModulePicker(selected) { selected = it }
                    ModuleContent(selected, Modifier.weight(1f).fillMaxWidth())
                }
            } else {
                Row(Modifier.padding(pad).fillMaxSize().onSizeChanged { width = it.width }) {
                    Pane(left, { left = it }, Modifier.fillMaxHeight().weight(split))
                    Box(Modifier.width(14.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceVariant).pointerInput(Unit) {
                        detectDragGestures { _, drag -> split = (split + drag.x / width).coerceIn(0.25f, 0.75f) }
                    }, contentAlignment = Alignment.Center) { Icon(Icons.Default.DragIndicator, null) }
                    Pane(right, { right = it }, Modifier.fillMaxHeight().weight(1f - split))
                }
            }
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
fun Module.label() = when (this) { Module.CHECKLISTS -> "Checklists"; Module.CHARTS -> "Cartas"; Module.DOCUMENTS -> "Documentos"; Module.NOTES -> "Anotações" }

@Composable
fun ModuleContent(module: Module, modifier: Modifier) = Box(modifier.padding(10.dp)) {
    when (module) {
        Module.CHECKLISTS -> ChecklistScreen(modifier = modifier)
        Module.CHARTS -> FileLibraryScreen(type = "chart", title = "Cartas", modifier = modifier)
        Module.DOCUMENTS -> FileLibraryScreen(type = "document", title = "Documentos", modifier = modifier)
        Module.NOTES -> NotesScreen(modifier = modifier)
    }
}

@Composable fun ChecklistScreen(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val checklist = remember { ChecklistRepository(ctx).load() }
    var categoryIndex by rememberSaveable { mutableIntStateOf(0) }
    var checked by rememberSaveable { mutableStateOf(setOf<String>()) }
    var selectorOpen by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val cat = checklist.categories[categoryIndex]
    Column(modifier.fillMaxSize()) {
        Box {
            Text(
                text = "${checklist.aircraft} • ${cat.title}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { selectorOpen = true }
                    .padding(4.dp)
            )
            DropdownMenu(expanded = selectorOpen, onDismissRequest = { selectorOpen = false }) {
                checklist.categories.forEachIndexed { index, category ->
                    DropdownMenuItem(
                        text = { Text(category.title) },
                        onClick = {
                            categoryIndex = index
                            checked = emptySet()
                            selectorOpen = false
                        }
                    )
                }
            }
        }
        checklist.source_note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary) }
        LinearProgressIndicator(progress = { checked.size.toFloat() / cat.items.size.coerceAtLeast(1) }, Modifier.fillMaxWidth().padding(vertical = 8.dp))
        Box(Modifier.weight(1f)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(end = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(cat.items) { item ->
                    val key = cat.id + item.label
                    ElevatedCard(Modifier.fillMaxWidth().clickable { checked = if (key in checked) checked - key else checked + key }) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = key in checked, onCheckedChange = { checked = if (it) checked + key else checked - key })
                            Spacer(Modifier.width(8.dp))
                            Column { Text(item.label, fontWeight = FontWeight.SemiBold); Text(item.action) }
                        }
                    }
                }
            }
            ScrollIndicator(listState = listState, modifier = Modifier.align(Alignment.CenterEnd))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(enabled = categoryIndex > 0, onClick = { categoryIndex--; checked = emptySet() }) { Icon(Icons.Default.ArrowBack, null); Text("Anterior") }
            Button(onClick = { checked = cat.items.map { cat.id + it.label }.toSet() }) { Text("Marcar tudo") }
            Button(enabled = categoryIndex < checklist.categories.lastIndex, onClick = { categoryIndex++; checked = emptySet() }) { Text("Próximo"); Icon(Icons.Default.ArrowForward, null) }
        }
    }
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
    var createName by rememberSaveable(type) { mutableStateOf("") }
    var createError by rememberSaveable(type) { mutableStateOf<String?>(null) }

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
            if (isPreviewExpanded && selectedDocument != null) {
                FullScreenPreview(
                    file = selectedDocument,
                    onClose = { isPreviewExpanded = false },
                    modifier = Modifier.fillMaxSize()
                )
            } else if (maxWidth < 760.dp || !showInlinePreview) {
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
                    onDelete = { item ->
                        if (selectedDocumentId == item.id) selectedDocumentId = null
                        repo.delete(item.id)
                        refresh()
                    },
                    selectedDocumentId = selectedDocumentId,
                    modifier = Modifier.fillMaxSize()
                )
                if (!isWideScreen && selectedDocument != null) {
                    AlertDialog(
                        onDismissRequest = { selectedDocumentId = null },
                        confirmButton = {},
                        dismissButton = {},
                        title = { Text("Preview") },
                        text = {
                            PreviewFileCard(
                                file = selectedDocument,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 280.dp, max = 540.dp),
                                onExpand = { isPreviewExpanded = true },
                                onClose = { selectedDocumentId = null }
                            )
                        }
                    )
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
                        onDelete = { item ->
                            if (selectedDocumentId == item.id) selectedDocumentId = null
                            repo.delete(item.id)
                            refresh()
                        },
                        selectedDocumentId = selectedDocumentId,
                        modifier = Modifier.fillMaxHeight().weight(.4f)
                    )
                    ElevatedCard(Modifier.fillMaxHeight().weight(.6f)) {
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
}

@Composable
fun PreviewFileCard(
    file: StoredFile,
    modifier: Modifier = Modifier,
    onExpand: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null
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
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bitmap
                        }
                    }
                }
            }.getOrNull()
        } else null
    }

    Column(modifier.padding(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(file.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (file.isFolder) "Pasta" else "Arquivo", style = MaterialTheme.typography.bodySmall)
            }
            if (onExpand != null) IconButton(onClick = onExpand) { Icon(Icons.Default.OpenInFull, "Expandir") }
            if (onClose != null) IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Fechar") }
            if (uri != null) {
                IconButton(onClick = {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                }) { Icon(Icons.Default.OpenInNew, null) }
            }
        }
        Spacer(Modifier.height(8.dp))
        when {
            uri != null && mimeType.startsWith("image") -> AndroidView(
                factory = { android.widget.ImageView(it).apply { scaleType = android.widget.ImageView.ScaleType.FIT_CENTER } },
                update = { it.setImageURI(uri) },
                modifier = Modifier.fillMaxSize()
            )
            mimeType.startsWith("text") || file.contentText != null -> ElevatedCard(Modifier.fillMaxSize()) {
                LazyColumn(Modifier.fillMaxSize().padding(8.dp)) { item { Text(previewText) } }
            }
            pdfPreview != null -> ElevatedCard(Modifier.fillMaxSize()) {
                Image(
                    bitmap = pdfPreview.asImageBitmap(),
                    contentDescription = "Pré-visualização do PDF",
                    modifier = Modifier.fillMaxSize().padding(8.dp)
                )
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
    selectedDocumentId: String?,
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
                LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(items) { item ->
                        ListItem(
                            modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { onItemClick(item) },
                            leadingContent = { Icon(if (item.isFolder) Icons.Default.Folder else Icons.Default.InsertDriveFile, null) },
                            headlineContent = { Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text(if (item.isFolder) "Pasta" else "Documento") },
                            trailingContent = {
                                IconButton(onClick = { onDelete(item) }) { Icon(Icons.Default.Delete, null) }
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
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Visualização de documento", style = MaterialTheme.typography.bodySmall)
                    }
                },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, "Voltar") } }
            )
        }
    ) { padding ->
        PreviewFileCard(
            file = file,
            modifier = modifier.padding(padding).fillMaxSize(),
            onClose = onClose
        )
    }
}

private fun normalizeText(value: String): String =
    Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")

@Composable fun NotesScreen(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val repo = remember { NotesRepository(ctx) }
    val initialDraft = remember { repo.loadDraft() }
    var mode by rememberSaveable { mutableStateOf(initialDraft?.mode ?: "text") }
    var notes by remember { mutableStateOf(repo.list()) }
    var selectedNoteId by rememberSaveable { mutableStateOf(initialDraft?.selectedNoteId) }
    var draftTitle by rememberSaveable { mutableStateOf(initialDraft?.title ?: "nota_${System.currentTimeMillis()}") }
    var draftText by rememberSaveable { mutableStateOf(initialDraft?.text ?: "") }

    LaunchedEffect(mode, selectedNoteId, draftTitle, draftText) {
        repo.saveDraft(NoteDraft(mode = mode, title = draftTitle, text = draftText, selectedNoteId = selectedNoteId))
    }
    val notesList: @Composable (Modifier) -> Unit = { paneModifier ->
        Column(
            paneModifier
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                .padding(8.dp)
        ) {
            Text("Anotações", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("As notas são salvas no armazenamento interno do app.", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(notes) { note ->
                    ListItem(
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                            selectedNoteId = note.id
                            if (note.kind == "text") {
                                mode = "text"
                                draftTitle = note.title
                                draftText = repo.readText(note.id)
                            } else {
                                mode = "hand"
                                draftTitle = note.title
                            }
                        },
                        headlineContent = { Text(note.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(if (note.kind == "text") "Teclado" else "Mão livre") },
                        leadingContent = { Icon(if (note.kind == "text") Icons.Default.Description else Icons.Default.Draw, null) },
                        trailingContent = {
                            IconButton(onClick = {
                                repo.delete(note)
                                notes = repo.list()
                                if (selectedNoteId == note.id) selectedNoteId = null
                            }) { Icon(Icons.Default.Delete, null) }
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = if (selectedNoteId == note.id) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
                        )
                    )
                }
            }
        }
    }
    val editor: @Composable (Modifier) -> Unit = { paneModifier ->
        Column(paneModifier) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(mode == "text", { mode = "text" }, label = { Text("Teclado") })
                FilterChip(mode == "hand", { mode = "hand" }, label = { Text("Mão livre") })
            }
            Spacer(Modifier.height(8.dp))
            if (mode == "text") {
                TextNoteEditor(
                    modifier = Modifier.fillMaxSize(),
                    title = draftTitle,
                    text = draftText,
                    onTitleChange = { draftTitle = it },
                    onTextChange = { draftText = it },
                    onSaved = {
                        repo.saveText(draftTitle, draftText)
                        notes = repo.list()
                        selectedNoteId = repo.list()
                            .firstOrNull { it.kind == "text" && it.title == draftTitle }
                            ?.id
                    }
                )
            } else {
                HandNoteEditor(
                    modifier = Modifier.fillMaxSize(),
                    initialTitle = draftTitle,
                    onTitleChange = { draftTitle = it },
                    onSaved = { notes = repo.list() }
                )
            }
        }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        if (maxWidth < 720.dp) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                notesList(Modifier.fillMaxWidth().weight(.42f))
                editor(Modifier.fillMaxWidth().weight(.58f))
            }
        } else {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                notesList(Modifier.fillMaxHeight().weight(.35f))
                editor(Modifier.fillMaxHeight().weight(.65f))
            }
        }
    }
}

@Composable fun TextNoteEditor(
    modifier: Modifier,
    title: String,
    text: String,
    onTitleChange: (String) -> Unit,
    onTextChange: (String) -> Unit,
    onSaved: (() -> Unit)? = null
) {
    Column(modifier.fillMaxHeight()) {
        OutlinedTextField(title, onTitleChange, label = { Text("Nome do arquivo") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(text, onTextChange, label = { Text("Escreva aqui") }, modifier = Modifier.weight(1f).fillMaxWidth())
        Button(onClick = { onSaved?.invoke() }, Modifier.align(Alignment.End).padding(top = 8.dp)) { Text("Salvar") }
    }
}

@Composable fun HandNoteEditor(
    modifier: Modifier,
    initialTitle: String = "",
    onTitleChange: ((String) -> Unit)? = null,
    onSaved: (() -> Unit)? = null
) {
    val ctx = LocalContext.current
    val repo = remember { NotesRepository(ctx) }
    var title by rememberSaveable(initialTitle) { mutableStateOf(initialTitle.ifBlank { "rascunho_${System.currentTimeMillis()}" }) }
    var pad by remember { mutableStateOf<DrawingPad?>(null) }
    Column(modifier.fillMaxHeight()) {
        OutlinedTextField(
            title,
            {
                title = it
                onTitleChange?.invoke(it)
            },
            label = { Text("Nome do arquivo") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        AndroidView(
            factory = { DrawingPad(it).also { view -> pad = view } },
            modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(12.dp))
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            OutlinedButton(onClick = { pad?.clear() }) { Text("Limpar") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { pad?.savePng(repo.drawingFile(title)); onSaved?.invoke() }) { Text("Salvar PNG") }
        }
    }
}
