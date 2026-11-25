package com.anywall.converter.ui.navigation

/**
 * Rutas de navegación principales de AnyWall: Universal Converter (v0.5).
 * Incluye las 5 pestañas de la barra de navegación inferior:
 * - `HOME` (Convertir: Selección rápida por categoría y formato específico)
 * - `TOOLS` (Estudios: los 6 Estudios Táctiles y Renombrador por lote)
 * - `SECURITY_DATA` (Seguridad y Datos: Bóveda AES-256, JSON/CSV/XML/YAML, Duplicados SHA-256, Inspector Forense, QR y Diff)
 * - `HISTORY` (Historial)
 * - `SETTINGS` (Ajustes)
 * más la pantalla dedicada de flujo de conversión (`CONFIGURE`).
 */
enum class AnyWallDestination(
    val route: String,
    val titleEs: String,
    val titleEn: String,
    val isBottomBarTab: Boolean
) {
    HOME("home", "Convertir", "Convert", true),
    TOOLS("tools", "Estudios", "Studios", true),
    SECURITY_DATA("security_data", "Seguridad", "Security", true),
    HISTORY("history", "Historial", "History", true),
    SETTINGS("settings", "Ajustes", "Settings", true),
    CONFIGURE("configure", "Configurar Lote", "Configure Batch", false);

    fun localizedTitle(isEn: Boolean): String = if (isEn) titleEn else titleEs
}
