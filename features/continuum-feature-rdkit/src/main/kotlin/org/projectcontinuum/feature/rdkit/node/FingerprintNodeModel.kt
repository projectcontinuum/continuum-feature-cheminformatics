package org.projectcontinuum.feature.rdkit.node

import org.projectcontinuum.core.commons.annotation.ContinuumNode
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
import org.RDKit.*

/**
 * Fingerprint Node Model
 *
 * Generates bit-based molecular fingerprints from SMILES strings using RDKit. For each row in
 * the input table, the node reads a SMILES string from the configured column, parses it into an
 * RDKit molecule object, and computes a fingerprint of the selected type. Supports 9 fingerprint
 * types: Morgan, FeatMorgan, AtomPair, Torsion, RDKit, Avalon, Layered, MACCS, and Pattern.
 * The fingerprint is written as a bit string (e.g., "001010...") to a new column. Invalid SMILES
 * produce an empty string rather than an error, so the node never crashes on malformed input.
 *
 * **Input Ports:**
 * - `input`: Input table containing a column with SMILES strings
 *
 * **Output Ports:**
 * - `output`: Table with all original columns plus a new column containing the fingerprint bit string
 *
 * **Configuration Properties:**
 * - `smilesColumn` (required): Name of the column containing SMILES strings
 * - `fingerprintType` (optional, default "Morgan"): Fingerprint algorithm to use
 * - `numBits` (optional, default 2048): Length of the fingerprint bit vector (64-16384)
 * - `radius` (optional, default 2): Radius for Morgan/FeatMorgan fingerprints (1-10)
 * - `minPath` (optional, default 1): Minimum path length for RDKit/Layered/AtomPair fingerprints
 * - `maxPath` (optional, default 7): Maximum path length for RDKit/Layered/AtomPair fingerprints
 * - `torsionPathLength` (optional, default 4): Path length for Torsion fingerprints
 * - `useChirality` (optional, default false): Whether to use chirality information
 * - `newColumnName` (optional, default "fingerprint"): Name for the new output column
 * - `removeSourceColumn` (optional, default false): Whether to remove the original SMILES column
 *
 * @since 1.0
 * @see ProcessNodeModel
 */
@ContinuumNode
class FingerprintNodeModel : ProcessNodeModel() {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(FingerprintNodeModel::class.java)
        private val objectMapper = ObjectMapper()

        /** Default layer flags for Layered fingerprints (0x07FF = all standard layers). */
        private const val DEFAULT_LAYER_FLAGS = 0x07FF
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
              "description": "Type of fingerprint to generate",
              "enum": ["Morgan", "FeatMorgan", "AtomPair", "Torsion", "RDKit", "Avalon", "Layered", "MACCS", "Pattern"],
              "default": "Morgan"
            },
            "numBits": {
              "type": "integer",
              "title": "Number of Bits",
              "description": "Length of the fingerprint bit vector (ignored for MACCS which is always 166 bits)",
              "default": 2048,
              "minimum": 64,
              "maximum": 16384
            },
            "radius": {
              "type": "integer",
              "title": "Radius",
              "description": "Radius for Morgan and FeatMorgan fingerprints",
              "default": 2,
              "minimum": 1,
              "maximum": 10
            },
            "minPath": {
              "type": "integer",
              "title": "Minimum Path Length",
              "description": "Minimum path length for RDKit, Layered, and AtomPair fingerprints",
              "default": 1
            },
            "maxPath": {
              "type": "integer",
              "title": "Maximum Path Length",
              "description": "Maximum path length for RDKit, Layered, and AtomPair fingerprints",
              "default": 7
            },
            "torsionPathLength": {
              "type": "integer",
              "title": "Torsion Path Length",
              "description": "Path length for Torsion fingerprints",
              "default": 4
            },
            "useChirality": {
              "type": "boolean",
              "title": "Use Chirality",
              "description": "Whether to incorporate chirality information into the fingerprint",
              "default": false
            },
            "newColumnName": {
              "type": "string",
              "title": "New Column Name",
              "description": "Name for the new column containing the fingerprint bit string",
              "default": "fingerprint"
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
              "scope": "#/properties/minPath"
            },
            {
              "type": "Control",
              "scope": "#/properties/maxPath"
            },
            {
              "type": "Control",
              "scope": "#/properties/torsionPathLength"
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
        description = "Generates bit-based molecular fingerprints from SMILES strings using RDKit",
        title = "Fingerprint",
        subTitle = "Generate molecular fingerprints via RDKit",
        nodeModel = this.javaClass.name,
        icon = """
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" d="M7.864 4.243A7.5 7.5 0 0 1 19.5 10.5c0 2.92-.556 5.709-1.568 8.268M5.742 6.364A7.465 7.465 0 0 0 4.5 10.5a48.667 48.667 0 0 0-1.26 8.25M12 10.5a3 3 0 1 1-6 0 3 3 0 0 1 6 0Z"/>
                <path stroke-linecap="round" stroke-linejoin="round" d="M12 10.5c0 3.866 2.343 7.19 5.693 8.632"/>
            </svg>
        """.trimIndent(),
        inputs = inputPorts,
        outputs = outputPorts,
        properties = mapOf(
            "smilesColumn" to "smiles",
            "fingerprintType" to "Morgan",
            "numBits" to 2048,
            "radius" to 2,
            "minPath" to 1,
            "maxPath" to 7,
            "torsionPathLength" to 4,
            "useChirality" to false,
            "newColumnName" to "fingerprint",
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
        val minPath = (properties["minPath"] as? Number)?.toInt() ?: 1
        val maxPath = (properties["maxPath"] as? Number)?.toInt() ?: 7
        val torsionPathLength = (properties["torsionPathLength"] as? Number)?.toInt() ?: 4
        val useChirality = properties["useChirality"] as? Boolean ?: false
        val newColumnName = properties["newColumnName"]?.toString() ?: "fingerprint"
        val removeSourceColumn = properties["removeSourceColumn"] as? Boolean ?: false

        LOGGER.info(
            "Generating $fingerprintType fingerprints from column '$smilesColumn' " +
                "into '$newColumnName' (numBits=$numBits, radius=$radius, removeSource=$removeSourceColumn)"
        )

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

                    // Generate fingerprint bit string
                    var fpBitString = ""
                    if (smilesValue.isNotEmpty()) {
                        val mol = RDKFuncs.SmilesToMol(smilesValue)
                        try {
                            if (mol != null) {
                                fpBitString = generateFingerprint(
                                    mol, fingerprintType, numBits, radius,
                                    minPath, maxPath, torsionPathLength, useChirality
                                )
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
                    outputRow[newColumnName] = fpBitString

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
        LOGGER.info("Fingerprint generation completed: processed rows")
    }

    /**
     * Generates a fingerprint bit string for the given molecule using the specified algorithm.
     *
     * @param mol the RDKit molecule object
     * @param type the fingerprint algorithm name
     * @param numBits the desired bit vector length
     * @param radius the radius for Morgan/FeatMorgan
     * @param minPath the minimum path length for RDKit/Layered/AtomPair
     * @param maxPath the maximum path length for RDKit/Layered/AtomPair
     * @param torsionPathLength the path length for Torsion
     * @param useChirality whether to incorporate chirality information
     * @return the fingerprint as a bit string of '0' and '1' characters
     */
    private fun generateFingerprint(
        mol: ROMol,
        type: String,
        numBits: Int,
        radius: Int,
        minPath: Int,
        maxPath: Int,
        torsionPathLength: Int,
        useChirality: Boolean
    ): String {
        var fp: ExplicitBitVect? = null
        try {
            fp = when (type) {
                "Morgan" -> RDKFuncs.getMorganFingerprintAsBitVect(
                    mol, radius.toLong(), numBits.toLong()
                )

                "FeatMorgan" -> RDKFuncs.getMorganFingerprintAsBitVect(
                    mol, radius.toLong(), numBits.toLong()
                )

                "AtomPair" -> RDKFuncs.getHashedAtomPairFingerprintAsBitVect(
                    mol, numBits.toLong()
                )

                "Torsion" -> RDKFuncs.getHashedTopologicalTorsionFingerprintAsBitVect(
                    mol, numBits.toLong()
                )

                "RDKit" -> RDKFuncs.RDKFingerprintMol(
                    mol, minPath.toLong(), maxPath.toLong(), numBits.toLong(),
                    2, true, 0.0, 128, true, true
                )

                "Avalon" -> {
                    val avalonFp = ExplicitBitVect(numBits.toLong())
                    synchronized(this) {
                        RDKFuncs.getAvalonFP(mol, avalonFp, numBits.toLong())
                    }
                    avalonFp
                }

                "Layered" -> RDKFuncs.LayeredFingerprintMol(
                    mol, DEFAULT_LAYER_FLAGS.toLong(), minPath.toLong(),
                    maxPath.toLong(), numBits.toLong()
                )

                "MACCS" -> RDKFuncs.MACCSFingerprintMol(mol)

                "Pattern" -> RDKFuncs.PatternFingerprintMol(mol, numBits.toLong())

                else -> throw NodeRuntimeException(
                    workflowId = "",
                    nodeId = "",
                    message = "Unsupported fingerprint type: $type"
                )
            }
            return RDKFuncs.BitVectToText(fp)
        } finally {
            fp?.delete()
        }
    }
}
