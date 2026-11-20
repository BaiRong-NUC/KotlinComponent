pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("com.android.library") version "9.2.1"
        id("com.android.application") version "9.2.1"
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

apply(from = "gradle/ads-repositories.settings.gradle")

rootProject.name = "Manage"
include(":ads", ":sample")
