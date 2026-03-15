# Fingerprint Similarity Node

Compute pairwise molecular similarity between two SMILES columns using RDKit fingerprints. This node supports Morgan, RDKit, and MACCS fingerprint types with Tanimoto or Dice similarity metrics.

---

## What It Does

The Fingerprint Similarity Node reads two SMILES strings from each row (one from each configured column), parses them into RDKit molecule objects, generates fingerprints of the selected type, and computes their similarity using the chosen metric. The resulting similarity score (a value between 0.0 and 1.0) is written to a new column.

**Key Points:**
- Supports three fingerprint types: Morgan, RDKit, and MACCS
- Two similarity metrics: Tanimoto (default) and Dice
- Output is a numeric similarity score between 0.0 (completely dissimilar) and 1.0 (identical)
- Invalid SMILES in either column produce an empty string — no error is thrown
- All original columns are preserved in the output (unless removeSourceColumns is true)
- Native RDKit molecule and fingerprint objects are properly cleaned up after each row

---

## Configuration

### Input Ports
| Port | Description |
|------|-------------|
| **input** | Input table containing two columns with SMILES strings |

### Output Ports
| Port | Description |
|------|-------------|
| **output** | Table with all original columns plus a new column containing the similarity score |

### Properties
| Property | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| **smilesColumn1** | String | Yes | - | Name of the first SMILES column |
| **smilesColumn2** | String | Yes | - | Name of the second SMILES column |
| **fingerprintType** | String | No | "Morgan" | Type of fingerprint: Morgan, RDKit, or MACCS |
| **similarityMetric** | String | No | "Tanimoto" | Similarity metric: Tanimoto or Dice |
| **numBits** | Integer | No | 2048 | Number of bits for Morgan/RDKit fingerprints (ignored for MACCS) |
| **radius** | Integer | No | 2 | Radius for Morgan fingerprints (ignored for other types) |
| **newColumnName** | String | No | "similarity" | Name for the new output column |
| **removeSourceColumns** | Boolean | No | false | Whether to remove the original SMILES columns from the output |

---

## Example

**Input Table:**

| smiles_a | smiles_b | name |
|----------|----------|------|
| c1ccccc1 | c1ccccc1 | identical |
| c1ccccc1 | c1ccncc1 | benzene vs pyridine |
| INVALID | c1ccccc1 | bad pair |

**Configuration:**
- **smilesColumn1**: `smiles_a`
- **smilesColumn2**: `smiles_b`
- **fingerprintType**: `Morgan`
- **similarityMetric**: `Tanimoto`
- **newColumnName**: `similarity`

**Output Table:**

| smiles_a | smiles_b | name | similarity |
|----------|----------|------|------------|
| c1ccccc1 | c1ccccc1 | identical | 1.0 |
| c1ccccc1 | c1ccncc1 | benzene vs pyridine | 0.55 |
| INVALID | c1ccccc1 | bad pair | |

*(Exact similarity values depend on the fingerprint type and parameters.)*

---

## Tips & Warnings

- **Identical Molecules**: Tanimoto similarity of identical molecules is always 1.0.
- **Morgan vs RDKit**: Morgan fingerprints (ECFP-like) are more commonly used for virtual screening. RDKit fingerprints are topological path-based.
- **MACCS Keys**: MACCS fingerprints have a fixed length of 166 bits and encode specific structural patterns. The numBits parameter is ignored for MACCS.
- **Tanimoto vs Dice**: Tanimoto is the most widely used metric. Dice similarity is always >= Tanimoto similarity for the same pair.
- **Memory Safety**: Each RDKit molecule and fingerprint object is deleted in a `finally` block, preventing native memory leaks.

---

## Technical Details

- **Algorithm**: Sequential row processing with streaming reader; fingerprint generation and similarity computation via RDKit
- **Memory**: Processes one row at a time (suitable for large datasets)
- **Progress**: Reports percentage based on rows processed vs total rows
- **Resource Management**: Uses `.use {}` blocks for automatic stream cleanup and `finally` for RDKit native object deletion
