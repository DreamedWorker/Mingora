package org.mingora.launcher.hyp.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.mingora.launcher.hyp.models.common.SimpleGameEntry
import org.mingora.launcher.hyp.models.common.SimpleIconEntry
import org.mingora.launcher.hyp.models.common.SimpleImageEntry

@Serializable
internal data class GameBackgroundInfoWrapper (
    @SerialName("game_info_list")
    val gameInfoList: List<GameBackground>
)

@Serializable
data class GameBackground (
    val game: SimpleGameEntry,
    val backgrounds: List<BackgroundElement>
) {
    @Serializable
    data class BackgroundElement (
        val id: String,
        val background: SimpleImageEntry,
        val icon: SimpleIconEntry,
        val video: GameVideoBg,
        val theme: SimpleImageEntry,
        val type: String
    )
}

@Serializable
data class GameVideoBg (
    val url: String,
    val size: Long
)