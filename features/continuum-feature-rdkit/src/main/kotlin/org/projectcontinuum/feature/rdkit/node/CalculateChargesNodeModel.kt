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
 * Calculate Charges Node Model
 *
 * Computes Gasteiger partial charges per atom for molecules represented as SMILES strings using
 * RDKit. For each row in the input table, the node reads a SMILES string from the configured
 * column, parses it into an RDKit molecule object, computes Gasteiger charges, and serializes
 * the per-atom charges as a JSON array string. Invalid SMILES produce an empty string rather
 * than an error, so the node never crashes on malformed input.
 *
 * **Input Ports:**
 * - `input`: Input table containing a column with SMILES strings
 *
 * **Output Ports:**
 * - `output`: Table with all original columns plus a new column containing Gasteiger charges as a JSON array
 *
 * **Configuration Properties:**
 * - `smilesColumn` (required): Name of the column containing SMILES strings
 * - `chargesColumnName` (optional, default "gasteiger_charges"): Name for the new output column
 *
 * @since 1.0
 * @see ProcessNodeModel
 */
@ContinuumNode
class CalculateChargesNodeModel : ProcessNodeModel() {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(CalculateChargesNodeModel::class.java)
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
        "RDKit/Calculators"
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
            "chargesColumnName": {
              "type": "string",
              "title": "Charges Column Name",
              "description": "Name for the new column containing Gasteiger partial charges as a JSON array",
              "default": "gasteiger_charges"
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
              "scope": "#/properties/chargesColumnName"
            }
          ]
        }
        """.trimIndent(),
        object : TypeReference<Map<String, Any>>() {}
    )

    override val metadata = ContinuumWorkflowModel.NodeData(
        id = this.javaClass.name,
        description = "Computes Gasteiger partial charges per atom using RDKit",
        title = "Calculate Charges",
        subTitle = "Compute Gasteiger partial charges for SMILES molecules",
        nodeModel = this.javaClass.name,
        icon = """
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" d="M3.75 13.5l10.5-11.25L12 10.5h8.25L9.75 21.75 12 13.5H3.75z"/>
            </svg>
        """.trimIndent(),
        inputs = inputPorts,
        outputs = outputPorts,
        properties = mapOf(
            "smilesColumn" to "smiles",
            "chargesColumnName" to "gasteiger_charges"
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
        val chargesColumnName = properties["chargesColumnName"]?.toString() ?: "gasteiger_charges"

        LOGGER.info("Computing Gasteiger charges from column '$smilesColumn' into '$chargesColumnName'")

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

                    // Parse SMILES, compute Gasteiger charges, and serialize as JSON array
                    var chargesJson = ""
                    if (smilesValue.isNotEmpty()) {
                        val mol = RDKitNodeHelper.parseMoleculeOrNull(smilesValue)
                        try {
                            if (mol != null) {
                                mol.computeGasteigerCharges()
                                val charges = mutableListOf<Double>()
                                for (i in 0 until mol.getNumAtoms()) {
                                    val atom = mol.getAtomWithIdx(i)
                                    val chargeStr = atom.getProp("_GasteigerCharge")
                                    charges.add(chargeStr.toDoubleOrNull() ?: 0.0)
                                }
                                chargesJson = objectMapper.writeValueAsString(charges)
                            }
                        } finally {
                            mol?.delete()
                        }
                    }

                    // Build output row: all original columns plus charges column
                    val outputRow = row.toMutableMap<String, Any>()
                    outputRow[chargesColumnName] = chargesJson

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
        LOGGER.info("Gasteiger charge calculation completed: processed rows")
    }
}
