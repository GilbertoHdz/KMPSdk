// swift-tools-version: 5.9
//
// Swift Package Manager configuration for KMP SDK
// Using source-based distribution (local path) to avoid checksum management
//
// Created by Gilberto Hernandez on 31/01/26.
//
import PackageDescription

let package = Package(
    name: "kmpsdk",
    platforms: [
        .iOS(.v14)
    ],
    products: [
        .library(
            name: "kmpsdk",
            targets: ["kmpsdk"]
        )
    ],
    targets: [
        // Source-based distribution using local XCFramework path
        // This eliminates the need for manual checksum management
        // The XCFramework is built via Gradle and committed to the repository
        .binaryTarget(
            name: "kmpsdk",
            path: "sdk/build/XCFrameworks/release/kmpsdk.xcframework"
        )
    ]
)
