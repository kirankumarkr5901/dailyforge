// Where Gradle plugins (and the libraries they depend on) come from. The Gradle Plugin
// Portal forwards most downloads to Maven Central, and on Render's shared build
// machines Maven Central answers "429 Too Many Requests" — which failed the API deploy.
// Google's official Maven Central mirror comes first; the Plugin Portal stays as the
// fallback for plugins only published there (foojay), whose files it serves itself.
pluginManagement {
    repositories {
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        gradlePluginPortal()
    }
}

plugins {
    // Resolves and downloads the Java 21 toolchain when the machine only has a newer JDK.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "backend"
