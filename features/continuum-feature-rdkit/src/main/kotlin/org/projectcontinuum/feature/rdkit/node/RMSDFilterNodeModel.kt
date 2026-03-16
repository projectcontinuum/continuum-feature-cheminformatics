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
 * RMSD Filter Node Model
 *
 * Filters molecules using a greedy RMSD-based diversity selection. This is a splitter node that
 * reads all input molecules, generates 3D coordinates, and applies a greedy diversity filter based
 * on RMSD (root-mean-square deviation). The first molecule is always routed to the "above" (diverse)
 * output. Each subsequent molecule is compared against all molecules already in the "above" set:
 * if the minimum RMSD is greater than or equal to the threshold, it is added to "above"; otherwise
 * it is routed to "below" (redundant).
 *
 * **Input Ports:**
 * - `input`: Input table containing a column with SMILES strings
 *
 * **Output Ports:**
 * - `above`: Table containing diverse molecules (RMSD >= threshold from all other diverse molecules)
 * - `below`: Table containing redundant molecules (RMSD < threshold to at least one diverse molecule)
 *
 * **Configuration Properties:**
 * - `smilesColumn` (required): Name of the column containing SMILES strings
 * - `rmsdThreshold` (required, default 0.5): RMSD threshold for diversity filtering
 * - `ignoreHydrogens` (optional, default true): Whether to ignore hydrogens in RMSD calculation
 *
 * @since 1.0
 * @see ProcessNodeModel
 */
@ContinuumNode
class RMSDFilterNodeModel : ProcessNodeModel() {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(RMSDFilterNodeModel::class.java)
        private val objectMapper = ObjectMapper()
    }

    final override val inputPorts = mapOf(
        "input" to ContinuumWorkflowModel.NodePort(
            name = "input table",
            contentType = APPLICATION_OCTET_STREAM_VALUE
        )
    )

    final override val outputPorts = mapOf(
        "above" to ContinuumWorkflowModel.NodePort(
            name = "above table",
            contentType = APPLICATION_OCTET_STREAM_VALUE
        ),
        "below" to ContinuumWorkflowModel.NodePort(
            name = "below table",
            contentType = APPLICATION_OCTET_STREAM_VALUE
        )
    )

    override val categories = listOf(
        "RDKit",
        "Geometry"
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
            "rmsdThreshold": {
              "type": "number",
              "title": "RMSD Threshold",
              "description": "RMSD threshold for diversity filtering",
              "default": 0.5,
              "minimum": 0.0
            },
            "ignoreHydrogens": {
              "type": "boolean",
              "title": "Ignore Hydrogens",
              "description": "Whether to ignore hydrogens in RMSD calculation",
              "default": true
            }
          },
          "required": ["smilesColumn", "rmsdThreshold"]
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
              "scope": "#/properties/rmsdThreshold"
            },
            {
              "type": "Control",
              "scope": "#/properties/ignoreHydrogens"
            }
          ]
        }
        """.trimIndent(),
        object : TypeReference<Map<String, Any>>() {}
    )

    override val metadata = ContinuumWorkflowModel.NodeData(
        id = this.javaClass.name,
        description = "Filters molecules using a greedy RMSD-based diversity selection, splitting diverse and redundant molecules",
        title = "RMSD Filter",
        subTitle = "Greedy RMSD-based diversity filtering for molecules",
        nodeModel = this.javaClass.name,
        icon = """
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" d="M12 3c2.755 0 5.455.232 8.083.678.533.09.917.556.917 1.096v1.044a2.25 2.25 0 0 1-.659 1.591l-5.432 5.432a2.25 2.25 0 0 0-.659 1.591v2.927a2.25 2.25 0 0 1-1.244 2.013L9.75 21v-6.568a2.25 2.25 0 0 0-.659-1.591L3.659 7.409A2.25 2.25 0 0 1 3 5.818V4.774c0-.54.384-1.006.917-1.096A48.32 48.32 0 0 1 12 3z"/>
            </svg>
        """.trimIndent(),
        inputs = inputPorts,
        outputs = outputPorts,
        properties = mapOf(
            "smilesColumn" to "smiles",
            "rmsdThreshold" to 0.5,
            "ignoreHydrogens" to true
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
        val rmsdThreshold = (properties["rmsdThreshold"] as? Number)?.toDouble() ?: throw NodeRuntimeException(
            workflowId = "",
            nodeId = "",
            message = "rmsdThreshold is not provided"
        )
        val ignoreHydrogens = properties["ignoreHydrogens"] as? Boolean ?: true

        LOGGER.info("RMSD filtering column '$smilesColumn' with threshold=$rmsdThreshold (ignoreHydrogens=$ignoreHydrogens)")

        val inputReader = inputs["input"] ?: throw NodeRuntimeException(
            workflowId = "",
            nodeId = "",
            message = "Input port 'input' is not connected"
        )

        // === Read all rows and generate 3D coordinates ===
        data class MolEntry(
            val row: Map<String, Any>,
            val mol: ROMol?
        )

        val allEntries = mutableListOf<MolEntry>()

        inputReader.use { reader ->
            var row = reader.read()
            while (row != null) {
                val smilesValue = row[smilesColumn]?.toString() ?: ""
                var mol: ROMol? = null

                if (smilesValue.isNotEmpty()) {
                    val parsed = RDKitNodeHelper.parseMoleculeOrNull(smilesValue)
                    if (parsed != null) {
                        if (!ignoreHydrogens) {
                            RDKFuncs.addHs(parsed)
                        }
                        val params = RDKFuncs.getETKDGv3()
                        val embedResult = DistanceGeom.EmbedMolecule(parsed, params)
                        if (embedResult >= 0) {
                            mol = parsed
                        } else {
                            parsed.delete()
                        }
                    }
                }

                allEntries.add(MolEntry(row, mol))
                row = reader.read()
            }
        }

        nodeProgressCallback.report(40)

        // === Greedy diversity filter ===
        val diverseMols = mutableListOf<ROMol>()
        val aboveIndices = mutableSetOf<Int>()

        for (i in allEntries.indices) {
            val entry = allEntries[i]
            if (entry.mol == null) {
                // Invalid molecule — route to below
                continue
            }

            if (diverseMols.isEmpty()) {
                // First valid molecule — always "above"
                diverseMols.add(entry.mol)
                aboveIndices.add(i)
            } else {
                // Compute minimum RMSD against all diverse molecules
                var minRmsd = Double.MAX_VALUE
                for (diverseMol in diverseMols) {
                    val molCopy = ROMol(entry.mol)
                    try {
                        val rmsd = molCopy.alignMol(diverseMol)
                        if (rmsd < minRmsd) {
                            minRmsd = rmsd
                        }
                    } finally {
                        molCopy.delete()
                    }
                }

                if (minRmsd >= rmsdThreshold) {
                    diverseMols.add(entry.mol)
                    aboveIndices.add(i)
                }
            }
        }

        nodeProgressCallback.report(70)

        // === Write results to above and below ports ===
        val aboveWriter = nodeOutputWriter.createOutputPortWriter("above")
        val belowWriter = nodeOutputWriter.createOutputPortWriter("below")
        try {
            var aboveRowNumber = 0L
            var belowRowNumber = 0L

            for (i in allEntries.indices) {
                if (aboveIndices.contains(i)) {
                    aboveWriter.write(aboveRowNumber, allEntries[i].row)
                    aboveRowNumber++
                } else {
                    belowWriter.write(belowRowNumber, allEntries[i].row)
                    belowRowNumber++
                }
            }
        } finally {
            aboveWriter.close()
            belowWriter.close()
        }

        // === Clean up molecules ===
        for (entry in allEntries) {
            entry.mol?.delete()
        }

        nodeProgressCallback.report(100)
        LOGGER.info("RMSD filtering completed: ${aboveIndices.size} above, ${allEntries.size - aboveIndices.size} below")
    }
}
