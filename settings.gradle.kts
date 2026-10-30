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
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Obsidian design system, published from github.com/Redfox-OSS/obsidian.
        maven("https://redfox-oss.github.io/obsidian/maven") {
            content { includeGroup("dev.redfox") }
        }
    }
}

rootProject.name = "LookPe"
include(":app")
