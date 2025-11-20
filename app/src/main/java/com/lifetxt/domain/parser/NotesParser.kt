package com.lifetxt.domain.parser

import com.lifetxt.model.AudioTimestamp
import com.lifetxt.model.NoteEntry
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

object NotesParser {
    private val noteHeaderRegex = Regex("""^\+\((\d{4}-\d{2}-\d{2})\)\s*(.+)$""")
    private val timestampRegex = Regex("""\+\[(\d{2}):(\d{2}):(\d{2})]""")

    fun parse(content: String): List<NoteEntry> {
        if (content.isBlank()) return emptyList()
        val notes = mutableListOf<NoteEntry>()
        var currentHeader: NoteHeader? = null
        val buffer = StringBuilder()

        fun flush() {
            val header = currentHeader ?: return
            val body = buffer.toString().trimEnd()
            val timestamps = extractTimestamps(body)
            val noteId = UUID.nameUUIDFromBytes("${header.date}_${header.title}".toByteArray()).toString()
            notes += NoteEntry(
                id = noteId,
                createdAt = LocalDateTime.of(header.date, LocalTime.MIDNIGHT),
                title = header.title,
                body = body,
                timestamps = timestamps
            )
            buffer.clear()
        }

        content.lineSequence().forEach { line ->
            val match = noteHeaderRegex.find(line.trim())
            if (match != null) {
                flush()
                currentHeader = NoteHeader(
                    date = LocalDate.parse(match.groupValues[1]),
                    title = match.groupValues[2].trim()
                )
            } else {
                buffer.appendLine(line)
            }
        }
        flush()
        return notes.sortedByDescending { it.createdAt }
    }

    fun format(notes: List<NoteEntry>): String =
        notes.sortedByDescending { it.createdAt }
            .joinToString(separator = "\n") { note ->
                buildString {
                    appendLine("+(${note.createdAt.toLocalDate()}) ${note.title}")
                    appendLine(note.body.trimEnd())
                    appendLine()
                }
            }
            .trimEnd()

    private fun extractTimestamps(body: String): List<AudioTimestamp> {
        return timestampRegex.findAll(body).map { match ->
            val hours = match.groupValues[1].toLong()
            val minutes = match.groupValues[2].toLong()
            val seconds = match.groupValues[3].toLong()
            val duration = Duration.ofHours(hours)
                .plusMinutes(minutes)
                .plusSeconds(seconds)
            AudioTimestamp(
                label = match.value,
                offset = duration
            )
        }.toList()
    }

    private data class NoteHeader(
        val date: LocalDate,
        val title: String
    )
}
