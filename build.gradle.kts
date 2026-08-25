import com.google.protobuf.gradle.id
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm
import dev.bmcreations.protovalidate.gradle.ProtoVariant
import org.apache.tools.ant.taskdefs.condition.Os

plugins {
    kotlin("jvm") version "2.2.20"
    id("com.google.protobuf") version "0.10.0"
    id("dev.bmcreations.protovalidate") version "0.1.1"
    id("com.vanniktech.maven.publish") version "0.37.0"
}

// Versions are pinned here rather than inherited from a consumer, so the artifact this
// repo publishes is reproducible from this file alone.
val archSuffix = if (Os.isFamily(Os.FAMILY_MAC)) {
    if (System.getProperty("os.arch") == "aarch64") ":osx-aarch_64" else ":osx-x86_64"
} else ""

val protobufVersion = "4.35.1"
val grpcVersion = "1.83.1"
// 1.4.1 is what the Android app generates with today, so parity is exact. 1.5.0 was
// tested and differs only in line wrapping (344 lines, no API change) -- safe to adopt,
// but do it as its own commit so the diff stays readable.
val grpcKotlinVersion = "1.4.1"

// Publishing coordinates only. The generated code keeps its com.codeinc.flipcash.gen.*
// java_package -- that is the namespace the app imports, and it is set in the protos.
group = "com.flipcash"
version = System.getenv("RELEASE_VERSION") ?: "0.1.0-SNAPSHOT"

kotlin {
    jvmToolchain(21)
}

sourceSets {
    main {
        proto { srcDir("proto") }
    }
}

dependencies {
    api("com.google.protobuf:protobuf-kotlin-lite:$protobufVersion")
    api("io.grpc:grpc-protobuf-lite:$grpcVersion")
    api("io.grpc:grpc-stub:$grpcVersion")
    api("io.grpc:grpc-kotlin-stub:$grpcKotlinVersion")
    // The grpckt stubs reference kotlinx.coroutines.flow.Flow directly. The Android app
    // gets coroutines from elsewhere in its graph; a standalone artifact has to declare it.
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    // protoc-gen-validate-kt output needs this on the consumer classpath, so it is `api`.
    api("dev.bmcreations:protovalidate-runtime:0.1.1")
    compileOnly("javax.annotation:javax.annotation-api:1.3.2")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:$protobufVersion$archSuffix"
    }
    plugins {
        create("grpc") { artifact = "io.grpc:protoc-gen-grpc-java:$grpcVersion" }
        create("grpckt") { artifact = "io.grpc:protoc-gen-grpc-kotlin:$grpcKotlinVersion:jdk8@jar" }
    }
    generateProtoTasks {
        all().forEach {
            it.plugins {
                create("grpc") { option("lite") }
                create("grpckt") { option("lite") }
            }
            it.builtins {
                // The JVM variant of the protobuf plugin registers the `java` builtin by
                // default; the Android variant does not, which is why the app declares it
                // as a plugin instead.
                named("java") { option("lite") }
                create("kotlin") { option("lite") }
            }
        }
    }
}

// Supplies validate/validate.proto on protoc's include path from the plugin's own JAR,
// which is why neither this repo nor the Android app vendors that file.
protovalidate {
    variant.set(ProtoVariant.PGV)
}

mavenPublishing {
    // Central requires a javadoc jar to exist but does not require it to have content.
    // Everything here is generated protobuf/gRPC code, so real javadoc is a few minutes of
    // build time and 100 "no @return" warnings for pages nobody reads.
    configure(KotlinJvm(javadocJar = JavadocJar.Empty(), sourcesJar = true))

    publishToMavenCentral()
    // Central rejects unsigned artifacts. CI supplies the key through the
    // ORG_GRADLE_PROJECT_signingInMemoryKey* properties; locally this is a no-op unless
    // the same properties are set, so `publishToMavenLocal` still works unsigned.
    if (providers.gradleProperty("signingInMemoryKey").isPresent) {
        signAllPublications()
    }

    coordinates(group.toString(), "flipcash2-client-protocol", version.toString())

    pom {
        name.set("Flipcash client protocol")
        description.set("Generated Kotlin client for the Flipcash gRPC contract")
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
