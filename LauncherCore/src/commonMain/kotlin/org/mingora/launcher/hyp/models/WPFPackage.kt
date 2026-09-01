package org.mingora.launcher.hyp.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.mingora.launcher.hyp.models.common.SimpleGameEntry

@Serializable
data class WPFPackage(
    @SerialName("wpf_packages")
    val wpfPackages: List<WPFPackageElement>
) {
    @Serializable
    data class WPFPackageElement (
        val game: SimpleGameEntry,

        @SerialName("wpf_package")
        val wpfPackage: WPFPackageWPFPackage
    ) {
        @Serializable
        data class WPFPackageWPFPackage (
            val version: String,
            val url: String,
            val md5: String,
            val size: String
        )
    }
}
