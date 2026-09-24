package dev.juanvega

import dev.juanvega.source.csv.CsvParser
import dev.juanvega.source.csv.CsvWriter
import kotlin.test.*

/** Serialization round-trip of [CsvWriter] through [CsvParser]. */
class CsvWriterTest {

    @Test
    fun `round-trips quoted fields, embedded commas, quotes and newlines`() {
        val records = listOf(
            listOf("title", "start_time", "description"),
            listOf("Leg Day, \"heavy\"", "17 Aug 2026,\n15:17", ""),
            listOf("", "Emoji 💪", "multi\nline"),
        )

        assertEquals(records, CsvParser.parse(CsvWriter.write(records)))
    }

    @Test
    fun `quotes every field, Hevy style`() {
        val records = listOf(listOf("a", "b"), listOf("1", ""))

        assertEquals("\"a\",\"b\"\n\"1\",\"\"", CsvWriter.write(records))
    }

    @Test
    fun `no records yields an empty string`() {
        assertEquals("", CsvWriter.write(emptyList()))
    }
}
