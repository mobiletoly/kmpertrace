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
            url: "https://github.com/mobiletoly/kmpertrace/releases/download/v0.2.2/KmperTraceRuntime.xcframework.zip",
            checksum: "a8d95c57300cb0c25aca82a653a73b974ef6d0fc51b581c801d2ac0c5decf07a"
        )
    ]
)
