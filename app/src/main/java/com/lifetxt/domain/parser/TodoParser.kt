package com.lifetxt.domain.parser

import com.lifetxt.model.TaskLabel
import com.lifetxt.model.TodoPriority
import com.lifetxt.model.TodoTask
import java.security.MessageDigest
import java.time.LocalDate

object TodoParser {
    private val labelRegex = Regex("""#([phwt])""", RegexOption.IGNORE_CASE)
    private val priorityRegex = Regex("""^\(([AB])\)\s*""", RegexOption.IGNORE_CASE)
    private val doneRegex = Regex("""^x\s*(\d{4}-\d{2}-\d{2})?\s*(.+)$""", RegexOption.IGNORE_CASE)

    fun parseActive(content: String): List<TodoTask> {
        if (content.isBlank()) return emptyList()
        return content.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .mapNotNull(::parseActiveLine)
            .toList()
    }

    fun parseDone(content: String): List<TodoTask> {
        if (content.isBlank()) return emptyList()
        return content.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("x", ignoreCase = true) }
            .mapNotNull(::parseDoneLine)
            .toList()
    }

    fun parseLine(line: String): TodoTask? = parseActiveLine(line.trim())

    fun format(tasks: List<TodoTask>): String =
        tasks.joinToString(separator = "\n") { task ->
            val builder = StringBuilder()
            task.priority?.let { builder.append("(${it.name}) ") }
            builder.append(task.description)
            if (task.labels.isNotEmpty()) {
                builder.append(" ")
                builder.append(task.labels.joinToString(" ") {
                    when (it) {
                        TaskLabel.PERSONAL -> "#p"
                        TaskLabel.HOME -> "#h"
                        TaskLabel.WORK -> "#w"
                        TaskLabel.URGENT -> "#t"
                    }
                })
            }
            builder.toString()
        }

    fun formatDone(task: TodoTask, doneDate: LocalDate): String {
        val labelText = if (task.labels.isEmpty()) "" else {
            " " + task.labels.joinToString(" ") {
                when (it) {
                    TaskLabel.PERSONAL -> "#p"
                    TaskLabel.HOME -> "#h"
                    TaskLabel.WORK -> "#w"
                    TaskLabel.URGENT -> "#t"
                }
            }
        }
        val priority = task.priority?.let { "(${it.name}) " } ?: ""
        return "x ${doneDate} $priority${task.description}$labelText"
    }

    private fun parseActiveLine(line: String): TodoTask? {
        var working = line
        val priorityMatch = priorityRegex.find(working)
        val priority = priorityMatch?.groupValues?.get(1)?.uppercase()?.let { TodoPriority.valueOf(it) }
        working = priorityRegex.replace(working, "").trim()
        val labels = labelRegex.findAll(working)
            .mapNotNull { tokenToLabel(it.groupValues[1]) }
            .toSet()
        val description = working.replace(labelRegex, "").trim()
        if (description.isBlank()) return null
        return TodoTask(
            id = stableId(description, priority, labels),
            description = description,
            priority = priority,
            labels = labels,
            isDone = false,
            completedAt = null
        )
    }

    private fun parseDoneLine(line: String): TodoTask? {
        val match = doneRegex.find(line) ?: return null
        val dateText = match.groupValues[1].takeIf { it.isNotBlank() }
        val descriptionRaw = match.groupValues[2].trim()
        val labels = labelRegex.findAll(descriptionRaw).mapNotNull { tokenToLabel(it.groupValues[1]) }.toSet()
        val priorityMatch = priorityRegex.find(descriptionRaw)
        val priority = priorityMatch?.groupValues?.get(1)?.uppercase()?.let { TodoPriority.valueOf(it) }
        val description = descriptionRaw
            .replace(priorityRegex, "")
            .replace(labelRegex, "")
            .trim()
        return TodoTask(
            id = stableId(description, priority, labels),
            description = description,
            priority = priority,
            labels = labels,
            isDone = true,
            completedAt = dateText?.let(LocalDate::parse)
        )
    }

    private fun tokenToLabel(token: String): TaskLabel? = when (token.lowercase()) {
        "p" -> TaskLabel.PERSONAL
        "h" -> TaskLabel.HOME
        "w" -> TaskLabel.WORK
        "t" -> TaskLabel.URGENT
        else -> null
    }

    private fun stableId(description: String, priority: TodoPriority?, labels: Set<TaskLabel>): String {
        val digest = MessageDigest.getInstance("MD5")
        val seed = buildString {
            append(description.lowercase())
            append("|")
            append(priority?.name ?: "none")
            append("|")
            append(labels.sortedBy { it.name }.joinToString("-"))
        }
        val hash = digest.digest(seed.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }
}
