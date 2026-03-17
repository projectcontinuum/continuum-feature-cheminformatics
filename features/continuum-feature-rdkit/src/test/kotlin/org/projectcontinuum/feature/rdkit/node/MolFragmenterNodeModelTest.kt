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
class MolFragmenterNodeModelTest {

    private lateinit var nodeModel: MolFragmenterNodeModel
    private lateinit var mockInputReader: NodeInputReader
    private lateinit var mockOutputWriter: NodeOutputWriter
    private lateinit var mockFragmentsWriter: NodeOutputWriter.OutputPortWriter
    private lateinit var mockMoleculesWriter: NodeOutputWriter.OutputPortWriter
    private lateinit var mockProgressCallback: NodeProgressCallback

    @BeforeEach
    fun setUp() {
        nodeModel = MolFragmenterNodeModel()
        mockInputReader = mock()
        mockOutputWriter = mock()
        mockFragmentsWriter = mock()
        mockMoleculesWriter = mock()
        mockProgressCallback = mock()
        whenever(mockOutputWriter.createOutputPortWriter("fragments")).thenReturn(mockFragmentsWriter)
        whenever(mockOutputWriter.createOutputPortWriter("molecules")).thenReturn(mockMoleculesWriter)
    }

    // ===== Metadata Tests =====

    @Test
    fun `test node metadata is properly configured`() {
        val metadata = nodeModel.metadata
        assertEquals("org.projectcontinuum.feature.rdkit.node.MolFragmenterNodeModel", metadata.id)
        assertEquals("Fragments molecules using Murcko decomposition and outputs scaffold plus side chains", metadata.description)
        assertEquals("Molecule Fragmenter", metadata.title)
        assertEquals("Fragment molecules into scaffold and side chains", metadata.subTitle)
        assertNotNull(metadata.icon)
        assertTrue(metadata.icon.toString().contains("svg"))
    }

    @Test
    fun `test categories are correctly defined`() {
        val categories = nodeModel.categories
        assertEquals(2, categories.size)
        assertEquals("RDKit", categories[0])
        assertEquals("Fragments", categories[1])
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
    fun `test output ports have two ports fragments and molecules`() {
        val outputPorts = nodeModel.outputPorts
        assertEquals(2, outputPorts.size)
        assertNotNull(outputPorts["fragments"])
        assertEquals("fragments table", outputPorts["fragments"]!!.name)
        assertNotNull(outputPorts["molecules"])
        assertEquals("molecules table", outputPorts["molecules"]!!.name)
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
    fun `test execute with benzene produces fragments and molecules output`() {
        val rows = listOf(
            mapOf("smiles" to "c1ccccc1", "name" to "benzene")
        )
        mockSequentialReads(mockInputReader, rows)
        whenever(mockInputReader.getRowCount()).thenReturn(1)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "minPathLength" to 1,
            "maxPathLength" to 3
        )
        val inputs = mapOf("input" to mockInputReader)
        val fragCaptor = argumentCaptor<Map<String, Any>>()
        val molCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        // Fragments output should have at least one fragment
        verify(mockFragmentsWriter, times(1)).write(any(), fragCaptor.capture())
        val fragRow = fragCaptor.allValues[0]
        assertNotNull(fragRow["fragment_index"])
        assertNotNull(fragRow["fragment_smiles"])
        assertNotNull(fragRow["parent_smiles"])

        // Molecules output should have one row with fragment_indices
        verify(mockMoleculesWriter, times(1)).write(any(), molCaptor.capture())
        val molRow = molCaptor.allValues[0]
        assertEquals("benzene", molRow["name"])
        assertNotNull(molRow["fragment_indices"])
        assertTrue((molRow["fragment_indices"] as String).startsWith("["))

        verify(mockFragmentsWriter).close()
        verify(mockMoleculesWriter).close()
    }

    @Test
    fun `test execute with multiple molecules produces deduplicated fragments`() {
        val rows = listOf(
            mapOf("smiles" to "CC(=O)Oc1ccccc1C(=O)O", "name" to "aspirin"),
            mapOf("smiles" to "c1ccccc1", "name" to "benzene")
        )
        mockSequentialReads(mockInputReader, rows)
        whenever(mockInputReader.getRowCount()).thenReturn(2)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "minPathLength" to 1,
            "maxPathLength" to 3
        )
        val inputs = mapOf("input" to mockInputReader)
        val fragCaptor = argumentCaptor<Map<String, Any>>()
        val molCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        // Should have fragments written
        verify(mockFragmentsWriter, org.mockito.kotlin.atLeastOnce()).write(any(), fragCaptor.capture())
        assertTrue(fragCaptor.allValues.isNotEmpty())

        // Should have 2 molecule rows
        verify(mockMoleculesWriter, times(2)).write(any(), molCaptor.capture())
        assertEquals("aspirin", molCaptor.allValues[0]["name"])
        assertEquals("benzene", molCaptor.allValues[1]["name"])

        // Both molecule rows should have fragment_indices
        assertNotNull(molCaptor.allValues[0]["fragment_indices"])
        assertNotNull(molCaptor.allValues[1]["fragment_indices"])
    }

    @Test
    fun `test execute with molecule without rings treats whole molecule as fragment`() {
        val rows = listOf(
            mapOf("smiles" to "CCCCCC", "name" to "hexane")
        )
        mockSequentialReads(mockInputReader, rows)
        whenever(mockInputReader.getRowCount()).thenReturn(1)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "minPathLength" to 1,
            "maxPathLength" to 3
        )
        val inputs = mapOf("input" to mockInputReader)
        val fragCaptor = argumentCaptor<Map<String, Any>>()
        val molCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        // Should still have a fragment (the whole molecule)
        verify(mockFragmentsWriter, times(1)).write(any(), fragCaptor.capture())
        val fragSmiles = fragCaptor.allValues[0]["fragment_smiles"] as String
        assertTrue(fragSmiles.isNotEmpty())

        // Molecule row should reference the fragment
        verify(mockMoleculesWriter, times(1)).write(any(), molCaptor.capture())
        assertNotNull(molCaptor.allValues[0]["fragment_indices"])
    }

    @Test
    fun `test execute uses default properties`() {
        val rows = listOf(
            mapOf("smiles" to "c1ccccc1")
        )
        mockSequentialReads(mockInputReader, rows)
        whenever(mockInputReader.getRowCount()).thenReturn(1)

        val properties = mapOf(
            "smilesColumn" to "smiles"
        )
        val inputs = mapOf("input" to mockInputReader)

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        // Should complete without error using defaults
        verify(mockFragmentsWriter).close()
        verify(mockMoleculesWriter).close()
    }

    // ===== Error Tests =====

    @Test
    fun `test execute throws exception when smilesColumn is missing`() {
        val properties = mapOf(
            "minPathLength" to 1
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

    // ===== Edge Cases =====

    @Test
    fun `test execute with empty input produces empty outputs`() {
        mockSequentialReads(mockInputReader, emptyList())
        whenever(mockInputReader.getRowCount()).thenReturn(0)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "minPathLength" to 1,
            "maxPathLength" to 3
        )
        val inputs = mapOf("input" to mockInputReader)

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockFragmentsWriter, times(0)).write(any(), any())
        verify(mockMoleculesWriter, times(0)).write(any(), any())
        verify(mockFragmentsWriter).close()
        verify(mockMoleculesWriter).close()
    }

    @Test
    fun `test execute with invalid SMILES produces molecule row with empty fragment indices`() {
        val rows = listOf(
            mapOf("smiles" to "INVALID", "name" to "bad")
        )
        mockSequentialReads(mockInputReader, rows)
        whenever(mockInputReader.getRowCount()).thenReturn(1)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "minPathLength" to 1,
            "maxPathLength" to 3
        )
        val inputs = mapOf("input" to mockInputReader)
        val molCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        // Molecule row should still be written with empty fragment indices
        verify(mockMoleculesWriter, times(1)).write(any(), molCaptor.capture())
        assertEquals("bad", molCaptor.allValues[0]["name"])
        assertEquals("[]", molCaptor.allValues[0]["fragment_indices"])
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
