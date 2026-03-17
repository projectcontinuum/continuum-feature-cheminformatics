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
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@ExtendWith(RDKitTestExtension::class)
class DescriptorCalculationNodeModelTest {

    private lateinit var nodeModel: DescriptorCalculationNodeModel
    private lateinit var mockInputReader: NodeInputReader
    private lateinit var mockOutputWriter: NodeOutputWriter
    private lateinit var mockPortWriter: NodeOutputWriter.OutputPortWriter
    private lateinit var mockProgressCallback: NodeProgressCallback

    @BeforeEach
    fun setUp() {
        nodeModel = DescriptorCalculationNodeModel()
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
        assertEquals("org.projectcontinuum.feature.rdkit.node.DescriptorCalculationNodeModel", metadata.id)
        assertEquals("Calculates molecular descriptors from SMILES strings using RDKit", metadata.description)
        assertEquals("Descriptor Calculation", metadata.title)
        assertEquals("Compute molecular descriptors via RDKit", metadata.subTitle)
        assertNotNull(metadata.icon)
        assertTrue(metadata.icon.toString().contains("svg"))
    }

    @Test
    fun `test categories are correctly defined`() {
        val categories = nodeModel.categories
        assertEquals(2, categories.size)
        assertEquals("RDKit", categories[0])
        assertEquals("Calculators", categories[1])
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
    fun `test execute with AMW SlogP NumRings on benzene produces reasonable values`() {
        val rows = listOf(
            mapOf("smiles" to "c1ccccc1", "name" to "benzene")
        )
        mockSequentialReads(mockInputReader, rows)
        whenever(mockInputReader.getRowCount()).thenReturn(1)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "descriptors" to listOf("AMW", "SlogP", "NumRings"),
            "columnPrefix" to ""
        )
        val inputs = mapOf("input" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        val outputRow = rowCaptor.allValues[0]

        // AMW of benzene (C6H6) should be approximately 78.11
        val amw = (outputRow["AMW"] as Number).toDouble()
        assertTrue(amw > 77.0 && amw < 79.0, "AMW of benzene should be ~78.11 but was $amw")

        // SlogP of benzene should be approximately 1.6866
        val slogp = (outputRow["SlogP"] as Number).toDouble()
        assertTrue(slogp > 1.0 && slogp < 2.5, "SlogP of benzene should be ~1.69 but was $slogp")

        // NumRings of benzene should be 1
        val numRings = (outputRow["NumRings"] as Number).toInt()
        assertEquals(1, numRings)

        // Original columns should be preserved
        assertEquals("c1ccccc1", outputRow["smiles"])
        assertEquals("benzene", outputRow["name"])
    }

    @Test
    fun `test execute with invalid SMILES writes empty strings for all descriptor columns`() {
        val rows = listOf(
            mapOf("smiles" to "INVALID_SMILES", "name" to "bad")
        )
        mockSequentialReads(mockInputReader, rows)
        whenever(mockInputReader.getRowCount()).thenReturn(1)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "descriptors" to listOf("AMW", "SlogP", "NumRings"),
            "columnPrefix" to ""
        )
        val inputs = mapOf("input" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        val outputRow = rowCaptor.allValues[0]

        // All descriptor columns should be empty strings for invalid SMILES
        assertEquals("", outputRow["AMW"])
        assertEquals("", outputRow["SlogP"])
        assertEquals("", outputRow["NumRings"])

        // Original columns should still be preserved
        assertEquals("INVALID_SMILES", outputRow["smiles"])
        assertEquals("bad", outputRow["name"])
    }

    @Test
    fun `test execute with columnPrefix adds prefix to output column names`() {
        val rows = listOf(
            mapOf("smiles" to "c1ccccc1", "name" to "benzene")
        )
        mockSequentialReads(mockInputReader, rows)
        whenever(mockInputReader.getRowCount()).thenReturn(1)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "descriptors" to listOf("AMW", "SlogP"),
            "columnPrefix" to "rdkit_"
        )
        val inputs = mapOf("input" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        val outputRow = rowCaptor.allValues[0]

        // Columns should have the prefix
        assertTrue(outputRow.containsKey("rdkit_AMW"))
        assertTrue(outputRow.containsKey("rdkit_SlogP"))

        // Columns without prefix should NOT exist
        assertTrue(!outputRow.containsKey("AMW") || outputRow.containsKey("rdkit_AMW"))
        assertTrue(!outputRow.containsKey("SlogP") || outputRow.containsKey("rdkit_SlogP"))

        // Values should be numeric
        val amw = (outputRow["rdkit_AMW"] as Number).toDouble()
        assertTrue(amw > 0, "Prefixed AMW should be a positive number")
    }

    // ===== Error Tests =====

    @Test
    fun `test execute throws exception when smilesColumn is missing`() {
        val properties = mapOf(
            "descriptors" to listOf("AMW"),
            "columnPrefix" to ""
        )
        val inputs = mapOf("input" to mockInputReader)

        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)
        }
        assertEquals("smilesColumn is not provided", exception.message)
    }

    @Test
    fun `test execute throws exception when descriptors is missing`() {
        val properties = mapOf(
            "smilesColumn" to "smiles",
            "columnPrefix" to ""
        )
        val inputs = mapOf("input" to mockInputReader)

        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)
        }
        assertEquals("descriptors is not provided", exception.message)
    }

    @Test
    fun `test execute throws exception when descriptors is empty list`() {
        val properties = mapOf(
            "smilesColumn" to "smiles",
            "descriptors" to emptyList<String>(),
            "columnPrefix" to ""
        )
        val inputs = mapOf("input" to mockInputReader)

        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)
        }
        assertEquals("descriptors must not be empty", exception.message)
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
            "descriptors" to listOf("AMW"),
            "columnPrefix" to ""
        )
        val inputs = emptyMap<String, NodeInputReader>()

        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)
        }
        assertEquals("Input port 'input' is not connected", exception.message)
    }

    // ===== Edge Cases =====

    @Test
    fun `test execute with empty SMILES string writes empty strings`() {
        val rows = listOf(
            mapOf("smiles" to "", "name" to "empty")
        )
        mockSequentialReads(mockInputReader, rows)
        whenever(mockInputReader.getRowCount()).thenReturn(1)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "descriptors" to listOf("AMW", "NumRings"),
            "columnPrefix" to ""
        )
        val inputs = mapOf("input" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        assertEquals("", rowCaptor.allValues[0]["AMW"])
        assertEquals("", rowCaptor.allValues[0]["NumRings"])
    }

    @Test
    fun `test execute with multiple rows processes all`() {
        val rows = listOf(
            mapOf("smiles" to "c1ccccc1", "name" to "benzene"),
            mapOf("smiles" to "CC", "name" to "ethane"),
            mapOf("smiles" to "INVALID", "name" to "bad")
        )
        mockSequentialReads(mockInputReader, rows)
        whenever(mockInputReader.getRowCount()).thenReturn(3)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "descriptors" to listOf("AMW"),
            "columnPrefix" to ""
        )
        val inputs = mapOf("input" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(3)).write(any(), rowCaptor.capture())
        assertEquals(3, rowCaptor.allValues.size)

        // Benzene AMW should be numeric
        assertTrue((rowCaptor.allValues[0]["AMW"] as Number).toDouble() > 0)
        // Ethane AMW should be numeric
        assertTrue((rowCaptor.allValues[1]["AMW"] as Number).toDouble() > 0)
        // Invalid SMILES should be empty string
        assertEquals("", rowCaptor.allValues[2]["AMW"])
    }

    @Test
    fun `test execute reports progress`() {
        val rows = listOf(
            mapOf("smiles" to "C"),
            mapOf("smiles" to "CC"),
            mapOf("smiles" to "CCC"),
            mapOf("smiles" to "CCCC")
        )
        mockSequentialReads(mockInputReader, rows)
        whenever(mockInputReader.getRowCount()).thenReturn(4)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "descriptors" to listOf("AMW"),
            "columnPrefix" to ""
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
    fun `test execute properly closes output writer and input reader`() {
        val rows = listOf(mapOf("smiles" to "C"))
        mockSequentialReads(mockInputReader, rows)
        whenever(mockInputReader.getRowCount()).thenReturn(1)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "descriptors" to listOf("AMW"),
            "columnPrefix" to ""
        )
        val inputs = mapOf("input" to mockInputReader)

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter).close()
        verify(mockInputReader).close()
    }

    @Test
    fun `test execute with default columnPrefix produces unprefixed columns`() {
        val rows = listOf(
            mapOf("smiles" to "c1ccccc1")
        )
        mockSequentialReads(mockInputReader, rows)
        whenever(mockInputReader.getRowCount()).thenReturn(1)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "descriptors" to listOf("TPSA", "NumHBD")
        )
        val inputs = mapOf("input" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        val outputRow = rowCaptor.allValues[0]

        // Without columnPrefix, columns should use bare descriptor names
        assertTrue(outputRow.containsKey("TPSA"))
        assertTrue(outputRow.containsKey("NumHBD"))
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
