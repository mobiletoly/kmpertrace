package dev.goquick.kmpertrace.cli

import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.UsageError

internal fun usageError(message: String): Nothing = throw UsageError(message)

internal fun cliError(message: String): Nothing = throw CliktError(message)

