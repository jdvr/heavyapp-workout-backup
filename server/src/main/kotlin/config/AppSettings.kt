package dev.juanvega.config

import io.ktor.server.config.ApplicationConfig
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class AppSettings(
    /** Path to the workout CSV file. `null` when not configured; the CSV source stays empty. */
    val dataFile: String?,
    val refreshSeconds: Duration = DEFAULT_REFRESH,
) {
    companion object {
        val DEFAULT_REFRESH: Duration = 60.seconds

        fun from(config: ApplicationConfig): AppSettings {
            val dataFile = config
                .propertyOrNull("heavyapp.dataFile")
                ?.getString()
                ?.takeIf { it.isNotBlank() }
            val refresh = config
                .propertyOrNull("heavyapp.refreshSeconds")
                ?.getString()?.toLongOrNull()
                ?.seconds ?: DEFAULT_REFRESH
            return AppSettings(dataFile, refresh)
        }
    }
}
