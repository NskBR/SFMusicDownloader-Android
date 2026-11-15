plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.nsk.sfmd"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.nsk.sfmd"
        minSdk = 26
        targetSdk = 37
        versionCode = providers.gradleProperty("sfmdVersionCode").orElse("2").get().toInt()
        versionName = providers.gradleProperty("sfmdVersion").orElse("0.1.1").get()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    splits { abi { isEnable = true; reset(); include("arm64-v8a", "x86_64"); isUniversalApk = false } }
    buildFeatures { compose = true; buildConfig = true }
    packaging { jniLibs { useLegacyPackaging = true; keepDebugSymbols += setOf("**/*.so") }; resources.excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*") }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies {
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3:1.3.2")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("io.github.junkfood02.youtubedl-android:library:0.18.1")
    implementation("io.github.junkfood02.youtubedl-android:common:0.18.1")
    implementation("io.github.deniscerri:ytdlnis_packages.ffmpeg:9.0.2")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.room:room-runtime:2.8.4")
    annotationProcessor("androidx.room:room-compiler:2.8.4")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    testImplementation("junit:junit:4.13.2")
    // API 37 removed InputManager.getInstance; older Espresso cannot inject UI events.
    androidTestImplementation("androidx.test:runner:1.8.0-alpha04")
    androidTestImplementation("androidx.test:rules:1.8.0-alpha04")
    androidTestImplementation("androidx.test.ext:junit:1.4.0-alpha04")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.8.0-alpha04")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.7.8")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.7.8")
}
