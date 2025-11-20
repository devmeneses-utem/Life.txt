package com.lifetxt.domain.scripts

import com.lifetxt.data.FileRepository
import com.lifetxt.data.LifeFile
import com.lifetxt.domain.parser.CalendarParser
import com.lifetxt.model.CalendarDay
import com.lifetxt.model.CalendarTask
import com.lifetxt.model.signature
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.UUID

object CalendarScripts {
    suspend fun generateYearIfMissing(
        repository: FileRepository,
        year: Int,
        dispatcher: CoroutineDispatcher
    ) = withContext(dispatcher) {
        val existing = CalendarParser.parse(repository.readFile(LifeFile.CALENDAR))
            .associateBy { it.date }
            .toMutableMap()
        val start = LocalDate.of(year, 1, 1)
        val end = LocalDate.of(year, 12, 31)
        var cursor = start
        while (!cursor.isAfter(end)) {
            existing.putIfAbsent(cursor, CalendarDay(cursor, emptyList()))
            cursor = cursor.plusDays(1)
        }
        repository.writeFile(LifeFile.CALENDAR, CalendarParser.format(existing.values.sortedBy { it.date }))
    }

    suspend fun archivePastDays(
        repository: FileRepository,
        today: LocalDate,
        dispatcher: CoroutineDispatcher
    ) = withContext(dispatcher) {
        val currentDays = CalendarParser.parse(repository.readFile(LifeFile.CALENDAR))
        val (past, future) = currentDays.partition { it.date.isBefore(today) }
        if (past.isEmpty()) return@withContext
        val pastText = CalendarParser.format(past)
        val existingPast = repository.readFile(LifeFile.CALENDAR_PAST)
        val merged = listOf(pastText, existingPast)
            .filter { it.isNotBlank() }
            .joinToString(separator = "\n\n")
        repository.writeFile(LifeFile.CALENDAR, CalendarParser.format(future))
        repository.writeFile(LifeFile.CALENDAR_PAST, merged)
    }

    suspend fun applyRecurring(
        repository: FileRepository,
        fromDate: LocalDate,
        horizonWeeks: Int,
        monthSpan: Int,
        dispatcher: CoroutineDispatcher
    ) = withContext(dispatcher) {
        val days = CalendarParser.parse(repository.readFile(LifeFile.CALENDAR)).associateBy { it.date }.toMutableMap()
        val horizonEnd = run {
            val weeksEnd = fromDate.plusWeeks(horizonWeeks.toLong())
            val monthsEnd = fromDate.plusMonths(monthSpan.toLong())
            if (monthsEnd.isAfter(weeksEnd)) monthsEnd else weeksEnd
        }

        val skipEntriesRaw = parseSkipEntries(repository.readFile(LifeFile.CALENDAR_SKIP))
        val skipEntries = skipEntriesRaw.filter { entry ->
            val existingDay = days[entry.date]
            existingDay?.tasks?.none { it.signature() == entry.signature } ?: true
        }
        val skipMap = skipEntries.groupBy { it.date }
            .mapValues { (_, entries) -> entries.map { it.signature }.toSet() }

        val recurringTasks = days.values.flatMap { day ->
            day.tasks.mapNotNull { task ->
                task.recurrenceType()?.let { RecurringTask(day.date, task, it) }
            }
        }
        if (recurringTasks.isEmpty()) return@withContext

        recurringTasks.forEach { recurring ->
            var targetDate = recurring.nextDate(recurring.date)
            while (!targetDate.isAfter(horizonEnd)) {
                if (!targetDate.isBefore(fromDate)) {
                    val existingDay = days[targetDate] ?: CalendarDay(targetDate, emptyList())
                    val signature = recurring.task.signature()
                    val skipForDate = skipMap[targetDate]
                    if (skipForDate?.contains(signature) == true) {
                        targetDate = recurring.nextDate(targetDate)
                        continue
                    }
                    if (existingDay.tasks.none { it.signature() == signature }) {
                        val clone = recurring.task.copy(id = UUID.randomUUID().toString())
                        days[targetDate] = existingDay.copy(tasks = existingDay.tasks + clone)
                    }
                }
                targetDate = recurring.nextDate(targetDate)
            }
        }

        repository.writeFile(LifeFile.CALENDAR, CalendarParser.format(days.values.sortedBy { it.date }))
        val prunedSkips = skipEntries.filter { !it.date.isBefore(fromDate) }
        repository.writeFile(LifeFile.CALENDAR_SKIP, formatSkipEntries(prunedSkips))
    }

    private data class RecurringTask(
        val date: LocalDate,
        val task: CalendarTask,
        val type: RecurrenceType
    ) {
        fun nextDate(current: LocalDate): LocalDate = when (type) {
            RecurrenceType.DAILY -> current.plusDays(1)
            RecurrenceType.WEEKLY -> current.plusWeeks(1)
            RecurrenceType.ANNUAL -> current.plusYears(1)
        }
    }

    private enum class RecurrenceType { DAILY, WEEKLY, ANNUAL }

    private fun CalendarTask.recurrenceType(): RecurrenceType? {
        val trimmed = description.trim().lowercase()
        return when {
            trimmed.startsWith("@diario") -> RecurrenceType.DAILY
            trimmed.startsWith("@semanal") -> RecurrenceType.WEEKLY
            trimmed.startsWith("@anual") -> RecurrenceType.ANNUAL
            else -> null
        }
    }
}

data class SkipEntry(val date: LocalDate, val signature: String)

fun parseSkipEntries(raw: String): List<SkipEntry> =
    raw.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .mapNotNull { line ->
            val parts = line.split("|", limit = 2)
            if (parts.size != 2) return@mapNotNull null
            runCatching { LocalDate.parse(parts[0]) }.getOrNull()?.let { date ->
                SkipEntry(date, parts[1])
            }
        }
        .toList()

fun formatSkipEntries(entries: Collection<SkipEntry>): String =
    entries.sortedWith(compareBy<SkipEntry> { it.date }.thenBy { it.signature })
        .joinToString("\n") { "${it.date}|${it.signature}" }
