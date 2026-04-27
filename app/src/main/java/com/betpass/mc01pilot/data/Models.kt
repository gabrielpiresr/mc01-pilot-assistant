package com.betpass.mc01pilot.data

data class ChecklistFile(val aircraft: String, val version: String, val source_note: String?, val categories: List<ChecklistCategory>)
data class ChecklistCategory(val id: String, val title: String, val items: List<ChecklistItem>)
data class ChecklistItem(val label: String, val action: String)
data class StoredFile(val id: String, val name: String, val folder: String, val uri: String, val type: String, val createdAt: Long)
data class NoteFile(val id: String, val title: String, val kind: String, val updatedAt: Long)
data class NoteDraft(val mode: String, val title: String, val text: String, val selectedNoteId: String?)
enum class Module { CHECKLISTS, CHARTS, DOCUMENTS, NOTES }
