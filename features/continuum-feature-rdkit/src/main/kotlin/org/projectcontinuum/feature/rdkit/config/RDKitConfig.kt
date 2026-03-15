package org.projectcontinuum.feature.rdkit.config

import org.RDKit.RDKFuncs
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Configuration
import jakarta.annotation.PostConstruct
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Loads the RDKit native library bundled inside the application JAR.
 *
 * The native library is stored on the classpath at:
 *   native/<os>-<arch>/<libfile>
 *
 * Supported platforms and file names:
 *   - Linux   x86_64  → native/linux-x86_64/libGraphMolWrap.so
 *   - Linux   aarch64 → native/linux-aarch64/libGraphMolWrap.so
 *   - macOS   aarch64 → native/macos-aarch64/libGraphMolWrap.jnilib
 *   - macOS   x86_64  → native/macos-x86_64/libGraphMolWrap.jnilib
 *   - Windows x86_64  → native/win32-x86_64/GraphMolWrap.dll
 *
 * At startup this config extracts the library to a temporary directory and calls
 * [System.load] with the absolute path — no -Djava.library.path required.
 */
@Configuration
class RDKitConfig {

    private val log = LoggerFactory.getLogger(RDKitConfig::class.java)

    companion object {
        private const val LIB_BASE_NAME = "libGraphMolWrap"

        /**
         * Resolve the platform directory and native library filename.
         * Returns a pair of (platform-dir, filename).
         */
        private fun resolvePlatformLib(): Pair<String, String> {
            val os = System.getProperty("os.name").lowercase()
            val arch = System.getProperty("os.arch").lowercase()
            return when {
                os.contains("linux") && arch.contains("amd64")   -> "linux-x86_64"  to "$LIB_BASE_NAME.so"
                os.contains("linux") && arch.contains("aarch64") -> "linux-aarch64"  to "$LIB_BASE_NAME.so"
                os.contains("mac")   && arch.contains("aarch64") -> "macos-aarch64"  to "$LIB_BASE_NAME.jnilib"
                os.contains("mac")                                -> "macos-x86_64"  to "$LIB_BASE_NAME.jnilib"
                os.contains("win")   && arch.contains("amd64")   -> "win32-x86_64"  to "GraphMolWrap.dll"
                else -> throw UnsupportedOperationException(
                    "No bundled RDKit native library for os=$os arch=$arch"
                )
            }
        }
    }

    @PostConstruct
    fun loadNativeLibrary() {
        val (platformDir, libFileName) = resolvePlatformLib()
        val resourcePath = "native/$platformDir/$libFileName"
        log.info("Loading RDKit native library from classpath: {}", resourcePath)

        val inputStream = javaClass.classLoader.getResourceAsStream(resourcePath)
            ?: throw IllegalStateException(
                "RDKit native library not found on classpath at '$resourcePath'. " +
                "Ensure the library is placed in src/main/resources/$resourcePath"
            )

        // Extract to a temp file so System.load() can map it
        val tempDir = Files.createTempDirectory("rdkit-native")
        val tempLib = tempDir.resolve(libFileName)
        inputStream.use { src ->
            Files.copy(src, tempLib, StandardCopyOption.REPLACE_EXISTING)
        }
        // Clean up on JVM exit
        tempLib.toFile().deleteOnExit()
        tempDir.toFile().deleteOnExit()

        System.load(tempLib.toAbsolutePath().toString())

        log.info("✅ RDKit native library loaded from {}", tempLib)
        log.info("   RDK Fingerprint version : {}", RDKFuncs.getRDKFingerprintMolVersion())
        log.info("   Morgan FP version       : {}", RDKFuncs.getMorganFingerprintVersion())
    }
}

