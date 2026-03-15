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
 * Fingerprint Similarity Node Model
 *
 * Computes pairwise molecular similarity from two SMILES columns using RDKit fingerprints. For
 * each row in the input table, the node reads two SMILES strings from the configured columns,
 * parses them into RDKit molecule objects, generates fingerprints of the selected type (Morgan,
 * RDKit, or MACCS), and computes the similarity using the selected metric (Tanimoto or Dice).
 * Invalid SMILES in either column produce an empty string rather than an error.
 *
 * **Input Ports:**
 * - `input`: Input table containing two columns with SMILES strings
 *
 * **Output Ports:**
 * - `output`: Table with all original columns plus a new column containing the similarity score
 *
 * **Configuration Properties:**
 * - `smilesColumn1` (required): Name of the first SMILES column
 * - `smilesColumn2` (required): Name of the second SMILES column
 * - `fingerprintType` (optional, default "Morgan"): Type of fingerprint (Morgan, RDKit, MACCS)
 * - `similarityMetric` (optional, default "Tanimoto"): Similarity metric (Tanimoto, Dice)
 * - `numBits` (optional, default 2048): Number of bits for Morgan/RDKit fingerprints
 * - `radius` (optional, default 2): Radius for Morgan fingerprints
 * - `newColumnName` (optional, default "similarity"): Name for the new output column
 * - `removeSourceColumns` (optional, default false): Whether to remove the original SMILES columns
 *
 * @since 1.0
 * @see ProcessNodeModel
 */
@ContinuumNode
class FingerprintSimilarityNodeModel : ProcessNodeModel() {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(FingerprintSimilarityNodeModel::class.java)
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
            "smilesColumn1": {
              "type": "string",
              "title": "First SMILES Column",
              "description": "Name of the column containing the first SMILES strings"
            },
            "smilesColumn2": {
              "type": "string",
              "title": "Second SMILES Column",
              "description": "Name of the column containing the second SMILES strings"
            },
            "fingerprintType": {
              "type": "string",
              "title": "Fingerprint Type",
              "description": "Type of fingerprint to use for similarity computation",
              "enum": ["Morgan", "RDKit", "MACCS"],
              "default": "Morgan"
            },
            "similarityMetric": {
              "type": "string",
              "title": "Similarity Metric",
              "description": "Similarity metric to use",
              "enum": ["Tanimoto", "Dice"],
              "default": "Tanimoto"
            },
            "numBits": {
              "type": "integer",
              "title": "Number of Bits",
              "description": "Number of bits for Morgan/RDKit fingerprints (ignored for MACCS)",
              "default": 2048
            },
            "radius": {
              "type": "integer",
              "title": "Radius",
              "description": "Radius for Morgan fingerprints (ignored for other types)",
              "default": 2
            },
            "newColumnName": {
              "type": "string",
              "title": "New Column Name",
              "description": "Name for the new column containing the similarity score",
              "default": "similarity"
            },
            "removeSourceColumns": {
              "type": "boolean",
              "title": "Remove Source Columns",
              "description": "Whether to remove the original SMILES columns from the output",
              "default": false
            }
          },
          "required": ["smilesColumn1", "smilesColumn2"]
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
              "scope": "#/properties/smilesColumn1"
            },
            {
              "type": "Control",
              "scope": "#/properties/smilesColumn2"
            },
            {
              "type": "Control",
              "scope": "#/properties/fingerprintType"
            },
            {
              "type": "Control",
              "scope": "#/properties/similarityMetric"
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
              "scope": "#/properties/newColumnName"
            },
            {
              "type": "Control",
              "scope": "#/properties/removeSourceColumns"
            }
          ]
        }
        """.trimIndent(),
        object : TypeReference<Map<String, Any>>() {}
    )

    override val metadata = ContinuumWorkflowModel.NodeData(
        id = this.javaClass.name,
        description = "Computes pairwise molecular similarity from two SMILES columns using RDKit fingerprints",
        title = "Fingerprint Similarity",
        subTitle = "Compute Tanimoto/Dice similarity between molecule pairs",
        nodeModel = this.javaClass.name,
        icon = """
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" d="M7.5 21L3 16.5m0 0L7.5 12M3 16.5h13.5m0-13.5L21 7.5m0 0L16.5 12M21 7.5H7.5"/>
            </svg>
        """.trimIndent(),
        inputs = inputPorts,
        outputs = outputPorts,
        properties = mapOf(
            "smilesColumn1" to "smiles_a",
            "smilesColumn2" to "smiles_b",
            "fingerprintType" to "Morgan",
            "similarityMetric" to "Tanimoto",
            "numBits" to 2048,
            "radius" to 2,
            "newColumnName" to "similarity",
            "removeSourceColumns" to false
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
        val smilesColumn1 = properties?.get("smilesColumn1") as String? ?: throw NodeRuntimeException(
            workflowId = "",
            nodeId = "",
            message = "smilesColumn1 is not provided"
        )
        val smilesColumn2 = properties["smilesColumn2"] as String? ?: throw NodeRuntimeException(
            workflowId = "",
            nodeId = "",
            message = "smilesColumn2 is not provided"
        )
        val fingerprintType = properties["fingerprintType"]?.toString() ?: "Morgan"
        val similarityMetric = properties["similarityMetric"]?.toString() ?: "Tanimoto"
        val numBits = (properties["numBits"] as? Number)?.toInt() ?: 2048
        val radius = (properties["radius"] as? Number)?.toInt() ?: 2
        val newColumnName = properties["newColumnName"]?.toString() ?: "similarity"
        val removeSourceColumns = properties["removeSourceColumns"] as? Boolean ?: false

        LOGGER.info("Computing $similarityMetric similarity using $fingerprintType fingerprints from columns '$smilesColumn1' and '$smilesColumn2' into '$newColumnName'")

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
                    val smilesValue1 = row[smilesColumn1]?.toString() ?: ""
                    val smilesValue2 = row[smilesColumn2]?.toString() ?: ""

                    var similarityResult: Any = ""
                    if (smilesValue1.isNotEmpty() && smilesValue2.isNotEmpty()) {
                        val mol1 = RDKFuncs.SmilesToMol(smilesValue1)
                        val mol2 = RDKFuncs.SmilesToMol(smilesValue2)
                        try {
                            if (mol1 != null && mol2 != null) {
                                similarityResult = computeSimilarity(mol1, mol2, fingerprintType, similarityMetric, numBits, radius)
                            }
                        } finally {
                            mol1?.delete()
                            mol2?.delete()
                        }
                    }

                    // Build output row: all original columns plus similarity column
                    val outputRow = row.toMutableMap<String, Any>()
                    if (removeSourceColumns) {
                        outputRow.remove(smilesColumn1)
                        outputRow.remove(smilesColumn2)
                    }
                    outputRow[newColumnName] = similarityResult

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
        LOGGER.info("Fingerprint similarity computation completed: processed rows")
    }

    /**
     * Computes the similarity between two molecules using the specified fingerprint type and metric.
     */
    private fun computeSimilarity(
        mol1: ROMol,
        mol2: ROMol,
        fingerprintType: String,
        similarityMetric: String,
        numBits: Int,
        radius: Int
    ): Double {
        val fp1: ExplicitBitVect
        val fp2: ExplicitBitVect

        when (fingerprintType) {
            "RDKit" -> {
                fp1 = RDKFuncs.RDKFingerprintMol(mol1)
                fp2 = RDKFuncs.RDKFingerprintMol(mol2)
            }
            "MACCS" -> {
                fp1 = RDKFuncs.MACCSFingerprintMol(mol1)
                fp2 = RDKFuncs.MACCSFingerprintMol(mol2)
            }
            else -> {
                // Default to Morgan
                fp1 = RDKFuncs.getMorganFingerprintAsBitVect(mol1, radius.toLong(), numBits.toLong())
                fp2 = RDKFuncs.getMorganFingerprintAsBitVect(mol2, radius.toLong(), numBits.toLong())
            }
        }

        try {
            return when (similarityMetric) {
                "Dice" -> RDKFuncs.DiceSimilarity(fp1, fp2)
                else -> RDKFuncs.TanimotoSimilarity(fp1, fp2)
            }
        } finally {
            fp1.delete()
            fp2.delete()
        }
    }
}
