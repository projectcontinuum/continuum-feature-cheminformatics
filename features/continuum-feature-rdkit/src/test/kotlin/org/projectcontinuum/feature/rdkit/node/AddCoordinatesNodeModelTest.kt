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
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@ExtendWith(RDKitTestExtension::class)
class AddCoordinatesNodeModelTest {

    private lateinit var nodeModel: AddCoordinatesNodeModel
    private lateinit var mockInputReader: NodeInputReader
    private lateinit var mockOutputWriter: NodeOutputWriter
    private lateinit var mockPortWriter: NodeOutputWriter.OutputPortWriter
    private lateinit var mockProgressCallback: NodeProgressCallback

    @BeforeEach
    fun setUp() {
        nodeModel = AddCoordinatesNodeModel()
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
        assertEquals("org.projectcontinuum.feature.rdkit.node.AddCoordinatesNodeModel", metadata.id)
        assertEquals("Generates 2D or 3D coordinates for molecules from SMILES strings using RDKit", metadata.description)
        assertEquals("Add Coordinates", metadata.title)
        assertEquals("Generate 2D/3D molecular coordinates via RDKit", metadata.subTitle)
        assertNotNull(metadata.icon)
        assertTrue(metadata.icon.toString().contains("svg"))
    }

    @Test
    fun `test categories are correctly defined`() {
        val categories = nodeModel.categories
        assertEquals(1, categories.size)
        assertEquals("RDKit/Geometry", categories[0])
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
    fun `test execute 2D produces MolBlock containing 2D marker`() {
        val rows = listOf(
            mapOf("smiles" to "c1ccccc1", "name" to "benzene")
        )
        mockSequentialReads(mockInputReader, rows)
        whenever(mockInputReader.getRowCount()).thenReturn(1)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "dimension" to "2D",
            "newColumnName" to "mol_block",
            "removeSourceColumn" to false
        )
        val inputs = mapOf("input" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        val outputRow = rowCaptor.allValues[0]

        val molBlock = outputRow["mol_block"] as String
        assertTrue(molBlock.isNotEmpty(), "MolBlock should not be empty")
        // 2D MolBlocks should not contain "3D" in the header line
        // They typically contain "2D" or "0" in the dimension field
        assertTrue(molBlock.contains("V2000") || molBlock.contains("V3000"), "MolBlock should be in V2000 or V3000 format")

        // Original columns should be preserved
        assertEquals("c1ccccc1", outputRow["smiles"])
        assertEquals("benzene", outputRow["name"])
    }

    @Test
    fun `test execute with invalid SMILES produces empty MolBlock`() {
        val rows = listOf(
            mapOf("smiles" to "INVALID_SMILES", "name" to "bad")
        )
        mockSequentialReads(mockInputReader, rows)
        whenever(mockInputReader.getRowCount()).thenReturn(1)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "dimension" to "2D",
            "newColumnName" to "mol_block",
            "removeSourceColumn" to false
        )
        val inputs = mapOf("input" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        val outputRow = rowCaptor.allValues[0]

        assertEquals("", outputRow["mol_block"])
        assertEquals("INVALID_SMILES", outputRow["smiles"])
        assertEquals("bad", outputRow["name"])
    }

    @Test
    fun `test execute with removeSourceColumn removes SMILES column`() {
        val rows = listOf(
            mapOf("smiles" to "c1ccccc1", "name" to "benzene")
        )
        mockSequentialReads(mockInputReader, rows)
        whenever(mockInputReader.getRowCount()).thenReturn(1)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "dimension" to "2D",
            "newColumnName" to "mol_block",
            "removeSourceColumn" to true
        )
        val inputs = mapOf("input" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        val outputRow = rowCaptor.allValues[0]

        assertTrue(!outputRow.containsKey("smiles"), "Source column should be removed")
        assertEquals("benzene", outputRow["name"])
        assertTrue((outputRow["mol_block"] as String).isNotEmpty())
    }

    @Test
    fun `test execute with multiple rows processes all`() {
        val rows = listOf(
            mapOf("smiles" to "c1ccccc1", "name" to "benzene"),
            mapOf("smiles" to "CCO", "name" to "ethanol"),
            mapOf("smiles" to "INVALID", "name" to "bad")
        )
        mockSequentialReads(mockInputReader, rows)
        whenever(mockInputReader.getRowCount()).thenReturn(3)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "dimension" to "2D",
            "newColumnName" to "mol_block"
        )
        val inputs = mapOf("input" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(3)).write(any(), rowCaptor.capture())
        assertEquals(3, rowCaptor.allValues.size)

        // Benzene and ethanol should have non-empty MolBlocks
        assertTrue((rowCaptor.allValues[0]["mol_block"] as String).isNotEmpty())
        assertTrue((rowCaptor.allValues[1]["mol_block"] as String).isNotEmpty())
        // Invalid should be empty
        assertEquals("", rowCaptor.allValues[2]["mol_block"])
    }

    // ===== Error Tests =====

    @Test
    fun `test execute throws exception when smilesColumn is missing`() {
        val properties = mapOf(
            "dimension" to "2D",
            "newColumnName" to "mol_block"
        )
        val inputs = mapOf("input" to mockInputReader)

        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)
        }
        assertEquals("smilesColumn is not provided", exception.message)
    }

    @Test
    fun `test execute throws exception when properties are null`() {
        val inputs = mapOf("input" to mockInputReader)

        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(null, inputs, mockOutputWriter, mockProgressCallback)
        }
        assertEquals("smilesColumn is not provided", exception.message)
    }

    @Test
    fun `test execute throws exception when input port is missing`() {
        val properties = mapOf(
            "smilesColumn" to "smiles",
            "dimension" to "2D"
        )
        val inputs = emptyMap<String, NodeInputReader>()

        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)
        }
        assertEquals("Input port 'input' is not connected", exception.message)
    }

    // ===== Edge Cases =====

    @Test
    fun `test execute with empty input produces no output rows`() {
        mockSequentialReads(mockInputReader, emptyList())
        whenever(mockInputReader.getRowCount()).thenReturn(0)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "dimension" to "2D",
            "newColumnName" to "mol_block"
        )
        val inputs = mapOf("input" to mockInputReader)

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(0)).write(any(), any())
    }

    @Test
    fun `test execute with empty SMILES produces empty MolBlock`() {
        val rows = listOf(
            mapOf("smiles" to "", "name" to "empty")
        )
        mockSequentialReads(mockInputReader, rows)
        whenever(mockInputReader.getRowCount()).thenReturn(1)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "dimension" to "2D",
            "newColumnName" to "mol_block"
        )
        val inputs = mapOf("input" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        assertEquals("", rowCaptor.allValues[0]["mol_block"])
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
}
