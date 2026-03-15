# RDKit Experimental Nodes (4 nodes)

> Read `00-preamble.md` first for the full Continuum node pattern, RDKit API rules, and shared utilities.

---

## Create RGroupDecompositionNodeModel
- One input port "input" Table containing molecules as SMILES strings
- Properties:
  - smilesColumn (string, required — column containing SMILES)
  - coreSmarts (string, required — SMARTS pattern defining the core scaffold for decomposition)
  - coreColumnName (string, default "core")
  - rGroupPrefix (string, default "R" — prefix for R-group columns, produces R1, R2, R3...)
  - matchOnlyAtRGroups (boolean, default false — only decompose at marked R-group positions)
  - removeSourceColumn (boolean, default false)
- Output port "output" Table with original columns + core SMILES column + R1, R2, R3... columns (one per R-group position found)
- UI Schema: React Material JSONForm VerticalLayout with controls for smilesColumn, coreSmarts, coreColumnName, rGroupPrefix, matchOnlyAtRGroups, removeSourceColumn
- Behavior:
  1. Read properties, validate smilesColumn and coreSmarts
  2. Parse core SMARTS: `RDKFuncs.SmartsToMol(coreSmarts)` — throw if invalid
  3. Read ALL molecules first (R-group decomposition works best on the full set):
     - Parse all SMILES into ROMol list
  4. Create RGroupDecomposition:
     - `val rgd = RGroupDecomposition(coreMol)`
     - For each molecule: `rgd.add(mol)` — returns -1 if molecule doesn't match core
     - `rgd.process()` — finalize decomposition
  5. Extract results:
     - `rgd.getRGroupsAsRows()` → list of maps with "Core", "R1", "R2", etc.
     - For each result, convert ROMol values to SMILES
  6. Write output rows:
     - Molecules that match: original columns + core SMILES + R-group SMILES columns
     - Molecules that don't match the core: pass through with empty R-group columns
  7. Delete all molecules, core, and RGroupDecomposition in finally
- Thinking: R-group decomposition is a key SAR (Structure-Activity Relationship) analysis tool. Given a core scaffold, it identifies what R-groups are attached at each position. The number of R-group columns is dynamic (depends on the core). Mirrors KNIME RDKitRGroupDecomposition.
- Category: ["RDKit", "Experimental"]
- Example Properties: {
    "smilesColumn": "smiles",
    "coreSmarts": "c1ccc([*:1])cc1[*:2]",
    "coreColumnName": "core",
    "rGroupPrefix": "R",
    "matchOnlyAtRGroups": false,
    "removeSourceColumn": false
  }
- Detailed Example Input: [
    {"smiles": "c1ccc(O)cc1N", "name": "2-aminophenol"},
    {"smiles": "c1ccc(C)cc1Cl", "name": "2-chlorotoluene"},
    {"smiles": "CCCCCC", "name": "hexane"}
  ]
- Detailed Example Output: [
    {"smiles": "c1ccc(O)cc1N", "name": "2-aminophenol", "core": "c1ccc(-*)cc1-*", "R1": "O", "R2": "N"},
    {"smiles": "c1ccc(C)cc1Cl", "name": "2-chlorotoluene", "core": "c1ccc(-*)cc1-*", "R1": "C", "R2": "Cl"},
    {"smiles": "CCCCCC", "name": "hexane", "core": "", "R1": "", "R2": ""}
  ]

---

## Create StructureNormalizerNodeModel
- One input port "input" Table containing molecules as SMILES strings
- Properties:
  - smilesColumn (string, required — column containing SMILES)
  - newColumnName (string, default "normalized_smiles")
  - removeSourceColumn (boolean, default false)
  - normalizationSteps (array of string, enum: ["RemoveFragments", "Neutralize", "Canonicalize", "RemoveIsotopes", "RemoveStereo", "ReionizeMetal", "Cleanup"], default ["RemoveFragments", "Neutralize", "Canonicalize"])
- Output port "output" Table with original columns plus normalized SMILES
- UI Schema: React Material JSONForm VerticalLayout with:
  - Control for smilesColumn
  - Multi-select control for normalizationSteps
  - Controls for newColumnName, removeSourceColumn
- Behavior:
  1. Read properties, validate smilesColumn
  2. For each row, parse SMILES
  3. Apply selected normalization steps in order:
     - **RemoveFragments**: Keep largest fragment only (same as SaltStripper — `RDKFuncs.getMolFrags()`, keep largest)
     - **Neutralize**: Apply charge neutralization transforms:
       - `[N+:1](=O)[O-:2]>>[N+0:1](=O)[O-0:2]` (nitro)
       - `[NH3+:1]>>[NH2:1]` (ammonium to amine)
       - `[O-:1]>>[OH:1]` (carboxylate to acid)
     - **Canonicalize**: `RDKFuncs.MolToSmiles(mol)` then re-parse
     - **RemoveIsotopes**: For each atom, `atom.setIsotope(0)`
     - **RemoveStereo**: `RDKFuncs.removeStereochemistry(mol)`
     - **ReionizeMetal**: Standardize metal-organic salts
     - **Cleanup**: `RDKFuncs.cleanup(mol)` / `RDKFuncs.sanitizeMol(mol)`
  4. Convert to SMILES: `RDKFuncs.MolToSmiles(mol)`
  5. `mol.delete()` in finally
  6. Write output row
- Thinking: Structure standardization is critical for database registration and deduplication. The normalization steps are composable — users pick which ones to apply. Each step is independent. Mirrors KNIME RDKitStructureNormalizerV2. The order matters: remove fragments before neutralizing.
- Category: ["RDKit", "Experimental"]
- Example Properties: {
    "smilesColumn": "smiles",
    "newColumnName": "normalized_smiles",
    "removeSourceColumn": false,
    "normalizationSteps": ["RemoveFragments", "Neutralize", "Canonicalize"]
  }
- Detailed Example Input: [
    {"smiles": "CC(=O)[O-].[Na+]", "name": "sodium_acetate"},
    {"smiles": "c1ccc([NH3+])cc1.[Cl-]", "name": "aniline_hcl"},
    {"smiles": "c1ccccc1", "name": "benzene"}
  ]
- Detailed Example Output: [
    {"smiles": "CC(=O)[O-].[Na+]", "name": "sodium_acetate", "normalized_smiles": "CC(=O)O"},
    {"smiles": "c1ccc([NH3+])cc1.[Cl-]", "name": "aniline_hcl", "normalized_smiles": "Nc1ccccc1"},
    {"smiles": "c1ccccc1", "name": "benzene", "normalized_smiles": "c1ccccc1"}
  ]

---

## Create MoleculeCatalogFilterNodeModel
- One input port "input" Table containing molecules as SMILES strings
- Properties:
  - smilesColumn (string, required — column containing SMILES)
  - catalogs (array of string, required, enum: ["PAINS_A", "PAINS_B", "PAINS_C", "BRENK", "NIH", "ZINC", "ALL"], default ["PAINS_A"] — filter catalogs to check against)
  - addMatchDetailsColumn (boolean, default true)
  - matchDetailsColumnName (string, default "catalog_matches")
  - addMatchCountColumn (boolean, default true)
  - matchCountColumnName (string, default "catalog_match_count")
- Output ports (SPLITTER — 2 outputs):
  - "clean" Table with molecules that pass all catalog filters (no matches)
  - "flagged" Table with molecules that match one or more catalog entries
- UI Schema: React Material JSONForm VerticalLayout with:
  - Control for smilesColumn
  - Multi-select control for catalogs
  - Controls for addMatchDetailsColumn, matchDetailsColumnName, addMatchCountColumn, matchCountColumnName
- Behavior:
  1. Read properties, validate smilesColumn and catalogs
  2. Initialize FilterCatalog for each selected catalog type:
     - `val catalogParams = FilterCatalogParams()`
     - For each catalog: `catalogParams.addCatalog(FilterCatalogParams.FilterCatalogs.PAINS_A)` etc.
     - `val filterCatalog = FilterCatalog(catalogParams)`
  3. For each row:
     - Parse SMILES: `RDKFuncs.SmilesToMol(smiles)`
     - Get matches: `filterCatalog.getMatches(mol)` → `FilterCatalogEntry_Vect`
     - If no matches → write to "clean"
     - If matches → write to "flagged" with match details (catalog name, rule name, description)
     - `mol.delete()` in finally
  4. Delete filterCatalog in finally
- Thinking: Structural alert filtering. PAINS (Pan-Assay INterference compounds) are the most common — molecules with patterns that cause false positives in HTS. BRENK contains undesirable functional groups. The FilterCatalog API provides pre-built catalogs. Mirrors KNIME RDKitMoleculeCatalogFilter.
- Category: ["RDKit", "Experimental"]
- Example Properties: {
    "smilesColumn": "smiles",
    "catalogs": ["PAINS_A", "PAINS_B", "PAINS_C"],
    "addMatchDetailsColumn": true,
    "matchDetailsColumnName": "pains_matches",
    "addMatchCountColumn": true,
    "matchCountColumnName": "pains_count"
  }
- Detailed Example Input: [
    {"smiles": "c1ccccc1", "name": "benzene"},
    {"smiles": "O=C1C=CC(=O)C=C1", "name": "quinone"},
    {"smiles": "c1ccc2c(c1)ccc1ccccc12", "name": "naphthalene"}
  ]
- Detailed Example Output (port "clean"): [
    {"smiles": "c1ccccc1", "name": "benzene"},
    {"smiles": "c1ccc2c(c1)ccc1ccccc12", "name": "naphthalene"}
  ]
- Detailed Example Output (port "flagged"): [
    {"smiles": "O=C1C=CC(=O)C=C1", "name": "quinone", "pains_matches": "quinone_A(370)", "pains_count": 1}
  ]

---

## Create AdjustQueryPropertiesNodeModel
- One input port "input" Table containing molecules as SMILES strings
- Properties:
  - smilesColumn (string, required — column containing SMILES)
  - newColumnName (string, default "adjusted_query_smarts")
  - removeSourceColumn (boolean, default false)
  - adjustDegree (boolean, default true — add degree queries to atoms)
  - adjustRingCount (boolean, default true — add ring count queries)
  - makeDummiesQueries (boolean, default true — convert dummy atoms to queries)
  - aromatize (boolean, default true — apply aromaticity before adjusting)
  - makeAtomsGeneric (boolean, default false — replace atom types with any-atom queries)
  - makeBondsGeneric (boolean, default false — replace bond types with any-bond queries)
- Output port "output" Table with original columns plus adjusted SMARTS column
- UI Schema: React Material JSONForm VerticalLayout with controls for all boolean properties + smilesColumn, newColumnName, removeSourceColumn
- Behavior:
  1. Read properties, validate smilesColumn
  2. For each row, parse SMILES with `RDKFuncs.SmilesToMol(smiles)`
  3. Create AdjustQueryParameters:
     - `val params = AdjustQueryParameters()`
     - `params.setAdjustDegree(adjustDegree)`
     - `params.setAdjustRingCount(adjustRingCount)`
     - `params.setMakeDummiesQueries(makeDummiesQueries)`
     - `params.setAromatizeIfPossible(aromatize)`
     - `params.setMakeAtomsGeneric(makeAtomsGeneric)`
     - `params.setMakeBondsGeneric(makeBondsGeneric)`
  4. Adjust query: `val adjustedMol = RDKFuncs.adjustQueryProperties(mol, params)` → returns new ROMol
  5. Convert to SMARTS: `RDKFuncs.MolToSmarts(adjustedMol)`
  6. `mol.delete()` and `adjustedMol.delete()` in finally
  7. Write output row with SMARTS
- Thinking: Converts a molecule to a flexible substructure query by adding degree, ring count, and other query features to atoms. The resulting SMARTS is more specific than naive MolToSmarts but more flexible than exact match. Used in query-based virtual screening. Mirrors KNIME RDKitAdjustQueryProperties.
- Category: ["RDKit", "Experimental"]
- Example Properties: {
    "smilesColumn": "smiles",
    "newColumnName": "query_smarts",
    "adjustDegree": true,
    "adjustRingCount": true,
    "makeDummiesQueries": true,
    "aromatize": true,
    "makeAtomsGeneric": false,
    "makeBondsGeneric": false
  }
- Detailed Example Input: [
    {"smiles": "c1ccccc1O", "name": "phenol"},
    {"smiles": "CC(=O)O", "name": "acetic_acid"}
  ]
- Detailed Example Output: [
    {"smiles": "c1ccccc1O", "name": "phenol", "query_smarts": "[#6]1:[#6]:[#6]:[#6]:[#6]:[#6]:1-[#8]"},
    {"smiles": "CC(=O)O", "name": "acetic_acid", "query_smarts": "[#6]-[#6](=[#8])-[#8]"}
  ]
