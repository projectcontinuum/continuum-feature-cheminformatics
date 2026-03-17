# Molecule Catalog Filter Node

Filter molecules against structural alert catalogs (PAINS, BRENK, NIH, ZINC) using RDKit's FilterCatalog. Molecules matching any catalog entry are flagged as problematic, while clean molecules pass through. This is essential for removing problematic compounds in High-Throughput Screening (HTS) campaigns.

---

## What It Does

The Molecule Catalog Filter Node checks each molecule against one or more pre-built structural alert catalogs. PAINS (Pan-Assay INterference compoundS) patterns are the most common — they identify molecules with substructures known to cause false positives in biological assays. Molecules are split into "clean" (no matches) and "flagged" (one or more matches) outputs.

**Key Points:**
- Supports PAINS_A, PAINS_B, PAINS_C, BRENK, NIH, ZINC, and ALL catalogs
- Splitter node with two output ports (clean/flagged)
- Optional match details and count columns
- Invalid SMILES are routed to the clean port (cannot match anything)

---

## Configuration

### Input Ports
| Port | Description |
|------|-------------|
| **input** | Input table containing a column with SMILES strings |

### Output Ports
| Port | Description |
|------|-------------|
| **clean** | Molecules passing all catalog filters (no matches) |
| **flagged** | Molecules matching one or more catalog entries |

### Properties
| Property | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| **smilesColumn** | String | Yes | - | Name of the column containing SMILES |
| **catalogs** | String[] | Yes | ["PAINS_A"] | Catalog names to check against |
| **addMatchDetailsColumn** | Boolean | No | true | Add column with match details |
| **matchDetailsColumnName** | String | No | "catalog_matches" | Name for match details column |
| **addMatchCountColumn** | Boolean | No | true | Add column with match count |
| **matchCountColumnName** | String | No | "catalog_match_count" | Name for match count column |

---

## Example

**Input Table:**

| smiles | name |
|--------|------|
| c1ccccc1 | benzene |
| O=C1C=CC(=O)C=C1 | quinone |

**Output (clean):**

| smiles | name | catalog_matches | catalog_match_count |
|--------|------|-----------------|---------------------|
| c1ccccc1 | benzene | | 0 |

**Output (flagged):**

| smiles | name | catalog_matches | catalog_match_count |
|--------|------|-----------------|---------------------|
| O=C1C=CC(=O)C=C1 | quinone | quinone_A(370) | 1 |

---

## Technical Details

- **Algorithm**: Sequential row processing with FilterCatalog matching
- **Memory**: Processes one row at a time
- **Progress**: Reports percentage based on rows processed vs total rows

