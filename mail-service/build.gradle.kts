
application {
    mainClass = "eu.cooknco.ApplicationKt"
}

plugins {
    id("io.ktor.plugin") version "3.2.3"
}

dependencies {
    implementation(project(":shared"))

    //Mail
    implementation("org.eclipse.angus:jakarta.mail:2.0.3")

}

/**
 * Regenerates the next dbmigration SQL + model xml from the current entity classes,
 * by diffing them against src/main/resources/dbmigration/model. Run after changing
 * an @Entity: `./gradlew :mail-service:generateDbMigration`.
 */
tasks.register<JavaExec>("generateDbMigration") {
    group = "ebean"
    description = "Generate the DDL for the next DB migration from the current entities"
    mainClass.set("GenerateMigrationKt")
    classpath = sourceSets["test"].runtimeClasspath
    workingDir = rootProject.projectDir
}