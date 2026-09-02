import groovy.json.JsonSlurper
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("fabric-loom") version "1.11.8"
    id("com.gradleup.shadow") version "8.3.8"
}

val matrix = JsonSlurper().parse(rootProject.file("gradle/targets.json")) as Map<*, *>
val targetMinecraft = providers.gradleProperty("minecraftVersion").get()
val target = (matrix["targets"] as List<*>).map { it as Map<*, *> }
    .singleOrNull { it["minecraft"] == targetMinecraft }
    ?: error("Unsupported Minecraft version: $targetMinecraft. Select 1.20 through 1.20.6.")
val targetJava = (target["java"] as Number).toInt()
val bundled = configurations.create("bundled")
val isolatedJson = configurations.create("isolatedJson")

base.archivesName.set("map-preview-fabric-$targetMinecraft")
layout.buildDirectory.set(rootProject.layout.buildDirectory.dir("fabric/$targetMinecraft"))

repositories {
    maven("https://libraries.minecraft.net/")
    mavenCentral()
    maven("https://maven.fabricmc.net/")
}
repositories.withType<MavenArtifactRepository>().configureEach {
    if (url.host == "maven.fabricmc.net") {
        content { includeGroupByRegex("net\\.fabricmc.*"); includeGroup("fabric-loom") }
    }
    if (url.host == "libraries.minecraft.net") {
        content {
            includeGroup("com.mojang")
            includeGroup("ca.weblite")
            // Minecraft 1.20.5+ publishes a patched macOS FreeType native here.
            includeModule("org.lwjgl", "lwjgl-freetype")
        }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:$targetMinecraft")
    mappings("net.fabricmc:yarn:${target["yarn"]}:v2")
    modImplementation("net.fabricmc:fabric-loader:${matrix["fabric_loader"]}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${target["fabric_api"]}")
    for (module in listOf("core", "minecraft-common", "client-common", "pregen-common", "config", "compat-api", "platform-api")) {
        implementation(project(":$module"))
        if (module != "config") bundled(project(":$module")) { isTransitive = false }
    }
    isolatedJson("com.google.code.gson:gson:2.13.1") { isTransitive = false }
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("net.fabricmc:fabric-loader-junit:${matrix["fabric_loader"]}")
}

java {
    sourceCompatibility = JavaVersion.toVersion(targetJava)
    targetCompatibility = JavaVersion.toVersion(targetJava)
    withSourcesJar()
}
sourceSets {
    main {
        java.srcDir(rootProject.file("versions/shared/src/main/java"))
        java.srcDir(rootProject.file("versions/${target["api_family"]}/src/main/java"))
        resources.srcDir(rootProject.file("versions/shared/src/main/resources"))
    }
    test {
        java.srcDir(rootProject.file("versions/shared/src/test/java"))
        java.srcDir(rootProject.file("versions/${target["api_family"]}/src/test/java"))
    }
}
loom {
    runs {
        named("client") { runDir("run/$targetMinecraft/client") }
        named("server") { runDir("run/$targetMinecraft/server") }
    }
}
if (providers.gradleProperty("gameTests").isPresent) {
    fabricApi {
        configureTests {
            createSourceSet = true
            modId = "map_preview_gametest"
            enableGameTests = true
            eula = false
            clearRunDirectory = false
        }
    }
    val gameTestReport = layout.buildDirectory.file("gametest-results.xml")
    loom.runs.named("gameTest") {
        runDir("run/$targetMinecraft/gametest")
        vmArg("-Dfabric-api.gametest.report-file=${gameTestReport.get().asFile.absolutePath}")
    }
}
tasks.withType<JavaCompile>().configureEach {
    options.release.set(targetJava)
    options.encoding = "UTF-8"
}
tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
tasks.processResources {
    val properties = mapOf("version" to project.version, "minecraft" to targetMinecraft,
        "java" to targetJava, "loader" to matrix["fabric_loader"], "fabric_api" to target["fabric_api"])
    inputs.properties(properties)
    filesMatching("fabric.mod.json") { expand(properties) }
    from(rootProject.file("gradle/wrapper/LICENSE")) {
        into("META-INF/licenses")
        rename { "Gson-Apache-2.0.txt" }
    }
    from(rootProject.file("THIRD_PARTY_NOTICES.md")) { into("META-INF") }
}
val relocateConfig by tasks.registering(ShadowJar::class) {
    val configJar = project(":config").tasks.named<Jar>("jar")
    dependsOn(configJar)
    from(configJar.map { zipTree(it.archiveFile.get().asFile) })
    configurations = listOf(isolatedJson)
    archiveClassifier.set("config-internal")
    relocate("com.google.gson", "io.github.playroomproject.mappreview.internal.gson")
    exclude("META-INF/maven/**", "META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA", "module-info.class")
}
tasks.named<ShadowJar>("shadowJar") {
    dependsOn(relocateConfig)
    from(relocateConfig.map { zipTree(it.archiveFile.get().asFile) })
    configurations = listOf(bundled)
    archiveClassifier.set("dev-shadow")
    exclude("META-INF/maven/**", "META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA", "module-info.class")
    mergeServiceFiles()
}
tasks.remapJar {
    dependsOn(tasks.shadowJar)
    inputFile.set(tasks.shadowJar.flatMap { it.archiveFile })
    archiveClassifier.set("")
}
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(targetJava)) })
    maxHeapSize = "2G"
    testLogging { events("failed", "skipped") }
}
tasks.withType<JavaExec>().configureEach {
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(targetJava)) })
    maxHeapSize = "2G"
}
