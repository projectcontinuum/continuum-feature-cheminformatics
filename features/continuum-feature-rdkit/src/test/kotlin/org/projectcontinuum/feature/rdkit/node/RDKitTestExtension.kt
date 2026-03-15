package org.projectcontinuum.feature.rdkit.node

import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * JUnit5 extension that loads the RDKit native library before tests run.
 * Replicates the logic from RDKitConfig but works outside Spring context.
 *
 * Usage: Add `@ExtendWith(RDKitTestExtension::class)` to test classes that call RDKit.
 */
class RDKitTestExtension : BeforeAllCallback {

    companion object {
        @Volatile
        private var loaded = false
        private val lock = Any()

        private fun resolvePlatformLib(): Pair<String, String> {
            val os = System.getProperty("os.name").lowercase()
            val arch = System.getProperty("os.arch").lowercase()
            val libBaseName = "libGraphMolWrap"
            return when {
                os.contains("linux") && arch.contains("amd64") -> "linux-x86_64" to "$libBaseName.so"
                os.contains("linux") && arch.contains("aarch64") -> "linux-aarch64" to "$libBaseName.so"
                os.contains("mac") && arch.contains("aarch64") -> "macos-aarch64" to "$libBaseName.jnilib"
                os.contains("mac") -> "macos-x86_64" to "$libBaseName.jnilib"
                os.contains("win") && arch.contains("amd64") -> "win32-x86_64" to "GraphMolWrap.dll"
                else -> throw UnsupportedOperationException("No bundled RDKit native library for os=$os arch=$arch")
            }
        }
    }

    override fun beforeAll(context: ExtensionContext?) {
        if (loaded) return
        synchronized(lock) {
            if (loaded) return
            val (platformDir, libFileName) = resolvePlatformLib()
            val resourcePath = "native/$platformDir/$libFileName"
            val inputStream = javaClass.classLoader.getResourceAsStream(resourcePath)
                ?: throw IllegalStateException("RDKit native library not found at '$resourcePath'")
            val tempDir = Files.createTempDirectory("rdkit-test-native")
            val tempLib = tempDir.resolve(libFileName)
            inputStream.use { src ->
                Files.copy(src, tempLib, StandardCopyOption.REPLACE_EXISTING)
            }
            tempLib.toFile().deleteOnExit()
            tempDir.toFile().deleteOnExit()
            System.load(tempLib.toAbsolutePath().toString())
            loaded = true
        }
    }
}
