plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinxSerialization)
}

val wineCertResource = layout.projectDirectory.file("src/commonMain/resources/req")
val generatedWineCertSource = layout.buildDirectory.dir("generated/wineCertResource/commonMain/kotlin")

val generateWineCertResource = tasks.register("generateWineCertResource") {
    description = "Generate required certs into wine"

    inputs.file(wineCertResource)
    outputs.dir(generatedWineCertSource)

    doLast {
        val encoded = wineCertResource.asFile.readText().trim()
        require(encoded.isNotEmpty()) { "Wine certificate resource is empty." }
        require(encoded.length % 4 == 0) {
            "Wine certificate resource is not valid Base64: length must be a multiple of 4."
        }
        require(encoded.matches(Regex("[A-Za-z0-9+/]*={0,2}"))) {
            "Wine certificate resource contains invalid Base64 characters."
        }

        val outputFile = generatedWineCertSource.get().asFile.resolve(
            "org/mingora/launcher/wine/WineCertResource.kt"
        )
        outputFile.parentFile.mkdirs()
        outputFile.writeText(
            """
            package org.mingora.launcher.wine

            internal object WineCertResource {
                const val BASE64 = "$encoded"
            }
            """.trimIndent() + "\n"
        )
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    macosArm64 {
        binaries {
            framework {
                baseName = "LauncherCore"
            }
        }
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(generateWineCertResource)

            dependencies {
                implementation(project.dependencies.platform(libs.koin.bom))
                implementation(libs.koin.core)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx.json)
            }
        }
    }
}

dependencies {
    commonMainImplementation(libs.androidx.datastore)
    commonMainImplementation(libs.androidx.datastore.preference)

    "macosArm64MainImplementation"(libs.ktor.client.darwin)

    commonMainImplementation(libs.kotlinx.json)
    commonMainImplementation(libs.kotlinx.protobuf)
    commonMainImplementation(libs.kotlinx.coruntine)

    commonMainImplementation(libs.filekit.core)
    commonMainImplementation(libs.cryptography.core)
    commonMainImplementation(libs.cryptography.provider.optimal)
}