import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

val local = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun secret(name: String): String? = local.getProperty(name) ?: System.getenv(name)

android {
    namespace = "io.github.kasecrab.razorback"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.kasecrab.razorback"
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    val storeFile = secret("RAZORBACK_STORE_FILE")
    if (storeFile != null) {
        signingConfigs {
            create("release") {
                this.storeFile = file(storeFile)
                storePassword = secret("RAZORBACK_STORE_PASSWORD")
                keyAlias = secret("RAZORBACK_KEY_ALIAS")
                keyPassword = secret("RAZORBACK_KEY_PASSWORD")
            }
        }
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (storeFile != null) signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += listOf(
            "kotlin/**",
            "META-INF/*.version",
            "META-INF/*.kotlin_module",
            "META-INF/versions/**",
            "META-INF/LICENSE*",
            "META-INF/NOTICE*",
            "DebugProbesKt.bin",
        )
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    lint {
        abortOnError = false
        checkDependencies = false
    }
}

dependencies {
    implementation(libs.coroutines.android)
    testImplementation(libs.junit)
    testImplementation(libs.orgjson)
}
