# RDKit Converters Nodes (5 nodes)

> Read `00-preamble.md` first for the full Continuum node pattern, RDKit API rules, and shared utilities.

---

## Create CanonicalSmilesNodeModel
- One input port "input" Table containing molecules as SMILES strings
- Properties: smilesColumn (string, required — column containing SMILES), newColumnName (string, default "canonical_smiles" — name for the output column), removeSourceColumn (boolean, default false — whether to remove the original SMILES column)
- Output port "output" Table with original columns plus a new column containing canonical SMILES
- UI Schema: React Material JSONForm VerticalLayout with controls for smilesColumn, newColumnName, removeSourceColumn
- Behavior:
  1. Read `smilesColumn` property (required)
  2. For each row, read the SMILES string from `row[smilesColumn]`
  3. Parse with `RDKFuncs.SmilesToMol(smiles)` — if null, write empty string for canonical column
  4. Convert to canonical SMILES with `RDKFuncs.MolToSmiles(mol)`
  5. Call `mol.delete()` in finally block
  6. Write output row: all original columns (optionally removing source) + new canonical SMILES column
  7. Report progress as percentage
- Thinking: Simplest RDKit node. Parse SMILES, re-serialize as canonical. Validates the basic SMILES→ROMol→SMILES round-trip pattern used by all other nodes.
- Category: ["RDKit", "Converters"]
- Example Properties: {
    "smilesColumn": "smiles",
    "newColumnName": "canonical_smiles",
    "removeSourceColumn": false
  }
- Detailed Example Input: [
    {"smiles": "OC(=O)c1ccccc1OC(C)=O"},
    {"smiles": "c1ccccc1"},
    {"smiles": "INVALID_SMILES"}
  ]
- Detailed Example Output: [
    {"smiles": "OC(=O)c1ccccc1OC(C)=O", "canonical_smiles": "CC(=O)Oc1ccccc1C(=O)O"},
    {"smiles": "c1ccccc1", "canonical_smiles": "c1ccccc1"},
    {"smiles": "INVALID_SMILES", "canonical_smiles": ""}
  ]

---

## Create SmilesParserNodeModel
- One input port "input" Table containing molecules as SMILES, SDF, or SMARTS strings
- Properties:
  - smilesColumn (string, required — column containing molecule strings)
  - inputFormat (string, enum: ["SMILES", "SDF", "SMARTS"], default "SMILES" — format of the input)
  - newColumnName (string, default "parsed_smiles" — output column name for canonical SMILES)
  - removeSourceColumn (boolean, default false)
  - generateCoordinates (boolean, default false — whether to generate 2D coordinates and output as MolBlock)
  - coordinatesColumnName (string, default "mol_block" — column for MolBlock if coordinates generated)
  - sanitize (boolean, default true — whether to sanitize the molecule)
  - addErrorColumn (boolean, default true — add a column with parse error messages)
  - errorColumnName (string, default "parse_error")
- Output ports:
  - "output" Table with parsed molecules (all original columns + canonical SMILES + optional MolBlock + optional error column)
  - "errors" Table with rows that failed parsing
- UI Schema: React Material JSONForm VerticalLayout with controls for all properties. Show coordinatesColumnName only when generateCoordinates is true. Show errorColumnName only when addErrorColumn is true.
- Behavior:
  1. Read all properties, validate smilesColumn is provided
  2. For each row, read the molecule string from `row[smilesColumn]`
  3. Parse based on inputFormat:
     - SMILES: `RDKFuncs.SmilesToMol(str)`
     - SDF: `RDKFuncs.MolBlockToMol(str)` with sanitize option
     - SMARTS: `RDKFuncs.SmartsToMol(str)`
  4. If parse succeeds:
     - Get canonical SMILES via `RDKFuncs.MolToSmiles(mol)`
     - Optionally generate 2D coords: `RDKFuncs.compute2DCoords(mol)` then `RDKFuncs.MolToMolBlock(mol)`
     - Write to "output" port with all original columns + new columns
  5. If parse fails:
     - Write to "errors" port with original row + error message column
  6. Always `mol?.delete()` in finally
  7. Report progress
- Thinking: Mirrors KNIME Molecule2RDKitConverter. This is the main entry-point node for getting molecules into the Continuum RDKit pipeline. Support multiple input formats. The dual-output (success/error) pattern is important for data quality.
- Category: ["RDKit", "Converters"]
- Example Properties: {
    "smilesColumn": "molecule",
    "inputFormat": "SMILES",
    "newColumnName": "canonical_smiles",
    "removeSourceColumn": false,
    "generateCoordinates": false,
    "sanitize": true,
    "addErrorColumn": true,
    "errorColumnName": "parse_error"
  }
- Detailed Example Input: [
    {"molecule": "CC(=O)Oc1ccccc1C(=O)O", "name": "aspirin"},
    {"molecule": "NOT_A_SMILES", "name": "bad"},
    {"molecule": "Cn1c(=O)c2c(ncn2C)n(C)c1=O", "name": "caffeine"}
  ]
- Detailed Example Output (port "output"): [
    {"molecule": "CC(=O)Oc1ccccc1C(=O)O", "name": "aspirin", "canonical_smiles": "CC(=O)Oc1ccccc1C(=O)O", "parse_error": ""},
    {"molecule": "Cn1c(=O)c2c(ncn2C)n(C)c1=O", "name": "caffeine", "canonical_smiles": "Cn1c(=O)c2c(ncn2C)n(C)c1=O", "parse_error": ""}
  ]
- Detailed Example Output (port "errors"): [
    {"molecule": "NOT_A_SMILES", "name": "bad", "parse_error": "Failed to parse SMILES: NOT_A_SMILES"}
  ]

---

## Create MoleculeWriterNodeModel
- One input port "input" Table containing molecules as SMILES strings
- Properties:
  - smilesColumn (string, required — column containing SMILES)
  - outputFormat (string, enum: ["SMILES", "SDF", "SMARTS"], default "SMILES")
  - newColumnName (string, default "converted")
  - removeSourceColumn (boolean, default false)
- Output port "output" Table with original columns plus converted molecule string
- UI Schema: React Material JSONForm VerticalLayout with controls for smilesColumn, outputFormat, newColumnName, removeSourceColumn
- Behavior:
  1. Read properties, validate smilesColumn
  2. For each row, parse SMILES with `RDKFuncs.SmilesToMol(smiles)`
  3. Convert based on outputFormat:
     - SMILES: `RDKFuncs.MolToSmiles(mol)` (canonical)
     - SDF: generate 2D coords if needed (`RDKFuncs.compute2DCoords(mol)`), then `RDKFuncs.MolToMolBlock(mol)`
     - SMARTS: `RDKFuncs.MolToSmarts(mol)`
  4. `mol.delete()` in finally
  5. Write output row with converted string
- Thinking: Reverse of SmilesParser — takes internal SMILES representation and outputs in user-chosen format. SDF requires 2D coordinates, so generate them on the fly. Mirrors KNIME RDKit2MoleculeConverter.
- Category: ["RDKit", "Converters"]
- Example Properties: {
    "smilesColumn": "smiles",
    "outputFormat": "SDF",
    "newColumnName": "mol_block",
    "removeSourceColumn": false
  }
- Detailed Example Input: [
    {"smiles": "c1ccccc1", "name": "benzene"},
    {"smiles": "CC(=O)O", "name": "acetic acid"}
  ]
- Detailed Example Output: [
    {"smiles": "c1ccccc1", "name": "benzene", "mol_block": "\n     RDKit          2D\n\n  6  6  0  0  0  0  0  0  0  0999 V2000\n...M  END\n"},
    {"smiles": "CC(=O)O", "name": "acetic acid", "mol_block": "\n     RDKit          2D\n\n  4  3  0  0  0  0  0  0  0  0999 V2000\n...M  END\n"}
  ]

---

## Create InChIToMoleculeNodeModel
- One input port "input" Table containing InChI strings
- Properties:
  - inchiColumn (string, required — column containing InChI strings)
  - newColumnName (string, default "smiles" — output column for canonical SMILES)
  - removeSourceColumn (boolean, default false)
  - sanitize (boolean, default true)
  - removeHydrogens (boolean, default true)
  - addReturnCodeColumn (boolean, default false)
  - returnCodeColumnName (string, default "inchi_return_code")
  - addMessageColumn (boolean, default false)
  - messageColumnName (string, default "inchi_message")
- Output port "output" Table with canonical SMILES from InChI
- UI Schema: React Material JSONForm VerticalLayout with controls for all properties
- Behavior:
  1. Read properties, validate inchiColumn
  2. For each row, read InChI string from `row[inchiColumn]`
  3. Convert with synchronized block: `RDKFuncs.InchiToMol(inchi, extraInfo, sanitize, removeHydrogens)` where `extraInfo = ExtraInchiReturnValues()`
  4. If mol is valid, get canonical SMILES: `RDKFuncs.MolToSmiles(mol)`
  5. Optionally extract return code via `extraInfo.getReturnCode()`, message via `extraInfo.getMessagePtr()`
  6. `mol.delete()` in finally
  7. Write output row with SMILES + optional info columns
- Thinking: InChI operations require synchronization (thread safety). Use a companion object lock. Mirrors KNIME RDKitInChI2Molecule. The ExtraInchiReturnValues provides diagnostic info.
- Category: ["RDKit", "Converters"]
- Example Properties: {
    "inchiColumn": "inchi",
    "newColumnName": "smiles",
    "removeSourceColumn": false,
    "sanitize": true,
    "removeHydrogens": true,
    "addReturnCodeColumn": false,
    "addMessageColumn": false
  }
- Detailed Example Input: [
    {"inchi": "InChI=1S/C6H6/c1-2-4-6-5-3-1/h1-6H", "name": "benzene"},
    {"inchi": "InChI=1S/C9H8O4/c1-6(10)13-8-5-3-2-4-7(8)9(11)12/h2-5H,1H3,(H,11,12)", "name": "aspirin"}
  ]
- Detailed Example Output: [
    {"inchi": "InChI=1S/C6H6/c1-2-4-6-5-3-1/h1-6H", "name": "benzene", "smiles": "c1ccccc1"},
    {"inchi": "InChI=1S/C9H8O4/c1-6(10)13-8-5-3-2-4-7(8)9(11)12/h2-5H,1H3,(H,11,12)", "name": "aspirin", "smiles": "CC(=O)Oc1ccccc1C(=O)O"}
  ]

---

## Create MoleculeToInChINodeModel
- One input port "input" Table containing molecules as SMILES strings
- Properties:
  - smilesColumn (string, required — column containing SMILES)
  - newInChIColumnName (string, default "inchi" — output column for InChI string)
  - removeSourceColumn (boolean, default false)
  - generateInChIKey (boolean, default true)
  - inchiKeyColumnName (string, default "inchi_key")
  - addReturnCodeColumn (boolean, default false)
  - returnCodeColumnName (string, default "inchi_return_code")
  - addAuxInfoColumn (boolean, default false)
  - auxInfoColumnName (string, default "inchi_aux_info")
  - advancedOptions (string, default "" — additional InChI generation options string)
- Output port "output" Table with InChI and optional InChI Key columns
- UI Schema: React Material JSONForm VerticalLayout with controls for all properties. Show inchiKeyColumnName only when generateInChIKey is true.
- Behavior:
  1. Read properties, validate smilesColumn
  2. For each row, parse SMILES with `RDKFuncs.SmilesToMol(smiles)`
  3. Convert with synchronized block:
     - If advancedOptions is empty: `RDKFuncs.MolToInchi(mol, extraInfo)`
     - If advancedOptions is set: `RDKFuncs.MolToInchi(mol, extraInfo, advancedOptions)`
  4. Optionally generate InChI Key: `RDKFuncs.InchiToInchiKey(inchi)`
  5. `mol.delete()` in finally
  6. Write output row with InChI + optional InChI Key + optional return code/aux info
- Thinking: Mirrors KNIME RDKitMolecule2InChI. InChI operations need synchronized blocks for thread safety. The advancedOptions string allows fine-tuning InChI generation (e.g., stereo handling).
- Category: ["RDKit", "Converters"]
- Example Properties: {
    "smilesColumn": "smiles",
    "newInChIColumnName": "inchi",
    "removeSourceColumn": false,
    "generateInChIKey": true,
    "inchiKeyColumnName": "inchi_key",
    "advancedOptions": ""
  }
- Detailed Example Input: [
    {"smiles": "c1ccccc1", "name": "benzene"},
    {"smiles": "CC(=O)Oc1ccccc1C(=O)O", "name": "aspirin"}
  ]
- Detailed Example Output: [
    {"smiles": "c1ccccc1", "name": "benzene", "inchi": "InChI=1S/C6H6/c1-2-4-6-5-3-1/h1-6H", "inchi_key": "UHOVQNZJYSORNB-UHFFFAOYSA-N"},
    {"smiles": "CC(=O)Oc1ccccc1C(=O)O", "name": "aspirin", "inchi": "InChI=1S/C9H8O4/c1-6(10)13-8-5-3-2-4-7(8)9(11)12/h2-5H,1H3,(H,11,12)", "inchi_key": "BSYNRYMUTXBXSQ-UHFFFAOYSA-N"}
  ]
