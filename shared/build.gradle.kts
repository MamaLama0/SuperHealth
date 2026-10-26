import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

kotlin {
    jvmToolchain(21)

    // Aperçu sur ordinateur (Linux) : même interface, fenêtre au format iPhone.
    jvm("desktop") {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_21) }
    }

    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
            linkerOpts("-lsqlite3")
        }
    }

    compilerOptions {
        freeCompilerArgs.addAll("-Xexpect-actual-classes")
        optIn.addAll(
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.foundation.ExperimentalFoundationApi",
            "androidx.compose.foundation.layout.ExperimentalLayoutApi",
            "androidx.compose.animation.ExperimentalAnimationApi",
            "kotlinx.coroutines.ExperimentalCoroutinesApi",
            "kotlin.time.ExperimentalTime",
            "kotlinx.cinterop.ExperimentalForeignApi",
            "kotlinx.cinterop.BetaInteropApi",
        )
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(libs.compose.material3)
            implementation(libs.compose.icons.extended)
            implementation(libs.navigation.compose)
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.lifecycle.runtime.compose)
            implementation(libs.coroutines.core)
            implementation(libs.serialization.json)
            implementation(libs.datetime)
            implementation(libs.kotlinx.io)
            implementation(libs.room.runtime)
            implementation(libs.sqlite.bundled)
            implementation(libs.ktor.core)
        }
        iosMain.dependencies {
            implementation(libs.ktor.darwin)
        }
        getByName("desktopTest") {
            dependencies { implementation(kotlin("test")) }
        }
        getByName("desktopMain") {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.coroutines.swing)
                implementation(libs.ktor.java)
            }
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    listOf("kspDesktop", "kspIosArm64", "kspIosSimulatorArm64").forEach { add(it, libs.room.compiler) }
}

compose.resources {
    publicResClass = false
    packageOfResClass = "app.superhealth.res"
    generateResClass = always
}

compose.desktop {
    application {
        mainClass = "app.superhealth.PreviewKt"
    }
}

// Captures au format iPhone sans fenêtre : ./gradlew :shared:screenshots -Pshots="out @onboarded home= ..."
tasks.register<JavaExec>("screenshots") {
    val main = kotlin.jvm("desktop").compilations.getByName("main")
    dependsOn(main.compileTaskProvider)
    classpath = files(main.output.allOutputs, main.runtimeDependencyFiles)
    mainClass.set("app.superhealth.ScreenshotsKt")
    args = (project.findProperty("shots") as String? ?: "${rootDir}/screenshots welcome=").split(" ")
    systemProperty("superhealth.data", project.findProperty("data") as String? ?: "${layout.buildDirectory.get()}/preview-data")
    systemProperty("superhealth.assets", "${rootDir}/assets")
    systemProperty("superhealth.lang", project.findProperty("lang") as String? ?: "fr")
    systemProperty("java.awt.headless", "true")
    jvmArgs("-Xmx3g")
}
