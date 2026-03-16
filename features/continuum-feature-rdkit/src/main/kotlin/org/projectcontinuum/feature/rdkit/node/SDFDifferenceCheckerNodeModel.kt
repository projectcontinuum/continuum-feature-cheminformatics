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
 * SDF Difference Checker Node Model
 *
 * Compares two tables of molecules row by row (index-matched), identifying differences in
 * canonical SMILES, molecular properties, and optionally 3D coordinates. This is a QA/validation
 * node for regression testing of cheminformatics pipelines.
 *
 * **Input Ports:**
 * - `left`: Left table containing molecules (SMILES or MolBlock)
 * - `right`: Right table containing molecules (SMILES or MolBlock)
 *
 * **Output Ports:**
 * - `output`: Comparison results with match status and difference descriptions
 *
 * **Configuration Properties:**
 * - `leftSmilesColumn` (required): SMILES/MolBlock column in left table
 * - `rightSmilesColumn` (required): SMILES/MolBlock column in right table
 * - `compareCoordinates` (optional, default false): Also compare 2D/3D coordinates
 * - `compareProperties` (optional, default true): Compare molecular properties (MW, formula)
 * - `toleranceForCoordinates` (optional, default 0.001): Tolerance for coordinate comparison
 * - `outputDifferencesOnly` (optional, default true): Only output rows with differences
 * - `differenceColumnName` (optional, default "differences"): Column name for difference description
 * - `matchColumnName` (optional, default "match_status"): Column name for match status
 *
 * @since 1.0
 * @see ProcessNodeModel
 */
@ContinuumNode
class SDFDifferenceCheckerNodeModel : ProcessNodeModel() {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(SDFDifferenceCheckerNodeModel::class.java)
        private val objectMapper = ObjectMapper()
    }

    final override val inputPorts = mapOf(
        "left" to ContinuumWorkflowModel.NodePort(
            name = "left table",
            contentType = APPLICATION_OCTET_STREAM_VALUE
        ),
        "right" to ContinuumWorkflowModel.NodePort(
            name = "right table",
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
        "RDKit/Testing"
    )

    val propertiesSchema: Map<String, Any> = objectMapper.readValue(
        """
        {
          "type": "object",
          "properties": {
            "leftSmilesColumn": {
              "type": "string",
              "title": "Left SMILES Column",
              "description": "Name of the SMILES or MolBlock column in the left table"
            },
            "rightSmilesColumn": {
              "type": "string",
              "title": "Right SMILES Column",
              "description": "Name of the SMILES or MolBlock column in the right table"
            },
            "compareCoordinates": {
              "type": "boolean",
              "title": "Compare Coordinates",
              "description": "Also compare 2D/3D coordinates",
              "default": false
            },
            "compareProperties": {
              "type": "boolean",
              "title": "Compare Properties",
              "description": "Compare molecular properties (MW, formula)",
              "default": true
            },
            "toleranceForCoordinates": {
              "type": "number",
              "title": "Coordinate Tolerance",
              "description": "Tolerance for coordinate comparison",
              "default": 0.001
            },
            "outputDifferencesOnly": {
              "type": "boolean",
              "title": "Output Differences Only",
              "description": "If true, only output rows with differences",
              "default": true
            },
            "differenceColumnName": {
              "type": "string",
              "title": "Difference Column Name",
              "description": "Name for the column containing difference descriptions",
              "default": "differences"
            },
            "matchColumnName": {
              "type": "string",
              "title": "Match Column Name",
              "description": "Name for the column containing match status",
              "default": "match_status"
            }
          },
          "required": ["leftSmilesColumn", "rightSmilesColumn"]
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
              "scope": "#/properties/leftSmilesColumn"
            },
            {
              "type": "Control",
              "scope": "#/properties/rightSmilesColumn"
            },
            {
              "type": "Control",
              "scope": "#/properties/compareCoordinates"
            },
            {
              "type": "Control",
              "scope": "#/properties/compareProperties"
            },
            {
              "type": "Control",
              "scope": "#/properties/toleranceForCoordinates"
            },
            {
              "type": "Control",
              "scope": "#/properties/outputDifferencesOnly"
            },
            {
              "type": "Control",
              "scope": "#/properties/differenceColumnName"
            },
            {
              "type": "Control",
              "scope": "#/properties/matchColumnName"
            }
          ]
        }
        """.trimIndent(),
        object : TypeReference<Map<String, Any>>() {}
    )

    override val metadata = ContinuumWorkflowModel.NodeData(
        id = this.javaClass.name,
        description = "Compares two tables of molecules row by row, identifying SMILES, property, and coordinate differences",
        title = "SDF Difference Checker",
        subTitle = "Compare two molecule tables for QA and regression testing",
        nodeModel = this.javaClass.name,
        icon = """
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" d="M7.5 21 3 16.5m0 0L7.5 12M3 16.5h13.5m0-13.5L21 7.5m0 0L16.5 12M21 7.5H7.5"/>
            </svg>
        """.trimIndent(),
        inputs = inputPorts,
        outputs = outputPorts,
        properties = mapOf(
            "leftSmilesColumn" to "smiles",
            "rightSmilesColumn" to "smiles",
            "compareCoordinates" to false,
            "compareProperties" to true,
            "toleranceForCoordinates" to 0.001,
            "outputDifferencesOnly" to false,
            "differenceColumnName" to "differences",
            "matchColumnName" to "match_status"
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
        val leftSmilesColumn = properties?.get("leftSmilesColumn") as String? ?: throw NodeRuntimeException(
            workflowId = "",
            nodeId = "",
            message = "leftSmilesColumn is not provided"
        )
        val rightSmilesColumn = properties["rightSmilesColumn"] as String? ?: throw NodeRuntimeException(
            workflowId = "",
            nodeId = "",
            message = "rightSmilesColumn is not provided"
        )
        val compareCoordinates = properties["compareCoordinates"] as? Boolean ?: false
        val compareProperties = properties["compareProperties"] as? Boolean ?: true
        val toleranceForCoordinates = (properties["toleranceForCoordinates"] as? Number)?.toDouble() ?: 0.001
        val outputDifferencesOnly = properties["outputDifferencesOnly"] as? Boolean ?: true
        val differenceColumnName = properties["differenceColumnName"]?.toString() ?: "differences"
        val matchColumnName = properties["matchColumnName"]?.toString() ?: "match_status"

        LOGGER.info("SDF Difference Checker: left='$leftSmilesColumn', right='$rightSmilesColumn', compareCoords=$compareCoordinates, compareProps=$compareProperties")

        val leftReader = inputs["left"] ?: throw NodeRuntimeException(
            workflowId = "",
            nodeId = "",
            message = "Input port 'left' is not connected"
        )
        val rightReader = inputs["right"] ?: throw NodeRuntimeException(
            workflowId = "",
            nodeId = "",
            message = "Input port 'right' is not connected"
        )

        // Read ALL rows from both tables
        val leftRows = mutableListOf<Map<String, Any>>()
        leftReader.use { reader ->
            var row = reader.read()
            while (row != null) {
                leftRows.add(row)
                row = reader.read()
            }
        }

        val rightRows = mutableListOf<Map<String, Any>>()
        rightReader.use { reader ->
            var row = reader.read()
            while (row != null) {
                rightRows.add(row)
                row = reader.read()
            }
        }

        nodeProgressCallback.report(20)

        val maxRows = maxOf(leftRows.size, rightRows.size)

        nodeOutputWriter.createOutputPortWriter("output").use { writer ->
            var outputRowNumber = 0L

            for (i in 0 until maxRows) {
                val leftRow = if (i < leftRows.size) leftRows[i] else null
                val rightRow = if (i < rightRows.size) rightRows[i] else null

                val leftSmiles = leftRow?.get(leftSmilesColumn)?.toString() ?: ""
                val rightSmiles = rightRow?.get(rightSmilesColumn)?.toString() ?: ""

                val differences = mutableListOf<String>()
                val matchStatus: String

                when {
                    leftRow != null && rightRow == null -> {
                        matchStatus = "left_only"
                        differences.add("No corresponding row in right table")
                    }
                    leftRow == null && rightRow != null -> {
                        matchStatus = "right_only"
                        differences.add("No corresponding row in left table")
                    }
                    leftRow != null && rightRow != null -> {
                        // Compare canonical SMILES using memory-safe helpers
                        var leftCanonical = ""
                        var rightCanonical = ""

                        if (leftSmiles.isNotEmpty()) {
                            leftCanonical = RDKitNodeHelper.withMoleculeOrMolBlock(leftSmiles) { mol ->
                                RDKFuncs.MolToSmiles(mol)
                            } ?: ""
                        }
                        if (rightSmiles.isNotEmpty()) {
                            rightCanonical = RDKitNodeHelper.withMoleculeOrMolBlock(rightSmiles) { mol ->
                                RDKFuncs.MolToSmiles(mol)
                            } ?: ""
                        }

                        if (leftCanonical != rightCanonical) {
                            differences.add("SMILES differ: $leftCanonical vs $rightCanonical")
                        }

                        // Compare properties
                        if (compareProperties && leftSmiles.isNotEmpty() && rightSmiles.isNotEmpty()) {
                            RDKitNodeHelper.withMoleculeOrMolBlock(leftSmiles) { leftMol ->
                                RDKitNodeHelper.withMoleculeOrMolBlock(rightSmiles) { rightMol ->
                                    val leftMW = RDKFuncs.calcAMW(leftMol, false)
                                    val rightMW = RDKFuncs.calcAMW(rightMol, false)
                                    if (Math.abs(leftMW - rightMW) > 0.01) {
                                        differences.add("MW differ: %.2f vs %.2f".format(leftMW, rightMW))
                                    }

                                    val leftFormula = RDKFuncs.getMolFormula(leftMol)
                                    val rightFormula = RDKFuncs.getMolFormula(rightMol)
                                    if (leftFormula != rightFormula) {
                                        differences.add("Formula differ: $leftFormula vs $rightFormula")
                                    }
                                }
                            }
                        }

                        // Compare coordinates
                        if (compareCoordinates && leftSmiles.isNotEmpty() && rightSmiles.isNotEmpty()) {
                            RDKitNodeHelper.withMoleculeOrMolBlock(leftSmiles) { leftMol ->
                                RDKitNodeHelper.withMoleculeOrMolBlock(rightSmiles) { rightMol ->
                                    if (leftMol.getNumConformers() > 0 && rightMol.getNumConformers() > 0) {
                                        val leftConf = leftMol.getConformer(0)
                                        val rightConf = rightMol.getConformer(0)
                                        val numAtoms = minOf(leftMol.getNumAtoms(), rightMol.getNumAtoms()).toInt()

                                        for (atomIdx in 0 until numAtoms) {
                                            val leftPos = leftConf.getAtomPos(atomIdx.toLong())
                                            val rightPos = rightConf.getAtomPos(atomIdx.toLong())
                                            val dx = leftPos.x - rightPos.x
                                            val dy = leftPos.y - rightPos.y
                                            val dz = leftPos.z - rightPos.z
                                            val dist = Math.sqrt(dx * dx + dy * dy + dz * dz)
                                            if (dist > toleranceForCoordinates) {
                                                differences.add("Atom $atomIdx coordinates differ by %.4f".format(dist))
                                            }
                                        }
                                    } else {
                                        if (leftMol.getNumConformers() == 0L || rightMol.getNumConformers() == 0L) {
                                            differences.add("Coordinate comparison requested but one or both molecules lack conformers")
                                        }
                                    }
                                }
                            }
                        }

                        matchStatus = if (differences.isEmpty()) "match" else "mismatch"
                    }
                    else -> {
                        matchStatus = "match"
                    }
                }

                // Decide whether to write this row
                if (!outputDifferencesOnly || matchStatus != "match") {
                    val outputRow = mutableMapOf<String, Any>()

                    // Include columns from both tables
                    if (leftRow != null) {
                        for ((key, value) in leftRow) {
                            outputRow["left_$key"] = value
                        }
                    }
                    if (rightRow != null) {
                        for ((key, value) in rightRow) {
                            outputRow["right_$key"] = value
                        }
                    }

                    outputRow[matchColumnName] = matchStatus
                    outputRow[differenceColumnName] = differences.joinToString("; ")

                    writer.write(outputRowNumber, outputRow)
                    outputRowNumber++
                }

                if (maxRows > 0) {
                    nodeProgressCallback.report((20 + (i + 1) * 80 / maxRows).toInt())
                }
            }
        }

        nodeProgressCallback.report(100)
        LOGGER.info("SDF Difference Checker completed")
    }
}




