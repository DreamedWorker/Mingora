package org.mingora.launcher.wine

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.path
import io.github.vinceglb.filekit.readString
import io.github.vinceglb.filekit.resolve
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException
import org.mingora.launcher.core.util.FileHelper
import kotlin.io.encoding.Base64

internal class WineInfEditor {
    suspend fun addCertsToWine(wineDirectory: PlatformFile): Result<Unit> {
        return try {
            val wineInfFile = wineDirectory.resolve(WINE_INF_RELATIVE_PATH)
            check(wineInfFile.exists()) {
                "Could not find wine inf file: ${wineInfFile.path}"
            }

            val certBlock = Base64.decode(WineCertResource.BASE64).decodeToString()
                .normalizeLineEndings()
                .trim()
            check(certBlock.isNotEmpty()) {
                "Decoded Wine certificate block is empty."
            }

            val lines = wineInfFile.readString()
                .normalizeLineEndings()
                .split("\n")
                .toMutableList()
            val marker = "; URL Associations"
            val found = lines.indexOfFirst { it.trim() == marker }
            check(found >= 0) {
                "Could not find URL Associations section in wine.inf."
            }

            val nextBlockLoc = (found + 1 until lines.size)
                .firstOrNull { lines[it].trim().isEmpty() }
                ?: throw IOException(
                    "Could not find the end of URL Associations section in wine.inf."
                )

            val certLines = certBlock.split("\n")
            val sectionLines = lines.subList(found + 1, nextBlockLoc)
            if (sectionLines.containsConsecutiveLines(certLines)) {
                return Result.success(Unit)
            }

            lines.addAll(nextBlockLoc, listOf("") + certLines)
            FileHelper.insertContentWithPosition(wineInfFile, lines.joinToString("\n"))
            Result.success(Unit)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    private fun String.normalizeLineEndings(): String =
        replace("\r\n", "\n").replace("\r", "\n")

    private fun List<String>.containsConsecutiveLines(target: List<String>): Boolean {
        return !(target.isEmpty() || target.size > size) && windowed(target.size).any { it == target }
    }

    private companion object {
        const val WINE_INF_RELATIVE_PATH = "share/wine/wine.inf"
    }
}
