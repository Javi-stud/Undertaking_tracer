package com.school.servicemonitor.data.repository

import com.school.servicemonitor.data.dao.ViolationDao
import com.school.servicemonitor.data.entity.Violation
import kotlinx.coroutines.flow.Flow

class ViolationRepository(private val dao: ViolationDao) {

    fun observeAll(): Flow<List<Violation>> = dao.observeAll()

    suspend fun getActive(): List<Violation> = dao.getActive()

    suspend fun get(id: Long): Violation? = dao.getById(id)

    suspend fun isNameTaken(name: String, excludeId: Long): Boolean =
        dao.countByName(name, excludeId) > 0

    suspend fun add(violation: Violation): Long = dao.insert(violation)

    suspend fun update(violation: Violation) = dao.update(violation)
}