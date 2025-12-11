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
            url: "https://github.com/mobiletoly/kmpertrace/releases/download/v0.1.8/KmperTraceRuntime.xcframework.zip",
            checksum: "c1911a473168eccbd98bd751fd7002a91aea159c05144200f4ec71df2d4afff5"
        )
    ]
)
