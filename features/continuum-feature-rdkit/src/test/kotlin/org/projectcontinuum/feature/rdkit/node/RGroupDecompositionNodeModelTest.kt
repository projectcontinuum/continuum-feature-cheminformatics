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
class RGroupDecompositionNodeModelTest {

    private lateinit var nodeModel: RGroupDecompositionNodeModel
    private lateinit var mockInputReader: NodeInputReader
    private lateinit var mockOutputWriter: NodeOutputWriter
    private lateinit var mockPortWriter: NodeOutputWriter.OutputPortWriter
    private lateinit var mockProgressCallback: NodeProgressCallback

    @BeforeEach
    fun setUp() {
        nodeModel = RGroupDecompositionNodeModel()
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
        assertEquals("org.projectcontinuum.feature.rdkit.node.RGroupDecompositionNodeModel", metadata.id)
        assertEquals("Decomposes molecules into a core scaffold and R-groups using RDKit RGroupDecomposition", metadata.description)
        assertEquals("R-Group Decomposition", metadata.title)
        assertEquals("Identify substituents on a core scaffold", metadata.subTitle)
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
    fun `test execute decomposes substituted benzenes`() {
        val rows = listOf(
            mapOf("smiles" to "c1ccc(O)cc1", "name" to "phenol"),
            mapOf("smiles" to "c1ccc(N)cc1", "name" to "aniline")
        )
        mockSequentialReads(mockInputReader, rows)
        whenever(mockInputReader.getRowCount()).thenReturn(2)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "coreSmarts" to "c1ccccc1",
            "coreColumnName" to "core",
            "rGroupPrefix" to "R",
            "matchOnlyAtRGroups" to false,
            "removeSourceColumn" to false
        )
        val inputs = mapOf("input" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(2)).write(any(), rowCaptor.capture())

        // Both molecules should have core column
        for (outputRow in rowCaptor.allValues) {
            assertTrue(outputRow.containsKey("core"))
            assertTrue(outputRow.containsKey("smiles"))
        }
    }

    @Test
    fun `test execute with non-matching molecule writes empty rgroup columns`() {
        val rows = listOf(
            mapOf("smiles" to "c1ccc(O)cc1", "name" to "phenol"),
            mapOf("smiles" to "CCCCCC", "name" to "hexane")
        )
        mockSequentialReads(mockInputReader, rows)
        whenever(mockInputReader.getRowCount()).thenReturn(2)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "coreSmarts" to "c1ccccc1",
            "coreColumnName" to "core",
            "rGroupPrefix" to "R",
            "removeSourceColumn" to false
        )
        val inputs = mapOf("input" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(2)).write(any(), rowCaptor.capture())

        // Hexane should have empty core
        val hexaneRow = rowCaptor.allValues[1]
        assertEquals("", hexaneRow["core"])
    }

    // ===== Error Tests =====

    @Test
    fun `test execute throws when smilesColumn is missing`() {
        val properties = mapOf("coreSmarts" to "c1ccccc1")
        val inputs = mapOf("input" to mockInputReader)

        assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)
        }
    }

    @Test
    fun `test execute throws when coreSmarts is missing`() {
        val properties = mapOf("smilesColumn" to "smiles")
        val inputs = mapOf("input" to mockInputReader)

        assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)
        }
    }

    @Test
    fun `test execute throws when input port is not connected`() {
        val properties = mapOf(
            "smilesColumn" to "smiles",
            "coreSmarts" to "c1ccccc1"
        )
        val inputs = mapOf<String, NodeInputReader>()

        assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)
        }
    }

    @Test
    fun `test execute throws for invalid core SMARTS`() {
        val rows = listOf(mapOf("smiles" to "c1ccccc1"))
        mockSequentialReads(mockInputReader, rows)
        whenever(mockInputReader.getRowCount()).thenReturn(1)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "coreSmarts" to "INVALID_SMARTS[[[[",
            "removeSourceColumn" to false
        )
        val inputs = mapOf("input" to mockInputReader)

        assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)
        }
    }

    // ===== Resource Cleanup Tests =====

    @Test
    fun `test execute properly closes output writer`() {
        val rows = listOf(mapOf("smiles" to "c1ccc(O)cc1"))
        mockSequentialReads(mockInputReader, rows)
        whenever(mockInputReader.getRowCount()).thenReturn(1)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "coreSmarts" to "c1ccccc1",
            "removeSourceColumn" to false
        )
        val inputs = mapOf("input" to mockInputReader)

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter).close()
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

