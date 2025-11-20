package com.lifetxt.domain.parser

import com.lifetxt.model.InboxEntry
import java.time.LocalDate
import java.util.UUID

object InboxParser {
    private val dateRegex = Regex("""^\d{4}-\d{2}-\d{2}$""")

    fun parse(content: String): List<InboxEntry> {
        if (content.isBlank()) return emptyList()
        val entries = mutableListOf<InboxEntry>()
        var currentDate: LocalDate? = null
        val buffer = StringBuilder()
        fun flush() {
            val date = currentDate ?: return
            val body = buffer.toString().trim()
            if (body.isNotEmpty()) {
                val idSeed = "$date|$body"
                entries += InboxEntry(
                    id = UUID.nameUUIDFromBytes(idSeed.toByteArray()).toString(),
                    date = date,
                    body = body
                )
            }
            buffer.clear()
        }

        content.lineSequence().forEach { line ->
            if (dateRegex.matches(line.trim())) {
                flush()
                currentDate = LocalDate.parse(line.trim())
            } else {
                buffer.appendLine(line)
            }
        }
        flush()
        return entries.sortedByDescending { it.date }
    }

    fun format(entries: List<InboxEntry>): String =
        entries.sortedByDescending { it.date }
            .joinToString(separator = "\n\n") { entry ->
                buildString {
                    appendLine(entry.date.toString())
                    append(entry.body.trim())
                }
            }
}
