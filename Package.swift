// swift-tools-version: 5.9
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
        .binaryTarget(
            name: "kmpsdk",
            url: "https://github.com/GilbertoHdz/KMPSdk/releases/download/0.1.0/kmpsdk.xcframework.zip",
            checksum: "2e92910f27e4ea27ca62cdbd548e5d733d9055aede0c3d12c4d612ba245409ce"
        )
    ]
)
