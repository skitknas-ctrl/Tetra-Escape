import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "io.github.skitknasctrl.tetraescape"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.skitknasctrl.tetraescape"
        minSdk = 26
        targetSdk = 35
        // CI passes -PbuildNumber=<run number>; test builds are then versioned 0.1.0-test.<n>
        val baseVersion = "0.1.0"
        val buildNumber = (project.findProperty("buildNumber") as String?)?.toIntOrNull()
        versionCode = buildNumber ?: 1
        versionName = if (buildNumber != null) "$baseVersion-test.$buildNumber" else "$baseVersion-dev"
    }

    signingConfigs {
        // Test-only key so that every CI build can update the previous one in place.
        // Release builds are signed with a key supplied via GitHub secrets (see build.yml).
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        create("release") {
            val ksPath = System.getenv("RELEASE_KEYSTORE")
            if (ksPath != null && file(ksPath).exists()) {
                storeFile = file(ksPath)
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug" // test app installs alongside the real release
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            val rel = signingConfigs.getByName("release")
            signingConfig = if (rel.storeFile != null) rel else null
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    sourceSets {
        getByName("main").kotlin.srcDirs("src/main/kotlin")
        getByName("test").kotlin.srcDirs("src/test/kotlin")
    }
    lint {
        abortOnError = false
    }
    dependenciesInfo {
        // F-Droid: no Google-signed dependency metadata block in the APK
        includeInApk = false
        includeInBundle = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.core:core-ktx:1.13.1")
    testImplementation("junit:junit:4.13.2")
}
