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
 * R-Group Decomposition Node Model
 *
 * Decomposes molecules into a core scaffold and R-groups (substituents) using RDKit's
 * RGroupDecomposition. Given a core SMARTS pattern, identifies what functional groups are
 * attached at each labeled position across a set of molecules. This is a key tool for
 * Structure-Activity Relationship (SAR) analysis.
 *
 * **Input Ports:**
 * - `input`: Input table containing a column with SMILES strings
 *
 * **Output Ports:**
 * - `output`: Table with original columns plus core SMILES and R-group SMILES columns (R1, R2, ...)
 *
 * **Configuration Properties:**
 * - `smilesColumn` (required): Name of the column containing SMILES strings
 * - `coreSmarts` (required): SMARTS pattern defining the core scaffold for decomposition
 * - `coreColumnName` (optional, default "core"): Name for the core output column
 * - `rGroupPrefix` (optional, default "R"): Prefix for R-group columns (R1, R2, ...)
 * - `matchOnlyAtRGroups` (optional, default false): Only decompose at marked R-group positions
 * - `removeSourceColumn` (optional, default false): Whether to remove the original SMILES column
 *
 * @since 1.0
 * @see ProcessNodeModel
 */
@ContinuumNode
class RGroupDecompositionNodeModel : ProcessNodeModel() {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(RGroupDecompositionNodeModel::class.java)
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
            "coreSmarts": {
              "type": "string",
              "title": "Core SMARTS",
              "description": "SMARTS pattern defining the core scaffold for decomposition"
            },
            "coreColumnName": {
              "type": "string",
              "title": "Core Column Name",
              "description": "Name for the output column containing the core SMILES",
              "default": "core"
            },
            "rGroupPrefix": {
              "type": "string",
              "title": "R-Group Prefix",
              "description": "Prefix for R-group columns (e.g. R produces R1, R2, ...)",
              "default": "R"
            },
            "matchOnlyAtRGroups": {
              "type": "boolean",
              "title": "Match Only At R-Groups",
              "description": "Only decompose at explicitly marked R-group positions in the core",
              "default": false
            },
            "removeSourceColumn": {
              "type": "boolean",
              "title": "Remove Source Column",
              "description": "Whether to remove the original SMILES column from the output",
              "default": false
            }
          },
          "required": ["smilesColumn", "coreSmarts"]
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
              "scope": "#/properties/coreSmarts"
            },
            {
              "type": "Control",
              "scope": "#/properties/coreColumnName"
            },
            {
              "type": "Control",
              "scope": "#/properties/rGroupPrefix"
            },
            {
              "type": "Control",
              "scope": "#/properties/matchOnlyAtRGroups"
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
        description = "Decomposes molecules into a core scaffold and R-groups using RDKit RGroupDecomposition",
        title = "R-Group Decomposition",
        subTitle = "Identify substituents on a core scaffold",
        nodeModel = this.javaClass.name,
        icon = """
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" d="M3.75 6A2.25 2.25 0 0 1 6 3.75h2.25A2.25 2.25 0 0 1 10.5 6v2.25a2.25 2.25 0 0 1-2.25 2.25H6a2.25 2.25 0 0 1-2.25-2.25V6ZM3.75 15.75A2.25 2.25 0 0 1 6 13.5h2.25a2.25 2.25 0 0 1 2.25 2.25V18a2.25 2.25 0 0 1-2.25 2.25H6A2.25 2.25 0 0 1 3.75 18v-2.25ZM13.5 6a2.25 2.25 0 0 1 2.25-2.25H18A2.25 2.25 0 0 1 20.25 6v2.25A2.25 2.25 0 0 1 18 10.5h-2.25a2.25 2.25 0 0 1-2.25-2.25V6ZM13.5 15.75a2.25 2.25 0 0 1 2.25-2.25H18a2.25 2.25 0 0 1 2.25 2.25V18A2.25 2.25 0 0 1 18 20.25h-2.25a2.25 2.25 0 0 1-2.25-2.25v-2.25Z"/>
            </svg>
        """.trimIndent(),
        inputs = inputPorts,
        outputs = outputPorts,
        properties = mapOf(
            "smilesColumn" to "smiles",
            "coreSmarts" to "c1ccc([*:1])cc1[*:2]",
            "coreColumnName" to "core",
            "rGroupPrefix" to "R",
            "matchOnlyAtRGroups" to false,
            "removeSourceColumn" to false
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
        val coreSmarts = properties["coreSmarts"] as String? ?: throw NodeRuntimeException(
            workflowId = "",
            nodeId = "",
            message = "coreSmarts is not provided"
        )
        val coreColumnName = properties["coreColumnName"]?.toString() ?: "core"
        val rGroupPrefix = properties["rGroupPrefix"]?.toString() ?: "R"
        val matchOnlyAtRGroups = properties["matchOnlyAtRGroups"] as? Boolean ?: false
        val removeSourceColumn = properties["removeSourceColumn"] as? Boolean ?: false

        LOGGER.info("R-Group Decomposition: column '$smilesColumn', core '$coreSmarts'")

        val inputReader = inputs["input"] ?: throw NodeRuntimeException(
            workflowId = "",
            nodeId = "",
            message = "Input port 'input' is not connected"
        )

        // Parse core SMARTS
        val coreMol = RDKFuncs.SmartsToMol(coreSmarts) ?: throw NodeRuntimeException(
            workflowId = "",
            nodeId = "",
            message = "Invalid core SMARTS: $coreSmarts"
        )

        try {
            // Read ALL molecules first (R-group decomposition works on the full set)
            val allRows = mutableListOf<Map<String, Any>>()
            val allMols = mutableListOf<ROMol?>()

            inputReader.use { reader ->
                var row = reader.read()
                while (row != null) {
                    allRows.add(row)
                    val smilesValue = row[smilesColumn]?.toString() ?: ""
                    if (smilesValue.isNotEmpty()) {
                        allMols.add(RDKFuncs.SmilesToMol(smilesValue))
                    } else {
                        allMols.add(null)
                    }
                    row = reader.read()
                }
            }

            nodeProgressCallback.report(20)

            // Create RGroupDecomposition and add molecules
            val rgd = RGroupDecomposition(coreMol)
            val addResults = mutableListOf<Int>()
            try {
                for (mol in allMols) {
                    if (mol != null) {
                        addResults.add(rgd.add(mol))
                    } else {
                        addResults.add(-1)
                    }
                }

                // Process the decomposition
                val processResult = rgd.process()

                nodeProgressCallback.report(60)

                // Get results as rows — each row is a StringMolMap (key→ROMol)
                val rGroupRows = rgd.getRGroupsAsRows()

                // Build output
                nodeOutputWriter.createOutputPortWriter("output").use { writer ->
                    var successIndex = 0
                    for (i in allRows.indices) {
                        val outputRow = allRows[i].toMutableMap<String, Any>()
                        if (removeSourceColumn) {
                            outputRow.remove(smilesColumn)
                        }

                        if (addResults[i] >= 0 && successIndex < rGroupRows.size().toInt()) {
                            // This molecule matched the core — extract R-groups from the row
                            val rgRow = rGroupRows[successIndex]
                            // Iterate keys using the Str_Vect returned by keys()
                            val keyVect = rgRow.keys()
                            try {
                                for (j in 0 until keyVect.size().toInt()) {
                                    val key = keyVect[j]
                                    val colName = if (key.equals("Core", ignoreCase = true)) {
                                        coreColumnName
                                    } else {
                                        "$rGroupPrefix${key.removePrefix("R")}"
                                    }
                                    try {
                                        val rMol = rgRow[key]
                                        outputRow[colName] = if (rMol != null) RDKFuncs.MolToSmiles(rMol) else ""
                                    } catch (e: Exception) {
                                        outputRow[colName] = ""
                                    }
                                }
                            } finally {
                                keyVect.delete()
                            }
                            successIndex++
                        } else {
                            // Molecule did not match core — empty R-group columns
                            outputRow[coreColumnName] = ""
                        }

                        writer.write(i.toLong(), outputRow)

                        if (allRows.isNotEmpty()) {
                            nodeProgressCallback.report((60 + (i + 1) * 40 / allRows.size).toInt())
                        }
                    }
                }
            } finally {
                rgd.delete()
            }

            // Cleanup molecules
            for (mol in allMols) {
                mol?.delete()
            }
        } finally {
            coreMol.delete()
        }

        nodeProgressCallback.report(100)
        LOGGER.info("R-Group Decomposition completed")
    }
}



