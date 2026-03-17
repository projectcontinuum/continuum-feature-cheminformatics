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
 * Descriptor Calculation Node Model
 *
 * Calculates molecular descriptors from SMILES strings using RDKit. For each row in the input
 * table, the node reads a SMILES string from the configured column, parses it into an RDKit
 * molecule object, and computes the selected set of molecular descriptors. Supports 41 scalar
 * descriptors including molecular weight, topological polar surface area, LogP, hydrogen bond
 * donors/acceptors, ring counts, connectivity indices, and more. Invalid SMILES produce empty
 * strings for all descriptor columns rather than an error, so the node never crashes on
 * malformed input.
 *
 * **Input Ports:**
 * - `input`: Input table containing a column with SMILES strings
 *
 * **Output Ports:**
 * - `output`: Table with all original columns plus new columns for each selected descriptor
 *
 * **Configuration Properties:**
 * - `smilesColumn` (required): Name of the column containing SMILES strings
 * - `descriptors` (required): List of descriptor names to calculate (multi-select from 41 available)
 * - `columnPrefix` (optional, default ""): Optional prefix for output column names
 *
 * @since 1.0
 * @see ProcessNodeModel
 */
@ContinuumNode
class DescriptorCalculationNodeModel : ProcessNodeModel() {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(DescriptorCalculationNodeModel::class.java)
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
            "descriptors": {
              "type": "array",
              "title": "Descriptors to Calculate",
              "description": "Select one or more molecular descriptors to compute",
              "items": {
                "type": "string",
                "enum": [
                  "AMW", "ExactMW", "SlogP", "SMR", "LabuteASA", "TPSA",
                  "NumLipinskiHBA", "NumLipinskiHBD", "NumRotatableBonds", "NumHBD", "NumHBA",
                  "NumAmideBonds", "NumHeteroAtoms", "NumHeavyAtoms", "NumAtoms",
                  "NumStereocenters", "NumUnspecifiedStereocenters",
                  "NumRings", "NumAromaticRings", "NumSaturatedRings", "NumAliphaticRings",
                  "NumAromaticHeterocycles", "NumSaturatedHeterocycles", "NumAliphaticHeterocycles",
                  "NumAromaticCarbocycles", "NumSaturatedCarbocycles", "NumAliphaticCarbocycles",
                  "FractionCSP3",
                  "Chi0v", "Chi1v", "Chi2v", "Chi3v", "Chi4v",
                  "Chi1n", "Chi2n", "Chi3n", "Chi4n",
                  "HallKierAlpha", "kappa1", "kappa2", "kappa3"
                ]
              },
              "uniqueItems": true
            },
            "columnPrefix": {
              "type": "string",
              "title": "Column Prefix",
              "description": "Optional prefix for output descriptor column names",
              "default": ""
            }
          },
          "required": ["smilesColumn", "descriptors"]
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
              "scope": "#/properties/descriptors"
            },
            {
              "type": "Control",
              "scope": "#/properties/columnPrefix"
            }
          ]
        }
        """.trimIndent(),
        object : TypeReference<Map<String, Any>>() {}
    )

    override val metadata = ContinuumWorkflowModel.NodeData(
        id = this.javaClass.name,
        description = "Calculates molecular descriptors from SMILES strings using RDKit",
        title = "Descriptor Calculation",
        subTitle = "Compute molecular descriptors via RDKit",
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
            "descriptors" to listOf("AMW", "SlogP", "TPSA", "NumHBD", "NumHBA", "NumRotatableBonds", "NumRings"),
            "columnPrefix" to ""
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
        val descriptorList = (properties["descriptors"] as? List<*>)?.map { it.toString() }
            ?: throw NodeRuntimeException(
                workflowId = "",
                nodeId = "",
                message = "descriptors is not provided"
            )
        if (descriptorList.isEmpty()) {
            throw NodeRuntimeException(
                workflowId = "",
                nodeId = "",
                message = "descriptors must not be empty"
            )
        }
        val columnPrefix = properties["columnPrefix"]?.toString() ?: ""

        LOGGER.info("Calculating ${descriptorList.size} descriptors from column '$smilesColumn' (prefix='$columnPrefix')")

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

                    // Build output row: start with all original columns
                    val outputRow = row.toMutableMap<String, Any>()

                    if (smilesValue.isNotEmpty()) {
                        RDKitNodeHelper.withMolecule(smilesValue) { mol ->
                            for (descriptor in descriptorList) {
                                outputRow[columnPrefix + descriptor] = computeDescriptor(mol, descriptor)
                            }
                        } ?: run {
                            // Null mol (invalid SMILES) — write empty strings
                            for (descriptor in descriptorList) {
                                outputRow[columnPrefix + descriptor] = ""
                            }
                        }
                    } else {
                        // Empty SMILES — write empty strings
                        for (descriptor in descriptorList) {
                            outputRow[columnPrefix + descriptor] = ""
                        }
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
        LOGGER.info("Descriptor calculation completed: processed rows")
    }

    /**
     * Computes a single molecular descriptor by name.
     *
     * @param mol the RDKit molecule object
     * @param name the descriptor name (must match one of the enum values)
     * @return the computed descriptor value (Double, Long, or Int)
     */
    private fun computeDescriptor(mol: ROMol, name: String): Any = when (name) {
        "AMW" -> RDKFuncs.calcAMW(mol, false)
        "ExactMW" -> RDKFuncs.calcExactMW(mol, false)
        "SlogP" -> RDKFuncs.calcMolLogP(mol)
        "SMR" -> RDKFuncs.calcMolMR(mol)
        "LabuteASA" -> RDKFuncs.calcLabuteASA(mol)
        "TPSA" -> RDKFuncs.calcTPSA(mol)
        "NumLipinskiHBA" -> RDKFuncs.calcLipinskiHBA(mol)
        "NumLipinskiHBD" -> RDKFuncs.calcLipinskiHBD(mol)
        "NumRotatableBonds" -> RDKFuncs.calcNumRotatableBonds(mol)
        "NumHBD" -> RDKFuncs.calcNumHBD(mol)
        "NumHBA" -> RDKFuncs.calcNumHBA(mol)
        "NumAmideBonds" -> RDKFuncs.calcNumAmideBonds(mol)
        "NumHeteroAtoms" -> RDKFuncs.calcNumHeteroatoms(mol)
        "NumHeavyAtoms" -> mol.getNumHeavyAtoms()
        "NumAtoms" -> mol.getNumAtoms(false)
        "NumStereocenters" -> {
            RDKFuncs.assignStereochemistry(mol)
            RDKFuncs.numAtomStereoCenters(mol)
        }
        "NumUnspecifiedStereocenters" -> {
            RDKFuncs.assignStereochemistry(mol)
            RDKFuncs.numUnspecifiedAtomStereoCenters(mol)
        }
        "NumRings" -> RDKFuncs.calcNumRings(mol)
        "NumAromaticRings" -> RDKFuncs.calcNumAromaticRings(mol)
        "NumSaturatedRings" -> RDKFuncs.calcNumSaturatedRings(mol)
        "NumAliphaticRings" -> RDKFuncs.calcNumAliphaticRings(mol)
        "NumAromaticHeterocycles" -> RDKFuncs.calcNumAromaticHeterocycles(mol)
        "NumSaturatedHeterocycles" -> RDKFuncs.calcNumSaturatedHeterocycles(mol)
        "NumAliphaticHeterocycles" -> RDKFuncs.calcNumAliphaticHeterocycles(mol)
        "NumAromaticCarbocycles" -> RDKFuncs.calcNumAromaticCarbocycles(mol)
        "NumSaturatedCarbocycles" -> RDKFuncs.calcNumSaturatedCarbocycles(mol)
        "NumAliphaticCarbocycles" -> RDKFuncs.calcNumAliphaticCarbocycles(mol)
        "FractionCSP3" -> RDKFuncs.calcFractionCSP3(mol)
        "Chi0v" -> RDKFuncs.calcChi0v(mol)
        "Chi1v" -> RDKFuncs.calcChi1v(mol)
        "Chi2v" -> RDKFuncs.calcChi2v(mol)
        "Chi3v" -> RDKFuncs.calcChi3v(mol)
        "Chi4v" -> RDKFuncs.calcChi4v(mol)
        "Chi1n" -> RDKFuncs.calcChi1n(mol)
        "Chi2n" -> RDKFuncs.calcChi2n(mol)
        "Chi3n" -> RDKFuncs.calcChi3n(mol)
        "Chi4n" -> RDKFuncs.calcChi4n(mol)
        "HallKierAlpha" -> RDKFuncs.calcHallKierAlpha(mol)
        "kappa1" -> RDKFuncs.calcKappa1(mol)
        "kappa2" -> RDKFuncs.calcKappa2(mol)
        "kappa3" -> RDKFuncs.calcKappa3(mol)
        else -> ""
    }
}
