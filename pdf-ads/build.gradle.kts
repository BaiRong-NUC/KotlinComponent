plugins {
    id("com.android.library")
}

group = "com.pdf.shared"
version = "0.1.0"

android {
    namespace = "com.pdf.shared.ads"
    compileSdk = 36
    defaultConfig {
        minSdk = 24
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    api("com.google.android.gms:play-services-ads:25.5.0")
    api("com.adjust.sdk:adjust-android:5.8.0")
    testImplementation("junit:junit:4.13.2")
}
