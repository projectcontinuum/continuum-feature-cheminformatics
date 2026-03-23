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
class OneComponentReactionNodeModelTest {

    private lateinit var nodeModel: OneComponentReactionNodeModel
    private lateinit var mockReactantsReader: NodeInputReader
    private lateinit var mockReactionReader: NodeInputReader
    private lateinit var mockOutputWriter: NodeOutputWriter
    private lateinit var mockPortWriter: NodeOutputWriter.OutputPortWriter
    private lateinit var mockProgressCallback: NodeProgressCallback

    @BeforeEach
    fun setUp() {
        nodeModel = OneComponentReactionNodeModel()
        mockReactantsReader = mock()
        mockReactionReader = mock()
        mockOutputWriter = mock()
        mockPortWriter = mock()
        mockProgressCallback = mock()
        whenever(mockOutputWriter.createOutputPortWriter("output")).thenReturn(mockPortWriter)
    }

    // ===== Metadata Tests =====

    @Test
    fun `test node metadata is properly configured`() {
        val metadata = nodeModel.metadata
        assertEquals("org.projectcontinuum.feature.rdkit.node.OneComponentReactionNodeModel", metadata.id)
        assertEquals("Runs one-component chemical reactions on reactant molecules using RDKit reaction SMARTS", metadata.description)
        assertEquals("One Component Reaction", metadata.title)
        assertEquals("Apply single-reactant reactions to molecules", metadata.subTitle)
        assertNotNull(metadata.icon)
        assertTrue(metadata.icon.toString().contains("svg"))
    }

    @Test
    fun `test categories are correctly defined`() {
        val categories = nodeModel.categories
        assertEquals(1, categories.size)
        assertEquals("RDKit/Reactions", categories[0])
    }

    // ===== Port Tests =====

    @Test
    fun `test input ports have two ports reactants and reaction`() {
        val inputPorts = nodeModel.inputPorts
        assertEquals(2, inputPorts.size)
        assertNotNull(inputPorts["reactants"])
        assertEquals("reactants table", inputPorts["reactants"]!!.name)
        assertNotNull(inputPorts["reaction"])
        assertEquals("reaction table", inputPorts["reaction"]!!.name)
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
    fun `test execute with simple identity reaction on benzene`() {
        val reactionRows = listOf(
            mapOf("reaction_smarts" to "[c:1]>>[c:1]O")
        )
        val reactantRows = listOf(
            mapOf("smiles" to "c1ccccc1", "name" to "benzene")
        )
        mockSequentialReads(mockReactionReader, reactionRows)
        mockSequentialReads(mockReactantsReader, reactantRows)

        val properties = mapOf(
            "reactantSmilesColumn" to "smiles",
            "reactionSmartsColumn" to "reaction_smarts",
            "productColumnName" to "product_smiles",
            "productIndexColumnName" to "product_index",
            "reactantIndexColumnName" to "reactant_index",
            "includeReactantColumns" to true
        )
        val inputs = mapOf("reactants" to mockReactantsReader, "reaction" to mockReactionReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, org.mockito.kotlin.atLeastOnce()).write(any(), rowCaptor.capture())
        val outputRows = rowCaptor.allValues

        // Should produce at least one product row
        assertTrue(outputRows.isNotEmpty(), "Expected at least one product row")

        // Each output row should contain product columns
        for (outputRow in outputRows) {
            assertTrue(outputRow.containsKey("product_smiles"))
            assertTrue(outputRow.containsKey("product_index"))
            assertTrue(outputRow.containsKey("reactant_index"))
            // Original reactant columns should be included
            assertEquals("benzene", outputRow["name"])
        }
    }

    @Test
    fun `test execute with empty reactants produces no output`() {
        val reactionRows = listOf(
            mapOf("reaction_smarts" to "[c:1]>>[c:1]O")
        )
        mockSequentialReads(mockReactionReader, reactionRows)
        mockSequentialReads(mockReactantsReader, emptyList())

        val properties = mapOf(
            "reactantSmilesColumn" to "smiles",
            "reactionSmartsColumn" to "reaction_smarts"
        )
        val inputs = mapOf("reactants" to mockReactantsReader, "reaction" to mockReactionReader)

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(0)).write(any(), any())
    }

    @Test
    fun `test execute with no valid reactions produces no output`() {
        mockSequentialReads(mockReactionReader, emptyList())
        val reactantRows = listOf(
            mapOf("smiles" to "c1ccccc1", "name" to "benzene")
        )
        mockSequentialReads(mockReactantsReader, reactantRows)

        val properties = mapOf(
            "reactantSmilesColumn" to "smiles",
            "reactionSmartsColumn" to "reaction_smarts"
        )
        val inputs = mapOf("reactants" to mockReactantsReader, "reaction" to mockReactionReader)

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(0)).write(any(), any())
    }

    // ===== Error Tests =====

    @Test
    fun `test execute throws exception when reactantSmilesColumn is missing`() {
        val properties = mapOf(
            "reactionSmartsColumn" to "reaction_smarts"
        )
        val inputs = mapOf("reactants" to mockReactantsReader, "reaction" to mockReactionReader)

        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)
        }
        assertEquals("reactantSmilesColumn is not provided", exception.message)
    }

    @Test
    fun `test execute throws exception when reactionSmartsColumn is missing`() {
        val properties = mapOf(
            "reactantSmilesColumn" to "smiles"
        )
        val inputs = mapOf("reactants" to mockReactantsReader, "reaction" to mockReactionReader)

        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)
        }
        assertEquals("reactionSmartsColumn is not provided", exception.message)
    }

    @Test
    fun `test execute throws exception when properties are null`() {
        val inputs = mapOf("reactants" to mockReactantsReader, "reaction" to mockReactionReader)

        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(null, inputs, mockOutputWriter, mockProgressCallback)
        }
        assertEquals("reactantSmilesColumn is not provided", exception.message)
    }

    @Test
    fun `test execute throws exception when reactants port is missing`() {
        val properties = mapOf(
            "reactantSmilesColumn" to "smiles",
            "reactionSmartsColumn" to "reaction_smarts"
        )
        val inputs = mapOf("reaction" to mockReactionReader)

        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)
        }
        assertEquals("Input port 'reactants' is not connected", exception.message)
    }

    @Test
    fun `test execute throws exception when reaction port is missing`() {
        val properties = mapOf(
            "reactantSmilesColumn" to "smiles",
            "reactionSmartsColumn" to "reaction_smarts"
        )
        val inputs = mapOf("reactants" to mockReactantsReader)

        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)
        }
        assertEquals("Input port 'reaction' is not connected", exception.message)
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
