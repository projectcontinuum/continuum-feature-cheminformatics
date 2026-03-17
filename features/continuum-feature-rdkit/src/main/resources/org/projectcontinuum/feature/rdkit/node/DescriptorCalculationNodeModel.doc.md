# Descriptor Calculation Node

Calculate molecular descriptors from SMILES strings using RDKit. This is the most comprehensive RDKit descriptor node, supporting 41 scalar molecular descriptors including molecular weight, lipophilicity, topological polar surface area, hydrogen bond counts, ring counts, connectivity indices, and shape descriptors.

---

## What It Does

The Descriptor Calculation Node reads a SMILES string from each row, parses it into an RDKit molecule object, and computes the selected set of molecular descriptors. Each descriptor is written to a new column in the output table. You can select any combination of the 41 available descriptors and optionally add a prefix to the output column names.

**Key Points:**
- Supports 41 scalar molecular descriptors across multiple categories
- Multi-select: choose any subset of descriptors to compute
- Invalid SMILES produce empty strings for all descriptor columns — no error is thrown
- All original columns are preserved in the output
- Optional column prefix to avoid name collisions
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
| **output** | Table with all original columns plus new columns for each selected descriptor |

### Properties
| Property | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| **smilesColumn** | String | Yes | - | Name of the column containing SMILES strings |
| **descriptors** | Array of String | Yes | - | List of descriptor names to calculate (multi-select) |
| **columnPrefix** | String | No | "" | Optional prefix for output descriptor column names |

### Available Descriptors

| Category | Descriptors |
|----------|-------------|
| **Molecular Weight** | AMW, ExactMW |
| **Lipophilicity & Surface Area** | SlogP, SMR, LabuteASA, TPSA |
| **Hydrogen Bonds** | NumLipinskiHBA, NumLipinskiHBD, NumHBD, NumHBA |
| **Bond & Atom Counts** | NumRotatableBonds, NumAmideBonds, NumHeteroAtoms, NumHeavyAtoms, NumAtoms |
| **Stereocenters** | NumStereocenters, NumUnspecifiedStereocenters |
| **Ring Counts** | NumRings, NumAromaticRings, NumSaturatedRings, NumAliphaticRings |
| **Heterocycle Counts** | NumAromaticHeterocycles, NumSaturatedHeterocycles, NumAliphaticHeterocycles |
| **Carbocycle Counts** | NumAromaticCarbocycles, NumSaturatedCarbocycles, NumAliphaticCarbocycles |
| **SP3 Fraction** | FractionCSP3 |
| **Connectivity Indices (Valence)** | Chi0v, Chi1v, Chi2v, Chi3v, Chi4v |
| **Connectivity Indices (Non-Hydrogen)** | Chi1n, Chi2n, Chi3n, Chi4n |
| **Shape Descriptors** | HallKierAlpha, kappa1, kappa2, kappa3 |

---

## Example

**Input Table:**

| smiles | name |
|--------|------|
| c1ccccc1 | benzene |
| CC(=O)Oc1ccccc1C(=O)O | aspirin |
| INVALID_SMILES | bad |

**Configuration:**
- **smilesColumn**: `smiles`
- **descriptors**: `["AMW", "SlogP", "NumRings"]`
- **columnPrefix**: `""`

**Output Table:**

| smiles | name | AMW | SlogP | NumRings |
|--------|------|-----|-------|----------|
| c1ccccc1 | benzene | 78.11 | 1.6866 | 1 |
| CC(=O)Oc1ccccc1C(=O)O | aspirin | 180.16 | 1.3101 | 1 |
| INVALID_SMILES | bad | | | |

---

## Tips & Warnings

- **Invalid SMILES**: Rows with unparseable SMILES will get empty strings for all descriptor columns. The node will not fail or skip the row.
- **Column Prefix**: Use a prefix (e.g., `"rdkit_"`) when your table already contains columns with names matching descriptor names, to avoid overwriting existing data.
- **Descriptor Selection**: Start with a common subset (AMW, SlogP, TPSA, NumHBD, NumHBA, NumRotatableBonds, NumRings) for drug-likeness screening. Add more descriptors as needed for QSAR modeling.
- **Stereocenters**: The NumStereocenters and NumUnspecifiedStereocenters descriptors automatically assign stereochemistry before counting.
- **Memory Safety**: Each RDKit molecule object is deleted in a `finally` block, preventing native memory leaks.

---

## Technical Details

- **Algorithm**: Sequential row processing with streaming reader
- **Memory**: Processes one row at a time (suitable for large datasets)
- **Progress**: Reports percentage based on rows processed vs total rows
- **Resource Management**: Uses `.use {}` blocks for automatic stream cleanup and `finally` for RDKit native object deletion
- **Descriptor Count**: 41 scalar descriptors (vector descriptors such as slogp_VSA, smr_VSA, peoe_VSA, and MQN are not included)
