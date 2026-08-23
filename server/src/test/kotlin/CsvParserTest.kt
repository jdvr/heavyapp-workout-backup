package dev.juanvega

import dev.juanvega.source.csv.CsvParser
import kotlin.test.*

class CsvParserTest {

    @Test
    fun `parses plain rows`() {
        val records = CsvParser.parse("a,b,c\n1,2,3")
        assertEquals(listOf(listOf("a", "b", "c"), listOf("1", "2", "3")), records)
    }

    @Test
    fun `parses quoted fields containing commas`() {
        val records = CsvParser.parse("\"title\",\"notes\"\n\"Leg day\",\"note, with comma\"")
        assertEquals(listOf("Leg day", "note, with comma"), records[1])
    }

    @Test
    fun `parses escaped quotes`() {
        val records = CsvParser.parse("\"say \"\"hi\"\"\"")
        assertEquals("say \"hi\"", records.single().single())
    }

    @Test
    fun `empty fields become empty strings`() {
        val records = CsvParser.parse("a,,c")
        assertEquals(listOf("a", "", "c"), records.single())
    }

    @Test
    fun `handles crlf line endings`() {
        val records = CsvParser.parse("a,b\r\n1,2\r\n")
        assertEquals(2, records.size)
        assertEquals(listOf("1", "2"), records[1])
    }

    @Test
    fun `skips blank lines`() {
        val records = CsvParser.parse("\na,b\n\n1,2\n")
        assertEquals(2, records.size)
    }
}
