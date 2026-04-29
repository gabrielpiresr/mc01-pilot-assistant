package com.betpass.mc01pilot.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.betpass.mc01pilot.BuildConfig

data class SearchChunk(val source: String, val section: String, val text: String, val page: Int? = null)
data class SearchResult(val chunk: SearchChunk, val score: Int)
private data class CachedChunk(val source: String, val page: Int, val text: String)

@Composable
fun AiSearchScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val corpus = remember { mutableStateListOf<SearchChunk>() }
    val query = remember { mutableStateOf("") }
    val results = remember { mutableStateListOf<SearchResult>() }
    val aiSummary = remember { mutableStateOf<String?>(null) }
    val loading = remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val previewChunk = remember { mutableStateOf<SearchChunk?>(null) }

    LaunchedEffect(Unit) {
        corpus.clear()
        corpus.addAll(loadSearchCorpus(context))
    }

    Column(modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Busca IA MC01", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Resumo curto + trecho + fonte. Em caso de conflito, o app destaca fontes divergentes.")
        OutlinedTextField(
            value = query.value,
            onValueChange = { query.value = it },
            label = { Text("Dúvida operacional") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = {
            results.clear()
            aiSummary.value = null
            val top = searchCorpus(query.value, corpus).take(8)
            results.addAll(top)
            if (top.isNotEmpty()) {
                loading.value = true
                scope.launch {
                    aiSummary.value = generateAiSummary(query.value, top) ?: buildShortSummary(query.value, top)
                    loading.value = false
                }
            }
        }) { Text(if (loading.value) "Consultando IA..." else "Buscar") }

        if (results.isNotEmpty()) {
            val summary = aiSummary.value ?: buildShortSummary(query.value, results)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Resumo", fontWeight = FontWeight.Bold)
                    Text(summary)
                }
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(results) { item ->
                Card(Modifier.fillMaxWidth().clickable { if (item.chunk.page != null && item.chunk.source.endsWith(".pdf")) previewChunk.value = item.chunk }) {
                    Column(Modifier.padding(12.dp)) {
                        Text(item.chunk.section, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Text(item.chunk.text)
                        Spacer(Modifier.height(6.dp))
                        Text("Fonte: ${item.chunk.source}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        previewChunk.value?.let { chunk ->
            PdfPreviewSheet(chunk = chunk, onDismiss = { previewChunk.value = null })
        }
    }
}

private fun searchCorpus(query: String, corpus: List<SearchChunk>): List<SearchResult> {
    val terms = tokenize(query)
    if (terms.isEmpty()) return emptyList()
    return corpus.mapNotNull { chunk ->
        val hay = tokenize("${chunk.section} ${chunk.text} ${chunk.source}")
        val score = terms.fold(0) { acc, term ->
            acc + if (hay.contains(term)) 3 else 0
        } + terms.fold(0) { acc, t ->
            acc + if (chunk.text.lowercase(Locale.getDefault()).contains(t)) 1 else 0
        }
        if (score <= 0) null else SearchResult(chunk, score)
    }.sortedByDescending { it.score }
}

private fun buildShortSummary(query: String, results: List<SearchResult>): String {
    val top = results.take(3)
    val sources = top.map { it.chunk.source.substringBefore(" • pág") }.distinct()
    val conflict = if (sources.size > 1) " Possível conflito entre documentos; valide as fontes." else ""
    val snippets = top.joinToString(" ") { it.chunk.text.take(120) }
    return "Para: '$query'. $snippets$conflict"
}

private fun tokenize(value: String): Set<String> =
    value.lowercase(Locale.getDefault())
        .replace(Regex("[^a-z0-9à-úç ]"), " ")
        .split(Regex("\\s+"))
        .filter { it.length >= 3 }
        .toSet()

private fun loadSearchCorpus(context: Context): List<SearchChunk> {
    PDFBoxResourceLoader.init(context)
    val chunks = mutableListOf<SearchChunk>()
    fun readAsset(path: String): String = context.assets.open(path).bufferedReader().use { it.readText() }

    runCatching {
        val checklistJson = JsonParser.parseString(readAsset("checklists/mc01_checklist.json")).asJsonObject
        checklistJson.getAsJsonArray("checklists")?.forEach { cl ->
            val clObj = cl.asJsonObject
            val checklistName = clObj.get("name")?.asString ?: "Checklist"
            clObj.getAsJsonArray("sections")?.forEach { sec ->
                val secObj = sec.asJsonObject
                val secName = secObj.get("name")?.asString ?: "Seção"
                secObj.getAsJsonArray("items")?.forEach { item ->
                    val itObj = item.asJsonObject
                    val label = itObj.get("label")?.asString ?: return@forEach
                    val action = itObj.get("action")?.asString ?: ""
                    chunks.add(SearchChunk("checklists/mc01_checklist.json", "$checklistName • $secName", "$label: $action"))
                }
            }
        }
    }

    runCatching {
        val emergencyJson = JsonParser.parseString(readAsset("checklists/mc01_emergency.json")).asJsonObject
        emergencyJson.getAsJsonArray("checklists")?.forEach { cl ->
            val clObj = cl.asJsonObject
            val checklistName = clObj.get("name")?.asString ?: "Emergência"
            clObj.getAsJsonArray("sections")?.forEach { sec ->
                val secObj = sec.asJsonObject
                val secName = secObj.get("name")?.asString ?: "Seção"
                secObj.getAsJsonArray("items")?.forEach { item ->
                    val itObj = item.asJsonObject
                    val label = itObj.get("label")?.asString ?: return@forEach
                    val action = itObj.get("action")?.asString ?: ""
                    chunks.add(SearchChunk("checklists/mc01_emergency.json", "$checklistName • $secName", "$label: $action"))
                }
            }
        }
    }

    runCatching {
        val rawReadme = readAsset("raw_docs/read.me")
        rawReadme.lines().filter { it.isNotBlank() }.forEachIndexed { idx, line ->
            chunks.add(SearchChunk("raw_docs/read.me", "Observações raw_docs #${idx + 1}", line.trim()))
        }
    }

    chunks.addAll(loadPdfChunks(context))
    return chunks
}

private fun loadPdfChunks(context: Context): List<SearchChunk> {
    val pdfs = context.assets.list("raw_docs")?.filter { it.endsWith(".pdf", ignoreCase = true) } ?: emptyList()
    if (pdfs.isEmpty()) return emptyList()

    val cacheFile = File(context.filesDir, "ai_pdf_cache_v1.json")
    val gson = Gson()
    if (cacheFile.exists()) {
        runCatching {
            val type = object : TypeToken<List<CachedChunk>>() {}.type
            val cached: List<CachedChunk> = gson.fromJson(cacheFile.readText(), type) ?: emptyList()
            if (cached.isNotEmpty()) {
                return cached.map { SearchChunk(it.source, "Trecho PDF • pág ${it.page}", it.text, page = it.page) }
            }
        }
    }

    val extracted = mutableListOf<CachedChunk>()
    val stripper = PDFTextStripper()
    pdfs.forEach { pdfName ->
        runCatching {
            context.assets.open("raw_docs/$pdfName").use { input ->
                PDDocument.load(input).use { doc ->
                    val totalPages = doc.numberOfPages
                    for (page in 1..totalPages) {
                        stripper.startPage = page
                        stripper.endPage = page
                        val text = (stripper.getText(doc) ?: "").replace(Regex("\\s+"), " ").trim()
                        if (text.length < 80) continue
                        chunkText(text).forEach { part ->
                            extracted.add(CachedChunk("raw_docs/$pdfName", page, part))
                        }
                    }
                }
            }
        }
    }

    if (extracted.isNotEmpty()) {
        runCatching { cacheFile.writeText(gson.toJson(extracted)) }
    }

    return extracted.map { SearchChunk(it.source, "Trecho PDF • pág ${it.page}", it.text) }
}



@Composable
private fun PdfPreviewSheet(chunk: SearchChunk, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val preview = remember(chunk.source, chunk.page) { renderPdfAssetPage(context, chunk.source, chunk.page ?: 1) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().fillMaxHeight().padding(12.dp).verticalScroll(rememberScrollState())) {
            Text("${chunk.source} • pág ${chunk.page}", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            if (preview != null) {
                Image(bitmap = preview.asImageBitmap(), contentDescription = "Preview PDF", modifier = Modifier.fillMaxWidth())
            } else {
                Text("Não foi possível renderizar a página.")
            }
            Spacer(Modifier.height(10.dp))
            Text(chunk.text)
            TextButton(onClick = onDismiss) { Text("Fechar") }
        }
    }
}

private fun renderPdfAssetPage(context: Context, assetPath: String, page: Int): Bitmap? {
    return runCatching {
        val safePage = if (page <= 0) 1 else page
        val cacheFile = File(context.cacheDir, "preview_${assetPath.substringAfterLast('/')}_${safePage}.pdf")
        context.assets.open(assetPath).use { input -> cacheFile.outputStream().use { input.copyTo(it) } }
        val pfd = ParcelFileDescriptor.open(cacheFile, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        val targetPage = (safePage - 1).coerceIn(0, renderer.pageCount - 1)
        val pageObj = renderer.openPage(targetPage)
        val width = (pageObj.width * 2).coerceAtLeast(1200)
        val height = (pageObj.height * 2).coerceAtLeast(1600)
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        pageObj.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        pageObj.close(); renderer.close(); pfd.close()
        bmp
    }.getOrNull()
}

private suspend fun generateAiSummary(query: String, results: List<SearchResult>): String? = withContext(Dispatchers.IO) {
    if (BuildConfig.OPENAI_API_KEY.isBlank()) return@withContext null
    runCatching {
        val contextBlock = results.take(5).joinToString("\n\n") {
            "Fonte: ${it.chunk.source} | ${it.chunk.section}\nTrecho: ${it.chunk.text.take(700)}"
        }
        val payload = mapOf(
            "model" to BuildConfig.OPENAI_MODEL,
            "input" to listOf(
                mapOf("role" to "system", "content" to "Você é assistente operacional do MC01. Responda em PT-BR, objetivo, sem inventar. Sempre cite fonte(s). Se houver conflito, diga explicitamente."),
                mapOf("role" to "user", "content" to "Pergunta: $query\n\nContexto recuperado:\n$contextBlock\n\nResponda com: 1) resumo curto, 2) conflito (se houver), 3) fontes usadas.")
            )
        )

        val conn = (URL("https://api.openai.com/v1/responses").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Authorization", "Bearer ${BuildConfig.OPENAI_API_KEY}")
            setRequestProperty("Content-Type", "application/json")
            doOutput = true
            connectTimeout = 15000
            readTimeout = 30000
        }
        OutputStreamWriter(conn.outputStream).use { it.write(Gson().toJson(payload)) }
        val raw = (if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.use { it.readText() } ?: return@runCatching null
        val json = JsonParser.parseString(raw).asJsonObject
        json.getAsJsonArray("output")?.forEach { out ->
            val content = out.asJsonObject.getAsJsonArray("content") ?: return@forEach
            content.forEach { c ->
                val obj = c.asJsonObject
                if (obj.get("type")?.asString == "output_text") {
                    return@runCatching obj.get("text")?.asString
                }
            }
        }
        null
    }.getOrNull()
}

private fun chunkText(text: String, size: Int = 900, overlap: Int = 120): List<String> {
    if (text.length <= size) return listOf(text)
    val out = mutableListOf<String>()
    var i = 0
    while (i < text.length) {
        val end = minOf(i + size, text.length)
        val chunk = text.substring(i, end).trim()
        if (chunk.length >= 120) out.add(chunk)
        if (end == text.length) break
        i += (size - overlap)
    }
    return out
}
