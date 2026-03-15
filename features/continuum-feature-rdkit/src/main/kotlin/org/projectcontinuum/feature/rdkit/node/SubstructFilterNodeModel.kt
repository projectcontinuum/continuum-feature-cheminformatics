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
 * Substructure Filter Node Model
 *
 * Filters molecules based on a SMARTS substructure query. This node is a splitter: molecules
 * that contain the specified substructure are routed to the "match" output port, while molecules
 * that do not match are routed to the "noMatch" output port. The SMARTS query is parsed once
 * and reused for all rows.
 *
 * **Input Ports:**
 * - `input`: Input table containing a column with SMILES strings
 *
 * **Output Ports:**
 * - `match`: Table containing molecules that match the SMARTS query
 * - `noMatch`: Table containing molecules that do not match the SMARTS query
 *
 * **Configuration Properties:**
 * - `smilesColumn` (required): Name of the column containing SMILES strings
 * - `smartsQuery` (required): SMARTS pattern to match against
 * - `useChirality` (optional, default false): Whether to use chirality in substructure matching
 *
 * @since 1.0
 * @see ProcessNodeModel
 */
@ContinuumNode
class SubstructFilterNodeModel : ProcessNodeModel() {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(SubstructFilterNodeModel::class.java)
        private val objectMapper = ObjectMapper()
    }

    final override val inputPorts = mapOf(
        "input" to ContinuumWorkflowModel.NodePort(
            name = "input table",
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
              "description": "Name of the column containing SMILES strings"
            },
            "smartsQuery": {
              "type": "string",
              "title": "SMARTS Query",
              "description": "SMARTS pattern to match against molecules"
            },
            "useChirality": {
              "type": "boolean",
              "title": "Use Chirality",
              "description": "Whether to use chirality in substructure matching",
              "default": false
            }
          },
          "required": ["smilesColumn", "smartsQuery"]
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
              "scope": "#/properties/smartsQuery"
            },
            {
              "type": "Control",
              "scope": "#/properties/useChirality"
            }
          ]
        }
        """.trimIndent(),
        object : TypeReference<Map<String, Any>>() {}
    )

    override val metadata = ContinuumWorkflowModel.NodeData(
        id = this.javaClass.name,
        description = "Filters molecules by SMARTS substructure query, splitting matches and non-matches into separate output ports",
        title = "Substructure Filter",
        subTitle = "Split molecules by SMARTS substructure match",
        nodeModel = this.javaClass.name,
        icon = """
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" d="m21 21-5.197-5.197m0 0A7.5 7.5 0 1 0 5.196 5.196a7.5 7.5 0 0 0 10.607 10.607z"/>
            </svg>
        """.trimIndent(),
        inputs = inputPorts,
        outputs = outputPorts,
        properties = mapOf(
            "smilesColumn" to "smiles",
            "smartsQuery" to "[OH]",
            "useChirality" to false
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
        val smartsQuery = properties["smartsQuery"] as String? ?: throw NodeRuntimeException(
            workflowId = "",
            nodeId = "",
            message = "smartsQuery is not provided"
        )
        val useChirality = properties["useChirality"] as? Boolean ?: false

        LOGGER.info("Substructure filtering column '$smilesColumn' with SMARTS '$smartsQuery' (chirality=$useChirality)")

        val inputReader = inputs["input"] ?: throw NodeRuntimeException(
            workflowId = "",
            nodeId = "",
            message = "Input port 'input' is not connected"
        )

        // === Parse the SMARTS query once ===
        val query = RDKFuncs.SmartsToMol(smartsQuery) ?: throw NodeRuntimeException(
            workflowId = "",
            nodeId = "",
            message = "Invalid SMARTS query: $smartsQuery"
        )

        val matchWriter = nodeOutputWriter.createOutputPortWriter("match")
        val noMatchWriter = nodeOutputWriter.createOutputPortWriter("noMatch")
        try {
            inputReader.use { reader ->
                var row = reader.read()
                var matchRowNumber = 0L
                var noMatchRowNumber = 0L

                while (row != null) {
                    val smilesValue = row[smilesColumn]?.toString() ?: ""
                    var matched = false

                    if (smilesValue.isNotEmpty()) {
                        val mol = RDKFuncs.SmilesToMol(smilesValue)
                        if (mol != null) {
                            try {
                                matched = mol.hasSubstructMatch(query)
                            } finally {
                                mol.delete()
                            }
                        }
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
            query.delete()
        }

        nodeProgressCallback.report(100)
        LOGGER.info("Substructure filtering completed")
    }
}
