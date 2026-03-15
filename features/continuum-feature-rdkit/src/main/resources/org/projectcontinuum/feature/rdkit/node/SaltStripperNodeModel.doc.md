# Salt Stripper Node

Remove salt and counterion fragments from molecules represented as SMILES strings using RDKit. This node is essential for cleaning compound data that includes salts, keeping only the largest (most relevant) molecular fragment.

---

## What It Does

The Salt Stripper Node reads a SMILES string from each row, parses it into an RDKit molecule object, and when `keepLargestFragmentOnly` is enabled, splits the molecule into its disconnected fragments using `RDKFuncs.getMolFrags()`. It then identifies the largest fragment by heavy atom count and returns only that fragment as a SMILES string. Single-component molecules are simply canonicalized. Invalid SMILES produce an empty string rather than an error, so the node never crashes on malformed input.

**Key Points:**
- Strips salts and counterions by keeping the largest fragment
- Fragment size is determined by heavy atom count
- Invalid SMILES produce an empty string — no error is thrown
- All original columns are preserved in the output
- Optionally removes the source SMILES column
- All native RDKit molecule and fragment objects are properly cleaned up after each row

---

## Configuration

### Input Ports
| Port | Description |
|------|-------------|
| **input** | Input table containing a column with SMILES strings |

### Output Ports
| Port | Description |
|------|-------------|
| **output** | Table with all original columns plus the new stripped SMILES column |

### Properties
| Property | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| **smilesColumn** | String | Yes | - | Name of the column containing SMILES strings |
| **newColumnName** | String | No | "stripped_smiles" | Name for the new output column |
| **removeSourceColumn** | Boolean | No | false | Whether to remove the original SMILES column |
| **keepLargestFragmentOnly** | Boolean | No | true | Whether to keep only the largest fragment by heavy atom count |

---

## Example

**Input Table:**

| smiles | name |
|--------|------|
| CC(=O)[O-].[Na+] | sodium acetate |
| c1ccccc1 | benzene |
| INVALID_SMILES | bad |

**Configuration:**
- **smilesColumn**: `smiles`
- **newColumnName**: `stripped_smiles`
- **removeSourceColumn**: `false`
- **keepLargestFragmentOnly**: `true`

**Output Table:**

| smiles | name | stripped_smiles |
|--------|------|----------------|
| CC(=O)[O-].[Na+] | sodium acetate | CC(=O)[O-] |
| c1ccccc1 | benzene | c1ccccc1 |
| INVALID_SMILES | bad | |

---

## Tips & Warnings

- **Invalid SMILES**: Rows with unparseable SMILES will get an empty string in the output column. The node will not fail or skip the row.
- **Fragment Selection**: When multiple fragments have the same heavy atom count, the first one encountered is kept.
- **Single Component**: Molecules with only one fragment are simply canonicalized without fragmentation.
- **keepLargestFragmentOnly = false**: When disabled, the molecule is just canonicalized without any salt stripping.
- **Memory Safety**: All RDKit molecule objects and fragment vectors are deleted in `finally` blocks, preventing native memory leaks.
- **Remove Source Column**: Enable this to keep your table tidy when you no longer need the original SMILES representation.

---

## Technical Details

- **Algorithm**: Sequential row processing with streaming reader
- **Memory**: Processes one row at a time (suitable for large datasets)
- **Progress**: Reports percentage based on rows processed vs total rows
- **Resource Management**: Uses `.use {}` blocks for automatic stream cleanup and `finally` for RDKit native object deletion
