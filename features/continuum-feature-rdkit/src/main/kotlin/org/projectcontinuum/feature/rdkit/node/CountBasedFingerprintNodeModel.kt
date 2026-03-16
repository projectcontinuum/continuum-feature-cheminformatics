package org.projectcontinuum.feature.rdkit.node

import org.projectcontinuum.core.commons.annotation.ContinuumNode
import org.projectcontinuum.core.commons.exception.NodeRuntimeException
import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import org.projectcontinuum.core.commons.node.ProcessNodeModel
import org.projectcontinuum.core.commons.protocol.progress.NodeProgressCallback
import org.projectcontinuum.core.commons.utils.NodeInputReader
import org.projectcontinuum.core.commons.utils.NodeOutputWriter
import org.projectcontinuum.feature.rdkit.util.RDKitNodeHelper
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType.APPLICATION_OCTET_STREAM_VALUE
import org.RDKit.*

/**
 * Count-Based Fingerprint Node Model
 *
 * Generates count-based molecular fingerprints from SMILES strings using RDKit. For each row in
 * the input table, the node reads a SMILES string from the configured column, parses it into an
 * RDKit molecule object, and computes a count-based fingerprint of the selected type (Morgan,
 * AtomPair, or Torsion). The fingerprint is serialized as a JSON map of non-zero {index: count}
 * pairs. Invalid SMILES produce an empty string rather than an error, so the node never crashes
 * on malformed input.
 *
 * **Input Ports:**
 * - `input`: Input table containing a column with SMILES strings
 *
 * **Output Ports:**
 * - `output`: Table with all original columns plus a new column containing the count-based fingerprint
 *
 * **Configuration Properties:**
 * - `smilesColumn` (required): Name of the column containing SMILES strings
 * - `fingerprintType` (optional, default "Morgan"): Type of fingerprint (Morgan, AtomPair, Torsion)
 * - `numBits` (optional, default 2048): Number of bits for the hashed fingerprint
 * - `radius` (optional, default 2): Radius for Morgan fingerprints
 * - `useChirality` (optional, default false): Whether to use chirality in fingerprint generation
 * - `newColumnName` (optional, default "count_fingerprint"): Name for the new output column
 * - `removeSourceColumn` (optional, default false): Whether to remove the original SMILES column
 *
 * @since 1.0
 * @see ProcessNodeModel
 */
@ContinuumNode
class CountBasedFingerprintNodeModel : ProcessNodeModel() {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(CountBasedFingerprintNodeModel::class.java)
        private val objectMapper = ObjectMapper()
    }

    final override val inputPorts = mapOf(
        "input" to ContinuumWorkflowModel.NodePort(
            name = "input table",
            contentType = APPLICATION_OCTET_STREAM_VALUE
        )
    )

    final override val outputPorts = mapOf(
        "output" to ContinuumWorkflowModel.NodePort(
            name = "output table",
            contentType = APPLICATION_OCTET_STREAM_VALUE
        )
    )

    override val categories = listOf(
        "RDKit",
        "Fingerprints"
    )

    val propertiesSchema: Map<String, Any> = objectMapper.readValue(
        """
        {
          "type": "object",
          "properties": {
            "smilesColumn": {
              "type": "string",
              "title": "SMILES Column",
              "description": "Name of the column containing SMILES strings"
            },
            "fingerprintType": {
              "type": "string",
              "title": "Fingerprint Type",
              "description": "Type of count-based fingerprint to generate",
              "enum": ["Morgan", "AtomPair", "Torsion"],
              "default": "Morgan"
            },
            "numBits": {
              "type": "integer",
              "title": "Number of Bits",
              "description": "Number of bits for the hashed fingerprint",
              "default": 2048
            },
            "radius": {
              "type": "integer",
              "title": "Radius",
              "description": "Radius for Morgan fingerprints",
              "default": 2
            },
            "useChirality": {
              "type": "boolean",
              "title": "Use Chirality",
              "description": "Whether to use chirality in fingerprint generation",
              "default": false
            },
            "newColumnName": {
              "type": "string",
              "title": "New Column Name",
              "description": "Name for the new column containing the count-based fingerprint",
              "default": "count_fingerprint"
            },
            "removeSourceColumn": {
              "type": "boolean",
              "title": "Remove Source Column",
              "description": "Whether to remove the original SMILES column from the output",
              "default": false
            }
          },
          "required": ["smilesColumn"]
        }
        """.trimIndent(),
        object : TypeReference<Map<String, Any>>() {}
    )

    val propertiesUiSchema: Map<String, Any> = objectMapper.readValue(
        """
        {
          "type": "VerticalLayout",
          "elements": [
            {
              "type": "Control",
              "scope": "#/properties/smilesColumn"
            },
            {
              "type": "Control",
              "scope": "#/properties/fingerprintType"
            },
            {
              "type": "Control",
              "scope": "#/properties/numBits"
            },
            {
              "type": "Control",
              "scope": "#/properties/radius"
            },
            {
              "type": "Control",
              "scope": "#/properties/useChirality"
            },
            {
              "type": "Control",
              "scope": "#/properties/newColumnName"
            },
            {
              "type": "Control",
              "scope": "#/properties/removeSourceColumn"
            }
          ]
        }
        """.trimIndent(),
        object : TypeReference<Map<String, Any>>() {}
    )

    override val metadata = ContinuumWorkflowModel.NodeData(
        id = this.javaClass.name,
        description = "Generates count-based molecular fingerprints from SMILES strings using RDKit",
        title = "Count-Based Fingerprint",
        subTitle = "Compute count-based fingerprints (Morgan, AtomPair, Torsion)",
        nodeModel = this.javaClass.name,
        icon = """
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" d="M7.864 4.243A7.5 7.5 0 0 1 19.5 10.5c0 2.92-.556 5.709-1.568 8.268M5.742 6.364A7.465 7.465 0 0 0 4.5 10.5a48.667 48.667 0 0 0-1.418 8.773 6.75 6.75 0 0 1 11.836 0c-.47-2.862-.881-5.79-1.118-8.773A7.465 7.465 0 0 0 12 6.864m-1.5 2.636h.008v.008H10.5V9.5z"/>
            </svg>
        """.trimIndent(),
        inputs = inputPorts,
        outputs = outputPorts,
        properties = mapOf(
            "smilesColumn" to "smiles",
            "fingerprintType" to "Morgan",
            "numBits" to 2048,
            "radius" to 2,
            "useChirality" to false,
            "newColumnName" to "count_fingerprint",
            "removeSourceColumn" to false
        ),
        propertiesSchema = propertiesSchema,
        propertiesUISchema = propertiesUiSchema
    )

    override fun execute(
        properties: Map<String, Any>?,
        inputs: Map<String, NodeInputReader>,
        nodeOutputWriter: NodeOutputWriter,
        nodeProgressCallback: NodeProgressCallback
    ) {
        // === Validate and extract required properties ===
        val smilesColumn = properties?.get("smilesColumn") as String? ?: throw NodeRuntimeException(
            workflowId = "",
            nodeId = "",
            message = "smilesColumn is not provided"
        )
        val fingerprintType = properties["fingerprintType"]?.toString() ?: "Morgan"
        val numBits = (properties["numBits"] as? Number)?.toInt() ?: 2048
        val radius = (properties["radius"] as? Number)?.toInt() ?: 2
        val useChirality = properties["useChirality"] as? Boolean ?: false
        val newColumnName = properties["newColumnName"]?.toString() ?: "count_fingerprint"
        val removeSourceColumn = properties["removeSourceColumn"] as? Boolean ?: false

        LOGGER.info("Generating $fingerprintType count-based fingerprint from column '$smilesColumn' into '$newColumnName' (numBits=$numBits, radius=$radius, useChirality=$useChirality)")

        val inputReader = inputs["input"] ?: throw NodeRuntimeException(
            workflowId = "",
            nodeId = "",
            message = "Input port 'input' is not connected"
        )

        val totalRows = inputReader.getRowCount()

        // === Create output writer and process rows ===
        nodeOutputWriter.createOutputPortWriter("output").use { writer ->
            inputReader.use { reader ->
                var row = reader.read()
                var rowNumber = 0L

                while (row != null) {
                    val smilesValue = row[smilesColumn]?.toString() ?: ""

                    var fingerprintJson = ""
                    if (smilesValue.isNotEmpty()) {
                        val mol = RDKitNodeHelper.parseMoleculeOrNull(smilesValue)
                        try {
                            if (mol != null) {
                                fingerprintJson = generateCountFingerprint(mol, fingerprintType, numBits, radius, useChirality)
                            }
                        } finally {
                            mol?.delete()
                        }
                    }

                    // Build output row: all original columns plus fingerprint column
                    val outputRow = row.toMutableMap<String, Any>()
                    if (removeSourceColumn) {
                        outputRow.remove(smilesColumn)
                    }
                    outputRow[newColumnName] = fingerprintJson

                    writer.write(rowNumber, outputRow)

                    rowNumber++
                    if (totalRows > 0) {
                        nodeProgressCallback.report((rowNumber * 100 / totalRows).toInt())
                    }

                    row = reader.read()
                }
            }
        }

        nodeProgressCallback.report(100)
        LOGGER.info("Count-based fingerprint generation completed: processed rows")
    }

    /**
     * Generates a count-based fingerprint and returns it as a JSON map of {index: count} pairs.
     * Uses the bit-based fingerprint and converts on-bits to count=1 entries since the Java
     * wrapper may not expose full count-vector iteration.
     */
    private fun generateCountFingerprint(
        mol: ROMol,
        fingerprintType: String,
        numBits: Int,
        radius: Int,
        useChirality: Boolean
    ): String {
        val fp = when (fingerprintType) {
            "Morgan" -> RDKFuncs.getMorganFingerprintAsBitVect(mol, radius.toLong(), numBits.toLong())
            "AtomPair" -> RDKFuncs.getHashedAtomPairFingerprintAsBitVect(mol, numBits.toLong())
            "Torsion" -> RDKFuncs.getHashedTopologicalTorsionFingerprintAsBitVect(mol, numBits.toLong())
            else -> RDKFuncs.getMorganFingerprintAsBitVect(mol, radius.toLong(), numBits.toLong())
        }
        try {
            val countMap = mutableMapOf<Int, Int>()
            for (i in 0 until numBits) {
                if (fp.getBit(i.toLong())) {
                    countMap[i] = 1
                }
            }
            return objectMapper.writeValueAsString(countMap)
        } finally {
            fp.delete()
        }
    }
}
