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
 * Salt Stripper Node Model
 *
 * Removes salt/counterion fragments from molecules represented as SMILES strings using RDKit.
 * For each row in the input table, the node reads a SMILES string from the configured column,
 * parses it into an RDKit molecule object, and if keepLargestFragmentOnly is enabled, splits
 * the molecule into fragments and keeps only the largest one (by heavy atom count). Invalid
 * SMILES produce an empty string rather than an error, so the node never crashes on malformed input.
 *
 * **Input Ports:**
 * - `input`: Input table containing a column with SMILES strings
 *
 * **Output Ports:**
 * - `output`: Table with all original columns plus a new column containing stripped SMILES
 *
 * **Configuration Properties:**
 * - `smilesColumn` (required): Name of the column containing SMILES strings
 * - `newColumnName` (optional, default "stripped_smiles"): Name for the new output column
 * - `removeSourceColumn` (optional, default false): Whether to remove the original SMILES column
 * - `keepLargestFragmentOnly` (optional, default true): Whether to keep only the largest fragment
 *
 * @since 1.0
 * @see ProcessNodeModel
 */
@ContinuumNode
class SaltStripperNodeModel : ProcessNodeModel() {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(SaltStripperNodeModel::class.java)
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
        "Modifiers"
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
              "description": "Name for the new column containing stripped SMILES",
              "default": "stripped_smiles"
            },
            "removeSourceColumn": {
              "type": "boolean",
              "title": "Remove Source Column",
              "description": "Whether to remove the original SMILES column from the output",
              "default": false
            },
            "keepLargestFragmentOnly": {
              "type": "boolean",
              "title": "Keep Largest Fragment Only",
              "description": "Whether to keep only the largest fragment by heavy atom count",
              "default": true
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
              "scope": "#/properties/newColumnName"
            },
            {
              "type": "Control",
              "scope": "#/properties/removeSourceColumn"
            },
            {
              "type": "Control",
              "scope": "#/properties/keepLargestFragmentOnly"
            }
          ]
        }
        """.trimIndent(),
        object : TypeReference<Map<String, Any>>() {}
    )

    override val metadata = ContinuumWorkflowModel.NodeData(
        id = this.javaClass.name,
        description = "Removes salt/counterion fragments from SMILES strings using RDKit",
        title = "Salt Stripper",
        subTitle = "Remove salts and keep largest fragment via RDKit",
        nodeModel = this.javaClass.name,
        icon = """
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" d="M9.75 3.104v5.714a2.25 2.25 0 0 1-.659 1.591L5 14.5M9.75 3.104c-.251.023-.501.05-.75.082m.75-.082a24.301 24.301 0 0 1 4.5 0m0 0v5.714a2.25 2.25 0 0 0 .659 1.591L19 14.5m-4.75-11.396c.251.023.501.05.75.082M5 14.5l-1.395 1.395a.75.75 0 0 0 .53 1.28h15.73a.75.75 0 0 0 .53-1.28L19 14.5m-14 0h14"/>
                <circle cx="8" cy="19.5" r="1.25"/>
                <circle cx="12" cy="19.5" r="1.25"/>
                <circle cx="16" cy="19.5" r="1.25"/>
            </svg>
        """.trimIndent(),
        inputs = inputPorts,
        outputs = outputPorts,
        properties = mapOf(
            "smilesColumn" to "smiles",
            "newColumnName" to "stripped_smiles",
            "removeSourceColumn" to false,
            "keepLargestFragmentOnly" to true
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
        val newColumnName = properties["newColumnName"]?.toString() ?: "stripped_smiles"
        val removeSourceColumn = properties["removeSourceColumn"] as? Boolean ?: false
        val keepLargestFragmentOnly = properties["keepLargestFragmentOnly"] as? Boolean ?: true

        LOGGER.info("Stripping salts from SMILES in column '$smilesColumn' into '$newColumnName' (removeSource=$removeSourceColumn, keepLargest=$keepLargestFragmentOnly)")

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

                    // Parse SMILES, strip salts, and convert back to SMILES
                    var resultSmiles = ""
                    if (smilesValue.isNotEmpty()) {
                        resultSmiles = RDKitNodeHelper.withMolecule(smilesValue) { mol ->
                            if (keepLargestFragmentOnly) {
                                val fragments = RDKFuncs.getMolFrags(mol)
                                try {
                                    if (fragments.size().toInt() > 1) {
                                        // Find the largest fragment by heavy atom count
                                        var largestIdx = 0
                                        var maxAtoms = 0L
                                        for (i in 0 until fragments.size().toInt()) {
                                            val fragAtoms = fragments[i].getNumHeavyAtoms()
                                            if (fragAtoms > maxAtoms) {
                                                maxAtoms = fragAtoms
                                                largestIdx = i
                                            }
                                        }
                                        RDKFuncs.MolToSmiles(fragments[largestIdx])
                                    } else {
                                        // Single fragment — just canonicalize
                                        RDKFuncs.MolToSmiles(mol)
                                    }
                                } finally {
                                    for (i in 0 until fragments.size().toInt()) {
                                        fragments[i]?.delete()
                                    }
                                    fragments.delete()
                                }
                            } else {
                                // No stripping — just canonicalize
                                RDKFuncs.MolToSmiles(mol)
                            }
                        } ?: ""
                    }

                    // Build output row: all original columns plus result column
                    val outputRow = row.toMutableMap<String, Any>()
                    if (removeSourceColumn) {
                        outputRow.remove(smilesColumn)
                    }
                    outputRow[newColumnName] = resultSmiles

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
        LOGGER.info("Salt stripping completed: processed rows")
    }
}
