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
            url: "https://github.com/mobiletoly/kmpertrace/releases/download/v0.1.6/KmperTraceRuntime.xcframework.zip",
            checksum: "2f1bfcd89a5cdac86de18e9c3fedcf896ee9253a0171393302fad7d18c7dfe37"
        )
    ]
)
