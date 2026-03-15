# Molecule Extractor Node

Split multi-component molecules into individual fragments, producing one output row per fragment. This node performs row expansion: a single input row with a multi-component SMILES (e.g., a salt like "CC(=O)[O-].[Na+]") produces multiple output rows, one for each disconnected component.

---

## What It Does

The Molecule Extractor Node reads a SMILES string from each row, parses it into an RDKit molecule object, and uses `RDKFuncs.getMolFrags()` to split the molecule into its disconnected fragments. Each fragment is converted back to a SMILES string and written as a separate output row, along with a fragment ID (zero-indexed). Single-component SMILES produce exactly one output row with fragment_id=0. Invalid SMILES produce a single row with an empty fragment string.

**Key Points:**
- Row expansion: one input row can produce multiple output rows
- Each output row contains all original columns plus fragment SMILES and fragment ID
- Fragment IDs are zero-indexed within each input molecule
- Single-component SMILES produce exactly one output row
- Invalid SMILES produce one row with an empty fragment string
- All native RDKit molecule and fragment objects are properly cleaned up

---

## Configuration

### Input Ports
| Port | Description |
|------|-------------|
| **input** | Input table containing a column with SMILES strings |

### Output Ports
| Port | Description |
|------|-------------|
| **output** | Table with all original columns plus fragment SMILES and fragment ID columns (may have more rows than input) |

### Properties
| Property | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| **smilesColumn** | String | Yes | - | Name of the column containing SMILES strings |
| **newColumnName** | String | No | "fragment_smiles" | Name for the new column containing fragment SMILES |
| **fragmentIdColumnName** | String | No | "fragment_id" | Name for the new column containing fragment IDs |
| **sanitizeFragments** | Boolean | No | true | Whether to sanitize fragments after extraction |

---

## Example

**Input Table:**

| smiles | name |
|--------|------|
| CC(=O)[O-].[Na+] | sodium acetate |
| c1ccccc1 | benzene |

**Configuration:**
- **smilesColumn**: `smiles`
- **newColumnName**: `fragment_smiles`
- **fragmentIdColumnName**: `fragment_id`

**Output Table:**

| smiles | name | fragment_smiles | fragment_id |
|--------|------|----------------|-------------|
| CC(=O)[O-].[Na+] | sodium acetate | CC(=O)[O-] | 0 |
| CC(=O)[O-].[Na+] | sodium acetate | [Na+] | 1 |
| c1ccccc1 | benzene | c1ccccc1 | 0 |

---

## Tips & Warnings

- **Row Expansion**: The output table may have significantly more rows than the input table. Each multi-component SMILES expands into as many rows as it has fragments.
- **Fragment IDs**: Fragment IDs are zero-indexed and reset for each input molecule. They represent the order in which RDKit returns the fragments.
- **Invalid SMILES**: Rows with unparseable SMILES will produce a single output row with an empty fragment SMILES and fragment_id=0.
- **Memory Safety**: All RDKit molecule objects, fragment vectors, and individual fragments are deleted in `finally` blocks, preventing native memory leaks.

---

## Technical Details

- **Algorithm**: Sequential row processing with streaming reader; each row may produce multiple output rows
- **Memory**: Processes one row at a time (suitable for large datasets)
- **Progress**: Reports percentage based on input rows processed vs total input rows
- **Resource Management**: Uses `.use {}` blocks for automatic stream cleanup and `finally` for RDKit native object deletion
