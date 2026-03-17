# Substructure Filter Node

Filter molecules based on a SMARTS substructure query, splitting matches and non-matches into separate output ports. This node is a splitter that routes each molecule to one of two outputs depending on whether it contains the specified substructure.

---

## What It Does

The Substructure Filter Node parses a SMARTS query once, then iterates over each row in the input table. For each valid SMILES, it checks whether the molecule contains the specified substructure. Matching molecules are written to the "match" port, and non-matching molecules (including invalid SMILES) are written to the "noMatch" port.

**Key Points:**
- SMARTS query is parsed once and reused for all rows (efficient)
- Two output ports: "match" and "noMatch"
- Rows with invalid or empty SMILES are sent to the "noMatch" port
- Optional chirality-aware matching
- All RDKit molecule objects are properly deleted after use

---

## Configuration

### Input Ports
| Port | Description |
|------|-------------|
| **input** | Input table containing a column with SMILES strings |

### Output Ports
| Port | Description |
|------|-------------|
| **match** | Table containing molecules that match the SMARTS query |
| **noMatch** | Table containing molecules that do not match the SMARTS query |

### Properties
| Property | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| **smilesColumn** | String | Yes | - | Name of the column containing SMILES strings |
| **smartsQuery** | String | Yes | - | SMARTS pattern to match against molecules |
| **useChirality** | Boolean | No | false | Whether to use chirality in substructure matching |

---

## Example

**Input Table:**

| smiles | name |
|--------|------|
| c1ccc(O)cc1 | phenol |
| c1ccccc1 | benzene |
| CCO | ethanol |
| CC(=O)O | acetic acid |

**Configuration:**
- **smilesColumn**: `smiles`
- **smartsQuery**: `[OH]`

**Match Output:**

| smiles | name |
|--------|------|
| c1ccc(O)cc1 | phenol |
| CCO | ethanol |
| CC(=O)O | acetic acid |

**No Match Output:**

| smiles | name |
|--------|------|
| c1ccccc1 | benzene |

---

## Tips & Warnings

- **SMARTS Syntax**: Ensure your SMARTS query is valid. An invalid query will cause the node to throw an error before processing any rows.
- **Chirality**: When `useChirality` is true, the matching considers stereochemistry. This is useful for distinguishing enantiomers.
- **Invalid SMILES**: Rows with empty or unparseable SMILES are always routed to the "noMatch" port.
- **Memory Safety**: The SMARTS query molecule is deleted after all rows are processed. Individual molecule objects are deleted in a `finally` block after each row.

---

## Technical Details

- **Algorithm**: Sequential row processing with a pre-parsed SMARTS query
- **Memory**: Processes one row at a time (suitable for large datasets)
- **Progress**: Reports 100% on completion
- **Resource Management**: Uses `finally` blocks for both the query molecule and per-row molecule cleanup; output writers are closed in a `finally` block
