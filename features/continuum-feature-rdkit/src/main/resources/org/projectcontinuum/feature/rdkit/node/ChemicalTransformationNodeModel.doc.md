# Chemical Transformation Node

Apply chemical transformations iteratively to molecules using RDKit reaction SMARTS. This node reads a set of reactions and applies them sequentially to each molecule for a configurable number of cycles.

---

## What It Does

The Chemical Transformation Node reads all reaction SMARTS from the "reactions" input port, then processes each molecule from the "molecules" input port. For each molecule, it applies the reactions iteratively for up to maxReactionCycles iterations. In each cycle, for each reaction, if products are generated the first product is sanitized and used as input for the next reaction and subsequent cycles. If no reaction changes the molecule in a given cycle, iteration stops early.

**Key Points:**
- Two input ports: "molecules" (molecules to transform) and "reactions" (transformation SMARTS)
- One output port: "output" (table with original columns plus transformed SMILES)
- Reactions are applied sequentially and iteratively up to maxReactionCycles
- In each cycle, the first product of the first matching product set is used
- Early termination if no reaction changes the molecule in a cycle
- Products that fail sanitization are skipped (molecule keeps its current form)
- Original columns are preserved (source column optionally removed)
- All RDKit objects are properly deleted after use

---

## Configuration

### Input Ports
| Port | Description |
|------|-------------|
| **molecules** | Table containing a column with SMILES strings |
| **reactions** | Table containing a column with reaction SMARTS strings |

### Output Ports
| Port | Description |
|------|-------------|
| **output** | Table with all original columns plus a new column with the transformed SMILES |

### Properties
| Property | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| **smilesColumn** | String | Yes | - | Name of the SMILES column in the molecules table |
| **reactionSmartsColumn** | String | Yes | - | Name of the reaction SMARTS column in the reactions table |
| **newColumnName** | String | No | "transformed_smiles" | Name of the output column for the transformed SMILES |
| **removeSourceColumn** | Boolean | No | false | Whether to remove the original SMILES column from the output |
| **maxReactionCycles** | Integer | No | 1 | Maximum number of iterative reaction cycles (1-100) |

---

## Example

**Molecules Table:**

| smiles | name |
|--------|------|
| CC(=O)[O-] | acetate |
| c1ccc(O)cc1 | phenol |

**Reactions Table:**

| transform |
|----------|
| [O-:1]>>[OH:1] |

**Configuration:**
- **smilesColumn**: `smiles`
- **reactionSmartsColumn**: `transform`
- **newColumnName**: `transformed_smiles`
- **maxReactionCycles**: `1`

**Output Table:**

| smiles | name | transformed_smiles |
|--------|------|--------------------|
| CC(=O)[O-] | acetate | CC(=O)O |
| c1ccc(O)cc1 | phenol | c1ccc(O)cc1 |

---

## Tips & Warnings

- **Reaction Order**: Reactions are applied in the order they appear in the reactions table. The order can affect the final result.
- **Iterative Cycles**: Use maxReactionCycles > 1 when a transformation may need to be applied multiple times (e.g., removing multiple protecting groups).
- **Early Stop**: Iteration stops as soon as a full cycle produces no changes, so setting a high maxReactionCycles does not always mean slow execution.
- **First Product Only**: Only the first product of the first product set is taken. This is suitable for deterministic transformations but may miss alternative products.
- **No Reaction Validation**: Unlike the One/Two Component Reaction nodes, this node does not validate the number of reactant templates. Reactions should be single-reactant transformations.

---

## Technical Details

- **Algorithm**: Load all reactions, then sequential row processing with iterative transformation cycles
- **Memory**: Reactions are held in memory; molecules are processed one at a time with per-cycle RDKit object creation and cleanup
- **Progress**: Reports 20% after loading reactions, then proportional progress during molecule processing
- **Resource Management**: Reactions deleted in outer `finally` block; per-cycle molecules and product vectors deleted in inner `finally` blocks; output writer closed via `.use {}` block
