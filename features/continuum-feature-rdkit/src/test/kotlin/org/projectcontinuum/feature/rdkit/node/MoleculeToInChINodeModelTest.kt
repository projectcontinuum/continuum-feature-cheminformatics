package org.projectcontinuum.feature.rdkit.node

import org.projectcontinuum.core.commons.exception.NodeRuntimeException
import org.projectcontinuum.core.commons.protocol.progress.NodeProgressCallback
import org.projectcontinuum.core.commons.utils.NodeInputReader
import org.projectcontinuum.core.commons.utils.NodeOutputWriter
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@ExtendWith(RDKitTestExtension::class)
class MoleculeToInChINodeModelTest {

    private lateinit var nodeModel: MoleculeToInChINodeModel
    private lateinit var mockInputReader: NodeInputReader
    private lateinit var mockOutputWriter: NodeOutputWriter
    private lateinit var mockPortWriter: NodeOutputWriter.OutputPortWriter
    private lateinit var mockProgressCallback: NodeProgressCallback

    @BeforeEach
    fun setUp() {
        nodeModel = MoleculeToInChINodeModel()
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
        assertEquals("org.projectcontinuum.feature.rdkit.node.MoleculeToInChINodeModel", metadata.id)
        assertEquals("Converts SMILES strings to InChI and InChI Key using RDKit", metadata.description)
        assertEquals("Molecule to InChI", metadata.title)
        assertEquals("Convert SMILES to InChI and InChI Key", metadata.subTitle)
        assertNotNull(metadata.icon)
        assertTrue(metadata.icon.toString().contains("svg"))
    }

    @Test
    fun `test categories are correctly defined`() {
        val categories = nodeModel.categories
        assertEquals(2, categories.size)
        assertEquals("RDKit", categories[0])
        assertEquals("Converters", categories[1])
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
    fun `test execute with valid SMILES produces InChI and InChI Key`() {
        val rows = listOf(
            mapOf("smiles" to "c1ccccc1", "name" to "benzene"),
            mapOf("smiles" to "CC(=O)O", "name" to "acetic acid")
        )
        mockSequentialReads(mockInputReader, rows)
        whenever(mockInputReader.getRowCount()).thenReturn(2)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "newInChIColumnName" to "inchi",
            "removeSourceColumn" to false,
            "generateInChIKey" to true,
            "inchiKeyColumnName" to "inchi_key"
        )
        val inputs = mapOf("input" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(2)).write(any(), rowCaptor.capture())
        assertEquals(2, rowCaptor.allValues.size)

        // Check InChI values are present and start with "InChI="
        val inchi0 = rowCaptor.allValues[0]["inchi"] as String
        assertTrue(inchi0.startsWith("InChI="))
        val inchi1 = rowCaptor.allValues[1]["inchi"] as String
        assertTrue(inchi1.startsWith("InChI="))

        // Check InChI Key values are present and non-empty
        val inchiKey0 = rowCaptor.allValues[0]["inchi_key"] as String
        assertTrue(inchiKey0.isNotEmpty())
        val inchiKey1 = rowCaptor.allValues[1]["inchi_key"] as String
        assertTrue(inchiKey1.isNotEmpty())

        // Check original columns are preserved
        assertEquals("c1ccccc1", rowCaptor.allValues[0]["smiles"])
        assertEquals("benzene", rowCaptor.allValues[0]["name"])
    }

    @Test
    fun `test execute with generateInChIKey false does not include InChI Key column`() {
        val rows = listOf(
            mapOf("smiles" to "c1ccccc1", "name" to "benzene")
        )
        mockSequentialReads(mockInputReader, rows)
        whenever(mockInputReader.getRowCount()).thenReturn(1)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "newInChIColumnName" to "inchi",
            "removeSourceColumn" to false,
            "generateInChIKey" to false,
            "inchiKeyColumnName" to "inchi_key"
        )
        val inputs = mapOf("input" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        val outputRow = rowCaptor.allValues[0]

        // InChI should be present
        assertTrue(outputRow.containsKey("inchi"))
        assertTrue((outputRow["inchi"] as String).startsWith("InChI="))

        // InChI Key column should NOT be present
        assertTrue(!outputRow.containsKey("inchi_key"))
    }

    @Test
    fun `test execute with invalid SMILES writes empty strings and does not crash`() {
        val rows = listOf(
            mapOf("smiles" to "INVALID_SMILES", "name" to "bad"),
            mapOf("smiles" to "c1ccccc1", "name" to "benzene")
        )
        mockSequentialReads(mockInputReader, rows)
        whenever(mockInputReader.getRowCount()).thenReturn(2)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "newInChIColumnName" to "inchi",
            "removeSourceColumn" to false,
            "generateInChIKey" to true,
            "inchiKeyColumnName" to "inchi_key"
        )
        val inputs = mapOf("input" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(2)).write(any(), rowCaptor.capture())

        // Invalid SMILES should produce empty strings
        assertEquals("", rowCaptor.allValues[0]["inchi"])
        assertEquals("", rowCaptor.allValues[0]["inchi_key"])
        // Original columns still preserved
        assertEquals("INVALID_SMILES", rowCaptor.allValues[0]["smiles"])
        assertEquals("bad", rowCaptor.allValues[0]["name"])

        // Valid SMILES should produce non-empty InChI
        assertTrue((rowCaptor.allValues[1]["inchi"] as String).isNotEmpty())
        assertTrue((rowCaptor.allValues[1]["inchi_key"] as String).isNotEmpty())
    }

    @Test
    fun `test execute with removeSourceColumn true removes the source column`() {
        val rows = listOf(
            mapOf("smiles" to "c1ccccc1", "name" to "benzene")
        )
        mockSequentialReads(mockInputReader, rows)
        whenever(mockInputReader.getRowCount()).thenReturn(1)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "newInChIColumnName" to "inchi",
            "removeSourceColumn" to true,
            "generateInChIKey" to true,
            "inchiKeyColumnName" to "inchi_key"
        )
        val inputs = mapOf("input" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        val outputRow = rowCaptor.allValues[0]

        // Source column should be removed
        assertTrue(!outputRow.containsKey("smiles"))
        // Other columns should be preserved
        assertEquals("benzene", outputRow["name"])
        // InChI columns should be present
        assertTrue(outputRow.containsKey("inchi"))
        assertTrue(outputRow.containsKey("inchi_key"))
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
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        // Default InChI column name is "inchi"
        assertTrue(rowCaptor.allValues[0].containsKey("inchi"))
        // Default generateInChIKey is true, so "inchi_key" column should be present
        assertTrue(rowCaptor.allValues[0].containsKey("inchi_key"))
        // Source column should NOT be removed by default
        assertTrue(rowCaptor.allValues[0].containsKey("smiles"))
    }

    // ===== Error Tests =====

    @Test
    fun `test execute throws exception when smilesColumn is missing`() {
        val properties = mapOf(
            "newInChIColumnName" to "inchi"
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
    fun `test execute with empty input`() {
        mockSequentialReads(mockInputReader, emptyList())
        whenever(mockInputReader.getRowCount()).thenReturn(0)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "newInChIColumnName" to "inchi",
            "removeSourceColumn" to false,
            "generateInChIKey" to true,
            "inchiKeyColumnName" to "inchi_key"
        )
        val inputs = mapOf("input" to mockInputReader)

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, never()).write(any(), any())
    }

    @Test
    fun `test execute with empty SMILES string writes empty InChI`() {
        val rows = listOf(
            mapOf("smiles" to "", "name" to "empty")
        )
        mockSequentialReads(mockInputReader, rows)
        whenever(mockInputReader.getRowCount()).thenReturn(1)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "newInChIColumnName" to "inchi",
            "removeSourceColumn" to false,
            "generateInChIKey" to true,
            "inchiKeyColumnName" to "inchi_key"
        )
        val inputs = mapOf("input" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        assertEquals("", rowCaptor.allValues[0]["inchi"])
        assertEquals("", rowCaptor.allValues[0]["inchi_key"])
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
