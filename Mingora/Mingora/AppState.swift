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
