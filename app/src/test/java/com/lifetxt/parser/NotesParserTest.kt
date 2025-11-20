package com.lifetxt.parser

import com.lifetxt.domain.parser.NotesParser
import org.junit.Assert.assertEquals
import org.junit.Test

class NotesParserTest {

    @Test
    fun `detects timestamps`() {
        val input = """
            +(2024-06-11) Clase
            Conceptos base
            +[00:01:15] Derivada
        """.trimIndent()

        val notes = NotesParser.parse(input)
        assertEquals(1, notes.size)
        assertEquals(1, notes.first().timestamps.size)
        assertEquals("+[00:01:15]", notes.first().timestamps.first().label)
    }
}
