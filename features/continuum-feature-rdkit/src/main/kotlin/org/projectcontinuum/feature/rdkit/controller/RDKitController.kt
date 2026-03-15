package org.projectcontinuum.feature.rdkit.controller

import org.projectcontinuum.feature.rdkit.service.RDKitService
import org.springframework.web.bind.annotation.*

/**
 * REST controller exposing RDKit cheminformatics operations.
 *
 * Every endpoint exercises a different area of the RDKit native library
 * to verify that the Java/JNI bindings work end-to-end.
 */
@RestController
@RequestMapping("/api/rdkit")
class RDKitController(private val rdkitService: RDKitService) {

    // ── 1. Parse SMILES → basic molecule info ──────────────────
    @GetMapping("/parse")
    fun parseMolecule(@RequestParam smiles: String) =
        rdkitService.parseMolecule(smiles)

    // ── 2. Molecular descriptors ───────────────────────────────
    @GetMapping("/descriptors")
    fun descriptors(@RequestParam smiles: String) =
        rdkitService.computeDescriptors(smiles)

    // ── 3. Substructure search ─────────────────────────────────
    @GetMapping("/substruct")
    fun substructSearch(
        @RequestParam smiles: String,
        @RequestParam smarts: String,
    ) = rdkitService.substructureSearch(smiles, smarts)

    // ── 4. Fingerprint similarity ──────────────────────────────
    @GetMapping("/similarity")
    fun similarity(
        @RequestParam smiles1: String,
        @RequestParam smiles2: String,
    ) = rdkitService.computeSimilarity(smiles1, smiles2)

    // ── 5. Format conversions (MolBlock, InChI, SMARTS) ────────
    @GetMapping("/convert")
    fun convert(@RequestParam smiles: String) =
        rdkitService.convertFormats(smiles)

    // ── 6. Murcko scaffold ─────────────────────────────────────
    @GetMapping("/scaffold")
    fun scaffold(@RequestParam smiles: String) =
        rdkitService.murckoScaffold(smiles)

    // ── 7. 3D embedding ────────────────────────────────────────
    @GetMapping("/embed3d")
    fun embed3d(@RequestParam smiles: String) =
        rdkitService.embed3D(smiles)

    // ── 8. Ring analysis ───────────────────────────────────────
    @GetMapping("/rings")
    fun rings(@RequestParam smiles: String) =
        rdkitService.analyzeRings(smiles)

    // ── 9. Atom-level inspection ───────────────────────────────
    @GetMapping("/atoms")
    fun atoms(@RequestParam smiles: String) =
        rdkitService.inspectAtoms(smiles)

    // ── 10. Lipinski Rule of Five ──────────────────────────────
    @GetMapping("/lipinski")
    fun lipinski(@RequestParam smiles: String) =
        rdkitService.checkLipinski(smiles)
}

