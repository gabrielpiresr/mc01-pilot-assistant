package com.betpass.mc01pilot

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import com.betpass.mc01pilot.ui.DrawingPad
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.betpass.mc01pilot.data.*
import java.text.DateFormat

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
    MaterialTheme {
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

@Composable fun ModuleContent(module: Module, modifier: Modifier) = Box(modifier.padding(10.dp)) {
    when(module) { Module.CHECKLISTS -> ChecklistScreen(modifier); Module.CHARTS -> FileLibraryScreen("chart", "Cartas", modifier); Module.DOCUMENTS -> FileLibraryScreen("document", "Documentos", modifier); Module.NOTES -> NotesScreen(modifier) }
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
        checklist.source_note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
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
    var folders by remember(type) { mutableStateOf(repo.listFolders(type)) }
    var selectedFolder by rememberSaveable(type) { mutableStateOf("Geral") }
    var selectedId by rememberSaveable(type) { mutableStateOf<String?>(null) }
    var newFolderName by rememberSaveable(type) { mutableStateOf("") }
    var renameFolderFrom by rememberSaveable(type) { mutableStateOf<String?>(null) }
    var renameFolderTo by rememberSaveable(type) { mutableStateOf("") }
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
                    ?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else null
                    }
            }.getOrNull() ?: (it.lastPathSegment?.substringAfterLast('/') ?: "arquivo")
            repo.add(name, selectedFolder, it, type)
            items = repo.list(type)
            folders = repo.listFolders(type)
        }
    }
    val filteredItems = remember(items, selectedFolder) { items.filter { it.folder == selectedFolder } }
    val selectedItem = filteredItems.firstOrNull { it.id == selectedId } ?: items.firstOrNull { it.id == selectedId }

    Row(modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.widthIn(min = 190.dp).fillMaxHeight().border(1.dp, MaterialTheme.colorScheme.outlineVariant).padding(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text("Nova pasta") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = {
                    repo.createFolder(type, newFolderName)
                    folders = repo.listFolders(type)
                    if (newFolderName.isNotBlank()) selectedFolder = newFolderName.trim()
                    newFolderName = ""
                }) { Icon(Icons.Default.CreateNewFolder, null) }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(folders) { folderName ->
                    ListItem(
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                            selectedFolder = folderName
                        },
                        headlineContent = { Text(folderName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingContent = { Icon(Icons.Default.Folder, null) },
                        trailingContent = {
                            if (folderName != "Geral") {
                                Row {
                                    IconButton(onClick = {
                                        renameFolderFrom = folderName
                                        renameFolderTo = folderName
                                    }) { Icon(Icons.Default.Edit, null) }
                                    IconButton(onClick = {
                                        repo.deleteFolder(type, folderName)
                                        items = repo.list(type)
                                        folders = repo.listFolders(type)
                                        if (selectedFolder == folderName) selectedFolder = "Geral"
                                    }) { Icon(Icons.Default.Delete, null) }
                                }
                            }
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = if (selectedFolder == folderName) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
                        )
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { picker.launch(arrayOf("application/pdf", "image/*", "text/*")) },
                modifier = Modifier.fillMaxWidth()
            ) { Icon(Icons.Default.UploadFile, null); Spacer(Modifier.width(6.dp)); Text("Enviar arquivo") }
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ElevatedCard(Modifier.fillMaxWidth().weight(.52f)) {
                if (filteredItems.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Nenhum arquivo nessa pasta.") }
                } else {
                    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(filteredItems) { f ->
                            ListItem(
                                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { selectedId = f.id },
                                leadingContent = { Icon(Icons.Default.InsertDriveFile, null) },
                                headlineContent = { Text(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                supportingContent = { Text("Pasta: ${f.folder}") },
                                trailingContent = {
                                    Row {
                                        IconButton(onClick = {
                                            selectedId = if (selectedId == f.id) null else selectedId
                                            repo.delete(f.id)
                                            items = repo.list(type)
                                            folders = repo.listFolders(type)
                                        }) { Icon(Icons.Default.Delete, null) }
                                    }
                                },
                                colors = ListItemDefaults.colors(
                                    containerColor = if (selectedId == f.id) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                )
                            )
                        }
                    }
            }
        }
    }
}

@Composable
fun PreviewFileCard(file: StoredFile, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val uri = remember(file.uri) { Uri.parse(file.uri) }
    val mimeType = remember(file.uri) { ctx.contentResolver.getType(uri).orEmpty() }
    val previewText = remember(file.id, mimeType) {
        if (mimeType.startsWith("text")) {
            runCatching {
                ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
            }.getOrDefault("Não foi possível carregar o conteúdo de texto.")
        } else ""
    }
    Column(modifier.padding(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(file.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Pasta: ${file.folder}", style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            }) { Icon(Icons.Default.OpenInNew, null) }
        }
        Spacer(Modifier.height(8.dp))
        when {
            mimeType.startsWith("image") -> AndroidView(
                factory = { android.widget.ImageView(it).apply { scaleType = android.widget.ImageView.ScaleType.FIT_CENTER } },
                update = { it.setImageURI(uri) },
                modifier = Modifier.fillMaxSize()
            )
            mimeType.startsWith("text") -> {
                ElevatedCard(Modifier.fillMaxSize()) {
                    LazyColumn(Modifier.fillMaxSize().padding(8.dp)) { item { Text(previewText) } }
                }
            }
            ElevatedCard(Modifier.fillMaxWidth().weight(.48f)) {
                selectedItem?.let { PreviewFileCard(it, modifier = Modifier.fillMaxSize()) }
                    ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Selecione um arquivo para visualizar aqui.")
                    }
            }
        }
    }
    if (renameFolderFrom != null) {
        AlertDialog(
            onDismissRequest = { renameFolderFrom = null },
            title = { Text("Renomear pasta") },
            text = {
                OutlinedTextField(renameFolderTo, { renameFolderTo = it }, label = { Text("Nome da pasta") }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = {
                    renameFolderFrom?.let { repo.renameFolder(type, it, renameFolderTo) }
                    folders = repo.listFolders(type)
                    items = repo.list(type)
                    if (selectedFolder == renameFolderFrom) selectedFolder = renameFolderTo
                    renameFolderFrom = null
                }) { Text("Salvar") }
            },
            dismissButton = { TextButton(onClick = { renameFolderFrom = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
fun PreviewFileCard(file: StoredFile, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val uri = remember(file.uri) { Uri.parse(file.uri) }
    val mimeType = remember(file.uri) { ctx.contentResolver.getType(uri).orEmpty() }
    val previewText = remember(file.id, mimeType) {
        if (mimeType.startsWith("text")) {
            runCatching {
                ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
            }.getOrDefault("Não foi possível carregar o conteúdo de texto.")
        } else ""
    }
    Column(modifier.padding(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(file.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Pasta: ${file.folder}", style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            }) { Icon(Icons.Default.OpenInNew, null) }
        }
        Spacer(Modifier.height(8.dp))
        when {
            mimeType.startsWith("image") -> AndroidView(
                factory = { android.widget.ImageView(it).apply { scaleType = android.widget.ImageView.ScaleType.FIT_CENTER } },
                update = { it.setImageURI(uri) },
                modifier = Modifier.fillMaxSize()
            )
            mimeType.startsWith("text") -> {
                ElevatedCard(Modifier.fillMaxSize()) {
                    LazyColumn(Modifier.fillMaxSize().padding(8.dp)) { item { Text(previewText) } }
                }
            }
            else -> ElevatedCard(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Pré-visualização rápida indisponível para este tipo de arquivo.")
                    Text("Tipo: ${mimeType.ifBlank { "desconhecido" }}")
                    Text("Adicionado em: ${DateFormat.getDateTimeInstance().format(file.createdAt)}")
                    Button(onClick = {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                    }) { Text("Abrir arquivo") }
                }
            }
        }
    }
}

@Composable fun NotesScreen(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val repo = remember { NotesRepository(ctx) }
    var mode by rememberSaveable { mutableStateOf("text") }
    var notes by remember { mutableStateOf(repo.list()) }
    var selectedNoteId by rememberSaveable { mutableStateOf<String?>(null) }
    var draftTitle by rememberSaveable { mutableStateOf("nota_${System.currentTimeMillis()}") }
    var draftText by rememberSaveable { mutableStateOf("") }
    Row(modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.widthIn(min = 220.dp).weight(.35f).border(1.dp, MaterialTheme.colorScheme.outlineVariant).padding(8.dp)) {
            Text("Anotações", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("As notas são salvas no armazenamento interno do app.", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(notes) { note ->
                    ListItem(
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                            selectedNoteId = note.id
                            if (note.kind == "text") {
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
        Column(Modifier.weight(.65f).fillMaxHeight()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(mode=="text", { mode="text" }, label={Text("Teclado")})
                FilterChip(mode=="hand", { mode="hand" }, label={Text("Mão livre")})
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
