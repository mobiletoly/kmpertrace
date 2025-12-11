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
            url: "https://github.com/mobiletoly/kmpertrace/releases/download/v0.1.11/KmperTraceRuntime.xcframework.zip",
            checksum: "0a7ae407206ad6ffebf87de134fa17cf9b8bb38dbffc7c36cb94c040dc422470"
        )
    ]
)
