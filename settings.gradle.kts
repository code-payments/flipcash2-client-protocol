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
        google()
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
        google()
        maven(url = "https://plugins.gradle.org/m2/")
        maven(url = "https://jitpack.io")
        // Node.js and Yarn for the Wire artifact's js(nodejs) tests. KGP adds these
        // repositories to the project, which PREFER_SETTINGS ignores, so they are declared here.
        ivy(url = "https://nodejs.org/dist") {
            name = "Node.js distributions"
            patternLayout { artifact("v[revision]/[artifact](-v[revision]-[classifier]).[ext]") }
            metadataSources { artifact() }
            content { includeModule("org.nodejs", "node") }
        }
        ivy(url = "https://github.com/yarnpkg/yarn/releases/download") {
            name = "Yarn distributions"
            patternLayout { artifact("v[revision]/[artifact](-v[revision]).[ext]") }
            metadataSources { artifact() }
            content { includeModule("com.yarnpkg", "yarn") }
        }
    }
}

// The same pins, for the runtime dependencies the build scripts declare.
gradle.extra["toolchain"] = file("scripts/toolchain.env").readLines()
    .map { it.trim() }
    .filter { it.isNotEmpty() && !it.startsWith("#") && "=" in it }
    .associate { it.substringBefore("=") to it.substringAfter("=") }

rootProject.name = "flipcash2-client-protocol"

// Wire messages for Kotlin Multiplatform. The project name is the artifact id, because a
// composite build (the apps' protoLocalRoot) substitutes by group and project name.
include(":flipcash2-client-protocol-wire")
project(":flipcash2-client-protocol-wire").projectDir = file("wire")
