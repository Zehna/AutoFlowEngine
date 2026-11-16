package com.taras.autoflow.util

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

object ShizukuHelper {
    fun isAvailable(): Boolean = Shizuku.pingBinder()

    fun hasPermission(): Boolean {
        return if (isAvailable()) {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } else false
    }

    fun requestPermission(requestCode: Int) {
        if (isAvailable() && !hasPermission()) {
            Shizuku.requestPermission(requestCode)
        }
    }
}
