# RDKit Continuum Nodes — Preamble & Architecture Guide

You are an expert Continuum Node developer. Your job is to implement KNIME RDKit nodes as native Continuum workflow nodes in Kotlin. Each node goes in:

```
continuum-feature-cheminformatics/features/continuum-feature-rdkit/src/main/kotlin/org/projectcontinuum/feature/rdkit/node/
```

Documentation for each node goes in:

```
continuum-feature-cheminformatics/features/continuum-feature-rdkit/src/main/resources/org/projectcontinuum/feature/rdkit/node/<ClassName>.doc.md
```

---

## Continuum Node Pattern (Complete Reference)

Every Continuum node is a Kotlin class annotated `@ContinuumNode` extending `ProcessNodeModel()`. Here is the canonical boilerplate:

```kotlin
package org.projectcontinuum.feature.rdkit.node

import org.projectcontinuum.core.commons.exception.NodeRuntimeException
import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import org.projectcontinuum.core.commons.node.ProcessNodeModel
import org.projectcontinuum.core.commons.protocol.progress.NodeProgressCallback
import org.projectcontinuum.core.commons.utils.NodeInputReader
import org.projectcontinuum.core.commons.utils.NodeOutputWriter
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType.APPLICATION_OCTET_STREAM_VALUE
import org.projectcontinuum.core.commons.annotation.ContinuumNode
import org.RDKit.*

@ContinuumNode
class ExampleNodeModel : ProcessNodeModel() {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(ExampleNodeModel::class.java)
        private val objectMapper = ObjectMapper()
    }

    // 1. INPUT PORTS
    final override val inputPorts = mapOf(
        "input" to ContinuumWorkflowModel.NodePort(
            name = "input table",
            contentType = APPLICATION_OCTET_STREAM_VALUE
        )
    )

    // 2. OUTPUT PORTS
    final override val outputPorts = mapOf(
        "output" to ContinuumWorkflowModel.NodePort(
            name = "output table",
            contentType = APPLICATION_OCTET_STREAM_VALUE
        )
    )

    // 3. CATEGORIES
    override val categories = listOf("RDKit", "SubCategory")

    // 4. PROPERTIES SCHEMA (JSON Schema)
    val propertiesSchema: Map<String, Any> = objectMapper.readValue(
        """
        {
          "type": "object",
          "properties": {
            "smilesColumn": {
              "type": "string",
              "title": "SMILES Column",
              "description": "Column containing SMILES strings"
            }
          },
          "required": ["smilesColumn"]
        }
        """.trimIndent(),
        object : TypeReference<Map<String, Any>>() {}
    )

    // 5. UI SCHEMA (React Material JSONForms)
    val propertiesUiSchema: Map<String, Any> = objectMapper.readValue(
        """
        {
          "type": "VerticalLayout",
          "elements": [
            { "type": "Control", "scope": "#/properties/smilesColumn" }
          ]
        }
        """.trimIndent(),
        object : TypeReference<Map<String, Any>>() {}
    )

    // 6. METADATA
    override val metadata = ContinuumWorkflowModel.NodeData(
        id = this.javaClass.name,
        description = "One-line description",
        title = "Node Title",
        subTitle = "Short subtitle",
        nodeModel = this.javaClass.name,
        icon = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><circle cx="12" cy="12" r="10"/></svg>""",
        inputs = inputPorts,
        outputs = outputPorts,
        properties = mapOf("smilesColumn" to "smiles"),
        propertiesSchema = propertiesSchema,
        propertiesUISchema = propertiesUiSchema
    )

    // 7. EXECUTE
    override fun execute(
        properties: Map<String, Any>?,
        inputs: Map<String, NodeInputReader>,
        nodeOutputWriter: NodeOutputWriter,
        nodeProgressCallback: NodeProgressCallback
    ) {
        val smilesColumn = properties?.get("smilesColumn") as String?
            ?: throw NodeRuntimeException(workflowId = "", nodeId = "", message = "smilesColumn is not provided")

        val inputReader = inputs["input"]
            ?: throw NodeRuntimeException(workflowId = "", nodeId = "", message = "Input port 'input' is not connected")

        val totalRows = inputReader.getRowCount()

        nodeOutputWriter.createOutputPortWriter("output").use { writer ->
            inputReader.use { reader ->
                var row = reader.read()
                var rowNumber = 0L
                while (row != null) {
                    val smiles = row[smilesColumn]?.toString() ?: ""
                    val mol = RDKFuncs.SmilesToMol(smiles)
                    try {
                        if (mol != null) {
                            val result = RDKFuncs.MolToSmiles(mol) // YOUR COMPUTATION HERE
                            val outputRow = row.toMutableMap<String, Any>()
                            outputRow["result"] = result
                            writer.write(rowNumber, outputRow)
                        } else {
                            // Invalid SMILES — pass through with empty computed columns
                            val outputRow = row.toMutableMap<String, Any>()
                            outputRow["result"] = ""
                            writer.write(rowNumber, outputRow)
                        }
                    } finally {
                        mol?.delete()
                    }
                    rowNumber++
                    if (totalRows > 0) nodeProgressCallback.report((rowNumber * 100 / totalRows).toInt())
                    row = reader.read()
                }
            }
        }
        nodeProgressCallback.report(100)
    }
}
```

---

## RDKit Java Wrapper Rules

### Key Classes (from `org.RDKit.*`)
- `RDKFuncs` — Static utility methods (SmilesToMol, MolToSmiles, calcAMW, etc.)
- `ROMol` — Read-only molecule object
- `RWMol` — Read-write molecule object (extends ROMol)
- `ExplicitBitVect` — Bit-vector fingerprints
- `SparseIntVect32` — Count-based fingerprints
- `ExtraInchiReturnValues` — Extra info from InChI conversion
- `ChemicalReaction` — Reaction SMIRKS object
- `EmbedParameters` — 3D embedding parameters
- `DistanceGeom` — Distance geometry (EmbedMolecule, EmbedMultipleConfs)
- `ForceField` — Force field optimization (UFF, MMFF)
- `Match_Vect`, `Match_Vect_Vect` — Substructure match results
- `Double_Vect`, `Int_Vect`, `UInt_Vect` — Native vector types

### Memory Management (CRITICAL)
Every `ROMol`, `RWMol`, `ExplicitBitVect`, `ChemicalReaction` must be `.delete()`-ed in a `finally` block:
```kotlin
val mol = RDKFuncs.SmilesToMol(smiles)
try {
    // use mol
} finally {
    mol?.delete()
}
```

### Thread Safety
- RDKit JNI calls are NOT thread-safe per molecule object
- Do not share `ROMol` objects across threads
- InChI operations require synchronization (use `synchronized(lock)` block)

### Common API Patterns (from existing RDKitService.kt)
```kotlin
// Parse SMILES
val mol = RDKFuncs.SmilesToMol(smiles)  // returns null for invalid SMILES

// Parse SMARTS (for substructure queries)
val query = RDKFuncs.SmartsToMol(smarts)

// Canonical SMILES output
val canonical = RDKFuncs.MolToSmiles(mol)

// MolBlock (SDF) output
val molBlock = RDKFuncs.MolToMolBlock(mol)

// InChI conversion
val extraInfo = ExtraInchiReturnValues()
val inchi = RDKFuncs.MolToInchi(mol, extraInfo)
val inchiKey = RDKFuncs.InchiToInchiKey(inchi)

// Descriptors
val mw = RDKFuncs.calcAMW(mol)
val tpsa = RDKFuncs.calcTPSA(mol)
val logP = RDKFuncs.calcCrippenDescriptors(mol).getFirst()

// Substructure search
val hasMatch = mol.hasSubstructMatch(query)
val matches = mol.getSubstructMatches(query)

// Fingerprints
val rdkFp = RDKFuncs.RDKFingerprintMol(mol)
val morganFp = RDKFuncs.getMorganFingerprintAsBitVect(mol, 2, 2048)
val tanimoto = RDKFuncs.TanimotoSimilarity(fp1, fp2)

// Murcko scaffold
val scaffold = RDKFuncs.MurckoDecompose(mol)

// 3D embedding
RDKFuncs.addHs(mol)
val params = RDKFuncs.getETKDGv3()
DistanceGeom.EmbedMolecule(mol, params)

// Add/Remove Hs
RDKFuncs.addHs(mol)
RDKFuncs.removeHs(mol)
```

---

## Data Model Adaptation (KNIME → Continuum)

| KNIME Concept | Continuum Equivalent |
|---|---|
| RDKitMolValue cell type | SMILES string in a named column |
| SettingsModelString | JSON Schema `"type": "string"` property |
| SettingsModelBoolean | JSON Schema `"type": "boolean"` property |
| SettingsModelInteger | JSON Schema `"type": "integer"` property |
| SettingsModelDouble | JSON Schema `"type": "number"` property |
| SettingsModelEnumeration | JSON Schema `"type": "string", "enum": [...]` |
| SettingsModelEnumerationArray | JSON Schema `"type": "array", "items": {"type": "string", "enum": [...]}` |
| 2 output ports (match/noMatch) | `outputPorts` map with `"match"` and `"noMatch"` keys |
| 2 input ports | `inputPorts` map with `"molecules"` and `"queries"` (or similar) keys |

---

## Error Handling

- **Missing required property**: Throw `NodeRuntimeException(workflowId = "", nodeId = "", message = "...")`
- **Missing input port**: Throw `NodeRuntimeException`
- **Invalid SMILES**: Preserve row with empty string for computed columns (do NOT skip rows)
- **RDKit operation failure**: Log warning, write row with empty computed columns, continue

---

## Category Convention

All RDKit nodes use dual categories: `listOf("RDKit", "<SubCategory>")`. Sub-categories are:
- `Converters`, `Modifiers`, `Calculators`, `Geometry`, `Fingerprints`, `Fragments`, `Searching`, `Reactions`, `Rendering`, `Experimental`, `Testing`

---

## Shared Utility: RDKitNodeHelper.kt

Before implementing nodes, create this utility at:
`src/main/kotlin/org/projectcontinuum/feature/rdkit/util/RDKitNodeHelper.kt`

```kotlin
package org.projectcontinuum.feature.rdkit.util

import org.RDKit.RDKFuncs
import org.RDKit.ROMol

object RDKitNodeHelper {
    fun parseMolecule(smiles: String): ROMol =
        RDKFuncs.SmilesToMol(smiles) ?: throw IllegalArgumentException("Invalid SMILES: $smiles")

    fun parseMoleculeOrNull(smiles: String): ROMol? = RDKFuncs.SmilesToMol(smiles)

    fun parseSmartsOrNull(smarts: String): ROMol? = RDKFuncs.SmartsToMol(smarts)

    fun toCanonicalSmiles(mol: ROMol): String = RDKFuncs.MolToSmiles(mol)

    inline fun <T> withMolecule(smiles: String, block: (ROMol) -> T): T? {
        val mol = parseMoleculeOrNull(smiles) ?: return null
        try {
            return block(mol)
        } finally {
            mol.delete()
        }
    }

    inline fun <T> withSmarts(smarts: String, block: (ROMol) -> T): T? {
        val mol = parseSmartsOrNull(smarts) ?: return null
        try {
            return block(mol)
        } finally {
            mol.delete()
        }
    }
}
```

---

## File Index

| File | Category | Nodes |
|------|----------|-------|
| `01-converters.md` | Converters | 5 nodes |
| `02-modifiers.md` | Modifiers | 5 nodes |
| `03-calculators.md` | Calculators | 2 nodes |
| `04-geometry.md` | Geometry | 5 nodes |
| `05-fingerprints.md` | Fingerprints | 4 nodes |
| `06-fragments.md` | Fragments | 3 nodes |
| `07-searching.md` | Searching | 5 nodes |
| `08-reactions.md` | Reactions | 3 nodes |
| `09-rendering.md` | Rendering | 1 node |
| `10-experimental.md` | Experimental | 4 nodes |
| `11-testing.md` | Testing | 1 node |

**Total: 38 nodes**
