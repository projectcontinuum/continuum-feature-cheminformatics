# RDKit Modifiers Nodes (5 nodes)

> Read `00-preamble.md` first for the full Continuum node pattern, RDKit API rules, and shared utilities.

---

## Create AddHsNodeModel
- One input port "input" Table containing molecules as SMILES strings
- Properties: smilesColumn (string, required), newColumnName (string, default "smiles_with_hs"), removeSourceColumn (boolean, default false)
- Output port "output" Table with original columns plus new column containing SMILES with explicit hydrogens
- UI Schema: React Material JSONForm VerticalLayout with controls for smilesColumn, newColumnName, removeSourceColumn
- Behavior:
  1. Read properties, validate smilesColumn
  2. For each row, parse SMILES with `RDKFuncs.SmilesToMol(smiles)`
  3. Add hydrogens: `RDKFuncs.addHs(mol)` (modifies mol in-place, adding explicit Hs)
  4. Convert back to SMILES: `RDKFuncs.MolToSmiles(mol)`
  5. `mol.delete()` in finally
  6. Write output row with new SMILES containing explicit Hs
- Thinking: Simple modifier. RDKFuncs.addHs() adds implicit Hs as explicit atoms. The resulting SMILES will be longer (e.g., benzene → `[H]c1c([H])c([H])c([H])c([H])c1[H]`). Mirrors KNIME RDKitAddHs.
- Category: ["RDKit", "Modifiers"]
- Example Properties: {
    "smilesColumn": "smiles",
    "newColumnName": "smiles_with_hs",
    "removeSourceColumn": false
  }
- Detailed Example Input: [
    {"smiles": "c1ccccc1", "name": "benzene"},
    {"smiles": "O", "name": "water"}
  ]
- Detailed Example Output: [
    {"smiles": "c1ccccc1", "name": "benzene", "smiles_with_hs": "[H]c1c([H])c([H])c([H])c([H])c1[H]"},
    {"smiles": "O", "name": "water", "smiles_with_hs": "[H]O[H]"}
  ]

---

## Create RemoveHsNodeModel
- One input port "input" Table containing molecules as SMILES strings
- Properties: smilesColumn (string, required), newColumnName (string, default "smiles_no_hs"), removeSourceColumn (boolean, default false)
- Output port "output" Table with original columns plus new column containing SMILES with hydrogens removed
- UI Schema: React Material JSONForm VerticalLayout with controls for smilesColumn, newColumnName, removeSourceColumn
- Behavior:
  1. Read properties, validate smilesColumn
  2. For each row, parse SMILES with `RDKFuncs.SmilesToMol(smiles)`
  3. Remove hydrogens: `RDKFuncs.removeHs(mol)` (modifies mol in-place)
  4. Sanitize: `RDKFuncs.sanitizeMol(mol)` to clean up the molecule
  5. Convert back to SMILES: `RDKFuncs.MolToSmiles(mol)`
  6. `mol.delete()` in finally
  7. Write output row
- Thinking: Inverse of AddHs. Strips explicit hydrogen atoms, converting them to implicit. Mirrors KNIME RDKitRemoveHs.
- Category: ["RDKit", "Modifiers"]
- Example Properties: {
    "smilesColumn": "smiles",
    "newColumnName": "smiles_no_hs",
    "removeSourceColumn": false
  }
- Detailed Example Input: [
    {"smiles": "[H]c1c([H])c([H])c([H])c([H])c1[H]", "name": "benzene_explicit_h"},
    {"smiles": "[H]O[H]", "name": "water_explicit_h"}
  ]
- Detailed Example Output: [
    {"smiles": "[H]c1c([H])c([H])c([H])c([H])c1[H]", "name": "benzene_explicit_h", "smiles_no_hs": "c1ccccc1"},
    {"smiles": "[H]O[H]", "name": "water_explicit_h", "smiles_no_hs": "O"}
  ]

---

## Create AromatizeNodeModel
- One input port "input" Table containing molecules as SMILES strings
- Properties: smilesColumn (string, required), newColumnName (string, default "aromatic_smiles"), removeSourceColumn (boolean, default false)
- Output port "output" Table with original columns plus new column containing aromatized SMILES
- UI Schema: React Material JSONForm VerticalLayout with controls for smilesColumn, newColumnName, removeSourceColumn
- Behavior:
  1. Read properties, validate smilesColumn
  2. For each row, parse SMILES with `RDKFuncs.SmilesToMol(smiles)`
  3. Apply aromaticity perception: `RDKFuncs.setAromaticity(mol)`
  4. Adjust hydrogens: `RDKFuncs.adjustHs(mol)` (optional, corrects H counts)
  5. Convert to SMILES: `RDKFuncs.MolToSmiles(mol)` — will use aromatic notation (lowercase letters)
  6. `mol.delete()` in finally
  7. Write output row
- Thinking: Forces aromaticity perception using RDKit rules. Kekule SMILES like C1=CC=CC=C1 become aromatic c1ccccc1. Mirrors KNIME RDKitAromatize.
- Category: ["RDKit", "Modifiers"]
- Example Properties: {
    "smilesColumn": "smiles",
    "newColumnName": "aromatic_smiles",
    "removeSourceColumn": false
  }
- Detailed Example Input: [
    {"smiles": "C1=CC=CC=C1", "name": "benzene_kekule"},
    {"smiles": "c1ccncc1", "name": "pyridine"}
  ]
- Detailed Example Output: [
    {"smiles": "C1=CC=CC=C1", "name": "benzene_kekule", "aromatic_smiles": "c1ccccc1"},
    {"smiles": "c1ccncc1", "name": "pyridine", "aromatic_smiles": "c1ccncc1"}
  ]

---

## Create KekulizeNodeModel
- One input port "input" Table containing molecules as SMILES strings
- Properties: smilesColumn (string, required), newColumnName (string, default "kekule_smiles"), removeSourceColumn (boolean, default false)
- Output port "output" Table with original columns plus new column containing Kekule SMILES
- UI Schema: React Material JSONForm VerticalLayout with controls for smilesColumn, newColumnName, removeSourceColumn
- Behavior:
  1. Read properties, validate smilesColumn
  2. For each row, parse SMILES with `RDKFuncs.SmilesToMol(smiles)`
  3. Cast to RWMol if needed (RDKit requires RWMol for kekulize)
  4. Kekulize: `mol.Kekulize(true)` — converts aromatic bonds to alternating single/double
  5. Convert to SMILES: `RDKFuncs.MolToSmiles(mol)` — will use Kekule notation (uppercase letters, explicit double bonds)
  6. `mol.delete()` in finally
  7. Write output row
- Thinking: Inverse of Aromatize. Converts aromatic SMILES to Kekule form with explicit single/double bonds. The `true` parameter clears aromaticity flags. Mirrors KNIME RDKitKekulize.
- Category: ["RDKit", "Modifiers"]
- Example Properties: {
    "smilesColumn": "smiles",
    "newColumnName": "kekule_smiles",
    "removeSourceColumn": false
  }
- Detailed Example Input: [
    {"smiles": "c1ccccc1", "name": "benzene_aromatic"},
    {"smiles": "c1ccncc1", "name": "pyridine"}
  ]
- Detailed Example Output: [
    {"smiles": "c1ccccc1", "name": "benzene_aromatic", "kekule_smiles": "C1=CC=CC=C1"},
    {"smiles": "c1ccncc1", "name": "pyridine", "kekule_smiles": "C1=CC=NC=C1"}
  ]

---

## Create SaltStripperNodeModel
- One input port "input" Table containing molecules as SMILES (may contain salts/fragments separated by ".")
- Properties:
  - smilesColumn (string, required — column containing SMILES)
  - newColumnName (string, default "stripped_smiles")
  - removeSourceColumn (boolean, default false)
  - keepLargestFragmentOnly (boolean, default true — keep only largest fragment by atom count)
- Output port "output" Table with original columns plus new column containing stripped SMILES
- UI Schema: React Material JSONForm VerticalLayout with controls for smilesColumn, newColumnName, removeSourceColumn, keepLargestFragmentOnly
- Behavior:
  1. Read properties, validate smilesColumn
  2. For each row, parse SMILES with `RDKFuncs.SmilesToMol(smiles)`
  3. If `keepLargestFragmentOnly` is true:
     - Get fragments: `RDKFuncs.getMolFrags(mol)` returns `ROMol_Vect`
     - Iterate fragments, find the one with the most heavy atoms: `frag.getNumHeavyAtoms()`
     - Convert the largest fragment to SMILES: `RDKFuncs.MolToSmiles(largestFrag)`
     - Delete ALL fragments in finally
  4. If `keepLargestFragmentOnly` is false:
     - Use a default salt definition list (common salts: Na+, Cl-, etc.)
     - Remove known salt fragments and return remaining molecule
  5. `mol.delete()` in finally
  6. Write output row with stripped SMILES
- Thinking: Multi-component SMILES like "CC(=O)O.[Na]" represent salts. This node strips the salt, keeping the parent compound. The keepLargestFragmentOnly approach is the simplest and most common. Mirrors KNIME RDKitSaltStripper.
- Category: ["RDKit", "Modifiers"]
- Example Properties: {
    "smilesColumn": "smiles",
    "newColumnName": "stripped_smiles",
    "removeSourceColumn": false,
    "keepLargestFragmentOnly": true
  }
- Detailed Example Input: [
    {"smiles": "CC(=O)[O-].[Na+]", "name": "sodium_acetate"},
    {"smiles": "[NH3+]CCC([O-])=O.[Cl-]", "name": "beta_alanine_hcl"},
    {"smiles": "c1ccccc1", "name": "benzene"}
  ]
- Detailed Example Output: [
    {"smiles": "CC(=O)[O-].[Na+]", "name": "sodium_acetate", "stripped_smiles": "CC(=O)[O-]"},
    {"smiles": "[NH3+]CCC([O-])=O.[Cl-]", "name": "beta_alanine_hcl", "stripped_smiles": "[NH3+]CCC([O-])=O"},
    {"smiles": "c1ccccc1", "name": "benzene", "stripped_smiles": "c1ccccc1"}
  ]
