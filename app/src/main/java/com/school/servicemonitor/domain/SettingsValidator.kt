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

    const val MIN_CREDIT_MINUTES = 15
    const val MAX_CREDIT_MINUTES = 240
    const val MIN_MULTIPLIER = 1
    const val MAX_MULTIPLIER = 5

    val SELECTABLE_DAYS: List<DayOfWeek> = listOf(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY
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
        return days.filter { it != DayOfWeek.SATURDAY && it != DayOfWeek.SUNDAY }.sorted().joinToString(",") { it.name.take(3) }
    }

    fun parseDays(stored: String?): Set<DayOfWeek> {
        val parsed = ScheduleCalculator.parseServiceDays(stored)
        val filtered = parsed.filter { it != DayOfWeek.SATURDAY && it != DayOfWeek.SUNDAY }.toSet()
        return filtered.ifEmpty { DEFAULT_DAYS }
    }

    fun fromStored(creditStr: String?, multiplierStr: String?, daysStr: String?): SettingsValues {
        val credit = creditStr?.trim()?.toIntOrNull() ?: 60
        val multiplier = multiplierStr?.trim()?.toIntOrNull() ?: 2
        val validCredit = if (credit in MIN_CREDIT_MINUTES..MAX_CREDIT_MINUTES) credit else 60
        val validMultiplier = if (multiplier in MIN_MULTIPLIER..MAX_MULTIPLIER) multiplier else 2
        val days = parseDays(daysStr)
        return SettingsValues(validCredit, validMultiplier, days)
    }

    fun validate(input: SettingsInput): SettingsCheck {
        val credit = input.creditText.trim().toIntOrNull()
        val multiplier = input.multiplierText.trim().toIntOrNull()

        val creditError = when {
            credit == null -> "Please enter daily credit minutes."
            credit < MIN_CREDIT_MINUTES || credit > MAX_CREDIT_MINUTES -> "Credit minutes must be between $MIN_CREDIT_MINUTES and $MAX_CREDIT_MINUTES."
            else -> null
        }

        val multiplierError = when {
            multiplier == null -> "Please enter the SSG multiplier."
            multiplier < MIN_MULTIPLIER || multiplier > MAX_MULTIPLIER -> "Multiplier must be between $MIN_MULTIPLIER and $MAX_MULTIPLIER."
            else -> null
        }

        val validDays = input.days.filter { it != DayOfWeek.SATURDAY && it != DayOfWeek.SUNDAY }.toSet()
        val daysError = if (validDays.isEmpty()) {
            "Please select at least one valid service day."
        } else null

        val errors = SettingsErrors(creditError, multiplierError, daysError)
        if (errors.hasErrors()) {
            return SettingsCheck.Invalid(errors)
        }

        return SettingsCheck.Valid(
            SettingsValues(
                dailyCreditMinutes = credit!!,
                ssgMultiplier = multiplier!!,
                serviceDays = validDays
            )
        )
    }
}
