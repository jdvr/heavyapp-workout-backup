package dev.juanvega.source.csv

/**
 * Merges an uploaded Hevy export into the CSV already stored.
 *
 * Hevy's export only covers a rolling window (roughly the last three months), so
 * [CsvWorkoutSource] replacing the stored file outright would drop every older
 * workout. This merge keeps the stored workouts the upload does not mention, and lets
 * the uploaded rows win for the workouts it does mention (they are the fresher copy),
 * so re-importing a fresh export never loses history.
 *
 * Workouts are matched by the stable ID derived from `(title, start_time)` — the same
 * identity [WorkoutCsvReader] gives the API. All rows of a workout are replaced
 * together, so an updated workout can never leave orphan sets behind.
 */
internal object CsvImportMerger {

    /** What a merge did, for logging and the import response. */
    internal data class Outcome(
        /** Records of the merged file: the upload's header, then every workout. */
        val records: List<List<String>>,
        /** Workouts of the upload that were not stored yet. */
        val added: Int,
        /** Stored workouts whose uploaded rows differ — the upload won. */
        val updated: Int,
        /** Stored workouts the upload repeats unchanged. */
        val unchanged: Int,
        /** Stored workouts the upload does not mention, kept as they were. */
        val preserved: Int,
        /** Distinct workouts in [records]. */
        val workoutCount: Int,
    )

    /**
     * @param storedRecords records of the stored CSV, or `null` when there is nothing to
     *   merge into (no file yet). `[records].first()` is a header.
     * @param uploadedRecords records of the uploaded CSV; `first()` is the header.
     */
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
                // Rows without a usable identity belong to no workout; they are kept
                // verbatim rather than silently dropped.
                stored?.let { addAll(it.unkeyable) }
            },
            added = added,
            updated = updated,
            unchanged = unchanged,
            preserved = preserved,
            workoutCount = merged.size,
        )
    }

    /** Data rows grouped by the workout they belong to, plus the rows that map to none. */
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
