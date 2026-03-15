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
class SDFDifferenceCheckerNodeModelTest {

    private lateinit var nodeModel: SDFDifferenceCheckerNodeModel
    private lateinit var mockLeftReader: NodeInputReader
    private lateinit var mockRightReader: NodeInputReader
    private lateinit var mockOutputWriter: NodeOutputWriter
    private lateinit var mockPortWriter: NodeOutputWriter.OutputPortWriter
    private lateinit var mockProgressCallback: NodeProgressCallback

    @BeforeEach
    fun setUp() {
        nodeModel = SDFDifferenceCheckerNodeModel()
        mockLeftReader = mock()
        mockRightReader = mock()
        mockOutputWriter = mock()
        mockPortWriter = mock()
        mockProgressCallback = mock()
        whenever(mockOutputWriter.createOutputPortWriter("output")).thenReturn(mockPortWriter)
    }

    // ===== Metadata Tests =====

    @Test
    fun `test node metadata is properly configured`() {
        val metadata = nodeModel.metadata
        assertEquals("org.projectcontinuum.feature.rdkit.node.SDFDifferenceCheckerNodeModel", metadata.id)
        assertEquals("Compares two tables of molecules row by row, identifying SMILES, property, and coordinate differences", metadata.description)
        assertEquals("SDF Difference Checker", metadata.title)
        assertEquals("Compare two molecule tables for QA and regression testing", metadata.subTitle)
        assertNotNull(metadata.icon)
        assertTrue(metadata.icon.toString().contains("svg"))
    }

    @Test
    fun `test categories are correctly defined`() {
        val categories = nodeModel.categories
        assertEquals(2, categories.size)
        assertEquals("RDKit", categories[0])
        assertEquals("Testing", categories[1])
    }

    // ===== Port Tests =====

    @Test
    fun `test input ports have two ports left and right`() {
        val inputPorts = nodeModel.inputPorts
        assertEquals(2, inputPorts.size)
        assertNotNull(inputPorts["left"])
        assertEquals("left table", inputPorts["left"]!!.name)
        assertNotNull(inputPorts["right"])
        assertEquals("right table", inputPorts["right"]!!.name)
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
    fun `test execute with identical molecules reports match`() {
        val leftRows = listOf(
            mapOf("smiles" to "c1ccccc1", "name" to "benzene")
        )
        val rightRows = listOf(
            mapOf("smiles" to "c1ccccc1", "name" to "benzene")
        )
        mockSequentialReads(mockLeftReader, leftRows)
        mockSequentialReads(mockRightReader, rightRows)

        val properties = mapOf(
            "leftSmilesColumn" to "smiles",
            "rightSmilesColumn" to "smiles",
            "compareCoordinates" to false,
            "compareProperties" to true,
            "outputDifferencesOnly" to false,
            "differenceColumnName" to "differences",
            "matchColumnName" to "match_status"
        )
        val inputs = mapOf("left" to mockLeftReader, "right" to mockRightReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        val outputRow = rowCaptor.allValues[0]
        assertEquals("match", outputRow["match_status"])
        assertEquals("", outputRow["differences"])
    }

    @Test
    fun `test execute with different molecules reports mismatch`() {
        val leftRows = listOf(
            mapOf("smiles" to "c1ccccc1", "name" to "benzene")
        )
        val rightRows = listOf(
            mapOf("smiles" to "CCO", "name" to "ethanol")
        )
        mockSequentialReads(mockLeftReader, leftRows)
        mockSequentialReads(mockRightReader, rightRows)

        val properties = mapOf(
            "leftSmilesColumn" to "smiles",
            "rightSmilesColumn" to "smiles",
            "compareProperties" to true,
            "outputDifferencesOnly" to false,
            "differenceColumnName" to "differences",
            "matchColumnName" to "match_status"
        )
        val inputs = mapOf("left" to mockLeftReader, "right" to mockRightReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        val outputRow = rowCaptor.allValues[0]
        assertEquals("mismatch", outputRow["match_status"])
        val differences = outputRow["differences"] as String
        assertTrue(differences.contains("SMILES differ"))
    }

    @Test
    fun `test execute with left only row reports left_only`() {
        val leftRows = listOf(
            mapOf("smiles" to "c1ccccc1", "name" to "benzene"),
            mapOf("smiles" to "CCO", "name" to "ethanol")
        )
        val rightRows = listOf(
            mapOf("smiles" to "c1ccccc1", "name" to "benzene")
        )
        mockSequentialReads(mockLeftReader, leftRows)
        mockSequentialReads(mockRightReader, rightRows)

        val properties = mapOf(
            "leftSmilesColumn" to "smiles",
            "rightSmilesColumn" to "smiles",
            "compareProperties" to false,
            "outputDifferencesOnly" to false,
            "differenceColumnName" to "differences",
            "matchColumnName" to "match_status"
        )
        val inputs = mapOf("left" to mockLeftReader, "right" to mockRightReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(2)).write(any(), rowCaptor.capture())
        assertEquals("match", rowCaptor.allValues[0]["match_status"])
        assertEquals("left_only", rowCaptor.allValues[1]["match_status"])
    }

    @Test
    fun `test execute with outputDifferencesOnly true filters matches`() {
        val leftRows = listOf(
            mapOf("smiles" to "c1ccccc1", "name" to "benzene"),
            mapOf("smiles" to "CCO", "name" to "ethanol")
        )
        val rightRows = listOf(
            mapOf("smiles" to "c1ccccc1", "name" to "benzene"),
            mapOf("smiles" to "CCCO", "name" to "propanol")
        )
        mockSequentialReads(mockLeftReader, leftRows)
        mockSequentialReads(mockRightReader, rightRows)

        val properties = mapOf(
            "leftSmilesColumn" to "smiles",
            "rightSmilesColumn" to "smiles",
            "compareProperties" to false,
            "outputDifferencesOnly" to true,
            "differenceColumnName" to "differences",
            "matchColumnName" to "match_status"
        )
        val inputs = mapOf("left" to mockLeftReader, "right" to mockRightReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        // Only the mismatch row should be written
        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        assertEquals("mismatch", rowCaptor.allValues[0]["match_status"])
    }

    // ===== Error Tests =====

    @Test
    fun `test execute throws when leftSmilesColumn is missing`() {
        val properties = mapOf("rightSmilesColumn" to "smiles")
        val inputs = mapOf("left" to mockLeftReader, "right" to mockRightReader)

        assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)
        }
    }

    @Test
    fun `test execute throws when rightSmilesColumn is missing`() {
        val properties = mapOf("leftSmilesColumn" to "smiles")
        val inputs = mapOf("left" to mockLeftReader, "right" to mockRightReader)

        assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)
        }
    }

    @Test
    fun `test execute throws when left port is not connected`() {
        val properties = mapOf(
            "leftSmilesColumn" to "smiles",
            "rightSmilesColumn" to "smiles"
        )
        val inputs = mapOf("right" to mockRightReader)

        assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)
        }
    }

    @Test
    fun `test execute throws when right port is not connected`() {
        val properties = mapOf(
            "leftSmilesColumn" to "smiles",
            "rightSmilesColumn" to "smiles"
        )
        val inputs = mapOf("left" to mockLeftReader)

        assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)
        }
    }

    // ===== Resource Cleanup Tests =====

    @Test
    fun `test execute properly closes output writer`() {
        val leftRows = listOf(mapOf("smiles" to "C"))
        val rightRows = listOf(mapOf("smiles" to "C"))
        mockSequentialReads(mockLeftReader, leftRows)
        mockSequentialReads(mockRightReader, rightRows)

        val properties = mapOf(
            "leftSmilesColumn" to "smiles",
            "rightSmilesColumn" to "smiles",
            "outputDifferencesOnly" to false,
            "differenceColumnName" to "differences",
            "matchColumnName" to "match_status"
        )
        val inputs = mapOf("left" to mockLeftReader, "right" to mockRightReader)

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
