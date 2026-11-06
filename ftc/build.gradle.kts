import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library")
    kotlin("android")
    `maven-publish`
}

android {
    namespace = "com.rmsftc.ftcImpl"
    compileSdk = 30

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_1_8
    }
}

dependencies {
    // The core types are part of this module's public API.
    api(project(":fsm"))
    api(project(":cmdlib"))
    api(project(":behaviorTrees"))

    // Kotlin types appear in the public API, so the stdlib must reach consumers.
    api(kotlin("stdlib"))

    // Provided by the host robot app at runtime. Compile against the same
    // version the consuming Quickstart uses; never use implementation here.
    compileOnly(libs.bundles.ftc)

    // Optional: uncomment if you wire up FTC Dashboard graphing in the OpModes.
    // compileOnly("com.acmerobotics.dashboard:dashboard:0.4.16")

    // JUnit 4 for the local unit tests (the template ExampleUnitTest uses
    // org.junit.Test / org.junit.Assert).
    testImplementation(libs.junit)
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = project.group.toString()
            artifactId = "ftc"
            version = project.version.toString()

            afterEvaluate {
                from(components["release"])
            }
        }
    }
}