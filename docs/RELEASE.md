# KmperTrace Release Checklist (Maven + SwiftPM)

This repo publishes:
- Maven Central: `kmpertrace-runtime` (and other JVM/KMP artifacts) via `publish.yml` on GitHub Release.
- SwiftPM binary: XCFramework zip attached to the GitHub Release, with `Package.swift` in the repo root pointing to that zip.

## Release steps (CI-canonical XCFramework)
1) Bump version
   - Edit `gradle.properties` `kmpertraceVersion=...` (e.g., `0.1.5` -> `0.1.6`).
   - Commit & push to main.

2) Run helper workflow to build + checksum
   - GitHub → Actions → **Compute SPM XCFramework checksum** → Run workflow.
   - Select the commit you just pushed (not a tag).
   - CI builds the XCFramework, zips it, prints checksum and suggested URL, uploads the zip artifact.

3) Update `Package.swift`
   - From helper logs, copy checksum + suggested URL (`.../releases/download/v<version>/KmperTraceRuntime.xcframework.zip`).
   - Edit root `Package.swift` with that URL and checksum.
   - Commit & push.

4) Stage the zip for publish workflow
   - Download the `KmperTraceRuntime.xcframework.zip` artifact from the helper run.
   - Place it at `kmpertrace-runtime/build/XCFrameworks/release/KmperTraceRuntime.xcframework.zip` in the repo (so publish.yml can verify/upload it).

5) Tag
   - Ensure HEAD contains the updated `Package.swift`.
   - Tag the commit: `git tag v<version>`; `git push origin v<version>`.

6) Create GitHub Release (manual)
   - In GitHub UI, create a release for tag `v<version>`.

7) What CI does on the release event
   - publish.yml:
     - Publishes Maven artifacts.
     - Verifies `swift package compute-checksum` on the staged zip matches `Package.swift`.
     - Uploads that exact zip to the GitHub Release (no rebuild).
     - Fails if checksum mismatches or zip is missing.

## SwiftPM consumers
- Add package dependency:
  ```swift
  .package(url: "https://github.com/mobiletoly/kmpertrace.git", from: "<version>")
  ```
- SwiftPM resolves the tag, reads `Package.swift` from that tag, downloads the zip from the release asset, and verifies the checksum.

## Notes / Gotchas
- The commit the tag points to must already contain the final `Package.swift`; SwiftPM ignores later commits.
- The helper workflow is the canonical build; publish.yml does not rebuild the XCFramework.
- If checksum verification fails in publish.yml, rerun helper, update `Package.swift`, and retag with a new version.
- `Package.swift` must live in the repo root and be correct per tag; SwiftPM ignores Package.swift uploaded as a release asset.
- Avoid snapshot versions when tagging.
- Deprecated Gradle warnings about `exec` are harmless but can be cleaned up later.
