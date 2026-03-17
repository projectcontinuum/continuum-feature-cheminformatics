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
class ChemicalTransformationNodeModelTest {

    private lateinit var nodeModel: ChemicalTransformationNodeModel
    private lateinit var mockMoleculesReader: NodeInputReader
    private lateinit var mockReactionsReader: NodeInputReader
    private lateinit var mockOutputWriter: NodeOutputWriter
    private lateinit var mockPortWriter: NodeOutputWriter.OutputPortWriter
    private lateinit var mockProgressCallback: NodeProgressCallback

    @BeforeEach
    fun setUp() {
        nodeModel = ChemicalTransformationNodeModel()
        mockMoleculesReader = mock()
        mockReactionsReader = mock()
        mockOutputWriter = mock()
        mockPortWriter = mock()
        mockProgressCallback = mock()
        whenever(mockOutputWriter.createOutputPortWriter("output")).thenReturn(mockPortWriter)
    }

    // ===== Metadata Tests =====

    @Test
    fun `test node metadata is properly configured`() {
        val metadata = nodeModel.metadata
        assertEquals("org.projectcontinuum.feature.rdkit.node.ChemicalTransformationNodeModel", metadata.id)
        assertEquals("Applies chemical transformations iteratively to molecules using RDKit reaction SMARTS", metadata.description)
        assertEquals("Chemical Transformation", metadata.title)
        assertEquals("Iteratively transform molecules using reaction SMARTS", metadata.subTitle)
        assertNotNull(metadata.icon)
        assertTrue(metadata.icon.toString().contains("svg"))
    }

    @Test
    fun `test categories are correctly defined`() {
        val categories = nodeModel.categories
        assertEquals(2, categories.size)
        assertEquals("RDKit", categories[0])
        assertEquals("Reactions", categories[1])
    }

    // ===== Port Tests =====

    @Test
    fun `test input ports have two ports molecules and reactions`() {
        val inputPorts = nodeModel.inputPorts
        assertEquals(2, inputPorts.size)
        assertNotNull(inputPorts["molecules"])
        assertEquals("molecules table", inputPorts["molecules"]!!.name)
        assertNotNull(inputPorts["reactions"])
        assertEquals("reactions table", inputPorts["reactions"]!!.name)
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
    fun `test execute transforms molecule using reaction SMARTS`() {
        // Use a simple, reliable reaction: replace Cl with F
        val reactionRows = listOf(
            mapOf("transform" to "[C:1][Cl:2]>>[C:1][F:2]")
        )
        val moleculeRows = listOf(
            mapOf("smiles" to "CCCl", "name" to "chloroethane")
        )
        mockSequentialReads(mockReactionsReader, reactionRows)
        mockSequentialReads(mockMoleculesReader, moleculeRows)
        whenever(mockMoleculesReader.getRowCount()).thenReturn(1)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "reactionSmartsColumn" to "transform",
            "newColumnName" to "transformed_smiles",
            "removeSourceColumn" to false,
            "maxReactionCycles" to 1
        )
        val inputs = mapOf("molecules" to mockMoleculesReader, "reactions" to mockReactionsReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        val outputRow = rowCaptor.allValues[0]

        // The transformed SMILES should contain F instead of Cl
        val transformed = outputRow["transformed_smiles"]?.toString() ?: ""
        assertTrue(transformed.isNotEmpty(), "Expected a non-empty transformed SMILES")
        assertTrue(transformed.contains("F") && !transformed.contains("Cl"),
            "Expected fluoroethane, got '$transformed'")

        // Original columns should be preserved
        assertEquals("chloroethane", outputRow["name"])
        assertEquals("CCCl", outputRow["smiles"])
    }

    @Test
    fun `test execute with no matching reaction passes molecule through unchanged`() {
        val reactionRows = listOf(
            mapOf("transform" to "[O-:1]>>[OH:1]")
        )
        val moleculeRows = listOf(
            mapOf("smiles" to "c1ccccc1", "name" to "benzene")
        )
        mockSequentialReads(mockReactionsReader, reactionRows)
        mockSequentialReads(mockMoleculesReader, moleculeRows)
        whenever(mockMoleculesReader.getRowCount()).thenReturn(1)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "reactionSmartsColumn" to "transform",
            "newColumnName" to "transformed_smiles",
            "maxReactionCycles" to 1
        )
        val inputs = mapOf("molecules" to mockMoleculesReader, "reactions" to mockReactionsReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        val outputRow = rowCaptor.allValues[0]

        // Benzene has no [O-], so should pass through unchanged
        assertEquals("c1ccccc1", outputRow["transformed_smiles"])
    }

    @Test
    fun `test execute with removeSourceColumn removes source column`() {
        val reactionRows = listOf(
            mapOf("transform" to "[O-:1]>>[OH:1]")
        )
        val moleculeRows = listOf(
            mapOf("smiles" to "CC(=O)[O-]", "name" to "acetate")
        )
        mockSequentialReads(mockReactionsReader, reactionRows)
        mockSequentialReads(mockMoleculesReader, moleculeRows)
        whenever(mockMoleculesReader.getRowCount()).thenReturn(1)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "reactionSmartsColumn" to "transform",
            "newColumnName" to "transformed_smiles",
            "removeSourceColumn" to true,
            "maxReactionCycles" to 1
        )
        val inputs = mapOf("molecules" to mockMoleculesReader, "reactions" to mockReactionsReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        val outputRow = rowCaptor.allValues[0]

        // Source column should be removed
        assertTrue(!outputRow.containsKey("smiles"))
        // Other columns should be preserved
        assertEquals("acetate", outputRow["name"])
        // Result column should be present
        assertTrue(outputRow.containsKey("transformed_smiles"))
    }

    @Test
    fun `test execute with no valid reactions passes molecules through unchanged`() {
        mockSequentialReads(mockReactionsReader, emptyList())
        val moleculeRows = listOf(
            mapOf("smiles" to "c1ccccc1", "name" to "benzene")
        )
        mockSequentialReads(mockMoleculesReader, moleculeRows)
        whenever(mockMoleculesReader.getRowCount()).thenReturn(1)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "reactionSmartsColumn" to "transform",
            "newColumnName" to "transformed_smiles"
        )
        val inputs = mapOf("molecules" to mockMoleculesReader, "reactions" to mockReactionsReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        val outputRow = rowCaptor.allValues[0]

        // With no reactions, the transformed SMILES should equal the original
        assertEquals("c1ccccc1", outputRow["transformed_smiles"])
    }

    @Test
    fun `test execute with empty molecules produces no output`() {
        val reactionRows = listOf(
            mapOf("transform" to "[O-:1]>>[OH:1]")
        )
        mockSequentialReads(mockReactionsReader, reactionRows)
        mockSequentialReads(mockMoleculesReader, emptyList())
        whenever(mockMoleculesReader.getRowCount()).thenReturn(0)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "reactionSmartsColumn" to "transform"
        )
        val inputs = mapOf("molecules" to mockMoleculesReader, "reactions" to mockReactionsReader)

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(0)).write(any(), any())
    }

    // ===== Error Tests =====

    @Test
    fun `test execute throws exception when smilesColumn is missing`() {
        val properties = mapOf(
            "reactionSmartsColumn" to "transform"
        )
        val inputs = mapOf("molecules" to mockMoleculesReader, "reactions" to mockReactionsReader)

        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)
        }
        assertEquals("smilesColumn is not provided", exception.message)
    }

    @Test
    fun `test execute throws exception when reactionSmartsColumn is missing`() {
        val properties = mapOf(
            "smilesColumn" to "smiles"
        )
        val inputs = mapOf("molecules" to mockMoleculesReader, "reactions" to mockReactionsReader)

        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)
        }
        assertEquals("reactionSmartsColumn is not provided", exception.message)
    }

    @Test
    fun `test execute throws exception when properties are null`() {
        val inputs = mapOf("molecules" to mockMoleculesReader, "reactions" to mockReactionsReader)

        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(null, inputs, mockOutputWriter, mockProgressCallback)
        }
        assertEquals("smilesColumn is not provided", exception.message)
    }

    @Test
    fun `test execute throws exception when molecules port is missing`() {
        val properties = mapOf(
            "smilesColumn" to "smiles",
            "reactionSmartsColumn" to "transform"
        )
        val inputs = mapOf("reactions" to mockReactionsReader)

        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)
        }
        assertEquals("Input port 'molecules' is not connected", exception.message)
    }

    @Test
    fun `test execute throws exception when reactions port is missing`() {
        val properties = mapOf(
            "smilesColumn" to "smiles",
            "reactionSmartsColumn" to "transform"
        )
        val inputs = mapOf("molecules" to mockMoleculesReader)

        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)
        }
        assertEquals("Input port 'reactions' is not connected", exception.message)
    }

    @Test
    fun `test execute throws exception when maxReactionCycles is out of range`() {
        val properties = mapOf(
            "smilesColumn" to "smiles",
            "reactionSmartsColumn" to "transform",
            "maxReactionCycles" to 0
        )
        val inputs = mapOf("molecules" to mockMoleculesReader, "reactions" to mockReactionsReader)

        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)
        }
        assertEquals("maxReactionCycles must be between 1 and 100", exception.message)
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
