# SDF Difference Checker Node

Compare two tables of molecules row by row, identifying differences in canonical SMILES, molecular properties, and optionally 3D coordinates. This is a QA/validation node for regression testing of cheminformatics pipelines.

---

## What It Does

The SDF Difference Checker Node reads all molecules from both input tables, then compares them index-matched (row 0 vs row 0, row 1 vs row 1, etc.). For each pair, it checks canonical SMILES equality and optionally compares molecular weight, formula, and atom coordinates. The output includes a match status and a description of any differences found.

**Key Points:**
- Index-matched comparison (row by row)
- Handles unequal table sizes (left_only / right_only)
- Configurable property and coordinate comparison
- Tolerance-based coordinate comparison for floating-point differences
- Option to output only rows with differences

---

## Configuration

### Input Ports
| Port | Description |
|------|-------------|
| **left** | Left table containing molecules |
| **right** | Right table containing molecules |

### Output Ports
| Port | Description |
|------|-------------|
| **output** | Comparison results with match status and difference descriptions |

### Properties
| Property | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| **leftSmilesColumn** | String | Yes | - | SMILES/MolBlock column in left table |
| **rightSmilesColumn** | String | Yes | - | SMILES/MolBlock column in right table |
| **compareCoordinates** | Boolean | No | false | Also compare 2D/3D coordinates |
| **compareProperties** | Boolean | No | true | Compare MW and formula |
| **toleranceForCoordinates** | Number | No | 0.001 | Tolerance for coordinate comparison |
| **outputDifferencesOnly** | Boolean | No | true | Only output rows with differences |
| **differenceColumnName** | String | No | "differences" | Column name for difference description |
| **matchColumnName** | String | No | "match_status" | Column name for match status |

### Match Status Values
| Status | Meaning |
|--------|---------|
| **match** | Identical molecules |
| **mismatch** | Different molecules at the same index |
| **left_only** | Row exists only in the left table |
| **right_only** | Row exists only in the right table |

---

## Example

**Left Table:**

| smiles | name |
|--------|------|
| c1ccccc1 | benzene |
| CCO | ethanol |
| CC(=O)O | acetic_acid |

**Right Table:**

| smiles | name |
|--------|------|
| c1ccccc1 | benzene |
| CCCO | propanol |

**Output Table (outputDifferencesOnly = false):**

| left_smiles | right_smiles | match_status | differences |
|-------------|--------------|--------------|-------------|
| c1ccccc1 | c1ccccc1 | match | |
| CCO | CCCO | mismatch | SMILES differ: CCO vs CCCO; MW differ: 46.07 vs 60.10 |
| CC(=O)O | | left_only | No corresponding row in right table |

---

## Technical Details

- **Algorithm**: Batch comparison — reads all rows from both tables into memory
- **Memory**: Loads both tables into memory (not suitable for extremely large datasets)
- **Progress**: Reports progress in two phases: loading (0-20%) and comparing (20-100%)
- **Format Detection**: Automatically detects MolBlock (SDF) vs SMILES input format

