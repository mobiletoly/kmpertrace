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
            url: "https://github.com/mobiletoly/kmpertrace/releases/download/v0.1.3/KmperTraceRuntime.xcframework.zip",
            checksum: "2b821338a69bdabfea99e83bf7010f0b0c402e531332663fe9b26349b19df179"
        )
    ]
)
