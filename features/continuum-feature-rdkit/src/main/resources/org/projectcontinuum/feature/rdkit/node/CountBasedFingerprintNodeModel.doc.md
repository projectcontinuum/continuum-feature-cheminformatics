# Count-Based Fingerprint Node

Generate count-based molecular fingerprints from SMILES strings using RDKit. This node supports Morgan, AtomPair, and Torsion fingerprint types and outputs a JSON map of non-zero bit indices to their counts.

---

## What It Does

The Count-Based Fingerprint Node reads a SMILES string from each row, parses it into an RDKit molecule object, and computes a count-based fingerprint of the selected type. The fingerprint is serialized as a JSON map of non-zero `{index: count}` pairs, making it compact and suitable for downstream similarity or machine learning workflows.

**Key Points:**
- Supports three fingerprint types: Morgan, AtomPair, and Torsion
- Output is a JSON object mapping non-zero bit indices to their counts (e.g., `{"42":1, "128":1, "512":1}`)
- Invalid SMILES produce an empty string — no error is thrown
- All original columns are preserved in the output (unless removeSourceColumn is true)
- Configurable number of bits, radius (for Morgan), and chirality handling
- Native RDKit molecule and fingerprint objects are properly cleaned up after each row

---

## Configuration

### Input Ports
| Port | Description |
|------|-------------|
| **input** | Input table containing a column with SMILES strings |

### Output Ports
| Port | Description |
|------|-------------|
| **output** | Table with all original columns plus a new column containing the count-based fingerprint |

### Properties
| Property | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| **smilesColumn** | String | Yes | - | Name of the column containing SMILES strings |
| **fingerprintType** | String | No | "Morgan" | Type of fingerprint: Morgan, AtomPair, or Torsion |
| **numBits** | Integer | No | 2048 | Number of bits for the hashed fingerprint |
| **radius** | Integer | No | 2 | Radius for Morgan fingerprints (ignored for other types) |
| **useChirality** | Boolean | No | false | Whether to use chirality in fingerprint generation |
| **newColumnName** | String | No | "count_fingerprint" | Name for the new output column |
| **removeSourceColumn** | Boolean | No | false | Whether to remove the original SMILES column from the output |

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
- **fingerprintType**: `Morgan`
- **numBits**: `2048`
- **radius**: `2`
- **newColumnName**: `count_fingerprint`

**Output Table:**

| smiles | name | count_fingerprint |
|--------|------|-------------------|
| c1ccccc1 | benzene | {"80":1,"294":1,...} |
| CC(=O)O | acetic acid | {"155":1,"478":1,...} |
| INVALID_SMILES | bad | |

---

## Tips & Warnings

- **Invalid SMILES**: Rows with unparseable SMILES will get an empty string in the fingerprint column. The node will not fail or skip the row.
- **Fingerprint Type**: Morgan fingerprints (equivalent to ECFP) are the most commonly used for similarity searching and QSAR modeling. AtomPair and Torsion fingerprints capture different structural features.
- **Radius**: Only applies to Morgan fingerprints. Radius 2 is equivalent to ECFP4, radius 3 to ECFP6.
- **Count vs. Bit**: This node produces count-based fingerprints where each index maps to the number of times that feature was found. For bit-based (binary) fingerprints, use the Bit Fingerprint node.
- **Memory Safety**: Each RDKit molecule and fingerprint object is deleted in a `finally` block, preventing native memory leaks.

---

## Technical Details

- **Algorithm**: Sequential row processing with streaming reader; hashed fingerprint generation via RDKit
- **Memory**: Processes one row at a time (suitable for large datasets)
- **Progress**: Reports percentage based on rows processed vs total rows
- **Resource Management**: Uses `.use {}` blocks for automatic stream cleanup and `finally` for RDKit native object deletion
