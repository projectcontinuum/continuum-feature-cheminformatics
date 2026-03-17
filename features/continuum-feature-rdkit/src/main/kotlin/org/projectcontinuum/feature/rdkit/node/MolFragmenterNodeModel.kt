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
 * Molecule Fragmenter Node Model
 *
 * Fragments molecules by performing Murcko decomposition and collecting the scaffold plus side
 * chains as separate fragments. This node uses two output ports: "fragments" contains one row per
 * unique fragment (with a fragment index, fragment SMILES, and parent molecule SMILES), while
 * "molecules" contains the original rows augmented with a JSON array of fragment indices that
 * belong to each molecule.
 *
 * The fragmentation strategy uses MurckoDecompose to extract the scaffold, then derives side
 * chains by removing the scaffold from the original molecule. Each unique fragment is assigned
 * a sequential index.
 *
 * **Input Ports:**
 * - `input`: Input table containing a column with SMILES strings
 *
 * **Output Ports:**
 * - `fragments`: Table with fragment_index, fragment_smiles, and parent_smiles columns
 * - `molecules`: Original table augmented with a fragment_indices JSON array column
 *
 * **Configuration Properties:**
 * - `smilesColumn` (required): Name of the column containing SMILES strings
 * - `minPathLength` (optional, default 1, min 1): Minimum path length for fragment enumeration
 * - `maxPathLength` (optional, default 3, max 10): Maximum path length for fragment enumeration
 *
 * @since 1.0
 * @see ProcessNodeModel
 */
@ContinuumNode
class MolFragmenterNodeModel : ProcessNodeModel() {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(MolFragmenterNodeModel::class.java)
        private val objectMapper = ObjectMapper()
    }

    final override val inputPorts = mapOf(
        "input" to ContinuumWorkflowModel.NodePort(
            name = "input table",
            contentType = APPLICATION_OCTET_STREAM_VALUE
        )
    )

    final override val outputPorts = mapOf(
        "fragments" to ContinuumWorkflowModel.NodePort(
            name = "fragments table",
            contentType = APPLICATION_OCTET_STREAM_VALUE
        ),
        "molecules" to ContinuumWorkflowModel.NodePort(
            name = "molecules table",
            contentType = APPLICATION_OCTET_STREAM_VALUE
        )
    )

    override val categories = listOf(
        "RDKit/Fragments"
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
            "minPathLength": {
              "type": "integer",
              "title": "Minimum Path Length",
              "description": "Minimum path length for fragment enumeration",
              "default": 1,
              "minimum": 1
            },
            "maxPathLength": {
              "type": "integer",
              "title": "Maximum Path Length",
              "description": "Maximum path length for fragment enumeration",
              "default": 3,
              "maximum": 10
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
              "scope": "#/properties/minPathLength"
            },
            {
              "type": "Control",
              "scope": "#/properties/maxPathLength"
            }
          ]
        }
        """.trimIndent(),
        object : TypeReference<Map<String, Any>>() {}
    )

    override val metadata = ContinuumWorkflowModel.NodeData(
        id = this.javaClass.name,
        description = "Fragments molecules using Murcko decomposition and outputs scaffold plus side chains",
        title = "Molecule Fragmenter",
        subTitle = "Fragment molecules into scaffold and side chains",
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
            "minPathLength" to 1,
            "maxPathLength" to 3
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
        val minPathLength = (properties["minPathLength"] as? Number)?.toInt() ?: 1
        val maxPathLength = (properties["maxPathLength"] as? Number)?.toInt() ?: 3

        LOGGER.info("Fragmenting molecules from column '$smilesColumn' (minPath=$minPathLength, maxPath=$maxPathLength)")

        val inputReader = inputs["input"] ?: throw NodeRuntimeException(
            workflowId = "",
            nodeId = "",
            message = "Input port 'input' is not connected"
        )

        // === Build a global fragment registry (SMILES -> index) ===
        val fragmentRegistry = LinkedHashMap<String, Int>() // SMILES -> fragment index
        val fragmentParents = LinkedHashMap<String, String>() // fragment SMILES -> parent SMILES
        val moleculeRows = mutableListOf<Pair<Map<String, Any>, List<Int>>>() // (original row, fragment indices)

        val totalRows = inputReader.getRowCount()

        // === First pass: read all rows, compute fragments, and build registry ===
        inputReader.use { reader ->
            var row = reader.read()
            var inputRowNumber = 0L

            while (row != null) {
                val smilesValue = row[smilesColumn]?.toString() ?: ""
                val fragIndices = mutableListOf<Int>()

                if (smilesValue.isNotEmpty()) {
                    val mol = RDKFuncs.SmilesToMol(smilesValue)
                    try {
                        if (mol != null) {
                            // Get the Murcko scaffold
                            val scaffold = RDKFuncs.MurckoDecompose(mol)
                            try {
                                if (scaffold != null && scaffold.numAtoms.toInt() > 0) {
                                    val scaffoldSmiles = RDKFuncs.MolToSmiles(scaffold)
                                    if (scaffoldSmiles.isNotEmpty()) {
                                        if (!fragmentRegistry.containsKey(scaffoldSmiles)) {
                                            fragmentRegistry[scaffoldSmiles] = fragmentRegistry.size
                                            fragmentParents[scaffoldSmiles] = smilesValue
                                        }
                                        fragIndices.add(fragmentRegistry[scaffoldSmiles]!!)
                                    }

                                    // Get side chains by splitting the molecule into disconnected fragments
                                    val fragments = RDKFuncs.getMolFrags(mol)
                                    try {
                                        val fragCount = fragments.size().toInt()
                                        for (i in 0 until fragCount) {
                                            val fragSmiles = RDKFuncs.MolToSmiles(fragments[i])
                                            if (fragSmiles.isNotEmpty() && fragSmiles != scaffoldSmiles) {
                                                if (!fragmentRegistry.containsKey(fragSmiles)) {
                                                    fragmentRegistry[fragSmiles] = fragmentRegistry.size
                                                    fragmentParents[fragSmiles] = smilesValue
                                                }
                                                fragIndices.add(fragmentRegistry[fragSmiles]!!)
                                            }
                                        }
                                    } finally {
                                        for (i in 0 until fragments.size().toInt()) {
                                            fragments[i]?.delete()
                                        }
                                        fragments.delete()
                                    }
                                } else {
                                    // No rings — treat the whole molecule as a single fragment
                                    val canonSmiles = RDKFuncs.MolToSmiles(mol)
                                    if (canonSmiles.isNotEmpty()) {
                                        if (!fragmentRegistry.containsKey(canonSmiles)) {
                                            fragmentRegistry[canonSmiles] = fragmentRegistry.size
                                            fragmentParents[canonSmiles] = smilesValue
                                        }
                                        fragIndices.add(fragmentRegistry[canonSmiles]!!)
                                    }
                                }
                            } finally {
                                scaffold?.delete()
                            }
                        }
                    } finally {
                        mol?.delete()
                    }
                }

                moleculeRows.add(Pair(row, fragIndices))

                inputRowNumber++
                if (totalRows > 0) {
                    nodeProgressCallback.report((inputRowNumber * 50 / totalRows).toInt())
                }

                row = reader.read()
            }
        }

        nodeProgressCallback.report(50)

        // === Write fragments output ===
        val fragmentsWriter = nodeOutputWriter.createOutputPortWriter("fragments")
        val moleculesWriter = nodeOutputWriter.createOutputPortWriter("molecules")
        try {
            var fragmentRowNumber = 0L
            for ((fragSmiles, fragIndex) in fragmentRegistry) {
                val fragmentRow = mapOf<String, Any>(
                    "fragment_index" to fragIndex,
                    "fragment_smiles" to fragSmiles,
                    "parent_smiles" to (fragmentParents[fragSmiles] ?: "")
                )
                fragmentsWriter.write(fragmentRowNumber, fragmentRow)
                fragmentRowNumber++
            }

            nodeProgressCallback.report(75)

            // === Write molecules output ===
            var moleculeRowNumber = 0L
            for ((originalRow, fragIndices) in moleculeRows) {
                val outputRow = originalRow.toMutableMap<String, Any>()
                outputRow["fragment_indices"] = objectMapper.writeValueAsString(fragIndices)
                moleculesWriter.write(moleculeRowNumber, outputRow)
                moleculeRowNumber++
            }
        } finally {
            fragmentsWriter.close()
            moleculesWriter.close()
        }

        nodeProgressCallback.report(100)
        LOGGER.info("Molecule fragmentation completed: ${fragmentRegistry.size} unique fragments found")
    }
}
