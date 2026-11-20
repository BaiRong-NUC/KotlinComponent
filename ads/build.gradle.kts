plugins {
    id("com.android.library")
}

group = "dev.manage"
version = "1.0.0"

android {
    namespace = "dev.manage.ads"
    compileSdk { version = release(36) { minorApiLevel = 1 } }
    defaultConfig {
        minSdk = 24
    }
    testOptions.unitTests.isReturnDefaultValues = true
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    api("com.google.android.gms:play-services-ads:25.5.0")
    api("androidx.lifecycle:lifecycle-runtime:2.9.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.google.android.play:hsdp:2.2.0")
    implementation("com.google.ads.mediation:applovin:13.6.4.2")
    implementation("com.google.ads.mediation:facebook:6.22.0.0")
    implementation("com.google.ads.mediation:mintegral:17.1.81.1")
    implementation("com.google.ads.mediation:pangle:8.3.0.4.0") {
        exclude(group = "com.pangle.global", module = "tiktok-business-android-sdk-comp")
    }
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test-jvm:1.9.0")
}
