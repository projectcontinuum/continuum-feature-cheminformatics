# RDKit Fragments Nodes (3 nodes)

> Read `00-preamble.md` first for the full Continuum node pattern, RDKit API rules, and shared utilities.

---

## Create MurckoScaffoldNodeModel
- One input port "input" Table containing molecules as SMILES strings
- Properties:
  - smilesColumn (string, required — column containing SMILES)
  - newColumnName (string, default "murcko_scaffold")
  - removeSourceColumn (boolean, default false)
  - makeGenericFramework (boolean, default false — if true, convert all atoms to C and all bonds to single to get generic framework)
- Output port "output" Table with original columns plus Murcko scaffold SMILES column
- UI Schema: React Material JSONForm VerticalLayout with controls for smilesColumn, newColumnName, removeSourceColumn, makeGenericFramework
- Behavior:
  1. Read properties, validate smilesColumn
  2. For each row, parse SMILES with `RDKFuncs.SmilesToMol(smiles)`
  3. Extract Murcko scaffold: `val scaffold = RDKFuncs.MurckoDecompose(mol)`
  4. If `makeGenericFramework` is true:
     - `RDKFuncs.makeScaffoldGeneric(scaffold)` — converts all atoms to C, bonds to single
  5. Convert scaffold to SMILES: `RDKFuncs.MolToSmiles(scaffold)`
  6. `mol.delete()` and `scaffold.delete()` in finally
  7. Write output row with scaffold SMILES
- Thinking: Murcko scaffold extracts the core ring system and linkers, removing all side chains. The generic framework further simplifies by replacing all atoms with carbon. Common in medicinal chemistry for scaffold analysis. Mirrors KNIME RDKitMurckoScaffold.
- Category: ["RDKit", "Fragments"]
- Example Properties: {
    "smilesColumn": "smiles",
    "newColumnName": "scaffold",
    "removeSourceColumn": false,
    "makeGenericFramework": false
  }
- Detailed Example Input: [
    {"smiles": "CC(=O)Oc1ccccc1C(=O)O", "name": "aspirin"},
    {"smiles": "Cn1c(=O)c2c(ncn2C)n(C)c1=O", "name": "caffeine"},
    {"smiles": "CCCCCC", "name": "hexane"}
  ]
- Detailed Example Output: [
    {"smiles": "CC(=O)Oc1ccccc1C(=O)O", "name": "aspirin", "scaffold": "c1ccccc1"},
    {"smiles": "Cn1c(=O)c2c(ncn2C)n(C)c1=O", "name": "caffeine", "scaffold": "c1ncc2[nH]cnc2n1"},
    {"smiles": "CCCCCC", "name": "hexane", "scaffold": ""}
  ]

---

## Create MoleculeExtractorNodeModel
- One input port "input" Table containing multi-component molecules (SMILES with "." separator)
- Properties:
  - smilesColumn (string, required — column containing SMILES)
  - newColumnName (string, default "fragment_smiles")
  - fragmentIdColumnName (string, default "fragment_id")
  - sanitizeFragments (boolean, default true)
- Output port "output" Table — one row per fragment (row expansion). Contains original columns + fragment SMILES + fragment_id
- UI Schema: React Material JSONForm VerticalLayout with controls for smilesColumn, newColumnName, fragmentIdColumnName, sanitizeFragments
- Behavior:
  1. Read properties, validate smilesColumn
  2. For each row, parse SMILES with `RDKFuncs.SmilesToMol(smiles)`
  3. Get fragments: `RDKFuncs.getMolFrags(mol, sanitizeFragments)` → returns `ROMol_Vect`
  4. For each fragment i in the vector:
     - Convert to SMILES: `RDKFuncs.MolToSmiles(fragment)`
     - Write one output row with: all original columns + fragment SMILES + fragment_id=i
  5. Delete all fragments and mol in finally (iterate and `.delete()` each fragment in the vector)
  6. If molecule has only one fragment, write single row with fragment_id=0
- Thinking: Row expansion node. Multi-component SMILES like "CC(=O)[O-].[Na+]" gets split into individual fragments. Each fragment becomes its own row. Useful for analyzing salt forms, mixtures. Mirrors KNIME RDKitMoleculeExtractor.
- Category: ["RDKit", "Fragments"]
- Example Properties: {
    "smilesColumn": "smiles",
    "newColumnName": "fragment_smiles",
    "fragmentIdColumnName": "fragment_id",
    "sanitizeFragments": true
  }
- Detailed Example Input: [
    {"smiles": "CC(=O)[O-].[Na+]", "name": "sodium_acetate"},
    {"smiles": "c1ccccc1", "name": "benzene"}
  ]
- Detailed Example Output: [
    {"smiles": "CC(=O)[O-].[Na+]", "name": "sodium_acetate", "fragment_smiles": "CC(=O)[O-]", "fragment_id": 0},
    {"smiles": "CC(=O)[O-].[Na+]", "name": "sodium_acetate", "fragment_smiles": "[Na+]", "fragment_id": 1},
    {"smiles": "c1ccccc1", "name": "benzene", "fragment_smiles": "c1ccccc1", "fragment_id": 0}
  ]

---

## Create MolFragmenterNodeModel
- One input port "input" Table containing molecules as SMILES strings
- Properties:
  - smilesColumn (string, required — column containing SMILES)
  - minPathLength (integer, default 1, min 1 — minimum bond path length for fragments)
  - maxPathLength (integer, default 7, max 30 — maximum bond path length for fragments)
- Output ports (2 outputs):
  - "fragments" Table with columns: fragment_index (int), fragment_smiles (string), fragment_size (int), parent_smiles (string)
  - "molecules" Table with original columns + fragment_indices (JSON array of int — which fragment IDs match)
- UI Schema: React Material JSONForm VerticalLayout with controls for smilesColumn, minPathLength, maxPathLength
- Behavior:
  1. Read properties, validate smilesColumn, check minPath <= maxPath
  2. For each row, parse SMILES with `RDKFuncs.SmilesToMol(smiles)`
  3. Find all subgraphs: `RDKFuncs.findAllSubgraphsOfLengthsMtoN(mol, minPathLength, maxPathLength)`
  4. For each subgraph path:
     - Extract fragment: `RDKFuncs.pathToSubmol(mol, path)` → fragment ROMol
     - Convert to SMILES: `RDKFuncs.MolToSmiles(fragment)`
     - Record fragment_index, fragment_smiles, fragment_size (num atoms)
     - `fragment.delete()` in finally
  5. Write unique fragments to "fragments" port (deduplicate by SMILES)
  6. Write molecule row to "molecules" port with list of matching fragment indices
  7. `mol.delete()` in finally
- Thinking: Generates all molecular fragments within a size range by finding subgraphs. This is computationally intensive for large molecules with many possible fragments. Results in two output tables — a fragment library and a mapping back to parent molecules. Mirrors KNIME RDKitMolFragmenter.
- Category: ["RDKit", "Fragments"]
- Example Properties: {
    "smilesColumn": "smiles",
    "minPathLength": 1,
    "maxPathLength": 3
  }
- Detailed Example Input: [
    {"smiles": "CCCO", "name": "propanol"}
  ]
- Detailed Example Output (port "fragments"): [
    {"fragment_index": 0, "fragment_smiles": "CC", "fragment_size": 2, "parent_smiles": "CCCO"},
    {"fragment_index": 1, "fragment_smiles": "CO", "fragment_size": 2, "parent_smiles": "CCCO"},
    {"fragment_index": 2, "fragment_smiles": "CCO", "fragment_size": 3, "parent_smiles": "CCCO"},
    {"fragment_index": 3, "fragment_smiles": "CCC", "fragment_size": 3, "parent_smiles": "CCCO"}
  ]
- Detailed Example Output (port "molecules"): [
    {"smiles": "CCCO", "name": "propanol", "fragment_indices": "[0, 1, 2, 3]"}
  ]
