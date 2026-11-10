package com.school.servicemonitor.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.school.servicemonitor.data.entity.Student
import com.school.servicemonitor.data.entity.StudentStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface StudentDao {

    @Insert
    suspend fun insert(student: Student): Long

    @Update
    suspend fun update(student: Student)

    @Query("DELETE FROM students WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM students WHERE id = :id")
    fun observeById(id: Long): Flow<Student?>

    @Query("SELECT * FROM students WHERE id = :id")
    suspend fun getById(id: Long): Student?

    // excludeId lets an existing student keep their own number when editing.
    @Query("SELECT COUNT(*) FROM students WHERE student_number = :number AND id != :excludeId")
    suspend fun countByNumber(number: String, excludeId: Long): Int

    @Query("UPDATE students SET status = :status, updated_at = :now WHERE id = :id")
    suspend fun setStatus(id: Long, status: StudentStatus, now: Long)

    // Search by number, first name, last name, or full name in either order.
    @Query(
        """
        SELECT * FROM students
        WHERE (status = 'ACTIVE' OR :includeArchived = 1)
          AND (
                :query = ''
             OR student_number LIKE '%' || :query || '%'
             OR first_name LIKE '%' || :query || '%'
             OR last_name LIKE '%' || :query || '%'
             OR (first_name || ' ' || last_name) LIKE '%' || :query || '%'
             OR (last_name || ' ' || first_name) LIKE '%' || :query || '%'
             OR (last_name || ', ' || first_name) LIKE '%' || :query || '%'
          )
        ORDER BY last_name COLLATE NOCASE ASC, first_name COLLATE NOCASE ASC
        """
    )
    fun search(query: String, includeArchived: Boolean): Flow<List<Student>>

    @Query(
        """
        SELECT * FROM students
        WHERE status = 'ACTIVE'
        ORDER BY last_name COLLATE NOCASE ASC, first_name COLLATE NOCASE ASC
        """
    )
    fun observeActive(): Flow<List<Student>>

}