# Structure Normalizer Node

Apply configurable normalization steps to standardize molecule SMILES representations. Structure standardization is critical for database registration, deduplication, and consistent downstream analysis.

---

## What It Does

The Structure Normalizer Node applies a sequence of normalization steps to each molecule. Users select which steps to apply and in what order. Steps include fragment removal, charge neutralization, canonicalization, isotope removal, stereo removal, and general cleanup.

**Key Points:**
- Composable normalization pipeline — pick the steps you need
- Order matters: fragment removal before neutralization is recommended
- Invalid SMILES produce an empty string
- Each step is applied independently and sequentially

---

## Configuration

### Input Ports
| Port | Description |
|------|-------------|
| **input** | Input table containing a column with SMILES strings |

### Output Ports
| Port | Description |
|------|-------------|
| **output** | Table with original columns plus normalized SMILES column |

### Properties
| Property | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| **smilesColumn** | String | Yes | - | Name of the column containing SMILES strings |
| **newColumnName** | String | No | "normalized_smiles" | Name for the normalized output column |
| **removeSourceColumn** | Boolean | No | false | Whether to remove the original SMILES column |
| **normalizationSteps** | String[] | No | ["RemoveFragments", "Neutralize", "Canonicalize"] | Steps to apply in order |

### Available Normalization Steps
| Step | Description |
|------|-------------|
| **RemoveFragments** | Keep only the largest fragment (removes salts/counterions) |
| **Neutralize** | Remove charges where possible (ammonium→amine, carboxylate→acid) |
| **Canonicalize** | Convert to canonical SMILES form |
| **RemoveIsotopes** | Set all atom isotopes to 0 |
| **RemoveStereo** | Remove stereochemistry information |
| **ReionizeMetal** | Standardize metal-organic salts |
| **Cleanup** | Sanitize molecule (fix valence, aromaticity, etc.) |

---

## Example

**Input Table:**

| smiles | name |
|--------|------|
| CC(=O)[O-].[Na+] | sodium_acetate |
| c1ccc([NH3+])cc1.[Cl-] | aniline_hcl |
| c1ccccc1 | benzene |

**Configuration:**
- **normalizationSteps**: `["RemoveFragments", "Neutralize", "Canonicalize"]`

**Output Table:**

| smiles | name | normalized_smiles |
|--------|------|-------------------|
| CC(=O)[O-].[Na+] | sodium_acetate | CC(=O)O |
| c1ccc([NH3+])cc1.[Cl-] | aniline_hcl | Nc1ccccc1 |
| c1ccccc1 | benzene | c1ccccc1 |

---

## Technical Details

- **Algorithm**: Sequential row processing with streaming reader
- **Memory**: Processes one row at a time
- **Progress**: Reports percentage based on rows processed vs total rows

