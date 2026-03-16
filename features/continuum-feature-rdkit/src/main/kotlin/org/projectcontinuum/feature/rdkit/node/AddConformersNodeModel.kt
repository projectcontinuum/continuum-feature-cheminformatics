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
 * Add Conformers Node Model
 *
 * Generates multiple 3D conformers for molecules from SMILES strings using RDKit distance geometry.
 * This is a row expansion node: for each input row, it produces N output rows (one per conformer).
 * Each conformer is written as a separate MolBlock along with a conformer ID column. Explicit
 * hydrogens are added before embedding, and the ETKDGv3 algorithm is used for conformer generation.
 * Invalid SMILES or molecules that fail embedding produce a single output row with empty values.
 *
 * **Input Ports:**
 * - `input`: Input table containing a column with SMILES strings
 *
 * **Output Ports:**
 * - `output`: Table with original columns plus conformer MolBlock and conformer ID columns (row expansion)
 *
 * **Configuration Properties:**
 * - `smilesColumn` (required): Name of the column containing SMILES strings
 * - `numberOfConformers` (optional, default 5): Number of conformers to generate per molecule
 * - `randomSeed` (optional, default 42): Random seed for reproducible conformer generation
 * - `newColumnName` (optional, default "conformer_mol_block"): Name for the conformer MolBlock column
 * - `conformerIdColumnName` (optional, default "conformer_id"): Name for the conformer ID column
 * - `removeSourceColumn` (optional, default false): Whether to remove the original SMILES column
 *
 * @since 1.0
 * @see ProcessNodeModel
 */
@ContinuumNode
class AddConformersNodeModel : ProcessNodeModel() {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(AddConformersNodeModel::class.java)
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
        "Geometry"
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
            "numberOfConformers": {
              "type": "integer",
              "title": "Number of Conformers",
              "description": "Number of conformers to generate per molecule",
              "default": 5,
              "minimum": 1
            },
            "randomSeed": {
              "type": "integer",
              "title": "Random Seed",
              "description": "Random seed for reproducible conformer generation",
              "default": 42
            },
            "newColumnName": {
              "type": "string",
              "title": "Conformer MolBlock Column",
              "description": "Name for the column containing the conformer MolBlock",
              "default": "conformer_mol_block"
            },
            "conformerIdColumnName": {
              "type": "string",
              "title": "Conformer ID Column",
              "description": "Name for the column containing the conformer ID",
              "default": "conformer_id"
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
              "scope": "#/properties/numberOfConformers"
            },
            {
              "type": "Control",
              "scope": "#/properties/randomSeed"
            },
            {
              "type": "Control",
              "scope": "#/properties/newColumnName"
            },
            {
              "type": "Control",
              "scope": "#/properties/conformerIdColumnName"
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
        description = "Generates multiple 3D conformers for molecules from SMILES strings using RDKit distance geometry",
        title = "Add Conformers",
        subTitle = "Generate multiple 3D conformers per molecule",
        nodeModel = this.javaClass.name,
        icon = """
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" d="M6.429 9.75L2.25 12l4.179 2.25m0-4.5l5.571 3 5.571-3m-11.142 0L2.25 7.5 12 2.25l9.75 5.25-4.179 2.25m0 0L21.75 12l-4.179 2.25m0 0L12 17.25 6.43 14.25m11.14 0l4.179 2.25L12 21.75l-9.75-5.25 4.179-2.25"/>
            </svg>
        """.trimIndent(),
        inputs = inputPorts,
        outputs = outputPorts,
        properties = mapOf(
            "smilesColumn" to "smiles",
            "numberOfConformers" to 5,
            "randomSeed" to 42,
            "newColumnName" to "conformer_mol_block",
            "conformerIdColumnName" to "conformer_id",
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
        val numberOfConformers = (properties["numberOfConformers"] as? Number)?.toInt() ?: 5
        val randomSeed = (properties["randomSeed"] as? Number)?.toInt() ?: 42
        val newColumnName = properties["newColumnName"]?.toString() ?: "conformer_mol_block"
        val conformerIdColumnName = properties["conformerIdColumnName"]?.toString() ?: "conformer_id"
        val removeSourceColumn = properties["removeSourceColumn"] as? Boolean ?: false

        LOGGER.info("Generating $numberOfConformers conformers from column '$smilesColumn' (seed=$randomSeed)")

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
                var inputRowNumber = 0L
                var outputRowNumber = 0L

                while (row != null) {
                    val smilesValue = row[smilesColumn]?.toString() ?: ""

                    val baseRow = row.toMutableMap<String, Any>()
                    if (removeSourceColumn) {
                        baseRow.remove(smilesColumn)
                    }

                    if (smilesValue.isNotEmpty()) {
                        val conformerResult = RDKitNodeHelper.withMolecule(smilesValue) { mol ->
                            RDKFuncs.addHs(mol)
                            val confIds = DistanceGeom.EmbedMultipleConfs(mol, numberOfConformers.toLong())

                            if (confIds.size() > 0) {
                                val conformers = mutableListOf<Pair<String, Int>>()
                                for (i in 0 until confIds.size().toInt()) {
                                    val confId = confIds.get(i)
                                    val molBlock = RDKFuncs.MolToMolBlock(mol, true, confId)
                                    conformers.add(Pair(molBlock, confId))
                                }
                                conformers
                            } else {
                                null
                            }
                        }

                        if (conformerResult != null && conformerResult.isNotEmpty()) {
                            for ((molBlock, confId) in conformerResult) {
                                val outputRow = baseRow.toMutableMap<String, Any>()
                                outputRow[newColumnName] = molBlock
                                outputRow[conformerIdColumnName] = confId
                                writer.write(outputRowNumber, outputRow)
                                outputRowNumber++
                            }
                        } else {
                            // Invalid SMILES or embedding failed — write single row with empty values
                            val outputRow = baseRow.toMutableMap<String, Any>()
                            outputRow[newColumnName] = ""
                            outputRow[conformerIdColumnName] = ""
                            writer.write(outputRowNumber, outputRow)
                            outputRowNumber++
                        }
                    } else {
                        // Empty SMILES — write single row with empty values
                        val outputRow = baseRow.toMutableMap<String, Any>()
                        outputRow[newColumnName] = ""
                        outputRow[conformerIdColumnName] = ""
                        writer.write(outputRowNumber, outputRow)
                        outputRowNumber++
                    }

                    inputRowNumber++
                    if (totalRows > 0) {
                        nodeProgressCallback.report((inputRowNumber * 100 / totalRows).toInt())
                    }

                    row = reader.read()
                }
            }
        }

        nodeProgressCallback.report(100)
        LOGGER.info("Conformer generation completed: processed rows")
    }
}
