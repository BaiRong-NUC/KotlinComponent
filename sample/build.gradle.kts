plugins {
    id("com.android.application")
}

android {
    namespace = "dev.manage.sample"
    sourceSets.getByName("main").kotlin.srcDir(rootProject.file("../tool/http"))
    testOptions.unitTests.isReturnDefaultValues = true
    compileSdk { version = release(36) { minorApiLevel = 1 } }
    defaultConfig {
        applicationId = "dev.manage.sample"
        minSdk = 24
        targetSdk = 36
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test-jvm:1.9.0")
    implementation("androidx.activity:activity:1.9.0")
    implementation("com.google.android.ump:user-messaging-platform:4.0.0")
}
