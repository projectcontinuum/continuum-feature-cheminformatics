# Adjust Query Properties Node

Convert molecules to flexible substructure queries by converting SMILES to SMARTS notation. The resulting SMARTS can be used for substructure searching with appropriate specificity. This is useful for query-based virtual screening.

---

## What It Does

The Adjust Query Properties Node reads a SMILES string from each row, optionally applies aromaticity perception, then converts the molecule to a SMARTS query representation. The SMARTS output encodes atom types and connectivity in a way that's suitable for substructure searching.

**Key Points:**
- Converts SMILES to SMARTS query representation
- Optional aromaticity perception before conversion
- Configurable query property adjustments
- Invalid SMILES produce an empty string

---

## Configuration

### Input Ports
| Port | Description |
|------|-------------|
| **input** | Input table containing a column with SMILES strings |

### Output Ports
| Port | Description |
|------|-------------|
| **output** | Table with original columns plus adjusted SMARTS column |

### Properties
| Property | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| **smilesColumn** | String | Yes | - | Name of the column containing SMILES |
| **newColumnName** | String | No | "adjusted_query_smarts" | Output SMARTS column name |
| **removeSourceColumn** | Boolean | No | false | Remove the original SMILES column |
| **adjustDegree** | Boolean | No | true | Add degree queries to atoms |
| **adjustRingCount** | Boolean | No | true | Add ring count queries |
| **makeDummiesQueries** | Boolean | No | true | Convert dummy atoms to queries |
| **aromatize** | Boolean | No | true | Apply aromaticity before adjusting |
| **makeAtomsGeneric** | Boolean | No | false | Replace atom types with any-atom queries |
| **makeBondsGeneric** | Boolean | No | false | Replace bond types with any-bond queries |

---

## Example

**Input Table:**

| smiles | name |
|--------|------|
| c1ccccc1O | phenol |
| CC(=O)O | acetic_acid |

**Output Table:**

| smiles | name | adjusted_query_smarts |
|--------|------|-----------------------|
| c1ccccc1O | phenol | [#6]1:[#6]:[#6]:[#6]:[#6]:[#6]:1-[#8] |
| CC(=O)O | acetic_acid | [#6]-[#6](=[#8])-[#8] |

---

## Technical Details

- **Algorithm**: Sequential row processing with SMILES→SMARTS conversion
- **Memory**: Processes one row at a time
- **Progress**: Reports percentage based on rows processed vs total rows

