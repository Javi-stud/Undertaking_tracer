package com.school.servicemonitor.domain

import java.time.DayOfWeek

data class SettingsValues(
    val dailyCreditMinutes: Int,
    val ssgMultiplier: Int,
    val serviceDays: Set<DayOfWeek>
)

data class SettingsInput(
    val creditText: String,
    val multiplierText: String,
    val days: Set<DayOfWeek>
)

data class SettingsErrors(
    val credit: String? = null,
    val multiplier: String? = null,
    val days: String? = null
) {
    fun hasErrors(): Boolean = credit != null || multiplier != null || days != null
}

sealed interface SettingsCheck {
    data class Valid(val values: SettingsValues) : SettingsCheck
    data class Invalid(val errors: SettingsErrors) : SettingsCheck
}

object SettingsValidator {

    const val MIN_CREDIT_MINUTES = 10
    const val MAX_CREDIT_MINUTES = 480
    const val MIN_MULTIPLIER = 1
    const val MAX_MULTIPLIER = 5

    val SELECTABLE_DAYS: List<DayOfWeek> = listOf(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY,
        DayOfWeek.SATURDAY,
        DayOfWeek.SUNDAY
    )

    val DEFAULT_DAYS: Set<DayOfWeek> = setOf(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY
    )

    fun defaults(): SettingsValues = SettingsValues(
        dailyCreditMinutes = 60,
        ssgMultiplier = 2,
        serviceDays = DEFAULT_DAYS
    )

    fun encodeDays(days: Set<DayOfWeek>): String {
        return days.sorted().joinToString(",") { it.name.take(3) }
    }

    fun parseDays(stored: String?): Set<DayOfWeek> {
        if (stored.isNullOrBlank()) return DEFAULT_DAYS
        val result = mutableSetOf<DayOfWeek>()
        for (part in stored.split(",")) {
            val token = part.trim().uppercase()
            val day = when (token) {
                "MON", "MONDAY" -> DayOfWeek.MONDAY
                "TUE", "TUESDAY" -> DayOfWeek.TUESDAY
                "WED", "WEDNESDAY" -> DayOfWeek.WEDNESDAY
                "THU", "THURSDAY" -> DayOfWeek.THURSDAY
                "FRI", "FRIDAY" -> DayOfWeek.FRIDAY
                "SAT", "SATURDAY" -> DayOfWeek.SATURDAY
                "SUN", "SUNDAY" -> DayOfWeek.SUNDAY
                else -> null
            }
            if (day != null) result.add(day)
        }
        return if (result.isEmpty()) DEFAULT_DAYS else result
    }

    fun fromStored(creditStr: String?, multiplierStr: String?, daysStr: String?): SettingsValues {
        val credit = creditStr?.toIntOrNull() ?: 60
        val multiplier = multiplierStr?.toIntOrNull() ?: 2
        val days = parseDays(daysStr)
        return SettingsValues(credit, multiplier, days)
    }

    fun validate(input: SettingsInput): SettingsCheck {
        val credit = input.creditText.toIntOrNull()
        val multiplier = input.multiplierText.toIntOrNull()

        val creditError = when {
            credit == null -> "Please enter daily credit minutes."
            credit < MIN_CREDIT_MINUTES -> "Credit minutes must be at least $MIN_CREDIT_MINUTES."
            credit > MAX_CREDIT_MINUTES -> "Credit minutes cannot exceed $MAX_CREDIT_MINUTES."
            else -> null
        }

        val multiplierError = when {
            multiplier == null -> "Please enter the SSG multiplier."
            multiplier < MIN_MULTIPLIER -> "Multiplier must be at least $MIN_MULTIPLIER."
            multiplier > MAX_MULTIPLIER -> "Multiplier cannot exceed $MAX_MULTIPLIER."
            else -> null
        }

        val daysError = if (input.days.isEmpty()) {
            "Please select at least one service day."
        } else null

        val errors = SettingsErrors(creditError, multiplierError, daysError)
        if (errors.hasErrors()) {
            return SettingsCheck.Invalid(errors)
        }

        return SettingsCheck.Valid(
            SettingsValues(
                dailyCreditMinutes = credit!!,
                ssgMultiplier = multiplier!!,
                serviceDays = input.days
            )
        )
    }
}
