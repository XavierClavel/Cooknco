import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

/**
 * A configuration value, looked up in `local.properties` first and then in the environment.
 *
 * Two sources because there are two callers: a workstation, where these live in
 * `local.properties` (never committed), and CI, which only has environment variables fed from
 * the GitHub secrets. `local.properties` wins so that a variable left exported in a shell
 * cannot silently override the file.
 *
 * `providers.environmentVariable` rather than `System.getenv`: the variable becomes a declared
 * configuration-cache input, so the cache is invalidated when it changes instead of serving a
 * build configured against the old value.
 */
fun configValue(key: String, default: String = ""): String =
    localProperties.getProperty(key)
        ?: providers.environmentVariable(key).orNull
        ?: default

/**
 * The Play upload keystore, or `null` when it is not available here.
 *
 * The file is **never** committed: on a workstation it is pointed at by `KEYSTORE_FILE` in
 * `local.properties`, and in CI it is rebuilt from the `ANDROID_KEYSTORE_BASE64` secret before
 * Gradle is called. A relative path resolves against `app/`.
 */
val uploadKeystore: java.io.File? = configValue("KEYSTORE_FILE")
    .takeIf { it.isNotBlank() }
    ?.let { rootProject.file(it) }
    ?.takeIf { it.isFile }

/**
 * Version major. **The only version value still written by hand here**, and it only moves for a
 * product milestone — everything else is derived from the git history just below.
 *
 * Unrelated to the backend's `version` in the repository root `build.gradle.kts`: that one tags
 * Docker images, this one is what the Play Console shows.
 */
val versionMajor = 1

/**
 * Number of commits reachable from `HEAD`, which **is** the `versionCode`.
 *
 * Neither a committed literal nor a CI counter. Google Play refuses a `versionCode` it has
 * already seen, for ever, so the counter must never go back and never reset:
 * `github.run_number` restarts at 1 the moment a workflow is renamed, and a literal in this
 * file needed a bot commit on every push, rewritten with `sed` and read back with `grep`. The
 * commit count is a property of the commit — not of the branch, not of the run, not of the
 * order merges happened in. `develop` and `master` derive the same value at the same commit,
 * which is what the `internal` to `alpha` promotion depends on, since it recompiles nothing.
 *
 * Counted over the whole monorepo rather than over `app/` alone: a backend-only commit moves
 * the number too. That is deliberate — the number only has to be monotonic, and one shared
 * with the repository is one that nothing about the app can reset.
 *
 * `providers.exec` rather than `System.getProcess...`: the result becomes a declared
 * configuration-cache input, re-executed on each build to invalidate it, so a local commit
 * changes the number without anyone having to clear the cache.
 *
 * A **shallow** clone (`fetch-depth: 1`, the `actions/checkout` default) only counts the
 * commits it carries, which is a number far below the last published one. Nothing can detect
 * that here — a full clone costs nothing locally — hence the monotonicity guard in
 * `android-deploy.yml`, which compares this number against the `android-build-*` tags before
 * anything is compiled.
 */
val gitCommitCount: Int = runCatching {
    providers.exec {
        commandLine("git", "rev-list", "--count", "HEAD")
        // Without this, a repository with no commits (a bare `git init`) would fail
        // configuration instead of falling back.
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim().toIntOrNull()
}.getOrNull() ?: 1.also {
    // Not a failure: a source archive with no `.git` has to stay debug-buildable. What must
    // not pass is a *release* at this number — the CI guard takes care of that.
    logger.warn("versionCode: `git rev-list` unavailable, falling back to $it. Do not publish this build.")
}

android {
    namespace = "com.xavierclavel.cooknco"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.xavierclavel.cooknco"
        minSdk = 24
        targetSdk = 36
        // Derived, never committed: `versionCode` is the commit count and `versionName`
        // composes it with the major. See `gitCommitCount` above for why.
        //
        // Consequence worth knowing: `versionName` **changes on every commit**, so it carries
        // no semantic meaning beyond the major. That is the price of zero bot commits and of a
        // number no two branches can disagree on.
        //
        // CI does not grep these lines — it asks Gradle (`:androidApp:printVersion`). No
        // format has to be preserved here.
        versionCode = gitCommitCount
        versionName = "$versionMajor.$gitCommitCount"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // Declared only where the keystore is: otherwise an `assembleRelease` on a machine
        // without the key would fail at configuration time, when an unsigned release APK is
        // still useful for checking size or minification.
        if (uploadKeystore != null) {
            create("release") {
                storeFile = uploadKeystore
                storePassword = configValue("KEYSTORE_PASSWORD")
                keyAlias = configValue("KEY_ALIAS")
                keyPassword = configValue("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // `findByName` and not `getByName`: the config above only exists where the keystore
            // does, and a null `signingConfig` is exactly what the `jarsigner -verify` step in
            // `android-deploy.yml` is there to catch — an unsigned bundle otherwise leaves the
            // build with no error at all.
            signingConfig = signingConfigs.findByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":composeApp"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)

    // Push notifications. The service that receives one, and the code that draws it, have
    // to live in the application module — a manifest entry cannot come from a KMP library.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.androidx.core)

    debugImplementation(platform(libs.androidx.compose.bom))
    debugImplementation(libs.androidx.compose.ui.tooling)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

/**
 * Prints `<versionName> <versionCode>`, which is how CI learns what it is about to publish.
 *
 * Replaces grepping the literals out of this file: there are no literals left to read, and —
 * more to the point — what CI tags and promotes is now what Gradle actually compiled, rather
 * than a second derivation that only hopes to agree.
 *
 * Values are captured at configuration time: `doLast` must not touch the project, the
 * configuration cache being on.
 */
tasks.register("printVersion") {
    group = "help"
    description = "Prints `versionName versionCode` of the Android bundle."
    val name = "$versionMajor.$gitCommitCount"
    val code = gitCommitCount
    doLast { println("$name $code") }
}
