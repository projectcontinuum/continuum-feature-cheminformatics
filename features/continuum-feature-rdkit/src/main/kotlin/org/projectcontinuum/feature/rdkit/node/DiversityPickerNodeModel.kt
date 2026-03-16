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
 * Diversity Picker Node Model
 *
 * Selects a diverse subset of molecules from an input table using the MaxMin algorithm with
 * RDKit fingerprints. This is a cross-row operation: all rows are read into memory, fingerprints
 * are generated for each molecule, and the MaxMin diversity picking algorithm selects the most
 * structurally diverse subset. The node has two output ports: "picked" contains the selected
 * diverse molecules, and "unpicked" contains the remaining molecules.
 *
 * **Input Ports:**
 * - `input`: Input table containing a column with SMILES strings
 *
 * **Output Ports:**
 * - `picked`: Table containing the selected diverse molecules
 * - `unpicked`: Table containing the remaining molecules
 *
 * **Configuration Properties:**
 * - `smilesColumn` (required): Name of the column containing SMILES strings
 * - `numberToPick` (required): Number of diverse molecules to select
 * - `randomSeed` (optional, default 42): Seed for reproducible selection of the starting molecule
 * - `fingerprintType` (optional, default "Morgan"): Type of fingerprint (Morgan, RDKit)
 * - `numBits` (optional, default 2048): Number of bits for the fingerprint
 * - `radius` (optional, default 2): Radius for Morgan fingerprints
 *
 * @since 1.0
 * @see ProcessNodeModel
 */
@ContinuumNode
class DiversityPickerNodeModel : ProcessNodeModel() {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(DiversityPickerNodeModel::class.java)
        private val objectMapper = ObjectMapper()
    }

    final override val inputPorts = mapOf(
        "input" to ContinuumWorkflowModel.NodePort(
            name = "input table",
            contentType = APPLICATION_OCTET_STREAM_VALUE
        )
    )

    final override val outputPorts = mapOf(
        "picked" to ContinuumWorkflowModel.NodePort(
            name = "picked table",
            contentType = APPLICATION_OCTET_STREAM_VALUE
        ),
        "unpicked" to ContinuumWorkflowModel.NodePort(
            name = "unpicked table",
            contentType = APPLICATION_OCTET_STREAM_VALUE
        )
    )

    override val categories = listOf(
        "RDKit",
        "Fingerprints"
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
            "numberToPick": {
              "type": "integer",
              "title": "Number to Pick",
              "description": "Number of diverse molecules to select",
              "minimum": 1
            },
            "randomSeed": {
              "type": "integer",
              "title": "Random Seed",
              "description": "Seed for reproducible selection of the starting molecule",
              "default": 42
            },
            "fingerprintType": {
              "type": "string",
              "title": "Fingerprint Type",
              "description": "Type of fingerprint to use for diversity calculation",
              "enum": ["Morgan", "RDKit"],
              "default": "Morgan"
            },
            "numBits": {
              "type": "integer",
              "title": "Number of Bits",
              "description": "Number of bits for the fingerprint",
              "default": 2048
            },
            "radius": {
              "type": "integer",
              "title": "Radius",
              "description": "Radius for Morgan fingerprints (ignored for RDKit type)",
              "default": 2
            }
          },
          "required": ["smilesColumn", "numberToPick"]
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
              "scope": "#/properties/numberToPick"
            },
            {
              "type": "Control",
              "scope": "#/properties/randomSeed"
            },
            {
              "type": "Control",
              "scope": "#/properties/fingerprintType"
            },
            {
              "type": "Control",
              "scope": "#/properties/numBits"
            },
            {
              "type": "Control",
              "scope": "#/properties/radius"
            }
          ]
        }
        """.trimIndent(),
        object : TypeReference<Map<String, Any>>() {}
    )

    override val metadata = ContinuumWorkflowModel.NodeData(
        id = this.javaClass.name,
        description = "Selects a diverse subset of molecules using MaxMin diversity picking with RDKit fingerprints",
        title = "Diversity Picker",
        subTitle = "MaxMin diversity picking for molecular subsets",
        nodeModel = this.javaClass.name,
        icon = """
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" d="M3.75 6A2.25 2.25 0 0 1 6 3.75h2.25A2.25 2.25 0 0 1 10.5 6v2.25a2.25 2.25 0 0 1-2.25 2.25H6a2.25 2.25 0 0 1-2.25-2.25V6zM3.75 15.75A2.25 2.25 0 0 1 6 13.5h2.25a2.25 2.25 0 0 1 2.25 2.25V18a2.25 2.25 0 0 1-2.25 2.25H6A2.25 2.25 0 0 1 3.75 18v-2.25zM13.5 6a2.25 2.25 0 0 1 2.25-2.25H18A2.25 2.25 0 0 1 20.25 6v2.25A2.25 2.25 0 0 1 18 10.5h-2.25a2.25 2.25 0 0 1-2.25-2.25V6zM13.5 15.75a2.25 2.25 0 0 1 2.25-2.25H18a2.25 2.25 0 0 1 2.25 2.25V18A2.25 2.25 0 0 1 18 20.25h-2.25A2.25 2.25 0 0 1 13.5 18v-2.25z"/>
            </svg>
        """.trimIndent(),
        inputs = inputPorts,
        outputs = outputPorts,
        properties = mapOf(
            "smilesColumn" to "smiles",
            "numberToPick" to 3,
            "randomSeed" to 42,
            "fingerprintType" to "Morgan",
            "numBits" to 2048,
            "radius" to 2
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
        val numberToPick = (properties["numberToPick"] as? Number)?.toInt() ?: throw NodeRuntimeException(
            workflowId = "",
            nodeId = "",
            message = "numberToPick is not provided"
        )
        if (numberToPick < 1) {
            throw NodeRuntimeException(
                workflowId = "",
                nodeId = "",
                message = "numberToPick must be at least 1"
            )
        }
        val randomSeed = (properties["randomSeed"] as? Number)?.toInt() ?: 42
        val fingerprintType = properties["fingerprintType"]?.toString() ?: "Morgan"
        val numBits = (properties["numBits"] as? Number)?.toInt() ?: 2048
        val radius = (properties["radius"] as? Number)?.toInt() ?: 2

        LOGGER.info("Diversity picking $numberToPick molecules from column '$smilesColumn' using $fingerprintType fingerprints (seed=$randomSeed)")

        val inputReader = inputs["input"] ?: throw NodeRuntimeException(
            workflowId = "",
            nodeId = "",
            message = "Input port 'input' is not connected"
        )

        // === Read all rows into memory ===
        val allRows = mutableListOf<Map<String, Any>>()
        val fingerprints = mutableListOf<ExplicitBitVect?>()

        inputReader.use { reader ->
            var row = reader.read()
            while (row != null) {
                allRows.add(row)
                val smilesValue = row[smilesColumn]?.toString() ?: ""
                if (smilesValue.isNotEmpty()) {
                    val fp = RDKitNodeHelper.withMolecule(smilesValue) { mol ->
                        when (fingerprintType) {
                            "RDKit" -> RDKFuncs.RDKFingerprintMol(mol)
                            else -> RDKFuncs.getMorganFingerprintAsBitVect(mol, radius.toLong(), numBits.toLong())
                        }
                    }
                    fingerprints.add(fp)
                } else {
                    fingerprints.add(null)
                }
                row = reader.read()
            }
        }

        nodeProgressCallback.report(30)

        // === MaxMin diversity picking ===
        val pickedIndices = maxMinPick(fingerprints, numberToPick, randomSeed)

        nodeProgressCallback.report(70)

        // === Clean up fingerprints ===
        for (fp in fingerprints) {
            fp?.delete()
        }

        // === Write results to picked and unpicked ports ===
        val pickedSet = pickedIndices.toSet()
        val pickedWriter = nodeOutputWriter.createOutputPortWriter("picked")
        val unpickedWriter = nodeOutputWriter.createOutputPortWriter("unpicked")
        try {
            var pickedRowNumber = 0L
            var unpickedRowNumber = 0L

            for (i in allRows.indices) {
                if (pickedSet.contains(i)) {
                    pickedWriter.write(pickedRowNumber, allRows[i])
                    pickedRowNumber++
                } else {
                    unpickedWriter.write(unpickedRowNumber, allRows[i])
                    unpickedRowNumber++
                }
            }
        } finally {
            pickedWriter.close()
            unpickedWriter.close()
        }

        nodeProgressCallback.report(100)
        LOGGER.info("Diversity picking completed: picked ${pickedIndices.size} from ${allRows.size} molecules")
    }

    /**
     * MaxMin diversity picking algorithm.
     *
     * Selects [numberToPick] diverse molecules from the list of fingerprints. Starts with
     * a seed molecule (based on randomSeed), then iteratively picks the molecule with the
     * maximum minimum distance to all already-picked molecules.
     *
     * @param fingerprints list of fingerprints (null entries are skipped)
     * @param numberToPick number of molecules to pick
     * @param randomSeed seed for selecting the starting molecule
     * @return list of indices of picked molecules
     */
    private fun maxMinPick(
        fingerprints: List<ExplicitBitVect?>,
        numberToPick: Int,
        randomSeed: Int
    ): List<Int> {
        // Find indices of valid (non-null) fingerprints
        val validIndices = fingerprints.indices.filter { fingerprints[it] != null }

        if (validIndices.isEmpty()) {
            return emptyList()
        }

        val actualPick = minOf(numberToPick, validIndices.size)
        val picked = mutableListOf<Int>()

        // Start with a seed-based molecule
        val startIdx = validIndices[randomSeed.mod(validIndices.size)]
        picked.add(startIdx)

        // Track the minimum distance from each candidate to the already-picked set
        val minDistances = DoubleArray(fingerprints.size) { Double.MAX_VALUE }

        while (picked.size < actualPick) {
            val lastPicked = picked.last()
            val lastFp = fingerprints[lastPicked]!!

            var bestCandidate = -1
            var bestMinDist = -1.0

            for (idx in validIndices) {
                if (idx in picked) continue

                // Update min distance for this candidate considering the newly picked molecule
                val dist = 1.0 - RDKFuncs.TanimotoSimilarity(fingerprints[idx]!!, lastFp)
                if (dist < minDistances[idx]) {
                    minDistances[idx] = dist
                }

                // MaxMin: pick the candidate with the largest minimum distance
                if (minDistances[idx] > bestMinDist) {
                    bestMinDist = minDistances[idx]
                    bestCandidate = idx
                }
            }

            if (bestCandidate >= 0) {
                picked.add(bestCandidate)
            } else {
                break
            }
        }

        return picked
    }
}
