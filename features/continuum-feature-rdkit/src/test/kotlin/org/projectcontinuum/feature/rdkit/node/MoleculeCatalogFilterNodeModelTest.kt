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
class MoleculeCatalogFilterNodeModelTest {

    private lateinit var nodeModel: MoleculeCatalogFilterNodeModel
    private lateinit var mockInputReader: NodeInputReader
    private lateinit var mockOutputWriter: NodeOutputWriter
    private lateinit var mockCleanWriter: NodeOutputWriter.OutputPortWriter
    private lateinit var mockFlaggedWriter: NodeOutputWriter.OutputPortWriter
    private lateinit var mockProgressCallback: NodeProgressCallback

    @BeforeEach
    fun setUp() {
        nodeModel = MoleculeCatalogFilterNodeModel()
        mockInputReader = mock()
        mockOutputWriter = mock()
        mockCleanWriter = mock()
        mockFlaggedWriter = mock()
        mockProgressCallback = mock()
        whenever(mockOutputWriter.createOutputPortWriter("clean")).thenReturn(mockCleanWriter)
        whenever(mockOutputWriter.createOutputPortWriter("flagged")).thenReturn(mockFlaggedWriter)
    }

    // ===== Metadata Tests =====

    @Test
    fun `test node metadata is properly configured`() {
        val metadata = nodeModel.metadata
        assertEquals("org.projectcontinuum.feature.rdkit.node.MoleculeCatalogFilterNodeModel", metadata.id)
        assertEquals("Filters molecules against structural alert catalogs (PAINS, BRENK, NIH, ZINC) using RDKit FilterCatalog", metadata.description)
        assertEquals("Molecule Catalog Filter", metadata.title)
        assertEquals("Flag molecules matching structural alert catalogs", metadata.subTitle)
        assertNotNull(metadata.icon)
        assertTrue(metadata.icon.toString().contains("svg"))
    }

    @Test
    fun `test categories are correctly defined`() {
        val categories = nodeModel.categories
        assertEquals(1, categories.size)
        assertEquals("RDKit/Experimental", categories[0])
    }

    // ===== Port Tests =====

    @Test
    fun `test input ports are correctly defined`() {
        val inputPorts = nodeModel.inputPorts
        assertEquals(1, inputPorts.size)
        assertNotNull(inputPorts["input"])
    }

    @Test
    fun `test output ports have two ports clean and flagged`() {
        val outputPorts = nodeModel.outputPorts
        assertEquals(2, outputPorts.size)
        assertNotNull(outputPorts["clean"])
        assertEquals("clean table", outputPorts["clean"]!!.name)
        assertNotNull(outputPorts["flagged"])
        assertEquals("flagged table", outputPorts["flagged"]!!.name)
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
    fun `test execute routes clean benzene to clean port`() {
        val rows = listOf(
            mapOf("smiles" to "c1ccccc1", "name" to "benzene")
        )
        mockSequentialReads(mockInputReader, rows)
        whenever(mockInputReader.getRowCount()).thenReturn(1)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "catalogs" to listOf("PAINS_A"),
            "addMatchDetailsColumn" to true,
            "matchDetailsColumnName" to "catalog_matches",
            "addMatchCountColumn" to true,
            "matchCountColumnName" to "catalog_match_count"
        )
        val inputs = mapOf("input" to mockInputReader)
        val cleanCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        // Benzene should be clean (no PAINS alerts)
        verify(mockCleanWriter, times(1)).write(any(), cleanCaptor.capture())
        val cleanRow = cleanCaptor.allValues[0]
        assertEquals(0, cleanRow["catalog_match_count"])
    }

    @Test
    fun `test execute with invalid SMILES routes to clean with zero matches`() {
        val rows = listOf(
            mapOf("smiles" to "INVALID_SMILES", "name" to "bad")
        )
        mockSequentialReads(mockInputReader, rows)
        whenever(mockInputReader.getRowCount()).thenReturn(1)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "catalogs" to listOf("PAINS_A"),
            "addMatchDetailsColumn" to true,
            "matchDetailsColumnName" to "catalog_matches",
            "addMatchCountColumn" to true,
            "matchCountColumnName" to "catalog_match_count"
        )
        val inputs = mapOf("input" to mockInputReader)
        val cleanCaptor = argumentCaptor<Map<String, Any>>()

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        // Invalid SMILES cannot match anything, so goes to clean
        verify(mockCleanWriter, times(1)).write(any(), cleanCaptor.capture())
    }

    @Test
    fun `test execute with empty SMILES routes to clean`() {
        val rows = listOf(
            mapOf("smiles" to "", "name" to "empty")
        )
        mockSequentialReads(mockInputReader, rows)
        whenever(mockInputReader.getRowCount()).thenReturn(1)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "catalogs" to listOf("PAINS_A"),
            "addMatchDetailsColumn" to false,
            "addMatchCountColumn" to false
        )
        val inputs = mapOf("input" to mockInputReader)

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockCleanWriter, times(1)).write(any(), any())
    }

    // ===== Error Tests =====

    @Test
    fun `test execute throws when smilesColumn is missing`() {
        val properties = mapOf("catalogs" to listOf("PAINS_A"))
        val inputs = mapOf("input" to mockInputReader)

        assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)
        }
    }

    @Test
    fun `test execute throws when catalogs is missing`() {
        val properties = mapOf("smilesColumn" to "smiles")
        val inputs = mapOf("input" to mockInputReader)

        assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)
        }
    }

    @Test
    fun `test execute throws when input port is not connected`() {
        val properties = mapOf(
            "smilesColumn" to "smiles",
            "catalogs" to listOf("PAINS_A")
        )
        val inputs = mapOf<String, NodeInputReader>()

        assertThrows<NodeRuntimeException> {
            nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)
        }
    }

    // ===== Resource Cleanup Tests =====

    @Test
    fun `test execute properly closes output writers`() {
        val rows = listOf(mapOf("smiles" to "C"))
        mockSequentialReads(mockInputReader, rows)
        whenever(mockInputReader.getRowCount()).thenReturn(1)

        val properties = mapOf(
            "smilesColumn" to "smiles",
            "catalogs" to listOf("PAINS_A"),
            "addMatchDetailsColumn" to true,
            "matchDetailsColumnName" to "catalog_matches",
            "addMatchCountColumn" to true,
            "matchCountColumnName" to "catalog_match_count"
        )
        val inputs = mapOf("input" to mockInputReader)

        nodeModel.execute(properties, inputs, mockOutputWriter, mockProgressCallback)

        verify(mockCleanWriter).close()
        verify(mockFlaggedWriter).close()
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

