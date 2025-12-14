package dev.goquick.kmpertrace.cli

internal fun detectConsoleWidth(): Int {
    System.getenv("COLUMNS")?.toIntOrNull()?.let { if (it > 0) return it }
    return 80
}

