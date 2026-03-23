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
class MCSNodeModelTest {

    private lateinit var nodeModel: MCSNodeModel
    private lateinit var mockInputReader: NodeInputReader
    private lateinit var mockOutputWriter: NodeOutputWriter
    private lateinit var mockPortWriter: NodeOutputWriter.OutputPortWriter
    private lateinit var mockProgressCallback: NodeProgressCallback

    @BeforeEach
    fun setUp() {
        nodeModel = MCSNodeModel()
        mockInputReader = mock()
        mockOutputWriter = mock()
        mockPortWriter = mock()
        mockProgressCallback = mock()
        whenever(mockOutputWriter.createOutputPortWriter("output")).thenReturn(mockPortWriter)
    }

    // ===== Metadata Tests =====

    @Test
    fun `test node metadata is properly configured`() {
        val metadata = nodeModel.metadata
        assertEquals("org.projectcontinuum.feature.rdkit.node.MCSNodeModel", metadata.id)
        assertEquals("Finds the maximum common substructure (MCS) among all input molecules", metadata.description)
        assertEquals("Maximum Common Substructure", metadata.title)
        assertEquals("Find the largest substructure common to all molecules", metadata.subTitle)
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
    fun `test execute with phenol aniline toluene finds benzene ring as MCS`() {
        val rows = listOf(
            mapOf("smiles" to "c1ccc(O)cc1", "name" to "phenol"),
            mapOf("smiles" to "c1ccc(N)cc1", "name" to "aniline"),
            mapOf("smiles" to "Cc1ccccc1", "name" to "toluene")
        )
        mockSequentialReads(mockInputReader, rows)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "threshold" to 1.0,
            "ringMatchesRingOnly" to true,
            "completeRingsOnly" to true,
            "timeout" to 60
        )
        val inputs = mapOf("input" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        val outputRow = rowCaptor.allValues[0]

        // MCS should be a benzene ring: 6 atoms, 6 bonds
        val mcsSmarts = outputRow["mcs_smarts"] as String
        val numAtoms = (outputRow["num_atoms"] as Number).toInt()
        val numBonds = (outputRow["num_bonds"] as Number).toInt()
        val numMolecules = (outputRow["num_molecules"] as Number).toInt()

        assertTrue(mcsSmarts.isNotEmpty(), "MCS SMARTS should not be empty")
        assertEquals(6, numAtoms, "MCS should have 6 atoms (benzene ring)")
        assertEquals(6, numBonds, "MCS should have 6 bonds (benzene ring)")
        assertEquals(3, numMolecules, "Should report 3 molecules")
    }

    @Test
    fun `test execute with single molecule returns its SMARTS`() {
        val rows = listOf(
            mapOf("smiles" to "c1ccccc1", "name" to "benzene")
        )
        mockSequentialReads(mockInputReader, rows)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "threshold" to 1.0,
            "ringMatchesRingOnly" to true,
            "completeRingsOnly" to true,
            "timeout" to 60
        )
        val inputs = mapOf("input" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        val outputRow = rowCaptor.allValues[0]

        val mcsSmarts = outputRow["mcs_smarts"] as String
        assertTrue(mcsSmarts.isNotEmpty(), "MCS SMARTS should not be empty for single molecule")
        assertEquals(1, outputRow["num_molecules"])
    }

    @Test
    fun `test execute with two identical molecules returns full molecule`() {
        val rows = listOf(
            mapOf("smiles" to "CCO", "name" to "ethanol1"),
            mapOf("smiles" to "CCO", "name" to "ethanol2")
        )
        mockSequentialReads(mockInputReader, rows)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "threshold" to 1.0,
            "ringMatchesRingOnly" to true,
            "completeRingsOnly" to true,
            "timeout" to 60
        )
        val inputs = mapOf("input" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        val outputRow = rowCaptor.allValues[0]

        val numAtoms = (outputRow["num_atoms"] as Number).toInt()
        val numBonds = (outputRow["num_bonds"] as Number).toInt()

        // Ethanol: C-C-O = 3 atoms, 2 bonds
        assertEquals(3, numAtoms, "MCS of identical ethanol should have 3 atoms")
        assertEquals(2, numBonds, "MCS of identical ethanol should have 2 bonds")
        assertEquals(2, outputRow["num_molecules"])
    }

    // ===== Error Tests =====

    @Test
    fun `test execute throws exception when smilesColumn is missing`() {
        val properties = mapOf(
            "threshold" to 1.0
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
            "smilesColumn" to "smiles"
        )
        val inputs = emptyMap<String, NodeInputReader>()

        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)
        }
        assertEquals("Input port 'input' is not connected", exception.message)
    }

    @Test
    fun `test execute throws exception when no valid molecules found`() {
        val rows = listOf(
            mapOf("smiles" to "INVALID", "name" to "bad")
        )
        mockSequentialReads(mockInputReader, rows)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "threshold" to 1.0,
            "ringMatchesRingOnly" to true,
            "completeRingsOnly" to true,
            "timeout" to 60
        )
        val inputs = mapOf("input" to mockInputReader)

        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)
        }
        assertEquals("No valid molecules found for MCS computation", exception.message)
    }

    @Test
    fun `test execute throws exception when all SMILES are empty`() {
        val rows = listOf(
            mapOf("smiles" to "", "name" to "empty")
        )
        mockSequentialReads(mockInputReader, rows)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "threshold" to 1.0,
            "ringMatchesRingOnly" to true,
            "completeRingsOnly" to true,
            "timeout" to 60
        )
        val inputs = mapOf("input" to mockInputReader)

        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)
        }
        assertEquals("No valid molecules found for MCS computation", exception.message)
    }

    // ===== Edge Cases =====

    @Test
    fun `test execute skips invalid SMILES and computes MCS from valid ones`() {
        val rows = listOf(
            mapOf("smiles" to "c1ccc(O)cc1", "name" to "phenol"),
            mapOf("smiles" to "INVALID", "name" to "bad"),
            mapOf("smiles" to "c1ccc(N)cc1", "name" to "aniline")
        )
        mockSequentialReads(mockInputReader, rows)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "threshold" to 1.0,
            "ringMatchesRingOnly" to true,
            "completeRingsOnly" to true,
            "timeout" to 60
        )
        val inputs = mapOf("input" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        val outputRow = rowCaptor.allValues[0]

        // Only 2 valid molecules (phenol, aniline)
        assertEquals(2, outputRow["num_molecules"])
        val numAtoms = (outputRow["num_atoms"] as Number).toInt()
        assertTrue(numAtoms >= 6, "MCS of phenol and aniline should have at least 6 atoms")
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
