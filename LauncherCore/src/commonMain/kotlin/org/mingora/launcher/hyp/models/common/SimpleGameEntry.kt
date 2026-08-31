package org.mingora.launcher.hyp.models.common

import kotlinx.serialization.Serializable

@Serializable
data class SimpleGameEntry(
    val id: String,
    val biz: String
)
