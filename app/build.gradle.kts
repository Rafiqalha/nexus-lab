import groovy.json.JsonSlurper
import java.io.File
import java.security.MessageDigest

plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

val nexusStaging = providers.gradleProperty("nexusStaging").orNull == "true"

fun java.io.File.sha256(): String = inputStream().use { stream ->
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(1024 * 1024)
    while (true) {
        val count = stream.read(buffer)
        if (count < 0) break
        digest.update(buffer, 0, count)
    }
    digest.digest().joinToString("") { "%02x".format(it) }
}

android {
    buildFeatures { buildConfig = true }
    namespace = "org.nexus.lab"
    compileSdk = 35
    testBuildType = "release"
    defaultConfig {
        applicationId = "org.nexus.lab"
        minSdk = 26
        targetSdk = 35
        buildConfigField("boolean", "NEXUS_STAGING", nexusStaging.toString())
        versionCode = 2
        versionName = "0.1-preview-lab"
        ndk { abiFilters += "arm64-v8a" }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    sourceSets { getByName("main") { if (nexusStaging) manifest.srcFile("src/staging/AndroidManifest.xml") } }
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
        val sourceIdentityFile = rootProject.file("SDK_IDENTITY.json")
        val stagedIdentityFile = file("src/main/assets/sdk-identity.json")
        check(sourceIdentityFile.isFile && stagedIdentityFile.isFile) { "Run tools/build_android.sh first" }
        val parser = JsonSlurper()
        val source = parser.parse(sourceIdentityFile) as Map<*, *>
        val staged = parser.parse(stagedIdentityFile) as Map<*, *>
        val identityKeys = listOf(
            "source_sha", "source_digest", "target", "staging_root_public_key", "profile",
            "abi", "package_directory", "exports", "sdk_sha256", "header_sha256",
            "package_manifest_sha256"
        )
        for (key in identityKeys) check(source[key] == staged[key]) { "Staged SDK identity differs at $key" }
        val expectedProfile = if (nexusStaging) "staging-enrollment" else "default-preview"
        check(source["profile"] == expectedProfile) { "Gradle profile does not match imported SDK" }

        val sdk = file("src/main/jniLibs/arm64-v8a/libnexus_sdk.so")
        val bridge = file("src/main/jniLibs/arm64-v8a/libnexus_lab.so")
        check(sdk.isFile && bridge.isFile) { "Run tools/build_android.sh first" }
        check(sdk.sha256() == source["sdk_sha256"]) { "Staged SDK library hash mismatch" }
        check(bridge.sha256() == staged["bridge_sha256"]) { "Staged JNI bridge hash mismatch" }

        val repositoryRoot = rootProject.projectDir.canonicalFile
        val packageDirectory = rootProject.file(source["package_directory"].toString()).canonicalFile
        check(packageDirectory.path.startsWith(repositoryRoot.path + File.separator)) {
            "SDK package must remain inside the immutable vendor directory"
        }
        val manifestFile = packageDirectory.resolve("MANIFEST.json")
        check(manifestFile.isFile && manifestFile.sha256() == source["package_manifest_sha256"]) {
            "SDK package manifest identity mismatch"
        }
        val manifest = parser.parse(manifestFile) as Map<*, *>
        check(manifest["source_commit"] == source["source_sha"] && manifest["source_dirty"] == false) {
            "SDK package source identity mismatch"
        }
        check(manifest["target"] == "aarch64-linux-android" && manifest["production_approved"] == false) {
            "SDK package target or production marker mismatch"
        }
        val features = (manifest["features"] as? List<*>)?.map { it.toString() }
        check(features == if (nexusStaging) listOf("staging-enrollment") else emptyList<String>()) {
            "SDK manifest feature profile mismatch"
        }
    }
}
tasks.named("preBuild") { dependsOn(verifySdk) }
