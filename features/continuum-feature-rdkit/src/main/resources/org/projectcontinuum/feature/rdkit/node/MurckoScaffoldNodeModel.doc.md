# Murcko Scaffold Node

Extract the Murcko scaffold (core ring structure) from molecules represented as SMILES strings using RDKit. This node is useful for identifying the core structural frameworks of molecules, which is a common step in medicinal chemistry and compound library analysis.

---

## What It Does

The Murcko Scaffold Node reads a SMILES string from each row, parses it into an RDKit molecule object, and computes the Murcko decomposition using `RDKFuncs.MurckoDecompose()`. The Murcko scaffold is the core ring system of a molecule with all side chains removed but linkers between rings preserved. Optionally, the scaffold can be further reduced to a generic framework where all atoms are replaced with carbon and all bonds become single bonds.

**Key Points:**
- Extracts the Murcko scaffold (core ring structure) from each molecule
- Molecules without rings (e.g., hexane) produce an empty scaffold string
- Optionally generates a generic framework (all carbons, single bonds)
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
| **output** | Table with all original columns plus the new Murcko scaffold SMILES column |

### Properties
| Property | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| **smilesColumn** | String | Yes | - | Name of the column containing SMILES strings |
| **newColumnName** | String | No | "scaffold" | Name for the new output column |
| **removeSourceColumn** | Boolean | No | false | Whether to remove the original SMILES column |
| **makeGenericFramework** | Boolean | No | false | Whether to make the scaffold generic (all carbons, single bonds) |

---

## Example

**Input Table:**

| smiles | name |
|--------|------|
| CC(=O)Oc1ccccc1C(=O)O | aspirin |
| CCCCCC | hexane |
| c1ccc2c(c1)cc1ccccc12 | fluorene |

**Configuration:**
- **smilesColumn**: `smiles`
- **newColumnName**: `scaffold`
- **removeSourceColumn**: `false`
- **makeGenericFramework**: `false`

**Output Table:**

| smiles | name | scaffold |
|--------|------|----------|
| CC(=O)Oc1ccccc1C(=O)O | aspirin | c1ccccc1 |
| CCCCCC | hexane | |
| c1ccc2c(c1)cc1ccccc12 | fluorene | c1ccc2c(c1)cc1ccccc12 |

---

## Tips & Warnings

- **No Rings**: Molecules without any ring systems (e.g., linear alkanes) will produce an empty scaffold string. The node will not fail or skip the row.
- **Generic Framework**: When `makeGenericFramework` is enabled, all atom types are replaced with carbon and all bond types become single bonds. This is useful for grouping molecules by their topological framework.
- **Invalid SMILES**: Rows with unparseable SMILES will get an empty string in the scaffold column. The node will not fail or skip the row.
- **Memory Safety**: Each RDKit molecule object and scaffold are deleted in `finally` blocks, preventing native memory leaks.
- **Remove Source Column**: Enable this to keep your table tidy when you no longer need the original SMILES representation.

---

## Technical Details

- **Algorithm**: Sequential row processing with streaming reader using Murcko decomposition
- **Memory**: Processes one row at a time (suitable for large datasets)
- **Progress**: Reports percentage based on rows processed vs total rows
- **Resource Management**: Uses `.use {}` blocks for automatic stream cleanup and `finally` for RDKit native object deletion
