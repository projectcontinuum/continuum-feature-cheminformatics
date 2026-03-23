plugins {
    id("org.projectcontinuum.worker") version "0.0.9"
}

group = "org.projectcontinuum.app.worker.example"
description = "Continuum Feature Example Worker — Spring Boot worker application for example feature nodes"
version = property("featureVersion").toString()

val continuumPlatformVersion = property("continuumPlatformVersion").toString()

continuum {
  continuumVersion.set(continuumPlatformVersion)
}

dependencies {
    implementation(project(":features:continuum-feature-rdkit"))
}
