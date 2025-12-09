# KmperTrace CLI — User Guide

Practical how-to for both non-interactive (`print`) and interactive (`tui`) modes. Keep this current when flags or UX change.

## Quick Start (copy/paste)

```bash
# Install once
./gradlew :kmpertrace-cli:installDist

# Non-interactive: render a file
kmpertrace-cli print --file app.log --color=on

# Non-interactive: pipe and follow
adb logcat -v epoch | kmpertrace-cli print --follow --color=on

# Interactive: Android live (auto PID reattach)
kmpertrace-cli tui --source adb --adb-pkg dev.goquick.kmpertrace.sampleapp

# Interactive: iOS sim
kmpertrace-cli tui --source ios --ios-proc SampleApp

# Interactive: tail a file
kmpertrace-cli tui --source file --file /path/to/log.log --follow
```

## When to Use Which Mode

- `print`: one-shot or piping (CI logs, saved files, scripts). `--follow` tails stdin/file.
- `tui`: live viewing with incremental redraw, search toggle, status bar, auto width.

## Key Behaviors

- Wrapping: default `tui` uses `--max-line-width=auto`; `print` is unlimited unless set. Soft-wrap for user messages; stacks/metadata stay hard-wrapped.
- Sources:
  - `adb`: builds a command that waits for the package PID and reattaches if the app restarts.
  - `ios`: uses `xcrun simctl ... --predicate process == "<proc>"`.
  - `file`/`stdin`: just read; `--follow` tails the file/stdin stream.
- Buffer: ring buffer (default 5000 events). Dropped count shown in the status bar.
- Raw logs (opt-in): `--raw-logs` or `[r]` in `tui` merges non-KmperTrace lines from the same process into the untraced timeline; `[r]` cycles off → debug → info → error → all.
- Parsing in both `print` and `tui`:
  - Structured KmperTrace lines end with `|{ … }|`; they are always parsed first and shown in traces/untraced sections.
  - When raw is enabled, non-KmperTrace lines from the same process are also shown. We understand common Android inputs (`adb logcat -v epoch`, threadtime, Android Studio console copy/paste) and Apple unified logs (`log stream/show`), so you can pipe saved files or live streams directly into `print` or `tui`.

## Flags (essentials)

- Inputs:
  - `-s/--source {stdin|file|adb|ios}` (default `stdin`). Pick `adb`/`ios` for live devices; `file` for saved/tailing logs.
  - `-f/--file PATH` use a file (add `--follow` to tail).
  - `--adb-pkg PKG` build the adb command (waits for PID, reattaches on restart); `--adb-cmd CMD` override entirely.
  - `--ios-proc NAME` build the simctl command; `--ios-cmd CMD` override entirely.
- Formatting (defaults: `color=auto`, `tui` width=auto, `print` width=unlimited, `time-format=time-only`, sources shown):
  - `-C/--color {auto|on|off}` force/disable color.
  - `-w/--max-line-width {N|auto|unlimited|0}` wrap width. `auto` tracks terminal width (tui default). `0`/`unlimited` disables wrap.
  - `-T/--time-format {full|time-only}` `full` = `2025-01-01T12:34:56.789Z`; `time-only` = `12:34:56.789` (default).
  - `-H/--hide-source` hide component/operation metadata.
- Filters (stack/message search is case-insensitive):
  - `-m/--min-level {verbose|debug|info|warn|error|assert}`
  - `--trace-id ID`, `--component NAME`, `--operation NAME`
  - `-F/--filter SUBSTRING` match message/stack.
  - `--exclude-untraced` drop events with `trace_id=0`.
- Buffer:
  - `-M/--max-events N` ring buffer size before dropping oldest (default 5000).
- Raw logs:
  - `--raw-logs {off|all|verbose|debug|info|warn|error|assert}` (default `off`). Applies to both `print` and `tui`; merges matching raw lines into the output. In `tui`, `[r]` cycles the level even if the flag was not set.

## Interactive Keys (tui)

- `?` help | `c` clear | `r` cycle raw logs (off → debug → info → error → all) | `l` cycle structured min level (all → debug → info → error) | `/` search term (empty clears) | `q` quit
- Raw single-key on Unix. If raw unavailable (e.g., Windows), type the letter then Enter.

## Troubleshooting

- Nothing from `adb`: ensure `--adb-pkg` is correct and the app is installed; CLI waits for PID and reattaches on restart.
- Wide/ugly stacks: set `-w auto` (tui default) or a number; use `unlimited/0` to disable wrap.
- Missing color: force `--color=on`; disable with `--color=off` for logs/CI.
- Too many drops: raise `-M`; status bar shows dropped count.
