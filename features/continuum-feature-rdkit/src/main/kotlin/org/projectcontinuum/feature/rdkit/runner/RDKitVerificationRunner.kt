package org.projectcontinuum.feature.rdkit.runner

import org.projectcontinuum.feature.rdkit.service.RDKitService
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

/**
 * Runs on application startup and exercises every RDKit feature to verify the
 * native library is working correctly.  Output goes to the application log.
 */
@Component
class RDKitVerificationRunner(
    private val rdkit: RDKitService,
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(RDKitVerificationRunner::class.java)

    override fun run(args: ApplicationArguments) {

        val aspirin = "CC(=O)Oc1ccccc1C(=O)O"          // aspirin
        val caffeine = "Cn1c(=O)c2c(ncn2C)n(C)c1=O"    // caffeine
        val ibuprofen = "CC(C)Cc1ccc(cc1)C(C)C(=O)O"    // ibuprofen
        val benzene = "c1ccccc1"                          // benzene

        log.info("╔══════════════════════════════════════════════════════════════╗")
        log.info("║           RDKit Java Bindings – Feature Verification        ║")
        log.info("╚══════════════════════════════════════════════════════════════╝")

        // ── 1. SMILES parsing ──────────────────────────────────────────
        runTest("1. SMILES Parsing") {
            val info = rdkit.parseMolecule(aspirin)
            log.info("   Input          : {}", info.inputSmiles)
            log.info("   Canonical      : {}", info.canonicalSmiles)
            log.info("   Formula        : {}", info.molecularFormula)
            log.info("   Atoms / Bonds  : {} / {}", info.numAtoms, info.numBonds)
        }

        // ── 2. Molecular descriptors ───────────────────────────────────
        runTest("2. Molecular Descriptors (Aspirin)") {
            val d = rdkit.computeDescriptors(aspirin)
            log.info("   AMW            : {}", fmt(d.averageMolecularWeight))
            log.info("   ExactMW        : {}", fmt(d.exactMolecularWeight))
            log.info("   TPSA           : {}", fmt(d.topologicalPolarSurfaceArea))
            log.info("   Labute ASA     : {}", fmt(d.labuteASA))
            log.info("   CLogP / MR     : {} / {}", fmt(d.crippenLogP), fmt(d.crippenMR))
            log.info("   HBD / HBA      : {} / {}", d.numHBondDonors, d.numHBondAcceptors)
            log.info("   RotBonds       : {}", d.numRotatableBonds)
            log.info("   Rings (arom)   : {} ({})", d.numRings, d.numAromaticRings)
            log.info("   Fraction CSP3  : {}", fmt(d.fractionCSP3))
        }

        // ── 3. Substructure search ─────────────────────────────────────
        runTest("3. Substructure Search (Aspirin contains carboxylic acid?)") {
            val res = rdkit.substructureSearch(aspirin, "[CX3](=O)[OX2H1]")
            log.info("   Match?         : {}", res.hasMatch)
            log.info("   Match count    : {}", res.matchCount)
        }

        runTest("3b. Substructure Search (Benzene in Aspirin?)") {
            val res = rdkit.substructureSearch(aspirin, "c1ccccc1")
            log.info("   Match?         : {}", res.hasMatch)
            log.info("   Match count    : {}", res.matchCount)
        }

        // ── 4. Fingerprint similarity ──────────────────────────────────
        runTest("4. Tanimoto/Dice Similarity (Aspirin vs Ibuprofen)") {
            val sim = rdkit.computeSimilarity(aspirin, ibuprofen)
            log.info("   RDK  Tanimoto  : {}", fmt(sim.rdkTanimoto))
            log.info("   RDK  Dice      : {}", fmt(sim.rdkDice))
            log.info("   Morgan Tanimoto: {}", fmt(sim.morganTanimoto))
            log.info("   Morgan Dice    : {}", fmt(sim.morganDice))
        }

        runTest("4b. Self-similarity (Aspirin vs Aspirin)") {
            val sim = rdkit.computeSimilarity(aspirin, aspirin)
            log.info("   RDK  Tanimoto  : {} (expected 1.0)", fmt(sim.rdkTanimoto))
            log.info("   Morgan Tanimoto: {} (expected 1.0)", fmt(sim.morganTanimoto))
        }

        // ── 5. Format conversions ──────────────────────────────────────
        runTest("5. Format Conversions (Caffeine)") {
            val conv = rdkit.convertFormats(caffeine)
            log.info("   Canonical SMILES : {}", conv.canonicalSmiles)
            log.info("   InChI            : {}", conv.inchi)
            log.info("   InChIKey         : {}", conv.inchiKey)
            log.info("   SMARTS           : {}", conv.smarts)
            log.info("   MolBlock (first line): {}", conv.molBlock.lines().firstOrNull())
        }

        // ── 6. Murcko scaffold ─────────────────────────────────────────
        runTest("6. Murcko Scaffold Decomposition") {
            val s1 = rdkit.murckoScaffold(aspirin)
            log.info("   Aspirin   → {}", s1.scaffoldSmiles)
            val s2 = rdkit.murckoScaffold(ibuprofen)
            log.info("   Ibuprofen → {}", s2.scaffoldSmiles)
            val s3 = rdkit.murckoScaffold(caffeine)
            log.info("   Caffeine  → {}", s3.scaffoldSmiles)
        }

        // ── 7. 3D embedding ────────────────────────────────────────────
        runTest("7. 3D Coordinate Generation (Benzene)") {
            val emb = rdkit.embed3D(benzene)
            log.info("   Conformers     : {}", emb.numConformers)
            log.info("   MolBlock lines : {}", emb.molBlockWith3D.lines().size)
        }

        // ── 8. Ring analysis ───────────────────────────────────────────
        runTest("8. Ring Analysis (Caffeine)") {
            val r = rdkit.analyzeRings(caffeine)
            log.info("   Total rings        : {}", r.numRings)
            log.info("   Aromatic rings     : {}", r.numAromaticRings)
            log.info("   Aliphatic rings    : {}", r.numAliphaticRings)
            log.info("   Heterocycles       : {}", r.numHeterocycles)
            log.info("   Arom. heterocycles : {}", r.numAromaticHeterocycles)
        }

        // ── 9. Atom inspection ─────────────────────────────────────────
        runTest("9. Atom-level Inspection (Benzene)") {
            val atoms = rdkit.inspectAtoms(benzene)
            atoms.forEach { a ->
                log.info("   Atom[{}] {} (Z={}, aromatic={}, hybrid={})",
                    a.index, a.symbol, a.atomicNum, a.isAromatic, a.hybridization)
            }
        }

        // ── 10. Lipinski Rule of Five ──────────────────────────────────
        runTest("10. Lipinski Ro5 Check") {
            listOf(aspirin, caffeine, ibuprofen).forEach { smi ->
                val lip = rdkit.checkLipinski(smi)
                log.info("   {} → pass={} (MW={}, LogP={}, HBD={}, HBA={})",
                    lip.smiles, lip.passesRuleOfFive,
                    fmt(lip.molecularWeight), fmt(lip.logP),
                    lip.numHBondDonors, lip.numHBondAcceptors)
                if (lip.violations.isNotEmpty()) {
                    log.info("     violations: {}", lip.violations)
                }
            }
        }

        log.info("╔══════════════════════════════════════════════════════════════╗")
        log.info("║       ✅ All RDKit feature verifications completed!         ║")
        log.info("╚══════════════════════════════════════════════════════════════╝")
    }

    // ── helpers ────────────────────────────────────────────────────
    private fun runTest(name: String, block: () -> Unit) {
        log.info("┌─ {} ──────────────────────────────────", name)
        try {
            block()
            log.info("└─ ✅ PASSED")
        } catch (e: Exception) {
            log.error("└─ ❌ FAILED: {}", e.message, e)
        }
    }

    private fun fmt(d: Double) = String.format("%.4f", d)
}


