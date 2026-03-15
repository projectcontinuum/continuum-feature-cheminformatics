package org.projectcontinuum.feature.rdkit.service

import org.RDKit.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Service that wraps various RDKit cheminformatics operations.
 * Each method exercises a different capability of the RDKit library.
 */
@Service
class RDKitService {

    private val log = LoggerFactory.getLogger(RDKitService::class.java)

    // ────────────────────────────────────────────────────────────
    //  1. SMILES parsing & canonical SMILES
    // ────────────────────────────────────────────────────────────

    data class MoleculeInfo(
        val inputSmiles: String,
        val canonicalSmiles: String,
        val numAtoms: Long,
        val numHeavyAtoms: Long,
        val numBonds: Long,
        val molecularFormula: String,
    )

    fun parseMolecule(smiles: String): MoleculeInfo {
        val mol = RDKFuncs.SmilesToMol(smiles)
            ?: throw IllegalArgumentException("Invalid SMILES: $smiles")
        try {
            val canonical = RDKFuncs.MolToSmiles(mol)
            val formula = RDKFuncs.getMolFormula(mol)
            return MoleculeInfo(
                inputSmiles = smiles,
                canonicalSmiles = canonical,
                numAtoms = mol.getNumAtoms(),
                numHeavyAtoms = mol.getNumHeavyAtoms(),
                numBonds = mol.getNumBonds(),
                molecularFormula = formula,
            )
        } finally {
            mol.delete()
        }
    }

    // ────────────────────────────────────────────────────────────
    //  2. Molecular descriptors
    // ────────────────────────────────────────────────────────────

    data class MolecularDescriptors(
        val smiles: String,
        val averageMolecularWeight: Double,
        val exactMolecularWeight: Double,
        val topologicalPolarSurfaceArea: Double,
        val labuteASA: Double,
        val crippenLogP: Double,
        val crippenMR: Double,
        val numHBondDonors: Long,
        val numHBondAcceptors: Long,
        val numRotatableBonds: Long,
        val numRings: Long,
        val numAromaticRings: Long,
        val numHeteroatoms: Long,
        val fractionCSP3: Double,
        val formalCharge: Int,
    )

    fun computeDescriptors(smiles: String): MolecularDescriptors {
        val mol = RDKFuncs.SmilesToMol(smiles)
            ?: throw IllegalArgumentException("Invalid SMILES: $smiles")
        try {
            val crippen = RDKFuncs.calcCrippenDescriptors(mol)
            return MolecularDescriptors(
                smiles = RDKFuncs.MolToSmiles(mol),
                averageMolecularWeight = RDKFuncs.calcAMW(mol),
                exactMolecularWeight = RDKFuncs.calcExactMW(mol),
                topologicalPolarSurfaceArea = RDKFuncs.calcTPSA(mol),
                labuteASA = RDKFuncs.calcLabuteASA(mol),
                crippenLogP = crippen.getFirst(),
                crippenMR = crippen.getSecond(),
                numHBondDonors = RDKFuncs.calcNumHBD(mol),
                numHBondAcceptors = RDKFuncs.calcNumHBA(mol),
                numRotatableBonds = RDKFuncs.calcNumRotatableBonds(mol),
                numRings = RDKFuncs.calcNumRings(mol),
                numAromaticRings = RDKFuncs.calcNumAromaticRings(mol),
                numHeteroatoms = RDKFuncs.calcNumHeteroatoms(mol),
                fractionCSP3 = RDKFuncs.calcFractionCSP3(mol),
                formalCharge = RDKFuncs.getFormalCharge(mol),
            )
        } finally {
            mol.delete()
        }
    }

    // ────────────────────────────────────────────────────────────
    //  3. Substructure search
    // ────────────────────────────────────────────────────────────

    data class SubstructureResult(
        val moleculeSmiles: String,
        val querySmarts: String,
        val hasMatch: Boolean,
        val matchCount: Int,
        val atomMappings: List<List<Pair<Int, Int>>>,
    )

    fun substructureSearch(smiles: String, smarts: String): SubstructureResult {
        val mol = RDKFuncs.SmilesToMol(smiles)
            ?: throw IllegalArgumentException("Invalid SMILES: $smiles")
        val query = RDKFuncs.SmartsToMol(smarts)
            ?: throw IllegalArgumentException("Invalid SMARTS: $smarts")
        try {
            val hasMatch = mol.hasSubstructMatch(query)
            val matches = mol.getSubstructMatches(query)
            val mappings = mutableListOf<List<Pair<Int, Int>>>()
            for (i in 0 until matches.size().toInt()) {
                val match = matches.get(i)
                val pairs = mutableListOf<Pair<Int, Int>>()
                for (j in 0 until match.size().toInt()) {
                    val pair = match.get(j)
                    pairs.add(Pair(pair.getFirst(), pair.getSecond()))
                }
                mappings.add(pairs)
            }
            return SubstructureResult(
                moleculeSmiles = RDKFuncs.MolToSmiles(mol),
                querySmarts = smarts,
                hasMatch = hasMatch,
                matchCount = matches.size().toInt(),
                atomMappings = mappings,
            )
        } finally {
            mol.delete()
            query.delete()
        }
    }

    // ────────────────────────────────────────────────────────────
    //  4. Fingerprints & Tanimoto similarity
    // ────────────────────────────────────────────────────────────

    data class SimilarityResult(
        val smiles1: String,
        val smiles2: String,
        val rdkTanimoto: Double,
        val rdkDice: Double,
        val morganTanimoto: Double,
        val morganDice: Double,
    )

    fun computeSimilarity(smiles1: String, smiles2: String): SimilarityResult {
        val mol1 = RDKFuncs.SmilesToMol(smiles1)
            ?: throw IllegalArgumentException("Invalid SMILES: $smiles1")
        val mol2 = RDKFuncs.SmilesToMol(smiles2)
            ?: throw IllegalArgumentException("Invalid SMILES: $smiles2")
        try {
            // RDK fingerprints
            val rdkFp1 = RDKFuncs.RDKFingerprintMol(mol1)
            val rdkFp2 = RDKFuncs.RDKFingerprintMol(mol2)
            val rdkTanimoto = RDKFuncs.TanimotoSimilarity(rdkFp1, rdkFp2)
            val rdkDice = RDKFuncs.DiceSimilarity(rdkFp1, rdkFp2)

            // Morgan fingerprints (radius=2, 2048 bits – equivalent to ECFP4)
            val morganFp1 = RDKFuncs.getMorganFingerprintAsBitVect(mol1, 2, 2048)
            val morganFp2 = RDKFuncs.getMorganFingerprintAsBitVect(mol2, 2, 2048)
            val morganTanimoto = RDKFuncs.TanimotoSimilarity(morganFp1, morganFp2)
            val morganDice = RDKFuncs.DiceSimilarity(morganFp1, morganFp2)

            return SimilarityResult(
                smiles1 = RDKFuncs.MolToSmiles(mol1),
                smiles2 = RDKFuncs.MolToSmiles(mol2),
                rdkTanimoto = rdkTanimoto,
                rdkDice = rdkDice,
                morganTanimoto = morganTanimoto,
                morganDice = morganDice,
            )
        } finally {
            mol1.delete()
            mol2.delete()
        }
    }

    // ────────────────────────────────────────────────────────────
    //  5. Format conversions (SMILES ↔ MolBlock, InChI, SMARTS)
    // ────────────────────────────────────────────────────────────

    data class FormatConversions(
        val canonicalSmiles: String,
        val molBlock: String,
        val v3kMolBlock: String,
        val smarts: String,
        val inchi: String,
        val inchiKey: String,
    )

    fun convertFormats(smiles: String): FormatConversions {
        val mol = RDKFuncs.SmilesToMol(smiles)
            ?: throw IllegalArgumentException("Invalid SMILES: $smiles")
        try {
            val inchiExtra = ExtraInchiReturnValues()
            val inchi = RDKFuncs.MolToInchi(mol, inchiExtra)
            val inchiKey = RDKFuncs.InchiToInchiKey(inchi)
            return FormatConversions(
                canonicalSmiles = RDKFuncs.MolToSmiles(mol),
                molBlock = RDKFuncs.MolToMolBlock(mol),
                v3kMolBlock = RDKFuncs.MolToV3KMolBlock(mol),
                smarts = RDKFuncs.MolToSmarts(mol),
                inchi = inchi,
                inchiKey = inchiKey,
            )
        } finally {
            mol.delete()
        }
    }

    // ────────────────────────────────────────────────────────────
    //  6. Murcko scaffold decomposition
    // ────────────────────────────────────────────────────────────

    data class ScaffoldResult(
        val inputSmiles: String,
        val scaffoldSmiles: String,
    )

    fun murckoScaffold(smiles: String): ScaffoldResult {
        val mol = RDKFuncs.SmilesToMol(smiles)
            ?: throw IllegalArgumentException("Invalid SMILES: $smiles")
        try {
            val scaffold = RDKFuncs.MurckoDecompose(mol)
            val scaffoldSmiles = RDKFuncs.MolToSmiles(scaffold)
            scaffold.delete()
            return ScaffoldResult(
                inputSmiles = RDKFuncs.MolToSmiles(mol),
                scaffoldSmiles = scaffoldSmiles,
            )
        } finally {
            mol.delete()
        }
    }

    // ────────────────────────────────────────────────────────────
    //  7. 3D coordinate generation (embedding)
    // ────────────────────────────────────────────────────────────

    data class EmbeddingResult(
        val smiles: String,
        val numConformers: Long,
        val molBlockWith3D: String,
    )

    fun embed3D(smiles: String): EmbeddingResult {
        val mol = RDKFuncs.SmilesToMol(smiles)
            ?: throw IllegalArgumentException("Invalid SMILES: $smiles")
        try {
            // Add hydrogens in-place (important for realistic 3D geometry)
            RDKFuncs.addHs(mol)
            // Embed with ETKDG (state-of-the-art distance geometry)
            val params = RDKFuncs.getETKDGv3()
            val confId = DistanceGeom.EmbedMolecule(mol, params)
            if (confId < 0) {
                throw RuntimeException("3D embedding failed for: $smiles")
            }
            val molBlockWith3D = RDKFuncs.MolToMolBlock(mol)
            // Remove Hs in-place for a cleaner output
            RDKFuncs.removeHs(mol)
            val numConf = mol.getNumConformers()
            return EmbeddingResult(
                smiles = RDKFuncs.MolToSmiles(mol),
                numConformers = numConf,
                molBlockWith3D = molBlockWith3D,
            )
        } finally {
            mol.delete()
        }
    }

    // ────────────────────────────────────────────────────────────
    //  8. Ring information
    // ────────────────────────────────────────────────────────────

    data class RingSystemInfo(
        val smiles: String,
        val numRings: Long,
        val numAromaticRings: Long,
        val numAliphaticRings: Long,
        val numSaturatedRings: Long,
        val numHeterocycles: Long,
        val numAromaticHeterocycles: Long,
        val numAromaticCarbocycles: Long,
    )

    fun analyzeRings(smiles: String): RingSystemInfo {
        val mol = RDKFuncs.SmilesToMol(smiles)
            ?: throw IllegalArgumentException("Invalid SMILES: $smiles")
        try {
            return RingSystemInfo(
                smiles = RDKFuncs.MolToSmiles(mol),
                numRings = RDKFuncs.calcNumRings(mol),
                numAromaticRings = RDKFuncs.calcNumAromaticRings(mol),
                numAliphaticRings = RDKFuncs.calcNumAliphaticRings(mol),
                numSaturatedRings = RDKFuncs.calcNumSaturatedRings(mol),
                numHeterocycles = RDKFuncs.calcNumHeterocycles(mol),
                numAromaticHeterocycles = RDKFuncs.calcNumAromaticHeterocycles(mol),
                numAromaticCarbocycles = RDKFuncs.calcNumAromaticCarbocycles(mol),
            )
        } finally {
            mol.delete()
        }
    }

    // ────────────────────────────────────────────────────────────
    //  9. Atom-level inspection
    // ────────────────────────────────────────────────────────────

    data class AtomInfo(
        val index: Long,
        val symbol: String,
        val atomicNum: Int,
        val mass: Double,
        val formalCharge: Int,
        val isAromatic: Boolean,
        val degree: Long,
        val totalNumHs: Long,
        val hybridization: String,
    )

    fun inspectAtoms(smiles: String): List<AtomInfo> {
        val mol = RDKFuncs.SmilesToMol(smiles)
            ?: throw IllegalArgumentException("Invalid SMILES: $smiles")
        try {
            val atoms = mutableListOf<AtomInfo>()
            for (i in 0 until mol.getNumAtoms()) {
                val atom = mol.getAtomWithIdx(i)
                atoms.add(
                    AtomInfo(
                        index = atom.getIdx(),
                        symbol = atom.getSymbol(),
                        atomicNum = atom.getAtomicNum(),
                        mass = atom.getMass(),
                        formalCharge = atom.getFormalCharge(),
                        isAromatic = atom.getIsAromatic(),
                        degree = atom.getDegree(),
                        totalNumHs = atom.getTotalNumHs(),
                        hybridization = atom.getHybridization().toString(),
                    )
                )
            }
            return atoms
        } finally {
            mol.delete()
        }
    }

    // ────────────────────────────────────────────────────────────
    //  10. Lipinski's Rule of Five check
    // ────────────────────────────────────────────────────────────

    data class LipinskiResult(
        val smiles: String,
        val molecularWeight: Double,
        val logP: Double,
        val numHBondDonors: Long,
        val numHBondAcceptors: Long,
        val passesRuleOfFive: Boolean,
        val violations: List<String>,
    )

    fun checkLipinski(smiles: String): LipinskiResult {
        val mol = RDKFuncs.SmilesToMol(smiles)
            ?: throw IllegalArgumentException("Invalid SMILES: $smiles")
        try {
            val mw = RDKFuncs.calcAMW(mol)
            val crippen = RDKFuncs.calcCrippenDescriptors(mol)
            val logP = crippen.getFirst()
            val hbd = RDKFuncs.calcNumHBD(mol)
            val hba = RDKFuncs.calcNumHBA(mol)

            val violations = mutableListOf<String>()
            if (mw > 500.0) violations.add("MW > 500 (${String.format("%.2f", mw)})")
            if (logP > 5.0) violations.add("LogP > 5 (${String.format("%.2f", logP)})")
            if (hbd > 5) violations.add("HBD > 5 ($hbd)")
            if (hba > 10) violations.add("HBA > 10 ($hba)")

            return LipinskiResult(
                smiles = RDKFuncs.MolToSmiles(mol),
                molecularWeight = mw,
                logP = logP,
                numHBondDonors = hbd,
                numHBondAcceptors = hba,
                passesRuleOfFive = violations.isEmpty(),
                violations = violations,
            )
        } finally {
            mol.delete()
        }
    }
}





