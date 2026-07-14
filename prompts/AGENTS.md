# prompts

## Purpose

AI agent prompt library for implementing and extending cheminformatics nodes. Contains the full specification, architecture guide, and per-category implementation prompts used to drive node development via LLM assistants.

## Ownership

Updated whenever node patterns, architecture, or categories change.

## Local Contracts

- `00-preamble.md` — canonical node pattern, RDKit JNI rules, data model mapping, error handling, category conventions, `RDKitNodeHelper` spec. **Read this first before reading any other prompt file.**
- Numbered prompt files `01-converters.md` through `11-testing.md` each cover one node category matching the corresponding test-workflow phase
- File index (from preamble):

| File | Category | Node count |
|---|---|---|
| `01-converters.md` | Converters | 5 |
| `02-modifiers.md` | Modifiers | 5 |
| `03-calculators.md` | Calculators | 2 |
| `04-geometry.md` | Geometry | 5 |
| `05-fingerprints.md` | Fingerprints | 4 |
| `06-fragments.md` | Fragments | 3 |
| `07-searching.md` | Searching | 5 |
| `08-reactions.md` | Reactions | 3 |
| `09-rendering.md` | Rendering | 1 |
| `10-experimental.md` | Experimental | 4 |
| `11-testing.md` | Testing | 1 |

## Work Guidance

- When adding a new node, also add its specification to the appropriate numbered prompt file
- When node patterns change (new ports, new error handling, new helper methods), update `00-preamble.md` first, then update affected category files
- Category numbering must stay in sync with `test-workflows/rdkit/` subdirectory numbering

## Verification

No automated check. Manual: verify node count per file matches what's implemented in `features/continuum-feature-rdkit/src/main/kotlin/.../node/`.

## Child DOX Index

No child AGENTS.md.
