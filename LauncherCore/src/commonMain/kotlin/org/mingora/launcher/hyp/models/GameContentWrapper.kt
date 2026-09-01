package org.mingora.launcher.hyp.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.mingora.launcher.hyp.models.common.SimpleGameEntry
import org.mingora.launcher.hyp.models.common.SimpleIconEntry
import org.mingora.launcher.hyp.models.common.SimpleImageEntry

@Serializable
internal data class GameContentWrapper (
    val content: GameContent
)

@Serializable
data class GameContent (
    val game: SimpleGameEntry,
    val language: String,
    val banners: List<Banner>,
    val posts: List<Post>,

    @SerialName("social_media_list")
    val socialMediaList: List<SocialMediaList>
) {
    @Serializable
    data class Banner (
        val id: String,
        val image: SimpleImageEntry,

        @SerialName("i18n_identifier")
        val i18NIdentifier: String
    )

    @Serializable
    data class Post (
        val id: String,
        val type: String,
        val title: String,
        val link: String,
        val date: String,

        @SerialName("login_state_in_link")
        val loginStateInLink: Boolean,

        @SerialName("i18n_identifier")
        val i18NIdentifier: String
    )

    @Serializable
    data class SocialMediaList (
        val id: String,
        val icon: SimpleIconEntry,

        @SerialName("qr_image")
        val qrImage: SimpleImageEntry,

        @SerialName("qr_desc")
        val qrDesc: String,

        val links: List<Link>,

        @SerialName("enable_red_dot")
        val enableRedDot: Boolean,

        @SerialName("red_dot_content")
        val redDotContent: String
    ) {
        @Serializable
        data class Link (
            val title: String,
            val link: String,

            @SerialName("login_state_in_link")
            val loginStateInLink: Boolean
        )
    }
}
