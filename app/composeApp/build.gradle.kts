import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Compose Multiplatform's resource accessors (Res.drawable.*, Res.font.*) are generated
// under this package rather than one derived from the (unset) Gradle project group.
compose.resources {
    packageOfResClass = "com.xavierclavel.cooknco.resources"
}

kotlin {
    android {
        namespace = "com.xavierclavel.cooknco.shared"
        compileSdk = 37
        minSdk = 24

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }

        // Runs commonTest on the JVM.
        withHostTest {}

        // Off by default for this plugin (com.android.kotlin.multiplatform.library) as of
        // AGP 9 — without it, Compose Multiplatform's generated resources (composeResources,
        // used by ui.theme.CookncoFont and the auth screens' Res.drawable.*) compile fine but
        // never get copied into :androidApp's assets, so painterResource()/Font() throw
        // MissingResourceException the moment anything using them actually renders. See
        // https://youtrack.jetbrains.com/issue/CMP-9547.
        androidResources {
            enable = true
        }
    }

    // Compose Multiplatform 1.11 no longer publishes iosX64 (Intel simulator).
    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            // `api` so :androidApp can host the Compose entry point.
            api(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.ui.tooling.preview)
            implementation(libs.compose.material.icons.extended)
            implementation(libs.compose.components.resources)

            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.androidx.navigation.compose)
            implementation(libs.androidx.datastore.preferences)
            implementation(libs.okio)

            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)

            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor3)
            implementation(libs.reorderable)
        }

        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.browser)
            // NotificationCompat, for the cook timer's own notification. Unlike a push, it is
            // drawn from here: nothing about it needs a manifest entry except the receiver
            // its buttons are addressed to.
            implementation(libs.androidx.core)
            implementation(libs.ktor.client.okhttp)
            // The recipe scanner: Google's document capture flow, and the text reader run
            // over what it captured. Both unbundled, so the APK carries neither the scanning
            // UI nor the recognition model — Play services delivers them on first use, which
            // this app can depend on because Firebase already does.
            implementation(libs.mlkit.document.scanner)
            implementation(libs.mlkit.text.recognition)
            // Only for the push token: the notification itself is drawn by :androidApp,
            // which is where the service that receives one has to be declared.
            implementation(project.dependencies.platform(libs.firebase.bom))
            implementation(libs.firebase.messaging)
        }

        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
    }
}
