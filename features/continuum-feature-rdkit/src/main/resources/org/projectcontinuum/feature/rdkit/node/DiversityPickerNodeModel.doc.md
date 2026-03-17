# Diversity Picker Node

Select a diverse subset of molecules from an input table using the MaxMin diversity picking algorithm with RDKit fingerprints. This node is a splitter: it routes picked molecules to one output port and the remaining molecules to another.

---

## What It Does

The Diversity Picker Node reads all rows from the input table, generates molecular fingerprints for each valid SMILES, and applies the MaxMin diversity picking algorithm to select the most structurally diverse subset. Molecules are split into two output ports: "picked" (the diverse subset) and "unpicked" (the rest).

**Key Points:**
- Cross-row operation: all rows are loaded into memory for pairwise distance computation
- MaxMin algorithm: iteratively picks the molecule most distant from the already-selected set
- Supports Morgan and RDKit fingerprint types
- Two output ports: "picked" and "unpicked"
- Rows with invalid or empty SMILES are excluded from picking and sent to "unpicked"
- Reproducible results via the randomSeed parameter

---

## Configuration

### Input Ports
| Port | Description |
|------|-------------|
| **input** | Input table containing a column with SMILES strings |

### Output Ports
| Port | Description |
|------|-------------|
| **picked** | Table containing the selected diverse molecules |
| **unpicked** | Table containing the remaining molecules |

### Properties
| Property | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| **smilesColumn** | String | Yes | - | Name of the column containing SMILES strings |
| **numberToPick** | Integer | Yes | - | Number of diverse molecules to select (minimum 1) |
| **randomSeed** | Integer | No | 42 | Seed for reproducible selection of the starting molecule |
| **fingerprintType** | String | No | "Morgan" | Type of fingerprint: Morgan or RDKit |
| **numBits** | Integer | No | 2048 | Number of bits for the fingerprint |
| **radius** | Integer | No | 2 | Radius for Morgan fingerprints (ignored for RDKit type) |

---

## Example

**Input Table:**

| smiles | name |
|--------|------|
| c1ccccc1 | benzene |
| c1ccncc1 | pyridine |
| CCO | ethanol |
| CC(=O)O | acetic acid |

**Configuration:**
- **smilesColumn**: `smiles`
- **numberToPick**: `2`
- **fingerprintType**: `Morgan`

**Picked Output:**

| smiles | name |
|--------|------|
| c1ccccc1 | benzene |
| CCO | ethanol |

**Unpicked Output:**

| smiles | name |
|--------|------|
| c1ccncc1 | pyridine |
| CC(=O)O | acetic acid |

*(Actual picks depend on the fingerprint type, parameters, and random seed.)*

---

## Tips & Warnings

- **Memory Usage**: This node loads all rows and fingerprints into memory. For very large datasets (millions of rows), consider pre-filtering or sampling first.
- **numberToPick**: If numberToPick exceeds the number of valid molecules, all valid molecules are picked.
- **Invalid SMILES**: Rows with invalid or empty SMILES are always sent to the "unpicked" port and are never selected.
- **Random Seed**: The seed determines which molecule is selected first. Different seeds may produce different (but equally diverse) subsets.
- **MaxMin Algorithm**: The algorithm starts with a seed molecule, then iteratively picks the molecule with the maximum minimum Tanimoto distance to all already-picked molecules. This ensures structural diversity.
- **Memory Safety**: All RDKit molecule and fingerprint objects are properly deleted after use.

---

## Technical Details

- **Algorithm**: MaxMin diversity picking using Tanimoto distance (1 - Tanimoto similarity)
- **Memory**: All rows and fingerprints are loaded into memory (cross-row operation)
- **Progress**: Reports 30% after loading, 70% after picking, 100% after writing
- **Resource Management**: Fingerprints are deleted after picking; output writers are closed in a `finally` block
