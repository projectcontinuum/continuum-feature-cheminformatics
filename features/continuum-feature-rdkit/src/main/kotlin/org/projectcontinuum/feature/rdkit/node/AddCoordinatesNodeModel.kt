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
 * Add Coordinates Node Model
 *
 * Generates 2D or 3D coordinates for molecules from SMILES strings using RDKit. For each row
 * in the input table, the node reads a SMILES string from the configured column, parses it into
 * an RDKit molecule object, generates coordinates in the requested dimension, and writes the
 * resulting MolBlock to a new column. In 2D mode, coordinates are computed directly on the
 * molecule. In 3D mode, explicit hydrogens are added and a distance geometry embedding is
 * performed using the ETKDGv3 algorithm. Invalid SMILES produce an empty string rather than
 * an error.
 *
 * **Input Ports:**
 * - `input`: Input table containing a column with SMILES strings
 *
 * **Output Ports:**
 * - `output`: Table with all original columns plus a new column containing the MolBlock
 *
 * **Configuration Properties:**
 * - `smilesColumn` (required): Name of the column containing SMILES strings
 * - `dimension` (optional, default "2D"): Dimension for coordinate generation ("2D" or "3D")
 * - `newColumnName` (optional, default "mol_block"): Name for the new MolBlock column
 * - `removeSourceColumn` (optional, default false): Whether to remove the original SMILES column
 *
 * @since 1.0
 * @see ProcessNodeModel
 */
@ContinuumNode
class AddCoordinatesNodeModel : ProcessNodeModel() {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(AddCoordinatesNodeModel::class.java)
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
        "RDKit/Geometry"
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
            "dimension": {
              "type": "string",
              "title": "Dimension",
              "description": "Dimension for coordinate generation",
              "enum": ["2D", "3D"],
              "default": "2D"
            },
            "newColumnName": {
              "type": "string",
              "title": "New Column Name",
              "description": "Name for the new column containing the MolBlock",
              "default": "mol_block"
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
              "scope": "#/properties/dimension"
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
        description = "Generates 2D or 3D coordinates for molecules from SMILES strings using RDKit",
        title = "Add Coordinates",
        subTitle = "Generate 2D/3D molecular coordinates via RDKit",
        nodeModel = this.javaClass.name,
        icon = """
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" d="M3.75 6.75h16.5M3.75 12h16.5m-16.5 5.25H12"/>
                <circle cx="18" cy="17.25" r="2.25"/>
            </svg>
        """.trimIndent(),
        inputs = inputPorts,
        outputs = outputPorts,
        properties = mapOf(
            "smilesColumn" to "smiles",
            "dimension" to "2D",
            "newColumnName" to "mol_block",
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
        val dimension = properties["dimension"]?.toString() ?: "2D"
        val newColumnName = properties["newColumnName"]?.toString() ?: "mol_block"
        val removeSourceColumn = properties["removeSourceColumn"] as? Boolean ?: false

        LOGGER.info("Generating $dimension coordinates from column '$smilesColumn' into '$newColumnName'")

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

                    val outputRow = row.toMutableMap<String, Any>()
                    if (removeSourceColumn) {
                        outputRow.remove(smilesColumn)
                    }

                    var molBlock: Any = ""
                    if (smilesValue.isNotEmpty()) {
                        molBlock = RDKitNodeHelper.withMolecule(smilesValue) { mol ->
                            when (dimension) {
                                "3D" -> generate3DCoords(mol)
                                else -> generate2DCoords(mol)
                            }
                        } ?: ""
                    }

                    outputRow[newColumnName] = molBlock

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
        LOGGER.info("Coordinate generation completed: processed rows")
    }

    /**
     * Generates 2D coordinates for a molecule and returns the MolBlock.
     */
    private fun generate2DCoords(mol: ROMol): String {
        mol.compute2DCoords()
        return RDKFuncs.MolToMolBlock(mol)
    }

    /**
     * Generates 3D coordinates for a molecule using ETKDGv3 embedding and returns the MolBlock.
     * Returns an empty string if embedding fails.
     */
    private fun generate3DCoords(mol: ROMol): String {
        RDKFuncs.addHs(mol)
        val params = RDKFuncs.getETKDGv3()
        val result = DistanceGeom.EmbedMolecule(mol, params)
        return if (result >= 0) {
            RDKFuncs.MolToMolBlock(mol)
        } else {
            ""
        }
    }
}
