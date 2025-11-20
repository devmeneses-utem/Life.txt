package com.lifetxt.domain.parser

import com.lifetxt.model.CalendarDay
import com.lifetxt.model.CalendarTask
import com.lifetxt.model.TaskLabel
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID

object CalendarParser {
    private val dayRegex = Regex("""^(\d{4}-\d{2}-\d{2})""")
    private val taskRegex = Regex("""^\+\s*(?:\[(\d{2}:\d{2})](?:-\[(\d{2}:\d{2})])?)?\s*(.+)$""")
    private val labelRegex = Regex("""#([phwt])""", RegexOption.IGNORE_CASE)
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun parse(content: String): List<CalendarDay> {
        if (content.isBlank()) return emptyList()
        val days = mutableListOf<CalendarDay>()
        var currentDate: LocalDate? = null
        val tasksBuffer = mutableListOf<CalendarTask>()
        content.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isBlank()) return@forEach
            val dayMatch = dayRegex.find(line)
            if (dayMatch != null) {
                currentDate?.let { date ->
                    days += CalendarDay(date, tasksBuffer.toList())
                }
                currentDate = LocalDate.parse(dayMatch.groupValues[1])
                tasksBuffer.clear()
            } else {
                val date = currentDate ?: return@forEach
                parseTask(line, date)?.let { tasksBuffer += it }
            }
        }
        currentDate?.let { date ->
            days += CalendarDay(date, tasksBuffer.toList())
        }
        return days
    }

    fun format(days: List<CalendarDay>): String {
        return buildString {
            days.sortedBy { it.date }.forEach { day ->
                appendLine(day.date.toString())
                day.tasks.forEach { task ->
                    append("+ ")
                    when {
                        task.time != null && task.endTime != null ->
                            append("[${task.time.format(timeFormatter)}]-[${task.endTime.format(timeFormatter)}] ")
                        task.time != null ->
                            append("[${task.time.format(timeFormatter)}] ")
                    }
                    append(task.description)
                    if (task.labels.isNotEmpty()) {
                        append(" ")
                        append(task.labels.joinToString(" ") { label ->
                            when (label) {
                                TaskLabel.PERSONAL -> "#p"
                                TaskLabel.HOME -> "#h"
                                TaskLabel.WORK -> "#w"
                                TaskLabel.URGENT -> "#t"
                            }
                        })
                    }
                    appendLine()
                }
                appendLine()
            }
        }.trimEnd()
    }

    fun upsertDay(days: List<CalendarDay>, day: CalendarDay): List<CalendarDay> {
        val mutable = days.toMutableList()
        val index = mutable.indexOfFirst { it.date == day.date }
        if (index >= 0) mutable[index] = day else mutable += day
        return mutable.sortedBy { it.date }
    }

    private fun parseTask(rawLine: String, date: LocalDate): CalendarTask? {
        val line = sanitizeTaskLine(rawLine)
        if (!line.startsWith("+")) return null
        val match = taskRegex.find(line) ?: return null
        val timeText = match.groupValues[1]
        val endTimeText = match.groupValues[2]
        val descriptionRaw = match.groupValues[3].trim()
        val labels = labelRegex.findAll(descriptionRaw)
            .mapNotNull { labelFromToken(it.groupValues[1]) }
            .toSet()
        val description = descriptionRaw.replace(labelRegex, "").trim()
        val time = timeText.takeIf { it.isNotBlank() }?.let { LocalTime.parse(it) }
        val endTime = endTimeText.takeIf { it.isNotBlank() }?.let { LocalTime.parse(it) }
        val signature = "$date|$line"
        val id = UUID.nameUUIDFromBytes(signature.toByteArray()).toString()
        return CalendarTask(id = id, description = description, time = time, endTime = endTime, labels = labels)
    }

    private fun labelFromToken(token: String): TaskLabel? = when (token.lowercase()) {
        "p" -> TaskLabel.PERSONAL
        "h" -> TaskLabel.HOME
        "w" -> TaskLabel.WORK
        "t" -> TaskLabel.URGENT
        else -> null
    }

    private fun sanitizeTaskLine(rawLine: String): String {
        val trimmed = rawLine.trim()
        val withoutMarkers = trimmed.trimStart {
            it.isWhitespace() ||
                it == '\u2022' ||
                it == '-' ||
                it == '\u2013' ||
                it == '\u2014' ||
                it == '\u00B7' ||
                it == '\u00E2' ||
                it == '\u20AC' ||
                it == '\u00A2'
        }
        return withoutMarkers.trimStart()
    }
}


