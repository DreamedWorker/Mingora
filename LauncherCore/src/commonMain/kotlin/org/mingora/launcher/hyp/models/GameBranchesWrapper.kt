package org.mingora.launcher.hyp.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.mingora.launcher.hyp.models.common.SimpleGameEntry

@Serializable
internal data class GameBranchesWrapper (
    @SerialName("game_branches")
    val gameBranches: List<GameBranch>
)

@Serializable
data class GameBranch (
    val game: SimpleGameEntry,
    val main: Main,

    @SerialName("pre_download")
    val preDownload: Main? = null,

    @SerialName("enable_base_pkg_predownload")
    val enableBasePkgPredownload: Boolean
) {
    @Serializable
    data class Main (
        @SerialName("package_id")
        val packageID: String,

        val branch: String,
        val password: String,
        val tag: String,

        @SerialName("diff_tags")
        val diffTags: List<String>,

        val categories: List<Category>,

        @SerialName("required_client_version")
        val requiredClientVersion: String
    ) {
        @Serializable
        data class Category (
            @SerialName("category_id")
            val categoryID: String,

            @SerialName("matching_field")
            val matchingField: String,

            val type: Type,
            val scenarios: List<Scenario>
        ) {
            @Serializable
            enum class Scenario(val value: String) {
                @SerialName("CATEGORY_SCENARIO_BASE") CategoryScenarioBase("CATEGORY_SCENARIO_BASE"),
                @SerialName("CATEGORY_SCENARIO_FULL") CategoryScenarioFull("CATEGORY_SCENARIO_FULL");
            }

            @Serializable
            enum class Type(val value: String) {
                @SerialName("CATEGORY_TYPE_AUDIO") CategoryTypeAudio("CATEGORY_TYPE_AUDIO"),
                @SerialName("CATEGORY_TYPE_RESOURCE") CategoryTypeResource("CATEGORY_TYPE_RESOURCE");
            }
        }
    }
}
