pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }

    val kotlin_version: String by settings
    val ktor_version: String by settings

    plugins {
        kotlin("jvm") version kotlin_version
        kotlin("plugin.serialization") version kotlin_version
        id("io.ktor.plugin") version ktor_version
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.5.0"
}

rootProject.name = "ktor-server-api-gateway"