// Minecraft 26.x (unobfuscated: no mappings). 1.21.11 builds from legacy.gradle.kts instead.
plugins {
    id("net.fabricmc.fabric-loom") version "1.17.20"
}

val mc = property("mc") as String
// Per release: the version range the jar claims, and the Mod Menu build compiled against.
val targets = mapOf(
    "26.1" to Pair(">=26.1 <26.2", "18.0.2"),
    "26.2" to Pair(">=26.2 <26.3", "20.0.3"),
    "26.3" to Pair(">=26.3 <26.4", "21.0.0"),
)
val target = targets[mc] ?: error("Unknown target $mc; one of ${targets.keys} or 1.21.11")

version = "${property("mod_version")}+mc$mc"
group = property("maven_group") as String
base { archivesName = property("archives_base_name") as String }

repositories {
    mavenCentral()
    maven("https://maven.terraformersmc.com/releases/")
}

dependencies {
    minecraft("com.mojang:minecraft:$mc")
    implementation("net.fabricmc:fabric-loader:${property("loader_version")}")
    // Only compiled against: the settings button shows up in Mod Menu when it's installed.
    compileOnly("com.terraformersmc:modmenu:${target.second}") { isTransitive = false }
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// src/main is plain Java with no Minecraft imports; src/game is the game-facing code shared by
// every target, and Screens (where the open screen lives) differs from 26.2 on.
sourceSets {
    main {
        java.srcDir("src/game/java")
        java.srcDir(if (mc == "26.1") "src/screens-field/java" else "src/screens-gui/java")
    }
}

val expansions = mapOf(
    "version" to project.version.toString(),
    "minecraft" to target.first,
    "java" to "25",
    "mixinjava" to "JAVA_25",
)
tasks.processResources {
    inputs.properties(expansions)
    filesMatching(listOf("fabric.mod.json", "priceshare.mixins.json")) { expand(expansions) }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
    options.encoding = "UTF-8"
    options.compilerArgs.add("-Xlint:deprecation")
}
java { toolchain { languageVersion = JavaLanguageVersion.of(25) } }

tasks.test { useJUnitPlatform() }
tasks.jar { from("LICENSE") }

loom {
    runs {
        named("client") {
            vmArg("-Xmx1G")
            (findProperty("psArgs") as String?)?.split(" ")?.filter { it.isNotBlank() }?.forEach { vmArg(it) }
        }
    }
}
