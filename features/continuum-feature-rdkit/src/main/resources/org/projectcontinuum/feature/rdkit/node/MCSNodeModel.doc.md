# Maximum Common Substructure (MCS) Node

Find the maximum common substructure (MCS) among all input molecules. This is a cross-row operation that reads all molecules into memory and computes the largest substructure common to all (or a threshold fraction of) the input molecules.

---

## What It Does

The MCS Node reads all SMILES from the input table, parses them into RDKit molecule objects, and applies the MCS algorithm to find the largest common substructure. The result is a single output row containing the MCS as a SMARTS string, along with the number of atoms, number of bonds, and the count of input molecules used.

**Key Points:**
- Cross-row operation: all molecules are loaded into memory
- Output is a single row with MCS SMARTS, atom count, bond count, and molecule count
- Uses RDKit's `findMCS` algorithm via `ROMol_Vect`
- For a single molecule, the MCS is the molecule itself
- An error is thrown if no valid molecules are found
- All RDKit molecule objects are properly deleted after use

---

## Configuration

### Input Ports
| Port | Description |
|------|-------------|
| **input** | Input table containing a column with SMILES strings |

### Output Ports
| Port | Description |
|------|-------------|
| **output** | Single-row table with MCS SMARTS, atom count, bond count, and molecule count |

### Properties
| Property | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| **smilesColumn** | String | Yes | - | Name of the column containing SMILES strings |
| **threshold** | Number | No | 1.0 | Fraction of molecules that must contain the MCS (0.0 to 1.0) |
| **ringMatchesRingOnly** | Boolean | No | true | Whether ring bonds only match ring bonds |
| **completeRingsOnly** | Boolean | No | true | Whether partial ring matches are disallowed |
| **timeout** | Integer | No | 60 | Timeout in seconds for the MCS computation |

---

## Example

**Input Table:**

| smiles | name |
|--------|------|
| c1ccc(O)cc1 | phenol |
| c1ccc(N)cc1 | aniline |
| Cc1ccccc1 | toluene |

**Configuration:**
- **smilesColumn**: `smiles`
- **threshold**: `1.0`

**Output Table:**

| mcs_smarts | num_atoms | num_bonds | num_molecules |
|------------|-----------|-----------|---------------|
| [#6]1:[#6]:[#6]:[#6]:[#6]:[#6]:1 | 6 | 6 | 3 |

*(The MCS of phenol, aniline, and toluene is the benzene ring.)*

---

## Tips & Warnings

- **Memory Usage**: This node loads all molecules into memory. For very large datasets, consider pre-filtering or sampling first.
- **Computation Time**: MCS computation can be expensive for large molecule sets. Use the `timeout` parameter to prevent excessive computation.
- **Threshold**: A threshold of 1.0 means the MCS must be present in all molecules. Lower values allow the MCS to be absent from some molecules.
- **Single Molecule**: If only one valid molecule is found, its SMARTS representation is returned as the MCS.
- **Invalid SMILES**: Rows with invalid or empty SMILES are silently skipped. If no valid molecules remain, an error is thrown.

---

## Technical Details

- **Algorithm**: RDKit's `findMCS` algorithm applied to all valid molecules via `ROMol_Vect`
- **Memory**: All molecules loaded into memory (cross-row operation)
- **Progress**: Reports 30% after loading, 80% after MCS computation, 100% after writing
- **Resource Management**: All molecules deleted in a `finally` block; `ROMol_Vect` deleted after MCS computation; output writer closed via `.use {}` block
