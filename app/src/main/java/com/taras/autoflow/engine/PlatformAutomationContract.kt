package com.taras.autoflow.engine

data class HealthCheckItem(
    val title: String,
    val isGranted: Boolean,
    val actionLabel: String = "Aktifkan",
    val onAction: () -> Unit
)
