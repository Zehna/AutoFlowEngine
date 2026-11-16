package com.taras.autoflow.data

import androidx.room.*

@Entity(tableName = "workflow_state_store")
data class WorkflowStateEntity(
    @PrimaryKey val key: String,
    val value: String,
    val namespace: String = "default",
    val updatedAt: Long = System.currentTimeMillis()
)

@Dao
interface WorkflowStateDao {
    @Query("SELECT value FROM workflow_state_store WHERE `key` = :key AND namespace = :namespace LIMIT 1")
    suspend fun getValue(key: String, namespace: String = "default"): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putValue(entity: WorkflowStateEntity)

    @Query("DELETE FROM workflow_state_store WHERE `key` = :key AND namespace = :namespace")
    suspend fun deleteKey(key: String, namespace: String = "default")

    @Query("DELETE FROM workflow_state_store WHERE namespace = :namespace")
    suspend fun clearNamespace(namespace: String)
}
