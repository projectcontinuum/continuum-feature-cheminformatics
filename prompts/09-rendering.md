# RDKit Rendering Nodes (1 node)

> Read `00-preamble.md` first for the full Continuum node pattern, RDKit API rules, and shared utilities.

---

## Create RDKit2SVGNodeModel
- One input port "input" Table containing molecules as SMILES strings
- Properties:
  - smilesColumn (string, required — column containing SMILES)
  - svgColumnName (string, default "svg" — output column for SVG string)
  - width (integer, default 300, min 50, max 2000 — SVG width in pixels)
  - height (integer, default 300, min 50, max 2000 — SVG height in pixels)
  - highlightAtoms (string, default "" — optional comma-separated atom indices to highlight)
  - highlightColor (string, default "#FF0000" — highlight color as hex)
  - removeSourceColumn (boolean, default false)
- Output port "output" Table with original columns plus SVG string column
- UI Schema: React Material JSONForm VerticalLayout with controls for smilesColumn, svgColumnName, width, height, highlightAtoms, highlightColor, removeSourceColumn
- Behavior:
  1. Read properties, validate smilesColumn
  2. For each row, parse SMILES with `RDKFuncs.SmilesToMol(smiles)`
  3. Generate 2D coordinates if not present: check `mol.getNumConformers()`, if 0 → `RDKFuncs.compute2DCoords(mol)`
  4. Create SVG drawing:
     - `val drawer = MolDraw2DSVG(width, height)`
     - If highlightAtoms specified: parse comma-separated indices into `Int_Vect`
     - `drawer.drawMolecule(mol)` — or with highlight: `drawer.drawMolecule(mol, "", highlightAtomsVect)`
     - `drawer.finishDrawing()`
     - `val svg = drawer.getDrawingText()`
  5. Fix SVG namespace if needed: ensure `xmlns="http://www.w3.org/2000/svg"` is present
  6. `mol.delete()` in finally
  7. Write output row with SVG string
- Thinking: Renders molecules as SVG images for display/export. 2D coordinates are required. The MolDraw2DSVG class produces clean SVG. Highlighting allows visual emphasis on specific atoms (e.g., substructure match results). Mirrors KNIME RDKit2SVG.
- Category: ["RDKit", "Rendering"]
- Example Properties: {
    "smilesColumn": "smiles",
    "svgColumnName": "svg",
    "width": 300,
    "height": 300,
    "highlightAtoms": "",
    "highlightColor": "#FF0000",
    "removeSourceColumn": false
  }
- Detailed Example Input: [
    {"smiles": "c1ccccc1", "name": "benzene"},
    {"smiles": "CC(=O)O", "name": "acetic_acid"}
  ]
- Detailed Example Output: [
    {"smiles": "c1ccccc1", "name": "benzene", "svg": "<svg xmlns='http://www.w3.org/2000/svg' width='300' height='300'>...</svg>"},
    {"smiles": "CC(=O)O", "name": "acetic_acid", "svg": "<svg xmlns='http://www.w3.org/2000/svg' width='300' height='300'>...</svg>"}
  ]
