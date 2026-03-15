# Remove Hydrogens Node

Remove explicit hydrogens from molecules represented as SMILES strings using RDKit. This node is useful for cleaning up SMILES that contain explicit hydrogen atoms, producing a more compact representation.

---

## What It Does

The Remove Hydrogens Node reads a SMILES string from each row, parses it into an RDKit molecule object, removes explicit hydrogens via `RDKFuncs.removeHs()`, and re-serializes the result as a SMILES string. This produces a cleaner SMILES without unnecessary explicit Hs.

**Key Points:**
- Removes explicit hydrogens from valid SMILES
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
| **output** | Table with all original columns plus the new SMILES column without explicit Hs |

### Properties
| Property | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| **smilesColumn** | String | Yes | - | Name of the column containing SMILES strings |
| **newColumnName** | String | No | "smiles_no_hs" | Name for the new output column |
| **removeSourceColumn** | Boolean | No | false | Whether to remove the original SMILES column |

---

## Example

**Input Table:**

| smiles | name |
|--------|------|
| [H]OC(=O)c1ccccc1 | benzoic acid with Hs |
| c1ccccc1 | benzene |
| INVALID_SMILES | bad |

**Configuration:**
- **smilesColumn**: `smiles`
- **newColumnName**: `smiles_no_hs`
- **removeSourceColumn**: `false`

**Output Table:**

| smiles | name | smiles_no_hs |
|--------|------|--------------|
| [H]OC(=O)c1ccccc1 | benzoic acid with Hs | OC(=O)c1ccccc1 |
| c1ccccc1 | benzene | c1ccccc1 |
| INVALID_SMILES | bad | |

---

## Tips & Warnings

- **Invalid SMILES**: Rows with unparseable SMILES will get an empty string in the output column. The node will not fail or skip the row.
- **Implicit vs Explicit**: This node removes only explicit hydrogens. Implicit hydrogens are not affected.
- **Memory Safety**: Each RDKit molecule object is deleted in a `finally` block, preventing native memory leaks.
- **Remove Source Column**: Enable this to keep your table tidy when you no longer need the original SMILES representation.

---

## Technical Details

- **Algorithm**: Sequential row processing with streaming reader
- **Memory**: Processes one row at a time (suitable for large datasets)
- **Progress**: Reports percentage based on rows processed vs total rows
- **Resource Management**: Uses `.use {}` blocks for automatic stream cleanup and `finally` for RDKit native object deletion
