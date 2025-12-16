# KmperTrace CLI

Small JVM CLI that renders structured KmperTrace logs into ASCII trace trees with level glyphs and
optional source metadata.

## Install / Run

From the repo root:

```bash
./gradlew :kmpertrace-cli:installDist
./kmpertrace-cli/build/install/kmpertrace-cli/bin/kmpertrace-cli --help
```

## Commands

### `print`

Render trace trees from structured logs (expects lines with the `|{ ts=... }|` suffix emitted by
KmperTrace).

Options:

- `--file`, `-f <path>`: Read from a file (defaults to stdin).
- `--hide-source`: Hide source component/operation/location metadata.
- `--max-line-width <N>`: Wrap output lines at N characters (unlimited when omitted).
- `--color {auto|on|off}`: ANSI color output (default: auto).
- `--time-format {time-only|full}`: Show timestamps as time-only (default) or full ISO.
- `--raw-logs {off|all|verbose|debug|info|warn|error|assert}`: Include non-KmperTrace raw lines (default: off).
- `--span-attrs {off|on}`: Show span attributes next to span names (default: off).
- `--help`: Show usage.

### `tui`

Interactive TUI that streams logs from a source (adb/ios/file/stdin) and live-refreshes.

See `docs/CLI-UserGuide.md` for current flags and keys.

iOS notes:
- Simulator logs are streamed via `xcrun simctl ... log stream`.
- Real device logs require `idevicesyslog` (libimobiledevice).

## Colors

- Default `--color=auto` only emits ANSI when stdout is a TTY. Gradle’s `:run` captures stdout, so colors are off unless forced.
- Force colors under Gradle: `./gradlew --console=rich :kmpertrace-cli:run --args="print --file /path/to.log --color=on"`.
- Or run the installed binary directly in a TTY: `./build/install/kmpertrace-cli/bin/kmpertrace-cli print --file /path/to.log --color=on`.
- Disable colors explicitly with `--color=off`.

## Examples

Render from a file:

```bash
kmpertrace-cli print --file /path/to/results.log
```

Render from `adb logcat` (Android):

```bash
adb logcat -v brief | kmpertrace-cli print
```

Wrap long lines to 80 chars:

```bash
kmpertrace-cli print --file results.log --max-line-width 80
```

Hide source metadata:

```bash
kmpertrace-cli print --file results.log --hide-source
```
Untraced log records (`trace=0`) are interleaved by timestamp alongside trace output (e.g., a record before a trace will show before the trace header).
