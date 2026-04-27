package com.betpass.mc01pilot.data

data class ChecklistFile(
    val aircraft: String,
    val version: String,
    val source_note: String?,
    val categories: List<ChecklistCategory>,
    val checklists: List<ChecklistGroup> = emptyList()
)
data class ChecklistCategory(val id: String, val title: String, val items: List<ChecklistItem>)
data class ChecklistItem(val label: String, val action: String)
data class ChecklistGroup(val id: String, val title: String, val sections: List<ChecklistSection>)
data class ChecklistSection(val id: String, val title: String, val items: List<ChecklistItem>)
data class StoredFile(
    val id: String,
    val name: String,
    val folder: String? = null,
    val uri: String? = null,
    val type: String,
    val createdAt: Long,
    val parentId: String? = null,
    val contentText: String? = null,
    val updatedAt: Long? = null,
    val isFolder: Boolean = false
)
data class NoteFile(val id: String, val title: String, val kind: String, val updatedAt: Long)
data class NoteDraft(val mode: String, val title: String, val text: String, val selectedNoteId: String?)
enum class Module { CHECKLISTS, EMERGENCY, CHARTS, DOCUMENTS, NOTES, WEIGHT_BALANCE }
