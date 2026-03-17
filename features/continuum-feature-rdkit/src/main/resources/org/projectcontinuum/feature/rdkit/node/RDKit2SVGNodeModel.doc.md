# RDKit to SVG Node

Render molecules as SVG (Scalable Vector Graphics) images using RDKit's MolDraw2DSVG drawing engine. This node produces publication-quality 2D depictions of molecules that can be displayed directly in web browsers or exported as images.

---

## What It Does

The RDKit to SVG Node reads a SMILES string from each row, generates 2D coordinates if the molecule doesn't already have them, and renders the molecule as an SVG string. The SVG output includes proper namespace declarations for direct use in web pages. Optional atom highlighting allows visual emphasis on specific atoms (e.g., substructure match results).

**Key Points:**
- Generates 2D coordinates automatically if not present
- Configurable SVG width and height
- Optional atom highlighting with custom color
- SVG output includes proper XML namespace
- Invalid SMILES produce an empty string — no error is thrown
- Native RDKit drawing objects are properly cleaned up after each row

---

## Configuration

### Input Ports
| Port | Description |
|------|-------------|
| **input** | Input table containing a column with SMILES strings |

### Output Ports
| Port | Description |
|------|-------------|
| **output** | Table with all original columns plus the SVG string column |

### Properties
| Property | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| **smilesColumn** | String | Yes | - | Name of the column containing SMILES strings |
| **svgColumnName** | String | No | "svg" | Name for the output SVG column |
| **width** | Integer | No | 300 | SVG width in pixels (50–2000) |
| **height** | Integer | No | 300 | SVG height in pixels (50–2000) |
| **highlightAtoms** | String | No | "" | Comma-separated atom indices to highlight |
| **highlightColor** | String | No | "#FF0000" | Highlight color as hex string |
| **removeSourceColumn** | Boolean | No | false | Whether to remove the original SMILES column |

---

## Example

**Input Table:**

| smiles | name |
|--------|------|
| c1ccccc1 | benzene |
| CC(=O)O | acetic acid |
| INVALID_SMILES | bad |

**Configuration:**
- **smilesColumn**: `smiles`
- **svgColumnName**: `svg`
- **width**: `300`
- **height**: `300`

**Output Table:**

| smiles | name | svg |
|--------|------|-----|
| c1ccccc1 | benzene | `<svg xmlns='...' width='300' height='300'>...</svg>` |
| CC(=O)O | acetic acid | `<svg xmlns='...' width='300' height='300'>...</svg>` |
| INVALID_SMILES | bad | |

---

## Tips & Warnings

- **Highlighting**: Use the `highlightAtoms` property with comma-separated 0-based atom indices (e.g., "0,1,2" to highlight the first three atoms). Atom indices beyond the molecule size are silently ignored.
- **SVG Namespace**: The node ensures `xmlns="http://www.w3.org/2000/svg"` is present so the output can be embedded directly in HTML.
- **2D Coordinates**: The node automatically computes 2D coordinates using RDKit's `compute2DCoords()` if the molecule has no conformers.
- **Invalid SMILES**: Rows with unparseable SMILES will get an empty string in the SVG column.
- **Memory Safety**: Each RDKit molecule and drawer object is deleted in a `finally` block, preventing native memory leaks.

---

## Technical Details

- **Algorithm**: Sequential row processing with streaming reader
- **Memory**: Processes one row at a time (suitable for large datasets)
- **Progress**: Reports percentage based on rows processed vs total rows
- **Resource Management**: Uses `.use {}` blocks for automatic stream cleanup and `finally` for RDKit native object deletion

