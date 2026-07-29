import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Version is derived from the release tag in CI (passed via -PappVersionName=vX.Y.Z or the
// APP_VERSION_NAME env var). Local builds fall back to the dev version below.
val fallbackVersionName = "1.0.1"

fun resolveVersionName(): String {
    val provided = (project.findProperty("appVersionName") as String?)
        ?: System.getenv("APP_VERSION_NAME")
    return provided?.trim()?.removePrefix("v")?.takeIf { it.isNotEmpty() } ?: fallbackVersionName
}

// Maps a semver name (e.g. "1.2.3") to a monotonically increasing integer (10203).
fun resolveVersionCode(versionName: String): Int {
    val core = versionName.substringBefore("-")
    val parts = core.split(".")
    val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
    val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
    return major * 10000 + minor * 100 + patch
}

val appVersionName = resolveVersionName()
val appVersionCode = resolveVersionCode(appVersionName)
val apkBuildDate = SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(Date())
val localPropertiesFile = rootProject.file("local.properties")
val localProperties = Properties().apply {
    if (localPropertiesFile.isFile) {
        localPropertiesFile.inputStream().use(::load)
    }
}
val keystorePropertiesFile = rootProject.file("keystore.properties")
val hasLocusMimicKeystore = keystorePropertiesFile.isFile
val keystoreProperties = Properties().apply {
    if (hasLocusMimicKeystore) {
        keystorePropertiesFile.inputStream().use(::load)
    }
}

fun requiredSigningProperty(name: String): String =
    keystoreProperties.getProperty(name) ?: error("Missing signing property: $name")

android {
    namespace = "com.locusmimic.app"
    compileSdk = 36

    signingConfigs {
        if (hasLocusMimicKeystore) {
            create("locusMimic") {
                storeFile = rootProject.file(requiredSigningProperty("storeFile"))
                storePassword = requiredSigningProperty("storePassword")
                keyAlias = requiredSigningProperty("keyAlias")
                keyPassword = requiredSigningProperty("keyPassword")
            }
        }
    }

    defaultConfig {
        applicationId = "com.locusmimic.app"
        minSdk = 30
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
        ndk {
            // Keep the universal APK installable on common physical devices and Android
            // emulators. The module currently has no bundled native library of its own.
            abiFilters += setOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
        buildConfigField(
            "String",
            "BAIDU_WEB_AK",
            "\"${localProperties.getProperty("BAIDU_WEB_AK", "")}\""
        )
    }

    buildTypes {
        debug {
            if (hasLocusMimicKeystore) {
                signingConfig = signingConfigs["locusMimic"]
            }
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (hasLocusMimicKeystore) {
                signingConfigs["locusMimic"]
            } else {
                signingConfigs["debug"]
            }
        }

    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }


    packaging {
        resources {
            merges += "META-INF/xposed/*"
        }
    }

    kotlinOptions {
        jvmTarget = "21"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    applicationVariants.all {
        val debugSuffix = if (buildType.name == "debug") "-debug" else ""
        outputs.all {
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName =
                "LocusMimic-${versionName}${debugSuffix}-${apkBuildDate}.apk"
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.material3)
    implementation("androidx.compose.material:material")
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hiddenapibypass)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    compileOnly(libs.libxposed.api)
    implementation(libs.libxposed.service)
}
