package org.mingora.launcher.hyp.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class HYPApiWrapper<T>(
    @SerialName("retcode") val code: Int,
    @SerialName("message") val message: String,
    @SerialName("data") val data: T,
)
