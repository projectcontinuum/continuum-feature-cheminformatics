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
class FingerprintSimilarityNodeModelTest {

    private lateinit var nodeModel: FingerprintSimilarityNodeModel
    private lateinit var mockInputReader: NodeInputReader
    private lateinit var mockOutputWriter: NodeOutputWriter
    private lateinit var mockPortWriter: NodeOutputWriter.OutputPortWriter
    private lateinit var mockProgressCallback: NodeProgressCallback

    @BeforeEach
    fun setUp() {
        nodeModel = FingerprintSimilarityNodeModel()
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
        assertEquals("org.projectcontinuum.feature.rdkit.node.FingerprintSimilarityNodeModel", metadata.id)
        assertEquals("Computes pairwise molecular similarity from two SMILES columns using RDKit fingerprints", metadata.description)
        assertEquals("Fingerprint Similarity", metadata.title)
        assertEquals("Compute Tanimoto/Dice similarity between molecule pairs", metadata.subTitle)
        assertNotNull(metadata.icon)
        assertTrue(metadata.icon.toString().contains("svg"))
    }

    @Test
    fun `test categories are correctly defined`() {
        val categories = nodeModel.categories
        assertEquals(1, categories.size)
        assertEquals("RDKit/Fingerprints", categories[0])
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
    fun `test execute with benzene vs benzene produces similarity of 1 point 0`() {
        val rows = listOf(
            mapOf("smiles_a" to "c1ccccc1", "smiles_b" to "c1ccccc1", "name" to "identical")
        )
        mockSequentialReads(mockInputReader, rows)
        whenever(mockInputReader.getRowCount()).thenReturn(1)

        val properties = mapOf(
            "smilesColumn1" to "smiles_a",
            "smilesColumn2" to "smiles_b",
            "fingerprintType" to "Morgan",
            "similarityMetric" to "Tanimoto",
            "numBits" to 2048,
            "radius" to 2,
            "newColumnName" to "similarity",
            "removeSourceColumns" to false
        )
        val inputs = mapOf("input" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        val outputRow = rowCaptor.allValues[0]

        // Identical molecules should have Tanimoto similarity of 1.0
        val similarity = (outputRow["similarity"] as Number).toDouble()
        assertEquals(1.0, similarity, 0.001)

        // Original columns should be preserved
        assertEquals("c1ccccc1", outputRow["smiles_a"])
        assertEquals("c1ccccc1", outputRow["smiles_b"])
        assertEquals("identical", outputRow["name"])
    }

    @Test
    fun `test execute with benzene vs pyridine produces similarity between 0 and 1`() {
        val rows = listOf(
            mapOf("smiles_a" to "c1ccccc1", "smiles_b" to "c1ccncc1", "name" to "benz_vs_pyr")
        )
        mockSequentialReads(mockInputReader, rows)
        whenever(mockInputReader.getRowCount()).thenReturn(1)

        val properties = mapOf(
            "smilesColumn1" to "smiles_a",
            "smilesColumn2" to "smiles_b",
            "fingerprintType" to "Morgan",
            "similarityMetric" to "Tanimoto",
            "numBits" to 2048,
            "radius" to 2,
            "newColumnName" to "similarity"
        )
        val inputs = mapOf("input" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        val outputRow = rowCaptor.allValues[0]

        // Benzene vs pyridine should have similarity between 0 and 1 (exclusive)
        val similarity = (outputRow["similarity"] as Number).toDouble()
        assertTrue(similarity > 0.0, "Similarity between benzene and pyridine should be > 0, was $similarity")
        assertTrue(similarity < 1.0, "Similarity between benzene and pyridine should be < 1, was $similarity")
    }

    @Test
    fun `test execute with invalid SMILES writes empty string`() {
        val rows = listOf(
            mapOf("smiles_a" to "INVALID_SMILES", "smiles_b" to "c1ccccc1", "name" to "bad")
        )
        mockSequentialReads(mockInputReader, rows)
        whenever(mockInputReader.getRowCount()).thenReturn(1)

        val properties = mapOf(
            "smilesColumn1" to "smiles_a",
            "smilesColumn2" to "smiles_b",
            "fingerprintType" to "Morgan",
            "similarityMetric" to "Tanimoto",
            "numBits" to 2048,
            "radius" to 2,
            "newColumnName" to "similarity"
        )
        val inputs = mapOf("input" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())

        // Invalid SMILES should produce empty string
        assertEquals("", rowCaptor.allValues[0]["similarity"])
        // Original columns still preserved
        assertEquals("INVALID_SMILES", rowCaptor.allValues[0]["smiles_a"])
        assertEquals("c1ccccc1", rowCaptor.allValues[0]["smiles_b"])
    }

    // ===== Error Tests =====

    @Test
    fun `test execute throws exception when smilesColumn1 is missing`() {
        val properties = mapOf(
            "smilesColumn2" to "smiles_b",
            "newColumnName" to "similarity"
        )
        val inputs = mapOf("input" to mockInputReader)

        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)
        }
        assertEquals("smilesColumn1 is not provided", exception.message)
    }

    @Test
    fun `test execute throws exception when smilesColumn2 is missing`() {
        val properties = mapOf(
            "smilesColumn1" to "smiles_a",
            "newColumnName" to "similarity"
        )
        val inputs = mapOf("input" to mockInputReader)

        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)
        }
        assertEquals("smilesColumn2 is not provided", exception.message)
    }

    @Test
    fun `test execute throws exception when properties are null`() {
        val inputs = mapOf("input" to mockInputReader)

        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(null, inputs, mockOutputWriter, mockProgressCallback)
        }
        assertEquals("smilesColumn1 is not provided", exception.message)
    }

    @Test
    fun `test execute throws exception when input port is missing`() {
        val properties = mapOf(
            "smilesColumn1" to "smiles_a",
            "smilesColumn2" to "smiles_b"
        )
        val inputs = emptyMap<String, NodeInputReader>()

        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)
        }
        assertEquals("Input port 'input' is not connected", exception.message)
    }

    // ===== Edge Cases =====

    @Test
    fun `test execute with removeSourceColumns removes both source columns`() {
        val rows = listOf(
            mapOf("smiles_a" to "c1ccccc1", "smiles_b" to "c1ccccc1", "name" to "test")
        )
        mockSequentialReads(mockInputReader, rows)
        whenever(mockInputReader.getRowCount()).thenReturn(1)

        val properties = mapOf(
            "smilesColumn1" to "smiles_a",
            "smilesColumn2" to "smiles_b",
            "fingerprintType" to "Morgan",
            "similarityMetric" to "Tanimoto",
            "numBits" to 2048,
            "radius" to 2,
            "newColumnName" to "similarity",
            "removeSourceColumns" to true
        )
        val inputs = mapOf("input" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        val outputRow = rowCaptor.allValues[0]

        // Source columns should be removed
        assertTrue(!outputRow.containsKey("smiles_a"))
        assertTrue(!outputRow.containsKey("smiles_b"))
        // Other columns should be preserved
        assertEquals("test", outputRow["name"])
        // Result column should be present
        assertTrue(outputRow.containsKey("similarity"))
    }

    @Test
    fun `test execute uses default property values`() {
        val rows = listOf(
            mapOf("smiles_a" to "c1ccccc1", "smiles_b" to "c1ccccc1")
        )
        mockSequentialReads(mockInputReader, rows)
        whenever(mockInputReader.getRowCount()).thenReturn(1)

        val properties = mapOf(
            "smilesColumn1" to "smiles_a",
            "smilesColumn2" to "smiles_b"
        )
        val inputs = mapOf("input" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        // Default column name is "similarity"
        assertTrue(rowCaptor.allValues[0].containsKey("similarity"))
        // Source columns should NOT be removed by default
        assertTrue(rowCaptor.allValues[0].containsKey("smiles_a"))
        assertTrue(rowCaptor.allValues[0].containsKey("smiles_b"))
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
