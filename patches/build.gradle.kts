rootProject.name = "gboard-patches"

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()

        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/MorpheApp/registry")
            credentials {
                username =
                    providers.gradleProperty("gpr.user").orNull
                        ?: System.getenv("GITHUB_ACTOR")
                password =
                    providers.gradleProperty("gpr.key").orNull
                        ?: System.getenv("GITHUB_TOKEN")
            }
        }

        maven {
            url = uri("https://jitpack.io")
        }
    }
}

plugins {
    id("app.morphe.patches") version "1.5.1" apply false
}

include(":patches")
include(":extensions:extension")            """.trimIndent()
        )
    }
}

val generatePreviewAssetsIndex by tasks.registering {
    val sourceDir = previewAssetsSourceDir
    val outputFile = generatedPreviewAssetsResourcesDir.map { directory ->
        directory.file("settings-previews/index.txt")
    }

    inputs.dir(sourceDir)
    outputs.file(outputFile)

    doLast {
        val sourceRoot = sourceDir.asFile
        if (!sourceRoot.exists()) {
            throw GradleException("Preview assets directory not found: $sourceRoot")
        }

        val indexedAssets = sourceRoot.walkTopDown()
            .filter { file -> file.isFile && file.name != "index.txt" }
            .map { file -> file.relativeTo(sourceRoot).invariantSeparatorsPath }
            .sorted()
            .toList()

        if (indexedAssets.isEmpty()) {
            throw GradleException("No preview assets found under $sourceRoot")
        }

        val output = outputFile.get().asFile
        output.parentFile.mkdirs()
        output.writeText(
            indexedAssets.joinToString(
                separator = System.lineSeparator(),
                postfix = System.lineSeparator()
            ),
            Charsets.UTF_8
        )
    }
}

val generateGboardVersionBindings by tasks.registering(GenerateTargetBindingsTask::class) {
    val outputFile = generatedVersionBindingsDir.map { directory ->
        directory.file(
            "dev/jason/gboardpatches/patches/gboard/shared/generated/GboardVersionBindings.kt"
        )
    }

    dependsOn(bindingCompilerSourceSet.classesTaskName)
    profileFile.set(versionBindingsProfile)
    compilerClasspath.from(bindingCompilerSourceSet.runtimeClasspath)
    this.outputFile.set(outputFile)
}

patches {
    about {
        name = "Gboard Patches"
        description = "Morphe patches for Gboard."
        source = "https://github.com/jasonwu1994/gboard-patches"
        author = "Jason Wu"
        contact = "https://github.com/jasonwu1994/gboard-patches/issues"
        website = "https://github.com/jasonwu1994/gboard-patches"
        license = "GPLv3"
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}

sourceSets.named("main") {
    java.srcDir(generatedPatchInfoDir)
    java.srcDir(generatedVersionBindingsDir)
    resources.srcDir(generatedPreviewAssetsResourcesDir)
}

dependencies {
    implementation(libs.gson)
    add(bindingCompilerSourceSet.implementationConfigurationName, libs.gson)
    add(patchMetadataSourceSet.implementationConfigurationName, libs.gson)
    testImplementation(libs.gson)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.ow2.asm:asm-analysis:9.7.1")
    testImplementation("org.ow2.asm:asm-tree:9.7.1")
}

tasks {
    named<Test>("test") {
        dependsOn(syncExtensionTask)
        inputs.file(rootProject.file("patches-list.json"))
        inputs.dir(runtimeAbiOutputDirectory)
            .withPathSensitivity(PathSensitivity.RELATIVE)
        inputs.dir(compiledPatchClasses)
            .withPathSensitivity(PathSensitivity.RELATIVE)
        doFirst {
            systemProperty(
                "gboard.runtimeAbiOutputDirectory",
                runtimeAbiOutputDirectory.get()
                    .relativeTo(projectDir)
                    .invariantSeparatorsPath,
            )
            systemProperty(
                "gboard.compiledPatchClasses",
                compiledPatchClasses.get().asFile
                    .relativeTo(projectDir)
                    .invariantSeparatorsPath,
            )
        }
    }

    named("compileKotlin") {
        dependsOn(generatePatchBuildInfo, generateGboardVersionBindings)
    }

    named("processResources") {
        dependsOn(generatePreviewAssetsIndex)
    }

    named("sourcesJar") {
        dependsOn(
            generatePatchBuildInfo,
            generateGboardVersionBindings,
            generatePreviewAssetsIndex
        )
    }

    register<JavaExec>("generatePatchesList") {
        description = "Build patch with patch list"

        dependsOn(build, patchMetadataSourceSet.classesTaskName)

        classpath = patchMetadataSourceSet.runtimeClasspath
        mainClass.set("dev.jason.gboardpatches.util.PatchListGeneratorKt")
    }

    register("normalizePatchMetadataEncoding") {
        description = "Ensures generated patch metadata JSON files are encoded as UTF-8 without BOM."

        doLast {
            listOf(
                rootProject.file("patches-bundle.json"),
                rootProject.file("patches-list.json"),
            ).forEach { jsonFile ->
                if (!jsonFile.exists()) {
                    return@forEach
                }

                val bytes = jsonFile.readBytes()
                val hasUtf8Bom =
                    bytes.size >= utf8Bom.size &&
                        utf8Bom.indices.all { index -> bytes[index] == utf8Bom[index] }

                if (hasUtf8Bom) {
                    jsonFile.writeBytes(bytes.copyOfRange(utf8Bom.size, bytes.size))
                }
            }
        }
    }

    named("generatePatchesList") {
        finalizedBy("normalizePatchMetadataEncoding")
    }
    // Used by gradle-semantic-release-plugin.
    publish {
        dependsOn("generatePatchesList")
    }
}
