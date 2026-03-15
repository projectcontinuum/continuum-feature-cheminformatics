# Molecule Fragmenter Node

Fragment molecules using Murcko decomposition and output the scaffold plus side chains as separate fragments. This node produces two outputs: a fragment table with unique fragments and a molecule table with fragment index references.

---

## What It Does

The Molecule Fragmenter Node reads SMILES strings from the input table and fragments each molecule using Murcko decomposition. For each molecule, it extracts the Murcko scaffold (core ring structure) and any disconnected side chain fragments. All unique fragments across the entire dataset are assigned sequential indices. The node outputs two tables: one with the unique fragments and another with the original molecules augmented with their fragment index references.

**Key Points:**
- Two output ports: "fragments" and "molecules"
- Fragments are deduplicated across the entire dataset
- Each fragment is assigned a unique sequential index
- Molecules without rings are treated as a single fragment (the whole molecule)
- The "molecules" output contains the original columns plus a JSON array of fragment indices
- Invalid or empty SMILES produce no fragments

---

## Configuration

### Input Ports
| Port | Description |
|------|-------------|
| **input** | Input table containing a column with SMILES strings |

### Output Ports
| Port | Description |
|------|-------------|
| **fragments** | Table with fragment_index, fragment_smiles, and parent_smiles columns |
| **molecules** | Original table augmented with a fragment_indices JSON array column |

### Properties
| Property | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| **smilesColumn** | String | Yes | - | Name of the column containing SMILES strings |
| **minPathLength** | Integer | No | 1 | Minimum path length for fragment enumeration (min: 1) |
| **maxPathLength** | Integer | No | 3 | Maximum path length for fragment enumeration (max: 10) |

---

## Example

**Input Table:**

| smiles | name |
|--------|------|
| CC(=O)Oc1ccccc1C(=O)O | aspirin |
| c1ccccc1 | benzene |

**Configuration:**
- **smilesColumn**: `smiles`
- **minPathLength**: `1`
- **maxPathLength**: `3`

**Fragments Output:**

| fragment_index | fragment_smiles | parent_smiles |
|----------------|----------------|---------------|
| 0 | c1ccccc1 | CC(=O)Oc1ccccc1C(=O)O |
| 1 | c1ccccc1 | c1ccccc1 |

**Molecules Output:**

| smiles | name | fragment_indices |
|--------|------|-----------------|
| CC(=O)Oc1ccccc1C(=O)O | aspirin | [0] |
| c1ccccc1 | benzene | [1] |

---

## Tips & Warnings

- **Fragment Deduplication**: Fragments are identified by their canonical SMILES string. The same structural fragment appearing in different molecules will share the same index only if the SMILES strings are identical.
- **Molecules Without Rings**: Molecules without any ring systems (e.g., linear alkanes) are treated as a single fragment representing the whole molecule.
- **JSON Output**: The `fragment_indices` column in the molecules output contains a JSON array of integers, which can be parsed downstream for analysis.
- **Memory Safety**: All RDKit molecule objects, scaffolds, and fragment vectors are deleted in `finally` blocks, preventing native memory leaks.
- **Two-Pass Processing**: The node reads all input rows before writing output, so it uses more memory than single-pass nodes.

---

## Technical Details

- **Algorithm**: Two-phase processing — first pass collects all fragments and builds a registry, second pass writes both output tables
- **Memory**: Holds all rows and fragment registry in memory (may not be suitable for very large datasets)
- **Progress**: Reports 0-50% during input processing, 50-75% during fragment output, 75-100% during molecule output
- **Resource Management**: Uses `.use {}` blocks for input reader cleanup and `finally` for output writer closing and RDKit native object deletion
