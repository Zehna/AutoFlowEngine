package com.taras.autoflow.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "workflow_state_store")
data class WorkflowStateEntity(
    @PrimaryKey val key: String,
    val value: String,
    val namespace: String = "default",
    val expiresAt: Long? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

@Dao
interface WorkflowStateDao {
    @Query("SELECT value FROM workflow_state_store WHERE `key` = :key AND namespace = :namespace AND (expiresAt IS NULL OR expiresAt > :now) LIMIT 1")
    suspend fun getValidValue(key: String, namespace: String = "default", now: Long = System.currentTimeMillis()): String?

    @Query("SELECT value FROM workflow_state_store WHERE `key` = :key AND namespace = :namespace LIMIT 1")
    suspend fun getValue(key: String, namespace: String = "default"): String?

    @Query("SELECT value FROM workflow_state_store WHERE `key` = :key AND (expiresAt IS NULL OR expiresAt > :now) LIMIT 1")
    suspend fun getValueAnyNamespace(key: String, now: Long = System.currentTimeMillis()): String?

    @Query("SELECT * FROM workflow_state_store ORDER BY updatedAt DESC")
    fun observeAllStates(): Flow<List<WorkflowStateEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putValue(entity: WorkflowStateEntity)

    @Query("DELETE FROM workflow_state_store WHERE expiresAt IS NOT NULL AND expiresAt <= :now")
    suspend fun purgeExpired(now: Long = System.currentTimeMillis())

    @Query("DELETE FROM workflow_state_store WHERE `key` = :key AND namespace = :namespace")
    suspend fun deleteKey(key: String, namespace: String = "default")

    @Query("DELETE FROM workflow_state_store WHERE `key` = :key")
    suspend fun deleteByKey(key: String)

    @Query("DELETE FROM workflow_state_store WHERE namespace = :namespace")
    suspend fun clearNamespace(namespace: String)
}
