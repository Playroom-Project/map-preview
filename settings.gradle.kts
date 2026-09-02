pluginManagement {
    repositories { gradlePluginPortal() }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { mavenCentral() }
}

rootProject.name = "map-preview"
include("core", "minecraft-common", "client-common", "pregen-common", "config", "compat-api", "platform-api", "benchmarks")
project(":compat-api").projectDir = file("compat/api")
project(":platform-api").projectDir = file("platforms/common")
