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
class SubstructFilterNodeModelTest {

    private lateinit var nodeModel: SubstructFilterNodeModel
    private lateinit var mockInputReader: NodeInputReader
    private lateinit var mockOutputWriter: NodeOutputWriter
    private lateinit var mockMatchWriter: NodeOutputWriter.OutputPortWriter
    private lateinit var mockNoMatchWriter: NodeOutputWriter.OutputPortWriter
    private lateinit var mockProgressCallback: NodeProgressCallback

    @BeforeEach
    fun setUp() {
        nodeModel = SubstructFilterNodeModel()
        mockInputReader = mock()
        mockOutputWriter = mock()
        mockMatchWriter = mock()
        mockNoMatchWriter = mock()
        mockProgressCallback = mock()
        whenever(mockOutputWriter.createOutputPortWriter("match")).thenReturn(mockMatchWriter)
        whenever(mockOutputWriter.createOutputPortWriter("noMatch")).thenReturn(mockNoMatchWriter)
    }

    // ===== Metadata Tests =====

    @Test
    fun `test node metadata is properly configured`() {
        val metadata = nodeModel.metadata
        assertEquals("org.projectcontinuum.feature.rdkit.node.SubstructFilterNodeModel", metadata.id)
        assertEquals("Filters molecules by SMARTS substructure query, splitting matches and non-matches into separate output ports", metadata.description)
        assertEquals("Substructure Filter", metadata.title)
        assertEquals("Split molecules by SMARTS substructure match", metadata.subTitle)
        assertNotNull(metadata.icon)
        assertTrue(metadata.icon.toString().contains("svg"))
    }

    @Test
    fun `test categories are correctly defined`() {
        val categories = nodeModel.categories
        assertEquals(1, categories.size)
        assertEquals("RDKit/Searching", categories[0])
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
    fun `test output ports have two ports match and noMatch`() {
        val outputPorts = nodeModel.outputPorts
        assertEquals(2, outputPorts.size)
        assertNotNull(outputPorts["match"])
        assertEquals("match table", outputPorts["match"]!!.name)
        assertNotNull(outputPorts["noMatch"])
        assertEquals("no match table", outputPorts["noMatch"]!!.name)
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
    fun `test execute phenol matches hydroxyl and benzene does not`() {
        val rows = listOf(
            mapOf("smiles" to "c1ccc(O)cc1", "name" to "phenol"),
            mapOf("smiles" to "c1ccccc1", "name" to "benzene")
        )
        mockSequentialReads(mockInputReader, rows)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "smartsQuery" to "[OH]",
            "useChirality" to false
        )
        val inputs = mapOf("input" to mockInputReader)
        val matchCaptor = argumentCaptor<Map<String, Any>>()
        val noMatchCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockMatchWriter, times(1)).write(any(), matchCaptor.capture())
        verify(mockNoMatchWriter, times(1)).write(any(), noMatchCaptor.capture())

        // Phenol should match [OH]
        assertEquals("c1ccc(O)cc1", matchCaptor.allValues[0]["smiles"])
        assertEquals("phenol", matchCaptor.allValues[0]["name"])

        // Benzene should not match [OH]
        assertEquals("c1ccccc1", noMatchCaptor.allValues[0]["smiles"])
        assertEquals("benzene", noMatchCaptor.allValues[0]["name"])

        // Verify writers are closed
        verify(mockMatchWriter).close()
        verify(mockNoMatchWriter).close()
    }

    @Test
    fun `test execute all molecules match`() {
        val rows = listOf(
            mapOf("smiles" to "c1ccc(O)cc1", "name" to "phenol"),
            mapOf("smiles" to "CCO", "name" to "ethanol")
        )
        mockSequentialReads(mockInputReader, rows)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "smartsQuery" to "[OH]",
            "useChirality" to false
        )
        val inputs = mapOf("input" to mockInputReader)
        val matchCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockMatchWriter, times(2)).write(any(), matchCaptor.capture())
        verify(mockNoMatchWriter, times(0)).write(any(), any())

        val matchedSmiles = matchCaptor.allValues.map { it["smiles"] as String }.toSet()
        assertEquals(setOf("c1ccc(O)cc1", "CCO"), matchedSmiles)
    }

    @Test
    fun `test execute no molecules match`() {
        val rows = listOf(
            mapOf("smiles" to "c1ccccc1", "name" to "benzene"),
            mapOf("smiles" to "C=C", "name" to "ethylene")
        )
        mockSequentialReads(mockInputReader, rows)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "smartsQuery" to "[OH]",
            "useChirality" to false
        )
        val inputs = mapOf("input" to mockInputReader)
        val noMatchCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockMatchWriter, times(0)).write(any(), any())
        verify(mockNoMatchWriter, times(2)).write(any(), noMatchCaptor.capture())

        val noMatchSmiles = noMatchCaptor.allValues.map { it["smiles"] as String }.toSet()
        assertEquals(setOf("c1ccccc1", "C=C"), noMatchSmiles)
    }

    // ===== Error Tests =====

    @Test
    fun `test execute throws exception when smilesColumn is missing`() {
        val properties = mapOf(
            "smartsQuery" to "[OH]"
        )
        val inputs = mapOf("input" to mockInputReader)

        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)
        }
        assertEquals("smilesColumn is not provided", exception.message)
    }

    @Test
    fun `test execute throws exception when smartsQuery is missing`() {
        val properties = mapOf(
            "smilesColumn" to "smiles"
        )
        val inputs = mapOf("input" to mockInputReader)

        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)
        }
        assertEquals("smartsQuery is not provided", exception.message)
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
            "smartsQuery" to "[OH]"
        )
        val inputs = emptyMap<String, NodeInputReader>()

        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)
        }
        assertEquals("Input port 'input' is not connected", exception.message)
    }

    @Test
    fun `test execute throws exception for invalid SMARTS query`() {
        val properties = mapOf(
            "smilesColumn" to "smiles",
            "smartsQuery" to "[INVALID"
        )
        val inputs = mapOf("input" to mockInputReader)

        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)
        }
        assertTrue(exception.message!!.contains("Invalid SMARTS query"))
    }

    // ===== Edge Cases =====

    @Test
    fun `test execute with invalid SMILES sends to noMatch`() {
        val rows = listOf(
            mapOf("smiles" to "INVALID", "name" to "bad"),
            mapOf("smiles" to "c1ccc(O)cc1", "name" to "phenol")
        )
        mockSequentialReads(mockInputReader, rows)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "smartsQuery" to "[OH]",
            "useChirality" to false
        )
        val inputs = mapOf("input" to mockInputReader)
        val matchCaptor = argumentCaptor<Map<String, Any>>()
        val noMatchCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockMatchWriter, times(1)).write(any(), matchCaptor.capture())
        verify(mockNoMatchWriter, times(1)).write(any(), noMatchCaptor.capture())

        assertEquals("c1ccc(O)cc1", matchCaptor.allValues[0]["smiles"])
        assertEquals("INVALID", noMatchCaptor.allValues[0]["smiles"])
    }

    @Test
    fun `test execute with empty input produces empty outputs`() {
        mockSequentialReads(mockInputReader, emptyList())

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "smartsQuery" to "[OH]",
            "useChirality" to false
        )
        val inputs = mapOf("input" to mockInputReader)

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockMatchWriter, times(0)).write(any(), any())
        verify(mockNoMatchWriter, times(0)).write(any(), any())
        verify(mockMatchWriter).close()
        verify(mockNoMatchWriter).close()
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
