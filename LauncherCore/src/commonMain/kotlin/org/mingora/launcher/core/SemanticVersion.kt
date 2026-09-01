package org.mingora.launcher.core

data class SemanticVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
) {
    override fun toString(): String {
        return "$major.$minor.$patch"
    }

    companion object {
        fun parse(version: String): SemanticVersion {
            val list = version.split(".")
            val major = list[0].toInt()
            val minor = list[1].toInt()
            val patch = list[2].toInt()
            return SemanticVersion(major, minor, patch)
        }
    }
}
