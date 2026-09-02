pluginManagement {
    repositories {
        mavenCentral()
        maven("https://maven.fabricmc.net/") {
            content { includeGroupByRegex("net\\.fabricmc.*"); includeGroup("fabric-loom") }
        }
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories { mavenCentral() }
}

rootProject.name = "map-preview"
include("core", "minecraft-common", "client-common", "pregen-common", "config", "compat-api", "platform-api", "benchmarks")
project(":compat-api").projectDir = file("compat/api")
project(":platform-api").projectDir = file("platforms/common")

val loader = providers.gradleProperty("loader").getOrElse("fabric")
require(loader in setOf("fabric", "none")) {
    "Only Fabric produces a distributable on 1.20.x. Forge, NeoForge and Quilt have porting targets in gradle/targets.json. Use -Ploader=none for the shared libraries."
}
if (loader == "fabric") {
    include("fabric")
    project(":fabric").projectDir = file("platforms/fabric")
}
