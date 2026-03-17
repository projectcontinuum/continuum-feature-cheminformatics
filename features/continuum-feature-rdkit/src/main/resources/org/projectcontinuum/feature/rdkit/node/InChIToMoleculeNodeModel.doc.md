# InChI to Molecule Node

Convert InChI (International Chemical Identifier) strings to canonical SMILES representations using RDKit. This node is useful when working with InChI-encoded molecular data and you need SMILES strings for downstream processing.

---

## What It Does

The InChI to Molecule Node reads an InChI string from each row, converts it to an RDKit molecule object using the RDKit InChI parser, and serializes it as a canonical SMILES string.

**Key Points:**
- Converts any valid InChI to canonical SMILES
- Invalid InChI strings produce an empty string — no error is thrown
- All original columns are preserved in the output
- Optionally removes the source InChI column
- Supports sanitization and hydrogen removal options
- Thread-safe: InChI operations are synchronized with a lock
- Native RDKit molecule objects are properly cleaned up after each row

---

## Configuration

### Input Ports
| Port | Description |
|------|-------------|
| **input** | Input table containing a column with InChI strings |

### Output Ports
| Port | Description |
|------|-------------|
| **output** | Table with all original columns plus the new SMILES column |

### Properties
| Property | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| **inchiColumn** | String | Yes | - | Name of the column containing InChI strings |
| **newColumnName** | String | No | "smiles" | Name for the new output column |
| **removeSourceColumn** | Boolean | No | false | Whether to remove the original InChI column |
| **sanitize** | Boolean | No | true | Sanitize molecules during conversion |
| **removeHydrogens** | Boolean | No | true | Remove explicit hydrogens after conversion |

---

## Example

**Input Table:**

| inchi | name |
|-------|------|
| InChI=1S/C6H6/c1-2-4-6-5-3-1/h1-6H | benzene |
| InChI=1S/CH2O2/c2-1-3/h1H,(H,2,3) | formic acid |
| INVALID_INCHI | bad |

**Configuration:**
- **inchiColumn**: `inchi`
- **newColumnName**: `smiles`
- **removeSourceColumn**: `false`

**Output Table:**

| inchi | name | smiles |
|-------|------|--------|
| InChI=1S/C6H6/c1-2-4-6-5-3-1/h1-6H | benzene | c1ccccc1 |
| InChI=1S/CH2O2/c2-1-3/h1H,(H,2,3) | formic acid | O=CO |
| INVALID_INCHI | bad | |

---

## Tips & Warnings

- **Invalid InChI**: Rows with unparseable InChI strings will get an empty string in the output column. The node will not fail or skip the row.
- **Thread Safety**: RDKit InChI operations are not thread-safe, so all conversions are synchronized using a lock. This may impact performance in parallel workflows.
- **Sanitization**: Enabled by default. Disable if you need to preserve non-standard valences or other unusual molecular features.
- **Hydrogen Removal**: Enabled by default. Disable if you need explicit hydrogen atoms in the output SMILES.
- **Memory Safety**: Each RDKit molecule object is deleted in a `finally` block, preventing native memory leaks.

---

## Technical Details

- **Algorithm**: Sequential row processing with streaming reader
- **Thread Safety**: Uses `synchronized(INCHI_LOCK)` for InChI operations
- **Memory**: Processes one row at a time (suitable for large datasets)
- **Progress**: Reports percentage based on rows processed vs total rows
- **Resource Management**: Uses `.use {}` blocks for automatic stream cleanup and `finally` for RDKit native object deletion
