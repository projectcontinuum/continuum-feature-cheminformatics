# One Component Reaction Node

Run one-component chemical reactions on reactant molecules using RDKit reaction SMARTS. This is a row-expansion node: each reactant may produce multiple products, and each product is written as a separate output row.

---

## What It Does

The One Component Reaction Node reads all reaction SMARTS from the "reaction" input port, parses them into RDKit ChemicalReaction objects, and validates that each reaction has exactly one reactant template. For each reactant molecule in the "reactants" input port, it applies every reaction and collects all products. Each product is sanitized and converted to a SMILES string, then written as a separate row in the output table.

**Key Points:**
- Two input ports: "reactants" (molecules to react) and "reaction" (reaction SMARTS)
- One output port: "output" (expanded rows with products)
- All reactions are loaded into memory first, then reactants are processed row-by-row
- Each reaction must have exactly one reactant template (validated on load)
- Products that fail sanitization are silently skipped
- Output includes product SMILES, product index, reactant index, and optionally the original reactant columns
- All RDKit objects (reactions, molecules, product sets) are properly deleted after use

---

## Configuration

### Input Ports
| Port | Description |
|------|-------------|
| **reactants** | Table containing a column with reactant SMILES strings |
| **reaction** | Table containing a column with reaction SMARTS strings |

### Output Ports
| Port | Description |
|------|-------------|
| **output** | Table with one row per product, including product SMILES, product index, reactant index, and optional reactant columns |

### Properties
| Property | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| **reactantSmilesColumn** | String | Yes | - | Name of the SMILES column in the reactants table |
| **reactionSmartsColumn** | String | Yes | - | Name of the reaction SMARTS column in the reaction table |
| **productColumnName** | String | No | "product_smiles" | Name of the output column for product SMILES |
| **productIndexColumnName** | String | No | "product_index" | Name of the output column for the product index |
| **reactantIndexColumnName** | String | No | "reactant_index" | Name of the output column for the reactant row index |
| **includeReactantColumns** | Boolean | No | true | Whether to include the original reactant columns in the output |

---

## Example

**Reactants Table:**

| smiles | name |
|--------|------|
| c1ccccc1 | benzene |
| c1ccncc1 | pyridine |

**Reaction Table:**

| reaction_smarts |
|----------------|
| [c:1]>>[c:1]O |

**Configuration:**
- **reactantSmilesColumn**: `smiles`
- **reactionSmartsColumn**: `reaction_smarts`
- **productColumnName**: `product_smiles`

**Output Table:**

| smiles | name | product_smiles | product_index | reactant_index |
|--------|------|---------------|---------------|----------------|
| c1ccccc1 | benzene | Oc1ccccc1 | 0 | 0 |
| c1ccncc1 | pyridine | Oc1ccncc1 | 0 | 1 |

---

## Tips & Warnings

- **Reaction Validation**: Each reaction SMARTS must define exactly one reactant template. Multi-component reactions will cause an error.
- **Row Expansion**: A single reactant can produce many output rows if the reaction yields multiple product sets or multiple products per set.
- **Sanitization Failures**: Products that cannot be sanitized are silently skipped. Check output row counts to detect skipped products.
- **Memory Safety**: All RDKit objects (ChemicalReaction, ROMol, ROMol_Vect, ROMol_Vect_Vect) are properly deleted in `finally` blocks.

---

## Technical Details

- **Algorithm**: Load all reactions, then sequential row processing of reactants with row expansion
- **Memory**: Reactions are held in memory; reactants and products are processed and cleaned up per row
- **Progress**: Reports 20% after loading reactions, 100% on completion
- **Resource Management**: Reactions deleted in outer `finally` block; per-row molecules and product vectors deleted in inner `finally` blocks; output writer closed via `.use {}` block
