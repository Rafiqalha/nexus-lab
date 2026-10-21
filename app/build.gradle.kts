plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "org.nexus.lab"
    compileSdk = 35
    testBuildType = "release"
    defaultConfig {
        applicationId = "org.nexus.lab"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1-preview-lab"
        ndk { abiFilters += "arm64-v8a" }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes { release { isMinifyEnabled = false; signingConfig = signingConfigs.getByName("debug") } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    packaging { jniLibs { useLegacyPackaging = true; keepDebugSymbols += "**/*.so" } }
}
dependencies {
    val camera = "1.4.2"
    implementation("androidx.camera:camera-camera2:$camera")
    implementation("androidx.camera:camera-lifecycle:$camera")
    implementation("androidx.camera:camera-view:$camera")
    implementation("androidx.activity:activity-ktx:1.10.1")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:rules:1.6.1")
}
val verifySdk by tasks.registering {
    doLast {
        check(file("src/main/assets/sdk-identity.json").isFile) { "Run tools/build_android.sh first" }
        for (lib in listOf("libnexus_sdk.so", "libnexus_lab.so")) check(file("src/main/jniLibs/arm64-v8a/$lib").isFile)
    }
}
tasks.named("preBuild") { dependsOn(verifySdk) }
