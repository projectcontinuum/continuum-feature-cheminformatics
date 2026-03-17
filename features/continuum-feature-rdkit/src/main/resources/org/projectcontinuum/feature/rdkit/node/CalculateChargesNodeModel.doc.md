# Calculate Charges Node

Compute Gasteiger partial charges per atom for molecules represented as SMILES strings using RDKit. This node is useful for downstream electrostatic analysis, QSAR/QSPR modeling, or visualizing charge distributions across molecular structures.

---

## What It Does

The Calculate Charges Node reads a SMILES string from each row, parses it into an RDKit molecule object, computes Gasteiger partial charges for every atom, and writes the per-atom charges as a JSON array string into a new column.

**Key Points:**
- Computes Gasteiger partial charges for all atoms in a valid molecule
- Output is a JSON array of doubles, one charge per atom (e.g., `[-0.412, 0.206, 0.206]`)
- Invalid SMILES produce an empty string — no error is thrown
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
| **output** | Table with all original columns plus the new Gasteiger charges column |

### Properties
| Property | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| **smilesColumn** | String | Yes | - | Name of the column containing SMILES strings |
| **chargesColumnName** | String | No | "gasteiger_charges" | Name for the new output column |

---

## Example

**Input Table:**

| smiles | name |
|--------|------|
| O | water |
| CC | ethane |
| INVALID_SMILES | bad |

**Configuration:**
- **smilesColumn**: `smiles`
- **chargesColumnName**: `gasteiger_charges`

**Output Table:**

| smiles | name | gasteiger_charges |
|--------|------|-------------------|
| O | water | [-0.411, 0.2055, 0.2055] |
| CC | ethane | [-0.033, -0.033, ...] |
| INVALID_SMILES | bad | |

*(Exact numeric values depend on the RDKit Gasteiger implementation.)*

---

## Tips & Warnings

- **Invalid SMILES**: Rows with unparseable SMILES will get an empty string in the charges column. The node will not fail or skip the row.
- **JSON Array Format**: The output is a JSON array of doubles. Use a downstream JSON parser or array column converter if you need individual charge values.
- **Atom Order**: Charges appear in the same order as atoms in the RDKit molecule (canonical atom ordering after SMILES parsing).
- **Memory Safety**: Each RDKit molecule object is deleted in a `finally` block, preventing native memory leaks.
- **Implicit Hydrogens**: Gasteiger charges are computed only for heavy atoms by default (the atoms present in the SMILES). If you need charges on explicit hydrogens, use the Add Hydrogens node first.

---

## Technical Details

- **Algorithm**: Sequential row processing with streaming reader; Gasteiger charge computation via `RDKFuncs.computeGasteigerCharges`
- **Memory**: Processes one row at a time (suitable for large datasets)
- **Progress**: Reports percentage based on rows processed vs total rows
- **Resource Management**: Uses `.use {}` blocks for automatic stream cleanup and `finally` for RDKit native object deletion
