package com.taras.autoflow.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "node_execution_vault")
data class NodeExecutionLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val workflowId: String,
    val workflowName: String,
    val nodeId: String,
    val nodeType: String,
    val status: String,
    val inputPayload: String,
    val outputPayload: String,
    val executionDurationMs: Long,
    val errorTrace: String? = null,
    val executedAt: Long = System.currentTimeMillis()
)

@Dao
interface NodeExecutionLogDao {
    @Insert
    suspend fun recordLog(log: NodeExecutionLogEntity): Long

    @Query("SELECT * FROM node_execution_vault ORDER BY id DESC")
    fun observeAllLogs(): Flow<List<NodeExecutionLogEntity>>

    @Query("SELECT * FROM node_execution_vault WHERE workflowId = :wfId ORDER BY id DESC")
    fun observeLogsByWorkflow(wfId: String): Flow<List<NodeExecutionLogEntity>>

    @Query("SELECT * FROM node_execution_vault ORDER BY id DESC")
    suspend fun getFullLogsSnapshot(): List<NodeExecutionLogEntity>

    @Query("DELETE FROM node_execution_vault")
    suspend fun purgeAllLogs()
}
