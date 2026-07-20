pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
    }
    val continuumPlatformVersion = providers.gradleProperty("continuumPlatformVersion").get()
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "org.projectcontinuum.worker" || requested.id.id == "org.projectcontinuum.feature") {
                useVersion(continuumPlatformVersion)
            }
        }
    }
}

rootProject.name = "continuum-feature-cheminformatics"

include(":features:continuum-feature-rdkit")
include(":worker")
