package com.school.servicemonitor.domain

import com.school.servicemonitor.data.entity.SanctionRow
import com.school.servicemonitor.data.entity.SanctionStatus
import java.time.LocalDate

/** What the engine needs to know about one sanction. */
data class SanctionBalance(
    val sanctionId: Long,
    val status: SanctionStatus,
    val startDate: LocalDate,
    val endDate: LocalDate,        // the deadline
    val assignedDate: LocalDate,
    val remainingMinutes: Int
)

/** "Give this many minutes to this sanction." */
data class Allocation(val sanctionId: Long, val minutes: Int)

fun SanctionRow.toBalance() = SanctionBalance(
    sanctionId = sanction.id,
    status = sanction.status,
    startDate = sanction.startDate,
    endDate = sanction.endDate,
    assignedDate = sanction.assignedDate,
    remainingMinutes = (sanction.assignedMinutes - allocatedMinutes - exemptedMinutes).coerceAtLeast(0)
)

object AllocationEngine {

    /**
     * Splits [minutesToCredit] across sanctions, earliest deadline first.
     * Tie-break: oldest assigned date, then lowest id.
     */
    fun allocate(
        balances: List<SanctionBalance>,
        serviceDate: LocalDate,
        minutesToCredit: Int
    ): List<Allocation> {
        if (minutesToCredit <= 0) return emptyList()

        val eligible = balances
            .filter {
                (it.status == SanctionStatus.ACTIVE || it.status == SanctionStatus.OVERDUE) &&
                        it.remainingMinutes > 0 &&
                        !it.startDate.isAfter(serviceDate)      // not started yet = not required
            }
            .sortedWith(
                compareBy<SanctionBalance> { it.endDate }
                    .thenBy { it.assignedDate }
                    .thenBy { it.sanctionId }
            )

        var left = minutesToCredit
        val result = mutableListOf<Allocation>()
        for (balance in eligible) {
            if (left <= 0) break
            val take = minOf(balance.remainingMinutes, left)
            result += Allocation(balance.sanctionId, take)
            left -= take
        }
        return result
    }

    /** Daily cap: a student can never earn more than [dailyLimit] minutes on one date. */
    fun allowedToday(requested: Int, dailyLimit: Int, alreadyCredited: Int): Int =
        minOf(requested, (dailyLimit - alreadyCredited).coerceAtLeast(0)).coerceAtLeast(0)

    /**
     * The status a sanction should have after credits or exemptions change.
     * Nothing left: EXEMPTED if an exemption cancelled part of it, otherwise COMPLETED.
     * Something left on a closed sanction (a credit was reversed): it opens again.
     */
    fun resolveStatus(
        current: SanctionStatus,
        assignedMinutes: Int,
        allocatedMinutes: Int,
        endDate: LocalDate,
        today: LocalDate,
        exemptedMinutes: Int = 0
    ): SanctionStatus {
        val remaining = assignedMinutes - allocatedMinutes - exemptedMinutes
        if (remaining <= 0) {
            return if (exemptedMinutes > 0) SanctionStatus.EXEMPTED else SanctionStatus.COMPLETED
        }
        if (current == SanctionStatus.COMPLETED || current == SanctionStatus.EXEMPTED) {
            return if (endDate.isBefore(today)) SanctionStatus.OVERDUE else SanctionStatus.ACTIVE
        }
        return current
    }
}