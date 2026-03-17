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
class AddConformersNodeModelTest {

    private lateinit var nodeModel: AddConformersNodeModel
    private lateinit var mockInputReader: NodeInputReader
    private lateinit var mockOutputWriter: NodeOutputWriter
    private lateinit var mockPortWriter: NodeOutputWriter.OutputPortWriter
    private lateinit var mockProgressCallback: NodeProgressCallback

    @BeforeEach
    fun setUp() {
        nodeModel = AddConformersNodeModel()
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
        assertEquals("org.projectcontinuum.feature.rdkit.node.AddConformersNodeModel", metadata.id)
        assertEquals("Generates multiple 3D conformers for molecules from SMILES strings using RDKit distance geometry", metadata.description)
        assertEquals("Add Conformers", metadata.title)
        assertEquals("Generate multiple 3D conformers per molecule", metadata.subTitle)
        assertNotNull(metadata.icon)
        assertTrue(metadata.icon.toString().contains("svg"))
    }

    @Test
    fun `test categories are correctly defined`() {
        val categories = nodeModel.categories
        assertEquals(2, categories.size)
        assertEquals("RDKit", categories[0])
        assertEquals("Geometry", categories[1])
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
    fun `test execute hexane with 3 conformers produces 3 output rows`() {
        val rows = listOf(
            mapOf("smiles" to "CCCCCC", "name" to "hexane")
        )
        mockSequentialReads(mockInputReader, rows)
        whenever(mockInputReader.getRowCount()).thenReturn(1)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "numberOfConformers" to 3,
            "randomSeed" to 42,
            "newColumnName" to "conformer_mol_block",
            "conformerIdColumnName" to "conformer_id",
            "removeSourceColumn" to false
        )
        val inputs = mapOf("input" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(3)).write(any(), rowCaptor.capture())
        assertEquals(3, rowCaptor.allValues.size)

        // Each output row should have a conformer MolBlock and conformer ID
        for (outputRow in rowCaptor.allValues) {
            val molBlock = outputRow["conformer_mol_block"]
            assertTrue(molBlock is String && molBlock.isNotEmpty(), "Conformer MolBlock should not be empty")
            assertNotNull(outputRow["conformer_id"], "Conformer ID should not be null")
            // Original columns should be preserved
            assertEquals("CCCCCC", outputRow["smiles"])
            assertEquals("hexane", outputRow["name"])
        }

        // Conformer IDs should be distinct
        val confIds = rowCaptor.allValues.map { it["conformer_id"] }.toSet()
        assertEquals(3, confIds.size, "All conformer IDs should be distinct")
    }

    @Test
    fun `test execute with invalid SMILES produces single row with empty values`() {
        val rows = listOf(
            mapOf("smiles" to "INVALID_SMILES", "name" to "bad")
        )
        mockSequentialReads(mockInputReader, rows)
        whenever(mockInputReader.getRowCount()).thenReturn(1)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "numberOfConformers" to 3,
            "randomSeed" to 42,
            "newColumnName" to "conformer_mol_block",
            "conformerIdColumnName" to "conformer_id"
        )
        val inputs = mapOf("input" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        val outputRow = rowCaptor.allValues[0]

        assertEquals("", outputRow["conformer_mol_block"])
        assertEquals("", outputRow["conformer_id"])
        assertEquals("INVALID_SMILES", outputRow["smiles"])
    }

    @Test
    fun `test execute with multiple input rows expands all`() {
        val rows = listOf(
            mapOf("smiles" to "CCCCCC", "name" to "hexane"),
            mapOf("smiles" to "CCCCC", "name" to "pentane")
        )
        mockSequentialReads(mockInputReader, rows)
        whenever(mockInputReader.getRowCount()).thenReturn(2)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "numberOfConformers" to 2,
            "randomSeed" to 42,
            "newColumnName" to "conformer_mol_block",
            "conformerIdColumnName" to "conformer_id"
        )
        val inputs = mapOf("input" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        // 2 molecules x 2 conformers each = 4 output rows
        verify(mockPortWriter, times(4)).write(any(), rowCaptor.capture())
        assertEquals(4, rowCaptor.allValues.size)

        // First 2 rows should be hexane, next 2 should be pentane
        assertEquals("hexane", rowCaptor.allValues[0]["name"])
        assertEquals("hexane", rowCaptor.allValues[1]["name"])
        assertEquals("pentane", rowCaptor.allValues[2]["name"])
        assertEquals("pentane", rowCaptor.allValues[3]["name"])
    }

    // ===== Error Tests =====

    @Test
    fun `test execute throws exception when smilesColumn is missing`() {
        val properties = mapOf(
            "numberOfConformers" to 3,
            "randomSeed" to 42
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
            "numberOfConformers" to 3
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
            "numberOfConformers" to 3,
            "randomSeed" to 42,
            "newColumnName" to "conformer_mol_block",
            "conformerIdColumnName" to "conformer_id"
        )
        val inputs = mapOf("input" to mockInputReader)

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(0)).write(any(), any())
    }

    @Test
    fun `test execute with empty SMILES produces single row with empty values`() {
        val rows = listOf(
            mapOf("smiles" to "", "name" to "empty")
        )
        mockSequentialReads(mockInputReader, rows)
        whenever(mockInputReader.getRowCount()).thenReturn(1)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "numberOfConformers" to 3,
            "randomSeed" to 42,
            "newColumnName" to "conformer_mol_block",
            "conformerIdColumnName" to "conformer_id"
        )
        val inputs = mapOf("input" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        assertEquals("", rowCaptor.allValues[0]["conformer_mol_block"])
        assertEquals("", rowCaptor.allValues[0]["conformer_id"])
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
