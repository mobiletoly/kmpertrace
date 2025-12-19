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
   - Run it on the commit that contains the version bump.
   - CI builds the XCFramework, zips it, prints the checksum and suggested URL.

3) Update `Package.swift`
   - From helper logs, copy checksum + suggested URL (`.../releases/download/v<version>/KmperTraceRuntime.xcframework.zip`).
   - Edit root `Package.swift` with that URL and checksum.
   - Commit & push.

4) Tag
   - Ensure HEAD contains the updated `Package.swift`.
   - Tag the commit: `git tag v<version>`; `git push origin v<version>`.

5) Create GitHub Release (manual)
   - In GitHub UI, create a release for tag `v<version>`.

6) What CI does on the release event
   - publish.yml:
     - Publishes Maven artifacts.
     - Builds the iOS XCFramework zip from the tag commit.
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
- Avoid snapshot versions when tagging.
- Deprecated Gradle warnings about `exec` are harmless but can be cleaned up later.
