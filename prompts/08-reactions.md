# RDKit Reactions Nodes (3 nodes)

> Read `00-preamble.md` first for the full Continuum node pattern, RDKit API rules, and shared utilities.

---

## Create OneComponentReactionNodeModel
- Two input ports:
  - "reactants" Table containing reactant molecules as SMILES
  - "reaction" Table containing reaction SMARTS (single row, or multiple reactions to apply sequentially)
- Properties:
  - reactantSmilesColumn (string, required — SMILES column in reactants table)
  - reactionSmartsColumn (string, required — SMARTS column in reaction table)
  - productColumnName (string, default "product_smiles")
  - productIndexColumnName (string, default "product_index")
  - reactantIndexColumnName (string, default "reactant_index")
  - includeReactantColumns (boolean, default true — pass through columns from reactant table)
- Output port "output" Table — row expansion: one row per product. Contains product SMILES + product index + reactant index + optional reactant columns
- UI Schema: React Material JSONForm VerticalLayout with controls for all properties
- Behavior:
  1. Read properties, validate columns
  2. Read ALL reactions from "reaction" table:
     - Parse each SMARTS: `ChemicalReaction.ReactionFromSmarts(reactionSmarts)`
     - Initialize: `reaction.initReactantMatchers()`
     - Validate: `reaction.getNumReactantTemplates() == 1` (must be one-component)
  3. For each reactant row:
     - Parse SMILES: `RDKFuncs.SmilesToMol(smiles)` → reactant
     - For each reaction:
       - Create reactant vector: `ROMol_Vect` with single molecule
       - Run reaction: `reaction.runReactants(reactantVect)` → returns `ROMol_Vect_Vect` (outer=product sets, inner=products in each set)
       - For each product set and product within:
         - Sanitize: `RDKFuncs.sanitizeMol(product)` (may throw — catch and skip bad products)
         - Convert to SMILES: `RDKFuncs.MolToSmiles(product)`
         - Write one output row with product SMILES, product index, reactant row index, + optional reactant columns
         - `product.delete()` in finally
     - `reactant.delete()` in finally
  4. Delete all reactions in finally
- Thinking: Row expansion node. One reactant can produce multiple products (regiochemistry, etc.). The reaction SMARTS defines the transformation. Products need sanitization since reaction products may have incorrect valences. Mirrors KNIME RDKitOneComponentReaction.
- Category: ["RDKit", "Reactions"]
- Example Properties: {
    "reactantSmilesColumn": "smiles",
    "reactionSmartsColumn": "reaction_smarts",
    "productColumnName": "product_smiles",
    "productIndexColumnName": "product_index",
    "reactantIndexColumnName": "reactant_index",
    "includeReactantColumns": true
  }
- Detailed Example Input:
  Reactants table: [
    {"smiles": "c1ccccc1", "name": "benzene"},
    {"smiles": "c1ccccc1C", "name": "toluene"}
  ]
  Reaction table: [
    {"reaction_smarts": "[c:1]>>[c:1]O"}
  ]
- Detailed Example Output: [
    {"smiles": "c1ccccc1", "name": "benzene", "product_smiles": "Oc1ccccc1", "product_index": 0, "reactant_index": 0},
    {"smiles": "c1ccccc1C", "name": "toluene", "product_smiles": "Cc1ccccc1O", "product_index": 0, "reactant_index": 1}
  ]

---

## Create TwoComponentReactionNodeModel
- Three input ports:
  - "reactant1" Table containing first reactant molecules as SMILES
  - "reactant2" Table containing second reactant molecules as SMILES
  - "reaction" Table containing reaction SMARTS (single row or multiple)
- Properties:
  - reactant1SmilesColumn (string, required — SMILES column for reactant 1)
  - reactant2SmilesColumn (string, required — SMILES column for reactant 2)
  - reactionSmartsColumn (string, required — SMARTS column in reaction table)
  - matrixExpansion (boolean, default false — if true, run all combinations of reactant1 x reactant2; if false, pair by row index)
  - productColumnName (string, default "product_smiles")
  - productIndexColumnName (string, default "product_index")
  - includeReactantColumns (boolean, default true)
- Output port "output" Table — row expansion: one row per product
- UI Schema: React Material JSONForm VerticalLayout with controls for all properties
- Behavior:
  1. Read properties, validate all SMILES columns
  2. Read ALL reactions from "reaction" table and parse with `ChemicalReaction.ReactionFromSmarts()`
     - Validate: `reaction.getNumReactantTemplates() == 2`
  3. Read ALL reactant2 molecules into a list (for matrix expansion or paired access)
  4. If `matrixExpansion` is true:
     - For each reactant1 row × each reactant2 molecule:
       - Create reactant vector with both molecules
       - `reaction.runReactants(reactantVect)` → products
       - Sanitize and write each product as output row
  5. If `matrixExpansion` is false:
     - Pair reactant1[i] with reactant2[i] by row index
     - Run reaction on each pair
  6. Delete all molecules and reactions in finally
- Thinking: Two-reactant reaction. Matrix expansion creates the full combinatorial library (N1 × N2 combinations). Paired mode matches rows 1:1. Products are sanitized. Common in combinatorial chemistry. Mirrors KNIME RDKitTwoComponentReaction.
- Category: ["RDKit", "Reactions"]
- Example Properties: {
    "reactant1SmilesColumn": "amine_smiles",
    "reactant2SmilesColumn": "acid_smiles",
    "reactionSmartsColumn": "rxn_smarts",
    "matrixExpansion": true,
    "productColumnName": "product_smiles",
    "productIndexColumnName": "product_index",
    "includeReactantColumns": true
  }
- Detailed Example Input:
  Reactant1 table: [
    {"amine_smiles": "CC(N)C", "amine_name": "isopropylamine"},
    {"amine_smiles": "c1ccc(N)cc1", "amine_name": "aniline"}
  ]
  Reactant2 table: [
    {"acid_smiles": "CC(=O)O", "acid_name": "acetic_acid"}
  ]
  Reaction table: [
    {"rxn_smarts": "[N:1].[C:2](=O)[OH]>>[N:1][C:2]=O"}
  ]
- Detailed Example Output: [
    {"amine_smiles": "CC(N)C", "amine_name": "isopropylamine", "acid_smiles": "CC(=O)O", "acid_name": "acetic_acid", "product_smiles": "CC(=O)NC(C)C", "product_index": 0},
    {"amine_smiles": "c1ccc(N)cc1", "amine_name": "aniline", "acid_smiles": "CC(=O)O", "acid_name": "acetic_acid", "product_smiles": "CC(=O)Nc1ccccc1", "product_index": 0}
  ]

---

## Create ChemicalTransformationNodeModel
- Two input ports:
  - "molecules" Table containing molecules as SMILES
  - "reactions" Table containing transformation SMARTS
- Properties:
  - smilesColumn (string, required — SMILES column in molecules table)
  - reactionSmartsColumn (string, required — SMARTS column in reactions table)
  - newColumnName (string, default "transformed_smiles")
  - removeSourceColumn (boolean, default false)
  - maxReactionCycles (integer, default 1, min 1, max 100 — maximum number of iterative transformation cycles)
- Output port "output" Table with original columns plus transformed SMILES
- UI Schema: React Material JSONForm VerticalLayout with controls for all properties
- Behavior:
  1. Read properties, validate columns
  2. Read ALL reactions from "reactions" table and parse as ChemicalReaction
  3. For each molecule row:
     - Parse SMILES: `RDKFuncs.SmilesToMol(smiles)` → currentMol
     - For cycle = 1 to maxReactionCycles:
       - For each reaction:
         - Create reactant vector with currentMol
         - Run: `reaction.runReactants(reactantVect)` → products
         - If products exist: take first product, sanitize, set as currentMol for next cycle
         - If no products: stop cycling
     - Convert final mol to SMILES: `RDKFuncs.MolToSmiles(currentMol)`
     - `currentMol.delete()` in finally (and intermediates)
  4. Write output row with transformed SMILES
- Thinking: Iterative transformation — applies reactions repeatedly until no more changes or maxCycles reached. Useful for standardization transforms (e.g., tautomer normalization, charge neutralization). Each cycle feeds the product back as input. Mirrors KNIME RDKitChemicalTransformation.
- Category: ["RDKit", "Reactions"]
- Example Properties: {
    "smilesColumn": "smiles",
    "reactionSmartsColumn": "transform",
    "newColumnName": "transformed_smiles",
    "removeSourceColumn": false,
    "maxReactionCycles": 3
  }
- Detailed Example Input:
  Molecules table: [
    {"smiles": "CC(=O)[O-]", "name": "acetate_anion"},
    {"smiles": "c1ccccc1[NH3+]", "name": "anilinium"}
  ]
  Reactions table: [
    {"transform": "[O-:1]>>[OH:1]"},
    {"transform": "[NH3+:1]>>[NH2:1]"}
  ]
- Detailed Example Output: [
    {"smiles": "CC(=O)[O-]", "name": "acetate_anion", "transformed_smiles": "CC(=O)O"},
    {"smiles": "c1ccccc1[NH3+]", "name": "anilinium", "transformed_smiles": "Nc1ccccc1"}
  ]
