import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import com.vanniktech.maven.publish.SourcesJar
import dev.bmcreations.protovalidate.gradle.MessageTarget
import dev.bmcreations.protovalidate.gradle.ProtoVariant

// Wire messages and protovalidate-kt validators for Kotlin Multiplatform, generated from the
// same proto/ as the root project's protobuf-java artifact. Messages only: no gRPC services,
// because each platform brings its own transport.
plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library") version "9.5.1"
    id("com.squareup.wire")
    id("dev.bmcreations.protovalidate")
    id("com.vanniktech.maven.publish")
}

@Suppress("UNCHECKED_CAST")
val toolchain = gradle.extra["toolchain"] as Map<String, String>

group = rootProject.group
version = rootProject.version

// Wire, and protovalidate-kt in Wire mode, take their Kotlin package from java_package. The
// upstream protos set that to com.codeinc.flipcash.gen.*, which is the protobuf-java artifact's
// namespace; Android carries both artifacts while it moves service by service, so the Wire
// classes need their own. This rewrites java_package in a build-time copy of proto/ and
// leaves proto/ itself alone, since sync-protos.sh owns it.
val wireProtos = tasks.register<Sync>("wireProtos") {
    // Locals, not script-level vals: the actions below must not capture the build script, or
    // the configuration cache (which the Android app enables) cannot store this task.
    val genPackage = "com.codeinc.flipcash.gen."
    val wirePackage = "com.codeinc.flipcash.wire."
    val outDir = layout.buildDirectory.dir("wire-protos")
    from(rootProject.layout.projectDirectory.dir("proto"))
    include("**/*.proto")
    into(outDir)
    filter { line ->
        if (line.trimStart().startsWith("option java_package")) line.replace(genPackage, wirePackage) else line
    }
    doLast {
        val leftover = outDir.get().asFile.walkTopDown()
            .filter { it.isFile && it.readText().contains(genPackage) }
            .toList()
        check(leftover.isEmpty()) { "$genPackage survived the rewrite in $leftover" }
    }
}

kotlin {
    jvmToolchain(21)
    // No explicitApi(): protovalidate-kt 0.2.0 emits its validator objects without a
    // visibility modifier, and every file here is generated.

    jvm()
    android {
        namespace = "com.codeinc.flipcash.wire"
        compileSdk = 36
        minSdk = 24
    }
    js(IR) {
        nodejs()
    }
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api("com.squareup.wire:wire-runtime:${toolchain.getValue("WIRE_VERSION")}")
            api("dev.bmcreations:protovalidate-runtime:${toolchain.getValue("PROTOVALIDATE_KT_VERSION")}")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmTest.dependencies {
            // The protobuf-java artifact, for the byte-compatibility tests.
            implementation(project(":"))
        }
    }
}

wire {
    sourcePath { srcDir(layout.buildDirectory.dir("wire-protos")) }
    // validate/validate.proto resolves imports but is not generated.
    protoPath { srcDir(protovalidate.validateProtoDir) }
    kotlin {
        rpcRole = "none"
        // Applied options would generate validate.proto's extension types into the artifact.
        emitAppliedOptions = false
        emitDeclaredOptions = false
    }
}

protovalidate {
    variant.set(ProtoVariant.PGV)
    target.set(MessageTarget.WIRE)
    protoDirs.setFrom(wireProtos)
}

tasks.matching { it.name.startsWith("generate") && it.name.endsWith("Protos") }.configureEach {
    dependsOn(wireProtos)
}

mavenPublishing {
    configure(KotlinMultiplatform(javadocJar = JavadocJar.Empty(), sourcesJar = SourcesJar.Sources()))

    publishToMavenCentral()
    if (providers.gradleProperty("signingInMemoryKey").isPresent) {
        signAllPublications()
    }

    coordinates(group.toString(), "flipcash2-client-protocol-wire", version.toString())

    pom {
        name.set("Flipcash client protocol (Wire)")
        description.set("Wire Kotlin Multiplatform messages and validators for the Flipcash contract")
        inceptionYear.set("2026")
        url.set("https://github.com/code-payments/flipcash2-client-protocol")
        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("code-payments")
                name.set("Code Payments")
                url.set("https://github.com/code-payments")
            }
        }
        scm {
            url.set("https://github.com/code-payments/flipcash2-client-protocol")
            connection.set("scm:git:git://github.com/code-payments/flipcash2-client-protocol.git")
            developerConnection.set("scm:git:ssh://git@github.com/code-payments/flipcash2-client-protocol.git")
        }
    }
}
