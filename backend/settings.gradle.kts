plugins {
    // Resolves and downloads the Java 21 toolchain when the machine only has a newer JDK.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "backend"
