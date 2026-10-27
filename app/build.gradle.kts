import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val distributeApk =
    providers.gradleProperty("distributeApk").map(String::toBooleanStrict).orElse(false).get()
val configuredAuthBaseUrl = providers.gradleProperty("authBaseUrl")
val apkVersionCode =
    providers
        .gradleProperty("apkVersionCode")
        .map { value ->
            value.toIntOrNull()?.takeIf { it in 1..2_100_000_000 }
                ?: error("apkVersionCode must be an integer between 1 and 2100000000.")
        }
        .orElse(1)
        .get()

if (distributeApk) {
    require(configuredAuthBaseUrl.isPresent) {
        "Distributed APKs require an explicit authBaseUrl reachable from physical devices."
    }
    require(providers.gradleProperty("apkVersionCode").isPresent && apkVersionCode > 1) {
        "Distributed APKs require an explicit incremental apkVersionCode greater than 1."
    }
}

val authBaseUrl =
    configuredAuthBaseUrl
        .orElse("http://10.0.2.2:8080/")
        .map { value ->
            val uri = URI(value.trim())
            require(uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank()) {
                "authBaseUrl must be an absolute HTTP or HTTPS URL."
            }
            require(uri.userInfo == null && uri.query == null && uri.fragment == null) {
                "authBaseUrl must not include credentials, a query or a fragment."
            }
            if (distributeApk) {
                val host = uri.host.lowercase()
                require(
                    host !in
                        setOf(
                            "10.0.2.2",
                            "10.0.3.2",
                            "localhost",
                            "0.0.0.0",
                            "[::1]",
                            "::1",
                            "192.168.1.x",
                        ) && !host.startsWith("127.") && !host.endsWith(".localhost"),
                ) {
                    "authBaseUrl must use a real LAN or published host, not an emulator or placeholder."
                }
            }
            uri.toASCIIString().let { if (it.endsWith("/")) it else "$it/" }
        }
        .get()

android {
    namespace = "com.clusterjam.mobile"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.clusterjam.mobile"
        minSdk = 24
        targetSdk = 37
        versionCode = apkVersionCode
        versionName = if (distributeApk) "1.0-test.$apkVersionCode" else "1.0"
        buildConfigField("String", "AUTH_BASE_URL", "\"$authBaseUrl\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    if (distributeApk) {
        signingConfigs {
            create("testing") {
                fun signingValue(name: String): String =
                    providers.environmentVariable(name).orNull?.takeIf { it.isNotBlank() }
                        ?: error("Missing testing signing environment variable: $name")

                storeFile = file(signingValue("CLUSTERJAM_KEYSTORE_PATH"))
                require(storeFile?.isFile == true) { "Testing keystore file does not exist." }
                storePassword = signingValue("CLUSTERJAM_KEYSTORE_PASSWORD")
                keyAlias = signingValue("CLUSTERJAM_KEY_ALIAS")
                keyPassword = signingValue("CLUSTERJAM_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            if (distributeApk) signingConfig = signingConfigs.getByName("testing")
        }
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.okhttp.mockwebserver)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
}
