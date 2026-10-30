import com.android.build.api.variant.BuildConfigField
import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val distributeApk =
    providers.gradleProperty("distributeApk").map(String::toBooleanStrict).orElse(false).get()
val configuredAuthBaseUrl = providers.gradleProperty("authBaseUrl")
val configuredProfileBaseUrl = providers.gradleProperty("profileBaseUrl")
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

fun serviceBaseUrl(value: String, propertyName: String): String {
    val uri = URI(value.trim())
    require(uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank()) {
        "$propertyName must be an absolute HTTP or HTTPS URL."
    }
    require(uri.userInfo == null && uri.query == null && uri.fragment == null) {
        "$propertyName must not include credentials, a query or a fragment."
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
            "$propertyName must use a real LAN or published host, not an emulator or placeholder."
        }
    }
    return uri.toASCIIString().let { if (it.endsWith("/")) it else "$it/" }
}

val authBaseUrl =
    serviceBaseUrl(
        configuredAuthBaseUrl.orElse("http://10.0.2.2:8080/").get(),
        "authBaseUrl",
    )
val profileBaseUrl =
    serviceBaseUrl(
        configuredProfileBaseUrl.orElse(authBaseUrl).get(),
        "profileBaseUrl",
    )

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
        buildConfigField("String", "PROFILE_BASE_URL", "\"$profileBaseUrl\"")

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

androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        requireNotNull(variant.buildConfigFields)
            .put(
                "AUTH_BASE_URL",
                configuredAuthBaseUrl.orElse("").map { explicit ->
                    require(explicit.isNotBlank() && URI(explicit.trim()).scheme == "https") {
                        "Release requires an explicit HTTPS authBaseUrl."
                    }
                    BuildConfigField(
                        "String",
                        "\"$authBaseUrl\"",
                        "Explicit HTTPS backend for release",
                    )
                },
            )
        requireNotNull(variant.buildConfigFields)
            .put(
                "PROFILE_BASE_URL",
                configuredProfileBaseUrl.orElse(configuredAuthBaseUrl).orElse("").map { explicit ->
                    require(explicit.isNotBlank() && URI(explicit.trim()).scheme == "https") {
                        "Release requires an explicit HTTPS profileBaseUrl or authBaseUrl."
                    }
                    BuildConfigField(
                        "String",
                        "\"$profileBaseUrl\"",
                        "Explicit HTTPS Profile backend or shared gateway",
                    )
                },
            )
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
