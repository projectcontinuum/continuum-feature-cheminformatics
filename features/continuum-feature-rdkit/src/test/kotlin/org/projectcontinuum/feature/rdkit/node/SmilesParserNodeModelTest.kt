package org.projectcontinuum.feature.rdkit.node

import org.projectcontinuum.core.commons.exception.NodeRuntimeException
import org.projectcontinuum.core.commons.protocol.progress.NodeProgressCallback
import org.projectcontinuum.core.commons.utils.NodeInputReader
import org.projectcontinuum.core.commons.utils.NodeOutputWriter
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@ExtendWith(RDKitTestExtension::class)
class SmilesParserNodeModelTest {

    private lateinit var nodeModel: SmilesParserNodeModel
    private lateinit var mockInputReader: NodeInputReader
    private lateinit var mockOutputWriter: NodeOutputWriter
    private lateinit var mockOutputPortWriter: NodeOutputWriter.OutputPortWriter
    private lateinit var mockErrorPortWriter: NodeOutputWriter.OutputPortWriter
    private lateinit var mockProgressCallback: NodeProgressCallback

    @BeforeEach
    fun setUp() {
        nodeModel = SmilesParserNodeModel()
        mockInputReader = mock()
        mockOutputWriter = mock()
        mockOutputPortWriter = mock()
        mockErrorPortWriter = mock()
        mockProgressCallback = mock()
        whenever(mockOutputWriter.createOutputPortWriter("output")).thenReturn(mockOutputPortWriter)
        whenever(mockOutputWriter.createOutputPortWriter("errors")).thenReturn(mockErrorPortWriter)
    }

    // ===== Configuration Tests =====

    @Test
    fun `test node metadata is properly configured`() {
        val metadata = nodeModel.metadata
        assertEquals("org.projectcontinuum.feature.rdkit.node.SmilesParserNodeModel", metadata.id)
        assertEquals("Parses molecule strings into canonical SMILES with error routing", metadata.description)
        assertEquals("SMILES Parser", metadata.title)
        assertEquals("Parse and validate molecule strings", metadata.subTitle)
        assertNotNull(metadata.icon)
        assertTrue(metadata.icon.toString().contains("svg"))
    }

    @Test
    fun `test two output ports are defined`() {
        val outputPorts = nodeModel.outputPorts
        assertEquals(2, outputPorts.size)
        assertNotNull(outputPorts["output"])
        assertNotNull(outputPorts["errors"])
        assertEquals("parsed molecules", outputPorts["output"]!!.name)
        assertEquals("failed molecules", outputPorts["errors"]!!.name)
    }

    @Test
    fun `test input ports are correctly defined`() {
        val inputPorts = nodeModel.inputPorts
        assertEquals(1, inputPorts.size)
        assertNotNull(inputPorts["input"])
        assertEquals("input table", inputPorts["input"]!!.name)
    }

    @Test
    fun `test categories are correctly defined`() {
        val categories = nodeModel.categories
        assertEquals(2, categories.size)
        assertEquals("RDKit", categories[0])
        assertEquals("Converters", categories[1])
    }

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
    fun `test execute with valid SMILES goes to output port`() {
        val rows = listOf(
            mapOf("smiles" to "CCO", "id" to 1),
            mapOf("smiles" to "c1ccccc1", "id" to 2)
        )
        mockSequentialReads(mockInputReader, rows)
        whenever(mockInputReader.getRowCount()).thenReturn(2)

        val properties = mapOf<String, Any>(
            "smilesColumn" to "smiles",
            "inputFormat" to "SMILES",
            "newColumnName" to "canonical_smiles"
        )
        val inputs = mapOf("input" to mockInputReader)
        val outputCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockOutputPortWriter, times(2)).write(any(), outputCaptor.capture())
        verify(mockErrorPortWriter, never()).write(any(), any())

        // Verify canonical SMILES column was added
        assertTrue(outputCaptor.allValues[0].containsKey("canonical_smiles"))
        assertTrue(outputCaptor.allValues[1].containsKey("canonical_smiles"))

        // Verify original columns preserved
        assertEquals(1, outputCaptor.allValues[0]["id"])
        assertEquals(2, outputCaptor.allValues[1]["id"])
    }

    @Test
    fun `test execute with invalid SMILES goes to errors port`() {
        val rows = listOf(
            mapOf("smiles" to "INVALID_MOLECULE", "id" to 1)
        )
        mockSequentialReads(mockInputReader, rows)
        whenever(mockInputReader.getRowCount()).thenReturn(1)

        val properties = mapOf<String, Any>(
            "smilesColumn" to "smiles",
            "inputFormat" to "SMILES",
            "newColumnName" to "canonical_smiles"
        )
        val inputs = mapOf("input" to mockInputReader)
        val errorCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockOutputPortWriter, never()).write(any(), any())
        verify(mockErrorPortWriter, times(1)).write(any(), errorCaptor.capture())

        // Verify error column was added
        assertTrue(errorCaptor.firstValue.containsKey("parse_error"))
        assertEquals(1, errorCaptor.firstValue["id"])
    }

    @Test
    fun `test missing smilesColumn throws NodeRuntimeException`() {
        val properties = mapOf<String, Any>(
            "inputFormat" to "SMILES"
        )
        val inputs = mapOf("input" to mockInputReader)

        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)
        }
        assertEquals("smilesColumn is not provided", exception.message)
    }

    @Test
    fun `test mixed input routes valid to output and invalid to errors`() {
        val rows = listOf(
            mapOf("smiles" to "CCO", "id" to 1),
            mapOf("smiles" to "INVALID", "id" to 2),
            mapOf("smiles" to "c1ccccc1", "id" to 3),
            mapOf("smiles" to "", "id" to 4),
            mapOf("smiles" to "CC(=O)O", "id" to 5)
        )
        mockSequentialReads(mockInputReader, rows)
        whenever(mockInputReader.getRowCount()).thenReturn(5)

        val properties = mapOf<String, Any>(
            "smilesColumn" to "smiles",
            "inputFormat" to "SMILES",
            "newColumnName" to "canonical_smiles"
        )
        val inputs = mapOf("input" to mockInputReader)
        val outputCaptor = argumentCaptor<Map<String, Any>>()
        val errorCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockOutputPortWriter, times(3)).write(any(), outputCaptor.capture())
        verify(mockErrorPortWriter, times(2)).write(any(), errorCaptor.capture())

        // Verify valid rows went to output
        assertEquals(1, outputCaptor.allValues[0]["id"])
        assertEquals(3, outputCaptor.allValues[1]["id"])
        assertEquals(5, outputCaptor.allValues[2]["id"])

        // Verify invalid rows went to errors
        assertEquals(2, errorCaptor.allValues[0]["id"])
        assertEquals(4, errorCaptor.allValues[1]["id"])
    }

    @Test
    fun `test execute with row indices are sequential per port`() {
        val rows = listOf(
            mapOf("smiles" to "CCO", "id" to 1),
            mapOf("smiles" to "INVALID", "id" to 2),
            mapOf("smiles" to "c1ccccc1", "id" to 3)
        )
        mockSequentialReads(mockInputReader, rows)
        whenever(mockInputReader.getRowCount()).thenReturn(3)

        val properties = mapOf<String, Any>(
            "smilesColumn" to "smiles",
            "inputFormat" to "SMILES",
            "newColumnName" to "canonical_smiles"
        )
        val inputs = mapOf("input" to mockInputReader)
        val outputIndexCaptor = argumentCaptor<Long>()
        val errorIndexCaptor = argumentCaptor<Long>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockOutputPortWriter, times(2)).write(outputIndexCaptor.capture(), any())
        verify(mockErrorPortWriter, times(1)).write(errorIndexCaptor.capture(), any())

        // Output port row indices: 0, 1
        assertEquals(0L, outputIndexCaptor.allValues[0])
        assertEquals(1L, outputIndexCaptor.allValues[1])

        // Error port row indices: 0
        assertEquals(0L, errorIndexCaptor.allValues[0])
    }

    @Test
    fun `test execute throws exception when input port is missing`() {
        val properties = mapOf<String, Any>(
            "smilesColumn" to "smiles"
        )
        val inputs = emptyMap<String, NodeInputReader>()

        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)
        }
        assertEquals("Input port 'input' is not connected", exception.message)
    }

    @Test
    fun `test execute with empty input`() {
        mockSequentialReads(mockInputReader, emptyList())
        whenever(mockInputReader.getRowCount()).thenReturn(0)

        val properties = mapOf<String, Any>(
            "smilesColumn" to "smiles",
            "inputFormat" to "SMILES",
            "newColumnName" to "canonical_smiles"
        )
        val inputs = mapOf("input" to mockInputReader)

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockOutputPortWriter, never()).write(any(), any())
        verify(mockErrorPortWriter, never()).write(any(), any())
    }

    @Test
    fun `test execute properly closes both writers`() {
        val rows = listOf(mapOf("smiles" to "CCO", "id" to 1))
        mockSequentialReads(mockInputReader, rows)
        whenever(mockInputReader.getRowCount()).thenReturn(1)

        val properties = mapOf<String, Any>(
            "smilesColumn" to "smiles",
            "inputFormat" to "SMILES"
        )
        val inputs = mapOf("input" to mockInputReader)

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockOutputPortWriter).close()
        verify(mockErrorPortWriter).close()
    }

    @Test
    fun `test execute properly closes input reader`() {
        val rows = listOf(mapOf("smiles" to "CCO", "id" to 1))
        mockSequentialReads(mockInputReader, rows)
        whenever(mockInputReader.getRowCount()).thenReturn(1)

        val properties = mapOf<String, Any>(
            "smilesColumn" to "smiles",
            "inputFormat" to "SMILES"
        )
        val inputs = mapOf("input" to mockInputReader)

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockInputReader).close()
    }

    @Test
    fun `test execute reports progress`() {
        val rows = listOf(
            mapOf("smiles" to "CCO"),
            mapOf("smiles" to "c1ccccc1"),
            mapOf("smiles" to "CC"),
            mapOf("smiles" to "CCCC")
        )
        mockSequentialReads(mockInputReader, rows)
        whenever(mockInputReader.getRowCount()).thenReturn(4)

        val properties = mapOf<String, Any>(
            "smilesColumn" to "smiles",
            "inputFormat" to "SMILES"
        )
        val inputs = mapOf("input" to mockInputReader)
        val progressCaptor = argumentCaptor<Int>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockProgressCallback, times(5)).report(progressCaptor.capture())
        // 25%, 50%, 75%, 100% from loop + final 100%
        assertEquals(25, progressCaptor.allValues[0])
        assertEquals(50, progressCaptor.allValues[1])
        assertEquals(75, progressCaptor.allValues[2])
        assertEquals(100, progressCaptor.allValues[3])
        assertEquals(100, progressCaptor.allValues[4])
    }

    @Test
    fun `test execute with removeSourceColumn true`() {
        val rows = listOf(
            mapOf("smiles" to "CCO", "id" to 1)
        )
        mockSequentialReads(mockInputReader, rows)
        whenever(mockInputReader.getRowCount()).thenReturn(1)

        val properties = mapOf<String, Any>(
            "smilesColumn" to "smiles",
            "inputFormat" to "SMILES",
            "newColumnName" to "canonical_smiles",
            "removeSourceColumn" to true
        )
        val inputs = mapOf("input" to mockInputReader)
        val outputCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockOutputPortWriter, times(1)).write(any(), outputCaptor.capture())

        // Verify source column removed
        assertTrue(!outputCaptor.firstValue.containsKey("smiles"))
        // Verify other columns preserved
        assertEquals(1, outputCaptor.firstValue["id"])
        // Verify canonical SMILES added
        assertTrue(outputCaptor.firstValue.containsKey("canonical_smiles"))
    }

    @Test
    fun `test execute with addErrorColumn false`() {
        val rows = listOf(
            mapOf("smiles" to "INVALID", "id" to 1)
        )
        mockSequentialReads(mockInputReader, rows)
        whenever(mockInputReader.getRowCount()).thenReturn(1)

        val properties = mapOf<String, Any>(
            "smilesColumn" to "smiles",
            "inputFormat" to "SMILES",
            "addErrorColumn" to false
        )
        val inputs = mapOf("input" to mockInputReader)
        val errorCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockErrorPortWriter, times(1)).write(any(), errorCaptor.capture())

        // Verify error column NOT added
        assertTrue(!errorCaptor.firstValue.containsKey("parse_error"))
        // Verify original columns preserved
        assertEquals(1, errorCaptor.firstValue["id"])
    }

    @Test
    fun `test execute with blank smiles goes to errors`() {
        val rows = listOf(
            mapOf("smiles" to "   ", "id" to 1)
        )
        mockSequentialReads(mockInputReader, rows)
        whenever(mockInputReader.getRowCount()).thenReturn(1)

        val properties = mapOf<String, Any>(
            "smilesColumn" to "smiles",
            "inputFormat" to "SMILES"
        )
        val inputs = mapOf("input" to mockInputReader)
        val errorCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockOutputPortWriter, never()).write(any(), any())
        verify(mockErrorPortWriter, times(1)).write(any(), errorCaptor.capture())

        assertTrue(errorCaptor.firstValue.containsKey("parse_error"))
        assertTrue((errorCaptor.firstValue["parse_error"] as String).contains("empty or missing"))
    }

    @Test
    fun `test execute with missing smiles column in row goes to errors`() {
        val rows = listOf(
            mapOf("other_column" to "value", "id" to 1)
        )
        mockSequentialReads(mockInputReader, rows)
        whenever(mockInputReader.getRowCount()).thenReturn(1)

        val properties = mapOf<String, Any>(
            "smilesColumn" to "smiles",
            "inputFormat" to "SMILES"
        )
        val inputs = mapOf("input" to mockInputReader)
        val errorCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockOutputPortWriter, never()).write(any(), any())
        verify(mockErrorPortWriter, times(1)).write(any(), errorCaptor.capture())

        assertTrue(errorCaptor.firstValue.containsKey("parse_error"))
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
