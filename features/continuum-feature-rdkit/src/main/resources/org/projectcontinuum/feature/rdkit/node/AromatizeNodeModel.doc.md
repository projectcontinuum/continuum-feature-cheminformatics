# Aromatize Node

Apply aromaticity perception to molecules represented as SMILES strings using RDKit. This node ensures that aromatic rings are properly detected and represented in the output SMILES, using RDKit's aromaticity model.

---

## What It Does

The Aromatize Node reads a SMILES string from each row, parses it into an RDKit molecule object, applies aromaticity perception via `RDKFuncs.setAromaticity()`, and re-serializes the result as a SMILES string. This ensures that aromatic rings use lowercase atom symbols (e.g., `c` instead of `C` for aromatic carbons).

**Key Points:**
- Applies RDKit aromaticity perception to valid SMILES
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
| **output** | Table with all original columns plus the new aromatic SMILES column |

### Properties
| Property | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| **smilesColumn** | String | Yes | - | Name of the column containing SMILES strings |
| **newColumnName** | String | No | "aromatic_smiles" | Name for the new output column |
| **removeSourceColumn** | Boolean | No | false | Whether to remove the original SMILES column |

---

## Example

**Input Table:**

| smiles | name |
|--------|------|
| C1=CC=CC=C1 | benzene (Kekule) |
| c1ccccc1 | benzene (aromatic) |
| INVALID_SMILES | bad |

**Configuration:**
- **smilesColumn**: `smiles`
- **newColumnName**: `aromatic_smiles`
- **removeSourceColumn**: `false`

**Output Table:**

| smiles | name | aromatic_smiles |
|--------|------|-----------------|
| C1=CC=CC=C1 | benzene (Kekule) | c1ccccc1 |
| c1ccccc1 | benzene (aromatic) | c1ccccc1 |
| INVALID_SMILES | bad | |

---

## Tips & Warnings

- **Invalid SMILES**: Rows with unparseable SMILES will get an empty string in the output column. The node will not fail or skip the row.
- **Aromaticity Model**: Uses RDKit's default aromaticity model which follows Hueckel's rule for ring systems.
- **Memory Safety**: Each RDKit molecule object is deleted in a `finally` block, preventing native memory leaks.
- **Remove Source Column**: Enable this to keep your table tidy when you no longer need the original SMILES representation.

---

## Technical Details

- **Algorithm**: Sequential row processing with streaming reader
- **Memory**: Processes one row at a time (suitable for large datasets)
- **Progress**: Reports percentage based on rows processed vs total rows
- **Resource Management**: Uses `.use {}` blocks for automatic stream cleanup and `finally` for RDKit native object deletion
