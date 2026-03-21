plugins {
    id("org.projectcontinuum.worker") version "0.0.6"
}

group = "org.projectcontinuum.app.worker.example"
description = "Continuum Feature Example Worker — Spring Boot worker application for example feature nodes"
version = "0.0.1"

continuum {
    continuumVersion.set("0.0.7")
}

dependencies {
    implementation(project(":features:continuum-feature-rdkit"))
}
