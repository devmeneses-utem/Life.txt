package com.lifetxt.parser

import com.lifetxt.domain.parser.RecurringEntry
import com.lifetxt.domain.parser.RecurringParser
import org.junit.Assert.assertEquals
import org.junit.Test

class RecurringParserTest {
    @Test
    fun `parses annual monthly and weekly sections`() {
        val input = """
            @anual
            12-24 + Navidad #p
            @mensual
            10 + Pagar tarjeta #w
            @semanal
            lun + Basura #h
        """.trimIndent()

        val entries = RecurringParser.parse(input)
        assertEquals(3, entries.size)
        assert(entries.first() is RecurringEntry.Annual)
    }
}
