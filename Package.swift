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
            url: "https://github.com/mobiletoly/kmpertrace/releases/download/v0.1.10/KmperTraceRuntime.xcframework.zip",
            checksum: "0fe1efc170d312c1f0141c328e4e0aeb5d0504980bdaaccce2cb2de2a051788c"
        )
    ]
)
