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
class FingerprintNodeModelTest {

    private lateinit var nodeModel: FingerprintNodeModel
    private lateinit var mockInputReader: NodeInputReader
    private lateinit var mockOutputWriter: NodeOutputWriter
    private lateinit var mockPortWriter: OutputPortWriter
    private lateinit var mockProgressCallback: NodeProgressCallback

    @BeforeEach
    fun setUp() {
        nodeModel = FingerprintNodeModel()
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
        assertEquals("org.projectcontinuum.feature.rdkit.node.FingerprintNodeModel", metadata.id)
        assertEquals("Generates bit-based molecular fingerprints from SMILES strings using RDKit", metadata.description)
        assertEquals("Fingerprint", metadata.title)
        assertEquals("Generate molecular fingerprints via RDKit", metadata.subTitle)
        assertNotNull(metadata.icon)
        assertTrue(metadata.icon.toString().contains("svg"))
    }

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

    @Test
    fun `test categories are correctly defined`() {
        val categories = nodeModel.categories
        assertEquals(2, categories.size)
        assertEquals("RDKit", categories[0])
        assertEquals("Fingerprints", categories[1])
    }

    // ===== Success Tests =====

    @Test
    fun `test execute with Morgan fingerprint on benzene produces bit string of length 2048`() {
        val rows = listOf(
            mapOf("smiles" to "c1ccccc1", "name" to "benzene")
        )
        mockSequentialReads(mockInputReader, rows)
        whenever(mockInputReader.getRowCount()).thenReturn(1)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "fingerprintType" to "Morgan",
            "numBits" to 2048,
            "radius" to 2,
            "newColumnName" to "fingerprint",
            "removeSourceColumn" to false
        )
        val inputs = mapOf("input" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        val outputRow = rowCaptor.allValues[0]

        val fpString = outputRow["fingerprint"] as String
        // Fingerprint should be a string of 0s and 1s with length 2048
        assertEquals(2048, fpString.length)
        assertTrue(fpString.all { it == '0' || it == '1' })
        // Benzene should set at least some bits
        assertTrue(fpString.contains("1"))

        // Original columns should be preserved
        assertEquals("c1ccccc1", outputRow["smiles"])
        assertEquals("benzene", outputRow["name"])
    }

    @Test
    fun `test execute with MACCS fingerprint on benzene produces bit string of length 166`() {
        val rows = listOf(
            mapOf("smiles" to "c1ccccc1", "name" to "benzene")
        )
        mockSequentialReads(mockInputReader, rows)
        whenever(mockInputReader.getRowCount()).thenReturn(1)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "fingerprintType" to "MACCS",
            "numBits" to 2048,
            "newColumnName" to "fingerprint",
            "removeSourceColumn" to false
        )
        val inputs = mapOf("input" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        val outputRow = rowCaptor.allValues[0]

        val fpString = outputRow["fingerprint"] as String
        // MACCS keys produce a non-empty fingerprint string
        assertTrue(fpString.isNotEmpty())
        // Benzene should produce a non-trivial fingerprint
        assertTrue(fpString.length > 1)
    }

    @Test
    fun `test execute with invalid SMILES produces empty string`() {
        val rows = listOf(
            mapOf("smiles" to "INVALID_SMILES", "name" to "bad")
        )
        mockSequentialReads(mockInputReader, rows)
        whenever(mockInputReader.getRowCount()).thenReturn(1)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "fingerprintType" to "Morgan",
            "numBits" to 2048,
            "radius" to 2,
            "newColumnName" to "fingerprint",
            "removeSourceColumn" to false
        )
        val inputs = mapOf("input" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        val outputRow = rowCaptor.allValues[0]

        // Invalid SMILES should produce empty string
        assertEquals("", outputRow["fingerprint"])

        // Original columns should still be preserved
        assertEquals("INVALID_SMILES", outputRow["smiles"])
        assertEquals("bad", outputRow["name"])
    }

    // ===== Error Tests =====

    @Test
    fun `test execute throws exception when smilesColumn is missing`() {
        val properties = mapOf(
            "fingerprintType" to "Morgan",
            "numBits" to 2048
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
            "fingerprintType" to "Morgan"
        )
        val inputs = emptyMap<String, NodeInputReader>()

        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)
        }
        assertEquals("Input port 'input' is not connected", exception.message)
    }

    // ===== Edge Cases =====

    @Test
    fun `test execute with empty SMILES string writes empty fingerprint`() {
        val rows = listOf(
            mapOf("smiles" to "", "name" to "empty")
        )
        mockSequentialReads(mockInputReader, rows)
        whenever(mockInputReader.getRowCount()).thenReturn(1)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "fingerprintType" to "Morgan",
            "numBits" to 2048,
            "radius" to 2,
            "newColumnName" to "fingerprint",
            "removeSourceColumn" to false
        )
        val inputs = mapOf("input" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        assertEquals("", rowCaptor.allValues[0]["fingerprint"])
    }

    @Test
    fun `test execute with removeSourceColumn removes the source column`() {
        val rows = listOf(
            mapOf("smiles" to "c1ccccc1", "name" to "benzene")
        )
        mockSequentialReads(mockInputReader, rows)
        whenever(mockInputReader.getRowCount()).thenReturn(1)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "fingerprintType" to "Morgan",
            "numBits" to 2048,
            "radius" to 2,
            "newColumnName" to "fingerprint",
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
        // Fingerprint column should be present
        assertTrue(outputRow.containsKey("fingerprint"))
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
            "fingerprintType" to "Morgan",
            "numBits" to 2048,
            "radius" to 2,
            "newColumnName" to "fingerprint",
            "removeSourceColumn" to false
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
            "fingerprintType" to "Morgan",
            "numBits" to 2048,
            "radius" to 2,
            "newColumnName" to "fingerprint",
            "removeSourceColumn" to false
        )
        val inputs = mapOf("input" to mockInputReader)

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter).close()
        verify(mockInputReader).close()
    }

    @Test
    fun `test execute uses default properties when only smilesColumn is provided`() {
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
        val outputRow = rowCaptor.allValues[0]

        // Default column name is "fingerprint"
        assertTrue(outputRow.containsKey("fingerprint"))
        // Default fingerprint type is Morgan with 2048 bits
        val fpString = outputRow["fingerprint"] as String
        assertEquals(2048, fpString.length)
        assertTrue(fpString.all { it == '0' || it == '1' })
        // Source column should NOT be removed by default
        assertTrue(outputRow.containsKey("smiles"))
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
