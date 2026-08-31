package org.mingora.launcher.hyp.models.common

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SimpleIconEntry(
    val url: String,

    @SerialName("hover_url")
    val hoverURL: String,

    val link: String,

    @SerialName("login_state_in_link")
    val loginStateInLink: Boolean,

    val md5: String,
    val size: Long
)
