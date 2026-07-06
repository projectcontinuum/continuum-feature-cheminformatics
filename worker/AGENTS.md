# worker

## Purpose

Spring Boot worker application that registers all cheminformatics node implementations with Temporal and serves as the runtime host for workflow execution. It has no node logic of its own — it depends on `:features:continuum-feature-rdkit` and delegates everything to it.

## Ownership

- Gradle subproject: `:worker`
- Group: `org.projectcontinuum.app.worker.example`
- Version: controlled by `featureVersion` in `gradle.properties`
- Plugin: `org.projectcontinuum.worker` version `0.0.12`

## Local Contracts

- Entry point: `App.kt` — `@SpringBootApplication` + `runApplication<App>()`
- Application config: `src/main/resources/application.yaml`
- Temporal namespace resolved from env `TEMPORAL_NAMESPACE` (default: `default`)
- Temporal server address resolved from env `TEMPORAL_SERVER_ADDRESS` (default: `localhost:7233`)
- Worker auto-discovery scans `org.projectcontinuum.core.worker.starter`
- No node models or business logic belong in this subproject

## Work Guidance

- To add a new feature library, add it as `implementation(project(":features:..."))` in `build.gradle.kts`
- All configuration tuning goes in `application.yaml`; do not hardcode Temporal addresses
- Run locally: `./gradlew :worker:bootRun` (requires local infrastructure from `docker/` to be running)
- The IntelliJ run config is at `.run/FeatureCheminsformatics.run.xml`

## Verification

`./gradlew :worker:build` compiles and packages. Worker correctness is verified via end-to-end test workflows in `test-workflows/` rather than unit tests.

## Child DOX Index

No child AGENTS.md.
