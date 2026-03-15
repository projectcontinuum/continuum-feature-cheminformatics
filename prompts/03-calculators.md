# RDKit Calculators Nodes (2 nodes)

> Read `00-preamble.md` first for the full Continuum node pattern, RDKit API rules, and shared utilities.

---

## Create DescriptorCalculationNodeModel
- One input port "input" Table containing molecules as SMILES strings
- Properties:
  - smilesColumn (string, required — column containing SMILES)
  - descriptors (array of string, required — list of descriptors to compute; enum values listed below)
  - columnPrefix (string, default "" — optional prefix for output column names)
- Descriptor enum values (all 45+):
  "SlogP", "SMR", "LabuteASA", "TPSA", "AMW", "ExactMW",
  "NumLipinskiHBA", "NumLipinskiHBD", "NumRotatableBonds",
  "NumHBD", "NumHBA", "NumAmideBonds", "NumHeteroAtoms", "NumHeavyAtoms",
  "NumAtoms", "NumStereocenters", "NumUnspecifiedStereocenters",
  "NumRings", "NumAromaticRings", "NumSaturatedRings", "NumAliphaticRings",
  "NumAromaticHeterocycles", "NumSaturatedHeterocycles", "NumAliphaticHeterocycles",
  "NumAromaticCarbocycles", "NumSaturatedCarbocycles", "NumAliphaticCarbocycles",
  "FractionCSP3",
  "Chi0v", "Chi1v", "Chi2v", "Chi3v", "Chi4v",
  "Chi1n", "Chi2n", "Chi3n", "Chi4n",
  "HallKierAlpha", "kappa1", "kappa2", "kappa3",
  "slogp_VSA" (12 columns), "smr_VSA" (10 columns), "peoe_VSA" (14 columns),
  "MQN" (42 columns)
- Output port "output" Table with original columns plus one column per selected descriptor
- UI Schema: React Material JSONForm VerticalLayout with:
  - Control for smilesColumn
  - Multi-select control for descriptors (with enum list)
  - Control for columnPrefix
- Behavior:
  1. Read properties, validate smilesColumn and descriptors array
  2. For each row, parse SMILES with `RDKFuncs.SmilesToMol(smiles)`
  3. For each selected descriptor, compute the value using RDKit:
     - `SlogP`: `RDKFuncs.calcMolLogP(mol)` → Double
     - `SMR`: `RDKFuncs.calcMolMR(mol)` → Double
     - `LabuteASA`: `RDKFuncs.calcLabuteASA(mol)` → Double
     - `TPSA`: `RDKFuncs.calcTPSA(mol)` → Double
     - `AMW`: `RDKFuncs.calcAMW(mol, false)` → Double
     - `ExactMW`: `RDKFuncs.calcExactMW(mol, false)` → Double
     - `NumLipinskiHBA`: `RDKFuncs.calcLipinskiHBA(mol)` → Int
     - `NumLipinskiHBD`: `RDKFuncs.calcLipinskiHBD(mol)` → Int
     - `NumRotatableBonds`: `RDKFuncs.calcNumRotatableBonds(mol)` → Int
     - `NumHBD`: `RDKFuncs.calcNumHBD(mol)` → Int
     - `NumHBA`: `RDKFuncs.calcNumHBA(mol)` → Int
     - `NumAmideBonds`: `RDKFuncs.calcNumAmideBonds(mol)` → Int
     - `NumHeteroAtoms`: `RDKFuncs.calcNumHeteroatoms(mol)` → Int
     - `NumHeavyAtoms`: `mol.getNumHeavyAtoms()` → Int
     - `NumAtoms`: `mol.getNumAtoms(false)` → Int
     - `NumStereocenters`: `RDKFuncs.numAtomStereoCenters(mol)` → Int (call `RDKFuncs.assignStereochemistry(mol)` first)
     - `NumUnspecifiedStereocenters`: `RDKFuncs.numUnspecifiedAtomStereoCenters(mol)` → Int
     - `NumRings`: `RDKFuncs.calcNumRings(mol)` → Int
     - `NumAromaticRings`: `RDKFuncs.calcNumAromaticRings(mol)` → Int
     - `NumSaturatedRings`: `RDKFuncs.calcNumSaturatedRings(mol)` → Int
     - `NumAliphaticRings`: `RDKFuncs.calcNumAliphaticRings(mol)` → Int
     - `NumAromaticHeterocycles`: `RDKFuncs.calcNumAromaticHeterocycles(mol)` → Int
     - `NumSaturatedHeterocycles`: `RDKFuncs.calcNumSaturatedHeterocycles(mol)` → Int
     - `NumAliphaticHeterocycles`: `RDKFuncs.calcNumAliphaticHeterocycles(mol)` → Int
     - `NumAromaticCarbocycles`: `RDKFuncs.calcNumAromaticCarbocycles(mol)` → Int
     - `NumSaturatedCarbocycles`: `RDKFuncs.calcNumSaturatedCarbocycles(mol)` → Int
     - `NumAliphaticCarbocycles`: `RDKFuncs.calcNumAliphaticCarbocycles(mol)` → Int
     - `FractionCSP3`: `RDKFuncs.calcFractionCSP3(mol)` → Double
     - `Chi0v..Chi4v`: `RDKFuncs.calcChi0v(mol)` ... `RDKFuncs.calcChi4v(mol)` → Double
     - `Chi1n..Chi4n`: `RDKFuncs.calcChi1n(mol)` ... `RDKFuncs.calcChi4n(mol)` → Double
     - `HallKierAlpha`: `RDKFuncs.calcHallKierAlpha(mol)` → Double
     - `kappa1..kappa3`: `RDKFuncs.calcKappa1(mol)` ... `RDKFuncs.calcKappa3(mol)` → Double
     - `slogp_VSA`: `RDKFuncs.calcSlogP_VSA(mol)` → Double_Vect (12 values, create columns slogp_VSA_1..slogp_VSA_12)
     - `smr_VSA`: `RDKFuncs.calcSMR_VSA(mol)` → Double_Vect (10 values, create columns smr_VSA_1..smr_VSA_10)
     - `peoe_VSA`: `RDKFuncs.calcPEOE_VSA(mol)` → Double_Vect (14 values, create columns peoe_VSA_1..peoe_VSA_14)
     - `MQN`: `RDKFuncs.calcMQNs(mol)` → UInt_Vect (42 values, create columns MQN_1..MQN_42)
  4. `mol.delete()` in finally
  5. Write output row with all original columns + computed descriptor columns (prefixed if configured)
- Thinking: Most complex single node. Use a when/switch on descriptor name to call the right RDKit function. For vector descriptors (VSA, MQN), expand into multiple output columns. Consider creating a helper function `computeDescriptor(mol, descriptorName): Map<String, Any>` that returns one or more column values. Mirrors KNIME DescriptorCalculationNodeModel.
- Category: ["RDKit", "Calculators"]
- Example Properties: {
    "smilesColumn": "smiles",
    "descriptors": ["AMW", "SlogP", "TPSA", "NumHBD", "NumHBA", "NumRotatableBonds", "NumRings"],
    "columnPrefix": ""
  }
- Detailed Example Input: [
    {"smiles": "CC(=O)Oc1ccccc1C(=O)O", "name": "aspirin"},
    {"smiles": "c1ccccc1", "name": "benzene"}
  ]
- Detailed Example Output: [
    {"smiles": "CC(=O)Oc1ccccc1C(=O)O", "name": "aspirin", "AMW": 180.16, "SlogP": 1.31, "TPSA": 63.6, "NumHBD": 1, "NumHBA": 4, "NumRotatableBonds": 3, "NumRings": 1},
    {"smiles": "c1ccccc1", "name": "benzene", "AMW": 78.11, "SlogP": 1.69, "TPSA": 0.0, "NumHBD": 0, "NumHBA": 0, "NumRotatableBonds": 0, "NumRings": 1}
  ]

---

## Create CalculateChargesNodeModel
- One input port "input" Table containing molecules as SMILES strings
- Properties:
  - smilesColumn (string, required — column containing SMILES)
  - chargesColumnName (string, default "gasteiger_charges" — output column for charge list)
- Output port "output" Table with original columns plus a new column containing a JSON array of Gasteiger charges (one per atom)
- UI Schema: React Material JSONForm VerticalLayout with controls for smilesColumn, chargesColumnName
- Behavior:
  1. Read properties, validate smilesColumn
  2. For each row, parse SMILES with `RDKFuncs.SmilesToMol(smiles)`
  3. Compute Gasteiger charges: create `Double_Vect()`, call `mol.computeGasteigerCharges(chargesVec)`
  4. Extract charges into a `List<Double>` by iterating the Double_Vect
  5. `mol.delete()` in finally
  6. Write output row with charges as JSON array string (e.g., "[-0.28, 0.15, -0.33, ...]")
- Thinking: Gasteiger charges are atomic-level partial charges useful for electrostatic analysis. The output is a list of doubles, one per atom in the molecule. Store as JSON array string since Continuum rows use primitives. Mirrors KNIME RDKitCalculateCharges.
- Category: ["RDKit", "Calculators"]
- Example Properties: {
    "smilesColumn": "smiles",
    "chargesColumnName": "gasteiger_charges"
  }
- Detailed Example Input: [
    {"smiles": "O", "name": "water"},
    {"smiles": "CC", "name": "ethane"}
  ]
- Detailed Example Output: [
    {"smiles": "O", "name": "water", "gasteiger_charges": "[-0.411]"},
    {"smiles": "CC", "name": "ethane", "gasteiger_charges": "[-0.054, -0.054]"}
  ]
