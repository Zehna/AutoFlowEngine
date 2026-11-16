package com.taras.autoflow.data

import androidx.room.*
import com.taras.autoflow.engine.WorkflowDefinition
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json

@Entity(tableName = "workflows")
data class WorkflowEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String = "",
    val definitionJson: String,
    val isScheduled: Boolean = false,
    val scheduleIntervalMinutes: Long = 15L,
    val lastRunAt: Long? = null,
    val lastRunStatus: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toDefinition(json: Json = defaultWorkflowJson): WorkflowDefinition =
        json.decodeFromString(WorkflowDefinition.serializer(), definitionJson)

    companion object {
        val defaultWorkflowJson = Json {
            ignoreUnknownKeys = true
            prettyPrint = true
            encodeDefaults = true
        }

        fun fromDefinition(
            definition: WorkflowDefinition,
            isScheduled: Boolean = false,
            scheduleIntervalMinutes: Long = 15L,
            lastRunAt: Long? = null,
            lastRunStatus: String? = null
        ): WorkflowEntity = WorkflowEntity(
            id = definition.id,
            name = definition.name,
            description = definition.description,
            definitionJson = defaultWorkflowJson.encodeToString(WorkflowDefinition.serializer(), definition),
            isScheduled = isScheduled,
            scheduleIntervalMinutes = scheduleIntervalMinutes,
            lastRunAt = lastRunAt,
            lastRunStatus = lastRunStatus,
            updatedAt = System.currentTimeMillis()
        )
    }
}

@Dao
interface WorkflowEntityDao {
    @Query("SELECT * FROM workflows ORDER BY updatedAt DESC")
    fun observeAllWorkflows(): Flow<List<WorkflowEntity>>

    @Query("SELECT * FROM workflows WHERE id = :id LIMIT 1")
    suspend fun getWorkflowById(id: String): WorkflowEntity?

    @Query("SELECT COUNT(*) FROM workflows")
    suspend fun getWorkflowCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWorkflow(entity: WorkflowEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(entities: List<WorkflowEntity>)

    @Query("DELETE FROM workflows WHERE id = :id")
    suspend fun deleteWorkflowById(id: String)

    @Query("UPDATE workflows SET isScheduled = :scheduled, scheduleIntervalMinutes = :intervalMinutes, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateSchedule(id: String, scheduled: Boolean, intervalMinutes: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE workflows SET lastRunAt = :runAt, lastRunStatus = :status WHERE id = :id")
    suspend fun updateRunStatus(id: String, runAt: Long, status: String)
}
