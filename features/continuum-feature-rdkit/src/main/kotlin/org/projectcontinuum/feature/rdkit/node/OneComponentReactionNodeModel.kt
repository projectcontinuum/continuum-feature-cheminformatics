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
import org.projectcontinuum.feature.rdkit.util.RDKitNodeHelper

/**
 * One Component Reaction Node Model
 *
 * Runs one-component chemical reactions using RDKit. Reads all reactions from the "reaction"
 * input port and applies each to every reactant molecule from the "reactants" input port.
 * This is a row-expansion node: each reactant may produce multiple products across multiple
 * reactions, and each product is written as a separate output row.
 *
 * **Input Ports:**
 * - `reactants`: Table containing a column with reactant SMILES strings
 * - `reaction`: Table containing a column with reaction SMARTS strings
 *
 * **Output Ports:**
 * - `output`: Table with one row per product, including product SMILES, product index, reactant index, and optional reactant columns
 *
 * **Configuration Properties:**
 * - `reactantSmilesColumn` (required): Name of the SMILES column in the reactants table
 * - `reactionSmartsColumn` (required): Name of the reaction SMARTS column in the reaction table
 * - `productColumnName` (optional, default "product_smiles"): Name of the output column for product SMILES
 * - `productIndexColumnName` (optional, default "product_index"): Name of the output column for product index
 * - `reactantIndexColumnName` (optional, default "reactant_index"): Name of the output column for reactant index
 * - `includeReactantColumns` (optional, default true): Whether to include original reactant columns in the output
 *
 * @since 1.0
 * @see ProcessNodeModel
 */
@ContinuumNode
class OneComponentReactionNodeModel : ProcessNodeModel() {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(OneComponentReactionNodeModel::class.java)
        private val objectMapper = ObjectMapper()
    }

    final override val inputPorts = mapOf(
        "reactants" to ContinuumWorkflowModel.NodePort(
            name = "reactants table",
            contentType = APPLICATION_OCTET_STREAM_VALUE
        ),
        "reaction" to ContinuumWorkflowModel.NodePort(
            name = "reaction table",
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
            "reactantSmilesColumn": {
              "type": "string",
              "title": "Reactant SMILES Column",
              "description": "Name of the column containing reactant SMILES strings"
            },
            "reactionSmartsColumn": {
              "type": "string",
              "title": "Reaction SMARTS Column",
              "description": "Name of the column containing reaction SMARTS strings"
            },
            "productColumnName": {
              "type": "string",
              "title": "Product Column Name",
              "description": "Name of the output column for product SMILES",
              "default": "product_smiles"
            },
            "productIndexColumnName": {
              "type": "string",
              "title": "Product Index Column Name",
              "description": "Name of the output column for the product index within a product set",
              "default": "product_index"
            },
            "reactantIndexColumnName": {
              "type": "string",
              "title": "Reactant Index Column Name",
              "description": "Name of the output column for the reactant row index",
              "default": "reactant_index"
            },
            "includeReactantColumns": {
              "type": "boolean",
              "title": "Include Reactant Columns",
              "description": "Whether to include the original reactant columns in the output",
              "default": true
            }
          },
          "required": ["reactantSmilesColumn", "reactionSmartsColumn"]
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
              "scope": "#/properties/reactantSmilesColumn"
            },
            {
              "type": "Control",
              "scope": "#/properties/reactionSmartsColumn"
            },
            {
              "type": "Control",
              "scope": "#/properties/productColumnName"
            },
            {
              "type": "Control",
              "scope": "#/properties/productIndexColumnName"
            },
            {
              "type": "Control",
              "scope": "#/properties/reactantIndexColumnName"
            },
            {
              "type": "Control",
              "scope": "#/properties/includeReactantColumns"
            }
          ]
        }
        """.trimIndent(),
        object : TypeReference<Map<String, Any>>() {}
    )

    override val metadata = ContinuumWorkflowModel.NodeData(
        id = this.javaClass.name,
        description = "Runs one-component chemical reactions on reactant molecules using RDKit reaction SMARTS",
        title = "One Component Reaction",
        subTitle = "Apply single-reactant reactions to molecules",
        nodeModel = this.javaClass.name,
        icon = """
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" d="M9.75 3.104v5.714a2.25 2.25 0 0 1-.659 1.591L5 14.5M9.75 3.104c-.251.023-.501.05-.75.082m.75-.082a24.301 24.301 0 0 1 4.5 0m0 0v5.714a2.25 2.25 0 0 0 .659 1.591L19 14.5m-4.75-11.396c.251.023.501.05.75.082M19 14.5l-1.5 6.5H6.5L5 14.5m14 0H5"/>
            </svg>
        """.trimIndent(),
        inputs = inputPorts,
        outputs = outputPorts,
        properties = mapOf(
            "reactantSmilesColumn" to "smiles",
            "reactionSmartsColumn" to "reaction_smarts",
            "productColumnName" to "product_smiles",
            "productIndexColumnName" to "product_index",
            "reactantIndexColumnName" to "reactant_index",
            "includeReactantColumns" to true
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
        val reactantSmilesColumn = properties?.get("reactantSmilesColumn") as String? ?: throw NodeRuntimeException(
            workflowId = "",
            nodeId = "",
            message = "reactantSmilesColumn is not provided"
        )
        val reactionSmartsColumn = properties["reactionSmartsColumn"] as String? ?: throw NodeRuntimeException(
            workflowId = "",
            nodeId = "",
            message = "reactionSmartsColumn is not provided"
        )
        val productColumnName = properties["productColumnName"]?.toString() ?: "product_smiles"
        val productIndexColumnName = properties["productIndexColumnName"]?.toString() ?: "product_index"
        val reactantIndexColumnName = properties["reactantIndexColumnName"]?.toString() ?: "reactant_index"
        val includeReactantColumns = properties["includeReactantColumns"] as? Boolean ?: true

        LOGGER.info("One component reaction: reactants column '$reactantSmilesColumn', reaction SMARTS column '$reactionSmartsColumn'")

        val reactantsReader = inputs["reactants"] ?: throw NodeRuntimeException(
            workflowId = "",
            nodeId = "",
            message = "Input port 'reactants' is not connected"
        )
        val reactionReader = inputs["reaction"] ?: throw NodeRuntimeException(
            workflowId = "",
            nodeId = "",
            message = "Input port 'reaction' is not connected"
        )

        // === Read ALL reactions first ===
        val reactions = mutableListOf<ChemicalReaction>()
        try {
            reactionReader.use { reader ->
                var row = reader.read()
                while (row != null) {
                    val smartsValue = row[reactionSmartsColumn]?.toString() ?: ""
                    if (smartsValue.isNotEmpty()) {
                        val reaction = ChemicalReaction.ReactionFromSmarts(smartsValue)
                        if (reaction != null) {
                            reaction.initReactantMatchers()
                            if (reaction.getNumReactantTemplates() != 1.toLong()) {
                                reaction.delete()
                                throw NodeRuntimeException(
                                    workflowId = "",
                                    nodeId = "",
                                    message = "Reaction '$smartsValue' requires ${reaction.getNumReactantTemplates()} reactants, but this node supports only 1"
                                )
                            }
                            reactions.add(reaction)
                        }
                    }
                    row = reader.read()
                }
            }

            nodeProgressCallback.report(20)

            if (reactions.isEmpty()) {
                LOGGER.warn("No valid reactions found — output will be empty")
            }

            // === Process each reactant against all reactions ===
            nodeOutputWriter.createOutputPortWriter("output").use { writer ->
                reactantsReader.use { reader ->
                    var row = reader.read()
                    var reactantIndex = 0L
                    var outputRowNumber = 0L

                    while (row != null) {
                        val smilesValue = row[reactantSmilesColumn]?.toString() ?: ""
                        if (smilesValue.isNotEmpty()) {
                            val mol = RDKitNodeHelper.parseMoleculeOrNull(smilesValue)
                            if (mol != null) {
                                try {
                                    for (reaction in reactions) {
                                        val reactantVect = ROMol_Vect()
                                        try {
                                            reactantVect.add(mol)
                                            val productSets = reaction.runReactants(reactantVect)
                                            try {
                                                for (setIdx in 0 until productSets.size().toInt()) {
                                                    val productSet = productSets.get(setIdx)
                                                    for (prodIdx in 0 until productSet.size().toInt()) {
                                                        val product = productSet.get(prodIdx)
                                                        try {
                                                            val rwProduct = RWMol(product)
                                                            try {
                                                                RDKFuncs.sanitizeMol(rwProduct)
                                                            } finally {
                                                                rwProduct.delete()
                                                            }
                                                            val productSmiles = product.MolToSmiles()

                                                            val outputRow = mutableMapOf<String, Any>()
                                                            if (includeReactantColumns) {
                                                                outputRow.putAll(row)
                                                            }
                                                            outputRow[productColumnName] = productSmiles
                                                            outputRow[productIndexColumnName] = prodIdx
                                                            outputRow[reactantIndexColumnName] = reactantIndex

                                                            writer.write(outputRowNumber, outputRow)
                                                            outputRowNumber++
                                                        } catch (e: Exception) {
                                                            // Skip products that fail sanitization
                                                            LOGGER.debug("Skipping product that failed sanitization: ${e.message}")
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
                                    }
                                } finally {
                                    mol.delete()
                                }
                            }
                        }

                        reactantIndex++
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
        LOGGER.info("One component reaction completed")
    }
}
