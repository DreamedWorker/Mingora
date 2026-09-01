package org.mingora.launcher.hyp.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.mingora.launcher.hyp.models.chunk.SimpleChunkDownloadEntry
import org.mingora.launcher.hyp.models.chunk.SimpleChunkManifestEntry
import org.mingora.launcher.hyp.models.chunk.SimpleChunkStatEntry

@Serializable
data class GameSophonChunkPatch(
    @SerialName("build_id")
    val buildID: String,

    @SerialName("patch_id")
    val patchID: String,

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

        @SerialName("diff_download")
        val diffDownload: SimpleChunkDownloadEntry,

        @SerialName("manifest_download")
        val manifestDownload: SimpleChunkDownloadEntry,

        @SerialName("matching_field")
        val matchingField: String,

        val stats: Map<String, SimpleChunkStatEntry>
    )
}
