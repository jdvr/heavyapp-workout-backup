package dev.juanvega.source.csv

import dev.juanvega.config.AppSettings
import io.opentelemetry.api.trace.Tracer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Loads the CSV source synchronously (so the server never serves an empty state on
 * a healthy file) and then reloads every [AppSettings.refreshSeconds] in the
 * background, each pass wrapped in an OTel span per the repo's observability rules.
 */
fun CoroutineScope.launchCsvRefresh(source: CsvWorkoutSource, settings: AppSettings, tracer: Tracer) {
    source.loadNow()

    launch(Dispatchers.IO) {
        while (true) {
            delay(settings.refreshSeconds)
            reloadWithSpan(source, tracer)
        }
    }
}

private suspend fun reloadWithSpan(source: CsvWorkoutSource, tracer: Tracer) {
    val span = tracer.spanBuilder("csv.source.reload").startSpan()
    val scope = span.makeCurrent()
    try {
        source.loadNow()
    } finally {
        scope.close()
        span.end()
    }
}
