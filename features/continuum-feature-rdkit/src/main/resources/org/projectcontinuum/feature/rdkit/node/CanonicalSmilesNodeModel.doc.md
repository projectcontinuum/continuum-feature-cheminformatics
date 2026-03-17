# Canonical SMILES Node

Convert SMILES strings to their canonical (standardized) form using RDKit. This is the simplest RDKit conversion node and is useful for normalizing molecule representations so that the same molecule always has the same SMILES string, regardless of how it was originally written.

---

## What It Does

The Canonical SMILES Node reads a SMILES string from each row, parses it into an RDKit molecule object, and re-serializes it as a canonical SMILES string. This ensures a unique, deterministic representation for each molecule.

**Key Points:**
- Converts any valid SMILES to its canonical (unique) form
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
| **output** | Table with all original columns plus the new canonical SMILES column |

### Properties
| Property | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| **smilesColumn** | String | Yes | - | Name of the column containing SMILES strings |
| **newColumnName** | String | No | "canonical_smiles" | Name for the new output column |
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
- **newColumnName**: `canonical_smiles`
- **removeSourceColumn**: `false`

**Output Table:**

| smiles | name | canonical_smiles |
|--------|------|------------------|
| OC(=O)c1ccccc1OC(C)=O | aspirin | CC(=O)Oc1ccccc1C(=O)O |
| c1ccccc1 | benzene | c1ccccc1 |
| INVALID_SMILES | bad | |

---

## Tips & Warnings

- **Invalid SMILES**: Rows with unparseable SMILES will get an empty string in the canonical column. The node will not fail or skip the row.
- **Canonicalization**: Two different SMILES for the same molecule (e.g., `C(O)=O` and `OC=O`) will produce the same canonical output.
- **Memory Safety**: Each RDKit molecule object is deleted in a `finally` block, preventing native memory leaks.
- **Remove Source Column**: Enable this to keep your table tidy when you no longer need the original SMILES representation.

---

## Technical Details

- **Algorithm**: Sequential row processing with streaming reader
- **Memory**: Processes one row at a time (suitable for large datasets)
- **Progress**: Reports percentage based on rows processed vs total rows
- **Resource Management**: Uses `.use {}` blocks for automatic stream cleanup and `finally` for RDKit native object deletion
