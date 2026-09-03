//
//  AppState.swift
//  Mingora
//
//  Created by 鸳汐 on 2026/8/30.
//

import AppKit
import LauncherCore

@Observable
class AppState: Identifiable {
    var isFirstOpen = false
    var gameInfos: [GameInfo] = []
    var lastOpenedGame: String? = nil
    var mainlyLauncher: HYPLauncherId?
    var gameNews: [GameContent] = []
    var gameInstallStatuses: [String: InstallStatus] = [:]
    private var installPollingTask: Task<Void, Never>?
    
    func setupBasicData() {
        Task { @MainActor in
            isFirstOpen = try! await LauncherCoreKt.isFirstLaunch().boolValue
            if !isFirstOpen {
                do {
                    let launcher = try await LauncherCoreKt.getMainlyUsedLauncher()
                    self.mainlyLauncher = launcher
                    let cachedGameInfos = try await HomeService.shared.getGamesInfoDuringLaunching(launcher: launcher)
                    self.gameInfos = cachedGameInfos
                    let lastGame = try await LauncherCoreKt.getLastOpenedGame()
                    if let lastGame = lastGame {
                        if !cachedGameInfos.isEmpty && cachedGameInfos.contains(where: { $0.id == lastGame }) {
                            self.lastOpenedGame = lastGame
                        }
                    } else {
                        if !cachedGameInfos.isEmpty {
                            self.lastOpenedGame = cachedGameInfos.first!.id
                        }
                    }
                } catch {
                    NSApplication.shared.terminate(nil)
                }
            }
        }
    }
    
    func fillGamesDuringSetup(_ games: [GameInfo]) {
        gameInfos.removeAll()
        if !games.isEmpty {
            gameInfos.append(contentsOf: games)
            let firstGame = games.first!.id
            setLastOpenedGame(gameId: firstGame)
        }
    }
    
    func setLastOpenedGame(gameId: String) {
        Task { @MainActor in
            try! await LauncherCoreKt.setLastOpenedGame(gameId: gameId)
            self.lastOpenedGame = gameId
        }
    }
    
    func updateOrInsertGameInfo(_ neoInfo: GameInfo) {
        if let index = gameInfos.firstIndex(where: { $0.id == neoInfo.id }) {
            if !gameInfos[index].isEqual(neoInfo) {
                gameInfos[index] = neoInfo
            }
        } else {
            gameInfos.append(neoInfo)
        }
    }
    
    func installStatus(for gameId: String) -> InstallStatus {
        gameInstallStatuses[gameId] ?? .notInstalled
    }

    func startInstallStatusPolling() {
        installPollingTask?.cancel()
        installPollingTask = Task { @MainActor in
            while !Task.isCancelled {
                await refreshInstallStatuses()
                try? await Task.sleep(for: .milliseconds(250))
            }
        }
    }

    func stopInstallStatusPolling() {
        installPollingTask?.cancel()
        installPollingTask = nil
    }

    @MainActor
    func refreshInstallStatuses() async {
        for game in gameInfos {
            do {
                let status = try await GameService.shared.getGameInstallStatus(
                    gameId: GameId.Companion.shared.convertId(id: game.id)
                )
                gameInstallStatuses[game.id] = InstallStatus(status)
            } catch {
                print("读取游戏安装状态失败: \(error)")
            }
        }
    }

    func cacheGameNews(_ content: GameContent) {
        if !gameNews.contains(where: { $0.game.id == content.game.id }) {
            gameNews.append(content)
        }
    }
}

extension AppState {
    static var forPreviewWithWizard: AppState {
        let state = AppState()
        state.isFirstOpen = true
        return state
    }
}


enum InstallStatus: Equatable {
    case notInstalled
    case preparing
    case downloading(downloadedBytes: Int64, totalBytes: Int64)
    case paused(downloadedBytes: Int64, totalBytes: Int64)
    case completed
    case failed
    case terminated

    init(_ status: GameInstallStatus) {
        switch status.state {
        case "preparing": self = .preparing
        case "downloading": self = .downloading(downloadedBytes: status.downloadedBytes, totalBytes: status.totalBytes)
        case "paused": self = .paused(downloadedBytes: status.downloadedBytes, totalBytes: status.totalBytes)
        case "completed": self = .completed
        case "failed": self = .failed
        case "terminated": self = .terminated
        default: self = .notInstalled
        }
    }

    var progress: Double {
        switch self {
        case .downloading(let downloaded, let total), .paused(let downloaded, let total):
            guard total > 0 else { return 0 }
            return min(max(Double(downloaded) / Double(total), 0), 1)
        case .completed: return 1
        default: return 0
        }
    }

    var downloadedText: String? {
        switch self {
        case .downloading(let downloaded, let total), .paused(let downloaded, let total):
            return "\(Self.formatBytes(downloaded)) / \(Self.formatBytes(total))"
        default: return nil
        }
    }

    private static func formatBytes(_ value: Int64) -> String {
        ByteCountFormatter.string(fromByteCount: value, countStyle: .file)
    }
}
