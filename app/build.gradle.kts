import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// Release signing comes only from CI environment variables (never from files in the repo).
val keystorePath: String? = System.getenv("KEYSTORE_PATH")
val appVersionName = (findProperty("appVersionName") as String?) ?: "0.0.0-dev"
val appVersionCode = (findProperty("appVersionCode") as String?)?.toInt() ?: 1

android {
    namespace = "io.github.akhamiak.claudeusage"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.akhamiak.claudeusage"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
    }

    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystorePath != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures { buildConfig = true }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        // Security checks fail the build (spec §11.1).
        error += setOf(
            "HardcodedDebugMode", "AllowBackup", "TrustAllX509TrustManager", "BadHostnameVerifier",
            "SSLCertificateSocketFactoryCreateSocket", "SSLCertificateSocketFactoryGetInsecure",
            "PackagedPrivateKey", "SecureRandom", "GetInstance", "WorldReadableFiles",
            "WorldWriteableFiles", "UnspecifiedImmutableFlag", "MutableImplicitPendingIntent",
            "InsecureBaseConfiguration", "UnsafeIntentLaunch", "SetJavaScriptEnabled",
            "AuthLeak", "LogConditional",
        )
        disable += setOf("OldTargetApi", "GradleDependency", "AndroidGradlePluginVersion", "NewerVersionAvailable")
    }

    testOptions { unitTests.isReturnDefaultValues = true }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(libs.androidx.core)
    implementation(libs.androidx.work)
    testImplementation(libs.junit)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.json)
}
