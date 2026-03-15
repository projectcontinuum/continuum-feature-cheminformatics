# RDKit Geometry Nodes (5 nodes)

> Read `00-preamble.md` first for the full Continuum node pattern, RDKit API rules, and shared utilities.

---

## Create AddCoordinatesNodeModel
- One input port "input" Table containing molecules as SMILES strings
- Properties:
  - smilesColumn (string, required — column containing SMILES)
  - dimension (string, enum: ["2D", "3D"], default "2D" — coordinate dimension to generate)
  - newColumnName (string, default "mol_block" — output column for MolBlock with coordinates)
  - removeSourceColumn (boolean, default false)
  - smartsTemplate (string, default "" — optional SMARTS template for 2D layout alignment)
- Output port "output" Table with original columns plus new column containing MolBlock with coordinates
- UI Schema: React Material JSONForm VerticalLayout with controls for smilesColumn, dimension, newColumnName, removeSourceColumn. Show smartsTemplate only when dimension is "2D".
- Behavior:
  1. Read properties, validate smilesColumn
  2. For each row, parse SMILES with `RDKFuncs.SmilesToMol(smiles)`
  3. If dimension is "2D":
     - If smartsTemplate is provided, parse it and use as template: `RDKFuncs.compute2DCoordsMimicDistMat(mol, templateMol)`
     - Otherwise: `RDKFuncs.compute2DCoords(mol)`
  4. If dimension is "3D":
     - Add Hs: `RDKFuncs.addHs(mol)` (needed for good 3D geometry)
     - Create embed params: `RDKFuncs.getETKDGv3()` with seed 42
     - Embed: `DistanceGeom.EmbedMolecule(mol, params)`
     - If embedding fails (returns -1), write empty MolBlock
  5. Convert to MolBlock: `RDKFuncs.MolToMolBlock(mol)`
  6. `mol.delete()` in finally, also delete template mol if used
  7. Write output row
- Thinking: 2D is fast and always succeeds. 3D uses ETKDG distance geometry and can fail for complex molecules. Adding Hs before 3D embedding is critical for correct geometry. Mirrors KNIME RDKitAddCoordinates.
- Category: ["RDKit", "Geometry"]
- Example Properties: {
    "smilesColumn": "smiles",
    "dimension": "2D",
    "newColumnName": "mol_block",
    "removeSourceColumn": false,
    "smartsTemplate": ""
  }
- Detailed Example Input: [
    {"smiles": "c1ccccc1", "name": "benzene"},
    {"smiles": "CC(=O)O", "name": "acetic_acid"}
  ]
- Detailed Example Output: [
    {"smiles": "c1ccccc1", "name": "benzene", "mol_block": "\n     RDKit          2D\n...\nM  END\n"},
    {"smiles": "CC(=O)O", "name": "acetic_acid", "mol_block": "\n     RDKit          2D\n...\nM  END\n"}
  ]

---

## Create OptimizeGeometryNodeModel
- One input port "input" Table containing molecules as SMILES (will generate 3D coords if not present)
- Properties:
  - smilesColumn (string, required)
  - forceField (string, enum: ["MMFF94", "UFF"], default "MMFF94")
  - iterations (integer, default 200, min 1, max 10000)
  - newMoleculeColumnName (string, default "optimized_mol_block")
  - newEnergyColumnName (string, default "energy")
  - newConvergedColumnName (string, default "converged")
  - removeSourceColumn (boolean, default false)
- Output port "output" Table with original columns plus optimized MolBlock, energy, and convergence flag
- UI Schema: React Material JSONForm VerticalLayout with controls for all properties
- Behavior:
  1. Read properties, validate smilesColumn
  2. For each row, parse SMILES and generate 3D coordinates:
     - `RDKFuncs.addHs(mol)`
     - `DistanceGeom.EmbedMolecule(mol, RDKFuncs.getETKDGv3())`
  3. Create force field:
     - MMFF94: `ForceField.MMFFGetMoleculeForceField(mol)` (check `ForceField.MMFFHasAllMoleculeParams(mol)` first)
     - UFF: `ForceField.UFFGetMoleculeForceField(mol)`
  4. Initialize and minimize: `ff.initialize()`, `converged = ff.minimize(iterations)` (returns 0 if converged)
  5. Calculate energy: `ff.calcEnergy()`
  6. Convert to MolBlock: `RDKFuncs.MolToMolBlock(mol)`
  7. `mol.delete()` and `ff.delete()` in finally
  8. Write output row with optimized MolBlock, energy (Double), converged (Boolean)
- Thinking: First generates 3D coordinates, then minimizes with a force field. MMFF94 is preferred for drug-like molecules; fall back to UFF if MMFF params missing. Convergence = 0 means success. Mirrors KNIME RDKitOptimizeGeometry.
- Category: ["RDKit", "Geometry"]
- Example Properties: {
    "smilesColumn": "smiles",
    "forceField": "MMFF94",
    "iterations": 200,
    "newMoleculeColumnName": "optimized_mol_block",
    "newEnergyColumnName": "energy",
    "newConvergedColumnName": "converged",
    "removeSourceColumn": false
  }
- Detailed Example Input: [
    {"smiles": "c1ccccc1", "name": "benzene"},
    {"smiles": "CCCCCC", "name": "hexane"}
  ]
- Detailed Example Output: [
    {"smiles": "c1ccccc1", "name": "benzene", "optimized_mol_block": "...3D MolBlock...", "energy": 12.45, "converged": true},
    {"smiles": "CCCCCC", "name": "hexane", "optimized_mol_block": "...3D MolBlock...", "energy": 3.78, "converged": true}
  ]

---

## Create AddConformersNodeModel
- One input port "input" Table containing molecules as SMILES
- Properties:
  - smilesColumn (string, required)
  - numberOfConformers (integer, default 10, min 1, max 1000)
  - maxIterations (integer, default 0 — 0 means use default)
  - randomSeed (integer, default 42)
  - pruneRmsThreshold (number, default -1.0 — negative means no pruning)
  - enforceChirality (boolean, default true)
  - useExpTorsionAngles (boolean, default true)
  - useBasicKnowledge (boolean, default true)
  - cleanupWithUff (boolean, default true — optimize conformers with UFF after generation)
  - onlyHeavyAtomsForRMS (boolean, default false)
  - newColumnName (string, default "conformers_mol_block")
  - removeSourceColumn (boolean, default false)
- Output port "output" Table — one row per conformer (expands input rows). Contains original columns + conformer MolBlock + conformer_id (integer)
- UI Schema: React Material JSONForm VerticalLayout with controls for all properties
- Behavior:
  1. Read properties, validate smilesColumn
  2. For each row, parse SMILES and add Hs
  3. Create EmbedParameters: `RDKFuncs.getETKDGv3()` and configure:
     - `params.setRandomSeed(randomSeed)`
     - `params.setNumConfs(numberOfConformers)` (or use `DistanceGeom.EmbedMultipleConfs()`)
     - `params.setPruneRmsThresh(pruneRmsThreshold)` if > 0
     - `params.setEnforceChirality(enforceChirality)`
     - `params.setUseExpTorsionAnglePrefs(useExpTorsionAngles)`
     - `params.setUseBasicKnowledge(useBasicKnowledge)`
  4. Embed: `DistanceGeom.EmbedMultipleConfs(mol, params)` or `DistanceGeom.EmbedMultipleConfs(mol, numberOfConformers, ...)`
  5. Optionally optimize each conformer with UFF: `ForceField.UFFOptimizeMolecule(mol, maxIterations, confId=i)`
  6. For each conformer i=0..numConformers-1:
     - Get MolBlock for conformer: `RDKFuncs.MolToMolBlock(mol, true, i)` (the 3rd param is confId)
     - Write one output row with original columns + conformer MolBlock + conformer_id=i
  7. `mol.delete()` in finally
- Thinking: Row expansion node — one input row produces multiple output rows (one per conformer). The EmbedParameters control the quality of 3D generation. UFF cleanup improves geometry. Mirrors KNIME RDKitAddConformers.
- Category: ["RDKit", "Geometry"]
- Example Properties: {
    "smilesColumn": "smiles",
    "numberOfConformers": 5,
    "randomSeed": 42,
    "pruneRmsThreshold": 0.5,
    "enforceChirality": true,
    "useExpTorsionAngles": true,
    "cleanupWithUff": true,
    "newColumnName": "conformer_mol_block",
    "removeSourceColumn": false
  }
- Detailed Example Input: [
    {"smiles": "CCCCCC", "name": "hexane"}
  ]
- Detailed Example Output: [
    {"smiles": "CCCCCC", "name": "hexane", "conformer_id": 0, "conformer_mol_block": "...3D MolBlock conf 0..."},
    {"smiles": "CCCCCC", "name": "hexane", "conformer_id": 1, "conformer_mol_block": "...3D MolBlock conf 1..."},
    {"smiles": "CCCCCC", "name": "hexane", "conformer_id": 2, "conformer_mol_block": "...3D MolBlock conf 2..."}
  ]

---

## Create Open3DAlignmentNodeModel
- Two input ports:
  - "query" Table containing query molecules as SMILES
  - "reference" Table containing reference molecules as SMILES
- Properties:
  - querySmilesColumn (string, required — SMILES column in query table)
  - referenceSmilesColumn (string, required — SMILES column in reference table)
  - newAlignedColumnName (string, default "aligned_mol_block")
  - newRmsdColumnName (string, default "alignment_rmsd")
  - newScoreColumnName (string, default "alignment_score")
  - newRefIdColumnName (string, default "reference_row_id")
  - allowReflection (boolean, default false — allow mirror-image alignment)
  - maxIterations (integer, default 50, min 1, max 1000)
  - removeSourceColumn (boolean, default false)
- Output port "output" Table with aligned molecules, RMSD, score, and reference row ID
- UI Schema: React Material JSONForm VerticalLayout with controls for all properties
- Behavior:
  1. Read properties, validate both SMILES columns
  2. Read ALL reference molecules into a list (small table expected)
  3. For each reference mol: parse SMILES, add Hs, embed 3D, check `ForceField.MMFFHasAllMoleculeParams()`
  4. For each query row:
     - Parse SMILES, add Hs, embed 3D
     - For each reference mol: perform O3A alignment: `queryMol.O3AAlignMol(refMol, ...)`
     - Returns RMSD and score as Double_Pair
     - Keep the best alignment (lowest RMSD)
     - Write output row with aligned MolBlock, best RMSD, best score, reference row ID
  5. Delete all molecules in finally blocks
- Thinking: Cross-table operation — reads all references first, then aligns each query. O3A (Open3DAlign) uses MMFF force field for shape-based alignment. Needs 3D coordinates on both molecules. Mirrors KNIME RDKitOpen3DAlignment.
- Category: ["RDKit", "Geometry"]
- Example Properties: {
    "querySmilesColumn": "smiles",
    "referenceSmilesColumn": "ref_smiles",
    "newAlignedColumnName": "aligned_mol_block",
    "newRmsdColumnName": "alignment_rmsd",
    "newScoreColumnName": "alignment_score",
    "allowReflection": false,
    "maxIterations": 50
  }
- Detailed Example Input:
  Query table: [
    {"smiles": "c1ccc2c(c1)cc1ccccc12", "name": "naphthalene"}
  ]
  Reference table: [
    {"ref_smiles": "c1ccccc1", "ref_name": "benzene"}
  ]
- Detailed Example Output: [
    {"smiles": "c1ccc2c(c1)cc1ccccc12", "name": "naphthalene", "aligned_mol_block": "...aligned 3D MolBlock...", "alignment_rmsd": 0.42, "alignment_score": 85.3, "reference_row_id": 0}
  ]

---

## Create RMSDFilterNodeModel
- One input port "input" Table containing molecules as SMILES (with 3D coordinates as MolBlock column, or will generate)
- Properties:
  - smilesColumn (string, required — column with SMILES or MolBlock)
  - rmsdThreshold (number, required, default 0.5, min 0.0 — RMSD cutoff)
  - ignoreHydrogens (boolean, default true — ignore H atoms in RMSD calculation)
- Output ports (SPLITTER — 2 outputs):
  - "above" Table with rows where RMSD >= threshold (diverse conformers)
  - "below" Table with rows where RMSD < threshold (redundant conformers)
- UI Schema: React Material JSONForm VerticalLayout with controls for smilesColumn, rmsdThreshold, ignoreHydrogens
- Behavior:
  1. Read properties, validate smilesColumn and rmsdThreshold
  2. Read ALL rows first into a list (need pairwise comparison)
  3. For the first row, always write to "above" (it's the initial reference)
  4. For each subsequent row:
     - Parse molecule and compute RMSD against all molecules already in "above" set
     - If minimum RMSD to any accepted molecule >= threshold: add to "above" (diverse)
     - If minimum RMSD < threshold: write to "below" (redundant)
     - RMSD calculation: `mol1.alignMol(mol2)` returns RMSD as Double
     - If ignoreHydrogens, remove Hs before comparison: `RDKFuncs.removeHs(mol)`
  5. Delete all molecules in finally
  6. Report progress
- Thinking: Greedy diversity filter based on RMSD. Commonly used after conformer generation to remove redundant structures. First conformer always accepted, subsequent ones tested against all accepted. Mirrors KNIME RDKitRMSDFilter. This is a splitter node with 2 output ports.
- Category: ["RDKit", "Geometry"]
- Example Properties: {
    "smilesColumn": "mol_block",
    "rmsdThreshold": 0.5,
    "ignoreHydrogens": true
  }
- Detailed Example Input: [
    {"mol_block": "...conformer_0...", "conformer_id": 0},
    {"mol_block": "...conformer_1...", "conformer_id": 1},
    {"mol_block": "...conformer_2...", "conformer_id": 2}
  ]
- Detailed Example Output (port "above"): [
    {"mol_block": "...conformer_0...", "conformer_id": 0},
    {"mol_block": "...conformer_2...", "conformer_id": 2}
  ]
- Detailed Example Output (port "below"): [
    {"mol_block": "...conformer_1...", "conformer_id": 1}
  ]
