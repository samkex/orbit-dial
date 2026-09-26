import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins { alias(libs.plugins.android.application) }

val appVersionName = "0.1"

// Release signing credentials. Never in the repo: keystore.properties is gitignored and the
// keystore itself lives outside the working tree (see README / keystore.properties storeFile).
// Resolution order, first hit wins:
//   1. keystore.properties at the project root
//   2. the environment (ORBIT_DIAL_STORE_FILE / _STORE_PASSWORD / _KEY_ALIAS / _KEY_PASSWORD)
// If neither is present the release signing config is simply not created, so a fresh clone still
// builds — assembleDebug signs with the debug key, assembleRelease produces an unsigned APK.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun credential(key: String, env: String): String? =
    (keystoreProps.getProperty(key) ?: System.getenv(env))?.takeIf { it.isNotBlank() }

val signStoreFile     = credential("storeFile",     "ORBIT_DIAL_STORE_FILE")
val signStorePassword = credential("storePassword", "ORBIT_DIAL_STORE_PASSWORD")
val signKeyAlias      = credential("keyAlias",      "ORBIT_DIAL_KEY_ALIAS")
val signKeyPassword   = credential("keyPassword",   "ORBIT_DIAL_KEY_PASSWORD")

val releaseSigningAvailable =
    signStoreFile != null && signStorePassword != null &&
    signKeyAlias != null && signKeyPassword != null &&
    file(signStoreFile!!).exists()

android {
    namespace  = "com.kexsam.orbitdial"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.kexsam.orbitdial"
        minSdk        = libs.versions.minSdk.get().toInt()
        targetSdk     = libs.versions.targetSdk.get().toInt()
        versionCode   = 1
        versionName   = appVersionName
    }

    signingConfigs {
        if (releaseSigningAvailable) {
            create("release") {
                storeFile     = file(signStoreFile!!)
                storePassword = signStorePassword
                keyAlias      = signKeyAlias
                keyPassword   = signKeyPassword
                // Signature schemes are left at AGP's defaults, which follow minSdk. At minSdk
                // 33 that means v3 only; v1/v2 exist for platforms this app never runs on.
            }
        }
    }

    buildTypes {
        debug   { isMinifyEnabled = false }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Output name. Gives glyph-orbit-dial-v0.1-release.apk and glyph-orbit-dial-v0.1-debug.apk,
// rather than the default app-release.apk, which says nothing once it is off this machine.
base { archivesName.set("glyph-orbit-dial-v$appVersionName") }

kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

dependencies {
    // Glyph Matrix SDK. Not in the repo: Nothing's EULA forbids redistribution. See libs/README.md.
    implementation(files("../libs/glyph-matrix-sdk-2.0.aar"))
}

if (!releaseSigningAvailable) {
    logger.lifecycle(
        "glyph-clock: no release signing credentials found (keystore.properties or " +
        "ORBIT_DIAL_* environment variables); assembleRelease will produce an unsigned APK."
    )
}
