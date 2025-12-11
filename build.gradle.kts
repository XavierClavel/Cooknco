allprojects {
    version = "1.2.4"
    group = "eu.cooknco"
}

plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("kapt") version "2.2.21"
    kotlin("plugin.serialization") version "2.2.21"
    id("io.ebean") version "17.0.1"
}

val junitVersion = "5.10.2"
val testcontainersVersion = "1.20.1"
val ebeanVersion = "17.0.1"
val ktorVersion = "3.2.3"
val koinVersion = "4.0.0"
val kotlin_version: String by project
val logback_version: String by project

subprojects {
    if (name == "frontend") return@subprojects
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jetbrains.kotlin.plugin.serialization")

    if (name != "shared") {
        apply(plugin = "org.jetbrains.kotlin.kapt")
        apply(plugin = "io.ebean")

        ebean {
            debugLevel = 1
            queryBeans = true
        }
    }

    dependencies {

        //Tests
        testImplementation(kotlin("test"))
        testImplementation("org.testcontainers:junit-jupiter:$testcontainersVersion")
        testImplementation("org.testcontainers:testcontainers:${testcontainersVersion}")
        testImplementation("org.testcontainers:postgresql:${testcontainersVersion}")

        //Server -> Ktor
        implementation("io.ktor:ktor-server-core-jvm:${ktorVersion}")
        implementation("io.ktor:ktor-server-webjars-jvm:${ktorVersion}")
        implementation("io.ktor:ktor-server-cors:${ktorVersion}")
        implementation("io.ktor:ktor-server-content-negotiation-jvm:${ktorVersion}")
        implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:${ktorVersion}")
        implementation("io.ktor:ktor-server-netty-jvm:${ktorVersion}")
        implementation("io.ktor:ktor-server-sessions:${ktorVersion}")
        implementation("io.ktor:ktor-server-auth:${ktorVersion}")
        implementation("io.ktor:ktor-server-status-pages:${ktorVersion}")
        implementation("io.ktor:ktor-server-sse:${ktorVersion}")
        testImplementation("io.ktor:ktor-server-test-host-jvm:${ktorVersion}")

        //DB -> Ebean
        implementation("org.hibernate:hibernate-core:6.6.1.Final")
        implementation("org.postgresql:postgresql:42.7.4")
        implementation("com.zaxxer:HikariCP:6.0.0")
        implementation("org.flywaydb:flyway-core:10.20.0")
        implementation("io.ebean:ebean:$ebeanVersion")
        implementation("io.ebean:ebean-platform-postgres:$ebeanVersion")
        implementation("io.ebean:ebean-ddl-generator:$ebeanVersion")
        implementation("io.ebean:ebean-migration:14.2.0")
        testImplementation("io.ebean:ebean-test:$ebeanVersion")
        testImplementation("io.ebean:ebean:$ebeanVersion")

        if (name != "shared") {
            kapt("io.ebean:querybean-generator:$ebeanVersion")
        }

        //Dependency injection -> Koin
        implementation("io.insert-koin:koin-ktor:${koinVersion}")
        implementation("io.insert-koin:koin-test-jvm:${koinVersion}")

        //Logging
        implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
        implementation("ch.qos.logback:logback-classic:${logback_version}")
    }

    tasks.test {
        useJUnitPlatform()
    }
}

tasks.register("printVersion") {
    doLast {
        println(project.version)
    }
}