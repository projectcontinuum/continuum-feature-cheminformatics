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
 * Functional Group Filter Node Model
 *
 * Filters molecules based on the presence and count of specified functional groups. Each
 * functional group is defined by a SMARTS pattern along with minimum and maximum count
 * constraints. This node is a splitter: molecules that satisfy all functional group criteria
 * are routed to the "pass" output port, while those that do not are routed to the "fail" port.
 *
 * **Input Ports:**
 * - `input`: Input table containing a column with SMILES strings
 *
 * **Output Ports:**
 * - `pass`: Table containing molecules that satisfy all functional group criteria
 * - `fail`: Table containing molecules that do not satisfy the criteria
 *
 * **Configuration Properties:**
 * - `smilesColumn` (required): Name of the column containing SMILES strings
 * - `functionalGroups` (required): Array of functional group definitions with name, smarts, minCount, and maxCount
 *
 * @since 1.0
 * @see ProcessNodeModel
 */
@ContinuumNode
class FunctionalGroupFilterNodeModel : ProcessNodeModel() {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(FunctionalGroupFilterNodeModel::class.java)
        private val objectMapper = ObjectMapper()
    }

    final override val inputPorts = mapOf(
        "input" to ContinuumWorkflowModel.NodePort(
            name = "input table",
            contentType = APPLICATION_OCTET_STREAM_VALUE
        )
    )

    final override val outputPorts = mapOf(
        "pass" to ContinuumWorkflowModel.NodePort(
            name = "pass table",
            contentType = APPLICATION_OCTET_STREAM_VALUE
        ),
        "fail" to ContinuumWorkflowModel.NodePort(
            name = "fail table",
            contentType = APPLICATION_OCTET_STREAM_VALUE
        )
    )

    override val categories = listOf(
        "RDKit/Searching"
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
            "functionalGroups": {
              "type": "array",
              "title": "Functional Groups",
              "description": "List of functional group definitions to filter by",
              "items": {
                "type": "object",
                "properties": {
                  "name": {
                    "type": "string",
                    "title": "Group Name",
                    "description": "Name of the functional group"
                  },
                  "smarts": {
                    "type": "string",
                    "title": "SMARTS Pattern",
                    "description": "SMARTS pattern defining the functional group"
                  },
                  "minCount": {
                    "type": "integer",
                    "title": "Minimum Count",
                    "description": "Minimum number of matches required (0 = no minimum)",
                    "default": 1
                  },
                  "maxCount": {
                    "type": "integer",
                    "title": "Maximum Count",
                    "description": "Maximum number of matches allowed (-1 = no maximum)",
                    "default": -1
                  }
                },
                "required": ["name", "smarts"]
              }
            }
          },
          "required": ["smilesColumn", "functionalGroups"]
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
              "scope": "#/properties/functionalGroups"
            }
          ]
        }
        """.trimIndent(),
        object : TypeReference<Map<String, Any>>() {}
    )

    override val metadata = ContinuumWorkflowModel.NodeData(
        id = this.javaClass.name,
        description = "Filters molecules by functional group presence and count using SMARTS patterns",
        title = "Functional Group Filter",
        subTitle = "Split molecules by functional group criteria",
        nodeModel = this.javaClass.name,
        icon = """
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" d="M9.75 3.104v5.714a2.25 2.25 0 0 1-.659 1.591L5.659 13.84A2.25 2.25 0 0 0 5 15.432v.318a2.25 2.25 0 0 0 2.25 2.25h9.5A2.25 2.25 0 0 0 19 15.75v-.318a2.25 2.25 0 0 0-.659-1.591l-3.432-3.432a2.25 2.25 0 0 1-.659-1.591V3.104"/>
            </svg>
        """.trimIndent(),
        inputs = inputPorts,
        outputs = outputPorts,
        properties = mapOf(
            "smilesColumn" to "smiles",
            "functionalGroups" to listOf(
                mapOf("name" to "Hydroxyl", "smarts" to "[OH]", "minCount" to 1, "maxCount" to -1)
            )
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
        val functionalGroupsRaw = properties["functionalGroups"] as? List<Map<String, Any>> ?: throw NodeRuntimeException(
            workflowId = "",
            nodeId = "",
            message = "functionalGroups is not provided"
        )

        if (functionalGroupsRaw.isEmpty()) {
            throw NodeRuntimeException(
                workflowId = "",
                nodeId = "",
                message = "functionalGroups must contain at least one entry"
            )
        }

        LOGGER.info("Functional group filtering on column '$smilesColumn' with ${functionalGroupsRaw.size} group(s)")

        val inputReader = inputs["input"] ?: throw NodeRuntimeException(
            workflowId = "",
            nodeId = "",
            message = "Input port 'input' is not connected"
        )

        // === Parse SMARTS patterns for each functional group ===
        data class FunctionalGroupDef(
            val name: String,
            val pattern: ROMol,
            val minCount: Int,
            val maxCount: Int
        )

        val groupDefs = mutableListOf<FunctionalGroupDef>()
        try {
            for (groupDef in functionalGroupsRaw) {
                val name = groupDef["name"]?.toString() ?: "unnamed"
                val smarts = groupDef["smarts"]?.toString() ?: throw NodeRuntimeException(
                    workflowId = "",
                    nodeId = "",
                    message = "Functional group '$name' is missing a SMARTS pattern"
                )
                val minCount = (groupDef["minCount"] as? Number)?.toInt() ?: 1
                val maxCount = (groupDef["maxCount"] as? Number)?.toInt() ?: -1

                val pattern = RDKFuncs.SmartsToMol(smarts) ?: throw NodeRuntimeException(
                    workflowId = "",
                    nodeId = "",
                    message = "Invalid SMARTS pattern for functional group '$name': $smarts"
                )
                groupDefs.add(FunctionalGroupDef(name, pattern, minCount, maxCount))
            }

            // === Process each row ===
            val passWriter = nodeOutputWriter.createOutputPortWriter("pass")
            val failWriter = nodeOutputWriter.createOutputPortWriter("fail")
            try {
                inputReader.use { reader ->
                    var row = reader.read()
                    var passRowNumber = 0L
                    var failRowNumber = 0L

                    while (row != null) {
                        val smilesValue = row[smilesColumn]?.toString() ?: ""
                        var passes = false

                        if (smilesValue.isNotEmpty()) {
                            val mol = RDKFuncs.SmilesToMol(smilesValue)
                            if (mol != null) {
                                try {
                                    passes = groupDefs.all { groupDef ->
                                        val matchCount = mol.getSubstructMatches(groupDef.pattern).size().toInt()
                                        val meetsMin = matchCount >= groupDef.minCount
                                        val meetsMax = groupDef.maxCount < 0 || matchCount <= groupDef.maxCount
                                        meetsMin && meetsMax
                                    }
                                } finally {
                                    mol.delete()
                                }
                            }
                        }

                        if (passes) {
                            passWriter.write(passRowNumber, row)
                            passRowNumber++
                        } else {
                            failWriter.write(failRowNumber, row)
                            failRowNumber++
                        }

                        row = reader.read()
                    }
                }
            } finally {
                passWriter.close()
                failWriter.close()
            }
        } finally {
            // === Clean up SMARTS patterns ===
            for (groupDef in groupDefs) {
                groupDef.pattern.delete()
            }
        }

        nodeProgressCallback.report(100)
        LOGGER.info("Functional group filtering completed")
    }
}
