package org.mingora.launcher.hyp.models.chunk

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SimpleChunkDownloadEntry(
    val encryption: Long,
    val password: String,
    val compression: Long,

    @SerialName("url_prefix")
    val urlPrefix: String,

    @SerialName("url_suffix")
    val urlSuffix: String
)
