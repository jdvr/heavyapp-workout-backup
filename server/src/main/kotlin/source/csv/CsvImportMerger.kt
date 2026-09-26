package dev.juanvega.source.csv

/**
 * Merges an uploaded Hevy export into the CSV already stored. Workouts are matched by
 * the stable ID [WorkoutCsvReader] gives the API, and all rows of a workout are
 * replaced together, so an updated workout can never leave orphan sets behind.
 */
internal object CsvImportMerger {

    internal data class Outcome(
        val records: List<List<String>>,
        val added: Int,
        val updated: Int,
        val unchanged: Int,
        val preserved: Int,
        val workoutCount: Int,
    )

    fun merge(storedRecords: List<List<String>>?, uploadedRecords: List<List<String>>): Outcome {
        require(uploadedRecords.isNotEmpty()) { "uploaded CSV must contain a header" }
        val header = uploadedRecords.first()
        val uploaded = group(uploadedRecords)
        val stored = storedRecords
            ?.takeIf { it.isNotEmpty() }
            ?.let { group(it).realignedTo(header) }

        var added = 0
        var updated = 0
        var unchanged = 0
        val merged = LinkedHashMap<String, List<List<String>>>()
        uploaded.byWorkout.forEach { (id, rows) ->
            when (val previous = stored?.byWorkout?.get(id)) {
                null -> added++
                rows -> unchanged++
                else -> updated++
            }
            // Either way the upload's rows (and their order) win for this workout.
            merged[id] = rows
        }
        var preserved = 0
        stored?.byWorkout?.forEach { (id, rows) ->
            if (id !in merged) {
                merged[id] = rows
                preserved++
            }
        }

        return Outcome(
            records = buildList {
                add(header)
                merged.values.forEach(::addAll)
                // Rows without a usable identity belong to no workout; kept verbatim
                // rather than silently dropped.
                stored?.let { addAll(it.unkeyable) }
            },
            added = added,
            updated = updated,
            unchanged = unchanged,
            preserved = preserved,
            workoutCount = merged.size,
        )
    }

    private class Groups(
        val header: List<String>,
        val byWorkout: Map<String, List<List<String>>>,
        val unkeyable: List<List<String>>,
    )

    private fun group(records: List<List<String>>): Groups {
        val indexOf = WorkoutCsvReader.headerIndexes(records.first())
        val byWorkout = LinkedHashMap<String, MutableList<List<String>>>()
        val unkeyable = mutableListOf<List<String>>()
        records.drop(1).forEach { row ->
            when (val id = WorkoutCsvReader.workoutIdOf(row, indexOf)) {
                null -> unkeyable += row
                else -> byWorkout.getOrPut(id) { mutableListOf() } += row
            }
        }
        return Groups(records.first(), byWorkout, unkeyable)
    }

    /**
     * Rewrites the row cells to [header]'s column order when the stored file was written
     * with a different one — Hevy could add or reorder export columns between imports.
     * Columns the new header lacks are dropped and missing ones are left empty: the
     * upload defines the shape, which is what the reader will parse next anyway.
     */
    private fun Groups.realignedTo(header: List<String>): Groups {
        val storedColumns = this.header.map { it.trim() }
        val targetColumns = header.map { it.trim() }
        if (storedColumns == targetColumns) return this

        fun realign(row: List<String>): List<String> = targetColumns.map { column ->
            storedColumns.indexOf(column).takeIf { it >= 0 }?.let(row::getOrNull).orEmpty()
        }
        return Groups(
            header = header,
            byWorkout = byWorkout.mapValues { (_, rows) -> rows.map(::realign) },
            unkeyable = unkeyable.map(::realign),
        )
    }
}
