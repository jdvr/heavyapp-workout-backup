package dev.juanvega.source.csv

import java.io.Reader

/**
 * Minimal RFC 4180-style CSV reader: quoted fields, escaped quotes (""), commas
 * inside quotes, CRLF/LF line endings. Good enough for Hevy's export format and
 * fully unit-testable without an extra dependency.
 */
internal object CsvParser {

    fun parse(reader: Reader): List<List<String>> {
        val input = SingleCharPushbackReader(reader)
        val records = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val field = StringBuilder()

        var inQuotes = false
        var fieldStarted = false

        fun endField() {
            row.add(field.toString())
            field.setLength(0)
            fieldStarted = false
        }

        fun endRecord() {
            // Skip empty lines entirely (a record of one unquoted empty cell).
            if (!fieldStarted && row.isEmpty()) return
            endField()
            records.add(row.toList())
            row.clear()
        }

        while (true) {
            val c = input.read()
            if (c == -1) break
            when (val ch = c.toChar()) {
                '"' -> if (inQuotes) {
                    val next = input.read()
                    if (next == '"'.code) { // Escaped quote.
                        field.append('"')
                        fieldStarted = true
                    } else {
                        inQuotes = false
                        if (next != -1) input.pushback(next)
                    }
                } else {
                    inQuotes = true
                    fieldStarted = true
                }
                ',' -> if (inQuotes) {
                    field.append(ch)
                    fieldStarted = true
                } else endField()
                '\r', '\n' -> if (inQuotes) {
                    field.append(ch)
                    fieldStarted = true
                } else {
                    // Consume the LF of a CRLF pair; leave anything else for the loop.
                    if (ch == '\r') {
                        val next = input.read()
                        if (next != '\n'.code && next != -1) input.pushback(next)
                    }
                    endRecord()
                }
                else -> {
                    field.append(ch)
                    fieldStarted = true
                }
            }
        }
        if (fieldStarted || row.isNotEmpty()) endRecord()

        return records
    }

    fun parse(text: String): List<List<String>> =
        text.reader().let { SingleCharPushbackReader(it).use(::parse) }
}

/** [Reader] wrapper allowing a single character to be pushed back. */
private class SingleCharPushbackReader(private val delegate: Reader) : Reader() {

    private var pushedBack: Int? = null

    override fun read(): Int = pushedBack?.also { pushedBack = null } ?: delegate.read()

    fun pushback(value: Int) {
        check(pushedBack == null) { "Only one character can be pushed back" }
        pushedBack = value
    }

    override fun read(cbuf: CharArray, off: Int, len: Int): Int = delegate.read(cbuf, off, len)

    override fun close() = delegate.close()
}
