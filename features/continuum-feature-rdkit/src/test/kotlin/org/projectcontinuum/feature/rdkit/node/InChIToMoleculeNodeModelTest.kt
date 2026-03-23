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
class InChIToMoleculeNodeModelTest {

    private lateinit var nodeModel: InChIToMoleculeNodeModel
    private lateinit var mockInputReader: NodeInputReader
    private lateinit var mockOutputWriter: NodeOutputWriter
    private lateinit var mockPortWriter: NodeOutputWriter.OutputPortWriter
    private lateinit var mockProgressCallback: NodeProgressCallback

    @BeforeEach
    fun setUp() {
        nodeModel = InChIToMoleculeNodeModel()
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
        assertEquals("org.projectcontinuum.feature.rdkit.node.InChIToMoleculeNodeModel", metadata.id)
        assertEquals("Converts InChI strings to canonical SMILES using RDKit", metadata.description)
        assertEquals("InChI to Molecule", metadata.title)
        assertEquals("Convert InChI strings to SMILES", metadata.subTitle)
        assertNotNull(metadata.icon)
        assertTrue(metadata.icon.toString().contains("svg"))
    }

    @Test
    fun `test categories are correctly defined`() {
        val categories = nodeModel.categories
        assertEquals(1, categories.size)
        assertEquals("RDKit/Converters", categories[0])
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
    fun `test execute with valid InChI produces SMILES output`() {
        val rows = listOf(
            mapOf("inchi" to "InChI=1S/C6H6/c1-2-4-6-5-3-1/h1-6H", "name" to "benzene"),
            mapOf("inchi" to "InChI=1S/CH2O2/c2-1-3/h1H,(H,2,3)", "name" to "formic acid")
        )
        mockSequentialReads(mockInputReader, rows)
        whenever(mockInputReader.getRowCount()).thenReturn(2)

        val properties = mapOf(
            "inchiColumn" to "inchi",
            "newColumnName" to "smiles",
            "removeSourceColumn" to false,
            "sanitize" to true,
            "removeHydrogens" to true
        )
        val inputs = mapOf("input" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(2)).write(any(), rowCaptor.capture())
        assertEquals(2, rowCaptor.allValues.size)

        // Check SMILES values are present and non-empty
        assertNotNull(rowCaptor.allValues[0]["smiles"])
        assertTrue((rowCaptor.allValues[0]["smiles"] as String).isNotEmpty())
        assertNotNull(rowCaptor.allValues[1]["smiles"])
        assertTrue((rowCaptor.allValues[1]["smiles"] as String).isNotEmpty())

        // Check original columns are preserved
        assertEquals("InChI=1S/C6H6/c1-2-4-6-5-3-1/h1-6H", rowCaptor.allValues[0]["inchi"])
        assertEquals("benzene", rowCaptor.allValues[0]["name"])
    }

    @Test
    fun `test execute with invalid InChI writes empty string and does not crash`() {
        val rows = listOf(
            mapOf("inchi" to "INVALID_INCHI", "name" to "bad"),
            mapOf("inchi" to "InChI=1S/C6H6/c1-2-4-6-5-3-1/h1-6H", "name" to "benzene")
        )
        mockSequentialReads(mockInputReader, rows)
        whenever(mockInputReader.getRowCount()).thenReturn(2)

        val properties = mapOf(
            "inchiColumn" to "inchi",
            "newColumnName" to "smiles",
            "removeSourceColumn" to false,
            "sanitize" to true,
            "removeHydrogens" to true
        )
        val inputs = mapOf("input" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(2)).write(any(), rowCaptor.capture())

        // Invalid InChI should produce empty string
        assertEquals("", rowCaptor.allValues[0]["smiles"])
        // Original columns still preserved
        assertEquals("INVALID_INCHI", rowCaptor.allValues[0]["inchi"])
        assertEquals("bad", rowCaptor.allValues[0]["name"])

        // Valid InChI should produce non-empty SMILES
        assertNotNull(rowCaptor.allValues[1]["smiles"])
        assertTrue((rowCaptor.allValues[1]["smiles"] as String).isNotEmpty())
    }

    @Test
    fun `test execute with removeSourceColumn true removes the source column`() {
        val rows = listOf(
            mapOf("inchi" to "InChI=1S/C6H6/c1-2-4-6-5-3-1/h1-6H", "name" to "benzene")
        )
        mockSequentialReads(mockInputReader, rows)
        whenever(mockInputReader.getRowCount()).thenReturn(1)

        val properties = mapOf(
            "inchiColumn" to "inchi",
            "newColumnName" to "smiles",
            "removeSourceColumn" to true,
            "sanitize" to true,
            "removeHydrogens" to true
        )
        val inputs = mapOf("input" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        val outputRow = rowCaptor.allValues[0]

        // Source column should be removed
        assertTrue(!outputRow.containsKey("inchi"))
        // Other columns should be preserved
        assertEquals("benzene", outputRow["name"])
        // SMILES column should be present
        assertTrue(outputRow.containsKey("smiles"))
    }

    @Test
    fun `test execute uses default properties`() {
        val rows = listOf(
            mapOf("inchi" to "InChI=1S/C6H6/c1-2-4-6-5-3-1/h1-6H")
        )
        mockSequentialReads(mockInputReader, rows)
        whenever(mockInputReader.getRowCount()).thenReturn(1)

        val properties = mapOf(
            "inchiColumn" to "inchi"
        )
        val inputs = mapOf("input" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        // Default column name is "smiles"
        assertTrue(rowCaptor.allValues[0].containsKey("smiles"))
        // Source column should NOT be removed by default
        assertTrue(rowCaptor.allValues[0].containsKey("inchi"))
    }

    // ===== Error Tests =====

    @Test
    fun `test execute throws exception when inchiColumn is missing`() {
        val properties = mapOf(
            "newColumnName" to "smiles"
        )
        val inputs = mapOf("input" to mockInputReader)

        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)
        }
        assertEquals("inchiColumn is not provided", exception.message)
    }

    @Test
    fun `test execute throws exception when properties are null`() {
        val inputs = mapOf("input" to mockInputReader)

        val exception = assertThrows<NodeRuntimeException> {
            nodeModel.execute(null, inputs, mockOutputWriter, mockProgressCallback)
        }
        assertEquals("inchiColumn is not provided", exception.message)
    }

    @Test
    fun `test execute throws exception when input port is missing`() {
        val properties = mapOf(
            "inchiColumn" to "inchi"
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
            "inchiColumn" to "inchi",
            "newColumnName" to "smiles",
            "removeSourceColumn" to false,
            "sanitize" to true,
            "removeHydrogens" to true
        )
        val inputs = mapOf("input" to mockInputReader)

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, never()).write(any(), any())
    }

    @Test
    fun `test execute with empty InChI string writes empty SMILES`() {
        val rows = listOf(
            mapOf("inchi" to "", "name" to "empty")
        )
        mockSequentialReads(mockInputReader, rows)
        whenever(mockInputReader.getRowCount()).thenReturn(1)

        val properties = mapOf(
            "inchiColumn" to "inchi",
            "newColumnName" to "smiles",
            "removeSourceColumn" to false,
            "sanitize" to true,
            "removeHydrogens" to true
        )
        val inputs = mapOf("input" to mockInputReader)
        val rowCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockPortWriter, times(1)).write(any(), rowCaptor.capture())
        assertEquals("", rowCaptor.allValues[0]["smiles"])
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
