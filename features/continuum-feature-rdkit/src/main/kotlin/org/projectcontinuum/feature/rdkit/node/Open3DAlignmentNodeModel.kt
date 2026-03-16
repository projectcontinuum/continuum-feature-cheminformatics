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
 * Open 3D Alignment Node Model
 *
 * Aligns query molecules against reference molecules in 3D using RDKit. This node has two input
 * ports: one for query molecules and one for reference molecules. All reference molecules are read
 * first, then for each query molecule, the node generates 3D coordinates, aligns it against each
 * reference, and keeps the alignment with the best (lowest) RMSD. The output includes the aligned
 * MolBlock, RMSD, and alignment score.
 *
 * **Input Ports:**
 * - `query`: Input table containing query molecules as SMILES strings
 * - `reference`: Input table containing reference molecules as SMILES strings
 *
 * **Output Ports:**
 * - `output`: Table with original query columns plus aligned MolBlock, RMSD, and score columns
 *
 * **Configuration Properties:**
 * - `querySmilesColumn` (required): Name of the SMILES column in the query table
 * - `referenceSmilesColumn` (required): Name of the SMILES column in the reference table
 * - `newAlignedColumnName` (optional, default "aligned_mol_block"): Name for the aligned MolBlock column
 * - `newRmsdColumnName` (optional, default "alignment_rmsd"): Name for the RMSD column
 * - `newScoreColumnName` (optional, default "alignment_score"): Name for the alignment score column
 * - `removeSourceColumn` (optional, default false): Whether to remove the original SMILES column
 *
 * @since 1.0
 * @see ProcessNodeModel
 */
@ContinuumNode
class Open3DAlignmentNodeModel : ProcessNodeModel() {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(Open3DAlignmentNodeModel::class.java)
        private val objectMapper = ObjectMapper()
    }

    final override val inputPorts = mapOf(
        "query" to ContinuumWorkflowModel.NodePort(
            name = "query table",
            contentType = APPLICATION_OCTET_STREAM_VALUE
        ),
        "reference" to ContinuumWorkflowModel.NodePort(
            name = "reference table",
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
        "Geometry"
    )

    val propertiesSchema: Map<String, Any> = objectMapper.readValue(
        """
        {
          "type": "object",
          "properties": {
            "querySmilesColumn": {
              "type": "string",
              "title": "Query SMILES Column",
              "description": "Name of the column containing SMILES strings in the query table"
            },
            "referenceSmilesColumn": {
              "type": "string",
              "title": "Reference SMILES Column",
              "description": "Name of the column containing SMILES strings in the reference table"
            },
            "newAlignedColumnName": {
              "type": "string",
              "title": "Aligned MolBlock Column",
              "description": "Name for the column containing the aligned MolBlock",
              "default": "aligned_mol_block"
            },
            "newRmsdColumnName": {
              "type": "string",
              "title": "RMSD Column",
              "description": "Name for the column containing the alignment RMSD",
              "default": "alignment_rmsd"
            },
            "newScoreColumnName": {
              "type": "string",
              "title": "Score Column",
              "description": "Name for the column containing the alignment score",
              "default": "alignment_score"
            },
            "removeSourceColumn": {
              "type": "boolean",
              "title": "Remove Source Column",
              "description": "Whether to remove the original SMILES column from the output",
              "default": false
            }
          },
          "required": ["querySmilesColumn", "referenceSmilesColumn"]
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
              "scope": "#/properties/querySmilesColumn"
            },
            {
              "type": "Control",
              "scope": "#/properties/referenceSmilesColumn"
            },
            {
              "type": "Control",
              "scope": "#/properties/newAlignedColumnName"
            },
            {
              "type": "Control",
              "scope": "#/properties/newRmsdColumnName"
            },
            {
              "type": "Control",
              "scope": "#/properties/newScoreColumnName"
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
        description = "Aligns query molecules against reference molecules in 3D using RDKit",
        title = "Open 3D Alignment",
        subTitle = "3D molecular alignment against reference structures",
        nodeModel = this.javaClass.name,
        icon = """
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" d="M7.5 21L3 16.5m0 0L7.5 12M3 16.5h13.5m0-13.5L21 7.5m0 0L16.5 12M21 7.5H7.5"/>
            </svg>
        """.trimIndent(),
        inputs = inputPorts,
        outputs = outputPorts,
        properties = mapOf(
            "querySmilesColumn" to "smiles",
            "referenceSmilesColumn" to "ref_smiles",
            "newAlignedColumnName" to "aligned_mol_block",
            "newRmsdColumnName" to "alignment_rmsd",
            "newScoreColumnName" to "alignment_score",
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
        val querySmilesColumn = properties?.get("querySmilesColumn") as String? ?: throw NodeRuntimeException(
            workflowId = "",
            nodeId = "",
            message = "querySmilesColumn is not provided"
        )
        val referenceSmilesColumn = properties["referenceSmilesColumn"] as String? ?: throw NodeRuntimeException(
            workflowId = "",
            nodeId = "",
            message = "referenceSmilesColumn is not provided"
        )
        val newAlignedColumnName = properties["newAlignedColumnName"]?.toString() ?: "aligned_mol_block"
        val newRmsdColumnName = properties["newRmsdColumnName"]?.toString() ?: "alignment_rmsd"
        val newScoreColumnName = properties["newScoreColumnName"]?.toString() ?: "alignment_score"
        val removeSourceColumn = properties["removeSourceColumn"] as? Boolean ?: false

        LOGGER.info("3D alignment: query column '$querySmilesColumn', reference column '$referenceSmilesColumn'")

        val queryReader = inputs["query"] ?: throw NodeRuntimeException(
            workflowId = "",
            nodeId = "",
            message = "Input port 'query' is not connected"
        )
        val referenceReader = inputs["reference"] ?: throw NodeRuntimeException(
            workflowId = "",
            nodeId = "",
            message = "Input port 'reference' is not connected"
        )

        // === Read ALL reference molecules first using memory-safe helper ===
        RDKitNodeHelper.withMoleculeList { refMols ->
            referenceReader.use { reader ->
                var row = reader.read()
                while (row != null) {
                    val smilesValue = row[referenceSmilesColumn]?.toString() ?: ""
                    if (smilesValue.isNotEmpty()) {
                        val mol = RDKitNodeHelper.parseMoleculeOrNull(smilesValue)
                        if (mol != null) {
                            RDKFuncs.addHs(mol)
                            val params = RDKFuncs.getETKDGv3()
                            val embedResult = DistanceGeom.EmbedMolecule(mol, params)
                            if (embedResult >= 0) {
                                refMols.add(mol)
                            } else {
                                mol.delete()
                            }
                        }
                    }
                    row = reader.read()
                }
            }

            nodeProgressCallback.report(30)

            // === Process each query molecule ===
            nodeOutputWriter.createOutputPortWriter("output").use { writer ->
                queryReader.use { reader ->
                    var row = reader.read()
                    var rowNumber = 0L

                    while (row != null) {
                        val smilesValue = row[querySmilesColumn]?.toString() ?: ""

                        val outputRow = row.toMutableMap<String, Any>()
                        if (removeSourceColumn) {
                            outputRow.remove(querySmilesColumn)
                        }

                        var alignedMolBlock: Any = ""
                        var bestRmsd: Any = ""
                        var bestScore: Any = ""

                        if (smilesValue.isNotEmpty() && refMols.isNotEmpty()) {
                            RDKitNodeHelper.withMolecule(smilesValue) { queryMol ->
                                RDKFuncs.addHs(queryMol)
                                val params = RDKFuncs.getETKDGv3()
                                val embedResult = DistanceGeom.EmbedMolecule(queryMol, params)

                                if (embedResult >= 0) {
                                    var minRmsd = Double.MAX_VALUE

                                    for (refMol in refMols) {
                                        // Create a copy of the query mol for alignment
                                        val queryMolCopy = ROMol(queryMol)
                                        try {
                                            val rmsd = queryMolCopy.alignMol(refMol)
                                            if (rmsd < minRmsd) {
                                                minRmsd = rmsd
                                                alignedMolBlock = RDKFuncs.MolToMolBlock(queryMolCopy)
                                                bestRmsd = rmsd
                                                bestScore = rmsd
                                            }
                                        } finally {
                                            queryMolCopy.delete()
                                        }
                                    }
                                }
                            }
                        }

                        outputRow[newAlignedColumnName] = alignedMolBlock
                        outputRow[newRmsdColumnName] = bestRmsd
                        outputRow[newScoreColumnName] = bestScore

                        writer.write(rowNumber, outputRow)
                        rowNumber++

                        row = reader.read()
                    }
                }
            }
        }

        nodeProgressCallback.report(100)
        LOGGER.info("3D alignment completed: processed rows")
    }
}
