plugins { id("com.android.application") }

apply(from = rootProject.file("ads/ads-config.gradle"))

android {
    namespace = "dev.manage.sample"
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
    implementation(project(":ads"))
    implementation("androidx.activity:activity:1.9.0")
    implementation("com.google.android.ump:user-messaging-platform:4.0.0")
}
