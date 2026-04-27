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
    private val prefs by lazy { context.getSharedPreferences("checklist_prefs", Context.MODE_PRIVATE) }
    private val favoritesKey = "favorite_category_ids"

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
            val legacyFile = gson.fromJson(jsonText, ChecklistFile::class.java)
            return legacyFile.copy(
                categories = legacyFile.categories,
                checklists = legacyFile.categories.map { category ->
                    ChecklistGroup(
                        id = category.id,
                        title = category.title,
                        sections = listOf(
                            ChecklistSection(
                                id = "${category.id}_items",
                                title = "",
                                items = category.items
                            )
                        )
                    )
                }
            )
        }

        // Formato novo: checklists -> sections/items
        val categories = mutableListOf<ChecklistCategory>()
        val groupedChecklists = mutableListOf<ChecklistGroup>()

        val checklists = root.getAsJsonArray("checklists") ?: JsonArray()

        checklists.forEach { checklistElement ->
            val checklist = checklistElement.asJsonObject
            val checklistId = checklist.get("id")?.asString ?: safeId(checklist.get("name")?.asString ?: "checklist")
            val checklistName = checklist.get("name")?.asString ?: checklistId
            val sections = mutableListOf<ChecklistSection>()

            // Caso tenha sections
            if (checklist.has("sections")) {
                checklist.getAsJsonArray("sections").forEach { sectionElement ->
                    val section = sectionElement.asJsonObject
                    val sectionName = section.get("name")?.asString ?: checklistName
                    val sectionId = "${checklistId}_${safeId(sectionName)}"
                    val items = parseItems(section.get("items"))

                    categories.add(
                        ChecklistCategory(
                            id = sectionId,
                            title = "$checklistName • $sectionName",
                            items = items
                        )
                    )
                    sections.add(
                        ChecklistSection(
                            id = sectionId,
                            title = sectionName,
                            items = items
                        )
                    )
                }
            }

            // Caso tenha items direto no checklist
            if (checklist.has("items")) {
                val items = parseItems(checklist.get("items"))
                categories.add(
                    ChecklistCategory(
                        id = checklistId,
                        title = checklistName,
                        items = items
                    )
                )
                sections.add(
                    ChecklistSection(
                        id = "${checklistId}_items",
                        title = "",
                        items = items
                    )
                )
            }

            if (sections.isNotEmpty()) {
                groupedChecklists.add(
                    ChecklistGroup(
                        id = checklistId,
                        title = checklistName,
                        sections = sections
                    )
                )
            }
        }

        return ChecklistFile(
            aircraft = aircraft,
            version = version,
            source_note = sourceNote,
            categories = categories,
            checklists = groupedChecklists
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

    fun loadFavoriteCategoryIds(): Set<String> =
        prefs.getStringSet(favoritesKey, emptySet())?.toSet() ?: emptySet()

    fun saveFavoriteCategoryIds(ids: Set<String>) {
        prefs.edit().putStringSet(favoritesKey, ids).apply()
    }
}

class LibraryRepository(private val context: Context) {
    private val gson = Gson()
    private val file = File(context.filesDir, "library.json")
    private val listType = object : TypeToken<List<StoredFile>>() {}.type

    fun list(type: String): List<StoredFile> = normalized(type)

    fun createFolder(type: String, name: String, parentId: String?): StoredFile {
        val safeName = name.trim()
        require(safeName.isNotBlank()) { "Nome inválido" }
        val folder = StoredFile(
            id = UUID.randomUUID().toString(),
            name = safeName,
            type = type,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            parentId = parentId,
            isFolder = true
        )
        write(read() + folder)
        return folder
    }

    fun createPlaceholderFile(type: String, name: String, parentId: String?): StoredFile {
        val safeName = name.trim()
        require(safeName.isNotBlank()) { "Nome inválido" }
        val item = StoredFile(
            id = UUID.randomUUID().toString(),
            name = safeName,
            type = type,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            parentId = parentId,
            contentText = ""
        )
        write(read() + item)
        return item
    }

    fun addImported(name: String, parentId: String?, uri: Uri, type: String): StoredFile {
        val item = StoredFile(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifBlank { "arquivo" },
            uri = uri.toString(),
            type = type,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            parentId = parentId
        )
        write(read() + item)
        return item
    }

    fun nameExists(type: String, parentId: String?, name: String): Boolean {
        val safeName = name.trim()
        if (safeName.isBlank()) return false
        return normalized(type).any {
            it.parentId == parentId && it.name.equals(safeName, ignoreCase = true)
        }
    }

    fun delete(id: String) {
        val all = read()
        val descendants = collectDescendantIds(all, id)
        write(all.filterNot { it.id == id || it.id in descendants })
    }

    fun rename(id: String, newName: String): Boolean {
        val safeName = newName.trim()
        if (safeName.isBlank()) return false
        val all = read()
        val target = all.firstOrNull { it.id == id } ?: return false
        if (all.any {
                it.id != id &&
                    it.type == target.type &&
                    it.parentId == target.parentId &&
                    it.name.equals(safeName, ignoreCase = true)
            }
        ) return false
        write(
            all.map {
                if (it.id == id) it.copy(name = safeName, updatedAt = System.currentTimeMillis()) else it
            }
        )
        return true
    }

    private fun read(): List<StoredFile> =
        if (!file.exists()) emptyList()
        else gson.fromJson(file.readText(), listType) ?: emptyList()

    private fun write(items: List<StoredFile>) {
        file.writeText(gson.toJson(items))
    }

    private fun normalized(type: String): List<StoredFile> {
        val source = read().filter { it.type == type }
        val explicitFolders = source.filter { it.isFolder }
        val legacyFolderNames = source.mapNotNull { it.folder?.takeIf { folder -> folder.isNotBlank() && folder != "Geral" } }.distinct()
        val legacyFolders = legacyFolderNames
            .filterNot { name -> explicitFolders.any { it.name.equals(name, ignoreCase = true) && it.parentId == null } }
            .map { name ->
                StoredFile(
                    id = legacyFolderId(type, name),
                    name = name,
                    type = type,
                    createdAt = System.currentTimeMillis(),
                    parentId = null,
                    updatedAt = System.currentTimeMillis(),
                    isFolder = true
                )
            }
        val normalizedFiles = source.filterNot { it.isFolder }.map { item ->
            if (item.parentId != null || item.folder == null || item.folder == "Geral") item
            else item.copy(parentId = legacyFolderId(type, item.folder))
        }
        return (explicitFolders + legacyFolders + normalizedFiles)
            .distinctBy { it.id }
            .sortedWith(compareBy<StoredFile>({ !it.isFolder }, { it.name.lowercase() }))
    }

    private fun collectDescendantIds(items: List<StoredFile>, rootId: String): Set<String> {
        val result = mutableSetOf<String>()
        val queue = ArrayDeque<String>()
        queue.add(rootId)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            items.filter { it.parentId == current }.forEach { child ->
                if (result.add(child.id)) queue.add(child.id)
            }
        }
        return result
    }

    private fun legacyFolderId(type: String, folderName: String): String =
        "legacy_${type}_${folderName.trim().lowercase().replace(Regex("[^a-z0-9]+"), "_")}"
}

class NotesRepository(private val context: Context) {
    private val gson = Gson()
    private val dir = File(context.filesDir, "notes").apply { mkdirs() }
    private val draftFile = File(dir, "_draft.json")

    fun list(): List<NoteFile> =
        dir.listFiles()?.filterNot { it.name == "_draft.json" }?.map { f ->
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

    fun loadDraft(): NoteDraft? =
        if (!draftFile.exists()) null
        else runCatching { gson.fromJson(draftFile.readText(), NoteDraft::class.java) }.getOrNull()

    fun saveDraft(draft: NoteDraft) {
        draftFile.writeText(gson.toJson(draft))
    }

    fun delete(note: NoteFile) {
        File(dir, safe(note.id) + if (note.kind == "hand") ".png" else ".txt").delete()
    }

    private fun safe(s: String) =
        s.ifBlank { "nota_${System.currentTimeMillis()}" }
            .replace(Regex("[^A-Za-z0-9_-]"), "_")
}
