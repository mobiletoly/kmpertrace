package dev.goquick.kmpertrace.cli

import dev.goquick.kmpertrace.cli.ansi.AnsiMode

internal fun parseAnsiMode(color: String?): AnsiMode =
    when (color?.lowercase()) {
        "on", "true", "yes" -> AnsiMode.ON
        "off", "false", "no" -> AnsiMode.OFF
        null, "auto" -> AnsiMode.AUTO
        else -> usageError("Invalid --color value: $color (use auto|on|off)")
    }

internal fun parseTimeFormat(timeFormatOpt: String?): TimeFormat =
    when (timeFormatOpt?.lowercase()) {
        null, "time-only", "time" -> TimeFormat.TIME_ONLY
        "full" -> TimeFormat.FULL
        else -> usageError("Invalid --time-format value: $timeFormatOpt (use full|time-only)")
    }

/**
 * Returns (maxLineWidth, autoWidth). `autoByDefault` controls the null case:
 * - Print: false (manual width unless user asked for auto)
 * - TUI: true (auto width when user omits the flag)
 */
internal fun resolveWidth(maxLineWidthOpt: String?, autoByDefault: Boolean): Pair<Int?, Boolean> =
    when {
        maxLineWidthOpt == null -> null to autoByDefault
        maxLineWidthOpt.equals("auto", ignoreCase = true) -> null to true
        maxLineWidthOpt.equals(
            "unlimited",
            ignoreCase = true
        ) || maxLineWidthOpt == "0" -> null to false

        else -> maxLineWidthOpt.toInt() to false
    }

internal fun validateMaxWidth(value: String) {
    require(
        value.equals("auto", ignoreCase = true) ||
            value.equals("unlimited", ignoreCase = true) ||
            value == "0" ||
            value.toIntOrNull()?.let { n -> n > 0 } == true
    ) { "max-line-width must be a positive integer, 'auto', or 'unlimited'/0" }
}
