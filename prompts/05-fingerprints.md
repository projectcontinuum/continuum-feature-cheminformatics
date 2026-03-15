# RDKit Fingerprints Nodes (4 nodes)

> Read `00-preamble.md` first for the full Continuum node pattern, RDKit API rules, and shared utilities.

---

## Create FingerprintNodeModel
- One input port "input" Table containing molecules as SMILES strings
- Properties:
  - smilesColumn (string, required — column containing SMILES)
  - fingerprintType (string, required, enum: ["Morgan", "FeatMorgan", "AtomPair", "Torsion", "RDKit", "Avalon", "Layered", "MACCS", "Pattern"], default "Morgan")
  - numBits (integer, default 2048, min 64, max 16384 — bit vector length; not used for MACCS which is always 166)
  - radius (integer, default 2, min 1, max 10 — Morgan/FeatMorgan radius)
  - minPath (integer, default 1, min 1 — RDKit/Layered/AtomPair minimum path length)
  - maxPath (integer, default 7, min 1 — RDKit/Layered/AtomPair maximum path length)
  - torsionPathLength (integer, default 4, min 2 — Torsion fingerprint path length)
  - layerFlags (integer, default 4294967295 — Layered fingerprint layer flags bitmask)
  - useChirality (boolean, default false — Morgan/FeatMorgan/AtomPair/Torsion chirality)
  - newColumnName (string, default "fingerprint" — output column for bit string)
  - removeSourceColumn (boolean, default false)
- Output port "output" Table with original columns plus fingerprint column (as bit string "0101001...")
- UI Schema: React Material JSONForm VerticalLayout with:
  - Control for smilesColumn
  - Control for fingerprintType
  - Controls for type-specific parameters with visibility rules:
    - numBits: show for all EXCEPT MACCS
    - radius: show for Morgan, FeatMorgan
    - minPath, maxPath: show for RDKit, Layered, AtomPair
    - torsionPathLength: show for Torsion
    - layerFlags: show for Layered
    - useChirality: show for Morgan, FeatMorgan, AtomPair, Torsion
  - Control for newColumnName, removeSourceColumn
- Behavior:
  1. Read properties, validate smilesColumn and fingerprintType
  2. For each row, parse SMILES with `RDKFuncs.SmilesToMol(smiles)`
  3. Generate fingerprint based on type:
     - **Morgan**: `RDKFuncs.getMorganFingerprintAsBitVect(mol, radius, numBits)` — if useChirality, pass chirality flag
     - **FeatMorgan**: Same as Morgan but compute feature invariants first: `RDKFuncs.getFeatureInvariants(mol)`, then `RDKFuncs.getMorganFingerprintAsBitVect(mol, radius, numBits, featureInvariants)`
     - **AtomPair**: `RDKFuncs.getHashedAtomPairFingerprintAsBitVect(mol, numBits, minPath, maxPath)`
     - **Torsion**: `RDKFuncs.getHashedTopologicalTorsionFingerprintAsBitVect(mol, numBits, torsionPathLength)`
     - **RDKit**: `RDKFuncs.RDKFingerprintMol(mol, minPath, maxPath, numBits, 2, true, 0.0, 128, true, true)`
     - **Avalon**: `RDKFuncs.getAvalonFP(mol, fp, numBits, false, true, 8388608)` — synchronize with a lock (Avalon not thread-safe)
     - **Layered**: `RDKFuncs.LayeredFingerprintMol(mol, layerFlags, minPath, maxPath, numBits)`
     - **MACCS**: `RDKFuncs.MACCSFingerprintMol(mol)` — always 166 bits, ignore numBits
     - **Pattern**: `RDKFuncs.PatternFingerprintMol(mol, numBits)`
  4. Convert ExplicitBitVect to bit string: `fp.ToBitString()` → "010011..."
  5. `mol.delete()` and `fp.delete()` in finally
  6. Write output row with fingerprint string
- Thinking: Central fingerprint node supporting 9 types. Each type has different parameters. Morgan (radius 2, 2048 bits) ≈ ECFP4, the most common. MACCS has fixed 166 bits. Avalon needs synchronization. Store as bit string for maximum interoperability. Mirrors KNIME RDKitFingerprint.
- Category: ["RDKit", "Fingerprints"]
- Example Properties: {
    "smilesColumn": "smiles",
    "fingerprintType": "Morgan",
    "numBits": 2048,
    "radius": 2,
    "useChirality": false,
    "newColumnName": "morgan_fp",
    "removeSourceColumn": false
  }
- Detailed Example Input: [
    {"smiles": "c1ccccc1", "name": "benzene"},
    {"smiles": "CC(=O)O", "name": "acetic_acid"}
  ]
- Detailed Example Output: [
    {"smiles": "c1ccccc1", "name": "benzene", "morgan_fp": "0000010001...0010100000"},
    {"smiles": "CC(=O)O", "name": "acetic_acid", "morgan_fp": "0001000100...0000010010"}
  ]

---

## Create CountBasedFingerprintNodeModel
- One input port "input" Table containing molecules as SMILES strings
- Properties:
  - smilesColumn (string, required)
  - fingerprintType (string, required, enum: ["Morgan", "FeatMorgan", "AtomPair", "Torsion"], default "Morgan" — only types that support count-based)
  - numBits (integer, default 2048, min 64, max 16384)
  - radius (integer, default 2 — Morgan/FeatMorgan)
  - minPath (integer, default 1 — AtomPair)
  - maxPath (integer, default 30 — AtomPair)
  - torsionPathLength (integer, default 4 — Torsion)
  - useChirality (boolean, default false)
  - newColumnName (string, default "count_fingerprint")
  - removeSourceColumn (boolean, default false)
- Output port "output" Table with original columns plus count fingerprint as JSON array of integers
- UI Schema: React Material JSONForm VerticalLayout (same conditional visibility as FingerprintNodeModel for type-specific params)
- Behavior:
  1. Read properties, validate smilesColumn
  2. For each row, parse SMILES
  3. Generate count-based fingerprint:
     - **Morgan**: `RDKFuncs.getHashedMorganFingerprint(mol, radius, numBits)` → SparseIntVect32
     - **FeatMorgan**: Similar with feature invariants
     - **AtomPair**: `RDKFuncs.getHashedAtomPairFingerprint(mol, numBits, minPath, maxPath)` → SparseIntVect32
     - **Torsion**: `RDKFuncs.getHashedTopologicalTorsionFingerprint(mol, numBits, torsionPathLength)` → SparseIntVect32
  4. Convert SparseIntVect32 to a JSON-serializable map of {bit_index: count} for non-zero entries
  5. `mol.delete()` in finally
  6. Write output row with count fingerprint as JSON string
- Thinking: Count-based fingerprints store integer counts instead of binary bits, providing richer information for similarity and ML. Only 4 types support counts. Store as JSON map of non-zero {index: count} pairs to keep output compact. Mirrors KNIME RDKitCountBasedFingerprint.
- Category: ["RDKit", "Fingerprints"]
- Example Properties: {
    "smilesColumn": "smiles",
    "fingerprintType": "Morgan",
    "numBits": 2048,
    "radius": 2,
    "useChirality": false,
    "newColumnName": "morgan_count_fp"
  }
- Detailed Example Input: [
    {"smiles": "c1ccccc1", "name": "benzene"}
  ]
- Detailed Example Output: [
    {"smiles": "c1ccccc1", "name": "benzene", "morgan_count_fp": "{\"650\": 6, \"1024\": 1, \"1876\": 3}"}
  ]

---

## Create FingerprintSimilarityNodeModel
- One input port "input" Table containing molecules as SMILES (or pre-computed fingerprint columns)
- Properties:
  - smilesColumn1 (string, required — first molecule SMILES column)
  - smilesColumn2 (string, required — second molecule SMILES column)
  - fingerprintType (string, enum: ["Morgan", "RDKit", "MACCS", "AtomPair", "Torsion"], default "Morgan")
  - similarityMetric (string, enum: ["Tanimoto", "Dice"], default "Tanimoto")
  - numBits (integer, default 2048)
  - radius (integer, default 2 — for Morgan)
  - newColumnName (string, default "similarity")
  - removeSourceColumns (boolean, default false)
- Output port "output" Table with original columns plus similarity score column (Double 0.0-1.0)
- UI Schema: React Material JSONForm VerticalLayout with controls for all properties
- Behavior:
  1. Read properties, validate both SMILES columns
  2. For each row:
     - Parse SMILES1 → mol1, SMILES2 → mol2
     - Generate fingerprint for each molecule (using configured type and params)
     - Compute similarity: `RDKFuncs.TanimotoSimilarity(fp1, fp2)` or `RDKFuncs.DiceSimilarity(fp1, fp2)`
     - `mol1.delete()`, `mol2.delete()`, `fp1.delete()`, `fp2.delete()` in finally
  3. Write output row with similarity score
- Thinking: New Continuum-specific node (derived from KNIME fingerprint functionality). Compares two molecules per row using fingerprint similarity. Common use case: comparing pairs of molecules in the same table. The existing RDKitService.kt computeSimilarity() method shows the exact pattern.
- Category: ["RDKit", "Fingerprints"]
- Example Properties: {
    "smilesColumn1": "smiles_a",
    "smilesColumn2": "smiles_b",
    "fingerprintType": "Morgan",
    "similarityMetric": "Tanimoto",
    "numBits": 2048,
    "radius": 2,
    "newColumnName": "tanimoto_similarity"
  }
- Detailed Example Input: [
    {"smiles_a": "c1ccccc1", "smiles_b": "c1ccncc1", "name": "benzene_vs_pyridine"},
    {"smiles_a": "c1ccccc1", "smiles_b": "c1ccccc1", "name": "benzene_vs_benzene"}
  ]
- Detailed Example Output: [
    {"smiles_a": "c1ccccc1", "smiles_b": "c1ccncc1", "name": "benzene_vs_pyridine", "tanimoto_similarity": 0.67},
    {"smiles_a": "c1ccccc1", "smiles_b": "c1ccccc1", "name": "benzene_vs_benzene", "tanimoto_similarity": 1.0}
  ]

---

## Create DiversityPickerNodeModel
- One input port "input" Table containing molecules as SMILES strings
- Properties:
  - smilesColumn (string, required)
  - numberToPick (integer, required, min 1 — number of diverse molecules to select)
  - randomSeed (integer, default 42)
  - fingerprintType (string, enum: ["Morgan", "RDKit"], default "Morgan" — FP type for diversity calculation)
  - numBits (integer, default 2048)
  - radius (integer, default 2)
- Output ports (SPLITTER — 2 outputs):
  - "picked" Table with the diverse subset
  - "unpicked" Table with remaining molecules
- UI Schema: React Material JSONForm VerticalLayout with controls for all properties
- Behavior:
  1. Read properties, validate smilesColumn and numberToPick
  2. Read ALL rows into memory (cross-row operation)
  3. Generate fingerprints for all molecules
  4. Compute pairwise distance matrix (1 - Tanimoto similarity)
  5. Run MaxMin diversity picking:
     - Start with random seed molecule
     - Iteratively pick the molecule maximally distant from the already-picked set
     - Repeat until numberToPick molecules selected
  6. Write picked molecules to "picked" port, rest to "unpicked" port
  7. Delete all fingerprints and molecules in finally
- Thinking: Cross-row operation requiring all fingerprints in memory. MaxMin algorithm is greedy: O(N*K) where N=total, K=picked. The KNIME implementation uses `RDKFuncs.LazyBitVectorPicker()` for efficiency — check if available in Java wrapper, otherwise implement MaxMin manually. Mirrors KNIME RDKitDiversityPicker.
- Category: ["RDKit", "Fingerprints"]
- Example Properties: {
    "smilesColumn": "smiles",
    "numberToPick": 3,
    "randomSeed": 42,
    "fingerprintType": "Morgan",
    "numBits": 2048,
    "radius": 2
  }
- Detailed Example Input: [
    {"smiles": "c1ccccc1", "name": "benzene"},
    {"smiles": "CC(=O)O", "name": "acetic_acid"},
    {"smiles": "c1ccncc1", "name": "pyridine"},
    {"smiles": "CC(=O)Oc1ccccc1C(=O)O", "name": "aspirin"},
    {"smiles": "CCO", "name": "ethanol"}
  ]
- Detailed Example Output (port "picked"): [
    {"smiles": "c1ccccc1", "name": "benzene"},
    {"smiles": "CC(=O)O", "name": "acetic_acid"},
    {"smiles": "CC(=O)Oc1ccccc1C(=O)O", "name": "aspirin"}
  ]
- Detailed Example Output (port "unpicked"): [
    {"smiles": "c1ccncc1", "name": "pyridine"},
    {"smiles": "CCO", "name": "ethanol"}
  ]
