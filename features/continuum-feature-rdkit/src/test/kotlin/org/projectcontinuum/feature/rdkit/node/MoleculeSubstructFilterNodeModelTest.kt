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
class MoleculeSubstructFilterNodeModelTest {

    private lateinit var nodeModel: MoleculeSubstructFilterNodeModel
    private lateinit var mockMoleculesReader: NodeInputReader
    private lateinit var mockQueriesReader: NodeInputReader
    private lateinit var mockOutputWriter: NodeOutputWriter
    private lateinit var mockMatchWriter: NodeOutputWriter.OutputPortWriter
    private lateinit var mockNoMatchWriter: NodeOutputWriter.OutputPortWriter
    private lateinit var mockProgressCallback: NodeProgressCallback

    @BeforeEach
    fun setUp() {
        nodeModel = MoleculeSubstructFilterNodeModel()
        mockMoleculesReader = mock()
        mockQueriesReader = mock()
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
        assertEquals("org.projectcontinuum.feature.rdkit.node.MoleculeSubstructFilterNodeModel", metadata.id)
        assertEquals("Filters molecules against a table of SMILES-based substructure queries, splitting matches and non-matches", metadata.description)
        assertEquals("Molecule Substructure Filter", metadata.title)
        assertEquals("Filter molecules using a query table of substructures", metadata.subTitle)
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
    fun `test input ports have two ports molecules and queries`() {
        val inputPorts = nodeModel.inputPorts
        assertEquals(2, inputPorts.size)
        assertNotNull(inputPorts["molecules"])
        assertEquals("molecules table", inputPorts["molecules"]!!.name)
        assertNotNull(inputPorts["queries"])
        assertEquals("queries table", inputPorts["queries"]!!.name)
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
    fun `test execute with any mode matches molecules containing at least one query`() {
        val queryRows = listOf(
            mapOf("query_smiles" to "[OH]")
        )
        val moleculeRows = listOf(
            mapOf("smiles" to "c1ccc(O)cc1", "name" to "phenol"),
            mapOf("smiles" to "c1ccccc1", "name" to "benzene"),
            mapOf("smiles" to "CCO", "name" to "ethanol")
        )
        mockSequentialReads(mockQueriesReader, queryRows)
        mockSequentialReads(mockMoleculesReader, moleculeRows)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "querySmilesColumn" to "query_smiles",
            "matchMode" to "any"
        )
        val inputs = mapOf("molecules" to mockMoleculesReader, "queries" to mockQueriesReader)
        val matchCaptor = argumentCaptor<Map<String, Any>>()
        val noMatchCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockMatchWriter, times(2)).write(any(), matchCaptor.capture())
        verify(mockNoMatchWriter, times(1)).write(any(), noMatchCaptor.capture())

        val matchedNames = matchCaptor.allValues.map { it["name"] as String }.toSet()
        assertTrue(matchedNames.contains("phenol"))
        assertTrue(matchedNames.contains("ethanol"))

        assertEquals("benzene", noMatchCaptor.allValues[0]["name"])

        verify(mockMatchWriter).close()
        verify(mockNoMatchWriter).close()
    }

    @Test
    fun `test execute with all mode requires all queries to match`() {
        val queryRows = listOf(
            mapOf("query_smiles" to "c1ccccc1"),
            mapOf("query_smiles" to "[OH]")
        )
        val moleculeRows = listOf(
            mapOf("smiles" to "c1ccc(O)cc1", "name" to "phenol"),
            mapOf("smiles" to "c1ccccc1", "name" to "benzene"),
            mapOf("smiles" to "CCO", "name" to "ethanol")
        )
        mockSequentialReads(mockQueriesReader, queryRows)
        mockSequentialReads(mockMoleculesReader, moleculeRows)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "querySmilesColumn" to "query_smiles",
            "matchMode" to "all"
        )
        val inputs = mapOf("molecules" to mockMoleculesReader, "queries" to mockQueriesReader)
        val matchCaptor = argumentCaptor<Map<String, Any>>()
        val noMatchCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        // Only phenol has both a benzene ring and [OH]
        verify(mockMatchWriter, times(1)).write(any(), matchCaptor.capture())
        verify(mockNoMatchWriter, times(2)).write(any(), noMatchCaptor.capture())

        assertEquals("phenol", matchCaptor.allValues[0]["name"])

        val noMatchNames = noMatchCaptor.allValues.map { it["name"] as String }.toSet()
        assertTrue(noMatchNames.contains("benzene"))
        assertTrue(noMatchNames.contains("ethanol"))
    }

    @Test
    fun `test execute with no valid queries sends all to noMatch`() {
        val queryRows = listOf(
            mapOf("query_smiles" to "INVALID")
        )
        val moleculeRows = listOf(
            mapOf("smiles" to "c1ccc(O)cc1", "name" to "phenol")
        )
        mockSequentialReads(mockQueriesReader, queryRows)
        mockSequentialReads(mockMoleculesReader, moleculeRows)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "querySmilesColumn" to "query_smiles",
            "matchMode" to "any"
        )
        val inputs = mapOf("molecules" to mockMoleculesReader, "queries" to mockQueriesReader)
        val noMatchCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockMatchWriter, times(0)).write(any(), any())
        verify(mockNoMatchWriter, times(1)).write(any(), noMatchCaptor.capture())
        assertEquals("phenol", noMatchCaptor.allValues[0]["name"])
    }

    // ===== Error Tests =====

    @Test
    fun `test execute throws exception when smilesColumn is missing`() {
        val properties = mapOf(
            "querySmilesColumn" to "query_smiles"
        )
        val inputs = mapOf("molecules" to mockMoleculesReader, "queries" to mockQueriesReader)

        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)
        }
        assertEquals("smilesColumn is not provided", exception.message)
    }

    @Test
    fun `test execute throws exception when querySmilesColumn is missing`() {
        val properties = mapOf(
            "smilesColumn" to "smiles"
        )
        val inputs = mapOf("molecules" to mockMoleculesReader, "queries" to mockQueriesReader)

        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)
        }
        assertEquals("querySmilesColumn is not provided", exception.message)
    }

    @Test
    fun `test execute throws exception when properties are null`() {
        val inputs = mapOf("molecules" to mockMoleculesReader, "queries" to mockQueriesReader)

        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(null, inputs, mockOutputWriter, mockProgressCallback)
        }
        assertEquals("smilesColumn is not provided", exception.message)
    }

    @Test
    fun `test execute throws exception when molecules port is missing`() {
        val properties = mapOf(
            "smilesColumn" to "smiles",
            "querySmilesColumn" to "query_smiles"
        )
        val inputs = mapOf("queries" to mockQueriesReader)

        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)
        }
        assertEquals("Input port 'molecules' is not connected", exception.message)
    }

    @Test
    fun `test execute throws exception when queries port is missing`() {
        val properties = mapOf(
            "smilesColumn" to "smiles",
            "querySmilesColumn" to "query_smiles"
        )
        val inputs = mapOf("molecules" to mockMoleculesReader)

        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)
        }
        assertEquals("Input port 'queries' is not connected", exception.message)
    }

    // ===== Edge Cases =====

    @Test
    fun `test execute with empty molecules input produces empty outputs`() {
        val queryRows = listOf(
            mapOf("query_smiles" to "[OH]")
        )
        mockSequentialReads(mockQueriesReader, queryRows)
        mockSequentialReads(mockMoleculesReader, emptyList())

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "querySmilesColumn" to "query_smiles",
            "matchMode" to "any"
        )
        val inputs = mapOf("molecules" to mockMoleculesReader, "queries" to mockQueriesReader)

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockMatchWriter, times(0)).write(any(), any())
        verify(mockNoMatchWriter, times(0)).write(any(), any())
        verify(mockMatchWriter).close()
        verify(mockNoMatchWriter).close()
    }

    @Test
    fun `test execute with invalid molecule SMILES sends to noMatch`() {
        val queryRows = listOf(
            mapOf("query_smiles" to "[OH]")
        )
        val moleculeRows = listOf(
            mapOf("smiles" to "INVALID", "name" to "bad")
        )
        mockSequentialReads(mockQueriesReader, queryRows)
        mockSequentialReads(mockMoleculesReader, moleculeRows)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "querySmilesColumn" to "query_smiles",
            "matchMode" to "any"
        )
        val inputs = mapOf("molecules" to mockMoleculesReader, "queries" to mockQueriesReader)
        val noMatchCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockMatchWriter, times(0)).write(any(), any())
        verify(mockNoMatchWriter, times(1)).write(any(), noMatchCaptor.capture())
        assertEquals("INVALID", noMatchCaptor.allValues[0]["smiles"])
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
