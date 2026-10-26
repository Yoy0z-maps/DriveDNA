pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Pleos Playground artifacts (anonymous read; no credentials required).
        maven("https://nexus-playground.pleos.ai/repository/maven-releases/") {
            content { includeGroup("ai.pleos.playground") }
        }
    }
}

rootProject.name = "DriveDNA"
include(":app", ":core:domain", ":core:data", ":vehicle")
