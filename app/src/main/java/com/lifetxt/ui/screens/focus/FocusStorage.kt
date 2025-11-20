package com.lifetxt.ui.screens.focus

import com.lifetxt.data.FileRepository
import com.lifetxt.data.LifeFile
import java.time.LocalDate

class FocusStorage(
    private val fileRepository: FileRepository
) {
    suspend fun readState(): FocusPersistence {
        val raw = fileRepository.readFile(LifeFile.FOCUS)
        if (raw.isBlank()) return FocusPersistence(emptyMap())
        val entries = mutableMapOf<LocalDate, Int>()
        raw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { line ->
                val parts = line.split("|")
                if (parts.size == 2) {
                    val day = runCatching { LocalDate.parse(parts[0]) }.getOrNull()
                    val seconds = parts[1].toIntOrNull()
                    if (day != null && seconds != null) {
                        entries[day] = seconds
                    }
                }
            }
        return FocusPersistence(entries.toMap())
    }

    suspend fun writeState(history: Map<LocalDate, Int>) {
        if (history.isEmpty()) {
            fileRepository.writeFile(LifeFile.FOCUS, "")
            return
        }
        val payload = history
            .toList()
            .sortedBy { it.first }
            .joinToString(separator = "\n") { (day, seconds) -> "${day}|$seconds" }
        fileRepository.writeFile(LifeFile.FOCUS, payload)
    }
}

data class FocusPersistence(
    val entries: Map<LocalDate, Int> = emptyMap()
)
