# Add Hydrogens Node

Add explicit hydrogen atoms to molecules represented as SMILES strings using RDKit. This node is useful when downstream processing or visualization requires explicit hydrogens, for example before 3D coordinate generation or for force-field calculations.

---

## What It Does

The Add Hydrogens Node reads a SMILES string from each row, parses it into an RDKit molecule object, adds explicit hydrogen atoms, and re-serializes the result as a SMILES string containing all hydrogens.

**Key Points:**
- Adds explicit hydrogens to any valid molecule
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
| **output** | Table with all original columns plus the new SMILES-with-Hs column |

### Properties
| Property | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| **smilesColumn** | String | Yes | - | Name of the column containing SMILES strings |
| **newColumnName** | String | No | "smiles_with_hs" | Name for the new output column |
| **removeSourceColumn** | Boolean | No | false | Whether to remove the original SMILES column |

---

## Example

**Input Table:**

| smiles | name |
|--------|------|
| c1ccccc1 | benzene |
| C | methane |
| INVALID_SMILES | bad |

**Configuration:**
- **smilesColumn**: `smiles`
- **newColumnName**: `smiles_with_hs`
- **removeSourceColumn**: `false`

**Output Table:**

| smiles | name | smiles_with_hs |
|--------|------|----------------|
| c1ccccc1 | benzene | [cH]1[cH][cH][cH][cH][cH]1 |
| C | methane | [CH4] |
| INVALID_SMILES | bad | |

---

## Tips & Warnings

- **Invalid SMILES**: Rows with unparseable SMILES will get an empty string in the output column. The node will not fail or skip the row.
- **SMILES Length**: Adding explicit hydrogens significantly increases the length of SMILES strings. Benzene goes from `c1ccccc1` to a much longer representation.
- **Use Cases**: Explicit hydrogens are typically needed for 3D coordinate generation, force-field calculations, or when hydrogen positions are chemically relevant.
- **Memory Safety**: Each RDKit molecule object is deleted in a `finally` block, preventing native memory leaks.
- **Remove Source Column**: Enable this to keep your table tidy when you no longer need the original SMILES representation.

---

## Technical Details

- **Algorithm**: Sequential row processing with streaming reader
- **Memory**: Processes one row at a time (suitable for large datasets)
- **Progress**: Reports percentage based on rows processed vs total rows
- **Resource Management**: Uses `.use {}` blocks for automatic stream cleanup and `finally` for RDKit native object deletion
