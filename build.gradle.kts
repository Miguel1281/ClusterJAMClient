// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.spotless)
}

spotless {
    // Keep pre-existing files intact; enforce formatting as files are changed or added.
    ratchetFrom("f50da2e6f520db701560988a4e36eac02ba14d4c")
    kotlin {
        target("app/src/**/*.kt")
        ktfmt(libs.versions.ktfmt.get()).kotlinlangStyle().configure {
            it.setMaxWidth(100)
        }
    }
    kotlinGradle {
        target("*.gradle.kts", "app/*.gradle.kts")
        ktfmt(libs.versions.ktfmt.get()).kotlinlangStyle().configure {
            it.setMaxWidth(100)
        }
    }
}
