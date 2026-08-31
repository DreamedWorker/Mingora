package org.mingora.launcher.hyp.models.common

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SimpleImageEntry(
    val url: String,
    val link: String,

    @SerialName("login_state_in_link")
    val loginStateInLink: Boolean
)
