pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()

        maven {
            url = uri("https://maven.pkg.github.com/GilbertoHdz/KMPSdk")
//            credentials {
//                username = providers.gradleProperty("githubPackageUsername").orNull
//                password = providers.gradleProperty("githubPackagePassword").orNull
//            }
        }

    }
}

rootProject.name = "KMP Sdk"
include(":app")
include(":sdk")
