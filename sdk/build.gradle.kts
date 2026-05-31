import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import org.jetbrains.kotlin.gradle.dsl.KotlinJsCompile
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.android.lint)
    alias(libs.plugins.vanniktech.mavenPublish)
}

kotlin {

    // Target declarations - add or remove as needed below. These define
    // which platforms this KMP module supports.
    // See: https://kotlinlang.org/docs/multiplatform-discover-project.html#targets
    androidLibrary {
        namespace = "com.gilbertohdz.sdk"
        compileSdk = 36
        minSdk = 24

        withHostTestBuilder {
        }

        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    // For iOS targets, this is also where you should
    // configure native binary output. For more information, see:
    // https://kotlinlang.org/docs/multiplatform-build-native-binaries.html#build-xcframeworks

    // A step-by-step guide on how to include this library in an XCode
    // project can be found here:
    // https://developer.android.com/kotlin/multiplatform/migrate
    val xcframeworkName = "kmpsdk"
    val xcf = XCFramework(xcframeworkName)

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = xcframeworkName
            // Specify CFBundleIdentifier to uniquely identify the framework
            binaryOption("bundleId", "com.gilbertohdz.${xcframeworkName}")
            xcf.add(this)
            isStatic = true
        }
    }

    // Source set declarations.
    // Declaring a target automatically creates a source set with the same name. By default, the
    // Kotlin Gradle Plugin creates additional source sets that depend on each other, since it is
    // common to share sources between related targets.
    // See: https://kotlinlang.org/docs/multiplatform-hierarchy.html
    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlin.stdlib)
                // Add KMP dependencies here
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }

        androidMain {
            dependencies {
                // Add Android-specific dependencies here. Note that this source set depends on
                // commonMain by default and will correctly pull the Android artifacts of any KMP
                // dependencies declared in commonMain.
            }
        }

        getByName("androidDeviceTest") {
            dependencies {
                implementation(libs.androidx.runner)
                implementation(libs.androidx.core)
                implementation(libs.androidx.junit)
            }
        }

        iosMain {
            dependencies {
                // Add iOS-specific dependencies here. This a source set created by Kotlin Gradle
                // Plugin (KGP) that each specific iOS target (e.g., iosX64) depends on as
                // part of KMP’s default source set hierarchy. Note that this source set depends
                // on common by default and will correctly pull the iOS artifacts of any
                // KMP dependencies declared in commonMain.
            }
        }

        jsMain {
            dependencies {

            }
        }
    }

    js {
        moduleName = "kmpsdk"
        browser {
            binaries.library()
        }
        nodejs {
            binaries.library()
        }
        compilations["main"].packageJson {
            name = "@gilbertohdz/kmpsdk"
            version = project.version.toString()
            customField("description", "KMP SDK for JavaScript")
            customField("repository", mapOf(
                "type" to "git",
                "url" to "https://github.com/GilbertoHdz/KMPSdk.git"
            ))
            customField("keywords", listOf("kmp", "kotlin", "multiplatform"))
            customField("author", "Gilberto Hernandez")
            customField("license", "MIT")
        }
    }
}

tasks.withType<KotlinJsCompile>().configureEach {
    compilerOptions {
        target = "es2015"
    }
}


tasks.register<Exec>("publishToNpm") {
    dependsOn("jsNodeProductionLibraryDistribution")
    workingDir = layout.buildDirectory.asFile.get().resolve("dist/js/productionLibrary")
    commandLine("sh", "-c", """
        npm config set @gilbertohdz:registry https://npm.pkg.github.com
        npm config set //npm.pkg.github.com/:_authToken ${System.getenv("GITHUB_TOKEN") ?: ""}
        npm publish
    """.trimIndent())
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/gilbertohdz/KMPSdk")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

mavenPublishing {
    configure(
        KotlinMultiplatform(
            javadocJar = JavadocJar.None(),
            sourcesJar = false
        )
    )
}

val baseVersion = providers.gradleProperty("VERSION_NAME").get()
val isSnapshot = System.getenv("IS_SNAPSHOT")?.toBoolean() ?: true


group = providers.gradleProperty("GROUP").get()
version = if (isSnapshot) "$baseVersion-SNAPSHOT" else baseVersion



// ========== Build Tasks (Local) ==========

// 1. Zip del XCFramework
val packageXCFramework by tasks.registering(Zip::class) {
    group = "build"
    description = "Zip the XCFramework for distribution"

    dependsOn("assembleKmpsdkReleaseXCFramework")

    from("build/XCFrameworks/release/kmpsdk.xcframework")
    archiveFileName.set("kmpsdk.xcframework.zip")
    destinationDirectory.set(file("build/XCFrameworks/release"))

    doLast {
        println("✅ XCFramework ZIP created at: ${destinationDirectory.asFile.get()}/kmpsdk.xcframework.zip")
    }
}

// 2. Build npm package (local, no publish)
val buildNpm by tasks.registering {
    group = "build"
    description = "Build the JS library for Node.js"

    dependsOn("jsNodeProductionLibraryDistribution")

    doLast {
        val npmDir = file("build/dist/js/productionLibrary")
        if (npmDir.exists()) {
            println("✅ npm package built at: ${npmDir.absolutePath}")
            println("   Files:")
            npmDir.listFiles()?.forEach { file ->
                if (file.isFile) println("   - ${file.name}")
            }
        } else {
            println("❌ npm package directory not found")
        }
    }
}

// 3. Build everything locally (no publish)
val buildAll by tasks.registering {
    group = "build"
    description = "Build all artifacts locally (Maven, npm, XCFramework) without publishing"

    dependsOn(
        "build",
        buildNpm,
        packageXCFramework
    )

    tasks["buildNpm"].mustRunAfter("build")
    packageXCFramework.get().mustRunAfter("build")

    doLast {
        println("")
        println("========================================")
        println("✅ Build Complete!")
        println("========================================")
        println("Version: $version (isSnapshot=$isSnapshot)")
        println("")
        println("📦 Artifacts Generated:")
        println("   Maven: build/outputs/")
        println("   npm:   build/dist/js/productionLibrary/")
        println("   iOS:   build/XCFrameworks/release/kmpsdk.xcframework.zip")
        println("")
    }
}

// ========== Publish Tasks (GitHub) ==========

// 4. Publish npm to GitHub Packages
val publishNpmToGitHub by tasks.registering(Exec::class) {
    group = "publishing"
    description = "Publish the npm package to GitHub Packages (requires GITHUB_TOKEN)"

    dependsOn("jsNodeProductionLibraryDistribution")

    workingDir("build/dist/js/productionLibrary")
    commandLine(
        "sh", "-c",
        """
        npm config set @gilbertohdz:registry https://npm.pkg.github.com &&
        npm config set //npm.pkg.github.com/:_authToken ${'$'}{GITHUB_TOKEN:-""} &&
        npm publish
        """.trimIndent()
    )

    onlyIf {
        System.getenv("GITHUB_TOKEN") != null
    }
}

// 5. Publish Maven to GitHub Packages (handled by vanniktech plugin)
// Just runs the standard "publish" task configured in publishing block

// 6. Master publish task (GitHub Actions)
val publishAllToGitHub by tasks.registering {
    group = "publishing"
    description = "Build and publish all artifacts to GitHub Packages (for CI/CD)"

    dependsOn(
        "build",
        buildNpm,
        packageXCFramework,
        "publish",              // Maven (vanniktech plugin)
        publishNpmToGitHub
    )

    tasks["buildNpm"].mustRunAfter("build")
    packageXCFramework.get().mustRunAfter("build")
    tasks["publish"].mustRunAfter("build")
    publishNpmToGitHub.get().mustRunAfter("buildNpm")

    doLast {
        println("")
        println("========================================")
        println("✅ Publish Complete!")
        println("========================================")
        println("Version: $version (isSnapshot=$isSnapshot)")
        println("")
        println("📦 Published Artifacts:")
        println("   Maven:   GitHub Packages")
        println("   npm:     GitHub Packages")
        println("   iOS ZIP: (for GitHub Release)")
        println("")
    }
}