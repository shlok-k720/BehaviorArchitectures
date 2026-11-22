import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `java-library`
    kotlin("jvm")
    `maven-publish`
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
    withSourcesJar()
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_1_8
    }
}

dependencies {
    // Kotlin types appear in the public API (Case, StateMachine), so the stdlib
    // must be visible to consumers too. kotlin("stdlib") follows the plugin version.
    api(libs.ftcutils.core)

    api(kotlin("stdlib"))

    testImplementation(libs.junit)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.platform.suite)
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("release") {
            groupId = project.group.toString()
            artifactId = "fsm"
            version = project.version.toString()

            afterEvaluate {
                from(components["java"])
            }
        }
    }
}