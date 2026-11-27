plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.anywall.converter"
    compileSdk = 35
    ndkVersion = "27.0.12077973"

    defaultConfig {
        applicationId = "com.anywall.converter"
        minSdk = 26
        targetSdk = 35
        versionCode = 6
        versionName = "0.6"
        vectorDrawables {
            useSupportLibrary = true
        }
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
            }
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    signingConfigs {
        create("release") {
            val ksPath = System.getenv("ANYWALL_KEYSTORE_PATH")
            if (!ksPath.isNullOrBlank() && file(ksPath).exists()) {
                storeFile = file(ksPath)
                storePassword = System.getenv("ANYWALL_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANYWALL_KEY_ALIAS")
                keyPassword = System.getenv("ANYWALL_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // Tree-shaking con R8 (-dontobfuscate) para eliminar los +11,900 iconos no utilizados
            // de material-icons-extended preservando PDFBox, Mammoth, Commons Compress y Media3.
            isMinifyEnabled = true
            isShrinkResources = true
            val ksPath = System.getenv("ANYWALL_KEYSTORE_PATH")
            if (!ksPath.isNullOrBlank() && file(ksPath).exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/LICENSE.md",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/NOTICE.md",
                "META-INF/INDEX.LIST",
                "META-INF/*.kotlin_module",
                "META-INF/versions/**/module-info.class",
                "module-info.class",
                "org/bouncycastle/pqc/**"
            )
        }
    }
}

// Mantener compatibilidad con scripts/CI que buscan app-release.apk copiando el APK universal
// (y dejando disponibles también los APKs ligeros por arquitectura como app-arm64-v8a-release.apk).
tasks.configureEach {
    if (name == "packageRelease") {
        doLast {
            val outDir = layout.buildDirectory.dir("outputs/apk/release").get().asFile
            val universalSigned = outDir.resolve("app-universal-release.apk")
            val universalUnsigned = outDir.resolve("app-universal-release-unsigned.apk")
            when {
                universalSigned.exists() -> universalSigned.copyTo(outDir.resolve("app-release.apk"), overwrite = true)
                universalUnsigned.exists() -> universalUnsigned.copyTo(outDir.resolve("app-release-unsigned.apk"), overwrite = true)
            }
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

dependencies {
    // Core + Compose
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Base de datos y trabajos en segundo plano
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime.ktx)

    // Imágenes / SVG / QR
    implementation(libs.androidsvg)
    implementation(libs.zxing.core)

    // Documentos / Office
    implementation(libs.pdfbox.android)
    implementation(libs.mammoth)

    // Audio / Video
    implementation(libs.media3.transformer)
    implementation(libs.media3.effect)
    implementation(libs.media3.common)
    implementation(libs.media3.muxer)
    implementation(libs.jump3r)

    // Archivos comprimidos
    implementation(libs.commons.compress)
    implementation(libs.xz)

    testImplementation(libs.junit)
}
