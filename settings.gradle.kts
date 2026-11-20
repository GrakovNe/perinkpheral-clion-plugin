import org.jetbrains.intellij.platform.gradle.extensions.intellijPlatform

rootProject.name = "perinkpheral-clion-plugin"

pluginManagement {
    plugins {
        id("org.jetbrains.kotlin.jvm") version "2.4.21"
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("org.jetbrains.intellij.platform.settings") version "2.19.0"
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        intellijPlatform {
            defaultRepositories()
        }
    }
}

// The core - the build, the checks, the device and the simulator's network - is the SDK's own,
// tools/core of the perinkpheral repository: a checkout beside this one, or -PperinkpheralPath.
includeBuild((providers.gradleProperty("perinkpheralPath").orNull ?: "../perinkpheral-os") + "/tools/core")
