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
class Open3DAlignmentNodeModelTest {

    private lateinit var nodeModel: Open3DAlignmentNodeModel
    private lateinit var mockQueryReader: NodeInputReader
    private lateinit var mockReferenceReader: NodeInputReader
    private lateinit var mockOutputWriter: NodeOutputWriter
    private lateinit var mockPortWriter: NodeOutputWriter.OutputPortWriter
    private lateinit var mockProgressCallback: NodeProgressCallback

    @BeforeEach
    fun setUp() {
        nodeModel = Open3DAlignmentNodeModel()
        mockQueryReader = mock()
        mockReferenceReader = mock()
        mockOutputWriter = mock()
        mockPortWriter = mock()
        mockProgressCallback = mock()
        whenever(mockOutputWriter.createOutputPortWriter("output")).thenReturn(mockPortWriter)
    }

    // ===== Metadata Tests =====

    @Test
    fun `test node metadata is properly configured`() {
        val metadata = nodeModel.metadata
        assertEquals("org.projectcontinuum.feature.rdkit.node.Open3DAlignmentNodeModel", metadata.id)
        assertEquals("Aligns query molecules against reference molecules in 3D using RDKit", metadata.description)
        assertEquals("Open 3D Alignment", metadata.title)
        assertEquals("3D molecular alignment against reference structures", metadata.subTitle)
        assertNotNull(metadata.icon)
        assertTrue(metadata.icon.toString().contains("svg"))
    }

    @Test
    fun `test categories are correctly defined`() {
        val categories = nodeModel.categories
        assertEquals(1, categories.size)
        assertEquals("RDKit/Geometry", categories[0])
    }

    // ===== Port Tests =====

    @Test
    fun `test input ports have two ports query and reference`() {
        val inputPorts = nodeModel.inputPorts
        assertEquals(2, inputPorts.size)
        assertNotNull(inputPorts["query"])
        assertEquals("query table", inputPorts["query"]!!.name)
        assertNotNull(inputPorts["reference"])
        assertEquals("reference table", inputPorts["reference"]!!.name)
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

    // ===== Error Tests =====

    @Test
    fun `test execute throws exception when querySmilesColumn is missing`() {
        val properties = mapOf(
            "referenceSmilesColumn" to "ref_smiles"
        )
        val inputs = mapOf("query" to mockQueryReader, "reference" to mockReferenceReader)

        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)
        }
        assertEquals("querySmilesColumn is not provided", exception.message)
    }

    @Test
    fun `test execute throws exception when referenceSmilesColumn is missing`() {
        val properties = mapOf(
            "querySmilesColumn" to "smiles"
        )
        val inputs = mapOf("query" to mockQueryReader, "reference" to mockReferenceReader)

        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)
        }
        assertEquals("referenceSmilesColumn is not provided", exception.message)
    }

    @Test
    fun `test execute throws exception when properties are null`() {
        val inputs = mapOf("query" to mockQueryReader, "reference" to mockReferenceReader)

        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(null, inputs, mockOutputWriter, mockProgressCallback)
        }
        assertEquals("querySmilesColumn is not provided", exception.message)
    }

    @Test
    fun `test execute throws exception when query port is missing`() {
        val properties = mapOf(
            "querySmilesColumn" to "smiles",
            "referenceSmilesColumn" to "ref_smiles"
        )
        val inputs = mapOf("reference" to mockReferenceReader)

        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)
        }
        assertEquals("Input port 'query' is not connected", exception.message)
    }

    @Test
    fun `test execute throws exception when reference port is missing`() {
        val properties = mapOf(
            "querySmilesColumn" to "smiles",
            "referenceSmilesColumn" to "ref_smiles"
        )
        val inputs = mapOf("query" to mockQueryReader)

        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)
        }
        assertEquals("Input port 'reference' is not connected", exception.message)
    }

    // ===== Edge Cases =====

    @Test
    fun `test execute with empty reference produces empty alignment columns`() {
        mockSequentialReads(mockReferenceReader, emptyList())
        val queryRows = listOf(
            mapOf("smiles" to "c1ccccc1", "name" to "benzene")
        )
        mockSequentialReads(mockQueryReader, queryRows)

        val properties = mapOf(
            "querySmilesColumn" to "smiles",
            "referenceSmilesColumn" to "ref_smiles",
            "newAlignedColumnName" to "aligned_mol_block",
            "newRmsdColumnName" to "alignment_rmsd",
            "newScoreColumnName" to "alignment_score"
        )
        val inputs = mapOf("query" to mockQueryReader, "reference" to mockReferenceReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        val outputRow = rowCaptor.allValues[0]

        assertEquals("", outputRow["aligned_mol_block"])
        assertEquals("", outputRow["alignment_rmsd"])
        assertEquals("", outputRow["alignment_score"])
        assertEquals("c1ccccc1", outputRow["smiles"])
    }

    @Test
    fun `test execute with empty query input produces no output rows`() {
        val refRows = listOf(
            mapOf("ref_smiles" to "c1ccccc1")
        )
        mockSequentialReads(mockReferenceReader, refRows)
        mockSequentialReads(mockQueryReader, emptyList())

        val properties = mapOf(
            "querySmilesColumn" to "smiles",
            "referenceSmilesColumn" to "ref_smiles",
            "newAlignedColumnName" to "aligned_mol_block",
            "newRmsdColumnName" to "alignment_rmsd",
            "newScoreColumnName" to "alignment_score"
        )
        val inputs = mapOf("query" to mockQueryReader, "reference" to mockReferenceReader)

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(0)).write(any(), any())
    }

    @Test
    fun `test execute with invalid query SMILES produces empty alignment columns`() {
        val refRows = listOf(
            mapOf("ref_smiles" to "c1ccccc1")
        )
        val queryRows = listOf(
            mapOf("smiles" to "INVALID", "name" to "bad")
        )
        mockSequentialReads(mockReferenceReader, refRows)
        mockSequentialReads(mockQueryReader, queryRows)

        val properties = mapOf(
            "querySmilesColumn" to "smiles",
            "referenceSmilesColumn" to "ref_smiles",
            "newAlignedColumnName" to "aligned_mol_block",
            "newRmsdColumnName" to "alignment_rmsd",
            "newScoreColumnName" to "alignment_score"
        )
        val inputs = mapOf("query" to mockQueryReader, "reference" to mockReferenceReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        val outputRow = rowCaptor.allValues[0]

        assertEquals("", outputRow["aligned_mol_block"])
        assertEquals("", outputRow["alignment_rmsd"])
        assertEquals("", outputRow["alignment_score"])
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
