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
    api(project(":core"))

    // Kotlin types appear in the public API, so the stdlib must reach consumers.
    api(kotlin("stdlib"))

    // Provided by the host robot app at runtime. Compile against the same
    // version the consuming Quickstart uses; never use implementation here.
    compileOnly("org.firstinspires.ftc:RobotCore:12.0.0")
    compileOnly("org.firstinspires.ftc:Hardware:12.0.0")
    compileOnly("org.firstinspires.ftc:FtcCommon:12.0.0")

    // Optional: uncomment if you wire up FTC Dashboard graphing in the OpModes.
    // compileOnly("com.acmerobotics.dashboard:dashboard:0.4.16")
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