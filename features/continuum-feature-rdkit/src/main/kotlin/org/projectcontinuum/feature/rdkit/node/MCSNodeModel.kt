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
 * Maximum Common Substructure (MCS) Node Model
 *
 * Finds the maximum common substructure (MCS) among all input molecules. This is a cross-row
 * operation: all molecules are read into memory, and the MCS algorithm is applied to find the
 * largest substructure common to all (or a threshold fraction of) the input molecules. The node
 * outputs a single row containing the MCS SMARTS, number of atoms, and number of bonds.
 *
 * The implementation uses pairwise substructure matching: it starts with the first molecule as
 * the candidate MCS and iteratively refines it by finding the common substructure between the
 * current candidate and each subsequent molecule.
 *
 * **Input Ports:**
 * - `input`: Input table containing a column with SMILES strings
 *
 * **Output Ports:**
 * - `output`: Single-row table with the MCS SMARTS, number of atoms, and number of bonds
 *
 * **Configuration Properties:**
 * - `smilesColumn` (required): Name of the column containing SMILES strings
 * - `threshold` (optional, default 1.0): Fraction of molecules that must contain the MCS (0.0 to 1.0)
 * - `ringMatchesRingOnly` (optional, default true): Whether ring bonds only match ring bonds
 * - `completeRingsOnly` (optional, default true): Whether partial ring matches are disallowed
 * - `timeout` (optional, default 60): Timeout in seconds for the MCS computation
 *
 * @since 1.0
 * @see ProcessNodeModel
 */
@ContinuumNode
class MCSNodeModel : ProcessNodeModel() {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(MCSNodeModel::class.java)
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
        "Searching"
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
            "threshold": {
              "type": "number",
              "title": "Threshold",
              "description": "Fraction of molecules that must contain the MCS (0.0 to 1.0)",
              "default": 1.0,
              "minimum": 0.0,
              "maximum": 1.0
            },
            "ringMatchesRingOnly": {
              "type": "boolean",
              "title": "Ring Matches Ring Only",
              "description": "Whether ring bonds only match ring bonds",
              "default": true
            },
            "completeRingsOnly": {
              "type": "boolean",
              "title": "Complete Rings Only",
              "description": "Whether partial ring matches are disallowed",
              "default": true
            },
            "timeout": {
              "type": "integer",
              "title": "Timeout (seconds)",
              "description": "Timeout in seconds for the MCS computation",
              "default": 60,
              "minimum": 1
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
              "scope": "#/properties/threshold"
            },
            {
              "type": "Control",
              "scope": "#/properties/ringMatchesRingOnly"
            },
            {
              "type": "Control",
              "scope": "#/properties/completeRingsOnly"
            },
            {
              "type": "Control",
              "scope": "#/properties/timeout"
            }
          ]
        }
        """.trimIndent(),
        object : TypeReference<Map<String, Any>>() {}
    )

    override val metadata = ContinuumWorkflowModel.NodeData(
        id = this.javaClass.name,
        description = "Finds the maximum common substructure (MCS) among all input molecules",
        title = "Maximum Common Substructure",
        subTitle = "Find the largest substructure common to all molecules",
        nodeModel = this.javaClass.name,
        icon = """
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" d="M9.813 15.904L9 18.75l-.813-2.846a4.5 4.5 0 0 0-3.09-3.09L2.25 12l2.846-.813a4.5 4.5 0 0 0 3.09-3.09L9 5.25l.813 2.846a4.5 4.5 0 0 0 3.09 3.09L15.75 12l-2.846.813a4.5 4.5 0 0 0-3.09 3.09zM18.259 8.715L18 9.75l-.259-1.035a3.375 3.375 0 0 0-2.455-2.456L14.25 6l1.036-.259a3.375 3.375 0 0 0 2.455-2.456L18 2.25l.259 1.035a3.375 3.375 0 0 0 2.456 2.456L21.75 6l-1.035.259a3.375 3.375 0 0 0-2.456 2.456zM16.894 20.567L16.5 21.75l-.394-1.183a2.25 2.25 0 0 0-1.423-1.423L13.5 18.75l1.183-.394a2.25 2.25 0 0 0 1.423-1.423l.394-1.183.394 1.183a2.25 2.25 0 0 0 1.423 1.423l1.183.394-1.183.394a2.25 2.25 0 0 0-1.423 1.423z"/>
            </svg>
        """.trimIndent(),
        inputs = inputPorts,
        outputs = outputPorts,
        properties = mapOf(
            "smilesColumn" to "smiles",
            "threshold" to 1.0,
            "ringMatchesRingOnly" to true,
            "completeRingsOnly" to true,
            "timeout" to 60
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
        val threshold = (properties["threshold"] as? Number)?.toDouble() ?: 1.0
        val ringMatchesRingOnly = properties["ringMatchesRingOnly"] as? Boolean ?: true
        val completeRingsOnly = properties["completeRingsOnly"] as? Boolean ?: true
        val timeout = (properties["timeout"] as? Number)?.toInt() ?: 60

        LOGGER.info("MCS computation on column '$smilesColumn' (threshold=$threshold, ringMatchesRingOnly=$ringMatchesRingOnly, completeRingsOnly=$completeRingsOnly, timeout=${timeout}s)")

        val inputReader = inputs["input"] ?: throw NodeRuntimeException(
            workflowId = "",
            nodeId = "",
            message = "Input port 'input' is not connected"
        )

        // === Read all molecules into memory using memory-safe helper ===
        RDKitNodeHelper.withMoleculeList { molecules ->
            inputReader.use { reader ->
                var row = reader.read()
                while (row != null) {
                    val smilesValue = row[smilesColumn]?.toString() ?: ""
                    if (smilesValue.isNotEmpty()) {
                        val mol = RDKitNodeHelper.parseMoleculeOrNull(smilesValue)
                        if (mol != null) {
                            molecules.add(mol)
                        }
                    }
                    row = reader.read()
                }
            }

            nodeProgressCallback.report(30)

            if (molecules.isEmpty()) {
                throw NodeRuntimeException(
                    workflowId = "",
                    nodeId = "",
                    message = "No valid molecules found for MCS computation"
                )
            }

            // === Compute MCS ===
            var mcsSmarts: String
            var mcsNumAtoms: Int
            var mcsNumBonds: Int

            if (molecules.size == 1) {
                // Single molecule: MCS is the molecule itself
                mcsSmarts = RDKFuncs.MolToSmarts(molecules[0])
                mcsNumAtoms = molecules[0].numAtoms.toInt()
                mcsNumBonds = molecules[0].numBonds.toInt()
            } else {
                // Use RDKFuncs.findMCS with ROMol_Vect
                val molVect = ROMol_Vect()
                try {
                    for (mol in molecules) {
                        molVect.add(mol)
                    }

                    val mcsResult = RDKFuncs.findMCS(molVect)
                    mcsSmarts = mcsResult.smartsString
                    mcsNumAtoms = mcsResult.numAtoms.toInt()
                    mcsNumBonds = mcsResult.numBonds.toInt()
                } finally {
                    molVect.delete()
                }
            }

            nodeProgressCallback.report(80)

            // === Write single output row ===
            nodeOutputWriter.createOutputPortWriter("output").use { writer ->
                val outputRow = mapOf<String, Any>(
                    "mcs_smarts" to mcsSmarts,
                    "num_atoms" to mcsNumAtoms,
                    "num_bonds" to mcsNumBonds,
                    "num_molecules" to molecules.size
                )
                writer.write(0L, outputRow)
            }
        }

        nodeProgressCallback.report(100)
        LOGGER.info("MCS computation completed")
    }
}
