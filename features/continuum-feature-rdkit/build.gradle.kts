plugins {
    id("org.projectcontinuum.feature") version "0.0.6"
}

group = "org.projectcontinuum.feature.example"
description = "Continuum Feature Example — a sample feature module with a Hello World node"
version = "0.0.1"

continuum {
    continuumVersion.set("0.0.7")
}

dependencies {
    implementation(files("lib/org.RDKit.jar"))
}
