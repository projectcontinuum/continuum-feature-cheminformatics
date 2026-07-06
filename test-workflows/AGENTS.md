# test-workflows

## Purpose

End-to-end test workflows for all RDKit cheminformatics nodes. Each `.cwf` workflow file exercises one node using a standard four-node pattern and is the primary integration-level verification for node correctness.

## Ownership

Updated when a node is added, renamed, or its behavior changes.

## Local Contracts

- All workflows live under `rdkit/` organized by category, mirroring the `prompts/` numbering
- Directory: `rdkit/<phase>/<NodeModelName>/test-<kebab-name>.cwf`
- Standard 4-node workflow pattern:
  1. **CreateTableNodeModel** — input molecules as SMILES
  2. **Node under test** — the RDKit node being verified
  3. **CreateTableNodeModel** — expected output rows
  4. **SDFDifferenceCheckerNodeModel** — compares actual vs expected; outputs `match_status` per row (`match`, `mismatch`, `left_only`, `right_only`)
- Test passes when all rows have `match_status = match`

- Phase/category mapping:

| Phase | Category | Nodes covered |
|---|---|---|
| 01-converters | Converters | CanonicalSmiles, InChIToMolecule, MoleculeToInChI, MoleculeWriter, SmilesParser |
| 02-modifiers | Modifiers | AddHs, Aromatize, Kekulize, RemoveHs, SaltStripper |
| 03-calculators | Calculators | CalculateCharges, DescriptorCalculation |
| 04-geometry | Geometry | AddConformers, AddCoordinates, OptimizeGeometry |
| 05-fingerprints | Fingerprints | DiversityPicker, Fingerprint, FingerprintSimilarity |
| 06-fragments | Fragments | MolFragmenter, MoleculeExtractor, MurckoScaffold |
| 07-searching | Searching | MCS, SubstructFilter |
| 08-reactions | Reactions | OneComponentReaction, TwoComponentReaction |
| 09-rendering | Rendering | RDKit2SVG |
| 10-experimental | Experimental | StructureNormalizer |
| 11-testing | Testing | SDFDifferenceChecker |

- Common test molecules: benzene (`c1ccccc1`), ethanol (`CCO`), aspirin (`CC(=O)Oc1ccccc1C(=O)O`), phenol (`c1ccc(O)cc1`), aniline (`c1ccc(N)cc1`)

## Work Guidance

- When adding a new node, create `rdkit/<phase>/<NodeModelName>/test-<kebab-name>.cwf` following the 4-node pattern
- Test cases must cover: normal operation, invalid SMILES input, and any node-specific configuration variants
- Phase number must match the category numbering in `prompts/` and the node's `categories` list

## Verification

Run workflows via Continuum Workbench or the Continuum API. Check `SDFDifferenceChecker` output for all `match` rows. The test runner is `RDKitVerificationRunner.kt` in the feature subproject.

## Child DOX Index

No child AGENTS.md.
