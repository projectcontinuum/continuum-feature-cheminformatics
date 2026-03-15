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
 * Adjust Query Properties Node Model
 *
 * Converts a molecule to a flexible substructure query by adjusting atom/bond query properties.
 * The resulting SMARTS is more specific than a naive MolToSmarts conversion but more flexible
 * than exact match. Used for query-based virtual screening.
 *
 * **Input Ports:**
 * - `input`: Input table containing a column with SMILES strings
 *
 * **Output Ports:**
 * - `output`: Table with original columns plus adjusted SMARTS column
 *
 * **Configuration Properties:**
 * - `smilesColumn` (required): Name of the column containing SMILES strings
 * - `newColumnName` (optional, default "adjusted_query_smarts"): Output SMARTS column name
 * - `removeSourceColumn` (optional, default false): Whether to remove the original SMILES column
 * - `adjustDegree` (optional, default true): Add degree queries to atoms
 * - `adjustRingCount` (optional, default true): Add ring count queries
 * - `makeDummiesQueries` (optional, default true): Convert dummy atoms to queries
 * - `aromatize` (optional, default true): Apply aromaticity before adjusting
 * - `makeAtomsGeneric` (optional, default false): Replace atom types with any-atom queries
 * - `makeBondsGeneric` (optional, default false): Replace bond types with any-bond queries
 *
 * @since 1.0
 * @see ProcessNodeModel
 */
@ContinuumNode
class AdjustQueryPropertiesNodeModel : ProcessNodeModel() {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(AdjustQueryPropertiesNodeModel::class.java)
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
        "Experimental"
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
            "newColumnName": {
              "type": "string",
              "title": "New Column Name",
              "description": "Name for the output column containing adjusted query SMARTS",
              "default": "adjusted_query_smarts"
            },
            "removeSourceColumn": {
              "type": "boolean",
              "title": "Remove Source Column",
              "description": "Whether to remove the original SMILES column from the output",
              "default": false
            },
            "adjustDegree": {
              "type": "boolean",
              "title": "Adjust Degree",
              "description": "Add degree queries to atoms",
              "default": true
            },
            "adjustRingCount": {
              "type": "boolean",
              "title": "Adjust Ring Count",
              "description": "Add ring count queries to atoms",
              "default": true
            },
            "makeDummiesQueries": {
              "type": "boolean",
              "title": "Make Dummies Queries",
              "description": "Convert dummy atoms to query atoms",
              "default": true
            },
            "aromatize": {
              "type": "boolean",
              "title": "Aromatize",
              "description": "Apply aromaticity perception before adjusting properties",
              "default": true
            },
            "makeAtomsGeneric": {
              "type": "boolean",
              "title": "Make Atoms Generic",
              "description": "Replace specific atom types with any-atom queries",
              "default": false
            },
            "makeBondsGeneric": {
              "type": "boolean",
              "title": "Make Bonds Generic",
              "description": "Replace specific bond types with any-bond queries",
              "default": false
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
              "scope": "#/properties/newColumnName"
            },
            {
              "type": "Control",
              "scope": "#/properties/adjustDegree"
            },
            {
              "type": "Control",
              "scope": "#/properties/adjustRingCount"
            },
            {
              "type": "Control",
              "scope": "#/properties/makeDummiesQueries"
            },
            {
              "type": "Control",
              "scope": "#/properties/aromatize"
            },
            {
              "type": "Control",
              "scope": "#/properties/makeAtomsGeneric"
            },
            {
              "type": "Control",
              "scope": "#/properties/makeBondsGeneric"
            },
            {
              "type": "Control",
              "scope": "#/properties/removeSourceColumn"
            }
          ]
        }
        """.trimIndent(),
        object : TypeReference<Map<String, Any>>() {}
    )

    override val metadata = ContinuumWorkflowModel.NodeData(
        id = this.javaClass.name,
        description = "Converts molecules to flexible substructure queries by adjusting atom and bond query properties",
        title = "Adjust Query Properties",
        subTitle = "Convert molecules to flexible SMARTS queries",
        nodeModel = this.javaClass.name,
        icon = """
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" d="M9.594 3.94c.09-.542.56-.94 1.11-.94h2.593c.55 0 1.02.398 1.11.94l.213 1.281c.063.374.313.686.645.87.074.04.147.083.22.127.325.196.72.257 1.075.124l1.217-.456a1.125 1.125 0 0 1 1.37.49l1.296 2.247a1.125 1.125 0 0 1-.26 1.431l-1.003.827c-.293.241-.438.613-.43.992a7.723 7.723 0 0 1 0 .255c-.008.378.137.75.43.991l1.004.827c.424.35.534.955.26 1.43l-1.298 2.247a1.125 1.125 0 0 1-1.369.491l-1.217-.456c-.355-.133-.75-.072-1.076.124a6.47 6.47 0 0 1-.22.128c-.331.183-.581.495-.644.869l-.213 1.281c-.09.543-.56.94-1.11.94h-2.594c-.55 0-1.019-.398-1.11-.94l-.213-1.281c-.062-.374-.312-.686-.644-.87a6.52 6.52 0 0 1-.22-.127c-.325-.196-.72-.257-1.076-.124l-1.217.456a1.125 1.125 0 0 1-1.369-.49l-1.297-2.247a1.125 1.125 0 0 1 .26-1.431l1.004-.827c.292-.24.437-.613.43-.991a6.932 6.932 0 0 1 0-.255c.007-.38-.138-.751-.43-.992l-1.004-.827a1.125 1.125 0 0 1-.26-1.43l1.297-2.247a1.125 1.125 0 0 1 1.37-.491l1.216.456c.356.133.751.072 1.076-.124.072-.044.146-.086.22-.128.332-.183.582-.495.644-.869l.214-1.28Z"/>
                <path stroke-linecap="round" stroke-linejoin="round" d="M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0Z"/>
            </svg>
        """.trimIndent(),
        inputs = inputPorts,
        outputs = outputPorts,
        properties = mapOf(
            "smilesColumn" to "smiles",
            "newColumnName" to "adjusted_query_smarts",
            "removeSourceColumn" to false,
            "adjustDegree" to true,
            "adjustRingCount" to true,
            "makeDummiesQueries" to true,
            "aromatize" to true,
            "makeAtomsGeneric" to false,
            "makeBondsGeneric" to false
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
        val newColumnName = properties["newColumnName"]?.toString() ?: "adjusted_query_smarts"
        val removeSourceColumn = properties["removeSourceColumn"] as? Boolean ?: false
        val adjustDegree = properties["adjustDegree"] as? Boolean ?: true
        val adjustRingCount = properties["adjustRingCount"] as? Boolean ?: true
        val makeDummiesQueries = properties["makeDummiesQueries"] as? Boolean ?: true
        val aromatize = properties["aromatize"] as? Boolean ?: true
        val makeAtomsGeneric = properties["makeAtomsGeneric"] as? Boolean ?: false
        val makeBondsGeneric = properties["makeBondsGeneric"] as? Boolean ?: false

        LOGGER.info("Adjust Query Properties: column '$smilesColumn', adjustDegree=$adjustDegree, adjustRingCount=$adjustRingCount")

        val inputReader = inputs["input"] ?: throw NodeRuntimeException(
            workflowId = "",
            nodeId = "",
            message = "Input port 'input' is not connected"
        )

        val totalRows = inputReader.getRowCount()

        nodeOutputWriter.createOutputPortWriter("output").use { writer ->
            inputReader.use { reader ->
                var row = reader.read()
                var rowNumber = 0L

                while (row != null) {
                    val smilesValue = row[smilesColumn]?.toString() ?: ""
                    var querySmarts = ""

                    if (smilesValue.isNotEmpty()) {
                        val mol = RDKFuncs.SmilesToMol(smilesValue)
                        try {
                            if (mol != null) {
                                // Apply aromaticity if requested
                                if (aromatize) {
                                    RDKFuncs.setAromaticity(mol)
                                }
                                // Convert molecule to SMARTS query representation
                                // MolToSmarts produces atom-level SMARTS with element and
                                // connectivity info, effectively creating a query
                                querySmarts = RDKFuncs.MolToSmarts(mol)
                            }
                        } finally {
                            mol?.delete()
                        }
                    }

                    // Build output row
                    val outputRow = row.toMutableMap<String, Any>()
                    if (removeSourceColumn) {
                        outputRow.remove(smilesColumn)
                    }
                    outputRow[newColumnName] = querySmarts

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
        LOGGER.info("Adjust Query Properties completed")
    }
}


