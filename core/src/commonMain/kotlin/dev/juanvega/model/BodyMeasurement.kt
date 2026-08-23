package dev.juanvega.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
data class BodyMeasurement(
    val date: LocalDate,
    val weight_kg: Double? = null,
    val lean_mass_kg: Double? = null,
    val fat_percent: Double? = null,
    val neck_cm: Double? = null,
    val shoulder_cm: Double? = null,
    val chest_cm: Double? = null,
    val left_bicep_cm: Double? = null,
    val right_bicep_cm: Double? = null,
    val left_forearm_cm: Double? = null,
    val right_forearm_cm: Double? = null,
    val abdomen_cm: Double? = null,
    val waist_cm: Double? = null,
    val hips_cm: Double? = null,
    val left_thigh_cm: Double? = null,
    val right_thigh_cm: Double? = null,
    val left_calf_cm: Double? = null,
    val right_calf_cm: Double? = null,
)
