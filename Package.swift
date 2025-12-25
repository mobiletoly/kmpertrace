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
            checksum: "2152c0f6756b74719c16a2ba7423172f4af7744ce53a5e95e127cbde1046c7f6"
        )
    ]
)
