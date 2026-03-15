# RDKit Testing Nodes (1 node)

> Read `00-preamble.md` first for the full Continuum node pattern, RDKit API rules, and shared utilities.

---

## Create SDFDifferenceCheckerNodeModel
- Two input ports:
  - "left" Table containing molecules (with SDF MolBlock column or SMILES)
  - "right" Table containing molecules (with SDF MolBlock column or SMILES)
- Properties:
  - leftSmilesColumn (string, required — SMILES or MolBlock column in left table)
  - rightSmilesColumn (string, required — SMILES or MolBlock column in right table)
  - compareCoordinates (boolean, default false — also compare 2D/3D coordinates)
  - compareProperties (boolean, default true — compare molecular properties)
  - toleranceForCoordinates (number, default 0.001 — tolerance for coordinate comparison)
  - outputDifferencesOnly (boolean, default true — if true, only output rows with differences)
  - differenceColumnName (string, default "differences")
  - matchColumnName (string, default "match_status" — "match", "mismatch", "left_only", "right_only")
- Output port "output" Table with comparison results: all columns from both tables + match_status + differences description
- UI Schema: React Material JSONForm VerticalLayout with controls for all properties
- Behavior:
  1. Read properties, validate both columns
  2. Read ALL molecules from both tables into lists
  3. For each row pair (matched by index):
     - Parse both molecules (SMILES or MolBlock)
     - Compare canonical SMILES: `RDKFuncs.MolToSmiles(left)` vs `RDKFuncs.MolToSmiles(right)`
     - If compareCoordinates: compare atom coordinates within tolerance
     - If compareProperties: compare molecular descriptors (MW, formula, etc.)
     - Determine match_status:
       - "match" — identical molecules
       - "mismatch" — different molecules
       - "left_only" — row exists only in left
       - "right_only" — row exists only in right
     - Build differences description string
  4. Delete all molecules in finally
  5. Write output row(s) based on outputDifferencesOnly flag
- Thinking: QA/validation node. Compares two SDF-derived tables for consistency. Index-matched comparison (row 0 vs row 0). Important for regression testing of cheminformatics pipelines. Coordinate comparison uses tolerance for floating-point differences. Mirrors KNIME RDKitSDFDifferenceChecker.
- Category: ["RDKit", "Testing"]
- Example Properties: {
    "leftSmilesColumn": "smiles",
    "rightSmilesColumn": "smiles",
    "compareCoordinates": false,
    "compareProperties": true,
    "toleranceForCoordinates": 0.001,
    "outputDifferencesOnly": false,
    "differenceColumnName": "differences",
    "matchColumnName": "match_status"
  }
- Detailed Example Input:
  Left table: [
    {"smiles": "c1ccccc1", "name": "benzene"},
    {"smiles": "CCO", "name": "ethanol"},
    {"smiles": "CC(=O)O", "name": "acetic_acid"}
  ]
  Right table: [
    {"smiles": "c1ccccc1", "name": "benzene"},
    {"smiles": "CCCO", "name": "propanol"}
  ]
- Detailed Example Output: [
    {"left_smiles": "c1ccccc1", "right_smiles": "c1ccccc1", "name": "benzene", "match_status": "match", "differences": ""},
    {"left_smiles": "CCO", "right_smiles": "CCCO", "name": "ethanol", "match_status": "mismatch", "differences": "SMILES differ: CCO vs CCCO; MW differ: 46.07 vs 60.10"},
    {"left_smiles": "CC(=O)O", "right_smiles": "", "name": "acetic_acid", "match_status": "left_only", "differences": "No corresponding row in right table"}
  ]
