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
 * Optimize Geometry Node Model
 *
 * Optimizes the 3D geometry of molecules from SMILES strings using a force field (MMFF94 or UFF).
 * For each row in the input table, the node reads a SMILES string, parses it into an RDKit
 * molecule, adds explicit hydrogens, embeds 3D coordinates using the ETKDGv3 algorithm, and then
 * minimizes the energy using the selected force field. The output includes the optimized MolBlock,
 * the final energy, and whether the minimization converged. Invalid SMILES produce empty strings
 * for all output columns rather than an error.
 *
 * **Input Ports:**
 * - `input`: Input table containing a column with SMILES strings
 *
 * **Output Ports:**
 * - `output`: Table with all original columns plus optimized MolBlock, energy, and convergence columns
 *
 * **Configuration Properties:**
 * - `smilesColumn` (required): Name of the column containing SMILES strings
 * - `forceField` (optional, default "MMFF94"): Force field to use (MMFF94 or UFF)
 * - `iterations` (optional, default 200): Maximum number of minimization iterations
 * - `newMoleculeColumnName` (optional, default "optimized_mol_block"): Name for the optimized MolBlock column
 * - `newEnergyColumnName` (optional, default "energy"): Name for the energy column
 * - `newConvergedColumnName` (optional, default "converged"): Name for the convergence column
 * - `removeSourceColumn` (optional, default false): Whether to remove the original SMILES column
 *
 * @since 1.0
 * @see ProcessNodeModel
 */
@ContinuumNode
class OptimizeGeometryNodeModel : ProcessNodeModel() {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(OptimizeGeometryNodeModel::class.java)
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
            "forceField": {
              "type": "string",
              "title": "Force Field",
              "description": "Force field to use for geometry optimization",
              "enum": ["MMFF94", "UFF"],
              "default": "MMFF94"
            },
            "iterations": {
              "type": "integer",
              "title": "Iterations",
              "description": "Maximum number of minimization iterations",
              "default": 200,
              "minimum": 1
            },
            "newMoleculeColumnName": {
              "type": "string",
              "title": "Optimized MolBlock Column",
              "description": "Name for the column containing the optimized MolBlock",
              "default": "optimized_mol_block"
            },
            "newEnergyColumnName": {
              "type": "string",
              "title": "Energy Column",
              "description": "Name for the column containing the final energy",
              "default": "energy"
            },
            "newConvergedColumnName": {
              "type": "string",
              "title": "Converged Column",
              "description": "Name for the column indicating convergence",
              "default": "converged"
            },
            "removeSourceColumn": {
              "type": "boolean",
              "title": "Remove Source Column",
              "description": "Whether to remove the original SMILES column from the output",
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
              "scope": "#/properties/forceField"
            },
            {
              "type": "Control",
              "scope": "#/properties/iterations"
            },
            {
              "type": "Control",
              "scope": "#/properties/newMoleculeColumnName"
            },
            {
              "type": "Control",
              "scope": "#/properties/newEnergyColumnName"
            },
            {
              "type": "Control",
              "scope": "#/properties/newConvergedColumnName"
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
        description = "Optimizes 3D molecular geometry using MMFF94 or UFF force fields via RDKit",
        title = "Optimize Geometry",
        subTitle = "Force field geometry optimization for molecules",
        nodeModel = this.javaClass.name,
        icon = """
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" d="M10.5 6h9.75M10.5 6a1.5 1.5 0 1 1-3 0m3 0a1.5 1.5 0 1 0-3 0M3.75 6H7.5m3 12h9.75m-9.75 0a1.5 1.5 0 0 1-3 0m3 0a1.5 1.5 0 0 0-3 0m-3.75 0H7.5m9-6h3.75m-3.75 0a1.5 1.5 0 0 1-3 0m3 0a1.5 1.5 0 0 0-3 0m-9.75 0h9.75"/>
            </svg>
        """.trimIndent(),
        inputs = inputPorts,
        outputs = outputPorts,
        properties = mapOf(
            "smilesColumn" to "smiles",
            "forceField" to "MMFF94",
            "iterations" to 200,
            "newMoleculeColumnName" to "optimized_mol_block",
            "newEnergyColumnName" to "energy",
            "newConvergedColumnName" to "converged",
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
        val forceField = properties["forceField"]?.toString() ?: "MMFF94"
        val iterations = (properties["iterations"] as? Number)?.toInt() ?: 200
        val newMoleculeColumnName = properties["newMoleculeColumnName"]?.toString() ?: "optimized_mol_block"
        val newEnergyColumnName = properties["newEnergyColumnName"]?.toString() ?: "energy"
        val newConvergedColumnName = properties["newConvergedColumnName"]?.toString() ?: "converged"
        val removeSourceColumn = properties["removeSourceColumn"] as? Boolean ?: false

        LOGGER.info("Optimizing geometry from column '$smilesColumn' using $forceField force field (max iterations=$iterations)")

        val inputReader = inputs["input"] ?: throw NodeRuntimeException(
            workflowId = "",
            nodeId = "",
            message = "Input port 'input' is not connected"
        )

        val totalRows = inputReader.getRowCount()

        // === Create output writer and process rows ===
        nodeOutputWriter.createOutputPortWriter("output").use { writer ->
            inputReader.use { reader ->
                var row = reader.read()
                var rowNumber = 0L

                while (row != null) {
                    val smilesValue = row[smilesColumn]?.toString() ?: ""

                    val outputRow = row.toMutableMap<String, Any>()
                    if (removeSourceColumn) {
                        outputRow.remove(smilesColumn)
                    }

                    var molBlockResult: Any = ""
                    var energyResult: Any = ""
                    var convergedResult: Any = ""

                    if (smilesValue.isNotEmpty()) {
                        RDKitNodeHelper.withMolecule(smilesValue) { mol ->
                            RDKFuncs.addHs(mol)
                            val params = RDKFuncs.getETKDGv3()
                            val embedResult = DistanceGeom.EmbedMolecule(mol, params)

                            if (embedResult >= 0) {
                                val ff = when (forceField) {
                                    "UFF" -> ForceField.UFFGetMoleculeForceField(mol)
                                    else -> ForceField.MMFFGetMoleculeForceField(mol)
                                }
                                if (ff != null) {
                                    try {
                                        ff.initialize()
                                        val minimizeResult = ff.minimize(iterations.toLong())
                                        convergedResult = minimizeResult == 0
                                        energyResult = ff.calcEnergy()
                                        molBlockResult = RDKFuncs.MolToMolBlock(mol)
                                    } finally {
                                        ff.delete()
                                    }
                                }
                            }
                        }
                    }

                    outputRow[newMoleculeColumnName] = molBlockResult
                    outputRow[newEnergyColumnName] = energyResult
                    outputRow[newConvergedColumnName] = convergedResult

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
        LOGGER.info("Geometry optimization completed: processed rows")
    }
}
