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
            checksum: "80e9c8981d353b2aa92e9e726217afab8ce77322fe53315e89f0dab28a4741be"
        )
    ]
)
