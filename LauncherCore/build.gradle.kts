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

        compilations["main"].cinterops {
            create("zstd") {
                defFile(
                    layout.buildDirectory
                        .file("generated/zstd/zstd.def")
                        .get()
                        .asFile
                )
            }

            create("hdiffpatch") {
                defFile(
                    layout.buildDirectory
                        .file("generated/hpatch/hpatch.def")
                        .get()
                        .asFile
                )
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

val zstdSource = rootProject.file("third_party/zstd")
val zstdLibDir = zstdSource.resolve("lib")
val hdiffSource = rootProject.file("third_party/HDiffPatch")
val bzip2Source = rootProject.file("third_party/bzip2")

val zstdInputTree = fileTree(zstdLibDir) {
    exclude("obj") // make 生成的中间对象文件目录
    exclude("libzstd.a") // 输出产物
    exclude("libzstd*.dylib") // make 生成的动态库
    exclude("libzstd.pc") // make 生成的 pkg-config 文件
}
val hdiffInputTree = fileTree(hdiffSource) {
    exclude(".git")
    exclude("libhdiffpatch.a") // 输出产物
    exclude("hdiffz", "hpatchz", "unit_test") // make 生成的可执行文件
    exclude("**/*.o") // make 生成的中间对象文件
}
// hdiffpatch 的 Makefile 会直接编译 third_party/bzip2 下的源码
val bzip2InputTree = fileTree(bzip2Source) {
    exclude("**/*.o")
}

tasks.register<Exec>("buildZstd") {
    // 任务描述
    group = "LibsBuild"
    description = "Builds a Zstd binary from Zstd"

    val libZstd = zstdLibDir.resolve("libzstd.a")

    inputs.dir(zstdInputTree)
    outputs.file(libZstd)

    // 任务步骤
    workingDir(zstdSource)
    environment("CFLAGS", "-O3 -arch arm64") // 只编译 arm64 架构
    commandLine(
        "make",
        "-j${Runtime.getRuntime().availableProcessors()}",
        "lib-release"
    )
}

tasks.register<Exec>("buildHDiffPatch") {
    group = "LibsBuild"
    description = "Builds a HDiffPatch library"

    val libHDiffPatch = hdiffSource.resolve("libhdiffpatch.a")

    inputs.dir(hdiffInputTree)
    inputs.dir(bzip2InputTree)
    outputs.file(libHDiffPatch)

    workingDir(hdiffSource)
    environment("CFLAGS", "-O3 -arch arm64")
    val args = listOf("LDEF=0", "LZMA=0", "ZSTD=0", "MD5=0", "XXH=0", "VCD=0", "BSD=0")
    commandLine(
        "make",
        "-j${Runtime.getRuntime().availableProcessors()}",
        *args.toTypedArray(),
    )
}

val generateZstdDef = tasks.register("generateZstdDef") {
    group = "native_libs_build"
    description = "Generates a Zstd definition"

    val output =
        layout.buildDirectory.file(
            "generated/zstd/zstd.def"
        )
    outputs.file(output)

    doLast {
        val file = output.get().asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            headers = ${zstdSource.resolve("lib/zstd.h")}
            
            package = zstd
            
            compilerOpts = -I${zstdSource.resolve("lib")}
            
            staticLibraries = libzstd.a
            
            libraryPaths = ${zstdSource.resolve("lib")}
            """.trimIndent()
        )
    }
}

val generateHPatchDef = tasks.register("generateHPatchDef") {
    group = "native_libs_build"
    description = "Generates a HPatch definition"

    val output =
        layout.buildDirectory.file(
            "generated/hpatch/hpatch.def"
        )
    outputs.file(output)

    doLast {
        val file = output.get().asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            headers = ${hdiffSource.resolve("libHDiffPatch/HPatch/patch.h")}
            
            package = hdiffpatch
            
            compilerOpts = -I${hdiffSource}
            
            staticLibraries = libhdiffpatch.a
            
            libraryPaths = $hdiffSource
            """.trimIndent()
        )
    }
}

tasks.matching {
    it.name.contains("cinterop")
}.configureEach {

    dependsOn(
        "buildZstd",
        "buildHDiffPatch",
        generateZstdDef,
        generateHPatchDef
    )
}