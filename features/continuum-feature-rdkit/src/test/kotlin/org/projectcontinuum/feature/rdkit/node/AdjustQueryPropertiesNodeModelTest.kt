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
class AdjustQueryPropertiesNodeModelTest {

    private lateinit var nodeModel: AdjustQueryPropertiesNodeModel
    private lateinit var mockInputReader: NodeInputReader
    private lateinit var mockOutputWriter: NodeOutputWriter
    private lateinit var mockPortWriter: NodeOutputWriter.OutputPortWriter
    private lateinit var mockProgressCallback: NodeProgressCallback

    @BeforeEach
    fun setUp() {
        nodeModel = AdjustQueryPropertiesNodeModel()
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
        assertEquals("org.projectcontinuum.feature.rdkit.node.AdjustQueryPropertiesNodeModel", metadata.id)
        assertEquals("Converts molecules to flexible substructure queries by adjusting atom and bond query properties", metadata.description)
        assertEquals("Adjust Query Properties", metadata.title)
        assertEquals("Convert molecules to flexible SMARTS queries", metadata.subTitle)
        assertNotNull(metadata.icon)
        assertTrue(metadata.icon.toString().contains("svg"))
    }

    @Test
    fun `test categories are correctly defined`() {
        val categories = nodeModel.categories
        assertEquals(1, categories.size)
        assertEquals("RDKit/Experimental", categories[0])
    }

    // ===== Port Tests =====

    @Test
    fun `test input ports are correctly defined`() {
        val inputPorts = nodeModel.inputPorts
        assertEquals(1, inputPorts.size)
        assertNotNull(inputPorts["input"])
    }

    @Test
    fun `test output ports are correctly defined`() {
        val outputPorts = nodeModel.outputPorts
        assertEquals(1, outputPorts.size)
        assertNotNull(outputPorts["output"])
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
    fun `test execute converts benzene to SMARTS query`() {
        val rows = listOf(
            mapOf("smiles" to "c1ccccc1", "name" to "benzene")
        )
        mockSequentialReads(mockInputReader, rows)
        whenever(mockInputReader.getRowCount()).thenReturn(1)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "newColumnName" to "query_smarts",
            "removeSourceColumn" to false,
            "adjustDegree" to true,
            "adjustRingCount" to true,
            "aromatize" to true
        )
        val inputs = mapOf("input" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        val outputRow = rowCaptor.allValues[0]

        assertTrue(outputRow.containsKey("query_smarts"))
        val smarts = outputRow["query_smarts"] as String
        assertTrue(smarts.isNotEmpty())
        // SMARTS should contain atom-level queries
        assertTrue(smarts.contains("[") || smarts.contains(":"))

        // Original columns preserved
        assertEquals("c1ccccc1", outputRow["smiles"])
        assertEquals("benzene", outputRow["name"])
    }

    @Test
    fun `test execute with multiple molecules`() {
        val rows = listOf(
            mapOf("smiles" to "c1ccccc1", "name" to "benzene"),
            mapOf("smiles" to "CC(=O)O", "name" to "acetic_acid")
        )
        mockSequentialReads(mockInputReader, rows)
        whenever(mockInputReader.getRowCount()).thenReturn(2)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "newColumnName" to "adjusted_query_smarts",
            "removeSourceColumn" to false
        )
        val inputs = mapOf("input" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(2)).write(any(), rowCaptor.capture())
        for (outputRow in rowCaptor.allValues) {
            assertTrue(outputRow.containsKey("adjusted_query_smarts"))
            val smarts = outputRow["adjusted_query_smarts"] as String
            assertTrue(smarts.isNotEmpty())
        }
    }

    @Test
    fun `test execute with invalid SMILES writes empty SMARTS`() {
        val rows = listOf(
            mapOf("smiles" to "INVALID", "name" to "bad")
        )
        mockSequentialReads(mockInputReader, rows)
        whenever(mockInputReader.getRowCount()).thenReturn(1)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "newColumnName" to "query_smarts",
            "removeSourceColumn" to false
        )
        val inputs = mapOf("input" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        assertEquals("", rowCaptor.allValues[0]["query_smarts"])
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
            "newColumnName" to "query_smarts",
            "removeSourceColumn" to true
        )
        val inputs = mapOf("input" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        assertTrue(!rowCaptor.allValues[0].containsKey("smiles"))
        assertTrue(rowCaptor.allValues[0].containsKey("query_smarts"))
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
        val properties = mapOf("smilesColumn" to "smiles")
        val inputs = mapOf<String, NodeInputReader>()

        assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)
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
            "newColumnName" to "query_smarts"
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
            "newColumnName" to "query_smarts"
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
}

