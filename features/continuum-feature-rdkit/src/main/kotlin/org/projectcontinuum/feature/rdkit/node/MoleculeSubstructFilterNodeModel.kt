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
 * Molecule Substructure Filter Node Model
 *
 * Filters molecules from a first input table against a set of SMILES-based substructure queries
 * from a second input table. This node is a splitter with two input ports and two output ports.
 * All queries are read first, then each molecule is checked against the queries. Depending on the
 * match mode ("any" or "all"), a molecule matches if it contains at least one query substructure
 * or all query substructures, respectively.
 *
 * **Input Ports:**
 * - `molecules`: Input table containing molecules as SMILES strings
 * - `queries`: Input table containing query SMILES strings
 *
 * **Output Ports:**
 * - `match`: Table containing molecules that match the query criteria
 * - `noMatch`: Table containing molecules that do not match
 *
 * **Configuration Properties:**
 * - `smilesColumn` (required): Name of the column containing SMILES strings in the molecules table
 * - `querySmilesColumn` (required): Name of the column containing query SMILES in the queries table
 * - `matchMode` (optional, default "any"): Match mode — "any" (match if any query matches) or "all" (match if all queries match)
 *
 * @since 1.0
 * @see ProcessNodeModel
 */
@ContinuumNode
class MoleculeSubstructFilterNodeModel : ProcessNodeModel() {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(MoleculeSubstructFilterNodeModel::class.java)
        private val objectMapper = ObjectMapper()
    }

    final override val inputPorts = mapOf(
        "molecules" to ContinuumWorkflowModel.NodePort(
            name = "molecules table",
            contentType = APPLICATION_OCTET_STREAM_VALUE
        ),
        "queries" to ContinuumWorkflowModel.NodePort(
            name = "queries table",
            contentType = APPLICATION_OCTET_STREAM_VALUE
        )
    )

    final override val outputPorts = mapOf(
        "match" to ContinuumWorkflowModel.NodePort(
            name = "match table",
            contentType = APPLICATION_OCTET_STREAM_VALUE
        ),
        "noMatch" to ContinuumWorkflowModel.NodePort(
            name = "no match table",
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
              "description": "Name of the column containing SMILES strings in the molecules table"
            },
            "querySmilesColumn": {
              "type": "string",
              "title": "Query SMILES Column",
              "description": "Name of the column containing query SMILES in the queries table"
            },
            "matchMode": {
              "type": "string",
              "title": "Match Mode",
              "description": "Whether to match any or all queries",
              "enum": ["any", "all"],
              "default": "any"
            }
          },
          "required": ["smilesColumn", "querySmilesColumn"]
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
              "scope": "#/properties/querySmilesColumn"
            },
            {
              "type": "Control",
              "scope": "#/properties/matchMode"
            }
          ]
        }
        """.trimIndent(),
        object : TypeReference<Map<String, Any>>() {}
    )

    override val metadata = ContinuumWorkflowModel.NodeData(
        id = this.javaClass.name,
        description = "Filters molecules against a table of SMILES-based substructure queries, splitting matches and non-matches",
        title = "Molecule Substructure Filter",
        subTitle = "Filter molecules using a query table of substructures",
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
            "querySmilesColumn" to "query_smiles",
            "matchMode" to "any"
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
        val querySmilesColumn = properties["querySmilesColumn"] as String? ?: throw NodeRuntimeException(
            workflowId = "",
            nodeId = "",
            message = "querySmilesColumn is not provided"
        )
        val matchMode = properties["matchMode"]?.toString() ?: "any"

        LOGGER.info("Molecule substructure filter: molecules column '$smilesColumn', queries column '$querySmilesColumn', mode=$matchMode")

        val moleculesReader = inputs["molecules"] ?: throw NodeRuntimeException(
            workflowId = "",
            nodeId = "",
            message = "Input port 'molecules' is not connected"
        )
        val queriesReader = inputs["queries"] ?: throw NodeRuntimeException(
            workflowId = "",
            nodeId = "",
            message = "Input port 'queries' is not connected"
        )

        // === Read ALL queries first using memory-safe helper ===
        RDKitNodeHelper.withMoleculeList { queryMols ->
            queriesReader.use { reader ->
                var row = reader.read()
                while (row != null) {
                    val querySmilesValue = row[querySmilesColumn]?.toString() ?: ""
                    if (querySmilesValue.isNotEmpty()) {
                        // Try SMARTS first (for substructure queries), fall back to SMILES
                        val queryMol = RDKitNodeHelper.parseSmartsOrNull(querySmilesValue)
                            ?: RDKitNodeHelper.parseMoleculeOrNull(querySmilesValue)
                        if (queryMol != null) {
                            queryMols.add(queryMol)
                        }
                    }
                    row = reader.read()
                }
            }

            nodeProgressCallback.report(30)

            if (queryMols.isEmpty()) {
                LOGGER.warn("No valid query molecules found — all molecules will be sent to noMatch")
            }

            // === Process each molecule against all queries ===
            val matchWriter = nodeOutputWriter.createOutputPortWriter("match")
            val noMatchWriter = nodeOutputWriter.createOutputPortWriter("noMatch")
            try {
                moleculesReader.use { reader ->
                    var row = reader.read()
                    var matchRowNumber = 0L
                    var noMatchRowNumber = 0L

                    while (row != null) {
                        val smilesValue = row[smilesColumn]?.toString() ?: ""
                        var matched = false

                        if (smilesValue.isNotEmpty() && queryMols.isNotEmpty()) {
                            matched = RDKitNodeHelper.withMolecule(smilesValue) { mol ->
                                when (matchMode) {
                                    "all" -> queryMols.all { query -> mol.hasSubstructMatch(query) }
                                    else -> queryMols.any { query -> mol.hasSubstructMatch(query) }
                                }
                            } ?: false
                        }

                        if (matched) {
                            matchWriter.write(matchRowNumber, row)
                            matchRowNumber++
                        } else {
                            noMatchWriter.write(noMatchRowNumber, row)
                            noMatchRowNumber++
                        }

                        row = reader.read()
                    }
                }
            } finally {
                matchWriter.close()
                noMatchWriter.close()
            }
        }

        nodeProgressCallback.report(100)
        LOGGER.info("Molecule substructure filter completed")
    }
}
