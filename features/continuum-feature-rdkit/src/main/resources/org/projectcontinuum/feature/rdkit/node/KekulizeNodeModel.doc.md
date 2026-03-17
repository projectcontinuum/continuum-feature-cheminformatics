# Kekulize Node

Convert aromatic SMILES strings to their Kekule (explicit double-bond) form using RDKit. This node replaces aromatic notation with explicit alternating single and double bonds, which is required by some downstream tools and file formats.

---

## What It Does

The Kekulize Node reads a SMILES string from each row, parses it into an RDKit molecule object, creates a writable RWMol copy, applies Kekulize() to assign explicit bond orders, and re-serializes the result as a SMILES string. This converts aromatic representations like `c1ccccc1` to their Kekule form like `C1=CC=CC=C1`.

**Key Points:**
- Converts aromatic SMILES to explicit Kekule form
- Invalid SMILES produce an empty string — no error is thrown
- All original columns are preserved in the output
- Optionally removes the source SMILES column
- Uses RWMol (writable molecule) for the Kekulize operation
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
| **output** | Table with all original columns plus the new Kekule SMILES column |

### Properties
| Property | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| **smilesColumn** | String | Yes | - | Name of the column containing SMILES strings |
| **newColumnName** | String | No | "kekule_smiles" | Name for the new output column |
| **removeSourceColumn** | Boolean | No | false | Whether to remove the original SMILES column |

---

## Example

**Input Table:**

| smiles | name |
|--------|------|
| c1ccccc1 | benzene |
| c1ccncc1 | pyridine |
| INVALID_SMILES | bad |

**Configuration:**
- **smilesColumn**: `smiles`
- **newColumnName**: `kekule_smiles`
- **removeSourceColumn**: `false`

**Output Table:**

| smiles | name | kekule_smiles |
|--------|------|---------------|
| c1ccccc1 | benzene | C1=CC=CC=C1 |
| c1ccncc1 | pyridine | C1=CN=CC=C1 |
| INVALID_SMILES | bad | |

---

## Tips & Warnings

- **Invalid SMILES**: Rows with unparseable SMILES will get an empty string in the output column. The node will not fail or skip the row.
- **RWMol Requirement**: The Kekulize operation requires a writable molecule (RWMol), which is created as a copy of the parsed read-only molecule.
- **Memory Safety**: Both the read-only molecule and the writable copy are deleted in `finally` blocks, preventing native memory leaks.
- **Remove Source Column**: Enable this to keep your table tidy when you no longer need the original SMILES representation.

---

## Technical Details

- **Algorithm**: Sequential row processing with streaming reader
- **Memory**: Processes one row at a time (suitable for large datasets)
- **Progress**: Reports percentage based on rows processed vs total rows
- **Resource Management**: Uses `.use {}` blocks for automatic stream cleanup and `finally` for RDKit native object deletion
