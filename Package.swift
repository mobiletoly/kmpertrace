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
            url: "https://github.com/mobiletoly/kmpertrace/releases/download/v0.1.9/KmperTraceRuntime.xcframework.zip",
            checksum: "b7e7a626adfdb9b381f847800610f0fe3d4de5b235c5f5baf6eacd7af7851bd7"
        )
    ]
)
