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
class TwoComponentReactionNodeModelTest {

    private lateinit var nodeModel: TwoComponentReactionNodeModel
    private lateinit var mockReactant1Reader: NodeInputReader
    private lateinit var mockReactant2Reader: NodeInputReader
    private lateinit var mockReactionReader: NodeInputReader
    private lateinit var mockOutputWriter: NodeOutputWriter
    private lateinit var mockPortWriter: NodeOutputWriter.OutputPortWriter
    private lateinit var mockProgressCallback: NodeProgressCallback

    @BeforeEach
    fun setUp() {
        nodeModel = TwoComponentReactionNodeModel()
        mockReactant1Reader = mock()
        mockReactant2Reader = mock()
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
        assertEquals("org.projectcontinuum.feature.rdkit.node.TwoComponentReactionNodeModel", metadata.id)
        assertEquals("Runs two-component chemical reactions on pairs of reactant molecules using RDKit reaction SMARTS", metadata.description)
        assertEquals("Two Component Reaction", metadata.title)
        assertEquals("Apply two-reactant reactions to molecule pairs", metadata.subTitle)
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
    fun `test input ports have three ports reactant1 reactant2 and reaction`() {
        val inputPorts = nodeModel.inputPorts
        assertEquals(3, inputPorts.size)
        assertNotNull(inputPorts["reactant1"])
        assertEquals("reactant1 table", inputPorts["reactant1"]!!.name)
        assertNotNull(inputPorts["reactant2"])
        assertEquals("reactant2 table", inputPorts["reactant2"]!!.name)
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
    fun `test execute with empty inputs produces no output`() {
        mockSequentialReads(mockReactionReader, emptyList())
        mockSequentialReads(mockReactant2Reader, emptyList())
        mockSequentialReads(mockReactant1Reader, emptyList())

        val properties = mapOf(
            "reactant1SmilesColumn" to "amine",
            "reactant2SmilesColumn" to "acid",
            "reactionSmartsColumn" to "rxn_smarts"
        )
        val inputs = mapOf(
            "reactant1" to mockReactant1Reader,
            "reactant2" to mockReactant2Reader,
            "reaction" to mockReactionReader
        )

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(0)).write(any(), any())
    }

    // ===== Error Tests =====

    @Test
    fun `test execute throws exception when reactant1SmilesColumn is missing`() {
        val properties = mapOf(
            "reactant2SmilesColumn" to "acid",
            "reactionSmartsColumn" to "rxn_smarts"
        )
        val inputs = mapOf(
            "reactant1" to mockReactant1Reader,
            "reactant2" to mockReactant2Reader,
            "reaction" to mockReactionReader
        )

        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)
        }
        assertEquals("reactant1SmilesColumn is not provided", exception.message)
    }

    @Test
    fun `test execute throws exception when reactant2SmilesColumn is missing`() {
        val properties = mapOf(
            "reactant1SmilesColumn" to "amine",
            "reactionSmartsColumn" to "rxn_smarts"
        )
        val inputs = mapOf(
            "reactant1" to mockReactant1Reader,
            "reactant2" to mockReactant2Reader,
            "reaction" to mockReactionReader
        )

        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)
        }
        assertEquals("reactant2SmilesColumn is not provided", exception.message)
    }

    @Test
    fun `test execute throws exception when reactionSmartsColumn is missing`() {
        val properties = mapOf(
            "reactant1SmilesColumn" to "amine",
            "reactant2SmilesColumn" to "acid"
        )
        val inputs = mapOf(
            "reactant1" to mockReactant1Reader,
            "reactant2" to mockReactant2Reader,
            "reaction" to mockReactionReader
        )

        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)
        }
        assertEquals("reactionSmartsColumn is not provided", exception.message)
    }

    @Test
    fun `test execute throws exception when properties are null`() {
        val inputs = mapOf(
            "reactant1" to mockReactant1Reader,
            "reactant2" to mockReactant2Reader,
            "reaction" to mockReactionReader
        )

        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(null, inputs, mockOutputWriter, mockProgressCallback)
        }
        assertEquals("reactant1SmilesColumn is not provided", exception.message)
    }

    @Test
    fun `test execute throws exception when reactant1 port is missing`() {
        val properties = mapOf(
            "reactant1SmilesColumn" to "amine",
            "reactant2SmilesColumn" to "acid",
            "reactionSmartsColumn" to "rxn_smarts"
        )
        val inputs = mapOf(
            "reactant2" to mockReactant2Reader,
            "reaction" to mockReactionReader
        )

        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)
        }
        assertEquals("Input port 'reactant1' is not connected", exception.message)
    }

    @Test
    fun `test execute throws exception when reactant2 port is missing`() {
        val properties = mapOf(
            "reactant1SmilesColumn" to "amine",
            "reactant2SmilesColumn" to "acid",
            "reactionSmartsColumn" to "rxn_smarts"
        )
        val inputs = mapOf(
            "reactant1" to mockReactant1Reader,
            "reaction" to mockReactionReader
        )

        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)
        }
        assertEquals("Input port 'reactant2' is not connected", exception.message)
    }

    @Test
    fun `test execute throws exception when reaction port is missing`() {
        val properties = mapOf(
            "reactant1SmilesColumn" to "amine",
            "reactant2SmilesColumn" to "acid",
            "reactionSmartsColumn" to "rxn_smarts"
        )
        val inputs = mapOf(
            "reactant1" to mockReactant1Reader,
            "reactant2" to mockReactant2Reader
        )

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
