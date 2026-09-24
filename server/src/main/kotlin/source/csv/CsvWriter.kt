package dev.juanvega.source.csv

/**
 * Serializes parsed CSV records back to text in Hevy's export style: every field is
 * quoted and embedded quotes are doubled, one record per line, LF line endings, no
 * trailing newline.
 *
 * Round-trips through [CsvParser] — `CsvParser.parse(CsvWriter.write(records)) == records`
 * — so a merged file is always re-readable by [WorkoutCsvReader].
 */
internal object CsvWriter {

    private const val FIELD_SEPARATOR = ','
    private const val RECORD_SEPARATOR = '\n'
    private const val QUOTE = '"'

    /** Writes [records] as CSV text; an empty list yields an empty string. */
    fun write(records: List<List<String>>): String =
        records.joinToString(RECORD_SEPARATOR.toString()) { record ->
            record.joinToString(FIELD_SEPARATOR.toString()) { field -> field.quoted() }
        }

    private fun String.quoted(): String = QUOTE + replace(QUOTE.toString(), "$QUOTE$QUOTE") + QUOTE
}
