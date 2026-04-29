package com.betpass.mc01pilot.ai

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.gson.JsonParser
import java.util.Locale

data class SearchChunk(val source: String, val section: String, val text: String)
data class SearchResult(val chunk: SearchChunk, val score: Int)

@Composable
fun AiSearchScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val corpus = remember { mutableStateListOf<SearchChunk>() }
    val query = remember { mutableStateOf("") }
    val results = remember { mutableStateListOf<SearchResult>() }

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
            results.addAll(searchCorpus(query.value, corpus).take(6))
        }) { Text("Buscar") }

        if (results.isNotEmpty()) {
            val summary = buildShortSummary(query.value, results)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Resumo", fontWeight = FontWeight.Bold)
                    Text(summary)
                }
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(results) { item ->
                Card(Modifier.fillMaxWidth()) {
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
    }
}

private fun searchCorpus(query: String, corpus: List<SearchChunk>): List<SearchResult> {
    val terms = tokenize(query)
    if (terms.isEmpty()) return emptyList()
    return corpus.mapNotNull { chunk ->
        val hay = tokenize("${chunk.section} ${chunk.text} ${chunk.source}")
        val score = terms.sumOf { term -> if (hay.contains(term)) 3 else 0 } + terms.sumOf { t -> if (chunk.text.lowercase(Locale.getDefault()).contains(t)) 1 else 0 }
        if (score <= 0) null else SearchResult(chunk, score)
    }.sortedByDescending { it.score }
}

private fun buildShortSummary(query: String, results: List<SearchResult>): String {
    val top = results.take(3)
    val sources = top.map { it.chunk.source }.distinct()
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

    runCatching {
        context.assets.list("raw_docs")?.filter { it.endsWith(".pdf", ignoreCase = true) }?.forEach { pdf ->
            chunks.add(SearchChunk("raw_docs/$pdf", "Documento disponível", "Documento disponível para consulta manual: $pdf"))
        }
    }

    return chunks
}
