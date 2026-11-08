import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Signatur: keystore/spymon.jks + keystore/keystore.properties liegen NICHT im öffentlichen Repo
// (sonst könnte jeder eine „SpyMon-Update“-APK mit deinem Schlüssel signieren).
val ksProps = Properties().apply {
    val f = rootProject.file("keystore/keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val hasKey = rootProject.file("keystore/spymon.jks").exists() && ksProps.getProperty("storePassword") != null

android {
    namespace = "de.cappy.spymon"
    compileSdk = 35

    defaultConfig {
        applicationId = "de.cappy.spymon"
        minSdk = 29
        targetSdk = 34
        // >>> Bei jeder Änderung beide hochzählen (und CHANGELOG in Dialogs.kt ergänzen) <<<
        versionCode = 3
        versionName = "1.2"
    }

    signingConfigs {
        if (hasKey) create("spymon") {
            storeFile = rootProject.file("keystore/spymon.jks")
            storePassword = ksProps.getProperty("storePassword")
            keyAlias = ksProps.getProperty("keyAlias")
            keyPassword = ksProps.getProperty("keyPassword")
        }
    }

    buildTypes {
        getByName("debug") { if (hasKey) signingConfig = signingConfigs.getByName("spymon") }
        getByName("release") {
            isMinifyEnabled = false
            if (hasKey) signingConfig = signingConfigs.getByName("spymon")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
