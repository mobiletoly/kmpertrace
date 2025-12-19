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
            url: "https://github.com/mobiletoly/kmpertrace/releases/download/v0.2.3/KmperTraceRuntime.xcframework.zip",
            checksum: "d626d35f466eef6e54e1e80e2f15a94beb346473f934309678e5934041f7a1f5"
        )
    ]
)
