package org.mingora.launcher.app

/** 供 UI 读取的安装任务快照。 */
data class GameInstallStatus(
    /** notInstalled / preparing / downloading / paused / completed / failed / terminated */
    val state: String,
    val downloadedBytes: Long,
    val totalBytes: Long,
) {
    val progress: Double
        get() = if (totalBytes > 0L) {
            (downloadedBytes.toDouble() / totalBytes.toDouble()).coerceIn(0.0, 1.0)
        } else {
            0.0
        }
}
