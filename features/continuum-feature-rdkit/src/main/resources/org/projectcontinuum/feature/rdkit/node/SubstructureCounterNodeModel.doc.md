# Substructure Counter Node

Count substructure matches for each molecule against a table of query patterns. For each query pattern, a new column is added to the output table with the match count. Optionally, a total hits column can be added.

---

## What It Does

The Substructure Counter Node reads all query patterns from a second input table, parses them into RDKit molecule objects (trying SMARTS first, then SMILES), and then iterates over each molecule in the first input table. For each molecule, it counts the number of substructure matches against each query pattern and writes the counts as new columns. An optional total hits column sums all match counts per molecule.

**Key Points:**
- Two input ports: "molecules" (molecules to count) and "queries" (substructure patterns)
- One output port with original columns plus one count column per query
- Query names from the queries table are used as column names
- Patterns are parsed as SMARTS first; if that fails, they are parsed as SMILES
- Optional total hits column sums all query matches per molecule
- Invalid or empty SMILES produce 0 counts for all queries

---

## Configuration

### Input Ports
| Port | Description |
|------|-------------|
| **molecules** | Table containing molecules as SMILES strings |
| **queries** | Table containing query patterns (SMILES or SMARTS) and optional names |

### Output Ports
| Port | Description |
|------|-------------|
| **output** | Table with all original columns plus count columns per query |

### Properties
| Property | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| **smilesColumn** | String | Yes | - | Name of the SMILES column in the molecules table |
| **querySmilesColumn** | String | Yes | - | Name of the pattern column in the queries table |
| **queryNameColumn** | String | No | "" | Column with query names (used as output column names) |
| **uniqueMatchesOnly** | Boolean | No | true | Whether to count only unique substructure matches |
| **addTotalHitsColumn** | Boolean | No | true | Whether to add a total hits column |
| **totalHitsColumnName** | String | No | "total_hits" | Name for the total hits column |

---

## Example

**Molecules Table:**

| smiles | name |
|--------|------|
| Oc1cc(O)ccc1 | catechol |
| c1ccccc1 | benzene |

**Queries Table:**

| pattern | pattern_name |
|---------|-------------|
| [OH] | hydroxyl |
| c1ccccc1 | aromatic_ring |

**Configuration:**
- **smilesColumn**: `smiles`
- **querySmilesColumn**: `pattern`
- **queryNameColumn**: `pattern_name`
- **addTotalHitsColumn**: `true`

**Output Table:**

| smiles | name | hydroxyl | aromatic_ring | total_hits |
|--------|------|----------|--------------|------------|
| Oc1cc(O)ccc1 | catechol | 2 | 1 | 3 |
| c1ccccc1 | benzene | 0 | 1 | 1 |

---

## Tips & Warnings

- **Query Names**: If `queryNameColumn` is empty or a name is missing, auto-generated names (query_0, query_1, ...) are used.
- **SMARTS vs SMILES**: The node first attempts to parse each query as SMARTS. If that fails, it falls back to SMILES. This allows mixing both notations in the query table.
- **Invalid Queries**: Queries that cannot be parsed as either SMARTS or SMILES are silently skipped.
- **Memory Safety**: All query and molecule RDKit objects are properly deleted after use.

---

## Technical Details

- **Algorithm**: Load all queries, then sequential row processing of molecules with match counting
- **Memory**: Query molecules held in memory; target molecules processed one at a time
- **Progress**: Reports 30% after loading queries, 100% on completion
- **Resource Management**: Query molecules deleted in outer `finally` block; per-row molecules deleted in inner `finally` blocks; output writer closed via `.use {}` block
