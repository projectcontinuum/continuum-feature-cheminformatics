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
class SaltStripperNodeModelTest {

    private lateinit var nodeModel: SaltStripperNodeModel
    private lateinit var mockInputReader: NodeInputReader
    private lateinit var mockOutputWriter: NodeOutputWriter
    private lateinit var mockPortWriter: NodeOutputWriter.OutputPortWriter
    private lateinit var mockProgressCallback: NodeProgressCallback

    @BeforeEach
    fun setUp() {
        nodeModel = SaltStripperNodeModel()
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
        assertEquals("org.projectcontinuum.feature.rdkit.node.SaltStripperNodeModel", metadata.id)
        assertEquals("Removes salt/counterion fragments from SMILES strings using RDKit", metadata.description)
        assertEquals("Salt Stripper", metadata.title)
        assertEquals("Remove salts and keep largest fragment via RDKit", metadata.subTitle)
        assertNotNull(metadata.icon)
        assertTrue(metadata.icon.toString().contains("svg"))
    }

    @Test
    fun `test categories are correctly defined`() {
        val categories = nodeModel.categories
        assertEquals(2, categories.size)
        assertEquals("RDKit", categories[0])
        assertEquals("Modifiers", categories[1])
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
    fun `test execute with multi-component SMILES strips salts`() {
        val rows = listOf(
            mapOf("smiles" to "CC(=O)[O-].[Na+]", "name" to "sodium acetate"),
            mapOf("smiles" to "c1ccccc1", "name" to "benzene")
        )
        mockSequentialReads(mockInputReader, rows)
        whenever(mockInputReader.getRowCount()).thenReturn(2)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "newColumnName" to "stripped_smiles",
            "removeSourceColumn" to false,
            "keepLargestFragmentOnly" to true
        )
        val inputs = mapOf("input" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(2)).write(any(), rowCaptor.capture())
        assertEquals(2, rowCaptor.allValues.size)

        // Multi-component should keep largest fragment (acetate, not sodium)
        assertEquals("CC(=O)[O-]", rowCaptor.allValues[0]["stripped_smiles"])

        // Single-component should just canonicalize
        assertNotNull(rowCaptor.allValues[1]["stripped_smiles"])
        assertTrue((rowCaptor.allValues[1]["stripped_smiles"] as String).isNotEmpty())

        // Check original columns are preserved
        assertEquals("CC(=O)[O-].[Na+]", rowCaptor.allValues[0]["smiles"])
        assertEquals("sodium acetate", rowCaptor.allValues[0]["name"])
        assertEquals("c1ccccc1", rowCaptor.allValues[1]["smiles"])
        assertEquals("benzene", rowCaptor.allValues[1]["name"])
    }

    @Test
    fun `test execute with single component SMILES canonicalizes`() {
        val rows = listOf(
            mapOf("smiles" to "c1ccccc1", "name" to "benzene")
        )
        mockSequentialReads(mockInputReader, rows)
        whenever(mockInputReader.getRowCount()).thenReturn(1)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "newColumnName" to "stripped_smiles",
            "removeSourceColumn" to false,
            "keepLargestFragmentOnly" to true
        )
        val inputs = mapOf("input" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        assertNotNull(rowCaptor.allValues[0]["stripped_smiles"])
        assertTrue((rowCaptor.allValues[0]["stripped_smiles"] as String).isNotEmpty())
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
            "newColumnName" to "stripped_smiles",
            "removeSourceColumn" to false,
            "keepLargestFragmentOnly" to true
        )
        val inputs = mapOf("input" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(2)).write(any(), rowCaptor.capture())

        // Invalid SMILES should produce empty string
        assertEquals("", rowCaptor.allValues[0]["stripped_smiles"])
        // Original columns still preserved
        assertEquals("INVALID_SMILES", rowCaptor.allValues[0]["smiles"])
        assertEquals("bad", rowCaptor.allValues[0]["name"])

        // Valid SMILES should produce non-empty result
        assertNotNull(rowCaptor.allValues[1]["stripped_smiles"])
        assertTrue((rowCaptor.allValues[1]["stripped_smiles"] as String).isNotEmpty())
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
        // Default column name is "stripped_smiles"
        assertTrue(rowCaptor.allValues[0].containsKey("stripped_smiles"))
        // Source column should NOT be removed by default
        assertTrue(rowCaptor.allValues[0].containsKey("smiles"))
    }

    @Test
    fun `test execute with removeSourceColumn true removes the source column`() {
        val rows = listOf(
            mapOf("smiles" to "CC(=O)[O-].[Na+]", "name" to "sodium acetate")
        )
        mockSequentialReads(mockInputReader, rows)
        whenever(mockInputReader.getRowCount()).thenReturn(1)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "newColumnName" to "stripped_smiles",
            "removeSourceColumn" to true,
            "keepLargestFragmentOnly" to true
        )
        val inputs = mapOf("input" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        val outputRow = rowCaptor.allValues[0]

        // Source column should be removed
        assertTrue(!outputRow.containsKey("smiles"))
        // Other columns should be preserved
        assertEquals("sodium acetate", outputRow["name"])
        // Result column should be present
        assertTrue(outputRow.containsKey("stripped_smiles"))
    }

    @Test
    fun `test execute with keepLargestFragmentOnly false just canonicalizes`() {
        val rows = listOf(
            mapOf("smiles" to "CC(=O)[O-].[Na+]", "name" to "sodium acetate")
        )
        mockSequentialReads(mockInputReader, rows)
        whenever(mockInputReader.getRowCount()).thenReturn(1)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "newColumnName" to "stripped_smiles",
            "removeSourceColumn" to false,
            "keepLargestFragmentOnly" to false
        )
        val inputs = mapOf("input" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        val result = rowCaptor.allValues[0]["stripped_smiles"] as String

        // When keepLargestFragmentOnly is false, the full SMILES should be canonicalized (both fragments present)
        assertNotNull(result)
        assertTrue(result.isNotEmpty())
        // The result should still contain both fragments
        assertTrue(result.contains("."))
    }

    // ===== Error Tests =====

    @Test
    fun `test execute throws exception when smilesColumn is missing`() {
        val properties = mapOf(
            "newColumnName" to "stripped_smiles"
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
            "newColumnName" to "stripped_smiles",
            "removeSourceColumn" to false,
            "keepLargestFragmentOnly" to true
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
            "newColumnName" to "stripped_smiles",
            "removeSourceColumn" to false,
            "keepLargestFragmentOnly" to true
        )
        val inputs = mapOf("input" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        assertEquals("", rowCaptor.allValues[0]["stripped_smiles"])
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
