package com.betpass.mc01pilot.data

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import java.io.File
import java.util.UUID

class ChecklistRepository(private val context: Context) {
    private val gson = Gson()

    fun load(): ChecklistFile {
        val jsonText = context.assets
            .open("checklists/mc01_checklist.json")
            .bufferedReader()
            .use { it.readText() }

        val root = JsonParser.parseString(jsonText).asJsonObject

        val aircraft = root.get("aircraft")?.asString ?: "MC01"
        val version = root.get("version")?.asString ?: ""
        val sourceNote = root.get("source_note")?.asString

        // Formato antigo: categories
        if (root.has("categories")) {
            return gson.fromJson(jsonText, ChecklistFile::class.java)
        }

        // Formato novo: checklists -> sections/items
        val categories = mutableListOf<ChecklistCategory>()

        val checklists = root.getAsJsonArray("checklists") ?: JsonArray()

        checklists.forEach { checklistElement ->
            val checklist = checklistElement.asJsonObject
            val checklistId = checklist.get("id")?.asString ?: safeId(checklist.get("name")?.asString ?: "checklist")
            val checklistName = checklist.get("name")?.asString ?: checklistId

            // Caso tenha sections
            if (checklist.has("sections")) {
                checklist.getAsJsonArray("sections").forEach { sectionElement ->
                    val section = sectionElement.asJsonObject
                    val sectionName = section.get("name")?.asString ?: checklistName
                    val sectionId = "${checklistId}_${safeId(sectionName)}"

                    categories.add(
                        ChecklistCategory(
                            id = sectionId,
                            title = "$checklistName • $sectionName",
                            items = parseItems(section.get("items"))
                        )
                    )
                }
            }

            // Caso tenha items direto no checklist
            if (checklist.has("items")) {
                categories.add(
                    ChecklistCategory(
                        id = checklistId,
                        title = checklistName,
                        items = parseItems(checklist.get("items"))
                    )
                )
            }
        }

        return ChecklistFile(
            aircraft = aircraft,
            version = version,
            source_note = sourceNote,
            categories = categories
        )
    }

    private fun parseItems(element: JsonElement?): List<ChecklistItem> {
        if (element == null || !element.isJsonArray) return emptyList()

        return element.asJsonArray.mapNotNull { itemElement ->
            val item = itemElement.asJsonObject
            val label = item.get("label")?.asString ?: return@mapNotNull null
            val action = item.get("action")?.asString ?: ""
            ChecklistItem(label = label, action = action)
        }
    }

    private fun safeId(value: String): String {
        return value
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
    }
}

class LibraryRepository(private val context: Context) {
    private val gson = Gson()
    private val file = File(context.filesDir, "library.json")
    private val foldersFile = File(context.filesDir, "library_folders.json")
    private val listType = object : TypeToken<List<StoredFile>>() {}.type
    private val foldersType = object : TypeToken<Map<String, List<String>>>() {}.type

    fun list(type: String): List<StoredFile> =
        read().filter { it.type == type }.sortedWith(compareBy({ it.folder }, { it.name }))

    fun listFolders(type: String): List<String> {
        val fromFiles = list(type).map { it.folder }
        val stored = readFolders()[type].orEmpty()
        return (fromFiles + stored + "Geral").distinct().sorted()
    }

    fun createFolder(type: String, name: String) {
        val safeName = name.trim()
        if (safeName.isBlank()) return
        val all = readFolders().toMutableMap()
        val current = all[type].orEmpty().toMutableList()
        if (safeName !in current) current.add(safeName)
        all[type] = current.sorted()
        writeFolders(all)
    }

    fun renameFolder(type: String, oldName: String, newName: String) {
        val safeNew = newName.trim()
        if (safeNew.isBlank() || oldName == safeNew) return
        val renamedFiles = read().map { item ->
            if (item.type == type && item.folder == oldName) item.copy(folder = safeNew) else item
        }
        write(renamedFiles)
        val all = readFolders().toMutableMap()
        val current = all[type].orEmpty().toMutableList()
        current.remove(oldName)
        if (safeNew !in current) current.add(safeNew)
        all[type] = current.sorted()
        writeFolders(all)
    }

    fun deleteFolder(type: String, name: String) {
        val migrated = read().map { item ->
            if (item.type == type && item.folder == name) item.copy(folder = "Geral") else item
        }
        write(migrated)
        val all = readFolders().toMutableMap()
        all[type] = all[type].orEmpty().filterNot { it == name }
        writeFolders(all)
    }

    fun add(name: String, folder: String, uri: Uri, type: String): StoredFile {
        val item = StoredFile(
            UUID.randomUUID().toString(),
            name,
            folder.ifBlank { "Geral" },
            uri.toString(),
            type,
            System.currentTimeMillis()
        )
        write(read() + item)
        return item
    }

    fun delete(id: String) = write(read().filterNot { it.id == id })

    private fun read(): List<StoredFile> =
        if (!file.exists()) emptyList()
        else gson.fromJson(file.readText(), listType) ?: emptyList()

    private fun write(items: List<StoredFile>) {
        file.writeText(gson.toJson(items))
    }

    private fun readFolders(): Map<String, List<String>> =
        if (!foldersFile.exists()) emptyMap()
        else gson.fromJson(foldersFile.readText(), foldersType) ?: emptyMap()

    private fun writeFolders(items: Map<String, List<String>>) {
        foldersFile.writeText(gson.toJson(items))
    }
}

class NotesRepository(private val context: Context) {
    private val dir = File(context.filesDir, "notes").apply { mkdirs() }

    fun list(): List<NoteFile> =
        dir.listFiles()?.map { f ->
            val kind = if (f.extension == "png") "hand" else "text"
            NoteFile(f.nameWithoutExtension, f.nameWithoutExtension, kind, f.lastModified())
        }?.sortedByDescending { it.updatedAt } ?: emptyList()

    fun saveText(title: String, text: String) {
        File(dir, safe(title) + ".txt").writeText(text)
    }

    fun readText(id: String): String =
        File(dir, safe(id) + ".txt").takeIf { it.exists() }?.readText() ?: ""

    fun drawingFile(title: String): File =
        File(dir, safe(title) + ".png")

    fun delete(note: NoteFile) {
        File(dir, safe(note.id) + if (note.kind == "hand") ".png" else ".txt").delete()
    }

    private fun safe(s: String) =
        s.ifBlank { "nota_${System.currentTimeMillis()}" }
            .replace(Regex("[^A-Za-z0-9_-]"), "_")
}
