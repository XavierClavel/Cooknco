import java.util.Properties

plugins {
    id("io.ktor.plugin") version "3.2.3"
}

application {
    mainClass.set("com.xavierclavel.ApplicationKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

dependencies {
    val ktorVersion = "3.2.3"
    val itextVersion = "8.0.5"
    val hopliteVersion = "2.9.0"

    implementation(project(":shared"))

    //Ktor client
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")

    implementation("org.webjars:jquery:3.2.1")
    implementation("io.github.smiley4:ktor-swagger-ui:4.0.0")

    //Secrets
    implementation("io.github.cdimascio:dotenv-kotlin:6.5.0")

    //Configuration
    implementation("com.sksamuel.hoplite:hoplite-core:$hopliteVersion")
    implementation("com.sksamuel.hoplite:hoplite-yaml:$hopliteVersion")

    //Encryption -> bcrypt
    implementation("at.favre.lib:bcrypt:0.10.2")

    //Webp support
    implementation("org.sejda.imageio:webp-imageio:0.1.6")

    //Images metadata parsing
    implementation("com.drewnoakes:metadata-extractor:2.19.0")

    //
    implementation("net.coobird:thumbnailator:0.4.20")

    //Document layouts: logic-less templating, so a layout an operator saved stays markup
    implementation("com.samskivert:jmustache:1.16")

    //PDF reading, to assert on what an export actually contains. The writing is Chromium's,
    //in its own service: see GotenbergPdfRenderer.
    testImplementation("com.itextpdf:itext-core:$itextVersion")
    testImplementation("com.itextpdf:bouncy-castle-adapter:$itextVersion")

    //Mocking
    testImplementation("io.mockk:mockk:1.13.16")

    //Job scheduling
    implementation("dev.inmo:krontab:2.7.1")

    //MCP server: the /mcp endpoint speaks Model Context Protocol over Streamable HTTP.
    //Held at the last release built against this build's Ktor and Kotlin (3.2.3 / 2.2.21) — every
    //release after it compiles here but fails at runtime, reaching for Ktor internals that moved
    //(0.15.0: "tried to access private field io.ktor.http.HttpMethod.Post"). Moving up means
    //moving Ktor with it; McpControllerTest is what would catch the mismatch again.
    implementation("io.modelcontextprotocol:kotlin-sdk-server:0.10.0")

    //Redis for session storage
    implementation("io.lettuce:lettuce-core:6.5.3.RELEASE")

    //Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactive:1.10.1")
}

ktor {
    docker {
        localImageName.set("cooknco/backend")
        imageTag.set(rootProject.version.toString())
    }
}



/**
 * Regenerates the next dbmigration SQL + model xml from the current entity classes,
 * by diffing them against src/main/resources/dbmigration/model. Run after changing
 * an @Entity: `./gradlew :backend:generateDbMigration`.
 */
tasks.register<JavaExec>("generateDbMigration") {
    group = "ebean"
    description = "Generate the DDL for the next DB migration from the current entities"
    mainClass.set("main.GenerateMigrationKt")
    classpath = sourceSets["test"].runtimeClasspath
    workingDir = rootProject.projectDir
}

val generatedVersionDir = "${buildDir}/generated-version"

sourceSets {
    named("main") {
        output.dir(mapOf("builtBy" to "generateVersionProperties"), generatedVersionDir)
    }
}

tasks.register("generateVersionProperties") {
    doLast {
        val propertiesFile = file("$generatedVersionDir/version.properties")
        propertiesFile.parentFile.mkdirs()
        val properties = Properties()
        properties.setProperty("version", rootProject.version.toString())
        propertiesFile.writer().use { properties.store(it, null) }
    }
}

tasks.named("processResources") {
    dependsOn("generateVersionProperties")
}

tasks.shadowJar {
    archiveBaseName.set("cooknco")
    archiveClassifier.set("")
    manifest {
        attributes(
            "Main-Class" to "com.xavierclavel.ApplicationKt"
        )
    }
}