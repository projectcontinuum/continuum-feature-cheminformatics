# RMSD Filter Node

Filter molecules using a greedy RMSD-based diversity selection, splitting diverse and redundant molecules into separate output ports. This is a cross-row splitter node that performs structural comparison in 3D.

---

## What It Does

The RMSD Filter Node reads all input molecules, generates 3D coordinates for each, and then applies a greedy diversity filter based on RMSD (root-mean-square deviation). The first valid molecule is always routed to the "above" (diverse) output. Each subsequent molecule is compared against all molecules already in the "above" set: if the minimum RMSD to any diverse molecule is greater than or equal to the threshold, it is added to "above"; otherwise it is routed to "below" (redundant).

**Key Points:**
- Splitter node: two output ports — "above" (diverse) and "below" (redundant)
- Cross-row operation: all molecules are read into memory for comparison
- Greedy algorithm: first molecule is always "above", subsequent molecules compared to "above" set
- 3D coordinates are generated automatically using ETKDGv3
- Optional hydrogen handling for RMSD calculation
- Invalid SMILES or embedding failures are routed to "below"
- All RDKit molecule objects are properly cleaned up after processing

---

## Configuration

### Input Ports
| Port | Description |
|------|-------------|
| **input** | Input table containing a column with SMILES strings |

### Output Ports
| Port | Description |
|------|-------------|
| **above** | Table containing diverse molecules (RMSD >= threshold from all other diverse molecules) |
| **below** | Table containing redundant molecules (RMSD < threshold to at least one diverse molecule) |

### Properties
| Property | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| **smilesColumn** | String | Yes | - | Name of the column containing SMILES strings |
| **rmsdThreshold** | Number | Yes | 0.5 | RMSD threshold for diversity filtering |
| **ignoreHydrogens** | Boolean | No | true | Whether to ignore hydrogens in RMSD calculation |

---

## Example

**Input Table:**

| smiles | name |
|--------|------|
| c1ccccc1 | benzene |
| c1ccncc1 | pyridine |
| c1ccc(O)cc1 | phenol |
| C1CCCCC1 | cyclohexane |

**Configuration:**
- **smilesColumn**: `smiles`
- **rmsdThreshold**: `0.5`

**Above Output (diverse):**

| smiles | name |
|--------|------|
| c1ccccc1 | benzene |
| C1CCCCC1 | cyclohexane |

**Below Output (redundant):**

| smiles | name |
|--------|------|
| c1ccncc1 | pyridine |
| c1ccc(O)cc1 | phenol |

---

## Tips & Warnings

- **Threshold Selection**: A lower threshold produces more diverse molecules (stricter filtering). A higher threshold allows more similar molecules through.
- **Order Dependence**: The greedy algorithm is order-dependent. The first molecule is always selected, and subsequent selections depend on which molecules were already picked.
- **Hydrogen Handling**: When `ignoreHydrogens` is true (default), hydrogens are not added before embedding, which speeds up computation. Set to false for more accurate RMSD including hydrogen positions.
- **Memory**: All molecules and their 3D coordinates are held in memory. Large datasets may require significant memory.
- **Invalid Molecules**: Molecules that fail SMILES parsing or 3D embedding are always routed to "below".
- **Computational Cost**: RMSD computation scales quadratically with the number of diverse molecules selected.

---

## Technical Details

- **Algorithm**: Greedy diversity filtering — O(N * K) where N is total molecules and K is diverse set size
- **3D Method**: `addHs()` (if not ignoring hydrogens) then `EmbedMolecule()` with ETKDGv3
- **RMSD Calculation**: Uses `alignMol()` which returns RMSD after optimal superposition
- **Memory**: All molecules stored in memory for cross-row comparison
- **Progress**: Reports 40% after reading/embedding, 70% after diversity filtering, 100% after writing
- **Resource Management**: Uses `finally` blocks for both output writer and molecule cleanup
