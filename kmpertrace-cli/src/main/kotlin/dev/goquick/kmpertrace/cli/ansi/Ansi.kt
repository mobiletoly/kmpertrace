package dev.goquick.kmpertrace.cli.ansi

/**
 * Controls whether ANSI color codes should be used in CLI output.
 */
enum class AnsiMode { AUTO, ON, OFF }

internal fun AnsiMode.shouldColorize(): Boolean =
    when (this) {
        AnsiMode.ON -> true
        AnsiMode.OFF -> false
        AnsiMode.AUTO -> System.console() != null
    }

internal object AnsiPalette {
    const val header = "\u001B[1;36m" // bold cyan
    const val span = "\u001B[1m" // bold
    const val timestamp = "\u001B[2m" // dim
    const val logger = "\u001B[36m" // cyan
    const val source = "\u001B[35m" // magenta
    const val location = "\u001B[2m" // dim
    const val error = "\u001B[31m" // red
    const val warn = "\u001B[33m" // yellow
    const val statusBg = "\u001B[48;5;254m" // light gray background
    const val statusFg = "\u001B[30m" // black foreground for status line
    const val statusTrace = "\u001B[34m" // blue for trace count
    const val reset = "\u001B[0m"
}

internal fun maybeColor(text: String, code: String, colorize: Boolean): String =
    if (colorize) "$code$text${AnsiPalette.reset}" else text

internal fun maybeColorBold(text: String, colorize: Boolean): String =
    if (colorize) "${AnsiPalette.span}$text${AnsiPalette.reset}" else text

internal fun stripAnsi(text: String): String = text.replace(ansiRegex, "")

private val ansiRegex = "\u001B\\[[;\\d]*m".toRegex()
