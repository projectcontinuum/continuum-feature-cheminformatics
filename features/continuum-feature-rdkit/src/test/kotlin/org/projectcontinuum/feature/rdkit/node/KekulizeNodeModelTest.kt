package org.projectcontinuum.feature.rdkit.node

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.kotlin.*
import org.projectcontinuum.core.commons.exception.NodeRuntimeException
import org.projectcontinuum.core.commons.protocol.progress.NodeProgressCallback
import org.projectcontinuum.core.commons.utils.NodeInputReader
import org.projectcontinuum.core.commons.utils.NodeOutputWriter
import org.projectcontinuum.core.commons.utils.NodeOutputWriter.OutputPortWriter
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@ExtendWith(RDKitTestExtension::class)
class KekulizeNodeModelTest {

    private lateinit var nodeModel: KekulizeNodeModel
    private lateinit var mockInputReader: NodeInputReader
    private lateinit var mockOutputWriter: NodeOutputWriter
    private lateinit var mockPortWriter: NodeOutputWriter.OutputPortWriter
    private lateinit var mockProgressCallback: NodeProgressCallback

    @BeforeEach
    fun setUp() {
        nodeModel = KekulizeNodeModel()
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
        assertEquals("org.projectcontinuum.feature.rdkit.node.KekulizeNodeModel", metadata.id)
        assertEquals("Converts aromatic SMILES to Kekule form using RDKit", metadata.description)
        assertEquals("Kekulize", metadata.title)
        assertEquals("Convert aromatic SMILES to Kekule form via RDKit", metadata.subTitle)
        assertNotNull(metadata.icon)
        assertTrue(metadata.icon.toString().contains("svg"))
    }

    @Test
    fun `test categories are correctly defined`() {
        val categories = nodeModel.categories
        assertEquals(1, categories.size)
        assertEquals("RDKit/Modifiers", categories[0])
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
    fun `test execute with valid SMILES produces Kekule output`() {
        val rows = listOf(
            mapOf("smiles" to "c1ccccc1", "name" to "benzene"),
            mapOf("smiles" to "c1ccncc1", "name" to "pyridine")
        )
        mockSequentialReads(mockInputReader, rows)
        whenever(mockInputReader.getRowCount()).thenReturn(2)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "newColumnName" to "kekule_smiles",
            "removeSourceColumn" to false
        )
        val inputs = mapOf("input" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(2)).write(any(), rowCaptor.capture())
        assertEquals(2, rowCaptor.allValues.size)

        // Check result SMILES values are present
        assertNotNull(rowCaptor.allValues[0]["kekule_smiles"])
        assertNotNull(rowCaptor.allValues[1]["kekule_smiles"])

        // Check original columns are preserved
        assertEquals("c1ccccc1", rowCaptor.allValues[0]["smiles"])
        assertEquals("benzene", rowCaptor.allValues[0]["name"])
        assertEquals("c1ccncc1", rowCaptor.allValues[1]["smiles"])
        assertEquals("pyridine", rowCaptor.allValues[1]["name"])
    }

    @Test
    fun `test execute with invalid SMILES writes empty string and does not crash`() {
        val rows = listOf(
            mapOf("smiles" to "INVALID_SMILES", "name" to "bad"),
            mapOf("smiles" to "c1ccccc1", "name" to "benzene")
        )
        mockSequentialReads(mockInputReader, rows)
        whenever(mockInputReader.getRowCount()).thenReturn(2)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "newColumnName" to "kekule_smiles",
            "removeSourceColumn" to false
        )
        val inputs = mapOf("input" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(2)).write(any(), rowCaptor.capture())

        // Invalid SMILES should produce empty string
        assertEquals("", rowCaptor.allValues[0]["kekule_smiles"])
        // Original columns still preserved
        assertEquals("INVALID_SMILES", rowCaptor.allValues[0]["smiles"])
        assertEquals("bad", rowCaptor.allValues[0]["name"])

        // Valid SMILES should produce non-empty result
        assertNotNull(rowCaptor.allValues[1]["kekule_smiles"])
        assertTrue((rowCaptor.allValues[1]["kekule_smiles"] as String).isNotEmpty())
    }

    @Test
    fun `test execute uses default newColumnName and removeSourceColumn`() {
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
        // Default column name is "kekule_smiles"
        assertTrue(rowCaptor.allValues[0].containsKey("kekule_smiles"))
        // Source column should NOT be removed by default
        assertTrue(rowCaptor.allValues[0].containsKey("smiles"))
    }

    @Test
    fun `test execute with removeSourceColumn true removes the source column`() {
        val rows = listOf(
            mapOf("smiles" to "c1ccccc1", "name" to "benzene")
        )
        mockSequentialReads(mockInputReader, rows)
        whenever(mockInputReader.getRowCount()).thenReturn(1)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "newColumnName" to "kekule_smiles",
            "removeSourceColumn" to true
        )
        val inputs = mapOf("input" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        val outputRow = rowCaptor.allValues[0]

        // Source column should be removed
        assertTrue(!outputRow.containsKey("smiles"))
        // Other columns should be preserved
        assertEquals("benzene", outputRow["name"])
        // Result column should be present
        assertTrue(outputRow.containsKey("kekule_smiles"))
    }

    // ===== Error Tests =====

    @Test
    fun `test execute throws exception when smilesColumn is missing`() {
        val properties = mapOf(
            "newColumnName" to "kekule_smiles"
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
            "smilesColumn" to "smiles"
        )
        val inputs = emptyMap<String, NodeInputReader>()

        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)
        }
        assertEquals("Input port 'input' is not connected", exception.message)
    }

    // ===== Edge Cases =====

    @Test
    fun `test execute with empty input`() {
        mockSequentialReads(mockInputReader, emptyList())
        whenever(mockInputReader.getRowCount()).thenReturn(0)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "newColumnName" to "kekule_smiles",
            "removeSourceColumn" to false
        )
        val inputs = mapOf("input" to mockInputReader)

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, never()).write(any(), any())
    }

    @Test
    fun `test execute with empty SMILES string writes empty result`() {
        val rows = listOf(
            mapOf("smiles" to "", "name" to "empty")
        )
        mockSequentialReads(mockInputReader, rows)
        whenever(mockInputReader.getRowCount()).thenReturn(1)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "newColumnName" to "kekule_smiles",
            "removeSourceColumn" to false
        )
        val inputs = mapOf("input" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        assertEquals("", rowCaptor.allValues[0]["kekule_smiles"])
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
