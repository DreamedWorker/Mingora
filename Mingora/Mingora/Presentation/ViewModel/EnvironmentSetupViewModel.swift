//
//  EnvironmentSetupViewModel.swift
//  Mingora
//
//  Created by 鸳汐 on 2026/8/30.
//

import Foundation
import LauncherCore

@Observable
class EnvironmentSetupViewModel {
    var useMirror = false
    var selectedServer: HYPLauncherId = .chinaOfficial
    private(set) var isInstalling = false
    private(set) var isCompleted = false
    private(set) var hasStarted = false
    private(set) var statusMessage = "等待开始"
    private(set) var currentResource = ""
    private(set) var downloadedBytes: Int64 = 0
    private(set) var totalBytes: Int64?
    private(set) var errorMessage: String?
    
    var progress: Double? {
        guard let totalBytes, totalBytes > 0 else {
            return nil
        }
        return min(max(Double(downloadedBytes) / Double(totalBytes), 0), 1)
    }
    
    @MainActor func startProcess(setGames: ([GameInfo]) -> Void) async {
        guard !isInstalling, !isCompleted else {
            return
        }
        
        isInstalling = true
        hasStarted = true
        isCompleted = false
        statusMessage = "正在准备环境…"
        currentResource = ""
        downloadedBytes = 0
        totalBytes = nil
        errorMessage = nil
        
        let useMirror = useMirror
        let useLauncher = selectedServer
        
        do {
            try await WineService.shared.installEnv(
                useMirror: useMirror,
                onProgress: { [weak self] resource, downloaded, total in
                    Task { @MainActor [weak self] in
                        guard let self, self.isInstalling else {
                            return
                        }
                        self.currentResource = resource
                        self.statusMessage = "正在下载"
                        self.downloadedBytes = downloaded.int64Value
                        self.totalBytes = total?.int64Value
                    }
                },
                onPostConfiguration: { prefixDirPath in
                    do {
                        guard let resourceDirectory = RuntimeResourceInstaller.resourceDirectory else {
                            return "找不到 App 资源目录。"
                        }
                        try RuntimeResourceInstaller.copyWineRuntimeFiles(
                            from: resourceDirectory,
                            to: URL(fileURLWithPath: prefixDirPath)
                        )
                        return nil
                    } catch {
                        return error.localizedDescription
                    }
                },
            )
            
            let games = try? await HomeService.shared.fetchGameInfoAfterConfigure(launcher: useLauncher)
            setGames(games ?? [])
            
            self.isInstalling = false
            self.statusMessage = "正在完成最后配置…"
            self.downloadedBytes = self.totalBytes ?? self.downloadedBytes
            self.isCompleted = true
            self.errorMessage = nil
        } catch {
            Task { @MainActor [weak self] in
                guard let self else {
                    return
                }
                self.isInstalling = false
                self.statusMessage = "环境配置失败"
                self.errorMessage = error.localizedDescription
                
            }
        }
    }
}
