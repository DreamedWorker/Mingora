package org.mingora.launcher.hyp.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.mingora.launcher.hyp.models.common.SimpleGameEntry

@Serializable
internal data class GameChannelSDKWrapper (
    @SerialName("game_channel_sdks")
    val gameChannelSdks: List<GameChannelSDK>
)

@Serializable
data class GameChannelSDK (
    val game: SimpleGameEntry,
    val version: String,

    @SerialName("channel_sdk_pkg")
    val channelSDKPkg: ChannelSDKPkg,

    @SerialName("pkg_version_file_name")
    val pkgVersionFileName: String
) {
    @Serializable
    data class ChannelSDKPkg (
        val url: String,
        val md5: String,
        val size: String,

        @SerialName("decompressed_size")
        val decompressedSize: String
    )
}
