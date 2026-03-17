# Molecule Writer Node

Convert SMILES strings to different molecule representation formats (SMILES, SDF, or SMARTS) using RDKit. This node is useful when you need to export or convert molecular data into a specific format for downstream processing or external tools.

---

## What It Does

The Molecule Writer Node reads a SMILES string from each row, parses it into an RDKit molecule object, and converts it to the selected output format. Supported formats are canonical SMILES, SDF (with 2D coordinates), and SMARTS.

**Key Points:**
- Converts valid SMILES to SMILES, SDF, or SMARTS format
- SDF output includes computed 2D coordinates
- Invalid SMILES produce an empty string — no error is thrown
- All original columns are preserved in the output
- Optionally removes the source SMILES column
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
| **output** | Table with all original columns plus the new converted column |

### Properties
| Property | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| **smilesColumn** | String | Yes | - | Name of the column containing SMILES strings |
| **outputFormat** | String | No | "SMILES" | Output format: SMILES, SDF, or SMARTS |
| **newColumnName** | String | No | "converted" | Name for the new output column |
| **removeSourceColumn** | Boolean | No | false | Whether to remove the original SMILES column |

---

## Example

**Input Table:**

| smiles | name |
|--------|------|
| OC(=O)c1ccccc1OC(C)=O | aspirin |
| c1ccccc1 | benzene |
| INVALID_SMILES | bad |

**Configuration:**
- **smilesColumn**: `smiles`
- **outputFormat**: `SMARTS`
- **newColumnName**: `converted`
- **removeSourceColumn**: `false`

**Output Table:**

| smiles | name | converted |
|--------|------|-----------|
| OC(=O)c1ccccc1OC(C)=O | aspirin | [#6](-[#8])...(SMARTS output) |
| c1ccccc1 | benzene | [#6]1:[#6]:[#6]:[#6]:[#6]:[#6]:1 |
| INVALID_SMILES | bad | |

---

## Tips & Warnings

- **Invalid SMILES**: Rows with unparseable SMILES will get an empty string in the output column. The node will not fail or skip the row.
- **SDF Format**: When using SDF output, the node computes 2D coordinates before generating the MolBlock. This adds a small overhead per molecule.
- **SMARTS Output**: SMARTS representations are useful for substructure queries but are generally more verbose than SMILES.
- **Memory Safety**: Each RDKit molecule object is deleted in a `finally` block, preventing native memory leaks.
- **Remove Source Column**: Enable this to keep your table tidy when you no longer need the original SMILES representation.

---

## Technical Details

- **Algorithm**: Sequential row processing with streaming reader
- **Memory**: Processes one row at a time (suitable for large datasets)
- **Progress**: Reports percentage based on rows processed vs total rows
- **Resource Management**: Uses `.use {}` blocks for automatic stream cleanup and `finally` for RDKit native object deletion
