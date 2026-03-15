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
import org.RDKit.ExtraInchiReturnValues
import org.RDKit.RDKFuncs

/**
 * InChI to Molecule Node Model
 *
 * Converts InChI strings to canonical SMILES representations using RDKit. For each row in the
 * input table, the node reads an InChI string from the configured column, converts it to an RDKit
 * molecule object, and serializes it as a canonical SMILES string. Invalid InChI strings produce
 * an empty string rather than an error, so the node never crashes on malformed input.
 *
 * InChI conversion is not thread-safe in RDKit, so all InChI operations are synchronized using
 * a companion object lock.
 *
 * **Input Ports:**
 * - `input`: Input table containing a column with InChI strings
 *
 * **Output Ports:**
 * - `output`: Table with all original columns plus a new column containing canonical SMILES
 *
 * **Configuration Properties:**
 * - `inchiColumn` (required): Name of the column containing InChI strings
 * - `newColumnName` (optional, default "smiles"): Name for the new output column
 * - `removeSourceColumn` (optional, default false): Whether to remove the original InChI column
 * - `sanitize` (optional, default true): Sanitize molecules during conversion
 * - `removeHydrogens` (optional, default true): Remove explicit hydrogens after conversion
 *
 * @since 1.0
 * @see ProcessNodeModel
 */
@ContinuumNode
class InChIToMoleculeNodeModel : ProcessNodeModel() {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(InChIToMoleculeNodeModel::class.java)
        private val objectMapper = ObjectMapper()
        private val INCHI_LOCK = Any()
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
        "Converters"
    )

    val propertiesSchema: Map<String, Any> = objectMapper.readValue(
        """
        {
          "type": "object",
          "properties": {
            "inchiColumn": {
              "type": "string",
              "title": "InChI Column",
              "description": "Name of the column containing InChI strings"
            },
            "newColumnName": {
              "type": "string",
              "title": "New Column Name",
              "description": "Name for the new column containing SMILES",
              "default": "smiles"
            },
            "removeSourceColumn": {
              "type": "boolean",
              "title": "Remove Source Column",
              "description": "Whether to remove the original InChI column from the output",
              "default": false
            },
            "sanitize": {
              "type": "boolean",
              "title": "Sanitize",
              "description": "Sanitize molecules during conversion",
              "default": true
            },
            "removeHydrogens": {
              "type": "boolean",
              "title": "Remove Hydrogens",
              "description": "Remove explicit hydrogens after conversion",
              "default": true
            }
          },
          "required": ["inchiColumn"]
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
              "scope": "#/properties/inchiColumn"
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
              "scope": "#/properties/removeHydrogens"
            }
          ]
        }
        """.trimIndent(),
        object : TypeReference<Map<String, Any>>() {}
    )

    override val metadata = ContinuumWorkflowModel.NodeData(
        id = this.javaClass.name,
        description = "Converts InChI strings to canonical SMILES using RDKit",
        title = "InChI to Molecule",
        subTitle = "Convert InChI strings to SMILES",
        nodeModel = this.javaClass.name,
        icon = """
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" d="M9 8.25H7.5a2.25 2.25 0 0 0-2.25 2.25v9a2.25 2.25 0 0 0 2.25 2.25h9a2.25 2.25 0 0 0 2.25-2.25v-9a2.25 2.25 0 0 0-2.25-2.25H15M9 12l3 3m0 0 3-3m-3 3V2.25"/>
            </svg>
        """.trimIndent(),
        inputs = inputPorts,
        outputs = outputPorts,
        properties = mapOf(
            "inchiColumn" to "inchi",
            "newColumnName" to "smiles",
            "removeSourceColumn" to false,
            "sanitize" to true,
            "removeHydrogens" to true
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
        val inchiColumn = properties?.get("inchiColumn") as String? ?: throw NodeRuntimeException(
            workflowId = "",
            nodeId = "",
            message = "inchiColumn is not provided"
        )
        val newColumnName = properties["newColumnName"]?.toString() ?: "smiles"
        val removeSourceColumn = properties["removeSourceColumn"] as? Boolean ?: false
        val sanitize = properties["sanitize"] as? Boolean ?: true
        val removeHydrogens = properties["removeHydrogens"] as? Boolean ?: true

        LOGGER.info("Converting InChI from column '$inchiColumn' into '$newColumnName' (removeSource=$removeSourceColumn, sanitize=$sanitize, removeHydrogens=$removeHydrogens)")

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
                    val inchiValue = row[inchiColumn]?.toString() ?: ""

                    // Parse InChI and convert to SMILES
                    var smiles = ""
                    if (inchiValue.isNotEmpty()) {
                        synchronized(INCHI_LOCK) {
                            val mol = RDKFuncs.InchiToMol(inchiValue, ExtraInchiReturnValues(), sanitize, removeHydrogens)
                            try {
                                if (mol != null) {
                                    smiles = RDKFuncs.MolToSmiles(mol)
                                }
                            } finally {
                                mol?.delete()
                            }
                        }
                    }

                    // Build output row: all original columns plus SMILES column
                    val outputRow = row.toMutableMap<String, Any>()
                    if (removeSourceColumn) {
                        outputRow.remove(inchiColumn)
                    }
                    outputRow[newColumnName] = smiles

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
        LOGGER.info("InChI to Molecule conversion completed: processed rows")
    }
}
