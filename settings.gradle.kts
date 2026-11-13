pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/")
    }
}

// One build per Minecraft version, picked with -Pmc=<version> (default 26.2). 1.21.11 is the last
// obfuscated release and needs the remapping Loom, so it has its own build file.
val mc = providers.gradleProperty("mc").getOrElse("26.2")
rootProject.buildFileName = if (mc.startsWith("1.")) "legacy.gradle.kts" else "build.gradle.kts"
rootProject.name = "priceshare"
