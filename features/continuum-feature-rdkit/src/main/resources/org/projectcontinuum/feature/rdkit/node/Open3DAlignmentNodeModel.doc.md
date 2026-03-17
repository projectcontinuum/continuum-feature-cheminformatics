# Open 3D Alignment Node

Align query molecules against reference molecules in 3D using RDKit. This node has two input ports and finds the best alignment for each query molecule across all reference molecules.

---

## What It Does

The Open 3D Alignment Node reads all reference molecules first, generates their 3D coordinates, and stores them in memory. Then for each query molecule, it generates 3D coordinates and aligns it against every reference molecule. The best alignment (lowest RMSD) is kept, and the aligned MolBlock, RMSD, and alignment score are written to the output.

**Key Points:**
- Two input ports: query molecules and reference molecules
- All reference molecules are read into memory first
- Each query molecule is aligned against every reference — best RMSD wins
- Generates 3D coordinates automatically using ETKDGv3 before alignment
- Uses `alignMol()` for 3D molecular alignment
- Invalid SMILES or embedding failures produce empty strings
- Reference molecules are cleaned up in a `finally` block

---

## Configuration

### Input Ports
| Port | Description |
|------|-------------|
| **query** | Input table containing query molecules as SMILES strings |
| **reference** | Input table containing reference molecules as SMILES strings |

### Output Ports
| Port | Description |
|------|-------------|
| **output** | Table with original query columns plus aligned MolBlock, RMSD, and score columns |

### Properties
| Property | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| **querySmilesColumn** | String | Yes | - | Name of the SMILES column in the query table |
| **referenceSmilesColumn** | String | Yes | - | Name of the SMILES column in the reference table |
| **newAlignedColumnName** | String | No | "aligned_mol_block" | Name for the aligned MolBlock column |
| **newRmsdColumnName** | String | No | "alignment_rmsd" | Name for the RMSD column |
| **newScoreColumnName** | String | No | "alignment_score" | Name for the alignment score column |
| **removeSourceColumn** | Boolean | No | false | Whether to remove the original SMILES column |

---

## Example

**Query Table:**

| smiles | name |
|--------|------|
| c1ccncc1 | pyridine |
| c1ccccc1 | benzene |

**Reference Table:**

| ref_smiles |
|------------|
| c1ccc(O)cc1 |

**Configuration:**
- **querySmilesColumn**: `smiles`
- **referenceSmilesColumn**: `ref_smiles`

**Output Table:**

| smiles | name | aligned_mol_block | alignment_rmsd | alignment_score |
|--------|------|-------------------|----------------|-----------------|
| c1ccncc1 | pyridine | (aligned 3D MolBlock) | 0.35 | 0.35 |
| c1ccccc1 | benzene | (aligned 3D MolBlock) | 0.12 | 0.12 |

---

## Tips & Warnings

- **Reference Count**: The node aligns each query against ALL references. With many references and queries, this can be computationally expensive.
- **3D Embedding**: Both query and reference molecules need successful 3D embedding. Molecules that fail embedding are skipped.
- **RMSD Interpretation**: Lower RMSD means a better alignment. A value of 0.0 means perfect overlap.
- **Memory**: All reference molecules are held in memory for the duration of the node execution.
- **Hydrogens**: Explicit hydrogens are added automatically before 3D embedding.

---

## Technical Details

- **Algorithm**: All-references-vs-each-query alignment with best-RMSD selection
- **3D Method**: `addHs()` then `EmbedMolecule()` with ETKDGv3 for both query and reference
- **Alignment**: Uses `alignMol()` which returns RMSD directly
- **Memory**: All reference molecules stored in memory; query molecules processed one at a time
- **Progress**: Reports 30% after reading references, 100% after processing all queries
- **Resource Management**: Uses `finally` block for reference molecule cleanup and `.use {}` for streams
