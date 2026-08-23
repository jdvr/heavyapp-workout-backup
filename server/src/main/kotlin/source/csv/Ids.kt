package dev.juanvega.source.csv

import java.security.MessageDigest

/**
 * Deterministic, stable identifiers for entities the CSV export does not carry.
 *
 * The real Hevy API uses opaque IDs; we derive stable 8-char uppercase hex IDs
 * from the entity's identity so that:
 *  - `/v1/exercise_history/{templateId}` can already resolve today,
 *  - IDs survive reloads and restarts,
 *  - a future exercise-template catalog can replace this mapping without
 *    changing the route layer.
 */
internal fun stableId(vararg identityParts: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    identityParts.forEach { part ->
        digest.update(part.trim().lowercase().toByteArray())
        digest.update(0.toByte())
    }
    return digest.digest().joinToString("") { "%02X".format(it) }.take(8)
}
