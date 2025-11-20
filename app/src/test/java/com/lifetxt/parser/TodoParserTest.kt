package com.lifetxt.parser

import com.lifetxt.domain.parser.TodoParser
import com.lifetxt.model.TaskLabel
import com.lifetxt.model.TodoPriority
import org.junit.Assert.assertEquals
import org.junit.Test

class TodoParserTest {

    @Test
    fun `extracts priorities and labels`() {
        val input = """
            (A) Preparar informe #w
            (B) Llamar a mama #p
        """.trimIndent()

        val tasks = TodoParser.parseActive(input)
        assertEquals(2, tasks.size)
        assertEquals(TodoPriority.A, tasks.first().priority)
        assertEquals(TaskLabel.WORK, tasks.first().labels.first())
    }
}
