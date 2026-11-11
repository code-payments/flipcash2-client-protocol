pluginManagement {
    // scripts/toolchain.env is the one place generator versions are pinned. pluginManagement
    // is evaluated before the rest of this file and cannot see its values, so it reads the
    // file itself.
    val toolchain = file("scripts/toolchain.env").readLines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") && "=" in it }
        .associate { it.substringBefore("=") to it.substringAfter("=") }
    fun pin(key: String): String = toolchain[key] ?: error("scripts/toolchain.env has no $key")

    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
        maven(url = "https://jitpack.io")
    }
    plugins {
        id("com.squareup.wire") version pin("WIRE_VERSION")
        id("dev.bmcreations.protovalidate") version pin("PROTOVALIDATE_KT_VERSION")
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.PREFER_SETTINGS
    repositories {
        mavenLocal()
        mavenCentral()
        maven(url = "https://plugins.gradle.org/m2/")
        maven(url = "https://jitpack.io")
    }
}

// The same pins, for the runtime dependencies the build scripts declare.
gradle.extra["toolchain"] = file("scripts/toolchain.env").readLines()
    .map { it.trim() }
    .filter { it.isNotEmpty() && !it.startsWith("#") && "=" in it }
    .associate { it.substringBefore("=") to it.substringAfter("=") }

rootProject.name = "flipcash2-client-protocol"
