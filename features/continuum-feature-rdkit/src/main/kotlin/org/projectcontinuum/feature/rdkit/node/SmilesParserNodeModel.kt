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
import org.RDKit.RDKFuncs

/**
 * SMILES Parser Node Model
 *
 * Parses molecule strings (SMILES, SDF, or SMARTS) into canonical SMILES representations
 * using RDKit. Successfully parsed molecules are sent to the "output" port, while rows that
 * fail parsing are routed to the "errors" port with an error message.
 *
 * **Input Ports:**
 * - `input`: Input table containing a column with molecule strings
 *
 * **Output Ports:**
 * - `output`: Table with successfully parsed molecules (canonical SMILES added)
 * - `errors`: Table with rows that failed parsing (error message added)
 *
 * **Configuration Properties:**
 * - `smilesColumn` (required): Name of the column containing molecule strings
 * - `inputFormat` (optional, default "SMILES"): Input format — SMILES, SDF, or SMARTS
 * - `newColumnName` (optional, default "canonical_smiles"): Name for the canonical SMILES column
 * - `removeSourceColumn` (optional, default false): Remove the original source column from output
 * - `sanitize` (optional, default true): Sanitize molecules during parsing
 * - `addErrorColumn` (optional, default true): Add an error column to failed rows
 * - `errorColumnName` (optional, default "parse_error"): Name for the error column
 *
 * **Behavior:**
 * - Parses each row's molecule string based on the selected input format
 * - Successfully parsed molecules get a canonical SMILES column added to the output port
 * - Failed molecules are routed to the errors port with an error description
 * - Native RDKit molecule objects are always freed via `mol.delete()` in a finally block
 *
 * @since 1.0
 * @see ProcessNodeModel
 */
@ContinuumNode
class SmilesParserNodeModel : ProcessNodeModel() {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(SmilesParserNodeModel::class.java)
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
            name = "parsed molecules",
            contentType = APPLICATION_OCTET_STREAM_VALUE
        ),
        "errors" to ContinuumWorkflowModel.NodePort(
            name = "failed molecules",
            contentType = APPLICATION_OCTET_STREAM_VALUE
        )
    )

    override val categories = listOf(
        "RDKit",
        "Converters"
    )

    val propertiesSchema: Map<String, Any> = objectMapper.readValue(
        """
        {
          "type": "object",
          "properties": {
            "smilesColumn": {
              "type": "string",
              "title": "SMILES Column",
              "description": "Name of the column containing molecule strings"
            },
            "inputFormat": {
              "type": "string",
              "title": "Input Format",
              "description": "Format of the molecule strings",
              "enum": ["SMILES", "SDF", "SMARTS"],
              "default": "SMILES"
            },
            "newColumnName": {
              "type": "string",
              "title": "New Column Name",
              "description": "Name for the new column containing canonical SMILES",
              "default": "canonical_smiles"
            },
            "removeSourceColumn": {
              "type": "boolean",
              "title": "Remove Source Column",
              "description": "Remove the original molecule string column from output",
              "default": false
            },
            "sanitize": {
              "type": "boolean",
              "title": "Sanitize",
              "description": "Sanitize molecules during parsing",
              "default": true
            },
            "addErrorColumn": {
              "type": "boolean",
              "title": "Add Error Column",
              "description": "Add an error description column to failed rows",
              "default": true
            },
            "errorColumnName": {
              "type": "string",
              "title": "Error Column Name",
              "description": "Name for the error description column on failed rows",
              "default": "parse_error"
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
              "scope": "#/properties/inputFormat"
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
              "scope": "#/properties/sanitize"
            },
            {
              "type": "Control",
              "scope": "#/properties/addErrorColumn"
            },
            {
              "type": "Control",
              "scope": "#/properties/errorColumnName"
            }
          ]
        }
        """.trimIndent(),
        object : TypeReference<Map<String, Any>>() {}
    )

    override val metadata = ContinuumWorkflowModel.NodeData(
        id = this.javaClass.name,
        description = "Parses molecule strings into canonical SMILES with error routing",
        title = "SMILES Parser",
        subTitle = "Parse and validate molecule strings",
        nodeModel = this.javaClass.name,
        icon = """
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" stroke="currentColor" strokeWidth={1.5} viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" d="M9 8.25H7.5a2.25 2.25 0 0 0-2.25 2.25v9a2.25 2.25 0 0 0 2.25 2.25h9a2.25 2.25 0 0 0 2.25-2.25v-9a2.25 2.25 0 0 0-2.25-2.25H15M9 12l3 3m0 0 3-3m-3 3V2.25" />
            </svg>
        """.trimIndent(),
        inputs = inputPorts,
        outputs = outputPorts,
        properties = mapOf(
            "smilesColumn" to "smiles",
            "inputFormat" to "SMILES",
            "newColumnName" to "canonical_smiles",
            "removeSourceColumn" to false,
            "sanitize" to true,
            "addErrorColumn" to true,
            "errorColumnName" to "parse_error"
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
        val inputFormat = properties["inputFormat"]?.toString() ?: "SMILES"
        val newColumnName = properties["newColumnName"]?.toString() ?: "canonical_smiles"
        val removeSourceColumn = properties["removeSourceColumn"] as? Boolean ?: false
        val sanitize = properties["sanitize"] as? Boolean ?: true
        val addErrorColumn = properties["addErrorColumn"] as? Boolean ?: true
        val errorColumnName = properties["errorColumnName"]?.toString() ?: "parse_error"

        LOGGER.info("Parsing molecules from column '$smilesColumn' (format=$inputFormat) into '$newColumnName'")

        val inputReader = inputs["input"] ?: throw NodeRuntimeException(
            workflowId = "",
            nodeId = "",
            message = "Input port 'input' is not connected"
        )

        val totalRows = inputReader.getRowCount()
        var outputCount = 0L
        var errorCount = 0L

        // === Create both output writers upfront (required for proper resource management) ===
        nodeOutputWriter.createOutputPortWriter("output").use { outputWriter ->
            nodeOutputWriter.createOutputPortWriter("errors").use { errorWriter ->
                inputReader.use { reader ->
                    var row = reader.read()
                    var rowNumber = 0L

                    while (row != null) {
                        val moleculeString = row[smilesColumn]?.toString()

                        if (moleculeString.isNullOrBlank()) {
                            // Missing or blank molecule string — route to errors
                            val errorRow = buildErrorRow(row, smilesColumn, removeSourceColumn, addErrorColumn, errorColumnName, "Molecule string is empty or missing")
                            errorWriter.write(errorCount, errorRow)
                            errorCount++
                        } else {
                            // Attempt to parse the molecule
                            var mol: org.RDKit.ROMol? = null
                            try {
                                mol = when (inputFormat) {
                                    "SMILES" -> RDKFuncs.SmilesToMol(moleculeString)
                                    "SDF" -> RDKFuncs.MolBlockToMol(moleculeString)
                                    "SMARTS" -> RDKFuncs.SmartsToMol(moleculeString)
                                    else -> RDKFuncs.SmilesToMol(moleculeString)
                                }

                                if (mol != null) {
                                    val canonicalSmiles = RDKFuncs.MolToSmiles(mol)
                                    val outputRow = buildOutputRow(row, smilesColumn, removeSourceColumn, newColumnName, canonicalSmiles)
                                    outputWriter.write(outputCount, outputRow)
                                    outputCount++
                                } else {
                                    val errorRow = buildErrorRow(row, smilesColumn, removeSourceColumn, addErrorColumn, errorColumnName, "Failed to parse $inputFormat: $moleculeString")
                                    errorWriter.write(errorCount, errorRow)
                                    errorCount++
                                }
                            } catch (e: Exception) {
                                val errorRow = buildErrorRow(row, smilesColumn, removeSourceColumn, addErrorColumn, errorColumnName, "Parse error ($inputFormat): ${e.message}")
                                errorWriter.write(errorCount, errorRow)
                                errorCount++
                            } finally {
                                mol?.delete()
                            }
                        }

                        rowNumber++
                        if (totalRows > 0) {
                            nodeProgressCallback.report((rowNumber * 100 / totalRows).toInt())
                        }

                        row = reader.read()
                    }
                }
            }
        }

        nodeProgressCallback.report(100)
        LOGGER.info("SMILES parsing completed: $outputCount successful, $errorCount failed")
    }

    /**
     * Build an output row for successfully parsed molecules.
     */
    private fun buildOutputRow(
        row: Map<String, Any>,
        smilesColumn: String,
        removeSourceColumn: Boolean,
        newColumnName: String,
        canonicalSmiles: String
    ): Map<String, Any> {
        val outputRow = if (removeSourceColumn) {
            row.filterKeys { it != smilesColumn }.toMutableMap()
        } else {
            row.toMutableMap()
        }
        outputRow[newColumnName] = canonicalSmiles
        return outputRow
    }

    /**
     * Build an error row for molecules that failed parsing.
     */
    private fun buildErrorRow(
        row: Map<String, Any>,
        smilesColumn: String,
        removeSourceColumn: Boolean,
        addErrorColumn: Boolean,
        errorColumnName: String,
        errorMessage: String
    ): Map<String, Any> {
        val errorRow = if (removeSourceColumn) {
            row.filterKeys { it != smilesColumn }.toMutableMap()
        } else {
            row.toMutableMap()
        }
        if (addErrorColumn) {
            errorRow[errorColumnName] = errorMessage
        }
        return errorRow
    }
}
