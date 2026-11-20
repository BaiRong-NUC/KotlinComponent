pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("com.android.application") version "9.2.1"
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "sample"
extra["manageAdsAppPath"] = ":"
extra["manageAdsDir"] = "../ads"
apply(from = "gradle/ads.gradle")
