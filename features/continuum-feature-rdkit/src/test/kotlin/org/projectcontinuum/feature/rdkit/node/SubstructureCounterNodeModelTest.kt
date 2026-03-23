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
class SubstructureCounterNodeModelTest {

    private lateinit var nodeModel: SubstructureCounterNodeModel
    private lateinit var mockMoleculesReader: NodeInputReader
    private lateinit var mockQueriesReader: NodeInputReader
    private lateinit var mockOutputWriter: NodeOutputWriter
    private lateinit var mockPortWriter: NodeOutputWriter.OutputPortWriter
    private lateinit var mockProgressCallback: NodeProgressCallback

    @BeforeEach
    fun setUp() {
        nodeModel = SubstructureCounterNodeModel()
        mockMoleculesReader = mock()
        mockQueriesReader = mock()
        mockOutputWriter = mock()
        mockPortWriter = mock()
        mockProgressCallback = mock()
        whenever(mockOutputWriter.createOutputPortWriter("output")).thenReturn(mockPortWriter)
    }

    // ===== Metadata Tests =====

    @Test
    fun `test node metadata is properly configured`() {
        val metadata = nodeModel.metadata
        assertEquals("org.projectcontinuum.feature.rdkit.node.SubstructureCounterNodeModel", metadata.id)
        assertEquals("Counts substructure matches for each molecule against a table of query patterns", metadata.description)
        assertEquals("Substructure Counter", metadata.title)
        assertEquals("Count substructure matches per query pattern", metadata.subTitle)
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
    fun `test execute counts hydroxyl in catechol as 2`() {
        val queryRows = listOf(
            mapOf("pattern" to "[OH]", "pattern_name" to "hydroxyl")
        )
        val moleculeRows = listOf(
            mapOf("smiles" to "Oc1cc(O)ccc1", "name" to "catechol")
        )
        mockSequentialReads(mockQueriesReader, queryRows)
        mockSequentialReads(mockMoleculesReader, moleculeRows)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "querySmilesColumn" to "pattern",
            "queryNameColumn" to "pattern_name",
            "uniqueMatchesOnly" to true,
            "addTotalHitsColumn" to true,
            "totalHitsColumnName" to "total_hits"
        )
        val inputs = mapOf("molecules" to mockMoleculesReader, "queries" to mockQueriesReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        val outputRow = rowCaptor.allValues[0]

        // Catechol has 2 hydroxyl groups
        assertEquals(2, outputRow["hydroxyl"])
        assertEquals(2, outputRow["total_hits"])
        // Original columns preserved
        assertEquals("Oc1cc(O)ccc1", outputRow["smiles"])
        assertEquals("catechol", outputRow["name"])
    }

    @Test
    fun `test execute with multiple queries creates multiple count columns`() {
        val queryRows = listOf(
            mapOf("pattern" to "[OH]", "pattern_name" to "hydroxyl"),
            mapOf("pattern" to "c1ccccc1", "pattern_name" to "aromatic_ring")
        )
        val moleculeRows = listOf(
            mapOf("smiles" to "c1ccc(O)cc1", "name" to "phenol"),
            mapOf("smiles" to "c1ccccc1", "name" to "benzene")
        )
        mockSequentialReads(mockQueriesReader, queryRows)
        mockSequentialReads(mockMoleculesReader, moleculeRows)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "querySmilesColumn" to "pattern",
            "queryNameColumn" to "pattern_name",
            "uniqueMatchesOnly" to true,
            "addTotalHitsColumn" to true,
            "totalHitsColumnName" to "total_hits"
        )
        val inputs = mapOf("molecules" to mockMoleculesReader, "queries" to mockQueriesReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(2)).write(any(), rowCaptor.capture())

        // Phenol: 1 hydroxyl, 1 aromatic ring
        val phenolRow = rowCaptor.allValues[0]
        assertEquals(1, phenolRow["hydroxyl"])
        assertEquals(1, phenolRow["aromatic_ring"])
        assertEquals(2, phenolRow["total_hits"])

        // Benzene: 0 hydroxyl, 1 aromatic ring
        val benzeneRow = rowCaptor.allValues[1]
        assertEquals(0, benzeneRow["hydroxyl"])
        assertEquals(1, benzeneRow["aromatic_ring"])
        assertEquals(1, benzeneRow["total_hits"])
    }

    @Test
    fun `test execute without total hits column omits it`() {
        val queryRows = listOf(
            mapOf("pattern" to "[OH]", "pattern_name" to "hydroxyl")
        )
        val moleculeRows = listOf(
            mapOf("smiles" to "c1ccc(O)cc1", "name" to "phenol")
        )
        mockSequentialReads(mockQueriesReader, queryRows)
        mockSequentialReads(mockMoleculesReader, moleculeRows)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "querySmilesColumn" to "pattern",
            "queryNameColumn" to "pattern_name",
            "uniqueMatchesOnly" to true,
            "addTotalHitsColumn" to false,
            "totalHitsColumnName" to "total_hits"
        )
        val inputs = mapOf("molecules" to mockMoleculesReader, "queries" to mockQueriesReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        val outputRow = rowCaptor.allValues[0]

        assertTrue(outputRow.containsKey("hydroxyl"))
        assertTrue(!outputRow.containsKey("total_hits"))
    }

    @Test
    fun `test execute with auto-generated query names`() {
        val queryRows = listOf(
            mapOf("pattern" to "[OH]")
        )
        val moleculeRows = listOf(
            mapOf("smiles" to "c1ccc(O)cc1", "name" to "phenol")
        )
        mockSequentialReads(mockQueriesReader, queryRows)
        mockSequentialReads(mockMoleculesReader, moleculeRows)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "querySmilesColumn" to "pattern",
            "queryNameColumn" to "",
            "uniqueMatchesOnly" to true,
            "addTotalHitsColumn" to true,
            "totalHitsColumnName" to "total_hits"
        )
        val inputs = mapOf("molecules" to mockMoleculesReader, "queries" to mockQueriesReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        val outputRow = rowCaptor.allValues[0]

        // Auto-generated name should be "query_0"
        assertTrue(outputRow.containsKey("query_0"))
        assertEquals(1, outputRow["query_0"])
    }

    // ===== Error Tests =====

    @Test
    fun `test execute throws exception when smilesColumn is missing`() {
        val properties = mapOf(
            "querySmilesColumn" to "pattern"
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
            "querySmilesColumn" to "pattern"
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
            "querySmilesColumn" to "pattern"
        )
        val inputs = mapOf("molecules" to mockMoleculesReader)

        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)
        }
        assertEquals("Input port 'queries' is not connected", exception.message)
    }

    // ===== Edge Cases =====

    @Test
    fun `test execute with invalid molecule SMILES produces zero counts`() {
        val queryRows = listOf(
            mapOf("pattern" to "[OH]", "pattern_name" to "hydroxyl")
        )
        val moleculeRows = listOf(
            mapOf("smiles" to "INVALID", "name" to "bad")
        )
        mockSequentialReads(mockQueriesReader, queryRows)
        mockSequentialReads(mockMoleculesReader, moleculeRows)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "querySmilesColumn" to "pattern",
            "queryNameColumn" to "pattern_name",
            "uniqueMatchesOnly" to true,
            "addTotalHitsColumn" to true,
            "totalHitsColumnName" to "total_hits"
        )
        val inputs = mapOf("molecules" to mockMoleculesReader, "queries" to mockQueriesReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        val outputRow = rowCaptor.allValues[0]

        assertEquals(0, outputRow["hydroxyl"])
        assertEquals(0, outputRow["total_hits"])
    }

    @Test
    fun `test execute with empty molecules input produces empty output`() {
        val queryRows = listOf(
            mapOf("pattern" to "[OH]", "pattern_name" to "hydroxyl")
        )
        mockSequentialReads(mockQueriesReader, queryRows)
        mockSequentialReads(mockMoleculesReader, emptyList())

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "querySmilesColumn" to "pattern",
            "queryNameColumn" to "pattern_name",
            "uniqueMatchesOnly" to true,
            "addTotalHitsColumn" to true,
            "totalHitsColumnName" to "total_hits"
        )
        val inputs = mapOf("molecules" to mockMoleculesReader, "queries" to mockQueriesReader)

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(0)).write(any(), any())
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
