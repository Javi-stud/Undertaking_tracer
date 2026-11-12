package com.school.servicemonitor.data.repository

import android.util.Log
import androidx.room.withTransaction
import com.school.servicemonitor.data.AppDatabase
import com.school.servicemonitor.data.dao.DeadlineEventDao
import com.school.servicemonitor.data.dao.SanctionDao
import com.school.servicemonitor.data.dao.SanctionHistoryDao
import com.school.servicemonitor.data.dao.StudentDao
import com.school.servicemonitor.data.entity.DeadlineEventRow
import com.school.servicemonitor.data.entity.DeadlineRepeatEvent
import com.school.servicemonitor.data.entity.RepeatEventStatus
import com.school.servicemonitor.data.entity.Sanction
import com.school.servicemonitor.data.entity.SanctionHistory
import com.school.servicemonitor.data.entity.SanctionHistoryType
import com.school.servicemonitor.data.entity.SanctionStatus
import com.school.servicemonitor.data.entity.StudentStatus
import com.school.servicemonitor.domain.DeadlineMonitor
import com.school.servicemonitor.ui.components.display
import com.school.servicemonitor.ui.components.formatMinutes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.LocalDate

sealed interface RepeatOutcome {
    data class Saved(val newSanctionId: Long) : RepeatOutcome
    data class Rejected(val message: String) : RepeatOutcome
}

class DeadlineRepository(
    private val db: AppDatabase,
    private val eventDao: DeadlineEventDao,
    private val historyDao: SanctionHistoryDao,
    private val sanctionDao: SanctionDao,
    private val studentDao: StudentDao
) {

    fun observePending(): Flow<List<DeadlineEventRow>> = eventDao.observePending()

    fun observeForStudent(studentId: Long): Flow<List<DeadlineEventRow>> =
        eventDao.observeForStudent(studentId)

    /**
     * Runs the deadline check now, and again every time a sanction, credit or exemption changes.
     * The first run happens as soon as the app starts. Safe to run repeatedly.
     */
    fun startMonitoring(scope: CoroutineScope) {
        scope.launch {
            sanctionDao.observeAllForActiveStudents().collectLatest {
                try {
                    scan()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("DeadlineRepository", "Deadline scan failed; it will retry on the next change", e)
                }
            }
        }
    }

    /**
     * Finds sanctions that passed their deadline with hours still owed.
     * Marks them OVERDUE and proposes ONE repeat per deadline. A proposal adds no sanction
     * and no hours. Returns how many new proposals were created.
     */
    suspend fun scan(today: LocalDate = LocalDate.now()): Int = db.withTransaction {
        val now = System.currentTimeMillis()
        var created = 0

        for (row in DeadlineMonitor.overdue(sanctionDao.getAllRowsForActiveStudents(), today)) {
            val s = row.sanction
            if (s.status == SanctionStatus.ACTIVE) {
                sanctionDao.updateStatus(s.id, SanctionStatus.OVERDUE, null)
            }

            // The unique (sanction, deadline) index refuses a duplicate: the result is -1.
            val eventId = eventDao.insertIgnore(
                DeadlineRepeatEvent(
                    originalSanctionId = s.id,
                    triggeringDeadline = s.endDate,
                    detectedAt = now
                )
            )
            if (eventId != -1L) {
                created++
                historyDao.insert(
                    SanctionHistory(
                        sanctionId = s.id,
                        eventType = SanctionHistoryType.EXPIRED,
                        details = "Deadline ${s.endDate.display()} passed with " +
                                "${formatMinutes(DeadlineMonitor.proposedMinutes(s).coerceAtMost(Int.MAX_VALUE))} assigned.",
                        createdAt = now
                    )
                )
                historyDao.insert(
                    SanctionHistory(
                        sanctionId = s.id,
                        eventType = SanctionHistoryType.REPEAT_PROPOSED,
                        details = "A repeat of the full ${formatMinutes(DeadlineMonitor.proposedMinutes(s))} " +
                                "was proposed. Nothing was added yet.",
                        createdAt = now
                    )
                )
            }
        }
        created
    }

    /**
     * The operator confirmed: create the repeated sanction with the FULL original hours and the
     * dates they picked, link it to the original, and close the event. One transaction.
     */
    suspend fun confirm(eventId: Long, newStart: LocalDate, newEnd: LocalDate): RepeatOutcome =
        db.withTransaction {
            val today = LocalDate.now()
            DeadlineMonitor.validateRepeatDates(newStart, newEnd, today)?.let {
                return@withTransaction RepeatOutcome.Rejected(it)
            }

            val event = eventDao.getById(eventId)
                ?: return@withTransaction RepeatOutcome.Rejected("This repeat could not be found.")
            if (event.status != RepeatEventStatus.DETECTED) {
                return@withTransaction RepeatOutcome.Rejected("This repeat was already handled.")
            }
            val original = sanctionDao.getById(event.originalSanctionId)
                ?: return@withTransaction RepeatOutcome.Rejected("The original sanction was not found.")
            val student = studentDao.getById(original.studentId)
                ?: return@withTransaction RepeatOutcome.Rejected("Student not found.")
            if (student.status != StudentStatus.ACTIVE) {
                return@withTransaction RepeatOutcome.Rejected("Restore the student before confirming a repeat.")
            }

            val now = System.currentTimeMillis()
            val minutes = DeadlineMonitor.proposedMinutes(original)
            val newId = sanctionDao.insert(
                Sanction(
                    studentId = original.studentId,
                    violationId = original.violationId,
                    baseHours = original.baseHours,
                    multiplierApplied = original.multiplierApplied,
                    assignedMinutes = minutes,
                    assignedDate = today,
                    startDate = newStart,
                    endDate = newEnd,
                    status = SanctionStatus.ACTIVE,
                    parentSanctionId = original.id,
                    createdAt = now
                )
            )

            // If the event was closed by something else in the meantime, roll everything back.
            check(eventDao.markConfirmed(eventId, newId, now) == 1) { "The repeat was already handled." }

            historyDao.insert(
                SanctionHistory(
                    sanctionId = original.id,
                    relatedSanctionId = newId,
                    eventType = SanctionHistoryType.REPEAT_CONFIRMED,
                    details = "Repeat confirmed: ${formatMinutes(minutes)} from " +
                            "${newStart.display()} to ${newEnd.display()}.",
                    createdAt = now
                )
            )
            historyDao.insert(
                SanctionHistory(
                    sanctionId = newId,
                    relatedSanctionId = original.id,
                    eventType = SanctionHistoryType.REPEAT_CREATED,
                    details = "Created as a repeat of an earlier sanction whose deadline " +
                            "${event.triggeringDeadline.display()} passed.",
                    createdAt = now
                )
            )
            RepeatOutcome.Saved(newId)
        }

    /** The operator decided not to repeat. A reason is required. The original keeps its hours. */
    suspend fun dismiss(eventId: Long, reason: String): RepeatOutcome = db.withTransaction {
        val clean = reason.trim()
        if (clean.isEmpty()) {
            return@withTransaction RepeatOutcome.Rejected("A reason is required to dismiss a repeat.")
        }
        val event = eventDao.getById(eventId)
            ?: return@withTransaction RepeatOutcome.Rejected("This repeat could not be found.")
        if (event.status != RepeatEventStatus.DETECTED) {
            return@withTransaction RepeatOutcome.Rejected("This repeat was already handled.")
        }

        val now = System.currentTimeMillis()
        check(eventDao.markDismissed(eventId, clean, now) == 1) { "The repeat was already handled." }
        historyDao.insert(
            SanctionHistory(
                sanctionId = event.originalSanctionId,
                eventType = SanctionHistoryType.REPEAT_DISMISSED,
                details = "Repeat dismissed: $clean",
                createdAt = now
            )
        )
        RepeatOutcome.Saved(0L)
    }
}