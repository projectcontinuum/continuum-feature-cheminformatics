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
 * Two Component Reaction Node Model
 *
 * Runs two-component chemical reactions using RDKit. Reads all reactions from the "reaction"
 * input port, all second reactants from the "reactant2" input port, then processes each first
 * reactant from the "reactant1" input port. Supports both pairwise (index-matched) and matrix
 * (all combinations) modes. This is a row-expansion node: each reactant pair may produce
 * multiple products, and each product is written as a separate output row.
 *
 * **Input Ports:**
 * - `reactant1`: Table containing a column with first reactant SMILES strings
 * - `reactant2`: Table containing a column with second reactant SMILES strings
 * - `reaction`: Table containing a column with reaction SMARTS strings
 *
 * **Output Ports:**
 * - `output`: Table with one row per product, including product SMILES, product index, and optional reactant columns
 *
 * **Configuration Properties:**
 * - `reactant1SmilesColumn` (required): Name of the SMILES column in the reactant1 table
 * - `reactant2SmilesColumn` (required): Name of the SMILES column in the reactant2 table
 * - `reactionSmartsColumn` (required): Name of the reaction SMARTS column in the reaction table
 * - `matrixExpansion` (optional, default false): If true, run all r1 x r2 combinations; if false, pair by index
 * - `productColumnName` (optional, default "product_smiles"): Name of the output column for product SMILES
 * - `productIndexColumnName` (optional, default "product_index"): Name of the output column for product index
 * - `includeReactantColumns` (optional, default true): Whether to include original reactant columns in the output
 *
 * @since 1.0
 * @see ProcessNodeModel
 */
@ContinuumNode
class TwoComponentReactionNodeModel : ProcessNodeModel() {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(TwoComponentReactionNodeModel::class.java)
        private val objectMapper = ObjectMapper()
    }

    final override val inputPorts = mapOf(
        "reactant1" to ContinuumWorkflowModel.NodePort(
            name = "reactant1 table",
            contentType = APPLICATION_OCTET_STREAM_VALUE
        ),
        "reactant2" to ContinuumWorkflowModel.NodePort(
            name = "reactant2 table",
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
            "reactant1SmilesColumn": {
              "type": "string",
              "title": "Reactant 1 SMILES Column",
              "description": "Name of the column containing first reactant SMILES strings"
            },
            "reactant2SmilesColumn": {
              "type": "string",
              "title": "Reactant 2 SMILES Column",
              "description": "Name of the column containing second reactant SMILES strings"
            },
            "reactionSmartsColumn": {
              "type": "string",
              "title": "Reaction SMARTS Column",
              "description": "Name of the column containing reaction SMARTS strings"
            },
            "matrixExpansion": {
              "type": "boolean",
              "title": "Matrix Expansion",
              "description": "If true, run all reactant1 x reactant2 combinations; if false, pair by index",
              "default": false
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
            "includeReactantColumns": {
              "type": "boolean",
              "title": "Include Reactant Columns",
              "description": "Whether to include the original reactant columns in the output",
              "default": true
            }
          },
          "required": ["reactant1SmilesColumn", "reactant2SmilesColumn", "reactionSmartsColumn"]
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
              "scope": "#/properties/reactant1SmilesColumn"
            },
            {
              "type": "Control",
              "scope": "#/properties/reactant2SmilesColumn"
            },
            {
              "type": "Control",
              "scope": "#/properties/reactionSmartsColumn"
            },
            {
              "type": "Control",
              "scope": "#/properties/matrixExpansion"
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
              "scope": "#/properties/includeReactantColumns"
            }
          ]
        }
        """.trimIndent(),
        object : TypeReference<Map<String, Any>>() {}
    )

    override val metadata = ContinuumWorkflowModel.NodeData(
        id = this.javaClass.name,
        description = "Runs two-component chemical reactions on pairs of reactant molecules using RDKit reaction SMARTS",
        title = "Two Component Reaction",
        subTitle = "Apply two-reactant reactions to molecule pairs",
        nodeModel = this.javaClass.name,
        icon = """
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" d="M9.75 3.104v5.714a2.25 2.25 0 0 1-.659 1.591L5 14.5M9.75 3.104c-.251.023-.501.05-.75.082m.75-.082a24.301 24.301 0 0 1 4.5 0m0 0v5.714a2.25 2.25 0 0 0 .659 1.591L19 14.5m-4.75-11.396c.251.023.501.05.75.082M19 14.5l-1.5 6.5H6.5L5 14.5m14 0H5m7 3.5v3"/>
            </svg>
        """.trimIndent(),
        inputs = inputPorts,
        outputs = outputPorts,
        properties = mapOf(
            "reactant1SmilesColumn" to "amine",
            "reactant2SmilesColumn" to "acid",
            "reactionSmartsColumn" to "rxn_smarts",
            "matrixExpansion" to false,
            "productColumnName" to "product_smiles",
            "productIndexColumnName" to "product_index",
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
        val reactant1SmilesColumn = properties?.get("reactant1SmilesColumn") as String? ?: throw NodeRuntimeException(
            workflowId = "",
            nodeId = "",
            message = "reactant1SmilesColumn is not provided"
        )
        val reactant2SmilesColumn = properties["reactant2SmilesColumn"] as String? ?: throw NodeRuntimeException(
            workflowId = "",
            nodeId = "",
            message = "reactant2SmilesColumn is not provided"
        )
        val reactionSmartsColumn = properties["reactionSmartsColumn"] as String? ?: throw NodeRuntimeException(
            workflowId = "",
            nodeId = "",
            message = "reactionSmartsColumn is not provided"
        )
        val matrixExpansion = properties["matrixExpansion"] as? Boolean ?: false
        val productColumnName = properties["productColumnName"]?.toString() ?: "product_smiles"
        val productIndexColumnName = properties["productIndexColumnName"]?.toString() ?: "product_index"
        val includeReactantColumns = properties["includeReactantColumns"] as? Boolean ?: true

        LOGGER.info("Two component reaction: r1 column '$reactant1SmilesColumn', r2 column '$reactant2SmilesColumn', reaction column '$reactionSmartsColumn', matrix=$matrixExpansion")

        val reactant1Reader = inputs["reactant1"] ?: throw NodeRuntimeException(
            workflowId = "",
            nodeId = "",
            message = "Input port 'reactant1' is not connected"
        )
        val reactant2Reader = inputs["reactant2"] ?: throw NodeRuntimeException(
            workflowId = "",
            nodeId = "",
            message = "Input port 'reactant2' is not connected"
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
                            if (reaction.getNumReactantTemplates() != 2.toLong()) {
                                reaction.delete()
                                throw NodeRuntimeException(
                                    workflowId = "",
                                    nodeId = "",
                                    message = "Reaction '$smartsValue' requires ${reaction.getNumReactantTemplates()} reactants, but this node supports exactly 2"
                                )
                            }
                            reactions.add(reaction)
                        }
                    }
                    row = reader.read()
                }
            }

            nodeProgressCallback.report(10)

            // === Read ALL reactant2 molecules ===
            val reactant2Rows = mutableListOf<Map<String, Any>>()
            reactant2Reader.use { reader ->
                var row = reader.read()
                while (row != null) {
                    reactant2Rows.add(row)
                    row = reader.read()
                }
            }

            nodeProgressCallback.report(20)

            if (reactions.isEmpty()) {
                LOGGER.warn("No valid reactions found — output will be empty")
            }

            // === Process reactant pairs ===
            nodeOutputWriter.createOutputPortWriter("output").use { writer ->
                reactant1Reader.use { reader ->
                    var r1Row = reader.read()
                    var r1Index = 0
                    var outputRowNumber = 0L

                    while (r1Row != null) {
                        val smiles1 = r1Row[reactant1SmilesColumn]?.toString() ?: ""
                        if (smiles1.isNotEmpty()) {
                            val mol1 = RDKFuncs.SmilesToMol(smiles1)
                            if (mol1 != null) {
                                try {
                                    if (matrixExpansion) {
                                        // Matrix mode: pair with every reactant2
                                        for (r2Row in reactant2Rows) {
                                            outputRowNumber = runReactionPair(
                                                mol1, r1Row, r2Row, reactant2SmilesColumn,
                                                reactions, writer, outputRowNumber,
                                                productColumnName, productIndexColumnName,
                                                includeReactantColumns
                                            )
                                        }
                                    } else {
                                        // Pairwise mode: pair by index
                                        if (r1Index < reactant2Rows.size) {
                                            val r2Row = reactant2Rows[r1Index]
                                            outputRowNumber = runReactionPair(
                                                mol1, r1Row, r2Row, reactant2SmilesColumn,
                                                reactions, writer, outputRowNumber,
                                                productColumnName, productIndexColumnName,
                                                includeReactantColumns
                                            )
                                        }
                                    }
                                } finally {
                                    mol1.delete()
                                }
                            }
                        }

                        r1Index++
                        r1Row = reader.read()
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
        LOGGER.info("Two component reaction completed")
    }

    /**
     * Runs all reactions for a single pair of reactants and writes product rows.
     *
     * @return the updated output row number
     */
    private fun runReactionPair(
        mol1: ROMol,
        r1Row: Map<String, Any>,
        r2Row: Map<String, Any>,
        reactant2SmilesColumn: String,
        reactions: List<ChemicalReaction>,
        writer: NodeOutputWriter.OutputPortWriter,
        startRowNumber: Long,
        productColumnName: String,
        productIndexColumnName: String,
        includeReactantColumns: Boolean
    ): Long {
        var outputRowNumber = startRowNumber
        val smiles2 = r2Row[reactant2SmilesColumn]?.toString() ?: ""
        if (smiles2.isEmpty()) return outputRowNumber

        val mol2 = RDKFuncs.SmilesToMol(smiles2)
        if (mol2 == null) return outputRowNumber

        try {
            for (reaction in reactions) {
                val reactantVect = ROMol_Vect()
                try {
                    reactantVect.add(mol1)
                    reactantVect.add(mol2)
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
                                        outputRow.putAll(r1Row)
                                        // Add r2 columns with prefix to avoid collisions
                                        for ((key, value) in r2Row) {
                                            if (outputRow.containsKey(key)) {
                                                outputRow["r2_$key"] = value
                                            } else {
                                                outputRow[key] = value
                                            }
                                        }
                                    }
                                    outputRow[productColumnName] = productSmiles
                                    outputRow[productIndexColumnName] = prodIdx

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
            mol2.delete()
        }

        return outputRowNumber
    }
}
