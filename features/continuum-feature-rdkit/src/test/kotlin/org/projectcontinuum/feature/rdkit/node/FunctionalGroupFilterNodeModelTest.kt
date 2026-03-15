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
class FunctionalGroupFilterNodeModelTest {

    private lateinit var nodeModel: FunctionalGroupFilterNodeModel
    private lateinit var mockInputReader: NodeInputReader
    private lateinit var mockOutputWriter: NodeOutputWriter
    private lateinit var mockPassWriter: NodeOutputWriter.OutputPortWriter
    private lateinit var mockFailWriter: NodeOutputWriter.OutputPortWriter
    private lateinit var mockProgressCallback: NodeProgressCallback

    @BeforeEach
    fun setUp() {
        nodeModel = FunctionalGroupFilterNodeModel()
        mockInputReader = mock()
        mockOutputWriter = mock()
        mockPassWriter = mock()
        mockFailWriter = mock()
        mockProgressCallback = mock()
        whenever(mockOutputWriter.createOutputPortWriter("pass")).thenReturn(mockPassWriter)
        whenever(mockOutputWriter.createOutputPortWriter("fail")).thenReturn(mockFailWriter)
    }

    // ===== Metadata Tests =====

    @Test
    fun `test node metadata is properly configured`() {
        val metadata = nodeModel.metadata
        assertEquals("org.projectcontinuum.feature.rdkit.node.FunctionalGroupFilterNodeModel", metadata.id)
        assertEquals("Filters molecules by functional group presence and count using SMARTS patterns", metadata.description)
        assertEquals("Functional Group Filter", metadata.title)
        assertEquals("Split molecules by functional group criteria", metadata.subTitle)
        assertNotNull(metadata.icon)
        assertTrue(metadata.icon.toString().contains("svg"))
    }

    @Test
    fun `test categories are correctly defined`() {
        val categories = nodeModel.categories
        assertEquals(2, categories.size)
        assertEquals("RDKit", categories[0])
        assertEquals("Searching", categories[1])
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
    fun `test output ports have two ports pass and fail`() {
        val outputPorts = nodeModel.outputPorts
        assertEquals(2, outputPorts.size)
        assertNotNull(outputPorts["pass"])
        assertEquals("pass table", outputPorts["pass"]!!.name)
        assertNotNull(outputPorts["fail"])
        assertEquals("fail table", outputPorts["fail"]!!.name)
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
    fun `test execute with hydroxyl filter passes phenol and fails benzene`() {
        val rows = listOf(
            mapOf("smiles" to "c1ccc(O)cc1", "name" to "phenol"),
            mapOf("smiles" to "c1ccccc1", "name" to "benzene")
        )
        mockSequentialReads(mockInputReader, rows)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "functionalGroups" to listOf(
                mapOf("name" to "Hydroxyl", "smarts" to "[OH]", "minCount" to 1, "maxCount" to -1)
            )
        )
        val inputs = mapOf("input" to mockInputReader)
        val passCaptor = argumentCaptor<Map<String, Any>>()
        val failCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPassWriter, times(1)).write(any(), passCaptor.capture())
        verify(mockFailWriter, times(1)).write(any(), failCaptor.capture())

        assertEquals("phenol", passCaptor.allValues[0]["name"])
        assertEquals("benzene", failCaptor.allValues[0]["name"])

        verify(mockPassWriter).close()
        verify(mockFailWriter).close()
    }

    @Test
    fun `test execute with maxCount filters molecules with too many matches`() {
        val rows = listOf(
            mapOf("smiles" to "c1ccc(O)cc1", "name" to "phenol"),
            mapOf("smiles" to "Oc1cc(O)ccc1", "name" to "catechol")
        )
        mockSequentialReads(mockInputReader, rows)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "functionalGroups" to listOf(
                mapOf("name" to "Hydroxyl", "smarts" to "[OH]", "minCount" to 1, "maxCount" to 1)
            )
        )
        val inputs = mapOf("input" to mockInputReader)
        val passCaptor = argumentCaptor<Map<String, Any>>()
        val failCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPassWriter, times(1)).write(any(), passCaptor.capture())
        verify(mockFailWriter, times(1)).write(any(), failCaptor.capture())

        // Phenol has exactly 1 OH, catechol has 2 OH
        assertEquals("phenol", passCaptor.allValues[0]["name"])
        assertEquals("catechol", failCaptor.allValues[0]["name"])
    }

    @Test
    fun `test execute with multiple functional groups uses AND logic`() {
        val rows = listOf(
            mapOf("smiles" to "c1ccc(O)cc1", "name" to "phenol"),
            mapOf("smiles" to "c1ccc(N)cc1", "name" to "aniline"),
            mapOf("smiles" to "CCO", "name" to "ethanol")
        )
        mockSequentialReads(mockInputReader, rows)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "functionalGroups" to listOf(
                mapOf("name" to "Hydroxyl", "smarts" to "[OH]", "minCount" to 1, "maxCount" to -1),
                mapOf("name" to "Aromatic Ring", "smarts" to "c1ccccc1", "minCount" to 1, "maxCount" to -1)
            )
        )
        val inputs = mapOf("input" to mockInputReader)
        val passCaptor = argumentCaptor<Map<String, Any>>()
        val failCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        // Only phenol has both OH and aromatic ring
        verify(mockPassWriter, times(1)).write(any(), passCaptor.capture())
        verify(mockFailWriter, times(2)).write(any(), failCaptor.capture())

        assertEquals("phenol", passCaptor.allValues[0]["name"])

        val failedNames = failCaptor.allValues.map { it["name"] as String }.toSet()
        assertTrue(failedNames.contains("aniline"))
        assertTrue(failedNames.contains("ethanol"))
    }

    // ===== Error Tests =====

    @Test
    fun `test execute throws exception when smilesColumn is missing`() {
        val properties = mapOf(
            "functionalGroups" to listOf(
                mapOf("name" to "Hydroxyl", "smarts" to "[OH]", "minCount" to 1, "maxCount" to -1)
            )
        )
        val inputs = mapOf("input" to mockInputReader)

        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)
        }
        assertEquals("smilesColumn is not provided", exception.message)
    }

    @Test
    fun `test execute throws exception when functionalGroups is missing`() {
        val properties = mapOf(
            "smilesColumn" to "smiles"
        )
        val inputs = mapOf("input" to mockInputReader)

        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)
        }
        assertEquals("functionalGroups is not provided", exception.message)
    }

    @Test
    fun `test execute throws exception when functionalGroups is empty`() {
        val properties = mapOf(
            "smilesColumn" to "smiles",
            "functionalGroups" to emptyList<Map<String, Any>>()
        )
        val inputs = mapOf("input" to mockInputReader)

        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)
        }
        assertEquals("functionalGroups must contain at least one entry", exception.message)
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
            "functionalGroups" to listOf(
                mapOf("name" to "Hydroxyl", "smarts" to "[OH]", "minCount" to 1, "maxCount" to -1)
            )
        )
        val inputs = emptyMap<String, NodeInputReader>()

        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)
        }
        assertEquals("Input port 'input' is not connected", exception.message)
    }

    // ===== Edge Cases =====

    @Test
    fun `test execute with invalid SMILES sends to fail`() {
        val rows = listOf(
            mapOf("smiles" to "INVALID", "name" to "bad")
        )
        mockSequentialReads(mockInputReader, rows)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "functionalGroups" to listOf(
                mapOf("name" to "Hydroxyl", "smarts" to "[OH]", "minCount" to 1, "maxCount" to -1)
            )
        )
        val inputs = mapOf("input" to mockInputReader)
        val failCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPassWriter, times(0)).write(any(), any())
        verify(mockFailWriter, times(1)).write(any(), failCaptor.capture())
        assertEquals("INVALID", failCaptor.allValues[0]["smiles"])
    }

    @Test
    fun `test execute with empty input produces empty outputs`() {
        mockSequentialReads(mockInputReader, emptyList())

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "functionalGroups" to listOf(
                mapOf("name" to "Hydroxyl", "smarts" to "[OH]", "minCount" to 1, "maxCount" to -1)
            )
        )
        val inputs = mapOf("input" to mockInputReader)

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPassWriter, times(0)).write(any(), any())
        verify(mockFailWriter, times(0)).write(any(), any())
        verify(mockPassWriter).close()
        verify(mockFailWriter).close()
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
