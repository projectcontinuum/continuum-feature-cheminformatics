package org.projectcontinuum.feature.rdkit.util

import org.RDKit.ExtraInchiReturnValues
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
     * Parse an InChI string into an ROMol, returning null if invalid.
     * Caller MUST call mol.delete() in a finally block if non-null.
     */
    fun parseInchiOrNull(inchi: String, sanitize: Boolean = true, removeHydrogens: Boolean = true): ROMol? =
        RDKFuncs.InchiToMol(inchi, ExtraInchiReturnValues(), sanitize, removeHydrogens)

    /**
     * Parse a MolBlock string into an ROMol, returning null if invalid.
     * Caller MUST call mol.delete() in a finally block if non-null.
     */
    fun parseMolBlockOrNull(molBlock: String): ROMol? =
        RDKFuncs.MolBlockToMol(molBlock)

    /**
     * Parse a molecule from SMILES or MolBlock format.
     * Tries to detect MolBlock format by looking for "V2000", "V3000", or "M  END".
     * Returns null if invalid.
     * Caller MUST call mol.delete() in a finally block if non-null.
     */
    fun parseMoleculeOrMolBlockOrNull(input: String): ROMol? {
        return if (input.contains("V2000") || input.contains("V3000") || input.contains("M  END")) {
            parseMolBlockOrNull(input)
        } else {
            parseMoleculeOrNull(input)
        }
    }

    // ...existing code...

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

    /**
     * Execute a block with a parsed InChI, automatically cleaning up native memory.
     * Returns null if the InChI is invalid.
     */
    inline fun <T> withInchi(inchi: String, sanitize: Boolean = true, removeHydrogens: Boolean = true, block: (ROMol) -> T): T? {
        val mol = parseInchiOrNull(inchi, sanitize, removeHydrogens) ?: return null
        try {
            return block(mol)
        } finally {
            mol.delete()
        }
    }

    /**
     * Execute a block with a parsed molecule from SMILES or MolBlock, automatically cleaning up native memory.
     * Returns null if the input is invalid.
     */
    inline fun <T> withMoleculeOrMolBlock(input: String, block: (ROMol) -> T): T? {
        val mol = parseMoleculeOrMolBlockOrNull(input) ?: return null
        try {
            return block(mol)
        } finally {
            mol.delete()
        }
    }

    /**
     * Execute a block with two parsed molecules, automatically cleaning up native memory.
     * Returns null if either SMILES is invalid.
     */
    inline fun <T> withTwoMolecules(smiles1: String, smiles2: String, block: (ROMol, ROMol) -> T): T? {
        val mol1 = parseMoleculeOrNull(smiles1) ?: return null
        try {
            val mol2 = parseMoleculeOrNull(smiles2) ?: return null
            try {
                return block(mol1, mol2)
            } finally {
                mol2.delete()
            }
        } finally {
            mol1.delete()
        }
    }

    /**
     * Execute a block with a parsed query (SMARTS or SMILES), automatically cleaning up native memory.
     * Tries SMARTS first, falls back to SMILES. Returns null if invalid.
     */
    inline fun <T> withQuery(query: String, block: (ROMol) -> T): T? {
        val mol = parseSmartsOrNull(query) ?: parseMoleculeOrNull(query) ?: return null
        try {
            return block(mol)
        } finally {
            mol.delete()
        }
    }

    /**
     * Manages a list of ROMol objects, ensuring cleanup on scope exit.
     * Usage:
     * ```
     * withMoleculeList { list ->
     *     list.add(parseMoleculeOrNull("CCO")!!)
     *     // ... use list
     * } // all molecules deleted automatically
     * ```
     */
    inline fun <T> withMoleculeList(block: (MutableList<ROMol>) -> T): T {
        val list = mutableListOf<ROMol>()
        try {
            return block(list)
        } finally {
            for (mol in list) {
                mol.delete()
            }
        }
    }
}
