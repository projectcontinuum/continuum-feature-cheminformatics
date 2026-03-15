# Add Coordinates Node

Generate 2D or 3D coordinates for molecules from SMILES strings using RDKit. This node converts flat SMILES representations into spatial molecular structures with proper atom coordinates.

---

## What It Does

The Add Coordinates Node reads a SMILES string from each row, parses it into an RDKit molecule object, and generates coordinates in the requested dimension (2D or 3D). In 2D mode, coordinates are computed directly on the molecule using RDKit's coordinate generation. In 3D mode, explicit hydrogens are added and a distance geometry embedding is performed using the ETKDGv3 algorithm. The resulting MolBlock is written to a new column.

**Key Points:**
- Supports both 2D and 3D coordinate generation
- 2D mode uses `compute2DCoords()` for fast layout generation
- 3D mode uses ETKDGv3 distance geometry embedding for realistic conformations
- 3D mode automatically adds explicit hydrogens before embedding
- Invalid SMILES or failed embeddings produce empty strings — no error is thrown
- All original columns are preserved in the output
- Native RDKit molecule objects are properly cleaned up after each row

---

## Configuration

### Input Ports
| Port | Description |
|------|-------------|
| **input** | Input table containing a column with SMILES strings |

### Output Ports
| Port | Description |
|------|-------------|
| **output** | Table with all original columns plus a new column containing the MolBlock |

### Properties
| Property | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| **smilesColumn** | String | Yes | - | Name of the column containing SMILES strings |
| **dimension** | String | No | "2D" | Dimension for coordinate generation ("2D" or "3D") |
| **newColumnName** | String | No | "mol_block" | Name for the new MolBlock column |
| **removeSourceColumn** | Boolean | No | false | Whether to remove the original SMILES column |

---

## Example

**Input Table:**

| smiles | name |
|--------|------|
| c1ccccc1 | benzene |
| CCO | ethanol |
| INVALID | bad |

**Configuration:**
- **smilesColumn**: `smiles`
- **dimension**: `2D`
- **newColumnName**: `mol_block`

**Output Table:**

| smiles | name | mol_block |
|--------|------|-----------|
| c1ccccc1 | benzene | (2D MolBlock) |
| CCO | ethanol | (2D MolBlock) |
| INVALID | bad | |

---

## Tips & Warnings

- **2D vs 3D**: Use 2D coordinates for depiction and visualization. Use 3D coordinates for geometry optimization, conformer analysis, or shape-based comparisons.
- **3D Embedding Failure**: Some molecules may fail 3D embedding (e.g., very large or strained molecules). These produce an empty string in the output column.
- **Hydrogens in 3D**: In 3D mode, explicit hydrogens are added automatically before embedding. The resulting MolBlock will contain hydrogen atoms.
- **Invalid SMILES**: Rows with unparseable SMILES produce empty strings. The node does not fail on malformed input.
- **Memory Safety**: Each RDKit molecule object is deleted in a `finally` block, preventing native memory leaks.

---

## Technical Details

- **Algorithm**: Sequential row processing with streaming reader
- **2D Method**: `mol.compute2DCoords()` followed by `MolToMolBlock()`
- **3D Method**: `addHs()` followed by `EmbedMolecule()` with ETKDGv3 parameters
- **Memory**: Processes one row at a time (suitable for large datasets)
- **Progress**: Reports percentage based on rows processed vs total rows
- **Resource Management**: Uses `.use {}` blocks for automatic stream cleanup and `finally` for RDKit native object deletion
