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
 * Molecule Extractor Node Model
 *
 * Splits multi-component molecules into individual fragments, producing one output row per fragment.
 * This is a ROW EXPANSION node: one input row with a multi-component SMILES (e.g., "CC(=O)[O-].[Na+]")
 * produces multiple output rows, one for each disconnected fragment. Single-component molecules
 * produce exactly one output row. Each output row contains the original columns plus a fragment
 * SMILES column and a fragment ID column.
 *
 * Invalid SMILES produce an empty string rather than an error, so the node never crashes on
 * malformed input.
 *
 * **Input Ports:**
 * - `input`: Input table containing a column with SMILES strings
 *
 * **Output Ports:**
 * - `output`: Table with all original columns plus fragment SMILES and fragment ID columns (may have more rows than input)
 *
 * **Configuration Properties:**
 * - `smilesColumn` (required): Name of the column containing SMILES strings
 * - `newColumnName` (optional, default "fragment_smiles"): Name for the new column containing fragment SMILES
 * - `fragmentIdColumnName` (optional, default "fragment_id"): Name for the new column containing fragment IDs
 * - `sanitizeFragments` (optional, default true): Whether to sanitize fragments after extraction
 *
 * @since 1.0
 * @see ProcessNodeModel
 */
@ContinuumNode
class MoleculeExtractorNodeModel : ProcessNodeModel() {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(MoleculeExtractorNodeModel::class.java)
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
        "Fragments"
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
              "title": "Fragment SMILES Column Name",
              "description": "Name for the new column containing fragment SMILES",
              "default": "fragment_smiles"
            },
            "fragmentIdColumnName": {
              "type": "string",
              "title": "Fragment ID Column Name",
              "description": "Name for the new column containing fragment IDs",
              "default": "fragment_id"
            },
            "sanitizeFragments": {
              "type": "boolean",
              "title": "Sanitize Fragments",
              "description": "Whether to sanitize fragments after extraction",
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
              "scope": "#/properties/fragmentIdColumnName"
            },
            {
              "type": "Control",
              "scope": "#/properties/sanitizeFragments"
            }
          ]
        }
        """.trimIndent(),
        object : TypeReference<Map<String, Any>>() {}
    )

    override val metadata = ContinuumWorkflowModel.NodeData(
        id = this.javaClass.name,
        description = "Splits multi-component molecules into individual fragments, one row per fragment",
        title = "Molecule Extractor",
        subTitle = "Extract individual fragments from multi-component molecules",
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
            "newColumnName" to "fragment_smiles",
            "fragmentIdColumnName" to "fragment_id",
            "sanitizeFragments" to true
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
        val newColumnName = properties["newColumnName"]?.toString() ?: "fragment_smiles"
        val fragmentIdColumnName = properties["fragmentIdColumnName"]?.toString() ?: "fragment_id"
        val sanitizeFragments = properties["sanitizeFragments"] as? Boolean ?: true

        LOGGER.info("Extracting fragments from column '$smilesColumn' into '$newColumnName' (fragmentIdColumn=$fragmentIdColumnName, sanitize=$sanitizeFragments)")

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

                    if (smilesValue.isNotEmpty()) {
                        val mol = RDKFuncs.SmilesToMol(smilesValue)
                        try {
                            if (mol != null) {
                                val fragments = RDKFuncs.getMolFrags(mol)
                                try {
                                    val fragCount = fragments.size().toInt()
                                    for (i in 0 until fragCount) {
                                        val fragSmiles = RDKFuncs.MolToSmiles(fragments[i])

                                        val outputRow = row.toMutableMap<String, Any>()
                                        outputRow[newColumnName] = fragSmiles
                                        outputRow[fragmentIdColumnName] = i

                                        writer.write(outputRowNumber, outputRow)
                                        outputRowNumber++
                                    }
                                } finally {
                                    for (i in 0 until fragments.size().toInt()) {
                                        fragments[i]?.delete()
                                    }
                                    fragments.delete()
                                }
                            } else {
                                // Invalid SMILES — write one row with empty fragment
                                val outputRow = row.toMutableMap<String, Any>()
                                outputRow[newColumnName] = ""
                                outputRow[fragmentIdColumnName] = 0

                                writer.write(outputRowNumber, outputRow)
                                outputRowNumber++
                            }
                        } finally {
                            mol?.delete()
                        }
                    } else {
                        // Empty SMILES — write one row with empty fragment
                        val outputRow = row.toMutableMap<String, Any>()
                        outputRow[newColumnName] = ""
                        outputRow[fragmentIdColumnName] = 0

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
        LOGGER.info("Molecule extraction completed: processed rows")
    }
}
