# R-Group Decomposition Node

Decompose molecules into a core scaffold and R-groups (substituents) using RDKit's RGroupDecomposition algorithm. This is a key tool for Structure-Activity Relationship (SAR) analysis in medicinal chemistry.

---

## What It Does

Given a core SMARTS pattern, the R-Group Decomposition Node identifies what functional groups are attached at each labeled position across a set of molecules. Molecules that do not match the core scaffold receive empty R-group columns.

**Key Points:**
- Reads all molecules first for batch decomposition (cross-row operation)
- Dynamic R-group columns (R1, R2, R3...) based on the core scaffold
- Non-matching molecules pass through with empty R-group columns
- Configurable core column name and R-group prefix

---

## Configuration

### Input Ports
| Port | Description |
|------|-------------|
| **input** | Input table containing a column with SMILES strings |

### Output Ports
| Port | Description |
|------|-------------|
| **output** | Table with original columns plus core SMILES and R-group SMILES columns |

### Properties
| Property | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| **smilesColumn** | String | Yes | - | Name of the column containing SMILES strings |
| **coreSmarts** | String | Yes | - | SMARTS pattern defining the core scaffold |
| **coreColumnName** | String | No | "core" | Name for the core output column |
| **rGroupPrefix** | String | No | "R" | Prefix for R-group columns (R1, R2, ...) |
| **matchOnlyAtRGroups** | Boolean | No | false | Only decompose at marked R-group positions |
| **removeSourceColumn** | Boolean | No | false | Whether to remove the original SMILES column |

---

## Example

**Input Table:**

| smiles | name |
|--------|------|
| c1ccc(O)cc1N | 2-aminophenol |
| c1ccc(C)cc1Cl | 2-chlorotoluene |
| CCCCCC | hexane |

**Configuration:**
- **smilesColumn**: `smiles`
- **coreSmarts**: `c1ccc([*:1])cc1[*:2]`

**Output Table:**

| smiles | name | core | R1 | R2 |
|--------|------|------|----|----|
| c1ccc(O)cc1N | 2-aminophenol | c1ccc(-*)cc1-* | O | N |
| c1ccc(C)cc1Cl | 2-chlorotoluene | c1ccc(-*)cc1-* | C | Cl |
| CCCCCC | hexane | | | |

---

## Tips & Warnings

- **Core SMARTS**: Use labeled dummy atoms `[*:1]`, `[*:2]` to mark R-group positions
- **Batch Operation**: All molecules are read into memory before decomposition
- **Non-matching molecules**: Rows where the molecule does not match the core get empty R-group columns
- **Memory Safety**: All RDKit objects are properly cleaned up in `finally` blocks

---

## Technical Details

- **Algorithm**: RDKit RGroupDecomposition with batch processing
- **Memory**: Loads all molecules into memory (not suitable for extremely large datasets)
- **Progress**: Reports progress in two phases: loading (0-60%) and writing (60-100%)

