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
            url: "https://github.com/mobiletoly/kmpertrace/releases/download/v0.1.4/KmperTraceRuntime.xcframework.zip",
            checksum: "62aab8931c4232665d2a733fe6aa0f69e0c04572d84ff58a284175d83ffde9de"
        )
    ]
)
