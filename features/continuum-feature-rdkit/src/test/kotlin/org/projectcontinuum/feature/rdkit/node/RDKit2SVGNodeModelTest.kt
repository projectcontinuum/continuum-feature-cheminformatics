package org.projectcontinuum.feature.rdkit.node

import org.projectcontinuum.core.commons.exception.NodeRuntimeException
import org.projectcontinuum.core.commons.protocol.progress.NodeProgressCallback
import org.projectcontinuum.core.commons.utils.NodeInputReader
import org.projectcontinuum.core.commons.utils.NodeOutputWriter
import org.projectcontinuum.core.commons.utils.NodeOutputWriter.OutputPortWriter
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.w3c.dom.Document
import java.io.StringWriter
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@ExtendWith(RDKitTestExtension::class)
class RDKit2SVGNodeModelTest {

    private lateinit var nodeModel: RDKit2SVGNodeModel
    private lateinit var mockInputReader: NodeInputReader
    private lateinit var mockOutputWriter: NodeOutputWriter
    private lateinit var mockPortWriter: NodeOutputWriter.OutputPortWriter
    private lateinit var mockProgressCallback: NodeProgressCallback

    @BeforeEach
    fun setUp() {
        nodeModel = RDKit2SVGNodeModel()
        mockInputReader = mock()
        mockOutputWriter = mock()
        mockPortWriter = mock()
        mockProgressCallback = mock()
        whenever(mockOutputWriter.createOutputPortWriter("output")).thenReturn(mockPortWriter)
    }

    // ===== Metadata Tests =====

    @Test
    fun `test node metadata is properly configured`() {
        val metadata = nodeModel.metadata
        assertEquals("org.projectcontinuum.feature.rdkit.node.RDKit2SVGNodeModel", metadata.id)
        assertEquals("Renders molecules as SVG images using RDKit MolDraw2DSVG", metadata.description)
        assertEquals("RDKit to SVG", metadata.title)
        assertEquals("Render molecules as SVG depictions", metadata.subTitle)
        assertNotNull(metadata.icon)
        assertTrue(metadata.icon.toString().contains("svg"))
    }

    @Test
    fun `test categories are correctly defined`() {
        val categories = nodeModel.categories
        assertEquals(2, categories.size)
        assertEquals("RDKit", categories[0])
        assertEquals("Rendering", categories[1])
    }

    // ===== Port Tests =====

    @Test
    fun `test input ports are correctly defined`() {
        val inputPorts = nodeModel.inputPorts
        assertEquals(1, inputPorts.size)
        assertNotNull(inputPorts["input"])
        assertEquals("input table", inputPorts["input"]!!.name)
    }

    @Test
    fun `test output ports are correctly defined`() {
        val outputPorts = nodeModel.outputPorts
        assertEquals(1, outputPorts.size)
        assertNotNull(outputPorts["output"])
        assertEquals("output table", outputPorts["output"]!!.name)
    }

    // ===== Schema Tests =====

    @Test
    fun `test properties schema is valid`() {
        val schema = nodeModel.propertiesSchema
        assertNotNull(schema)
        assertEquals("object", schema["type"])
        assertTrue(schema.containsKey("properties"))
        assertTrue(schema.containsKey("required"))
    }

    // ===== Success Tests =====

    @Test
    fun `test execute renders benzene as SVG`() {
        val rows = listOf(
            mapOf("smiles" to "c1ccccc1", "name" to "benzene")
        )
        mockSequentialReads(mockInputReader, rows)
        whenever(mockInputReader.getRowCount()).thenReturn(1)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "svgColumnName" to "svg",
            "width" to 300,
            "height" to 300,
            "highlightAtoms" to "",
            "highlightColor" to "#FF0000",
            "removeSourceColumn" to false
        )
        val inputs = mapOf("input" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        val outputRow = rowCaptor.allValues[0]

        // SVG column should exist and contain a Document
        assertTrue(outputRow.containsKey("svg"))
        assertTrue(outputRow["svg"] is Document, "SVG output should be a DOM Document")
        val svgDoc = outputRow["svg"] as Document
        val svgString = documentToString(svgDoc)
        assertTrue(svgString.contains("<svg"))
        assertTrue(svgString.contains("</svg>"))

        // Original columns preserved
        assertEquals("c1ccccc1", outputRow["smiles"])
        assertEquals("benzene", outputRow["name"])
    }

    @Test
    fun `test execute with multiple molecules renders all as SVG`() {
        val rows = listOf(
            mapOf("smiles" to "c1ccccc1", "name" to "benzene"),
            mapOf("smiles" to "CC(=O)O", "name" to "acetic_acid")
        )
        mockSequentialReads(mockInputReader, rows)
        whenever(mockInputReader.getRowCount()).thenReturn(2)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "svgColumnName" to "molecule_svg",
            "width" to 200,
            "height" to 200,
            "removeSourceColumn" to false
        )
        val inputs = mapOf("input" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(2)).write(any(), rowCaptor.capture())
        for (outputRow in rowCaptor.allValues) {
            assertTrue(outputRow.containsKey("molecule_svg"))
            assertTrue(outputRow["molecule_svg"] is Document, "SVG output should be a DOM Document")
            val svgString = documentToString(outputRow["molecule_svg"] as Document)
            assertTrue(svgString.contains("<svg"))
            assertTrue(svgString.contains("</svg>"))
        }
    }

    @Test
    fun `test execute with invalid SMILES writes empty SVG string`() {
        val rows = listOf(
            mapOf("smiles" to "INVALID", "name" to "bad"),
            mapOf("smiles" to "c1ccccc1", "name" to "benzene")
        )
        mockSequentialReads(mockInputReader, rows)
        whenever(mockInputReader.getRowCount()).thenReturn(2)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "svgColumnName" to "svg",
            "removeSourceColumn" to false
        )
        val inputs = mapOf("input" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(2)).write(any(), rowCaptor.capture())

        // Invalid SMILES produces empty string
        assertEquals("", rowCaptor.allValues[0]["svg"])
        assertEquals("INVALID", rowCaptor.allValues[0]["smiles"])

        // Valid SMILES produces a Document
        assertTrue(rowCaptor.allValues[1]["svg"] is Document, "Valid SMILES should produce a DOM Document")
        val svgString = documentToString(rowCaptor.allValues[1]["svg"] as Document)
        assertTrue(svgString.contains("<svg"))
    }

    @Test
    fun `test execute with empty SMILES writes empty SVG string`() {
        val rows = listOf(
            mapOf("smiles" to "", "name" to "empty")
        )
        mockSequentialReads(mockInputReader, rows)
        whenever(mockInputReader.getRowCount()).thenReturn(1)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "svgColumnName" to "svg",
            "removeSourceColumn" to false
        )
        val inputs = mapOf("input" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        assertEquals("", rowCaptor.allValues[0]["svg"])
    }

    @Test
    fun `test execute with removeSourceColumn true removes source`() {
        val rows = listOf(
            mapOf("smiles" to "c1ccccc1", "name" to "benzene")
        )
        mockSequentialReads(mockInputReader, rows)
        whenever(mockInputReader.getRowCount()).thenReturn(1)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "svgColumnName" to "svg",
            "removeSourceColumn" to true
        )
        val inputs = mapOf("input" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        val outputRow = rowCaptor.allValues[0]
        assertTrue(!outputRow.containsKey("smiles"))
        assertEquals("benzene", outputRow["name"])
        assertTrue(outputRow.containsKey("svg"))
    }

    @Test
    fun `test execute with highlight atoms produces SVG`() {
        val rows = listOf(
            mapOf("smiles" to "c1ccccc1", "name" to "benzene")
        )
        mockSequentialReads(mockInputReader, rows)
        whenever(mockInputReader.getRowCount()).thenReturn(1)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "svgColumnName" to "svg",
            "width" to 300,
            "height" to 300,
            "highlightAtoms" to "0,1,2",
            "highlightColor" to "#00FF00",
            "removeSourceColumn" to false
        )
        val inputs = mapOf("input" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        assertTrue(rowCaptor.allValues[0]["svg"] is Document, "Highlighted SVG should be a DOM Document")
        val svgString = documentToString(rowCaptor.allValues[0]["svg"] as Document)
        assertTrue(svgString.contains("<svg"))
        assertTrue(svgString.contains("</svg>"))
    }

    @Test
    fun `test execute uses default properties when not provided`() {
        val rows = listOf(
            mapOf("smiles" to "c1ccccc1")
        )
        mockSequentialReads(mockInputReader, rows)
        whenever(mockInputReader.getRowCount()).thenReturn(1)

        val properties = mapOf(
            "smilesColumn" to "smiles"
        )
        val inputs = mapOf("input" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        // Default column name is "svg"
        assertTrue(rowCaptor.allValues[0].containsKey("svg"))
        // Source column preserved by default
        assertTrue(rowCaptor.allValues[0].containsKey("smiles"))
    }

    // ===== Error Tests =====

    @Test
    fun `test execute throws when smilesColumn is missing`() {
        val properties = mapOf<String, Any>()
        val inputs = mapOf("input" to mockInputReader)

        assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)
        }
    }

    @Test
    fun `test execute throws when input port is not connected`() {
        val properties = mapOf(
            "smilesColumn" to "smiles"
        )
        val inputs = mapOf<String, NodeInputReader>()

        assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)
        }
    }

    @Test
    fun `test execute throws when properties is null`() {
        val inputs = mapOf("input" to mockInputReader)

        assertThrows<NodeRuntimeException> {
            nodeModel.execute(null, inputs, mockOutputWriter, mockProgressCallback)
        }
    }

    // ===== Resource Cleanup Tests =====

    @Test
    fun `test execute properly closes output writer`() {
        val rows = listOf(mapOf("smiles" to "C"))
        mockSequentialReads(mockInputReader, rows)
        whenever(mockInputReader.getRowCount()).thenReturn(1)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "svgColumnName" to "svg",
            "removeSourceColumn" to false
        )
        val inputs = mapOf("input" to mockInputReader)

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter).close()
    }

    @Test
    fun `test execute properly closes input reader`() {
        val rows = listOf(mapOf("smiles" to "C"))
        mockSequentialReads(mockInputReader, rows)
        whenever(mockInputReader.getRowCount()).thenReturn(1)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "svgColumnName" to "svg",
            "removeSourceColumn" to false
        )
        val inputs = mapOf("input" to mockInputReader)

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockInputReader).close()
    }

    // ===== Helper Methods =====

    private fun mockSequentialReads(reader: NodeInputReader, rows: List<Map<String, Any>>) {
        val rowsWithNull = rows + null
        var callCount = 0

        whenever(reader.read()).thenAnswer {
            val result = rowsWithNull[callCount]
            callCount++
            result
        }
    }

    private fun documentToString(doc: Document): String {
        val transformer = TransformerFactory.newInstance().newTransformer()
        val writer = StringWriter()
        transformer.transform(DOMSource(doc), StreamResult(writer))
        return writer.toString()
    }
}

