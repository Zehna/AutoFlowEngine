package com.taras.autoflow.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        WorkflowStateEntity::class,
        NodeExecutionLogEntity::class,
        WorkflowEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AutoFlowDatabase : RoomDatabase() {
    abstract fun stateDao(): WorkflowStateDao
    abstract fun logDao(): NodeExecutionLogDao
    abstract fun workflowDao(): WorkflowEntityDao

    companion object {
        @Volatile
        private var instance: AutoFlowDatabase? = null

        fun getInstance(context: Context): AutoFlowDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AutoFlowDatabase::class.java,
                    "autoflow_v2.db"
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
    }
}
