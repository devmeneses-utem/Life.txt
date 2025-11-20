package com.lifetxt.domain.parser

import com.lifetxt.model.CharacterProfile
import java.util.UUID

object CharactersParser {
    fun parse(content: String): List<CharacterProfile> {
        if (content.isBlank()) return emptyList()
        return content.split("\n\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { block ->
                val lines = block.lines()
                val name = lines.first().removePrefix("#").trim().ifBlank { "Sin nombre" }
                val description = lines.drop(1).joinToString("\n").trim().ifBlank { "Sin detalles" }
                val idSeed = "$name|$description"
                CharacterProfile(
                    id = UUID.nameUUIDFromBytes(idSeed.toByteArray()).toString(),
                    name = name,
                    description = description
                )
            }
    }

    fun format(profiles: List<CharacterProfile>): String =
        profiles.joinToString(separator = "\n\n") { profile ->
            buildString {
                appendLine("# ${profile.name}")
                append(profile.description.trim())
            }.trimEnd()
        }
}
