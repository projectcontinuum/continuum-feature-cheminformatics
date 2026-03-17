# Molecule to InChI Node

Convert SMILES strings to InChI (International Chemical Identifier) and optionally InChI Key representations using RDKit. This node is useful when you need standardized, unique molecular identifiers for database lookups, deduplication, or interoperability with other chemical databases.

---

## What It Does

The Molecule to InChI Node reads a SMILES string from each row, parses it into an RDKit molecule object, and generates an InChI string. When enabled, it also generates the corresponding InChI Key, which is a fixed-length hash suitable for database indexing and searching.

**Key Points:**
- Converts valid SMILES to InChI strings
- Optionally generates InChI Keys (enabled by default)
- Invalid SMILES produce empty strings — no error is thrown
- All original columns are preserved in the output
- Optionally removes the source SMILES column
- Thread-safe: InChI operations are synchronized with a lock
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
| **output** | Table with all original columns plus InChI and optionally InChI Key columns |

### Properties
| Property | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| **smilesColumn** | String | Yes | - | Name of the column containing SMILES strings |
| **newInChIColumnName** | String | No | "inchi" | Name for the new InChI column |
| **removeSourceColumn** | Boolean | No | false | Whether to remove the original SMILES column |
| **generateInChIKey** | Boolean | No | true | Whether to also generate InChI Keys |
| **inchiKeyColumnName** | String | No | "inchi_key" | Name for the InChI Key column |

---

## Example

**Input Table:**

| smiles | name |
|--------|------|
| c1ccccc1 | benzene |
| CC(=O)O | acetic acid |
| INVALID_SMILES | bad |

**Configuration:**
- **smilesColumn**: `smiles`
- **newInChIColumnName**: `inchi`
- **generateInChIKey**: `true`
- **inchiKeyColumnName**: `inchi_key`

**Output Table:**

| smiles | name | inchi | inchi_key |
|--------|------|-------|-----------|
| c1ccccc1 | benzene | InChI=1S/C6H6/c1-2-4-6-5-3-1/h1-6H | UHOVQNZJYSORNB-UHFFFAOYSA-N |
| CC(=O)O | acetic acid | InChI=1S/C2H4O2/c1-2(3)4/h1H3,(H,3,4) | QTBSBXVTEAMEQO-UHFFFAOYSA-N |
| INVALID_SMILES | bad | | |

---

## Tips & Warnings

- **Invalid SMILES**: Rows with unparseable SMILES will get empty strings in both the InChI and InChI Key columns. The node will not fail or skip the row.
- **InChI Key**: The InChI Key is a fixed-length 27-character hash of the InChI string. It is useful for database indexing but is not guaranteed to be unique for all molecules.
- **Thread Safety**: RDKit InChI operations are not thread-safe, so all conversions are synchronized using a lock. This may impact performance in parallel workflows.
- **Memory Safety**: Each RDKit molecule object is deleted in a `finally` block, preventing native memory leaks.
- **Remove Source Column**: Enable this to keep your table tidy when you no longer need the original SMILES representation.

---

## Technical Details

- **Algorithm**: Sequential row processing with streaming reader
- **Thread Safety**: Uses `synchronized(INCHI_LOCK)` for InChI operations
- **Memory**: Processes one row at a time (suitable for large datasets)
- **Progress**: Reports percentage based on rows processed vs total rows
- **Resource Management**: Uses `.use {}` blocks for automatic stream cleanup and `finally` for RDKit native object deletion
