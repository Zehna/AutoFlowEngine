package com.taras.autoflow.util

import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

data class ShellOutput(val exitCode: Int, val stdout: String, val stderr: String)

object ShizukuHelper {
    fun isAvailable(): Boolean = try {
        Shizuku.pingBinder()
    } catch (_: Exception) {
        false
    }

    fun hasPermission(): Boolean {
        return try {
            isAvailable() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) {
            false
        }
    }

    fun requestPermission(requestCode: Int) {
        if (isAvailable() && !hasPermission()) {
            Shizuku.requestPermission(requestCode)
        }
    }

    suspend fun runShell(command: String): Result<ShellOutput> = withContext(Dispatchers.IO) {
        if (!hasPermission()) {
            return@withContext Result.failure(IllegalStateException("Izin Shizuku belum diberikan atau layanan Shizuku belum berjalan"))
        }
        try {
            val method = Shizuku::class.java.getMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            val process = method.invoke(null, arrayOf("sh", "-c", command), null, null) as Process
            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            Result.success(ShellOutput(exitCode, stdout, stderr))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
