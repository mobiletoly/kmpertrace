# KmperTrace iOS XCFramework (pure iOS/Swift)

Two ways to link the runtime without KMP in your Xcode project:

If you have a KMP app with Swift host code, prefer exporting KmperTrace from your KMP-produced
framework instead: see `docs/IOS-KMP-Swift.md`.

## 1) Manual drag & drop
1. Grab `KmperTraceRuntime.xcframework.zip` from the GitHub Release assets.
2. Unzip, drag `KmperTraceRuntime.xcframework` into Xcode, select **Embed & Sign**.
3. In Swift/ObjC, `import KmperTraceRuntime`.

## 2) Swift Package Manager (binary target)
1. From the same Release, download the accompanying `Package.swift` (generated per release).
2. In Xcode > Add Package Dependency, point to the repo URL and pick the tag matching the release.
   - The `Package.swift` binary target references the release asset URL and checksum.

Notes:
- Built from `iosArm64` + `iosSimulatorArm64`, static by default.
- CI builds on macOS using `./gradlew :kmpertrace-runtime:assembleKmperTraceRuntimeReleaseXCFramework`; the zipped XCFramework + Package.swift are attached to releases automatically.

## Swift-friendly logging + trace binding

Kotlin-style logging APIs use message lambdas (`Log.d { ... }`), which are awkward to call from Swift.
The runtime ships a small Swift-facing facade:

- `dev.goquick.kmpertrace.swift.KmperTraceSwift` — string-based logging (`d/i/w/e`) and snapshot helpers.

Recommended pattern for non-coroutine callbacks:

1) Capture a `KmperTraceSnapshot` while a span is active.
2) In Swift callbacks (GCD/delegate/SDK callbacks), re-install it with `snapshot.with { ... }` (or `KmperTraceSwift.withSnapshot(...)`) while emitting logs via `KmperTraceSwift`.
