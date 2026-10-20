plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.bookzor.core.torrent"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // libtorrent4j & Android native binaries (arm64 for tablet, plus arm & x86_64)
    api(libs.libtorrent4j)
    api(libs.libtorrent4j.android.arm64)
    api(libs.libtorrent4j.android.arm)
    api(libs.libtorrent4j.android.x64)

    // Embedded HTTP Range Loopback Server
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)

    testImplementation(libs.junit)
}
