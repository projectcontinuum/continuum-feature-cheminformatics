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
import org.RDKit.ExtraInchiReturnValues
import org.RDKit.RDKFuncs

/**
 * Molecule to InChI Node Model
 *
 * Converts SMILES strings to InChI (International Chemical Identifier) representations using RDKit.
 * For each row in the input table, the node reads a SMILES string from the configured column,
 * parses it into an RDKit molecule object, and generates an InChI string. Optionally also generates
 * the InChI Key. Invalid SMILES produce empty strings rather than errors.
 *
 * InChI conversion is not thread-safe in RDKit, so all InChI operations are synchronized using
 * a companion object lock.
 *
 * **Input Ports:**
 * - `input`: Input table containing a column with SMILES strings
 *
 * **Output Ports:**
 * - `output`: Table with all original columns plus new columns for InChI and optionally InChI Key
 *
 * **Configuration Properties:**
 * - `smilesColumn` (required): Name of the column containing SMILES strings
 * - `newInChIColumnName` (optional, default "inchi"): Name for the new InChI column
 * - `removeSourceColumn` (optional, default false): Whether to remove the original SMILES column
 * - `generateInChIKey` (optional, default true): Whether to also generate InChI Keys
 * - `inchiKeyColumnName` (optional, default "inchi_key"): Name for the InChI Key column
 *
 * @since 1.0
 * @see ProcessNodeModel
 */
@ContinuumNode
class MoleculeToInChINodeModel : ProcessNodeModel() {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(MoleculeToInChINodeModel::class.java)
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
            "smilesColumn": {
              "type": "string",
              "title": "SMILES Column",
              "description": "Name of the column containing SMILES strings"
            },
            "newInChIColumnName": {
              "type": "string",
              "title": "InChI Column Name",
              "description": "Name for the new column containing InChI strings",
              "default": "inchi"
            },
            "removeSourceColumn": {
              "type": "boolean",
              "title": "Remove Source Column",
              "description": "Whether to remove the original SMILES column from the output",
              "default": false
            },
            "generateInChIKey": {
              "type": "boolean",
              "title": "Generate InChI Key",
              "description": "Whether to also generate InChI Key for each molecule",
              "default": true
            },
            "inchiKeyColumnName": {
              "type": "string",
              "title": "InChI Key Column Name",
              "description": "Name for the new column containing InChI Keys",
              "default": "inchi_key"
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
              "scope": "#/properties/newInChIColumnName"
            },
            {
              "type": "Control",
              "scope": "#/properties/removeSourceColumn"
            },
            {
              "type": "Control",
              "scope": "#/properties/generateInChIKey"
            },
            {
              "type": "Control",
              "scope": "#/properties/inchiKeyColumnName"
            }
          ]
        }
        """.trimIndent(),
        object : TypeReference<Map<String, Any>>() {}
    )

    override val metadata = ContinuumWorkflowModel.NodeData(
        id = this.javaClass.name,
        description = "Converts SMILES strings to InChI and InChI Key using RDKit",
        title = "Molecule to InChI",
        subTitle = "Convert SMILES to InChI and InChI Key",
        nodeModel = this.javaClass.name,
        icon = """
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" d="M3 16.5v2.25A2.25 2.25 0 0 0 5.25 21h13.5A2.25 2.25 0 0 0 21 18.75V16.5M16.5 12 12 16.5m0 0L7.5 12m4.5 4.5V3"/>
            </svg>
        """.trimIndent(),
        inputs = inputPorts,
        outputs = outputPorts,
        properties = mapOf(
            "smilesColumn" to "smiles",
            "newInChIColumnName" to "inchi",
            "removeSourceColumn" to false,
            "generateInChIKey" to true,
            "inchiKeyColumnName" to "inchi_key"
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
        val newInChIColumnName = properties["newInChIColumnName"]?.toString() ?: "inchi"
        val removeSourceColumn = properties["removeSourceColumn"] as? Boolean ?: false
        val generateInChIKey = properties["generateInChIKey"] as? Boolean ?: true
        val inchiKeyColumnName = properties["inchiKeyColumnName"]?.toString() ?: "inchi_key"

        LOGGER.info("Converting SMILES from column '$smilesColumn' to InChI into '$newInChIColumnName' (removeSource=$removeSourceColumn, generateInChIKey=$generateInChIKey)")

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

                    // Parse SMILES and convert to InChI
                    var inchi = ""
                    var inchiKey = ""
                    if (smilesValue.isNotEmpty()) {
                        RDKitNodeHelper.withMolecule(smilesValue) { mol ->
                            synchronized(INCHI_LOCK) {
                                inchi = RDKFuncs.MolToInchi(mol, ExtraInchiReturnValues())
                                if (generateInChIKey && inchi.isNotEmpty()) {
                                    inchiKey = RDKFuncs.InchiToInchiKey(inchi)
                                }
                            }
                        }
                    }

                    // Build output row: all original columns plus InChI (and optionally InChI Key)
                    val outputRow = row.toMutableMap<String, Any>()
                    if (removeSourceColumn) {
                        outputRow.remove(smilesColumn)
                    }
                    outputRow[newInChIColumnName] = inchi
                    if (generateInChIKey) {
                        outputRow[inchiKeyColumnName] = inchiKey
                    }

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
        LOGGER.info("Molecule to InChI conversion completed: processed rows")
    }
}
