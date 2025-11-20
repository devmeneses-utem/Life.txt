package com.lifetxt.domain.parser

import com.lifetxt.model.ProjectEntry
import java.util.UUID

object ProjectsParser {
    fun parse(content: String): List<ProjectEntry> {
        if (content.isBlank()) return emptyList()
        return content.split("\n\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map {
                ProjectEntry(
                    id = UUID.randomUUID().toString(),
                    body = it
                )
            }
    }

    fun format(entries: List<ProjectEntry>): String =
        entries.joinToString(separator = "\n\n") { it.body.trim() }
}
