package org.mingora.launcher.hyp.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import org.mingora.launcher.hyp.models.common.SimpleIconEntry
import org.mingora.launcher.hyp.models.common.SimpleImageEntry

@Serializable
data class GameInfoWrapper (
    val games: List<GameInfo>
)

@Serializable
data class GameInfo (
    val id: String,
    val biz: String,
    val display: GameInfoDisplay,
    val reservation: JsonElement? = null,

    @SerialName("display_status")
    val displayStatus: String,
) {
    fun isEqual(other: GameInfo): Boolean {
        return this.display == other.display
    }
}

@Serializable
data class GameInfoDisplay (
    val language: String,
    val name: String,
    val icon: SimpleIconEntry,
    val title: String,
    val subtitle: String,
    val background: SimpleImageEntry,
    val logo: SimpleImageEntry,
    val thumbnail: SimpleImageEntry,
    val shortcut: SimpleIconEntry,

    @SerialName("wpf_icon")
    val wpfIcon: SimpleIconEntry,

    @SerialName("top_left_logo")
    val topLeftLogo: SimpleImageEntry? = null,

    val introduction: String
)
