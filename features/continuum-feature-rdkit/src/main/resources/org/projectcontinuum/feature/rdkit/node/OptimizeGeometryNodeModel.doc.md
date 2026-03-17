# Optimize Geometry Node

Optimize the 3D geometry of molecules using MMFF94 or UFF force fields via RDKit. This node embeds molecules in 3D space and then minimizes their energy to find a stable conformation.

---

## What It Does

The Optimize Geometry Node reads a SMILES string from each row, parses it into an RDKit molecule, adds explicit hydrogens, embeds 3D coordinates using the ETKDGv3 algorithm, and then minimizes the energy using the selected force field (MMFF94 or UFF). The output includes the optimized MolBlock, the final energy value, and whether the minimization converged.

**Key Points:**
- Supports MMFF94 and UFF force fields
- Configurable maximum number of minimization iterations
- Reports convergence status (converged = true when minimization returns 0)
- Reports final energy value after optimization
- Handles embedding failures and invalid SMILES gracefully with empty strings
- All original columns are preserved in the output
- Force field and molecule objects are properly cleaned up in `finally` blocks

---

## Configuration

### Input Ports
| Port | Description |
|------|-------------|
| **input** | Input table containing a column with SMILES strings |

### Output Ports
| Port | Description |
|------|-------------|
| **output** | Table with all original columns plus optimized MolBlock, energy, and convergence columns |

### Properties
| Property | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| **smilesColumn** | String | Yes | - | Name of the column containing SMILES strings |
| **forceField** | String | No | "MMFF94" | Force field to use (MMFF94 or UFF) |
| **iterations** | Integer | No | 200 | Maximum number of minimization iterations |
| **newMoleculeColumnName** | String | No | "optimized_mol_block" | Name for the optimized MolBlock column |
| **newEnergyColumnName** | String | No | "energy" | Name for the energy column |
| **newConvergedColumnName** | String | No | "converged" | Name for the convergence column |
| **removeSourceColumn** | Boolean | No | false | Whether to remove the original SMILES column |

---

## Example

**Input Table:**

| smiles | name |
|--------|------|
| c1ccccc1 | benzene |
| CCO | ethanol |

**Configuration:**
- **smilesColumn**: `smiles`
- **forceField**: `MMFF94`
- **iterations**: `200`

**Output Table:**

| smiles | name | optimized_mol_block | energy | converged |
|--------|------|---------------------|--------|-----------|
| c1ccccc1 | benzene | (3D MolBlock) | 5.23 | true |
| CCO | ethanol | (3D MolBlock) | 2.87 | true |

---

## Tips & Warnings

- **MMFF94 vs UFF**: MMFF94 is generally preferred for organic drug-like molecules. UFF (Universal Force Field) has broader element coverage and may be needed for organometallic compounds.
- **Convergence**: If convergence is `false`, the minimization did not reach a minimum within the specified iterations. Try increasing the iteration count.
- **Embedding Failure**: Some molecules may fail 3D embedding. These produce empty strings for all output columns.
- **Force Field Failure**: If the force field cannot be constructed for a molecule (e.g., missing parameters), empty strings are produced.
- **Memory Safety**: Force field and molecule objects are deleted in `finally` blocks, preventing native memory leaks.

---

## Technical Details

- **Algorithm**: Sequential row processing — embed, then minimize per row
- **3D Method**: `addHs()` then `EmbedMolecule()` with ETKDGv3, then force field minimization
- **Convergence Check**: `ff.minimize()` returns 0 if converged, non-zero otherwise
- **Energy**: Computed via `ff.calcEnergy()` after minimization
- **Memory**: Processes one row at a time (suitable for large datasets)
- **Progress**: Reports percentage based on rows processed vs total rows
- **Resource Management**: Uses `.use {}` blocks and `finally` for cleanup of native objects
