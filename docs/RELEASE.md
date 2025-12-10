# KmperTrace Release Checklist (Maven + SwiftPM)

This repo publishes:
- Maven Central: `kmpertrace-runtime` (and other JVM/KMP artifacts) via `publish.yml` on GitHub Release.
- SwiftPM binary: XCFramework zip attached to the GitHub Release, with `Package.swift` in the repo root pointing to that zip.

## 1) Bump version
- Edit `gradle.properties` `kmpertraceVersion=...` (e.g., `0.1.1` -> `0.1.2`).
- The CLI version is derived automatically from `project.version`.

## 2) Prepare SwiftPM artifacts locally
```bash
./gradlew :kmpertrace-runtime:prepareSpmRelease
```
This will:
- Build the release `KmperTraceRuntime.xcframework`
- Zip it under `kmpertrace-runtime/build/XCFrameworks/release/KmperTraceRuntime.xcframework.zip`
- Compute SHA-256
- Overwrite the root `Package.swift` using `Package.swift.template` with:
  - URL: `https://github.com/mobiletoly/kmpertrace/releases/download/v<version>/KmperTraceRuntime.xcframework.zip`
  - Checksum: the computed SHA-256
- Print the URL + checksum to console.

## 3) Review diffs
```bash
git diff
```
Confirm:
- `gradle.properties` version bump.
- `Package.swift` URL points to `.../v<version>/...`
- Checksum looks updated.

## 4) Commit and tag
```bash
git commit -am "Release KmperTrace v<version>"
git tag v<version>
git push origin main
git push origin v<version>
```

## 5) Create GitHub Release for the tag
- Use the tag `v<version>` you just pushed.
- The `publish.yml` workflow (runs on release event) will:
  - Publish Maven artifacts (runtime, etc.)
  - Build the release XCFramework
  - Zip it as `KmperTraceRuntime.xcframework.zip`
  - Upload the zip as a GitHub Release asset (for SwiftPM binary target)

## 6) SwiftPM consumers
- Add package dependency:
  ```swift
  .package(url: "https://github.com/mobiletoly/kmpertrace.git", from: "<version>")
  ```
- SwiftPM resolves the tag, reads `Package.swift` from that tag, downloads the zip from the release asset, and verifies the checksum.

## Notes / Gotchas
- `Package.swift` must live in the repo root and be correct per tag; SwiftPM ignores Package.swift uploaded as a release asset.
- If you run `prepareSpmRelease` on `-SNAPSHOT`, it will write a URL with that version. Only tag/publish with non-snapshot versions.
- Deprecated Gradle warnings about `exec` are harmless but can be cleaned up later.
