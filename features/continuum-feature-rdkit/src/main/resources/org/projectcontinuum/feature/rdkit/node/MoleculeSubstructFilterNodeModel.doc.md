# Molecule Substructure Filter Node

Filter molecules from one input table against a set of SMILES-based substructure queries from a second input table. This node is a splitter that routes matching and non-matching molecules to separate output ports.

---

## What It Does

The Molecule Substructure Filter Node reads all query SMILES from the queries input port, parses them into RDKit molecule objects, then iterates over each molecule in the molecules input port. For each molecule, it checks whether it matches the query set according to the selected match mode ("any" or "all"). Matching molecules go to the "match" port; non-matching molecules go to the "noMatch" port.

**Key Points:**
- Two input ports: "molecules" (molecules to filter) and "queries" (substructure queries)
- Two output ports: "match" and "noMatch"
- All queries are loaded into memory first, then molecules are processed row-by-row
- "any" mode: molecule matches if it contains at least one query substructure
- "all" mode: molecule matches only if it contains every query substructure
- Rows with invalid or empty SMILES are sent to "noMatch"
- Query molecules are cleaned up after all rows are processed

---

## Configuration

### Input Ports
| Port | Description |
|------|-------------|
| **molecules** | Table containing molecules as SMILES strings |
| **queries** | Table containing query SMILES strings |

### Output Ports
| Port | Description |
|------|-------------|
| **match** | Table containing molecules that match the query criteria |
| **noMatch** | Table containing molecules that do not match |

### Properties
| Property | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| **smilesColumn** | String | Yes | - | Name of the SMILES column in the molecules table |
| **querySmilesColumn** | String | Yes | - | Name of the SMILES column in the queries table |
| **matchMode** | String | No | "any" | Match mode: "any" (at least one match) or "all" (all must match) |

---

## Example

**Molecules Table:**

| smiles | name |
|--------|------|
| c1ccc(O)cc1 | phenol |
| c1ccccc1 | benzene |
| c1ccc(N)cc1 | aniline |

**Queries Table:**

| query_smiles |
|-------------|
| c1ccccc1 |
| [OH] |

**Configuration (mode = "any"):**

**Match Output:**

| smiles | name |
|--------|------|
| c1ccc(O)cc1 | phenol |
| c1ccccc1 | benzene |
| c1ccc(N)cc1 | aniline |

**Configuration (mode = "all"):**

**Match Output:**

| smiles | name |
|--------|------|
| c1ccc(O)cc1 | phenol |

---

## Tips & Warnings

- **Query Loading**: All queries are loaded into memory before processing. Keep the query list reasonable in size.
- **Empty Queries**: If no valid query molecules are found, all molecules are sent to "noMatch".
- **Match Mode**: Use "any" for OR-logic (union) and "all" for AND-logic (intersection).
- **Memory Safety**: All RDKit molecule objects (both query and target) are properly deleted after use.

---

## Technical Details

- **Algorithm**: Load all queries, then sequential row processing of molecules
- **Memory**: Query molecules are held in memory; target molecules are processed one at a time
- **Progress**: Reports 30% after loading queries, 100% on completion
- **Resource Management**: Query molecules deleted in outer `finally` block; per-row molecules deleted in inner `finally` blocks; output writers closed in `finally` block
