package com.anywall.converter.domain.model

import java.util.UUID

/**
 * Estado reactivo en vivo del trabajo de conversión actual en WorkManager.
 */
data class ActiveJobState(
    val workId: UUID? = null,
    val isRunning: Boolean = false,
    val progress: Float = 0f,
    val statusMessage: String = "",
    val isCompleted: Boolean = false,
    val isFailed: Boolean = false,
    val errorMessage: String? = null,
    val generatedCount: Int = 0
)
