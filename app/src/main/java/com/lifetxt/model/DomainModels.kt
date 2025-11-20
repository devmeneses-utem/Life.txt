package com.lifetxt.model

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

enum class TaskLabel {
    PERSONAL, // #p
    HOME,     // #h
    WORK,     // #w
    URGENT    // #t
}

data class CalendarDay(
    val date: LocalDate,
    val tasks: List<CalendarTask>
)

data class CalendarTask(
    val id: String = UUID.randomUUID().toString(),
    val description: String,
    val time: LocalTime? = null,
    val endTime: LocalTime? = null,
    val labels: Set<TaskLabel> = emptySet()
)

fun CalendarTask.signature(): String =
    buildString {
        append(description.lowercase())
        append("|")
        append(time?.toString() ?: "")
        append("-")
        append(endTime?.toString() ?: "")
        append("|")
        append(labels.sortedBy { it.name }.joinToString(","))
    }

fun CalendarTask.isInlineRecurring(): Boolean {
    val trimmed = description.trim().lowercase()
    return trimmed.startsWith("@diario") ||
        trimmed.startsWith("@semanal") ||
        trimmed.startsWith("@anual")
}

enum class TodoPriority { A, B }

data class TodoTask(
    val id: String = UUID.randomUUID().toString(),
    val description: String,
    val priority: TodoPriority? = null,
    val labels: Set<TaskLabel> = emptySet(),
    val isDone: Boolean = false,
    val completedAt: LocalDate? = null
)

fun TodoTask.isInlineRecurring(): Boolean = description.trimStart().startsWith("@")

fun TodoTask.inlineRecurrenceSignature(): String? {
    val trimmed = description.trimStart()
    if (!trimmed.startsWith("@")) return null
    val whitespaceIndex = trimmed.indexOfFirst { it.isWhitespace() }
    return if (whitespaceIndex == -1) trimmed else trimmed.substring(0, whitespaceIndex)
}

data class InboxEntry(
    val id: String = UUID.randomUUID().toString(),
    val date: LocalDate,
    val body: String
)

data class CharacterProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String
)

data class AudioTimestamp(
    val label: String,
    val offset: Duration
)

data class NoteAttachment(
    val audioPath: String? = null,
    val imagePaths: List<String> = emptyList()
)

data class NoteEntry(
    val id: String = UUID.randomUUID().toString(),
    val createdAt: LocalDateTime,
    val title: String,
    val body: String,
    val timestamps: List<AudioTimestamp> = emptyList(),
    val attachments: NoteAttachment = NoteAttachment()
)

data class ProjectEntry(
    val id: String = UUID.randomUUID().toString(),
    val body: String
)
