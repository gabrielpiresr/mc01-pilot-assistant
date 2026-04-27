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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.betpass.mc01pilot.ui.DrawingPad
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.betpass.mc01pilot.data.*
import java.io.FileOutputStream

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
    var left by remember { mutableStateOf(Module.CHECKLISTS) }
    var right by remember { mutableStateOf(Module.CHARTS) }
    var split by remember { mutableFloatStateOf(0.55f) }
    var width by remember { mutableIntStateOf(1) }
    MaterialTheme {
        Scaffold(topBar = { TopAppBar(title = { Text("MC01 Pilot Assistant") }) }) { pad ->
            if (cfg.screenWidthDp < 700) {
                var selected by remember { mutableStateOf(Module.CHECKLISTS) }
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
    var categoryIndex by remember { mutableIntStateOf(0) }
    var checked by remember { mutableStateOf(setOf<String>()) }
    val cat = checklist.categories[categoryIndex]
    Column(modifier.fillMaxSize()) {
        Text("${checklist.aircraft} • ${cat.title}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        checklist.source_note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        LinearProgressIndicator(progress = { checked.size.toFloat() / cat.items.size.coerceAtLeast(1) }, Modifier.fillMaxWidth().padding(vertical = 8.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(enabled = categoryIndex > 0, onClick = { categoryIndex--; checked = emptySet() }) { Icon(Icons.Default.ArrowBack, null); Text("Anterior") }
            Button(onClick = { checked = cat.items.map { cat.id + it.label }.toSet() }) { Text("Marcar tudo") }
            Button(enabled = categoryIndex < checklist.categories.lastIndex, onClick = { categoryIndex++; checked = emptySet() }) { Text("Próximo"); Icon(Icons.Default.ArrowForward, null) }
        }
    }
}

@Composable fun FileLibraryScreen(type: String, title: String, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val repo = remember { LibraryRepository(ctx) }
    var items by remember { mutableStateOf(repo.list(type)) }
    var folder by remember { mutableStateOf("Geral") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            ctx.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val name = it.lastPathSegment?.substringAfterLast('/') ?: "arquivo"
            repo.add(name, folder, it, type); items = repo.list(type)
        }
    }
    Column(modifier.fillMaxSize()) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(folder, { folder = it }, label = { Text("Pasta") }, modifier = Modifier.weight(1f), singleLine = true)
            Button(onClick = { picker.launch(arrayOf("application/pdf", "image/*", "text/*")) }) { Icon(Icons.Default.UploadFile, null); Text("Subir") }
        }
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items) { f ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Folder, null); Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) { Text(f.folder, fontWeight = FontWeight.Bold); Text(f.name) }
                        IconButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(f.uri)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }) { Icon(Icons.Default.OpenInNew, null) }
                        IconButton(onClick = { repo.delete(f.id); items = repo.list(type) }) { Icon(Icons.Default.Delete, null) }
                    }
                }
            }
        }
    }
}

@Composable fun NotesScreen(modifier: Modifier = Modifier) {
    var mode by remember { mutableStateOf("text") }
    Row(modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.widthIn(min = 170.dp).weight(.35f)) {
            Text("Anotações", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Row { FilterChip(mode=="text", { mode="text" }, label={Text("Teclado")}); Spacer(Modifier.width(6.dp)); FilterChip(mode=="hand", { mode="hand" }, label={Text("Mão livre")}) }
            Text("As notas são salvas no armazenamento interno do app.", style = MaterialTheme.typography.bodySmall)
        }
        if (mode == "text") TextNoteEditor(Modifier.weight(.65f)) else HandNoteEditor(Modifier.weight(.65f))
    }
}

@Composable fun TextNoteEditor(modifier: Modifier) {
    val ctx = LocalContext.current; val repo = remember { NotesRepository(ctx) }
    var title by remember { mutableStateOf("nota_${System.currentTimeMillis()}") }
    var text by remember { mutableStateOf("") }
    Column(modifier.fillMaxHeight()) {
        OutlinedTextField(title, { title = it }, label = { Text("Nome do arquivo") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(text, { text = it }, label = { Text("Escreva aqui") }, modifier = Modifier.weight(1f).fillMaxWidth())
        Button(onClick = { repo.saveText(title, text) }, Modifier.align(Alignment.End).padding(top = 8.dp)) { Text("Salvar") }
    }
}

@Composable fun HandNoteEditor(modifier: Modifier) {
    val ctx = LocalContext.current
    val repo = remember { NotesRepository(ctx) }
    var title by remember { mutableStateOf("rascunho_${System.currentTimeMillis()}") }
    var pad by remember { mutableStateOf<DrawingPad?>(null) }
    Column(modifier.fillMaxHeight()) {
        OutlinedTextField(title, { title = it }, label = { Text("Nome do arquivo") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        AndroidView(
            factory = { DrawingPad(it).also { view -> pad = view } },
            modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(12.dp))
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            OutlinedButton(onClick = { pad?.clear() }) { Text("Limpar") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { pad?.savePng(repo.drawingFile(title)) }) { Text("Salvar PNG") }
        }
    }
}
