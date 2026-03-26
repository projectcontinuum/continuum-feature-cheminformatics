<p align="center">
  <img src="https://img.shields.io/badge/RDKit-Powered-3B82F6?style=for-the-badge&logo=data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAyNCAyNCI+PHBhdGggZmlsbD0id2hpdGUiIGQ9Ik0xMiAyTDIgN3Y1aDJ2N2g1djRoNnYtNGg1di03aDJWN0wxMiAyem0wIDIuMTggNy4wOSAzLjU0TDEyIDExLjI3IDQuOTEgNy43MiAxMiA0LjE4eiIvPjwvc3ZnPg==" alt="RDKit Powered"/>
  <img src="https://img.shields.io/badge/Nodes-38-10B981?style=for-the-badge" alt="38 Nodes"/>
  <img src="https://img.shields.io/badge/Categories-10-8B5CF6?style=for-the-badge" alt="10 Categories"/>
  <img src="https://img.shields.io/badge/Descriptors-41-F59E0B?style=for-the-badge" alt="41 Descriptors"/>
  <img src="https://img.shields.io/badge/Fingerprints-9_Types-EF4444?style=for-the-badge" alt="9 Fingerprint Types"/>
</p>

# Continuum Feature Cheminformatics

Production-grade cheminformatics workflow nodes for the [Continuum](https://github.com/projectcontinuum/Continuum) platform, powered by [RDKit](https://www.rdkit.org/). Designed for computational chemistry, medicinal chemistry, and drug discovery teams who need scalable, reproducible molecular processing pipelines.

> **From SMILES to insights** -- parse, standardize, fingerprint, filter, react, and visualize molecules at scale, all within declarative, versionable Continuum workflows.

---

## Why This Module

| Challenge | How Continuum Cheminformatics Helps |
|-----------|-------------------------------------|
| **Compound library curation** | Standardize structures with salt stripping, aromatization, canonical SMILES, and structure normalization nodes |
| **Hit triage & filtering** | Apply PAINS, BRENK, NIH, and ZINC structural alert catalogs; filter by substructure, functional groups, or Lipinski properties |
| **SAR analysis** | Decompose molecules into Murcko scaffolds and R-groups; compute 41 molecular descriptors in a single node |
| **Virtual screening** | Generate 9 fingerprint types, compute Tanimoto/Dice similarity, and select diverse subsets via MaxMin picking |
| **Reaction enumeration** | Run one- and two-component reactions using SMARTS-based reaction templates at scale |
| **3D modeling prep** | Generate conformers, optimize geometries with MMFF94/UFF force fields, and align molecules in 3D |
| **Reproducibility** | Every step is a versioned workflow node with explicit configuration -- no hidden scripts or ad-hoc notebooks |

---

## Supported Capabilities at a Glance

### Fingerprint Algorithms

Both bit-based and count-based representations are supported:

| Algorithm | Type | Typical Use Case |
|-----------|------|------------------|
| **Morgan (ECFP)** | Circular | General-purpose similarity, QSAR, virtual screening |
| **FeatMorgan (FCFP)** | Circular (pharmacophoric) | Pharmacophore-aware similarity |
| **AtomPair** | Topological | Scaffold hopping, diversity analysis |
| **Torsion** | Topological | Conformational similarity |
| **RDKit** | Path-based | Substructure-oriented similarity |
| **Avalon** | Substructure | Fast screening, patent searching |
| **Layered** | Path-based (layered) | Similarity with tunable abstraction layers |
| **MACCS** | Structural keys (166-bit) | Quick pharmacophore screening |
| **Pattern** | Substructure | Substructure matching fingerprints |

### Molecular Descriptors (41 Available)

Compute any combination of descriptors in a single pass:

| Category | Descriptors |
|----------|-------------|
| **Physicochemical** | AMW, ExactMW, SlogP, SMR, LabuteASA, TPSA |
| **Hydrogen Bonding** | NumLipinskiHBA, NumLipinskiHBD, NumHBD, NumHBA |
| **Structural Counts** | NumRotatableBonds, NumAmideBonds, NumHeteroAtoms, NumHeavyAtoms, NumAtoms |
| **Stereochemistry** | NumStereocenters, NumUnspecifiedStereocenters |
| **Ring Systems** | NumRings, NumAromaticRings, NumSaturatedRings, NumAliphaticRings, NumAromaticHeterocycles, NumSaturatedHeterocycles, NumAliphaticHeterocycles, NumAromaticCarbocycles, NumSaturatedCarbocycles, NumAliphaticCarbocycles |
| **Complexity** | FractionCSP3 |
| **Connectivity (Chi)** | Chi0v, Chi1v, Chi2v, Chi3v, Chi4v, Chi1n, Chi2n, Chi3n, Chi4n |
| **Shape (Kier)** | HallKierAlpha, kappa1, kappa2, kappa3 |

### Structural Alert Catalogs

The **Molecule Catalog Filter** node screens compounds against established medicinal chemistry filter sets:

| Catalog | Purpose |
|---------|---------|
| **PAINS** | Pan-Assay Interference Compounds -- flags promiscuous HTS artifacts |
| **BRENK** | Brenk structural alerts -- identifies unstable or reactive motifs |
| **NIH** | NIH MLSMR exclusion filters |
| **ZINC** | ZINC database drug-likeness filters |

---

## Node Reference

### Calculators

Nodes for computing molecular properties and descriptors.

| Node | Description |
|------|-------------|
| **Calculate Charges** | Computes Gasteiger partial charges per atom using RDKit |
| **Descriptor Calculation** | Calculates up to 41 molecular descriptors from SMILES strings in a single pass |

### Converters

Nodes for parsing and converting between molecular representations (SMILES, InChI, SDF, SMARTS).

| Node | Description |
|------|-------------|
| **Canonical SMILES** | Converts SMILES strings to their canonical form using RDKit |
| **InChI to Molecule** | Converts InChI strings to canonical SMILES using RDKit |
| **Molecule to InChI** | Converts SMILES strings to InChI and InChI Key using RDKit |
| **Molecule Writer** | Converts SMILES strings to different molecule formats (SDF, SMARTS, etc.) using RDKit |
| **SMILES Parser** | Parses molecule strings (SMILES, SDF, SMARTS) into canonical SMILES with error routing |

### Experimental

Advanced nodes under active development for specialized workflows.

| Node | Description |
|------|-------------|
| **Adjust Query Properties** | Converts molecules to flexible substructure queries by adjusting atom and bond query properties |
| **Molecule Catalog Filter** | Filters molecules against structural alert catalogs (PAINS, BRENK, NIH, ZINC) using RDKit FilterCatalog |
| **R-Group Decomposition** | Decomposes molecules into a core scaffold and R-groups using RDKit RGroupDecomposition |
| **Structure Normalizer** | Applies configurable normalization steps to standardize molecule SMILES representations |

### Fingerprints

Nodes for generating molecular fingerprints and computing chemical similarity.

| Node | Description |
|------|-------------|
| **Count-Based Fingerprint** | Generates count-based fingerprints (Morgan, AtomPair, Torsion) as JSON {index: count} maps |
| **Diversity Picker** | Selects a diverse subset of molecules using MaxMin diversity picking with RDKit fingerprints |
| **Fingerprint** | Generates bit-based fingerprints supporting 9 algorithms (Morgan, FeatMorgan, AtomPair, Torsion, RDKit, Avalon, Layered, MACCS, Pattern) |
| **Fingerprint Similarity** | Computes pairwise Tanimoto or Dice similarity from two SMILES columns |

### Fragments

Nodes for molecular fragmentation, scaffold extraction, and decomposition.

| Node | Description |
|------|-------------|
| **Molecule Fragmenter** | Fragments molecules using Murcko decomposition and outputs scaffold plus side chains |
| **Molecule Extractor** | Splits multi-component molecules (e.g., salts, mixtures) into individual fragments, one row per fragment |
| **Murcko Scaffold** | Extracts the Murcko scaffold (core ring structure) from molecules using RDKit |

### Geometry

Nodes for 3D coordinate generation, conformer sampling, force field optimization, and molecular alignment.

| Node | Description |
|------|-------------|
| **Add Conformers** | Generates multiple 3D conformers using RDKit distance geometry (ETKDG) |
| **Add Coordinates** | Generates 2D or 3D coordinates for molecules from SMILES strings |
| **Open 3D Alignment** | Aligns query molecules against reference molecules in 3D using Open3DAlign |
| **Optimize Geometry** | Optimizes 3D molecular geometry using MMFF94 or UFF force fields |
| **RMSD Filter** | Filters conformers/molecules using greedy RMSD-based diversity selection |

### Modifiers

Nodes for chemical structure standardization and modification.

| Node | Description |
|------|-------------|
| **Add Hydrogens** | Adds explicit hydrogens to molecules using RDKit |
| **Aromatize** | Applies aromaticity perception to SMILES strings using RDKit |
| **Kekulize** | Converts aromatic SMILES to Kekule form (explicit double bonds) using RDKit |
| **Remove Hydrogens** | Removes explicit hydrogens from SMILES strings using RDKit |
| **Salt Stripper** | Removes salt/counterion fragments from SMILES strings using RDKit |

### Reactions

Nodes for combinatorial chemistry and reaction enumeration using SMARTS templates.

| Node | Description |
|------|-------------|
| **Chemical Transformation** | Applies chemical transformations iteratively to molecules using reaction SMARTS |
| **One Component Reaction** | Runs one-component reactions (e.g., deprotection, oxidation) on reactant molecules |
| **Two Component Reaction** | Runs two-component reactions (e.g., amide coupling, Suzuki) on pairs of reactant molecules |

### Rendering

Nodes for molecular visualization and depiction.

| Node | Description |
|------|-------------|
| **RDKit to SVG** | Renders molecules as publication-quality SVG images using RDKit MolDraw2DSVG |

### Searching

Nodes for substructure searching, pattern matching, and maximum common substructure analysis.

| Node | Description |
|------|-------------|
| **Functional Group Filter** | Filters molecules by functional group presence and count using SMARTS patterns |
| **Maximum Common Substructure** | Finds the MCS among all input molecules -- useful for SAR and lead series identification |
| **Molecule Substructure Filter** | Filters molecules against a table of SMILES-based substructure queries, splitting matches and non-matches |
| **Substructure Counter** | Counts substructure matches for each molecule against a table of query patterns |
| **Substructure Filter** | Filters molecules by SMARTS substructure query, splitting matches and non-matches into separate output ports |

### Testing

Nodes for validation and quality assurance of molecular data pipelines.

| Node | Description |
|------|-------------|
| **SDF Difference Checker** | Compares two tables of molecules row by row, identifying SMILES, property, and coordinate differences |

---

## Example Workflow Patterns

Below are common cheminformatics pipeline patterns you can build by connecting these nodes:

### Compound Library Standardization

```
SMILES Parser --> Salt Stripper --> Structure Normalizer --> Canonical SMILES --> Descriptor Calculation
```

### Hit Triage Pipeline

```
                                      +--> Molecule Catalog Filter (PAINS) --> [Clean Hits]
SMILES Parser --> Descriptor Calc --> |
                                      +--> Functional Group Filter --> [Flagged Compounds]
```

### Similarity Search & Diversity Selection

```
SMILES Parser --> Fingerprint (Morgan) --> Fingerprint Similarity --> Diversity Picker --> [Diverse Subset]
```

### Reaction Enumeration

```
[Reactant A] --\
                +--> Two Component Reaction (Suzuki SMARTS) --> Descriptor Calculation --> [Product Library]
[Reactant B] --/
```

### 3D Conformer Generation Pipeline

```
SMILES Parser --> Add Coordinates (3D) --> Add Conformers --> Optimize Geometry (MMFF94) --> RMSD Filter --> [Diverse Conformers]
```

---

## Project Structure

```
continuum-feature-cheminformatics/
├── features/
│   └── continuum-feature-rdkit/           # RDKit cheminformatics nodes
│       ├── build.gradle.kts
│       ├── lib/org.RDKit.jar              # RDKit Java/JNI bindings
│       └── src/
│           ├── main/kotlin/.../node/      # 38 node implementations
│           └── test/kotlin/.../node/      # Unit tests for every node
├── worker/                                # Spring Boot worker application
├── docker/                                # Local dev infrastructure (Temporal, Kafka, MinIO)
├── test-workflows/                        # Automated test workflows organized by category
│   └── rdkit/
│       ├── 01-converters/
│       ├── 02-modifiers/
│       ├── 03-calculators/
│       ├── 04-geometry/
│       ├── 05-fingerprints/
│       ├── 06-fragments/
│       ├── 07-searching/
│       ├── 08-reactions/
│       ├── 09-rendering/
│       ├── 10-experimental/
│       └── 11-testing/
├── settings.gradle.kts
└── gradle.properties
```

---

## Technology Stack

| Component | Technology | Purpose |
|-----------|-----------|---------|
| **Cheminformatics Engine** | [RDKit](https://www.rdkit.org/) (via Java/JNI bindings) | Molecular parsing, fingerprinting, descriptors, reactions, 3D geometry |
| **Workflow Orchestration** | [Temporal](https://temporal.io/) | Distributed, fault-tolerant workflow execution |
| **Event Streaming** | [Apache Kafka](https://kafka.apache.org/) | Progress reporting, workflow event bus |
| **Data Storage** | [Apache Parquet](https://parquet.apache.org/) on S3/MinIO | Columnar storage for tabular molecular data |
| **Runtime** | [Spring Boot](https://spring.io/projects/spring-boot) + JDK 21 | Worker application framework |
| **Build** | [Gradle](https://gradle.org/) (Kotlin DSL) | Multi-module build with Jib containerization |

---

## Getting Started

### Prerequisites

- **JDK 21** -- [Eclipse Temurin](https://adoptium.net/) recommended
- **Docker & Docker Compose** -- for local infrastructure
- **GitHub PAT** with `read:packages` scope -- for downloading Continuum dependencies

### Quick Start

```bash
# 1. Clone the repository
git clone <repository-url>
cd continuum-feature-cheminformatics

# 2. Set GitHub credentials (for Continuum dependency resolution)
export GITHUB_USERNAME=your-username
export GITHUB_TOKEN=ghp_your-token

# 3. Start local infrastructure
cd docker && docker compose up -d && cd ..

# 4. Build the project
./gradlew build

# 5. Run the worker
./gradlew :worker:bootRun
```

Your cheminformatics nodes are now registered with Temporal and ready to execute workflows.

For detailed setup instructions and how to create custom nodes, see the [Continuum Feature Template](https://github.com/projectcontinuum/continuum-feature-template).

---

## Related Repositories

| Repository | Description |
|-----------|-------------|
| [Continuum](https://github.com/projectcontinuum/Continuum) | Core platform -- API server, worker framework, shared libraries |
| [continuum-workbench](https://github.com/projectcontinuum/continuum-workbench) | Browser-based workflow editor (Eclipse Theia + React Flow) |
| [continuum-feature-base](https://github.com/projectcontinuum/continuum-feature-base) | Base analytics nodes -- data transforms, REST, scripting |
| [continuum-feature-ai](https://github.com/projectcontinuum/continuum-feature-ai) | AI/ML nodes -- LLM fine-tuning with Unsloth + LoRA |
| [continuum-feature-template](https://github.com/projectcontinuum/continuum-feature-template) | Template for scaffolding new feature modules |
