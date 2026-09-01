package org.mingora.launcher.hyp.models.chunk

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SimpleChunkStatEntry(
    @SerialName("compressed_size")
    val compressedSize: String,

    @SerialName("uncompressed_size")
    val uncompressedSize: String,

    @SerialName("file_count")
    val fileCount: String,

    @SerialName("chunk_count")
    val chunkCount: String
)
