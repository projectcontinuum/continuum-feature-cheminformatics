# Two Component Reaction Node

Run two-component chemical reactions on pairs of reactant molecules using RDKit reaction SMARTS. This is a row-expansion node that supports both pairwise (index-matched) and matrix (all combinations) modes.

---

## What It Does

The Two Component Reaction Node reads all reaction SMARTS from the "reaction" input port and validates that each has exactly two reactant templates. It then reads all second reactants from the "reactant2" input port into memory. For each first reactant in "reactant1", it pairs with second reactants according to the expansion mode (pairwise or matrix) and runs all reactions. Each product is sanitized, converted to SMILES, and written as a separate output row.

**Key Points:**
- Three input ports: "reactant1" (first reactants), "reactant2" (second reactants), and "reaction" (reaction SMARTS)
- One output port: "output" (expanded rows with products)
- Two pairing modes: pairwise (r1[i] with r2[i]) and matrix (every r1 with every r2)
- All reactions and second reactants are loaded into memory first
- Each reaction must have exactly two reactant templates (validated on load)
- Products that fail sanitization are silently skipped
- When including reactant columns, second reactant columns that collide with first reactant column names are prefixed with "r2_"
- All RDKit objects are properly deleted after use

---

## Configuration

### Input Ports
| Port | Description |
|------|-------------|
| **reactant1** | Table containing a column with first reactant SMILES strings |
| **reactant2** | Table containing a column with second reactant SMILES strings |
| **reaction** | Table containing a column with reaction SMARTS strings |

### Output Ports
| Port | Description |
|------|-------------|
| **output** | Table with one row per product, including product SMILES, product index, and optional reactant columns |

### Properties
| Property | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| **reactant1SmilesColumn** | String | Yes | - | Name of the SMILES column in the reactant1 table |
| **reactant2SmilesColumn** | String | Yes | - | Name of the SMILES column in the reactant2 table |
| **reactionSmartsColumn** | String | Yes | - | Name of the reaction SMARTS column in the reaction table |
| **matrixExpansion** | Boolean | No | false | If true, run all r1 x r2 combinations; if false, pair by index |
| **productColumnName** | String | No | "product_smiles" | Name of the output column for product SMILES |
| **productIndexColumnName** | String | No | "product_index" | Name of the output column for the product index |
| **includeReactantColumns** | Boolean | No | true | Whether to include the original reactant columns in the output |

---

## Example

**Reactant1 Table:**

| amine | name |
|-------|------|
| CCN | ethylamine |
| NCCN | ethylenediamine |

**Reactant2 Table:**

| acid |
|------|
| CC(=O)O |
| C(=O)O |

**Reaction Table:**

| rxn_smarts |
|-----------|
| [N:1].[C:2](=O)[OH]>>[N:1][C:2]=O |

**Configuration (pairwise mode):**
- **reactant1SmilesColumn**: `amine`
- **reactant2SmilesColumn**: `acid`
- **matrixExpansion**: `false`

**Output Table (pairwise):**

| amine | name | acid | product_smiles | product_index |
|-------|------|------|---------------|---------------|
| CCN | ethylamine | CC(=O)O | CCNC(C)=O | 0 |
| NCCN | ethylenediamine | C(=O)O | O=CNCCN | 0 |

---

## Tips & Warnings

- **Reaction Validation**: Each reaction SMARTS must define exactly two reactant templates. Reactions with a different count will cause an error.
- **Matrix vs Pairwise**: Matrix expansion produces r1_count x r2_count pairs, which can lead to large outputs. Use pairwise mode when inputs are pre-aligned.
- **Memory**: All second reactants are loaded into memory. Keep the reactant2 table at a reasonable size.
- **Column Collisions**: When reactant1 and reactant2 tables share column names, reactant2 columns are prefixed with "r2_" to avoid overwriting.
- **Sanitization Failures**: Products that cannot be sanitized are silently skipped.

---

## Technical Details

- **Algorithm**: Load all reactions and reactant2 rows, then sequential processing of reactant1 rows with row expansion
- **Memory**: Reactions and reactant2 rows are held in memory; reactant1 rows are processed one at a time
- **Progress**: Reports 10% after loading reactions, 20% after loading reactant2, 100% on completion
- **Resource Management**: Reactions deleted in outer `finally` block; per-pair molecules and product vectors deleted in inner `finally` blocks; output writer closed via `.use {}` block
