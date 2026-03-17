# SMILES Parser

Parse molecule strings (SMILES, SDF, or SMARTS) into canonical SMILES representations using RDKit. Valid molecules are routed to the output port, while failed rows are captured separately on the errors port.

---

## What It Does

The SMILES Parser reads molecule strings from a specified column and attempts to parse each one using the selected input format. Successfully parsed molecules produce a canonical SMILES representation, while rows that fail parsing are routed to a dedicated error output with a diagnostic message.

**Key Points:**
- Parses SMILES, SDF (MolBlock), or SMARTS input formats
- Routes valid molecules to **output** port with canonical SMILES
- Routes invalid molecules to **errors** port with error description
- Native RDKit memory is always freed via `mol.delete()` in a finally block
- Each output port maintains independent row numbering starting from 0

---

## Configuration

### Input Ports
| Port | Description |
|------|-------------|
| **input** | Input table containing a column with molecule strings |

### Output Ports
| Port | Description |
|------|-------------|
| **output** | Table with successfully parsed molecules (canonical SMILES column added) |
| **errors** | Table with rows that failed parsing (error message column added) |

### Properties
| Property | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| **smilesColumn** | String | Yes | `smiles` | Name of the column containing molecule strings |
| **inputFormat** | String (enum) | No | `SMILES` | Input format: `SMILES`, `SDF`, or `SMARTS` |
| **newColumnName** | String | No | `canonical_smiles` | Name for the new canonical SMILES column |
| **removeSourceColumn** | Boolean | No | `false` | Remove the original molecule string column from output |
| **sanitize** | Boolean | No | `true` | Sanitize molecules during parsing |
| **addErrorColumn** | Boolean | No | `true` | Add an error description column to failed rows |
| **errorColumnName** | String | No | `parse_error` | Name for the error description column |

---

## How It Works

1. **Validate properties** - ensure `smilesColumn` is provided
2. **Read each row** from the input table
3. **Extract molecule string** from the configured column
4. **Parse based on format**:
   - `SMILES` uses `RDKFuncs.SmilesToMol()`
   - `SDF` uses `RDKFuncs.MolBlockToMol()`
   - `SMARTS` uses `RDKFuncs.SmartsToMol()`
5. **Route the row**:
   - If parse succeeds: compute canonical SMILES, add to row, write to **output**
   - If parse fails: add error message, write to **errors**
6. **Free native memory** via `mol.delete()` in a finally block
7. **Report progress** as percentage of rows processed

---

## Example

**Input:**

| id | smiles |
|----|--------|
| 1 | CCO |
| 2 | INVALID_SMILES |
| 3 | c1ccccc1 |
| 4 | [bogus] |

**Configuration:**
- **smilesColumn**: `smiles`
- **inputFormat**: `SMILES`
- **newColumnName**: `canonical_smiles`

**Output Port (successfully parsed):**

| id | smiles | canonical_smiles |
|----|--------|-----------------|
| 1 | CCO | CCO |
| 3 | c1ccccc1 | c1ccccc1 |

**Errors Port (failed parsing):**

| id | smiles | parse_error |
|----|--------|-------------|
| 2 | INVALID_SMILES | Failed to parse SMILES: INVALID_SMILES |
| 4 | [bogus] | Failed to parse SMILES: [bogus] |

---

## Tips

- Use this node as the first step in any RDKit workflow to validate and normalize molecule input
- Connect the **errors** port to a logging or review node to inspect which molecules failed
- Set **removeSourceColumn** to `true` if you only want the canonical form in downstream nodes
- When parsing SDF/MolBlock input, the column should contain the full MolBlock text (multi-line)
- SMARTS parsing is useful for validating query patterns before substructure searches
- Empty or blank molecule strings are always routed to the errors port
- Each output port has independent row numbering starting from 0
