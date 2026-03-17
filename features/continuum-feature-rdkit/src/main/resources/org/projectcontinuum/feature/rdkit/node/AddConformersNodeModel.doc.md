# Add Conformers Node

Generate multiple 3D conformers for molecules from SMILES strings using RDKit distance geometry. This is a row expansion node: each input molecule produces multiple output rows, one per conformer.

---

## What It Does

The Add Conformers Node reads a SMILES string from each row, adds explicit hydrogens, and uses the ETKDGv3 distance geometry algorithm to generate multiple 3D conformers. Each conformer is written as a separate output row containing the conformer MolBlock and a conformer ID. This means one input row produces N output rows, where N is the number of successfully generated conformers.

**Key Points:**
- Row expansion: one input row produces N output rows (one per conformer)
- Uses ETKDGv3 distance geometry for conformer generation
- Configurable number of conformers and random seed for reproducibility
- Each conformer gets its own MolBlock and conformer ID
- Invalid SMILES or failed embeddings produce a single row with empty values
- All original columns are preserved and duplicated across conformer rows
- Native RDKit molecule objects are properly cleaned up after each input row

---

## Configuration

### Input Ports
| Port | Description |
|------|-------------|
| **input** | Input table containing a column with SMILES strings |

### Output Ports
| Port | Description |
|------|-------------|
| **output** | Table with original columns plus conformer MolBlock and conformer ID (row expansion) |

### Properties
| Property | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| **smilesColumn** | String | Yes | - | Name of the column containing SMILES strings |
| **numberOfConformers** | Integer | No | 5 | Number of conformers to generate per molecule |
| **randomSeed** | Integer | No | 42 | Random seed for reproducible conformer generation |
| **newColumnName** | String | No | "conformer_mol_block" | Name for the conformer MolBlock column |
| **conformerIdColumnName** | String | No | "conformer_id" | Name for the conformer ID column |
| **removeSourceColumn** | Boolean | No | false | Whether to remove the original SMILES column |

---

## Example

**Input Table:**

| smiles | name |
|--------|------|
| CCCCCC | hexane |

**Configuration:**
- **smilesColumn**: `smiles`
- **numberOfConformers**: `3`

**Output Table:**

| smiles | name | conformer_mol_block | conformer_id |
|--------|------|---------------------|--------------|
| CCCCCC | hexane | (3D MolBlock conf 0) | 0 |
| CCCCCC | hexane | (3D MolBlock conf 1) | 1 |
| CCCCCC | hexane | (3D MolBlock conf 2) | 2 |

---

## Tips & Warnings

- **Row Expansion**: Be aware that the output table will have more rows than the input. If you generate 10 conformers for 1000 molecules, the output will have up to 10,000 rows.
- **Random Seed**: Use the same random seed for reproducible results across runs.
- **Embedding Failure**: Some molecules may generate fewer conformers than requested, or none at all. Molecules that fail embedding produce a single row with empty values.
- **Flexible Molecules**: Flexible molecules (many rotatable bonds) benefit from more conformers to adequately sample conformational space.
- **Memory**: All conformers for a single molecule are generated at once. Very large molecules with many requested conformers may use significant memory.
- **Hydrogens**: Explicit hydrogens are added automatically before conformer generation.

---

## Technical Details

- **Algorithm**: Per-row conformer generation using `EmbedMultipleConfs()`
- **3D Method**: `addHs()` then `EmbedMultipleConfs()` with configurable count
- **MolBlock Generation**: `MolToMolBlock(mol, true, confId)` for each conformer ID
- **Row Numbering**: Output row numbers are sequential across all conformers from all input rows
- **Memory**: Processes one input molecule at a time, but generates all conformers for that molecule simultaneously
- **Progress**: Reports percentage based on input rows processed vs total input rows
- **Resource Management**: Uses `.use {}` blocks and `finally` for cleanup of native objects
