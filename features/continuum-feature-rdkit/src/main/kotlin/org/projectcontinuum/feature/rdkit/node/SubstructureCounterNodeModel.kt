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
 * Substructure Counter Node Model
 *
 * Counts the number of substructure matches for each molecule against a set of query patterns
 * from a second input table. For each query, a new column is added to the output with the
 * match count. Optionally, a total hits column sums all query match counts per molecule.
 *
 * **Input Ports:**
 * - `molecules`: Input table containing molecules as SMILES strings
 * - `queries`: Input table containing query SMILES/SMARTS patterns and optional names
 *
 * **Output Ports:**
 * - `output`: Table with all original columns plus a count column per query and an optional total hits column
 *
 * **Configuration Properties:**
 * - `smilesColumn` (required): Name of the column containing SMILES strings in the molecules table
 * - `querySmilesColumn` (required): Name of the column containing query SMILES in the queries table
 * - `queryNameColumn` (optional, default ""): Column with query names (used as output column names)
 * - `uniqueMatchesOnly` (optional, default true): Whether to count only unique matches
 * - `addTotalHitsColumn` (optional, default true): Whether to add a total hits column
 * - `totalHitsColumnName` (optional, default "total_hits"): Name for the total hits column
 *
 * @since 1.0
 * @see ProcessNodeModel
 */
@ContinuumNode
class SubstructureCounterNodeModel : ProcessNodeModel() {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(SubstructureCounterNodeModel::class.java)
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
              "description": "Name of the column containing SMILES strings in the molecules table"
            },
            "querySmilesColumn": {
              "type": "string",
              "title": "Query SMILES Column",
              "description": "Name of the column containing query SMILES/SMARTS patterns in the queries table"
            },
            "queryNameColumn": {
              "type": "string",
              "title": "Query Name Column",
              "description": "Column with query names (used as output column names). Leave empty to use auto-generated names.",
              "default": ""
            },
            "uniqueMatchesOnly": {
              "type": "boolean",
              "title": "Unique Matches Only",
              "description": "Whether to count only unique substructure matches",
              "default": true
            },
            "addTotalHitsColumn": {
              "type": "boolean",
              "title": "Add Total Hits Column",
              "description": "Whether to add a column summing all query match counts",
              "default": true
            },
            "totalHitsColumnName": {
              "type": "string",
              "title": "Total Hits Column Name",
              "description": "Name for the total hits column",
              "default": "total_hits"
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
              "scope": "#/properties/queryNameColumn"
            },
            {
              "type": "Control",
              "scope": "#/properties/uniqueMatchesOnly"
            },
            {
              "type": "Control",
              "scope": "#/properties/addTotalHitsColumn"
            },
            {
              "type": "Control",
              "scope": "#/properties/totalHitsColumnName"
            }
          ]
        }
        """.trimIndent(),
        object : TypeReference<Map<String, Any>>() {}
    )

    override val metadata = ContinuumWorkflowModel.NodeData(
        id = this.javaClass.name,
        description = "Counts substructure matches for each molecule against a table of query patterns",
        title = "Substructure Counter",
        subTitle = "Count substructure matches per query pattern",
        nodeModel = this.javaClass.name,
        icon = """
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" d="M15.75 15.75V18m-7.5-6.75V18m15-8.25v7.5a2.25 2.25 0 0 1-2.25 2.25H3A2.25 2.25 0 0 1 .75 17.25v-7.5m22.5 0L12 2.25.75 9.75m22.5 0L12 17.25.75 9.75"/>
            </svg>
        """.trimIndent(),
        inputs = inputPorts,
        outputs = outputPorts,
        properties = mapOf(
            "smilesColumn" to "smiles",
            "querySmilesColumn" to "pattern",
            "queryNameColumn" to "pattern_name",
            "uniqueMatchesOnly" to true,
            "addTotalHitsColumn" to true,
            "totalHitsColumnName" to "total_hits"
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
        val queryNameColumn = properties["queryNameColumn"]?.toString() ?: ""
        val uniqueMatchesOnly = properties["uniqueMatchesOnly"] as? Boolean ?: true
        val addTotalHitsColumn = properties["addTotalHitsColumn"] as? Boolean ?: true
        val totalHitsColumnName = properties["totalHitsColumnName"]?.toString() ?: "total_hits"

        LOGGER.info("Substructure counter: molecules column '$smilesColumn', queries column '$querySmilesColumn', uniqueOnly=$uniqueMatchesOnly")

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

        // === Read ALL queries first ===
        data class QueryDef(val name: String, val mol: ROMol)

        val queryDefs = mutableListOf<QueryDef>()
        try {
            queriesReader.use { reader ->
                var row = reader.read()
                var queryIndex = 0
                while (row != null) {
                    val querySmilesValue = row[querySmilesColumn]?.toString() ?: ""
                    val queryName = if (queryNameColumn.isNotEmpty()) {
                        row[queryNameColumn]?.toString() ?: "query_$queryIndex"
                    } else {
                        "query_$queryIndex"
                    }

                    if (querySmilesValue.isNotEmpty()) {
                        // Try as SMARTS first, fall back to SMILES
                        val queryMol = RDKFuncs.SmartsToMol(querySmilesValue)
                            ?: RDKFuncs.SmilesToMol(querySmilesValue)
                        if (queryMol != null) {
                            queryDefs.add(QueryDef(queryName, queryMol))
                        }
                    }
                    queryIndex++
                    row = reader.read()
                }
            }

            nodeProgressCallback.report(30)

            // === Process each molecule against all queries ===
            nodeOutputWriter.createOutputPortWriter("output").use { writer ->
                moleculesReader.use { reader ->
                    var row = reader.read()
                    var rowNumber = 0L

                    while (row != null) {
                        val smilesValue = row[smilesColumn]?.toString() ?: ""
                        val outputRow = row.toMutableMap<String, Any>()
                        var totalHits = 0

                        if (smilesValue.isNotEmpty()) {
                            val mol = RDKFuncs.SmilesToMol(smilesValue)
                            if (mol != null) {
                                try {
                                    for (queryDef in queryDefs) {
                                        val matches = mol.getSubstructMatches(queryDef.mol)
                                        val count = matches.size().toInt()
                                        outputRow[queryDef.name] = count
                                        totalHits += count
                                    }
                                } finally {
                                    mol.delete()
                                }
                            } else {
                                // Invalid molecule: set all counts to 0
                                for (queryDef in queryDefs) {
                                    outputRow[queryDef.name] = 0
                                }
                            }
                        } else {
                            // Empty SMILES: set all counts to 0
                            for (queryDef in queryDefs) {
                                outputRow[queryDef.name] = 0
                            }
                        }

                        if (addTotalHitsColumn) {
                            outputRow[totalHitsColumnName] = totalHits
                        }

                        writer.write(rowNumber, outputRow)
                        rowNumber++

                        row = reader.read()
                    }
                }
            }
        } finally {
            // === Clean up query molecules ===
            for (queryDef in queryDefs) {
                queryDef.mol.delete()
            }
        }

        nodeProgressCallback.report(100)
        LOGGER.info("Substructure counter completed: ${queryDefs.size} queries processed")
    }
}
