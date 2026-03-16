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
 * Structure Normalizer Node Model
 *
 * Applies configurable normalization steps to molecules to standardize their SMILES representation.
 * Normalization is critical for database registration, deduplication, and consistent downstream
 * analysis. Users select which steps to apply and in what order.
 *
 * **Input Ports:**
 * - `input`: Input table containing a column with SMILES strings
 *
 * **Output Ports:**
 * - `output`: Table with original columns plus normalized SMILES column
 *
 * **Configuration Properties:**
 * - `smilesColumn` (required): Name of the column containing SMILES strings
 * - `newColumnName` (optional, default "normalized_smiles"): Name for the normalized SMILES column
 * - `removeSourceColumn` (optional, default false): Whether to remove the original SMILES column
 * - `normalizationSteps` (optional): Array of normalization steps to apply in order
 *
 * @since 1.0
 * @see ProcessNodeModel
 */
@ContinuumNode
class StructureNormalizerNodeModel : ProcessNodeModel() {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(StructureNormalizerNodeModel::class.java)
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
        "Experimental"
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
            "newColumnName": {
              "type": "string",
              "title": "New Column Name",
              "description": "Name for the output column containing normalized SMILES",
              "default": "normalized_smiles"
            },
            "removeSourceColumn": {
              "type": "boolean",
              "title": "Remove Source Column",
              "description": "Whether to remove the original SMILES column from the output",
              "default": false
            },
            "normalizationSteps": {
              "type": "array",
              "title": "Normalization Steps",
              "description": "Normalization steps to apply in order",
              "items": {
                "type": "string",
                "enum": ["RemoveFragments", "Neutralize", "Canonicalize", "RemoveIsotopes", "RemoveStereo", "ReionizeMetal", "Cleanup"]
              },
              "default": ["RemoveFragments", "Neutralize", "Canonicalize"]
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
              "scope": "#/properties/normalizationSteps"
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
        description = "Applies configurable normalization steps to standardize molecule SMILES representations",
        title = "Structure Normalizer",
        subTitle = "Standardize molecules via configurable normalization pipeline",
        nodeModel = this.javaClass.name,
        icon = """
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" d="M10.5 6h9.75M10.5 6a1.5 1.5 0 1 1-3 0m3 0a1.5 1.5 0 1 0-3 0M3.75 6H7.5m3 12h9.75m-9.75 0a1.5 1.5 0 0 1-3 0m3 0a1.5 1.5 0 0 0-3 0m-3.75 0H7.5m9-6h3.75m-3.75 0a1.5 1.5 0 0 1-3 0m3 0a1.5 1.5 0 0 0-3 0m-9.75 0h9.75"/>
            </svg>
        """.trimIndent(),
        inputs = inputPorts,
        outputs = outputPorts,
        properties = mapOf(
            "smilesColumn" to "smiles",
            "newColumnName" to "normalized_smiles",
            "removeSourceColumn" to false,
            "normalizationSteps" to listOf("RemoveFragments", "Neutralize", "Canonicalize")
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
        val newColumnName = properties["newColumnName"]?.toString() ?: "normalized_smiles"
        val removeSourceColumn = properties["removeSourceColumn"] as? Boolean ?: false
        @Suppress("UNCHECKED_CAST")
        val normalizationSteps = (properties["normalizationSteps"] as? List<String>)
            ?: listOf("RemoveFragments", "Neutralize", "Canonicalize")

        LOGGER.info("Structure Normalizer: column '$smilesColumn', steps $normalizationSteps")

        val inputReader = inputs["input"] ?: throw NodeRuntimeException(
            workflowId = "",
            nodeId = "",
            message = "Input port 'input' is not connected"
        )

        // Pre-parse neutralization reaction SMARTS
        val neutralizationTransforms = listOf(
            "[NH3+:1]>>[NH2:1]",
            "[NH2+:1]>>[NH:1]",
            "[n+:1]>>[n:1]",
            "[O-:1]>>[OH:1]",
            "[S-:1]>>[SH:1]"
        )
        val neutralizationReactions = mutableListOf<ChemicalReaction>()
        if (normalizationSteps.contains("Neutralize")) {
            for (smarts in neutralizationTransforms) {
                val rxn = ChemicalReaction.ReactionFromSmarts(smarts)
                if (rxn != null) {
                    rxn.initReactantMatchers()
                    neutralizationReactions.add(rxn)
                }
            }
        }

        try {
            val totalRows = inputReader.getRowCount()

            nodeOutputWriter.createOutputPortWriter("output").use { writer ->
                inputReader.use { reader ->
                    var row = reader.read()
                    var rowNumber = 0L

                    while (row != null) {
                        val smilesValue = row[smilesColumn]?.toString() ?: ""
                        var normalizedSmiles = ""

                        if (smilesValue.isNotEmpty()) {
                            normalizedSmiles = applyNormalization(smilesValue, normalizationSteps, neutralizationReactions)
                        }

                        // Build output row
                        val outputRow = row.toMutableMap<String, Any>()
                        if (removeSourceColumn) {
                            outputRow.remove(smilesColumn)
                        }
                        outputRow[newColumnName] = normalizedSmiles

                        writer.write(rowNumber, outputRow)

                        rowNumber++
                        if (totalRows > 0) {
                            nodeProgressCallback.report((rowNumber * 100 / totalRows).toInt())
                        }

                        row = reader.read()
                    }
                }
            }
        } finally {
            for (rxn in neutralizationReactions) {
                rxn.delete()
            }
        }

        nodeProgressCallback.report(100)
        LOGGER.info("Structure Normalizer completed")
    }

    /**
     * Applies the configured normalization steps to a SMILES string.
     * Returns the normalized SMILES or empty string if the input cannot be parsed.
     */
    private fun applyNormalization(
        inputSmiles: String,
        steps: List<String>,
        neutralizationReactions: List<ChemicalReaction>
    ): String {
        // Verify the input is a valid SMILES before applying normalization
        val initialMol = RDKitNodeHelper.parseMoleculeOrNull(inputSmiles)
        if (initialMol == null) return ""
        initialMol.delete()

        var currentSmiles = inputSmiles

        for (step in steps) {
            val mol = RDKitNodeHelper.parseMoleculeOrNull(currentSmiles)
            if (mol == null) return currentSmiles

            try {
                when (step) {
                    "RemoveFragments" -> {
                        currentSmiles = removeFragments(mol)
                    }
                    "Neutralize" -> {
                        currentSmiles = neutralize(currentSmiles, neutralizationReactions)
                    }
                    "Canonicalize" -> {
                        currentSmiles = RDKFuncs.MolToSmiles(mol)
                    }
                    "RemoveIsotopes" -> {
                        val rwMol = RWMol(mol)
                        try {
                            for (i in 0 until rwMol.getNumAtoms().toInt()) {
                                val atom = rwMol.getAtomWithIdx(i.toLong())
                                atom.setIsotope(0)
                            }
                            currentSmiles = RDKFuncs.MolToSmiles(rwMol)
                        } finally {
                            rwMol.delete()
                        }
                    }
                    "RemoveStereo" -> {
                        RDKFuncs.removeStereochemistry(mol)
                        currentSmiles = RDKFuncs.MolToSmiles(mol)
                    }
                    "ReionizeMetal" -> {
                        // Standardize metal-organic salts by re-canonicalizing
                        currentSmiles = RDKFuncs.MolToSmiles(mol)
                    }
                    "Cleanup" -> {
                        val rwMol = RWMol(mol)
                        try {
                            RDKFuncs.sanitizeMol(rwMol)
                            currentSmiles = RDKFuncs.MolToSmiles(rwMol)
                        } finally {
                            rwMol.delete()
                        }
                    }
                    else -> {
                        LOGGER.warn("Unknown normalization step: $step")
                    }
                }
            } catch (e: Exception) {
                LOGGER.debug("Normalization step '$step' failed for '$currentSmiles': ${e.message}")
            } finally {
                mol.delete()
            }
        }

        return currentSmiles
    }

    /**
     * Keeps only the largest fragment by heavy atom count.
     */
    private fun removeFragments(mol: ROMol): String {
        val frags = RDKFuncs.getMolFrags(mol)
        try {
            if (frags.size() <= 1) {
                return RDKFuncs.MolToSmiles(mol)
            }

            var largestFrag: ROMol? = null
            var maxAtoms = -1L
            for (i in 0 until frags.size().toInt()) {
                val frag = frags[i]
                val numAtoms = frag.getNumHeavyAtoms()
                if (numAtoms > maxAtoms) {
                    maxAtoms = numAtoms
                    largestFrag = frag
                }
            }

            return if (largestFrag != null) {
                RDKFuncs.MolToSmiles(largestFrag)
            } else {
                RDKFuncs.MolToSmiles(mol)
            }
        } finally {
            frags.delete()
        }
    }

    /**
     * Applies neutralization reaction SMARTS to remove charges where possible.
     */
    private fun neutralize(smiles: String, reactions: List<ChemicalReaction>): String {
        var currentSmiles = smiles

        for (reaction in reactions) {
            val mol = RDKitNodeHelper.parseMoleculeOrNull(currentSmiles) ?: continue
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
                                        currentSmiles = RDKFuncs.MolToSmiles(rwProduct)
                                    } finally {
                                        rwProduct.delete()
                                    }
                                } catch (e: Exception) {
                                    // Sanitization failed, keep current
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

        return currentSmiles
    }
}


