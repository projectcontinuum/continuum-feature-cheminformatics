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
class DiversityPickerNodeModelTest {

    private lateinit var nodeModel: DiversityPickerNodeModel
    private lateinit var mockInputReader: NodeInputReader
    private lateinit var mockOutputWriter: NodeOutputWriter
    private lateinit var mockPickedWriter: NodeOutputWriter.OutputPortWriter
    private lateinit var mockUnpickedWriter: NodeOutputWriter.OutputPortWriter
    private lateinit var mockProgressCallback: NodeProgressCallback

    @BeforeEach
    fun setUp() {
        nodeModel = DiversityPickerNodeModel()
        mockInputReader = mock()
        mockOutputWriter = mock()
        mockPickedWriter = mock()
        mockUnpickedWriter = mock()
        mockProgressCallback = mock()
        whenever(mockOutputWriter.createOutputPortWriter("picked")).thenReturn(mockPickedWriter)
        whenever(mockOutputWriter.createOutputPortWriter("unpicked")).thenReturn(mockUnpickedWriter)
    }

    // ===== Metadata Tests =====

    @Test
    fun `test node metadata is properly configured`() {
        val metadata = nodeModel.metadata
        assertEquals("org.projectcontinuum.feature.rdkit.node.DiversityPickerNodeModel", metadata.id)
        assertEquals("Selects a diverse subset of molecules using MaxMin diversity picking with RDKit fingerprints", metadata.description)
        assertEquals("Diversity Picker", metadata.title)
        assertEquals("MaxMin diversity picking for molecular subsets", metadata.subTitle)
        assertNotNull(metadata.icon)
        assertTrue(metadata.icon.toString().contains("svg"))
    }

    @Test
    fun `test categories are correctly defined`() {
        val categories = nodeModel.categories
        assertEquals(2, categories.size)
        assertEquals("RDKit", categories[0])
        assertEquals("Fingerprints", categories[1])
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
    fun `test output ports have two ports picked and unpicked`() {
        val outputPorts = nodeModel.outputPorts
        assertEquals(2, outputPorts.size)
        assertNotNull(outputPorts["picked"])
        assertEquals("picked table", outputPorts["picked"]!!.name)
        assertNotNull(outputPorts["unpicked"])
        assertEquals("unpicked table", outputPorts["unpicked"]!!.name)
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
    fun `test execute picking 2 from 4 molecules splits correctly`() {
        val rows = listOf(
            mapOf("smiles" to "c1ccccc1", "name" to "benzene"),
            mapOf("smiles" to "c1ccncc1", "name" to "pyridine"),
            mapOf("smiles" to "CCO", "name" to "ethanol"),
            mapOf("smiles" to "CC(=O)O", "name" to "acetic acid")
        )
        mockSequentialReads(mockInputReader, rows)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "numberToPick" to 2,
            "randomSeed" to 42,
            "fingerprintType" to "Morgan",
            "numBits" to 2048,
            "radius" to 2
        )
        val inputs = mapOf("input" to mockInputReader)
        val pickedCaptor = argumentCaptor<Map<String, Any>>()
        val unpickedCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPickedWriter, times(2)).write(any(), pickedCaptor.capture())
        verify(mockUnpickedWriter, times(2)).write(any(), unpickedCaptor.capture())

        // 2 picked + 2 unpicked = 4 total
        assertEquals(2, pickedCaptor.allValues.size)
        assertEquals(2, unpickedCaptor.allValues.size)

        // All rows should have their original columns preserved
        val allSmiles = (pickedCaptor.allValues + unpickedCaptor.allValues).map { it["smiles"] as String }.toSet()
        assertEquals(setOf("c1ccccc1", "c1ccncc1", "CCO", "CC(=O)O"), allSmiles)

        // Verify writers are closed
        verify(mockPickedWriter).close()
        verify(mockUnpickedWriter).close()
    }

    @Test
    fun `test execute with numberToPick greater than input size picks all`() {
        val rows = listOf(
            mapOf("smiles" to "c1ccccc1", "name" to "benzene"),
            mapOf("smiles" to "CCO", "name" to "ethanol")
        )
        mockSequentialReads(mockInputReader, rows)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "numberToPick" to 10,
            "randomSeed" to 42,
            "fingerprintType" to "Morgan",
            "numBits" to 2048,
            "radius" to 2
        )
        val inputs = mapOf("input" to mockInputReader)
        val pickedCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPickedWriter, times(2)).write(any(), pickedCaptor.capture())
        assertEquals(2, pickedCaptor.allValues.size)
    }

    // ===== Error Tests =====

    @Test
    fun `test execute throws exception when numberToPick is missing`() {
        val properties = mapOf(
            "smilesColumn" to "smiles",
            "randomSeed" to 42
        )
        val inputs = mapOf("input" to mockInputReader)

        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)
        }
        assertEquals("numberToPick is not provided", exception.message)
    }

    @Test
    fun `test execute throws exception when smilesColumn is missing`() {
        val properties = mapOf(
            "numberToPick" to 2,
            "randomSeed" to 42
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
            "numberToPick" to 2
        )
        val inputs = emptyMap<String, NodeInputReader>()

        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)
        }
        assertEquals("Input port 'input' is not connected", exception.message)
    }

    // ===== Edge Cases =====

    @Test
    fun `test execute with invalid SMILES sends them to unpicked`() {
        val rows = listOf(
            mapOf("smiles" to "c1ccccc1", "name" to "benzene"),
            mapOf("smiles" to "INVALID", "name" to "bad"),
            mapOf("smiles" to "CCO", "name" to "ethanol")
        )
        mockSequentialReads(mockInputReader, rows)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "numberToPick" to 2,
            "randomSeed" to 42,
            "fingerprintType" to "Morgan",
            "numBits" to 2048,
            "radius" to 2
        )
        val inputs = mapOf("input" to mockInputReader)
        val pickedCaptor = argumentCaptor<Map<String, Any>>()
        val unpickedCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPickedWriter, times(2)).write(any(), pickedCaptor.capture())
        verify(mockUnpickedWriter, times(1)).write(any(), unpickedCaptor.capture())

        // The invalid SMILES row should be in unpicked
        val unpickedSmiles = unpickedCaptor.allValues.map { it["smiles"] as String }
        assertTrue(unpickedSmiles.contains("INVALID"))
    }

    @Test
    fun `test execute with empty input produces empty outputs`() {
        mockSequentialReads(mockInputReader, emptyList())

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "numberToPick" to 2,
            "randomSeed" to 42,
            "fingerprintType" to "Morgan",
            "numBits" to 2048,
            "radius" to 2
        )
        val inputs = mapOf("input" to mockInputReader)

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPickedWriter, times(0)).write(any(), any())
        verify(mockUnpickedWriter, times(0)).write(any(), any())
        // Writers should still be closed
        verify(mockPickedWriter).close()
        verify(mockUnpickedWriter).close()
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
