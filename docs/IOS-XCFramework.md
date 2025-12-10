# KmperTrace iOS XCFramework (pure iOS/Swift)

Two ways to link the runtime without KMP in your Xcode project:

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
