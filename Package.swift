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
            url: "https://github.com/mobiletoly/kmpertrace/releases/download/v0.2.0/KmperTraceRuntime.xcframework.zip",
            checksum: "d1dc51eef739f9ba6339846122a808df53c3d79b630d958f5bc103d95d84cd34"
        )
    ]
)
