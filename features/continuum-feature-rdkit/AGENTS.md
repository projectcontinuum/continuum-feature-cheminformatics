# features/continuum-feature-rdkit

## Purpose

Implements all 38 RDKit-powered cheminformatics nodes for the Continuum platform. This is the sole Gradle subproject (`org.projectcontinuum.feature.example`) that contains node logic; it ships as a library consumed by the `worker` subproject.

## Ownership

- Gradle subproject: `:features:continuum-feature-rdkit`
- Group: `org.projectcontinuum.feature.example`
- Version: controlled by `featureVersion` in `gradle.properties`
- Plugin: `org.projectcontinuum.feature` version `0.0.12`

## Local Contracts

- Every node is a Kotlin class annotated `@ContinuumNode` extending `ProcessNodeModel()` in `src/main/kotlin/org/projectcontinuum/feature/rdkit/node/`
- Node documentation goes in `src/main/resources/org/projectcontinuum/feature/rdkit/node/<ClassName>.doc.md` (one `.doc.md` per node)
- The shared RDKit JNI library is at `lib/org.RDKit.jar` — added to dependencies via `implementation(files("lib/org.RDKit.jar"))`
- Native `.so` / `.jnilib` / `.dll` binaries live in `src/main/resources/native/<platform>/`
- Spring auto-configuration entry point is `AutoConfigure.kt` with `@ComponentScan`; the import is registered in `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- `RDKitNodeHelper` at `src/main/kotlin/org/projectcontinuum/feature/rdkit/util/RDKitNodeHelper.kt` provides safe mol/SMARTS parse helpers with auto-delete; use it instead of inline RDKFuncs calls where possible
- `RDKitService.kt` is a diagnostic/reference service; node logic does not delegate to it
- `RDKitVerificationRunner.kt` is a Continuum verification runner registered alongside nodes

## Work Guidance

**Node anatomy** (canonical order):
1. `inputPorts` — `Map<String, NodePort>`, keys are port names
2. `outputPorts` — same
3. `categories` — `listOf("RDKit", "<SubCategory>")` (see categories below)
4. `propertiesSchema` — JSON Schema `Map<String, Any>` from inline trimIndent string
5. `propertiesUiSchema` — React Material JSONForms UI schema
6. `metadata` — `ContinuumWorkflowModel.NodeData` with `id = this.javaClass.name`
7. `execute()` — reads from `inputs`, writes rows to `nodeOutputWriter`, calls `nodeProgressCallback.report()`

**Node categories** (dual: `"RDKit"` + one of):
`Converters`, `Modifiers`, `Calculators`, `Geometry`, `Fingerprints`, `Fragments`, `Searching`, `Reactions`, `Rendering`, `Experimental`, `Testing`

**RDKit JNI rules**:
- Every `ROMol`, `RWMol`, `ExplicitBitVect`, `ChemicalReaction` must be `.delete()`-ed in a `finally` block — memory leaks otherwise
- RDKit JNI objects are NOT thread-safe per object; never share across threads
- InChI operations require external synchronization

**Error handling**:
- Missing required property → `NodeRuntimeException(workflowId = "", nodeId = "", message = "...")`
- Invalid SMILES → preserve row with empty string for computed columns; do NOT skip rows
- RDKit operation failure → log warning, write row with empty columns, continue

**KNIME → Continuum mapping**:
- `RDKitMolValue` → SMILES string in named column
- `SettingsModelString` → JSON Schema `"type": "string"` property
- `SettingsModelEnumeration` → `"type": "string", "enum": [...]`
- Two output ports → `outputPorts` map with `"match"` and `"noMatch"` keys

## Verification

Run `./gradlew :features:continuum-feature-rdkit:test` for unit tests. Each node has a corresponding `*NodeModelTest.kt` in `src/test/kotlin/org/projectcontinuum/feature/rdkit/node/`. `RDKitTestExtension.kt` provides shared JUnit 5 extension for RDKit JNI initialization.

## Child DOX Index

No child AGENTS.md — all source subdirectories (`node/`, `service/`, `util/`, `runner/`, `config/`, `controller/`, `resources/`) belong to this boundary.
