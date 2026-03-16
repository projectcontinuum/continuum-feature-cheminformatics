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
import org.w3c.dom.Document
import org.xml.sax.InputSource
import org.RDKit.*
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

/**
 * RDKit to SVG Node Model
 *
 * Renders molecules as SVG images using RDKit's MolDraw2DSVG. For each row in the input table,
 * the node reads a SMILES string, generates 2D coordinates if not present, and renders an SVG
 * depiction of the molecule. Optional atom highlighting is supported.
 *
 * **Input Ports:**
 * - `input`: Input table containing a column with SMILES strings
 *
 * **Output Ports:**
 * - `output`: Table with all original columns plus an SVG string column
 *
 * **Configuration Properties:**
 * - `smilesColumn` (required): Name of the column containing SMILES strings
 * - `svgColumnName` (optional, default "svg"): Name for the output SVG column
 * - `width` (optional, default 300): SVG width in pixels
 * - `height` (optional, default 300): SVG height in pixels
 * - `highlightAtoms` (optional, default ""): Comma-separated atom indices to highlight
 * - `highlightColor` (optional, default "#FF0000"): Highlight color as hex string
 * - `removeSourceColumn` (optional, default false): Whether to remove the original SMILES column
 *
 * @since 1.0
 * @see ProcessNodeModel
 */
@ContinuumNode
class RDKit2SVGNodeModel : ProcessNodeModel() {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(RDKit2SVGNodeModel::class.java)
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
        "Rendering"
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
            "svgColumnName": {
              "type": "string",
              "title": "SVG Column Name",
              "description": "Name of the output column for the SVG string",
              "default": "svg"
            },
            "width": {
              "type": "integer",
              "title": "Width",
              "description": "SVG width in pixels",
              "default": 300,
              "minimum": 50,
              "maximum": 2000
            },
            "height": {
              "type": "integer",
              "title": "Height",
              "description": "SVG height in pixels",
              "default": 300,
              "minimum": 50,
              "maximum": 2000
            },
            "highlightAtoms": {
              "type": "string",
              "title": "Highlight Atoms",
              "description": "Optional comma-separated atom indices to highlight",
              "default": ""
            },
            "highlightColor": {
              "type": "string",
              "title": "Highlight Color",
              "description": "Highlight color as hex string (e.g. #FF0000)",
              "default": "#FF0000"
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
              "scope": "#/properties/svgColumnName"
            },
            {
              "type": "Control",
              "scope": "#/properties/width"
            },
            {
              "type": "Control",
              "scope": "#/properties/height"
            },
            {
              "type": "Control",
              "scope": "#/properties/highlightAtoms"
            },
            {
              "type": "Control",
              "scope": "#/properties/highlightColor"
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
        description = "Renders molecules as SVG images using RDKit MolDraw2DSVG",
        title = "RDKit to SVG",
        subTitle = "Render molecules as SVG depictions",
        nodeModel = this.javaClass.name,
        icon = """
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" d="m2.25 15.75 5.159-5.159a2.25 2.25 0 0 1 3.182 0l5.159 5.159m-1.5-1.5 1.409-1.409a2.25 2.25 0 0 1 3.182 0l2.909 2.909M3.75 21h16.5A2.25 2.25 0 0 0 22.5 18.75V5.25A2.25 2.25 0 0 0 20.25 3H3.75A2.25 2.25 0 0 0 1.5 5.25v13.5A2.25 2.25 0 0 0 3.75 21Z"/>
            </svg>
        """.trimIndent(),
        inputs = inputPorts,
        outputs = outputPorts,
        properties = mapOf(
            "smilesColumn" to "smiles",
            "svgColumnName" to "svg",
            "width" to 300,
            "height" to 300,
            "highlightAtoms" to "",
            "highlightColor" to "#FF0000",
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
        val svgColumnName = properties["svgColumnName"]?.toString() ?: "svg"
        val width = (properties["width"] as? Number)?.toInt() ?: 300
        val height = (properties["height"] as? Number)?.toInt() ?: 300
        val highlightAtoms = properties["highlightAtoms"]?.toString() ?: ""
        val highlightColor = properties["highlightColor"]?.toString() ?: "#FF0000"
        val removeSourceColumn = properties["removeSourceColumn"] as? Boolean ?: false

        LOGGER.info("RDKit2SVG: rendering from column '$smilesColumn' into '$svgColumnName' (${width}x${height})")

        val inputReader = inputs["input"] ?: throw NodeRuntimeException(
            workflowId = "",
            nodeId = "",
            message = "Input port 'input' is not connected"
        )

        // Parse highlight atom indices once
        val highlightAtomIndices = if (highlightAtoms.isNotBlank()) {
            highlightAtoms.split(",").mapNotNull { it.trim().toIntOrNull() }
        } else {
            emptyList()
        }

        val totalRows = inputReader.getRowCount()

        // === Create output writer and process rows ===
        nodeOutputWriter.createOutputPortWriter("output").use { writer ->
            inputReader.use { reader ->
                var row = reader.read()
                var rowNumber = 0L

                while (row != null) {
                    val smilesValue = row[smilesColumn]?.toString() ?: ""
                    var svgDocument: Document? = null

                    if (smilesValue.isNotEmpty()) {
                        val mol = RDKFuncs.SmilesToMol(smilesValue)
                        try {
                            if (mol != null) {
                                // Generate 2D coordinates if not present
                                if (mol.getNumConformers() == 0L) {
                                    mol.compute2DCoords()
                                }

                                // Create SVG drawer
                                val drawer = MolDraw2DSVG(width, height)
                                try {
                                    if (highlightAtomIndices.isNotEmpty()) {
                                        val atomVect = Int_Vect()
                                        try {
                                            for (idx in highlightAtomIndices) {
                                                if (idx < mol.getNumAtoms().toInt()) {
                                                    atomVect.add(idx)
                                                }
                                            }
                                            drawer.drawMolecule(mol, "", atomVect)
                                        } finally {
                                            atomVect.delete()
                                        }
                                    } else {
                                        drawer.drawMolecule(mol)
                                    }
                                    drawer.finishDrawing()
                                    var svgText = drawer.getDrawingText()

                                    // Ensure SVG namespace is present
                                    if (!svgText.contains("xmlns=")) {
                                        svgText = svgText.replaceFirst(
                                            "<svg",
                                            "<svg xmlns=\"http://www.w3.org/2000/svg\""
                                        )
                                    }

                                    // Parse SVG string into DOM Document
                                    svgDocument = parseSvgToDocument(svgText)
                                } finally {
                                    drawer.delete()
                                }
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
                    if (svgDocument != null) {
                        outputRow[svgColumnName] = svgDocument
                    } else {
                        outputRow[svgColumnName] = ""
                    }

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
        LOGGER.info("RDKit2SVG rendering completed")
    }

    /**
     * Parses an SVG string into a DOM Document for MIME-typed serialization.
     */
    private fun parseSvgToDocument(svgText: String): Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        val builder = factory.newDocumentBuilder()
        return builder.parse(InputSource(StringReader(svgText)))
    }
}


