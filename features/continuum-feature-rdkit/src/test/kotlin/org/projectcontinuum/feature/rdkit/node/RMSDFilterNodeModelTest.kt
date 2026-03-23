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
class RMSDFilterNodeModelTest {

    private lateinit var nodeModel: RMSDFilterNodeModel
    private lateinit var mockInputReader: NodeInputReader
    private lateinit var mockOutputWriter: NodeOutputWriter
    private lateinit var mockAboveWriter: NodeOutputWriter.OutputPortWriter
    private lateinit var mockBelowWriter: NodeOutputWriter.OutputPortWriter
    private lateinit var mockProgressCallback: NodeProgressCallback

    @BeforeEach
    fun setUp() {
        nodeModel = RMSDFilterNodeModel()
        mockInputReader = mock()
        mockOutputWriter = mock()
        mockAboveWriter = mock()
        mockBelowWriter = mock()
        mockProgressCallback = mock()
        whenever(mockOutputWriter.createOutputPortWriter("above")).thenReturn(mockAboveWriter)
        whenever(mockOutputWriter.createOutputPortWriter("below")).thenReturn(mockBelowWriter)
    }

    // ===== Metadata Tests =====

    @Test
    fun `test node metadata is properly configured`() {
        val metadata = nodeModel.metadata
        assertEquals("org.projectcontinuum.feature.rdkit.node.RMSDFilterNodeModel", metadata.id)
        assertEquals("Filters molecules using a greedy RMSD-based diversity selection, splitting diverse and redundant molecules", metadata.description)
        assertEquals("RMSD Filter", metadata.title)
        assertEquals("Greedy RMSD-based diversity filtering for molecules", metadata.subTitle)
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
    fun `test output ports have two ports above and below`() {
        val outputPorts = nodeModel.outputPorts
        assertEquals(2, outputPorts.size)
        assertNotNull(outputPorts["above"])
        assertEquals("above table", outputPorts["above"]!!.name)
        assertNotNull(outputPorts["below"])
        assertEquals("below table", outputPorts["below"]!!.name)
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

    // ===== Error Tests =====

    @Test
    fun `test execute throws exception when smilesColumn is missing`() {
        val properties = mapOf(
            "rmsdThreshold" to 0.5
        )
        val inputs = mapOf("input" to mockInputReader)

        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)
        }
        assertEquals("smilesColumn is not provided", exception.message)
    }

    @Test
    fun `test execute throws exception when rmsdThreshold is missing`() {
        val properties = mapOf(
            "smilesColumn" to "smiles"
        )
        val inputs = mapOf("input" to mockInputReader)

        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)
        }
        assertEquals("rmsdThreshold is not provided", exception.message)
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
            "rmsdThreshold" to 0.5
        )
        val inputs = emptyMap<String, NodeInputReader>()

        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)
        }
        assertEquals("Input port 'input' is not connected", exception.message)
    }

    // ===== Edge Cases =====

    @Test
    fun `test execute with empty input produces empty outputs`() {
        mockSequentialReads(mockInputReader, emptyList())

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "rmsdThreshold" to 0.5,
            "ignoreHydrogens" to true
        )
        val inputs = mapOf("input" to mockInputReader)

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockAboveWriter, times(0)).write(any(), any())
        verify(mockBelowWriter, times(0)).write(any(), any())
        verify(mockAboveWriter).close()
        verify(mockBelowWriter).close()
    }

    @Test
    fun `test execute with invalid SMILES sends to below`() {
        val rows = listOf(
            mapOf("smiles" to "INVALID", "name" to "bad")
        )
        mockSequentialReads(mockInputReader, rows)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "rmsdThreshold" to 0.5,
            "ignoreHydrogens" to true
        )
        val inputs = mapOf("input" to mockInputReader)
        val belowCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockAboveWriter, times(0)).write(any(), any())
        verify(mockBelowWriter, times(1)).write(any(), belowCaptor.capture())

        assertEquals("INVALID", belowCaptor.allValues[0]["smiles"])
        assertEquals("bad", belowCaptor.allValues[0]["name"])

        verify(mockAboveWriter).close()
        verify(mockBelowWriter).close()
    }

    @Test
    fun `test execute with single valid molecule sends it to above`() {
        val rows = listOf(
            mapOf("smiles" to "c1ccccc1", "name" to "benzene")
        )
        mockSequentialReads(mockInputReader, rows)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "rmsdThreshold" to 0.5,
            "ignoreHydrogens" to true
        )
        val inputs = mapOf("input" to mockInputReader)
        val aboveCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockAboveWriter, times(1)).write(any(), aboveCaptor.capture())
        verify(mockBelowWriter, times(0)).write(any(), any())

        assertEquals("c1ccccc1", aboveCaptor.allValues[0]["smiles"])
        assertEquals("benzene", aboveCaptor.allValues[0]["name"])

        verify(mockAboveWriter).close()
        verify(mockBelowWriter).close()
    }

    @Test
    fun `test execute writers are closed in finally block`() {
        mockSequentialReads(mockInputReader, emptyList())

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "rmsdThreshold" to 0.5,
            "ignoreHydrogens" to true
        )
        val inputs = mapOf("input" to mockInputReader)

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockAboveWriter).close()
        verify(mockBelowWriter).close()
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
