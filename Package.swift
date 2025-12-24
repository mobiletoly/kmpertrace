// swift-tools-version:5.7
import PackageDescription

let package = Package(
    name: "KmperTraceRuntime",
    platforms: [
        .iOS(.v13)
    ],
    products: [
        .library(
            name: "KmperTraceRuntime",
            targets: ["KmperTraceRuntime"]
        )
    ],
    targets: [
        .binaryTarget(
            name: "KmperTraceRuntime",
            url: "https://github.com/mobiletoly/kmpertrace/releases/download/v0.3.0/KmperTraceRuntime.xcframework.zip",
            checksum: "2076d5c7b6bb5cef4a199d6f776e16e83ed427693c18f52a0a08435d30c33af1"
        )
    ]
)
