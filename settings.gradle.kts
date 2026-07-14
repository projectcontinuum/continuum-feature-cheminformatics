pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "continuum-feature-cheminformatics"

include(":features:continuum-feature-rdkit")
include(":worker")
