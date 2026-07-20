plugins {
    id("org.projectcontinuum.feature")
}

group = "org.projectcontinuum.feature.example"
description = "Continuum Feature Example — a sample feature module with a Hello World node"
version = property("featureVersion").toString()

val continuumPlatformVersion = property("continuumPlatformVersion").toString()

continuum {
    continuumVersion.set(continuumPlatformVersion)
}

dependencies {
    implementation(files("lib/org.RDKit.jar"))
}
