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
 * Molecule Catalog Filter Node Model
 *
 * Filters molecules against structural alert catalogs (PAINS, BRENK, NIH, ZINC).
 * Molecules matching any catalog entry are routed to the "flagged" output, while
 * clean molecules are routed to the "clean" output. This is essential for removing
 * problematic compounds in HTS (High-Throughput Screening) campaigns.
 *
 * **Input Ports:**
 * - `input`: Input table containing a column with SMILES strings
 *
 * **Output Ports:**
 * - `clean`: Table with molecules passing all catalog filters (no matches)
 * - `flagged`: Table with molecules matching one or more catalog entries
 *
 * **Configuration Properties:**
 * - `smilesColumn` (required): Name of the column containing SMILES strings
 * - `catalogs` (required): Array of catalog names to check against
 * - `addMatchDetailsColumn` (optional, default true): Add column with match details
 * - `matchDetailsColumnName` (optional, default "catalog_matches"): Name for match details column
 * - `addMatchCountColumn` (optional, default true): Add column with match count
 * - `matchCountColumnName` (optional, default "catalog_match_count"): Name for match count column
 *
 * @since 1.0
 * @see ProcessNodeModel
 */
@ContinuumNode
class MoleculeCatalogFilterNodeModel : ProcessNodeModel() {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(MoleculeCatalogFilterNodeModel::class.java)
        private val objectMapper = ObjectMapper()
    }

    final override val inputPorts = mapOf(
        "input" to ContinuumWorkflowModel.NodePort(
            name = "input table",
            contentType = APPLICATION_OCTET_STREAM_VALUE
        )
    )

    final override val outputPorts = mapOf(
        "clean" to ContinuumWorkflowModel.NodePort(
            name = "clean table",
            contentType = APPLICATION_OCTET_STREAM_VALUE
        ),
        "flagged" to ContinuumWorkflowModel.NodePort(
            name = "flagged table",
            contentType = APPLICATION_OCTET_STREAM_VALUE
        )
    )

    override val categories = listOf(
        "RDKit/Experimental"
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
            "catalogs": {
              "type": "array",
              "title": "Catalogs",
              "description": "Filter catalogs to check against",
              "items": {
                "type": "string",
                "enum": ["PAINS_A", "PAINS_B", "PAINS_C", "BRENK", "NIH", "ZINC", "ALL"]
              },
              "default": ["PAINS_A"]
            },
            "addMatchDetailsColumn": {
              "type": "boolean",
              "title": "Add Match Details Column",
              "description": "Add a column with details of catalog matches",
              "default": true
            },
            "matchDetailsColumnName": {
              "type": "string",
              "title": "Match Details Column Name",
              "description": "Name for the match details column",
              "default": "catalog_matches"
            },
            "addMatchCountColumn": {
              "type": "boolean",
              "title": "Add Match Count Column",
              "description": "Add a column with the number of catalog matches",
              "default": true
            },
            "matchCountColumnName": {
              "type": "string",
              "title": "Match Count Column Name",
              "description": "Name for the match count column",
              "default": "catalog_match_count"
            }
          },
          "required": ["smilesColumn", "catalogs"]
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
              "scope": "#/properties/catalogs"
            },
            {
              "type": "Control",
              "scope": "#/properties/addMatchDetailsColumn"
            },
            {
              "type": "Control",
              "scope": "#/properties/matchDetailsColumnName"
            },
            {
              "type": "Control",
              "scope": "#/properties/addMatchCountColumn"
            },
            {
              "type": "Control",
              "scope": "#/properties/matchCountColumnName"
            }
          ]
        }
        """.trimIndent(),
        object : TypeReference<Map<String, Any>>() {}
    )

    override val metadata = ContinuumWorkflowModel.NodeData(
        id = this.javaClass.name,
        description = "Filters molecules against structural alert catalogs (PAINS, BRENK, NIH, ZINC) using RDKit FilterCatalog",
        title = "Molecule Catalog Filter",
        subTitle = "Flag molecules matching structural alert catalogs",
        nodeModel = this.javaClass.name,
        icon = """
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" d="M12 9v3.75m-9.303 3.376c-.866 1.5.217 3.374 1.948 3.374h14.71c1.73 0 2.813-1.874 1.948-3.374L13.949 3.378c-.866-1.5-3.032-1.5-3.898 0L2.697 16.126ZM12 15.75h.007v.008H12v-.008Z"/>
            </svg>
        """.trimIndent(),
        inputs = inputPorts,
        outputs = outputPorts,
        properties = mapOf(
            "smilesColumn" to "smiles",
            "catalogs" to listOf("PAINS_A"),
            "addMatchDetailsColumn" to true,
            "matchDetailsColumnName" to "catalog_matches",
            "addMatchCountColumn" to true,
            "matchCountColumnName" to "catalog_match_count"
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
        @Suppress("UNCHECKED_CAST")
        val catalogs = (properties["catalogs"] as? List<String>) ?: throw NodeRuntimeException(
            workflowId = "",
            nodeId = "",
            message = "catalogs is not provided"
        )
        val addMatchDetailsColumn = properties["addMatchDetailsColumn"] as? Boolean ?: true
        val matchDetailsColumnName = properties["matchDetailsColumnName"]?.toString() ?: "catalog_matches"
        val addMatchCountColumn = properties["addMatchCountColumn"] as? Boolean ?: true
        val matchCountColumnName = properties["matchCountColumnName"]?.toString() ?: "catalog_match_count"

        LOGGER.info("Molecule Catalog Filter: column '$smilesColumn', catalogs $catalogs")

        val inputReader = inputs["input"] ?: throw NodeRuntimeException(
            workflowId = "",
            nodeId = "",
            message = "Input port 'input' is not connected"
        )

        // Initialize FilterCatalog
        val catalogParams = FilterCatalogParams()
        for (catalog in catalogs) {
            when (catalog) {
                "PAINS_A" -> catalogParams.addCatalog(FilterCatalogParams.FilterCatalogs.PAINS_A)
                "PAINS_B" -> catalogParams.addCatalog(FilterCatalogParams.FilterCatalogs.PAINS_B)
                "PAINS_C" -> catalogParams.addCatalog(FilterCatalogParams.FilterCatalogs.PAINS_C)
                "BRENK" -> catalogParams.addCatalog(FilterCatalogParams.FilterCatalogs.BRENK)
                "NIH" -> catalogParams.addCatalog(FilterCatalogParams.FilterCatalogs.NIH)
                "ZINC" -> catalogParams.addCatalog(FilterCatalogParams.FilterCatalogs.ZINC)
                "ALL" -> catalogParams.addCatalog(FilterCatalogParams.FilterCatalogs.ALL)
            }
        }
        val filterCatalog = FilterCatalog(catalogParams)

        try {
            val totalRows = inputReader.getRowCount()

            nodeOutputWriter.createOutputPortWriter("clean").use { cleanWriter ->
                nodeOutputWriter.createOutputPortWriter("flagged").use { flaggedWriter ->
                    inputReader.use { reader ->
                        var row = reader.read()
                        var rowNumber = 0L
                        var cleanRowNumber = 0L
                        var flaggedRowNumber = 0L

                        while (row != null) {
                            val smilesValue = row[smilesColumn]?.toString() ?: ""
                            var matchCount = 0
                            var matchDetails = ""

                            if (smilesValue.isNotEmpty()) {
                                RDKitNodeHelper.withMolecule(smilesValue) { mol ->
                                    val matches = filterCatalog.getMatches(mol)
                                    try {
                                        matchCount = matches.size().toInt()
                                        if (matchCount > 0) {
                                            val details = mutableListOf<String>()
                                            for (i in 0 until matchCount) {
                                                val entry = matches[i]
                                                try {
                                                    details.add(entry.getDescription())
                                                } catch (e: Exception) {
                                                    details.add("match_$i")
                                                }
                                            }
                                            matchDetails = details.joinToString("; ")
                                        }
                                    } finally {
                                        matches.delete()
                                    }
                                }
                            }

                            val outputRow = row.toMutableMap<String, Any>()
                            if (addMatchDetailsColumn) {
                                outputRow[matchDetailsColumnName] = matchDetails
                            }
                            if (addMatchCountColumn) {
                                outputRow[matchCountColumnName] = matchCount
                            }

                            if (matchCount == 0) {
                                cleanWriter.write(cleanRowNumber, outputRow)
                                cleanRowNumber++
                            } else {
                                flaggedWriter.write(flaggedRowNumber, outputRow)
                                flaggedRowNumber++
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
        } finally {
            filterCatalog.delete()
            catalogParams.delete()
        }

        nodeProgressCallback.report(100)
        LOGGER.info("Molecule Catalog Filter completed")
    }
}

