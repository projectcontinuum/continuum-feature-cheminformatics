# Fingerprint Node

Generate bit-based molecular fingerprints from SMILES strings using RDKit. This node supports 9 different fingerprint types covering circular, path-based, substructure, and key-based algorithms, making it the most versatile fingerprinting node in the toolkit.

---

## What It Does

The Fingerprint Node reads a SMILES string from each row, parses it into an RDKit molecule object, and computes a fingerprint of the selected type. The fingerprint is written as a bit string (a sequence of `0` and `1` characters) to a new column. Each fingerprint type encodes different structural features of the molecule.

**Key Points:**
- Supports 9 fingerprint types: Morgan, FeatMorgan, AtomPair, Torsion, RDKit, Avalon, Layered, MACCS, and Pattern
- Configurable bit vector length (64-16384 bits) for most types; MACCS is always 166 bits
- Algorithm-specific parameters (radius, path lengths) are fully configurable
- Invalid SMILES produce an empty string -- no error is thrown
- All original columns are preserved in the output
- Optionally removes the source SMILES column
- Native RDKit fingerprint and molecule objects are properly cleaned up after each row

---

## Configuration

### Input Ports
| Port | Description |
|------|-------------|
| **input** | Input table containing a column with SMILES strings |

### Output Ports
| Port | Description |
|------|-------------|
| **output** | Table with all original columns plus a new column containing the fingerprint bit string |

### Properties
| Property | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| **smilesColumn** | String | Yes | - | Name of the column containing SMILES strings |
| **fingerprintType** | String | No | "Morgan" | Fingerprint algorithm (see table below) |
| **numBits** | Integer | No | 2048 | Length of the fingerprint bit vector (64-16384; ignored for MACCS) |
| **radius** | Integer | No | 2 | Radius for Morgan and FeatMorgan fingerprints (1-10) |
| **minPath** | Integer | No | 1 | Minimum path length for RDKit, Layered, and AtomPair fingerprints |
| **maxPath** | Integer | No | 7 | Maximum path length for RDKit, Layered, and AtomPair fingerprints |
| **torsionPathLength** | Integer | No | 4 | Path length for Torsion fingerprints |
| **useChirality** | Boolean | No | false | Whether to incorporate chirality information |
| **newColumnName** | String | No | "fingerprint" | Name for the new output column |
| **removeSourceColumn** | Boolean | No | false | Whether to remove the original SMILES column |

### Fingerprint Types

| Type | Description | Key Parameters |
|------|-------------|----------------|
| **Morgan** | Circular fingerprint (ECFP-like) based on atom environments | radius, numBits |
| **FeatMorgan** | Feature-based Morgan fingerprint (FCFP-like) using pharmacophoric atom invariants | radius, numBits |
| **AtomPair** | Encodes pairs of atoms and the shortest path between them | numBits |
| **Torsion** | Encodes topological torsion (4-atom paths by default) | numBits, torsionPathLength |
| **RDKit** | Daylight-style path-based fingerprint | numBits, minPath, maxPath |
| **Avalon** | Avalon toolkit fingerprint | numBits |
| **Layered** | Layered fingerprint encoding multiple structural features | numBits, minPath, maxPath |
| **MACCS** | 166-bit MACCS structural keys (fixed length, ignores numBits) | none |
| **Pattern** | Substructure pattern fingerprint | numBits |

---

## Example

**Input Table:**

| smiles | name |
|--------|------|
| c1ccccc1 | benzene |
| CC(=O)Oc1ccccc1C(=O)O | aspirin |
| INVALID_SMILES | bad |

**Configuration:**
- **smilesColumn**: `smiles`
- **fingerprintType**: `Morgan`
- **numBits**: `2048`
- **radius**: `2`
- **newColumnName**: `fingerprint`
- **removeSourceColumn**: `false`

**Output Table:**

| smiles | name | fingerprint |
|--------|------|-------------|
| c1ccccc1 | benzene | 00000...10010...00000 (2048 chars) |
| CC(=O)Oc1ccccc1C(=O)O | aspirin | 00100...01001...00010 (2048 chars) |
| INVALID_SMILES | bad | |

---

## Tips & Warnings

- **Invalid SMILES**: Rows with unparseable SMILES will get an empty string in the fingerprint column. The node will not fail or skip the row.
- **MACCS Fingerprints**: MACCS keys always produce a 166-bit fingerprint regardless of the numBits setting.
- **Morgan Radius**: A radius of 2 corresponds to ECFP4; radius 3 corresponds to ECFP6. Higher radii capture larger molecular neighborhoods but may increase hash collisions.
- **Bit Vector Length**: Larger numBits values reduce hash collisions but produce longer bit strings. 2048 is a common default for most applications.
- **FeatMorgan**: In this implementation, FeatMorgan uses the same algorithm as Morgan. Full feature-invariant support may vary by RDKit version.
- **Avalon Thread Safety**: The Avalon fingerprint computation uses a synchronized block internally for thread safety.
- **Memory Safety**: Both the molecule and fingerprint bit vector objects are deleted in `finally` blocks, preventing native memory leaks.
- **Remove Source Column**: Enable this to keep your table tidy when you no longer need the original SMILES representation.

---

## Technical Details

- **Algorithm**: Sequential row processing with streaming reader
- **Memory**: Processes one row at a time (suitable for large datasets)
- **Progress**: Reports percentage based on rows processed vs total rows
- **Resource Management**: Uses `.use {}` blocks for automatic stream cleanup and `finally` for RDKit native object deletion
- **Thread Safety**: Avalon fingerprint generation is synchronized; all other types are safe for concurrent use
