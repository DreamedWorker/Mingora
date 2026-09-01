package org.mingora.launcher.hyp.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.mingora.launcher.hyp.models.common.SimpleGameEntry

@Serializable
internal data class GameConfigsWrapper (
    @SerialName("launch_configs")
    val launchConfigs: List<GameConfig>
)

@Serializable
data class GameConfig (
    val game: SimpleGameEntry,

    @SerialName("exe_file_name")
    val exeFileName: String,

    @SerialName("installation_dir")
    val installationDir: String,

    @SerialName("audio_pkg_scan_dir")
    val audioPkgScanDir: String,

    @SerialName("audio_pkg_res_dir")
    val audioPkgResDir: String,

    @SerialName("audio_pkg_cache_dir")
    val audioPkgCacheDir: String,

    @SerialName("game_cached_res_dir")
    val gameCachedResDir: String,

    @SerialName("game_screenshot_dir")
    val gameScreenshotDir: String,

    @SerialName("game_log_gen_dir")
    val gameLogGenDir: String,

    @SerialName("game_crash_file_gen_dir")
    val gameCrashFileGenDir: String,

    @SerialName("default_download_mode")
    val defaultDownloadMode: String,

    @SerialName("enable_customer_service")
    val enableCustomerService: Boolean,

    @SerialName("local_res_dir")
    val localResDir: String,

    @SerialName("local_res_cache_dir")
    val localResCacheDir: String,

    @SerialName("res_category_dir")
    val resCategoryDir: String,

    @SerialName("game_res_cut_dir")
    val gameResCutDir: String,

    @SerialName("enable_game_log_export")
    val enableGameLogExport: Boolean,

    @SerialName("game_log_export_config")
    val gameLogExportConfig: GameLogExportConfig? = null,

    @SerialName("blacklist_dir")
    val blacklistDir: String,

    @SerialName("wpf_exe_dir")
    val wpfExeDir: String,

    @SerialName("wpf_pkg_version_dir")
    val wpfPkgVersionDir: String,

    @SerialName("enable_audio_pkg_mgmt")
    val enableAudioPkgMgmt: Boolean,

    @SerialName("audio_pkg_config_dir")
    val audioPkgConfigDir: String,

    @SerialName("enable_resource_deletion_adapter")
    val enableResourceDeletionAdapter: Boolean,

    @SerialName("enable_resource_blacklist")
    val enableResourceBlacklist: Boolean,

    @SerialName("enable_redundant_file_cleanup")
    val enableRedundantFileCleanup: Boolean,

    @SerialName("redundant_file_cleanup_paths")
    val redundantFileCleanupPaths: List<String>,

    @SerialName("enable_v2_game_detection")
    val enableV2GameDetection: Boolean,

    @SerialName("related_processes")
    val relatedProcesses: List<String>,

    @SerialName("enable_ldiff")
    val enableLdiff: Boolean,

    @SerialName("scenario_pkg_info")
    val scenarioPkgInfo: ScenarioPkgInfo? = null,

    @SerialName("local_scenario_config_path")
    val localScenarioConfigPath: String,

    @SerialName("enable_full_pkg_recommend")
    val enableFullPkgRecommend: Boolean,

    @SerialName("enable_scenario_pkg")
    val enableScenarioPkg: Boolean,

    @SerialName("enable_dx_switch")
    val enableDxSwitch: Boolean,

    @SerialName("enable_write_verify_result")
    val enableWriteVerifyResult: Boolean,

    @SerialName("write_verify_result_path")
    val writeVerifyResultPath: String,

    @SerialName("enable_driver_upgrade_alert")
    val enableDriverUpgradeAlert: Boolean,

    @SerialName("disable_reservation_auto_download")
    val disableReservationAutoDownload: Boolean
) {
    @Serializable
    data class GameLogExportConfig (
        @SerialName("file_size_filter")
        val fileSizeFilter: String,

        @SerialName("export_timeout")
        val exportTimeout: String,

        @SerialName("export_files")
        val exportFiles: List<ExportFile>
    ) {
        @Serializable
        data class ExportFile (
            @SerialName("file_type")
            val fileType: String,

            val method: String,
            val path: String
        )
    }

    @Serializable
    data class ScenarioPkgInfo (
        @SerialName("full_pkg_name")
        val fullPkgName: String,

        @SerialName("full_pkg_desc")
        val fullPkgDesc: String,

        @SerialName("base_pkg_name")
        val basePkgName: String,

        @SerialName("base_pkg_desc")
        val basePkgDesc: String
    )
}
