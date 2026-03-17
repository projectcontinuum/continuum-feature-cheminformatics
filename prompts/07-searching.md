# RDKit Searching Nodes (5 nodes)

> Read `00-preamble.md` first for the full Continuum node pattern, RDKit API rules, and shared utilities.

---

## Create SubstructFilterNodeModel
- One input port "input" Table containing molecules as SMILES strings
- Properties:
  - smilesColumn (string, required — column containing SMILES)
  - smartsQuery (string, required — SMARTS pattern for substructure matching)
  - useChirality (boolean, default false — consider stereochemistry in matching)
  - addMatchColumn (boolean, default false — add column with matched atom indices)
  - matchColumnName (string, default "match_atoms")
- Output ports (SPLITTER — 2 outputs):
  - "match" Table with rows that contain the substructure
  - "noMatch" Table with rows that do not contain the substructure
- UI Schema: React Material JSONForm VerticalLayout with controls for smilesColumn, smartsQuery, useChirality, addMatchColumn. Show matchColumnName only when addMatchColumn is true.
- Behavior:
  1. Read properties, validate smilesColumn and smartsQuery
  2. Parse SMARTS query once: `val query = RDKFuncs.SmartsToMol(smartsQuery)` — throw error if invalid
  3. For each row:
     - Parse SMILES: `RDKFuncs.SmilesToMol(smiles)`
     - Check match: `mol.hasSubstructMatch(query)`
     - If addMatchColumn, get match details: `mol.getSubstructMatches(query)` → extract atom indices
     - If match: write to "match" port (with optional match atom indices)
     - If no match: write to "noMatch" port
     - `mol.delete()` in finally
  4. `query.delete()` after all rows processed
- Thinking: Core substructure filtering node. SMARTS pattern defines the query. The splitter pattern (match/noMatch) is very common in cheminformatics workflows. The match atoms column helps with downstream highlighting/analysis. Mirrors KNIME RDKitSubstructFilter.
- Category: ["RDKit", "Searching"]
- Example Properties: {
    "smilesColumn": "smiles",
    "smartsQuery": "[OH]",
    "useChirality": false,
    "addMatchColumn": true,
    "matchColumnName": "match_atoms"
  }
- Detailed Example Input: [
    {"smiles": "c1ccccc1O", "name": "phenol"},
    {"smiles": "c1ccccc1", "name": "benzene"},
    {"smiles": "CCO", "name": "ethanol"}
  ]
- Detailed Example Output (port "match"): [
    {"smiles": "c1ccccc1O", "name": "phenol", "match_atoms": "[[6]]"},
    {"smiles": "CCO", "name": "ethanol", "match_atoms": "[[2]]"}
  ]
- Detailed Example Output (port "noMatch"): [
    {"smiles": "c1ccccc1", "name": "benzene"}
  ]

---

## Create MoleculeSubstructFilterNodeModel
- Two input ports:
  - "molecules" Table containing molecules as SMILES
  - "queries" Table containing query molecules as SMILES (used as substructure patterns)
- Properties:
  - smilesColumn (string, required — SMILES column in molecules table)
  - querySmilesColumn (string, required — SMILES column in queries table)
  - matchMode (string, enum: ["any", "all"], default "any" — match ANY query or ALL queries)
  - useChirality (boolean, default false)
- Output ports (SPLITTER — 2 outputs):
  - "match" Table with molecules matching the query condition
  - "noMatch" Table with molecules not matching
- UI Schema: React Material JSONForm VerticalLayout with controls for all properties
- Behavior:
  1. Read properties, validate both SMILES columns
  2. Read ALL query molecules first into a list:
     - For each query row, parse SMILES into ROMol and store (delete later)
  3. For each molecule row:
     - Parse SMILES: `RDKFuncs.SmilesToMol(smiles)`
     - For each query: `mol.hasSubstructMatch(queryMol)`
     - If matchMode is "any": match if ANY query matches
     - If matchMode is "all": match if ALL queries match
     - Write to "match" or "noMatch" accordingly
     - `mol.delete()` in finally
  4. Delete all query molecules in finally
- Thinking: Like SubstructFilter but queries come from a second input table instead of a SMARTS string. Reads all queries first, then tests each molecule. Common for library screening against a panel of pharmacophore patterns. Mirrors KNIME RDKitMoleculeSubstructFilter.
- Category: ["RDKit", "Searching"]
- Example Properties: {
    "smilesColumn": "smiles",
    "querySmilesColumn": "query_smiles",
    "matchMode": "any",
    "useChirality": false
  }
- Detailed Example Input:
  Molecules table: [
    {"smiles": "c1ccccc1O", "name": "phenol"},
    {"smiles": "c1ccccc1", "name": "benzene"},
    {"smiles": "c1ccc(O)cc1N", "name": "aminophenol"}
  ]
  Queries table: [
    {"query_smiles": "c1ccccc1O"},
    {"query_smiles": "c1ccccc1N"}
  ]
- Detailed Example Output (port "match"): [
    {"smiles": "c1ccccc1O", "name": "phenol"},
    {"smiles": "c1ccc(O)cc1N", "name": "aminophenol"}
  ]
- Detailed Example Output (port "noMatch"): [
    {"smiles": "c1ccccc1", "name": "benzene"}
  ]

---

## Create FunctionalGroupFilterNodeModel
- One input port "input" Table containing molecules as SMILES strings
- Properties:
  - smilesColumn (string, required — column containing SMILES)
  - functionalGroups (array of objects, required — list of functional group conditions, each with: name (string), smarts (string), minCount (integer, default 0), maxCount (integer, default -1 meaning unlimited))
  - addFailedPatternColumn (boolean, default false — add column listing which patterns failed)
  - failedPatternColumnName (string, default "failed_patterns")
- Output ports (SPLITTER — 2 outputs):
  - "pass" Table with molecules passing all functional group conditions
  - "fail" Table with molecules failing one or more conditions
- UI Schema: React Material JSONForm VerticalLayout with:
  - Control for smilesColumn
  - Array control for functionalGroups with detail layout for name, smarts, minCount, maxCount
  - Controls for addFailedPatternColumn, failedPatternColumnName
- Behavior:
  1. Read properties, validate smilesColumn and functionalGroups
  2. Pre-parse all SMARTS patterns: `RDKFuncs.SmartsToMol(smarts)` for each group
  3. For each row:
     - Parse SMILES: `RDKFuncs.SmilesToMol(smiles)`
     - For each functional group condition:
       - Count matches: `mol.getSubstructMatches(pattern).size()`
       - Check: count >= minCount AND (maxCount < 0 OR count <= maxCount)
     - If ALL conditions pass → write to "pass" port
     - If ANY condition fails → write to "fail" port (with optional failed patterns list)
     - `mol.delete()` in finally
  4. Delete all pattern mols in finally
- Thinking: Flexible functional group filter. Users define their own groups with SMARTS and count constraints. Common preset groups: amide (`[NX3][CX3](=[OX1])`) min 0 max 0 = "no amides". Mirrors KNIME FunctionalGroupFilterV2. The array property allows dynamic group definitions.
- Category: ["RDKit", "Searching"]
- Example Properties: {
    "smilesColumn": "smiles",
    "functionalGroups": [
      {"name": "Hydroxyl", "smarts": "[OH]", "minCount": 1, "maxCount": -1},
      {"name": "Nitro", "smarts": "[N+](=O)[O-]", "minCount": 0, "maxCount": 0}
    ],
    "addFailedPatternColumn": true,
    "failedPatternColumnName": "failed_patterns"
  }
- Detailed Example Input: [
    {"smiles": "c1ccccc1O", "name": "phenol"},
    {"smiles": "c1ccc([N+](=O)[O-])cc1O", "name": "nitrophenol"},
    {"smiles": "c1ccccc1", "name": "benzene"}
  ]
- Detailed Example Output (port "pass"): [
    {"smiles": "c1ccccc1O", "name": "phenol"}
  ]
- Detailed Example Output (port "fail"): [
    {"smiles": "c1ccc([N+](=O)[O-])cc1O", "name": "nitrophenol", "failed_patterns": "Nitro (count=1, max=0)"},
    {"smiles": "c1ccccc1", "name": "benzene", "failed_patterns": "Hydroxyl (count=0, min=1)"}
  ]

---

## Create SubstructureCounterNodeModel
- Two input ports:
  - "molecules" Table containing molecules as SMILES
  - "queries" Table containing query molecules/SMARTS
- Properties:
  - smilesColumn (string, required — SMILES column in molecules table)
  - querySmilesColumn (string, required — SMILES/SMARTS column in queries table)
  - queryNameColumn (string, default "" — optional column in queries table to use as output column names)
  - uniqueMatchesOnly (boolean, default true — count only unique matches)
  - useChirality (boolean, default false)
  - addTotalHitsColumn (boolean, default true)
  - totalHitsColumnName (string, default "total_substructure_hits")
- Output port "output" Table with original molecule columns plus one count column per query + optional total
- UI Schema: React Material JSONForm VerticalLayout with controls for all properties
- Behavior:
  1. Read properties, validate columns
  2. Read ALL queries first into a list with their names (from queryNameColumn, or "Query_1", "Query_2", etc.)
  3. Parse each query: if it looks like SMARTS (contains brackets/wildcards), use `RDKFuncs.SmartsToMol()`; otherwise `RDKFuncs.SmilesToMol()`
  4. For each molecule row:
     - Parse SMILES: `RDKFuncs.SmilesToMol(smiles)`
     - For each query:
       - Get matches: `mol.getSubstructMatches(query)` with uniqueMatchesOnly param
       - Count = matches.size()
       - Add count to output row with column name from query name
     - If addTotalHitsColumn: sum all counts
     - `mol.delete()` in finally
  5. Delete all query mols in finally
  6. Write output row with all original columns + count columns + optional total
- Thinking: Cross-table counter. Creates dynamic output columns (one per query). Query names become column headers. Uses SubstructMatchParameters for unique/chirality options. Mirrors KNIME SubstructureCounter.
- Category: ["RDKit", "Searching"]
- Example Properties: {
    "smilesColumn": "smiles",
    "querySmilesColumn": "pattern",
    "queryNameColumn": "pattern_name",
    "uniqueMatchesOnly": true,
    "useChirality": false,
    "addTotalHitsColumn": true,
    "totalHitsColumnName": "total_hits"
  }
- Detailed Example Input:
  Molecules table: [
    {"smiles": "c1ccc(O)c(O)c1", "name": "catechol"},
    {"smiles": "c1ccccc1", "name": "benzene"}
  ]
  Queries table: [
    {"pattern": "[OH]", "pattern_name": "hydroxyl_count"},
    {"pattern": "c1ccccc1", "pattern_name": "benzene_ring"}
  ]
- Detailed Example Output: [
    {"smiles": "c1ccc(O)c(O)c1", "name": "catechol", "hydroxyl_count": 2, "benzene_ring": 1, "total_hits": 3},
    {"smiles": "c1ccccc1", "name": "benzene", "hydroxyl_count": 0, "benzene_ring": 1, "total_hits": 1}
  ]

---

## Create MCSNodeModel
- One input port "input" Table containing molecules as SMILES strings
- Properties:
  - smilesColumn (string, required — column containing SMILES)
  - threshold (number, default 1.0, min 0.0, max 1.0 — fraction of molecules that must contain the MCS)
  - ringMatchesRingOnly (boolean, default true — ring bonds only match ring bonds)
  - completeRingsOnly (boolean, default true — only return complete rings in the MCS)
  - matchValences (boolean, default false — match atom valences)
  - atomComparison (string, enum: ["Any", "Elements", "Isotopes"], default "Elements")
  - bondComparison (string, enum: ["Any", "Order", "OrderExact"], default "Order")
  - timeout (integer, default 60, min 1 — seconds before timeout)
- Output port "output" Single-row table with MCS results: mcs_smarts (string), num_atoms (int), num_bonds (int), timed_out (boolean)
- UI Schema: React Material JSONForm VerticalLayout with controls for all properties
- Behavior:
  1. Read properties, validate smilesColumn
  2. Read ALL molecules into a list (cross-row operation):
     - Parse each SMILES: `RDKFuncs.SmilesToMol(smiles)`
     - Store in `ROMol_Vect`
  3. Compute MCS:
     - Create `MCSParameters` and configure: threshold, ringMatchesRingOnly, completeRingsOnly, matchValences, atomComparison, bondComparison
     - Call `RDKFuncs.findMCS(molVect, params)` with timeout
     - Extract result: SMARTS string, num atoms, num bonds, whether it timed out
  4. Delete all molecules in finally
  5. Write single output row with MCS results
- Thinking: Cross-row computation — needs ALL molecules in memory. The MCS algorithm is NP-hard, hence the timeout. The result is a single SMARTS pattern representing the largest common substructure. Threshold < 1.0 allows partial MCS (consensus across a fraction of molecules). Mirrors KNIME RDKitMCS.
- Category: ["RDKit", "Searching"]
- Example Properties: {
    "smilesColumn": "smiles",
    "threshold": 1.0,
    "ringMatchesRingOnly": true,
    "completeRingsOnly": true,
    "matchValences": false,
    "atomComparison": "Elements",
    "bondComparison": "Order",
    "timeout": 60
  }
- Detailed Example Input: [
    {"smiles": "c1ccccc1O", "name": "phenol"},
    {"smiles": "c1ccccc1N", "name": "aniline"},
    {"smiles": "c1ccccc1C", "name": "toluene"}
  ]
- Detailed Example Output: [
    {"mcs_smarts": "[#6]1:[#6]:[#6]:[#6]:[#6]:[#6]:1", "num_atoms": 6, "num_bonds": 6, "timed_out": false}
  ]
