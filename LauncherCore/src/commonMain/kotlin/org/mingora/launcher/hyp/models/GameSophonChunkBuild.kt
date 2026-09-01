package org.mingora.launcher.hyp.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.mingora.launcher.hyp.models.chunk.SimpleChunkDownloadEntry
import org.mingora.launcher.hyp.models.chunk.SimpleChunkManifestEntry
import org.mingora.launcher.hyp.models.chunk.SimpleChunkStatEntry

@Serializable
data class GameSophonChunkBuild(
    @SerialName("build_id")
    val buildID: String,

    val tag: String,
    val manifests: List<ManifestElement>
) {
    @Serializable
    data class ManifestElement (
        @SerialName("category_id")
        val categoryID: String,

        @SerialName("category_name")
        val categoryName: String,

        val manifest: SimpleChunkManifestEntry,

        @SerialName("chunk_download")
        val chunkDownload: SimpleChunkDownloadEntry,

        @SerialName("manifest_download")
        val manifestDownload: SimpleChunkDownloadEntry,

        @SerialName("matching_field")
        val matchingField: String,

        val stats: SimpleChunkStatEntry,

        @SerialName("deduplicated_stats")
        val deduplicatedStats: SimpleChunkStatEntry
    )
}
