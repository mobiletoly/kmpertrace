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
            url: "https://github.com/mobiletoly/kmpertrace/releases/download/v0.2.1/KmperTraceRuntime.xcframework.zip",
            checksum: "74df20d77b96a13da085a4bdfeb52e8d3999f835c897a778a2b23edbec67ccb6"
        )
    ]
)
