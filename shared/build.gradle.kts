import java.security.MessageDigest
import java.util.Random
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// IA offerte par SuperHealth : adresse, modèle et clé lus HORS du dépôt (~/.gradle/gradle.properties : superhealthAiUrl,
// superhealthAiModel, superhealthAiKey ; ou variables SUPERHEALTH_AI_URL / _MODEL / _KEY, secrets de la CI) et écrits
// masqués (XOR) dans une source générée : jamais en clair dans le dépôt ni dans l'app. Sans clé : option absente.
val aiGiftValues = listOf("url", "model", "key").associateWith { k ->
    ((findProperty("superhealthAi" + k.replaceFirstChar { it.uppercase() }) as String?)
        ?: System.getenv("SUPERHEALTH_AI_" + k.uppercase()) ?: "").trim()
}
val generateAiGift = tasks.register("generateAiGift") {
    val dir = layout.buildDirectory.dir("generated/sources/aigift")
    val values = aiGiftValues
    inputs.property("digest", MessageDigest.getInstance("SHA-256")
        .digest(values.values.joinToString("\n").toByteArray()).joinToString("") { "%02x".format(it) })
    outputs.dir(dir)
    doLast {
        val rnd = Random(values.values.joinToString("\n").hashCode() * 31L + 7)
        fun enc(v: String): String {
            val pad = v.indices.map { rnd.nextInt(0xFFFF) }
            val data = v.indices.map { v[it].code xor pad[it] }
            return "d(intArrayOf(${data.joinToString(", ")}), intArrayOf(${pad.joinToString(", ")}))"
        }
        val out = dir.get().file("app/superhealth/nutrition/data/AiGiftValues.kt").asFile
        out.parentFile.mkdirs()
        out.writeText("""package app.superhealth.nutrition.data

/** Généré à la compilation (tâche generateAiGift) : IA offerte, valeurs masquées. Ne pas modifier. */
internal object AiGiftValues {
    val url: String get() = ${enc(values.getValue("url"))}
    val model: String get() = ${enc(values.getValue("model"))}
    val key: String get() = ${enc(values.getValue("key"))}
    private fun d(a: IntArray, p: IntArray): String = CharArray(a.size) { (a[it] xor p[it]).toChar() }.concatToString()
}
""")
    }
}

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
        commonMain { kotlin.srcDir(generateAiGift) }
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

// Maquettes de thèmes (labo, hors app) : ./gradlew :shared:themelab -Pout=dossier
tasks.register<JavaExec>("themelab") {
    val main = kotlin.jvm("desktop").compilations.getByName("main")
    dependsOn(main.compileTaskProvider)
    classpath = files(main.output.allOutputs, main.runtimeDependencyFiles)
    mainClass.set("app.superhealth.lab.ThemeLabShotsKt")
    args = listOf(project.findProperty("out") as String? ?: "${rootDir}/themelab")
    systemProperty("superhealth.assets", "${rootDir}/assets")
    systemProperty("java.awt.headless", "true")
    jvmArgs("-Xmx3g")
}
