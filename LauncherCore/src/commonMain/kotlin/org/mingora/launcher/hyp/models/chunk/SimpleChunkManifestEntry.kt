package org.mingora.launcher.hyp.models.chunk

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SimpleChunkManifestEntry(
    val id: String,
    val checksum: String,

    @SerialName("compressed_size")
    val compressedSize: String,

    @SerialName("uncompressed_size")
    val uncompressedSize: String
)
