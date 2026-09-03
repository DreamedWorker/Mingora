package org.mingora.launcher.gameinstall.task

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.resolve
import org.mingora.launcher.hyp.proto.SophonFileChunkMode
import org.mingora.launcher.hyp.proto.SophonFilePatchMode

internal data class TaskFile(
    val nameWithRelativePath: String,
    val size: Long,
    val md5: String,
    val fullPath: PlatformFile,
    val chunks: MutableList<GameInstallChunk> = mutableListOf(),
    var patch: GameInstallPatch? = null,
    var isFinished: Boolean = false,
) {
    data class GameInstallChunk(
        val id: String,
        val offset: Long,
        val compressedSize: Long,
        val uncompressedSize: Long,
        val compressedMd5: String,
        val uncompressedMd5: String,
        val url: String,
    )

    data class GameInstallPatch(
        val id: String,
        val tag: String,
        val patchFileSize: Long,
        val patchFileMd5: String,
        val patchOffset: Long,
        val patchLength: Long,
        val originalFileName: String,
        val originalFileSize: Long,
        val originalFileMd5: String,
        val url: String,
        var compression: Boolean = false,
    )

    companion object {
        fun fromSophonChunkFile(
            item: SophonFileChunkMode,
            installPath: PlatformFile,
            urlPrefix: String
        ): TaskFile {
            val chunks = item.chunks.map { chunk ->
                GameInstallChunk(
                    id = chunk.id,
                    offset = chunk.offset,
                    compressedSize = chunk.compressedSize,
                    uncompressedSize = chunk.uncompressedSize,
                    compressedMd5 = chunk.compressedMd5,
                    uncompressedMd5 = chunk.uncompressedMd5,
                    url = "$urlPrefix/${chunk.id}",
                )
            }.toMutableList()
            return TaskFile(
                nameWithRelativePath = item.file,
                size = item.size,
                md5 = item.md5,
                fullPath = installPath.resolve(item.file),
                chunks = chunks,
            )
        }

        fun fromSophonPatchFile(
            item: SophonFilePatchMode,
            installPath: PlatformFile,
            localVersion: String,
            urlPrefix: String
        ): TaskFile {
            val patch = item.patches.firstOrNull { it.tag == localVersion }?.patch
            return TaskFile(
                nameWithRelativePath = item.file,
                size = item.size,
                md5 = item.md5,
                fullPath = installPath.resolve(item.file),
                patch = patch?.let {
                    GameInstallPatch(
                        id = it.id,
                        tag = it.tag,
                        patchFileSize = it.patchFileSize,
                        patchFileMd5 = it.patchFileMd5,
                        patchOffset = it.patchOffset,
                        patchLength = it.patchLength,
                        originalFileName = it.originalFileName,
                        originalFileSize = it.originalFileSize,
                        originalFileMd5 = it.originalFileMd5,
                        url = "$urlPrefix/${it.id}",
                    )
                },
            )
        }
    }
}
