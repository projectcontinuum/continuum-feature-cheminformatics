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
class StructureNormalizerNodeModelTest {

    private lateinit var nodeModel: StructureNormalizerNodeModel
    private lateinit var mockInputReader: NodeInputReader
    private lateinit var mockOutputWriter: NodeOutputWriter
    private lateinit var mockPortWriter: NodeOutputWriter.OutputPortWriter
    private lateinit var mockProgressCallback: NodeProgressCallback

    @BeforeEach
    fun setUp() {
        nodeModel = StructureNormalizerNodeModel()
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
        assertEquals("org.projectcontinuum.feature.rdkit.node.StructureNormalizerNodeModel", metadata.id)
        assertEquals("Applies configurable normalization steps to standardize molecule SMILES representations", metadata.description)
        assertEquals("Structure Normalizer", metadata.title)
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
    fun `test execute with RemoveFragments removes salt`() {
        val rows = listOf(
            mapOf("smiles" to "CC(=O)[O-].[Na+]", "name" to "sodium_acetate")
        )
        mockSequentialReads(mockInputReader, rows)
        whenever(mockInputReader.getRowCount()).thenReturn(1)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "newColumnName" to "normalized_smiles",
            "removeSourceColumn" to false,
            "normalizationSteps" to listOf("RemoveFragments")
        )
        val inputs = mapOf("input" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        val outputRow = rowCaptor.allValues[0]
        assertTrue(outputRow.containsKey("normalized_smiles"))
        val normalized = outputRow["normalized_smiles"] as String
        // Should not contain the dot (salt separator) and should not contain Na
        assertTrue(!normalized.contains("[Na+]"))
    }

    @Test
    fun `test execute with Canonicalize produces canonical SMILES`() {
        val rows = listOf(
            mapOf("smiles" to "C(O)=O", "name" to "formic_acid")
        )
        mockSequentialReads(mockInputReader, rows)
        whenever(mockInputReader.getRowCount()).thenReturn(1)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "newColumnName" to "normalized_smiles",
            "normalizationSteps" to listOf("Canonicalize")
        )
        val inputs = mapOf("input" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        val normalized = rowCaptor.allValues[0]["normalized_smiles"] as String
        assertTrue(normalized.isNotEmpty())
    }

    @Test
    fun `test execute with simple molecule passes through`() {
        val rows = listOf(
            mapOf("smiles" to "c1ccccc1", "name" to "benzene")
        )
        mockSequentialReads(mockInputReader, rows)
        whenever(mockInputReader.getRowCount()).thenReturn(1)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "newColumnName" to "normalized_smiles",
            "normalizationSteps" to listOf("RemoveFragments", "Canonicalize")
        )
        val inputs = mapOf("input" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        val normalized = rowCaptor.allValues[0]["normalized_smiles"] as String
        assertTrue(normalized.isNotEmpty())
    }

    @Test
    fun `test execute with invalid SMILES writes empty string`() {
        val rows = listOf(
            mapOf("smiles" to "INVALID", "name" to "bad")
        )
        mockSequentialReads(mockInputReader, rows)
        whenever(mockInputReader.getRowCount()).thenReturn(1)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "newColumnName" to "normalized_smiles",
            "normalizationSteps" to listOf("Canonicalize")
        )
        val inputs = mapOf("input" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        assertEquals("", rowCaptor.allValues[0]["normalized_smiles"])
    }

    @Test
    fun `test execute with removeSourceColumn removes source`() {
        val rows = listOf(
            mapOf("smiles" to "c1ccccc1", "name" to "benzene")
        )
        mockSequentialReads(mockInputReader, rows)
        whenever(mockInputReader.getRowCount()).thenReturn(1)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "newColumnName" to "normalized_smiles",
            "removeSourceColumn" to true,
            "normalizationSteps" to listOf("Canonicalize")
        )
        val inputs = mapOf("input" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        assertTrue(!rowCaptor.allValues[0].containsKey("smiles"))
        assertTrue(rowCaptor.allValues[0].containsKey("normalized_smiles"))
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
            "newColumnName" to "normalized_smiles",
            "normalizationSteps" to listOf("Canonicalize")
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

