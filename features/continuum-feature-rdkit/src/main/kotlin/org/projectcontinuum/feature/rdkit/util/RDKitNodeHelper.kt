package org.projectcontinuum.feature.rdkit.util

import org.RDKit.RDKFuncs
import org.RDKit.ROMol

/**
 * Shared utility functions for RDKit Continuum nodes.
 * Provides safe molecule parsing with proper native memory management.
 */
object RDKitNodeHelper {

    /**
     * Parse a SMILES string into an ROMol. Throws if invalid.
     * Caller MUST call mol.delete() in a finally block.
     */
    fun parseMolecule(smiles: String): ROMol =
        RDKFuncs.SmilesToMol(smiles)
            ?: throw IllegalArgumentException("Invalid SMILES: $smiles")

    /**
     * Parse a SMILES string into an ROMol, returning null if invalid.
     * Caller MUST call mol.delete() in a finally block if non-null.
     */
    fun parseMoleculeOrNull(smiles: String): ROMol? =
        RDKFuncs.SmilesToMol(smiles)

    /**
     * Parse a SMARTS string into a query ROMol, returning null if invalid.
     * Caller MUST call mol.delete() in a finally block if non-null.
     */
    fun parseSmartsOrNull(smarts: String): ROMol? =
        RDKFuncs.SmartsToMol(smarts)

    /**
     * Get the canonical SMILES representation of a molecule.
     */
    fun toCanonicalSmiles(mol: ROMol): String =
        RDKFuncs.MolToSmiles(mol)

    /**
     * Execute a block with a parsed molecule, automatically cleaning up native memory.
     * Returns null if the SMILES is invalid.
     */
    inline fun <T> withMolecule(smiles: String, block: (ROMol) -> T): T? {
        val mol = parseMoleculeOrNull(smiles) ?: return null
        try {
            return block(mol)
        } finally {
            mol.delete()
        }
    }

    /**
     * Execute a block with a parsed SMARTS query, automatically cleaning up native memory.
     * Returns null if the SMARTS is invalid.
     */
    inline fun <T> withSmarts(smarts: String, block: (ROMol) -> T): T? {
        val mol = parseSmartsOrNull(smarts) ?: return null
        try {
            return block(mol)
        } finally {
            mol.delete()
        }
    }
}
