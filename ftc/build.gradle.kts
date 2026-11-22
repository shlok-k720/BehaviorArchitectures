import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library")
    kotlin("android")
    `maven-publish`
}

android {
    namespace = "com.rmsftc.ftc"
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

    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_1_8
        // FTC SDK 12.0.0 and Sloth/Sinister are compiled with Kotlin 2.4
        // (metadata 2.4.0); this project's compiler is 2.1.20, which reads
        // metadata up to 2.2.0. Without this flag those classes are
        // "incompatible" and the FIR checker crashes reporting them
        // ("source must not be null" in FirIncompatibleClassTypeChecker).
        freeCompilerArgs.add("-Xskip-metadata-version-check")
    }
}

dependencies {
    // The core types are part of this module's public API.
    api(project(":fsm"))
    api(project(":cmdlib"))
    api(project(":behaviorTrees"))
    api(libs.ftcutils.ftc)

    // Kotlin types appear in the public API, so the stdlib must reach consumers.
    api(kotlin("stdlib"))

    // Provided by the host robot app at runtime. Compile against the same
    // version the consuming Quickstart uses; never use implementation here.
    compileOnly(libs.bundles.ftc)
    testCompileOnly(libs.bundles.ftc)

    // FTCUtils' published classes reference these in their signatures
    // (DaemonManager.DaemonFinder implements Sinister's Scanner; core's
    // Pose/Vector/DriveVector extend Pedro types) but FTCUtils declares
    // them runtime/compileOnly, so they are not on this module's compile
    // classpath by default. Without them the Kotlin FIR checker crashes
    // ("source must not be null") on the unresolvable supertypes.
    compileOnly(libs.sloth)
    compileOnly(libs.pedropathing.core)

    // Optional: uncomment if you wire up FTC Dashboard graphing in the OpModes.
    // compileOnly("com.acmerobotics.dashboard:dashboard:0.4.16")

    // JUnit 4 for the local unit tests (the template ExampleUnitTest uses
    // org.junit.Test / org.junit.Assert).
    testImplementation(libs.junit)
    // JUnit 5 for the moved builder tests and the converter/manager tests.
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.platform.suite)
    // Lets the JUnit 4 tests keep running under the JUnit Platform.
    testImplementation(libs.junit.vintage.engine)
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