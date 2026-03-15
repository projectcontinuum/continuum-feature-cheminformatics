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
 * Chemical Transformation Node Model
 *
 * Applies a set of chemical transformations (reaction SMARTS) iteratively to each molecule.
 * Reads all reactions from the "reactions" input port, then for each molecule in the "molecules"
 * input port, applies the reactions sequentially for up to maxReactionCycles iterations. In each
 * cycle, for each reaction, if products are generated the first product is sanitized and used as
 * input for the next cycle. The final transformed SMILES is written to the output.
 *
 * **Input Ports:**
 * - `molecules`: Table containing a column with SMILES strings
 * - `reactions`: Table containing a column with reaction SMARTS strings
 *
 * **Output Ports:**
 * - `output`: Table with all original columns plus (or replacing) a column with the transformed SMILES
 *
 * **Configuration Properties:**
 * - `smilesColumn` (required): Name of the SMILES column in the molecules table
 * - `reactionSmartsColumn` (required): Name of the reaction SMARTS column in the reactions table
 * - `newColumnName` (optional, default "transformed_smiles"): Name of the output column for transformed SMILES
 * - `removeSourceColumn` (optional, default false): Whether to remove the original SMILES column from the output
 * - `maxReactionCycles` (optional, default 1, min 1, max 100): Maximum number of iterative reaction cycles
 *
 * @since 1.0
 * @see ProcessNodeModel
 */
@ContinuumNode
class ChemicalTransformationNodeModel : ProcessNodeModel() {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(ChemicalTransformationNodeModel::class.java)
        private val objectMapper = ObjectMapper()
    }

    final override val inputPorts = mapOf(
        "molecules" to ContinuumWorkflowModel.NodePort(
            name = "molecules table",
            contentType = APPLICATION_OCTET_STREAM_VALUE
        ),
        "reactions" to ContinuumWorkflowModel.NodePort(
            name = "reactions table",
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
        "Reactions"
    )

    val propertiesSchema: Map<String, Any> = objectMapper.readValue(
        """
        {
          "type": "object",
          "properties": {
            "smilesColumn": {
              "type": "string",
              "title": "SMILES Column",
              "description": "Name of the column containing SMILES strings in the molecules table"
            },
            "reactionSmartsColumn": {
              "type": "string",
              "title": "Reaction SMARTS Column",
              "description": "Name of the column containing reaction SMARTS strings in the reactions table"
            },
            "newColumnName": {
              "type": "string",
              "title": "New Column Name",
              "description": "Name of the output column for the transformed SMILES",
              "default": "transformed_smiles"
            },
            "removeSourceColumn": {
              "type": "boolean",
              "title": "Remove Source Column",
              "description": "Whether to remove the original SMILES column from the output",
              "default": false
            },
            "maxReactionCycles": {
              "type": "integer",
              "title": "Max Reaction Cycles",
              "description": "Maximum number of iterative reaction cycles to apply",
              "default": 1,
              "minimum": 1,
              "maximum": 100
            }
          },
          "required": ["smilesColumn", "reactionSmartsColumn"]
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
              "scope": "#/properties/reactionSmartsColumn"
            },
            {
              "type": "Control",
              "scope": "#/properties/newColumnName"
            },
            {
              "type": "Control",
              "scope": "#/properties/removeSourceColumn"
            },
            {
              "type": "Control",
              "scope": "#/properties/maxReactionCycles"
            }
          ]
        }
        """.trimIndent(),
        object : TypeReference<Map<String, Any>>() {}
    )

    override val metadata = ContinuumWorkflowModel.NodeData(
        id = this.javaClass.name,
        description = "Applies chemical transformations iteratively to molecules using RDKit reaction SMARTS",
        title = "Chemical Transformation",
        subTitle = "Iteratively transform molecules using reaction SMARTS",
        nodeModel = this.javaClass.name,
        icon = """
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" d="M16.023 9.348h4.992v-.001M2.985 19.644v-4.992m0 0h4.992m-4.993 0 3.181 3.183a8.25 8.25 0 0 0 13.803-3.7M4.031 9.865a8.25 8.25 0 0 1 13.803-3.7l3.181 3.182"/>
            </svg>
        """.trimIndent(),
        inputs = inputPorts,
        outputs = outputPorts,
        properties = mapOf(
            "smilesColumn" to "smiles",
            "reactionSmartsColumn" to "transform",
            "newColumnName" to "transformed_smiles",
            "removeSourceColumn" to false,
            "maxReactionCycles" to 1
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
        val reactionSmartsColumn = properties["reactionSmartsColumn"] as String? ?: throw NodeRuntimeException(
            workflowId = "",
            nodeId = "",
            message = "reactionSmartsColumn is not provided"
        )
        val newColumnName = properties["newColumnName"]?.toString() ?: "transformed_smiles"
        val removeSourceColumn = properties["removeSourceColumn"] as? Boolean ?: false
        val maxReactionCycles = (properties["maxReactionCycles"] as? Number)?.toInt() ?: 1

        if (maxReactionCycles < 1 || maxReactionCycles > 100) {
            throw NodeRuntimeException(
                workflowId = "",
                nodeId = "",
                message = "maxReactionCycles must be between 1 and 100"
            )
        }

        LOGGER.info("Chemical transformation: SMILES column '$smilesColumn', reaction SMARTS column '$reactionSmartsColumn', maxCycles=$maxReactionCycles")

        val moleculesReader = inputs["molecules"] ?: throw NodeRuntimeException(
            workflowId = "",
            nodeId = "",
            message = "Input port 'molecules' is not connected"
        )
        val reactionsReader = inputs["reactions"] ?: throw NodeRuntimeException(
            workflowId = "",
            nodeId = "",
            message = "Input port 'reactions' is not connected"
        )

        // === Read ALL reactions first ===
        val reactions = mutableListOf<ChemicalReaction>()
        try {
            reactionsReader.use { reader ->
                var row = reader.read()
                while (row != null) {
                    val smartsValue = row[reactionSmartsColumn]?.toString() ?: ""
                    if (smartsValue.isNotEmpty()) {
                        val reaction = ChemicalReaction.ReactionFromSmarts(smartsValue)
                        if (reaction != null) {
                            reaction.initReactantMatchers()
                            reactions.add(reaction)
                        }
                    }
                    row = reader.read()
                }
            }

            nodeProgressCallback.report(20)

            if (reactions.isEmpty()) {
                LOGGER.warn("No valid reactions found — molecules will be passed through unchanged")
            }

            val totalRows = moleculesReader.getRowCount()

            // === Process each molecule ===
            nodeOutputWriter.createOutputPortWriter("output").use { writer ->
                moleculesReader.use { reader ->
                    var row = reader.read()
                    var rowNumber = 0L

                    while (row != null) {
                        val smilesValue = row[smilesColumn]?.toString() ?: ""
                        var transformedSmiles = smilesValue

                        if (smilesValue.isNotEmpty() && reactions.isNotEmpty()) {
                            transformedSmiles = applyTransformations(smilesValue, reactions, maxReactionCycles)
                        }

                        // Build output row
                        val outputRow = row.toMutableMap<String, Any>()
                        if (removeSourceColumn) {
                            outputRow.remove(smilesColumn)
                        }
                        outputRow[newColumnName] = transformedSmiles

                        writer.write(rowNumber, outputRow)

                        rowNumber++
                        if (totalRows > 0) {
                            nodeProgressCallback.report((20 + rowNumber * 80 / totalRows).toInt())
                        }

                        row = reader.read()
                    }
                }
            }
        } finally {
            // === Clean up reactions ===
            for (reaction in reactions) {
                reaction.delete()
            }
        }

        nodeProgressCallback.report(100)
        LOGGER.info("Chemical transformation completed")
    }

    /**
     * Applies the list of reactions iteratively to a molecule for up to maxCycles iterations.
     * In each cycle, for each reaction, if products exist the first product is sanitized and
     * used as input for the next iteration. Returns the final transformed SMILES string.
     */
    private fun applyTransformations(
        inputSmiles: String,
        reactions: List<ChemicalReaction>,
        maxCycles: Int
    ): String {
        var currentSmiles = inputSmiles

        for (cycle in 0 until maxCycles) {
            var changed = false

            for (reaction in reactions) {
                val mol = RDKFuncs.SmilesToMol(currentSmiles)
                if (mol == null) return currentSmiles

                try {
                    val reactantVect = ROMol_Vect()
                    try {
                        reactantVect.add(mol)
                        val productSets = reaction.runReactants(reactantVect)
                        try {
                            if (productSets.size() > 0) {
                                val firstSet = productSets.get(0)
                                if (firstSet.size() > 0) {
                                    val product = firstSet.get(0)
                                    try {
                                        val rwProduct = RWMol(product)
                                        try {
                                            RDKFuncs.sanitizeMol(rwProduct)
                                        } finally {
                                            rwProduct.delete()
                                        }
                                        val productSmiles = product.MolToSmiles()
                                        if (productSmiles.isNotEmpty() && productSmiles != currentSmiles) {
                                            currentSmiles = productSmiles
                                            changed = true
                                        }
                                    } catch (e: Exception) {
                                        // Sanitization failed, keep current SMILES
                                        LOGGER.debug("Product sanitization failed in cycle $cycle: ${e.message}")
                                    } finally {
                                        product.delete()
                                    }
                                }
                            }
                        } finally {
                            productSets.delete()
                        }
                    } finally {
                        reactantVect.delete()
                    }
                } finally {
                    mol.delete()
                }
            }

            // If no reaction changed the molecule in this cycle, stop early
            if (!changed) break
        }

        return currentSmiles
    }
}
