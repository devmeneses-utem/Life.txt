package com.lifetxt.parser

import com.lifetxt.domain.parser.CalendarParser
import com.lifetxt.model.TaskLabel
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class CalendarParserTest {

    @Test
    fun `parses day headers and tasks`() {
        val input = """
            2024-06-11
            + [08:00] Reunion #w
            + Gimnasio #p
            
            2024-06-12
            + Limpiar #h
        """.trimIndent()

        val days = CalendarParser.parse(input)
        assertEquals(2, days.size)
        val firstDay = days.first()
        assertEquals(LocalDate.of(2024, 6, 11), firstDay.date)
        assertEquals(2, firstDay.tasks.size)
        assertEquals(TaskLabel.WORK, firstDay.tasks.first().labels.first())
    }
}
