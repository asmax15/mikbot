import dev.schlaubi.mikbot.plugins.gradle.publishing.MakeRepositoryIndexTask
import dev.schlaubi.mikbot.plugins.gradle.publishing.PluginExtension
import dev.schlaubi.mikbot.plugins.gradle.publishing.pluginExtensionName
import java.nio.file.Path

plugins {
    id("com.google.devtools.ksp") // used for plugin-processor
    kotlin("jvm")
    //kotlin("kapt") // used for pf4j processor (currently self-made with ksp)
}

extensions.create<PluginExtension>(pluginExtensionName)

// There might be a better way of doing this, but for now I can't be bothered figuring it out
val pluginMainFile: Path = buildDir
    .resolve("generated")
    .resolve("ksp")
    .resolve("main")
    .resolve("resources")
    .resolve("META-INF")
    .resolve("MANIFEST.MF")
    .toPath()

val plugin: Configuration by configurations.creating
val optionalPlugin: Configuration by configurations.creating

configurations {
    compileOnly {
        extendsFrom(plugin)
        extendsFrom(optionalPlugin)
    }
}

dependencies {
    compileOnly(kotlin("stdlib-jdk8")) // this one is included in the bot itself
    compileOnly(project(":api"))
    ksp(project(":plugin-processor"))
}

tasks {
    val patchProperties = task<PatchPropertiesTask>("patchPluginProperties") {
        dependsOn("kspKotlin")
        propertiesFile.set(
            buildDir.resolve("generated")
                .resolve("ksp")
                .resolve("main")
                .resolve("resources")
                .resolve("META-INF")
                .resolve("plugin.properties")
                .toPath()
        )
    }

    jar {
        dependsOn(patchProperties)
    }

    // Taken from: https://github.com/twatzl/pf4j-kotlin-demo/blob/master/plugins/build.gradle.kts#L20-L35
    val archive = register<Jar>("assemblePlugin") {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        dependsOn(jar)

        destinationDirectory.set(buildDir.resolve("plugin"))
        archiveBaseName.set("plugin-${project.name}")

        // first taking the classes generated by the jar task
        into("classes") {
            with(jar.get())
        }

        // and then we also need to include any libraries that are needed by the plugin
        dependsOn(configurations.runtimeClasspath)
        into("lib") {
            from({
                val mainConfiguration = project(":").configurations["runtimeClasspath"].files.map { it.removeVersion() }

                // filter out dupe dependencies
                configurations.runtimeClasspath.get().files.filter {
                    it.removeVersion() !in mainConfiguration
                }
            })
        }
        archiveExtension.set("zip")

        into(".") {
            from(pluginMainFile.parent)
            include("plugin.properties")
        }
    }

    val repository = rootProject.file("ci-repo").toPath()

    val buildRepository = task<MakeRepositoryIndexTask>("buildRepository") {
        targetDirectory.set(repository)
        repositoryUrl.set("https://plugin-repository.mikbot.schlaubi.net")
    }

    afterEvaluate {
        task<Copy>("copyFilesIntoRepo") {
            dependsOn(buildRepository)
            from(archive)
            include("*.zip")
            // providing version manually, as of weird evaluation errors
            into(repository.resolve("${project.name}/$version"))
        }
    }
}

fun File.removeVersion() = name.takeWhile { !it.isDigit() }
