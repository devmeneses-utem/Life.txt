package com.lifetxt.domain.parser

import com.lifetxt.model.CalendarTask
import com.lifetxt.model.TaskLabel
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.Locale

sealed interface RecurringEntry {
    val task: CalendarTask

    data class Annual(
        val month: Int,
        val day: Int,
        override val task: CalendarTask
    ) : RecurringEntry

    data class Monthly(
        val day: Int,
        override val task: CalendarTask
    ) : RecurringEntry

    data class Weekly(
        val dayOfWeek: DayOfWeek,
        override val task: CalendarTask
    ) : RecurringEntry
}

object RecurringParser {
    private val sectionRegex = Regex("""^@(anual|mensual|semanal)$""", RegexOption.IGNORE_CASE)
    private val annualRegex = Regex("""^(\d{2})-(\d{2})\s+(.+)$""")
    private val monthlyRegex = Regex("""^(\d{1,2})\s+(.+)$""")
    private val weeklyRegex = Regex("""^([a-z]{2,})\s+(.+)$""", RegexOption.IGNORE_CASE)
    private val labelRegex = Regex("""#([phwt])""", RegexOption.IGNORE_CASE)
    private val timeRegex = Regex("""\[(\d{2}:\d{2})](?:-\[(\d{2}:\d{2})])?""")

    fun parse(content: String): List<RecurringEntry> {
        if (content.isBlank()) return emptyList()
        val entries = mutableListOf<RecurringEntry>()
        var currentSection: Section = Section.ANNUAL
        content.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isBlank()) return@forEach
            val sectionMatch = sectionRegex.find(line)
            if (sectionMatch != null) {
                currentSection = Section.fromToken(sectionMatch.groupValues[1])
                return@forEach
            }
            when (currentSection) {
                Section.ANNUAL -> parseAnnual(line)?.let(entries::add)
                Section.MONTHLY -> parseMonthly(line)?.let(entries::add)
                Section.WEEKLY -> parseWeekly(line)?.let(entries::add)
            }
        }
        return entries
    }

    private fun parseAnnual(line: String): RecurringEntry.Annual? {
        val match = annualRegex.find(line) ?: return null
        val month = match.groupValues[1].toInt()
        val day = match.groupValues[2].toInt()
        val task = parseTask(match.groupValues[3]) ?: return null
        return RecurringEntry.Annual(month = month, day = day, task = task)
    }

    private fun parseMonthly(line: String): RecurringEntry.Monthly? {
        val match = monthlyRegex.find(line) ?: return null
        val day = match.groupValues[1].toInt()
        val task = parseTask(match.groupValues[2]) ?: return null
        return RecurringEntry.Monthly(day = day, task = task)
    }

    private fun parseWeekly(line: String): RecurringEntry.Weekly? {
        val match = weeklyRegex.find(line) ?: return null
        val dayToken = match.groupValues[1]
        val task = parseTask(match.groupValues[2]) ?: return null
        val dayOfWeek = dayOfWeekFromToken(dayToken) ?: return null
        return RecurringEntry.Weekly(dayOfWeek = dayOfWeek, task = task)
    }

    private fun parseTask(body: String): CalendarTask? {
        val timeMatch = timeRegex.find(body)
        val timeValue = timeMatch?.groupValues?.get(1)?.let { LocalTime.parse(it) }
        val endTimeValue = timeMatch?.groupValues?.get(2)?.takeIf { it.isNotBlank() }?.let { LocalTime.parse(it) }
        val labels = labelRegex.findAll(body)
            .mapNotNull { tokenToLabel(it.groupValues[1]) }
            .toSet()
        val description = body
            .replace(timeRegex, "")
            .replace(labelRegex, "")
            .removePrefix("+")
            .trim()
        if (description.isBlank()) return null
        return CalendarTask(description = description, time = timeValue, endTime = endTimeValue, labels = labels)
    }

    private fun dayOfWeekFromToken(token: String): DayOfWeek? {
        return when (token.lowercase(Locale.getDefault())) {
            "lun", "lunes" -> DayOfWeek.MONDAY
            "mar", "martes" -> DayOfWeek.TUESDAY
            "mie", "miercoles" -> DayOfWeek.WEDNESDAY
            "jue", "jueves" -> DayOfWeek.THURSDAY
            "vie", "viernes" -> DayOfWeek.FRIDAY
            "sab", "sabado" -> DayOfWeek.SATURDAY
            "dom", "domingo" -> DayOfWeek.SUNDAY
            else -> null
        }
    }

    private fun tokenToLabel(token: String): TaskLabel? = when (token.lowercase()) {
        "p" -> TaskLabel.PERSONAL
        "h" -> TaskLabel.HOME
        "w" -> TaskLabel.WORK
        "t" -> TaskLabel.URGENT
        else -> null
    }

    private enum class Section {
        ANNUAL, MONTHLY, WEEKLY;

        companion object {
            fun fromToken(token: String): Section = when (token.lowercase(Locale.getDefault())) {
                "mensual" -> MONTHLY
                "semanal" -> WEEKLY
                else -> ANNUAL
            }
        }
    }
}

