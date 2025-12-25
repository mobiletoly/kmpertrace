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
            url: "https://github.com/mobiletoly/kmpertrace/releases/download/v0.3.1/KmperTraceRuntime.xcframework.zip",
            checksum: "ab1f645650e10c79b48a2b5286aeb492deaebd2d739d5c3bf7c74ab640a6a1cf"
        )
    ]
)
