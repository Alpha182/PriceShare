// Minecraft 1.21.11, the last obfuscated release: the remapping Loom with Mojang's official
// names, so the same game-facing code (src/game) compiles here as on 26.x.
plugins {
    id("fabric-loom") version "1.17.20"
}

val mc = property("mc") as String
require(mc == "1.21.11") { "legacy.gradle.kts only builds 1.21.11, not $mc" }

version = "${property("mod_version")}+mc$mc"
group = property("maven_group") as String
base { archivesName = property("archives_base_name") as String }

repositories {
    mavenCentral()
    maven("https://maven.terraformersmc.com/releases/")
}

dependencies {
    minecraft("com.mojang:minecraft:$mc")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:${property("loader_version")}")
    modCompileOnly("com.terraformersmc:modmenu:17.0.1") { isTransitive = false }
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

sourceSets {
    main {
        java.srcDir("src/game/java")
        java.srcDir("src/screens-field/java")
    }
}

val expansions = mapOf(
    "version" to project.version.toString(),
    "minecraft" to "1.21.11",
    "java" to "21",
    "mixinjava" to "JAVA_21",
)
tasks.processResources {
    inputs.properties(expansions)
    filesMatching(listOf("fabric.mod.json", "priceshare.mixins.json")) { expand(expansions) }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 21
    options.encoding = "UTF-8"
    options.compilerArgs.add("-Xlint:deprecation")
}
java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }

tasks.test { useJUnitPlatform() }
tasks.jar { from("LICENSE") }
