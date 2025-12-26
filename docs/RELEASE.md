# KmperTrace Release Checklist (Maven + SwiftPM)

This repo publishes:
- Maven Central: `kmpertrace-runtime` (and other JVM/KMP artifacts) via `publish.yml` on GitHub Release.
- SwiftPM binary: XCFramework zip attached to the GitHub Release, with `Package.swift` in the repo root pointing to that zip.

## Release steps (SwiftPM + Maven)
1) Bump version
   - Edit `gradle.properties` `kmpertraceVersion=...` (e.g., `0.1.5` -> `0.1.6`).
   - Commit & push to main.

2) Compute SwiftPM checksum for the XCFramework zip
   - GitHub → Actions → **Compute SPM XCFramework checksum** → Run workflow.
   - Use workflow from: `main`.
   - Fill the "Version hint (e.g. 0.1.6) — used only for logging a suggested URL" field with `<version>`.
   - Run it on the commit that contains the version bump (create a temporary tag/branch if needed).
   - CI builds the XCFramework, normalizes archives/Info.plist for deterministic output, zips it, and prints the checksum + suggested URL.

3) Update `Package.swift`
   - From helper logs, copy checksum + suggested URL (`.../releases/download/v<version>/KmperTraceRuntime.xcframework.zip`).
   - Edit root `Package.swift` with that URL and checksum.
   - Commit & push.

4) Tag
   - Ensure HEAD contains the updated `Package.swift`.
   - Tag the commit: `git tag v<version>`; `git push origin v<version>`.

5) Create GitHub Release (manual)
   - In GitHub UI, create a release for tag `v<version>`.
   - This release event triggers `publish.yml` and uploads the XCFramework zip to the release assets.

6) What CI does on the release event
   - publish.yml:
     - Publishes Maven artifacts.
     - Builds the iOS XCFramework zip from the tag commit.
     - Normalizes the static archives/Info.plist to keep the checksum stable across runs.
     - Verifies `swift package compute-checksum` on that zip matches `Package.swift`.
     - Uploads the zip to the GitHub Release.
     - Fails if checksum mismatches.

## SwiftPM consumers
- Add package dependency:
  ```swift
  .package(url: "https://github.com/mobiletoly/kmpertrace.git", from: "<version>")
  ```
- SwiftPM resolves the tag, reads `Package.swift` from that tag, downloads the zip from the release asset, and verifies the checksum.

## Notes / Gotchas
- The commit the tag points to must already contain the final `Package.swift`; SwiftPM ignores later commits.
- The helper workflow exists to help you update `Package.swift` before tagging; publish.yml still verifies the checksum at release time.
- If checksum verification fails in publish.yml, update `Package.swift` and retag with a new version.
- `Package.swift` must live in the repo root and be correct per tag; SwiftPM ignores Package.swift uploaded as a release asset.
- `Package.swift` can be formatted either as multi-line or single-line; CI parses `checksum: "..."` anywhere in the file.
- Do not use Actions artifact URLs in `Package.swift`; use the GitHub Release asset URL.
- If you run publish.yml manually via workflow_dispatch, ensure the tag exists and set "Tag/branch to run against" to `v<version>`; the release upload step requires a tag context.
- Avoid snapshot versions when tagging.
- Deprecated Gradle warnings about `exec` are harmless but can be cleaned up later.
