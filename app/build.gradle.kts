import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.ksp)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover)
}

// Release signing is read from keystore.properties (gitignored). Copy
// keystore.properties.example to keystore.properties and fill it in to produce
// a signed release build; without it, release builds are left unsigned.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

// A missing or misspelled key used to throw a bare NullPointerException from
// the unchecked `as String` casts below, at configuration time, with no hint
// which key was the problem. Name it instead.
fun keystoreProp(key: String): String = keystoreProperties[key] as? String
    ?: throw GradleException(
        "keystore.properties is missing '$key' (see keystore.properties.example)"
    )

android {
    namespace = "com.flowpay.app"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "com.flowpay.app"
        minSdk = 29
        targetSdk = 35
        versionCode = 6
        versionName = "1.0.1"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = file(keystoreProp("storeFile"))
                storePassword = keystoreProp("storePassword")
                keyAlias = keystoreProp("keyAlias")
                keyPassword = keystoreProp("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            ndk {
                abiFilters += listOf("arm64-v8a")
            }
        }
        debug {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            ndk {
                abiFilters += listOf("arm64-v8a")
            }
            isDebuggable = true
            buildConfigField("boolean", "DEBUG_LOGS", "true")
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
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    lint {
        disable += "NullSafeMutableLiveData"
        disable += "GradleDependency"
        disable += "AndroidGradlePluginVersion"
        error += "HardcodedText"
        error += "SetTextI18n"
        baseline = file("lint-baseline.xml")
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

tasks.matching { it.name == "packageRelease" }.configureEach {
    doFirst {
        if (!keystorePropertiesFile.exists() && !project.hasProperty("allowUnsigned")) {
            throw GradleException(
                "Release build would be UNSIGNED and uninstallable: keystore.properties is missing. " +
                    "Copy keystore.properties.example and fill it in to sign the release, or pass " +
                    "-PallowUnsigned to build an intentionally unsigned APK."
            )
        }
    }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("config/detekt/detekt.yml"))
    baseline = file("detekt-baseline.xml")
}

kover {
    reports {
        filters {
            includes {
                packages(
                    "com.flowpay.app.payment",
                    "com.flowpay.app.payment.sms",
                    "com.flowpay.app.telephony"
                )
                classes("com.flowpay.app.helpers.TransactionDetector*")
            }
        }
        verify {
            rule {
                minBound(85)
            }
        }
    }
}

dependencies {
    detektPlugins(libs.detekt.formatting)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.constraintlayout)

    implementation(libs.androidx.localbroadcastmanager)

    implementation(libs.zxing.core)

    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.camerax.core)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation("net.zetetic:sqlcipher-android:${libs.versions.sqlcipher.get()}@aar")
    implementation(libs.androidx.sqlite)

    implementation(libs.material)
    implementation(libs.androidx.cardview)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
}
